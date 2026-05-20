package com.allan.tileblast;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.allan.tileblast.storage.StorageManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HighScoreActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_highscores);

        Typeface fontBold = ResourcesCompat.getFont(this, R.font.silkscreen_bold);
        Typeface fontReg = ResourcesCompat.getFont(this, R.font.silkscreen);

        TextView title = findViewById(R.id.hsTitle);
        title.setTypeface(fontBold);

        TextView classicLabel = findViewById(R.id.classicLabel);
        classicLabel.setTypeface(fontBold);
        TextView chaosLabel = findViewById(R.id.chaosLabel);
        chaosLabel.setTypeface(fontBold);

        LinearLayout classicList = findViewById(R.id.classicList);
        LinearLayout chaosList = findViewById(R.id.chaosList);

        StorageManager storage = new StorageManager(this);
        populateScores(classicList, storage.getHighScores("classic", 10), fontReg);
        populateScores(chaosList, storage.getHighScores("chaos", 10), fontReg);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.btnBackText)).setTypeface(fontBold);
    }

    private void populateScores(LinearLayout container, List<StorageManager.HighScore> scores, Typeface font) {
        if (scores.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No scores yet");
            tv.setTextColor(0xFF888888);
            tv.setTextSize(14);
            tv.setTypeface(font);
            tv.setPadding(0, 8, 0, 8);
            container.addView(tv);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        int rank = 1;
        for (StorageManager.HighScore hs : scores) {
            TextView tv = new TextView(this);
            String dateStr = sdf.format(new Date(hs.date));
            tv.setText(String.format(Locale.US, "#%d  %,d pts  •  %s", rank, hs.score, dateStr));
            tv.setTextColor(rank == 1 ? 0xFFFFD700 : 0xFFCCCCCC);
            tv.setTextSize(14);
            tv.setTypeface(font);
            tv.setPadding(0, 6, 0, 6);
            container.addView(tv);
            rank++;
        }
    }
}
