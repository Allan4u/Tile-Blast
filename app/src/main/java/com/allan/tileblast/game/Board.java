package com.allan.tileblast.game;

public class Board {
    public static final int EMPTY = 0;
    public static final int FILLED = 1;
    public static final int HOVERED = 2;
    public static final int HOVERED_BREAK_FILLED = 3;
    public static final int HOVERED_BREAK_EMPTY = 4;

    private int size;
    private int[][] cells;       // cell state
    private int[][] colors;      // color index per cell
    private int[][] hoverColors; // hover break color index

    public Board(int size) {
        this.size = size;
        cells = new int[size][size];
        colors = new int[size][size];
        hoverColors = new int[size][size];
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

    // Check if a piece can be placed at (px, py)
    public boolean canPlace(Piece piece, int px, int py) {
        for (int dy = 0; dy < piece.getRows(); dy++) {
            for (int dx = 0; dx < piece.getCols(); dx++) {
                if (piece.matrix[dy][dx] == 1) {
                    int bx = px + dx, by = py + dy;
                    if (bx < 0 || bx >= size || by < 0 || by >= size) return false;
                    if (cells[by][bx] == FILLED) return false;
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
                if (cells[r][c] != FILLED && cells[r][c] != HOVERED) { full = false; break; }
            }
            rowFull[r] = full;
        }
        for (int c = 0; c < size; c++) {
            boolean full = true;
            for (int r = 0; r < size; r++) {
                if (cells[r][c] != FILLED && cells[r][c] != HOVERED) { full = false; break; }
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
                if (cells[r][c] != FILLED) { full = false; break; }
            }
            if (full) { rowFull[r] = true; count++; }
        }
        for (int c = 0; c < size; c++) {
            boolean full = true;
            for (int r = 0; r < size; r++) {
                if (cells[r][c] != FILLED) { full = false; break; }
            }
            if (full) { colFull[c] = true; count++; }
        }

        // Clear lines
        for (int r = 0; r < size; r++) {
            if (rowFull[r]) {
                for (int c = 0; c < size; c++) cells[r][c] = EMPTY;
            }
        }
        for (int c = 0; c < size; c++) {
            if (colFull[c]) {
                for (int r = 0; r < size; r++) cells[r][c] = EMPTY;
            }
        }

        return count;
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
