package com.maryvellous.lavablobs;

/** Gameplay session policy, independent of Canvas. Storage format and
 * existing save/clear/record semantics remain owned by the existing stores. */
final class GameSessionController {
    private final BlobWorld world;
    private final GameSpec spec;
    interface Storage {
        boolean checkpoint(GameSpec spec,BlobWorld world,float aimX,boolean eraseMode,float seconds);
        void record(GameSpec spec,BlobWorld world,boolean finished,float seconds);
        void clear();
    }
    private final Storage storage;
    private float lastCheckpoint,activeSeconds;
    private long lastActionMs;
    private boolean finalized;
    GameSessionController(BlobWorld world,GameSpec spec,ProgressStore progress,GameSave session,long now){
        this(world,spec,new Storage(){
            public boolean checkpoint(GameSpec s,BlobWorld w,float x,boolean erase,float seconds){return session.save(s,w,x,erase,seconds);}
            public void record(GameSpec s,BlobWorld w,boolean finished,float seconds){progress.save(s,w,finished,seconds);}
            public void clear(){session.clear();}
        },now);
    }
    GameSessionController(BlobWorld world,GameSpec spec,Storage storage,long now){
        this.world=world;this.spec=spec;this.storage=storage;lastActionMs=now;
    }
    void restoreActiveTime(float seconds){activeSeconds=seconds;lastCheckpoint=world.time;}
    void action(long now){lastActionMs=now;}
    void accountFrame(float elapsed,long now){
        if(!world.gameOver&&now-lastActionMs<=12_000L)activeSeconds+=elapsed;
    }
    boolean checkpointDue(){return !world.gameOver&&world.time-lastCheckpoint>1.5f;}
    boolean checkpoint(float aimX,boolean eraseMode){
        if(world.gameOver){storage.clear();return false;}
        boolean saved=storage.checkpoint(spec,world,aimX,eraseMode,activeSeconds);
        if(saved)lastCheckpoint=world.time;
        return saved;
    }
    void finalizeIfNeeded(){
        if(world.gameOver&&!finalized){
            storage.record(spec,world,true,activeSeconds);
            storage.clear();finalized=true;
        }
    }
    void restart(long now){
        if(world.dropCount>0&&!world.gameOver)storage.record(spec,world,false,activeSeconds);
        storage.clear();world.reset();
        activeSeconds=0f;lastActionMs=now;lastCheckpoint=0f;finalized=false;
    }
}
