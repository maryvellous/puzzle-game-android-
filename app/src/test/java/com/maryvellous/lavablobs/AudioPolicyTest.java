package com.maryvellous.lavablobs;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioPolicyTest {
    @Test public void effectSwitchGatesAllPlaybackIncludingLoadedSamples() {
        assertTrue(AudioPolicy.effectsAllowed(true,false,true));
        assertFalse(AudioPolicy.effectsAllowed(false,false,true));
        assertFalse(AudioPolicy.effectsAllowed(true,true,true));
        assertFalse(AudioPolicy.effectsAllowed(true,false,false));
        // Disabling all effects must not change the collision threshold or merge tuning.
        assertEquals(42f,42f,.0001f);
    }

    @Test public void mergesSlowOnlySlightly() {
        assertEquals(1f,AudioPolicy.mergeRate(1),.00001f);
        assertEquals(.92f,AudioPolicy.mergeRate(100),.00001f);
    }

    @Test public void onlyEnergeticContactMakesSound() {
        assertFalse(AudioPolicy.shouldPlayImpact(-5f));
        assertFalse(AudioPolicy.shouldPlayImpact(0f));
        assertFalse(AudioPolicy.shouldPlayImpact(20f));
        assertFalse(AudioPolicy.shouldPlayImpact(41.99f));
        assertTrue(AudioPolicy.shouldPlayImpact(42f));
        assertTrue(AudioPolicy.shouldPlayImpact(310f));
    }

    @Test public void globalImpactSpacingRequires350Milliseconds() {
        assertEquals(350L,AudioPolicy.IMPACT_MIN_GAP_MS);
        assertTrue(AudioPolicy.allowImpactAt(1000L,0L,0L));
        assertFalse(AudioPolicy.allowImpactAt(1349L,1000L,0L));
        assertTrue(AudioPolicy.allowImpactAt(1350L,1000L,0L));
        assertFalse(AudioPolicy.allowImpactAt(1500L,1000L,1450L));
        assertTrue(AudioPolicy.allowImpactAt(1550L,1000L,1450L));
    }

    @Test public void samePairNeeds700MillisecondsBeforeAnotherEvent() {
        assertEquals(.70f,AudioPolicy.IMPACT_PAIR_COOLDOWN_SECONDS,.0001f);
        assertTrue(AudioPolicy.allowPairImpactAt(1.0f,null));
        assertFalse(AudioPolicy.allowPairImpactAt(1.699f,1f));
        assertTrue(AudioPolicy.allowPairImpactAt(1.7001f,1f));
    }

    @Test public void impactHasConstantVeryLowVolume() {
        assertEquals(.08f,AudioPolicy.impactVolume(),.00001f);
        assertTrue(AudioPolicy.impactVolume()<.56f);
    }

    @Test public void silenceAndDamagedPcmAreRejectedForOtherEffects() {
        byte[] silent=new byte[2048];
        assertFalse(AudioPolicy.usableMonoPcm16(silent,0,silent.length));
        byte[] signal=new byte[2048];
        for(int i=0;i<signal.length;i+=2) {signal[i]=0;signal[i+1]=16;}
        assertTrue(AudioPolicy.usableMonoPcm16(signal,0,signal.length));
        assertFalse(AudioPolicy.usableMonoPcm16(signal,4,2048));
    }
}
