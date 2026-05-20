# Requirements Document

## Introduction

The Ghost Preview & Smart Hints feature enhances the TileBlast block puzzle game with improved visual feedback during piece placement and an optional hint system. The current hover preview uses a subtle 30% alpha transparency that is difficult to see. This feature adds a distinct outlined ghost preview, line-break highlighting, valid/invalid placement indicators, snap-to-grid feedback, and a limited-use hint system that suggests optimal placements.

## Glossary

- **Ghost_Preview**: The visual representation of where a dragged piece will land on the grid, rendered with enhanced visibility including outline and pulsing effects
- **Line_Break_Highlight**: A glowing visual effect applied to rows or columns that would be completed if the piece is placed at the current hover position
- **Hint_System**: An optional feature that calculates and displays the best placement for a hand piece, limited to a fixed number of uses per game
- **Placement_Indicator**: A color-coded overlay (green for valid, red for invalid) shown on grid cells during piece dragging
- **Snap_Feedback**: A combination of haptic pulse and visual scale bounce triggered when a dragged piece aligns to a valid grid position
- **GameView**: The main Android View responsible for rendering the game board, hand pieces, and handling touch input
- **Board**: The grid state manager that tracks cell states (EMPTY, FILLED, HOVERED) and validates piece placement
- **Hand**: The container managing the set of pieces available for the player to place
- **Hint_Button**: A UI element that triggers the hint calculation and display when tapped by the player
- **Hint_Indicator**: An animated visual marker showing the suggested placement position on the grid
- **Best_Placement**: The optimal grid position for a piece, determined by prioritizing line completion, gap filling, and edge placement

## Requirements

### Requirement 1: Enhanced Ghost Preview Rendering

**User Story:** As a player, I want a clearly visible ghost preview when dragging a piece over the grid, so that I can accurately see where the piece will land before releasing.

#### Acceptance Criteria

1. WHEN a piece is dragged over a valid grid position, THE Ghost_Preview SHALL render the piece shape at 60% alpha with a 2dp white outline around each block
2. WHILE a piece is being dragged over a valid grid position, THE Ghost_Preview SHALL animate a pulsing alpha cycle between 50% and 70% over a 600ms period
3. WHEN a piece is dragged away from the grid or to an invalid position, THE Ghost_Preview SHALL disappear within one frame (16ms)
4. THE Ghost_Preview SHALL use the same color as the dragged piece for the filled blocks

### Requirement 2: Valid and Invalid Placement Indicator

**User Story:** As a player, I want to see whether my current drag position is valid or invalid, so that I can adjust placement without guessing.

#### Acceptance Criteria

1. WHEN a piece is dragged over a grid position where Board.canPlace returns true, THE Placement_Indicator SHALL render a green tint (alpha 40%) over the cells the piece would occupy
2. WHEN a piece is dragged over a grid position where the piece overlaps existing filled cells, THE Placement_Indicator SHALL render a red tint (alpha 40%) over the overlapping cells
3. WHEN a piece is dragged over a grid position where the piece extends beyond the board boundary, THE Placement_Indicator SHALL render a red tint (alpha 40%) over the cells that are within bounds
4. WHEN a piece is not over the grid area, THE Placement_Indicator SHALL not render any tint on the grid

### Requirement 3: Line Break Highlight

**User Story:** As a player, I want to see which rows or columns will be cleared if I place the piece at the current position, so that I can make strategic decisions before committing.

#### Acceptance Criteria

1. WHEN a piece is hovered over a valid position that would complete one or more rows or columns, THE Line_Break_Highlight SHALL render a gold-colored border (3dp width, color 0xFFFFD700) around each cell in the affected rows and columns
2. WHILE a line-break-completing hover is active, THE Line_Break_Highlight SHALL animate a pulsing glow effect with alpha cycling between 60% and 100% over an 800ms period
3. WHEN the hover position changes to one that does not complete any lines, THE Line_Break_Highlight SHALL disappear within one frame (16ms)
4. THE Line_Break_Highlight SHALL display simultaneously with the Ghost_Preview without visual conflict

### Requirement 4: Snap-to-Grid Feedback

**User Story:** As a player, I want tactile and visual confirmation when my dragged piece snaps to a grid position, so that I feel confident about the alignment.

#### Acceptance Criteria

1. WHEN a dragged piece transitions from a non-aligned position to a valid grid-aligned position, THE Snap_Feedback SHALL trigger a haptic pulse of 15ms duration
2. WHEN a dragged piece snaps to a new valid grid position, THE Snap_Feedback SHALL animate the ghost preview with a scale bounce from 105% to 100% over 120ms
3. WHEN a dragged piece moves between two adjacent valid grid positions, THE Snap_Feedback SHALL trigger for each new position
4. THE Snap_Feedback SHALL not trigger when the piece is first picked up from the hand area

### Requirement 5: Hint System

**User Story:** As a player, I want an optional hint that shows me a good placement for one of my pieces, so that I can get help when stuck without the game playing itself.

#### Acceptance Criteria

1. THE Hint_Button SHALL be displayed in the game HUD area and be accessible during active gameplay
2. WHEN the player taps the Hint_Button and hints remain available, THE Hint_System SHALL calculate the Best_Placement for one of the hand pieces within 100ms
3. WHEN the Hint_System calculates the Best_Placement, THE Hint_System SHALL prioritize positions that complete lines over positions that fill gaps, and positions that fill gaps over edge placements
4. WHEN a hint is activated, THE Hint_Indicator SHALL display the suggested piece and position with a repeating animated indicator (pulsing outline) for 3 seconds
5. THE Hint_System SHALL provide exactly 3 hint uses per game session
6. WHEN all 3 hints have been used, THE Hint_Button SHALL display in a disabled state and not respond to taps
7. WHEN a new game session starts, THE Hint_System SHALL reset the available hint count to 3
8. IF no valid placement exists for any hand piece when the hint is requested, THEN THE Hint_System SHALL not consume a hint use and SHALL display a brief message indicating no moves are available

### Requirement 6: Hint Placement Scoring

**User Story:** As a developer, I want a deterministic scoring algorithm for hint placement, so that the hint system provides consistent and useful suggestions.

#### Acceptance Criteria

1. THE Hint_System SHALL score each valid placement by summing: (lines_completed * 1000) + (adjacent_filled_cells * 10) + (edge_cells * 5)
2. WHEN multiple placements have the same score, THE Hint_System SHALL select the placement closest to the board center
3. THE Hint_System SHALL evaluate all valid positions for all non-null hand pieces and select the highest-scoring placement
4. THE Hint_System SHALL return both the piece index and the grid coordinates (x, y) of the best placement

### Requirement 7: Hint State Persistence

**User Story:** As a player, I want my remaining hint count preserved when the game is paused or the app is backgrounded, so that I do not lose or gain hints unexpectedly.

#### Acceptance Criteria

1. WHEN the game state is saved (app backgrounded or paused), THE Hint_System SHALL persist the remaining hint count in the saved state bundle
2. WHEN the game state is restored, THE Hint_System SHALL restore the previously saved hint count
3. IF the saved state does not contain a hint count, THEN THE Hint_System SHALL default to 3 available hints
