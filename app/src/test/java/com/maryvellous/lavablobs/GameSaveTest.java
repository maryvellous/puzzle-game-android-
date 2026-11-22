package com.maryvellous.lavablobs;

import org.junit.Test;
import org.json.JSONObject;
import java.util.Random;
import static org.junit.Assert.*;

public class GameSaveTest {
    @Test public void fullMembraneAndCountersRoundTrip() throws Exception {
        BlobWorld w=new BlobWorld(new Random(83),GameSpec.ENDLESS[0]);
        w.setHeight(900f);
        w.nextTier=0;
        assertTrue(w.launch(210f));
        for(int i=0;i<22;i++)w.tick(.016f);
        Blob b=w.blobs.get(0);
        b.px[3]+=9.2f;b.py[4]-=2.8f;b.vx[4]=74f;
        b.recenter();
        w.score=67420;w.eraseCount=3;w.earnedErases=4;w.usedErases=1;
        w.blockedTime=2.1f;
        w.mergeCount=24;
        w.mergesByTier[2]=8;
        JSONObject json=GameSave.encode(GameSpec.ENDLESS[0],w,157f,true);
        BlobWorld other=new BlobWorld(new Random(1),GameSpec.ENDLESS[0]);
        GameSave.restore(other,new JSONObject(json.toString()));
        assertEquals(w.blobs.size(),other.blobs.size());
        assertEquals(w.score,other.score);
        assertEquals(w.eraseCount,other.eraseCount);
        assertEquals(w.usedErases,other.usedErases);
        assertEquals(w.nextTier,other.nextTier);
        assertEquals(w.blockedTime,other.blockedTime,.0001f);
        assertEquals(w.mergesByTier[2],other.mergesByTier[2]);
        assertEquals(w.time,other.time,.0001f);
        for(int i=0;i<Blob.NODES;i++) {
            assertEquals(b.px[i],other.blobs.get(0).px[i],.0001f);
            assertEquals(b.py[i],other.blobs.get(0).py[i],.0001f);
            assertEquals(b.vx[i],other.blobs.get(0).vx[i],.0001f);
            assertEquals(b.vy[i],other.blobs.get(0).vy[i],.0001f);
        }
        assertEquals(w.random.nextInt(),other.random.nextInt());
        assertEquals(157f,(float)json.getDouble("aimX"),.0001f);
        assertTrue(json.getBoolean("eraseMode"));
    }

    @Test public void legacySaveGetsNewScoreWithoutLosingProgress() throws Exception {
        BlobWorld old=new BlobWorld(new Random(77),GameSpec.ENDLESS[0]);
        old.score=390; // legacy: 100 + 2*~? values don't need to be exact
        old.mergesByTier[0]=1;
        old.mergesByTier[1]=2;
        old.mergeCount=3;
        old.usedErases=0;
        old.blobs.add(new Blob(210,400,2,new Random(2)));
        JSONObject saved=GameSave.encode(GameSpec.ENDLESS[0],old,210f,false);
        saved.remove("scoringVersion");
        assertEquals(10+2*16,new GameSave.Snapshot(GameSpec.ENDLESS[0],saved).points());
        BlobWorld restored=new BlobWorld(new Random(2),GameSpec.ENDLESS[0]);
        GameSave.restore(restored,saved);
        assertEquals(10+2*16,restored.score);
        assertEquals(3,restored.mergeCount);
        assertEquals(1,restored.blobs.size());
        assertEquals(0,restored.eraseCount);
    }

    @Test public void stickyContactsRoundTrip() throws Exception {
        BlobWorld w=new BlobWorld(new Random(44),GameSpec.LEVELS[1]);
        Blob a=new Blob(162,450,2,new Random(1));
        Blob b=new Blob(223,450,3,new Random(2));
        w.blobs.add(a);w.blobs.add(b);
        BlobWorld.Adhesion link=new BlobWorld.Adhesion(a,b,5,12);
        link.age=.21f;w.adhesions.add(link);
        BlobWorld other=new BlobWorld(new Random(0),GameSpec.LEVELS[1]);
        GameSave.restore(other,GameSave.encode(GameSpec.LEVELS[1],w,210f,false));
        assertEquals(2,other.blobs.size());
        assertEquals(1,other.adhesions.size());
        assertSame(other.blobs.get(0),other.adhesions.get(0).a);
        assertSame(other.blobs.get(1),other.adhesions.get(0).b);
        assertEquals(.21f,other.adhesions.get(0).age,.0001f);
    }

