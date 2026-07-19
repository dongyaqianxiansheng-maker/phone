package com.example.callrecorder;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSIONS = 100;
    private static final int REQ_CONSENT = 200;

    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.PROCESS_OUTGOING_CALLS
    };

    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.tv_status);
        Button startButton = findViewById(R.id.btn_start);
        Button stopButton = findViewById(R.id.btn_stop);

        startButton.setOnClickListener(v -> beginSetupFlow());
        stopButton.setOnClickListener(v -> stopRecordingService());

        updateStatus();
    }

    /** Entry point: consent first, then standard permission dialogs, then start the service. */
    private void beginSetupFlow() {
        if (!ConsentActivity.hasConsented(this)) {
            startActivityForResult(new Intent(this, ConsentActivity.class), REQ_CONSENT);
            return;
        }
        requestMissingPermissions();
    }

    private void requestMissingPermissions() {
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String perm : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                missing.add(perm);
            }
        }

        if (missing.isEmpty()) {
            startRecordingService();
        } else {
            // Standard system permission dialog. No shortcuts, no silent grants.
            ActivityCompat.requestPermissions(this,
                    missing.toArray(new String[0]), REQ_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startRecordingService();
            } else {
                Toast.makeText(this,
                        getString(R.string.toast_permissions_required),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CONSENT && resultCode == RESULT_OK) {
            requestMissingPermissions();
        } else if (requestCode == REQ_CONSENT) {
            Toast.makeText(this, getString(R.string.toast_consent_required), Toast.LENGTH_LONG).show();
        }
    }

    private void startRecordingService() {
        requestIgnoreBatteryOptimizations();

        Intent serviceIntent = new Intent(this, CallRecorderService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        updateStatus();
    }

    /**
     * Asks the user to exempt this app from battery/idle power management.
     *
     * This uses the standard, documented public Intent
     * (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) that shows the normal
     * system dialog — it is not a silent grant and the user can decline it.
     * Without this exemption, some OEMs (and stock Android's own App
     * Standby / Doze) will kill this foreground service for being idle
     * between calls, which stops call recording from working reliably.
     */
    private void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                // Some OEMs strip this system dialog from their firmware.
                // Fall back to sending the user to the general battery
                // settings screen instead of silently doing nothing.
                try {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (android.content.ActivityNotFoundException e2) {
                    Toast.makeText(this,
                            "Please manually exempt this app from battery optimization in system settings.",
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void stopRecordingService() {
        stopService(new Intent(this, CallRecorderService.class));
        updateStatus();
    }

    private void updateStatus() {
        statusText.setText(ConsentActivity.hasConsented(this)
                ? getString(R.string.status_consent_given)
                : getString(R.string.status_consent_not_given));
    }
}
