package com.allan.tileblast.leaderboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.allan.tileblast.storage.StorageManager;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Top-level service for online leaderboard interactions:
 *   - Submitting scores (with online/offline routing)
 *   - Reading top-N leaderboards (all-time and weekly)
 *   - Computing player rank
 *   - Real-time leaderboard updates via snapshot listeners
 *   - Player data deletion
 */
public class LeaderboardService {

    private static final String TAG = "LeaderboardService";

    private static final int TOP_N = 100;
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 999_999;
    private static final long RATE_LIMIT_MS = 10_000L;

    /** Allowed game modes — used by both submission validation and Firestore rules. */
    public static final Set<String> VALID_MODES = new HashSet<>(Arrays.asList(
            "classic", "chaos", "timed60", "timed90", "daily"));

    private static final String PREFS_NAME = "tile_blast_leaderboard";
    private static final String KEY_LAST_SUBMISSION = "last_submission_ms";
    private static final String KEY_CACHED_RANK_PREFIX = "cached_rank_";
    private static final String KEY_LAST_VIEWED_MODE = "last_viewed_mode";
    private static final String KEY_LB_PB_PREFIX = "lb_pb_";

    @SuppressWarnings("StaticFieldLeak")
    private static LeaderboardService instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final FirebaseFirestore firestore;
    private final AuthManager authManager;
    private final ScoreQueue scoreQueue;
    private final StorageManager storageManager;

    @Nullable private ListenerRegistration activeListener;

    public interface LeaderboardCallback {
        void onScoresLoaded(List<LeaderboardEntry> entries);
        void onError(String message);
    }

    public interface RankCallback {
        /** rank is -1 if the player has no submitted scores. */
        void onRankLoaded(int rank);
        void onError(String message);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onFailure(String message);
    }

    private LeaderboardService(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.firestore = FirebaseFirestore.getInstance();
        this.authManager = AuthManager.getInstance(appContext);
        this.scoreQueue = new ScoreQueue(appContext);
        this.storageManager = new StorageManager(appContext);
    }

    public static synchronized LeaderboardService getInstance(Context context) {
        if (instance == null) {
            instance = new LeaderboardService(context);
        }
        return instance;
    }

    public ScoreQueue getScoreQueue() { return scoreQueue; }

    // ─── Score submission ──────────────────────────────────────────────────

    /**
     * @return true iff the score is strictly greater than the player's existing
     *         leaderboard personal best for the given mode (per Requirements 4.1–4.3).
     *         Tracked separately from the local high score so that submission
     *         decisions are independent of local high-score persistence ordering.
     */
    public boolean shouldSubmitScore(int score, String mode) {
        if (!isValidMode(mode)) return false;
        if (!isValidScore(score)) return false;
        int currentBest = prefs.getInt(KEY_LB_PB_PREFIX + mode, 0);
        return score > currentBest;
    }

    /** Returns the leaderboard-tracked personal best for the given mode (0 if none). */
    public int getLeaderboardPersonalBest(String mode) {
        return prefs.getInt(KEY_LB_PB_PREFIX + mode, 0);
    }

    /**
     * Builds the exact 5-field score document required by Property 3 and the
     * Firestore rules: userId, displayName, score, mode, timestamp.
     */
    public Map<String, Object> buildScoreDocument(int score, String mode) {
        Map<String, Object> doc = new LinkedHashMap<>();
        String uid = authManager.getUserId() != null ? authManager.getUserId() : "";
        String name = authManager.getDisplayName() != null ? authManager.getDisplayName() : "Player";
        doc.put("userId", uid);
        doc.put("displayName", name);
        doc.put("score", score);
        doc.put("mode", mode);
        doc.put("timestamp", System.currentTimeMillis());
        return doc;
    }

    /** Score range validation — Property 9. */
    public static boolean isValidScore(int score) {
        return score >= MIN_SCORE && score <= MAX_SCORE;
    }

    /** Game mode validation — Property 11. */
    public static boolean isValidMode(@Nullable String mode) {
        return mode != null && VALID_MODES.contains(mode);
    }

    /** Client-side rate limit check — Requirement 9.3. */
    private boolean isRateLimited() {
        long last = prefs.getLong(KEY_LAST_SUBMISSION, 0L);
        long now = System.currentTimeMillis();
        return last > 0 && (now - last) < RATE_LIMIT_MS;
    }

    private void recordSubmissionTime() {
        prefs.edit().putLong(KEY_LAST_SUBMISSION, System.currentTimeMillis()).apply();
    }

