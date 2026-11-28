package com.maryvellous.lavablobs;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Random;
public class NewUiGeometryTest {
    @Test public void allNewStoryVesselsAdmitEveryDrop(){
        for(GameSpec s:GameSpec.LEVELS)for(float h:new float[]{780,850,930,1200}){
            BlobWorld w=new BlobWorld(new Random(9),s);w.setHeight(h);
            assertTrue(s.name,w.vessel.contains(210,(w.vessel.top+w.bottom())*.5f));
            for(int tier=0;tier<5;tier++){w.nextTier=tier;assertTrue(s.name+" tier "+tier,w.canLaunchAnywhere());}
            assertEquals(162,w.vessel.xs.length);
        }
    }
    @Test public void roundedCapsHaveRealCurvedShoulders(){
        for(GameSpec s:GameSpec.LEVELS){
            Vessel v=new Vessel(s.shape,850);
            int n=v.segments;
            assertEquals(80,n);
            assertTrue(s.name,v.xs[3]>v.xs[0]+12);
            assertTrue(s.name,v.xs[n-3]>v.xs[n]+12);
            assertTrue(s.name,v.xs[n+1+3]<v.xs[n+1]-12);
            assertTrue(s.name,v.xs[2*n-2]<v.xs[2*n+1]-12);
            for(int i=0;i<n;i++){
                assertTrue(v.ys[i+1]>v.ys[i]);
                assertTrue(v.xs[i]>v.xs[v.xs.length-1-i]);
            }
        }
    }
    @Test public void snappingNeverCrossesBounds(){
        assertEquals(0,HomeCarousel.target(0,0,-100,120,12));
        assertEquals(11,HomeCarousel.target(11,11,100,120,12));
        assertEquals(4,HomeCarousel.target(3,3.22f,30,120,12));
        assertEquals(2,HomeCarousel.target(3,2.78f,-30,120,12));
    }
}
