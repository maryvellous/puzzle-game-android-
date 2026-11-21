package com.maryvellous.lavablobs;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.Log;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;
import java.util.Locale;

/**
 * Portrait, resolution-independent Android Canvas game.
 * All artwork is procedural: no sprites, engines, trackers or network access.
 */
public final class LavaView extends View {
    private static final float W = BlobWorld.WIDTH;
    private static final int BACKGROUND = Palette.BACKGROUND_TOP;
    private static final float PHYSICS_STEP = 1f / 60f;
    private static final int MAX_STEPS_PER_FRAME = 4;
    private static final int PERF_REPORT_FRAMES = 180;
    private static final Typeface FONT_REGULAR = Typeface.create("sans-serif",Typeface.NORMAL);
    private static final Typeface FONT_MEDIUM = Typeface.create("sans-serif-medium",Typeface.NORMAL);
    // Android built-in playful face: inspired by chunky handwritten display fonts,
    // without bundling the separately licensed More Sugar font.
    private static final Typeface FONT_DISPLAY = Typeface.create("casual",Typeface.BOLD);
    // Solid, saturated, intentionally distinguishable hues from blue-indigo
    // through lavender and violet, never red/yellow/green.
    private final Palette.Theme theme;
    private final GameSpec spec;
    private final ProgressStore progress;
    private final Runnable onHome;
    private final GameSave session;
    private float lastCheckpoint;
    private final BlobWorld world;
    private final GameAudio audio;
    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letters = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path blobPath = new Path();
    private final Path speakerPath = new Path();
    private final Path titleGlyph = new Path(), titleWarped = new Path();
    private final long titleStart = System.nanoTime();
    private final Path cachedGlassPath = new Path();
    private Vessel cachedGlassVessel;
    private Shader cachedBackgroundShader;
    private float cachedShaderHeight = -1f;
    private final boolean perfLogging;
    private final float[] xx = new float[Blob.NODES];
    private final float[] yy = new float[Blob.NODES];
    private final ArrayList<Wave> waves = new ArrayList<>();
    private final ArrayList<Droplet> droplets = new ArrayList<>();
    private final ArrayList<PopFx> pops = new ArrayList<>();
    private float shakeAge=1f,shakePress,popPress;
    private int shakeStep;
    private float scorePulse;
    private long lastAnimatedScore;

    private float aimX = W / 2;
    private long previousFrame;
    private float physicsAccumulator;
    private int perfFrames,perfSteps,perfSaveCount;
    private long perfGapNs,perfWorstGapNs,perfPhysicsNs,perfCanvasNs,perfCpuNs;
    private long perfSaveNs,perfWorstSaveNs;
    private boolean aiming;
    private boolean draggingFromTray;
    private int activePointerId=MotionEvent.INVALID_POINTER_ID;
    private float dragX,dragY;
    private float activeSeconds;
    private long lastActionMs=SystemClock.uptimeMillis();
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

    private static final class PopFx {
        final float x,y,r;
        final int tier;
        float age;
        PopFx(float x,float y,float r,int tier) {
            this.x=x;this.y=y;this.r=r;this.tier=tier;
        }
    }

