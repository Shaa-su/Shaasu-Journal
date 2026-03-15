package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class StoryDetailActivity extends AppCompatActivity {

    private TextView dateDisplayText;
    private EditText titleEditText;
    private EditText storyEditText;
    private LinearLayout goalsContainer;
    private Button addGoalButton;
    private Button saveButton;
    private Button cancelButton;
    
    private int selectedDay;
    private int selectedMonth;
    private int selectedYear;
    
    private List<GoalItem> goalItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_story_detail);
        
        dateDisplayText = findViewById(R.id.dateDisplayText);
        titleEditText = findViewById(R.id.titleEditText);
        storyEditText = findViewById(R.id.storyEditText);
        goalsContainer = findViewById(R.id.goalsContainer);
        addGoalButton = findViewById(R.id.addGoalButton);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);
        
        // Get date from intent
        Intent intent = getIntent();
        selectedDay = intent.getIntExtra("day", 1);
        selectedMonth = intent.getIntExtra("month", 0); // 0-11
        selectedYear = intent.getIntExtra("year", 2026);
        
        // Display the date
        String[] monthNames = {"January", "February", "March", "April", "May", "June",
                              "July", "August", "September", "October", "November", "December"};
        String dateStr = monthNames[selectedMonth] + " " + selectedDay + ", " + selectedYear;
        dateDisplayText.setText("Date: " + dateStr);
        
        // Load existing story if available
        loadStory();
        
        // Add Goal button click listener
        addGoalButton.setOnClickListener(v -> addGoal(""));
        
        // Save button click listener
        saveButton.setOnClickListener(v -> saveStory());
        
        // Cancel button click listener
        cancelButton.setOnClickListener(v -> finish());
    }
    
    private void addGoal(String goalText) {
        LinearLayout goalLayout = new LinearLayout(this);
        goalLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        goalLayout.setOrientation(LinearLayout.HORIZONTAL);
        goalLayout.setPadding(0, 8, 0, 8);
        
        // Create checkbox
        CheckBox goalCheckBox = new CheckBox(this);
        goalCheckBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        goalCheckBox.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
        
        // Create EditText for goal text
        EditText goalEditText = new EditText(this);
        LinearLayout.LayoutParams editTextParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1);
        editTextParams.setMargins(12, 0, 12, 0);
        goalEditText.setLayoutParams(editTextParams);
        goalEditText.setBackgroundColor(0xFF3c3c3c);
        goalEditText.setTextColor(0xFFffffff);
        goalEditText.setHintTextColor(0xFF808080);
        goalEditText.setHint("Enter goal...");
        goalEditText.setPadding(8, 8, 8, 8);
        goalEditText.setText(goalText);
        
        // Create delete button
        Button deleteButton = new Button(this);
        deleteButton.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        deleteButton.setText("×");
        deleteButton.setTextSize(20);
        deleteButton.setBackgroundColor(0xFFf44336);
        deleteButton.setTextColor(0xFFffffff);
        deleteButton.setPadding(8, 0, 8, 0);
        
        // Add delete functionality
        deleteButton.setOnClickListener(v -> {
            goalsContainer.removeView(goalLayout);
            goalItems.remove(new GoalItem(goalCheckBox, goalEditText));
        });
        
        // Add views to goal layout
        goalLayout.addView(goalCheckBox);
        goalLayout.addView(goalEditText);
        goalLayout.addView(deleteButton);
        
        // Add goal layout to container
        goalsContainer.addView(goalLayout);
        
        // Store goal item
        goalItems.add(new GoalItem(goalCheckBox, goalEditText));
    }
    
    private void saveStory() {
        String title = titleEditText.getText().toString();
        String story = storyEditText.getText().toString();
        
        if (story.isEmpty() && goalItems.isEmpty()) {
            Toast.makeText(this, "Please write at least a story or goal", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Collect goals data
        StringBuilder goalsData = new StringBuilder();
        for (int i = 0; i < goalItems.size(); i++) {
            GoalItem item = goalItems.get(i);
            String goalText = item.editText.getText().toString();
            boolean isCompleted = item.checkBox.isChecked();
            
            if (!goalText.isEmpty()) {
                if (i > 0) {
                    goalsData.append("|||");
                }
                goalsData.append(goalText).append("|").append(isCompleted);
            }
        }
        
        // Save to SharedPreferences
        SharedPreferences sharedPref = getSharedPreferences("stories", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        
        String dateKey = selectedYear + "-" + String.format("%02d", selectedMonth + 1) + "-" + String.format("%02d", selectedDay);
        String storyData = title + "||" + story + "||" + goalsData.toString();
        
        editor.putString(dateKey, storyData);
        editor.apply();
        
        Toast.makeText(this, "Story saved!", Toast.LENGTH_SHORT).show();
        finish();
    }
    
    private void loadStory() {
        SharedPreferences sharedPref = getSharedPreferences("stories", MODE_PRIVATE);
        String dateKey = selectedYear + "-" + String.format("%02d", selectedMonth + 1) + "-" + String.format("%02d", selectedDay);
        String storyData = sharedPref.getString(dateKey, null);
        
        if (storyData != null) {
            String[] parts = storyData.split("\\|\\|");
            if (parts.length >= 1) {
                titleEditText.setText(parts[0]);
            }
            if (parts.length >= 2) {
                storyEditText.setText(parts[1]);
            }
            if (parts.length >= 3 && !parts[2].isEmpty()) {
                String[] goals = parts[2].split("\\|\\|\\|");
                for (String goal : goals) {
                    String[] goalParts = goal.split("\\|");
                    String goalText = goalParts.length > 0 ? goalParts[0] : "";
                    boolean isCompleted = goalParts.length > 1 && Boolean.parseBoolean(goalParts[1]);
                    
                    addGoal(goalText);
                    
                    // Set checkbox state for the last added goal
                    if (!goalItems.isEmpty()) {
                        GoalItem lastGoal = goalItems.get(goalItems.size() - 1);
                        lastGoal.checkBox.setChecked(isCompleted);
                    }
                }
            }
        }
    }
    
    private static class GoalItem {
        CheckBox checkBox;
        EditText editText;
        
        GoalItem(CheckBox checkBox, EditText editText) {
            this.checkBox = checkBox;
            this.editText = editText;
        }
    }
}
