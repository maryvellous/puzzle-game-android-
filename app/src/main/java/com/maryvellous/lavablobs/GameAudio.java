package com.maryvellous.lavablobs;

import android.content.Context;
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
    private final SoundPool sounds;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"LavaAudioClips");t.setDaemon(true);return t;
    });
    private final Set<Integer> ready=Collections.newSetFromMap(new ConcurrentHashMap<Integer,Boolean>());
    private final int[] bloops=new int[4],bag={0,1,2,3};
    private final Random rng=new Random();
    private int bagCursor=4,previousBloop=-1;
    private volatile boolean released;
    private volatile int impactId,impactFallbackId,launchId,popId;
    private long lastImpactMs,lastMergeMs;

    GameAudio(Context context) {
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
        // Independent, non-merge sound for impact fallback; never play a full merge Bloop as a collision.
        impactFallbackId=sounds.load(context,R.raw.swoosh_source,1);
        Context app=context.getApplicationContext();
        worker.execute(()->prepareClips(app));
    }

    private void prepareClips(Context context) {
        // Separate preparations: a failed launch/POP conversion cannot affect collisions.
        // Impact is a new, shorter filtered excerpt (v3 busts the old cached v2 WAV).
        try {
            File impact=EffectCompiler.renderSoftImpact(context,R.raw.bloop_2,
                    "lava_impact_v3.wav",.28f,.53f,false,5,40);
            synchronized(this) {
                if(released)return;
                impactId=sounds.load(impact.getAbsolutePath(),1);
                if(impactId==0)Log.w(TAG,"Impact WAV load rejected; using quiet swoosh fallback");
            }
        }catch(Exception e){
            Log.w(TAG,"Impact WAV unavailable; using quiet swoosh fallback",e);
        }
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
        if(released)return;
        int id=bloops[nextBloop()];
        if(!ready.contains(id))return;
        lastMergeMs=SystemClock.elapsedRealtime();
        sounds.play(id,.56f,.56f,2,0,AudioPolicy.mergeRate(mergedTier));
    }

    void launch(int tier) {
        if(released||!ready.contains(launchId))return;
        float rate=Math.max(1.08f,1.23f-.035f*Math.max(0,tier));
        sounds.play(launchId,.16f,.16f,0,0,rate);
    }

    void impact(int tier,float speed) {
        if(released)return;
        int id=AudioPolicy.impactSample(impactId,ready.contains(impactId),
                impactFallbackId,ready.contains(impactFallbackId));
        if(id==0)return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastImpactMs<88L||now-lastMergeMs<100L)return;
        float volume=AudioPolicy.impactVolume(speed);
        // Original swoosh is longer than the edited excerpt: accelerate and attenuate it.
        boolean fallback=id==impactFallbackId;
        float gain=fallback?volume*.58f:volume;
        float rate=fallback?1.85f:AudioPolicy.impactRate(tier);
        int streamId=sounds.play(id,gain,gain,1,0,rate);
        if(streamId!=0)lastImpactMs=now;
        else Log.w(TAG,"SoundPool rejected collision playback (id="+id+")");
    }

    void pop() {
        if(released||!ready.contains(popId))return;
        sounds.play(popId,.29f,.29f,1,0,1.30f);
    }

    void pause(){if(!released)sounds.autoPause();}
    void resume(){if(!released)sounds.autoResume();}

    synchronized void release() {
        if(released)return;
        released=true;
        worker.shutdownNow();
        ready.clear();
        sounds.release();
    }
}
