# Requirements Document

## Introduction

The Special Tiles feature introduces three new obstacle tile types (Frozen, Locked, and Bomb) to the TileBlast board, increasing strategic depth in Chaos and Daily Challenge modes. Each special tile has distinct destruction rules, placement constraints, and visual treatments. Special tiles spawn dynamically as players progress, force the player to plan around obstacles, and persist across game state save/restore cycles. They are explicitly disabled in Classic and Timed modes to preserve those modes' simpler experience.

## Glossary

- **Board**: The square grid of cells that holds placed pieces and tiles.
- **Cell**: A single square on the Board, identified by (x, y) coordinates.
- **Piece**: A polyomino shape from the player's Hand that the player drags onto the Board.
- **Hand**: The set of available pieces the player can place this turn.
- **Turn**: The complete cycle from the player picking up a piece to the piece being placed and the resulting line breaks resolving.
- **Line Break**: The clearing of a fully filled row or column.
- **Special_Tile**: A tile occupying a cell with non-standard destruction or placement rules; either Frozen, Locked, or Bomb.
- **Frozen_Tile**: A Special_Tile that requires two Line Breaks involving it to be destroyed.
- **Cracked_Frozen_Tile**: A Frozen_Tile that has been hit by one Line Break and shows a cracked overlay.
- **Locked_Tile**: A Special_Tile that can only be removed by a Line Break that includes the cell.
- **Bomb_Tile**: A Special_Tile with a countdown that, when it reaches zero, destroys a 3x3 area centered on itself.
- **Bomb_Countdown**: An integer associated with a Bomb_Tile that decrements each turn.
- **Bomb_Power_Up**: An existing player ability that clears tiles from the Board (referenced for exclusion rules).
- **Game_Mode**: One of Classic, Timed, Chaos, or Daily_Challenge.
- **Chaos_Mode**: A Game_Mode where Special_Tiles are eligible to spawn.
- **Daily_Challenge_Mode**: A Game_Mode where Special_Tiles are eligible to spawn.
- **Spawn_Manager**: The Special_Tile subsystem responsible for selecting cells and creating Special_Tiles each turn.
- **Tile_Renderer**: The Special_Tile subsystem responsible for drawing Special_Tiles within GameView.
- **Persistence_Manager**: The Special_Tile subsystem responsible for serializing and restoring Special_Tile state in the Bundle.

## Requirements

### Requirement 1: Frozen Tile Behavior

**User Story:** As a player, I want Frozen Tiles to require two line breaks to clear, so that the board presents an escalating challenge.

#### Acceptance Criteria

1. THE Board SHALL represent a Frozen_Tile as a cell state that is neither EMPTY nor FILLED.
2. WHEN a Line Break includes a cell containing a Frozen_Tile that has not been previously hit, THE Board SHALL convert that cell to a Cracked_Frozen_Tile.
3. WHEN a Line Break includes a cell containing a Cracked_Frozen_Tile, THE Board SHALL clear the cell to EMPTY.
4. IF a Piece placement would overlap a cell containing a Frozen_Tile or Cracked_Frozen_Tile, THEN THE Board SHALL reject the placement.
5. WHEN evaluating whether a row or column is complete for Line Break purposes, THE Board SHALL count cells containing a Frozen_Tile or Cracked_Frozen_Tile as filled.
6. THE Tile_Renderer SHALL draw a Frozen_Tile using a light-blue ice visual treatment distinguishable from any Piece color.
7. THE Tile_Renderer SHALL draw a Cracked_Frozen_Tile using the Frozen_Tile visual with an additional cracked overlay.

### Requirement 2: Locked Tile Behavior

**User Story:** As a player, I want Locked Tiles that can only be removed by completing a line through them, so that I must commit to clearing strategies rather than relying on power-ups.

#### Acceptance Criteria

1. THE Board SHALL represent a Locked_Tile as a cell state that is neither EMPTY nor FILLED.
2. WHEN a Line Break includes a cell containing a Locked_Tile, THE Board SHALL clear only that Locked_Tile cell to EMPTY along with the other cells normally cleared by the Line Break.
3. IF a Piece placement would overlap a cell containing a Locked_Tile, THEN THE Board SHALL reject the placement.
4. WHEN evaluating whether a row or column is complete for Line Break purposes, THE Board SHALL count cells containing a Locked_Tile as filled.
5. IF the Bomb_Power_Up targets a cell containing a Locked_Tile, THEN THE Board SHALL leave the Locked_Tile unchanged.
6. THE Tile_Renderer SHALL draw a Locked_Tile using a dark gray fill (RGB 64, 64, 64) with a chain icon overlay.

### Requirement 3: Bomb Tile Countdown and Detonation

**User Story:** As a player, I want Bomb Tiles with visible countdowns, so that I can plan placements to detonate them safely or contain their damage.

#### Acceptance Criteria

