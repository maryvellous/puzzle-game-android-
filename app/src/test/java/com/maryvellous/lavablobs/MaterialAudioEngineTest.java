package com.maryvellous.lavablobs;

import com.maryvellous.lavablobs.audio.BloopMaterialBank;
import com.maryvellous.lavablobs.audio.MaterialAudioEngine;
import org.junit.Test;
import static org.junit.Assert.*;

/** Tests the SAME platform-neutral sample engine intended for Android. */
public class MaterialAudioEngineTest {
    private static BloopMaterialBank bank(boolean silentParts) {
        BloopMaterialBank.Material[] material=new BloopMaterialBank.Material[4];
        for(int m=0;m<4;m++) {
            float[] complete=new float[16000],attack=new float[2000],tail=new float[3300];
            for(int i=0;i<complete.length;i++)
                complete[i]=(float)(.55*Math.sin(2*Math.PI*(145+m*26)*i/44100.));
            if(!silentParts) {
                for(int i=0;i<attack.length;i++)
                    attack[i]=(float)(.24*Math.sin(2*Math.PI*(280+m*17)*i/44100.)*
                            Math.sin(Math.PI*i/(attack.length-1)));
                for(int i=0;i<tail.length;i++)
                    tail[i]=(float)(.20*Math.sin(2*Math.PI*(150+m*19)*i/44100.)*
                            Math.sin(Math.PI*i/(tail.length-1)));
            }
            material[m]=new BloopMaterialBank.Material(complete,attack,tail);
        }
        return new BloopMaterialBank(material);
    }

    @Test public void completeFusionsFinishEvenUnderOverlappingBursts() {
        MaterialAudioEngine engine=new MaterialAudioEngine(bank(false));
        float[] rendered=new float[44100*3];
        int at=0;
        for(int i=0;i<20;i++) {
            int target=i*2600;
            engine.renderMono(rendered,at,target-at);
            at=target;
            engine.merge(i*2+1,i*2+2,20+i*3f);
        }
        engine.renderMono(rendered,at,rendered.length-at);
        assertEquals(20,engine.fusionsStarted());
        assertEquals("No fusion may be stolen/truncated",20,engine.fusionsCompleted());
        assertEquals(0,engine.activeOneShots());
        assertTrue(engine.peakOutput()<=.87001f);
        for(float sample:rendered)assertTrue(Float.isFinite(sample));
    }

    @Test public void onlyFusionCanProduceWholeBloopWhenAllPartialsAreSilent() {
        MaterialAudioEngine engine=new MaterialAudioEngine(bank(true));
        float[] rendered=new float[44100];
        engine.contact(1,2,300f,150f,.4f,28f,36f);
        engine.launch(32);
        engine.pop(20,45);
        engine.renderMono(rendered,0,rendered.length);
        for(float s:rendered)assertEquals("No original full material from a non-fusion",0f,s,0f);
        assertEquals(0,engine.fusionsStarted());
        engine.merge(1,2,32);
        engine.renderMono(rendered,0,rendered.length);
        float peak=0;
        for(float s:rendered)peak=Math.max(peak,Math.abs(s));
        assertTrue("Complete original Bloop must now be present",peak>.1f);
        assertEquals(1,engine.fusionsCompleted());
    }

    @Test public void stationaryTouchWithPhysicsJitterIsExactlySilentForTenSeconds() {
        MaterialAudioEngine engine=new MaterialAudioEngine(bank(false));
        float[] frame=new float[368];
        float highest=0f;
        // Mimics a crowded top-of-vessel scene with contact but tiny
        // oscillations; pressure is NONZERO, not a fake "no contact" case.
        for(int tick=0;tick<1200;tick++) {
            for(int pair=1;pair<=8;pair++) {
                float slide=3f+(tick%7)*3f;       // 3–21 units/s
                float pressure=.16f+(tick%5-2)*.002f;
                engine.contact(pair,pair+30,12f,slide,pressure,
                        25f+pair,31f+pair,1f/120f);
            }
            engine.renderMono(frame,0,frame.length);
            for(float sample:frame)highest=Math.max(highest,Math.abs(sample));
        }
        assertEquals("Ten seconds of nearly stationary touching must be SILENT",
                0f,highest,0f);
        assertEquals("Not one squish may be triggered by solver micro-noise",
                0,engine.squishEvents());
        assertEquals(0,engine.impactEvents());
        assertEquals(8,engine.activePairs());
    }

