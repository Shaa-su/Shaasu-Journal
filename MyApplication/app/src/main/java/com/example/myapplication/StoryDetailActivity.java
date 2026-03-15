package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Base64;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StoryDetailActivity extends AppCompatActivity {

    private TextView dateDisplayText;
    private EditText titleEditText;
    private EditText storyEditText;
    private LinearLayout goalsContainer;
    private Button addGoalButton;
    private Button addImageButton;
    private Button saveButton;
    private Button cancelButton;
    
    private int selectedDay;
    private int selectedMonth;
    private int selectedYear;
    
    private List<GoalItem> goalItems = new ArrayList<>();
    
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int MAX_IMAGE_WIDTH = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_story_detail);
        
        dateDisplayText = findViewById(R.id.dateDisplayText);
        titleEditText = findViewById(R.id.titleEditText);
        storyEditText = findViewById(R.id.storyEditText);
        goalsContainer = findViewById(R.id.goalsContainer);
        addGoalButton = findViewById(R.id.addGoalButton);
        addImageButton = findViewById(R.id.addImageButton);
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
        
        // Add Image button click listener
        addImageButton.setOnClickListener(v -> openImagePicker());
        
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
    
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                addImage(imageUri);
            }
        }
    }
    
    private void addImage(Uri imageUri) {
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
            if (bitmap != null) {
                // Compress/resize the bitmap
                Bitmap compressedBitmap = compressImage(bitmap);
                
                // Get current cursor position
                int cursorPosition = storyEditText.getSelectionStart();
                if (cursorPosition < 0) {
                    cursorPosition = storyEditText.getText().length();
                }
                
                // Get current SpannableStringBuilder from EditText
                SpannableStringBuilder spannableBuilder = new SpannableStringBuilder(storyEditText.getText());
                
                // Create ImageSpan with the compressed bitmap
                ImageSpan imageSpan = new ImageSpan(this, compressedBitmap);
                
                // Insert a placeholder character for the image
                spannableBuilder.insert(cursorPosition, "\u0001");
                
                // Apply ImageSpan to the placeholder
                spannableBuilder.setSpan(imageSpan, cursorPosition, cursorPosition + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                
                // Set the modified SpannableStringBuilder to EditText
                storyEditText.setText(spannableBuilder);
                
                // Move cursor after the inserted image
                storyEditText.setSelection(cursorPosition + 1);
                
                Toast.makeText(this, "Image inserted at cursor position", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error loading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private Bitmap compressImage(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        if (width <= MAX_IMAGE_WIDTH) {
            return bitmap;
        }
        
        // Calculate new height maintaining aspect ratio
        float ratio = (float) height / width;
        int newHeight = (int) (MAX_IMAGE_WIDTH * ratio);
        
        return Bitmap.createScaledBitmap(bitmap, MAX_IMAGE_WIDTH, newHeight, true);
    }
    
    private String encodeImageToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }
    
    private Bitmap decodeImageFromBase64(String encodedString) {
        try {
            byte[] decodedString = Base64.decode(encodedString, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    private void saveStory() {
        String title = titleEditText.getText().toString();
        
        // Extract text and inline images from SpannableStringBuilder
        Spanned spannedStory = (Spanned) storyEditText.getText();
        String storyWithPlaceholders = extractStoryWithImagePlaceholders(spannedStory);
        
        if (storyWithPlaceholders.isEmpty() && goalItems.isEmpty()) {
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
        String storyData = title + "||" + storyWithPlaceholders + "||" + goalsData.toString();
        
        editor.putString(dateKey, storyData);
        editor.apply();
        
        Toast.makeText(this, "Story saved!", Toast.LENGTH_SHORT).show();
        finish();
    }
    
    private String extractStoryWithImagePlaceholders(Spanned spanned) {
        StringBuilder result = new StringBuilder();
        ImageSpan[] imageSpans = spanned.getSpans(0, spanned.length(), ImageSpan.class);
        
        int lastIndex = 0;
        for (ImageSpan imageSpan : imageSpans) {
            int start = spanned.getSpanStart(imageSpan);
            int end = spanned.getSpanEnd(imageSpan);
            
            // Add text before image
            result.append(spanned.subSequence(lastIndex, start));
            
            // Get the bitmap from the ImageSpan and encode it
            Bitmap bitmap = getImageBitmapFromImageSpan(imageSpan);
            if (bitmap != null) {
                String encodedImage = encodeImageToBase64(bitmap);
                result.append("[IMG:").append(encodedImage).append("]");
            }
            
            lastIndex = end;
        }
        
        // Add remaining text after last image
        result.append(spanned.subSequence(lastIndex, spanned.length()));
        
        return result.toString();
    }
    
    private Bitmap getImageBitmapFromImageSpan(ImageSpan imageSpan) {
        try {
            // ImageSpan stores the bitmap in its drawable
            return ((android.graphics.drawable.BitmapDrawable) imageSpan.getDrawable()).getBitmap();
        } catch (Exception e) {
            return null;
        }
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
                // Parse story with inline images
                SpannableStringBuilder spannableStory = parseStoryWithImages(parts[1]);
                storyEditText.setText(spannableStory);
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
    
    private SpannableStringBuilder parseStoryWithImages(String storyWithPlaceholders) {
        SpannableStringBuilder result = new SpannableStringBuilder();
        
        // Pattern to match [IMG:base64data]
        Pattern pattern = Pattern.compile("\\[IMG:(.+?)\\]");
        Matcher matcher = pattern.matcher(storyWithPlaceholders);
        
        int lastIndex = 0;
        while (matcher.find()) {
            // Add text before image
            result.append(storyWithPlaceholders.subSequence(lastIndex, matcher.start()));
            
            // Decode and insert image
            String encodedImage = matcher.group(1);
            Bitmap bitmap = decodeImageFromBase64(encodedImage);
            if (bitmap != null) {
                int insertPosition = result.length();
                result.insert(insertPosition, "\u0001");
                
                Bitmap compressedBitmap = compressImage(bitmap);
                ImageSpan imageSpan = new ImageSpan(this, compressedBitmap);
                result.setSpan(imageSpan, insertPosition, insertPosition + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            
            lastIndex = matcher.end();
        }
        
        // Add remaining text after last image
        result.append(storyWithPlaceholders.subSequence(lastIndex, storyWithPlaceholders.length()));
        
        return result;
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
