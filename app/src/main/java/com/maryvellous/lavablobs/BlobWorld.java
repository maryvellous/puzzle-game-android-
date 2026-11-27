package com.maryvellous.lavablobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Real particle/segment soft-body interactions. This solver never collides
 * centres or circular radii. Blobs rise; they are free to pile up against the
 * top surface. Different colours have short-lived sticky contact patches.
 */
final class BlobWorld {
    static final float WIDTH = 420f, LEFT = 29f, RIGHT = 391f, TOP = 155f;
    static final int ERASE_EVERY_POINTS=2_000; // provisional old-scale equivalent; validate in endless playtests

    static final class Merge {
        final float x, y, radius;
        final int tier, points;
        // Non-breaking acoustic metadata; legacy SoundPool still reads x/y/tier.
        final int parentAId, parentBId, resultId;
        final float simulationTime;
        Merge(float x, float y, float radius, int tier, int points,
                int parentAId, int parentBId, int resultId, float simulationTime) {
            this.x=x; this.y=y; this.radius=radius;
            this.tier=tier; this.points=points;
            this.parentAId=parentAId; this.parentBId=parentBId;
            this.resultId=resultId; this.simulationTime=simulationTime;
        }
    }

    static final class Impact {
        final int tier;
        final float speed;
        Impact(int tier,float speed){this.tier=tier;this.speed=speed;}
    }

    static final class Adhesion {
        final Blob a, b;
        final int ai, bi;
        float age;
        Adhesion(Blob a, Blob b, int ai, int bi) {
            this.a=a; this.b=b; this.ai=ai; this.bi=bi;
        }
        float length() {
            return (float)Math.hypot(a.px[ai]-b.px[bi], a.py[ai]-b.py[bi]);
        }
    }

    private static final int PASSES = 5;
    private static final float SKIN = 3f;
    final ArrayList<Blob> blobs = new ArrayList<>();
    final ArrayList<Merge> merges = new ArrayList<>();
    private final ArrayList<Impact> impacts = new ArrayList<>();
    private final Map<Long,Float> impactTimes = new HashMap<>();
    final ArrayList<Adhesion> adhesions = new ArrayList<>();
    // IDs are monotonic for one game, including deleted/merged blobs and saves.
    int nextBlobId=1;
    private ContactProbe contactProbe; // Null in 1.0.11: zero DSP/probe overhead.
    Random random;
    final GameSpec spec;
    Vessel vessel;
    float height=850f, time, blockedTime,checkClock;
    long score;
    int nextTier,dropCount,mergeCount,maxTierReached;
    int earnedErases,usedErases,eraseCount;
    // Non-persistent event payload for the short cosmetic POP animation.
    float lastPoppedX,lastPoppedY,lastPoppedRadius;
    int lastPoppedTier,lastPoppedId;
    final int[] mergesByTier=new int[40];
    boolean gameOver;

    BlobWorld(Random random){this(random,null);}
    BlobWorld(Random random,GameSpec spec) {
        this.random=random;this.spec=spec;
        reset();
    }

    /**
     * Diagnostic-only subscription for physics/acoustic tests and future V2.
     * No listener is installed by the current gameplay/audio implementation.
     */
    void setContactProbe(ContactProbe.Listener listener) {
        contactProbe=listener==null?null:new ContactProbe(listener);
    }

    // Canonical unordered key; both IDs must be positive. No identityHashCode.
    static long pairKey(int idA,int idB) {
        if(idA<=0||idB<=0||idA==idB)
            throw new IllegalArgumentException("Invalid blob pair");
        long lo=Math.min(idA,idB)&0xffffffffL;
        long hi=Math.max(idA,idB)&0xffffffffL;
        return (lo<<32)|hi;
    }

