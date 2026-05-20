package com.allan.tileblast;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.allan.tileblast.game.Piece;
import com.allan.tileblast.theme.BoardSkin;
import com.allan.tileblast.theme.ColorPalette;
import com.allan.tileblast.theme.ThemeManager;

import java.util.Locale;

/**
 * Settings screen for the Themes & Customization feature. Hosts a custom
 * {@link SettingsView} that draws palette tiles, skin tiles, a preview area,
 * and a back button using only Canvas primitives.
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        AppContext.init(getApplicationContext());
        setContentView(new SettingsView(this));
    }

    /**
     * Custom Canvas-based view that lays out palette grid (2x3), skin strip
     * (1x5), and a preview area. Locked tiles are dimmed with a 50% black
     * overlay and show a lock glyph plus the unlock threshold.
     */
    static class SettingsView extends View {

        private final ThemeManager theme;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface fontBold;
        private final Typeface fontRegular;
        private final float density;

        private final RectF[] paletteRects = new RectF[ColorPalette.values().length];
        private final RectF[] skinRects = new RectF[BoardSkin.values().length];
        private final RectF backRect = new RectF();
        private final RectF previewRect = new RectF();

        SettingsView(Context ctx) {
            super(ctx);
            this.theme = ThemeManager.getInstance(ctx);
            this.density = getResources().getDisplayMetrics().density;
            setBackgroundColor(Color.BLACK);
            fontBold = ResourcesCompat.getFont(ctx, R.font.silkscreen_bold);
            fontRegular = ResourcesCompat.getFont(ctx, R.font.silkscreen);
            for (int i = 0; i < paletteRects.length; i++) paletteRects[i] = new RectF();
            for (int i = 0; i < skinRects.length; i++) skinRects[i] = new RectF();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            layoutRects(w, h);
        }

        private void layoutRects(int w, int h) {
            float pad = 16 * density;
            float headerH = 56 * density;
            backRect.set(pad, pad, pad + 88 * density, pad + headerH - 4 * density);

            // Palette grid: 2 rows x 3 cols, just below header section title
            float sectionTop = headerH + 32 * density; // header + section title
            float gridLeft = pad;
            float gridRight = w - pad;
            float gridW = gridRight - gridLeft;
            float tileGap = 8 * density;
            float tileW = (gridW - 2 * tileGap) / 3f;
            float tileH = tileW * 0.62f; // wider-than-tall swatch tile
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 3; col++) {
                    int idx = row * 3 + col;
                    float l = gridLeft + col * (tileW + tileGap);
                    float t = sectionTop + row * (tileH + tileGap);
                    paletteRects[idx].set(l, t, l + tileW, t + tileH);
                }
            }
            float paletteBottom = sectionTop + 2 * tileH + tileGap;

            // Skin strip: 1 x 5 below palettes (account for second section title)
            float skinTop = paletteBottom + 32 * density;
            float skinTileW = (gridW - 4 * tileGap) / 5f;
            float skinTileH = skinTileW * 0.9f;
            for (int i = 0; i < 5; i++) {
                float l = gridLeft + i * (skinTileW + tileGap);
                skinRects[i].set(l, skinTop, l + skinTileW, skinTop + skinTileH);
            }
            float skinBottom = skinTop + skinTileH;

            // Preview region fills the remaining space
            float previewTop = skinBottom + 32 * density;
            float previewBottom = h - pad;
            // Make it square within available width
            float previewSize = Math.min(gridW, previewBottom - previewTop);
            float previewLeft = (w - previewSize) / 2f;
            previewRect.set(previewLeft, previewTop, previewLeft + previewSize, previewTop + previewSize);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();

            drawHeader(canvas, w);
            drawSectionTitle(canvas, "COLOR PALETTE", paletteRects[0].top - 12 * density);
            drawPaletteGrid(canvas);
            drawSectionTitle(canvas, "BOARD SKIN", skinRects[0].top - 12 * density);
            drawSkinStrip(canvas);
            drawSectionTitle(canvas, "PREVIEW", previewRect.top - 12 * density);
            drawPreview(canvas);
        }

        private void drawHeader(Canvas canvas, int w) {
            // Back button background
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF222831);
            canvas.drawRoundRect(backRect, 8 * density, 8 * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(0xFFFFFFFF);
            canvas.drawRoundRect(backRect, 8 * density, 8 * density, paint);

            textPaint.setTypeface(fontBold);
            textPaint.setTextSize(14 * density);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            float baseline = backRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText("< BACK", backRect.centerX(), baseline, textPaint);

            // Title
            textPaint.setTypeface(fontBold);
            textPaint.setTextSize(20 * density);
            textPaint.setColor(0xFFFFD700);
            textPaint.setTextAlign(Paint.Align.CENTER);
            float titleBaseline = backRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText("SETTINGS", w / 2f, titleBaseline, textPaint);
        }

        private void drawSectionTitle(Canvas canvas, String title, float baselineY) {
            textPaint.setTypeface(fontBold);
            textPaint.setTextSize(13 * density);
            textPaint.setColor(0xFFAAAAAA);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(title, paletteRects[0].left, baselineY, textPaint);
        }

        private void drawPaletteGrid(Canvas canvas) {
            ColorPalette[] palettes = ColorPalette.values();
            ColorPalette active = theme.getActivePalette();
            for (int i = 0; i < palettes.length; i++) {
                ColorPalette p = palettes[i];
                RectF r = paletteRects[i];
                drawPaletteTile(canvas, r, p, p == active, theme.isUnlocked(p));
            }
        }

        private void drawPaletteTile(Canvas canvas, RectF r, ColorPalette p, boolean selected, boolean unlocked) {
            // Background
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF1a1a1a);
            canvas.drawRoundRect(r, 8 * density, 8 * density, paint);

            // 6-color swatch (2 rows x 3 cols) inset within the tile
            int[][] colors = p.getColors();
            float inset = 8 * density;
            float labelH = 18 * density;
            float swatchTop = r.top + inset;
            float swatchBottom = r.bottom - labelH - inset;
            float swatchLeft = r.left + inset;
            float swatchRight = r.right - inset;
            float sw = (swatchRight - swatchLeft) / 3f;
            float sh = (swatchBottom - swatchTop) / 2f;
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 3; col++) {
                    int idx = row * 3 + col;
                    int[] c = colors[idx];
                    paint.setColor(Color.rgb(c[0], c[1], c[2]));
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(
                            swatchLeft + col * sw,
                            swatchTop + row * sh,
                            swatchLeft + (col + 1) * sw,
                            swatchTop + (row + 1) * sh,
                            paint);
                }
            }

            // Name label
            textPaint.setTypeface(fontBold);
            textPaint.setTextSize(11 * density);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            float labelBaseline = r.bottom - inset / 2f - 2 * density;
            canvas.drawText(p.displayName, r.centerX(), labelBaseline, textPaint);

            // Selection indicator
            if (selected && unlocked) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3f * density);
                paint.setColor(0xFFFFD700);
                canvas.drawRoundRect(r, 8 * density, 8 * density, paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1f);
                paint.setColor(0xFF444444);
                canvas.drawRoundRect(r, 8 * density, 8 * density, paint);
            }

            if (!unlocked) {
                drawLockOverlay(canvas, r, p.unlockThreshold);
            }
        }

        private void drawSkinStrip(Canvas canvas) {
            BoardSkin[] skins = BoardSkin.values();
            BoardSkin active = theme.getActiveSkin();
            for (int i = 0; i < skins.length; i++) {
                BoardSkin s = skins[i];
                RectF r = skinRects[i];
                drawSkinTile(canvas, r, s, s == active, theme.isUnlocked(s));
            }
        }

        private void drawSkinTile(Canvas canvas, RectF r, BoardSkin s, boolean selected, boolean unlocked) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF1a1a1a);
            canvas.drawRoundRect(r, 6 * density, 6 * density, paint);

            float inset = 4 * density;
            float labelH = 16 * density;
            RectF thumb = new RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - labelH - inset);
            int blockSize = (int) Math.max(8 * density, thumb.width() / 4f);
            try {
                s.drawBackground(canvas, thumb, blockSize, paint);
            } catch (Throwable ignore) {
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.BLACK);
                canvas.drawRect(thumb, paint);
            }
            paint.setShader(null);

            // Name label
            textPaint.setTypeface(fontBold);
            textPaint.setTextSize(10 * density);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            float labelBaseline = r.bottom - 4 * density;
            canvas.drawText(s.displayName, r.centerX(), labelBaseline, textPaint);

            if (selected && unlocked) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3f * density);
                paint.setColor(0xFFFFD700);
                canvas.drawRoundRect(r, 6 * density, 6 * density, paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1f);
                paint.setColor(0xFF444444);
                canvas.drawRoundRect(r, 6 * density, 6 * density, paint);
            }

            if (!unlocked) {
                drawLockOverlay(canvas, r, s.unlockThreshold);
            }
        }

        private void drawLockOverlay(Canvas canvas, RectF r, int threshold) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x80000000); // 50% black
            canvas.drawRoundRect(r, 6 * density, 6 * density, paint);

            // Lock glyph
            textPaint.setTypeface(fontBold);
            textPaint.setTextSize(20 * density);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            float lockBaseline = r.centerY();
            canvas.drawText("\uD83D\uDD12", r.centerX(), lockBaseline, textPaint);

            // Threshold value
            textPaint.setTypeface(fontBold);
            textPaint.setTextSize(11 * density);
            textPaint.setColor(0xFFFFD700);
            canvas.drawText(formatThreshold(threshold), r.centerX(), lockBaseline + 16 * density, textPaint);
        }

        private static String formatThreshold(int n) {
            return String.format(Locale.US, "%,d", n);
        }

        // Simple sample shapes for the preview (3 small pieces).
        private static final int[][] SAMPLE_SHAPE_A = {{1, 1}, {1, 1}};
        private static final int[][] SAMPLE_SHAPE_B = {{1, 1, 1}};
        private static final int[][] SAMPLE_SHAPE_C = {{0, 1, 0}, {1, 1, 1}};

        private void drawPreview(Canvas canvas) {
            // Draw a 4x4 sample grid using the active skin
            int gridSize = 4;
            float pad = 6 * density;
            RectF inner = new RectF(previewRect.left + pad, previewRect.top + pad,
                    previewRect.right - pad, previewRect.bottom - pad);
            int blockSize = (int) Math.min(inner.width() / gridSize, inner.height() / gridSize);
            float gridW = blockSize * gridSize;
            float gridH = blockSize * gridSize;
            float gridLeft = inner.centerX() - gridW / 2f;
            float gridTop = inner.centerY() - gridH / 2f;
            RectF gridRect = new RectF(gridLeft, gridTop, gridLeft + gridW, gridTop + gridH);

            // Border around preview
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3 * density);
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(previewRect, 8 * density, 8 * density, paint);

            // Active skin background
            try {
                theme.getActiveSkin().drawBackground(canvas, gridRect, blockSize, paint);
            } catch (Throwable ignore) {
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.BLACK);
                canvas.drawRect(gridRect, paint);
            }
            paint.setShader(null);

            // Cell outlines (subtle)
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.5f);
            paint.setColor(0x33FFFFFF);
            for (int gy = 0; gy < gridSize; gy++) {
                for (int gx = 0; gx < gridSize; gx++) {
                    canvas.drawRect(
                            gridLeft + gx * blockSize,
                            gridTop + gy * blockSize,
                            gridLeft + (gx + 1) * blockSize,
                            gridTop + (gy + 1) * blockSize,
                            paint);
                }
            }

            // Render three sample pieces using active palette colors via Piece.getColorFromIndex.
            drawSampleShape(canvas, SAMPLE_SHAPE_A, 0, 0, 0, gridLeft, gridTop, blockSize);
            drawSampleShape(canvas, SAMPLE_SHAPE_B, 0, 2, 1, gridLeft, gridTop, blockSize);
            drawSampleShape(canvas, SAMPLE_SHAPE_C, 1, 3, 3, gridLeft, gridTop, blockSize);
        }

        private void drawSampleShape(Canvas canvas, int[][] shape, int gx, int gy, int colorIdx,
                                     float gridLeft, float gridTop, int blockSize) {
            for (int dy = 0; dy < shape.length; dy++) {
                for (int dx = 0; dx < shape[dy].length; dx++) {
                    if (shape[dy][dx] == 1) {
                        int bx = gx + dx;
                        int by = gy + dy;
                        if (bx < 0 || by < 0 || bx >= 4 || by >= 4) continue;
                        float cx = gridLeft + bx * blockSize;
                        float cy = gridTop + by * blockSize;
                        drawBeveledBlock(canvas, cx, cy, blockSize, colorIdx);
                    }
                }
            }
        }

        private void drawBeveledBlock(Canvas canvas, float x, float y, int size, int colorIdx) {
            paint.setShader(null);
            int bw = Math.max(size / 6, 2);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Piece.getColorFromIndex(colorIdx));
            canvas.drawRect(x, y, x + size, y + size, paint);
            paint.setColor(Piece.getTopBorder(colorIdx));
            canvas.drawRect(x, y, x + size, y + bw, paint);
            paint.setColor(Piece.getLeftBorder(colorIdx));
            canvas.drawRect(x, y, x + bw, y + size, paint);
            paint.setColor(Piece.getRightBorder(colorIdx));
            canvas.drawRect(x + size - bw, y, x + size, y + size, paint);
            paint.setColor(Piece.getBottomBorder(colorIdx));
            canvas.drawRect(x, y + size - bw, x + size, y + size, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            float x = event.getX();
            float y = event.getY();

            if (backRect.contains(x, y)) {
                Context ctx = getContext();
                if (ctx instanceof SettingsActivity) {
                    ((SettingsActivity) ctx).finish();
                }
                return true;
            }

            ColorPalette[] palettes = ColorPalette.values();
            for (int i = 0; i < palettes.length; i++) {
                if (paletteRects[i].contains(x, y)) {
                    ColorPalette p = palettes[i];
                    if (theme.isUnlocked(p)) {
                        theme.setActivePalette(p);
                    } else {
                        showLockedToast(p.unlockThreshold);
                    }
                    invalidate();
                    return true;
                }
            }

            BoardSkin[] skins = BoardSkin.values();
            for (int i = 0; i < skins.length; i++) {
                if (skinRects[i].contains(x, y)) {
                    BoardSkin s = skins[i];
                    if (theme.isUnlocked(s)) {
                        theme.setActiveSkin(s);
                    } else {
                        showLockedToast(s.unlockThreshold);
                    }
                    invalidate();
                    return true;
                }
            }
            return true;
        }

        private void showLockedToast(int threshold) {
            Toast.makeText(getContext(),
                    String.format(Locale.US, "Unlock at %,d points", threshold),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
