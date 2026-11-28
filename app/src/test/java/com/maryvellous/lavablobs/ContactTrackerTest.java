package com.maryvellous.lavablobs;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/** Continuous contact memory and release rules; no actual sound playback. */
public class ContactTrackerTest {
    private static ContactProbe.Contact realMembraneSample() {
        BlobWorld world=new BlobWorld(new Random(901));
        Blob upper=new Blob(210,350,2,new Random(1));
        Blob lower=new Blob(210,422,1,new Random(2));
        for(int i=0;i<Blob.NODES;i++)lower.vy[i]=-310f;
        world.blobs.add(upper);world.blobs.add(lower);
        final ContactProbe.Contact[] sample={null};
        world.setContactProbe((time,dt,contacts)->{
            if(sample[0]==null&&!contacts.isEmpty())sample[0]=contacts.get(0);
        });
        for(int i=0;i<24&&sample[0]==null;i++)world.tick(.016f);
        assertNotNull("Test must use a real membrane contact",sample[0]);
        return sample[0];
    }

    @Test public void trackerBeginsOnceUpdatesAndReleasesAfterHysteresis() {
        ContactProbe.Contact c=realMembraneSample();
        List<ContactTracker.Transition> changes=new ArrayList<>();
        ContactTracker tracker=new ContactTracker(changes::add);
        tracker.onContactStep(1f,.008f,Collections.singletonList(c));
        tracker.onContactStep(1.008f,.008f,Collections.singletonList(c));
        assertEquals(2,changes.size());
        assertEquals(ContactTracker.Kind.BEGIN,changes.get(0).kind);
        assertEquals(ContactTracker.Kind.UPDATE,changes.get(1).kind);
        assertEquals(1,tracker.activePairs());

        // 2 missed substeps must not cause false release or a second impact.
        tracker.onContactStep(1.016f,.008f,Collections.emptyList());
        tracker.onContactStep(1.024f,.008f,Collections.emptyList());
        assertEquals(2,changes.size());
        tracker.onContactStep(1.032f,.008f,Collections.singletonList(c));
        assertEquals(ContactTracker.Kind.UPDATE,changes.get(2).kind);
        assertEquals(1,tracker.activePairs());

        for(int i=0;i<6;i++)
            tracker.onContactStep(1.040f+i*.008f,.008f,Collections.emptyList());
        assertEquals(0,tracker.activePairs());
        assertEquals(ContactTracker.Kind.END,changes.get(3).kind);
        assertEquals(4,changes.size());
        assertEquals(c.blobA,changes.get(3).blobA);
        assertTrue(changes.get(3).contactAge>changes.get(0).contactAge);
        // A long-separated pair really may generate a new BEGIN on recontact.
        tracker.onContactStep(1.096f,.008f,Collections.singletonList(c));
        assertEquals(ContactTracker.Kind.BEGIN,changes.get(4).kind);
    }

    @Test public void mergeAndPopCancelWithoutFakeReleaseSound() {
        ContactProbe.Contact c=realMembraneSample();
        List<ContactTracker.Transition> changes=new ArrayList<>();
        ContactTracker tracker=new ContactTracker(changes::add);
        tracker.onContactStep(1f,.008f,Collections.singletonList(c));
        BlobWorld.Merge merge=new BlobWorld.Merge(c.x,c.y,40f,3,10,
                c.blobA,100,101,1.004f);
        tracker.onMerge(merge);
        assertEquals(0,tracker.activePairs());
        assertEquals(ContactTracker.Kind.CANCELLED,changes.get(1).kind);
        for(int i=1;i<=6;i++)
            tracker.onContactStep(1.004f+i*.008f,.008f,Collections.emptyList());
        assertEquals(2,changes.size());

        tracker.onContactStep(1.060f,.008f,Collections.singletonList(c));
        tracker.onPop(c.blobB,1.065f);
        assertEquals(ContactTracker.Kind.CANCELLED,changes.get(3).kind);
        assertEquals(0,tracker.activePairs());
    }

    @Test public void resetClearsPendingContactsAndRestartsTimeline() {
        ContactProbe.Contact c=realMembraneSample();
        List<ContactTracker.Transition> changes=new ArrayList<>();
        ContactTracker tracker=new ContactTracker(changes::add);
        tracker.onContactStep(9f,.008f,Collections.singletonList(c));
        tracker.onReset();
        assertEquals(0,tracker.activePairs());
        tracker.onContactStep(.008f,.008f,Collections.singletonList(c));
        assertEquals(2,changes.size());
        assertEquals(ContactTracker.Kind.BEGIN,changes.get(1).kind);
    }

    @Test public void connectedProbeForwardsPhysicsAndResetToTracker() {
        BlobWorld world=new BlobWorld(new Random(51));
        Blob upper=new Blob(210,350,2,new Random(1));
        Blob lower=new Blob(210,422,1,new Random(2));
        for(int i=0;i<Blob.NODES;i++)lower.vy[i]=-310f;
        world.blobs.add(upper);world.blobs.add(lower);
        List<ContactTracker.Transition> events=new ArrayList<>();
        ContactTracker tracker=new ContactTracker(events::add);
        world.setContactProbe(tracker);
        for(int i=0;i<24;i++)world.tick(.016f);
        assertTrue("Actual world probe should feed contact beginnings",
                events.stream().anyMatch(e->e.kind==ContactTracker.Kind.BEGIN));
        world.reset();
        assertEquals("Reset discards previous world contact memory",0,tracker.activePairs());
        assertEquals("No ghost contact remains after reset",0,world.blobs.size());
    }
    @Test public void terminalOnlyPreservesReleaseAndCancellationPayloads(){
        ContactProbe.Contact c=realMembraneSample();
        List<ContactTracker.Transition> full=new ArrayList<>(),terminal=new ArrayList<>();
        ContactTracker a=new ContactTracker(full::add),b=new ContactTracker(terminal::add,true);
        for(ContactTracker tracker:new ContactTracker[]{a,b}){
            tracker.onContactStep(1f,.008f,Collections.singletonList(c));
            tracker.onContactStep(1.008f,.008f,Collections.singletonList(c));
            for(int i=0;i<6;i++)tracker.onContactStep(1.016f+i*.008f,.008f,Collections.emptyList());
            tracker.onContactStep(1.070f,.008f,Collections.singletonList(c));
            tracker.onPop(c.blobA,1.075f);
            assertEquals(0,tracker.activePairs());
        }
        full.removeIf(t->t.kind==ContactTracker.Kind.BEGIN||t.kind==ContactTracker.Kind.UPDATE);
        assertEquals(2,terminal.size());assertEquals(full.size(),terminal.size());
        for(int j=0;j<full.size();j++){
            ContactTracker.Transition x=full.get(j),y=terminal.get(j);
            assertEquals(x.kind,y.kind);assertEquals(x.blobA,y.blobA);assertEquals(x.blobB,y.blobB);
            assertEquals(x.time,y.time,0f);assertEquals(x.contactAge,y.contactAge,0f);
            assertSame(x.contact,y.contact);
        }
    }
}
