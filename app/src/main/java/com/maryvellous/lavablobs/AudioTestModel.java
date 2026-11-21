package com.maryvellous.lavablobs;

/**
 * Pure, deterministic mapping from the existing soft-body world to acoustic
 * control values. No sound playback or physics mutation in this class.
 */
final class AudioTestModel {
    private AudioTestModel() {}

    static float clamp(float x,float lo,float hi) {
        return Math.max(lo,Math.min(hi,x));
    }

    static float compression(Blob blob) {
        float ideal=Math.max(1f,blob.expectedArea());
        float areaDeficit=clamp((ideal-Math.abs(blob.area()))/ideal,0f,1f);
        float radius=Math.max(1f,blob.radiusTarget);
        float radialDeficit=0f;
        for(int i=0;i<Blob.NODES;i++) {
            float dx=blob.px[i]-blob.x,dy=blob.py[i]-blob.y;
            float distance=(float)Math.hypot(dx,dy);
            radialDeficit=Math.max(radialDeficit,clamp((radius-distance)/radius,0f,1f));
        }
        return Math.max(areaDeficit,radialDeficit);
    }

    static float slip(BlobWorld.Adhesion link) {
        float dx=link.b.x-link.a.x,dy=link.b.y-link.a.y;
        float distance=(float)Math.hypot(dx,dy);
        if(distance<.01f)return 0f;
        float tangentialX=-dy/distance,tangentialY=dx/distance;
        float dvx=link.a.vx[link.ai]-link.b.vx[link.bi];
        float dvy=link.a.vy[link.ai]-link.b.vy[link.bi];
        return Math.abs(dvx*tangentialX+dvy*tangentialY);
    }

    /** 13 units/sec of near-static contact must remain silent. */
    static float frictionIntensity(float slip) {
        float progress=clamp((slip-13f)/135f,0f,1f);
        return (float)Math.pow(progress,1.2);
    }

    /** A held, deformed membrane never emits an independent tone. */
    static float compressionIntensity(float oldAmount,float newAmount,float dt) {
        if(dt<=0f)return 0f;
        float rate=Math.abs(newAmount-oldAmount)/dt;
        return clamp((rate-.06f)/2.3f,0f,1f);
    }

    static float impactIntensity(float approach) {
        return clamp((approach-42f)/250f,0f,1f);
    }
}
