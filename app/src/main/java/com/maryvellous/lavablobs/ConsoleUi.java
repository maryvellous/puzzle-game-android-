package com.maryvellous.lavablobs;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.Button;
import android.widget.ScrollView;

/** Neutral sheets and exact palette colors shared by Home and gameplay. */
final class ConsoleUi {
    static int ink(int color){
        double r=channel((color>>16)&255),g=channel((color>>8)&255),b=channel(color&255);
        return .2126*r+.7152*g+.0722*b>.179?0xFF211A26:0xFFFFFFFF;
    }
    private static double channel(int n){double s=n/255.;return s<=.04045?s/12.92:Math.pow((s+.055)/1.055,2.4);}
    static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable round(Context c,int fill,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(c,radius));return d;}
    static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(c,18),dp(c,12),dp(c,18),dp(c,16));return v;}
    static TextView text(Context c,String s,int size){TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(0xFF211A26);t.setPadding(0,dp(c,5),0,dp(c,5));return t;}
    static void action(LinearLayout parent,String s,int color,Runnable action){
        Context c=parent.getContext();Button b=new Button(c);b.setText(s);b.setAllCaps(false);b.setTextColor(ink(color));b.setBackground(round(c,color,16));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(c,48));lp.topMargin=dp(c,8);
        parent.addView(b,lp);b.setOnClickListener(v->action.run());
    }
    static void swatches(LinearLayout parent,Palette.Theme theme,boolean sizes){
        Context c=parent.getContext();LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
        for(int i=0;i<9;i++){
            android.widget.FrameLayout slot=new android.widget.FrameLayout(c);
            row.addView(slot,new LinearLayout.LayoutParams(0,dp(c,38),1));
            android.view.View dot=new android.view.View(c);
            dot.setBackground(round(c,theme.forTier(i),100));
            int d=dp(c,sizes?10+2*i:22);
            android.widget.FrameLayout.LayoutParams p=new android.widget.FrameLayout.LayoutParams(d,d,Gravity.CENTER);slot.addView(dot,p);
        }
        parent.addView(row);
    }
    static AlertDialog sheet(Context c,String title,LinearLayout body){
        ScrollView sc=new ScrollView(c);sc.addView(body);
        AlertDialog d=new AlertDialog.Builder(c,android.R.style.Theme_Material_Light_Dialog_Alert).setTitle(title).setView(sc).setNegativeButton("Chiudi",null).create();
        d.setCanceledOnTouchOutside(true);return d;
    }
    static void help(LinearLayout body,Palette.Theme theme,boolean endless){
        Context c=body.getContext();
        body.addView(text(c,endless?"Unisci blob della stessa taglia e fai più punti possibile prima che finisca lo spazio.":"Unisci abbastanza blob per raggiungere l’obiettivo prima che finisca lo spazio.",17));
        body.addView(text(c,"Le nove taglie base",14));swatches(body,theme,true);
        body.addView(text(c,"Due blob della stessa taglia si fondono nella successiva. Oltre la nona, i colori si ripetono.",14));
        body.addView(text(c,"Tocca il recipiente o trascina il prossimo blob dal basso. Scuoti il telefono o premi Scuoti per liberare spazio.",14));
        if(endless)body.addView(text(c,"POP: seleziona un blob da rimuovere quando hai una carica.",14));
    }
}
