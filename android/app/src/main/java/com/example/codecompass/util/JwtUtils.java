package com.example.codecompass.util;

import android.util.Base64;

import org.json.JSONObject;

/**
 * Client-side JWT decoder — mirrors web frontend's decodeToken() in auth.js.
 * Does NOT verify signature; used only to read claims from a trusted backend-issued token.
 */
public class JwtUtils {

    private JwtUtils() {}

    /**
     * Decode the payload of a JWT and return it as a JSONObject.
     * Returns null if the token is null, malformed, or cannot be parsed.
     */
    public static JSONObject decode(String token) {
        if (token == null || token.isEmpty()) return null;
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_PADDING);
            return new JSONObject(new String(decoded));
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns true if the token exists and is not expired. */
    public static boolean isValid(String token) {
        JSONObject payload = decode(token);
        if (payload == null) return false;
        long exp = payload.optLong("exp", 0) * 1000L;
        return exp > System.currentTimeMillis();
    }

    public static String getFullName(String token) {
        JSONObject p = decode(token);
        return p != null ? p.optString("full_name", "") : "";
    }

    public static String getEmail(String token) {
        JSONObject p = decode(token);
        return p != null ? p.optString("email", "") : "";
    }

    public static String getRole(String token) {
        JSONObject p = decode(token);
        return p != null ? p.optString("role", "") : "";
    }

    public static boolean isOnboarded(String token) {
        JSONObject p = decode(token);
        return p != null && p.optBoolean("is_onboarded", false);
    }

    public static boolean isEmailVerified(String token) {
        JSONObject p = decode(token);
        return p != null && p.optBoolean("email_verified", false);
    }
}
