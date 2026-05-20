package com.allan.tileblast.leaderboard;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.allan.tileblast.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for leaderboard rows. Highlights the current player
 * by matching userId.
 */
public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    private static final int HIGHLIGHT_COLOR = 0x33FFD700; // translucent gold
    private static final int DEFAULT_BG = 0x00000000;      // transparent

    private final List<LeaderboardEntry> entries = new ArrayList<>();
    @Nullable private String currentUserId;

    public void setEntries(@Nullable List<LeaderboardEntry> newEntries) {
        entries.clear();
        if (newEntries != null) entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    public void setCurrentUserId(@Nullable String userId) {
        this.currentUserId = userId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard_entry, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        LeaderboardEntry e = entries.get(position);
        h.rank.setText(String.format(Locale.US, "#%d", e.rank));
        h.name.setText(e.displayName == null ? "Player" : e.displayName);
        h.score.setText(String.format(Locale.US, "%,d", e.score));

        boolean isMe = currentUserId != null && currentUserId.equals(e.userId);
        h.itemView.setBackgroundColor(isMe ? HIGHLIGHT_COLOR : DEFAULT_BG);

        // Optional gold tint for #1
        int color = (e.rank == 1) ? 0xFFFFD700 : (isMe ? 0xFFFFFF66 : 0xFFCCCCCC);
        h.rank.setTextColor(color);
        h.name.setTextColor(color);
        h.score.setTextColor(color);
    }

    @Override
    public int getItemCount() { return entries.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView rank;
        final TextView name;
        final TextView score;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            rank = itemView.findViewById(R.id.entryRank);
            name = itemView.findViewById(R.id.entryName);
            score = itemView.findViewById(R.id.entryScore);
            Typeface font = ResourcesCompat.getFont(itemView.getContext(), R.font.silkscreen);
            if (font != null) {
                rank.setTypeface(font);
                name.setTypeface(font);
                score.setTypeface(font);
            }
        }
    }
}
