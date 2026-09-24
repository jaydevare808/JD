package com.jd.papernote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Deque;

public final class PaperCanvasView extends View {
    public static final int PAGE_WIDTH = 1536;
    public static final int PAGE_HEIGHT = 2168;

    public static final String PAPER_BLANK = "blank";
    public static final String PAPER_RULED = "ruled";
    public static final String PAPER_GRAPH = "graph";
    public static final String PAPER_DOT = "dot";
    public static final String PAPER_MATH = "math";
    public static final String PAPER_EXAM_2 = "exam_2";
    public static final String PAPER_EXAM_3 = "exam_3";
    public static final String PAPER_EXAM_4 = "exam_4";
    public static final String PAPER_EXPERIMENT = "experiment";

    public static final int TOOL_PEN = 0;
    public static final int TOOL_HIGHLIGHTER = 1;
    public static final int TOOL_ERASER = 2;
    public static final int TOOL_LINE = 3;
    public static final int TOOL_RECT = 4;
    public static final int TOOL_OVAL = 5;
    public static final int TOOL_TEXT = 6;

    public static final int STUDY_VIEW_NORMAL = 0;
    public static final int STUDY_VIEW_MARKS = 1;
    public static final int STUDY_VIEW_FRICTION = 2;

    public interface Listener {
        void onCanvasDirty();
        void onRequestText(float pageX, float pageY);
    }

    public interface InteractionListener {
        void onStrokeStarted(float pageX, float pageY, int tool);
        void onPinPlaced(float pageX, float pageY);
        void onRecallRegionPlaced(float left, float top, float right, float bottom);
    }

    public static final class StudyPin {
        public final float x;
        public final float y;
        public final String type;
        public final String label;
        public final boolean resolved;

        public StudyPin(float x, float y, String type, String label, boolean resolved) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.label = label;
            this.resolved = resolved;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF pageRect = new RectF();
    private final Deque<EditCommand> undo = new ArrayDeque<>();
    private final Deque<EditCommand> redo = new ArrayDeque<>();

    private Bitmap inkBitmap;
    private Bitmap baseBitmap;
    private Listener listener;
    private InteractionListener interactionListener;
    private SoundEngine soundEngine;
    private List<StudyPin> studyPins = new ArrayList<>();
    private List<NotebookStore.RecallRegion> recallRegions = new ArrayList<>();
    private final int[] heatmap = new int[48];
    private int studyViewMode = STUDY_VIEW_NORMAL;
    private Bitmap ghostBitmap;
    private float ghostAlpha = 0f;
    private boolean recallMode = false;
    private boolean placingPin = false;
    private boolean placingRecallRegion = false;
    private boolean recallRegionDrawing = false;
    private float recallStartX;
    private float recallStartY;
    private float recallEndX;
    private float recallEndY;
    private boolean replaying = false;
    private Bitmap replayFinalBitmap;
    private int replayIndex = 0;
    private Runnable replayRunnable;

    private int tool = TOOL_PEN;
    private int inkColor = Color.rgb(24, 35, 51);
    private float penSize = 4.6f;
    private float stabilizer = 0.0f;
    private boolean writeMode = true;
    private boolean palmShield = true;
    private boolean marginEnabled = true;
    private String paperType = PAPER_RULED;

    private int activePointerId = -1;
    private boolean drawing;
    private boolean ignoredDown;

    private float shapeStartX;
    private float shapeStartY;
    private float shapeEndX;
    private float shapeEndY;

    private final Path liveStroke = new Path();
    private final ArrayList<PointF> livePoints = new ArrayList<>(256);
    private float liveLastX;
    private float liveLastY;
    private float liveCurveEndX;
    private float liveCurveEndY;
    private boolean liveStrokeActive;
    private boolean liveStrokeMoved;

    private final float[] pagePoint = new float[2];

    private float baseScale = 1f;
    private float zoom = 1f;
    private float panX;
    private float panY;
    private float panLastX;
    private float panLastY;
    private boolean panning;

    private final ScaleGestureDetector scaleDetector;

    public PaperCanvasView(Context context) {
        super(context);

        // Keep the View hardware accelerated. The old implementation forced a software
        // layer, which made live handwriting feel slow on tablets. Commands are rasterized
        // to the backing Bitmap only when a stroke is committed.
        setLayerType(View.LAYER_TYPE_NONE, null);
        setBackgroundColor(Color.rgb(223, 226, 231));
        setFocusable(true);
        setClickable(true);

        paint.setAntiAlias(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        scaleDetector = new ScaleGestureDetector(
                context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        if (!writeMode) {
                            zoom = clamp(zoom * detector.getScaleFactor(), 1f, 3f);
                            invalidate();
                        }
                        return true;
                    }
                }
        );
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setInteractionListener(InteractionListener listener) {
        this.interactionListener = listener;
    }

    public void setStudyPins(List<StudyPin> pins) {
        this.studyPins = pins == null ? new ArrayList<>() : new ArrayList<>(pins);
        invalidate();
    }

    public void setRecallRegions(List<NotebookStore.RecallRegion> regions) {
        this.recallRegions = regions == null ? new ArrayList<>() : new ArrayList<>(regions);
        invalidate();
    }

    public int getRecallRegionCount() {
        return recallRegions.size();
    }

    public void setHeatmap(int[] values) {
        java.util.Arrays.fill(heatmap, 0);
        if (values != null) {
            System.arraycopy(values, 0, heatmap, 0, Math.min(values.length, heatmap.length));
        }
        invalidate();
    }

