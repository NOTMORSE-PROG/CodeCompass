package com.example.codecompass.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class TokenManager {

    private static final String PREFS_FILE      = "cc_secure_prefs";
    private static final String KEY_ACCESS_TOKEN  = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    private static final String USER_PREFS_FILE      = "cc_user_prefs";
    private static final String KEY_HAS_PASSWORD     = "has_password";
    private static final String KEY_GOOGLE_CONNECTED = "google_connected";

    private static SharedPreferences getPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            // Fallback to regular SharedPreferences (should not happen on supported devices)
            return context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        }
    }

    public static void saveTokens(Context context, String accessToken, String refreshToken) {
        getPrefs(context).edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    /** Updates only the access token (used by TokenAuthenticator after a silent refresh). */
    public static void saveAccessToken(Context context, String accessToken) {
        getPrefs(context).edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .apply();
    }

    public static String getBearerToken(Context context) {
        return "Bearer " + getAccessToken(context);
    }

    public static String getAccessToken(Context context) {
        return getPrefs(context).getString(KEY_ACCESS_TOKEN, null);
    }

    public static String getRefreshToken(Context context) {
        return getPrefs(context).getString(KEY_REFRESH_TOKEN, null);
    }

    public static boolean hasToken(Context context) {
        String token = getAccessToken(context);
        return token != null && !token.isEmpty();
    }

    // ── User flags (non-sensitive: hasPassword, googleConnected) ─────────────

    public static void saveUserFlags(Context context, boolean hasPassword, boolean googleConnected) {
        context.getSharedPreferences(USER_PREFS_FILE, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_HAS_PASSWORD,     hasPassword)
                .putBoolean(KEY_GOOGLE_CONNECTED, googleConnected)
                .apply();
    }

    public static boolean isHasPassword(Context context) {
        return context.getSharedPreferences(USER_PREFS_FILE, Context.MODE_PRIVATE)
                .getBoolean(KEY_HAS_PASSWORD, false);
    }

    public static boolean isGoogleConnected(Context context) {
        return context.getSharedPreferences(USER_PREFS_FILE, Context.MODE_PRIVATE)
                .getBoolean(KEY_GOOGLE_CONNECTED, false);
    }

    public static void clear(Context context) {
        getPrefs(context).edit().clear().apply();
        context.getSharedPreferences(USER_PREFS_FILE, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
