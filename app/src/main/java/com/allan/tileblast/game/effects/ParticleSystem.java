package com.allan.tileblast.game.effects;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.allan.tileblast.game.Piece;

import java.util.Random;

/**
 * Manages particle spawning for line break explosions and celebration effects.
 */
public class ParticleSystem {
    private final ParticlePool pool;
    private final float density;
    private final Random random;

    public ParticleSystem(float density) {
        this.pool = new ParticlePool();
        this.density = density;
        this.random = new Random();
    }

    /**
     * Spawn 4-8 particles per cleared cell at the cell's screen position.
     */
    public void spawnLineBreak(int cellX, int cellY, int blockSize, int gridLeft, int gridTop, int colorIndex) {
        float centerX = gridLeft + cellX * blockSize + blockSize / 2f;
        float centerY = gridTop + cellY * blockSize + blockSize / 2f;
        int color = Piece.getColorFromIndex(colorIndex);

        int count = 4 + random.nextInt(5); // 4 to 8 inclusive
        for (int i = 0; i < count; i++) {
            Particle p = pool.obtain();
            if (p == null) return; // pool exhausted

            // Random velocity with magnitude between 100 and 400 px/s
            float angle = random.nextFloat() * (float)(2 * Math.PI);
            float magnitude = 100f + random.nextFloat() * 300f;
            float vx = (float) Math.cos(angle) * magnitude;
            float vy = (float) Math.sin(angle) * magnitude - 200f; // bias upward

            // Radius between 2dp and 5dp
            float radius = (2f + random.nextFloat() * 3f) * density;

            // Lifetime between 400 and 700 ms
            float lifetime = 400f + random.nextFloat() * 300f;

            p.reset(centerX, centerY, vx, vy, color, radius, lifetime);
        }
    }

    /**
     * Spawn 80-120 gold particles from center for perfect clear celebration.
     */
    public void spawnCelebration(float centerX, float centerY) {
        int goldColor = 0xFFFFD700;
        int count = 80 + random.nextInt(41); // 80 to 120 inclusive

        for (int i = 0; i < count; i++) {
            Particle p = pool.obtain();
            if (p == null) return; // pool exhausted

            // Random velocity in all directions
            float angle = random.nextFloat() * (float)(2 * Math.PI);
            float magnitude = 150f + random.nextFloat() * 350f;
            float vx = (float) Math.cos(angle) * magnitude;
            float vy = (float) Math.sin(angle) * magnitude;

            // Radius between 2dp and 5dp
            float radius = (2f + random.nextFloat() * 3f) * density;

            // Lifetime between 500 and 900 ms (longer for celebration)
            float lifetime = 500f + random.nextFloat() * 400f;

            p.reset(centerX, centerY, vx, vy, goldColor, radius, lifetime);
        }
    }

    public void update(float deltaMs) {
        pool.updateAll(deltaMs);
    }

    public void draw(Canvas canvas, Paint paint) {
        pool.drawAll(canvas, paint);
    }

    public boolean isActive() {
        return pool.hasActive();
    }
}
