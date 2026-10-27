package com.maryvellous.lavablobs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * Six levels per horizontal page, plus three immediately available endless
 * vessels. Logo letters really deform, rather than wobbling a static label.
 */
public final class HomeView extends View {
    interface Listener { void start(GameSpec spec); }

    private final Listener listener;
    private final Runnable resume;
    private final GameSave.Snapshot saved;
    private final ProgressStore progress;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letter=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path(), glyph=new Path(), warped=new Path();
    private int page=0;
    // 0 = regular levels, 1 = endless, 2 = vessel-height lab.
    private int section=0;
    private long start=System.nanoTime();
    private float downX,downY;

    HomeView(Context context,ProgressStore progress,Listener listener,
             Runnable resume,GameSave gameSave) {
        super(context);
        this.progress=progress;
        this.listener=listener;
        this.resume=resume;
        this.saved=gameSave.peek();
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        letter.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
        letter.setTextAlign(Paint.Align.LEFT);
    }

    private float scale(){return Math.min(getWidth()/420f,getHeight()/780f);}
    private float offX(){return(getWidth()-420f*scale())*.5f;}

    private float shift(){return saved==null?0f:56f;}
    private float cardsTop(){return 239f+shift();}
    private float cardHeight(float h){
        return Math.min(134f,Math.max(88f,(h-430f-shift())/3f));
    }

    @Override protected void onDraw(Canvas raw) {
        super.onDraw(raw);
        if(getWidth()==0||getHeight()==0)return;
        float sc=scale(),h=getHeight()/sc;
        raw.drawColor(0xFF11111F);
        raw.save();raw.translate(offX(),0);raw.scale(sc,sc);
        float t=(System.nanoTime()-start)/1_000_000_000f;
        drawTitle(raw,t);
        label(raw,"Un piccolo gioco tutto morbido",210,151,12,0xFFC5B8CC,false);

        if(saved!=null){
            rounded(raw,32,162,388,208,17,0xFF53334A);
            paint.setColor(0xFFDB5461);
            raw.drawCircle(52,185,5,paint);
            labelLeft(raw,"CONTINUA · "+saved.spec.name,68,183,13,0xFFF6E8EE,true,292);
            labelLeft(raw,saved.points()+" punti · "+saved.drops()+" drop",68,200,10,0xFFE6BDCB,false,288);
        }

        float navY=174+shift();
        rounded(raw,32,navY,204,navY+44,17,section==1?0xFF683851:0xFF252A3A);
        rounded(raw,216,navY,388,navY+44,17,section==2?0xFF683851:0xFF252A3A);
        label(raw,section==1?"← LIVELLI":"∞  INFINITA",118,navY+28,13,0xFFF1DEE6,true);
        label(raw,section==2?"← LIVELLI":"PROVE",302,navY+28,13,0xFFF1DEE6,true);

        int count=section==0?6:3;
        float cardH=cardHeight(h);
        float top=cardsTop();
        for(int index=0;index<count;index++){
            int levelId=page*6+index;
            GameSpec spec=section==1?GameSpec.ENDLESS[index]:
                    section==2?GameSpec.TRIALS[index]:
                    (levelId<GameSpec.LEVELS.length?GameSpec.LEVELS[levelId]:null);
            if(spec==null)continue;
            float x=31+(index%2)*184f,y=top+(index/2)*(cardH+10f);
            boolean locked=!spec.endless&&!spec.trial&&levelId>progress.highestUnlocked();
            drawCard(raw,spec,x,y,172,cardH,locked);
        }

        float infoY=top+3*(cardH+10f)+4;
        if(section==0){
            int pages=(GameSpec.LEVELS.length+5)/6;
            for(int i=0;i<pages;i++){
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(page==i?0xFFDB5461:0xFF555366);
                raw.drawCircle(210+(i-(pages-1)*.5f)*17,infoY,4.5f,paint);
            }
            label(raw,"Scorri per altri livelli",210,infoY+25,11,0xFFAFA5B8,false);
        }else if(section==1){
            label(raw,"Scegli un recipiente · Erase ogni 20.000 punti",210,infoY+21,
                    11,0xFFB8B1C5,false);
        }else{
            label(raw,"Confronta dimensioni e durata delle partite",210,infoY+21,
                    11,0xFFB8B1C5,false);
        }

        rounded(raw,118,h-64,302,h-25,17,0xFF242939);
        label(raw,"COPIA DATI PARTITE",210,h-39,11,0xFFD3C6D5,true);
        raw.restore();
        postInvalidateOnAnimation();
    }

