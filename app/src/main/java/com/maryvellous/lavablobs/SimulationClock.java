package com.maryvellous.lavablobs;

/** Frame pacing independent of Android Canvas. Fixed step and catch-up limits
 * deliberately match the approved game; reset drops accumulated old time. */
final class SimulationClock {
    static final float STEP=1f/60f;
    static final int MAX_STEPS=4;
    private long previousFrame,gapNs;
    private float elapsed,accumulator;
    float beginFrame(long now){
        gapNs=previousFrame==0?0:Math.max(0L,now-previousFrame);
        elapsed=Math.min(.10f,gapNs/1_000_000_000f);
        previousFrame=now;
        accumulator=Math.min(STEP*MAX_STEPS,accumulator+elapsed);
        return elapsed;
    }
    int advance(BlobWorld world){
        int steps=0;
        while(accumulator+1e-7f>=STEP&&steps<MAX_STEPS){
            world.tick(STEP);
            accumulator=Math.max(0f,accumulator-STEP);
            steps++;
        }
        return steps;
    }
    long gapNs(){return gapNs;}
    void reset(){previousFrame=gapNs=0;elapsed=accumulator=0;}
}
