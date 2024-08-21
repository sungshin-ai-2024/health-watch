package com.example.sensorapp;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.Log;

public class AccListener implements SensorEventListener {

    private final SensorManager sensorManager;
    private final WebSocketClient webSocketClient;
    private final Sensor sensor;
    private int stepCount = 0;
    private long lastUpdateTime = 0;

    public AccListener(Context context, WebSocketClient webSocketClient) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.webSocketClient = webSocketClient;
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public void start() {
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        Log.d("AccListener", "Accelerometer listener started");
    }

    public void stop() {
        sensorManager.unregisterListener(this, sensor);
        Log.d("AccListener", "Accelerometer listener stopped");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;
        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastUpdateTime) > 300) {
                lastUpdateTime = currentTime;

                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                double acceleration = Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;
                if (acceleration > 3.2) {  // 걸음 감지 기준 (값은 조정 가능)
                    stepCount++;
                    Log.d("step", "Step detected. Current step count: " + stepCount);
                    webSocketClient.addStepCount(stepCount);
                }

                Log.d("AccListener", "Accelerometer data received - Timestamp: " + event.timestamp
                        + ", Values: x=" + x + ", y=" + y + ", z=" + z);
            }
        }webSocketClient.addAcc(event.timestamp, event.values);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d("AccListener", "Sensor accuracy changed: " + accuracy);
    }
}
