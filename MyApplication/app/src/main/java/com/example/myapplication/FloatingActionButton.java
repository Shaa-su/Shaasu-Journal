package com.example.myapplication;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.animation.ObjectAnimator;

import androidx.core.content.ContextCompat;

public class FloatingActionButton extends View {
    
    // Main FAB properties
    private float mainFabRadius = 50f; // 100dp diameter
    private float mainFabX;
    private float mainFabY;
    private int mainFabColor;
    private int mainFabExpandedColor;
    
    // Menu properties
    private float menuButtonRadius = 37.5f; // 75dp diameter
    private float expandedDistance = 220f;
    private float expandedAnimationValue = 0f;
    
    // Menu buttons
    private MenuButton[] menuButtons = new MenuButton[4];
    private static final int IMAGE_BUTTON = 0;
    private static final int GOALS_BUTTON = 1;
    private static final int WALLPAPER_BUTTON = 2;
    private static final int REMIND_BUTTON = 3;
    
    // Touch and drag properties
    private boolean isDragging = false;
    private float touchStartX;
    private float touchStartY;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean isExpanded = false;
    private boolean ignoreNextUp = false;
    
    // Paint objects
    private Paint mainPaint;
    private Paint mainGlowPaint;
    private Paint menuPaint;
    private Paint menuStrokePaint;
    private Paint textPaint;
    private Paint menuTextPaint;
    private Paint overlayPaint;
    
    // Screen dimensions
    private float screenWidth;
    private float screenHeight;
    private float statusBarHeight;
    
    // Interface for menu callbacks
    public interface FABMenuListener {
        void onImageButtonClick();
        void onGoalsButtonClick();
        void onWallpaperButtonClick();
        void onReminderButtonClick();
    }
    
    private FABMenuListener menuListener;
    
    // Inner class for menu buttons
    private static class MenuButton {
        float x;
        float y;
        float radius;
        int color;
        String label;
        int textColor;

        // Hit target for pill buttons
        final RectF hitRect = new RectF();
        final RectF touchRect = new RectF();
        String text;
        String icon;
        
        MenuButton(float x, float y, float radius, int color, String label) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.color = color;
            this.label = label;
            this.textColor = 0xFFffffff;
        }

