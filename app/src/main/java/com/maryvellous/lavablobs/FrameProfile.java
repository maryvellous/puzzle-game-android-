package com.maryvellous.lavablobs;

import java.util.Locale;

/** Allocation-free samples on the UI thread, exported only on explicit request. */
final class FrameProfile {
    private static final String[] LABELS={"0-4","5-7","8-10","11-15","16+"};
    private final long[][] totals=new long[LABELS.length][12];
    private final long[][] gaps=new long[5][251],ticks=new long[5][251],saves=new long[5][251];
    private static final int RECENT=2048;
    private final long[] recentGap=new long[RECENT],recentAt=new long[RECENT];
    private final int[] recentBucket=new int[RECENT],recentSteps=new int[RECENT];
    private int recentCursor,recentSize;
    private static void bin(long[] h,long ns){h[(int)Math.min(250,Math.max(0,ns/1_000_000L))]++;}
    private static String percentile(long[] h,double p){
        long n=0;for(long v:h)n+=v;
        if(n==0)return "-";
        long target=(long)Math.ceil(n*p),sum=0;
        for(int i=0;i<h.length;i++){sum+=h[i];if(sum>=target)return i==250?">=250":"<"+(i+1);}
        return "-";
    }
    static int bucket(int count){return count<=4?0:count<=7?1:count<=10?2:count<=15?3:4;}
    void frame(int count,long gap,long physics,long canvas,long total,int steps){
        if(gap<=0)return;
        int b=bucket(count);
        long[] a=totals[b];
        bin(gaps[b],gap);
        if(steps>0)bin(ticks[b],physics/steps);
        int r=recentCursor;recentAt[r]=System.nanoTime();recentGap[r]=gap;recentBucket[r]=b;recentSteps[r]=steps;
        recentCursor=(r+1)%RECENT;recentSize=Math.min(RECENT,recentSize+1);
        a[0]++;a[1]+=gap;a[2]+=physics;a[3]+=canvas;a[4]+=total;a[5]+=steps;
        if(gap>25_000_000L)a[6]++;
        a[7]=Math.max(a[7],gap);a[8]=Math.max(a[8],total);
    }
    void save(int count,long duration){
        int b=bucket(count);long[] a=totals[b];bin(saves[b],duration);a[9]++;a[10]+=duration;a[11]=Math.max(a[11],duration);
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
            out.append("  gap p95=").append(percentile(gaps[i],.95)).append(" ms p99=").append(percentile(gaps[i],.99))
                .append(" ms; per-frame mean tick p95=").append(percentile(ticks[i],.95))
                .append(" ms; save p95=").append(percentile(saves[i],.95)).append(" ms\n");
        }
        long now=recentSize==0?0:recentAt[(recentCursor-1+RECENT)%RECENT];long[][] recent=new long[5][4];
        for(int j=0;j<recentSize;j++){
            int k=(recentCursor-1-j+RECENT)%RECENT;
            if(now-recentAt[k]>20_000_000_000L)break;
            long[] a=recent[recentBucket[k]];a[0]++;a[1]+=recentGap[k];a[2]+=recentSteps[k];if(recentGap[k]>25_000_000L)a[3]++;
        }
        out.append("Recent active frames, 20 s ending at last sample:\n");
        for(int i=0;i<5;i++){long[] a=recent[i];if(a[1]>0)out.append(String.format(Locale.US,"blob=%s frames=%d fps=%.1f steps/s=%.1f gaps>25ms=%d%n",LABELS[i],a[0],a[0]*1e9/a[1],a[2]*1e9/a[1],a[3]));}
        return out.toString();
    }
}
