package com.maryvellous.lavablobs;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Deterministic offline acoustic-control trace from the actual Java solver.
 * Test artifact ONLY: no runtime logging, audio renderer or Android output.
 */
public class ContactProbeTraceTest {
    private static final float DT=1f/60f;

    private static Blob blob(float x,float y,int tier) {
        return new Blob(x,y,tier,new Random(3));
    }

    private static void speed(Blob b,float vx,float vy) {
        for(int i=0;i<Blob.NODES;i++){b.vx[i]=vx;b.vy[i]=vy;}
    }

    private static void write(PrintWriter csv,String scenario,String kind,float time,
            float dt,int a,int b,int result,ContactProbe.Contact c,float age) {
        csv.printf(Locale.US,
                "%s,%s,%.6f,%.6f,%d,%d,%d,%.4f,%.4f,%.5f,%.5f,%.4f,%.4f,%.6f,%.3f,%.3f,%d,%.5f%n",
                scenario,kind,time,dt,a,b,result,
                c==null?0f:c.x,c==null?0f:c.y,
                c==null?0f:c.nx,c==null?0f:c.ny,
                c==null?0f:c.approachSpeed,c==null?0f:c.slideSpeed,
                c==null?0f:c.compression,c==null?0f:c.radiusA,
                c==null?0f:c.radiusB,c==null?0:c.projectionSamples,age);
    }

    /**
     * Capture exactly the simulated 24 membrane nodes, without rebuilding
     * circles or inventing motion for the synchronized video preview.
     * `frame=0` precedes the first tick; frame N uses world.time after tick N.
     */
    private static void writeMerge(PrintWriter csv,String scenario,BlobWorld.Merge merge) {
        // Keep schema stable and encode the TRUE resulting radius for pitch.
        csv.printf(Locale.US,
                "%s,merge,%.6f,0,%d,%d,%d,0,0,0,0,0,0,0,%.4f,0,0,0%n",
                scenario,merge.simulationTime,merge.parentAId,merge.parentBId,
                merge.resultId,merge.radius);
    }

    private static void writeMesh(PrintWriter mesh,String scenario,int frame,BlobWorld world) {
        for(Blob blob:world.blobs) {
            for(int node=0;node<Blob.NODES;node++) {
                mesh.printf(Locale.US,"%s,%d,%.8f,%d,%d,%d,%.6f,%.6f%n",
                        scenario,frame,world.time,blob.id,blob.tier,node,
                        blob.px[node],blob.py[node]);
            }
        }
    }

    private static void writeMeshHeader(PrintWriter mesh) {
        mesh.println("scenario,frame,time,blob_id,tier,node,x,y");
    }

    private static void run(PrintWriter csv,PrintWriter mesh,String name,long seed) {
        BlobWorld world=new BlobWorld(new Random(seed));
        Blob a,b;
        switch(name) {
            case "impact":
                a=blob(210,350,2);b=blob(210,422,1);
                speed(a,0,0);speed(b,0,-310);break;
            case "slide":
            case "release":
                a=blob(181,386,2);b=blob(239,386,1);
                speed(a,0,100);speed(b,0,-100);break;
            case "pressure":
                a=blob(210,350,2);b=blob(210,405,1);
                speed(a,0,0);speed(b,0,-85);break;
            case "fusion":
                a=blob(170,400,0);b=blob(204,400,0);
                break;
            default: throw new IllegalArgumentException(name);
        }
        world.blobs.add(a);world.blobs.add(b);
        world.ensureBlobIds();
        writeMesh(mesh,name,0,world);
        final int[] raw={0},begins={0},ends={0},merges={0};
        ContactTracker tracker=new ContactTracker(t->{
            if(t.kind==ContactTracker.Kind.BEGIN)begins[0]++;
            if(t.kind==ContactTracker.Kind.END)ends[0]++;
            write(csv,name,t.kind.name().toLowerCase(Locale.US),t.time,
                    t.contact.dt,t.blobA,t.blobB,0,t.contact,t.contactAge);
        });
        world.setContactProbe(new ContactProbe.Listener(){
            @Override public void onContactStep(float time,float dt,
                    List<ContactProbe.Contact> contacts) {
                for(ContactProbe.Contact c:contacts) {
                    raw[0]++;
                    write(csv,name,"contact",time,dt,c.blobA,c.blobB,0,c,0f);
                }
                tracker.onContactStep(time,dt,contacts);
            }
            @Override public void onMerge(BlobWorld.Merge m) {
                merges[0]++;
                tracker.onMerge(m);
                writeMerge(csv,name,m);
            }
            @Override public void onPop(int blobId,float time) {
                tracker.onPop(blobId,time);
            }
            @Override public void onReset() {tracker.reset();}
        });
        for(int i=0;i<96;i++) {
            if(name.equals("release")&&i==12)b.translate(140f,0f);
            if(name.equals("pressure")&&i<25)
                for(int n=0;n<Blob.NODES;n++)b.vy[n]-=10f;
            world.tick(DT);
            writeMesh(mesh,name,i+1,world);
        }
        if(name.equals("fusion"))assertEquals(1,merges[0]);
        else assertTrue("Expected actual membrane contacts in "+name,raw[0]>0);
        if(name.equals("slide")||name.equals("release"))
            assertTrue("Expected persistent contact in "+name,begins[0]>0);
        if(name.equals("release"))
            assertTrue("Separated pair should emit END",ends[0]>0);
    }

