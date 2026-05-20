# Requirements Document

## Introduction

Themes & Customization adds visual personalization to TileBlast. Players can choose from 6 color palettes (which recolor the 6 piece colors) and 5 board skins (which change the grid background). Selections are made from a Settings screen accessible from the main menu, with immediate visual feedback. Most palettes and skins start locked and unlock as the player accumulates total cumulative score across all games and modes. Selections and total score are persisted across sessions via SharedPreferences.

## Glossary

- **Theme_Manager**: The Android component responsible for loading, applying, and persisting the active color palette and board skin.
- **Settings_Screen**: The Android Activity that displays palette and skin selection sections, an in-screen preview, and unlock states.
- **Color_Palette**: A named set of 6 RGB piece colors used by the game's pieces and board cells. The 6 palettes are: Default, Neon, Pastel, Retro, Dark, Ocean.
- **Board_Skin**: A named visual treatment for the board grid background. The 5 skins are: Default, Wood, Metal, Space, Pixel Art.
- **Active_Palette**: The Color_Palette currently applied to the game's rendering.
- **Active_Skin**: The Board_Skin currently applied to the game's rendering.
- **Total_Score**: The cumulative sum of all per-game scores the player has earned across all modes since first launch, stored persistently.
- **Unlock_Threshold**: The Total_Score value at or above which a specific Color_Palette or Board_Skin becomes selectable.
- **Storage_Manager**: The existing component (`StorageManager`) responsible for reading and writing SharedPreferences values.
- **Preview_Area**: A region on the Settings_Screen that renders sample pieces and a sample grid using the currently selected palette and skin.

## Requirements

### Requirement 1: Color Palette Catalog

**User Story:** As a player, I want six distinct color palettes available, so that I can change the look of the game pieces.

#### Acceptance Criteria

1. THE Theme_Manager SHALL define exactly 6 Color_Palettes named Default, Neon, Pastel, Retro, Dark, and Ocean.
2. THE Theme_Manager SHALL define exactly 6 RGB piece colors for each Color_Palette.
3. WHEN the application starts for the first time, THE Theme_Manager SHALL set the Active_Palette to Default.
4. WHEN a Color_Palette is set as Active_Palette, THE Theme_Manager SHALL replace the 6 piece colors used for piece rendering with the colors of that palette.

### Requirement 2: Board Skin Catalog

**User Story:** As a player, I want five board skins available, so that I can change the look of the playing grid.

#### Acceptance Criteria

1. THE Theme_Manager SHALL define exactly 5 Board_Skins named Default, Wood, Metal, Space, and Pixel Art.
2. THE Theme_Manager SHALL define a background drawable or color specification for each Board_Skin.
3. WHEN the application starts for the first time, THE Theme_Manager SHALL set the Active_Skin to Default.
4. WHEN a Board_Skin is set as Active_Skin, THE Theme_Manager SHALL render the board grid background using that skin's specification.

### Requirement 3: Settings Screen Access

**User Story:** As a player, I want a Settings option on the main menu, so that I can open the customization screen.

#### Acceptance Criteria

1. THE MainActivity SHALL display a Settings button on the main menu.
2. WHEN the Settings button is tapped, THE MainActivity SHALL launch the Settings_Screen.
3. WHEN the Settings_Screen is displayed, THE Settings_Screen SHALL show a Color Palette section and a Board Skin section.
4. WHEN the Settings_Screen is displayed, THE Settings_Screen SHALL show a Preview_Area that renders sample pieces and a sample grid using the current Active_Palette and Active_Skin.
5. WHEN a back navigation gesture or back button is performed on the Settings_Screen, THE Settings_Screen SHALL close and return the user to the main menu.

### Requirement 4: Selection and Immediate Apply

**User Story:** As a player, I want my selection to apply immediately, so that I can see the effect without leaving the screen.

#### Acceptance Criteria

1. WHEN a player taps an unlocked Color_Palette in the Color Palette section, THE Theme_Manager SHALL set the tapped palette as Active_Palette before the next frame is drawn.
2. WHEN a player taps an unlocked Board_Skin in the Board Skin section, THE Theme_Manager SHALL set the tapped skin as Active_Skin before the next frame is drawn.
3. WHEN the Active_Palette changes, THE Preview_Area SHALL re-render using the new Active_Palette within 100ms.
4. WHEN the Active_Skin changes, THE Preview_Area SHALL re-render using the new Active_Skin within 100ms.
5. THE Settings_Screen SHALL visually mark the currently selected Color_Palette with a selection indicator.
6. THE Settings_Screen SHALL visually mark the currently selected Board_Skin with a selection indicator.

