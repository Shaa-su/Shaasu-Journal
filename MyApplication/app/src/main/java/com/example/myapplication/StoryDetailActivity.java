package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Base64;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.view.inputmethod.EditorInfo;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StoryDetailActivity extends AppCompatActivity {

    private TextView dateDisplayText;
    private EditText titleEditText;
    private EditText storyEditText;
    private LinearLayout goalsContainer;
    private View goalsModal;
    private View goalsBackdrop;
    private View goalsSheet;
    private TextView goalsEmptyText;
    private TextView goalsCountText;
    private View goalsProgressFill;
    private EditText addGoalEditText;
    private View addGoalButton;
    private Button doneGoalsButton;
    private ImageButton goalsCloseButton;
    private Button saveButton;
    private View topBar;
    private FloatingActionButton customFab;
    private ImageView wallpaperImageView;
    private ImageButton backButton;

    // Top-bar goal tracker
    private View goalTrackerPill;
    private TextView goalTrackerText;
    private View goalTrackerRing;
    private ImageView goalTrackerCheck;
    
    private int selectedDay;
    private int selectedMonth;
    private int selectedYear;
    
    private List<GoalItem> goalItems = new ArrayList<>();
    private Bitmap wallpaperBitmap;

    private ScaleGestureDetector imageScaleDetector;
    private ResizableImageSpan activeResizableImageSpan;
    private boolean isScalingInlineImage = false;
    private float touchDownX;
    private float touchDownY;
    private int touchSlop;

    private PopupWindow imageResizePopup;
    private SeekBar imageResizeSeekBar;
    private View imageResizePopupContent;
    
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int PICK_WALLPAPER_REQUEST = 2;
    private static final int MAX_INLINE_IMAGE_WIDTH_PX = 900;
    private static final int MIN_INLINE_IMAGE_WIDTH_PX = 80;
    private static final int DEFAULT_INLINE_IMAGE_WIDTH_PX = 300;
    private static final int RESIZE_SEEKBAR_MAX = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_story_detail);
        
        // Configure soft keyboard to not resize layout
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        
        dateDisplayText = findViewById(R.id.dateDisplayText);
        titleEditText = findViewById(R.id.titleEditText);
        storyEditText = findViewById(R.id.storyEditText);
        goalsContainer = findViewById(R.id.goalsContainer);
        goalsModal = findViewById(R.id.goalsModal);
        goalsBackdrop = findViewById(R.id.goalsBackdrop);
        goalsSheet = findViewById(R.id.goalsSheet);
        goalsEmptyText = findViewById(R.id.goalsEmptyText);
        goalsCountText = findViewById(R.id.goalsCountText);
        goalsProgressFill = findViewById(R.id.goalsProgressFill);
        addGoalEditText = findViewById(R.id.addGoalEditText);
        addGoalButton = findViewById(R.id.addGoalButton);
        doneGoalsButton = findViewById(R.id.doneGoalsButton);
        goalsCloseButton = findViewById(R.id.goalsCloseButton);
        saveButton = findViewById(R.id.saveButton);
        topBar = findViewById(R.id.topBar);
        goalTrackerPill = findViewById(R.id.goalTrackerPill);
        goalTrackerText = findViewById(R.id.goalTrackerText);
        goalTrackerRing = findViewById(R.id.goalTrackerRing);
        goalTrackerCheck = findViewById(R.id.goalTrackerCheck);
        customFab = findViewById(R.id.customFab);
        wallpaperImageView = findViewById(R.id.wallpaperImageView);
        backButton = findViewById(R.id.backButton);
        // Keep wallpaper fully opaque; overlay controls readability
        wallpaperImageView.setAlpha(1f);
        
        // Get date from intent
        Intent intent = getIntent();
        selectedDay = intent.getIntExtra("day", 1);
        selectedMonth = intent.getIntExtra("month", 0); // 0-11
        selectedYear = intent.getIntExtra("year", 2026);
        
        // Display the date
        String[] monthNames = {"January", "February", "March", "April", "May", "June",
                              "July", "August", "September", "October", "November", "December"};
        String dateStr = monthNames[selectedMonth] + " " + selectedDay + ", " + selectedYear;
        dateDisplayText.setText(dateStr);

        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                dismissInlineImageResizePopup();
                finish();
            });
        }
        
        // Load existing story if available
        loadStory();

        // Ensure tracker is correct even when no story exists yet
        updateTopGoalTracker();

        if (goalTrackerPill != null) {
            goalTrackerPill.setOnClickListener(v -> showGoalsModal());
        }

        setupInlineImageResizeGesture();

        touchSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();

        storyEditText.setOnKeyListener((v, keyCode, event) -> {
            dismissInlineImageResizePopup();
            return false;
        });

        storyEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) dismissInlineImageResizePopup();
        });
        
        // Goals sheet actions
        if (goalsBackdrop != null) {
            goalsBackdrop.setOnClickListener(v -> hideGoalsModal());
        }
        if (goalsCloseButton != null) {
            goalsCloseButton.setOnClickListener(v -> hideGoalsModal());
        }
        if (doneGoalsButton != null) {
            doneGoalsButton.setOnClickListener(v -> hideGoalsModal());
        }
        if (addGoalButton != null) {
            addGoalButton.setOnClickListener(v -> submitNewGoalFromInput());
        }
        if (addGoalEditText != null) {
            addGoalEditText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    submitNewGoalFromInput();
                    return true;
                }
                return false;
            });
        }
        
        // Save button click listener
        saveButton.setOnClickListener(v -> {
            dismissInlineImageResizePopup();
            saveStory();
        });
        
        // Initialize custom FAB with menu listener
        customFab.setMenuListener(new FloatingActionButton.FABMenuListener() {
            @Override
            public void onImageButtonClick() {
                openImagePicker();
            }
            
            @Override
            public void onGoalsButtonClick() {
                toggleGoalsVisibility();
            }
            
            @Override
            public void onWallpaperButtonClick() {
                openWallpaperPicker();
            }
        });
    }

    private void setupInlineImageResizeGesture() {
        imageScaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                activeResizableImageSpan = findResizableImageSpanAt(detector.getFocusX(), detector.getFocusY());
                isScalingInlineImage = activeResizableImageSpan != null;
                return isScalingInlineImage;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (activeResizableImageSpan == null) return false;

                float scaleFactor = detector.getScaleFactor();
                if (!Float.isFinite(scaleFactor)) return false;

                int currentWidth = activeResizableImageSpan.getWidthPx();
                int currentHeight = activeResizableImageSpan.getHeightPx();
                if (currentWidth <= 0 || currentHeight <= 0) return false;

                // getScaleFactor() is incremental since the previous callback
                int newWidth = clamp((int) (currentWidth * scaleFactor), MIN_INLINE_IMAGE_WIDTH_PX, getMaxInlineImageWidthPx());
                float ratio = activeResizableImageSpan.getAspectRatio();
                int newHeight = Math.max(1, (int) (newWidth * ratio));

                activeResizableImageSpan.setSizePx(newWidth, newHeight);
                storyEditText.invalidate();
                storyEditText.requestLayout();
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                activeResizableImageSpan = null;
                isScalingInlineImage = false;
            }
        });

        storyEditText.setOnTouchListener((v, event) -> {
            if (imageScaleDetector != null) {
                imageScaleDetector.onTouchEvent(event);
            }

            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                touchDownX = event.getX();
                touchDownY = event.getY();
            } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                float dx = Math.abs(event.getX() - touchDownX);
                float dy = Math.abs(event.getY() - touchDownY);

                // Treat as a tap if the finger didn't move much and we weren't scaling.
                if (!isScalingInlineImage && dx < touchSlop && dy < touchSlop) {
                    ResizableImageSpan tapped = findResizableImageSpanAt(event.getX(), event.getY());
                    if (tapped != null) {
                        activeResizableImageSpan = tapped;
                        showInlineImageResizeSeekbarFor(tapped);
                    } else {
                        dismissInlineImageResizePopup();
                    }
                }
            }

            // Only consume touch events while an inline image is actively being pinch-scaled.
            return isScalingInlineImage;
        });
    }

    private void showInlineImageResizeSeekbarFor(ResizableImageSpan span) {
        if (span == null) return;

        if (imageResizeSeekBar == null) {
            imageResizeSeekBar = new SeekBar(this);
            imageResizeSeekBar.setMax(RESIZE_SEEKBAR_MAX);
            int seekWidth = dpToPx(320);
            imageResizeSeekBar.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    seekWidth,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            imageResizeSeekBar.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
            imageResizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    if (activeResizableImageSpan == null) return;

                    int minW = MIN_INLINE_IMAGE_WIDTH_PX;
                    int maxW = getMaxInlineImageWidthPx();
                    int targetWidth = minW + (int) ((progress / (float) RESIZE_SEEKBAR_MAX) * (maxW - minW));
                    targetWidth = clamp(targetWidth, minW, maxW);

                    float ratio = activeResizableImageSpan.getAspectRatio();
                    int targetHeight = Math.max(1, (int) (targetWidth * ratio));

                    activeResizableImageSpan.setSizePx(targetWidth, targetHeight);
                    refreshSpanLayout(activeResizableImageSpan);
                    storyEditText.invalidate();
                    storyEditText.requestLayout();

                    // Keep the seekbar visually anchored under the image while resizing
                    positionInlineImageResizePopup(activeResizableImageSpan);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            android.widget.LinearLayout container = new android.widget.LinearLayout(this);
            container.setOrientation(android.widget.LinearLayout.VERTICAL);
            container.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            container.setBackgroundColor(0xCC1C1C1C);
            container.addView(imageResizeSeekBar);
            imageResizePopupContent = container;
        }

        if (imageResizePopup == null) {
            imageResizePopup = new PopupWindow(imageResizePopupContent,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    true);
            imageResizePopup.setOutsideTouchable(true);
            imageResizePopup.setFocusable(true);
            imageResizePopup.setOnDismissListener(() -> {
                // Clear selection when popup closes
                activeResizableImageSpan = null;
            });
        }

        // Update seekbar progress to match current size
        int minW = MIN_INLINE_IMAGE_WIDTH_PX;
        int maxW = getMaxInlineImageWidthPx();
        int currentW = clamp(span.getWidthPx(), minW, maxW);
        int progress = (int) (((currentW - minW) / (float) (maxW - minW)) * RESIZE_SEEKBAR_MAX);
        progress = clamp(progress, 0, RESIZE_SEEKBAR_MAX);
        imageResizeSeekBar.setProgress(progress);

        if (!imageResizePopup.isShowing()) {
            // Show first at a safe default, then reposition accurately after layout.
            imageResizePopup.showAtLocation(storyEditText, android.view.Gravity.TOP | android.view.Gravity.START, 0, 0);
        }

        // Position under the tapped image (or re-position if already showing)
        positionInlineImageResizePopup(span);
    }

    private void positionInlineImageResizePopup(ResizableImageSpan span) {
        if (span == null || imageResizePopup == null || !imageResizePopup.isShowing()) return;
        if (!(storyEditText.getText() instanceof Spanned)) return;
        android.text.Layout layout = storyEditText.getLayout();
        if (layout == null) {
            storyEditText.post(() -> positionInlineImageResizePopup(span));
            return;
        }

        Spanned spanned = (Spanned) storyEditText.getText();
        int start = spanned.getSpanStart(span);
        if (start < 0) return;

        int line = layout.getLineForOffset(start);
        float xText = layout.getPrimaryHorizontal(start);
        int yText = layout.getLineBottom(line);

        float xInView = xText + storyEditText.getTotalPaddingLeft() - storyEditText.getScrollX();
        float yInView = yText + storyEditText.getTotalPaddingTop() - storyEditText.getScrollY();

        int[] loc = new int[2];
        storyEditText.getLocationOnScreen(loc);
        int xOnScreen = (int) (loc[0] + xInView);
        int yOnScreen = (int) (loc[1] + yInView);

        // Add a small gap under the image
        int gapPx = (int) (8 * getResources().getDisplayMetrics().density);
        yOnScreen += gapPx;

        // Clamp X so popup stays on screen
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        View content = imageResizePopup.getContentView();
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupW = content.getMeasuredWidth();
        int popupH = content.getMeasuredHeight();
        int desiredX = xOnScreen - (popupW / 2);
        int clampedX = Math.max(0, Math.min(desiredX, Math.max(0, screenW - popupW)));

        int clampedY = Math.max(0, Math.min(yOnScreen, Math.max(0, screenH - popupH)));

        imageResizePopup.update(clampedX, clampedY,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.max(1, (int) (dp * density));
    }

    private void dismissInlineImageResizePopup() {
        if (imageResizePopup != null && imageResizePopup.isShowing()) {
            imageResizePopup.dismiss();
        }
        activeResizableImageSpan = null;
    }

    private void refreshSpanLayout(ResizableImageSpan span) {
        if (span == null) return;
        if (!(storyEditText.getText() instanceof Spanned)) return;
        Spanned spanned = (Spanned) storyEditText.getText();
        int start = spanned.getSpanStart(span);
        int end = spanned.getSpanEnd(span);
        if (start < 0 || end <= start) return;

        // Re-apply the span to encourage TextView to re-measure the replacement.
        SpannableStringBuilder editable = new SpannableStringBuilder(spanned);
        editable.removeSpan(span);
        editable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int selection = storyEditText.getSelectionStart();
        storyEditText.setText(editable);
        if (selection >= 0 && selection <= editable.length()) {
            storyEditText.setSelection(selection);
        }
    }

    private int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private int getMaxInlineImageWidthPx() {
        // Keep images within screen width and a reasonable cap for performance
        int screenWidthPx = getResources().getDisplayMetrics().widthPixels;
        int paddingPx = storyEditText != null ? (storyEditText.getTotalPaddingLeft() + storyEditText.getTotalPaddingRight()) : 0;
        int available = Math.max(1, screenWidthPx - paddingPx);
        return Math.min(MAX_INLINE_IMAGE_WIDTH_PX, available);
    }

    private ResizableImageSpan findResizableImageSpanAt(float xView, float yView) {
        if (storyEditText == null) return null;
        android.text.Layout layout = storyEditText.getLayout();
        if (layout == null) return null;

        float x = xView + storyEditText.getScrollX() - storyEditText.getTotalPaddingLeft();
        float y = yView + storyEditText.getScrollY() - storyEditText.getTotalPaddingTop();

        int line = layout.getLineForVertical((int) y);
        int offset = layout.getOffsetForHorizontal(line, x);

        Spanned text = (Spanned) storyEditText.getText();
        if (text == null) return null;

        // Try exact offset first
        ResizableImageSpan[] spans = text.getSpans(offset, offset, ResizableImageSpan.class);
        if (spans != null && spans.length > 0) return spans[0];

        // Sometimes offset lands adjacent; check nearby
        int start = Math.max(0, offset - 1);
        int end = Math.min(text.length(), offset + 1);
        spans = text.getSpans(start, end, ResizableImageSpan.class);
        if (spans != null && spans.length > 0) return spans[0];

        return null;
    }
    
    
    private void toggleGoalsVisibility() {
        dismissInlineImageResizePopup();
        if (goalsModal.getVisibility() == View.VISIBLE) {
            hideGoalsModal();
        } else {
            showGoalsModal();
        }
    }

    private void showGoalsModal() {
        if (customFab != null) customFab.collapseImmediately();
        updateGoalsUi();
        goalsModal.setAlpha(0f);
        goalsModal.setVisibility(View.VISIBLE);
        goalsModal.animate()
                .alpha(1f)
                .setDuration(250)
                .start();
    }

    private void hideGoalsModal() {
        if (goalsModal == null) return;
        if (goalsModal.getVisibility() != View.VISIBLE) return;

        goalsModal.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        goalsModal.setVisibility(View.GONE);
                        if (addGoalEditText != null) addGoalEditText.clearFocus();
                    })
                    .start();
    }

    private void submitNewGoalFromInput() {
        if (addGoalEditText == null) return;
        String text = addGoalEditText.getText().toString().trim();
        if (text.isEmpty()) return;
        addGoal(text, false);
        addGoalEditText.setText("");
        updateGoalsUi();
    }

    private void updateGoalsUi() {
        updateGoalsEmptyState();
        updateGoalsHeader();
        updateTopGoalTracker();
    }

    private void updateGoalsEmptyState() {
        if (goalsEmptyText == null || goalsContainer == null) return;
        boolean empty = goalsContainer.getChildCount() == 0;
        goalsEmptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void updateGoalsHeader() {
        int total = goalItems.size();
        int completed = 0;
        for (GoalItem item : goalItems) {
            if (item.completed) completed++;
        }

        if (goalsCountText != null) {
            goalsCountText.setText(completed + " of " + total + " completed");
        }

        if (goalsProgressFill != null) {
            float ratio = total <= 0 ? 0f : (completed / (float) total);
            View parent = (View) goalsProgressFill.getParent();
            if (parent instanceof androidx.constraintlayout.widget.ConstraintLayout) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                        (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) goalsProgressFill.getLayoutParams();
                lp.matchConstraintPercentWidth = Math.max(0f, Math.min(1f, ratio));
                goalsProgressFill.setLayoutParams(lp);
            }
        }
    }

    private void updateTopGoalTracker() {
        if (goalTrackerPill == null || goalTrackerText == null || goalTrackerRing == null || goalTrackerCheck == null) {
            return;
        }

        int total = goalItems.size();
        int completed = 0;
        for (GoalItem item : goalItems) {
            if (item.completed) completed++;
        }

        if (total <= 0) {
            goalTrackerPill.setVisibility(View.GONE);
            return;
        }

        goalTrackerPill.setVisibility(View.VISIBLE);
        goalTrackerText.setText(completed + "/" + total);

        boolean allDone = completed == total;
        goalTrackerCheck.setVisibility(allDone ? View.VISIBLE : View.GONE);
        goalTrackerRing.setBackgroundResource(allDone ? R.drawable.bg_goal_tracker_ring_done : R.drawable.bg_goal_tracker_ring);
        int textColor = androidx.core.content.ContextCompat.getColor(this,
                allDone ? R.color.menu_text_primary : R.color.menu_text_secondary);
        goalTrackerText.setTextColor(textColor);
    }

    private void addGoal(String goalText) {
        addGoal(goalText, false);
    }

    private void addGoal(String goalText, boolean completed) {
        if (goalsContainer == null) return;

        View row = LayoutInflater.from(this).inflate(R.layout.item_goal_row, goalsContainer, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = dpToPx(10);
        row.setLayoutParams(lp);

        View checkButton = row.findViewById(R.id.goalCheckButton);
        View checkCircle = row.findViewById(R.id.goalCheckCircle);
        ImageView checkIcon = row.findViewById(R.id.goalCheckIcon);
        TextView goalTextView = row.findViewById(R.id.goalText);
        ImageButton deleteButton = row.findViewById(R.id.goalDeleteButton);

        goalTextView.setText(goalText);

        GoalItem item = new GoalItem(row, goalTextView, checkCircle, checkIcon, deleteButton);
        goalItems.add(item);
        goalsContainer.addView(row);

        setGoalCompleted(item, completed);

        if (checkButton != null) {
            checkButton.setOnClickListener(v -> {
                setGoalCompleted(item, !item.completed);
                updateGoalsUi();
            });
        }

        if (deleteButton != null) {
            deleteButton.setOnClickListener(v -> {
                goalsContainer.removeView(row);
                goalItems.remove(item);
                updateGoalsUi();
            });
        }

        updateGoalsUi();
    }

    private void setGoalCompleted(GoalItem item, boolean completed) {
        if (item == null) return;
        item.completed = completed;

        if (item.checkCircle != null) {
            item.checkCircle.setBackgroundResource(completed ? R.drawable.bg_goal_check_checked : R.drawable.bg_goal_check_unchecked);
        }
        if (item.checkIcon != null) {
            item.checkIcon.setVisibility(completed ? View.VISIBLE : View.GONE);
        }
        if (item.textView != null) {
            int color = androidx.core.content.ContextCompat.getColor(this,
                    completed ? R.color.menu_text_secondary : R.color.menu_text_primary);
            item.textView.setTextColor(color);
            item.textView.setAlpha(completed ? 0.8f : 1f);

            int flags = item.textView.getPaintFlags();
            if (completed) {
                item.textView.setPaintFlags(flags | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                item.textView.setPaintFlags(flags & (~Paint.STRIKE_THRU_TEXT_FLAG));
            }
        }
    }
    
    private void openImagePicker() {
        dismissInlineImageResizePopup();
        if (customFab != null) customFab.collapseImmediately();
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }
    
    private void openWallpaperPicker() {
        dismissInlineImageResizePopup();
        if (customFab != null) customFab.collapseImmediately();
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_WALLPAPER_REQUEST);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                addImage(imageUri);
            }
        } else if (requestCode == PICK_WALLPAPER_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri wallpaperUri = data.getData();
            if (wallpaperUri != null) {
                setWallpaper(wallpaperUri);
            }
        }
    }
    
    private void addImage(Uri imageUri) {
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
            if (bitmap != null) {
                // Compress/resize the bitmap to a reasonable max size, but allow manual resizing within bounds
                Bitmap compressedBitmap = compressImage(bitmap, getMaxInlineImageWidthPx());
                
                // Get current cursor position
                int cursorPosition = storyEditText.getSelectionStart();
                if (cursorPosition < 0) {
                    cursorPosition = storyEditText.getText().length();
                }
                
                // Get current SpannableStringBuilder from EditText
                SpannableStringBuilder spannableBuilder = new SpannableStringBuilder(storyEditText.getText());
                
                int initialWidth = Math.min(Math.min(compressedBitmap.getWidth(), getMaxInlineImageWidthPx()), DEFAULT_INLINE_IMAGE_WIDTH_PX);
                float ratio = (float) compressedBitmap.getHeight() / (float) compressedBitmap.getWidth();
                int initialHeight = Math.max(1, (int) (initialWidth * ratio));

                // Create resizable span
                ResizableImageSpan imageSpan = new ResizableImageSpan(this, compressedBitmap, initialWidth, initialHeight);
                
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
    
    private Bitmap compressImage(Bitmap bitmap, int maxWidthPx) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        if (width <= maxWidthPx) {
            return bitmap;
        }
        
        // Calculate new height maintaining aspect ratio
        float ratio = (float) height / width;
        int newHeight = (int) (maxWidthPx * ratio);
        
        return Bitmap.createScaledBitmap(bitmap, maxWidthPx, newHeight, true);
    }
    
    private void setWallpaper(Uri wallpaperUri) {
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(wallpaperUri));
            if (bitmap != null) {
                // Scale wallpaper to a reasonable size for the device (avoids tiny bitmap being blown up)
                int screenWidthPx = getResources().getDisplayMetrics().widthPixels;
                int maxWallpaperWidth = Math.min(1080, screenWidthPx);
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                
                if (width > maxWallpaperWidth) {
                    float ratio = (float) height / width;
                    int newHeight = (int) (maxWallpaperWidth * ratio);
                    bitmap = Bitmap.createScaledBitmap(bitmap, maxWallpaperWidth, newHeight, true);
                }
                
                wallpaperBitmap = bitmap;
                wallpaperImageView.setImageBitmap(bitmap);
                wallpaperImageView.setAlpha(1f);
                Toast.makeText(this, "hey this is update for now?", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Error loading wallpaper", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
        boolean firstGoal = true;
        for (int i = 0; i < goalItems.size(); i++) {
            GoalItem item = goalItems.get(i);
            String goalText = item.textView != null ? item.textView.getText().toString() : "";
            boolean isCompleted = item.completed;

            goalText = goalText != null ? goalText.trim() : "";
            if (goalText.isEmpty()) continue;

            if (!firstGoal) {
                goalsData.append("|||");
            }
            goalsData.append(goalText).append("|").append(isCompleted);
            firstGoal = false;
        }
        
        // Save to SharedPreferences
        SharedPreferences sharedPref = getSharedPreferences("stories", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        
        String dateKey = selectedYear + "-" + String.format("%02d", selectedMonth + 1) + "-" + String.format("%02d", selectedDay);
        
        // Encode wallpaper if set
        String wallpaperEncoded = "";
        if (wallpaperBitmap != null) {
            wallpaperEncoded = encodeImageToBase64(wallpaperBitmap);
        }
        
        // Format: title||story||goals[WALLPAPER_MARKER]wallpaperBase64
        String storyData = title + "||" + storyWithPlaceholders + "||" + goalsData.toString();
        if (!wallpaperEncoded.isEmpty()) {
            storyData += "[WALLPAPER_MARKER]" + wallpaperEncoded;
        }
        
        editor.putString(dateKey, storyData);
        editor.apply();
        
        Toast.makeText(this, "Story saved!", Toast.LENGTH_SHORT).show();
        finish();
    }
    
    private String extractStoryWithImagePlaceholders(Spanned spanned) {
        StringBuilder result = new StringBuilder();
        ImageSpan[] imageSpans = spanned.getSpans(0, spanned.length(), ImageSpan.class);

        // getSpans() order is not guaranteed; sort by position so saving works
        Arrays.sort(imageSpans, Comparator.comparingInt(spanned::getSpanStart));
        
        int lastIndex = 0;
        for (ImageSpan imageSpan : imageSpans) {
            int start = spanned.getSpanStart(imageSpan);
            int end = spanned.getSpanEnd(imageSpan);

            if (start < 0 || end < 0) continue;
            if (start < lastIndex) {
                // Defensive: skip overlapping/out-of-order spans
                continue;
            }
            
            // Add text before image
            result.append(spanned.subSequence(lastIndex, start));
            
            // Get the bitmap from the ImageSpan and encode it
            Bitmap bitmap = getImageBitmapFromImageSpan(imageSpan);
            if (bitmap != null) {
                String encodedImage = encodeImageToBase64(bitmap);

                int widthPx = 0;
                int heightPx = 0;
                try {
                    android.graphics.drawable.Drawable d = imageSpan.getDrawable();
                    if (d != null && d.getBounds() != null) {
                        widthPx = d.getBounds().width();
                        heightPx = d.getBounds().height();
                    }
                } catch (Exception ignored) {
                }

                if (widthPx > 0 && heightPx > 0) {
                    // Persist size: [IMG:base64:width:height]
                    result.append("[IMG:").append(encodedImage).append(":").append(widthPx).append(":").append(heightPx).append("]");
                } else {
                    result.append("[IMG:").append(encodedImage).append("]");
                }
            }
            
            lastIndex = end;
        }
        
        // Add remaining text after last image
        result.append(spanned.subSequence(lastIndex, spanned.length()));
        
        return result.toString();
    }
    
    private Bitmap getImageBitmapFromImageSpan(ImageSpan imageSpan) {
        try {
            android.graphics.drawable.Drawable drawable = imageSpan.getDrawable();
            if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                return ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
            } else {
                // For other drawable types, try to convert to bitmap
                int width = drawable.getIntrinsicWidth();
                int height = drawable.getIntrinsicHeight();
                if (width <= 0) width = 300;
                if (height <= 0) height = 300;
                
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                drawable.setBounds(0, 0, width, height);
                drawable.draw(canvas);
                return bitmap;
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error extracting image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }
    
    private void loadStory() {
        SharedPreferences sharedPref = getSharedPreferences("stories", MODE_PRIVATE);
        String dateKey = selectedYear + "-" + String.format("%02d", selectedMonth + 1) + "-" + String.format("%02d", selectedDay);
        String storyData = sharedPref.getString(dateKey, null);
        
        if (storyData != null) {
            // First, extract wallpaper if present
            String wallpaperEncoded = "";
            if (storyData.contains("[WALLPAPER_MARKER]")) {
                String[] wallpaperParts = storyData.split("\\[WALLPAPER_MARKER\\]");
                storyData = wallpaperParts[0]; // Main data without wallpaper
                if (wallpaperParts.length > 1) {
                    wallpaperEncoded = wallpaperParts[1];
                }
            }

            // Legacy format: title||story||goals (goals separated by |||)
            // NOTE: We must NOT use split("||") because "|||" contains "||".
            String title = "";
            String story = "";
            String goalsBlob = "";
            int firstSep = storyData.indexOf("||");
            if (firstSep < 0) {
                title = storyData;
            } else {
                title = storyData.substring(0, firstSep);
                int secondSep = storyData.indexOf("||", firstSep + 2);
                if (secondSep < 0) {
                    story = storyData.substring(firstSep + 2);
                } else {
                    story = storyData.substring(firstSep + 2, secondSep);
                    goalsBlob = storyData.substring(secondSep + 2);
                }
            }

            titleEditText.setText(title);

            if (story != null && !story.isEmpty()) {
                SpannableStringBuilder spannableStory = parseStoryWithImages(story);
                storyEditText.setText(spannableStory);
            }

            if (goalsBlob != null && !goalsBlob.isEmpty()) {
                String[] goals = goalsBlob.split("\\|\\|\\|");
                for (String goal : goals) {
                    String[] goalParts = goal.split("\\|");
                    String goalText = goalParts.length > 0 ? goalParts[0] : "";
                    boolean isCompleted = goalParts.length > 1 && Boolean.parseBoolean(goalParts[1]);

                    addGoal(goalText, isCompleted);
                }
            }

            updateGoalsUi();
            
            // Load wallpaper
            if (!wallpaperEncoded.isEmpty()) {
                Bitmap wallpaper = decodeImageFromBase64(wallpaperEncoded);
                if (wallpaper != null) {
                    wallpaperBitmap = wallpaper;
                    wallpaperImageView.setImageBitmap(wallpaper);
                    wallpaperImageView.setAlpha(1f);
                }
            }
        }
    }
    
    private SpannableStringBuilder parseStoryWithImages(String storyWithPlaceholders) {
        SpannableStringBuilder result = new SpannableStringBuilder();
        
        // Pattern to match [IMG:base64data] (or [IMG:base64:width:height]) with DOTALL flag to handle newlines
        Pattern pattern = Pattern.compile("\\[IMG:(.+?)\\]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(storyWithPlaceholders);
        
        int lastIndex = 0;
        while (matcher.find()) {
            // Add text before image
            result.append(storyWithPlaceholders.subSequence(lastIndex, matcher.start()));
            
            // Decode and insert image
            String payload = matcher.group(1);
            ParsedImagePayload parsed = ParsedImagePayload.parse(payload);
            Bitmap bitmap = decodeImageFromBase64(parsed.base64);
            if (bitmap != null) {
                int insertPosition = result.length();
                result.insert(insertPosition, "\u0001");

                Bitmap compressedBitmap = compressImage(bitmap, getMaxInlineImageWidthPx());

                int defaultWidth = Math.min(Math.min(compressedBitmap.getWidth(), getMaxInlineImageWidthPx()), DEFAULT_INLINE_IMAGE_WIDTH_PX);
                float ratio = (float) compressedBitmap.getHeight() / (float) compressedBitmap.getWidth();
                int defaultHeight = Math.max(1, (int) (defaultWidth * ratio));

                int widthPx = parsed.widthPx != null ? clamp(parsed.widthPx, MIN_INLINE_IMAGE_WIDTH_PX, getMaxInlineImageWidthPx()) : defaultWidth;
                int heightPx;
                if (parsed.heightPx != null && parsed.widthPx != null && parsed.widthPx > 0) {
                    // Keep user's saved aspect if provided
                    float savedRatio = (float) parsed.heightPx / (float) parsed.widthPx;
                    heightPx = Math.max(1, (int) (widthPx * savedRatio));
                } else {
                    heightPx = defaultHeight;
                }

                ResizableImageSpan imageSpan = new ResizableImageSpan(this, compressedBitmap, widthPx, heightPx);
                result.setSpan(imageSpan, insertPosition, insertPosition + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                // If decoding failed, keep the placeholder text as-is
                result.append(matcher.group(0));
            }
            
            lastIndex = matcher.end();
        }
        
        // Add remaining text after last image
        result.append(storyWithPlaceholders.subSequence(lastIndex, storyWithPlaceholders.length()));
        
        return result;
    }

    private static class ParsedImagePayload {
        final String base64;
        final Integer widthPx;
        final Integer heightPx;

        private ParsedImagePayload(String base64, Integer widthPx, Integer heightPx) {
            this.base64 = base64;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
        }

        static ParsedImagePayload parse(String payload) {
            if (payload == null) return new ParsedImagePayload("", null, null);

            // Backward compatible:
            // - Old: base64
            // - New: base64:width:height
            int lastColon = payload.lastIndexOf(':');
            if (lastColon <= 0) {
                return new ParsedImagePayload(payload, null, null);
            }

            int secondLastColon = payload.lastIndexOf(':', lastColon - 1);
            if (secondLastColon <= 0) {
                return new ParsedImagePayload(payload, null, null);
            }

            String maybeWidth = payload.substring(secondLastColon + 1, lastColon);
            String maybeHeight = payload.substring(lastColon + 1);

            try {
                int w = Integer.parseInt(maybeWidth.trim());
                int h = Integer.parseInt(maybeHeight.trim());
                if (w > 0 && h > 0) {
                    String base64 = payload.substring(0, secondLastColon);
                    return new ParsedImagePayload(base64, w, h);
                }
            } catch (NumberFormatException ignored) {
            }

            return new ParsedImagePayload(payload, null, null);
        }
    }

    private static class ResizableImageSpan extends ImageSpan {
        private final android.graphics.drawable.BitmapDrawable drawable;
        private final float aspectRatio;

        ResizableImageSpan(android.content.Context context, Bitmap bitmap, int widthPx, int heightPx) {
            super(context, bitmap);
            drawable = new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);

            // Lock aspect ratio to the underlying bitmap (prevents drift after repeated resizes,
            // which is especially noticeable on very wide/horizontal images).
            float w = bitmap != null ? bitmap.getWidth() : 0;
            float h = bitmap != null ? bitmap.getHeight() : 0;
            float r;
            if (w > 0 && h > 0) {
                r = h / w;
            } else if (widthPx > 0 && heightPx > 0) {
                r = (float) heightPx / (float) widthPx;
            } else {
                r = 1f;
            }
            aspectRatio = (Float.isFinite(r) && r > 0f) ? r : 1f;
            setSizePx(widthPx, heightPx);
        }

        @Override
        public android.graphics.drawable.Drawable getDrawable() {
            return drawable;
        }

        void setSizePx(int widthPx, int heightPx) {
            int w = Math.max(1, widthPx);
            int h = Math.max(1, heightPx);
            drawable.setBounds(0, 0, w, h);
        }

        int getWidthPx() {
            return drawable.getBounds().width();
        }

        int getHeightPx() {
            return drawable.getBounds().height();
        }

        float getAspectRatio() {
            return aspectRatio;
        }
    }
    
    
    private static class GoalItem {
        final View row;
        final TextView textView;
        final View checkCircle;
        final ImageView checkIcon;
        final ImageButton deleteButton;
        boolean completed;

        GoalItem(View row, TextView textView, View checkCircle, ImageView checkIcon, ImageButton deleteButton) {
            this.row = row;
            this.textView = textView;
            this.checkCircle = checkCircle;
            this.checkIcon = checkIcon;
            this.deleteButton = deleteButton;
        }
    }
}
