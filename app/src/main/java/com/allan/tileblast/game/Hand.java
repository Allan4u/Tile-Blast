package com.allan.tileblast.game;

import java.util.Random;

public class Hand {
    private Piece[] pieces;
    private int size;
    private Random random;

    public Hand(int size) {
        this(size, null);
    }

    /**
     * Creates a hand seeded with the provided {@link Random}. When non-null,
     * the seeded RNG is used for piece generation, producing deterministic
     * sequences across devices for the same seed.
     */
    public Hand(int size, Random random) {
        this.size = size;
        this.random = random;
        this.pieces = new Piece[size];
        refill();
    }

    public void refill() {
        for (int i = 0; i < size; i++) {
            pieces[i] = (random != null)
                    ? Piece.getRandomPiece(random)
                    : Piece.getRandomPiece();
        }
    }

    public Piece get(int index) {
        return pieces[index];
    }

    public void remove(int index) {
        pieces[index] = null;
    }

    public int getSize() { return size; }

    public boolean isEmpty() {
        for (Piece p : pieces) {
            if (p != null) return false;
        }
        return true;
    }

    public Piece[] getAll() {
        return pieces;
    }
}
