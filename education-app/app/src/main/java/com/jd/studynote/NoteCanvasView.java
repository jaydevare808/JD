package com.jd.studynote;

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
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

public final class NoteCanvasView extends View {
    public static final int PAGE_WIDTH = 1024;
    public static final int PAGE_HEIGHT = 1448;

    public static final String PAPER_BLANK = "blank";
    public static final String PAPER_RULED = "ruled";
    public static final String PAPER_GRAPH = "graph";

    public static final int TOOL_PEN = 0;
    public static final int TOOL_HIGHLIGHTER = 1;
    public static final int TOOL_ERASER = 2;

    private static final int MAX_UNDO_COMMANDS = 16;
    private static final int MAX_POINTS_PER_STROKE = 2048;
    private static final float MIN_POINT_DISTANCE = 0.9f;

    public interface Listener {
        void onCanvasDirty();
    }

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marginPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Deque<StrokeCommand> undo = new ArrayDeque<>();
    private final Deque<StrokeCommand> redo = new ArrayDeque<>();
    private final ArrayList<PointF> livePoints = new ArrayList<>(256);
    private final Path livePath = new Path();

    private Bitmap inkBitmap;
    private Bitmap baseBitmap;
    private Canvas inkCanvas;
    private Listener listener;

    private int tool = TOOL_PEN;
    private int inkColor = 0xFF1B1838;
    private float penSize = 6.0f;
    private String paperType = PAPER_RULED;

    private boolean drawing;
    private boolean ignoredTouch;
    private int activePointerId = -1;
    private float lastX;
    private float lastY;
    private final float[] pagePoint = new float[2];

    private float baseScale = 1f;
    private final RectF pageRect = new RectF();
    private long lastFrame = 0L;

