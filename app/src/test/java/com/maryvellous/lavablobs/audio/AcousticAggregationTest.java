package com.maryvellous.lavablobs.audio;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;

public class AcousticAggregationTest {
    private static float[] render(MaterialAudioEngine engine,int n){
        float[] pcm=new float[n];engine.renderMono(pcm,0,n);return pcm;
    }
    @Test public void singletonPreservesSamplesAfterWindowDelay(){
        MaterialAudioEngine dry=new MaterialAudioEngine(AudioSchedulerTest.bank());
        MaterialAudioEngine wet=new MaterialAudioEngine(AudioSchedulerTest.bank(),null,true);
        dry.wallImpact(280,32);wet.wallImpact(280,32);
        float[] a=render(dry,2000),b=render(wet,3000);
        int delay=Math.round(44100*.012f);
        for(int i=0;i<delay;i++)assertEquals(0f,b[i],0f);
        for(int i=0;i<a.length;i++)assertEquals(a[i],b[i+delay],0f);
        assertEquals(1,wet.aggregateGroups());assertEquals(0,wet.aggregateCollapsed());
    }
    @Test public void compatibleBurstReducesPolyphonyAndKeepsFusionComplete(){
        MaterialAudioEngine dry=new MaterialAudioEngine(AudioSchedulerTest.bank());
        MaterialAudioEngine wet=new MaterialAudioEngine(AudioSchedulerTest.bank(),null,true);
        for(int i=0;i<8;i++){dry.wallImpact(1000,40);wet.wallImpact(1000,40);}
        dry.merge(1,2,32);wet.merge(1,2,32);
        float[] a=render(dry,4096),b=render(wet,4096);
        assertEquals(8,dry.peakShortVoices());assertEquals(1,wet.peakShortVoices());
        assertEquals(8,wet.aggregateCandidates());assertEquals(1,wet.aggregateGroups());
        assertEquals(7,wet.aggregateCollapsed());assertEquals(0,wet.aggregateRejected());
        assertEquals(1,wet.fusionsStarted());assertEquals(1,wet.fusionsCompleted());
        assertTrue(wet.minimumLimiterGain()>=dry.minimumLimiterGain());
        assertTrue(wet.peakOutput()<=.871f);assertEquals(0f,b[b.length-1],0f);
        assertTrue(a[0]>0f);assertTrue(b[0]>0f);
    }
    @Test public void familyAndPitchRemainDistinctAndWindowsDoNotExtend(){
        List<Float> emitted=new ArrayList<>();
        AcousticAggregator agg=new AcousticAggregator((clip,gain,rate)->{emitted.add(gain);return true;});
        float[] sound=new float[]{.2f};
        agg.offer(AcousticAggregator.Family.WALL,0,sound,.2f,1f);
        agg.offer(AcousticAggregator.Family.GEL,0,sound,.2f,1f);
        agg.offer(AcousticAggregator.Family.WALL,0,sound,.2f,.7f);
        agg.offer(AcousticAggregator.Family.WALL,500,sound,.2f,1f);
        agg.flushDue(528);assertTrue(emitted.isEmpty());
        agg.flushDue(529);assertEquals(3,emitted.size());
        assertTrue(emitted.get(0)>.2f&&emitted.get(0)<=.3f);
        assertEquals(1,agg.collapsed());
    }
    @Test public void resetCancelsPendingClusters(){
        MaterialAudioEngine engine=new MaterialAudioEngine(AudioSchedulerTest.bank(),null,true);
        engine.wallImpact(300,32);engine.reset();
        for(float s:render(engine,4096))assertEquals(0f,s,0f);
        assertEquals(0,engine.aggregateGroups());
    }
    @Test public void removedBlobCancelsOnlyItsPendingDeformation(){
        List<Float> emitted=new ArrayList<>();
        AcousticAggregator agg=new AcousticAggregator((clip,gain,rate)->{emitted.add(gain);return true;});
        float[] sound={.2f};
        agg.offer(AcousticAggregator.Family.SQUISH,0,1,2,sound,.4f,1f);
        agg.offer(AcousticAggregator.Family.SQUISH,0,3,4,sound,.2f,1f);
        agg.offer(AcousticAggregator.Family.GEL,0,1,2,sound,.3f,1f);
        agg.cancelBlob(1);
        agg.flushDue(529);assertEquals(1,emitted.size());assertEquals(.3f,emitted.get(0),0f);
        agg.flushDue(1323);assertEquals(2,emitted.size());assertEquals(.2f,emitted.get(1),0f);
        assertEquals(1,agg.cancelled());assertEquals(0,agg.collapsed());
    }
    @Test public void entirePendingDeformationCanBeCancelled(){
        AcousticAggregator agg=new AcousticAggregator((clip,gain,rate)->{fail("Cancelled cluster played");return true;});
        agg.offer(AcousticAggregator.Family.SQUISH,0,1,2,new float[]{.2f},.2f,1f);
        agg.cancelBlob(2);agg.flushDue(10000);
        assertEquals(1,agg.cancelled());assertEquals(0,agg.emitted());
    }
}