    LavaView(Context context, GameSpec spec, ProgressStore progress,
            Runnable onHome,GameSave session,GameSave.Snapshot resume) {
        super(context);
        this.spec=spec;this.progress=progress;this.onHome=onHome;
        this.session=session;
        audio=new GameAudio(context);
        theme=new ThemeStore(context).current();
        perfLogging=(context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        world=new BlobWorld(new Random(),spec);
        if(resume!=null) {
            try {
                GameSave.restore(world,resume.data);
                aimX=(float)resume.data.optDouble("aimX",W/2);
                eraseMode=world.eraseCount>0&&resume.data.optBoolean("eraseMode",false);
                lastCheckpoint=world.time;
                activeSeconds=(float)resume.data.optDouble("activeSeconds",world.time);
            }catch(Exception ignored){
                // A corrupted save must not crash startup. Keep the old slot
                // until the next successful checkpoint replaces it.
                world.reset();
            }
        }
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setHapticFeedbackEnabled(true);
        best=progress.record(spec.id);
        lastAnimatedScore=world.score;
        letters.setTextAlign(Paint.Align.CENTER);
        line.setStyle(Paint.Style.STROKE);
    }

    /** Preserve game without counting a pause as a completed playtest. */
    void leave() { saveCheckpoint(); audio.release(); }
    void releaseAudio(){audio.release();}
    void pauseAudio(){audio.pause();}
    void resumeAudio(){audio.resume();}

    void saveCheckpoint() {
        if(world.gameOver) {session.clear();return;}
        long started=perfLogging?System.nanoTime():0L;
        boolean saved=session.save(spec,world,aimX,eraseMode,activeSeconds);
        if(perfLogging) {
            long spent=System.nanoTime()-started;
            perfSaveNs+=spent;
            perfWorstSaveNs=Math.max(perfWorstSaveNs,spent);
            perfSaveCount++;
        }
        if(saved)lastCheckpoint=world.time;
    }

    /** Called by the activity's sensor listener on the UI thread. */
    void shake(float sensorX, float sensorY, float sensorZ) {
        lastActionMs=SystemClock.uptimeMillis();
        world.shake(sensorX, sensorY, sensorZ);
        invalidate();
    }

    void pauseClock() {previousFrame=0L;physicsAccumulator=0f;}

    private float scale() {
        return Math.min(getWidth() / W, getHeight() / 780f);
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
        long gapNs = previousFrame == 0L ? 0L : Math.max(0L,now-previousFrame);
        float elapsed = Math.min(.10f,gapNs / 1_000_000_000f);
        previousFrame = now;
        // Active playtime excludes long in-foreground idle periods without
        // changing the simulation timer, particles or game-over countdown.
        if(!world.gameOver&&SystemClock.uptimeMillis()-lastActionMs<=12_000L)
            activeSeconds+=elapsed;
        // Fixed-size physics steps keep membrane stiffness independent of FPS.
        // Bound catch-up work: never try to simulate unlimited missed frames.
        physicsAccumulator=Math.min(PHYSICS_STEP*MAX_STEPS_PER_FRAME,
                physicsAccumulator+elapsed);
        long physicsStart=System.nanoTime();
        int steps=0;
        while(physicsAccumulator+1e-7f>=PHYSICS_STEP&&steps<MAX_STEPS_PER_FRAME) {
            world.tick(PHYSICS_STEP);
            physicsAccumulator=Math.max(0f,physicsAccumulator-PHYSICS_STEP);
            steps++;
        }
        long physicsNs=System.nanoTime()-physicsStart;
        if(steps>0)audio.observePhysics(world,steps*PHYSICS_STEP);
        advanceEffects(elapsed);
        if(world.score!=lastAnimatedScore){
            lastAnimatedScore=world.score;
            scorePulse=.33f;
        }
        for (BlobWorld.Merge merge : world.consumeMerges()) {
            audio.merge(merge.tier);
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
        // Several blobs may collide in a frame: only the strongest can sound.
        BlobWorld.Impact strongest=BlobWorld.strongestImpact(world.consumeImpacts());
        if(strongest!=null)audio.impact(strongest.tier,strongest.speed,
                strongest.radiusA,strongest.radiusB);
        if (world.score > best) best=world.score;
        if(world.gameOver&&!finalized) {
            progress.save(spec,world,true,activeSeconds);
            session.clear();
            finalized=true;
        }else if(!world.gameOver&&world.time-lastCheckpoint>1.5f){
            saveCheckpoint();
        }
        long canvasStart=System.nanoTime();
        Canvas canvas = raw;
        canvas.save();
        canvas.translate(offsetX(), 0f);
        canvas.scale(scale, scale);

        drawBackground(canvas, h);
        // Cosmetic whole-vessel wobble. The physical vessel and all collider
        // positions remain untouched; internal floating particles are unchanged.
        canvas.save();
        if(shakeAge<.40f) {
            float fade=1f-shakeAge/.40f;
            canvas.translate((float)Math.sin(shakeAge*64f)*5.5f*fade,
                    (float)Math.sin(shakeAge*41f)*2.4f*fade);
        }
        drawVessel(canvas);
        canvas.save();
        Path glass=glassPath();
        canvas.clipPath(glass);
        drawFloatingSpecks(canvas);
        drawWaves(canvas);
        drawAdhesions(canvas);
        for(Blob b:world.blobs)drawBlob(canvas,b);
        drawDroplets(canvas);
        drawPops(canvas);
        canvas.restore();
        drawVesselRim(canvas);
        canvas.restore();
        drawHUD(canvas,h);
        if(BuildConfig.AUDIO_TEST) {
            letters.setTypeface(FONT_MEDIUM);
            letters.setTextSize(11f);
            letters.setColor(Color.argb(215,255,230,161));
            letters.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(BuildConfig.AUDIO_SIZE_TEST
                    ?"AUDIO SIZE TEST - NON DEFINITIVO"
                    :"AUDIO TEST - NON DEFINITIVO",W*.5f,h-12f,letters);
        }
        if(world.gameOver)drawGameOver(canvas,h);

        canvas.restore();
        long canvasNs=System.nanoTime()-canvasStart;
        recordPerformance(gapNs,physicsNs,canvasNs,System.nanoTime()-now,steps);
        postInvalidateOnAnimation();
    }

    private void recordPerformance(long gapNs,long physicsNs,long canvasNs,long cpuNs,int steps) {
        if(!perfLogging)return;
        perfFrames++;
        perfGapNs+=gapNs;
        perfWorstGapNs=Math.max(perfWorstGapNs,gapNs);
        perfPhysicsNs+=physicsNs;
        perfCanvasNs+=canvasNs;
        perfCpuNs+=cpuNs;
        perfSteps+=steps;
        if(perfFrames<PERF_REPORT_FRAMES)return;
        double fps=perfGapNs==0?0:perfFrames*1e9/(double)perfGapNs;
        double simRate=perfGapNs==0?0:perfSteps*PHYSICS_STEP*1e9/perfGapNs;
        Log.d("LavaPerf",String.format(Locale.US,
                "fps=%.1f worstGap=%.1fms simRate=%.2f physics=%.2fms canvas=%.2fms cpu=%.2fms saves=%d saveAvg=%.1fms saveMax=%.1fms blobs=%d",
                fps,perfWorstGapNs/1e6,simRate,
                perfPhysicsNs/(1e6*perfFrames),perfCanvasNs/(1e6*perfFrames),
                perfCpuNs/(1e6*perfFrames),perfSaveCount,
                perfSaveCount==0?0:perfSaveNs/(1e6*perfSaveCount),
                perfWorstSaveNs/1e6,world.blobs.size()));
        perfFrames=perfSteps=perfSaveCount=0;
        perfGapNs=perfWorstGapNs=perfPhysicsNs=perfCanvasNs=perfCpuNs=0L;
        perfSaveNs=perfWorstSaveNs=0L;
    }

    private void drawBackground(Canvas canvas, float h) {
        paint.setStyle(Paint.Style.FILL);
        if(cachedBackgroundShader==null||cachedShaderHeight!=h) {
            cachedBackgroundShader=new LinearGradient(0f,0f,0f,h,
                    Palette.BACKGROUND_TOP,Palette.BACKGROUND_BOTTOM,Shader.TileMode.CLAMP);
            cachedShaderHeight=h;
        }
        paint.setShader(cachedBackgroundShader);
        canvas.drawRect(0, 0, W, h, paint);
        paint.setShader(null);
        for (int i = 0; i < 20; i++) {
            float x = (i * 101 + 55) % 400 + 10;
            float y = (i * 163 + 89) % (int) h;
            paint.setColor(Palette.alpha(theme.speck,54));
            canvas.drawCircle(x, y, (i % 3 + 1) * .60f, paint);
        }
    }

    private Path glassPath() {
        // The physical vessel profile is immutable until world.setHeight()
        // swaps the Vessel instance. Reuse one Path for all three draw passes.
        if(cachedGlassVessel!=world.vessel) {
            cachedGlassPath.reset();
            for(int i=0;i<world.vessel.xs.length;i++){
                float x=world.vessel.xs[i],y=world.vessel.ys[i];
                if(i==0)cachedGlassPath.moveTo(x,y);
                else cachedGlassPath.lineTo(x,y);
            }
            cachedGlassPath.close();
            cachedGlassVessel=world.vessel;
        }
        return cachedGlassPath;
    }

    private void drawVessel(Canvas canvas) {
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.vesselFill);
        canvas.drawPath(glassPath(),paint);
    }

    private void drawFloatingSpecks(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 24; i++) {
            float x = 55 + (i * 79 % 305);
            float range = world.bottom() - world.vessel.top - 25;
            float y = world.bottom() - ((i * 63f + world.time * (8 + i % 5 * 4)) % Math.max(1,range));
            paint.setColor(Palette.alpha(theme.speck,83));
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
        // HEX is applied without alpha, tint or shader.
        paint.setColor(theme.forTier(b.tier));
        paint.setAlpha(255);
        paint.setColorFilter(null);
        canvas.drawPath(blobPath, paint);

        int blobColor=theme.forTier(b.tier);
        float rimBlend=Palette.relativeLuminance(blobColor)<.12f?.64f:.23f;
        line.setColor(blend(blobColor,Palette.VESSEL_OUTLINE,rimBlend));
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
            paint.setColor(withAlpha(theme.forTier(link.a.tier),(int)(220f*alpha)));
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
            line.setColor(withAlpha(theme.forTier(w.tier), (int) (155 * (1 - progress))));
            line.setStrokeWidth(2.8f * (1-progress) + .5f);
            c.drawCircle(w.x, w.y, w.r * (.55f + progress * 1.22f), line);
        }
    }

