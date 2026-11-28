package com.maryvellous.lavablobs;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/** Physics-only V2 probe tests. No SoundPool, audio rendering or APK changes. */
public class ContactProbeTest {
    private static Blob blob(float x,float y,int tier) {
        return new Blob(x,y,tier,new Random(3));
    }

    private static void speed(Blob b,float vx,float vy) {
        for(int i=0;i<Blob.NODES;i++) {
            b.vx[i]=vx; b.vy[i]=vy;
        }
    }

    @Test public void idsAreUniqueAcrossCreationFusionAndReset() {
        BlobWorld w=new BlobWorld(new Random(41));
        w.nextTier=0;
        assertTrue(w.launch(210));
        int firstId=w.blobs.get(0).id;
        assertTrue(firstId>0);
        w.blobs.clear(); // Simulate a removed blob; its ID must NOT be reused.
        assertTrue(w.launch(210));
        assertTrue(w.blobs.get(0).id>firstId);

        w.reset();
        Blob a=blob(170,400,0), b=blob(204,400,0);
        w.blobs.add(a);
        w.blobs.add(b);
        w.tick(.016f);
        assertTrue(a.id>0);
        assertTrue(b.id>0);
        assertNotEquals(a.id,b.id);
        assertEquals(1,w.blobs.size());
        Blob born=w.blobs.get(0);
        assertTrue(born.id>a.id);
        assertTrue(born.id>b.id);
        List<BlobWorld.Merge> merges=w.consumeMerges();
        assertEquals(1,merges.size());
        BlobWorld.Merge m=merges.get(0);
        assertEquals(a.id,m.parentAId);
        assertEquals(b.id,m.parentBId);
        assertEquals(born.id,m.resultId);
        assertTrue(m.simulationTime>0f);
    }

    @Test public void realWallProjectionIsPassiveAndDeduplicated() {
        Vessel vessel=new Vessel(-1,850f);
        Blob observed=blob(35f,400f,1),baseline=blob(35f,400f,1);
        observed.id=1;baseline.id=1;
        speed(observed,-250f,0);speed(baseline,-250f,0);
        final int[] events={0};
        ContactProbe probe=new ContactProbe(new ContactProbe.Listener(){
            @Override public void onContactStep(float time,float dt,List<ContactProbe.Contact> c){}
            @Override public void onWallImpact(int id,int side,float approach,float radius){
                assertEquals(1,id);assertTrue(approach>70f);events[0]++;
            }
        });
        probe.beginStep();
        for(int pass=0;pass<5;pass++){
            vessel.confine(observed,probe);
            vessel.confine(baseline);
        }
        probe.finishStep(.008f,.008f);
        assertEquals("One real impact, not one per vertex or solver pass",1,events[0]);
        for(int i=0;i<Blob.NODES;i++){
            assertEquals(baseline.px[i],observed.px[i],0f);
            assertEquals(baseline.py[i],observed.py[i],0f);
        }
        speed(observed,0,0);
        for(int step=1;step<=120;step++){
            probe.beginStep();
            // Simulate stationary pressure repeatedly projected by the solver.
            observed.px[0]=28f;observed.py[0]=400f;
            vessel.confine(observed,probe);
            probe.finishStep(.008f+step*.008f,.008f);
        }
        assertEquals("No further sound during stationary pressure",1,events[0]);
    }

    @Test public void pairKeyIsSymmetricAndDoesNotAliasDistinctPairs() {
        assertEquals(BlobWorld.pairKey(1,3),BlobWorld.pairKey(3,1));
        assertNotEquals(BlobWorld.pairKey(1,3),BlobWorld.pairKey(1,4));
        assertNotEquals(BlobWorld.pairKey(1,3),BlobWorld.pairKey(2,3));
    }

    @Test public void probeReportsRealMembraneContactsOncePerPairPerSubstep() {
        BlobWorld w=new BlobWorld(new Random(91));
        Blob upper=blob(210,350,2),lower=blob(210,422,1);
        speed(upper,0,0);
        speed(lower,0,-310);
        w.blobs.add(upper);
        w.blobs.add(lower);
        final List<ContactProbe.Contact> seen=new ArrayList<>();
        final int[] emptySteps={0},observedSteps={0};
        final float[] lastTime={-1f};
        w.setContactProbe((time,dt,contacts)->{
            assertTrue("Time must advance between physics substeps",time>lastTime[0]);
            lastTime[0]=time;
            assertTrue(dt>0f);
            observedSteps[0]++;
            if(contacts.isEmpty())emptySteps[0]++;
            Set<Long> keys=new HashSet<>();
            for(ContactProbe.Contact c:contacts) {
                assertEquals("Probe time matches solver time",time,c.time,1e-5f);
                assertTrue(c.blobA>0&&c.blobB>c.blobA);
                assertTrue("Duplicate pair within a solver step",
                        keys.add(BlobWorld.pairKey(c.blobA,c.blobB)));
                assertTrue(c.projectionSamples>0);
                assertTrue(Float.isFinite(c.approachSpeed));
                assertTrue(Float.isFinite(c.slideSpeed));
                assertTrue(Float.isFinite(c.compression));
                assertTrue(Float.isFinite(c.x)&&Float.isFinite(c.y));
                assertEquals(1f,(float)Math.hypot(c.nx,c.ny),.002f);
                assertTrue(c.compression>=0f&&c.compression<=1f);
                assertTrue(c.approachSpeed>=0f&&c.slideSpeed>=0f);
                seen.add(c);
            }
        });
        for(int frame=0;frame<24;frame++)w.tick(.016f);
        assertEquals(48,observedSteps[0]); // 2 physics substeps per tick.
        assertTrue("Probe should include contact-free steps",emptySteps[0]>0);
        assertFalse("Fast blob must contact a real membrane",seen.isEmpty());
        float maxApproach=0;
        for(ContactProbe.Contact c:seen) {
            assertEquals(Math.min(upper.id,lower.id),c.blobA);
            assertEquals(Math.max(upper.id,lower.id),c.blobB);
            maxApproach=Math.max(maxApproach,c.approachSpeed);
        }
        assertTrue("Closing speed is measured before projection",maxApproach>20f);
        int count=observedSteps[0];
        w.setContactProbe(null);
        w.tick(.016f);
        assertEquals("Unsubscribed probe must not run",count,observedSteps[0]);
    }

