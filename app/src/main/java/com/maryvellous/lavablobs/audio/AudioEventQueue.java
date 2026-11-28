package com.maryvellous.lavablobs.audio;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.ToIntFunction;

/** Bounded FIFO with reserved control capacity. Storage is allocated once;
 * admitted events are never evicted. All operations retain the original lock. */
public final class AudioEventQueue<T> {
    private final Object[] items;
    private final int capacity,observationLimit;
    private int head,tail,size;
    private long rejectedObservations,rejectedControls;
    private int highWater;
    public AudioEventQueue(int capacity,int reserved) {
        if(capacity<2||reserved<1||reserved>=capacity)throw new IllegalArgumentException();
        this.capacity=capacity;observationLimit=capacity-reserved;
        items=new Object[capacity];
    }
    private int next(int index){return index+1==capacity?0:index+1;}
    public synchronized boolean offer(T item,boolean authoritative) {
        if(size>=(authoritative?capacity:observationLimit)){
            if(authoritative)rejectedControls++;else rejectedObservations++;
            return false;
        }
        items[tail]=Objects.requireNonNull(item);tail=next(tail);size++;
        highWater=Math.max(highWater,size);
        return true;
    }
    @SuppressWarnings("unchecked")
    private T first(){return size==0?null:(T)items[head];}
    private T removeFirst(){
        T item=first();
        if(item!=null){items[head]=null;head=next(head);size--;}
        return item;
    }
    public synchronized T poll(){return removeFirst();}
    public synchronized T pollAtMost(ToIntFunction<T> generation,int expected){
        T first=first();
        return first!=null&&generation.applyAsInt(first)<=expected?removeFirst():null;
    }
    public synchronized void clear(){
        if(size!=0){
            if(head<tail)Arrays.fill(items,head,tail,null);
            else {Arrays.fill(items,head,capacity,null);Arrays.fill(items,0,tail,null);}
        }
        head=tail=size=0;
    }
    public synchronized int size(){return size;}
    public synchronized int highWater(){return highWater;}
    public synchronized long rejectedObservations(){return rejectedObservations;}
    public synchronized long rejectedControls(){return rejectedControls;}
}
