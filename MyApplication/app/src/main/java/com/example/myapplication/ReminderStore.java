package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class ReminderStore {
    private ReminderStore() {}

    private static final String ENCRYPTED_PREFS = "reminders_secure";

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
            throw new IllegalStateException("Unable to open encrypted reminder store", e);
        }
    }

    public static List<Reminder> getAll(Context context) {
        SharedPreferences prefs = get(context);
        Map<String, ?> all = prefs.getAll();
        List<Reminder> out = new ArrayList<>();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            if (!(e.getValue() instanceof String)) continue;
            String raw = (String) e.getValue();
            try {
                Reminder r = Reminder.fromJson(new JSONObject(raw));
                if (r != null) out.add(r);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public static Reminder getById(Context context, String id) {
        if (id == null) return null;
        SharedPreferences prefs = get(context);
        String raw = prefs.getString(id, null);
        if (raw == null) return null;
        try {
            return Reminder.fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void put(Context context, Reminder reminder) {
        if (reminder == null) return;
        SharedPreferences prefs = get(context);
        try {
            prefs.edit().putString(reminder.id, reminder.toJson().toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void delete(Context context, String id) {
        if (id == null) return;
        SharedPreferences prefs = get(context);
        prefs.edit().remove(id).apply();
    }

    public static void clear(Context context) {
        SharedPreferences prefs = get(context);
        prefs.edit().clear().apply();
    }

    public static void cleanupExpired(Context context) {
        long now = System.currentTimeMillis();
        List<Reminder> all = getAll(context);
        for (Reminder r : all) {
            if (!r.repeatDaily && isDayDone(r, now)) {
                delete(context, r.id);
            }
        }
    }

    public static HashSet<String> getReminderDateKeys(Context context) {
        long now = System.currentTimeMillis();
        List<Reminder> all = getAll(context);
        HashSet<String> keys = new HashSet<>();
        for (Reminder r : all) {
            if (r == null || r.dateKey == null) continue;
            if (isDayDone(r, now)) continue;
            keys.add(r.dateKey);
        }
        return keys;
    }

    public static boolean isDayDone(Reminder r, long nowMillis) {
        if (r == null || r.dateKey == null) return true;
        Calendar end = parseDateKeyEndOfDay(r.dateKey);
        if (end == null) return true;
        return nowMillis > end.getTimeInMillis();
    }

    public static Calendar parseDateKeyEndOfDay(String dateKey) {
        if (dateKey == null) return null;
        try {
            String[] parts = dateKey.split("-");
            if (parts.length != 3) return null;
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]) - 1;
            int d = Integer.parseInt(parts[2]);
            Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR, y);
            c.set(Calendar.MONTH, m);
            c.set(Calendar.DAY_OF_MONTH, d);
            c.set(Calendar.HOUR_OF_DAY, 23);
            c.set(Calendar.MINUTE, 59);
            c.set(Calendar.SECOND, 59);
            c.set(Calendar.MILLISECOND, 999);
            return c;
        } catch (Exception ignored) {
            return null;
        }
    }
}