    void ensureBlobIds() {
        int max=nextBlobId-1;
        for(Blob b:blobs) {
            if(b.id<0)throw new IllegalStateException("Invalid blob ID");
            max=Math.max(max,b.id);
        }
        if(max>=Integer.MAX_VALUE-1)
            throw new IllegalStateException("Exhausted blob IDs");
        if(nextBlobId<=max)nextBlobId=max+1;
        for(Blob b:blobs)if(b.id==0)assignBlobId(b);
    }

    private void assignBlobId(Blob b) {
        if(b.id!=0)throw new IllegalStateException("Blob ID already assigned");
        if(nextBlobId>=Integer.MAX_VALUE)
            throw new IllegalStateException("Exhausted blob IDs");
        b.id=nextBlobId++;
    }

    float bottom(){return vessel.bottom;}
    void setHeight(float height){
        float h=Math.max(620f,height);
        if(Math.abs(this.height-h)>.2f) {
            this.height=h;
            vessel=new Vessel(spec==null?-1:spec.shape,h);
        }
    }
    void reset(){
        blobs.clear();merges.clear();impacts.clear();impactTimes.clear();adhesions.clear();
        nextBlobId=1;
        if(contactProbe!=null)contactProbe.reset();
        score=0;time=0f;blockedTime=0f;checkClock=0f;gameOver=false;
        dropCount=0;mergeCount=0;maxTierReached=0;
        earnedErases=0;usedErases=0;eraseCount=0;
        java.util.Arrays.fill(mergesByTier,0);
        vessel=new Vessel(spec==null?-1:spec.shape,height);
        nextTier=rollTier();
    }
    private int rollTier(){return GameSpec.rollDrop(random);}

    boolean canLaunch(float x) {
        if(gameOver)return false;
        float r=Blob.RADII[nextTier],launchRadius=r*.76f;
        float cx=clamp(x,LEFT+r,RIGHT-r),cy=bottom()-r-7f;
        if(!vessel.fits(cx,cy,launchRadius))return false;
        float nodeClearance=launchRadius+2f;
        float nodeClearanceSq=nodeClearance*nodeClearance;
        for(Blob b:blobs) {
            if(pointInside(b,cx,cy))return false;
            for(int i=0;i<Blob.NODES;i++) {
                float dx=b.px[i]-cx,dy=b.py[i]-cy;
                if(dx*dx+dy*dy<nodeClearanceSq)return false;
            }
            float dx=b.x-cx,dy=b.y-cy;
            float limit=launchRadius+b.radius()*.56f;
            if(dx*dx+dy*dy<limit*limit)return false;
        }
        return true;
    }

    boolean launch(float x) {
        if (!canLaunch(x)) return false;
        float r=Blob.RADII[nextTier];
        Blob b=new Blob(clamp(x,LEFT+r,RIGHT-r),bottom()-r-7f,nextTier,.76f);
        for (int i=0;i<Blob.NODES;i++) {
            b.vy[i]=-180f;
            b.vx[i]=(random.nextFloat()-.5f)*24f;
        }
        assignBlobId(b);
        blobs.add(b);
        dropCount++;
        maxTierReached=Math.max(maxTierReached,nextTier);
        if(contactProbe!=null)contactProbe.launched(b.tier,b.radius(),time);
        nextTier=rollTier();
        checkClock=.2f;
        return true;
    }

    void shake(float sensorX,float sensorY,float sensorZ) {
        if (gameOver || blobs.isEmpty()) return;
        float magnitude=(float)Math.sqrt(sensorX*sensorX+sensorY*sensorY+sensorZ*sensorZ);
        float strength=clamp(magnitude/8f,.55f,1.75f);
        for (Blob b:blobs) {
            float factor=1.18f-b.tier*.07f;
            float x=clamp(-sensorX*25f,-310f,310f)*factor;
            float y=clamp(sensorY*25f,-310f,310f)*factor;
            // Z-axis jolts also stir the ring, through independent node motion.
            float depth=Math.min(125f,Math.abs(sensorZ)*20f);
            float direction=(b.x>WIDTH*.5f?1f:-1f);
            b.kick(x+direction*depth*.25f*strength,y,random,
                    48f*strength + depth);
        }
    }

