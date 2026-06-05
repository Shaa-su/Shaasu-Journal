package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

public class VaultActivity extends AppCompatActivity {

    private TextView addButton;
    private View cardGmail;
    private View cardInstagram;
    private View cardBanking;
    private View cardCreditCard;
    private View cardWifi;
    private View cardSecretNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vault);

        View root = findViewById(android.R.id.content);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                return insets;
            });
            ViewCompat.requestApplyInsets(root);
        }

        // Back button
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // + Add
        addButton = findViewById(R.id.addButton);
        if (addButton != null) {
            addButton.setOnClickListener(v -> handleAdd());
        }

        // Category cards
        cardGmail = findViewById(R.id.cardGmail);
        cardInstagram = findViewById(R.id.cardInstagram);
        cardBanking = findViewById(R.id.cardBanking);
        cardCreditCard = findViewById(R.id.cardCreditCard);
        cardWifi = findViewById(R.id.cardWifi);
        cardSecretNote = findViewById(R.id.cardSecretNote);

        if (cardGmail != null) cardGmail.setOnClickListener(v -> handleCardTap("Gmail"));
        if (cardInstagram != null) cardInstagram.setOnClickListener(v -> handleCardTap("Instagram"));
        if (cardBanking != null) cardBanking.setOnClickListener(v -> handleCardTap("Banking"));
        if (cardCreditCard != null) cardCreditCard.setOnClickListener(v -> handleCardTap("Credit Card"));
        if (cardWifi != null) cardWifi.setOnClickListener(v -> handleCardTap("Wi-Fi"));
        if (cardSecretNote != null) cardSecretNote.setOnClickListener(v -> handleCardTap("Secret Note"));
    }

    private void handleAdd() {
        Toast.makeText(this, "Add new vault item coming soon", Toast.LENGTH_SHORT).show();
    }

    private void handleCardTap(String label) {
        Toast.makeText(this, label + " vault coming soon", Toast.LENGTH_SHORT).show();
    }
}
