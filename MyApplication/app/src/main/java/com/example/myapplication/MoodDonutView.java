package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public final class MoodDonutView extends View {

    private int goodCount;
    private int normalCount;
    private int badCount;

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    public MoodDonutView(Context context) {
        super(context);
        init();
    }

    public MoodDonutView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MoodDonutView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setData(int good, int normal, int bad) {
        this.goodCount = Math.max(0, good);
        this.normalCount = Math.max(0, normal);
        this.badCount = Math.max(0, bad);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float strokePx = dp(12f);
        float halfStroke = strokePx / 2f;

        int w = getWidth();
        int h = getHeight();
        int size = Math.min(w, h);
        float left = (w - size) / 2f + halfStroke;
        float top = (h - size) / 2f + halfStroke;
        float right = left + size - strokePx;
        float bottom = top + size - strokePx;

        arcRect.set(left, top, right, bottom);

        ringPaint.setStrokeWidth(strokePx);
        trackPaint.setStrokeWidth(strokePx);

        int total = goodCount + normalCount + badCount;

        // Track ring
        trackPaint.setColor(getResources().getColor(R.color.menu_surface_alt));
        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint);

        if (total <= 0) {
            return;
        }

        float startAngle = -90f;

        // Good (teal)
        float goodSweep = 360f * goodCount / (float) total;
        ringPaint.setColor(getResources().getColor(R.color.menu_teal));
        if (goodSweep > 0.5f) {
            canvas.drawArc(arcRect, startAngle, goodSweep, false, ringPaint);
        }
        startAngle += goodSweep;

        // Normal (secondary)
        float normalSweep = 360f * normalCount / (float) total;
        ringPaint.setColor(getResources().getColor(R.color.menu_text_secondary));
        if (normalSweep > 0.5f) {
            canvas.drawArc(arcRect, startAngle, normalSweep, false, ringPaint);
        }
        startAngle += normalSweep;

        // Bad (purple accent)
        float badSweep = 360f * badCount / (float) total;
        ringPaint.setColor(getResources().getColor(R.color.fab_image));
        if (badSweep > 0.5f) {
            canvas.drawArc(arcRect, startAngle, badSweep, false, ringPaint);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
