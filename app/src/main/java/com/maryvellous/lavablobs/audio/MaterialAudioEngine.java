package com.maryvellous.lavablobs.audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * One deterministic, platform-neutral PCM engine for offline previews AND a
 * future Android AudioTrack adapter. One command thread owns this instance:
 * enqueue/serialize simulation commands before calling renderMono, rather
 * than mutating these collections from an Android audio callback.
 *
 * Sound design rule: only fusion can start a whole Bloop. All contacts, launch
 * and POP use partial sources from BloopMaterialBank; static quiet is valid.
 * Complete fusion voices NEVER get stolen. The scene bus may attenuate to
 * maintain headroom but cannot terminate or replace a fusion.
 */
public final class MaterialAudioEngine {
    public static final int SAMPLE_RATE=BloopMaterialBank.SAMPLE_RATE;
    private static final int MAX_SHORT_VOICES=24;

    private static final class Voice {
        final float[] sound;
        final boolean fusion;
        final float volume,rate;
        double position;
        Voice(float[] sound,float volume,float rate,boolean fusion) {
            this.sound=sound;this.volume=volume;this.rate=rate;this.fusion=fusion;
        }
        float sample() {
            if(position>=sound.length)return 0f;
            int start=(int)position;
            int next=Math.min(start+1,sound.length-1);
            float frac=(float)(position-start);
            float s=sound[start]*(1-frac)+sound[next]*frac;
            position+=rate;
            return volume*s;
        }
    }

    private static final class Pair {
        final long key;
        final BloopMaterialBank.Material material;
        final float rate;
        final long firstSample;
        float phase,gain,goal,previousCompression;
        boolean releasing,excited;
        Pair(long key,BloopMaterialBank.Material material,float rate,float pressure,
             long firstSample) {
            this.key=key;this.material=material;this.rate=rate;
            this.previousCompression=pressure;
            this.firstSample=firstSample;
        }
    }

    private final BloopMaterialBank bank;
    private final Map<Long,Pair> byKey=new HashMap<>();
    private final ArrayList<Pair> pairs=new ArrayList<>();
    private final ArrayList<Voice> voices=new ArrayList<>();
    private final float gainRise=(float)(1-Math.exp(-1d/(SAMPLE_RATE*.025)));
    private final float gainFall=(float)(1-Math.exp(-1d/(SAMPLE_RATE*.085)));
    private final float limiterRelease=(float)(1-Math.exp(-1d/(SAMPLE_RATE*.150)));
    private float masterGain=1f,peakOutput;
    private int fusionIndex,contactBeginCount,fusionsStarted,fusionsCompleted;
    private int shortVoicesRejected,peakVoices;
    private long sampleClock;

    public MaterialAudioEngine(BloopMaterialBank bank) {
        if(bank==null)throw new IllegalArgumentException("Material bank required");
        this.bank=bank;
    }

    private static float clamp(float value,float low,float high) {
        return Math.max(low,Math.min(high,value));
    }

    private static long key(int idA,int idB) {
        if(idA<=0||idB<=0||idA==idB)
            throw new IllegalArgumentException("Invalid stable blob pair");
        long small=(Math.min(idA,idB)&0xffffffffL);
        long large=(Math.max(idA,idB)&0xffffffffL);
        return (small<<32)|large;
    }

    public static float pitchForRadius(float radius) {
        if(!(radius>0f)||!Float.isFinite(radius))return 1f;
        return clamp((float)Math.pow(32f/radius,.40),.70f,1.35f);
    }

    private void shortVoice(float[] audio,float volume,float rate) {
        int shortCount=0;
        for(int i=0;i<voices.size();i++)if(!voices.get(i).fusion)shortCount++;
        if(shortCount>=MAX_SHORT_VOICES) {
            shortVoicesRejected++;
            return;
        }
        if(volume>.0001f)voices.add(new Voice(audio,volume,rate,false));
    }

    /**
     * Exactly one observation per pair per physical substep. No sound begins
     * merely because another of the five contact-solver passes ran.
     */
    public void contact(int idA,int idB,float approach,float slide,
                        float compression,float radiusA,float radiusB) {
        if(!Float.isFinite(approach)||!Float.isFinite(slide)||
                !Float.isFinite(compression)||!Float.isFinite(radiusA)||
                !Float.isFinite(radiusB))
            throw new IllegalArgumentException("Nonfinite contact observation");
        long k=key(idA,idB);
        Pair pair=byKey.get(k);
        float radius=(radiusA+radiusB)*.5f;
        if(pair==null) {
            BloopMaterialBank.Material material=bank.at(contactBeginCount++);
            pair=new Pair(k,material,pitchForRadius(radius),
                    clamp(compression,0f,1f),sampleClock);
            byKey.put(k,pair);
            pairs.add(pair);
        }
        pair.releasing=false;
        // A membrane can first touch slowly and then take its strongest
        // closing impulse one or two solver substeps later. Allow exactly
        // ONE physical onset inside the first 55 ms, never every pass/frame.
        if(!pair.excited && approach>18f &&
                sampleClock-pair.firstSample<=Math.round(.055f*SAMPLE_RATE)) {
            float intensity=clamp((float)(
                    Math.log1p((approach-18f)/30f)/Math.log1p(10d)),0f,1f);
            shortVoice(pair.material.attack,.18f*intensity,pair.rate);
            pair.excited=true;
        }
        float press=clamp(compression,0f,1f);
        float movement=(float)Math.pow(clamp(Math.max(0f,slide)/130f,0f,1f),.65);
        float deform=clamp(Math.abs(press-pair.previousCompression)*14f,0f,1f);
        float drive=clamp(.75f*movement+.25f*deform,0f,1f);
        // Quiet compression without movement/renewed deformation is correct.
        pair.goal=(slide>4f||deform>.028f)?drive:0f;
        pair.previousCompression=press;
    }

