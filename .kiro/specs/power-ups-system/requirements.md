# Requirements Document

## Introduction

The Power-Ups System adds four special abilities to TileBlast that players earn through skilled play and use to extend their runs: Bomb (destroys a 3x3 area), Line Sweep (clears a chosen row or column), Rotate (rotates the dragged piece 90° clockwise), and Undo (reverts the last placement). Power-ups are awarded on combo level 4+ achievements and on score milestones, capped at 2 per type for a maximum inventory of 8. Power-ups are surfaced in slots below the hand area and persist across configuration changes through the existing Bundle-based state save/restore mechanism in `GameView`.

## Glossary

- **Power_Up_System**: The subsystem responsible for inventory tracking, acquisition, activation, application, persistence, and UI rendering of power-ups within `GameView`.
- **Power_Up_Inventory**: The data structure holding an integer count for each of the four power-up types.
- **Power_Up_Type**: One of the four enumerated values: `BOMB`, `LINE_SWEEP`, `ROTATE`, `UNDO`.
- **Bomb_Power_Up**: A power-up that, when activated and targeted at a cell `(tx, ty)`, sets every cell in the 3x3 area centered at `(tx, ty)` and intersected with the board bounds to `Board.EMPTY`.
- **Line_Sweep_Power_Up**: A power-up that, when activated and targeted at a row `r` or column `c`, sets every cell in that row or column to `Board.EMPTY`.
- **Rotate_Power_Up**: A power-up that, when activated, replaces the currently dragged `Piece` with a `Piece` whose matrix is the source matrix rotated 90° clockwise, preserving `colorIndex`.
- **Undo_Power_Up**: A power-up that, when activated, restores the `Board` cells, `Hand` pieces, and `ScoreManager` score and combo to the values captured immediately before the most recent successful piece placement.
- **Targeting_Mode**: A `GameView` state, entered after activating `Bomb_Power_Up` or `Line_Sweep_Power_Up`, in which the next tap on the grid applies the power-up effect and a Cancel button is displayed.
- **Active_Power_Up**: The `Power_Up_Type` whose targeting is currently in progress; null when not in `Targeting_Mode`.
- **Board_Snapshot**: A capture of `Board.cells`, `Board.colors`, the four `Hand` pieces (shape index, color index, and rotated matrix if applicable), `ScoreManager.score`, and `ScoreManager.combo` taken before a placement is applied.
- **Combo_Level**: The integer value returned by `ScoreManager.processLineBreak` and exposed via `ScoreManager.getCombo()`.
- **Score_Milestone**: One of the fixed score thresholds: 1000, 5000, 10000, 25000, 50000.
- **Power_Up_Slot**: A rectangular UI region below the hand area that displays a power-up icon and a numeric count badge for one `Power_Up_Type`.
- **Bundle**: The Android `android.os.Bundle` instance passed to `GameView.saveState` and `GameView.restoreState`.

## Requirements

### Requirement 1: Power-Up Inventory Capacity

**User Story:** As a player, I want a clear cap on how many power-ups I can stockpile, so that earning power-ups remains meaningful and the UI stays compact.

#### Acceptance Criteria

1. THE Power_Up_System SHALL maintain exactly four counters, one for each `Power_Up_Type`: `BOMB`, `LINE_SWEEP`, `ROTATE`, `UNDO`.
2. THE Power_Up_System SHALL initialize each counter to 0 at the start of a new game.
3. THE Power_Up_System SHALL enforce a per-type maximum count of 2.
4. IF an acquisition event would raise a counter above 2, THEN THE Power_Up_System SHALL cap that counter at 2 regardless of how many power-ups the event would otherwise award and SHALL discard the surplus.
5. THE Power_Up_System SHALL expose a read-only count for each `Power_Up_Type` to `GameView` for rendering.

### Requirement 2: Acquisition on Combo Level 4 or Higher

**User Story:** As a player, I want to be rewarded with a power-up when I chain high-level combos, so that strong play earns tactical options.

#### Acceptance Criteria

1. WHEN a placement results in `ScoreManager.getCombo()` returning a value greater than or equal to 4, THE Power_Up_System SHALL select one `Power_Up_Type` uniformly at random from the set `{BOMB, LINE_SWEEP, ROTATE, UNDO}`.
2. WHEN a `Power_Up_Type` is selected by combo acquisition, THE Power_Up_System SHALL increment the counter for that type by 1, subject to the maximum specified in Requirement 1.
3. WHERE `ScoreManager.getCombo()` returns a value strictly less than 4, THE Power_Up_System SHALL grant zero combo-acquisition power-ups for that placement.
4. THE Power_Up_System SHALL grant at most one combo-acquisition power-up per placement, regardless of the combo level achieved.