    private void drawDroplets(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        for (Droplet d : droplets) {
            paint.setColor(withAlpha(theme.forTier(d.tier), (int) (220 * (1f - d.age / .70f))));
            c.drawCircle(d.x, d.y, d.radius * (1f - d.age / .70f), paint);
        }
    }

    private void advanceEffects(float dt) {
        blockedFlash = Math.max(0f, blockedFlash - dt);
        scorePulse=Math.max(0f,scorePulse-dt);
        shakePress=Math.max(0f,shakePress-dt*3.5f);
        popPress=Math.max(0f,popPress-dt*3.5f);
        if(shakeAge<.41f) {
            shakeAge+=dt;
            // Three alternating pulses recreate a phone shake rather than a
            // single directional kick. Reuse the already approved physics API.
            while(shakeStep<3&&shakeAge>=(shakeStep+1)*.085f) {
                if(shakeStep==0)world.shake(-8f, 5f, 8f);
                else if(shakeStep==1)world.shake(7f,-4.5f,8f);
                else world.shake(-5.5f,3.5f,7f);
                shakeStep++;
            }
        }
        for(int i=pops.size()-1;i>=0;i--){
            PopFx fx=pops.get(i);
            fx.age+=dt;
            if(fx.age>.58f)pops.remove(i);
        }
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

    private void drawPops(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        for(PopFx fx:pops){
            final float t=fx.age;
            int base=theme.forTier(fx.tier);
            if(t<.095f) {
                float swell=t/.095f;
                paint.setColor(withAlpha(base,Math.round(255f*(1f-swell*.36f))));
                c.drawCircle(fx.x,fx.y,fx.r*(1f+.18f*swell),paint);
            } else {
                float progress=Math.min(1f,(t-.095f)/.485f);
                float eased=1f-(1f-progress)*(1f-progress);
                line.setColor(withAlpha(Palette.VESSEL_OUTLINE,Math.round(210f*(1f-progress))));
                line.setStrokeWidth(3.8f*(1f-progress)+.4f);
                c.drawCircle(fx.x,fx.y,fx.r*(.65f+eased*.75f),line);
                for(int i=0;i<14;i++){
                    float angle=(float)(i*Math.PI*2/14+.25f);
                    float distance=fx.r*(.32f+1.42f*eased);
                    float px=fx.x+(float)Math.cos(angle)*distance;
                    float py=fx.y+(float)Math.sin(angle)*distance;
                    paint.setColor(withAlpha(i%4==0?Palette.VESSEL_OUTLINE:base,
                            Math.round(235f*(1f-progress))));
                    c.drawCircle(px,py,Math.max(.8f,(2.8f+(i%3)*1.2f)*(1f-progress)),paint);
                }
            }
        }
    }

    private void drawVesselRim(Canvas c) {
        line.setColor(Palette.VESSEL_OUTLINE);
        line.setStrokeWidth(4.2f);
        c.drawPath(glassPath(),line);
        line.setColor(Palette.alpha(Palette.VESSEL_OUTLINE,112));
        line.setStrokeWidth(1.1f);
        c.drawPath(glassPath(),line);
    }

    /** Bubbly HUD: quiet ivory header, colourful floating counters, spacious play. */
    private void drawHUD(Canvas c,float h) {
        // Ivory is shared by every theme and keeps the changing title readable.
        hudPanel(c,18,19,402,103,35,Palette.NEUTRAL_SURFACE);
        BlobTitle.draw(c,letters,paint,titleGlyph,titleWarped,theme,
                (System.nanoTime()-titleStart)/1_000_000_000f,34f,66f);
        String location=spec.endless?"Modalità infinita":
                spec.trial?spec.name:"Livello "+(spec.id+1)+" · "+spec.name;
        label(c,location,210,90,11,theme.onSurfaceMuted,false);

        // Two round floating header actions, not more outlined rectangles.
        bubbleButton(c,53,60,23,theme.secondary,0);
        bubbleButton(c,367,60,23,theme.tertiary,0);
        label(c,"↻",53,69,26,contrastInk(theme.secondary),true);
        label(c,"⌂",367,69,25,contrastInk(theme.tertiary),true);

        // Score bubbles bounce lightly when the number changes.
        float bounce=scorePulse>0?
                (float)Math.sin((.33f-scorePulse)/.33f*Math.PI)*.045f:0f;
        c.save();c.scale(1f+bounce,1f+bounce,112,143);
        bubbleCounter(c,28,109,197,174,theme.forTier(0));
        label(c,"PUNTI",112,132,11,contrastInk(theme.forTier(0)),true);
        bigNumber(c,Long.toString(world.score),112,159,26,contrastInk(theme.forTier(0)),145f);
        c.restore();

        int goalFill=theme.tertiary;
        bubbleCounter(c,224,109,392,174,goalFill);
        String heading=spec.endless?"RECORD":spec.trial?"DROP":"OBIETTIVO";
        String figure=spec.endless?Long.toString(Math.max(best,world.score)):
                spec.trial?Integer.toString(world.dropCount):
                Integer.toString(spec.minScore);
        label(c,heading,308,132,11,contrastInk(goalFill),true);
        bigNumber(c,figure,308,159,26,contrastInk(goalFill),143f);

        if(!spec.endless&&!spec.trial)
            label(c,"EXTRA "+spec.extras(world.score,world.maxTierReached,
                    world.mergeCount)+"/"+spec.totalExtras(),
                    308,194,10.5f,theme.text,true);
        drawSoundToggle(c);

        if(world.blockedTime>.5f&&!world.gameOver) {
            label(c,"INGRESSO BLOCCATO · "+(int)Math.ceil(world.remainingSeconds())+"s",
                    210,212,11,theme.caution,true);
            if(spec.endless&&world.eraseCount>0)
                label(c,"Usa POP per liberare spazio",210,229,10.5f,theme.text,false);
        }
        if(eraseMode)
            label(c,"TOCCA IL BLOB DA FAR SCOPPIARE",210,228,11f,theme.caution,true);
        else if(world.dropCount==0&&!aiming&&!spec.trial)
            label(c,"TOCCA IL VASO O TRASCINA LA BOLLA",210,218,10f,theme.text,false);

        if(aiming) {
            line.setColor(Palette.alpha(theme.secondary,150));
            line.setStrokeWidth(1.4f);
            line.setPathEffect(new android.graphics.DashPathEffect(new float[]{7,11},0f));
            c.drawLine(aimX,world.bottom()-8,aimX,world.vessel.top+10,line);
            line.setPathEffect(null);
        }

        float centerY=h-70f;
        float nextX=spec.endless?210f:276f;
        float shakeX=spec.endless?77f:118f;
        // Small central next-blob bubble replaces the old broad tray.
        bubbleButton(c,nextX,centerY,43,Palette.NEUTRAL_SURFACE,0f);
        float previewScale=Math.min(1.04f,30f/(Blob.RADII[world.nextTier]*.76f));
        drawNextBubble(c,nextX,centerY-4f,previewScale);
        // The tray stays visible as a cancel target while a ghost follows
        // the fingertip into the vessel.
        if(draggingFromTray&&dragY<centerY-50f) {
            float ghostScale=Math.min(1f,32f/(Blob.RADII[world.nextTier]*.76f));
            drawNextBubble(c,dragX,Math.max(world.vessel.top+12f,dragY),ghostScale);
        }
        label(c,"PROSSIMO",nextX,centerY-53f,10.5f,theme.text,true);
        label(c,"TRASCINA",nextX,h-13,10.5f,theme.text,true);

        int shakeFill=theme.secondary;
        bubbleButton(c,shakeX,centerY,43,shakeFill,shakePress);
        int shakeInk=contrastInk(shakeFill);
        label(c,"≈",shakeX,centerY-6,27,shakeInk,true);
        label(c,"SCUOTI",shakeX,centerY+20,14,shakeInk,true);

        // POP does not even render in levels or trials.
        if(spec.endless) {
            boolean available=world.eraseCount>0;
            int popFill=available?(eraseMode?theme.tertiary:theme.primary):
                    Palette.NEUTRAL_SURFACE;
            int popInk=available?contrastInk(popFill):theme.onSurfaceMuted;
            bubbleButton(c,343,centerY,43,popFill,popPress);
            label(c,"POP",343,centerY+11,23,popInk,true);
            label(c,available?"×"+world.eraseCount:"0",343,centerY-16,12,popInk,true);
        }

        if(blockedFlash>0f&&world.blockedTime<=.5f)
            label(c,"QUESTO INGRESSO È OCCUPATO",210,
                    Math.min(245f,world.vessel.top-8f),11,theme.caution,true);
    }

    /** Independent effects toggle; a separate music switch can be added later. */
    private void drawSoundToggle(Canvas c) {
        final float x=365f,y=205f;
        boolean enabled=audio.effectsEnabled();
        int fill=enabled?theme.secondary:Palette.NEUTRAL_SURFACE;
        int ink=enabled?contrastInk(fill):theme.onSurfaceMuted;
        bubbleButton(c,x,y,21f,fill,0f);
        speakerPath.reset();
        speakerPath.moveTo(x-11,y-5);
        speakerPath.lineTo(x-6,y-5);
        speakerPath.lineTo(x+1,y-11);
        speakerPath.lineTo(x+1,y+11);
        speakerPath.lineTo(x-6,y+5);
        speakerPath.lineTo(x-11,y+5);
        speakerPath.close();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ink);
        c.drawPath(speakerPath,paint);
        line.setColor(ink);
        line.setStrokeWidth(2.2f);
        line.setStrokeCap(Paint.Cap.ROUND);
        if(enabled) {
            c.drawArc(x-3,y-10,x+15,y+10,-62,124,false,line);
        } else {
            c.drawLine(x+6,y-9,x+17,y+9,line);
        }
        line.setStrokeCap(Paint.Cap.BUTT);
        label(c,enabled?"SUONI":"MUTO",x,y+36,10,theme.text,true);
    }

