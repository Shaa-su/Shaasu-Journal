package com.example.myapplication;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StoriesListActivity extends AppCompatActivity {

    private ListView storiesListView;
    private Button backButton;
    private ArrayAdapter<String> adapter;
    private List<String> storiesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stories_list);
        
        storiesListView = findViewById(R.id.storiesListView);
        backButton = findViewById(R.id.backButton);
        
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
            // Can add functionality to view/edit individual story
        });
        
        // Back button
        backButton.setOnClickListener(v -> finish());
    }
    
    private void loadAllStories() {
        SharedPreferences sharedPref = getSharedPreferences("stories", MODE_PRIVATE);
        Map<String, ?> allStories = sharedPref.getAll();
        
        storiesList.clear();
        
        if (allStories.isEmpty()) {
            storiesList.add("No stories created yet. Start writing!");
        } else {
            // Sort dates in reverse order (newest first)
            List<String> dates = new ArrayList<>(allStories.keySet());
            Collections.sort(dates, Collections.reverseOrder());
            
            for (String dateKey : dates) {
                String storyData = (String) allStories.get(dateKey);
                if (storyData != null) {
                    // New format: title||story||goals
                    String[] parts = storyData.split("\\|\\|");
                    String title = parts.length > 0 ? parts[0] : "";
                    String story = parts.length > 1 ? parts[1] : "";
                    String goals = parts.length > 2 ? parts[2] : "";
                    
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
