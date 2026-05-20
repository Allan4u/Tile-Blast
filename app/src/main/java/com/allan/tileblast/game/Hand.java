package com.allan.tileblast.game;

public class Hand {
    private Piece[] pieces;
    private int size;

    public Hand(int size) {
        this.size = size;
        pieces = new Piece[size];
        refill();
    }

    public void refill() {
        for (int i = 0; i < size; i++) {
            pieces[i] = Piece.getRandomPiece();
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
