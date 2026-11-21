package com.maryvellous.lavablobs;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Non-shipping acoustic experiment: samples the existing 24-node BlobWorld
 * without adding listeners, changing its solver or changing game audio.
 * Four repeatable two-body experiments produce a CSV for offline DSP.
 */
public class AcousticTwoBlobTraceTest {
    private static final float DT=1f/60f;
    private static final int FRAMES=96;
    private static final float SKIN=5.8f;

    static final class Frame {
        float contact, approach, slide, compression, radius, time;
        int contactNodes, merge;
    }

    static final class Recorder {
        int framesWithContact, framesWithSlide, merges;
        float maxApproach, maxCompression;
    }

    static Frame observe(Blob a, Blob b) {
        Frame f=new Frame();
        f.radius=(a.radius()+b.radius())*.5f;
        f.compression=Math.max(compression(a),compression(b));
        accumulate(f,a,b);
        accumulate(f,b,a);
        if(f.contactNodes>0) {
            f.contact=1f;
            f.approach/=f.contactNodes;
            f.slide/=f.contactNodes;
        }
        return f;
    }

    private static float compression(Blob b) {
        float areaTarget=Math.max(.01f,b.expectedArea());
        float areaDeficit=Math.max(0f,(areaTarget-Math.abs(b.area()))/areaTarget);
        float radial=0f;
        for(int i=0;i<Blob.NODES;i++) {
            float r=(float)Math.hypot(b.px[i]-b.x,b.py[i]-b.y);
            radial=Math.max(radial,Math.max(0f,(b.radiusTarget-r)/Math.max(1f,b.radiusTarget)));
        }
        return Math.min(1f,Math.max(areaDeficit,radial));
    }

    /** Local membrane segment projection, not circular body hitboxes. */
    private static void accumulate(Frame out,Blob src,Blob target) {
        for(int i=0;i<Blob.NODES;i++) {
            float x=src.px[i],y=src.py[i];
            float best=Float.POSITIVE_INFINITY,contactX=0,contactY=0;
            float targetVx=0,targetVy=0;
            for(int j=0;j<Blob.NODES;j++) {
                int k=(j+1)%Blob.NODES;
                float ax=target.px[j],ay=target.py[j];
                float dx=target.px[k]-ax,dy=target.py[k]-ay;
                float lenSq=dx*dx+dy*dy;
                float t=lenSq<1e-6f?0f:Math.max(0f,Math.min(1f,
                            ((x-ax)*dx+(y-ay)*dy)/lenSq));
                float px=ax+t*dx,py=ay+t*dy;
                float sepSq=(px-x)*(px-x)+(py-y)*(py-y);
                if(sepSq<best) {
                    best=sepSq;contactX=px;contactY=py;
                    targetVx=target.vx[j]*(1f-t)+target.vx[k]*t;
                    targetVy=target.vy[j]*(1f-t)+target.vy[k]*t;
                }
            }
            if(best>SKIN*SKIN)continue;
            float nx=contactX-x,ny=contactY-y;
            float distance=(float)Math.hypot(nx,ny);
            if(distance<.0001f) {
                nx=target.x-src.x;
                ny=target.y-src.y;
                distance=(float)Math.hypot(nx,ny);
            }
            if(distance<.0001f)continue;
            nx/=distance;ny/=distance;
            float dvx=src.vx[i]-targetVx, dvy=src.vy[i]-targetVy;
            out.approach+=Math.max(0f,dvx*nx+dvy*ny);
            out.slide+=Math.abs(dvx*(-ny)+dvy*nx);
            out.contactNodes++;
        }
    }

    private static Blob make(float x,float y,int tier) {
        return new Blob(x,y,tier,new Random(1));
    }

    private static void speed(Blob b,float vx,float vy) {
        for(int i=0;i<Blob.NODES;i++) {b.vx[i]=vx;b.vy[i]=vy;}
    }

