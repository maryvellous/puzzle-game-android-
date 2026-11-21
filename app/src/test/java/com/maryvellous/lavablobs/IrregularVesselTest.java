package com.maryvellous.lavablobs;

import org.junit.Test;
import static org.junit.Assert.*;

public class IrregularVesselTest {
    @Test public void shapeProfilesHaveVisibleNecksAndAreStillPlayable() {
        for(int kind=1;kind<=10;kind++){
            Vessel v=new Vessel(kind,880f);
            assertTrue(v.contains(210,(v.top+v.bottom)*.5f));
            assertTrue("No initial spawn "+kind,
                    v.fits(210,v.bottom-25f,18f*.76f));
            float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
            for(int i=0;i<=24;i++){
                float half=(v.xs[i]-v.xs[49-i])*.5f;
                min=Math.min(min,half);max=Math.max(max,half);
            }
            assertTrue("Container "+kind+" should have visually distinct lobes",
                    max-min>20f);
        }
    }
    @Test public void samePathForPhysicsAndDrawing() {
        Vessel v=new Vessel(3,850f);
        assertFalse(v.contains(30,390));
        assertTrue(v.contains(210,390));
        assertFalse(v.fits(210,v.top+3,18f));
    }
}
