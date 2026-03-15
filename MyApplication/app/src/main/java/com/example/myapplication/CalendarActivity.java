package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class CalendarActivity extends AppCompatActivity {
    
    private WebView calendarWebView;
    private TextView monthYearText;
    private Calendar currentCalendar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);
        
        // Find views
        calendarWebView = findViewById(R.id.calendarWebView);
        monthYearText = findViewById(R.id.monthYearText);
        Button backButton = findViewById(R.id.backButton);
        Button prevButton = findViewById(R.id.prevButton);
        Button nextButton = findViewById(R.id.nextButton);
        Button todayButton = findViewById(R.id.todayButton);
        
        // Configure WebView
        calendarWebView.getSettings().setDefaultTextEncodingName("utf-8");
        calendarWebView.getSettings().setJavaScriptEnabled(true);
        calendarWebView.addJavascriptInterface(new DatePickerInterface(), "Android");
        
        // Initialize calendar
        currentCalendar = Calendar.getInstance();
        
        // Set up button listeners
        backButton.setOnClickListener(v -> handleBack());
        prevButton.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            updateCalendar();
        });
        nextButton.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            updateCalendar();
        });
        todayButton.setOnClickListener(v -> {
            android.util.Log.d("CalendarActivity", "TODAY button clicked");
            Calendar today = Calendar.getInstance();
            Intent intent = new Intent(CalendarActivity.this, StoryDetailActivity.class);
            intent.putExtra("day", today.get(Calendar.DAY_OF_MONTH));
            intent.putExtra("month", today.get(Calendar.MONTH));
            intent.putExtra("year", today.get(Calendar.YEAR));
            startActivityForResult(intent, 1);
        });
        
        // Display initial calendar
        updateCalendar();
    }
    
    private void updateCalendar() {
        // Update month/year display
        String[] months = {"January", "February", "March", "April", "May", "June",
                          "July", "August", "September", "October", "November", "December"};
        int month = currentCalendar.get(Calendar.MONTH);
        int year = currentCalendar.get(Calendar.YEAR);
        monthYearText.setText(months[month] + " " + year);
        
        // Generate HTML calendar
        String html = generateCalendarHTML();
        calendarWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }
    
    private String generateCalendarHTML() {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset='utf-8'><style>");
        html.append("body { margin: 0; padding: 0; font-family: Arial, sans-serif; background-color: #2c2c2c; }");
        html.append("table { width: 100%; border-collapse: collapse; }");
        html.append("th, td { border: 1px solid #555; text-align: center; padding: 12px; height: 50px; color: #fff; }");
        html.append("th { background-color: #1c1c1c; font-weight: bold; height: 40px; padding: 8px; }");
        html.append("td { background-color: #404040; cursor: pointer; }");
        html.append("td:hover { background-color: #505050; }");
        html.append(".empty { background-color: transparent; border: 1px solid #555; cursor: default; }");
        html.append(".empty:hover { background-color: transparent; }");
        html.append(".saved { background-color: #2a6d3a; }");
        html.append(".saved:hover { background-color: #348a4a; }");
        html.append("</style></head><body>");
        
        html.append("<table>");
        
        // Header row
        html.append("<tr>");
        String[] dayNames = {"S", "M", "T", "W", "T", "F", "S"};
        for (String day : dayNames) {
            html.append("<th>").append(day).append("</th>");
        }
        html.append("</tr>");
        
        // Get first day and days in month
        Calendar tempCalendar = (Calendar) currentCalendar.clone();
        tempCalendar.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = tempCalendar.get(Calendar.DAY_OF_WEEK) - 1; // 0 = Sunday
        int daysInMonth = currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        int month = currentCalendar.get(Calendar.MONTH);
        int year = currentCalendar.get(Calendar.YEAR);
        
        int cellCount = 0;
        int day = 1;
        
        html.append("<tr>");
        
        // Add empty cells before month starts
        for (int i = 0; i < firstDayOfWeek; i++) {
            html.append("<td class='empty'></td>");
            cellCount++;
        }
        
        // Add days of the month
        SharedPreferences sharedPref = getSharedPreferences("stories", MODE_PRIVATE);
        while (day <= daysInMonth) {
            if (cellCount == 7) {
                html.append("</tr><tr>");
                cellCount = 0;
            }
            // Check if this day has a saved story
            String dateKey = year + "-" + String.format("%02d", month + 1) + "-" + String.format("%02d", day);
            boolean hasSavedStory = sharedPref.contains(dateKey);
            String cssClass = hasSavedStory ? "saved" : "";
            
            html.append("<td class='").append(cssClass).append("' onclick='Android.openStoryEditor(").append(day).append(")')>")
                .append(day).append("</td>");
            day++;
            cellCount++;
        }
        
        // Fill remaining cells
        while (cellCount < 7) {
            html.append("<td class='empty'></td>");
            cellCount++;
        }
        
        html.append("</tr>");
        
        html.append("</table>");
        html.append("</body></html>");
        
        return html.toString();
    }
    
    private void handleBack() {
        finish();
    }
    
    // JavaScript interface for handling date clicks
    private class DatePickerInterface {
        @JavascriptInterface
        public void openStoryEditor(int day) {
            Intent intent = new Intent(CalendarActivity.this, StoryDetailActivity.class);
            intent.putExtra("day", day);
            intent.putExtra("month", currentCalendar.get(Calendar.MONTH));
            intent.putExtra("year", currentCalendar.get(Calendar.YEAR));
            startActivityForResult(intent, 1);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            // Refresh calendar when returning from StoryDetailActivity
            updateCalendar();
        }
    }
}