    @Test public void tangentialSlipAndDeformationAreObservableWithoutChangingSolver() {
        BlobWorld observed=new BlobWorld(new Random(77));
        BlobWorld baseline=new BlobWorld(new Random(77));
        Blob oa=blob(181,386,2),ob=blob(239,386,1);
        Blob ba=blob(181,386,2),bb=blob(239,386,1);
        for(Blob b:new Blob[]{oa,ba})speed(b,0,100);
        for(Blob b:new Blob[]{ob,bb})speed(b,0,-100);
        observed.blobs.add(oa);observed.blobs.add(ob);
        baseline.blobs.add(ba);baseline.blobs.add(bb);
        final float[] maxSlide={0f},maxCompression={0f};
        final int[] contacts={0};
        observed.setContactProbe((time,dt,list)->{
            for(ContactProbe.Contact c:list){
                contacts[0]++;
                maxSlide[0]=Math.max(maxSlide[0],c.slideSpeed);
                maxCompression[0]=Math.max(maxCompression[0],c.compression);
            }
        });
        for(int frame=0;frame<15;frame++){
            observed.tick(.016f);
            baseline.tick(.016f);
        }
        assertTrue("Sliding membranes must yield contact",contacts[0]>0);
        assertTrue("Tangential slip is measured on actual contact",maxSlide[0]>5f);
        assertTrue("Contact geometry must provide a deformation proxy",
                maxCompression[0]>0f);
        assertEquals(baseline.blobs.size(),observed.blobs.size());
        assertEquals(baseline.score,observed.score);
        for(int j=0;j<observed.blobs.size();j++) {
            Blob with=observed.blobs.get(j),without=baseline.blobs.get(j);
            assertEquals(without.id,with.id);
            for(int i=0;i<Blob.NODES;i++) {
                assertEquals("Probe changed membrane X",without.px[i],with.px[i],0f);
                assertEquals("Probe changed membrane Y",without.py[i],with.py[i],0f);
                assertEquals("Probe changed velocity X",without.vx[i],with.vx[i],0f);
                assertEquals("Probe changed velocity Y",without.vy[i],with.vy[i],0f);
            }
        }
    }

    @Test public void noContactDeliversEmptyStepsAndFusionDoesNotFakeImpact() {
        BlobWorld distant=new BlobWorld(new Random(88));
        distant.blobs.add(blob(210,300,2));
        distant.blobs.add(blob(210,520,1));
        final int[] empty={0},nonempty={0};
        distant.setContactProbe((t,dt,contacts)->{
            if(contacts.isEmpty())empty[0]++;
            else nonempty[0]+=contacts.size();
        });
        for(int i=0;i<3;i++)distant.tick(.016f);
        assertEquals(6,empty[0]);
        assertEquals(0,nonempty[0]);

        BlobWorld merged=new BlobWorld(new Random(89));
        merged.blobs.add(blob(170,400,0));
        merged.blobs.add(blob(204,400,0));
        final int[] touches={0};
        merged.setContactProbe((t,dt,contacts)->touches[0]+=contacts.size());
        merged.tick(.016f);
        assertEquals(1,merged.consumeMerges().size());
        assertEquals("Fusion shouldn't emit an unrelated pair impact",0,touches[0]);
    }
    @Test public void sharedBodyMetricsAreMeasuredOncePerStepAndNeverStale(){
        Blob a=blob(100,400,1),b=blob(150,400,1),c=blob(200,400,1);
        a.id=1;b.id=2;c.id=3;
        List<ContactProbe.Contact> seen=new ArrayList<>();
        ContactProbe p=new ContactProbe((t,dt,contacts)->seen.addAll(contacts));
        for(int step=1;step<=2;step++){
            speed(a,100f*step,0);speed(b,0,0);speed(c,0,0);
            p.beginStep();
            p.observe(a,0,b,0,.5f,1f,0f,1f,125f,400f);
            p.observe(a,0,c,0,.5f,1f,0f,1f,150f,400f);
            p.observe(b,0,c,0,.5f,1f,0f,1f,175f,400f);
            p.finishStep(step*.008f,.008f);
            assertEquals(3L*step,p.measuredBodies());
            assertEquals(6L*step,p.contactMetricRequests());
            assertEquals(100f*step,seen.get((step-1)*3).bulkMotion,0f);
            assertEquals(100f*step,seen.get((step-1)*3+1).bulkMotion,0f);
            assertEquals(0f,seen.get((step-1)*3+2).bulkMotion,0f);
        }
        p.reset();assertEquals(0,p.measuredBodies());assertEquals(0,p.contactMetricRequests());
    }
}
