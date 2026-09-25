package com.jd.papernote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StudyToolsActivity extends Activity {
    private NotebookStore store;
    private ArrayList<NotebookStore.NotebookMeta> notebooks;
    private Spinner notebookSpinner;
    private TextView selectedInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new NotebookStore(this);
        notebooks = new ArrayList<>(store.list());
        build();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F7FB);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(12), dp(14), dp(12));
        header.setBackgroundColor(0xFF182339);

        Button back = button("‹", true);
        back.setTextSize(25);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Study Workspace", 21, Color.WHITE, true);
        TextView subtitle = text("Every study feature in one place. All data stays on this device.", 12, 0xFFC9D2E1, false);
        titleBox.addView(title);
        titleBox.addView(subtitle);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.setMargins(dp(8), 0, 0, 0);
        header.addView(titleBox, titleParams);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(26));

        content.addView(text("SELECT NOTEBOOK", 11, 0xFF667085, true), bottomMargin(dp(7)));

        notebookSpinner = new Spinner(this);
        ArrayList<String> names = new ArrayList<>();
        for (NotebookStore.NotebookMeta n : notebooks) {
            names.add(n.title + "  •  " + n.subject);
        }
        if (names.isEmpty()) names.add("Create a notebook first");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        notebookSpinner.setAdapter(adapter);
        if (!names.isEmpty()) notebookSpinner.setSelection(0);
        notebookSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateSelectedInfo();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {
                updateSelectedInfo();
            }
        });
        content.addView(notebookSpinner, bottomMargin(dp(7)));

        selectedInfo = text("", 12, 0xFF5B6473, false);
        selectedInfo.setPadding(dp(2), 0, dp(2), dp(12));
        content.addView(selectedInfo);

        LinearLayout aiCard = card();
        aiCard.addView(text("PAPERNOTE AI TUTOR", 15, 0xFF182339, true));
        aiCard.addView(text("Ask for an explanation, create a quiz, make flashcards, or turn your study markers into a revision prompt. The AI runs locally.", 12, 0xFF667085, false));
        Button aiOpen = button("OPEN AI", true);
        aiOpen.setOnClickListener(v -> openAiAssistant());
        aiCard.addView(aiOpen, topMargin(dp(9), 0));
        content.addView(aiCard, bottomMargin(dp(11)));

        content.addView(text("PAGE & REVISION", 11, 0xFF667085, true), bottomMargin(dp(7)));
        addFeature(content, "Study marks", "Pin doubts, mistakes, important ideas and revise targets on the exact spot.", "marks");
        addFeature(content, "Study inbox", "Open, resolve or reopen every study marker from this notebook.", "inbox");
        addFeature(content, "Recall cover", "Hide your handwriting for active recall and reveal it when you finish.", "recall");
        addFeature(content, "Ghost compare", "Capture a page snapshot and compare your current page with an earlier version.", "ghost");
        addFeature(content, "Page analytics", "See active time, stroke count and a page-area writing heatmap.", "analytics");
        addFeature(content, "Concept thread", "Connect two pages with a named concept relationship.", "link");
        addFeature(content, "Handwriting replay", "Replay the current editing session stroke-by-stroke without losing the saved page.", "replay");
        addFeature(content, "Exam practice", "Create a 2-, 3- or 4-mark answer page with a real countdown timer.", "exam");
        addFeature(content, "Science experiment", "Create a structured lab page with Aim, Apparatus, Procedure and Result sections.", "experiment");

        content.addView(text("STUDY UTILITIES", 11, 0xFF667085, true), topMargin(dp(15), dp(7)));
        addUtility(content, "Scientific calculator", "Evaluate arithmetic, powers, square roots and π locally.", v -> showCalculator());
        addUtility(content, "Focus timer", "A local 25-minute focus session with a completion alert.", v -> showFocusTimer());
        addUtility(content, "Study checklist", "Build a quick revision checklist and place it on the selected page.", v -> openEditor("checklist"));
        addUtility(content, "Export notebook", "Export the selected notebook to PDF, PNG, JPG, WebP or ZIP.", v -> openEditor("export"));
        addUtility(content, "Share backup", "Create a .papernote backup and send it through Android's share sheet.", v -> openEditor("share"));
        addUtility(content, "Study Hub", "Open the dashboard with progress, timeline, markers and concept threads.", v -> startActivity(new Intent(this, StudyHubActivity.class)));

        TextView footer = text(
                "PaperNote does not upload your notes. Study Workspace uses the same local notebook data as the editor.",
                11, 0xFF7A8495, false
        );
        footer.setPadding(dp(3), dp(15), dp(3), 0);
        content.addView(footer);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        updateSelectedInfo();
    }

    private void openAiAssistant() {
        NotebookStore.NotebookMeta n = selectedNotebook();
        Intent intent = new Intent(this, AiAssistantActivity.class);
        if (n != null) {
            intent.putExtra("notebook_id", n.id);
            intent.putExtra("page_index", 0);
        }
        startActivity(intent);
    }

    private void updateSelectedInfo() {
        NotebookStore.NotebookMeta n = selectedNotebook();
        if (selectedInfo == null) return;
        if (n == null) {
            selectedInfo.setText("No notebook is available yet.");
            return;
        }
        int openMarks = store.countStudyMarks(n, null, true);
        long activeMs = store.getNotebookActiveMs(n);
        selectedInfo.setText(
                n.pages.size() + " pages  •  " + openMarks + " open study marks  •  " + formatDuration(activeMs)
        );
    }

    private NotebookStore.NotebookMeta selectedNotebook() {
        if (notebooks.isEmpty() || notebookSpinner == null) return null;
        int index = Math.max(0, Math.min(notebookSpinner.getSelectedItemPosition(), notebooks.size() - 1));
        return notebooks.get(index);
    }

    private void addFeature(LinearLayout parent, String title, String description, String feature) {
        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, 0xFF182339, true));
        labels.addView(text(description, 12, 0xFF667085, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        Button open = button("OPEN", false);
        open.setOnClickListener(v -> openEditor(feature));
        row.addView(open, new LinearLayout.LayoutParams(dp(78), dp(40)));
        card.addView(row);
        parent.addView(card, bottomMargin(dp(8)));
    }

    private void addUtility(LinearLayout parent, String title, String description, View.OnClickListener listener) {
        LinearLayout card = card();
        TextView label = text(title, 15, 0xFF182339, true);
        card.addView(label);
        card.addView(text(description, 12, 0xFF667085, false));
        Button open = button("OPEN", false);
        open.setOnClickListener(listener);
        card.addView(open, topMargin(dp(9), 0));
        parent.addView(card, bottomMargin(dp(8)));
    }

    private void openEditor(String feature) {
        NotebookStore.NotebookMeta n = selectedNotebook();
        if (n == null) {
            new AlertDialog.Builder(this)
                    .setTitle("No notebook")
                    .setMessage("Create a notebook from the Home screen before using notebook-specific tools.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("notebook_id", n.id);
        intent.putExtra("page_index", 0);
        intent.putExtra("open_feature", feature);
        startActivity(intent);
    }

    private void showCalculator() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(5), dp(14), 0);

        EditText expression = new EditText(this);
        expression.setHint("Example: (12+8)*3/4, sqrt(144), 2^5, π*10");
        expression.setSingleLine(true);
        expression.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(expression);

        TextView result = text("Result: ", 18, 0xFF182339, true);
        result.setPadding(0, dp(12), 0, dp(6));
        box.addView(result);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Scientific calculator")
                .setView(box)
                .setNegativeButton("Close", null)
                .setPositiveButton("Calculate", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                double value = evaluate(expression.getText().toString());
                result.setText("Result: " + formatNumber(value));
            } catch (Exception e) {
                result.setText("Result: Invalid expression");
            }
        }));
        dialog.show();
    }

    private void showFocusTimer() {
        final long durationMs = 25L * 60L * 1000L;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(5), dp(16), 0);

        TextView time = text("25:00", 44, 0xFF182339, true);
        time.setGravity(Gravity.CENTER);
        box.addView(time);
        box.addView(text("Stay on one chapter or problem set. The timer works offline.", 12, 0xFF667085, false));

        final CountDownTimer[] timer = {null};
        final boolean[] running = {false};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Focus timer")
                .setView(box)
                .setNegativeButton("Close", null)
                .setPositiveButton("Start", null)
                .create();

        dialog.setOnDismissListener(d -> {
            if (timer[0] != null) timer[0].cancel();
        });

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (running[0]) {
                if (timer[0] != null) timer[0].cancel();
                running[0] = false;
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Start");
                return;
            }
            running[0] = true;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Pause");
            timer[0] = new CountDownTimer(durationMs, 1000L) {
                @Override public void onTick(long millisUntilFinished) {
                    long totalSeconds = millisUntilFinished / 1000L;
                    time.setText(String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L));
                }
                @Override public void onFinish() {
                    running[0] = false;
                    time.setText("00:00");
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Start");
                    android.media.ToneGenerator tone = new android.media.ToneGenerator(
                            android.media.AudioManager.STREAM_NOTIFICATION, 90);
                    tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 700);
                    tone.release();
                }
            }.start();
        }));
        dialog.show();
    }

    private double evaluate(String source) {
        if (source == null || source.trim().isEmpty()) throw new IllegalArgumentException();
        String cleaned = source.replace("×", "*").replace("÷", "/").replace("π", String.valueOf(Math.PI));
        return new ExpressionParser(cleaned).parse();
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-10) return Long.toString(Math.round(value));
        return String.format(Locale.US, "%.10f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String formatDuration(long ms) {
        long minutes = Math.max(0L, ms / 60000L);
        if (minutes < 60) return minutes + " min";
        return (minutes / 60L) + "h " + String.format(Locale.US, "%02dm", minutes % 60L);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(rounded(0xFFFFFFFF, 18));
        card.setElevation(dp(1));
        return card;
    }

    private LinearLayout.LayoutParams bottomMargin(int value) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, value);
        return lp;
    }

    private LinearLayout.LayoutParams topMargin(int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, top, 0, bottom);
        return lp;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }

    private Button button(String label, boolean dark) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setTextColor(dark ? Color.WHITE : 0xFF182339);
        b.setBackground(rounded(dark ? 0xFF2A3854 : Color.WHITE, 12));
        return b;
    }

    private android.graphics.drawable.GradientDrawable rounded(int color, float radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), color == Color.WHITE ? 0xFFE2E5EB : color);
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class ExpressionParser {
        private final String source;
        private int index;

        ExpressionParser(String source) {
            this.source = source.replaceAll("\\s+", "");
        }

        double parse() {
            double value = expression();
            if (index != source.length()) throw new IllegalArgumentException();
            return value;
        }

        private double expression() {
            double value = term();
            while (index < source.length()) {
                char op = source.charAt(index);
                if (op != '+' && op != '-') break;
                index++;
                double rhs = term();
                value = op == '+' ? value + rhs : value - rhs;
            }
            return value;
        }

        private double term() {
            double value = power();
            while (index < source.length()) {
                char op = source.charAt(index);
                if (op != '*' && op != '/') break;
                index++;
                double rhs = power();
                if (op == '/' && Math.abs(rhs) < 1e-15) throw new ArithmeticException();
                value = op == '*' ? value * rhs : value / rhs;
            }
            return value;
        }

        private double power() {
            double base = unary();
            if (index < source.length() && source.charAt(index) == '^') {
                index++;
                base = Math.pow(base, power());
            }
            return base;
        }

        private double unary() {
            if (index < source.length() && source.charAt(index) == '+') {
                index++;
                return unary();
            }
            if (index < source.length() && source.charAt(index) == '-') {
                index++;
                return -unary();
            }
            return primary();
        }

        private double primary() {
            if (index >= source.length()) throw new IllegalArgumentException();
            if (source.charAt(index) == '(') {
                index++;
                double value = expression();
                if (index >= source.length() || source.charAt(index) != ')') throw new IllegalArgumentException();
                index++;
                return value;
            }
            if (source.startsWith("sqrt(", index)) {
                index += 5;
                double value = expression();
                if (index >= source.length() || source.charAt(index) != ')') throw new IllegalArgumentException();
                index++;
                if (value < 0) throw new ArithmeticException();
                return Math.sqrt(value);
            }
            int start = index;
            boolean dot = false;
            while (index < source.length()) {
                char ch = source.charAt(index);
                if (Character.isDigit(ch)) index++;
                else if (ch == '.' && !dot) {
                    dot = true;
                    index++;
                } else break;
            }
            if (start == index) throw new IllegalArgumentException();
            return Double.parseDouble(source.substring(start, index));
        }
    }
}
