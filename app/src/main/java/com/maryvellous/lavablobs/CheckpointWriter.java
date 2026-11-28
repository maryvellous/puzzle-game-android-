package com.maryvellous.lavablobs;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Bounded latest-wins mailbox. A single executor orders writes and slot
 * barriers; capture happens outside this class on the simulation owner. */
final class CheckpointWriter<T> {
    private final ExecutorService executor;
    private final Consumer<T> write;
    private final Object lock=new Object();
    private T pending;
    private boolean drainScheduled;
    CheckpointWriter(ExecutorService executor,Consumer<T> write){
        this.executor=executor;this.write=write;
    }
    void offer(T value){
        Objects.requireNonNull(value);
        synchronized(lock){
            pending=value;
            if(!drainScheduled){drainScheduled=true;executor.execute(this::drain);}
        }
    }
    // Worker-owned; reported at the next barrier if no later write succeeds.
    private RuntimeException lastFailure;
    private void drain(){
        while(true){
            T next;
            synchronized(lock){
                next=pending;pending=null;
                if(next==null){drainScheduled=false;return;}
            }
            try{write.accept(next);lastFailure=null;}
            catch(RuntimeException failure){lastFailure=failure;}
        }
    }
    /** Only the owner thread submits; no new offers while awaiting a barrier. */
    Future<?> barrier(){
        synchronized(lock){return executor.submit(()->{if(lastFailure!=null)throw lastFailure;});}
    }
    /** Drop queued writes but let an in-flight atomic write finish before clear. */
    Future<?> discardAndRun(Runnable action){
        synchronized(lock){pending=null;return executor.submit(()->{action.run();lastFailure=null;});}
    }
}
