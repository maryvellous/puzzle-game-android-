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
    private final ProgressStore progress;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letter=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path(), glyph=new Path(), warped=new Path();
    private int page=0;
    private boolean chooseEndless=false;
    private long start=System.nanoTime();
    private float downX,downY;

    HomeView(Context context,ProgressStore progress,Listener listener) {
        super(context);
        this.progress=progress;
        this.listener=listener;
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        letter.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
        letter.setTextAlign(Paint.Align.LEFT);
    }

    private float scale(){return Math.min(getWidth()/420f,getHeight()/620f);}
    private float offX(){return(getWidth()-420f*scale())*.5f;}

    @Override protected void onDraw(Canvas raw) {
        super.onDraw(raw);
        if(getWidth()==0||getHeight()==0)return;
        float sc=scale(),h=getHeight()/sc;
        raw.drawColor(0xFF111427);
        raw.save();raw.translate(offX(),0);raw.scale(sc,sc);
        float t=(System.nanoTime()-start)/1_000_000_000f;
        drawTitle(raw,t);
        label(raw,"Un piccolo gioco tutto morbido",210,155,13,0xFFA5ABB9,false);
        rounded(raw,70,170,350,217,24,0xFF233645);
        label(raw,chooseEndless?"←  TORNA AI LIVELLI":"∞  MODALITÀ INFINITA",210,200,15,0xFFDAE9E4,true);
        int count=chooseEndless?3:6;
        float cardH=Math.min(145f,Math.max(95f,(h-330f)/3f));
        float gap=10f,cardW=172f;
        for(int index=0;index<count;index++){
            int row=index/2,col=index%2;
            int levelId=page*6+index;
            GameSpec spec=chooseEndless?GameSpec.ENDLESS[index]:
                    (levelId<GameSpec.LEVELS.length?GameSpec.LEVELS[levelId]:null);
            if(spec==null)continue;
            float x=32+col*(cardW+12),y=239+row*(cardH+gap);
            boolean locked=!spec.endless&&levelId>progress.highestUnlocked();
            drawCard(raw,spec,x,y,cardW,cardH,locked);
        }
        float dotsY=239+3*(cardH+gap)+14;
        if(!chooseEndless) {
            for(int p=0;p<(GameSpec.LEVELS.length+5)/6;p++){
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(p==page?0xFFDB5461:0xFF50586B);
                raw.drawCircle(199+(p-(GameSpec.LEVELS.length+5)/12f)*18,dotsY,4.5f,paint);
            }
            label(raw,"Scorri lateralmente per gli altri livelli",210,dotsY+25,11,0xFF8C96A7,false);
        }else{
            label(raw,"Scegli il contenitore · Erase ogni 20.000 punti",210,dotsY+21,11,0xFFA5ABB9,false);
        }
        rounded(raw,118,h-63,302,h-24,18,0xFF263747);
        label(raw,"COPIA DATI PARTITE",210,h-38,11,0xFFD7E5E8,true);
        raw.restore();
        postInvalidateOnAnimation();
    }

    private void drawTitle(Canvas c,float t) {
        String title="Lava Blob";
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

    private void drawCard(Canvas c,GameSpec spec,float x,float y,float w,float h,boolean locked) {
        rounded(c,x,y,x+w,y+h,20,locked?0xFF202A37:0xFF253845);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(locked?0xFF34414F:0xFF416775);
        c.drawRoundRect(new RectF(x,y,x+w,y+h),20,20,paint);
        Vessel vessel=new Vessel(spec.shape,850f);
        float factor=(h-33f)/(vessel.bottom-vessel.top);
        factor=Math.min(factor,.31f);
        float iconCenter=x+43f;
        float baseline=y+(h-(vessel.bottom-vessel.top)*factor)*.5f;
        path.reset();
        for(int i=0;i<vessel.xs.length;i++) {
            float px=iconCenter+(vessel.xs[i]-210f)*factor;
            float py=baseline+(vessel.ys[i]-vessel.top)*factor;
            if(i==0)path.moveTo(px,py);else path.lineTo(px,py);
        }
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(locked?0xFF344351:0xFF19363D);
        c.drawPath(path,paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.4f);
        paint.setColor(locked?0xFF5E6873:Palette.COLORS[Math.abs(spec.shape)%9]);
        c.drawPath(path,paint);
        float lx=x+82;
        if(spec.endless) {
            label(c,"∞",lx+25,y+h*.34f,30,0xFFE6E9E9,true);
            label(c,spec.name.substring(11),lx+30,y+h*.66f,13,0xFFE6E9E9,true);
            label(c,"Senza limite",lx+30,y+h*.83f,10,0xFFA0B1B9,false);
            return;
        }
        String name=spec.name;
        label(c,""+(spec.id+1),lx+30,y+h*.31f,25,locked?0xFF737E88:0xFFECF4EF,true);
        label(c,name,lx+30,y+h*.52f,12,locked?0xFF78858D:0xFFBDD9DB,true);
        if(locked) {
            label(c,"Da sbloccare",lx+30,y+h*.72f,10,0xFF77858C,false);
        }else{
            label(c,progress.cleared(spec.id)?"Completato ✓":"Obiettivo "+spec.minScore,
                    lx+30,y+h*.70f,10,0xFFAED0C0,false);
            if(progress.cleared(spec.id))label(c,
                    "Extra "+progress.extras(spec.id)+"/"+spec.totalExtras(),
                    lx+30,y+h*.84f,10,0xFFDBB7B9,false);
        }
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
                if(Math.abs(x-downX)>48f&&!chooseEndless) {
                    page=Math.max(0,Math.min((GameSpec.LEVELS.length-1)/6,
                            page+(x<downX?1:-1)));
                    invalidate();return true;
                }
                if(y>=170&&y<=220) {
                    chooseEndless=!chooseEndless;page=0;invalidate();return true;
                }
                if(y>=h-71) {progress.copyReport();return true;}
                float cardH=Math.min(145f,Math.max(95f,(h-330f)/3f));
                for(int i=0;i<(chooseEndless?3:6);i++){
                    float lx=32+(i%2)*184f,ly=239+(i/2)*(cardH+10f);
                    if(x>=lx&&x<=lx+172&&y>=ly&&y<=ly+cardH) {
                        int id=page*6+i;
                        if(chooseEndless)listener.start(GameSpec.ENDLESS[i]);
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
