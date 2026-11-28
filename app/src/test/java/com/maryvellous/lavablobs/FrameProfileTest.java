package com.maryvellous.lavablobs;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrameProfileTest {
    @Test public void bucketsAndStats(){
        assertEquals(0,FrameProfile.bucket(4));assertEquals(1,FrameProfile.bucket(5));
        assertEquals(2,FrameProfile.bucket(8));assertEquals(2,FrameProfile.bucket(10));
        assertEquals(3,FrameProfile.bucket(11));assertEquals(4,FrameProfile.bucket(16));
        FrameProfile p=new FrameProfile();
        p.frame(8,20_000_000,4_000_000,2_000_000,7_000_000,1);
        p.frame(10,30_000_000,6_000_000,4_000_000,12_000_000,2);
        p.save(9,10_000_000);
        String report=p.report();
        assertTrue(report.contains("blob=8-10 frames=2 fps=40.0 steps/s=60.0 gaps>25ms=1"));
        assertTrue(report.contains("physics/frame=5.00ms"));
        assertTrue(report.contains("saves=1 saveAvg=10.00ms"));
        assertFalse(report.contains("blob=0-4"));
    }
}
