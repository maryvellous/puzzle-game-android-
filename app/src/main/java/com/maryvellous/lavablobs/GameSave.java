package com.maryvellous.lavablobs;

import android.content.Context;
import android.util.AtomicFile;
import java.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Random;

/**
 * One atomic, private autosave slot. Never stored in the playtest log.
 * A checkpoint contains the *actual 24-node membrane* of every blob.
 */
final class GameSave {
    private static final int VERSION=1;
    private final AtomicFile slot;

    static final class Snapshot {
        final GameSpec spec;
        final JSONObject data;
        Snapshot(GameSpec spec,JSONObject data){this.spec=spec;this.data=data;}
        long points(){
            if(data.optInt("scoringVersion",1)>=2)return data.optLong("score");
            JSONArray tiers=data.optJSONArray("byTier");
            return tiers==null?data.optLong("score"):scoreFromMerges(tiers);
        }
        int drops(){return data.optInt("drops");}
    }

    private static long scoreFromMerges(JSONArray tiers){
        long score=0L;
        for(int i=0;i<Math.min(40,tiers.length());i++)
            score+=(long)tiers.optInt(i)*GameSpec.pointsForMerge(i);
        return score;
    }

    GameSave(Context context){
        slot=new AtomicFile(new File(context.getFilesDir(),"active_game_v1.json"));
    }

    boolean exists(){return peek()!=null;}
    Snapshot peek(){
        flush();
        try {
            JSONObject data=new JSONObject(new String(slot.readFully(),StandardCharsets.UTF_8));
            if(data.optInt("version")!=VERSION)return null;
            GameSpec spec=findSpec(data.getInt("id"));
            if(spec==null||data.optBoolean("gameOver"))return null;
            if(spec.shape>=14&&(data.optInt("shape",-1)!=spec.shape||data.optInt("roundedGeometry",0)!=1))return null;
            return new Snapshot(spec,data);
        }catch(Exception ignored){return null;}
    }

    static GameSpec findSpec(int id){
        for(GameSpec spec:GameSpec.LEVELS)if(spec.id==id)return spec;
        for(GameSpec spec:GameSpec.ENDLESS)if(spec.id==id)return spec;
        for(GameSpec spec:GameSpec.TRIALS)if(spec.id==id)return spec;
        return null;
    }

    // One process-wide writer also serializes operations across Activity recreation.
    private static final java.util.concurrent.ExecutorService WRITER=
        java.util.concurrent.Executors.newSingleThreadExecutor(r->{
            Thread t=new Thread(r,"LavaCheckpoint");t.setDaemon(true);return t;
        });
    private final Object pendingLock=new Object();
    private Capture pending;
    private boolean drainScheduled;

    // Called only at lifecycle/slot boundaries, never for a normal launch.
    void flush(){
        try {WRITER.submit(()->{}).get();}
        catch(InterruptedException e){Thread.currentThread().interrupt();android.util.Log.w("LavaSave","Checkpoint flush interrupted",e);}
        catch(java.util.concurrent.ExecutionException e){android.util.Log.w("LavaSave","Checkpoint flush failed",e);}
    }

    void clear(){
        synchronized(pendingLock){pending=null;}
        // Ordered after any in-flight write, so old saves cannot resurrect.
        try {WRITER.submit(()->slot.delete()).get();}
        catch(InterruptedException e){Thread.currentThread().interrupt();android.util.Log.w("LavaSave","Clear interrupted",e);}
        catch(java.util.concurrent.ExecutionException e){android.util.Log.w("LavaSave","Clear failed",e);}
    }

    boolean save(GameSpec spec,BlobWorld world,float aimX,boolean eraseMode){
        return save(spec,world,aimX,eraseMode,world.time);
    }

    boolean save(GameSpec spec,BlobWorld world,float aimX,boolean eraseMode,float activeSeconds){
        if(world.gameOver){clear();return true;}
        final Capture snapshot;
        try {snapshot=new Capture(spec,world,aimX,eraseMode,activeSeconds);}
        catch(Exception e){android.util.Log.w("LavaSave","Snapshot failed",e);return false;}
        synchronized(pendingLock){
            pending=snapshot;
            if(!drainScheduled){drainScheduled=true;WRITER.execute(this::drain);}
        }
        // Accepted for writing, not necessarily durable yet.
        return true;
    }

    private void drain(){
        while(true){
            Capture snapshot;
            synchronized(pendingLock){
                snapshot=pending;pending=null;
                if(snapshot==null){drainScheduled=false;return;}
            }
            write(snapshot);
        }
    }

    private void write(Capture snapshot){
        FileOutputStream out=null;
        try {
            byte[] bytes=snapshot.json().toString().getBytes(StandardCharsets.UTF_8);
            out=slot.startWrite();out.write(bytes);slot.finishWrite(out);
        }catch(Exception failure){
            if(out!=null)slot.failWrite(out);
            android.util.Log.w("LavaSave","Checkpoint write failed; next checkpoint can retry",failure);
        }
    }

