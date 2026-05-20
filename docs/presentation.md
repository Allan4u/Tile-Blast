---
title: "TileBlast"
subtitle: "Block Puzzle Game with 10 Features"
author: "(Isi nama developer di sini)"
date: ""
---

# TileBlast

**Block Puzzle Game with 10 Features**

Developer: _(isi nama)_

Repo: _(isi link)_

---

# Daftar Isi

- Tentang TileBlast
- Tech Stack & Arsitektur
- 5 Game Modes
- 10 Fitur Utama
- Visual & Audio
- Persistence & Testing
- Spec-Driven Development
- Tantangan Teknis
- Future Work

---

# Tentang TileBlast

- Block puzzle game native Android
- Terinspirasi Blockudoku & 1010!
- 24 bentuk piece dengan distribusi terbobot
- Drag & drop di grid 8x8 (atau 10x10 di Chaos)
- Line/column penuh akan pecah dan beri skor
- Dibuat full Java + Canvas (tanpa game engine)

---

# Tech Stack

- **Bahasa**: Java 17
- **Platform**: Android SDK 34, min SDK 24
- **Build**: Gradle Kotlin DSL
- **Backend**: Firebase Auth + Firestore
- **Audio**: SoundPool dengan pitch modulation
- **Rendering**: Canvas-based custom View
- **Testing**: jqwik (property-based testing)

---

# Arsitektur (Overview)

3-layer architecture:

- **UI Layer**: 8 Activities + custom Views
- **Game Logic**: Board, Hand, Piece, ScoreManager, PowerUpManager
- **Persistence**: StorageManager (SharedPreferences) + Firestore

Modul:

- `game/` `audio/` `storage/` `leaderboard/`
- `daily/` `stats/` `theme/` `progression/`
- `game/effects/` (sub-modul efek visual)

---

# 5 Game Modes

- **Classic** — grid 8x8, hand 3, durasi tak terbatas
- **Chaos** — grid 10x10, hand 5, special tiles aktif
- **Timed 60** — 60 detik, multiplier 1.0 hingga 2.5
- **Timed 90** — 90 detik, multiplier 1.0 hingga 3.0
- **Daily Challenge** — seed deterministic per tanggal, target 2000

---

# Fitur 1: Enhanced Visual Feedback

- Particle explosion saat line break (4-8 partikel/cell)
- Screen flash putih/gold di combo >= 3
- Zoom pulse 1.0 -> 1.03 -> 1.0 di combo >= 4
- Score pop "+N" yang naik & fade selama 800 ms
- Combo text dengan overshoot scale-in
- Perfect clear celebration: 80-120 partikel emas

Classes: `EffectManager` `ParticleSystem` `ScreenFlash` `ZoomPulse` `ScorePopManager` `ComboTextAnim`

---

# Fitur 2: Ghost Preview & Hints

- Ghost block ber-pulse alpha mengikuti drag
- Highlight cell yang akan pecah (HOVERED_BREAK)
- Tombol Hint dengan animasi attention pulse
- Best-placement scoring oleh `HintCalculator`
- Tiebreaker pakai jarak ke center board

Formula: `lines * 1000 + adjacent * 10 + edge * 5`

---

# Fitur 3: Power-Ups System

4 power-up types:

- **BOMB** — 3x3 explode, +10 per filled cell
- **LINE_SWEEP** — bersihkan baris atau kolom
- **ROTATE** — putar piece 90° saat sedang drag
- **UNDO** — kembalikan board ke sebelum penempatan

State machine: `IDLE` -> `TARGETING` -> `EXECUTING`
Acquisition: combo >= 4 atau milestone score
Cap 2 per type, 5 milestone (1K/5K/10K/25K/50K)

---

# Fitur 4: Special Tiles

- **FROZEN** — perlu 2x clear (FROZEN -> FROZEN_CRACKED -> EMPTY)
- **LOCKED** — clear sekali jadi EMPTY
- **BOMB_TILE** — countdown 5 turn, meledak 3x3
- Chain detonation = game over

Spawn probabilistik (chaos & daily):

- Frozen 10% (cap 5, min turn 5)
- Locked 5% (cap 3)
- Bomb 3% (cap 2)

Class: `SpecialTilesManager`

---

# Fitur 5: Daily Challenge

- Seed = `year*10000 + month*100 + day` (YYYYMMDD)
- Sama untuk semua pemain di tanggal yang sama
- Target 2000 score = bintang emas
- Streak tracking (consecutive starred days)
- Calendar view 6x5 grid (30 hari)
- 3 reward tiers: 3 day, 7 day, 14 day

Classes: `DailyChallengeEngine` `ChallengeStorage` `StreakTracker` `CalendarView`

---

# Fitur 6: Statistics & Achievements

20 achievement total, 5 kategori:

- **SCORE** (5) — First Game, Centurion, 1K/5K/10K Club
- **COMBO** (3) — x2, x5, x10
- **LINES** (3) — 10, 100, 1000 lines
- **GAMES** (3) — Marathon (10), Dedicated (50), Veteran (100)
- **SPECIAL** (6) — Speed Demon, Perfect, Streak 3/7, Prestige, Completionist

Per-mode stats: games played, average score, best combo, total lines, play time, win streak

Classes: `StatisticsManager` `AchievementManager` `AchievementDef`

---

# Fitur 7: Online Leaderboard (Firebase)

- Anonymous sign-in saat pertama launch
- Optional Google Sign-In (link ke akun anonymous)
- Top-100 per mode, all-time + weekly
- Snapshot listener untuk live update
- Offline queue dengan ConnectivityManager
- Firestore rules dengan field validation
- Rate limit 10 detik client-side

