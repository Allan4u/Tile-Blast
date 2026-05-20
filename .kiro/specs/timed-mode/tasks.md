# Implementation Plan: Timed Mode

## Overview

Convert the feature design into a series of prompts for a code-generation LLM that will implement each step with incremental progress. Make sure that each prompt builds on the previous prompts, and ends with wiring things together. There should be no hanging or orphaned code that isn't integrated into a previous step. Focus ONLY on tasks that involve writing, modifying, or testing code.

This plan implements Timed Mode in Java for Android Canvas. Build order: (1) the pure-JVM `TimedModeController` so its math is property-tested in isolation, (2) `ScoreManager` overloads for the multiplier, (3) menu plumbing in `MainActivity` and the `activity_main.xml` layout, (4) `GameActivity` intent-extra routing and lifecycle hooks, (5) `GameView` HUD timer drawing and game-over integration, and (6) high-score screen sections plus end-to-end wiring.

## Tasks

- [ ] 1. Implement TimedModeController core
  - [ ] 1.1 Create `TimedModeController` class with constructor, fields, and pure queries
    - Create `app/src/main/java/com/allan/tileblast/game/TimedModeController.java`
    - Declare constants `TIME_BONUS_PER_LINE_BREAK_SEC = 1.5f`, `END_BONUS_PER_SECOND = 5`, `TIME_BONUS_FLASH_DURATION_MS = 800L`
    - Add fields: `modeName`, `initialDurationSec`, `accumulatedBonusSec`, `elapsedSec`, `paused`, `expired`, `lastTickMs`, `timeBonusFlashEndMs`
    - Implement constructor `TimedModeController(String modeName, float initialDurationSec)` that initializes all fields and sets `paused = true`, `expired = false`
    - Implement `getModeName()`, `getInitialDurationSec()`, `isPaused()`, `isExpired()`, `isActive()` queries
    - Implement `remainingTime()` as `Math.max(0, initialDurationSec + accumulatedBonusSec - elapsedSec)`
    - Implement `elapsedTime()` returning `elapsedSec`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 4.3_
  
  - [ ] 1.2 Implement multiplier band logic
    - Add private band constants `BAND_15`, `BAND_30`, `BAND_45`, `BAND_60`
    - Implement `scoreMultiplier()` as a piecewise function: 1.0 in `[0,15)`, 1.5 in `[15,30)`, 2.0 in `[30,45)`, 2.5 in `[45,60)`, then 3.0 if `initialDurationSec >= 90` else clamp at 2.5
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_
  
  - [ ]* 1.3 Write property test for multiplier band table
    - **Property 1: Multiplier band table holds across the elapsed range**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
    - Use jqwik `@Property(tries = 200)` with `@ForAll` `elapsedSeconds` in `[0, 200]` and `timedModeKey` in `{"timed60", "timed90"}`
    - Construct controller, force `elapsedSec` via a tick that advances to the desired value, assert `scoreMultiplier()` equals the band table value
  
  - [ ] 1.4 Implement tick, pause, resume, and onLineBreak
    - Implement `tick(long nowMs)`: if `paused || expired` no-op; if `nowMs < lastTickMs` set `lastTickMs = nowMs` (no negative deltas); else advance `elapsedSec += (nowMs - lastTickMs) / 1000f`, then `lastTickMs = nowMs`; if `remainingTime() <= 0` set `expired = true` and clamp `elapsedSec = initialDurationSec + accumulatedBonusSec`
    - Implement `pause()`: if not paused, set `paused = true`
    - Implement `resume(long nowMs)`: set `paused = false; lastTickMs = nowMs`
    - Implement `onLineBreak()`: `accumulatedBonusSec += TIME_BONUS_PER_LINE_BREAK_SEC; timeBonusFlashEndMs = System.currentTimeMillis() + TIME_BONUS_FLASH_DURATION_MS`
    - Implement `isTimeBonusFlashActive(long nowMs)` returning `nowMs < timeBonusFlashEndMs`
    - Implement `endBonus()` returning `END_BONUS_PER_SECOND * (int) Math.floor(remainingTime())` if `remainingTime() > 0` else `0`
    - _Requirements: 4.1, 4.2, 4.3, 5.1, 5.3, 6.1, 6.2, 9.1, 9.2, 9.3_
  
  - [ ]* 1.5 Write property test for line-break time bonus accumulation
    - **Property 5: Line-break time bonus accumulates exactly 1.5s per call**
    - **Validates: Requirements 4.1, 4.3**
    - Generate `N` in `[0, 200]`, call `onLineBreak()` N times, assert `remainingTime()` increased by exactly `1.5 * N` and is never clamped to an upper bound
  
  - [ ]* 1.6 Write property test for time-bonus flash window
    - **Property 6: Time-bonus flash window**
    - **Validates: Requirements 4.2**
    - Call `onLineBreak()` at `t0`, sweep observation times `t` in `[t0 - 100, t0 + 1000]`, assert `isTimeBonusFlashActive(t)` is true iff `t in [t0, t0 + 800)`; verify a second `onLineBreak()` resets the window
  
  - [ ]* 1.7 Write property test for timer expiry idempotency
    - **Property 7: Timer expiry is idempotent**
    - **Validates: Requirements 5.1, 5.3**
    - Drive ticks past `initialDurationSec + accumulatedBonusSec`, assert first expiring tick sets `isExpired() == true` and `remainingTime() == 0`, and any subsequent tick leaves `remainingTime()` and `elapsedSec` unchanged
  
  - [ ]* 1.8 Write property test for end-of-round bonus formula
    - **Property 9: End-of-round time bonus formula**
    - **Validates: Requirements 6.1, 6.2**
    - Generate `remaining` in `[-5, 200]` and reverse-engineer controller state to produce that remaining; assert `endBonus()` equals `5 * (int) Math.floor(remaining)` for positive remaining and `0` for non-positive
  
  - [ ]* 1.9 Write property test for pause freezes time
    - **Property 10: Pause freezes time advancement**
    - **Validates: Requirements 9.1, 9.2**
    - Snapshot `(remainingTime, elapsedTime, scoreMultiplier)` at `pause()`, then call arbitrary `tick(nowMs)` sequences, assert all three queries return the snapshot values
  
  - [ ]* 1.10 Write property test for pause/resume transparency
    - **Property 11: Pause/resume is observationally transparent**
    - **Validates: Requirements 9.3**
    - Compare `elapsedSec` after `tick(t1) -> pause() -> resume(t1+dt) -> tick(t1+dt+x)` against `tick(t1) -> tick(t1+x)`, assert equal within float tolerance

