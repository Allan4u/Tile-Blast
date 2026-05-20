package com.allan.tileblast.daily;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

import com.allan.tileblast.R;

import java.util.Collections;
import java.util.List;

/**
 * Custom view rendering a 6×5 grid of the most recent 30 daily-challenge days.
 * Most recent day appears at the bottom-right; earliest at the top-left.
 *
 * <p>Cells display:
 * <ul>
 *   <li><b>empty</b> — gray outline (no attempt)</li>
 *   <li><b>played, no star</b> — score text on a dim fill</li>
 *   <li><b>starred</b> — gold star on a bright fill</li>
 *   <li><b>today</b> — additional highlighted border</li>
 * </ul>
 *
 * <p>Above the grid, the current streak count is displayed as
 * "🔥 N day" / "🔥 N days".
 */
public class CalendarView extends View {

    private static final int COLS = 5;
    private static final int ROWS = 6;
    private static final int CELLS = COLS * ROWS;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Typeface fontRegular;
    private Typeface fontBold;
    private float density;

    private List<DayEntry> entries = Collections.emptyList();
    private int streakCount = 0;

    public CalendarView(Context context) { super(context); init(context); }
    public CalendarView(Context context, AttributeSet attrs) { super(context, attrs); init(context); }
    public CalendarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context ctx) {
        density = getResources().getDisplayMetrics().density;
        try {
            fontRegular = ResourcesCompat.getFont(ctx, R.font.silkscreen);
            fontBold = ResourcesCompat.getFont(ctx, R.font.silkscreen_bold);
        } catch (Exception ignored) {
            fontRegular = Typeface.DEFAULT;
            fontBold = Typeface.DEFAULT_BOLD;
        }
        setBackgroundColor(Color.BLACK);
    }

    /**
     * Provides the data to render. {@code entries} should be in chronological
     * order (oldest first) with length up to 30; if shorter, leading cells
     * render as empty.
     */
    public void setData(List<DayEntry> entries, int streakCount) {
        this.entries = (entries != null) ? entries : Collections.emptyList();
        this.streakCount = Math.max(0, streakCount);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Streak header
        float headerHeight = 56 * density;
        textPaint.setTypeface(fontBold);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(20 * density);
        textPaint.setColor(0xFFFFD700);
        String streakText = "\uD83D\uDD25 " + streakCount + (streakCount == 1 ? " day" : " days");
        canvas.drawText(streakText, w / 2f, headerHeight * 0.65f, textPaint);

        // Grid area below the header
        float gridTop = headerHeight + 8 * density;
        float gridBottom = h - 8 * density;
        float gridHeight = gridBottom - gridTop;

        float spacing = 6 * density;
        float padding = 12 * density;
        float cellW = (w - 2 * padding - spacing * (COLS - 1)) / COLS;
        float cellH = (gridHeight - spacing * (ROWS - 1)) / ROWS;
        float cellSize = Math.min(cellW, cellH);

        // Recompute total grid extents for centering
        float totalGridW = COLS * cellSize + (COLS - 1) * spacing;
        float totalGridH = ROWS * cellSize + (ROWS - 1) * spacing;
        float startX = (w - totalGridW) / 2f;
        float startY = gridTop + (gridHeight - totalGridH) / 2f;

        // Number of entries we have; align to the bottom-right.
        int entryCount = Math.min(entries.size(), CELLS);
        // First displayed cell index in the grid (top-left) — empty cells leading.
        int firstDataIdx = CELLS - entryCount;

        for (int i = 0; i < CELLS; i++) {
            int row = i / COLS;
            int col = i % COLS;
            float left = startX + col * (cellSize + spacing);
            float top = startY + row * (cellSize + spacing);
            RectF rect = new RectF(left, top, left + cellSize, top + cellSize);

            if (i < firstDataIdx) {
                drawEmptyCell(canvas, rect);
            } else {
                DayEntry e = entries.get(i - firstDataIdx);
                drawDayCell(canvas, rect, e, cellSize);
            }
        }
    }

    private void drawEmptyCell(Canvas canvas, RectF rect) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        paint.setColor(0xFF333333);
        canvas.drawRoundRect(rect, 6 * density, 6 * density, paint);
    }

    private void drawDayCell(Canvas canvas, RectF rect, DayEntry e, float cellSize) {
        // Fill
        paint.setStyle(Paint.Style.FILL);
        if (e.starred) {
            paint.setColor(0xFF4A3A0A); // bright dimmed-gold fill
        } else if (e.score > 0) {
            paint.setColor(0xFF1A1A2E); // dim fill — played but no star
        } else {
            paint.setColor(0xFF0A0A14); // not attempted
        }
        canvas.drawRoundRect(rect, 6 * density, 6 * density, paint);

        // Outline (today highlighted differently)
        paint.setStyle(Paint.Style.STROKE);
        if (e.isToday) {
            paint.setStrokeWidth(2.5f * density);
            paint.setColor(0xFFFFFFFF);
        } else if (e.starred) {
            paint.setStrokeWidth(1.5f * density);
            paint.setColor(0xFFFFD700);
        } else {
            paint.setStrokeWidth(1.5f * density);
            paint.setColor(0xFF555555);
        }
        canvas.drawRoundRect(rect, 6 * density, 6 * density, paint);

        if (e.starred) {
            // Gold star glyph
            textPaint.setTypeface(fontBold);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(cellSize * 0.55f);
            textPaint.setColor(0xFFFFD700);
            float cx = rect.centerX();
            float cy = rect.centerY() + cellSize * 0.18f;
            canvas.drawText("\u2605", cx, cy, textPaint);
        } else if (e.score > 0) {
            // Display score (compact)
            textPaint.setTypeface(fontRegular);
            textPaint.setTextAlign(Paint.Align.CENTER);
            float ts = cellSize * 0.28f;
            textPaint.setTextSize(ts);
            textPaint.setColor(0xFFCCCCCC);
            String text = formatScore(e.score);
            float cx = rect.centerX();
            float cy = rect.centerY() + ts * 0.35f;
            canvas.drawText(text, cx, cy, textPaint);
        }
    }

    private String formatScore(int score) {
        if (score >= 1000) {
            float k = score / 1000f;
            // Use one decimal place for sub-10k values.
            if (score < 10000) {
                return String.format("%.1fk", k);
            }
            return ((int) k) + "k";
        }
        return Integer.toString(score);
    }
}