        boolean contains(float px, float py) {
            return touchRect.contains(px, py);
        }
    }
    
    public FloatingActionButton(Context context) {
        super(context);
        init(context);
    }
    
    public FloatingActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public FloatingActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        // Get screen dimensions
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        
        // Calculate status bar height
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
        } else {
            statusBarHeight = dpToPx(25);
        }
        
        // Enable software rendering so shadow glows are visible
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        mainFabColor = ContextCompat.getColor(context, R.color.menu_teal);
        mainFabExpandedColor = ContextCompat.getColor(context, R.color.menu_surface_alt);

        // Initialize position to bottom-right corner
        mainFabX = screenWidth - dpToPx(50) - dpToPx(16);
        mainFabY = screenHeight - dpToPx(50) - dpToPx(16);
        
        // Initialize paint objects
        mainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mainPaint.setColor(mainFabColor);
        mainPaint.setStyle(Paint.Style.FILL);

        mainGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mainGlowPaint.setStyle(Paint.Style.FILL);
        
        menuPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        menuPaint.setStyle(Paint.Style.FILL);

        menuStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        menuStrokePaint.setStyle(Paint.Style.STROKE);
        menuStrokePaint.setStrokeWidth(dpToPx(1.6f));
        
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFFffffff);
        textPaint.setTextSize(dpToPx(14));
        textPaint.setTextAlign(Paint.Align.CENTER);

        menuTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        menuTextPaint.setColor(0xFFFFFFFF);
        menuTextPaint.setTextSize(dpToPx(14));
        menuTextPaint.setTextAlign(Paint.Align.LEFT);
        
        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setColor(0x66000000);
        overlayPaint.setStyle(Paint.Style.FILL);
        
        // Initialize menu buttons
        menuButtons[IMAGE_BUTTON] = new MenuButton(0, 0, menuButtonRadius, ContextCompat.getColor(context, R.color.fab_image), "");
        menuButtons[IMAGE_BUTTON].text = "Insert Image";
        menuButtons[IMAGE_BUTTON].icon = "🖼";

        menuButtons[WALLPAPER_BUTTON] = new MenuButton(0, 0, menuButtonRadius, ContextCompat.getColor(context, R.color.fab_wallpaper), "");
        menuButtons[WALLPAPER_BUTTON].text = "Set Wallpaper";
        menuButtons[WALLPAPER_BUTTON].icon = "✦";

        menuButtons[GOALS_BUTTON] = new MenuButton(0, 0, menuButtonRadius, ContextCompat.getColor(context, R.color.fab_goals), "");
        menuButtons[GOALS_BUTTON].text = "Goals";
        menuButtons[GOALS_BUTTON].icon = "◎";

        menuButtons[REMIND_BUTTON] = new MenuButton(0, 0, menuButtonRadius, ContextCompat.getColor(context, R.color.menu_teal), "");
        menuButtons[REMIND_BUTTON].text = "Remind";
        menuButtons[REMIND_BUTTON].icon = "⏰";
        
        setWillNotDraw(false);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Draw overlay if expanded
        if (isExpanded || expandedAnimationValue > 0) {
            overlayPaint.setAlpha((int) (0x66 * expandedAnimationValue));
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), overlayPaint);
        }
        
        // Draw menu buttons if expanded
        if (expandedAnimationValue > 0) {
            drawMenuButtons(canvas);
        }
        
        // Draw main FAB
        drawMainFab(canvas);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0) screenWidth = w;
        if (h > 0) screenHeight = h;

        // Keep FAB inside bounds after rotations / layout changes.
        float padding = dpToPx(5);
        mainFabX = Math.max(mainFabRadius + padding, Math.min(mainFabX, screenWidth - mainFabRadius - padding));
        mainFabY = Math.max(mainFabRadius + padding, Math.min(mainFabY, screenHeight - mainFabRadius - padding));
    }
    
    private void drawMainFab(Canvas canvas) {
        float t = Math.max(0f, Math.min(1f, expandedAnimationValue));

        // Blend teal -> surface when expanded
        int fill = lerpColor(mainFabColor, mainFabExpandedColor, t);
        mainPaint.setColor(fill);
        mainPaint.setAlpha(255);

        // Glow halo fades out as it expands
        float glowStrength = 1f - t;
        int glowColor = withAlpha(mainFabColor, 0.70f * glowStrength);
        mainGlowPaint.setColor(withAlpha(mainFabColor, 0.20f * glowStrength));
        mainGlowPaint.setShadowLayer(dpToPx(18) * glowStrength, 0, 0, glowColor);
        if (glowStrength > 0.01f) {
            canvas.drawCircle(mainFabX, mainFabY, mainFabRadius - dpToPx(2), mainGlowPaint);
        }

        // Main circle
        mainGlowPaint.setShadowLayer(0, 0, 0, 0);
        canvas.drawCircle(mainFabX, mainFabY, mainFabRadius, mainPaint);

        // Draw icon for main FAB (true centered baseline)
        textPaint.setTextSize(dpToPx(28));
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        String mainIcon = (isExpanded || expandedAnimationValue > 0.5f) ? "×" : "+";
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = mainFabY - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(mainIcon, mainFabX, baseline, textPaint);
    }
    
    private void drawMenuButtons(Canvas canvas) {
        // Pill geometry (matches the screenshot style)
        float pillHeight = dpToPx(44);
        float pillRadius = pillHeight / 2f;

        float alpha = Math.max(0f, Math.min(1f, expandedAnimationValue));
        updateMenuButtonsLayout(alpha);
        int bgColor = ContextCompat.getColor(getContext(), R.color.menu_surface_alt);
        int textColor = ContextCompat.getColor(getContext(), R.color.menu_text_primary);

        for (MenuButton button : menuButtons) {
            float cy = button.y;

            // Fill
            menuPaint.setStyle(Paint.Style.FILL);
            menuPaint.setColor(withAlpha(bgColor, 0.92f * alpha));
            canvas.drawRoundRect(button.hitRect, pillRadius, pillRadius, menuPaint);

            // Glow stroke
            int strokeColor = withAlpha(button.color, 0.95f * alpha);
            int glowStroke = withAlpha(button.color, 0.70f * alpha);
            menuStrokePaint.setColor(strokeColor);
            menuStrokePaint.setShadowLayer(dpToPx(10), 0, 0, glowStroke);
            canvas.drawRoundRect(button.hitRect, pillRadius, pillRadius, menuStrokePaint);

            // Crisp stroke
            menuStrokePaint.setShadowLayer(0, 0, 0, 0);
            canvas.drawRoundRect(button.hitRect, pillRadius, pillRadius, menuStrokePaint);

            // Icon bubble
            float pillLeft = button.hitRect.left;
            float iconCx = pillLeft + dpToPx(24);
            float iconCy = cy;
            float iconR = dpToPx(12);
            RectF iconRect = new RectF(iconCx - iconR, iconCy - iconR, iconCx + iconR, iconCy + iconR);

            menuPaint.setColor(withAlpha(bgColor, 1.0f * alpha));
            canvas.drawOval(iconRect, menuPaint);

            menuStrokePaint.setColor(strokeColor);
            menuStrokePaint.setShadowLayer(dpToPx(10), 0, 0, glowStroke);
            canvas.drawOval(iconRect, menuStrokePaint);
            menuStrokePaint.setShadowLayer(0, 0, 0, 0);
            canvas.drawOval(iconRect, menuStrokePaint);

            // Icon text
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(dpToPx(14));
            textPaint.setColor(withAlpha(textColor, alpha));
            Paint.FontMetrics fmI = textPaint.getFontMetrics();
            float iconBase = iconCy - (fmI.ascent + fmI.descent) / 2f;
            canvas.drawText(button.icon != null ? button.icon : "", iconCx, iconBase, textPaint);

            // Label
            menuTextPaint.setColor(withAlpha(textColor, alpha));
            menuTextPaint.setTextSize(dpToPx(14));
            Paint.FontMetrics fm = menuTextPaint.getFontMetrics();
            float textBase = cy - (fm.ascent + fm.descent) / 2f;
            float textX = iconCx + dpToPx(18);
            canvas.drawText(button.text != null ? button.text : "", textX, textBase, menuTextPaint);
        }
    }

    private void updateMenuButtonsLayout(float alpha) {
        // Pill geometry (matches the screenshot style)
        float pillHeight = dpToPx(44);
        float pillWidth = dpToPx(172);
        float step = pillHeight + dpToPx(14);
        float screenPadding = dpToPx(16);
        float viewWidth = getWidth() > 0 ? getWidth() : screenWidth;

        // All stacked ABOVE the main FAB (closest first)
        menuButtons[GOALS_BUTTON].x = mainFabX;
        menuButtons[GOALS_BUTTON].y = mainFabY - (step * 1f * alpha);

        menuButtons[REMIND_BUTTON].x = mainFabX;
        menuButtons[REMIND_BUTTON].y = mainFabY - (step * 2f * alpha);

        menuButtons[WALLPAPER_BUTTON].x = mainFabX;
        menuButtons[WALLPAPER_BUTTON].y = mainFabY - (step * 3f * alpha);

        menuButtons[IMAGE_BUTTON].x = mainFabX;
        menuButtons[IMAGE_BUTTON].y = mainFabY - (step * 4f * alpha);

        for (MenuButton button : menuButtons) {
            float cy = button.y;
            float desiredLeft = mainFabX - (pillWidth / 2f);
            float minLeft = screenPadding;
            float maxLeft = Math.max(minLeft, viewWidth - screenPadding - pillWidth);
            float pillLeft = Math.max(minLeft, Math.min(desiredLeft, maxLeft));
            float pillRight = pillLeft + pillWidth;
            float pillTop = cy - (pillHeight / 2f);
            float pillBottom = cy + (pillHeight / 2f);

            button.hitRect.set(pillLeft, pillTop, pillRight, pillBottom);
            button.touchRect.set(button.hitRect);
            // Slightly larger touch target for reliable tapping.
            button.touchRect.inset(-dpToPx(10), -dpToPx(10));
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                float touchX = event.getX();
                float touchY = event.getY();
                
                // Check if touching any menu button first (when expanded)
                if ((isExpanded || expandedAnimationValue > 0f) && expandedAnimationValue > 0f) {
                    updateMenuButtonsLayout(Math.max(0f, Math.min(1f, expandedAnimationValue)));
                    for (int i = 0; i < menuButtons.length; i++) {
                        MenuButton button = menuButtons[i];
                        if (button.contains(touchX, touchY)) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                            handleMenuButtonClick(i);
                            collapse();
                            ignoreNextUp = true;
                            return true;
                        }
                    }
                }
                
                // Check if touching main FAB
                float distanceToMain = distance(touchX, touchY, mainFabX, mainFabY);
                
                if (distanceToMain <= mainFabRadius + dpToPx(20)) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    // Start drag or expand/collapse
                    isDragging = false;
                    touchStartX = touchX;
                    touchStartY = touchY;
                    dragOffsetX = touchX - mainFabX;
                    dragOffsetY = touchY - mainFabY;
                    ignoreNextUp = false;
                    return true;
                }

                // If menu is open and user taps anywhere else, close it.
                if (isExpanded && expandedAnimationValue > 0f) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    collapse();
                    // Prevent ACTION_UP from toggling it open again.
                    ignoreNextUp = true;
                    return true;
                }
                break;
                
            case MotionEvent.ACTION_MOVE:
                float moveX = event.getX();
                float moveY = event.getY();
                
                float moveDistance = distance(moveX, moveY, touchStartX, touchStartY);
                
                if (moveDistance > dpToPx(10) && !isDragging) {
                    isDragging = true;
                    collapse();
                }
                
                if (isDragging) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    // Update position while dragging
                    mainFabX = moveX - dragOffsetX;
                    mainFabY = moveY - dragOffsetY;
                    
                    // Clamp to screen bounds
                    float viewWidth = getWidth() > 0 ? getWidth() : screenWidth;
                    float viewHeight = getHeight() > 0 ? getHeight() : screenHeight;
                    float padding = dpToPx(5);
                    mainFabX = Math.max(mainFabRadius + padding, Math.min(mainFabX, viewWidth - mainFabRadius - padding));
                    mainFabY = Math.max(mainFabRadius + padding, Math.min(mainFabY, viewHeight - mainFabRadius - padding));
                    
                    invalidate();
                }
                return true;
                
            case MotionEvent.ACTION_UP:
                if (ignoreNextUp) {
                    ignoreNextUp = false;
                    isDragging = false;
                    return true;
                }
                if (isDragging) {
                    // Just stop dragging - no snapping to corners
                    isDragging = false;
                } else {
                    // Toggle expand/collapse on main FAB click
                    setExpanded(!isExpanded);
                    performClick();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                ignoreNextUp = false;
                isDragging = false;
                return true;
        }
        
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
    
    private void setExpanded(boolean expanded) {
        if (expanded == isExpanded) return; // Already in desired state
        
        if (expanded) {
            expand();
        } else {
            collapse();
        }
    }
    
    
    
    @SuppressWarnings("unused")
    public void setFabX(float x) {
        this.mainFabX = x;
        invalidate();
    }
    
    @SuppressWarnings("unused")
    public void setFabY(float y) {
        this.mainFabY = y;
        invalidate();
    }
    
    public float getFabX() {
        return mainFabX;
    }
    
    public float getFabY() {
        return mainFabY;
    }
    
    private void expand() {
        isExpanded = true;
        ObjectAnimator animator = ObjectAnimator.ofFloat(this, "expandedAnimationValue", 0f, 1f);
        animator.setDuration(300);
        animator.start();
    }
    
    private void collapse() {
        isExpanded = false;
        ObjectAnimator animator = ObjectAnimator.ofFloat(this, "expandedAnimationValue", expandedAnimationValue, 0f);
        animator.setDuration(300);
        animator.start();
    }

    // Used by activities before launching pickers to ensure the dim overlay isn't left on-screen.
    public void collapseImmediately() {
        isExpanded = false;
        expandedAnimationValue = 0f;
        invalidate();
    }
    
    @SuppressWarnings("unused")
    public void setExpandedAnimationValue(float value) {
        this.expandedAnimationValue = value;
        invalidate();
    }
    
    public float getExpandedAnimationValue() {
        return expandedAnimationValue;
    }
    
    private void handleMenuButtonClick(int buttonIndex) {
        if (menuListener == null) return;
        
        switch (buttonIndex) {
            case IMAGE_BUTTON:
                menuListener.onImageButtonClick();
                break;
            case GOALS_BUTTON:
                menuListener.onGoalsButtonClick();
                break;
            case WALLPAPER_BUTTON:
                menuListener.onWallpaperButtonClick();
                break;
            case REMIND_BUTTON:
                menuListener.onReminderButtonClick();
                break;
        }
    }
    
    public void setMenuListener(FABMenuListener listener) {
        this.menuListener = listener;
    }
    
    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    private float dpToPx(float dp) {
        return dp * Resources.getSystem().getDisplayMetrics().density;
    }

    private int withAlpha(int color, float alpha01) {
        int a = Math.max(0, Math.min(255, (int) (255f * alpha01)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private int lerpColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a1 = (from >> 24) & 0xFF;
        int r1 = (from >> 16) & 0xFF;
        int g1 = (from >> 8) & 0xFF;
        int b1 = from & 0xFF;

        int a2 = (to >> 24) & 0xFF;
        int r2 = (to >> 16) & 0xFF;
        int g2 = (to >> 8) & 0xFF;
        int b2 = to & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
