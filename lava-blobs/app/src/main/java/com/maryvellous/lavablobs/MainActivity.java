package com.maryvellous.lavablobs;

import android.app.Activity;
import android.app.AlertDialog;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

/** Single activity; every game and menu view lives inside the safe content area. */
public final class MainActivity extends Activity implements SensorEventListener {
    private final ShakeDetector shakeDetector=new ShakeDetector();
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private LavaView game;
    private ProgressStore progress;
    private GameSave session;
    private FrameLayout root;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(0xFF111427);
        getWindow().getDecorView().setSystemUiVisibility(0);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);

        progress=new ProgressStore(this);
        session=new GameSave(this);
        sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);
        if(sensorManager!=null)accelerometer=sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        root=new FrameLayout(this);
        root.setClipToPadding(true);
        root.setBackgroundColor(0xFF111427);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int navigationBottom=Build.VERSION.SDK_INT>=30?
                    insets.getInsets(WindowInsets.Type.navigationBars()).bottom:
                    insets.getSystemWindowInsetBottom();
            v.setPadding(0,0,0,navigationBottom);
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        showHome();
    }

    private void display(View view) {
        root.removeAllViews();
        root.addView(view,new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
    }

    void showHome() {
        if(game!=null){game.leave();game=null;}
        stopSensor();
        display(new HomeView(this,progress,this::play,this::resume,session));
    }

    private void play(GameSpec spec) {
        // Exactly one active game: never overwrite without an explicit choice.
        if(session.exists()) {
            new AlertDialog.Builder(this)
                    .setTitle("Sostituire la partita?")
                    .setMessage("La partita in pausa verrà sostituita da quella nuova.")
                    .setNegativeButton("Annulla",null)
                    .setPositiveButton("Nuova partita",(dialog,which)->startNew(spec))
                    .show();
        } else startNew(spec);
    }

    private void startNew(GameSpec spec) {
        if(game!=null){game.leave();game=null;}
        session.clear();
        game=new LavaView(this,spec,progress,this::showHome,session,null);
        display(game);
        game.saveCheckpoint();
        shakeDetector.reset();
        startSensor();
    }

    private void resume() {
        GameSave.Snapshot active=session.peek();
        if(active==null){showHome();return;}
        if(game!=null){game.leave();game=null;}
        game=new LavaView(this,active.spec,progress,this::showHome,session,active);
        display(game);
        shakeDetector.reset();
        startSensor();
    }

    private void startSensor(){
        if(sensorManager!=null&&accelerometer!=null&&game!=null)
            sensorManager.registerListener(this,accelerometer,SensorManager.SENSOR_DELAY_GAME);
    }
    private void stopSensor(){
        if(sensorManager!=null)sensorManager.unregisterListener(this);
    }
    @Override protected void onPause(){
        if(game!=null)game.saveCheckpoint();
        stopSensor();
        super.onPause();
    }
    @Override protected void onResume(){
        super.onResume();
        shakeDetector.reset();
        startSensor();
    }
    @Override public void onSensorChanged(SensorEvent event){
        if(event.sensor.getType()!=Sensor.TYPE_ACCELEROMETER||game==null)return;
        ShakeDetector.Impulse i=shakeDetector.sample(
            event.values[0],event.values[1],event.values[2],event.timestamp);
        if(i!=null)game.shake(i.x,i.y,i.z);
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public void onBackPressed(){
        if(game!=null)showHome();
        else super.onBackPressed();
    }
}
