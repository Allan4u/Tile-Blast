# Requirements Document

## Introduction

The Timed Mode feature adds a sprint-style game mode to TileBlast where the Player has a fixed amount of time (60 or 90 seconds) to earn as many points as possible. The mode rewards fast play and combo chains by adding time to the clock when lines break, and increases the Score_Multiplier as elapsed time grows. Power-ups are disabled to keep the focus on raw speed and pattern recognition. Timed Mode runs on the Classic 8×8 board with a 3-piece Hand and uses a separate Timed_Leaderboard so that times scores do not mix with Classic or Chaos scores.

## Glossary

- **TileBlast**: The existing Android tile-placement game.
- **Timed_Mode**: The new game mode introduced by this feature, instantiated as either `Timed_Mode_60` (60 second initial duration) or `Timed_Mode_90` (90 second initial duration).
- **Countdown_Timer**: The HUD element that displays the remaining time in seconds, decrementing in real time while the game is active.
- **Game_Engine**: The combined runtime composed of `GameView`, `Board`, `Hand`, and `ScoreManager` that processes input, updates state, and renders the game.
- **Score_Multiplier**: A numeric factor (1.0x, 1.5x, 2.0x, 2.5x, 3.0x) that the Game_Engine applies to all score additions, determined by the elapsed time since the round started.
- **Elapsed_Time**: The number of seconds of active gameplay since the round started, excluding paused time.
- **Remaining_Time**: The number of seconds left on the Countdown_Timer, computed as `(initial_duration + accumulated_time_bonuses) - Elapsed_Time`.
- **Time_Bonus**: A 1.5 second addition to Remaining_Time that the Game_Engine grants each time one or more lines break.
- **Line_Break**: An event in which the Board clears at least one full row or column after a placement, as defined by the existing Classic Mode rules.
- **Time_Remaining_Bonus**: An end-of-round score addition equal to `5 × floor(Remaining_Time)` points, awarded when the round ends with Remaining_Time greater than 0.
- **Mode_Menu**: The main menu screen (`MainActivity` / `activity_main.xml`) where the Player selects a mode.
- **Timed_Leaderboard**: The high score list dedicated to Timed_Mode rounds, persisted by the Storage_Layer using its own mode keys (`timed60`, `timed90`), separate from `classic` and `chaos`.
- **Storage_Layer**: The `StorageManager` responsible for persisting high scores and reading the best score per mode.
- **HUD**: The on-screen heads-up display rendered by `GameView` that shows score, best score, combo, and (in Timed_Mode) the Countdown_Timer.
- **Player**: The user interacting with TileBlast.
- **Power_Up**: Any gameplay-altering ability provided by the existing power-ups-system feature.

## Requirements

### Requirement 1: Timed Mode Selection From Main Menu

**User Story:** As a Player, I want to start a Timed Mode round with a chosen duration from the main menu, so that I can pick the sprint length that fits my session.

#### Acceptance Criteria

1. THE Mode_Menu SHALL display a `TIMED 60s` button and a `TIMED 90s` button in addition to the existing Classic, Chaos, and High Scores buttons.
2. WHEN the Player taps the `TIMED 60s` button, THE Mode_Menu SHALL launch `GameActivity` with mode `timed60`, board size 8, hand size 3, and initial duration 60 seconds.
3. WHEN the Player taps the `TIMED 90s` button, THE Mode_Menu SHALL launch `GameActivity` with mode `timed90`, board size 8, hand size 3, and initial duration 90 seconds.
4. THE Mode_Menu SHALL render the Timed Mode buttons using the same font, sizing, and visual conventions as the existing Classic and Chaos buttons.

### Requirement 2: Countdown Timer Display

**User Story:** As a Player, I want a clearly visible countdown timer at the top of the screen, so that I always know how much time I have left.

#### Acceptance Criteria

1. WHILE Timed_Mode is active and not paused, THE Countdown_Timer SHALL display Remaining_Time as an integer number of seconds (rounded down), updated at least 10 times per second.
2. WHILE Timed_Mode is active, THE Countdown_Timer SHALL be rendered at the top of the HUD with a text size at least 1.5 times the size of the score text.
3. WHILE Remaining_Time is greater than 30 seconds, THE Countdown_Timer SHALL be rendered in white (`#FFFFFFFF`).
4. WHILE Remaining_Time is greater than 10 seconds and less than or equal to 30 seconds, THE Countdown_Timer SHALL be rendered in yellow (`#FFFFD700`).
5. WHILE Remaining_Time is greater than 5 seconds and less than or equal to 10 seconds, THE Countdown_Timer SHALL be rendered in red (`#FFFF3333`) without pulsing.
6. WHILE Remaining_Time is greater than 0 and less than or equal to 5 seconds, THE Countdown_Timer SHALL be rendered in red (`#FFFF3333`) and SHALL pulse at 2 Hz by varying its scale between 1.0 and 1.2.
7. WHERE the active mode is not Timed_Mode, THE Countdown_Timer SHALL NOT be rendered.

### Requirement 3: Elapsed-Time Score Multiplier

**User Story:** As a Player, I want my score gains to grow as a round progresses, so that surviving longer feels increasingly rewarding.

#### Acceptance Criteria

