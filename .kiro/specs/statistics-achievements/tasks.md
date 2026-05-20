# Implementation Plan: Statistics & Achievements

## Overview

Incrementally build the statistics tracking and achievement system for TileBlast. Start with the data layer (`StatisticsManager`, `AchievementDef`, `AchievementManager`), then add the notification queue to `GameView`, create the two new Activities, wire buttons into `MainActivity`, and finally integrate hooks into the gameplay loop.

## Tasks

- [ ] 1. Create StatisticsManager with persistence
  - [ ] 1.1 Create `StatisticsManager.java` in `com.allan.tileblast.stats` package
    - Implement constructor accepting `Context`, obtain SharedPreferences via `context.getSharedPreferences("tile_blast_prefs", Context.MODE_PRIVATE)`
    - Implement `recordGameEnd(String mode, int finalScore)`: increment mode-specific and total games played, accumulate score for average, update win streak (score > 1000 increments, else resets), persist immediately
    - Implement `recordCombo(int comboLevel)`: compare with stored best combo, update if higher, persist
    - Implement `recordLinesCleared(int count)`: add to total lines counter, persist
    - Implement `recordPlayTime(long sessionSeconds)`: add to total play time, persist
    - Implement query methods: `getGamesPlayed(String mode)`, `getTotalGamesPlayed()`, `getAverageScore(String mode)` (return 0 if no games), `getBestCombo()`, `getTotalLinesCleared()`, `getTotalPlayTimeSeconds()`, `getCurrentWinStreak()`, `getBestWinStreak()`
    - Use SharedPreferences keys prefixed with `stats_` as defined in design (e.g., `stats_games_classic`, `stats_score_classic`, `stats_best_combo`, etc.)
    - Handle missing/corrupt data by defaulting to 0
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 4.1, 4.2, 5.1, 5.2, 6.1, 6.2, 6.3, 6.4, 12.1, 12.3, 12.5_

  - [ ]* 1.2 Write property tests for StatisticsManager
    - **Property 1: Games played counter invariant** — For any sequence of N game-end events for a mode, mode counter == N and total == sum of all mode counters
    - **Property 2: Average score formula correctness** — Average == cumulative sum / games played (integer division), or 0 if empty
    - **Property 3: Best combo is maximum of all observed combos** — Best combo == max of all recorded combo levels
    - **Property 4: Lines cleared accumulation** — Total lines == sum of all recorded line counts
    - **Property 5: Win streak correctness** — Current streak == longest suffix of scores > 1000; best streak == longest contiguous subsequence > 1000
    - **Validates: Requirements 1.1, 1.2, 2.1, 2.3, 3.1, 3.2, 4.1, 6.1, 6.2, 6.3**

- [ ] 2. Create AchievementDef enum and AchievementManager
  - [ ] 2.1 Create `AchievementDef.java` enum in `com.allan.tileblast.stats` package
    - Define all 20 achievements with fields: `displayName`, `description`, `category`, `threshold`
    - Define inner enum `Category { SCORE, COMBO, LINES, GAMES, SPECIAL }`
    - Include: FIRST_GAME, CENTURION, THOUSAND_CLUB, FIVE_THOUSAND, TEN_THOUSAND, COMBO_STARTER, COMBO_MASTER, COMBO_LEGEND, LINE_BREAKER, LINE_DESTROYER, LINE_ANNIHILATOR, MARATHON, DEDICATED, VETERAN, SPEED_DEMON, PERFECT, STREAK_3, STREAK_7, PRESTIGE, COMPLETIONIST
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [ ] 2.2 Create `AchievementManager.java` in `com.allan.tileblast.stats` package
    - Implement constructor accepting `Context`, load unlock states from SharedPreferences (keys `ach_<ENUM_NAME>`, boolean, default false)
    - Implement `evaluateScore(int finalScore, String mode)`: check FIRST_GAME (always on game end), CENTURION, THOUSAND_CLUB, FIVE_THOUSAND, TEN_THOUSAND against finalScore; check SPEED_DEMON if mode is "timed" and score >= 500. Return list of newly unlocked names.
    - Implement `evaluateCombo(int comboLevel)`: check COMBO_STARTER, COMBO_MASTER, COMBO_LEGEND. Return newly unlocked.
    - Implement `evaluateCumulative(StatisticsManager stats)`: check LINE_BREAKER/DESTROYER/ANNIHILATOR against `getTotalLinesCleared()`, MARATHON/DEDICATED/VETERAN against `getTotalGamesPlayed()`, STREAK_3/STREAK_7 against `getCurrentWinStreak()` and `getBestWinStreak()`. Return newly unlocked.
    - Implement `evaluatePerfectClear()`: unlock PERFECT. Return newly unlocked.
    - Implement `evaluatePrestige()`: unlock PRESTIGE. Return newly unlocked.
    - Each evaluate method: check if already unlocked (skip), check threshold, persist unlock, then check Completionist (if 19 others unlocked, unlock it too)
    - Implement query methods: `isUnlocked(AchievementDef)`, `getUnlockedCount()`, `getAllAchievements()`
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 12.2, 12.4, 12.6_

  - [ ]* 2.3 Write property tests for AchievementManager
    - **Property 6: Achievement evaluation correctness** — For any stat values, the set of unlocked achievements is exactly those whose threshold is met
    - **Property 7: Achievement unlock idempotence** — Calling evaluate multiple times after condition is met does not change state after first unlock
    - **Property 8: Corrupt/missing data defaults to locked** — Missing SharedPreferences keys result in all achievements locked
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 12.5, 12.6**

