package com.maryvellous.lavablobs;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Short non-blocking SFX. Original floraphonic Bloop files play unchanged for
 * merges. Two quiet derived clips are edited once off-thread and cached.
 */
final class GameAudio {
    private static final String TAG="LavaAudio";
    private static final String EFFECTS_KEY="sound_effects_enabled_v1";
    private final SoundPool sounds;
    private final ExperimentalAudio audioTest;
    private final SharedPreferences preferences;
    private boolean effectsEnabled;
    // Remember recent streams so muting cuts off sounds that are already playing.
    private final int[] recentStreams=new int[16];
    private int streamCursor;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"LavaAudioClips");t.setDaemon(true);return t;
    });
    private final Set<Integer> ready=Collections.newSetFromMap(new ConcurrentHashMap<Integer,Boolean>());
    private final int[] bloops=new int[4],bag={0,1,2,3};
    private final Random rng=new Random();
    private int bagCursor=4,previousBloop=-1;
    private volatile boolean released;
    private final int impactId;
    private volatile int launchId,popId;
    private long lastImpactMs,lastMergeMs;

    GameAudio(Context context) {
        preferences=context.getSharedPreferences("lava_blobs",Context.MODE_PRIVATE);
        effectsEnabled=preferences.getBoolean(EFFECTS_KEY,true);
        sounds=new SoundPool.Builder().setMaxStreams(8)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        sounds.setOnLoadCompleteListener((pool,id,status)->{
            if(status==0&&!released)ready.add(id);
            else if(status!=0)Log.w(TAG,"SoundPool load failed: "+id+", status "+status);
        });
        bloops[0]=sounds.load(context,R.raw.bloop_1,1);
        bloops[1]=sounds.load(context,R.raw.bloop_2,1);
        bloops[2]=sounds.load(context,R.raw.bloop_3,1);
        bloops[3]=sounds.load(context,R.raw.bloop_4,1);
        // Distinct, pre-rendered 112ms rubber thump; no runtime conversion or Bloop fallback.
        impactId=sounds.load(context,R.raw.lava_impact_soft,1);
        Context app=context.getApplicationContext();
        worker.execute(()->prepareClips(app));
        audioTest=BuildConfig.AUDIO_TEST?new ExperimentalAudio(context,effectsEnabled):null;
    }

    private void prepareClips(Context context) {
        // Only launch and POP need conversion; a conversion failure cannot mute impacts.
        try {
            File launch=EffectCompiler.render(context,R.raw.swoosh_source,
                    "lava_launch_v2.wav",.47f,.94f,true,5,13);
            synchronized(this) {
                if(released)return;
                launchId=sounds.load(launch.getAbsolutePath(),1);
                if(launchId==0)Log.w(TAG,"Launch WAV refused by SoundPool");
            }
        }catch(Exception e){
            Log.w(TAG,"Launch effect unavailable",e);
        }
        try {
            File pop=EffectCompiler.render(context,R.raw.bloop_2,
                    "lava_pop_v1.wav",.04f,.23f,false,3,22);
            synchronized(this) {
                if(released)return;
                popId=sounds.load(pop.getAbsolutePath(),1);
                if(popId==0)Log.w(TAG,"POP WAV refused by SoundPool");
            }
        }catch(Exception e){
            Log.w(TAG,"POP effect unavailable",e);
        }
    }

    boolean effectsEnabled(){return effectsEnabled;}

    void setEffectsEnabled(boolean enabled) {
        if(released||effectsEnabled==enabled)return;
        effectsEnabled=enabled;
        preferences.edit().putBoolean(EFFECTS_KEY,enabled).apply();
        if(audioTest!=null)audioTest.setEnabled(enabled);
        if(!enabled) {
            for(int streamId:recentStreams)if(streamId!=0)sounds.stop(streamId);
            java.util.Arrays.fill(recentStreams,0);
        }
    }

    private boolean canPlay(int soundId) {
        return AudioPolicy.effectsAllowed(effectsEnabled,released,
                soundId>0&&ready.contains(soundId));
    }

    private int playEffect(int soundId,float volume,int priority,float rate) {
        int streamId=sounds.play(soundId,volume,volume,priority,0,rate);
        if(streamId!=0)recentStreams[streamCursor++%recentStreams.length]=streamId;
        return streamId;
    }

    private int nextBloop() {
        if(bagCursor==bag.length) {
            for(int i=bag.length-1;i>0;i--) {
                int j=rng.nextInt(i+1),swap=bag[i];
                bag[i]=bag[j];bag[j]=swap;
            }
            if(bag[0]==previousBloop){
                int swap=bag[0];bag[0]=bag[1];bag[1]=swap;
            }
            bagCursor=0;
        }
        int chosen=bag[bagCursor++];
        previousBloop=chosen;
        return chosen;
    }

    void merge(int mergedTier) {
        if(audioTest!=null)audioTest.onMerge();
        if(released||!effectsEnabled)return;
        int id=bloops[nextBloop()];
        if(!canPlay(id))return;
        if(playEffect(id,.56f,2,AudioPolicy.mergeRate(mergedTier))!=0)
            lastMergeMs=SystemClock.elapsedRealtime();
    }

    void launch(int tier) {
        if(!canPlay(launchId))return;
        float rate=Math.max(1.08f,1.23f-.035f*Math.max(0,tier));
        playEffect(launchId,.16f,0,rate);
    }

    void impact(int tier,float approachSpeed,float radiusA,float radiusB) {
        if(audioTest!=null){
            audioTest.impact(tier,radiusA,radiusB,approachSpeed);
            return;
        }
        if(!canPlay(impactId))return;
        long now=SystemClock.elapsedRealtime();
        if(!AudioPolicy.allowImpactAt(now,lastImpactMs,lastMergeMs))return;
        float volume=AudioPolicy.impactVolume();
        int streamId=playEffect(impactId,volume,1,1f);
        if(streamId!=0)lastImpactMs=now;
        else Log.w(TAG,"SoundPool rejected soft impact playback (id="+impactId+")");
    }

    void pop() {
        if(!canPlay(popId))return;
        playEffect(popId,.29f,1,1.30f);
    }

    /** Observe once per physics frame; never once per solver pass. */
    void observePhysics(BlobWorld world,float elapsedSeconds) {
        if(audioTest!=null&&!released)audioTest.observe(world,elapsedSeconds);
    }

    void pause(){
        if(released)return;
        sounds.autoPause();
        if(audioTest!=null)audioTest.pause();
    }
    void resume(){
        if(released)return;
        sounds.autoResume();
        if(audioTest!=null)audioTest.resume();
    }

    synchronized void release() {
        if(released)return;
        released=true;
        worker.shutdownNow();
        ready.clear();
        if(audioTest!=null)audioTest.release();
        sounds.release();
    }
}
