package com.maryvellous.lavablobs;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.Log;
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
    // Solid, saturated, intentionally distinguishable hues from blue-indigo
    // through lavender and violet, never red/yellow/green.
    private final Palette.Theme theme;
    private final GameSpec spec;
    private final ProgressStore progress;
    private final Runnable onHome;
    private final GameSave session;
    private float lastCheckpoint;
    private final BlobWorld world;
    private final Random random = new Random();
    private final SharedPreferences prefs;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letters = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path blobPath = new Path();
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

    private float aimX = W / 2;
    private long previousFrame;
    private float physicsAccumulator;
    private int perfFrames,perfSteps,perfSaveCount;
    private long perfGapNs,perfWorstGapNs,perfPhysicsNs,perfCanvasNs,perfCpuNs;
    private long perfSaveNs,perfWorstSaveNs;
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

    LavaView(Context context, GameSpec spec, ProgressStore progress,
            Runnable onHome,GameSave session,GameSave.Snapshot resume) {
        super(context);
        this.spec=spec;this.progress=progress;this.onHome=onHome;
        this.session=session;
        theme=new ThemeStore(context).current();
        perfLogging=(context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        world=new BlobWorld(new Random(),spec);
        if(resume!=null) {
            try {
                GameSave.restore(world,resume.data);
                aimX=(float)resume.data.optDouble("aimX",W/2);
                eraseMode=world.eraseCount>0&&resume.data.optBoolean("eraseMode",false);
                lastCheckpoint=world.time;
            }catch(Exception ignored){
                // A corrupted save must not crash startup. Keep the old slot
                // until the next successful checkpoint replaces it.
                world.reset();
            }
        }
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setHapticFeedbackEnabled(true);
        prefs=context.getSharedPreferences("lava_blobs",Context.MODE_PRIVATE);
        best=progress.record(spec.id);
        letters.setTextAlign(Paint.Align.CENTER);
        line.setStyle(Paint.Style.STROKE);
    }

    /** Preserve game without counting a pause as a completed playtest. */
    void leave() { saveCheckpoint(); }

    void saveCheckpoint() {
        if(world.gameOver) {session.clear();return;}
        long started=perfLogging?System.nanoTime():0L;
        boolean saved=session.save(spec,world,aimX,eraseMode);
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
        advanceEffects(elapsed);
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
        line.setColor(Palette.VESSEL_OUTLINE);
        line.setStrokeWidth(4.2f);
        c.drawPath(glassPath(),line);
        line.setColor(Palette.alpha(Palette.VESSEL_OUTLINE,112));
        line.setStrokeWidth(1.1f);
        c.drawPath(glassPath(),line);
    }

    /** In-game layout from the fourth Canva board, with colours from Theme. */
    private void drawHUD(Canvas c,float h) {
        BlobTitle.draw(c,letters,paint,titleGlyph,titleWarped,theme,
                (System.nanoTime()-titleStart)/1_000_000_000f,35f,61f);
        String location=spec.endless?"Modalità infinita":
                spec.trial?spec.name:"Livello "+(spec.id+1)+" · "+spec.name;
        label(c,location,210,87,11,theme.muted,false);

        // Controls are now on opposite sides of the shared animated title.
        hudPanel(c,29,32,75,76,18,theme.surfaceRaised);
        hudPanel(c,345,32,391,76,18,theme.surfaceRaised);
        label(c,"↻",52,63,26,theme.text,false);
        label(c,"⌂",368,62,25,theme.text,false);

        // Canva page 4: two large, clearly separated counters.
        int scoreFill=theme.forTier(0);
        int goalFill=theme.primary;
        hudPanel(c,29,109,195,169,22,scoreFill);
        hudPanel(c,225,109,391,169,22,goalFill);
        int scoreInk=contrastInk(scoreFill);
        int goalInk=contrastInk(goalFill);
        label(c,"PUNTI",112,130,11,scoreInk,true);
        label(c,Long.toString(world.score),112,155,23,scoreInk,true);
        String heading=spec.endless?"RECORD":spec.trial?"DROP":"OBIETTIVO";
        String figure=spec.endless?Long.toString(Math.max(best,world.score)):
                spec.trial?Integer.toString(world.dropCount):
                Integer.toString(spec.minScore);
        label(c,heading,308,130,11,goalInk,true);
        label(c,figure,308,155,23,goalInk,true);

        if(!spec.endless&&!spec.trial)
            label(c,"EXTRA "+spec.extras(world.score,world.maxTierReached,
                    world.mergeCount)+"/"+spec.totalExtras(),
                    308,186,10.5f,theme.secondary,true);

        if(world.blockedTime>.5f&&!world.gameOver) {
            label(c,"INGRESSO BLOCCATO · "+(int)Math.ceil(world.remainingSeconds())+"s",
                    210,211,11.5f,theme.caution,true);
            if(spec.endless&&world.eraseCount>0)
                label(c,"Usa Erase per liberare spazio",210,228,10.5f,theme.muted,false);
        }
        if(eraseMode)
            label(c,"TOCCA IL BLOB DA ELIMINARE",210,229,11f,theme.caution,true);

        // Narrow floating next-blob tray; both footer actions remain visible
        // on short displays even when a taller vessel is being played.
        float controlsTop=h-62f;
        float deckTop=world.bottom()+13f;
        float deckBottom=Math.max(deckTop+63f,controlsTop-9f);
        float deckHeight=deckBottom-deckTop;
        if(aiming) {
            line.setColor(Palette.alpha(theme.secondary,150));
            line.setStrokeWidth(1.4f);
            line.setPathEffect(new android.graphics.DashPathEffect(new float[]{7,11},0f));
            c.drawLine(aimX,world.bottom()-8,aimX,world.vessel.top+10,line);
            line.setPathEffect(null);
        }
        hudPanel(c,100,deckTop,320,deckBottom,29,theme.surfaceRaised);
        float previewY=deckTop+Math.min(35f,deckHeight*.45f);
        drawNextBubble(c,aimX,previewY,Math.max(14f,Math.min(1f,deckHeight/105f)));
        label(c,"TRASCINA E RILASCIA",210,deckBottom-11f,10f,theme.muted,true);

        // The purple buttons from the mockup: shaking remains available
        // through the accelerometer; tapping the left button is an alternative.
        int shakeFill=theme.tertiary;
        int eraseFill=world.eraseCount>0&&spec.endless?
                (eraseMode?theme.tertiary:theme.secondary):theme.surface;
        hudPanel(c,32,controlsTop,193,h-10f,22,shakeFill);
        hudPanel(c,227,controlsTop,388,h-10f,22,eraseFill);
        label(c,"SCUOTI",112.5f,controlsTop+33,15,contrastInk(shakeFill),true);
        String eraseText=spec.endless?"ERASE · "+world.eraseCount:"ERASE";
        label(c,eraseText,307.5f,controlsTop+33,15,
                world.eraseCount>0&&spec.endless?contrastInk(eraseFill):theme.muted,true);

        if(blockedFlash>0f)
            label(c,"QUESTO INGRESSO È OCCUPATO",210,world.bottom()+8f,11,
                    theme.caution,true);
    }

    private int contrastInk(int color) {
        return Palette.relativeLuminance(color)>.43f ? 0xFF211A26 : 0xFFF7F1F3;
    }

    private void hudPanel(Canvas c,float l,float t,float r,float b,float radius,int fill){
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(fill);
        c.drawRoundRect(l,t,r,b,radius,radius,paint);
        line.setColor(Palette.mix(fill,theme.border,.58f));
        line.setStrokeWidth(1f);
        c.drawRoundRect(l,t,r,b,radius,radius,line);
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
        label(c,title,210,top+57,21,theme.text,true);
        label(c,world.score+" punti  ·  "+world.dropCount+" drop",
                210,top+99,17,theme.muted,false);
        if(!spec.endless&&!spec.trial)
            label(c,"Extra "+spec.extras(world.score,world.maxTierReached,
                    world.mergeCount)+"/"+spec.totalExtras(),
                    210,top+125,13,theme.secondary,false);

        float buttonsTop=h*.585f,buttonsBottom=h*.664f;
        hudPanel(c,55,buttonsTop,198,buttonsBottom,15,theme.primary);
        hudPanel(c,221,buttonsTop,365,buttonsBottom,15,theme.surface);
        label(c,"RIPROVA",127,h*.633f,13,theme.onPrimary,true);
        label(c,"HOME",293,h*.633f,13,theme.text,true);
    }

    private void label(Canvas c, String value, float x, float y, float size,
                       int color, boolean bold) {
        letters.setShader(null);
        letters.setColor(color);
        letters.setTextSize(size);
        letters.setTypeface(bold?FONT_MEDIUM:FONT_REGULAR);
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
                if(y>32f&&y<79f&&x>342f){onHome.run();return true;}
                if(y>32f&&y<79f&&x<80f){restart();return true;}
                float controlsTop=h-62f;
                if(y>=controlsTop&&y<=h&&x>=227f) {
                    if(spec.endless&&world.eraseCount>0)
                        eraseMode=!eraseMode;
                    invalidate();return true;
                }
                if(y>=controlsTop&&y<=h&&x<=193f) {
                    if(!world.gameOver) {
                        world.shake(5f,-6f,9f);
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        saveCheckpoint();
                    }
                    invalidate();return true;
                }
                if(eraseMode){
                    if(world.eraseAt(x,y)){
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        eraseMode=false;
                        saveCheckpoint();
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
                    if(world.launch(aimX)) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        saveCheckpoint();
                    } else blockedFlash=.9f;
                }
                aiming=false;return true;
            case MotionEvent.ACTION_CANCEL:
                aiming=false;return true;
            default:return true;
        }
    }

    private void restart() {
        if(world.dropCount>0&&!world.gameOver)progress.save(spec,world,false);
        session.clear();
        world.reset();
        waves.clear();droplets.clear();
        blockedFlash=0f;eraseMode=false;aiming=false;
        aimX=W*.5f;previousFrame=0;finalized=false;lastCheckpoint=0f;
        saveCheckpoint();
        invalidate();
    }
}
