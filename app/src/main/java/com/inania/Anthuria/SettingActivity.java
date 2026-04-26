package com.inania.Anthuria;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        ImageButton btnBack = findViewById(R.id.btn_back_settings);
        btnBack.setOnClickListener(v -> finish());

        SwitchMaterial switchNotifications = findViewById(R.id.switch_notifications);
        SwitchMaterial switchDarkMode = findViewById(R.id.switch_dark_mode);

        SharedPreferences prefs = getSharedPreferences(AnthuriaApp.PREFS_NAME, MODE_PRIVATE);
        boolean isDark = prefs.getBoolean(AnthuriaApp.KEY_DARK_MODE, false);
        switchDarkMode.setChecked(isDark);

        switchNotifications.setOnCheckedChangeListener((btn, isChecked) ->
                Toast.makeText(this,
                        isChecked ? "Уведомления включены" : "Уведомления отключены",
                        Toast.LENGTH_SHORT).show());

        switchDarkMode.setOnCheckedChangeListener((btn, isChecked) -> {
            // 1. Persist the preference.
            prefs.edit().putBoolean(AnthuriaApp.KEY_DARK_MODE, isChecked).apply();

            // 2. Apply globally — AppCompat recreates this activity automatically.
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        findViewById(R.id.btn_help).setOnClickListener(v ->
                Toast.makeText(this, "Поддержка: support@inania.com", Toast.LENGTH_LONG).show());

        findViewById(R.id.btn_about).setOnClickListener(v ->
                Toast.makeText(this, "Anthuria — Создай дом мечты", Toast.LENGTH_SHORT).show());
    }
}
