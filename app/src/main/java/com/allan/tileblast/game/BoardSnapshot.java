package com.allan.tileblast.game;

import android.os.Bundle;

/**
 * Immutable snapshot of the game state (board, hand, score) used by the Undo power-up.
 * Captures deep copies of all mutable state so that mutations to the live objects
 * between capture and restore do not affect the snapshot.
 */
public final class BoardSnapshot {

    final int[][] cells;
    final int[][] colors;
    final int[] handShapes;       // -1 for null slots
    final int[] handColors;
    final int[][][] handMatrices; // explicit matrix per slot to preserve rotation; null entry for empty slots
    final int score;
    final int combo;

    private BoardSnapshot(int[][] cells, int[][] colors,
                          int[] handShapes, int[] handColors, int[][][] handMatrices,
                          int score, int combo) {
        this.cells = cells;
        this.colors = colors;
        this.handShapes = handShapes;
        this.handColors = handColors;
        this.handMatrices = handMatrices;
        this.score = score;
        this.combo = combo;
    }

    /**
     * Deep-copies the current state of the board, hand, and score manager into an immutable snapshot.
     */
    public static BoardSnapshot capture(Board board, Hand hand, ScoreManager scoreManager) {
        int size = board.getSize();

        // Deep-copy board cells and colors
        int[][] cellsCopy = new int[size][size];
        int[][] colorsCopy = new int[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                cellsCopy[y][x] = board.getCell(x, y);
                colorsCopy[y][x] = board.getCellColor(x, y);
            }
        }

        // Deep-copy hand pieces
        int handSize = hand.getSize();
        int[] shapes = new int[handSize];
        int[] handColorsCopy = new int[handSize];
        int[][][] matrices = new int[handSize][][];

        for (int i = 0; i < handSize; i++) {
            Piece p = hand.get(i);
            if (p == null) {
                shapes[i] = -1;
                handColorsCopy[i] = -1;
                matrices[i] = null;
            } else {
                shapes[i] = p.shapeIndex;
                handColorsCopy[i] = p.colorIndex;
                // Deep-copy the active matrix (may be rotated)
                int rows = p.getRows();
                int cols = p.getCols();
                int[][] matCopy = new int[rows][cols];
                for (int r = 0; r < rows; r++) {
                    System.arraycopy(p.matrix[r], 0, matCopy[r], 0, cols);
                }
                matrices[i] = matCopy;
            }
        }

