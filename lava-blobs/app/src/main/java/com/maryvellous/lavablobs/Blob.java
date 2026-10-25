package com.maryvellous.lavablobs;

import java.util.Random;

/**
 * An independently animated, 32-node elastic perimeter around a moving mass.
 * Collisions press the perimeter inward; neighbour springs redistribute the
 * displacement, which decays gradually rather than snapping to a circle.
 */
final class Blob {
    static final float[] RADII = {18f, 23f, 29f, 36f, 45f, 56f, 69f, 84f, 99f};
    static final int NODES = 32;

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
        return Math.max(radius() * .36f, Math.min(radius() * 1.46f,
                radius() * scale + deformation[node] + organic));
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
     * Sculpt the actual silhouette at contact: almost all penetration becomes
     * a broad flat dimple; displacement also flows toward perpendicular
     * lobes, so blobs look like compressible jelly rather than rigid balls.
     * The same nodes drive both rendering and collision support distances.
     */
    void squish(float nx, float ny, float depth) {
        float radius = radius();
        float indentation = Math.min(radius * .56f, Math.max(0f, depth));
        if (indentation < .001f) return;
        for (int i = 0; i < NODES; i++) {
            float theta = (float) (i * 2.0 * Math.PI / NODES);
            float alignment = (float) (Math.cos(theta) * nx + Math.sin(theta) * ny);
            float face = Math.max(0f, (alignment - .04f) / .96f);
            // A smooth, wide contact patch instead of a single sharp dimple.
            float inward = indentation * face * face;
            // Material displaced at the contact fattens the two shoulders.
            float shoulder = Math.max(0f, 1f - Math.abs(alignment) / .52f);
            float sideways = indentation * .46f * shoulder * shoulder;
            deformation[i] = Math.max(-radius * .56f,
                    Math.min(radius * .46f, deformation[i] - inward + sideways));
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
            accelerations[i] = -19f * deformation[i] + 26f * neighbours + ripple;
        }
        // A softer membrane: neighbouring nodes transmit a ripple before
        // the perimeter returns to rest, rather than instantly springing back.
        float damping = (float) Math.exp(-3.3f * dt);
        float minimum = -radius() * .56f;
        float maximum = radius() * .46f;
        for (int i = 0; i < NODES; i++) {
            speed[i] = (speed[i] + accelerations[i] * dt) * damping;
            deformation[i] += speed[i] * dt;
            if (deformation[i] < minimum) {
                deformation[i] = minimum;
                speed[i] = Math.max(0f, speed[i]) * .25f;
            } else if (deformation[i] > maximum) {
                deformation[i] = maximum;
                speed[i] = Math.min(0f, speed[i]) * .25f;
            }
        }
    }
}
