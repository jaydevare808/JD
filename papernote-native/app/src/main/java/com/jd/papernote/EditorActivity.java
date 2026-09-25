package com.jd.papernote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EditorActivity extends Activity implements PaperCanvasView.Listener {
    private static final int REQUEST_IMAGE = 501;
    private static final int REQUEST_BACKUP = 503;
    private static final int REQUEST_RESTORE = 504;
    private static final int REQUEST_PDF_IMPORT = 506;
    private static final int REQUEST_EXPORT_PERMISSION = 505;

    private NotebookStore store;
    private ExecutorService ioExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private NotebookStore.NotebookMeta currentNotebook;
    private int currentPageIndex;

    private PaperCanvasView canvasView;
    private TextView titleLabel;
    private TextView pageLabel;
    private TextView saveLabel;
    private Button writeModeButton;
    private Button palmButton;
    private Button marginButton;

    private Runnable pendingAutosave;
    private boolean saveInFlight;
    private boolean saveAgain;
    private boolean destroyed;
    private ExportManager.Format pendingExportFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        store = new NotebookStore(this);
        ioExecutor = Executors.newSingleThreadExecutor();

        Window window = getWindow();
        window.setStatusBarColor(0xFF182339);
        window.setNavigationBarColor(0xFF182339);

        String notebookId = getIntent().getStringExtra("notebook_id");
        if (notebookId == null || notebookId.trim().isEmpty()) {
            finish();
            return;
        }

        try {
            currentNotebook = store.get(notebookId);
            currentPageIndex = getIntent().getIntExtra("page_index", 0);
            currentPageIndex = clampPageIndex(currentPageIndex);
            buildEditor();
            String requested = getIntent().getStringExtra("open_feature");
            if ("ai_save".equals(requested)) {
                String text = getIntent().getStringExtra("ai_text");
                if (text != null && !text.trim().isEmpty()) {
                    mainHandler.postDelayed(() -> canvasView.addText(
                            text.trim(), 145f, 190f), 250L);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not open notebook.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (currentNotebook != null && canvasView != null) {
            saveCurrentPageNow();
        }
        if (ioExecutor != null) ioExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        saveCurrentPageNow();
        finish();
    }

    private int clampPageIndex(int index) {
        if (currentNotebook == null || currentNotebook.pages.isEmpty()) return 0;
        return Math.max(0, Math.min(index, currentNotebook.pages.size() - 1));
    }

    private void buildEditor() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFE2E6ED);

        root.addView(buildHeader());
        root.addView(buildToolBar());
        root.addView(buildSettingsBar());

        FrameLayout canvasFrame = new FrameLayout(this);
        canvasFrame.setPadding(dp(7), dp(7), dp(7), dp(7));

        canvasView = new PaperCanvasView(this);
        canvasView.setListener(this);
        canvasView.setPenSize(4.6f);
        canvasView.setStabilizer(0.04f);
        canvasFrame.addView(canvasView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(canvasFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(buildBottomBar());

        setContentView(root);
        loadCurrentPage();

        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(220L).start();
    }

    private View buildHeader() {
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

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleLabel = text(currentNotebook.title, 16, Color.WHITE, true);
        TextView subject = text(currentNotebook.subject, 11, 0xFFC9D2E1, false);
        titleBox.addView(titleLabel);
        titleBox.addView(subject);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.setMargins(dp(8), 0, dp(6), 0);
        header.addView(titleBox, titleLp);

        Button ai = toolbarButton("AI");
        ai.setTextColor(Color.WHITE);
        ai.setBackground(rounded(0xFF405DE6, 12));
        ai.setOnClickListener(v -> openAiAssistant());
        header.addView(ai, new LinearLayout.LayoutParams(dp(48), dp(44)));

        saveLabel = text("Saved", 10, 0xFFD8DFEB, true);
        saveLabel.setGravity(Gravity.CENTER);
        saveLabel.setBackground(rounded(0xFF26334D, 14));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(dp(64), dp(34));
        saveLp.setMargins(dp(6), 0, dp(4), 0);
        header.addView(saveLabel, saveLp);

        Button more = toolbarButton("⋮");
        more.setTextSize(22);
        more.setTextColor(Color.WHITE);
        more.setBackground(rounded(0xFF26334D, 12));
        more.setOnClickListener(v -> showMoreMenu(more));
        header.addView(more, new LinearLayout.LayoutParams(dp(44), dp(44)));

        return header;
    }

    private View buildToolBar() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setBackgroundColor(0xFFF9FAFC);

        LinearLayout tools = new LinearLayout(this);
        tools.setPadding(dp(8), dp(6), dp(8), dp(6));

        writeModeButton = toolbarButton("WRITE");
        writeModeButton.setOnClickListener(v -> {
            canvasView.setWriteMode(!canvasView.isWriteMode());
            updateWriteModeButton();
        });
        tools.addView(writeModeButton);

        addTool(tools, "PEN", PaperCanvasView.TOOL_PEN);
        addTool(tools, "MARKER", PaperCanvasView.TOOL_HIGHLIGHTER);
        addTool(tools, "ERASER", PaperCanvasView.TOOL_ERASER);
        addTool(tools, "LINE", PaperCanvasView.TOOL_LINE);
        addTool(tools, "RECT", PaperCanvasView.TOOL_RECT);
        addTool(tools, "OVAL", PaperCanvasView.TOOL_OVAL);
        addTool(tools, "TEXT", PaperCanvasView.TOOL_TEXT);

        Button image = toolbarButton("IMAGE");
        image.setOnClickListener(v -> chooseImage());
        tools.addView(image);

        Button undo = toolbarButton("UNDO");
        undo.setOnClickListener(v -> canvasView.undo());
        tools.addView(undo);

        Button redo = toolbarButton("REDO");
        redo.setOnClickListener(v -> canvasView.redo());
        tools.addView(redo);

        Button export = toolbarButton("EXPORT");
        export.setOnClickListener(v -> showExportDialog());
        tools.addView(export);

        scroll.addView(tools);
        return scroll;
    }

    private void addTool(LinearLayout parent, String label, int tool) {
        Button button = toolbarButton(label);
        button.setOnClickListener(v -> canvasView.setTool(tool));
        parent.addView(button);
    }

    private View buildSettingsBar() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(4), dp(8), dp(5));
        row.setBackgroundColor(Color.WHITE);
        row.setElevation(dp(1));

        TextView penLabel = text("Size", 11, 0xFF667085, true);
        row.addView(penLabel);

        SeekBar size = new SeekBar(this);
        size.setMax(40);
        size.setProgress(8);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                canvasView.setPenSize(1.5f + progress * 0.52f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        row.addView(size, new LinearLayout.LayoutParams(dp(130), dp(42)));

        TextView smoothLabel = text("Smooth", 11, 0xFF667085, true);
        LinearLayout.LayoutParams smoothTextLp = new LinearLayout.LayoutParams(-2, dp(42));
        smoothTextLp.setMargins(dp(4), 0, 0, 0);
        row.addView(smoothLabel, smoothTextLp);

        SeekBar smooth = new SeekBar(this);
        smooth.setMax(20);
        smooth.setProgress(4);
        smooth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                canvasView.setStabilizer(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        row.addView(smooth, new LinearLayout.LayoutParams(dp(105), dp(42)));

        Button ink = toolbarButton("INK");
        ink.setOnClickListener(v -> showColorDialog());
        row.addView(ink);

        palmButton = toolbarButton("PALM ON");
        palmButton.setOnClickListener(v -> {
            canvasView.setPalmShield(!canvasView.isPalmShield());
            updatePalmButton();
        });
        row.addView(palmButton);

        marginButton = toolbarButton("MARGIN");
        marginButton.setOnClickListener(v -> {
            canvasView.setMarginEnabled(!isMarginEnabled());
            updateMarginButton();
        });
        row.addView(marginButton);

        return row;
    }

    private boolean isMarginEnabled() {
        // The editor keeps margin enabled by default. Button state reflects the last change.
        return marginButton != null && marginButton.getTag() == null
                ? true
                : marginButton != null && Boolean.TRUE.equals(marginButton.getTag());
    }

    private void updateMarginButton() {
        boolean enabled = !isMarginEnabled();
        marginButton.setTag(enabled);
        marginButton.setText(enabled ? "MARGIN OFF" : "MARGIN");
    }

    private View buildBottomBar() {
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(7), dp(5), dp(7), dp(5));
        bottom.setBackgroundColor(Color.WHITE);
        bottom.setElevation(dp(4));

        Button previous = toolbarButton("‹");
        previous.setTextSize(22);
        previous.setOnClickListener(v -> movePage(-1));
        bottom.addView(previous, new LinearLayout.LayoutParams(dp(46), dp(44)));

        LinearLayout pageBox = new LinearLayout(this);
        pageBox.setOrientation(LinearLayout.VERTICAL);
        pageBox.setGravity(Gravity.CENTER);
        pageLabel = text("Page 1 / 1", 12, 0xFF182339, true);
        pageLabel.setGravity(Gravity.CENTER);
        TextView auto = text("AUTO-SAVE", 9, 0xFF7A8495, true);
        pageAutoSet(pageBox, auto);
        pageBox.addView(pageLabel, 0);
        bottom.addView(pageBox, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button next = toolbarButton("›");
        next.setTextSize(22);
        next.setOnClickListener(v -> movePage(1));
        bottom.addView(next, new LinearLayout.LayoutParams(dp(46), dp(44)));

        Button add = toolbarButton("+ PAGE");
        add.setOnClickListener(v -> addPage());
        bottom.addView(add);

        Button paper = toolbarButton("PAPER");
        paper.setOnClickListener(v -> showPaperDialog());
        bottom.addView(paper);

        return bottom;
    }

    private void pageAutoSet(LinearLayout box, TextView auto) {
        // Kept as a helper to keep the bottom bar layout deterministic.
        box.addView(auto);
    }

    private void loadCurrentPage() {
        if (currentNotebook == null || currentNotebook.pages.isEmpty()) return;

        currentPageIndex = clampPageIndex(currentPageIndex);
        NotebookStore.PageMeta page = currentNotebook.pages.get(currentPageIndex);

        Bitmap bitmap = store.loadPageBitmap(
                page.id, PaperCanvasView.PAGE_WIDTH, PaperCanvasView.PAGE_HEIGHT);

        canvasView.loadBitmap(bitmap);
        canvasView.setPaperType(page.paperType);
        canvasView.setTool(PaperCanvasView.TOOL_PEN);
        canvasView.setWriteMode(true);
        canvasView.setPalmShield(true);
        canvasView.setMarginEnabled(true);

        pageLabel.setText("Page " + (currentPageIndex + 1)
                + " / " + currentNotebook.pages.size());
        saveLabel.setText("Saved");
        updateWriteModeButton();
        updatePalmButton();
        updateMarginButton();
    }

    private void movePage(int delta) {
        saveCurrentPageNow();
        int target = currentPageIndex + delta;
        if (target < 0 || target >= currentNotebook.pages.size()) return;
        currentPageIndex = target;
        loadCurrentPage();
    }

    private void addPage() {
        saveCurrentPageNow();
        NotebookStore.PageMeta current = currentNotebook.pages.get(currentPageIndex);
        NotebookStore.PageMeta page = store.addPage(
                currentNotebook,
                "Page " + (currentNotebook.pages.size() + 1),
                current.paperType
        );
        currentPageIndex = currentNotebook.pages.size() - 1;
        try {
            store.save(currentNotebook);
        } catch (Exception e) {
            toast("Could not save notebook.");
            return;
        }
        loadCurrentPage();
    }

    private void showPaperDialog() {
        String[] labels = {"Blank", "Ruled", "Graph", "Dot Grid", "Math"};
        String[] values = {
                PaperCanvasView.PAPER_BLANK,
                PaperCanvasView.PAPER_RULED,
                PaperCanvasView.PAPER_GRAPH,
                PaperCanvasView.PAPER_DOT,
                PaperCanvasView.PAPER_MATH
        };

        String current = currentNotebook.pages.get(currentPageIndex).paperType;
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checked = i;
                break;
            }
        }

        final int initial = checked;
        new AlertDialog.Builder(this)
                .setTitle("Paper")
                .setSingleChoiceItems(labels, checked, null)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    android.widget.ListView list = ((AlertDialog) dialog).getListView();
                    int selected = list == null ? initial : list.getCheckedItemPosition();
                    if (selected < 0 || selected >= values.length) selected = initial;

                    currentNotebook.pages.get(currentPageIndex).paperType = values[selected];
                    canvasView.setPaperType(values[selected]);
                    saveCurrentPageNow();
                })
                .show();
    }

    private void showColorDialog() {
        final int[] colors = {
                Color.rgb(24, 35, 51),
                Color.rgb(25, 74, 160),
                Color.rgb(125, 40, 72),
                Color.rgb(15, 105, 80),
                Color.rgb(102, 62, 28),
                Color.rgb(92, 49, 150)
        };
        String[] labels = {"Navy", "Blue", "Burgundy", "Green", "Brown", "Purple"};
        new AlertDialog.Builder(this)
                .setTitle("Ink color")
                .setItems(labels, (dialog, which) -> canvasView.setInkColor(colors[which]))
                .show();
    }

    private void onSaveRequested() {
        if (saveLabel != null) saveLabel.setText("Editing");
        scheduleAutosave();
    }

    @Override
    public void onCanvasDirty() {
        onSaveRequested();
    }

    private void scheduleAutosave() {
        if (pendingAutosave != null) mainHandler.removeCallbacks(pendingAutosave);
        pendingAutosave = () -> {
            pendingAutosave = null;
            requestAsyncSave();
        };
        mainHandler.postDelayed(pendingAutosave, 1200L);
    }

    private void requestAsyncSave() {
        if (destroyed || currentNotebook == null || canvasView == null) return;
        if (saveInFlight) {
            saveAgain = true;
            return;
        }

        final int pageIndex = currentPageIndex;
        final String pageId = currentNotebook.pages.get(pageIndex).id;
        final Bitmap source = canvasView.getInkBitmap();
        if (source == null || source.isRecycled()) return;

        Bitmap copy;
        try {
            copy = source.copy(Bitmap.Config.ARGB_8888, false);
        } catch (OutOfMemoryError e) {
            // Keep the editor alive. A later save can retry after the heap is less busy.
            saveLabel.setText("Save delayed");
            return;
        }

        final Bitmap snapshot = copy;
        saveInFlight = true;

        ioExecutor.submit(() -> {
            boolean ok = true;
            try {
                store.savePageBitmap(pageId, snapshot);
                store.save(currentNotebook);
            } catch (Exception e) {
                ok = false;
            } finally {
                snapshot.recycle();
                final boolean success = ok;
                runOnUiThread(() -> {
                    saveInFlight = false;
                    if (success) {
                        if (saveLabel != null) saveLabel.setText("Saved");
                    } else if (saveLabel != null) {
                        saveLabel.setText("Save error");
                    }

                    if (saveAgain && !destroyed) {
                        saveAgain = false;
                        mainHandler.postDelayed(this::requestAsyncSave, 450L);
                    }
                });
            }
        });
    }

    private void saveCurrentPageNow() {
        if (currentNotebook == null || canvasView == null) return;

        if (pendingAutosave != null) {
            mainHandler.removeCallbacks(pendingAutosave);
            pendingAutosave = null;
        }

        try {
            NotebookStore.PageMeta page = currentNotebook.pages.get(currentPageIndex);
            Bitmap source = canvasView.getInkBitmap();
            if (source != null && !source.isRecycled()) {
                store.savePageBitmap(page.id, source);
            }
            store.save(currentNotebook);
            if (saveLabel != null) saveLabel.setText("Saved");
        } catch (Exception e) {
            if (saveLabel != null) saveLabel.setText("Save error");
        }
    }

    @Override
    public void onRequestText(float pageX, float pageY) {
        EditText input = new EditText(this);
        input.setHint("Text to place on the page");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(2);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add text")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", null)
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        toast("Enter some text first.");
                        return;
                    }
                    canvasView.addText(value, pageX, pageY);
                    dialog.dismiss();
                })
        );
        dialog.show();
        input.requestFocus();
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Rename notebook");
        menu.getMenu().add("Rename page");
        menu.getMenu().add("Duplicate page");
        menu.getMenu().add("Delete page");
        menu.getMenu().add("Clear page");
        menu.getMenu().add("Import PDF");
        menu.getMenu().add("Backup notebook");
        menu.getMenu().add("Restore backup");
        menu.getMenu().add("Toggle margin");
        menu.getMenu().add("About passive stylus");

        menu.setOnMenuItemClickListener(item -> {
            switch (item.getTitle().toString()) {
                case "Rename notebook":
                    renameNotebook();
                    return true;
                case "Rename page":
                    renamePage();
                    return true;
                case "Duplicate page":
                    duplicateCurrentPage();
                    return true;
                case "Delete page":
                    confirmDeletePage();
                    return true;
                case "Clear page":
                    confirmClearPage();
                    return true;
                case "Import PDF":
                    choosePdf();
                    return true;
                case "Backup notebook":
                    chooseBackupDestination();
                    return true;
                case "Restore backup":
                    chooseRestoreFile();
                    return true;
                case "Toggle margin":
                    canvasView.setMarginEnabled(!isMarginEnabled());
                    updateMarginButton();
                    return true;
                case "About passive stylus":
                    new AlertDialog.Builder(this)
                            .setTitle("Passive stylus")
                            .setMessage(
                                    "A passive capacitive stylus is reported to Android as normal touch. " +
                                    "PaperNote uses a first-contact lock and palm-size filtering. " +
                                    "True electronic palm rejection requires an active pen digitizer."
                            )
                            .setPositiveButton("OK", null)
                            .show();
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void renameNotebook() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setText(currentNotebook.title);

        EditText subject = new EditText(this);
        subject.setSingleLine(true);
        subject.setText(currentNotebook.subject);

        box.addView(title);
        box.addView(subject);

        new AlertDialog.Builder(this)
                .setTitle("Rename notebook")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        store.renameNotebook(
                                currentNotebook,
                                title.getText().toString(),
                                subject.getText().toString()
                        );
                        titleLabel.setText(currentNotebook.title);
                    } catch (Exception e) {
                        toast("Could not rename notebook.");
                    }
                })
                .show();
    }

    private void renamePage() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(currentNotebook.pages.get(currentPageIndex).title);

        new AlertDialog.Builder(this)
                .setTitle("Rename page")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        store.renamePage(currentNotebook, currentPageIndex, input.getText().toString());
                        pageLabel.setText("Page " + (currentPageIndex + 1)
                                + " / " + currentNotebook.pages.size());
                    } catch (Exception e) {
                        toast("Could not rename page.");
                    }
                })
                .show();
    }

    private void duplicateCurrentPage() {
        saveCurrentPageNow();

        NotebookStore.PageMeta original = currentNotebook.pages.get(currentPageIndex);
        NotebookStore.PageMeta copy = store.addPage(
                currentNotebook, original.title + " Copy", original.paperType);

        Bitmap source = canvasView.getInkBitmap();
        if (source != null && !source.isRecycled()) {
            Bitmap duplicate = source.copy(Bitmap.Config.ARGB_8888, false);
            try {
                store.savePageBitmap(copy.id, duplicate);
            } catch (Exception e) {
                toast("Could not duplicate page.");
                duplicate.recycle();
                return;
            }
            duplicate.recycle();
        }

        currentPageIndex = currentNotebook.pages.size() - 1;
        try {
            store.save(currentNotebook);
        } catch (Exception e) {
            toast("Could not save notebook.");
            return;
        }
        loadCurrentPage();
    }

    private void confirmDeletePage() {
        if (currentNotebook.pages.size() <= 1) {
            toast("A notebook must keep at least one page.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete page?")
                .setMessage("This permanently removes the current page.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    saveCurrentPageNow();
                    store.removePage(currentNotebook, currentPageIndex);
                    currentPageIndex = clampPageIndex(currentPageIndex);
                    try {
                        store.save(currentNotebook);
                    } catch (Exception ignored) {
                    }
                    loadCurrentPage();
                })
                .show();
    }

    private void confirmClearPage() {
        new AlertDialog.Builder(this)
                .setTitle("Clear page?")
                .setMessage("All handwriting and inserted content on this page will be removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> canvasView.clearPage())
                .show();
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    private void choosePdf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_PDF_IMPORT);
    }

    private void importPdfPages(Uri uri) {
        final NotebookStore.NotebookMeta notebook = currentNotebook;
        final int firstImportedIndex = notebook.pages.size();

        new AlertDialog.Builder(this)
                .setTitle("Import PDF")
                .setMessage("Importing the PDF pages into this notebook. Large PDFs can take longer.")
                .setPositiveButton("OK", null)
                .show();

        ioExecutor.submit(() -> {
            int imported = 0;
            try (ParcelFileDescriptor descriptor =
                         getContentResolver().openFileDescriptor(uri, "r")) {

                if (descriptor == null) throw new Exception("Could not open the PDF.");

                android.graphics.pdf.PdfRenderer renderer =
                        new android.graphics.pdf.PdfRenderer(descriptor);

                try {
                    int count = renderer.getPageCount();
                    if (count == 0) throw new Exception("The PDF has no pages.");

                    for (int i = 0; i < count; i++) {
                        android.graphics.pdf.PdfRenderer.Page pdfPage = renderer.openPage(i);
                        try {
                            int targetWidth = Math.min(1400, pdfPage.getWidth());
                            float scale = targetWidth / (float) pdfPage.getWidth();
                            int targetHeight = Math.max(1, Math.round(pdfPage.getHeight() * scale));

                            if (targetHeight > 1900) {
                                scale = 1900f / pdfPage.getHeight();
                                targetWidth = Math.max(1, Math.round(pdfPage.getWidth() * scale));
                                targetHeight = 1900;
                            }

                            Bitmap bitmap = Bitmap.createBitmap(
                                    targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                            try {
                                bitmap.eraseColor(Color.WHITE);
                                pdfPage.render(
                                        bitmap, null, null,
                                        android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                                NotebookStore.PageMeta added = store.addPage(
                                        notebook,
                                        "PDF • Page " + (i + 1),
                                        PaperCanvasView.PAPER_BLANK
                                );
                                store.savePageBitmap(added.id, bitmap);
                                imported++;
                            } finally {
                                bitmap.recycle();
                            }
                        } finally {
                            pdfPage.close();
                        }
                    }
                    store.save(notebook);

                    final int countImported = imported;
                    final int newIndex = Math.min(
                            firstImportedIndex, notebook.pages.size() - 1);
                    runOnUiThread(() -> {
                        currentPageIndex = newIndex;
                        currentNotebook = notebook;
                        loadCurrentPage();
                        toast(countImported + " PDF page"
                                + (countImported == 1 ? "" : "s") + " imported.");
                    });
                } finally {
                    renderer.close();
                }
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("PDF import failed")
                        .setMessage(e.getMessage() == null ? "The PDF could not be imported." : e.getMessage())
                        .setPositiveButton("OK", null)
                        .show());
            }
        });
    }

    private void chooseBackupDestination() {
        saveCurrentPageNow();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, safeFileName(currentNotebook.title) + ".papernote");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_BACKUP);
    }

    private void chooseRestoreFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_RESTORE);
    }

    private void showExportDialog() {
        String[] labels = {
                "PDF • all pages",
                "PNG • one image per page",
                "JPG • one image per page",
                "WebP • one image per page",
                "ZIP • PNG pages"
        };
        ExportManager.Format[] formats = {
                ExportManager.Format.PDF,
                ExportManager.Format.PNG,
                ExportManager.Format.JPG,
                ExportManager.Format.WEBP,
                ExportManager.Format.ZIP
        };

        new AlertDialog.Builder(this)
                .setTitle("Export notebook")
                .setItems(labels, (dialog, which) -> beginExport(formats[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void beginExport(ExportManager.Format format) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingExportFormat = format;
            requestPermissions(
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_EXPORT_PERMISSION
            );
            return;
        }

        saveCurrentPageNow();
        if (saveLabel != null) saveLabel.setText("Exporting…");

        ioExecutor.submit(() -> ExportManager.export(
                this,
                store,
                currentNotebook,
                format,
                new ExportManager.Callback() {
                    @Override public void onComplete(String message) {
                        runOnUiThread(() -> {
                            if (saveLabel != null) saveLabel.setText("Saved");
                            toast(message);
                        });
                    }

                    @Override public void onError(String message) {
                        runOnUiThread(() -> {
                            if (saveLabel != null) saveLabel.setText("Saved");
                            new AlertDialog.Builder(EditorActivity.this)
                                    .setTitle("Export failed")
                                    .setMessage(message)
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
                    }
                }
        ));
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_EXPORT_PERMISSION) return;

        if (grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
                && pendingExportFormat != null) {
            ExportManager.Format format = pendingExportFormat;
            pendingExportFormat = null;
            beginExport(format);
        } else {
            pendingExportFormat = null;
            toast("Storage permission was not granted.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        try {
            if (requestCode == REQUEST_IMAGE) {
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    Bitmap decoded = android.graphics.BitmapFactory.decodeStream(in);
                    if (decoded == null) throw new Exception("Could not read the selected image.");

                    int maxDimension = 1800;
                    float scale = Math.min(
                            1f,
                            maxDimension / (float) Math.max(
                                    decoded.getWidth(), decoded.getHeight()));
                    if (scale < 1f) {
                        Bitmap scaled = Bitmap.createScaledBitmap(
                                decoded,
                                Math.max(1, Math.round(decoded.getWidth() * scale)),
                                Math.max(1, Math.round(decoded.getHeight() * scale)),
                                true
                        );
                        decoded.recycle();
                        decoded = scaled;
                    }

                    try {
                        canvasView.addImage(decoded);
                    } finally {
                        if (!decoded.isRecycled()) decoded.recycle();
                    }
                }
                return;
            }

            if (requestCode == REQUEST_BACKUP) {
                saveCurrentPageNow();
                String backup = store.exportBackup(
                        currentNotebook,
                        PaperCanvasView.PAGE_WIDTH,
                        PaperCanvasView.PAGE_HEIGHT
                );
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new Exception("Could not create backup file.");
                    out.write(backup.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                toast("Backup exported.");
                return;
            }

            if (requestCode == REQUEST_RESTORE) {
                StringBuilder builder = new StringBuilder();
                try (InputStream in = getContentResolver().openInputStream(uri);
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    if (in == null) throw new Exception("Could not open backup file.");
                    String line;
                    while ((line = reader.readLine()) != null) builder.append(line);
                }

                NotebookStore.NotebookMeta restored = store.importBackup(builder.toString());
                currentNotebook = restored;
                currentPageIndex = 0;
                loadCurrentPage();
                toast("Backup restored.");
                return;
            }

            if (requestCode == REQUEST_PDF_IMPORT) {
                importPdfPages(uri);
            }
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("Operation failed")
                    .setMessage(e.getMessage() == null ? "Please try again." : e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void openAiAssistant() {
        Intent intent = new Intent(this, AiAssistantActivity.class);
        intent.putExtra("notebook_id", currentNotebook.id);
        intent.putExtra("page_index", currentPageIndex);
        startActivity(intent);
    }

    private void updateWriteModeButton() {
        if (writeModeButton == null || canvasView == null) return;
        writeModeButton.setText(canvasView.isWriteMode() ? "WRITE" : "PAN");
    }

    private void updatePalmButton() {
        if (palmButton == null || canvasView == null) return;
        palmButton.setText(canvasView.isPalmShield() ? "PALM ON" : "PALM OFF");
    }

    private void updateMarginButton() {
        if (marginButton == null) return;
        boolean enabled = isMarginEnabled();
        marginButton.setText(enabled ? "MARGIN" : "MARGIN OFF");
        marginButton.setTag(enabled);
    }

    private Button styledButton(String label, boolean primary) {
        Button button = toolbarButton(label);
        button.setTextSize(13);
        button.setTextColor(primary ? Color.WHITE : 0xFF182339);
        button.setBackground(rounded(primary ? 0xFF405DE6 : Color.WHITE, 12));
        return button;
    }

    private Button toolbarButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setTextSize(11);
        button.setTextColor(0xFF182339);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(rounded(Color.WHITE, 11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
        lp.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(lp);
        return button;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        return t;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), color == Color.WHITE ? 0xFFE2E5EB : color);
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safeFileName(String value) {
        String cleaned = value == null
                ? "PaperNote"
                : value.replaceAll("[^a-zA-Z0-9._ -]", "_").trim();
        return cleaned.isEmpty() ? "PaperNote" : cleaned;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
