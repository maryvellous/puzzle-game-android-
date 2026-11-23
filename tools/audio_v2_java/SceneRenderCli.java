package com.maryvellous.lavablobs.audio;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Desktop-only harness for the EXACT SAME MaterialAudioEngine Java class that
 * can later be used from Android. JDK WAV file I/O stays outside audio core.
 * This file never belongs in Android source sets or shipping APKs.
 */
public final class SceneRenderCli {
    private static final int RATE=BloopMaterialBank.SAMPLE_RATE;
    private static final String[] SCENARIOS={"impact","slide","pressure","release","fusion"};
    private static final float SINGLE_SECONDS=2.0f;
    private static final float PAUSE_SECONDS=.30f;

    private static final class Row {
        String scenario,type;
        float time,dt,normal,slip,pressure,radiusA,radiusB,bulkMotion;
        int a,b,result;
        int ordinal;
    }

    private static void check(boolean condition,String detail) {
        if(!condition)throw new IllegalArgumentException(detail);
    }

    private static float[] readMonoWav(Path path) throws Exception {
        try(AudioInputStream stream=AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat format=stream.getFormat();
            check(format.getChannels()==1&&format.getSampleRate()==RATE &&
                    format.getSampleSizeInBits()==16 &&
                    format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED) &&
                    !format.isBigEndian(),"Expected 44.1kHz mono PCM16 LE WAV: "+path);
            byte[] bytes=stream.readAllBytes();
            check(bytes.length>500&&(bytes.length&1)==0,"Invalid PCM: "+path);
            float[] out=new float[bytes.length/2];
            for(int i=0;i<out.length;i++) {
                int lo=bytes[2*i]&0xff;
                int hi=bytes[2*i+1];
                out[i]=(short)((hi<<8)|lo)/32768f;
            }
            return out;
        }
    }

    private static void writeWav(Path path,float[] audio) throws IOException {
        Files.createDirectories(path.getParent());
        int dataSize=audio.length*2;
        ByteBuffer header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        header.putInt(36+dataSize);
        header.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        header.putInt(16);
        header.putShort((short)1);
        header.putShort((short)1);
        header.putInt(RATE);
        header.putInt(RATE*2);
        header.putShort((short)2);
        header.putShort((short)16);
        header.put("data".getBytes(StandardCharsets.US_ASCII));
        header.putInt(dataSize);
        try(FileOutputStream stream=new FileOutputStream(path.toFile())) {
            stream.write(header.array());
            byte[] buffer=new byte[8192];
            int size=0;
            for(float v:audio) {
                if(!Float.isFinite(v))throw new IOException("Nonfinite DSP output");
                check(Math.abs(v)<=.875f,"Output exceeds headroom");
                short pcm=(short)Math.round(Math.max(-1f,Math.min(1f,v))*32767f);
                buffer[size++]=(byte)(pcm&255);
                buffer[size++]=(byte)((pcm>>>8)&255);
                if(size>=buffer.length){stream.write(buffer);size=0;}
            }
            if(size>0)stream.write(buffer,0,size);
        }
    }

    private static BloopMaterialBank loadBank(Path source) throws Exception {
        BloopMaterialBank.Material[] materials=new BloopMaterialBank.Material[4];
        for(int i=0;i<4;i++) {
            String name="bloop_"+(i+1);
            materials[i]=new BloopMaterialBank.Material(
                readMonoWav(source.resolve("full_fusions").resolve(name+"_complete.wav")),
                readMonoWav(source.resolve("isolated_auditions").resolve(name+"_attack_only.wav")),
                readMonoWav(source.resolve("isolated_auditions").resolve(name+"_tail_only.wav")));
        }
        return new BloopMaterialBank(materials);
    }

    private static List<Row> readRows(Path path) throws IOException {
        List<Row> data=new ArrayList<>();
        try(BufferedReader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String header=reader.readLine();
            check(header!=null&&header.equals(
                    "scenario,type,time,dt,blob_a,blob_b,result_id,x,y,nx,ny,normal_speed,tangent_speed,compression,radius_a,radius_b,projection_samples,contact_age,bulk_motion"),
                    "Unexpected contact CSV schema: "+path);
            String line;
            int index=0;
            while((line=reader.readLine())!=null) {
                String[] parts=line.split(",",-1);
                check(parts.length==19,"Unexpected CSV row");
                Row row=new Row();
                row.scenario=parts[0];
                row.type=parts[1];
                row.time=Float.parseFloat(parts[2]);
                row.dt=Float.parseFloat(parts[3]);
                row.a=Integer.parseInt(parts[4]);
                row.b=Integer.parseInt(parts[5]);
                row.result=Integer.parseInt(parts[6]);
                row.normal=Float.parseFloat(parts[11]);
                row.slip=Float.parseFloat(parts[12]);
                row.pressure=Float.parseFloat(parts[13]);
                row.radiusA=Float.parseFloat(parts[14]);
                row.radiusB=Float.parseFloat(parts[15]);
                row.bulkMotion=Float.parseFloat(parts[18]);
                row.ordinal=index++;
                check(Float.isFinite(row.time)&&row.time>=0,
                        "Nonfinite or negative timestamp");
                data.add(row);
            }
        }
        // Keep source ordering for equal timestamps; Java probe and tracker
        // emit actual sequential physics observations.
        data.sort(Comparator.comparingDouble((Row r)->r.time).thenComparingInt(r->r.ordinal));
        return data;
    }

    private static float[] render(BloopMaterialBank bank,List<Row> events,
                                   String scenario,float duration) {
        MaterialAudioEngine engine=new MaterialAudioEngine(bank);
        int endSample=Math.round(duration*RATE);
        float[] output=new float[endSample];
        int current=0;
        int actualContacts=0;
        for(Row e:events) {
            if(!e.scenario.equals(scenario))continue;
            int at=Math.min(endSample,Math.max(current,Math.round(e.time*RATE)));
            if(at>current) {
                engine.renderMono(output,current,at-current);
                current=at;
            }
            switch(e.type) {
                case "contact":
                    // The five single-behavior auditions isolate one gesture,
                    // as in the artistically approved Python reference:
                    // sliding/compression/release demonstrate TAIL response
                    // without accidentally starting an impact. The real
                    // 12-blob scene preserves ALL physical closing impulses.
                    float approach=(scenario.equals("impact")||
                            scenario.equals("stress_real"))?e.normal:0f;
                    engine.contact(e.a,e.b,approach,e.slip,e.pressure,e.radiusA,e.radiusB,
                            e.dt>0f?e.dt:1f/120f,e.bulkMotion);
                    actualContacts++;
                    break;
                case "end":
                    engine.endContact(e.a,e.b);
                    break;
                case "cancelled":
                    engine.endContact(e.a,e.b);
                    break;
                case "merge":
                    // Single fusion audition preserves the original full
                    // Bloop tempo for strict comparison with the approved
                    // reference. The real multi-blob scene uses physical size.
                    engine.merge(e.a,e.b,scenario.equals("fusion")?32f:
                            (e.radiusA>0?e.radiusA:32f));
                    break;
                default:
                    // begin/update rows are tracker diagnostics; raw contact
                    // samples above drive the actual engine exactly once.
            }
        }
        if(current<endSample)engine.renderMono(output,current,endSample-current);
        check(engine.fusionsStarted()==engine.fusionsCompleted(),
                "Whole fusion tail would be truncated in "+scenario+
                ": "+engine.fusionsStarted()+" vs "+engine.fusionsCompleted());
        check(engine.peakOutput()<=.871f,"Clipped final output");
        System.out.printf("%s | contacts=%d pairBegins=%d fullFusions=%d squishes=%d impacts=%d peak=%.4f shortRejected=%d%n",
                scenario,actualContacts,engine.contactBegins(),
                engine.fusionsCompleted(),engine.squishEvents(),engine.impactEvents(),
                engine.peakOutput(),engine.rejectedShortVoices());
        if(scenario.equals("fusion"))
            check(engine.fusionsCompleted()==1,"Expected exactly one whole Bloop");
        return output;
    }

    private static float[] stressSynthetic(BloopMaterialBank bank) {
        MaterialAudioEngine engine=new MaterialAudioEngine(bank);
        float[] output=new float[7*RATE];
        int pos=0;
        for(int tick=0;tick<140;tick++) {
            int at=Math.round((tick*.05f)*RATE);
            if(at>pos){engine.renderMono(output,pos,at-pos);pos=at;}
            if(tick<36&&tick%3==1)engine.merge(1+tick,400+tick,32+tick);
            if(tick<90)engine.launch(20+tick%7*4);
        }
        if(pos<output.length)engine.renderMono(output,pos,output.length-pos);
        check(engine.fusionsStarted()==engine.fusionsCompleted(),
                "Synthetic fusion tail was truncated");
        check(engine.fusionsCompleted()==12,"Expected twelve full fusion voices");
        System.out.printf("synthetic | fullFusions=%d shortRejected=%d peak=%.4f%n",
                engine.fusionsCompleted(),engine.rejectedShortVoices(),engine.peakOutput());
        return output;
    }

    private static float[] montage(List<float[]> sounds) {
        int gap=Math.round(PAUSE_SECONDS*RATE);
        int size=0;
        for(float[] sound:sounds)size+=sound.length+gap;
        float[] all=new float[size];
        int offset=0;
        for(float[] sound:sounds) {
            System.arraycopy(sound,0,all,offset,sound.length);
            offset+=sound.length+gap;
        }
        return all;
    }

    public static void main(String[] args) throws Exception {
        check(args.length==4,
                "Usage: SceneRenderCli <materialDir> <contactCsv> <stressCsv> <outputDir>");
        Path material=Path.of(args[0]),contacts=Path.of(args[1]);
        Path stress=Path.of(args[2]),out=Path.of(args[3]);
        BloopMaterialBank bank=loadBank(material);
        List<Row> events=readRows(contacts);
        List<float[]> scenes=new ArrayList<>();
        for(String name:SCENARIOS) {
            float[] audio=render(bank,events,name,SINGLE_SECONDS);
            scenes.add(audio);
            writeWav(out.resolve(name+"_from_real_membranes.wav"),audio);
        }
        writeWav(out.resolve("listen_real_physics_v2.wav"),montage(scenes));
        writeWav(out.resolve("real_12_blob_scene_stress.wav"),
                render(bank,readRows(stress),"stress_real",3f));
        writeWav(out.resolve("synthetic_mixer_stress.wav"),stressSynthetic(bank));
        Files.writeString(out.resolve("README.txt"),
                "These are JAVA DSP candidate previews. The PCM engine under app/src/main/java"
                +" is the SAME source used for offline and future Android playback. "
                +"The Python previews in ../listening remain the artistically approved"
                +" REFERENCE, not a second shipping DSP. Fusion sources complete."
                +" Video uses the real Java 24-node membrane trace, schematic rendering."
                +"\n",StandardCharsets.UTF_8);
    }
}
