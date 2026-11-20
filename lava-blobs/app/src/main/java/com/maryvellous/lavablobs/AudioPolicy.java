package com.maryvellous.lavablobs;

/** Pure audio tuning, independent of Android for unit tests. */
final class AudioPolicy {
    private AudioPolicy() {}
    static float mergeRate(int tier) {
        return Math.max(.92f, 1f - .01f * Math.max(0, tier - 1));
    }
    static float impactRate(int tier) {
        return Math.max(.79f, Math.min(1.42f, 1.38f - .07f * Math.max(0, tier)));
    }
    static float impactVolume(float speed) {
        return Math.min(.15f, .065f + Math.max(0f, speed - 70f) * .00045f);
    }
}