- [ ] 2. Implement TimedModeController persistence
  - [ ] 2.1 Add saveState and restoreState methods
    - Add `saveState(Bundle out)` writing: `timer_present=true`, `timer_mode`, `timer_initial`, `timer_bonus_sec`, `timer_elapsed_sec`, `timer_expired`, `timer_paused`
    - Add `restoreState(Bundle in)` reading those keys with defaults (`accumulatedBonusSec=0`, `elapsedSec=0`, `expired=false`); always force `paused = true` regardless of persisted value; reset `lastTickMs = 0` and `timeBonusFlashEndMs = 0`
    - _Requirements: 10.1, 10.2_
  
  - [ ]* 2.2 Write property test for save/restore round trip
    - **Property 13: Controller save / restore round trip with paused-on-restore invariant**
    - **Validates: Requirements 10.1, 10.2, 10.3**
    - Generate random action sequences (TICK, PAUSE, RESUME, LINE_BREAK), serialize via `saveState(bundle)`, construct fresh controller via `restoreState(bundle)`, assert all data fields match and `isPaused() == true`

- [ ] 3. Extend ScoreManager with multiplier support
  - [ ] 3.1 Add multiplier overloads to ScoreManager
    - Edit `app/src/main/java/com/allan/tileblast/game/ScoreManager.java`
    - Add overload `addPlacement(int blockCount, float multiplier)`: compute `delta = Math.round(blockCount * multiplier)`, add to `score`, return `delta`
    - Refactor existing `addPlacement(int blockCount)` to delegate `return addPlacement(blockCount, 1.0f)` for binary compatibility
    - Add overload `processLineBreak(int linesBroken, float multiplier)`: same combo / turnsSinceBreak logic as today; compute `rawBonus = Math.round(linesBroken * boardSize * 10f * combo)`, then `delta = Math.round(rawBonus * multiplier)`, add `delta` to `score`
    - Refactor existing `processLineBreak(int linesBroken)` to delegate `return processLineBreak(linesBroken, 1.0f)`
    - Add `addBonus(int bonus)` that does `if (bonus > 0) score += bonus`
    - _Requirements: 3.6, 6.1_
  
  - [ ]* 3.2 Write property test for score multiplier and rounding
    - **Property 2: Score increments multiply, round, and add**
    - **Validates: Requirements 3.6**
    - Generate `blockCount` in `[1,25]`, `linesBroken` in `[0,16]`, `boardSize` in `{8,10}`, `combo` seed in `[0,20]`, `multiplier` in `[1.0, 3.0]`
    - Assert `addPlacement(blockCount, multiplier)` raises score by exactly `Math.round(blockCount * multiplier)`
    - Assert `processLineBreak(linesBroken>0, multiplier)` raises score by exactly `Math.round(Math.round(linesBroken * boardSize * 10f * (combo + linesBroken)) * multiplier)`
    - Assert `processLineBreak(0, multiplier)` leaves score unchanged