    public void setStudyViewMode(int mode) {
        studyViewMode = Math.max(STUDY_VIEW_NORMAL, Math.min(STUDY_VIEW_FRICTION, mode));
        invalidate();
    }

    public int getStudyViewMode() {
        return studyViewMode;
    }

    public void beginRecallRegionPlacement() {
        placingRecallRegion = true;
        recallRegionDrawing = false;
        setWriteMode(true);
        cancelLiveStroke();
        invalidate();
    }

    public void setGhostBitmap(Bitmap bitmap, float alpha) {
        if (ghostBitmap != null && ghostBitmap != bitmap && !ghostBitmap.isRecycled()) {
            ghostBitmap.recycle();
        }
        ghostBitmap = bitmap;
        ghostAlpha = Math.max(0f, Math.min(1f, alpha));
        invalidate();
    }

    public void clearGhostBitmap() {
        if (ghostBitmap != null && !ghostBitmap.isRecycled()) ghostBitmap.recycle();
        ghostBitmap = null;
        ghostAlpha = 0f;
        invalidate();
    }

    public boolean hasGhostBitmap() {
        return ghostBitmap != null && !ghostBitmap.isRecycled();
    }

    public void setRecallMode(boolean enabled) {
        recallMode = enabled;
        cancelLiveStroke();
        invalidate();
    }

    public boolean isRecallMode() {
        return recallMode;
    }

    public void centerOnPagePoint(float pageX, float pageY) {
        writeMode = false;
        zoom = 1.65f;
        float scale = currentScale();
        float pageW = PAGE_WIDTH * scale;
        float pageH = PAGE_HEIGHT * scale;
        panX = pageW * 0.5f - pageX * scale;
        panY = pageH * 0.5f - pageY * scale;
        invalidate();
    }

    public boolean isReplaying() {
        return replaying;
    }

    public boolean replaySession() {
        if (replaying || undo.isEmpty() || inkBitmap == null || baseBitmap == null) return false;

        stopReplaySession();
        replayFinalBitmap = inkBitmap.copy(Bitmap.Config.ARGB_8888, false);
        Bitmap firstFrame = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Bitmap old = inkBitmap;
        inkBitmap = firstFrame;
        if (old != null && !old.isRecycled() && old != baseBitmap) old.recycle();

        replayIndex = 0;
        replaying = true;
        replayRunnable = new Runnable() {
            @Override public void run() {
                if (!replaying) return;
                if (replayIndex >= undo.size()) {
                    replaying = false;
                    if (replayFinalBitmap != null && !replayFinalBitmap.isRecycled()) replayFinalBitmap.recycle();
                    replayFinalBitmap = null;
                    replayRunnable = null;
                    invalidate();
                    return;
                }
                EditCommand[] commands = undo.toArray(new EditCommand[0]);
                if (replayIndex < commands.length) {
                    commands[replayIndex].apply(new Canvas(inkBitmap));
                }
                replayIndex++;
                invalidate();
                postDelayed(this, 55L);
            }
        };
        post(replayRunnable);
        return true;
    }

    public void stopReplaySession() {
        if (replayRunnable != null) removeCallbacks(replayRunnable);
        replayRunnable = null;
        replaying = false;
        if (replayFinalBitmap != null && !replayFinalBitmap.isRecycled()) {
            Bitmap old = inkBitmap;
            inkBitmap = replayFinalBitmap;
            replayFinalBitmap = null;
            if (old != null && old != baseBitmap && !old.isRecycled()) old.recycle();
        }
        replayIndex = 0;
        invalidate();
    }

    public void beginPinPlacement() {
        placingPin = true;
        setWriteMode(true);
        cancelLiveStroke();
        invalidate();
    }

    public void setSoundEngine(SoundEngine soundEngine) {
        this.soundEngine = soundEngine;
    }

    public void setTool(int tool) {
        this.tool = tool;
        cancelLiveStroke();
        invalidate();
    }

    public int getTool() {
        return tool;
    }

    public void setInkColor(int color) {
        inkColor = color;
        invalidate();
    }

    public void setPenSize(float size) {
        penSize = clamp(size, 1.5f, 22f);
        invalidate();
    }

    public float getPenSize() {
        return penSize;
    }

    public void setStabilizer(float value) {
        stabilizer = clamp(value, 0f, 0.25f);
    }

    public float getStabilizer() {
        return stabilizer;
    }

    public void setWriteMode(boolean enabled) {
        writeMode = enabled;
        resetViewport();
        cancelLiveStroke();
        invalidate();
    }

    public boolean isWriteMode() {
        return writeMode;
    }

    public void setPalmShield(boolean enabled) {
        palmShield = enabled;
    }

    public boolean isPalmShield() {
        return palmShield;
    }

    public void setMarginEnabled(boolean enabled) {
        marginEnabled = enabled;
        invalidate();
    }

    public void setPaperType(String type) {
        paperType = type == null ? PAPER_RULED : type;
        invalidate();
    }

    public String getPaperType() {
        return paperType;
    }

    public void loadBitmap(Bitmap bitmap) {
        cancelLiveStroke();

        if (inkBitmap != null && inkBitmap != bitmap && !inkBitmap.isRecycled()) {
            inkBitmap.recycle();
        }
        if (baseBitmap != null && !baseBitmap.isRecycled()) {
            baseBitmap.recycle();
        }

        if (bitmap == null) {
            inkBitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888);
        } else {
            inkBitmap = bitmap;
        }

        // One stable base copy for session-local vector undo. We never make a full-page
        // PNG snapshot for every pen/eraser action.
        baseBitmap = inkBitmap.copy(Bitmap.Config.ARGB_8888, true);
        undo.clear();
        redo.clear();
        clearGhostBitmap();
        stopReplaySession();
        resetViewport();
        invalidate();
    }

