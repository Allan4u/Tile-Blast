package com.allan.tileblast;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.allan.tileblast.audio.AudioManager;
import com.allan.tileblast.game.GameView;

public class GameActivity extends AppCompatActivity implements GameView.GameCallback {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        int boardSize = getIntent().getIntExtra("boardSize", 8);
        int handSize = getIntent().getIntExtra("handSize", 3);
        String mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "classic";

        gameView = new GameView(this);
        setContentView(gameView);
        gameView.setup(boardSize, handSize, mode, this);

        if (savedInstanceState != null) {
            gameView.restoreState(savedInstanceState);
        }

        // Register back press handler (replaces deprecated onBackPressed)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
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
        // Score already saved in GameView
    }

    @Override
    public void onPauseRequested() {
        finish(); // Go back to menu
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
