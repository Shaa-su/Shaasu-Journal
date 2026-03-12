package com.example.myapplication;

import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class CalendarActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);
        
        // Find back button
        Button backButton = findViewById(R.id.backButton);
        
        // Set up back button click listener
        backButton.setOnClickListener(v -> handleBack());
    }
    
    private void handleBack() {
        // Close CalendarActivity and go back to MainActivity
        finish();
    }
}