    /** Only immutable copies cross the thread boundary. No live BlobWorld,
     * Blob, adhesion or Random reference escapes the owner thread. */
    private static final class Capture {
        final int id,shape,drops,merges,maxTier,nextTier,nextBlobId,earned,used,eraseCount;
        final float height,time,activeSeconds,blockedTime,checkClock,aimX;
        final long score;
        final boolean gameOver,eraseMode;
        final int[] byTier;
        final byte[] rng;
        final Body[] bodies;
        final Link[] links;
        Capture(GameSpec spec,BlobWorld w,float aimX,boolean eraseMode,float seconds) throws Exception{
            w.ensureBlobIds();
            id=spec.id;shape=spec.shape;height=w.height;time=w.time;activeSeconds=Math.max(0f,seconds);
            score=w.score;drops=w.dropCount;merges=w.mergeCount;maxTier=w.maxTierReached;nextTier=w.nextTier;
            nextBlobId=w.nextBlobId;blockedTime=w.blockedTime;checkClock=w.checkClock;
            earned=w.earnedErases;used=w.usedErases;eraseCount=w.eraseCount;gameOver=w.gameOver;
            this.aimX=aimX;this.eraseMode=eraseMode;byTier=w.mergesByTier.clone();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(ObjectOutputStream stream=new ObjectOutputStream(bytes)){stream.writeObject(w.random);}
            rng=bytes.toByteArray();
            bodies=new Body[w.blobs.size()];
            for(int i=0;i<bodies.length;i++)bodies[i]=new Body(w.blobs.get(i));
            ArrayList<Link> copy=new ArrayList<>(w.adhesions.size());
            for(BlobWorld.Adhesion link:w.adhesions){
                int ia=w.blobs.indexOf(link.a),ib=w.blobs.indexOf(link.b);
                if(ia>=0&&ib>=0)copy.add(new Link(ia,ib,link.ai,link.bi,link.age));
            }
            links=copy.toArray(new Link[0]);
        }
        JSONObject json() throws Exception{
            JSONObject state=new JSONObject();
            state.put("version",VERSION);state.put("scoringVersion",2);
            state.put("id",id);state.put("shape",shape);if(shape>=14)state.put("roundedGeometry",1);
            state.put("height",height);state.put("time",time);state.put("activeSeconds",activeSeconds);
            state.put("score",score);state.put("drops",drops);state.put("merges",merges);
            state.put("maxTier",maxTier);state.put("nextTier",nextTier);state.put("nextBlobId",nextBlobId);
            state.put("blockedTime",blockedTime);state.put("checkClock",checkClock);
            state.put("eraseEarned",earned);state.put("eraseUsed",used);state.put("eraseCount",eraseCount);
            state.put("gameOver",gameOver);state.put("aimX",aimX);state.put("eraseMode",eraseMode);
            state.put("byTier",new JSONArray(byTier));state.put("rng",Base64.getEncoder().encodeToString(rng));
            JSONArray particles=new JSONArray();for(Body body:bodies)particles.put(body.json());
            state.put("blobs",particles);
            JSONArray items=new JSONArray();
            for(Link link:links){JSONArray item=new JSONArray();item.put(link.a);item.put(link.b);item.put(link.ai);item.put(link.bi);item.put(link.age);items.put(item);}
            state.put("links",items);
            return state;
        }
    }
    private static final class Body {
        final int tier,id;
        final float age,radiusTarget,launchScale;
        final float[] nodes=new float[Blob.NODES*4];
        Body(Blob b){
            tier=b.tier;id=b.id;age=b.age;radiusTarget=b.radiusTarget;launchScale=b.launchScale;
            for(int i=0;i<Blob.NODES;i++){int k=i*4;nodes[k]=b.px[i];nodes[k+1]=b.py[i];nodes[k+2]=b.vx[i];nodes[k+3]=b.vy[i];}
        }
        JSONObject json() throws Exception{
            JSONObject body=new JSONObject();body.put("tier",tier);body.put("blobId",id);body.put("age",age);
            body.put("radiusTarget",radiusTarget);body.put("launchScale",launchScale);
            JSONArray points=new JSONArray();
            for(int k=0;k<nodes.length;k+=4){JSONArray node=new JSONArray();node.put(nodes[k]);node.put(nodes[k+1]);node.put(nodes[k+2]);node.put(nodes[k+3]);points.put(node);}
            body.put("nodes",points);return body;
        }
    }
    private static final class Link {
        final int a,b,ai,bi;final float age;
        Link(int a,int b,int ai,int bi,float age){this.a=a;this.b=b;this.ai=ai;this.bi=bi;this.age=age;}
    }

    static JSONObject encode(GameSpec spec,BlobWorld w,float aimX,boolean eraseMode) throws Exception {
        return encode(spec,w,aimX,eraseMode,w.time);
    }

    static JSONObject encode(GameSpec spec,BlobWorld w,float aimX,boolean eraseMode,float activeSeconds) throws Exception {
        return new Capture(spec,w,aimX,eraseMode,activeSeconds).json();
    }

