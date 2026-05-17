package com.inania.Anthuria;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

public final class LocaleHelper {

    public static final String KEY_LANGUAGE = "language";
    public static final String LANG_EN = "en";
    public static final String LANG_RU = "ru";

    private LocaleHelper() {}

    public static String getSavedLanguage(Context ctx) {
        return ctx.getSharedPreferences(AnthuriaApp.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, LANG_EN);
    }

    public static void setLanguage(Context ctx, String lang) {
        ctx.getSharedPreferences(AnthuriaApp.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANGUAGE, lang).commit(); // commit = synchronous, safe before restart
    }

    public static Context wrap(Context base) {
        String lang = base.getSharedPreferences(AnthuriaApp.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, LANG_EN);
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Configuration config = new Configuration(base.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(new LocaleList(locale));
        } else {
            config.setLocale(locale);
        }
        return base.createConfigurationContext(config);
    }
}
