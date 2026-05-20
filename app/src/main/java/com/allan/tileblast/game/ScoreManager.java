package com.allan.tileblast.game;

public class ScoreManager {
    private int score = 0;
    private int combo = 0;
    private int turnsSinceBreak = 0;
    private int boardSize;

    public ScoreManager(int boardSize) {
        this.boardSize = boardSize;
    }

    // Called when a piece is placed
    public int addPlacement(int blockCount) {
        score += blockCount;
        return blockCount;
    }

    // Called after line break check
    // Returns: combo level if lines were broken, 0 if not
    public int processLineBreak(int linesBroken) {
        if (linesBroken > 0) {
            turnsSinceBreak = 0;
            combo += linesBroken;
            int lineBonus = Math.round(linesBroken * boardSize * 10f * combo);
            score += lineBonus;
            return combo;
        } else {
            turnsSinceBreak++;
            if (turnsSinceBreak >= 2) {
                combo = 0;
            }
            return 0;
        }
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
