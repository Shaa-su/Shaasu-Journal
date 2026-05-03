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

import androidx.appcompat.app.AppCompatActivity;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

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

        updateCalendar();
    }

    private void updateCalendar() {
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};

        int month0 = currentCalendar.get(Calendar.MONTH);
        int year = currentCalendar.get(Calendar.YEAR);

        monthText.setText(months[month0]);
        yearText.setText(String.valueOf(year));

        List<DayCell> cells = buildCells(year, month0);
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
    }

    private void updateMoodStats(int year, int month0) {
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};

        if (moodStatsMonthText != null) {
            moodStatsMonthText.setText(months[month0] + " " + year);
        }

        SharedPreferences sharedPref = getSharedPreferences("stories", MODE_PRIVATE);

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

    private List<DayCell> buildCells(int year, int month0) {
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

        SharedPreferences sharedPref = getSharedPreferences("stories", MODE_PRIVATE);
        Calendar today = Calendar.getInstance();
        int todayY = today.get(Calendar.YEAR);
        int todayM = today.get(Calendar.MONTH);
        int todayD = today.get(Calendar.DAY_OF_MONTH);

        List<DayCell> cells = new ArrayList<>(42);

        for (int i = 0; i < offset; i++) {
            int day = (daysInPrevMonth - offset + 1) + i;
            int cellMonth0 = prevMonth.get(Calendar.MONTH);
            int cellYear = prevMonth.get(Calendar.YEAR);
            cells.add(makeCell(sharedPref, todayY, todayM, todayD, cellYear, cellMonth0, day, false));
        }

        for (int day = 1; day <= daysInMonth; day++) {
            cells.add(makeCell(sharedPref, todayY, todayM, todayD, year, month0, day, true));
        }

        Calendar nextMonth = (Calendar) firstOfMonth.clone();
        nextMonth.add(Calendar.MONTH, 1);
        int nextMonth0 = nextMonth.get(Calendar.MONTH);
        int nextYear = nextMonth.get(Calendar.YEAR);

        int day = 1;
        while (cells.size() < 42) {
            cells.add(makeCell(sharedPref, todayY, todayM, todayD, nextYear, nextMonth0, day, false));
            day++;
        }

        return cells;
    }

    private DayCell makeCell(SharedPreferences sharedPref,
                            int todayY, int todayM, int todayD,
                            int year, int month0, int day,
                            boolean inCurrentMonth) {
        String dateKey = year + "-" + String.format("%02d", month0 + 1) + "-" + String.format("%02d", day);
        boolean hasEntry = sharedPref.contains(dateKey);
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
        return new DayCell(year, month0, day, inCurrentMonth, isToday, hasEntry, hasMood, moodEmoji, isSelected);
    }

    private static final class DayCell {
        final int year;
        final int month0;
        final int day;
        final boolean inCurrentMonth;
        final boolean isToday;
        final boolean hasEntry;
        final boolean hasMood;
        final String moodEmoji;
        final boolean isSelected;

        DayCell(int year, int month0, int day, boolean inCurrentMonth, boolean isToday, boolean hasEntry, boolean hasMood, String moodEmoji, boolean isSelected) {
            this.year = year;
            this.month0 = month0;
            this.day = day;
            this.inCurrentMonth = inCurrentMonth;
            this.isToday = isToday;
            this.hasEntry = hasEntry;
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

            VH(@NonNull View itemView) {
                super(itemView);
                txtDay = itemView.findViewById(R.id.txtDay);
                dot = itemView.findViewById(R.id.dot);
                moodEmoji = itemView.findViewById(R.id.moodEmoji);
            }
        }
    }
}
