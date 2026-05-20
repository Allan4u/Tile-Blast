package com.allan.tileblast.leaderboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists score submissions when the device is offline and syncs them to
 * Firestore once connectivity returns.
 *
 * Each queued entry is a Map<String, Object> with the same fields as a Firestore
 * score document plus an internal "_mode" routing field (already part of the
 * score document) used to determine the destination subcollection.
 *
 * Queue is serialized to SharedPreferences as a JSON array.
 */
public class ScoreQueue {

    private static final String TAG = "ScoreQueue";

    private static final String PREFS_NAME = "tile_blast_score_queue";
    private static final String PREF_KEY_QUEUE = "score_queue";

    private final Context appContext;
    private final SharedPreferences prefs;
    private final FirebaseFirestore firestore;

    @Nullable private ConnectivityManager connectivityManager;
    @Nullable private ConnectivityManager.NetworkCallback networkCallback;

    public interface SyncCallback {
        void onSyncComplete(int successCount, int failCount);
    }

    public ScoreQueue(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.firestore = FirebaseFirestore.getInstance();
    }

    // ─── Queue operations ──────────────────────────────────────────────────

    public synchronized void enqueue(@NonNull Map<String, Object> scoreDocument) {
        List<Map<String, Object>> queue = loadQueue();
        queue.add(new LinkedHashMap<>(scoreDocument));
        saveQueue(queue);
    }

    public synchronized List<Map<String, Object>> getQueuedScores() {
        return loadQueue();
    }

    public synchronized void removeFromQueue(int index) {
        List<Map<String, Object>> queue = loadQueue();
        if (index >= 0 && index < queue.size()) {
            queue.remove(index);
            saveQueue(queue);
        }
    }

    public synchronized void clearQueue() {
        prefs.edit().remove(PREF_KEY_QUEUE).apply();
    }

    public synchronized int getQueueSize() {
        return loadQueue().size();
    }

    // ─── Persistence ───────────────────────────────────────────────────────

    /** Visible for tests. */
    void saveQueue(List<Map<String, Object>> queue) {
        try {
            JSONArray arr = new JSONArray();
            for (Map<String, Object> entry : queue) {
                arr.put(new JSONObject(entry));
            }
            prefs.edit().putString(PREF_KEY_QUEUE, arr.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to save queue", e);
        }
    }

    /** Visible for tests. */
    List<Map<String, Object>> loadQueue() {
        List<Map<String, Object>> queue = new ArrayList<>();
        String json = prefs.getString(PREF_KEY_QUEUE, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Map<String, Object> map = new LinkedHashMap<>();
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    Object v = obj.get(k);
                    map.put(k, v);
                }
                queue.add(map);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load queue, clearing", e);
            queue = new ArrayList<>();
        }
        return queue;
    }

    // ─── Connectivity listener ─────────────────────────────────────────────

    public synchronized void registerConnectivityListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        if (networkCallback != null) return; // Already registered

        connectivityManager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.d(TAG, "Network available — syncing queued scores");
                syncQueuedScores(null);
            }
        };
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback);
        } catch (Exception e) {
            Log.w(TAG, "Failed to register network callback", e);
            networkCallback = null;
        }
    }

    public synchronized void unregisterConnectivityListener() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                Log.w(TAG, "Failed to unregister network callback", e);
            }
            networkCallback = null;
        }
    }

    // ─── Sync ──────────────────────────────────────────────────────────────

    /**
     * Submits queued scores to Firestore in chronological (timestamp ascending) order.
     * Successfully submitted entries are removed from the queue.
     * Permanent failures (rules rejection) are removed and logged.
     * Transient failures retain the entry for the next sync attempt.
     */
    public synchronized void syncQueuedScores(@Nullable SyncCallback callback) {
        List<Map<String, Object>> queue = loadQueue();
        if (queue.isEmpty()) {
            if (callback != null) callback.onSyncComplete(0, 0);
            return;
        }

        // Sort oldest-first by timestamp for chronological submission
        Collections.sort(queue, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                long ta = extractTimestamp(a);
                long tb = extractTimestamp(b);
                return Long.compare(ta, tb);
            }
        });

        final int total = queue.size();
        final int[] success = {0};
        final int[] fail = {0};
        final List<Map<String, Object>> remaining =
                Collections.synchronizedList(new ArrayList<Map<String, Object>>());
        final int[] processed = {0};

        for (final Map<String, Object> entry : queue) {
            String mode = entry.get("mode") != null ? entry.get("mode").toString() : null;
            String userId = entry.get("userId") != null ? entry.get("userId").toString() : null;
            if (mode == null || userId == null) {
                // Malformed — drop
                fail[0]++;
                if (++processed[0] == total) finalizeSync(remaining, success, fail, callback);
                continue;
            }
            Map<String, Object> docData = new HashMap<>(entry);
            firestore.collection("leaderboards")
                    .document(mode)
                    .collection("scores")
                    .document(userId)
                    .set(docData)
                    .addOnSuccessListener(aVoid -> {
                        synchronized (this) {
                            success[0]++;
                            if (++processed[0] == total) finalizeSync(remaining, success, fail, callback);
                        }
                    })
                    .addOnFailureListener(e -> {
                        synchronized (this) {
                            if (isPermanentFailure(e)) {
                                Log.w(TAG, "Permanent failure for queued score, dropping: " + e.getMessage());
                                fail[0]++;
                            } else {
                                // Transient — retain for next attempt
                                remaining.add(entry);
                                fail[0]++;
                            }
                            if (++processed[0] == total) finalizeSync(remaining, success, fail, callback);
                        }
                    });
        }
    }

    private void finalizeSync(List<Map<String, Object>> remaining, int[] success, int[] fail,
                              @Nullable SyncCallback callback) {
        saveQueue(new ArrayList<>(remaining));
        if (callback != null) callback.onSyncComplete(success[0], fail[0]);
    }

    private static boolean isPermanentFailure(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            return code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    || code == FirebaseFirestoreException.Code.INVALID_ARGUMENT;
        }
        return false;
    }

    private static long extractTimestamp(Map<String, Object> entry) {
        Object t = entry.get("timestamp");
        if (t instanceof Number) return ((Number) t).longValue();
        if (t instanceof String) {
            try { return Long.parseLong((String) t); } catch (NumberFormatException ignore) { }
        }
        return 0L;
    }
}
