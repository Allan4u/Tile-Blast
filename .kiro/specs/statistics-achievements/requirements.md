# Requirements Document

## Introduction

Statistics & Achievements system for TileBlast that tracks player performance metrics across game modes, awards achievements for milestones, and provides dedicated screens for viewing progress. All data persists locally via SharedPreferences through the existing StorageManager.

## Glossary

- **Statistics_Manager**: Component responsible for tracking, computing, and persisting all player statistics
- **Achievement_Manager**: Component responsible for evaluating achievement unlock conditions and managing achievement state
- **Statistics_Screen**: Activity accessible from the main menu that displays all tracked statistics
- **Achievement_Screen**: Activity accessible from the main menu that displays all achievements in a grid layout
- **Achievement_Notification**: Toast-style popup displayed at the top of the game screen when an achievement is unlocked
- **Win_Streak**: Consecutive sequence of completed games where the final score exceeds 1000
- **Perfect_Clear**: A board state where all cells are empty after a line break
- **Game_Mode**: One of the available play modes (classic, chaos, or timed)
- **StorageManager**: Existing persistence component using SharedPreferences for local data storage
- **GameView**: Existing custom View that renders the game board, hand pieces, and handles gameplay interaction
- **ScoreManager**: Existing component that tracks score and combo level during a single game session

## Requirements

### Requirement 1: Track Games Played

**User Story:** As a player, I want to see how many games I have played per mode and overall, so that I can track my engagement over time.

#### Acceptance Criteria

1. WHEN a game ends, THE Statistics_Manager SHALL increment the total games played counter for the active Game_Mode by 1
2. WHEN a game ends, THE Statistics_Manager SHALL increment the overall total games played counter by 1
3. THE Statistics_Manager SHALL persist the games played counters to StorageManager immediately after each update

### Requirement 2: Track Average Score

**User Story:** As a player, I want to see my average score per game mode, so that I can measure my improvement.

#### Acceptance Criteria

1. WHEN a game ends, THE Statistics_Manager SHALL recalculate the average score for the active Game_Mode using the formula: total cumulative score divided by total games played for that mode
2. THE Statistics_Manager SHALL persist the cumulative score and games played values used for average calculation to StorageManager immediately after each update
3. THE Statistics_Manager SHALL return 0 as the average score WHEN no games have been played for a given Game_Mode

### Requirement 3: Track Best Combo

**User Story:** As a player, I want to see the highest combo level I have ever achieved, so that I can try to beat my personal record.

#### Acceptance Criteria

1. WHEN a combo level is achieved during gameplay, THE Statistics_Manager SHALL compare the combo level to the stored best combo value
2. WHEN the current combo level exceeds the stored best combo value, THE Statistics_Manager SHALL update the best combo value to the current combo level
3. THE Statistics_Manager SHALL persist the best combo value to StorageManager immediately after each update

### Requirement 4: Track Total Lines Cleared

**User Story:** As a player, I want to see how many lines I have cleared across all games, so that I can appreciate my cumulative progress.

#### Acceptance Criteria

1. WHEN lines are cleared during gameplay, THE Statistics_Manager SHALL add the number of lines cleared to the total lines cleared counter
2. THE Statistics_Manager SHALL persist the total lines cleared counter to StorageManager immediately after each update

### Requirement 5: Track Total Play Time

**User Story:** As a player, I want to see how many minutes I have spent playing, so that I can understand my time investment.

#### Acceptance Criteria

1. WHILE a game session is active and not paused, THE Statistics_Manager SHALL accumulate elapsed time in seconds
2. WHEN a game session ends or is paused, THE Statistics_Manager SHALL persist the accumulated play time to StorageManager
3. THE Statistics_Manager SHALL display total play time in whole minutes on the Statistics_Screen

### Requirement 6: Track Win Streak

**User Story:** As a player, I want to see my current and best win streaks, so that I can challenge myself to maintain consistency.

#### Acceptance Criteria

1. WHEN a game ends with a final score greater than 1000, THE Statistics_Manager SHALL increment the current win streak counter by 1
2. WHEN a game ends with a final score of 1000 or less, THE Statistics_Manager SHALL reset the current win streak counter to 0
3. WHEN the current win streak exceeds the stored best win streak, THE Statistics_Manager SHALL update the best win streak to the current win streak value
4. THE Statistics_Manager SHALL persist both win streak values to StorageManager immediately after each update

### Requirement 7: Statistics Screen

**User Story:** As a player, I want to access a statistics screen from the main menu, so that I can review all my tracked performance data in one place.

#### Acceptance Criteria

