package com.study.pytorch;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class ConfigActivity extends AppCompatActivity {
    public static final String APP_PREFERENCES = "appSettings";
    public static final String APP_PREFERENCES_NET = "netType";
    public static final String APP_PREFERENCES_THRESHOLD = "maskThreshold";
    private SharedPreferences mSettings;
    private int netType;
    private int maskThreshold;

    private EditText maskThresholdInput;
    private SwitchMaterial netSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);
        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        maskThresholdInput = findViewById(R.id.maskThresholdInput);
        netSwitch = findViewById(R.id.netSwitch);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {
        super.onResume();

        if (mSettings.contains(APP_PREFERENCES_NET)) {
            netType = mSettings.getInt(APP_PREFERENCES_NET, 0);
            netSwitch.setChecked(netType != 0);
        }

        if (mSettings.contains(APP_PREFERENCES_THRESHOLD)) {
            maskThreshold = mSettings.getInt(APP_PREFERENCES_THRESHOLD, 128);
            maskThresholdInput.setText(Integer.toString(maskThreshold));
        }
    }

    public void save(View view) {
        try {
            maskThreshold = Integer.parseInt(maskThresholdInput.getText().toString());
            netType = (netSwitch.isChecked() ? 1 : 0);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Неверные значения", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = mSettings.edit();
        editor.putInt(APP_PREFERENCES_NET, netType);
        editor.putInt(APP_PREFERENCES_THRESHOLD, maskThreshold);
        editor.apply();
        finish();

    }
}