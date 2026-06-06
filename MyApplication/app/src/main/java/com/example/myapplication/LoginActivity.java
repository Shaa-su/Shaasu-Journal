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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends AppCompatActivity {

    private EditText usernameInput;
    private EditText passwordInput;
    private Button loginButton;
    private ImageView passwordToggle;
    private View resetLink;
    private TextView loginTitle;
    private TextView loginTagline;
    private boolean isPasswordVisible = false;

    private static final String PREFS_NAME = "login_prefs";
    private static final String KEY_USERNAME_HASH = "username_hash";
    private static final String KEY_PASSWORD_HASH = "password_hash";
    private static final String KEY_REQUIRED_CORRECT = "required_correct";
    private static final String KEY_QUESTIONS = "questions";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        passwordToggle = findViewById(R.id.passwordToggle);
        resetLink = findViewById(R.id.reset);
        loginTitle = findViewById(R.id.loginTitle);
        loginTagline = findViewById(R.id.loginTagline);

        SharedPreferences prefs = LoginStore.get(this);
        boolean hasAccount = prefs.contains(KEY_USERNAME_HASH);

        // Always show app name as title
        if (loginTitle != null) loginTitle.setText("Shaasu Journal");

        if (!hasAccount) {
            // First launch — show account setup
            if (loginTagline != null) loginTagline.setText("Create your account to get started");
            if (loginButton != null) loginButton.setText("Create Account");
            loginButton.setOnClickListener(v -> handleCreateAccount());
            if (resetLink != null) resetLink.setVisibility(View.GONE);
        } else {
            // Existing user — show login
            if (loginTagline != null) loginTagline.setText("Welcome back to your journal");
            loginButton.setOnClickListener(v -> handleLogin());
            if (resetLink != null) {
                resetLink.setVisibility(View.VISIBLE);
                resetLink.setOnClickListener(v -> showRecoveryDialog());
            }
        }

        if (passwordToggle != null) {
            passwordToggle.setOnClickListener(v -> togglePasswordVisibility());
        }
    }

    // ─── Hash helper ──────────────────────────────────────────

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return input;
        }
    }

    // ─── Create Account ───────────────────────────────────────

    private void handleCreateAccount() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 3) {
            Toast.makeText(this, "Password must be at least 3 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save credentials
        SharedPreferences prefs = LoginStore.get(this);
        prefs.edit()
                .putString(KEY_USERNAME_HASH, hash(username.toLowerCase()))
                .putString(KEY_PASSWORD_HASH, hash(password))
                .apply();

        Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();

        // Show recovery setup
        showRecoverySetupDialog(true);
    }

    // ─── Login ────────────────────────────────────────────────

    private void handleLogin() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = LoginStore.get(this);
        String storedUserHash = prefs.getString(KEY_USERNAME_HASH, null);
        String storedPassHash = prefs.getString(KEY_PASSWORD_HASH, null);

        if (storedUserHash != null && storedUserHash.equals(hash(username.toLowerCase()))
                && storedPassHash != null && storedPassHash.equals(hash(password))) {
            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Invalid username or password", Toast.LENGTH_SHORT).show();
        }
    }

    // ─── Password toggle ──────────────────────────────────────

    private void togglePasswordVisibility() {
        if (passwordInput == null) return;
        int selection = passwordInput.getSelectionEnd();
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            if (passwordToggle != null) passwordToggle.setImageResource(R.drawable.ic_eye_off_simple);
        } else {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            if (passwordToggle != null) passwordToggle.setImageResource(R.drawable.ic_eye_simple);
        }
        if (selection >= 0) passwordInput.setSelection(Math.min(selection, passwordInput.getText().length()));
    }

    // ─── Recovery Setup ───────────────────────────────────────

    private static class QuestionItem {
        String question;
        String answerHash;
        boolean caseSensitive;

        QuestionItem(String question, String answerHash, boolean caseSensitive) {
            this.question = question;
            this.answerHash = answerHash;
            this.caseSensitive = caseSensitive;
        }

        JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("question", question);
            obj.put("answerHash", answerHash);
            obj.put("caseSensitive", caseSensitive);
            return obj;
        }

        static QuestionItem fromJson(JSONObject obj) throws Exception {
            return new QuestionItem(
                    obj.optString("question", ""),
                    obj.optString("answerHash", ""),
                    obj.optBoolean("caseSensitive", false)
            );
        }
    }

    private void showRecoverySetupDialog(boolean isFirstTime) {
        View container = LayoutInflater.from(this).inflate(R.layout.dialog_recovery_setup, null, false);
        if (container == null) return;

        EditText questionInput = container.findViewById(R.id.recoveryQQuestion);
        EditText answerInput = container.findViewById(R.id.recoveryQAnswer);
        TextView caseToggle = container.findViewById(R.id.recoveryQCaseToggle);
        TextView addBtn = container.findViewById(R.id.recoveryQAddBtn);
        LinearLayout questionList = container.findViewById(R.id.recoveryQList);
        TextView listLabel = container.findViewById(R.id.recoveryQListLabel);
        TextView saveBtn = container.findViewById(R.id.recoveryQSaveBtn);
        TextView requiredCount = container.findViewById(R.id.requiredCount);
        TextView requiredMinus = container.findViewById(R.id.requiredMinus);
        TextView requiredPlus = container.findViewById(R.id.requiredPlus);

        final List<QuestionItem> questions = new ArrayList<>();
        final boolean[] caseSensitive = {false};

        // Case toggle
        if (caseToggle != null) {
            caseToggle.setOnClickListener(v -> {
                caseSensitive[0] = !caseSensitive[0];
                caseToggle.setText(caseSensitive[0] ? "ON" : "OFF");
                caseToggle.setBackgroundResource(caseSensitive[0] ? R.drawable.bg_toggle_on : R.drawable.bg_toggle_off);
                caseToggle.setTextColor(caseSensitive[0] ? getColor(R.color.black) : getColor(R.color.menu_text_secondary));
            });
        }

        // Required count controls
        final int[] required = {1};
        if (requiredMinus != null) {
            requiredMinus.setOnClickListener(v -> {
                if (required[0] > 1) {
                    required[0]--;
                    requiredCount.setText(String.valueOf(required[0]));
                }
            });
        }
        if (requiredPlus != null) {
            requiredPlus.setOnClickListener(v -> {
                if (required[0] < questions.size() + 1) {
                    required[0]++;
                    requiredCount.setText(String.valueOf(required[0]));
                }
            });
        }

        // Load existing questions (not first time)
        if (!isFirstTime) {
            SharedPreferences existingPrefs = LoginStore.get(this);
            String existingRaw = existingPrefs.getString(KEY_QUESTIONS, "[]");
            try {
                JSONArray existingArr = new JSONArray(existingRaw);
                for (int i = 0; i < existingArr.length(); i++) {
                    questions.add(QuestionItem.fromJson(existingArr.getJSONObject(i)));
                }
            } catch (Exception ignored) {}
            if (!questions.isEmpty()) {
                required[0] = existingPrefs.getInt(KEY_REQUIRED_CORRECT, 1);
                if (requiredCount != null) requiredCount.setText(String.valueOf(required[0]));
            }
            refreshQuestionList(questionList, listLabel, questions, required, requiredCount, requiredPlus);
        }

        // Add question
        if (addBtn != null) {
            addBtn.setOnClickListener(v -> {
                String q = questionInput != null ? questionInput.getText().toString().trim() : "";
                String a = answerInput != null ? answerInput.getText().toString().trim() : "";
                if (q.isEmpty() || a.isEmpty()) {
                    Toast.makeText(this, "Please fill in both question and answer", Toast.LENGTH_SHORT).show();
                    return;
                }
                String answerToStore = caseSensitive[0] ? a : a.toLowerCase();
                questions.add(new QuestionItem(q, hash(answerToStore), caseSensitive[0]));

                if (questionInput != null) questionInput.setText("");
                if (answerInput != null) answerInput.setText("");
                caseSensitive[0] = false;
                if (caseToggle != null) {
                    caseToggle.setText("OFF");
                    caseToggle.setBackgroundResource(R.drawable.bg_toggle_off);
                    caseToggle.setTextColor(getColor(R.color.menu_text_secondary));
                }

                // Update list
                refreshQuestionList(questionList, listLabel, questions, required, requiredCount, requiredPlus);
                Toast.makeText(this, "Question added!", Toast.LENGTH_SHORT).show();
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

        // Save
        if (saveBtn != null) {
            saveBtn.setOnClickListener(v -> {
                if (questions.isEmpty()) {
                    Toast.makeText(this, "Please add at least 1 question", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (required[0] > questions.size()) {
                    Toast.makeText(this, "Required correct answers can't exceed total questions", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Save to prefs
                try {
                    JSONArray arr = new JSONArray();
                    for (QuestionItem qi : questions) arr.put(qi.toJson());
                    SharedPreferences prefs = LoginStore.get(this);
                    prefs.edit()
                            .putString(KEY_QUESTIONS, arr.toString())
                            .putInt(KEY_REQUIRED_CORRECT, required[0])
                            .apply();
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to save questions", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(this, "Recovery setup complete!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();

                if (isFirstTime) {
                    // First-time setup → navigate to main
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                }
                // Otherwise (reset flow) → stay on login screen
            });
        }

        dialog.show();
    }

    private void refreshQuestionList(LinearLayout list, TextView label,
                                      List<QuestionItem> questions, int[] required,
                                      TextView requiredCount, TextView requiredPlus) {
        if (list == null) return;
        list.removeAllViews();
        if (label != null) label.setVisibility(questions.isEmpty() ? View.GONE : View.VISIBLE);

        for (int i = 0; i < questions.size(); i++) {
            final int index = i;
            View row = LayoutInflater.from(this).inflate(R.layout.item_recovery_question_row, list, false);
            if (row == null) continue;

            TextView qText = row.findViewById(R.id.recoveryRowQuestion);
            TextView removeBtn = row.findViewById(R.id.recoveryRowRemove);

            if (qText != null) {
                String label2 = (i + 1) + ". " + questions.get(i).question;
                if (questions.get(i).caseSensitive) label2 += " [CS]";
                qText.setText(label2);
            }
            if (removeBtn != null) {
                removeBtn.setOnClickListener(v -> {
                    questions.remove(index);
                    refreshQuestionList(list, label, questions, required, requiredCount, requiredPlus);
                    // Update required max
                    if (required[0] > questions.size() && !questions.isEmpty()) {
                        required[0] = questions.size();
                        if (requiredCount != null) requiredCount.setText(String.valueOf(required[0]));
                    }
                    if (requiredPlus != null) {
                        requiredPlus.setEnabled(required[0] < questions.size() + 1);
                    }
                });
            }
            list.addView(row);
        }

        // Update required max
        if (required[0] > questions.size() && !questions.isEmpty()) {
            required[0] = questions.size();
            if (requiredCount != null) requiredCount.setText(String.valueOf(required[0]));
        }
        if (requiredPlus != null) {
            requiredPlus.setEnabled(required[0] < questions.size() + 1);
        }
    }

    // ─── Recovery Answer ──────────────────────────────────────

    private void showRecoveryDialog() {
        SharedPreferences prefs = LoginStore.get(this);
        String questionsRaw = prefs.getString(KEY_QUESTIONS, "[]");
        int requiredCorrect = prefs.getInt(KEY_REQUIRED_CORRECT, 1);

        final List<QuestionItem> questions = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(questionsRaw);
            for (int i = 0; i < arr.length(); i++) {
                questions.add(QuestionItem.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            Toast.makeText(this, "No recovery questions found", Toast.LENGTH_SHORT).show();
            return;
        }

        if (questions.isEmpty()) {
            Toast.makeText(this, "No recovery questions set up", Toast.LENGTH_SHORT).show();
            return;
        }

        View container = LayoutInflater.from(this).inflate(R.layout.dialog_recovery_answer, null, false);
        if (container == null) return;

        TextView subtitle = container.findViewById(R.id.recoverSubtitle);
        LinearLayout questionsList = container.findViewById(R.id.recoverQuestionsList);
        TextView errorText = container.findViewById(R.id.recoverError);
        TextView cancelBtn = container.findViewById(R.id.recoverCancel);
        TextView verifyBtn = container.findViewById(R.id.recoverVerify);

        if (subtitle != null) {
            subtitle.setText("Answer at least " + requiredCorrect + " of " + questions.size() + " questions correctly to reset your password.");
        }

        final EditText[] answerInputs = new EditText[questions.size()];

        if (questionsList != null) {
            for (int i = 0; i < questions.size(); i++) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_recover_question_row, questionsList, false);
                if (row == null) continue;

                TextView qLabel = row.findViewById(R.id.recoverQNumber);
                TextView qText = row.findViewById(R.id.recoverQText);
                EditText aInput = row.findViewById(R.id.recoverQAnswer);
                TextView csLabel = row.findViewById(R.id.recoverQCaseLabel);

                if (qLabel != null) qLabel.setText("Q" + (i + 1) + ":");
                if (qText != null) qText.setText(questions.get(i).question);
                if (aInput != null) {
                    aInput.setHint("Your answer");
                    answerInputs[i] = aInput;
                }
                if (csLabel != null) {
                    csLabel.setVisibility(questions.get(i).caseSensitive ? View.VISIBLE : View.GONE);
                }

                questionsList.addView(row);
            }
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

        if (cancelBtn != null) cancelBtn.setOnClickListener(v -> dialog.dismiss());

        if (verifyBtn != null) {
            verifyBtn.setOnClickListener(v -> {
                int correct = 0;
                for (int i = 0; i < questions.size(); i++) {
                    String userAnswer = answerInputs[i] != null ? answerInputs[i].getText().toString().trim() : "";
                    if (userAnswer.isEmpty()) continue;
                    QuestionItem qi = questions.get(i);
                    String check = qi.caseSensitive ? userAnswer : userAnswer.toLowerCase();
                    if (hash(check).equals(qi.answerHash)) {
                        correct++;
                    }
                }

                if (correct >= requiredCorrect) {
                    Toast.makeText(this, "Verified! (" + correct + "/" + questions.size() + " correct)", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    showResetPasswordDialog();
                } else {
                    if (errorText != null) {
                        errorText.setText("Only " + correct + "/" + requiredCorrect + " correct. Try again.");
                        errorText.setVisibility(View.VISIBLE);
                    }
                }
            });
        }

        dialog.show();
    }

    // ─── Reset Password ───────────────────────────────────────

    private void showResetPasswordDialog() {
        View container = LayoutInflater.from(this).inflate(R.layout.dialog_reset_password, null, false);
        if (container == null) return;

        EditText newUsernameInput = container.findViewById(R.id.resetNewUsername);
        EditText newPassInput = container.findViewById(R.id.resetNewPassword);
        EditText confirmPassInput = container.findViewById(R.id.resetConfirmPassword);
        TextView cancelBtn = container.findViewById(R.id.resetCancel);
        TextView saveBtn = container.findViewById(R.id.resetSave);

        // Pre-fill current username
        SharedPreferences prefs = LoginStore.get(this);
        // We don't store plaintext username, so leave it empty for the user to type

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });

        if (cancelBtn != null) cancelBtn.setOnClickListener(v -> dialog.dismiss());

        if (saveBtn != null) {
            saveBtn.setOnClickListener(v -> {
                String newUsername = newUsernameInput != null ? newUsernameInput.getText().toString().trim() : "";
                String newPass = newPassInput != null ? newPassInput.getText().toString().trim() : "";
                String confirm = confirmPassInput != null ? confirmPassInput.getText().toString().trim() : "";

                if (newUsername.isEmpty() && newPass.isEmpty()) {
                    Toast.makeText(this, "Please fill in at least one field", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!newPass.equals(confirm)) {
                    Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!newPass.isEmpty() && newPass.length() < 3) {
                    Toast.makeText(this, "Password must be at least 3 characters", Toast.LENGTH_SHORT).show();
                    return;
                }

                SharedPreferences.Editor editor = prefs.edit();
                if (!newUsername.isEmpty()) {
                    editor.putString(KEY_USERNAME_HASH, hash(newUsername.toLowerCase()));
                }
                if (!newPass.isEmpty()) {
                    editor.putString(KEY_PASSWORD_HASH, hash(newPass));
                }
                editor.commit();

                Toast.makeText(this, "Credentials updated!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                // Now let user update their recovery questions
                showRecoverySetupDialog(false);
            });
        }

        dialog.show();
    }
}
