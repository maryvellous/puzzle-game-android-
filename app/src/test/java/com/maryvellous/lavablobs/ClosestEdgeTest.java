package com.maryvellous.lavablobs;

import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Checks that the segment-box shortcut preserves the original exact query. */
public class ClosestEdgeTest {
    @Test public void prunedSearchMatchesEverySegmentSearch() throws Exception {
        BlobWorld world=new BlobWorld(new Random(1));
        Method closest=BlobWorld.class.getDeclaredMethod("closestEdge",Blob.class,float.class,float.class);
        closest.setAccessible(true);
        Class<?> edgeClass=closest.getReturnType();
        Field start=edgeClass.getDeclaredField("start");start.setAccessible(true);
        Field t=edgeClass.getDeclaredField("t");t.setAccessible(true);
        Field qx=edgeClass.getDeclaredField("qx");qx.setAccessible(true);
        Field qy=edgeClass.getDeclaredField("qy");qy.setAccessible(true);
        Field distance=edgeClass.getDeclaredField("distance");distance.setAccessible(true);
        Random random=new Random(1729);
        for(int shape=0;shape<40;shape++) {
            Blob polygon=new Blob(210,400,shape%9,new Random(shape));
            for(int i=0;i<Blob.NODES;i++) {
                polygon.px[i]+=(random.nextFloat()-.5f)*35f;
                polygon.py[i]+=(random.nextFloat()-.5f)*35f;
            }
            for(int sample=0;sample<40;sample++) {
                float x=50f+random.nextFloat()*320f;
                float y=220f+random.nextFloat()*360f;
                Edge expected=bruteForce(polygon,x,y);
                Object actual=closest.invoke(world,polygon,x,y);
                assertEquals(expected.start,start.getInt(actual));
                assertEquals(expected.t,t.getFloat(actual),1e-5f);
                assertEquals(expected.qx,qx.getFloat(actual),1e-4f);
                assertEquals(expected.qy,qy.getFloat(actual),1e-4f);
                assertEquals(expected.distance,distance.getFloat(actual),1e-4f);
            }
        }
    }

    @Test public void expandedContactBoundsNeverSkipNearbyEdges() {
        Random random=new Random(802);
        BlobWorld.Bounds bounds=new BlobWorld.Bounds();
        for(int shape=0;shape<40;shape++) {
            Blob polygon=new Blob(210,400,shape%9,new Random(shape));
            for(int i=0;i<Blob.NODES;i++) {
                polygon.px[i]+=(random.nextFloat()-.5f)*35f;
                polygon.py[i]+=(random.nextFloat()-.5f)*35f;
            }
            bounds.set(polygon);
            polygon.px[shape%Blob.NODES]+=25f;
            bounds.include(polygon.px[shape%Blob.NODES],polygon.py[shape%Blob.NODES]);
            for(int sample=0;sample<120;sample++) {
                float x=50f+random.nextFloat()*320f;
                float y=220f+random.nextFloat()*360f;
                if(bounds.cannotTouch(x,y))
                    assertTrue(bruteForce(polygon,x,y).distance>=3f);
            }
        }
    }

    private static Edge bruteForce(Blob polygon,float x,float y) {
        Edge found=new Edge();
        float minSq=Float.MAX_VALUE;
        for(int j=0;j<Blob.NODES;j++) {
            int k=(j+1)%Blob.NODES;
            float ax=polygon.px[j],ay=polygon.py[j];
            float dx=polygon.px[k]-ax,dy=polygon.py[k]-ay;
            float len2=dx*dx+dy*dy;
            float t=len2<1e-6f?0f:BlobWorld.clamp(((x-ax)*dx+(y-ay)*dy)/len2,0f,1f);
            float qx=ax+dx*t,qy=ay+dy*t;
            float d2=(x-qx)*(x-qx)+(y-qy)*(y-qy);
            if(d2<minSq) {
                minSq=d2;found.start=j;found.t=t;found.qx=qx;found.qy=qy;
            }
        }
        found.distance=(float)Math.sqrt(minSq);
        return found;
    }

    private static final class Edge {
        int start;
        float t,qx,qy,distance;
    }
}
