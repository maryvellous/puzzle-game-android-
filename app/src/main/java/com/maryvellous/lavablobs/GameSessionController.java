package com.maryvellous.lavablobs;

/** Gameplay session policy, independent of Canvas. Storage format and
 * existing save/clear/record semantics remain owned by the existing stores. */
final class GameSessionController {
    private final BlobWorld world;
    private final GameSpec spec;
    private final ProgressStore progress;
    private final GameSave session;
    private float lastCheckpoint,activeSeconds;
    private long lastActionMs;
    private boolean finalized;
    GameSessionController(BlobWorld world,GameSpec spec,ProgressStore progress,GameSave session,long now){
        this.world=world;this.spec=spec;this.progress=progress;this.session=session;lastActionMs=now;
    }
    void restoreActiveTime(float seconds){activeSeconds=seconds;lastCheckpoint=world.time;}
    void action(long now){lastActionMs=now;}
    void accountFrame(float elapsed,long now){
        if(!world.gameOver&&now-lastActionMs<=12_000L)activeSeconds+=elapsed;
    }
    boolean checkpointDue(){return !world.gameOver&&world.time-lastCheckpoint>1.5f;}
    boolean checkpoint(float aimX,boolean eraseMode){
        if(world.gameOver){session.clear();return false;}
        boolean saved=session.save(spec,world,aimX,eraseMode,activeSeconds);
        if(saved)lastCheckpoint=world.time;
        return saved;
    }
    void finalizeIfNeeded(){
        if(world.gameOver&&!finalized){
            progress.save(spec,world,true,activeSeconds);
            session.clear();finalized=true;
        }
    }
    void restart(long now){
        if(world.dropCount>0&&!world.gameOver)progress.save(spec,world,false,activeSeconds);
        session.clear();world.reset();
        activeSeconds=0f;lastActionMs=now;lastCheckpoint=0f;finalized=false;
    }
}