### Requirement 3: Acquisition on Score Milestones

**User Story:** As a player, I want to receive a power-up when my score reaches a milestone, so that my progress is rewarded with new tools.

#### Acceptance Criteria

1. WHEN `ScoreManager.getScore()` transitions from a value below a `Score_Milestone` to a value greater than or equal to that `Score_Milestone`, THE Power_Up_System SHALL select one `Power_Up_Type` uniformly at random from the set `{BOMB, LINE_SWEEP, ROTATE, UNDO}`.
2. WHEN a `Power_Up_Type` is selected by milestone acquisition, THE Power_Up_System SHALL increment the counter for that type by 1, subject to the maximum specified in Requirement 1.
3. THE Power_Up_System SHALL grant each `Score_Milestone` reward at most once per game.
4. THE Power_Up_System SHALL persist the set of already-earned `Score_Milestone` values for the duration of the current game and SHALL not reset that set until a new game begins.
5. IF a single placement causes the score to cross multiple `Score_Milestone` values, THEN THE Power_Up_System SHALL grant one power-up per crossed milestone.

### Requirement 4: Bomb Activation and Targeting

**User Story:** As a player, I want to detonate a bomb on a chosen cell, so that I can clear a tight cluster of filled cells.

#### Acceptance Criteria

1. WHEN the player taps the `BOMB` `Power_Up_Slot` and its counter is greater than 0 and the game is not paused and the game is not over and no piece is being dragged, THE Power_Up_System SHALL enter `Targeting_Mode` with `Active_Power_Up` set to `BOMB`.
2. IF the player taps the `BOMB` `Power_Up_Slot` and its counter is 0, THEN THE Power_Up_System SHALL leave `Active_Power_Up` unchanged and SHALL play no activation effect.
3. WHILE `Active_Power_Up` equals `BOMB`, THE Power_Up_System SHALL render a visual highlight on the cell currently under the player's finger when that finger is within the grid bounds.
4. WHEN the player taps a cell at grid coordinates `(tx, ty)` while `Active_Power_Up` equals `BOMB`, THE Power_Up_System SHALL set every cell `(x, y)` such that `tx-1 <= x <= tx+1` and `ty-1 <= y <= ty+1` and `0 <= x < boardSize` and `0 <= y < boardSize` to `Board.EMPTY`.
5. WHEN a Bomb destroys cells, THE Power_Up_System SHALL add 10 points per cell that was in `Board.FILLED` state immediately before the bomb was applied to `ScoreManager.score`.
6. WHEN a Bomb is applied, THE Power_Up_System SHALL decrement the `BOMB` counter by 1 and SHALL exit `Targeting_Mode`.
7. WHEN a Bomb is applied, THE Power_Up_System SHALL not increment `ScoreManager.combo` and SHALL not invoke `ScoreManager.processLineBreak`.

### Requirement 5: Line Sweep Activation and Targeting

**User Story:** As a player, I want to clear a full row or column on demand, so that I can recover from a stuck board.

#### Acceptance Criteria

1. WHEN the player taps the `LINE_SWEEP` `Power_Up_Slot` and its counter is greater than 0 and the game is not paused and the game is not over and no piece is being dragged, THE Power_Up_System SHALL enter `Targeting_Mode` with `Active_Power_Up` set to `LINE_SWEEP`.
2. IF the player taps the `LINE_SWEEP` `Power_Up_Slot` and its counter is 0, THEN THE Power_Up_System SHALL leave `Active_Power_Up` unchanged and SHALL play no activation effect.
3. WHILE `Active_Power_Up` equals `LINE_SWEEP`, THE Power_Up_System SHALL render a visual highlight on either the row or the column under the player's finger, choosing the row when the finger's offset within the cell is closer to the row center and the column otherwise.
4. WHEN the player taps a row `r` while `Active_Power_Up` equals `LINE_SWEEP`, THE Power_Up_System SHALL set every cell in row `r` to `Board.EMPTY`.
5. WHEN the player taps a column `c` while `Active_Power_Up` equals `LINE_SWEEP`, THE Power_Up_System SHALL set every cell in column `c` to `Board.EMPTY`.
6. WHEN a Line Sweep is applied, THE Power_Up_System SHALL decrement the `LINE_SWEEP` counter by 1 and SHALL exit `Targeting_Mode`.
7. WHEN a Line Sweep is applied, THE Power_Up_System SHALL not increment `ScoreManager.combo` and SHALL not invoke `ScoreManager.processLineBreak`.