    static void restore(BlobWorld w,JSONObject state) throws Exception {
        if(state.getInt("version")!=VERSION||state.getInt("id")!=w.spec.id)
            throw new IllegalArgumentException("Incompatible save");
        w.reset();
        if(state.has("shape")&&state.getInt("shape")!=w.spec.shape)
            throw new IllegalArgumentException("Saved vessel geometry differs");
        w.setHeight((float)state.getDouble("height"));
        w.time=(float)state.getDouble("time");
        w.score=state.getLong("score");
        w.dropCount=state.getInt("drops");
        w.mergeCount=state.getInt("merges");
        w.maxTierReached=state.getInt("maxTier");
        w.nextTier=state.getInt("nextTier");
        w.blockedTime=(float)state.getDouble("blockedTime");
        w.checkClock=(float)state.getDouble("checkClock");
        w.earnedErases=state.getInt("eraseEarned");
        w.usedErases=state.getInt("eraseUsed");
        w.eraseCount=state.getInt("eraseCount");
        w.gameOver=state.getBoolean("gameOver");
        if(w.nextTier<0||w.nextTier>=5||w.dropCount<0||w.mergeCount<0||w.score<0)
            throw new IllegalArgumentException("Invalid save counters");
        JSONArray tiers=state.getJSONArray("byTier");
        for(int i=0;i<Math.min(tiers.length(),w.mergesByTier.length);i++)
            w.mergesByTier[i]=tiers.getInt(i);
        // Existing 1.0.2 saves remain usable. Only the score is migrated:
        // the old formula rounded to tens, so reconstruct the exact new
        // score from the persisted number of merges at each tier.
        if(state.optInt("scoringVersion",1)<2) {
            w.score=scoreFromMerges(tiers);
            if(w.spec.endless) {
                w.earnedErases=(int)Math.min(2_000_000_000L,
                        w.score/BlobWorld.ERASE_EVERY_POINTS);
                w.eraseCount=Math.max(0,w.earnedErases-w.usedErases);
            }
        }
        byte[] raw=Base64.getDecoder().decode(state.getString("rng"));
        if(raw.length>2048)throw new IllegalArgumentException("Invalid RNG");
        ObjectInputStream stream=new ObjectInputStream(new ByteArrayInputStream(raw));
        Object rng=stream.readObject();stream.close();
        if(!(rng instanceof Random))throw new IllegalArgumentException("Invalid RNG type");
        w.random=(Random)rng;

        JSONArray bodies=state.getJSONArray("blobs");
        if(bodies.length()>90)throw new IllegalArgumentException("Too many blobs");
        java.util.HashSet<Integer> seenIds=new java.util.HashSet<>();
        for(int j=0;j<bodies.length();j++){
            JSONObject body=bodies.getJSONObject(j);
            int tier=body.getInt("tier");
            if(tier<0||tier>64)throw new IllegalArgumentException("Invalid tier");
            Blob b=new Blob(210,400,tier,new Random());
            // Previous VERSION=1 saves have no blobId: assign new IDs below.
            int id=body.optInt("blobId",0);
            if(id<0||id>=Integer.MAX_VALUE||(id>0&&!seenIds.add(id)))
                throw new IllegalArgumentException("Invalid or duplicate blob ID");
            b.id=id;
            b.age=(float)body.getDouble("age");
            b.radiusTarget=(float)body.getDouble("radiusTarget");
            b.launchScale=(float)body.getDouble("launchScale");
            JSONArray nodes=body.getJSONArray("nodes");
            if(nodes.length()!=Blob.NODES)throw new IllegalArgumentException("Wrong membrane");
            for(int i=0;i<Blob.NODES;i++){
                JSONArray node=nodes.getJSONArray(i);
                if(node.length()!=4)throw new IllegalArgumentException("Wrong node");
                b.px[i]=checked(node.getDouble(0));
                b.py[i]=checked(node.getDouble(1));
                b.vx[i]=checked(node.getDouble(2));
                b.vy[i]=checked(node.getDouble(3));
            }
            b.recenter();w.blobs.add(b);
        }
        int savedNextId=state.optInt("nextBlobId",1);
        if(savedNextId<=0||savedNextId>=Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid next blob ID");
        w.nextBlobId=savedNextId;
        w.ensureBlobIds();
        JSONArray links=state.getJSONArray("links");
        for(int i=0;i<links.length();i++){
            JSONArray link=links.getJSONArray(i);
            int a=link.getInt(0),b=link.getInt(1),ai=link.getInt(2),bi=link.getInt(3);
            if(a<0||b<0||a>=w.blobs.size()||b>=w.blobs.size()||
                    ai<0||bi<0||ai>=Blob.NODES||bi>=Blob.NODES)continue;
            BlobWorld.Adhesion adhesion=new BlobWorld.Adhesion(w.blobs.get(a),w.blobs.get(b),ai,bi);
            adhesion.age=checked(link.getDouble(4));
            w.adhesions.add(adhesion);
        }
    }

    private static float checked(double value){
        if(!Double.isFinite(value)||Math.abs(value)>100000)
            throw new IllegalArgumentException("Invalid particle");
        return (float)value;
    }
}
