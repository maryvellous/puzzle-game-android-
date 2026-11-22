package com.maryvellous.lavablobs;

import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Time-domain interpretation of ContactProbe snapshots. Pure JVM, no Android
 * audio APIs and no effect on physics. This is a future V2 building block:
 * one BEGIN, zero or more UPDATEs, then one END per real physical contact.
 *
 * Short gaps are tolerated to avoid fake re-impacts when a soft membrane
 * temporarily oscillates outside the solver's collision skin.
 */
final class ContactTracker implements ContactProbe.Listener {
    // 40 ms, a preliminary timing parameter. Final value needs listening tests.
    static final float RELEASE_GRACE_SECONDS=.040f;

    enum Kind { BEGIN, UPDATE, END, CANCELLED }

    static final class Transition {
        final Kind kind;
        final int blobA,blobB;
        final float time,contactAge;
        final ContactProbe.Contact contact; // Last real observation for END.
        Transition(Kind kind,State state,float time) {
            this.kind=kind;
            blobA=state.last.blobA;
            blobB=state.last.blobB;
            this.time=time;
            contactAge=state.age;
            contact=state.last;
        }
    }

    interface Listener {
        void onTransition(Transition event);
    }

    private static final class State {
        ContactProbe.Contact last;
        float age,missing;
        boolean seen;
        State(ContactProbe.Contact first) {
            last=first;
            age=first.dt;
        }
    }

    private final Listener listener;
    private final Map<Long,State> active=new LinkedHashMap<>();
    private float previousTime=-1f;

    ContactTracker(Listener listener) {
        if(listener==null)throw new IllegalArgumentException("Missing tracker listener");
        this.listener=listener;
    }

    @Override public void onContactStep(float time,float dt,List<ContactProbe.Contact> contacts) {
        if(!Float.isFinite(time)||!Float.isFinite(dt)||dt<=0f ||
                (previousTime>=0f&&time<=previousTime))
            throw new IllegalArgumentException("Non-monotonic acoustic step");
        previousTime=time;
        for(State state:active.values())state.seen=false;

        for(ContactProbe.Contact c:contacts) {
            long key=BlobWorld.pairKey(c.blobA,c.blobB);
            State state=active.get(key);
            if(state==null) {
                state=new State(c);
                active.put(key,state);
                state.seen=true;
                listener.onTransition(new Transition(Kind.BEGIN,state,time));
            } else {
                state.last=c;
                state.age+=dt;
                state.missing=0f;
                state.seen=true;
                listener.onTransition(new Transition(Kind.UPDATE,state,time));
            }
        }

        Iterator<State> iter=active.values().iterator();
        while(iter.hasNext()) {
            State state=iter.next();
            if(state.seen)continue;
            state.age+=dt;
            state.missing+=dt;
            if(state.missing>=RELEASE_GRACE_SECONDS) {
                iter.remove();
                listener.onTransition(new Transition(Kind.END,state,time));
            }
        }
    }

    @Override public void onMerge(BlobWorld.Merge merge) {
        cancelBlob(merge.parentAId,merge.simulationTime);
        cancelBlob(merge.parentBId,merge.simulationTime);
    }

    @Override public void onPop(int blobId,float time) {
        cancelBlob(blobId,time);
    }

    @Override public void onReset() {reset();}

    /** A removed or merged blob must not fake a physical separation sound. */
    void cancelBlob(int blobId,float time) {
        Iterator<State> iter=active.values().iterator();
        while(iter.hasNext()) {
            State state=iter.next();
            if(state.last.blobA!=blobId&&state.last.blobB!=blobId)continue;
            iter.remove();
            listener.onTransition(new Transition(Kind.CANCELLED,state,time));
        }
    }

    void reset() {
        active.clear();
        previousTime=-1f;
    }

    int activePairs(){return active.size();}
}
