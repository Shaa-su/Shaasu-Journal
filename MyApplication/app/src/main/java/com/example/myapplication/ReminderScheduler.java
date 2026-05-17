package com.example.myapplication;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.List;

public final class ReminderScheduler {
    private ReminderScheduler() {}

    public static void schedule(Context context, Reminder reminder) {
        if (context == null || reminder == null) return;
        long now = System.currentTimeMillis();
        if (reminder.triggerAtMillis <= now) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildPendingIntent(context, reminder.id);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!am.canScheduleExactAlarms()) {
                am.set(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pi);
                return;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pi);
        }
    }

    public static void cancel(Context context, Reminder reminder) {
        if (context == null || reminder == null) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = buildPendingIntent(context, reminder.id);
        am.cancel(pi);
    }

    public static void rescheduleAll(Context context) {
        if (context == null) return;
        ReminderStore.cleanupExpired(context);
        List<Reminder> all = ReminderStore.getAll(context);
        for (Reminder r : all) {
            if (r != null && !ReminderStore.isDayDone(r, System.currentTimeMillis())) {
                schedule(context, r);
            }
        }
    }

    private static PendingIntent buildPendingIntent(Context context, String reminderId) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.example.myapplication.REMINDER_TRIGGER");
        intent.putExtra("reminder_id", reminderId);
        int requestCode = reminderId != null ? reminderId.hashCode() : 0;
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
