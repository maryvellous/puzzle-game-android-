package com.maryvellous.lavablobs;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.Log;

import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AUDIO-TEST APK ONLY. A live, hybrid sample/granular-material prototype driven
 * by actual soft-body adhesion velocities and membrane deformation changes.
 *
 * Timbres come from the user's C4.1 audition results, rendered to small WAV
 * buffers at build time. This is deliberately a SoundPool control prototype,
 * NOT the final realtime modal+granular PCM audio engine. No new physics forces.
 */
final class ExperimentalAudio {
    private static final String TAG="LavaAudioTest";
    private final SoundPool pool;
    private final Set<Integer> ready=ConcurrentHashMap.newKeySet();
    private final IdentityHashMap<Blob,Float> previousShape=new IdentityHashMap<>();
    private final IdentityHashMap<Blob,Float> shapeChanges=new IdentityHashMap<>();

    private final int impactId, frictionId, compressionId;
    private int frictionStream,compressionStream,lastImpactStream;
    private float frictionLevel,compressionLevel;
    private boolean enabled,paused,released;
    private long lastImpactMs,lastMergeMs;
    private int countFrames;

    ExperimentalAudio(Context context,boolean enabled) {
        this.enabled=enabled;
        pool=new SoundPool.Builder().setMaxStreams(5)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build();
        pool.setOnLoadCompleteListener((s,id,status)->{
            if(status==0&&!released)ready.add(id);
            else if(status!=0)Log.e(TAG,"Failed to load experimental material "+id+", status="+status);
        });
        impactId=load(context,"lava_test_impact");
        frictionId=load(context,"lava_test_friction");
        compressionId=load(context,"lava_test_compression");
        Log.i(TAG,"Experimental physics-driven SFX selected, loaded ids: "+
                impactId+"/"+frictionId+"/"+compressionId);
    }

    private int load(Context context,String name) {
        int raw=context.getResources().getIdentifier(name,"raw",context.getPackageName());
        if(raw==0) {
            Log.e(TAG,"Missing generated AUDIO TEST only resource: "+name);
            return 0;
        }
        return pool.load(context,raw,1);
    }

    private boolean available(int id) {
        return id>0&&ready.contains(id)&&enabled&&!paused&&!released;
    }

    void setEnabled(boolean value) {
        enabled=value;
        if(!enabled)stopEverything();
    }

    void onMerge() {
        lastMergeMs=SystemClock.elapsedRealtime();
        stopContinuous();
    }

    void impact(int tier,float speed) {
        if(!available(impactId))return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastMergeMs<140 || now-lastImpactMs<115)return;
        float energy=AudioTestModel.impactIntensity(speed);
        // Keep this below merge level, but don't bury a brief impact in silence.
        float volume=.48f+.42f*(float)Math.sqrt(energy);
        float rate=AudioTestModel.clamp(1.0f-.012f*Math.max(0,tier-1),.87f,1.08f);
        int stream=pool.play(impactId,volume,volume,2,0,rate);
        if(stream!=0){
            lastImpactMs=now;
            lastImpactStream=stream;
        }
    }

    /**
     * Called AFTER the existing world tick, never from within any of the five
     * contact solver passes. Each adhesion contributes at most once per sample.
     */
    void observe(BlobWorld world,float dt) {
        if(released||paused)return;
        // Shape history updates even if audio is muted, avoiding a loud
        // artificial jump when the user's SFX toggle is re-enabled.
        shapeChanges.clear();
        previousShape.keySet().retainAll(world.blobs);
        for(Blob blob:world.blobs) {
            float shape=AudioTestModel.compression(blob);
            Float old=previousShape.put(blob,shape);
            float change=old==null?0f:AudioTestModel.compressionIntensity(old,shape,dt);
            shapeChanges.put(blob,change);
        }

        if(!enabled)return;
        float maxSlip=0f,maxCompression=0f;
        // Existing short-lived adhesion links are the actual contact sensor:
        // no circle-vs-circle collision and no additional physics simulation.
        for(BlobWorld.Adhesion link:world.adhesions) {
            maxSlip=Math.max(maxSlip,AudioTestModel.slip(link));
            Float a=shapeChanges.get(link.a),b=shapeChanges.get(link.b);
            maxCompression=Math.max(maxCompression,Math.max(a==null?0:a,b==null?0:b));
        }

        float targetFriction=AudioTestModel.frictionIntensity(maxSlip)*.50f;
        // C4 Compression B is intentionally nearly inaudible.
        float targetCompression=AudioTestModel.clamp(maxCompression,0f,1f)*.20f;
        float fAlpha=Math.min(1f,Math.max(.02f,dt*11f));
        float cAlpha=Math.min(1f,Math.max(.02f,dt*8f));
        frictionLevel+=(targetFriction-frictionLevel)*fAlpha;
        compressionLevel+=(targetCompression-compressionLevel)*cAlpha;

        // Looping timbral material is separate from physical event triggering.
        // A surface resting against another generates no scraping.
        frictionStream=updateLoop(frictionStream,frictionId,frictionLevel,
                .78f+.18f*AudioTestModel.frictionIntensity(maxSlip));
        compressionStream=updateLoop(compressionStream,compressionId,compressionLevel,1f);

        if(++countFrames%600==0) {
            Log.d(TAG,"contactLinks="+world.adhesions.size()+
                    " slip="+maxSlip+" friction="+frictionLevel+
                    " compression="+compressionLevel);
        }
    }

    private int updateLoop(int stream,int soundId,float gain,float rate) {
        if(gain<.002f) {
            if(stream!=0)pool.stop(stream);
            return 0;
        }
        if(!available(soundId))return 0;
        if(stream==0) {
            // The seamless short C4.1-derived texture has its own silent ramp.
            stream=pool.play(soundId,.001f,.001f,0,-1,rate);
        }
        if(stream!=0) {
            pool.setVolume(stream,gain,gain);
            pool.setRate(stream,rate);
        }
        return stream;
    }

    void pause() {
        paused=true;
        stopContinuous();
        if(!released)pool.autoPause();
    }

    void resume() {
        if(released)return;
        pool.autoResume();
        paused=false;
    }

    private void stopContinuous() {
        if(released)return;
        if(frictionStream!=0)pool.stop(frictionStream);
        if(compressionStream!=0)pool.stop(compressionStream);
        frictionStream=0;
        compressionStream=0;
        frictionLevel=0;
        compressionLevel=0;
    }

    private void stopEverything(){
        if(released)return;
        stopContinuous();
        if(lastImpactStream!=0)pool.stop(lastImpactStream);
        lastImpactStream=0;
    }

    void release() {
        if(released)return;
        stopEverything();
        released=true;
        ready.clear();
        previousShape.clear();
        shapeChanges.clear();
        pool.release();
    }
}
