package com.allan.tileblast.game;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/**
 * Manages the power-up inventory, acquisition, activation state machine,
 * and persistence for the Power-Ups System.
 */
public class PowerUpManager {

    // ─── ApplyResult ────────────────────────────────────────────────────────────

    /**
     * Result of applying a targeted power-up (BOMB or LINE_SWEEP) at a cell.
     */
    public static class ApplyResult {
        public final boolean applied;
        public final int cellsCleared;
        public final int filledCellsCleared;
        public final boolean wasRow;
        public final int targetIndex;

        public ApplyResult(boolean applied, int cellsCleared, int filledCellsCleared,
                           boolean wasRow, int targetIndex) {
            this.applied = applied;
            this.cellsCleared = cellsCleared;
            this.filledCellsCleared = filledCellsCleared;
            this.wasRow = wasRow;
            this.targetIndex = targetIndex;
        }
    }

    // ─── Activation State Enum ───────────────────────────────────────────────────

    /** The three states of the power-up activation state machine. */
    public enum ActivationState {
        IDLE, TARGETING, EXECUTING
    }

    // ─── GameContext ─────────────────────────────────────────────────────────────

    /**
     * Immutable context struct passed to activation gating methods so that
     * PowerUpManager can evaluate gating rules without a back-reference to GameView.
     */
    public static class GameContext {
        public final boolean paused;
        public final boolean gameOver;
        public final boolean dragging;
        public final boolean hasSnapshot;
        public final boolean lastPlacementClean;

        public GameContext(boolean paused, boolean gameOver, boolean dragging,
                           boolean hasSnapshot, boolean lastPlacementClean) {
            this.paused = paused;
            this.gameOver = gameOver;
            this.dragging = dragging;
            this.hasSnapshot = hasSnapshot;
            this.lastPlacementClean = lastPlacementClean;
        }
    }

    // ─── Constants ───────────────────────────────────────────────────────────────

    /** Maximum number of power-ups a player can hold per type. */
    public static final int MAX_PER_TYPE = 2;

    /** Fixed score thresholds at which a power-up is awarded. */
    public static final int[] SCORE_MILESTONES = {1000, 5000, 10000, 25000, 50000};

    // ─── Inventory ───────────────────────────────────────────────────────────────

    /** Inventory counts, one per PowerUpType (indexed by ordinal). */
    private final int[] counts = new int[PowerUpType.values().length];

    /** Score milestones already awarded in the current game. */
    private final HashSet<Integer> earnedMilestones = new HashSet<>();

    /** Random source for acquisition selection (constructor-injected for testability). */
    private final Random random;

    // ─── Snapshot ─────────────────────────────────────────────────────────────────

    /** Captured board/hand/score snapshot for the Undo power-up. Null when no undo is available. */
    private BoardSnapshot snapshot = null;

    // ─── Activation State ────────────────────────────────────────────────────────

    private ActivationState state = ActivationState.IDLE;
    private PowerUpType activeType = null;
    private int targetGridX = -1;
    private int targetGridY = -1;
    private float targetFracX = 0.5f;
    private float targetFracY = 0.5f;
    private boolean targetIsRow = false;

    /**
     * Constructs a PowerUpManager with the given random source.
     * Production code passes {@code new Random()}; tests pass a seeded instance.
     */
    public PowerUpManager(Random random) {
        this.random = random;
    }

    /**
     * Returns the current count for the given power-up type.
     */
    public int getCount(PowerUpType type) {
        return counts[type.ordinal()];
    }

    /**
     * Resets all counters to 0 and clears the earned milestones set.
     * Called at the start of a new game.
     */
    public void newGameReset() {
        Arrays.fill(counts, 0);
        earnedMilestones.clear();
        state = ActivationState.IDLE;
        activeType = null;
        snapshot = null;
    }

