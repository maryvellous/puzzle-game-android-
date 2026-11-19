package com.maryvellous.lavablobs;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

/** Checks game-space layout, not the approved membrane simulation. */
public class VesselLayoutTest {
    private static float height(Vessel v){return v.bottom-v.top;}

    @Test public void threeTestVariantsKeepShapeAndChangeOnlyHeight() {
        assertEquals(3,GameSpec.TRIALS.length);
        int[] expected={285,340,400};
        for(int i=0;i<3;i++) {
            GameSpec spec=GameSpec.TRIALS[i];
            assertTrue(spec.trial);
            assertFalse(spec.endless);
            Vessel v=new Vessel(spec.shape,930);
            assertEquals(expected[i],height(v),.001f);
            // Compare per-side X at all normalized perimeter samples.
            Vessel reference=new Vessel(GameSpec.TRIALS[1].shape,930);
            for(int j=0;j<v.xs.length;j++)assertEquals(reference.xs[j],v.xs[j],.0001f);
        }
    }

    @Test public void standardFirstLevelMatchesMiddleExperiment() {
        for(float screen:new float[]{780f,820f,930f,1200f}) {
            Vessel a=new Vessel(GameSpec.LEVELS[0].shape,screen);
            Vessel b=new Vessel(GameSpec.TRIALS[1].shape,screen);
            assertEquals(340f,height(a),.001f);
            assertEquals(height(a),height(b),.001f);
            assertEquals(a.top,b.top,.001f);
        }
    }

    @Test public void vesselDoesNotStretchOnTallPhones() {
        Vessel small=new Vessel(0,780),tall=new Vessel(0,1200);
        assertEquals(340f,height(small),.001f);
        assertEquals(340f,height(tall),.001f);
        // Geometry is positioned further down to centre vessel + launcher.
        assertTrue(tall.top>small.top+170f);
    }

    @Test public void panelFitsInsideVirtualSafeCanvasForAllShapes() {
        for(float h:new float[]{780f,850f,930f,1200f}) {
            for(int shape=0;shape<=13;shape++) {
                Vessel v=new Vessel(shape,h);
                float panelBottom=v.bottom+22f+100f;
                assertTrue("panel would be clipped in vessel "+shape,
                        panelBottom<=h-22f+.001f);
                assertTrue(v.top>=208f);
                assertTrue(v.bottom>v.top);
                assertTrue(v.fits(210,v.bottom-18f-7f,18f*.76f));
            }
        }
    }

    @Test public void oldPhysicsFixtureBoundsAreUnchanged() {
        Vessel legacy=new Vessel(-1,820f);
        assertEquals(155f,legacy.top,.001f);
        assertEquals(667f,legacy.bottom,.001f);
        assertEquals(29f,legacy.xs[legacy.xs.length-1],.001f);
    }

    @Test public void experimentNeverProgressesByScoreAsARegularLevel() {
        for(GameSpec trial:GameSpec.TRIALS){
            assertEquals(0,trial.totalExtras());
            assertEquals(0,trial.extrasMask(100000,14,300));
            assertTrue(trial.passed(0));
        }
        assertFalse(GameSpec.LEVELS[1].trial);
    }
}
