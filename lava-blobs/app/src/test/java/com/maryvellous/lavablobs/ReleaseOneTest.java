package com.maryvellous.lavablobs;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class ReleaseOneTest {
    @Test public void nineColoursCycleInCorrectUserOrder() {
        assertEquals(0xFF065143,Palette.forTier(0));
        assertEquals(0xFF4B748E,Palette.forTier(1));
        assertEquals(0xFFDB5461,Palette.forTier(8));
        assertEquals(0xFF065143,Palette.forTier(9));
        assertEquals(0xFF4B748E,Palette.forTier(10));
    }

    @Test public void allFiveDropSizesOccurWithPlannedWeights() {
        Random r=new Random(311);
        int[] c=new int[5];
        for(int i=0;i<100_000;i++)c[GameSpec.rollDrop(r)]++;
        int[] expected={45000,28000,16000,8000,3000};
        for(int i=0;i<5;i++)assertTrue("tier "+i,c[i]>expected[i]-900&&c[i]<expected[i]+900);
    }

    @Test public void scoringMatchesApprovedCurveAndHasNoNineTierCap() {
        int[] values={100,160,260,410,660,1050,1680,2680,4290,6870};
        for(int i=0;i<values.length;i++)assertEquals("fusion "+i,
                values[i],GameSpec.pointsForMerge(i));
        Blob giant=new Blob(210,410,13,new Random(3));
        assertTrue(giant.radius()>Blob.RADII[8]);
    }

    @Test public void levelsAndEndlessAreSeparated() {
        assertEquals(12,GameSpec.LEVELS.length);
        assertEquals(3,GameSpec.ENDLESS.length);
        for(GameSpec s:GameSpec.LEVELS) {
            assertFalse(s.endless);
            assertTrue(s.extraScore>s.minScore);
            assertTrue(s.goalTier>=5);
        }
        for(GameSpec s:GameSpec.ENDLESS)assertTrue(s.endless);
    }

    @Test public void shapedGlassesAreNotIdenticalAndCanReceiveDrops() {
        Vessel jar=new Vessel(0,850),hourglass=new Vessel(3,850);
        assertTrue(jar.contains(210,400));
        assertTrue(hourglass.contains(210,400));
        assertFalse(jar.contains(20,400));
        assertTrue(jar.xs.length>30);
        assertNotEquals(jar.xs[10],hourglass.xs[10],.1f);
        for(int kind=0;kind<=10;kind++) {
            Vessel v=new Vessel(kind,850);
            assertTrue("An inlet should admit at least a smallest blob (vessel "+kind+")",
                    v.fits(210,v.bottom-18-7,18f*.76f));
        }
    }

    @Test public void permanentBlockMustPersistAndEraseOnlyWorksInEndless() {
        BlobWorld endless=new BlobWorld(new Random(12),GameSpec.ENDLESS[0]);
        BlobWorld level=new BlobWorld(new Random(12),GameSpec.LEVELS[0]);
        assertTrue(endless.graceSeconds()>level.graceSeconds());
        endless.eraseCount=1;
        Blob b=new Blob(210,380,0,new Random(1));
        endless.blobs.add(b);
        assertTrue(endless.eraseAt(210,380));
        assertEquals(0,endless.eraseCount);
        assertEquals(1,endless.usedErases);
        level.eraseCount=1;
        level.blobs.add(new Blob(210,380,0,new Random(2)));
        assertFalse(level.eraseAt(210,380));
    }

    @Test public void idleBlobsAtBottomDoNotAutomaticallyEndGame() {
        BlobWorld world=new BlobWorld(new Random(19),GameSpec.ENDLESS[0]);
        Blob b=new Blob(210,world.bottom()-23,0,new Random(2));
        world.blobs.add(b);
        for(int i=0;i<220;i++)world.tick(.016f);
        assertFalse("The old Y-threshold used to falsely end runs",world.gameOver);
    }
}
