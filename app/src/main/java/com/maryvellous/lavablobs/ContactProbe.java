package com.maryvellous.lavablobs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Passive acoustic observation of actual vertex/edge contacts.
 *
 * A probe is optional and never alters particle positions, velocities, RNG or
 * contact resolution. It aggregates every projection in the five solver passes
 * into ONE measurement per unordered pair per physics substep. A zero-contact
 * step is still delivered, so a future Contact Tracker can detect releases.
 *
 * All callbacks run synchronously on the simulation thread; they must be cheap.
 * The receiver must copy measurements before handing them to another thread.
 */
final class ContactProbe {
    interface Listener {
        void onContactStep(float simulationTime, float dt, List<Contact> contacts);
        default void onWallImpact(int blobId,int side,float approach,float radius) {}
        default void onWallImpact(int blobId,int side,float approach,float radius,float time) {
            onWallImpact(blobId,side,approach,radius);
        }
        default void onLaunch(int tier,float radius,float time) {}
        default void onMerge(BlobWorld.Merge event) {}
        default void onPop(int blobId,float simulationTime) {}
        default void onPop(int blobId,float simulationTime,float radius) {onPop(blobId,simulationTime);}
        default void onReset() {}
    }

    static final class Contact {
        final int blobA, blobB; // Stable IDs in ascending order.
        final float time, dt;
        final float radiusA, radiusB;
        final float x, y, nx, ny; // Weighted contact point and A -> B normal.
        final float approachSpeed, slideSpeed; // Max closing speed, mean tangential slip.
        final float compression; // Bounded geometric proxy, NOT force or impulse.
        // Actual bulk motion from ALL 24 particle velocities AFTER the
        // physics substep: unlike local sliding, solver chatter at the
        // contact patch cannot alone make a resting body sound active.
        final float bulkMotion;
        final int projectionSamples; // Includes repeat observations across solver passes.

        Contact(Accumulator a, float time, float dt) {
            blobA=a.a.id;
            blobB=a.b.id;
            this.time=time;
            this.dt=dt;
            radiusA=a.a.radiusTarget;
            radiusB=a.b.radiusTarget;
            float inv=1f/a.samples;
            x=a.x*inv;
            y=a.y*inv;
            float length=(float)Math.hypot(a.nx,a.ny);
            if(length>1e-5f) {
                nx=a.nx/length;
                ny=a.ny/length;
            } else {
                float dx=a.b.x-a.a.x, dy=a.b.y-a.a.y;
                float centerDistance=(float)Math.hypot(dx,dy);
                nx=centerDistance>1e-5f?dx/centerDistance:1f;
                ny=centerDistance>1e-5f?dy/centerDistance:0f;
            }
            approachSpeed=a.peakApproach;
            slideSpeed=a.slide*inv;
            // Area loss is a global membrane proxy; depth is local. Neither is
            // an exact contact force in this position-based solver.
            compression=Math.max(a.localCompression,
                    Math.max(areaLoss(a.a),areaLoss(a.b)));
            projectionSamples=a.samples;
            bulkMotion=Math.max(bodySpeed(a.a),bodySpeed(a.b));
        }

        private static float bodySpeed(Blob b) {
            float vx=0f,vy=0f;
            for(int i=0;i<Blob.NODES;i++){vx+=b.vx[i];vy+=b.vy[i];}
            return (float)Math.hypot(vx/Blob.NODES,vy/Blob.NODES);
        }

        private static float areaLoss(Blob blob) {
            float expected=Math.max(.01f,blob.expectedArea());
            return Blob.clamp((expected-Math.abs(blob.area()))/expected,0f,1f);
        }
    }

    private static final class Accumulator {
        final Blob a,b;
        float x,y,nx,ny,peakApproach,slide,localCompression;
        int samples;
        Accumulator(Blob first, Blob second) {
            if(first.id<second.id) { a=first; b=second; }
            else { a=second; b=first; }
        }
    }

    private final Listener listener;
    // LinkedHashMap keeps contact order deterministic for offline traces.
    private final Map<Long,Accumulator> contacts=new LinkedHashMap<>();

    ContactProbe(Listener listener) {
        if(listener==null)throw new IllegalArgumentException("Missing probe listener");
        this.listener=listener;
    }

