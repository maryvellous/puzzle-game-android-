package com.maryvellous.lavablobs;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;
import android.util.Log;

import com.maryvellous.lavablobs.audio.BloopMaterialBank;
import com.maryvellous.lavablobs.audio.MaterialAudioEngine;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Audio V2 gameplay bridge ONLY for the standalone .audiov2 variant.
 *
 * The UI/physics thread observes real membranes and puts simple commands in a
 * bounded queue. The worker loads the FOUR original MP3s and drives the exact
 * same pure Java MaterialAudioEngine tested offline, on ONE audio thread.
 *
 * AudioTrack STREAM PCM16 (no network/service/NDK). No MP3 decoding, sample
 * allocation or Android calls occur in MaterialAudioEngine.renderMono().
 */
final class V2GameSound implements GameSound, ContactProbe.Listener {
    private static final String TAG="LavaV2Audio";
    private static final String PREFS="lava_blobs";
    private static final String EFFECTS_KEY="sound_effects_enabled_v1";
    private static final int RATE=BloopMaterialBank.SAMPLE_RATE;
    private static final int BLOCK_FRAMES=384; // ~8.7 ms; device buffers may be larger.
    private static final int CAPACITY=8192;

    private static final int CONTACT=1,END=2,CANCEL=3,MERGE=4,LAUNCH=5,POP=6,RESET=7;
    private static final class Command {
        final int type,a,b;
        final float normal,slip,compression,radiusA,radiusB,dt,bulkMotion;
        Command(int type,int a,int b,float normal,float slip,
                float compression,float radiusA,float radiusB){
            this(type,a,b,normal,slip,compression,radiusA,radiusB,1f/120f,0f);
        }
        Command(int type,int a,int b,float normal,float slip,
                float compression,float radiusA,float radiusB,float dt,
                float bulkMotion){
            this.type=type;this.a=a;this.b=b;this.normal=normal;this.slip=slip;
            this.compression=compression;this.radiusA=radiusA;
            this.radiusB=radiusB;this.dt=dt;this.bulkMotion=bulkMotion;
        }
    }

    private final Context app;
    private final SharedPreferences preferences;
    private final ContactTracker tracker;
    private final ArrayBlockingQueue<Command> queue=new ArrayBlockingQueue<>(CAPACITY);
    private final Object lifecycle=new Object();
    private final Thread audioThread;
    private volatile boolean enabled,paused,closed,ready;
    private volatile AudioTrack track;
    private volatile String state="CARICAMENTO";
    private volatile int droppedContacts;
    private long latestReportMs;
    private int blocksSinceReport;
    private long renderNsSinceReport,worstRenderNs;
    private int underrunBaseline;

