package com.maryvellous.lavablobs;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure JVM checks for the audio-test mapping from existing soft-body data. */
public final class AudioTestModelTest {

    @Test public void frictionNeedsTangentialRelativeMotion() {
        assertEquals(0f,AudioTestModel.frictionIntensity(0f),0.00001f);
        assertEquals(0f,AudioTestModel.frictionIntensity(13f),0.00001f);
        assertTrue(AudioTestModel.frictionIntensity(150f)>.9f);
    }

    @Test public void constantDeformationIsSilence() {
        assertEquals(0f,AudioTestModel.compressionIntensity(.27f,.27f,1f/60f),0.00001f);
        assertTrue(AudioTestModel.compressionIntensity(.25f,.29f,1f/60f)>.5f);
    }

    @Test public void localSlipUsesActualMembraneVelocities() {
        Blob a=new Blob(190,300,1,1f);
        Blob b=new Blob(235,300,2,1f);
        BlobWorld.Adhesion contact=new BlobWorld.Adhesion(a,b,0,12);
        assertEquals(0f,AudioTestModel.slip(contact),1e-5f);
        a.vy[0]=95f;b.vy[12]=-95f;
        assertTrue(AudioTestModel.slip(contact)>180f);
    }

    @Test public void geometricCompressionSamplesRealMembrane() {
        Blob body=new Blob(160,300,2,1f);
        float before=AudioTestModel.compression(body);
        body.px[0]-=12f;
        body.recenter();
        assertTrue(AudioTestModel.compression(body)>before+.02f);
    }

    @Test public void impactRespectsSpeedAndLimit() {
        assertEquals(0f,AudioTestModel.impactIntensity(42f),0.0001f);
        assertTrue(AudioTestModel.impactIntensity(180f)>.4f);
        assertEquals(1f,AudioTestModel.impactIntensity(999f),0.0001f);
    }
}
