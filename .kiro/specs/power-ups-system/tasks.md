# Implementation Plan: Power-Ups System

## Overview

Convert the feature design into a series of prompts for a code-generation LLM that will implement each step with incremental progress. Make sure that each prompt builds on the previous prompts, and ends with wiring things together. There should be no hanging or orphaned code that isn't integrated into a previous step. Focus ONLY on tasks that involve writing, modifying, or testing code.

This plan builds the Power-Ups System bottom-up: first the foundation value types (`PowerUpType` enum, `BoardSnapshot`), then the `PowerUpManager` collaborator with its inventory, state machine, apply effects, and persistence, then the UI surfaces (slot rendering, targeting overlay, cancel button), and finally integration into `GameView` (touch routing, placement hooks, lifecycle). Property-based tests using **jqwik** validate the 10 correctness properties from the design and are placed close to the implementation they cover.

## Tasks

- [x] 1. Add jqwik test dependency for property-based tests
  - Add `testImplementation("net.jqwik:jqwik:1.8.5")` to `app/build.gradle.kts` so JVM-side property tests can run under JUnit 5.
  - Add the JUnit Platform engine (`testImplementation("org.junit.jupiter:junit-jupiter:5.10.x")`) and configure `tasks.withType<Test> { useJUnitPlatform() }` if not already present.
  - _Requirements: Testing Strategy (design)_

- [x] 2. Foundation types
  - [x] 2.1 Create `PowerUpType` enum
    - Create `app/src/main/java/com/allan/tileblast/game/PowerUpType.java` with values `BOMB, LINE_SWEEP, ROTATE, UNDO` in that declaration order.
    - Add `static PowerUpType randomFrom(Random r)` helper used by acquisition.
    - _Requirements: 1.1, 9.2_

  - [x] 2.2 Create `BoardSnapshot` value class
    - Create `app/src/main/java/com/allan/tileblast/game/BoardSnapshot.java` as an immutable holder for `cells[][]`, `colors[][]`, `handShapes[]`, `handColors[]`, `handMatrices[][][]`, `score`, `combo`.
    - Add `static BoardSnapshot capture(Board, Hand, ScoreManager)` that deep-copies arrays and per-slot piece matrices.
    - Add `void restoreInto(Board, Hand, ScoreManager)` that writes captured values back into the live objects.
    - Add `void writeTo(Bundle)` and `static BoardSnapshot readFrom(Bundle)` using the `pu_snap_*` keys from the design (returning `null` when `pu_snap_present` is absent or false).
    - _Requirements: 8.1, 8.2, 8.3, 12.2, 12.5, 12.6_

  - [x]* 2.3 Write property test for snapshot round-trip
    - Create `app/src/test/java/com/allan/tileblast/game/BoardSnapshotPropertyTest.java`.
    - **Property 7: Snapshot capture-restore round trip** - generate a random `(Board, Hand, ScoreManager)` state `S`, capture, mutate the live state, then `restoreInto(...)` and assert every cell value, color, hand shape index, hand color index, hand matrix, score, and combo equals `S`.
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 7.4**

