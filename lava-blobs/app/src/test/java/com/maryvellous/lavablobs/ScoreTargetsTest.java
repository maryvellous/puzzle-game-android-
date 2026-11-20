package com.maryvellous.lavablobs;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScoreTargetsTest {
    @Test public void fialaAndOndeHaveApprovedTargets() {
        assertEquals(2200,GameSpec.LEVELS[4].minScore);
        assertEquals(2000,GameSpec.LEVELS[5].minScore);
    }
    @Test public void goalsAreNotForcedToRise() {
        assertTrue(GameSpec.LEVELS[4].minScore>GameSpec.LEVELS[5].minScore);
        for(GameSpec level:GameSpec.LEVELS)assertTrue(
                "Extra must exceed minimum for "+level.name,level.extraScore>level.minScore);
    }
    @Test public void scoringFormulaUnchanged() {
        assertEquals(10,GameSpec.pointsForMerge(0));
        assertEquals(16,GameSpec.pointsForMerge(1));
        assertEquals(26,GameSpec.pointsForMerge(2));
    }
}