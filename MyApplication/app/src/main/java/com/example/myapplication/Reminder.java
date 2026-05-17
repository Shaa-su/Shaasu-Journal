package com.example.myapplication;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public final class Reminder {
    public final String id;
    public final String title;
    public final long triggerAtMillis;
    public final String dateKey; // yyyy-MM-dd
    public final boolean repeatDaily;

    private Reminder(String id, String title, long triggerAtMillis, String dateKey, boolean repeatDaily) {
        this.id = id;
        this.title = title;
        this.triggerAtMillis = triggerAtMillis;
        this.dateKey = dateKey;
        this.repeatDaily = repeatDaily;
    }

    public static Reminder create(String title, long triggerAtMillis, String dateKey, boolean repeatDaily) {
        String safeTitle = title == null ? "" : title.trim();
        if (safeTitle.isEmpty()) safeTitle = "Reminder";
        String id = UUID.randomUUID().toString();
        return new Reminder(id, safeTitle, triggerAtMillis, dateKey, repeatDaily);
    }

    public static Reminder update(String id, String title, long triggerAtMillis, String dateKey, boolean repeatDaily) {
        if (id == null) return null;
        String safeTitle = title == null ? "" : title.trim();
        if (safeTitle.isEmpty()) safeTitle = "Reminder";
        return new Reminder(id, safeTitle, triggerAtMillis, dateKey, repeatDaily);
    }

    public Reminder withTriggerAt(long newTriggerAtMillis) {
        return new Reminder(id, title, newTriggerAtMillis, dateKey, repeatDaily);
    }

    public Reminder withTriggerAndDate(long newTriggerAtMillis, String newDateKey) {
        return new Reminder(id, title, newTriggerAtMillis, newDateKey, repeatDaily);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("title", title);
        obj.put("triggerAtMillis", triggerAtMillis);
        obj.put("dateKey", dateKey);
        obj.put("repeatDaily", repeatDaily);
        return obj;
    }

    public static Reminder fromJson(JSONObject obj) throws JSONException {
        if (obj == null) return null;
        String id = obj.optString("id", null);
        String title = obj.optString("title", "Reminder");
        long triggerAtMillis = obj.optLong("triggerAtMillis", -1);
        String dateKey = obj.optString("dateKey", null);
        boolean repeatDaily = obj.has("repeatDaily")
            ? obj.optBoolean("repeatDaily", false)
            : obj.optBoolean("repeatHourly", false);
        if (id == null || dateKey == null || triggerAtMillis <= 0) return null;
        return new Reminder(id, title, triggerAtMillis, dateKey, repeatDaily);
    }
}
