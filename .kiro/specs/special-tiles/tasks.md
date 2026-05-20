# Implementation Plan: Special Tiles

Convert the feature design into a series of prompts for a code-generation LLM that will implement each step with incremental progress. Make sure that each prompt builds on the previous prompts, and ends with wiring things together. There should be no hanging or orphaned code that isn't integrated into a previous step. Focus ONLY on tasks that involve writing, modifying, or testing code.

## Overview

Implementation proceeds bottom-up: first extend `Board` with the four new cell states and helpers, then introduce `SpecialTilesManager` with spawn/detonate logic, then update `breakLines`/`canPlace` line-break semantics, then add rendering for the new tiles, then wire everything into `GameView` (turn flow + game-over) and finally extend persistence. Implementation language is **Java** (Android), as established by the existing codebase and the design document.

## Tasks

- [ ] 1. Extend Board with special-tile cell states and helpers
  - [ ] 1.1 Add new cell-state constants and helper predicates to `Board`
    - Add `public static final int FROZEN = 5;`, `FROZEN_CRACKED = 6;`, `LOCKED = 7;`, `BOMB_TILE = 8;` to `app/src/main/java/com/allan/tileblast/game/Board.java`
    - Add `public static boolean isSpecial(int cellState)` returning true for the four new states
    - Add `public static boolean isLineFillable(int cellState)` returning true for `FILLED`, `FROZEN`, `FROZEN_CRACKED`, `LOCKED`, `BOMB_TILE`
    - _Requirements: 1.1, 2.1, 3.1_

  - [ ] 1.2 Add bomb countdown grid and accessors to `Board`
    - Add `private int[][] bombCountdowns` field initialized in the constructor to a `size × size` array of zeros
    - Add `public int getBombCountdown(int x, int y)` and `public void setBombCountdown(int x, int y, int value)`
    - Add `public void setSpecial(int x, int y, int specialState, int initialCountdown)` that sets `cells[y][x]`, leaves the existing color in place, and writes `bombCountdowns[y][x] = initialCountdown` only when `specialState == BOMB_TILE` (clearing it to 0 otherwise)
    - _Requirements: 3.2, 3.8_

  - [ ] 1.3 Update `Board.canPlace` to reject overlap with special tiles
    - Modify the overlap check inside `canPlace` so a piece collides not only with `FILLED` but also with any cell where `isSpecial(cells[by][bx])` is true
    - _Requirements: 1.4, 2.3, 3.6, 7.1_

  - [ ] 1.4 Update line-completion checks to count specials as filled
    - In `Board.breakLines`: replace the row/column "full" check so a row or column is considered complete when every cell satisfies `isLineFillable`
    - In `Board.updateHoveredBreaks`: replace the row/column "full" check so cells satisfying `isLineFillable` (or in `HOVERED`) count toward fullness, so the hover preview matches the real break behavior
    - _Requirements: 1.5, 2.4_

  - [ ] 1.5 Apply per-state transitions when clearing completed lines in `breakLines`
    - In `Board.breakLines`, replace the "set to EMPTY" sweep over completed rows and columns with a per-cell transition:
      - `FILLED` → `EMPTY`
      - `FROZEN` → `FROZEN_CRACKED` (cell stays occupied)
      - `FROZEN_CRACKED` → `EMPTY`
      - `LOCKED` → `EMPTY`
      - `BOMB_TILE` → `EMPTY`, and clear `bombCountdowns[y][x] = 0`; do NOT trigger 3×3 detonation
    - Preserve the existing return value (number of completed lines)
    - _Requirements: 1.2, 1.3, 2.2, 3.7_

  - [ ]* 1.6 Write unit tests for `Board` special-tile behavior
    - Test `isSpecial` and `isLineFillable` for every cell-state value
    - Test `canPlace` rejects pieces overlapping each of `FROZEN`, `FROZEN_CRACKED`, `LOCKED`, `BOMB_TILE`
    - Test `breakLines` transitions: `FROZEN` → `FROZEN_CRACKED`, `FROZEN_CRACKED` → `EMPTY`, `LOCKED` → `EMPTY`, `BOMB_TILE` → `EMPTY` (no detonation), `FILLED` → `EMPTY`
    - Test that a row containing a `FROZEN` tile breaks and the frozen cell becomes `FROZEN_CRACKED` while remaining cells become `EMPTY`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, 3.1, 3.6, 3.7, 7.1_