    @Test public void genuineShakeMakesFiniteSquishesThenReturnsToExactSilence() {
        MaterialAudioEngine engine=new MaterialAudioEngine(bank(false));
        float[] frame=new float[368];
        engine.contact(11,15,0,0,.5f,26f,40f,1f/120f);
        engine.renderMono(frame,0,frame.length);
        // Substantial relative motion / shape deformation, for ~400ms.
        float movingPeak=0f;
        for(int tick=0;tick<48;tick++){
            float pressure=.5f+(tick%4)*.028f;
            engine.contact(11,15,0,190f,pressure,26f,40f,1f/120f);
            engine.renderMono(frame,0,frame.length);
            for(float sample:frame)movingPeak=Math.max(movingPeak,Math.abs(sample));
        }
        assertTrue("Actual movement must excite a physical tail gesture",movingPeak>.003f);
        assertTrue("At least one finite squish must be emitted",engine.squishEvents()>0);
        assertTrue("A 400ms motion must not generate an uncontrolled storm",
                engine.squishEvents()<=5);
        assertEquals(1,engine.contactBegins());

        // The blobs REMAIN touching, but stop sliding/deforming.
        for(int tick=0;tick<90;tick++){
            engine.contact(11,15,0,3f,.5f,26f,40f,1f/120f);
            engine.renderMono(frame,0,frame.length);
        }
        assertEquals("Touching is still tracked",1,engine.activePairs());
        assertEquals("Finite sounds have natural ends even while still touching",
                0,engine.activeOneShots());
        float restPeak=0f;
        for(int tick=0;tick<100;tick++){
            engine.contact(11,15,0,2f,.5f,26f,40f,1f/120f);
            engine.renderMono(frame,0,frame.length);
            for(float sample:frame)restPeak=Math.max(restPeak,Math.abs(sample));
        }
        assertEquals("No continuous tail noise after movement stops",0f,restPeak,0f);
        engine.endContact(11,15);
        assertEquals(0,engine.activePairs());
    }

    @Test public void transientSquishIntensityAndPitchFollowMotionAndSize() {
        MaterialAudioEngine slow=new MaterialAudioEngine(bank(false));
        MaterialAudioEngine fast=new MaterialAudioEngine(bank(false));
        float[] s=new float[368],f=new float[368];
        for(int tick=0;tick<120;tick++){
            slow.contact(1,2,0,54f,.10f,32,32,1f/120f);
            fast.contact(1,2,0,220f,.10f,64,64,1f/120f);
            slow.renderMono(s,0,s.length);
            fast.renderMono(f,0,f.length);
        }
        assertTrue("High-energy motion emits more squish events",
                fast.squishEvents()>slow.squishEvents());
        assertTrue(MaterialAudioEngine.pitchForRadius(22f)>
                MaterialAudioEngine.pitchForRadius(65f));
        assertEquals(0,slow.impactEvents());
        assertEquals(0,fast.impactEvents());
    }

    @Test public void contactUpdatesDoNotRetriggerImpactsAndUnknownEndsAreSafe() {
        MaterialAudioEngine engine=new MaterialAudioEngine(bank(false));
        engine.contact(2,7,210,0,.1f,32,32);
        int before=engine.activeOneShots();
        assertEquals(1,before);
        for(int i=0;i<10;i++)engine.contact(7,2,250,0,.1f,32,32);
        assertEquals("Repeated solver steps must not retrigger impacts",
                before,engine.activeOneShots());
        assertEquals(1,engine.contactBegins());
        engine.endContact(8,9);
        engine.cancelBlob(7);
        float[] sound=new float[44100];
        engine.renderMono(sound,0,sound.length);
        assertEquals(0,engine.activePairs());
    }

    @Test public void shortDelayedPhysicalImpactStillTriggersExactlyOnce() {
        MaterialAudioEngine engine=new MaterialAudioEngine(bank(false));
        float[] block=new float[441]; // 10ms
        engine.contact(4,9,4,25,.1f,32,32);
        assertEquals(0,engine.activeOneShots());
        engine.renderMono(block,0,block.length);
        engine.contact(4,9,145,40,.1f,32,32);
        assertEquals("The first real closing impulse must be captured",1,engine.activeOneShots());
        for(int i=0;i<9;i++){
            engine.renderMono(block,0,block.length);
            engine.contact(4,9,175,40,.1f,32,32);
        }
        assertEquals("No retrigger even with repeated high speed",1,engine.contactBegins());
        // At this point the first attack may have played out, so count the
        // input onset via the largest possible active-voice count instead.
        assertTrue(engine.peakVoices()<=1);
    }

    @Test public void tooManyPartialVoicesAreLimitedWithoutSacrificingFusions() {
        MaterialAudioEngine engine=new MaterialAudioEngine(bank(false));
        for(int i=0;i<75;i++)engine.launch(20+i%5);
        assertEquals(24,engine.activeOneShots());
        assertTrue(engine.rejectedShortVoices()>0);
        for(int i=0;i<12;i++)engine.merge(i+1,i+25,30);
        float[] out=new float[44100];
        engine.renderMono(out,0,out.length);
        assertEquals(12,engine.fusionsCompleted());
        assertTrue(engine.peakOutput()<=.87001f);
    }

    @Test public void invalidSourceOrEventsAreRejected() {
        try {
            new BloopMaterialBank(new BloopMaterialBank.Material[4]);
            fail("Null original Bloops must fail");
        }catch(IllegalArgumentException expected){}
        MaterialAudioEngine engine=new MaterialAudioEngine(bank(false));
        try {
            engine.contact(1,1,5,1,0,20,20);
            fail("Self contact is invalid");
        }catch(IllegalArgumentException expected){}
        try {
            engine.contact(1,2,Float.NaN,1,0,20,20);
            fail("Nonfinite physics control must fail");
        }catch(IllegalArgumentException expected){}
        assertTrue(MaterialAudioEngine.pitchForRadius(20)>
                MaterialAudioEngine.pitchForRadius(80));
        engine.merge(1,2,32);
        engine.reset();
        assertEquals(0,engine.activeOneShots());
        assertEquals(0,engine.fusionsStarted());
    }
}
