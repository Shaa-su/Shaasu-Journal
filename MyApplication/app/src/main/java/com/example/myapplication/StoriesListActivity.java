package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class StoriesListActivity extends AppCompatActivity {

    private ListView storiesListView;
    private ImageButton backButton;
    private LinearLayout emptyStateContainer;
    private Button openCalendarButton;
    private TextView countBadge;
    private com.google.android.material.floatingactionbutton.FloatingActionButton calendarFab;
    private ArrayAdapter<String> adapter;
    private List<String> storiesList;
    private final List<String> storyDateKeys = new ArrayList<>();

    private static final Pattern IMAGE_PLACEHOLDER_PATTERN = Pattern.compile("\\[IMG:(.+?)\\]", Pattern.DOTALL);
    private static final String WALLPAPER_MARKER = "[WALLPAPER_MARKER]";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stories_list);
        
        storiesListView = findViewById(R.id.storiesListView);
        backButton = findViewById(R.id.backButton);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        openCalendarButton = findViewById(R.id.openCalendarButton);
        countBadge = findViewById(R.id.countBadge);
        calendarFab = findViewById(R.id.calendarFab);
        
        storiesList = new ArrayList<>();
        
        // Set up adapter with custom styling
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, storiesList) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View view = super.getView(position, convertView, parent);
                android.widget.TextView textView = (android.widget.TextView) view;
                textView.setTextColor(android.graphics.Color.WHITE);
                textView.setTextSize(14);
                textView.setPadding(16, 16, 16, 16);
                return view;
            }
        };
        
        storiesListView.setAdapter(adapter);
        
        // Load all stories from SharedPreferences
        loadAllStories();
        
        // Set item click listener to view story details
        storiesListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= storyDateKeys.size()) return;
            String dateKey = storyDateKeys.get(position);
            if (dateKey == null || dateKey.isEmpty()) return;

            // dateKey format: YYYY-MM-DD
            String[] parts = dateKey.split("-");
            if (parts.length != 3) return;

            try {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]) - 1; // 0-11
                int day = Integer.parseInt(parts[2]);

                Intent intent = new Intent(StoriesListActivity.this, StoryDetailActivity.class);
                intent.putExtra("day", day);
                intent.putExtra("month", month);
                intent.putExtra("year", year);
                startActivity(intent);
            } catch (NumberFormatException ignored) {
                // Ignore invalid date key
            }
        });
        
        // Back button
        backButton.setOnClickListener(v -> finish());

        if (openCalendarButton != null) {
            openCalendarButton.setOnClickListener(v -> {
                openCalendar();
            });
        }

        if (calendarFab != null) {
            calendarFab.setOnClickListener(v -> openCalendar());
        }
    }

    private void openCalendar() {
        Intent intent = new Intent(StoriesListActivity.this, CalendarActivity.class);
        startActivity(intent);
    }
    
    private void loadAllStories() {
        SharedPreferences sharedPref = getSharedPreferences("stories", MODE_PRIVATE);
        Map<String, ?> allStories = sharedPref.getAll();
        
        storiesList.clear();
        storyDateKeys.clear();

        int storyCount = 0;
        for (Map.Entry<String, ?> entry : allStories.entrySet()) {
            if (entry.getValue() instanceof String) storyCount++;
        }

        if (countBadge != null) {
            countBadge.setText(String.valueOf(storyCount));
        }
        
        if (allStories.isEmpty()) {
            if (storiesListView != null) storiesListView.setVisibility(android.view.View.GONE);
            if (emptyStateContainer != null) emptyStateContainer.setVisibility(android.view.View.VISIBLE);
        } else {
            if (emptyStateContainer != null) emptyStateContainer.setVisibility(android.view.View.GONE);
            if (storiesListView != null) storiesListView.setVisibility(android.view.View.VISIBLE);

            // Sort dates in reverse order (newest first)
            List<String> dates = new ArrayList<>(allStories.keySet());
            Collections.sort(dates, Collections.reverseOrder());
            
            for (String dateKey : dates) {
                String storyData = (String) allStories.get(dateKey);
                if (storyData != null) {
                    // Format: title||story||goals[WALLPAPER_MARKER]wallpaperBase64
                    // Strip wallpaper payload for preview parsing.
                    String storyDataForPreview = storyData;
                    int markerIndex = storyDataForPreview.indexOf(WALLPAPER_MARKER);
                    if (markerIndex >= 0) {
                        storyDataForPreview = storyDataForPreview.substring(0, markerIndex);
                    }

                    String[] parts = storyDataForPreview.split("\\|\\|");
                    String title = parts.length > 0 ? parts[0] : "";
                    String story = parts.length > 1 ? parts[1] : "";
                    String goals = parts.length > 2 ? parts[2] : "";

                    // Remove inline image placeholders from preview text
                    story = IMAGE_PLACEHOLDER_PATTERN.matcher(story).replaceAll("[image]");
                    
                    // Format the display
                    String preview = dateKey + "\n";
                    
                    if (!title.isEmpty()) {
                        preview += "📝 " + title + "\n";
                    }
                    
                    if (!story.isEmpty()) {
                        preview += (story.length() > 60 ? story.substring(0, 60) + "..." : story) + "\n";
                    }
                    
                    if (!goals.isEmpty()) {
                        int goalCount = goals.split("\\|\\|\\|").length;
                        preview += "✓ " + goalCount + " goal(s)";
                    }
                    
                    storiesList.add(preview);
                    storyDateKeys.add(dateKey);
                }
            }
        }
        
        adapter.notifyDataSetChanged();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Reload stories when returning to this activity
        loadAllStories();
    }
}
