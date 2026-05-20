# Implementation Plan: Level Progression & XP System

## Overview

Implement a persistent level progression system for TileBlast. Players earn XP from gameplay (score, combos, perfect clears), advance through 100 levels, unlock rewards at milestones, and can prestige at max level. Built incrementally: data models first, then core logic, persistence, game integration, and finally UI layers.

## Tasks

- [ ] 1. Create Reward enum and LevelManager core class
  - [ ] 1.1 Create the Reward enum
    - Create file `app/src/main/java/com/allan/tileblast/progression/Reward.java`
    - Define enum with entries: EXTRA_HINT(5), NEON_PALETTE(10), WOOD_SKIN(20), EXTRA_POWERUP_SLOT(30), RETRO_PALETTE(50), SPACE_SKIN(75), MASTER_BADGE(100)
    - Each entry has `unlockLevel` (int) and `displayName` (String) fields
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7_

  - [ ] 1.2 Create LevelManager class with XP calculation logic
    - Create file `app/src/main/java/com/allan/tileblast/progression/LevelManager.java`
    - Constructor takes `StorageManager` parameter
    - Implement `calculateGameXP(int finalScore, int[] comboLevels, int perfectClearCount)` using formula: `floor((floor(score/10) + sum(5*comboLevel_i) + 50*perfectClears) * (1.0 + 0.1*prestigeCount))`
    - Implement `awardXP(int finalScore, int[] comboLevels, int perfectClearCount)` that calculates XP, applies to current state, handles multi-level-ups, and caps at level 100
    - Clamp negative scores to 0, handle empty combo arrays gracefully
    - _Requirements: 1.1, 1.2, 2.1, 2.2, 2.3, 3.1, 3.2, 4.1, 4.2, 4.3_

  - [ ] 1.3 Implement level queries and progression logic in LevelManager
    - Implement `getLevel()` returning current level (1-100)
    - Implement `getCurrentXP()` returning XP within current level
    - Implement `getXPForNextLevel()` returning `level * 100`
    - Implement `getProgressRatio()` returning `currentXP / (level * 100)`, or 1.0 at level 100
    - Implement `getNewLevelsReached()` that returns and clears the list of new levels reached after last `awardXP` call
    - Level-up logic: while `currentXP >= xpForNextLevel` and `level < 100`, subtract threshold and increment level
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 7.2, 7.3_

  - [ ] 1.4 Implement prestige logic in LevelManager
    - Implement `getPrestigeCount()`, `getPrestigeMultiplier()` (1.0 + 0.1 * prestigeCount)
    - Implement `canPrestige()` returning true only when level == 100
    - Implement `activatePrestige()`: reset level to 1, currentXP to 0, increment prestige count, preserve unlocked rewards
    - No-op if called below level 100
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

  - [ ] 1.5 Implement reward unlock logic in LevelManager
    - Implement `getUnlockedRewards()` returning all rewards with `unlockLevel <= currentLevel`
    - Implement `isRewardUnlocked(Reward reward)` checking if reward's unlock level is met
    - Rewards persist across prestige resets
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8_

- [ ] 2. Extend StorageManager for progression persistence
  - [ ] 2.1 Add progression persistence methods to StorageManager
    - Add SharedPreferences keys: `player_level`, `player_xp`, `prestige_count`, `unlocked_rewards`
    - Add `saveProgression(int level, int xp, int prestigeCount, List<String> unlockedRewards)` method
    - Add `loadLevel()`, `loadXP()`, `loadPrestigeCount()`, `loadUnlockedRewards()` methods
    - Store unlocked rewards as JSON array of reward enum names
    - Default to level 1, 0 XP, 0 prestige, empty rewards on missing/corrupted data
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6_

  - [ ] 2.2 Wire LevelManager save/load to StorageManager
    - Implement `save()` in LevelManager that calls StorageManager persistence methods
    - Implement `load()` in LevelManager that restores state from StorageManager
    - Call `save()` automatically after `awardXP()` and `activatePrestige()`
    - Call `load()` in LevelManager constructor
    - _Requirements: 11.5, 11.6_

- [ ] 3. Checkpoint - Core logic complete
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 4. Integrate LevelManager into GameView
  - [ ] 4.1 Track combo levels and perfect clears during gameplay in GameView
    - Add `ArrayList<Integer> comboLevelsThisGame` field to track each combo level achieved
    - Add `int perfectClearCount` field to count board clears
    - In `attemptPlacement()`, after `processLineBreak()`, if comboLevel > 0 add it to `comboLevelsThisGame`
    - Detect perfect clear: after `breakLines()`, check if board is completely empty; if so increment `perfectClearCount`
    - Reset both trackers in `setup()`
    - _Requirements: 2.1, 2.2, 3.1, 3.2_

  - [ ] 4.2 Award XP on game over and display level in HUD
    - Add `LevelManager` field to GameView, initialize in `init()` using existing `storageManager`
    - In game over flow (inside `attemptPlacement()` when `gameOver = true`), call `levelManager.awardXP(score, comboLevelsArray, perfectClearCount)`
    - In `drawHUD()`, draw current level text (e.g., "LV.5") in top-left area
    - _Requirements: 1.1, 8.2_

  - [ ] 4.3 Expose level-up data to GameActivity via callback
    - Extend `GameCallback` interface: add `onLevelUp(List<Integer> newLevels, List<Reward> newRewards)`
    - After `awardXP()` in game over, check `getNewLevelsReached()`; if non-empty, determine newly unlocked rewards and call callback
    - _Requirements: 6.1, 6.3_