1. WHILE Timed_Mode is active and Elapsed_Time is in the half-open interval `[0s, 15s)`, THE Score_Multiplier SHALL equal 1.0.
2. WHILE Timed_Mode is active and Elapsed_Time is in the half-open interval `[15s, 30s)`, THE Score_Multiplier SHALL equal 1.5.
3. WHILE Timed_Mode is active and Elapsed_Time is in the half-open interval `[30s, 45s)`, THE Score_Multiplier SHALL equal 2.0.
4. WHILE Timed_Mode is active and Elapsed_Time is in the half-open interval `[45s, 60s)`, THE Score_Multiplier SHALL equal 2.5.
5. WHERE the active mode is `timed90` AND Elapsed_Time is greater than or equal to 60 seconds, THE Score_Multiplier SHALL equal 3.0.
6. WHEN the Game_Engine awards points for a placement or a Line_Break, THE Game_Engine SHALL multiply the points by the current Score_Multiplier, round to the nearest integer, and add the result to the Player's score.
7. THE Game_Engine SHALL display the current Score_Multiplier in the HUD as text formatted `xN.N` (for example `x2.5`) whenever the Score_Multiplier is greater than 1.0.

### Requirement 4: Line-Break Time Bonus

**User Story:** As a Player, I want clearing lines to extend my time, so that combos can keep a round going.

#### Acceptance Criteria

1. WHEN one or more Line_Breaks occur on a single placement, THE Game_Engine SHALL increase Remaining_Time by 1.5 seconds, applied exactly once per placement regardless of how many lines clear simultaneously.
2. THE Game_Engine SHALL display a transient `+1.5s` indicator near the Countdown_Timer for 800 milliseconds whenever a Time_Bonus is granted.
3. THE Game_Engine SHALL allow Remaining_Time to exceed the initial duration as Time_Bonus is accumulated.

### Requirement 5: Round End Conditions

**User Story:** As a Player, I want the round to end when I run out of time or moves, so that the sprint has a clear conclusion.

#### Acceptance Criteria

1. WHEN Remaining_Time reaches 0 seconds, THE Game_Engine SHALL transition the round to the game-over state.
2. WHEN no piece in the Hand can be placed anywhere on the Board, THE Game_Engine SHALL transition the round to the game-over state.
3. WHEN the round transitions to game-over, THE Game_Engine SHALL stop decrementing the Countdown_Timer and SHALL ignore any placement input received more than 100 milliseconds after the transition.
4. WHEN the round transitions to game-over, THE Game_Engine SHALL display the game-over overlay with the final score, including any Time_Remaining_Bonus.

### Requirement 6: Time-Remaining End Bonus

**User Story:** As a Player, I want leftover seconds to add to my final score, so that ending a round early still rewards efficient play.

#### Acceptance Criteria

1. IF the round transitions to game-over while Remaining_Time is greater than 0 seconds, THEN THE Game_Engine SHALL add a Time_Remaining_Bonus equal to `5 × floor(Remaining_Time)` points to the final score before persisting it.
2. IF the round transitions to game-over while Remaining_Time is less than or equal to 0 seconds, THEN THE Game_Engine SHALL add 0 Time_Remaining_Bonus points.
3. WHEN the game-over overlay is displayed for a Timed_Mode round, THE Game_Engine SHALL show the base score, the Time_Remaining_Bonus, and the final score as separate labeled values.

### Requirement 7: Separate Timed Leaderboard

**User Story:** As a Player, I want Timed Mode scores tracked separately from Classic and Chaos, so that comparisons between modes stay fair.

#### Acceptance Criteria

1. WHEN a `timed60` round ends, THE Storage_Layer SHALL persist the final score under mode key `timed60`.
2. WHEN a `timed90` round ends, THE Storage_Layer SHALL persist the final score under mode key `timed90`.
3. THE Storage_Layer SHALL return high scores for `timed60` and `timed90` independently of `classic` and `chaos` when queried by mode key.
4. THE High Scores screen SHALL allow the Player to view the `timed60` and `timed90` leaderboards as distinct lists.
5. WHILE Timed_Mode is active, THE HUD SHALL display the best score for the active Timed_Mode mode key (`timed60` or `timed90`), not the Classic or Chaos best.

### Requirement 8: Power-Ups Disabled In Timed Mode

**User Story:** As a Player, I want power-ups turned off in Timed Mode, so that the mode stays focused on speed.

#### Acceptance Criteria

1. WHILE Timed_Mode is active, THE Game_Engine SHALL NOT grant any Power_Up to the Player.
2. WHILE Timed_Mode is active, THE Game_Engine SHALL NOT render any Power_Up icons in the HUD.
3. IF the Player attempts to invoke a Power_Up action while Timed_Mode is active, THEN THE Game_Engine SHALL ignore the input.

### Requirement 9: Pause Behavior

**User Story:** As a Player, I want pausing to also pause the timer, so that interruptions do not cost me time.

#### Acceptance Criteria

1. WHILE the round is paused, THE Countdown_Timer SHALL hold its current Remaining_Time value and SHALL NOT decrement.
2. WHILE the round is paused, THE Game_Engine SHALL hold the Elapsed_Time value used to determine the Score_Multiplier.
3. WHEN the round resumes from pause, THE Countdown_Timer SHALL continue decrementing from the held Remaining_Time value.
4. WHEN `GameActivity` enters the background (`onPause`), THE Game_Engine SHALL pause the round automatically.

### Requirement 10: State Persistence Across Configuration Changes

**User Story:** As a Player, I want the timer to survive screen rotation, so that I do not lose progress on accidental orientation changes.

#### Acceptance Criteria

1. WHEN `GameActivity` saves instance state, THE Game_Engine SHALL persist the active mode key, initial duration, Remaining_Time, Elapsed_Time, accumulated Time_Bonus seconds, and paused flag.
2. WHEN `GameActivity` restores instance state for a Timed_Mode round, THE Game_Engine SHALL resume the round in the paused state with Remaining_Time, Elapsed_Time, and accumulated Time_Bonus seconds restored to the persisted values.
3. IF the persisted mode key is not `timed60` or `timed90`, THEN THE Game_Engine SHALL set the active mode to Classic or Chaos based on the persisted mode key and SHALL NOT enable Timed_Mode behavior.
