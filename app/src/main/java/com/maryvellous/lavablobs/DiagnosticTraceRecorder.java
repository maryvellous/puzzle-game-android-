package com.maryvellous.lavablobs;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;
import com.maryvellous.lavablobs.audio.AudioEvent;
import com.maryvellous.lavablobs.audio.AudioTrace;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.*;

/** Preview only, local cache, no network. The simulation thread stores existing
 * immutable event references; a bounded background executor exports on pause. */
final class DiagnosticTraceRecorder {
    private static final int MAX_EVENTS=32768;
    private static final double MAX_SECONDS=30;
    private final File cache;
    private final ThreadPoolExecutor writer=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"LavaTraceExport");t.setDaemon(true);return t;},
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private ArrayList<AudioEvent> events=new ArrayList<>(1024);
    private boolean truncated;
    private long sequence;
    DiagnosticTraceRecorder(Context context){cache=context.getCacheDir();}
    void record(AudioEvent event){
        if(truncated)return;
        if(events.size()>=MAX_EVENTS||(!events.isEmpty()&&
                event.time-events.get(0).time>MAX_SECONDS)){truncated=true;return;}
        events.add(event);
    }
    void flush(long rejectedObservations,long rejectedControls){
        if(events.isEmpty())return;
        ArrayList<AudioEvent> snapshot=events;
        boolean cut=truncated;
        long id=++sequence;
        events=new ArrayList<>(1024);truncated=false;
        writer.execute(()->export(snapshot,cut,id,rejectedObservations,rejectedControls));
    }
    void close(){writer.shutdown();}
    private void export(ArrayList<AudioEvent> snapshot,boolean cut,long id,long observations,long controls){
        // Fixed filenames prevent unbounded cache growth. CSV is input trace;
        // runtime transport counters may differ and are included in metadata.
        AtomicFile trace=new AtomicFile(new File(cache,"audio-trace-v1.csv"));
        FileOutputStream stream=null;
        try{
            stream=trace.startWrite();
            Writer text=new OutputStreamWriter(stream,StandardCharsets.UTF_8);
            AudioTrace.write(text,snapshot);
            trace.finishWrite(stream);stream=null;
            AtomicFile meta=new AtomicFile(new File(cache,"audio-trace-v1.meta"));
            FileOutputStream metadata=null;
            try{
                metadata=meta.startWrite();
                String info="sequence="+id+"\ngeneration="+snapshot.get(0).generation+
                        "\nevents="+snapshot.size()+"\ntruncated="+cut+
                        "\nfirstSimulationTime="+snapshot.get(0).time+
                        "\nlastSimulationTime="+snapshot.get(snapshot.size()-1).time+
                        "\nrejectedObservationsCumulative="+observations+
                        "\nrejectedControlsCumulative="+controls+"\n";
                metadata.write(info.getBytes(StandardCharsets.UTF_8));
                meta.finishWrite(metadata);metadata=null;
            }finally{if(metadata!=null)meta.failWrite(metadata);}
            Log.i("LavaV2Audio","Local trace exported: "+snapshot.size()+" events; truncated="+cut);
        }catch(Exception e){
            if(stream!=null)trace.failWrite(stream);
            Log.w("LavaV2Audio","Local diagnostic trace export failed",e);
        }
    }
}
