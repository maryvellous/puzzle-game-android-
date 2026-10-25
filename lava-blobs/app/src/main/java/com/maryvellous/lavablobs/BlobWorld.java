package com.maryvellous.lavablobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pure Java simulation: upward buoyancy, soft collisions and chain mergers. */
final class BlobWorld {
    static final float WIDTH = 420f;
    static final float LEFT = 29f;
    static final float RIGHT = 391f;
    static final float TOP = 155f;
    static final int MAX_TIER = Blob.RADII.length - 1;

    static final class Merge {
        final float x, y, radius;
        final int tier, points;
        Merge(float x, float y, float radius, int tier, int points) {
            this.x = x; this.y = y; this.radius = radius;
            this.tier = tier; this.points = points;
        }
    }

    final ArrayList<Blob> blobs = new ArrayList<>();
    final ArrayList<Merge> merges = new ArrayList<>();
    private final Random random;
    float height = 850f;
    float time;
    float crowdedTime;
    int score;
    int nextTier;
    boolean gameOver;

    BlobWorld(Random random) {
        this.random = random;
        reset();
    }

    float bottom() {
        return height - 153f;
    }

    void setHeight(float newHeight) {
        height = Math.max(620f, newHeight);
    }

    void reset() {
        blobs.clear();
        merges.clear();
        score = 0;
        time = 0;
        crowdedTime = 0;
        gameOver = false;
        nextTier = rollTier();
    }

    private int rollTier() {
        int[] options = {0, 0, 0, 1, 1, 1, 2, 2, 3};
        return options[random.nextInt(options.length)];
    }

    boolean canLaunch(float x) {
        if (gameOver || blobs.size() > 75) return false;
        float r = Blob.RADII[nextTier];
        float clamped = clamp(x, LEFT + r, RIGHT - r);
        float y = bottom() - r - 7f;
        for (Blob b : blobs) {
            float dx = b.x - clamped, dy = b.y - y;
            if (dx * dx + dy * dy < (r + b.physicalRadius() - 5f) * (r + b.physicalRadius() - 5f))
                return false;
        }
        return true;
    }

    boolean launch(float x) {
        if (!canLaunch(x)) return false;
        float r = Blob.RADII[nextTier];
        Blob b = new Blob(clamp(x, LEFT + r, RIGHT - r), bottom() - r - 7f, nextTier, random);
        b.scale = .78f;
        b.vy = -184f;
        b.vx = (random.nextFloat() - .5f) * 22f;
        b.bump(0f, 1f, 100f);
        blobs.add(b);
        nextTier = rollTier();
        return true;
    }

    void tick(float delta) {
        if (gameOver) return;
        float dt = clamp(delta, 0f, .042f);
        for (int sub = 0; sub < 2; sub++) {
            step(dt * .5f);
        }
        boolean crowded = blobs.size() >= 8;
        if (crowded) {
            crowded = false;
            for (Blob b : blobs) {
                if (b.y + b.physicalRadius() > bottom() - 27f) {
                    crowded = true;
                    break;
                }
            }
        }
        crowdedTime = crowded ? crowdedTime + dt : Math.max(0f, crowdedTime - dt * 2.5f);
        if (crowdedTime > 2.8f || blobs.size() > 75) gameOver = true;
    }