- [x] 3. PowerUpManager - inventory and acquisition
  - [x] 3.1 Create `PowerUpManager` skeleton with inventory
    - Create `app/src/main/java/com/allan/tileblast/game/PowerUpManager.java`.
    - Add `MAX_PER_TYPE = 2`, `SCORE_MILESTONES = {1000, 5000, 10000, 25000, 50000}`, `int[] counts` of length 4, `HashSet<Integer> earnedMilestones`, `Random random` (constructor-injected).
    - Implement `int getCount(PowerUpType)`, `void newGameReset()`.
    - _Requirements: 1.1, 1.2, 1.5_

  - [x] 3.2 Implement combo acquisition
    - Add `PowerUpType grantOnCombo(int comboLevel)`: when `comboLevel >= 4`, pick a uniform-random `PowerUpType`; if its counter is `< MAX_PER_TYPE`, increment and return the type; otherwise return `null` (cap clamped, surplus discarded).
    - When `comboLevel < 4`, do nothing and return `null`.
    - _Requirements: 1.3, 1.4, 2.1, 2.2, 2.3, 2.4_

  - [x]* 3.3 Write property test for inventory cap and combo grant invariant
    - Create `app/src/test/java/com/allan/tileblast/game/PowerUpManagerInventoryPropertyTest.java`.
    - **Property 1: Inventory cap and combo-grant invariant** - for any inventory in `[0,2]^4` and any `comboLevel` in `[0, 20]`, after `grantOnCombo` every counter is in `[0, 2]` and the sum changes by 0 or 1, with 1 only when `comboLevel >= 4` and at least one counter is below the cap.
    - **Validates: Requirements 1.3, 1.4, 2.1, 2.2, 2.3, 2.4**

  - [x] 3.4 Implement milestone acquisition
    - Add `List<PowerUpType> grantOnScore(int prevScore, int newScore)`: for each milestone `m` in `SCORE_MILESTONES` with `prevScore < m <= newScore` and `!earnedMilestones.contains(m)`, pick a uniform-random `PowerUpType`, attempt to increment under cap, add `m` to `earnedMilestones`, and append the granted type (or `null`-skip when capped) to the result.
    - Ensure repeated calls with the same `(prevScore, newScore)` after the first return zero grants because milestones are now in the earned set.
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x]* 3.5 Write property test for milestone uniqueness and count
    - Add `PowerUpManagerMilestonePropertyTest.java` next to the inventory test.
    - **Property 2: Milestone-grant uniqueness and count** - for any `prevScore <= newScore` and any earned set, `grantOnScore` produces exactly one grant per milestone in `(prevScore, newScore]` not already earned, and a second call returns zero.
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

- [x] 4. PowerUpManager - activation state machine
  - [x] 4.1 Implement activation, cancel, and gating
    - Add `enum ActivationState { IDLE, TARGETING, EXECUTING }` and fields `state`, `activeType`, `targetGridX/Y`, `targetFracX/Y`, `targetIsRow`.
    - Add `GameContext` parameter (struct of `paused`, `gameOver`, `dragging`, `hasSnapshot`, `lastPlacementClean`).
    - Implement `boolean activate(PowerUpType type, GameContext ctx)`: applies the gating matrix from Property 3 and either enters `TARGETING` (BOMB/LINE_SWEEP) or returns `false` early. ROTATE/UNDO defer their effect to dedicated apply methods called immediately by `GameView` when `activate` returns `true`.
    - Implement `void cancel()` that resets `state = IDLE`, `activeType = null`, leaves all counters unchanged.
    - Implement `void updateTargetCursor(int gridX, int gridY, float fracX, float fracY)` that updates targeting fields and computes `targetIsRow` for `LINE_SWEEP` per the row/column rule (row when `|fracY - 0.5| < |fracX - 0.5|`).
    - Add getters: `getState()`, `getActiveType()`, `isTargeting()`, `getTargetGridX()`, `getTargetGridY()`, `isTargetingRow()`, `isUndoEnabled(GameContext)`.
    - _Requirements: 4.1, 4.2, 5.1, 5.2, 5.3, 6.1, 6.2, 7.5, 8.5, 8.6, 8.7, 10.1, 10.2, 10.3, 10.4_

  - [x]* 4.2 Write property test for activation gating matrix
    - Add `PowerUpManagerActivationPropertyTest.java`.
    - **Property 3: Activation gating matrix** - enumerate the cross-product of `(type, count, paused, gameOver, dragging, hasSnapshot, lastPlacementClean)` and assert `activate` enters `TARGETING` (BOMB/LINE_SWEEP) or signals immediate apply (ROTATE/UNDO) iff the gating rules in the design hold; otherwise returns `false` and leaves state unchanged.
    - **Validates: Requirements 4.1, 5.1, 7.5, 8.5, 8.6, 8.7, 10.1, 10.2, 10.3**

  - [x]* 4.3 Write property test for cancel preserves inventory
    - Add `PowerUpManagerCancelPropertyTest.java`.
    - **Property 10: Cancel preserves inventory** - for any manager in `TARGETING` and any inventory, after `cancel()` the state is `IDLE`, `activeType == null`, and every counter equals its pre-cancel value.
    - **Validates: Requirements 6.2, 6.4, 10.4**