- [ ] 5. Implement level-up overlay in GameActivity
  - [ ] 5.1 Add level-up overlay UI to GameActivity
    - Implement `onLevelUp` callback in GameActivity
    - Create a full-screen overlay (FrameLayout or custom View) showing "LEVEL UP!" with the new level number
    - If new rewards were unlocked, display reward names below the level number
    - Overlay dismisses on tap
    - Style with existing Silkscreen fonts and gold/white color scheme matching game aesthetic
    - _Requirements: 6.1, 6.2, 6.3_

- [ ] 6. Add progress bar and level display to MainActivity
  - [ ] 6.1 Add level indicator and XP progress bar to main menu layout
    - Modify `activity_main.xml`: add a level text view (e.g., "Level 12") and a horizontal progress bar below it, positioned above the game mode buttons
    - Add prestige star indicator (e.g., "★x2") visible only when prestige count > 0
    - Style with Silkscreen font and game color palette
    - _Requirements: 7.1, 8.1, 8.3_

  - [ ] 6.2 Wire MainActivity to LevelManager for live data
    - Instantiate `LevelManager` in `onCreate()` using `StorageManager`
    - Set progress bar max to `levelManager.getXPForNextLevel()`, progress to `levelManager.getCurrentXP()`
    - Set level text to current level
    - Show/hide prestige stars based on `getPrestigeCount()`
    - Refresh on `onResume()` so returning from a game shows updated progress
    - _Requirements: 7.2, 7.3, 8.1, 8.3_

- [ ] 7. Checkpoint - UI integration complete
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 8. Implement prestige flow
  - [ ] 8.1 Add prestige button and confirmation to MainActivity
    - Add a "PRESTIGE" button to the main menu, visible only when `canPrestige()` returns true (level 100)
    - On tap, show a confirmation dialog explaining: level resets to 1, XP multiplier increases, rewards are kept
    - On confirm, call `levelManager.activatePrestige()` and refresh all UI elements
    - Hide button when prestige is not available
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

- [ ] 9. Final checkpoint - Full feature complete
  - Ensure all tests pass, ask the user if questions arise.

- [ ]* 10. Property-based tests with jqwik
  - [ ]* 10.1 Write property test for XP formula correctness
    - **Property 1: XP Formula Correctness**
    - **Validates: Requirements 1.1, 2.1, 2.2, 2.3, 3.1, 3.2, 4.1, 4.2, 4.3**
    - Generate random scores [0,100000], combo arrays [0-20 elements, values 1-10], perfect clears [0-5], prestige [0-10]
    - Assert `calculateGameXP` matches the formula: `floor((floor(score/10) + sum(5*comboLevel_i) + 50*perfectClears) * (1.0 + 0.1*prestigeCount))`

  - [ ]* 10.2 Write property test for level calculation from cumulative XP
    - **Property 2: Level Calculation from Cumulative XP**
    - **Validates: Requirements 5.1, 5.3, 5.4, 5.5**
    - Generate random XP award sequences (1-50 awards, each 0-5000)
    - Assert resulting level equals highest L in [1,100] where sum of thresholds ≤ total XP

  - [ ]* 10.3 Write property test for progress bar fill ratio
    - **Property 3: Progress Bar Fill Ratio**
    - **Validates: Requirements 7.2, 7.3**
    - Generate random levels [1,100] and valid currentXP values
    - Assert `getProgressRatio()` equals `currentXP / (level * 100)` for levels < 100, and 1.0 at level 100

  - [ ]* 10.4 Write property test for reward unlock set correctness
    - **Property 4: Reward Unlock Set Correctness**
    - **Validates: Requirements 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7**
    - Generate random levels [1,100]
    - Assert unlocked rewards are exactly those with `unlockLevel <= level`

  - [ ]* 10.5 Write property test for prestige reset invariants
    - **Property 5: Prestige Reset Invariants**
    - **Validates: Requirements 10.2, 10.3, 10.4, 10.5**
    - Generate random pre-prestige states (level=100, random rewards, prestige count [0,20])
    - Assert after `activatePrestige()`: level=1, currentXP=0, prestige incremented, rewards preserved

  - [ ]* 10.6 Write property test for persistence round-trip
    - **Property 6: Persistence Round-Trip**
    - **Validates: Requirements 11.1, 11.2, 11.3, 11.4, 11.5**
    - Generate random valid progression states
    - Assert save then load produces equivalent state

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The LevelManager is self-contained with a thin persistence boundary, making it easy to test in isolation
- Combo levels tracked in GameView are the combo multiplier values returned by `ScoreManager.processLineBreak()`
- Perfect clear detection checks if the board has zero filled cells after line breaks

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.4", "1.5"] },
    { "id": 2, "tasks": ["1.3", "2.1"] },
    { "id": 3, "tasks": ["2.2"] },
    { "id": 4, "tasks": ["4.1"] },
    { "id": 5, "tasks": ["4.2", "4.3"] },
    { "id": 6, "tasks": ["5.1", "6.1"] },
    { "id": 7, "tasks": ["6.2"] },
    { "id": 8, "tasks": ["8.1"] },
    { "id": 9, "tasks": ["10.1", "10.2", "10.3", "10.4", "10.5", "10.6"] }
  ]
}
```
