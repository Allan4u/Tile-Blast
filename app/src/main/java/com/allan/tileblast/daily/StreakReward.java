package com.allan.tileblast.daily;

/**
 * Streak rewards earned by maintaining consecutive starred days.
 * The {@link #threshold} is the streak length required to claim the reward.
 */
public enum StreakReward {

    /** 3-day streak: awards 1 power-up. */
    POWER_UP_3DAY(3, "Power-up Bonus"),

    /** 7-day streak: unlocks a theme. */
    THEME_7DAY(7, "Theme Unlocked"),

    /** 14-day streak: prestige XP bonus. */
    XP_BONUS_14DAY(14, "Prestige XP Bonus");

    /** Streak length required to claim the reward. */
    public final int threshold;

    /** Human-readable display name. */
    public final String displayName;

    StreakReward(int threshold, String displayName) {
        this.threshold = threshold;
        this.displayName = displayName;
    }
}
