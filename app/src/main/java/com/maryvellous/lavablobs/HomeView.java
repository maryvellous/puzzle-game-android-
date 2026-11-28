package com.maryvellous.lavablobs;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.HashMap;

/** Vertically snapping vessel carousel with a permanent neutral bottom console. */
public final class HomeView extends View {
    interface Listener {void start(GameSpec spec);}
    private static final float W=420f;
    private static final Typeface NORMAL=Typeface.create("sans-serif",Typeface.NORMAL),MEDIUM=Typeface.create("sans-serif-medium",Typeface.NORMAL),DISPLAY=Typeface.create("casual",Typeface.BOLD);
    private final Listener listener;
    private final Runnable resume;
    private final GameSave.Snapshot saved;
    private final ProgressStore progress;
    private final ThemeStore themeStore;
    private Palette.Theme theme;
    private int selectedTheme;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG),letter=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path(),glyph=new Path();
    private final HashMap<Integer,Vessel> miniatures=new HashMap<>();
    private final long start=System.nanoTime();
    private boolean endless,dragging,carouselGesture;
    private float position,storyPosition,endlessPosition,downX,downY,downPosition;
    private ValueAnimator animator;
    private AlertDialog modal;
    private int pointer=MotionEvent.INVALID_POINTER_ID;
    HomeView(Context context,ProgressStore progress,Listener listener,Runnable resume,GameSave gameSave){
        super(context);this.progress=progress;this.listener=listener;this.resume=resume;
        saved=gameSave.peek();themeStore=new ThemeStore(context);selectedTheme=themeStore.selection();theme=themeStore.current();
        storyPosition=progress.highestUnlocked();endless=saved!=null&&saved.spec.endless;
        if(endless)for(int i=0;i<GameSpec.ENDLESS.length;i++)if(GameSpec.ENDLESS[i].id==saved.spec.id)endlessPosition=i;
        position=endless?endlessPosition:storyPosition;setLayerType(View.LAYER_TYPE_HARDWARE,null);
    }
    private float scale(){return Math.min(getWidth()/W,getHeight()/620f);}
    private float offX(){return (getWidth()-W*scale())*.5f;}
    private float h(){return getHeight()/Math.max(.001f,scale());}
    private float consoleTop(){return h()-94;}
    private float top(){return saved==null?134:206;}
    private float bottom(){return consoleTop()-38;}
    private float center(){return (top()+bottom())*.5f;}
    private float cardHeight(){return Math.min(192,Math.max(88,(bottom()-top())*.53f));}
    private float step(){return cardHeight()*.93f+12;}
    private GameSpec[] specs(){return endless?GameSpec.ENDLESS:GameSpec.LEVELS;}
    private int focus(){return HomeCarousel.nearest(position,specs().length);}
    private boolean locked(GameSpec s){return !s.endless&&s.id>progress.highestUnlocked();}
    private void flat(Canvas c,float l,float t,float r,float b,float radius,int fill){panel(c,l,t,r,b,radius,fill,fill,0);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(getWidth()==0||getHeight()==0)return;
        c.drawColor(Palette.BACKGROUND_TOP);c.save();c.translate(offX(),0);c.scale(scale(),scale());
        flat(c,22,22,398,99,32,theme.forTier(BlobTitle.backing(selectedTheme)));
        float t=ValueAnimator.areAnimatorsEnabled()?(System.nanoTime()-start)/1e9f:0;
        BlobTitle.drawHome(c,letter,paint,glyph,theme,selectedTheme,t,55,79);
        textCentered(c,endless?"Scegli un recipiente":"Scegli un livello",210,122,15,theme.text,true);
        if(saved!=null){
            flat(c,22,138,398,196,23,Palette.NEUTRAL_SURFACE);
            drawPreview(c,saved.spec.shape,29,143,83,191,false);
            text(c,"Continua",93,161,20,theme.onSurface,true,240);
            text(c,(saved.spec.endless?"Infinita":"Livello "+(saved.spec.id+1))+" · "+saved.points()+" punti",93,183,13,theme.onSurfaceMuted,false,265);
            textCentered(c,"›",373,175,30,theme.onSurface,true);
        }
        c.save();c.clipRect(0,top(),W,bottom());
        int f=focus();for(int i=0;i<specs().length;i++)if(i!=f&&Math.abs(i-position)<2.5f)drawCard(c,i);
        drawCard(c,f);c.restore();
        textCentered(c,"‹",70,consoleTop()-13,24,theme.text,true);
        textCentered(c,(f+1)+" / "+specs().length,210,consoleTop()-15,13,theme.text,true);
        textCentered(c,"›",350,consoleTop()-13,24,theme.text,true);
        drawConsole(c);c.restore();
        if(ValueAnimator.areAnimatorsEnabled()&&hasWindowFocus()&&isShown())postInvalidateDelayed(33);
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)invalidate();}
    private void drawCard(Canvas c,int i){
        GameSpec s=specs()[i];boolean lock=locked(s),selected=i==focus();
        float f=HomeCarousel.scale(i-position),width=376*f,height=cardHeight()*f,cy=center()+(i-position)*step(),l=(W-width)*.5f,t=cy-height*.5f;
        flat(c,l,t,l+width,t+height,30*f,selected?(lock?0xFFD2C9D0:Palette.NEUTRAL_SURFACE):0xFF514B52);
        drawPreview(c,s.shape,l+12*f,t+9*f,l+width*.43f,t+height-9*f,lock);
        float tx=l+width*.47f,avail=width*.48f;
        int ink=selected?theme.onSurface:theme.text,muted=selected?theme.onSurfaceMuted:theme.muted;
        text(c,s.endless?"INFINITA":"LIVELLO "+(s.id+1),tx,t+height*.22f,11*f,muted,true,avail);
        text(c,s.name.replace("Infinita · ",""),tx,t+height*.43f,23*f,ink,true,avail);
        String status=lock?"Bloccato":s.endless?"Record "+progress.record(s.id):progress.cleared(s.id)?"Completato · "+progress.extras(s.id)+"/"+s.totalExtras()+" extra":"Obiettivo "+s.minScore;
        text(c,status,tx,t+height*.63f,13*f,muted,false,avail);
        if(!lock&&selected){
            float by=t+height*.71f,bh=Math.min(33,height*.23f);int fill=theme.forTier(4);
            flat(c,tx,by,tx+avail,by+bh,15*f,fill);textCentered(c,"Gioca",tx+avail*.5f,by+bh*.68f,14*f,ConsoleUi.ink(fill),true);
        }
    }
    private void drawConsole(Canvas c){
        float y=consoleTop();flat(c,0,y,W,h()+28,30,Palette.NEUTRAL_SURFACE);
        text(c,"Modalità",20,y+29,13,theme.onSurface,true,105);text(c,"infinita",20,y+48,13,theme.onSurface,true,105);
        flat(c,110,y+20,178,y+58,19,endless?theme.forTier(4):0xFFB8AFB8);
        paint.setColor(Palette.NEUTRAL_SURFACE);c.drawCircle(endless?159:129,y+39,15,paint);
        int colors=theme.forTier(5),help=theme.forTier(3);
        flat(c,196,y+16,324,y+68,23,colors);textCentered(c,"Temi",260,y+49,18,ConsoleUi.ink(colors),true);
        flat(c,339,y+16,397,y+68,24,help);textCentered(c,"?",368,y+51,24,ConsoleUi.ink(help),true);
    }
    private void stopAnimation(){if(animator!=null){animator.cancel();animator=null;}}
    private void snap(int n){
        stopAnimation();int dest=HomeCarousel.nearest(n,specs().length);
        if(!ValueAnimator.areAnimatorsEnabled()){position=dest;invalidate();return;}
        animator=ValueAnimator.ofFloat(position,dest);animator.setDuration(300);animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(a->{position=(float)a.getAnimatedValue();invalidate();});animator.start();
    }
    private void switchMode(){stopAnimation();if(endless)endlessPosition=focus();else storyPosition=focus();endless=!endless;position=endless?endlessPosition:storyPosition;invalidate();}
    private boolean hit(int i,float x,float y){float f=HomeCarousel.scale(i-position),cy=center()+(i-position)*step();return Math.abs(x-210)<=188*f&&Math.abs(y-cy)<=cardHeight()*f*.5f;}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(getWidth()==0||getHeight()==0)return false;float x=(e.getX()-offX())/scale(),y=e.getY()/scale();
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:pointer=e.getPointerId(0);downX=x;downY=y;dragging=false;carouselGesture=y>=top()&&y<=bottom();if(carouselGesture)stopAnimation();downPosition=position;return true;
            case MotionEvent.ACTION_MOVE:{int p=e.findPointerIndex(pointer);if(p<0)return true;y=e.getY(p)/scale();
                if(carouselGesture&&Math.abs(y-downY)>7)dragging=true;
                if(dragging){position=HomeCarousel.clamp(downPosition+(downY-y)/step(),specs().length);invalidate();}return true;}
            case MotionEvent.ACTION_POINTER_UP:if(e.getPointerId(e.getActionIndex())==pointer){pointer=MotionEvent.INVALID_POINTER_ID;if(carouselGesture)snap(focus());}return true;
            case MotionEvent.ACTION_CANCEL:pointer=MotionEvent.INVALID_POINTER_ID;if(carouselGesture)snap(focus());return true;
            case MotionEvent.ACTION_UP:
                if(pointer==MotionEvent.INVALID_POINTER_ID)return true;pointer=MotionEvent.INVALID_POINTER_ID;
                if(dragging){snap(HomeCarousel.target(downPosition,position,downY-y,step(),specs().length));return true;}
                if(Math.abs(x-downX)>18||Math.abs(y-downY)>18)return true;
                performClick();
                if(y>=consoleTop()&&downY>=consoleTop()){if(x<190)switchMode();else if(x<331)showThemes();else showHelp();return true;}
                if(saved!=null&&y>=138&&y<=196&&downY>=138&&downY<=196){resume.run();return true;}
                if(y>=top()&&y<=bottom()){
                    int i=focus();if(!hit(i,x,y)){for(int j=0;j<specs().length;j++)if(hit(j,x,y)){snap(j);break;}return true;}
                    if(locked(specs()[i]))Toast.makeText(getContext(),"Completa il livello precedente",Toast.LENGTH_SHORT).show();else listener.start(specs()[i]);
                }else if(y>bottom()&&y<consoleTop())snap(focus()+(x<210?-1:1));
                return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
    private void showThemes(){
        LinearLayout body=ConsoleUi.column(getContext());
        modal=ConsoleUi.sheet(getContext(),"Scegli un tema",body);
        for(int i=0;i<Palette.COUNT;i++){
            final int index=i;LinearLayout tile=ConsoleUi.column(getContext());
            tile.setBackground(ConsoleUi.round(getContext(),i==selectedTheme?0xFFD2C9D0:0xFFF7F1F3,16));
            tile.addView(ConsoleUi.text(getContext(),Palette.at(i).name+(i==selectedTheme?"  ✓":""),16));
            ConsoleUi.swatches(tile,Palette.at(i),false);body.addView(tile);
            tile.setClickable(true);tile.setFocusable(true);tile.setOnClickListener(view->{themeStore.select(index);selectedTheme=index;theme=themeStore.current();invalidate();modal.dismiss();});
        }
        modal.setOnDismissListener(d->modal=null);modal.show();
    }
    private void showHelp(){
        LinearLayout body=ConsoleUi.column(getContext());ConsoleUi.help(body,theme,endless);
        ConsoleUi.action(body,"Copia dati partite",theme.forTier(5),progress::copyReport);
        modal=ConsoleUi.sheet(getContext(),"Come si gioca",body);modal.setOnDismissListener(d->modal=null);modal.show();
    }
    @Override protected void onDetachedFromWindow(){stopAnimation();if(modal!=null)modal.dismiss();super.onDetachedFromWindow();}
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
        // A transparent miniature directly on the card, without a black box.
        // Dark neutral tint maintains the silhouette against the ivory.
        paint.setColor(Palette.alpha(theme.onSurfaceMuted,locked?10:24));
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
        paint.setColor(Palette.alpha(theme.onSurfaceMuted,locked?125:205));
        c.drawPath(path,paint);
        paint.setStyle(Paint.Style.FILL);
        c.restore();
    }

    private void panel(Canvas c,float l,float t,float r,float b,float radius,
                       int fill,int stroke,float strokeSize){
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        // The Canva panels have flat colour with no decorative outline.
        c.drawRoundRect(l,t,r,b,radius,radius,paint);
    }

    private void text(Canvas c,String value,float x,float y,float size,
                      int color,boolean bold,float maxWidth){
        letter.setTypeface(bold&&size>=14f?DISPLAY:(bold?MEDIUM:NORMAL));
        letter.setTextAlign(Paint.Align.LEFT);
        letter.setTextSize(size);
        float w=letter.measureText(value);
        if(w>maxWidth)letter.setTextSize(Math.max(9f,size*maxWidth/w));
        letter.setColor(color);
        c.drawText(value,x,y,letter);
    }
    private void textCentered(Canvas c,String value,float x,float y,float size,
                              int color,boolean bold){
        letter.setTypeface(bold&&size>=14f?DISPLAY:(bold?MEDIUM:NORMAL));
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


}