    private static Recorder scenario(PrintWriter csv,String mode,long seed) {
        BlobWorld world=new BlobWorld(new Random(seed));
        Blob a,b;
        switch(mode) {
            case "impact":
                a=make(210,350,2);b=make(210,422,1);
                speed(a,0,0);speed(b,0,-310);break;
            case "compression":
                a=make(210,350,2);b=make(210,408,1);
                speed(a,0,0);speed(b,0,-85);break;
            case "sliding":
                a=make(181,386,2);b=make(239,386,1);
                speed(a,16,100);speed(b,-16,-100);break;
            case "fusion":
                a=make(210,350,0);b=make(210,391,0);
                speed(a,0,0);speed(b,0,-180);break;
            default: throw new AssertionError("unknown demo: "+mode);
        }
        world.blobs.add(a);world.blobs.add(b);
        Recorder stats=new Recorder();
        for(int i=0;i<FRAMES;i++) {
            // Controlled external impulses make the trial repeatable; these
            // are input velocities, not changes to physical constraints.
            if(mode.equals("compression")&&i<46) {
                for(int n=0;n<Blob.NODES;n++)b.vy[n]-=10f;
            }
            if(mode.equals("sliding")&&i<43) {
                for(int n=0;n<Blob.NODES;n++) {
                    a.vx[n]+=2.5f;b.vx[n]-=2.5f;
                    a.vy[n]+=1.0f;b.vy[n]-=1.0f;
                }
            }
            Frame before=world.blobs.size()==2?observe(world.blobs.get(0),world.blobs.get(1)):new Frame();
            world.tick(DT);
            Frame after=world.blobs.size()==2?observe(world.blobs.get(0),world.blobs.get(1)):before;
            int merges=world.consumeMerges().size();
            // Use observed contact AFTER projection; pre-solver relative
            // velocities remain available for a rapidly resolving impact.
            if(merges>0) {
                after=before;
                after.merge=merges;
            } else if(after.contactNodes>0) {
                after.approach=Math.max(after.approach,before.approach);
                after.slide=Math.max(after.slide,before.slide);
            }
            after.time=i*DT;
            stats.merges+=merges;
            if(after.contactNodes>0)stats.framesWithContact++;
            if(after.contactNodes>0&&after.slide>5f)stats.framesWithSlide++;
            stats.maxApproach=Math.max(stats.maxApproach,after.approach);
            stats.maxCompression=Math.max(stats.maxCompression,after.compression);
            csv.printf(Locale.US,"%s,%.6f,%.1f,%.5f,%.5f,%.6f,%.6f,%d,%d%n",
                mode,after.time,after.radius,after.contact,after.approach,
                after.slide,after.compression,after.contactNodes,after.merge);
        }
        return stats;
    }

    @Test public void actualSoftBodyTrajectoriesCanDriveAnOfflineSoundModel() throws IOException {
        File dir=new File("build/audio_lab");
        assertTrue("Cannot create test artifact directory",dir.isDirectory()||dir.mkdirs());
        File result=new File(dir,"two_blob_physics.csv");
        try(PrintWriter csv=new PrintWriter(new FileWriter(result))) {
            csv.println("scenario,time,radius,contact,normal_speed,tangent_speed,compression,contact_nodes,merge");
            Recorder impact=scenario(csv,"impact",91L);
            Recorder comp=scenario(csv,"compression",92L);
            Recorder scrape=scenario(csv,"sliding",93L);
            Recorder fusion=scenario(csv,"fusion",94L);
            System.out.println("ACOUSTIC_PROBE_TRACE: "+result.getAbsolutePath());
            System.out.printf(Locale.US,"impact: contacts=%d normal_max=%.2f%n",
                    impact.framesWithContact,impact.maxApproach);
            System.out.printf(Locale.US,"compression: contacts=%d max_compression=%.3f%n",
                    comp.framesWithContact,comp.maxCompression);
            System.out.printf(Locale.US,"sliding: contacts=%d moving=%d%n",
                    scrape.framesWithContact,scrape.framesWithSlide);
            System.out.printf(Locale.US,"fusion: merges=%d%n",fusion.merges);
            assertTrue("Fast impact must have real mesh contact",impact.framesWithContact>0);
            assertTrue("Fast impact must yield relative approach",impact.maxApproach>20f);
            assertTrue("Compression must have contact",comp.framesWithContact>0);
            assertTrue("Compression must deform a mesh",comp.maxCompression>0.001f);
            assertTrue("Sliding must make surface contact",scrape.framesWithContact>0);
            assertTrue("Tangential sliding must be observed",scrape.framesWithSlide>0);
            assertEquals("Same-tier blobs should merge exactly once",1,fusion.merges);
        }
        assertTrue(result.isFile()&&result.length()>1000);
    }
}
