package com.allan.tileblast.theme;

import android.graphics.Color;

/**
 * Color palettes available for piece rendering.
 *
 * Each palette defines exactly 6 RGB triplets that map to the 6 piece color
 * indices used by {@link com.allan.tileblast.game.Piece}. Palettes have an
 * unlock threshold based on cumulative total score across all games.
 *
 * Validates: Requirements 1.1, 1.2, 5.2
 */
public enum ColorPalette {
    DEFAULT("Default", 0, new int[][]{
        {227, 143, 16},
        {186, 19, 38},
        {16, 158, 40},
        {20, 56, 184},
        {101, 19, 148},
        {31, 165, 222}
    }),
    NEON("Neon", 5000, new int[][]{
        {255, 20, 147},
        {0, 255, 255},
        {57, 255, 20},
        {255, 255, 0},
        {255, 105, 180},
        {138, 43, 226}
    }),
    PASTEL("Pastel", 10000, new int[][]{
        {255, 179, 186},
        {255, 223, 186},
        {255, 255, 186},
        {186, 255, 201},
        {186, 225, 255},
        {218, 191, 255}
    }),
    RETRO("Retro", 25000, new int[][]{
        {229, 96, 36},
        {234, 162, 33},
        {236, 201, 41},
        {72, 142, 87},
        {61, 105, 158},
        {115, 65, 128}
    }),
    DARK("Dark", 50000, new int[][]{
        {139, 0, 0},
        {0, 100, 0},
        {0, 0, 139},
        {75, 0, 130},
        {139, 69, 19},
        {47, 79, 79}
    }),
    OCEAN("Ocean", 100000, new int[][]{
        {0, 119, 190},
        {72, 202, 228},
        {144, 224, 239},
        {0, 150, 136},
        {0, 77, 64},
        {26, 35, 126}
    });

    public final String displayName;
    public final int unlockThreshold;
    private final int[][] colors;

    ColorPalette(String displayName, int unlockThreshold, int[][] colors) {
        if (colors == null || colors.length != 6) {
            throw new IllegalStateException("ColorPalette must define exactly 6 colors");
        }
        for (int[] c : colors) {
            if (c == null || c.length != 3) {
                throw new IllegalStateException("Each palette color must be an RGB triplet");
            }
        }
        this.displayName = displayName;
        this.unlockThreshold = unlockThreshold;
        this.colors = colors;
    }

    /** Returns the raw RGB matrix (6x3). */
    public int[][] getColors() {
        return colors;
    }

    /** Returns an Android color int for the given index. */
    public int getColor(int idx) {
        int[] c = colors[idx];
        return Color.rgb(c[0], c[1], c[2]);
    }

    /** Resolves a palette by its enum name, falling back to DEFAULT for null/unknown ids. */
    public static ColorPalette fromId(String id) {
        if (id == null) return DEFAULT;
        for (ColorPalette p : values()) {
            if (p.name().equals(id)) return p;
        }
        return DEFAULT;
    }
}
