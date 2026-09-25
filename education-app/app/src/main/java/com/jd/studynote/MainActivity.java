package com.jd.studynote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements NoteCanvasView.Listener {
    private NotebookStore store;
    private ExecutorService ioExecutor;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private LinearLayout homeList;
    private TextView homeStats;

    private NotebookStore.NotebookMeta notebook;
    private int pageIndex;

    private NoteCanvasView canvas;
    private TextView editorTitle;
    private TextView editorPage;
    private TextView saveState;

    private Runnable pendingSave;
    private boolean saveInFlight;
    private boolean saveAgain;
    private boolean destroyed;
    private boolean inEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        store = new NotebookStore(this);
        ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "StudyNote-IO");
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(24, 35, 57));
        window.setNavigationBarColor(Color.rgb(24, 35, 57));

        if (store.list().isEmpty()) {
            try {
                store.create("Math Practice", "Mathematics", NoteCanvasView.PAPER_GRAPH);
            } catch (Exception ignored) {
            }
        }

        showHome();
    }

    @Override
    public void onStop() {
        if (inEditor && notebook != null && canvas != null) {
            checkpointSave();
        }
        super.onStop();
    }

    @Override
    public void onTrimMemory(int level) {
        if (inEditor && notebook != null && canvas != null) {
            checkpointSave();
            if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
                canvas.clearUndoHistory();
            }
        }
        super.onTrimMemory(level);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (pendingSave != null) {
            handler.removeCallbacks(pendingSave);
            pendingSave = null;
        }
        if (ioExecutor != null) {
            ioExecutor.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (inEditor) {
            checkpointSave();
            exitEditor();
            return;
        }
        super.onBackPressed();
    }

    private void showHome() {
        inEditor = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 247, 251));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(20), dp(20), dp(18));
        header.setBackgroundColor(Color.rgb(24, 35, 57));

        TextView title = text("StudyNote", 30, Color.WHITE, true);
        header.addView(title);
        TextView subtitle = text("A simple handwriting notebook for school.", 12, 0xFFC9D2E1, false);
        subtitle.setPadding(0, dp(3), 0, 0);
        header.addView(subtitle);

        homeStats = text("0 notebooks  •  0 pages", 11, 0xFFE6EBF4, true);
        homeStats.setPadding(0, dp(12), 0, 0);
        header.addView(homeStats);
        root.addView(header);

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(16), dp(16), dp(16), dp(10));

        Button add = primaryButton("+ New notebook");
        add.setOnClickListener(v -> createNotebook(null, null, NoteCanvasView.PAPER_RULED));
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(actions);

        TextView section = text("MY NOTEBOOKS", 11, 0xFF7A8495, true);
        section.setPadding(dp(18), dp(2), dp(18), dp(8));
        root.addView(section);

        ScrollView scroll = new ScrollView(this);
        homeList = new LinearLayout(this);
        homeList.setOrientation(LinearLayout.VERTICAL);
        homeList.setPadding(dp(16), 0, dp(16), dp(20));
        scroll.addView(homeList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        refreshHome();
    }

    private void refreshHome() {
        if (homeList == null) return;
        homeList.removeAllViews();

        List<NotebookStore.NotebookMeta> notebooks = store.list();
        int pages = 0;
        for (NotebookStore.NotebookMeta n : notebooks) {
            pages += n.pages.size();
            addNotebookCard(n);
        }

        if (notebooks.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("No notebooks yet. Create one and start writing.", 14, 0xFF667085, false));
            homeList.addView(empty);
        }

        homeStats.setText(
                notebooks.size() + " notebook" + (notebooks.size() == 1 ? "" : "s")
                        + "  •  " + pages + " saved page" + (pages == 1 ? "" : "s")
        );
    }

    private void addNotebookCard(NotebookStore.NotebookMeta n) {
        LinearLayout card = card();

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(n.title, 18, 0xFF182339, true));
        labels.addView(text(
                n.subject + "  •  " + n.pages.size() + " page" + (n.pages.size() == 1 ? "" : "s"),
                12, 0xFF667085, false
        ));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        Button open = button("OPEN");
        open.setOnClickListener(v -> enterEditor(n.id, 0));
        row.addView(open, new LinearLayout.LayoutParams(dp(82), dp(42)));

        card.addView(row);
        card.setOnClickListener(v -> enterEditor(n.id, 0));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(9));
        homeList.addView(card, lp);
    }

    private void createNotebook(String titlePreset, String subjectPreset, String paperType) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), 0, dp(8), 0);

        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setHint("Notebook title");
        if (titlePreset != null) title.setText(titlePreset);

        EditText subject = new EditText(this);
        subject.setSingleLine(true);
        subject.setHint("Subject");
        if (subjectPreset != null) subject.setText(subjectPreset);

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
                                paperType
                        );
                        dialog.dismiss();
                        enterEditor(created.id, 0);
                    } catch (Exception e) {
                        toast("Could not create notebook.");
                    }
                })
        );
        dialog.show();
        title.requestFocus();
    }

    private void enterEditor(String notebookId, int desiredPage) {
        try {
            notebook = store.get(notebookId);
        } catch (Exception e) {
            toast("Could not open notebook.");
            return;
        }

        inEditor = true;
        pageIndex = clampPage(desiredPage);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildEditor();
    }

    private void buildEditor() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFE2E6ED);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(7), dp(7), dp(7), dp(7));
        header.setBackgroundColor(0xFF182339);
        header.setElevation(dp(3));

        Button back = toolbarButton("‹");
        back.setTextSize(24);
        back.setTextColor(Color.WHITE);
        back.setBackground(rounded(0xFF26334D, 12));
        back.setOnClickListener(v -> onBackPressed());
        header.addView(back, new LinearLayout.LayoutParams(dp(46), dp(44)));

        editorTitle = text(notebook.title, 16, Color.WHITE, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.setMargins(dp(9), 0, dp(6), 0);
        header.addView(editorTitle, titleLp);

        saveState = text("Saved", 10, 0xFFD8DFEB, true);
        saveState.setGravity(Gravity.CENTER);
        saveState.setBackground(rounded(0xFF26334D, 13));
        header.addView(saveState, new LinearLayout.LayoutParams(dp(68), dp(34)));

        Button rename = toolbarButton("NAME");
        rename.setOnClickListener(v -> renameCurrent());
        header.addView(rename, new LinearLayout.LayoutParams(dp(58), dp(44)));

        root.addView(header);
        root.addView(buildToolbar());

        FrameLayout canvasFrame = new FrameLayout(this);
        canvasFrame.setPadding(dp(7), dp(7), dp(7), dp(7));

        canvas = new NoteCanvasView(this);
        canvas.setListener(this);
        canvasFrame.addView(canvas, new FrameLayout.LayoutParams(-1, -1));
        root.addView(canvasFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(buildBottomBar());

        setContentView(root);
        loadPage();
    }

    private View buildToolbar() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setBackgroundColor(0xFFF9FAFC);

        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(7), dp(5), dp(7), dp(5));

        addTool(row, "PEN", NoteCanvasView.TOOL_PEN);
        addTool(row, "HIGHLIGHT", NoteCanvasView.TOOL_HIGHLIGHTER);
        addTool(row, "ERASER", NoteCanvasView.TOOL_ERASER);

        Button undo = toolbarButton("UNDO");
        undo.setOnClickListener(v -> canvas.undo());
        row.addView(undo);

        Button redo = toolbarButton("REDO");
        redo.setOnClickListener(v -> canvas.redo());
        row.addView(redo);

        Button clear = toolbarButton("CLEAR");
        clear.setOnClickListener(v -> confirmClear());
        row.addView(clear);

        Button color = toolbarButton("INK");
        color.setOnClickListener(v -> showColorDialog());
        row.addView(color);

        TextView sizeLabel = text(" Size ", 11, 0xFF667085, true);
        sizeLabel.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(sizeLabel);

        SeekBar size = new SeekBar(this);
        size.setMax(28);
        size.setProgress(8);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                canvas.setPenSize(2.0f + progress * 0.55f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        row.addView(size, new LinearLayout.LayoutParams(dp(115), dp(44)));

        Button paper = toolbarButton("PAPER");
        paper.setOnClickListener(v -> showPaperDialog());
        row.addView(paper);

        Button pageName = toolbarButton("PAGE NAME");
        pageName.setOnClickListener(v -> renamePage());
        row.addView(pageName);

        scroll.addView(row);
        return scroll;
    }

    private void addTool(LinearLayout parent, String label, int tool) {
        Button b = toolbarButton(label);
        b.setOnClickListener(v -> canvas.setTool(tool));
        parent.addView(b);
    }

    private View buildBottomBar() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(5), dp(7), dp(5));
        row.setBackgroundColor(Color.WHITE);
        row.setElevation(dp(4));

        Button previous = toolbarButton("‹");
        previous.setTextSize(22);
        previous.setOnClickListener(v -> movePage(-1));
        row.addView(previous, new LinearLayout.LayoutParams(dp(46), dp(44)));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER);

        editorPage = text("", 12, 0xFF182339, true);
        editorPage.setGravity(Gravity.CENTER);
        middle.addView(editorPage);

        TextView autosave = text("AUTO-SAVE", 9, 0xFF7A8495, true);
        middle.addView(autosave);
        row.addView(middle, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button next = toolbarButton("›");
        next.setTextSize(22);
        next.setOnClickListener(v -> movePage(1));
        row.addView(next, new LinearLayout.LayoutParams(dp(46), dp(44)));

        Button add = toolbarButton("+ PAGE");
        add.setOnClickListener(v -> addPage());
        row.addView(add);

        return row;
    }

    private void loadPage() {
        if (canvas == null || notebook.pages.isEmpty()) return;
        pageIndex = clampPage(pageIndex);

        NotebookStore.PageMeta page = notebook.pages.get(pageIndex);
        Bitmap bitmap = store.loadPageBitmap(
                page.id, NoteCanvasView.PAGE_WIDTH, NoteCanvasView.PAGE_HEIGHT);

        canvas.loadBitmap(bitmap);
        canvas.setPaperType(page.paperType);
        canvas.setTool(NoteCanvasView.TOOL_PEN);
        canvas.setPenSize(6.4f);

        editorPage.setText(
                "Page " + (pageIndex + 1) + " / " + notebook.pages.size()
        );
        saveState.setText("Saved");
        editorTitle.setText(notebook.title);
    }

    private void movePage(int delta) {
        checkpointSave();

        int target = pageIndex + delta;
        if (target < 0 || target >= notebook.pages.size()) return;

        pageIndex = target;
        loadPage();
    }

    private void addPage() {
        checkpointSave();

        String paper = notebook.pages.get(pageIndex).paperType;
        NotebookStore.PageMeta page = store.addPage(
                notebook,
                "Page " + (notebook.pages.size() + 1),
                paper
        );

        pageIndex = notebook.pages.size() - 1;
        try {
            store.saveNotebook(notebook);
        } catch (Exception e) {
            toast("Could not save notebook.");
            return;
        }
        loadPage();
    }

    private void renameCurrent() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(notebook.title);

        new AlertDialog.Builder(this)
                .setTitle("Rename notebook")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        store.renameNotebook(notebook, input.getText().toString());
                        editorTitle.setText(notebook.title);
                    } catch (Exception e) {
                        toast("Could not rename notebook.");
                    }
                })
                .show();
    }

    private void renamePage() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(notebook.pages.get(pageIndex).title);

        new AlertDialog.Builder(this)
                .setTitle("Rename page")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        store.renamePage(notebook, pageIndex, input.getText().toString());
                        editorPage.setText("Page " + (pageIndex + 1) + " / " + notebook.pages.size());
                    } catch (Exception e) {
                        toast("Could not rename page.");
                    }
                })
                .show();
    }

    private void showPaperDialog() {
        String[] labels = {"Blank", "Ruled", "Graph"};
        String[] values = {
                NoteCanvasView.PAPER_BLANK,
                NoteCanvasView.PAPER_RULED,
                NoteCanvasView.PAPER_GRAPH
        };

        String current = notebook.pages.get(pageIndex).paperType;
        int checked = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checked = i;
                break;
            }
        }
        final int initialChecked = checked;

        new AlertDialog.Builder(this)
                .setTitle("Paper")
                .setSingleChoiceItems(labels, initialChecked, null)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (d, w) -> {
                    android.widget.ListView list = ((AlertDialog) d).getListView();
                    int selected = list == null
                            ? initialChecked
                            : list.getCheckedItemPosition();
                    if (selected < 0 || selected >= values.length) selected = initialChecked;

                    notebook.pages.get(pageIndex).paperType = values[selected];
                    canvas.setPaperType(values[selected]);
                    checkpointSave();
                })
                .show();
    }

    private void showColorDialog() {
        final int[] colors = {
                0xFF182339,
                0xFF1E5AA8,
                0xFF7D2848,
                0xFF0F6950,
                0xFF6A461F,
                0xFF5C3196
        };
        String[] labels = {"Navy", "Blue", "Burgundy", "Green", "Brown", "Purple"};

        new AlertDialog.Builder(this)
                .setTitle("Ink")
                .setItems(labels, (d, which) -> canvas.setInkColor(colors[which]))
                .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear page?")
                .setMessage("This removes all handwriting from the current page.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (d, w) -> canvas.clearPage())
                .show();
    }

    @Override
    public void onCanvasDirty() {
        if (saveState != null) saveState.setText("Saving…");
        scheduleSave();
    }

    private void scheduleSave() {
        if (pendingSave != null) handler.removeCallbacks(pendingSave);
        pendingSave = () -> {
            pendingSave = null;
            enqueueSave(false);
        };
        handler.postDelayed(pendingSave, 850L);
    }

    private void checkpointSave() {
        if (pendingSave != null) {
            handler.removeCallbacks(pendingSave);
            pendingSave = null;
        }
        enqueueSave(true);
    }

    private void enqueueSave(boolean immediate) {
        if (destroyed || !inEditor || notebook == null || canvas == null) return;
        if (saveInFlight) {
            saveAgain = true;
            return;
        }

        final int currentIndex = pageIndex;
        if (currentIndex < 0 || currentIndex >= notebook.pages.size()) return;

        final String pageId = notebook.pages.get(currentIndex).id;
        final Bitmap source = canvas.getInkBitmap();
        if (source == null || source.isRecycled()) return;

        final Bitmap snapshot;
        try {
            snapshot = source.copy(Bitmap.Config.ARGB_8888, false);
        } catch (OutOfMemoryError e) {
            if (saveState != null) saveState.setText("Memory busy");
            if (!immediate) {
                handler.postDelayed(this::checkpointSave, 1500L);
            }
            return;
        }

        final NotebookStore.NotebookMeta metadata = NotebookStore.copyOf(notebook);
        saveInFlight = true;

        ioExecutor.execute(() -> {
            boolean ok = true;
            try {
                store.savePageBitmap(pageId, snapshot);
                store.saveNotebook(metadata);
            } catch (Exception e) {
                ok = false;
            } finally {
                if (!snapshot.isRecycled()) snapshot.recycle();
                final boolean success = ok;
                runOnUiThread(() -> {
                    saveInFlight = false;
                    if (saveState != null) {
                        saveState.setText(success ? "Saved" : "Save error");
                    }
                    if (saveAgain && !destroyed) {
                        saveAgain = false;
                        handler.postDelayed(() -> enqueueSave(true), 250L);
                    }
                });
            }
        });
    }

    private void exitEditor() {
        inEditor = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        canvas = null;
        notebook = null;
        showHome();
    }

    private int clampPage(int index) {
        if (notebook == null || notebook.pages.isEmpty()) return 0;
        return Math.max(0, Math.min(index, notebook.pages.size() - 1));
    }

    private Button primaryButton(String value) {
        Button b = button(value);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(0xFF3157D5, 14));
        return b;
    }

    private Button toolbarButton(String value) {
        Button b = button(value);
        b.setTextSize(11);
        b.setPadding(dp(9), 0, dp(9), 0);
        return b;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(9), 0, dp(9), 0);
        b.setTextSize(12);
        b.setTextColor(0xFF182339);
        b.setBackground(rounded(Color.WHITE, 12));
        return b;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(13), dp(14), dp(13));
        box.setBackground(rounded(Color.WHITE, 16));
        box.setElevation(dp(1));
        return box;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setLineSpacing(0f, 1.1f);
        if (bold) {
            t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        }
        return t;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (color == Color.WHITE) {
            d.setStroke(dp(1), 0xFFE0E5EC);
        }
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }
}