### Requirement 5: Unlock Conditions

**User Story:** As a player, I want to unlock palettes and skins by playing well, so that I have goals to work toward.

#### Acceptance Criteria

1. THE Theme_Manager SHALL treat the Default Color_Palette and the Default Board_Skin as unlocked at all times.
2. THE Theme_Manager SHALL assign Unlock_Thresholds for the non-default Color_Palettes as follows: Neon = 5000, Pastel = 10000, Retro = 25000, Dark = 50000, Ocean = 100000.
3. THE Theme_Manager SHALL assign Unlock_Thresholds for the non-default Board_Skins as follows: Wood = 5000, Metal = 25000, Space = 50000, Pixel Art = 100000.
4. WHILE the Total_Score is greater than or equal to a Color_Palette's Unlock_Threshold, THE Theme_Manager SHALL report that Color_Palette as unlocked.
5. WHILE the Total_Score is less than a Color_Palette's Unlock_Threshold, THE Theme_Manager SHALL report that Color_Palette as locked.
6. WHILE the Total_Score is greater than or equal to a Board_Skin's Unlock_Threshold, THE Theme_Manager SHALL report that Board_Skin as unlocked.
7. WHILE the Total_Score is less than a Board_Skin's Unlock_Threshold, THE Theme_Manager SHALL report that Board_Skin as locked.

### Requirement 6: Locked Item Display and Interaction

**User Story:** As a player, I want to see which palettes and skins are locked and what is required to unlock them, so that I know what to aim for.

#### Acceptance Criteria

1. WHEN the Settings_Screen renders a locked Color_Palette, THE Settings_Screen SHALL display a lock icon and the Unlock_Threshold value for that palette.
2. WHEN the Settings_Screen renders a locked Board_Skin, THE Settings_Screen SHALL display a lock icon and the Unlock_Threshold value for that skin.
3. IF a player taps a locked Color_Palette, THEN THE Settings_Screen SHALL leave the Active_Palette unchanged and display a message containing the Unlock_Threshold required.
4. IF a player taps a locked Board_Skin, THEN THE Settings_Screen SHALL leave the Active_Skin unchanged and display a message containing the Unlock_Threshold required.

### Requirement 7: Total Score Tracking

**User Story:** As a player, I want every game I play to count toward unlocking themes, so that progress is continuous across all my games.

#### Acceptance Criteria

1. WHEN a game ends with a final score, THE Theme_Manager SHALL add that final score to the persisted Total_Score.
2. THE Storage_Manager SHALL persist Total_Score in SharedPreferences under a dedicated key.
3. WHEN the application starts, THE Theme_Manager SHALL load Total_Score from the Storage_Manager.
4. IF no Total_Score value exists in SharedPreferences, THEN THE Theme_Manager SHALL initialize Total_Score to 0.
5. THE Total_Score SHALL be a non-negative integer.

### Requirement 8: Selection Persistence

**User Story:** As a player, I want my chosen palette and skin to be remembered, so that the game looks the same the next time I play.

#### Acceptance Criteria

1. WHEN the Active_Palette is changed, THE Storage_Manager SHALL persist the palette identifier in SharedPreferences under a dedicated key.
2. WHEN the Active_Skin is changed, THE Storage_Manager SHALL persist the skin identifier in SharedPreferences under a dedicated key.
3. WHEN the application starts, THE Theme_Manager SHALL load the persisted palette identifier and set it as Active_Palette.
4. WHEN the application starts, THE Theme_Manager SHALL load the persisted skin identifier and set it as Active_Skin.
5. IF the persisted palette identifier refers to a Color_Palette whose current unlock state is locked, THEN THE Theme_Manager SHALL set Active_Palette to Default.
6. IF the persisted palette identifier is missing or unrecognized, THEN THE Theme_Manager SHALL set Active_Palette to Default.
7. IF the persisted skin identifier refers to a Board_Skin whose current unlock state is locked, THEN THE Theme_Manager SHALL set Active_Skin to Default.
8. IF the persisted skin identifier is missing or unrecognized, THEN THE Theme_Manager SHALL set Active_Skin to Default.

### Requirement 9: Application Across Game Screens

**User Story:** As a player, I want my selected palette and skin to apply across all game modes, so that the game consistently reflects my choices.

#### Acceptance Criteria

1. WHEN the GameView renders pieces, THE GameView SHALL use the colors of the Active_Palette.
2. WHEN the GameView renders the board grid background, THE GameView SHALL use the specification of the Active_Skin.
3. WHEN the GameActivity starts, THE GameActivity SHALL apply the Active_Palette and Active_Skin to the GameView before the first frame is drawn.
