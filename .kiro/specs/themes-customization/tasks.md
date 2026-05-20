# Implementation Plan: Themes & Customization

## Overview

Implement visual personalization for TileBlast with 6 color palettes and 5 board skins, unlocked via cumulative score. The implementation builds incrementally: domain enums → singleton manager → persistence extension → app context helper → piece/view refactors → settings UI → main menu wiring.

## Tasks

- [ ] 1. Create domain model enums and AppContext helper
  - [ ] 1.1 Create `ColorPalette` enum
    - Create file `app/src/main/java/com/allan/tileblast/theme/ColorPalette.java`
    - Define 6 palettes (DEFAULT, NEON, PASTEL, RETRO, DARK, OCEAN) with display names, unlock thresholds, and 6 RGB color triplets each
    - Implement `getColors()`, `getColor(int idx)` (returns `Color.rgb`), and `fromId(String id)` (fallback to DEFAULT)
    - _Requirements: 1.1, 1.2, 5.2_

  - [ ] 1.2 Create `BoardSkin` enum
    - Create file `app/src/main/java/com/allan/tileblast/theme/BoardSkin.java`
    - Define 5 skins (DEFAULT, WOOD, METAL, SPACE, PIXEL_ART) with display names and unlock thresholds
    - Implement abstract `drawBackground(Canvas c, RectF rect, int blockSize, Paint paint)` with each variant providing its own Canvas-based rendering (no bitmaps)
    - DEFAULT: solid black; WOOD: brown gradient + grain lines; METAL: gray gradient + 45° hatching; SPACE: dark blue + star dots; PIXEL_ART: 2x2 checkerboard
    - Implement `fromId(String id)` (fallback to DEFAULT)
    - _Requirements: 2.1, 2.2, 5.3_

  - [ ] 1.3 Create `AppContext` helper
    - Create file `app/src/main/java/com/allan/tileblast/AppContext.java`
    - Implement static `init(Context c)` that stores `c.getApplicationContext()` and static `get()` that returns it
    - This enables `Piece`'s static methods to access `ThemeManager` without a Context parameter
    - _Requirements: 9.1_