- [x] 5. PowerUpManager - apply effects
  - [x] 5.1 Implement `applyAtCell` for BOMB
    - When `activeType == BOMB`, clamp the 3x3 area centered at `(tx, ty)` to grid bounds, count `FILLED` cells in that bounded area before clearing, set every cell in the bounded area to `Board.EMPTY`, add `10 * filledCount` to `scoreManager.score`, decrement the BOMB counter, transition through `EXECUTING` to `IDLE`, leave `combo` unchanged, and do not invoke `processLineBreak`.
    - Return an `ApplyResult` with `applied`, `cellsCleared`, `filledCellsCleared`, `wasRow=false`.
    - _Requirements: 4.4, 4.5, 4.6, 4.7_

  - [x]* 5.2 Write property test for bomb apply
    - Add `PowerUpManagerBombPropertyTest.java`.
    - **Property 4: Bomb apply preserves and clears** - for any board, any in-bounds `(tx, ty)`, and any BOMB count in `[1,2]`, the bounded 3x3 becomes empty, cells outside are unchanged, score increases by exactly `10 * filledBefore`, the BOMB counter decreases by 1, combo is unchanged, and `state == IDLE`.
    - **Validates: Requirements 4.4, 4.5, 4.6, 4.7**

  - [x] 5.3 Implement `applyAtCell` for LINE_SWEEP
    - When `activeType == LINE_SWEEP`, recompute `targetIsRow` from `(fracX, fracY)` (row when `|fracY - 0.5| < |fracX - 0.5|`), set every cell in the chosen row or column to `Board.EMPTY`, decrement the LINE_SWEEP counter, transition to `IDLE`, leave `combo` unchanged, and do not invoke `processLineBreak`.
    - Set `ApplyResult.wasRow` and `targetIndex` accordingly.
    - _Requirements: 5.3, 5.4, 5.5, 5.6, 5.7_

  - [x]* 5.4 Write property test for line sweep apply
    - Add `PowerUpManagerLineSweepPropertyTest.java`.
    - **Property 5: Line-sweep apply preserves and clears** - for any board, in-bounds `(tx, ty)`, and `(fracX, fracY)` in `[0, 1)^2`, the chosen line is selected by the design rule, every cell in that line is empty, every cell outside is unchanged, the LINE_SWEEP counter decreases by 1, combo is unchanged, and `state == IDLE`.
    - **Validates: Requirements 5.3, 5.4, 5.5, 5.6, 5.7**

  - [x] 5.5 Implement `applyRotate`
    - Add `Piece applyRotate(Piece dragged)` that builds a new matrix `R'[c][R - 1 - r] = M[r][c]`, returns a new `Piece` with the rotated matrix and the same `colorIndex`, decrements the ROTATE counter, and leaves `state` at `IDLE`.
    - Allow consecutive activations during a single drag by leaving counters as the only mutation.
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.6_

  - [x]* 5.6 Write property test for rotate 4-cycle and shape
    - Add `PowerUpManagerRotatePropertyTest.java`.
    - **Property 6: Rotate is a 4-cycle and matches the rotation formula** - for any matrix `M` of `R x C` and any color, the returned matrix has `C x R`, satisfies `R'[c][R - 1 - r] == M[r][c]`, preserves color, and four consecutive rotations produce a matrix bitwise-equal to `M`.
    - **Validates: Requirements 7.1, 7.2, 7.6**

  - [x] 5.7 Implement snapshot lifecycle and `applyUndo`
    - Add `void captureSnapshot(Board, Hand, ScoreManager)` that calls `BoardSnapshot.capture(...)` and stores the result.
    - Add `void clearSnapshot()` that nulls the snapshot (called by `GameView.attemptPlacement` when `linesBroken > 0`).
    - Add `boolean hasSnapshot()`.
    - Add `BoardSnapshot applyUndo(Board, Hand, ScoreManager)` that validates the snapshot's board size matches the live board, calls `snapshot.restoreInto(...)`, decrements the UNDO counter, discards the snapshot, and returns it for caller use; returns `null` and clears the snapshot on size mismatch.
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

