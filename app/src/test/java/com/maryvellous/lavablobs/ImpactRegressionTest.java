package com.maryvellous.lavablobs;

import org.junit.Test;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/** End-to-end physics regression for the silent blob-to-blob collision bug. */
public class ImpactRegressionTest {
    @Test public void fastBlobFromBelowHitsStationaryDifferentTierAndEmitsImpact() {
        BlobWorld world=new BlobWorld(new Random(41));
        Blob upper=new Blob(210,350,2,new Random(2));
        Blob lower=new Blob(210,422,1,new Random(3));
        for(int i=0;i<Blob.NODES;i++) {
            lower.vy[i]=-310f;
            upper.vy[i]=0f;
        }
        world.blobs.add(upper);
        world.blobs.add(lower);
        BlobWorld.Impact first=null;
        for(int frame=0;frame<24;frame++) {
            world.tick(.016f);
            List<BlobWorld.Impact> events=world.consumeImpacts();
            if(!events.isEmpty()) {
                first=events.get(0);
                break;
            }
        }
        assertNotNull("Fast upward hit on another tier must enqueue an audible collision",first);
        assertTrue("Approach speed should exceed the sound threshold",
                first.speed>=42f);
        assertEquals("Different-tier contact must not fuse the blobs",2,world.blobs.size());
    }

    @Test public void strongestImpactIsSelectedFromSimultaneousCollisions() {
        List<BlobWorld.Impact> impacts=java.util.Arrays.asList(
                new BlobWorld.Impact(1,48f),
                new BlobWorld.Impact(4,152f),
                new BlobWorld.Impact(3,75f));
        BlobWorld.Impact strongest=BlobWorld.strongestImpact(impacts);
        assertNotNull(strongest);
        assertEquals(152f,strongest.speed,.00001f);
        assertEquals(4,strongest.tier);
        assertNull(BlobWorld.strongestImpact(java.util.Collections.emptyList()));
    }

    @Test public void sameTierContactFusesInsteadOfEnqueueingImpact() {
        BlobWorld world=new BlobWorld(new Random(42));
        world.blobs.add(new Blob(210,350,0,new Random(1)));
        world.blobs.add(new Blob(210,389,0,new Random(2)));
        world.tick(.016f);
        assertEquals(1,world.blobs.size());
        assertEquals(1,world.consumeMerges().size());
        assertTrue("Fusions use their own audio event",world.consumeImpacts().isEmpty());
    }

    @Test public void distantBlobsDoNotEmitImpacts() {
        BlobWorld world=new BlobWorld(new Random(43));
        Blob upper=new Blob(210,330,2,new Random(4));
        Blob lower=new Blob(210,540,1,new Random(5));
        for(int i=0;i<Blob.NODES;i++)lower.vy[i]=-310f;
        world.blobs.add(upper);
        world.blobs.add(lower);
        for(int frame=0;frame<4;frame++) {
            world.tick(.016f);
            assertTrue("Approach velocity alone must not sound without a real contact",
                    world.consumeImpacts().isEmpty());
        }
    }
}
