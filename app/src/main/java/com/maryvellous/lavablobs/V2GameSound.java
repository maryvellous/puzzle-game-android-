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
import com.maryvellous.lavablobs.audio.AudioEventQueue;
import com.maryvellous.lavablobs.audio.AudioTimeline;
import com.maryvellous.lavablobs.audio.AudioEvent;
import com.maryvellous.lavablobs.audio.AudioBlockScheduler;


/**
 * Audio V2.3 gameplay bridge for the main app and isolated preview.
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
    private static final String VOLUME_KEY="sound_effects_volume_v2";
    private static final String LAST_VOLUME_KEY="sound_effects_last_nonzero_v2";
    private static final int RATE=BloopMaterialBank.SAMPLE_RATE;
    private static final int BLOCK_FRAMES=384; // ~8.7 ms; device buffers may be larger.
    private static final int CAPACITY=8192;
    private static final int MAX_DISPATCH_PER_BLOCK=512;

    private final Context app;
    private final boolean diagnosticBuild;
    private final SharedPreferences preferences;
    private final PhysicsAudioEvents physicsEvents;
    private final AudioEventQueue<AudioEvent> queue=new AudioEventQueue<>(CAPACITY,1024);
    private volatile int generation;
    private volatile boolean transportFault;
    private final Object lifecycle=new Object();
    private final Thread audioThread;
    private volatile boolean paused,closed,ready;
    private volatile int volumePercent;
    private volatile int muteEpoch;
    private int lastNonzeroVolume;
    private volatile String state="CARICAMENTO";
    private long maxWriteNs;
    private volatile long startupDiscarded;
    private long latestReportMs;
    private int blocksSinceReport;
    private long renderNsSinceReport,worstRenderNs;
    private int underrunBaseline;

    V2GameSound(Context context) {
        app=context.getApplicationContext();
        diagnosticBuild=app.getPackageName().endsWith(".audiov21");
        preferences=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        volumePercent=Math.max(0,Math.min(100,preferences.getInt(VOLUME_KEY,
                preferences.getBoolean(EFFECTS_KEY,true)?100:0)));
        lastNonzeroVolume=Math.max(1,Math.min(100,preferences.getInt(LAST_VOLUME_KEY,
                volumePercent>0?volumePercent:100)));
        physicsEvents=new PhysicsAudioEvents(this::send,()->generation,System::nanoTime,this::reset);
        audioThread=new Thread(this::runAudio,"LavaV2-PCM");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    @Override public boolean effectsEnabled(){return volumePercent>0;}
    @Override public boolean supportsVolumeSlider(){return true;}
    @Override public int volumePercent(){return volumePercent;}
    @Override public void setEffectsEnabled(boolean next) {
        if(next==effectsEnabled())return;
        setVolumePercent(next?lastNonzeroVolume:0);
    }

    @Override public void setVolumePercent(int percent) {
        int next=Math.max(0,Math.min(100,percent));
        if(closed||volumePercent==next)return;
        volumePercent=next;
        if(next>0)lastNonzeroVolume=next;
        preferences.edit().putInt(VOLUME_KEY,next)
                .putInt(LAST_VOLUME_KEY,lastNonzeroVolume)
                .putBoolean(EFFECTS_KEY,next>0).apply();
        if(next==0){
            advanceGeneration();
            muteEpoch++;
            // The worker owns pause/flush, even if the thumb immediately
            // leaves zero. Avoid pausing AudioTrack behind its back.
        }
        synchronized(lifecycle){lifecycle.notifyAll();}
    }

    // The authoritative source is the simulation, never the View.
    @Override public void merge(BlobWorld.Merge merge) {}
    @Override public void launch(int tier,float radius) {}
    @Override public void pop(int blobId,float radius) {}
    @Override public void discardLegacyImpacts(){}
    @Override public void onContactStep(float time,float dt,List<ContactProbe.Contact> contacts){
        physicsEvents.onContactStep(time,dt,contacts);
    }
    @Override public void onWallImpact(int blobId,int side,float approach,float radius,float time){
        physicsEvents.onWallImpact(blobId,side,approach,radius,time);
    }
    @Override public void onMerge(BlobWorld.Merge merge){physicsEvents.onMerge(merge);}
    @Override public void onLaunch(int tier,float radius,float time){physicsEvents.onLaunch(tier,radius,time);}
    @Override public void onPop(int blobId,float time,float radius){physicsEvents.onPop(blobId,time,radius);}
    @Override public void onReset(){physicsEvents.onReset();}

    private void advanceGeneration() {
        generation++;
        queue.clear();
        transportFault=false;
    }
    @Override public void reset() {
        if(!closed)advanceGeneration();
    }
    private void send(AudioEvent event) {
        if(closed||paused||!effectsEnabled()||transportFault)return;
        if(!ready){startupDiscarded++;return;}
        if(!queue.offer(event,event.authoritative())&&event.authoritative()){
            // Finite capacity cannot promise infinite delivery: fail visibly
            // and reset rather than silently evicting an accepted fusion/END.
            transportFault=true;
            generation++;
            queue.clear();
            Log.e(TAG,"Authoritative audio overflow; audio suspended until lifecycle reset");
        }
    }
    @Override public void pause() {
        paused=true;
        advanceGeneration();
        synchronized(lifecycle){lifecycle.notifyAll();}
    }
    @Override public void resume() {
        paused=false;
        synchronized(lifecycle){lifecycle.notifyAll();}
    }
    @Override public String diagnosticStatus() {
        if(!diagnosticBuild)return null;
        if(transportFault)return "AUDIO V2 · ERRORE CODA";
        if(!effectsEnabled())return "AUDIO V2 · MUTO";
        if(paused)return "AUDIO V2 · PAUSA";
        return "AUDIO V2 · "+state+(queue.rejectedObservations()>0?" · Q:"+queue.rejectedObservations():"");
    }
    @Override public void release() {
        if(closed)return;
        closed=true;ready=false;
        advanceGeneration();
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

    private void runAudio(){
        AudioTrack device=null;
        try{
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            // All expensive MP3 decoding and acoustic segmentation occur here.
            BloopMaterialBank materials=AndroidBloopDecoder.load(app);
            if(closed)return;
            float[] releaseSwoosh=null;
            try{releaseSwoosh=AndroidBloopDecoder.loadLaunch(app);}
            catch(InterruptedException interrupted){throw interrupted;}
            catch(Exception failure){Log.w(TAG,"Classic release unavailable; using material attack",failure);}
            MaterialAudioEngine engine=new MaterialAudioEngine(materials,releaseSwoosh);
            engine.setOutputVolumePercent(volumePercent);
            engine.reset();
            device=createTrack();
            if(closed)return;
            queue.clear();
            ready=true;
            state="PRONTO";
            float[] floatBlock=new float[BLOCK_FRAMES];
            short[] shortBlock=new short[BLOCK_FRAMES];
            boolean playing=false;
            int seenMuteEpoch=muteEpoch;
            int seenGeneration=generation;
            AudioTimeline timeline=new AudioTimeline(RATE,BLOCK_FRAMES,Math.round(RATE*.12f));
            AudioBlockScheduler scheduler=new AudioBlockScheduler(queue,timeline,MAX_DISPATCH_PER_BLOCK);
            java.util.function.IntSupplier generationSource=()->generation;
            underrunBaseline=device.getUnderrunCount();
            while(!closed){
                if(seenGeneration!=generation){
                    seenGeneration=generation;
                    if(playing){device.pause();device.flush();playing=false;}
                    engine.reset();scheduler.reset();
                }
                if(seenMuteEpoch!=muteEpoch){
                    seenMuteEpoch=muteEpoch;
                    if(playing){device.pause();device.flush();playing=false;}
                    engine.setOutputVolumePercent(0);engine.reset();
                }
                if(paused||!effectsEnabled()||transportFault){
                    if(playing){
                        device.pause();
                        device.flush();
                        playing=false;
                    }
                    engine.reset();
                    synchronized(lifecycle){
                        while((paused||!effectsEnabled()||transportFault)&&!closed)
                            lifecycle.wait(200);
                    }
                    continue;
                }
                if(!playing){device.play();playing=true;}
                long started=System.nanoTime();
                engine.setOutputVolumePercent(volumePercent);
                if(!scheduler.render(engine,floatBlock,seenGeneration,generationSource))continue;
                long spent=System.nanoTime()-started;
                worstRenderNs=Math.max(worstRenderNs,spent);
                renderNsSinceReport+=spent;
                blocksSinceReport++;
                for(int i=0;i<BLOCK_FRAMES;i++){
                    float sample=Math.max(-1f,Math.min(1f,floatBlock[i]));
                    shortBlock[i]=(short)Math.round(sample*32767f);
                }
                int written=0;
                long writeStart=System.nanoTime();
                while(written<BLOCK_FRAMES&&!closed&&!paused&&effectsEnabled()&&seenGeneration==generation){
                    int n=device.write(shortBlock,written,BLOCK_FRAMES-written,
                            AudioTrack.WRITE_BLOCKING);
                    if(n<0)throw new IllegalStateException("PCM write error "+n);
                    if(n==0)throw new IllegalStateException("Blocking PCM write made no progress");
                    written+=n;
                }
                maxWriteNs=Math.max(maxWriteNs,System.nanoTime()-writeStart);
                long now=android.os.SystemClock.elapsedRealtime();
                if(now-latestReportMs>=5000){
                    Log.d(TAG,"audioBlockAvg="+(blocksSinceReport==0?0:
                            renderNsSinceReport/(blocksSinceReport*1_000_000.0))+
                            "ms worst="+worstRenderNs/1_000_000.0+
                            "ms underrunsDelta="+(device.getUnderrunCount()-underrunBaseline)+
                            " active="+engine.activePairs()+" fullFusions="+
                            engine.fusionsCompleted()+" peak="+engine.peakOutput()+
                            " squishes="+engine.squishEvents()+
                            " impacts="+engine.impactEvents()+
                            " droppedObs="+queue.rejectedObservations()+" rejectedControl="+queue.rejectedControls()+
                            " queueDepth="+queue.size()+" queueHigh="+queue.highWater()+
                            " maxQueueAgeMs="+scheduler.maxQueueAgeNs()/1e6+" maxDispatchMs="+scheduler.maxDispatchNs()/1e6+
                            " maxWriteMs="+maxWriteNs/1e6+" dispatched="+scheduler.dispatched()+
                            " lateEvents="+scheduler.lateEvents()+" rebases="+scheduler.rebases()+
                            " dispatchBudgetHits="+scheduler.budgetHits()+" startupDiscarded="+startupDiscarded+
                            " peakDry="+engine.peakDry()+" minLimiterGain="+engine.minimumLimiterGain()+
                            " limitedFrames="+engine.limitedFrames()+" renderedFrames="+engine.renderedFrames()+
                            " peakVoices="+engine.peakVoices());
                    latestReportMs=now;
                    blocksSinceReport=0;renderNsSinceReport=0;worstRenderNs=0;
                    scheduler.resetIntervalMaxima();maxWriteNs=0;underrunBaseline=device.getUnderrunCount();
                }
            }
        }catch(InterruptedException ignored){
            Thread.currentThread().interrupt();
        }catch(Exception failure){
            state="ERRORE";
            Log.e(TAG,"Audio V2 unavailable (gameplay can continue silently)",failure);
        }finally{
            ready=false;
            if(device!=null){
                try{device.pause();}catch(Exception ignored){}
                try{device.flush();}catch(Exception ignored){}
                try{device.release();}catch(Exception ignored){}
            }
        }
    }
}
