# Requirements Document

## Introduction

The Daily Challenge feature provides players with a unique puzzle each day, generated deterministically from a date-based seed. All players receive the same pieces in the same order on a given day. Players aim to reach a target score to earn a star, track their progress on a calendar view, and build streaks for rewards. The mode uses an 8x8 board with 3-piece hand and special tiles enabled (Chaos mode spawn rules).

## Glossary

- **Daily_Challenge_Engine**: The subsystem responsible for generating deterministic daily puzzles from a date seed, managing attempts, scoring, and determining challenge completion.
- **Seed_Generator**: The component that produces a deterministic random seed from the current date in YYYYMMDD format.
- **Calendar_View**: The UI component displaying the past 30 days with star indicators for completed challenges and streak information.
- **Streak_Tracker**: The component that calculates and persists consecutive days of completed challenges.
- **Challenge_Storage**: The persistence layer storing daily results, streak data, and calendar state in SharedPreferences.
- **Leaderboard_Reporter**: The component responsible for reporting daily scores for leaderboard display.
- **Target_Score**: The daily goal score (e.g., 2000 points) that a player must reach to earn a star for that day.
- **Star**: A visual indicator awarded when a player meets or exceeds the Target_Score for a given day.
- **Streak**: A count of consecutive calendar days on which the player earned a Star.

## Requirements

### Requirement 1: Deterministic Daily Puzzle Generation

**User Story:** As a player, I want each day to have a unique puzzle with the same pieces for all players, so that I can compete fairly on a level playing field.

#### Acceptance Criteria

1. WHEN a daily challenge is started, THE Seed_Generator SHALL produce a seed by parsing the current date as an integer in YYYYMMDD format.
2. WHEN a seed is produced, THE Daily_Challenge_Engine SHALL initialize a java.util.Random instance with that seed value.
3. WHEN the seeded Random instance is initialized, THE Daily_Challenge_Engine SHALL generate all piece selections and piece orderings using that single Random instance.
4. THE Daily_Challenge_Engine SHALL produce an identical sequence of pieces for any two executions using the same date seed.
5. WHEN a daily challenge is started, THE Daily_Challenge_Engine SHALL configure the board as 8x8 with a 3-piece hand.
6. WHEN a daily challenge is started, THE Daily_Challenge_Engine SHALL enable special tile spawning using the same rules as Chaos mode, seeded from the same Random instance.

### Requirement 2: Target Score and Star Completion

**User Story:** As a player, I want a target score goal each day, so that I have a clear objective to achieve and can earn a star for completing it.

#### Acceptance Criteria

1. WHEN a daily challenge is started, THE Daily_Challenge_Engine SHALL display the Target_Score for that day.
2. WHEN the player's score meets or exceeds the Target_Score, THE Daily_Challenge_Engine SHALL award a Star for that day.
3. WHEN a Star is awarded, THE Challenge_Storage SHALL persist the star status for that calendar date.
4. THE Daily_Challenge_Engine SHALL set the Target_Score to 2000 points for each daily challenge.

### Requirement 3: Single Attempt with Best Score Tracking

**User Story:** As a player, I want my best score to count for the daily challenge, so that I can retry to improve without losing my progress.

#### Acceptance Criteria

1. WHEN a player completes a daily challenge attempt, THE Challenge_Storage SHALL record the score for that date.
2. WHEN a player has multiple attempts for the same date, THE Challenge_Storage SHALL retain only the highest score.
3. WHEN a player starts a subsequent attempt for the same date, THE Daily_Challenge_Engine SHALL allow the attempt to proceed.
4. WHEN a daily challenge game ends, THE Daily_Challenge_Engine SHALL display the player's score alongside the Target_Score and indicate whether a Star was earned.

### Requirement 4: Calendar View

**User Story:** As a player, I want to see a calendar of my past 30 days of challenges, so that I can track my progress and streaks visually.

#### Acceptance Criteria

1. WHEN the Calendar_View is opened, THE Calendar_View SHALL display the most recent 30 calendar days.
2. WHEN a day has a completed challenge with a Star, THE Calendar_View SHALL display a star indicator on that day.
3. WHEN a day has a completed challenge without a Star, THE Calendar_View SHALL display a score indicator without a star on that day.
4. WHEN a day has no recorded attempt, THE Calendar_View SHALL display that day as empty.
5. THE Calendar_View SHALL highlight the current day distinctly from past days.
6. WHEN the Calendar_View is opened, THE Streak_Tracker SHALL display the current streak count on the calendar.

