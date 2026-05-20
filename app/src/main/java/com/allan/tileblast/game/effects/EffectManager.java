package com.allan.tileblast.game.effects;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.allan.tileblast.game.Piece;

import java.util.List;

/**
 * Orchestrates all visual effects: particles, screen flash, zoom pulse,
 * combo text, score pops, and piece snap animation.
 * Calculates deltaTime internally with 33ms max clamp.
 */
public class EffectManager {
    private static final float MAX_DELTA_MS = 33f; // clamp to ~30fps

    private ParticleSystem particleSystem;
    private ScreenFlash screenFlash;
    private ZoomPulse zoomPulse;
    private ComboTextAnim comboTextAnim;
    private ScorePopManager scorePopManager;
    private PieceSnapAnim pieceSnapAnim;
    private long lastFrameTime;
    private float density;

    private final Paint effectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public void init(float density) {
        this.density = density;
        particleSystem = new ParticleSystem(density);
        screenFlash = new ScreenFlash();
        zoomPulse = new ZoomPulse();
        comboTextAnim = new ComboTextAnim();
        scorePopManager = new ScorePopManager();
        pieceSnapAnim = new PieceSnapAnim();
        lastFrameTime = System.nanoTime();
    }

    // ======== TRIGGER METHODS ========

    /**
     * Trigger particle effects for cleared cells.
     */
    public void onLineBreak(List<int[]> clearedCells, int blockSize, int gridLeft, int gridTop) {
        if (clearedCells == null) return;
        for (int[] cell : clearedCells) {
            // cell[0] = x, cell[1] = y, cell[2] = colorIndex
            particleSystem.spawnLineBreak(cell[0], cell[1], blockSize, gridLeft, gridTop, cell[2]);
        }
    }

    /**
     * Trigger screen flash and zoom pulse based on combo level and lines broken.
     * Also triggers combo text animation for combo >= 3.
     */
    public void onCombo(int comboLevel, int linesBroken) {
        // Screen flash at combo >= 3
        if (comboLevel >= 3) {
            screenFlash.trigger(comboLevel);
            // Combo text animation
            String text = "COMBO x" + comboLevel + "!";
            comboTextAnim.trigger(text, 0xFFFFD700, 38f * density, 400f);
        }

        // Zoom pulse at combo >= 4 OR lines >= 3
        if (comboLevel >= 4 || linesBroken >= 3) {
            if (zoomPulse.isActive()) {
                zoomPulse.triggerFrom(zoomPulse.getScale());
            } else {
                zoomPulse.trigger();
            }
        }
    }

    /**
     * Trigger perfect clear celebration.
     */
    public void onPerfectClear(float centerX, float centerY, int viewWidth, int viewHeight) {
        particleSystem.spawnCelebration(centerX, centerY);
        comboTextAnim.trigger("PERFECT CLEAR", 0xFFFFD700, 48f * density, 800f);
    }

    /**
     * Trigger score pop for point gain.
     */
    public void onScoreGain(int points, float x, float y) {
        scorePopManager.spawn("+" + points, x, y, density);
    }

    /**
     * Start piece snap animation.
     */
    public void startPieceSnap(Piece piece, float fromX, float fromY, float toX, float toY, int gridX, int gridY) {
        pieceSnapAnim.start(piece, fromX, fromY, toX, toY, gridX, gridY);
    }

    // ======== FRAME UPDATE ========

    /**
     * Update all effects. Calculates deltaTime internally, clamped to 33ms max.
     */
    public void update() {
        long now = System.nanoTime();
        float deltaMs = (now - lastFrameTime) / 1_000_000f;
        lastFrameTime = now;

        // Clamp deltaTime to prevent physics glitches
        if (deltaMs < 0f) deltaMs = 0f;
        if (deltaMs > MAX_DELTA_MS) deltaMs = MAX_DELTA_MS;

        particleSystem.update(deltaMs);
        screenFlash.update(deltaMs);
        zoomPulse.update(deltaMs);
        comboTextAnim.update(deltaMs);
        scorePopManager.update(deltaMs);
        pieceSnapAnim.update(deltaMs);
    }

    // ======== DRAW METHODS ========

    /**
     * Apply zoom pulse scale transform around grid center.
     */
    public void applyZoomTransform(Canvas canvas, float gridCenterX, float gridCenterY) {
        if (zoomPulse.isActive()) {
            float scale = zoomPulse.getScale();
            canvas.scale(scale, scale, gridCenterX, gridCenterY);
        }
    }

    /**
     * Draw all active particles.
     */
    public void drawParticles(Canvas canvas) {
        particleSystem.draw(canvas, effectPaint);
    }

    /**
     * Draw screen flash overlay.
     */
    public void drawScreenFlash(Canvas canvas, int viewWidth, int viewHeight) {
        screenFlash.draw(canvas, effectPaint, viewWidth, viewHeight);
    }

    /**
     * Draw score pop texts.
     */
    public void drawScorePops(Canvas canvas, Typeface font) {
        scorePopManager.draw(canvas, effectPaint, font, density);
    }

    /**
     * Draw combo text animation.
     */
    public void drawComboText(Canvas canvas, Typeface font, int viewWidth, int viewHeight) {
        comboTextAnim.draw(canvas, effectPaint, font, viewWidth, viewHeight);
    }

    /**
     * Draw piece snap animation.
     */
    public void drawPieceSnap(Canvas canvas, float blockSize) {
        // Drawing is handled by GameView using getCurrentX/Y from pieceSnapAnim
    }

    // ======== STATE QUERIES ========

    /**
     * @return true if any effect is currently active
     */
    public boolean isActive() {
        return particleSystem.isActive()
                || screenFlash.isActive()
                || zoomPulse.isActive()
                || comboTextAnim.isActive()
                || scorePopManager.isActive()
                || pieceSnapAnim.isActive();
    }

    /**
     * @return true if piece snap animation is in progress
     */
    public boolean isPieceSnapping() {
        return pieceSnapAnim.isActive();
    }

    /**
     * @return the PieceSnapAnim instance for position queries
     */
    public PieceSnapAnim getPieceSnapAnim() {
        return pieceSnapAnim;
    }
}
