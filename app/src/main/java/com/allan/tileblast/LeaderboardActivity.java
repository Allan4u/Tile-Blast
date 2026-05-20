package com.allan.tileblast;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.allan.tileblast.leaderboard.AuthManager;
import com.allan.tileblast.leaderboard.LeaderboardAdapter;
import com.allan.tileblast.leaderboard.LeaderboardEntry;
import com.allan.tileblast.leaderboard.LeaderboardService;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

/**
 * Displays the global leaderboard with five game-mode tabs and an
 * All-Time / Weekly toggle. Uses a Firestore snapshot listener for live updates.
 */
public class LeaderboardActivity extends AppCompatActivity {

    private static final String[] MODES = {"classic", "chaos", "timed60", "timed90", "daily"};
    private static final String[] MODE_TITLES = {"Classic", "Chaos", "Timed 60", "Timed 90", "Daily"};

    private static final int RC_GOOGLE_SIGN_IN = 9001;

    private LeaderboardService service;
    private AuthManager auth;
    private LeaderboardAdapter adapter;
    private TabLayout tabs;
    private MaterialButtonToggleGroup toggle;
    private RecyclerView list;
    private TextView empty;
    private TextView authStatus;
    private Button authBtn;

    private String currentMode = "classic";
    private boolean showWeekly = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Apply Material theme for this activity (TabLayout + ToggleGroup require it)
        setTheme(R.style.AppTheme_Material);
        setContentView(R.layout.activity_leaderboard);

        service = LeaderboardService.getInstance(this);
        auth = AuthManager.getInstance(this);

        currentMode = service.getLastViewedMode();
        if (!isValidMode(currentMode)) currentMode = "classic";

        // Override from intent extra if provided
        String extraMode = getIntent().getStringExtra("mode");
        if (extraMode != null && isValidMode(extraMode)) {
            currentMode = extraMode;
        }

        list = findViewById(R.id.lbList);
        empty = findViewById(R.id.lbEmpty);
        tabs = findViewById(R.id.lbTabs);
        toggle = findViewById(R.id.lbToggle);
        authStatus = findViewById(R.id.lbAuthStatus);
        authStatus.setOnClickListener(v -> showEditNameDialog());
        authBtn = findViewById(R.id.lbAuthBtn);

        // RecyclerView setup
        adapter = new LeaderboardAdapter();
        adapter.setCurrentUserId(auth.getUserId());
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        // Tabs
        for (int i = 0; i < MODES.length; i++) {
            TabLayout.Tab tab = tabs.newTab().setText(MODE_TITLES[i]).setTag(MODES[i]);
            tabs.addTab(tab);
            if (MODES[i].equals(currentMode)) {
                tab.select();
            }
        }
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                Object tag = tab.getTag();
                if (tag instanceof String) {
                    currentMode = (String) tag;
                    service.setLastViewedMode(currentMode);
                    loadLeaderboard();
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        // Toggle
        Button btnAllTime = findViewById(R.id.lbToggleAllTime);
        Button btnWeekly = findViewById(R.id.lbToggleWeekly);
        toggle.check(btnAllTime.getId());
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            showWeekly = (checkedId == btnWeekly.getId());
            loadLeaderboard();
        });

        // Auth button
        authBtn.setOnClickListener(v -> onAuthButtonClicked());

        // Back button
        findViewById(R.id.lbBackBtn).setOnClickListener(v -> finish());

        updateAuthUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadLeaderboard();
    }

    @Override
    protected void onStop() {
        super.onStop();
        service.detachListener();
    }

    /** Re-attaches the snapshot listener for the currently selected mode + toggle. */
    private void loadLeaderboard() {
        service.detachListener();
        service.attachListener(currentMode, showWeekly, new LeaderboardService.LeaderboardCallback() {
            @Override
            public void onScoresLoaded(List<LeaderboardEntry> entries) {
                adapter.setEntries(entries);
                if (entries.isEmpty()) {
                    list.setVisibility(View.GONE);
                    empty.setVisibility(View.VISIBLE);
                } else {
                    list.setVisibility(View.VISIBLE);
                    empty.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String message) {
                list.setVisibility(View.GONE);
                empty.setVisibility(View.VISIBLE);
                if (message != null
                        && (message.contains("PERMISSION_DENIED") || message.contains("permissions"))) {
                    empty.setText("Leaderboard not available yet.\n"
                            + "Firestore security rules need to be deployed.\n"
                            + "See firebase/README.md.");
                } else {
                    empty.setText(message != null ? "Error: " + message : "Could not load leaderboard");
                }
            }
        });
    }

    /** Updates the auth status text and button label based on current auth state. */
    private void updateAuthUi() {
        String name = auth.getDisplayName();
        if (name == null) name = "";
        if (auth.isAnonymous()) {
            authStatus.setText("Anonymous: " + name + " (tap to edit)");
            authBtn.setText("Sign in with Google");
        } else {
            authStatus.setText("Signed in as " + name + " (tap to edit)");
            authBtn.setText("Sign out");
        }
    }

    private void showEditNameDialog() {
        final EditText input = new EditText(this);
        input.setText(auth.getDisplayName() == null ? "" : auth.getDisplayName());
        input.setSelectAllOnFocus(true);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, pad/2, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Ganti Username")
                .setMessage("3-20 karakter")
                .setView(container)
                .setPositiveButton("Simpan", (dialog, which) -> {
                    String newName = input.getText().toString();
                    if (auth.updateDisplayName(newName)) {
                        updateAuthUi();
                        Toast.makeText(this, "Username diubah", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Username harus 3-20 karakter", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void onAuthButtonClicked() {
        if (auth.isAnonymous()) {
            auth.signInWithGoogle(this, RC_GOOGLE_SIGN_IN);
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Sign out?")
                    .setMessage("Sign out of your Google account?")
                    .setPositiveButton("Sign out", (dialog, which) -> {
                        auth.signOut();
                        // Re-anonymize so the user keeps a valid session
                        auth.signInAnonymously(new AuthManager.AuthCallback() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> {
                                    adapter.setCurrentUserId(auth.getUserId());
                                    updateAuthUi();
                                });
                            }

                            @Override
                            public void onFailure(String errorMessage) {
                                runOnUiThread(() -> {
                                    Toast.makeText(LeaderboardActivity.this,
                                            errorMessage, Toast.LENGTH_LONG).show();
                                    updateAuthUi();
                                });
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            auth.handleGoogleSignInResult(data, new AuthManager.AuthCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        adapter.setCurrentUserId(auth.getUserId());
                        updateAuthUi();
                        Toast.makeText(LeaderboardActivity.this,
                                "Signed in", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onFailure(String errorMessage) {
                    runOnUiThread(() -> {
                        String hint = errorMessage;
                        if (errorMessage != null && errorMessage.contains("code 10")) {
                            hint = "Sign-in gagal (code 10): SHA-1 fingerprint belum "
                                    + "didaftarkan di Firebase Console. "
                                    + "Lihat firebase/GOOGLE_SIGNIN_FIX.md.";
                        } else if (errorMessage != null && errorMessage.contains("code 12500")) {
                            hint = "Sign-in gagal (code 12500): Google provider "
                                    + "belum di-enable di Firebase Console.";
                        }
                        Toast.makeText(LeaderboardActivity.this,
                                hint, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
    }

    private static boolean isValidMode(String mode) {
        return LeaderboardService.isValidMode(mode);
    }
}
