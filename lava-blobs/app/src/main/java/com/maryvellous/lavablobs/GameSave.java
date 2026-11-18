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
        long points(){return data.optLong("score");}
        int drops(){return data.optInt("drops");}
    }

    GameSave(Context context){
        slot=new AtomicFile(new File(context.getFilesDir(),"active_game_v1.json"));
    }

    boolean exists(){return peek()!=null;}
    Snapshot peek(){
        try {
            JSONObject data=new JSONObject(new String(slot.readFully(),StandardCharsets.UTF_8));
            if(data.optInt("version")!=VERSION)return null;
            GameSpec spec=findSpec(data.getInt("id"));
            if(spec==null||data.optBoolean("gameOver"))return null;
            return new Snapshot(spec,data);
        }catch(Exception ignored){return null;}
    }

    static GameSpec findSpec(int id){
        for(GameSpec spec:GameSpec.LEVELS)if(spec.id==id)return spec;
        for(GameSpec spec:GameSpec.ENDLESS)if(spec.id==id)return spec;
        for(GameSpec spec:GameSpec.TRIALS)if(spec.id==id)return spec;
        return null;
    }

    void clear(){slot.delete();}

    boolean save(GameSpec spec,BlobWorld world,float aimX,boolean eraseMode){
        if(world.gameOver){clear();return true;}
        FileOutputStream out=null;
        try {
            JSONObject data=encode(spec,world,aimX,eraseMode);
            byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);
            out=slot.startWrite();
            out.write(bytes);
            slot.finishWrite(out);
            return true;
        }catch(Exception failure){
            if(out!=null)slot.failWrite(out);
            return false;
        }
    }

    static JSONObject encode(GameSpec spec,BlobWorld w,float aimX,boolean eraseMode) throws Exception {
        JSONObject state=new JSONObject();
        state.put("version",VERSION);
        state.put("scoringVersion",2);
        state.put("id",spec.id);
        state.put("height",w.height);
        state.put("time",w.time);
        state.put("score",w.score);
        state.put("drops",w.dropCount);
        state.put("merges",w.mergeCount);
        state.put("maxTier",w.maxTierReached);
        state.put("nextTier",w.nextTier);
        state.put("blockedTime",w.blockedTime);
        state.put("checkClock",w.checkClock);
        state.put("eraseEarned",w.earnedErases);
        state.put("eraseUsed",w.usedErases);
        state.put("eraseCount",w.eraseCount);
        state.put("gameOver",w.gameOver);
        state.put("aimX",aimX);
        state.put("eraseMode",eraseMode);
        state.put("byTier",new JSONArray(w.mergesByTier));
        // java.util.Random is serializable; preserve the future drop sequence
        // and the turbulence randomness across close/reopen.
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ObjectOutputStream stream=new ObjectOutputStream(bytes);
        stream.writeObject(w.random);stream.close();
        state.put("rng",Base64.getEncoder().encodeToString(bytes.toByteArray()));

        JSONArray particles=new JSONArray();
        for(Blob b:w.blobs){
            JSONObject body=new JSONObject();
            body.put("tier",b.tier);
            body.put("age",b.age);
            body.put("radiusTarget",b.radiusTarget);
            body.put("launchScale",b.launchScale);
            JSONArray nodes=new JSONArray();
            for(int i=0;i<Blob.NODES;i++){
                JSONArray node=new JSONArray();
                node.put(b.px[i]);node.put(b.py[i]);
                node.put(b.vx[i]);node.put(b.vy[i]);
                nodes.put(node);
            }
            body.put("nodes",nodes);
            particles.put(body);
        }
        state.put("blobs",particles);
        JSONArray links=new JSONArray();
        for(BlobWorld.Adhesion link:w.adhesions){
            int ia=w.blobs.indexOf(link.a),ib=w.blobs.indexOf(link.b);
            if(ia<0||ib<0)continue;
            JSONArray item=new JSONArray();
            item.put(ia);item.put(ib);item.put(link.ai);item.put(link.bi);item.put(link.age);
            links.put(item);
        }
        state.put("links",links);
        return state;
    }

    static void restore(BlobWorld w,JSONObject state) throws Exception {
        if(state.getInt("version")!=VERSION||state.getInt("id")!=w.spec.id)
            throw new IllegalArgumentException("Incompatible save");
        w.reset();
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
            long migrated=0L;
            for(int i=0;i<w.mergesByTier.length;i++)
                migrated+=(long)w.mergesByTier[i]*GameSpec.pointsForMerge(i);
            w.score=migrated;
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
        for(int j=0;j<bodies.length();j++){
            JSONObject body=bodies.getJSONObject(j);
            int tier=body.getInt("tier");
            if(tier<0||tier>64)throw new IllegalArgumentException("Invalid tier");
            Blob b=new Blob(210,400,tier,new Random());
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
