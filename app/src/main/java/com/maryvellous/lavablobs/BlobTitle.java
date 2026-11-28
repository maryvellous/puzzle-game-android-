package com.maryvellous.lavablobs;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Typeface;

/**
 * The ONE animated Lava Blob wordmark used both on the Home and in-game HUD.
 * Its letter contours and two soft sine deformations match the original Home.
 */
final class BlobTitle {
    private BlobTitle() {}
    static int backing(int index){return index==0?8:index==1?5:8;}
    /**
     * Organic wordmark: retain the original typeface and contours, but use
     * a smooth displacement field in glyph coordinates, not tangent-based
     * offsets along contours. All closed paths remain closed and counters
     * use the same field as their surrounding outline.
     */
    static void drawHome(Canvas c,Paint letters,Paint paint,Path glyph,Path warped,
            Palette.Theme theme,int index,float t,float size,float baseline){
        Paint.Align align=letters.getTextAlign();
        letters.setTextAlign(Paint.Align.LEFT);
        letters.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
        letters.setTextSize(size);
        String word="Lava Blob";float x=(420-letters.measureText(word))*.5f;
        int[] colors=index==0?new int[]{1,2,4,6}:index==1?new int[]{2,3,4,6}:new int[]{0,1,6,7};
        float unit=size/58f;
        float[] pos=new float[2];
        PathMeasure measure=new PathMeasure();
        for(int i=0;i<word.length();i++){
            String ch=word.substring(i,i+1);float advance=letters.measureText(ch);
            if(!ch.equals(" ")){
                glyph.reset();letters.getTextPath(ch,0,1,x,baseline,glyph);
                warped.reset();warped.setFillType(glyph.getFillType());measure.setPath(glyph,true);
                do{
                    float len=measure.getLength();if(len<=0)continue;
                    int steps=Math.max(16,(int)Math.ceil(len/(1.15f*unit)));
                    // Sample [0,length), then close exactly to the first point.
                    for(int j=0;j<steps;j++){
                        if(!measure.getPosTan(len*j/steps,pos,null))continue;
                        float u=(pos[0]-x)/unit,v=(pos[1]-baseline)/unit;
                        float dx=unit*(float)(2.8*Math.sin(v*.092+i*.83)+.8*Math.sin(u*.075+v*.06+t*.62));
                        float dy=unit*(float)(2.6*Math.sin(u*.13+i*.9)+.65*Math.sin(v*.1+t*.72+i));
                        if(j==0)warped.moveTo(pos[0]+dx,pos[1]+dy);
                        else warped.lineTo(pos[0]+dx,pos[1]+dy);
                    }
                    warped.close();
                }while(measure.nextContour());
                paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(theme.forTier(colors[i%4]));
                c.drawPath(warped,paint);
            }x+=advance;
        }
        letters.setTextAlign(align);
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
