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
            assertEquals(50,w.vessel.xs.length);
        }
    }
    @Test public void snappingNeverCrossesBounds(){
        assertEquals(0,HomeCarousel.target(0,0,-100,120,12));
        assertEquals(11,HomeCarousel.target(11,11,100,120,12));
        assertEquals(4,HomeCarousel.target(3,3.22f,30,120,12));
        assertEquals(2,HomeCarousel.target(3,2.78f,-30,120,12));
    }
}
