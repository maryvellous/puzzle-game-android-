package com.maryvellous.lavablobs;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Random;

public class SimulationClockTest {
    @Test public void boundsCatchupAndDropsStaleTimeAfterReset(){
        BlobWorld world=new BlobWorld(new Random(41));
        SimulationClock c=new SimulationClock();
        assertEquals(0f,c.beginFrame(1_000_000_000L),0f);
        assertEquals(0,c.advance(world));
        assertEquals(.10f,c.beginFrame(2_000_000_000L),0f);
        assertEquals(4,c.advance(world));
        assertEquals(4*SimulationClock.STEP,world.time,1e-6f);
        c.beginFrame(2_008_000_000L);assertEquals(0,c.advance(world));
        c.reset();
        c.beginFrame(3_000_000_000L);assertEquals(0,c.advance(world));
        c.beginFrame(3_010_000_000L);assertEquals(0,c.advance(world));
        c.beginFrame(3_020_000_000L);assertEquals(1,c.advance(world));
        assertEquals(5*SimulationClock.STEP,world.time,1e-6f);
    }
}
