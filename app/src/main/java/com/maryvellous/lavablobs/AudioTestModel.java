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

    // MIX TEST: ignore very light jostling between already-nearby membranes.
    // The solver still reports all contacts; only this sound gate changes.
    static boolean isAudibleImpact(float approachSpeed) {
        return approachSpeed >= 118f;
    }

    static float audibleImpactIntensity(float approachSpeed) {
        return clamp((approachSpeed-118f)/240f,0f,1f);
    }

    // Earlier adhesion slip remained nearly silent because 13..148 units/s
    // was mapped onto a shallow 0..1 gain. The new soft-knee curve starts
    // after genuine motion, then develops audibly without constant clicks.
    static float audibleFrictionIntensity(float slip) {
        if(slip <= 8f)return 0f;
        return (float)Math.pow(clamp((slip-8f)/88f,0f,1f),.74f);
    }

    /** Prevent sudden negative gains or sum of two continuous loop voices. */
    static boolean shouldPreferFriction(float frictionTarget,float compressionTarget) {
        return frictionTarget >= .035f ||
                (frictionTarget > .010f && frictionTarget >= compressionTarget*.6f);
    }

    // The game renders a 2D membrane, but the intended acoustic body has
    // volume. Cube-mean the CURRENT membrane radii so the larger of two
    // unequal blobs contributes proportionately more acoustic mass.
    static float pairRadius(float radiusA,float radiusB) {
        float a=Math.max(8f,radiusA),b=Math.max(8f,radiusB);
        return (float)Math.cbrt((a*a*a+b*b*b)*.5);
    }

    /** SoundPool changes playback speed and pitch together (0.5..2.0). */
    static float sizePlaybackRate(float effectiveRadius) {
        float radius=Math.max(8f,effectiveRadius);
        float scale=(float)Math.pow(Blob.RADII[2]/radius,.46);
        return clamp(scale,.56f,1.34f);
    }

    /** A ~100ms glide prevents discontinuous pitch jumps as contacts change. */
    static float smoothPlaybackRate(float previous,float target,float dt) {
        float alpha=(float)(1.0-Math.exp(-10.0*Math.max(0f,dt)));
        return previous+(target-previous)*clamp(alpha,0f,1f);
    }
}
