package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public final class EventStore {
    private EventStore() {}

    private static final String ENCRYPTED_PREFS = "events_secure";

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
            throw new IllegalStateException("Unable to open encrypted event store", e);
        }
    }

    public static final class EventItem {
        public final String id;
        public final String title;
        public final String note;
        public final int year;
        public final int month; // 0-11
        public final int day;
        public final boolean repeatYearly;
        public final boolean notifyOnDay;
        public final long createdAtMillis;

        public EventItem(String id, String title, String note, int year, int month, int day,
                         boolean repeatYearly, boolean notifyOnDay, long createdAtMillis) {
            this.id = id;
            this.title = title;
            this.note = note;
            this.year = year;
            this.month = month;
            this.day = day;
            this.repeatYearly = repeatYearly;
            this.notifyOnDay = notifyOnDay;
            this.createdAtMillis = createdAtMillis;
        }

        JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("title", title);
            obj.put("note", note);
            obj.put("year", year);
            obj.put("month", month);
            obj.put("day", day);
            obj.put("repeatYearly", repeatYearly);
            obj.put("notifyOnDay", notifyOnDay);
            obj.put("createdAtMillis", createdAtMillis);
            return obj;
        }

        static EventItem fromJson(JSONObject obj) throws Exception {
            if (obj == null) return null;
            return new EventItem(
                    obj.optString("id", UUID.randomUUID().toString()),
                    obj.optString("title", ""),
                    obj.optString("note", ""),
                    obj.optInt("year", 2026),
                    obj.optInt("month", 0),
                    obj.optInt("day", 1),
                    obj.optBoolean("repeatYearly", false),
                    obj.optBoolean("notifyOnDay", false),
                    obj.optLong("createdAtMillis", System.currentTimeMillis())
            );
        }
    }

    public static List<EventItem> getAll(Context context) {
        SharedPreferences prefs = get(context);
        String raw = prefs.getString("events_list", "[]");
        List<EventItem> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                EventItem item = EventItem.fromJson(arr.getJSONObject(i));
                if (item != null) out.add(item);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void put(Context context, EventItem item) {
        if (item == null) return;
        List<EventItem> all = getAll(context);
        // Replace existing or add new
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(item.id)) {
                all.set(i, item);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(item);
        saveAll(context, all);
    }

    public static void delete(Context context, String eventId) {
        if (eventId == null) return;
        List<EventItem> all = getAll(context);
        List<EventItem> remaining = new ArrayList<>();
        for (EventItem item : all) {
            if (!item.id.equals(eventId)) remaining.add(item);
        }
        saveAll(context, remaining);
    }

    private static void saveAll(Context context, List<EventItem> items) {
        try {
            JSONArray arr = new JSONArray();
            for (EventItem item : items) {
                arr.put(item.toJson());
            }
            get(context).edit().putString("events_list", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** Build a trigger timestamp for the event's next occurrence (respecting yearly repeat). */
    public static long computeTriggerAtMillis(EventItem item) {
        Calendar cal = Calendar.getInstance();
        int nowYear = cal.get(Calendar.YEAR);
        int nowMonth = cal.get(Calendar.MONTH);
        int nowDay = cal.get(Calendar.DAY_OF_MONTH);

        // If the event date is still in the future this year, use this year
        int targetYear = item.year;
        if (item.repeatYearly) {
            // For yearly events, use the next occurrence
            if (item.month < nowMonth || (item.month == nowMonth && item.day < nowDay)) {
                targetYear = nowYear + 1;
            } else {
                targetYear = nowYear;
            }
        }

        cal.set(Calendar.YEAR, targetYear);
        cal.set(Calendar.MONTH, item.month);
        cal.set(Calendar.DAY_OF_MONTH, item.day);
        cal.set(Calendar.HOUR_OF_DAY, 9); // 9:00 AM default
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.YEAR, 1);
        }
        return cal.getTimeInMillis();
    }
}
