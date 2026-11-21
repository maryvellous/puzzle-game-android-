package com.maryvellous.lavablobs;

/** Pure audio tuning, independent of Android for unit tests. */
final class AudioPolicy {
    private AudioPolicy() {}

    static final float IMPACT_PAIR_COOLDOWN_SECONDS = .70f;
    static final long IMPACT_MIN_GAP_MS = 350L;
    static final float IMPACT_VOLUME = .08f;

    /** Reject a truncated or nearly silent decoded launch/POP effect. */
    static boolean usableMonoPcm16(byte[] pcm,int offset,int size) {
        if(pcm==null||offset<0||size<512||size%2!=0||offset>pcm.length-size)return false;
        int peak=0;
        long sum=0L;
        for(int i=offset;i<offset+size;i+=2) {
            int sample=(short)((pcm[i]&255)|((pcm[i+1]&255)<<8));
            int abs=Math.abs(sample);
            peak=Math.max(peak,abs);
            sum+=abs;
        }
        return peak>=256 && sum/(size/2)>=64;
    }

    static float mergeRate(int tier) {
        return Math.max(.92f, 1f - .01f * Math.max(0, tier - 1));
    }

    static boolean shouldPlayImpact(float approachSpeed) {
        return approachSpeed >= 42f;
    }

    static boolean allowPairImpactAt(float nowSeconds,Float previousSeconds) {
        return previousSeconds==null||nowSeconds-previousSeconds>=IMPACT_PAIR_COOLDOWN_SECONDS;
    }

    /** Do not repeat impact sounds in dense shakes or immediately after a merge. */
    static boolean allowImpactAt(long nowMs,long lastImpactMs,long lastMergeMs) {
        return (lastImpactMs==0L || nowMs-lastImpactMs>=IMPACT_MIN_GAP_MS)
                &&(lastMergeMs==0L || nowMs-lastMergeMs>=100L);
    }

    static float impactVolume() {return IMPACT_VOLUME;}
}
