package com.maryvellous.lavablobs;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

/**
 * Portrait, resolution-independent Android Canvas game.
 * All artwork is procedural: no sprites, engines, trackers or network access.
 */
public final class LavaView extends View {
    private static final float W = BlobWorld.WIDTH;
    private static final int BACKGROUND = Color.rgb(10, 9, 31);
    // Deliberately varied colours, not nine nearly identical dark purples.
    // The palette stays between pale blue, indigo, lavender and violet.
    private static final int[] PURPLES = {
            0xFF4347C7, // indaco brillante
            0xFF9DCEFF, // azzurro chiaro
            0xFF5A35C7, // indaco profondo
            0xFFCAAFFF, // lavanda
            0xFF7831D5, // viola intenso
            0xFF728DFA, // pervinca azzurra
            0xFF512AA8, // viola scuro
            0xFFB581F0, // lilla
            0xFF9250EB  // ametista
    };
    private final BlobWorld world = new BlobWorld(new Random());
    private final Random random = new Random();
    private final SharedPreferences prefs;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letters = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path blobPath = new Path();
    private final Matrix shaderMatrix = new Matrix();
    private final float[] xx = new float[Blob.NODES];
    private final float[] yy = new float[Blob.NODES];
    private final RadialGradient[] blobShaders = new RadialGradient[PURPLES.length];
    private final ArrayList<Wave> waves = new ArrayList<>();
    private final ArrayList<Droplet> droplets = new ArrayList<>();

    private float aimX = W / 2;
    private long previousFrame;
    private boolean aiming;
    private float blockedFlash;
    private int best;

    private static final class Wave {
        float x, y, r, age;
        int tier;
        Wave(float x, float y, float r, int tier) {
            this.x = x; this.y = y; this.r = r; this.tier = tier;
        }
    }

    private static final class Droplet {
        float x, y, vx, vy, age, radius;
        int tier;
        Droplet(float x, float y, float vx, float vy, float radius, int tier) {
            this.x=x; this.y=y; this.vx=vx; this.vy=vy;
            this.radius=radius; this.tier=tier;
        }
    }

