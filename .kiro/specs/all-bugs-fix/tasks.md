# Implementation Plan

## Overview

This task list implements fixes for 10 bugs in the TileBlast Android game using the bug condition methodology. The workflow follows: (1) write exploration tests to confirm bugs exist, (2) write preservation tests to capture existing correct behavior, (3) implement fixes, (4) verify all tests pass.

## Tasks

- [ ] 1. Write bug condition exploration tests
  - **Property 1: Bug Condition** - TileBlast Multi-Bug Exploration
  - **CRITICAL**: These tests MUST FAIL on unfixed code - failure confirms the bugs exist
  - **DO NOT attempt to fix the tests or the code when they fail**
  - **NOTE**: These tests encode the expected behavior - they will validate the fixes when they pass after implementation
  - **GOAL**: Surface counterexamples that demonstrate each bug exists
  - **Scoped PBT Approach**: Scope properties to concrete failing cases for each bug condition
  - Test 1a: Pause overlay touch - simulate tap at (10, cy+140) where button X range is [w/2-110, w/2+110]. Assert NO action is triggered (from Bug Condition: `isBugCondition_TouchArea` where tx NOT WITHIN button_X_range)
  - Test 1b: GameOver overlay touch - simulate tap at (10, cy+200) where button X range is [w/2-110, w/2+110]. Assert NO action is triggered
  - Test 1c: SoundPool leak - create AudioManager, simulate onDestroy lifecycle, assert `soundPool` is null after destruction (from Bug Condition: `isBugCondition_SoundPoolLeak` where event = ACTIVITY_DESTROYED)
  - Test 1d: Density offset - set density=3.0, call calculateLayout, assert blockSize cap = `46 * 3 * density` and drag offset = `40 * density` (from Bug Condition: `isBugCondition_DensityOffset` where density ≠ 1.0)
  - Test 1e: Pause button size - set density=3.0, assert pauseBtnRect width >= `48 * density` pixels
  - Test 1f: Score pruning - add 150 scores via saveScore, assert stored count ≤ 100 (from Bug Condition: `isBugCondition_UnboundedScores` where currentCount >= maxAllowed)
  - Test 1g: Sound load guard - call playPlace() before onLoadComplete fires, assert no `soundPool.play()` call occurs (from Bug Condition: `isBugCondition_SoundNotLoaded` where loaded = FALSE)
  - Test 1h: State persistence - simulate process death with game in progress, recreate activity, assert game state (board, hand, score, combo) is restored
  - Test 1i: Release build signing - verify release build config does NOT reference debug signing config
  - Test 1j: OnBackPressedDispatcher - on API 33+, verify back press is handled via OnBackPressedDispatcher callback, not deprecated onBackPressed()
  - Run all tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests FAIL (this is correct - it proves the bugs exist)
  - Document counterexamples found to understand root causes
  - Mark task complete when tests are written, run, and failures are documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10_

- [ ] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - TileBlast Existing Behavior Preservation
  - **IMPORTANT**: Follow observation-first methodology
  - **Step 1 - Observe**: Run UNFIXED code with non-buggy inputs and record actual outputs
  - **Step 2 - Write**: Write property-based tests asserting those observed outputs
  - **Step 3 - Verify**: Confirm tests PASS on UNFIXED code
  - Observe: Tap within pause overlay Resume button bounds → game resumes (Preservation Req 3.1)
  - Observe: Tap within pause overlay Quit button bounds → navigates to menu (Preservation Req 3.2)
  - Observe: Tap within game-over "Back to Menu" button bounds → navigates to menu (Preservation Req 3.10)
  - Observe: Sound playback after all sounds loaded → correct sounds play at configured volume (Preservation Req 3.3)
  - Observe: calculateLayout at density=1.0 → blockSize cap = 138px, drag offset = 40px (Preservation Req 3.4)
  - Observe: Tap within pause button visible area → game pauses (Preservation Req 3.5)
  - Observe: Back press on API < 33 → pause or finish behavior (Preservation Req 3.6)
  - Observe: getHighScores() with < 100 scores → returns sorted descending with correct limit (Preservation Req 3.7)
  - Observe: Normal game completion without process death → score saved, game over screen shown (Preservation Req 3.8)
  - Observe: Debug build → uses debug keystore (Preservation Req 3.9)
  - Observe: Grid, HUD, hand pieces, combo overlays render correctly (Preservation Req 3.11)
  - Write property-based tests: for all touch events within button bounds, verify same action triggers
  - Write property-based tests: for all densities = 1.0, verify layout values unchanged
  - Write property-based tests: for all score sets below threshold, verify retrieval unchanged
  - Write property-based tests: for all loaded sounds, verify playback unchanged
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10, 3.11_