### Requirement 5: Streak Tracking

**User Story:** As a player, I want my consecutive days of completed challenges tracked, so that I can build streaks and earn rewards.

#### Acceptance Criteria

1. WHEN a Star is earned for the current date, THE Streak_Tracker SHALL increment the streak count by one.
2. WHEN the player opens the daily challenge and the previous calendar day has no Star, THE Streak_Tracker SHALL reset the streak count to zero.
3. THE Streak_Tracker SHALL calculate the streak as the number of consecutive calendar days ending on the most recent starred day.
4. WHEN the streak count changes, THE Challenge_Storage SHALL persist the updated streak value.

### Requirement 6: Streak Rewards

**User Story:** As a player, I want to earn rewards for maintaining streaks, so that I am motivated to play daily.

#### Acceptance Criteria

1. WHEN the Streak_Tracker reaches a 3-day streak, THE Daily_Challenge_Engine SHALL award 1 power-up to the player.
2. WHEN the Streak_Tracker reaches a 7-day streak, THE Daily_Challenge_Engine SHALL unlock a theme for the player.
3. WHEN the Streak_Tracker reaches a 14-day streak, THE Daily_Challenge_Engine SHALL award a prestige XP bonus to the player.
4. WHEN a streak reward threshold is reached, THE Daily_Challenge_Engine SHALL notify the player of the reward earned.
5. THE Challenge_Storage SHALL persist which streak rewards have been claimed to prevent duplicate awards for the same streak.

### Requirement 7: Offline Support

**User Story:** As a player, I want to play the daily challenge without an internet connection, so that I can enjoy the feature anywhere.

#### Acceptance Criteria

1. THE Seed_Generator SHALL derive the daily seed from the device's local date without requiring a network request.
2. THE Daily_Challenge_Engine SHALL generate all challenge data (pieces, special tile spawns) locally from the seed without server communication.
3. WHEN the device has no network connectivity, THE Daily_Challenge_Engine SHALL allow the player to start and complete a daily challenge.

### Requirement 8: Persistence

**User Story:** As a player, I want my daily challenge results, streak, and calendar data saved locally, so that my progress is preserved across app sessions.

#### Acceptance Criteria

1. THE Challenge_Storage SHALL store daily results (date, score, star status) in SharedPreferences.
2. THE Challenge_Storage SHALL store the current streak count in SharedPreferences.
3. THE Challenge_Storage SHALL store calendar data for the past 30 days in SharedPreferences.
4. WHEN the app is reopened, THE Challenge_Storage SHALL restore all daily challenge state from SharedPreferences.
5. IF SharedPreferences data is corrupted or missing, THEN THE Challenge_Storage SHALL initialize with default empty state without crashing.

### Requirement 9: Daily Leaderboard

**User Story:** As a player, I want to see how my daily score compares to others, so that I feel competitive and engaged.

#### Acceptance Criteria

1. WHEN a daily challenge attempt is completed, THE Leaderboard_Reporter SHALL record the player's best score for that date.
2. WHEN the player views the daily leaderboard, THE Leaderboard_Reporter SHALL display scores sorted in descending order for the current date.
3. THE Leaderboard_Reporter SHALL integrate with the existing Firebase leaderboard infrastructure when available.
4. WHEN Firebase is unavailable, THE Leaderboard_Reporter SHALL display only the local player's score without error.

### Requirement 10: Game Board Configuration

**User Story:** As a player, I want the daily challenge to use a consistent board setup, so that the experience is predictable and fair.

#### Acceptance Criteria

1. THE Daily_Challenge_Engine SHALL configure the game board as 8 rows by 8 columns.
2. THE Daily_Challenge_Engine SHALL configure the hand to hold 3 pieces at a time.
3. WHEN pieces are consumed from the hand, THE Daily_Challenge_Engine SHALL refill the hand from the seeded sequence when all 3 slots are empty.
4. THE Daily_Challenge_Engine SHALL enable special tiles with spawn probabilities and rules identical to Chaos mode.
5. WHEN special tiles spawn during a daily challenge, THE Daily_Challenge_Engine SHALL use the seeded Random instance for spawn decisions to maintain determinism.
