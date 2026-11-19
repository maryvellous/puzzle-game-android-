package com.maryvellous.lavablobs;

/**
 * Removes slowly changing gravity/tilt from accelerometer samples and detects
 * abrupt movements. No platform dependencies so thresholds can be unit-tested.
 */
final class ShakeDetector {
    static final class Impulse {
        final float x, y, z;
        Impulse(float x, float y, float z) {
            this.x = x; this.y = y; this.z = z;
        }
    }

    private static final float THRESHOLD = 3.7f;  // m/s² after gravity filtering
    private static final long COOLDOWN_NS = 160_000_000L;
    private static final float GRAVITY_TAU_SECONDS = .45f;

    private float gx, gy, gz;
    private long lastSample, lastShake;
    private boolean initialized;

    void reset() {
        initialized = false;
        lastSample = 0L;
        lastShake = 0L;
    }

    Impulse sample(float x, float y, float z, long timestampNs) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return null;
        if (!initialized || timestampNs <= lastSample) {
            gx = x; gy = y; gz = z;
            lastSample = timestampNs;
            initialized = true;
            return null; // Never react to the initial gravity reading.
        }
        float seconds = Math.min(.10f, Math.max(.001f, (timestampNs - lastSample) * 1e-9f));
        lastSample = timestampNs;
        float alpha = 1f - (float) Math.exp(-seconds / GRAVITY_TAU_SECONDS);
        gx += alpha * (x - gx);
        gy += alpha * (y - gy);
        gz += alpha * (z - gz);

        float dx = x - gx, dy = y - gy, dz = z - gz;
        float energy = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (energy < THRESHOLD || (lastShake != 0L && timestampNs - lastShake < COOLDOWN_NS)) {
            return null;
        }
        lastShake = timestampNs;
        return new Impulse(dx, dy, dz);
    }
}
