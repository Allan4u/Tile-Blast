# TileBlast All-Bugs-Fix Design

## Overview

This design addresses 10 bugs in the TileBlast Android game spanning touch handling, resource management, density-independent rendering, API compatibility, storage efficiency, state persistence, build configuration, and audio loading. The fix strategy is minimal and targeted: each bug is corrected at its root cause without altering unrelated behavior. The bugs cluster into four categories: UI touch accuracy (bugs 1, 4, 10), resource lifecycle (bugs 2, 9), density/scaling (bugs 3, 4), and architecture/config (bugs 5, 6, 7, 8).

## Glossary

- **Bug_Condition (C)**: The specific input or state that triggers incorrect behavior in the unfixed code
- **Property (P)**: The desired correct behavior that must hold after the fix is applied
- **Preservation**: Existing correct behavior that must remain unchanged by the fix
- **GameView**: The custom `View` in `GameView.java` responsible for rendering the game board, hand pieces, overlays, and handling all touch events
- **AudioManager**: Singleton in `AudioManager.java` wrapping Android `SoundPool` for game sound effects
- **StorageManager**: Class in `StorageManager.java` persisting high scores to `SharedPreferences` as a JSON array
- **GameActivity**: The `AppCompatActivity` hosting `GameView`, managing lifecycle and back-press handling
- **blockSize**: The pixel dimension of one grid cell, currently capped with a hardcoded pixel value
- **dp (density-independent pixel)**: A virtual pixel unit that scales with screen density (1dp = 1px at 160dpi)

## Bug Details

### Bug Condition

The bugs manifest across multiple subsystems. The common thread is that each defect activates under a specific, identifiable condition:

1. **Touch area too wide (Pause overlay)**: `handlePauseTouch` checks only Y-coordinate, ignoring X bounds
2. **SoundPool leak**: `AudioManager.release()` is never called from any lifecycle method
3. **Hardcoded pixel values**: `calculateLayout` uses `40` px offset and `46 * 3` px cap without density scaling
4. **Pause button too small**: `pauseBtnRect` is 50×50 px which is < 48dp on densities > ~1.04
5. **Deprecated onBackPressed**: `GameActivity.onBackPressed()` override is deprecated on API 33+
6. **Unbounded score storage**: `saveScore` appends without pruning
7. **State lost on process death**: No `onSaveInstanceState`/`onRestoreInstanceState` implementation
8. **Release signed with debug key**: `build.gradle.kts` sets `signingConfig = signingConfigs.getByName("debug")` for release
9. **First sound silent**: `soundPool.play()` called before `onLoadComplete` fires
10. **Touch area too wide (GameOver overlay)**: `handleGameOverTouch` checks only Y-coordinate, ignoring X bounds

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type BugInput {bugId: int, context: varies}
  OUTPUT: boolean

  SWITCH input.bugId:
    CASE 1, 10:  // Touch area too wide
      RETURN input.ty IS WITHIN button_Y_range
             AND input.tx IS NOT WITHIN button_X_range
             AND overlay IS VISIBLE (paused OR gameOver)

    CASE 2:  // SoundPool leak
      RETURN input.lifecycleEvent = ON_DESTROY
             AND AudioManager.soundPool ≠ NULL

    CASE 3:  // Density offset
      RETURN input.displayDensity ≠ 1.0

    CASE 4:  // Pause button too small
      RETURN input.displayDensity > 1.0
             AND pauseBtnRect.width() < 48 * input.displayDensity

    CASE 5:  // Deprecated onBackPressed
      RETURN input.apiLevel >= 33

    CASE 6:  // Unbounded scores
      RETURN storedScores.length >= MAX_SCORES

    CASE 7:  // State lost
      RETURN input.lifecycleEvent = PROCESS_DEATH
             AND gameState.inProgress = TRUE

    CASE 8:  // Debug keystore on release
      RETURN input.buildType = "release"

    CASE 9:  // Sound not loaded
      RETURN input.soundLoaded = FALSE
             AND input.playRequested = TRUE

  END SWITCH
