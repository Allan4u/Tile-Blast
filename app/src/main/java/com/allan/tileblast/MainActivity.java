package com.allan.tileblast;

import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        // Logo
        ImageView logo = findViewById(R.id.logo);
        logo.setImageResource(R.raw.logo);

        // Buttons
        findViewById(R.id.btnClassic).setOnClickListener(v -> startGame(8, 3, "classic"));
        findViewById(R.id.btnChaos).setOnClickListener(v -> startGame(10, 5, "chaos"));
        findViewById(R.id.btnHighScores).setOnClickListener(v -> {
            startActivity(new Intent(this, HighScoreActivity.class));
        });

        // Apply font
        Typeface font = ResourcesCompat.getFont(this, R.font.silkscreen_bold);
        ((TextView) findViewById(R.id.btnClassicText)).setTypeface(font);
        ((TextView) findViewById(R.id.btnClassicDesc)).setTypeface(ResourcesCompat.getFont(this, R.font.silkscreen));
        ((TextView) findViewById(R.id.btnChaosText)).setTypeface(font);
        ((TextView) findViewById(R.id.btnChaosDesc)).setTypeface(ResourcesCompat.getFont(this, R.font.silkscreen));
        ((TextView) findViewById(R.id.btnHighScoresText)).setTypeface(font);
    }

    private void startGame(int boardSize, int handSize, String mode) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra("boardSize", boardSize);
        intent.putExtra("handSize", handSize);
        intent.putExtra("mode", mode);
        startActivity(intent);
    }
}
