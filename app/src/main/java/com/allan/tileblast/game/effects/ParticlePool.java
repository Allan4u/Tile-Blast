package com.allan.tileblast.game.effects;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Pre-allocated pool of 512 particles for zero-allocation rendering.
 */
public class ParticlePool {
    private static final int MAX_PARTICLES = 512;

    private final Particle[] pool;
    private int activeCount;

    public ParticlePool() {
        pool = new Particle[MAX_PARTICLES];
        for (int i = 0; i < MAX_PARTICLES; i++) {
            pool[i] = new Particle();
        }
        activeCount = 0;
    }

    /**
     * Get an inactive particle from the pool.
     * @return inactive Particle, or null if pool is exhausted
     */
    public Particle obtain() {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (!pool[i].active) {
                activeCount++;
                return pool[i];
            }
        }
        return null; // pool exhausted, silently drop
    }

    /**
     * Return a particle to the pool.
     */
    public void release(Particle p) {
        if (p != null && p.active) {
            p.active = false;
            activeCount--;
        }
    }

    /**
     * Update all active particles and auto-release expired ones.
     */
    public void updateAll(float deltaMs) {
        activeCount = 0;
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (pool[i].active) {
                pool[i].update(deltaMs);
                if (pool[i].active) {
                    activeCount++;
                }
            }
        }
    }

    /**
     * Draw all active particles as filled circles.
     */
    public void drawAll(Canvas canvas, Paint paint) {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (pool[i].active) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(pool[i].color);
                paint.setAlpha((int) (pool[i].alpha * 255));
                canvas.drawCircle(pool[i].x, pool[i].y, pool[i].radius, paint);
            }
        }
        paint.setAlpha(255);
    }

    /**
     * @return true if any particle is active
     */
    public boolean hasActive() {
        return activeCount > 0;
    }

    /**
     * Release all active particles.
     */
    public void releaseAll() {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            pool[i].active = false;
        }
        activeCount = 0;
    }
}
