package com.maryvellous.lavablobs.audio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/** Versioned, local-only acoustic event trace. Preserve event order and exact
 * Float/Double text round trips; wall and input events use the same schema. */
public final class AudioTrace {
    public static final String HEADER="lava-audio-trace-v1,time,generation,a,b,normal,slip,compression,radius_a,radius_b,dt,bulk_motion";
    private AudioTrace(){}
    public static void write(Writer out,Iterable<AudioEvent> events) throws IOException{
        out.write(HEADER+"\n");
        for(AudioEvent e:events){
            out.write(e.type.name()+","+e.time+","+e.generation+","+e.a+","+e.b+","+
                    e.normal+","+e.slip+","+e.compression+","+e.radiusA+","+e.radiusB+","+
                    e.dt+","+e.bulkMotion+"\n");
        }
        out.flush();
    }
    public static List<AudioEvent> read(Reader in) throws IOException{
        BufferedReader reader=new BufferedReader(in);
        if(!HEADER.equals(reader.readLine()))throw new IOException("Unsupported acoustic trace schema");
        List<AudioEvent> result=new ArrayList<>();
        String line;
        while((line=reader.readLine())!=null){
            String[] v=line.split(",",-1);
            if(v.length!=12)throw new IOException("Malformed acoustic trace row");
            try{
                result.add(new AudioEvent(AudioEvent.Type.valueOf(v[0]),
                        Integer.parseInt(v[3]),Integer.parseInt(v[4]),
                        Float.parseFloat(v[5]),Float.parseFloat(v[6]),Float.parseFloat(v[7]),
                        Float.parseFloat(v[8]),Float.parseFloat(v[9]),Float.parseFloat(v[10]),
                        Float.parseFloat(v[11]),Double.parseDouble(v[1]),Integer.parseInt(v[2]),0));
            }catch(IllegalArgumentException bad){throw new IOException("Invalid acoustic trace row",bad);}
        }
        return result;
    }
}
