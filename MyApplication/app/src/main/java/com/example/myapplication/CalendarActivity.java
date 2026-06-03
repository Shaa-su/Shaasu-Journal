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
import android.widget.Toast;
import android.text.InputType;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Paint;

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
    private TextView tabGoals;
    private LinearLayout statsContainer;
    private LinearLayout remindersContainer;
    private LinearLayout remindersList;
    private TextView remindersEmptyText;
    private LinearLayout goalsContainer;
    private LinearLayout goalsDateTabs;
    private LinearLayout goalsColOngoing;
    private LinearLayout goalsColWorking;
    private LinearLayout goalsColDone;
    private TextView goalsEmptyText;
    private EditText goalsAddInput;
    private View goalsAddButton;
    private String selectedGoalsDateKey;

    private static final int TAB_STATS = 0;
    private static final int TAB_REMINDERS = 1;
    private static final int TAB_GOALS = 2;

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
        tabGoals = findViewById(R.id.tabGoals);
        statsContainer = findViewById(R.id.statsContainer);
        remindersContainer = findViewById(R.id.remindersContainer);
        remindersList = findViewById(R.id.remindersList);
        remindersEmptyText = findViewById(R.id.remindersEmptyText);
        goalsContainer = findViewById(R.id.goalsContainer);
        goalsDateTabs = findViewById(R.id.goalsDateTabs);
        goalsColOngoing = findViewById(R.id.goalsColOngoing);
        goalsColWorking = findViewById(R.id.goalsColWorking);
        goalsColDone = findViewById(R.id.goalsColDone);
        goalsEmptyText = findViewById(R.id.goalsEmptyText);
        goalsAddInput = findViewById(R.id.goalsAddInput);
        goalsAddButton = findViewById(R.id.goalsAddButton);

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
            tabStats.setOnClickListener(v -> selectTab(TAB_STATS));
        }
        if (tabReminders != null) {
            tabReminders.setOnClickListener(v -> selectTab(TAB_REMINDERS));
        }
        if (tabGoals != null) {
            tabGoals.setOnClickListener(v -> selectTab(TAB_GOALS));
        }

        if (goalsAddButton != null) {
            goalsAddButton.setOnClickListener(v -> submitNewGoalFromInput());
        }

        selectTab(TAB_STATS);

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
        updateGoalsList(year, month0);
    }

    private void selectTab(int tab) {
        if (tabStats != null) tabStats.setSelected(tab == TAB_STATS);
        if (tabReminders != null) tabReminders.setSelected(tab == TAB_REMINDERS);
        if (tabGoals != null) tabGoals.setSelected(tab == TAB_GOALS);

        if (statsContainer != null) statsContainer.setVisibility(tab == TAB_STATS ? View.VISIBLE : View.GONE);
        if (remindersContainer != null) remindersContainer.setVisibility(tab == TAB_REMINDERS ? View.VISIBLE : View.GONE);
        if (goalsContainer != null) goalsContainer.setVisibility(tab == TAB_GOALS ? View.VISIBLE : View.GONE);

        int month0 = currentCalendar.get(Calendar.MONTH);
        int year = currentCalendar.get(Calendar.YEAR);

        if (tab == TAB_REMINDERS) {
            updateRemindersList(year, month0);
        } else if (tab == TAB_GOALS) {
            updateGoalsList(year, month0);
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

    private void updateGoalsList(int year, int month0) {
        if (goalsContainer == null || goalsDateTabs == null || goalsColOngoing == null) return;

        goalsDateTabs.removeAllViews();
        goalsColOngoing.removeAllViews();
        goalsColWorking.removeAllViews();
        goalsColDone.removeAllViews();

        SharedPreferences sharedPref = StoryStore.get(this);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month0);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        Map<String, List<GoalEntry>> grouped = new LinkedHashMap<>();
        for (int day = 1; day <= daysInMonth; day++) {
            String dateKey = year + "-" + String.format("%02d", month0 + 1) + "-" + String.format("%02d", day);
            String storyData = sharedPref.getString(dateKey, null);
            if (storyData == null || storyData.isEmpty()) continue;
            List<GoalEntry> goals = extractGoalsFromStory(storyData);
            if (goals.isEmpty()) continue;
            grouped.put(dateKey, goals);
        }

        List<String> dateKeys = new ArrayList<>(grouped.keySet());
        Collections.sort(dateKeys);

        if (goalsEmptyText != null) {
            goalsEmptyText.setVisibility(dateKeys.isEmpty() ? View.VISIBLE : View.GONE);
        }

        if (selectedGoalsDateKey == null) {
            selectedGoalsDateKey = getTodayDateKey();
        }

        List<String> displayKeys = new ArrayList<>(dateKeys);
        String todayKey = getTodayDateKey();
        if (!displayKeys.contains(todayKey)) {
            displayKeys.add(todayKey);
        }
        if (selectedGoalsDateKey != null && !displayKeys.contains(selectedGoalsDateKey)) {
            displayKeys.add(selectedGoalsDateKey);
        }
        Collections.sort(displayKeys);

        if (goalsAddInput != null) goalsAddInput.setEnabled(true);
        if (goalsAddButton != null) goalsAddButton.setEnabled(true);

        renderGoalDateTabs(displayKeys, grouped);
        showGoalsForDate(selectedGoalsDateKey, grouped.get(selectedGoalsDateKey));
    }

    private void renderGoalDateTabs(List<String> dateKeys, Map<String, List<GoalEntry>> grouped) {
        if (goalsDateTabs == null) return;
        goalsDateTabs.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);
        SimpleDateFormat dateFmt = new SimpleDateFormat("MMM d", Locale.US);

        TextView pickChip = (TextView) inflater.inflate(android.R.layout.simple_list_item_1, goalsDateTabs, false);
        pickChip.setText("Pick date");
        pickChip.setTextSize(12f);
        pickChip.setTextColor(getColor(R.color.menu_text_primary));
        pickChip.setGravity(android.view.Gravity.CENTER);
        pickChip.setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8));
        pickChip.setBackgroundResource(R.drawable.bg_reminder_time_chip);
        LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pickLp.setMarginEnd(dpToPx(8));
        pickChip.setLayoutParams(pickLp);
        pickChip.setOnClickListener(v -> showGoalsDatePicker());
        goalsDateTabs.addView(pickChip);

        for (String dateKey : dateKeys) {
            TextView chip = (TextView) inflater.inflate(android.R.layout.simple_list_item_1, goalsDateTabs, false);
            Date d = parseDateKey(dateKey);
            chip.setText(d != null ? dateFmt.format(d) : dateKey);
            chip.setTextSize(12f);
            chip.setTextColor(getColor(R.color.menu_text_primary));
            chip.setGravity(android.view.Gravity.CENTER);
            int padH = dpToPx(14);
            int padV = dpToPx(8);
            chip.setPadding(padH, padV, padH, padV);
            boolean selected = dateKey.equals(selectedGoalsDateKey);
            chip.setBackgroundResource(selected ? R.drawable.bg_reminder_time_chip_selected : R.drawable.bg_reminder_time_chip);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMarginEnd(dpToPx(8));
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                selectedGoalsDateKey = dateKey;
                renderGoalDateTabs(dateKeys, grouped);
                showGoalsForDate(dateKey, grouped.get(dateKey));
            });

            goalsDateTabs.addView(chip);
        }
    }

    private void showGoalsForDate(String dateKey, List<GoalEntry> goals) {
        if (goalsColOngoing == null) return;
        goalsColOngoing.removeAllViews();
        goalsColWorking.removeAllViews();
        goalsColDone.removeAllViews();
        final List<GoalEntry> finalGoals = (goals != null) ? goals : new ArrayList<>();

        // Setup the columns and individual cards to accept drag drops
        android.view.View.OnDragListener dragListener = (v, event) -> {
            switch (event.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case android.view.DragEvent.ACTION_DRAG_ENTERED:
                    v.setAlpha(0.6f); // visually indicate drop target
                    return true;
                case android.view.DragEvent.ACTION_DRAG_EXITED:
                    v.setAlpha(1f);
                    return true;
                case android.view.DragEvent.ACTION_DROP:
                    v.setAlpha(1f);
                    GoalEntry draggedEntry = (GoalEntry) event.getLocalState();
                    if (draggedEntry != null) {
                        int newState = 0;
                        int vid = v.getId();
                        android.view.ViewParent parent = v.getParent();
                        if (vid == R.id.goalsColOngoing || vid == R.id.colOngoingContainer || parent == goalsColOngoing) newState = 0;
                        else if (vid == R.id.goalsColWorking || vid == R.id.colWorkingContainer || parent == goalsColWorking) newState = 1;
                        else if (vid == R.id.goalsColDone || vid == R.id.colDoneContainer || parent == goalsColDone) newState = 2;

                        if (draggedEntry.state != newState) {
                            draggedEntry.state = newState;
                            saveGoalsForDate(dateKey, finalGoals);
                            showGoalsForDate(dateKey, finalGoals);
                        }
                    }
                    return true;
                case android.view.DragEvent.ACTION_DRAG_ENDED:
                    v.setAlpha(1f);
                    return true;
            }
            return false;
        };

        LayoutInflater inflater = LayoutInflater.from(this);
        for (GoalEntry entry : finalGoals) {
            View row = inflater.inflate(R.layout.item_goal_row, null, false);
            View checkButton = row.findViewById(R.id.goalCheckButton);
            View checkCircle = row.findViewById(R.id.goalCheckCircle);
            android.widget.ImageView checkIcon = row.findViewById(R.id.goalCheckIcon);
            
            // Hide standard checkboxes since this is a drag-and-drop Kanban board
            if (checkButton != null) checkButton.setVisibility(View.GONE);
            if (checkCircle != null) checkCircle.setVisibility(View.GONE);
            if (checkIcon != null) checkIcon.setVisibility(View.GONE);

            TextView goalText = row.findViewById(R.id.goalText);

            goalText.setText(entry.text);

            if (entry.state == 2) { // Completed
                goalText.setPaintFlags(goalText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                goalText.setAlpha(0.6f);
            } else {
                goalText.setPaintFlags(goalText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                goalText.setAlpha(1f);
            }

            // Create a shared drag starter
            android.view.View.OnLongClickListener dragStarter = v -> {
                android.content.ClipData data = android.content.ClipData.newPlainText("goal", "goal");
                View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(row);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    row.startDragAndDrop(data, shadowBuilder, entry, 0);
                } else {
                    row.startDrag(data, shadowBuilder, entry, 0);
                }
                return true;
            };

            row.setOnLongClickListener(dragStarter);
            row.setOnDragListener(dragListener); // Allow dropping directly onto another card

            if (goalText != null) {
                goalText.setOnClickListener(v -> showEditGoalDialog(dateKey, entry, finalGoals));
                goalText.setOnLongClickListener(dragStarter); // Prevent text from swallowing the long press
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.topMargin = dpToPx(8);
            row.setLayoutParams(lp);
            row.setBackgroundResource(R.drawable.bg_login_input); // Gives a nice card shape

            if (entry.state == 0) goalsColOngoing.addView(row);
            else if (entry.state == 1) goalsColWorking.addView(row);
            else goalsColDone.addView(row);
        }
        
        View ongoingCont = findViewById(R.id.colOngoingContainer);
        View workingCont = findViewById(R.id.colWorkingContainer);
        View doneCont = findViewById(R.id.colDoneContainer);

        if (ongoingCont != null) ongoingCont.setOnDragListener(dragListener);
        if (workingCont != null) workingCont.setOnDragListener(dragListener);
        if (doneCont != null) doneCont.setOnDragListener(dragListener);

        goalsColOngoing.setOnDragListener(dragListener);
        goalsColWorking.setOnDragListener(dragListener);
        goalsColDone.setOnDragListener(dragListener);
    }

    private void showEditGoalDialog(String dateKey, GoalEntry entry, List<GoalEntry> goals) {
        if (entry == null) return;

        View container = LayoutInflater.from(this).inflate(R.layout.dialog_edit_goal, null, false);
        EditText input = container.findViewById(R.id.editGoalInput);
        TextView saveBtn = container.findViewById(R.id.editGoalSave);
        TextView cancelBtn = container.findViewById(R.id.editGoalCancel);
        TextView deleteBtn = container.findViewById(R.id.editGoalDelete);

        if (input == null || saveBtn == null || cancelBtn == null || deleteBtn == null) return;

        input.setText(entry.text);
        input.setSelection(input.getText().length());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });

        saveBtn.setOnClickListener(v -> {
            String txt = input.getText() != null ? input.getText().toString().trim() : "";
            if (txt.isEmpty()) return;
            entry.text = txt;
            saveGoalsForDate(dateKey, goals);
            showGoalsForDate(dateKey, goals);
            dialog.dismiss();
        });

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        deleteBtn.setOnClickListener(v -> {
            goals.remove(entry);
            saveGoalsForDate(dateKey, goals);
            updateGoalsList(currentCalendar.get(Calendar.YEAR), currentCalendar.get(Calendar.MONTH));
            dialog.dismiss();
        });

        dialog.show();
    }

    private void submitNewGoalFromInput() {
        if (goalsAddInput == null) return;
        if (selectedGoalsDateKey == null) {
            selectedGoalsDateKey = getTodayDateKey();
        }
        String text = goalsAddInput.getText() != null ? goalsAddInput.getText().toString().trim() : "";
        if (text.isEmpty()) return;

        List<GoalEntry> goals = getGoalsForDate(selectedGoalsDateKey);
        goals.add(new GoalEntry(text, 0)); // 0 = Planning
        saveGoalsForDate(selectedGoalsDateKey, goals);
        goalsAddInput.setText("");
        updateGoalsList(currentCalendar.get(Calendar.YEAR), currentCalendar.get(Calendar.MONTH));
    }

    private List<GoalEntry> getGoalsForDate(String dateKey) {
        List<GoalEntry> goals = new ArrayList<>();
        if (dateKey == null) return goals;
        SharedPreferences sharedPref = StoryStore.get(this);
        String storyData = sharedPref.getString(dateKey, null);
        if (storyData == null || storyData.isEmpty()) return goals;
        return extractGoalsFromStory(storyData);
    }

    private List<GoalEntry> extractGoalsFromStory(String storyData) {
        List<GoalEntry> goals = new ArrayList<>();
        StoryParts parts = parseStoryParts(storyData);
        String goalsBlob = parts != null ? parts.goalsBlob : "";
        if (goalsBlob == null || goalsBlob.trim().isEmpty()) return goals;

        String[] items = goalsBlob.split("\\|\\|\\|");
        for (String item : items) {
            if (item == null || item.trim().isEmpty()) continue;
            String[] partsGoal = item.split("\\|");
            String text = partsGoal.length > 0 ? partsGoal[0] : "";
            
            int state = 0;
            if (partsGoal.length > 1) {
                String stateStr = partsGoal[1];
                if ("true".equalsIgnoreCase(stateStr)) state = 2; // Migrate old checked
                else if ("false".equalsIgnoreCase(stateStr)) state = 0; // Migrate old unchecked
                else {
                    try { state = Integer.parseInt(stateStr); } catch (Exception ignored) {}
                }
            }
            
            if (text.trim().isEmpty()) continue;
            goals.add(new GoalEntry(text, state));
        }

        return goals;
    }

    private void saveGoalsForDate(String dateKey, List<GoalEntry> goals) {
        if (dateKey == null) return;
        SharedPreferences sharedPref = StoryStore.get(this);
        String storyData = sharedPref.getString(dateKey, null);
        if (storyData == null) {
            StoryParts fresh = new StoryParts();
            fresh.title = "";
            fresh.story = "";
            fresh.goalsBlob = serializeGoals(goals);
            String rebuilt = buildStoryData(fresh);
            sharedPref.edit().putString(dateKey, rebuilt).apply();
            return;
        }

        StoryParts parts = parseStoryParts(storyData);
        if (parts == null) return;
        parts.goalsBlob = serializeGoals(goals);

        String rebuilt = buildStoryData(parts);
        sharedPref.edit().putString(dateKey, rebuilt).apply();
    }

    private String serializeGoals(List<GoalEntry> goals) {
        if (goals == null || goals.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (GoalEntry entry : goals) {
            if (entry == null) continue;
            String text = entry.text != null ? entry.text.trim() : "";
            if (text.isEmpty()) continue;
            if (!first) out.append("|||");
            out.append(text).append("|").append(entry.state);
            first = false;
        }
        return out.toString();
    }

    private StoryParts parseStoryParts(String storyData) {
        if (storyData == null) return null;
        String working = storyData;

        String wallpaperEncoded = "";
        int wpIndex = working.indexOf(WALLPAPER_MARKER);
        if (wpIndex >= 0) {
            wallpaperEncoded = working.substring(wpIndex + WALLPAPER_MARKER.length());
            working = working.substring(0, wpIndex);
        }

        String moodId = null;
        int moodIndex = working.indexOf(MOOD_MARKER);
        if (moodIndex >= 0) {
            moodId = working.substring(moodIndex + MOOD_MARKER.length()).trim();
            working = working.substring(0, moodIndex);
        }

        String title = "";
        String story = "";
        String goalsBlob = "";
        int firstSep = working.indexOf("||");
        if (firstSep < 0) {
            title = working;
        } else {
            title = working.substring(0, firstSep);
            int secondSep = working.indexOf("||", firstSep + 2);
            if (secondSep < 0) {
                story = working.substring(firstSep + 2);
            } else {
                story = working.substring(firstSep + 2, secondSep);
                goalsBlob = working.substring(secondSep + 2);
            }
        }

        StoryParts parts = new StoryParts();
        parts.title = title != null ? title : "";
        parts.story = story != null ? story : "";
        parts.goalsBlob = goalsBlob != null ? goalsBlob : "";
        parts.moodId = moodId;
        parts.wallpaperEncoded = wallpaperEncoded;
        return parts;
    }

    private String buildStoryData(StoryParts parts) {
        String storyData = (parts.title != null ? parts.title : "")
                + "||"
                + (parts.story != null ? parts.story : "")
                + "||"
                + (parts.goalsBlob != null ? parts.goalsBlob : "");

        if (parts.moodId != null && !parts.moodId.trim().isEmpty()) {
            storyData += MOOD_MARKER + parts.moodId.trim();
        }
        if (parts.wallpaperEncoded != null && !parts.wallpaperEncoded.isEmpty()) {
            storyData += WALLPAPER_MARKER + parts.wallpaperEncoded;
        }
        return storyData;
    }

    private static class GoalEntry {
        String text;
        int state;

        GoalEntry(String text, int state) {
            this.text = text;
            this.state = state;
        }
    }

    private static class StoryParts {
        String title;
        String story;
        String goalsBlob;
        String moodId;
        String wallpaperEncoded;
    }

    private String getTodayDateKey() {
        Calendar today = Calendar.getInstance();
        return today.get(Calendar.YEAR) + "-" + String.format(Locale.US, "%02d", today.get(Calendar.MONTH) + 1)
                + "-" + String.format(Locale.US, "%02d", today.get(Calendar.DAY_OF_MONTH));
    }

    private void showGoalsDatePicker() {
        View container = LayoutInflater.from(this).inflate(R.layout.dialog_goals_date_picker, null, false);
        TextView monthText = container.findViewById(R.id.pickerMonthText);
        TextView yearText = container.findViewById(R.id.pickerYearText);
        ImageButton prevButton = container.findViewById(R.id.pickerPrevButton);
        ImageButton nextButton = container.findViewById(R.id.pickerNextButton);
        RecyclerView daysRecycler = container.findViewById(R.id.pickerRecyclerDays);

        if (monthText == null || yearText == null || prevButton == null || nextButton == null || daysRecycler == null) {
            return;
        }

        Calendar pickerCal = Calendar.getInstance();
        if (selectedGoalsDateKey != null) {
            Date d = parseDateKey(selectedGoalsDateKey);
            if (d != null) pickerCal.setTime(d);
        }

        daysRecycler.setLayoutManager(new GridLayoutManager(this, 7));
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};

        HashSet<String> reminderKeys = ReminderStore.getReminderDateKeys(this);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });

        Runnable refresh = () -> {
            int year = pickerCal.get(Calendar.YEAR);
            int month0 = pickerCal.get(Calendar.MONTH);
            monthText.setText(months[month0]);
            yearText.setText(String.valueOf(year));
            List<DayCell> cells = buildPickerCells(year, month0, reminderKeys, selectedGoalsDateKey);
            daysRecycler.setAdapter(new DaysAdapter(cells, cell -> {
                selectedGoalsDateKey = cell.year + "-" + String.format(Locale.US, "%02d", cell.month0 + 1)
                        + "-" + String.format(Locale.US, "%02d", cell.day);
                currentCalendar.set(Calendar.YEAR, cell.year);
                currentCalendar.set(Calendar.MONTH, cell.month0);
                currentCalendar.set(Calendar.DAY_OF_MONTH, 1);
                updateCalendar();
                selectTab(TAB_GOALS);
                dialog.dismiss();
            }));
        };

        prevButton.setOnClickListener(v -> {
            pickerCal.add(Calendar.MONTH, -1);
            refresh.run();
        });
        nextButton.setOnClickListener(v -> {
            pickerCal.add(Calendar.MONTH, 1);
            refresh.run();
        });

        refresh.run();
        dialog.show();
    }

    private void showEditReminderDialog(Reminder reminder) {
        if (reminder == null) return;

        View container = LayoutInflater.from(this).inflate(R.layout.dialog_reminder, null, false);
        EditText titleInput = container.findViewById(R.id.reminderTitleInput);
        TextView toggleClock = container.findViewById(R.id.reminderToggleClock);
        TextView toggleKeyboard = container.findViewById(R.id.reminderToggleKeyboard);
        TextView hourChip = container.findViewById(R.id.reminderHourChip);
        TextView minuteChip = container.findViewById(R.id.reminderMinuteChip);
        TextView ampmChip = container.findViewById(R.id.reminderAmPmChip);
        View timeRow = container.findViewById(R.id.reminderTimeRow);
        TextView pickTimeHint = container.findViewById(R.id.reminderPickTimeHint);
        TextView modeLabel = container.findViewById(R.id.reminderModeLabel);
        ReminderClockView clockView = container.findViewById(R.id.reminderClock);
        View keyboardRow = container.findViewById(R.id.reminderKeyboardRow);
        EditText hourInput = container.findViewById(R.id.reminderHourInput);
        EditText minuteInput = container.findViewById(R.id.reminderMinuteInput);
        TextView keyboardAmPm = container.findViewById(R.id.reminderKeyboardAmPm);
        TextView repeatOnce = container.findViewById(R.id.reminderRepeatOnce);
        TextView repeatDaily = container.findViewById(R.id.reminderRepeatDaily);
        TextView cancelBtn = container.findViewById(R.id.reminderCancel);
        TextView saveBtn = container.findViewById(R.id.reminderSave);
        if (titleInput == null || toggleClock == null || toggleKeyboard == null
            || hourChip == null || minuteChip == null || ampmChip == null
            || timeRow == null || pickTimeHint == null || modeLabel == null || clockView == null
            || keyboardRow == null || hourInput == null || minuteInput == null || keyboardAmPm == null
            || repeatOnce == null || repeatDaily == null || cancelBtn == null || saveBtn == null) {
            return;
        }

        titleInput.setText(reminder.title);
        Calendar current = Calendar.getInstance();
        current.setTimeInMillis(reminder.triggerAtMillis);
        final int[] chosenHour = {current.get(Calendar.HOUR_OF_DAY)};
        final int[] chosenMinute = {current.get(Calendar.MINUTE)};
        final boolean is24 = DateFormat.is24HourFormat(this);

        clockView.setTime(chosenHour[0], chosenMinute[0], DateFormat.is24HourFormat(this));

        updateTimeChips(hourChip, minuteChip, ampmChip, chosenHour[0], chosenMinute[0]);

        toggleClock.setBackgroundResource(R.drawable.bg_reminder_time_chip_selected);
        toggleKeyboard.setBackgroundResource(R.drawable.bg_reminder_time_chip);
        keyboardRow.setVisibility(View.GONE);
        clockView.setVisibility(View.VISIBLE);
        pickTimeHint.setVisibility(View.VISIBLE);
        modeLabel.setVisibility(View.VISIBLE);

        Runnable syncInputsFromChosen = () -> {
            int displayHour = chosenHour[0];
            if (!is24) {
                displayHour = chosenHour[0] % 12;
                if (displayHour == 0) displayHour = 12;
            }
            hourInput.setText(String.format(Locale.US, "%02d", displayHour));
            minuteInput.setText(String.format(Locale.US, "%02d", chosenMinute[0]));
            if (is24) {
                keyboardAmPm.setVisibility(View.GONE);
            } else {
                keyboardAmPm.setVisibility(View.VISIBLE);
                keyboardAmPm.setText(chosenHour[0] >= 12 ? "PM" : "AM");
            }
        };

        Runnable syncChosenFromInputs = () -> {
            int hourVal = safeParseInt(hourInput.getText() != null ? hourInput.getText().toString().trim() : "", is24 ? chosenHour[0] : 12);
            int minuteVal = safeParseInt(minuteInput.getText() != null ? minuteInput.getText().toString().trim() : "", chosenMinute[0]);
            minuteVal = Math.max(0, Math.min(59, minuteVal));

            if (is24) {
                hourVal = Math.max(0, Math.min(23, hourVal));
                chosenHour[0] = hourVal;
            } else {
                hourVal = Math.max(1, Math.min(12, hourVal));
                boolean pm = "PM".equalsIgnoreCase(keyboardAmPm.getText().toString());
                int hour24 = (hourVal % 12) + (pm ? 12 : 0);
                if (hour24 == 24) hour24 = 0;
                chosenHour[0] = hour24;
            }
            chosenMinute[0] = minuteVal;
            updateTimeChips(hourChip, minuteChip, ampmChip, chosenHour[0], chosenMinute[0]);
            clockView.setTime(chosenHour[0], chosenMinute[0], DateFormat.is24HourFormat(this));
        };

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

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });

        clockView.setOnTimeChangeListener((h, m, mode) -> {
            chosenHour[0] = h;
            chosenMinute[0] = m;
            updateTimeChips(hourChip, minuteChip, ampmChip, h, m);
            modeLabel.setText(mode == ReminderClockView.Mode.HOUR ? "Select Hour" : "Select Minute");
        });

        toggleClock.setOnClickListener(v -> {
            toggleClock.setBackgroundResource(R.drawable.bg_reminder_time_chip_selected);
            toggleKeyboard.setBackgroundResource(R.drawable.bg_reminder_time_chip);
            timeRow.setVisibility(View.VISIBLE);
            clockView.setVisibility(View.VISIBLE);
            pickTimeHint.setVisibility(View.VISIBLE);
            modeLabel.setVisibility(View.VISIBLE);
            keyboardRow.setVisibility(View.GONE);
        });

        toggleKeyboard.setOnClickListener(v -> {
            toggleKeyboard.setBackgroundResource(R.drawable.bg_reminder_time_chip_selected);
            toggleClock.setBackgroundResource(R.drawable.bg_reminder_time_chip);
            timeRow.setVisibility(View.GONE);
            clockView.setVisibility(View.GONE);
            pickTimeHint.setVisibility(View.GONE);
            modeLabel.setVisibility(View.GONE);
            keyboardRow.setVisibility(View.VISIBLE);
            syncInputsFromChosen.run();
        });

        keyboardAmPm.setOnClickListener(v -> {
            if (is24) return;
            keyboardAmPm.setText("AM".equalsIgnoreCase(keyboardAmPm.getText().toString()) ? "PM" : "AM");
            syncChosenFromInputs.run();
        });

        hourInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) syncChosenFromInputs.run();
        });

        minuteInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) syncChosenFromInputs.run();
        });

        timeRow.setOnClickListener(v -> {
            if (keyboardRow.getVisibility() == View.VISIBLE) {
                syncChosenFromInputs.run();
                return;
            }
            ReminderClockView.Mode mode = clockView.getMode();
            ReminderClockView.Mode next = (mode == ReminderClockView.Mode.HOUR)
                    ? ReminderClockView.Mode.MINUTE
                    : ReminderClockView.Mode.HOUR;
            clockView.setMode(next);
            modeLabel.setText(next == ReminderClockView.Mode.HOUR ? "Select Hour" : "Select Minute");
        });

        hourChip.setOnClickListener(v -> {
            showNumberInput("Set Hour", DateFormat.is24HourFormat(this) ? 0 : 1,
                    DateFormat.is24HourFormat(this) ? 23 : 12, chosenHour[0] % 12 == 0 ? 12 : chosenHour[0] % 12, value -> {
                        int hour = value;
                        if (!DateFormat.is24HourFormat(this)) {
                            boolean pm = chosenHour[0] >= 12;
                            hour = (value % 12) + (pm ? 12 : 0);
                            if (hour == 24) hour = 0;
                        }
                        chosenHour[0] = hour;
                        updateTimeChips(hourChip, minuteChip, ampmChip, chosenHour[0], chosenMinute[0]);
                        clockView.setTime(chosenHour[0], chosenMinute[0], DateFormat.is24HourFormat(this));
                    });
        });

        minuteChip.setOnClickListener(v -> {
            showNumberInput("Set Minute", 0, 59, chosenMinute[0], value -> {
                chosenMinute[0] = value;
                updateTimeChips(hourChip, minuteChip, ampmChip, chosenHour[0], chosenMinute[0]);
                clockView.setTime(chosenHour[0], chosenMinute[0], DateFormat.is24HourFormat(this));
            });
        });

        ampmChip.setOnClickListener(v -> {
            if (DateFormat.is24HourFormat(this)) return;
            if (chosenHour[0] >= 12) {
                chosenHour[0] -= 12;
            } else {
                chosenHour[0] += 12;
            }
            updateTimeChips(hourChip, minuteChip, ampmChip, chosenHour[0], chosenMinute[0]);
            clockView.setTime(chosenHour[0], chosenMinute[0], DateFormat.is24HourFormat(this));
        });

        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        saveBtn.setOnClickListener(v -> {
            if (keyboardRow.getVisibility() == View.VISIBLE) {
                syncChosenFromInputs.run();
            }
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

    private interface NumberInputCallback {
        void onValue(int value);
    }

    private void showNumberInput(String title, int min, int max, int current, NumberInputCallback cb) {
        android.content.Context themed = new android.view.ContextThemeWrapper(this, R.style.ThemeOverlay_App_DarkDialog);
        EditText input = new EditText(themed);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(String.valueOf(current));
        input.setTextColor(getColor(R.color.menu_text_primary));
        input.setHintTextColor(getColor(R.color.menu_text_secondary));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.menu_teal)));

        new AlertDialog.Builder(themed)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String txt = input.getText() != null ? input.getText().toString() : "";
                    try {
                        int val = Integer.parseInt(txt);
                        if (val < min || val > max) {
                            Toast.makeText(this, "Enter a value between " + min + " and " + max, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (cb != null) cb.onValue(val);
                    } catch (NumberFormatException ignored) {
                        Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int safeParseInt(String text, int fallback) {
        if (text == null || text.isEmpty()) return fallback;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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

    private List<DayCell> buildPickerCells(int year, int month0, HashSet<String> reminderKeys, String selectedKey) {
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
            cells.add(makePickerCell(sharedPref, reminderKeys, selectedKey, todayY, todayM, todayD, cellYear, cellMonth0, day, false));
        }

        for (int day = 1; day <= daysInMonth; day++) {
            cells.add(makePickerCell(sharedPref, reminderKeys, selectedKey, todayY, todayM, todayD, year, month0, day, true));
        }

        Calendar nextMonth = (Calendar) firstOfMonth.clone();
        nextMonth.add(Calendar.MONTH, 1);
        int nextMonth0 = nextMonth.get(Calendar.MONTH);
        int nextYear = nextMonth.get(Calendar.YEAR);

        int day = 1;
        while (cells.size() < 42) {
            cells.add(makePickerCell(sharedPref, reminderKeys, selectedKey, todayY, todayM, todayD, nextYear, nextMonth0, day, false));
            day++;
        }

        return cells;
    }

    private DayCell makePickerCell(SharedPreferences sharedPref, HashSet<String> reminderKeys, String selectedKey,
                                   int todayY, int todayM, int todayD,
                                   int year, int month0, int day,
                                   boolean inCurrentMonth) {
        String dateKey = year + "-" + String.format(Locale.US, "%02d", month0 + 1) + "-" + String.format(Locale.US, "%02d", day);
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
        boolean isSelected = selectedKey != null && selectedKey.equals(dateKey);
        return new DayCell(year, month0, day, inCurrentMonth, isToday, hasEntry, hasReminder, hasMood, moodEmoji, isSelected);
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
