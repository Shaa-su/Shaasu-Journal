package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MoodBarChartView extends View {

    private static final int BAR_COLOR = 0xFF19BFAE;
    private static final int AXIS_COLOR = 0xFFD1D1D1;
    private static final int LABEL_COLOR = 0xFF757575;
    private static final int GRID_COLOR = 0x1A000000;

    private Paint barPaint;
    private Paint axisPaint;
    private Paint labelPaint;
    private Paint gridPaint;
    private Paint emojiPaint;

    private List<Mood> moods = new ArrayList<>();
    private Map<String, Integer> moodCounts;
    private int maxCount = 0;
    private int yAxisSteps = 4; // 0, 2, 4, 6, 8

    public MoodBarChartView(Context context) {
        super(context);
        init();
    }

    public MoodBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MoodBarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(BAR_COLOR);
        barPaint.setStyle(Paint.Style.FILL);

        axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setColor(AXIS_COLOR);
        axisPaint.setStrokeWidth(dpToPx(1));

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(LABEL_COLOR);
        labelPaint.setTextSize(dpToPx(10));
        labelPaint.setTextAlign(Paint.Align.RIGHT);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(GRID_COLOR);
        gridPaint.setStrokeWidth(dpToPx(0.5f));

        emojiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emojiPaint.setTextSize(dpToPx(18));
        emojiPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setMoodData(List<Mood> allMoods, Map<String, Integer> counts) {
        this.moods = new ArrayList<>(allMoods);
        this.moodCounts = counts;

        // Find max count and calculate steps
        maxCount = 0;
        for (Mood m : moods) {
            Integer c = counts.get(m.id);
            if (c != null && c > maxCount) maxCount = c;
        }

        // Round up to nearest even number for Y-axis
        if (maxCount <= 0) {
            maxCount = 4; // Default to 4 if no data
        }
        yAxisSteps = Math.max(2, ((maxCount + 1) / 2) * 2);
        if (yAxisSteps < maxCount) yAxisSteps += 2;

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Margins
        int leftMargin = dpToPx(32);
        int rightMargin = dpToPx(12);
        int topMargin = dpToPx(8);
        int bottomMargin = dpToPx(32);

        int chartLeft = leftMargin;
        int chartRight = width - rightMargin;
        int chartTop = topMargin;
        int chartBottom = height - bottomMargin;
        int chartWidth = chartRight - chartLeft;
        int chartHeight = chartBottom - chartTop;

        int barCount = moods.size();
        if (barCount == 0) return;

        float barSpacing = chartWidth / (float) barCount;
        float barWidth = barSpacing * 0.6f;

        int numSteps = yAxisSteps / 2; // 0, 2, 4, 6, 8 → 4 steps above 0

        // Draw Y-axis labels and gridlines
        for (int i = 0; i <= numSteps; i++) {
            int value = i * 2;
            float y = chartBottom - (chartHeight * value / (float) yAxisSteps);

            // Gridline
            if (i > 0) {
                canvas.drawLine(chartLeft, y, chartRight, y, gridPaint);
            }

            // Label
            String label = String.valueOf(value);
            canvas.drawText(label, chartLeft - dpToPx(6), y + dpToPx(4), labelPaint);
        }

        // Draw X-axis baseline
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint);

        // Draw Y-axis line
        canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, axisPaint);

        // Draw bars and emoji labels
        for (int i = 0; i < barCount; i++) {
            Mood mood = moods.get(i);
            Integer count = moodCounts != null ? moodCounts.get(mood.id) : null;
            int c = (count != null) ? count : 0;

            float barCenterX = chartLeft + barSpacing * i + barSpacing / 2f;
            float barHeight = (yAxisSteps > 0) ? (chartHeight * c / (float) yAxisSteps) : 0;
            float barTop = chartBottom - barHeight;

            // Bar with rounded top corners
            if (barHeight > 0) {
                float barLeft = barCenterX - barWidth / 2f;
                float barRight = barCenterX + barWidth / 2f;
                float radius = dpToPx(3);

                RectF rect = new RectF(barLeft, barTop, barRight, chartBottom);
                canvas.drawRoundRect(rect, radius, radius, barPaint);
            }

            // Emoji label below X-axis
            float emojiY = chartBottom + dpToPx(22);
            canvas.drawText(mood.emoji, barCenterX, emojiY, emojiPaint);
        }
    }

    private int dpToPx(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
