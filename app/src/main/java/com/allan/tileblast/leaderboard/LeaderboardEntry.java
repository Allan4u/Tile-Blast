package com.allan.tileblast.leaderboard;

/**
 * Plain data class representing a single leaderboard row.
 * Rank is computed client-side from the position in the sorted list.
 */
public class LeaderboardEntry {
    public String userId;
    public String displayName;
    public int score;
    public String mode;
    public long timestamp;
    public int rank;

    public LeaderboardEntry() { }

    public LeaderboardEntry(String userId, String displayName, int score,
                            String mode, long timestamp, int rank) {
        this.userId = userId;
        this.displayName = displayName;
        this.score = score;
        this.mode = mode;
        this.timestamp = timestamp;
        this.rank = rank;
    }
}
