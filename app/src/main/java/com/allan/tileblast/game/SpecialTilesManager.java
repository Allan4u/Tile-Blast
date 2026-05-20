package com.allan.tileblast.game;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Owns spawn evaluation, mode gating, and bomb decrement/detonation for special tiles
 * (Frozen, Locked, Bomb). Lives alongside {@link GameView} and is the single source of
 * truth for when, where, and which special tile is placed each turn.
 */
public class SpecialTilesManager {
    public static final int BOMB_INITIAL_COUNTDOWN = 5;
    public static final int FROZEN_MIN_TURN = 5;     // strict: spawn only when turn > 5
    public static final int FROZEN_CAP = 5;
    public static final int LOCKED_CAP = 3;
    public static final int BOMB_CAP = 2;
    public static final float FROZEN_PROBABILITY = 0.10f;
    public static final float LOCKED_PROBABILITY = 0.05f;
    public static final float BOMB_PROBABILITY = 0.03f;

    private static final String KEY_TURN_NUMBER = "st_turn_number";
    private static final String KEY_BOMB_COUNTDOWNS = "st_bomb_countdowns";

    public interface DetonationCallback {
        void onChainBombGameOver();
    }

    private final Board board;
    private final String mode;
    private final Random rng;
    private int turnNumber;

    public SpecialTilesManager(Board board, String mode, Random rng) {
        this.board = board;
        this.mode = mode;
        this.rng = rng;
        this.turnNumber = 0;
    }

    /** True for chaos and daily modes (case-insensitive). */
    public boolean isModeEligible() {
        return "chaos".equalsIgnoreCase(mode) || "daily".equalsIgnoreCase(mode);
    }

    public int getTurnNumber() { return turnNumber; }

    public void setTurnNumber(int turnNumber) { this.turnNumber = turnNumber; }

    /**
     * Evaluate spawn rolls for frozen, locked, then bomb. No-op outside eligible modes.
     * Each type rolls independently; up to three specials can spawn in a single turn.
     */
    public void evaluateSpawns(int turnNumber) {
        this.turnNumber = turnNumber;
        if (!isModeEligible()) return;
        trySpawn(Board.FROZEN, FROZEN_CAP, FROZEN_PROBABILITY, 0, true);
        trySpawn(Board.LOCKED, LOCKED_CAP, LOCKED_PROBABILITY, 0, false);
        trySpawn(Board.BOMB_TILE, BOMB_CAP, BOMB_PROBABILITY, BOMB_INITIAL_COUNTDOWN, false);
    }

    /**
     * Decrement every bomb countdown by 1; detonate any that hit 0 by clearing a 3x3
     * bounded by board edges. If a bomb's detonation zone includes another bomb, the
     * callback's {@code onChainBombGameOver} fires and processing short-circuits.
     *
     * @return number of detonations performed
     */
    public int decrementBombsAndDetonate(DetonationCallback cb) {
        if (!isModeEligible()) return 0;
        int size = board.getSize();
        List<int[]> bombCells = new ArrayList<>();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (board.getCell(x, y) == Board.BOMB_TILE) {
                    bombCells.add(new int[]{x, y});
                }
            }
        }
        // Decrement every bomb's countdown
        for (int[] cell : bombCells) {
            int x = cell[0], y = cell[1];
            board.setBombCountdown(x, y, board.getBombCountdown(x, y) - 1);
        }
        int detonations = 0;
        for (int[] cell : bombCells) {
            int bx = cell[0], by = cell[1];
            // Skip if the cell is no longer a bomb (already cleared by a previous detonation)
            if (board.getCell(bx, by) != Board.BOMB_TILE) continue;
            if (board.getBombCountdown(bx, by) > 0) continue;

            boolean chainHit = false;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int x = bx + dx;
                    int y = by + dy;
                    if (x < 0 || x >= size || y < 0 || y >= size) continue;
                    if ((dx != 0 || dy != 0) && board.getCell(x, y) == Board.BOMB_TILE) {
                        chainHit = true;
                    }
                    board.setCell(x, y, Board.EMPTY, board.getCellColor(x, y));
                    board.setBombCountdown(x, y, 0);
                }
            }
            detonations++;
            if (chainHit) {
                if (cb != null) cb.onChainBombGameOver();
                return detonations;
            }
        }
        return detonations;
    }

    public void saveState(Bundle out) {
        out.putInt(KEY_TURN_NUMBER, turnNumber);
        int size = board.getSize();
        int[] flattened = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (board.getCell(x, y) == Board.BOMB_TILE) {
                    flattened[y * size + x] = board.getBombCountdown(x, y);
                } else {
                    flattened[y * size + x] = 0;
                }
            }
        }
        out.putIntArray(KEY_BOMB_COUNTDOWNS, flattened);
    }

    public void restoreState(Bundle in) {
        if (in == null) return;
        turnNumber = in.getInt(KEY_TURN_NUMBER, 0);
        int[] flattened = in.getIntArray(KEY_BOMB_COUNTDOWNS);
        int size = board.getSize();
        if (flattened != null && flattened.length == size * size) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int v = flattened[y * size + x];
                    if (v != 0 && board.getCell(x, y) == Board.BOMB_TILE) {
                        board.setBombCountdown(x, y, v);
                    }
                }
            }
        }
    }

    // ===== Internals =====

    private int countCells(int... states) {
        int size = board.getSize();
        int n = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int s = board.getCell(x, y);
                for (int target : states) {
                    if (s == target) { n++; break; }
                }
            }
        }
        return n;
    }

    private List<int[]> listEmptyCells() {
        int size = board.getSize();
        List<int[]> empties = new ArrayList<>();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (board.getCell(x, y) == Board.EMPTY) {
                    empties.add(new int[]{x, y});
                }
            }
        }
        return empties;
    }

    private void trySpawn(int state, int cap, float probability, int initialCountdown, boolean turnGated) {
        int count;
        if (state == Board.FROZEN) {
            count = countCells(Board.FROZEN, Board.FROZEN_CRACKED);
        } else {
            count = countCells(state);
        }
        if (turnGated && turnNumber <= FROZEN_MIN_TURN) return;
        if (count >= cap) return;
        List<int[]> empties = listEmptyCells();
        if (empties.isEmpty()) return;
        if (rng.nextFloat() >= probability) return;
        int[] pick = empties.get(rng.nextInt(empties.size()));
        board.setSpecial(pick[0], pick[1], state, initialCountdown);
    }
}
