package com.maryvellous.puzzlegame;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class PuzzleView extends View {
    private static final int MODE_MENU = 0;
    private static final int MODE_PUZZLE = 1;

    private static final float WORLD_W = 1000f;
    private static final float WORLD_H = 2100f;
    private static final float BOARD_X = 50f;
    private static final float BOARD_Y = 120f;
    private static final float BOARD_SIZE = 900f;
    private static final float TRAY_X = 50f;
    private static final float TRAY_Y = 1120f;

    private static final int CREAM = Color.rgb(247, 232, 198);
    private static final int PAPER = Color.rgb(255, 249, 236);
    private static final int PLUM = Color.rgb(112, 55, 91);
    private static final int PURPLE = Color.rgb(91, 48, 84);
    private static final int BROWN = Color.rgb(82, 53, 47);
    private static final int GREEN = Color.rgb(79, 125, 91);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap[] images = new Bitmap[5];
    private final RectF[] puzzleRects = new RectF[5];
    private final RectF[] difficultyRects = new RectF[3];
    private final int[] difficultyPieces = new int[]{36, 64, 100};
    private final SharedPreferences prefs;
    private final ScaleGestureDetector scaleDetector;
    private final Random random = new Random();

    private final List<Piece> pieces = new ArrayList<>();
    private int mode = MODE_MENU;
    private int selectedDifficulty = 1;
    private int selectedPuzzle = 0;
    private int gridSize = 8;
    private int solvedCount = 0;
    private boolean complete = false;

    private float density;
    private float scaledDensity;
    private float headerH;
    private float baseScale = 1f;
    private float zoom = 1f;
    private float panX = 0f;
    private float panY = 0f;

    private Piece draggingPiece = null;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean panning = false;
    private float lastTouchX;
    private float lastTouchY;

    private final RectF backRect = new RectF();
    private final RectF zoomOutRect = new RectF();
    private final RectF zoomResetRect = new RectF();
    private final RectF zoomInRect = new RectF();
    private final RectF completionMenuRect = new RectF();
    private final RectF completionNextRect = new RectF();

    public PuzzleView(Context context) {
        super(context);
        setFocusable(true);
        density = getResources().getDisplayMetrics().density;
        scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        headerH = dp(88);
        prefs = context.getSharedPreferences("puzzle_progress", Context.MODE_PRIVATE);

        images[0] = BitmapFactory.decodeResource(getResources(), R.drawable.puzzle_1);
        images[1] = BitmapFactory.decodeResource(getResources(), R.drawable.puzzle_2);
        images[2] = BitmapFactory.decodeResource(getResources(), R.drawable.puzzle_3);
        images[3] = BitmapFactory.decodeResource(getResources(), R.drawable.puzzle_4);
        images[4] = BitmapFactory.decodeResource(getResources(), R.drawable.puzzle_5);

        for (int i = 0; i < puzzleRects.length; i++) puzzleRects[i] = new RectF();
        for (int i = 0; i < difficultyRects.length; i++) difficultyRects[i] = new RectF();

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (mode != MODE_PUZZLE || complete) return false;
                float old = zoom;
                float next = clamp(zoom * detector.getScaleFactor(), baseScale * 0.72f, baseScale * 4.5f);
                if (Math.abs(next - old) < 0.0001f) return true;
                float fx = detector.getFocusX();
                float fy = detector.getFocusY();
                float wx = (fx - panX) / old;
                float wy = (fy - panY) / old;
                zoom = next;
                panX = fx - wx * zoom;
                panY = fy - wy * zoom;
                clampPan();
                invalidate();
                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                cancelDrag();
                panning = false;
                return mode == MODE_PUZZLE && !complete;
            }
        });

        setBackgroundColor(CREAM);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mode == MODE_MENU) drawMenu(canvas);
        else drawPuzzle(canvas);
    }

    private void drawMenu(Canvas canvas) {
        canvas.drawColor(CREAM);
        int w = getWidth();
        int h = getHeight();

        paint.setColor(BROWN);
        paint.setTextSize(sp(30));
        paint.setFakeBoldText(true);
        drawCentered(canvas, "Rovina Puzzle", w / 2f, dp(48));

        paint.setFakeBoldText(false);
        paint.setTextSize(sp(14));
        paint.setColor(PURPLE);
        drawCentered(canvas, "Scegli quanti pezzi e poi un disegno", w / 2f, dp(76));

        float gap = dp(10);
        float buttonW = Math.min(dp(100), (w - dp(48) - gap * 2) / 3f);
        float total = buttonW * 3 + gap * 2;
        float startX = (w - total) / 2f;
        float y = dp(96);
        float bh = dp(46);
        for (int i = 0; i < 3; i++) {
            RectF r = difficultyRects[i];
            r.set(startX + i * (buttonW + gap), y, startX + i * (buttonW + gap) + buttonW, y + bh);
            paint.setColor(i == selectedDifficulty ? PLUM : PAPER);
            canvas.drawRoundRect(r, dp(16), dp(16), paint);
            paint.setTextSize(sp(15));
            paint.setFakeBoldText(true);
            paint.setColor(i == selectedDifficulty ? Color.WHITE : PLUM);
            drawCentered(canvas, difficultyPieces[i] + " pezzi", r.centerX(), r.centerY() + sp(5));
        }
        paint.setFakeBoldText(false);

        float top = dp(164);
        float bottomMargin = dp(20);
        float rowGap = dp(12);
        float colGap = dp(12);
        float side = dp(16);
        float colW = (w - side * 2 - colGap) / 2f;
        float availableH = h - top - bottomMargin;
        float rowH = (availableH - rowGap * 2) / 3f;
        float cardH = Math.max(dp(150), rowH);
        float imageSize = Math.min(colW, cardH - dp(34));
        float cardW = imageSize;

        for (int i = 0; i < images.length; i++) {
            int col = i % 2;
            int row = i / 2;
            float left = col == 0 ? side : w - side - cardW;
            float cardTop = top + row * (rowH + rowGap);
            RectF card = puzzleRects[i];
            card.set(left, cardTop, left + cardW, cardTop + imageSize + dp(30));

            paint.setColor(PAPER);
            canvas.drawRoundRect(card, dp(16), dp(16), paint);

            RectF imageRect = new RectF(card.left, card.top, card.right, card.top + imageSize);
            canvas.save();
            Path clip = new Path();
            clip.addRoundRect(imageRect, dp(16), dp(16), Path.Direction.CW);
            canvas.clipPath(clip);
            canvas.drawBitmap(images[i], null, imageRect, paint);
            canvas.restore();

            paint.setColor(BROWN);
            paint.setTextSize(sp(13));
            paint.setFakeBoldText(true);
            canvas.drawText("Puzzle " + (i + 1), card.left + dp(10), card.bottom - dp(9), paint);
            paint.setFakeBoldText(false);

            if (prefs.getBoolean("done_" + i, false)) {
                float cx = card.right - dp(18);
                float cy = card.bottom - dp(15);
                paint.setColor(GREEN);
                canvas.drawCircle(cx, cy, dp(10), paint);
                paint.setColor(Color.WHITE);
                paint.setTextSize(sp(11));
                paint.setFakeBoldText(true);
                drawCentered(canvas, "✓", cx, cy + sp(4));
                paint.setFakeBoldText(false);
            }
        }
    }

    private void drawPuzzle(Canvas canvas) {
        canvas.drawColor(CREAM);

        canvas.save();
        canvas.translate(panX, panY);
        canvas.scale(zoom, zoom);

        paint.setColor(PAPER);
        RectF board = new RectF(BOARD_X, BOARD_Y, BOARD_X + BOARD_SIZE, BOARD_Y + BOARD_SIZE);
        canvas.drawRect(board, paint);

        paint.setAlpha(28);
        canvas.drawBitmap(images[selectedPuzzle], null, board, paint);
        paint.setAlpha(255);

        linePaint.setColor(Color.argb(80, 90, 60, 65));
        linePaint.setStrokeWidth(Math.max(1f, 1.2f / zoom));
        linePaint.setStyle(Paint.Style.STROKE);
        float tile = BOARD_SIZE / gridSize;
        for (int i = 0; i <= gridSize; i++) {
            float p = BOARD_X + i * tile;
            canvas.drawLine(p, BOARD_Y, p, BOARD_Y + BOARD_SIZE, linePaint);
            p = BOARD_Y + i * tile;
            canvas.drawLine(BOARD_X, p, BOARD_X + BOARD_SIZE, p, linePaint);
        }

        paint.setColor(Color.argb(45, 112, 55, 91));
        RectF tray = new RectF(TRAY_X - 18, TRAY_Y - 58, TRAY_X + BOARD_SIZE + 18, TRAY_Y + BOARD_SIZE + 18);
        canvas.drawRoundRect(tray, 22, 22, paint);
        paint.setColor(PURPLE);
        paint.setTextSize(28);
        paint.setFakeBoldText(true);
        canvas.drawText("PEZZI", TRAY_X, TRAY_Y - 22, paint);
        paint.setFakeBoldText(false);

        Piece active = draggingPiece;
        for (Piece piece : pieces) {
            if (piece == active) continue;
            drawPiece(canvas, piece, tile);
        }
        if (active != null) drawPiece(canvas, active, tile);

        canvas.restore();

        drawHeader(canvas);
        if (complete) drawCompletion(canvas);
    }

    private void drawPiece(Canvas canvas, Piece piece, float tile) {
        RectF dst = new RectF(piece.x, piece.y, piece.x + tile, piece.y + tile);
        if (!piece.locked) {
            paint.setColor(Color.argb(42, 40, 20, 25));
            canvas.drawRect(dst.left + 6, dst.top + 8, dst.right + 6, dst.bottom + 8, paint);
        }
        paint.setAlpha(piece.locked ? 255 : 248);
        canvas.drawBitmap(images[selectedPuzzle], piece.src, dst, paint);
        paint.setAlpha(255);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Math.max(1f, 1.3f / zoom));
        linePaint.setColor(piece.locked ? Color.argb(65, 70, 45, 50) : Color.argb(150, 70, 40, 55));
        canvas.drawRect(dst, linePaint);
    }

    private void drawHeader(Canvas canvas) {
        int w = getWidth();
        paint.setColor(Color.argb(247, 255, 249, 236));
        canvas.drawRect(0, 0, w, headerH, paint);
        paint.setColor(Color.argb(40, 80, 40, 50));
        canvas.drawRect(0, headerH - 1, w, headerH, paint);

        float btn = dp(48);
        backRect.set(dp(10), dp(14), dp(10) + btn, dp(14) + btn);
        paint.setColor(PLUM);
        canvas.drawRoundRect(backRect, dp(14), dp(14), paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(sp(24));
        paint.setFakeBoldText(true);
        drawCentered(canvas, "‹", backRect.centerX(), backRect.centerY() + sp(8));
        paint.setFakeBoldText(false);

        paint.setColor(BROWN);
        paint.setTextSize(sp(16));
        paint.setFakeBoldText(true);
        canvas.drawText("Puzzle " + (selectedPuzzle + 1), dp(72), dp(32), paint);
        paint.setFakeBoldText(false);
        paint.setTextSize(sp(12));
        paint.setColor(PURPLE);
        canvas.drawText((gridSize * gridSize) + " pezzi · " + solvedCount + "/" + (gridSize * gridSize), dp(72), dp(53), paint);
        paint.setTextSize(sp(10));
        canvas.drawText("Pizzica per zoomare · trascina lo sfondo per spostarti", dp(72), dp(70), paint);

        float right = w - dp(10);
        float controlH = dp(40);
        float smallW = dp(42);
        float resetW = dp(64);
        float top = dp(20);
        zoomInRect.set(right - smallW, top, right, top + controlH);
        zoomResetRect.set(zoomInRect.left - dp(6) - resetW, top, zoomInRect.left - dp(6), top + controlH);
        zoomOutRect.set(zoomResetRect.left - dp(6) - smallW, top, zoomResetRect.left - dp(6), top + controlH);

        drawZoomButton(canvas, zoomOutRect, "−", sp(22));
        drawZoomButton(canvas, zoomResetRect, String.format(Locale.ITALIAN, "%d%%", Math.round((zoom / baseScale) * 100)), sp(11));
        drawZoomButton(canvas, zoomInRect, "+", sp(20));
    }

    private void drawZoomButton(Canvas canvas, RectF r, String label, float size) {
        paint.setColor(PAPER);
        canvas.drawRoundRect(r, dp(12), dp(12), paint);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(1));
        linePaint.setColor(Color.argb(70, 112, 55, 91));
        canvas.drawRoundRect(r, dp(12), dp(12), linePaint);
        paint.setColor(PLUM);
        paint.setTextSize(size);
        paint.setFakeBoldText(true);
        drawCentered(canvas, label, r.centerX(), r.centerY() + size * 0.34f);
        paint.setFakeBoldText(false);
    }

    private void drawCompletion(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        paint.setColor(Color.argb(145, 45, 25, 40));
        canvas.drawRect(0, 0, w, h, paint);

        float cardW = Math.min(w - dp(40), dp(360));
        float cardH = dp(230);
        RectF card = new RectF((w - cardW) / 2f, (h - cardH) / 2f, (w + cardW) / 2f, (h + cardH) / 2f);
        paint.setColor(PAPER);
        canvas.drawRoundRect(card, dp(24), dp(24), paint);

        paint.setColor(BROWN);
        paint.setTextSize(sp(28));
        paint.setFakeBoldText(true);
        drawCentered(canvas, "Puzzle completato!", card.centerX(), card.top + dp(58));
        paint.setFakeBoldText(false);
        paint.setColor(PURPLE);
        paint.setTextSize(sp(14));
        drawCentered(canvas, (gridSize * gridSize) + " pezzi sistemati", card.centerX(), card.top + dp(90));

        float gap = dp(10);
        float bw = (cardW - dp(48) - gap) / 2f;
        float by = card.bottom - dp(70);
        completionMenuRect.set(card.left + dp(24), by, card.left + dp(24) + bw, by + dp(46));
        completionNextRect.set(completionMenuRect.right + gap, by, completionMenuRect.right + gap + bw, by + dp(46));

        paint.setColor(Color.rgb(231, 216, 192));
        canvas.drawRoundRect(completionMenuRect, dp(15), dp(15), paint);
        paint.setColor(PLUM);
        canvas.drawRoundRect(completionNextRect, dp(15), dp(15), paint);

        paint.setTextSize(sp(14));
        paint.setFakeBoldText(true);
        paint.setColor(BROWN);
        drawCentered(canvas, "Menu", completionMenuRect.centerX(), completionMenuRect.centerY() + sp(5));
        paint.setColor(Color.WHITE);
        drawCentered(canvas, selectedPuzzle < images.length - 1 ? "Prossimo" : "Ricomincia", completionNextRect.centerX(), completionNextRect.centerY() + sp(5));
        paint.setFakeBoldText(false);
    }

    private void startPuzzle(int puzzleIndex) {
        selectedPuzzle = puzzleIndex;
        gridSize = (int) Math.round(Math.sqrt(difficultyPieces[selectedDifficulty]));
        solvedCount = 0;
        complete = false;
        pieces.clear();

        Bitmap bitmap = images[selectedPuzzle];
        int srcW = bitmap.getWidth() / gridSize;
        int srcH = bitmap.getHeight() / gridSize;
        float tile = BOARD_SIZE / gridSize;

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < gridSize * gridSize; i++) slots.add(i);
        Collections.shuffle(slots, random);

        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {
                int index = row * gridSize + col;
                int slot = slots.get(index);
                int sr = slot / gridSize;
                int sc = slot % gridSize;

                int l = col * srcW;
                int t = row * srcH;
                int r = (col == gridSize - 1) ? bitmap.getWidth() : (col + 1) * srcW;
                int b = (row == gridSize - 1) ? bitmap.getHeight() : (row + 1) * srcH;

                Piece p = new Piece();
                p.src = new Rect(l, t, r, b);
                p.targetX = BOARD_X + col * tile;
                p.targetY = BOARD_Y + row * tile;
                p.homeX = TRAY_X + sc * tile;
                p.homeY = TRAY_Y + sr * tile;
                p.x = p.homeX;
                p.y = p.homeY;
                pieces.add(p);
            }
        }

        Collections.shuffle(pieces, random);
        mode = MODE_PUZZLE;
        post(new Runnable() {
            @Override
            public void run() {
                resetCamera();
                invalidate();
            }
        });
    }

    private void resetCamera() {
        if (getWidth() <= 0) return;
        baseScale = Math.max(0.1f, (getWidth() - dp(16)) / WORLD_W);
        zoom = baseScale;
        panX = (getWidth() - WORLD_W * zoom) / 2f;
        panY = headerH + dp(8) - BOARD_Y * zoom;
        clampPan();
    }

    private void setZoom(float factor) {
        float next = clamp(baseScale * factor, baseScale * 0.72f, baseScale * 4.5f);
        float fx = getWidth() / 2f;
        float fy = (getHeight() + headerH) / 2f;
        float wx = (fx - panX) / zoom;
        float wy = (fy - panY) / zoom;
        zoom = next;
        panX = fx - wx * zoom;
        panY = fy - wy * zoom;
        clampPan();
        invalidate();
    }

    private void nudgeZoom(float multiplier) {
        float old = zoom;
        float next = clamp(zoom * multiplier, baseScale * 0.72f, baseScale * 4.5f);
        float fx = getWidth() / 2f;
        float fy = (getHeight() + headerH) / 2f;
        float wx = (fx - panX) / old;
        float wy = (fy - panY) / old;
        zoom = next;
        panX = fx - wx * zoom;
        panY = fy - wy * zoom;
        clampPan();
        invalidate();
    }

    private void clampPan() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float margin = dp(80);
        float contentW = WORLD_W * zoom;
        float contentH = WORLD_H * zoom;

        if (contentW <= getWidth() - margin) {
            panX = (getWidth() - contentW) / 2f;
        } else {
            float minX = getWidth() - margin - contentW;
            float maxX = margin;
            panX = clamp(panX, minX, maxX);
        }

        float minY = getHeight() - margin - contentH;
        float maxY = headerH + margin;
        panY = clamp(panY, minY, maxY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mode == MODE_PUZZLE) scaleDetector.onTouchEvent(event);

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (mode == MODE_MENU) {
                    for (int i = 0; i < difficultyRects.length; i++) {
                        if (difficultyRects[i].contains(x, y)) {
                            selectedDifficulty = i;
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            invalidate();
                            return true;
                        }
                    }
                    for (int i = 0; i < puzzleRects.length; i++) {
                        if (puzzleRects[i].contains(x, y)) {
                            startPuzzle(i);
                            return true;
                        }
                    }
                    return true;
                }

                if (complete) {
                    if (completionMenuRect.contains(x, y)) {
                        mode = MODE_MENU;
                        invalidate();
                        return true;
                    }
                    if (completionNextRect.contains(x, y)) {
                        int next = selectedPuzzle < images.length - 1 ? selectedPuzzle + 1 : selectedPuzzle;
                        startPuzzle(next);
                        return true;
                    }
                    return true;
                }

                if (backRect.contains(x, y)) {
                    mode = MODE_MENU;
                    pieces.clear();
                    invalidate();
                    return true;
                }
                if (zoomOutRect.contains(x, y)) {
                    nudgeZoom(0.78f);
                    return true;
                }
                if (zoomResetRect.contains(x, y)) {
                    setZoom(1f);
                    return true;
                }
                if (zoomInRect.contains(x, y)) {
                    nudgeZoom(1.28f);
                    return true;
                }

                if (y < headerH) return true;
                float wx = (x - panX) / zoom;
                float wy = (y - panY) / zoom;
                float tile = BOARD_SIZE / gridSize;
                draggingPiece = findPieceAt(wx, wy, tile);
                if (draggingPiece != null) {
                    dragOffsetX = wx - draggingPiece.x;
                    dragOffsetY = wy - draggingPiece.y;
                    pieces.remove(draggingPiece);
                    pieces.add(draggingPiece);
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                } else {
                    panning = true;
                    lastTouchX = x;
                    lastTouchY = y;
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                cancelDrag();
                panning = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (scaleDetector.isInProgress()) return true;
                if (draggingPiece != null && event.getPointerCount() == 1) {
                    float mx = (x - panX) / zoom;
                    float my = (y - panY) / zoom;
                    draggingPiece.x = mx - dragOffsetX;
                    draggingPiece.y = my - dragOffsetY;
                    invalidate();
                } else if (panning && event.getPointerCount() == 1) {
                    panX += x - lastTouchX;
                    panY += y - lastTouchY;
                    lastTouchX = x;
                    lastTouchY = y;
                    clampPan();
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (draggingPiece != null) {
                    tryDrop(draggingPiece);
                    draggingPiece = null;
                    invalidate();
                }
                panning = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                cancelDrag();
                panning = false;
                invalidate();
                return true;
        }
        return true;
    }

    private Piece findPieceAt(float wx, float wy, float tile) {
        for (int i = pieces.size() - 1; i >= 0; i--) {
            Piece p = pieces.get(i);
            if (p.locked) continue;
            if (wx >= p.x && wx <= p.x + tile && wy >= p.y && wy <= p.y + tile) return p;
        }
        return null;
    }

    private void tryDrop(Piece p) {
        float tile = BOARD_SIZE / gridSize;
        float cx = p.x + tile / 2f;
        float cy = p.y + tile / 2f;
        float tx = p.targetX + tile / 2f;
        float ty = p.targetY + tile / 2f;
        float tolerance = tile * 0.46f;
        if (Math.abs(cx - tx) <= tolerance && Math.abs(cy - ty) <= tolerance) {
            p.x = p.targetX;
            p.y = p.targetY;
            p.locked = true;
            solvedCount++;
            performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            if (solvedCount == pieces.size()) {
                complete = true;
                prefs.edit().putBoolean("done_" + selectedPuzzle, true).apply();
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        } else {
            p.x = p.homeX;
            p.y = p.homeY;
        }
    }

    private void cancelDrag() {
        if (draggingPiece != null && !draggingPiece.locked) {
            draggingPiece.x = draggingPiece.homeX;
            draggingPiece.y = draggingPiece.homeY;
        }
        draggingPiece = null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mode == MODE_PUZZLE) resetCamera();
    }

    private void drawCentered(Canvas canvas, String text, float centerX, float baselineY) {
        canvas.drawText(text, centerX - paint.measureText(text) / 2f, baselineY, paint);
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * scaledDensity;
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static class Piece {
        Rect src;
        float x;
        float y;
        float homeX;
        float homeY;
        float targetX;
        float targetY;
        boolean locked;
    }
}