- [x] 6. Checkpoint - all PowerUpManager unit and property tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. PowerUpManager - persistence
  - [x] 7.1 Implement `saveState`/`restoreState` for counters and milestones
    - Add `void saveState(Bundle)` that writes `pu_count_bomb`, `pu_count_sweep`, `pu_count_rotate`, `pu_count_undo`, and `pu_milestones` (as `int[]`).
    - Add `void restoreState(Bundle)` that reads each counter independently (defaulting missing keys to 0), restores `earnedMilestones`, and forces `state = IDLE`, `activeType = null` regardless of any saved active state.
    - _Requirements: 12.1, 12.3, 12.4, 12.7_

  - [x] 7.2 Wire snapshot persistence
    - In `saveState`, write `pu_snap_present` and the full snapshot key block via `BoardSnapshot.writeTo(...)` when a snapshot exists.
    - In `restoreState`, call `BoardSnapshot.readFrom(...)`; assign to the manager's snapshot field; on missing keys, leave the snapshot null.
    - _Requirements: 12.2, 12.5, 12.6_

  - [x]* 7.3 Write property test for save-restore round trip
    - Add `PowerUpManagerPersistencePropertyTest.java` under `app/src/androidTest/...` (uses `Bundle`, must run instrumented).
    - **Property 8: Save-restore round trip** - for any manager state reached through arbitrary `grantOnCombo`, `grantOnScore`, `captureSnapshot`, `clearSnapshot`, and apply sequences, serializing and re-loading yields equal counters, equal earned-milestones set, equal snapshot, and `IDLE` state with `activeType == null`.
    - **Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7**

- [x] 8. AudioManager extensions
  - [x] 8.1 Add power-up audio hooks
    - Modify `app/src/main/java/com/allan/tileblast/audio/AudioManager.java` to add `playPowerUpAcquired()` (reuse `place.mp3` at pitch 1.3f) and `playPowerUp(PowerUpType type)` mapping BOMB → `playJuiciness`, LINE_SWEEP → `playBreak`, ROTATE → `playPlace` at 1.5f, UNDO → `playPlace` at 0.7f, per the design Audio Mapping.
    - Reuse existing `SoundPool` plumbing; no new asset files.
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_

- [x] 9. GameView UI - layout and slot rendering
  - [x] 9.1 Add slot layout to `calculateLayout`
    - In `GameView`, add `RectF[] slotRects = new RectF[4]`, `RectF cancelBtnRect`, and a `density` field initialized in `init` from `getResources().getDisplayMetrics().density`.
    - In `calculateLayout`, compute slot squares of `max(48 * density, blockSize)` px on a side, 8 dp inter-slot spacing, centered horizontally below `handY + handHeight`.
    - Position `cancelBtnRect` in the top-right of the grid area, sized at least 48 dp on each side, not colliding with the existing pause button.
    - _Requirements: 9.1, 9.3_

  - [x] 9.2 Implement `drawPowerUpSlots`
    - Add `private void drawPowerUpSlots(Canvas)` invoked from `onDraw` after the existing hand pass.
    - For each `PowerUpType` (in declaration order), draw rounded-rect background, a type-distinct icon (bomb circle + fuse, sweep arrow with vertical bar, rotate curved arrow, undo left-curved arrow) drawn with `Canvas` primitives, a count badge (small filled circle in the top-right with the count digit centered, always rendered including 0), a 3 px gold highlight border when `powerUps.getActiveType() == type`, and 40% alpha overall when count == 0, when `type == UNDO && !powerUps.isUndoEnabled(ctx)`, or when `type == ROTATE && draggingIndex < 0`.
    - _Requirements: 9.1, 9.2, 9.4, 9.5, 9.6, 9.7, 8.5, 8.6, 8.7_

  - [x] 9.3 Implement targeting overlay and cancel button
    - Add `private void drawTargetingOverlay(Canvas)` that renders a 50%-alpha white fill plus 3 px white border on the bomb 3x3 (clamped to bounds) when `activeType == BOMB`, or a translucent fill on the chosen row or column when `activeType == LINE_SWEEP` (using `isTargetingRow()`).
    - Add `private void drawCancelButton(Canvas)` that renders a red rounded rect with an "X" glyph in `cancelBtnRect`.
    - In `onDraw`, after `drawPowerUpSlots`, when `powerUps.isTargeting()` call both helpers.
    - _Requirements: 4.3, 5.3, 6.1_

