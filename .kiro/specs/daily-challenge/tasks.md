# Implementation Plan: Daily Challenge

## Overview

Implement a deterministic daily puzzle mode for TileBlast. A date-based seed (YYYYMMDD) drives a `java.util.Random` instance that produces identical piece sequences for all players on the same day. Players aim for a 2000-point target score to earn a star, track progress on a 30-day calendar, and build streaks for rewards. The implementation builds incrementally: core engine → storage → streak logic → seeded Random integration into Hand/GameView → calendar UI → activity → main menu wiring.

## Tasks

- [ ] 1. Create DailyChallengeEngine and Piece seeded Random support
  - [ ] 1.1 Create `DailyChallengeEngine` class in `com.allan.tileblast.daily` package
    - Create file `app/src/main/java/com/allan/tileblast/daily/DailyChallengeEngine.java`
    - Implement constants: `TARGET_SCORE = 2000`, `BOARD_SIZE = 8`, `HAND_SIZE = 3`
    - Implement `generateSeed()` returning YYYYMMDD as long from `LocalDate.now()`
    - Implement `generateSeed(LocalDate date)` overload for testability
    - Implement `createRandom(long seed)` returning `new java.util.Random(seed)`
    - _Requirements: 1.1, 1.2, 1.5, 2.4, 10.1, 10.2_

  - [ ] 1.2 Add `getRandomPiece(Random rng)` overload to `Piece.java`
    - Add a new static method `public static Piece getRandomPiece(Random rng)` that uses the provided Random for shape distribution and color selection instead of the static `random` field
    - Existing `getRandomPiece()` remains unchanged (backward compatible)
    - _Requirements: 1.3, 1.4, 10.5_

  - [ ]* 1.3 Write property test: Seed generation produces YYYYMMDD integer
    - **Property 1: Seed generation produces YYYYMMDD integer**
    - **Validates: Requirements 1.1**
    - Use jqwik to generate arbitrary `LocalDate` values, verify `generateSeed(date)` equals `year * 10000 + month * 100 + day`

  - [ ]* 1.4 Write property test: Deterministic piece sequence
    - **Property 2: Deterministic piece sequence**
    - **Validates: Requirements 1.3, 1.4, 10.3, 10.5**
    - Use jqwik to generate arbitrary long seeds, produce two piece sequences of length N using `new Random(seed)` + `Piece.getRandomPiece(rng)`, assert identical shape and color at every position

- [ ] 2. Implement ChallengeStorage
  - [ ] 2.1 Create `ChallengeStorage` class in `com.allan.tileblast.daily` package
    - Create file `app/src/main/java/com/allan/tileblast/daily/ChallengeStorage.java`
    - Use SharedPreferences (`daily_challenge_prefs`) for persistence
    - Implement `recordScore(String dateKey, int score)` — stores max of existing and new score
    - Implement `getScore(String dateKey)` — returns stored score or 0
    - Implement `hasStar(String dateKey)` and `awardStar(String dateKey)`
    - Implement `getStreakCount()` / `setStreakCount(int count)`
    - Implement `isRewardClaimed(StreakReward)` / `claimReward(StreakReward)`
    - Implement `getCalendarEntries(LocalDate today)` returning last 30 `DayEntry` objects
    - All getters return defaults (0, false, empty) on parse failure — no exceptions
    - _Requirements: 2.3, 3.1, 3.2, 5.4, 6.5, 8.1, 8.2, 8.3, 8.4, 8.5_

  - [ ] 2.2 Create `DayEntry` data class in `com.allan.tileblast.daily` package
    - Create file `app/src/main/java/com/allan/tileblast/daily/DayEntry.java`
    - Fields: `String dateKey`, `int score`, `boolean starred`, `boolean isToday`
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [ ] 2.3 Create `StreakReward` enum in `com.allan.tileblast.daily` package
    - Create file `app/src/main/java/com/allan/tileblast/daily/StreakReward.java`
    - Values: `POWER_UP_3DAY(3)`, `THEME_7DAY(7)`, `XP_BONUS_14DAY(14)` with `public final int threshold`
    - _Requirements: 6.1, 6.2, 6.3_

  - [ ]* 2.4 Write property test: Best score retention
    - **Property 4: Best score retention**
    - **Validates: Requirements 3.1, 3.2**
    - Use jqwik to generate arbitrary sequences of scores for the same dateKey, verify `getScore(dateKey)` returns the maximum

  - [ ]* 2.5 Write property test: Persistence round-trip
    - **Property 7: Persistence round-trip**
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4**
    - Use jqwik to generate arbitrary state (scores map, stars set, streak count, claimed rewards), serialize to SharedPreferences, read back, assert equivalence

  - [ ]* 2.6 Write property test: Corrupted data resilience
    - **Property 8: Corrupted data resilience**
    - **Validates: Requirements 8.5**
    - Use jqwik to generate arbitrary malformed strings, inject into SharedPreferences, verify getters return defaults without throwing

- [ ] 3. Implement StreakTracker
  - [ ] 3.1 Create `StreakTracker` class in `com.allan.tileblast.daily` package
    - Create file `app/src/main/java/com/allan/tileblast/daily/StreakTracker.java`
    - Constructor takes `ChallengeStorage`
    - Implement `calculateStreak()` — iterates backwards from today counting consecutive starred days; resets to 0 if yesterday unstarred
    - Implement `getCurrentStreak()` — returns persisted streak count
    - Implement `checkRewards()` — returns list of newly-reached `StreakReward` values (checks `isRewardClaimed` to avoid duplicates, calls `claimReward` for new ones)
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 6.1, 6.2, 6.3, 6.4, 6.5_

  - [ ]* 3.2 Write property test: Streak calculation
    - **Property 5: Streak calculation**
    - **Validates: Requirements 5.1, 5.2, 5.3**
    - Use jqwik to generate arbitrary boolean arrays (starred days), verify `calculateStreak()` returns trailing-true count from most recent day

  - [ ]* 3.3 Write property test: Reward claim idempotence
    - **Property 6: Reward claim idempotence**
    - **Validates: Requirements 6.5**
    - Use jqwik to generate streak histories with claimed sets, call `checkRewards()` multiple times, verify no duplicate awards

  - [ ]* 3.4 Write property test: Star threshold correctness
    - **Property 3: Star threshold correctness**
    - **Validates: Requirements 2.2**
    - Use jqwik to generate arbitrary int scores, verify star awarded iff `score >= 2000`