- [ ] 3. Fix touch area bounds for pause overlay (Bug 1)

  - [ ] 3.1 Add X-bound check to handlePauseTouch
    - Compute button X bounds: `float bx1 = w/2f - 110, bx2 = w/2f + 110`
    - Add `tx > bx1 && tx < bx2` condition to Resume button Y-range check
    - Add `tx > bx1 && tx < bx2` condition to Quit button Y-range check
    - _Bug_Condition: isBugCondition_TouchArea(X) where X.tx NOT WITHIN button_X_range AND overlay = PAUSED_
    - _Expected_Behavior: handleTouch'(X) = NO_ACTION for out-of-X-bounds taps_
    - _Preservation: Taps within both X and Y bounds continue to trigger Resume/Quit_
    - _Requirements: 2.1, 3.1, 3.2_

- [ ] 4. Fix touch area bounds for game-over overlay (Bug 10)

  - [ ] 4.1 Add X-bound check to handleGameOverTouch
    - Compute button X bounds: `float bx1 = w/2f - 110, bx2 = w/2f + 110`
    - Add `tx > bx1 && tx < bx2` condition to "Back to Menu" button Y-range check
    - _Bug_Condition: isBugCondition_TouchArea(X) where X.tx NOT WITHIN button_X_range AND overlay = GAME_OVER_
    - _Expected_Behavior: handleTouch'(X) = NO_ACTION for out-of-X-bounds taps_
    - _Preservation: Taps within both X and Y bounds continue to trigger Back to Menu_
    - _Requirements: 2.10, 3.10_

- [ ] 5. Fix SoundPool resource leak (Bug 2)

  - [ ] 5.1 Call AudioManager.release() in GameActivity.onDestroy
    - Override `onDestroy()` in GameActivity
    - Call `AudioManager.getInstance(this).release()` before `super.onDestroy()`
    - Ensure `soundPool` is set to null after release
    - _Bug_Condition: isBugCondition_SoundPoolLeak(X) where X.event = ACTIVITY_DESTROYED AND soundPool ≠ NULL_
    - _Expected_Behavior: After onDestroy, AudioManager.soundPool = NULL_
    - _Preservation: Sound playback during active gameplay unchanged_
    - _Requirements: 2.2, 3.3_

- [ ] 6. Fix hardcoded pixel values for density independence (Bug 3)

  - [ ] 6.1 Convert pixel constants to density-scaled values
    - Store `density = getResources().getDisplayMetrics().density` as a field in GameView
    - Replace hardcoded `40` drag offset with `(int)(40 * density)` in `drawDraggingPiece` and `updateHoverPreview`
    - Replace `46 * 3` blockSize cap with `(int)(46 * 3 * density)` in `calculateLayout`
    - Replace `gridPadding = 40` with `(int)(40 * density)`
    - _Bug_Condition: isBugCondition_DensityOffset(X) where X.density ≠ 1.0_
    - _Expected_Behavior: offset' = 40dp * density, blockSizeCap = 46*3*density_
    - _Preservation: At density=1.0, pixel values remain identical (40*1.0=40, 138*1.0=138)_
    - _Requirements: 2.3, 3.4_

- [ ] 7. Fix pause button touch target size (Bug 4)

  - [ ] 7.1 Ensure pause button meets 48dp minimum
    - Compute minimum size: `int minPx = (int)(48 * density)`
    - Set `pauseBtnRect` dimensions to `Math.max(50, minPx)` pixels in each dimension
    - _Bug_Condition: pauseBtnRect.width() < 48 * density on high-density screens_
    - _Expected_Behavior: pauseBtnRect width and height >= 48 * density for all densities_
    - _Preservation: Pause button remains tappable within its visible area_
    - _Requirements: 2.4, 3.5_

