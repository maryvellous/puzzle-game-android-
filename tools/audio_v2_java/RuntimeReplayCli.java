package com.maryvellous.lavablobs.audio;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class RuntimeReplayCli {
    private static final int RATE=MaterialAudioEngine.SAMPLE_RATE;
    private static float[] readWav(Path path)throws Exception{
        try(AudioInputStream in=AudioSystem.getAudioInputStream(path.toFile())){
            AudioFormat f=in.getFormat();
            if(f.getChannels()!=1||f.getSampleRate()!=RATE||f.getSampleSizeInBits()!=16||
                    f.isBigEndian()||!AudioFormat.Encoding.PCM_SIGNED.equals(f.getEncoding()))
                throw new IOException("Expected mono 44100 PCM16LE: "+path);
            byte[] bytes=in.readAllBytes();float[] samples=new float[bytes.length/2];
            for(int i=0;i<samples.length;i++)samples[i]=(short)((bytes[i*2]&255)|(bytes[i*2+1]<<8))/32768f;
            return samples;
        }
    }
    private static void writeWav(Path path,float[] samples)throws Exception{
        byte[] bytes=new byte[samples.length*2];
        for(int i=0;i<samples.length;i++){
            if(!Float.isFinite(samples[i])||Math.abs(samples[i])>.871f)
                throw new IOException("Invalid PCM at "+i);
            short s=(short)Math.round(samples[i]*32767f);
            bytes[2*i]=(byte)s;bytes[2*i+1]=(byte)(s>>8);
        }
        AudioFormat f=new AudioFormat(RATE,16,1,true,false);
        try(AudioInputStream in=new AudioInputStream(new ByteArrayInputStream(bytes),f,samples.length)){
            AudioSystem.write(in,AudioFileFormat.Type.WAVE,path.toFile());
        }
    }
    private static String metrics(String label,MaterialAudioEngine e,float[] pcm){
        double energy=0;
        for(float x:pcm)energy+=(double)x*x;
        double rms=Math.sqrt(energy/pcm.length);
        return String.format(Locale.ROOT,
                "%s,%d,%d,%.7f,%.7f,%.7f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.9f,%.9f,%.9f,%d,%d,%d",
                label,e.peakVoices(),e.peakShortVoices(),e.peakDry(),e.peakOutput(),
                e.minimumLimiterGain(),e.limitedFrames(),e.renderedFrames(),
                e.fusionsStarted(),e.fusionsCompleted(),e.rejectedShortVoices(),
                e.aggregateCandidates(),e.aggregateGroups(),e.aggregateCollapsed(),e.aggregateRejected(),
                e.peakGainStep(),e.peakSampleStep(),rms,e.mixLatencyFrames(),e.allocatedVoices(),e.reusedVoices());
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("Usage: RuntimeReplayCli decodedWavDir trace outputDir");
        Path decoded=Path.of(args[0]),trace=Path.of(args[1]),out=Path.of(args[2]);
        Files.createDirectories(out);
        BloopMaterialBank.Material[] parts=new BloopMaterialBank.Material[4];
        for(int i=0;i<4;i++)parts[i]=PcmMaterialPreparation.prepareMaterial(readWav(decoded.resolve("bloop_"+(i+1)+".wav")));
        BloopMaterialBank bank=new BloopMaterialBank(parts);
        float[] launch=PcmMaterialPreparation.prepareLaunch(readWav(decoded.resolve("swoosh_source.wav")));
        List<AudioEvent> events;
        try(Reader in=Files.newBufferedReader(trace)){events=AudioTrace.read(in);}
        if(events.isEmpty())throw new IOException("Empty trace");
        int frames=(int)Math.ceil((events.get(events.size()-1).time-events.get(0).time+3)*RATE/AudioReplay.BLOCK)*AudioReplay.BLOCK;
        long expected=events.stream().filter(e->e.type==AudioEvent.Type.MERGE).count();
        StringBuilder report=new StringBuilder("mode,peak_voices,peak_secondary,peak_dry,peak_output,min_limiter_gain,limited_frames,rendered_frames,fusions_started,fusions_completed,rejected_short,candidates,groups,collapsed,rejected_clusters,peak_gain_step,peak_sample_step,rms,mix_latency_frames,voice_objects_allocated,voice_pool_hits\n");
        StringBuilder stemReport=new StringBuilder("mode,family,dry_peak,dry_rms,export_gain\n");
        for(MaterialAudioEngine.MixProfile profile:MaterialAudioEngine.MixProfile.values()){
          for(boolean aggregated:new boolean[]{false,true}){
            MaterialAudioEngine e=new MaterialAudioEngine(bank,launch,aggregated,profile);
            String label=profile.name().toLowerCase(Locale.ROOT)+(aggregated?"-cluster":"-bypass");
            float[][] stems=profile!=MaterialAudioEngine.MixProfile.APPROVED?e.captureDryStems(frames):null;
            float[] pcm=AudioReplay.render(e,events,frames);
            if(e.fusionsStarted()!=expected||e.fusionsCompleted()!=expected)
                throw new IOException("Lost or truncated fusion in "+label);
            for(int i=pcm.length-RATE/4;i<pcm.length;i++)
                if(pcm[i]!=0f)throw new IOException("Tail did not settle in "+label);
            writeWav(out.resolve(label+".wav"),pcm);
            report.append(metrics(label,e,pcm)).append('\n');
            if(stems!=null){
                Path stemDir=out.resolve(label+"-dry-stems");Files.createDirectories(stemDir);
                float max=0f;
                for(float[] stem:stems)for(float x:stem)max=Math.max(max,Math.abs(x));
                float scale=max>.87f?.87f/max:1f;
                for(MaterialAudioEngine.VoiceFamily family:MaterialAudioEngine.VoiceFamily.values()){
                    float[] dry=stems[family.ordinal()],export=new float[frames];
                    float peak=0;double sum=0;
                    for(int i=0;i<frames;i++){peak=Math.max(peak,Math.abs(dry[i]));sum+=(double)dry[i]*dry[i];export[i]=dry[i]*scale;}
                    String name=family.name().toLowerCase(Locale.ROOT);
                    writeWav(stemDir.resolve(name+".wav"),export);
                    stemReport.append(String.format(Locale.ROOT,"%s,%s,%.9f,%.9f,%.9f%n",label,name,peak,Math.sqrt(sum/frames),scale));
                }
            }
          }
        }
        Files.writeString(out.resolve("metrics.csv"),report.toString(),StandardCharsets.UTF_8);
        Files.writeString(out.resolve("stem_metrics.csv"),stemReport.toString(),StandardCharsets.UTF_8);
        System.out.print(report);System.out.print(stemReport);
    }
}
