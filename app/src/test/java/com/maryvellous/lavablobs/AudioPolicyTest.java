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
        assertFalse(AudioPolicy.shouldPlayImpact(8f));
        assertFalse(AudioPolicy.shouldPlayImpact(19.9f));
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
    @Test public void editedImpactHasLessLowFrequencyBodyThanSource() {
        byte[] source=new byte[4096];
        for(int i=0;i<source.length;i+=2) {
            int sample=(i/2)%64<32?12000:-12000;
            source[i]=(byte)(sample&255);
            source[i+1]=(byte)((sample>>8)&255);
        }
        byte[] soft=source.clone();
        AudioPolicy.softenImpactPcm16(soft);
        assertFalse(java.util.Arrays.equals(source,soft));
        assertTrue(AudioPolicy.usableMonoPcm16(soft,0,soft.length));
        long origTotal=0,softTotal=0;
        for(int i=0;i<source.length;i+=2) {
            origTotal+=Math.abs((short)((source[i]&255)|((source[i+1]&255)<<8)));
            softTotal+=Math.abs((short)((soft[i]&255)|((soft[i+1]&255)<<8)));
        }
        assertTrue("Softening must reduce energy and sound less like a full merge",softTotal<origTotal);
    }
    @Test public void largeCollisionIsLighter() {
        assertTrue(AudioPolicy.impactRate(1)>AudioPolicy.impactRate(8));
        assertTrue(AudioPolicy.impactRate(30)>=1.02f);
    }
    @Test public void collisionsStaySubtle() {
        assertTrue(AudioPolicy.impactVolume(90f)>.095f);
        assertTrue(AudioPolicy.impactVolume(500f)<=.18f);
        assertTrue(AudioPolicy.impactVolume(200f)>AudioPolicy.impactVolume(80f));
        assertTrue(AudioPolicy.impactVolume(200f)<.56f);
    }
}