- [ ] 8. Fix deprecated onBackPressed for API 33+ (Bug 5)

  - [ ] 8.1 Replace onBackPressed with OnBackPressedDispatcher
    - Remove `onBackPressed()` override from GameActivity
    - In `onCreate`, register `OnBackPressedCallback` via `getOnBackPressedDispatcher().addCallback(this, callback)`
    - Callback logic: if game not over and not paused → pause; if paused → finish; if game over → finish
    - _Bug_Condition: isBugCondition where apiLevel >= 33 and onBackPressed is called_
    - _Expected_Behavior: Back press handled via OnBackPressedDispatcher on API 33+_
    - _Preservation: Back-press behavior on API < 33 remains unchanged (pause or finish)_
    - _Requirements: 2.5, 3.6_

- [ ] 9. Fix unbounded score storage growth (Bug 6)

  - [ ] 9.1 Add score pruning to saveScore
    - After `arr.put(obj)`, sort the array by score descending
    - If `arr.length() > MAX_SCORES` (100), truncate to MAX_SCORES entries
    - Write the pruned array back to SharedPreferences
    - Add `private static final int MAX_SCORES = 100` constant to StorageManager
    - _Bug_Condition: isBugCondition_UnboundedScores(X) where currentCount >= maxAllowed_
    - _Expected_Behavior: storedScores.length <= MAX_SCORES after saveScore_
    - _Preservation: getHighScores() returns same sorted results for score sets below threshold_
    - _Requirements: 2.6, 3.7_

- [ ] 10. Fix state lost on process death (Bug 7)

  - [ ] 10.1 Implement state serialization in GameView
    - Add `saveState(Bundle outState)` method to GameView
    - Serialize board array, hand pieces, score, combo count to the Bundle
    - Add `restoreState(Bundle savedInstanceState)` method to GameView
    - Deserialize and restore board, hand, score, combo from Bundle
    - _Requirements: 2.7_

  - [ ] 10.2 Hook state persistence into GameActivity lifecycle
    - Override `onSaveInstanceState(Bundle outState)` in GameActivity
    - Call `gameView.saveState(outState)` then `super.onSaveInstanceState(outState)`
    - In `onCreate`, if `savedInstanceState != null`, call `gameView.restoreState(savedInstanceState)`
    - _Bug_Condition: process death with gameState.inProgress = TRUE_
    - _Expected_Behavior: Game state restored on activity recreation after process death_
    - _Preservation: Normal game completion without process death unchanged_
    - _Requirements: 2.7, 3.8_

- [ ] 11. Fix release build signed with debug keystore (Bug 8)

  - [ ] 11.1 Remove debug signing from release build type
    - In `app/build.gradle.kts`, remove `signingConfig = signingConfigs.getByName("debug")` from the `release` block
    - Add comment: `// TODO: Configure release signing for Play Store distribution`
    - _Bug_Condition: buildType = "release" AND signingConfig = debug_
    - _Expected_Behavior: Release build does NOT reference debug signing config_
    - _Preservation: Debug builds continue to use debug keystore_
    - _Requirements: 2.8, 3.9_

- [ ] 12. Fix first sound silent due to play before load (Bug 9)

  - [ ] 12.1 Add per-sound load tracking to AudioManager
    - Replace `private boolean loaded` with `private Set<Integer> loadedSoundIds = new HashSet<>()`
    - In `onLoadComplete` listener, add: `loadedSoundIds.add(sampleId)`
    - Remove the old `loaded = true` assignment
    - _Requirements: 2.9_

  - [ ] 12.2 Guard play methods with load-completion check
    - In each `play*()` method (playPlace, playBreak, playCombo, playGameOver, playJuiciness), add guard: `if (soundPool != null && loadedSoundIds.contains(soundId))`
    - Skip `soundPool.play()` call if sound is not yet loaded
    - _Bug_Condition: isBugCondition_SoundNotLoaded(X) where X.loaded = FALSE AND playRequested = TRUE_
    - _Expected_Behavior: play'(X) = SKIPPED (never plays unloaded sound)_
    - _Preservation: Loaded sounds continue to play at configured volume_
    - _Requirements: 2.9, 3.3_