    @Test public void rejectWrongSlotOrInvalidMembrane() throws Exception {
        BlobWorld w=new BlobWorld(new Random(1),GameSpec.LEVELS[0]);
        JSONObject json=GameSave.encode(GameSpec.LEVELS[0],w,210f,false);
        BlobWorld wrong=new BlobWorld(new Random(2),GameSpec.LEVELS[1]);
        try{GameSave.restore(wrong,json);fail("Wrong level should be rejected");}
        catch(IllegalArgumentException expected){}
        json.put("nextTier",7);
        try{GameSave.restore(w,json);fail("Impossible next drop should be rejected");}
        catch(IllegalArgumentException expected){}
    }

    @Test public void blobIdentityAndNextCounterSurviveSaveRestore() throws Exception {
        BlobWorld world=new BlobWorld(new Random(31),GameSpec.ENDLESS[0]);
        world.nextTier=0;
        assertTrue(world.launch(210f));
        int removedId=world.blobs.get(0).id;
        world.blobs.clear(); // Identity may not be reused after deletion.
        world.nextTier=0;
        assertTrue(world.launch(210f));
        int savedId=world.blobs.get(0).id;
        assertTrue(savedId>removedId);
        int next=world.nextBlobId;
        JSONObject data=GameSave.encode(GameSpec.ENDLESS[0],world,210f,false);
        BlobWorld loaded=new BlobWorld(new Random(5),GameSpec.ENDLESS[0]);
        GameSave.restore(loaded,new JSONObject(data.toString()));
        assertEquals(savedId,loaded.blobs.get(0).id);
        assertEquals(next,loaded.nextBlobId);
        loaded.blobs.clear();
        loaded.nextTier=0;
        assertTrue(loaded.launch(210f));
        assertEquals(next,loaded.blobs.get(0).id);
    }

    @Test public void legacySavesWithoutIdsAreMigratedWithoutVersionBump() throws Exception {
        BlobWorld world=new BlobWorld(new Random(32),GameSpec.LEVELS[0]);
        world.blobs.add(new Blob(130,450,2,new Random(1)));
        world.blobs.add(new Blob(270,450,3,new Random(2)));
        JSONObject data=GameSave.encode(GameSpec.LEVELS[0],world,210f,false);
        data.remove("nextBlobId");
        org.json.JSONArray bodies=data.getJSONArray("blobs");
        for(int i=0;i<bodies.length();i++)bodies.getJSONObject(i).remove("blobId");
        BlobWorld loaded=new BlobWorld(new Random(2),GameSpec.LEVELS[0]);
        GameSave.restore(loaded,data);
        assertEquals(2,loaded.blobs.size());
        assertTrue(loaded.blobs.get(0).id>0);
        assertTrue(loaded.blobs.get(1).id>0);
        assertNotEquals(loaded.blobs.get(0).id,loaded.blobs.get(1).id);
        assertTrue(loaded.nextBlobId>loaded.blobs.get(1).id);
    }

    @Test public void duplicatePersistedBlobIdsAreRejected() throws Exception {
        BlobWorld world=new BlobWorld(new Random(33),GameSpec.LEVELS[0]);
        world.blobs.add(new Blob(130,450,2,new Random(1)));
        world.blobs.add(new Blob(270,450,3,new Random(2)));
        JSONObject data=GameSave.encode(GameSpec.LEVELS[0],world,210f,false);
        org.json.JSONArray bodies=data.getJSONArray("blobs");
        bodies.getJSONObject(1).put("blobId",bodies.getJSONObject(0).getInt("blobId"));
        BlobWorld loaded=new BlobWorld(new Random(2),GameSpec.LEVELS[0]);
        try {
            GameSave.restore(loaded,data);
            fail("Duplicate IDs must not reach the acoustic tracker");
        } catch(IllegalArgumentException expected) {
            // No silently aliased pair identity.
        }
    }

    @Test public void mappingForEverySavedMode() {
        for(GameSpec s:GameSpec.LEVELS)assertSame(s,GameSave.findSpec(s.id));
        for(GameSpec s:GameSpec.ENDLESS)assertSame(s,GameSave.findSpec(s.id));
        for(GameSpec s:GameSpec.TRIALS)assertSame(s,GameSave.findSpec(s.id));
        assertNull(GameSave.findSpec(-99));
    }
}