    /**
     * Attempts to grant a power-up when the player reaches combo level 4 or higher.
     * Picks a uniform-random PowerUpType; if its counter is below MAX_PER_TYPE,
     * increments the counter and returns the type. Otherwise returns null
     * (surplus discarded, cap clamped).
     *
     * @param comboLevel the current combo level
     * @return the granted PowerUpType, or null if combo < 4 or the chosen type is at cap
     */
    public PowerUpType grantOnCombo(int comboLevel) {
        if (comboLevel < 4) {
            return null;
        }
        PowerUpType type = PowerUpType.randomFrom(random);
        int idx = type.ordinal();
        if (counts[idx] < MAX_PER_TYPE) {
            counts[idx]++;
            return type;
        }
        return null;
    }

    /**
     * Grants power-ups for each score milestone crossed between prevScore and newScore.
     * For each milestone m in SCORE_MILESTONES where prevScore < m <= newScore and
     * the milestone has not already been earned, picks a uniform-random PowerUpType,
     * attempts to increment under cap, marks the milestone as earned, and appends
     * the granted type (or null if capped) to the result list.
     *
     * Repeated calls with the same (prevScore, newScore) after the first return an
     * empty list because milestones are now in the earned set.
     *
     * @param prevScore the score before the placement
     * @param newScore  the score after the placement
     * @return list of granted PowerUpTypes (null entries indicate cap was reached)
     */
    public List<PowerUpType> grantOnScore(int prevScore, int newScore) {
        List<PowerUpType> result = new ArrayList<>();
        for (int m : SCORE_MILESTONES) {
            if (prevScore < m && m <= newScore && !earnedMilestones.contains(m)) {
                earnedMilestones.add(m);
                PowerUpType type = PowerUpType.randomFrom(random);
                int idx = type.ordinal();
                if (counts[idx] < MAX_PER_TYPE) {
                    counts[idx]++;
                    result.add(type);
                } else {
                    result.add(null);
                }
            }
        }
        return result;
    }

    // ─── Activation State Machine ────────────────────────────────────────────────

    /**
     * Attempts to activate the given power-up type, applying the gating matrix.
     * <p>
     * Gating rules (all types): count > 0, !paused, !gameOver (except BOMB/LINE_SWEEP
     * which are allowed when gameOver is true per Requirement 10.2).
     * <ul>
     *   <li>BOMB / LINE_SWEEP: additionally !dragging. Enters TARGETING on success.</li>
     *   <li>ROTATE: additionally dragging must be true. Returns true for immediate apply.</li>
     *   <li>UNDO: additionally hasSnapshot and lastPlacementClean must be true. Returns true for immediate apply.</li>
     * </ul>
     *
     * @param type the power-up type to activate
     * @param ctx  the current game context for gating evaluation
     * @return true if activation succeeded (entered TARGETING or ready for immediate apply)
     */
    public boolean activate(PowerUpType type, GameContext ctx) {
        // Common gates: must have count > 0 and not be paused
        if (counts[type.ordinal()] <= 0) return false;
        if (ctx.paused) return false;

        switch (type) {
            case BOMB:
            case LINE_SWEEP:
                // BOMB and LINE_SWEEP are allowed even when gameOver (Req 10.2)
                if (ctx.dragging) return false;
                state = ActivationState.TARGETING;
                activeType = type;
                return true;

            case ROTATE:
                if (ctx.gameOver) return false;
                if (!ctx.dragging) return false;
                // Immediate apply - caller will invoke applyRotate
                return true;

            case UNDO:
                if (ctx.gameOver) return false;
                if (!ctx.hasSnapshot) return false;
                if (!ctx.lastPlacementClean) return false;
                // Immediate apply - caller will invoke applyUndo
                return true;

            default:
                return false;
        }
    }

    /**
     * Cancels the current targeting mode, resetting state to IDLE.
     * Leaves all counters unchanged.
     */
    public void cancel() {
        state = ActivationState.IDLE;
        activeType = null;
    }

    /**
     * Updates the targeting cursor position and recomputes the row/column selection
     * for LINE_SWEEP.
     * <p>
     * The row/column rule: row is chosen when {@code |fracY - 0.5| < |fracX - 0.5|},
     * otherwise column.
     *
     * @param gridX grid column index under the finger
     * @param gridY grid row index under the finger
     * @param fracX fractional X position within the cell [0, 1)
     * @param fracY fractional Y position within the cell [0, 1)
     */
    public void updateTargetCursor(int gridX, int gridY, float fracX, float fracY) {
        this.targetGridX = gridX;
        this.targetGridY = gridY;
        this.targetFracX = fracX;
        this.targetFracY = fracY;
        // Compute row vs column for LINE_SWEEP:
        // Row when the finger is closer to the vertical center of the cell
        this.targetIsRow = Math.abs(fracY - 0.5f) < Math.abs(fracX - 0.5f);
    }

