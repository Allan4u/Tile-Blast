# Requirements Document

## Introduction

Enhanced Visual Feedback adds particle effects, screen flashes, zoom pulses, animated text, a perfect clear bonus, smooth piece snap animations, and score pop animations to the TileBlast Android game. These effects increase game juice and player satisfaction by providing rich visual responses to gameplay actions. All rendering is Canvas-based within the existing custom GameView.

## Glossary

- **GameView**: The custom Android View responsible for all game rendering and touch input handling via Canvas
- **Particle_System**: A subsystem that manages the lifecycle (spawn, update, draw, recycle) of small visual elements used for explosion and celebration effects
- **Particle**: A small colored visual element with position, velocity, color, alpha, and lifetime properties
- **Line_Break**: The event when a complete row or column of filled cells is cleared from the board
- **Combo_Level**: An integer tracked by ScoreManager representing consecutive line breaks without a two-turn gap
- **Screen_Flash**: A full-screen semi-transparent color overlay that appears briefly and fades out
- **Zoom_Pulse**: A temporary scale transformation applied to the grid area that grows and returns to normal
- **Combo_Text**: The overlay text displayed when a combo is achieved, showing the combo multiplier
- **Perfect_Clear**: The state when the board contains zero filled cells after a piece placement and line break
- **Piece_Snap_Animation**: A short interpolated movement of a piece from its drag release position to its final grid position
- **Score_Pop**: A floating text element showing point gains that rises upward and fades out
- **Easing_Function**: A mathematical function that controls the rate of change of an animation parameter over time
- **Board**: The grid data structure (NxN) that tracks filled and empty cells
- **ScoreManager**: The component that tracks score, combo level, and calculates point bonuses
- **AudioManager**: The singleton that manages sound effect playback via SoundPool

## Requirements

### Requirement 1: Particle Effects on Line Break

**User Story:** As a player, I want to see blocks explode into colored particles when a line is cleared, so that line breaks feel impactful and satisfying.

#### Acceptance Criteria

1. WHEN a Line_Break occurs, THE Particle_System SHALL spawn 4 to 8 Particle instances per cleared cell at the cell's screen position
2. WHEN Particle instances are spawned, THE Particle_System SHALL assign each Particle the same color as the cleared cell's block color
3. WHEN Particle instances are spawned, THE Particle_System SHALL assign each Particle a random velocity vector with magnitude between 100 and 400 pixels per second
4. WHILE a Particle is active, THE Particle_System SHALL update the Particle position by applying its velocity and a downward gravity of 800 pixels per second squared each frame
5. WHILE a Particle is active, THE Particle_System SHALL reduce the Particle alpha linearly from 1.0 to 0.0 over the Particle lifetime of 400 to 700 milliseconds
6. WHEN a Particle alpha reaches 0.0 or its lifetime expires, THE Particle_System SHALL remove the Particle from the active set
7. THE Particle_System SHALL render all active Particle instances as filled circles with radius between 2 and 5 density-independent pixels

### Requirement 2: Screen Flash on Big Combos

**User Story:** As a player, I want to see a brief screen flash when I achieve a high combo, so that big combos feel powerful and rewarding.

#### Acceptance Criteria

1. WHEN Combo_Level reaches 3 or higher after a Line_Break, THE GameView SHALL display a Screen_Flash overlay covering the entire view
2. WHEN a Screen_Flash is triggered at Combo_Level 3, THE GameView SHALL render the overlay with a white color at 15% initial opacity
3. WHEN a Screen_Flash is triggered at Combo_Level 4 or higher, THE GameView SHALL render the overlay with a gold color (0xFFFFD700) at an initial opacity of 15% plus 5% per combo level above 3, capped at 40%
4. WHEN a Screen_Flash is displayed, THE GameView SHALL fade the overlay opacity from its initial value to 0% over a duration between 100 and 200 milliseconds
5. WHILE a Screen_Flash is active, THE GameView SHALL continue rendering all game elements beneath the overlay

### Requirement 3: Camera Zoom Pulse on Big Combos

**User Story:** As a player, I want the grid to pulse with a subtle zoom effect on large combos or multi-line breaks, so that these moments feel dramatic.

#### Acceptance Criteria

