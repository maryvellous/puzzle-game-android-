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

    @Test public void smallerPairsAreHigherAndQuicker() {
        float small=AudioTestModel.pairRadius(20f,26f);
        float medium=AudioTestModel.pairRadius(32f,40f);
        float large=AudioTestModel.pairRadius(77f,94f);
        assertTrue(small<medium);
        assertTrue(medium<large);
        float smallRate=AudioTestModel.sizePlaybackRate(small);
        float mediumRate=AudioTestModel.sizePlaybackRate(medium);
        float largeRate=AudioTestModel.sizePlaybackRate(large);
        assertTrue("Small blobs must sound clearly higher",smallRate>1.10f);
        assertTrue("Large blobs must sound clearly slower",largeRate<.70f);
        assertTrue("Pitch separation should be audible",smallRate/largeRate>1.65f);
        assertTrue(smallRate>mediumRate);
        assertTrue(mediumRate>largeRate);
    }

    @Test public void effectiveRadiusModelsVolumeNotTierAverage() {
        assertEquals(20f,AudioTestModel.pairRadius(20f,20f),.0001f);
        assertEquals(94f,AudioTestModel.pairRadius(94f,94f),.0001f);
        // For mixed sizes, cubic volume weighting is closer to the large
        // blob than the simple radius average.
        assertTrue(AudioTestModel.pairRadius(20f,94f)>(20f+94f)*.5f);
        assertEquals(AudioTestModel.pairRadius(26f,77f),
                AudioTestModel.pairRadius(77f,26f),.0001f);
    }

    @Test public void playbackRateRespectsSoundPoolBounds() {
        assertTrue(AudioTestModel.sizePlaybackRate(10f)<=1.34f);
        assertTrue(AudioTestModel.sizePlaybackRate(500f)>=.56f);
        assertEquals(1f,AudioTestModel.sizePlaybackRate(Blob.RADII[2]),.0001f);
    }

    @Test public void pitchFollowsNewContactsSmoothly() {
        float previous=1.18f,target=.62f;
        float next=AudioTestModel.smoothPlaybackRate(previous,target,1f/60f);
        assertTrue(next<previous);
        assertTrue(next>target);
        assertEquals(previous,AudioTestModel.smoothPlaybackRate(previous,target,0f),1e-6f);
        float repeated=next;
        for(int i=0;i<30;i++)
            repeated=AudioTestModel.smoothPlaybackRate(repeated,target,1f/60f);
        assertEquals(target,repeated,.005f);
    }

    @Test public void impactEventPreservesTwoSourceRadii() {
        BlobWorld.Impact collision=new BlobWorld.Impact(4,150f,20f,94f);
        assertEquals(20f,collision.radiusA,.0001f);
        assertEquals(94f,collision.radiusB,.0001f);
    }

    @Test public void microContactsAreSilentInNewMixWithoutChangingPhysics() {
        assertFalse(AudioTestModel.isAudibleImpact(42f));
        assertFalse(AudioTestModel.isAudibleImpact(85f));
        assertFalse(AudioTestModel.isAudibleImpact(117.9f));
        assertTrue(AudioTestModel.isAudibleImpact(118f));
        assertTrue(AudioTestModel.isAudibleImpact(292f));
        assertEquals(0f,AudioTestModel.audibleImpactIntensity(118f),1e-6f);
        assertTrue(AudioTestModel.audibleImpactIntensity(200f)>.3f);
        assertEquals(1f,AudioTestModel.audibleImpactIntensity(1000f),1e-6f);
    }

    @Test public void frictionIsAudibleForRealSlidingButSilentAtRest() {
        assertEquals(0f,AudioTestModel.audibleFrictionIntensity(0f),1e-6f);
        assertEquals(0f,AudioTestModel.audibleFrictionIntensity(8f),1e-6f);
        assertTrue(AudioTestModel.audibleFrictionIntensity(45f)>.45f);
        assertTrue(AudioTestModel.audibleFrictionIntensity(85f)>.8f);
        assertEquals(1f,AudioTestModel.audibleFrictionIntensity(180f),1e-6f);
    }

    @Test public void onlyOneContinuousVoiceHasPriority() {
        assertTrue(AudioTestModel.shouldPreferFriction(.15f,.12f));
        assertTrue(AudioTestModel.shouldPreferFriction(.04f,.24f));
        assertFalse(AudioTestModel.shouldPreferFriction(0f,.15f));
        assertFalse(AudioTestModel.shouldPreferFriction(.005f,.15f));
        // Gentle adhesive slip is not interrupted when compression is quieter.
        assertTrue(AudioTestModel.shouldPreferFriction(.025f,.03f));
    }
}