1. THE Statistics_Screen SHALL be accessible via a button on the main menu
2. THE Statistics_Screen SHALL display total games played per Game_Mode and overall
3. THE Statistics_Screen SHALL display average score per Game_Mode
4. THE Statistics_Screen SHALL display the best combo ever achieved
5. THE Statistics_Screen SHALL display total lines cleared
6. THE Statistics_Screen SHALL display total play time in minutes
7. THE Statistics_Screen SHALL display current win streak and best win streak
8. THE Statistics_Screen SHALL use a scrollable vertical layout with labeled sections for each statistic category

### Requirement 8: Achievement Unlock Evaluation

**User Story:** As a player, I want achievements to unlock automatically when I meet their conditions, so that I am rewarded for reaching milestones.

#### Acceptance Criteria

1. WHEN a game ends, THE Achievement_Manager SHALL evaluate all score-based achievement conditions against the final game score
2. WHEN a combo level is achieved during gameplay, THE Achievement_Manager SHALL evaluate all combo-based achievement conditions against the current combo level
3. WHEN statistics are updated, THE Achievement_Manager SHALL evaluate all cumulative achievement conditions (total games, total lines, win streak)
4. WHEN an achievement condition is met and the achievement is not already unlocked, THE Achievement_Manager SHALL mark the achievement as unlocked and persist the state to StorageManager
5. THE Achievement_Manager SHALL evaluate the Completionist achievement WHEN any other achievement is unlocked

### Requirement 9: Achievement Definitions

**User Story:** As a player, I want a set of 20 achievements covering score, combo, lines, games played, and special milestones, so that I have varied goals to pursue.

#### Acceptance Criteria

1. THE Achievement_Manager SHALL define the following score-based achievements: First Game (complete 1 game), Centurion (score 100 or more in a single game), Thousand Club (score 1000 or more in a single game), Five Thousand (score 5000 or more in a single game), Ten Thousand (score 10000 or more in a single game)
2. THE Achievement_Manager SHALL define the following combo-based achievements: Combo Starter (achieve combo level 2), Combo Master (achieve combo level 5), Combo Legend (achieve combo level 10)
3. THE Achievement_Manager SHALL define the following line-based achievements: Line Breaker (clear 10 lines total), Line Destroyer (clear 100 lines total), Line Annihilator (clear 1000 lines total)
4. THE Achievement_Manager SHALL define the following games-played achievements: Marathon (play 10 games total), Dedicated (play 50 games total), Veteran (play 100 games total)
5. THE Achievement_Manager SHALL define the following special achievements: Speed Demon (score 500 or more in Timed 60s mode), Perfect (achieve a perfect clear), Streak 3 (win streak of 3), Streak 7 (win streak of 7), Prestige (prestige once), Completionist (unlock all other 19 achievements)

### Requirement 10: Achievement Notification

**User Story:** As a player, I want to see a brief notification when I unlock an achievement during gameplay, so that I receive immediate feedback on my accomplishment.

#### Acceptance Criteria

1. WHEN an achievement is unlocked during gameplay, THE Achievement_Notification SHALL display a toast-style popup at the top of the game screen
2. THE Achievement_Notification SHALL display the achievement name and a brief description
3. THE Achievement_Notification SHALL remain visible for 3 seconds before automatically dismissing
4. THE Achievement_Notification SHALL not obstruct core gameplay elements (board, hand pieces)
5. IF multiple achievements are unlocked simultaneously, THEN THE Achievement_Notification SHALL queue notifications and display them sequentially

### Requirement 11: Achievement Screen

**User Story:** As a player, I want to view all achievements in a grid layout, so that I can see my progress and identify which achievements remain locked.

#### Acceptance Criteria

1. THE Achievement_Screen SHALL be accessible via a button on the main menu
2. THE Achievement_Screen SHALL display all 20 achievements in a grid layout
3. THE Achievement_Screen SHALL display unlocked achievements with full color and a checkmark indicator
4. THE Achievement_Screen SHALL display locked achievements with a grayed-out appearance
5. THE Achievement_Screen SHALL display the achievement name and unlock condition for each achievement badge
6. THE Achievement_Screen SHALL display a progress summary showing the count of unlocked achievements out of 20

### Requirement 12: Data Persistence

**User Story:** As a player, I want my statistics and achievements to persist between app sessions, so that I do not lose my progress.

#### Acceptance Criteria

1. THE Statistics_Manager SHALL store all statistics data in SharedPreferences via the existing StorageManager
2. THE Achievement_Manager SHALL store all achievement unlock states in SharedPreferences via the existing StorageManager
3. WHEN the application launches, THE Statistics_Manager SHALL load all previously saved statistics from StorageManager
4. WHEN the application launches, THE Achievement_Manager SHALL load all previously saved achievement states from StorageManager
5. IF stored data is missing or corrupted, THEN THE Statistics_Manager SHALL initialize all statistics to their default values (zero)
6. IF stored data is missing or corrupted, THEN THE Achievement_Manager SHALL initialize all achievements to the locked state
