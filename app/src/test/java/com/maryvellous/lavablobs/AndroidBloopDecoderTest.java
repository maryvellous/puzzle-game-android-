package com.maryvellous.lavablobs;

import org.junit.Test;

import static org.junit.Assert.*;

/** The offline segmentation heuristic must also run deterministically on Java. */
public class AndroidBloopDecoderTest {
    @Test public void measuredAttackAndTailNeverIncludeTheFusionCore() throws Exception {
        final int rate=44100;
        float[] audio=new float[(int)(.60f*rate)];
        for(int i=0;i<audio.length;i++) {
            float t=i/(float)rate;
            float strength=0f;
            if(t>.04f&&t<.26f) {
                if(t<.08f)strength=(t-.04f)/.04f*.85f;
                else if(t<.115f)strength=.85f;
                else strength=.85f*(.26f-t)/.145f;
            }
            audio[i]=(float)(strength*Math.sin(2d*Math.PI*205d*t));
        }
        int[] r=AndroidBloopDecoder.measureRegions(audio);
        assertTrue(r[0]>0);
        assertTrue(r[0]<r[1]);
        assertTrue(r[1]<r[2]);
        assertTrue(r[2]<r[3]);
        assertTrue(r[3]<audio.length);
        assertTrue("Attack is short",r[1]-r[0]<.045f*rate);
        assertTrue("Tail must begin after the peak",r[2]>.09f*rate);
    }

    @Test public void releaseExcerptIsReversedAndFadedLikeClassic() throws Exception {
        float[] source=new float[10000];
        for(int i=0;i<source.length;i++)source[i]=i/10000f;
        float[] clip=AndroidBloopDecoder.prepareLaunch(source);
        assertEquals(4700,clip.length);
        assertEquals(.9099f,clip[300],.000001f);
        assertTrue(clip[0]<.01f);
        assertTrue(clip[clip.length-1]<.01f);
    }

    @Test public void silenceIsNotAValidBloop() {
        try {
            AndroidBloopDecoder.measureRegions(new float[44100]);
            fail("A silent source cannot become acoustic material");
        }catch(java.io.IOException expected){}
    }
}
