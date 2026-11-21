package com.maryvellous.lavablobs;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioPolicyTest {
    @Test public void mergesSlowOnlySlightly() {
        assertEquals(1f, AudioPolicy.mergeRate(1),.00001f);
        assertTrue(AudioPolicy.mergeRate(5)<AudioPolicy.mergeRate(2));
        assertEquals(.92f, AudioPolicy.mergeRate(100),.00001f);
    }
    @Test public void gentleApproachingContactsCanMakeSound() {
        assertFalse(AudioPolicy.shouldPlayImpact(-5f));
        assertFalse(AudioPolicy.shouldPlayImpact(0f));
        assertFalse(AudioPolicy.shouldPlayImpact(7.9f));
        assertTrue(AudioPolicy.shouldPlayImpact(8f));
        assertTrue(AudioPolicy.shouldPlayImpact(20f));
        assertTrue(AudioPolicy.shouldPlayImpact(42f));
    }
    @Test public void impactSelectsEditedClipAndFallsBackToBundledSource() {
        assertEquals(12,AudioPolicy.impactSample(12,true,2,true));
        assertEquals(2,AudioPolicy.impactSample(12,false,2,true));
        assertEquals(2,AudioPolicy.impactSample(0,false,2,true));
        assertEquals(0,AudioPolicy.impactSample(12,false,2,false));
    }
    @Test public void silentOrDamagedImpactSampleIsRejected() {
        byte[] silent=new byte[2048];
        assertFalse(AudioPolicy.usableMonoPcm16(silent,0,silent.length));
        byte[] signal=new byte[2048];
        for(int i=0;i<signal.length;i+=2) {signal[i]=(byte)0x00;signal[i+1]=(byte)0x10;}
        assertTrue(AudioPolicy.usableMonoPcm16(signal,0,signal.length));
        assertFalse(AudioPolicy.usableMonoPcm16(signal,4,2048));
    }
    @Test public void largeCollisionIsLower() {
        assertTrue(AudioPolicy.impactRate(1)>AudioPolicy.impactRate(8));
        assertTrue(AudioPolicy.impactRate(30)>=.79f);
    }
    @Test public void collisionsStaySubtle() {
        assertTrue(AudioPolicy.impactVolume(90f)>.15f);
        assertTrue(AudioPolicy.impactVolume(500f)<=.32f);
        assertTrue(AudioPolicy.impactVolume(200f)>AudioPolicy.impactVolume(80f));
    }
}