    /** True when at least one location can introduce the next actual mesh. */
    boolean canLaunchAnywhere() {
        for(float x=LEFT+18f;x<=RIGHT-18f;x+=8f)if(canLaunch(x))return true;
        return canLaunch(210f);
    }

    float graceSeconds() {return spec!=null&&spec.endless?10f:7f;}
    float remainingSeconds(){return Math.max(0f,graceSeconds()-blockedTime);}

    boolean eraseAt(float x,float y) {
        if(spec==null||!spec.endless||eraseCount<1||gameOver)return false;
        for(int i=blobs.size()-1;i>=0;i--) {
            Blob b=blobs.get(i);
            if(!pointInside(b,x,y))continue;
            lastPoppedX=b.x;
            lastPoppedY=b.y;
            lastPoppedRadius=b.radius();
            lastPoppedTier=b.tier;
            lastPoppedId=b.id;
            blobs.remove(i);
            if(contactProbe!=null)contactProbe.popped(b.id,time,lastPoppedRadius);
            adhesions.removeIf(link->link.a==b||link.b==b);
            eraseCount--;usedErases++;
            blockedTime=0;checkClock=.2f;
            return true;
        }
        return false;
    }

    void tick(float dt) {
        if(gameOver)return;
        dt=clamp(dt,0f,.042f);
        for(int s=0;s<2;s++)step(dt*.5f);
        // No Y-threshold. Check real incoming-object clearance, repeatedly,
        // after giving the soft bodies time to rebound from any phone shake.
        checkClock+=dt;
        if(checkClock>=.18f) {
            checkClock=0f;
            if(canLaunchAnywhere())blockedTime=0f;
            else blockedTime+=.18f;
        }
        if(blockedTime>=graceSeconds())gameOver=true;
    }

    private void step(float dt) {
        if(dt<=0f)return;
        time+=dt;
        ensureBlobIds(); // Also supports test worlds that inject Blob instances directly.
        if(contactProbe!=null)contactProbe.beginStep();
        for(Blob b:blobs)b.integrate(dt,time);

        // Merge detection uses actual mesh boundaries. No spherical "sum of
        // radii" may trigger fusion across a visible gap.
        boolean fused=false;
        outer:
        for(int i=0;i<blobs.size();i++) {
            Blob a=blobs.get(i);
            for(int j=i+1;j<blobs.size();j++) {
                Blob b=blobs.get(j);
                if(a.tier==b.tier&&nearEnough(a,b,1.25f)&&surfaceTouch(a,b,2.6f)) {
                    fuse(i,j,a,b);
                    fused=true;
                    break outer;
                }
            }
        }
        if(fused)adhesions.removeIf(link->!blobs.contains(link.a)||!blobs.contains(link.b));

        // Constraint iterations alternate membrane pressure with true
        // particle-to-edge contact projection. Local contacts flatten the
        // outline. Area pressure redistributes the displaced substance.
        for(int pass=0;pass<PASSES;pass++) {
            for(Blob b:blobs)b.solveMembrane();
            for(int i=0;i<blobs.size();i++) {
                Blob a=blobs.get(i);
                for(int j=i+1;j<blobs.size();j++) {
                    Blob b=blobs.get(j);
                    if(a.tier==b.tier||!nearEnough(a,b,1.55f))continue;
                    projectContacts(a,b,pass==0);
                }
            }
            solveAdhesions();
            for(Blob b:blobs)vessel.confine(b,contactProbe);
        }
        for(Blob b:blobs)b.finish(dt);
        updateAdhesions(dt);
        if(contactProbe!=null)contactProbe.finishStep(time,dt);
    }

