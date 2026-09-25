package com.jd.papernote;

import android.app.Activity;
import android.app.AlertDialog;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private NotebookStore store;
    private ArrayList<NotebookStore.NotebookMeta> notebooks = new ArrayList<>();
    private LinearLayout notebookList;
    private EditText search;
    private TextView headerStats;
    private String lastNotebookId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new NotebookStore(this);
        lastNotebookId = getPreferences(MODE_PRIVATE).getString("last_notebook_id", null);
        refreshNotebooks();
        buildHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store == null) return;
        refreshNotebooks();
        if (notebookList != null) renderNotebooks(search == null ? "" : search.getText().toString());
        if (headerStats != null) headerStats.setText(buildStats());
    }

    private void refreshNotebooks() {
        notebooks = new ArrayList<>(store.list());
    }

    private void buildHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F7FB);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(20), dp(20), dp(17));
        header.setBackgroundColor(0xFF182339);
        header.setElevation(dp(5));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(text("PaperNote", 29, Color.WHITE, true));
        brand.addView(text("Handwrite. Understand. Revise.", 13, 0xFFC9D2E1, false));
        titleRow.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));

        Button about = topButton("ABOUT");
        about.setOnClickListener(v -> showAbout());
        titleRow.addView(about, new LinearLayout.LayoutParams(dp(76), dp(42)));
        header.addView(titleRow);

        headerStats = text(buildStats(), 12, 0xFFE7ECF4, true);
        headerStats.setPadding(0, dp(13), 0, 0);
        header.addView(headerStats);

        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(24));

        LinearLayout aiCard = card(0xFF202E4A);
        LinearLayout aiTop = new LinearLayout(this);
        aiTop.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout aiText = new LinearLayout(this);
        aiText.setOrientation(LinearLayout.VERTICAL);
        aiText.addView(text("PaperNote AI", 20, Color.WHITE, true));
        aiText.addView(text("Free on-device study assistant. No account, no API key, no cloud.", 12, 0xFFD7DFEC, false));
        aiTop.addView(aiText, new LinearLayout.LayoutParams(0, -2, 1f));
        Button openAi = pillButton("OPEN AI", true);
        openAi.setOnClickListener(v -> openAi(null, 0));
        aiTop.addView(openAi);
        aiCard.addView(aiTop);
        TextView aiActions = text("Explain • Quiz me • Flashcards • Study plan • Simplify • Exam answer", 11, 0xFFC5D0E1, false);
        aiActions.setPadding(0, dp(9), 0, 0);
        aiCard.addView(aiActions);
        body.addView(aiCard, bottomMargin(dp(11)));

        body.addView(section("QUICK START"));

        LinearLayout quickRow1 = new LinearLayout(this);
        quickRow1.setGravity(Gravity.CENTER_VERTICAL);
        addQuick(quickRow1, "+ New notebook", true, v -> showNewNotebookDialog());
        addQuick(quickRow1, "Continue", false, v -> continueLastNotebook());
        body.addView(quickRow1, bottomMargin(dp(8)));

        LinearLayout quickRow2 = new LinearLayout(this);
        quickRow2.setGravity(Gravity.CENTER_VERTICAL);
        addQuick(quickRow2, "Import PDF", false, v -> importPdfFlow());
        addQuick(quickRow2, "Study Workspace", false,
                v -> startActivity(new Intent(this, StudyWorkspaceActivity.class)));
        body.addView(quickRow2, bottomMargin(dp(12)));

        body.addView(section("SEARCH NOTEBOOKS"));
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search title or subject");
        search.setTextSize(14);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setBackground(rounded(Color.WHITE, 15));
        search.setPadding(dp(14), 0, dp(14), 0);
        body.addView(search, bottomMargin(dp(12)));

        LinearLayout studyRow = new LinearLayout(this);
        studyRow.setGravity(Gravity.CENTER_VERTICAL);
        Button hub = actionButton("Study Hub");
        hub.setOnClickListener(v -> startActivity(new Intent(this, StudyHubActivity.class)));
        studyRow.addView(hub, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button ai = actionButton("AI Assistant");
        ai.setOnClickListener(v -> openAi(null, 0));
        LinearLayout.LayoutParams aip = new LinearLayout.LayoutParams(0, dp(44), 1f);
        aip.setMargins(dp(8), 0, 0, 0);
        studyRow.addView(ai, aip);
        body.addView(studyRow, bottomMargin(dp(14)));

        body.addView(section("RECENT NOTEBOOKS"));
        notebookList = new LinearLayout(this);
        notebookList.setOrientation(LinearLayout.VERTICAL);
        body.addView(notebookList);

        if (notebooks.isEmpty()) {
            LinearLayout empty = card(Color.WHITE);
            empty.addView(text("Your first notebook is one tap away.", 17, 0xFF182339, true));
            empty.addView(text(
                    "PaperNote keeps notebooks on this device. Create a notebook, write naturally, and use Study Workspace when you need a revision system.",
                    12, 0xFF667085, false));
            Button create = actionButton("+ Create notebook");
            create.setOnClickListener(v -> showNewNotebookDialog());
            empty.addView(create, topMargin(dp(10), 0));
            notebookList.addView(empty);
        } else {
            renderNotebooks("");
        }

        TextView footer = text(
                "Offline-first • local notebooks • user-controlled backups • no required account",
                11, 0xFF7A8495, false);
        footer.setPadding(dp(4), dp(14), dp(4), 0);
        body.addView(footer);

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderNotebooks(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        header.setAlpha(0f);
        header.setTranslationY(-dp(10));
        header.animate().alpha(1f).translationY(0f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private String buildStats() {
        int pages = 0;
        int due = 0;
        for (NotebookStore.NotebookMeta n : notebooks) {
            pages += n.pages.size();
            due += store.getDueStudyMarks(n).size();
        }
        return notebooks.size() + " notebooks   •   " + pages + " pages   •   " + due + " due review items";
    }

    private void renderNotebooks(String rawQuery) {
        if (notebookList == null) return;
        notebookList.removeAllViews();
        String q = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);

        int shown = 0;
        for (NotebookStore.NotebookMeta notebook : notebooks) {
            if (!q.isEmpty()
                    && !notebook.title.toLowerCase(Locale.ROOT).contains(q)
                    && !notebook.subject.toLowerCase(Locale.ROOT).contains(q)) continue;
            addNotebookCard(notebookList, notebook);
            shown++;
        }

        if (shown == 0) {
            LinearLayout empty = card(Color.WHITE);
            empty.addView(text("No matching notebook", 16, 0xFF182339, true));
            empty.addView(text("Try another title or subject.", 12, 0xFF667085, false));
            notebookList.addView(empty);
        }
    }

    private void addNotebookCard(LinearLayout parent, NotebookStore.NotebookMeta notebook) {
        LinearLayout card = card(Color.WHITE);
        card.setOnClickListener(v -> openNotebook(notebook.id, 0));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(notebook.title, 18, 0xFF182339, true));

        int open = store.countStudyMarks(notebook, null, true);
        int due = store.getDueStudyMarks(notebook).size();
        String meta = notebook.subject + "  •  " + notebook.pages.size()
                + (notebook.pages.size() == 1 ? " page" : " pages");
        if (open > 0) meta += "  •  " + open + " open";
        if (due > 0) meta += "  •  " + due + " due";
        labels.addView(text(meta, 12, 0xFF667085, false));
        top.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView arrow = text("›", 27, 0xFF7A8495, true);
        arrow.setGravity(Gravity.CENTER);
        top.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(42)));
        card.addView(top);

        if (notebook.id.equals(lastNotebookId)) {
            TextView last = text("LAST OPENED", 9, 0xFF4169E1, true);
            last.setPadding(0, dp(7), 0, 0);
            card.addView(last);
        }

        parent.addView(card, bottomMargin(dp(9)));
    }

    private void continueLastNotebook() {
        if (lastNotebookId != null) {
            try {
                store.get(lastNotebookId);
                openNotebook(lastNotebookId, 0);
                return;
            } catch (Exception ignored) {
            }
        }
        if (!notebooks.isEmpty()) {
            openNotebook(notebooks.get(0).id, 0);
        } else {
            showNewNotebookDialog();
        }
    }

    private void importPdfFlow() {
        if (notebooks.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Create a notebook first")
                    .setMessage("Create or restore a notebook, then use its editor to import a PDF as editable PaperNote pages.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Create notebook", (d, w) -> showNewNotebookDialog())
                    .show();
        } else {
            openNotebook(notebooks.get(0).id, 0, "import_pdf");
        }
    }

    private void openNotebook(String id, int pageIndex) {
        openNotebook(id, pageIndex, null);
    }

    private void openNotebook(String id, int pageIndex, String feature) {
        lastNotebookId = id;
        getPreferences(MODE_PRIVATE).edit().putString("last_notebook_id", id).apply();
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("notebook_id", id);
        intent.putExtra("page_index", pageIndex);
        if (feature != null) intent.putExtra("open_feature", feature);
        startActivity(intent);
    }

    private void openAi(String id, int pageIndex) {
        Intent intent = new Intent(this, AiAssistantActivity.class);
        if (id != null) intent.putExtra("notebook_id", id);
        intent.putExtra("page_index", pageIndex);
        startActivity(intent);
    }

    private void showNewNotebookDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(5), dp(20), 0);

        EditText title = new EditText(this);
        title.setHint("Notebook title");
        title.setSingleLine(true);
        box.addView(title);

        EditText subject = new EditText(this);
        subject.setHint("Subject");
        subject.setSingleLine(true);
        box.addView(subject);

        String[] templates = {"Ruled", "Graph", "Dot Grid", "Blank", "Math Practice", "Experiment", "Cornell Notes", "Problem → Solution", "Formula Sheet", "Flashcard Grid"};
        final String[] paper = {
                PaperCanvasView.PAPER_RULED,
                PaperCanvasView.PAPER_GRAPH,
                PaperCanvasView.PAPER_DOT,
                PaperCanvasView.PAPER_BLANK,
                PaperCanvasView.PAPER_MATH,
                PaperCanvasView.PAPER_EXPERIMENT,
                PaperCanvasView.PAPER_CORNELL,
                PaperCanvasView.PAPER_PROBLEM,
                PaperCanvasView.PAPER_FORMULA,
                PaperCanvasView.PAPER_FLASHCARDS
        };

        final int[] selected = {0};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("New notebook")
                .setView(box)
                .setSingleChoiceItems(templates, 0, (d, which) -> selected[0] = which)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                NotebookStore.NotebookMeta created = store.create(
                        title.getText().toString(),
                        subject.getText().toString(),
                        paper[selected[0]]);
                dialog.dismiss();
                refreshNotebooks();
                headerStats.setText(buildStats());
                openNotebook(created.id, 0);
            } catch (Exception e) {
                Toast.makeText(this, "Could not create notebook", Toast.LENGTH_SHORT).show();
            }
        }));
        dialog.show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("About PaperNote")
                .setMessage(
                        "PaperNote is a handwriting-first study notebook built around local storage and practical revision workflows.\n\n" +
                        "PaperNote AI is optional and runs a compact language model on-device. It does not require an account or API key.\n\n" +
                        "Backups and exports are controlled by you.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void addQuick(LinearLayout row, String label, boolean primary, View.OnClickListener listener) {
        Button b = primary ? pillButton(label, true) : actionButton(label);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        row.addView(b, lp);
    }

    private Button actionButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setTextColor(0xFF182339);
        b.setBackground(rounded(Color.WHITE, 13));
        return b;
    }

    private Button pillButton(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setTextColor(primary ? Color.WHITE : 0xFF182339);
        b.setBackground(rounded(primary ? 0xFF4169E1 : Color.WHITE, 13));
        return b;
    }

    private Button topButton(String label) {
        Button b = topButtonBase(label);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(0xFF2A3854, 12));
        return b;
    }

    private Button topButtonBase(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(10);
        b.setMinHeight(0);
        b.setMinWidth(0);
        return b;
    }

    private TextView section(String label) {
        TextView t = text(label, 11, 0xFF7A8495, true);
        t.setPadding(dp(3), dp(2), dp(3), dp(7));
        return t;
    }

    private LinearLayout card(int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(13), dp(15), dp(13));
        box.setBackground(rounded(color, 17));
        box.setElevation(dp(1));
        return box;
    }

    private LinearLayout.LayoutParams bottomMargin(int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, bottom);
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

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), color == Color.WHITE ? 0xFFE0E4EA : color);
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