1. THE Board SHALL represent a Bomb_Tile as a cell state that is neither EMPTY nor FILLED.
2. WHEN a Bomb_Tile is created, THE Spawn_Manager SHALL initialize the Bomb_Countdown for that tile to 5.
3. WHEN the player completes a Piece placement, THE Board SHALL decrement the Bomb_Countdown of every Bomb_Tile on the Board by 1.
4. WHEN a Bomb_Tile's Bomb_Countdown reaches 0, THE Board SHALL clear all cells within a 3x3 area centered on the Bomb_Tile to EMPTY, bounded by the Board edges.
5. IF a Bomb_Tile's 3x3 detonation area contains another Bomb_Tile, THEN THE System SHALL trigger game over.
6. IF a Piece placement would overlap a cell containing a Bomb_Tile, THEN THE Board SHALL reject the placement.
7. WHEN a Line Break includes a cell containing a Bomb_Tile, THE Board SHALL clear the cell to EMPTY without triggering 3x3 detonation.
8. THE Tile_Renderer SHALL draw a Bomb_Tile using a red fill (RGB 200, 30, 30) with the current Bomb_Countdown value rendered in white.

### Requirement 4: Spawn Eligibility by Game Mode

**User Story:** As a player, I want Special Tiles only in Chaos and Daily Challenge modes, so that Classic and Timed modes remain unchanged.

#### Acceptance Criteria

1. WHILE the active Game_Mode is Classic, THE Spawn_Manager SHALL NOT create any Special_Tile.
2. WHILE the active Game_Mode is Timed, THE Spawn_Manager SHALL NOT create any Special_Tile.
3. WHERE the active Game_Mode is Chaos_Mode, THE Spawn_Manager SHALL evaluate Special_Tile spawning at the start of each turn.
4. WHERE the active Game_Mode is Daily_Challenge_Mode, THE Spawn_Manager SHALL evaluate Special_Tile spawning at the start of each turn.

### Requirement 5: Spawn Probability and Limits

**User Story:** As a player, I want Special Tile spawn rates capped, so that the board does not become overwhelmed with obstacles.

#### Acceptance Criteria

1. WHEN evaluating Frozen_Tile spawning at the start of a turn whose number is greater than 5, THE Spawn_Manager SHALL spawn one Frozen_Tile with probability 0.10.
2. WHILE the count of Frozen_Tile and Cracked_Frozen_Tile cells on the Board is greater than or equal to 5, THE Spawn_Manager SHALL NOT spawn additional Frozen_Tiles.
3. WHEN evaluating Locked_Tile spawning at the start of a turn, THE Spawn_Manager SHALL spawn one Locked_Tile with probability 0.05.
4. WHILE the count of Locked_Tile cells on the Board is greater than or equal to 3, THE Spawn_Manager SHALL NOT spawn additional Locked_Tiles.
5. WHEN evaluating Bomb_Tile spawning at the start of a turn, THE Spawn_Manager SHALL spawn one Bomb_Tile with probability 0.03.
6. WHILE the count of Bomb_Tile cells on the Board is greater than or equal to 2, THE Spawn_Manager SHALL NOT spawn additional Bomb_Tiles.
7. WHEN evaluating any Special_Tile spawn, THE Spawn_Manager SHALL check for at least one EMPTY cell before applying the probability roll and the per-type limit check.
8. IF no EMPTY cell is available when a spawn is evaluated, THEN THE Spawn_Manager SHALL skip the spawn for that tile type for that turn.
9. WHEN spawning any Special_Tile, THE Spawn_Manager SHALL select the target cell uniformly at random from cells whose state is EMPTY.

### Requirement 6: Special Tile State Persistence

**User Story:** As a player, I want Special Tiles to be preserved across pause and resume, so that interrupting a game does not lose progress.

#### Acceptance Criteria

1. WHEN GameView saves state to a Bundle, THE Persistence_Manager SHALL include the cell state of every Frozen_Tile, Cracked_Frozen_Tile, Locked_Tile, and Bomb_Tile.
2. WHEN GameView saves state to a Bundle, THE Persistence_Manager SHALL include the Bomb_Countdown value for every Bomb_Tile.
3. WHEN GameView restores state from a Bundle that contains Special_Tile data, THE Persistence_Manager SHALL recreate every Special_Tile at its saved cell coordinates with its saved cell state.
4. WHEN GameView restores state from a Bundle that contains Bomb_Tile data, THE Persistence_Manager SHALL recreate every Bomb_Tile with its saved Bomb_Countdown value.
5. WHEN GameView restores state from a Bundle that contains Special_Tile data, THE Persistence_Manager SHALL preserve the per-game spawn counts so that maximum tile limits remain enforced.

### Requirement 7: Game Over Detection with Special Tiles

**User Story:** As a player, I want game over to account for Special Tile constraints, so that placement availability checks reflect the true board state.

#### Acceptance Criteria

1. WHEN GameView checks whether any Hand Piece can be placed, THE Board SHALL treat cells containing a Frozen_Tile, Cracked_Frozen_Tile, Locked_Tile, or Bomb_Tile as unavailable for placement.
2. IF no Piece in the Hand can be placed on any combination of EMPTY cells given current Special_Tile occupancy, THEN THE System SHALL trigger game over.
3. IF a Bomb_Tile detonation destroys another Bomb_Tile per Requirement 3.5, THEN THE System SHALL display the game over screen with the current score regardless of whether other Hand Pieces could still be placed.
