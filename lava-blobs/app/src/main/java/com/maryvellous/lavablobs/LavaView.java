package com.maryvellous.lavablobs;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
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
    // Solid, saturated, intentionally distinguishable hues from blue-indigo
    // through lavender and violet, never red/yellow/green.
    private final GameSpec spec;
    private final ProgressStore progress;
    private final Runnable onHome;
    private final BlobWorld world;
    private final Random random = new Random();
    private final SharedPreferences prefs;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letters = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path blobPath = new Path();
    private final float[] xx = new float[Blob.NODES];
    private final float[] yy = new float[Blob.NODES];
    private final ArrayList<Wave> waves = new ArrayList<>();
    private final ArrayList<Droplet> droplets = new ArrayList<>();

    private float aimX = W / 2;
    private long previousFrame;
    private boolean aiming;
    private float blockedFlash;
    private long best;
    private boolean eraseMode;
    private boolean finalized;

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

    LavaView(Context context, GameSpec spec, ProgressStore progress, Runnable onHome) {
        super(context);
        this.spec=spec;this.progress=progress;this.onHome=onHome;
        world=new BlobWorld(new Random(),spec);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setHapticFeedbackEnabled(true);
        prefs=context.getSharedPreferences("lava_blobs",Context.MODE_PRIVATE);
        best=progress.record(spec.id);
        letters.setTextAlign(Paint.Align.CENTER);
        line.setStyle(Paint.Style.STROKE);
    }

    void leave() {
        if(!finalized&&(world.dropCount>0||world.mergeCount>0)){
            progress.save(spec,world,false);
            finalized=true;
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
        if (world.score > best) best=world.score;
        if(world.gameOver&&!finalized) {
            progress.save(spec,world,true);
            finalized=true;
        }
        Canvas canvas = raw;
        canvas.save();
        canvas.translate(offsetX(), 0f);
        canvas.scale(scale, scale);

        drawBackground(canvas, h);
        drawVessel(canvas);
        canvas.save();
        Path glass=glassPath();
        canvas.clipPath(glass);
        drawFloatingSpecks(canvas);
        drawWaves(canvas);
        drawAdhesions(canvas);
        for(Blob b:world.blobs)drawBlob(canvas,b);
        drawDroplets(canvas);
        canvas.restore();
        drawVesselRim(canvas);
        drawHUD(canvas,h);
        if(world.gameOver)drawGameOver(canvas,h);

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

    private Path glassPath() {
        Path shape=new Path();
        for(int i=0;i<world.vessel.xs.length;i++){
            float x=world.vessel.xs[i],y=world.vessel.ys[i];
            if(i==0)shape.moveTo(x,y);else shape.lineTo(x,y);
        }
        shape.close();return shape;
    }

    private void drawVessel(Canvas canvas) {
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF1B2B37);
        canvas.drawPath(glassPath(),paint);
    }

    private void drawFloatingSpecks(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 24; i++) {
            float x = 55 + (i * 79 % 305);
            float range = world.bottom() - world.vessel.top - 25;
            float y = world.bottom() - ((i * 63f + world.time * (8 + i % 5 * 4)) % Math.max(1,range));
            paint.setColor(0x535D54BD);
            c.drawCircle(x + (float)Math.sin(world.time + i) * 2f, y, (i % 3 + 1) * .65f, paint);
        }
    }

    private void drawBlob(Canvas canvas, Blob b) {
        float r = b.radius();
        for (int i = 0; i < Blob.NODES; i++) {
            xx[i] = b.px[i];
            yy[i] = b.py[i];
        }
        // The silhouette is the real positional mesh, not a visual warp.
        // Short smoothing tangents avoid inventing distant overshooting lobes.
        blobPath.reset();
        blobPath.moveTo(xx[0], yy[0]);
        for (int i = 0; i < Blob.NODES; i++) {
            int prev = (i + Blob.NODES - 1) % Blob.NODES;
            int next = (i + 1) % Blob.NODES;
            int after = (i + 2) % Blob.NODES;
            blobPath.cubicTo(
                    xx[i] + (xx[next] - xx[prev]) / 9f,
                    yy[i] + (yy[next] - yy[prev]) / 9f,
                    xx[next] - (xx[after] - xx[i]) / 9f,
                    yy[next] - (yy[after] - yy[i]) / 9f,
                    xx[next], yy[next]);
        }
        blobPath.close();

        // Exactly one opaque fill; no fake light ovals, radial gradients or
        // translucent collision halos. The elastic outline is the entire blob.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(Palette.forTier(b.tier));
        canvas.drawPath(blobPath, paint);

        line.setColor(blend(Palette.forTier(b.tier), 0xFFF1E9FF, .23f));
        line.setStrokeWidth(Math.max(1.0f, r * .020f));
        canvas.drawPath(blobPath, line);
    }

    private void drawAdhesions(Canvas canvas) {
        // Subtle coloured filaments appear between different-tier blobs as
        // they part. They are tied to actual physical sticky particle links.
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (BlobWorld.Adhesion link : world.adhesions) {
            float d=link.length();
            if(d<4.5f||d>25f)continue;
            float alpha=(1f-link.age/.36f)*(.85f-(d-4.5f)/50f);
            paint.setColor(withAlpha(Palette.forTier(link.a.tier),(int)(220f*alpha)));
            paint.setStrokeWidth(Math.max(1.2f,4.1f-d*.12f));
            canvas.drawLine(link.a.px[link.ai],link.a.py[link.ai],
                    link.b.px[link.bi],link.b.py[link.bi],paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawWaves(Canvas c) {
        for (Wave w : waves) {
            float progress = w.age / .68f;
            line.setColor(withAlpha(Palette.forTier(w.tier), (int) (155 * (1 - progress))));
            line.setStrokeWidth(2.8f * (1-progress) + .5f);
            c.drawCircle(w.x, w.y, w.r * (.55f + progress * 1.22f), line);
        }
    }

    private void drawDroplets(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        for (Droplet d : droplets) {
            paint.setColor(withAlpha(Palette.forTier(d.tier), (int) (220 * (1f - d.age / .70f))));
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
        line.setColor(0xFF688F95);
        line.setStrokeWidth(4.2f);
        c.drawPath(glassPath(),line);
        line.setColor(0x886DA8A0);
        line.setStrokeWidth(1.1f);
        c.drawPath(glassPath(),line);
    }

    private void drawHUD(Canvas c,float h) {
        label(c,"LAVA BLOB",128,53,25,0xFFEDF4EE,true);
        label(c,spec.endless?"Modalità infinita":("Livello "+(spec.id+1)+" · "+spec.name),
                160,77,12,0xFFB1CDD0,false);
        label(c,"PUNTI",73,105,10,0xFF9EB0B4,true);
        label(c,Long.toString(world.score),73,133,22,0xFFFFFFFF,true);
        label(c,"RECORD",189,105,10,0xFF9EB0B4,true);
        label(c,Long.toString(best),189,133,20,0xFFFFFFFF,true);

        paint.setColor(0xFF263D45);paint.setStyle(Paint.Style.FILL);
        c.drawCircle(319,58,20,paint);
        c.drawCircle(376,58,20,paint);
        label(c,"↻",319,66,25,0xFFD7EDE4,false);
        label(c,"⌂",376,66,24,0xFFD7EDE4,false);

        if(spec.endless) {
            paint.setColor(eraseMode?0xFFDB5461:0xFF275F5A);
            c.drawRoundRect(new RectF(273,97,398,143),17,17,paint);
            label(c,"ERASE · "+world.eraseCount,335,125,14,0xFFFFFFFF,true);
        }else{
            label(c,"OBIETTIVO "+spec.minScore,328,110,11,0xFFB0D1C6,true);
            label(c,"EXTRA "+spec.extras(world.score,world.maxTierReached,
                    world.mergeCount)+"/"+spec.totalExtras(),
                    327,132,11,0xFFE6B8C2,false);
        }

        if(world.blockedTime>.5f&&!world.gameOver) {
            label(c,"INGRESSO BLOCCATO · "+(int)Math.ceil(world.remainingSeconds())+"s",
                    210,188,12,0xFFFFAAB1,true);
            if(spec.endless&&world.eraseCount>0)
                label(c,"Usa un Erase per liberare spazio",210,206,11,0xFFE8D9D9,false);
        }
        if(eraseMode) {
            label(c,"TOCCA IL BLOB DA ELIMINARE",210,190,12,0xFFFFBBC9,true);
        }

        float nextY=world.bottom()+80;
        if(aiming) {
            line.setColor(0x6679A6B1);line.setStrokeWidth(1.6f);
            line.setPathEffect(new android.graphics.DashPathEffect(new float[]{7,11},0f));
            c.drawLine(aimX,world.bottom()-8,aimX,world.vessel.top+10,line);
            line.setPathEffect(null);
        }
        paint.setStyle(Paint.Style.FILL);paint.setShader(null);
        paint.setColor(0xFF24333F);
        c.drawRoundRect(new RectF(43,world.bottom()+32,377,h-22),21,21,paint);
        drawNextBubble(c,aimX,nextY);
        label(c,"TRASCINA · RILASCIA · SCUOTI",210,h-29,11,0xFFC3D3D4,true);
        if(blockedFlash>0f)
            label(c,"QUESTO INGRESSO È OCCUPATO",210,world.bottom()+22,11,
                0xFFFFAAB1,true);
    }

    private void drawNextBubble(Canvas c,float x,float y) {
        float r=Blob.RADII[world.nextTier];
        paint.setColor(Palette.forTier(world.nextTier));
        c.drawCircle(x,y,r*.76f,paint);
        line.setColor(blend(Palette.forTier(world.nextTier),0xFFFFFFFF,.28f));
        line.setStrokeWidth(1.3f);
        c.drawCircle(x,y,r*.76f,line);
    }

    private void drawGameOver(Canvas c,float h) {
        paint.setStyle(Paint.Style.FILL);paint.setShader(null);
        paint.setColor(0xEB141C29);
        c.drawRoundRect(new RectF(34,h*.36f,386,h*.71f),29,29,paint);
        boolean won=spec.passed(world.score);
        label(c,spec.endless?"PARTITA FINITA":(won?"LIVELLO SUPERATO":"RIPROVA"),
                210,h*.44f,22,0xFFF2F5F1,true);
        label(c,world.score+" punti",210,h*.495f,19,0xFFC6DCD7,false);
        if(!spec.endless)label(c,"Extra "+spec.extras(world.score,
                world.maxTierReached,world.mergeCount)+"/"+spec.totalExtras(),
                210,h*.537f,14,0xFFD9BFC9,false);
        paint.setColor(0xFF275F5A);
        c.drawRoundRect(new RectF(55,h*.585f,198,h*.664f),15,15,paint);
        paint.setColor(0xFF3A4553);
        c.drawRoundRect(new RectF(221,h*.585f,365,h*.664f),15,15,paint);
        label(c,"RIPROVA",127,h*.633f,13,0xFFF6F4F2,true);
        label(c,"HOME",293,h*.633f,13,0xFFF6F4F2,true);
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
        if(getWidth()==0||getHeight()==0)return false;
        float x=(event.getX()-offsetX())/scale(),y=event.getY()/scale();
        float h=getHeight()/scale();
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(world.gameOver){
                    if(y>h*.57f&&y<h*.70f){
                        if(x>=209)onHome.run();else restart();
                    }
                    return true;
                }
                if(y<94f&&x>345f){onHome.run();return true;}
                if(y<94f&&x>287f){restart();return true;}
                if(spec.endless&&x>266f&&y>96f&&y<149f) {
                    eraseMode=!eraseMode;invalidate();return true;
                }
                if(eraseMode){
                    if(world.eraseAt(x,y)){
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        eraseMode=false;
                    }
                    return true;
                }
                aiming=true;aimX=BlobWorld.clamp(x,49f,371f);return true;
            case MotionEvent.ACTION_MOVE:
                if(aiming)aimX=BlobWorld.clamp(x,49f,371f);
                return true;
            case MotionEvent.ACTION_UP:
                if(aiming){
                    aimX=BlobWorld.clamp(x,49f,371f);
                    if(world.launch(aimX))
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    else blockedFlash=.9f;
                }
                aiming=false;return true;
            case MotionEvent.ACTION_CANCEL:
                aiming=false;return true;
            default:return true;
        }
    }

    private void restart() {
        leave();
        world.reset();
        waves.clear();droplets.clear();
        blockedFlash=0f;eraseMode=false;aiming=false;
        aimX=W*.5f;previousFrame=0;finalized=false;
        invalidate();
    }
}
