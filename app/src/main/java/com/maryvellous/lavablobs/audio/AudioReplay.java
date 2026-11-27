package com.maryvellous.lavablobs.audio;

import java.util.List;

/** Offline replay feeds the same queue, clock mapping, dispatcher and renderer
 * as Android. A trace represents one simulation generation, starting at t=0. */
public final class AudioReplay {
    public static final int BLOCK=384;
    private AudioReplay(){}
    public static float[] render(MaterialAudioEngine engine,List<AudioEvent> events,int frames){
        if(frames<=0||frames%BLOCK!=0)throw new IllegalArgumentException("Use whole PCM blocks");
        final int generation=events.isEmpty()?0:events.get(0).generation;
        double previous=-1;
        for(AudioEvent e:events){
            if(e.generation!=generation||e.time<previous)
                throw new IllegalArgumentException("Replay requires ordered events in one generation");
            previous=e.time;
        }
        AudioEventQueue<AudioEvent> queue=new AudioEventQueue<>(8192,1024);
        AudioBlockScheduler scheduler=new AudioBlockScheduler(queue,
                new AudioTimeline(MaterialAudioEngine.SAMPLE_RATE,BLOCK,
                    Math.round(MaterialAudioEngine.SAMPLE_RATE*.12f)),512);
        float[] result=new float[frames],block=new float[BLOCK];
        int next=0;
        for(int offset=0;offset<frames;offset+=BLOCK){
            double horizon=(offset+BLOCK)/(double)MaterialAudioEngine.SAMPLE_RATE;
            while(next<events.size()&&events.get(next).time<=horizon){
                AudioEvent e=events.get(next++);
                if(!queue.offer(e,e.authoritative()))throw new IllegalStateException("Replay overflow");
            }
            if(!scheduler.render(engine,block,generation,()->generation))
                throw new IllegalStateException("Unexpected replay generation change");
            System.arraycopy(block,0,result,offset,BLOCK);
        }
        if(next!=events.size()||queue.size()!=0||scheduler.dispatched()!=events.size())
            throw new IllegalArgumentException("Replay duration too short or events not dispatched");
        return result;
    }
}
