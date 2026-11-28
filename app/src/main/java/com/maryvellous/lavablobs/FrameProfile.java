package com.maryvellous.lavablobs;

import java.util.Locale;

/** Allocation-free samples on the UI thread, exported only on explicit request. */
final class FrameProfile {
    private static final String[] LABELS={"0-4","5-7","8-10","11-15","16+"};
    private final long[][] totals=new long[LABELS.length][12];
    static int bucket(int count){return count<=4?0:count<=7?1:count<=10?2:count<=15?3:4;}
    void frame(int count,long gap,long physics,long canvas,long total,int steps){
        if(gap<=0)return;
        long[] a=totals[bucket(count)];
        a[0]++;a[1]+=gap;a[2]+=physics;a[3]+=canvas;a[4]+=total;a[5]+=steps;
        if(gap>25_000_000L)a[6]++;
        a[7]=Math.max(a[7],gap);a[8]=Math.max(a[8],total);
    }
    void save(int count,long duration){
        long[] a=totals[bucket(count)];a[9]++;a[10]+=duration;a[11]=Math.max(a[11],duration);
    }
    private static double ms(long n,long count){return count==0?0:n/(1e6*count);}
    String report(){
        StringBuilder out=new StringBuilder("Frame intervals and UI-thread wall times; not GPU timings.\n");
        for(int i=0;i<LABELS.length;i++){
            long[] a=totals[i];
            if(a[0]==0&&a[9]==0)continue;
            double fps=a[1]==0?0:a[0]*1e9/a[1],sim=a[1]==0?0:a[5]*1e9/a[1];
            out.append(String.format(Locale.US,
                "blob=%s frames=%d fps=%.1f steps/s=%.1f gaps>25ms=%d gapMax=%.2fms physics/frame=%.2fms physics/tick=%.2fms canvas/frame=%.2fms UI/frame=%.2fms UImax=%.2fms saves=%d saveAvg=%.2fms saveMax=%.2fms%n",
                LABELS[i],a[0],fps,sim,a[6],ms(a[7],1),ms(a[2],a[0]),ms(a[2],a[5]),ms(a[3],a[0]),ms(a[4],a[0]),ms(a[8],1),a[9],ms(a[10],a[9]),ms(a[11],1)));
        }
        return out.toString();
    }
}
