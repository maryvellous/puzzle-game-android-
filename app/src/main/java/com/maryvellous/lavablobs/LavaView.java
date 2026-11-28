package com.maryvellous.lavablobs;

import android.content.Context;
import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.content.SharedPreferences;
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
    private final Runnable onHome;
    private final Runnable onNext;
    private AlertDialog modal;
    private boolean lifecyclePaused;
    private final SharedPreferences uiPreferences;
    private int musicVolume;
    private int uiAction;
    private float touchX,touchY;
    private static final int UI_NONE=0,UI_SOUND=1,UI_MUSIC=2,UI_HELP=3,UI_PAUSE=4,UI_SHAKE=5,UI_EXTRA=6,UI_RETRY=7,UI_HOME=8,UI_NEXT=9;
    private float panelTop(float h){return GameConsoleLayout.panelTop(h);}
    private float panelY(float h,float y){return GameConsoleLayout.panelY(h,y);}
    private Vessel layoutVessel;
    private float layoutWidth;
    private float visualScale(float h){
        if(layoutVessel!=world.vessel){
            layoutVessel=world.vessel;float width=0;
            for(float x:layoutVessel.xs)width=Math.max(width,2*Math.abs(x-210));
            layoutWidth=width;
        }
        return GameConsoleLayout.vesselScale(h,layoutWidth,world.bottom()-world.vessel.top);
    }
    private float screenX(float x,float h){return GameConsoleLayout.toScreenX(x,visualScale(h));}
    private float screenY(float y,float h){return GameConsoleLayout.toScreenY(y,world.bottom(),visualScale(h),h);}
    private float worldX(float x,float h){return GameConsoleLayout.toWorldX(x,visualScale(h));}
    private float worldY(float y,float h){return GameConsoleLayout.toWorldY(y,world.bottom(),visualScale(h),h);}
    private float entryY(float h){return GameConsoleLayout.entryY(h);}
    private boolean paused(){return modal!=null||lifecyclePaused;}
    private final GameSessionController gameSession;
    private final BlobWorld world;
    private final GameSound audio;
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
    private final FrameProfile frameProfile=new FrameProfile();
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
    private final SimulationClock simulationClock=new SimulationClock();
    private int perfFrames,perfSteps,perfSaveCount,perfSlowFrames,perfCatchupFrames,perfPeakBlobs;
    private long perfGapNs,perfWorstGapNs,perfPhysicsNs,perfCanvasNs,perfCpuNs,perfWorstPhysicsNs,perfWorstCanvasNs;
    private long perfSaveNs,perfWorstSaveNs;
    private boolean aiming;
    private boolean draggingVolume;
    private static final float VOLUME_LEFT=65f,VOLUME_RIGHT=150f,VOLUME_Y=194f;
    private boolean draggingFromTray;
    private int activePointerId=MotionEvent.INVALID_POINTER_ID;
    private float dragX,dragY;
    private float blockedFlash;
    private long best;
    private boolean eraseMode;

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
            Runnable onHome,Runnable onNext,GameSave session,GameSave.Snapshot resume) {
        super(context);
        this.spec=spec;this.onHome=onHome;this.onNext=onNext;
        uiPreferences=context.getSharedPreferences("lava_blobs",Context.MODE_PRIVATE);
        musicVolume=uiPreferences.getInt("music_volume",70);
        // Approved refined + aggregated audio in every gameplay build.
        audio=new V2GameSound(context);
        theme=new ThemeStore(context).current();
        perfLogging=(context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        world=new BlobWorld(new Random(),spec);
        world.setProfiling(perfLogging);
        gameSession=new GameSessionController(world,spec,progress,session,SystemClock.uptimeMillis());
        if(resume!=null) {
            try {
                GameSave.restore(world,resume.data);
                aimX=(float)resume.data.optDouble("aimX",W/2);
                eraseMode=world.eraseCount>0&&resume.data.optBoolean("eraseMode",false);
                gameSession.restoreActiveTime((float)resume.data.optDouble("activeSeconds",world.time));
            }catch(Exception ignored){
                // A corrupted save must not crash startup. Keep the old slot
                // until the next successful checkpoint replaces it.
                world.reset();
            }
        }
        // Passive observation drives the approved material audio engine.
        world.disableLegacyImpactCollection();
        world.setContactProbe((V2GameSound)audio);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setHapticFeedbackEnabled(true);
        best=progress.record(spec.id);
        lastAnimatedScore=world.score;
        letters.setTextAlign(Paint.Align.CENTER);
        line.setStyle(Paint.Style.STROKE);
    }

    /** Preserve game without counting a pause as a completed playtest. */
    void leave() { lifecyclePaused=true;dismissSheet();saveCheckpoint();audio.release(); }
    void releaseAudio(){lifecyclePaused=true;dismissSheet();audio.release();}
    void pauseAudio(){lifecyclePaused=true;audio.pause();simulationClock.reset();}
    void resumeAudio(){lifecyclePaused=false;if(modal==null)audio.resume();simulationClock.reset();invalidate();}
    private void dismissSheet(){if(modal!=null)modal.dismiss();}
    boolean closeOpenSheet(){if(modal==null)return false;modal.dismiss();return true;}

    void saveCheckpoint() {
        if(world.gameOver){gameSession.checkpoint(aimX,eraseMode);return;}
        long started=perfLogging?System.nanoTime():0L;
        gameSession.checkpoint(aimX,eraseMode);
        if(perfLogging) {
            long spent=System.nanoTime()-started;
            perfSaveNs+=spent;
            perfWorstSaveNs=Math.max(perfWorstSaveNs,spent);
            perfSaveCount++;
            if(!paused()&&!world.gameOver)frameProfile.save(world.blobs.size(),spent);
        }
    }

    /** Called by the activity's sensor listener on the UI thread. */
    void shake(float sensorX, float sensorY, float sensorZ) {
        if(paused()||world.gameOver)return;
        gameSession.action(SystemClock.uptimeMillis());
        world.shake(sensorX, sensorY, sensorZ);
        invalidate();
    }

    void pauseClock() {simulationClock.reset();}

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
        if(paused())simulationClock.reset();
        float elapsed=paused()?0f:simulationClock.beginFrame(now);
        long gapNs=simulationClock.gapNs();
        // Active playtime excludes long in-foreground idle periods without
        // changing the simulation timer, particles or game-over countdown.
        if(!paused())gameSession.accountFrame(elapsed,SystemClock.uptimeMillis());
        // Fixed-size physics steps keep membrane stiffness independent of FPS.
        // Bound catch-up work: never try to simulate unlimited missed frames.
        long physicsStart=System.nanoTime();
        int steps=paused()?0:simulationClock.advance(world);
        long physicsNs=System.nanoTime()-physicsStart;
        advanceEffects(elapsed);
        if(world.score!=lastAnimatedScore){
            lastAnimatedScore=world.score;
            scorePulse=.33f;
        }
        for (BlobWorld.Merge merge : world.consumeMerges()) {
            // Audio observes the same authoritative fusion directly from BlobWorld.
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
        gameSession.finalizeIfNeeded();
        if(!paused()&&gameSession.checkpointDue())saveCheckpoint();
        long canvasStart=System.nanoTime();
        Canvas canvas = raw;
        canvas.save();
        canvas.translate(offsetX(), 0f);
        canvas.scale(scale, scale);

        drawBackground(canvas, h);
        // Cosmetic whole-vessel wobble. The physical vessel and all collider
        // positions remain untouched; internal floating particles are unchanged.
        canvas.save();
        canvas.translate(210f,GameConsoleLayout.visualBottom(h));
        canvas.scale(visualScale(h),visualScale(h));
        canvas.translate(-210f,-world.bottom());
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
        String diagnostic=audio.diagnosticStatus();
        if(diagnostic!=null)
            label(canvas,diagnostic,210f,100f,10f,theme.text,false);
        if(world.gameOver)drawGameOver(canvas,h);

        canvas.restore();
        long canvasNs=System.nanoTime()-canvasStart;
        long frameNs=System.nanoTime()-now;
        if(perfLogging&&!paused()&&!world.gameOver&&gapNs>0)
            frameProfile.frame(world.blobs.size(),gapNs,physicsNs,canvasNs,frameNs,steps);
        recordPerformance(gapNs,physicsNs,canvasNs,frameNs,steps);
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
        if(gapNs>25_000_000L)perfSlowFrames++;
        if(steps>1)perfCatchupFrames++;
        perfPeakBlobs=Math.max(perfPeakBlobs,world.blobs.size());
        perfWorstPhysicsNs=Math.max(perfWorstPhysicsNs,physicsNs);
        perfWorstCanvasNs=Math.max(perfWorstCanvasNs,canvasNs);
        if(perfFrames<PERF_REPORT_FRAMES)return;
        double fps=perfGapNs==0?0:perfFrames*1e9/(double)perfGapNs;
        double simRate=perfGapNs==0?0:perfSteps*SimulationClock.STEP*1e9/perfGapNs;
        Log.d("LavaPerf",String.format(Locale.US,
                "fps=%.1f worstGap=%.1fms simRate=%.2f physics=%.2fms canvas=%.2fms cpu=%.2fms saves=%d saveAvg=%.1fms saveMax=%.1fms blobs=%d peakBlobs=%d gapsOver25ms=%d catchupFrames=%d physicsMax=%.2fms canvasMax=%.2fms",
                fps,perfWorstGapNs/1e6,simRate,
                perfPhysicsNs/(1e6*perfFrames),perfCanvasNs/(1e6*perfFrames),
                perfCpuNs/(1e6*perfFrames),perfSaveCount,
                perfSaveCount==0?0:perfSaveNs/(1e6*perfSaveCount),
                perfWorstSaveNs/1e6,world.blobs.size(),perfPeakBlobs,perfSlowFrames,perfCatchupFrames,
                perfWorstPhysicsNs/1e6,perfWorstCanvasNs/1e6));
        perfFrames=perfSteps=perfSaveCount=perfSlowFrames=perfCatchupFrames=perfPeakBlobs=0;
        perfGapNs=perfWorstGapNs=perfPhysicsNs=perfCanvasNs=perfCpuNs=0L;
        perfSaveNs=perfWorstSaveNs=perfWorstPhysicsNs=perfWorstCanvasNs=0L;
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
        line.setStrokeJoin(Paint.Join.ROUND);
        c.drawPath(glassPath(),line);
        line.setColor(Palette.alpha(Palette.VESSEL_OUTLINE,112));
        line.setStrokeWidth(1.1f);
        c.drawPath(glassPath(),line);
    }

    /** Bubbly HUD: quiet ivory header, colourful floating counters, spacious play. */
    private void drawHUD(Canvas c,float h) {
        int ink=theme.onSurface,muted=theme.onSurfaceMuted;
        float headerOffset=Math.max(0,Math.min(14,(h-780)*.12f));
        c.save();c.translate(0,headerOffset);
        String place=spec.endless?"INFINITA":spec.trial?"PROVA":"LIVELLO "+(spec.id+1);
        label(c,place,75,36,14,theme.text,true);
        bigNumber(c,spec.name.replace("Infinita · ",""),75,58,14,theme.text,132);
        iconButton(c,176,40,theme.forTier(2),audio.effectsEnabled()?"♪":"×");
        iconButton(c,239,40,theme.forTier(5),"♫");
        iconButton(c,302,40,theme.forTier(4),"?");
        iconButton(c,365,40,theme.forTier(6),"Ⅱ");
        c.restore();
        float y=entryY(h),p=panelTop(h);
        boolean blocked=world.blockedTime>0&&!world.gameOver;
        boolean valid=!aiming||world.canLaunch(aimX);
        int danger=0xFFFFB0A0,entryInk=blocked||blockedFlash>0?danger:Palette.NEUTRAL_SURFACE;
        // Inlet mark follows the actual bottom segment, not the screen center.
        Vessel v=world.vessel;int edge=v.xs.length/2-1;
        line.setStrokeWidth(blocked?7:4);line.setStrokeCap(Paint.Cap.ROUND);line.setColor(entryInk);
        c.drawLine(screenX(v.xs[edge+1]+3,h),screenY(v.bottom,h),screenX(v.xs[edge]-3,h),screenY(v.bottom,h),line);
        line.setStrokeCap(Paint.Cap.BUTT);
        if(aiming){
            line.setColor(Palette.alpha(valid?theme.forTier(4):danger,200));line.setStrokeWidth(2f);
            line.setPathEffect(new android.graphics.DashPathEffect(new float[]{7,11},0));
            c.drawLine(screenX(aimX,h),screenY(v.bottom,h)-8,screenX(aimX,h),screenY(v.top,h)+10,line);line.setPathEffect(null);
            if(valid&&draggingFromTray&&dragY<y-25)drawNextBubble(c,dragX,Math.max(screenY(v.top,h)+12,dragY),.76f);
        }
        int launchFill=theme.forTier(6);
        hudPanel(c,24,y-24,396,y+24,22,blocked?0xFF702F36:launchFill);
        int launchInk=blocked?0xFFFFFFFF:ConsoleUi.ink(launchFill);
        drawNextBubble(c,210,y,Math.min(1f,18f/(Blob.RADII[world.nextTier]*.76f)));
        label(c,blocked?"INGRESSO BLOCCATO":blockedFlash>0?"INGRESSO OCCUPATO":"PROSSIMO",110,y+4,blocked?13:12,launchInk,true);
        label(c,blocked?(int)Math.ceil(world.remainingSeconds())+" s":"TRASCINA",310,y+5,blocked?21:12,launchInk,true);
        if(eraseMode)label(c,"TOCCA IL BLOB DA FAR SCOPPIARE",210,screenY(v.top,h)-10,13,theme.text,true);
        hudPanel(c,0,p,W,h+28,30,Palette.NEUTRAL_SURFACE);
        label(c,"PUNTI",100,panelY(h,23),12,muted,true);
        bigNumber(c,Long.toString(world.score),100,panelY(h,56),29,ink,174);
        label(c,spec.endless?"RECORD":spec.trial?"LANCI":"OBIETTIVO",306,panelY(h,23),12,muted,true);
        String goal=spec.endless?Long.toString(Math.max(best,world.score)):spec.trial?Integer.toString(world.dropCount):Integer.toString(spec.minScore);
        bigNumber(c,goal,306,panelY(h,56),24,ink,174);
        if(!spec.endless&&!spec.trial){
            hudPanel(c,23,panelY(h,69),397,panelY(h,75),3,0xFFC7BBC5);
            float fill=374f*Math.min(1f,(float)world.score/spec.minScore);
            if(fill>0)hudPanel(c,23,panelY(h,69),23+fill,panelY(h,75),3,theme.forTier(4));
            label(c,"Extra "+spec.extras(world.score,world.maxTierReached,world.mergeCount)+"/"+spec.totalExtras(),210,panelY(h,94),12,muted,true);
        }else label(c,spec.endless?"Fusioni "+world.mergeCount:"Fusioni "+world.mergeCount,210,panelY(h,92),12,muted,false);
        int shake=theme.forTier(3),extra=spec.endless?theme.forTier(4):theme.forTier(5);
        hudPanel(c,22,panelY(h,103),204,panelY(h,150),17,shake);
        label(c,"≈   Scuoti",113,panelY(h,132),17,ConsoleUi.ink(shake),true);
        if(spec.endless&&world.eraseCount==0)extra=0xFFC8BFC6;
        hudPanel(c,216,panelY(h,103),398,panelY(h,150),17,extra);
        if(eraseMode){line.setColor(ink);line.setStrokeWidth(2);c.drawRoundRect(216,panelY(h,103),398,panelY(h,150),17,17,line);}
        label(c,spec.endless?"POP  ×"+world.eraseCount:"Extra",307,panelY(h,132),17,ConsoleUi.ink(extra),true);
        label(c,"Taglie",42,panelY(h,166),10,muted,true);
        for(int i=0;i<9;i++){
            paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(theme.forTier(i));
            c.drawCircle(31+44.75f*i,panelY(h,184),5.5f+i*1.38f,paint);
        }
    }
    private void iconButton(Canvas c,float x,float y,int color,String label){
        hudPanel(c,x-25,y-25,x+25,y+25,25,color);
        label(c,label,x,y+8,23,ConsoleUi.ink(color),true);
    }
    private void showSheet(String title,LinearLayout body){
        if(modal!=null||world.gameOver)return;
        aiming=false;draggingFromTray=false;activePointerId=MotionEvent.INVALID_POINTER_ID;uiAction=UI_NONE;
        saveCheckpoint();simulationClock.reset();audio.pause();
        modal=ConsoleUi.sheet(getContext(),title,body);
        modal.setOnDismissListener(dialog->{modal=null;simulationClock.reset();if(!lifecyclePaused)audio.resume();invalidate();});
        modal.show();invalidate();
    }
    private void openHelp(){
        LinearLayout body=ConsoleUi.column(getContext());ConsoleUi.help(body,theme,spec.endless);showSheet("Come si gioca",body);
    }
    private void volume(LinearLayout body,String title,int initial,boolean music){
        TextView value=ConsoleUi.text(getContext(),title+" · "+initial+"%",16);body.addView(value);
        SeekBar seek=new SeekBar(getContext());seek.setMax(100);seek.setProgress(initial);body.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int n,boolean user){
                value.setText(title+" · "+n+"%");
                if(music){musicVolume=n;uiPreferences.edit().putInt("music_volume",n).apply();}
                else audio.setVolumePercent(n);
                invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });
        if(music)body.addView(ConsoleUi.text(getContext(),"Volume predisposto per la colonna sonora, non ancora inclusa.",13));
    }
    private void openVolume(boolean music){
        LinearLayout body=ConsoleUi.column(getContext());volume(body,music?"Musica":"Effetti sonori",music?musicVolume:audio.volumePercent(),music);
        showSheet(music?"Musica":"Suoni",body);
    }
    private void copyFrameProfile(){
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard==null)return;
        String report="Lava Blob "+BuildConfig.VERSION_NAME+" | livello="+spec.id+" | shape="+spec.shape+" | "+getWidth()+"x"+getHeight()+" | device="+android.os.Build.MODEL+" | drops="+world.dropCount+"\n"+frameProfile.report()+world.physicsProfile();
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Lava Blob performance",report));
        android.widget.Toast.makeText(getContext(),"Dati prestazioni copiati",android.widget.Toast.LENGTH_SHORT).show();
    }
    private void openPause(){
        LinearLayout body=ConsoleUi.column(getContext());
        volume(body,"Effetti sonori",audio.volumePercent(),false);volume(body,"Musica",musicVolume,true);
        ConsoleUi.action(body,"Riprendi",theme.forTier(4),()->dismissSheet());
        ConsoleUi.action(body,"Ricomincia",theme.forTier(3),()->{
            if(modal==null)return;
            new AlertDialog.Builder(getContext(),android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("Ricominciare la partita?").setMessage("La partita in corso verrà sostituita.")
                .setNegativeButton("Annulla",null).setPositiveButton("Ricomincia",(d,w)->{dismissSheet();restart();}).show();
        });
        if(perfLogging)ConsoleUi.action(body,"Copia dati prestazioni",theme.forTier(2),this::copyFrameProfile);
        ConsoleUi.action(body,"Torna alla Home",theme.forTier(5),onHome);
        showSheet("Pausa",body);
    }
    private void openExtras(){
        LinearLayout body=ConsoleUi.column(getContext());
        body.addView(ConsoleUi.text(getContext(),"Obiettivo: "+spec.minScore+" punti",17));
        int mask=spec.extrasMask(world.score,world.maxTierReached,world.mergeCount);
        body.addView(ConsoleUi.text(getContext(),((mask&1)!=0?"✓  ":"○  ")+spec.extraScore+" punti",15));
        body.addView(ConsoleUi.text(getContext(),((mask&2)!=0?"✓  ":"○  ")+"Taglia "+spec.goalTier,15));
        if(spec.mergeGoal>0)body.addView(ConsoleUi.text(getContext(),((mask&4)!=0?"✓  ":"○  ")+spec.mergeGoal+" fusioni",15));
        showSheet("Obiettivi extra",body);
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

    private boolean nextAvailable(){return !spec.endless&&!spec.trial&&spec.passed(world.score)&&spec.id+1<GameSpec.LEVELS.length;}
    private void drawGameOver(Canvas c,float h){
        c.drawColor(0x99000000);float t=h*.30f;
        hudPanel(c,25,t,395,t+288,30,Palette.NEUTRAL_SURFACE);
        String heading=spec.trial?"PROVA CONCLUSA":spec.endless?"PARTITA FINITA":spec.passed(world.score)?"LIVELLO SUPERATO":"RIPROVA";
        label(c,heading,210,t+45,22,theme.onSurface,true);
        bigNumber(c,world.score+" punti",210,t+91,32,theme.onSurface,320);
        label(c,world.mergeCount+" fusioni · "+world.dropCount+" lanci",210,t+119,14,theme.onSurfaceMuted,false);
        if(!spec.endless&&!spec.trial)label(c,"Extra "+spec.extras(world.score,world.maxTierReached,world.mergeCount)+"/"+spec.totalExtras(),210,t+144,14,theme.onSurfaceMuted,true);
        int retry=theme.forTier(3),home=theme.forTier(5),next=theme.forTier(4);
        if(nextAvailable()){hudPanel(c,47,t+160,373,t+211,18,next);label(c,"Prossimo livello",210,t+192,17,ConsoleUi.ink(next),true);}
        hudPanel(c,47,t+223,203,t+273,17,retry);label(c,"Riprova",125,t+254,16,ConsoleUi.ink(retry),true);
        hudPanel(c,217,t+223,373,t+273,17,home);label(c,"Home",295,t+254,16,ConsoleUi.ink(home),true);
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

    private int actionAt(float x,float y,float h){
        if(world.gameOver){
            float t=h*.30f;
            if(x>=47&&x<=373&&y>=t+160&&y<=t+211&&nextAvailable())return UI_NEXT;
            if(y>=t+223&&y<=t+273){if(x>=47&&x<=203)return UI_RETRY;if(x>=217&&x<=373)return UI_HOME;}
            return UI_NONE;
        }
        float headerOffset=Math.max(0,Math.min(14,(h-780)*.12f));
        if(y>=15+headerOffset&&y<=65+headerOffset){
            if(x>=151&&x<=201)return UI_SOUND;
            if(x>=214&&x<=264)return UI_MUSIC;
            if(x>=277&&x<=327)return UI_HELP;
            if(x>=340&&x<=390)return UI_PAUSE;
        }
        float p=panelTop(h);
        if(y>=panelY(h,103)&&y<=panelY(h,150)){if(x>=22&&x<=204)return UI_SHAKE;if(x>=216&&x<=398)return UI_EXTRA;}
        return UI_NONE;
    }
    private void doAction(int a){
        switch(a){
            case UI_SOUND:openVolume(false);break;
            case UI_MUSIC:openVolume(true);break;
            case UI_HELP:openHelp();break;
            case UI_PAUSE:openPause();break;
            case UI_SHAKE:
                world.shake(8,-5,8);shakeAge=0;shakeStep=0;shakePress=1;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);saveCheckpoint();break;
            case UI_EXTRA:
                if(spec.endless){if(world.eraseCount>0){eraseMode=!eraseMode;popPress=1;}}else if(!spec.trial)openExtras();
                break;
            case UI_RETRY:restart();break;
            case UI_HOME:onHome.run();break;
            case UI_NEXT:if(nextAvailable())onNext.run();break;
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(getWidth()==0||getHeight()==0||paused())return false;
        float sc=scale(),h=getHeight()/sc;
        float x=(event.getX()-offsetX())/sc,y=event.getY()/sc;
        float wx=worldX(x,h),wy=worldY(y,h);
        boolean inVessel=wy>=world.vessel.top-5&&wy<=world.bottom()+5&&wx>=BlobWorld.LEFT&&wx<=BlobWorld.RIGHT;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                gameSession.action(SystemClock.uptimeMillis());activePointerId=event.getPointerId(0);
                touchX=x;touchY=y;uiAction=actionAt(x,y,h);
                aiming=false;draggingFromTray=false;
                if(uiAction!=UI_NONE||world.gameOver)return true;
                if(eraseMode)return true;
                boolean tray=x>=24&&x<=396&&Math.abs(y-entryY(h))<=24;
                if(tray||inVessel){
                    aiming=true;draggingFromTray=tray;dragX=x;dragY=y;
                    aimX=BlobWorld.clamp(tray?210:wx,49,371);invalidate();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if(aiming){
                    int pointer=event.findPointerIndex(activePointerId);if(pointer>=0){
                        dragX=(event.getX(pointer)-offsetX())/sc;dragY=event.getY(pointer)/sc;
                        aimX=BlobWorld.clamp(worldX(dragX,h),49,371);gameSession.action(SystemClock.uptimeMillis());invalidate();
                    }
                }return true;
            case MotionEvent.ACTION_POINTER_UP:
                if(event.getPointerId(event.getActionIndex())==activePointerId){aiming=false;draggingFromTray=false;uiAction=UI_NONE;activePointerId=MotionEvent.INVALID_POINTER_ID;invalidate();}
                return true;
            case MotionEvent.ACTION_UP:
                if(activePointerId==MotionEvent.INVALID_POINTER_ID)return true;
                activePointerId=MotionEvent.INVALID_POINTER_ID;
                int a=uiAction;uiAction=UI_NONE;
                if(a!=UI_NONE){if(a==actionAt(x,y,h)&&Math.abs(y-touchY)<25)doAction(a);invalidate();return true;}
                if(world.gameOver)return true;
                if(eraseMode){
                    if(inVessel&&Math.abs(y-touchY)<20&&Math.abs(x-touchX)<20&&world.eraseAt(wx,wy)){
                        pops.add(new PopFx(world.lastPoppedX,world.lastPoppedY,world.lastPoppedRadius,world.lastPoppedTier));
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);popPress=1;eraseMode=false;saveCheckpoint();
                    }
                    invalidate();return true;
                }
                if(aiming&&inVessel){
                    aimX=BlobWorld.clamp(wx,49,371);
                    if(world.launch(aimX)){performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);saveCheckpoint();}
                    else blockedFlash=.9f;
                }
                aiming=false;draggingFromTray=false;invalidate();return true;
            case MotionEvent.ACTION_CANCEL:
                aiming=false;draggingFromTray=false;uiAction=UI_NONE;activePointerId=MotionEvent.INVALID_POINTER_ID;invalidate();return true;
            default:return true;
        }
    }

    private void restart() {
        gameSession.restart(SystemClock.uptimeMillis());
        waves.clear();droplets.clear();pops.clear();
        blockedFlash=0f;eraseMode=false;aiming=false;draggingFromTray=false;draggingVolume=false;
        activePointerId=MotionEvent.INVALID_POINTER_ID;
        shakeAge=1f;shakePress=popPress=0f;shakeStep=0;scorePulse=0f;lastAnimatedScore=0L;
        aimX=W*.5f;simulationClock.reset();
        saveCheckpoint();
        invalidate();
    }
}
