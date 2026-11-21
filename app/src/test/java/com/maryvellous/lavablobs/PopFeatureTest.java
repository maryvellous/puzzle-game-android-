package com.maryvellous.lavablobs;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

/** POP retains the old consumable semantics while supplying only cosmetic data. */
public class PopFeatureTest {
    @Test public void popOnlyWorksInEndlessAndCapturesCorrectVisualPayload() {
        BlobWorld endless=new BlobWorld(new Random(1),GameSpec.ENDLESS[0]);
        endless.eraseCount=1;
        Blob b=new Blob(210f,440f,2,new Random(2));
        endless.blobs.add(b);
        float expectedRadius=b.radius();
        assertTrue(endless.eraseAt(b.x,b.y));
        assertEquals(0,endless.blobs.size());
        assertEquals(0,endless.eraseCount);
        assertEquals(1,endless.usedErases);
        assertEquals(2,endless.lastPoppedTier);
        assertEquals(b.x,endless.lastPoppedX,0.001f);
        assertEquals(b.y,endless.lastPoppedY,0.001f);
        assertEquals(expectedRadius,endless.lastPoppedRadius,0.001f);

        BlobWorld level=new BlobWorld(new Random(3),GameSpec.LEVELS[0]);
        level.eraseCount=1;
        level.blobs.add(new Blob(210f,440f,2,new Random(4)));
        assertFalse(level.eraseAt(210f,440f));
        assertEquals(1,level.blobs.size());
        assertEquals(1,level.eraseCount);
    }

    @Test public void noPopWithoutConsumable() {
        BlobWorld endless=new BlobWorld(new Random(1),GameSpec.ENDLESS[0]);
        endless.blobs.add(new Blob(210f,440f,0,new Random(2)));
        assertFalse(endless.eraseAt(210f,440f));
        assertEquals(1,endless.blobs.size());
    }

    @Test public void alternatingShakeImpulsesPreservePositiveMembraneArea() {
        BlobWorld world=new BlobWorld(new Random(6),GameSpec.ENDLESS[0]);
        Blob blob=new Blob(180f,425f,2,new Random(9));
        world.blobs.add(blob);
        world.shake(8f,-5f,8f);
        world.shake(-8f,5f,8f);
        world.shake(7f,-4.5f,8f);
        for(int i=0;i<15;i++)world.tick(.016f);
        assertTrue(blob.area()>20f);
        for(int i=0;i<Blob.NODES;i++){
            assertTrue(Float.isFinite(blob.px[i]));
            assertTrue(Float.isFinite(blob.py[i]));
        }
    }
}