    V2GameSound(Context context) {
        app=context.getApplicationContext();
        preferences=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        enabled=preferences.getBoolean(EFFECTS_KEY,true);
        tracker=new ContactTracker(this::onTransition);
        audioThread=new Thread(this::runAudio,"LavaV2-PCM");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    @Override public boolean effectsEnabled(){return enabled;}

    @Override public void setEffectsEnabled(boolean next) {
        if(closed||enabled==next)return;
        enabled=next;
        preferences.edit().putBoolean(EFFECTS_KEY,next).apply();
        // Muting also stops already-playing complete Bloops by user choice;
        // that is distinct from voice stealing during ordinary gameplay.
        if(!next){
            queue.clear();
            AudioTrack playing=track;
            if(playing!=null)try{playing.pause();playing.flush();}catch(Exception ignored){}
        }
        synchronized(lifecycle){lifecycle.notifyAll();}
    }

    @Override public void merge(BlobWorld.Merge merge) {
        send(new Command(MERGE,merge.parentAId,merge.parentBId,
                0,0,0,merge.radius,0));
    }
    @Override public void launch(int tier,float radius) {
        send(new Command(LAUNCH,0,0,0,0,0,radius,0));
    }
    @Override public void pop(int blobId,float radius) {
        send(new Command(POP,blobId,0,0,0,0,radius,0));
    }

    /** V2 never plays the legacy strongest-impact-per-frame SoundPool event. */
    @Override public void discardLegacyImpacts(){}

    @Override public void onContactStep(float time,float dt,List<ContactProbe.Contact> contacts) {
        for(ContactProbe.Contact c:contacts){
            send(new Command(CONTACT,c.blobA,c.blobB,c.approachSpeed,
                    c.slideSpeed,c.compression,c.radiusA,c.radiusB,
                    dt,c.bulkMotion));
        }
        tracker.onContactStep(time,dt,contacts);
    }

    private void onTransition(ContactTracker.Transition transition) {
        if(transition.kind==ContactTracker.Kind.END)
            send(new Command(END,transition.blobA,transition.blobB,0,0,0,0,0));
        else if(transition.kind==ContactTracker.Kind.CANCELLED)
            send(new Command(CANCEL,transition.blobA,transition.blobB,0,0,0,0,0));
    }

    @Override public void onMerge(BlobWorld.Merge merge){
        // Actual full fusion is sent exactly once from LavaView's merge event.
        // Cancel phantom contact tails immediately at the physics source.
        tracker.onMerge(merge);
    }
    @Override public void onPop(int blobId,float simulationTime){tracker.onPop(blobId,simulationTime);}
    @Override public void onReset(){tracker.reset();reset();}

    @Override public void reset() {
        if(!closed){
            queue.clear();
            send(new Command(RESET,0,0,0,0,0,0,0));
        }
    }

    private void send(Command command) {
        if(closed||!enabled||!ready)return;
        if(queue.offer(command))return;
        if(command.type==CONTACT) {
            droppedContacts++;
        }else{
            // Critical fusion/POP/mute reset must not be dropped behind
            // stale contact observations in a busy scene.
            queue.poll();
            if(!queue.offer(command))Log.w(TAG,"Critical audio command overflow");
        }
    }

    @Override public void pause() {
        paused=true;
        queue.clear();
        AudioTrack playing=track;
        if(playing!=null)try{playing.pause();playing.flush();}catch(Exception ignored){}
        synchronized(lifecycle){lifecycle.notifyAll();}
    }
    @Override public void resume() {
        paused=false;
        synchronized(lifecycle){lifecycle.notifyAll();}
    }
    @Override public String diagnosticStatus() {
        if(!enabled)return "AUDIO V2 · MUTO";
        if(paused)return "AUDIO V2 · PAUSA";
        return "AUDIO V2 · "+state+(droppedContacts>0?" · Q:"+droppedContacts:"");
    }
    @Override public void release() {
        if(closed)return;
        closed=true;ready=false;
        queue.clear();
        AudioTrack playing=track;
        if(playing!=null)try{playing.pause();playing.flush();}catch(Exception ignored){}
        synchronized(lifecycle){lifecycle.notifyAll();}
        audioThread.interrupt();
    }

    private static AudioTrack createTrack() {
        int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if(min<=0)throw new IllegalStateException("Unsupported AudioTrack output");
        int bufferBytes=Math.max(min,BLOCK_FRAMES*2*4);
        AudioTrack created=new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build();
        if(created.getState()!=AudioTrack.STATE_INITIALIZED){
            created.release();
            throw new IllegalStateException("AudioTrack initialization failed");
        }
        Log.i(TAG,"PCM 44100 mono16, buffer="+created.getBufferSizeInFrames()+
                " frames, performanceMode="+created.getPerformanceMode());
        return created;
    }

    private void process(MaterialAudioEngine engine,Command c) {
        switch(c.type){
            case CONTACT:
                engine.contact(c.a,c.b,c.normal,c.slip,c.compression,
                        c.radiusA,c.radiusB,c.dt,c.bulkMotion);
                break;
            case END:
                engine.endContact(c.a,c.b);
                break;
            case CANCEL:
                engine.endContact(c.a,c.b);
                break;
            case MERGE:
                engine.merge(c.a,c.b,c.radiusA);
                break;
            case LAUNCH:
                engine.launch(c.radiusA);
                break;
            case POP:
                engine.pop(c.a,c.radiusA);
                break;
            case RESET:
                engine.reset();
                break;
            default:throw new IllegalStateException("Unknown audio command");
        }
    }

    private void runAudio(){
        AudioTrack device=null;
        try{
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            // All expensive MP3 decoding and acoustic segmentation occur here.
            BloopMaterialBank materials=AndroidBloopDecoder.load(app);
            if(closed)return;
            MaterialAudioEngine engine=new MaterialAudioEngine(materials);
            device=createTrack();
            track=device;
            if(closed)return;
            queue.clear();
            ready=true;
            state="PRONTO";
            float[] floatBlock=new float[BLOCK_FRAMES];
            short[] shortBlock=new short[BLOCK_FRAMES];
            boolean playing=false;
            while(!closed){
                if(paused||!enabled){
                    if(playing){
                        device.pause();
                        device.flush();
                        playing=false;
                    }
                    engine.reset();
                    queue.clear();
                    synchronized(lifecycle){
                        while((paused||!enabled)&&!closed)
                            lifecycle.wait(200);
                    }
                    continue;
                }
                if(!playing){device.play();playing=true;}
                // Single owner for all engine mutations, never concurrent
                // with Android Canvas or the physics step.
                Command cmd;
                int processed=0;
                while((cmd=queue.poll())!=null){
                    process(engine,cmd);
                    if(++processed>=CAPACITY)break;
                }
                long started=System.nanoTime();
                engine.renderMono(floatBlock,0,BLOCK_FRAMES);
                long spent=System.nanoTime()-started;
                worstRenderNs=Math.max(worstRenderNs,spent);
                renderNsSinceReport+=spent;
                blocksSinceReport++;
                for(int i=0;i<BLOCK_FRAMES;i++){
                    float sample=Math.max(-1f,Math.min(1f,floatBlock[i]));
                    shortBlock[i]=(short)Math.round(sample*32767f);
                }
                int written=0;
                while(written<BLOCK_FRAMES&&!closed&&!paused&&enabled){
                    int n=device.write(shortBlock,written,BLOCK_FRAMES-written,
                            AudioTrack.WRITE_BLOCKING);
                    if(n<0)throw new IllegalStateException("PCM write error "+n);
                    if(n==0)break;
                    written+=n;
                }
                long now=android.os.SystemClock.elapsedRealtime();
                if(now-latestReportMs>=5000){
                    Log.d(TAG,"audioBlockAvg="+(blocksSinceReport==0?0:
                            renderNsSinceReport/(blocksSinceReport*1_000_000.0))+
                            "ms worst="+worstRenderNs/1_000_000.0+
                            "ms underruns="+device.getUnderrunCount()+
                            " active="+engine.activePairs()+" fullFusions="+
                            engine.fusionsCompleted()+" peak="+engine.peakOutput()+
                            " squishes="+engine.squishEvents()+
                            " impacts="+engine.impactEvents()+
                            " dropped="+droppedContacts);
                    latestReportMs=now;
                    blocksSinceReport=0;renderNsSinceReport=0;worstRenderNs=0;
                }
            }
        }catch(InterruptedException ignored){
            Thread.currentThread().interrupt();
        }catch(Exception failure){
            state="ERRORE";
            Log.e(TAG,"Audio V2 unavailable (gameplay can continue silently)",failure);
        }finally{
            ready=false;
            track=null;
            if(device!=null){
                try{device.pause();}catch(Exception ignored){}
                try{device.flush();}catch(Exception ignored){}
                try{device.release();}catch(Exception ignored){}
            }
        }
    }
}
