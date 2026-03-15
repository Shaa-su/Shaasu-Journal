package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import android.os.Environment;
import android.net.Uri;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

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
        
        // Find export button
        Button exportButton = findViewById(R.id.exportButton);
        exportButton.setOnClickListener(v -> handleExportData());
        
        // Find import button
        Button importButton = findViewById(R.id.importButton);
        importButton.setOnClickListener(v -> handleImportData());
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
    
    private void handleExportData() {
        try {
            SharedPreferences sharedPref = getSharedPreferences("stories", MODE_PRIVATE);
            Map<String, ?> allEntries = sharedPref.getAll();
            
            if (allEntries.isEmpty()) {
                Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Create JSON object with all stories
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("export_date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            jsonObject.put("stories", new JSONObject(allEntries));
            
            // Create file in Downloads directory
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }
            
            String fileName = "shaasu_stories_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json";
            File exportFile = new File(downloadsDir, fileName);
            
            // Write JSON to file
            try (FileWriter writer = new FileWriter(exportFile)) {
                writer.write(jsonObject.toString(2)); // Pretty print with indent
            }
            
            Toast.makeText(this, "Data exported to: " + fileName, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handleImportData() {
        // Open file picker to select JSON file
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        try {
            startActivityForResult(intent, 2); // 2 = import request code
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "No file manager found", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 2 && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            if (fileUri != null) {
                importFromUri(fileUri);
            }
        }
    }
    
    private void importFromUri(Uri uri) {
        try {
            // Read JSON from selected file
            StringBuilder jsonContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getContentResolver().openInputStream(uri)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }
            }
            
            JSONObject jsonObject = new JSONObject(jsonContent.toString());
            JSONObject storiesObject = jsonObject.getJSONObject("stories");
            
            // Restore data to SharedPreferences
            SharedPreferences sharedPref = getSharedPreferences("stories", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            
            // Clear existing data first
            editor.clear();
            
            // Add all stories from import
            @SuppressWarnings("unchecked")
            java.util.Iterator<String> keys = storiesObject.keys();
            int importedCount = 0;
            while (keys.hasNext()) {
                String key = keys.next();
                String value = storiesObject.getString(key);
                editor.putString(key, value);
                importedCount++;
            }
            
            editor.apply();
            
            Toast.makeText(this, "Successfully imported " + importedCount + " stories!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
