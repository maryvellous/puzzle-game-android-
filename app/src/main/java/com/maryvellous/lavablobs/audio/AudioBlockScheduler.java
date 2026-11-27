package com.maryvellous.lavablobs.audio;

import java.util.function.IntSupplier;

/** Worker-owned scheduler. No Android dependency. A generation change invalidates a block. */
public final class AudioBlockScheduler {
    private static final java.util.function.ToIntFunction<AudioEvent> EVENT_GENERATION=e->e.generation;
    private final AudioEventQueue<AudioEvent> queue;
    private final AudioTimeline timeline;
    private final int maxDispatch;
    private AudioEvent pending;
    private long pendingFrame,cursor;
    private long maxQueueAgeNs,maxDispatchNs,dispatched,budgetHits;
    public AudioBlockScheduler(AudioEventQueue<AudioEvent> queue,AudioTimeline timeline,int maxDispatch){
        if(maxDispatch<=0)throw new IllegalArgumentException();
        this.queue=queue;this.timeline=timeline;this.maxDispatch=maxDispatch;
    }
    public boolean render(MaterialAudioEngine engine,float[] output,int generation,IntSupplier currentGeneration){
        int offset=0,processed=0;
        long dispatchNs=0;
        while(processed<maxDispatch&&generation==currentGeneration.getAsInt()){
            if(pending==null){
                pending=queue.pollAtMost(EVENT_GENERATION,generation);
                if(pending==null)break;
                if(pending.generation!=generation){pending=null;processed++;continue;}
                pendingFrame=timeline.schedule(pending.time,cursor+offset);
            }
            if(pendingFrame>=cursor+output.length)break;
            int at=(int)Math.max(offset,pendingFrame-cursor);
            if(at>offset){engine.renderMono(output,offset,at-offset);offset=at;}
            long started=System.nanoTime();
            if(pending.enqueuedNs>0)maxQueueAgeNs=Math.max(maxQueueAgeNs,started-pending.enqueuedNs);
            pending.apply(engine);
            dispatchNs+=System.nanoTime()-started;
            dispatched++;processed++;pending=null;
        }
        if(processed==maxDispatch)budgetHits++;
        maxDispatchNs=Math.max(maxDispatchNs,dispatchNs);
        if(offset<output.length)engine.renderMono(output,offset,output.length-offset);
        if(generation!=currentGeneration.getAsInt())return false;
        cursor+=output.length;
        return true;
    }
    public void reset(){pending=null;cursor=0;timeline.reset();}
    public long cursor(){return cursor;}
    public long maxQueueAgeNs(){return maxQueueAgeNs;}
    public long maxDispatchNs(){return maxDispatchNs;}
    public long dispatched(){return dispatched;}
    public long budgetHits(){return budgetHits;}
    public long lateEvents(){return timeline.lateEvents();}
    public long rebases(){return timeline.rebases();}
    public void resetIntervalMaxima(){maxQueueAgeNs=maxDispatchNs=0;}
}
