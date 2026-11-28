package com.maryvellous.lavablobs.audio;

import java.util.ArrayList;
import java.util.ArrayDeque;
import com.maryvellous.lavablobs.audio.MaterialAudioEngine.MixProfile;
import com.maryvellous.lavablobs.audio.MaterialAudioEngine.VoiceFamily;

/** Audio-thread-owned PCM renderer. Physical contact policy is deliberately
 * outside this class. Voice recycling is bounded, never limits fusion playback.
 * Sum in reverse insertion order, exactly as the reference renderer did.
 * Stable compaction runs once per frame with finished voices. */
final class PcmMixer {
    private static final int MAX_SHORT_VOICES=24,RETAINED_VOICES=64;
    static final float SECONDARY_GAIN=3f;
    private final ArrayList<PcmVoice> voices=new ArrayList<>(RETAINED_VOICES);
    private final ArrayDeque<PcmVoice> free=new ArrayDeque<>(RETAINED_VOICES);
    private final MixProfile profile;
    private final PeakLimiter limiter;
    private float[][] diagnosticStems;
    private int activeShortVoices,peakShortVoices,fusionsStarted,fusionsCompleted,shortVoicesRejected,peakVoices;
    private long allocatedVoices,reusedVoices;
    private float peakOutput,previousOutput,peakSampleStep,peakDry,minimumLimiterGain=1f;
    private long limitedFrames,renderedFrames;
    private float outputGain=1f,targetOutputGain=1f;
    private final float volumeSmoothing=(float)(1-Math.exp(-1d/(MaterialAudioEngine.SAMPLE_RATE*.008)));

    PcmMixer(MixProfile profile){
        this.profile=profile;
        limiter=new PeakLimiter(MaterialAudioEngine.SAMPLE_RATE,profile!=MixProfile.APPROVED?
                Math.round(MaterialAudioEngine.SAMPLE_RATE*.003f):0);
        // Allocate common burst capacity before any audio is rendered.
        for(int i=0;i<RETAINED_VOICES;i++)free.addLast(new PcmVoice());
        allocatedVoices=RETAINED_VOICES;
    }
    private void start(VoiceFamily family,float[] sound,float volume,float rate,boolean fusion){
        PcmVoice v=free.pollLast();
        if(v==null){v=new PcmVoice();allocatedVoices++;}else reusedVoices++;
        v.start(sound,volume,rate,fusion,profile!=MixProfile.APPROVED,family,profile==MixProfile.CUBIC);
        voices.add(v);
    }
    private void recycle(PcmVoice v){
        v.release();
        if(free.size()<RETAINED_VOICES)free.addLast(v);
    }
    boolean shortVoice(VoiceFamily family,float[] sound,float volume,float rate){
        if(activeShortVoices>=MAX_SHORT_VOICES){shortVoicesRejected++;return false;}
        if(!(volume>.0001f))return false;
        start(family,sound,volume*SECONDARY_GAIN,rate,false);
        activeShortVoices++;peakShortVoices=Math.max(peakShortVoices,activeShortVoices);
        return true;
    }
    void fusion(float[] sound,float rate){
        start(VoiceFamily.FUSION,sound,.48f,rate,true);fusionsStarted++;
    }
    void setOutputVolumePercent(int percent){
        float position=Math.max(0f,Math.min(1f,percent/100f));
        targetOutputGain=position*position;
        if(percent<=0)outputGain=0f;
    }
    float[][] captureDryStems(int frames){
        if(renderedFrames!=0||frames<=0)throw new IllegalStateException("Capture requires fresh engine and positive length");
        diagnosticStems=new float[VoiceFamily.values().length][frames];return diagnosticStems;
    }
    float sample(){
        float dry=0f;
        boolean ended=false;
        for(int i=voices.size()-1;i>=0;i--){
            PcmVoice v=voices.get(i);
            float sample=v.sample();dry+=sample;
            if(diagnosticStems!=null&&renderedFrames<diagnosticStems[0].length)
                diagnosticStems[v.family.ordinal()][(int)renderedFrames]+=sample;
            if(v.finished()){
                if(v.fusion)fusionsCompleted++;else activeShortVoices--;
                recycle(v);voices.set(i,null);ended=true;
            }
        }
        if(ended){
            int write=0;
            for(int read=0;read<voices.size();read++){
                PcmVoice v=voices.get(read);
                if(v!=null)voices.set(write++,v);
            }
            while(voices.size()>write)voices.remove(voices.size()-1);
        }
        peakDry=Math.max(peakDry,Math.abs(dry));
        float limited=limiter.process(dry);
        minimumLimiterGain=Math.min(minimumLimiterGain,limiter.gain());
        if(limiter.gain()<.999f)limitedFrames++;
        renderedFrames++;
        outputGain+=(targetOutputGain-outputGain)*volumeSmoothing;
        float out=limited*outputGain;
        peakSampleStep=Math.max(peakSampleStep,Math.abs(out-previousOutput));previousOutput=out;
        peakOutput=Math.max(peakOutput,Math.abs(out));
        peakVoices=Math.max(peakVoices,voices.size());
        return out;
    }
    void reset(){
        for(int i=voices.size()-1;i>=0;i--)recycle(voices.get(i));
        voices.clear();activeShortVoices=peakShortVoices=0;
        diagnosticStems=null;limiter.reset();
        peakOutput=previousOutput=peakSampleStep=0f;outputGain=targetOutputGain;
        peakDry=0f;minimumLimiterGain=1f;limitedFrames=renderedFrames=0;
        fusionsStarted=fusionsCompleted=shortVoicesRejected=peakVoices=0;
        // Allocation counters intentionally remain lifetime diagnostics.
    }
    int activeOneShots(){return voices.size();}
    int fusionsStarted(){return fusionsStarted;}
    int fusionsCompleted(){return fusionsCompleted;}
    int rejectedShortVoices(){return shortVoicesRejected;}
    int peakVoices(){return peakVoices;}
    int peakShortVoices(){return peakShortVoices;}
    float peakOutput(){return peakOutput;}
    float peakGainStep(){return limiter.peakGainStep();}
    float peakSampleStep(){return peakSampleStep;}
    int mixLatencyFrames(){return limiter.latency();}
    float peakDry(){return peakDry;}
    float minimumLimiterGain(){return minimumLimiterGain;}
    long limitedFrames(){return limitedFrames;}
    long renderedFrames(){return renderedFrames;}
    long allocatedVoices(){return allocatedVoices;}
    long reusedVoices(){return reusedVoices;}
}
