package com.maryvellous.lavablobs;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** The fast interior rejection must never change the exact wall response. */
public class VesselFastPathTest {
    @Test public void fastInteriorAndExactWallSolverMatchAcrossAllShapes() {
        for (int shape=-1;shape<=13;shape++) {
            Vessel vessel=new Vessel(shape,850f);
            float height=vessel.bottom-vessel.top;
            for (int row=0;row<=10;row++) {
                float y=vessel.top-2f+(height+4f)*row/10f;
                for (int col=0;col<=12;col++) {
                    float x=30f+30f*col;
                    Blob optimized=new Blob(210f,420f,0,1f);
                    Blob reference=new Blob(210f,420f,0,1f);
                    for(int i=0;i<Blob.NODES;i++) {
                        optimized.px[i]=reference.px[i]=x;
                        optimized.py[i]=reference.py[i]=y;
                    }
                    vessel.confine(optimized);
                    originalConfine(vessel,reference);
                    for(int i=0;i<Blob.NODES;i++) {
                        String label="vessel "+shape+" x="+x+" y="+y+" node="+i;
                        assertEquals(label,reference.px[i],optimized.px[i],.0001f);
                        assertEquals(label,reference.py[i],optimized.py[i],.0001f);
                    }
                }
            }
        }
    }

    /** The unchanged pre-optimization implementation, kept as a test oracle. */
    private static void originalConfine(Vessel vessel,Blob blob) {
        for(int i=0;i<Blob.NODES;i++) {
            float x=blob.px[i],y=blob.py[i];
            boolean inside=vessel.contains(x,y);
            float nearestX=0f,nearestY=0f;
            float minSq=Float.MAX_VALUE;
            for(int j=0;j<vessel.xs.length;j++) {
                int k=(j+1)%vessel.xs.length;
                float dx=vessel.xs[k]-vessel.xs[j];
                float dy=vessel.ys[k]-vessel.ys[j];
                float len=dx*dx+dy*dy;
                float t=len<1e-5f?0f:Blob.clamp(
                        ((x-vessel.xs[j])*dx+(y-vessel.ys[j])*dy)/len,0f,1f);
                float qx=vessel.xs[j]+dx*t,qy=vessel.ys[j]+dy*t;
                float ddx=x-qx,ddy=y-qy,distSq=ddx*ddx+ddy*ddy;
                if(distSq<minSq){minSq=distSq;nearestX=qx;nearestY=qy;}
            }
            float distance=(float)Math.sqrt(minSq);
            if(inside&&distance>=.8f)continue;
            float dx=x-nearestX,dy=y-nearestY;
            float length=(float)Math.hypot(dx,dy);
            if(length<.0001f) {
                dx=blob.x-nearestX;dy=blob.y-nearestY;
                length=(float)Math.hypot(dx,dy);
            }
            if(length<.001f) {
                dx=210f-nearestX;dy=(vessel.top+vessel.bottom)*.5f-nearestY;
                length=(float)Math.hypot(dx,dy);
            }
            if(length<.001f)continue;
            float direction=inside?1f:-1f;
            blob.px[i]=nearestX+dx/length*direction*.85f;
            blob.py[i]=nearestY+dy/length*direction*.85f;
        }
    }
}