- [ ] 4. Add Timed Mode buttons to MainActivity
  - [ ] 4.1 Create timed button drawable
    - Create `app/src/main/res/drawable/btn_timed.xml` mirroring the gradient style of `btn_classic.xml` and `btn_chaos.xml` with a teal accent (`#FF00BFA5`)
    - _Requirements: 1.4_
  
  - [ ] 4.2 Add Timed 60s and Timed 90s buttons to activity_main.xml
    - Edit `app/src/main/res/layout/activity_main.xml`
    - Add `LinearLayout` with id `btnTimed60` containing `TextView` ids `btnTimed60Text` (text "Timed 60s") and `btnTimed60Desc` (text "8x8 Grid - 60 Seconds"), background `@drawable/btn_timed`
    - Add `LinearLayout` with id `btnTimed90` containing `TextView` ids `btnTimed90Text` (text "Timed 90s") and `btnTimed90Desc` (text "8x8 Grid - 90 Seconds"), background `@drawable/btn_timed`
    - Place both rows below `btnChaos` and above `btnHighScores`
    - Match `width=280dp`, `padding=16dp`, `layout_marginBottom=16dp`, `textSize=20sp` / `12sp` from existing buttons
    - _Requirements: 1.1, 1.4_
  
  - [ ] 4.3 Wire MainActivity click handlers and add durationSec parameter
    - Edit `MainActivity.java`
    - Change `startGame(int boardSize, int handSize, String mode)` to `startGame(int boardSize, int handSize, String mode, float durationSec)` and set `i.putExtra("durationSec", durationSec)`
    - Update existing Classic and Chaos calls to pass `0f` for `durationSec`
    - Add `findViewById(R.id.btnTimed60).setOnClickListener(v -> startGame(8, 3, "timed60", 60f))`
    - Add `findViewById(R.id.btnTimed90).setOnClickListener(v -> startGame(8, 3, "timed90", 90f))`
    - Apply `silkscreen_bold` typeface to `btnTimed60Text` and `btnTimed90Text`; apply `silkscreen` to the two desc TextViews
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [ ] 5. Route durationSec extra through GameActivity
  - [ ] 5.1 Read durationSec extra and forward to GameView.setup
    - Edit `GameActivity.java`
    - In `onCreate`, read `float duration = getIntent().getFloatExtra("durationSec", 0f)` after the existing `mode` read
    - Update the call to `gameView.setup(boardSize, handSize, mode, this)` to use the new 5-argument signature `gameView.setup(boardSize, handSize, mode, duration, this)` (signature added in task 6.1)
    - _Requirements: 1.2, 1.3, 10.1_
  
  - [ ] 5.2 Add onPause hook to auto-pause Timed Mode rounds
    - Add `@Override protected void onPause()` to `GameActivity` that calls `super.onPause()`, then if `gameView != null && !gameView.isGameOver()` calls `gameView.setPaused(true)`
    - _Requirements: 9.4_

