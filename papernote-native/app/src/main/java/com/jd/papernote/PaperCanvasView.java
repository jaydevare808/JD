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

import java.io.ByteArrayOutputStream;
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

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF pageRect = new RectF();
    private final Deque<byte[]> undo = new ArrayDeque<>();
    private final Deque<byte[]> redo = new ArrayDeque<>();

    private Bitmap inkBitmap;
    private Listener listener;
    private SoundEngine soundEngine;

    private int tool = TOOL_PEN;
    private int inkColor = Color.rgb(24, 35, 51);
    private float penSize = 5.5f;
    private float stabilizer = 0.06f;
    private boolean writeMode = true;
    private boolean palmShield = true;
    private boolean marginEnabled = true;
    private String paperType = PAPER_RULED;

    private int activePointerId = -1;
    private boolean drawing;
    private boolean ignoredDown;
    private float lastPageX;
    private float lastPageY;
    private float shapeStartX;
    private float shapeStartY;
    private float shapeEndX;
    private float shapeEndY;
    private byte[] actionSnapshot;

    private float baseScale = 1f;
    private float zoom = 1f;
    private float panX = 0f;
    private float panY = 0f;
    private float panLastX;
    private float panLastY;
    private boolean panning;

    private final ScaleGestureDetector scaleDetector;

    public PaperCanvasView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setBackgroundColor(Color.rgb(223, 226, 231));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (!writeMode) {
                    float oldZoom = zoom;
                    zoom = Math.max(1f, Math.min(3.0f, zoom * detector.getScaleFactor()));
                    if (Math.abs(zoom - oldZoom) > 0.001f) invalidate();
                }
                return true;
            }
        });
        setFocusable(true);
        setClickable(true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setSoundEngine(SoundEngine soundEngine) {
        this.soundEngine = soundEngine;
    }

    public void setTool(int tool) {
        this.tool = tool;
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
        penSize = Math.max(1.5f, Math.min(28f, size));
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
        cancelCurrentAction();
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
        if (inkBitmap != null && inkBitmap != bitmap && !inkBitmap.isRecycled()) {
            inkBitmap.recycle();
        }
        inkBitmap = bitmap == null ? Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888) : bitmap;
        undo.clear();
        redo.clear();
        actionSnapshot = null;
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
        beginAction();
        Canvas c = new Canvas(inkBitmap);
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(inkColor);
        textPaint.setTextSize(Math.max(26f, penSize * 6.0f));
        textPaint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        String[] lines = text.split("\n");
        float y = clamp(pageY, 20f, PAGE_HEIGHT - 20f);
        for (String line : lines) {
            c.drawText(line, clamp(pageX, 10f, PAGE_WIDTH - 20f), y, textPaint);
            y += textPaint.getTextSize() * 1.25f;
        }
        finishAction();
    }

    public void addImage(Bitmap image) {
        if (inkBitmap == null || image == null) return;
        beginAction();
        int maxWidth = (int) (PAGE_WIDTH * 0.72f);
        int maxHeight = (int) (PAGE_HEIGHT * 0.58f);
        float scale = Math.min(1f, Math.min(maxWidth / (float) image.getWidth(), maxHeight / (float) image.getHeight()));
        int w = Math.max(1, Math.round(image.getWidth() * scale));
        int h = Math.max(1, Math.round(image.getHeight() * scale));
        float left = (PAGE_WIDTH - w) * 0.5f;
        float top = (PAGE_HEIGHT - h) * 0.25f;
        RectF dst = new RectF(left, top, left + w, top + h);
        Canvas c = new Canvas(inkBitmap);
        c.drawBitmap(image, null, dst, bitmapPaint);
        finishAction();
    }

    public void clearPage() {
        if (inkBitmap == null) return;
        beginAction();
        Canvas c = new Canvas(inkBitmap);
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        finishAction();
    }

    public void undo() {
        if (undo.isEmpty() || inkBitmap == null) return;
        byte[] previous = undo.removeLast();
        redo.addLast(snapshot());
        restore(previous);
        actionSnapshot = null;
        if (listener != null) listener.onCanvasDirty();
    }

    public void redo() {
        if (redo.isEmpty() || inkBitmap == null) return;
        byte[] next = redo.removeLast();
        undo.addLast(snapshot());
        restore(next);
        actionSnapshot = null;
        if (listener != null) listener.onCanvasDirty();
    }

    private void beginAction() {
        if (inkBitmap == null) return;
        actionSnapshot = snapshot();
        undo.addLast(actionSnapshot);
        while (undo.size() > 14) undo.removeFirst();
        redo.clear();
    }

    private void finishAction() {
        actionSnapshot = null;
        drawing = false;
        panning = false;
        activePointerId = -1;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
        if (listener != null) listener.onCanvasDirty();
    }

    private void cancelCurrentAction() {
        if (actionSnapshot != null && inkBitmap != null) {
            if (!undo.isEmpty() && undo.peekLast() == actionSnapshot) undo.removeLast();
            restore(actionSnapshot);
            actionSnapshot = null;
        }
        drawing = false;
        panning = false;
        activePointerId = -1;
        ignoredDown = false;
        invalidate();
    }

    private byte[] snapshot() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        inkBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }

    private void restore(byte[] bytes) {
        android.graphics.Bitmap decoded = BitmapFactoryCompat.decode(bytes);
        if (decoded == null) return;
        if (inkBitmap != null && !inkBitmap.isRecycled()) inkBitmap.recycle();
        inkBitmap = decoded;
        invalidate();
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

        if (drawing && isShapeTool(tool)) {
            Paint preview = configureStrokePaint(false);
            preview.setStyle(Paint.Style.STROKE);
            preview.setColor(inkColor);
            preview.setAlpha(200);
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

        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0x20000000);
        shadow.setStyle(Paint.Style.STROKE);
        shadow.setStrokeWidth(dp(1));
        canvas.drawRect(pageRect, shadow);
    }

    private boolean isShapeTool(int value) {
        return value == TOOL_LINE || value == TOOL_RECT || value == TOOL_OVAL;
    }

    private Paint configureStrokePaint(boolean eraser) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setFilterBitmap(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);

        if (eraser) {
            paint.setColor(Color.TRANSPARENT);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            paint.setStrokeWidth(penSize * 3.0f);
        } else {
            paint.setXfermode(null);
            paint.setColor(inkColor);
            paint.setStrokeWidth(penSize);
            if (tool == TOOL_HIGHLIGHTER) {
                paint.setAlpha(88);
                paint.setStrokeWidth(penSize * 3.2f);
                paint.setStrokeCap(Paint.Cap.SQUARE);
            } else {
                paint.setAlpha(255);
            }
        }
        return paint;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!writeMode) {
            return handlePanTouch(event);
        }
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
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && panning && activePointerId >= 0) {
                    float x = event.getX(0);
                    float y = event.getY(0);
                    panX += x - panLastX;
                    panY += y - panLastY;
                    panLastX = x;
                    panLastY = y;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                panning = false;
                activePointerId = -1;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return true;
        }
    }

    private boolean handleWriteTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
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
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);

                if (tool == TOOL_PEN || tool == TOOL_HIGHLIGHTER || tool == TOOL_ERASER || isShapeTool(tool)) {
                    beginAction();
                }

                float[] p = screenToPage(event.getX(0), event.getY(0));
                lastPageX = p[0];
                lastPageY = p[1];

                if (tool == TOOL_PEN || tool == TOOL_HIGHLIGHTER || tool == TOOL_ERASER) {
                    drawDot(p[0], p[1], event.getPressure(0), event.getToolType(0));
                    invalidate();
                    if (soundEngine != null && tool != TOOL_ERASER) soundEngine.tick();
                } else if (isShapeTool(tool)) {
                    shapeStartX = p[0];
                    shapeStartY = p[1];
                    shapeEndX = p[0];
                    shapeEndY = p[1];
                    invalidate();
                } else if (tool == TOOL_TEXT) {
                    drawing = false;
                    activePointerId = -1;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (ignoredDown || activePointerId < 0) return true;
                int index = event.findPointerIndex(activePointerId);
                if (index < 0) return true;

                float[] current = screenToPage(event.getX(index), event.getY(index));

                if (tool == TOOL_PEN || tool == TOOL_HIGHLIGHTER || tool == TOOL_ERASER) {
                    for (int h = 0; h < event.getHistorySize(index); h++) {
                        float[] hp = screenToPage(
                                event.getHistoricalX(index, h),
                                event.getHistoricalY(index, h)
                        );
                        drawSegment(lastPageX, lastPageY, hp[0], hp[1],
                                event.getHistoricalPressure(index, h),
                                event.getToolType(index));
                        lastPageX = hp[0];
                        lastPageY = hp[1];
                    }

                    drawSegment(lastPageX, lastPageY, current[0], current[1],
                            event.getPressure(index), event.getToolType(index));
                    lastPageX = current[0];
                    lastPageY = current[1];
                    invalidate();
                    if (soundEngine != null && tool != TOOL_ERASER) soundEngine.tick();
                } else if (isShapeTool(tool)) {
                    shapeEndX = current[0];
                    shapeEndY = current[1];
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (ignoredDown) {
                    ignoredDown = false;
                    return true;
                }

                if (tool == TOOL_TEXT) {
                    float[] textPoint = screenToPage(event.getX(0), event.getY(0));
                    drawing = false;
                    activePointerId = -1;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    if (listener != null) listener.onRequestText(textPoint[0], textPoint[1]);
                    return true;
                }

                if (isShapeTool(tool)) {
                    float[] end = screenToPage(event.getX(0), event.getY(0));
                    shapeEndX = end[0];
                    shapeEndY = end[1];
                    commitShape();
                } else if (tool == TOOL_PEN || tool == TOOL_HIGHLIGHTER || tool == TOOL_ERASER) {
                    float[] end = screenToPage(event.getX(0), event.getY(0));
                    if (Math.hypot(end[0] - lastPageX, end[1] - lastPageY) > 0.15f) {
                        drawSegment(lastPageX, lastPageY, end[0], end[1],
                                event.getPressure(0), event.getToolType(0));
                    }
                    finishAction();
                } else {
                    drawing = false;
                    activePointerId = -1;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                cancelCurrentAction();
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                // Passive capacitive styluses are reported as ordinary touch contacts. If a broad
                // palm contact arrived first, allow a later, small contact to take over as the
                // writing contact. If a stroke is already active, keep extra contacts ignored.
                if (activePointerId < 0 && ignoredDown && event.getPointerCount() >= 2) {
                    int newIndex = event.getActionIndex();
                    if (newIndex >= 0 && newIndex < event.getPointerCount() && !isLikelyPalm(event, newIndex)) {
                        activePointerId = event.getPointerId(newIndex);
                        ignoredDown = false;
                        drawing = true;
                        if (tool == TOOL_PEN || tool == TOOL_HIGHLIGHTER || tool == TOOL_ERASER || isShapeTool(tool)) {
                            beginAction();
                        }
                        float[] takeover = screenToPage(event.getX(newIndex), event.getY(newIndex));
                        lastPageX = takeover[0];
                        lastPageY = takeover[1];

                        if (tool == TOOL_PEN || tool == TOOL_HIGHLIGHTER || tool == TOOL_ERASER) {
                            drawDot(takeover[0], takeover[1], event.getPressure(newIndex), event.getToolType(newIndex));
                            if (soundEngine != null && tool != TOOL_ERASER) soundEngine.tick();
                        } else if (isShapeTool(tool)) {
                            shapeStartX = takeover[0];
                            shapeStartY = takeover[1];
                            shapeEndX = takeover[0];
                            shapeEndY = takeover[1];
                        }
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
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

    private boolean isLikelyPalm(MotionEvent event, int index) {
        float major = event.getToolMajor(index);
        float minor = event.getToolMinor(index);
        float size = event.getSize(index);
        float threshold = dp(34);
        return major > threshold || minor > threshold || size > 0.36f;
    }

    private void drawDot(float x, float y, float pressure, int toolType) {
        if (inkBitmap == null) return;
        Paint p = configureStrokePaint(tool == TOOL.ERASER);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(getEffectiveWidth(pressure, toolType));
        Canvas c = new Canvas(inkBitmap);
        c.drawPoint(x, y, p);
        p.setXfermode(null);
    }

    private void drawSegment(float x1, float y1, float x2, float y2, float pressure, int toolType) {
        if (inkBitmap == null) return;

        float alpha = 1f - (stabilizer * 0.35f);
        float targetX = x1 + (x2 - x1) * alpha;
        float targetY = y1 + (y2 - y1) * alpha;

        if (Math.hypot(targetX - x1, targetY - y1) < 0.15f) return;

        Paint p = configureStrokePaint(tool == TOOL.ERASER);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(getEffectiveWidth(pressure, toolType));

        Canvas c = new Canvas(inkBitmap);
        c.drawLine(x1, y1, targetX, targetY, p);
        p.setXfermode(null);
    }

    private float getEffectiveWidth(float pressure, int toolType) {
        if (tool == TOOL_ERASER) return penSize * 3.0f;
        if (tool == TOOL_HIGHLIGHTER) return penSize * 3.2f;
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS && pressure > 0f) {
            float normalized = Math.max(0.15f, Math.min(1f, pressure));
            return penSize * (0.72f + normalized * 0.48f);
        }
        // Passive capacitive styluses identify as finger/touch on Android, so use a
        // stable width rather than inventing pressure data.
        return penSize;
    }

    private void commitShape() {
        if (inkBitmap == null) return;
        Paint p = configureStrokePaint(false);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, penSize));
        p.setAlpha(255);

        RectF rect = new RectF(
                Math.min(shapeStartX, shapeEndX),
                Math.min(shapeStartY, shapeEndY),
                Math.max(shapeStartX, shapeEndX),
                Math.max(shapeStartY, shapeEndY)
        );
        Canvas c = new Canvas(inkBitmap);
        if (tool == TOOL_LINE) {
            c.drawLine(shapeStartX, shapeStartY, shapeEndX, shapeEndY, p);
        } else if (tool == TOOL_RECT) {
            c.drawRect(rect, p);
        } else if (tool == TOOL_OVAL) {
            c.drawOval(rect, p);
        }
        p.setXfermode(null);
        finishAction();
    }

    private float[] screenToPage(float screenX, float screenY) {
        updatePageRect();
        float scale = currentScale();
        float x = (screenX - pageRect.left) / scale;
        float y = (screenY - pageRect.top) / scale;
        return new float[] {
                clamp(x, 0f, PAGE_WIDTH),
                clamp(y, 0f, PAGE_HEIGHT)
        };
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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

    private static final class BitmapFactoryCompat {
        static Bitmap decode(byte[] bytes) {
            return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }
    }
}
