package com.maryvellous.lavablobs;

import java.util.Random;

/**
 * A closed 24-particle, two-dimensional pressure membrane.
 *
 * Every visible vertex has its own position and velocity. Springs constrain
 * neighbour distances, an area constraint represents internal fluid pressure,
 * and collisions directly move particles rather than deforming a fake ring
 * around an invisible circle. There are NO radial displacement limits or
 * rigid circular hitboxes in this class.
 */
final class Blob {
    static final float[] RADII = {20f, 26f, 32f, 40f, 50f, 63f, 77f, 94f, 111f};
    static final int NODES = 24;
    private static final float TWO_PI = (float) (Math.PI * 2);
    private static final float PERIMETER_FACTOR = (float) (2 * Math.sin(Math.PI / NODES));
    private static final float BEND_FACTOR = (float) (2 * Math.sin(2 * Math.PI / NODES));
    private static final float AREA_FACTOR = (float) (NODES * .5 * Math.sin(TWO_PI / NODES));

    final int tier;
    // Assigned by BlobWorld (0 means not registered). Never derived from object identity.
    int id;
    final float[] px = new float[NODES], py = new float[NODES];
    final float[] vx = new float[NODES], vy = new float[NODES];
    private final float[] lastX = new float[NODES], lastY = new float[NODES];

    float x, y, age, radiusTarget, launchScale;

    Blob(float centerX, float centerY, int tier, Random random) {
        this(centerX, centerY, tier, 1f);
    }

    Blob(float centerX, float centerY, int tier, float initialScale) {
        this.tier = tier;
        launchScale = Math.max(.65f, Math.min(1f, initialScale));
        radiusTarget = radius() * launchScale;
        for (int i = 0; i < NODES; i++) {
            float angle = TWO_PI * i / NODES;
            px[i] = centerX + (float) Math.cos(angle) * radiusTarget;
            py[i] = centerY + (float) Math.sin(angle) * radiusTarget;
        }
        recenter();
    }

    float radius() {
        if(tier<RADII.length)return RADII[tier];
        // Unlimited merge tiers: colour repeats after nine, size grows until
        // the finite playfield determines practical impossibility.
        return (float)Math.min(179.2,111.0*Math.pow(1.15,tier-(RADII.length-1)));
    }

    void recenter() {
        float sx = 0f, sy = 0f;
        for (int i = 0; i < NODES; i++) { sx += px[i]; sy += py[i]; }
        x = sx / NODES;
        y = sy / NODES;
    }

    void translate(float dx, float dy) {
        for (int i = 0; i < NODES; i++) {
            px[i] += dx; py[i] += dy;
        }
        x += dx; y += dy;
    }

    float area() {
        float sum = 0f;
        for (int i = 0; i < NODES; i++) {
            int j = (i + 1) % NODES;
            sum += px[i] * py[j] - px[j] * py[i];
        }
        return sum * .5f;
    }

    float expectedArea() {
        return AREA_FACTOR * radiusTarget * radiusTarget;
    }

    void kick(float kickX, float kickY, Random random, float strength) {
        for (int i = 0; i < NODES; i++) {
            vx[i] += kickX + (random.nextFloat() - .5f) * strength;
            vy[i] += kickY + (random.nextFloat() - .5f) * strength;
        }
    }

    void integrate(float dt, float time) {
        age += dt;
        // The merged/spawned membrane inflates gradually, not as a visual-only
        // scale transform. Target area and spring rest lengths change together.
        radiusTarget += (radius() - radiusTarget) * Math.min(1f, 3.7f * dt);
        // The drift, lift and damping are shared by every particle of this blob.
        // Calculate them once per integration step rather than 24 times.
        float drift = (float) Math.sin(time * 1.65f + y * .014f + tier) * 13f * dt;
        float lift = (-135f + tier * 1.5f) * dt;
        float friction = (float) Math.exp(-1.65f * dt);
        for (int i = 0; i < NODES; i++) {
            lastX[i] = px[i];
            lastY[i] = py[i];
            vx[i] += drift;
            vy[i] += lift;
            vx[i] *= friction;
            vy[i] *= friction;
            vx[i] = clamp(vx[i], -380f, 380f);
            vy[i] = clamp(vy[i], -380f, 380f);
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
        }
        recenter();
    }

    void solveMembrane() {
        // Strong perimeter, weaker bending, firm but compliant area pressure.
        float restEdge = radiusTarget * PERIMETER_FACTOR;
        float restBend = radiusTarget * BEND_FACTOR;
        for (int i = 0; i < NODES; i++) {
            solveDistance(i, (i + 1) % NODES, restEdge, .77f);
        }
        for (int i = 0; i < NODES; i++) {
            solveDistance(i, (i + 2) % NODES, restBend, .21f);
        }
        solvePressure();
    }

    private void solveDistance(int i, int j, float target, float stiffness) {
        float dx = px[j] - px[i], dy = py[j] - py[i];
        float dist = (float) Math.hypot(dx, dy);
        if (dist < .001f) return;
        float amount = clamp((dist - target) / dist * stiffness * .5f, -.25f, .25f);
        float cx = dx * amount, cy = dy * amount;
        px[i] += cx; py[i] += cy;
        px[j] -= cx; py[j] -= cy;
    }

    private void solvePressure() {
        float target = expectedArea();
        float current = area();
        float sumGradient = 0f;
        // Global signed area constraint, the same technique used in PBD
        // incompressible membranes. Avoids pointy dents and area collapse.
        for (int i = 0; i < NODES; i++) {
            int p = (i + NODES - 1) % NODES, n = (i + 1) % NODES;
            float gx = (py[n] - py[p]) * .5f;
            float gy = (px[p] - px[n]) * .5f;
            sumGradient += gx * gx + gy * gy;
        }
        if (sumGradient < 1e-4f) return;
        float lambda = (target - current) * .66f / sumGradient;
        for (int i = 0; i < NODES; i++) {
            int p = (i + NODES - 1) % NODES, n = (i + 1) % NODES;
            float gx = (py[n] - py[p]) * .5f;
            float gy = (px[p] - px[n]) * .5f;
            px[i] += clamp(lambda * gx, -4.5f, 4.5f);
            py[i] += clamp(lambda * gy, -4.5f, 4.5f);
        }
    }

    void confine(float left, float right, float top, float bottom) {
        for (int i = 0; i < NODES; i++) {
            px[i] = clamp(px[i], left + .8f, right - .8f);
            py[i] = clamp(py[i], top + .8f, bottom - .8f);
        }
    }

    void finish(float dt) {
        float inv = 1f / Math.max(.001f, dt);
        for (int i = 0; i < NODES; i++) {
            vx[i] = clamp((px[i] - lastX[i]) * inv, -360f, 360f);
            vy[i] = clamp((py[i] - lastY[i]) * inv, -360f, 360f);
        }
        recenter();
    }

    static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
