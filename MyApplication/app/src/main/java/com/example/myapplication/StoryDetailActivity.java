package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class StoryDetailActivity extends AppCompatActivity {

    private TextView dateDisplayText;
    private EditText storyEditText;
    private EditText goalsEditText;
    private Button saveButton;
    private Button cancelButton;
    
    private int selectedDay;
    private int selectedMonth;
    private int selectedYear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_story_detail);
        
        dateDisplayText = findViewById(R.id.dateDisplayText);
        storyEditText = findViewById(R.id.storyEditText);
        goalsEditText = findViewById(R.id.goalsEditText);
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
        
        // Save button click listener
        saveButton.setOnClickListener(v -> saveStory());
        
        // Cancel button click listener
        cancelButton.setOnClickListener(v -> finish());
    }
    
    private void saveStory() {
        String story = storyEditText.getText().toString();
        String goals = goalsEditText.getText().toString();
        
        if (story.isEmpty() && goals.isEmpty()) {
            Toast.makeText(this, "Please write at least a story or goal", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Save to SharedPreferences
        SharedPreferences sharedPref = getSharedPreferences("stories", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        
        String dateKey = selectedYear + "-" + String.format("%02d", selectedMonth + 1) + "-" + String.format("%02d", selectedDay);
        String storyData = story + "|||" + goals; // Using ||| as separator
        
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
            String[] parts = storyData.split("\\|\\|\\|");
            if (parts.length >= 1) {
                storyEditText.setText(parts[0]);
            }
            if (parts.length >= 2) {
                goalsEditText.setText(parts[1]);
            }
        }
    }
}