- [ ] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Integrate seeded Random into Hand and GameView
  - [ ] 5.1 Add seeded Random constructor to `Hand`
    - Add `private Random random` field to `Hand.java`
    - Add constructor `public Hand(int size, Random random)` that stores the Random and calls `refill()`
    - Modify `refill()` to use `Piece.getRandomPiece(this.random)` when `random != null`, otherwise use `Piece.getRandomPiece()`
    - Existing `Hand(int size)` constructor remains unchanged (sets `random = null`)
    - _Requirements: 1.3, 1.4, 10.3, 10.5_

  - [ ] 5.2 Add seeded Random overload to `GameView.setup()`
    - Add overloaded method `public void setup(int boardSize, int handSize, String modeName, GameCallback cb, Random seededRandom)`
    - When `seededRandom` is provided, create `Hand` with `new Hand(handSize, seededRandom)`
    - Store the seeded Random for potential SpecialTilesManager integration
    - Existing `setup(int, int, String, GameCallback)` remains unchanged
    - _Requirements: 1.2, 1.3, 10.3, 10.5_

- [ ] 6. Implement CalendarView
  - [ ] 6.1 Create `CalendarView` custom View in `com.allan.tileblast.daily` package
    - Create file `app/src/main/java/com/allan/tileblast/daily/CalendarView.java`
    - Extend `android.view.View`, implement Canvas-based drawing (consistent with app style)
    - Implement `setData(List<DayEntry> entries, int streakCount)`
    - Draw 6×5 grid (30 days), most recent at bottom-right
    - Cell states: empty (gray outline), played no star (score text, dim fill), starred (gold star, bright fill), today (highlighted border)
    - Display streak count above grid as "🔥 X days"
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

- [ ] 7. Implement DailyChallengeActivity
  - [ ] 7.1 Create `DailyChallengeActivity` in `com.allan.tileblast` package
    - Create file `app/src/main/java/com/allan/tileblast/DailyChallengeActivity.java`
    - Extend `AppCompatActivity`, implement `GameView.GameCallback`
    - In `onCreate`: generate seed via `DailyChallengeEngine.generateSeed()`, create `Random(seed)`, set up `GameView` with seeded overload (8×8, 3 pieces, "daily" mode)
    - Initialize `ChallengeStorage` and `StreakTracker`
    - Store `todayKey` as YYYYMMDD string
    - _Requirements: 1.1, 1.2, 1.5, 1.6, 2.1, 7.1, 7.2, 7.3, 10.1, 10.2, 10.4_

  - [ ] 7.2 Implement `onGameOver` callback and result dialog
    - In `onGameOver(int finalScore)`: call `storage.recordScore(todayKey, score)`
    - If `score >= TARGET_SCORE`: call `storage.awardStar(todayKey)`
    - Call `streakTracker.calculateStreak()` and persist
    - Call `streakTracker.checkRewards()` and notify if new rewards
    - Show result overlay with score, target, star status, "View Calendar" and "Retry" buttons
    - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 3.4, 5.1, 5.2, 6.4_

  - [ ] 7.3 Implement calendar display
    - Add `showCalendar()` method that inflates/shows `CalendarView` with data from `storage.getCalendarEntries(today)` and streak count
    - Wire "View Calendar" button from result dialog
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

- [ ] 8. Wire Daily Challenge button into MainActivity
  - [ ] 8.1 Add "Daily Challenge" button to `activity_main.xml`
    - Add a new `LinearLayout` button (matching existing style: `btn_daily` drawable background, 280dp width)
    - Include title TextView "Daily Challenge" and description "8×8 Grid • Target: 2000"
    - Place between Chaos Mode and High Scores buttons
    - Create `res/drawable/btn_daily.xml` with a distinct color scheme (gold/amber gradient)
    - _Requirements: 1.5, 2.1_

  - [ ] 8.2 Wire button click in `MainActivity.java`
    - Add `findViewById(R.id.btnDaily).setOnClickListener(v -> startActivity(new Intent(this, DailyChallengeActivity.class)))`
    - Apply font styling consistent with other buttons
    - Register `DailyChallengeActivity` in `AndroidManifest.xml`
    - _Requirements: 1.5_

- [ ] 9. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (jqwik library)
- Unit tests validate specific examples and edge cases
- The seeded Random integration (task 5) is backward-compatible — existing game modes are unaffected
- Leaderboard reporting (Requirement 9) is deferred to the online-leaderboard-firebase spec

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "2.2", "2.3"] },
    { "id": 1, "tasks": ["1.3", "1.4", "2.1"] },
    { "id": 2, "tasks": ["2.4", "2.5", "2.6", "3.1"] },
    { "id": 3, "tasks": ["3.2", "3.3", "3.4", "5.1"] },
    { "id": 4, "tasks": ["5.2", "6.1"] },
    { "id": 5, "tasks": ["7.1"] },
    { "id": 6, "tasks": ["7.2", "7.3"] },
    { "id": 7, "tasks": ["8.1"] },
    { "id": 8, "tasks": ["8.2"] }
  ]
}
```
