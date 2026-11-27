package com.maryvellous.lavablobs.audio;

import java.util.ArrayList;

/** Experimental acoustic clustering AFTER physical gating. No fusion, launch
 * or POP enters this class. Windows never extend; singleton PCM parameters
 * are retained exactly. The renderer owns admission and mixing. */
final class AcousticAggregator {
    enum Family { GEL, WALL, SQUISH }
    interface Sink { boolean emit(float[] sound,float gain,float rate); }
    private static final double RATE_RATIO=1.12;
    private static final int MAX_GROUPS=32,MAX_CONTRIBUTIONS=64;
    private final ArrayList<Group> groups=new ArrayList<>(MAX_GROUPS);
    private final Sink sink;
    private long nextDue=Long.MAX_VALUE;
    private long candidates,emitted,collapsed,rejected,cancelled;
    private int maxGroup;
    private static final class Contribution{
        final int a,b;
        final float[] sound;
        final float gain,rate;
        Contribution(int a,int b,float[] sound,float gain,float rate){
            this.a=a;this.b=b;this.sound=sound;this.gain=gain;this.rate=rate;
        }
    }
    private static final class Group{
        final Family family;
        final long end;
        final ArrayList<Contribution> parts=new ArrayList<>(4);
        float[] representative;
        float strongest,representativeRate;
        double energy,weightedLogRate;
        int count;
        Group(Family family,long end,Contribution c){
            this.family=family;this.end=end;add(c);
        }
        void add(Contribution c){parts.add(c);accumulate(c);}
        private void accumulate(Contribution c){
            if(count==0||c.gain>strongest){representative=c.sound;strongest=c.gain;representativeRate=c.rate;}
            double weight=(double)c.gain*c.gain;
            energy+=weight;weightedLogRate+=weight*Math.log(c.rate);count++;
        }
        int cancelSquish(int blob){
            if(family!=Family.SQUISH)return 0;
            int before=parts.size();
            parts.removeIf(c->c.a==blob||c.b==blob);
            if(parts.size()==before)return 0;
            count=0;energy=weightedLogRate=0;strongest=0;representative=null;
            for(Contribution c:parts)accumulate(c);
            return before-parts.size();
        }
        boolean compatible(Family kind,float rate){
            if(family!=kind||parts.size()>=MAX_CONTRIBUTIONS)return false;
            float firstRate=parts.get(0).rate;
            return Math.max(firstRate/rate,rate/firstRate)<=RATE_RATIO;
        }
    }
    AcousticAggregator(Sink sink){this.sink=sink;}
    private static int window(Family f){
        return Math.round(MaterialAudioEngine.SAMPLE_RATE*(f==Family.SQUISH?.030f:.012f));
    }
    boolean offer(Family family,long frame,float[] sound,float gain,float rate){
        return offer(family,frame,0,0,sound,gain,rate);
    }
    boolean offer(Family family,long frame,int a,int b,float[] sound,float gain,float rate){
        if(gain<=.0001f||!Float.isFinite(gain)||!Float.isFinite(rate)||rate<=0f)return false;
        flushDue(frame);
        candidates++;
        Contribution part=new Contribution(a,b,sound,gain,rate);
        for(Group g:groups){
            if(g.compatible(family,rate)){
                g.add(part);maxGroup=Math.max(maxGroup,g.count);return true;
            }
        }
        if(groups.size()>=MAX_GROUPS){
            // Explicit finite bound: flush oldest instead of dropping a gesture.
            emit(groups.remove(0));recomputeDue();
        }
        Group g=new Group(family,frame+window(family),part);
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
    /** Impacts already happened; only unplayed deformation contributions are
     * invalidated by removal. Existing PCM voices always finish naturally. */
    void cancelBlob(int blob){
        if(blob<=0)return;
        for(int i=groups.size()-1;i>=0;i--){
            Group g=groups.get(i);cancelled+=g.cancelSquish(blob);
            if(g.count==0)groups.remove(i);
        }
        recomputeDue();
    }
    void reset(){groups.clear();nextDue=Long.MAX_VALUE;candidates=emitted=collapsed=rejected=cancelled=0;maxGroup=0;}
    long candidates(){return candidates;}
    long emitted(){return emitted;}
    long collapsed(){return collapsed;}
    long rejected(){return rejected;}
    long cancelled(){return cancelled;}
    int maxGroup(){return maxGroup;}
}