- [ ] 3. Checkpoint - Core data layer
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 4. Add achievement notification queue to GameView
  - [ ] 4.1 Add notification queue fields and rendering to `GameView.java`
    - Add fields: `LinkedList<String> achievementQueue`, `String currentNotification`, `long notificationEndTime`
    - Add `enqueueAchievement(String name)` method
    - In `onDraw`, after overlays: if `currentNotification` is null and queue is non-empty, dequeue next, set `notificationEndTime = System.currentTimeMillis() + 3000`
    - Draw a gold banner at top of screen (below HUD) with achievement name text, semi-transparent background
    - After 3 seconds, clear `currentNotification` and check queue for next
    - Ensure banner does not obstruct board or hand pieces (position above grid area)
    - Call `invalidate()` while notification is active to animate dismissal
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

- [ ] 5. Create StatisticsActivity
  - [ ] 5.1 Create `activity_statistics.xml` layout
    - Scrollable vertical layout with labeled sections
    - Sections: Games Played (per mode + total), Average Score (per mode), Best Combo, Total Lines Cleared, Total Play Time, Win Streaks (current + best)
    - Use dark theme consistent with existing app (black background, white/gold text)
    - Include a back/close button or use ActionBar back navigation
    - _Requirements: 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8_

  - [ ] 5.2 Create `StatisticsActivity.java` in `com.allan.tileblast`
    - Instantiate `StatisticsManager` with context
    - Populate all TextViews with statistics values from StatisticsManager query methods
    - Display play time formatted as whole minutes
    - Register in `AndroidManifest.xml`
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

- [ ] 6. Create AchievementActivity
  - [ ] 6.1 Create `activity_achievements.xml` layout
    - Grid layout (e.g., GridLayout or RecyclerView with GridLayoutManager) displaying 20 achievement badges
    - Each badge shows: achievement name, description/condition, locked/unlocked state
    - Unlocked: full color with checkmark indicator
    - Locked: grayed-out appearance
    - Progress summary at top: "X / 20 Unlocked"
    - Dark theme consistent with app
    - _Requirements: 11.2, 11.3, 11.4, 11.5, 11.6_

  - [ ] 6.2 Create `AchievementActivity.java` in `com.allan.tileblast`
    - Instantiate `AchievementManager` with context
    - Populate grid with all 20 achievements from `getAllAchievements()`
    - Style each badge based on `isUnlocked()` state
    - Display unlocked count via `getUnlockedCount()`
    - Register in `AndroidManifest.xml`
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6_

- [ ] 7. Checkpoint - UI screens complete
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 8. Add Statistics and Achievements buttons to MainActivity
  - [ ] 8.1 Update `activity_main.xml` and `MainActivity.java`
    - Add a "Statistics" button to the main menu layout (below High Scores, same style as existing buttons)
    - Add an "Achievements" button to the main menu layout (below Statistics)
    - Create drawable backgrounds `btn_statistics.xml` and `btn_achievements.xml` matching existing button style
    - Wire click listeners in `MainActivity.java` to launch `StatisticsActivity` and `AchievementActivity`
    - Apply font styling consistent with existing buttons
    - _Requirements: 7.1, 11.1_

- [ ] 9. Integrate statistics and achievement hooks into GameView
  - [ ] 9.1 Add StatisticsManager and AchievementManager to GameView
    - Instantiate both managers in `init(Context)` method
    - Add `sessionStartTime` field, set in `setup()` to `System.currentTimeMillis()`
    - _Requirements: 12.3, 12.4_

  - [ ] 9.2 Add integration hooks in `attemptPlacement()` method
    - After `board.breakLines()`: if `linesBroken > 0`, call `statisticsManager.recordLinesCleared(linesBroken)`
    - After lines cleared: if `board.isEmpty()`, call `achievementManager.evaluatePerfectClear()` and enqueue results
    - After combo: if `comboLevel >= 2`, call `statisticsManager.recordCombo(comboLevel)` and `achievementManager.evaluateCombo(comboLevel)`, enqueue results
    - In game-over block: call `statisticsManager.recordGameEnd(modeName, scoreManager.getScore())`, calculate elapsed play time and call `statisticsManager.recordPlayTime(elapsed)`, call `achievementManager.evaluateScore(...)` and `achievementManager.evaluateCumulative(statisticsManager)`, enqueue all results
    - _Requirements: 1.1, 1.2, 2.1, 3.1, 4.1, 5.2, 6.1, 8.1, 8.2, 8.3, 10.1_

  - [ ] 9.3 Add `Board.isEmpty()` helper method
    - Add method to `Board.java` that returns true if all cells are `EMPTY`
    - _Requirements: 9.5 (Perfect clear detection)_

- [ ] 10. Final checkpoint - Full integration
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- All persistence uses the same `tile_blast_prefs` SharedPreferences file as existing StorageManager, with `stats_` and `ach_` key prefixes to avoid collisions
- `StatisticsManager` and `AchievementManager` are plain Java classes (no singletons) — each Activity/GameView creates its own instance reading from the same SharedPreferences

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1"] },
    { "id": 1, "tasks": ["1.2", "2.2"] },
    { "id": 2, "tasks": ["2.3", "4.1", "9.3"] },
    { "id": 3, "tasks": ["5.1", "6.1", "8.1"] },
    { "id": 4, "tasks": ["5.2", "6.2", "9.1"] },
    { "id": 5, "tasks": ["9.2"] }
  ]
}
```