- [ ] 2. Implement SpecialTilesManager
  - [ ] 2.1 Create `SpecialTilesManager` skeleton with constants and constructor
    - Create `app/src/main/java/com/allan/tileblast/game/SpecialTilesManager.java` in package `com.allan.tileblast.game`
    - Define constants: `BOMB_INITIAL_COUNTDOWN = 5`, `FROZEN_MIN_TURN = 5`, `FROZEN_CAP = 5`, `LOCKED_CAP = 3`, `BOMB_CAP = 2`, `FROZEN_PROBABILITY = 0.10f`, `LOCKED_PROBABILITY = 0.05f`, `BOMB_PROBABILITY = 0.03f`
    - Define nested `public interface DetonationCallback { void onChainBombGameOver(); }`
    - Constructor `SpecialTilesManager(Board board, String mode, java.util.Random rng)` storing the three fields and initializing `turnNumber = 0`
    - Add `public boolean isModeEligible()` returning true only for `"chaos"` and `"daily"` (case-insensitive compare via `equalsIgnoreCase`)
    - _Requirements: 3.2, 4.1, 4.2, 4.3, 4.4, 5.1, 5.3, 5.5_

  - [ ] 2.2 Implement spawn evaluation in `SpecialTilesManager.evaluateSpawns`
    - Add `public void evaluateSpawns(int turnNumber)`: store `this.turnNumber = turnNumber`, no-op if `!isModeEligible()`
    - Helper `private int countCells(int... states)` scanning `board` for any of the given states
    - Helper `private java.util.List<int[]> listEmptyCells()` returning all `(x,y)` where `cells[y][x] == EMPTY`
    - Helper `private void trySpawn(int state, int cap, float probability, int initialCountdown, boolean turnGated)`:
      - Compute current count: frozen counts both `FROZEN` and `FROZEN_CRACKED`; locked counts `LOCKED`; bomb counts `BOMB_TILE`
      - If `turnGated && turnNumber <= FROZEN_MIN_TURN` return
      - If count ≥ cap return
      - Build list of empty cells; if empty return
      - If `rng.nextFloat() >= probability` return
      - Pick a random index uniformly with `rng.nextInt(empties.size())` and call `board.setSpecial(x, y, state, initialCountdown)`
    - Call `trySpawn` for frozen (turn-gated, countdown 0), then locked (countdown 0), then bomb (countdown `BOMB_INITIAL_COUNTDOWN`) in that fixed order
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9_

  - [ ] 2.3 Implement bomb decrement and detonation in `SpecialTilesManager.decrementBombsAndDetonate`
    - Add `public int decrementBombsAndDetonate(DetonationCallback cb)`
    - Iterate the board collecting `(x, y)` of every `BOMB_TILE`; decrement each `bombCountdowns[y][x]` by 1
    - For each cell whose countdown is now 0, perform a 3×3 clear centered on `(bx, by)`, bounded by `0..size-1`:
      - Track `chainHit = true` if any cell in the 3×3 (excluding the center itself) is also `BOMB_TILE`
      - Set those cells to `EMPTY` and reset their `bombCountdowns` to 0 via `Board` accessors
    - If `chainHit` is true, invoke `cb.onChainBombGameOver()` and return immediately (count of detonations so far)
    - Otherwise return the number of detonations performed
    - _Requirements: 3.3, 3.4, 3.5, 7.3_

  - [ ]* 2.4 Write unit tests for `SpecialTilesManager` spawn and detonation
    - With a stubbed `Random` (e.g., subclass returning fixed values), verify:
      - `isModeEligible()` returns false for `"classic"`/`"timed"`, true for `"chaos"`/`"daily"`
      - In classic/timed mode, `evaluateSpawns` makes no change to the board
      - Frozen does not spawn while `turnNumber <= 5`, spawns at turn 6 with controlled rng
      - Caps prevent additional spawns once frozen ≥ 5, locked ≥ 3, bomb ≥ 2 (count both `FROZEN` and `FROZEN_CRACKED` for the frozen cap)
      - Spawn skipped when no empty cell exists
      - Bomb spawn writes `BOMB_INITIAL_COUNTDOWN = 5`
      - `decrementBombsAndDetonate` decrements every bomb countdown by 1
      - When a bomb hits 0, a 3×3 area centered on it is cleared (bounded by edges)
      - When two bombs are adjacent and one detonates, `onChainBombGameOver` fires
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.3, 4.4, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9_

