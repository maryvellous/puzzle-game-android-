package com.maryvellous.lavablobs.audio;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class AudioSchedulerTest {
    static BloopMaterialBank bank(){
        BloopMaterialBank.Material[] materials=new BloopMaterialBank.Material[4];
        for(int j=0;j<4;j++){
            float[] full=new float[1024],attack=new float[128],tail=new float[128];
            Arrays.fill(full,.25f+j*.01f);Arrays.fill(attack,.2f);Arrays.fill(tail,.15f);
            materials[j]=new BloopMaterialBank.Material(full,attack,tail);
        }
        return new BloopMaterialBank(materials);
    }
    static AudioEvent fusion(double time,int generation,int id){
        return new AudioEvent(AudioEvent.Type.MERGE,id,id+1,0,0,0,32,0,1f/120f,0,time,generation,0);
    }
    static float[] replay(boolean delayed){
        AudioEventQueue<AudioEvent> q=new AudioEventQueue<>(32,8);
        AudioBlockScheduler scheduler=new AudioBlockScheduler(q,new AudioTimeline(44100,384,5292),512);
        MaterialAudioEngine engine=new MaterialAudioEngine(bank());
        q.offer(fusion(0,1,1),true);
        if(!delayed)q.offer(fusion(.01,1,3),true);
        float[] result=new float[384*8],block=new float[384];
        for(int i=0;i<8;i++){
            if(delayed&&i==1)q.offer(fusion(.01,1,3),true);
            assertTrue(scheduler.render(engine,block,1,()->1));
            System.arraycopy(block,0,result,i*384,384);
        }
        assertEquals(2,engine.fusionsStarted());assertEquals(2,engine.fusionsCompleted());
        assertEquals(0,scheduler.lateEvents());assertEquals(0,engine.activeOneShots());
        return result;
    }
    @Test public void samePhysicalSequenceAcrossDeliveryBatches(){
        float[] a=replay(false),b=replay(true);
        assertArrayEquals(a,b,0f);
        assertEquals(0f,a[383],0f);assertTrue(a[384]>0f);
        assertEquals(0f,a[a.length-1],0f);
    }
    @Test public void resetDoesNotEatNextGeneration(){
        AudioEventQueue<AudioEvent> q=new AudioEventQueue<>(8,2);
        AudioBlockScheduler scheduler=new AudioBlockScheduler(q,new AudioTimeline(44100,0,5292),512);
        MaterialAudioEngine engine=new MaterialAudioEngine(bank());
        q.offer(fusion(0,2,3),true);
        assertTrue(scheduler.render(engine,new float[384],1,()->1));
        assertEquals(1,q.size());assertEquals(0,engine.fusionsStarted());
        scheduler.reset();engine.reset();
        assertTrue(scheduler.render(engine,new float[384],2,()->2));
        assertEquals(1,engine.fusionsStarted());
    }
    @Test public void limiterDiagnosticsDoNotAlterHeadroom(){
        MaterialAudioEngine engine=new MaterialAudioEngine(bank());
        for(int i=0;i<12;i++)engine.merge(1+2*i,2+2*i,32);
        float[] block=new float[2048];engine.renderMono(block,0,block.length);
        assertTrue(engine.peakDry()>.87f);assertTrue(engine.minimumLimiterGain()<1f);
        assertTrue(engine.limitedFrames()>0);assertEquals(2048,engine.renderedFrames());
        assertTrue(engine.peakOutput()<=.871f);assertEquals(12,engine.fusionsCompleted());
    }
}
