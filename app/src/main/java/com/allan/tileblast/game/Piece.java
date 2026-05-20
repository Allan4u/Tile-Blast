package com.allan.tileblast.game;

import android.graphics.Color;
import java.util.Random;

public class Piece {
    // 24 piece shapes as int[][] matrices
    public static final int[][][] SHAPES = {
        // L-shapes (8 rotations)
        {{1,0,0},{1,1,1}},
        {{1,1},{1,0},{1,0}},
        {{1,1,1},{0,0,1}},
        {{0,1},{0,1},{1,1}},
        {{0,0,1},{1,1,1}},
        {{1,0},{1,0},{1,1}},
        {{1,1,1},{1,0,0}},
        {{1,1},{0,1},{0,1}},
        // T-shapes (4 rotations)
        {{1,1,1},{0,1,0}},
        {{1,0},{1,1},{1,0}},
        {{0,1,0},{1,1,1}},
        {{0,1},{1,1},{0,1}},
        // S/Z shapes (4 rotations)
        {{0,1,1},{1,1,0}},
        {{1,0},{1,1},{0,1}},
        {{1,1,0},{0,1,1}},
        {{0,1},{1,1},{1,0}},
        // 3x3 square
        {{1,1,1},{1,1,1},{1,1,1}},
        // 2x2 square
        {{1,1},{1,1}},
        // 4x1 vertical
        {{1},{1},{1},{1}},
        // 1x4 horizontal
        {{1,1,1,1}},
        // 3x1 vertical
        {{1},{1},{1}},
        // 1x3 horizontal
        {{1,1,1}},
        // 2x1 vertical
        {{1},{1}},
        // 1x2 horizontal
        {{1,1}},
    };

    // Distribution weights (higher = more common)
    public static final float[] DISTRIBUTION = {
        2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f,  // L-shapes
        1.5f, 1.5f, 1.5f, 1.5f,           // T-shapes
        1f, 1f, 1f, 1f,                     // S/Z shapes
        3f,                                  // 3x3
        6f,                                  // 2x2
        2f, 2f,                              // 4x1, 1x4
        4f, 4f,                              // 3x1, 1x3
        2f, 2f,                              // 2x1, 1x2
    };

    // 6 piece colors (RGB)
    public static final int[][] COLORS = {
        {227, 143, 16},   // Orange
        {186, 19, 38},    // Crimson
        {16, 158, 40},    // Green
        {20, 56, 184},    // Blue
        {101, 19, 148},   // Purple
        {31, 165, 222},   // Cyan
    };

    private static final Random random = new Random();
    private static float totalDistribution = 0f;

    static {
        for (float d : DISTRIBUTION) totalDistribution += d;
    }

    public int[][] matrix;
    public int colorIndex;
    public int shapeIndex;

    public Piece(int shapeIndex, int colorIndex) {
        this.shapeIndex = shapeIndex;
        this.matrix = SHAPES[shapeIndex];
        this.colorIndex = colorIndex;
    }

    public int getRows() { return matrix.length; }
    public int getCols() { return matrix[0].length; }

    public int getBlockCount() {
        int count = 0;
        for (int[] row : matrix)
            for (int cell : row)
                if (cell == 1) count++;
        return count;
    }

    public int getColor() {
        int[] c = COLORS[colorIndex];
        return Color.rgb(c[0], c[1], c[2]);
    }

    public int[] getColorRGB() {
        return COLORS[colorIndex];
    }

    // Beveled border colors (3D effect)
    public int getTopBorderColor() {
        int[] c = COLORS[colorIndex];
        return Color.rgb(
            clamp((int)(c[0] * 214f/131f)),
            clamp((int)(c[1] * 167f/83f)),
            clamp((int)(c[2] * 247f/203f))
        );
    }

    public int getLeftBorderColor() {
        int[] c = COLORS[colorIndex];
        return Color.rgb(
            clamp((int)(c[0] * 164f/131f)),
            clamp((int)(c[1] * 119f/83f)),
            clamp((int)(c[2] * 224f/203f))
        );
    }

    public int getRightBorderColor() {
        int[] c = COLORS[colorIndex];
        return Color.rgb(
            clamp((int)(c[0] * 123f/131f)),
            clamp((int)(c[1] * 69f/83f)),
            clamp((int)(c[2] * 153f/203f))
        );
    }

    public int getBottomBorderColor() {
        int[] c = COLORS[colorIndex];
        return Color.rgb(
            clamp((int)(c[0] * 92f/131f)),
            clamp((int)(c[1] * 43f/83f)),
            clamp((int)(c[2] * 132f/203f))
        );
    }

    // Static border color methods for arbitrary color index
    public static int getTopBorder(int colorIdx) {
        int[] c = COLORS[colorIdx];
        return Color.rgb(clamp((int)(c[0]*214f/131f)), clamp((int)(c[1]*167f/83f)), clamp((int)(c[2]*247f/203f)));
    }
    public static int getLeftBorder(int colorIdx) {
        int[] c = COLORS[colorIdx];
        return Color.rgb(clamp((int)(c[0]*164f/131f)), clamp((int)(c[1]*119f/83f)), clamp((int)(c[2]*224f/203f)));
    }
    public static int getRightBorder(int colorIdx) {
        int[] c = COLORS[colorIdx];
        return Color.rgb(clamp((int)(c[0]*123f/131f)), clamp((int)(c[1]*69f/83f)), clamp((int)(c[2]*153f/203f)));
    }
    public static int getBottomBorder(int colorIdx) {
        int[] c = COLORS[colorIdx];
        return Color.rgb(clamp((int)(c[0]*92f/131f)), clamp((int)(c[1]*43f/83f)), clamp((int)(c[2]*132f/203f)));
    }

    public static int getColorFromIndex(int colorIdx) {
        int[] c = COLORS[colorIdx];
        return Color.rgb(c[0], c[1], c[2]);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    public static Piece getRandomPiece() {
        float pos = random.nextFloat() * totalDistribution;
        int idx = 0;
        for (int i = 0; i < DISTRIBUTION.length; i++) {
            pos -= DISTRIBUTION[i];
            if (pos < 0) { idx = i; break; }
        }
        return new Piece(idx, random.nextInt(COLORS.length));
    }

    public static int getRandomColorIndex() {
        return random.nextInt(COLORS.length);
    }
}
