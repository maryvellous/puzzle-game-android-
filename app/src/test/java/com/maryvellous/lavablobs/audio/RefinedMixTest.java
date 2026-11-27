package com.maryvellous.lavablobs.audio;

import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class RefinedMixTest {
    @Test public void lookaheadCeilingAndGainStepHoldUnderStress(){
        PeakLimiter old=new PeakLimiter(44100,0),next=new PeakLimiter(44100,132);
        Random r=new Random(43);
        for(int i=0;i<44100;i++){
            float x=i%317==0?8f:(r.nextFloat()-.5f)*.6f;
            old.process(x);
            float y=next.process(x);
            assertTrue(Float.isFinite(y));assertTrue(Math.abs(y)<=.870001f);
        }
        assertTrue(old.peakGainStep()>.8f);
        assertTrue(next.peakGainStep()<.008f);
    }
    @Test public void belowCeilingIsUnchangedExceptDeclaredLatencyAndReset(){
        PeakLimiter lim=new PeakLimiter(44100,132);
        float[] signal=new float[1024];
        for(int i=0;i<signal.length;i++)signal[i]=(float)(.5*Math.sin(i*.1));
        for(int i=0;i<signal.length+132;i++){
            float output=lim.process(i<signal.length?signal[i]:0f);
            assertEquals(i<132?0f:signal[i-132],output,0f);
        }
        lim.process(10f);lim.reset();
        for(int i=0;i<1000;i++)assertEquals(0f,lim.process(0f),0f);
        assertEquals(1f,lim.gain(),0f);
    }
    @Test public void voiceBoundariesAreZeroEvenAtFractionalRate(){
        for(float rate:new float[]{.7f,1f,1.23f,1.35f}){
            float[] source=new float[401];java.util.Arrays.fill(source,.5f);
            PcmVoice voice=new PcmVoice(source,1f,rate,true,true);
            float first=voice.sample(),last=first;int frames=1;
            while(!voice.finished()){last=voice.sample();frames++;}
            assertEquals((int)Math.ceil(source.length/(double)rate),frames);
            assertEquals(0f,first,0f);assertEquals(0f,last,0f);assertEquals(0f,voice.sample(),0f);
        }
    }
    @Test public void bothRefinedModesKeepAllFusionsAndSettleToSilence(){
        for(boolean cluster:new boolean[]{false,true}){
            MaterialAudioEngine e=new MaterialAudioEngine(AudioSchedulerTest.bank(),null,cluster,MaterialAudioEngine.MixProfile.REFINED);
            for(int i=0;i<20;i++){e.wallImpact(300,32);e.merge(2*i+1,2*i+2,32);}
            float[] pcm=new float[44100];
            e.renderMono(pcm,0,pcm.length);
            assertEquals(20,e.fusionsStarted());assertEquals(20,e.fusionsCompleted());
            assertEquals(132,e.mixLatencyFrames());assertTrue(e.peakOutput()<=.870001f);
            assertTrue(e.peakGainStep()<=.008f);
            for(int i=pcm.length-1000;i<pcm.length;i++)assertEquals(0f,pcm[i],0f);
            e.merge(1,2,32);e.renderMono(pcm,0,100);e.reset();
            e.renderMono(pcm,0,pcm.length);
            for(float sample:pcm)assertEquals(0f,sample,0f);
        }
    }
}
