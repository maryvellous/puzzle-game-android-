package com.maryvellous.lavablobs;

import java.util.Random;

/**
 * An independently animated, 20-node elastic perimeter around a moving mass.
 * Collisions press the perimeter inward; neighbour springs redistribute the
 * displacement, which decays gradually rather than snapping to a circle.
 */
final class Blob {
    static final float[] RADII = {18f, 23f, 29f, 36f, 45f, 56f, 69f, 84f, 99f};
    static final int NODES = 20;

    float x, y, vx, vy, age;
    float scale = 0.70f;
    final int tier;
    final float[] deformation = new float[NODES];
    private final float[] speed = new float[NODES];
    private final float phase;

    Blob(float x, float y, int tier, Random random) {
        this.x = x;
        this.y = y;
        this.tier = tier;
        phase = random.nextFloat() * 6.2831853f;
        for (int i = 0; i < NODES; i++) {
            deformation[i] = (random.nextFloat() - .5f) * radius() * .04f;
        }
    }

    float radius() {
        return RADII[tier];
    }

    /**
     * The same deformable outline as the renderer, sampled along an arbitrary
     * ray. Neither launch checks nor collisions use an invisible solid circle.
     */
    float outlineRadius(float angle, float time) {
        final float twoPi = (float) (Math.PI * 2.0);
        float normalised = angle % twoPi;
        if (normalised < 0f) normalised += twoPi;
        float index = normalised * NODES / twoPi;
        int first = Math.min(NODES - 1, (int) index);
        float fraction = index - first;
        float a = surfaceRadius(first, time);
        float b = surfaceRadius((first + 1) % NODES, time);
        return a + (b - a) * fraction;
    }

    float surfaceRadius(int node, float time) {
        float theta = (float) (node * Math.PI * 2 / NODES);
        float organic = (float) (Math.sin(3 * theta + time * 1.65 + phase) * .018
                + Math.sin(5 * theta - time * 1.10 + phase * 1.6) * .012
                + Math.sin(time * 2.3 + phase) * .009) * radius();
        return Math.max(radius() * .56f,
                radius() * scale + deformation[node] + organic);
    }

    void bump(float nx, float ny, float force) {
        float strength = Math.min(210f, Math.max(0f, force));
        for (int i = 0; i < NODES; i++) {
            double theta = i * 2 * Math.PI / NODES;
            float directional = (float) (Math.cos(theta) * nx + Math.sin(theta) * ny);
            if (directional > .15f) {
                float proximity = (directional - .15f) / .85f;
                speed[i] -= strength * proximity * proximity;
            } else if (directional < -.50f) {
                speed[i] += strength * .10f * (-directional);
            }
        }
    }

    /**
     * Shape deformation also participates in contact resolution. The leading
     * face becomes flatter and the opposite face bulges very slightly, so
     * touching blobs visibly yield instead of floating apart on rigid hitboxes.
     */
    void squish(float nx, float ny, float depth) {
        float indentation = Math.min(radius() * .17f, Math.max(0f, depth));
        float limit = radius() * .28f;
        for (int i = 0; i < NODES; i++) {
            double theta = i * 2.0 * Math.PI / NODES;
            float alignment = (float) (Math.cos(theta) * nx + Math.sin(theta) * ny);
            if (alignment > .15f) {
                float local = (alignment - .15f) / .85f;
                deformation[i] -= indentation * local * local;
            } else if (alignment < -.45f) {
                deformation[i] += indentation * .22f * (-alignment - .45f) / .55f;
            }
            deformation[i] = Math.max(-limit, Math.min(limit, deformation[i]));
        }
    }

    void updateShape(float dt, float time) {
        age += dt;
        // A merge expands beyond target very briefly, followed by a damped settle.
        scale += (1f - scale) * Math.min(1f, dt * 6.8f);
        float[] accelerations = new float[NODES];
        for (int i = 0; i < NODES; i++) {
            int previous = (i + NODES - 1) % NODES;
            int next = (i + 1) % NODES;
            float neighbours = deformation[previous] + deformation[next] - 2f * deformation[i];
            float ripple = (float) Math.sin(time * 3.3 + phase + i * .78) * .38f;
            accelerations[i] = -63f * deformation[i] + 36f * neighbours + ripple;
        }
        float damping = (float) Math.exp(-4.6f * dt);
        float max = radius() * .28f;
        for (int i = 0; i < NODES; i++) {
            speed[i] = (speed[i] + accelerations[i] * dt) * damping;
            deformation[i] += speed[i] * dt;
            if (deformation[i] < -max) {
                deformation[i] = -max;
                speed[i] = Math.max(0f, speed[i]) * .25f;
            } else if (deformation[i] > max) {
                deformation[i] = max;
                speed[i] = Math.min(0f, speed[i]) * .25f;
            }
        }
    }
}
