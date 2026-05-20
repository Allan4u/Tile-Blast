# Requirements Document

## Introduction

The Level Progression & XP System adds a persistent progression layer to TileBlast. Players earn experience points (XP) from gameplay, advance through 100 levels, unlock rewards at milestone levels, and can prestige after reaching the maximum level. This system provides long-term engagement and a sense of accomplishment beyond individual game scores.

## Glossary

- **XP_Engine**: The component responsible for calculating and awarding experience points based on game performance
- **Level_Manager**: The component responsible for tracking player level, determining level-up events, and managing prestige
- **Progress_Bar**: The visual UI element on the main menu that displays current XP progress toward the next level
- **Level_Up_Overlay**: The celebration screen displayed when a player advances to a new level
- **Reward_System**: The component that manages unlockable content tied to specific levels
- **Prestige_Manager**: The component that handles prestige resets and multiplier tracking
- **HUD**: The in-game heads-up display showing score, combo, and level information
- **StorageManager**: The existing SharedPreferences-based persistence layer
- **Combo_Level**: The current combo multiplier tracked by ScoreManager during gameplay
- **Perfect_Clear**: A game event where the entire board is cleared of all filled cells
- **Prestige_Star**: A badge indicator showing how many times a player has prestiged

## Requirements

### Requirement 1: XP Calculation from Game Score

**User Story:** As a player, I want to earn XP based on my game score, so that every game contributes to my progression.

#### Acceptance Criteria

1. WHEN a game ends, THE XP_Engine SHALL award XP equal to the final game score divided by 10, rounded down to the nearest integer
2. WHEN the final game score is less than 10, THE XP_Engine SHALL award 0 XP from the score component

### Requirement 2: XP Bonus from Combos

**User Story:** As a player, I want to earn bonus XP from combos during gameplay, so that skillful play is rewarded with faster progression.

#### Acceptance Criteria

1. WHEN a combo is achieved during gameplay, THE XP_Engine SHALL award bonus XP equal to 5 multiplied by the Combo_Level
2. THE XP_Engine SHALL accumulate combo bonus XP across all combos within a single game session
3. WHEN a game ends, THE XP_Engine SHALL add the total accumulated combo bonus XP to the game XP reward

### Requirement 3: XP Bonus from Perfect Clear

**User Story:** As a player, I want to earn a large XP bonus for clearing the entire board, so that achieving a perfect clear feels rewarding.

#### Acceptance Criteria

1. WHEN a Perfect_Clear occurs during gameplay, THE XP_Engine SHALL award 50 bonus XP
2. WHEN multiple Perfect_Clears occur in a single game, THE XP_Engine SHALL award 50 bonus XP for each occurrence

### Requirement 4: Prestige XP Multiplier

**User Story:** As a player who has prestiged, I want my XP earnings to increase, so that subsequent level progressions feel faster.

#### Acceptance Criteria

1. THE XP_Engine SHALL multiply all earned XP by a prestige multiplier before applying it to the player total
2. THE XP_Engine SHALL calculate the prestige multiplier as 1.0 plus 0.1 multiplied by the prestige count (e.g., prestige count 1 yields multiplier 1.1, prestige count 2 yields multiplier 1.2)
3. WHEN the prestige count is 0, THE XP_Engine SHALL apply a multiplier of 1.0 (no bonus)

### Requirement 5: Level Progression Formula

**User Story:** As a player, I want a clear and predictable leveling curve, so that I understand how much XP I need to reach the next level.

#### Acceptance Criteria

1. THE Level_Manager SHALL require XP equal to the target level multiplied by 100 to advance from the previous level (Level 1 requires 100 XP, Level 2 requires 200 XP, Level 50 requires 5000 XP)
2. THE Level_Manager SHALL support levels from 1 through 100
3. WHEN the player accumulates sufficient XP to reach the next level threshold, THE Level_Manager SHALL advance the player to the next level
4. WHEN the player accumulates XP exceeding the current level threshold, THE Level_Manager SHALL carry over excess XP toward the subsequent level
5. WHEN the player reaches Level 100, THE Level_Manager SHALL stop level advancement until prestige is activated

