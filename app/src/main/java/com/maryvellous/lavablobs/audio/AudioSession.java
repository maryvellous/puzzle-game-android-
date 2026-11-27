package com.maryvellous.lavablobs.audio;

/** Producer/worker boundary. All lifecycle and queue-admission changes are
 * serialized; the worker reads one immutable snapshot without locking.
 * No device calls here: only the audio worker may own the device. */
public final class AudioSession {
    public enum State { STARTING, READY, PAUSED, MUTED, OVERFLOW, ERROR, STOPPED }
    public enum Admission { ACCEPTED, INACTIVE, STARTUP, STALE, OBSERVATION_REJECTED, CONTROL_OVERFLOW }
    public static final class Snapshot {
        public final State state;
        public final int generation,volume;
        private Snapshot(State state,int generation,int volume){
            this.state=state;this.generation=generation;this.volume=volume;
        }
        public boolean running(){return state==State.READY;}
        public boolean stopped(){return state==State.STOPPED||state==State.ERROR;}
    }
    private final AudioEventQueue<AudioEvent> queue;
    private boolean deviceReady,paused,overflow,error,closed;
    private int generation,volume;
    private volatile Snapshot snapshot;
    public AudioSession(AudioEventQueue<AudioEvent> queue,int volume){
        this.queue=queue;this.volume=clamp(volume);publish();
    }
    private static int clamp(int v){return Math.max(0,Math.min(100,v));}
    private void publish(){
        State state=closed?State.STOPPED:error?State.ERROR:overflow?State.OVERFLOW:
                paused?State.PAUSED:volume==0?State.MUTED:deviceReady?State.READY:State.STARTING;
        snapshot=new Snapshot(state,generation,volume);
    }
    private void invalidate(){queue.clear();generation++;}
    public Snapshot snapshot(){return snapshot;}
    public synchronized void ready(){
        if(closed||error)return;
        deviceReady=true;publish();notifyAll();
    }
    public synchronized void volume(int value){
        if(closed)return;
        int next=clamp(value);
        if(next==volume)return;
        if(next==0){invalidate();overflow=false;}
        volume=next;publish();notifyAll();
    }
    public synchronized void pause(){
        if(closed||paused)return;
        paused=true;overflow=false;invalidate();publish();notifyAll();
    }
    public synchronized void resume(){
        if(closed||!paused)return;
        paused=false;publish();notifyAll();
    }
    public synchronized void reset(){
        if(closed)return;
        overflow=false;invalidate();publish();notifyAll();
    }
    public synchronized void fail(){
        if(closed||error)return;
        error=true;deviceReady=false;invalidate();publish();notifyAll();
    }
    public synchronized void close(){
        if(closed)return;
        closed=true;deviceReady=false;invalidate();publish();notifyAll();
    }
    public synchronized Admission submit(AudioEvent event){
        if(event.generation!=generation)return Admission.STALE;
        if(snapshot.state==State.STARTING)return Admission.STARTUP;
        if(!snapshot.running())return Admission.INACTIVE;
        if(queue.offer(event,event.authoritative()))return Admission.ACCEPTED;
        if(!event.authoritative())return Admission.OBSERVATION_REJECTED;
        overflow=true;invalidate();publish();notifyAll();
        return Admission.CONTROL_OVERFLOW;
    }
    public synchronized Snapshot awaitRunnable() throws InterruptedException {
        while(!snapshot.running()&&!snapshot.stopped())wait();
        return snapshot;
    }
}