    private boolean nearEnough(Blob a,Blob b,float scale) {
        // Broad-phase filter only. The shape and collision are evaluated
        // entirely from membrane vertices and line segments below.
        float dx=a.x-b.x,dy=a.y-b.y;
        float limit=(a.radiusTarget+b.radiusTarget)*scale+5f;
        return dx*dx+dy*dy < limit*limit;
    }

    private void fuse(int i,int j,Blob a,Blob b) {
        float cx=(a.x+b.x)*.5f,cy=(a.y+b.y)*.5f;
        int points=GameSpec.pointsForMerge(a.tier);
        int nextTier=a.tier+1;
        blobs.remove(j);blobs.remove(i);
        score+=points;
        mergeCount++;
        if(a.tier<mergesByTier.length)mergesByTier[a.tier]++;
        maxTierReached=Math.max(maxTierReached,nextTier);
        Blob born=new Blob(cx,cy,nextTier,.72f);
        assignBlobId(born);
        Merge event=new Merge(cx,cy,born.radius(),nextTier,points,
                a.id,b.id,born.id,time);
        merges.add(event);
        if(contactProbe!=null)contactProbe.merged(event);
        if(spec!=null&&spec.endless) {
            int earned=(int)Math.min(2_000_000_000L,score/ERASE_EVERY_POINTS);
            if(earned>earnedErases) {eraseCount+=earned-earnedErases;earnedErases=earned;}
        }
        // Preserve some fluid motion through merging.
        for(int k=0;k<Blob.NODES;k++) {
            born.vx[k]=(a.vx[k]+b.vx[k])*.36f;
            born.vy[k]=(a.vy[k]+b.vy[k])*.36f-64f;
        }
        vessel.confine(born);
        blobs.add(born);
    }

    private boolean surfaceTouch(Blob a,Blob b,float tolerance) {
        for(int i=0;i<Blob.NODES;i++) {
            if(pointInside(b,a.px[i],a.py[i])||
                    closestEdge(b,a.px[i],a.py[i]).distance<tolerance) return true;
            if(pointInside(a,b.px[i],b.py[i])||
                    closestEdge(a,b.px[i],b.py[i]).distance<tolerance) return true;
        }
        return false;
    }

    private static final class Edge {
        int start; float t,distance,qx,qy;
    }

    static final class Bounds {
        float minX,maxX,minY,maxY;
        void set(Blob blob) {
            minX=maxX=blob.px[0];minY=maxY=blob.py[0];
            for(int i=1;i<Blob.NODES;i++)include(blob.px[i],blob.py[i]);
        }
        void include(float x,float y) {
            if(x<minX)minX=x;else if(x>maxX)maxX=x;
            if(y<minY)minY=y;else if(y>maxY)maxY=y;
        }
        boolean cannotTouch(float x,float y) {
            float margin=SKIN+.01f;
            return x<minX-margin||x>maxX+margin||y<minY-margin||y>maxY+margin;
        }
    }

    // Collision checks run sequentially on the UI thread. Reusing the
    // temporary result avoids thousands of short-lived objects per frame.
    private final Edge edgeScratch=new Edge();
    private final Bounds contactBoundsA=new Bounds(),contactBoundsB=new Bounds();

    private Edge closestEdge(Blob polygon,float x,float y) {
        Edge found=edgeScratch;
        float minSq=Float.MAX_VALUE;
        for(int j=0;j<Blob.NODES;j++) {
            int k=(j+1)%Blob.NODES;
            float ax=polygon.px[j],ay=polygon.py[j];
            float bx=polygon.px[k],by=polygon.py[k];
            // Distance to the segment's box is a lower bound on distance to
            // the segment itself. Skip projection only when it cannot win.
            float minX=ax<bx?ax:bx,maxX=ax>bx?ax:bx;
            float boxDx=x<minX?minX-x:(x>maxX?x-maxX:0f);
            float minY=ay<by?ay:by,maxY=ay>by?ay:by;
            float boxDy=y<minY?minY-y:(y>maxY?y-maxY:0f);
            if(boxDx*boxDx+boxDy*boxDy>minSq+1e-3f)continue;
            float dx=bx-ax,dy=by-ay;
            float len2=dx*dx+dy*dy;
            float t=len2<1e-6f?0f:clamp(((x-ax)*dx+(y-ay)*dy)/len2,0f,1f);
            float qx=ax+dx*t,qy=ay+dy*t;
            float d2=(x-qx)*(x-qx)+(y-qy)*(y-qy);
            if(d2<minSq) {
                minSq=d2;
                found.start=j;
                found.t=t;
                found.qx=qx;found.qy=qy;
            }
        }
        found.distance=(float)Math.sqrt(minSq);
        return found;
    }

