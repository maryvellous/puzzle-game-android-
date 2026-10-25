package com.maryvellous.lavablobs;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class BlobWorldTest {
    @Test public void blobsRiseInsteadOfFalling() {
        BlobWorld world = new BlobWorld(new Random(7));
        world.setHeight(820);
        world.nextTier = 0;
        assertTrue(world.launch(210));
        float originalY = world.blobs.get(0).y;
        for (int i = 0; i < 65; i++) world.tick(.016f);
        assertTrue("Positive screen Y is downward: a floating blob must rise",
                world.blobs.get(0).y < originalY - 80f);
    }

    @Test public void sameTierFusesAndAwardsPoints() {
        BlobWorld world = new BlobWorld(new Random(8));
        Blob a = new Blob(183, 430, 0, new Random(2));
        Blob b = new Blob(195, 430, 0, new Random(3));
        a.age = b.age = 1f;
        world.blobs.add(a);
        world.blobs.add(b);
        world.tick(.016f);
        assertEquals(1, world.blobs.size());
        assertEquals(1, world.blobs.get(0).tier);
        assertTrue(world.score > 0);
        assertEquals(1, world.consumeMerges().size());
    }

    @Test public void differentTiersDoNotFuse() {
        BlobWorld world = new BlobWorld(new Random(8));
        Blob a = new Blob(183, 430, 0, new Random(2));
        Blob b = new Blob(195, 430, 1, new Random(3));
        a.age = b.age = 1f;
        world.blobs.add(a);
        world.blobs.add(b);
        world.tick(.016f);
        assertEquals(2, world.blobs.size());
        assertEquals(0, world.score);
    }

    @Test public void launchedBlobRespectsVesselWalls() {
        BlobWorld world = new BlobWorld(new Random(8));
        world.nextTier = 3;
        assertTrue(world.launch(-1000));
        for (int i = 0; i < 300; i++) world.tick(.016f);
        for (Blob b : world.blobs) {
            assertTrue(b.x >= BlobWorld.LEFT + b.physicalRadius() - .01f);
            assertTrue(b.x <= BlobWorld.RIGHT - b.physicalRadius() + .01f);
            assertTrue(b.y >= BlobWorld.TOP + b.physicalRadius() - .01f);
        }
    }

    @Test public void resetClearsProgress() {
        BlobWorld world = new BlobWorld(new Random(8));
        world.nextTier = 2;
        world.launch(210);
        world.score = 500;
        world.reset();
        assertTrue(world.blobs.isEmpty());
        assertEquals(0, world.score);
        assertFalse(world.gameOver);
    }
}
