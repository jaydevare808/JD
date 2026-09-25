package com.jd.papernote

import android.content.Context
import android.os.Build
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lifecycle-safe wrapper around the bundled on-device model.
 *
 * The model is intentionally lazy-loaded and held only while the AI screen needs it.
 * Releasing it when the AI screen stops is important because the native model can consume
 * hundreds of MB of RAM.
 */
object LocalAiBridge {
    private val lock = Any()
    private val scope = CoroutineScope(Dispatchers.IO)

    interface Callback {
        fun onReady()
        fun onResult(text: String, tokensPerSecond: Float)
        fun onError(message: String)
    }

    private var activeJob: Job? = null
    private var activeModel: LlamaModel? = null
    private var releaseWhenIdle = false

    @JvmStatic
    fun isSupported(): Boolean {
        return Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
    }

    @JvmStatic
    fun ask(context: Context, prompt: String, callback: Callback) {
        if (!isSupported()) {
            callback.onError("PaperNote AI requires a 64-bit ARM device.")
            return
        }

        val safePrompt = prompt.trim()
        if (safePrompt.isEmpty()) {
            callback.onError("Ask a study question first.")
            return
        }

        val previousJob = synchronized(lock) {
            val previous = activeJob
            previous?.cancel()
            releaseWhenIdle = false
            previous
        }

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                // Never let two native inference calls share the same model at once.
                previousJob?.join()
                val modelFile = File(context.filesDir, "papernote-ai/PaperNote-AI.gguf")
                prepareModel(context, modelFile)

                val model = synchronized(lock) {
                    activeModel
                } ?: Llama.loadModel(
                    modelPath = modelFile.absolutePath,
                    config = LlamaConfig(
                        contextSize = 2048,
                        threads = maxOf(
                            2,
                            minOf(Runtime.getRuntime().availableProcessors(), 6)
                        ),
                        temperature = 0.35f,
                        topP = 0.9f,
                        topK = 40
                    )
                ).also {
                    synchronized(lock) {
                        activeModel = it
                    }
                }

                withContext(Dispatchers.Main) {
                    callback.onReady()
                }

                val result = Llama.complete(
                    model,
                    prompt = safePrompt,
                    systemPrompt = "You are PaperNote AI, a patient school study tutor. " +
                            "Explain concepts clearly. For mathematics and physics, show necessary steps. " +
                            "Distinguish formulas from reasoning. Never pretend certainty. " +
                            "Use simple language and concise headings.",
                    maxTokens = 384
                )

                withContext(Dispatchers.Main) {
                    callback.onResult(result.text.trim(), result.tokensPerSecond)
                }
            } catch (t: Throwable) {
                val message = t.message?.takeIf { it.isNotBlank() }
                    ?: "PaperNote AI could not complete the request."
                withContext(Dispatchers.Main) {
                    callback.onError(message)
                }
            } finally {
                synchronized(lock) {
                    if (activeJob === coroutineContext[Job]) {
                        activeJob = null
                    }
                    if (releaseWhenIdle) {
                        releaseActiveModelLocked()
                        releaseWhenIdle = false
                    }
                }
            }
        }

        synchronized(lock) {
            activeJob = job
        }
        job.start()
    }

    @JvmStatic
    fun cancel() {
        synchronized(lock) {
            activeJob?.cancel()
        }
    }

    /**
     * Request cancellation and native-model release after the active native call becomes idle.
     * When no request is running, release is immediate.
     */
    @JvmStatic
    fun release() {
        synchronized(lock) {
            val job = activeJob
            if (job == null || job.isCompleted) {
                releaseActiveModelLocked()
                releaseWhenIdle = false
            } else {
                releaseWhenIdle = true
                job.cancel()
            }
        }
    }

    private fun releaseActiveModelLocked() {
        activeModel?.let {
            try {
                Llama.releaseModel(it)
            } catch (_: Throwable) {
            }
        }
        activeModel = null
    }

    private fun prepareModel(context: Context, target: File) {
        if (target.exists() && target.length() >= 80_000_000L) return

        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".part")
        if (temp.exists()) temp.delete()

        context.assets.open("PaperNote-AI.gguf").use { input ->
            temp.outputStream().use { output ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    output.write(buffer, 0, count)
                }
                output.flush()
            }
        }

        if (temp.length() < 80_000_000L) {
            temp.delete()
            throw IllegalStateException("Offline AI model is missing or incomplete.")
        }

        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IllegalStateException("Unable to prepare the offline AI model.")
        }
    }
}
