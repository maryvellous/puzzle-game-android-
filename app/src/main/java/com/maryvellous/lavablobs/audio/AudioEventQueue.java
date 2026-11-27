package com.maryvellous.lavablobs.audio;

import java.util.ArrayDeque;

/** FIFO with reserved control capacity. Never evicts an accepted event. */
public final class AudioEventQueue<T> {
    private final ArrayDeque<T> items=new ArrayDeque<>();
    private final int capacity,observationLimit;
    private long rejectedObservations,rejectedControls;
    private int highWater;
    public AudioEventQueue(int capacity,int reserved) {
        if(capacity<2||reserved<1||reserved>=capacity)throw new IllegalArgumentException();
        this.capacity=capacity;observationLimit=capacity-reserved;
    }
    public synchronized boolean offer(T item,boolean authoritative) {
        if(items.size()>=(authoritative?capacity:observationLimit)){
            if(authoritative)rejectedControls++;else rejectedObservations++;
            return false;
        }
        items.addLast(item);
        highWater=Math.max(highWater,items.size());
        return true;
    }
    public synchronized T poll(){return items.pollFirst();}
    public synchronized void clear(){items.clear();}
    public synchronized int size(){return items.size();}
    public synchronized int highWater(){return highWater;}
    public synchronized long rejectedObservations(){return rejectedObservations;}
    public synchronized long rejectedControls(){return rejectedControls;}
}