- [ ] 13. Verify bug condition exploration tests now pass

  - [ ] 13.1 Re-run exploration tests for touch area fixes (Bugs 1, 10)
    - **Property 1: Expected Behavior** - Overlay Touch Bounds Fixed
    - **IMPORTANT**: Re-run the SAME tests from task 1 (tests 1a, 1b) - do NOT write new tests
    - Run pause overlay and game-over overlay X-bounds tests
    - **EXPECTED OUTCOME**: Tests PASS (confirms touch area bugs are fixed)
    - _Requirements: 2.1, 2.10_

  - [ ] 13.2 Re-run exploration tests for resource and lifecycle fixes (Bugs 2, 5, 7)
    - **Property 1: Expected Behavior** - Resource Lifecycle Fixed
    - **IMPORTANT**: Re-run the SAME tests from task 1 (tests 1c, 1h, 1j) - do NOT write new tests
    - Run SoundPool leak, state persistence, and OnBackPressedDispatcher tests
    - **EXPECTED OUTCOME**: Tests PASS (confirms lifecycle bugs are fixed)
    - _Requirements: 2.2, 2.5, 2.7_

  - [ ] 13.3 Re-run exploration tests for density and sizing fixes (Bugs 3, 4)
    - **Property 1: Expected Behavior** - Density Independence Fixed
    - **IMPORTANT**: Re-run the SAME tests from task 1 (tests 1d, 1e) - do NOT write new tests
    - Run density offset and pause button size tests
    - **EXPECTED OUTCOME**: Tests PASS (confirms density bugs are fixed)
    - _Requirements: 2.3, 2.4_

  - [ ] 13.4 Re-run exploration tests for storage and audio fixes (Bugs 6, 9)
    - **Property 1: Expected Behavior** - Storage and Audio Fixed
    - **IMPORTANT**: Re-run the SAME tests from task 1 (tests 1f, 1g) - do NOT write new tests
    - Run score pruning and sound load guard tests
    - **EXPECTED OUTCOME**: Tests PASS (confirms storage and audio bugs are fixed)
    - _Requirements: 2.6, 2.9_

  - [ ] 13.5 Re-run exploration test for build config fix (Bug 8)
    - **Property 1: Expected Behavior** - Release Build Signing Fixed
    - **IMPORTANT**: Re-run the SAME test from task 1 (test 1i) - do NOT write a new test
    - Run release build signing config test
    - **EXPECTED OUTCOME**: Test PASSES (confirms release no longer uses debug keystore)
    - _Requirements: 2.8_

- [ ] 14. Verify preservation tests still pass

  - [ ] 14.1 Re-run all preservation property tests
    - **Property 2: Preservation** - All Existing Behavior Preserved
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run all preservation tests: valid overlay taps, sound playback, layout at mdpi, score retrieval, back press on API < 33, debug build signing
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions introduced)
    - Confirm all preservation tests still pass after all fixes applied

- [ ] 15. Checkpoint - Ensure all tests pass
  - Run the full test suite (exploration + preservation tests)
  - Verify all 10 bug fixes are working correctly
  - Verify no regressions in existing behavior
  - Ensure all tests pass, ask the user if questions arise

## Task Dependency Graph

```json
{
  "waves": [
    ["1", "2"],
    ["3", "4", "5", "6", "7", "8", "9", "10", "11", "12"],
    ["13"],
    ["14"],
    ["15"]
  ]
}
```

## Notes

- Exploration tests (task 1) must be written and run BEFORE any implementation begins. They are expected to FAIL on unfixed code.
- Preservation tests (task 2) must be written and verified PASSING on unfixed code before implementation.
- Implementation tasks (3-12) can be done in any order after tasks 1 and 2 are complete.
- Verification tasks (13-14) re-run the same tests from tasks 1 and 2 — no new tests are written.
- Bug 8 (release signing) is a build config change that can be verified by inspecting the gradle file.
- Bug 7 (state persistence) requires both GameView serialization methods and GameActivity lifecycle hooks.
