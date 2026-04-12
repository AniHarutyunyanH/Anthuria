package com.inania.Anthuria;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstRun = pref.getBoolean("isFirstRun", true);
        if (isFirstRun) {
            // Самый первый раз — идем на приветствие
            startActivity(new Intent(this, WelcomeActivity.class));
        } else {
            // Уже заходили — проверяем авторизацию Firebase
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                startActivity(new Intent(this, MainActivity.class));
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
        }
        finish(); // Закрываем диспетчер, чтобы он не висел в памяти
    }
}