- [x] 10. GameView - touch routing
  - [x] 10.1 Route `ACTION_DOWN` through power-ups
    - In `onTouchEvent`, before existing pause / hand-drag logic, branch on `powerUps.isTargeting()`: if tap is in `cancelBtnRect`, call `powerUps.cancel()`, `invalidate()`, return; else if tap is in the grid, store the targeting cell; else ignore (per Requirement 6.3).
    - If not targeting and tap hits any `slotRects[i]`, build `GameContext`, call `powerUps.activate(type, ctx)`. If it returned true and the type is ROTATE, call `powerUps.applyRotate(hand.get(draggingIndex))`, replace the dragged piece, play `audioManager.playPowerUp(ROTATE)`, and `invalidate`. If it returned true and the type is UNDO, call `powerUps.applyUndo(board, hand, scoreManager)`, recompute `gameOver = !board.canPlaceAny(hand.getAll())`, play `audioManager.playPowerUp(UNDO)`, and `invalidate`. Otherwise (BOMB/LINE_SWEEP entered TARGETING), just `invalidate`.
    - Otherwise fall through to existing pause-button / hand-drag logic.
    - _Requirements: 4.1, 4.2, 5.1, 5.2, 6.1, 6.2, 6.3, 7.1, 7.2, 7.3, 7.5, 7.6, 8.3, 8.4, 10.1, 10.2, 10.3_

  - [x] 10.2 Route `ACTION_MOVE` for targeting cursor
    - In `onTouchEvent`, when `powerUps.isTargeting()` and the finger is inside the grid, compute `gridX, gridY, fracX, fracY` and call `powerUps.updateTargetCursor(...)` then `invalidate()`.
    - Otherwise, fall through to the existing drag-update logic.
    - _Requirements: 4.3, 5.3_

  - [x] 10.3 Route `ACTION_UP` to apply
    - In `onTouchEvent`, when `powerUps.isTargeting()` and the finger ends inside the grid, call `powerUps.applyAtCell(gridX, gridY, fracX, fracY, board, scoreManager)`. On `applied`, play `audioManager.playPowerUp(activeType)`, vibrate 30 ms via the existing `Vibrator` field, recompute `gameOver = !board.canPlaceAny(hand.getAll())`, and if the game was previously over but is now playable, set `gameOver = false`; if it became newly over, invoke the existing game-over flow via `callback.onGameOver(scoreManager.getScore())`. `invalidate()`.
    - Otherwise fall through to the existing placement logic.
    - _Requirements: 4.4, 4.5, 4.6, 5.4, 5.5, 5.6, 10.5, 10.6, 11.1, 11.2_