    public Bitmap getInkBitmap() {
        return inkBitmap;
    }

    public Bitmap renderPageBitmap() {
        return renderPage(inkBitmap, paperType, marginEnabled);
    }

    public void addText(String text, float pageX, float pageY) {
        if (inkBitmap == null || text == null || text.trim().isEmpty()) return;
        execute(new TextCommand(text.trim(), clamp(pageX, 12f, PAGE_WIDTH - 24f),
                clamp(pageY, 24f, PAGE_HEIGHT - 24f), inkColor, Math.max(28f, penSize * 6f)));
    }

    public void addImage(Bitmap image) {
        if (inkBitmap == null || image == null || image.isRecycled()) return;

        int maxWidth = (int) (PAGE_WIDTH * 0.72f);
        int maxHeight = (int) (PAGE_HEIGHT * 0.58f);
        float scale = Math.min(
                1f,
                Math.min(maxWidth / (float) image.getWidth(), maxHeight / (float) image.getHeight())
        );
        int w = Math.max(1, Math.round(image.getWidth() * scale));
        int h = Math.max(1, Math.round(image.getHeight() * scale));

        Bitmap copy = image.copy(Bitmap.Config.ARGB_8888, true);
        RectF dst = new RectF(
                (PAGE_WIDTH - w) * 0.5f,
                (PAGE_HEIGHT - h) * 0.25f,
                (PAGE_WIDTH - w) * 0.5f + w,
                (PAGE_HEIGHT - h) * 0.25f + h
        );

        if (copy.getWidth() != w || copy.getHeight() != h) {
            Bitmap scaled = Bitmap.createScaledBitmap(copy, w, h, true);
            copy.recycle();
            copy = scaled;
        }

        execute(new ImageCommand(copy, dst));
    }

    public void clearPage() {
        if (inkBitmap == null) return;
        execute(new ClearCommand());
    }

    public void undo() {
        if (undo.isEmpty() || inkBitmap == null) return;
        EditCommand command = undo.removeLast();
        redo.addLast(command);
        rebuildFromBase();
        invalidate();
        notifyDirty();
    }

    public void redo() {
        if (redo.isEmpty() || inkBitmap == null) return;
        EditCommand command = redo.removeLast();
        undo.addLast(command);
        applyCommand(command);
        invalidate();
        notifyDirty();
    }

    private void execute(EditCommand command) {
        if (command == null || inkBitmap == null) return;
        undo.addLast(command);
        while (undo.size() > 80) {
            EditCommand old = undo.removeFirst();
            old.release();
        }
        releaseRedo();
        applyCommand(command);
        notifyDirty();
    }

    private void applyCommand(EditCommand command) {
        command.apply(new Canvas(inkBitmap));
    }

    private void rebuildFromBase() {
        if (baseBitmap == null) return;
        Bitmap rebuilt = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Bitmap old = inkBitmap;
        inkBitmap = rebuilt;

        Canvas canvas = new Canvas(inkBitmap);
        for (EditCommand command : undo) {
            command.apply(canvas);
        }

        if (old != null && old != baseBitmap && !old.isRecycled()) {
            old.recycle();
        }
    }

    private void releaseRedo() {
        while (!redo.isEmpty()) {
            redo.removeFirst().release();
        }
    }

    private void notifyDirty() {
        if (listener != null) listener.onCanvasDirty();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        calculateBaseScale(width, height);
    }

    private void calculateBaseScale(int width, int height) {
        float horizontalPadding = dp(20);
        float verticalPadding = dp(12);
        float sx = Math.max(1f, width - horizontalPadding * 2f) / PAGE_WIDTH;
        float sy = Math.max(1f, height - verticalPadding * 2f) / PAGE_HEIGHT;
        baseScale = Math.min(sx, sy);
        resetViewport();
    }

    private void resetViewport() {
        zoom = 1f;
        panX = 0f;
        panY = 0f;
    }

    private float currentScale() {
        return baseScale * (writeMode ? 1f : zoom);
    }

