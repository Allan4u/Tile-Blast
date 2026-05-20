package com.allan.tileblast.game.effects;

/**
 * A single floating "+N" score text that rises and fades over 800ms.
 */
public class ScorePop {
    private static final float LIFETIME = 800f;  // ms
    private static final float RISE_DISTANCE_DP = 60f;

    public float x, y;          // starting position
    public float offsetY;       // current vertical offset (negative = upward)
    public float alpha;
    public String text;
    public float elapsed;
    public boolean active;
    public int stackIndex;      // for vertical stacking
    private float riseDistancePx;

    public ScorePop() {
        active = false;
    }

    /**
     * Reset score pop for reuse from pool.
     */
    public void reset(String text, float x, float y, int stackIndex, float density) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.stackIndex = stackIndex;
        this.riseDistancePx = RISE_DISTANCE_DP * density;
        this.elapsed = 0f;
        this.offsetY = 0f;
        this.alpha = 1.0f;
        this.active = true;
    }

    public void update(float deltaMs) {
        if (!active) return;

        elapsed += deltaMs;

        if (elapsed >= LIFETIME) {
            active = false;
            alpha = 0f;
            return;
        }

        float progress = elapsed / LIFETIME;
        offsetY = -riseDistancePx * progress;
        alpha = 1.0f - progress;
    }

    public boolean isExpired() {
        return elapsed >= LIFETIME;
    }
}
