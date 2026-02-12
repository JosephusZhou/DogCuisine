package com.dogcuisine.sync;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class WebDavSyncConfig {

    public static final String PREF_SYNC = "webdav_sync";
    public static final String KEY_URL = "url";
    public static final String KEY_USER = "user";
    public static final String KEY_PASS = "pass";

    public final String url;
    public final String user;
    public final String pass;

    private WebDavSyncConfig(@NonNull String url, @NonNull String user, @NonNull String pass) {
        this.url = url;
        this.user = user;
        this.pass = pass;
    }

    @Nullable
    public static WebDavSyncConfig load(@NonNull Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE);
        String url = safe(sp.getString(KEY_URL, ""));
        String user = safe(sp.getString(KEY_USER, ""));
        String pass = safe(sp.getString(KEY_PASS, ""));
        if (url.isEmpty()) {
            return null;
        }
        return new WebDavSyncConfig(url, user, pass);
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
