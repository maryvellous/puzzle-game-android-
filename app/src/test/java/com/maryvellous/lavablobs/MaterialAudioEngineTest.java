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

    @Test public void movementExcitesTailWhileStaticPressureCanRemainSilent() {
        MaterialAudioEngine engine=new MaterialAudioEngine(bank(false));
        float[] rendered=new float[44100/4];
        engine.contact(11,15,0,0,.5f,26f,40f);
        engine.renderMono(rendered,0,rendered.length);
        float quiet=0;
        for(float v:rendered)quiet=Math.max(quiet,Math.abs(v));
        assertEquals(0f,quiet,0f);
        engine.contact(11,15,0,160f,.52f,26f,40f);
        engine.renderMono(rendered,0,rendered.length);
        float moving=0;
        for(float v:rendered)moving=Math.max(moving,Math.abs(v));
        assertTrue("Tangential motion should audibly excite Bloop tail",moving>.002f);
        assertEquals(1,engine.contactBegins());
        engine.endContact(11,15);
        engine.renderMono(rendered,0,rendered.length);
        assertEquals("The release must fade, not cut a contact voice immediately",
                1,engine.activePairs());
        float[] tail=new float[44100];
        engine.renderMono(tail,0,tail.length);
        assertEquals("Released texture should eventually disappear",0,engine.activePairs());
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
