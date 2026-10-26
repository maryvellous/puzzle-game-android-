package com.maryvellous.lavablobs;

import android.app.Activity;
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
    private FrameLayout root;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(0xFF111427);
        getWindow().getDecorView().setSystemUiVisibility(0);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);

        progress=new ProgressStore(this);
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
        display(new HomeView(this,progress,this::play));
    }

    private void play(GameSpec spec) {
        if(game!=null)game.leave();
        game=new LavaView(this,spec,progress,this::showHome);
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
