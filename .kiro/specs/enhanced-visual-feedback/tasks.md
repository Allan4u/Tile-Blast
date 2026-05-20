# Implementation Plan: Enhanced Visual Feedback

## Overview

This plan implements a particle system, animation framework, and multiple visual effects for the TileBlast Android game. All rendering integrates into the existing Canvas-based `GameView.onDraw()` pipeline using zero-allocation object pooling. The implementation builds incrementally: foundation utilities first, then individual effects, then integration into the game loop.

## Tasks

- [ ] 1. Foundation — Easing utility, Particle class, and ParticlePool
  - [ ] 1.1 Create the Easing utility class
    - Create `app/src/main/java/com/allan/tileblast/game/effects/Easing.java`
    - Implement `easeOut(float t)` returning `1 - (1-t)^2`
    - Implement `easeIn(float t)` returning `t^2`
    - Implement `overshoot(float t)` using formula `1 + (2.2*t - 2.2)*t*(t - 1)` that peaks at ~1.2
    - Implement `linear(float t)` returning `t`
    - Clamp input t to [0.0, 1.0] range in all methods
    - _Requirements: 3.2, 3.3, 4.1, 4.4, 6.2_

  - [ ] 1.2 Create the Particle class
    - Create `app/src/main/java/com/allan/tileblast/game/effects/Particle.java`
    - Define fields: x, y, vx, vy, alpha, radius, color, lifetime, elapsed, active
    - Implement `reset(float x, float y, float vx, float vy, int color, float radius, float lifetime)` to initialize all fields
    - Implement `update(float deltaMs)` applying velocity, gravity (800 px/s²), and linear alpha fade
    - Implement `isExpired()` returning true when elapsed >= lifetime
    - _Requirements: 1.3, 1.4, 1.5, 1.6_

  - [ ] 1.3 Create the ParticlePool class
    - Create `app/src/main/java/com/allan/tileblast/game/effects/ParticlePool.java`
    - Pre-allocate array of 512 Particle instances in constructor
    - Implement `obtain()` returning first inactive particle, or null if pool exhausted
    - Implement `release(Particle p)` marking particle inactive
    - Implement `updateAll(float deltaMs)` updating all active particles and auto-releasing expired ones
    - Implement `drawAll(Canvas canvas, Paint paint)` rendering active particles as filled circles
    - Implement `hasActive()` and `releaseAll()`
    - _Requirements: 1.6, 1.7_

  - [ ]* 1.4 Write property tests for Easing functions
    - **Property 9: Zoom pulse easing curve** — verify easeOut and easeIn produce correct scale values
    - **Property 10: Combo text animation state** — verify overshoot peaks at ~1.2
    - **Validates: Requirements 3.2, 3.3, 4.1, 4.4**

  - [ ]* 1.5 Write property tests for Particle physics
    - **Property 4: Particle physics update** — verify position equals expected formula with gravity
    - **Property 5: Particle lifecycle — alpha and removal** — verify alpha = max(0, 1.0 - t/L) and expired state
    - **Validates: Requirements 1.4, 1.5, 1.6**

- [ ] 2. Core effects — ParticleSystem, ScreenFlash, ZoomPulse
  - [ ] 2.1 Create the ParticleSystem class
    - Create `app/src/main/java/com/allan/tileblast/game/effects/ParticleSystem.java`
    - Inject ParticlePool and screen density in constructor
    - Implement `spawnLineBreak(int cellX, int cellY, int blockSize, int gridLeft, int gridTop, int colorIndex)` spawning 4–8 particles per cell with random velocity (100–400 px/s magnitude) and radius (2–5 dp)
    - Implement `spawnCelebration(float centerX, float centerY)` spawning 80–120 gold particles in all directions
    - Implement `update(float deltaMs)` delegating to pool
    - Implement `draw(Canvas canvas, Paint paint)` delegating to pool
    - Implement `isActive()` delegating to pool's `hasActive()`
    - _Requirements: 1.1, 1.2, 1.3, 1.7, 5.3_

  - [ ] 2.2 Create the ScreenFlash class
    - Create `app/src/main/java/com/allan/tileblast/game/effects/ScreenFlash.java`
    - Implement `trigger(int comboLevel)` setting color (white for combo 3, gold for 4+) and initial opacity (15% base + 5% per level above 3, capped at 40%)
    - Implement `update(float deltaMs)` fading opacity linearly over 100–200ms duration
    - Implement `draw(Canvas canvas, Paint paint, int viewWidth, int viewHeight)` drawing full-screen overlay
    - Implement `isActive()` returning true while opacity > 0
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [ ] 2.3 Create the ZoomPulse class
    - Create `app/src/main/java/com/allan/tileblast/game/effects/ZoomPulse.java`
    - Implement `trigger()` starting pulse from scale 1.0
    - Implement `triggerFrom(float currentScale)` for interruption support
    - Implement `update(float deltaMs)` using easeOut for first 150ms (1.0→1.03) and easeIn for next 150ms (1.03→1.0)
    - Implement `getScale()` returning current scale value
    - Implement `isActive()` returning true while animating
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [ ]* 2.4 Write property tests for ParticleSystem spawn counts
    - **Property 1: Particle spawn count per cleared cell** — verify 4×N to 8×N particles for N cells
    - **Property 2: Particle color matches source cell** — verify all particles have correct color
    - **Property 3: Particle initialization invariants** — verify velocity magnitude [100, 400] and radius [2dp, 5dp]
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.7**

  - [ ]* 2.5 Write property tests for ScreenFlash and ZoomPulse
    - **Property 6: Screen flash opacity formula** — verify opacity calculation for all combo levels ≥ 3
    - **Property 7: Screen flash fade** — verify linear fade from initial opacity to 0
    - **Property 8: Zoom pulse trigger condition** — verify triggers iff combo ≥ 4 OR lines ≥ 3
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 3.1**

