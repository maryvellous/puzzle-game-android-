package com.maryvellous.lavablobs;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShakeDetectorTest {
    private static final long SECOND = 1_000_000_000L;
    private static final long TICK = 20_000_000L;

    @Test public void stationaryGravityDoesNotShake() {
        ShakeDetector detector = new ShakeDetector();
        for (int i = 0; i < 40; i++) {
            assertNull(detector.sample(0f, 9.81f, 0f, SECOND + i * TICK));
        }
    }

    @Test public void strongSidewaysJerkTriggersOnceThenCooldown() {
        ShakeDetector detector = new ShakeDetector();
        assertNull(detector.sample(0f, 9.81f, 0f, SECOND));
        ShakeDetector.Impulse kick = detector.sample(9f, 9.81f, 0f, SECOND + TICK);
        assertNotNull(kick);
        assertTrue(kick.x > 5f);
        assertNull("Do not spam impulses on consecutive sensor samples",
                detector.sample(-9f, 9.81f, 0f, SECOND + 2 * TICK));
        assertNotNull("A later second jolt should move them again",
                detector.sample(-9f, 9.81f, 0f, SECOND + 13 * TICK));
    }

    @Test public void slowTiltIsFilteredOut() {
        ShakeDetector detector = new ShakeDetector();
        // ~2.5 m/s²/s: a deliberate gradual change of orientation.
        for (int i = 0; i < 60; i++) {
            float tilt = i / 59f * 2.5f;
            assertNull(detector.sample(tilt, 9.81f - tilt * .24f, 0f,
                    SECOND + i * TICK));
        }
    }

    @Test public void forwardBackwardJoltAlsoRegisters() {
        ShakeDetector detector = new ShakeDetector();
        assertNull(detector.sample(0, 9.81f, 0, SECOND));
        ShakeDetector.Impulse kick = detector.sample(0, 9.81f, 11f, SECOND + TICK);
        assertNotNull(kick);
        assertTrue(kick.z > 8f);
    }

    @Test public void resumingReseedsGravityInsteadOfFiringFalsePositive() {
        ShakeDetector detector = new ShakeDetector();
        detector.sample(0, 9.81f, 0, SECOND);
        detector.reset();
        assertNull(detector.sample(8, 1, 0, SECOND + TICK));
    }
}
