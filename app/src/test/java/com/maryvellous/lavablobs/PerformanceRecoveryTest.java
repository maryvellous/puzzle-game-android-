package com.maryvellous.lavablobs;

import org.junit.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Random;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class PerformanceRecoveryTest {
    private static boolean oldContains(Vessel v,float x,float y){
        boolean inside=false;
        for(int i=0,j=v.xs.length-1;i<v.xs.length;j=i++)
            if((v.ys[i]>y)!=(v.ys[j]>y))
                if(x<(v.xs[j]-v.xs[i])*(y-v.ys[i])/(v.ys[j]-v.ys[i])+v.xs[i])inside=!inside;
        return inside;
    }

    @Test public void binaryRayMatchesOriginalAtVerticesAndRandomPoints(){
        Random r=new Random(20261128L);
        for(float h:new float[]{680,850,1080})for(int shape=-1;shape<=25;shape++){
            Vessel v=new Vessel(shape,h);
            for(int i=0;i<300;i++){
                float x=10+r.nextFloat()*400,y=v.top-2+r.nextFloat()*(v.bottom-v.top+4);
                assertEquals(oldContains(v,x,y),v.contains(x,y));
            }
            for(int i=0;i<v.xs.length;i++)for(float dy:new float[]{-0.001f,0f,.001f})
                for(float dx:new float[]{-.001f,0f,.001f})
                    assertEquals(oldContains(v,v.xs[i]+dx,v.ys[i]+dy),v.contains(v.xs[i]+dx,v.ys[i]+dy));
        }
    }

    @Test public void boundedEdgeSearchPreservesEveryAcceptedContact() throws Exception{
        BlobWorld w=new BlobWorld(new Random(1));
        Method m=BlobWorld.class.getDeclaredMethod("closestEdge",Blob.class,float.class,float.class,float.class);
        m.setAccessible(true);
        Field d=m.getReturnType().getDeclaredField("distance"),e=m.getReturnType().getDeclaredField("start");
        d.setAccessible(true);e.setAccessible(true);
        Random r=new Random(91);
        for(int shape=0;shape<30;shape++){
            Blob b=new Blob(210,400,shape%9,new Random(shape));
            for(int n=0;n<Blob.NODES;n++){b.px[n]+=(r.nextFloat()-.5f)*25;b.py[n]+=(r.nextFloat()-.5f)*25;}
            for(int i=0;i<300;i++){
                int n=r.nextInt(Blob.NODES);
                float x=b.px[n]+(r.nextFloat()-.5f)*18,y=b.py[n]+(r.nextFloat()-.5f)*18;
                Object full=m.invoke(w,b,x,y,Float.POSITIVE_INFINITY);
                float distance=d.getFloat(full);int edge=e.getInt(full);
                for(float cut:new float[]{2.6f,3f}){
                    Object limited=m.invoke(w,b,x,y,cut);
                    assertEquals(distance<cut,d.getFloat(limited)<cut);
                    if(distance<cut){assertEquals(distance,d.getFloat(limited),0f);assertEquals(edge,e.getInt(limited));}
                }
            }
        }
    }

    @Test public void captureDoesNotReadMutatedWorldWhenEncoded() throws Exception{
        GameSpec spec=GameSpec.LEVELS[0];
        BlobWorld w=new BlobWorld(new Random(23),spec);
        w.launch(210);w.tick(1f/60f);
        JSONObject expected=GameSave.encode(spec,w,210,false,4f);
        Class<?> cls=Class.forName("com.maryvellous.lavablobs.GameSave$Capture");
        Constructor<?> ctor=cls.getDeclaredConstructor(GameSpec.class,BlobWorld.class,float.class,boolean.class,float.class);
        ctor.setAccessible(true);Object capture=ctor.newInstance(spec,w,210f,false,4f);
        w.tick(1f/60f);w.random.nextLong();w.reset();
        Method json=cls.getDeclaredMethod("json");json.setAccessible(true);
        JSONObject actual=(JSONObject)json.invoke(capture);
        assertEquals(expected.toString(),actual.toString());
        BlobWorld restored=new BlobWorld(new Random(0),spec);
        GameSave.restore(restored,actual);
        assertEquals(expected.getInt("drops"),restored.dropCount);
        assertEquals(expected.getJSONArray("blobs").length(),restored.blobs.size());
    }

    @Test public void profilingDoesNotChangeRapidLaunchSimulation() throws Exception{
        GameSpec spec=GameSpec.LEVELS[0];
        BlobWorld a=new BlobWorld(new Random(884),spec),b=new BlobWorld(new Random(884),spec);
        b.setProfiling(true);
        for(int tick=0;tick<180;tick++){
            if(tick%6==0){float x=100+(tick%5)*55;assertEquals(a.launch(x),b.launch(x));}
            a.tick(1f/60f);b.tick(1f/60f);
        }
        assertEquals(GameSave.encode(spec,a,210,false).toString(),GameSave.encode(spec,b,210,false).toString());
        assertTrue(b.physicsProfile().contains("contacts+probe"));
    }
}
