package com.allan.tileblast.game;

import android.os.Bundle;

/**
 * Owns the timer state machine for Timed Mode rounds.
 *
 * Pure JVM class (the only Android dependency is Bundle for save/restore).
 * Computes Score_Multiplier, Remaining_Time, and end-of-round bonus from
 * elapsed time and accumulated line-break time bonuses.
 */
public class TimedModeController {

    public static final float TIME_BONUS_PER_LINE_BREAK_SEC = 1.5f;
    public static final int   END_BONUS_PER_SECOND          = 5;
    public static final long  TIME_BONUS_FLASH_DURATION_MS  = 800L;

    // Multiplier band boundaries (seconds)
    private static final float BAND_15 = 15f;
    private static final float BAND_30 = 30f;
    private static final float BAND_45 = 45f;
    private static final float BAND_60 = 60f;

    private final String modeName;            // "timed60" or "timed90"
    private final float initialDurationSec;   // 60 or 90

    private float accumulatedBonusSec;        // total seconds added by line-break bonuses
    private float elapsedSec;                 // seconds of active gameplay
    private boolean paused;
    private boolean expired;

    private long lastTickMs;                  // wall-clock ms of the last tick / resume
    private long timeBonusFlashEndMs;         // wall-clock ms when "+1.5s" indicator should disappear

    public TimedModeController(String modeName, float initialDurationSec) {
        this.modeName = modeName;
        this.initialDurationSec = initialDurationSec;
        this.accumulatedBonusSec = 0f;
        this.elapsedSec = 0f;
        this.paused = true;
        this.expired = false;
        this.lastTickMs = 0L;
        this.timeBonusFlashEndMs = 0L;
    }

    // ─── Queries ─────────────────────────────────────────────────────────────

    public String getModeName() { return modeName; }

    public float getInitialDurationSec() { return initialDurationSec; }

    public boolean isPaused() { return paused; }

    public boolean isExpired() { return expired; }

    public boolean isActive() { return !paused && !expired; }

    public float remainingTime() {
        return Math.max(0f, initialDurationSec + accumulatedBonusSec - elapsedSec);
    }

    public float elapsedTime() { return elapsedSec; }

    /**
     * Multiplier band table: 1.0 / 1.5 / 2.0 / 2.5 / 3.0.
     * `timed60` clamps at 2.5 once elapsed >= 60.
     */
    public float scoreMultiplier() {
        if (elapsedSec < BAND_15) return 1.0f;
        if (elapsedSec < BAND_30) return 1.5f;
        if (elapsedSec < BAND_45) return 2.0f;
        if (elapsedSec < BAND_60) return 2.5f;
        // elapsed >= 60
        if (initialDurationSec >= 90f) return 3.0f;
        return 2.5f;
    }

    public boolean isTimeBonusFlashActive(long nowMs) {
        return nowMs < timeBonusFlashEndMs;
    }

    public int endBonus() {
        float remaining = remainingTime();
        if (remaining <= 0f) return 0;
        return END_BONUS_PER_SECOND * (int) Math.floor(remaining);
    }

    // ─── State transitions ───────────────────────────────────────────────────

    public void tick(long nowMs) {
        if (paused || expired) {
            return;
        }
        if (lastTickMs == 0L) {
            // First tick after restore with no resume - just anchor.
            lastTickMs = nowMs;
            return;
        }
        if (nowMs < lastTickMs) {
            // Clock went backwards: treat delta as 0 to keep elapsed monotonic.
            lastTickMs = nowMs;
            return;
        }
        float delta = (nowMs - lastTickMs) / 1000f;
        elapsedSec += delta;
        lastTickMs = nowMs;
        if (remainingTime() <= 0f) {
            expired = true;
            // Clamp so remainingTime() is exactly 0 (no negatives leak into endBonus).
            elapsedSec = initialDurationSec + accumulatedBonusSec;
        }
    }

    public void pause() {
        if (!paused) {
            paused = true;
        }
    }

    public void resume(long nowMs) {
        paused = false;
        lastTickMs = nowMs;
    }

    public void onLineBreak() {
        accumulatedBonusSec += TIME_BONUS_PER_LINE_BREAK_SEC;
        timeBonusFlashEndMs = System.currentTimeMillis() + TIME_BONUS_FLASH_DURATION_MS;
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    public void saveState(Bundle out) {
        out.putBoolean("timer_present", true);
        out.putString("timer_mode", modeName);
        out.putFloat("timer_initial", initialDurationSec);
        out.putFloat("timer_bonus_sec", accumulatedBonusSec);
        out.putFloat("timer_elapsed_sec", elapsedSec);
        out.putBoolean("timer_expired", expired);
        out.putBoolean("timer_paused", paused);
    }

    /**
     * Restores fields from a Bundle. Always restores the controller into the
     * paused state regardless of the persisted flag (Requirement 10.2).
     */
    public void restoreState(Bundle in) {
        this.accumulatedBonusSec = in.getFloat("timer_bonus_sec", 0f);
        this.elapsedSec = in.getFloat("timer_elapsed_sec", 0f);
        this.expired = in.getBoolean("timer_expired", false);
        this.paused = true;                   // forced paused on restore
        this.lastTickMs = 0L;
        this.timeBonusFlashEndMs = 0L;
    }
}
