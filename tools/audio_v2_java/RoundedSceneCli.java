package com.maryvellous.lavablobs;

import com.maryvellous.lavablobs.audio.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Deterministic full-vessel fixture, compiled against both reference and candidate.
 * Timings exclude snapshot hashing, CSV writing and warmup. Never an Android benchmark. */
public final class RoundedSceneCli {
    private static final int WINDOWS=3, WINDOW_FRAMES=30, FRAMES=WINDOWS*WINDOW_FRAMES, WARMUP=2, RUNS=3;
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
    private static BlobWorld scene(int shape,int window,List<AudioEvent> events){
        BlobWorld w=new BlobWorld(new Random(90031+shape+31L*window),GameSpec.LEVELS[new int[]{0,2,7}[shape]]);
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
        String stateHash=null,traceHash=null;int eventsCount=0,minBlobs=99,maxBlobs=0,denseFrames=0;
        for(int r=-WARMUP;r<RUNS;r++){
            MessageDigest states=MessageDigest.getInstance("SHA-256"),traces=MessageDigest.getInstance("SHA-256");
            int runEvents=0,runDense=0;
            try(DataOutputStream snapshots=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),states))){
                for(int window=0;window<WINDOWS;window++){
                    List<AudioEvent> events=new ArrayList<>();
                    BlobWorld w=scene(shape,window,events);
                    maxBlobs=Math.max(maxBlobs,w.blobs.size());
                    for(int frame=0;frame<WINDOW_FRAMES;frame++){
                        long start=System.nanoTime();
                        if(frame==0)w.shake((window%2==0?5f:-5f),-3f,4f);
                        w.tick(1f/60f);
                        long elapsed=System.nanoTime()-start;
                        if(r>=0)times[at++]=elapsed;
                        state(snapshots,w);
                        minBlobs=Math.min(minBlobs,w.blobs.size());maxBlobs=Math.max(maxBlobs,w.blobs.size());
                        if(w.blobs.size()>=20)runDense++;
                    }
                    StringWriter trace=new StringWriter();AudioTrace.write(trace,events);
                    byte[] traceBytes=trace.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    traces.update(traceBytes);runEvents+=events.size();
                    if(r==RUNS-1)Files.write(output.resolve("dense-"+shape+"-"+window+"-events.csv"),traceBytes);
                }
            }
            String sh=hex(states.digest()),th=hex(traces.digest());
            if(stateHash!=null&&(!stateHash.equals(sh)||!traceHash.equals(th)))throw new IllegalStateException("Nondeterministic scene");
            stateHash=sh;traceHash=th;eventsCount=runEvents;denseFrames=runDense;
        }
        if(maxBlobs!=42||denseFrames<30||eventsCount<100)
            throw new IllegalStateException("Fixture not sufficiently dense: blobs="+minBlobs+".."+maxBlobs+" denseFrames="+denseFrames+" events="+eventsCount);
        Arrays.sort(times);
        double avg=0;for(long n:times)avg+=ms(n);avg/=times.length;
        Files.writeString(output.resolve("dense-"+shape+"-hashes.txt"),stateHash+"\n"+traceHash+"\n");
        System.out.printf(Locale.ROOT,"ROUNDED shape=%d frames=%d blobs=%d..%d denseFrames=%d events=%d mean_ms=%.3f p50_ms=%.3f p95_ms=%.3f p99_ms=%.3f max_ms=%.3f state=%s trace=%s%n",
            shape,times.length,minBlobs,maxBlobs,denseFrames,eventsCount,avg,ms(times[times.length/2]),ms(times[(int)Math.ceil(times.length*.95)-1]),ms(times[(int)Math.ceil(times.length*.99)-1]),ms(times[times.length-1]),stateHash,traceHash);
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Usage: RoundedSceneCli outputDir");
        Path out=Path.of(args[0]);Files.createDirectories(out);
        for(int shape=0;shape<3;shape++)run(shape,out);
    }
}
