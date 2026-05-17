package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.text.format.DateFormat;
import android.widget.TimePicker;
import android.widget.Toast;
import android.os.Build;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AlertDialog;

import java.util.Calendar;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.LinkedHashMap;

public class CalendarActivity extends AppCompatActivity {

    private RecyclerView recyclerDays;
    private TextView monthText;
    private TextView yearText;
    private Calendar currentCalendar;

    private int selectedYear;
    private int selectedMonth0;
    private int selectedDay;

    private static final String MOOD_MARKER = "[MOOD_MARKER]";
    private static final String WALLPAPER_MARKER = "[WALLPAPER_MARKER]";

    // Mood stats views
    private TextView moodStatsMonthText;
    private TextView moodLoggedBadge;
    private TextView moodFrequencyEmptyText;
    private LinearLayout moodFrequencyList;
    private MoodDonutView moodDonut;
    private TextView moodCountGood;
    private TextView moodPercentGood;
    private TextView moodCountNormal;
    private TextView moodPercentNormal;
    private TextView moodCountBad;
    private TextView moodPercentBad;
    private TextView moodBreakdownTotal;
    private LinearLayout moodAllMoodsRow;

    private TextView tabStats;
    private TextView tabReminders;
    private LinearLayout statsContainer;
    private LinearLayout remindersContainer;
    private LinearLayout remindersList;
    private TextView remindersEmptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StoryStore.migrateIfNeeded(this);

        setContentView(R.layout.activity_calendar);

