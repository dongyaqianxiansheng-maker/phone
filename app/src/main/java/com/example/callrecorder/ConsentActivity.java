package com.example.callrecorder;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * One-time in-app consent/disclosure screen.
 *
 * This is a *supplement* to the signed paper disclaimer your client
 * provided — it is not a substitute for it, and it does not by itself
 * make recording the other party on the call lawful in every
 * jurisdiction. It records the device owner's explicit acknowledgment
 * that call recording will occur, timestamps it, and refuses to let the
 * recording service start until this has happened at least once.
 */
public class ConsentActivity extends AppCompatActivity {

    public static final String PREFS = "call_recorder_prefs";
    public static final String KEY_CONSENT_GIVEN = "consent_given";
    public static final String KEY_CONSENT_TIMESTAMP = "consent_timestamp";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consent);

        TextView disclosure = findViewById(R.id.tv_disclosure);
        disclosure.setText(R.string.consent_body);

        Button agree = findViewById(R.id.btn_agree);
        Button decline = findViewById(R.id.btn_decline);

        agree.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            prefs.edit()
                    .putBoolean(KEY_CONSENT_GIVEN, true)
                    .putLong(KEY_CONSENT_TIMESTAMP, System.currentTimeMillis())
                    .apply();
            setResult(RESULT_OK);
            finish();
        });

        decline.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    public static boolean hasConsented(android.content.Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
        return prefs.getBoolean(KEY_CONSENT_GIVEN, false);
    }
}
