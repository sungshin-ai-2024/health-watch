package com.example.sensorapp;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.NonNull;

import com.samsung.android.service.health.tracking.ConnectionListener;
import com.samsung.android.service.health.tracking.HealthTracker;
import com.samsung.android.service.health.tracking.HealthTrackerException;
import com.samsung.android.service.health.tracking.HealthTrackingService;
import com.samsung.android.service.health.tracking.data.DataPoint;
import com.samsung.android.service.health.tracking.data.HealthTrackerType;
import com.samsung.android.service.health.tracking.data.ValueKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class PpgListener implements HealthTracker.TrackerEventListener {

    private static final String APP_TAG = "PpgListener";
    private final Context context;
    private final Handler handler = new Handler(Objects.requireNonNull(Looper.myLooper()));
    private final WebSocketClient webSocketClient;
    private boolean isMeasure = false;

    private HealthTrackingService healthTrackingService = null;
    private HealthTracker ppgTracker = null;
    private boolean connected = false;

    final ConnectionListener connectionListener = new ConnectionListener() {
        @Override
        public void onConnectionSuccess() {
            Log.i(APP_TAG, "Connected");
            handler.post(() -> Toast.makeText(context, context.getString(R.string.ConnectedToHS), Toast.LENGTH_SHORT).show());
            connected = true;
            ppgTracker = healthTrackingService.getHealthTracker(HealthTrackerType.PPG_GREEN);
        }

        @Override
        public void onConnectionEnded() {
            Log.i(APP_TAG, "Disconnected");
        }

        @Override
        public void onConnectionFailed(HealthTrackerException e) {
            if (e.getErrorCode() == HealthTrackerException.OLD_PLATFORM_VERSION || e.getErrorCode() == HealthTrackerException.PACKAGE_NOT_INSTALLED)
                handler.post(() -> Toast.makeText(context, context.getString(R.string.NoHealthPlatformError), Toast.LENGTH_LONG).show());
            if (e.hasResolution()) {
                e.resolve((Activity) context);
            } else {
                Log.e(APP_TAG, "Could not connect to Health Services: " + e.getMessage());
                handler.post(() -> Toast.makeText(context, context.getString(R.string.ConnectionError), Toast.LENGTH_LONG).show());
            }
        }
    };

    public PpgListener(Context context, WebSocketClient webSocketClient) {
        this.context = context;
        this.webSocketClient = webSocketClient;

        try {
            healthTrackingService = new HealthTrackingService(connectionListener, context);
            healthTrackingService.connectService();
        } catch (Throwable t) {
            Log.e(APP_TAG, Objects.requireNonNull(t.getMessage()));
        }
    }

    public boolean isConnected() {
        return connected;
    }

    @Override
    public void onDataReceived(@NonNull List<DataPoint> list) {
        if (!isMeasure || list.isEmpty()) return;

        List<Long> timestamp = new ArrayList<>();
        List<Integer> values = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            DataPoint dataPoint = list.get(i);
            timestamp.add(dataPoint.getTimestamp());
            values.add(dataPoint.getValue(ValueKey.PpgGreenSet.PPG_GREEN));
        }

        // 로그를 사용해 데이터가 들어오는지 확인
        Log.d("asdf", "PPG 데이터 수신됨: " + values.toString());
        Log.d("asdf", "타임스탬프: " + timestamp.toString());

        webSocketClient.setPpg(timestamp, Arrays.toString(values.toArray()));

        // 피크 탐지 및 BPM 계산
        List<Long> peakTimestamps = detectPeaks(values, 500); // 임계값 500은 데이터에 따라 조정 가능
        int bpm = calculateBPM(peakTimestamps);

        Log.d("asdf", "계산된 BPM: " + bpm);

        // BPM 데이터를 전송
        try {
            webSocketClient.sendBpm(bpm);
        } catch (JSONException e) {
            Log.e(APP_TAG, Objects.requireNonNull(e.getMessage()));
        }
    }

    @Override
    public void onFlushCompleted() {
        Log.i(APP_TAG, "onFlushCompleted called");
    }

    @Override
    public void onError(HealthTracker.TrackerError trackerError) {
        Log.i(APP_TAG, " onError called");
        if (trackerError == HealthTracker.TrackerError.PERMISSION_ERROR)
            handler.post(() -> Toast.makeText(context, context.getString(R.string.NoPermission), Toast.LENGTH_SHORT).show());
        if (trackerError == HealthTracker.TrackerError.SDK_POLICY_ERROR)
            handler.post(() -> Toast.makeText(context, context.getString(R.string.SDKPolicyError), Toast.LENGTH_SHORT).show());
    }

    public void start() {
        isMeasure = true;
        handler.post(() -> ppgTracker.setEventListener(this));
    }

    public void stop() {
        isMeasure = false;
        if (ppgTracker != null)
            ppgTracker.unsetEventListener();
        handler.removeCallbacksAndMessages(null);
    }

    public void disconnectService() {
        if (healthTrackingService != null)
            healthTrackingService.disconnectService();
    }

    private List<Long> detectPeaks(List<Integer> ppgValues, int threshold) {
        List<Long> peakTimestamps = new ArrayList<>();
        List<Long> ppgTimestamp = webSocketClient.getPpgTimestamp();  // ppgTimestamp 가져오기

        for (int i = 1; i < ppgValues.size() - 1; i++) {
            if (ppgValues.get(i) > threshold && ppgValues.get(i) > ppgValues.get(i - 1) && ppgValues.get(i) > ppgValues.get(i + 1)) {
                peakTimestamps.add(ppgTimestamp.get(i));  // 가져온 ppgTimestamp 사용
            }
        }
        return peakTimestamps;
    }


    private int calculateBPM(List<Long> peakTimestamps) {
        if (peakTimestamps.size() < 2) return 0;
        long totalTime = peakTimestamps.get(peakTimestamps.size() - 1) - peakTimestamps.get(0); // 첫 피크와 마지막 피크 간의 시간 차이
        long numBeats = peakTimestamps.size() - 1; // 피크 간의 비트 수
        return (int) ((numBeats * 60 * 1000) / totalTime); // BPM 계산
    }
}
