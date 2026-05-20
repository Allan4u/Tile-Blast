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
}
