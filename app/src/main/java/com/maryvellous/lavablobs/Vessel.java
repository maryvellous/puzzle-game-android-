package com.maryvellous.lavablobs;

/**
 * Closed glass silhouette sampled into straight edges for both graphics and
 * physical contact; no rectangular/invisible collider around an irregular
 * container. All positions are in the same 420-unit virtual canvas.
 */
final class Vessel {
    final float[] xs,ys;
    final float top,bottom;
    final int shape;
    private static final int SEGMENTS=24;
    // Each vase is x-monotone with straight, sampled left/right sides.
    // A conservative interior corridor avoids expensive exact wall queries.
    private final float safeClearance;
    private final float[] edgeDx,edgeDy,edgeLengthSq,edgeMinX,edgeMaxX,edgeMinY,edgeMaxY;
    private final Closest closestScratch=new Closest();

    Vessel(int shape,float screenHeight) {
        this.shape=shape;
        if(shape==-1) { // Preserve v0.5 test fixture coordinates exactly.
            top=155f;
            bottom=Math.max(470f,screenHeight-153f);
        } else {
            // Fixed container height, NOT proportional to the phone's screen.
            // The vessel and launcher are centred together below the HUD.
            float playableTop=208f, playableBottom=screenHeight-22f;
            float launcherSpace=124f;
            float free=playableBottom-playableTop;
            float jarHeight=Math.min(desiredHeight(shape),free-launcherSpace);
            jarHeight=Math.max(220f,jarHeight);
            top=playableTop+(free-(jarHeight+launcherSpace))*.5f;
            bottom=top+jarHeight;
        }
        float[] x=new float[(SEGMENTS+1)*2];
        float[] y=new float[x.length];
        float mid=210f;
        for(int i=0;i<=SEGMENTS;i++) {
            float t=i/(float)SEGMENTS;
            float yy=top+(bottom-top)*t;
            float half=halfWidth(shape,t);
            float offset=offset(shape,t);
            x[i]=mid+offset+half; y[i]=yy; // clockwise right side
        }
        for(int i=0;i<=SEGMENTS;i++) {
            float t=1f-i/(float)SEGMENTS;
            float yy=top+(bottom-top)*t;
            float half=halfWidth(shape,t);
            float offset=offset(shape,t);
            x[SEGMENTS+1+i]=mid+offset-half; y[SEGMENTS+1+i]=yy;
        }
        xs=x;ys=y;
        int n=xs.length;
        edgeDx=new float[n];edgeDy=new float[n];edgeLengthSq=new float[n];
        edgeMinX=new float[n];edgeMaxX=new float[n];edgeMinY=new float[n];edgeMaxY=new float[n];
        for(int i=0;i<n;i++){
            int k=(i+1)%n;
            float dx=xs[k]-xs[i],dy=ys[k]-ys[i];
            edgeDx[i]=dx;edgeDy[i]=dy;edgeLengthSq[i]=dx*dx+dy*dy;
            edgeMinX[i]=Math.min(xs[i],xs[k]);edgeMaxX[i]=Math.max(xs[i],xs[k]);
            edgeMinY[i]=Math.min(ys[i],ys[k]);edgeMaxY[i]=Math.max(ys[i],ys[k]);
        }
        float maxSlope=0f;
        for(int i=0;i<xs.length-1;i++){
            float dy=Math.abs(ys[i+1]-ys[i]);
            if(dy>.001f)maxSlope=Math.max(maxSlope,Math.abs(xs[i+1]-xs[i])/dy);
        }
        // Minimum horizontal margin guaranteeing >0.8 world units of
        // Euclidean distance from even the steepest side segment.
        safeClearance=.8f*(float)Math.sqrt(1f+maxSlope*maxSlope)+2f;
    }

    // Shape heights are in game-world units and remain consistent on phones
    // with different screen aspect ratios; the canvas is responsible for scale.
    static float desiredHeight(int kind) {
        switch(kind) {
            case -1:return 0f;
            case 0:return 340f;
            case 1:return 360f;
            case 2:return 325f;
            case 3:return 355f;
            case 4:return 340f;
            case 5:return 350f;
            case 6:return 360f;
            case 7:return 320f;
            case 8:return 370f;
            case 9:return 370f;
            case 10:return 340f;
            case 11:return 285f; // comparison A
            case 12:return 340f; // comparison B
            case 13:return 400f; // comparison C
            default:return 340f;
        }
    }

    // shape -1 = old prototype glass for physics regression tests.
    private static float halfWidth(int kind,float t) {
        float bell=(float)Math.sin(Math.PI*t);
        switch(kind) {
            case -1:return 181f;
            case 0:case 11:case 12:case 13:return 138f-14f*bell;
            // Strong silhouettes, similar overall volume: gameplay variety
            // comes from necks, lobes and asymmetry, not from huge vessels.
            case 1:return 126f+29f*bell-37f*bump(t,.30f,.11f);
            case 2:return 154f-39f*t+11f*(float)Math.sin(2*Math.PI*t);
            case 3:return 138f-59f*bump(t,.52f,.16f);
            case 4:return 109f+43f*bell-9f*(float)Math.cos(3*Math.PI*t);
            case 5:return 132f+25f*(float)Math.sin(4*Math.PI*t+.18f);
            case 6:return 96f+60f*bell*bell;
            case 7:return 128f-30f*t+32f*bell*bell;
            case 8:return 137f+17f*bell-30f*bump(t,.74f,.12f);
            case 9:return 132f-45f*bump(t,.28f,.12f)-43f*bump(t,.70f,.11f);
            case 10:return 127f+24f*(float)Math.sin(3*Math.PI*t+.35f)
                              +14f*(float)Math.sin(5*Math.PI*t);
            default:return 125f;
        }
    }
    private static float offset(int kind,float t) {
        if(kind==1)return 11f*(float)Math.sin(Math.PI*t);
        if(kind==3)return 12f*(float)Math.sin(2*Math.PI*t);
        if(kind==4)return -9f*(float)Math.sin(Math.PI*t);
        if(kind==5)return 20f*(float)Math.sin(3*Math.PI*t);
        if(kind==6)return 10f*(float)Math.sin(Math.PI*t);
        if(kind==8)return -22f*(float)Math.sin(Math.PI*t);
        if(kind==9)return 18f*(float)Math.sin(2*Math.PI*t);
        if(kind==10)return 20f*(float)Math.sin(2*Math.PI*t+.35f);
        return 0f;
    }