    // ─── Apply Effects ───────────────────────────────────────────────────────────

    /**
     * Applies the currently active targeted power-up (BOMB or LINE_SWEEP) at the
     * given grid cell. Returns an {@link ApplyResult} describing what happened.
     * <p>
     * For BOMB: clamps the 3x3 area centered at (tx, ty) to grid bounds, counts
     * FILLED cells, clears the area, adds 10 * filledCount to score, decrements
     * the BOMB counter, transitions to IDLE, and leaves combo unchanged.
     *
     * @param tx           grid column of the target cell
     * @param ty           grid row of the target cell
     * @param fracX        fractional X position within the cell [0, 1)
     * @param fracY        fractional Y position within the cell [0, 1)
     * @param board        the game board
     * @param scoreManager the score manager
     * @return an ApplyResult describing the outcome
     */
    public ApplyResult applyAtCell(int tx, int ty, float fracX, float fracY,
                                   Board board, ScoreManager scoreManager) {
        if (state != ActivationState.TARGETING || activeType == null) {
            return new ApplyResult(false, 0, 0, false, -1);
        }

        if (activeType == PowerUpType.BOMB) {
            return applyBomb(tx, ty, board, scoreManager);
        }

        if (activeType == PowerUpType.LINE_SWEEP) {
            return applyLineSweep(tx, ty, fracX, fracY, board);
        }

        return new ApplyResult(false, 0, 0, false, -1);
    }

