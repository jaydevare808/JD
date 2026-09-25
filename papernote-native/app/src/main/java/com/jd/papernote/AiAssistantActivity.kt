package com.jd.papernote

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class AiAssistantActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var model: LlamaModel? = null
    private var modelBusy = false

    private lateinit var conversation: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var status: TextView
    private lateinit var contextLabel: TextView

    private var notebookId: String? = null
    private var pageIndex: Int = 0
    private lateinit var store: NotebookStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = NotebookStore(this)
        notebookId = intent.getStringExtra("notebook_id")
        pageIndex = intent.getIntExtra("page_index", 0)
        buildUi()
        prepareModel()
    }

    override fun onDestroy() {
        scope.coroutineContext.cancel()
        model?.let { Llama.releaseModel(it) }
        model = null
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF5F7FB.toInt())
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(0xFF182339.toInt())
            elevation = dp(4).toFloat()
        }

        val back = button("‹", true).apply {
            textSize = 24f
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(44)))

        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(text("PaperNote AI", 21f, Color.WHITE, true))
        titleBox.addView(text("Free • on-device • no account • no cloud", 12f, 0xFFC9D2E1.toInt(), false))
        val titleParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(8), 0, 0, 0) }
        header.addView(titleBox, titleParams)

        val info = button("i", true).apply {
            textSize = 16f
            setOnClickListener { showAboutAi() }
        }
        header.addView(info, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(header)

        val contextCard = card().apply {
            addView(text("STUDY CONTEXT", 11f, 0xFF667085.toInt(), true))
            contextLabel = text(buildStudyContextSummary(), 12f, 0xFF394557.toInt(), false)
            addView(contextLabel)
            val refresh = button("Refresh context", false).apply {
                setOnClickListener { contextLabel.text = buildStudyContextSummary() }
            }
            addView(refresh, topMargin(dp(8), 0))
        }
        root.addView(contextCard, bottomMargin(dp(10)))

        val shortcuts = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        shortcut(shortcuts, "Explain", "Explain the study topic clearly with a simple example.")
        shortcut(shortcuts, "Quiz me", "Create 5 short practice questions from my study context. Put answers after the questions.")
        shortcut(shortcuts, "Flashcards", "Create 6 concise flashcards from my study context. Use question → answer format.")
        root.addView(shortcuts, horizontalMargin(dp(14), dp(8)))

        val shortcuts2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        shortcut(shortcuts2, "Study plan", "Create a realistic study plan for the topic I am working on.")
        shortcut(shortcuts2, "Simplify", "Explain the topic as if I am learning it for the first time. Avoid unnecessary jargon.")
        shortcut(shortcuts2, "Exam answer", "Show how I could structure a concise exam-style answer. Do not invent board-specific marking rules.")
        root.addView(shortcuts2, horizontalMargin(dp(14), 0))

        scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        conversation = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(12))
        }
        scroll.addView(conversation)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val inputBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
        }

        input = EditText(this).apply {
            hint = "Ask PaperNote AI…"
            setTextSize(14f)
            minLines = 1
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackground(rounded(Color.WHITE, 14))
        }
        inputBar.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))

        sendButton = button("Send", true).apply {
            setOnClickListener { sendPrompt(input.text.toString()) }
        }
        val sendParams = LinearLayout.LayoutParams(dp(82), dp(52)).apply { setMargins(dp(8), 0, 0, 0) }
        inputBar.addView(sendButton, sendParams)
        root.addView(inputBar)

        status = text("Preparing on-device AI…", 11f, 0xFF667085.toInt(), false)
        status.setPadding(dp(14), dp(3), dp(14), dp(6))
        root.addView(status)

        setContentView(root)
        addAssistantMessage(
            "Hi. I am PaperNote AI.

I can help with explanations, quizzes, flashcards, study plans and exam-answer structure. I use the small language model bundled with this app and your local notebook metadata/study markers.

Important: I do not currently read the handwriting pixels on your page. For exact note content, paste or type it into the chat. Verify important academic facts."
        )
    }

    private fun prepareModel() {
        status.text = "Preparing local AI model…"
        setBusy(true)
        scope.launch(Dispatchers.IO) {
            try {
                val modelFile = copyModelIfNeeded()
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
                val loaded = Llama.loadModel(
                    modelFile.absolutePath,
                    LlamaConfig(contextSize = 1536, threads = threads)
                )
                withContext(Dispatchers.Main) {
                    model = loaded
                    status.text = "AI ready • local inference • no internet"
                    setBusy(false)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    status.text = "AI unavailable"
                    setBusy(false)
                    addAssistantMessage("I could not start the bundled AI model on this device. You can continue using PaperNote's offline study tools. Error: ${t.message ?: "unknown error"}")
                }
            }
        }
    }

    private fun copyModelIfNeeded(): File {
        val dir = File(filesDir, "models").apply { mkdirs() }
        val target = File(dir, MODEL_NAME)
        val expectedMin = 100L * 1024L * 1024L
        if (target.exists() && target.length() >= expectedMin) return target

        val temp = File(dir, "$MODEL_NAME.part")
        if (temp.exists()) temp.delete()

        assets.open(MODEL_NAME).use { input ->
            temp.outputStream().use { output ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }

        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Could not install local AI model" }
        check(target.length() >= expectedMin) { "Bundled AI model is incomplete" }
        return target
    }

    private fun sendPrompt(raw: String) {
        val question = raw.trim()
        if (question.isEmpty() || modelBusy) return
        val currentModel = model
        if (currentModel == null) {
            Toast.makeText(this, "AI is still preparing.", Toast.LENGTH_SHORT).show()
            return
        }

        addUserMessage(question)
        input.setText("")
        setBusy(true)
        val system = """
            You are PaperNote AI, a patient study assistant for school and college students.
            Be concise but useful. Teach step by step. When the student asks for practice,
            create questions first and answers after them. Never claim to have seen handwriting
            or a document unless its text is actually present in the prompt. Do not invent
            board-specific marking schemes, syllabus facts, citations, or numerical answers.
            When uncertain, say so and advise verification. Keep the tone encouraging and practical.
        """.trimIndent()

        val prompt = buildPrompt(question)
        scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    Llama.complete(
                        currentModel,
                        prompt = prompt,
                        systemPrompt = system,
                        maxTokens = 256
                    )
                }
                addAssistantMessage(result.text.trim().ifEmpty { "I could not generate a useful answer. Try rephrasing the question." })
                status.text = String.format(
                    Locale.US,
                    "AI ready • %.1f tok/s • local only",
                    result.tokensPerSecond
                )
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                addAssistantMessage("Generation failed: ${t.message ?: "unknown error"}. Your notes were not uploaded.")
                status.text = "AI ready • local inference"
            } finally {
                setBusy(false)
            }
        }
    }

    private fun buildPrompt(question: String): String {
        val context = buildFullStudyContext()
        return """
            STUDY CONTEXT:
            $context

            STUDENT QUESTION:
            $question
        """.trimIndent()
    }

    private fun buildStudyContextSummary(): String {
        val n = currentNotebook() ?: return "No notebook selected. Ask general study questions or open AI from a notebook."
        val page = currentPage(n)
        val marks = page?.let { store.getStudyMarks(it.id).count { !it.resolved } } ?: 0
        return "${n.title} • ${n.subject} • ${n.pages.size} pages • $marks open study markers"
    }

    private fun buildFullStudyContext(): String {
        val n = currentNotebook() ?: return "No notebook context available."
        val page = currentPage(n)
        val parts = StringBuilder()
        parts.append("Notebook: ").append(n.title).append("\n")
        parts.append("Subject: ").append(n.subject).append("\n")
        parts.append("Pages: ").append(n.pages.size).append("\n")
        if (page != null) {
            parts.append("Current page: ").append(page.title).append("\n")
            val stats = store.getPageStats(page.id)
            parts.append("Current page activity: ").append(stats.strokes)
                .append(" recorded writing starts, ")
                .append(stats.activeMs / 60000L).append(" active minutes.\n")
            val marks = store.getStudyMarks(page.id)
            if (marks.isNotEmpty()) {
                parts.append("Current page markers:\n")
                marks.take(12).forEach {
                    parts.append("- ").append(it.type)
                        .append(if (it.resolved) " (resolved)" else " (open)")
                        .append(": ").append(it.note.ifEmpty { "no note" }).append("\n")
                }
            }
        }
        parts.append("Open study markers across notebook:\n")
        for (p in n.pages) {
            for (m in store.getStudyMarks(p.id)) {
                if (!m.resolved) {
                    parts.append("- ").append(p.title).append(" / ").append(m.type)
                        .append(": ").append(m.note.ifEmpty { "no note" }).append("\n")
                }
            }
        }
        return parts.toString().take(7000)
    }

    private fun currentNotebook(): NotebookStore.NotebookMeta? {
        val id = notebookId ?: return null
        return try { store.get(id) } catch (_: Exception) { null }
    }

    private fun currentPage(n: NotebookStore.NotebookMeta): NotebookStore.PageMeta? {
        if (n.pages.isEmpty()) return null
        return n.pages[pageIndex.coerceIn(0, n.pages.lastIndex)]
    }

    private fun shortcut(row: LinearLayout, label: String, prompt: String) {
        val b = button(label, false)
        b.textSize = 11f
        b.setOnClickListener { sendPrompt(prompt) }
        row.addView(b, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
            setMargins(dp(3), 0, dp(3), 0)
        })
    }

    private fun addUserMessage(message: String) {
        addMessage("YOU", message, 0xFFE8EEF9.toInt())
    }

    private fun addAssistantMessage(message: String) {
        addMessage("PAPERNOTE AI", message, Color.WHITE)
    }

    private fun addMessage(label: String, message: String, background: Int) {
        val box = card().apply {
            setBackground(rounded(background, 17))
            addView(text(label, 10f, 0xFF667085.toInt(), true))
            addView(text(message, 14f, 0xFF202A39.toInt(), false).apply {
                setPadding(0, dp(5), 0, 0)
            })
        }
        conversation.addView(box, bottomMargin(dp(8)))
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun setBusy(busy: Boolean) {
        modelBusy = busy
        if (::sendButton.isInitialized) sendButton.isEnabled = !busy
        if (::input.isInitialized) input.isEnabled = !busy
    }

    private fun showAboutAi() {
        AlertDialog.Builder(this)
            .setTitle("About PaperNote AI")
            .setMessage(
                "PaperNote AI runs a compact language model on your device. No account, API key, server request, or per-token charge is used. The assistant is designed for study help, not as an authoritative source. It does not currently interpret the handwriting pixels of a page."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackground(rounded(Color.WHITE, 17))
        }
    }

    private fun button(label: String, dark: Boolean): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextSize(12f)
            minHeight = 0
            minWidth = 0
            setPadding(dp(9), 0, dp(9), 0)
            setTextColor(if (dark) Color.WHITE else 0xFF182339.toInt())
            setBackground(rounded(if (dark) 0xFF2A3854.toInt() else Color.WHITE, 12))
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            this.text = value
            setTextSize(size)
            setTextColor(color)
            if (bold) setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }

    private fun rounded(color: Int, radius: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), if (color == Color.WHITE) 0xFFE0E4EA.toInt() else color)
        }
    }

    private fun bottomMargin(bottom: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, bottom) }

    private fun horizontalMargin(horizontal: Int, bottom: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, dp(40)).apply { setMargins(horizontal, 0, horizontal, bottom) }

    private fun topMargin(top: Int, bottom: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, top, 0, bottom) }

    private fun dp(value: Int): Int =
        kotlin.math.round(value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MODEL_NAME = "SmolLM2-135M-Instruct.Q4_K_M.gguf"
    }
}
