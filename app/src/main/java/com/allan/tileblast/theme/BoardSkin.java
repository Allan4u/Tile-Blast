package com.allan.tileblast.theme;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * Board skins that paint the grid background.
 *
 * Each variant supplies its own Canvas-based rendering strategy, so no bitmap
 * assets are added to the APK. Skins have an unlock threshold based on
 * cumulative total score across all games.
 *
 * Validates: Requirements 2.1, 2.2, 5.3
 */
public enum BoardSkin {
    DEFAULT("Default", 0) {
        @Override
        public void drawBackground(Canvas c, RectF rect, int blockSize, Paint paint) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.BLACK);
            c.drawRect(rect, paint);
        }
    },
    WOOD("Wood", 5000) {
        @Override
        public void drawBackground(Canvas c, RectF rect, int blockSize, Paint paint) {
            // Brown vertical gradient
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                Color.rgb(101, 67, 33),
                Color.rgb(58, 36, 16),
                Shader.TileMode.CLAMP));
            c.drawRect(rect, paint);
            paint.setShader(null);

            // Horizontal grain lines
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(Color.argb(60, 30, 18, 8));
            float step = Math.max(blockSize / 4f, 6f);
            for (float y = rect.top + step / 2f; y < rect.bottom; y += step) {
                c.drawLine(rect.left, y, rect.right, y, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }
    },
    METAL("Metal", 25000) {
        @Override
        public void drawBackground(Canvas c, RectF rect, int blockSize, Paint paint) {
            // Gray vertical gradient
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                Color.rgb(140, 140, 150),
                Color.rgb(60, 60, 70),
                Shader.TileMode.CLAMP));
            c.drawRect(rect, paint);
            paint.setShader(null);

            // 45 degree hatching for brushed-metal effect
            c.save();
            c.clipRect(rect);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(Color.argb(40, 220, 220, 230));
            float step = Math.max(blockSize / 6f, 4f);
            float diag = rect.width() + rect.height();
            for (float i = -rect.height(); i < diag; i += step) {
                c.drawLine(rect.left + i, rect.top,
                           rect.left + i + rect.height(), rect.bottom, paint);
            }
            c.restore();
            paint.setStyle(Paint.Style.FILL);
        }
    },
    SPACE("Space", 50000) {
        @Override
        public void drawBackground(Canvas c, RectF rect, int blockSize, Paint paint) {
            // Dark blue vertical gradient (deep space)
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                Color.rgb(8, 12, 40),
                Color.rgb(2, 4, 16),
                Shader.TileMode.CLAMP));
            c.drawRect(rect, paint);
            paint.setShader(null);

            // Procedurally placed star dots (deterministic per-frame layout based on rect)
            paint.setStyle(Paint.Style.FILL);
            long seed = ((long) Float.floatToIntBits(rect.width())) * 31
                + Float.floatToIntBits(rect.height());
            Random rnd = new Random(seed);
            int starCount = Math.max(20, (int) (rect.width() * rect.height() / 4000f));
            for (int i = 0; i < starCount; i++) {
                float sx = rect.left + rnd.nextFloat() * rect.width();
                float sy = rect.top + rnd.nextFloat() * rect.height();
                float radius = 0.6f + rnd.nextFloat() * 1.6f;
                int alpha = 120 + rnd.nextInt(135);
                paint.setColor(Color.argb(alpha, 255, 255, 255));
                c.drawCircle(sx, sy, radius, paint);
            }
        }
    },
    PIXEL_ART("Pixel Art", 100000) {
        @Override
        public void drawBackground(Canvas c, RectF rect, int blockSize, Paint paint) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            // Base color
            paint.setColor(Color.rgb(28, 28, 36));
            c.drawRect(rect, paint);

            // 2x2 alternating-color checkerboard in screen-space cells (block-sized)
            float tile = Math.max(blockSize, 12f);
            int colA = Color.rgb(40, 40, 52);
            int colB = Color.rgb(22, 22, 30);
            c.save();
            c.clipRect(rect);
            int rows = (int) Math.ceil(rect.height() / tile) + 1;
            int cols = (int) Math.ceil(rect.width() / tile) + 1;
            for (int r = 0; r < rows; r++) {
                for (int col = 0; col < cols; col++) {
                    paint.setColor(((r + col) % 2 == 0) ? colA : colB);
                    float left = rect.left + col * tile;
                    float top = rect.top + r * tile;
                    c.drawRect(left, top, left + tile, top + tile, paint);
                }
            }
            c.restore();
        }
    };

    public final String displayName;
    public final int unlockThreshold;

    BoardSkin(String displayName, int unlockThreshold) {
        this.displayName = displayName;
        this.unlockThreshold = unlockThreshold;
    }

    /**
     * Paint the grid background within {@code rect}. Implementations must use
     * only Canvas primitives — no bitmap assets.
     */
    public abstract void drawBackground(Canvas c, RectF rect, int blockSize, Paint paint);

    /** Resolves a skin by its enum name, falling back to DEFAULT for null/unknown ids. */
    public static BoardSkin fromId(String id) {
        if (id == null) return DEFAULT;
        for (BoardSkin s : values()) {
            if (s.name().equals(id)) return s;
        }
        return DEFAULT;
    }
}
