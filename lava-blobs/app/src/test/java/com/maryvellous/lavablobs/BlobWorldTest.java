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

    @Test public void blobsMergeOnOrdinaryContactNotDeepOverlap() {
        BlobWorld world = new BlobWorld(new Random(8));
        Blob a = new Blob(175, 430, 0, new Random(2));
        Blob b = new Blob(210, 430, 0, new Random(3));
        // At full visible size, 35 units apart means that two radius-18
        // contours really touch. At spawn scale 0.7 they correctly do not.
        a.scale = b.scale = 1f;
        a.age = b.age = 1f;
        world.blobs.add(a);
        world.blobs.add(b);
        world.tick(.016f);
        assertEquals("A normal surface touch should cause a merge", 1, world.blobs.size());
        assertEquals(1, world.blobs.get(0).tier);
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
            assertTrue(b.x >= BlobWorld.LEFT + b.outlineRadius((float)Math.PI, world.time) - .01f);
            assertTrue(b.x <= BlobWorld.RIGHT - b.outlineRadius(0f, world.time) + .01f);
            assertTrue(b.y >= BlobWorld.TOP + b.outlineRadius((float)-Math.PI/2, world.time) - .01f);
        }
    }

    @Test public void aFreshBlobHasNoFullSizeInvisibleCollider() {
        Blob b = new Blob(200, 450, 3, new Random(2));
        b.scale = .70f;
        float size = b.outlineRadius(0f, 0f);
        assertTrue("A fresh blob's collision shape must match its smaller rendered outline",
                size < b.radius() * .78f);
    }

    @Test public void compressedOutlinesDoNotMergeBeforeTheyTouch() {
        BlobWorld world = new BlobWorld(new Random(9));
        Blob a = new Blob(170, 430, 3, new Random(2));
        Blob b = new Blob(240, 430, 3, new Random(3));
        a.scale = 1f;
        b.scale = 1f;
        a.age = b.age = 1f;
        // Both opposing surfaces visibly flatten toward their centres.
        for (int index : new int[] {0, 1, 19}) a.deformation[index] = -9f;
        for (int index : new int[] {Blob.NODES / 2 - 1, Blob.NODES / 2, Blob.NODES / 2 + 1}) b.deformation[index] = -9f;
        world.blobs.add(a);
        world.blobs.add(b);
        world.tick(.016f);
        assertEquals("A gap between squished visible outlines must remain empty",
                2, world.blobs.size());
    }

    @Test public void enlargedVisibleSurfaceCausesContact() {
        BlobWorld world = new BlobWorld(new Random(9));
        Blob a = new Blob(170, 430, 3, new Random(2));
        Blob b = new Blob(240, 430, 3, new Random(3));
        a.scale = b.scale = 1f;
        a.age = b.age = 1f;
        world.blobs.add(a);
        world.blobs.add(b);
        world.tick(.016f);
        assertEquals("Visible surfaces meet at distance 70; they should merge",
                1, world.blobs.size());
    }

    @Test public void shakeStirsAndSquishesBlobsWithoutChangingTheirTier() {
        BlobWorld world = new BlobWorld(new Random(6));
        Blob a = new Blob(150, 370, 0, new Random(2));
        Blob b = new Blob(270, 390, 4, new Random(3));
        a.scale = b.scale = 1f;
        world.blobs.add(a);
        world.blobs.add(b);
        world.shake(9f, 0f, 0f);
        assertTrue("Acceleration must cause actual movement, not just an animation",
                a.vx < -100f && b.vx < -50f);
        assertEquals(0, a.tier);
        assertEquals(4, b.tier);
        for (int i = 0; i < 10; i++) world.tick(.016f);
        assertTrue("The shake must excite elastic perimeter nodes",
                hasAnyDeformation(a));
    }

    @Test public void depthOnlyShakeMovesBlobsToo() {
        BlobWorld world = new BlobWorld(new Random(6));
        Blob b = new Blob(110, 400, 2, new Random(4));
        b.vx = b.vy = 0;
        world.blobs.add(b);
        world.shake(0f, 0f, 8f);
        assertTrue(Math.abs(b.vx) + Math.abs(b.vy) > 3f);
    }

    @Test public void shakeDoesNotChangeFinishedGame() {
        BlobWorld world = new BlobWorld(new Random(6));
        Blob b = new Blob(110, 400, 2, new Random(4));
        world.blobs.add(b);
        world.gameOver = true;
        world.shake(12, 12, 12);
        assertEquals(0f, b.vx, .001f);
        assertEquals(0f, b.vy, .001f);
    }

    private static boolean hasAnyDeformation(Blob b) {
        for (float v : b.deformation) {
            if (Math.abs(v) > .25f) return true;
        }
        return false;
    }

    @Test public void contactMostlyFlattensFacesInsteadOfShovingCentres() {
        BlobWorld world = new BlobWorld(new Random(51));
        Blob a = new Blob(150, 405, 4, new Random(10)); // radius 45
        Blob b = new Blob(229, 405, 5, new Random(11)); // radius 56
        a.scale = b.scale = 1f;
        a.age = b.age = 1f;
        world.blobs.add(a);
        world.blobs.add(b);
        float originalA = a.outlineRadius(0f, world.time);
        float originalB = b.outlineRadius((float)Math.PI, world.time);
        float originalSide = a.outlineRadius((float)Math.PI/2, world.time);
        world.tick(.016f);
        assertEquals(2, world.blobs.size());
        assertTrue("The first contact face should visibly flatten",
                a.outlineRadius(0f, world.time) < originalA - 5f);
        assertTrue("The second contact face should visibly flatten",
                b.outlineRadius((float)Math.PI, world.time) < originalB - 5f);
        assertTrue("Jelly should bulge sideways when compressed",
                a.outlineRadius((float)Math.PI/2, world.time) > originalSide + 1f);
        assertTrue("Centre separation should be much smaller than total overlap",
                b.x - a.x < 90f);
    }

    @Test public void topWallPancakesTheBlobRatherThanPushingItsCentre() {
        BlobWorld world = new BlobWorld(new Random(52));
        Blob b = new Blob(210, BlobWorld.TOP + 58, 7, new Random(11));
        b.scale = 1f;
        b.age = 1f;
        world.blobs.add(b);
        float oldY = b.y;
        world.tick(.016f);
        assertTrue("The top-facing surface must compress strongly",
                b.deformation[Blob.NODES * 3 / 4] < -12f);
        assertTrue("The bubble should keep most of its centre position",
                b.y - oldY < 15f);
        assertTrue("The rendered silhouette may not pass through the glass",
                b.y - b.outlineRadius((float)-Math.PI/2, world.time) >= BlobWorld.TOP - .02f);
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
