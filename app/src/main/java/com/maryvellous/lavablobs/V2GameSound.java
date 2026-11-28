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
import com.maryvellous.lavablobs.audio.TimingHistogram;
import com.maryvellous.lavablobs.audio.AudioSession;


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
    private final DiagnosticTraceRecorder traceRecorder;
    private final SharedPreferences preferences;
    private final PhysicsAudioEvents physicsEvents;
    private final AudioEventQueue<AudioEvent> queue=new AudioEventQueue<>(CAPACITY,1024);
    private final AudioSession session;
    private final Thread audioThread;
    private int lastNonzeroVolume;
    private long maxWriteNs;
    private final TimingHistogram renderTiming=new TimingHistogram(),writeTiming=new TimingHistogram();
    private final java.util.concurrent.atomic.AtomicLongArray rejectedByType=new java.util.concurrent.atomic.AtomicLongArray(AudioEvent.Type.values().length);
    private volatile long startupDiscarded;
    private long latestReportMs;
    private int blocksSinceReport;
    private long renderNsSinceReport,worstRenderNs;
    private int underrunBaseline;

    V2GameSound(Context context) {
        app=context.getApplicationContext();
        diagnosticBuild=app.getPackageName().endsWith(".audiov21")||BuildConfig.AUDIO_AGGREGATION;
        traceRecorder=diagnosticBuild?new DiagnosticTraceRecorder(app):null;
        preferences=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        int initialVolume=Math.max(0,Math.min(100,preferences.getInt(VOLUME_KEY,
                preferences.getBoolean(EFFECTS_KEY,true)?100:0)));
        session=new AudioSession(queue,initialVolume);
        lastNonzeroVolume=Math.max(1,Math.min(100,preferences.getInt(LAST_VOLUME_KEY,
                initialVolume>0?initialVolume:100)));
        physicsEvents=new PhysicsAudioEvents(this::send,()->session.snapshot().generation,System::nanoTime,this::reset);
        audioThread=new Thread(this::runAudio,"LavaV2-PCM");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    @Override public boolean effectsEnabled(){return session.snapshot().volume>0;}
    @Override public boolean supportsVolumeSlider(){return true;}
    @Override public int volumePercent(){return session.snapshot().volume;}
    @Override public void setEffectsEnabled(boolean next) {
        if(next==effectsEnabled())return;
        setVolumePercent(next?lastNonzeroVolume:0);
    }

    @Override public void setVolumePercent(int percent) {
        int next=Math.max(0,Math.min(100,percent));
        if(session.snapshot().state==AudioSession.State.STOPPED||volumePercent()==next)return;
        int previousGeneration=session.snapshot().generation;
        session.volume(next);
        flushIfChanged(previousGeneration);
        if(next>0)lastNonzeroVolume=next;
        preferences.edit().putInt(VOLUME_KEY,next)
                .putInt(LAST_VOLUME_KEY,lastNonzeroVolume)
                .putBoolean(EFFECTS_KEY,next>0).apply();
    }

    // The authoritative source is the simulation, never the View.
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

    private void flushIfChanged(int previousGeneration){
        if(traceRecorder!=null&&previousGeneration!=session.snapshot().generation)
            traceRecorder.flush(queue.rejectedObservations(),queue.rejectedControls());
    }
    @Override public void reset() {
        int before=session.snapshot().generation;session.reset();flushIfChanged(before);
    }
    private void send(AudioEvent event) {
        // Record admitted inputs and overload candidates; generation changes
        // split traces, including when a control overflow invalidates the queue.
        if(traceRecorder!=null&&session.snapshot().running())traceRecorder.record(event);
        AudioSession.Admission result=session.submit(event);
        if(result==AudioSession.Admission.STARTUP){startupDiscarded++;return;}
        if(result==AudioSession.Admission.OBSERVATION_REJECTED||result==AudioSession.Admission.CONTROL_OVERFLOW)
            rejectedByType.incrementAndGet(event.type.ordinal());
        if(result==AudioSession.Admission.CONTROL_OVERFLOW){
            flushIfChanged(event.generation);
            Log.e(TAG,"Authoritative audio overflow; audio suspended until lifecycle reset");
        }
    }
    @Override public void pause() {
        int before=session.snapshot().generation;session.pause();flushIfChanged(before);
    }
    @Override public void resume(){session.resume();}
    @Override public String diagnosticStatus() {
        if(!diagnosticBuild)return null;
        AudioSession.State state=session.snapshot().state;
        String label;
        switch(state){
            case READY:label="PRONTO";break;
            case STARTING:label="CARICAMENTO";break;
            case PAUSED:label="PAUSA";break;
            case MUTED:label="MUTO";break;
            case OVERFLOW:label="ERRORE CODA";break;
            default:label=state.name();
        }
        return (BuildConfig.AUDIO_AGGREGATION? "AUDIO V2 · REFINED CLUSTER · ":"AUDIO V2 · REFINED BYPASS · ")+label+
                (queue.rejectedObservations()>0?" · Q:"+queue.rejectedObservations():"");
    }
    @Override public void release() {
        if(session.snapshot().state==AudioSession.State.STOPPED)return;
        int before=session.snapshot().generation;session.close();flushIfChanged(before);
        if(traceRecorder!=null)traceRecorder.close();
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
            if(session.snapshot().stopped())return;
            float[] releaseSwoosh=null;
            try{releaseSwoosh=AndroidBloopDecoder.loadLaunch(app);}
            catch(InterruptedException interrupted){throw interrupted;}
            catch(Exception failure){Log.w(TAG,"Classic release unavailable; using material attack",failure);}
            MaterialAudioEngine engine=new MaterialAudioEngine(materials,releaseSwoosh,BuildConfig.AUDIO_AGGREGATION,MaterialAudioEngine.MixProfile.REFINED);
            engine.setOutputVolumePercent(volumePercent());
            engine.reset();
            device=createTrack();
            if(session.snapshot().stopped())return;
            session.ready();
            float[] floatBlock=new float[BLOCK_FRAMES];
            short[] shortBlock=new short[BLOCK_FRAMES];
            boolean playing=false;
            int seenGeneration=session.snapshot().generation;
            AudioTimeline timeline=new AudioTimeline(RATE,BLOCK_FRAMES,Math.round(RATE*.12f));
            AudioBlockScheduler scheduler=new AudioBlockScheduler(queue,timeline,MAX_DISPATCH_PER_BLOCK);
            java.util.function.IntSupplier generationSource=()->session.snapshot().generation;
            underrunBaseline=device.getUnderrunCount();
            while(true){
                AudioSession.Snapshot current=session.snapshot();
                if(current.stopped())break;
                if(seenGeneration!=current.generation){
                    seenGeneration=current.generation;
                    if(playing){device.pause();device.flush();playing=false;}
                    engine.setOutputVolumePercent(current.volume);
                    engine.reset();scheduler.reset();
                }
                if(!current.running()){
                    if(playing){device.pause();device.flush();playing=false;}
                    session.awaitRunnable();
                    continue;
                }
                if(!playing){device.play();playing=true;}
                long started=System.nanoTime();
                engine.setOutputVolumePercent(current.volume);
                if(!scheduler.render(engine,floatBlock,seenGeneration,generationSource))continue;
                long spent=System.nanoTime()-started;
                worstRenderNs=Math.max(worstRenderNs,spent);renderTiming.record(spent);
                renderNsSinceReport+=spent;
                blocksSinceReport++;
                for(int i=0;i<BLOCK_FRAMES;i++){
                    float sample=Math.max(-1f,Math.min(1f,floatBlock[i]));
                    shortBlock[i]=(short)Math.round(sample*32767f);
                }
                int written=0;
                long writeStart=System.nanoTime();
                while(written<BLOCK_FRAMES&&session.snapshot().running()&&seenGeneration==session.snapshot().generation){
                    int n=device.write(shortBlock,written,BLOCK_FRAMES-written,
                            AudioTrack.WRITE_BLOCKING);
                    if(n<0)throw new IllegalStateException("PCM write error "+n);
                    if(n==0)throw new IllegalStateException("Blocking PCM write made no progress");
                    written+=n;
                }
                long writeNs=System.nanoTime()-writeStart;
                maxWriteNs=Math.max(maxWriteNs,writeNs);writeTiming.record(writeNs);
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
                            " maxWriteMs="+maxWriteNs/1e6+
                            " p99QueueAgeUpperMs="+scheduler.p99QueueAgeNs()/1e6+
                            " p99DispatchUpperMs="+scheduler.p99DispatchNs()/1e6+
                            " p99RenderUpperMs="+renderTiming.p99UpperNs()/1e6+
                            " p99WriteUpperMs="+writeTiming.p99UpperNs()/1e6+
                            " rejectedByType="+rejectedByType+" dispatched="+scheduler.dispatched()+
                            " lateEvents="+scheduler.lateEvents()+" rebases="+scheduler.rebases()+
                            " dispatchBudgetHits="+scheduler.budgetHits()+" startupDiscarded="+startupDiscarded+
                            " peakDry="+engine.peakDry()+" minLimiterGain="+engine.minimumLimiterGain()+
                            " limitedFrames="+engine.limitedFrames()+" renderedFrames="+engine.renderedFrames()+
                            " peakGainStep="+engine.peakGainStep()+" peakSampleStep="+engine.peakSampleStep()+
                            " mixLatencyFrames="+engine.mixLatencyFrames()+
                            " voiceObjectsAllocated="+engine.allocatedVoices()+" voicePoolHits="+engine.reusedVoices()+
                            " peakVoices="+engine.peakVoices()+" peakSecondary="+engine.peakShortVoices()+
                            " clusterCandidates="+engine.aggregateCandidates()+" groups="+engine.aggregateGroups()+
                            " collapsed="+engine.aggregateCollapsed()+" rejectedClusters="+engine.aggregateRejected()+
                            " cancelledClusters="+engine.aggregateCancelled());
                    latestReportMs=now;
                    blocksSinceReport=0;renderNsSinceReport=0;worstRenderNs=0;
                    scheduler.resetIntervalMaxima();maxWriteNs=0;renderTiming.reset();writeTiming.reset();underrunBaseline=device.getUnderrunCount();
                }
            }
        }catch(InterruptedException ignored){
            Thread.currentThread().interrupt();
        }catch(Exception failure){
            session.fail();
            Log.e(TAG,"Audio V2 unavailable (gameplay can continue silently)",failure);
        }finally{
            if(!session.snapshot().stopped())session.fail();
            if(device!=null){
                try{device.pause();}catch(Exception ignored){}
                try{device.flush();}catch(Exception ignored){}
                try{device.release();}catch(Exception ignored){}
            }
        }
    }
}
