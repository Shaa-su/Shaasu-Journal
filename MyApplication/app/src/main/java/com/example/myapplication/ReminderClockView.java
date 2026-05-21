package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class ReminderClockView extends View {

    public enum Mode { HOUR, MINUTE }

    private Mode mode = Mode.HOUR;
    private int selectedHour24 = 9;
    private int selectedMinute = 0;
    private boolean is24Hour = false;

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private OnTimeChangeListener listener;

    public interface OnTimeChangeListener {
        void onTimeChanged(int hour24, int minute, Mode mode);
    }

    public ReminderClockView(Context context) {
        super(context);
        init();
    }

    public ReminderClockView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ReminderClockView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(2));
        ringPaint.setColor(ContextCompat.getColor(getContext(), R.color.menu_teal));
        ringPaint.setAlpha(80);

        numberPaint.setColor(ContextCompat.getColor(getContext(), R.color.menu_teal));
        numberPaint.setTextSize(dp(14));
        numberPaint.setTextAlign(Paint.Align.CENTER);

        handPaint.setColor(ContextCompat.getColor(getContext(), R.color.menu_teal));
        handPaint.setStrokeWidth(dp(3));
        handPaint.setStyle(Paint.Style.STROKE);

        centerPaint.setColor(ContextCompat.getColor(getContext(), R.color.menu_teal));
        centerPaint.setStyle(Paint.Style.FILL);

        selectedPaint.setColor(ContextCompat.getColor(getContext(), R.color.menu_teal));
        selectedPaint.setStyle(Paint.Style.FILL);
    }

    public void setMode(Mode mode) {
        if (mode == null) return;
        this.mode = mode;
        invalidate();
    }

    public Mode getMode() {
        return mode;
    }

    public void setTime(int hour24, int minute, boolean is24Hour) {
        this.is24Hour = is24Hour;
        this.selectedHour24 = Math.max(0, Math.min(23, hour24));
        this.selectedMinute = Math.max(0, Math.min(59, minute));
        invalidate();
    }

    public int getHour24() {
        return selectedHour24;
    }

    public int getMinute() {
        return selectedMinute;
    }

    public void setOnTimeChangeListener(OnTimeChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * 0.38f;

        canvas.drawCircle(cx, cy, radius, ringPaint);

        int count = 12;
        for (int i = 0; i < count; i++) {
            float angle = (float) Math.toRadians((i * 30) - 90);
            float tx = cx + (float) Math.cos(angle) * (radius - dp(10));
            float ty = cy + (float) Math.sin(angle) * (radius - dp(10));

            String label;
            if (mode == Mode.HOUR) {
                int h = i == 0 ? 12 : i;
                label = String.valueOf(h);
            } else {
                int m = (i * 5) % 60;
                label = String.format("%02d", m);
            }

            Paint.FontMetrics fm = numberPaint.getFontMetrics();
            float textY = ty - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(label, tx, textY, numberPaint);
        }

        int index = 0;
        if (mode == Mode.HOUR) {
            int hour12 = selectedHour24 % 12;
            index = hour12 == 0 ? 0 : hour12;
        } else {
            index = Math.round(selectedMinute / 5f) % 12;
        }

        float selAngle = (float) Math.toRadians((index * 30) - 90);
        float handX = cx + (float) Math.cos(selAngle) * (radius - dp(16));
        float handY = cy + (float) Math.sin(selAngle) * (radius - dp(16));

        canvas.drawLine(cx, cy, handX, handY, handPaint);
        canvas.drawCircle(cx, cy, dp(4), centerPaint);
        canvas.drawCircle(handX, handY, dp(16), selectedPaint);

        // Selected number on top of the bubble
        String selLabel;
        if (mode == Mode.HOUR) {
            int hour12 = selectedHour24 % 12;
            selLabel = String.valueOf(hour12 == 0 ? 12 : hour12);
        } else {
            int m = Math.round(selectedMinute / 5f) * 5;
            if (m == 60) m = 0;
            selLabel = String.format("%02d", m);
        }

        numberPaint.setColor(ContextCompat.getColor(getContext(), R.color.menu_text_primary));
        Paint.FontMetrics fmS = numberPaint.getFontMetrics();
        float selTextY = handY - (fmS.ascent + fmS.descent) / 2f;
        canvas.drawText(selLabel, handX, selTextY, numberPaint);
        numberPaint.setColor(ContextCompat.getColor(getContext(), R.color.menu_teal));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
            return true;
        }
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float dx = event.getX() - cx;
        float dy = event.getY() - cy;
        double angle = Math.atan2(dy, dx);
        double degrees = Math.toDegrees(angle) + 90;
        if (degrees < 0) degrees += 360;
        int index = (int) Math.round(degrees / 30.0) % 12;

        if (mode == Mode.HOUR) {
            int hour12 = index == 0 ? 12 : index;
            int base = selectedHour24 >= 12 ? 12 : 0;
            int newHour = (hour12 % 12) + base;
            if (newHour == 24) newHour = 0;
            selectedHour24 = newHour;
        } else {
            int m = index * 5;
            if (m == 60) m = 0;
            selectedMinute = m;
        }

        if (listener != null) {
            listener.onTimeChanged(selectedHour24, selectedMinute, mode);
        }
        invalidate();
        return true;
    }

    private float dp(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