        View root = findViewById(android.R.id.content);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                return insets;
            });
            ViewCompat.requestApplyInsets(root);
        }

        recyclerDays = findViewById(R.id.recyclerDays);
        monthText = findViewById(R.id.monthText);
        yearText = findViewById(R.id.yearText);

        moodStatsMonthText = findViewById(R.id.moodStatsMonthText);
        moodLoggedBadge = findViewById(R.id.moodLoggedBadge);
        moodFrequencyEmptyText = findViewById(R.id.moodFrequencyEmptyText);
        moodFrequencyList = findViewById(R.id.moodFrequencyList);
        moodDonut = findViewById(R.id.moodDonut);
        moodCountGood = findViewById(R.id.moodCountGood);
        moodPercentGood = findViewById(R.id.moodPercentGood);
        moodCountNormal = findViewById(R.id.moodCountNormal);
        moodPercentNormal = findViewById(R.id.moodPercentNormal);
        moodCountBad = findViewById(R.id.moodCountBad);
        moodPercentBad = findViewById(R.id.moodPercentBad);
        moodBreakdownTotal = findViewById(R.id.moodBreakdownTotal);
        moodAllMoodsRow = findViewById(R.id.moodAllMoodsRow);

        tabStats = findViewById(R.id.tabStats);
        tabReminders = findViewById(R.id.tabReminders);
        statsContainer = findViewById(R.id.statsContainer);
        remindersContainer = findViewById(R.id.remindersContainer);
        remindersList = findViewById(R.id.remindersList);
        remindersEmptyText = findViewById(R.id.remindersEmptyText);

        ImageButton backButton = findViewById(R.id.backButton);
        ImageButton prevButton = findViewById(R.id.prevButton);
        ImageButton nextButton = findViewById(R.id.nextButton);
        LinearLayout todayButton = findViewById(R.id.todayButton);

        currentCalendar = Calendar.getInstance();
        Calendar today = Calendar.getInstance();
        selectedYear = today.get(Calendar.YEAR);
        selectedMonth0 = today.get(Calendar.MONTH);
        selectedDay = today.get(Calendar.DAY_OF_MONTH);

        recyclerDays.setLayoutManager(new GridLayoutManager(this, 7));

        backButton.setOnClickListener(v -> finish());
        prevButton.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            updateCalendar();
        });
        nextButton.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            updateCalendar();
        });
        todayButton.setOnClickListener(v -> {
            Calendar t = Calendar.getInstance();
            selectedYear = t.get(Calendar.YEAR);
            selectedMonth0 = t.get(Calendar.MONTH);
            selectedDay = t.get(Calendar.DAY_OF_MONTH);
            currentCalendar.set(Calendar.YEAR, selectedYear);
            currentCalendar.set(Calendar.MONTH, selectedMonth0);
            updateCalendar();

            openStoryEditor(selectedYear, selectedMonth0, selectedDay);
        });

        if (tabStats != null) {
            tabStats.setOnClickListener(v -> selectTab(false));
        }
        if (tabReminders != null) {
            tabReminders.setOnClickListener(v -> selectTab(true));
        }

        selectTab(false);

        // Defer heavy loading to the next frame to reduce navigation lag.
        if (recyclerDays != null) {
            recyclerDays.post(this::updateCalendar);
        } else {
            updateCalendar();
        }
    }

    private void updateCalendar() {
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};

        int month0 = currentCalendar.get(Calendar.MONTH);
        int year = currentCalendar.get(Calendar.YEAR);

        monthText.setText(months[month0]);
        yearText.setText(String.valueOf(year));

        ReminderStore.cleanupExpired(this);
        HashSet<String> reminderKeys = ReminderStore.getReminderDateKeys(this);

        List<DayCell> cells = buildCells(year, month0, reminderKeys);
        recyclerDays.setAdapter(new DaysAdapter(cells, cell -> {
            selectedYear = cell.year;
            selectedMonth0 = cell.month0;
            selectedDay = cell.day;

            if (cell.month0 != currentCalendar.get(Calendar.MONTH) || cell.year != currentCalendar.get(Calendar.YEAR)) {
                currentCalendar.set(Calendar.YEAR, cell.year);
                currentCalendar.set(Calendar.MONTH, cell.month0);
                updateCalendar();
            }

            openStoryEditor(cell.year, cell.month0, cell.day);
        }));

        updateMoodStats(year, month0);
        updateRemindersList(year, month0);
    }

    private void selectTab(boolean showReminders) {
        if (tabStats != null) tabStats.setSelected(!showReminders);
        if (tabReminders != null) tabReminders.setSelected(showReminders);
        if (statsContainer != null) statsContainer.setVisibility(showReminders ? View.GONE : View.VISIBLE);
        if (remindersContainer != null) remindersContainer.setVisibility(showReminders ? View.VISIBLE : View.GONE);

        if (showReminders) {
            int month0 = currentCalendar.get(Calendar.MONTH);
            int year = currentCalendar.get(Calendar.YEAR);
            updateRemindersList(year, month0);
        }
    }

    private void updateRemindersList(int year, int month0) {
        if (remindersList == null) return;

        remindersList.removeAllViews();
        ReminderStore.cleanupExpired(this);
        List<Reminder> all = ReminderStore.getAll(this);
        long now = System.currentTimeMillis();
        Calendar startOfMonth = Calendar.getInstance();
        startOfMonth.set(Calendar.YEAR, year);
        startOfMonth.set(Calendar.MONTH, month0);
        startOfMonth.set(Calendar.DAY_OF_MONTH, 1);
        startOfMonth.set(Calendar.HOUR_OF_DAY, 0);
        startOfMonth.set(Calendar.MINUTE, 0);
        startOfMonth.set(Calendar.SECOND, 0);
        startOfMonth.set(Calendar.MILLISECOND, 0);

        Calendar endOfMonth = (Calendar) startOfMonth.clone();
        endOfMonth.add(Calendar.MONTH, 1);
        endOfMonth.add(Calendar.MILLISECOND, -1);

        Map<String, List<Reminder>> grouped = new LinkedHashMap<>();
        for (Reminder r : all) {
            if (r == null) continue;
            if (r.triggerAtMillis < now) continue;
            if (r.triggerAtMillis < startOfMonth.getTimeInMillis()) continue;
            if (r.triggerAtMillis > endOfMonth.getTimeInMillis()) continue;
            String key = r.dateKey;
            if (key == null) continue;
            if (!grouped.containsKey(key)) grouped.put(key, new ArrayList<>());
            grouped.get(key).add(r);
        }

        List<String> dateKeys = new ArrayList<>(grouped.keySet());
        Collections.sort(dateKeys);

        if (remindersEmptyText != null) {
            remindersEmptyText.setVisibility(dateKeys.isEmpty() ? View.VISIBLE : View.GONE);
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.US);
        SimpleDateFormat dateFmt = new SimpleDateFormat("MMM d, yyyy", Locale.US);

        for (String dateKey : dateKeys) {
            List<Reminder> list = grouped.get(dateKey);
            if (list == null || list.isEmpty()) continue;
            Collections.sort(list, Comparator.comparingLong(a -> a.triggerAtMillis));

            View section = inflater.inflate(R.layout.item_reminder_date_section, remindersList, false);
            TextView headerText = section.findViewById(R.id.reminderDateText);
            TextView headerCount = section.findViewById(R.id.reminderDateCount);
            LinearLayout dateList = section.findViewById(R.id.reminderDateList);
            View header = section.findViewById(R.id.reminderDateHeader);

            if (headerText != null) {
                Date d = parseDateKey(dateKey);
                headerText.setText(d != null ? dateFmt.format(d) : dateKey);
            }
            if (headerCount != null) {
                headerCount.setText(String.valueOf(list.size()));
            }

            if (dateList != null) {
                dateList.removeAllViews();
                for (Reminder r : list) {
                    View row = inflater.inflate(R.layout.item_reminder_row, dateList, false);
                    TextView title = row.findViewById(R.id.reminderTitle);
                    TextView time = row.findViewById(R.id.reminderTime);
                    TextView edit = row.findViewById(R.id.reminderEdit);
                    TextView del = row.findViewById(R.id.reminderDelete);

                    if (title != null) title.setText(r.title);
                    if (time != null) {
                        String t = timeFmt.format(new Date(r.triggerAtMillis));
                        if (r.repeatDaily) t = t + " • Daily";
                        time.setText(t);
                    }

                    if (edit != null) {
                        edit.setOnClickListener(v -> showEditReminderDialog(r));
                    }
                    if (del != null) {
                        del.setOnClickListener(v -> {
                            ReminderScheduler.cancel(this, r);
                            ReminderStore.delete(this, r.id);
                            updateRemindersList(year, month0);
                            updateCalendar();
                        });
                    }

                    dateList.addView(row);
                }
            }

            if (header != null && dateList != null) {
                header.setOnClickListener(v -> {
                    int vis = dateList.getVisibility();
                    dateList.setVisibility(vis == View.VISIBLE ? View.GONE : View.VISIBLE);
                });
            }

            remindersList.addView(section);
        }
    }

    private void showEditReminderDialog(Reminder reminder) {
        if (reminder == null) return;

        View container = LayoutInflater.from(this).inflate(R.layout.dialog_reminder, null, false);
        EditText titleInput = container.findViewById(R.id.reminderTitleInput);
        TextView hourChip = container.findViewById(R.id.reminderHourChip);
        TextView minuteChip = container.findViewById(R.id.reminderMinuteChip);
        TextView ampmChip = container.findViewById(R.id.reminderAmPmChip);
        View timeRow = container.findViewById(R.id.reminderTimeRow);
        TimePicker timePicker = container.findViewById(R.id.reminderTimePicker);
        TextView repeatOnce = container.findViewById(R.id.reminderRepeatOnce);
        TextView repeatDaily = container.findViewById(R.id.reminderRepeatDaily);
        TextView cancelBtn = container.findViewById(R.id.reminderCancel);
        TextView saveBtn = container.findViewById(R.id.reminderSave);
        if (titleInput == null || hourChip == null || minuteChip == null || ampmChip == null
            || timeRow == null || timePicker == null || repeatOnce == null || repeatDaily == null
            || cancelBtn == null || saveBtn == null) {
            return;
        }

        titleInput.setText(reminder.title);
        Calendar current = Calendar.getInstance();
        current.setTimeInMillis(reminder.triggerAtMillis);
        final int[] chosenHour = {current.get(Calendar.HOUR_OF_DAY)};
        final int[] chosenMinute = {current.get(Calendar.MINUTE)};

        timePicker.setIs24HourView(DateFormat.is24HourFormat(this));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePicker.setHour(chosenHour[0]);
            timePicker.setMinute(chosenMinute[0]);
        } else {
            timePicker.setCurrentHour(chosenHour[0]);
            timePicker.setCurrentMinute(chosenMinute[0]);
        }

        updateTimeChips(hourChip, minuteChip, ampmChip, chosenHour[0], chosenMinute[0]);

        repeatOnce.setSelected(!reminder.repeatDaily);
        repeatDaily.setSelected(reminder.repeatDaily);

        repeatOnce.setOnClickListener(v -> {
            repeatOnce.setSelected(true);
            repeatDaily.setSelected(false);
        });
        repeatDaily.setOnClickListener(v -> {
            repeatDaily.setSelected(true);
            repeatOnce.setSelected(false);
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();

        timePicker.setOnTimeChangedListener((view, hour, minute) -> {
            chosenHour[0] = hour;
            chosenMinute[0] = minute;
            updateTimeChips(hourChip, minuteChip, ampmChip, hour, minute);
        });

        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        saveBtn.setOnClickListener(v -> {
            Calendar when = Calendar.getInstance();
            String[] parts = reminder.dateKey.split("-");
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]) - 1;
            int day = Integer.parseInt(parts[2]);
            when.set(Calendar.YEAR, y);
            when.set(Calendar.MONTH, m);
            when.set(Calendar.DAY_OF_MONTH, day);
            when.set(Calendar.HOUR_OF_DAY, chosenHour[0]);
            when.set(Calendar.MINUTE, chosenMinute[0]);
            when.set(Calendar.SECOND, 0);
            when.set(Calendar.MILLISECOND, 0);

            if (when.getTimeInMillis() <= System.currentTimeMillis()) {
                Toast.makeText(this, "Pick a future time", Toast.LENGTH_SHORT).show();
                return;
            }

            Reminder updated = Reminder.update(
                    reminder.id,
                    titleInput.getText() != null ? titleInput.getText().toString() : "Reminder",
                    when.getTimeInMillis(),
                    reminder.dateKey,
                    repeatDaily.isSelected()
            );

            ReminderScheduler.cancel(this, reminder);
            ReminderStore.put(this, updated);
            ReminderScheduler.schedule(this, updated);
            updateRemindersList(currentCalendar.get(Calendar.YEAR), currentCalendar.get(Calendar.MONTH));
            updateCalendar();
            dialog.dismiss();
        });

        dialog.show();
    }

    private Date parseDateKey(String dateKey) {
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
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTime();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void updateTimeChips(TextView hourChip, TextView minuteChip, TextView ampmChip, int hour24, int minute) {
        boolean is24 = DateFormat.is24HourFormat(this);
        int displayHour = hour24;
        String ampm = "AM";
        if (!is24) {
            if (hour24 >= 12) ampm = "PM";
            displayHour = hour24 % 12;
            if (displayHour == 0) displayHour = 12;
        }
        if (hourChip != null) hourChip.setText(String.format(Locale.US, "%02d", displayHour));
        if (minuteChip != null) minuteChip.setText(String.format(Locale.US, "%02d", minute));
        if (ampmChip != null) {
            if (is24) {
                ampmChip.setVisibility(View.GONE);
            } else {
                ampmChip.setVisibility(View.VISIBLE);
                ampmChip.setText(ampm);
            }
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void updateMoodStats(int year, int month0) {
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};

        if (moodStatsMonthText != null) {
            moodStatsMonthText.setText(months[month0] + " " + year);
        }

        SharedPreferences sharedPref = StoryStore.get(this);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month0);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        Map<String, Integer> moodCounts = new HashMap<>();
        int good = 0;
        int normal = 0;
        int bad = 0;
        int totalLogged = 0;

        for (int day = 1; day <= daysInMonth; day++) {
            String dateKey = year + "-" + String.format("%02d", month0 + 1) + "-" + String.format("%02d", day);
            String storyData = sharedPref.getString(dateKey, null);
            if (storyData == null || storyData.isEmpty()) continue;

            String moodId = extractMoodId(storyData);
            if (moodId == null || moodId.trim().isEmpty()) continue;

            Mood mood = Mood.findById(moodId.trim());
            if (mood == null) continue;

            totalLogged++;
            moodCounts.put(mood.id, (moodCounts.containsKey(mood.id) ? moodCounts.get(mood.id) : 0) + 1);

            if (mood.category == Mood.CATEGORY_GOOD) good++;
            else if (mood.category == Mood.CATEGORY_NORMAL) normal++;
            else bad++;
        }

        if (moodLoggedBadge != null) {
            moodLoggedBadge.setText(totalLogged + " logged");
        }

        // Frequency list
        if (moodFrequencyList != null) {
            moodFrequencyList.removeAllViews();

            List<Mood> moods = new ArrayList<>(Mood.all());
            // Keep only moods with count > 0
            List<Mood> present = new ArrayList<>();
            for (Mood mood : moods) {
                Integer c = moodCounts.get(mood.id);
                if (c != null && c > 0) present.add(mood);
            }

            Collections.sort(present, (a, b) -> {
                int ca = moodCounts.get(a.id);
                int cb = moodCounts.get(b.id);
                int diff = cb - ca;
                if (diff != 0) return diff;
                return a.label.compareToIgnoreCase(b.label);
            });

            int max = 0;
            for (Mood mood : present) {
                int c = moodCounts.get(mood.id);
                if (c > max) max = c;
            }

            if (moodFrequencyEmptyText != null) {
                moodFrequencyEmptyText.setVisibility(present.isEmpty() ? View.VISIBLE : View.GONE);
            }

            android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
            for (Mood mood : present) {
                View row = inflater.inflate(R.layout.item_mood_frequency_row, moodFrequencyList, false);
                TextView emoji = row.findViewById(R.id.freqEmoji);
                TextView label = row.findViewById(R.id.freqLabel);
                TextView count = row.findViewById(R.id.freqCount);
                View fill = row.findViewById(R.id.freqBarFill);

                int c = moodCounts.get(mood.id);
                if (emoji != null) emoji.setText(mood.emoji);
                if (label != null) label.setText(mood.label);
                if (count != null) count.setText(String.valueOf(c));

                float pct = (max > 0) ? (c / (float) max) : 0f;
                if (fill != null && fill.getLayoutParams() instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) fill.getLayoutParams();
                    lp.matchConstraintPercentWidth = Math.max(0f, Math.min(1f, pct));
                    fill.setLayoutParams(lp);
                }

                moodFrequencyList.addView(row);
            }
        }

        // Breakdown
        int total = good + normal + bad;
        if (moodDonut != null) {
            moodDonut.setData(good, normal, bad);
        }
        setCountAndPercent(moodCountGood, moodPercentGood, good, total);
        setCountAndPercent(moodCountNormal, moodPercentNormal, normal, total);
        setCountAndPercent(moodCountBad, moodPercentBad, bad, total);
        if (moodBreakdownTotal != null) {
            moodBreakdownTotal.setText("Total: " + total + " entr" + (total == 1 ? "y" : "ies"));
        }

        // All moods row
        if (moodAllMoodsRow != null) {
            moodAllMoodsRow.removeAllViews();
            android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
            for (Mood mood : Mood.all()) {
                View cell = inflater.inflate(R.layout.item_mood_month_item, moodAllMoodsRow, false);
                TextView emoji = cell.findViewById(R.id.monthMoodEmoji);
                TextView count = cell.findViewById(R.id.monthMoodCount);
                int c = moodCounts.containsKey(mood.id) ? moodCounts.get(mood.id) : 0;

                if (emoji != null) emoji.setText(mood.emoji);
                if (count != null) count.setText(String.valueOf(c));

                float alpha = c > 0 ? 1f : 0.25f;
                cell.setAlpha(alpha);

                moodAllMoodsRow.addView(cell);
            }
        }
    }

    private void setCountAndPercent(TextView countView, TextView percentView, int count, int total) {
        if (countView != null) countView.setText(String.valueOf(count));
        int pct = 0;
        if (total > 0) {
            pct = Math.round((count * 100f) / (float) total);
        }
        if (percentView != null) percentView.setText(pct + "%");
    }

    private String extractMoodId(String storyData) {
        if (storyData == null) return null;
        int moodIndex = storyData.indexOf(MOOD_MARKER);
        if (moodIndex < 0) return null;

        int start = moodIndex + MOOD_MARKER.length();
        int end = storyData.length();
        int wpIndex = storyData.indexOf(WALLPAPER_MARKER, start);
        if (wpIndex >= 0) end = Math.min(end, wpIndex);
        String moodId = storyData.substring(start, end);
        return moodId != null ? moodId.trim() : null;
    }

    private void openStoryEditor(int year, int month0, int day) {
        Intent intent = new Intent(CalendarActivity.this, StoryDetailActivity.class);
        intent.putExtra("day", day);
        intent.putExtra("month", month0);
        intent.putExtra("year", year);
        startActivityForResult(intent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            // Refresh calendar when returning from StoryDetailActivity
            updateCalendar();
        }
    }

    private List<DayCell> buildCells(int year, int month0, HashSet<String> reminderKeys) {
        Calendar firstOfMonth = Calendar.getInstance();
        firstOfMonth.set(Calendar.YEAR, year);
        firstOfMonth.set(Calendar.MONTH, month0);
        firstOfMonth.set(Calendar.DAY_OF_MONTH, 1);
        firstOfMonth.set(Calendar.HOUR_OF_DAY, 0);
        firstOfMonth.set(Calendar.MINUTE, 0);
        firstOfMonth.set(Calendar.SECOND, 0);
        firstOfMonth.set(Calendar.MILLISECOND, 0);

        int offset = firstOfMonth.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        if (offset < 0) offset += 7;

        Calendar prevMonth = (Calendar) firstOfMonth.clone();
        prevMonth.add(Calendar.MONTH, -1);
        int daysInPrevMonth = prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH);

        int daysInMonth = firstOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH);

        SharedPreferences sharedPref = StoryStore.get(this);
        Calendar today = Calendar.getInstance();
        int todayY = today.get(Calendar.YEAR);
        int todayM = today.get(Calendar.MONTH);
        int todayD = today.get(Calendar.DAY_OF_MONTH);

        List<DayCell> cells = new ArrayList<>(42);

        for (int i = 0; i < offset; i++) {
            int day = (daysInPrevMonth - offset + 1) + i;
            int cellMonth0 = prevMonth.get(Calendar.MONTH);
            int cellYear = prevMonth.get(Calendar.YEAR);
            cells.add(makeCell(sharedPref, reminderKeys, todayY, todayM, todayD, cellYear, cellMonth0, day, false));
        }

        for (int day = 1; day <= daysInMonth; day++) {
            cells.add(makeCell(sharedPref, reminderKeys, todayY, todayM, todayD, year, month0, day, true));
        }

        Calendar nextMonth = (Calendar) firstOfMonth.clone();
        nextMonth.add(Calendar.MONTH, 1);
        int nextMonth0 = nextMonth.get(Calendar.MONTH);
        int nextYear = nextMonth.get(Calendar.YEAR);

        int day = 1;
        while (cells.size() < 42) {
            cells.add(makeCell(sharedPref, reminderKeys, todayY, todayM, todayD, nextYear, nextMonth0, day, false));
            day++;
        }

        return cells;
    }

    private DayCell makeCell(SharedPreferences sharedPref, HashSet<String> reminderKeys,
                            int todayY, int todayM, int todayD,
                            int year, int month0, int day,
                            boolean inCurrentMonth) {
        String dateKey = year + "-" + String.format("%02d", month0 + 1) + "-" + String.format("%02d", day);
        boolean hasEntry = sharedPref.contains(dateKey);
        boolean hasReminder = reminderKeys != null && reminderKeys.contains(dateKey);
        boolean hasMood = false;
        String moodEmoji = null;
        if (hasEntry) {
            String storyData = sharedPref.getString(dateKey, null);
            String moodId = extractMoodId(storyData);
            Mood mood = Mood.findById(moodId);
            if (mood != null) {
                hasMood = true;
                moodEmoji = mood.emoji;
            }
        }
        boolean isToday = (year == todayY && month0 == todayM && day == todayD);
        boolean isSelected = (year == selectedYear && month0 == selectedMonth0 && day == selectedDay);
        return new DayCell(year, month0, day, inCurrentMonth, isToday, hasEntry, hasReminder, hasMood, moodEmoji, isSelected);
    }

    private static final class DayCell {
        final int year;
        final int month0;
        final int day;
        final boolean inCurrentMonth;
        final boolean isToday;
        final boolean hasEntry;
        final boolean hasReminder;
        final boolean hasMood;
        final String moodEmoji;
        final boolean isSelected;

        DayCell(int year, int month0, int day, boolean inCurrentMonth, boolean isToday, boolean hasEntry, boolean hasReminder, boolean hasMood, String moodEmoji, boolean isSelected) {
            this.year = year;
            this.month0 = month0;
            this.day = day;
            this.inCurrentMonth = inCurrentMonth;
            this.isToday = isToday;
            this.hasEntry = hasEntry;
            this.hasReminder = hasReminder;
            this.hasMood = hasMood;
            this.moodEmoji = moodEmoji;
            this.isSelected = isSelected;
        }
    }

    private interface DayClickListener {
        void onClick(DayCell cell);
    }

    private static final class DaysAdapter extends RecyclerView.Adapter<DaysAdapter.VH> {
        private final List<DayCell> cells;
        private final DayClickListener listener;

        DaysAdapter(List<DayCell> cells, DayClickListener listener) {
            this.cells = cells;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            DayCell cell = cells.get(position);

            h.txtDay.setText(String.valueOf(cell.day));

            if (cell.isSelected) {
                h.txtDay.setBackgroundResource(R.drawable.bg_cal_day_selected);
                h.txtDay.setTextColor(0xFFFFFFFF);
                h.txtDay.setAlpha(1f);
            } else {
                h.txtDay.setBackground(null);
                if (cell.inCurrentMonth) {
                    h.txtDay.setTextColor(0xFFFFFFFF);
                    h.txtDay.setAlpha(1f);
                } else {
                    h.txtDay.setTextColor(h.itemView.getResources().getColor(R.color.menu_teal));
                    h.txtDay.setAlpha(0.7f);
                }
            }

            if (h.moodEmoji != null) {
                h.moodEmoji.setVisibility(View.GONE);
            }

            if (h.reminderBadge != null) {
                h.reminderBadge.setVisibility(cell.hasReminder ? View.VISIBLE : View.GONE);
            }

            if (!cell.isSelected && cell.isToday) {
                h.dot.setVisibility(View.VISIBLE);
                h.dot.setBackgroundResource(R.drawable.bg_cal_dot_today);
            } else if (!cell.isSelected && cell.hasEntry && cell.hasMood && cell.moodEmoji != null && !cell.moodEmoji.isEmpty()) {
                h.dot.setVisibility(View.GONE);
                if (h.moodEmoji != null) {
                    h.moodEmoji.setText(cell.moodEmoji);
                    h.moodEmoji.setVisibility(View.VISIBLE);
                }
            } else if (!cell.isSelected && cell.hasEntry) {
                h.dot.setVisibility(View.VISIBLE);
                h.dot.setBackgroundResource(R.drawable.bg_cal_dot_entry);
            } else {
                h.dot.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onClick(cell);
            });
        }

        @Override
        public int getItemCount() {
            return cells.size();
        }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView txtDay;
            final View dot;
            final TextView moodEmoji;
            final TextView reminderBadge;

            VH(@NonNull View itemView) {
                super(itemView);
                txtDay = itemView.findViewById(R.id.txtDay);
                dot = itemView.findViewById(R.id.dot);
                moodEmoji = itemView.findViewById(R.id.moodEmoji);
                reminderBadge = itemView.findViewById(R.id.reminderBadge);
            }
        }
    }
}
