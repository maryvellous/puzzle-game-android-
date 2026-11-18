package com.maryvellous.lavablobs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import java.util.HashMap;

/**
 * Minimal, theme-aware home screen. The hand-deformed Lava Blob title is
 * deliberately preserved; visual hierarchy and all other surfaces are new.
 * The content scrolls on smaller devices rather than shrinking touch targets.
 */
public final class HomeView extends View {
    interface Listener { void start(GameSpec spec); }
    private static final float W=420f;
    private static final float MARGIN=29f;
    private static final float CARD_WIDTH=176f;
    private static final float CARD_HEIGHT=99f;
    private static final float CARD_GAP=10f;
    private static final Typeface NORMAL=Typeface.create("sans-serif",Typeface.NORMAL);
    private static final Typeface MEDIUM=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    private static final Typeface HEAVY=Typeface.create("sans-serif-black",Typeface.BOLD);

    private final Listener listener;
    private final Runnable resume;
    private final GameSave.Snapshot saved;
    private final ProgressStore progress;
    private final ThemeStore themeStore;
    private Palette.Theme theme;
    private int selectedTheme;

    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letter=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path(), glyph=new Path(), warped=new Path();
    private final HashMap<Integer,Vessel> miniatures=new HashMap<>();
    private Shader backdrop;
    private float backdropHeight=-1f;
    private final long start=System.nanoTime();

    private int page=0,section=0;
    private float scrollY,gestureStartX,gestureStartY,scrollOnDown;
    private boolean dragged;

    HomeView(Context context,ProgressStore progress,Listener listener,
             Runnable resume,GameSave gameSave) {
        super(context);
        this.progress=progress;
        this.listener=listener;
        this.resume=resume;
        this.saved=gameSave.peek();
        themeStore=new ThemeStore(context);
        selectedTheme=themeStore.selection();
        theme=themeStore.current();
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        letter.setTextAlign(Paint.Align.LEFT);
    }

    private float scale(){return getWidth()/W;}
    private float offX(){return 0f;}
    private float navTop(){return saved==null?154f:228f;}
    private float cardTop(){return navTop()+57f;}
    private float cardBottom(){return cardTop()+3*CARD_HEIGHT+2*CARD_GAP;}
    private float themesTop(){return cardBottom()+39f;}
    private float themesCardsTop(){return themesTop()+18f;}
    private float copyTop(){return themesCardsTop()+82f;}
    private float contentHeight(){return copyTop()+59f;}
    private float viewportHeight(){return getHeight()/Math.max(.001f,scale());}
    private float maxScroll(){return Math.max(0f,contentHeight()-viewportHeight());}

    @Override protected void onDraw(Canvas raw) {
        super.onDraw(raw);
        if(getWidth()==0||getHeight()==0)return;
        float sc=scale(),h=viewportHeight();
        scrollY=Math.min(scrollY,maxScroll());
        raw.drawColor(Palette.BACKGROUND_TOP);
        raw.save();
        raw.scale(sc,sc);
        drawBackdrop(raw,h);
        raw.translate(0,-scrollY);
        drawTitle(raw,(System.nanoTime()-start)/1_000_000_000f);
        if(saved!=null)drawResume(raw);
        drawNavigation(raw);
        drawLevels(raw);
        drawThemes(raw);
        drawCopy(raw);
        raw.restore();
        postInvalidateOnAnimation();
    }

