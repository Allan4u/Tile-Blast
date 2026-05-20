package com.allan.tileblast.game;

import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

import com.allan.tileblast.R;
import com.allan.tileblast.audio.AudioManager;
import com.allan.tileblast.game.effects.EffectManager;
import com.allan.tileblast.game.effects.PieceSnapAnim;
import com.allan.tileblast.progression.LevelManager;
import com.allan.tileblast.progression.Reward;
import com.allan.tileblast.stats.AchievementManager;
import com.allan.tileblast.stats.StatisticsManager;
import com.allan.tileblast.storage.StorageManager;
import com.allan.tileblast.theme.ThemeManager;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class GameView extends View {

    public interface GameCallback {
        void onGameOver(int finalScore);

        void onPauseRequested();

        /**
         * Called after a game ends if the player advanced to one or more new levels.
         * Implementations should display a level-up overlay.
         */
        void onLevelUp(List<Integer> newLevels, List<Reward> newRewards);
    }

    // Game mode
    private int boardSize;
    private int handSize;
    private String modeName;

    // Game state
    private Board board;
    private Hand hand;
    private Random seededRandom; // non-null in deterministic modes (e.g. Daily Challenge)
    private ScoreManager scoreManager;
    private boolean gameOver = false;
    private boolean paused = false;

    // Drag state
    private int draggingIndex = -1;
    private float dragX, dragY;
    private int hoverGridX = -1, hoverGridY = -1;
    private boolean hoverValid = false;
    private int hoverComputedGx = -1, hoverComputedGy = -1;

    // Snap-to-grid feedback
    private int prevSnapGridX = -1, prevSnapGridY = -1;
    private long snapBounceStartTime = 0;
    private boolean firstPickup = true;

    // Hint system
    private int hintsRemaining = 3;
    private RectF hintBtnRect = new RectF();
    private HintCalculator.PlacementResult activeHint = null;
    private long hintDisplayStartTime = 0;
    private static final long HINT_DISPLAY_DURATION = 3000;
    private String noMovesMessage = null;
    private long noMovesMessageTime = 0;

    // Layout
    private int blockSize;
    private int gridLeft, gridTop;
    private int handY;
    private float handBlockSize;
    private RectF[] handRects;

    // Shake
    private float shakeX = 0, shakeY = 0;
    private long shakeEndTime = 0;
    private float shakeIntensity = 0;

    // Combo visual (replaced by ComboTextAnim in EffectManager)

    // Drawing
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Typeface fontRegular, fontBold;

    // Callback
    private GameCallback callback;
    private AudioManager audioManager;
    private StorageManager storageManager;
    private int bestScore = 0;

    // Effects
    private EffectManager effectManager;

    // Power-ups
    private PowerUpManager powerUps;
    private boolean lastPlacementClean = true;

    // Special tiles
    private SpecialTilesManager specialTiles;
    private int turnNumber;

    // Statistics & Achievements
    private StatisticsManager statisticsManager;
    private AchievementManager achievementManager;
    private long sessionStartTime = 0;
    private LinkedList<String> achievementQueue = new LinkedList<>();
    private String currentNotification = null;
    private long notificationEndTime = 0;
    private static final long NOTIFICATION_DURATION_MS = 3000;

    // Timed Mode
    private TimedModeController timer;
    private long inputDeadlineMs = 0L;
    private int timeRemainingBonusForOverlay = 0;

    // Level progression
    private LevelManager levelManager;
    private final ArrayList<Integer> comboLevelsThisGame = new ArrayList<>();
    private int perfectClearCount = 0;
    private boolean xpAwardedThisGame = false;

    // Pause button
    private RectF pauseBtnRect = new RectF();

    // Power-up slot layout
    private RectF[] slotRects = new RectF[4];
    private RectF cancelBtnRect = new RectF();

    // Density for dp-to-px conversion
    private float density;

    public GameView(Context context) { super(context); init(context); }
    public GameView(Context context, AttributeSet attrs) { super(context, attrs); init(context); }

    private void init(Context ctx) {
        fontRegular = ResourcesCompat.getFont(ctx, R.font.silkscreen);
        fontBold = ResourcesCompat.getFont(ctx, R.font.silkscreen_bold);
        audioManager = AudioManager.getInstance(ctx);
        storageManager = new StorageManager(ctx);
        statisticsManager = new StatisticsManager(ctx);
        achievementManager = new AchievementManager(ctx);
        density = getResources().getDisplayMetrics().density;
        effectManager = new EffectManager();
        effectManager.init(density);
        powerUps = new PowerUpManager(new Random());
        levelManager = new LevelManager(storageManager);
        setBackgroundColor(Color.BLACK);
    }

    public void setup(int boardSize, int handSize, String modeName, GameCallback cb) {
        setup(boardSize, handSize, modeName, 0f, cb, null);
    }

    /**
     * Timed-mode aware setup. {@code durationSec > 0} along with a timed mode key
     * (e.g. "timed60", "timed90") constructs a {@link TimedModeController}.
     */
    public void setup(int boardSize, int handSize, String modeName, float durationSec, GameCallback cb) {
        setup(boardSize, handSize, modeName, durationSec, cb, null);
    }

    /**
     * Seeded variant of {@link #setup(int, int, String, GameCallback)}.
     * When {@code seededRandom} is non-null, the hand uses it for piece
     * generation, producing identical piece sequences across devices for the
     * same seed (used by Daily Challenge mode).
     */
    public void setup(int boardSize, int handSize, String modeName, GameCallback cb, Random seededRandom) {
        setup(boardSize, handSize, modeName, 0f, cb, seededRandom);
    }

    /**
     * Master setup overload. All other {@code setup} variants delegate here.
     */
    public void setup(int boardSize, int handSize, String modeName, float durationSec,
                      GameCallback cb, Random seededRandom) {
        this.boardSize = boardSize;
        this.handSize = handSize;
        this.modeName = modeName;
        this.callback = cb;
        this.seededRandom = seededRandom;
        this.board = new Board(boardSize);
        this.hand = (seededRandom != null) ? new Hand(handSize, seededRandom) : new Hand(handSize);
        this.scoreManager = new ScoreManager(boardSize);
        this.gameOver = false;
        this.paused = false;
        this.bestScore = storageManager.getBestScore(modeName);
        this.hintsRemaining = 3;
        this.activeHint = null;
        this.comboLevelsThisGame.clear();
        this.perfectClearCount = 0;
        this.xpAwardedThisGame = false;
        this.inputDeadlineMs = 0L;
        this.timeRemainingBonusForOverlay = 0;
        powerUps.newGameReset();
        sessionStartTime = System.currentTimeMillis();
        achievementQueue.clear();
        currentNotification = null;
        notificationEndTime = 0;
        this.specialTiles = new SpecialTilesManager(board, modeName, (seededRandom != null) ? seededRandom : new Random());
        this.turnNumber = 0;

        // Timed Mode controller — only constructed for timed modes with positive duration.
        if (durationSec > 0f && ("timed60".equals(modeName) || "timed90".equals(modeName))) {
            this.timer = new TimedModeController(modeName, durationSec);
            this.timer.resume(System.currentTimeMillis());
        } else {
            this.timer = null;
        }

        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateLayout(w, h);
    }

    private void calculateLayout(int w, int h) {
        // Grid block size: fit in available space
        int gridPadding = (int)(40 * density);
        int hudHeight = Math.max(160, (int)(56 * density));
        int handHeight = (int)(60 * density);
        int availW = w - gridPadding;
        int availH = h - hudHeight - handHeight - 60;
        blockSize = Math.min(availW / boardSize, availH / boardSize);
        blockSize = Math.min(blockSize, (int)(46 * 3 * density)); // cap (density-aware)

        int gridW = blockSize * boardSize;
        int gridH = blockSize * boardSize;
        gridLeft = (w - gridW) / 2;
        gridTop = hudHeight + 20;

        // Hand area
        handY = gridTop + gridH + (int)(20 * density);
        handBlockSize = blockSize * 0.45f;

        // Calculate hand piece rects
        handRects = new RectF[handSize];
        float totalHandW = 0;
        float[] pieceWidths = new float[handSize];
        for (int i = 0; i < handSize; i++) {
            Piece p = hand.get(i);
            if (p != null) {
                pieceWidths[i] = p.getCols() * handBlockSize + (int)(8 * density);
            } else {
                pieceWidths[i] = 3 * handBlockSize + (int)(8 * density);
            }
            totalHandW += pieceWidths[i];
        }
        float startX = (w - totalHandW) / 2f;
        for (int i = 0; i < handSize; i++) {
            handRects[i] = new RectF(startX, handY, startX + pieceWidths[i], handY + 5 * handBlockSize);
            startX += pieceWidths[i];
        }

        // Pause button (at least 48dp touch target)
        int minPx = (int)(48 * density);
        int btnSize = Math.max(50, minPx);
        pauseBtnRect.set(w - btnSize - 10, 15, w - 10, 15 + btnSize);

        // Power-up slot layout: 4 squares centered horizontally below hand area
        float slotSize = Math.max(48 * density, blockSize);
        float slotSpacing = 8 * density;
        float totalSlotsW = 4 * slotSize + 3 * slotSpacing;
        float slotStartX = (w - totalSlotsW) / 2f;
        float slotTopY = handY + handHeight + (int)(16 * density);
        for (int i = 0; i < 4; i++) {
            float left = slotStartX + i * (slotSize + slotSpacing);
            slotRects[i] = new RectF(left, slotTopY, left + slotSize, slotTopY + slotSize);
        }

        // Cancel button: top-right of grid area, at least 48dp, not colliding with pause button
        int cancelSize = Math.max((int)(48 * density), btnSize);
        float cancelRight = pauseBtnRect.left - 10; // 10px gap left of pause button
        float cancelLeft = cancelRight - cancelSize;
        float cancelTop = 15;
        cancelBtnRect.set(cancelLeft, cancelTop, cancelRight, cancelTop + cancelSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (board == null) return;

        int w = getWidth(), h = getHeight();

        // Tick the timer (Timed Mode) before drawing so HUD reads fresh values.
        if (timer != null && !paused && !gameOver) {
            timer.tick(System.currentTimeMillis());
            if (timer.isExpired()) {
                triggerGameOver();
            }
        }

        // Update all effects (calculates deltaTime internally)
        effectManager.update();

        // Apply shake
        updateShake();
        canvas.save();
        canvas.translate(shakeX, shakeY);

        // Apply zoom pulse transform around grid center
        float gridCenterX = gridLeft + (blockSize * boardSize) / 2f;
        float gridCenterY = gridTop + (blockSize * boardSize) / 2f;
        effectManager.applyZoomTransform(canvas, gridCenterX, gridCenterY);

        drawHUD(canvas, w);
        drawHintButton(canvas, w);
        drawGrid(canvas);
        drawHandPieces(canvas, w);
        drawPowerUpSlots(canvas);

        // Draw targeting overlay and cancel button when in targeting mode
        if (powerUps.isTargeting()) {
            drawTargetingOverlay(canvas);
            drawCancelButton(canvas);
        }

        // Draw piece snap animation (if active) instead of dragging piece
        PieceSnapAnim snap = effectManager.getPieceSnapAnim();
        if (snap.hasPendingPiece()) {
            if (snap.isActive()) {
                Piece snapPiece = snap.getPiece();
                if (snapPiece != null) {
                    drawPieceAt(canvas, snapPiece, snap.getCurrentX(), snap.getCurrentY(), blockSize, 1.0f);
                }
            }
            // Check if snap completed this frame
            if (snap.isComplete()) {
                commitPieceSnap();
            }
        }

        drawDraggingPiece(canvas);
        drawPauseButton(canvas, w);

        canvas.restore();

        // Draw effects above game, below overlays
        effectManager.drawParticles(canvas);
        effectManager.drawScreenFlash(canvas, w, h);
        effectManager.drawScorePops(canvas, fontBold);
        effectManager.drawComboText(canvas, fontBold, w, h);

        // Overlays (no shake)
        if (paused) drawPauseOverlay(canvas, w, h);
        if (gameOver) drawGameOverOverlay(canvas, w, h);

        // Achievement notification banner (above all gameplay, below pause/game-over overlays)
        drawAchievementNotification(canvas, w, h);

        // "No moves" message (shown for 1.5s)
        if (noMovesMessage != null) {
            long msgElapsed = System.currentTimeMillis() - noMovesMessageTime;
            if (msgElapsed < 1500) {
                float alpha = 1.0f - (msgElapsed / 1500f);
                textPaint.setTypeface(fontBold);
                textPaint.setTextSize(20 * density);
                textPaint.setColor(0xFFFF6666);
                textPaint.setAlpha((int)(alpha * 255));
                textPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(noMovesMessage, w / 2f, h / 2f, textPaint);
                textPaint.setAlpha(255);
                invalidate();
            } else {
                noMovesMessage = null;
            }
        }

        // Keep animating if shaking or effects active
        if (System.currentTimeMillis() < shakeEndTime || effectManager.isActive()) invalidate();
        if (currentNotification != null || !achievementQueue.isEmpty()) invalidate();

        // Timed Mode: keep frame loop going so the countdown updates continuously.
        if (timer != null && !paused && !gameOver) invalidate();
    }

    private void drawHUD(Canvas canvas, int w) {
        // Timed Mode: draw countdown timer above the score, push score block down.
        int scoreY = 50;
        if (timer != null) {
            drawCountdown(canvas, w, 50);
            scoreY = 50 + 80; // shift score / best down by 80px
        }

        // Score
        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(36);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("" + scoreManager.getScore(), w / 2f, scoreY, textPaint);

        // Best score
        textPaint.setTypeface(fontRegular);
        textPaint.setTextSize(16);
        textPaint.setColor(Color.GRAY);
        canvas.drawText("BEST: " + bestScore, w / 2f, scoreY + 25, textPaint);

        // Player level (top-left, aligned with score row baseline)
        if (levelManager != null) {
            textPaint.setTypeface(fontBold);
            textPaint.setTextSize(14 * density);
            textPaint.setColor(0xFFFFD700);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("LV." + levelManager.getLevel(), 12, scoreY, textPaint);
        }

        // Combo
        if (scoreManager.getCombo() > 0) {
            textPaint.setTypeface(fontBold);
            textPaint.setTextSize(20);
            textPaint.setColor(0xFFFFD700);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("COMBO x" + scoreManager.getCombo(), w / 2f, scoreY + 50, textPaint);
        }

        // Timed Mode: multiplier label below combo line.
        if (timer != null && timer.scoreMultiplier() > 1.0f) {
            drawMultiplier(canvas, w, scoreY + 75);
        }
    }

    // ======== TIMED MODE HUD ========

    /**
     * Returns the countdown text color for a given remaining-seconds value.
     * Pure helper for property testing.
     */
    public static int countdownColor(float remaining) {
        if (remaining > 30f) return 0xFFFFFFFF;          // white
        if (remaining > 10f) return 0xFFFFD700;          // yellow
        return 0xFFFF3333;                                // red
    }

    /**
     * Returns the countdown pulse scale for a given remaining-seconds value at
     * wall-clock time {@code nowMs}. Pure helper for property testing.
     */
    public static float countdownPulseScale(float remaining, long nowMs) {
        if (remaining > 5f || remaining <= 0f) return 1.0f;
        // 2 Hz pulse between 1.0 and 1.2.
        return 1.0f + 0.2f * (0.5f + 0.5f * (float) Math.sin(2 * Math.PI * 2 * (nowMs / 1000f)));
    }

    /**
     * Returns the multiplier label string ("x1.5", "x2.0", ...) or null when the
     * multiplier is at its baseline of 1.0 or below. Pure helper for property testing.
     */
    public static String multiplierLabel(float multiplier) {
        if (multiplier <= 1.0f) return null;
        return String.format(java.util.Locale.US, "x%.1f", multiplier);
    }

    private void drawCountdown(Canvas canvas, int w, int yTop) {
        if (timer == null) return;
        float remaining = timer.remainingTime();
        int seconds = (int) Math.floor(remaining);
        long now = System.currentTimeMillis();

        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(56);
        textPaint.setColor(countdownColor(remaining));
        textPaint.setTextAlign(Paint.Align.CENTER);

        float cx = w / 2f;
        float cy = yTop;
        float pulseScale = countdownPulseScale(remaining, now);

        canvas.save();
        if (pulseScale != 1.0f) {
            canvas.scale(pulseScale, pulseScale, cx, cy);
        }
        canvas.drawText(String.valueOf(seconds), cx, cy, textPaint);
        canvas.restore();

        // "+1.5s" flash indicator just below the countdown for the flash window.
        if (timer.isTimeBonusFlashActive(now)) {
            textPaint.setTypeface(fontBold);
            textPaint.setTextSize(20);
            textPaint.setColor(0xFF00FFAA);
            canvas.drawText("+1.5s", cx, cy + 30, textPaint);
        }
    }

    private void drawMultiplier(Canvas canvas, int w, int y) {
        if (timer == null) return;
        String label = multiplierLabel(timer.scoreMultiplier());
        if (label == null) return;
        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(20);
        textPaint.setColor(0xFFFFD700);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, w / 2f, y, textPaint);
    }

    private void drawGrid(Canvas canvas) {
        int gridW = blockSize * boardSize;
        int gridH = blockSize * boardSize;

        // Grid border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        if (draggingIndex >= 0 && hand.get(draggingIndex) != null) {
            paint.setColor(hand.get(draggingIndex).getColor());
        } else {
            paint.setColor(Color.WHITE);
        }
        canvas.drawRoundRect(gridLeft - 4, gridTop - 4, gridLeft + gridW + 4, gridTop + gridH + 4, 8, 8, paint);

        // Grid background — use active skin (procedurally drawn). Falls back to
        // solid black on exception so a misbehaving skin can't crash gameplay.
        paint.setStyle(Paint.Style.FILL);
        RectF gridRect = new RectF(gridLeft, gridTop, gridLeft + gridW, gridTop + gridH);
        try {
            ThemeManager.getInstance(getContext())
                    .getActiveSkin()
                    .drawBackground(canvas, gridRect, blockSize, paint);
        } catch (Throwable t) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.BLACK);
            canvas.drawRect(gridLeft, gridTop, gridLeft + gridW, gridTop + gridH, paint);
        }
        // Skins may set a shader; reset before drawing cells.
        paint.setShader(null);

        // Draw cells
        for (int y = 0; y < boardSize; y++) {
            for (int x = 0; x < boardSize; x++) {
                int cx = gridLeft + x * blockSize;
                int cy = gridTop + y * blockSize;
                int cell = board.getCell(x, y);
                int colorIdx = board.getCellColor(x, y);

                if (cell == Board.FILLED || cell == Board.HOVERED_BREAK_FILLED) {
                    int ci = (cell == Board.HOVERED_BREAK_FILLED) ? board.getHoverColor(x, y) : colorIdx;
                    drawBeveledBlock(canvas, cx, cy, blockSize, ci, 1f);
                } else if (cell == Board.HOVERED) {
                    drawGhostBlock(canvas, cx, cy, blockSize, colorIdx);
                } else if (cell == Board.HOVERED_BREAK_EMPTY) {
                    int ci = board.getHoverColor(x, y);
                    drawGhostBlock(canvas, cx, cy, blockSize, ci);
                } else if (Board.isSpecial(cell)) {
                    drawSpecialCell(canvas, cx, cy, blockSize, x, y, cell);
                } else {
                    // Empty cell border
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(0.5f);
                    paint.setColor(0xFF282828);
                    canvas.drawRect(cx, cy, cx + blockSize, cy + blockSize, paint);
                }
            }
        }

        // Draw placement indicator overlay
        if (draggingIndex >= 0) {
            Piece piece = hand.get(draggingIndex);
            if (piece != null && hoverComputedGx >= 0 && hoverComputedGy >= 0) {
                drawPlacementIndicator(canvas, piece, hoverComputedGx, hoverComputedGy, hoverValid);
            }
        }

        // Draw line-break highlight
        drawLineBreakHighlight(canvas);

        // Draw hint indicator
        drawHintIndicator(canvas);

        // Keep animating during hover for pulse effects
        if (draggingIndex >= 0 || activeHint != null) invalidate();
    }

    private void drawBeveledBlock(Canvas canvas, int x, int y, int size, int colorIdx, float alpha) {
        int a = (int)(alpha * 255);
        int bw = Math.max(size / 6, 2); // border width

        // Fill
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Piece.getColorFromIndex(colorIdx));
        paint.setAlpha(a);
        canvas.drawRect(x, y, x + size, y + size, paint);

        // Top border (light)
        paint.setColor(Piece.getTopBorder(colorIdx));
        paint.setAlpha(a);
        canvas.drawRect(x, y, x + size, y + bw, paint);

        // Left border
        paint.setColor(Piece.getLeftBorder(colorIdx));
        paint.setAlpha(a);
        canvas.drawRect(x, y, x + bw, y + size, paint);

        // Right border (dark)
        paint.setColor(Piece.getRightBorder(colorIdx));
        paint.setAlpha(a);
        canvas.drawRect(x + size - bw, y, x + size, y + size, paint);

        // Bottom border (darkest)
        paint.setColor(Piece.getBottomBorder(colorIdx));
        paint.setAlpha(a);
        canvas.drawRect(x, y + size - bw, x + size, y + size, paint);

        paint.setAlpha(255);
    }

    // ======== SPECIAL TILE RENDERING ========

    /**
     * Draws a special-tile cell (FROZEN, FROZEN_CRACKED, LOCKED, BOMB_TILE) procedurally
     * using {@link Canvas} primitives. Called from {@link #drawGrid} for any cell where
     * {@link Board#isSpecial(int)} is true.
     */
    private void drawSpecialCell(Canvas canvas, int cx, int cy, int blockSize, int x, int y, int cellState) {
        switch (cellState) {
            case Board.FROZEN:
                drawFrozenBase(canvas, cx, cy, blockSize);
                break;
            case Board.FROZEN_CRACKED:
                drawFrozenBase(canvas, cx, cy, blockSize);
                drawFrozenCracks(canvas, cx, cy, blockSize);
                break;
            case Board.LOCKED:
                drawLockedTile(canvas, cx, cy, blockSize);
                break;
            case Board.BOMB_TILE:
                drawBombTile(canvas, cx, cy, blockSize, x, y);
                break;
        }
    }

    private void drawFrozenBase(Canvas canvas, int cx, int cy, int blockSize) {
        // Base ice fill
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(160, 220, 255));
        paint.setAlpha(255);
        canvas.drawRect(cx, cy, cx + blockSize, cy + blockSize, paint);

        // Lighter top-left wedge highlight
        paint.setColor(Color.rgb(210, 240, 255));
        Path wedge = new Path();
        wedge.moveTo(cx, cy);
        wedge.lineTo(cx + blockSize, cy);
        wedge.lineTo(cx, cy + blockSize);
        wedge.close();
        canvas.drawPath(wedge, paint);

        // Subtle border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, blockSize / 24f));
        paint.setColor(Color.rgb(100, 170, 220));
        canvas.drawRect(cx, cy, cx + blockSize, cy + blockSize, paint);
    }

    private void drawFrozenCracks(Canvas canvas, int cx, int cy, int blockSize) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, blockSize / 14f));
        paint.setColor(Color.WHITE);
        paint.setStrokeCap(Paint.Cap.ROUND);

        // Crack 1: jagged diagonal across the cell
        Path c1 = new Path();
        c1.moveTo(cx + blockSize * 0.15f, cy + blockSize * 0.20f);
        c1.lineTo(cx + blockSize * 0.40f, cy + blockSize * 0.45f);
        c1.lineTo(cx + blockSize * 0.30f, cy + blockSize * 0.65f);
        c1.lineTo(cx + blockSize * 0.55f, cy + blockSize * 0.85f);
        canvas.drawPath(c1, paint);

        // Crack 2: shorter branch
        Path c2 = new Path();
        c2.moveTo(cx + blockSize * 0.40f, cy + blockSize * 0.45f);
        c2.lineTo(cx + blockSize * 0.65f, cy + blockSize * 0.30f);
        c2.lineTo(cx + blockSize * 0.85f, cy + blockSize * 0.40f);
        canvas.drawPath(c2, paint);

        // Crack 3: small branch
        Path c3 = new Path();
        c3.moveTo(cx + blockSize * 0.30f, cy + blockSize * 0.65f);
        c3.lineTo(cx + blockSize * 0.10f, cy + blockSize * 0.80f);
        canvas.drawPath(c3, paint);
    }

    private void drawLockedTile(Canvas canvas, int cx, int cy, int blockSize) {
        // Dark gray fill
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(64, 64, 64));
        paint.setAlpha(255);
        canvas.drawRect(cx, cy, cx + blockSize, cy + blockSize, paint);

        // Subtle border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, blockSize / 24f));
        paint.setColor(Color.rgb(100, 100, 100));
        canvas.drawRect(cx, cy, cx + blockSize, cy + blockSize, paint);

        // Chain icon overlay: two interlocking rounded rects
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, blockSize / 10f));
        paint.setColor(Color.WHITE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float linkSize = blockSize * 0.30f;
        float r = linkSize * 0.5f;
        // Upper-left link
        float l1L = cx + blockSize * 0.18f;
        float l1T = cy + blockSize * 0.18f;
        canvas.drawRoundRect(l1L, l1T, l1L + linkSize, l1T + linkSize, r, r, paint);
        // Lower-right link (overlapping)
        float l2L = cx + blockSize * 0.45f;
        float l2T = cy + blockSize * 0.45f;
        canvas.drawRoundRect(l2L, l2T, l2L + linkSize, l2T + linkSize, r, r, paint);
    }

    private void drawBombTile(Canvas canvas, int cx, int cy, int blockSize, int x, int y) {
        // Red fill
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(200, 30, 30));
        paint.setAlpha(255);
        canvas.drawRect(cx, cy, cx + blockSize, cy + blockSize, paint);

        // Subtle border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, blockSize / 24f));
        paint.setColor(Color.rgb(140, 20, 20));
        canvas.drawRect(cx, cy, cx + blockSize, cy + blockSize, paint);

        // Countdown digit
        int countdown = board.getBombCountdown(x, y);
        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(blockSize * 0.55f);
        textPaint.setColor(Color.WHITE);
        textPaint.setAlpha(255);
        textPaint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = cy + blockSize / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(String.valueOf(countdown), cx + blockSize / 2f, baseline, textPaint);
    }

    // ======== GHOST PREVIEW & HINT RENDERING ========

    /**
     * Compute a pulsing alpha value using a sine wave.
     * @param periodMs period in milliseconds
     * @param min minimum alpha value
     * @param max maximum alpha value
     * @return alpha in [min, max]
     */
    private float computePulseAlpha(int periodMs, float min, float max) {
        if (periodMs <= 0) return min;
        float t = (System.currentTimeMillis() % periodMs) / (float) periodMs;
        return min + (max - min) * (0.5f + 0.5f * (float) Math.sin(2 * Math.PI * t));
    }

    /**
     * Draw a ghost block with pulsing alpha fill + white outline + optional snap bounce.
     */
    private void drawGhostBlock(Canvas canvas, int cx, int cy, int blockSize, int colorIdx) {
        float pulse = computePulseAlpha(600, 0.5f, 0.7f);

        // Compute snap bounce scale
        float scale = 1.0f;
        long bounceElapsed = System.currentTimeMillis() - snapBounceStartTime;
        if (bounceElapsed >= 0 && bounceElapsed < 120) {
            float t = bounceElapsed / 120f;
            scale = 1.05f - 0.05f * t; // 105% -> 100%
        }

        int scaledSize = (int)(blockSize * scale);
        int offset = (scaledSize - blockSize) / 2;
        int sx = cx - offset;
        int sy = cy - offset;

        // Fill with piece color at pulsing alpha
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Piece.getColorFromIndex(colorIdx));
        paint.setAlpha((int)(pulse * 255));
        canvas.drawRect(sx, sy, sx + scaledSize, sy + scaledSize, paint);

        // White outline (2dp)
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        paint.setColor(Color.WHITE);
        paint.setAlpha((int)(pulse * 255));
        canvas.drawRect(sx, sy, sx + scaledSize, sy + scaledSize, paint);
        paint.setAlpha(255);
    }

    /**
     * Draw green/red placement indicator overlay.
     */
    private void drawPlacementIndicator(Canvas canvas, Piece piece, int gx, int gy, boolean valid) {
        int tintColor = valid ? 0x6600FF00 : 0x66FF0000; // 40% alpha green or red
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tintColor);
        for (int dy = 0; dy < piece.getRows(); dy++) {
            for (int dx = 0; dx < piece.getCols(); dx++) {
                if (piece.matrix[dy][dx] == 1) {
                    int bx = gx + dx, by = gy + dy;
                    if (bx >= 0 && bx < boardSize && by >= 0 && by < boardSize) {
                        int cx2 = gridLeft + bx * blockSize;
                        int cy2 = gridTop + by * blockSize;
                        canvas.drawRect(cx2, cy2, cx2 + blockSize, cy2 + blockSize, paint);
                    }
                }
            }
        }
    }

    /**
     * Draw gold pulsing border on cells that would complete lines.
     */
    private void drawLineBreakHighlight(Canvas canvas) {
        boolean hasBreak = false;
        float pulse = computePulseAlpha(800, 0.6f, 1.0f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3 * density);
        paint.setColor(0xFFFFD700); // Gold
        paint.setAlpha((int)(pulse * 255));
        for (int y = 0; y < boardSize; y++) {
            for (int x = 0; x < boardSize; x++) {
                int cell = board.getCell(x, y);
                if (cell == Board.HOVERED_BREAK_FILLED || cell == Board.HOVERED_BREAK_EMPTY) {
                    int cx2 = gridLeft + x * blockSize;
                    int cy2 = gridTop + y * blockSize;
                    canvas.drawRect(cx2, cy2, cx2 + blockSize, cy2 + blockSize, paint);
                    hasBreak = true;
                }
            }
        }
        paint.setAlpha(255);
    }

    /**
     * Draw the hint indicator (pulsing teal outline) for 3 seconds.
     */
    private void drawHintIndicator(Canvas canvas) {
        if (activeHint == null) return;
        long elapsed = System.currentTimeMillis() - hintDisplayStartTime;
        if (elapsed > HINT_DISPLAY_DURATION) { activeHint = null; return; }

        Piece piece = hand.get(activeHint.pieceIndex);
        if (piece == null) { activeHint = null; return; }

        float pulse = computePulseAlpha(800, 0.4f, 1.0f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3 * density);
        paint.setColor(0xFF00FFAA); // Bright teal
        paint.setAlpha((int)(pulse * 255));

        for (int dy = 0; dy < piece.getRows(); dy++) {
            for (int dx = 0; dx < piece.getCols(); dx++) {
                if (piece.matrix[dy][dx] == 1) {
                    int cx2 = gridLeft + (activeHint.gridX + dx) * blockSize;
                    int cy2 = gridTop + (activeHint.gridY + dy) * blockSize;
                    canvas.drawRect(cx2, cy2, cx2 + blockSize, cy2 + blockSize, paint);
                }
            }
        }
        paint.setAlpha(255);
        invalidate(); // Keep animating
    }

    /**
     * Draw the hint button in the HUD area (top-left).
     */
    private void drawHintButton(Canvas canvas, int w) {
        int btnW = (int)(80 * density);
        int btnH = (int)(36 * density);
        float left = 10;
        float top = 15;
        hintBtnRect.set(left, top, left + btnW, top + btnH);

        boolean enabled = hintsRemaining > 0 && !gameOver && !paused;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(enabled ? 0xFF00AAFF : 0xFF444444);
        canvas.drawRoundRect(hintBtnRect, 8, 8, paint);

        // Border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(enabled ? 0xFF0088CC : 0xFF333333);
        canvas.drawRoundRect(hintBtnRect, 8, 8, paint);

        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(14 * density);
        textPaint.setColor(enabled ? Color.WHITE : Color.GRAY);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("HINT (" + hintsRemaining + ")", hintBtnRect.centerX(), hintBtnRect.centerY() + 5 * density, textPaint);
    }

    // ======== SNAP DETECTION ========

    /**
     * Check if the grid position changed and trigger snap feedback.
     */
    private void checkSnap(int newGx, int newGy) {
        if (firstPickup) {
            firstPickup = false;
            prevSnapGridX = newGx;
            prevSnapGridY = newGy;
            return; // No feedback on first pickup
        }
        if (newGx != prevSnapGridX || newGy != prevSnapGridY) {
            if (newGx >= 0 && newGy >= 0) { // valid position
                vibrateSnap(15);
                snapBounceStartTime = System.currentTimeMillis();
            }
            prevSnapGridX = newGx;
            prevSnapGridY = newGy;
        }
    }

    /**
     * Short haptic pulse for snap feedback.
     */
    private void vibrateSnap(int ms) {
        try {
            Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(ms);
                }
            }
        } catch (Exception e) { /* ignore */ }
    }

    private void drawHandPieces(Canvas canvas, int w) {
        if (handRects == null) return;
        calculateHandRects(w);
        for (int i = 0; i < handSize; i++) {
            if (i == draggingIndex) continue; // skip dragging one
            Piece piece = hand.get(i);
            if (piece == null) continue;
            float px = handRects[i].left + 10;
            float py = handRects[i].top + 10;
            drawPieceAt(canvas, piece, px, py, handBlockSize, 0.8f);
        }
    }

    private void calculateHandRects(int w) {
        float totalHandW = 0;
        float[] pieceWidths = new float[handSize];
        for (int i = 0; i < handSize; i++) {
            Piece p = hand.get(i);
            if (p != null) {
                pieceWidths[i] = p.getCols() * handBlockSize + (int)(8 * density);
            } else {
                pieceWidths[i] = 3 * handBlockSize + (int)(8 * density);
            }
            totalHandW += pieceWidths[i];
        }
        float startX = (w - totalHandW) / 2f;
        for (int i = 0; i < handSize; i++) {
            Piece p = hand.get(i);
            float ph = (p != null ? p.getRows() : 3) * handBlockSize + (int)(8 * density);
            handRects[i] = new RectF(startX, handY, startX + pieceWidths[i], handY + ph);
            startX += pieceWidths[i];
        }
    }

    private void drawPieceAt(Canvas canvas, Piece piece, float px, float py, float bs, float alpha) {
        for (int dy = 0; dy < piece.getRows(); dy++) {
            for (int dx = 0; dx < piece.getCols(); dx++) {
                if (piece.matrix[dy][dx] == 1) {
                    int bx = (int)(px + dx * bs);
                    int by = (int)(py + dy * bs);
                    drawBeveledBlock(canvas, bx, by, (int)bs, piece.colorIndex, alpha);
                }
            }
        }
    }

    private PowerUpManager.GameContext buildGameContext() {
        return new PowerUpManager.GameContext(paused, gameOver, draggingIndex >= 0,
                powerUps.hasSnapshot(), lastPlacementClean);
    }

    private void drawPowerUpSlots(Canvas canvas) {
        if (slotRects[0] == null) return;
        PowerUpType[] types = PowerUpType.values();
        PowerUpManager.GameContext ctx = buildGameContext();

        for (int i = 0; i < types.length; i++) {
            PowerUpType type = types[i];
            RectF rect = slotRects[i];
            int count = powerUps.getCount(type);

            // Determine if this slot should be dimmed (40% alpha)
            boolean dimmed = count == 0
                    || (type == PowerUpType.UNDO && !powerUps.isUndoEnabled(ctx))
                    || (type == PowerUpType.ROTATE && draggingIndex < 0);

            int saveCount = canvas.save();
            if (dimmed) {
                paint.setAlpha(102); // 40% of 255
            }

            // Draw rounded-rect background
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF1A1A2E);
            if (dimmed) paint.setAlpha(102);
            canvas.drawRoundRect(rect, 12, 12, paint);

            // Draw active highlight border (3px gold) when this type is active
            if (powerUps.getActiveType() == type) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3 * density);
                paint.setColor(0xFFFFD700);
                if (dimmed) paint.setAlpha(102);
                canvas.drawRoundRect(rect, 12, 12, paint);
            }

            // Draw type-distinct icon
            float cx = rect.centerX();
            float cy = rect.centerY();
            float iconSize = Math.min(rect.width(), rect.height()) * 0.35f;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f * density);
            paint.setStrokeCap(Paint.Cap.ROUND);

            switch (type) {
                case BOMB:
                    // Bomb: circle + fuse line
                    paint.setColor(0xFFFF4444);
                    if (dimmed) paint.setAlpha(102);
                    canvas.drawCircle(cx, cy + iconSize * 0.15f, iconSize, paint);
                    // Fuse
                    paint.setColor(0xFFFFAA00);
                    if (dimmed) paint.setAlpha(102);
                    canvas.drawLine(cx, cy - iconSize * 0.85f, cx + iconSize * 0.4f, cy - iconSize * 1.2f, paint);
                    break;

                case LINE_SWEEP:
                    // Sweep: vertical bar + arrow pointing right
                    paint.setColor(0xFF44AAFF);
                    if (dimmed) paint.setAlpha(102);
                    // Vertical bar
                    canvas.drawLine(cx - iconSize * 0.6f, cy - iconSize, cx - iconSize * 0.6f, cy + iconSize, paint);
                    // Arrow shaft
                    canvas.drawLine(cx - iconSize * 0.3f, cy, cx + iconSize * 0.8f, cy, paint);
                    // Arrow head
                    canvas.drawLine(cx + iconSize * 0.8f, cy, cx + iconSize * 0.4f, cy - iconSize * 0.4f, paint);
                    canvas.drawLine(cx + iconSize * 0.8f, cy, cx + iconSize * 0.4f, cy + iconSize * 0.4f, paint);
                    break;

                case ROTATE:
                    // Rotate: curved arrow (arc + arrowhead)
                    paint.setColor(0xFF44FF88);
                    if (dimmed) paint.setAlpha(102);
                    RectF arcRect = new RectF(cx - iconSize, cy - iconSize, cx + iconSize, cy + iconSize);
                    canvas.drawArc(arcRect, -60, 240, false, paint);
                    // Arrowhead at end of arc
                    float endAngle = (float) Math.toRadians(180);
                    float ax = cx + iconSize * (float) Math.cos(endAngle);
                    float ay = cy + iconSize * (float) Math.sin(endAngle);
                    canvas.drawLine(ax, ay, ax + iconSize * 0.4f, ay - iconSize * 0.3f, paint);
                    canvas.drawLine(ax, ay, ax + iconSize * 0.4f, ay + iconSize * 0.3f, paint);
                    break;

                case UNDO:
                    // Undo: left-curved arrow
                    paint.setColor(0xFFFFFF44);
                    if (dimmed) paint.setAlpha(102);
                    RectF undoArc = new RectF(cx - iconSize, cy - iconSize, cx + iconSize, cy + iconSize);
                    canvas.drawArc(undoArc, 120, -240, false, paint);
                    // Arrowhead pointing left
                    float undoEndAngle = (float) Math.toRadians(120);
                    float ux = cx + iconSize * (float) Math.cos(undoEndAngle);
                    float uy = cy + iconSize * (float) Math.sin(undoEndAngle);
                    canvas.drawLine(ux, uy, ux - iconSize * 0.1f, uy - iconSize * 0.5f, paint);
                    canvas.drawLine(ux, uy, ux + iconSize * 0.5f, uy - iconSize * 0.1f, paint);
                    break;
            }

            // Draw count badge (small filled circle in top-right with count digit)
            float badgeRadius = 8 * density;
            float badgeX = rect.right - badgeRadius - 4 * density;
            float badgeY = rect.top + badgeRadius + 4 * density;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF333355);
            if (dimmed) paint.setAlpha(102);
            canvas.drawCircle(badgeX, badgeY, badgeRadius, paint);

            textPaint.setTypeface(fontBold);
            textPaint.setTextSize(10 * density);
            textPaint.setColor(Color.WHITE);
            if (dimmed) textPaint.setAlpha(102);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.valueOf(count), badgeX, badgeY + 4 * density, textPaint);

            // Restore alpha
            paint.setAlpha(255);
            textPaint.setAlpha(255);
            canvas.restoreToCount(saveCount);
        }
    }

    /**
     * Draws the targeting overlay on the grid when a power-up is in TARGETING mode.
     * For BOMB: renders a 50%-alpha white fill plus 3px white border on the 3x3 area
     * (clamped to grid bounds) centered at the target cursor.
     * For LINE_SWEEP: renders a translucent fill on the chosen row or column.
     */
    private void drawTargetingOverlay(Canvas canvas) {
        int tx = powerUps.getTargetGridX();
        int ty = powerUps.getTargetGridY();
        if (tx < 0 || ty < 0 || tx >= boardSize || ty >= boardSize) return;

        PowerUpType activeType = powerUps.getActiveType();
        if (activeType == null) return;

        if (activeType == PowerUpType.BOMB) {
            // Clamp the 3x3 area to grid bounds
            int minX = Math.max(0, tx - 1);
            int maxX = Math.min(boardSize - 1, tx + 1);
            int minY = Math.max(0, ty - 1);
            int maxY = Math.min(boardSize - 1, ty + 1);

            // Draw 50%-alpha white fill on each cell in the clamped 3x3 area
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setAlpha(128); // 50% alpha
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    int cx = gridLeft + x * blockSize;
                    int cy = gridTop + y * blockSize;
                    canvas.drawRect(cx, cy, cx + blockSize, cy + blockSize, paint);
                }
            }

            // Draw 3px white border around the entire clamped area
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setColor(Color.WHITE);
            paint.setAlpha(255);
            int left = gridLeft + minX * blockSize;
            int top = gridTop + minY * blockSize;
            int right = gridLeft + (maxX + 1) * blockSize;
            int bottom = gridTop + (maxY + 1) * blockSize;
            canvas.drawRect(left, top, right, bottom, paint);
            paint.setAlpha(255);

        } else if (activeType == PowerUpType.LINE_SWEEP) {
            boolean isRow = powerUps.isTargetingRow();

            // Draw translucent fill on the chosen row or column
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setAlpha(80); // translucent fill

            if (isRow) {
                // Fill the entire row
                for (int x = 0; x < boardSize; x++) {
                    int cx = gridLeft + x * blockSize;
                    int cy = gridTop + ty * blockSize;
                    canvas.drawRect(cx, cy, cx + blockSize, cy + blockSize, paint);
                }
            } else {
                // Fill the entire column
                for (int y = 0; y < boardSize; y++) {
                    int cx = gridLeft + tx * blockSize;
                    int cy = gridTop + y * blockSize;
                    canvas.drawRect(cx, cy, cx + blockSize, cy + blockSize, paint);
                }
            }
            paint.setAlpha(255);
        }

        invalidate(); // Keep redrawing while targeting
    }

    /**
     * Draws the cancel button (red rounded rect with "X" glyph) in cancelBtnRect.
     * Shown only during targeting mode.
     */
    private void drawCancelButton(Canvas canvas) {
        // Red rounded rect background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFCC3333);
        canvas.drawRoundRect(cancelBtnRect, 12, 12, paint);

        // Border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(0xFFFF4444);
        canvas.drawRoundRect(cancelBtnRect, 12, 12, paint);

        // "X" glyph centered in the button
        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(22 * density);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("X", cancelBtnRect.centerX(),
                cancelBtnRect.centerY() + 8 * density, textPaint);
    }

    private void drawDraggingPiece(Canvas canvas) {
        if (draggingIndex < 0) return;
        Piece piece = hand.get(draggingIndex);
        if (piece == null) return;

        float bs = blockSize; // full size when dragging
        float px = dragX - (piece.getCols() * bs) / 2f;
        float py = dragY - (piece.getRows() * bs) - (int)(40 * density); // offset above finger

        drawPieceAt(canvas, piece, px, py, bs, 0.85f);
    }

    private void drawPauseButton(Canvas canvas, int w) {
        int minPx = (int)(48 * density);
        int btnSize = Math.max(50, minPx);
        pauseBtnRect.set(w - btnSize - 10, 15, w - 10, 15 + btnSize);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x1AFFFFFF);
        canvas.drawRoundRect(pauseBtnRect, 12, 12, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(pauseBtnRect, 12, 12, paint);

        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(22);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("II", pauseBtnRect.centerX(), pauseBtnRect.centerY() + 8, textPaint);
    }

    private void drawPauseOverlay(Canvas canvas, int w, int h) {
        // Dim background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xB3000000);
        canvas.drawRect(0, 0, w, h, paint);

        // Container
        float cw = Math.min(w * 0.85f, 400);
        float ch = 350;
        float cx = (w - cw) / 2f;
        float cy = (h - ch) / 2f;
        paint.setColor(0xFA0A0A0A);
        canvas.drawRoundRect(cx, cy, cx + cw, cy + ch, 24, 24, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(0x4D969696);
        canvas.drawRoundRect(cx, cy, cx + cw, cy + ch, 24, 24, paint);

        // Title
        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(42);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("PAUSED", w / 2f, cy + 70, textPaint);

        // Buttons
        drawButton(canvas, w / 2f, cy + 140, "Resume", 0xFF00FF00);
        drawButton(canvas, w / 2f, cy + 210, "Quit", 0xFFFF3333);
    }

    private void drawGameOverOverlay(Canvas canvas, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xB3000000);
        canvas.drawRect(0, 0, w, h, paint);

        float cw = Math.min(w * 0.85f, 400);
        float ch = 300;
        float cx = (w - cw) / 2f;
        float cy = (h - ch) / 2f;
        paint.setColor(0xFA0A0A0A);
        canvas.drawRoundRect(cx, cy, cx + cw, cy + ch, 24, 24, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(0x4D969696);
        canvas.drawRoundRect(cx, cy, cx + cw, cy + ch, 24, 24, paint);

        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(42);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("GAME OVER", w / 2f, cy + 70, textPaint);

        if (timer != null && timeRemainingBonusForOverlay > 0) {
            // Timed Mode breakdown: Score (base), Time Bonus, Final.
            int finalScore = scoreManager.getScore();
            int baseScore = finalScore - timeRemainingBonusForOverlay;

            textPaint.setTypeface(fontRegular);
            textPaint.setTextSize(22);
            textPaint.setColor(0xFFCCCCCC);
            canvas.drawText("Score: " + baseScore, w / 2f, cy + 115, textPaint);

            textPaint.setColor(0xFF00FFAA);
            canvas.drawText("Time Bonus: +" + timeRemainingBonusForOverlay, w / 2f, cy + 145, textPaint);

            textPaint.setTypeface(fontBold);
            textPaint.setTextSize(26);
            textPaint.setColor(0xFFFFD700);
            canvas.drawText("Final: " + finalScore, w / 2f, cy + 180, textPaint);
        } else {
            textPaint.setTypeface(fontRegular);
            textPaint.setTextSize(26);
            textPaint.setColor(0xFFFFD700);
            canvas.drawText("Score: " + scoreManager.getScore(), w / 2f, cy + 120, textPaint);
        }

        drawButton(canvas, w / 2f, cy + 200, "Back to Menu", 0xFFFF3333);
    }

    private void drawButton(Canvas canvas, float cx, float y, String text, int color) {
        float bw = 220, bh = 50;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        RectF r = new RectF(cx - bw / 2, y - bh / 2, cx + bw / 2, y + bh / 2);
        canvas.drawRoundRect(r, 12, 12, paint);

        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(22);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, cx, y + 8, textPaint);
    }

    // ======== TOUCH HANDLING ========

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float tx = event.getX();
        float ty = event.getY();

        // ─── Power-up targeting intercept (works even during gameOver) ────────────
        if (powerUps.isTargeting() && event.getAction() == MotionEvent.ACTION_DOWN) {
            if (cancelBtnRect.contains(tx, ty)) {
                powerUps.cancel();
                invalidate();
                return true;
            }
            // Check if tap is inside the grid
            int gridW = blockSize * boardSize;
            int gridH = blockSize * boardSize;
            if (tx >= gridLeft && tx < gridLeft + gridW && ty >= gridTop && ty < gridTop + gridH) {
                // Store the targeting cell - actual apply happens on ACTION_UP (task 10.3)
                int cellX = (int) ((tx - gridLeft) / blockSize);
                int cellY = (int) ((ty - gridTop) / blockSize);
                cellX = Math.max(0, Math.min(boardSize - 1, cellX));
                cellY = Math.max(0, Math.min(boardSize - 1, cellY));
                float fracX = ((tx - gridLeft) - cellX * blockSize) / blockSize;
                float fracY = ((ty - gridTop) - cellY * blockSize) / blockSize;
                powerUps.updateTargetCursor(cellX, cellY, fracX, fracY);
                invalidate();
                return true;
            }
            // Tap elsewhere while targeting: ignore (Requirement 6.3)
            return true;
        }

        // ─── Power-up slot activation intercept (works even during gameOver for BOMB/LINE_SWEEP) ──
        if (!powerUps.isTargeting() && event.getAction() == MotionEvent.ACTION_DOWN) {
            for (int i = 0; i < slotRects.length; i++) {
                if (slotRects[i] != null && slotRects[i].contains(tx, ty)) {
                    PowerUpType type = PowerUpType.values()[i];
                    PowerUpManager.GameContext ctx = buildGameContext();
                    boolean activated = powerUps.activate(type, ctx);
                    if (activated) {
                        if (type == PowerUpType.ROTATE) {
                            // Immediate apply: rotate the dragged piece
                            Piece rotated = powerUps.applyRotate(hand.get(draggingIndex));
                            hand.getAll()[draggingIndex] = rotated;
                            audioManager.playPowerUp(PowerUpType.ROTATE);
                            calculateLayout(getWidth(), getHeight());
                            invalidate();
                        } else if (type == PowerUpType.UNDO) {
                            // Immediate apply: undo last placement
                            powerUps.applyUndo(board, hand, scoreManager);
                            gameOver = !board.canPlaceAny(hand.getAll());
                            audioManager.playPowerUp(PowerUpType.UNDO);
                            calculateLayout(getWidth(), getHeight());
                            invalidate();
                        } else {
                            // BOMB or LINE_SWEEP entered TARGETING mode
                            invalidate();
                        }
                    }
                    return true;
                }
            }
        }

        if (gameOver) {
            // 100ms grace window: allow in-flight placement input that arrived just before
            // the timer-driven game-over to complete (Requirement 5.3 / Property 8).
            if (inputDeadlineMs > 0 && System.currentTimeMillis() < inputDeadlineMs
                    && draggingIndex >= 0 && event.getAction() == MotionEvent.ACTION_UP) {
                attemptPlacement();
                draggingIndex = -1;
                board.clearHover();
                hoverGridX = -1;
                hoverGridY = -1;
                hoverValid = false;
                hoverComputedGx = -1;
                hoverComputedGy = -1;
                firstPickup = true;
                prevSnapGridX = -1;
                prevSnapGridY = -1;
                invalidate();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) handleGameOverTouch(tx, ty);
            return true;
        }
        if (paused) {
            if (event.getAction() == MotionEvent.ACTION_UP) handlePauseTouch(tx, ty);
            return true;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Check hint button
                if (hintBtnRect.contains(tx, ty)) {
                    handleHintTap();
                    return true;
                }
                // Check pause button
                if (pauseBtnRect.contains(tx, ty)) {
                    paused = true;
                    if (powerUps.isTargeting()) {
                        powerUps.cancel();
                    }
                    invalidate();
                    return true;
                }
                // Force-complete any in-progress snap animation
                if (effectManager.getPieceSnapAnim().hasPendingPiece()) {
                    effectManager.getPieceSnapAnim().forceComplete();
                    commitPieceSnap();
                }
                // Check hand pieces
                if (handRects != null) {
                    for (int i = 0; i < handSize; i++) {
                        if (hand.get(i) != null && handRects[i].contains(tx, ty)) {
                            draggingIndex = i;
                            dragX = tx;
                            dragY = ty;
                            firstPickup = true;
                            prevSnapGridX = -1;
                            prevSnapGridY = -1;
                            hoverValid = false;
                            hoverComputedGx = -1;
                            hoverComputedGy = -1;
                            invalidate();
                            return true;
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                // Power-up targeting cursor update
                if (powerUps.isTargeting()) {
                    int gridW2 = blockSize * boardSize;
                    int gridH2 = blockSize * boardSize;
                    if (tx >= gridLeft && tx < gridLeft + gridW2 && ty >= gridTop && ty < gridTop + gridH2) {
                        int gridX = (int) ((tx - gridLeft) / blockSize);
                        int gridY = (int) ((ty - gridTop) / blockSize);
                        gridX = Math.max(0, Math.min(boardSize - 1, gridX));
                        gridY = Math.max(0, Math.min(boardSize - 1, gridY));
                        float fracX = ((tx - gridLeft) - gridX * blockSize) / blockSize;
                        float fracY = ((ty - gridTop) - gridY * blockSize) / blockSize;
                        powerUps.updateTargetCursor(gridX, gridY, fracX, fracY);
                        invalidate();
                        return true;
                    }
                    break;
                }
                if (draggingIndex >= 0) {
                    dragX = tx;
                    dragY = ty;
                    updateHoverPreview();
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // ─── Power-up targeting apply on finger release ───────────────────
                if (powerUps.isTargeting()) {
                    int gridW3 = blockSize * boardSize;
                    int gridH3 = blockSize * boardSize;
                    if (tx >= gridLeft && tx < gridLeft + gridW3 && ty >= gridTop && ty < gridTop + gridH3) {
                        int gridX = (int) ((tx - gridLeft) / blockSize);
                        int gridY = (int) ((ty - gridTop) / blockSize);
                        gridX = Math.max(0, Math.min(boardSize - 1, gridX));
                        gridY = Math.max(0, Math.min(boardSize - 1, gridY));
                        float fracX = ((tx - gridLeft) - gridX * blockSize) / blockSize;
                        float fracY = ((ty - gridTop) - gridY * blockSize) / blockSize;

                        // Save activeType before apply (it gets nulled after)
                        PowerUpType activeType = powerUps.getActiveType();

                        PowerUpManager.ApplyResult result = powerUps.applyAtCell(
                                gridX, gridY, fracX, fracY, board, scoreManager);

                        if (result.applied) {
                            audioManager.playPowerUp(activeType);
                            vibrate();

                            // Recompute game-over state
                            boolean wasGameOver = gameOver;
                            gameOver = !board.canPlaceAny(hand.getAll());

                            if (wasGameOver && !gameOver) {
                                // Was over but now playable - just clear gameOver (already set false)
                            } else if (!wasGameOver && gameOver) {
                                // Became newly over - invoke game-over flow
                                audioManager.playGameover();
                                storageManager.saveScore(scoreManager.getScore(), modeName);
                                ThemeManager.getInstance(getContext())
                                        .addToTotalScore(scoreManager.getScore());
                                awardXPForGame();
                                if (callback != null) callback.onGameOver(scoreManager.getScore());
                            }

                            invalidate();
                            return true;
                        }
                    }
                    // Finger released outside grid while targeting: ignore
                    return true;
                }

                if (draggingIndex >= 0) {
                    attemptPlacement();
                    draggingIndex = -1;
                    board.clearHover();
                    hoverGridX = -1;
                    hoverGridY = -1;
                    hoverValid = false;
                    hoverComputedGx = -1;
                    hoverComputedGy = -1;
                    // Reset snap state
                    firstPickup = true;
                    prevSnapGridX = -1;
                    prevSnapGridY = -1;
                    invalidate();
                }
                break;
        }
        return true;
    }

    private void updateHoverPreview() {
        if (draggingIndex < 0) return;
        Piece piece = hand.get(draggingIndex);
        if (piece == null) return;

        // Calculate grid position from drag coordinates
        float bs = blockSize;
        float px = dragX - (piece.getCols() * bs) / 2f;
        float py = dragY - (piece.getRows() * bs) - (int)(40 * density);

        int gx = Math.round((px - gridLeft) / bs);
        int gy = Math.round((py - gridTop) / bs);

        board.clearHover();

        // Check if piece is within grid bounds
        boolean inBounds = gx >= 0 && gy >= 0 && gx + piece.getCols() <= boardSize && gy + piece.getRows() <= boardSize;
        // Also consider partially overlapping positions for invalid indicator
        boolean nearGrid = gx >= -(piece.getCols()) && gy >= -(piece.getRows())
                && gx < boardSize && gy < boardSize;

        if (inBounds) {
            if (board.canPlace(piece, gx, gy)) {
                hoverGridX = gx;
                hoverGridY = gy;
                hoverValid = true;
                hoverComputedGx = gx;
                hoverComputedGy = gy;
                board.setHover(piece, gx, gy);
                checkSnap(gx, gy);
            } else {
                hoverGridX = -1;
                hoverGridY = -1;
                hoverValid = false;
                hoverComputedGx = gx;
                hoverComputedGy = gy;
                checkSnap(gx, gy);
            }
        } else if (nearGrid) {
            // Piece extends beyond boundary - show invalid indicator for in-bounds cells
            hoverGridX = -1;
            hoverGridY = -1;
            hoverValid = false;
            hoverComputedGx = gx;
            hoverComputedGy = gy;
            checkSnap(gx, gy);
        } else {
            hoverGridX = -1;
            hoverGridY = -1;
            hoverValid = false;
            hoverComputedGx = -1;
            hoverComputedGy = -1;
        }
    }

    private void attemptPlacement() {
        if (hoverGridX < 0 || hoverGridY < 0) return;
        Piece piece = hand.get(draggingIndex);
        if (piece == null) return;
        if (!board.canPlace(piece, hoverGridX, hoverGridY)) return;

        // Force-complete any in-progress snap animation
        if (effectManager.getPieceSnapAnim().hasPendingPiece()) {
            effectManager.getPieceSnapAnim().forceComplete();
            commitPieceSnap();
        }

        // Calculate drag release position (where piece currently is visually)
        float bs = blockSize;
        float fromX = dragX - (piece.getCols() * bs) / 2f;
        float fromY = dragY - (piece.getRows() * bs) - (int)(40 * density);

        // Calculate target grid position
        float toX = gridLeft + hoverGridX * bs;
        float toY = gridTop + hoverGridY * bs;

        // Clear hover
        board.clearHover();

        // Start piece snap animation
        effectManager.startPieceSnap(piece, fromX, fromY, toX, toY, hoverGridX, hoverGridY);

        // Audio + haptic for placement
        audioManager.playPlace();
        vibrate();

        // Remove from hand immediately (piece is now animating)
        hand.remove(draggingIndex);

        // Refill hand if empty
        if (hand.isEmpty()) hand.refill();

        calculateLayout(getWidth(), getHeight());
        invalidate();
    }

    /**
     * Called when piece snap animation completes - commits piece to board
     * and processes line breaks and effects.
     */
    private void commitPieceSnap() {
        PieceSnapAnim snap = effectManager.getPieceSnapAnim();
        Piece piece = snap.getPiece();
        if (piece == null) return;

        // Mark the piece as committed so subsequent frames don't re-trigger commit
        // and the early-return guard above protects against double-commit.
        snap.clearPiece();

        int gx = snap.getGridX();
        int gy = snap.getGridY();

        // Capture pre-placement state for power-up acquisition and undo
        int prevScore = scoreManager.getScore();
        powerUps.captureSnapshot(board, hand, scoreManager);

        // Place piece on board
        board.placePiece(piece, gx, gy);

        // Compute Timed Mode multiplier once at the top of scoring.
        float multiplier = (timer != null) ? timer.scoreMultiplier() : 1.0f;

        // Score for placement
        int placementPoints = scoreManager.addPlacement(piece.getBlockCount(), multiplier);

        // Score pop for placement
        float scorePosX = getWidth() / 2f;
        float scorePosY = 50f;
        effectManager.onScoreGain(placementPoints, scorePosX, scorePosY);

        // Collect cells that will be cleared (before breaking)
        List<int[]> clearedCells = collectClearedCells();

        // Break lines
        int linesBroken = board.breakLines();
        int comboLevel = scoreManager.processLineBreak(linesBroken, multiplier);

        // Time bonus on line break (Timed Mode only)
        if (linesBroken > 0 && timer != null) {
            timer.onLineBreak();
        }

        // Track combo levels for XP
        if (comboLevel > 0) {
            comboLevelsThisGame.add(comboLevel);
        }

        // Update lastPlacementClean: true when no lines broken, false when lines broken
        lastPlacementClean = (linesBroken == 0);

        if (linesBroken > 0) {
            // Undo is disabled when the last placement broke a line (Requirement 8.5)
            powerUps.clearSnapshot();

            // Stats: track total lines cleared
            statisticsManager.recordLinesCleared(linesBroken);

            triggerShake(linesBroken * 8);

            // Trigger particle effects for cleared cells
            effectManager.onLineBreak(clearedCells, blockSize, gridLeft, gridTop);

            // Trigger combo effects
            if (comboLevel >= 2) {
                effectManager.onCombo(comboLevel, linesBroken);
                audioManager.playCombo(comboLevel);
                if (comboLevel >= 4 || linesBroken >= 3) audioManager.playJuiciness();
            } else {
                audioManager.playBreak();
            }

            // Stats + achievements: track combo level
            if (comboLevel >= 2) {
                statisticsManager.recordCombo(comboLevel);
                enqueueAchievements(achievementManager.evaluateCombo(comboLevel));
            }

            // Score pop for line break bonus
            int lineBonus = Math.round(linesBroken * boardSize * 10f * comboLevel);
            if (lineBonus > 0) {
                effectManager.onScoreGain(lineBonus, scorePosX, scorePosY - 30);
            }

            // Perfect clear check
            if (board.isEmpty()) {
                scoreManager.addPerfectClearBonus();
                perfectClearCount++;
                float centerX = gridLeft + (blockSize * boardSize) / 2f;
                float centerY = gridTop + (blockSize * boardSize) / 2f;
                effectManager.onPerfectClear(centerX, centerY, getWidth(), getHeight());
                audioManager.playPerfectClear();
                effectManager.onScoreGain(500, scorePosX, scorePosY - 60);

                // Achievement: perfect clear
                enqueueAchievements(achievementManager.evaluatePerfectClear());
            }
        }

        // Power-up acquisition: combo grant
        PowerUpType comboGrant = powerUps.grantOnCombo(scoreManager.getCombo());
        if (comboGrant != null) {
            audioManager.playPowerUpAcquired();
        }

        // Power-up acquisition: score milestone grant
        List<PowerUpType> milestoneGrants = powerUps.grantOnScore(prevScore, scoreManager.getScore());
        boolean anyMilestoneGranted = false;
        for (PowerUpType g : milestoneGrants) {
            if (g != null) { anyMilestoneGranted = true; break; }
        }
        if (anyMilestoneGranted) {
            audioManager.playPowerUpAcquired();
        }

        // Update best
        if (scoreManager.getScore() > bestScore) bestScore = scoreManager.getScore();

        // ===== SPECIAL TILES =====
        final boolean[] chainGameOver = { false };
        specialTiles.decrementBombsAndDetonate(() -> chainGameOver[0] = true);
        if (chainGameOver[0]) {
            triggerGameOver();
            calculateLayout(getWidth(), getHeight());
            invalidate();
            return;
        }
        turnNumber++;
        specialTiles.evaluateSpawns(turnNumber);
        // ===== END SPECIAL TILES =====

        // Check game over
        if (!board.canPlaceAny(hand.getAll())) {
            triggerGameOver();
        }

        calculateLayout(getWidth(), getHeight());
        invalidate();
    }

    /**
     * Centralised game-over routine. Sets {@link #gameOver}, plays SFX, persists the
     * score, awards XP, and invokes the {@link GameCallback#onGameOver} callback.
     */
    private void triggerGameOver() {
        if (gameOver) return; // idempotent
        gameOver = true;
        long gameOverMs = System.currentTimeMillis();
        inputDeadlineMs = gameOverMs + 100; // 100ms grace window for in-flight input

        // Timed Mode: convert remaining seconds into a final-score bonus.
        if (timer != null) {
            int bonus = timer.endBonus();
            if (bonus > 0) {
                scoreManager.addBonus(bonus);
                timeRemainingBonusForOverlay = bonus;
            }
        }

        audioManager.playGameover();
        storageManager.saveScore(scoreManager.getScore(), modeName);
        ThemeManager.getInstance(getContext()).addToTotalScore(scoreManager.getScore());
        recordSessionStats();
        awardXPForGame();
        if (callback != null) callback.onGameOver(scoreManager.getScore());
    }

    /**
     * Records end-of-game statistics and evaluates score/cumulative achievements.
     */
    private void recordSessionStats() {
        if (statisticsManager == null || achievementManager == null) return;
        int finalScore = scoreManager.getScore();

        // Persist play time first so cumulative achievements can see it.
        if (sessionStartTime > 0) {
            long elapsedMs = System.currentTimeMillis() - sessionStartTime;
            long elapsedSec = Math.max(0, elapsedMs / 1000L);
            statisticsManager.recordPlayTime(elapsedSec);
            sessionStartTime = 0; // prevent double-count if invoked twice
        }

        // Record game-end (updates games played, average score, win streak)
        statisticsManager.recordGameEnd(modeName, finalScore);

        // Evaluate score-based and cumulative achievements
        List<String> unlocked = achievementManager.evaluateScore(finalScore, modeName);
        unlocked.addAll(achievementManager.evaluateCumulative(statisticsManager));
        enqueueAchievements(unlocked);
    }

    /**
     * Awards XP for the just-finished game and reports level-ups via the callback.
     * Idempotent — only runs once per game.
     */
    private void awardXPForGame() {
        if (xpAwardedThisGame || levelManager == null) return;
        xpAwardedThisGame = true;

        int[] combos = new int[comboLevelsThisGame.size()];
        for (int i = 0; i < combos.length; i++) combos[i] = comboLevelsThisGame.get(i);

        levelManager.awardXP(scoreManager.getScore(), combos, perfectClearCount);

        List<Integer> newLevels = levelManager.getNewLevelsReached();
        if (!newLevels.isEmpty() && callback != null) {
            List<Reward> newRewards = levelManager.getRewardsForLevels(newLevels);
            callback.onLevelUp(newLevels, newRewards);
        }
    }

    /**
     * Collect cells that will be cleared by line breaks (before actually clearing).
     */
    private List<int[]> collectClearedCells() {
        List<int[]> cells = new ArrayList<>();
        boolean[] rowFull = new boolean[boardSize];
        boolean[] colFull = new boolean[boardSize];

        for (int r = 0; r < boardSize; r++) {
            boolean full = true;
            for (int c = 0; c < boardSize; c++) {
                if (board.getCell(c, r) != Board.FILLED) { full = false; break; }
            }
            rowFull[r] = full;
        }
        for (int c = 0; c < boardSize; c++) {
            boolean full = true;
            for (int r = 0; r < boardSize; r++) {
                if (board.getCell(c, r) != Board.FILLED) { full = false; break; }
            }
            colFull[c] = full;
        }

        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                if ((rowFull[r] || colFull[c]) && board.getCell(c, r) == Board.FILLED) {
                    cells.add(new int[]{c, r, board.getCellColor(c, r)});
                }
            }
        }
        return cells;
    }

    private void handleHintTap() {
        if (hintsRemaining <= 0 || gameOver || paused) return;
        if (activeHint != null) return; // debounce while hint is showing

        HintCalculator calculator = new HintCalculator(board);
        HintCalculator.PlacementResult result = calculator.findBestPlacement(hand.getAll());

        if (result != null) {
            hintsRemaining--;
            activeHint = result;
            hintDisplayStartTime = System.currentTimeMillis();
        } else {
            // No valid moves - show message, don't consume hint
            noMovesMessage = "No moves available";
            noMovesMessageTime = System.currentTimeMillis();
        }
        invalidate();
    }

    private void handlePauseTouch(float tx, float ty) {
        int w = getWidth(), h = getHeight();
        float cw = Math.min(w * 0.85f, 400);
        float ch = 350;
        float cx = (w - cw) / 2f;
        float cy = (h - ch) / 2f;

        // Button X bounds (buttons are 220px wide, centered at w/2)
        float bx1 = w / 2f - 110;
        float bx2 = w / 2f + 110;

        // Resume button
        if (tx > bx1 && tx < bx2 && ty > cy + 115 && ty < cy + 165) { paused = false; invalidate(); }
        // Quit button
        if (tx > bx1 && tx < bx2 && ty > cy + 185 && ty < cy + 235) { if (callback != null) callback.onPauseRequested(); }
    }

    private void handleGameOverTouch(float tx, float ty) {
        int w = getWidth(), h = getHeight();
        float cw = Math.min(w * 0.85f, 400);
        float ch = 300;
        float cx = (w - cw) / 2f;
        float cy = (h - ch) / 2f;

        // Button X bounds (buttons are 220px wide, centered at w/2)
        float bx1 = w / 2f - 110;
        float bx2 = w / 2f + 110;

        // Back to menu button
        if (tx > bx1 && tx < bx2 && ty > cy + 175 && ty < cy + 225) {
            if (callback != null) callback.onPauseRequested();
        }
    }

    // ======== EFFECTS ========

    private void triggerShake(float intensity) {
        shakeIntensity = intensity;
        shakeEndTime = System.currentTimeMillis() + 200;
        invalidate();
    }

    private void updateShake() {
        if (System.currentTimeMillis() < shakeEndTime) {
            float t = (shakeEndTime - System.currentTimeMillis()) / 200f;
            shakeX = (float)(Math.random() * 2 - 1) * shakeIntensity * t;
            shakeY = (float)(Math.random() * 2 - 1) * shakeIntensity * t;
        } else {
            shakeX = 0;
            shakeY = 0;
        }
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(30);
                }
            }
        } catch (Exception e) { /* ignore */ }
    }

    public void setPaused(boolean p) {
        this.paused = p;
        if (p && powerUps.isTargeting()) {
            powerUps.cancel();
        }
        if (timer != null) {
            if (p) {
                timer.pause();
            } else {
                timer.resume(System.currentTimeMillis());
            }
        }
        invalidate();
    }
    public boolean isPaused() { return paused; }
    public boolean isGameOver() { return gameOver; }

    // ======== ACHIEVEMENT NOTIFICATION ========

    /**
     * Enqueue an achievement notification to be displayed at the top of the screen.
     */
    public void enqueueAchievement(String name) {
        if (name == null) return;
        achievementQueue.add(name);
        invalidate();
    }

    /**
     * Render a gold banner showing the current achievement notification.
     * Cycles through queued notifications, each visible for 3 seconds.
     */
    private void drawAchievementNotification(Canvas canvas, int w, int h) {
        long now = System.currentTimeMillis();

        // Advance the queue if no current notification or the current one expired.
        if (currentNotification == null || now >= notificationEndTime) {
            currentNotification = achievementQueue.pollFirst();
            if (currentNotification != null) {
                notificationEndTime = now + NOTIFICATION_DURATION_MS;
            } else {
                notificationEndTime = 0;
                return;
            }
        }
        if (currentNotification == null) return;

        // Banner sized to fit between top edge and grid (above HUD area is too crowded;
        // we draw it at the very top with a slight downward offset so it sits above the
        // HUD without obstructing the board or hand pieces).
        float bannerHeight = 56 * density;
        float marginX = 16 * density;
        float top = 4 * density;
        RectF banner = new RectF(marginX, top, w - marginX, top + bannerHeight);

        // Fade in/out for the first/last 300ms
        float remaining = notificationEndTime - now;
        float elapsed = NOTIFICATION_DURATION_MS - remaining;
        float alpha = 1.0f;
        float fadeMs = 300f;
        if (elapsed < fadeMs) alpha = elapsed / fadeMs;
        else if (remaining < fadeMs) alpha = remaining / fadeMs;
        if (alpha < 0) alpha = 0;
        if (alpha > 1) alpha = 1;

        // Semi-transparent dark background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xCC1A1A2E);
        paint.setAlpha((int) (alpha * 0xCC));
        canvas.drawRoundRect(banner, 12, 12, paint);

        // Gold border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f * density);
        paint.setColor(0xFFFFD700);
        paint.setAlpha((int) (alpha * 255));
        canvas.drawRoundRect(banner, 12, 12, paint);

        // "ACHIEVEMENT UNLOCKED" small label
        textPaint.setTypeface(fontRegular);
        textPaint.setTextSize(10 * density);
        textPaint.setColor(0xFFFFD700);
        textPaint.setAlpha((int) (alpha * 255));
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("ACHIEVEMENT UNLOCKED",
                banner.centerX(), banner.top + 16 * density, textPaint);

        // Achievement name (centered)
        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(16 * density);
        textPaint.setColor(Color.WHITE);
        textPaint.setAlpha((int) (alpha * 255));
        canvas.drawText(currentNotification,
                banner.centerX(), banner.top + 38 * density, textPaint);

        paint.setAlpha(255);
        textPaint.setAlpha(255);
    }

    /**
     * Convenience helper: enqueue every name in {@code names}.
     */
    private void enqueueAchievements(List<String> names) {
        if (names == null) return;
        for (String n : names) enqueueAchievement(n);
    }

    // ======== STATE PERSISTENCE (Bug 7) ========

    public void saveState(Bundle outState) {
        if (board == null || scoreManager == null) return;

        // Save board state
        int size = board.getSize();
        int[] cellStates = new int[size * size];
        int[] cellColors = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int cell = board.getCell(x, y);
                // Save FILLED, FROZEN, FROZEN_CRACKED, LOCKED, BOMB_TILE verbatim;
                // collapse only hover states to their underlying real state
                int saved;
                if (cell == Board.HOVERED || cell == Board.HOVERED_BREAK_EMPTY) {
                    saved = Board.EMPTY;
                } else if (cell == Board.HOVERED_BREAK_FILLED) {
                    saved = Board.FILLED;
                } else {
                    saved = cell;
                }
                cellStates[y * size + x] = saved;
                cellColors[y * size + x] = board.getCellColor(x, y);
            }
        }
        outState.putIntArray("board_cells", cellStates);
        outState.putIntArray("board_colors", cellColors);
        outState.putInt("board_size", size);

        // Save hand state
        int handCount = hand.getSize();
        outState.putInt("hand_size", handCount);
        for (int i = 0; i < handCount; i++) {
            Piece p = hand.get(i);
            if (p != null) {
                outState.putInt("hand_shape_" + i, p.shapeIndex);
                outState.putInt("hand_color_" + i, p.colorIndex);
            } else {
                outState.putInt("hand_shape_" + i, -1);
            }
        }

        // Save score and combo
        outState.putInt("score", scoreManager.getScore());
        outState.putInt("combo", scoreManager.getCombo());

        // Save game flags
        outState.putBoolean("game_over", gameOver);
        outState.putBoolean("paused", paused);
        outState.putInt("best_score", bestScore);

        // Save hint state
        outState.putInt("hints_remaining", hintsRemaining);

        // Save power-up state
        powerUps.saveState(outState);

        // Save special-tile state
        if (specialTiles != null) specialTiles.saveState(outState);

        // Save Timed Mode controller state.
        if (timer != null) {
            timer.saveState(outState);
        } else {
            outState.putBoolean("timer_present", false);
        }
    }

    public void restoreState(Bundle savedState) {
        if (savedState == null || board == null) return;

        int size = savedState.getInt("board_size", 0);
        if (size != board.getSize()) return;

        // Restore board
        int[] cellStates = savedState.getIntArray("board_cells");
        int[] cellColors = savedState.getIntArray("board_colors");
        if (cellStates != null && cellColors != null) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    board.setCell(x, y, cellStates[y * size + x], cellColors[y * size + x]);
                }
            }
        }

        // Restore hand
        int handCount = savedState.getInt("hand_size", 0);
        if (handCount == hand.getSize()) {
            for (int i = 0; i < handCount; i++) {
                int shapeIdx = savedState.getInt("hand_shape_" + i, -1);
                if (shapeIdx >= 0) {
                    int colorIdx = savedState.getInt("hand_color_" + i, 0);
                    // Replace hand piece via remove and direct access
                    hand.remove(i);
                    hand.getAll()[i] = new Piece(shapeIdx, colorIdx);
                } else {
                    hand.remove(i);
                }
            }
        }

        // Restore score and combo
        int score = savedState.getInt("score", 0);
        int combo = savedState.getInt("combo", 0);
        scoreManager.restoreState(score, combo);

        // Restore flags
        gameOver = savedState.getBoolean("game_over", false);
        paused = savedState.getBoolean("paused", false);
        bestScore = savedState.getInt("best_score", 0);

        // Restore hint state
        hintsRemaining = savedState.getInt("hints_remaining", 3);

        // Restore power-up state
        powerUps.restoreState(savedState);

        // Restore special-tile state (after board cells are restored verbatim)
        if (specialTiles != null) {
            specialTiles.restoreState(savedState);
            this.turnNumber = specialTiles.getTurnNumber();
        }

        // Restore Timed Mode controller (always paused after restore — Requirement 10.2).
        if (savedState.getBoolean("timer_present", false)) {
            String tMode = savedState.getString("timer_mode");
            float tInitial = savedState.getFloat("timer_initial", 0f);
            if ("timed60".equals(tMode) || "timed90".equals(tMode)) {
                this.timer = new TimedModeController(tMode, tInitial);
                this.timer.restoreState(savedState);
                this.paused = true;  // resumed round must start paused
            } else {
                this.timer = null;
            }
        } else {
            this.timer = null;
        }

        calculateLayout(getWidth(), getHeight());
        invalidate();
    }

    // ======== BACK PRESS HANDLING ========

    /**
     * Handles back press when power-up targeting is active.
     * @return true if the back press was consumed (targeting was cancelled), false otherwise
     */
    public boolean onBackPressed() {
        if (powerUps.isTargeting()) {
            powerUps.cancel();
            invalidate();
            return true;
        }
        return false;
    }
}