- [ ] 3. Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 4. Render special tiles in `GameView`
  - [ ] 4.1 Add `drawSpecialCell` to `GameView`
    - Add `private void drawSpecialCell(Canvas canvas, int cx, int cy, int blockSize, int x, int y, int cellState)` to `GameView`
    - Implement fills per design:
      - `FROZEN`: fill RGB(160, 220, 255) plus a lighter top-left wedge highlight using a triangle path
      - `FROZEN_CRACKED`: same fill as `FROZEN` plus 2–3 white crack `Path`s drawn with `STROKE`, stroke width ≈ `blockSize / 14`
      - `LOCKED`: fill RGB(64, 64, 64) and a chain icon overlay (two interlocking rounded rects, white, stroke width ≈ `blockSize / 10`)
      - `BOMB_TILE`: fill RGB(200, 30, 30) and a centered countdown digit using `fontBold` at `blockSize * 0.55f` reading `board.getBombCountdown(x, y)`
    - All shapes drawn procedurally via `Canvas` primitives — no asset additions
    - _Requirements: 1.6, 1.7, 2.6, 3.8_

  - [ ] 4.2 Wire `drawSpecialCell` into `GameView.drawGrid`
    - In the cell rendering loop, before the empty-cell branch, add a check `else if (Board.isSpecial(cell)) drawSpecialCell(canvas, cx, cy, blockSize, x, y, cell);`
    - Ensure the empty-cell border still draws for genuinely empty cells only
    - _Requirements: 1.6, 1.7, 2.6, 3.8_

  - [ ]* 4.3 Write a smoke test for special-tile rendering
    - Create a `Board` with one cell of each special state set via `setSpecial`, render to an off-screen `Bitmap`-backed `Canvas`, and assert that the center pixel of each special cell matches its expected fill color (within tolerance)
    - _Requirements: 1.6, 1.7, 2.6, 3.8_

- [ ] 5. Integrate special tiles into the turn flow
  - [ ] 5.1 Add `SpecialTilesManager` instance and `turnNumber` to `GameView`
    - Add `private SpecialTilesManager specialTiles;` and `private int turnNumber;` fields
    - In `GameView.setup`, after `this.hand = new Hand(handSize);`, instantiate `this.specialTiles = new SpecialTilesManager(board, modeName, new java.util.Random());` and set `this.turnNumber = 0;`
    - _Requirements: 4.3, 4.4_

  - [ ] 5.2 Extract `triggerGameOver` helper from inline game-over branch
    - Extract the existing inline game-over logic (set `gameOver = true`, play game-over sfx, save score, invoke `callback.onGameOver`) into `private void triggerGameOver()`
    - Replace the existing game-over branch in `attemptPlacement` with a call to `triggerGameOver()`
    - _Requirements: 7.2, 7.3_

  - [ ] 5.3 Hook special-tile phases into `attemptPlacement`
    - In `GameView.attemptPlacement`, immediately after `int comboLevel = scoreManager.processLineBreak(linesBroken);` and the line-break audio/visual block:
      - Use a `final boolean[] chainGameOver = { false };` and call `specialTiles.decrementBombsAndDetonate(() -> chainGameOver[0] = true);`
      - If `chainGameOver[0]` is true: call `triggerGameOver()` and `return`
      - Increment `turnNumber` and call `specialTiles.evaluateSpawns(turnNumber)`
    - Leave the existing hand refill and `canPlaceAny` check (now using `triggerGameOver`) afterward
    - _Requirements: 3.3, 3.4, 3.5, 4.3, 4.4, 5.1–5.9, 7.1, 7.2, 7.3_

  - [ ]* 5.4 Write unit tests for the integrated turn flow
    - Using a `GameView` constructed in a test or by extracting `attemptPlacement`'s special-tile block into a small testable method, verify:
      - Playing a turn in chaos mode increments `turnNumber` by exactly 1
      - A bomb whose countdown reaches 0 detonates a 3×3 area on the next placement (without using line breaks)
      - A chain detonation flips `gameOver` to true
      - Caps and probabilities still hold over many simulated turns with a seeded RNG
    - _Requirements: 3.3, 3.4, 3.5, 4.1, 4.2, 4.3, 4.4, 5.1–5.9, 7.2, 7.3_