    private void updatePageRect() {
        float scale = currentScale();
        float w = PAGE_WIDTH * scale;
        float h = PAGE_HEIGHT * scale;
        float left = (getWidth() - w) * 0.5f + panX;
        float top = (getHeight() - h) * 0.5f + panY;
        pageRect.set(left, top, left + w, top + h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updatePageRect();
        canvas.drawColor(Color.rgb(223, 226, 231));

        canvas.save();
        canvas.clipRect(pageRect);

        float scale = currentScale();
        canvas.translate(pageRect.left, pageRect.top);
        canvas.scale(scale, scale);

        drawPaperBackground(canvas, paperType, marginEnabled);

        if (studyViewMode != STUDY_VIEW_MARKS && ghostBitmap != null && !ghostBitmap.isRecycled() && ghostAlpha > 0f) {
            Paint ghostPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            ghostPaint.setAlpha(Math.round(255f * ghostAlpha));
            canvas.drawBitmap(ghostBitmap, 0f, 0f, ghostPaint);
        }

        if (inkBitmap != null) {
            Paint contentPaint = bitmapPaint;
            int oldAlpha = contentPaint.getAlpha();
            contentPaint.setAlpha(studyViewMode == STUDY_VIEW_MARKS ? 65 : 255);
            canvas.drawBitmap(inkBitmap, 0f, 0f, contentPaint);
            contentPaint.setAlpha(oldAlpha);
        }

        if (studyPins != null) {
            Paint pinFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint pinRing = new Paint(Paint.ANTI_ALIAS_FLAG);
            pinRing.setStyle(Paint.Style.STROKE);
            pinRing.setStrokeWidth(5f);
            pinRing.setColor(Color.WHITE);
            float pinRadius = 18f;
            for (StudyPin pin : studyPins) {
                if (pin.resolved) {
                    pinFill.setColor(0xFF7F8794);
                    pinFill.setAlpha(150);
                } else if (NotebookStore.StudyMark.DOUBT.equals(pin.type)) {
                    pinFill.setColor(0xFFE58A23);
                    pinFill.setAlpha(255);
                } else if (NotebookStore.StudyMark.MISTAKE.equals(pin.type)) {
                    pinFill.setColor(0xFFD64545);
                    pinFill.setAlpha(255);
                } else if (NotebookStore.StudyMark.IMPORTANT.equals(pin.type)) {
                    pinFill.setColor(0xFFE1B21D);
                    pinFill.setAlpha(255);
                } else {
                    pinFill.setColor(0xFF3D6FE8);
                    pinFill.setAlpha(255);
                }
                canvas.drawCircle(pin.x, pin.y, pinRadius, pinFill);
                canvas.drawCircle(pin.x, pin.y, pinRadius + 3f, pinRing);
                if (pin.label != null && !pin.label.isEmpty()) {
                    Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    labelPaint.setColor(Color.WHITE);
                    labelPaint.setTextSize(18f);
                    labelPaint.setTypeface(Typeface.DEFAULT_BOLD);
                    labelPaint.setTextAlign(Paint.Align.CENTER);
                    String shortLabel = pin.label.length() > 2 ? pin.label.substring(0, 2) : pin.label;
                    canvas.drawText(shortLabel, pin.x, pin.y + 6f, labelPaint);
                }
            }
        }

        if (!recallMode && studyViewMode != STUDY_VIEW_MARKS && liveStrokeActive && tool != TOOL_ERASER
                && (tool == TOOL_PEN || tool == TOOL_HIGHLIGHTER)) {
            Paint livePaint = configurePaint(tool == TOOL_HIGHLIGHTER);
            canvas.drawPath(liveStroke, livePaint);

            if (liveStrokeMoved) {
                canvas.drawLine(
                        liveCurveEndX,
                        liveCurveEndY,
                        liveLastX,
                        liveLastY,
                        livePaint
                );
            } else {
                canvas.drawPoint(liveLastX, liveLastY, livePaint);
            }
        }

        if (!recallMode && studyViewMode != STUDY_VIEW_MARKS && liveStrokeActive && tool == TOOL_ERASER) {
            Paint eraserPreview = new Paint(Paint.ANTI_ALIAS_FLAG);
            eraserPreview.setStyle(Paint.Style.STROKE);
            eraserPreview.setStrokeWidth(Math.max(1f, penSize * 0.45f));
            eraserPreview.setColor(0x88606A78);
            canvas.drawCircle(liveLastX, liveLastY, eraserRadius(), eraserPreview);
        }

        if (!recallMode && studyViewMode != STUDY_VIEW_MARKS && drawing && isShapeTool(tool)) {
            Paint preview = configurePaint(false);
            preview.setStyle(Paint.Style.STROKE);
            preview.setColor(inkColor);
            preview.setAlpha(210);
            preview.setStrokeWidth(Math.max(2f, penSize));
            RectF rect = new RectF(
                    Math.min(shapeStartX, shapeEndX),
                    Math.min(shapeStartY, shapeEndY),
                    Math.max(shapeStartX, shapeEndX),
                    Math.max(shapeStartY, shapeEndY)
            );

            if (tool == TOOL_LINE) {
                canvas.drawLine(shapeStartX, shapeStartY, shapeEndX, shapeEndY, preview);
            } else if (tool == TOOL_RECT) {
                canvas.drawRect(rect, preview);
            } else if (tool == TOOL_OVAL) {
                canvas.drawOval(rect, preview);
            }
        }

        if (studyViewMode == STUDY_VIEW_FRICTION && !recallMode) {
            drawHeatmap(canvas);
        }

        if (recallMode) {
            drawRecallCover(canvas);
        }

        if (placingRecallRegion && recallRegionDrawing) {
            Paint select = new Paint(Paint.ANTI_ALIAS_FLAG);
            select.setStyle(Paint.Style.STROKE);
            select.setStrokeWidth(4f);
            select.setColor(0xFF3D6FE8);
            select.setPathEffect(new android.graphics.DashPathEffect(new float[]{14f, 9f}, 0f));
            RectF selected = new RectF(
                    Math.min(recallStartX, recallEndX),
                    Math.min(recallStartY, recallEndY),
                    Math.max(recallStartX, recallEndX),
                    Math.max(recallStartY, recallEndY)
            );
            canvas.drawRect(selected, select);
        }

        canvas.restore();

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(1));
        border.setColor(0x20000000);
        canvas.drawRect(pageRect, border);
    }

    private void drawHeatmap(Canvas canvas) {
        int max = 1;
        for (int value : heatmap) max = Math.max(max, value);
        Paint heatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cellW = PAGE_WIDTH / 6f;
        float cellH = PAGE_HEIGHT / 8f;
        for (int i = 0; i < heatmap.length; i++) {
            int alpha = 20 + Math.round(145f * heatmap[i] / (float) max);
            heatPaint.setColor(Color.argb(Math.min(180, alpha), 58, 110, 230));
            float left = (i % 6) * cellW;
            float top = (i / 6) * cellH;
            canvas.drawRect(left, top, left + cellW, top + cellH, heatPaint);
        }
    }

