package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Find logout button
        Button logoutButton = findViewById(R.id.logoutButton);
        
        // Set up logout button click listener
        logoutButton.setOnClickListener(v -> handleLogout());
        
        // Find create goal button
        Button createGoalButton = findViewById(R.id.creategoal);
        
        // Set up create goal button click listener
        createGoalButton.setOnClickListener(v -> handleCreateGoal());
        
        // Find stories button
        Button storiesButton = findViewById(R.id.existingstory);
        
        // Set up stories button click listener
        storiesButton.setOnClickListener(v -> handleViewStories());
    }
    
    private void handleViewStories() {
        // Navigate to StoriesListActivity
        Intent intent = new Intent(MainActivity.this, StoriesListActivity.class);
        startActivity(intent);
    }
    
    private void handleLogout() {
        // Navigate back to LoginActivity
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        startActivity(intent);
        finish(); // Close MainActivity
    }
    
    private void handleCreateGoal() {
        // Navigate to CalendarActivity
        Intent intent = new Intent(MainActivity.this, CalendarActivity.class);
        startActivity(intent);
    }
}
