package com.jd.studynote;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
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
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements NoteCanvasView.Listener {
    private static final int REQUEST_EXPORT_PNG = 701;
    private static final int WELCOME_VERSION = 2;
    private static final String PREFS = "studynote_prefs";
    private static final String KEY_WELCOME_VERSION = "welcome_version";

    private static final int BG = Color.rgb(245, 247, 251);
    private static final int NAVY = Color.rgb(27, 24, 56);
    private static final int ACCENT = Color.rgb(91, 84, 217);
    private static final int ACCENT_DARK = Color.rgb(72, 65, 171);
    private static final int TEXT = Color.rgb(28, 33, 48);
    private static final int MUTED = Color.rgb(107, 114, 128);
    private static final int BORDER = Color.rgb(224, 228, 236);
    private static final int PAPER_PANEL = Color.rgb(232, 235, 241);

    private final Handler handler = new Handler(Looper.getMainLooper());

    private NotebookStore store;
    private ExecutorService ioExecutor;
    private SharedPreferences preferences;

    private LinearLayout root;
    private LinearLayout homeList;
    private TextView homeStats;

    private NotebookStore.NotebookMeta notebook;
    private int pageIndex;

    private NoteCanvasView canvas;
    private TextView editorTitle;
    private TextView editorSubject;
    private TextView editorPage;
    private TextView saveState;
    private TextView palmState;

    private Button penButton;
    private Button highlighterButton;
    private Button eraserButton;

    private Runnable pendingSave;
    private boolean saveInFlight;
    private boolean saveAgain;
    private boolean destroyed;
    private boolean inEditor;
    private Bitmap pendingExport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        store = new NotebookStore(this);
        ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "StudyNote-IO");
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);

        Window window = getWindow();
        window.setStatusBarColor(NAVY);
        window.setNavigationBarColor(NAVY);

        if (Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
        }

        ensureDefaultNotebook();

        if (preferences.getInt(KEY_WELCOME_VERSION, 0) < WELCOME_VERSION) {
            showWelcome();
        } else {
            showHome();
        }

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBack
            );
        }
    }

    private void ensureDefaultNotebook() {
        if (!store.list().isEmpty()) return;

        try {
            store.create(
                    "Math Practice",
                    "Mathematics",
                    NoteCanvasView.PAPER_GRAPH
            );
        } catch (Exception ignored) {
        }
    }

    private void handleBack() {
        if (inEditor) {
            checkpointSave();
            exitEditor();
        } else {
            finish();
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        handleBack();
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

        if (pendingExport != null && !pendingExport.isRecycled()) {
            pendingExport.recycle();
            pendingExport = null;
        }

        if (ioExecutor != null) {
            ioExecutor.shutdown();
        }

        super.onDestroy();
    }

    private void showWelcome() {
        inEditor = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(40), dp(24), dp(26));

        TextView logo = text("✎", 34, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(gradient(ACCENT_DARK, ACCENT));
        content.addView(logo, new LinearLayout.LayoutParams(dp(82), dp(82)));

        TextView title = text("Welcome to StudyNote", 30, TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(22), 0, 0);
        content.addView(title, titleLp);

        TextView intro = text(
                "A focused handwriting notebook built for precise study on a tablet.",
                15,
                MUTED,
                false
        );
        intro.setGravity(Gravity.CENTER);
        intro.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(-1, -2);
        introLp.setMargins(dp(24), dp(10), dp(24), 0);
        content.addView(intro, introLp);

        LinearLayout featureCard = card();
        featureCard.setPadding(dp(18), dp(18), dp(18), dp(8));

        addWelcomeFeature(
                featureCard,
                "✦",
                "Strict palm rejection",
                "Only stylus input writes. Your palm and fingers are ignored."
        );
        addWelcomeFeature(
                featureCard,
                "✓",
                "Protected autosave",
                "Handwriting is saved in the background without replacing the page."
        );
        addWelcomeFeature(
                featureCard,
                "▤",
                "Study paper",
                "Use blank, ruled, or graph paper for your subjects."
        );

        LinearLayout.LayoutParams featureLp = new LinearLayout.LayoutParams(-1, -2);
        featureLp.setMargins(0, dp(28), 0, 0);
        content.addView(featureCard, featureLp);

        LinearLayout statusCard = card();
        statusCard.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView status = text("PALM REJECTION", 10, ACCENT_DARK, true);
        status.setLetterSpacing(0.08f);
        statusCard.addView(status);

        TextView statusText = text(
                "ON  •  Stylus-only writing mode",
                14,
                TEXT,
                true
        );
        statusText.setPadding(0, dp(5), 0, 0);
        statusCard.addView(statusText);

        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, dp(12), 0, 0);
        content.addView(statusCard, statusLp);

        Button start = button("Start writing", Color.WHITE, ACCENT);
        start.setTextSize(15);
        start.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        start.setBackground(rounded(ACCENT, 16));
        start.setOnClickListener(v -> {
            preferences.edit().putInt(KEY_WELCOME_VERSION, WELCOME_VERSION).apply();
            showHome();
        });

        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(54));
        startLp.setMargins(0, dp(24), 0, 0);
        content.addView(start, startLp);

        TextView foot = text(
                "Designed for writing first. Tools stay out of your way.",
                11,
                MUTED,
                false
        );
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1, -2);
        footLp.setMargins(0, dp(12), 0, 0);
        content.addView(foot, footLp);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
    }

    private void addWelcomeFeature(
            LinearLayout parent,
            String icon,
            String title,
            String description
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(10));

        TextView badge = text(icon, 16, ACCENT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(0xFFF0F0FF, 12));
        row.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView heading = text(title, 14, TEXT, true);
        labels.addView(heading);

        TextView detail = text(description, 11, MUTED, false);
        detail.setLineSpacing(0f, 1.15f);
        detail.setPadding(0, dp(2), 0, 0);
        labels.addView(detail);

        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1f);
        labelsLp.setMargins(dp(12), 0, 0, 0);
        row.addView(labels, labelsLp);

        parent.addView(row);
    }

    private void showHome() {
        inEditor = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(22), dp(20), dp(18));
        header.setBackground(gradient(NAVY, ACCENT_DARK));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = text("✎", 25, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(rounded(0x30FFFFFF, 15));
        brandRow.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);

        TextView title = text("StudyNote", 27, Color.WHITE, true);
        brandText.addView(title);

        TextView subtitle = text(
                "Focused handwriting for serious study.",
                12,
                0xFFDAD8F8,
                false
        );
        subtitle.setPadding(0, dp(2), 0, 0);
        brandText.addView(subtitle);

        LinearLayout.LayoutParams brandTextLp = new LinearLayout.LayoutParams(0, -2, 1f);
        brandTextLp.setMargins(dp(12), 0, dp(12), 0);
        brandRow.addView(brandText, brandTextLp);

        Button newNotebook = button("+ New", NAVY, Color.WHITE);
        newNotebook.setBackground(rounded(Color.WHITE, 13));
        newNotebook.setOnClickListener(
                v -> createNotebook(null, null, NoteCanvasView.PAPER_RULED)
        );
        brandRow.addView(newNotebook, new LinearLayout.LayoutParams(dp(88), dp(44)));

        header.addView(brandRow);

        homeStats = text("0 notebooks  •  0 pages", 11, 0xFFE6E4FA, true);
        homeStats.setPadding(0, dp(15), 0, 0);
        header.addView(homeStats);

        root.addView(header);

        TextView section = text("YOUR NOTEBOOKS", 11, 0xFF7A8191, true);
        section.setPadding(dp(20), dp(18), dp(20), dp(10));
        root.addView(section);

        ScrollView scroll = new ScrollView(this);
        homeList = new LinearLayout(this);
        homeList.setOrientation(LinearLayout.VERTICAL);
        homeList.setPadding(dp(16), 0, dp(16), dp(24));
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
            empty.addView(
                    text("Create a notebook to start writing.", 14, MUTED, false)
            );
            homeList.addView(empty);
        }

        homeStats.setText(
                notebooks.size()
                        + " notebook"
                        + (notebooks.size() == 1 ? "" : "s")
                        + "  •  "
                        + pages
                        + " page"
                        + (pages == 1 ? "" : "s")
        );
    }

    private void addNotebookCard(NotebookStore.NotebookMeta n) {
        LinearLayout box = card();

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        String firstLetter = n.title == null || n.title.trim().isEmpty()
                ? "N"
                : String.valueOf(n.title.trim().charAt(0)).toUpperCase();

        TextView badge = text(firstLetter, 18, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(ACCENT, 14));
        row.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        labels.addView(text(n.title, 17, TEXT, true));

        TextView meta = text(
                n.subject
                        + "  •  "
                        + n.pages.size()
                        + " page"
                        + (n.pages.size() == 1 ? "" : "s"),
                12,
                MUTED,
                false
        );
        meta.setPadding(0, dp(3), 0, 0);
        labels.addView(meta);

        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1f);
        labelsLp.setMargins(dp(12), 0, dp(8), 0);
        row.addView(labels, labelsLp);

        TextView arrow = text("›", 25, ACCENT, false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(38), dp(46)));

        box.addView(row);
        box.setOnClickListener(v -> enterEditor(n.id, 0));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        homeList.addView(box, lp);
    }

    private void createNotebook(
            String titlePreset,
            String subjectPreset,
            String paperType
    ) {
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
        root.setBackgroundColor(PAPER_PANEL);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(8), dp(8));
        header.setBackground(gradient(NAVY, ACCENT_DARK));

        Button back = toolbarButton("‹", Color.WHITE, 25);
        back.setBackground(rounded(0x22FFFFFF, 13));
        back.setOnClickListener(v -> handleBack());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL);

        editorTitle = text(notebook.title, 16, Color.WHITE, true);
        titles.addView(editorTitle);

        editorSubject = text(notebook.subject, 10, 0xFFD9D7F0, false);
        editorSubject.setPadding(0, dp(2), 0, 0);
        titles.addView(editorSubject);

        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titlesLp.setMargins(dp(10), 0, dp(8), 0);
        header.addView(titles, titlesLp);

        saveState = text("Saved", 10, 0xFFF0EEFF, true);
        saveState.setGravity(Gravity.CENTER);
        saveState.setBackground(rounded(0x22FFFFFF, 12));
        header.addView(saveState, new LinearLayout.LayoutParams(dp(68), dp(34)));

        palmState = text("Palm ON", 9, 0xFFF0EEFF, true);
        palmState.setGravity(Gravity.CENTER);
        palmState.setBackground(rounded(0x22FFFFFF, 12));

        LinearLayout.LayoutParams palmLp = new LinearLayout.LayoutParams(dp(68), dp(34));
        palmLp.setMargins(dp(6), 0, 0, 0);
        header.addView(palmState, palmLp);

        Button more = toolbarButton("⋮", Color.WHITE, 22);
        more.setBackground(rounded(0x22FFFFFF, 13));
        more.setOnClickListener(this::showMoreMenu);
        LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        moreLp.setMargins(dp(6), 0, 0, 0);
        header.addView(more, moreLp);

        root.addView(header);
        root.addView(buildToolbar());

        FrameLayout canvasFrame = new FrameLayout(this);
        canvasFrame.setPadding(dp(8), dp(8), dp(8), dp(8));

        canvas = new NoteCanvasView(this);
        canvas.setListener(this);

        canvasFrame.addView(
                canvas,
                new FrameLayout.LayoutParams(-1, -1)
        );

        root.addView(
                canvasFrame,
                new LinearLayout.LayoutParams(-1, 0, 1f)
        );

        root.addView(buildBottomBar());

        setContentView(root);
        loadPage();
    }

    private View buildToolbar() {
        LinearLayout outer = new LinearLayout(this);
        outer.setGravity(Gravity.CENTER_VERTICAL);
        outer.setPadding(dp(8), dp(7), dp(8), dp(7));
        outer.setBackgroundColor(Color.WHITE);

        penButton = toolButton("Pen");
        highlighterButton = toolButton("Highlight");
        eraserButton = toolButton("Eraser");

        penButton.setOnClickListener(v -> {
            canvas.setTool(NoteCanvasView.TOOL_PEN);
            updateToolButtons();
        });

        highlighterButton.setOnClickListener(v -> {
            canvas.setTool(NoteCanvasView.TOOL_HIGHLIGHTER);
            updateToolButtons();
        });

        eraserButton.setOnClickListener(v -> {
            canvas.setTool(NoteCanvasView.TOOL_ERASER);
            updateToolButtons();
        });

        outer.addView(penButton);
        outer.addView(
                highlighterButton,
                marginLp(dp(6), dp(0), dp(0), dp(0))
        );
        outer.addView(
                eraserButton,
                marginLp(dp(6), dp(0), dp(0), dp(0))
        );

        View divider = new View(this);
        divider.setBackgroundColor(BORDER);

        LinearLayout.LayoutParams dividerLp =
                new LinearLayout.LayoutParams(dp(1), dp(30));
        dividerLp.setMargins(dp(8), 0, dp(8), 0);
        outer.addView(divider, dividerLp);

        Button undo = toolbarButton("↶", TEXT, 20);
        undo.setOnClickListener(v -> canvas.undo());
        outer.addView(undo);

        Button redo = toolbarButton("↷", TEXT, 20);
        redo.setOnClickListener(v -> canvas.redo());
        outer.addView(redo);

        TextView writing = text(
                "Stylus writing",
                11,
                MUTED,
                true
        );
        writing.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams writingLp =
                new LinearLayout.LayoutParams(0, dp(32), 1f);
        writingLp.setMargins(dp(8), 0, 0, 0);
        outer.addView(writing, writingLp);

        return outer;
    }

    private View buildBottomBar() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(6), dp(7), dp(6));
        row.setBackgroundColor(Color.WHITE);

        Button previous = toolbarButton("‹", TEXT, 23);
        previous.setOnClickListener(v -> movePage(-1));
        row.addView(previous, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER);

        editorPage = text("", 12, TEXT, true);
        editorPage.setGravity(Gravity.CENTER);
        middle.addView(editorPage);

        TextView autosave = text("AUTO-SAVE ON", 9, MUTED, true);
        autosave.setGravity(Gravity.CENTER);
        middle.addView(autosave);

        row.addView(
                middle,
                new LinearLayout.LayoutParams(0, dp(44), 1f)
        );

        Button next = toolbarButton("›", TEXT, 23);
        next.setOnClickListener(v -> movePage(1));
        row.addView(next, new LinearLayout.LayoutParams(dp(44), dp(44)));

        Button add = toolbarButton("+ Page", ACCENT, 11);
        add.setOnClickListener(v -> addPage());
        row.addView(add);

        return row;
    }

    private void loadPage() {
        if (canvas == null || notebook.pages.isEmpty()) return;

        pageIndex = clampPage(pageIndex);

        NotebookStore.PageMeta page = notebook.pages.get(pageIndex);

        Bitmap bitmap = store.loadPageBitmap(
                page.id,
                NoteCanvasView.PAGE_WIDTH,
                NoteCanvasView.PAGE_HEIGHT
        );

        canvas.loadBitmap(bitmap);
        canvas.setPaperType(page.paperType);
        canvas.setTool(NoteCanvasView.TOOL_PEN);
        canvas.setPenSize(6.0f);

        editorPage.setText(
                "Page " + (pageIndex + 1) + " / " + notebook.pages.size()
        );
        saveState.setText("Saved");
        editorTitle.setText(notebook.title);
        editorSubject.setText(notebook.subject);
        palmState.setText("Palm ON");

        updateToolButtons();
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

        store.addPage(
                notebook,
                "Page " + (notebook.pages.size() + 1),
                paper
        );

        pageIndex = notebook.pages.size() - 1;

        loadPage();
        checkpointSave();
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);

        menu.getMenu().add("Pen colour");
        menu.getMenu().add("Pen size");
        menu.getMenu().add("Paper template");
        menu.getMenu().add("Export page as PNG");
        menu.getMenu().add("Rename page");
        menu.getMenu().add("Rename notebook");
        menu.getMenu().add("Clear page");

        menu.getMenu().add("Palm rejection: ON");

        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();

            switch (title) {
                case "Pen colour":
                    showColorDialog();
                    return true;

                case "Pen size":
                    showPenSizeDialog();
                    return true;

                case "Paper template":
                    showPaperDialog();
                    return true;

                case "Export page as PNG":
                    exportCurrentPage();
                    return true;

                case "Rename page":
                    renamePage();
                    return true;

                case "Rename notebook":
                    renameCurrent();
                    return true;

                case "Clear page":
                    confirmClear();
                    return true;

                case "Palm rejection: ON":
                    showPalmRejectionInfo();
                    return true;

                default:
                    return false;
            }
        });

        menu.show();
    }

    private void showPalmRejectionInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Palm rejection is ON")
                .setMessage(
                        "StudyNote accepts only stylus or stylus-eraser input for writing. "
                                + "Finger and palm touches are ignored, including extra fingers "
                                + "touching the page while you write."
                )
                .setPositiveButton("OK", null)
                .show();
    }

    private void showPenSizeDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), 0, dp(10), 0);

        TextView value = text(
                "Current: " + formatSize(canvas.getPenSize()),
                12,
                TEXT,
                true
        );
        value.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(value);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(32);
        seekBar.setProgress(
                Math.round((canvas.getPenSize() - 2f) / 0.5f)
        );
        box.addView(seekBar);

        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar bar,
                            int progress,
                            boolean fromUser
                    ) {
                        float size = 2f + progress * 0.5f;
                        value.setText(
                                "Current: " + formatSize(size)
                        );
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar bar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar bar) {
                    }
                }
        );

        new AlertDialog.Builder(this)
                .setTitle("Pen size")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (d, w) -> {
                    float size = 2f + seekBar.getProgress() * 0.5f;
                    canvas.setPenSize(size);
                })
                .show();
    }

    private String formatSize(float value) {
        return String.format(java.util.Locale.US, "%.1f", value);
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
                    String value = input.getText().toString().trim();

                    if (!value.isEmpty()) {
                        notebook.title = value;
                        editorTitle.setText(value);
                        checkpointSave();
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
                    String value = input.getText().toString().trim();

                    if (!value.isEmpty()) {
                        notebook.pages.get(pageIndex).title = value;
                        checkpointSave();
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
                .setTitle("Paper template")
                .setSingleChoiceItems(labels, initialChecked, null)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (d, w) -> {
                    android.widget.ListView list =
                            ((AlertDialog) d).getListView();

                    int selected = list == null
                            ? initialChecked
                            : list.getCheckedItemPosition();

                    if (selected < 0 || selected >= values.length) {
                        selected = initialChecked;
                    }

                    notebook.pages.get(pageIndex).paperType = values[selected];
                    canvas.setPaperType(values[selected]);
                    checkpointSave();
                })
                .show();
    }

    private void showColorDialog() {
        final int[] colors = {
                0xFF1B1838,
                0xFF1D4ED8,
                0xFF0F766E,
                0xFF9F1239
        };

        String[] labels = {
                "Dark",
                "Blue",
                "Teal",
                "Red"
        };

        new AlertDialog.Builder(this)
                .setTitle("Pen colour")
                .setItems(
                        labels,
                        (d, which) -> canvas.setInkColor(colors[which])
                )
                .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear this page?")
                .setMessage(
                        "This removes all handwriting from the current page."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Clear",
                        (d, w) -> canvas.clearPage()
                )
                .show();
    }

    private void exportCurrentPage() {
        if (canvas == null) return;

        Bitmap exported = canvas.exportBitmap();

        if (exported == null) {
            toast("Could not prepare the page.");
            return;
        }

        if (pendingExport != null && !pendingExport.isRecycled()) {
            pendingExport.recycle();
        }

        pendingExport = exported;

        String pageTitle = notebook.pages.get(pageIndex).title
                .replaceAll("[^a-zA-Z0-9._-]+", "_");

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(
                Intent.EXTRA_TITLE,
                pageTitle + ".png"
        );

        try {
            startActivityForResult(
                    intent,
                    REQUEST_EXPORT_PNG
            );
        } catch (Exception e) {
            pendingExport.recycle();
            pendingExport = null;
            toast("No file picker is available.");
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != REQUEST_EXPORT_PNG) return;

        Bitmap bitmap = pendingExport;
        pendingExport = null;

        if (bitmap == null || bitmap.isRecycled()) return;

        if (
                resultCode != RESULT_OK
                        || data == null
                        || data.getData() == null
        ) {
            bitmap.recycle();
            return;
        }

        Uri uri = data.getData();

        try (OutputStream out =
                     getContentResolver().openOutputStream(uri)) {

            if (
                    out == null
                            || !bitmap.compress(
                            Bitmap.CompressFormat.PNG,
                            100,
                            out
                    )
            ) {
                throw new Exception("PNG export failed");
            }

            toast("Page exported.");

        } catch (Exception e) {
            toast("Could not export the page.");

        } finally {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    @Override
    public void onCanvasDirty() {
        if (saveState != null) {
            saveState.setText("Saving…");
        }

        scheduleSave();
    }

    private void scheduleSave() {
        if (pendingSave != null) {
            handler.removeCallbacks(pendingSave);
        }

        pendingSave = () -> {
            pendingSave = null;
            enqueueSave();
        };

        handler.postDelayed(pendingSave, 850L);
    }

    private void checkpointSave() {
        if (pendingSave != null) {
            handler.removeCallbacks(pendingSave);
            pendingSave = null;
        }

        enqueueSave();
    }

    private void enqueueSave() {
        if (
                destroyed
                        || !inEditor
                        || notebook == null
                        || canvas == null
        ) {
            return;
        }

        if (saveInFlight) {
            saveAgain = true;
            return;
        }

        final int currentIndex = pageIndex;

        if (
                currentIndex < 0
                        || currentIndex >= notebook.pages.size()
        ) {
            return;
        }

        final String pageId =
                notebook.pages.get(currentIndex).id;

        final Bitmap source = canvas.getInkBitmap();

        if (source == null || source.isRecycled()) {
            return;
        }

        final Bitmap snapshot;

        try {
            snapshot = source.copy(
                    Bitmap.Config.ARGB_8888,
                    false
            );
        } catch (OutOfMemoryError e) {
            if (saveState != null) {
                saveState.setText("Memory busy");
            }

            if (!saveAgain) {
                handler.postDelayed(
                        this::checkpointSave,
                        1200L
                );
            }

            return;
        }

        final NotebookStore.NotebookMeta metadata =
                NotebookStore.copyOf(notebook);

        saveInFlight = true;

        ioExecutor.execute(() -> {
            boolean ok = true;

            try {
                store.savePageBitmap(
                        pageId,
                        snapshot
                );
                store.saveNotebook(metadata);
            } catch (Exception e) {
                ok = false;
            } finally {
                if (!snapshot.isRecycled()) {
                    snapshot.recycle();
                }

                final boolean success = ok;

                runOnUiThread(() -> {
                    saveInFlight = false;

                    if (saveState != null) {
                        saveState.setText(
                                success
                                        ? "Saved"
                                        : "Save error"
                        );
                    }

                    if (saveAgain && !destroyed) {
                        saveAgain = false;
                        handler.postDelayed(
                                this::enqueueSave,
                                250L
                        );
                    }
                });
            }
        });
    }

    private void exitEditor() {
        inEditor = false;

        getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        canvas = null;
        notebook = null;

        showHome();
    }

    private void updateToolButtons() {
        if (canvas == null) return;

        int tool = canvas.getTool();

        styleToolButton(
                penButton,
                tool == NoteCanvasView.TOOL_PEN
        );

        styleToolButton(
                highlighterButton,
                tool == NoteCanvasView.TOOL_HIGHLIGHTER
        );

        styleToolButton(
                eraserButton,
                tool == NoteCanvasView.TOOL_ERASER
        );
    }

    private void styleToolButton(
            Button button,
            boolean selected
    ) {
        if (button == null) return;

        if (selected) {
            button.setTextColor(Color.WHITE);
            button.setBackground(
                    rounded(ACCENT, 14)
            );
        } else {
            button.setTextColor(TEXT);
            button.setBackground(
                    rounded(0xFFF4F5F8, 14)
            );
        }
    }

    private int clampPage(int index) {
        if (
                notebook == null
                        || notebook.pages.isEmpty()
        ) {
            return 0;
        }

        return Math.max(
                0,
                Math.min(
                        index,
                        notebook.pages.size() - 1
                )
        );
    }

    private Button toolButton(String value) {
        Button b = button(
                value,
                TEXT,
                0xFFF4F5F8
        );

        b.setTextSize(11);
        b.setPadding(
                dp(12),
                0,
                dp(12),
                0
        );

        return b;
    }

    private Button toolbarButton(
            String value,
            int textColor,
            float textSize
    ) {
        Button b = button(
                value,
                textColor,
                Color.WHITE
        );

        b.setTextSize(textSize);
        return b;
    }

    private Button button(
            String value,
            int textColor,
            int background
    ) {
        Button b = new Button(this);

        b.setText(value);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setTextSize(12);
        b.setTextColor(textColor);
        b.setGravity(Gravity.CENTER);
        b.setPadding(
                dp(10),
                0,
                dp(10),
                0
        );
        b.setBackground(
                rounded(background, 12)
        );

        return b;
    }

    private LinearLayout.LayoutParams marginLp(
            int left,
            int top,
            int right,
            int bottom
    ) {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        -2,
                        dp(44)
                );

        lp.setMargins(
                left,
                top,
                right,
                bottom
        );

        return lp;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);

        box.setOrientation(
                LinearLayout.VERTICAL
        );
        box.setPadding(
                dp(15),
                dp(14),
                dp(15),
                dp(14)
        );
        box.setBackground(
                rounded(Color.WHITE, 18)
        );
        box.setElevation(dp(1));

        return box;
    }

    private TextView text(
            String value,
            float size,
            int color,
            boolean bold
    ) {
        TextView t = new TextView(this);

        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setLineSpacing(0f, 1.1f);

        if (bold) {
            t.setTypeface(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
            );
        }

        return t;
    }

    private GradientDrawable rounded(
            int color,
            float radius
    ) {
        GradientDrawable d =
                new GradientDrawable();

        d.setColor(color);
        d.setCornerRadius(dp(radius));

        if (
                color == Color.WHITE
                        || color == 0xFFF4F5F8
                        || color == 0xFFF0F0FF
        ) {
            d.setStroke(
                    dp(1),
                    BORDER
            );
        }

        return d;
    }

    private GradientDrawable gradient(
            int start,
            int end
    ) {
        GradientDrawable d =
                new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{start, end}
                );

        d.setCornerRadius(0f);
        return d;
    }

    private int dp(float value) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private void toast(String value) {
        Toast.makeText(
                this,
                value,
                Toast.LENGTH_SHORT
        ).show();
    }
}
