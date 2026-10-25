package com.maryvellous.lavablobs;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

/** Regression tests against the old spiky, rigid radial-contact model. */
public class BlobWorldTest {
    @Test public void blobsRiseAgainstTheCeiling() {
        BlobWorld w=new BlobWorld(new Random(7));
        w.setHeight(820);
        w.nextTier=0;
        assertTrue(w.launch(210));
        float initial=w.blobs.get(0).y;
        for(int i=0;i<95;i++)w.tick(.016f);
        assertTrue(w.blobs.get(0).y<initial-90f);
    }

    @Test public void membranesHavePositiveAreaAndRealPerimeterParticles() {
        Blob b=new Blob(210,410,6,new Random(4));
        float initial=b.area();
        assertTrue(initial>0f);
        b.py[Blob.NODES*3/4]+=30f;
        for(int i=0;i<35;i++)b.solveMembrane();
        assertTrue(b.area()>initial*.75f);
        assertTrue(b.area()<initial*1.3f);
        assertTrue(b.px[0]>b.x);
    }

    @Test public void differentTierContactDeformsRealVerticesWithoutMerging() {
        BlobWorld w=new BlobWorld(new Random(8));
        Blob a=new Blob(166,410,4,new Random(2));
        Blob b=new Blob(247,410,5,new Random(3));
        w.blobs.add(a);w.blobs.add(b);
        float beforeA=a.px[0],beforeB=b.px[Blob.NODES/2];
        for(int i=0;i<10;i++)w.tick(.016f);
        assertEquals(2,w.blobs.size());
        assertTrue("Particle contacts must reshape the left membrane",
                Math.abs(a.px[0]-beforeA)>1f);
        assertTrue("Particle contacts must reshape the right membrane",
                Math.abs(b.px[Blob.NODES/2]-beforeB)>1f);
        assertTrue(a.area()>0f&&b.area()>0f);
    }

    @Test public void differentTiersDontMergeAndMatchingTiersDo() {
        BlobWorld w=new BlobWorld(new Random(9));
        w.blobs.add(new Blob(170,400,0,new Random(3)));
        w.blobs.add(new Blob(197,400,1,new Random(3)));
        w.tick(.016f);
        assertEquals(2,w.blobs.size());
        BlobWorld other=new BlobWorld(new Random(10));
        other.blobs.add(new Blob(170,400,0,new Random(3)));
        other.blobs.add(new Blob(204,400,0,new Random(3)));
        other.tick(.016f);
        assertEquals(1,other.blobs.size());
        assertEquals(1,other.blobs.get(0).tier);
        assertTrue(other.score>0);
        assertEquals(1,other.consumeMerges().size());
    }

    @Test public void matchDoesNotMergeAcrossVisibleGap() {
        BlobWorld w=new BlobWorld(new Random(11));
        w.blobs.add(new Blob(170,400,0,new Random(1)));
        w.blobs.add(new Blob(216,400,0,new Random(2)));
        w.tick(.016f);
        assertEquals(2,w.blobs.size());
    }

    @Test public void everyPhysicalVertexRemainsInsideGlass() {
        BlobWorld w=new BlobWorld(new Random(12));
        w.setHeight(820);
        Blob a=new Blob(60,BlobWorld.TOP+32,5,new Random(2));
        Blob b=new Blob(365,600,4,new Random(3));
        w.blobs.add(a);w.blobs.add(b);
        for(int t=0;t<70;t++)w.tick(.016f);
        for(Blob blob:w.blobs)for(int i=0;i<Blob.NODES;i++) {
            assertTrue(blob.px[i]>=BlobWorld.LEFT-.01f);
            assertTrue(blob.px[i]<=BlobWorld.RIGHT+.01f);
            assertTrue(blob.py[i]>=BlobWorld.TOP-.01f);
            assertTrue(blob.py[i]<=w.bottom()+.01f);
        }
    }

    @Test public void viscousAdhesionIsTemporaryAndReleases() {
        BlobWorld w=new BlobWorld(new Random(20));
        Blob a=new Blob(168,400,3,new Random(3));
        Blob b=new Blob(245,400,4,new Random(4));
        w.blobs.add(a);w.blobs.add(b);
        for(int t=0;t<5;t++)w.tick(.016f);
        assertTrue("Contact must generate a short-lived sticky patch",
                !w.adhesions.isEmpty());
        b.translate(100,0);
        for(int t=0;t<4;t++)w.tick(.016f);
        assertTrue("Sticky links must break instead of trapping blobs forever",
                w.adhesions.isEmpty());
    }

    @Test public void shakeMovesNodesAndSurvivesContact() {
        BlobWorld w=new BlobWorld(new Random(6));
        Blob b=new Blob(140,430,2,new Random(2));
        w.blobs.add(b);
        float oldX=b.x;
        w.shake(9f,0f,9f);
        for(int i=0;i<7;i++)w.tick(.016f);
        assertTrue(b.x<oldX-4f);
        assertTrue(b.area()>0f);
    }

    @Test public void manyContactsDoNotFormNeedleSpikes() {
        BlobWorld w=new BlobWorld(new Random(3));
        for(int i=0;i<8;i++)w.blobs.add(
                new Blob(72+(i%4)*73,245+(i/4)*80,i,new Random(i)));
        for(int i=0;i<100;i++) {
            if(i%15==0)w.shake(4f,-2f,2f);
            w.tick(.016f);
        }
        for(Blob b:w.blobs) {
            assertTrue("Area must remain positive and finite",b.area()>20f);
            for(int i=0;i<Blob.NODES;i++) {
                int next=(i+1)%Blob.NODES;
                float d=(float)Math.hypot(b.px[i]-b.px[next],b.py[i]-b.py[next]);
                assertTrue("No runaway spikes or torn membrane", d<b.radius()*.90f+5f);
            }
        }
    }

    @Test public void restartClearsProgressAndStickyLinks() {
        BlobWorld w=new BlobWorld(new Random(8));
        w.nextTier=1;
        assertTrue(w.launch(210));
        w.score=100;
        w.reset();
        assertTrue(w.blobs.isEmpty());
        assertTrue(w.adhesions.isEmpty());
        assertEquals(0,w.score);
        assertFalse(w.gameOver);
    }
}
