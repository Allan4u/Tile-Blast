package com.allan.tileblast.storage;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StorageManager {
    private static final String PREFS_NAME = "tile_blast_prefs";
    private static final String KEY_SCORES = "high_scores";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_PLAYER_LEVEL = "player_level";
    private static final String KEY_PLAYER_XP = "player_xp";
    private static final String KEY_PRESTIGE_COUNT = "prestige_count";
    private static final String KEY_UNLOCKED_REWARDS = "unlocked_rewards";
    private static final String KEY_ACTIVE_PALETTE = "active_palette";
    private static final String KEY_ACTIVE_SKIN = "active_skin";
    private static final String KEY_TOTAL_SCORE = "total_score";
    private static final int MAX_SCORES = 100;
    private SharedPreferences prefs;

    public StorageManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static class HighScore implements Comparable<HighScore> {
        public int score;
        public long date;
        public String mode; // "classic" or "chaos"

        public HighScore(int score, long date, String mode) {
            this.score = score;
            this.date = date;
            this.mode = mode;
        }

        @Override
        public int compareTo(HighScore o) {
            return Integer.compare(o.score, this.score); // descending
        }
    }

    public void saveScore(int score, String mode) {
        try {
            String json = prefs.getString(KEY_SCORES, "[]");
            JSONArray arr = new JSONArray(json);
            JSONObject obj = new JSONObject();
            obj.put("score", score);
            obj.put("date", System.currentTimeMillis());
            obj.put("mode", mode);
            arr.put(obj);

            // Sort by score descending and truncate to MAX_SCORES
            if (arr.length() > MAX_SCORES) {
                List<JSONObject> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    list.add(arr.getJSONObject(i));
                }
                Collections.sort(list, (a, b) -> {
                    try {
                        return Integer.compare(b.getInt("score"), a.getInt("score"));
                    } catch (Exception e) {
                        return 0;
                    }
                });
                arr = new JSONArray();
                for (int i = 0; i < Math.min(list.size(), MAX_SCORES); i++) {
                    arr.put(list.get(i));
                }
            }

            prefs.edit().putString(KEY_SCORES, arr.toString()).apply();
        } catch (Exception e) { /* ignore */ }
    }

    public List<HighScore> getHighScores(String mode, int limit) {
        List<HighScore> scores = new ArrayList<>();
        try {
            String json = prefs.getString(KEY_SCORES, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String m = obj.getString("mode");
                int s = obj.getInt("score");
                if (m.equals(mode) && s > 0) {
                    scores.add(new HighScore(s, obj.getLong("date"), m));
                }
            }
        } catch (Exception e) { /* ignore */ }
        Collections.sort(scores);
        if (limit > 0 && scores.size() > limit) {
            scores = scores.subList(0, limit);
        }
        return scores;
    }

    public int getBestScore(String mode) {
        List<HighScore> scores = getHighScores(mode, 1);
        return scores.isEmpty() ? 0 : scores.get(0).score;
    }

    public void setVolume(float volume) {
        prefs.edit().putFloat(KEY_VOLUME, volume).apply();
    }

    public float getVolume() {
        return prefs.getFloat(KEY_VOLUME, 1.0f);
    }

    // ─── LEVEL PROGRESSION PERSISTENCE ───────────────────────────────────────

    /**
     * Persists the player's progression state in a single edit.
     * Unlocked rewards are stored as a JSON array of enum names.
     */
    public void saveProgression(int level, int xp, int prestigeCount, List<String> unlockedRewards) {
        try {
            JSONArray arr = new JSONArray();
            if (unlockedRewards != null) {
                for (String name : unlockedRewards) {
                    if (name != null) arr.put(name);
                }
            }
            prefs.edit()
                    .putInt(KEY_PLAYER_LEVEL, level)
                    .putInt(KEY_PLAYER_XP, xp)
                    .putInt(KEY_PRESTIGE_COUNT, prestigeCount)
                    .putString(KEY_UNLOCKED_REWARDS, arr.toString())
                    .apply();
        } catch (Exception e) { /* ignore */ }
    }

    public int loadLevel() {
        int v = prefs.getInt(KEY_PLAYER_LEVEL, 1);
        if (v < 1) v = 1;
        if (v > 100) v = 100;
        return v;
    }

    public int loadXP() {
        int v = prefs.getInt(KEY_PLAYER_XP, 0);
        return v < 0 ? 0 : v;
    }

    public int loadPrestigeCount() {
        int v = prefs.getInt(KEY_PRESTIGE_COUNT, 0);
        return v < 0 ? 0 : v;
    }

    public List<String> loadUnlockedRewards() {
        List<String> out = new ArrayList<>();
        try {
            String json = prefs.getString(KEY_UNLOCKED_REWARDS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, null);
                if (s != null && !s.isEmpty()) out.add(s);
            }
        } catch (Exception e) {
            // Corrupted data — return empty list.
        }
        return out;
    }

    // ─── THEME / CUSTOMIZATION PERSISTENCE ───────────────────────────────────

    /** Returns the persisted active color palette enum name, or {@code null} if unset. */
    public String getActivePaletteId() {
        return prefs.getString(KEY_ACTIVE_PALETTE, null);
    }

    public void setActivePaletteId(String id) {
        prefs.edit().putString(KEY_ACTIVE_PALETTE, id).apply();
    }

    /** Returns the persisted active board skin enum name, or {@code null} if unset. */
    public String getActiveSkinId() {
        return prefs.getString(KEY_ACTIVE_SKIN, null);
    }

    public void setActiveSkinId(String id) {
        prefs.edit().putString(KEY_ACTIVE_SKIN, id).apply();
    }

    /**
     * Returns the cumulative total score across all games. Always non-negative;
     * negative values are clamped to 0 as a defensive measure against direct
     * SharedPreferences edits.
     */
    public int getTotalScore() {
        return Math.max(0, prefs.getInt(KEY_TOTAL_SCORE, 0));
    }

    /** Persists the cumulative total score, clamping to non-negative. */
    public void setTotalScore(int score) {
        prefs.edit().putInt(KEY_TOTAL_SCORE, Math.max(0, score)).apply();
    }

    /**
     * Adds {@code delta} to the persisted total score. Non-positive deltas are
     * ignored. The new total is clamped to non-negative.
     */
    public void addToTotalScore(int delta) {
        if (delta <= 0) return;
        setTotalScore(getTotalScore() + delta);
    }
}
