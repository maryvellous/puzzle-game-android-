package com.maryvellous.lavablobs.audio;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.maryvellous.lavablobs.audio.MaterialAudioEngine.VoiceFamily.*;

public class PcmMixerTest {
    private static float[] clip(){
        float[] a=new float[401];
        for(int i=0;i<a.length;i++)a[i]=(float)(.2*Math.sin(i*.17));
        return a;
    }
    @Test public void recyclingResetsFractionalPlaybackAndEnvelope(){
        float[] a=clip();
        PcmVoice reused=new PcmVoice();
        reused.start(a,.8f,1.23f,false,true,WALL);
        for(int i=0;i<97;i++)reused.sample();
        reused.release();
        assertTrue(reused.finished());assertEquals(0f,reused.sample(),0f);
        reused.start(a,.48f,.7f,true,true,FUSION);
        PcmVoice fresh=new PcmVoice(a,.48f,.7f,true,true,FUSION);
        while(!fresh.finished())assertEquals(fresh.sample(),reused.sample(),0f);
        assertTrue(reused.finished());assertTrue(reused.fusion);
        assertEquals(FUSION,reused.family);
    }
    @Test public void commonBurstsRequireNoAdditionalVoiceAllocation(){
        PcmMixer m=new PcmMixer(MaterialAudioEngine.MixProfile.REFINED);
        float[] a=clip();
        long allocations=m.allocatedVoices();
        for(int round=0;round<10;round++){
            for(int i=0;i<24;i++)assertTrue(m.shortVoice(WALL,a,.3f,1f));
            assertFalse(m.shortVoice(POP,a,.3f,1f));
            for(int i=0;i<32;i++)m.fusion(a,1f);
            for(int i=0;i<1024;i++)assertTrue(Math.abs(m.sample())<=.870001f);
            assertEquals(0,m.activeOneShots());
        }
        assertEquals(320,m.fusionsCompleted());
        assertEquals(allocations,m.allocatedVoices());
        assertEquals(560,m.reusedVoices());
    }
    @Test public void poolCapacityIsNotFusionAdmissionLimit(){
        PcmMixer m=new PcmMixer(MaterialAudioEngine.MixProfile.REFINED);
        float[] a=clip();
        for(int i=0;i<90;i++)m.fusion(a,1f);
        assertEquals(90,m.activeOneShots());
        for(int i=0;i<1024;i++)m.sample();
        assertEquals(90,m.fusionsCompleted());
        long high=m.allocatedVoices();
        m.reset();
        for(int i=0;i<64;i++)m.fusion(a,1f);
        assertEquals(high,m.allocatedVoices());
        m.reset();
        for(int i=0;i<1024;i++)assertEquals(0f,m.sample(),0f);
    }
    @Test public void stableCompactionPreservesReverseSumWhenMiddleVoiceEnds(){
        PcmMixer m=new PcmMixer(MaterialAudioEngine.MixProfile.APPROVED);
        float[] longClip=clip(),shortClip=Arrays.copyOf(longClip,96);
        m.fusion(longClip,1f);m.fusion(shortClip,1f);m.fusion(longClip,.7f);
        PcmVoice a=new PcmVoice(longClip,.48f,1f,true,false);
        PcmVoice b=new PcmVoice(shortClip,.48f,1f,true,false);
        PcmVoice c=new PcmVoice(longClip,.48f,.7f,true,false);
        for(int i=0;i<1024;i++){
            float sum=0;
            if(!c.finished())sum+=c.sample();
            if(!b.finished())sum+=b.sample();
            if(!a.finished())sum+=a.sample();
            assertEquals(sum,m.sample(),0f);
        }
        assertEquals(3,m.fusionsCompleted());
    }
}
