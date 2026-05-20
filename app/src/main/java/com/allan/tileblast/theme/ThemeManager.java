package com.allan.tileblast.theme;

import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.allan.tileblast.storage.StorageManager;

/**
 * Singleton holding the active color palette, active board skin, and cumulative
 * total score across all games. Owns unlock logic and persistence
 * orchestration via {@link StorageManager}.
 *
 * Initialization is performed lazily on the first {@link #getInstance(Context)}
 * call, which loads persisted state and resolves invalid or now-locked
 * selections back to {@link ColorPalette#DEFAULT} / {@link BoardSkin#DEFAULT}.
 *
 * Validates: Requirements 1.3, 2.3, 4.1, 4.2, 5.1, 5.4-5.7, 6.3, 6.4, 7.1,
 * 7.3-7.5, 8.3-8.8.
 */
public final class ThemeManager {

    private static volatile ThemeManager INSTANCE;

    private final StorageManager storage;
    private ColorPalette activePalette;
    private BoardSkin activeSkin;
    private int totalScore;

    private ThemeManager(Context appCtx) {
        this.storage = new StorageManager(appCtx);
        this.totalScore = storage.getTotalScore();
        this.activePalette = resolvePalette(storage.getActivePaletteId());
        this.activeSkin = resolveSkin(storage.getActiveSkinId());
    }

    /**
     * Returns the singleton instance, constructing it on first use using the
     * application context derived from {@code ctx}. Thread-safe via
     * double-checked locking.
     *
     * @throws IllegalStateException if {@code ctx} is null and no instance has
     *     been constructed yet.
     */
    public static ThemeManager getInstance(Context ctx) {
        ThemeManager local = INSTANCE;
        if (local == null) {
            synchronized (ThemeManager.class) {
                local = INSTANCE;
                if (local == null) {
                    if (ctx == null) {
                        throw new IllegalStateException(
                            "ThemeManager.getInstance(null) called before initialization");
                    }
                    local = new ThemeManager(ctx.getApplicationContext());
                    INSTANCE = local;
                }
            }
        }
        return local;
    }

    // ─── queries ────────────────────────────────────────────────────────────

    public ColorPalette getActivePalette() {
        return activePalette;
    }

    public BoardSkin getActiveSkin() {
        return activeSkin;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public boolean isUnlocked(ColorPalette p) {
        if (p == null) return false;
        return totalScore >= p.unlockThreshold;
    }

    public boolean isUnlocked(BoardSkin s) {
        if (s == null) return false;
        return totalScore >= s.unlockThreshold;
    }

    // ─── mutations ──────────────────────────────────────────────────────────

    /**
     * Sets the active color palette. Returns {@code false} (and does nothing)
     * if the palette is currently locked.
     */
    public boolean setActivePalette(ColorPalette p) {
        if (p == null || !isUnlocked(p)) return false;
        activePalette = p;
        storage.setActivePaletteId(p.name());
        return true;
    }

    /**
     * Sets the active board skin. Returns {@code false} (and does nothing) if
     * the skin is currently locked.
     */
    public boolean setActiveSkin(BoardSkin s) {
        if (s == null || !isUnlocked(s)) return false;
        activeSkin = s;
        storage.setActiveSkinId(s.name());
        return true;
    }

    /**
     * Adds the given delta to the persisted total score. Non-positive deltas
     * are ignored. The new total is clamped to non-negative.
     */
    public void addToTotalScore(int delta) {
        if (delta <= 0) return;
        long next = (long) totalScore + (long) delta;
        if (next < 0) next = Integer.MAX_VALUE; // overflow guard
        totalScore = (int) Math.max(0, next);
        storage.setTotalScore(totalScore);
    }

    // ─── persistence-resolution helpers ─────────────────────────────────────

    private ColorPalette resolvePalette(String id) {
        if (id == null) return ColorPalette.DEFAULT;
        ColorPalette p = ColorPalette.fromId(id);
        return isUnlocked(p) ? p : ColorPalette.DEFAULT;
    }

    private BoardSkin resolveSkin(String id) {
        if (id == null) return BoardSkin.DEFAULT;
        BoardSkin s = BoardSkin.fromId(id);
        return isUnlocked(s) ? s : BoardSkin.DEFAULT;
    }

    // ─── test hooks ─────────────────────────────────────────────────────────

    @VisibleForTesting
    public static void resetForTests() {
        synchronized (ThemeManager.class) {
            INSTANCE = null;
        }
    }
}
