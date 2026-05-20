package com.allan.tileblast.daily;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence layer for the daily-challenge feature. Stores per-day scores,
 * star awards, the current streak count, and which streak rewards have been
 * claimed in a dedicated {@link SharedPreferences} file.
 *
 * <p>All getters return safe defaults if the underlying JSON is malformed,
 * missing, or otherwise unreadable.
 */
public class ChallengeStorage {

    private static final String TAG = "ChallengeStorage";
    private static final String PREFS_NAME = "daily_challenge_prefs";

    private static final String KEY_SCORES = "daily_scores";
    private static final String KEY_STARS = "daily_stars";
    private static final String KEY_STREAK = "streak_count";
    private static final String KEY_REWARDS = "claimed_rewards";

    private static final DateTimeFormatter KEY_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SharedPreferences prefs;

    public ChallengeStorage(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ----- Score / Star -----

    /**
     * Records a score for a given date, retaining the maximum of the existing
     * and new value. Invalid existing data is treated as if there were none.
     */
    public void recordScore(String dateKey, int score) {
        if (dateKey == null) return;
        if (score < 0) score = 0;
        JSONObject scores = readScores();
        int existing = scores.optInt(dateKey, 0);
        if (score > existing) {
            try {
                scores.put(dateKey, score);
                prefs.edit().putString(KEY_SCORES, scores.toString()).apply();
            } catch (Exception e) {
                Log.w(TAG, "recordScore: " + e.getMessage());
            }
        }
    }

    /** Returns the recorded score for the date, or 0 if none. */
    public int getScore(String dateKey) {
        if (dateKey == null) return 0;
        return readScores().optInt(dateKey, 0);
    }

    /** Returns true if a star has been awarded for the date. */
    public boolean hasStar(String dateKey) {
        if (dateKey == null) return false;
        JSONArray stars = readStars();
        for (int i = 0; i < stars.length(); i++) {
            if (dateKey.equals(stars.optString(i, null))) return true;
        }
        return false;
    }

    /** Awards a star for the date (idempotent). */
    public void awardStar(String dateKey) {
        if (dateKey == null) return;
        if (hasStar(dateKey)) return;
        JSONArray stars = readStars();
        stars.put(dateKey);
        prefs.edit().putString(KEY_STARS, stars.toString()).apply();
    }

    // ----- Streak -----

    /** Returns the persisted streak count (0 if absent or negative). */
    public int getStreakCount() {
        int v = prefs.getInt(KEY_STREAK, 0);
        return Math.max(0, v);
    }

    /** Persists the streak count (clamped to >= 0). */
    public void setStreakCount(int count) {
        prefs.edit().putInt(KEY_STREAK, Math.max(0, count)).apply();
    }

    // ----- Rewards -----

    /** Returns true if the given reward has already been claimed. */
    public boolean isRewardClaimed(StreakReward reward) {
        if (reward == null) return false;
        JSONArray claimed = readClaimedRewards();
        for (int i = 0; i < claimed.length(); i++) {
            if (reward.name().equals(claimed.optString(i, null))) return true;
        }
        return false;
    }

    /** Marks the given reward as claimed (idempotent). */
    public void claimReward(StreakReward reward) {
        if (reward == null) return;
        if (isRewardClaimed(reward)) return;
        JSONArray claimed = readClaimedRewards();
        claimed.put(reward.name());
        prefs.edit().putString(KEY_REWARDS, claimed.toString()).apply();
    }

    // ----- Calendar -----

    /**
     * Returns 30 entries representing the most recent 30 calendar days,
     * ordered from oldest (index 0) to most recent (index 29 = today).
     */
    public List<DayEntry> getCalendarEntries(LocalDate today) {
        List<DayEntry> result = new ArrayList<>(30);
        if (today == null) today = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String key = d.format(KEY_FORMAT);
            DayEntry e = new DayEntry();
            e.dateKey = key;
            e.score = getScore(key);
            e.starred = hasStar(key);
            e.isToday = (i == 0);
            result.add(e);
        }
        return result;
    }

    // ----- Internal JSON readers (corruption-resilient) -----

    private JSONObject readScores() {
        String raw = prefs.getString(KEY_SCORES, null);
        if (raw == null || raw.isEmpty()) return new JSONObject();
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            Log.w(TAG, "Corrupted scores JSON; resetting to empty");
            return new JSONObject();
        }
    }

    private JSONArray readStars() {
        String raw = prefs.getString(KEY_STARS, null);
        if (raw == null || raw.isEmpty()) return new JSONArray();
        try {
            return new JSONArray(raw);
        } catch (Exception e) {
            Log.w(TAG, "Corrupted stars JSON; resetting to empty");
            return new JSONArray();
        }
    }

    private JSONArray readClaimedRewards() {
        String raw = prefs.getString(KEY_REWARDS, null);
        if (raw == null || raw.isEmpty()) return new JSONArray();
        try {
            return new JSONArray(raw);
        } catch (Exception e) {
            Log.w(TAG, "Corrupted rewards JSON; resetting to empty");
            return new JSONArray();
        }
    }
}
