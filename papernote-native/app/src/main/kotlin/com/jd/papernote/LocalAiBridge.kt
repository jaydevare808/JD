package com.jd.papernote

import android.content.Context
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object LocalAiBridge {
    private val lock = Any()

    interface Callback {
        fun onReady()
        fun onResult(text: String, tokensPerSecond: Float)
        fun onError(message: String)
    }

    private var activeJob: Job? = null
    private var activeModel: LlamaModel? = null

    @JvmStatic
    fun isSupported(): Boolean {
        return android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
    }

    @JvmStatic
    fun ask(context: Context, prompt: String, callback: Callback) {
        if (!isSupported()) {
            callback.onError("PaperNote AI Lite currently requires a 64-bit ARM device.")
            return
        }
        val safePrompt = prompt.trim()
        if (safePrompt.isEmpty()) {
            callback.onError("Ask a study question first.")
            return
        }
        cancel()
        activeJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val modelFile = File(context.filesDir, "papernote-ai/PaperNote-AI.gguf")
                modelFile.parentFile?.mkdirs()
                if (!modelFile.exists() || modelFile.length() < 200_000_000L) {
                    copyBundledModel(context, modelFile)
                }

                if (!modelFile.exists() || modelFile.length() < 80_000_000L) {
                    throw IllegalStateException("Offline AI model is missing or incomplete.")
                }

                val model = activeModel ?: Llama.loadModel(
                    modelPath = modelFile.absolutePath,
                    config = LlamaConfig(
                        contextSize = 2048,
                        threads = maxOf(2, minOf(Runtime.getRuntime().availableProcessors(), 6)),
                        temperature = 0.35f,
                        topP = 0.9f,
                        topK = 40
                    )
                ).also { activeModel = it }
                withContext(Dispatchers.Main) { callback.onReady() }

                val result = Llama.complete(
                    model,
                    prompt = safePrompt,
                    systemPrompt = "You are PaperNote AI, a patient study tutor. Explain concepts clearly, show concise steps for maths and physics, distinguish formulas from reasoning, and never pretend certainty. Prefer simple language. The user is a student.",
                    maxTokens = 384
                )
                withContext(Dispatchers.Main) {
                    callback.onResult(result.text.trim(), result.tokensPerSecond)
                }
            } catch (t: Throwable) {
                val message = t.message?.takeIf { it.isNotBlank() } ?: "AI request failed."
                withContext(Dispatchers.Main) { callback.onError(message) }
            } finally {
                synchronized(lock) {
                    activeJob = null
                }
            }
        }
    }

    @JvmStatic
    fun cancel() {
        synchronized(lock) {
            activeJob?.cancel()
            activeJob = null
        }
    }

    @JvmStatic
    fun release() {
        synchronized(lock) {
            activeJob?.cancel()
            activeJob = null
            activeModel?.let {
                try {
                    Llama.releaseModel(it)
                } catch (_: Throwable) {
                }
            }
            activeModel = null
        }
    }

    private fun copyBundledModel(context: Context, target: File) {
        val temp = File(target.parentFile, target.name + ".part")
        if (temp.exists()) temp.delete()
        context.assets.open("PaperNote-AI.gguf").use { input ->
            temp.outputStream().use { output ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    output.write(buffer, 0, n)
                }
                output.flush()
            }
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) throw IllegalStateException("Unable to prepare offline AI model.")
    }
}
