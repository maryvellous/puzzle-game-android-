package com.maryvellous.lavablobs.audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * V2.1 MATERIAL-DYNAMICS ENGINE — not "contact equals sound".
 *
 * A pair may remain touching for an unlimited time IN COMPLETE SILENCE.
 * Brief material sounds are emitted only after measurable sliding or a
 * substantial CHANGE in membrane deformation has accumulated enough physical
 * activity. A static or nearly static contact cannot start/restart a loop.
 *
 * Exactly one of the four full original Bloops is played per real fusion.
 * Other events use finite attack/tail excerpts, never full Bloop cores.
 *
 * Pure Java engine shared by the JVM harness and Android AudioTrack adapter.
 * The caller serializes commands on the audio thread. No Android dependency.
 */
public final class MaterialAudioEngine {
    public static final int SAMPLE_RATE=BloopMaterialBank.SAMPLE_RATE;
    private static final int MAX_SHORT_VOICES=24;
    // Diagnostic V2.2: raise only non-fusion voices by 3x (+9.5 dB).
    private static final float SECONDARY_GAIN=3f;

    // Units: world coordinates/second for slip; normalized membrane deformation.
    // These are a deliberately conservative initial "dead zone" for solver
    // micro-motion, NOT absolute guarantees for all phones/scenes.
    private static final float SLIP_QUIET=45f;
    private static final float DEFORMATION_QUIET=.018f;
    private static final float IMPACT_MIN_SPEED=70f;
    private static final float BULK_MOTION_QUIET=75f;
    private static final float STRONG_DEFORMATION_QUIET=.075f;
    private static final float SQUISH_ENERGY_TRIGGER=.025f; // integrated motion-seconds
    private static final float SQUISH_MIN_INTERVAL=.100f;
    private static final float MAX_PHYSICS_DT=.042f;

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
            int at=(int)position;
            int next=Math.min(at+1,sound.length-1);
            float t=(float)(position-at);
            float value=sound[at]*(1-t)+sound[next]*t;
            position+=rate;
            return volume*value;
        }
    }

    private static final class Pair {
        final long key;
        final BloopMaterialBank.Material material;
        final float rate,scale;
        final int materialIndex;
        float lastCompression;
        float activeSeconds;
        float accumulatedMovement;
        float lastSquishSeconds=-1000f;
        boolean impactStarted;

        Pair(long key,BloopMaterialBank.Material material,float rate,
             float scale,float compression,int materialIndex) {
            this.key=key;this.material=material;this.rate=rate;this.materialIndex=materialIndex;
            this.scale=scale;this.lastCompression=compression;
        }
    }

    private final BloopMaterialBank bank;
    private final float[] releaseSwoosh;
    private final float[][] gelImpacts=new float[BloopMaterialBank.SOURCE_COUNT][];
    private int wallIndex;
    private final Map<Long,Pair> pairs=new HashMap<>();
    private final ArrayList<Voice> voices=new ArrayList<>();
    private final float limiterRelease=(float)(1-Math.exp(-1d/(SAMPLE_RATE*.150)));
    private float masterGain=1f,peakOutput;
    private int fusionIndex,contactBeginCount,fusionsStarted,fusionsCompleted;
    private int shortVoicesRejected,peakVoices;
    private int squishEvents,impactEvents;

    public MaterialAudioEngine(BloopMaterialBank bank) {this(bank,null);}

    public MaterialAudioEngine(BloopMaterialBank bank,float[] releaseSwoosh) {
        if(bank==null)throw new IllegalArgumentException("Material bank required");
        this.bank=bank;
        if(releaseSwoosh!=null){
            if(releaseSwoosh.length<96)throw new IllegalArgumentException("Release clip too short");
            for(float sample:releaseSwoosh)if(!Float.isFinite(sample)||Math.abs(sample)>1.01f)
                throw new IllegalArgumentException("Invalid release PCM");
        }
        this.releaseSwoosh=releaseSwoosh==null?null:releaseSwoosh.clone();
        for(int i=0;i<gelImpacts.length;i++)gelImpacts[i]=gelatinous(bank.at(i));
    }

    // Finite, smooth attack and tail only; no fusion core, loops or synthetic tone.
    // A rising playback-rate contour gives a soft elastic 'uuuuiip' response.
    private static float[] gelatinous(BloopMaterialBank.Material m){
        int overlap=Math.max(1,Math.min(m.attack.length,m.tail.length)/4);
        int length=m.attack.length+m.tail.length-overlap;
        float[] joined=new float[length];
        for(int i=0;i<m.attack.length;i++){
            float fade=i>=m.attack.length-overlap?(m.attack.length-1-i)/(float)overlap:1f;
            joined[i]+=.8f*m.attack[i]*fade;
        }
        for(int i=0;i<m.tail.length;i++){
            float fade=i<overlap?i/(float)overlap:1f;
            joined[m.attack.length-overlap+i]+=.9f*m.tail[i]*fade;
        }
        float[] out=new float[Math.max(96,(int)(length*1.25f))];
        float previous=0f;
        double position=0;
        int fadeIn=Math.min(out.length/3,(int)(SAMPLE_RATE*.012f));
        int fadeOut=Math.min(out.length/3,(int)(SAMPLE_RATE*.018f));
        for(int i=0;i<out.length;i++){
            if(position>=length-1)break;
            int at=(int)position;float frac=(float)(position-at);
            float raw=joined[at]*(1-frac)+joined[at+1]*frac;
            previous+=.22f*(raw-previous);
            float envelope=Math.min(1f,Math.min(i/(float)Math.max(1,fadeIn),(out.length-1-i)/(float)Math.max(1,fadeOut)));
            // Smoothly rise from a stretched attack to a lighter tail.
            float progress=(float)(position/(length-1));
            float endFade=clamp((length-1-(float)position)/Math.max(1f,SAMPLE_RATE*.012f),0f,1f);
            out[i]=previous*envelope*endFade;
            position+=.63f+.56f*progress;
        }
        return out;
    }

    private static float clamp(float n,float lo,float hi) {
        return Math.max(lo,Math.min(hi,n));
    }
    private static long key(int idA,int idB) {
        if(idA<=0||idB<=0||idA==idB)
            throw new IllegalArgumentException("Invalid stable blob pair");
        long smaller=Math.min(idA,idB)&0xffffffffL;
        long larger=Math.max(idA,idB)&0xffffffffL;
        return (smaller<<32)|larger;
    }
    public static float pitchForRadius(float radius) {
        if(!(radius>0f)||!Float.isFinite(radius))return 1f;
        return clamp((float)Math.pow(32f/radius,.40),.70f,1.35f);
    }
    private static float sizeGain(float radius) {
        return clamp((float)Math.pow(Math.max(1f,radius)/32f,.28),.70f,1.25f);
    }

    private boolean shortVoice(float[] audio,float volume,float rate) {
        int shortCount=0;
        for(Voice item:voices)if(!item.fusion)shortCount++;
        if(shortCount>=MAX_SHORT_VOICES) {
            shortVoicesRejected++;
            return false;
        }
        if(volume>.0001f) {
            voices.add(new Voice(audio,volume*SECONDARY_GAIN,rate,false));
            return true;
        }
        return false;
    }

    /** Backward-compatible test entrypoint with the solver's normal half-step. */
    public void contact(int idA,int idB,float approach,float slide,
                        float compression,float radiusA,float radiusB) {
        contact(idA,idB,approach,slide,compression,radiusA,radiusB,
                1f/120f,Math.max(Math.max(0f,approach),Math.max(0f,slide)));
    }

    public void contact(int idA,int idB,float approach,float slide,
                        float compression,float radiusA,float radiusB,float dt) {
        contact(idA,idB,approach,slide,compression,radiusA,radiusB,dt,
                Math.max(Math.max(0f,approach),Math.max(0f,slide)));
    }

    /**
     * One observation per stable pair per real substep.
     *
     * dt MUST be the simulation step duration, not the rate at which AudioTrack
     * happens to drain the command queue. The physical event budget is
     * therefore independent of device audio-buffer size / UI batching.
     *
     * A stationary pair only updates state. It does NOT play a texture at all.
     */
    public void contact(int idA,int idB,float approach,float slide,
                        float compression,float radiusA,float radiusB,float dt,
                        float bulkMotion) {
        if(!Float.isFinite(approach)||!Float.isFinite(slide)||
                !Float.isFinite(bulkMotion)||bulkMotion<0f||
                !Float.isFinite(compression)||!Float.isFinite(radiusA)||
                !Float.isFinite(radiusB)||!Float.isFinite(dt)||
                dt<=0f||dt>MAX_PHYSICS_DT)
            throw new IllegalArgumentException("Nonfinite/invalid contact observation");
        long key=key(idA,idB);
        Pair pair=pairs.get(key);
        float radius=.5f*(radiusA+radiusB);
        if(pair==null) {
            int materialIndex=contactBeginCount++%BloopMaterialBank.SOURCE_COUNT;
            pair=new Pair(key,bank.at(materialIndex),pitchForRadius(radius),
                    sizeGain(radius),clamp(compression,0f,1f),materialIndex);
            pairs.put(key,pair);
        }
        pair.activeSeconds+=dt;

        // Only a substantial initial closing impulse creates an IMPACT attack.
        // It never retriggers on a resting/pressed pair.
        if(!pair.impactStarted && approach>IMPACT_MIN_SPEED &&
                pair.activeSeconds<=.060f) {
            // Broader dynamic range on soft-body collisions. Wall impacts keep
            // the original V2.2 attack and gain curve.
            float dynamic=(float)Math.pow(clamp((approach-IMPACT_MIN_SPEED)/280f,0f,1f),.8f);
            if(shortVoice(gelImpacts[pair.materialIndex],(.045f+.32f*dynamic)*pair.scale,
                    pair.rate))impactEvents++;
            pair.impactStarted=true;
        }

        float pressure=clamp(compression,0f,1f);
        float realDeformation=Math.abs(pressure-pair.lastCompression);
        pair.lastCompression=pressure;

        // Do not amplify tiny solver corrections: the old engine's
        // slide>4 / deformation>.002 gates made a continuous high note.
        float slipMotion=clamp((Math.max(0f,slide)-SLIP_QUIET)/140f,0f,1f);
        float stretchMotion=clamp(
                (realDeformation-DEFORMATION_QUIET)*14f,0f,1f);
        // An immobile blob's contact patch can report surprisingly high
        // local slip because particles oscillate under the five solver
        // passes. Require ACTUAL bulk movement of the 24-node membrane
        // before local slip can make sound. A single major deformation
        // can also make a short squish even if the centre is constrained.
        float bulkDrive=clamp(
                (bulkMotion-BULK_MOTION_QUIET)/110f,0f,1f);
        float strongDeformation=clamp(
                (realDeformation-STRONG_DEFORMATION_QUIET)*12f,0f,1f);
        float activity=clamp(bulkDrive*(.72f*slipMotion+.28f*stretchMotion)+
                .65f*strongDeformation,0f,1f);

        if(activity<.10f) {
            // "Contact remains" and "sound continues" are NOT equivalent.
            // Discard stale activity so barely-moving contacts don't
            // periodically accumulate into fake sound.
            pair.accumulatedMovement=Math.max(0f,
                    pair.accumulatedMovement-dt*2.0f);
        }else{
            pair.accumulatedMovement=Math.min(.10f,
                    pair.accumulatedMovement+activity*dt);
            if(pair.accumulatedMovement>=SQUISH_ENERGY_TRIGGER &&
                    pair.activeSeconds-pair.lastSquishSeconds>=SQUISH_MIN_INTERVAL) {
                // Finite acoustic gesture from the original Bloop TAIL.
                // There is NO looping oscillator, texture or sustained voice.
                float dynamicGain=.045f+.24f*(float)Math.pow(activity,1.15f);
                if(shortVoice(pair.material.tail,dynamicGain*pair.scale,pair.rate)) {
                    squishEvents++;
                    pair.lastSquishSeconds=pair.activeSeconds;
                }
                pair.accumulatedMovement=0f;
            }
        }
    }

    /**
     * The lifecycle tracker already applies 40ms gap hysteresis; a genuine END
     * can remove a contact immediately. A fading finite one-shot remains
     * independent from the pair and will finish naturally.
     */
    public void endContact(int idA,int idB) {pairs.remove(key(idA,idB));}

    public void cancelBlob(int blobId) {
        for(Iterator<Pair> i=pairs.values().iterator();i.hasNext();) {
            Pair pair=i.next();
            if((int)(pair.key>>>32)==blobId || (int)pair.key==blobId)i.remove();
        }
    }

    /** Whole Bloop, never stolen or shortened, even in a fusion cascade. */
    public void merge(int parentA,int parentB,float radius) {
        if(parentA>0)cancelBlob(parentA);
        if(parentB>0)cancelBlob(parentB);
        BloopMaterialBank.Material material=bank.at(fusionIndex++);
        voices.add(new Voice(material.complete,.48f,pitchForRadius(radius),true));
        fusionsStarted++;
    }

    /** Actual rigid-wall onset: original V2.2 impact timbre and gain. */
    public void wallImpact(float approach,float radius){
        if(!Float.isFinite(approach)||!Float.isFinite(radius)||approach<=IMPACT_MIN_SPEED)return;
        float magnitude=clamp((float)(Math.log1p((approach-IMPACT_MIN_SPEED)/40f)/Math.log1p(280d/40d)),0f,1f);
        shortVoice(bank.at(wallIndex++).attack,.18f*magnitude*sizeGain(radius),pitchForRadius(radius));
    }

    public void launch(float radius) {launch(0,radius);}

    public void launch(int tier,float radius) {
        if(releaseSwoosh!=null){
            // Same reversed swoosh and tier-dependent playback rate as 1.0.11.
            // Slightly stronger than the historical .16 to suit this diagnostic mix.
            float rate=Math.max(1.08f,1.23f-.035f*Math.max(0,tier));
            shortVoice(releaseSwoosh,.20f/SECONDARY_GAIN,rate);
        }else{
            BloopMaterialBank.Material m=bank.at((fusionIndex+2)%4);
            shortVoice(m.attack,.055f*sizeGain(radius),pitchForRadius(radius));
        }
    }

    public void pop(int blobId,float radius) {
        cancelBlob(blobId);
        BloopMaterialBank.Material m=bank.at((fusionIndex+1)%4);
        shortVoice(m.attack,.14f*sizeGain(radius),pitchForRadius(radius));
    }

    /**
     * All one-shots are finite. No code path reads/loops the contact
     * texture, so static indefinite contact after finite tails is exact zero.
     * No allocation or Android call inside the sample loop.
     */
    public void renderMono(float[] output,int offset,int frames) {
        if(output==null||offset<0||frames<0||offset>output.length-frames)
            throw new IllegalArgumentException("Output buffer bounds");
        for(int s=offset;s<offset+frames;s++) {
            float dry=0f;
            for(int i=voices.size()-1;i>=0;i--) {
                Voice v=voices.get(i);
                dry+=v.sample();
                if(v.position>=v.sound.length){
                    if(v.fusion)fusionsCompleted++;
                    voices.remove(i);
                }
            }
            // Global gain protects summed complete Bloop voices without
            // clipping or deleting either full source.
            float need=Math.abs(dry)>.87f?.87f/Math.abs(dry):1f;
            if(need<masterGain)masterGain=need;
            else masterGain+=(need-masterGain)*limiterRelease;
            output[s]=dry*masterGain;
            peakOutput=Math.max(peakOutput,Math.abs(output[s]));
            peakVoices=Math.max(peakVoices,voices.size());
        }
    }

    public void reset() {
        pairs.clear();voices.clear();masterGain=1f;peakOutput=0f;
        fusionIndex=0;wallIndex=0;contactBeginCount=0;fusionsStarted=0;fusionsCompleted=0;
        shortVoicesRejected=0;peakVoices=0;squishEvents=0;impactEvents=0;
    }
    public int activePairs(){return pairs.size();}
    public int activeOneShots(){return voices.size();}
    public int contactBegins(){return contactBeginCount;}
    public int fusionsStarted(){return fusionsStarted;}
    public int fusionsCompleted(){return fusionsCompleted;}
    public int rejectedShortVoices(){return shortVoicesRejected;}
    public int peakVoices(){return peakVoices;}
    public float peakOutput(){return peakOutput;}
    public int squishEvents(){return squishEvents;}
    public int impactEvents(){return impactEvents;}
}
