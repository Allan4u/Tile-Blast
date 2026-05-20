package com.allan.tileblast.stats;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Tracks player statistics across game sessions.
 * Persists to SharedPreferences via the same prefs file as StorageManager.
 */
public class StatisticsManager {
    private static final String PREFS_NAME = "tile_blast_prefs";

    // Mode keys
    private static final String KEY_GAMES_PREFIX = "stats_games_"; // + mode
    private static final String KEY_SCORE_PREFIX = "stats_score_"; // + mode (cumulative, long)
    private static final String KEY_GAMES_TOTAL = "stats_games_total";
    private static final String KEY_BEST_COMBO = "stats_best_combo";
    private static final String KEY_LINES_TOTAL = "stats_lines_total";
    private static final String KEY_PLAYTIME = "stats_playtime";
    private static final String KEY_STREAK_CURRENT = "stats_streak_current";
    private static final String KEY_STREAK_BEST = "stats_streak_best";

    private static final int WIN_THRESHOLD = 1000;

    private final SharedPreferences prefs;

    public StatisticsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Recording ───────────────────────────────────────────────────────────

    public void recordGameEnd(String mode, int finalScore) {
        if (mode == null) return;
        String modeKey = sanitizeMode(mode);

        // Increment per-mode and total games played
        int modeGames = safeGetInt(KEY_GAMES_PREFIX + modeKey, 0) + 1;
        int totalGames = safeGetInt(KEY_GAMES_TOTAL, 0) + 1;

        // Accumulate score (long to avoid overflow over many games)
        long modeScore = safeGetLong(KEY_SCORE_PREFIX + modeKey, 0L) + Math.max(0, finalScore);

        // Win streak: > 1000 increments, otherwise resets
        int currentStreak = safeGetInt(KEY_STREAK_CURRENT, 0);
        int bestStreak = safeGetInt(KEY_STREAK_BEST, 0);
        if (finalScore > WIN_THRESHOLD) {
            currentStreak += 1;
            if (currentStreak > bestStreak) bestStreak = currentStreak;
        } else {
            currentStreak = 0;
        }

        SharedPreferences.Editor ed = prefs.edit();
        ed.putInt(KEY_GAMES_PREFIX + modeKey, modeGames);
        ed.putInt(KEY_GAMES_TOTAL, totalGames);
        ed.putLong(KEY_SCORE_PREFIX + modeKey, modeScore);
        ed.putInt(KEY_STREAK_CURRENT, currentStreak);
        ed.putInt(KEY_STREAK_BEST, bestStreak);
        ed.apply();
    }

    public void recordCombo(int comboLevel) {
        if (comboLevel <= 0) return;
        int best = safeGetInt(KEY_BEST_COMBO, 0);
        if (comboLevel > best) {
            prefs.edit().putInt(KEY_BEST_COMBO, comboLevel).apply();
        }
    }

    public void recordLinesCleared(int count) {
        if (count <= 0) return;
        int total = safeGetInt(KEY_LINES_TOTAL, 0);
        prefs.edit().putInt(KEY_LINES_TOTAL, total + count).apply();
    }

    public void recordPlayTime(long sessionSeconds) {
        if (sessionSeconds <= 0) return;
        long total = safeGetLong(KEY_PLAYTIME, 0L);
        prefs.edit().putLong(KEY_PLAYTIME, total + sessionSeconds).apply();
    }

    // ─── Queries ─────────────────────────────────────────────────────────────

    public int getGamesPlayed(String mode) {
        if (mode == null) return 0;
        return safeGetInt(KEY_GAMES_PREFIX + sanitizeMode(mode), 0);
    }

    public int getTotalGamesPlayed() {
        return safeGetInt(KEY_GAMES_TOTAL, 0);
    }

    public int getAverageScore(String mode) {
        if (mode == null) return 0;
        String modeKey = sanitizeMode(mode);
        int games = safeGetInt(KEY_GAMES_PREFIX + modeKey, 0);
        if (games <= 0) return 0;
        long total = safeGetLong(KEY_SCORE_PREFIX + modeKey, 0L);
        return (int) (total / games);
    }

    public int getBestCombo() {
        return safeGetInt(KEY_BEST_COMBO, 0);
    }

    public int getTotalLinesCleared() {
        return safeGetInt(KEY_LINES_TOTAL, 0);
    }

    public long getTotalPlayTimeSeconds() {
        return safeGetLong(KEY_PLAYTIME, 0L);
    }

    public int getCurrentWinStreak() {
        return safeGetInt(KEY_STREAK_CURRENT, 0);
    }

    public int getBestWinStreak() {
        return safeGetInt(KEY_STREAK_BEST, 0);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private int safeGetInt(String key, int def) {
        try {
            return prefs.getInt(key, def);
        } catch (ClassCastException e) {
            // Corrupt entry (wrong type) — overwrite with default
            prefs.edit().remove(key).apply();
            return def;
        }
    }

    private long safeGetLong(String key, long def) {
        try {
            return prefs.getLong(key, def);
        } catch (ClassCastException e) {
            prefs.edit().remove(key).apply();
            return def;
        }
    }

    private String sanitizeMode(String mode) {
        // Lowercase and only allow alphanumeric to avoid weird key collisions
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mode.length(); i++) {
            char c = Character.toLowerCase(mode.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) sb.append(c);
        }
        return sb.length() == 0 ? "unknown" : sb.toString();
    }
}