    /**
     * Applies the BOMB effect at the given target cell.
     */
    private ApplyResult applyBomb(int tx, int ty, Board board, ScoreManager scoreManager) {
        int size = board.getSize();

        // Clamp the 3x3 area to grid bounds
        int minX = Math.max(0, tx - 1);
        int maxX = Math.min(size - 1, tx + 1);
        int minY = Math.max(0, ty - 1);
        int maxY = Math.min(size - 1, ty + 1);

        // Transition to EXECUTING
        state = ActivationState.EXECUTING;

        // Count filled cells and total cells in the bounded area
        int filledCount = 0;
        int totalCells = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                totalCells++;
                if (board.getCell(x, y) == Board.FILLED) {
                    filledCount++;
                }
            }
        }

        // Clear every cell in the bounded area
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                board.setCell(x, y, Board.EMPTY, 0);
            }
        }

        // Add score: 10 points per filled cell destroyed
        scoreManager.addScore(10 * filledCount);

        // Decrement the BOMB counter
        counts[PowerUpType.BOMB.ordinal()]--;

        // Transition to IDLE
        state = ActivationState.IDLE;
        activeType = null;

        return new ApplyResult(true, totalCells, filledCount, false, -1);
    }

    /**
     * Applies the LINE_SWEEP effect at the given target cell.
     * Recomputes targetIsRow from (fracX, fracY): row when |fracY - 0.5| < |fracX - 0.5|.
     * Clears every cell in the chosen row or column.
     */
    private ApplyResult applyLineSweep(int tx, int ty, float fracX, float fracY, Board board) {
        int size = board.getSize();

        // Transition to EXECUTING
        state = ActivationState.EXECUTING;

        // Recompute row vs column from fractional position
        boolean wasRow = Math.abs(fracY - 0.5f) < Math.abs(fracX - 0.5f);

        // Determine the target index: row index (ty) if wasRow, column index (tx) if not
        int targetIndex = wasRow ? ty : tx;

        // Count filled cells and clear the chosen line
        int filledCount = 0;
        if (wasRow) {
            for (int x = 0; x < size; x++) {
                if (board.getCell(x, ty) == Board.FILLED) {
                    filledCount++;
                }
                board.setCell(x, ty, Board.EMPTY, 0);
            }
        } else {
            for (int y = 0; y < size; y++) {
                if (board.getCell(tx, y) == Board.FILLED) {
                    filledCount++;
                }
                board.setCell(tx, y, Board.EMPTY, 0);
            }
        }

        // Decrement the LINE_SWEEP counter
        counts[PowerUpType.LINE_SWEEP.ordinal()]--;

        // Transition to IDLE
        state = ActivationState.IDLE;
        activeType = null;

        return new ApplyResult(true, size, filledCount, wasRow, targetIndex);
    }

    // ─── Getters ─────────────────────────────────────────────────────────────────

    /** Returns the current activation state. */
    public ActivationState getState() {
        return state;
    }

    /** Returns the currently active power-up type, or null if IDLE. */
    public PowerUpType getActiveType() {
        return activeType;
    }

    /** Returns true if the manager is in TARGETING state. */
    public boolean isTargeting() {
        return state == ActivationState.TARGETING;
    }

    /** Returns the grid X coordinate of the current targeting cursor. */
    public int getTargetGridX() {
        return targetGridX;
    }

    /** Returns the grid Y coordinate of the current targeting cursor. */
    public int getTargetGridY() {
        return targetGridY;
    }

    /** Returns true if LINE_SWEEP targeting is selecting a row (vs column). */
    public boolean isTargetingRow() {
        return targetIsRow;
    }

    // ─── Rotate Effect ──────────────────────────────────────────────────────────

    /**
     * Applies the ROTATE power-up to the dragged piece.
     * Builds a new matrix R'[c][R - 1 - r] = M[r][c] (90° clockwise rotation),
     * returns a new Piece with the rotated matrix and the same colorIndex,
     * decrements the ROTATE counter, and leaves state at IDLE.
     * <p>
     * Consecutive activations during a single drag are allowed — only the counter
     * is mutated (decremented).
     *
     * @param dragged the piece currently being dragged
     * @return a new Piece with the rotated matrix
     */
    public Piece applyRotate(Piece dragged) {
        int[][] M = dragged.matrix;
        int R = M.length;
        int C = M[0].length;

        // Build rotated matrix: R'[c][R - 1 - r] = M[r][c]
        // Result has C rows and R columns
        int[][] rotated = new int[C][R];
        for (int r = 0; r < R; r++) {
            for (int c = 0; c < C; c++) {
                rotated[c][R - 1 - r] = M[r][c];
            }
        }

        // Create a new Piece preserving the colorIndex
        Piece result = new Piece(dragged.shapeIndex, dragged.colorIndex);
        result.matrix = rotated;

        // Decrement the ROTATE counter
        counts[PowerUpType.ROTATE.ordinal()]--;

        // State remains IDLE (no state transition needed for immediate-apply power-ups)
        state = ActivationState.IDLE;

        return result;
    }

    // ─── Snapshot Lifecycle ──────────────────────────────────────────────────────

    /**
     * Captures a deep-copy snapshot of the current board, hand, and score state.
     * Called by {@code GameView.attemptPlacement} before mutating the board.
     *
     * @param board        the game board
     * @param hand         the player's hand
     * @param scoreManager the score manager
     */
    public void captureSnapshot(Board board, Hand hand, ScoreManager scoreManager) {
        this.snapshot = BoardSnapshot.capture(board, hand, scoreManager);
    }

    /**
     * Clears the stored snapshot, disabling Undo.
     * Called by {@code GameView.attemptPlacement} when {@code linesBroken > 0}.
     */
    public void clearSnapshot() {
        this.snapshot = null;
    }

    /**
     * Returns true if a snapshot is currently stored (Undo is potentially available).
     */
    public boolean hasSnapshot() {
        return snapshot != null;
    }

    /**
     * Applies the UNDO power-up: validates the snapshot's board size matches the live board,
     * restores the captured state into the live objects, decrements the UNDO counter,
     * discards the snapshot, and returns it for caller use.
     * <p>
     * Returns {@code null} and clears the snapshot on board-size mismatch.
     *
     * @param board        the game board
     * @param hand         the player's hand
     * @param scoreManager the score manager
     * @return the applied snapshot, or null if the snapshot was invalid
     */
    public BoardSnapshot applyUndo(Board board, Hand hand, ScoreManager scoreManager) {
        if (snapshot == null) {
            return null;
        }

        // Validate board size matches
        if (snapshot.getBoardSize() != board.getSize()) {
            snapshot = null;
            return null;
        }

        // Restore the captured state into the live objects
        snapshot.restoreInto(board, hand, scoreManager);

        // Decrement the UNDO counter
        counts[PowerUpType.UNDO.ordinal()]--;

        // Keep a reference to return, then discard
        BoardSnapshot applied = snapshot;
        snapshot = null;

        return applied;
    }

    // ─── Getters ─────────────────────────────────────────────────────────────────

    /**
     * Returns whether the UNDO power-up is currently enabled (can be activated).
     * Undo is enabled when: count > 0, hasSnapshot, lastPlacementClean, !paused, !gameOver.
     */
    public boolean isUndoEnabled(GameContext ctx) {
        return counts[PowerUpType.UNDO.ordinal()] > 0
                && !ctx.paused
                && !ctx.gameOver
                && ctx.hasSnapshot
                && ctx.lastPlacementClean;
    }

    // ─── Persistence ─────────────────────────────────────────────────────────────

    private static final String KEY_COUNT_BOMB = "pu_count_bomb";
    private static final String KEY_COUNT_SWEEP = "pu_count_sweep";
    private static final String KEY_COUNT_ROTATE = "pu_count_rotate";
    private static final String KEY_COUNT_UNDO = "pu_count_undo";
    private static final String KEY_MILESTONES = "pu_milestones";

    /**
     * Writes the power-up counters and earned milestones to the given Bundle.
     * Called by {@code GameView.saveState} to persist state across configuration changes.
     *
     * @param outState the Bundle to write into
     */
    public void saveState(Bundle outState) {
        outState.putInt(KEY_COUNT_BOMB, counts[PowerUpType.BOMB.ordinal()]);
        outState.putInt(KEY_COUNT_SWEEP, counts[PowerUpType.LINE_SWEEP.ordinal()]);
        outState.putInt(KEY_COUNT_ROTATE, counts[PowerUpType.ROTATE.ordinal()]);
        outState.putInt(KEY_COUNT_UNDO, counts[PowerUpType.UNDO.ordinal()]);

        // Write earned milestones as an int array
        int[] milestonesArray = new int[earnedMilestones.size()];
        int i = 0;
        for (int m : earnedMilestones) {
            milestonesArray[i++] = m;
        }
        outState.putIntArray(KEY_MILESTONES, milestonesArray);

        // Write snapshot persistence
        if (snapshot != null) {
            snapshot.writeTo(outState);
        } else {
            outState.putBoolean("pu_snap_present", false);
        }
    }

    /**
     * Restores the power-up counters and earned milestones from the given Bundle.
     * Each counter is read independently; missing keys default to 0.
     * Always forces {@code state = IDLE} and {@code activeType = null} regardless
     * of any saved active state (Requirement 12.7).
     *
     * @param savedState the Bundle to read from
     */
    public void restoreState(Bundle savedState) {
        // Restore each counter independently, defaulting to 0 if missing
        counts[PowerUpType.BOMB.ordinal()] = savedState.getInt(KEY_COUNT_BOMB, 0);
        counts[PowerUpType.LINE_SWEEP.ordinal()] = savedState.getInt(KEY_COUNT_SWEEP, 0);
        counts[PowerUpType.ROTATE.ordinal()] = savedState.getInt(KEY_COUNT_ROTATE, 0);
        counts[PowerUpType.UNDO.ordinal()] = savedState.getInt(KEY_COUNT_UNDO, 0);

        // Restore earned milestones
        earnedMilestones.clear();
        int[] milestonesArray = savedState.getIntArray(KEY_MILESTONES);
        if (milestonesArray != null) {
            for (int m : milestonesArray) {
                earnedMilestones.add(m);
            }
        }

        // Restore snapshot; readFrom returns null on missing keys
        snapshot = BoardSnapshot.readFrom(savedState);

        // Always reset activation state on restore (Requirement 12.7)
        state = ActivationState.IDLE;
        activeType = null;
    }
}
