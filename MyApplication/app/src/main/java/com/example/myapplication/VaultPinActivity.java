package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import java.security.MessageDigest;

public class VaultPinActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "vault_pin_prefs";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_RECOVERY_QUESTION = "recovery_question";
    private static final String KEY_RECOVERY_ANSWER_HASH = "recovery_answer_hash";
    private static final String KEY_RECOVERY_CASE_SENSITIVE = "recovery_case_sensitive";
    private static final int PIN_LENGTH = 6;

    private StringBuilder currentPin = new StringBuilder();
    private ImageView[] dotViews = new ImageView[PIN_LENGTH];
    private TextView titleText;
    private TextView subtitleText;
    private TextView errorText;
    private TextView forgotLink;
    private TextView unlockButton;
    private TextView backHomeButton;
    private boolean isSettingPin = false;
    private String firstPinAttempt = null;
    private boolean recoverySkipped = false;

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

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean hasPin = prefs.contains(KEY_PIN_HASH);
        isSettingPin = !hasPin;
        boolean hasRecovery = prefs.contains(KEY_RECOVERY_QUESTION);

        // Wire views
        titleText = findViewById(R.id.pinTitleText);
        subtitleText = findViewById(R.id.pinSubtitleText);
        errorText = findViewById(R.id.pinErrorText);
        forgotLink = findViewById(R.id.pinForgotLink);
        unlockButton = findViewById(R.id.pinUnlockButton);
        backHomeButton = findViewById(R.id.pinBackHome);

        dotViews[0] = findViewById(R.id.pinDot0);
        dotViews[1] = findViewById(R.id.pinDot1);
        dotViews[2] = findViewById(R.id.pinDot2);
        dotViews[3] = findViewById(R.id.pinDot3);
        dotViews[4] = findViewById(R.id.pinDot4);
        dotViews[5] = findViewById(R.id.pinDot5);

        // Show Forgot PIN link only in verify mode and if recovery was set
        if (forgotLink != null) {
            forgotLink.setVisibility(!isSettingPin && hasRecovery ? View.VISIBLE : View.GONE);
            forgotLink.setOnClickListener(v -> showRecoveryVerifyDialog());
        }

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
                if (pin.equals(firstPinAttempt)) {
                    // Match — save the PIN hash
                    String hash = hashPin(pin);
                    prefs.edit().putString(KEY_PIN_HASH, hash).apply();
                    Toast.makeText(this, "PIN set successfully!", Toast.LENGTH_SHORT).show();

                    // Prompt for recovery setup (unless skipped or already has one)
                    if (!recoverySkipped && !prefs.contains(KEY_RECOVERY_QUESTION)) {
                        showRecoverySetupDialog();
                    } else {
                        openVault();
                    }
                } else {
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

    // ─── Recovery Setup ───────────────────────────────────────

    private void showRecoverySetupDialog() {
        showRecoverySetupInternal(true); // from first-time setup → opens vault on done
    }

    private void showRecoverySetupForExistingUser() {
        showRecoverySetupInternal(false); // from existing user → opens vault on done
    }

    private void showRecoverySetupInternal(boolean isFirstTime) {
        View container = LayoutInflater.from(this).inflate(R.layout.dialog_vault_recovery, null, false);
        if (container == null) return;

        TextView title = container.findViewById(R.id.recoveryTitle);
        TextView skipBtn = container.findViewById(R.id.recoverySkip);
        TextView saveBtn = container.findViewById(R.id.recoverySave);
        TextView extraNote = container.findViewById(R.id.recoveryExtraNote);
        EditText questionInput = container.findViewById(R.id.recoveryQuestionInput);
        EditText answerInput = container.findViewById(R.id.recoveryAnswerInput);
        TextView caseToggle = container.findViewById(R.id.recoveryCaseToggle);

        if (title != null) title.setText("Set Recovery Question");
        if (questionInput != null) questionInput.setHint("e.g. What was your first pet?");
        if (answerInput != null) {
            answerInput.setHint("Your answer");
            answerInput.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        if (extraNote != null) {
            extraNote.setVisibility(isFirstTime ? View.GONE : View.VISIBLE);
            extraNote.setText("Your vault is unlocked. Set a recovery question in case you forget your PIN.");
        }

        // Case-sensitive toggle
        final boolean[] isCaseSensitive = {false};
        if (caseToggle != null) {
            caseToggle.setText("OFF");
            caseToggle.setBackgroundResource(R.drawable.bg_toggle_off);
            caseToggle.setOnClickListener(v -> {
                isCaseSensitive[0] = !isCaseSensitive[0];
                if (isCaseSensitive[0]) {
                    caseToggle.setText("ON");
                    caseToggle.setBackgroundResource(R.drawable.bg_toggle_on);
                    caseToggle.setTextColor(getColor(R.color.black));
                } else {
                    caseToggle.setText("OFF");
                    caseToggle.setBackgroundResource(R.drawable.bg_toggle_off);
                    caseToggle.setTextColor(getColor(R.color.menu_text_secondary));
                }
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });

        if (skipBtn != null) {
            skipBtn.setOnClickListener(v -> {
                recoverySkipped = true;
                dialog.dismiss();
                if (isFirstTime) {
                    launchVaultActivity();
                } else {
                    launchVaultActivity();
                }
            });
        }

        if (saveBtn != null) {
            saveBtn.setOnClickListener(v -> {
                String question = questionInput != null ? questionInput.getText().toString().trim() : "";
                String answer = answerInput != null ? answerInput.getText().toString().trim() : "";
                if (question.isEmpty() || answer.isEmpty()) {
                    Toast.makeText(this, "Please fill in both fields", Toast.LENGTH_SHORT).show();
                    return;
                }
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                String answerToStore = isCaseSensitive[0] ? answer : answer.toLowerCase();
                prefs.edit()
                        .putString(KEY_RECOVERY_QUESTION, question)
                        .putString(KEY_RECOVERY_ANSWER_HASH, hashPin(answerToStore))
                        .putBoolean(KEY_RECOVERY_CASE_SENSITIVE, isCaseSensitive[0])
                        .apply();
                String msg = "Recovery question saved" + (isCaseSensitive[0] ? " (case-sensitive)" : "");
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                launchVaultActivity();
            });
        }

        dialog.show();
    }

    // ─── Recovery Verification ────────────────────────────────

    private void showRecoveryVerifyDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String question = prefs.getString(KEY_RECOVERY_QUESTION, "");

        View container = LayoutInflater.from(this).inflate(R.layout.dialog_vault_recovery, null, false);
        if (container == null) return;

        TextView title = container.findViewById(R.id.recoveryTitle);
        EditText questionInput = container.findViewById(R.id.recoveryQuestionInput);
        EditText answerInput = container.findViewById(R.id.recoveryAnswerInput);
        TextView saveBtn = container.findViewById(R.id.recoverySave);
        View skipBtn = container.findViewById(R.id.recoverySkip);

        if (title != null) title.setText("Answer Recovery Question");
        if (questionInput != null) {
            questionInput.setText(question);
            questionInput.setEnabled(false);
            questionInput.setFocusable(false);
            questionInput.setTextColor(getColor(R.color.menu_text_primary));
        }
        if (answerInput != null) {
            answerInput.setHint("Your answer");
            answerInput.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        if (saveBtn != null) saveBtn.setText("Verify & Reset");

        // Show case-sensitivity status
        TextView caseToggle = container.findViewById(R.id.recoveryCaseToggle);
        if (caseToggle != null) {
            boolean caseSensitive = prefs.getBoolean(KEY_RECOVERY_CASE_SENSITIVE, false);
            if (caseSensitive) {
                caseToggle.setText("ON");
                caseToggle.setBackgroundResource(R.drawable.bg_toggle_on);
                caseToggle.setTextColor(getColor(R.color.black));
            } else {
                caseToggle.setText("OFF");
                caseToggle.setBackgroundResource(R.drawable.bg_toggle_off);
                caseToggle.setTextColor(getColor(R.color.menu_text_secondary));
            }
            caseToggle.setEnabled(false);
            caseToggle.setClickable(false);
        }
        if (skipBtn != null) skipBtn.setVisibility(View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });

        if (saveBtn != null) {
            saveBtn.setOnClickListener(v -> {
                String answer = answerInput != null ? answerInput.getText().toString().trim() : "";
                if (answer.isEmpty()) {
                    Toast.makeText(this, "Please enter your answer", Toast.LENGTH_SHORT).show();
                    return;
                }
                String storedAnswerHash = prefs.getString(KEY_RECOVERY_ANSWER_HASH, null);
                if (storedAnswerHash == null) {
                    Toast.makeText(this, "No recovery answer saved. Please set up recovery again.", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    return;
                }
                boolean caseSensitive = prefs.getBoolean(KEY_RECOVERY_CASE_SENSITIVE, false);
                String answerToCheck = caseSensitive ? answer : answer.toLowerCase();
                if (storedAnswerHash.equals(hashPin(answerToCheck))) {
                    Toast.makeText(this, "Answer correct! Set a new PIN", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    startPinResetFlow();
                } else {
                    Toast.makeText(this, "Wrong answer. Try again.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        dialog.show();
    }

    // ─── PIN Reset ────────────────────────────────────────────

    private void startPinResetFlow() {
        // Clear PIN + recovery in a single atomic commit
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_PIN_HASH)
                .remove(KEY_RECOVERY_QUESTION)
                .remove(KEY_RECOVERY_ANSWER_HASH)
                .remove(KEY_RECOVERY_CASE_SENSITIVE)
                .apply();

        // Reset state
        isSettingPin = true;
        firstPinAttempt = null;
        recoverySkipped = false;
        currentPin.setLength(0);
        updateDots();

        // Update UI
        if (titleText != null) titleText.setText("Set your PIN");
        if (subtitleText != null) subtitleText.setText("Create a new 6-digit PIN to secure your vault");
        if (unlockButton != null) unlockButton.setText("Continue");
        if (errorText != null) errorText.setVisibility(View.GONE);
        if (forgotLink != null) forgotLink.setVisibility(View.GONE);

        Toast.makeText(this, "Please create a new PIN", Toast.LENGTH_SHORT).show();
    }

    // ─── Helpers ──────────────────────────────────────────────

    private void openVault() {
        // If user has a PIN but no recovery question, prompt to set one
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.contains(KEY_PIN_HASH) && !prefs.contains(KEY_RECOVERY_QUESTION)) {
            showRecoverySetupForExistingUser();
            return;
        }
        launchVaultActivity();
    }

    private void launchVaultActivity() {
        Intent intent = new Intent(VaultPinActivity.this, VaultActivity.class);
        startActivity(intent);
        finish();
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
            return pin;
        }
    }
}
