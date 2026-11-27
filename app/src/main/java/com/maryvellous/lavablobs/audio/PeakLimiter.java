package com.maryvellous.lavablobs.audio;

import java.util.Arrays;

/** Sample-peak limiter, allocation-free after construction. The reference
 * uses the approved instantaneous attack; the refined path delays PCM by
 * D frames, sliding-mins the gain over D+1 samples and averages those minima.
 * Every term of the average covers the delayed sample, so gain never exceeds
 * that sample's safe gain. Exponential release cannot increase above target.
 * Not a true-peak/inter-sample limiter. */
final class PeakLimiter {
    static final float CEILING=.87f;
    private final float release;
    private final int delay,window,capacity;
    private final float[] pcm,minima,dequeValues;
    private final long[] dequeFrames;
    private int pcmAt,meanAt,head,tail,count;
    private long frame;
    private double sum;
    private float gain=1f,releaseGain=1f,peakGainStep;
    PeakLimiter(int rate,int delay){
        if(rate<=0||delay<0)throw new IllegalArgumentException();
        this.delay=delay;window=delay+1;capacity=window+1;
        release=(float)(1-Math.exp(-1d/(rate*.150)));
        pcm=new float[Math.max(1,delay)];
        minima=new float[window];dequeValues=new float[capacity];dequeFrames=new long[capacity];
        reset();
    }
    int latency(){return delay;}
    float gain(){return gain;}
    float peakGainStep(){return peakGainStep;}
    private int next(int index){return index+1==capacity?0:index+1;}
    private int prev(int index){return index==0?capacity-1:index-1;}
    float process(float input){
        float need=Math.abs(input)>CEILING?CEILING/Math.abs(input):1f;
        float before=gain;
        if(delay==0){
            if(need<gain)gain=need;
            else gain+=(need-gain)*release;
            peakGainStep=Math.max(peakGainStep,Math.abs(gain-before));
            return input*gain;
        }
        while(count>0&&dequeFrames[head]<frame-delay){head=next(head);count--;}
        while(count>0&&dequeValues[prev(tail)]>=need){tail=prev(tail);count--;}
        dequeFrames[tail]=frame;dequeValues[tail]=need;tail=next(tail);count++;
        float min=dequeValues[head];
        if(min<releaseGain)releaseGain=min;
        else releaseGain+=(min-releaseGain)*release;
        sum+=releaseGain-minima[meanAt];
        minima[meanAt]=releaseGain;meanAt=(meanAt+1)%window;
        gain=(float)Math.max(0,Math.min(1,sum/window));
        float delayed=pcm[pcmAt];pcm[pcmAt]=input;pcmAt=(pcmAt+1)%delay;frame++;
        // Guard against rounding at the sample ceiling, not a normal attack.
        float safe=Math.abs(delayed)>CEILING?CEILING/Math.abs(delayed):1f;
        gain=Math.min(gain,safe);
        peakGainStep=Math.max(peakGainStep,Math.abs(gain-before));
        return delayed*gain;
    }
    void reset(){
        Arrays.fill(pcm,0f);Arrays.fill(minima,1f);
        pcmAt=meanAt=head=tail=count=0;frame=0;sum=window;
        gain=releaseGain=1f;peakGainStep=0f;
    }
}
