package com.example.myapplication;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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
            addButton.setOnClickListener(v -> showVaultAddDialog(null));
        }

        // Category cards (matches activity_vault.xml IDs)
        cardGmail = findViewById(R.id.cardGmail);
        cardInstagram = findViewById(R.id.cardInstagram);
        cardBanking = findViewById(R.id.cardBanking);
        cardCreditCard = findViewById(R.id.cardCreditCard);
        cardWifi = findViewById(R.id.cardWifi);
        cardSecretNote = findViewById(R.id.cardSecretNote);

        if (cardGmail != null) cardGmail.setOnClickListener(v -> showVaultAddDialog("Gmail"));
        if (cardInstagram != null) cardInstagram.setOnClickListener(v -> showVaultAddDialog("Instagram"));
        if (cardBanking != null) cardBanking.setOnClickListener(v -> showVaultAddDialog("Game Accounts"));
        if (cardCreditCard != null) cardCreditCard.setOnClickListener(v -> showVaultAddDialog("Credit Card"));
        if (cardWifi != null) cardWifi.setOnClickListener(v -> showVaultAddDialog("Other Accounts"));
        if (cardSecretNote != null) cardSecretNote.setOnClickListener(v -> showVaultAddDialog("Secret Note"));

        refreshVaultEntries();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshVaultEntries();
    }

    private void refreshVaultEntries() {
        View entriesScroll = findViewById(R.id.vaultEntriesScroll);
        View emptyState = findViewById(R.id.vaultEmptyState);
        LinearLayout entriesContainer = findViewById(R.id.vaultEntriesContainer);
        if (entriesScroll == null || emptyState == null || entriesContainer == null) return;

        java.util.List<VaultStore.VaultItem> items = VaultStore.getAll(this);
        entriesContainer.removeAllViews();

        // Update badge
        TextView badge = findViewById(R.id.vaultBadge);
        if (badge != null) badge.setText(String.valueOf(items.size()));

        if (items.isEmpty()) {
            entriesScroll.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            return;
        }

        entriesScroll.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (VaultStore.VaultItem item : items) {
            View row = inflater.inflate(R.layout.item_vault_entry, entriesContainer, false);
            bindVaultEntry(row, item);
            entriesContainer.addView(row);
        }
    }

    private void bindVaultEntry(View row, VaultStore.VaultItem item) {
        TextView titleView = row.findViewById(R.id.vaultEntryTitle);
        TextView subtitleView = row.findViewById(R.id.vaultEntrySubtitle);
        ImageView iconImage = row.findViewById(R.id.vaultEntryIconImage);

        if (titleView != null) {
            String displayLabel = item.label != null && !item.label.isEmpty() ? item.label : item.account;
            titleView.setText(displayLabel);
        }
        if (subtitleView != null) subtitleView.setText(item.category);

        // Icon tint by category
        if (iconImage != null) {
            int iconRes = android.R.drawable.ic_dialog_email;
            int tintColor = 0xFFFF4444;
            String cat = item.category != null ? item.category.toLowerCase(java.util.Locale.US) : "";
            if (cat.contains("instagram")) {
                iconRes = android.R.drawable.ic_menu_camera;
                tintColor = 0xFFFFD700;
            } else if (cat.contains("game")) {
                iconRes = android.R.drawable.ic_menu_compass;
                tintColor = 0xFF4488FF;
            } else if (cat.contains("credit") || cat.contains("card")) {
                iconRes = android.R.drawable.ic_menu_my_calendar;
                tintColor = 0xFF4488FF;
            } else if (cat.contains("other")) {
                iconRes = android.R.drawable.ic_menu_myplaces;
                tintColor = 0xFF22C55E;
            } else if (cat.contains("secret") || cat.contains("note")) {
                iconRes = android.R.drawable.ic_menu_edit;
                tintColor = 0xFFFF4444;
            }
            iconImage.setImageResource(iconRes);
            iconImage.setColorFilter(tintColor);
        }

        // Tap to show entry detail
        row.setOnClickListener(v -> showVaultEntryDialog(
                item.id, item.category, item.account, item.password, item.optional));
    }

    private void showVaultAddDialog(String category) {
        View container = LayoutInflater.from(this).inflate(R.layout.dialog_vault_add, null, false);

        EditText labelInput = container.findViewById(R.id.vaultLabelInput);
        EditText accountInput = container.findViewById(R.id.vaultAccountInput);
        EditText passwordInput = container.findViewById(R.id.vaultPasswordInput);
        EditText optionalInput = container.findViewById(R.id.vaultOptionalInput);
        ImageButton passwordToggle = container.findViewById(R.id.vaultPasswordToggle);
        TextView saveButton = container.findViewById(R.id.vaultSaveButton);
        View closeButton = container.findViewById(R.id.vaultDialogClose);

        if (saveButton == null || closeButton == null) return;

        // Update field labels based on category
        TextView accountLabel = container.findViewById(R.id.vaultAccountLabel);
        TextView passwordLabel = container.findViewById(R.id.vaultPasswordLabel);
        TextView optionalLabel = container.findViewById(R.id.vaultOptionalLabel);

        if (category != null) {
            String accountHint = category + " address";
            String passwordLabelText = "Password";
            String optionalHint = category + " email (optional)";
            String optionalLabelText = "Recovery email (optional)";
            if ("Credit Card".equals(category)) {
                accountHint = "Card number";
                passwordLabelText = "CVV / PIN";
                optionalHint = "Cardholder name";
                optionalLabelText = "Cardholder name";
            } else if ("Secret Note".equals(category)) {
                accountHint = "Title";
                passwordLabelText = "Note content";
                optionalHint = "Tags (optional)";
                optionalLabelText = "Tags (optional)";
            } else if ("Game Accounts".equals(category)) {
                accountHint = "Username / Email";
                passwordLabelText = "Password";
                optionalHint = "Game name";
                optionalLabelText = "Game name (optional)";
            } else if ("Other Accounts".equals(category)) {
                accountHint = "Account name";
                passwordLabelText = "Password";
                optionalHint = "Notes (optional)";
                optionalLabelText = "Notes (optional)";
            }
            if (accountInput != null) accountInput.setHint(accountHint);
            if (passwordInput != null) passwordInput.setHint(passwordLabelText);
            if (optionalInput != null) optionalInput.setHint(optionalHint);
            if (accountLabel != null) accountLabel.setText(accountHint);
            if (passwordLabel != null) passwordLabel.setText(passwordLabelText);
            if (optionalLabel != null) optionalLabel.setText(optionalLabelText);
        }

        final boolean[] passwordVisible = {false};
        if (passwordToggle != null && passwordInput != null) {
            passwordToggle.setOnClickListener(v -> {
                passwordVisible[0] = !passwordVisible[0];
                int type = passwordVisible[0] ? InputType.TYPE_CLASS_TEXT : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
                passwordInput.setInputType(type);
                passwordInput.setSelection(passwordInput.getText().length());
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

        closeButton.setOnClickListener(v -> dialog.dismiss());

        saveButton.setOnClickListener(v -> {
            String label = labelInput != null ? labelInput.getText().toString().trim() : "";
            String account = accountInput != null ? accountInput.getText().toString().trim() : "";
            String pass = passwordInput != null ? passwordInput.getText().toString().trim() : "";

            if (account.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please fill in required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            String optValue = optionalInput != null ? optionalInput.getText().toString().trim() : "";

            VaultStore.VaultItem vaultItem = new VaultStore.VaultItem(
                    java.util.UUID.randomUUID().toString(),
                    category != null ? category : "",
                    label,
                    account,
                    pass,
                    optValue,
                    System.currentTimeMillis()
            );
            VaultStore.put(this, vaultItem);
            refreshVaultEntries();
            Toast.makeText(this, "Saved to vault!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showVaultEntryDialog(final String itemId, String category, String account, String password, String optional) {
        View container = LayoutInflater.from(this).inflate(R.layout.dialog_vault_entry, null, false);

        TextView titleView = container.findViewById(R.id.entryTitle);
        TextView subtitleView = container.findViewById(R.id.entrySubtitle);
        TextView field1View = container.findViewById(R.id.entryField1Value);
        TextView field2View = container.findViewById(R.id.entryField2Value);
        TextView field3View = container.findViewById(R.id.entryField3Value);
        TextView field1Label = container.findViewById(R.id.entryField1Label);
        TextView field2Label = container.findViewById(R.id.entryField2Label);
        TextView field3Label = container.findViewById(R.id.entryField3Label);
        ImageView collapseArrow = container.findViewById(R.id.entryCollapseArrow);
        ImageButton field1Copy = container.findViewById(R.id.entryField1Copy);
        ImageButton field2Copy = container.findViewById(R.id.entryField2Copy);
        ImageButton field3Copy = container.findViewById(R.id.entryField3Copy);
        ImageButton passwordToggle = container.findViewById(R.id.entryPasswordToggle);
        View deleteRow = container.findViewById(R.id.entryDeleteLayout);
        View deleteText = container.findViewById(R.id.entryDeleteText);

        final boolean[] passwordVisible = {false};
        final String[] actualPassword = {password};

        // Dynamic field labels per category
        String cat = category != null ? category.toLowerCase(java.util.Locale.US) : "";
        boolean isNote = cat.contains("secret") || cat.contains("note");

        // Populate fields
        if (titleView != null) {
            String initials = account != null && !account.isEmpty()
                    ? account.substring(0, Math.min(2, account.length())).toUpperCase(java.util.Locale.US)
                    : "??";
            titleView.setText(initials);
        }
        if (subtitleView != null) subtitleView.setText(category + " \u00b7 3 fields");
        if (field1View != null) field1View.setText(account);
        if (field2View != null) {
            if (isNote) {
                field2View.setText(password != null ? password : "");
            } else {
                field2View.setText("\u2022 \u2022 \u2022");
            }
        }
        if (field3View != null) field3View.setText(optional != null && !optional.isEmpty() ? optional : "\u2014");
        String f1Label = "ACCOUNT";
        String f2Label = "PASSWORD";
        String f3Label = "OPTIONAL";
        if (cat.contains("secret") || cat.contains("note")) {
            f1Label = "TITLE";
            f2Label = "NOTE CONTENT";
            f3Label = "TAGS";
        } else if (cat.contains("credit") || cat.contains("card")) {
            f1Label = "CARD NUMBER";
            f2Label = "CVV / PIN";
            f3Label = "CARDHOLDER";
        } else if (cat.contains("game")) {
            f1Label = "USERNAME";
            f2Label = "PASSWORD";
            f3Label = "GAME NAME";
        } else if (cat.contains("other")) {
            f1Label = "ACCOUNT NAME";
            f2Label = "PASSWORD";
            f3Label = "NOTES";
        }
        if (field1Label != null) field1Label.setText(f1Label);
        if (field2Label != null) field2Label.setText(f2Label);
        if (field3Label != null) field3Label.setText(f3Label);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });

        // Collapse arrow
        if (collapseArrow != null) collapseArrow.setOnClickListener(v -> dialog.dismiss());

        // Copy field 1
        if (field1Copy != null) {
            field1Copy.setOnClickListener(v -> copyToClipboard(account, isNote ? "Title copied" : "Account copied"));
        }

        // Password visibility toggle (hide for notes/secret notes)
        if (passwordToggle != null) {
            passwordToggle.setVisibility(isNote ? View.GONE : View.VISIBLE);
        }
        if (passwordToggle != null && field2View != null && !isNote) {
            passwordToggle.setOnClickListener(v -> {
                passwordVisible[0] = !passwordVisible[0];
                if (passwordVisible[0]) {
                    field2View.setText(actualPassword[0]);
                    passwordToggle.setColorFilter(getColor(R.color.event_accent));
                } else {
                    field2View.setText("\u2022 \u2022 \u2022");
                    passwordToggle.setColorFilter(getColor(R.color.menu_text_secondary));
                }
            });
        }

        // Copy field 2 (password or note content for secret notes)
        if (field2Copy != null) {
            field2Copy.setOnClickListener(v -> {
                String copyText = isNote ? (password != null ? password : "") : actualPassword[0];
                copyToClipboard(copyText, isNote ? "Note copied" : "Password copied");
            });
        }

        // Copy field 3
        if (field3Copy != null) {
            field3Copy.setOnClickListener(v -> copyToClipboard(optional, isNote ? "Tags copied" : "Copied"));
        }

        // Delete
        if (deleteRow != null) {
            deleteRow.setOnClickListener(v -> {
                if (itemId != null) VaultStore.delete(this, itemId);
                refreshVaultEntries();
                Toast.makeText(this, "Entry deleted", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private void copyToClipboard(String text, String toastMsg) {
        if (text == null || text.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("vault", text));
        Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
    }
}
