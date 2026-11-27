package com.maryvellous.lavablobs.audio;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioTransportTest {
    @Test public void observationsCannotEvictAcceptedControls(){
        AudioEventQueue<String> q=new AudioEventQueue<>(4,2);
        assertTrue(q.offer("contact",false));
        assertTrue(q.offer("merge",true));
        assertFalse(q.offer("wall",false));
        assertTrue(q.offer("end",true));
        assertTrue(q.offer("pop",true));
        assertFalse(q.offer("merge2",true));
        assertEquals(1L,q.rejectedObservations());
        assertEquals(1L,q.rejectedControls());
        assertEquals(4,q.highWater());
        assertEquals("contact",q.poll());
        assertEquals("merge",q.poll());
        assertEquals("end",q.poll());
        assertEquals("pop",q.poll());
        assertNull(q.poll());
    }
    @Test public void clearPreservesDiagnosticsButNotOldEvents(){
        AudioEventQueue<Integer> q=new AudioEventQueue<>(4,1);
        q.offer(1,true);q.clear();q.offer(2,true);
        assertEquals(Integer.valueOf(2),q.poll());
        assertNull(q.poll());assertEquals(1,q.highWater());
    }
    @Test public void simulationSpacingSurvivesDifferentDeliveryBatches(){
        AudioTimeline first=new AudioTimeline(44100,384,5292);
        AudioTimeline second=new AudioTimeline(44100,384,5292);
        double[] times={1,1,1.01,1.02,1.03};
        long[] expected={384,384,825,1266,1707};
        for(int i=0;i<times.length;i++){
            assertEquals(expected[i],first.schedule(times[i],0));
            assertEquals(expected[i],second.schedule(times[i],i==0?0:384));
        }
    }
    @Test public void lateOrDiscontinuousEventsNeverScheduleInThePast(){
        AudioTimeline t=new AudioTimeline(44100,384,5292);
        assertEquals(384,t.schedule(1,0));
        assertEquals(2000,t.schedule(1.01,2000));
        assertEquals(1L,t.lateEvents());
        assertEquals(2384,t.schedule(40,2000));
        assertEquals(1L,t.rebases());
        t.reset();
        assertEquals(384,t.schedule(0,0));
    }
}
