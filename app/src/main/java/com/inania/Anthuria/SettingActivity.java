package com.inania.Anthuria;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        // Кнопка назад
        ImageButton btnBack = findViewById(R.id.btn_back_settings);
        btnBack.setOnClickListener(v -> finish());

        // Переключатели
        SwitchMaterial switchNotifications = findViewById(R.id.switch_notifications);
        SwitchMaterial switchDarkMode = findViewById(R.id.switch_dark_mode);

        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String status = isChecked ? "Notifications enabled" : "Notifications disabled";
            Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
        });

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Toast.makeText(this, "Theme changes will be applied in the next update!", Toast.LENGTH_SHORT).show();
        });

        // Кнопки разделов
        findViewById(R.id.btn_help).setOnClickListener(v ->
                Toast.makeText(this, "Support email: support@inania.com", Toast.LENGTH_LONG).show()
        );

        findViewById(R.id.btn_about).setOnClickListener(v ->
                Toast.makeText(this, "Anthuria - Design Your Dream Home", Toast.LENGTH_SHORT).show()
        );
    }
}