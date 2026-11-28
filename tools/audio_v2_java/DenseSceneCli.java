package com.maryvellous.lavablobs;

import com.maryvellous.lavablobs.audio.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Deterministic full-vessel fixture, compiled against both reference and candidate.
 * Timings exclude snapshot hashing, CSV writing and warmup. Never an Android benchmark. */
public final class DenseSceneCli {
    private static final int FRAMES=180, WARMUP=2, RUNS=3;
    private static void state(DataOutputStream out,BlobWorld w)throws IOException{
        out.writeInt(w.blobs.size());out.writeLong(w.score);
        out.writeInt(w.mergeCount);out.writeInt(w.nextTier);out.writeInt(w.nextBlobId);
        out.writeFloat(w.time);out.writeFloat(w.blockedTime);out.writeBoolean(w.gameOver);
        for(Blob b:w.blobs){
            out.writeInt(b.id);out.writeInt(b.tier);out.writeFloat(b.x);out.writeFloat(b.y);
            out.writeFloat(b.radiusTarget);out.writeFloat(b.age);out.writeFloat(b.launchScale);
            for(int i=0;i<Blob.NODES;i++){
                out.writeFloat(b.px[i]);out.writeFloat(b.py[i]);out.writeFloat(b.vx[i]);out.writeFloat(b.vy[i]);
            }
        }
        out.writeInt(w.adhesions.size());
        for(BlobWorld.Adhesion a:w.adhesions){
            out.writeInt(a.a.id);out.writeInt(a.b.id);out.writeInt(a.ai);out.writeInt(a.bi);out.writeFloat(a.age);
        }
    }
    private static BlobWorld scene(int shape,List<AudioEvent> events){
        BlobWorld w=new BlobWorld(new Random(90031+shape),GameSpec.ENDLESS[shape]);
        w.setHeight(850f);w.disableLegacyImpactCollection();
        w.setContactProbe(new PhysicsAudioEvents(events::add,()->1,()->0,()->{}));
        for(int row=0;row<7;row++)for(int col=0;col<6;col++){
            int tier=(row+col)%2;
            Blob b=new Blob(210f+(col-2.5f)*37f,w.vessel.top+29f+row*42f,tier,1f);
            for(int n=0;n<Blob.NODES;n++){b.vx[n]=(col%2==0?24f:-24f);b.vy[n]=-25f;}
            w.blobs.add(b);
        }
        w.ensureBlobIds();return w;
    }
    private static String hex(byte[] bytes){return HexFormat.of().formatHex(bytes);}
    private static double ms(long n){return n/1e6;}
    private static void run(int shape,Path output)throws Exception{
        long[] times=new long[FRAMES*RUNS];int at=0;
        String stateHash=null,traceHash=null;int eventsCount=0,minBlobs=99,maxBlobs=0;
        for(int r=-WARMUP;r<RUNS;r++){
            List<AudioEvent> events=new ArrayList<>();
            BlobWorld w=scene(shape,events);
            MessageDigest states=MessageDigest.getInstance("SHA-256");
            try(DataOutputStream snapshots=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),states))){
                for(int frame=0;frame<FRAMES;frame++){
                    long start=System.nanoTime();
                    if(frame%30==0)w.shake((frame%60==0?5f:-5f),-3f,4f);
                    w.tick(1f/60f);
                    long elapsed=System.nanoTime()-start;
                    if(r>=0)times[at++]=elapsed;
                    state(snapshots,w);
                    minBlobs=Math.min(minBlobs,w.blobs.size());maxBlobs=Math.max(maxBlobs,w.blobs.size());
                }
            }
            String sh=hex(states.digest());
            StringWriter trace=new StringWriter();AudioTrace.write(trace,events);
            String th=hex(MessageDigest.getInstance("SHA-256").digest(trace.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            if(stateHash!=null&&(!stateHash.equals(sh)||!traceHash.equals(th)))throw new IllegalStateException("Nondeterministic scene");
            stateHash=sh;traceHash=th;eventsCount=events.size();
            if(r==RUNS-1)Files.writeString(output.resolve("dense-"+shape+"-events.csv"),trace.toString());
        }
        if(maxBlobs!=42||minBlobs<12||eventsCount<100)throw new IllegalStateException("Fixture not sufficiently dense");
        Arrays.sort(times);
        double avg=0;for(long n:times)avg+=ms(n);avg/=times.length;
        Files.writeString(output.resolve("dense-"+shape+"-hashes.txt"),stateHash+"\n"+traceHash+"\n");
        System.out.printf(Locale.ROOT,"DENSE shape=%d frames=%d blobs=%d..%d events=%d mean_ms=%.3f p50_ms=%.3f p95_ms=%.3f p99_ms=%.3f max_ms=%.3f state=%s trace=%s%n",
            shape,times.length,minBlobs,maxBlobs,eventsCount,avg,ms(times[times.length/2]),ms(times[(int)Math.ceil(times.length*.95)-1]),ms(times[(int)Math.ceil(times.length*.99)-1]),ms(times[times.length-1]),stateHash,traceHash);
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Usage: DenseSceneCli outputDir");
        Path out=Path.of(args[0]);Files.createDirectories(out);
        for(int shape=0;shape<3;shape++)run(shape,out);
    }
}
