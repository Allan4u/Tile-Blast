package com.allan.tileblast.game.effects;

import com.allan.tileblast.game.Piece;

/**
 * Smooth piece snap animation: 100ms ease-out interpolation from
 * drag release position to final grid position.
 */
public class PieceSnapAnim {
    private static final float DURATION = 100f; // ms

    private Piece piece;
    private float startX, startY;  // drag release position
    private float endX, endY;      // grid target position
    private float elapsed;
    private boolean active;
    private int gridX, gridY;      // board coordinates for commit

    public PieceSnapAnim() {
        active = false;
    }

    /**
     * Start a piece snap animation.
     */
    public void start(Piece piece, float fromX, float fromY, float toX, float toY, int gridX, int gridY) {
        this.piece = piece;
        this.startX = fromX;
        this.startY = fromY;
        this.endX = toX;
        this.endY = toY;
        this.gridX = gridX;
        this.gridY = gridY;
        this.elapsed = 0f;
        this.active = true;
    }

    public void update(float deltaMs) {
        if (!active) return;

        elapsed += deltaMs;
        if (elapsed >= DURATION) {
            elapsed = DURATION;
            active = false;
        }
    }

    public float getCurrentX() {
        float t = Math.min(elapsed / DURATION, 1.0f);
        float eased = Easing.easeOut(t);
        return startX + (endX - startX) * eased;
    }

    public float getCurrentY() {
        float t = Math.min(elapsed / DURATION, 1.0f);
        float eased = Easing.easeOut(t);
        return startY + (endY - startY) * eased;
    }

    public boolean isComplete() {
        return elapsed >= DURATION;
    }

    /**
     * Force-complete the animation immediately (for interruption).
     */
    public void forceComplete() {
        elapsed = DURATION;
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * True when there is a pending piece that has not been committed yet.
     * Stays true after the animation completes until {@code clearPiece()} is called.
     */
    public boolean hasPendingPiece() {
        return piece != null;
    }

    public void clearPiece() {
        piece = null;
    }

    public Piece getPiece() {
        return piece;
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridY() {
        return gridY;
    }
}
