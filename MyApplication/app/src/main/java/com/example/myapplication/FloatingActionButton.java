package com.example.myapplication;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.util.Property;

public class FloatingActionButton extends View {
    
    // Main FAB properties
    private float mainFabRadius = 50f; // 100dp diameter
    private float mainFabX;
    private float mainFabY;
    private float mainFabColor = 0xFF2196F3;
    
    // Menu properties
    private float menuButtonRadius = 37.5f; // 75dp diameter
    private float expandedDistance = 220f;
    private float expandedAnimationValue = 0f;
    
    // Menu buttons
    private MenuButton[] menuButtons = new MenuButton[2];
    private static final int IMAGE_BUTTON = 0;
    private static final int GOALS_BUTTON = 1;
    
    // Touch and drag properties
    private boolean isDragging = false;
    private float touchStartX;
    private float touchStartY;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean isExpanded = false;
    
    // Paint objects
    private Paint mainPaint;
    private Paint menuPaint;
    private Paint textPaint;
    private Paint overlayPaint;
    
    // Screen dimensions
    private float screenWidth;
    private float screenHeight;
    private float statusBarHeight;
    
    // Interface for menu callbacks
    public interface FABMenuListener {
        void onImageButtonClick();
        void onGoalsButtonClick();
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
        
        MenuButton(float x, float y, float radius, int color, String label) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.color = color;
            this.label = label;
            this.textColor = 0xFFffffff;
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
        
        // Initialize position to bottom-right corner
        mainFabX = screenWidth - dpToPx(50) - dpToPx(16);
        mainFabY = screenHeight - dpToPx(50) - dpToPx(16);
        
        // Initialize paint objects
        mainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mainPaint.setColor((int) mainFabColor);
        mainPaint.setStyle(Paint.Style.FILL);
        mainPaint.setShadowLayer(dpToPx(4), 0, dpToPx(2), 0x40000000);
        
        menuPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        menuPaint.setStyle(Paint.Style.FILL);
        menuPaint.setShadowLayer(dpToPx(2), 0, dpToPx(1), 0x40000000);
        
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFFffffff);
        textPaint.setTextSize(dpToPx(14));
        textPaint.setTextAlign(Paint.Align.CENTER);
        
        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setColor(0x66000000);
        overlayPaint.setStyle(Paint.Style.FILL);
        
        // Initialize menu buttons
        menuButtons[IMAGE_BUTTON] = new MenuButton(0, 0, menuButtonRadius, 0xFF2196F3, "🖼");
        menuButtons[GOALS_BUTTON] = new MenuButton(0, 0, menuButtonRadius, 0xFFFF9800, "✓");
        
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
    
    private void drawMainFab(Canvas canvas) {
        mainPaint.setAlpha(255);
        canvas.drawCircle(mainFabX, mainFabY, mainFabRadius, mainPaint);
        
        // Draw icon for main FAB
        textPaint.setTextSize(dpToPx(28));
        textPaint.setColor(0xFFffffff);
        String mainIcon = "+";
        canvas.drawText(mainIcon, mainFabX, mainFabY + dpToPx(7), textPaint);
    }
    
    private void drawMenuButtons(Canvas canvas) {
        // Update menu button positions based on animation value
        float distance = expandedDistance * expandedAnimationValue;
        
        // Image button (top)
        menuButtons[IMAGE_BUTTON].x = mainFabX;
        menuButtons[IMAGE_BUTTON].y = mainFabY - distance;
        
        // Goals button (bottom)
        menuButtons[GOALS_BUTTON].x = mainFabX;
        menuButtons[GOALS_BUTTON].y = mainFabY + distance;
        
        // Draw each menu button
        for (MenuButton button : menuButtons) {
            menuPaint.setColor(button.color);
            menuPaint.setAlpha((int) (255 * expandedAnimationValue));
            canvas.drawCircle(button.x, button.y, button.radius, menuPaint);
            
            // Draw label/icon
            textPaint.setTextSize(dpToPx(18));
            textPaint.setColor(button.textColor);
            canvas.drawText(button.label, button.x, button.y + dpToPx(5), textPaint);
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                float touchX = event.getRawX();
                float touchY = event.getRawY() - statusBarHeight;
                
                // Check if touching any menu button first (when expanded)
                if (isExpanded && expandedAnimationValue > 0.2f) {
                    for (int i = 0; i < menuButtons.length; i++) {
                        MenuButton button = menuButtons[i];
                        float distanceToButton = distance(touchX, touchY, button.x, button.y);
                        if (distanceToButton <= button.radius + dpToPx(25)) {
                            handleMenuButtonClick(i);
                            collapse();
                            return true;
                        }
                    }
                }
                
                // Check if touching main FAB
                float distanceToMain = distance(touchX, touchY, mainFabX, mainFabY);
                
                if (distanceToMain <= mainFabRadius + dpToPx(20)) {
                    // Start drag or expand/collapse
                    isDragging = false;
                    touchStartX = touchX;
                    touchStartY = touchY;
                    dragOffsetX = touchX - mainFabX;
                    dragOffsetY = touchY - mainFabY;
                    return true;
                }
                break;
                
            case MotionEvent.ACTION_MOVE:
                float moveX = event.getRawX();
                float moveY = event.getRawY() - statusBarHeight;
                
                float moveDistance = distance(moveX, moveY, touchStartX, touchStartY);
                
                if (moveDistance > dpToPx(10) && !isDragging) {
                    isDragging = true;
                    collapse();
                }
                
                if (isDragging) {
                    // Update position while dragging
                    mainFabX = moveX - dragOffsetX;
                    mainFabY = moveY - dragOffsetY;
                    
                    // Clamp to screen bounds
                    mainFabX = Math.max(mainFabRadius + dpToPx(5), Math.min(mainFabX, screenWidth - mainFabRadius - dpToPx(5)));
                    mainFabY = Math.max(mainFabRadius + statusBarHeight + dpToPx(5), Math.min(mainFabY, screenHeight - mainFabRadius - dpToPx(20)));
                    
                    invalidate();
                }
                return true;
                
            case MotionEvent.ACTION_UP:
                if (isDragging) {
                    // Just stop dragging - no snapping to corners
                    isDragging = false;
                } else {
                    // Toggle expand/collapse on main FAB click
                    setExpanded(!isExpanded);
                }
                return true;
        }
        
        return super.onTouchEvent(event);
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
}
