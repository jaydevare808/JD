package com.jd.papernote;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public final class StudyWorkspaceActivity extends Activity {
    private NotebookStore store;
    private ArrayList<NotebookStore.NotebookMeta> notebooks = new ArrayList<>();
    private Spinner notebookSpinner;
    private Spinner filterSpinner;
    private EditText search;
    private TextView summary;
    private LinearLayout queue;
    private LinearLayout attention;

    private final String[] filters = {"All", "Doubts", "Mistakes", "Important", "Revise", "Due now"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new NotebookStore(this);
        refreshNotebooks();
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store == null) return;
        refreshNotebooks();
        rebuildNotebookSpinner();
        renderAll();
    }

    private void refreshNotebooks() {
        notebooks = new ArrayList<>(store.list());
    }

    private void rebuildNotebookSpinner() {
        if (notebookSpinner == null) return;
        String wanted = getIntent().getStringExtra("notebook_id");
        ArrayList<String> names = new ArrayList<>();
        for (NotebookStore.NotebookMeta notebook : notebooks) {
            names.add(notebook.title + "  •  " + notebook.subject);
        }
        if (names.isEmpty()) names.add("No notebooks yet");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, names);
        notebookSpinner.setAdapter(adapter);
        int selected = -1;
        if (wanted != null) {
            for (int i = 0; i < notebooks.size(); i++) {
                if (wanted.equals(notebooks.get(i).id)) {
                    selected = i;
                    break;
                }
            }
        }
        if (selected < 0 && !notebooks.isEmpty()) selected = 0;
        if (selected >= 0) notebookSpinner.setSelection(selected, false);
    }

    private NotebookStore.NotebookMeta selectedNotebook() {
        if (notebooks.isEmpty() || notebookSpinner == null) return null;
        int index = Math.max(0, Math.min(
                notebookSpinner.getSelectedItemPosition(), notebooks.size() - 1));
        return notebooks.get(index);
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F7FB);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(12), dp(14), dp(12));
        header.setBackgroundColor(0xFF182339);
        header.setElevation(dp(4));

        Button back = button("‹", true);
        back.setTextSize(24);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(text("Study Workspace", 21, Color.WHITE, true));
        titleBox.addView(text("Revision queue, page insights and exam tools", 12, 0xFFC9D2E1, false));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.setMargins(dp(8), 0, 0, 0);
        header.addView(titleBox, titleParams);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(28));

        body.addView(sectionLabel("NOTEBOOK"));
        notebookSpinner = new Spinner(this);
        body.addView(notebookSpinner, bottomMargin(dp(8)));
        rebuildNotebookSpinner();
        notebookSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                renderAll();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {
                renderAll();
            }
        });

        summary = text("", 13, 0xFF4F5D70, false);
        summary.setBackground(rounded(Color.WHITE, 16));
        summary.setPadding(dp(14), dp(12), dp(14), dp(12));
        body.addView(summary, bottomMargin(dp(11)));

        body.addView(sectionLabel("QUICK VIEWS"));
        LinearLayout quick1 = new LinearLayout(this);
        addQuick(quick1, "Doubts", "marks_view");
        addQuick(quick1, "Mistakes", "marks_view");
        addQuick(quick1, "Recall", "recall");
        addQuick(quick1, "Normal", "");
        body.addView(quick1, bottomMargin(dp(7)));

        LinearLayout quick2 = new LinearLayout(this);
        addQuick(quick2, "Replay", "replay");
        addQuick(quick2, "Ghost", "ghost");
        addQuick(quick2, "Exam", "exam");
        addQuick(quick2, "Experiment", "experiment");
        body.addView(quick2, bottomMargin(dp(12)));

        body.addView(sectionLabel("REVISION QUEUE"));
        LinearLayout filterRow = new LinearLayout(this);
        filterSpinner = new Spinner(this);
        filterSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, filters));
        filterRow.addView(filterSpinner, new LinearLayout.LayoutParams(dp(130), dp(44)));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search page or marker note");
        search.setTextSize(13);
        search.setBackground(rounded(Color.WHITE, 12));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        searchLp.setMargins(dp(8), 0, 0, 0);
        filterRow.addView(search, searchLp);
        body.addView(filterRow, bottomMargin(dp(8)));

        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                renderQueue();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {
                renderQueue();
            }
        });
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderQueue(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        queue = new LinearLayout(this);
        queue.setOrientation(LinearLayout.VERTICAL);
        body.addView(queue, bottomMargin(dp(12)));

        body.addView(sectionLabel("PAGE ATTENTION"));
        attention = new LinearLayout(this);
        attention.setOrientation(LinearLayout.VERTICAL);
        body.addView(attention, bottomMargin(dp(12)));

        body.addView(sectionLabel("FULL FEATURE SET"));
        addFeature(body, "Study markers", "Place D / M / ! / R markers at exact page coordinates and attach review notes.", "marks");
        addFeature(body, "Active recall", "Hide the page for self-testing, then reveal it without changing your saved handwriting.", "recall");
        addFeature(body, "Marks-only view", "Inspect study markers without the handwriting underneath. Tap a marker for details.", "marks_view");
        addFeature(body, "Ghost compare", "Capture an earlier page, show it as an adjustable transparent overlay, or clear it.", "ghost");
        addFeature(body, "Exam answer practice", "Create a timed 2-, 3- or 4-mark answer page with controlled writing space.", "exam");
        addFeature(body, "Science experiment page", "Use a structured experiment sheet for practical work.", "experiment");
        addFeature(body, "Concept threads", "Link related notebook pages and name the relationship between them.", "link");
        addFeature(body, "Handwriting replay", "Replay the current session stroke-by-stroke. The saved page remains recoverable.", "replay");
        addFeature(body, "Quick Share backup", "Create a .papernote backup and send it through Android sharing / Quick Share.", "share");
        addFeature(body, "Notebook export", "Export the selected notebook to PDF, PNG, JPG, WebP or ZIP.", "export");
        addFeature(body, "Scientific calculator", "Evaluate expressions locally, including powers, roots and π.", "calculator");
        addFeature(body, "Focus timer", "Run a local 25-minute focus session without cloud services.", "timer");
        addFeature(body, "Study checklist", "Build a revision checklist and place it on the current page.", "checklist");
        addFeature(body, "Study Hub", "Open progress, timeline and concept-thread summaries.", "hub");
        addFeature(body, "PaperNote AI", "Free on-device tutor for explanations, worked steps, quizzes, revision and study plans.", "ai");

        TextView footer = text(
                "PaperNote remains offline-first. Notes, study markers and analytics stay in local app storage.",
                11, 0xFF7A8495, false);
        footer.setPadding(dp(4), dp(12), dp(4), 0);
        body.addView(footer);

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        renderAll();
    }

    private void renderAll() {
        renderSummary();
        renderQueue();
        renderAttention();
    }

    private void renderSummary() {
        NotebookStore.NotebookMeta n = selectedNotebook();
        if (summary == null) return;
        if (n == null) {
            summary.setText("Create a notebook on Home to use the full Study Workspace.");
            return;
        }
        int open = store.countStudyMarks(n, null, true);
        int due = store.getDueStudyMarks(n).size();
        int doubts = store.countStudyMarks(n, NotebookStore.StudyMark.DOUBT, true);
        int mistakes = store.countStudyMarks(n, NotebookStore.StudyMark.MISTAKE, true);
        int revise = store.countStudyMarks(n, NotebookStore.StudyMark.REVISE, true);
        int important = store.countStudyMarks(n, NotebookStore.StudyMark.IMPORTANT, true);
        long minutes = store.getNotebookActiveMs(n) / 60000L;
        summary.setText(
                n.pages.size() + " pages  •  " + open + " open markers  •  " + due + " due now\n" +
                "Doubt " + doubts + "  •  Mistake " + mistakes + "  •  Important " +
                important + "  •  Revise " + revise + "  •  " + formatDuration(minutes));
    }

    private void renderQueue() {
        if (queue == null) return;
        queue.removeAllViews();
        NotebookStore.NotebookMeta n = selectedNotebook();
        if (n == null) return;
        String filter = filterSpinner == null ? "All" : String.valueOf(filterSpinner.getSelectedItem());
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        ArrayList<QueueRow> rows = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < n.pages.size(); pageIndex++) {
            NotebookStore.PageMeta page = n.pages.get(pageIndex);
            for (NotebookStore.StudyMark mark : store.getStudyMarks(page.id)) {
                if (mark.resolved) continue;
                if ("Doubts".equals(filter) && !NotebookStore.StudyMark.DOUBT.equals(mark.type)) continue;
                if ("Mistakes".equals(filter) && !NotebookStore.StudyMark.MISTAKE.equals(mark.type)) continue;
                if ("Important".equals(filter) && !NotebookStore.StudyMark.IMPORTANT.equals(mark.type)) continue;
                if ("Revise".equals(filter) && !NotebookStore.StudyMark.REVISE.equals(mark.type)) continue;
                if ("Due now".equals(filter) && mark.reviewAt > now) continue;
                String note = mark.note == null ? "" : mark.note;
                String hay = (page.title + " " + note).toLowerCase(Locale.ROOT);
                if (!query.isEmpty() && !hay.contains(query)) continue;
                rows.add(new QueueRow(page, pageIndex, mark));
            }
        }

        rows.sort((a, b) -> {
            int due = Long.compare(a.mark.reviewAt, b.mark.reviewAt);
            return due != 0 ? due : Long.compare(b.mark.createdAt, a.mark.createdAt);
        });

        if (rows.isEmpty()) {
            queue.addView(emptyCard("No matching open study markers."));
            return;
        }

        int limit = Math.min(10, rows.size());
        for (int i = 0; i < limit; i++) {
            QueueRow row = rows.get(i);
            LinearLayout card = card();
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            String due = row.mark.reviewAt <= now ? "DUE" : relativeDue(row.mark.reviewAt);
            top.addView(text(label(row.mark.type) + "  •  " + due, 14, 0xFF182339, true),
                    new LinearLayout.LayoutParams(0, -2, 1f));
            Button open = button("OPEN", false);
            open.setOnClickListener(v -> openEditor("marks_view", row.pageIndex));
            top.addView(open);
            card.addView(top);
            String details = row.page.title + (row.mark.note == null || row.mark.note.isEmpty()
                    ? "" : "  •  " + row.mark.note);
            card.addView(text(details, 12, 0xFF667085, false));
            queue.addView(card, bottomMargin(dp(7)));
        }
        if (rows.size() > 10) {
            queue.addView(text("Showing 10 items. Use filters or search to narrow the queue.",
                    11, 0xFF7A8495, false));
        }
    }

    private void renderAttention() {
        if (attention == null) return;
        attention.removeAllViews();
        NotebookStore.NotebookMeta n = selectedNotebook();
        if (n == null) {
            attention.addView(emptyCard("Select a notebook to see page attention signals."));
            return;
        }

        ArrayList<PageRow> pages = new ArrayList<>();
        for (int i = 0; i < n.pages.size(); i++) {
            NotebookStore.PageMeta page = n.pages.get(i);
            NotebookStore.PageStats stats = store.getPageStats(page.id);
            int open = 0;
            for (NotebookStore.StudyMark mark : store.getStudyMarks(page.id)) {
                if (!mark.resolved) open++;
            }
            int heat = 0;
            for (int v : stats.heatmap) heat += v;
            int score = open * 100 + Math.min(heat, 100);
            if (score > 0) pages.add(new PageRow(page, i, stats, open, score));
        }
        pages.sort((a, b) -> Integer.compare(b.score, a.score));
        if (pages.isEmpty()) {
            attention.addView(emptyCard("Write or add study markers to build page attention signals."));
            return;
        }
        for (int i = 0; i < Math.min(5, pages.size()); i++) {
            PageRow row = pages.get(i);
            LinearLayout card = card();
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(text(row.page.title, 14, 0xFF182339, true),
                    new LinearLayout.LayoutParams(0, -2, 1f));
            Button open = button("OPEN", false);
            open.setOnClickListener(v -> openEditor("", row.pageIndex));
            top.addView(open);
            card.addView(top);
            card.addView(text(
                    row.openMarkers + " open markers  •  " + row.stats.strokes +
                            " strokes  •  " + formatDuration(row.stats.activeMs / 60000L),
                    12, 0xFF667085, false));
            attention.addView(card, bottomMargin(dp(7)));
        }
    }

    private void addQuick(LinearLayout row, String label, String action) {
        Button b = button(label, false);
        b.setOnClickListener(v -> openEditor(action, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        row.addView(b, lp);
    }

    private void addFeature(LinearLayout parent, String title, String description, String action) {
        LinearLayout card = card();
        TextView titleView = text(title, 14, 0xFF182339, true);
        card.addView(titleView);
        TextView desc = text(description, 11, 0xFF667085, false);
        desc.setPadding(0, dp(4), 0, 0);
        card.addView(desc);
        Button open = button("OPEN", false);
        open.setOnClickListener(v -> {
            if ("hub".equals(action)) startActivity(new Intent(this, StudyHubActivity.class));
            else openEditor(action, 0);
        });
        card.addView(open, topMargin(dp(8), 0));
        parent.addView(card, bottomMargin(dp(7)));
    }

    private void openEditor(String feature, int pageIndex) {
        NotebookStore.NotebookMeta n = selectedNotebook();
        if ("ai".equals(feature)) {
            Intent intent = new Intent(this, AiAssistantActivity.class);
            if (n != null) {
                intent.putExtra("notebook_id", n.id);
                intent.putExtra("page_index", Math.max(0, Math.min(pageIndex, n.pages.size() - 1)));
            }
            startActivity(intent);
            return;
        }
        if (n == null) return;
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("notebook_id", n.id);
        intent.putExtra("page_index", Math.max(0, Math.min(pageIndex, n.pages.size() - 1)));
        if (feature != null && !feature.isEmpty()) intent.putExtra("open_feature", feature);
        startActivity(intent);
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(13), dp(12), dp(13), dp(12));
        box.setBackground(rounded(Color.WHITE, 16));
        box.setElevation(dp(1));
        return box;
    }

    private LinearLayout emptyCard(String message) {
        LinearLayout box = card();
        box.addView(text(message, 13, 0xFF667085, false));
        return box;
    }

    private TextView sectionLabel(String value) {
        TextView t = text(value, 11, 0xFF7A8495, true);
        t.setPadding(dp(2), dp(2), dp(2), dp(6));
        return t;
    }

    private Button button(String value, boolean dark) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setTextColor(dark ? Color.WHITE : 0xFF182339);
        b.setBackground(rounded(dark ? 0xFF2A3854 : Color.WHITE, 12));
        return b;
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
        t.setLineSpacing(0f, 1.1f);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), color == Color.WHITE ? 0xFFE0E5EC : color);
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String label(String type) {
        if (NotebookStore.StudyMark.DOUBT.equals(type)) return "Doubt";
        if (NotebookStore.StudyMark.MISTAKE.equals(type)) return "Mistake";
        if (NotebookStore.StudyMark.IMPORTANT.equals(type)) return "Important";
        return "Revise";
    }

    private String relativeDue(long timestamp) {
        long days = Math.max(1L, Math.round(
                (timestamp - System.currentTimeMillis()) / 86400000f));
        return days == 1 ? "tomorrow" : "in " + days + " days";
    }

    private String formatDuration(long minutes) {
        if (minutes < 60) return minutes + " min";
        return (minutes / 60L) + "h " +
                String.format(Locale.US, "%02dm", minutes % 60L);
    }

    private static final class QueueRow {
        final NotebookStore.PageMeta page;
        final int pageIndex;
        final NotebookStore.StudyMark mark;
        QueueRow(NotebookStore.PageMeta page, int pageIndex, NotebookStore.StudyMark mark) {
            this.page = page; this.pageIndex = pageIndex; this.mark = mark;
        }
    }

    private static final class PageRow {
        final NotebookStore.PageMeta page;
        final int pageIndex;
        final NotebookStore.PageStats stats;
        final int openMarkers;
        final int score;
        PageRow(NotebookStore.PageMeta page, int pageIndex, NotebookStore.PageStats stats,
                int openMarkers, int score) {
            this.page = page; this.pageIndex = pageIndex; this.stats = stats;
            this.openMarkers = openMarkers; this.score = score;
        }
    }
}
