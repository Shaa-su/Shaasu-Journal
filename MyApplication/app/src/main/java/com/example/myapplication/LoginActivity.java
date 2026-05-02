package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    
    private EditText usernameInput;
    private EditText passwordInput;
    private Button loginButton;
    private ImageView passwordToggle;
    private View resetLink;

    private boolean isPasswordVisible = false;
    
    // Hardcoded credentials
    private static final String CORRECT_USERNAME = "shaasu";
    private static final String CORRECT_PASSWORD = "123";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        // Initialize views
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        passwordToggle = findViewById(R.id.passwordToggle);
        resetLink = findViewById(R.id.reset);
        
        // Set up login button click listener
        loginButton.setOnClickListener(v -> handleLogin());

        if (passwordToggle != null) {
            passwordToggle.setOnClickListener(v -> togglePasswordVisibility());
        }

        if (resetLink != null) {
            resetLink.setOnClickListener(v -> Toast.makeText(this, "Reset Password: coming soon", Toast.LENGTH_SHORT).show());
        }
    }

    private void togglePasswordVisibility() {
        if (passwordInput == null) return;

        int selection = passwordInput.getSelectionEnd();
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            if (passwordToggle != null) {
                passwordToggle.setImageResource(R.drawable.ic_eye_off_simple);
            }
        } else {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            if (passwordToggle != null) {
                passwordToggle.setImageResource(R.drawable.ic_eye_simple);
            }
        }

        // Keep cursor position
        if (selection >= 0) {
            passwordInput.setSelection(Math.min(selection, passwordInput.getText().length()));
        }
    }
    
    private void handleLogin() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        
        // Validate username (case-insensitive) and password
        if (username.equalsIgnoreCase(CORRECT_USERNAME) && password.equals(CORRECT_PASSWORD)) {
            // Login successful
            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
            // Navigate to MainActivity
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // Close LoginActivity so user can't go back to it
        } else if (username.isEmpty() || password.isEmpty()) {
            // Empty fields
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
        } else {
            // Invalid credentials
            Toast.makeText(this, "Invalid username or password", Toast.LENGTH_SHORT).show();
        }
    }
}
