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

    /**
     * A short burst of acceleration moves the blobs as if the lamp had been
     * jolted. Horizontal sensor X runs right; screen Y runs downward, opposite
     * to accelerometer Y. Z-only shakes still stir the liquid via turbulence.
     */
    void shake(float sensorX, float sensorY, float sensorZ) {
        if (gameOver || blobs.isEmpty()) return;
        float magnitude = (float) Math.sqrt(sensorX * sensorX + sensorY * sensorY
                + sensorZ * sensorZ);
        float amplitude = clamp(magnitude / 8f, .55f, 1.75f);
        float inertialX = clamp(-sensorX * 25f, -320f, 320f);
        float inertialY = clamp(sensorY * 25f, -320f, 320f);
        float depth = Math.min(150f, Math.abs(sensorZ) * 22f);
        for (Blob b : blobs) {
            float sizeFactor = 1.15f - b.tier * .065f;
            // Shared inertial motion plus small independent swirls prevents
            // every bubble from translating as a single rigid block.
            float spread = 37f * amplitude;
            float swirlX = (random.nextFloat() * 2f - 1f) * spread;
            float swirlY = (random.nextFloat() * 2f - 1f) * spread;
            float dx = b.x - WIDTH * .5f;
            float dy = b.y - (TOP + bottom()) * .5f;
            float length = Math.max(35f, (float) Math.hypot(dx, dy));
            float outwardX = dx / length * depth * amplitude;
            float outwardY = dy / length * depth * amplitude;
            float impulseX = (inertialX + swirlX + outwardX) * sizeFactor;
            float impulseY = (inertialY + swirlY + outwardY) * sizeFactor;
            b.vx = clamp(b.vx + impulseX, -430f, 430f);
            b.vy = clamp(b.vy + impulseY, -430f, 430f);
            float impulseLength = (float) Math.hypot(impulseX, impulseY);
            float nx = impulseLength > .01f ? impulseX / impulseLength : 1f;
            float ny = impulseLength > .01f ? impulseY / impulseLength : 0f;
            b.bump(nx, ny, 105f * amplitude);
        }
    }

    boolean canLaunch(float x) {
        if (gameOver || blobs.size() > 75) return false;
        float r = Blob.RADII[nextTier];
        float spawnR = r * .78f;
        float clamped = clamp(x, LEFT + r, RIGHT - r);
        float y = bottom() - r - 7f;
        for (Blob b : blobs) {
            float dx = clamped - b.x, dy = y - b.y;
            float d = (float) Math.hypot(dx, dy);
            // Visible launch size (0.78x), not a full-sized invisible collider.
            float contact = spawnR + b.outlineRadius((float) Math.atan2(dy, dx), time);
            if (d < contact - 2f) return false;
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
                if (b.y + b.outlineRadius((float) (Math.PI / 2), time) > bottom() - 27f) {
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
            b.updateShape(dt, time);
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
                // Broad phase only: this bound cannot reject a possible contact,
                // even at maximum allowed stretch of the perimeter.
                float broad = (a.radius() + b.radius()) * 1.33f;
                if (d2 > broad * broad) continue;
                float d = (float) Math.sqrt(d2);
                float nx = d > .0001f ? dx / d : 1f;
                float ny = d > .0001f ? dy / d : 0f;
                float angle = (float) Math.atan2(ny, nx);
                float sum = a.outlineRadius(angle, time)
                        + b.outlineRadius(angle + (float)Math.PI, time);
                if (d >= sum) continue;
                if (a.tier == b.tier) {
                    fuse(i, j, a, b);
                    fused = true;
                    break;
                }
                float overlap = sum - d;
                float massA = a.radius() * a.radius();
                float massB = b.radius() * b.radius();
                float total = massA + massB;
                // The collision is resolved partly by centre separation and
                // partly by actual changes to the two visible perimeters.
                float shareA = massB / total;
                float shareB = massA / total;
                float shiftA = overlap * shareA * .67f;
                float shiftB = overlap * shareB * .67f;
                a.squish(nx, ny, overlap * shareA * .30f);
                b.squish(-nx, -ny, overlap * shareB * .30f);
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
                a.bump(nx, ny, wobble * dt * 48f);
                b.bump(-nx, -ny, wobble * dt * 48f);
            }
        }
        for (Blob b : blobs) {
            confine(b);
        }
    }

    private void confine(Blob b) {
        // Each wall touches the same contoured surface used by the renderer.
        float leftExtent = b.outlineRadius((float) Math.PI, time);
        if (b.x - leftExtent < LEFT) {
            float overlap = LEFT - (b.x - leftExtent);
            b.squish(-1f, 0f, overlap * .35f);
            b.x = LEFT + b.outlineRadius((float) Math.PI, time);
            if (b.vx < 0) { b.bump(-1, 0, Math.abs(b.vx) * .9f); b.vx *= -.28f; }
        }
        float rightExtent = b.outlineRadius(0f, time);
        if (b.x + rightExtent > RIGHT) {
            float overlap = b.x + rightExtent - RIGHT;
            b.squish(1f, 0f, overlap * .35f);
            b.x = RIGHT - b.outlineRadius(0f, time);
            if (b.vx > 0) { b.bump(1, 0, Math.abs(b.vx) * .9f); b.vx *= -.28f; }
        }
        float topExtent = b.outlineRadius((float) (-Math.PI / 2), time);
        if (b.y - topExtent < TOP) {
            float overlap = TOP - (b.y - topExtent);
            b.squish(0f, -1f, overlap * .35f);
            b.y = TOP + b.outlineRadius((float) (-Math.PI / 2), time);
            if (b.vy < 0) { b.bump(0, -1, Math.abs(b.vy) * 1.2f); b.vy *= -.14f; }
        }
        float bottomExtent = b.outlineRadius((float) (Math.PI / 2), time);
        if (b.y + bottomExtent > bottom()) {
            float overlap = b.y + bottomExtent - bottom();
            b.squish(0f, 1f, overlap * .35f);
            b.y = bottom() - b.outlineRadius((float) (Math.PI / 2), time);
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
