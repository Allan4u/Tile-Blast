package com.allan.tileblast.game.effects;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * Animated combo text with overshoot scale-in, hold, and fade-out.
 * Phase 1 (0-300ms): Scale from 0.0 to 1.0 with overshoot (peaks at 1.2)
 * Phase 2 (300-300+hold): Hold at scale 1.0, alpha 1.0
 * Phase 3 (last 300ms): Fade alpha from 1.0 to 0.0
 */
public class ComboTextAnim {
    private static final float SCALE_DURATION = 300f;  // ms
    private static final float FADE_DURATION = 300f;   // ms

    private String text;
    private float holdDuration;  // 400ms for combo, 800ms for perfect clear
    private float elapsed;
    private float scale;
    private float alpha;
    private boolean active;
    private int color;
    private float fontSize; // in px

    public ComboTextAnim() {
        active = false;
    }

    /**
     * Trigger a new combo text animation.
     */
    public void trigger(String text, int color, float fontSizePx, float holdDurationMs) {
        this.text = text;
        this.color = color;
        this.fontSize = fontSizePx;
        this.holdDuration = holdDurationMs;
        this.elapsed = 0f;
        this.scale = 0f;
        this.alpha = 1.0f;
        this.active = true;
    }

    public void update(float deltaMs) {
        if (!active) return;

        elapsed += deltaMs;

        float totalDuration = SCALE_DURATION + holdDuration + FADE_DURATION;

        if (elapsed < SCALE_DURATION) {
            // Phase 1: Scale-in with overshoot
            float t = elapsed / SCALE_DURATION;
            scale = Easing.overshoot(t);
            alpha = 1.0f;
        } else if (elapsed < SCALE_DURATION + holdDuration) {
            // Phase 2: Hold
            scale = 1.0f;
            alpha = 1.0f;
        } else if (elapsed < totalDuration) {
            // Phase 3: Fade-out
            scale = 1.0f;
            float fadeElapsed = elapsed - SCALE_DURATION - holdDuration;
            alpha = 1.0f - fadeElapsed / FADE_DURATION;
        } else {
            // Done
            active = false;
            alpha = 0f;
        }
    }

    public void draw(Canvas canvas, Paint paint, Typeface font, int viewWidth, int viewHeight) {
        if (!active || alpha <= 0f) return;

        paint.setTypeface(font);
        paint.setTextSize(fontSize * scale);
        paint.setColor(color);
        paint.setAlpha((int) (alpha * 255));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStyle(Paint.Style.FILL);

        // Position at 30% of view height
        float x = viewWidth / 2f;
        float y = viewHeight * 0.3f;

        canvas.drawText(text, x, y, paint);
        paint.setAlpha(255);
    }

    public float getScale() {
        return scale;
    }

    public float getAlpha() {
        return alpha;
    }

    public boolean isActive() {
        return active;
    }
}
