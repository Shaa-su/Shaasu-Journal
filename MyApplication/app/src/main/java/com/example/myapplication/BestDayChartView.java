package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class BestDayChartView extends View {

    private static final int GOOD_COLOR = 0xFF19BFAE;
    private static final int OTHER_COLOR = 0xFF2A5A55;
    private static final int EMPTY_COLOR = 0xFF1A3A3A;
    private static final int AXIS_LABEL_COLOR = 0xFF5A5A5A;
    private static final int PCT_LABEL_COLOR = 0xFF757575;
    private static final int GRID_COLOR = 0x1A000000;

    private Paint goodPaint;
    private Paint otherPaint;
    private Paint emptyPaint;
    private Paint labelPaint;
    private Paint pctPaint;
    private Paint gridPaint;
    private Paint legendPaint;
    private Paint legendTextPaint;

    private int[] goodCounts = new int[7]; // Sun=0, Mon=1, ...
    private int[] otherCounts = new int[7];
    private int maxTotal = 0;

    private static final String[] DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String[] PCT_LABELS = {"0%", "20%", "40%", "60%", "80%", "100%"};

    public BestDayChartView(Context context) {
        super(context);
        init();
    }

    public BestDayChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BestDayChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        goodPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        goodPaint.setColor(GOOD_COLOR);
        goodPaint.setStyle(Paint.Style.FILL);

        otherPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        otherPaint.setColor(OTHER_COLOR);
        otherPaint.setStyle(Paint.Style.FILL);

        emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emptyPaint.setColor(EMPTY_COLOR);
        emptyPaint.setStyle(Paint.Style.FILL);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(AXIS_LABEL_COLOR);
        labelPaint.setTextSize(dpToPx(12));
        labelPaint.setTextAlign(Paint.Align.RIGHT);

        pctPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pctPaint.setColor(PCT_LABEL_COLOR);
        pctPaint.setTextSize(dpToPx(9));
        pctPaint.setTextAlign(Paint.Align.CENTER);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(GRID_COLOR);
        gridPaint.setStrokeWidth(dpToPx(0.5f));

        legendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legendPaint.setStyle(Paint.Style.FILL);

        legendTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legendTextPaint.setColor(AXIS_LABEL_COLOR);
        legendTextPaint.setTextSize(dpToPx(10));
    }

    public void setData(int[] good, int[] other) {
        if (good != null && good.length == 7) goodCounts = good;
        if (other != null && other.length == 7) otherCounts = other;

        maxTotal = 0;
        for (int i = 0; i < 7; i++) {
            int total = goodCounts[i] + otherCounts[i];
            if (total > maxTotal) maxTotal = total;
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        int leftMargin = dpToPx(40);
        int rightMargin = dpToPx(16);
        int topMargin = dpToPx(36);
        int bottomMargin = dpToPx(24);

        int chartLeft = leftMargin;
        int chartRight = width - rightMargin;
        int chartTop = topMargin;
        int chartBottom = height - bottomMargin;
        int chartWidth = chartRight - chartLeft;
        int chartHeight = chartBottom - chartTop;

        float rowHeight = chartHeight / 7f;

        // ─── Legend ──────────────────────────────────
        float legendY = dpToPx(8);
        // Good moods legend
        legendPaint.setColor(GOOD_COLOR);
        canvas.drawRect(chartLeft, legendY, chartLeft + dpToPx(14), legendY + dpToPx(6), legendPaint);
        canvas.drawText("Good moods", chartLeft + dpToPx(20), legendY + dpToPx(6), legendTextPaint);

        // Other moods legend
        float otherLegendX = chartLeft + dpToPx(90);
        legendPaint.setColor(OTHER_COLOR);
        canvas.drawRect(otherLegendX, legendY, otherLegendX + dpToPx(14), legendY + dpToPx(6), legendPaint);
        canvas.drawText("Other moods", otherLegendX + dpToPx(20), legendY + dpToPx(6), legendTextPaint);

        // ─── Vertical gridlines & X-axis labels ─────
        for (int i = 0; i < 6; i++) {
            float x = chartLeft + chartWidth * i / 5f;

            // Gridline
            if (i > 0) {
                canvas.drawLine(x, chartTop, x, chartBottom, gridPaint);
            }

            // Percentage label
            canvas.drawText(PCT_LABELS[i], x, chartBottom + dpToPx(16), pctPaint);
        }

        // ─── Rows ────────────────────────────────────
        for (int day = 0; day < 7; day++) {
            float rowTop = chartTop + rowHeight * day;
            float rowBottom = rowTop + rowHeight;
            float rowCenterY = (rowTop + rowBottom) / 2f;
            float rowPad = dpToPx(3);
            float barTop = rowTop + rowPad;
            float barBottom = rowBottom - rowPad;

            int good = goodCounts[day];
            int other = otherCounts[day];
            int total = good + other;

            // Draw full-width empty track
            RectF track = new RectF(chartLeft, barTop, chartRight, barBottom);
            float radius = dpToPx(4);
            canvas.drawRoundRect(track, radius, radius, emptyPaint);

            if (total > 0 && maxTotal > 0) {
                float goodWidth = chartWidth * good / (float) maxTotal;
                float otherWidth = chartWidth * other / (float) maxTotal;

                // Good segment — solid cyan
                if (good > 0) {
                    canvas.drawRect(chartLeft, barTop, chartLeft + goodWidth, barBottom, goodPaint);
                    // Good count label
                    String label = String.valueOf(good);
                    float labelX = chartLeft + goodWidth / 2f;
                    float labelY = rowCenterY + dpToPx(4);
                    Paint countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    countPaint.setColor(0xFFFFFFFF);
                    countPaint.setTextSize(dpToPx(10));
                    countPaint.setTextAlign(Paint.Align.CENTER);
                    countPaint.setFakeBoldText(true);
                    canvas.drawText(label, labelX, labelY, countPaint);
                }

                // Other segment — dark teal
                if (other > 0) {
                    float otherStart = chartLeft + goodWidth;
                    canvas.drawRect(otherStart, barTop, otherStart + otherWidth, barBottom, otherPaint);
                    // Other count label
                    String label = String.valueOf(other);
                    float labelX = otherStart + otherWidth / 2f;
                    float labelY = rowCenterY + dpToPx(4);
                    Paint countPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
                    countPaint2.setColor(0xFFFFFFFF);
                    countPaint2.setTextSize(dpToPx(10));
                    countPaint2.setTextAlign(Paint.Align.CENTER);
                    countPaint2.setFakeBoldText(true);
                    canvas.drawText(label, labelX, labelY, countPaint2);
                }
            }

            // Day label
            float labelY = rowCenterY + dpToPx(4);
            canvas.drawText(DAYS[day], chartLeft - dpToPx(8), labelY, labelPaint);
        }
    }

    private int dpToPx(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
