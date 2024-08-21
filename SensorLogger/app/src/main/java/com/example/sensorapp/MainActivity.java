package com.example.sensorapp;

import static android.content.pm.PackageManager.PERMISSION_DENIED;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final String APP_TAG = "SensorApp";
    private Button btnStart;
    private TextView txtUuid;
    private WebSocketClient webSocketClient;
    private boolean permissionGranted = false;
    private AccListener accListener;
    private PpgListener ppgListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(v -> start());
        txtUuid = findViewById(R.id.txtUuid);

        Context context = this;
        TextView txtState = findViewById(R.id.txtState);
        TextView txtCount = findViewById(R.id.txtCount);

        List<String> permissions = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), "android.permission.ACTIVITY_RECOGNITION") == PackageManager.PERMISSION_DENIED)
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), "android.permission.BODY_SENSORS") == PackageManager.PERMISSION_DENIED)
            permissions.add(Manifest.permission.BODY_SENSORS);
        if (!permissions.isEmpty())
            requestPermissions(permissions.toArray(new String[0]), 0);
        else
            permissionGranted = true;

        webSocketClient = new WebSocketClient(context, txtState, txtCount);
        accListener = new AccListener(context, webSocketClient);
        ppgListener = new PpgListener(context, webSocketClient);
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
        if (!ppgListener.isConnected()) {
            Toast.makeText(getApplicationContext(), getString(R.string.ConnectionError), Toast.LENGTH_SHORT).show();
            return;
        }
        if (btnStart.getText().equals(getString(R.string.start))) {
            btnStart.setText(getString(R.string.stop));

            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            String uuid = UUID.randomUUID().toString();
            txtUuid.setText(uuid);
            webSocketClient.connectWebSocket(uuid);
            accListener.start();
            ppgListener.start();
        } else {
            btnStart.setText(getString(R.string.start));
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            webSocketClient.close();
            accListener.stop();
            ppgListener.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        webSocketClient.close();
        accListener.stop();
        ppgListener.stop();
        ppgListener.disconnectService();
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
