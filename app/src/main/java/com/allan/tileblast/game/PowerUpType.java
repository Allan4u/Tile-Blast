package com.allan.tileblast.game;

import java.util.Random;

public enum PowerUpType {
    BOMB,
    LINE_SWEEP,
    ROTATE,
    UNDO;

    /**
     * Returns a uniformly random PowerUpType using the given Random source.
     */
    public static PowerUpType randomFrom(Random r) {
        return values()[r.nextInt(values().length)];
    }
}
