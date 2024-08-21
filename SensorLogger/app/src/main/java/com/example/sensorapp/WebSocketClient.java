package com.example.sensorapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.sensorapp.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketClient {

    private static final long NANOS_TO_MILLIS = (long) 1e6;
    private static final String url = "ws://210.125.96.132:5000/ws/logger/send/";
    private static final String APP_TAG = "WebSocketClient";
    private static final int accInterval = 20;
    private static final long accBatchSize = 600;
    private final JSONObject data = new JSONObject();
    private final Handler handler = new Handler(Objects.requireNonNull(Looper.myLooper()));
    private final OkHttpClient client;
    private final Request request;
    private final Context context;
    private final TextView txtState;
    private final TextView txtCount;
    private final List<Long> accTimestamp = new ArrayList<>();
    private final List<float[]> accValues = new ArrayList<>();
    private List<Long> ppgTimestamp;
    private WebSocket webSocket;
    private int count;
    // 새로운 멤버 변수: 누적된 걸음수를 저장할 변수 추가
    private int cumulativeStepCount = 0;

    public List<Long> getPpgTimestamp() {
        return ppgTimestamp;
    }

    public void addStepCount(int stepCount) {
        // stepCount가 0이더라도 cumulativeStepCount에 업데이트
        if (stepCount == 0 && cumulativeStepCount == 0) {
            cumulativeStepCount = 0; // 이미 0이라면, 0으로 유지
        } else {
            cumulativeStepCount = stepCount; // 새롭게 전달된 stepCount로 업데이트
        }
    }
    public void sendStepCount() {
        try {
            // JSON 데이터에 누적된 걸음수 추가
            data.put("step_count", cumulativeStepCount);
            Log.d("asdf", "Data being sent: " + data.toString());

            // 웹소켓을 통해 데이터를 전송
            if (webSocket != null) {
                webSocket.send(data.toString());
                Log.d("asdf", "Sent cumulative step count: " + cumulativeStepCount);
            } else {
                Log.d("step", "WebSocket is null, cannot send data");
            }
        } catch (JSONException e) {
            Log.e("step", "Failed to send step count.", e);
        }
    }

    private final WebSocketListener listener = new WebSocketListener() {
        @Override
        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            super.onOpen(webSocket, response);
            txtState.setText(context.getString(R.string.WebSocketStateOpen));
        }

        @Override
        public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            super.onClosed(webSocket, code, reason);
            txtState.setText(context.getString(R.string.WebSocketStateClosed));
        }

        @Override
        public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
            super.onFailure(webSocket, t, response);
            Log.i(APP_TAG, " onFailure called");
            txtState.setText(context.getString(R.string.WebSocketStateFailure));
            handler.post(() -> Toast.makeText(context.getApplicationContext(), t.toString(), Toast.LENGTH_LONG).show());
        }

        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            super.onMessage(webSocket, text);
            count++;
            txtCount.setText(String.valueOf(count));
        }
    };
    private boolean isPpgCollected = false;
    private long elapsedRealtimeNanos;
    private long currentTimeMillis;

    public WebSocketClient(Context context, TextView txtState, TextView txtCount) {
        this.context = context;
        this.txtState = txtState;
        this.txtCount = txtCount;

        request = new Request.Builder()
                .url(url)
                .build();

        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    private long sensorTimestampToUnixTimestamp(long timestamp) {
        return (timestamp - elapsedRealtimeNanos) / NANOS_TO_MILLIS + currentTimeMillis;
    }

    public void connectWebSocket(String uuid) {
        try {
            data.put("uuid", uuid);
        } catch (JSONException e) {
            Log.e(APP_TAG, Objects.requireNonNull(e.getMessage()));
        }

        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
        currentTimeMillis = System.currentTimeMillis();

        count = 0;

        // 웹소켓 연결 시 누적된 걸음수를 초기화
        cumulativeStepCount = 0;

        txtCount.setText(String.valueOf(count));

        webSocket = client.newWebSocket(request, listener);
    }

    private List<Long> computeOutputTimestamp(long start) {
        List<Long> outputTimestamp = new ArrayList<>();
        for (long i = 0; i < accBatchSize; i++)
            outputTimestamp.add(start + accInterval * i);
        return outputTimestamp;
    }

    private float[][] interp(List<Long> in, List<float[]> values, List<Long> out) {
        float[][] newValues = new float[out.size()][values.get(0).length];
        int left = 0;
        for (int i = 0; i < out.size(); i++) {
            while (in.get(left + 1) < out.get(i)) left++;
            float[] vLeft = values.get(left);
            float[] vRight = values.get(left + 1);
            long tLeft = in.get(left);
            long tRight = in.get(left + 1);
            float d = (float) (out.get(i) - tLeft) / (tRight - tLeft);
            newValues[i][0] = vLeft[0] + (vLeft[0] - vRight[0]) * d;
            newValues[i][1] = vLeft[1] + (vLeft[1] - vRight[1]) * d;
            newValues[i][2] = vLeft[2] + (vLeft[2] - vRight[2]) * d;
        }
        return newValues;
    }

    private void removeAcc(long timestamp) {
        while (accTimestamp.get(1) < timestamp) {
            accTimestamp.remove(0);
            accValues.remove(0);
        }
    }

    public void addAcc(long timestamp, float[] values) {
        long unixTimestamp = sensorTimestampToUnixTimestamp(timestamp);
        accTimestamp.add(unixTimestamp);
        accValues.add(values.clone());

        if (!isPpgCollected) return;
        if (unixTimestamp < ppgTimestamp.get(0) + accInterval * accBatchSize) return;

        isPpgCollected = false;
        List<Long> outputTimestamp = computeOutputTimestamp(ppgTimestamp.get(0));
        float[][] newValues = interp(accTimestamp, accValues, outputTimestamp);
        removeAcc(ppgTimestamp.get(0));
        try {
            data.put("acc", Arrays.deepToString(newValues));
            send();
            sendStepCount();
        } catch (JSONException e) {
            Log.e(APP_TAG, Objects.requireNonNull(e.getMessage()));
        }
    }

    public void setPpg(List<Long> timestamp, String values) {
        ppgTimestamp = timestamp;
        try {
            data.put("time", timestamp.get(0));
            data.put("ppg", values);
            if (ppgTimestamp.get(0) > accTimestamp.get(0)) isPpgCollected = true;
        } catch (JSONException e) {
            Log.e(APP_TAG, Objects.requireNonNull(e.getMessage()));
        }
    }

    public void send() {
        if (webSocket != null)
            webSocket.send(data.toString());
    }

    public void sendBpm(int bpm) throws JSONException {
        data.put("bpm", bpm);
        send();
    }

    public void close() {
        if (webSocket != null)
            webSocket.close(1000, null);
    }
}
