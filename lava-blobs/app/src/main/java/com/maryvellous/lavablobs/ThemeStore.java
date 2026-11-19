package com.maryvellous.lavablobs;

import android.content.Context;
import android.content.SharedPreferences;

/** A local cosmetic preference, deliberately separate from game saves and progression. */
final class ThemeStore {
    private static final String KEY="active_theme_v1";
    private final SharedPreferences preferences;

    ThemeStore(Context context){
        preferences=context.getSharedPreferences("lava_blobs",Context.MODE_PRIVATE);
    }
    int selection(){
        int index=preferences.getInt(KEY,0);
        return index>=0&&index<Palette.COUNT?index:0;
    }
    Palette.Theme current(){return Palette.at(selection());}
    boolean select(int index){
        if(index<0||index>=Palette.COUNT)return false;
        preferences.edit().putInt(KEY,index).apply();
        return true;
    }
}
