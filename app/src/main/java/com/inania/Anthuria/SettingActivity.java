package com.inania.Anthuria;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        ImageButton btnBack = findViewById(R.id.btn_back_settings);
        btnBack.setOnClickListener(v -> finish());

        SwitchMaterial switchNotifications = findViewById(R.id.switch_notifications);
        SwitchMaterial switchDarkMode      = findViewById(R.id.switch_dark_mode);
        Spinner         spinnerLanguage    = findViewById(R.id.spinner_language);

        SharedPreferences prefs = getSharedPreferences(AnthuriaApp.PREFS_NAME, MODE_PRIVATE);
        boolean isDark = prefs.getBoolean(AnthuriaApp.KEY_DARK_MODE, false);
        switchDarkMode.setChecked(isDark);

        setupLanguageSpinner(spinnerLanguage);

        switchNotifications.setOnCheckedChangeListener((btn, isChecked) ->
                Toast.makeText(this,
                        isChecked ? R.string.notifications_enabled : R.string.notifications_disabled,
                        Toast.LENGTH_SHORT).show());

        switchDarkMode.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(AnthuriaApp.KEY_DARK_MODE, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        findViewById(R.id.btn_help).setOnClickListener(v ->
                Toast.makeText(this, R.string.support_contact, Toast.LENGTH_LONG).show());

        findViewById(R.id.btn_about).setOnClickListener(v ->
                Toast.makeText(this, R.string.about_app, Toast.LENGTH_SHORT).show());
    }

    private void setupLanguageSpinner(Spinner spinner) {
        String[] langs = {
                getString(R.string.language_english),
                getString(R.string.language_russian)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, langs);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        String current = LocaleHelper.getSavedLanguage(this);
        spinner.setSelection(LocaleHelper.LANG_RU.equals(current) ? 1 : 0, false);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String newLang = position == 1 ? LocaleHelper.LANG_RU : LocaleHelper.LANG_EN;
                // Guard covers both the automatic initial callback and same-item re-taps
                if (newLang.equals(LocaleHelper.getSavedLanguage(SettingActivity.this))) return;
                LocaleManager.applyLanguage(SettingActivity.this, newLang);
                LocaleManager.relocalizeActivity(SettingActivity.this);
                Toast.makeText(SettingActivity.this,
                        R.string.language_changed, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
}