- [ ] 6. Integrate timer into GameView
  - [ ] 6.1 Extend GameView.setup signature and construct TimedModeController
    - Edit `app/src/main/java/com/allan/tileblast/game/GameView.java`
    - Add fields: `private TimedModeController timer`, `private long inputDeadlineMs = 0L`, `private int timeRemainingBonusForOverlay = 0`
    - Change `setup(int boardSize, int handSize, String modeName, GameCallback cb)` to `setup(int boardSize, int handSize, String modeName, float durationSec, GameCallback cb)`
    - Inside `setup`, after existing init: if `durationSec > 0f && ("timed60".equals(modeName) || "timed90".equals(modeName))`, construct `this.timer = new TimedModeController(modeName, durationSec)` and call `timer.resume(System.currentTimeMillis())`; else `this.timer = null`
    - Ensure `bestScore = storageManager.getBestScore(modeName)` (already keyed by mode string) is unchanged so `timed60`/`timed90` best is shown
    - _Requirements: 1.2, 1.3, 7.5_
  
  - [ ] 6.2 Tick timer in onDraw and self-reschedule frames
    - In `onDraw(Canvas canvas)`, after the existing null-board guard: if `timer != null && !paused && !gameOver`, call `timer.tick(System.currentTimeMillis())` and if `timer.isExpired()` call `transitionToGameOver()` (added in task 6.5)
    - At the end of `onDraw`, ensure `if (timer != null && !paused && !gameOver) invalidate();` is called so the countdown updates at frame rate
    - _Requirements: 2.1, 5.1_
  
  - [ ] 6.3 Hook pause / resume into TimedModeController
    - In `setPaused(boolean p)`, after the existing `paused = p` assignment: if `timer != null && p` call `timer.pause()`; if `timer != null && !p` call `timer.resume(System.currentTimeMillis())`
    - _Requirements: 9.1, 9.2, 9.3, 9.4_
  
  - [ ] 6.4 Apply multiplier in attemptPlacement and call onLineBreak
    - In `attemptPlacement` (or wherever placement scoring happens), compute `float multiplier = (timer != null) ? timer.scoreMultiplier() : 1.0f` once at entry
    - Replace `scoreManager.addPlacement(blockCount)` with `scoreManager.addPlacement(blockCount, multiplier)`
    - Replace `scoreManager.processLineBreak(linesBroken)` with `scoreManager.processLineBreak(linesBroken, multiplier)`
    - After processLineBreak, if `linesBroken > 0 && timer != null` call `timer.onLineBreak()`
    - _Requirements: 3.6, 4.1, 4.3_
  
  - [ ] 6.5 Add transitionToGameOver helper and refactor existing game-over path
    - Add `private void transitionToGameOver()` that early-returns if `gameOver`; sets `gameOver = true`; sets `inputDeadlineMs = System.currentTimeMillis() + 100`; computes `int bonus = (timer != null) ? timer.endBonus() : 0`; if `bonus > 0` calls `scoreManager.addBonus(bonus)` and stores `timeRemainingBonusForOverlay = bonus`; calls `audioManager.playGameover()`; calls `storageManager.saveScore(scoreManager.getScore(), modeName)`; invokes `callback.onGameOver(scoreManager.getScore())`
    - Refactor the existing "no piece can be placed" branch in `attemptPlacement` to call `transitionToGameOver()` instead of inlining save/callback
    - In `onTouchEvent`, when `gameOver` is true, ignore input if `System.currentTimeMillis() >= inputDeadlineMs` (where `inputDeadlineMs > 0`)
    - _Requirements: 5.1, 5.2, 5.3, 6.1, 6.2, 7.1, 7.2_
  
  - [ ]* 6.6 Write test for post-game-over input grace window
    - **Property 8: Post-game-over input grace window**
    - **Validates: Requirements 5.3**
    - Force `transitionToGameOver()` at known `gameOverMs`, simulate touch events at offsets in `[0, 250]` ms, assert placements are processed iff `inputMs - gameOverMs <= 100`

