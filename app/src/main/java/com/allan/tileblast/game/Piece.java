package com.allan.tileblast.game;

import android.content.Context;
import android.graphics.Color;

import com.allan.tileblast.AppContext;
import com.allan.tileblast.theme.ColorPalette;
import com.allan.tileblast.theme.ThemeManager;

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

    /**
     * Returns the active palette's 6 RGB triplets. Resolves through the
     * {@link ThemeManager} singleton so callers always see the currently
     * selected palette without having to thread a Context.
     *
     * Falls back to {@link ColorPalette#DEFAULT}'s colors if the AppContext
     * has not been initialized yet (e.g. unit tests, edge cases during
     * startup).
     */
    public static int[][] getColors() {
        Context ctx = AppContext.get();
        if (ctx == null) {
            return ColorPalette.DEFAULT.getColors();
        }
        try {
            return ThemeManager.getInstance(ctx).getActivePalette().getColors();
        } catch (Throwable t) {
            return ColorPalette.DEFAULT.getColors();
        }
    }

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
        int[] c = getColors()[colorIndex];
        return Color.rgb(c[0], c[1], c[2]);
    }

    public int[] getColorRGB() {
        return getColors()[colorIndex];
    }

    // Beveled border colors (3D effect)
    public int getTopBorderColor() {
        int[] c = getColors()[colorIndex];
        return Color.rgb(
            clamp((int)(c[0] * 214f/131f)),
            clamp((int)(c[1] * 167f/83f)),
            clamp((int)(c[2] * 247f/203f))
        );
    }

    public int getLeftBorderColor() {
        int[] c = getColors()[colorIndex];
        return Color.rgb(
            clamp((int)(c[0] * 164f/131f)),
            clamp((int)(c[1] * 119f/83f)),
            clamp((int)(c[2] * 224f/203f))
        );
    }

    public int getRightBorderColor() {
        int[] c = getColors()[colorIndex];
        return Color.rgb(
            clamp((int)(c[0] * 123f/131f)),
            clamp((int)(c[1] * 69f/83f)),
            clamp((int)(c[2] * 153f/203f))
        );
    }

    public int getBottomBorderColor() {
        int[] c = getColors()[colorIndex];
        return Color.rgb(
            clamp((int)(c[0] * 92f/131f)),
            clamp((int)(c[1] * 43f/83f)),
            clamp((int)(c[2] * 132f/203f))
        );
    }

    // Static border color methods for arbitrary color index
    public static int getTopBorder(int colorIdx) {
        int[] c = getColors()[colorIdx];
        return Color.rgb(clamp((int)(c[0]*214f/131f)), clamp((int)(c[1]*167f/83f)), clamp((int)(c[2]*247f/203f)));
    }
    public static int getLeftBorder(int colorIdx) {
        int[] c = getColors()[colorIdx];
        return Color.rgb(clamp((int)(c[0]*164f/131f)), clamp((int)(c[1]*119f/83f)), clamp((int)(c[2]*224f/203f)));
    }
    public static int getRightBorder(int colorIdx) {
        int[] c = getColors()[colorIdx];
        return Color.rgb(clamp((int)(c[0]*123f/131f)), clamp((int)(c[1]*69f/83f)), clamp((int)(c[2]*153f/203f)));
    }
    public static int getBottomBorder(int colorIdx) {
        int[] c = getColors()[colorIdx];
        return Color.rgb(clamp((int)(c[0]*92f/131f)), clamp((int)(c[1]*43f/83f)), clamp((int)(c[2]*132f/203f)));
    }

    public static int getColorFromIndex(int colorIdx) {
        int[] c = getColors()[colorIdx];
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
        return new Piece(idx, random.nextInt(6));
    }

    /**
     * Generates a random piece using the provided {@link Random} instance.
     * Used for deterministic modes (e.g. Daily Challenge) where a seeded
     * Random must drive piece selection.
     */
    public static Piece getRandomPiece(Random rng) {
        float pos = rng.nextFloat() * totalDistribution;
        int idx = 0;
        for (int i = 0; i < DISTRIBUTION.length; i++) {
            pos -= DISTRIBUTION[i];
            if (pos < 0) { idx = i; break; }
        }
        return new Piece(idx, rng.nextInt(6));
    }

    public static int getRandomColorIndex() {
        return random.nextInt(6);
    }
}
