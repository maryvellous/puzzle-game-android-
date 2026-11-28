package com.maryvellous.lavablobs;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class GameSessionControllerTest {
    static final class FakeStorage implements GameSessionController.Storage {
        int saves,records,clears; boolean success=true,finished; float seconds;
        @Override public boolean checkpoint(GameSpec s,BlobWorld w,float x,boolean erase,float time){
            saves++;seconds=time;return success;
        }
        @Override public void record(GameSpec s,BlobWorld w,boolean done,float time){
            records++;finished=done;seconds=time;
        }
        @Override public void clear(){clears++;}
    }
    @Test public void inactiveTimeAndFailedCheckpointsPreservePolicy(){
        BlobWorld world=new BlobWorld(new Random(1));
        FakeStorage storage=new FakeStorage();
        GameSessionController c=new GameSessionController(world,GameSpec.ENDLESS[0],storage,0);
        c.restoreActiveTime(5f);c.accountFrame(.5f,12000);c.accountFrame(.5f,12001);
        world.time=2f;assertTrue(c.checkpointDue());storage.success=false;
        assertFalse(c.checkpoint(210,false));assertTrue(c.checkpointDue());
        storage.success=true;assertTrue(c.checkpoint(210,false));assertFalse(c.checkpointDue());
        assertEquals(5.5f,storage.seconds,0f);
        c.action(13000);c.accountFrame(.5f,13001);
        world.gameOver=true;c.accountFrame(10f,13002);
        c.finalizeIfNeeded();c.finalizeIfNeeded();
        assertEquals(1,storage.records);assertTrue(storage.finished);assertEquals(6f,storage.seconds,0f);
        assertFalse(c.checkpointDue());assertEquals(1,storage.clears);
    }
    @Test public void restartRecordsOnlyUnfinishedNonemptyGameAndResetsClock(){
        BlobWorld w=new BlobWorld(new Random(2));FakeStorage storage=new FakeStorage();
        GameSessionController c=new GameSessionController(w,GameSpec.ENDLESS[0],storage,0);
        w.dropCount=1;c.accountFrame(.25f,100);c.restart(200);
        assertEquals(1,storage.records);assertFalse(storage.finished);assertEquals(.25f,storage.seconds,0f);
        assertEquals(0,w.dropCount);assertFalse(c.checkpointDue());
        c.checkpoint(210,false);assertEquals(0f,storage.seconds,0f);
        c.restart(300);assertEquals(1,storage.records);
    }
}
