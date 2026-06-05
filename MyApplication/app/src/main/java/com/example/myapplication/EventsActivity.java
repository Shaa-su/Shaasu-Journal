package com.example.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import java.io.InputStream;

public class EventsActivity extends AppCompatActivity {

    private static final int PICK_BACKGROUND_REQUEST = 100;
    private static final int EDIT_EVENT_REQUEST = 200;
    private static final String PREFS_ALERTS_ENABLED = "events_alerts_enabled";

    private View enableAlertsButton;
    private TextView newButton;
    private TextView addFirstEventButton;
    private ImageView enableAlertsIcon;
    private TextView enableAlertsText;
    private View eventsScroll;
    private View emptyStateScroll;
    private LinearLayout eventsListContainer;

    // Repeat & toggle state
    private boolean repeatYearly = true;
    private boolean notifyOnDay = true;
    private int notifyHour = 9;
    private int notifyMinute = 0;
    private int overlayOpacity = 40; // 0-100
    // Event date state
    private int eventMonth; // 0-11
    private int eventDay;

    // Notification permission launcher
    private ActivityResultLauncher<String> notificationPermissionLauncher;

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

        // Notification permission launcher
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Notification permission denied — alerts won't work", Toast.LENGTH_LONG).show();
                    }
                }
        );

        // Back button
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // Events list containers
        eventsScroll = findViewById(R.id.eventsScroll);
        emptyStateScroll = findViewById(R.id.emptyStateScroll);
        eventsListContainer = findViewById(R.id.eventsListContainer);

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
            newButton.setOnClickListener(v -> {
                requestNotificationPermissionIfNeeded();
                showNewEventDialog();
            });
        }

        // + Add First Event
        addFirstEventButton = findViewById(R.id.addFirstEventButton);
        if (addFirstEventButton != null) {
            addFirstEventButton.setOnClickListener(v -> {
                requestNotificationPermissionIfNeeded();
                showNewEventDialog();
            });
        }

        // Load and display events
        refreshEventsList();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_BACKGROUND_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                // Copy to internal storage so it persists across activity restarts
                selectedBackgroundUri = copyToInternalStorage(imageUri);
                // Update dialog UI if it's still showing
                if (backgroundImage != null) {
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
                    } catch (Exception e) {
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    /** Copy a content URI to internal storage and return a permanent file:// URI. */
    private Uri copyToInternalStorage(Uri contentUri) {
        try {
            String fileName = "event_bg_" + System.currentTimeMillis() + ".jpg";
            java.io.File outFile = new java.io.File(getFilesDir(), fileName);
            InputStream is = getContentResolver().openInputStream(contentUri);
            java.io.FileOutputStream os = new java.io.FileOutputStream(outFile);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            is.close();
            os.close();
            return Uri.fromFile(outFile);
        } catch (Exception e) {
            // Fall back to original URI if copy fails
            return contentUri;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshEventsList();
    }

    private void refreshEventsList() {
        if (eventsListContainer == null || eventsScroll == null || emptyStateScroll == null) return;

        java.util.List<EventStore.EventItem> events = EventStore.getAll(this);
        eventsListContainer.removeAllViews();

        if (events.isEmpty()) {
            eventsScroll.setVisibility(View.GONE);
            emptyStateScroll.setVisibility(View.VISIBLE);
            return;
        }

        eventsScroll.setVisibility(View.VISIBLE);
        emptyStateScroll.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        java.text.SimpleDateFormat dateFmt = new java.text.SimpleDateFormat("MMMM d", java.util.Locale.US);
        java.util.Calendar today = java.util.Calendar.getInstance();

        // Group events into sections
        java.util.List<EventStore.EventItem> todayEvents = new java.util.ArrayList<>();
        java.util.List<EventStore.EventItem> pastEvents = new java.util.ArrayList<>();
        java.util.List<EventStore.EventItem> thisMonthEvents = new java.util.ArrayList<>();
        java.util.List<EventStore.EventItem> futureEvents = new java.util.ArrayList<>();

        for (EventStore.EventItem event : events) {
            boolean isToday = event.month == today.get(java.util.Calendar.MONTH)
                    && event.day == today.get(java.util.Calendar.DAY_OF_MONTH);
            boolean isPastThisYear = event.month < today.get(java.util.Calendar.MONTH)
                    || (event.month == today.get(java.util.Calendar.MONTH)
                        && event.day < today.get(java.util.Calendar.DAY_OF_MONTH));

            if (isToday) {
                todayEvents.add(event);
            } else if (isPastThisYear && !event.repeatYearly) {
                // Non-yearly events that have passed
                pastEvents.add(event);
            } else if (event.month == today.get(java.util.Calendar.MONTH)) {
                // Same month, not today, not past
                thisMonthEvents.add(event);
            } else {
                futureEvents.add(event);
            }
        }

        // Build sections
        addEventSection("Today", todayEvents, inflater, dateFmt, today);
        addEventSection("Past", pastEvents, inflater, dateFmt, today);
        addEventSection("This Month", thisMonthEvents, inflater, dateFmt, today);
        addEventSection("Upcoming", futureEvents, inflater, dateFmt, today);
    }

    private void addEventSection(String title, java.util.List<EventStore.EventItem> events,
                                  LayoutInflater inflater, java.text.SimpleDateFormat dateFmt,
                                  java.util.Calendar today) {
        if (events.isEmpty()) return;

        // Section header with styled border
        LinearLayout headerBox = new LinearLayout(this);
        headerBox.setOrientation(LinearLayout.HORIZONTAL);
        headerBox.setBackgroundResource(R.drawable.bg_event_section_header);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headerLp.setMargins(0, dpToPx(16), 0, dpToPx(10));
        headerBox.setLayoutParams(headerLp);
        headerBox.setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8));

        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(getColor(R.color.event_accent));
        header.setTextSize(14f);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        headerBox.addView(header);

        eventsListContainer.addView(headerBox);

        for (EventStore.EventItem event : events) {
            View card = inflater.inflate(R.layout.item_event_card, eventsListContainer, false);
            bindEventCard(card, event, dateFmt, today);
            eventsListContainer.addView(card);
        }
    }

    private void bindEventCard(View card, EventStore.EventItem event,
                                java.text.SimpleDateFormat dateFmt, java.util.Calendar today) {
        TextView titleView = card.findViewById(R.id.eventCardTitle);
        TextView noteView = card.findViewById(R.id.eventCardNote);
        TextView todayTextView = card.findViewById(R.id.eventTodayText);
        TextView dateTextView = card.findViewById(R.id.eventDateText);
        TextView yearlyChip = card.findViewById(R.id.eventYearlyChip);
        TextView alertChip = card.findViewById(R.id.eventAlertChip);
        ImageView cardImage = card.findViewById(R.id.eventCardImage);
        View alertToggle = card.findViewById(R.id.eventAlertToggle);
        View deleteButton = card.findViewById(R.id.eventDeleteButton);

        if (titleView != null) titleView.setText(event.title);
        if (noteView != null) noteView.setText(event.note.isEmpty() ? "No note" : event.note);

        // Load background image (prefer base64, fallback to file URI)
        if (cardImage != null) {
            Bitmap bm = null;
            // Try base64 first (survives export/import)
            if (event.backgroundBase64 != null && !event.backgroundBase64.isEmpty()) {
                try {
                    byte[] bytes = android.util.Base64.decode(event.backgroundBase64, android.util.Base64.DEFAULT);
                    bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                } catch (Exception ignored) {}
            }
            // Fallback to file URI
            if (bm == null && event.backgroundUri != null && !event.backgroundUri.isEmpty()) {
                try {
                    Uri uri = Uri.parse(event.backgroundUri);
                    if ("file".equals(uri.getScheme())) {
                        bm = BitmapFactory.decodeFile(uri.getPath());
                    } else {
                        InputStream is = getContentResolver().openInputStream(uri);
                        bm = BitmapFactory.decodeStream(is);
                        if (is != null) is.close();
                    }
                } catch (Exception ignored) {}
            }
            if (bm != null) {
                cardImage.setImageBitmap(bm);
                cardImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cardImage.setColorFilter(null);
            } else {
                cardImage.setImageDrawable(null);
            }
        }

        // Apply overlay opacity from event
        View scrimOverlay = card.findViewById(R.id.eventScrimOverlay);
        if (scrimOverlay != null) {
            float alpha = Math.max(0f, Math.min(1f, event.overlayOpacity / 100f));
            scrimOverlay.setAlpha(alpha);
        }

        // Date display
        java.util.Calendar eventCal = java.util.Calendar.getInstance();
        eventCal.set(java.util.Calendar.YEAR, event.year);
        eventCal.set(java.util.Calendar.MONTH, event.month);
        eventCal.set(java.util.Calendar.DAY_OF_MONTH, event.day);
        String dateStr = dateFmt.format(eventCal.getTime());
        if (dateTextView != null) dateTextView.setText(dateStr);

        // Repeat type chip
        if (yearlyChip != null) {
            yearlyChip.setVisibility(View.VISIBLE);
            if (event.repeatYearly) {
                yearlyChip.setText("Yearly");
                yearlyChip.setTextColor(getColor(R.color.menu_text_primary));
                yearlyChip.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.menu_text_primary)));
            } else {
                yearlyChip.setText("Once");
                yearlyChip.setTextColor(getColor(R.color.menu_text_primary));
                yearlyChip.setAlpha(0.8f);
                yearlyChip.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.menu_text_primary)));
            }
        }

        // Alert chip — reflects actual state (master toggle + event setting)
        if (alertChip != null) {
            boolean alertActive = event.notifyOnDay && alertsEnabled;
            alertChip.setVisibility(View.VISIBLE);
            if (alertActive) {
                alertChip.setText("Alert on");
                alertChip.setTextColor(getColor(R.color.event_accent));
                alertChip.setAlpha(1f);
                alertChip.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.event_accent)));
            } else {
                alertChip.setText("Alert off");
                alertChip.setTextColor(getColor(R.color.menu_text_secondary));
                alertChip.setAlpha(0.5f);
                alertChip.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.menu_text_secondary)));
            }
        }

        // Dynamic status text: Today / Finished / date + days count
        boolean isToday = event.month == today.get(java.util.Calendar.MONTH)
                && event.day == today.get(java.util.Calendar.DAY_OF_MONTH);
        if (todayTextView != null) {
            java.util.Calendar eventCalThisYear = java.util.Calendar.getInstance();
            eventCalThisYear.set(java.util.Calendar.YEAR, today.get(java.util.Calendar.YEAR));
            eventCalThisYear.set(java.util.Calendar.MONTH, event.month);
            eventCalThisYear.set(java.util.Calendar.DAY_OF_MONTH, event.day);
            eventCalThisYear.set(java.util.Calendar.HOUR_OF_DAY, 0);
            eventCalThisYear.set(java.util.Calendar.MINUTE, 0);
            eventCalThisYear.set(java.util.Calendar.SECOND, 0);
            eventCalThisYear.set(java.util.Calendar.MILLISECOND, 0);

            long todayMs = today.getTimeInMillis();
            long eventMs = eventCalThisYear.getTimeInMillis();
            long diffDays = (eventMs - todayMs) / (1000L * 60 * 60 * 24);

            if (isToday) {
                todayTextView.setText("Today");
                todayTextView.setTextColor(getColor(R.color.event_accent));
                todayTextView.setVisibility(View.VISIBLE);
            } else if (eventMs < todayMs && !event.repeatYearly) {
                // Past, not yearly → Finished with full date including year
                java.text.SimpleDateFormat fullDateFmt = new java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.US);
                java.util.Calendar pastCal = java.util.Calendar.getInstance();
                pastCal.set(java.util.Calendar.YEAR, event.year);
                pastCal.set(java.util.Calendar.MONTH, event.month);
                pastCal.set(java.util.Calendar.DAY_OF_MONTH, event.day);
                String finishedDate = fullDateFmt.format(pastCal.getTime());
                todayTextView.setText("Finished  ·  " + finishedDate);
                todayTextView.setTextColor(getColor(R.color.menu_text_secondary));
                todayTextView.setVisibility(View.VISIBLE);
            } else if (event.repeatYearly) {
                // Yearly event
                if (eventMs > todayMs) {
                    todayTextView.setText(dateStr + "  ·  " + diffDays + " days away");
                } else {
                    long nextYearMs = eventMs + (365L * 24 * 60 * 60 * 1000);
                    long daysUntilNext = (nextYearMs - todayMs) / (1000L * 60 * 60 * 24);
                    todayTextView.setText(dateStr + "  ·  " + daysUntilNext + " days away");
                }
                todayTextView.setTextColor(getColor(R.color.event_accent));
                todayTextView.setVisibility(View.VISIBLE);
            } else {
                // Future, not yearly
                todayTextView.setText(dateStr + "  ·  " + diffDays + " days away");
                todayTextView.setTextColor(getColor(R.color.event_accent));
                todayTextView.setVisibility(View.VISIBLE);
            }
        }

        // Alert toggle button
        if (alertToggle != null) {
            alertToggle.setOnClickListener(v -> {
                event.notifyOnDay = !event.notifyOnDay;
                // Update in store
                EventStore.put(this, event);
                // Schedule or cancel
                if (event.notifyOnDay && alertsEnabled) {
                    long triggerAt = EventStore.computeTriggerAtMillis(event);
                    String dateKey = String.format("%04d-%02d-%02d", event.year, event.month + 1, event.day);
                    Reminder reminder = Reminder.create(event.title, triggerAt, dateKey, false);
                    ReminderStore.put(this, reminder);
                    ReminderScheduler.schedule(this, reminder);
                } else {
                    // Cancel any existing reminders for this event
                    java.util.List<Reminder> all = ReminderStore.getAll(this);
                    for (Reminder r : all) {
                        if (r.title.equals(event.title)) {
                            ReminderScheduler.cancel(this, r);
                            ReminderStore.delete(this, r.id);
                        }
                    }
                }
                refreshEventsList();
            });
        }

        // Delete button
        if (deleteButton != null) {
            deleteButton.setOnClickListener(v -> {
                View container = LayoutInflater.from(this).inflate(R.layout.dialog_delete_event, null, false);
                TextView message = container.findViewById(R.id.deleteEventMessage);
                TextView cancelBtn = container.findViewById(R.id.deleteEventCancel);
                TextView confirmBtn = container.findViewById(R.id.deleteEventConfirm);
                if (message == null || cancelBtn == null || confirmBtn == null) return;

                message.setText("Delete \"" + event.title + "\"?");

                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setView(container)
                        .create();
                dialog.setOnShowListener(d -> {
                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    }
                });

                confirmBtn.setOnClickListener(c -> {
                    // Cancel any reminders
                    java.util.List<Reminder> all = ReminderStore.getAll(this);
                    for (Reminder r : all) {
                        if (r.title.equals(event.title)) {
                            ReminderScheduler.cancel(this, r);
                            ReminderStore.delete(this, r.id);
                        }
                    }
                    EventStore.delete(this, event.id);
                    refreshEventsList();
                    dialog.dismiss();
                });

                cancelBtn.setOnClickListener(c -> dialog.dismiss());
                dialog.show();
            });
        }

        // Tap card to edit
        card.setOnClickListener(v -> showNewEventDialog(event));
    }

    private String encodeUriToBase64(Uri uri) {
        if (uri == null) return null;
        try {
            Bitmap bm = null;
            if ("file".equals(uri.getScheme())) {
                bm = BitmapFactory.decodeFile(uri.getPath());
            } else {
                InputStream is = getContentResolver().openInputStream(uri);
                if (is != null) {
                    bm = BitmapFactory.decodeStream(is);
                    is.close();
                }
            }
            if (bm != null) {
                // Compress to prevent OOM on large images
                int maxDim = 600;
                if (bm.getWidth() > maxDim || bm.getHeight() > maxDim) {
                    float ratio = Math.min((float) maxDim / bm.getWidth(), (float) maxDim / bm.getHeight());
                    int w = (int) (bm.getWidth() * ratio);
                    int h = (int) (bm.getHeight() * ratio);
                    bm = Bitmap.createScaledBitmap(bm, w, h, true);
                }
                // Use JPEG for smaller size
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                return android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.DEFAULT);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true; // No runtime permission needed below Android 13
        }
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void handleEnableAlerts() {
        boolean turningOn = !alertsEnabled;

        // If turning on and no permission yet, request it first
        if (turningOn && !hasNotificationPermission()) {
            requestNotificationPermissionIfNeeded();
            // After granting, the user will need to tap again
            Toast.makeText(this, "Please grant notification permission first", Toast.LENGTH_SHORT).show();
            return;
        }

        alertsEnabled = turningOn;
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
        refreshEventsList();
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
        showNewEventDialog(null);
    }

    private void showNewEventDialog(EventStore.EventItem editEvent) {
        boolean isEditing = editEvent != null;
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

        android.widget.SeekBar overlaySlider = container.findViewById(R.id.overlaySlider);
        TextView overlayValue = container.findViewById(R.id.overlayValue);
        TextView notifyTimeDisplay = container.findViewById(R.id.notifyTimeDisplay);
        TextView notifyMinuteDisplay = container.findViewById(R.id.notifyMinuteDisplay);
        TextView notifyAmPmDisplay = container.findViewById(R.id.notifyAmPmDisplay);

        if (titleInput == null || saveButton == null || closeButton == null) return;

        // Initialize date from current date or from event being edited
        java.util.Calendar now = java.util.Calendar.getInstance();
        if (isEditing) {
            eventMonth = editEvent.month;
            eventDay = editEvent.day;
            overlayOpacity = editEvent.overlayOpacity;
            notifyHour = editEvent.notifyHour;
            notifyMinute = editEvent.notifyMinute;
            repeatYearly = editEvent.repeatYearly;
            notifyOnDay = editEvent.notifyOnDay;
            titleInput.setText(editEvent.title);
            if (noteInput != null && !editEvent.note.isEmpty() && !"No note".equals(editEvent.note)) {
                noteInput.setText(editEvent.note);
            }
            selectedBackgroundUri = editEvent.backgroundUri != null ? Uri.parse(editEvent.backgroundUri) : null;
        } else {
            eventMonth = now.get(java.util.Calendar.MONTH);
            eventDay = now.get(java.util.Calendar.DAY_OF_MONTH);
            overlayOpacity = 40;
            selectedBackgroundUri = null; // Reset background for new events
        }

        if (monthSelector != null) monthSelector.setText(MONTH_NAMES[eventMonth]);
        if (daySelector != null) daySelector.setText(String.valueOf(eventDay));

        // Set dialog title
        TextView dialogTitle = container.findViewById(R.id.dialogTitle);
        if (dialogTitle != null) {
            dialogTitle.setText(isEditing ? "Edit Event" : "New Event");
        }

        // Restore previously selected background if re-opening dialog
        Bitmap previewBm = null;
        if (backgroundImage != null) {
            // Try file URI first
            if (selectedBackgroundUri != null) {
                try {
                    InputStream is = getContentResolver().openInputStream(selectedBackgroundUri);
                    previewBm = BitmapFactory.decodeStream(is);
                    if (is != null) is.close();
                } catch (Exception ignored) {}
            }
            // Fallback to base64 (for imported or base64-only events)
            if (previewBm == null && isEditing && editEvent != null) {
                String b64 = editEvent.backgroundBase64;
                if (b64 != null && !b64.isEmpty()) {
                    try {
                        byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                        previewBm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    } catch (Exception ignored) {}
                }
            }
            if (previewBm != null) {
                backgroundImage.setImageBitmap(previewBm);
                backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                backgroundImage.setColorFilter(null);
                if (uploadOverlay != null) uploadOverlay.setVisibility(View.GONE);
            }
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

        // Notification time picker (using ReminderClockView)
        if (notifyTimeDisplay != null && notifyMinuteDisplay != null) {
            updateTimeDisplay(notifyTimeDisplay, notifyMinuteDisplay, notifyAmPmDisplay);
            View.OnClickListener openClock = v -> {
                View clockContainer = LayoutInflater.from(this).inflate(R.layout.dialog_reminder, null, false);
                ReminderClockView clockView = clockContainer.findViewById(R.id.reminderClock);
                TextView hourChip = clockContainer.findViewById(R.id.reminderHourChip);
                TextView minuteChip = clockContainer.findViewById(R.id.reminderMinuteChip);
                TextView ampmChip = clockContainer.findViewById(R.id.reminderAmPmChip);
                TextView toggleClock = clockContainer.findViewById(R.id.reminderToggleClock);
                TextView toggleKeyboard = clockContainer.findViewById(R.id.reminderToggleKeyboard);
                TextView modeLabel = clockContainer.findViewById(R.id.reminderModeLabel);
                TextView pickTimeHint = clockContainer.findViewById(R.id.reminderPickTimeHint);
                LinearLayout timeRow = clockContainer.findViewById(R.id.reminderTimeRow);
                LinearLayout keyboardRow = clockContainer.findViewById(R.id.reminderKeyboardRow);
                EditText hourInput = clockContainer.findViewById(R.id.reminderHourInput);
                EditText minuteInput = clockContainer.findViewById(R.id.reminderMinuteInput);
                TextView keyboardAmPm = clockContainer.findViewById(R.id.reminderKeyboardAmPm);
                TextView cancelBtn = clockContainer.findViewById(R.id.reminderCancel);
                TextView saveBtn = clockContainer.findViewById(R.id.reminderSave);
                View repeatSection = clockContainer.findViewById(R.id.reminderRepeatRow);
                EditText titleInputRem = clockContainer.findViewById(R.id.reminderTitleInput);

                if (clockView == null || hourChip == null || minuteChip == null || ampmChip == null
                        || cancelBtn == null || saveBtn == null) return;

                // Hide reminder title + repeat (not needed for event time picker)
                if (titleInputRem != null) titleInputRem.setVisibility(View.GONE);
                View reminderTimeLabel = clockContainer.findViewById(R.id.reminderTimeLabel);
                if (reminderTimeLabel != null) reminderTimeLabel.setVisibility(View.GONE);
                if (repeatSection != null) repeatSection.setVisibility(View.GONE);
                View repeatLabel = clockContainer.findViewById(R.id.reminderRepeatLabel);
                if (repeatLabel != null) repeatLabel.setVisibility(View.GONE);
                TextView titleView = clockContainer.findViewById(R.id.reminderDialogTitle);
                if (titleView != null) titleView.setText("Pick notification time");

                boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
                final int[] chosenHour = {notifyHour};
                final int[] chosenMinute = {notifyMinute};

                clockView.setTime(chosenHour[0], chosenMinute[0], is24);
                updateTimeChips(hourChip, minuteChip, ampmChip, chosenHour[0], chosenMinute[0]);

                // Sync keyboard inputs from chosen values
                Runnable syncInputsFromChosen = () -> {
                    int displayHour = chosenHour[0];
                    if (!is24) {
                        displayHour = chosenHour[0] % 12;
                        if (displayHour == 0) displayHour = 12;
                    }
                    if (hourInput != null) hourInput.setText(String.format(java.util.Locale.US, "%02d", displayHour));
                    if (minuteInput != null) minuteInput.setText(String.format(java.util.Locale.US, "%02d", chosenMinute[0]));
                    if (!is24 && keyboardAmPm != null) {
                        keyboardAmPm.setVisibility(View.VISIBLE);
                        keyboardAmPm.setText(chosenHour[0] >= 12 ? "PM" : "AM");
                    } else if (keyboardAmPm != null) {
                        keyboardAmPm.setVisibility(View.GONE);
                    }
                };

                Runnable syncChosenFromInputs = () -> {
                    int hourVal = safeParseInt(hourInput != null ? hourInput.getText().toString().trim() : "", is24 ? chosenHour[0] : 12);
                    int minuteVal = safeParseInt(minuteInput != null ? minuteInput.getText().toString().trim() : "", chosenMinute[0]);
                    minuteVal = Math.max(0, Math.min(59, minuteVal));
                    if (is24) {
                        hourVal = Math.max(0, Math.min(23, hourVal));
                        chosenHour[0] = hourVal;
                    } else {
                        hourVal = Math.max(1, Math.min(12, hourVal));
                        boolean pm = "PM".equalsIgnoreCase(keyboardAmPm != null ? keyboardAmPm.getText().toString() : "AM");
                        int hour24 = (hourVal % 12) + (pm ? 12 : 0);
                        if (hour24 == 24) hour24 = 0;
                        chosenHour[0] = hour24;
                    }
                    chosenMinute[0] = minuteVal;
                    updateTimeChips(hourChip, minuteChip, ampmChip, chosenHour[0], chosenMinute[0]);
                    clockView.setTime(chosenHour[0], chosenMinute[0], is24);
                };

                clockView.setOnTimeChangeListener((h, m, mode) -> {
                    chosenHour[0] = h;
                    chosenMinute[0] = m;
                    updateTimeChips(hourChip, minuteChip, ampmChip, h, m);
                    syncInputsFromChosen.run();
                    if (modeLabel != null)
                        modeLabel.setText(mode == ReminderClockView.Mode.HOUR ? "Select Hour" : "Select Minute");
                });

                // Toggle clock/keyboard modes
                if (toggleClock != null) {
                    toggleClock.setOnClickListener(ev -> {
                        toggleClock.setBackgroundResource(R.drawable.bg_reminder_time_chip_selected);
                        if (toggleKeyboard != null) toggleKeyboard.setBackgroundResource(R.drawable.bg_reminder_time_chip);
                        if (timeRow != null) timeRow.setVisibility(View.VISIBLE);
                        if (clockView != null) clockView.setVisibility(View.VISIBLE);
                        if (pickTimeHint != null) pickTimeHint.setVisibility(View.VISIBLE);
                        if (modeLabel != null) modeLabel.setVisibility(View.VISIBLE);
                        if (keyboardRow != null) keyboardRow.setVisibility(View.GONE);
                    });
                }
                if (toggleKeyboard != null) {
                    toggleKeyboard.setOnClickListener(ev -> {
                        toggleKeyboard.setBackgroundResource(R.drawable.bg_reminder_time_chip_selected);
                        if (toggleClock != null) toggleClock.setBackgroundResource(R.drawable.bg_reminder_time_chip);
                        if (timeRow != null) timeRow.setVisibility(View.GONE);
                        if (clockView != null) clockView.setVisibility(View.GONE);
                        if (pickTimeHint != null) pickTimeHint.setVisibility(View.GONE);
                        if (modeLabel != null) modeLabel.setVisibility(View.GONE);
                        if (keyboardRow != null) keyboardRow.setVisibility(View.VISIBLE);
                        syncInputsFromChosen.run();
                    });
                }

                if (timeRow != null) {
                    timeRow.setOnClickListener(ev -> {
                        if (keyboardRow != null && keyboardRow.getVisibility() == View.VISIBLE) {
                            syncChosenFromInputs.run();
                            return;
                        }
                        ReminderClockView.Mode curMode = clockView.getMode();
                        ReminderClockView.Mode next = curMode == ReminderClockView.Mode.HOUR
                                ? ReminderClockView.Mode.MINUTE : ReminderClockView.Mode.HOUR;
                        clockView.setMode(next);
                        if (modeLabel != null)
                            modeLabel.setText(next == ReminderClockView.Mode.HOUR ? "Select Hour" : "Select Minute");
                    });
                }

                hourChip.setOnClickListener(ev -> clockView.setMode(ReminderClockView.Mode.HOUR));
                minuteChip.setOnClickListener(ev -> clockView.setMode(ReminderClockView.Mode.MINUTE));
                if (!is24 && ampmChip != null) {
                    ampmChip.setOnClickListener(ev -> {
                        if (chosenHour[0] >= 12) chosenHour[0] -= 12;
                        else chosenHour[0] += 12;
                        clockView.setTime(chosenHour[0], chosenMinute[0], false);
                        updateTimeChips(hourChip, minuteChip, ampmChip, chosenHour[0], chosenMinute[0]);
                        syncInputsFromChosen.run();
                    });
                }

                if (keyboardAmPm != null) {
                    keyboardAmPm.setOnClickListener(ev -> {
                        if (is24) return;
                        keyboardAmPm.setText("AM".equalsIgnoreCase(keyboardAmPm.getText().toString()) ? "PM" : "AM");
                        syncChosenFromInputs.run();
                    });
                }
                if (hourInput != null) hourInput.setOnFocusChangeListener((ev, hasFocus) -> { if (!hasFocus) syncChosenFromInputs.run(); });
                if (minuteInput != null) minuteInput.setOnFocusChangeListener((ev, hasFocus) -> { if (!hasFocus) syncChosenFromInputs.run(); });

                syncInputsFromChosen.run();

                AlertDialog clockDialog = new AlertDialog.Builder(this)
                        .setView(clockContainer)
                        .create();
                clockDialog.setOnShowListener(d -> {
                    if (clockDialog.getWindow() != null) {
                        clockDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                        clockDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    }
                });

                cancelBtn.setOnClickListener(ev -> clockDialog.dismiss());
                saveBtn.setOnClickListener(ev -> {
                    // Sync keyboard inputs before saving (in case user typed directly)
                    if (keyboardRow != null && keyboardRow.getVisibility() == View.VISIBLE) {
                        syncChosenFromInputs.run();
                    }
                    notifyHour = chosenHour[0];
                    notifyMinute = chosenMinute[0];
                    updateTimeDisplay(notifyTimeDisplay, notifyMinuteDisplay, notifyAmPmDisplay);
                    clockDialog.dismiss();
                });

                clockDialog.show();
            };
            notifyTimeDisplay.setOnClickListener(openClock);
            notifyMinuteDisplay.setOnClickListener(openClock);
            if (notifyAmPmDisplay != null) notifyAmPmDisplay.setOnClickListener(openClock);
        }

        closeButton.setOnClickListener(v -> dialog.dismiss());

        // Month selector
        if (monthSelector != null) {
            monthSelector.setOnClickListener(v -> {
                showItemPickerDialog("Select Month", MONTH_NAMES, eventMonth, (which) -> {
                    eventMonth = which;
                    monthSelector.setText(MONTH_NAMES[which]);
                });
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
                showItemPickerDialog("Select Day", days, eventDay - 1, (which) -> {
                    eventDay = which + 1;
                    daySelector.setText(String.valueOf(eventDay));
                });
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

        // Overlay opacity slider
        if (overlaySlider != null && overlayValue != null) {
            overlaySlider.setProgress(overlayOpacity);
            overlayValue.setText(overlayOpacity + "%");
            // Sync the scrim preview to the initial value
            View scrim = container.findViewById(R.id.scrimPreview);
            if (scrim != null) {
                scrim.setAlpha(Math.max(0f, Math.min(1f, overlayOpacity / 100f)));
            }
            overlaySlider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                    overlayOpacity = progress;
                    overlayValue.setText(progress + "%");
                    // Live preview on the scrim overlay in dialog
                    View scrim = container.findViewById(R.id.scrimPreview);
                    if (scrim != null) {
                        scrim.setAlpha(Math.max(0f, Math.min(1f, progress / 100f)));
                    }
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
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
            int savedOpacity = overlaySlider != null ? overlaySlider.getProgress() : overlayOpacity;
            String eventId = isEditing ? editEvent.id : java.util.UUID.randomUUID().toString();
            String bgUriStr = selectedBackgroundUri != null ? selectedBackgroundUri.toString() : null;
            String bgBase64 = encodeUriToBase64(selectedBackgroundUri);
            EventStore.EventItem event = new EventStore.EventItem(
                    eventId,
                    title,
                    note,
                    currentYear,
                    eventMonth,
                    eventDay,
                    repeatYearly,
                    notifyOnDay,
                    notifyHour,
                    notifyMinute,
                    bgUriStr,
                    bgBase64,
                    savedOpacity,
                    System.currentTimeMillis()
            );
            EventStore.put(this, event);

            // Schedule notification only if both master toggle AND event toggle are ON
            if (notifyOnDay && alertsEnabled) {
                // Request notification permission if not granted yet
                if (!hasNotificationPermission()) {
                    requestNotificationPermissionIfNeeded();
                }
                long triggerAt = EventStore.computeTriggerAtMillis(event);
                String dateKey = String.format("%04d-%02d-%02d", event.year, event.month + 1, event.day);

                Reminder reminder = Reminder.create(title, triggerAt, dateKey, false);
                ReminderStore.put(this, reminder);
                ReminderScheduler.schedule(this, reminder);
            }

            String msg = "Event saved" + (notifyOnDay && !alertsEnabled ? " (alerts are off)" : "");
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            refreshEventsList();
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

    private void updateTimeDisplay(TextView hourDisplay, TextView minuteDisplay, TextView ampmDisplay) {
        if (hourDisplay == null || minuteDisplay == null) return;
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        int displayHour = notifyHour;
        String amPm = "AM";
        if (!is24) {
            if (notifyHour >= 12) amPm = "PM";
            displayHour = notifyHour % 12;
            if (displayHour == 0) displayHour = 12;
        }
        hourDisplay.setText(String.format(java.util.Locale.US, "%02d", displayHour));
        minuteDisplay.setText(String.format(java.util.Locale.US, "%02d", notifyMinute));
        if (ampmDisplay != null) {
            if (is24) ampmDisplay.setVisibility(View.GONE);
            else {
                ampmDisplay.setVisibility(View.VISIBLE);
                ampmDisplay.setText(amPm);
            }
        }
    }

    private void updateTimeChips(TextView hourChip, TextView minuteChip, TextView ampmChip, int hour24, int minute) {
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        int displayHour = hour24;
        String ampm = "AM";
        if (!is24) {
            if (hour24 >= 12) ampm = "PM";
            displayHour = hour24 % 12;
            if (displayHour == 0) displayHour = 12;
        }
        if (hourChip != null) hourChip.setText(String.format(java.util.Locale.US, "%02d", displayHour));
        if (minuteChip != null) minuteChip.setText(String.format(java.util.Locale.US, "%02d", minute));
        if (ampmChip != null) {
            if (is24) {
                ampmChip.setVisibility(View.GONE);
            } else {
                ampmChip.setVisibility(View.VISIBLE);
                ampmChip.setText(ampm);
            }
        }
    }

    private void showItemPickerDialog(String title, String[] items, int selectedIndex,
                                      java.util.function.IntConsumer onSelect) {
        View container = LayoutInflater.from(this).inflate(R.layout.dialog_select_item, null, false);
        TextView titleView = container.findViewById(R.id.selectDialogTitle);
        LinearLayout itemsContainer = container.findViewById(R.id.selectDialogItems);
        TextView cancelView = container.findViewById(R.id.selectDialogCancel);
        if (titleView == null || itemsContainer == null || cancelView == null) return;

        titleView.setText(title);
        itemsContainer.removeAllViews();

        // Cap scroll area so cancel button stays visible
        ScrollView scrollView = container.findViewById(R.id.selectScrollView);
        if (scrollView != null) {
            int itemHeightPx = dpToPx(50); // ~50dp per item row
            int maxVisibleItems = Math.min(items.length, 6);
            scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    itemHeightPx * maxVisibleItems));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        cancelView.setOnClickListener(v -> dialog.dismiss());

        for (int i = 0; i < items.length; i++) {
            final int index = i;
            TextView item = new TextView(this);
            item.setText(items[i]);
            item.setTextSize(15f);
            item.setPadding(0, dpToPx(14), 0, dpToPx(14));
            item.setClickable(true);
            item.setFocusable(true);

            if (i == selectedIndex) {
                item.setTextColor(getColor(R.color.event_accent));
                item.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                item.setTextColor(getColor(R.color.menu_text_primary));
            }

            item.setOnClickListener(v -> {
                onSelect.accept(index);
                dialog.dismiss();
            });
            itemsContainer.addView(item);

            // Divider
            if (i < items.length - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(getColor(R.color.menu_text_secondary));
                divider.setAlpha(0.12f);
                itemsContainer.addView(divider);
            }
        }

        dialog.show();
    }

    private int safeParseInt(String text, int fallback) {
        if (text == null || text.isEmpty()) return fallback;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
