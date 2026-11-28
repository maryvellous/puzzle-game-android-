package com.maryvellous.lavablobs.audio;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;

/** Experimental acoustic clustering AFTER physical gating. No fusion, launch
 * or POP enters this class. Windows never extend; singleton PCM parameters
 * are retained exactly. The renderer owns admission and mixing. */
final class AcousticAggregator {
    enum Family {
        GEL(MaterialAudioEngine.VoiceFamily.GEL), WALL(MaterialAudioEngine.VoiceFamily.WALL), SQUISH(MaterialAudioEngine.VoiceFamily.SQUISH);
        final MaterialAudioEngine.VoiceFamily voiceFamily;
        Family(MaterialAudioEngine.VoiceFamily voiceFamily){this.voiceFamily=voiceFamily;}
    }
    interface Sink { boolean emit(float[] sound,float gain,float rate); }
    interface FamilySink { boolean emit(Family family,float[] sound,float gain,float rate); }
    private static final double RATE_RATIO=1.12;
    private static final int MAX_GROUPS=32,MAX_CONTRIBUTIONS=64;
    private final ArrayList<Group> groups=new ArrayList<>(MAX_GROUPS);
    private final FamilySink sink;
    private final ArrayDeque<Group> free=new ArrayDeque<>(MAX_GROUPS);
    private long nextDue=Long.MAX_VALUE;
    private long candidates,emitted,collapsed,rejected,cancelled;
    private int maxGroup;
    /** Fixed storage reused across groups. Retain contribution order for exact
     * representative tie breaks and floating-point recomputation on cancel. */
    private static final class Group{
        Family family;
        long end;
        final int[] idsA=new int[MAX_CONTRIBUTIONS],idsB=new int[MAX_CONTRIBUTIONS];
        final float[][] sounds=new float[MAX_CONTRIBUTIONS][];
        final float[] gains=new float[MAX_CONTRIBUTIONS],rates=new float[MAX_CONTRIBUTIONS];
        float[] representative;
        float strongest,representativeRate;
        double energy,weightedLogRate;
        int count;
        void start(Family family,long end,int a,int b,float[] sound,float gain,float rate){
            this.family=family;this.end=end;add(a,b,sound,gain,rate);
        }
        void add(int a,int b,float[] sound,float gain,float rate){
            idsA[count]=a;idsB[count]=b;sounds[count]=sound;gains[count]=gain;rates[count]=rate;
            accumulate(sound,gain,rate);
        }
        private void accumulate(float[] sound,float gain,float rate){
            if(count==0||gain>strongest){representative=sound;strongest=gain;representativeRate=rate;}
            double weight=(double)gain*gain;
            energy+=weight;weightedLogRate+=weight*Math.log(rate);count++;
        }
        int cancelSquish(int blob){
            if(family!=Family.SQUISH)return 0;
            int before=count,write=0;
            for(int read=0;read<before;read++){
                if(idsA[read]==blob||idsB[read]==blob)continue;
                idsA[write]=idsA[read];idsB[write]=idsB[read];sounds[write]=sounds[read];
                gains[write]=gains[read];rates[write]=rates[read];write++;
            }
            if(write==before)return 0;
            Arrays.fill(sounds,write,before,null);
            count=0;energy=weightedLogRate=0;strongest=0;representative=null;
            for(int i=0;i<write;i++)accumulate(sounds[i],gains[i],rates[i]);
            return before-write;
        }
        boolean compatible(Family kind,float rate){
            if(family!=kind||count>=MAX_CONTRIBUTIONS)return false;
            float firstRate=rates[0];
            return Math.max(firstRate/rate,rate/firstRate)<=RATE_RATIO;
        }
        void clear(){
            Arrays.fill(sounds,0,count,null);
            count=0;energy=weightedLogRate=0;strongest=representativeRate=0;
            representative=null;family=null;end=0;
        }
    }
    AcousticAggregator(Sink sink){this((family,sound,gain,rate)->sink.emit(sound,gain,rate));}
    AcousticAggregator(FamilySink sink){this.sink=sink;for(int i=0;i<MAX_GROUPS;i++)free.addLast(new Group());}
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
        for(Group g:groups){
            if(g.compatible(family,rate)){
                g.add(a,b,sound,gain,rate);maxGroup=Math.max(maxGroup,g.count);return true;
            }
        }
        if(groups.size()>=MAX_GROUPS){
            // Explicit finite bound: flush oldest instead of dropping a gesture.
            emit(groups.remove(0));recomputeDue();
        }
        Group g=free.removeLast();g.start(family,frame+window(family),a,b,sound,gain,rate);
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
        if(!sink.emit(g.family,g.representative,gain,rate))rejected++;
        recycle(g);
    }
    /** Impacts already happened; only unplayed deformation contributions are
     * invalidated by removal. Existing PCM voices always finish naturally. */
    void cancelBlob(int blob){
        if(blob<=0)return;
        for(int i=groups.size()-1;i>=0;i--){
            Group g=groups.get(i);cancelled+=g.cancelSquish(blob);
            if(g.count==0)recycle(groups.remove(i));
        }
        recomputeDue();
    }
    private void recycle(Group g){g.clear();free.addLast(g);}
    void reset(){for(int i=groups.size()-1;i>=0;i--)recycle(groups.get(i));groups.clear();nextDue=Long.MAX_VALUE;candidates=emitted=collapsed=rejected=cancelled=0;maxGroup=0;}
    long candidates(){return candidates;}
    long emitted(){return emitted;}
    long collapsed(){return collapsed;}
    long rejected(){return rejected;}
    long cancelled(){return cancelled;}
    int maxGroup(){return maxGroup;}
}
