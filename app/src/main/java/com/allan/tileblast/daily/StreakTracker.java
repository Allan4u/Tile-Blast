package com.allan.tileblast.daily;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes and tracks the player's consecutive starred-day streak and
 * surfaces newly-reached streak rewards.
 */
public class StreakTracker {

    private static final DateTimeFormatter KEY_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Maximum days to walk backwards when computing the streak. */
    private static final int MAX_LOOKBACK = 365;

    private final ChallengeStorage storage;

    public StreakTracker(ChallengeStorage storage) {
        this.storage = storage;
    }

    /**
     * Returns the persisted streak count (last value written via
     * {@link ChallengeStorage#setStreakCount(int)}).
     */
    public int getCurrentStreak() {
        return storage.getStreakCount();
    }

    /**
     * Recalculates the streak from stored history and persists the result.
     *
     * <p>The streak is the count of consecutive starred days counting
     * backwards from the most recent day that has a star. If neither today
     * nor yesterday is starred, the streak is 0.
     */
    public int calculateStreak() {
        return calculateStreak(LocalDate.now());
    }

    /** Testable overload: compute streak relative to the provided "today". */
    public int calculateStreak(LocalDate today) {
        if (today == null) today = LocalDate.now();

        // Anchor: most recent day with a star, must be today or yesterday.
        // If today is starred, count from today; else if yesterday is starred,
        // count from yesterday; otherwise streak is 0.
        LocalDate anchor;
        if (storage.hasStar(today.format(KEY_FORMAT))) {
            anchor = today;
        } else if (storage.hasStar(today.minusDays(1).format(KEY_FORMAT))) {
            anchor = today.minusDays(1);
        } else {
            storage.setStreakCount(0);
            return 0;
        }

        int streak = 0;
        for (int i = 0; i < MAX_LOOKBACK; i++) {
            LocalDate d = anchor.minusDays(i);
            if (storage.hasStar(d.format(KEY_FORMAT))) {
                streak++;
            } else {
                break;
            }
        }
        storage.setStreakCount(streak);
        return streak;
    }

    /**
     * Returns the list of rewards that the current streak has reached but
     * which have not yet been claimed. Each returned reward is marked as
     * claimed in storage so that subsequent calls do not return duplicates.
     */
    public List<StreakReward> checkRewards() {
        int streak = storage.getStreakCount();
        List<StreakReward> awarded = new ArrayList<>();
        for (StreakReward reward : StreakReward.values()) {
            if (streak >= reward.threshold && !storage.isRewardClaimed(reward)) {
                storage.claimReward(reward);
                awarded.add(reward);
            }
        }
        return awarded;
    }
}