    private static boolean pointInside(Blob p,float x,float y) {
        boolean inside=false;
        for(int i=0,j=Blob.NODES-1;i<Blob.NODES;j=i++) {
            float ax=p.px[i],ay=p.py[i],bx=p.px[j],by=p.py[j];
            if(((ay>y)!=(by>y))&&(x<(bx-ax)*(y-ay)/(by-ay)+ax))
                inside=!inside;
        }
        return inside;
    }

    private void projectContacts(Blob a,Blob b,boolean createAdhesion) {
        // Include every current vertex, then only expand as projections move
        // vertices. These bounds remain conservative throughout this pair.
        contactBoundsA.set(a);contactBoundsB.set(b);
        boolean touched=false;
        for(int i=0;i<Blob.NODES;i++) {
            if(pushPointFromPolygon(a,i,b,contactBoundsA,contactBoundsB,createAdhesion))touched=true;
            if(pushPointFromPolygon(b,i,a,contactBoundsB,contactBoundsA,createAdhesion))touched=true;
        }
        // Later solver passes may create the first actual contact; audio must
        // observe them too. Per-pair cooldown in recordImpact prevents repeats.
        if(touched)recordImpact(a,b);
    }

    private void recordImpact(Blob a,Blob b) {
        float dx=b.x-a.x,dy=b.y-a.y;
        float distance=(float)Math.hypot(dx,dy);
        if(distance<.001f)return;
        float avx=0f,avy=0f,bvx=0f,bvy=0f;
        for(int i=0;i<Blob.NODES;i++){
            avx+=a.vx[i];avy+=a.vy[i];
            bvx+=b.vx[i];bvy+=b.vy[i];
        }
        float approach=((avx-bvx)*dx+(avy-bvy)*dy)/(Blob.NODES*distance);
        if(!AudioPolicy.shouldPlayImpact(approach))return;
        long key=pairKey(a.id,b.id);
        Float previous=impactTimes.get(key);
        if(!AudioPolicy.allowPairImpactAt(time,previous))return;
        Impact event=new Impact((a.tier+b.tier)/2,approach);
        if(impacts.size()<6) {
            impacts.add(event);
        } else {
            // Keep the strongest six during a burst, not just the first six.
            int weakest=0;
            for(int i=1;i<impacts.size();i++)
                if(impacts.get(i).speed<impacts.get(weakest).speed)weakest=i;
            if(approach<=impacts.get(weakest).speed)return;
            impacts.set(weakest,event);
        }
        impactTimes.put(key,time);
        if(impactTimes.size()>400)
            impactTimes.entrySet().removeIf(e->time-e.getValue()>1.2f);
    }

