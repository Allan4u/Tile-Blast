package com.allan.tileblast.game;

public class Board {
    public static final int EMPTY = 0;
    public static final int FILLED = 1;
    public static final int HOVERED = 2;
    public static final int HOVERED_BREAK_FILLED = 3;
    public static final int HOVERED_BREAK_EMPTY = 4;
    public static final int FROZEN = 5;
    public static final int FROZEN_CRACKED = 6;
    public static final int LOCKED = 7;
    public static final int BOMB_TILE = 8;

    private int size;
    private int[][] cells;       // cell state
    private int[][] colors;      // color index per cell
    private int[][] hoverColors; // hover break color index
    private int[][] bombCountdowns; // valid only where cells[y][x] == BOMB_TILE

    public Board(int size) {
        this.size = size;
        cells = new int[size][size];
        colors = new int[size][size];
        hoverColors = new int[size][size];
        bombCountdowns = new int[size][size];
        // Initialize with random colors for empty cells
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                colors[y][x] = Piece.getRandomColorIndex();
    }

    public int getSize() { return size; }
    public int getCell(int x, int y) { return cells[y][x]; }
    public int getCellColor(int x, int y) { return colors[y][x]; }
    public int getHoverColor(int x, int y) { return hoverColors[y][x]; }

    public void setCell(int x, int y, int state, int colorIdx) {
        cells[y][x] = state;
        colors[y][x] = colorIdx;
    }

    /** Returns true for any of the four special-tile states. */
    public static boolean isSpecial(int cellState) {
        return cellState == FROZEN
                || cellState == FROZEN_CRACKED
                || cellState == LOCKED
                || cellState == BOMB_TILE;
    }

    /** Returns true when the given cell-state counts as filled for line-completion checks. */
    public static boolean isLineFillable(int cellState) {
        return cellState == FILLED
                || cellState == FROZEN
                || cellState == FROZEN_CRACKED
                || cellState == LOCKED
                || cellState == BOMB_TILE;
    }

    public int getBombCountdown(int x, int y) { return bombCountdowns[y][x]; }
    public void setBombCountdown(int x, int y, int value) { bombCountdowns[y][x] = value; }

    /**
     * Set a special-tile state at (x, y), preserving the existing color.
     * For BOMB_TILE the countdown is initialized to {@code initialCountdown};
     * for any other special state the countdown slot is cleared to 0.
     */
    public void setSpecial(int x, int y, int specialState, int initialCountdown) {
        cells[y][x] = specialState;
        if (specialState == BOMB_TILE) {
            bombCountdowns[y][x] = initialCountdown;
        } else {
            bombCountdowns[y][x] = 0;
        }
    }

    // Check if a piece can be placed at (px, py)
    public boolean canPlace(Piece piece, int px, int py) {
        for (int dy = 0; dy < piece.getRows(); dy++) {
            for (int dx = 0; dx < piece.getCols(); dx++) {
                if (piece.matrix[dy][dx] == 1) {
                    int bx = px + dx, by = py + dy;
                    if (bx < 0 || bx >= size || by < 0 || by >= size) return false;
                    int s = cells[by][bx];
                    if (s == FILLED || isSpecial(s)) return false;
                }
            }
        }
        return true;
    }

    // Place a piece onto the board
    public void placePiece(Piece piece, int px, int py) {
        for (int dy = 0; dy < piece.getRows(); dy++) {
            for (int dx = 0; dx < piece.getCols(); dx++) {
                if (piece.matrix[dy][dx] == 1) {
                    cells[py + dy][px + dx] = FILLED;
                    colors[py + dy][px + dx] = piece.colorIndex;
                }
            }
        }
    }

    // Set hover preview (piece ghost)
    public void setHover(Piece piece, int px, int py) {
        clearHover();
        // Temporarily mark as hovered
        for (int dy = 0; dy < piece.getRows(); dy++) {
            for (int dx = 0; dx < piece.getCols(); dx++) {
                if (piece.matrix[dy][dx] == 1) {
                    int bx = px + dx, by = py + dy;
                    if (cells[by][bx] == EMPTY) {
                        cells[by][bx] = HOVERED;
                        colors[by][bx] = piece.colorIndex;
                    }
                }
            }
        }
        // Check which lines would break
        updateHoveredBreaks(piece.colorIndex);
    }

