package com.maryvellous.lavablobs.audio;

import java.util.ArrayList;

/** Experimental acoustic clustering AFTER physical gating. No fusion, launch
 * or POP enters this class. Windows never extend; singleton PCM parameters
 * are retained exactly. The renderer owns admission and mixing. */
final class AcousticAggregator {
    enum Family { GEL, WALL, SQUISH }
    interface Sink { boolean emit(float[] sound,float gain,float rate); }
    private static final double RATE_RATIO=1.12;
    private static final int MAX_GROUPS=32;
    private final ArrayList<Group> groups=new ArrayList<>(MAX_GROUPS);
    private final Sink sink;
    private long nextDue=Long.MAX_VALUE;
    private long candidates,emitted,collapsed,rejected;
    private int maxGroup;
    private static final class Group{
        final Family family;
        final long end;
        final float firstRate;
        float[] representative;
        float strongest,representativeRate;
        double energy,weightedLogRate;
        int count;
        Group(Family family,long end,float[] sound,float gain,float rate){
            this.family=family;this.end=end;firstRate=rate;
            add(sound,gain,rate);
        }
        void add(float[] sound,float gain,float rate){
            if(count==0||gain>strongest){representative=sound;strongest=gain;representativeRate=rate;}
            double weight=(double)gain*gain;
            energy+=weight;weightedLogRate+=weight*Math.log(rate);count++;
        }
        boolean compatible(Family kind,float rate){
            return family==kind&&Math.max(firstRate/rate,rate/firstRate)<=RATE_RATIO;
        }
    }
    AcousticAggregator(Sink sink){this.sink=sink;}
    private static int window(Family f){
        return Math.round(MaterialAudioEngine.SAMPLE_RATE*(f==Family.SQUISH?.030f:.012f));
    }
    boolean offer(Family family,long frame,float[] sound,float gain,float rate){
        if(gain<=.0001f||!Float.isFinite(gain)||!Float.isFinite(rate)||rate<=0f)return false;
        flushDue(frame);
        candidates++;
        for(Group g:groups){
            if(g.compatible(family,rate)){
                g.add(sound,gain,rate);maxGroup=Math.max(maxGroup,g.count);return true;
            }
        }
        if(groups.size()>=MAX_GROUPS){
            // Explicit finite bound: flush oldest instead of dropping a gesture.
            emit(groups.remove(0));recomputeDue();
        }
        Group g=new Group(family,frame+window(family),sound,gain,rate);
        groups.add(g);nextDue=Math.min(nextDue,g.end);maxGroup=Math.max(maxGroup,1);
        return true;
    }
    void flushDue(long frame){
        if(frame<nextDue)return;
        for(int i=0;i<groups.size();){
            Group g=groups.get(i);
            if(g.end<=frame){groups.remove(i);emit(g);}else i++;
        }
        recomputeDue();
    }
    private void recomputeDue(){
        nextDue=Long.MAX_VALUE;
        for(Group g:groups)nextDue=Math.min(nextDue,g.end);
    }
    private void emit(Group g){
        float gain=g.strongest,rate=g.representativeRate;
        if(g.count>1){
            // Monotone but bounded relative to strongest constituent.
            // Soft saturation of collective gain; tuning remains provisional.
            double relative=Math.sqrt(g.energy)/g.strongest;
            gain=(float)(g.strongest*(1+.5*Math.tanh((relative-1)/.5)));
            rate=(float)Math.exp(g.weightedLogRate/g.energy);
        }
        emitted++;collapsed+=g.count-1;
        if(!sink.emit(g.representative,gain,rate))rejected++;
    }
    void reset(){groups.clear();nextDue=Long.MAX_VALUE;candidates=emitted=collapsed=rejected=0;maxGroup=0;}
    long candidates(){return candidates;}
    long emitted(){return emitted;}
    long collapsed(){return collapsed;}
    long rejected(){return rejected;}
    int maxGroup(){return maxGroup;}
}