- [ ] 2. Implement ThemeManager singleton and StorageManager extension
  - [ ] 2.1 Extend `StorageManager` with theme persistence methods
    - Add keys: `KEY_ACTIVE_PALETTE`, `KEY_ACTIVE_SKIN`, `KEY_TOTAL_SCORE`
    - Add methods: `getActivePaletteId()`/`setActivePaletteId(String)`, `getActiveSkinId()`/`setActiveSkinId(String)`, `getTotalScore()`/`setTotalScore(int)`/`addToTotalScore(int)`
    - `getTotalScore()` clamps to non-negative; `addToTotalScore` short-circuits on `delta <= 0`
    - _Requirements: 7.2, 8.1, 8.2_

  - [ ] 2.2 Create `ThemeManager` singleton
    - Create file `app/src/main/java/com/allan/tileblast/theme/ThemeManager.java`
    - Implement `getInstance(Context)` with synchronized lazy init using application context
    - Load `totalScore`, `activePalette`, `activeSkin` from `StorageManager` on construction
    - Implement `resolvePalette(String id)` and `resolveSkin(String id)` with fallback to DEFAULT when id is null, unknown, or locked
    - Implement `isUnlocked(ColorPalette)`, `isUnlocked(BoardSkin)` — threshold comparison against totalScore
    - Implement `setActivePalette(ColorPalette)` / `setActiveSkin(BoardSkin)` — return false if locked, persist on success
    - Implement `addToTotalScore(int delta)` — ignore non-positive, accumulate and persist
    - Add `@VisibleForTesting static void resetForTests()` for test isolation
    - _Requirements: 1.3, 2.3, 4.1, 4.2, 5.1, 5.4, 5.5, 5.6, 5.7, 6.3, 6.4, 7.1, 7.3, 7.4, 7.5, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8_

  - [ ]* 2.3 Write property test: Default theme always unlocked (Property 3)
    - **Property 3: Default theme is always unlocked**
    - For any non-negative integer total score, `isUnlocked(ColorPalette.DEFAULT)` and `isUnlocked(BoardSkin.DEFAULT)` return true
    - **Validates: Requirements 5.1**

  - [ ]* 2.4 Write property test: Unlock predicate matches threshold (Property 4)
    - **Property 4: Unlock predicate matches threshold comparison**
    - For any palette/skin and any non-negative total score, `isUnlocked` returns true iff score >= threshold
    - **Validates: Requirements 5.4, 5.5, 5.6, 5.7**

  - [ ]* 2.5 Write property test: Locked selection is a no-op (Property 5)
    - **Property 5: Locked selection is a no-op**
    - For any locked palette/skin, `setActivePalette`/`setActiveSkin` returns false and leaves active unchanged
    - **Validates: Requirements 6.3, 6.4**

  - [ ]* 2.6 Write property test: Total score accumulates non-negative (Property 6)
    - **Property 6: Total score accumulates and stays non-negative**
    - For any sequence of non-negative deltas, resulting total equals sum and remains >= 0
    - **Validates: Requirements 7.1, 7.5**

  - [ ]* 2.7 Write property test: Total score persistence round-trip (Property 7)
    - **Property 7: Total score persistence round-trip**
    - For any non-negative int, after `setTotalScore(t)`, a fresh `ThemeManager` reports `getTotalScore() == t`
    - **Validates: Requirements 7.2, 7.3**

  - [ ]* 2.8 Write property test: Selection persistence round-trip (Property 8)
    - **Property 8: Selection persistence round-trip**
    - For any unlocked palette/skin, after setting them, a fresh `ThemeManager` (same prefs, sufficient score) reports the same selections
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4**

  - [ ]* 2.9 Write property test: Invalid persisted state resolves to Default (Property 9)
    - **Property 9: Invalid persisted state resolves to Default**
    - For any invalid or locked-at-current-score id, a fresh `ThemeManager` resolves to DEFAULT
    - **Validates: Requirements 8.5, 8.6, 8.7, 8.8**

- [ ] 3. Checkpoint - Core logic verification
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 4. Refactor Piece and GameView to use ThemeManager
  - [ ] 4.1 Refactor `Piece` to read colors from ThemeManager
    - Replace `public static final int[][] COLORS` with `public static int[][] getColors()` that delegates to `ThemeManager.getInstance(AppContext.get()).getActivePalette().getColors()`
    - Update `getColor()`, `getColorRGB()`, `getTopBorderColor()`, `getLeftBorderColor()`, `getRightBorderColor()`, `getBottomBorderColor()` to use `getColors()[colorIndex]`
    - Update static methods `getTopBorder(int)`, `getLeftBorder(int)`, `getRightBorder(int)`, `getBottomBorder(int)`, `getColorFromIndex(int)` to use `getColors()[colorIdx]`
    - Replace `COLORS.length` with literal `6` in `getRandomPiece()` and `getRandomColorIndex()`
    - _Requirements: 1.4, 9.1_

  - [ ] 4.2 Refactor `GameView` to use active skin and accumulate total score
    - In `drawGrid()`, replace the hard-coded `paint.setColor(Color.BLACK); canvas.drawRect(...)` grid background with `ThemeManager.getInstance(getContext()).getActiveSkin().drawBackground(canvas, gridRect, blockSize, paint)`
    - In `attemptPlacement()`, after `storageManager.saveScore(...)` on game over, add `ThemeManager.getInstance(getContext()).addToTotalScore(scoreManager.getScore())`
    - Wrap `drawBackground` call in try/catch to fall back to solid black on exception
    - _Requirements: 2.4, 7.1, 9.2, 9.3_

  - [ ]* 4.3 Write property test: Active palette propagates to piece colors (Property 1)
    - **Property 1: Active palette propagates to piece colors**
    - For any unlocked palette, after `setActivePalette(p)`, `Piece.getColors()` equals `p.getColors()`
    - **Validates: Requirements 1.4, 4.1, 9.1**

  - [ ]* 4.4 Write property test: Active skin is the active skin (Property 2)
    - **Property 2: Active skin is the active skin**
    - For any unlocked skin, after `setActiveSkin(s)`, `ThemeManager.getActiveSkin()` returns s
    - **Validates: Requirements 2.4, 4.2, 9.2**

