package com.allan.tileblast.game.effects;

/**
 * A single particle with position, velocity, gravity, alpha fade, and lifetime.
 * Used by ParticlePool for zero-allocation rendering.
 */
public class Particle {
    private static final float GRAVITY = 800f; // px/s²

    public float x, y;           // position in screen pixels
    public float vx, vy;         // velocity px/s
    public float alpha;          // [0.0, 1.0]
    public float radius;         // in px
    public int color;            // ARGB color
    public float lifetime;       // total lifetime in ms
    public float elapsed;        // elapsed time in ms
    public boolean active;       // pool flag

    public Particle() {
        active = false;
    }

    /**
     * Reset particle to initial state for reuse from pool.
     */
    public void reset(float x, float y, float vx, float vy, int color, float radius, float lifetime) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.color = color;
        this.radius = radius;
        this.lifetime = lifetime;
        this.elapsed = 0f;
        this.alpha = 1.0f;
        this.active = true;
    }

    /**
     * Update particle physics: velocity, gravity, alpha fade.
     * @param deltaMs time elapsed this frame in milliseconds
     */
    public void update(float deltaMs) {
        if (!active) return;

        elapsed += deltaMs;

        // Apply velocity and gravity
        float dtSec = deltaMs / 1000f;
        x += vx * dtSec;
        y += vy * dtSec;
        vy += GRAVITY * dtSec;

        // Linear alpha fade
        alpha = Math.max(0f, 1.0f - elapsed / lifetime);

        // Mark expired
        if (elapsed >= lifetime) {
            active = false;
        }
    }

    /**
     * @return true if particle has expired
     */
    public boolean isExpired() {
        return elapsed >= lifetime;
    }
}