END FUNCTION
```

### Examples

- **Bug 1**: Pause overlay visible, user taps at (50, cy+140) where button X range is [w/2-110, w/2+110]. Tap is outside X bounds but within Y range → Resume triggers incorrectly
- **Bug 2**: User finishes game, presses home, system kills process → SoundPool native memory never freed
- **Bug 3**: On a 3x density (xxhdpi) device, the 40px drag offset feels like only ~13dp above the finger instead of the intended ~40dp
- **Bug 4**: On a 3x density device, the 50×50px pause button is only ~17dp × 17dp, far below the 48dp minimum
- **Bug 6**: After 10,000 games, the scores JSON string in SharedPreferences is ~500KB, causing slow reads
- **Bug 9**: On a slow device, `playPlace()` is called within 50ms of AudioManager construction → `soundPool.play()` returns 0 (silence)
- **Bug 10**: Game over overlay visible, user taps at (20, cy+200) where button X range is [w/2-110, w/2+110] → "Back to Menu" triggers incorrectly

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Taps within the actual visible bounds of overlay buttons must continue to trigger their respective actions (Resume, Quit, Back to Menu)
- All sound effects (place, break, combo, gameover, juiciness) must continue to play at the configured volume during normal gameplay
- On baseline-density (mdpi, density=1.0) devices, the drag offset and block sizing must remain visually identical
- The pause button must continue to be tappable within its visible area
- Back-press behavior on API < 33 must remain unchanged (pause or finish)
- `getHighScores()` must continue to return scores sorted descending with the correct limit
- Normal game completion (without process death) must continue to save scores and show game over
- Debug builds must continue to use the debug keystore
- Grid, HUD, hand pieces, combo overlays, and shake effects must render with the same visual appearance

**Scope:**
All inputs that do NOT match any bug condition should produce identical results before and after the fix. This includes:
- Taps within button bounds on overlays
- Sound playback after all sounds have loaded
- Layout calculations on mdpi devices
- Back-press on API < 33
- Score storage when count is below the pruning threshold
- Normal activity lifecycle without process death

## Hypothesized Root Cause

Based on code analysis, the root causes are:

1. **Bug 1 & 10 - Missing X-bound check**: `handlePauseTouch` and `handleGameOverTouch` only compare `ty` against Y ranges. They never check `tx` against the button's X bounds (`cx - bw/2` to `cx + bw/2` where `bw=220`).

2. **Bug 2 - No lifecycle hook for release**: `AudioManager` has a `release()` method but `GameActivity` never calls it in `onDestroy()`. The singleton pattern means the SoundPool persists until process death.

3. **Bug 3 - Hardcoded pixel constants**: In `calculateLayout`, the drag offset (`-40` in `drawDraggingPiece` and `updateHoverPreview`) and the blockSize cap (`46 * 3`) are raw pixel values, not scaled by `getResources().getDisplayMetrics().density`.

4. **Bug 4 - Fixed pixel dimensions for pause button**: `pauseBtnRect` is set to a 50×50 pixel rectangle. On high-density screens this is smaller than 48dp.

5. **Bug 5 - Direct override of deprecated method**: `GameActivity` overrides `onBackPressed()` which is deprecated in API 33. Should use `getOnBackPressedDispatcher().addCallback()`.

6. **Bug 6 - Append-only storage**: `saveScore` does `arr.put(obj)` and writes back without any size check or pruning logic.

7. **Bug 7 - No state serialization**: `GameActivity` and `GameView` do not implement `onSaveInstanceState` or persist game state to a `Bundle`.

8. **Bug 8 - Explicit debug signing for release**: `build.gradle.kts` line `signingConfig = signingConfigs.getByName("debug")` in the `release` block.

9. **Bug 9 - No load-completion guard**: `AudioManager` sets `loaded = true` in `onLoadComplete` but the `play*()` methods never check this flag. Additionally, `loaded` is set to `true` on the first sound that finishes loading, not when all sounds are loaded.

10. **Bug 10 - Same as Bug 1**: `handleGameOverTouch` has the identical Y-only check pattern.

## Correctness Properties

Property 1: Bug Condition - Overlay Touch Bounds (Bugs 1, 10)

_For any_ touch event on the pause or game-over overlay where the tap Y-coordinate is within a button's Y range but the X-coordinate is outside the button's X range, the fixed `handlePauseTouch` and `handleGameOverTouch` functions SHALL take no action (not trigger Resume, Quit, or Back to Menu).

**Validates: Requirements 2.1, 2.10**

Property 2: Preservation - Valid Overlay Taps

_For any_ touch event on the pause or game-over overlay where the tap is within both the X and Y bounds of a button, the fixed functions SHALL produce the same result as the original functions, preserving correct button activation.

**Validates: Requirements 3.1, 3.2, 3.10**

Property 3: Bug Condition - SoundPool Resource Release (Bug 2)

_For any_ activity destruction event, the fixed lifecycle code SHALL call `AudioManager.release()`, ensuring `soundPool` is released and set to null.

**Validates: Requirements 2.2**

Property 4: Preservation - Sound Playback During Gameplay

_For any_ sound play request where sounds are loaded and the activity is active, the fixed code SHALL produce the same audio output as the original code.

**Validates: Requirements 3.3**

Property 5: Bug Condition - Density-Independent Layout (Bug 3)

_For any_ device with display density ≠ 1.0, the fixed `calculateLayout` and drag rendering SHALL compute offsets and caps using dp-to-px conversion (`value * density`), producing visually consistent results across densities.

**Validates: Requirements 2.3**

Property 6: Preservation - Baseline Density Layout

_For any_ device with display density = 1.0 (mdpi), the fixed layout calculations SHALL produce identical pixel values to the original code.

**Validates: Requirements 3.4**

Property 7: Bug Condition - Pause Button Minimum Size (Bug 4)

_For any_ screen density, the fixed pause button touch target SHALL be at least 48dp × 48dp (i.e., at least `48 * density` pixels in each dimension).

**Validates: Requirements 2.4**

Property 8: Bug Condition - OnBackPressedDispatcher (Bug 5)

_For any_ back-press event on API 33+, the fixed code SHALL handle it via `OnBackPressedDispatcher` callback rather than the deprecated `onBackPressed()` override.

**Validates: Requirements 2.5**

Property 9: Preservation - Back Press on API < 33

_For any_ back-press event on API < 33, the fixed code SHALL produce the same pause/finish behavior as the original code.

**Validates: Requirements 3.6**

Property 10: Bug Condition - Score Pruning (Bug 6)

_For any_ call to `saveScore` when the stored scores count exceeds the maximum threshold, the fixed function SHALL prune the array to retain only the top N scores (sorted by score descending).

**Validates: Requirements 2.6**

Property 11: Preservation - Score Retrieval

_For any_ call to `getHighScores`, the fixed code SHALL return the same sorted, limited results as the original code for scores within the retention window.

**Validates: Requirements 3.7**

Property 12: Bug Condition - State Persistence on Process Death (Bug 7)

_For any_ activity recreation after process death where a game was in progress, the fixed code SHALL restore the game state (board, hand, score, combo) from the saved instance state.

**Validates: Requirements 2.7**

Property 13: Preservation - Normal Game Completion

_For any_ normal game completion (without process death), the fixed code SHALL continue to save the score and show the game over screen identically to the original.

**Validates: Requirements 3.8**

Property 14: Bug Condition - Release Build Signing (Bug 8)

_For any_ release build, the fixed build configuration SHALL NOT reference the debug signing config.

**Validates: Requirements 2.8**

Property 15: Preservation - Debug Build Signing

_For any_ debug build, the fixed build configuration SHALL continue to use the debug keystore.

**Validates: Requirements 3.9**

Property 16: Bug Condition - Sound Load Guard (Bug 9)

_For any_ sound play request where the requested sound has not yet finished loading, the fixed code SHALL either queue the request until loading completes or skip playback, never calling `soundPool.play()` on an unloaded sound.

**Validates: Requirements 2.9**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `app/src/main/java/com/allan/tileblast/game/GameView.java`

**Functions**: `handlePauseTouch`, `handleGameOverTouch`, `calculateLayout`, `drawDraggingPiece`, `updateHoverPreview`, `drawPauseButton`

**Specific Changes**:

1. **Add X-bound checks to `handlePauseTouch`** (Bug 1):
   - Compute button X bounds: `float bx1 = w/2f - 110, bx2 = w/2f + 110`
   - Add `tx > bx1 && tx < bx2` condition to each button Y-range check
   
2. **Add X-bound checks to `handleGameOverTouch`** (Bug 10):
   - Same pattern as Bug 1: compute button X bounds and add X check

3. **Convert hardcoded pixel values to dp** (Bug 3):
   - Store `density = getResources().getDisplayMetrics().density` as a field
   - Replace `40` offset with `(int)(40 * density)` in `drawDraggingPiece` and `updateHoverPreview`
   - Replace `46 * 3` cap with `(int)(46 * 3 * density)` in `calculateLayout`
   - Replace `gridPadding = 40` with `(int)(40 * density)`

4. **Ensure pause button is at least 48dp** (Bug 4):
   - Compute minimum size: `int minPx = (int)(48 * density)`
   - Set `pauseBtnRect` dimensions to `max(50, minPx)` pixels

5. **Add sound load tracking per sound ID** (Bug 9):
   - Replace single `boolean loaded` with a `Set<Integer> loadedIds`
   - In `onLoadComplete`, add the sound ID to the set
   - In each `play*()` method, check if the sound ID is in `loadedIds` before calling `soundPool.play()`

---

**File**: `app/src/main/java/com/allan/tileblast/GameActivity.java`

**Functions**: `onCreate`, `onDestroy` (new), `onBackPressed` → `OnBackPressedCallback`, `onSaveInstanceState` (new)

**Specific Changes**:

6. **Call AudioManager.release() in onDestroy** (Bug 2):
   - Override `onDestroy()`, call `AudioManager.getInstance(this).release()` then `super.onDestroy()`

7. **Replace onBackPressed with OnBackPressedDispatcher** (Bug 5):
   - Remove `onBackPressed()` override
   - In `onCreate`, register an `OnBackPressedCallback` via `getOnBackPressedDispatcher().addCallback(this, callback)`
   - Callback logic: if game not over and not paused → pause; if paused → finish; if game over → finish

8. **Implement onSaveInstanceState / onRestoreInstanceState** (Bug 7):
   - In `GameView`, add `saveState(Bundle)` and `restoreState(Bundle)` methods that serialize/deserialize board, hand, score, combo
   - In `GameActivity.onSaveInstanceState`, call `gameView.saveState(outState)`
   - In `GameActivity.onCreate`, if `savedInstanceState != null`, call `gameView.restoreState(savedInstanceState)`

---

**File**: `app/src/main/java/com/allan/tileblast/storage/StorageManager.java`

**Function**: `saveScore`

**Specific Changes**:

9. **Prune scores after adding** (Bug 6):
   - After `arr.put(obj)`, sort the array by score descending
   - If `arr.length() > MAX_SCORES` (e.g., 100), truncate to `MAX_SCORES` entries
   - Write the pruned array back to SharedPreferences

---

**File**: `app/build.gradle.kts`

**Block**: `buildTypes.release`

**Specific Changes**:

10. **Remove debug signing from release** (Bug 8):
    - Remove or comment out `signingConfig = signingConfigs.getByName("debug")` from the `release` block
    - Optionally add a placeholder comment for future release signing configuration

---

**File**: `app/src/main/java/com/allan/tileblast/audio/AudioManager.java`

**Functions**: `AudioManager` constructor, `play*()` methods

**Specific Changes**:

11. **Track per-sound load state** (Bug 9):
    - Add `private Set<Integer> loadedSoundIds = new HashSet<>()`
    - In `onLoadComplete` listener: `loadedSoundIds.add(id)` (instead of `loaded = true`)
    - In each `play*()` method: guard with `if (soundPool != null && loadedSoundIds.contains(soundId))`

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bugs on unfixed code, then verify the fixes work correctly and preserve existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bugs BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write unit tests that exercise each bug condition on the unfixed code. Run these tests to observe failures and confirm root causes.

**Test Cases**:
1. **Pause Overlay X-Bounds Test**: Simulate tap at (10, cy+140) on pause overlay → Resume triggers incorrectly (will fail on unfixed code)
2. **GameOver Overlay X-Bounds Test**: Simulate tap at (10, cy+200) on game-over overlay → Back to Menu triggers incorrectly (will fail on unfixed code)
3. **SoundPool Leak Test**: Create AudioManager, simulate onDestroy, assert soundPool is null (will fail on unfixed code)
4. **Density Offset Test**: Set density=3.0, call calculateLayout, assert blockSize cap = 46*3*3 (will fail on unfixed code)
5. **Pause Button Size Test**: Set density=3.0, assert pauseBtnRect width >= 48*3 (will fail on unfixed code)
6. **Score Pruning Test**: Add 150 scores, call saveScore, assert stored count ≤ 100 (will fail on unfixed code)
7. **Sound Load Guard Test**: Call playPlace() before onLoadComplete fires, assert no soundPool.play() call (will fail on unfixed code)
8. **State Persistence Test**: Simulate process death with game in progress, recreate activity, assert state restored (will fail on unfixed code)

**Expected Counterexamples**:
- Touch handlers fire actions for out-of-X-bounds taps
- SoundPool remains non-null after activity destruction
- BlockSize cap is 138px regardless of density
- Scores array grows without bound
- `soundPool.play()` called with unloaded sound ID returns 0

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := fixedFunction(input)
  ASSERT expectedBehavior(result)
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT originalFunction(input) = fixedFunction(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many random touch coordinates, densities, and score counts automatically
- It catches edge cases at boundary conditions (e.g., tap exactly on button edge)
- It provides strong guarantees that non-buggy inputs are unaffected

**Test Plan**: Observe behavior on UNFIXED code first for valid inputs, then write property-based tests capturing that behavior.

**Test Cases**:
1. **Valid Overlay Tap Preservation**: Generate random taps within button bounds, verify same action triggers before and after fix
2. **Sound Playback Preservation**: Verify all sounds play correctly when loaded, same volume and timing
3. **Layout Preservation at mdpi**: Verify calculateLayout produces identical values at density=1.0
4. **Score Retrieval Preservation**: Verify getHighScores returns same results for small score sets
5. **Back Press Preservation (API < 33)**: Verify pause/finish behavior unchanged on older APIs

### Unit Tests

- Test `handlePauseTouch` with taps inside and outside button X bounds
- Test `handleGameOverTouch` with taps inside and outside button X bounds
- Test `AudioManager.release()` is called from activity lifecycle
- Test `calculateLayout` produces density-scaled values
- Test `pauseBtnRect` dimensions meet 48dp minimum at various densities
- Test `saveScore` prunes to MAX_SCORES entries
- Test `play*()` methods skip playback for unloaded sounds
- Test game state serialization/deserialization round-trip

### Property-Based Tests

- Generate random (tx, ty) coordinates and overlay states → verify only in-bounds taps trigger actions
- Generate random display densities → verify blockSize cap and drag offset scale correctly
- Generate random score lists of varying lengths → verify pruning maintains top-N invariant and sort order
- Generate random sound load sequences → verify no play() call occurs for unloaded sound IDs
- Generate random game states → verify serialization round-trip preserves all fields

### Integration Tests

- Test full game flow: play game → pause → tap outside button → verify no action → tap inside → verify action
- Test full game flow: game over → tap outside "Back to Menu" → verify no navigation → tap inside → verify navigation
- Test activity lifecycle: start game → simulate process death → recreate → verify game state restored
- Test audio lifecycle: start game → play sounds → finish activity → verify no SoundPool leak
- Test release build configuration: verify release variant does not use debug signing config
