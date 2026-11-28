package com.maryvellous.lavablobs.audio;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioSessionTest {
    private AudioEvent event(AudioEvent.Type type,int gen){
        return new AudioEvent(type,1,2,100,0,0,32,32,1f/120f,0,0,gen,0);
    }
    @Test public void startupAndRapidMuteResumeAreExplicit(){
        AudioEventQueue<AudioEvent> queue=new AudioEventQueue<>(8,2);
        AudioSession s=new AudioSession(queue,100);
        assertEquals(AudioSession.State.STARTING,s.snapshot().state);
        assertEquals(AudioSession.Admission.STARTUP,s.submit(event(AudioEvent.Type.MERGE,0)));
        s.ready();assertTrue(s.snapshot().running());
        assertEquals(AudioSession.Admission.ACCEPTED,s.submit(event(AudioEvent.Type.MERGE,0)));
        s.volume(0);assertEquals(AudioSession.State.MUTED,s.snapshot().state);
        s.volume(100);assertTrue(s.snapshot().running());assertEquals(1,s.snapshot().generation);
        assertEquals(0,queue.size());
        assertEquals(AudioSession.Admission.STALE,s.submit(event(AudioEvent.Type.MERGE,0)));
        assertEquals(AudioSession.Admission.ACCEPTED,s.submit(event(AudioEvent.Type.MERGE,1)));
        s.pause();s.pause();assertEquals(2,s.snapshot().generation);
        s.resume();assertTrue(s.snapshot().running());assertEquals(0,queue.size());
    }
    @Test public void overflowIsExplicitAndRecoverableByReset(){
        AudioEventQueue<AudioEvent> q=new AudioEventQueue<>(3,1);
        AudioSession s=new AudioSession(q,100);s.ready();
        assertEquals(AudioSession.Admission.ACCEPTED,s.submit(event(AudioEvent.Type.CONTACT,0)));
        assertEquals(AudioSession.Admission.ACCEPTED,s.submit(event(AudioEvent.Type.CONTACT,0)));
        assertEquals(AudioSession.Admission.OBSERVATION_REJECTED,s.submit(event(AudioEvent.Type.CONTACT,0)));
        assertEquals(AudioSession.Admission.ACCEPTED,s.submit(event(AudioEvent.Type.MERGE,0)));
        assertEquals(AudioSession.Admission.CONTROL_OVERFLOW,s.submit(event(AudioEvent.Type.END,0)));
        assertEquals(AudioSession.State.OVERFLOW,s.snapshot().state);assertEquals(0,q.size());
        s.reset();assertTrue(s.snapshot().running());assertEquals(2,s.snapshot().generation);
    }
    @Test public void pauseBeforeInitializationAndFailureCannotBeRevived()throws Exception{
        AudioSession s=new AudioSession(new AudioEventQueue<>(4,1),100);
        s.pause();s.ready();assertEquals(AudioSession.State.PAUSED,s.snapshot().state);
        s.resume();assertTrue(s.awaitRunnable().running());
        s.fail();s.reset();s.ready();s.resume();assertEquals(AudioSession.State.ERROR,s.awaitRunnable().state);
        s.close();s.ready();assertEquals(AudioSession.State.STOPPED,s.snapshot().state);
    }
}
