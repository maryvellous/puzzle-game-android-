package com.maryvellous.lavablobs;

/** Presentation-only layout. All body positions and rules remain in world units. */
final class GameConsoleLayout {
    static float panelHeight(float height){return 204f+Math.max(0f,Math.min(60f,(height-780f)*.52f));}
    static float panelTop(float height){return height-panelHeight(height);}
    static float panelY(float height,float original){return panelTop(height)+original*panelHeight(height)/204f;}
    static float vesselScale(float height,float width,float bodyHeight){
        float target=1f+Math.max(0f,Math.min(.14f,(height-780f)/680f));
        float limitWidth=378f/Math.max(1f,width);
        float limitHeight=(visualBottom(height)-98f)/Math.max(1f,bodyHeight);
        return Math.max(.8f,Math.min(target,Math.min(limitWidth,limitHeight)));
    }
    static float entryY(float height){return panelTop(height)-42f;}
    static float visualBottom(float height){return panelTop(height)-88f;}
    static float toScreenX(float x,float scale){return 210f+(x-210f)*scale;}
    static float toWorldX(float x,float scale){return 210f+(x-210f)/scale;}
    static float toScreenY(float y,float bottom,float scale,float height){return visualBottom(height)+(y-bottom)*scale;}
    static float toWorldY(float y,float bottom,float scale,float height){return bottom+(y-visualBottom(height))/scale;}
}
