package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public final class StoryStore {
    private StoryStore() {}

    // Plaintext legacy store name
    private static final String LEGACY_PREFS = "stories";

    // Encrypted store name
    private static final String ENCRYPTED_PREFS = "stories_secure";

    public static SharedPreferences get(Context context) {
        Context appCtx = context.getApplicationContext();
        try {
            MasterKey masterKey = new MasterKey.Builder(appCtx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    appCtx,
                    ENCRYPTED_PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // If this fails, we should not silently fall back to plaintext.
            throw new IllegalStateException("Unable to open encrypted story store", e);
        }
    }

    public static SharedPreferences getLegacyPlaintext(Context context) {
        return context.getApplicationContext().getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
    }

    public static void migrateIfNeeded(Context context) {
        SharedPreferences encrypted = get(context);
        SharedPreferences legacy = getLegacyPlaintext(context);

        // If encrypted already has data, don't overwrite.
        if (!encrypted.getAll().isEmpty()) return;
        if (legacy.getAll().isEmpty()) return;

        SharedPreferences.Editor dst = encrypted.edit();
        for (String key : legacy.getAll().keySet()) {
            Object val = legacy.getAll().get(key);
            if (val instanceof String) {
                dst.putString(key, (String) val);
            }
        }
        dst.apply();

        legacy.edit().clear().apply();
    }
}