- [ ] 6. Persist and restore special-tile state
  - [ ] 6.1 Persist special cell states in `GameView.saveState`
    - In `GameView.saveState`, replace the cell-state collapse so that `FROZEN`, `FROZEN_CRACKED`, `LOCKED`, and `BOMB_TILE` are written verbatim to `board_cells`; collapse only hover states (`HOVERED` → `EMPTY`, `HOVERED_BREAK_FILLED` → `FILLED`, `HOVERED_BREAK_EMPTY` → `EMPTY`); leave the other branches unchanged
    - _Requirements: 6.1_

  - [ ] 6.2 Add `saveState`/`restoreState` to `SpecialTilesManager`
    - Add `public void saveState(android.os.Bundle out)`: write `out.putInt("st_turn_number", turnNumber)` and a flattened `int[]` of bomb countdowns of length `size*size` (zero outside bomb cells) to key `"st_bomb_countdowns"`
    - Add `public void restoreState(android.os.Bundle in)`: read the turn number into `turnNumber` (default 0) and, if the countdown array is present and matches `size*size`, write each non-zero entry back via `board.setBombCountdown(x, y, value)` (only for cells where `board.getCell(x, y) == BOMB_TILE`)
    - _Requirements: 6.2, 6.4_

  - [ ] 6.3 Wire manager persistence into `GameView`
    - In `GameView.saveState`, after the existing per-field saves, call `specialTiles.saveState(outState);`
    - In `GameView.restoreState`, after the board cells are restored verbatim, call `specialTiles.restoreState(savedState);`
    - Confirm that cap enforcement is implicit because counts are derived from live board scans on each `evaluateSpawns`
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [ ]* 6.4 Write unit tests for persistence round-trip
    - Place each special state on a board, populate countdowns for two bombs at different countdown values, save into a `Bundle`, restore into a fresh `GameView`/`SpecialTilesManager`, and assert that all cell states, bomb countdowns, and `turnNumber` match
    - Additionally verify that after restore, `evaluateSpawns` enforces caps based on the restored cells (e.g., 5 frozen on the board means no new frozen spawn even when rng would pass)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [ ] 7. Final checkpoint
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP.
- Each task references specific requirements clauses for traceability.
- Testing tasks live next to their implementation tasks to catch regressions early.
- The design has no Correctness Properties section, so unit tests are used in place of property-based tests.
- `triggerGameOver` is extracted in 5.2 so both the chain-bomb path (7.3) and the standard no-move path (7.2) share one game-over routine.
- Spawn counts (caps) are derived on the fly from the live board, so persistence does not need a separate counter.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "1.4"] },
    { "id": 2, "tasks": ["1.5", "2.1"] },
    { "id": 3, "tasks": ["1.6", "2.2", "2.3"] },
    { "id": 4, "tasks": ["2.4", "4.1"] },
    { "id": 5, "tasks": ["4.2", "5.1", "5.2"] },
    { "id": 6, "tasks": ["4.3", "5.3", "6.1", "6.2"] },
    { "id": 7, "tasks": ["5.4", "6.3"] },
    { "id": 8, "tasks": ["6.4"] }
  ]
}
```
