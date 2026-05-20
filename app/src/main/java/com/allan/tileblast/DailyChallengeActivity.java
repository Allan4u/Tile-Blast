package com.allan.tileblast;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.allan.tileblast.audio.AudioManager;
import com.allan.tileblast.daily.CalendarView;
import com.allan.tileblast.daily.ChallengeStorage;
import com.allan.tileblast.daily.DailyChallengeEngine;
import com.allan.tileblast.daily.StreakReward;
import com.allan.tileblast.daily.StreakTracker;
import com.allan.tileblast.game.GameView;
import com.allan.tileblast.progression.Reward;

import java.util.List;
import java.util.Random;

/**
 * Hosts the daily challenge: produces a deterministic seed for the local
 * date, drives the {@link GameView} with a seeded {@link Random}, persists
 * results, updates the streak, and exposes the calendar view.
 */
public class DailyChallengeActivity extends AppCompatActivity
        implements GameView.GameCallback {

    private GameView gameView;
    private FrameLayout rootLayout;
    private ChallengeStorage storage;
    private StreakTracker streakTracker;
    private long seed;
    private String todayKey;
    private boolean resultShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        storage = new ChallengeStorage(this);
        streakTracker = new StreakTracker(storage);

        seed = DailyChallengeEngine.generateSeed();
        todayKey = DailyChallengeEngine.todayKey();
        Random rng = DailyChallengeEngine.createRandom(seed);

        gameView = new GameView(this);
        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);
        rootLayout.addView(gameView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(rootLayout);

        gameView.setup(
                DailyChallengeEngine.BOARD_SIZE,
                DailyChallengeEngine.HAND_SIZE,
                "daily",
                this,
                rng);

        if (savedInstanceState != null) {
            gameView.restoreState(savedInstanceState);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
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
        if (resultShown) return;
        resultShown = true;

        // 1. Record best score for today.
        storage.recordScore(todayKey, finalScore);

        // 2. Award star if target met.
        boolean starEarned = finalScore >= DailyChallengeEngine.TARGET_SCORE;
        if (starEarned) {
            storage.awardStar(todayKey);
        }

        // 3. Recompute streak.
        streakTracker.calculateStreak();

        // 4. Check newly-reached rewards.
        List<StreakReward> newRewards = streakTracker.checkRewards();

        // 5. Show result dialog.
        showResultDialog(finalScore, starEarned, newRewards);
    }

    @Override
    public void onPauseRequested() {
        finish();
    }

    @Override
    public void onLevelUp(List<Integer> newLevels, List<Reward> newRewards) {
        // Daily challenge surfaces its own result dialog; level-up is acknowledged
        // silently here so progression still happens but doesn't compete with the
        // streak/star result UI.
    }

    private void showResultDialog(int score, boolean starEarned, List<StreakReward> newRewards) {
        Typeface fontBold = ResourcesCompat.getFont(this, R.font.silkscreen_bold);
        Typeface fontRegular = ResourcesCompat.getFont(this, R.font.silkscreen);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        content.setBackgroundColor(0xFF0A0A0A);
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText(starEarned ? "★ STAR EARNED!" : "Daily Challenge");
        title.setTextColor(starEarned ? 0xFFFFD700 : 0xFFFFFFFF);
        title.setTextSize(26);
        title.setTypeface(fontBold);
        title.setGravity(Gravity.CENTER);
        content.addView(title);

        TextView scoreLine = new TextView(this);
        scoreLine.setText("Score: " + score);
        scoreLine.setTextColor(0xFFFFFFFF);
        scoreLine.setTextSize(20);
        scoreLine.setTypeface(fontRegular);
        scoreLine.setGravity(Gravity.CENTER);
        scoreLine.setPadding(0, pad / 2, 0, 0);
        content.addView(scoreLine);

        TextView targetLine = new TextView(this);
        targetLine.setText("Target: " + DailyChallengeEngine.TARGET_SCORE);
        targetLine.setTextColor(0xFFAAAAAA);
        targetLine.setTextSize(16);
        targetLine.setTypeface(fontRegular);
        targetLine.setGravity(Gravity.CENTER);
        content.addView(targetLine);

        int streak = streakTracker.getCurrentStreak();
        TextView streakLine = new TextView(this);
        streakLine.setText("\uD83D\uDD25 Streak: " + streak + (streak == 1 ? " day" : " days"));
        streakLine.setTextColor(0xFFFFD700);
        streakLine.setTextSize(18);
        streakLine.setTypeface(fontBold);
        streakLine.setGravity(Gravity.CENTER);
        streakLine.setPadding(0, pad / 2, 0, 0);
        content.addView(streakLine);

        if (newRewards != null && !newRewards.isEmpty()) {
            TextView rewardHeader = new TextView(this);
            rewardHeader.setText("Rewards Unlocked!");
            rewardHeader.setTextColor(0xFFFFD700);
            rewardHeader.setTextSize(16);
            rewardHeader.setTypeface(fontBold);
            rewardHeader.setGravity(Gravity.CENTER);
            rewardHeader.setPadding(0, pad / 2, 0, 0);
            content.addView(rewardHeader);
            for (StreakReward reward : newRewards) {
                TextView rewardLine = new TextView(this);
                rewardLine.setText("• " + reward.displayName);
                rewardLine.setTextColor(0xFFFFFFFF);
                rewardLine.setTextSize(14);
                rewardLine.setTypeface(fontRegular);
                rewardLine.setGravity(Gravity.CENTER);
                content.addView(rewardLine);
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(content);
        builder.setCancelable(false);

        builder.setPositiveButton("View Calendar", (dialog, which) -> {
            dialog.dismiss();
            showCalendar();
        });
        builder.setNegativeButton("Back", (dialog, which) -> {
            dialog.dismiss();
            finish();
        });

        AlertDialog dlg = builder.create();
        dlg.show();
    }

    /**
     * Displays a fullscreen calendar overlay populated from storage. Tapping
     * the close button returns to the daily-challenge result screen.
     */
    private void showCalendar() {
        Typeface fontBold = ResourcesCompat.getFont(this, R.font.silkscreen_bold);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xF5000000);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        column.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Last 30 Days");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22);
        title.setTypeface(fontBold);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, pad, 0, pad);
        column.addView(title);

        CalendarView calendar = new CalendarView(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f);
        column.addView(calendar, cp);

        calendar.setData(
                storage.getCalendarEntries(java.time.LocalDate.now()),
                streakTracker.getCurrentStreak());

        Button close = new Button(this);
        close.setText("Close");
        close.setTypeface(fontBold);
        close.setTextColor(0xFFFFFFFF);
        close.setBackgroundColor(0xFF1565C0);
        close.setOnClickListener(v -> rootLayout.removeView(overlay));

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = pad;
        bp.bottomMargin = pad;
        column.addView(close, bp);

        overlay.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        rootLayout.addView(overlay);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (gameView != null) {
            gameView.saveState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        AudioManager.getInstance(this).release();
        super.onDestroy();
    }
}