    private void drawRecallCover(Canvas canvas) {
        Paint cover = new Paint(Paint.ANTI_ALIAS_FLAG);
        cover.setColor(0xFFF3F5F8);
        if (recallRegions == null || recallRegions.isEmpty()) {
            canvas.drawRect(0f, 0f, PAGE_WIDTH, PAGE_HEIGHT, cover);
            Paint message = new Paint(Paint.ANTI_ALIAS_FLAG);
            message.setColor(0xFF7B8493);
            message.setTextSize(28f);
            message.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("RECALL MODE", 120f, 120f, message);
            canvas.drawText("Add hidden areas to practice selected concepts.", 120f, 160f, message);
            return;
        }
        for (NotebookStore.RecallRegion region : recallRegions) {
            RectF rect = new RectF(region.left, region.top, region.right, region.bottom);
            canvas.drawRoundRect(rect, 18f, 18f, cover);
            Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
            label.setColor(0xFF7B8493);
            label.setTextSize(22f);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            String title = region.label == null || region.label.isEmpty() ? "Recall" : region.label;
            canvas.drawText(title, rect.left + 18f, rect.top + 34f, label);
        }
    }

    private boolean isShapeTool(int value) {
        return value == TOOL_LINE || value == TOOL_RECT || value == TOOL_OVAL;
    }

    private Paint configurePaint(boolean highlighter) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        paint.setXfermode(null);

