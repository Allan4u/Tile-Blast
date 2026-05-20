# Bugfix Requirements Document

## Introduction

This document covers 10 bugs in the TileBlast Android game affecting touch handling accuracy, resource management, density-independent rendering, API compatibility, data storage efficiency, state persistence, and build configuration. These bugs range from user-facing interaction issues (phantom button taps) to systemic concerns (memory leaks, data loss on process death, unbounded storage growth).

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN the game is paused AND the user taps anywhere at the Y-coordinate range of the Resume or Quit buttons (regardless of X position) THEN the system triggers the Resume or Quit action even if the tap is outside the visible button bounds

1.2 WHEN the app process is destroyed or the activity finishes THEN the system never calls `AudioManager.release()`, causing the SoundPool native resources to leak until process death

1.3 WHEN the device has a high or low display density AND the user drags a piece THEN the system uses a hardcoded 40-pixel offset and a hardcoded `46 * 3` pixel cap for blockSize, causing the drag offset and block sizing to feel incorrect on non-baseline densities

1.4 WHEN the user attempts to tap the pause button THEN the system uses a 50×50 pixel touch target that may be smaller than the recommended 48dp minimum on high-density screens

1.5 WHEN the app runs on Android API 33+ AND the user presses the back button THEN the system calls the deprecated `onBackPressed()` method instead of using `OnBackPressedDispatcher`

1.6 WHEN the user completes a game AND `StorageManager.saveScore()` is called THEN the system appends to the JSON array without ever pruning old entries, causing unbounded growth of the stored scores string

1.7 WHEN the system kills the activity while the app is in the background (e.g., low memory) THEN the system loses all in-progress game state (board, hand, score, combo) with no way to restore it

1.8 WHEN a release build is created THEN the system signs it with the debug keystore (`signingConfigs.getByName("debug")`), making it unsuitable for Play Store distribution

1.9 WHEN the first sound is played immediately after AudioManager initialization on a slow device THEN the system calls `soundPool.play()` before the sound has finished loading, resulting in silence

1.10 WHEN the game is over AND the user taps anywhere at the Y-coordinate range of the "Back to Menu" button (regardless of X position) THEN the system triggers the back-to-menu action even if the tap is outside the visible button bounds

### Expected Behavior (Correct)

2.1 WHEN the game is paused AND the user taps THEN the system SHALL only trigger Resume or Quit if the tap is within both the X and Y bounds of the respective button rectangles

2.2 WHEN the activity is destroyed or the app lifecycle ends THEN the system SHALL call `AudioManager.release()` to free SoundPool native resources

2.3 WHEN the user drags a piece THEN the system SHALL use density-independent values (dp) for the drag offset and blockSize cap, converting to pixels using the device's display density

2.4 WHEN the pause button is rendered and its touch target is defined THEN the system SHALL ensure the touch target is at least 48dp × 48dp regardless of screen density

2.5 WHEN the app runs on Android API 33+ AND the user presses the back button THEN the system SHALL handle the back press using `OnBackPressedDispatcher` and `OnBackPressedCallback` instead of the deprecated `onBackPressed()` method

2.6 WHEN `StorageManager.saveScore()` is called THEN the system SHALL prune the stored scores array to retain only the top N entries (e.g., top 100), preventing unbounded growth

2.7 WHEN the system kills the activity while the app is in the background THEN the system SHALL persist the in-progress game state (board, hand, score, combo) via `onSaveInstanceState` or equivalent persistence mechanism, and restore it when the activity is recreated

2.8 WHEN a release build is created THEN the system SHALL use a dedicated release signing configuration (or no hardcoded signing config), not the debug keystore

2.9 WHEN a sound is requested to be played THEN the system SHALL verify that the sound has finished loading before calling `soundPool.play()`, or queue the play request until loading completes

2.10 WHEN the game is over AND the user taps THEN the system SHALL only trigger "Back to Menu" if the tap is within both the X and Y bounds of the button rectangle

### Unchanged Behavior (Regression Prevention)

3.1 WHEN the game is paused AND the user taps within the actual bounds of the Resume button THEN the system SHALL CONTINUE TO resume the game

3.2 WHEN the game is paused AND the user taps within the actual bounds of the Quit button THEN the system SHALL CONTINUE TO quit to the menu

3.3 WHEN sounds are played during normal gameplay (placement, line break, combo, game over) THEN the system SHALL CONTINUE TO play the correct sounds at the configured volume

