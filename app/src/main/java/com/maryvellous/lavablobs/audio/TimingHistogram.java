package com.maryvellous.lavablobs.audio;

/** Log2 nanosecond buckets: conservative approximate p99, bounded storage. */
public final class TimingHistogram {
    private final long[] buckets=new long[64];
    private long count,max;
    public void record(long ns){
        if(ns<0)return;
        int index=ns<=1?0:64-Long.numberOfLeadingZeros(ns-1);
        buckets[index]++;count++;max=Math.max(max,ns);
    }
    public long p99UpperNs(){
        if(count==0)return 0;
        long target=count-count/100,seen=0;
        for(int i=0;i<buckets.length;i++){
            seen+=buckets[i];
            if(seen>=target)return i==63?max:Math.min(max,1L<<i);
        }
        return max;
    }
    public long count(){return count;}
    public void reset(){java.util.Arrays.fill(buckets,0);count=max=0;}
}
