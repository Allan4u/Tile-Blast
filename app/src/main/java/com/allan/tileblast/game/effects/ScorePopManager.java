package com.allan.tileblast.game.effects;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * Manages a pool of ScorePop instances with stacking support.
 * Max 8 active pops. Pops within 200ms window are stacked vertically.
 */
public class ScorePopManager {
    private static final int MAX_POPS = 8;
    private static final float STACK_OFFSET_DP = 25f;
    private static final long STACK_WINDOW_MS = 200;
    private static final float FONT_SIZE_DP = 20f;

    private final ScorePop[] pool;
    private long lastSpawnTime;
    private int recentCount; // pops within stack window

    public ScorePopManager() {
        pool = new ScorePop[MAX_POPS];
        for (int i = 0; i < MAX_POPS; i++) {
            pool[i] = new ScorePop();
        }
        lastSpawnTime = 0;
        recentCount = 0;
    }

    /**
     * Spawn a new score pop. Recycles oldest if pool is full.
     */
    public void spawn(String text, float x, float y, float density) {
        long now = System.currentTimeMillis();

        // Determine stack index
        if (now - lastSpawnTime > STACK_WINDOW_MS) {
            recentCount = 0;
        }
        int stackIndex = recentCount;
        recentCount++;
        lastSpawnTime = now;

        // Find inactive slot or recycle oldest
        ScorePop target = null;
        float oldestElapsed = -1f;
        int oldestIdx = 0;

        for (int i = 0; i < MAX_POPS; i++) {
            if (!pool[i].active) {
                target = pool[i];
                break;
            }
            if (pool[i].elapsed > oldestElapsed) {
                oldestElapsed = pool[i].elapsed;
                oldestIdx = i;
            }
        }

        if (target == null) {
            // Recycle oldest
            target = pool[oldestIdx];
        }

        // Apply stack offset to y position
        float stackOffset = stackIndex * STACK_OFFSET_DP * density;
        target.reset(text, x, y - stackOffset, stackIndex, density);
    }

    public void update(float deltaMs) {
        for (int i = 0; i < MAX_POPS; i++) {
            if (pool[i].active) {
                pool[i].update(deltaMs);
            }
        }
    }

    public void draw(Canvas canvas, Paint paint, Typeface font, float density) {
        for (int i = 0; i < MAX_POPS; i++) {
            if (pool[i].active && pool[i].alpha > 0f) {
                paint.setTypeface(font);
                paint.setTextSize(FONT_SIZE_DP * density);
                paint.setColor(Color.WHITE);
                paint.setAlpha((int) (pool[i].alpha * 255));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setStyle(Paint.Style.FILL);

                canvas.drawText(pool[i].text,
                        pool[i].x,
                        pool[i].y + pool[i].offsetY,
                        paint);
            }
        }
        paint.setAlpha(255);
    }

    public boolean isActive() {
        for (int i = 0; i < MAX_POPS; i++) {
            if (pool[i].active) return true;
        }
        return false;
    }
}