### Requirement 6: Targeting Mode Cancel

**User Story:** As a player, I want to cancel a Bomb or Line Sweep before placing it, so that I do not waste a power-up on a misclick.

#### Acceptance Criteria

1. WHILE `Active_Power_Up` is not null, THE Power_Up_System SHALL render a Cancel button overlay.
2. WHEN the player taps the Cancel button, THE Power_Up_System SHALL set `Active_Power_Up` to null and SHALL leave all power-up counters unchanged.
3. WHEN the player taps outside the grid and outside the Cancel button while `Active_Power_Up` is not null, THE Power_Up_System SHALL leave `Active_Power_Up` and all counters unchanged.
4. WHEN the player presses the device back button while `Active_Power_Up` is not null, THE Power_Up_System SHALL set `Active_Power_Up` to null and SHALL leave all power-up counters unchanged.

### Requirement 7: Rotate During Drag

**User Story:** As a player, I want to rotate the piece I am currently dragging, so that I can fit it into a tighter spot.

#### Acceptance Criteria

1. WHEN the player taps the `ROTATE` `Power_Up_Slot` and its counter is greater than 0 and `GameView.draggingIndex` is greater than or equal to 0, THE Power_Up_System SHALL replace the dragged `Piece`'s matrix with a new matrix equal to the current matrix rotated 90° clockwise.
2. WHEN a Rotate is applied, THE Power_Up_System SHALL preserve the dragged `Piece`'s `colorIndex`.
3. WHEN a Rotate is applied, THE Power_Up_System SHALL decrement the `ROTATE` counter by 1.
4. WHEN a Rotate is applied, THE Power_Up_System SHALL update the hover preview using the rotated matrix at the current drag coordinates.
5. IF the player taps the `ROTATE` `Power_Up_Slot` and `GameView.draggingIndex` is less than 0, THEN THE Power_Up_System SHALL leave the `ROTATE` counter unchanged and SHALL not enter `Targeting_Mode`.
6. THE Power_Up_System SHALL allow consecutive Rotate activations during a single drag, decrementing the counter once per activation.

### Requirement 8: Undo Last Placement

**User Story:** As a player, I want to undo the placement I just made, so that I can recover from a single bad move.

#### Acceptance Criteria

1. WHEN a piece is successfully placed by `GameView.attemptPlacement`, THE Power_Up_System SHALL capture a `Board_Snapshot` representing the state immediately before that placement was applied.
2. THE Power_Up_System SHALL retain at most one `Board_Snapshot` at a time, replacing any prior snapshot on each new placement.
3. WHEN the player taps the `UNDO` `Power_Up_Slot` and its counter is greater than 0 and the most recent placement did not break any lines and the game is not over, THE Power_Up_System SHALL restore `Board.cells`, `Board.colors`, the four `Hand` pieces, `ScoreManager.score`, and `ScoreManager.combo` from the retained `Board_Snapshot`.
4. WHEN an Undo is applied, THE Power_Up_System SHALL decrement the `UNDO` counter by 1 and SHALL discard the retained `Board_Snapshot`.
5. IF the most recent placement broke one or more lines, THEN THE Power_Up_System SHALL render the `UNDO` `Power_Up_Slot` in a disabled visual state and SHALL ignore taps on that slot.
6. IF the game is over, THEN THE Power_Up_System SHALL render the `UNDO` `Power_Up_Slot` in a disabled visual state and SHALL ignore taps on that slot.
7. IF no `Board_Snapshot` is currently retained, THEN THE Power_Up_System SHALL render the `UNDO` `Power_Up_Slot` in a disabled visual state and SHALL ignore taps on that slot.

### Requirement 9: Power-Up Slot Rendering

**User Story:** As a player, I want to see my power-up inventory at a glance, so that I know what abilities are available.

#### Acceptance Criteria

