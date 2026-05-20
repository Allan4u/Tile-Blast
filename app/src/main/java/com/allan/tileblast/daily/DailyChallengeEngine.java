package com.allan.tileblast.daily;

import java.time.LocalDate;
import java.util.Random;

/**
 * Daily challenge engine: produces a deterministic seed for the current day
 * and creates a {@link Random} instance from it. The seed format is
 * {@code year * 10000 + month * 100 + day} (YYYYMMDD), so all players
 * receive the same puzzle on the same calendar date.
 *
 * <p>This class is a pure static utility — no mutable state.
 */
public final class DailyChallengeEngine {

    /** Score required to earn a star for the day. */
    public static final int TARGET_SCORE = 2000;

    /** Daily challenge board dimensions (rows = cols = 8). */
    public static final int BOARD_SIZE = 8;

    /** Number of pieces in the hand at any time. */
    public static final int HAND_SIZE = 3;

    private DailyChallengeEngine() { /* utility class */ }

    /**
     * Generates the seed for the device's current local date.
     *
     * @return seed value in YYYYMMDD format
     */
    public static long generateSeed() {
        return generateSeed(LocalDate.now());
    }

    /**
     * Generates the seed for the given {@link LocalDate}. Exposed for
     * testability — callers can pass an arbitrary date without depending on
     * the system clock.
     *
     * @param date the date to use; must not be {@code null}
     * @return seed value as {@code year * 10000 + month * 100 + day}
     */
    public static long generateSeed(LocalDate date) {
        return (long) date.getYear() * 10000L
                + (long) date.getMonthValue() * 100L
                + (long) date.getDayOfMonth();
    }

    /**
     * Creates a {@link Random} instance seeded with the provided value.
     * Two callers using the same seed will observe identical sequences.
     *
     * @param seed the seed value
     * @return a new {@link Random} instance seeded with {@code seed}
     */
    public static Random createRandom(long seed) {
        return new Random(seed);
    }

    /**
     * Returns the date key (YYYYMMDD string) for today.
     */
    public static String todayKey() {
        return dateKey(LocalDate.now());
    }

    /**
     * Returns the date key (YYYYMMDD string) for the given date.
     */
    public static String dateKey(LocalDate date) {
        return String.format("%04d%02d%02d",
                date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }
}
