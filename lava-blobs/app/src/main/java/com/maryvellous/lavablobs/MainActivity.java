package com.maryvellous.lavablobs;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.WindowManager;

/**
 * Sensors are registered only while the game is visible.
 * Accelerometer readings are filtered before reaching the physics world.
 */
public final class MainActivity extends Activity implements SensorEventListener {
    private final ShakeDetector shakeDetector = new ShakeDetector();
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private LavaView game;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        game = new LavaView(this);
        setContentView(game);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        shakeDetector.reset();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override protected void onPause() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        ShakeDetector.Impulse kick = shakeDetector.sample(
                event.values[0], event.values[1], event.values[2], event.timestamp);
        if (kick != null && game != null) game.shake(kick.x, kick.y, kick.z);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // The shake detector tolerates normal sensor noise.
    }
}
