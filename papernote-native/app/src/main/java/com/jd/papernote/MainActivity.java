package com.jd.papernote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_RESTORE = 504;

    private NotebookStore store;
    private LinearLayout notebookList;
    private TextView notebookCount;
    private TextView pageCount;
    private TextView emptyState;
    private ArrayList<NotebookStore.NotebookMeta> cachedNotebooks = new ArrayList<>();

    private static final int NAVY = Color.rgb(24, 35, 57);
    private static final int PRIMARY = Color.rgb(67, 88, 220);
    private static final int BG = Color.rgb(246, 248, 252);
    private static final int TEXT = Color.rgb(24, 35, 57);
    private static final int MUTED = Color.rgb(103, 114, 132);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new NotebookStore(this);

        Window window = getWindow();
        window.setStatusBarColor(NAVY);
        window.setNavigationBarColor(NAVY);

        ensureStarterNotebook();
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store == null) return;
        if (notebookList != null) refreshNotebookList("");
    }

    private void ensureStarterNotebook() {
        if (!store.list().isEmpty()) return;
        try {
            store.create("Math Practice", "Mathematics", PaperCanvasView.PAPER_GRAPH);
        } catch (Exception e) {
            Toast.makeText(this, "Could not create starter notebook", Toast.LENGTH_LONG).show();
        }
    }

    private void showHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.addView(buildHeader());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(16), dp(14), dp(16), dp(8));

        Button newNotebook = primaryButton("+ New notebook");
        newNotebook.setOnClickListener(v -> showNewNotebookDialog(null, null, PaperCanvasView.PAPER_RULED));
        actions.addView(newNotebook, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button restore = secondaryButton("Restore");
        restore.setOnClickListener(v -> chooseRestoreFile());
        LinearLayout.LayoutParams restoreLp = new LinearLayout.LayoutParams(dp(104), dp(48));
        restoreLp.setMargins(dp(10), 0, 0, 0);
        actions.addView(restore, restoreLp);

        content.addView(actions);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setHint("Search notebooks or subjects");
        search.setTextColor(TEXT);
        search.setHintTextColor(0xFF8992A2);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(rounded(Color.WHITE, 16, 0xFFE1E5EC));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(50));
        searchLp.setMargins(dp(16), dp(4), dp(16), dp(10));
        content.addView(search, searchLp);

        TextView section = text("MY NOTEBOOKS", 11, MUTED, true);
        section.setPadding(dp(17), 0, dp(17), dp(8));
        content.addView(section);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        notebookList = new LinearLayout(this);
        notebookList.setOrientation(LinearLayout.VERTICAL);
        notebookList.setPadding(dp(16), 0, dp(16), dp(24));

        emptyState = text(
                "Your notebooks will appear here. Create a notebook to start writing.",
                14, MUTED, false
        );
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(dp(24), dp(48), dp(24), dp(48));

        scroll.addView(notebookList);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        refreshNotebookList("");

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshNotebookList(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(18), dp(16), dp(17));
        header.setBackgroundColor(NAVY);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brandBox = new LinearLayout(this);
        brandBox.setOrientation(LinearLayout.VERTICAL);

        TextView brand = text("PaperNote", 29, Color.WHITE, true);
        TextView tagline = text(
                "Simple handwriting. Reliable notes.",
                13, 0xFFD6DCE8, false
        );
        brandBox.addView(brand);
        brandBox.addView(tagline);
        top.addView(brandBox, new LinearLayout.LayoutParams(0, -2, 1f));

        Button about = smallDarkButton("ABOUT");
        about.setOnClickListener(v -> showAbout());
        top.addView(about);

        header.addView(top);

        LinearLayout stats = new LinearLayout(this);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.setPadding(0, dp(14), 0, 0);

        notebookCount = text("0 notebooks", 12, 0xFFF0F3F8, true);
        pageCount = text("0 saved pages", 12, 0xFFF0F3F8, true);

        stats.addView(notebookCount, new LinearLayout.LayoutParams(0, -2, 1f));
        stats.addView(pageCount, new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(stats);

        return header;
    }

    private void refreshNotebookList(String rawQuery) {
        if (notebookList == null) return;

        cachedNotebooks = new ArrayList<>(store.list());

        String query = rawQuery == null
                ? ""
                : rawQuery.trim().toLowerCase(Locale.ROOT);

        notebookList.removeAllViews();

        int totalPages = 0;
        int visible = 0;

        for (NotebookStore.NotebookMeta notebook : cachedNotebooks) {
            totalPages += notebook.pages.size();

            String title = notebook.title == null ? "" : notebook.title;
            String subject = notebook.subject == null ? "" : notebook.subject;

            if (!query.isEmpty()
                    && !title.toLowerCase(Locale.ROOT).contains(query)
                    && !subject.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }

            addNotebookCard(notebookList, notebook);
            visible++;
        }

        if (notebookCount != null) {
            notebookCount.setText(
                    cachedNotebooks.size() + (cachedNotebooks.size() == 1 ? " notebook" : " notebooks")
            );
        }
        if (pageCount != null) {
            pageCount.setText(
                    totalPages + (totalPages == 1 ? " saved page" : " saved pages")
            );
        }

        if (visible == 0) {
            notebookList.addView(emptyState);
            emptyState.setText(
                    query.isEmpty()
                            ? "Your notebooks will appear here. Create a notebook to start writing."
                            : "No notebook matches your search."
            );
        }
    }

    private void addNotebookCard(LinearLayout parent, NotebookStore.NotebookMeta notebook) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(14), dp(14));
        card.setBackground(rounded(Color.WHITE, 18, 0xFFE2E6ED));
        card.setElevation(dp(1));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);

        TextView title = text(notebook.title, 18, TEXT, true);
        TextView subject = text(
                notebook.subject + "  •  " + notebook.pages.size()
                        + (notebook.pages.size() == 1 ? " page" : " pages"),
                13, MUTED, false
        );

        textBox.addView(title);
        textBox.addView(subject);

        String updated = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT
        ).format(new Date(notebook.updatedAt));

        TextView time = text("Updated " + updated, 11, 0xFF8992A2, false);
        time.setPadding(0, dp(5), 0, 0);
        textBox.addView(time);

        row.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1f));

        Button open = secondaryButton("Open");
        open.setOnClickListener(v -> openNotebook(notebook.id));
        row.addView(open, new LinearLayout.LayoutParams(dp(80), dp(44)));

        card.addView(row);

        card.setOnClickListener(v -> openNotebook(notebook.id));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(10));
        parent.addView(card, cardLp);
    }

    private void showNewNotebookDialog(
            String presetTitle,
            String presetSubject,
            String presetPaper
    ) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(4), dp(22), 0);

        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setHint("Notebook title");
        title.setText(presetTitle == null ? "" : presetTitle);

        EditText subject = new EditText(this);
        subject.setSingleLine(true);
        subject.setHint("Subject");
        subject.setText(presetSubject == null ? "" : presetSubject);

        TextView helper = text(
                "You can change the paper style anytime from the editor.",
                12, MUTED, false
        );
        helper.setPadding(0, dp(5), 0, 0);

        box.addView(title);
        box.addView(subject);
        box.addView(helper);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Create notebook")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String titleValue = title.getText().toString().trim();
                    String subjectValue = subject.getText().toString().trim();

                    if (titleValue.isEmpty()) titleValue = "New Notebook";
                    if (subjectValue.isEmpty()) subjectValue = "General Study";

                    try {
                        NotebookStore.NotebookMeta notebook = store.create(
                                titleValue,
                                subjectValue,
                                presetPaper == null
                                        ? PaperCanvasView.PAPER_RULED
                                        : presetPaper
                        );
                        dialog.dismiss();
                        openNotebook(notebook.id);
                    } catch (Exception e) {
                        Toast.makeText(
                                this,
                                "Could not create notebook",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
        );

        dialog.show();
    }

    private void openNotebook(String notebookId) {
        try {
            Intent intent = new Intent(this, EditorActivity.class);
            intent.putExtra("notebook_id", notebookId);
            intent.putExtra("page_index", 0);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open notebook", Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseRestoreFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_RESTORE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_RESTORE || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }

        try {
            android.net.Uri uri = data.getData();
            StringBuilder builder = new StringBuilder();

            try (
                    java.io.InputStream in = getContentResolver().openInputStream(uri);
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(
                                    in,
                                    java.nio.charset.StandardCharsets.UTF_8
                            )
                    )
            ) {
                if (in == null) throw new Exception("Could not open backup");
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }

            store.importBackup(builder.toString());
            Toast.makeText(this, "Backup restored", Toast.LENGTH_SHORT).show();
            refreshNotebookList("");
        } catch (Exception e) {
            Toast.makeText(this, "Could not restore backup", Toast.LENGTH_LONG).show();
        }
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("About PaperNote")
                .setMessage(
                        "PaperNote is an offline-first handwriting notebook.\n\n"
                                + "Your notebooks stay on this device unless you export or back them up yourself. "
                                + "No account is required."
                )
                .setPositiveButton("OK", null)
                .show();
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(PRIMARY, 14, PRIMARY));
        b.setPadding(dp(12), 0, dp(12), 0);
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setTextColor(TEXT);
        b.setBackground(rounded(Color.WHITE, 12, 0xFFE2E6ED));
        b.setPadding(dp(11), 0, dp(11), 0);
        return b;
    }

    private Button smallDarkButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(0xFF2B3853, 12, 0xFF3A4966));
        b.setPadding(dp(10), 0, dp(10), 0);
        return b;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        }
        return t;
    }

    private GradientDrawable rounded(int color, float radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), strokeColor);
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