1. THE Power_Up_System SHALL render exactly four `Power_Up_Slot` rectangles in a single row positioned vertically below the hand area defined by `GameView.handY`.
2. THE Power_Up_System SHALL order the slots left-to-right as: `BOMB`, `LINE_SWEEP`, `ROTATE`, `UNDO`.
3. THE Power_Up_System SHALL render each slot with a dimensional touch target of at least 48 dp on each side.
4. THE Power_Up_System SHALL render an icon inside each slot that is distinct for each `Power_Up_Type`.
5. THE Power_Up_System SHALL render a numeric count badge on each slot showing the current counter value for that `Power_Up_Type`.
6. WHILE a slot's counter is 0, THE Power_Up_System SHALL render that slot at 40 percent alpha and SHALL render its count badge as the digit `0`.
7. WHILE `Active_Power_Up` equals the `Power_Up_Type` of a slot, THE Power_Up_System SHALL render that slot with a highlighted border at least 3 px wide.

### Requirement 10: Interaction with Existing Game States

**User Story:** As a player, I want power-ups to behave consistently with pause and game-over states, so that the game feels predictable.

#### Acceptance Criteria

1. WHILE `GameView.paused` is true, THE Power_Up_System SHALL ignore taps on every `Power_Up_Slot` and SHALL not enter `Targeting_Mode`.
2. WHILE `GameView.gameOver` is true, THE Power_Up_System SHALL accept taps on the `BOMB` and `LINE_SWEEP` `Power_Up_Slot`s and SHALL apply their effects to the `Board`.
3. WHILE `GameView.gameOver` is true, THE Power_Up_System SHALL ignore taps on the `UNDO` `Power_Up_Slot` and SHALL ignore taps on the `ROTATE` `Power_Up_Slot`.
4. IF `GameView.paused` becomes true while `Active_Power_Up` is not null, THEN THE Power_Up_System SHALL set `Active_Power_Up` to null without decrementing any counter.
5. WHEN a Bomb or Line Sweep is applied while `GameView.gameOver` is true and the resulting board satisfies `Board.canPlaceAny(hand.getAll())`, THE Power_Up_System SHALL set `GameView.gameOver` to false to resume play.
6. WHEN a Bomb or Line Sweep is applied while `GameView.gameOver` is false and the resulting board does not satisfy `Board.canPlaceAny(hand.getAll())`, THE Power_Up_System SHALL set `GameView.gameOver` to true and SHALL invoke the existing game-over flow in `GameView`.

### Requirement 11: Audio and Haptic Feedback

**User Story:** As a player, I want clear feedback when a power-up activates, so that I know the action took effect.

#### Acceptance Criteria

1. WHEN a Bomb is applied, THE Power_Up_System SHALL invoke a bomb sound effect through `AudioManager` and SHALL trigger a vibration of 30 ms.
2. WHEN a Line Sweep is applied, THE Power_Up_System SHALL invoke a line-break sound effect through `AudioManager` and SHALL trigger a vibration of 30 ms.
3. WHEN a Rotate is applied, THE Power_Up_System SHALL invoke a rotate sound effect through `AudioManager`.
4. WHEN an Undo is applied, THE Power_Up_System SHALL invoke an undo sound effect through `AudioManager`.
5. WHEN a power-up is acquired through combo or milestone, THE Power_Up_System SHALL invoke an acquisition sound effect through `AudioManager`.

### Requirement 12: State Persistence Across Configuration Changes

**User Story:** As a player, I want my power-up inventory and undo state to survive screen rotations, so that I do not lose progress mid-game.

#### Acceptance Criteria

1. WHEN `GameView.saveState(Bundle outState)` is invoked, THE Power_Up_System SHALL write each of the four counter values to `outState` under stable string keys.
2. WHEN `GameView.saveState(Bundle outState)` is invoked and a `Board_Snapshot` is currently retained, THE Power_Up_System SHALL write the snapshot's board cells, board colors, hand piece shape indexes, hand piece color indexes, hand piece rotated matrices, snapshot score, and snapshot combo to `outState` under stable string keys.
3. WHEN `GameView.restoreState(Bundle savedState)` is invoked, THE Power_Up_System SHALL restore each counter from its key in `savedState` independently of the others.
4. WHEN `GameView.restoreState(Bundle savedState)` is invoked and a counter key is missing, THE Power_Up_System SHALL initialize only that counter to 0 and SHALL preserve any counter that was successfully restored.
5. WHEN `GameView.restoreState(Bundle savedState)` is invoked and the snapshot keys are present, THE Power_Up_System SHALL restore the `Board_Snapshot` regardless of whether the counter keys are present or missing.
6. WHEN `GameView.restoreState(Bundle savedState)` is invoked and the snapshot keys are missing, THE Power_Up_System SHALL leave the retained `Board_Snapshot` as null.
7. THE Power_Up_System SHALL set `Active_Power_Up` to null on every `restoreState` invocation.