- [ ] 3. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 4. Text animations — ComboTextAnim and ScorePopManager
  - [ ] 4.1 Create the ComboTextAnim class
    - Create `app/src/main/java/com/allan/tileblast/game/effects/ComboTextAnim.java`
    - Implement `trigger(String text, int color, float fontSizePx, float holdDurationMs)` starting animation
    - Implement `update(float deltaMs)` with three phases: scale-in (0–300ms, overshoot), hold (300–300+hold), fade-out (last 300ms)
    - Implement `draw(Canvas canvas, Paint paint, Typeface font, int viewWidth, int viewHeight)` rendering centered text at 30% height with current scale and alpha
    - Implement `getScale()`, `getAlpha()`, `isActive()`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 5.2, 5.4_

  - [ ] 4.2 Create the ScorePop and ScorePopManager classes
    - Create `app/src/main/java/com/allan/tileblast/game/effects/ScorePop.java` with fields: x, y, offsetY, alpha, text, elapsed, active, stackIndex
    - Implement `reset(String text, float x, float y, int stackIndex)` and `update(float deltaMs)` with 800ms lifetime, 60dp rise, linear alpha fade
    - Create `app/src/main/java/com/allan/tileblast/game/effects/ScorePopManager.java` with pool of 8 ScorePop instances
    - Implement `spawn(String text, float x, float y, float density)` with 200ms stacking window and 25dp vertical offset per stack index
    - Implement `update(float deltaMs)` and `draw(Canvas canvas, Paint paint, Typeface font, float density)` rendering white 20dp bold text
    - Implement `isActive()` returning true if any pop is active
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [ ]* 4.3 Write property tests for ComboTextAnim
    - **Property 10: Combo text animation state** — verify scale/alpha at each phase boundary
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4**

  - [ ]* 4.4 Write property tests for ScorePop
    - **Property 14: Score pop text format** — verify text equals "+" + N for score gain N
    - **Property 15: Score pop animation state** — verify offset = -60dp × (t/800) and alpha = 1.0 - t/800
    - **Property 16: Score pop stacking** — verify distinct vertical offsets within 200ms window
    - **Validates: Requirements 7.1, 7.2, 7.3, 7.5**

- [ ] 5. Gameplay integration — PieceSnapAnim and Perfect Clear detection
  - [ ] 5.1 Create the PieceSnapAnim class
    - Create `app/src/main/java/com/allan/tileblast/game/effects/PieceSnapAnim.java`
    - Implement `start(Piece piece, float fromX, float fromY, float toX, float toY, int gridX, int gridY)` storing start/end positions
    - Implement `update(float deltaMs)` interpolating position with easeOut over 100ms
    - Implement `getCurrentX()`, `getCurrentY()` returning interpolated position
    - Implement `isComplete()` returning true when elapsed >= 100ms
    - Implement `forceComplete()` immediately setting position to end and marking complete
    - Implement `isActive()` returning true while animating
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [ ] 5.2 Add Perfect Clear detection to Board and ScoreManager
    - Add `isEmpty()` method to `Board.java` that checks all cells for EMPTY state
    - Add `addPerfectClearBonus()` method to `ScoreManager.java` that adds 500 points to score
    - _Requirements: 5.1_

  - [ ]* 5.3 Write property tests for PieceSnapAnim and Perfect Clear
    - **Property 13: Piece snap interpolation** — verify position = S + (E - S) × easeOut(t/100)
    - **Property 11: Perfect clear detection and bonus** — verify 500 points added when board is empty
    - **Property 12: Perfect clear celebration particles** — verify 80–120 gold particles spawned
    - **Validates: Requirements 5.1, 5.3, 6.1, 6.2**

