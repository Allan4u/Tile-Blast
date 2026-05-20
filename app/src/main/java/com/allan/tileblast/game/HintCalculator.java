package com.allan.tileblast.game;

/**
 * Standalone scoring logic for the hint system.
 * Evaluates all valid placements and returns the best one.
 * No Android framework dependencies — only uses Board and Piece.
 */
public class HintCalculator {

    public static class PlacementResult {
        public final int pieceIndex;  // index in hand array
        public final int gridX;       // top-left grid X
        public final int gridY;       // top-left grid Y
        public final int score;       // computed score

        public PlacementResult(int pieceIndex, int gridX, int gridY, int score) {
            this.pieceIndex = pieceIndex;
            this.gridX = gridX;
            this.gridY = gridY;
            this.score = score;
        }
    }

    private final Board board;
    private final int boardSize;

    public HintCalculator(Board board) {
        this.board = board;
        this.boardSize = board.getSize();
    }

    /**
     * Evaluate all valid placements for all hand pieces.
     * Returns the highest-scoring PlacementResult, or null if no valid placement exists.
     */
    public PlacementResult findBestPlacement(Piece[] handPieces) {
        PlacementResult best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < handPieces.length; i++) {
            Piece piece = handPieces[i];
            if (piece == null) continue;

            for (int py = 0; py <= boardSize - piece.getRows(); py++) {
                for (int px = 0; px <= boardSize - piece.getCols(); px++) {
                    if (!board.canPlace(piece, px, py)) continue;

                    int score = scorePlacement(piece, px, py);
                    int dist = distanceToCenter(piece, px, py);

                    if (best == null || score > best.score || (score == best.score && dist < bestDistance)) {
                        best = new PlacementResult(i, px, py, score);
                        bestDistance = dist;
                    }
                }
            }
        }

        return best;
    }

    /**
     * Score a single placement.
     * Formula: (lines_completed * 1000) + (adjacent_filled_cells * 10) + (edge_cells * 5)
     */
    public int scorePlacement(Piece piece, int px, int py) {
        return countLinesCompleted(piece, px, py) * 1000
             + countAdjacentFilled(piece, px, py) * 10
             + countEdgeCells(piece, px, py) * 5;
    }

    /**
     * Count how many rows + columns would be completed if piece is placed at (px, py).
     * Simulates placement without mutating board state.
     */
    int countLinesCompleted(Piece piece, int px, int py) {
        int count = 0;

        // Check rows
        for (int r = 0; r < boardSize; r++) {
            boolean rowFull = true;
            for (int c = 0; c < boardSize; c++) {
                boolean occupied = (board.getCell(c, r) == Board.FILLED)
                        || isPieceCovering(piece, px, py, c, r);
                if (!occupied) { rowFull = false; break; }
            }
            if (rowFull) count++;
        }

        // Check columns
        for (int c = 0; c < boardSize; c++) {
            boolean colFull = true;
            for (int r = 0; r < boardSize; r++) {
                boolean occupied = (board.getCell(c, r) == Board.FILLED)
                        || isPieceCovering(piece, px, py, c, r);
                if (!occupied) { colFull = false; break; }
            }
            if (colFull) count++;
        }

        return count;
    }

    /**
     * Count filled cells adjacent (4-directional) to the piece's blocks after placement.
     * Excludes the piece's own blocks from the count.
     */
    int countAdjacentFilled(Piece piece, int px, int py) {
        // Use a boolean grid to track unique adjacent filled cells
        boolean[][] counted = new boolean[boardSize][boardSize];
        int count = 0;

        int[] dx = {0, 0, -1, 1};
        int[] dy = {-1, 1, 0, 0};

        for (int row = 0; row < piece.getRows(); row++) {
            for (int col = 0; col < piece.getCols(); col++) {
                if (piece.matrix[row][col] != 1) continue;
                int bx = px + col;
                int by = py + row;

                for (int d = 0; d < 4; d++) {
                    int nx = bx + dx[d];
                    int ny = by + dy[d];
                    if (nx < 0 || nx >= boardSize || ny < 0 || ny >= boardSize) continue;
                    if (counted[ny][nx]) continue;
                    // Must be a filled cell that is NOT part of the piece itself
                    if (board.getCell(nx, ny) == Board.FILLED && !isPieceCovering(piece, px, py, nx, ny)) {
                        counted[ny][nx] = true;
                        count++;
                    }
                }
            }
        }

        return count;
    }

    /**
     * Count how many of the piece's blocks touch a board edge.
     * A block touches an edge if it's in row 0, row (boardSize-1), col 0, or col (boardSize-1).
     */
    int countEdgeCells(Piece piece, int px, int py) {
        int count = 0;
        for (int row = 0; row < piece.getRows(); row++) {
            for (int col = 0; col < piece.getCols(); col++) {
                if (piece.matrix[row][col] != 1) continue;
                int bx = px + col;
                int by = py + row;
                if (bx == 0 || bx == boardSize - 1 || by == 0 || by == boardSize - 1) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Manhattan distance from placement center to board center (for tiebreaking).
     * Uses integer arithmetic scaled by 2 to avoid floating point.
     */
    int distanceToCenter(Piece piece, int px, int py) {
        // Multiply by 2 to avoid floating point
        int pieceCenterX2 = 2 * px + piece.getCols();  // 2 * (px + cols/2)
        int pieceCenterY2 = 2 * py + piece.getRows();  // 2 * (py + rows/2)
        int boardCenter2 = boardSize - 1;               // 2 * ((boardSize-1)/2)
        return Math.abs(pieceCenterX2 - boardCenter2) + Math.abs(pieceCenterY2 - boardCenter2);
    }

    /**
     * Check if the piece covers cell (cx, cy) when placed at (px, py).
     */
    private boolean isPieceCovering(Piece piece, int px, int py, int cx, int cy) {
        int localX = cx - px;
        int localY = cy - py;
        if (localX < 0 || localX >= piece.getCols() || localY < 0 || localY >= piece.getRows()) {
            return false;
        }
        return piece.matrix[localY][localX] == 1;
    }
}
