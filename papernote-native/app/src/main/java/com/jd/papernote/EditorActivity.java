package com.jd.papernote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditorActivity extends Activity implements PaperCanvasView.Listener {
    private static final int REQUEST_IMAGE = 501;
    private static final int REQUEST_EXPORT = 502;
    private static final int REQUEST_BACKUP = 503;
    private static final int REQUEST_RESTORE = 504;

    private static final int EXPORT_PDF = 1;
    private static final int EXPORT_PNG = 2;
    private static final int EXPORT_BACKUP = 3;

    private NotebookStore store;
    private SoundEngine soundEngine;
    private ExecutorService saveExecutor;
    private ExecutorService exportExecutor;
    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSave;

    private NotebookStore.NotebookMeta currentNotebook;
    private int currentPageIndex = 0;
    private PaperCanvasView canvasView;
    private TextView pageLabel;
    private TextView saveLabel;
    private TextView titleLabel;
    private Button writeModeButton;
    private Button palmButton;
    private Button soundButton;
    private FrameLayout canvasFrame;
    private TextView examTimerLabel;
    private final Handler featureHandler = new Handler(Looper.getMainLooper());
    private Runnable examTick;
    private long examEndAt = 0L;
    private String pendingPinType;
    private String pendingPinNote;
    private long lastFeatureActivityFlushAt = 0L;
    private int pendingFeatureStrokes = 0;
    private int pendingFeatureHeatX = 0;
    private int pendingFeatureHeatY = 0;
    private int pendingFeatureHeatSamples = 0;
    private int pendingFeatureTool = PaperCanvasView.TOOL_PEN;
    private int pendingExport = 0;
    private ExportManager.Format pendingExportFormat;
    private static final int REQUEST_STORAGE_PERMISSION = 505;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        store = new NotebookStore(this);
        soundEngine = new SoundEngine(this);
        saveExecutor = Executors.newSingleThreadExecutor();
        exportExecutor = Executors.newSingleThreadExecutor();

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(23, 32, 51));
        window.setNavigationBarColor(Color.rgb(23, 32, 51));

        String notebookId = getIntent().getStringExtra("notebook_id");
        if (notebookId == null || notebookId.trim().isEmpty()) {
            finish();
            return;
        }

        try {
            currentNotebook = store.get(notebookId);
            currentPageIndex = getIntent().getIntExtra("page_index", 0);
            currentPageIndex = Math.max(0, Math.min(currentPageIndex, currentNotebook.pages.size() - 1));
            buildEditor();
        } catch (Exception e) {
            Toast.makeText(this, "Could not open notebook", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        saveHandler.removeCallbacksAndMessages(null);
        featureHandler.removeCallbacksAndMessages(null);
        stopExamTimer(false);
        if (currentNotebook != null && !currentNotebook.pages.isEmpty()) {
            try {
                store.flushPageActivity(currentNotebook.pages.get(currentPageIndex).id);
            } catch (Exception ignored) {
            }
        }
        flushPendingFeatureStroke(); 
        saveCurrentPageNow();
        if (saveExecutor != null) saveExecutor.shutdown();
        if (exportExecutor != null) exportExecutor.shutdown();
        if (soundEngine != null) soundEngine.close();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        saveHandler.removeCallbacksAndMessages(null);
        featureHandler.removeCallbacksAndMessages(null);
        stopExamTimer(false);
        if (currentNotebook != null && !currentNotebook.pages.isEmpty()) {
            try { store.flushPageActivity(currentNotebook.pages.get(currentPageIndex).id); } catch (Exception ignored) {}
        }
        flushPendingFeatureStroke();
        saveCurrentPageNow();
        finish();
    }

    private void showHome() {
        currentNotebook = null;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 247, 250));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22), dp(24), dp(22), dp(18));
        header.setBackgroundColor(Color.rgb(23, 32, 51));

        TextView brand = text("PaperNote", 30, Color.WHITE, true);
        TextView subtitle = text("A handwriting-first study notebook for Maths, Physics, Chemistry and everyday learning.", 14, 0xFFD5DCE8, false);
        subtitle.setPadding(0, dp(6), 0, 0);
        header.addView(brand);
        header.addView(subtitle);
        root.addView(header);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(dp(16), dp(14), dp(16), dp(8));

        Button newButton = styledButton("+ New notebook", true);
        newButton.setOnClickListener(v -> showNewNotebookDialog(null, null, PaperCanvasView.PAPER_RULED));
        actionRow.addView(newButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button restoreButton = styledButton("Restore", false);
        restoreButton.setOnClickListener(v -> chooseRestoreFile());
        LinearLayout.LayoutParams rb = new LinearLayout.LayoutParams(dp(100), dp(48));
        rb.setMargins(dp(10), 0, 0, 0);
        actionRow.addView(restoreButton, rb);
        root.addView(actionRow);

        EditText search = new EditText(this);
        search.setHint("Search notebooks…");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable searchBg = rounded(0xFFFFFFFF, 14);
        search.setBackground(searchBg);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(48));
        searchLp.setMargins(dp(16), dp(6), dp(16), dp(12));
        root.addView(search, searchLp);

        HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(dp(16), 0, dp(16), dp(10));
        addChip(chips, "Math Practice", () -> showNewNotebookDialog("Math Practice", "Mathematics", PaperCanvasView.PAPER_GRAPH));
        addChip(chips, "Physics", () -> showNewNotebookDialog("Physics Notes", "Physics", PaperCanvasView.PAPER_RULED));
        addChip(chips, "Chemistry", () -> showNewNotebookDialog("Chemistry Notes", "Chemistry", PaperCanvasView.PAPER_RULED));
        addChip(chips, "Blank Notebook", () -> showNewNotebookDialog("New Notebook", "General Study", PaperCanvasView.PAPER_BLANK));
        chipsScroll.addView(chips);
        root.addView(chipsScroll);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), 0, dp(16), dp(16));

        List<NotebookStore.NotebookMeta> notebooks = store.list();
        for (NotebookStore.NotebookMeta notebook : notebooks) {
            addNotebookCard(list, notebook);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);

        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim().toLowerCase();
                list.removeAllViews();
                for (NotebookStore.NotebookMeta notebook : store.list()) {
                    if (q.isEmpty()
                            || notebook.title.toLowerCase().contains(q)
                            || notebook.subject.toLowerCase().contains(q)) {
                        addNotebookCard(list, notebook);
                    }
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void addNotebookCard(LinearLayout parent, NotebookStore.NotebookMeta notebook) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(14), dp(14), dp(14));
        card.setBackground(rounded(0xFFFFFFFF, 18));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView title = text(notebook.title, 19, Color.rgb(23, 32, 51), true);
        TextView info = text(notebook.subject + "  •  " + notebook.pages.size() + " page" + (notebook.pages.size() == 1 ? "" : "s"), 13, 0xFF6E7788, false);
        labels.addView(title);
        labels.addView(info);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        Button open = styledButton("Open", false);
        open.setOnClickListener(v -> openNotebook(notebook.id));
        row.addView(open, new LinearLayout.LayoutParams(dp(80), dp(44)));

        card.addView(row);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, dp(10));
        parent.addView(card, cp);
    }

    private void showNewNotebookDialog(String presetTitle, String presetSubject, String presetPaper) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);

        EditText title = new EditText(this);
        title.setHint("Notebook title");
        title.setSingleLine(true);
        title.setText(presetTitle == null ? "" : presetTitle);

        EditText subject = new EditText(this);
        subject.setHint("Subject");
        subject.setSingleLine(true);
        subject.setText(presetSubject == null ? "" : presetSubject);

        TextView helper = text("Choose a paper style after creating the notebook.", 13, 0xFF6E7788, false);
        helper.setPadding(0, dp(6), 0, 0);
        box.addView(title);
        box.addView(subject);
        box.addView(helper);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Create notebook")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                NotebookStore.NotebookMeta created = store.create(
                        title.getText().toString(),
                        subject.getText().toString(),
                        presetPaper == null ? PaperCanvasView.PAPER_RULED : presetPaper
                );
                dialog.dismiss();
                openNotebook(created.id);
            } catch (Exception e) {
                toast("Could not create notebook");
            }
        }));
        dialog.show();
    }

    private void openNotebook(String id) {
        try {
            currentNotebook = store.get(id);
            currentPageIndex = 0;
            buildEditor();
        } catch (Exception e) {
            toast("Could not open notebook");
        }
    }

    private void buildEditor() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFE9EDF3);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(7), dp(8), dp(7));
        header.setBackgroundColor(0xFF182339);
        header.setElevation(dp(4));

        Button back = toolbarButton("‹");
        back.setTextSize(22);
        back.setOnClickListener(v -> onBackPressed());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleLabel = text(currentNotebook.title, 17, Color.WHITE, true);
        TextView subject = text(currentNotebook.subject, 12, 0xFFC9D2E1, false);
        titleBox.addView(titleLabel);
        titleBox.addView(subject);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.setMargins(dp(10), 0, dp(6), 0);
        header.addView(titleBox, titleParams);

        saveLabel = text("Saved", 11, 0xFFD4DBE7, true);
        saveLabel.setGravity(Gravity.CENTER);
        saveLabel.setBackground(rounded(0xFF26334D, 18));
        header.addView(saveLabel, new LinearLayout.LayoutParams(dp(68), dp(34)));

        Button hub = toolbarButton("HUB");
        hub.setOnClickListener(v -> startActivity(new Intent(this, StudyHubActivity.class)));
        header.addView(hub, new LinearLayout.LayoutParams(dp(56), dp(44)));

        Button more = toolbarButton("⋮");
        more.setTextSize(22);
        more.setOnClickListener(v -> showMoreMenu(more));
        header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(44)));
        root.addView(header);

        TextView pageHint = text("WRITE MODE  •  Use the controls below for pen, marker, eraser and study tools.", 11, 0xFF5C6678, true);
        pageHint.setPadding(dp(14), dp(7), dp(14), dp(5));
        root.addView(pageHint);

        HorizontalScrollView toolScroll = new HorizontalScrollView(this);
        toolScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = new LinearLayout(this);
        tools.setPadding(dp(9), dp(4), dp(9), dp(7));

        writeModeButton = toolbarButton("WRITE");
        writeModeButton.setOnClickListener(v -> {
            canvasView.setWriteMode(!canvasView.isWriteMode());
            updateWriteModeButton();
        });
        tools.addView(writeModeButton);

        Button pen = toolbarButton("PEN");
        pen.setOnClickListener(v -> canvasView.setTool(PaperCanvasView.TOOL_PEN));
        tools.addView(pen);

        Button marker = toolbarButton("MARKER");
        marker.setOnClickListener(v -> canvasView.setTool(PaperCanvasView.TOOL_HIGHLIGHTER));
        tools.addView(marker);

        Button eraser = toolbarButton("ERASER");
        eraser.setOnClickListener(v -> canvasView.setTool(PaperCanvasView.TOOL_ERASER));
        tools.addView(eraser);

        Button line = toolbarButton("LINE");
        line.setOnClickListener(v -> canvasView.setTool(PaperCanvasView.TOOL_LINE));
        tools.addView(line);

        Button rect = toolbarButton("RECT");
        rect.setOnClickListener(v -> canvasView.setTool(PaperCanvasView.TOOL_RECT));
        tools.addView(rect);

        Button oval = toolbarButton("OVAL");
        oval.setOnClickListener(v -> canvasView.setTool(PaperCanvasView.TOOL_OVAL));
        tools.addView(oval);

        Button addText = toolbarButton("TEXT");
        addText.setOnClickListener(v -> canvasView.setTool(PaperCanvasView.TOOL_TEXT));
        tools.addView(addText);

        Button image = toolbarButton("IMAGE");
        image.setOnClickListener(v -> chooseImage());
        tools.addView(image);

        Button undo = toolbarButton("UNDO");
        undo.setOnClickListener(v -> canvasView.undo());
        tools.addView(undo);

        Button redo = toolbarButton("REDO");
        redo.setOnClickListener(v -> canvasView.redo());
        tools.addView(redo);

        Button study = toolbarButton("STUDY");
        study.setOnClickListener(v -> showStudyTools(study));
        tools.addView(study);

        toolScroll.addView(tools);
        root.addView(toolScroll);

        LinearLayout quickBar = new LinearLayout(this);
        quickBar.setGravity(Gravity.CENTER_VERTICAL);
        quickBar.setPadding(dp(10), dp(4), dp(10), dp(6));
        quickBar.setBackgroundColor(Color.WHITE);
        quickBar.setElevation(dp(2));

        TextView sizeLabel = text("Pen", 11, 0xFF596273, true);
        quickBar.addView(sizeLabel);

        SeekBar size = new SeekBar(this);
        size.setMax(45);
        size.setProgress(8);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                canvasView.setPenSize(1.5f + progress * 0.58f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        quickBar.addView(size, new LinearLayout.LayoutParams(dp(145), dp(42)));

        TextView smoothLabel = text("Smooth", 11, 0xFF596273, true);
        LinearLayout.LayoutParams smoothLabelParams = new LinearLayout.LayoutParams(-2, dp(42));
        smoothLabelParams.setMargins(dp(8), 0, 0, 0);
        quickBar.addView(smoothLabel, smoothLabelParams);

        SeekBar smooth = new SeekBar(this);
        smooth.setMax(25);
        smooth.setProgress(6);
        smooth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                canvasView.setStabilizer(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        quickBar.addView(smooth, new LinearLayout.LayoutParams(dp(115), dp(42)));

        Button color = toolbarButton("INK");
        color.setOnClickListener(v -> showColorDialog());
        quickBar.addView(color);

        palmButton = toolbarButton("PALM");
        palmButton.setOnClickListener(v -> {
            canvasView.setPalmShield(!canvasView.isPalmShield());
            updatePalmButton();
        });
        quickBar.addView(palmButton);

        soundButton = toolbarButton("SOUND");
        soundButton.setOnClickListener(v -> {
            boolean enabled = !soundEngine.isEnabled();
            soundEngine.setEnabled(enabled);
            updateSoundButton();
        });
        quickBar.addView(soundButton);

        root.addView(quickBar);

        canvasFrame = new FrameLayout(this);
        canvasFrame.setPadding(dp(8), dp(7), dp(8), dp(7));
        canvasView = new PaperCanvasView(this);
        canvasView.setListener(this);
        canvasView.setSoundEngine(soundEngine);
        canvasView.setInteractionListener(new PaperCanvasView.InteractionListener() {
            @Override
            public void onStrokeStarted(float pageX, float pageY, int tool) {
                if (currentNotebook == null || currentNotebook.pages.isEmpty()) return;
                pendingFeatureStrokes++;
                pendingFeatureHeatX += Math.round(pageX);
                pendingFeatureHeatY += Math.round(pageY);
                pendingFeatureTool = tool;
                pendingFeatureHeatSamples++;
                if (pendingFeatureStrokes >= 3) flushPendingFeatureStroke();
            }

            @Override
            public void onPinPlaced(float pageX, float pageY) {
                placePendingStudyMark(pageX, pageY);
            }
        });
        canvasFrame.addView(canvasView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(canvasFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(7), dp(5), dp(7), dp(5));
        bottom.setBackgroundColor(Color.WHITE);
        bottom.setElevation(dp(8));

        Button prev = toolbarButton("‹");
        prev.setTextSize(22);
        prev.setOnClickListener(v -> movePage(-1));
        bottom.addView(prev, new LinearLayout.LayoutParams(dp(46), dp(44)));

        LinearLayout pageBox = new LinearLayout(this);
        pageBox.setOrientation(LinearLayout.VERTICAL);
        pageBox.setGravity(Gravity.CENTER);
        pageLabel = text("Page 1 / 1", 13, 0xFF182339, true);
        pageLabel.setGravity(Gravity.CENTER);
        TextView autoSave = text("AUTO-SAVE ON", 9, 0xFF7A8495, true);
        autoSave.setGravity(Gravity.CENTER);
        pageBox.addView(pageLabel);
        pageBox.addView(autoSave);
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

        Button export = toolbarButton("EXPORT");
        export.setOnClickListener(v -> showExportDialog());
        bottom.addView(export);

        root.addView(bottom);

        setContentView(root);
        loadCurrentPage();
        updateWriteModeButton();
        updatePalmButton();
        updateSoundButton();

        // Subtle editor entrance: content arrives without covering the canvas with a modal animation.
        header.setAlpha(0f);
        header.animate().alpha(1f).setDuration(260).start();
        canvasFrame.setAlpha(0f);
        canvasFrame.setTranslationY(dp(16));
        canvasFrame.animate().alpha(1f).translationY(0f)
                .setDuration(360)
                .setStartDelay(80)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void loadCurrentPage() {
        if (currentNotebook == null || currentNotebook.pages.isEmpty()) return;
        NotebookStore.PageMeta page = currentNotebook.pages.get(currentPageIndex);
        Bitmap bitmap = store.loadPageBitmap(page.id, PaperCanvasView.PAGE_WIDTH, PaperCanvasView.PAGE_HEIGHT);
        canvasView.setPaperType(page.paperType);
        canvasView.loadBitmap(bitmap);
        refreshStudyPins();
        store.startPageSession(page.id);
        lastFeatureActivityFlushAt = System.currentTimeMillis();
        pageLabel.setText("Page " + (currentPageIndex + 1) + " / " + currentNotebook.pages.size());
        saveLabel.setText("Saved");
    }

    private void saveCurrentPageNow() {
        if (currentNotebook == null || canvasView == null) return;
        try {
            NotebookStore.PageMeta page = currentNotebook.pages.get(currentPageIndex);
            if (canvasView.getInkBitmap() != null) {
                store.savePageBitmap(page.id, canvasView.getInkBitmap());
            }
            store.save(currentNotebook);
            if (saveLabel != null) saveLabel.setText("Saved");
        } catch (Exception e) {
            toast("Save failed");
        }
    }

    private void saveCurrentPage() {
        if (currentNotebook == null || canvasView == null) return;

        if (pendingSave != null) {
            saveHandler.removeCallbacks(pendingSave);
        }

        pendingSave = () -> {
            saveCurrentPageSnapshotAsync();
            pendingSave = null;
        };

        saveHandler.postDelayed(pendingSave, 650L);
    }

    private void saveCurrentPageSnapshotAsync() {
        if (currentNotebook == null || canvasView == null) return;

        try {
            NotebookStore.PageMeta page = currentNotebook.pages.get(currentPageIndex);
            Bitmap source = canvasView.getInkBitmap();
            if (source == null) return;

            // Only make the expensive page copy after the user has paused writing.
            // This removes the visible pause that used to happen after every stroke.
            Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, false);
            store.save(currentNotebook);
            saveExecutor.submit(() -> {
                try {
                    store.savePageBitmap(page.id, copy);
                    copy.recycle();
                    runOnUiThread(() -> {
                        if (saveLabel != null) saveLabel.setText("Saved");
                    });
                } catch (Exception ignored) {
                    copy.recycle();
                    runOnUiThread(() -> {
                        if (saveLabel != null) saveLabel.setText("Save error");
                    });
                }
            });
        } catch (Exception e) {
            toast("Save failed");
        }
    }

    @Override
    public void onCanvasDirty() {
        if (saveLabel != null) saveLabel.setText("Editing");
        if (currentNotebook != null && !currentNotebook.pages.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastFeatureActivityFlushAt >= 5000L) {
                store.recordPageActivity(currentNotebook.pages.get(currentPageIndex).id);
                lastFeatureActivityFlushAt = now;
            }
        }
        saveCurrentPage();
    }

    @Override
    public void onRequestText(float pageX, float pageY) {
        final EditText input = new EditText(this);
        input.setHint("Type text to place on the page");
        input.setMinLines(2);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setPadding(dp(8), dp(4), dp(8), dp(4));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add text")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (!value.isEmpty()) {
                canvasView.addText(value, pageX, pageY);
                dialog.dismiss();
            }
        }));
        dialog.show();
        input.requestFocus();
    }

    private void addPage() {
        saveCurrentPageNow();
        NotebookStore.PageMeta page = store.addPage(
                currentNotebook,
                "Page " + (currentNotebook.pages.size() + 1),
                currentNotebook.pages.isEmpty() ? PaperCanvasView.PAPER_RULED : currentNotebook.pages.get(currentPageIndex).paperType
        );
        currentPageIndex = currentNotebook.pages.size() - 1;
        try {
            store.save(currentNotebook);
        } catch (Exception ignored) {
        }
        loadCurrentPage();
    }

    private void movePage(int delta) {
        saveCurrentPageNow();
        int next = currentPageIndex + delta;
        if (next < 0 || next >= currentNotebook.pages.size()) return;
        currentPageIndex = next;
        loadCurrentPage();
    }

    private void showPaperDialog() {
        String[] items = {
                "Blank", "Ruled", "Graph", "Dot Grid", "Math Practice",
                "2-Mark Answer", "3-Mark Answer", "4-Mark Answer", "Science Experiment"
        };
        String[] values = {
                PaperCanvasView.PAPER_BLANK,
                PaperCanvasView.PAPER_RULED,
                PaperCanvasView.PAPER_GRAPH,
                PaperCanvasView.PAPER_DOT,
                PaperCanvasView.PAPER_MATH,
                PaperCanvasView.PAPER_EXAM_2,
                PaperCanvasView.PAPER_EXAM_3,
                PaperCanvasView.PAPER_EXAM_4,
                PaperCanvasView.PAPER_EXPERIMENT
        };
        int checked = 1;
        String current = currentNotebook.pages.get(currentPageIndex).paperType;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) checked = i;

        final int defaultChecked = checked;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Paper template")
                .setSingleChoiceItems(items, checked, null)
                .setPositiveButton("Apply", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            ListViewCompat selection = new ListViewCompat(dialog);
            int which = selection.getCheckedItemPosition();
            if (which < 0) which = defaultChecked;
            currentNotebook.pages.get(currentPageIndex).paperType = values[which];
            canvasView.setPaperType(values[which]);
            saveCurrentPageNow();
            try { store.save(currentNotebook); } catch (Exception ignored) {}
            dialog.dismiss();
        }));
        dialog.show();
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


    private void showStudyTools(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Study marks  •  doubt / mistake / important / revise");
        menu.getMenu().add("Study inbox");
        menu.getMenu().add("Page analytics  •  time / strokes / heatmap");
        menu.getMenu().add(canvasView != null && canvasView.isRecallMode() ? "Reveal recall page" : "Recall cover");
        menu.getMenu().add("Ghost page  •  snapshot / compare");
        menu.getMenu().add("Exam practice  •  timed answer");
        menu.getMenu().add("Science experiment template");
        menu.getMenu().add("Concept thread");
        menu.getMenu().add("Quick-share notebook backup");
        if (examEndAt > 0L) menu.getMenu().add("Stop exam timer");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.startsWith("Study marks")) {
                showStudyMarksMenu();
                return true;
            }
            if ("Study inbox".equals(title)) {
                showStudyInbox();
                return true;
            }
            if (title.startsWith("Page analytics")) {
                showPageAnalytics();
                return true;
            }
            if (title.contains("Recall") || title.contains("Reveal recall")) {
                canvasView.setRecallMode(!canvasView.isRecallMode());
                toast(canvasView.isRecallMode()
                        ? "Recall cover active. Your page is safely hidden until you reveal it."
                        : "Recall page revealed.");
                return true;
            }
            if (title.startsWith("Ghost page")) {
                showGhostPageMenu();
                return true;
            }
            if (title.startsWith("Exam practice")) {
                showExamPractice();
                return true;
            }
            if (title.startsWith("Science experiment")) {
                showExperimentTemplate();
                return true;
            }
            if (title.startsWith("Concept thread")) {
                showConceptThread();
                return true;
            }
            if (title.startsWith("Quick-share")) {
                shareNotebookBackup();
                return true;
            }
            if ("Stop exam timer".equals(title)) {
                stopExamTimer(true);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showStudyMarksMenu() {
        final String[] labels = {
                "Doubt  •  I need to understand this",
                "Mistake  •  I got this wrong",
                "Important  •  high-value revision point",
                "Revise  •  come back before the exam"
        };
        final String[] types = {
                NotebookStore.StudyMark.DOUBT,
                NotebookStore.StudyMark.MISTAKE,
                NotebookStore.StudyMark.IMPORTANT,
                NotebookStore.StudyMark.REVISE
        };
        new AlertDialog.Builder(this)
                .setTitle("Pin a study marker")
                .setItems(labels, (dialog, which) -> requestStudyMark(types[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void requestStudyMark(String type) {
        EditText note = new EditText(this);
        note.setHint("Optional note, e.g. Why does current fall here?");
        note.setSingleLine(false);
        note.setMinLines(2);
        note.setPadding(dp(8), dp(5), dp(8), dp(5));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add " + studyTypeLabel(type).toLowerCase())
                .setView(note)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Place on page", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            pendingPinType = type;
            pendingPinNote = note.getText().toString().trim();
            dialog.dismiss();
            canvasView.beginPinPlacement();
            toast("Tap the exact spot on the page.");
        }));
        dialog.show();
    }

    private void placePendingStudyMark(float pageX, float pageY) {
        if (pendingPinType == null || currentNotebook == null) return;
        try {
            String pageId = currentNotebook.pages.get(currentPageIndex).id;
            store.addStudyMark(pageId, pendingPinType, pageX, pageY, pendingPinNote);
            toast(studyTypeLabel(pendingPinType) + " pinned");
            pendingPinType = null;
            pendingPinNote = null;
            refreshStudyPins();
        } catch (Exception e) {
            toast("Could not place study mark");
        }
    }

    private void refreshStudyPins() {
        if (canvasView == null || currentNotebook == null || currentNotebook.pages.isEmpty()) return;
        String pageId = currentNotebook.pages.get(currentPageIndex).id;
        java.util.ArrayList<PaperCanvasView.StudyPin> pins = new java.util.ArrayList<>();
        for (NotebookStore.StudyMark mark : store.getStudyMarks(pageId)) {
            pins.add(new PaperCanvasView.StudyPin(
                    mark.x,
                    mark.y,
                    mark.type,
                    studyTypeShort(mark.type),
                    mark.resolved
            ));
        }
        canvasView.setStudyPins(pins);
    }

    private String studyTypeShort(String type) {
        if (NotebookStore.StudyMark.DOUBT.equals(type)) return "D";
        if (NotebookStore.StudyMark.MISTAKE.equals(type)) return "M";
        if (NotebookStore.StudyMark.IMPORTANT.equals(type)) return "!";
        return "R";
    }

    private String studyTypeLabel(String type) {
        if (NotebookStore.StudyMark.DOUBT.equals(type)) return "Doubt";
        if (NotebookStore.StudyMark.MISTAKE.equals(type)) return "Mistake";
        if (NotebookStore.StudyMark.IMPORTANT.equals(type)) return "Important";
        return "Revise";
    }

    private void showStudyInbox() {
        if (currentNotebook == null) return;

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(4), dp(8), dp(8));

        int total = 0;
        for (int pageIndex = 0; pageIndex < currentNotebook.pages.size(); pageIndex++) {
            final int openPageIndex = pageIndex;
            NotebookStore.PageMeta page = currentNotebook.pages.get(pageIndex);
            for (NotebookStore.StudyMark mark : store.getStudyMarks(page.id)) {
                final NotebookStore.StudyMark marker = mark;
                final NotebookStore.PageMeta markerPage = page;
                total++;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(10), dp(9), dp(10), dp(9));
                row.setBackground(rounded(marker.resolved ? 0xFFF2F4F7 : 0xFFFFFFFF, 14));

                LinearLayout top = new LinearLayout(this);
                top.setGravity(Gravity.CENTER_VERTICAL);
                TextView label = text(
                        studyTypeLabel(marker.type) + (marker.resolved ? "  ✓" : ""),
                        14, marker.resolved ? 0xFF667085 : 0xFF182339, true);
                top.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

                Button open = toolbarButton("OPEN");
                open.setOnClickListener(v -> {
                    saveCurrentPageNow();
                    currentPageIndex = openPageIndex;
                    loadCurrentPage();
                    canvasView.centerOnPagePoint(marker.x, marker.y);
                    toast("Showing " + studyTypeLabel(marker.type).toLowerCase() + " on " + markerPage.title);
                });
                top.addView(open);

                Button resolve = toolbarButton(marker.resolved ? "REOPEN" : "RESOLVE");
                resolve.setOnClickListener(v -> {
                    try {
                        store.setStudyMarkResolved(markerPage.id, marker.id, !marker.resolved);
                        refreshStudyPins();
                        showStudyInbox();
                    } catch (Exception e) {
                        toast("Could not update marker");
                    }
                });
                top.addView(resolve);

                row.addView(top);
                row.addView(text(markerPage.title + "  •  " + marker.note, 12, 0xFF687385, false));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.setMargins(0, 0, 0, dp(7));
                list.addView(row, rp);
            }
        }

        if (total == 0) {
            list.addView(text("No study marks on this notebook yet.", 14, 0xFF687385, false));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(list);
        new AlertDialog.Builder(this)
                .setTitle("Study inbox  •  " + total)
                .setView(scroll)
                .setPositiveButton("Done", null)
                .show();
    }

    private void showPageAnalytics() {
        if (currentNotebook == null || currentNotebook.pages.isEmpty()) return;
        String pageId = currentNotebook.pages.get(currentPageIndex).id;
        NotebookStore.PageStats stats = store.getPageStats(pageId);
        int doubts = store.getStudyMarks(pageId).stream()
                .mapToInt(mark -> NotebookStore.StudyMark.DOUBT.equals(mark.type) && !mark.resolved ? 1 : 0)
                .sum();
        int mistakes = store.getStudyMarks(pageId).stream()
                .mapToInt(mark -> NotebookStore.StudyMark.MISTAKE.equals(mark.type) && !mark.resolved ? 1 : 0)
                .sum();
        int important = store.getStudyMarks(pageId).stream()
                .mapToInt(mark -> NotebookStore.StudyMark.IMPORTANT.equals(mark.type) && !mark.resolved ? 1 : 0)
                .sum();
        int revise = store.getStudyMarks(pageId).stream()
                .mapToInt(mark -> NotebookStore.StudyMark.REVISE.equals(mark.type) && !mark.resolved ? 1 : 0)
                .sum();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(2), dp(8), 0);
        box.addView(text(
                stats.strokes + " strokes  •  " + formatStudyDuration(stats.activeMs) +
                        " active  •  " + (doubts + mistakes + important + revise) + " open markers",
                14, 0xFF182339, true
        ));
        HeatmapView heatmap = new HeatmapView(this, stats.heatmap);
        box.addView(heatmap, new LinearLayout.LayoutParams(-1, dp(170)));
        box.addView(text(
                "Heatmap = where you most often start handwriting on this page. " +
                        "It is a local editing signal, not a judgment of academic ability.",
                11, 0xFF687385, false
        ));
        box.addView(text(
                "Doubt " + doubts + "  •  Mistake " + mistakes + "  •  Important " + important + "  •  Revise " + revise,
                12, 0xFF596273, true
        ));
        new AlertDialog.Builder(this)
                .setTitle("Page analytics  •  " + currentNotebook.pages.get(currentPageIndex).title)
                .setView(box)
                .setPositiveButton("Done", null)
                .show();
    }

    private String formatStudyDuration(long ms) {
        long minutes = Math.max(0L, ms / 60000L);
        if (minutes < 60) return minutes + " min";
        return (minutes / 60) + "h " + String.format(java.util.Locale.US, "%02dm", minutes % 60);
    }

    private void showGhostPageMenu() {
        String[] items = {
                "Capture snapshot of current page",
                "Show / hide ghost overlay",
                "Clear saved snapshot"
        };
        new AlertDialog.Builder(this)
                .setTitle("Ghost page")
                .setItems(items, (dialog, which) -> {
                    NotebookStore.PageMeta page = currentNotebook.pages.get(currentPageIndex);
                    if (which == 0) {
                        Bitmap source = canvasView.getInkBitmap();
                        if (source != null) {
                            Bitmap copy = source.copy(Bitmap.Config.ARGB_8888, false);
                            try {
                                store.savePageGhostSnapshot(page.id, copy);
                                toast("Ghost snapshot saved");
                            } catch (Exception e) {
                                toast("Could not save snapshot");
                            } finally {
                                copy.recycle();
                            }
                        }
                    } else if (which == 1) {
                        if (canvasView.hasGhostBitmap()) {
                            canvasView.clearGhostBitmap();
                            toast("Ghost overlay hidden");
                        } else {
                            Bitmap ghost = store.loadPageGhostSnapshot(
                                    page.id, PaperCanvasView.PAGE_WIDTH, PaperCanvasView.PAGE_HEIGHT);
                            if (ghost == null) {
                                toast("No snapshot yet. Capture one first.");
                            } else {
                                canvasView.setGhostBitmap(ghost, 0.24f);
                                toast("Ghost overlay shown");
                            }
                        }
                    } else {
                        store.deletePageGhostSnapshot(page.id);
                        canvasView.clearGhostBitmap();
                        toast("Ghost snapshot deleted");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showExamPractice() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(4), dp(16), 0);

        EditText question = new EditText(this);
        question.setHint("Question or task");
        question.setMinLines(2);
        question.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        EditText marks = new EditText(this);
        marks.setHint("Marks: 2, 3 or 4");
        marks.setSingleLine(true);
        marks.setInputType(InputType.TYPE_CLASS_NUMBER);

        EditText minutes = new EditText(this);
        minutes.setHint("Time in minutes, e.g. 6");
        minutes.setSingleLine(true);
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        box.addView(question);
        box.addView(marks);
        box.addView(minutes);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Exam practice")
                .setMessage("Create a fresh timed answer page. Practice time and space are recorded locally.")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int markValue;
            int timeMinutes;
            try {
                markValue = Integer.parseInt(marks.getText().toString().trim());
                timeMinutes = Integer.parseInt(minutes.getText().toString().trim());
            } catch (Exception e) {
                toast("Enter valid marks and time");
                return;
            }
            if (markValue < 2 || markValue > 4 || timeMinutes < 1 || timeMinutes > 180) {
                toast("Use 2–4 marks and 1–180 minutes");
                return;
            }
            String q = question.getText().toString().trim();
            if (q.isEmpty()) {
                toast("Enter the question or task");
                return;
            }
            saveCurrentPageNow();
            String paper = markValue == 2 ? PaperCanvasView.PAPER_EXAM_2
                    : markValue == 3 ? PaperCanvasView.PAPER_EXAM_3 : PaperCanvasView.PAPER_EXAM_4;
            NotebookStore.PageMeta page = store.addPage(
                    currentNotebook,
                    "Exam Practice  •  " + markValue + " marks",
                    paper
            );
            currentPageIndex = currentNotebook.pages.size() - 1;
            try { store.save(currentNotebook); } catch (Exception ignored) {}
            loadCurrentPage();
            canvasView.addText("Question: " + q, 145f, 155f);
            startExamTimer(timeMinutes);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void startExamTimer(int minutes) {
        stopExamTimer(false);
        examEndAt = System.currentTimeMillis() + minutes * 60L * 1000L;
        examTimerLabel = text(formatExamTime(minutes * 60L), 12, Color.WHITE, true);
        examTimerLabel.setGravity(Gravity.CENTER);
        examTimerLabel.setBackground(rounded(0xFFB23A48, 16));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(94), dp(38), Gravity.TOP | Gravity.END);
        lp.setMargins(0, dp(8), dp(14), 0);
        canvasFrame.addView(examTimerLabel, lp);

        examTick = new Runnable() {
            @Override public void run() {
                long remaining = Math.max(0L, examEndAt - System.currentTimeMillis());
                examTimerLabel.setText(formatExamTime(remaining / 1000L));
                if (remaining <= 0L) {
                    stopExamTimer(true);
                    toast("Exam timer finished. Your answer page is saved.");
                    return;
                }
                featureHandler.postDelayed(this, 500L);
            }
        };
        featureHandler.post(examTick);
        toast("Exam practice started");
    }

    private String formatExamTime(long totalSeconds) {
        long minutes = Math.max(0L, totalSeconds) / 60L;
        long seconds = Math.max(0L, totalSeconds) % 60L;
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void stopExamTimer(boolean keepMessage) {
        if (examTick != null) featureHandler.removeCallbacks(examTick);
        examTick = null;
        examEndAt = 0L;
        if (examTimerLabel != null && examTimerLabel.getParent() != null) {
            ((android.view.ViewGroup) examTimerLabel.getParent()).removeView(examTimerLabel);
        }
        examTimerLabel = null;
        if (keepMessage && saveLabel != null) saveLabel.setText("Saved");
    }

    private void showExperimentTemplate() {
        EditText title = new EditText(this);
        title.setHint("Experiment title, e.g. To verify Ohm's law");
        title.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Science experiment")
                .setMessage("Creates a dedicated experiment page with Aim, Apparatus, Procedure, Observations, Calculations, Result and Precautions sections.")
                .setView(title)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (dialog, which) -> {
                    String value = title.getText().toString().trim();
                    if (value.isEmpty()) value = "New Science Experiment";
                    saveCurrentPageNow();
                    NotebookStore.PageMeta page = store.addPage(
                            currentNotebook, value, PaperCanvasView.PAPER_EXPERIMENT);
                    currentPageIndex = currentNotebook.pages.size() - 1;
                    try { store.save(currentNotebook); } catch (Exception ignored) {}
                    loadCurrentPage();
                })
                .show();
    }

    private void showConceptThread() {
        if (currentNotebook.pages.size() < 2) {
            toast("Add another page before creating a concept thread");
            return;
        }
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> indices = new java.util.ArrayList<>();
        for (int i = 0; i < currentNotebook.pages.size(); i++) {
            if (i == currentPageIndex) continue;
            NotebookStore.PageMeta p = currentNotebook.pages.get(i);
            labels.add(p.title);
            indices.add(i);
        }
        new AlertDialog.Builder(this)
                .setTitle("Connect this page to…")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    int targetIndex = indices.get(which);
                    EditText label = new EditText(this);
                    label.setHint("Connection label, e.g. uses Kirchhoff's law");
                    new AlertDialog.Builder(this)
                            .setTitle("Name concept thread")
                            .setView(label)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Link", (d, w) -> {
                                try {
                                    store.addStudyLink(
                                            currentNotebook.pages.get(currentPageIndex).id,
                                            currentNotebook.pages.get(targetIndex).id,
                                            label.getText().toString()
                                    );
                                    toast("Concept thread created");
                                } catch (Exception e) {
                                    toast("Could not create thread");
                                }
                            }).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareNotebookBackup() {
        if (currentNotebook == null) return;
        saveCurrentPageNow();
        if (saveLabel != null) saveLabel.setText("Preparing share…");
        exportExecutor.submit(() -> {
            java.io.File file = new java.io.File(getCacheDir(),
                    safeFileName(currentNotebook.title) + "-" + System.currentTimeMillis() + ".papernote");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                String backup = store.exportBackup(currentNotebook,
                        PaperCanvasView.PAGE_WIDTH, PaperCanvasView.PAGE_HEIGHT);
                out.write(backup.getBytes(StandardCharsets.UTF_8));
                runOnUiThread(() -> {
                    android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                            EditorActivity.this, getPackageName() + ".fileprovider", file);
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("application/octet-stream");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Send PaperNote notebook"));
                    if (saveLabel != null) saveLabel.setText("Saved");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (saveLabel != null) saveLabel.setText("Saved");
                    toast("Could not prepare notebook share");
                });
            }
        });
    }

    private void flushPendingFeatureStroke() {
        if (currentNotebook == null || currentNotebook.pages.isEmpty() || pendingFeatureStrokes == 0) return;
        String pageId = currentNotebook.pages.get(currentPageIndex).id;
        int count = pendingFeatureStrokes;
        int avgX = pendingFeatureHeatSamples == 0 ? 0 : pendingFeatureHeatX / pendingFeatureHeatSamples;
        int avgY = pendingFeatureHeatSamples == 0 ? 0 : pendingFeatureHeatY / pendingFeatureHeatSamples;
        for (int i = 0; i < count; i++) {
            store.recordStroke(pageId, avgX, avgY);
        }
        pendingFeatureStrokes = 0;
        pendingFeatureHeatX = pendingFeatureHeatY = pendingFeatureHeatSamples = 0;
    }

    private static final class HeatmapView extends View {
        private final int[] values;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        HeatmapView(Activity context, int[] values) {
            super(context);
            this.values = values.clone();
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            int max = 1;
            for (int value : values) max = Math.max(max, value);
            float cellW = getWidth() / 6f;
            float cellH = getHeight() / 8f;
            for (int i = 0; i < 48; i++) {
                float intensity = values[i] / (float) max;
                int alpha = 18 + Math.round(150f * intensity);
                paint.setColor(Color.argb(alpha, 61, 111, 232));
                float left = i % 6 * cellW + 2;
                float top = i / 6 * cellH + 2;
                canvas.drawRoundRect(left, top, left + cellW - 4, top + cellH - 4, 7, 7, paint);
            }
        }
    }

    private void showCalculator() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        EditText expression = new EditText(this);
        expression.setHint("Example: (12+8)*3/4");
        expression.setSingleLine(true);
        expression.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_CLASS_PHONE
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(expression);

        TextView result = text("Result: ", 18, Color.rgb(23, 32, 51), true);
        result.setPadding(0, dp(14), 0, dp(8));
        box.addView(result);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Scientific calculator")
                .setView(box)
                .setNeutralButton("Clear", null)
                .setNegativeButton("Close", null)
                .setPositiveButton("Calculate", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    double value = ExpressionParser.evaluate(expression.getText().toString());
                    result.setText("Result: " + formatNumber(value));
                } catch (Exception e) {
                    result.setText("Result: Invalid expression");
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                expression.setText("");
                result.setText("Result: ");
            });
        });
        dialog.show();
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-10) {
            return Long.toString(Math.round(value));
        }
        return String.format(java.util.Locale.US, "%.10f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\\\.$", "");
    }

    private void showFocusTimer() {
        final Handler timerHandler = new Handler(Looper.getMainLooper());
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        TextView time = text("25:00", 42, Color.rgb(23, 32, 51), true);
        time.setGravity(Gravity.CENTER);
        box.addView(time);

        TextView hint = text("Focus on one chapter or problem set. You can stop at any time.", 13, 0xFF667085, false);
        hint.setPadding(0, dp(8), 0, 0);
        box.addView(hint);

        final long[] endAt = {0L};
        final boolean[] running = {false};
        final Runnable[] tick = new Runnable[1];

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Focus timer")
                .setView(box)
                .setNegativeButton("Close", null)
                .setPositiveButton("Start", null)
                .create();

        dialog.setOnDismissListener(d -> timerHandler.removeCallbacks(tick[0]));

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!running[0]) {
                    endAt[0] = System.currentTimeMillis() + 25L * 60L * 1000L;
                    running[0] = true;
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Pause");
                    tick[0] = new Runnable() {
                        @Override
                        public void run() {
                            long remaining = Math.max(0L, endAt[0] - System.currentTimeMillis());
                            long minutes = remaining / 60000L;
                            long seconds = (remaining / 1000L) % 60L;
                            time.setText(String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds));
                            if (remaining <= 0L) {
                                running[0] = false;
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Start");
                                android.media.ToneGenerator tone =
                                        new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 90);
                                tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 700);
                                tone.release();
                                return;
                            }
                            timerHandler.postDelayed(this, 250L);
                        }
                    };
                    timerHandler.post(tick[0]);
                } else {
                    long remaining = Math.max(0L, endAt[0] - System.currentTimeMillis());
                    endAt[0] = System.currentTimeMillis() + remaining;
                    running[0] = false;
                    timerHandler.removeCallbacks(tick[0]);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Start");
                }
            });
        });

        dialog.show();
    }

    private void showStudyChecklist() {
        final String[] tasks = {
                "Review formulas",
                "Solve 10 practice questions",
                "Solve 10 MCQs",
                "Mark difficult questions",
                "Revise mistakes",
                "Write a short summary"
        };
        boolean[] checked = new boolean[tasks.length];

        new AlertDialog.Builder(this)
                .setTitle("Study checklist")
                .setMultiChoiceItems(tasks, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("Close", null)
                .setPositiveButton("Save as page note", (dialog, which) -> {
                    StringBuilder note = new StringBuilder("Study Checklist\\n");
                    for (int i = 0; i < tasks.length; i++) {
                        note.append(checked[i] ? "☑ " : "☐ ").append(tasks[i]).append("\\n");
                    }
                    canvasView.addText(
                            note.toString().trim(),
                            150f,
                            180f
                    );
                })
                .show();
    }

    private static final class ExpressionParser {
        private final String source;
        private int index;

        private ExpressionParser(String source) {
            this.source = source.replace("×", "*")
                    .replace("÷", "/")
                    .replace("π", String.valueOf(Math.PI))
                    .replaceAll("\\\\s+", "");
        }

        static double evaluate(String source) {
            if (source == null || source.trim().isEmpty()) throw new IllegalArgumentException();
            ExpressionParser parser = new ExpressionParser(source);
            double value = parser.parseExpression();
            if (parser.index != parser.source.length()) throw new IllegalArgumentException();
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (index < source.length()) {
                char op = source.charAt(index);
                if (op != '+' && op != '-') break;
                index++;
                double rhs = parseTerm();
                value = op == '+' ? value + rhs : value - rhs;
            }
            return value;
        }

        private double parseTerm() {
            double value = parsePower();
            while (index < source.length()) {
                char op = source.charAt(index);
                if (op != '*' && op != '/') break;
                index++;
                double rhs = parsePower();
                if (op == '/' && Math.abs(rhs) < 1e-15) throw new ArithmeticException();
                value = op == '*' ? value * rhs : value / rhs;
            }
            return value;
        }

        private double parsePower() {
            double base = parseUnary();
            if (index < source.length() && source.charAt(index) == '^') {
                index++;
                base = Math.pow(base, parsePower());
            }
            return base;
        }

        private double parseUnary() {
            if (index < source.length() && source.charAt(index) == '+') {
                index++;
                return parseUnary();
            }
            if (index < source.length() && source.charAt(index) == '-') {
                index++;
                return -parseUnary();
            }
            return parsePrimary();
        }

        private double parsePrimary() {
            if (index >= source.length()) throw new IllegalArgumentException();

            if (source.charAt(index) == '(') {
                index++;
                double value = parseExpression();
                if (index >= source.length() || source.charAt(index) != ')') throw new IllegalArgumentException();
                index++;
                return value;
            }

            if (source.startsWith("sqrt(", index)) {
                index += 5;
                double value = parseExpression();
                if (index >= source.length() || source.charAt(index) != ')') throw new IllegalArgumentException();
                index++;
                if (value < 0) throw new ArithmeticException();
                return Math.sqrt(value);
            }

            int start = index;
            boolean dot = false;
            while (index < source.length()) {
                char ch = source.charAt(index);
                if (Character.isDigit(ch)) {
                    index++;
                } else if (ch == '.' && !dot) {
                    dot = true;
                    index++;
                } else {
                    break;
                }
            }
            if (start == index) throw new IllegalArgumentException();
            return Double.parseDouble(source.substring(start, index));
        }
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Rename notebook");
        menu.getMenu().add("Rename page");
        menu.getMenu().add("Duplicate page");
        menu.getMenu().add("Delete page");
        menu.getMenu().add("Clear current page");
        menu.getMenu().add("Backup notebook");
        menu.getMenu().add("Restore backup");
        menu.getMenu().add("About passive stylus");
        menu.getMenu().add("Delete notebook");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            switch (title) {
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
                    confirmDeleteCurrentPage();
                    return true;
                case "Clear current page":
                    new AlertDialog.Builder(this)
                            .setTitle("Clear this page?")
                            .setMessage("All handwriting and inserted content on this page will be removed.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Clear", (d, w) -> canvasView.clearPage())
                            .show();
                    return true;
                case "Backup notebook":
                    chooseBackupDestination();
                    return true;
                case "Restore backup":
                    chooseRestoreFile();
                    return true;
                case "About passive stylus":
                    new AlertDialog.Builder(this)
                            .setTitle("Passive stylus mode")
                            .setMessage(
                                    "Your non-electric capacitive stylus does not send a separate pen identity to Android. " +
                                    "PaperNote therefore accepts normal touch input for writing, locks the first contact as the active stroke, " +
                                    "and can ignore unusually large contact areas as a palm shield. True electronic palm rejection requires an active pen digitizer."
                            )
                            .setPositiveButton("OK", null)
                            .show();
                    return true;
                case "Delete notebook":
                    confirmDeleteNotebook();
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
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        store.renameNotebook(currentNotebook, title.getText().toString(), subject.getText().toString());
                        titleLabel.setText(currentNotebook.title);
                    } catch (Exception e) {
                        toast("Rename failed");
                    }
                }).show();
    }


    private void duplicateCurrentPage() {
        saveCurrentPageNow();
        NotebookStore.PageMeta original = currentNotebook.pages.get(currentPageIndex);
        NotebookStore.PageMeta copy = store.addPage(
                currentNotebook,
                original.title + " Copy",
                original.paperType
        );

        Bitmap image = canvasView.getInkBitmap();
        if (image != null) {
            Bitmap bitmapCopy = image.copy(Bitmap.Config.ARGB_8888, false);
            try {
                store.savePageBitmap(copy.id, bitmapCopy);
                bitmapCopy.recycle();
            } catch (Exception e) {
                bitmapCopy.recycle();
                toast("Could not duplicate page");
                return;
            }
        }

        currentPageIndex = currentNotebook.pages.size() - 1;
        try { store.save(currentNotebook); } catch (Exception ignored) {}
        loadCurrentPage();
    }

    private void confirmDeleteCurrentPage() {
        if (currentNotebook.pages.size() <= 1) {
            toast("A notebook must keep at least one page");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete current page?")
                .setMessage("This permanently removes the current page from this notebook.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    saveCurrentPageNow();
                    store.removePage(currentNotebook, currentPageIndex);
                    currentPageIndex = Math.min(currentPageIndex, currentNotebook.pages.size() - 1);
                    try { store.save(currentNotebook); } catch (Exception ignored) {}
                    loadCurrentPage();
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
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        store.renamePage(currentNotebook, currentPageIndex, input.getText().toString());
                        pageLabel.setText("Page " + (currentPageIndex + 1) + " / " + currentNotebook.pages.size());
                    } catch (Exception e) {
                        toast("Rename failed");
                    }
                }).show();
    }

    private void confirmDeleteNotebook() {
        new AlertDialog.Builder(this)
                .setTitle("Delete notebook?")
                .setMessage("This deletes the notebook and its page images from PaperNote.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    store.deleteNotebook(currentNotebook);
                    currentNotebook = null;
                    showHome();
                }).show();
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    private void chooseRestoreFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_RESTORE);
    }

    private void chooseBackupDestination() {
        saveCurrentPageNow();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, safeFileName(currentNotebook.title) + ".papernote");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_BACKUP);
    }

    private void showExportDialog() {
        saveCurrentPageNow();

        String[] labels = {
                "PDF  •  one document with all pages",
                "PNG  •  separate image for every page",
                "JPG  •  separate image for every page",
                "WebP  •  separate image for every page",
                "ZIP  •  all pages as PNG images"
        };

        ExportManager.Format[] formats = {
                ExportManager.Format.PDF,
                ExportManager.Format.PNG,
                ExportManager.Format.JPG,
                ExportManager.Format.WEBP,
                ExportManager.Format.ZIP
        };

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Export notebook")
                .setItems(labels, null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            beginExport(formats[position]);
        }));

        dialog.show();
    }

    private void beginExport(ExportManager.Format format) {
        if (currentNotebook == null) return;

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingExportFormat = format;
            requestPermissions(
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION
            );
            return;
        }

        saveCurrentPageNow();
        if (saveLabel != null) saveLabel.setText("Exporting…");

        exportExecutor.submit(() -> ExportManager.export(
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
                            if (saveLabel != null) saveLabel.setText("Export error");
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_STORAGE_PERMISSION) return;

        if (grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
                && pendingExportFormat != null) {
            ExportManager.Format format = pendingExportFormat;
            pendingExportFormat = null;
            beginExport(format);
        } else {
            pendingExportFormat = null;
            toast("Storage permission is required to save exports on this Android version.");
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
                    if (decoded == null) throw new Exception("Could not decode the image.");

                    int max = 1800;
                    float scale = Math.min(1f, max / (float) Math.max(decoded.getWidth(), decoded.getHeight()));
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

                    canvasView.addImage(decoded);
                    decoded.recycle();
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
                    if (out == null) throw new Exception("Could not open backup file.");
                    out.write(backup.getBytes(StandardCharsets.UTF_8));
                }
                toast("Backup exported");
                return;
            }

            if (requestCode == REQUEST_RESTORE) {
                StringBuilder builder = new StringBuilder();
                try (InputStream in = getContentResolver().openInputStream(uri);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    if (in == null) throw new Exception("Could not open restore file.");
                    String line;
                    while ((line = reader.readLine()) != null) builder.append(line);
                }

                NotebookStore.NotebookMeta restored = store.importBackup(builder.toString());
                toast("Backup restored");
                currentNotebook = restored;
                currentPageIndex = 0;
                buildEditor();
            }
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("Operation failed")
                    .setMessage(e.getMessage() == null ? "Please try again." : e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void updateWriteModeButton() {
        if (writeModeButton == null || canvasView == null) return;
        writeModeButton.setText(canvasView.isWriteMode() ? "WRITE" : "PAN");
    }

    private void updatePalmButton() {
        if (palmButton != null && canvasView != null) {
            palmButton.setText(canvasView.isPalmShield() ? "PALM✓" : "PALM");
        }
    }

    private void updateSoundButton() {
        if (soundButton != null) {
            soundButton.setText(soundEngine.isEnabled() ? "SOUND✓" : "SOUND");
        }
    }

    private Button styledButton(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : Color.rgb(23, 32, 51));
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackground(rounded(primary ? Color.rgb(64, 93, 230) : Color.WHITE, 14));
        return b;
    }

    private Button toolbarButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setTextColor(Color.rgb(23, 32, 51));
        b.setBackground(rounded(Color.WHITE, 12));
        b.setElevation(dp(1.5f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void addChip(LinearLayout parent, String label, Runnable action) {
        Button chip = styledButton(label, false);
        chip.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
        lp.setMargins(0, 0, dp(8), 0);
        parent.addView(chip, lp);
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

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safeFileName(String value) {
        String cleaned = value == null ? "PaperNote" : value.replaceAll("[^a-zA-Z0-9._ -]", "_").trim();
        return cleaned.isEmpty() ? "PaperNote" : cleaned;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Small adapter that lets the dialog read the selected radio item without exposing
    // Android's internal AlertController implementation details.
    private static final class ListViewCompat {
        private final AlertDialog dialog;
        ListViewCompat(AlertDialog dialog) { this.dialog = dialog; }
        int getCheckedItemPosition() {
            android.widget.ListView list = dialog.getListView();
            return list == null ? -1 : list.getCheckedItemPosition();
        }
    }
}