    private void drawBackdrop(Canvas c,float h){
        if(backdrop==null||Math.abs(backdropHeight-h)>.25f){
            backdrop=new LinearGradient(0f,0f,W,h,Palette.BACKGROUND_TOP,
                    Palette.BACKGROUND_BOTTOM,Shader.TileMode.CLAMP);
            backdropHeight=h;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(backdrop);
        c.drawRect(0,0,W,h,paint);
        paint.setShader(null);
    }

    // Original deforming title implementation retained intentionally.
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
            int titleColor=theme.forTier(i%theme.blobs.length);
            // Theme 3 includes near-black as a blob. Keep the animated logo readable
            // against the constant dark background without recolouring the blobs.
            paint.setColor(Palette.relativeLuminance(titleColor)<.12f?
                    theme.primary:titleColor);
            c.drawPath(warped,paint);
            x+=adv;
        }
    }

    private void drawResume(Canvas c){
        final float l=MARGIN,t=142f,r=W-MARGIN,b=214f;
        panel(c,l,t,r,b,19f,theme.surfaceRaised,Palette.mix(theme.surface,theme.border,.58f),1.2f);
        drawPreview(c,saved.spec.shape,l+11,t+8,l+75,b-8,false);
        text(c,"PARTITA SALVATA",l+87,t+19,10f,theme.muted,false,243f);
        text(c,"Continua",l+87,t+43,22f,theme.text,true,230f);
        String detail=(saved.spec.endless?"Infinita":saved.spec.trial?"Prova":"Livello "+(saved.spec.id+1))
                +" · "+saved.points()+" punti";
        text(c,detail,l+87,t+61,11.5f,theme.muted,false,235f);
        textCentered(c,"›",r-17,t+46,34,theme.primary,true);
    }

    private void drawNavigation(Canvas c){
        float y=navTop();
        panel(c,MARGIN,y,W-MARGIN,y+43f,22f,theme.surface,
                Palette.mix(theme.surface,theme.border,.46f),1f);
        float l=section==0?MARGIN+3:W/2f+1;
        panel(c,l,y+3,l+W/2f-MARGIN-4,y+40f,19f,theme.surfaceRaised,
                Palette.alpha(theme.primary,225),1.65f);
        textCentered(c,"LIVELLI",118,y+28,13,section==0?theme.text:theme.muted,true);
        textCentered(c,"∞  INFINITA",302,y+28,13,section==1?theme.text:theme.muted,true);
    }

    private void drawLevels(Canvas c){
        int count=section==0?6:3;
        for(int i=0;i<count;i++){
            int id=page*6+i;
            GameSpec spec=section==1?GameSpec.ENDLESS[i]:
                id<GameSpec.LEVELS.length?GameSpec.LEVELS[id]:null;
            if(spec==null)continue;
            float x=section==1?MARGIN:MARGIN+(i%2)*(CARD_WIDTH+10f);
            float y=cardTop()+(section==1?i:i/2)*(CARD_HEIGHT+CARD_GAP);
            float width=section==1?W-2*MARGIN:CARD_WIDTH;
            boolean locked=!spec.endless&&id>progress.highestUnlocked();
            boolean next=!locked&&!spec.endless&&!progress.cleared(id)&&id==progress.highestUnlocked();
            drawCard(c,spec,x,y,width,CARD_HEIGHT,locked,next);
        }
        float hintY=cardBottom()+19f;
        if(section==0){
            int pages=(GameSpec.LEVELS.length+5)/6;
            for(int i=0;i<pages;i++){
                paint.setColor(i==page?theme.primary:Palette.alpha(theme.muted,100));
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(W/2f+(i-(pages-1)*.5f)*17f,hintY-4f,3.8f,paint);
            }
            textCentered(c,"Scorri per altri livelli",W/2f,hintY+15f,10f,theme.muted,false);
        }else{
            textCentered(c,"Scegli un recipiente e gioca senza limite",W/2f,
                    hintY+6,11f,theme.muted,false);
        }
    }

    private void drawCard(Canvas c,GameSpec spec,float x,float y,float width,float height,
                          boolean locked,boolean next){
        int face=locked?Palette.mix(theme.surface,Palette.BACKGROUND_TOP,.35f):theme.surface;
        int border=locked?Palette.alpha(theme.border,100):
            next?theme.primary:Palette.mix(theme.surface,theme.border,.55f);
        panel(c,x,y,x+width,y+height,17f,face,border,next?1.8f:1.0f);
        drawPreview(c,spec.shape,x+8,y+10,x+69,y+height-10,locked);

        float tx=x+77f,available=width-85f;
        if(spec.endless){
            text(c,"∞  INFINITA",tx,y+25,9.8f,theme.muted,true,available);
            text(c,spec.name.replace("Infinita · ",""),tx,y+49,15f,theme.text,true,available);
            text(c,"Gioca",tx,y+80,11f,theme.secondary,false,available);
            return;
        }

        text(c,"LIVELLO "+(spec.id+1),tx,y+23,9.5f,theme.muted,true,available);
        text(c,spec.name,tx,y+47,14f,locked?theme.muted:theme.text,true,available);
        String status=locked?"Bloccato":progress.cleared(spec.id)?"Completato":"Da giocare";
        int statusColor=locked?theme.muted:progress.cleared(spec.id)?theme.positive:theme.secondary;
        paint.setColor(statusColor);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(tx+3,y+69,2.8f,paint);
        text(c,status,tx+10,y+73,10.4f,statusColor,false,available-10);
        if(progress.cleared(spec.id)&&!locked)
            text(c,"Extra "+progress.extras(spec.id)+"/"+spec.totalExtras(),
                    tx,y+89,9.2f,theme.muted,false,available);
        else if(next)
            text(c,"Tocca per iniziare",tx,y+89,9.2f,theme.muted,false,available);
    }

    private Vessel miniature(int shape) {
        Vessel v=miniatures.get(shape);
        if(v==null){v=new Vessel(shape,850f);miniatures.put(shape,v);}
        return v;
    }

    /** Real vessel geometry rendered to its own viewport; no fake beaker icons. */
    private void drawPreview(Canvas c,int shape,float l,float t,float r,float b,boolean locked){
        Vessel v=miniature(shape);
        float minX=Float.MAX_VALUE,maxX=-Float.MAX_VALUE;
        for(float xx:v.xs){minX=Math.min(minX,xx);maxX=Math.max(maxX,xx);}
        float factor=Math.min((r-l-6f)/Math.max(1f,maxX-minX),
                (b-t-5f)/Math.max(1f,v.bottom-v.top));
        float cx=(l+r)*.5f,cy=(t+b)*.5f;
        path.reset();
        for(int i=0;i<v.xs.length;i++){
            float x=cx+(v.xs[i]-(maxX+minX)*.5f)*factor;
            float y=cy+(v.ys[i]-(v.top+v.bottom)*.5f)*factor;
            if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
        }
        path.close();

        c.save();
        c.clipRect(l,t,r,b);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.vesselFill);
        c.drawPath(path,paint);
        c.save();c.clipPath(path);
        // Distinct simple flat blob discs hint at the game's soft-body movement.
        float s=Math.min(r-l,b-t);
        int opacity=locked?90:255;
        paint.setColor(Palette.alpha(theme.forTier(Math.abs(shape)%9),opacity));
        c.drawCircle(cx-s*.16f,b-s*.20f,s*.24f,paint);
        paint.setColor(Palette.alpha(theme.forTier((Math.abs(shape)+2)%9),opacity));
        c.drawCircle(cx+s*.15f,b-s*.16f,s*.24f,paint);
        paint.setColor(Palette.alpha(theme.forTier((Math.abs(shape)+4)%9),opacity));
        c.drawCircle(cx,b-s*.40f,s*.16f,paint);
        c.restore();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.7f);
        paint.setColor(Palette.alpha(Palette.VESSEL_OUTLINE,locked?135:240));
        c.drawPath(path,paint);
        paint.setStyle(Paint.Style.FILL);
        c.restore();
    }

    private void drawThemes(Canvas c){
        float y=themesTop();
        text(c,"Temi",MARGIN,y+7,17.5f,theme.text,true,110);
        textRight(c,"Tocca per cambiare",W-MARGIN,y+6,10f,theme.muted);
        float top=themesCardsTop();
        for(int i=0;i<Palette.COUNT;i++){
            Palette.Theme swatch=Palette.at(i);
            float x=MARGIN+i*122f,w=118f,bottom=top+73f;
            boolean chosen=i==selectedTheme;
            panel(c,x,top,x+w,bottom,15f,chosen?theme.surfaceRaised:theme.surface,
                    chosen?theme.primary:Palette.mix(theme.surface,theme.border,.56f),
                    chosen?2f:1f);
            text(c,swatch.name,x+11,top+18,11.5f,theme.text,chosen,65);
            if(chosen){
                paint.setColor(theme.primary);
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(x+w-15,top+15,7f,paint);
                textCentered(c,"✓",x+w-15,top+19,9.5f,theme.onPrimary,true);
            }
            c.save();c.clipRect(x+8,top+29,x+w-8,bottom-6);
            for(int j=0;j<9;j++){
                float xx=x+15+j*10.7f;
                float yy=top+58f+(float)Math.sin(j*.8f)*4f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(swatch.forTier(j));
                c.drawCircle(xx,yy,9f,paint);
                if(i==2&&j==8){
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(Palette.VESSEL_OUTLINE);
                    paint.setStrokeWidth(.75f);
                    c.drawCircle(xx,yy,9f,paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
            c.restore();
        }
    }

    private void drawCopy(Canvas c){
        float y=copyTop();
        panel(c,96f,y,324f,y+40f,20f,theme.surface,
                Palette.mix(theme.surface,theme.border,.58f),1.0f);
        textCentered(c,"COPIA DATI PARTITE",210,y+25,11f,theme.muted,true);
    }

    private void panel(Canvas c,float l,float t,float r,float b,float radius,
                       int fill,int stroke,float strokeSize){
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        c.drawRoundRect(l,t,r,b,radius,radius,paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(stroke);
        paint.setStrokeWidth(strokeSize);
        c.drawRoundRect(l,t,r,b,radius,radius,paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void text(Canvas c,String value,float x,float y,float size,
                      int color,boolean bold,float maxWidth){
        letter.setTypeface(bold?MEDIUM:NORMAL);
        letter.setTextAlign(Paint.Align.LEFT);
        letter.setTextSize(size);
        float w=letter.measureText(value);
        if(w>maxWidth)letter.setTextSize(Math.max(9f,size*maxWidth/w));
        letter.setColor(color);
        c.drawText(value,x,y,letter);
    }
    private void textCentered(Canvas c,String value,float x,float y,float size,
                              int color,boolean bold){
        letter.setTypeface(bold?MEDIUM:NORMAL);
        letter.setTextAlign(Paint.Align.CENTER);
        letter.setTextSize(size);
        letter.setColor(color);
        c.drawText(value,x,y,letter);
        letter.setTextAlign(Paint.Align.LEFT);
    }
    private void textRight(Canvas c,String value,float x,float y,float size,int color){
        letter.setTypeface(NORMAL);
        letter.setTextAlign(Paint.Align.RIGHT);
        letter.setTextSize(size);
        letter.setColor(color);
        c.drawText(value,x,y,letter);
        letter.setTextAlign(Paint.Align.LEFT);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(getWidth()==0||getHeight()==0)return false;
        float x=(e.getX()-offX())/scale();
        float y=e.getY()/scale();
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                gestureStartX=x;gestureStartY=y;scrollOnDown=scrollY;
                dragged=false;return true;
            case MotionEvent.ACTION_MOVE:
                float dy=y-gestureStartY,dx=x-gestureStartX;
                if(Math.abs(dy)>7f&&Math.abs(dy)>Math.abs(dx)*1.2f){
                    dragged=true;
                    scrollY=Blob.clamp(scrollOnDown-dy,0f,maxScroll());
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                float movedX=x-gestureStartX;
                float movedY=y-gestureStartY;
                if(dragged||Math.abs(movedY)>17f)return true;
                float adjustedY=y+scrollY;
                if(Math.abs(movedX)>46f&&section==0&&adjustedY>cardTop()-15f
                        &&adjustedY<cardBottom()+25f){
                    page=Math.max(0,Math.min((GameSpec.LEVELS.length-1)/6,
                            page+(movedX<0?1:-1)));
                    invalidate();
                    return true;
                }
                if(Math.abs(movedX)>18f)return true;
                if(saved!=null&&adjustedY>=142f&&adjustedY<=214f
                        &&x>=MARGIN&&x<=W-MARGIN){
                    resume.run();return true;
                }
                if(adjustedY>=navTop()&&adjustedY<=navTop()+43f
                        &&x>=MARGIN&&x<=W-MARGIN){
                    section=x<W/2f?0:1;
                    page=0;invalidate();return true;
                }
                if(adjustedY>=themesCardsTop()&&adjustedY<=themesCardsTop()+73f){
                    for(int i=0;i<Palette.COUNT;i++){
                        float left=MARGIN+i*122f;
                        if(x>=left&&x<=left+118f){
                            if(themeStore.select(i)){
                                selectedTheme=i;
                                theme=themeStore.current();
                                invalidate();
                            }
                            return true;
                        }
                    }
                }
                if(adjustedY>=copyTop()&&adjustedY<=copyTop()+40f
                        &&x>=96f&&x<=324f){
                    progress.copyReport();return true;
                }
                int count=section==0?6:3;
                for(int i=0;i<count;i++){
                    float l=section==1?MARGIN:MARGIN+(i%2)*(CARD_WIDTH+10f);
                    float t=cardTop()+(section==1?i:i/2)*(CARD_HEIGHT+CARD_GAP);
                    float w=section==1?W-2*MARGIN:CARD_WIDTH;
                    if(x>=l&&x<=l+w&&adjustedY>=t&&adjustedY<=t+CARD_HEIGHT){
                        int id=page*6+i;
                        if(section==1)listener.start(GameSpec.ENDLESS[i]);
                        else if(id<GameSpec.LEVELS.length&&id<=progress.highestUnlocked())
                            listener.start(GameSpec.LEVELS[id]);
                        return true;
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:dragged=false;return true;
            default:return true;
        }
    }
}
