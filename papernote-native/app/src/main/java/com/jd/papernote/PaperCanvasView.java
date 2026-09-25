package com.jd.papernote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayDeque;
import java.util.Deque;

public final class PaperCanvasView extends View {
    public static final int PAGE_WIDTH = 1536;
    public static final int PAGE_HEIGHT = 2168;

    public static final String PAPER_BLANK = "blank";
    public static final String PAPER_RULED = "ruled";
    public static final String PAPER_GRAPH = "graph";
    public static final String PAPER_DOT = "dot";
    public static final String PAPER_MATH = "math";

    public static final int TOOL_PEN = 0;
    public static final int TOOL_HIGHLIGHTER = 1;
    public static final int TOOL_ERASER = 2;
    public static final int TOOL_LINE = 3;
    public static final int TOOL_RECT = 4;
    public static final int TOOL_OVAL = 5;
    public static final int TOOL_TEXT = 6;

    public interface Listener {
        void onCanvasDirty();
        void onRequestText(float pageX, float pageY);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF pageRect = new RectF();
    private final Deque<EditCommand> undo = new ArrayDeque<>();
    private final Deque<EditCommand> redo = new ArrayDeque<>();

    private Bitmap inkBitmap;
    private Bitmap baseBitmap;
    private Listener listener;

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
    private float[] livePointX = new float[1024];
    private float[] livePointY = new float[1024];
    private int livePointCount = 0;
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
        trimUndoHistory();
        releaseRedo();
        applyCommand(command);
        notifyDirty();
    }

    private void applyCommand(EditCommand command) {
        command.apply(new Canvas(inkBitmap));
    }

    private void rebuildFromBase() {
        if (baseBitmap == null || inkBitmap == null || inkBitmap.isRecycled()) return;
        Canvas canvas = new Canvas(inkBitmap);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        canvas.drawBitmap(baseBitmap, 0f, 0f, bitmapPaint);
        for (EditCommand command : undo) {
            command.apply(canvas);
        }
    }

    private void releaseRedo() {
        while (!redo.isEmpty()) {
            redo.removeFirst().release();
        }
    }

    private void trimUndoHistory() {
        while (undo.size() > 40) {
            EditCommand old = undo.removeFirst();
            old.release();
        }
    }

    private void clearHistory() {
        while (!undo.isEmpty()) undo.removeFirst().release();
        while (!redo.isEmpty()) redo.removeFirst().release();
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

        if (inkBitmap != null) {
            canvas.drawBitmap(inkBitmap, 0f, 0f, bitmapPaint);
        }

        if (liveStrokeActive && tool != TOOL_ERASER
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

        if (liveStrokeActive && tool == TOOL_ERASER) {
            Paint eraserPreview = new Paint(Paint.ANTI_ALIAS_FLAG);
            eraserPreview.setStyle(Paint.Style.STROKE);
            eraserPreview.setStrokeWidth(Math.max(1f, penSize * 0.45f));
            eraserPreview.setColor(0x88606A78);
            canvas.drawCircle(liveLastX, liveLastY, eraserRadius(), eraserPreview);
        }

        if (drawing && isShapeTool(tool)) {
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

        canvas.restore();

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(1));
        border.setColor(0x20000000);
        canvas.drawRect(pageRect, border);
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
        livePointCount = 0;
        if (livePointCount >= livePointX.length) {
            if (livePointX.length >= 4096) return;
            int newSize = Math.min(4096, livePointX.length * 2);
            float[] nx = new float[newSize];
            float[] ny = new float[newSize];
            System.arraycopy(livePointX, 0, nx, 0, livePointCount);
            System.arraycopy(livePointY, 0, ny, 0, livePointCount);
            livePointX = nx;
            livePointY = ny;
        }
        livePointX[livePointCount] = x;
        livePointY[livePointCount] = y;
        livePointCount++;
        liveStroke.moveTo(x, y);
        liveLastX = x;
        liveLastY = y;
        liveCurveEndX = x;
        liveCurveEndY = y;
        liveStrokeActive = true;
        liveStrokeMoved = false;
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
        if (distance < 1.15f) return;

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

    private final Paint eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private void drawImmediateEraserSegment(float x1, float y1, float x2, float y2) {
        if (inkBitmap == null) return;

        eraserPaint.setStyle(Paint.Style.STROKE);
        eraserPaint.setStrokeCap(Paint.Cap.ROUND);
        eraserPaint.setStrokeJoin(Paint.Join.ROUND);
        eraserPaint.setStrokeWidth(Math.max(8f, penSize * 3.2f));
        eraserPaint.setColor(Color.TRANSPARENT);
        eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        new Canvas(inkBitmap).drawLine(x1, y1, x2, y2, eraserPaint);
        eraserPaint.setXfermode(null);
    }

    private void commitLiveStroke() {
        if (!liveStrokeActive || livePointCount == 0) {
            cancelLiveStroke();
            return;
        }

        StrokeCommand command = StrokeCommand.fromBuffers(
                livePointX, livePointY, livePointCount, inkColor, penSize, tool
        );

        if (tool == TOOL_ERASER) {
            undo.addLast(command);
            trimUndoHistory();
            releaseRedo();
            notifyDirty();
            cancelLiveStroke();
        } else {
            execute(command);
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

    private static final class StrokeCommand extends EditCommand {
        private final float[] xs;
        private final float[] ys;
        private final int count;
        private final int color;
        private final float width;
        private final int tool;

        private StrokeCommand(float[] xs, float[] ys, int count, int color, float width, int tool) {
            this.xs = xs;
            this.ys = ys;
            this.count = count;
            this.color = color;
            this.width = width;
            this.tool = tool;
        }

        static StrokeCommand fromBuffers(float[] xs, float[] ys, int count,
                                          int color, float width, int tool) {
            int safeCount = Math.max(1, Math.min(count, Math.min(xs.length, ys.length)));
            float[] copyX = new float[safeCount];
            float[] copyY = new float[safeCount];
            System.arraycopy(xs, 0, copyX, 0, safeCount);
            System.arraycopy(ys, 0, copyY, 0, safeCount);
            return new StrokeCommand(copyX, copyY, safeCount, color, width, tool);
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

            if (count == 1) {
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(xs[0], ys[0],
                        Math.max(0.8f, p.getStrokeWidth() * 0.5f), p);
            } else {
                Path path = new Path();
                path.moveTo(xs[0], ys[0]);
                for (int i = 1; i < count; i++) {
                    if (i == count - 1) {
                        path.quadTo(xs[i - 1], ys[i - 1], xs[i], ys[i]);
                    } else {
                        float midX = (xs[i] + xs[i + 1]) * 0.5f;
                        float midY = (ys[i] + ys[i + 1]) * 0.5f;
                        path.quadTo(xs[i], ys[i], midX, midY);
                    }
                }
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
        }

        if (marginEnabled) {
            Paint margin = new Paint(Paint.ANTI_ALIAS_FLAG);
            margin.setColor(Color.rgb(236, 139, 151));
            margin.setStrokeWidth(2f);
            canvas.drawLine(108, 0, 108, PAGE_HEIGHT, margin);
        }
    }
}