- [ ] 7. Render Timed Mode HUD
  - [ ] 7.1 Draw countdown timer with color bands and pulse
    - Add `private void drawCountdown(Canvas canvas, int w, int yTop)` that reads `timer.remainingTime()`, draws integer seconds at 56 px text size, color: white if `> 30`, yellow `#FFFFD700` if `> 10`, red `#FFFF3333` otherwise; if `remaining <= 5` apply scale `1.0 + 0.2 * (0.5 + 0.5 * sin(2 * pi * 2 * (now / 1000f)))` via `Canvas.scale(s, s, cx, cy)` around the text bounding box
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_
  
  - [ ] 7.2 Draw multiplier label and time-bonus flash
    - Add `private void drawMultiplier(Canvas canvas, int w, int y)` that renders `String.format("x%.1f", timer.scoreMultiplier())` in gold `#FFFFD700` at 20 px when `timer.scoreMultiplier() > 1.0f`; do not draw at 1.0
    - In `drawHUD(Canvas, int)` (or equivalent), if `timer != null` call `drawCountdown` first and shift score / best down by 80 px; after the existing combo line, call `drawMultiplier` when applicable
    - If `timer != null && timer.isTimeBonusFlashActive(System.currentTimeMillis())`, draw "+1.5s" just below the countdown
    - _Requirements: 2.7, 3.7, 4.2_
  
  - [ ] 7.3 Update game-over overlay for Timed Mode bonus breakdown
    - When drawing the existing game-over overlay: if `timer != null && timeRemainingBonusForOverlay > 0`, render three labeled lines instead of just final score: `"Score: " + (finalScore - timeRemainingBonusForOverlay)`, `"Time Bonus: +" + timeRemainingBonusForOverlay`, `"Final: " + finalScore`
    - _Requirements: 6.3_
  
  - [ ]* 7.4 Write property test for countdown color band and pulse scale
    - **Property 4: Countdown color band and pulse scale**
    - **Validates: Requirements 2.1, 2.3, 2.4, 2.5, 2.6**
    - Extract the color and pulse-scale computation into a pure helper method `static int countdownColor(float remaining)` and `static float countdownPulseScale(float remaining, long nowMs)` so the property test can drive them without a `Canvas`
    - Assert displayed integer equals `(int) Math.floor(remaining)`; color matches band table; pulse scale is exactly `1.0` when `remaining > 5` and is in `[1.0, 1.2]` when `remaining` is in `(0, 5]`
  
  - [ ]* 7.5 Write property test for multiplier label formatting
    - **Property 3: Multiplier label formatting**
    - **Validates: Requirements 3.7**
    - Extract a pure helper `static String multiplierLabel(float multiplier)` returning `String.format("x%.1f", multiplier)` if `> 1.0` else `null`
    - Generate `multiplier` uniformly in `[1.0, 3.0]` and assert the label matches the spec

