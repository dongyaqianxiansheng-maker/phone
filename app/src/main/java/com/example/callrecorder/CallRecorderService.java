package com.example.callrecorder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.format.DateFormat;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.IOException;

/**
 * Foreground service that watches call state via the standard
 * TelephonyManager / PhoneStateListener callbacks and records audio only
 * while a call is actually active (CALL_STATE_OFFHOOK).
 *
 * Design constraints honored here:
 *  - Uses only public, documented Android APIs (TelephonyManager,
 *    PhoneStateListener, MediaRecorder). No reflection into hidden
 *    classes, no root, no accessibility-service tricks.
 *  - Runs as a foreground service with foregroundServiceType="microphone",
 *    which forces Android to show a non-dismissible notification the
 *    entire time recording could occur. This is what makes it
 *    "non-silent" — the notification cannot be suppressed by this app.
 *  - Refuses to do anything if the in-app consent flag isn't set, as a
 *    second gate on top of the OS permission grants.
 *  - AudioSource.VOICE_CALL is a public MediaRecorder constant. It is
 *    NOT guaranteed to work on every OEM/device — some manufacturers
 *    disable it at the HAL level for privacy reasons. That is a
 *    device-capability limitation, not something to be worked around
 *    with private APIs. If it fails, this code falls back to MIC and
 *    logs the fact so the limitation is visible, rather than silently
 *    pretending call audio was captured.
 */
public class CallRecorderService extends Service {

    private static final String TAG = "CallRecorderService";
    private static final String CHANNEL_ID = "call_recording_channel";
    private static final int NOTIFICATION_ID = 1001;

    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private String currentOutputPath;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!ConsentActivity.hasConsented(this)) {
            Log.w(TAG, "Consent not recorded; refusing to start.");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Required permissions not granted; refusing to start.");
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Monitoring for calls"));
        registerCallStateListener();
        return START_STICKY;
    }

    private void registerCallStateListener() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                switch (state) {
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        beginRecording();
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                    case TelephonyManager.CALL_STATE_RINGING:
                        stopRecording();
                        break;
                }
            }
        };

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void beginRecording() {
        if (isRecording) return;

        File outDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (outDir != null && !outDir.exists()) {
            outDir.mkdirs();
        }
        String fileName = "call_" + DateFormat.format("yyyyMMdd_HHmmss", System.currentTimeMillis()) + ".m4a";
        currentOutputPath = new File(outDir, fileName).getAbsolutePath();

        mediaRecorder = new MediaRecorder();
        boolean started = tryStart(MediaRecorder.AudioSource.VOICE_CALL);
        if (!started) {
            Log.w(TAG, "VOICE_CALL source unavailable on this device/OEM; falling back to MIC. " +
                    "Recording quality/legality of far-end audio may differ.");
            mediaRecorder = new MediaRecorder();
            started = tryStart(MediaRecorder.AudioSource.MIC);
        }

        if (started) {
            isRecording = true;
            updateNotification("Recording call…");
        } else {
            Log.e(TAG, "Failed to start recording with any available audio source.");
            mediaRecorder = null;
        }
    }

    private boolean tryStart(int audioSource) {
        try {
            mediaRecorder.setAudioSource(audioSource);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setOutputFile(currentOutputPath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            return true;
        } catch (IOException | IllegalStateException e) {
            Log.e(TAG, "MediaRecorder failed to start with source " + audioSource, e);
            return false;
        }
    }

    private void stopRecording() {
        if (!isRecording || mediaRecorder == null) return;
        try {
            mediaRecorder.stop();
        } catch (RuntimeException e) {
            Log.w(TAG, "Recorder stop() called with nothing recorded", e);
        } finally {
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            updateNotification("Monitoring for calls");
            Log.i(TAG, "Saved recording to " + currentOutputPath);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Call Recording",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows when call recording is active");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private android.app.Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Call Recorder")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        stopRecording();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