    private void step(float dt) {
        time += dt;
        for (Blob b : blobs) {
            b.vy += (-128f + b.tier * 2.5f) * dt;
            b.vx += (float) Math.sin(time * 1.9 + b.y * .017 + b.tier) * 19f * dt;
            float friction = (float) Math.exp(-.66f * dt);
            b.vx *= friction;
            b.vy *= friction;
            b.vy = Math.max(-240f, b.vy);
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            confine(b);
        }
        // Resolve contacts and fusion after motion. One merger per substep
        // avoids processing removed bodies and leaves chain reactions stable.
        boolean fused = false;
        for (int i = 0; i < blobs.size() && !fused; i++) {
            Blob a = blobs.get(i);
            for (int j = i + 1; j < blobs.size(); j++) {
                Blob b = blobs.get(j);
                float dx = b.x - a.x, dy = b.y - a.y;
                float d2 = dx * dx + dy * dy;
                float sum = a.physicalRadius() + b.physicalRadius();
                if (d2 >= sum * sum) continue;
                float d = (float) Math.sqrt(d2);
                float nx = d > .0001f ? dx / d : 1f;
                float ny = d > .0001f ? dy / d : 0f;
                if (a.tier == b.tier && d < sum * .84f && a.age > .14f && b.age > .14f) {
                    fuse(i, j, a, b);
                    fused = true;
                    break;
                }
                float overlap = sum - d;
                float massA = a.radius() * a.radius();
                float massB = b.radius() * b.radius();
                float total = massA + massB;
                float shiftA = overlap * massB / total * .63f;
                float shiftB = overlap * massA / total * .63f;
                a.x -= nx * shiftA;
                a.y -= ny * shiftA;
                b.x += nx * shiftB;
                b.y += ny * shiftB;
                float approach = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny;
                if (approach < 0) {
                    float impulse = -(1.08f) * approach / (1f / massA + 1f / massB);
                    a.vx -= impulse * nx / massA;
                    a.vy -= impulse * ny / massA;
                    b.vx += impulse * nx / massB;
                    b.vy += impulse * ny / massB;
                }
                float wobble = Math.min(180f, 8f + overlap * 7f + Math.abs(approach) * .55f);
                a.bump(nx, ny, wobble * dt * 17f);
                b.bump(-nx, -ny, wobble * dt * 17f);
            }
        }
        for (Blob b : blobs) {
            confine(b);
            b.updateShape(dt, time);
        }
    }

    private void confine(Blob b) {
        float r = b.physicalRadius();
        if (b.x < LEFT + r) {
            b.x = LEFT + r;
            if (b.vx < 0) { b.bump(-1, 0, Math.abs(b.vx) * .9f); b.vx *= -.28f; }
        } else if (b.x > RIGHT - r) {
            b.x = RIGHT - r;
            if (b.vx > 0) { b.bump(1, 0, Math.abs(b.vx) * .9f); b.vx *= -.28f; }
        }
        if (b.y < TOP + r) {
            b.y = TOP + r;
            if (b.vy < 0) { b.bump(0, -1, Math.abs(b.vy) * 1.2f); b.vy *= -.14f; }
        } else if (b.y > bottom() - r) {
            b.y = bottom() - r;
            if (b.vy > 0) { b.bump(0, 1, Math.abs(b.vy)); b.vy *= -.18f; }
        }
    }

    private void fuse(int first, int second, Blob a, Blob b) {
        int newTier = Math.min(MAX_TIER, a.tier + 1);
        int points = (a.tier + 1) * (a.tier + 1) * 20;
        score += points;
        float x = (a.x + b.x) * .5f;
        float y = (a.y + b.y) * .5f;
        blobs.remove(second);
        blobs.remove(first);
        if (a.tier == MAX_TIER) {
            // Two maximum-size bubbles dissolve, freeing precious space.
            merges.add(new Merge(x, y, a.radius() * 1.3f, newTier, points));
            return;
        }
        Blob born = new Blob(x, y, newTier, random);
        born.vx = (a.vx + b.vx) * .5f;
        born.vy = (a.vy + b.vy) * .5f - 57f;
        born.scale = .70f;
        born.bump(0f, 1f, 95f);
        confine(born);
        blobs.add(born);
        merges.add(new Merge(x, y, born.radius(), newTier, points));
    }

    List<Merge> consumeMerges() {
        ArrayList<Merge> drained = new ArrayList<>(merges);
        merges.clear();
        return drained;
    }

    static float clamp(float value, float lo, float hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
