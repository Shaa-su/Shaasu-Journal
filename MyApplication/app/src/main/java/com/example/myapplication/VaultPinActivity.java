package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import java.security.MessageDigest;

public class VaultPinActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "vault_pin_prefs";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final int PIN_LENGTH = 6;

    private StringBuilder currentPin = new StringBuilder();
    private ImageView[] dotViews = new ImageView[PIN_LENGTH];
    private TextView titleText;
    private TextView subtitleText;
    private TextView errorText;
    private TextView unlockButton;
    private TextView backHomeButton;
    private boolean isSettingPin = false;
    private String firstPinAttempt = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vault_pin);

        // Edge-to-edge insets
        View root = findViewById(android.R.id.content);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                return insets;
            });
            ViewCompat.requestApplyInsets(root);
        }

        // Check if PIN already exists
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean hasPin = prefs.contains(KEY_PIN_HASH);
        isSettingPin = !hasPin;

        // Wire views
        titleText = findViewById(R.id.pinTitleText);
        subtitleText = findViewById(R.id.pinSubtitleText);
        errorText = findViewById(R.id.pinErrorText);
        unlockButton = findViewById(R.id.pinUnlockButton);
        backHomeButton = findViewById(R.id.pinBackHome);

        dotViews[0] = findViewById(R.id.pinDot0);
        dotViews[1] = findViewById(R.id.pinDot1);
        dotViews[2] = findViewById(R.id.pinDot2);
        dotViews[3] = findViewById(R.id.pinDot3);
        dotViews[4] = findViewById(R.id.pinDot4);
        dotViews[5] = findViewById(R.id.pinDot5);

        // Update UI based on mode
        if (isSettingPin) {
            titleText.setText("Set your PIN");
            subtitleText.setText("Create a 6-digit PIN to secure your vault");
            unlockButton.setText("Continue");
        } else {
            titleText.setText("Enter your PIN");
            subtitleText.setText("Unlock to access your saved passwords");
            unlockButton.setText("Unlock");
        }

        // Keypad click listeners
        int[] keyIds = {
            R.id.pinKey0, R.id.pinKey1, R.id.pinKey2, R.id.pinKey3,
            R.id.pinKey4, R.id.pinKey5, R.id.pinKey6, R.id.pinKey7,
            R.id.pinKey8, R.id.pinKey9
        };
        for (int id : keyIds) {
            TextView key = findViewById(id);
            if (key != null) {
                key.setOnClickListener(v -> {
                    String digit = ((TextView) v).getText().toString();
                    onDigitPressed(digit);
                });
            }
        }

        // Backspace
        View backKey = findViewById(R.id.pinKeyBack);
        if (backKey != null) {
            backKey.setOnClickListener(v -> onBackspacePressed());
        }

        // Unlock / Continue
        if (unlockButton != null) {
            unlockButton.setOnClickListener(v -> onUnlockPressed());
        }

        // Back to Home
        if (backHomeButton != null) {
            backHomeButton.setOnClickListener(v -> finish());
        }

        updateDots();
    }

    private void onDigitPressed(String digit) {
        if (currentPin.length() < PIN_LENGTH) {
            // Clear error on new input
            if (errorText != null) errorText.setVisibility(View.GONE);
            currentPin.append(digit);
            updateDots();
        }
    }

    private void onBackspacePressed() {
        if (currentPin.length() > 0) {
            currentPin.deleteCharAt(currentPin.length() - 1);
            updateDots();
        }
    }

    private void onUnlockPressed() {
        String pin = currentPin.toString();

        if (pin.length() < PIN_LENGTH) {
            Toast.makeText(this, "Please enter " + PIN_LENGTH + " digits", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        if (isSettingPin) {
            // First entry — save or confirm
            if (firstPinAttempt == null) {
                // First entry: save and ask to confirm
                firstPinAttempt = pin;
                currentPin.setLength(0);
                updateDots();
                titleText.setText("Confirm your PIN");
                subtitleText.setText("Re-enter your 6-digit PIN");
                unlockButton.setText("Confirm");
                Toast.makeText(this, "Now confirm your PIN", Toast.LENGTH_SHORT).show();
            } else {
                // Confirmation entry
                if (pin.equals(firstPinAttempt)) {
                    // Match — save the PIN hash
                    String hash = hashPin(pin);
                    prefs.edit().putString(KEY_PIN_HASH, hash).apply();
                    Toast.makeText(this, "PIN set successfully!", Toast.LENGTH_SHORT).show();
                    openVault();
                } else {
                    // Mismatch — reset
                    if (errorText != null) {
                        errorText.setText("PINs don't match. Try again.");
                        errorText.setVisibility(View.VISIBLE);
                    }
                    firstPinAttempt = null;
                    currentPin.setLength(0);
                    updateDots();
                    titleText.setText("Set your PIN");
                    subtitleText.setText("Create a 6-digit PIN to secure your vault");
                    unlockButton.setText("Continue");
                }
            }
        } else {
            // Verify
            String storedHash = prefs.getString(KEY_PIN_HASH, null);
            if (storedHash != null && storedHash.equals(hashPin(pin))) {
                openVault();
            } else {
                if (errorText != null) {
                    errorText.setText("Wrong PIN. Try again.");
                    errorText.setVisibility(View.VISIBLE);
                }
                currentPin.setLength(0);
                updateDots();
            }
        }
    }

    private void openVault() {
        Intent intent = new Intent(VaultPinActivity.this, VaultActivity.class);
        startActivity(intent);
        finish(); // Remove PIN screen from back stack
    }

    private void updateDots() {
        int len = currentPin.length();
        for (int i = 0; i < PIN_LENGTH; i++) {
            if (dotViews[i] != null) {
                dotViews[i].setImageResource(
                        i < len ? R.drawable.bg_pin_dot_filled : R.drawable.bg_pin_dot_empty
                );
            }
        }
    }

    private String hashPin(String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(pin.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            // Fallback to plaintext (shouldn't happen)
            return pin;
        }
    }
}
