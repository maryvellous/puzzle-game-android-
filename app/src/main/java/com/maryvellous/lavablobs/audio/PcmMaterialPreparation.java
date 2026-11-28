package com.maryvellous.lavablobs.audio;

import java.io.IOException;

/** Shared post-decode PCM preparation. Android and desktop keep their own
 * MP3 decoders but use exactly the same region, envelope and launch rules. */
public final class PcmMaterialPreparation {
    private static final int RATE=BloopMaterialBank.SAMPLE_RATE;
    private PcmMaterialPreparation(){}
    public static BloopMaterialBank.Material prepareMaterial(float[] full) throws IOException {
        int[] range=measureRegions(full);
        return new BloopMaterialBank.Material(full,fragment(full,range[0],range[1]),fragment(full,range[2],range[3]));
    }
    public static float[] prepareLaunch(float[] source) throws IOException {
        int start=Math.round(source.length*.47f),end=Math.round(source.length*.94f);
        int count=end-start;
        if(count<96)throw new IOException("Release source too short");
        float[] clip=new float[count];
        int fadeIn=RATE*5/1000,fadeOut=RATE*13/1000;
        float peak=0f;
        for(int i=0;i<count;i++){
            float envelope=Math.min(1f,Math.min((i+1f)/fadeIn,(count-i)/(float)fadeOut));
            clip[i]=source[end-1-i]*envelope;
            peak=Math.max(peak,Math.abs(clip[i]));
        }
        if(peak<.001f)throw new IOException("Release excerpt effectively silent");
        return clip;
    }

    /** [onset, endAttack, startTail, activeEnd] in samples. */
    public static int[] measureRegions(float[] audio) throws IOException {
        final int stride=Math.round(RATE*.005f);
        final int count=(audio.length+stride-1)/stride;
        float[] raw=new float[count];
        for(int frame=0;frame<count;frame++){
            double sum=0;
            for(int n=frame*stride;n<(frame+1)*stride;n++){
                float sample=n<audio.length?audio[n]:0f;
                sum+=sample*sample;
            }
            raw[frame]=(float)Math.sqrt(sum/stride+1e-14);
        }
        float[] levels=new float[count];
        float peak=0f;
        for(int i=0;i<count;i++){
            levels[i]=((i>0?raw[i-1]:0f)+raw[i]+
                    (i+1<count?raw[i+1]:0f))/3f;
            peak=Math.max(peak,levels[i]);
        }
        float threshold=Math.max((float)Math.pow(10,-50d/20d),peak*.025f);
        int first=-1,last=-1,peakFrame=0;
        for(int i=0;i<count;i++){
            if(levels[i]>=threshold){if(first<0)first=i;last=i;}
            if(levels[i]>levels[peakFrame])peakFrame=i;
        }
        if(first<0||last<=first)throw new IOException("Empty Bloop material");
        int onset=first*stride;
        int end=Math.min(audio.length,(last+1)*stride);
        if(end-onset<RATE*.065f)
            throw new IOException("Bloop material too short");
        int attackFrame=Math.min(first+4,Math.max(first+2,peakFrame-2));
        int attackEnd=Math.min(attackFrame*stride,end-(int)(RATE*.030f));
        int decayBegin=Math.max(peakFrame+3,attackEnd/stride+3);
        int tailStart=end-(int)(RATE*.025f);
        for(int i=decayBegin;i<last-1;i++){
            if(levels[i]<=peak*.45f &&
                    (levels[i]+levels[i+1]+levels[i+2])/3f<=peak*.60f) {
                tailStart=i*stride;break;
            }
        }
        tailStart=Math.max(attackEnd+(int)(RATE*.012f),tailStart);
        tailStart=Math.min(tailStart,end-(int)(RATE*.012f));
        if(attackEnd<=onset||tailStart<=attackEnd||tailStart>=end)
            throw new IOException("Cannot separate Bloop attack and tail");
        return new int[]{onset,attackEnd,tailStart,end};
    }

    private static float[] fragment(float[] source,int start,int end) {
        float[] fragment=new float[end-start];
        System.arraycopy(source,start,fragment,0,fragment.length);
        int fade=Math.min(fragment.length/2,Math.round(RATE*.006f));
        for(int i=0;i<fade;i++){
            float t=(float)Math.sin(i*(Math.PI/2)/(Math.max(1,fade-1)));
            float gain=t*t;
            fragment[i]*=gain;
            fragment[fragment.length-1-i]*=gain;
        }
        return fragment;
    }

}
