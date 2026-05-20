package com.allan.tileblast.stats;

/**
 * Defines all 20 achievements available in TileBlast.
 */
public enum AchievementDef {
    // Score-based
    FIRST_GAME("First Game", "Complete 1 game", Category.SCORE, 1),
    CENTURION("Centurion", "Score 100+", Category.SCORE, 100),
    THOUSAND_CLUB("Thousand Club", "Score 1000+", Category.SCORE, 1000),
    FIVE_THOUSAND("Five Thousand", "Score 5000+", Category.SCORE, 5000),
    TEN_THOUSAND("Ten Thousand", "Score 10000+", Category.SCORE, 10000),

    // Combo-based
    COMBO_STARTER("Combo Starter", "Combo x2", Category.COMBO, 2),
    COMBO_MASTER("Combo Master", "Combo x5", Category.COMBO, 5),
    COMBO_LEGEND("Combo Legend", "Combo x10", Category.COMBO, 10),

    // Line-based
    LINE_BREAKER("Line Breaker", "Clear 10 lines", Category.LINES, 10),
    LINE_DESTROYER("Line Destroyer", "Clear 100 lines", Category.LINES, 100),
    LINE_ANNIHILATOR("Line Annihilator", "Clear 1000 lines", Category.LINES, 1000),

    // Games-played
    MARATHON("Marathon", "Play 10 games", Category.GAMES, 10),
    DEDICATED("Dedicated", "Play 50 games", Category.GAMES, 50),
    VETERAN("Veteran", "Play 100 games", Category.GAMES, 100),

    // Special
    SPEED_DEMON("Speed Demon", "Score 500+ in Timed 60s", Category.SPECIAL, 500),
    PERFECT("Perfect", "Achieve a perfect clear", Category.SPECIAL, 1),
    STREAK_3("Streak 3", "Win streak of 3", Category.SPECIAL, 3),
    STREAK_7("Streak 7", "Win streak of 7", Category.SPECIAL, 7),
    PRESTIGE("Prestige", "Prestige once", Category.SPECIAL, 1),
    COMPLETIONIST("Completionist", "Unlock all 19 others", Category.SPECIAL, 19);

    public final String displayName;
    public final String description;
    public final Category category;
    public final int threshold;

    AchievementDef(String displayName, String description, Category category, int threshold) {
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.threshold = threshold;
    }

    public enum Category { SCORE, COMBO, LINES, GAMES, SPECIAL }
}