    private boolean pushPointFromPolygon(Blob source,int i,Blob target,
            Bounds sourceBounds,Bounds targetBounds,boolean addLink) {
        float x=source.px[i],y=source.py[i];
        // A point outside the expanded target box can neither be inside the
        // polygon nor within contact distance of any of its edges.
        if(targetBounds.cannotTouch(x,y))return false;
        Edge edge=closestEdge(target,x,y);
        boolean inside=pointInside(target,x,y);
        if(!inside&&edge.distance>=SKIN)return false;

        int j=edge.start,k=(j+1)%Blob.NODES;
        float nx=x-edge.qx,ny=y-edge.qy;
        float dist=Math.max(.001f,edge.distance);
        nx/=dist;ny/=dist;
        if(inside){nx=-nx;ny=-ny;}
        if(edge.distance<.001f) {
            // For coincident boundaries use target's local segment normal.
            nx=target.py[j]-target.py[k];
            ny=target.px[k]-target.px[j];
            float len=(float)Math.hypot(nx,ny);
            if(len>.001f) { nx/=len; ny/=len; }
            else { nx=1f;ny=0f; }
        }
        float overlap=inside?edge.distance+SKIN:SKIN-edge.distance;
        // Observe pre-projection local speeds, direction and penetration. The
        // probe is passive and is disabled in the current shipping game.
        if(contactProbe!=null)
            contactProbe.observe(source,i,target,j,edge.t,nx,ny,overlap,
                    (x+edge.qx)*.5f,(y+edge.qy)*.5f);
        float shift=Math.min(7f,overlap)*.70f;
        source.px[i]+=nx*shift*.62f;
        source.py[i]+=ny*shift*.62f;
        // Split reaction across the two adjacent target particles. This
        // creates a broad indent, unlike a circular separation impulse.
        float weightA=1f-edge.t,weightB=edge.t;
        target.px[j]-=nx*shift*.38f*weightA;
        target.py[j]-=ny*shift*.38f*weightA;
        target.px[k]-=nx*shift*.38f*weightB;
        target.py[k]-=ny*shift*.38f*weightB;
        sourceBounds.include(source.px[i],source.py[i]);
        targetBounds.include(target.px[j],target.py[j]);
        targetBounds.include(target.px[k],target.py[k]);

        if(addLink&&!inside&&edge.distance<2.6f&&source.tier!=target.tier) {
            addAdhesion(source,target,i,edge.t<.5f?j:k);
        }
        return true;
    }

    private void addAdhesion(Blob a,Blob b,int ai,int bi) {
        int pairCount=0;
        for(Adhesion link:adhesions) {
            if((link.a==a&&link.b==b)||(link.a==b&&link.b==a)) {
                pairCount++;
                if(pairCount>=2)return; // avoid making a rigid glue seam
            }
        }
        adhesions.add(new Adhesion(a,b,ai,bi));
    }

    private void solveAdhesions() {
        for(Adhesion link:adhesions) {
            float dx=link.b.px[link.bi]-link.a.px[link.ai];
            float dy=link.b.py[link.bi]-link.a.py[link.ai];
            float length=(float)Math.hypot(dx,dy);
            if(length<=4f||length>26f)continue;
            // A brief slimy string stretches before releasing.
            float strength=Math.min(1.6f,(length-4f)*.15f);
            float fraction=strength/length;
            link.a.px[link.ai]+=dx*fraction*.5f;
            link.a.py[link.ai]+=dy*fraction*.5f;
            link.b.px[link.bi]-=dx*fraction*.5f;
            link.b.py[link.bi]-=dy*fraction*.5f;
        }
    }

    private void updateAdhesions(float dt) {
        adhesions.removeIf(link->{
            link.age+=dt;
            return !blobs.contains(link.a)||!blobs.contains(link.b)
                    ||link.age>.36f||link.length()>26f;
        });
    }

    static Impact strongestImpact(List<Impact> events) {
        Impact strongest=null;
        for(Impact event:events)
            if(strongest==null||event.speed>strongest.speed)strongest=event;
        return strongest;
    }

    List<Impact> consumeImpacts() {
        ArrayList<Impact> drained=new ArrayList<>(impacts);
        impacts.clear();
        return drained;
    }

    List<Merge> consumeMerges() {
        ArrayList<Merge> drained=new ArrayList<>(merges);
        merges.clear();
        return drained;
    }

    static float clamp(float value,float lo,float hi) {
        return Math.max(lo,Math.min(hi,value));
    }
}
