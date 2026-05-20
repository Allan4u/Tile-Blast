package com.allan.tileblast;

import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.allan.tileblast.leaderboard.AuthManager;
import com.allan.tileblast.leaderboard.LeaderboardService;
import com.allan.tileblast.progression.LevelManager;
import com.allan.tileblast.storage.StorageManager;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private LeaderboardService leaderboardService;
    private AuthManager authManager;
    private TextView rankView;

    private LevelManager levelManager;
    private StorageManager storageManager;
    private TextView levelText, xpText, prestigeStars;
    private ProgressBar xpProgress;
    private View btnPrestige;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppContext.init(getApplicationContext());
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        // Progression
        storageManager = new StorageManager(this);
        levelManager = new LevelManager(storageManager);
        levelText = findViewById(R.id.levelText);
        xpProgress = findViewById(R.id.xpProgress);
        xpText = findViewById(R.id.xpText);
        prestigeStars = findViewById(R.id.prestigeStars);
        btnPrestige = findViewById(R.id.btnPrestige);
        btnPrestige.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Activate Prestige?")
                    .setMessage("Reset your level to 1 in exchange for a permanent +10% XP multiplier. Unlocked rewards are kept.")
                    .setPositiveButton("Prestige", (dialog, which) -> {
                        levelManager.activatePrestige();
                        refreshProgressionUi();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // Logo
        ImageView logo = findViewById(R.id.logo);
        logo.setImageResource(R.raw.logo);

        // Initialize Firebase auth on first launch (or restore existing session)
        authManager = AuthManager.getInstance(this);
        leaderboardService = LeaderboardService.getInstance(this);
        if (!authManager.isSignedIn()) {
            authManager.signInAnonymously(null);
        }
        // Register connectivity listener so queued scores sync when network returns
        leaderboardService.getScoreQueue().registerConnectivityListener();

        // Buttons
        findViewById(R.id.btnClassic).setOnClickListener(v -> startGame(8, 3, "classic", 0f));
        findViewById(R.id.btnChaos).setOnClickListener(v -> startGame(10, 5, "chaos", 0f));
        findViewById(R.id.btnTimed60).setOnClickListener(v -> startGame(8, 3, "timed60", 60f));
        findViewById(R.id.btnTimed90).setOnClickListener(v -> startGame(8, 3, "timed90", 90f));
        findViewById(R.id.btnHighScores).setOnClickListener(v -> {
            startActivity(new Intent(this, HighScoreActivity.class));
        });
        findViewById(R.id.btnStatistics).setOnClickListener(v -> {
            startActivity(new Intent(this, StatisticsActivity.class));
        });
        findViewById(R.id.btnAchievements).setOnClickListener(v -> {
            startActivity(new Intent(this, AchievementActivity.class));
        });
        findViewById(R.id.btnLeaderboard).setOnClickListener(v -> {
            Intent i = new Intent(this, LeaderboardActivity.class);
            i.putExtra("mode", leaderboardService.getLastViewedMode());
            startActivity(i);
        });
        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // Apply font
        Typeface fontBold = ResourcesCompat.getFont(this, R.font.silkscreen_bold);
        Typeface fontReg = ResourcesCompat.getFont(this, R.font.silkscreen);
        ((TextView) findViewById(R.id.btnClassicText)).setTypeface(fontBold);
        ((TextView) findViewById(R.id.btnClassicDesc)).setTypeface(fontReg);
        ((TextView) findViewById(R.id.btnChaosText)).setTypeface(fontBold);
        ((TextView) findViewById(R.id.btnChaosDesc)).setTypeface(fontReg);
        ((TextView) findViewById(R.id.btnTimed60Text)).setTypeface(fontBold);
        ((TextView) findViewById(R.id.btnTimed60Desc)).setTypeface(fontReg);
        ((TextView) findViewById(R.id.btnTimed90Text)).setTypeface(fontBold);
        ((TextView) findViewById(R.id.btnTimed90Desc)).setTypeface(fontReg);
        ((TextView) findViewById(R.id.btnHighScoresText)).setTypeface(fontBold);
        ((TextView) findViewById(R.id.btnStatisticsText)).setTypeface(fontBold);
        ((TextView) findViewById(R.id.btnAchievementsText)).setTypeface(fontBold);
        ((TextView) findViewById(R.id.btnLeaderboardText)).setTypeface(fontBold);
        ((TextView) findViewById(R.id.btnSettingsText)).setTypeface(fontBold);

        // Progression typefaces
        levelText.setTypeface(fontBold);
        prestigeStars.setTypeface(fontBold);
        ((TextView) findViewById(R.id.btnPrestigeText)).setTypeface(fontBold);
        xpText.setTypeface(fontReg);
        ((TextView) findViewById(R.id.btnPrestigeDesc)).setTypeface(fontReg);

        rankView = findViewById(R.id.btnLeaderboardRank);
        rankView.setTypeface(fontReg);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showCachedRank();
        refreshPlayerRank();
        refreshProgressionUi();
    }

    private void refreshProgressionUi() {
        if (levelManager == null) return;
        levelManager.load();  // reload from storage in case another activity changed it
        int lv = levelManager.getLevel();
        int xp = levelManager.getCurrentXP();
        int needed = levelManager.getXPForNextLevel();
        int prestige = levelManager.getPrestigeCount();

        levelText.setText("Level " + lv);
        xpText.setText(xp + " / " + needed + " XP");
        int progress = (int) Math.round(levelManager.getProgressRatio() * 100);
        xpProgress.setProgress(Math.max(0, Math.min(100, progress)));

        if (prestige > 0) {
            prestigeStars.setText("★ x" + prestige);
            prestigeStars.setVisibility(View.VISIBLE);
        } else {
            prestigeStars.setVisibility(View.GONE);
        }

        btnPrestige.setVisibility(lv >= LevelManager.MAX_LEVEL ? View.VISIBLE : View.GONE);
    }

    private void startGame(int boardSize, int handSize, String mode, float durationSec) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra("boardSize", boardSize);
        intent.putExtra("handSize", handSize);
        intent.putExtra("mode", mode);
        intent.putExtra("durationSec", durationSec);
        startActivity(intent);
    }

    /** Show whatever rank we last cached so the UI feels instant. */
    private void showCachedRank() {
        if (rankView == null) return;
        String mode = leaderboardService.getLastViewedMode();
        int cached = leaderboardService.getCachedRank(mode);
        rankView.setText(formatRank(cached));
    }

    /** Fire off an async fetch and update the rank text when it completes. */
    private void refreshPlayerRank() {
        if (rankView == null) return;
        final String mode = leaderboardService.getLastViewedMode();
        leaderboardService.getPlayerRank(mode, new LeaderboardService.RankCallback() {
            @Override
            public void onRankLoaded(int rank) {
                runOnUiThread(() -> rankView.setText(formatRank(rank)));
            }
            @Override
            public void onError(String message) {
                // Keep cached value visible on error
            }
        });
    }

    private static String formatRank(int rank) {
        if (rank <= 0) return "Unranked";
        return String.format(Locale.US, "You are #%d", rank);
    }
}
