package com.maryvellous.lavablobs.audio;

/** Maps a monotonic simulation clock to the PCM write cursor, with bounded lead.
 * The caller holds a scheduled event until its exact sample offset is reached.
 * A late event is reanchored rather than played in the past. */
public final class AudioTimeline {
    private final int rate,lead,maxLead;
    private double sourceOrigin;
    private long frameOrigin,lastFrame;
    private boolean anchored;
    private long rebases,lateEvents;
    public AudioTimeline(int rate,int lead,int maxLead){
        if(rate<=0||lead<0||maxLead<lead)throw new IllegalArgumentException();
        this.rate=rate;this.lead=lead;this.maxLead=maxLead;
    }
    public long schedule(double seconds,long cursor){
        if(!Double.isFinite(seconds)||seconds<0||cursor<0)throw new IllegalArgumentException();
        if(!anchored){anchor(seconds,cursor+lead);return lastFrame;}
        long target=frameOrigin+Math.round((seconds-sourceOrigin)*rate);
        if(target<cursor){
            lateEvents++;
            anchor(seconds,Math.max(cursor,lastFrame));
        }else if(target>cursor+maxLead){
            rebases++;
            anchor(seconds,Math.max(cursor+lead,lastFrame));
        }else lastFrame=Math.max(lastFrame,target);
        return lastFrame;
    }
    private void anchor(double seconds,long frame){
        anchored=true;sourceOrigin=seconds;frameOrigin=lastFrame=frame;
    }
    public void reset(){anchored=false;sourceOrigin=0;frameOrigin=lastFrame=0;}
    public long lateEvents(){return lateEvents;}
    public long rebases(){return rebases;}
}
