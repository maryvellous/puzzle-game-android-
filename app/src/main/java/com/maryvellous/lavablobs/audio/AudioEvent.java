package com.maryvellous.lavablobs.audio;

/** Immutable simulation event. Both desktop replay and Android use this dispatch. */
public final class AudioEvent {
    public enum Type { CONTACT, END, CANCEL, MERGE, LAUNCH, POP, WALL }
    public final Type type;
    public final int a,b,generation;
    public final double time;
    public final long enqueuedNs;
    public final float normal,slip,compression,radiusA,radiusB,dt,bulkMotion;
    public AudioEvent(Type type,int a,int b,float normal,float slip,float compression,
            float radiusA,float radiusB,float dt,float bulkMotion,
            double time,int generation,long enqueuedNs){
        if(type==null||!Double.isFinite(time)||time<0)throw new IllegalArgumentException();
        this.type=type;this.a=a;this.b=b;this.normal=normal;this.slip=slip;
        this.compression=compression;this.radiusA=radiusA;this.radiusB=radiusB;
        this.dt=dt;this.bulkMotion=bulkMotion;this.time=time;this.generation=generation;
        this.enqueuedNs=enqueuedNs;
    }
    public boolean authoritative(){return type!=Type.CONTACT&&type!=Type.WALL;}
    public void apply(MaterialAudioEngine engine){
        switch(type){
            case CONTACT:engine.contact(a,b,normal,slip,compression,radiusA,radiusB,dt,bulkMotion);break;
            case WALL:engine.wallImpact(normal,radiusA);break;
            case END:
            case CANCEL:engine.endContact(a,b);break;
            case MERGE:engine.merge(a,b,radiusA);break;
            case LAUNCH:engine.launch(a,radiusA);break;
            case POP:engine.pop(a,radiusA);break;
            default:throw new AssertionError(type);
        }
    }
}
