package com.maryvellous.lavablobs;

import com.maryvellous.lavablobs.audio.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Same deterministic inputs for baseline and candidate, no Android claims. */
public final class PerformanceRecoveryCli {
    private static final int TICKS=120, WARM=2, REPS=3;
    private static final int[] COUNTS={4,7,10,15,18};
    private static void hashState(DataOutputStream o,BlobWorld w)throws IOException{
        o.writeInt(w.blobs.size());o.writeLong(w.score);o.writeInt(w.dropCount);
        o.writeInt(w.mergeCount);o.writeInt(w.nextTier);o.writeInt(w.nextBlobId);
        o.writeFloat(w.time);o.writeFloat(w.blockedTime);o.writeBoolean(w.gameOver);
        for(Blob b:w.blobs){
            o.writeInt(b.id);o.writeInt(b.tier);o.writeFloat(b.x);o.writeFloat(b.y);
            o.writeFloat(b.age);o.writeFloat(b.radiusTarget);o.writeFloat(b.launchScale);
            for(int i=0;i<Blob.NODES;i++){o.writeFloat(b.px[i]);o.writeFloat(b.py[i]);o.writeFloat(b.vx[i]);o.writeFloat(b.vy[i]);}
        }
        o.writeInt(w.adhesions.size());
        for(BlobWorld.Adhesion a:w.adhesions){o.writeInt(a.a.id);o.writeInt(a.b.id);o.writeInt(a.ai);o.writeInt(a.bi);o.writeFloat(a.age);}
    }
    private static BlobWorld scene(int shape,int n,List<AudioEvent> events,boolean profile){
        BlobWorld w=new BlobWorld(new Random(88421+shape*31L+n),GameSpec.LEVELS[shape]);
        w.setHeight(850f);w.disableLegacyImpactCollection();
        w.setContactProbe(new PhysicsAudioEvents(events::add,()->1,()->0,()->{}));
        w.setProfiling(profile);
        for(int i=0;i<n;i++){
            int row=i/5,col=i%5;
            Blob b=new Blob(210+(col-2)*44,w.vessel.top+35+row*45,(row+col)%2,1f);
            for(int j=0;j<Blob.NODES;j++){b.vx[j]=(col%2==0?25f:-25f);b.vy[j]=-25f;}
            w.blobs.add(b);
        }
        w.ensureBlobIds();return w;
    }
    private static String execute(int shape,int n,long[] timings,int offset,boolean profile,boolean verbose)throws Exception{
        List<AudioEvent> events=new ArrayList<>();
        BlobWorld w=scene(shape,n,events,profile);
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(DataOutputStream out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest))){
            for(int tick=0;tick<TICKS;tick++){
                long start=System.nanoTime();
                // Burst of successive legal drop attempts, then shake and settle.
                if(tick<30&&tick%3==0)w.launch(70+(tick/3%5)*65);
                if(tick==45)w.shake(5,-3,4);
                w.tick(1f/60f);
                if(timings!=null)timings[offset+tick]=System.nanoTime()-start;
                hashState(out,w);
            }
        }
        StringWriter trace=new StringWriter();AudioTrace.write(trace,events);
        digest.update(trace.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(verbose){
            System.out.printf(Locale.ROOT,"profile shape=%d initial=%d acceptedDrops=%d final=%d events=%d%n",shape,n,w.dropCount,w.blobs.size(),events.size());
            System.out.print(w.physicsProfile());
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static double ms(long ns){return ns/1e6;}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("output dir");
        Path output=Path.of(args[0]);Files.createDirectories(output);
        StringBuilder hashes=new StringBuilder();
        for(int shape:new int[]{0,2,7})for(int n:COUNTS){
            long[] samples=new long[TICKS*REPS];String expected=null;
            for(int r=-WARM;r<REPS;r++){
                String hash=execute(shape,n,r<0?null:samples,r<0?0:r*TICKS,false,false);
                if(expected!=null&&!expected.equals(hash))throw new AssertionError("nondeterministic fixture");
                expected=hash;
            }
            String profileHash=execute(shape,n,null,0,true,true);
            if(!expected.equals(profileHash))throw new AssertionError("profiling changed physics/audio");
            Arrays.sort(samples);double avg=0;for(long s:samples)avg+=ms(s);avg/=samples.length;
            System.out.printf(Locale.ROOT,"PERF shape=%d initial=%d mean_ms=%.3f p50_ms=%.3f p95_ms=%.3f p99_ms=%.3f%n",shape,n,avg,ms(samples[samples.length/2]),ms(samples[(int)Math.ceil(samples.length*.95)-1]),ms(samples[(int)Math.ceil(samples.length*.99)-1]));
            hashes.append(shape).append(',').append(n).append(',').append(expected).append('\n');
        }
        Files.writeString(output.resolve("hashes.txt"),hashes.toString());
    }
}
