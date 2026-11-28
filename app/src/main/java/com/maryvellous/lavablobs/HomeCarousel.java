package com.maryvellous.lavablobs;
final class HomeCarousel {
    static float clamp(float value,int count){return Math.max(0,Math.min(count-1,value));}
    static int nearest(float value,int count){return Math.round(clamp(value,count));}
    static float scale(float distance){return 1f-.16f*Math.min(1.8f,Math.abs(distance));}
    static int target(float start,float position,float delta,float step,int count){
        int n=nearest(position,count);
        if(n==nearest(start,count)&&Math.abs(delta)>step*.16f)n+=delta>0?1:-1;
        return nearest(n,count);
    }
}
