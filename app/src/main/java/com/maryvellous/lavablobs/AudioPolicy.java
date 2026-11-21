package com.maryvellous.lavablobs;

/** Pure audio tuning, independent of Android for unit tests. */
final class AudioPolicy {
    private AudioPolicy() {}
    /** Reject a truncated or nearly silent decoded effect, so impact can fall back. */
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
        return approachSpeed >= 20f;
    }
    /** Prefer the edited impact, then a different bundled source (never the merge Bloop). */
    static int impactSample(int editedId, boolean editedReady, int fallbackId, boolean fallbackReady) {
        if(editedId>0 && editedReady)return editedId;
        return fallbackId>0 && fallbackReady ? fallbackId : 0;
    }
    /** Remove the rounded low-frequency body that makes an impact sound like a merge.
     * Leaves a short, lighter 'wip' from the original sampled sound, not a new synth. */
    static void softenImpactPcm16(byte[] pcm) {
        float low=0f;
        for(int i=0;i+1<pcm.length;i+=2) {
            int raw=(short)((pcm[i]&255)|((pcm[i+1]&255)<<8));
            low+=.075f*(raw-low);
            int soft=Math.round((raw-low)*.68f);
            soft=Math.max(-32768,Math.min(32767,soft));
            pcm[i]=(byte)(soft&255);
            pcm[i+1]=(byte)((soft>>8)&255);
        }
    }
    static float impactRate(int tier) {
        return Math.max(1.02f, Math.min(1.55f, 1.51f - .055f * Math.max(0, tier)));
    }
    static float impactVolume(float speed) {
        return Math.min(.18f, .095f + Math.max(0f, speed - 20f) * .00030f);
    }
}