    private void updateHoveredBreaks(int pieceColorIdx) {
        boolean[] rowFull = new boolean[size];
        boolean[] colFull = new boolean[size];

        for (int r = 0; r < size; r++) {
            boolean full = true;
            for (int c = 0; c < size; c++) {
                int s = cells[r][c];
                if (!(isLineFillable(s) || s == HOVERED)) { full = false; break; }
            }
            rowFull[r] = full;
        }
        for (int c = 0; c < size; c++) {
            boolean full = true;
            for (int r = 0; r < size; r++) {
                int s = cells[r][c];
                if (!(isLineFillable(s) || s == HOVERED)) { full = false; break; }
            }
            colFull[c] = full;
        }

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (rowFull[r] || colFull[c]) {
                    if (cells[r][c] == FILLED) {
                        cells[r][c] = HOVERED_BREAK_FILLED;
                        hoverColors[r][c] = pieceColorIdx;
                    } else if (cells[r][c] == HOVERED || cells[r][c] == EMPTY) {
                        cells[r][c] = HOVERED_BREAK_EMPTY;
                        hoverColors[r][c] = pieceColorIdx;
                    }
                }
            }
        }
    }

    // Clear all hover states
    public void clearHover() {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (cells[y][x] == HOVERED || cells[y][x] == HOVERED_BREAK_EMPTY) {
                    cells[y][x] = EMPTY;
                } else if (cells[y][x] == HOVERED_BREAK_FILLED) {
                    cells[y][x] = FILLED;
                }
            }
        }
    }

    // Break completed lines, return count
    public int breakLines() {
        boolean[] rowFull = new boolean[size];
        boolean[] colFull = new boolean[size];
        int count = 0;

        for (int r = 0; r < size; r++) {
            boolean full = true;
            for (int c = 0; c < size; c++) {
                if (!isLineFillable(cells[r][c])) { full = false; break; }
            }
            if (full) { rowFull[r] = true; count++; }
        }
        for (int c = 0; c < size; c++) {
            boolean full = true;
            for (int r = 0; r < size; r++) {
                if (!isLineFillable(cells[r][c])) { full = false; break; }
            }
            if (full) { colFull[c] = true; count++; }
        }

        // Apply per-cell transition for cells in cleared rows/columns
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (rowFull[r] || colFull[c]) {
                    applyBreakTransition(c, r);
                }
            }
        }

        return count;
    }

    private void applyBreakTransition(int x, int y) {
        int s = cells[y][x];
        switch (s) {
            case FILLED:
                cells[y][x] = EMPTY;
                break;
            case FROZEN:
                cells[y][x] = FROZEN_CRACKED;
                break;
            case FROZEN_CRACKED:
                cells[y][x] = EMPTY;
                break;
            case LOCKED:
                cells[y][x] = EMPTY;
                break;
            case BOMB_TILE:
                cells[y][x] = EMPTY;
                bombCountdowns[y][x] = 0;
                break;
            default:
                // EMPTY/HOVER states: no change
                break;
        }
    }

    // Check if any piece from hand can be placed
    public boolean canPlaceAny(Piece[] hand) {
        for (Piece piece : hand) {
            if (piece == null) continue;
            for (int y = 0; y <= size - piece.getRows(); y++) {
                for (int x = 0; x <= size - piece.getCols(); x++) {
                    if (canPlace(piece, x, y)) return true;
                }
            }
        }
        return false;
    }

    // Check if the board is completely empty (perfect clear)
    public boolean isEmpty() {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                if (cells[y][x] == FILLED) return false;
        return true;
    }

    // Get valid drop spots for a piece (for optimization)
    public boolean[][] getValidSpots(Piece piece) {
        boolean[][] spots = new boolean[size][size];
        if (piece == null) return spots;
        for (int y = 0; y <= size - piece.getRows(); y++) {
            for (int x = 0; x <= size - piece.getCols(); x++) {
                spots[y][x] = canPlace(piece, x, y);
            }
        }
        return spots;
    }
}