    public LavaView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setHapticFeedbackEnabled(true);
        prefs = context.getSharedPreferences("lava_blobs", Context.MODE_PRIVATE);
        best = prefs.getInt("best_score", 0);
        letters.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        letters.setTextAlign(Paint.Align.CENTER);
        line.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < PURPLES.length; i++) {
            float r = Blob.RADII[i];
            int soft = blend(PURPLES[i], 0xFFF4EBFF, .32f);
            int dark = blend(PURPLES[i], 0xFF151039, .28f);
            blobShaders[i] = new RadialGradient(
                    -r * .35f, -r * .40f, r * 1.75f,
                    new int[] {soft, PURPLES[i], dark},
                    new float[] {0f, .50f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    /** Called by the activity's sensor listener on the UI thread. */
    void shake(float sensorX, float sensorY, float sensorZ) {
        world.shake(sensorX, sensorY, sensorZ);
        invalidate();
    }

    private float scale() {
        return Math.min(getWidth() / W, getHeight() / 620f);
    }

    private float offsetX() {
        return (getWidth() - W * scale()) * .5f;
    }

    @Override protected void onDraw(Canvas raw) {
        super.onDraw(raw);
        raw.drawColor(BACKGROUND);
        if (getWidth() == 0 || getHeight() == 0) return;
        float scale = scale();
        float h = getHeight() / scale;
        world.setHeight(h);
        long now = System.nanoTime();
        float dt = previousFrame == 0 ? 0f : Math.min(.042f, (now - previousFrame) / 1_000_000_000f);
        previousFrame = now;
        world.tick(dt);
        advanceEffects(dt);
        for (BlobWorld.Merge merge : world.consumeMerges()) {
            waves.add(new Wave(merge.x, merge.y, merge.radius, merge.tier));
            for (int i = 0; i < 16; i++) {
                float angle = (float) (random.nextFloat() * Math.PI * 2);
                float velocity = 32f + random.nextFloat() * 115f;
                droplets.add(new Droplet(merge.x, merge.y,
                        (float) Math.cos(angle) * velocity,
                        (float) Math.sin(angle) * velocity - 28f,
                        1.8f + random.nextFloat() * 3.6f, merge.tier));
            }
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        if (world.score > best) {
            best = world.score;
            prefs.edit().putInt("best_score", best).apply();
        }
        Canvas canvas = raw;
        canvas.save();
        canvas.translate(offsetX(), 0f);
        canvas.scale(scale, scale);

        drawBackground(canvas, h);
        drawVessel(canvas);
        drawFloatingSpecks(canvas);
        drawWaves(canvas);
        for (Blob b : world.blobs) drawBlob(canvas, b);
        drawDroplets(canvas);
        drawVesselRim(canvas);
        drawHUD(canvas, h);
        if (world.gameOver) drawGameOver(canvas, h);

        canvas.restore();
        postInvalidateOnAnimation();
    }

    private void drawBackground(Canvas canvas, float h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0f, 0f, 0f, h,
                0xFF101035, 0xFF080819, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, W, h, paint);
        paint.setShader(null);
        for (int i = 0; i < 20; i++) {
            float x = (i * 101 + 55) % 400 + 10;
            float y = (i * 163 + 89) % (int) h;
            paint.setColor(0x385A52A4);
            canvas.drawCircle(x, y, (i % 3 + 1) * .60f, paint);
        }
    }

    private void drawVessel(Canvas canvas) {
        float top = BlobWorld.TOP - 21f;
        float bottom = world.bottom() + 18f;
        RectF bounds = new RectF(BlobWorld.LEFT - 13f, top,
                BlobWorld.RIGHT + 13f, bottom);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(45, top, 355, bottom,
                new int[] {0xFF19133D, 0xFF231744, 0xFF151133},
                new float[] {0, .55f, 1}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(bounds, 52f, 52f, paint);
        paint.setShader(null);
        paint.setColor(0x222E2774);
        RectF inner = new RectF(BlobWorld.LEFT, BlobWorld.TOP,
                BlobWorld.RIGHT, world.bottom());
        canvas.drawRoundRect(inner, 30f, 30f, paint);
        // The subtle diagonal reflection makes the vessel read as glass.
        paint.setColor(0x295F50B4);
        canvas.drawRoundRect(new RectF(34, top+45, 41, bottom-35), 3.5f, 3.5f, paint);
    }

    private void drawFloatingSpecks(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 24; i++) {
            float x = 55 + (i * 79 % 305);
            float range = world.bottom() - BlobWorld.TOP - 25;
            float y = world.bottom() - ((i * 63f + world.time * (8 + i % 5 * 4)) % range);
            paint.setColor(0x535D54BD);
            c.drawCircle(x + (float)Math.sin(world.time + i) * 2f, y, (i % 3 + 1) * .65f, paint);
        }
    }

    private void drawBlob(Canvas canvas, Blob b) {
        float r = b.radius();
        float currentTime = world.time;
        for (int i = 0; i < Blob.NODES; i++) {
            float theta = (float) (i * Math.PI * 2 / Blob.NODES);
            float radial = b.surfaceRadius(i, currentTime);
            xx[i] = b.x + (float) Math.cos(theta) * radial;
            yy[i] = b.y + (float) Math.sin(theta) * radial;
        }
        // Catmull-Rom to cubic Bézier: the drawn contour passes through
        // exactly the 20 nodes whose radial positions drive physical contacts.
        blobPath.reset();
        blobPath.moveTo(xx[0], yy[0]);
        for (int i = 0; i < Blob.NODES; i++) {
            int prev = (i + Blob.NODES - 1) % Blob.NODES;
            int next = (i + 1) % Blob.NODES;
            int after = (i + 2) % Blob.NODES;
            blobPath.cubicTo(
                    xx[i] + (xx[next] - xx[prev]) / 6f,
                    yy[i] + (yy[next] - yy[prev]) / 6f,
                    xx[next] - (xx[after] - xx[i]) / 6f,
                    yy[next] - (yy[after] - yy[i]) / 6f,
                    xx[next], yy[next]);
        }
        blobPath.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        // Removed the old translucent enclosing circle: its larger silhouette
        // looked like a second rigid bubble with a fake jelly shape inside.
        Shader shader = blobShaders[b.tier];
        shaderMatrix.reset();
        shaderMatrix.setTranslate(b.x, b.y);
        shader.setLocalMatrix(shaderMatrix);
        paint.setShader(shader);
        canvas.drawPath(blobPath, paint);
        paint.setShader(null);

        canvas.save();
        canvas.clipPath(blobPath);
        paint.setColor(0x50E1D4FF);
        canvas.drawOval(new RectF(b.x-r*.62f, b.y-r*.74f,
                b.x+r*.14f, b.y-r*.32f), paint);
        paint.setColor(0x3EB6A0FF);
        canvas.drawOval(new RectF(b.x+r*.23f, b.y+r*.27f,
                b.x+r*.69f, b.y+r*.55f), paint);
        canvas.restore();

        line.setColor(0x99DCCBFF);
        line.setStrokeWidth(Math.max(1.1f, r * .027f));
        canvas.drawPath(blobPath, line);
    }

    private void drawWaves(Canvas c) {
        for (Wave w : waves) {
            float progress = w.age / .68f;
            line.setColor(withAlpha(PURPLES[w.tier], (int) (155 * (1 - progress))));
            line.setStrokeWidth(2.8f * (1-progress) + .5f);
            c.drawCircle(w.x, w.y, w.r * (.55f + progress * 1.22f), line);
        }
    }

    private void drawDroplets(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        for (Droplet d : droplets) {
            paint.setColor(withAlpha(PURPLES[d.tier], (int) (220 * (1f - d.age / .70f))));
            c.drawCircle(d.x, d.y, d.radius * (1f - d.age / .70f), paint);
        }
    }

    private void advanceEffects(float dt) {
        blockedFlash = Math.max(0f, blockedFlash - dt);
        for (int i = waves.size()-1; i>=0; i--) {
            Wave w = waves.get(i);
            w.age += dt;
            if (w.age > .68f) waves.remove(i);
        }
        for (int i = droplets.size()-1; i>=0; i--) {
            Droplet d = droplets.get(i);
            d.age += dt;
            d.x += d.vx * dt;
            d.y += d.vy * dt;
            d.vy -= 40f * dt;
            if (d.age >= .70f) droplets.remove(i);
        }
    }

    private void drawVesselRim(Canvas c) {
        RectF edge = new RectF(BlobWorld.LEFT-13, BlobWorld.TOP-21f,
                BlobWorld.RIGHT+13, world.bottom()+18f);
        line.setColor(0x866A59AF);
        line.setStrokeWidth(3f);
        c.drawRoundRect(edge, 52, 52, line);
        line.setColor(0x4CBAA1FF);
        line.setStrokeWidth(1f);
        c.drawRoundRect(new RectF(BlobWorld.LEFT-9, BlobWorld.TOP-17,
                BlobWorld.RIGHT+9, world.bottom()+14), 49, 49, line);
        // Glass neck and base, purely decorative.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF34245C);
        c.drawRoundRect(new RectF(131, BlobWorld.TOP-34, 289, BlobWorld.TOP-19), 7, 7, paint);
        c.drawRoundRect(new RectF(100, world.bottom()+16, 320, world.bottom()+27), 5, 5, paint);
    }

    private void drawHUD(Canvas c, float h) {
        label(c, "LAVA BLOBS", 136, 53, 28, 0xFFE8DCFF, true);
        label(c, "le bolle vanno su", 136, 75, 12, 0xFF9787C2, false);
        label(c, "PUNTI", 76, 107, 10, 0xFF9787C2, true);
        label(c, String.valueOf(world.score), 76, 127, 20, 0xFFE8DCFF, true);
        label(c, "RECORD", 214, 107, 10, 0xFF9787C2, true);
        label(c, String.valueOf(best), 214, 127, 20, 0xFFE8DCFF, true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF2B204C);
        c.drawCircle(365, 73, 26, paint);
        label(c, "↻", 365, 83, 32, 0xFFE8DCFF, false);

        float launcher = world.bottom()+74;
        if (aiming) {
            line.setColor(0x668777DD);
            line.setStrokeWidth(1.7f);
            line.setPathEffect(new android.graphics.DashPathEffect(new float[] {8,11}, 0f));
            c.drawLine(aimX, world.bottom()-12, aimX, BlobWorld.TOP+15, line);
            line.setPathEffect(null);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x443D2D75);
        c.drawRoundRect(new RectF(42, world.bottom()+34, 378, h-20), 28, 28, paint);
        drawNextBubble(c, aimX, launcher);
        label(c, "TRASCINA · RILASCIA · SCUOTI", 210, h-23, 11, 0xFFC9B9ED, true);
        if (blockedFlash > 0f) {
            label(c, "SPOSTATI: QUI NON C'È SPAZIO",
                    210, world.bottom()-19, 12,
                    withAlpha(0xFFD4B6FF, (int)(255 * Math.min(1,blockedFlash * 3))), true);
        } else if (world.crowdedTime > .25f) {
            label(c, "LA LAMPADA SI STA RIEMPIENDO",
                    210, world.bottom()-19, 11, 0xFFD4B6FF, true);
        }
    }

    private void drawNextBubble(Canvas c, float x, float y) {
        float r = Blob.RADII[world.nextTier];
        paint.setColor(withAlpha(PURPLES[world.nextTier], 65));
        c.drawCircle(x, y, r * .88f + 6f, paint);
        paint.setColor(PURPLES[world.nextTier]);
        c.drawCircle(x, y, r * .88f, paint);
        paint.setColor(0xA0DED0FF);
        c.drawCircle(x-r*.25f, y-r*.25f, r*.17f, paint);
        line.setColor(0xAACDBEFF);
        line.setStrokeWidth(1.5f);
        c.drawCircle(x, y, r * .88f, line);
    }

    private void drawGameOver(Canvas c, float h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xD90B0822);
        c.drawRoundRect(new RectF(50,h*.36f,370,h*.66f),35,35,paint);
        label(c,"LAMPADA PIENA",210,h*.46f,26,0xFFE3D5FF,true);
        label(c,"Hai fatto " + world.score + " punti",210,h*.51f,16,0xFFC6B5EB,false);
        label(c,"TOCCA PER RICOMINCIARE",210,h*.59f,12,0xFFE3D5FF,true);
    }

    private void label(Canvas c, String value, float x, float y, float size,
                       int color, boolean bold) {
        letters.setShader(null);
        letters.setColor(color);
        letters.setTextSize(size);
        letters.setTypeface(Typeface.create(
                bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        c.drawText(value, x, y, letters);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255,alpha)) << 24);
    }

    private static int blend(int a, int b, float fraction) {
        float t = Math.max(0f, Math.min(1f, fraction));
        int red = (int) (Color.red(a) * (1-t) + Color.red(b) * t);
        int green = (int) (Color.green(a) * (1-t) + Color.green(b) * t);
        int blue = (int) (Color.blue(a) * (1-t) + Color.blue(b) * t);
        return Color.rgb(red, green, blue);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (getWidth() == 0 || getHeight() == 0) return false;
        float x = (event.getX() - offsetX()) / scale();
        float y = event.getY() / scale();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (world.gameOver) {
                    restart();
                    return true;
                }
                if (x > 332 && y < 115) {
                    restart();
                    return true;
                }
                aiming = true;
                aimX = BlobWorld.clamp(x, 52, 368);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (aiming) aimX = BlobWorld.clamp(x, 52, 368);
                return true;
            case MotionEvent.ACTION_UP:
                if (aiming) {
                    aimX = BlobWorld.clamp(x, 52, 368);
                    if (world.launch(aimX)) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    } else {
                        blockedFlash = .75f;
                    }
                }
                aiming = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                aiming = false;
                return true;
            default:
                return true;
        }
    }

    private void restart() {
        world.reset();
        waves.clear();
        droplets.clear();
        blockedFlash = 0f;
        aiming = false;
        aimX = W / 2;
        previousFrame = 0;
        invalidate();
    }
}
