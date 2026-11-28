package com.maryvellous.lavablobs.audio;

/** Finite one-shot; no loop, truncation or allocation during rendering.
 * Refined playback fades only the first 0.5 ms and last 1.5 ms of output,
 * including the fractional end of resampled clips. Source PCM is untouched. */
final class PcmVoice {
    private float[] sound;
    boolean fusion;
    MaterialAudioEngine.VoiceFamily family;
    private float volume,rate;
    private boolean declick;
    private int totalFrames,attack,release;
    private double position;
    private int frame;
    PcmVoice(float[] sound,float volume,float rate,boolean fusion,boolean declick){
        this(sound,volume,rate,fusion,declick,fusion?MaterialAudioEngine.VoiceFamily.FUSION:MaterialAudioEngine.VoiceFamily.WALL);
    }
    PcmVoice(float[] sound,float volume,float rate,boolean fusion,boolean declick,MaterialAudioEngine.VoiceFamily family){
        start(sound,volume,rate,fusion,declick,family);
    }
    PcmVoice(){}
    void start(float[] sound,float volume,float rate,boolean fusion,boolean declick,MaterialAudioEngine.VoiceFamily family){
        this.sound=sound;this.volume=volume;this.rate=rate;this.fusion=fusion;this.declick=declick;this.family=family;
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
        float value=sound[at]*(1-t)+sound[next]*t;
        position+=rate;
        if(declick){
            float edge=Math.max(0f,Math.min(1f,Math.min(frame/(float)attack,(totalFrames-1-frame)/(float)release)));
            // Cubic smoothstep has zero slope at both ends.
            value*=edge*edge*(3f-2f*edge);
        }
        frame++;
        return volume*value;
    }
}
