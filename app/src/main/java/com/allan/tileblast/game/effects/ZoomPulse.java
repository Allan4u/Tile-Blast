package com.allan.tileblast.game.effects;

/**
 * Subtle zoom pulse effect: 1.0 → 1.03 → 1.0 over 300ms.
 * First half uses ease-out, second half uses ease-in.
 * Triggers on combo >= 4 or lines broken >= 3.
 */
public class ZoomPulse {
    private static final float PEAK_SCALE = 1.03f;
    private static final float HALF_DURATION = 150f; // ms

    private float currentScale;
    private float elapsed;
    private boolean active;
    private float startScale; // for interruption support

    public ZoomPulse() {
        currentScale = 1.0f;
        active = false;
    }

    /**
     * Trigger a new zoom pulse from scale 1.0.
     */
    public void trigger() {
        startScale = 1.0f;
        elapsed = 0f;
        active = true;
    }

    /**
     * Trigger from current scale value (for interruption support).
     */
    public void triggerFrom(float currentScale) {
        this.startScale = currentScale;
        elapsed = 0f;
        active = true;
    }

    public void update(float deltaMs) {
        if (!active) return;

        elapsed += deltaMs;

        if (elapsed <= HALF_DURATION) {
            // First half: ease-out from startScale to PEAK_SCALE
            float t = elapsed / HALF_DURATION;
            float eased = Easing.easeOut(t);
            currentScale = startScale + (PEAK_SCALE - startScale) * eased;
        } else if (elapsed <= HALF_DURATION * 2) {
            // Second half: ease-in from PEAK_SCALE back to 1.0
            float t = (elapsed - HALF_DURATION) / HALF_DURATION;
            float eased = Easing.easeIn(t);
            currentScale = PEAK_SCALE + (1.0f - PEAK_SCALE) * eased;
        } else {
            // Done
            currentScale = 1.0f;
            active = false;
        }
    }

    public float getScale() {
        return currentScale;
    }

    public boolean isActive() {
        return active;
    }
}
