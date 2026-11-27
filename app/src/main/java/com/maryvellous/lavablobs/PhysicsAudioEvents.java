package com.maryvellous.lavablobs;

import com.maryvellous.lavablobs.audio.AudioEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import static com.maryvellous.lavablobs.audio.AudioEvent.Type.*;

/** Simulation-thread adapter. The exact same observation and transition mapping
 * is used by the Android bridge and by deterministic JVM trace fixtures. */
final class PhysicsAudioEvents implements ContactProbe.Listener {
    private final ContactTracker tracker;
    private final Consumer<AudioEvent> sink;
    private final IntSupplier generation;
    private final LongSupplier clock;
    private final Runnable reset;
    PhysicsAudioEvents(Consumer<AudioEvent> sink,IntSupplier generation,LongSupplier clock,Runnable reset){
        this.sink=sink;this.generation=generation;this.clock=clock;this.reset=reset;
        tracker=new ContactTracker(this::transition);
    }
    private void emit(AudioEvent.Type type,float time,int a,int b,float normal,float slip,
                      float compression,float radiusA,float radiusB,float dt,float bulkMotion){
        sink.accept(new AudioEvent(type,a,b,normal,slip,compression,radiusA,radiusB,dt,bulkMotion,
                time,generation.getAsInt(),clock.getAsLong()));
    }
    private void emit(AudioEvent.Type type,float time,int a,int b,float normal,float radius){
        emit(type,time,a,b,normal,0,0,radius,0,1f/120f,0);
    }
    @Override public void onContactStep(float time,float dt,List<ContactProbe.Contact> contacts){
        for(ContactProbe.Contact c:contacts)
            emit(CONTACT,time,c.blobA,c.blobB,c.approachSpeed,c.slideSpeed,c.compression,
                    c.radiusA,c.radiusB,dt,c.bulkMotion);
        tracker.onContactStep(time,dt,contacts);
    }
    private void transition(ContactTracker.Transition t){
        if(t.kind==ContactTracker.Kind.END)emit(END,t.time,t.blobA,t.blobB,0,0);
        else if(t.kind==ContactTracker.Kind.CANCELLED)emit(CANCEL,t.time,t.blobA,t.blobB,0,0);
    }
    @Override public void onWallImpact(int blobId,int side,float approach,float radius,float time){
        emit(WALL,time,blobId,side,approach,radius);
    }
    @Override public void onMerge(BlobWorld.Merge m){
        tracker.onMerge(m);
        emit(MERGE,m.simulationTime,m.parentAId,m.parentBId,0,m.radius);
    }
    @Override public void onLaunch(int tier,float radius,float time){
        emit(LAUNCH,time,tier,0,0,radius);
    }
    @Override public void onPop(int blobId,float time,float radius){
        tracker.onPop(blobId,time);
        emit(POP,time,blobId,0,0,radius);
    }
    @Override public void onReset(){tracker.reset();reset.run();}
}
