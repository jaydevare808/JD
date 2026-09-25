package com.jd.papernote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public final class AiAssistantActivity extends Activity {
    private NotebookStore store;
    private EditText question;
    private TextView answer;
    private TextView status;
    private Button askButton;
    private String selectedMode = "Tutor";
    private String notebookId;
    private int pageIndex;
    private String currentAnswer = "";

    private final LocalAiBridge.Callback aiCallback = new LocalAiBridge.Callback() {
        @Override public void onReady() {
            runOnUiThread(() -> {
                if (status != null) status.setText("LOCAL • READY • THINKING");
            });
        }

        @Override public void onResult(String text, float tokensPerSecond) {
            runOnUiThread(() -> {
                currentAnswer = text == null ? "" : text.trim();
                answer.setText(currentAnswer.isEmpty() ? "No answer returned." : currentAnswer);
                status.setText(String.format(Locale.US, "LOCAL • %.1f tok/s", tokensPerSecond));
                askButton.setEnabled(true);
            });
        }

        @Override public void onError(String message) {
            runOnUiThread(() -> {
                status.setText("LOCAL • ERROR");
                answer.setText(message == null ? "PaperNote AI could not answer." : message);
                askButton.setEnabled(true);
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new NotebookStore(this);
        notebookId = getIntent().getStringExtra("notebook_id");
        pageIndex = getIntent().getIntExtra("page_index", 0);
        build();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // The local model can use hundreds of MB of native memory. Release it as soon
        // as the AI screen is no longer visible so handwriting remains responsive.
        LocalAiBridge.release();
    }

    @Override
    protected void onDestroy() {
        LocalAiBridge.release();
        super.onDestroy();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F7FB);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(10), dp(12), dp(10));
        header.setBackgroundColor(0xFF182339);
        header.setElevation(dp(5));

        Button back = button("‹", true);
        back.setTextSize(24);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(text("PaperNote AI", 21, Color.WHITE, true));
        titleBox.addView(text("Private on-device study tutor", 12, 0xFFC9D2E1, false));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.setMargins(dp(8), 0, 0, 0);
        header.addView(titleBox, titleLp);

        status = text(LocalAiBridge.isSupported() ? "LOCAL • OFFLINE • FREE" : "NOT AVAILABLE ON THIS DEVICE", 10, 0xFFDDE5F2, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(7), 0, dp(7), 0);
        status.setBackground(rounded(LocalAiBridge.isSupported() ? 0xFF2A3854 : 0xFF7E3D45, 14));
        header.addView(status, new LinearLayout.LayoutParams(dp(132), dp(36)));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(28));

        LinearLayout modelCard = card();
        modelCard.addView(text("PAPERNOTE AI LITE", 11, 0xFF667085, true));
        modelCard.addView(text("SmolLM2 360M Instruct • Q4_K_M", 16, 0xFF182339, true));
        modelCard.addView(text(
                "Bundled for offline use. The model is roughly 271 MB before the rest of the app. " +
                        "No API key, subscription, or cloud request is required. Answers can be wrong, so verify important academic work.",
                12, 0xFF5B6473, false));
        body.addView(modelCard, bottomMargin(dp(10)));

        if (!LocalAiBridge.isSupported()) {
            LinearLayout unsupported = card();
            unsupported.addView(text("AI engine unavailable", 16, 0xFF8B2F3A, true));
            unsupported.addView(text(
                    "This build uses the free ARM64 on-device engine. The notebook and study features still work normally.",
                    12, 0xFF5B6473, false));
            body.addView(unsupported, bottomMargin(dp(10)));
        }

        if (notebookId != null) {
            NotebookStore.NotebookMeta notebook = null;
            try { notebook = store.get(notebookId); } catch (Exception ignored) {}
            if (notebook != null) {
                body.addView(contextCard(notebook));
            }
        }

        body.addView(text("STUDY MODE", 11, 0xFF667085, true), bottomMargin(dp(7)));
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        addMode(modes, "Tutor");
        addMode(modes, "Solve");
        addMode(modes, "Quiz");
        addMode(modes, "Revise");
        addMode(modes, "Plan");
        body.addView(modes, bottomMargin(dp(10)));

        question = new EditText(this);
        question.setHint("Ask a question, paste a problem, or describe what you are studying…");
        question.setTextSize(15);
        question.setGravity(Gravity.TOP | Gravity.START);
        question.setMinLines(5);
        question.setMaxLines(10);
        question.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        question.setPadding(dp(14), dp(12), dp(14), dp(12));
        question.setBackground(rounded(Color.WHITE, 16));
        body.addView(question, bottomMargin(dp(9)));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);

        askButton = button("Ask PaperNote AI", true);
        askButton.setTextSize(14);
        askButton.setOnClickListener(v -> ask());
        actionRow.addView(askButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button clear = button("Clear", false);
        clear.setOnClickListener(v -> {
            question.setText("");
            answer.setText("Your answer will appear here.");
            currentAnswer = "";
            status.setText(LocalAiBridge.isSupported() ? "LOCAL • OFFLINE • FREE" : "NOT AVAILABLE ON THIS DEVICE");
        });
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(dp(84), dp(48));
        clearLp.setMargins(dp(9), 0, 0, 0);
        actionRow.addView(clear, clearLp);
        body.addView(actionRow, bottomMargin(dp(12)));

        body.addView(text("ANSWER", 11, 0xFF667085, true), bottomMargin(dp(7)));
        LinearLayout answerCard = card();
        answer = text("Your answer will appear here.", 14, 0xFF263143, false);
        answer.setTextIsSelectable(true);
        answer.setLineSpacing(0f, 1.15f);
        answerCard.addView(answer);

        LinearLayout answerActions = new LinearLayout(this);
        answerActions.setGravity(Gravity.CENTER_VERTICAL);
        Button copy = button("Copy", false);
        copy.setOnClickListener(v -> copyAnswer());
        answerActions.addView(copy, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button savePage = button("Save to Page", false);
        savePage.setOnClickListener(v -> saveToPage());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        saveLp.setMargins(dp(8), 0, 0, 0);
        answerActions.addView(savePage, saveLp);
        answerCard.addView(answerActions, topMargin(dp(11), 0));
        body.addView(answerCard, bottomMargin(dp(10)));

        TextView privacy = text(
                "PaperNote AI Lite runs locally on supported devices. This assistant is not a substitute for your textbook, teacher or official answer key.",
                11, 0xFF7A8495, false);
        privacy.setPadding(dp(3), dp(5), dp(3), 0);
        body.addView(privacy);

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private LinearLayout contextCard(NotebookStore.NotebookMeta notebook) {
        LinearLayout box = card();
        box.addView(text("CURRENT NOTEBOOK", 11, 0xFF667085, true));
        box.addView(text(notebook.title + " • " + notebook.subject, 15, 0xFF182339, true));
        box.addView(text(
                notebook.pages.size() + " pages • current page " +
                        Math.min(pageIndex + 1, notebook.pages.size()),
                12, 0xFF5B6473, false));
        return box;
    }

    private void addMode(LinearLayout parent, String label) {
        Button b = button(label, "Tutor".equals(label));
        b.setTextSize(12);
        b.setOnClickListener(v -> {
            selectedMode = label;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child instanceof Button) {
                    ((Button) child).setBackground(rounded(
                            selectedMode.equals(((Button) child).getText().toString()) ? 0xFF405DE6 : Color.WHITE, 12));
                    ((Button) child).setTextColor(selectedMode.equals(((Button) child).getText().toString()) ? Color.WHITE : 0xFF182339);
                }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(0, 0, dp(7), 0);
        parent.addView(b, lp);
    }

    private void ask() {
        if (!LocalAiBridge.isSupported()) {
            toast("PaperNote AI Lite is unavailable on this device architecture.");
            return;
        }
        String userText = question.getText().toString().trim();
        if (userText.isEmpty()) {
            toast("Enter a study question first.");
            return;
        }
        if (userText.length() > 7000) {
            toast("Please keep the question under 7,000 characters.");
            return;
        }

        askButton.setEnabled(false);
        answer.setText("Preparing the local model…");
        status.setText("LOCAL • PREPARING");

        String context = buildContext();
        String prompt = buildPrompt(selectedMode, context, userText);
        LocalAiBridge.ask(getApplicationContext(), prompt, aiCallback);
    }

    private String buildContext() {
        if (notebookId == null) return "No notebook context was selected.";
        try {
            NotebookStore.NotebookMeta n = store.get(notebookId);
            if (n == null) return "No notebook context was selected.";
            String pageTitle = pageIndex >= 0 && pageIndex < n.pages.size()
                    ? n.pages.get(pageIndex).title : "current page";
            return "Notebook: " + n.title + "\nSubject: " + n.subject +
                    "\nPages: " + n.pages.size() +
                    "\nCurrent page: " + pageTitle;
        } catch (Exception e) {
            return "Notebook context unavailable.";
        }
    }

    private String buildPrompt(String mode, String context, String userText) {
        String task;
        switch (mode) {
            case "Solve":
                task = "Solve the problem step by step. Show formulas, substitute values, and state the final result. Never skip a necessary step.";
                break;
            case "Quiz":
                task = "Create a short 5-question quiz on the user's topic. Put the answer key after the questions. Keep it suitable for a school student.";
                break;
            case "Revise":
                task = "Create a compact revision sheet: key ideas, formulas, common mistakes, and 3 quick self-check questions.";
                break;
            case "Plan":
                task = "Create a practical study plan using only the supplied notebook context and the student's request. Keep it realistic and concise.";
                break;
            default:
                task = "Teach the topic like a patient school tutor. Start with the core idea, then a worked example, then a quick self-check.";
                break;
        }
        return "STUDY CONTEXT:\n" + context + "\n\nTASK:\n" + task +
                "\n\nSTUDENT REQUEST:\n" + userText +
                "\n\nRules: Be concise but useful. Do not invent syllabus-specific facts. " +
                "When unsure, say so. Use plain text and simple headings. Do not claim access to the student's handwriting unless text was supplied.";
    }

    private void copyAnswer() {
        if (currentAnswer.isEmpty()) {
            toast("There is no answer to copy.");
            return;
        }
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("PaperNote AI", currentAnswer));
        toast("Answer copied");
    }

    private void saveToPage() {
        if (currentAnswer.isEmpty() || notebookId == null) {
            toast("Open AI from a notebook to save answers to a page.");
            return;
        }
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("notebook_id", notebookId);
        intent.putExtra("page_index", Math.max(0, pageIndex));
        intent.putExtra("open_feature", "ai_save");
        intent.putExtra("ai_text", currentAnswer);
        startActivity(intent);
        toast("Opening the page to save the answer.");
    }

    private String formatDuration(long ms) {
        long minutes = Math.max(0L, ms / 60000L);
        if (minutes < 60) return minutes + " min";
        return (minutes / 60) + "h " + String.format(Locale.US, "%02dm", minutes % 60);
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(13), dp(15), dp(13));
        box.setBackground(rounded(Color.WHITE, 18));
        box.setElevation(dp(1.5f));
        return box;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setTextSize(13);
        b.setTextColor(primary ? Color.WHITE : 0xFF182339);
        b.setBackground(rounded(primary ? 0xFF405DE6 : Color.WHITE, 13));
        b.setPadding(dp(9), 0, dp(9), 0);
        return b;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), color == Color.WHITE ? 0xFFE2E5EB : color);
        return d;
    }

    private LinearLayout.LayoutParams bottomMargin(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp);
        return lp;
    }

    private LinearLayout.LayoutParams topMargin(int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, top, 0, bottom);
        return lp;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