1. WHEN Combo_Level reaches 4 or higher, or lines broken in a single placement reaches 3 or more, THE GameView SHALL apply a Zoom_Pulse to the grid rendering area
2. WHEN a Zoom_Pulse is triggered, THE GameView SHALL scale the grid area from 1.0 to 1.03 over the first 150 milliseconds using an ease-out Easing_Function
3. WHEN a Zoom_Pulse reaches peak scale of 1.03, THE GameView SHALL scale the grid area from 1.03 back to 1.0 over the next 150 milliseconds using an ease-in Easing_Function
4. THE GameView SHALL apply the Zoom_Pulse scale transformation around the center point of the grid area
5. IF a new Zoom_Pulse is triggered while a previous Zoom_Pulse is active, THEN THE GameView SHALL replace the previous pulse with the new one starting from the current scale value

### Requirement 4: Animated Combo Text

**User Story:** As a player, I want the combo text to animate in with a bouncy scale effect, so that combo notifications feel dynamic rather than static.

#### Acceptance Criteria

1. WHEN a Combo_Text is triggered, THE GameView SHALL animate the text scale from 0.0 to 1.0 with an overshoot Easing_Function over 300 milliseconds
2. WHEN the Combo_Text scale animation completes, THE GameView SHALL hold the text at full scale for 400 milliseconds
3. WHEN the Combo_Text hold period ends, THE GameView SHALL fade the text alpha from 1.0 to 0.0 over 300 milliseconds
4. THE GameView SHALL apply the overshoot easing with a peak scale of 1.2 before settling to 1.0
5. WHILE the Combo_Text is animating, THE GameView SHALL center the text horizontally and position it at 30% of the view height from the top

### Requirement 5: Perfect Clear Bonus

**User Story:** As a player, I want to be rewarded with bonus points and a special celebration when I completely clear the board, so that achieving a perfect clear feels like a major accomplishment.

#### Acceptance Criteria

1. WHEN all cells on the Board are empty after a piece placement and Line_Break processing, THE ScoreManager SHALL award a Perfect_Clear bonus of 500 points
2. WHEN a Perfect_Clear is detected, THE GameView SHALL display the text "PERFECT CLEAR" centered on screen with a gold color and font size of 48 density-independent pixels
3. WHEN a Perfect_Clear is detected, THE Particle_System SHALL spawn a celebration effect of 80 to 120 gold-colored Particle instances from the center of the screen with random velocities in all directions
4. WHEN a Perfect_Clear is detected, THE GameView SHALL animate the "PERFECT CLEAR" text with the same overshoot scale animation as Combo_Text, with a hold duration of 800 milliseconds
5. WHEN a Perfect_Clear is detected, THE AudioManager SHALL play a dedicated perfect clear sound effect
6. WHEN a Perfect_Clear celebration is active, THE GameView SHALL render the celebration above all other game elements except pause and game-over overlays

### Requirement 6: Smooth Piece Snap Animation

**User Story:** As a player, I want placed pieces to smoothly animate into their grid position, so that placements feel polished rather than abrupt.

#### Acceptance Criteria

1. WHEN a piece is successfully placed on the Board, THE GameView SHALL animate the piece from its drag release position to its final grid position over 100 milliseconds
2. WHEN a Piece_Snap_Animation is active, THE GameView SHALL interpolate the piece position using an ease-out Easing_Function
3. WHILE a Piece_Snap_Animation is active, THE GameView SHALL render the piece at its interpolated position with full opacity
4. WHEN the Piece_Snap_Animation completes, THE GameView SHALL commit the piece to the Board and proceed with Line_Break detection
5. IF the player begins dragging a new piece while a Piece_Snap_Animation is active, THEN THE GameView SHALL immediately complete the in-progress animation and commit the piece

### Requirement 7: Score Pop Animation

**User Story:** As a player, I want to see floating score numbers when I earn points, so that I have immediate visual feedback on point gains.

#### Acceptance Criteria

1. WHEN the score increases, THE GameView SHALL spawn a Score_Pop text displaying the point gain prefixed with "+" at the score display position
2. WHEN a Score_Pop is spawned, THE GameView SHALL animate the text rising upward by 60 density-independent pixels over 800 milliseconds
3. WHILE a Score_Pop is active, THE GameView SHALL fade the text alpha from 1.0 to 0.0 over its 800 millisecond lifetime
4. THE GameView SHALL render Score_Pop text in white color with a font size of 20 density-independent pixels using the bold typeface
5. IF multiple score increases occur within 200 milliseconds, THEN THE GameView SHALL display each Score_Pop with a vertical offset to prevent overlap
