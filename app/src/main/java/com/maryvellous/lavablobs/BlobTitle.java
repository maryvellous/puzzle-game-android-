package com.maryvellous.lavablobs;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Typeface;

/**
 * Home logo built from explicit soft shapes. Legacy HUD wordmark is retained
 * separately for compatibility; the Home never uses typeface contours.
 */
final class BlobTitle {
    private BlobTitle() {}
    static int backing(int index){return index==0?8:index==1?5:8;}
    // Hand-built soft bodies, not glyphs. Eight letters are independent
    // shapes with rounded lobes and explicit counters; no font is sampled.
    private static final float[] ADVANCE={38,40,38,40,46,26,40,40};
    private static final int[][] COLORS={{1,2,4,6},{2,3,4,6},{0,1,6,7}};
    static void drawHome(Canvas c,Paint letters,Paint paint,Path glyph,Path warped,
            Palette.Theme theme,int index,float t,float size,float baseline){
        final float unit=size/55f;
        final float width=308f+15f; // eight advances + word gap
        final int[] colors=COLORS[Math.max(0,Math.min(2,index))];
        float x=210f-width*unit*.5f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<ADVANCE.length;i++){
            if(i==4)x+=15f*unit;
            float phase=t*1.65f+i*.88f;
            float wave=(float)Math.sin(phase);
            float breath=(float)Math.sin(phase+.95f);
            c.save();
            c.translate(x,baseline+2.2f*wave*unit);
            c.scale(unit,unit);
            c.scale(1+.055f*breath,1-.075f*breath,ADVANCE[i]*.5f,-21f);
            paint.setColor(theme.forTier(colors[i%4]));
            body(c,paint,glyph,i,wave);
            c.restore();
            x+=ADVANCE[i]*unit;
        }
    }

    private static void body(Canvas c,Paint p,Path q,int i,float w){
        q.reset();
        q.setFillType(Path.FillType.WINDING);
        switch(i){
            case 0: // L: bent, pooled soft body
                q.moveTo(9,-47);q.cubicTo(2,-46,1,-39,3,-29);
                q.cubicTo(4,-19,1,-8,4,-3);q.cubicTo(8,3,20,1,30,0);
                q.cubicTo(40,-1,39,-13,30,-13);q.cubicTo(24,-13,20,-10,16,-12);
                q.cubicTo(13,-15,17,-31,16,-40);q.cubicTo(16,-45,13,-48,9,-47);
                q.close();c.drawPath(q,p);break;
            case 1: case 3: // a: annular blob plus a fused right-hand lobe
                ring(c,p,q,18,-15,17,17,8,8,w);
                q.reset();q.moveTo(27,-29);q.cubicTo(30,-37,38,-33,37,-24);
                q.cubicTo(35,-14,39,-6,36,-1);q.cubicTo(32,4,26,1,26,-6);
                q.cubicTo(28,-13,25,-22,27,-29);q.close();c.drawPath(q,p);break;
            case 2: // v: two merged lobes around a soft valley
                q.moveTo(4,-33);q.cubicTo(0,-30,4,-21,7,-14);
                q.cubicTo(10,-7,12,2,20,2);q.cubicTo(28,2,31,-12,35,-23);
                q.cubicTo(39,-34,28,-38,25,-29);q.cubicTo(23,-23,22,-17,20,-12);
                q.cubicTo(17,-18,17,-27,12,-32);q.cubicTo(10,-35,7,-35,4,-33);
                q.close();c.drawPath(q,p);break;
            case 4: // B: upright and two connected ring-shaped blobs
                ring(c,p,q,23,-34,19,14,8,6,w);
                ring(c,p,q,24,-13,20,15,9,7,-w);
                q.reset();q.moveTo(9,-48);q.cubicTo(1,-48,2,-38,3,-28);
                q.cubicTo(3,-16,0,-4,5,0);q.cubicTo(9,4,17,1,16,-7);
                q.cubicTo(14,-18,17,-34,16,-41);q.cubicTo(16,-46,13,-48,9,-48);
                q.close();c.drawPath(q,p);break;
            case 5: // l: upright droplet, slightly pooled at its base
                q.moveTo(11,-47);q.cubicTo(2,-46,3,-34,4,-24);
                q.cubicTo(5,-14,1,-6,5,-2);q.cubicTo(9,3,21,3,22,-4);
                q.cubicTo(23,-11,15,-10,15,-16);q.cubicTo(15,-28,20,-47,11,-47);
                q.close();c.drawPath(q,p);break;
            case 6: // o: soft torus
                ring(c,p,q,19,-15,18,17,8,8,w);break;
            case 7: // b: soft torus with a tall fused lobe
                ring(c,p,q,21,-15,17,17,8,8,w);
                q.reset();q.moveTo(9,-47);q.cubicTo(1,-47,2,-37,3,-28);
                q.cubicTo(4,-17,0,-7,4,-2);q.cubicTo(7,3,15,2,15,-5);
                q.cubicTo(13,-15,17,-31,16,-40);q.cubicTo(16,-45,13,-48,9,-47);
                q.close();c.drawPath(q,p);break;
        }
    }

    // Explicit outer and inner soft-body contours. Both breathe together,
    // so the negative space stays open throughout the animation.
    private static void ring(Canvas c,Paint p,Path q,float x,float y,
            float rx,float ry,float ix,float iy,float w){
        q.reset();q.setFillType(Path.FillType.EVEN_ODD);
        contour(q,x,y,rx,ry,w);
        contour(q,x,y,ix,iy,w*.5f);
        c.drawPath(q,p);
        q.setFillType(Path.FillType.WINDING);
    }
    private static void contour(Path q,float x,float y,float rx,float ry,float w){
        float k=.5523f;
        q.moveTo(x,y-ry);
        q.cubicTo(x+k*rx,y-ry-w,x+rx,y-k*ry,x+rx,y);
        q.cubicTo(x+rx,y+k*ry,x+k*rx,y+ry+w,x,y+ry);
        q.cubicTo(x-k*rx,y+ry,x-rx,y+k*ry,x-rx,y);
        q.cubicTo(x-rx,y-k*ry,x-k*rx,y-ry,x,y-ry);
        q.close();
    }

    static void draw(Canvas c, Paint letters, Paint paint, Path glyph,
            Path warped, Palette.Theme theme, float time,
            float size, float baseline) {
        final Paint.Align previousAlign=letters.getTextAlign();
        final String title="Lava Blob";
        letters.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
        letters.setTextAlign(Paint.Align.LEFT);
        letters.setTextSize(size);
        float total=letters.measureText(title);
        float x=(420f-total)*.5f;
        final float deformation=size/58f;
        for(int i=0;i<title.length();i++) {
            String ch=title.substring(i,i+1);
            float advance=letters.measureText(ch);
            if(ch.equals(" ")){x+=advance;continue;}
            glyph.reset();
            letters.getTextPath(ch,0,1,x,baseline,glyph);
            warped.reset();
            warped.setFillType(Path.FillType.EVEN_ODD);
            PathMeasure measure=new PathMeasure(glyph,false);
            do {
                float length=measure.getLength();
                if(length<=0)continue;
                float[] pos=new float[2],tan=new float[2];
                int steps=Math.max(12,(int)(length/(2.4f*deformation)));
                for(int j=0;j<=steps;j++) {
                    float arc=length*j/steps;
                    measure.getPosTan(arc,pos,tan);
                    float normalized=arc/deformation;
                    float wobble=deformation*(float)(
                            1.4f*Math.sin(time*2.3f+i*.84f+normalized*.056f)
                            +.8f*Math.sin(time*3.1f-i*.5f+normalized*.12f));
                    float tx=pos[0]-tan[1]*wobble;
                    float ty=pos[1]+tan[0]*wobble;
                    if(j==0)warped.moveTo(tx,ty);else warped.lineTo(tx,ty);
                }
                warped.close();
            }while(measure.nextContour());
            int titleColor=theme.forTier(i%theme.blobs.length);
            titleColor=Palette.relativeLuminance(titleColor)<.12f
                    ?theme.primary:titleColor;
            paint.setShader(null);
            // Light citrus/yellow letters need an ink edge against the new
            // ivory title cushion. Keep the original deforming contours.
            if(Palette.relativeLuminance(titleColor)>.58f){
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setStrokeWidth(Math.max(1.2f,size*.045f));
                paint.setColor(Palette.alpha(theme.onSurface,190));
                c.drawPath(warped,paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(titleColor);
            c.drawPath(warped,paint);
            x+=advance;
        }
        letters.setTextAlign(previousAlign);
    }
}
