package com.example.myapplication;

import android.os.Bundle;
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
        
        // Configure WebView
        calendarWebView.getSettings().setDefaultTextEncodingName("utf-8");
        
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
        html.append(".empty { background-color: #2c2c2c; border: none; cursor: default; }");
        html.append(".empty:hover { background-color: #2c2c2c; }");
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
        
        int day = 1;
        while (day <= daysInMonth) {
            html.append("<tr>");
            for (int i = 0; i < 7; i++) {
                if ((day == 1 && i < firstDayOfWeek) || day > daysInMonth) {
                    html.append("<td class='empty'></td>");
                } else {
                    final int dayNum = day;
                    html.append("<td onclick='selectDate(").append(dayNum).append(")'>")
                        .append(day).append("</td>");
                    day++;
                }
            }
            html.append("</tr>");
        }
        
        html.append("</table>");
        html.append("<script>");
        html.append("function selectDate(day) {");
        html.append("  alert('Selected: ' + day);");
        html.append("}");
        html.append("</script>");
        html.append("</body></html>");
        
        return html.toString();
    }
    
    private void handleBack() {
        finish();
    }
}
