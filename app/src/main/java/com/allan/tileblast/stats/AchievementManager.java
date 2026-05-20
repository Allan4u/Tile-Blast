package com.allan.tileblast.stats;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates achievement unlock conditions and persists unlock state.
 */
public class AchievementManager {
    private static final String PREFS_NAME = "tile_blast_prefs";
    private static final String KEY_PREFIX = "ach_"; // + enum name

    private final SharedPreferences prefs;

    public AchievementManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Public evaluation entry points ──────────────────────────────────────

    public List<String> evaluateScore(int finalScore, String mode) {
        List<String> newly = new ArrayList<>();
        // FIRST_GAME unlocks unconditionally on any game end.
        tryUnlock(AchievementDef.FIRST_GAME, true, newly);
        tryUnlock(AchievementDef.CENTURION, finalScore >= AchievementDef.CENTURION.threshold, newly);
        tryUnlock(AchievementDef.THOUSAND_CLUB, finalScore >= AchievementDef.THOUSAND_CLUB.threshold, newly);
        tryUnlock(AchievementDef.FIVE_THOUSAND, finalScore >= AchievementDef.FIVE_THOUSAND.threshold, newly);
        tryUnlock(AchievementDef.TEN_THOUSAND, finalScore >= AchievementDef.TEN_THOUSAND.threshold, newly);

        // SPEED_DEMON only in timed mode
        if (mode != null && mode.equalsIgnoreCase("timed")) {
            tryUnlock(AchievementDef.SPEED_DEMON, finalScore >= AchievementDef.SPEED_DEMON.threshold, newly);
        }

        checkCompletionist(newly);
        return newly;
    }

    public List<String> evaluateCombo(int comboLevel) {
        List<String> newly = new ArrayList<>();
        tryUnlock(AchievementDef.COMBO_STARTER, comboLevel >= AchievementDef.COMBO_STARTER.threshold, newly);
        tryUnlock(AchievementDef.COMBO_MASTER, comboLevel >= AchievementDef.COMBO_MASTER.threshold, newly);
        tryUnlock(AchievementDef.COMBO_LEGEND, comboLevel >= AchievementDef.COMBO_LEGEND.threshold, newly);
        checkCompletionist(newly);
        return newly;
    }

    public List<String> evaluateCumulative(StatisticsManager stats) {
        List<String> newly = new ArrayList<>();
        if (stats == null) return newly;

        int totalLines = stats.getTotalLinesCleared();
        tryUnlock(AchievementDef.LINE_BREAKER, totalLines >= AchievementDef.LINE_BREAKER.threshold, newly);
        tryUnlock(AchievementDef.LINE_DESTROYER, totalLines >= AchievementDef.LINE_DESTROYER.threshold, newly);
        tryUnlock(AchievementDef.LINE_ANNIHILATOR, totalLines >= AchievementDef.LINE_ANNIHILATOR.threshold, newly);

        int totalGames = stats.getTotalGamesPlayed();
        tryUnlock(AchievementDef.MARATHON, totalGames >= AchievementDef.MARATHON.threshold, newly);
        tryUnlock(AchievementDef.DEDICATED, totalGames >= AchievementDef.DEDICATED.threshold, newly);
        tryUnlock(AchievementDef.VETERAN, totalGames >= AchievementDef.VETERAN.threshold, newly);

        int currentStreak = stats.getCurrentWinStreak();
        int bestStreak = stats.getBestWinStreak();
        int maxStreakSeen = Math.max(currentStreak, bestStreak);
        tryUnlock(AchievementDef.STREAK_3, maxStreakSeen >= AchievementDef.STREAK_3.threshold, newly);
        tryUnlock(AchievementDef.STREAK_7, maxStreakSeen >= AchievementDef.STREAK_7.threshold, newly);

        checkCompletionist(newly);
        return newly;
    }

    public List<String> evaluatePerfectClear() {
        List<String> newly = new ArrayList<>();
        tryUnlock(AchievementDef.PERFECT, true, newly);
        checkCompletionist(newly);
        return newly;
    }

    public List<String> evaluatePrestige() {
        List<String> newly = new ArrayList<>();
        tryUnlock(AchievementDef.PRESTIGE, true, newly);
        checkCompletionist(newly);
        return newly;
    }

    // ─── Queries ─────────────────────────────────────────────────────────────

    public boolean isUnlocked(AchievementDef achievement) {
        if (achievement == null) return false;
        try {
            return prefs.getBoolean(KEY_PREFIX + achievement.name(), false);
        } catch (ClassCastException e) {
            prefs.edit().remove(KEY_PREFIX + achievement.name()).apply();
            return false;
        }
    }

    public int getUnlockedCount() {
        int count = 0;
        for (AchievementDef a : AchievementDef.values()) {
            if (isUnlocked(a)) count++;
        }
        return count;
    }

    public AchievementDef[] getAllAchievements() {
        return AchievementDef.values();
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    /**
     * Unlock the achievement if the condition is met and it isn't already unlocked.
     * Adds the displayName to {@code newly} when newly unlocked.
     */
    private void tryUnlock(AchievementDef def, boolean condition, List<String> newly) {
        if (!condition) return;
        if (isUnlocked(def)) return; // idempotent
        prefs.edit().putBoolean(KEY_PREFIX + def.name(), true).apply();
        newly.add(def.displayName);
    }

    /**
     * Unlock COMPLETIONIST when all 19 other achievements are unlocked.
     */
    private void checkCompletionist(List<String> newly) {
        if (isUnlocked(AchievementDef.COMPLETIONIST)) return;
        int unlockedOthers = 0;
        for (AchievementDef a : AchievementDef.values()) {
            if (a == AchievementDef.COMPLETIONIST) continue;
            if (isUnlocked(a)) unlockedOthers++;
        }
        if (unlockedOthers >= AchievementDef.COMPLETIONIST.threshold) {
            prefs.edit().putBoolean(KEY_PREFIX + AchievementDef.COMPLETIONIST.name(), true).apply();
            newly.add(AchievementDef.COMPLETIONIST.displayName);
        }
    }
}
