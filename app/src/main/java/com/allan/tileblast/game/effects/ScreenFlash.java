package com.allan.tileblast.game.effects;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Full-screen semi-transparent flash overlay triggered on big combos (>=3).
 * White for combo 3, gold for combo 4+.
 * Fades from initial opacity to 0 over 100-200ms.
 */
public class ScreenFlash {
    private float opacity;          // current opacity [0.0, 1.0]
    private float initialOpacity;
    private int color;              // base color (white or gold)
    private float duration;         // fade duration ms
    private float elapsed;
    private boolean active;

    public ScreenFlash() {
        active = false;
    }

    /**
     * Trigger a screen flash based on combo level.
     * Combo 3: white at 15% opacity
     * Combo 4+: gold at 15% + 5% per level above 3, capped at 40%
     */
    public void trigger(int comboLevel) {
        if (comboLevel < 3) return;

        if (comboLevel == 3) {
            color = 0xFFFFFFFF; // white
            initialOpacity = 0.15f;
        } else {
            color = 0xFFFFD700; // gold
            initialOpacity = Math.min(0.40f, 0.15f + 0.05f * (comboLevel - 3));
        }

        opacity = initialOpacity;
        duration = 150f; // 100-200ms range, use 150ms as middle
        elapsed = 0f;
        active = true;
    }

    public void update(float deltaMs) {
        if (!active) return;

        elapsed += deltaMs;
        opacity = initialOpacity * Math.max(0f, 1.0f - elapsed / duration);

        if (elapsed >= duration) {
            active = false;
            opacity = 0f;
        }
    }

    public void draw(Canvas canvas, Paint paint, int viewWidth, int viewHeight) {
        if (!active || opacity <= 0f) return;

        paint.setStyle(Paint.Style.FILL);
        int alpha = (int) (opacity * 255);
        // Extract RGB from color and apply computed alpha
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        paint.setColor((alpha << 24) | (r << 16) | (g << 8) | b);
        canvas.drawRect(0, 0, viewWidth, viewHeight, paint);
        paint.setAlpha(255);
    }

    public boolean isActive() {
        return active;
    }
}