        return new BoardSnapshot(cellsCopy, colorsCopy, shapes, handColorsCopy, matrices,
                scoreManager.getScore(), scoreManager.getCombo());
    }

    /**
     * Writes the captured snapshot values back into the live board, hand, and score manager.
     */
    public void restoreInto(Board board, Hand hand, ScoreManager scoreManager) {
        int size = board.getSize();

        // Restore board cells and colors
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                board.setCell(x, y, cells[y][x], colors[y][x]);
            }
        }

        // Restore hand pieces
        Piece[] pieces = hand.getAll();
        for (int i = 0; i < handShapes.length && i < pieces.length; i++) {
            if (handShapes[i] == -1) {
                pieces[i] = null;
            } else {
                Piece p = new Piece(handShapes[i], handColors[i]);
                // Restore the captured matrix (preserves rotation state)
                if (handMatrices[i] != null) {
                    int rows = handMatrices[i].length;
                    int cols = handMatrices[i][0].length;
                    int[][] matCopy = new int[rows][cols];
                    for (int r = 0; r < rows; r++) {
                        System.arraycopy(handMatrices[i][r], 0, matCopy[r], 0, cols);
                    }
                    p.matrix = matCopy;
                }
                pieces[i] = p;
            }
        }

        // Restore score and combo
        scoreManager.restoreState(score, combo);
    }

    /**
     * Serializes this snapshot into the given Bundle using the pu_snap_* keys.
     */
    public void writeTo(Bundle out) {
        out.putBoolean("pu_snap_present", true);

        int size = cells.length;
        out.putInt("pu_snap_size", size);

        // Flatten cells and colors to row-major 1D arrays
        int[] flatCells = new int[size * size];
        int[] flatColors = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                flatCells[y * size + x] = cells[y][x];
                flatColors[y * size + x] = colors[y][x];
            }
        }
        out.putIntArray("pu_snap_cells", flatCells);
        out.putIntArray("pu_snap_colors", flatColors);

        // Hand data
        out.putIntArray("pu_snap_hand_shapes", handShapes);
        out.putIntArray("pu_snap_hand_colors", handColors);

        // Per-slot matrices
        for (int i = 0; i < handShapes.length; i++) {
            if (handMatrices[i] != null) {
                int rows = handMatrices[i].length;
                int cols = handMatrices[i][0].length;
                out.putInt("pu_snap_hand_rows_" + i, rows);
                out.putInt("pu_snap_hand_cols_" + i, cols);
                int[] flat = new int[rows * cols];
                for (int r = 0; r < rows; r++) {
                    System.arraycopy(handMatrices[i][r], 0, flat, r * cols, cols);
                }
                out.putIntArray("pu_snap_hand_matrix_" + i, flat);
            } else {
                out.putInt("pu_snap_hand_rows_" + i, 0);
                out.putInt("pu_snap_hand_cols_" + i, 0);
            }
        }

        out.putInt("pu_snap_score", score);
        out.putInt("pu_snap_combo", combo);
    }

    /**
     * Reads a snapshot from the given Bundle. Returns null if pu_snap_present is absent or false,
     * or if required keys are missing or inconsistent.
     */
    public static BoardSnapshot readFrom(Bundle in) {
        if (in == null || !in.getBoolean("pu_snap_present", false)) {
            return null;
        }

        int size = in.getInt("pu_snap_size", 0);
        if (size <= 0) return null;

        int[] flatCells = in.getIntArray("pu_snap_cells");
        int[] flatColors = in.getIntArray("pu_snap_colors");
        if (flatCells == null || flatColors == null) return null;
        if (flatCells.length != size * size || flatColors.length != size * size) return null;

        // Unflatten to 2D arrays
        int[][] cellsArr = new int[size][size];
        int[][] colorsArr = new int[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                cellsArr[y][x] = flatCells[y * size + x];
                colorsArr[y][x] = flatColors[y * size + x];
            }
        }

        int[] shapes = in.getIntArray("pu_snap_hand_shapes");
        int[] handColorsArr = in.getIntArray("pu_snap_hand_colors");
        if (shapes == null || handColorsArr == null) return null;
        if (shapes.length != handColorsArr.length) return null;

        int handSize = shapes.length;
        int[][][] matrices = new int[handSize][][];
        for (int i = 0; i < handSize; i++) {
            int rows = in.getInt("pu_snap_hand_rows_" + i, 0);
            int cols = in.getInt("pu_snap_hand_cols_" + i, 0);
            if (rows > 0 && cols > 0) {
                int[] flat = in.getIntArray("pu_snap_hand_matrix_" + i);
                if (flat == null || flat.length != rows * cols) {
                    // Inconsistent data - use fallback from SHAPES if possible
                    if (shapes[i] >= 0 && shapes[i] < Piece.SHAPES.length) {
                        matrices[i] = deepCopyMatrix(Piece.SHAPES[shapes[i]]);
                    } else {
                        matrices[i] = null;
                    }
                } else {
                    int[][] mat = new int[rows][cols];
                    for (int r = 0; r < rows; r++) {
                        System.arraycopy(flat, r * cols, mat[r], 0, cols);
                    }
                    matrices[i] = mat;
                }
            } else {
                matrices[i] = null;
            }
        }

        int snapScore = in.getInt("pu_snap_score", 0);
        int snapCombo = in.getInt("pu_snap_combo", 0);

        return new BoardSnapshot(cellsArr, colorsArr, shapes, handColorsArr, matrices,
                snapScore, snapCombo);
    }

    /** Returns the board size captured in this snapshot. */
    public int getBoardSize() {
        return cells.length;
    }

    private static int[][] deepCopyMatrix(int[][] src) {
        int[][] copy = new int[src.length][];
        for (int r = 0; r < src.length; r++) {
            copy[r] = new int[src[r].length];
            System.arraycopy(src[r], 0, copy[r], 0, src[r].length);
        }
        return copy;
    }
}
