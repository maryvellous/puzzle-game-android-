package com.maryvellous.lavablobs;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.SystemClock;

import com.maryvellous.lavablobs.audio.BloopMaterialBank;
import com.maryvellous.lavablobs.audio.PcmMaterialPreparation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Load the four ORIGINAL embedded MP3 resources into an immutable PCM bank.
 * Runs on the dedicated audio startup thread, never on the UI/audio callback.
 *
 * Each attack / tail region is measured on the device from the same original
 * PCM, using the 5ms energy heuristic of tools/audio_v2/material_bank.py.
 * No artificial full-fusion samples or old C1-C4 DSP are bundled.
 */
final class AndroidBloopDecoder {
    private static final int RATE=BloopMaterialBank.SAMPLE_RATE;
    private static final int[] ORIGINALS={
            R.raw.bloop_1,R.raw.bloop_2,R.raw.bloop_3,R.raw.bloop_4
    };
    private static final long DECODE_TIMEOUT_MS=8000L;

    static BloopMaterialBank load(Context context) throws Exception {
        BloopMaterialBank.Material[] items=
                new BloopMaterialBank.Material[ORIGINALS.length];
        for(int i=0;i<ORIGINALS.length;i++){
            float[] full=decode(context,ORIGINALS[i]);
            items[i]=PcmMaterialPreparation.prepareMaterial(full);
        }
        return new BloopMaterialBank(items);
    }

    /** Restore the original 1.0.11 release gesture from its existing source. */
    static float[] loadLaunch(Context context) throws Exception {
        return prepareLaunch(decode(context,R.raw.swoosh_source));
    }

    static float[] prepareLaunch(float[] source) throws IOException {
        return PcmMaterialPreparation.prepareLaunch(source);
    }

    private static float[] decode(Context context,int res) throws Exception {
        try(AssetFileDescriptor descriptor=context.getResources().openRawResourceFd(res)){
            if(descriptor==null)throw new IOException("Cannot open original Bloop "+res);
            MediaExtractor extractor=new MediaExtractor();
            MediaCodec codec=null;
            boolean started=false;
            try {
                extractor.setDataSource(descriptor.getFileDescriptor(),
                        descriptor.getStartOffset(),descriptor.getLength());
                int track=-1;
                MediaFormat inputFormat=null;
                for(int i=0;i<extractor.getTrackCount();i++){
                    MediaFormat candidate=extractor.getTrackFormat(i);
                    String mime=candidate.getString(MediaFormat.KEY_MIME);
                    if(mime!=null&&mime.startsWith("audio/")){
                        track=i;inputFormat=candidate;break;
                    }
                }
                if(track<0||inputFormat==null)
                    throw new IOException("No audio track in source "+res);
                extractor.selectTrack(track);
                codec=MediaCodec.createDecoderByType(
                        inputFormat.getString(MediaFormat.KEY_MIME));
                // Request PCM16; verify the *actual* output format below.
                inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING,
                        AudioFormat.ENCODING_PCM_16BIT);
                codec.configure(inputFormat,null,null,0);
                codec.start();
                started=true;
                ByteArrayOutputStream bytes=new ByteArrayOutputStream(150000);
                MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
                int rate=inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                int channels=inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                int encoding=AudioFormat.ENCODING_PCM_16BIT;
                long deadline=SystemClock.uptimeMillis()+DECODE_TIMEOUT_MS;
                boolean inputDone=false,outputDone=false;
                while(!outputDone){
                    if(Thread.currentThread().isInterrupted())
                        throw new InterruptedException("Audio initialization cancelled");
                    if(SystemClock.uptimeMillis()>deadline)
                        throw new IOException("Bloop decoder timed out");
                    if(!inputDone){
                        int input=codec.dequeueInputBuffer(10000);
                        if(input>=0){
                            ByteBuffer buffer=codec.getInputBuffer(input);
                            if(buffer==null)throw new IOException("Null decoder input");
                            buffer.clear();
                            int length=extractor.readSampleData(buffer,0);
                            if(length<0){
                                codec.queueInputBuffer(input,0,0,0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone=true;
                            }else{
                                codec.queueInputBuffer(input,0,length,
                                        Math.max(0,extractor.getSampleTime()),0);
                                extractor.advance();
                            }
                        }
                    }
                    int output=codec.dequeueOutputBuffer(info,10000);
                    if(output==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                        MediaFormat f=codec.getOutputFormat();
                        rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        encoding=f.containsKey(MediaFormat.KEY_PCM_ENCODING)?
                                f.getInteger(MediaFormat.KEY_PCM_ENCODING):
                                AudioFormat.ENCODING_PCM_16BIT;
                        if(encoding!=AudioFormat.ENCODING_PCM_16BIT&&
                                encoding!=AudioFormat.ENCODING_PCM_FLOAT)
                            throw new IOException("Unsupported MP3 PCM type "+encoding);
                    }else if(output>=0){
                        ByteBuffer raw=codec.getOutputBuffer(output);
                        if(info.size>0){
                            if(raw==null)throw new IOException("Null decoder output");
                            raw.position(info.offset);
                            raw.limit(info.offset+info.size);
                            byte[] block=new byte[info.size];
                            raw.get(block);
                            bytes.write(block,0,block.length);
                        }
                        outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                        codec.releaseOutputBuffer(output,false);
                    }
                }
                if(channels<1||channels>2||rate<8000)
                    throw new IOException("Invalid decoder audio layout");
                byte[] data=bytes.toByteArray();
                int sampleBytes=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;
                int frameBytes=channels*sampleBytes;
                if(data.length<frameBytes*RATE/8 || data.length%frameBytes!=0)
                    throw new IOException("Invalid decoded Bloop length");
                ByteBuffer buffer=ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
                float[] mono=new float[data.length/frameBytes];
                for(int i=0;i<mono.length;i++){
                    float value=0f;
                    for(int channel=0;channel<channels;channel++){
                        value+=encoding==AudioFormat.ENCODING_PCM_FLOAT?
                                buffer.getFloat():buffer.getShort()/32768f;
                    }
                    mono[i]=Math.max(-1f,Math.min(1f,value/channels));
                }
                if(rate==RATE)return mono;
                // Safe off-thread fallback for devices decoding at 48 kHz.
                int length=(int)Math.round(mono.length*RATE/(double)rate);
                float[] converted=new float[length];
                for(int i=0;i<length;i++){
                    double position=i*rate/(double)RATE;
                    int at=Math.min(mono.length-1,(int)position);
                    int next=Math.min(mono.length-1,at+1);
                    float t=(float)(position-at);
                    converted[i]=mono[at]*(1f-t)+mono[next]*t;
                }
                return converted;
            }finally{
                if(codec!=null){
                    if(started){try{codec.stop();}catch(Exception ignored){}}
                    codec.release();
                }
                extractor.release();
            }
        }
    }

    static int[] measureRegions(float[] audio) throws IOException {
        return PcmMaterialPreparation.measureRegions(audio);
    }
    private AndroidBloopDecoder(){}
}
