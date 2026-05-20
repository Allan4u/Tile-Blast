package com.allan.tileblast.progression;

import com.allan.tileblast.storage.StorageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns all XP/level/prestige/reward state for the player.
 *
 * Construction loads persisted state from {@link StorageManager}.
 * Calling {@link #awardXP(int, int[], int)} or {@link #activatePrestige()}
 * automatically persists the new state.
 */
public class LevelManager {

    public static final int MAX_LEVEL = 100;

    private final StorageManager storage;

    private int level;          // 1-100
    private int currentXP;      // XP within current level
    private int prestigeCount;  // 0+
    private final List<Reward> unlockedRewards = new ArrayList<>();

    private final List<Integer> newLevelsReached = new ArrayList<>();

    public LevelManager(StorageManager storage) {
        this.storage = storage;
        load();
    }

    // ─── XP CALCULATION ──────────────────────────────────────────────────────

    /**
     * Pure calculation — returns the XP that would be awarded for a given
     * game result. Does not mutate state.
     *
     * Formula: floor((floor(score/10) + sum(5 * comboLevel_i) + 50 * perfectClears)
     *                * (1.0 + 0.1 * prestigeCount))
     */
    public int calculateGameXP(int finalScore, int[] comboLevels, int perfectClearCount) {
        int score = Math.max(0, finalScore);
        int perfects = Math.max(0, perfectClearCount);

        long base = score / 10;
        if (comboLevels != null) {
            for (int c : comboLevels) {
                if (c > 0) base += 5L * c;
            }
        }
        base += 50L * perfects;

        double multiplied = base * (1.0 + 0.1 * Math.max(0, prestigeCount));
        return (int) Math.floor(multiplied);
    }

    /**
     * Calculates XP for a game result and applies it to the current state,
     * handling multi-level-ups and capping at level 100. Persists immediately.
     *
     * Records each new level reached so the UI can show level-up overlays;
     * those records are consumed by {@link #getNewLevelsReached()}.
     */
    public void awardXP(int finalScore, int[] comboLevels, int perfectClearCount) {
        newLevelsReached.clear();

        int gained = calculateGameXP(finalScore, comboLevels, perfectClearCount);
        if (gained <= 0) {
            save();
            return;
        }

        // At max level, ignore further XP until prestige.
        if (level >= MAX_LEVEL) {
            save();
            return;
        }

        currentXP += gained;
        while (currentXP >= getXPForNextLevel() && level < MAX_LEVEL) {
            currentXP -= getXPForNextLevel();
            level++;
            newLevelsReached.add(level);
        }

        if (level >= MAX_LEVEL) {
            // No carry-over at max level.
            currentXP = 0;
        }

        save();
    }

    // ─── LEVEL QUERIES ───────────────────────────────────────────────────────

    public int getLevel() {
        return level;
    }

    public int getCurrentXP() {
        return currentXP;
    }

    /**
     * XP required to advance from the current level to the next.
     * Returns level * 100. Defined as level * 100 even at level 100 (always > 0).
     */
    public int getXPForNextLevel() {
        return level * 100;
    }

    /**
     * Fill ratio in [0.0, 1.0] for the progress bar.
     * Returns 1.0 at the max level.
     */
    public float getProgressRatio() {
        if (level >= MAX_LEVEL) return 1.0f;
        int needed = getXPForNextLevel();
        if (needed <= 0) return 0f;
        float r = (float) currentXP / (float) needed;
        if (r < 0f) return 0f;
        if (r > 1f) return 1f;
        return r;
    }

    /**
     * Returns the list of new levels reached since the last call and clears
     * the internal buffer. Intended to be called once per game-over by the UI.
     */
    public List<Integer> getNewLevelsReached() {
        List<Integer> copy = new ArrayList<>(newLevelsReached);
        newLevelsReached.clear();
        return copy;
    }

    // ─── PRESTIGE ────────────────────────────────────────────────────────────

    public int getPrestigeCount() {
        return prestigeCount;
    }

    public double getPrestigeMultiplier() {
        return 1.0 + 0.1 * prestigeCount;
    }

    public boolean canPrestige() {
        return level >= MAX_LEVEL;
    }

    /**
     * Resets level to 1 and currentXP to 0, increments prestige count.
     * Unlocked rewards are preserved. No-op below level 100.
     */
    public void activatePrestige() {
        if (!canPrestige()) return;
        level = 1;
        currentXP = 0;
        prestigeCount++;
        // Rewards preserved.
        save();
    }

    // ─── REWARDS ─────────────────────────────────────────────────────────────

    /**
     * Returns all rewards whose unlockLevel ≤ current level. Note: rewards
     * earned at higher levels before a prestige reset are also preserved
     * via the persisted unlockedRewards list.
     */
    public List<Reward> getUnlockedRewards() {
        List<Reward> result = new ArrayList<>();
        for (Reward r : Reward.values()) {
            if (isRewardUnlocked(r)) result.add(r);
        }
        return result;
    }

    public boolean isRewardUnlocked(Reward reward) {
        if (reward == null) return false;
        if (level >= reward.unlockLevel) return true;
        // Persisted unlocks survive prestige resets.
        return unlockedRewards.contains(reward);
    }

    /**
     * Convenience helper: returns rewards newly unlocked by the given list of
     * new levels. Does not mutate state. UI uses this when showing the level-up
     * overlay so the rewards can appear alongside the level number.
     */
    public List<Reward> getRewardsForLevels(List<Integer> levels) {
        List<Reward> result = new ArrayList<>();
        if (levels == null || levels.isEmpty()) return result;
        for (Integer l : levels) {
            if (l == null) continue;
            for (Reward r : Reward.values()) {
                if (r.unlockLevel == l) result.add(r);
            }
        }
        return result;
    }

    // ─── PERSISTENCE ─────────────────────────────────────────────────────────

    public void save() {
        if (storage == null) return;
        List<String> names = new ArrayList<>();
        // Persist all currently-unlocked rewards by level so they survive prestige.
        for (Reward r : Reward.values()) {
            if (level >= r.unlockLevel || unlockedRewards.contains(r)) {
                if (!names.contains(r.name())) names.add(r.name());
                if (!unlockedRewards.contains(r)) unlockedRewards.add(r);
            }
        }
        storage.saveProgression(level, currentXP, prestigeCount, names);
    }

    public void load() {
        if (storage == null) {
            level = 1;
            currentXP = 0;
            prestigeCount = 0;
            unlockedRewards.clear();
            return;
        }
        level = clampLevel(storage.loadLevel());
        currentXP = Math.max(0, storage.loadXP());
        prestigeCount = Math.max(0, storage.loadPrestigeCount());

        unlockedRewards.clear();
        List<String> names = storage.loadUnlockedRewards();
        if (names != null) {
            for (String n : names) {
                try {
                    Reward r = Reward.valueOf(n);
                    if (!unlockedRewards.contains(r)) unlockedRewards.add(r);
                } catch (IllegalArgumentException ignored) {
                    // Unknown reward name (older app version etc.) — skip.
                }
            }
        }
    }

    private static int clampLevel(int l) {
        if (l < 1) return 1;
        if (l > MAX_LEVEL) return MAX_LEVEL;
        return l;
    }
}
