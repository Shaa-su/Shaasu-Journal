package com.example.myapplication;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import java.io.InputStream;

public class EventsActivity extends AppCompatActivity {

    private static final int PICK_BACKGROUND_REQUEST = 100;

    private View enableAlertsButton;
    private TextView newButton;
    private TextView addFirstEventButton;

    // Repeat & toggle state
    private boolean repeatYearly = true;
    private boolean notifyOnDay = true;

    // Background photo state
    private Uri selectedBackgroundUri;
    private AlertDialog currentDialog;
    private ImageView backgroundImage;
    private View uploadOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_events);

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

        // Enable Alerts
        enableAlertsButton = findViewById(R.id.enableAlertsButton);
        if (enableAlertsButton != null) {
            enableAlertsButton.setOnClickListener(v -> handleEnableAlerts());
        }

        // + New
        newButton = findViewById(R.id.newButton);
        if (newButton != null) {
            newButton.setOnClickListener(v -> showNewEventDialog());
        }

        // + Add First Event
        addFirstEventButton = findViewById(R.id.addFirstEventButton);
        if (addFirstEventButton != null) {
            addFirstEventButton.setOnClickListener(v -> showNewEventDialog());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_BACKGROUND_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                selectedBackgroundUri = imageUri;
                // Update dialog UI if it's still showing
                if (backgroundImage != null) {
                    try {
                        InputStream is = getContentResolver().openInputStream(imageUri);
                        Bitmap bm = BitmapFactory.decodeStream(is);
                        if (bm != null) {
                            backgroundImage.setImageBitmap(bm);
                            backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            backgroundImage.setColorFilter(null);
                            if (uploadOverlay != null) uploadOverlay.setVisibility(View.GONE);
                        }
                        if (is != null) is.close();
                    } catch (Exception e) {
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    private void handleEnableAlerts() {
        Toast.makeText(this, "Enable Alerts coming soon", Toast.LENGTH_SHORT).show();
    }

    private void showNewEventDialog() {
        View container = LayoutInflater.from(this).inflate(R.layout.dialog_new_event, null, false);

        EditText titleInput = container.findViewById(R.id.eventTitleInput);
        EditText noteInput = container.findViewById(R.id.eventNoteInput);
        TextView repeatYearlyBtn = container.findViewById(R.id.repeatYearly);
        TextView repeatOnceBtn = container.findViewById(R.id.repeatOnce);
        TextView monthSelector = container.findViewById(R.id.monthSelector);
        TextView daySelector = container.findViewById(R.id.daySelector);
        View notifyToggle = container.findViewById(R.id.notifyToggle);
        TextView saveButton = container.findViewById(R.id.saveEventButton);
        View closeButton = container.findViewById(R.id.newEventClose);
        View backgroundSelector = container.findViewById(R.id.backgroundSelector);
        backgroundImage = container.findViewById(R.id.backgroundImage);
        uploadOverlay = container.findViewById(R.id.uploadOverlay);

        if (titleInput == null || saveButton == null || closeButton == null) return;

        // Restore previously selected background if re-opening dialog
        if (selectedBackgroundUri != null && backgroundImage != null) {
            try {
                InputStream is = getContentResolver().openInputStream(selectedBackgroundUri);
                Bitmap bm = BitmapFactory.decodeStream(is);
                if (bm != null) {
                    backgroundImage.setImageBitmap(bm);
                    backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    backgroundImage.setColorFilter(null);
                    if (uploadOverlay != null) uploadOverlay.setVisibility(View.GONE);
                }
                if (is != null) is.close();
            } catch (Exception ignored) {}
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();

        currentDialog = dialog;

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });

        // Repeat segment toggle
        if (repeatYearlyBtn != null && repeatOnceBtn != null) {
            updateRepeatSegments(repeatYearlyBtn, repeatOnceBtn);
            repeatYearlyBtn.setOnClickListener(v -> {
                repeatYearly = true;
                updateRepeatSegments(repeatYearlyBtn, repeatOnceBtn);
            });
            repeatOnceBtn.setOnClickListener(v -> {
                repeatYearly = false;
                updateRepeatSegments(repeatYearlyBtn, repeatOnceBtn);
            });
        }

        // Notify toggle
        if (notifyToggle != null) {
            updateToggleUi((LinearLayout) notifyToggle);
            notifyToggle.setOnClickListener(v -> {
                notifyOnDay = !notifyOnDay;
                updateToggleUi((LinearLayout) notifyToggle);
            });
        }

        closeButton.setOnClickListener(v -> dialog.dismiss());

        // Background photo picker
        if (backgroundSelector != null) {
            backgroundSelector.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                try {
                    startActivityForResult(intent, PICK_BACKGROUND_REQUEST);
                } catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show();
                }
            });
        }

        saveButton.setOnClickListener(v -> {
            String title = titleInput.getText() != null ? titleInput.getText().toString().trim() : "";
            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter an event title", Toast.LENGTH_SHORT).show();
                return;
            }
            // TODO: Save event logic
            Toast.makeText(this, "Event saved!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateRepeatSegments(TextView yearly, TextView once) {
        if (yearly == null || once == null) return;
        if (repeatYearly) {
            yearly.setBackgroundResource(R.drawable.bg_event_segment_selected);
            yearly.setTextColor(getColor(R.color.menu_bg));
            once.setBackgroundResource(R.drawable.bg_event_segment_unselected);
            once.setTextColor(getColor(R.color.menu_text_secondary));
        } else {
            once.setBackgroundResource(R.drawable.bg_event_segment_selected);
            once.setTextColor(getColor(R.color.menu_bg));
            yearly.setBackgroundResource(R.drawable.bg_event_segment_unselected);
            yearly.setTextColor(getColor(R.color.menu_text_secondary));
        }
    }

    private void updateToggleUi(LinearLayout toggle) {
        if (toggle == null) return;
        if (notifyOnDay) {
            toggle.setBackgroundResource(R.drawable.bg_event_toggle_track);
            toggle.setGravity(android.view.Gravity.END);
        } else {
            toggle.setBackgroundResource(R.drawable.bg_event_segment_unselected);
            toggle.setGravity(android.view.Gravity.START);
        }
    }
}
