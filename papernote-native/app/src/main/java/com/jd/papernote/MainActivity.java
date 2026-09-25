package com.jd.papernote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private NotebookStore store;
    private LinearLayout notebookList;
    private TextView statsLabel;
    private EditText search;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        store = new NotebookStore(this);

        Window window = getWindow();
        window.setStatusBarColor(0xFF182339);
        window.setNavigationBarColor(0xFF182339);

        // Keep first launch useful without creating a noisy tutorial or account wall.
        if (store.list().isEmpty()) {
            try {
                store.create("Math Practice", "Mathematics", PaperCanvasView.PAPER_GRAPH);
            } catch (Exception e) {
                Toast.makeText(this, "Could not create the starter notebook.", Toast.LENGTH_LONG).show();
            }
        }

        buildHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null && notebookList != null) refreshNotebookList();
    }

    private void buildHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F7FB);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(20), dp(20), dp(18));
        header.setBackgroundColor(0xFF182339);
        header.setElevation(dp(4));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);

        brand.addView(text("PaperNote", 29, Color.WHITE, true));
        brand.addView(text(
                "A calm handwriting-first study notebook.",
                12, 0xFFC9D2E1, false
        ));

        headerRow.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));

        Button about = button("ABOUT", false);
        about.setTextColor(Color.WHITE);
        about.setBackground(rounded(0xFF293750, 12));
        about.setOnClickListener(v -> showAbout());
        headerRow.addView(about);

        header.addView(headerRow);

        statsLabel = text("0 notebooks  •  0 pages", 11, 0xFFE6EBF4, true);
        statsLabel.setPadding(0, dp(12), 0, 0);
        header.addView(statsLabel);
        root.addView(header);

        LinearLayout topActions = new LinearLayout(this);
        topActions.setGravity(Gravity.CENTER_VERTICAL);
        topActions.setPadding(dp(16), dp(15), dp(16), dp(8));

        Button create = primaryButton("+ New notebook");
        create.setOnClickListener(v -> showNewNotebookDialog(null, null, PaperCanvasView.PAPER_RULED));
        topActions.addView(create, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button restore = button("RESTORE", false);
        restore.setOnClickListener(v -> chooseRestoreFile());
        LinearLayout.LayoutParams restoreLp = new LinearLayout.LayoutParams(dp(102), dp(48));
        restoreLp.setMargins(dp(10), 0, 0, 0);
        topActions.addView(restore, restoreLp);
        root.addView(topActions);

        LinearLayout aiCard = new LinearLayout(this);
        aiCard.setGravity(Gravity.CENTER_VERTICAL);
        aiCard.setPadding(dp(15), dp(14), dp(15), dp(14));
        aiCard.setBackground(rounded(0xFFEEF2FF, 18));
        aiCard.setElevation(dp(1));

        LinearLayout aiText = new LinearLayout(this);
        aiText.setOrientation(LinearLayout.VERTICAL);
        aiText.addView(text("PAPERNOTE AI", 11, 0xFF405DE6, true));
        aiText.addView(text("Free offline study tutor", 17, 0xFF182339, true));
        aiText.addView(text(
                "Ask • solve • quiz • revise • plan",
                12, 0xFF5B6473, false
        ));
        aiCard.addView(aiText, new LinearLayout.LayoutParams(0, -2, 1f));

        Button askAi = primaryButton("ASK AI");
        askAi.setOnClickListener(v -> {
            Intent intent = new Intent(this, AiAssistantActivity.class);
            startActivity(intent);
        });
        aiCard.addView(askAi, new LinearLayout.LayoutParams(dp(86), dp(44)));

        LinearLayout.LayoutParams aiLp = new LinearLayout.LayoutParams(-1, -2);
        aiLp.setMargins(dp(16), dp(4), dp(16), dp(12));
        root.addView(aiCard, aiLp);

        TextView section = text("MY NOTEBOOKS", 11, 0xFF7A8495, true);
        section.setPadding(dp(17), dp(2), dp(17), dp(7));
        root.addView(section);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setHint("Search notebooks or subjects");
        search.setPadding(dp(15), 0, dp(15), 0);
        search.setBackground(rounded(Color.WHITE, 14));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(46));
        searchLp.setMargins(dp(16), 0, dp(16), dp(9));
        root.addView(search, searchLp);

        HorizontalScrollView presetsScroll = new HorizontalScrollView(this);
        presetsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presets = new LinearLayout(this);
        presets.setPadding(dp(16), 0, dp(16), dp(10));
        addPreset(presets, "Math", "Mathematics", PaperCanvasView.PAPER_GRAPH);
        addPreset(presets, "Physics", "Physics", PaperCanvasView.PAPER_RULED);
        addPreset(presets, "Chemistry", "Chemistry", PaperCanvasView.PAPER_RULED);
        addPreset(presets, "Blank", "General Study", PaperCanvasView.PAPER_BLANK);
        presetsScroll.addView(presets);
        root.addView(presetsScroll);

        ScrollView listScroll = new ScrollView(this);
        listScroll.setFillViewport(true);

        notebookList = new LinearLayout(this);
        notebookList.setOrientation(LinearLayout.VERTICAL);
        notebookList.setPadding(dp(16), 0, dp(16), dp(18));
        listScroll.addView(notebookList);

        root.addView(listScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshNotebookList();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        refreshNotebookList();
    }

    private void refreshNotebookList() {
        if (notebookList == null) return;

        String query = search == null
                ? ""
                : search.getText().toString().trim().toLowerCase(Locale.ROOT);

        List<NotebookStore.NotebookMeta> notebooks = store.list();
        int totalPages = 0;
        notebookList.removeAllViews();

        for (NotebookStore.NotebookMeta notebook : notebooks) {
            totalPages += notebook.pages.size();

            if (!query.isEmpty()
                    && !notebook.title.toLowerCase(Locale.ROOT).contains(query)
                    && !notebook.subject.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }

            addNotebookCard(notebookList, notebook);
        }

        if (notebookList.getChildCount() == 0) {
            LinearLayout empty = card();
            empty.addView(text(
                    query.isEmpty()
                            ? "No notebooks yet. Create one to start writing."
                            : "No notebook matches that search.",
                    14, 0xFF667085, false
            ));
            notebookList.addView(empty);
        }

        statsLabel.setText(
                notebooks.size() + " notebook"
                        + (notebooks.size() == 1 ? "" : "s")
                        + "  •  " + totalPages + " saved page"
                        + (totalPages == 1 ? "" : "s")
        );
    }

    private void addNotebookCard(LinearLayout parent, NotebookStore.NotebookMeta notebook) {
        LinearLayout card = card();

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(notebook.title, 18, 0xFF182339, true));
        labels.addView(text(
                notebook.subject + "  •  " + notebook.pages.size()
                        + " page" + (notebook.pages.size() == 1 ? "" : "s"),
                12, 0xFF6B7280, false
        ));

        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        Button open = button("OPEN", false);
        open.setOnClickListener(v -> openNotebook(notebook.id));
        row.addView(open, new LinearLayout.LayoutParams(dp(80), dp(42)));

        card.addView(row);
        card.setOnClickListener(v -> openNotebook(notebook.id));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(9));
        parent.addView(card, cardLp);
    }

    private void showNewNotebookDialog(String presetTitle, String presetSubject, String presetPaper) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(19), dp(4), dp(19), 0);

        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setHint("Notebook title");
        if (presetTitle != null) title.setText(presetTitle);

        EditText subject = new EditText(this);
        subject.setSingleLine(true);
        subject.setHint("Subject");
        if (presetSubject != null) subject.setText(presetSubject);

        box.addView(title);
        box.addView(subject);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("New notebook")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    try {
                        NotebookStore.NotebookMeta created = store.create(
                                title.getText().toString(),
                                subject.getText().toString(),
                                presetPaper == null
                                        ? PaperCanvasView.PAPER_RULED
                                        : presetPaper
                        );
                        dialog.dismiss();
                        openNotebook(created.id);
                    } catch (Exception e) {
                        toast("Could not create notebook.");
                    }
                })
        );
        dialog.show();
    }

    private void addPreset(LinearLayout parent, String title, String subject, String paper) {
        Button b = button(title, false);
        b.setOnClickListener(v -> showNewNotebookDialog(
                title + " Notes", subject, paper));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(90), dp(40));
        lp.setMargins(0, 0, dp(8), 0);
        parent.addView(b, lp);
    }

    private void openNotebook(String id) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("notebook_id", id);
        intent.putExtra("page_index", 0);
        startActivity(intent);
    }

    private void chooseRestoreFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, 504);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 504 || resultCode != RESULT_OK || data == null
                || data.getData() == null) return;

        try {
            StringBuilder builder = new StringBuilder();
            try (java.io.InputStream in = getContentResolver().openInputStream(data.getData());
                 java.io.BufferedReader reader = new java.io.BufferedReader(
                         new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
                if (in == null) throw new Exception("Could not open backup.");
                String line;
                while ((line = reader.readLine()) != null) builder.append(line);
            }

            NotebookStore.NotebookMeta restored = store.importBackup(builder.toString());
            toast("Restored: " + restored.title);
            refreshNotebookList();
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("Restore failed")
                    .setMessage(e.getMessage() == null
                            ? "The backup could not be restored." : e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("About PaperNote")
                .setMessage(
                        "PaperNote is a handwriting-first study notebook.

" +
                        "• Local notebooks and page data
" +
                        "• No account or cloud requirement
" +
                        "• Free offline PaperNote AI on supported ARM64 devices
" +
                        "• Export and backup are user-controlled

" +
                        "The editor is designed to stay lightweight while writing."
                )
                .setPositiveButton("OK", null)
                .show();
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(13), dp(15), dp(13));
        box.setBackground(rounded(Color.WHITE, 18));
        box.setElevation(dp(1));
        return box;
    }

    private Button primaryButton(String label) {
        Button b = button(label, false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setBackground(rounded(0xFF405DE6, 12));
        return b;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(dp(9), 0, dp(9), 0);
        b.setTextColor(primary ? Color.WHITE : 0xFF182339);
        b.setBackground(rounded(primary ? 0xFF405DE6 : Color.WHITE, 12));
        return b;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD);
        }
        return t;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), color == Color.WHITE ? 0xFFE2E5EB : color);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