    /**
     * Main entry point invoked at game-over. Performs all gating checks and
     * either writes to Firestore (online) or enqueues for later (offline).
     */
    public void submitScore(int score, String mode) {
        if (!isValidScore(score)) {
            Log.w(TAG, "Rejected submission: score " + score + " out of range");
            return;
        }
        if (!isValidMode(mode)) {
            Log.w(TAG, "Rejected submission: invalid mode " + mode);
            return;
        }
        if (!shouldSubmitScore(score, mode)) {
            // Not a personal best
            return;
        }
        if (isRateLimited()) {
            // Enqueue rather than drop, so the score isn't lost
            Log.d(TAG, "Rate-limited, enqueuing score");
            scoreQueue.enqueue(buildScoreDocument(score, mode));
            return;
        }
        if (authManager.getUserId() == null) {
            // Not signed in yet — enqueue
            scoreQueue.enqueue(buildScoreDocument(score, mode));
            return;
        }

        // Update last-viewed mode for navigation defaults
        prefs.edit().putString(KEY_LAST_VIEWED_MODE, mode).apply();

        Map<String, Object> doc = buildScoreDocument(score, mode);
        recordSubmissionTime();
        String userId = authManager.getUserId();

        firestore.collection("leaderboards")
                .document(mode)
                .collection("scores")
                .document(userId)
                .set(doc)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Score submitted: " + score + " (" + mode + ")");
                    // Update leaderboard personal-best gate now that the write succeeded.
                    prefs.edit().putInt(KEY_LB_PB_PREFIX + mode, score).apply();
                    // Best-effort: update player document with last submission for rate-limit tracking
                    Map<String, Object> playerDoc = new LinkedHashMap<>();
                    playerDoc.put("displayName", doc.get("displayName"));
                    playerDoc.put("lastSubmission", new Timestamp(new java.util.Date()));
                    playerDoc.put("lastMode", mode);
                    firestore.collection("players").document(userId).set(playerDoc, com.google.firebase.firestore.SetOptions.merge());
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Direct submission failed, enqueueing: " + e.getMessage());
                    scoreQueue.enqueue(doc);
                });
    }

    // ─── Week boundary calculation (Property 12) ───────────────────────────

    /** Returns the most recent Monday 00:00:00 UTC at or before now (millis). */
    public long getWeekStartTimestamp() {
        return getWeekStartTimestamp(System.currentTimeMillis());
    }

    /** Returns the Sunday 23:59:59.999 UTC of the week containing now. */
    public long getWeekEndTimestamp() {
        return getWeekEndTimestamp(System.currentTimeMillis());
    }

    /** Visible for tests. */
    public static long getWeekStartTimestamp(long nowMillis) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(nowMillis);
        // Calendar.DAY_OF_WEEK uses 1=Sunday..7=Saturday; Monday=2.
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        // Days to subtract to reach Monday (treating Monday as week start).
        int daysFromMonday = (dow == Calendar.SUNDAY) ? 6 : (dow - Calendar.MONDAY);
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** Visible for tests. */
    public static long getWeekEndTimestamp(long nowMillis) {
        long start = getWeekStartTimestamp(nowMillis);
        // Add 7 days minus 1 millisecond to reach Sunday 23:59:59.999 UTC
        return start + (7L * 24L * 60L * 60L * 1000L) - 1L;
    }

    // ─── Leaderboard queries ───────────────────────────────────────────────

    public void getTopScores(String mode, boolean weekly, int limit,
                             @NonNull LeaderboardCallback callback) {
        if (!isValidMode(mode)) {
            callback.onError("Invalid mode: " + mode);
            return;
        }
        Query query = buildTopScoresQuery(mode, weekly, limit);
        query.get()
                .addOnSuccessListener(snapshot -> callback.onScoresLoaded(snapshotToEntries(snapshot)))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    private Query buildTopScoresQuery(String mode, boolean weekly, int limit) {
        Query q = firestore.collection("leaderboards")
                .document(mode)
                .collection("scores");

        if (weekly) {
            // Weekly filter: scores submitted on/after Monday 00:00 UTC.
            // Query stores timestamp as a number (millis); use long directly.
            q = q.whereGreaterThanOrEqualTo("timestamp", getWeekStartTimestamp());
        }
        return q.orderBy("score", Query.Direction.DESCENDING)
                .limit(Math.max(1, Math.min(limit, TOP_N)));
    }

    private List<LeaderboardEntry> snapshotToEntries(QuerySnapshot snapshot) {
        List<LeaderboardEntry> list = new ArrayList<>();
        int rank = 1;
        for (QueryDocumentSnapshot d : snapshot) {
            LeaderboardEntry e = new LeaderboardEntry();
            e.userId = stringField(d, "userId");
            e.displayName = stringField(d, "displayName");
            e.score = intField(d, "score");
            e.mode = stringField(d, "mode");
            e.timestamp = longField(d, "timestamp");
            e.rank = rank++;
            list.add(e);
        }
        return list;
    }

    public void getPlayerRank(String mode, @NonNull RankCallback callback) {
        if (!isValidMode(mode)) {
            callback.onError("Invalid mode: " + mode);
            return;
        }
        int playerBest = storageManager.getBestScore(mode);
        if (playerBest <= 0) {
            // Cache as -1 (unranked)
            prefs.edit().putInt(KEY_CACHED_RANK_PREFIX + mode, -1).apply();
            callback.onRankLoaded(-1);
            return;
        }
        // Count documents with score strictly greater than player's best
        firestore.collection("leaderboards")
                .document(mode)
                .collection("scores")
                .whereGreaterThan("score", playerBest)
                .get()
                .addOnSuccessListener(snapshot -> {
                    int higher = snapshot.size();
                    int rank = higher + 1;
                    prefs.edit().putInt(KEY_CACHED_RANK_PREFIX + mode, rank).apply();
                    callback.onRankLoaded(rank);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public int getCachedRank(String mode) {
        return prefs.getInt(KEY_CACHED_RANK_PREFIX + mode, -1);
    }

    public String getLastViewedMode() {
        return prefs.getString(KEY_LAST_VIEWED_MODE, "classic");
    }

    public void setLastViewedMode(String mode) {
        if (isValidMode(mode)) {
            prefs.edit().putString(KEY_LAST_VIEWED_MODE, mode).apply();
        }
    }

    // ─── Real-time listener ────────────────────────────────────────────────

    public void attachListener(String mode, boolean weekly,
                               @NonNull LeaderboardCallback callback) {
        detachListener();
        if (!isValidMode(mode)) {
            callback.onError("Invalid mode: " + mode);
            return;
        }
        Query query = buildTopScoresQuery(mode, weekly, TOP_N);
        activeListener = query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                callback.onError(error.getMessage());
                return;
            }
            if (snapshot != null) {
                callback.onScoresLoaded(snapshotToEntries(snapshot));
            }
        });
    }

    public void detachListener() {
        if (activeListener != null) {
            activeListener.remove();
            activeListener = null;
        }
    }

    // ─── Data deletion ─────────────────────────────────────────────────────

    public void deleteAllPlayerData(@NonNull DeleteCallback callback) {
        String userId = authManager.getUserId();
        if (userId == null) {
            callback.onFailure("Not signed in");
            return;
        }

        // Each player has at most one score per mode (doc id == userId), so deleting
        // those documents is sufficient. Then delete the player profile document.
        final int totalDocs = VALID_MODES.size() + 1; // modes + player doc
        final int[] completed = {0};
        final boolean[] failed = {false};

        Runnable maybeDone = () -> {
            if (completed[0] >= totalDocs) {
                if (failed[0]) callback.onFailure("Some deletions failed");
                else callback.onSuccess();
            }
        };

        for (String mode : VALID_MODES) {
            firestore.collection("leaderboards")
                    .document(mode)
                    .collection("scores")
                    .document(userId)
                    .delete()
                    .addOnCompleteListener(t -> {
                        if (!t.isSuccessful()) {
                            // Document not existing is fine; only flag actual permission errors
                            Log.d(TAG, "Delete (mode=" + mode + ") result: " + t.getException());
                        }
                        completed[0]++;
                        maybeDone.run();
                    });
        }
        firestore.collection("players").document(userId).delete()
                .addOnCompleteListener(t -> {
                    if (!t.isSuccessful() && t.getException() != null) {
                        Log.d(TAG, "Delete player doc: " + t.getException().getMessage());
                    }
                    completed[0]++;
                    maybeDone.run();
                });
    }

    // ─── Field extraction helpers ──────────────────────────────────────────

    private static String stringField(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        return v != null ? v.toString() : "";
    }

    private static int intField(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return 0;
    }

    private static long longField(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof Timestamp) return ((Timestamp) v).toDate().getTime();
        return 0L;
    }
}
