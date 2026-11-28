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
    private static final int LEGACY_SEGMENTS=24;
    private static final int ROUNDED_SEGMENTS=80;
    final int segments;
    // Each vase is x-monotone with straight, sampled left/right sides.
    // A conservative interior corridor avoids expensive exact wall queries.
    private final float safeClearance;
    private final float[] edgeDx,edgeDy,edgeLengthSq,edgeMinX,edgeMaxX,edgeMinY,edgeMaxY;
    private final Closest closestScratch=new Closest();
    private static final int EDGE_GROUP=8;
    private final float[] groupMinX,groupMaxX,groupMinY,groupMaxY;
    private final float[] innerLeft,innerRight;

    Vessel(int shape,float screenHeight) {
        this.shape=shape;
        segments=shape>=14?ROUNDED_SEGMENTS:LEGACY_SEGMENTS;
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
        float[] x=new float[(segments+1)*2];
        float[] y=new float[x.length];
        float mid=210f;
        for(int i=0;i<=segments;i++) {
            float t=i/(float)segments;
            float yy=top+(bottom-top)*t;
            float half=halfWidth(shape,t);
            if(shape>=14)half-=cornerInset(t*(bottom-top),bottom-top,halfWidth(shape,0),halfWidth(shape,1));
            float offset=offset(shape,t);
            x[i]=mid+offset+half; y[i]=yy; // clockwise right side
        }
        for(int i=0;i<=segments;i++) {
            float t=1f-i/(float)segments;
            float yy=top+(bottom-top)*t;
            float half=halfWidth(shape,t);
            if(shape>=14)half-=cornerInset(t*(bottom-top),bottom-top,halfWidth(shape,0),halfWidth(shape,1));
            float offset=offset(shape,t);
            x[segments+1+i]=mid+offset-half; y[segments+1+i]=yy;
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
        if(shape>=14){
            int groups=(n+EDGE_GROUP-1)/EDGE_GROUP;
            groupMinX=new float[groups];groupMaxX=new float[groups];groupMinY=new float[groups];groupMaxY=new float[groups];
            for(int g=0;g<groups;g++){
                float lx=Float.POSITIVE_INFINITY,ly=lx,rx=Float.NEGATIVE_INFINITY,ry=rx;
                for(int e=g*EDGE_GROUP;e<Math.min(n,(g+1)*EDGE_GROUP);e++){
                    lx=Math.min(lx,edgeMinX[e]);rx=Math.max(rx,edgeMaxX[e]);
                    ly=Math.min(ly,edgeMinY[e]);ry=Math.max(ry,edgeMaxY[e]);
                }
                groupMinX[g]=lx;groupMaxX[g]=rx;groupMinY[g]=ly;groupMaxY[g]=ry;
            }
            // A conservative interior box for each horizontal band. Include
            // neighbouring segments within 0.8 units vertically; the margin
            // guarantees that the exact wall query would be a no-op.
            innerLeft=new float[segments];innerRight=new float[segments];
            for(int band=0;band<segments;band++){
                float lo=ys[band]-.82f,hi=ys[band+1]+.82f;
                float left=Float.NEGATIVE_INFINITY,right=Float.POSITIVE_INFINITY;
                for(int e=0;e<n;e++)if(edgeMaxY[e]>=lo&&edgeMinY[e]<=hi){
                    if(e<segments)right=Math.min(right,edgeMinX[e]);
                    else if(e>segments&&e<n-1)left=Math.max(left,edgeMaxX[e]);
                }
                innerLeft[band]=left+.82f;innerRight[band]=right-.82f;
            }
        }else{groupMinX=groupMaxX=groupMinY=groupMaxY=innerLeft=innerRight=null;}
        float maxSlope=0f;
        for(int i=0;i<xs.length-1;i++){
            float dy=Math.abs(ys[i+1]-ys[i]);
            if(dy>.001f)maxSlope=Math.max(maxSlope,Math.abs(xs[i+1]-xs[i])/dy);
        }
        // Minimum horizontal margin guaranteeing >0.8 world units of
        // Euclidean distance from even the steepest side segment.
        safeClearance=.8f*(float)Math.sqrt(1f+maxSlope*maxSlope)+2f;
    }

    /**
     * Elliptical end-cap fillets, sampled into the actual physical polygon.
     * Keep a flat inlet and a wide roof, but join their ends with curved
     * shoulders. The y samples stay uniform, so the conservative interior
     * corridor and original exact wall response remain valid.
     */
    private static float cornerInset(float y,float height,float topHalf,float bottomHalf){
        final float ry=Math.min(54f,height*.19f);
        float dist=Math.min(y,height-y);
        if(dist>=ry)return 0f;
        float rx=Math.min(58f,(y<height*.5f?topHalf:bottomHalf)*.49f);
        float u=1f-Math.max(0f,dist)/ry;
        return rx*(1f-(float)Math.sqrt(Math.max(0f,1f-u*u)));
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
            case 14:return 340f;
            case 15:return 325f;
            case 16:return 360f;
            case 17:return 355f;
            case 18:return 340f;
            case 19:return 350f;
            case 20:return 360f;
            case 21:return 355f;
            case 22:return 320f;
            case 23:return 370f;
            case 24:return 370f;
            case 25:return 340f;
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
            // New rounded single-contour profiles. End caps remain wide enough
            // for all five initial drop sizes; the same sampled edges draw and collide.
            case 14:return 105f+43f*(float)Math.sin(Math.PI*(.15f+.85f*t));
            case 15:return 94f+68f*(float)Math.sin(Math.PI*(.17f+.83f*t));
            case 16:return 73f+91f*bump(t,.34f,.42f);
            case 17:return 88f+74f*bump(t,.33f,.33f);
            case 18:return 78f+73f*bell;
            case 19:return 119f+30f*bell+20f*(float)Math.sin(4*Math.PI*t);
            case 20:return 78f+90f*(float)Math.pow(bell,.7);
            case 21:return 90f+70f*(float)Math.pow(Math.cos(Math.PI*t),2);
            case 22:return 77f+82f*bump(t,.48f,.37f);
            case 23:return 80f+83f*(float)Math.pow(bell,.8);
            case 24:return 100f+50f*bell-25f*bump(t,.48f,.13f);
            case 25:return 84f+66f*bell+20f*(float)Math.sin(3*Math.PI*t);
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
        if(kind==19)return 19f*(float)Math.sin(2*Math.PI*t);
        if(kind==22)return 18f*bell(t);
        if(kind==23)return -31f*bell(t);
        if(kind==24)return 25f*(float)Math.sin(2*Math.PI*t);
        if(kind==25)return -24f*(float)Math.sin(Math.PI*t);
        return 0f;
    }
    private static float bell(float t){return (float)Math.sin(Math.PI*t);}

    private static float bump(float t,float center,float spread) {
        float d=(t-center)/spread;
        return (float)Math.exp(-d*d);
    }

    boolean contains(float x,float y) {
        if(!(y>=top&&y<bottom))return false;
        // Both sides are monotonic in y. Locate the only two crossings
        // with the same strict comparison and arithmetic as the ray loop.
        int lo=1,hi=segments;
        while(lo<hi){int mid=(lo+hi)>>>1;if(ys[mid]>y)hi=mid;else lo=mid+1;}
        int right=lo;
        lo=segments+2;hi=ys.length-1;
        while(lo<hi){int mid=(lo+hi)>>>1;if(ys[mid]<=y)hi=mid;else lo=mid+1;}
        int left=lo;
        return crossesRay(right,x,y)^crossesRay(left,x,y);
    }

    private boolean crossesRay(int i,float x,float y){
        int j=i-1;
        return ((ys[i]>y)!=(ys[j]>y))&&
                x<(xs[j]-xs[i])*(y-ys[i])/(ys[j]-ys[i])+xs[i];
    }

    private static final class Closest {
        float x,y,distance;
        int edge;
    }
    private Closest closest(float x,float y) {
        Closest result=closestScratch;
        float d2Min=Float.MAX_VALUE;
        for(int i=0;i<xs.length;i++) {
            // Skip groups only when their enclosing rectangle is already
            // farther away than the best edge. Traversal/ties stay identical.
            if(groupMinX!=null&&i%EDGE_GROUP==0){
                int g=i/EDGE_GROUP;
                float gx=x<groupMinX[g]?groupMinX[g]-x:(x>groupMaxX[g]?x-groupMaxX[g]:0f);
                float gy=y<groupMinY[g]?groupMinY[g]-y:(y>groupMaxY[g]?y-groupMaxY[g]:0f);
                if(gx*gx+gy*gy>d2Min+1e-3f){i=Math.min(xs.length,(g+1)*EDGE_GROUP)-1;continue;}
            }
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
        if(innerLeft!=null){
            if(y<=top+.82f||y>=bottom-.82f)return false;
            int band=Math.max(0,Math.min(segments-1,(int)((y-top)*segments/(bottom-top))));
            // Guard rounding at band boundaries by checking actual y samples.
            while(band>0&&y<ys[band])band--;
            while(band<segments-1&&y>ys[band+1])band++;
            return x>innerLeft[band]&&x<innerRight[band];
        }
        if(y<=top+safeClearance||y>=bottom-safeClearance)return false;
        float position=(y-top)*segments/(bottom-top);
        int band=Math.min(segments-1,(int)position);
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
                int side=edge<segments?0:edge==segments?1:edge<xs.length-1?2:3;
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
