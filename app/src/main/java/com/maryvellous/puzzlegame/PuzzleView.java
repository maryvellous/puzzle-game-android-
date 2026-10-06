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
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class PuzzleView extends View {
    private static final int MODE_MENU = 0;
    private static final int MODE_PUZZLE = 1;

    private static final int GRID = 10;
    private static final int TILE_COUNT = GRID * GRID;

    private static final float WORLD_W = 1000f;
    private static final float WORLD_H = 1160f;
    private static final float BOARD_X = 50f;
    private static final float BOARD_Y = 130f;
    private static final float BOARD_SIZE = 900f;

    private static final int CREAM = Color.rgb(247, 232, 198);
    private static final int PAPER = Color.rgb(255, 249, 236);
    private static final int PLUM = Color.rgb(112, 55, 91);
    private static final int PURPLE = Color.rgb(91, 48, 84);
    private static final int BROWN = Color.rgb(82, 53, 47);
    private static final int GREEN = Color.rgb(79, 125, 91);
    private static final int MUTED = Color.rgb(151, 122, 112);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap[] images = new Bitmap[5];
    private final RectF[] puzzleRects = new RectF[5];
    private final int[] board = new int[TILE_COUNT];

    private final SharedPreferences prefs;
    private final ScaleGestureDetector scaleDetector;
    private final Random random = new Random();

    private int mode = MODE_MENU;
    private int selectedPuzzle = 0;
    private int selectedSlot = -1;
    private int downSlot = -1;
    private int moves = 0;
    private boolean complete = false;
    private boolean previewHeld = false;
    private boolean panning = false;
    private boolean gestureMoved = false;
    private long completionStartedAt = 0L;

    private float density;
    private float scaledDensity;
    private float headerH;
    private float baseScale = 1f;
    private float zoom = 1f;
    private float panX = 0f;
    private float panY = 0f;
    private float lastTouchX;
    private float lastTouchY;
    private float downTouchX;
    private float downTouchY;
    private final float touchSlop;

    private final RectF backRect = new RectF();
    private final RectF previewRect = new RectF();
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
        headerH = dp(118);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        prefs = context.getSharedPreferences("puzzle_progress", Context.MODE_PRIVATE);

        images[0] = BitmapFactory.decodeResource(getResources(), R.drawable.puzzle_1);
        images[1] = BitmapFactory.decodeResource(getResources(), R.drawable.puzzle_2);
        images[2] = BitmapFactory.decodeResource(getResources(), R.drawable.puzzle_3);
        images[3] = BitmapFactory.decodeResource(getResources(), R.drawable.puzzle_4);
        images[4] = BitmapFactory.decodeResource(getResources(), R.drawable.puzzle_5);

        for (int i = 0; i < puzzleRects.length; i++) puzzleRects[i] = new RectF();

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (mode != MODE_PUZZLE || complete || previewHeld) return false;

                float oldZoom = zoom;
                float nextZoom = clamp(zoom * detector.getScaleFactor(), baseScale * 0.75f, baseScale * 5f);
                if (Math.abs(nextZoom - oldZoom) < 0.0001f) return true;

                float focusX = detector.getFocusX();
                float focusY = detector.getFocusY();
                float worldX = (focusX - panX) / oldZoom;
                float worldY = (focusY - panY) / oldZoom;

                zoom = nextZoom;
                panX = focusX - worldX * zoom;
                panY = focusY - worldY * zoom;
                clampPan();
                invalidate();
                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                gestureMoved = true;
                panning = false;
                downSlot = -1;
                return mode == MODE_PUZZLE && !complete && !previewHeld;
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
        drawCentered(canvas, "5 puzzle · 100 tessere ciascuno", w / 2f, dp(76));

        float top = dp(104);
        float bottomMargin = dp(18);
        float rowGap = dp(12);
        float colGap = dp(12);
        float side = dp(16);
        float colW = (w - side * 2 - colGap) / 2f;
        float availableH = h - top - bottomMargin;
        float rowH = (availableH - rowGap * 2) / 3f;
        float cardH = Math.max(dp(150), rowH);
        float imageSize = Math.min(colW, cardH - dp(38));
        float cardW = imageSize;

        for (int i = 0; i < images.length; i++) {
            int col = i % 2;
            int row = i / 2;
            float left = col == 0 ? side : w - side - cardW;
            float cardTop = top + row * (rowH + rowGap);

            RectF card = puzzleRects[i];
            card.set(left, cardTop, left + cardW, cardTop + imageSize + dp(34));

            paint.setColor(PAPER);
            canvas.drawRoundRect(card, dp(16), dp(16), paint);

            RectF imageRect = new RectF(card.left, card.top, card.right, card.top + imageSize);
            canvas.save();
            Path clip = new Path();
            clip.addRoundRect(imageRect, dp(16), dp(16), Path.Direction.CW);
            canvas.clipPath(clip);
            canvas.drawBitmap(images[i], null, imageRect, paint);
            canvas.restore();

            boolean saved = hasSavedPuzzle(i);
            boolean done = prefs.getBoolean("done_" + i, false);

            paint.setColor(BROWN);
            paint.setTextSize(sp(13));
            paint.setFakeBoldText(true);
            canvas.drawText(saved ? "Continua Puzzle " + (i + 1) : "Puzzle " + (i + 1),
                    card.left + dp(10), card.bottom - dp(12), paint);
            paint.setFakeBoldText(false);

            if (saved) {
                int savedMoves = prefs.getInt("save_moves_" + i, 0);
                paint.setColor(MUTED);
                paint.setTextSize(sp(9));
                canvas.drawText(savedMoves + (savedMoves == 1 ? " mossa salvata" : " mosse salvate"),
                        card.left + dp(10), card.bottom - dp(2), paint);
            }

            if (done) {
                float cx = card.right - dp(18);
                float cy = card.bottom - dp(17);
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

        RectF boardRect = new RectF(BOARD_X, BOARD_Y, BOARD_X + BOARD_SIZE, BOARD_Y + BOARD_SIZE);
        paint.setColor(PAPER);
        canvas.drawRect(boardRect, paint);

        if (complete || previewHeld) {
            canvas.drawBitmap(images[selectedPuzzle], null, boardRect, paint);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(Math.max(2f, 3f / zoom));
            linePaint.setColor(complete ? Color.argb(110, 82, 53, 47) : PLUM);
            canvas.drawRect(boardRect, linePaint);
        } else {
            drawShuffledBoard(canvas);
        }

        canvas.restore();

        if (!complete) drawHeader(canvas);
        else drawCompletion(canvas);
    }

    private void drawShuffledBoard(Canvas canvas) {
        Bitmap bitmap = images[selectedPuzzle];
        float tile = BOARD_SIZE / GRID;

        for (int slot = 0; slot < TILE_COUNT; slot++) {
            int piece = board[slot];
            int pieceRow = piece / GRID;
            int pieceCol = piece % GRID;
            int slotRow = slot / GRID;
            int slotCol = slot % GRID;

            int left = pieceCol * bitmap.getWidth() / GRID;
            int top = pieceRow * bitmap.getHeight() / GRID;
            int right = (pieceCol + 1) * bitmap.getWidth() / GRID;
            int bottom = (pieceRow + 1) * bitmap.getHeight() / GRID;

            Rect src = new Rect(left, top, right, bottom);
            RectF dst = new RectF(
                    BOARD_X + slotCol * tile,
                    BOARD_Y + slotRow * tile,
                    BOARD_X + (slotCol + 1) * tile,
                    BOARD_Y + (slotRow + 1) * tile
            );

            canvas.drawBitmap(bitmap, src, dst, paint);
        }

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Math.max(1f, 1.2f / zoom));
        linePaint.setColor(Color.argb(105, 72, 44, 56));

        for (int i = 0; i <= GRID; i++) {
            float x = BOARD_X + i * tile;
            float y = BOARD_Y + i * tile;
            canvas.drawLine(x, BOARD_Y, x, BOARD_Y + BOARD_SIZE, linePaint);
            canvas.drawLine(BOARD_X, y, BOARD_X + BOARD_SIZE, y, linePaint);
        }

        if (selectedSlot >= 0) {
            int row = selectedSlot / GRID;
            int col = selectedSlot % GRID;
            RectF selected = new RectF(
                    BOARD_X + col * tile,
                    BOARD_Y + row * tile,
                    BOARD_X + (col + 1) * tile,
                    BOARD_Y + (row + 1) * tile
            );

            paint.setColor(Color.argb(58, 255, 255, 255));
            canvas.drawRect(selected, paint);

            linePaint.setStrokeWidth(Math.max(4f, 6f / zoom));
            linePaint.setColor(PLUM);
            canvas.drawRect(selected, linePaint);
        }

        linePaint.setStrokeWidth(Math.max(2f, 3f / zoom));
        linePaint.setColor(Color.argb(180, 82, 53, 47));
        canvas.drawRect(new RectF(BOARD_X, BOARD_Y, BOARD_X + BOARD_SIZE, BOARD_Y + BOARD_SIZE), linePaint);
    }

    private void drawHeader(Canvas canvas) {
        int w = getWidth();

        paint.setColor(Color.argb(250, 255, 249, 236));
        canvas.drawRect(0, 0, w, headerH, paint);
        paint.setColor(Color.argb(45, 80, 40, 50));
        canvas.drawRect(0, headerH - 1, w, headerH, paint);

        backRect.set(dp(10), dp(10), dp(54), dp(54));
        paint.setColor(PLUM);
        canvas.drawRoundRect(backRect, dp(14), dp(14), paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(sp(24));
        paint.setFakeBoldText(true);
        drawCentered(canvas, "‹", backRect.centerX(), backRect.centerY() + sp(8));
        paint.setFakeBoldText(false);

        paint.setColor(BROWN);
        paint.setTextSize(sp(17));
        paint.setFakeBoldText(true);
        canvas.drawText("Puzzle " + (selectedPuzzle + 1), dp(66), dp(29), paint);
        paint.setFakeBoldText(false);

        previewRect.set(w - dp(96), dp(10), w - dp(10), dp(54));
        paint.setColor(previewHeld ? PLUM : PAPER);
        canvas.drawRoundRect(previewRect, dp(14), dp(14), paint);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(1));
        linePaint.setColor(Color.argb(75, 112, 55, 91));
        canvas.drawRoundRect(previewRect, dp(14), dp(14), linePaint);
        paint.setColor(previewHeld ? Color.WHITE : PLUM);
        paint.setTextSize(sp(11));
        paint.setFakeBoldText(true);
        drawCentered(canvas, "ANTEPRIMA", previewRect.centerX(), previewRect.centerY() + sp(4));
        paint.setFakeBoldText(false);

        paint.setTextSize(sp(12));
        paint.setColor(PURPLE);
        canvas.drawText("100 tessere · " + moves + (moves == 1 ? " mossa" : " mosse"), dp(16), dp(78), paint);

        float right = w - dp(10);
        float controlH = dp(34);
        float smallW = dp(36);
        float resetW = dp(56);
        float top = dp(59);

        zoomInRect.set(right - smallW, top, right, top + controlH);
        zoomResetRect.set(zoomInRect.left - dp(5) - resetW, top, zoomInRect.left - dp(5), top + controlH);
        zoomOutRect.set(zoomResetRect.left - dp(5) - smallW, top, zoomResetRect.left - dp(5), top + controlH);

        drawZoomButton(canvas, zoomOutRect, "−", sp(20));
        drawZoomButton(canvas, zoomResetRect,
                String.format(Locale.ITALIAN, "%d%%", Math.round((zoom / baseScale) * 100)), sp(10));
        drawZoomButton(canvas, zoomInRect, "+", sp(18));

        paint.setColor(PURPLE);
        paint.setTextSize(sp(10));
        canvas.drawText("Tocca due tessere per scambiarle · i progressi si salvano da soli", dp(16), dp(108), paint);
    }

    private void drawZoomButton(Canvas canvas, RectF r, String label, float size) {
        paint.setColor(PAPER);
        canvas.drawRoundRect(r, dp(11), dp(11), paint);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(1));
        linePaint.setColor(Color.argb(70, 112, 55, 91));
        canvas.drawRoundRect(r, dp(11), dp(11), linePaint);

        paint.setColor(PLUM);
        paint.setTextSize(size);
        paint.setFakeBoldText(true);
        drawCentered(canvas, label, r.centerX(), r.centerY() + size * 0.34f);
        paint.setFakeBoldText(false);
    }

    private void drawCompletion(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        float progress = Math.min(1f, (System.currentTimeMillis() - completionStartedAt) / 420f);
        float eased = 1f - (float) Math.pow(1f - progress, 3);

        paint.setColor(Color.argb((int) (92 * eased), 54, 34, 47));
        canvas.drawRect(0, 0, w, h, paint);

        float cardW = Math.min(w - dp(32), dp(390));
        float cardH = Math.min(dp(420), h - dp(56));
        float finalTop = (h - cardH) / 2f;
        float top = finalTop + dp(28) * (1f - eased);

        RectF card = new RectF(
                (w - cardW) / 2f,
                top,
                (w + cardW) / 2f,
                top + cardH
        );

        paint.setColor(Color.argb((int) (255 * eased), 255, 249, 236));
        canvas.drawRoundRect(card, dp(26), dp(26), paint);

        float imageSize = Math.min(cardW - dp(44), dp(230));
        RectF imageRect = new RectF(
                card.centerX() - imageSize / 2f,
                card.top + dp(26),
                card.centerX() + imageSize / 2f,
                card.top + dp(26) + imageSize
        );

        canvas.save();
        Path clip = new Path();
        clip.addRoundRect(imageRect, dp(18), dp(18), Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawBitmap(images[selectedPuzzle], null, imageRect, paint);
        canvas.restore();

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(1));
        linePaint.setColor(Color.argb((int) (55 * eased), 82, 53, 47));
        canvas.drawRoundRect(imageRect, dp(18), dp(18), linePaint);

        paint.setColor(PLUM);
        paint.setTextSize(sp(11));
        paint.setFakeBoldText(true);
        drawCentered(canvas, "COMPLETATO", card.centerX(), imageRect.bottom + dp(34));

        paint.setColor(BROWN);
        paint.setTextSize(sp(24));
        drawCentered(canvas, "Puzzle " + (selectedPuzzle + 1), card.centerX(), imageRect.bottom + dp(66));

        paint.setFakeBoldText(false);
        paint.setColor(MUTED);
        paint.setTextSize(sp(13));
        drawCentered(canvas, moves + (moves == 1 ? " mossa" : " mosse"),
                card.centerX(), imageRect.bottom + dp(91));

        float gap = dp(10);
        float sidePadding = dp(22);
        float bw = (cardW - sidePadding * 2 - gap) / 2f;
        float by = card.bottom - dp(66);

        completionMenuRect.set(card.left + sidePadding, by, card.left + sidePadding + bw, by + dp(44));
        completionNextRect.set(completionMenuRect.right + gap, by, completionMenuRect.right + gap + bw, by + dp(44));

        paint.setColor(Color.rgb(235, 221, 199));
        canvas.drawRoundRect(completionMenuRect, dp(14), dp(14), paint);
        paint.setColor(PLUM);
        canvas.drawRoundRect(completionNextRect, dp(14), dp(14), paint);

        paint.setTextSize(sp(13));
        paint.setFakeBoldText(true);
        paint.setColor(BROWN);
        drawCentered(canvas, "Menu", completionMenuRect.centerX(), completionMenuRect.centerY() + sp(5));
        paint.setColor(Color.WHITE);
        drawCentered(canvas, selectedPuzzle < images.length - 1 ? "Prossimo" : "Rigioca",
                completionNextRect.centerX(), completionNextRect.centerY() + sp(5));
        paint.setFakeBoldText(false);

        if (progress < 1f) postInvalidateOnAnimation();
    }

    private void startPuzzle(int puzzleIndex) {
        selectedPuzzle = puzzleIndex;
        selectedSlot = -1;
        downSlot = -1;
        complete = false;
        previewHeld = false;
        completionStartedAt = 0L;

        if (!loadSavedPuzzle(puzzleIndex)) {
            moves = 0;

            List<Integer> shuffled = new ArrayList<>();
            for (int i = 0; i < TILE_COUNT; i++) shuffled.add(i);

            do {
                Collections.shuffle(shuffled, random);
            } while (isSolved(shuffled));

            for (int i = 0; i < TILE_COUNT; i++) board[i] = shuffled.get(i);
            saveCurrentPuzzle();
        }

        mode = MODE_PUZZLE;
        post(new Runnable() {
            @Override
            public void run() {
                resetCamera();
                invalidate();
            }
        });
    }

    private boolean isSolved(List<Integer> values) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) != i) return false;
        }
        return true;
    }

    private boolean isBoardSolved() {
        for (int i = 0; i < TILE_COUNT; i++) {
            if (board[i] != i) return false;
        }
        return true;
    }

    private void handleTileTap(int slot) {
        if (slot < 0 || slot >= TILE_COUNT || complete) return;

        if (selectedSlot < 0) {
            selectedSlot = slot;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            invalidate();
            return;
        }

        if (selectedSlot == slot) {
            selectedSlot = -1;
            invalidate();
            return;
        }

        int tmp = board[selectedSlot];
        board[selectedSlot] = board[slot];
        board[slot] = tmp;

        selectedSlot = -1;
        moves++;
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);

        if (isBoardSolved()) {
            complete = true;
            completionStartedAt = System.currentTimeMillis();
            clearSavedPuzzle(selectedPuzzle);
            prefs.edit().putBoolean("done_" + selectedPuzzle, true).apply();
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } else {
            saveCurrentPuzzle();
        }

        invalidate();
    }

    private boolean hasSavedPuzzle(int puzzleIndex) {
        return prefs.getBoolean("save_active_" + puzzleIndex, false);
    }

    private void saveCurrentPuzzle() {
        if (complete || mode == MODE_MENU && completionStartedAt > 0L) return;

        StringBuilder builder = new StringBuilder(TILE_COUNT * 3);
        for (int i = 0; i < TILE_COUNT; i++) {
            if (i > 0) builder.append(',');
            builder.append(board[i]);
        }

        prefs.edit()
                .putBoolean("save_active_" + selectedPuzzle, true)
                .putString("save_board_" + selectedPuzzle, builder.toString())
                .putInt("save_moves_" + selectedPuzzle, moves)
                .apply();
    }

    private boolean loadSavedPuzzle(int puzzleIndex) {
        if (!hasSavedPuzzle(puzzleIndex)) return false;

        String saved = prefs.getString("save_board_" + puzzleIndex, null);
        if (saved == null) {
            clearSavedPuzzle(puzzleIndex);
            return false;
        }

        String[] parts = saved.split(",");
        if (parts.length != TILE_COUNT) {
            clearSavedPuzzle(puzzleIndex);
            return false;
        }

        boolean[] seen = new boolean[TILE_COUNT];
        try {
            for (int i = 0; i < TILE_COUNT; i++) {
                int value = Integer.parseInt(parts[i]);
                if (value < 0 || value >= TILE_COUNT || seen[value]) {
                    clearSavedPuzzle(puzzleIndex);
                    return false;
                }
                seen[value] = true;
                board[i] = value;
            }
        } catch (NumberFormatException e) {
            clearSavedPuzzle(puzzleIndex);
            return false;
        }

        moves = Math.max(0, prefs.getInt("save_moves_" + puzzleIndex, 0));

        if (isBoardSolved()) {
            clearSavedPuzzle(puzzleIndex);
            return false;
        }

        return true;
    }

    private void clearSavedPuzzle(int puzzleIndex) {
        prefs.edit()
                .remove("save_active_" + puzzleIndex)
                .remove("save_board_" + puzzleIndex)
                .remove("save_moves_" + puzzleIndex)
                .apply();
    }

    private int slotAtWorld(float wx, float wy) {
        if (wx < BOARD_X || wy < BOARD_Y || wx >= BOARD_X + BOARD_SIZE || wy >= BOARD_Y + BOARD_SIZE) {
            return -1;
        }

        float tile = BOARD_SIZE / GRID;
        int col = (int) ((wx - BOARD_X) / tile);
        int row = (int) ((wy - BOARD_Y) / tile);

        if (col < 0 || col >= GRID || row < 0 || row >= GRID) return -1;
        return row * GRID + col;
    }

    private void resetCamera() {
        if (getWidth() <= 0 || getHeight() <= 0) return;

        float widthScale = (getWidth() - dp(16)) / WORLD_W;
        float heightScale = (getHeight() - headerH - dp(16)) / WORLD_H;
        baseScale = Math.max(0.1f, Math.min(widthScale, heightScale));

        zoom = baseScale;
        panX = (getWidth() - WORLD_W * zoom) / 2f;
        panY = headerH + dp(8) - BOARD_Y * zoom;
        clampPan();
    }

    private void setZoom(float factor) {
        float oldZoom = zoom;
        float nextZoom = clamp(baseScale * factor, baseScale * 0.75f, baseScale * 5f);
        float focusX = getWidth() / 2f;
        float focusY = (getHeight() + headerH) / 2f;

        float worldX = (focusX - panX) / oldZoom;
        float worldY = (focusY - panY) / oldZoom;

        zoom = nextZoom;
        panX = focusX - worldX * zoom;
        panY = focusY - worldY * zoom;
        clampPan();
        invalidate();
    }

    private void nudgeZoom(float multiplier) {
        float oldZoom = zoom;
        float nextZoom = clamp(zoom * multiplier, baseScale * 0.75f, baseScale * 5f);
        float focusX = getWidth() / 2f;
        float focusY = (getHeight() + headerH) / 2f;

        float worldX = (focusX - panX) / oldZoom;
        float worldY = (focusY - panY) / oldZoom;

        zoom = nextZoom;
        panX = focusX - worldX * zoom;
        panY = focusY - worldY * zoom;
        clampPan();
        invalidate();
    }

    private void clampPan() {
        if (getWidth() <= 0 || getHeight() <= 0) return;

        float margin = dp(70);
        float contentW = WORLD_W * zoom;
        float contentH = WORLD_H * zoom;

        if (contentW <= getWidth() - margin) {
            panX = (getWidth() - contentW) / 2f;
        } else {
            float minX = getWidth() - margin - contentW;
            float maxX = margin;
            panX = clamp(panX, minX, maxX);
        }

        float usableTop = headerH;
        float usableH = getHeight() - usableTop;

        if (contentH <= usableH - margin) {
            panY = usableTop + (usableH - contentH) / 2f;
        } else {
            float minY = getHeight() - margin - contentH;
            float maxY = usableTop + margin;
            panY = clamp(panY, minY, maxY);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mode == MODE_PUZZLE && !previewHeld) scaleDetector.onTouchEvent(event);

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (mode == MODE_MENU) {
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
                        startFreshPuzzle(next);
                        return true;
                    }
                    return true;
                }

                if (backRect.contains(x, y)) {
                    saveCurrentPuzzle();
                    mode = MODE_MENU;
                    selectedSlot = -1;
                    invalidate();
                    return true;
                }

                if (previewRect.contains(x, y)) {
                    previewHeld = true;
                    invalidate();
                    return true;
                }

                if (zoomOutRect.contains(x, y)) {
                    nudgeZoom(0.8f);
                    return true;
                }

                if (zoomResetRect.contains(x, y)) {
                    setZoom(1f);
                    return true;
                }

                if (zoomInRect.contains(x, y)) {
                    nudgeZoom(1.25f);
                    return true;
                }

                if (y < headerH) return true;

                downTouchX = x;
                downTouchY = y;
                lastTouchX = x;
                lastTouchY = y;
                gestureMoved = false;
                panning = false;

                float wx = (x - panX) / zoom;
                float wy = (y - panY) / zoom;
                downSlot = slotAtWorld(wx, wy);
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                gestureMoved = true;
                panning = false;
                downSlot = -1;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (previewHeld) return true;
                if (scaleDetector.isInProgress()) {
                    gestureMoved = true;
                    return true;
                }

                if (event.getPointerCount() == 1) {
                    if (!panning) {
                        float dxTotal = x - downTouchX;
                        float dyTotal = y - downTouchY;
                        if (dxTotal * dxTotal + dyTotal * dyTotal > touchSlop * touchSlop) {
                            panning = true;
                            gestureMoved = true;
                            downSlot = -1;
                        }
                    }

                    if (panning) {
                        panX += x - lastTouchX;
                        panY += y - lastTouchY;
                        clampPan();
                        invalidate();
                    }

                    lastTouchX = x;
                    lastTouchY = y;
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (previewHeld) {
                    previewHeld = false;
                    invalidate();
                    return true;
                }

                if (!gestureMoved && !panning && downSlot >= 0) {
                    handleTileTap(downSlot);
                }

                panning = false;
                downSlot = -1;
                return true;

            case MotionEvent.ACTION_CANCEL:
                previewHeld = false;
                panning = false;
                downSlot = -1;
                invalidate();
                return true;
        }

        return true;
    }

    private void startFreshPuzzle(int puzzleIndex) {
        clearSavedPuzzle(puzzleIndex);
        selectedPuzzle = puzzleIndex;
        selectedSlot = -1;
        downSlot = -1;
        moves = 0;
        complete = false;
        previewHeld = false;
        completionStartedAt = 0L;

        List<Integer> shuffled = new ArrayList<>();
        for (int i = 0; i < TILE_COUNT; i++) shuffled.add(i);

        do {
            Collections.shuffle(shuffled, random);
        } while (isSolved(shuffled));

        for (int i = 0; i < TILE_COUNT; i++) board[i] = shuffled.get(i);
        saveCurrentPuzzle();

        mode = MODE_PUZZLE;
        post(new Runnable() {
            @Override
            public void run() {
                resetCamera();
                invalidate();
            }
        });
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

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