    /** Tell the mixer when ContactTracker has confirmed a real separation. */
    public void endContact(int idA,int idB) {
        Pair pair=byKey.get(key(idA,idB));
        if(pair!=null){pair.goal=0f;pair.releasing=true;}
    }

    /** Cancels tracking when a blob ceases to exist (merge/POP), no fake END. */
    public void cancelBlob(int blobId) {
        for(int i=0;i<pairs.size();i++) {
            Pair pair=pairs.get(i);
            int a=(int)(pair.key>>>32),b=(int)pair.key;
            if(a==blobId||b==blobId) {
                pair.goal=0f;
                pair.releasing=true;
            }
        }
    }

    /**
     * A WHOLE ORIGINAL Bloop every time, whatever the overlap. Never steal
     * or truncate another fusion voice. Radius changes playback pitch/time,
     * but the full source is traversed from first sample to final sample.
     */
    public void merge(int parentA,int parentB,float radius) {
        if(parentA>0)cancelBlob(parentA);
        if(parentB>0)cancelBlob(parentB);
        BloopMaterialBank.Material material=bank.at(fusionIndex++);
        voices.add(new Voice(material.complete,.46f,pitchForRadius(radius),true));
        fusionsStarted++;
    }

    /** Launch is always a quiet material fragment, not a full Bloop. */
    public void launch(float radius) {
        BloopMaterialBank.Material material=bank.at((fusionIndex+2)%4);
        shortVoice(material.attack,.055f,pitchForRadius(radius));
    }

    /** POP may be distinct, but cannot steal the whole fusion gesture. */
    public void pop(int blobId,float radius) {
        cancelBlob(blobId);
        BloopMaterialBank.Material material=bank.at((fusionIndex+1)%4);
        shortVoice(material.attack,.14f,pitchForRadius(radius));
    }

    /**
     * Fill an already allocated output buffer. No allocation, object boxing or
     * Android dependency in the sample loop. Input commands must be serialized
     * onto the same thread or queued for the render-thread adapter.
     */
    public void renderMono(float[] output,int offset,int frames) {
        if(output==null||offset<0||frames<0||offset>output.length-frames)
            throw new IllegalArgumentException("Output buffer bounds");
        for(int s=offset;s<offset+frames;s++) {
            float dry=0f;
            // Fusion and partial one-shots, with interpolation for mass/pitch.
            for(int i=voices.size()-1;i>=0;i--) {
                Voice voice=voices.get(i);
                dry+=voice.sample();
                if(voice.position>=voice.sound.length) {
                    if(voice.fusion)fusionsCompleted++;
                    voices.remove(i);
                }
            }
            // Continuous tail material, driven by the membrane controls.
            float texture=0f;
            float collectiveGain=pairs.size()>3 ?
                    (float)(1/Math.sqrt(pairs.size()/3f)):1f;
            for(int i=0;i<pairs.size();i++) {
                Pair pair=pairs.get(i);
                float coefficient=pair.goal>pair.gain?gainRise:gainFall;
                pair.gain+=(pair.goal-pair.gain)*coefficient;
                if(pair.gain<.00001f && pair.releasing)continue;
                float[] loop=pair.material.texture;
                int start=(int)pair.phase;
                int next=(start+1)%loop.length;
                float t=pair.phase-start;
                float source=loop[start]*(1-t)+loop[next]*t;
                texture+=source*pair.material.textureTrim*pair.gain;
                pair.phase+=pair.rate;
                if(pair.phase>=loop.length)pair.phase-=loop.length;
            }
            dry+=texture*.18f*collectiveGain;
            // Instantaneous protective gain catches unforeseen summed peaks.
            // Release is smoothed so a cascade does not permanently duck all
            // subsequent quiet audio. No voice gets cut short.
            float need=Math.abs(dry)>.87f?.87f/Math.abs(dry):1f;
            if(need<masterGain)masterGain=need;
            else masterGain+=(need-masterGain)*limiterRelease;
            output[s]=dry*masterGain;
            peakOutput=Math.max(peakOutput,Math.abs(output[s]));
            peakVoices=Math.max(peakVoices,voices.size());
            sampleClock++;
        }
        // State cleanup is once PER BLOCK, never per sample.
        for(int i=pairs.size()-1;i>=0;i--) {
            Pair p=pairs.get(i);
            if(p.releasing&&p.gain<.00005f) {
                byKey.remove(p.key);
                pairs.remove(i);
            }
        }
    }

    public void reset() {
        byKey.clear();pairs.clear();voices.clear();
        masterGain=1f;peakOutput=0f;fusionIndex=0;
        contactBeginCount=0;fusionsStarted=0;fusionsCompleted=0;
        shortVoicesRejected=0;peakVoices=0;sampleClock=0;
    }

    public int activePairs(){return pairs.size();}
    public int activeOneShots(){return voices.size();}
    public int contactBegins(){return contactBeginCount;}
    public int fusionsStarted(){return fusionsStarted;}
    public int fusionsCompleted(){return fusionsCompleted;}
    public int rejectedShortVoices(){return shortVoicesRejected;}
    public int peakVoices(){return peakVoices;}
    public float peakOutput(){return peakOutput;}
}