Classes: `AuthManager` `LeaderboardService` `ScoreQueue` `LeaderboardEntry` `LeaderboardAdapter`

---

# Fitur 8: Level Progression & XP

- 100 level, formula:
- `XP = floor(score/10) + 5*sumCombo + 50*perfectClears`
- Dikalikan `(1.0 + 0.1 * prestigeCount)`
- Prestige reset 100 -> 1, +10% multiplier permanen
- 7 reward unlock di level 5/10/20/30/50/75/100

Reward list:

- L5 Extra Hint, L10 Neon Palette, L20 Wood Skin
- L30 Extra Power-Up Slot, L50 Retro Palette
- L75 Space Skin, L100 Master Badge

Class: `LevelManager` `Reward`

---

# Fitur 9: Timed Mode

- HUD countdown warna-coded:
- Putih > 30s, kuning > 10s, merah <= 10s
- Pulse scale di 5 detik terakhir
- Score multiplier band 1.0 / 1.5 / 2.0 / 2.5 / 3.0
- Time bonus +1.5 detik per line break
- End-game bonus 5 poin per detik tersisa
- Auto-pause saat activity background

Class: `TimedModeController`

---

# Fitur 10: Themes & Customization

6 color palette:

- Default (free), Neon (5K), Pastel (10K)
- Retro (25K), Dark (50K), Ocean (100K)

5 board skin:

- Default, Wood (5K), Metal (25K), Space (50K), Pixel Art (100K)

- Unlock berdasarkan total cumulative score
- Preview live di SettingsActivity
- Skin pakai Canvas primitive (no bitmap)

Classes: `ThemeManager` `ColorPalette` `BoardSkin`

---

# Visual & Audio

Bevel block rendering:

- 4 border colors per cell (top/left/right/bottom)
- Computed dari base color via ratio matrix

9 sound effect:

- `place` `break_` `gameover` `juiciness`
- 5 combo level (`combo1` ... `combo5`)

Pitch modulation untuk power-up:

- BOMB juiciness, LINE_SWEEP break
- ROTATE place 1.5x, UNDO place 0.7x

---

# Persistence

Local (SharedPreferences):

- Best scores per mode (top 100, JSON)
- Achievement unlock state
- Statistics (games, combos, lines, playtime, streaks)
- Player level, XP, prestige count
- Active palette + skin
- Daily streak + claimed rewards

Activity state (Bundle):

- Board cells, hand, score, combo
- Power-up counts + snapshot
- Timer state (always restored as paused)

Online (Firestore):

- `leaderboards/{mode}/scores/{userId}`
- `players/{userId}` (rate limit metadata)

---

# Property-Based Testing

Framework: jqwik

~30 properties total, antara lain:

- BoardSnapshot round-trip (capture -> restore identity)
- Power-up inventory cap invariant (count <= 2)
- Score milestone uniqueness (no double-grant)
- Activation gating matrix (all combinations)
- Bomb/LineSweep apply preservation
- Rotate 4-cycle (4 rotations = identity)
- Save-restore round-trip via Bundle
- Hint scoring monotonicity
- Streak calculation determinism

---

# Spec-Driven Development

11 specs di `.kiro/specs/`:

- enhanced-visual-feedback
- ghost-preview-hints
- power-ups-system
- special-tiles
- daily-challenge
- statistics-achievements
- online-leaderboard-firebase
- level-progression-xp
- timed-mode
- themes-customization
- all-bugs-fix

Tiap spec berisi `requirements.md`, `design.md`, `tasks.md`.
Dependency graph + waves untuk parallel execution.

---

# Statistik Project

- Kurang lebih 50+ Java classes
- Sekitar 5000+ baris kode produksi
- 11 fitur major terintegrasi
- 8 Activity, 1 custom View utama, 11 effect class
- 20 achievement, 24 piece shape, 6 palette, 5 skin
- BUILD SUCCESSFUL

---

# Demo Highlights

_(isi screenshot di slide presentasi PPTX)_

- Main menu dengan level/XP bar
- Gameplay Classic dengan combo & particles
- Gameplay Chaos dengan special tiles
- Daily Challenge calendar view
- Online leaderboard (all-time + weekly)
- Settings (themes & skin preview)

---

# Tantangan Teknis (1/2)

- **Race condition di PieceSnapAnim**:
- active flag terhapus sebelum commit, piece hilang
- Solusi: `hasPendingPiece()` check terpisah dari `isActive()`

- **Firestore rules deployment**:
- Rules wajib di-deploy manual via CLI
- Default permission denied bikin user bingung
- Solusi: friendly error message + link `firebase/README.md`

---

# Tantangan Teknis (2/2)

- **Parallel subagent edit conflicts** di GameView.java:
- File 2200+ baris, multiple feature touch sama function
- Solusi: extract manager class (PowerUp, SpecialTiles, Timed)

- **Density-aware layout**:
- Block size, padding, font scaling perlu px+dp
- Solusi: `density` cached di `init()`, semua dimensi dikalikan

---

# Future Work

- Multiplayer mode (real-time vs)
- Custom theme editor (user-defined palette)
- Replay system (capture move log per game)
- Cloud save sync (Firebase Realtime DB)
- More piece shapes (curved, asymmetric)
- Tablet layout (landscape, dual-pane)
- Localization (i18n: ID, EN, JP)

---

# Penutup

**Q & A**

Project root:
`c:\Allan\CODE\GAME\TileBlast`

Repo: _(isi link)_

Terima kasih.
