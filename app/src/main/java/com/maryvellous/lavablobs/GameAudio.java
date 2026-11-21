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
    private volatile int impactId,launchId,popId;
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
        Context app=context.getApplicationContext();
        worker.execute(()->prepareClips(app));
    }

    private void prepareClips(Context context) {
        try {
            File launch=EffectCompiler.render(context,R.raw.swoosh_source,
                    "lava_launch_v2.wav",.47f,.94f,true,5,13);
            File impact=EffectCompiler.render(context,R.raw.bloop_2,
                    "lava_impact_v2.wav",.31f,.68f,false,14,52);
            File pop=EffectCompiler.render(context,R.raw.bloop_2,
                    "lava_pop_v1.wav",.04f,.23f,false,3,22);
            synchronized(this){
                if(released)return;
                launchId=sounds.load(launch.getAbsolutePath(),1);
                impactId=sounds.load(impact.getAbsolutePath(),1);
                popId=sounds.load(pop.getAbsolutePath(),1);
            }
        }catch(Exception e){
            Log.w(TAG,"Optional launch/impact preparation failed",e);
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
        if(released||!ready.contains(impactId))return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastImpactMs<88L||now-lastMergeMs<100L)return;
        lastImpactMs=now;
        float volume=AudioPolicy.impactVolume(speed);
        sounds.play(impactId,volume,volume,0,0,AudioPolicy.impactRate(tier));
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
