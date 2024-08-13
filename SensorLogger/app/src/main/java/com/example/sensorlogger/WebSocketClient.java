package com.example.sensorlogger;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketClient {
    private WebSocket webSocket;
    private static final String url = "ws://192.168.0.26:8000/ws/logger/send/";

    private final JSONObject data = new JSONObject();

    private final boolean[] ready = new boolean[]{false, false};

    public void connectWebSocket(String uuid) throws JSONException {
        data.put("uuid", uuid);

        Request request = new Request.Builder()
                .url(url)
                .build();

        WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                super.onOpen(webSocket, response);
            }
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        webSocket = client.newWebSocket(request, listener);
    }

    public boolean isReady() {
        return ready[0] && ready[1];
    }

    public void setAcc(long timestamp, String values) throws JSONException {
        data.put("time", timestamp);
        data.put("acc", values);
        ready[0] = true;
    }

    public void setPpg(long timestamp, String values) throws JSONException {
        data.put("time", timestamp);
        data.put("ppg", values);
        ready[1] = true;
    }

    public void send() {
        if (webSocket != null) {
            webSocket.send(data.toString());
            ready[0] = false;
            ready[1] = false;
        }
    }
}
