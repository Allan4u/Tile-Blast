package com.allan.tileblast.game.effects;

/**
 * Utility class providing easing functions for animations.
 * All methods accept normalized time t in [0.0, 1.0].
 */
public final class Easing {

    private Easing() {} // prevent instantiation

    /**
     * Ease-out: decelerating curve. Formula: 1 - (1-t)^2
     */
    public static float easeOut(float t) {
        t = clamp(t);
        float inv = 1f - t;
        return 1f - inv * inv;
    }

    /**
     * Ease-in: accelerating curve. Formula: t^2
     */
    public static float easeIn(float t) {
        t = clamp(t);
        return t * t;
    }

    /**
     * Overshoot: cubic overshoot that peaks at ~1.2 before settling to 1.0.
     * Formula: 1 + (2.2*t - 2.2)*t*(t - 1)
     */
    public static float overshoot(float t) {
        t = clamp(t);
        return 1f + (2.2f * t - 2.2f) * t * (t - 1f);
    }

    /**
     * Linear: identity function.
     */
    public static float linear(float t) {
        return clamp(t);
    }

    private static float clamp(float t) {
        if (t < 0f) return 0f;
        if (t > 1f) return 1f;
        return t;
    }
}
