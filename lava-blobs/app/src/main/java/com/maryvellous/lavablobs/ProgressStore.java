package com.maryvellous.lavablobs;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * No account, backend or analytics SDK. Records of local test sessions are
 * stored on the device and can be copied explicitly for balancing.
 */
final class ProgressStore {
    private static final String NAME="lava_blobs";
    private final SharedPreferences prefs;
    private final Context context;

    ProgressStore(Context context) {
        this.context=context;
        prefs=context.getSharedPreferences(NAME,Context.MODE_PRIVATE);
    }
    // Old scoring records stay stored as history; comparisons are only
    // between runs using the new base-10 curve.
    long record(int id) {return prefs.getLong("record_v2_"+id,0L);}
    boolean cleared(int id) {return prefs.getBoolean("clear_"+id,false);}
    int extras(int id) {return Integer.bitCount(prefs.getInt("extras_mask_"+id,0));}
    int highestUnlocked() {
        for(int i=0;i<GameSpec.LEVELS.length;i++) {
            if(!cleared(i))return i;
        }
        return GameSpec.LEVELS.length-1;
    }

    void save(GameSpec spec,BlobWorld world,boolean finished) {
        int extraMask=spec.extrasMask(world.score,world.maxTierReached,world.mergeCount);
        SharedPreferences.Editor editor=prefs.edit();
        if(world.score>record(spec.id))editor.putLong("record_v2_"+spec.id,world.score);
        if(!spec.endless&&!spec.trial) {
            if(finished&&spec.passed(world.score))editor.putBoolean("clear_"+spec.id,true);
            if(finished&&spec.passed(world.score))
                editor.putInt("extras_mask_"+spec.id,
                        prefs.getInt("extras_mask_"+spec.id,0)|extraMask);
        }
        editor.apply();
        try{
            JSONObject session=new JSONObject();
            session.put("version","1.0.3");
            session.put("scoringVersion",2);
            session.put("time",System.currentTimeMillis());
            session.put("mode",spec.trial?"vessel_trial":(spec.endless?"endless":"level"));
            session.put("vesselHeight",Math.round(world.vessel.bottom-world.vessel.top));
            session.put("level",spec.id);
            session.put("vessel",spec.shape);
            session.put("finished",finished);
            session.put("points",world.score);
            session.put("drops",world.dropCount);
            session.put("merges",world.mergeCount);
            session.put("maxSize",world.maxTierReached+1);
            session.put("eraseEarned",world.earnedErases);
            session.put("eraseUsed",world.usedErases);
            session.put("durationSeconds",Math.round(world.time));
            session.put("mergesBySize",new JSONArray(world.mergesByTier));
            JSONArray old=new JSONArray(prefs.getString("session_log","[]"));
            JSONArray updated=new JSONArray();
            updated.put(session);
            for(int i=0;i<Math.min(old.length(),49);i++)updated.put(old.get(i));
            prefs.edit().putString("session_log",updated.toString()).apply();
        }catch(Exception ignored){ /* Do not interrupt an offline game. */ }
    }

    void copyReport() {
        String data=prefs.getString("session_log","[]");
        ClipboardManager cm=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
        if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("Lava Blob test sessions",data));
        Toast.makeText(context,"Dati delle partite copiati",Toast.LENGTH_SHORT).show();
    }
}