- [x] 11. GameView - placement integration and lifecycle
  - [x] 11.1 Hook acquisition into `attemptPlacement`
    - In `attemptPlacement`, before `board.placePiece`, capture `prevScore = scoreManager.getScore()` and call `powerUps.captureSnapshot(board, hand, scoreManager)`.
    - After `scoreManager.processLineBreak`, when `linesBroken > 0` call `powerUps.clearSnapshot()` (Requirement 8.5 means undo is disabled when the last placement broke a line; clearing the snapshot is the simplest realization).
    - Call `PowerUpType combo = powerUps.grantOnCombo(scoreManager.getCombo())`; if non-null, call `audioManager.playPowerUpAcquired()`.
    - Call `List<PowerUpType> ms = powerUps.grantOnScore(prevScore, scoreManager.getScore())`; if any non-null entries exist, call `audioManager.playPowerUpAcquired()`.
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 3.4, 3.5, 8.1, 8.2, 8.5, 11.5_

  - [x]* 11.2 Write property test for game-over flag after apply
    - Add `GameViewApplyGameOverPropertyTest.java` under `app/src/androidTest/...` (uses `Board`/`Hand` + the `GameView` apply path).
    - **Property 9: Game-over flag tracks playability after apply** - for any board, hand, and BOMB or LINE_SWEEP apply at any in-bounds target, after the apply `gameOver == !board.canPlaceAny(hand.getAll())`.
    - **Validates: Requirements 10.5, 10.6**

  - [x] 11.3 Wire `saveState`/`restoreState`
    - In `GameView.saveState`, after the existing fields are written, call `powerUps.saveState(outState)`.
    - In `GameView.restoreState`, after the existing fields are restored, call `powerUps.restoreState(savedState)`.
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7_

  - [x] 11.4 Cancel on back press and on pause
    - Add `boolean onBackPressed()` to `GameView` that returns true and calls `powerUps.cancel()` + `invalidate()` when `powerUps.isTargeting()`; otherwise returns false. Wire it through `GameActivity.onBackPressed()`.
    - In the existing pause-toggle code path, when `paused` becomes true and `powerUps.isTargeting()`, call `powerUps.cancel()`.
    - _Requirements: 6.4, 10.4_

- [x] 12. GameView - construction and reset wiring
  - [x] 12.1 Construct `PowerUpManager` and reset on new game
    - Add `private PowerUpManager powerUps` field, constructed in `init` with `new PowerUpManager(new Random())`.
    - In whatever path starts a new game (`startNewGame` or equivalent), call `powerUps.newGameReset()`.
    - _Requirements: 1.2_

- [x] 13. Final checkpoint - integration tests
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP delivery; they cover property and integration tests that validate the design's correctness properties.
- Each task references specific requirements for traceability.
- Property test sub-tasks are placed close to the implementation they validate so failures surface immediately.
- Property tests use **jqwik** with `@Property(tries = 200)` per the design Testing Strategy.
- Tests that rely on `android.os.Bundle` (Property 8, Property 9) live under `app/src/androidTest` and run as instrumented tests; all other property tests live under `app/src/test` and run as plain JVM tests.
- Each property sub-task is annotated with its property number and the requirements clauses it checks.
- `GameView.java` is touched by many sub-tasks; those sub-tasks are sequenced in the dependency graph to avoid file-write conflicts. Same for `PowerUpManager.java`.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1", "2.1", "2.2", "8.1"] },
    { "id": 1, "tasks": ["2.3", "3.1"] },
    { "id": 2, "tasks": ["3.2"] },
    { "id": 3, "tasks": ["3.3", "3.4"] },
    { "id": 4, "tasks": ["3.5", "4.1"] },
    { "id": 5, "tasks": ["4.2", "4.3", "5.1"] },
    { "id": 6, "tasks": ["5.2", "5.3"] },
    { "id": 7, "tasks": ["5.4", "5.5"] },
    { "id": 8, "tasks": ["5.6", "5.7"] },
    { "id": 9, "tasks": ["7.1"] },
    { "id": 10, "tasks": ["7.2"] },
    { "id": 11, "tasks": ["7.3", "9.1"] },
    { "id": 12, "tasks": ["9.2"] },
    { "id": 13, "tasks": ["9.3"] },
    { "id": 14, "tasks": ["10.1"] },
    { "id": 15, "tasks": ["10.2"] },
    { "id": 16, "tasks": ["10.3"] },
    { "id": 17, "tasks": ["11.1"] },
    { "id": 18, "tasks": ["11.2", "11.3"] },
    { "id": 19, "tasks": ["11.4"] },
    { "id": 20, "tasks": ["12.1"] }
  ]
}
```