    // Aggregate all membrane vertices and solver passes into one observation per
    // blob and wall side. A held contact cannot repeatedly emit an impact.
    private static final class WallContact {
        final int blobId,side;
        final float radius;
        float peak;
        WallContact(Blob b,int side){this.blobId=b.id;this.side=side;this.radius=b.radiusTarget;}
    }
    private static final class WallState {
        float first,last;
        boolean fired;
        WallState(float time){first=last=time;}
    }
    private final Map<Long,WallContact> wallStep=new LinkedHashMap<>();
    private final Map<Long,WallState> wallStates=new LinkedHashMap<>();
    void beginStep() {contacts.clear();wallStep.clear();}

    void observeWall(Blob b,int side,float nx,float ny) {
        float vx=0f,vy=0f;
        for(int i=0;i<Blob.NODES;i++){vx+=b.vx[i];vy+=b.vy[i];}
        observeWall(b,side,nx,ny,vx,vy);
    }
    // Bulk sums may be reused across vertex projections in one confine call:
    // only positions are mutated by Vessel.confine, never velocities.
    void observeWall(Blob b,int side,float nx,float ny,float vx,float vy) {
        long key=((long)b.id<<2)|side;
        WallContact c=wallStep.get(key);
        if(c==null){c=new WallContact(b,side);wallStep.put(key,c);}
        // Use whole-body closing velocity, not isolated vertex correction jitter.
        c.peak=Math.max(c.peak,Math.max(0f,-(vx*nx+vy*ny)/Blob.NODES));
    }

    private void finishWalls(float time) {
        wallStates.entrySet().removeIf(e->time-e.getValue().last>.045f);
        for(Map.Entry<Long,WallContact> item:wallStep.entrySet()){
            WallContact c=item.getValue();
            WallState state=wallStates.get(item.getKey());
            if(state==null){state=new WallState(time);wallStates.put(item.getKey(),state);}
            state.last=time;
            if(!state.fired&&time-state.first<=.060f&&c.peak>70f){
                state.fired=true;
                listener.onWallImpact(c.blobId,c.side,c.peak,c.radius,time);
            }
        }
        wallStep.clear();
    }

    void observe(Blob source,int node,Blob target,int edgeNode,float t,
            float nx,float ny,float overlap,float x,float y) {
        if(source.id<=0||target.id<=0||source.id==target.id)
            throw new IllegalStateException("Contact requires distinct registered blob IDs");
        long key=BlobWorld.pairKey(source.id,target.id);
        Accumulator a=contacts.get(key);
        if(a==null) {
            a=new Accumulator(source,target);
            contacts.put(key,a);
        }
        int next=(edgeNode+1)%Blob.NODES;
        float tvx=target.vx[edgeNode]*(1f-t)+target.vx[next]*t;
        float tvy=target.vy[edgeNode]*(1f-t)+target.vy[next]*t;
        float dvx=source.vx[node]-tvx,dvy=source.vy[node]-tvy;
        // The outward correction normal points from target to source. Closing
        // velocity is its NEGATIVE projection, before any contact correction.
        float approach=Math.max(0f,-(dvx*nx+dvy*ny));
        float slip=Math.abs(-dvx*ny+dvy*nx);
        a.peakApproach=Math.max(a.peakApproach,approach);
        a.slide+=slip;
        float radius=Math.max(1f,Math.min(source.radiusTarget,target.radiusTarget));
        a.localCompression=Math.max(a.localCompression,
                Blob.clamp(overlap/radius,0f,1f));
        a.x+=x;
        a.y+=y;
        // The normal in the event is always from lower ID to higher ID.
        float direction=source.id<target.id?-1f:1f;
        a.nx+=nx*direction;
        a.ny+=ny*direction;
        a.samples++;
    }

    void finishStep(float time,float dt) {
        List<Contact> result=new ArrayList<>(contacts.size());
        for(Accumulator a:contacts.values())
            result.add(new Contact(a,time,dt));
        contacts.clear();
        listener.onContactStep(time,dt,Collections.unmodifiableList(result));
        finishWalls(time);
    }

    void merged(BlobWorld.Merge event) {listener.onMerge(event);}
    void launched(int tier,float radius,float time) {listener.onLaunch(tier,radius,time);}
    void popped(int blobId,float time,float radius) {listener.onPop(blobId,time,radius);}
    void popped(int blobId,float time) {listener.onPop(blobId,time);}
    void reset() {
        contacts.clear();wallStep.clear();wallStates.clear();
        listener.onReset();
    }
}
