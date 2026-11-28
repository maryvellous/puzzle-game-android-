package com.maryvellous.lavablobs.audio;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayDeque;
import java.util.Random;

public class AudioRingTest {
    @Test public void randomizedRingMatchesReferenceFifo(){
        AudioEventQueue<Integer> q=new AudioEventQueue<>(17,4);
        ArrayDeque<Integer> ref=new ArrayDeque<>();
        Random r=new Random(98);
        long obs=0,controls=0;int high=0;
        for(int i=0;i<20000;i++){
            int op=r.nextInt(20);
            if(op<12){
                boolean authoritative=r.nextBoolean();
                boolean accepted=ref.size()<(authoritative?17:13);
                assertEquals(accepted,q.offer(i,authoritative));
                if(accepted){ref.add(i);high=Math.max(high,ref.size());}
                else if(authoritative)controls++;else obs++;
            }else if(op<16){
                assertEquals(ref.pollFirst(),q.poll());
            }else if(op<19){
                int expected=i-r.nextInt(50);
                Integer first=ref.peekFirst();
                assertEquals(first!=null&&first<=expected?ref.pollFirst():null,q.pollAtMost(x->x,expected));
            }else{ref.clear();q.clear();}
            assertEquals(ref.size(),q.size());
            assertEquals(high,q.highWater());assertEquals(obs,q.rejectedObservations());assertEquals(controls,q.rejectedControls());
        }
    }
    @Test public void fullWrappedQueueClearsAndRetainsLifetimeCounters(){
        AudioEventQueue<Integer> q=new AudioEventQueue<>(4,1);
        for(int i=0;i<4;i++)assertTrue(q.offer(i,true));
        assertFalse(q.offer(4,true));assertEquals(Integer.valueOf(0),q.poll());
        assertTrue(q.offer(4,true));q.clear();assertNull(q.poll());
        assertEquals(4,q.highWater());assertEquals(1,q.rejectedControls());
        assertTrue(q.offer(9,false));assertNull(q.pollAtMost(x->x,8));
        assertEquals(Integer.valueOf(9),q.pollAtMost(x->x,9));
    }
}
