package com.maryvellous.lavablobs.audio;

/** Finite one-shot; no loop, truncation or allocation during rendering.
 * Refined playback fades only the first 0.5 ms and last 1.5 ms of output,
 * including the fractional end of resampled clips. Source PCM is untouched. */
final class PcmVoice {
    private float[] sound;
    boolean fusion;
    MaterialAudioEngine.VoiceFamily family;
    private float volume,rate;
    private boolean declick,cubic;
    private int totalFrames,attack,release;
    private double position;
    private int frame;
    PcmVoice(float[] sound,float volume,float rate,boolean fusion,boolean declick){
        this(sound,volume,rate,fusion,declick,fusion?MaterialAudioEngine.VoiceFamily.FUSION:MaterialAudioEngine.VoiceFamily.WALL);
    }
    PcmVoice(float[] sound,float volume,float rate,boolean fusion,boolean declick,MaterialAudioEngine.VoiceFamily family){
        this(sound,volume,rate,fusion,declick,family,false);
    }
    PcmVoice(float[] sound,float volume,float rate,boolean fusion,boolean declick,MaterialAudioEngine.VoiceFamily family,boolean cubic){
        start(sound,volume,rate,fusion,declick,family,cubic);
    }
    PcmVoice(){}
    void start(float[] sound,float volume,float rate,boolean fusion,boolean declick,MaterialAudioEngine.VoiceFamily family,boolean cubic){
        this.sound=sound;this.volume=volume;this.rate=rate;this.fusion=fusion;this.declick=declick;this.family=family;this.cubic=cubic;
        position=0;frame=0;
        totalFrames=(int)Math.ceil(sound.length/(double)rate);
        attack=Math.max(1,Math.min(totalFrames/2,Math.round(BloopMaterialBank.SAMPLE_RATE*.0005f)));
        release=Math.max(1,Math.min(totalFrames/2,Math.round(BloopMaterialBank.SAMPLE_RATE*.0015f)));
    }
    void release(){sound=null;family=null;position=0;frame=0;}
    boolean finished(){return sound==null||position>=sound.length;}
    float sample(){
        if(finished())return 0f;
        int at=(int)position,next=Math.min(at+1,sound.length-1);
        float t=(float)(position-at);
        float value=cubic?interpolateCubic(sound,at,t):sound[at]*(1-t)+sound[next]*t;
        position+=rate;
        if(declick){
            float edge=Math.max(0f,Math.min(1f,Math.min(frame/(float)attack,(totalFrames-1-frame)/(float)release)));
            // Cubic smoothstep has zero slope at both ends.
            value*=edge*edge*(3f-2f*edge);
        }
        frame++;
        return volume*value;
    }
    /** Four-point Catmull-Rom interpolation; exact at integer positions.
     * Bound overshoot to the four input samples, including replicated edges.
     * This is interpolation, not a full band-limited resampler. */
    static float interpolateCubic(float[] x,int at,float t){
        float a=x[Math.max(0,at-1)],b=x[at],c=x[Math.min(x.length-1,at+1)],d=x[Math.min(x.length-1,at+2)];
        float v=b+.5f*t*((c-a)+t*((2f*a-5f*b+4f*c-d)+t*(3f*(b-c)+d-a)));
        return Math.max(Math.min(Math.min(a,b),Math.min(c,d)),Math.min(Math.max(Math.max(a,b),Math.max(c,d)),v));
    }
}
