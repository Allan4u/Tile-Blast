package com.allan.tileblast.game;

public class ScoreManager {
    private int score = 0;
    private int combo = 0;
    private int turnsSinceBreak = 0;
    private int boardSize;

    public ScoreManager(int boardSize) {
        this.boardSize = boardSize;
    }

    // Called when a piece is placed (multiplier defaults to 1.0)
    public int addPlacement(int blockCount) {
        return addPlacement(blockCount, 1.0f);
    }

    // Multiplier-aware placement scoring (Timed Mode)
    public int addPlacement(int blockCount, float multiplier) {
        int delta = Math.round(blockCount * multiplier);
        score += delta;
        return delta;
    }

    // Called after line break check (multiplier defaults to 1.0)
    // Returns: combo level if lines were broken, 0 if not
    public int processLineBreak(int linesBroken) {
        return processLineBreak(linesBroken, 1.0f);
    }

    // Multiplier-aware line-break scoring (Timed Mode)
    public int processLineBreak(int linesBroken, float multiplier) {
        if (linesBroken > 0) {
            turnsSinceBreak = 0;
            combo += linesBroken;
            int rawBonus = Math.round(linesBroken * boardSize * 10f * combo);
            int delta = Math.round(rawBonus * multiplier);
            score += delta;
            return combo;
        } else {
            turnsSinceBreak++;
            if (turnsSinceBreak >= 2) {
                combo = 0;
            }
            return 0;
        }
    }

    // Award perfect clear bonus of 500 points
    public void addPerfectClearBonus() {
        score += 500;
    }

    // Add an arbitrary score amount (used by power-up effects like Bomb)
    public void addScore(int amount) {
        score += amount;
    }

    // Add an end-of-round bonus (e.g., Timed Mode time-remaining bonus)
    public void addBonus(int bonus) {
        if (bonus > 0) score += bonus;
    }

    public int getScore() { return score; }
    public int getCombo() { return combo; }

    public void reset() {
        score = 0;
        combo = 0;
        turnsSinceBreak = 0;
    }

    public void restoreState(int savedScore, int savedCombo) {
        this.score = savedScore;
        this.combo = savedCombo;
        this.turnsSinceBreak = 0;
    }
}
