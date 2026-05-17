package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.net.Uri;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final int IMPORT_REQUEST_CODE = 2;
    private static final int EXPORT_REQUEST_CODE = 3;

    private char[] pendingExportPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // One-time migration from legacy plaintext prefs to encrypted prefs.
        StoryStore.migrateIfNeeded(this);
        ReminderScheduler.rescheduleAll(this);

        setContentView(R.layout.activity_main);
        
        // Logout (top-right text)
        TextView logoutButton = findViewById(R.id.logoutButton);
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> handleLogout());
        }

        // Cards
        View createGoalCard = findViewById(R.id.creategoal);
        if (createGoalCard != null) {
            createGoalCard.setOnClickListener(v -> handleCreateGoal());
        }

        View storiesCard = findViewById(R.id.existingstory);
        if (storiesCard != null) {
            storiesCard.setOnClickListener(v -> handleViewStories());
        }

        View exportCard = findViewById(R.id.exportButton);
        if (exportCard != null) {
            exportCard.setOnClickListener(v -> handleExportData());
        }

        View importCard = findViewById(R.id.importButton);
        if (importCard != null) {
            importCard.setOnClickListener(v -> handleImportData());
        }
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
        Map<String, ?> allEntries = StoryStore.get(this).getAll();
        boolean hasStories = !allEntries.isEmpty();
        boolean hasReminders = !ReminderStore.getAll(this).isEmpty();

        if (!hasStories && !hasReminders) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        promptForPassword("Set a backup password", password -> {
            if (password == null || password.length == 0) {
                Toast.makeText(this, "Password required", Toast.LENGTH_SHORT).show();
                return;
            }

            // Store until the user picks a destination Uri.
            pendingExportPassword = password;

            String fileName = "shaasu_stories_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json";

            // Use Storage Access Framework so export works reliably on modern Android.
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);

            try {
                startActivityForResult(intent, EXPORT_REQUEST_CODE);
            } catch (android.content.ActivityNotFoundException e) {
                Arrays.fill(pendingExportPassword, '\0');
                pendingExportPassword = null;
                Toast.makeText(this, "No file picker found", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void handleImportData() {
        // Open file picker to select JSON file
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        try {
            startActivityForResult(intent, IMPORT_REQUEST_CODE);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "No file manager found", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == EXPORT_REQUEST_CODE && resultCode != RESULT_OK) {
            if (pendingExportPassword != null) {
                Arrays.fill(pendingExportPassword, '\0');
                pendingExportPassword = null;
            }
        }

        if (resultCode != RESULT_OK || data == null) return;
        Uri fileUri = data.getData();
        if (fileUri == null) return;

        if (requestCode == IMPORT_REQUEST_CODE) {
            importFromUri(fileUri);
        } else if (requestCode == EXPORT_REQUEST_CODE) {
            if (pendingExportPassword == null || pendingExportPassword.length == 0) {
                Toast.makeText(this, "Missing export password", Toast.LENGTH_SHORT).show();
                return;
            }
            exportToUriEncrypted(fileUri, pendingExportPassword);
            Arrays.fill(pendingExportPassword, '\0');
            pendingExportPassword = null;
        }
    }

    private void exportToUriEncrypted(Uri uri, char[] password) {
        try {
            Map<String, ?> allEntries = StoryStore.get(this).getAll();

            JSONObject storiesJson = new JSONObject();
            for (Map.Entry<String, ?> e : allEntries.entrySet()) {
                if (e.getValue() instanceof String) {
                    storiesJson.put(e.getKey(), (String) e.getValue());
                }
            }

            org.json.JSONArray remindersJson = new org.json.JSONArray();
            for (Reminder r : ReminderStore.getAll(this)) {
                if (r != null) remindersJson.put(r.toJson());
            }

            if (storiesJson.length() == 0 && remindersJson.length() == 0) {
                Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject root = new JSONObject();
            root.put("export_date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            if (storiesJson.length() > 0) {
                root.put("stories", storiesJson);
            }
            if (remindersJson.length() > 0) {
                root.put("reminders", remindersJson);
            }

            JSONObject envelope = ExportCrypto.encryptToEnvelope(root.toString(), password);

            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IllegalStateException("Unable to open export destination");
                os.write(envelope.toString(2).getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            Toast.makeText(this, "Encrypted export successful", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void importFromUri(Uri uri) {
        try {
            JSONObject jsonObject = readJsonObjectFromUri(uri);

            if (ExportCrypto.looksEncrypted(jsonObject)) {
                promptForPassword("Enter backup password", password -> {
                    if (password == null || password.length == 0) {
                        Toast.makeText(this, "Password required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        String plaintext = ExportCrypto.decryptEnvelope(jsonObject, password);
                        JSONObject plainRoot = new JSONObject(plaintext);
                        ImportResult result = importFromPlainRoot(plainRoot);
                        Toast.makeText(this, formatImportMessage(result), Toast.LENGTH_LONG).show();
                    } catch (GeneralSecurityException sec) {
                        Toast.makeText(this, "Wrong password or corrupted file", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    } finally {
                        Arrays.fill(password, '\0');
                    }
                });
                return;
            }

            ImportResult result = importFromPlainRoot(jsonObject);
            Toast.makeText(this, formatImportMessage(result), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private JSONObject readJsonObjectFromUri(Uri uri) throws Exception {
        StringBuilder jsonContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
        }
        return new JSONObject(jsonContent.toString());
    }

    private ImportResult importFromPlainRoot(JSONObject root) throws Exception {
        int importedStories = 0;
        int importedReminders = 0;

        if (root.has("stories")) {
            JSONObject storiesObject = root.getJSONObject("stories");

            // Validate first into a temp map (so a bad file can't wipe existing data)
            HashMap<String, String> toImport = new HashMap<>();
            Iterator<String> keys = storiesObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object val = storiesObject.get(key);
                if (!(val instanceof String)) {
                    throw new IllegalArgumentException("Invalid story value for key: " + key);
                }
                toImport.put(key, (String) val);
                importedStories++;
            }

            // Replace existing only after validation
            android.content.SharedPreferences sharedPref = StoryStore.get(this);
            android.content.SharedPreferences.Editor editor = sharedPref.edit();
            editor.clear();
            for (Map.Entry<String, String> e : toImport.entrySet()) {
                editor.putString(e.getKey(), e.getValue());
            }
            editor.apply();
        }

        if (root.has("reminders")) {
            org.json.JSONArray remindersArray = root.getJSONArray("reminders");
            java.util.List<Reminder> reminders = new java.util.ArrayList<>();
            for (int i = 0; i < remindersArray.length(); i++) {
                Object obj = remindersArray.get(i);
                if (!(obj instanceof org.json.JSONObject)) {
                    throw new IllegalArgumentException("Invalid reminder entry");
                }
                Reminder r = Reminder.fromJson((org.json.JSONObject) obj);
                if (r == null) {
                    throw new IllegalArgumentException("Invalid reminder entry");
                }
                reminders.add(r);
            }

            ReminderStore.clear(this);
            for (Reminder r : reminders) {
                ReminderStore.put(this, r);
                importedReminders++;
            }
            ReminderScheduler.rescheduleAll(this);
        }

        return new ImportResult(importedStories, importedReminders);
    }

    private String formatImportMessage(ImportResult result) {
        if (result == null) return "Import complete";
        if (result.reminders == 0) {
            return "Successfully imported " + result.stories + " stories!";
        }
        return "Successfully imported " + result.stories + " stories and " + result.reminders + " reminders!";
    }

    private static final class ImportResult {
        final int stories;
        final int reminders;

        ImportResult(int stories, int reminders) {
            this.stories = stories;
            this.reminders = reminders;
        }
    }

    private interface PasswordCallback {
        void onPassword(char[] password);
    }

    private void promptForPassword(String title, PasswordCallback callback) {
        EditText input = new EditText(this);
        input.setHint("Password");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("OK", (d, which) -> {
                    String pw = input.getText() != null ? input.getText().toString() : "";
                    callback.onPassword(pw.toCharArray());
                })
                .setNegativeButton("Cancel", (d, which) -> callback.onPassword(null))
                .show();
    }
}