    private static float bump(float t,float center,float spread) {
        float d=(t-center)/spread;
        return (float)Math.exp(-d*d);
    }

    boolean contains(float x,float y) {
        boolean inside=false;
        for(int i=0,j=xs.length-1;i<xs.length;j=i++) {
            if((ys[i]>y)!=(ys[j]>y)) {
                if(x<(xs[j]-xs[i])*(y-ys[i])/(ys[j]-ys[i])+xs[i])inside=!inside;
            }
        }
        return inside;
    }

    private static final class Closest {
        float x,y,distance;
        int edge;
    }
    private Closest closest(float x,float y) {
        Closest result=closestScratch;
        float d2Min=Float.MAX_VALUE;
        for(int i=0;i<xs.length;i++) {
            // A segment's box gives a conservative lower bound; retain edge
            // traversal order, strict comparison and original projection math.
            float boxDx=x<edgeMinX[i]?edgeMinX[i]-x:(x>edgeMaxX[i]?x-edgeMaxX[i]:0f);
            float boxDy=y<edgeMinY[i]?edgeMinY[i]-y:(y>edgeMaxY[i]?y-edgeMaxY[i]:0f);
            if(boxDx*boxDx+boxDy*boxDy>d2Min+1e-3f)continue;
            float dx=edgeDx[i],dy=edgeDy[i],len=edgeLengthSq[i];
            float t=len<1e-5f?0f:Blob.clamp(
                    ((x-xs[i])*dx+(y-ys[i])*dy)/len,0f,1f);
            float qx=xs[i]+dx*t,qy=ys[i]+dy*t;
            float ddx=x-qx,ddy=y-qy,d2=ddx*ddx+ddy*ddy;
            if(d2<d2Min){d2Min=d2;result.x=qx;result.y=qy;result.edge=i;}
        }
        result.distance=(float)Math.sqrt(d2Min);
        return result;
    }

    boolean fits(float cx,float cy,float r) {
        if(!contains(cx,cy))return false;
        return closest(cx,cy).distance>=r+.6f;
    }

    private boolean safelyInside(float x,float y) {
        if(y<=top+safeClearance||y>=bottom-safeClearance)return false;
        float position=(y-top)*SEGMENTS/(bottom-top);
        int band=Math.min(SEGMENTS-1,(int)position);
        float t=position-band;
        float right=xs[band]+(xs[band+1]-xs[band])*t;
        int leftIndex=xs.length-1-band;
        float left=xs[leftIndex]+(xs[leftIndex-1]-xs[leftIndex])*t;
        return x>left+safeClearance&&x<right-safeClearance;
    }

    void confine(Blob blob) {confine(blob,null);}

    void confine(Blob blob,ContactProbe probe) {
        boolean bulkMeasured=false;
        float bulkVx=0f,bulkVy=0f;
        for(int i=0;i<Blob.NODES;i++) {
            float x=blob.px[i],y=blob.py[i];
            // Exact no-op for particles provably farther than contact distance
            // from every wall, including concave shapes. The original collision
            // solver remains responsible for everything near an edge.
            if(safelyInside(x,y))continue;
            boolean inside=contains(x,y);
            Closest wall=closest(x,y);
            if(inside && wall.distance>=.8f)continue;
            // Push real membrane vertices inward from the nearest polygon edge.
            float dx=x-wall.x,dy=y-wall.y;
            float length=(float)Math.hypot(dx,dy);
            if(length<.0001f){
                // The centre is inside for playable shapes; this is a
                // stable fallback for a coincident wall/vertex.
                dx=blob.x-wall.x;dy=blob.y-wall.y;
                length=(float)Math.hypot(dx,dy);
            }
            if(length<.001f){dx=210f-wall.x;dy=(top+bottom)*.5f-wall.y;length=(float)Math.hypot(dx,dy);}
            if(length<.001f)continue;
            float direction=inside?1f:-1f;
            if(probe!=null){
                int edge=wall.edge;
                int side=edge<SEGMENTS?0:edge==SEGMENTS?1:edge<xs.length-1?2:3;
                if(!bulkMeasured){
                    for(int node=0;node<Blob.NODES;node++){bulkVx+=blob.vx[node];bulkVy+=blob.vy[node];}
                    bulkMeasured=true;
                }
                probe.observeWall(blob,side,dx/length*direction,dy/length*direction,bulkVx,bulkVy);
            }
            blob.px[i]=wall.x+dx/length*direction*.85f;
            blob.py[i]=wall.y+dy/length*direction*.85f;
        }
    }
}