    @Test public void exportReproducibleProbeAndLifecycleTrace() throws IOException {
        File dir=new File("build/audio_v2");
        assertTrue(dir.isDirectory()||dir.mkdirs());
        File file=new File(dir,"contact_probe_trace.csv");
        File meshFile=new File(dir,"two_blob_mesh_frames.csv");
        try(PrintWriter csv=new PrintWriter(new FileWriter(file));
            PrintWriter mesh=new PrintWriter(new FileWriter(meshFile))) {
            csv.println("scenario,type,time,dt,blob_a,blob_b,result_id,x,y,nx,ny,normal_speed,tangent_speed,compression,radius_a,radius_b,projection_samples,contact_age");
            writeMeshHeader(mesh);
            run(csv,mesh,"impact",91L);
            run(csv,mesh,"slide",92L);
            run(csv,mesh,"pressure",93L);
            run(csv,mesh,"release",94L);
            run(csv,mesh,"fusion",95L);
        }
        assertTrue("Trace must contain samples",file.isFile()&&file.length()>1000);
        assertTrue("Mesh trace must contain real polygon geometry",
                meshFile.isFile()&&meshFile.length()>10000);
        System.out.println("AUDIO_V2_CONTACT_TRACE: "+file.getAbsolutePath());
    }
    /**
     * A separate stress trace from the actual solver, NOT a synthetic event
     * generator. Multiple contacts and real mergers share the same time axis.
     */
    @Test public void exportRealMultiBlobSceneTrace() throws IOException {
        File dir=new File("build/audio_v2");
        assertTrue(dir.isDirectory()||dir.mkdirs());
        File file=new File(dir,"scene_stress_trace.csv");
        File meshFile=new File(dir,"scene_stress_mesh_frames.csv");
        BlobWorld world=new BlobWorld(new Random(1203));
        world.setHeight(850f);
        final int[] tiers={1,2,3,4};
        for(int row=0;row<3;row++) {
            for(int col=0;col<4;col++) {
                Blob b=blob(95+75*col,270+60*row,tiers[col]);
                speed(b,(col%2==0?1:-1)*35f,(row%2==0?1:-1)*22f);
                world.blobs.add(b);
            }
        }
        final int[] contacts={0},starts={0},merges={0};
        world.ensureBlobIds();
        try(PrintWriter csv=new PrintWriter(new FileWriter(file));
            PrintWriter mesh=new PrintWriter(new FileWriter(meshFile))) {
            csv.println("scenario,type,time,dt,blob_a,blob_b,result_id,x,y,nx,ny,normal_speed,tangent_speed,compression,radius_a,radius_b,projection_samples,contact_age");
            writeMeshHeader(mesh);
            writeMesh(mesh,"stress_real",0,world);
            ContactTracker tracker=new ContactTracker(t->{
                if(t.kind==ContactTracker.Kind.BEGIN)starts[0]++;
                write(csv,"stress_real",t.kind.name().toLowerCase(Locale.US),
                        t.time,t.contact.dt,t.blobA,t.blobB,0,t.contact,t.contactAge);
            });
            world.setContactProbe(new ContactProbe.Listener() {
                @Override public void onContactStep(float time,float dt,
                        List<ContactProbe.Contact> samples) {
                    java.util.HashSet<Long> pairs=new java.util.HashSet<>();
                    for(ContactProbe.Contact s:samples) {
                        contacts[0]++;
                        assertTrue("One observation per pair per substep",
                                pairs.add(BlobWorld.pairKey(s.blobA,s.blobB)));
                        write(csv,"stress_real","contact",time,dt,s.blobA,s.blobB,0,s,0f);
                    }
                    tracker.onContactStep(time,dt,samples);
                }
                @Override public void onMerge(BlobWorld.Merge m) {
                    merges[0]++;
                    tracker.onMerge(m);
                    writeMerge(csv,"stress_real",m);
                }
                @Override public void onPop(int blobId,float time) {
                    tracker.onPop(blobId,time);
                }
                @Override public void onReset() {tracker.reset();}
            });
            for(int frame=0;frame<96;frame++) {
                if(frame%20==0)world.shake(4f,-2f,3f);
                world.tick(DT);
                writeMesh(mesh,"stress_real",frame+1,world);
            }
        }
        assertTrue("Multi-blob mesh export missing",
                meshFile.isFile()&&meshFile.length()>10000);
        assertTrue("Many-body trace should contain real contacts",contacts[0]>12);
        assertTrue("At least one contact lifecycle must start",starts[0]>0);
        assertTrue(file.isFile()&&file.length()>1500);
        System.out.printf(Locale.US,
                "AUDIO_V2_REAL_STRESS: contacts=%d begins=%d merges=%d path=%s%n",
                contacts[0],starts[0],merges[0],file.getAbsolutePath());
    }

}