        paint.setColor(inkColor);
        paint.setAlpha(highlighter ? 90 : 255);
        paint.setStrokeWidth(highlighter ? penSize * 3.0f : penSize);
        if (highlighter) {
            paint.setStrokeCap(Paint.Cap.SQUARE);
        }
        return paint;
    }

    private Paint configureEraserPaint() {
        Paint eraser = new Paint(Paint.ANTI_ALIAS_FLAG);
        eraser.setAntiAlias(true);
        eraser.setStyle(Paint.Style.STROKE);
        eraser.setStrokeCap(Paint.Cap.ROUND);
        eraser.setStrokeJoin(Paint.Join.ROUND);
        eraser.setStrokeWidth(penSize * 3.0f);
        eraser.setColor(Color.TRANSPARENT);
        eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        return eraser;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (replaying) return true;

        if (placingRecallRegion) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    screenToPage(event.getX(), event.getY(), pagePoint);
                    recallStartX = recallEndX = pagePoint[0];
                    recallStartY = recallEndY = pagePoint[1];
                    recallRegionDrawing = true;
                    requestParentIntercept(true);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!recallRegionDrawing) return true;
                    screenToPage(event.getX(), event.getY(), pagePoint);
                    recallEndX = pagePoint[0];
                    recallEndY = pagePoint[1];
                    postInvalidateOnAnimation();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (recallRegionDrawing && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        screenToPage(event.getX(), event.getY(), pagePoint);
                        recallEndX = pagePoint[0];
                        recallEndY = pagePoint[1];
                        float left = Math.min(recallStartX, recallEndX);
                        float top = Math.min(recallStartY, recallEndY);
                        float right = Math.max(recallStartX, recallEndX);
                        float bottom = Math.max(recallStartY, recallEndY);
                        if (interactionListener != null) {
                            interactionListener.onRecallRegionPlaced(left, top, right, bottom);
                        }
                    }
                    placingRecallRegion = false;
                    recallRegionDrawing = false;
                    requestParentIntercept(false);
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        if (placingPin) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                screenToPage(event.getX(), event.getY(), pagePoint);
                placingPin = false;
                if (interactionListener != null) {
                    interactionListener.onPinPlaced(pagePoint[0], pagePoint[1]);
                }
                invalidate();
                return true;
            }
            return true;
        }

        if (recallMode) return true;
        if (!writeMode) return handlePanTouch(event);
        return handleWriteTouch(event);
    }

    private boolean handlePanTouch(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                panning = true;
                activePointerId = event.getPointerId(0);
                panLastX = event.getX(0);
                panLastY = event.getY(0);
                requestParentIntercept(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && panning && activePointerId >= 0) {
                    float x = event.getX(0);
                    float y = event.getY(0);
                    panX += x - panLastX;
                    panY += y - panLastY;
                    panLastX = x;
                    panLastY = y;
                    postInvalidateOnAnimation();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                panning = false;
                activePointerId = -1;
                requestParentIntercept(false);
                return true;

            default:
                return true;
        }
    }

    private boolean handleWriteTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                ignoredDown = false;

                if (event.getPointerCount() != 1) {
                    ignoredDown = true;
                    return true;
                }

                if (palmShield && isLikelyPalm(event, 0)) {
                    ignoredDown = true;
                    return true;
                }

                activePointerId = event.getPointerId(0);
                drawing = true;
                requestParentIntercept(true);

                if (isDrawingTool(tool) || isShapeTool(tool)) {
                    beginLiveOrShapeAction(event.getX(0), event.getY(0));
                }

                if (tool == TOOL_TEXT) {
                    drawing = false;
                    activePointerId = -1;
                    requestParentIntercept(false);
                    screenToPage(event.getX(0), event.getY(0), pagePoint);
                    if (listener != null) {
                        listener.onRequestText(pagePoint[0], pagePoint[1]);
                    }
                    return true;
                }

                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (ignoredDown || activePointerId < 0) return true;

                int index = event.findPointerIndex(activePointerId);
                if (index < 0) return true;

                if (isDrawingTool(tool)) {
                    for (int h = 0; h < event.getHistorySize(); h++) {
                        screenToPage(
                                event.getHistoricalX(index, h),
                                event.getHistoricalY(index, h),
                                pagePoint
                        );
                        appendLivePoint(pagePoint[0], pagePoint[1]);
                    }

                    screenToPage(event.getX(index), event.getY(index), pagePoint);
                    appendLivePoint(pagePoint[0], pagePoint[1]);

                    postInvalidateOnAnimation();
                    if (soundEngine != null && tool != TOOL_ERASER) soundEngine.tick();
                } else if (isShapeTool(tool)) {
                    screenToPage(event.getX(index), event.getY(index), pagePoint);
                    shapeEndX = pagePoint[0];
                    shapeEndY = pagePoint[1];
                    postInvalidateOnAnimation();
                }

                return true;
            }

            case MotionEvent.ACTION_UP: {
                if (ignoredDown) {
                    ignoredDown = false;
                    return true;
                }

                if (tool == TOOL_TEXT) return true;

                if (isDrawingTool(tool)) {
                    screenToPage(event.getX(0), event.getY(0), pagePoint);
                    appendLivePoint(pagePoint[0], pagePoint[1]);
                    commitLiveStroke();
                    return true;
                }

                if (isShapeTool(tool)) {
                    screenToPage(event.getX(0), event.getY(0), pagePoint);
                    shapeEndX = pagePoint[0];
                    shapeEndY = pagePoint[1];
                    commitShape();
                    return true;
                }

                drawing = false;
                activePointerId = -1;
                requestParentIntercept(false);
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                cancelLiveStroke();
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                // With a passive stylus, Android reports stylus contact as ordinary touch.
                // If a broad palm arrived first, permit a later small contact to take over.
                if (activePointerId < 0 && ignoredDown && event.getPointerCount() >= 2) {
                    int newIndex = event.getActionIndex();
                    if (newIndex >= 0 && newIndex < event.getPointerCount()
                            && !isLikelyPalm(event, newIndex)) {

                        activePointerId = event.getPointerId(newIndex);
                        ignoredDown = false;
                        drawing = true;
                        requestParentIntercept(true);

                        screenToPage(event.getX(newIndex), event.getY(newIndex), pagePoint);
                        if (isDrawingTool(tool)) {
                            beginLiveStroke(pagePoint[0], pagePoint[1]);
                        } else if (isShapeTool(tool)) {
                            shapeStartX = pagePoint[0];
                            shapeStartY = pagePoint[1];
                            shapeEndX = pagePoint[0];
                            shapeEndY = pagePoint[1];
                        }
                        invalidate();
                    }
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                return true;

            default:
                return true;
        }
    }

    private void beginLiveOrShapeAction(float screenX, float screenY) {
        screenToPage(screenX, screenY, pagePoint);
        if (isDrawingTool(tool)) {
            beginLiveStroke(pagePoint[0], pagePoint[1]);
        } else if (isShapeTool(tool)) {
            shapeStartX = pagePoint[0];
            shapeStartY = pagePoint[1];
            shapeEndX = pagePoint[0];
            shapeEndY = pagePoint[1];
        }
    }

    private boolean isDrawingTool(int value) {
        return value == TOOL_PEN || value == TOOL_HIGHLIGHTER || value == TOOL_ERASER;
    }

    private void beginLiveStroke(float x, float y) {
        liveStroke.reset();
        livePoints.clear();
        livePoints.add(new PointF(x, y));
        liveStroke.moveTo(x, y);
        liveLastX = x;
        liveLastY = y;
        liveCurveEndX = x;
        liveCurveEndY = y;
        liveStrokeActive = true;
        liveStrokeMoved = false;
        if (interactionListener != null) {
            interactionListener.onStrokeStarted(x, y, tool);
        }
    }

    private void appendLivePoint(float rawX, float rawY) {
        if (!liveStrokeActive) {
            beginLiveStroke(rawX, rawY);
            return;
        }

        float alpha = 1f - stabilizer;
        float x = liveLastX + (rawX - liveLastX) * alpha;
        float y = liveLastY + (rawY - liveLastY) * alpha;

        float distance = (float) Math.hypot(x - liveLastX, y - liveLastY);
        if (distance < 0.22f) return;

        livePoints.add(new PointF(x, y));

        if (livePoints.size() == 2) {
            liveStroke.lineTo(x, y);
            liveCurveEndX = x;
            liveCurveEndY = y;
        } else {
            float midX = (liveLastX + x) * 0.5f;
            float midY = (liveLastY + y) * 0.5f;
            liveStroke.quadTo(liveLastX, liveLastY, midX, midY);
            liveCurveEndX = midX;
            liveCurveEndY = midY;
        }

        // Eraser is applied immediately to the backing bitmap while the user moves.
        // It is NOT deferred until ACTION_UP, so the page visibly erases under the
        // stylus/finger exactly as it would on a physical page.
        if (tool == TOOL_ERASER && inkBitmap != null) {
            drawImmediateEraserSegment(liveLastX, liveLastY, x, y);
        }

        liveLastX = x;
        liveLastY = y;
        liveStrokeMoved = true;
    }

    private void drawImmediateEraserSegment(float x1, float y1, float x2, float y2) {
        if (inkBitmap == null) return;

        Paint eraser = new Paint(Paint.ANTI_ALIAS_FLAG);
        eraser.setStyle(Paint.Style.STROKE);
        eraser.setStrokeCap(Paint.Cap.ROUND);
        eraser.setStrokeJoin(Paint.Join.ROUND);
        eraser.setStrokeWidth(Math.max(8f, penSize * 3.2f));
        eraser.setColor(Color.TRANSPARENT);
        eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        Canvas bitmapCanvas = new Canvas(inkBitmap);
        bitmapCanvas.drawLine(x1, y1, x2, y2, eraser);
        eraser.setXfermode(null);
    }

    private void commitLiveStroke() {
        if (!liveStrokeActive || livePoints.isEmpty()) {
            cancelLiveStroke();
            return;
        }

        ArrayList<PointF> points = new ArrayList<>(livePoints.size());
        for (PointF point : livePoints) {
            points.add(new PointF(point.x, point.y));
        }

        if (tool == TOOL_ERASER) {
            // The erase has already been applied live. Add the command to history only;
            // executing it again is unnecessary and can create extra work on large strokes.
            undo.addLast(new StrokeCommand(points, inkColor, penSize, tool));
            while (undo.size() > 80) {
                EditCommand old = undo.removeFirst();
                old.release();
            }
            releaseRedo();
            notifyDirty();
            cancelLiveStroke();
        } else {
            execute(new StrokeCommand(points, inkColor, penSize, tool));
            cancelLiveStroke();
        }
    }

    private void cancelLiveStroke() {
        liveStroke.reset();
        livePoints.clear();
        liveStrokeActive = false;
        liveStrokeMoved = false;
        drawing = false;
        activePointerId = -1;
        requestParentIntercept(false);
        invalidate();
    }

    private void commitShape() {
        RectF rect = new RectF(
                Math.min(shapeStartX, shapeEndX),
                Math.min(shapeStartY, shapeEndY),
                Math.max(shapeStartX, shapeEndX),
                Math.max(shapeStartY, shapeEndY)
        );

        execute(new ShapeCommand(
                tool,
                shapeStartX,
                shapeStartY,
                shapeEndX,
                shapeEndY,
                rect,
                inkColor,
                Math.max(2f, penSize)
        ));

        drawing = false;
        activePointerId = -1;
        requestParentIntercept(false);
    }

    private float eraserRadius() {
        return Math.max(dp(8f), penSize * 1.5f);
    }

    private boolean isLikelyPalm(MotionEvent event, int index) {
        float major = event.getToolMajor(index);
        float minor = event.getToolMinor(index);
        float size = event.getSize(index);
        float threshold = dp(28f);
        return major > threshold || minor > threshold || size > 0.30f;
    }

    private void requestParentIntercept(boolean disallow) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private void screenToPage(float screenX, float screenY, float[] out) {
        updatePageRect();
        float scale = currentScale();
        out[0] = clamp((screenX - pageRect.left) / scale, 0f, PAGE_WIDTH);
        out[1] = clamp((screenY - pageRect.top) / scale, 0f, PAGE_HEIGHT);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private abstract static class EditCommand {
        abstract void apply(Canvas canvas);
        void release() {}
    }

    private static Path buildSmoothPath(ArrayList<PointF> points) {
        Path path = new Path();
        if (points.isEmpty()) return path;

        PointF first = points.get(0);
        path.moveTo(first.x, first.y);

        if (points.size() == 1) return path;
        if (points.size() == 2) {
            PointF second = points.get(1);
            path.lineTo(second.x, second.y);
            return path;
        }

        PointF previous = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            PointF current = points.get(i);
            if (i == points.size() - 1) {
                path.quadTo(previous.x, previous.y, current.x, current.y);
            } else {
                PointF next = points.get(i + 1);
                float midX = (current.x + next.x) * 0.5f;
                float midY = (current.y + next.y) * 0.5f;
                path.quadTo(current.x, current.y, midX, midY);
            }
            previous = current;
        }

        return path;
    }

    private static final class StrokeCommand extends EditCommand {
        private final ArrayList<PointF> points;
        private final int color;
        private final float width;
        private final int tool;

        StrokeCommand(ArrayList<PointF> points, int color, float width, int tool) {
            this.points = points;
            this.color = color;
            this.width = width;
            this.tool = tool;
        }

        @Override
        void apply(Canvas canvas) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(tool == TOOL_ERASER ? width * 3f
                    : tool == TOOL_HIGHLIGHTER ? width * 3f : width);

            if (tool == TOOL_ERASER) {
                p.setColor(Color.TRANSPARENT);
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            } else {
                p.setColor(color);
                p.setAlpha(tool == TOOL_HIGHLIGHTER ? 90 : 255);
                if (tool == TOOL_HIGHLIGHTER) p.setStrokeCap(Paint.Cap.SQUARE);
            }

            Path path = buildSmoothPath(points);
            if (points.size() == 1) {
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(points.get(0).x, points.get(0).y, Math.max(0.8f, p.getStrokeWidth() * 0.5f), p);
            } else {
                canvas.drawPath(path, p);
            }

            p.setXfermode(null);
        }
    }

    private static final class ShapeCommand extends EditCommand {
        private final int tool;
        private final float startX;
        private final float startY;
        private final float endX;
        private final float endY;
        private final RectF rect;
        private final int color;
        private final float width;

        ShapeCommand(int tool, float startX, float startY, float endX, float endY,
                     RectF rect, int color, float width) {
            this.tool = tool;
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.rect = new RectF(rect);
            this.color = color;
            this.width = width;
        }

        @Override
        void apply(Canvas canvas) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(width);
            p.setColor(color);

            if (tool == TOOL_LINE) {
                canvas.drawLine(startX, startY, endX, endY, p);
            } else if (tool == TOOL_RECT) {
                canvas.drawRect(rect, p);
            } else if (tool == TOOL_OVAL) {
                canvas.drawOval(rect, p);
            }
        }
    }

    private static final class TextCommand extends EditCommand {
        private final String text;
        private final float x;
        private final float y;
        private final int color;
        private final float size;

        TextCommand(String text, float x, float y, int color, float size) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.size = size;
        }

        @Override
        void apply(Canvas canvas) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(color);
            p.setTextSize(size);
            p.setTypeface(Typeface.create("sans", Typeface.NORMAL));

            float lineY = y;
            for (String line : text.split("\\n")) {
                canvas.drawText(line, x, lineY, p);
                lineY += size * 1.25f;
            }
        }
    }

    private static final class ImageCommand extends EditCommand {
        private Bitmap bitmap;
        private final RectF destination;

        ImageCommand(Bitmap bitmap, RectF destination) {
            this.bitmap = bitmap;
            this.destination = new RectF(destination);
        }

        @Override
        void apply(Canvas canvas) {
            if (bitmap != null && !bitmap.isRecycled()) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                canvas.drawBitmap(bitmap, null, destination, p);
            }
        }

        @Override
        void release() {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            bitmap = null;
        }
    }

    private static final class ClearCommand extends EditCommand {
        @Override
        void apply(Canvas canvas) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }
    }

    public static Bitmap renderPage(Bitmap ink, String paperType, boolean marginEnabled) {
        Bitmap page = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(page);
        drawPaperBackground(canvas, paperType, marginEnabled);
        if (ink != null && !ink.isRecycled()) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(ink, 0f, 0f, p);
        }
        return page;
    }

    public static void drawPaperBackground(Canvas canvas, String type, boolean marginEnabled) {
        canvas.drawColor(Color.rgb(255, 252, 245));

        Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(1f);
        grid.setColor(Color.rgb(221, 225, 233));

        if (PAPER_RULED.equals(type)) {
            for (int y = 84; y < PAGE_HEIGHT; y += 54) {
                canvas.drawLine(0, y, PAGE_WIDTH, y, grid);
            }
        } else if (PAPER_GRAPH.equals(type)) {
            grid.setColor(Color.rgb(224, 229, 236));
            for (int x = 0; x < PAGE_WIDTH; x += 40) canvas.drawLine(x, 0, x, PAGE_HEIGHT, grid);
            for (int y = 0; y < PAGE_HEIGHT; y += 40) canvas.drawLine(0, y, PAGE_WIDTH, y, grid);
            grid.setColor(Color.rgb(201, 208, 220));
            for (int x = 0; x < PAGE_WIDTH; x += 200) canvas.drawLine(x, 0, x, PAGE_HEIGHT, grid);
            for (int y = 0; y < PAGE_HEIGHT; y += 200) canvas.drawLine(0, y, PAGE_WIDTH, y, grid);
        } else if (PAPER_DOT.equals(type)) {
            grid.setStyle(Paint.Style.FILL);
            grid.setColor(Color.rgb(198, 204, 214));
            for (int y = 30; y < PAGE_HEIGHT; y += 36) {
                for (int x = 30; x < PAGE_WIDTH; x += 36) {
                    canvas.drawCircle(x, y, 1.6f, grid);
                }
            }
        } else if (PAPER_MATH.equals(type)) {
            for (int y = 80; y < PAGE_HEIGHT; y += 62) {
                canvas.drawLine(0, y, PAGE_WIDTH, y, grid);
            }
            grid.setColor(Color.rgb(205, 209, 217));
            grid.setStrokeWidth(2f);
            for (int y = 80; y < PAGE_HEIGHT; y += 248) {
                canvas.drawLine(0, y, PAGE_WIDTH, y, grid);
            }
        } else if (PAPER_EXAM_2.equals(type) || PAPER_EXAM_3.equals(type) || PAPER_EXAM_4.equals(type)) {
            for (int y = 205; y < PAGE_HEIGHT; y += 70) {
                canvas.drawLine(132, y, PAGE_WIDTH - 70, y, grid);
            }
            Paint header = new Paint(Paint.ANTI_ALIAS_FLAG);
            header.setColor(Color.rgb(88, 96, 112));
            header.setTextSize(30f);
            header.setTypeface(Typeface.DEFAULT_BOLD);
            String marks = PAPER_EXAM_2.equals(type) ? "2 MARK ANSWER" :
                    PAPER_EXAM_3.equals(type) ? "3 MARK ANSWER" : "4 MARK ANSWER";
            canvas.drawText(marks, 145, 72, header);
        } else if (PAPER_EXPERIMENT.equals(type)) {
            Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
            title.setColor(Color.rgb(75, 84, 102));
            title.setTextSize(28f);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("SCIENCE EXPERIMENT", 145, 68, title);
            String[] sections = {"Aim", "Apparatus / Materials", "Procedure", "Observations", "Calculations", "Result", "Precautions"};
            float top = 120f;
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setColor(Color.rgb(218, 223, 231));
            line.setStrokeWidth(1.5f);
            for (String section : sections) {
                Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
                label.setColor(Color.rgb(88, 96, 112));
                label.setTextSize(22f);
                label.setTypeface(Typeface.DEFAULT_BOLD);
                canvas.drawText(section, 145, top, label);
                top += 20f;
                for (int i = 0; i < 3; i++) {
                    canvas.drawLine(145, top + i * 58f, PAGE_WIDTH - 90, top + i * 58f, line);
                }
                top += 205f;
                if (top > PAGE_HEIGHT - 140) break;
            }
        }

        if (marginEnabled) {
            Paint margin = new Paint(Paint.ANTI_ALIAS_FLAG);
            margin.setColor(Color.rgb(236, 139, 151));
            margin.setStrokeWidth(2f);
            canvas.drawLine(108, 0, 108, PAGE_HEIGHT, margin);
        }
    }
}