- [ ] 8. Persist GameView timer state across configuration changes
  - [ ] 8.1 Save and restore TimedModeController in GameView
    - In `GameView.saveState(Bundle out)`: if `timer != null` call `timer.saveState(out)`; else `out.putBoolean("timer_present", false)`
    - In `GameView.restoreState(Bundle in)`: if `in.getBoolean("timer_present", false)`, read `timer_mode` and `timer_initial`, validate the mode is `"timed60"` or `"timed90"` (else skip and leave `timer = null`), construct a new `TimedModeController(timer_mode, timer_initial)`, call `timer.restoreState(in)` (which forces paused), and set `paused = true` on the GameView so the resumed round is paused (Requirement 10.2)
    - If `timer_present` is false or the mode is not timed, leave `timer = null` and let the existing mode (`classic`/`chaos`) plumbing run unchanged
    - _Requirements: 10.1, 10.2, 10.3_

- [ ] 9. Add Timed Mode sections to High Scores screen
  - [ ] 9.1 Add Timed 60s and Timed 90s sections to activity_highscores.xml
    - Edit `app/src/main/res/layout/activity_highscores.xml`
    - Add `TextView` id `timed60Label` with text "- Timed 60s -" and a `LinearLayout` id `timed60List`, mirroring the Classic/Chaos section style
    - Add `TextView` id `timed90Label` with text "- Timed 90s -" and a `LinearLayout` id `timed90List`
    - Place both new sections below the Chaos section and above the Back button
    - _Requirements: 7.4_
  
  - [ ] 9.2 Populate Timed 60s and Timed 90s lists in HighScoreActivity
    - Edit `HighScoreActivity.java`
    - In `onCreate`, after the existing populate calls, add:
      `populateScores(findViewById(R.id.timed60List), storage.getHighScores("timed60", 10), fontReg);`
      `populateScores(findViewById(R.id.timed90List), storage.getHighScores("timed90", 10), fontReg);`
    - Apply the existing label typeface to `timed60Label` and `timed90Label`
    - _Requirements: 7.4_
  
  - [ ]* 9.3 Write property test for per-mode high-score storage
    - **Property 12: High-score storage is per-mode**
    - **Validates: Requirements 7.1, 7.2, 7.3**
    - Generate random `(score, mode)` pairs across `{"classic", "chaos", "timed60", "timed90"}`, save them all, and for each mode key assert `getHighScores(k, 100)` returns only entries saved with mode `k`

- [ ] 10. Checkpoint - Ensure all tests pass
  - Run the full test suite (property tests for the controller, ScoreManager, and storage; example tests for layout)
  - Build the app to confirm no compile errors in `GameView`, `GameActivity`, `MainActivity`, or `HighScoreActivity`
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP path; they cover the property tests for the correctness properties defined in the design.
- Each task references specific requirement clauses for traceability.
- `TimedModeController` is built first because it is pure JVM code and can be exhaustively property-tested without an Android emulator (uses `Bundle` only via Robolectric in the JVM test source set).
- `ScoreManager` extension keeps existing zero-argument signatures by delegating to the new multiplier overloads with `1.0f`, so Classic and Chaos behavior is byte-for-byte preserved.
- `GameView.setup` is the single integration point: when the mode is not timed, `timer` stays `null` and every timer branch short-circuits.
- `transitionToGameOver` unifies the two end-of-round paths (no placeable piece and timer expiry) so the time-bonus and save-score logic only lives in one place.
- Power-ups suppression (Requirement 8) is enforced by simply not constructing the power-up manager in timed modes; the contract is documented in the design and a placeholder test is deferred until the `power-ups-system` feature lands.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "3.1", "4.1", "9.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "3.2", "4.2", "9.2", "9.3"] },
    { "id": 2, "tasks": ["1.4", "1.5", "1.6", "1.7", "1.8", "1.9", "1.10", "4.3"] },
    { "id": 3, "tasks": ["2.1"] },
    { "id": 4, "tasks": ["2.2", "6.1"] },
    { "id": 5, "tasks": ["6.2", "6.3", "6.4", "6.5"] },
    { "id": 6, "tasks": ["5.1", "5.2", "6.6", "7.1", "7.3"] },
    { "id": 7, "tasks": ["7.2", "7.4", "7.5", "8.1"] }
  ]
}
```
