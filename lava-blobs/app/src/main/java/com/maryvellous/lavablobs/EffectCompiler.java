package com.maryvellous.lavablobs;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * One-time off-main-thread editing of short original recordings. Android's
 * decoder supplies PCM; this class crops, reverses and fades it into cached
 * WAV files for SoundPool. No sound generation during gameplay.
 */
final class EffectCompiler {
    private EffectCompiler() {}
    static File render(Context ctx,int raw,String filename,float from,float to,
                       boolean reverse,int fadeInMs,int fadeOutMs) throws Exception {
        File output=new File(ctx.getCacheDir(),filename);
        if(output.isFile()&&output.length()>48)return output;
        Decoded d=decode(ctx,raw);
        if(d.channels<1||d.channels>2)throw new IOException("Unsupported audio channels");
        int frameBytes=d.channels*2,frames=d.pcm.length/frameBytes;
        int start=Math.max(0,Math.min(frames-1,Math.round(frames*from)));
        int end=Math.max(start+1,Math.min(frames,Math.round(frames*to)));
        int count=end-start;
        int fadeIn=Math.max(1,fadeInMs*d.rate/1000),fadeOut=Math.max(1,fadeOutMs*d.rate/1000);
        byte[] mono=new byte[count*2];
        for(int i=0;i<count;i++) {
            int src=(reverse?end-1-i:start+i)*frameBytes,sum=0;
            for(int ch=0;ch<d.channels;ch++) {
                int k=src+ch*2;
                sum+=(short)((d.pcm[k]&255)|((d.pcm[k+1]&255)<<8));
            }
            float gain=Math.min(1f,Math.min((i+1f)/fadeIn,(count-i)/(float)fadeOut));
            short sample=(short)(sum/d.channels*gain);
            mono[2*i]=(byte)(sample&255);mono[2*i+1]=(byte)((sample>>8)&255);
        }
        File tmp=new File(ctx.getCacheDir(),filename+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)) {
            out.write(new byte[]{'R','I','F','F'});le32(out,36+mono.length);
            out.write(new byte[]{'W','A','V','E','f','m','t',' '});
            le32(out,16);le16(out,1);le16(out,1);
            le32(out,d.rate);le32(out,d.rate*2);le16(out,2);le16(out,16);
            out.write(new byte[]{'d','a','t','a'});le32(out,mono.length);
            out.write(mono);out.getFD().sync();
        }
        if(!tmp.renameTo(output)) {
            if(output.exists()&&!output.delete())throw new IOException("Cannot replace cached sample");
            if(!tmp.renameTo(output))throw new IOException("Cannot save cached sample");
        }
        return output;
    }
    private static void le16(FileOutputStream o,int v)throws IOException {
        o.write(v&255);o.write((v>>8)&255);
    }
    private static void le32(FileOutputStream o,int v)throws IOException {
        le16(o,v&65535);le16(o,(v>>>16)&65535);
    }
    private static final class Decoded {
        final byte[] pcm;final int rate,channels;
        Decoded(byte[] pcm,int rate,int channels){
            this.pcm=pcm;this.rate=rate;this.channels=channels;
        }
    }
    private static Decoded decode(Context context,int resource)throws Exception {
        MediaExtractor extractor=new MediaExtractor();
        MediaCodec codec=null;
        AssetFileDescriptor afd=null;
        try {
            afd=context.getResources().openRawResourceFd(resource);
            if(afd==null)throw new IOException("Audio asset unavailable");
            extractor.setDataSource(afd.getFileDescriptor(),afd.getStartOffset(),afd.getLength());
            int track=-1;
            for(int i=0;i<extractor.getTrackCount();i++) {
                String mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("audio/")){track=i;break;}
            }
            if(track<0)throw new IOException("No audio track");
            extractor.selectTrack(track);
            MediaFormat format=extractor.getTrackFormat(track);
            codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format,null,null,0);codec.start();
            int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            ByteArrayOutputStream output=new ByteArrayOutputStream(80000);
            boolean inputDone=false,outputDone=false;
            for(int spin=0;spin<2500&&!outputDone;spin++) {
                if(!inputDone) {
                    int idx=codec.dequeueInputBuffer(2000);
                    if(idx>=0) {
                        ByteBuffer in=codec.getInputBuffer(idx);
                        if(in==null)throw new IOException("Decoder input unavailable");
                        in.clear();
                        int bytes=extractor.readSampleData(in,0);
                        if(bytes<0) {
                            codec.queueInputBuffer(idx,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone=true;
                        } else {
                            codec.queueInputBuffer(idx,0,bytes,extractor.getSampleTime(),0);
                            extractor.advance();
                        }
                    }
                }
                int idx=codec.dequeueOutputBuffer(info,2000);
                if(idx==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat fmt=codec.getOutputFormat();
                    rate=fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels=fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if(fmt.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            &&fmt.getInteger(MediaFormat.KEY_PCM_ENCODING)!=AudioFormat.ENCODING_PCM_16BIT)
                        throw new IOException("Decoder format is not PCM16");
                } else if(idx>=0) {
                    ByteBuffer out=codec.getOutputBuffer(idx);
                    if(info.size>0) {
                        if(out==null)throw new IOException("Decoder output unavailable");
                        out.position(info.offset);out.limit(info.offset+info.size);
                        byte[] bytes=new byte[info.size];out.get(bytes);output.write(bytes);
                    }
                    outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    codec.releaseOutputBuffer(idx,false);
                }
            }
            if(!outputDone||output.size()<256)throw new IOException("Audio decode incomplete");
            return new Decoded(output.toByteArray(),rate,channels);
        } finally {
            if(codec!=null){
                try{codec.stop();}catch(Exception ignored){}
                codec.release();
            }
            extractor.release();
            if(afd!=null)afd.close();
        }
    }
}
