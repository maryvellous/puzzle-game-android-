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

    Vessel(int shape,float screenHeight) {
        this.shape=shape;
        bottom=shape==-1?Math.max(470f,screenHeight-153f):
                Math.max(430f,screenHeight-187f);
        top=shape==-1?155f:Math.min(211f,bottom-305f);
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
    }

    // shape -1 = old prototype glass for physics regression tests.
    private static float halfWidth(int kind,float t) {
        float bell=(float)Math.sin(Math.PI*t);
        switch(kind) {
            case -1:return 181f;
            case 0:return 138f-14f*bell;
            case 1:return 105f+41f*bell-10f*t;
            case 2:return 154f-50f*t-9f*bell;
            case 3:return 139f-44f*(float)Math.pow(Math.sin(Math.PI*t),2);
            case 4:return 116f+23f*bell-13f*t;
            case 5:return 128f+13f*(float)Math.sin(Math.PI*2*t+.5f);
            case 6:return 99f+49f*bell;
            case 7:return 108f-18f*bell;
            case 8:return 121f+24f*bell+6f*t;
            case 9:return 132f-29f*(float)Math.pow(Math.sin(Math.PI*t),4);
            case 10:return 119f+19f*(float)Math.sin(3*Math.PI*t);
            default:return 125f;
        }
    }
    private static float offset(int kind,float t) {
        if(kind==5)return 17f*(float)Math.sin(t*2.0*Math.PI);
        if(kind==10)return 16f*(float)Math.sin(t*Math.PI*2.0+.5);
        if(kind==8)return -14f*(float)Math.sin(Math.PI*t);
        return 0f;
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
    }
    private Closest closest(float x,float y) {
        Closest result=new Closest();
        float d2Min=Float.MAX_VALUE;
        for(int i=0;i<xs.length;i++) {
            int k=(i+1)%xs.length;
            float dx=xs[k]-xs[i],dy=ys[k]-ys[i];
            float len=dx*dx+dy*dy;
            float t=len<1e-5f?0f:Blob.clamp(
                    ((x-xs[i])*dx+(y-ys[i])*dy)/len,0f,1f);
            float qx=xs[i]+dx*t,qy=ys[i]+dy*t;
            float ddx=x-qx,ddy=y-qy,d2=ddx*ddx+ddy*ddy;
            if(d2<d2Min){d2Min=d2;result.x=qx;result.y=qy;}
        }
        result.distance=(float)Math.sqrt(d2Min);
        return result;
    }

    boolean fits(float cx,float cy,float r) {
        if(!contains(cx,cy))return false;
        return closest(cx,cy).distance>=r+.6f;
    }

    void confine(Blob blob) {
        for(int i=0;i<Blob.NODES;i++) {
            float x=blob.px[i],y=blob.py[i];
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
            blob.px[i]=wall.x+dx/length*direction*.85f;
            blob.py[i]=wall.y+dy/length*direction*.85f;
        }
    }
}