- [ ] 5. Checkpoint - Rendering integration verification
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Implement SettingsActivity with Canvas-based UI
  - [ ] 6.1 Create `SettingsActivity` and `SettingsView`
    - Create file `app/src/main/java/com/allan/tileblast/SettingsActivity.java`
    - Activity sets full-screen flags and hosts a custom `SettingsView extends View`
    - `SettingsView` draws three regions: header with back button, palette grid (2x3), skin strip (1x5), and preview area
    - Each palette tile shows 6 colors as a 2x3 swatch; each skin tile shows a thumbnail via `drawBackground`
    - Selected palette/skin has a visible selection indicator (border highlight)
    - Locked tiles draw a 50% black overlay, lock glyph ("🔒"), and threshold text (e.g., "5,000")
    - _Requirements: 3.3, 3.4, 4.5, 4.6, 6.1, 6.2_

  - [ ] 6.2 Implement touch handling and preview rendering in `SettingsView`
    - Handle `onTouchEvent` ACTION_UP: check back button rect, palette rects, skin rects
    - On unlocked palette/skin tap: call `ThemeManager.setActivePalette`/`setActiveSkin` then `invalidate()`
    - On locked tap: show Toast with "Unlock at X points" message
    - Preview area: draw a 4x4 sample grid using active skin's `drawBackground` and 3 sample pieces using active palette colors via `Piece.getColorFromIndex`
    - Back button tap calls `finish()`
    - _Requirements: 3.5, 4.1, 4.2, 4.3, 4.4, 6.3, 6.4_

  - [ ]* 6.3 Write unit tests for SettingsActivity touch logic
    - Test that tapping unlocked palette updates ThemeManager active palette
    - Test that tapping locked palette leaves active palette unchanged
    - Test that back button finishes activity
    - _Requirements: 4.1, 4.2, 6.3, 6.4_

- [ ] 7. Wire MainActivity and register SettingsActivity
  - [ ] 7.1 Add Settings button to `activity_main.xml` and wire in `MainActivity`
    - Add a new button layout (similar style to existing buttons) with id `btnSettings` and text "Settings" to `activity_main.xml`
    - In `MainActivity.onCreate`, add `AppContext.init(getApplicationContext())` at the top
    - Wire `btnSettings` click listener to launch `SettingsActivity`
    - Apply font styling consistent with other buttons
    - _Requirements: 3.1, 3.2_

  - [ ] 7.2 Register `SettingsActivity` in `AndroidManifest.xml`
    - Add `<activity android:name=".SettingsActivity" />` entry
    - _Requirements: 3.2_

  - [ ]* 7.3 Write unit tests for MainActivity settings navigation
    - Test that Settings button exists and launches SettingsActivity on click
    - Test that AppContext is initialized in onCreate
    - _Requirements: 3.1, 3.2_

- [ ] 8. Final checkpoint - Full integration verification
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (Properties 1-9)
- All skins use Canvas primitives only — no bitmap assets added to APK
- The `AppContext` helper avoids threading Context through Piece's static API
- `Board`, `Hand`, and `ScoreManager` are intentionally untouched

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["2.1"] },
    { "id": 2, "tasks": ["2.2"] },
    { "id": 3, "tasks": ["2.3", "2.4", "2.5", "2.6", "2.7", "2.8", "2.9"] },
    { "id": 4, "tasks": ["4.1", "4.2"] },
    { "id": 5, "tasks": ["4.3", "4.4"] },
    { "id": 6, "tasks": ["6.1"] },
    { "id": 7, "tasks": ["6.2"] },
    { "id": 8, "tasks": ["6.3", "7.1", "7.2"] },
    { "id": 9, "tasks": ["7.3"] }
  ]
}
```
