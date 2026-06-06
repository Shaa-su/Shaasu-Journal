package com.example.myapplication;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class VaultActivity extends AppCompatActivity {

    private TextView addButton;
    private View cardGmail;
    private View cardInstagram;
    private View cardBanking;
    private View cardCreditCard;
    private View cardWifi;
    private View cardSecretNote;

    // Image picker for Secret Notes
    private ActivityResultLauncher<String> imagePickerLauncher;
    private Uri selectedImageUri;
    private String encodedImageBase64;
    private AlertDialog currentAddDialog;

    // Search
    private String searchQuery = "";
    private EditText searchInput;

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

        // Image picker for Secret Notes
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        encodedImageBase64 = encodeImageToBase64(uri);
                        // Update preview if dialog is showing
                        updateImagePreviewInDialog();
                    }
                }
        );

        // Back button
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // + Add — shows category picker first
        addButton = findViewById(R.id.addButton);
        if (addButton != null) {
            addButton.setOnClickListener(v -> showCategoryPicker());
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

        // Reset Recovery button (header, next to + Add)
        ImageButton resetRecoveryBtn = findViewById(R.id.resetRecoveryButton);
        if (resetRecoveryBtn != null) {
            SharedPreferences pinPrefs = PinStore.get(this);
            boolean hasRecovery = pinPrefs.contains("recovery_question");
            resetRecoveryBtn.setVisibility(hasRecovery ? View.VISIBLE : View.GONE);
            resetRecoveryBtn.setOnClickListener(v -> showResetRecoveryDialog());
        }

        // Search
        searchInput = findViewById(R.id.searchInput);
        if (searchInput != null) {
            searchInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    searchQuery = s != null ? s.toString().toLowerCase(java.util.Locale.US).trim() : "";
                    refreshVaultEntries();
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

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

        java.util.List<VaultStore.VaultItem> allItems = VaultStore.getAll(this);
        entriesContainer.removeAllViews();

        // Filter by search query
        java.util.List<VaultStore.VaultItem> items = allItems;
        if (searchQuery != null && !searchQuery.isEmpty()) {
            items = new java.util.ArrayList<>();
            for (VaultStore.VaultItem item : allItems) {
                String label = item.label != null ? item.label.toLowerCase(java.util.Locale.US) : "";
                String account = item.account != null ? item.account.toLowerCase(java.util.Locale.US) : "";
                String note = item.password != null ? item.password.toLowerCase(java.util.Locale.US) : "";
                String tags = item.optional != null ? item.optional.toLowerCase(java.util.Locale.US) : "";
                String category = item.category != null ? item.category.toLowerCase(java.util.Locale.US) : "";

                if (label.contains(searchQuery) || account.contains(searchQuery)
                        || note.contains(searchQuery) || tags.contains(searchQuery)
                        || category.contains(searchQuery)) {
                    items.add(item);
                }
            }
        }

        // Update badge
        TextView badge = findViewById(R.id.vaultBadge);
        if (badge != null) badge.setText(String.valueOf(allItems.size()));

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

        // Icon tint by category — all using cyan theme
        if (iconImage != null) {
            int iconRes = android.R.drawable.ic_dialog_email;
            String cat = item.category != null ? item.category.toLowerCase(java.util.Locale.US) : "";
            if (cat.contains("instagram")) {
                iconRes = android.R.drawable.ic_menu_camera;
            } else if (cat.contains("game")) {
                iconRes = android.R.drawable.ic_menu_compass;
            } else if (cat.contains("credit") || cat.contains("card")) {
                iconRes = android.R.drawable.ic_menu_my_calendar;
            } else if (cat.contains("other")) {
                iconRes = android.R.drawable.ic_menu_myplaces;
            } else if (cat.contains("secret") || cat.contains("note")) {
                iconRes = android.R.drawable.ic_menu_edit;
            }
            iconImage.setImageResource(iconRes);
            iconImage.setColorFilter(getColor(R.color.vault_accent));
        }

        // Tap to show entry detail
        row.setOnClickListener(v -> showVaultEntryDialog(
                item.id, item.category, item.account, item.password, item.optional));
    }

    private void showCategoryPicker() {
        final String[] categories = {"Gmail", "Instagram", "Game Accounts", "Credit Card", "Other Accounts", "Secret Note"};
        final int[] icons = {
                android.R.drawable.ic_dialog_email,      // Gmail
                android.R.drawable.ic_menu_camera,        // Instagram
                android.R.drawable.ic_menu_myplaces,      // Game Accounts
                android.R.drawable.ic_menu_manage,        // Credit Card
                android.R.drawable.ic_menu_compass,       // Other Accounts
                android.R.drawable.ic_menu_edit           // Secret Note
        };

        View container = LayoutInflater.from(this).inflate(R.layout.dialog_vault_category_picker, null, false);
        LinearLayout listLayout = container.findViewById(R.id.categoryList);
        ImageButton closeBtn = container.findViewById(R.id.categoryPickerClose);

        if (listLayout == null || closeBtn == null) {
            // Fallback to simple dialog if layout not found
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ThemeOverlay_App_DarkDialog);
            builder.setTitle("Choose type");
            builder.setItems(categories, (dialog, which) -> showVaultAddDialog(categories[which]));
            builder.setNegativeButton("Cancel", null);
            builder.show();
            return;
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

        for (int i = 0; i < categories.length; i++) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_vault_category_option, listLayout, false);
            ImageView iconView = row.findViewById(R.id.catOptionIcon);
            TextView nameView = row.findViewById(R.id.catOptionName);

            if (iconView != null) iconView.setImageResource(icons[i]);
            if (nameView != null) nameView.setText(categories[i]);

            final int index = i;
            row.setOnClickListener(v -> {
                dialog.dismiss();
                showVaultAddDialog(categories[index]);
            });
            listLayout.addView(row);
        }

        closeBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
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

        // Image section (Secret Notes only)
        LinearLayout imageSection = container.findViewById(R.id.vaultImageSection);
        TextView imagePickButton = container.findViewById(R.id.vaultImagePickButton);
        ImageView imagePreview = container.findViewById(R.id.vaultImagePreview);
        TextView imageRemove = container.findViewById(R.id.vaultImageRemove);

        boolean isNote = "Secret Note".equals(category);
        if (imageSection != null) {
            imageSection.setVisibility(isNote ? View.VISIBLE : View.GONE);
        }
        // Reset image state for a fresh dialog
        selectedImageUri = null;
        encodedImageBase64 = null;

        if (isNote && imagePickButton != null) {
            imagePickButton.setOnClickListener(v -> {
                imagePickerLauncher.launch("image/*");
            });
        }
        if (isNote && imageRemove != null) {
            imageRemove.setOnClickListener(v -> {
                selectedImageUri = null;
                encodedImageBase64 = null;
                if (imagePreview != null) {
                    imagePreview.setVisibility(View.GONE);
                    imagePreview.setImageDrawable(null);
                }
                if (imageRemove != null) imageRemove.setVisibility(View.GONE);
                if (imagePickButton != null) imagePickButton.setText("+ Choose image");
            });
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
        currentAddDialog = dialog;
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
                    encodedImageBase64,
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

        // Attached image display (Secret Notes with images)
        View imageSection = container.findViewById(R.id.entryImageSection);
        ImageView imageDisplay = container.findViewById(R.id.entryImageDisplay);
        // We need the item's imageBase64 — fetch it from store
        VaultStore.VaultItem fullItem = null;
        if (itemId != null) {
            for (VaultStore.VaultItem vi : VaultStore.getAll(this)) {
                if (vi.id.equals(itemId)) {
                    fullItem = vi;
                    break;
                }
            }
        }
        if (isNote && fullItem != null && fullItem.imageBase64 != null && imageSection != null && imageDisplay != null) {
            imageSection.setVisibility(View.VISIBLE);
            try {
                byte[] decoded = Base64.decode(fullItem.imageBase64, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                if (bmp != null) {
                    imageDisplay.setImageBitmap(bmp);
                }
            } catch (Exception e) {
                imageSection.setVisibility(View.GONE);
            }
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

    private void updateImagePreviewInDialog() {
        // Find the add dialog's views to update the preview
        // The dialog views are found dynamically
        if (currentAddDialog != null && currentAddDialog.getWindow() != null) {
            ViewGroup decorView = (ViewGroup) currentAddDialog.getWindow().getDecorView();
            ImageView preview = decorView.findViewById(R.id.vaultImagePreview);
            TextView pickBtn = decorView.findViewById(R.id.vaultImagePickButton);
            TextView removeBtn = decorView.findViewById(R.id.vaultImageRemove);
            if (preview != null && encodedImageBase64 != null) {
                try {
                    byte[] decoded = Base64.decode(encodedImageBase64, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                    if (bmp != null) {
                        preview.setImageBitmap(bmp);
                        preview.setVisibility(View.VISIBLE);
                        if (pickBtn != null) pickBtn.setText("Change image");
                        if (removeBtn != null) removeBtn.setVisibility(View.VISIBLE);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private String encodeImageToBase64(Uri imageUri) {
        if (imageUri == null) return null;
        try {
            InputStream is = getContentResolver().openInputStream(imageUri);
            if (is == null) return null;
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            if (bmp == null) return null;

            // Compress to JPEG at 85% quality, max 1024px width
            int maxWidth = 1024;
            if (bmp.getWidth() > maxWidth) {
                float ratio = (float) maxWidth / bmp.getWidth();
                int newHeight = Math.round(bmp.getHeight() * ratio);
                bmp = Bitmap.createScaledBitmap(bmp, maxWidth, newHeight, true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            byte[] bytes = baos.toByteArray();
            return Base64.encodeToString(bytes, Base64.DEFAULT);
        } catch (Exception e) {
            return null;
        }
    }

    private void showResetRecoveryDialog() {
        // Reuse the recovery setup dialog from VaultPinActivity's layout
        View container = LayoutInflater.from(this).inflate(R.layout.dialog_vault_recovery, null, false);
        if (container == null) return;

        TextView title = container.findViewById(R.id.recoveryTitle);
        TextView extraNote = container.findViewById(R.id.recoveryExtraNote);
        EditText questionInput = container.findViewById(R.id.recoveryQuestionInput);
        EditText answerInput = container.findViewById(R.id.recoveryAnswerInput);
        TextView caseToggle = container.findViewById(R.id.recoveryCaseToggle);
        TextView skipBtn = container.findViewById(R.id.recoverySkip);
        TextView saveBtn = container.findViewById(R.id.recoverySave);

        if (title != null) title.setText("Reset Recovery Question");
        if (extraNote != null) {
            extraNote.setVisibility(View.VISIBLE);
            extraNote.setText("Update your recovery question and answer.");
        }
        if (questionInput != null) {
            questionInput.setHint("e.g. What was your first pet?");
            questionInput.setText("");
        }
        if (answerInput != null) {
            answerInput.setHint("Your answer");
            answerInput.setInputType(InputType.TYPE_CLASS_TEXT);
            answerInput.setText("");
        }
        if (skipBtn != null) skipBtn.setText("Cancel");
        if (saveBtn != null) saveBtn.setText("Save");

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
            skipBtn.setOnClickListener(v -> dialog.dismiss());
        }

        if (saveBtn != null) {
            saveBtn.setOnClickListener(v -> {
                String question = questionInput != null ? questionInput.getText().toString().trim() : "";
                String answer = answerInput != null ? answerInput.getText().toString().trim() : "";
                if (question.isEmpty() || answer.isEmpty()) {
                    Toast.makeText(this, "Please fill in both fields", Toast.LENGTH_SHORT).show();
                    return;
                }
                SharedPreferences prefs = PinStore.get(this);
                String answerToStore = isCaseSensitive[0] ? answer : answer.toLowerCase();
                prefs.edit()
                        .putString("recovery_question", question)
                        .putString("recovery_answer_hash", hashAnswer(answerToStore))
                        .putBoolean("recovery_case_sensitive", isCaseSensitive[0])
                        .apply();
                String msg = "Recovery question updated" + (isCaseSensitive[0] ? " (case-sensitive)" : "");
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
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

    private String hashAnswer(String answer) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(answer.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return answer;
        }
    }
}