    public NoteCanvasView(Context context) {
        super(context);
        setFocusable(true);
        setClickable(true);
        setBackgroundColor(0xFFE4E7EC);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        eraserPaint.setStyle(Paint.Style.STROKE);
        eraserPaint.setStrokeCap(Paint.Cap.ROUND);
        eraserPaint.setStrokeJoin(Paint.Join.ROUND);
        eraserPaint.setColor(Color.TRANSPARENT);
        eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        gridPaint.setStrokeWidth(1f);
        marginPaint.setColor(0xFFE4A1AE);
        marginPaint.setStrokeWidth(2f);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1f));
        borderPaint.setColor(0x22000000);

        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int getTool() {
        return tool;
    }

    public void setTool(int value) {
        if (value < TOOL_PEN || value > TOOL_ERASER) return;
        if (drawing) cancelLiveStroke(true);
        tool = value;
        invalidate();
    }

    public void setInkColor(int color) {
        inkColor = color;
    }

    public void setPenSize(float value) {
        penSize = clamp(value, 2f, 18f);
    }

    public void setPaperType(String value) {
        paperType = normalizePaper(value);
        invalidate();
    }

    public void loadBitmap(Bitmap bitmap) {
        releaseBitmaps();

        try {
            inkBitmap = bitmap == null
                    ? Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
                    : bitmap;
            baseBitmap = inkBitmap.copy(Bitmap.Config.ARGB_8888, true);
            if (baseBitmap == null) throw new OutOfMemoryError();
            inkCanvas = new Canvas(inkBitmap);
        } catch (OutOfMemoryError error) {
            releaseBitmaps();
            inkBitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888);
            baseBitmap = null;
            inkCanvas = new Canvas(inkBitmap);
        }

        invalidate();
    }

    public Bitmap getInkBitmap() {
        return inkBitmap;
    }

    public Bitmap exportBitmap() {
        if (inkBitmap == null || inkBitmap.isRecycled()) return null;

        Bitmap output;
        try {
            output = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError error) {
            return null;
        }

        Canvas canvas = new Canvas(output);
        drawPaper(canvas);
        canvas.drawBitmap(inkBitmap, 0f, 0f, bitmapPaint);
        return output;
    }

    public void clearUndoHistory() {
        undo.clear();
        redo.clear();
        replaceBaseBitmapWithCurrent();
    }

    public void clearPage() {
        if (inkBitmap == null) return;
        execute(StrokeCommand.clear());
    }

    public void undo() {
        if (undo.isEmpty() || inkBitmap == null || baseBitmap == null) return;

        redo.addLast(undo.removeLast());
        rebuild();
        dirty();
    }

    public void redo() {
        if (redo.isEmpty() || inkBitmap == null) return;

        StrokeCommand command = redo.removeLast();
        if (undo.size() >= MAX_UNDO_COMMANDS) undo.removeFirst();
        undo.addLast(command);

        apply(command, inkCanvas);
        dirty();
        invalidate();
    }

    private void execute(StrokeCommand command) {
        if (inkBitmap == null || inkCanvas == null) return;

        if (undo.size() >= MAX_UNDO_COMMANDS) {
            undo.removeFirst();
        }
        undo.addLast(command);
        redo.clear();
        apply(command, inkCanvas);
        dirty();
        invalidate();
    }

    private void rebuild() {
        if (baseBitmap == null || baseBitmap.isRecycled() || inkBitmap == null) return;

        Bitmap rebuilt;
        try {
            rebuilt = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
        } catch (OutOfMemoryError error) {
            return;
        }

        Bitmap old = inkBitmap;
        inkBitmap = rebuilt;
        inkCanvas = new Canvas(inkBitmap);

        for (StrokeCommand command : undo) {
            apply(command, inkCanvas);
        }

        if (old != null && !old.isRecycled() && old != baseBitmap) {
            old.recycle();
        }
    }

    private void replaceBaseBitmapWithCurrent() {
        if (inkBitmap == null || inkBitmap.isRecycled()) return;
        Bitmap replacement;
        try {
            replacement = inkBitmap.copy(Bitmap.Config.ARGB_8888, true);
        } catch (OutOfMemoryError error) {
            return;
        }

        if (baseBitmap != null && !baseBitmap.isRecycled()) {
            baseBitmap.recycle();
        }
        baseBitmap = replacement;
    }

    private void dirty() {
        if (listener != null) listener.onCanvasDirty();
    }

    private void releaseBitmaps() {
        livePoints.clear();
        livePath.reset();
        undo.clear();
        redo.clear();

        if (inkBitmap != null && !inkBitmap.isRecycled()) inkBitmap.recycle();
        if (baseBitmap != null && !baseBitmap.isRecycled() && baseBitmap != inkBitmap) {
            baseBitmap.recycle();
        }

        inkBitmap = null;
        baseBitmap = null;
        inkCanvas = null;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float sx = Math.max(1f, width - dp(20f)) / PAGE_WIDTH;
        float sy = Math.max(1f, height - dp(20f)) / PAGE_HEIGHT;
        baseScale = Math.min(sx, sy);
        updatePageRect();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updatePageRect();
        canvas.drawColor(0xFFE4E7EC);

        canvas.save();
        canvas.clipRect(pageRect);
        canvas.translate(pageRect.left, pageRect.top);
        canvas.scale(baseScale, baseScale);

        drawPaper(canvas);

        if (inkBitmap != null && !inkBitmap.isRecycled()) {
            canvas.drawBitmap(inkBitmap, 0f, 0f, bitmapPaint);
        }

        if (drawing && tool != TOOL_ERASER) {
            strokePaint.setColor(inkColor);
            strokePaint.setAlpha(tool == TOOL_HIGHLIGHTER ? 80 : 255);
            strokePaint.setStrokeWidth(tool == TOOL_HIGHLIGHTER ? penSize * 2.8f : penSize);
            strokePaint.setStrokeCap(
                    tool == TOOL_HIGHLIGHTER ? Paint.Cap.SQUARE : Paint.Cap.ROUND
            );
            canvas.drawPath(livePath, strokePaint);
        }

        canvas.restore();
        canvas.drawRect(pageRect, borderPaint);
        lastFrame = SystemClock.uptimeMillis();
    }

    private void drawPaper(Canvas canvas) {
        canvas.drawColor(0xFFFFFDF8);
        gridPaint.setStrokeWidth(1f);

        if (PAPER_RULED.equals(paperType)) {
            gridPaint.setColor(0xFFDCE2EA);
            for (int y = 68; y < PAGE_HEIGHT; y += 48) {
                canvas.drawLine(0, y, PAGE_WIDTH, y, gridPaint);
            }
            canvas.drawLine(84, 0, 84, PAGE_HEIGHT, marginPaint);
        } else if (PAPER_GRAPH.equals(paperType)) {
            gridPaint.setColor(0xFFE0E4EA);
            for (int x = 0; x <= PAGE_WIDTH; x += 32) {
                canvas.drawLine(x, 0, x, PAGE_HEIGHT, gridPaint);
            }
            for (int y = 0; y <= PAGE_HEIGHT; y += 32) {
                canvas.drawLine(0, y, PAGE_WIDTH, y, gridPaint);
            }
            gridPaint.setColor(0xFFC9CFD8);
            for (int x = 0; x <= PAGE_WIDTH; x += 160) {
                canvas.drawLine(x, 0, x, PAGE_HEIGHT, gridPaint);
            }
            for (int y = 0; y <= PAGE_HEIGHT; y += 160) {
                canvas.drawLine(0, y, PAGE_WIDTH, y, gridPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ignoredTouch = event.getPointerCount() != 1 || !isInsidePage(event.getX(), event.getY());
                if (ignoredTouch) return true;

                activePointerId = event.getPointerId(0);
                drawing = true;
                toPage(event.getX(0), event.getY(0), pagePoint);
                beginStroke(pagePoint[0], pagePoint[1]);
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                return true;

            case MotionEvent.ACTION_MOVE:
                if (ignoredTouch || activePointerId < 0 || inkBitmap == null) return true;

                int index = event.findPointerIndex(activePointerId);
                if (index < 0) return true;

                for (int h = 0; h < event.getHistorySize(); h++) {
                    toPage(
                            event.getHistoricalX(index, h),
                            event.getHistoricalY(index, h),
                            pagePoint
                    );
                    appendPoint(pagePoint[0], pagePoint[1]);
                }

                toPage(event.getX(index), event.getY(index), pagePoint);
                appendPoint(pagePoint[0], pagePoint[1]);

                if (SystemClock.uptimeMillis() - lastFrame >= 8L) {
                    postInvalidateOnAnimation();
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                if (!ignoredTouch && activePointerId == event.getPointerId(event.getActionIndex())) {
                    commitLiveStroke();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (ignoredTouch) {
                    resetTouchState();
                    return true;
                }

                int upIndex = event.findPointerIndex(activePointerId);
                if (upIndex >= 0) {
                    toPage(event.getX(upIndex), event.getY(upIndex), pagePoint);
                    appendPoint(pagePoint[0], pagePoint[1]);
                }
                commitLiveStroke();
                return true;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_OUTSIDE:
                cancelLiveStroke(true);
                return true;

            default:
                return true;
        }
    }

    private boolean isInsidePage(float x, float y) {
        updatePageRect();
        return pageRect.contains(x, y);
    }

    private void beginStroke(float x, float y) {
        livePoints.clear();
        livePath.reset();
        livePoints.add(new PointF(x, y));
        livePath.moveTo(x, y);
        lastX = x;
        lastY = y;
    }

    private void appendPoint(float x, float y) {
        float dx = x - lastX;
        float dy = y - lastY;

        if (Math.hypot(dx, dy) < MIN_POINT_DISTANCE) return;

        float smoothedX = lastX + dx * 0.90f;
        float smoothedY = lastY + dy * 0.90f;

        if (livePoints.size() >= MAX_POINTS_PER_STROKE) {
            PointF last = livePoints.get(livePoints.size() - 1);
            last.set(smoothedX, smoothedY);
            lastX = smoothedX;
            lastY = smoothedY;
            return;
        }

        livePoints.add(new PointF(smoothedX, smoothedY));

        if (livePoints.size() == 2) {
            livePath.lineTo(smoothedX, smoothedY);
        } else {
            PointF previous = livePoints.get(livePoints.size() - 2);
            float midX = (previous.x + smoothedX) * 0.5f;
            float midY = (previous.y + smoothedY) * 0.5f;
            livePath.quadTo(previous.x, previous.y, midX, midY);
            livePath.lineTo(smoothedX, smoothedY);
        }

        if (tool == TOOL_ERASER && inkCanvas != null) {
            eraserPaint.setStrokeWidth(Math.max(12f, penSize * 3f));
            inkCanvas.drawLine(lastX, lastY, smoothedX, smoothedY, eraserPaint);
        }

        lastX = smoothedX;
        lastY = smoothedY;
    }

    private void commitLiveStroke() {
        if (!drawing || livePoints.isEmpty()) {
            resetTouchState();
            postInvalidateOnAnimation();
            return;
        }

        ArrayList<PointF> points = new ArrayList<>(livePoints.size());
        for (PointF point : livePoints) {
            points.add(new PointF(point.x, point.y));
        }

        if (tool == TOOL_ERASER) {
            if (undo.size() >= MAX_UNDO_COMMANDS) undo.removeFirst();
            undo.addLast(new StrokeCommand(points, inkColor, penSize, TOOL_ERASER, false));
            redo.clear();
            dirty();
            invalidate();
        } else {
            execute(new StrokeCommand(points, inkColor, penSize, tool, false));
        }

        resetTouchState();
    }

    private void cancelLiveStroke(boolean rebuild) {
        resetTouchState();
        if (rebuild && inkBitmap != null && baseBitmap != null) {
            rebuild();
        }
        postInvalidateOnAnimation();
    }

    private void resetTouchState() {
        livePoints.clear();
        livePath.reset();
        drawing = false;
        activePointerId = -1;
        ignoredTouch = false;
    }

    private void toPage(float screenX, float screenY, float[] out) {
        updatePageRect();
        out[0] = clamp((screenX - pageRect.left) / baseScale, 0f, PAGE_WIDTH);
        out[1] = clamp((screenY - pageRect.top) / baseScale, 0f, PAGE_HEIGHT);
    }

    private void updatePageRect() {
        float width = PAGE_WIDTH * baseScale;
        float height = PAGE_HEIGHT * baseScale;
        float left = (getWidth() - width) * 0.5f;
        float top = (getHeight() - height) * 0.5f;
        pageRect.set(left, top, left + width, top + height);
    }

    private static void apply(StrokeCommand command, Canvas canvas) {
        if (command.clear) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            return;
        }

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        if (command.tool == TOOL_ERASER) {
            paint.setColor(Color.TRANSPARENT);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            paint.setStrokeWidth(Math.max(12f, command.width * 3f));
        } else {
            paint.setColor(command.color);
            paint.setAlpha(command.tool == TOOL_HIGHLIGHTER ? 80 : 255);
            paint.setStrokeWidth(
                    command.tool == TOOL_HIGHLIGHTER
                            ? command.width * 2.8f
                            : command.width
            );
            if (command.tool == TOOL_HIGHLIGHTER) {
                paint.setStrokeCap(Paint.Cap.SQUARE);
            }
        }

        Path path = new Path();
        if (command.points.size() == 1) {
            PointF point = command.points.get(0);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(
                    point.x,
                    point.y,
                    Math.max(1f, paint.getStrokeWidth() * 0.5f),
                    paint
            );
        } else {
            PointF first = command.points.get(0);
            path.moveTo(first.x, first.y);

            PointF previous = first;
            for (int i = 1; i < command.points.size(); i++) {
                PointF current = command.points.get(i);
                if (i == command.points.size() - 1) {
                    path.quadTo(previous.x, previous.y, current.x, current.y);
                } else {
                    PointF next = command.points.get(i + 1);
                    float midX = (current.x + next.x) * 0.5f;
                    float midY = (current.y + next.y) * 0.5f;
                    path.quadTo(current.x, current.y, midX, midY);
                }
                previous = current;
            }
            canvas.drawPath(path, paint);
        }

        paint.setXfermode(null);
    }

    private static String normalizePaper(String value) {
        if (PAPER_BLANK.equals(value) || PAPER_RULED.equals(value) || PAPER_GRAPH.equals(value)) {
            return value;
        }
        return PAPER_RULED;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class StrokeCommand {
        final ArrayList<PointF> points;
        final int color;
        final float width;
        final int tool;
        final boolean clear;

        StrokeCommand(ArrayList<PointF> points, int color, float width, int tool, boolean clear) {
            this.points = points;
            this.color = color;
            this.width = width;
            this.tool = tool;
            this.clear = clear;
        }

        static StrokeCommand clear() {
            return new StrokeCommand(
                    new ArrayList<>(),
                    Color.TRANSPARENT,
                    0f,
                    TOOL_PEN,
                    true
            );
        }
    }
}
