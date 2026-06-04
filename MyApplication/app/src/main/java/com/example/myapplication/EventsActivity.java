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
    private int overlayOpacity = 40; // 0-100
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
            newButton.setOnClickListener(v -> showNewEventDialog());
        }

        // + Add First Event
        addFirstEventButton = findViewById(R.id.addFirstEventButton);
        if (addFirstEventButton != null) {
            addFirstEventButton.setOnClickListener(v -> showNewEventDialog());
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
            java.io.File outFile = new java.io.File(getCacheDir(), fileName);
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

        // Load background image
        if (cardImage != null && event.backgroundUri != null && !event.backgroundUri.isEmpty()) {
            try {
                Uri uri = Uri.parse(event.backgroundUri);
                Bitmap bm = null;
                if ("file".equals(uri.getScheme())) {
                    bm = BitmapFactory.decodeFile(uri.getPath());
                } else {
                    InputStream is = getContentResolver().openInputStream(uri);
                    bm = BitmapFactory.decodeStream(is);
                    if (is != null) is.close();
                }
                if (bm != null) {
                    cardImage.setImageBitmap(bm);
                    cardImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    cardImage.setColorFilter(null);
                }
            } catch (Exception ignored) {
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

        // Yearly chip
        if (yearlyChip != null) {
            yearlyChip.setVisibility(event.repeatYearly ? View.VISIBLE : View.GONE);
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
                new AlertDialog.Builder(this)
                        .setTitle("Delete Event")
                        .setMessage("Delete \"" + event.title + "\"?")
                        .setPositiveButton("Delete", (d, w) -> {
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
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        // Tap card to edit
        card.setOnClickListener(v -> showNewEventDialog(event));
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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

        if (titleInput == null || saveButton == null || closeButton == null) return;

        // Initialize date from current date or from event being edited
        java.util.Calendar now = java.util.Calendar.getInstance();
        if (isEditing) {
            eventMonth = editEvent.month;
            eventDay = editEvent.day;
            overlayOpacity = editEvent.overlayOpacity;
            repeatYearly = editEvent.repeatYearly;
            notifyOnDay = editEvent.notifyOnDay;
            titleInput.setText(editEvent.title);
            if (noteInput != null && !editEvent.note.isEmpty() && !"No note".equals(editEvent.note)) {
                noteInput.setText(editEvent.note);
            }
            if (editEvent.backgroundUri != null) {
                selectedBackgroundUri = Uri.parse(editEvent.backgroundUri);
            }
        } else {
            eventMonth = now.get(java.util.Calendar.MONTH);
            eventDay = now.get(java.util.Calendar.DAY_OF_MONTH);
            overlayOpacity = 40;
        }

        if (monthSelector != null) monthSelector.setText(MONTH_NAMES[eventMonth]);
        if (daySelector != null) daySelector.setText(String.valueOf(eventDay));

        // Set dialog title
        TextView dialogTitle = container.findViewById(R.id.dialogTitle);
        if (dialogTitle != null) {
            dialogTitle.setText(isEditing ? "Edit Event" : "New Event");
        }

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
            EventStore.EventItem event = new EventStore.EventItem(
                    eventId,
                    title,
                    note,
                    currentYear,
                    eventMonth,
                    eventDay,
                    repeatYearly,
                    notifyOnDay,
                    selectedBackgroundUri != null ? selectedBackgroundUri.toString() : null,
                    savedOpacity,
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
}
