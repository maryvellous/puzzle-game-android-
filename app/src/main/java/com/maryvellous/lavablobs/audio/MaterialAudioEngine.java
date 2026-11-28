package com.maryvellous.lavablobs.audio;

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
    public enum MixProfile { APPROVED, REFINED, CUBIC }
    public enum VoiceFamily { FUSION, GEL, WALL, SQUISH, LAUNCH, POP }
    private final PcmMixer mixer;
    private final AcousticAggregator aggregator;
    private int fusionIndex,contactBeginCount;
    private int squishEvents,impactEvents;
    /** Offline-only dry stems; never alters the mix or physical event budget. */
    public float[][] captureDryStems(int frames){return mixer.captureDryStems(frames);}
    /** Perceptual control after the limiter. */
    public void setOutputVolumePercent(int percent){mixer.setOutputVolumePercent(percent);}

    public MaterialAudioEngine(BloopMaterialBank bank) {this(bank,null);}

    public MaterialAudioEngine(BloopMaterialBank bank,float[] releaseSwoosh) {this(bank,releaseSwoosh,false);}

    /** Aggregation is experimental; default construction preserves V2.3. */
    public MaterialAudioEngine(BloopMaterialBank bank,float[] releaseSwoosh,boolean aggregation) {
        this(bank,releaseSwoosh,aggregation,MixProfile.APPROVED);
    }
    public MaterialAudioEngine(BloopMaterialBank bank,float[] releaseSwoosh,boolean aggregation,MixProfile profile) {
        if(bank==null||profile==null)throw new IllegalArgumentException("Material bank and mix profile required");
        this.bank=bank;mixer=new PcmMixer(profile);
        aggregator=aggregation?new AcousticAggregator((family,sound,gain,rate)->shortVoice(family.voiceFamily,sound,gain,rate)):null;
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

    private boolean shortVoice(VoiceFamily family,float[] audio,float volume,float rate) {
        return mixer.shortVoice(family,audio,volume,rate);
    }

    private boolean gesture(AcousticAggregator.Family family,float[] audio,float gain,float rate){
        return gesture(family,0,0,audio,gain,rate);
    }
    private boolean gesture(AcousticAggregator.Family family,int a,int b,float[] audio,float gain,float rate){
        return aggregator==null?shortVoice(family.voiceFamily,audio,gain,rate):aggregator.offer(family,mixer.renderedFrames(),a,b,audio,gain,rate);
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
            if(gesture(AcousticAggregator.Family.GEL,gelImpacts[pair.materialIndex],(.045f+.32f*dynamic)*pair.scale,
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
                if(gesture(AcousticAggregator.Family.SQUISH,idA,idB,pair.material.tail,dynamicGain*pair.scale,pair.rate)) {
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
        if(aggregator!=null)aggregator.cancelBlob(blobId);
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
        mixer.fusion(material.complete,pitchForRadius(radius));
    }

    /** Actual rigid-wall onset: original V2.2 impact timbre and gain. */
    public void wallImpact(float approach,float radius){
        if(!Float.isFinite(approach)||!Float.isFinite(radius)||approach<=IMPACT_MIN_SPEED)return;
        float magnitude=clamp((float)(Math.log1p((approach-IMPACT_MIN_SPEED)/40f)/Math.log1p(280d/40d)),0f,1f);
        gesture(AcousticAggregator.Family.WALL,bank.at(wallIndex++).attack,.18f*magnitude*sizeGain(radius),pitchForRadius(radius));
    }

    public void launch(float radius) {launch(0,radius);}

    public void launch(int tier,float radius) {
        if(releaseSwoosh!=null){
            // Same reversed swoosh and tier-dependent playback rate as 1.0.11.
            // Slightly stronger than the historical .16 to suit this diagnostic mix.
            float rate=Math.max(1.08f,1.23f-.035f*Math.max(0,tier));
            shortVoice(VoiceFamily.LAUNCH,releaseSwoosh,.20f/PcmMixer.SECONDARY_GAIN,rate);
        }else{
            BloopMaterialBank.Material m=bank.at((fusionIndex+2)%4);
            shortVoice(VoiceFamily.LAUNCH,m.attack,.055f*sizeGain(radius),pitchForRadius(radius));
        }
    }

    public void pop(int blobId,float radius) {
        cancelBlob(blobId);
        BloopMaterialBank.Material m=bank.at((fusionIndex+1)%4);
        shortVoice(VoiceFamily.POP,m.attack,.14f*sizeGain(radius),pitchForRadius(radius));
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
            if(aggregator!=null)aggregator.flushDue(mixer.renderedFrames());
            output[s]=mixer.sample();
        }
    }

    public void reset() {
        pairs.clear();mixer.reset();
        if(aggregator!=null)aggregator.reset();
        fusionIndex=0;wallIndex=0;contactBeginCount=0;squishEvents=0;impactEvents=0;
    }
    public int activePairs(){return pairs.size();}
    public int activeOneShots(){return mixer.activeOneShots();}
    public int contactBegins(){return contactBeginCount;}
    public int fusionsStarted(){return mixer.fusionsStarted();}
    public int fusionsCompleted(){return mixer.fusionsCompleted();}
    public int rejectedShortVoices(){return mixer.rejectedShortVoices();}
    public int peakVoices(){return mixer.peakVoices();}
    public int peakShortVoices(){return mixer.peakShortVoices();}
    public long allocatedVoices(){return mixer.allocatedVoices();}
    public long reusedVoices(){return mixer.reusedVoices();}
    public long aggregateCandidates(){return aggregator==null?0:aggregator.candidates();}
    public long aggregateGroups(){return aggregator==null?0:aggregator.emitted();}
    public long aggregateCollapsed(){return aggregator==null?0:aggregator.collapsed();}
    public long aggregateRejected(){return aggregator==null?0:aggregator.rejected();}
    public long aggregateCancelled(){return aggregator==null?0:aggregator.cancelled();}
    public float peakOutput(){return mixer.peakOutput();}
    public float peakGainStep(){return mixer.peakGainStep();}
    public float peakSampleStep(){return mixer.peakSampleStep();}
    public int mixLatencyFrames(){return mixer.mixLatencyFrames();}
    public float peakDry(){return mixer.peakDry();}
    public float minimumLimiterGain(){return mixer.minimumLimiterGain();}
    public long limitedFrames(){return mixer.limitedFrames();}
    public long renderedFrames(){return mixer.renderedFrames();}
    public int squishEvents(){return squishEvents;}
    public int impactEvents(){return impactEvents;}
}
