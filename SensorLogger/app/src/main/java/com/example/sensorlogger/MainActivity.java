package com.example.sensorlogger;

import static android.content.pm.PackageManager.PERMISSION_DENIED;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.samsung.android.service.health.tracking.ConnectionListener;
import com.samsung.android.service.health.tracking.HealthTracker;
import com.samsung.android.service.health.tracking.HealthTrackerException;
import com.samsung.android.service.health.tracking.HealthTrackingService;
import com.samsung.android.service.health.tracking.data.DataPoint;
import com.samsung.android.service.health.tracking.data.HealthTrackerType;
import com.samsung.android.service.health.tracking.data.ValueKey;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MainActivity extends Activity {

    private final String APP_TAG = "SensorLogger";

    private Button btnStart;
    private TextView txtUuid;

    private WebSocketClient mWebSocketClient;

    private boolean permissionGranted = false;

    private boolean isMeasure = false;
    private final Handler accHandler = new Handler(Objects.requireNonNull(Looper.myLooper()));
    private final Handler ppgHandler = new Handler(Objects.requireNonNull(Looper.myLooper()));
    private final HealthTracker.TrackerEventListener accListener = new HealthTracker.TrackerEventListener() {
        @Override
        public void onDataReceived(@NonNull List<DataPoint> list) {
            if (!isMeasure) return;
            if (list.isEmpty()) return;
            long timestamp = list.get(0).getTimestamp();
            int[][] values = new int[300][3];
            for (int i = 0; i < list.size(); i++) {
                DataPoint dataPoint = list.get(i);
                values[i][0] = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_X);
                values[i][1] = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Y);
                values[i][2] = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Z);
            }
            try {
                mWebSocketClient.setAcc(timestamp, Arrays.deepToString(values));
                if (mWebSocketClient.isReady()) mWebSocketClient.send();
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
            if (trackerError == HealthTracker.TrackerError.PERMISSION_ERROR) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(),
                        getString(R.string.NoPermission), Toast.LENGTH_SHORT).show());
            }
            if (trackerError == HealthTracker.TrackerError.SDK_POLICY_ERROR) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(),
                        getString(R.string.SDKPolicyError), Toast.LENGTH_SHORT).show());
            }
        }
    };
    private final HealthTracker.TrackerEventListener ppgListener = new HealthTracker.TrackerEventListener() {
        @Override
        public void onDataReceived(@NonNull List<DataPoint> list) {
            if (!isMeasure) return;
            if (list.isEmpty()) return;
            long timestamp = list.get(0).getTimestamp();
            int[] values = new int[300];
            for (int i = 0; i < list.size(); i++) {
                DataPoint dataPoint = list.get(i);
                values[i] = dataPoint.getValue(ValueKey.PpgGreenSet.PPG_GREEN);
            }
            try {
                mWebSocketClient.setPpg(timestamp, Arrays.toString(values));
                if (mWebSocketClient.isReady()) mWebSocketClient.send();
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
            if (trackerError == HealthTracker.TrackerError.PERMISSION_ERROR) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(),
                        getString(R.string.NoPermission), Toast.LENGTH_SHORT).show());
            }
            if (trackerError == HealthTracker.TrackerError.SDK_POLICY_ERROR) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(),
                        getString(R.string.SDKPolicyError), Toast.LENGTH_SHORT).show());
            }
        }
    };

    private HealthTrackingService healthTrackingService = null;
    private HealthTracker accTracker = null;
    private HealthTracker ppgTracker = null;
    private boolean connected = false;
    private final ConnectionListener connectionListener = new ConnectionListener() {
        @Override
        public void onConnectionSuccess() {
            Log.i(APP_TAG, "Connected");
            Toast.makeText(getApplicationContext(), getString(R.string.ConnectedToHS), Toast.LENGTH_SHORT).show();
            connected = true;
            accTracker = healthTrackingService.getHealthTracker(HealthTrackerType.ACCELEROMETER);
            ppgTracker = healthTrackingService.getHealthTracker(HealthTrackerType.PPG_GREEN);
        }

        @Override
        public void onConnectionEnded() {
            Log.i(APP_TAG, "Disconnected");
        }

        @Override
        public void onConnectionFailed(HealthTrackerException e) {
            if (e.getErrorCode() == HealthTrackerException.OLD_PLATFORM_VERSION || e.getErrorCode() == HealthTrackerException.PACKAGE_NOT_INSTALLED)
                Toast.makeText(getApplicationContext(), getString(R.string.NoHealthPlatformError), Toast.LENGTH_LONG).show();
            if (e.hasResolution()) {
                e.resolve(MainActivity.this);
            } else {
                Log.e(APP_TAG, "Could not connect to Health Services: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), getString(R.string.ConnectionError), Toast.LENGTH_LONG).show());
            }
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(v -> start());
        txtUuid = findViewById(R.id.txtUuid);

        mWebSocketClient = new WebSocketClient();

        List<String> permissions = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), "android.permission.ACTIVITY_RECOGNITION") == PackageManager.PERMISSION_DENIED)
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), "android.permission.BODY_SENSORS") == PackageManager.PERMISSION_DENIED)
            permissions.add(Manifest.permission.BODY_SENSORS);
        if (!permissions.isEmpty())
            requestPermissions(permissions.toArray(new String[0]), 0);
        else
            permissionGranted = true;

        try {
            healthTrackingService = new HealthTrackingService(connectionListener, getApplicationContext());
            healthTrackingService.connectService();
        } catch (Throwable t) {
            Log.e(APP_TAG, Objects.requireNonNull(t.getMessage()));
        }
    }

    private void start() {
        List<String> permissions = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), "android.permission.ACTIVITY_RECOGNITION") == PackageManager.PERMISSION_DENIED)
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), "android.permission.BODY_SENSORS") == PackageManager.PERMISSION_DENIED)
            permissions.add(Manifest.permission.BODY_SENSORS);
        if (!permissions.isEmpty())
            requestPermissions(permissions.toArray(new String[0]), 0);
        if (!permissionGranted) {
            Log.i(APP_TAG, "Could not get permissions. Terminating measurement");
            return;
        }
        if (!connected) {
            Toast.makeText(getApplicationContext(), getString(R.string.ConnectionError), Toast.LENGTH_SHORT).show();
            return;
        }
        if (btnStart.getText().equals(getString(R.string.start))) {
            btnStart.setText(getString(R.string.stop));
            isMeasure = true;
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            String uuid = UUID.randomUUID().toString();
            txtUuid.setText(uuid);
            try {
                mWebSocketClient.connectWebSocket(uuid);
            } catch (JSONException e) {
                Log.e(APP_TAG, Objects.requireNonNull(e.getMessage()));
            }

            accHandler.post(() -> accTracker.setEventListener(accListener));
            ppgHandler.post(() -> ppgTracker.setEventListener(ppgListener));
        } else {
            btnStart.setText(getString(R.string.start));
            isMeasure = false;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            if (accTracker != null)
                accTracker.unsetEventListener();
            accHandler.removeCallbacksAndMessages(null);

            if (ppgTracker != null)
                ppgTracker.unsetEventListener();
            ppgHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isMeasure = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (accTracker != null)
            accTracker.unsetEventListener();
        accHandler.removeCallbacksAndMessages(null);

        if (ppgTracker != null)
            ppgTracker.unsetEventListener();
        ppgHandler.removeCallbacksAndMessages(null);

        if (healthTrackingService != null)
            healthTrackingService.disconnectService();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 0) {
            permissionGranted = true;
            for (int i = 0; i < permissions.length; ++i) {
                if (grantResults[i] == PERMISSION_DENIED) {
                    if (!shouldShowRequestPermissionRationale(permissions[i]))
                        Toast.makeText(getApplicationContext(), getString(R.string.PermissionDeniedPermanently), Toast.LENGTH_LONG).show();
                    else
                        Toast.makeText(getApplicationContext(), getString(R.string.PermissionDeniedRationale), Toast.LENGTH_LONG).show();
                    permissionGranted = false;
                    break;
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}