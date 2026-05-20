package com.allan.tileblast;

import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.allan.tileblast.stats.AchievementDef;
import com.allan.tileblast.stats.AchievementManager;

public class AchievementActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_achievements);

        Typeface fontBold = ResourcesCompat.getFont(this, R.font.silkscreen_bold);
        Typeface fontReg = ResourcesCompat.getFont(this, R.font.silkscreen);

        ((TextView) findViewById(R.id.achTitle)).setTypeface(fontBold);
        ((TextView) findViewById(R.id.achProgress)).setTypeface(fontReg);
        ((TextView) findViewById(R.id.btnBackText)).setTypeface(fontBold);

        AchievementManager mgr = new AchievementManager(this);

        TextView progress = findViewById(R.id.achProgress);
        progress.setText(mgr.getUnlockedCount() + " / " + AchievementDef.values().length + " Unlocked");

        GridLayout grid = findViewById(R.id.achGrid);
        grid.removeAllViews();

        AchievementDef[] all = mgr.getAllAchievements();
        for (AchievementDef def : all) {
            View badge = buildBadge(def, mgr.isUnlocked(def), fontBold, fontReg);
            grid.addView(badge);
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private View buildBadge(AchievementDef def, boolean unlocked, Typeface fontBold, Typeface fontReg) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setBackgroundResource(unlocked
                ? R.drawable.bg_achievement_unlocked
                : R.drawable.bg_achievement_locked);

        int padding = dpToPx(10);
        container.setPadding(padding, padding, padding, padding);

        // GridLayout cell: span equally across two columns and use minimum height
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        container.setLayoutParams(lp);
        container.setMinimumHeight(dpToPx(110));

        // Name (with checkmark if unlocked)
        TextView name = new TextView(this);
        String namePrefix = unlocked ? "✓ " : "";
        name.setText(namePrefix + def.displayName);
        name.setTypeface(fontBold);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        name.setTextColor(unlocked ? 0xFFFFD700 : 0xFF666666);
        name.setGravity(Gravity.CENTER);
        container.addView(name);

        // Description
        TextView desc = new TextView(this);
        desc.setText(def.description);
        desc.setTypeface(fontReg);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        desc.setTextColor(unlocked ? 0xFFCCCCCC : 0xFF555555);
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dpToPx(6);
        desc.setLayoutParams(descLp);
        container.addView(desc);

        return container;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                Resources.getSystem().getDisplayMetrics());
    }
}
