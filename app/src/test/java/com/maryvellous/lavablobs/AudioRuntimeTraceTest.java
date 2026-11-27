package com.maryvellous.lavablobs;

import com.maryvellous.lavablobs.audio.*;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class AudioRuntimeTraceTest {
    private static Blob blob(float x,float y,int tier){return new Blob(x,y,tier,new Random(3));}
    @Test public void exportRuntimeStressTrace() throws Exception{
        List<AudioEvent> events=new ArrayList<>();
        BlobWorld world=new BlobWorld(new Random(1203),GameSpec.ENDLESS[0]);
        world.setHeight(850f);
        world.setContactProbe(new PhysicsAudioEvents(events::add,()->1,()->0,()->{}));
        world.nextTier=0;
        assertTrue(world.launch(210));
        Blob launched=world.blobs.get(0);
        world.eraseCount=1;
        assertTrue(world.eraseAt(launched.x,launched.y));
        int[] tiers={1,2,3,4};
        for(int row=0;row<3;row++)for(int col=0;col<4;col++){
            Blob b=blob(95+75*col,270+60*row,tiers[col]);
            for(int n=0;n<Blob.NODES;n++){
                b.vx[n]=(col%2==0?1:-1)*35f;
                b.vy[n]=(row%2==0?1:-1)*22f;
            }
            world.blobs.add(b);
        }
        world.blobs.add(blob(170,520,0));world.blobs.add(blob(204,520,0));
        world.ensureBlobIds();
        for(int frame=0;frame<120;frame++){
            if(frame%20==0)world.shake(4,-2,3);
            world.tick(1f/60f);
        }
        assertTrue(events.stream().anyMatch(e->e.type==AudioEvent.Type.CONTACT));
        assertEquals(1,events.stream().filter(e->e.type==AudioEvent.Type.LAUNCH).count());
        assertEquals(1,events.stream().filter(e->e.type==AudioEvent.Type.POP).count());
        assertEquals(world.mergeCount,events.stream().filter(e->e.type==AudioEvent.Type.MERGE).count());
        assertTrue(world.mergeCount>0);
        double previous=-1;
        for(AudioEvent e:events){assertTrue(e.time>=previous);previous=e.time;}
        File dir=new File("build/audio_v2");assertTrue(dir.isDirectory()||dir.mkdirs());
        File file=new File(dir,"runtime_events_v1.csv");
        try(Writer w=new FileWriter(file)){AudioTrace.write(w,events);}
        List<AudioEvent> replay;
        try(Reader r=new FileReader(file)){replay=AudioTrace.read(r);}
        assertEquals(events.size(),replay.size());
        for(int i=0;i<events.size();i++){
            AudioEvent a=events.get(i),b=replay.get(i);
            assertEquals(a.type,b.type);assertEquals(a.time,b.time,0);
            assertEquals(a.normal,b.normal,0);assertEquals(a.generation,b.generation);
        }
        System.out.println("RUNTIME_TRACE_EVENTS="+events.size()+" path="+file);
    }
    @Test public void wallUsesExactPhysicsTimestamp(){
        List<AudioEvent> events=new ArrayList<>();
        ContactProbe probe=new ContactProbe(new PhysicsAudioEvents(events::add,()->7,()->0,()->{}));
        Vessel vessel=new Vessel(-1,850);
        Blob b=blob(35,400,1);b.id=1;
        for(int i=0;i<Blob.NODES;i++)b.vx[i]=-250;
        probe.beginStep();
        for(int pass=0;pass<5;pass++)vessel.confine(b,probe);
        probe.finishStep(.008f,.008f);
        assertEquals(1,events.size());
        assertEquals(AudioEvent.Type.WALL,events.get(0).type);
        assertEquals(.008f,events.get(0).time,0);
    }
}