3.4 WHEN the user drags a piece on a baseline-density (mdpi) device THEN the system SHALL CONTINUE TO position the piece with the same visual offset as before

3.5 WHEN the user taps the pause button within its visible area THEN the system SHALL CONTINUE TO pause the game

3.6 WHEN the user presses back on API < 33 THEN the system SHALL CONTINUE TO pause or finish the game as before

3.7 WHEN `StorageManager.getHighScores()` is called THEN the system SHALL CONTINUE TO return scores sorted in descending order with the correct limit applied

3.8 WHEN the user completes a game normally without the activity being killed THEN the system SHALL CONTINUE TO save the score and show the game over screen

3.9 WHEN a debug build is created THEN the system SHALL CONTINUE TO use the debug keystore for signing

3.10 WHEN the game is over AND the user taps within the actual bounds of the "Back to Menu" button THEN the system SHALL CONTINUE TO navigate back to the menu

3.11 WHEN the grid, HUD, hand pieces, and combo overlays are drawn THEN the system SHALL CONTINUE TO render them with the same visual appearance and layout logic

---

## Bug Condition Derivations

### Bug 1 & 10: Touch area too wide (Pause and GameOver)

```pascal
FUNCTION isBugCondition_TouchArea(X)
  INPUT: X of type TouchEvent {tx: float, ty: float, overlayState: enum}
  OUTPUT: boolean

  // Bug triggers when tap is at correct Y but outside button X bounds
  RETURN X.ty IS WITHIN button_Y_range AND X.tx IS NOT WITHIN button_X_range
END FUNCTION
```

```pascal
// Property: Fix Checking - Touch Area
FOR ALL X WHERE isBugCondition_TouchArea(X) DO
  result ← handleTouch'(X)
  ASSERT result = NO_ACTION
END FOR
```

```pascal
// Property: Preservation Checking - Touch Area
FOR ALL X WHERE NOT isBugCondition_TouchArea(X) DO
  ASSERT handleTouch(X) = handleTouch'(X)
END FOR
```

### Bug 2: SoundPool Leak

```pascal
FUNCTION isBugCondition_SoundPoolLeak(X)
  INPUT: X of type LifecycleEvent {event: enum}
  OUTPUT: boolean

  RETURN X.event = ACTIVITY_DESTROYED
END FUNCTION
```

```pascal
// Property: Fix Checking - SoundPool Release
FOR ALL X WHERE isBugCondition_SoundPoolLeak(X) DO
  result ← onDestroy'(X)
  ASSERT AudioManager.soundPool = NULL
END FOR
```

### Bug 3: Drag offset not density-aware

```pascal
FUNCTION isBugCondition_DensityOffset(X)
  INPUT: X of type DeviceConfig {density: float}
  OUTPUT: boolean

  // Bug triggers on any non-1.0 density device
  RETURN X.density ≠ 1.0
END FUNCTION
```

```pascal
// Property: Fix Checking - Density Offset
FOR ALL X WHERE isBugCondition_DensityOffset(X) DO
  offset' ← computeOffset'(X)
  ASSERT offset' = 40dp * X.density  // offset scales with density
END FOR
```

### Bug 6: Unbounded score storage

```pascal
FUNCTION isBugCondition_UnboundedScores(X)
  INPUT: X of type ScoreState {currentCount: int, maxAllowed: int}
  OUTPUT: boolean

  RETURN X.currentCount >= X.maxAllowed
END FUNCTION
```

```pascal
// Property: Fix Checking - Score Pruning
FOR ALL X WHERE isBugCondition_UnboundedScores(X) DO
  saveScore'(X)
  ASSERT storedScores.length <= X.maxAllowed
END FOR
```

### Bug 9: First sound silent

```pascal
FUNCTION isBugCondition_SoundNotLoaded(X)
  INPUT: X of type SoundPlayRequest {soundId: int, loaded: boolean}
  OUTPUT: boolean

  RETURN X.loaded = FALSE
END FUNCTION
```

```pascal
// Property: Fix Checking - Sound Load Check
FOR ALL X WHERE isBugCondition_SoundNotLoaded(X) DO
  result ← play'(X)
  ASSERT result = QUEUED OR result = SKIPPED  // never plays unloaded sound
END FOR
```

```pascal
// Property: Preservation Checking (all bugs)
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT F(X) = F'(X)
END FOR
```