- [ ] 6. EffectManager and GameView integration
  - [ ] 6.1 Create the EffectManager class
    - Create `app/src/main/java/com/allan/tileblast/game/effects/EffectManager.java`
    - Initialize all sub-effects (ParticleSystem, ScreenFlash, ZoomPulse, ComboTextAnim, ScorePopManager, PieceSnapAnim) in `init(float density)`
    - Implement trigger methods: `onLineBreak(...)`, `onCombo(int comboLevel, int linesBroken)`, `onPerfectClear(...)`, `onScoreGain(int points, float x, float y)`, `startPieceSnap(...)`
    - Implement `update()` calculating deltaTime internally (clamped to 33ms max) and updating all sub-effects
    - Implement draw methods: `applyZoomTransform(Canvas, float, float)`, `drawParticles(Canvas)`, `drawScreenFlash(Canvas, int, int)`, `drawScorePops(Canvas)`, `drawComboText(Canvas, int, int)`, `drawPieceSnap(Canvas, float)`
    - Implement `isActive()` returning true if any sub-effect is active
    - Implement `isPieceSnapping()` and `getPieceSnapAnim()`
    - _Requirements: 1.1, 2.1, 3.1, 4.1, 5.3, 6.1, 7.1_

  - [ ] 6.2 Integrate EffectManager into GameView.onDraw() pipeline
    - Add `EffectManager effectManager` field to `GameView.java` and initialize in `init()` / `setup()`
    - Modify `onDraw()` to call `effectManager.update()` at the start of each frame
    - Apply zoom transform via `effectManager.applyZoomTransform(canvas, gridCenterX, gridCenterY)` after shake translate
    - Add `effectManager.drawPieceSnap(canvas, blockSize)` after hand pieces
    - Add `effectManager.drawParticles(canvas)` after `canvas.restore()`
    - Add `effectManager.drawScreenFlash(canvas, w, h)` after particles
    - Add `effectManager.drawScorePops(canvas)` after screen flash
    - Add `effectManager.drawComboText(canvas, w, h)` after score pops (replacing existing `drawComboOverlay`)
    - Call `invalidate()` when `effectManager.isActive()` to keep render loop alive
    - _Requirements: 2.5, 5.6_

  - [ ] 6.3 Integrate effect triggers into GameView.attemptPlacement()
    - Call `effectManager.onLineBreak(clearedCells, blockSize, gridLeft, gridTop)` when lines are broken
    - Call `effectManager.onCombo(comboLevel, linesBroken)` to trigger screen flash and zoom pulse
    - Add perfect clear check: if `linesBroken > 0 && board.isEmpty()` then call `scoreManager.addPerfectClearBonus()`, `effectManager.onPerfectClear(...)`, and `audioManager.playPerfectClear()`
    - Call `effectManager.onScoreGain(points, x, y)` for score pop on each point gain
    - Replace immediate piece commit with `effectManager.startPieceSnap(...)` for smooth snap animation
    - Handle piece snap completion: commit piece to board and proceed with line break detection
    - Handle piece snap interruption: call `forceComplete()` when new drag starts during active snap
    - _Requirements: 1.1, 2.1, 3.1, 5.1, 5.5, 6.4, 6.5, 7.1_

  - [ ] 6.4 Add perfect clear sound to AudioManager
    - Add `playPerfectClear()` method to `AudioManager.java` loading and playing a dedicated sound effect
    - Follow existing pattern used by `playCombo()` and `playBreak()`
    - _Requirements: 5.5_

- [ ] 7. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 8. Final verification and cleanup
  - [ ]* 8.1 Write unit tests for EffectManager integration
    - Test that `onCombo(3, ...)` triggers screen flash but not zoom pulse
    - Test that `onCombo(4, ...)` triggers both screen flash and zoom pulse
    - Test that `onLineBreak` with 3+ lines triggers zoom pulse regardless of combo
    - Test perfect clear triggers celebration particles, bonus, and text
    - Test piece snap interruption via `forceComplete()`
    - Test deltaTime clamping to 33ms max
    - _Requirements: 2.1, 3.1, 5.1, 5.3, 6.5_

  - [ ]* 8.2 Write unit tests for rendering order and edge cases
    - Test particles render above game elements but below overlays
    - Test screen flash renders beneath game elements (overlay on top)
    - Test score pop stacking with multiple rapid score gains
    - Test particle pool exhaustion silently drops new spawns
    - Test ScorePop pool recycles oldest when exhausted
    - _Requirements: 2.5, 5.6, 7.5_

- [ ] 9. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- All new classes go in the `com.allan.tileblast.game.effects` package
- The design uses Java with Android Canvas API — no external dependencies needed beyond jqwik for property tests
- DeltaTime is clamped to 33ms to prevent physics glitches on app resume

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "5.2"] },
    { "id": 2, "tasks": ["1.4", "1.5", "2.1", "2.2", "2.3"] },
    { "id": 3, "tasks": ["2.4", "2.5", "4.1", "4.2", "5.1"] },
    { "id": 4, "tasks": ["4.3", "4.4", "5.3"] },
    { "id": 5, "tasks": ["6.1"] },
    { "id": 6, "tasks": ["6.2", "6.3", "6.4"] },
    { "id": 7, "tasks": ["8.1", "8.2"] }
  ]
}
```
