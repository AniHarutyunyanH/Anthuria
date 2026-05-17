package com.inania.Anthuria;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class BaseActivity extends AppCompatActivity {

    private BroadcastReceiver localeReceiver;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onStart() {
        super.onStart();
        localeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                LocaleManager.relocalizeActivity(BaseActivity.this);
            }
        };
        IntentFilter filter = new IntentFilter(LocaleManager.ACTION_LOCALE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(this, localeReceiver, filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(localeReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        if (localeReceiver != null) {
            try {
                unregisterReceiver(localeReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            localeReceiver = null;
        }
        super.onStop();
    }
}
