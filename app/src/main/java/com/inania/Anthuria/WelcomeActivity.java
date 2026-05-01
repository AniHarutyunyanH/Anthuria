package com.inania.Anthuria;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends BaseActivity {

    // В WelcomeActivity.java теперь только это:
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        findViewById(R.id.btn_get_started).setOnClickListener(v -> {
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putBoolean("isFirstRun", false).apply();
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });
    }
}