### Requirement 6: Level Up Notification

**User Story:** As a player, I want to see a celebration when I level up, so that the achievement feels significant.

#### Acceptance Criteria

1. WHEN the player advances to a new level, THE Level_Up_Overlay SHALL display a celebration screen showing the new level number
2. THE Level_Up_Overlay SHALL remain visible until the player dismisses it by tapping
3. WHEN a level-up unlocks a reward, THE Level_Up_Overlay SHALL display the unlocked reward alongside the new level number

### Requirement 7: Progress Bar Display

**User Story:** As a player, I want to see my XP progress on the main menu, so that I know how close I am to the next level.

#### Acceptance Criteria

1. THE Progress_Bar SHALL be displayed on the main menu below the player level indicator
2. THE Progress_Bar SHALL fill proportionally based on current XP progress toward the next level (current XP within level divided by XP required for next level)
3. WHEN the player is at Level 100 with no prestige pending, THE Progress_Bar SHALL display as completely filled

### Requirement 8: Level Display on Main Menu and HUD

**User Story:** As a player, I want to see my current level on the main menu and during gameplay, so that my progression is always visible.

#### Acceptance Criteria

1. THE MainActivity SHALL display the player current level on the main menu screen
2. THE HUD SHALL display the player current level during gameplay
3. WHILE the player has one or more Prestige_Stars, THE MainActivity SHALL display the prestige star count alongside the level

### Requirement 9: Unlock Rewards at Milestone Levels

**User Story:** As a player, I want to unlock new content at specific levels, so that I have goals to work toward.

#### Acceptance Criteria

1. WHEN the player reaches Level 5, THE Reward_System SHALL unlock an extra hint (increasing hints per game from 3 to 4)
2. WHEN the player reaches Level 10, THE Reward_System SHALL unlock the Neon palette
3. WHEN the player reaches Level 20, THE Reward_System SHALL unlock the Wood skin
4. WHEN the player reaches Level 30, THE Reward_System SHALL unlock an extra power-up slot capacity (plus 1 maximum per type)
5. WHEN the player reaches Level 50, THE Reward_System SHALL unlock the Retro palette
6. WHEN the player reaches Level 75, THE Reward_System SHALL unlock the Space skin
7. WHEN the player reaches Level 100, THE Reward_System SHALL unlock the Master title badge
8. THE Reward_System SHALL persist unlocked rewards across game sessions

### Requirement 10: Prestige System

**User Story:** As a player who has reached the maximum level, I want to prestige and start over with a bonus, so that I have continued motivation to play.

#### Acceptance Criteria

1. WHEN the player is at Level 100, THE Prestige_Manager SHALL enable the prestige option
2. WHEN the player activates prestige, THE Prestige_Manager SHALL reset the player level to 1 with 0 current XP
3. WHEN the player activates prestige, THE Prestige_Manager SHALL increment the prestige count by 1
4. WHEN the player activates prestige, THE Prestige_Manager SHALL award a Prestige_Star badge
5. THE Prestige_Manager SHALL preserve all previously unlocked rewards after prestige reset
6. WHILE the player is below Level 100, THE Prestige_Manager SHALL keep the prestige option disabled

### Requirement 11: Data Persistence

**User Story:** As a player, I want my XP, level, and prestige data saved, so that my progress is not lost when I close the app.

#### Acceptance Criteria

1. THE StorageManager SHALL persist the player current XP total
2. THE StorageManager SHALL persist the player current level
3. THE StorageManager SHALL persist the player prestige count
4. THE StorageManager SHALL persist the list of unlocked rewards
5. WHEN the app is launched, THE StorageManager SHALL restore all persisted progression data
6. WHEN XP is awarded or a level-up occurs, THE StorageManager SHALL save the updated state immediately
