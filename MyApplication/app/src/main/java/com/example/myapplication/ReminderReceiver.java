package com.example.myapplication;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "shaasu_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String reminderId = intent.getStringExtra("reminder_id");
        if (reminderId == null) return;

        Reminder r = ReminderStore.getById(context, reminderId);
        if (r == null) return;

        createChannel(context);
        String title = r.title != null ? r.title : "Reminder";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText("Reminder")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify(reminderId.hashCode(), builder.build());

        long now = System.currentTimeMillis();
        if (ReminderStore.isDayDone(r, now)) {
            ReminderStore.delete(context, r.id);
            return;
        }

        if (r.repeatDaily) {
            Calendar next = Calendar.getInstance();
            next.setTimeInMillis(Math.max(now, r.triggerAtMillis));
            next.add(Calendar.DAY_OF_MONTH, 1);
            String newDateKey = String.format("%04d-%02d-%02d",
                    next.get(Calendar.YEAR),
                    next.get(Calendar.MONTH) + 1,
                    next.get(Calendar.DAY_OF_MONTH));
            Reminder updated = r.withTriggerAndDate(next.getTimeInMillis(), newDateKey);
            ReminderStore.put(context, updated);
            ReminderScheduler.schedule(context, updated);
        } else {
            ReminderStore.delete(context, r.id);
        }
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Shaasu reminders");
        nm.createNotificationChannel(channel);
    }
}