    private void bubbleCounter(Canvas c,float l,float t,float r,float b,int fill) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        c.drawOval(l,t,r,b,paint);
        // Gentle asymmetric lobes; no outline, gradient or fake shine.
        c.drawCircle(l+22f,t+20f,16f,paint);
        c.drawCircle(r-19f,b-19f,12f,paint);
    }

    private void bubbleButton(Canvas c,float x,float y,float r,int fill,float pressed) {
        float factor=1f-.095f*Math.min(1f,pressed);
        c.save();
        c.scale(factor,factor,x,y);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        c.drawCircle(x,y,r,paint);
        c.drawCircle(x-r*.72f,y-r*.16f,r*.26f,paint);
        c.drawCircle(x+r*.66f,y+r*.31f,r*.20f,paint);
        c.restore();
    }

    private void bigNumber(Canvas c,String value,float x,float y,
            float size,int ink,float maxWidth) {
        letters.setShader(null);
        letters.setColor(ink);
        letters.setTypeface(FONT_DISPLAY);
        letters.setTextSize(size);
        float measured=letters.measureText(value);
        if(measured>maxWidth)letters.setTextSize(Math.max(13f,size*maxWidth/measured));
        c.drawText(value,x,y,letters);
    }

    private int contrastInk(int color) {
        return Palette.relativeLuminance(color)>.43f ? 0xFF211A26 : 0xFFF7F1F3;
    }

    private void hudPanel(Canvas c,float l,float t,float r,float b,float radius,int fill){
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(fill);
        c.drawRoundRect(l,t,r,b,radius,radius,paint);
        // Flat Canva panels: no ornamental or contrasting button outlines.
    }

    private void drawNextBubble(Canvas c,float x,float y,float previewScale) {
        float r=Blob.RADII[world.nextTier]*.76f*previewScale;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(theme.forTier(world.nextTier));
        c.drawCircle(x,y,r,paint);
        int nextColor=theme.forTier(world.nextTier);
        line.setColor(blend(nextColor,theme.text,
                Palette.relativeLuminance(nextColor)<.12f?.66f:.28f));
        line.setStrokeWidth(1.3f);
        c.drawCircle(x,y,r,line);
    }

    private void drawGameOver(Canvas c,float h) {
        float top=h*.35f,bottom=h*.72f;
        hudPanel(c,34,top,386,bottom,26,Palette.alpha(theme.surfaceRaised,246));
        boolean won=spec.passed(world.score);
        String title=spec.trial?"PROVA CONCLUSA":spec.endless?"PARTITA FINITA":
                won?"LIVELLO SUPERATO":"RIPROVA";
        label(c,title,210,top+57,21,theme.onSurface,true);
        label(c,world.score+" punti  ·  "+world.dropCount+" drop",
                210,top+99,17,theme.onSurfaceMuted,false);
        if(!spec.endless&&!spec.trial)
            label(c,"Extra "+spec.extras(world.score,world.maxTierReached,
                    world.mergeCount)+"/"+spec.totalExtras(),
                    210,top+125,13,theme.onSurfaceMuted,false);

        float buttonsTop=h*.585f,buttonsBottom=h*.664f;
        hudPanel(c,55,buttonsTop,198,buttonsBottom,15,theme.primary);
        hudPanel(c,221,buttonsTop,365,buttonsBottom,15,theme.surface);
        label(c,"RIPROVA",127,h*.633f,13,theme.onPrimary,true);
        label(c,"HOME",293,h*.633f,13,theme.onSurface,true);
    }

    private void label(Canvas c, String value, float x, float y, float size,
                       int color, boolean bold) {
        letters.setShader(null);
        letters.setColor(color);
        letters.setTextSize(size);
        letters.setTypeface(bold&&size>=13f?FONT_DISPLAY:(bold?FONT_MEDIUM:FONT_REGULAR));
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
        final float sc=scale();
        final float h=getHeight()/sc;
        final int action=event.getActionMasked();
        float x=(event.getX()-offsetX())/sc;
        float y=event.getY()/sc;
        final float centerY=h-70f;
        final float nextX=spec.endless?210f:276f;

        switch(action){
            case MotionEvent.ACTION_DOWN:
                lastActionMs=SystemClock.uptimeMillis();
                activePointerId=event.getPointerId(0);
                if(hitBubble(x,y,365f,205f,27f)){
                    boolean enabled=!audio.effectsEnabled();
                    audio.setEffectsEnabled(enabled);
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    announceForAccessibility(enabled?"Effetti sonori attivati":"Effetti sonori disattivati");
                    invalidate();
                    return true;
                }
                if(world.gameOver){
                    if(y>h*.57f&&y<h*.70f){
                        if(x>=209)onHome.run();else restart();
                    }
                    return true;
                }
                if(y>26f&&y<98f&&x>337f){onHome.run();return true;}
                if(y>26f&&y<98f&&x<82f){restart();return true;}
                float shakeX=spec.endless?77f:118f;
                if(hitBubble(x,y,shakeX,centerY,51f)){
                    world.shake(8f,-5f,8f);
                    shakeAge=0f;shakeStep=0;shakePress=1f;
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    saveCheckpoint();
                    invalidate();return true;
                }
                if(spec.endless&&hitBubble(x,y,343f,centerY,51f)){
                    if(world.eraseCount>0){
                        eraseMode=!eraseMode;
                        popPress=1f;
                    }
                    invalidate();return true;
                }
                if(eraseMode){
                    if(y<centerY-50f&&world.eraseAt(x,y)){
                        pops.add(new PopFx(world.lastPoppedX,world.lastPoppedY,
                                world.lastPoppedRadius,world.lastPoppedTier));
                        audio.pop();
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        popPress=1f;
                        eraseMode=false;
                        saveCheckpoint();
                    }
                    invalidate();return true;
                }
                if(hitBubble(x,y,nextX,centerY,49f)){
                    // Begin a real tray drag, not a tap-to-launch.
                    aiming=true;
                    draggingFromTray=true;
                    dragX=x;dragY=y;
                    aimX=BlobWorld.clamp(x,49f,371f);
                    invalidate();return true;
                }
                if(y>=centerY-50f)return true;
                // Preserve the existing fast touch/slide/release launch.
                aiming=true;draggingFromTray=false;
                aimX=BlobWorld.clamp(x,49f,371f);
                invalidate();return true;

            case MotionEvent.ACTION_MOVE:
                if(aiming){
                    int pointer=event.findPointerIndex(activePointerId);
                    if(pointer>=0){
                        x=(event.getX(pointer)-offsetX())/sc;
                        y=event.getY(pointer)/sc;
                        aimX=BlobWorld.clamp(x,49f,371f);
                        if(draggingFromTray){dragX=x;dragY=y;}
                        lastActionMs=SystemClock.uptimeMillis();
                        invalidate();
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                if(aiming){
                    int pointer=event.findPointerIndex(activePointerId);
                    if(pointer>=0){
                        x=(event.getX(pointer)-offsetX())/sc;
                        y=event.getY(pointer)/sc;
                    }
                    aimX=BlobWorld.clamp(x,49f,371f);
                    // Releasing over the tray cancels the drag.
                    boolean shouldLaunch=!draggingFromTray||
                            (y<centerY-50f&&y>=world.vessel.top-25f);
                    if(shouldLaunch){
                        int tier=world.nextTier;
                        if(world.launch(aimX)){
                            audio.launch(tier);
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            saveCheckpoint();
                        }else blockedFlash=.9f;
                    }
                }
                aiming=false;draggingFromTray=false;
                activePointerId=MotionEvent.INVALID_POINTER_ID;
                invalidate();return true;

            case MotionEvent.ACTION_CANCEL:
                aiming=false;draggingFromTray=false;
                activePointerId=MotionEvent.INVALID_POINTER_ID;
                invalidate();return true;
            default:return true;
        }
    }

    private static boolean hitBubble(float x,float y,float cx,float cy,float r) {
        float dx=x-cx,dy=y-cy;
        return dx*dx+dy*dy<=r*r;
    }

    private void restart() {
        if(world.dropCount>0&&!world.gameOver)progress.save(spec,world,false,activeSeconds);
        session.clear();
        world.reset();
        waves.clear();droplets.clear();pops.clear();
        blockedFlash=0f;eraseMode=false;aiming=false;draggingFromTray=false;
        activePointerId=MotionEvent.INVALID_POINTER_ID;
        activeSeconds=0f;lastActionMs=SystemClock.uptimeMillis();
        shakeAge=1f;shakePress=popPress=0f;shakeStep=0;scorePulse=0f;lastAnimatedScore=0L;
        aimX=W*.5f;previousFrame=0;finalized=false;lastCheckpoint=0f;
        saveCheckpoint();
        invalidate();
    }
}