    private void drawTitle(Canvas c,float t) {
        String title="Lava Blob";
        letter.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
        letter.setTextAlign(Paint.Align.LEFT);
        letter.setTextSize(58f);
        float total=letter.measureText(title);
        float x=(420-total)*.5f;
        for(int i=0;i<title.length();i++){
            String ch=title.substring(i,i+1);
            float adv=letter.measureText(ch);
            if(ch.equals(" ")){x+=adv;continue;}
            glyph.reset();
            letter.getTextPath(ch,0,1,x,119,glyph);
            warped.reset();
            warped.setFillType(Path.FillType.EVEN_ODD);
            PathMeasure measure=new PathMeasure(glyph,false);
            do {
                float length=measure.getLength();
                if(length<=0)continue;
                float[] pos=new float[2],tan=new float[2];
                int steps=Math.max(12,(int)(length/2.4f));
                for(int j=0;j<=steps;j++) {
                    float s=length*j/steps;
                    measure.getPosTan(s,pos,tan);
                    float wobble=(float)(1.4f*Math.sin(t*2.3+i*.84+s*.056)
                            +.8f*Math.sin(t*3.1-i*.5+s*.12));
                    float tx=pos[0]-tan[1]*wobble;
                    float ty=pos[1]+tan[0]*wobble;
                    if(j==0)warped.moveTo(tx,ty);else warped.lineTo(tx,ty);
                }
                warped.close();
            }while(measure.nextContour());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Palette.COLORS[i%Palette.COLORS.length]);
            c.drawPath(warped,paint);
            x+=adv;
        }
    }

    private void drawCard(Canvas c,GameSpec spec,float x,float y,
            float width,float height,boolean locked){
        rounded(c,x,y,x+width,y+height,16,locked?0xFF1B202C:0xFF202938);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.1f);
        paint.setColor(locked?0xFF414353:0xFF515165);
        c.drawRoundRect(new RectF(x,y,x+width,y+height),16,16,paint);
        paint.setStyle(Paint.Style.FILL);

        // Entire preview is fitted and clipped to its own miniature panel.
        float iconLeft=x+10,iconTop=y+10,iconRight=x+77,iconBottom=y+height-10;
        c.save();
        c.clipRect(iconLeft,iconTop,iconRight,iconBottom);
        rounded(c,iconLeft,iconTop,iconRight,iconBottom,11,0xFF162A32);
        Vessel v=new Vessel(spec.shape,850f);
        float minX=Float.MAX_VALUE,maxX=-Float.MAX_VALUE;
        for(float px:v.xs){minX=Math.min(minX,px);maxX=Math.max(maxX,px);}
        float factor=Math.min((iconRight-iconLeft-12)/(maxX-minX),
                (iconBottom-iconTop-12)/(v.bottom-v.top));
        float cx=(iconLeft+iconRight)*.5f,cy=(iconTop+iconBottom)*.5f;
        path.reset();
        for(int i=0;i<v.xs.length;i++){
            float px=cx+(v.xs[i]-(maxX+minX)*.5f)*factor;
            float py=cy+(v.ys[i]-(v.top+v.bottom)*.5f)*factor;
            if(i==0)path.moveTo(px,py);else path.lineTo(px,py);
        }
        path.close();
        paint.setColor(0xFF1E3640);paint.setStyle(Paint.Style.FILL);
        c.drawPath(path,paint);
        c.save();c.clipPath(path);
        // Keep the interior glass tint and its little floating specks.
        paint.setColor(0x665B89A2);
        for(int i=0;i<7;i++)c.drawCircle(iconLeft+13+i*8f,
                iconTop+12+(i*29)%(int)Math.max(1,height-22),
                1.05f,paint);
        c.restore();
        paint.setColor(locked?0xFF555665:Palette.forTier(Math.abs(spec.shape)%9));
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.1f);
        c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        c.restore();

        float tx=x+87,ty=y+height*.25f;
        if(spec.trial){
            labelLeft(c,"PROVA "+(-9-spec.id),tx,ty+3,15,0xFFEEDDE6,true,74);
            labelLeft(c,""+Math.round(v.bottom-v.top)+" unità",tx,ty+24,11,0xFFC9BCCB,false,75);
            labelLeft(c,"Gioca",tx,y+height-15,10,0xFFDD8FAD,true,73);
        }else if(spec.endless){
            labelLeft(c,"INFINITA",tx,ty+3,14,0xFFF0DDE6,true,76);
            labelLeft(c,spec.name.substring(11),tx,ty+24,11,0xFFCCC0D0,false,76);
            labelLeft(c,"Senza limite",tx,y+height-15,10,0xFFBCAEC5,false,77);
        }else{
            labelLeft(c,""+(spec.id+1),tx,ty+5,24,
                    locked?0xFF8C8995:0xFFF0E3EA,true,75);
            labelLeft(c,spec.name,tx,ty+25,12,
                    locked?0xFF8C8995:0xFFE7DCE7,true,76);
            labelLeft(c,locked?"Bloccato":
                    progress.cleared(spec.id)?"Completato ✓":"Da giocare",
                    tx,y+height-28,10,locked?0xFF8D8B9A:0xFFB9CAC9,false,75);
            if(!locked&&progress.cleared(spec.id))
                labelLeft(c,"Extra "+progress.extras(spec.id)+"/"+spec.totalExtras(),
                        tx,y+height-13,10,0xFFE0A5C0,false,75);
        }
    }

    private void labelLeft(Canvas canvas,String text,float x,float y,float font,
            int color,boolean bold,float maxWidth) {
        letter.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        letter.setTextAlign(Paint.Align.LEFT);letter.setTextSize(font);
        float width=letter.measureText(text);
        if(width>maxWidth)letter.setTextSize(Math.max(8f,font*maxWidth/width));
        letter.setColor(color);
        canvas.drawText(text,x,y,letter);
    }

    private static void rounded(Canvas c,float l,float t,float r,float b,float radius,int color) {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);
        c.drawRoundRect(new RectF(l,t,r,b),radius,radius,p);
    }
    private void label(Canvas c,String s,float x,float y,float size,int color,boolean bold) {
        letter.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        letter.setTextAlign(Paint.Align.CENTER);
        letter.setTextSize(size);letter.setColor(color);
        c.drawText(s,x,y,letter);
        letter.setTextAlign(Paint.Align.LEFT);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if(getWidth()==0||getHeight()==0)return false;
        float x=(e.getX()-offX())/scale(),y=e.getY()/scale(),h=getHeight()/scale();
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downX=x;downY=y;return true;
            case MotionEvent.ACTION_UP:
                if(Math.abs(x-downX)>47f&&section==0){
                    page=Math.max(0,Math.min((GameSpec.LEVELS.length-1)/6,
                            page+(x<downX?1:-1)));
                    invalidate();return true;
                }
                if(saved!=null&&y>=158f&&y<=210f){
                    resume.run();return true;
                }
                float nav=174+shift();
                if(y>=nav&&y<=nav+44){
                    if(x>=32&&x<=204)section=section==1?0:1;
                    else if(x>=216&&x<=388)section=section==2?0:2;
                    page=0;invalidate();return true;
                }
                if(y>=h-72&&y<=h-20){progress.copyReport();return true;}
                float cardH=cardHeight(h),top=cardsTop();
                for(int i=0;i<(section==0?6:3);i++){
                    float lx=31+(i%2)*184f,ly=top+(i/2)*(cardH+10);
                    if(x>=lx&&x<=lx+172&&y>=ly&&y<=ly+cardH){
                        int id=page*6+i;
                        if(section==1)listener.start(GameSpec.ENDLESS[i]);
                        else if(section==2)listener.start(GameSpec.TRIALS[i]);
                        else if(id<GameSpec.LEVELS.length&&id<=progress.highestUnlocked())
                            listener.start(GameSpec.LEVELS[id]);
                        return true;
                    }
                }
                return true;
            default:return true;
        }
    }
}
