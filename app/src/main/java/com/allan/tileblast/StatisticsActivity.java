package com.allan.tileblast;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.allan.tileblast.stats.StatisticsManager;

import java.util.Locale;

public class StatisticsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_statistics);

        Typeface fontBold = ResourcesCompat.getFont(this, R.font.silkscreen_bold);
        Typeface fontReg = ResourcesCompat.getFont(this, R.font.silkscreen);

        // Title and section headers use bold
        applyFont(fontBold, R.id.statsTitle, R.id.sectionGames, R.id.sectionAverage,
                R.id.sectionCombo, R.id.sectionLines, R.id.sectionPlaytime, R.id.sectionStreak);

        // Stat values use regular
        applyFont(fontReg, R.id.statClassicGames, R.id.statChaosGames, R.id.statTimedGames,
                R.id.statTotalGames, R.id.statClassicAvg, R.id.statChaosAvg, R.id.statTimedAvg,
                R.id.statBestCombo, R.id.statTotalLines, R.id.statPlayTime,
                R.id.statStreakCurrent, R.id.statStreakBest);

        applyFont(fontBold, R.id.btnBackText);

        StatisticsManager stats = new StatisticsManager(this);

        // Games played per mode and total
        setText(R.id.statClassicGames, "Classic: " + stats.getGamesPlayed("classic"));
        setText(R.id.statChaosGames,   "Chaos: "   + stats.getGamesPlayed("chaos"));
        setText(R.id.statTimedGames,   "Timed: "   + stats.getGamesPlayed("timed"));
        setText(R.id.statTotalGames,   "Total: "   + stats.getTotalGamesPlayed());

        // Average score per mode
        setText(R.id.statClassicAvg, "Classic: " + formatScore(stats.getAverageScore("classic")));
        setText(R.id.statChaosAvg,   "Chaos: "   + formatScore(stats.getAverageScore("chaos")));
        setText(R.id.statTimedAvg,   "Timed: "   + formatScore(stats.getAverageScore("timed")));

        // Best combo
        setText(R.id.statBestCombo, "x" + stats.getBestCombo());

        // Total lines
        setText(R.id.statTotalLines, String.format(Locale.US, "%,d", stats.getTotalLinesCleared()));

        // Play time in whole minutes
        long totalMinutes = stats.getTotalPlayTimeSeconds() / 60L;
        setText(R.id.statPlayTime, totalMinutes + " min");

        // Win streaks
        setText(R.id.statStreakCurrent, "Current: " + stats.getCurrentWinStreak());
        setText(R.id.statStreakBest,    "Best: "    + stats.getBestWinStreak());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void setText(int id, String text) {
        TextView tv = findViewById(id);
        if (tv != null) tv.setText(text);
    }

    private void applyFont(Typeface font, int... ids) {
        if (font == null) return;
        for (int id : ids) {
            TextView tv = findViewById(id);
            if (tv != null) tv.setTypeface(font);
        }
    }

    private String formatScore(int score) {
        return String.format(Locale.US, "%,d", score);
    }
}
