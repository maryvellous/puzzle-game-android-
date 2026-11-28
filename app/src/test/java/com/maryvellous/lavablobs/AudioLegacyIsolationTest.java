package com.maryvellous.lavablobs;

import com.maryvellous.lavablobs.audio.AudioEvent;
import com.maryvellous.lavablobs.audio.AudioTrace;
import org.junit.Test;
import java.io.StringWriter;
import java.util.*;
import static org.junit.Assert.*;

public class AudioLegacyIsolationTest {
    private static BlobWorld fixture(boolean removeLegacy,List<AudioEvent> events){
        BlobWorld w=new BlobWorld(new Random(1203));
        w.setHeight(850);
        if(removeLegacy)w.disableLegacyImpactCollection();
        w.setContactProbe(new PhysicsAudioEvents(events::add,()->1,()->0,()->{}));
        for(int row=0;row<3;row++)for(int col=0;col<4;col++){
            Blob b=new Blob(95+75*col,270+60*row,1+col,new Random(3));
            for(int n=0;n<Blob.NODES;n++){
                b.vx[n]=(col%2==0?1:-1)*35;
                b.vy[n]=(row%2==0?1:-1)*22;
            }
            w.blobs.add(b);
        }
        w.ensureBlobIds();return w;
    }
    @Test public void disablingLegacyHasNoEffectOnSolverOrMaterialEvents()throws Exception{
        List<AudioEvent> baseline=new ArrayList<>(),candidate=new ArrayList<>();
        BlobWorld a=fixture(false,baseline),b=fixture(true,candidate);
        for(int frame=0;frame<120;frame++){
            if(frame%20==0){a.shake(4,-2,3);b.shake(4,-2,3);}
            a.tick(1f/60f);b.tick(1f/60f);
            assertEquals(a.score,b.score);assertEquals(a.blobs.size(),b.blobs.size());
            for(int i=0;i<a.blobs.size();i++){
                Blob x=a.blobs.get(i),y=b.blobs.get(i);
                assertEquals(x.id,y.id);assertArrayEquals(x.px,y.px,0f);
                assertArrayEquals(x.py,y.py,0f);assertArrayEquals(x.vx,y.vx,0f);
                assertArrayEquals(x.vy,y.vy,0f);
            }
            assertTrue(b.consumeImpacts().isEmpty());
            a.consumeImpacts();
        }
        assertTrue(baseline.size()>12);
        StringWriter left=new StringWriter(),right=new StringWriter();
        AudioTrace.write(left,baseline);AudioTrace.write(right,candidate);
        assertEquals(left.toString(),right.toString());
    }
}
