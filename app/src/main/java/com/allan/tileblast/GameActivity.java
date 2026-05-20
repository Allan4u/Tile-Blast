package com.allan.tileblast;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.allan.tileblast.audio.AudioManager;
import com.allan.tileblast.game.GameView;
import com.allan.tileblast.leaderboard.LeaderboardService;
import com.allan.tileblast.progression.Reward;

import java.util.List;

public class GameActivity extends AppCompatActivity implements GameView.GameCallback {

    private GameView gameView;
    private FrameLayout rootLayout;
    private View levelUpOverlay;
    private LeaderboardService leaderboardService;
    private String currentMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        int boardSize = getIntent().getIntExtra("boardSize", 8);
        int handSize = getIntent().getIntExtra("handSize", 3);
        String mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "classic";
        float duration = getIntent().getFloatExtra("durationSec", 0f);
        this.currentMode = mode;
        this.leaderboardService = LeaderboardService.getInstance(this);

        // Wrap GameView in a FrameLayout so we can stack the level-up overlay above.
        rootLayout = new FrameLayout(this);
        gameView = new GameView(this);
        rootLayout.addView(gameView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(rootLayout);

        gameView.setup(boardSize, handSize, mode, duration, this);

        if (savedInstanceState != null) {
            gameView.restoreState(savedInstanceState);
        }

        // Register back press handler (replaces deprecated onBackPressed)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // If a level-up overlay is showing, dismiss it first.
                if (levelUpOverlay != null) {
                    dismissLevelUpOverlay();
                    return;
                }
                // First, let GameView handle it (cancels power-up targeting)
                if (gameView != null && gameView.onBackPressed()) {
                    return;
                }
                if (gameView != null && !gameView.isGameOver()) {
                    if (gameView.isPaused()) {
                        finish();
                    } else {
                        gameView.setPaused(true);
                    }
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    public void onGameOver(int finalScore) {
        // Local high score is already saved by GameView. Submit to online leaderboard
        // (no-op if not a personal best, offline-queued if no connectivity).
        if (leaderboardService != null && currentMode != null) {
            leaderboardService.submitScore(finalScore, currentMode);
        }
    }

    @Override
    public void onPauseRequested() {
        finish(); // Go back to menu
    }

    @Override
    public void onLevelUp(List<Integer> newLevels, List<Reward> newRewards) {
        if (newLevels == null || newLevels.isEmpty()) return;
        // Use the highest level reached as the headline number.
        int highestLevel = newLevels.get(newLevels.size() - 1);
        showLevelUpOverlay(highestLevel, newRewards);
    }

    private void showLevelUpOverlay(int newLevel, List<Reward> newRewards) {
        if (levelUpOverlay != null) {
            // Replace existing overlay with the latest one.
            rootLayout.removeView(levelUpOverlay);
            levelUpOverlay = null;
        }

        Typeface fontBold = ResourcesCompat.getFont(this, R.font.silkscreen_bold);
        Typeface fontRegular = ResourcesCompat.getFont(this, R.font.silkscreen);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackground(new ColorDrawable(0xCC000000));
        overlay.setClickable(true);
        overlay.setFocusable(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("LEVEL UP!");
        title.setTypeface(fontBold);
        title.setTextColor(0xFFFFD700); // gold
        title.setTextSize(40);
        title.setGravity(Gravity.CENTER);
        content.addView(title);

        TextView levelText = new TextView(this);
        levelText.setText("LV." + newLevel);
        levelText.setTypeface(fontBold);
        levelText.setTextColor(Color.WHITE);
        levelText.setTextSize(64);
        levelText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lvLp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        content.addView(levelText, lvLp);

        if (newRewards != null && !newRewards.isEmpty()) {
            TextView rewardHeader = new TextView(this);
            rewardHeader.setText("UNLOCKED");
            rewardHeader.setTypeface(fontBold);
            rewardHeader.setTextColor(0xFFFFD700);
            rewardHeader.setTextSize(18);
            rewardHeader.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rhLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rhLp.topMargin = (int) (32 * getResources().getDisplayMetrics().density);
            content.addView(rewardHeader, rhLp);

            for (Reward r : newRewards) {
                TextView rv = new TextView(this);
                rv.setText(r.displayName);
                rv.setTypeface(fontRegular);
                rv.setTextColor(Color.WHITE);
                rv.setTextSize(20);
                rv.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rLp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
                content.addView(rv, rLp);
            }
        }

        TextView dismissHint = new TextView(this);
        dismissHint.setText("TAP TO CONTINUE");
        dismissHint.setTypeface(fontRegular);
        dismissHint.setTextColor(0xFFAAAAAA);
        dismissHint.setTextSize(14);
        dismissHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = (int) (40 * getResources().getDisplayMetrics().density);
        content.addView(dismissHint, hintLp);

        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        contentLp.gravity = Gravity.CENTER;
        overlay.addView(content, contentLp);

        overlay.setOnClickListener(v -> dismissLevelUpOverlay());

        rootLayout.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        levelUpOverlay = overlay;
    }

    private void dismissLevelUpOverlay() {
        if (levelUpOverlay != null) {
            rootLayout.removeView(levelUpOverlay);
            levelUpOverlay = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (gameView != null) {
            gameView.saveState(outState);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (leaderboardService != null) {
            leaderboardService.getScoreQueue().registerConnectivityListener();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Auto-pause Timed Mode rounds when the activity backgrounds (Requirement 9.4).
        if (gameView != null && !gameView.isGameOver()) {
            gameView.setPaused(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (leaderboardService != null) {
            leaderboardService.getScoreQueue().unregisterConnectivityListener();
        }
    }

    @Override
    protected void onDestroy() {
        AudioManager.getInstance(this).release();
        super.onDestroy();
    }
}
