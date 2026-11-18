package com.maryvellous.lavablobs;

import android.graphics.Color;

/**
 * Single source of truth for all visual themes.
 * Only the background and the vessel outline are independent of the theme.
 * Colours in blobs are always exact, opaque Canva HEX values.
 */
final class Palette {
    static final int BACKGROUND_TOP=0xFF322E32;
    static final int BACKGROUND_BOTTOM=0xFF3D3D3D;
    static final int VESSEL_OUTLINE=0xFFE6DFE3;

    static final class Theme {
        final String name;
        final int[] blobs;
        final int primary, secondary, tertiary;
        final int surface, surfaceRaised, border, text, muted;
        final int positive, caution, vesselFill, speck, onPrimary;

        Theme(String name,int[] blobs,int primary,int secondary,int tertiary) {
            if(blobs.length!=9)throw new IllegalArgumentException("Each theme must have 9 blob colours");
            this.name=name;
            this.blobs=blobs.clone();
            this.primary=primary;
            this.secondary=secondary;
            this.tertiary=tertiary;
            surface=mix(BACKGROUND_TOP,primary,.10f);
            surfaceRaised=mix(BACKGROUND_BOTTOM,secondary,.13f);
            border=mix(VESSEL_OUTLINE,primary,.52f);
            text=mix(VESSEL_OUTLINE,primary,.05f);
            muted=mix(VESSEL_OUTLINE,secondary,.30f);
            positive=mix(blobs[0],VESSEL_OUTLINE,.12f);
            caution=mix(blobs[3],VESSEL_OUTLINE,.22f);
            vesselFill=mix(BACKGROUND_TOP,secondary,.17f);
            speck=mix(primary,VESSEL_OUTLINE,.22f);
            onPrimary=relativeLuminance(primary)>.43f?0xFF211A26:0xFFF7F1F3;
        }
        int forTier(int tier){return blobs[Math.floorMod(tier,blobs.length)];}
    }

    private static final Theme[] THEMES={
        new Theme("Prisma",new int[]{
            0xFFDAF301,0xFFE9AE08,0xFFEB8403,0xFFF0420D,0xFFD23876,
            0xFFB435A8,0xFF9937C8,0xFF5A29DA,0xFF2D2383
        },0xFFEB8403,0xFFDAF301,0xFF9937C8),
        new Theme("Aurora",new int[]{
            0xFFA72F38,0xFFE2575A,0xFFEF8F55,0xFFF8DEBB,0xFF49A1A5,
            0xFF1D3058,0xFF9985C2,0xFF773661,0xFF5B5398
        },0xFF49A1A5,0xFFE2575A,0xFF9985C2),
        new Theme("Braci",new int[]{
            0xFFFDA24B,0xFFFB6204,0xFFCB3C06,0xFF980203,0xFF510207,
            0xFF5F1564,0xFF5B5398,0xFFA20096,0xFF0C0305
        },0xFFFB6204,0xFFA20096,0xFF9985C2)
    };

    static final int COUNT=THEMES.length;
    // Legacy reference for screens not yet migrated to Theme; do not use in new UI.
    static final int[] COLORS=THEMES[0].blobs.clone();

    static Theme at(int index){
        return THEMES[Math.max(0,Math.min(COUNT-1,index))];
    }
    static int forTier(int tier){return THEMES[0].forTier(tier);}

    static int alpha(int value,int opacity){
        return (value&0x00FFFFFF)|(Math.max(0,Math.min(255,opacity))<<24);
    }
    static int mix(int a,int b,float ratio){
        float t=Math.max(0f,Math.min(1f,ratio));
        return Color.rgb(
            (int)(Color.red(a)*(1-t)+Color.red(b)*t),
            (int)(Color.green(a)*(1-t)+Color.green(b)*t),
            (int)(Color.blue(a)*(1-t)+Color.blue(b)*t));
    }
    static float relativeLuminance(int color){
        float r=Color.red(color)/255f,g=Color.green(color)/255f,b=Color.blue(color)/255f;
        return .2126f*r+.7152f*g+.0722f*b;
    }
}
