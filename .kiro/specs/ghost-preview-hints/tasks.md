# Implementation Plan: Ghost Preview & Smart Hints

## Overview

This plan implements enhanced ghost preview rendering, valid/invalid placement indicators, line-break highlighting, snap-to-grid feedback, and a limited-use hint system for the TileBlast Android game. All rendering integrates into the existing Canvas-based pipeline in `GameView.onDraw()`. The `HintCalculator` is a standalone pure-logic class for testability.

## Tasks

- [ ] 1. Create HintCalculator class with scoring logic
  - [ ] 1.1 Create `HintCalculator.java` with `PlacementResult` inner class and constructor
    - Create file at `app/src/main/java/com/allan/tileblast/game/HintCalculator.java`
    - Define `PlacementResult` with fields: `pieceIndex`, `gridX`, `gridY`, `score`
    - Constructor takes `Board` reference and stores `boardSize`
    - _Requirements: 6.1, 6.4_

  - [ ] 1.2 Implement `countLinesCompleted(Piece, int, int)` method
    - Simulate placement without mutating board state
    - Check each row and column for full occupancy (FILLED cells + piece cells)
    - Return total count of rows + columns that would be completed
    - _Requirements: 6.1, 3.1_

  - [ ] 1.3 Implement `countAdjacentFilled(Piece, int, int)` method
    - For each block in the piece at position (px, py), check 4-directional neighbors
    - Count unique filled cells adjacent to piece blocks (exclude piece's own blocks)
    - _Requirements: 6.1_

  - [ ] 1.4 Implement `countEdgeCells(Piece, int, int)` method
    - Count piece blocks that touch board edges (row 0, row N-1, col 0, col N-1)
    - _Requirements: 6.1_

  - [ ] 1.5 Implement `scorePlacement(Piece, int, int)` method
    - Formula: `countLinesCompleted * 1000 + countAdjacentFilled * 10 + countEdgeCells * 5`
    - _Requirements: 6.1_

  - [ ] 1.6 Implement `distanceToCenter(Piece, int, int)` and `findBestPlacement(Piece[])` methods
    - `distanceToCenter`: Manhattan distance from placement center to board center
    - `findBestPlacement`: Iterate all hand pieces, all valid positions, score each, return highest score (tiebreak by center distance)
    - Return null if no valid placement exists
    - _Requirements: 6.2, 6.3, 6.4_

  - [ ]* 1.7 Write property test: Scoring Formula Correctness (Property 5)
    - **Property 5: Scoring Formula Correctness**
    - Verify `scorePlacement` returns exactly `countLinesCompleted * 1000 + countAdjacentFilled * 10 + countEdgeCells * 5`
    - Generate random board states and valid placements
    - **Validates: Requirements 6.1, 5.3**

  - [ ]* 1.8 Write property test: Best Placement is Maximum Score (Property 6)
    - **Property 6: Best Placement is Maximum Score**
    - Verify returned score >= all other valid placement scores
    - Verify null returned when no valid placement exists
    - **Validates: Requirements 6.3**

  - [ ]* 1.9 Write property test: Tiebreaker Selects Closest to Center (Property 7)
    - **Property 7: Tiebreaker Selects Closest to Center**
    - Generate boards with known ties, verify center-distance selection
    - **Validates: Requirements 6.2**

  - [ ]* 1.10 Write property test: Line Completion Detection (Property 3)
    - **Property 3: Line Completion Detection**
    - Verify `countLinesCompleted` matches brute-force simulation without mutating board
    - **Validates: Requirements 3.1**

- [ ] 2. Implement enhanced ghost preview rendering in GameView
  - [ ] 2.1 Add `computePulseAlpha(int periodMs, float min, float max)` utility method to GameView
    - Uses `System.currentTimeMillis()` modulo period with sine wave
    - Returns float in [min, max] range
    - Guard against periodMs <= 0 (return min)
    - _Requirements: 1.2, 3.2_

  - [ ] 2.2 Add `drawGhostBlock(Canvas, int cx, int cy, int blockSize, int colorIdx)` method to GameView
    - Fill with piece color at pulsing alpha (50%-70%, 600ms period)
    - Draw 2dp white outline stroke at same pulsing alpha
    - Replace existing `drawBeveledBlock(..., 0.3f)` call for `Board.HOVERED` cells in `drawGrid()`
    - _Requirements: 1.1, 1.2, 1.4_

  - [ ] 2.3 Update `drawGrid()` to use `drawGhostBlock` for HOVERED cells and call `invalidate()` during hover
    - Replace the `Board.HOVERED` branch to call `drawGhostBlock` instead of `drawBeveledBlock(..., 0.3f)`
    - Ensure continuous animation by calling `invalidate()` when hover is active
    - _Requirements: 1.1, 1.2, 1.3_

  - [ ]* 2.4 Write property test: Pulse Alpha Invariant (Property 1)
    - **Property 1: Pulse Alpha Invariant**
    - For any time value, verify `computePulseAlpha` returns value in [min, max]
    - **Validates: Requirements 1.2, 3.2**

- [ ] 3. Implement placement indicator rendering
  - [ ] 3.1 Add `drawPlacementIndicator(Canvas, Piece, int gx, int gy, boolean valid)` method to GameView
    - Green tint (0x6600FF00) when valid, red tint (0x66FF0000) when invalid
    - Draw over cells the piece would occupy (only those within board bounds)
    - _Requirements: 2.1, 2.2, 2.3_

  - [ ] 3.2 Update `updateHoverPreview()` to track validity and show indicator for invalid positions too
    - When piece is over grid but `canPlace` returns false, still compute grid position
    - Store a `hoverValid` boolean field
    - Call `drawPlacementIndicator` from `drawGrid()` when dragging
    - Do not render indicator when piece is not over the grid area
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [ ]* 3.3 Write property test: Invalid Placement Indicator Cell Identification (Property 2)
    - **Property 2: Invalid Placement Indicator Cell Identification**
    - Verify cells identified for red tinting match piece cells within bounds that overlap FILLED or extend beyond boundary
    - **Validates: Requirements 2.2, 2.3**

- [ ] 4. Implement line-break highlight rendering
  - [ ] 4.1 Add `drawLineBreakHighlight(Canvas)` method to GameView
    - Iterate cells with `HOVERED_BREAK_FILLED` or `HOVERED_BREAK_EMPTY` state
    - Draw gold border (0xFFFFD700, 3dp width) with pulsing alpha (60%-100%, 800ms period)
    - Call from `drawGrid()` after drawing cells
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [ ] 5. Checkpoint - Ensure all rendering compiles and runs
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Implement snap-to-grid feedback
  - [ ] 6.1 Add snap detection state fields and `checkSnap(int, int)` method to GameView
    - Fields: `prevSnapGridX`, `prevSnapGridY`, `snapBounceStartTime`, `firstPickup`
    - On first pickup: record position, no feedback
    - On position change: trigger haptic (15ms) and record bounce start time
    - Reset `firstPickup` on ACTION_UP/ACTION_CANCEL
    - _Requirements: 4.1, 4.3, 4.4_

  - [ ] 6.2 Add `vibrateSnap(int ms)` method and scale bounce animation to ghost preview
    - Short haptic pulse (15ms) using Vibrator service
    - Scale bounce: 105% to 100% over 120ms applied to ghost block rendering
    - Integrate bounce scale into `drawGhostBlock` when `snapBounceStartTime` is recent
    - _Requirements: 4.1, 4.2_

  - [ ] 6.3 Integrate `checkSnap` into `updateHoverPreview()` and reset snap state on drag end
    - Call `checkSnap(gx, gy)` after computing grid position in `updateHoverPreview()`
    - Reset `firstPickup = true` and `prevSnapGridX = -1` on ACTION_UP/ACTION_CANCEL
    - _Requirements: 4.1, 4.3, 4.4_

  - [ ]* 6.4 Write property test: Snap Detection Triggers on Position Change (Property 4)
    - **Property 4: Snap Detection Triggers on Position Change**
    - Verify snap triggers exactly on position change, not on first pickup
    - **Validates: Requirements 4.1, 4.3, 4.4**

- [ ] 7. Implement hint button and hint system integration
  - [ ] 7.1 Add hint state fields and `drawHintButton(Canvas, int w)` method to GameView
    - Fields: `hintsRemaining = 3`, `hintBtnRect`, `activeHint`, `hintDisplayStartTime`
    - Draw button in HUD area showing "HINT (N)" with enabled/disabled styling
    - Disabled when `hintsRemaining == 0` or `gameOver` or `paused`
    - _Requirements: 5.1, 5.5, 5.6_

  - [ ] 7.2 Add hint button tap handling in `onTouchEvent` ACTION_DOWN
    - Check if tap is within `hintBtnRect`
    - If hints remain and no active hint animation: invoke `HintCalculator.findBestPlacement()`
    - If result is non-null: decrement `hintsRemaining`, set `activeHint` and `hintDisplayStartTime`
    - If result is null: show "No moves available" message for 1.5s, do NOT decrement counter
    - _Requirements: 5.2, 5.5, 5.8_

  - [ ] 7.3 Add `drawHintIndicator(Canvas)` method to GameView
    - Draw pulsing teal outline (0xFF00FFAA, 3dp, 800ms pulse 40%-100%) around suggested placement cells
    - Display for 3 seconds then clear `activeHint`
    - Clear if piece is removed from hand
    - Call `invalidate()` to keep animating
    - _Requirements: 5.3, 5.4_

  - [ ] 7.4 Wire `drawHintButton` and `drawHintIndicator` into `onDraw()` pipeline
    - Call `drawHintButton` in HUD drawing section
    - Call `drawHintIndicator` after grid drawing
    - Ensure hint button is drawn after HUD elements
    - _Requirements: 5.1, 5.4_

  - [ ]* 7.5 Write property test: No Hint Consumed When No Moves Available (Property 8)
    - **Property 8: No Hint Consumed When No Moves Available**
    - Generate full boards with no valid placements, verify counter unchanged
    - **Validates: Requirements 5.8**

- [ ] 8. Implement hint state persistence
  - [ ] 8.1 Add hint count save/restore to `saveState(Bundle)` and `restoreState(Bundle)` methods
    - Save `hintsRemaining` with key `"hints_remaining"` in `saveState()`
    - Restore with `getInt("hints_remaining", 3)` in `restoreState()` (default 3 if missing)
    - Reset `hintsRemaining = 3` in `setup()` for new game
    - _Requirements: 7.1, 7.2, 7.3, 5.7_

  - [ ]* 8.2 Write property test: Hint Count Persistence Round-Trip (Property 9)
    - **Property 9: Hint Count Persistence Round-Trip**
    - For any hintsRemaining in {0, 1, 2, 3}, verify save/restore identity
    - **Validates: Requirements 7.1, 7.2**

- [ ] 9. Final checkpoint - Ensure all tests pass and features integrate
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- All rendering methods integrate into the existing `GameView.onDraw()` Canvas pipeline
- `HintCalculator` is a standalone pure-logic class that can be tested without Android framework dependencies
- The jqwik library is recommended for property-based testing on JVM

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "1.4", "2.2"] },
    { "id": 2, "tasks": ["1.5", "1.6", "2.3", "2.4"] },
    { "id": 3, "tasks": ["1.7", "1.8", "1.9", "1.10", "3.1"] },
    { "id": 4, "tasks": ["3.2", "4.1"] },
    { "id": 5, "tasks": ["3.3", "6.1"] },
    { "id": 6, "tasks": ["6.2", "6.3"] },
    { "id": 7, "tasks": ["6.4", "7.1"] },
    { "id": 8, "tasks": ["7.2", "7.3"] },
    { "id": 9, "tasks": ["7.4", "7.5"] },
    { "id": 10, "tasks": ["8.1"] },
    { "id": 11, "tasks": ["8.2"] }
  ]
}
```
