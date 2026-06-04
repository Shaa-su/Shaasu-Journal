package com.example.myapplication;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import java.io.InputStream;

public class EventsActivity extends AppCompatActivity {

    private static final int PICK_BACKGROUND_REQUEST = 100;
    private static final String PREFS_ALERTS_ENABLED = "events_alerts_enabled";

    private View enableAlertsButton;
    private TextView newButton;
    private TextView addFirstEventButton;
    private ImageView enableAlertsIcon;
    private TextView enableAlertsText;

    // Repeat & toggle state
    private boolean repeatYearly = true;
    private boolean notifyOnDay = true;
    // Event date state
    private int eventMonth; // 0-11
    private int eventDay;

    // Master alerts toggle
    private boolean alertsEnabled = false;

    // Background photo state
    private Uri selectedBackgroundUri;
    private AlertDialog currentDialog;
    private ImageView backgroundImage;
    private View uploadOverlay;

    private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_events);

        View root = findViewById(android.R.id.content);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                return insets;
            });
            ViewCompat.requestApplyInsets(root);
        }

        // Back button
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // Enable Alerts — find icon + text subviews
        enableAlertsButton = findViewById(R.id.enableAlertsButton);
        if (enableAlertsButton != null) {
            enableAlertsIcon = enableAlertsButton.findViewById(R.id.enableAlertsIcon);
            enableAlertsText = enableAlertsButton.findViewById(R.id.enableAlertsText);
            // Load persisted state
            alertsEnabled = getSharedPreferences("events_prefs", MODE_PRIVATE)
                    .getBoolean(PREFS_ALERTS_ENABLED, false);
            updateAlertsButtonUi();
            enableAlertsButton.setOnClickListener(v -> handleEnableAlerts());
        }

        // + New
        newButton = findViewById(R.id.newButton);
        if (newButton != null) {
            newButton.setOnClickListener(v -> showNewEventDialog());
        }

        // + Add First Event
        addFirstEventButton = findViewById(R.id.addFirstEventButton);
        if (addFirstEventButton != null) {
            addFirstEventButton.setOnClickListener(v -> showNewEventDialog());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_BACKGROUND_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                selectedBackgroundUri = imageUri;
                // Update dialog UI if it's still showing
                if (backgroundImage != null) {
                    try {
                        InputStream is = getContentResolver().openInputStream(imageUri);
                        Bitmap bm = BitmapFactory.decodeStream(is);
                        if (bm != null) {
                            backgroundImage.setImageBitmap(bm);
                            backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            backgroundImage.setColorFilter(null);
                            if (uploadOverlay != null) uploadOverlay.setVisibility(View.GONE);
                        }
                        if (is != null) is.close();
                    } catch (Exception e) {
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    private void handleEnableAlerts() {
        alertsEnabled = !alertsEnabled;
        // Persist
        getSharedPreferences("events_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean(PREFS_ALERTS_ENABLED, alertsEnabled)
                .apply();

        updateAlertsButtonUi();

        if (alertsEnabled) {
            // Re-schedule all event reminders
            scheduleAllEventReminders();
            Toast.makeText(this, "Alerts enabled for all events", Toast.LENGTH_SHORT).show();
        } else {
            // Cancel all event reminders
            cancelAllEventReminders();
            Toast.makeText(this, "Alerts disabled", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAlertsButtonUi() {
        if (enableAlertsButton == null) return;
        if (alertsEnabled) {
            enableAlertsButton.setBackgroundResource(R.drawable.bg_event_pill_glow);
            if (enableAlertsIcon != null)
                enableAlertsIcon.setColorFilter(getColor(R.color.event_accent));
            if (enableAlertsText != null) {
                enableAlertsText.setTextColor(getColor(R.color.event_accent));
                enableAlertsText.setText("Alerts On");
            }
        } else {
            enableAlertsButton.setBackgroundResource(R.drawable.bg_event_pill_glow_off);
            if (enableAlertsIcon != null)
                enableAlertsIcon.setColorFilter(getColor(R.color.menu_text_secondary));
            if (enableAlertsText != null) {
                enableAlertsText.setTextColor(getColor(R.color.menu_text_secondary));
                enableAlertsText.setText("Enable Alerts");
            }
        }
    }

    private void scheduleAllEventReminders() {
        java.util.List<EventStore.EventItem> events = EventStore.getAll(this);
        for (EventStore.EventItem event : events) {
            if (!event.notifyOnDay) continue;
            long triggerAt = EventStore.computeTriggerAtMillis(event);
            String dateKey = String.format("%04d-%02d-%02d", event.year, event.month + 1, event.day);
            Reminder reminder = Reminder.create(event.title, triggerAt, dateKey, false);
            ReminderStore.put(this, reminder);
            ReminderScheduler.schedule(this, reminder);
        }
    }

    private void cancelAllEventReminders() {
        java.util.List<Reminder> all = ReminderStore.getAll(this);
        for (Reminder r : all) {
            ReminderScheduler.cancel(this, r);
            ReminderStore.delete(this, r.id);
        }
    }

    private void showNewEventDialog() {
        View container = LayoutInflater.from(this).inflate(R.layout.dialog_new_event, null, false);

        EditText titleInput = container.findViewById(R.id.eventTitleInput);
        EditText noteInput = container.findViewById(R.id.eventNoteInput);
        TextView repeatYearlyBtn = container.findViewById(R.id.repeatYearly);
        TextView repeatOnceBtn = container.findViewById(R.id.repeatOnce);
        TextView monthSelector = container.findViewById(R.id.monthSelector);
        TextView daySelector = container.findViewById(R.id.daySelector);
        View notifyToggle = container.findViewById(R.id.notifyToggle);
        TextView saveButton = container.findViewById(R.id.saveEventButton);
        View closeButton = container.findViewById(R.id.newEventClose);
        View backgroundSelector = container.findViewById(R.id.backgroundSelector);
        backgroundImage = container.findViewById(R.id.backgroundImage);
        uploadOverlay = container.findViewById(R.id.uploadOverlay);

        if (titleInput == null || saveButton == null || closeButton == null) return;

        // Initialize date from current date
        java.util.Calendar now = java.util.Calendar.getInstance();
        eventMonth = now.get(java.util.Calendar.MONTH);
        eventDay = now.get(java.util.Calendar.DAY_OF_MONTH);

        if (monthSelector != null) monthSelector.setText(MONTH_NAMES[eventMonth]);
        if (daySelector != null) daySelector.setText(String.valueOf(eventDay));

        // Restore previously selected background if re-opening dialog
        if (selectedBackgroundUri != null && backgroundImage != null) {
            try {
                InputStream is = getContentResolver().openInputStream(selectedBackgroundUri);
                Bitmap bm = BitmapFactory.decodeStream(is);
                if (bm != null) {
                    backgroundImage.setImageBitmap(bm);
                    backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    backgroundImage.setColorFilter(null);
                    if (uploadOverlay != null) uploadOverlay.setVisibility(View.GONE);
                }
                if (is != null) is.close();
            } catch (Exception ignored) {}
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();

        currentDialog = dialog;

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });

        // Repeat segment toggle
        if (repeatYearlyBtn != null && repeatOnceBtn != null) {
            updateRepeatSegments(repeatYearlyBtn, repeatOnceBtn);
            repeatYearlyBtn.setOnClickListener(v -> {
                repeatYearly = true;
                updateRepeatSegments(repeatYearlyBtn, repeatOnceBtn);
            });
            repeatOnceBtn.setOnClickListener(v -> {
                repeatYearly = false;
                updateRepeatSegments(repeatYearlyBtn, repeatOnceBtn);
            });
        }

        // Notify toggle
        if (notifyToggle != null) {
            updateToggleUi((LinearLayout) notifyToggle);
            notifyToggle.setOnClickListener(v -> {
                notifyOnDay = !notifyOnDay;
                updateToggleUi((LinearLayout) notifyToggle);
            });
        }

        closeButton.setOnClickListener(v -> dialog.dismiss());

        // Month selector
        if (monthSelector != null) {
            monthSelector.setOnClickListener(v -> {
                String[] months = MONTH_NAMES;
                int current = eventMonth;
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Select Month")
                        .setItems(months, (d, which) -> {
                            eventMonth = which;
                            monthSelector.setText(MONTH_NAMES[which]);
                        })
                        .show();
            });
        }

        // Day selector
        if (daySelector != null) {
            daySelector.setOnClickListener(v -> {
                int maxDay = 31;
                java.util.Calendar temp = java.util.Calendar.getInstance();
                temp.set(java.util.Calendar.YEAR, now.get(java.util.Calendar.YEAR));
                temp.set(java.util.Calendar.MONTH, eventMonth);
                maxDay = temp.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
                String[] days = new String[maxDay];
                for (int i = 0; i < maxDay; i++) days[i] = String.valueOf(i + 1);
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Select Day")
                        .setItems(days, (d, which) -> {
                            eventDay = which + 1;
                            daySelector.setText(String.valueOf(eventDay));
                        })
                        .show();
            });
        }

        // Background photo picker
        if (backgroundSelector != null) {
            backgroundSelector.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                try {
                    startActivityForResult(intent, PICK_BACKGROUND_REQUEST);
                } catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show();
                }
            });
        }

        saveButton.setOnClickListener(v -> {
            String title = titleInput.getText() != null ? titleInput.getText().toString().trim() : "";
            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter an event title", Toast.LENGTH_SHORT).show();
                return;
            }

            String note = noteInput != null && noteInput.getText() != null
                    ? noteInput.getText().toString().trim() : "";

            int currentYear = now.get(java.util.Calendar.YEAR);

            // Create and save the event
            EventStore.EventItem event = new EventStore.EventItem(
                    java.util.UUID.randomUUID().toString(),
                    title,
                    note,
                    currentYear,
                    eventMonth,
                    eventDay,
                    repeatYearly,
                    notifyOnDay,
                    System.currentTimeMillis()
            );
            EventStore.put(this, event);

            // Schedule notification only if both master toggle AND event toggle are ON
            if (notifyOnDay && alertsEnabled) {
                long triggerAt = EventStore.computeTriggerAtMillis(event);
                String dateKey = String.format("%04d-%02d-%02d", event.year, event.month + 1, event.day);

                Reminder reminder = Reminder.create(title, triggerAt, dateKey, false);
                ReminderStore.put(this, reminder);
                ReminderScheduler.schedule(this, reminder);
            }

            String msg = "Event saved" + (notifyOnDay && !alertsEnabled ? " (alerts are off)" : "");
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateRepeatSegments(TextView yearly, TextView once) {
        if (yearly == null || once == null) return;
        if (repeatYearly) {
            yearly.setBackgroundResource(R.drawable.bg_event_segment_selected);
            yearly.setTextColor(getColor(R.color.menu_bg));
            once.setBackgroundResource(R.drawable.bg_event_segment_unselected);
            once.setTextColor(getColor(R.color.menu_text_secondary));
        } else {
            once.setBackgroundResource(R.drawable.bg_event_segment_selected);
            once.setTextColor(getColor(R.color.menu_bg));
            yearly.setBackgroundResource(R.drawable.bg_event_segment_unselected);
            yearly.setTextColor(getColor(R.color.menu_text_secondary));
        }
    }

    private void updateToggleUi(LinearLayout toggle) {
        if (toggle == null) return;
        if (notifyOnDay) {
            toggle.setBackgroundResource(R.drawable.bg_event_toggle_track);
            toggle.setGravity(android.view.Gravity.END);
        } else {
            toggle.setBackgroundResource(R.drawable.bg_event_segment_unselected);
            toggle.setGravity(android.view.Gravity.START);
        }
    }
}
