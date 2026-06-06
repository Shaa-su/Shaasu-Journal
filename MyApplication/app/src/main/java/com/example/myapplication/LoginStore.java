package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Encrypted storage for login credentials and recovery data.
 * Auto-migrates from plain SharedPreferences on first access.
 */
public final class LoginStore {
    private LoginStore() {}

    private static final String ENCRYPTED_NAME = "login_secure";
    private static final String PLAIN_NAME = "login_prefs";

    public static SharedPreferences get(Context context) {
        Context appCtx = context.getApplicationContext();
        try {
            MasterKey masterKey = new MasterKey.Builder(appCtx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            SharedPreferences encrypted = EncryptedSharedPreferences.create(
                    appCtx,
                    ENCRYPTED_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            // Migrate from plain prefs if needed
            if (encrypted.getAll().isEmpty()) {
                SharedPreferences plain = appCtx.getSharedPreferences(PLAIN_NAME, Context.MODE_PRIVATE);
                if (!plain.getAll().isEmpty()) {
                    SharedPreferences.Editor editor = encrypted.edit();
                    for (String key : plain.getAll().keySet()) {
                        Object val = plain.getAll().get(key);
                        if (val instanceof String) editor.putString(key, (String) val);
                        else if (val instanceof Boolean) editor.putBoolean(key, (Boolean) val);
                        else if (val instanceof Integer) editor.putInt(key, (Integer) val);
                        else if (val instanceof Long) editor.putLong(key, (Long) val);
                    }
                    editor.apply();
                    plain.edit().clear().apply();
                }
            }

            return encrypted;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to open encrypted login store", e);
        }
    }
}
