package com.allan.tileblast.progression;

/**
 * Unlockable rewards tied to level milestones.
 *
 * Each reward has an unlock level (1-100) and a display name shown in
 * the level-up overlay and other UI surfaces.
 */
public enum Reward {
    EXTRA_HINT(5, "Extra Hint"),
    NEON_PALETTE(10, "Neon Palette"),
    WOOD_SKIN(20, "Wood Skin"),
    EXTRA_POWERUP_SLOT(30, "Extra Power-Up Slot"),
    RETRO_PALETTE(50, "Retro Palette"),
    SPACE_SKIN(75, "Space Skin"),
    MASTER_BADGE(100, "Master Title Badge");

    public final int unlockLevel;
    public final String displayName;

    Reward(int unlockLevel, String displayName) {
        this.unlockLevel = unlockLevel;
        this.displayName = displayName;
    }
}
