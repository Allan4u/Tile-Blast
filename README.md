# TileBlast

> Android Block Puzzle Game — Capstone Project

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](#)
[![License](https://img.shields.io/badge/license-MIT-blue)](#license)
[![Android API](https://img.shields.io/badge/API-24%2B-green)](#)
[![Java](https://img.shields.io/badge/Java-17-orange)](#)
[![Firebase](https://img.shields.io/badge/Firebase-Auth%20%2B%20Firestore-orange)](#)
[![Platform](https://img.shields.io/badge/Platform-Android-3ddc84)](#)

---

## Daftar Isi

- [Tentang Project](#tentang-project)
- [Fitur Utama](#fitur-utama)
  - [Enhanced Visual Feedback](#1-enhanced-visual-feedback)
  - [Ghost Preview & Hints](#2-ghost-preview--hints)
  - [Power-Ups System](#3-power-ups-system)
  - [Special Tiles](#4-special-tiles)
  - [Daily Challenge](#5-daily-challenge)
  - [Statistics & Achievements](#6-statistics--achievements)
  - [Level Progression & XP](#7-level-progression--xp)
  - [Timed Mode](#8-timed-mode)
  - [Themes & Customization](#9-themes--customization)
  - [Online Leaderboard & Auth](#10-online-leaderboard--auth)
- [Tech Stack](#tech-stack)
- [Game Modes](#game-modes)
- [Struktur Project](#struktur-project)
- [Dokumentasi Per File](#dokumentasi-per-file)
  - [Activities](#activities)
  - [Game Core](#game-core)
  - [Visual Effects](#visual-effects)
  - [Audio](#audio)
  - [Storage](#storage)
  - [Daily Challenge Module](#daily-challenge-module)
  - [Statistics Module](#statistics-module)
  - [Progression Module](#progression-module)
  - [Theme Module](#theme-module)
  - [Leaderboard Module](#leaderboard-module)
- [Build & Run](#build--run)
- [Testing](#testing)
- [Persistence Architecture](#persistence-architecture)
- [Metodologi Pengembangan](#metodologi-pengembangan)
- [Roadmap / Future Work](#roadmap--future-work)
- [Contributing](#contributing)
- [License](#license)
- [Credits](#credits)

---

## Tentang Project

**TileBlast** adalah game block puzzle untuk Android yang dikembangkan sebagai
**Capstone Project**, terinspirasi dari Blockudoku dan 1010!. Pemain menempatkan
tiga (atau lima) potongan dari "hand" ke papan grid, dan menghapus baris atau
kolom yang terisi penuh untuk mendapatkan poin. Tantangannya adalah merencanakan
penempatan sebelum kehabisan ruang.

Seluruh rendering board dan piece dilakukan via custom `Canvas` view (`GameView`)
— tanpa bitmap besar maupun engine pihak ketiga. Fokus desain ada pada
**juiciness dan game feel**: particle effect, screen flash, zoom pulse, score pop,
combo text, dan piece snap animation membuat setiap penempatan terasa memuaskan.

Project ini mengimplementasikan **9 fitur utama** secara bertahap, mulai dari
visual feedback, ghost preview, sistem power-up dan special tiles, daily challenge
yang seed-deterministic, hingga leaderboard online berbasis Firebase Firestore.
Semua fitur dirancang dengan mempertimbangkan kualitas kode: zero-allocation
render path, persistence yang corruption-resilient, dan state machine yang
testable.

---

## Fitur Utama

### 1. Enhanced Visual Feedback

Lapisan efek visual yang membuat tindakan pemain terasa hidup. `EffectManager`
mengoordinasi enam subsistem yang dihitung sekali per frame dengan delta
waktu yang di-clamp pada 33 ms agar tidak ada physics glitch saat aplikasi
sempat lag.

Setiap line clear memicu `ParticleSystem` untuk menyemprotkan 4–8 partikel
per cell yang dibersihkan, lengkap dengan gravitasi 800 px/s² dan fade
linear sepanjang lifetime acak 400–700 ms. Particle memakai pool 512 slot
sehingga tidak ada alokasi heap di hot path. Saat combo mencapai level 3
atau lebih, `ScreenFlash` menyalakan overlay semi-transparan (putih untuk
combo 3, emas untuk combo 4+ dengan opacity yang naik per level), sementara
`ZoomPulse` memberi pulse halus pada grid (1.0 → 1.03 → 1.0 dalam 300 ms)
ketika combo mencapai 4 atau lebih dari 3 baris dibersihkan sekaligus.

Setiap penambahan poin memunculkan `ScorePop` melayang ke atas selama 800
ms dengan stacking otomatis bila beberapa pop terjadi dalam window 200 ms.
Combo besar menampilkan teks "COMBO xN!" dengan animasi overshoot scale-in,
hold, lalu fade-out. Perfect clear (papan kosong total) memicu celebration
khusus dengan 80–120 partikel emas dan teks "PERFECT CLEAR" 800 ms.

### 2. Ghost Preview & Hints

Saat pemain men-drag piece, `GameView` menampilkan **ghost preview**:
piece di-render semi-transparan pada cell-cell di bawah jari. Bila
penempatan tersebut akan men-trigger line break, baris atau kolom yang
akan terhapus disorot dengan pulse warna emas (`drawLineBreakHighlight`)
sehingga pemain bisa membaca hasil sebelum melepas jari.

Setiap pemain juga dapat menekan tombol **HINT** (3x per game). Tombol ini
memanggil `HintCalculator` yang mengevaluasi semua valid placement untuk
semua piece di hand, lalu memberi skor berdasarkan formula:

```
score = lines_completed * 1000
      + adjacent_filled_cells * 10
      + edge_cells * 5
```

Tiebreaker dipakai jarak Manhattan ke pusat papan. Hasil terbaik
ditampilkan sebagai indikator berkedip pada cell target sehingga pemain
melihat saran tanpa langsung dieksekusi.

### 3. Power-Ups System

Pemain dapat memperoleh empat jenis power-up melalui combo besar (level ≥ 4)
maupun milestone score (1000, 5000, 10000, 25000, 50000). Inventory dibatasi
**maksimal 2 per type** untuk menjaga keseimbangan, dan setiap power-up
ditampilkan di slot HUD dengan badge counter.

| Power-Up      | Efek                                                                 |
|---------------|----------------------------------------------------------------------|
| `BOMB`        | Membersihkan area 3×3 (clamped ke grid) dan memberi 10 poin per cell terisi |
| `LINE_SWEEP`  | Membersihkan satu baris atau kolom penuh (dipilih dari posisi finger) |
| `ROTATE`      | Memutar piece yang sedang di-drag 90° searah jarum jam (consecutive allowed) |
| `UNDO`        | Mengembalikan board, hand, dan score ke snapshot sebelum placement terakhir |

Aktivasi melewati state machine `IDLE → TARGETING → EXECUTING → IDLE` di
`PowerUpManager`. BOMB dan LINE_SWEEP masuk mode targeting sehingga pemain
memilih cell, lalu efek diterapkan. ROTATE dan UNDO langsung apply (immediate
mode). Snapshot untuk UNDO disimpan via `BoardSnapshot` yang melakukan deep
copy cells, colors, hand pieces (termasuk matrix yang sudah di-rotate), dan
score; snapshot dibuang segera setelah ada line break agar undo tidak
memblok progress mekanik utama.

### 4. Special Tiles

Untuk mode Chaos dan Daily, tiap turn ada peluang melakukan spawn tile spesial
pada cell kosong. `SpecialTilesManager` mengevaluasi tiga tipe secara
independen — frozen, locked, bomb — dengan probabilitas berbeda dan cap
maksimum simultan.

| Tile          | Probabilitas | Cap | Catatan                                              |
|---------------|--------------|-----|------------------------------------------------------|
| `FROZEN`      | 10%          | 5   | Butuh 2 line clear untuk dibersihkan (cracked → empty); spawn hanya setelah turn ke-5 |
| `LOCKED`      | 5%           | 3   | Bisa dibersihkan dalam 1 line clear, tetapi tidak bisa di-overwrite oleh piece     |
| `BOMB_TILE`   | 3%           | 2   | Countdown 5 turn; ledakkan 3×3 saat 0; chain detonation = game over instan         |

Bomb mendekrementasi countdownnya setiap turn, dan ledakan rantai (bomb yang
mendetonasi bomb lain) memicu callback `onChainBombGameOver` untuk akhiri
game. Visual khusus untuk frozen/locked/bomb dirender oleh `drawSpecialCell`
di GameView dengan crack pattern dan countdown text.

### 5. Daily Challenge

Tantangan harian dengan **seed deterministic** dari tanggal lokal device.
`DailyChallengeEngine.generateSeed()` menghasilkan integer
`year*10000 + month*100 + day` (format YYYYMMDD), lalu seed itu diumpankan
ke `Random` yang dipakai oleh `Hand` dan `SpecialTilesManager`. Hasilnya:
semua pemain di tanggal yang sama mendapat puzzle yang sama.

Target harian adalah **2000 poin**. Mencapai target memberi ⭐ untuk hari
itu; ⭐ konsekutif membentuk **streak**. Streak diawasi oleh `StreakTracker`
yang menjelajah mundur dari hari yang baru di-bintangi. Tiga reward tier
tersedia:

| Streak  | Reward          | Deskripsi                |
|---------|-----------------|--------------------------|
| 3 hari  | Power-up Bonus  | +1 random power-up       |
| 7 hari  | Theme Unlocked  | Unlock palette/skin tier |
| 14 hari | Prestige XP     | +XP multiplier permanent |

`CalendarView` menampilkan grid 6×5 untuk 30 hari terakhir (terbaru di kanan
bawah), masing-masing cell berisi skor compact, ⭐, atau outline kosong.
Highlight putih khusus untuk hari ini.

### 6. Statistics & Achievements

`StatisticsManager` menumpuk angka-angka kunci antar sesi: jumlah game per
mode, total game, cumulative score per mode (untuk hitung average), best
combo, total lines cleared, total play time, current/best win streak.
Streak dipelihara dengan threshold "win" = skor di atas 1000.

Sebanyak **20 achievements** dievaluasi oleh `AchievementManager` pada
event-event spesifik (game end, combo new, perfect clear, prestige
activated). Setiap achievement idempotent — sekali terbuka tetap terbuka.
Achievement `COMPLETIONIST` terbuka otomatis saat 19 lainnya sudah terbuka.

### 7. Level Progression & XP

Pemain naik melalui **100 level**. Setiap akhir game, `LevelManager`
menghitung XP berdasarkan formula:

```
gainedXP = floor( ( floor(score/10)
                    + sum(5 * comboLevel_i for each combo)
                    + 50 * perfectClearCount )
                  * (1.0 + 0.1 * prestigeCount) )
```

`getXPForNextLevel()` mengembalikan `level * 100`, jadi level 5 butuh 500
XP, level 50 butuh 5000 XP. Multi-level-up (XP melebihi beberapa level
sekaligus) ditangani dalam loop sehingga overlay menampilkan level
tertinggi yang dicapai.

Setelah mencapai level 100 pemain dapat **prestige**: level direset ke 1,
prestige count bertambah, dan XP multiplier permanent bertambah +10%.
Reward yang sudah unlock dipertahankan.

| Level | Reward                     | Deskripsi                              |
|-------|----------------------------|----------------------------------------|
| 5     | Extra Hint                 | +1 hint slot per game                  |
| 10    | Neon Palette               | Unlock color palette neon              |
| 20    | Wood Skin                  | Unlock board skin kayu                 |
| 30    | Extra Power-Up Slot        | Cap inventory naik dari 2 ke 3         |
| 50    | Retro Palette              | Unlock color palette retro             |
| 75    | Space Skin                 | Unlock board skin luar angkasa         |
| 100   | Master Title Badge         | Badge bergengsi di profile             |

### 8. Timed Mode

Dua varian timed: **Timed 60s** dan **Timed 90s**. `TimedModeController`
adalah pure JVM class (Bundle satu-satunya dependensi Android) yang
mengelola elapsed time, accumulated bonus dari line break, multiplier
band, dan end-of-round bonus.

Score multiplier band:

| Elapsed (s)    | Multiplier           |
|----------------|----------------------|
| 0–14           | 1.0×                 |
| 15–29          | 1.5×                 |
| 30–44          | 2.0×                 |
| 45–59          | 2.5×                 |
| 60+            | 2.5× (timed60) / 3.0× (timed90) |

Setiap line break memberi **+1.5 detik** waktu (ditampilkan flash 800 ms
di HUD). Saat waktu habis, sisa waktu dikonversi jadi end bonus dengan
formula `5 * floor(remaining)`. Activity auto-pause timer saat
`onPause()` agar tidak ada game berjalan di background.

### 9. Themes & Customization

Pemain bisa mengganti tampilan via Settings:

- **6 Color Palette**: DEFAULT, NEON, PASTEL, RETRO, DARK, OCEAN — setiap
  palette punya 6 RGB triplet untuk piece colors.
- **5 Board Skin**: DEFAULT, WOOD, METAL, SPACE, PIXEL_ART — masing-masing
  punya `drawBackground` Canvas-only (tanpa bitmap aset).

Palette dan skin di-unlock berdasarkan **cumulative total score** lintas
semua game. `ThemeManager` adalah singleton thread-safe (double-checked
locking) yang resolusi palette/skin invalid saat load — kalau pemain
sebelumnya pakai palette yang sekarang locked (misalnya total score
direset manual), aktivasi otomatis fallback ke DEFAULT.

| Item        | Unlock Threshold | Tipe    |
|-------------|------------------|---------|
| DEFAULT     | 0                | palette + skin |
| NEON / WOOD | 5,000            | palette / skin |
| PASTEL      | 10,000           | palette |
| METAL / RETRO | 25,000         | skin / palette |
| DARK / SPACE | 50,000          | palette / skin |
| OCEAN / PIXEL_ART | 100,000    | palette / skin |

### 10. Online Leaderboard & Auth

Pemain dapat melihat skor terbaik secara global melalui **Online Leaderboard** yang didukung oleh Firebase Firestore. Tersedia sistem otentikasi ganda:

- **Anonymous Sign-In**: Login otomatis sebagai guest (mis. "Player1234") sehingga pemain bisa langsung bermain.
- **Google Sign-In**: Menghubungkan akun anonymous ke akun Google agar data skor tersimpan aman melintasi perangkat.

Skor disubmit secara cerdas: hanya jika memecahkan *personal best* untuk mode tersebut. Terdapat sistem offline-queueing `ScoreQueue` yang akan menampung skor apabila device sedang tidak terhubung internet, dan otomatis melakukan sinkronisasi ke Firestore ketika koneksi kembali.

---

## Tech Stack

- **Bahasa**: Java 17
- **Android**: SDK 34 (compileSdk + targetSdk), `minSdk 24`
- **Build**: Gradle Kotlin DSL (`build.gradle.kts`)
- **Rendering**: Custom `Canvas` view (no engine pihak ketiga)
- **Audio**: `SoundPool` (max 6 streams, USAGE_GAME)
- **Backend**: Firebase Authentication & Cloud Firestore (Leaderboard)
- **Persistence**: `SharedPreferences` + JSON untuk struktur kompleks
- **Property-Based Testing**: [jqwik](https://jqwik.net) 1.8.5
- **Unit Testing**: JUnit 5 (Jupiter) + JUnit 4 (legacy compat) + Mockito
- **Desugaring**: `desugar_jdk_libs` 2.0.4 untuk `java.time` di minSdk 24
- **AndroidX**: appcompat, activity, core, recyclerview, material

---

## Game Modes

| Mode             | Grid  | Hand | Durasi    | Target | Special                               |
|------------------|-------|------|-----------|--------|---------------------------------------|
| Classic          | 8×8   | 3    | unlimited | —      | Permainan murni, tanpa special tiles  |
| Chaos            | 10×10 | 5    | unlimited | —      | Spawn frozen/locked/bomb tiles aktif  |
| Timed 60         | 8×8   | 3    | 60 detik  | —      | Multiplier 1.0–2.5×, +1.5s per line   |
| Timed 90         | 8×8   | 3    | 90 detik  | —      | Multiplier 1.0–3.0×, +1.5s per line   |
| Daily Challenge  | 8×8   | 3    | unlimited | 2000   | Seed deterministic, special tiles aktif, streak rewards |

---

## Struktur Project

```
TileBlast/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/allan/tileblast/
│           │   ├── MainActivity.java
│           │   ├── GameActivity.java
│           │   ├── HighScoreActivity.java
│           │   ├── DailyChallengeActivity.java
│           │   ├── StatisticsActivity.java
│           │   ├── AchievementActivity.java
│           │   ├── SettingsActivity.java
│           │   ├── AppContext.java
│           │   ├── audio/
│           │   │   └── AudioManager.java
│           │   ├── daily/
│           │   │   ├── CalendarView.java
│           │   │   ├── ChallengeStorage.java
│           │   │   ├── DailyChallengeEngine.java
│           │   │   ├── DayEntry.java
│           │   │   ├── StreakReward.java
│           │   │   └── StreakTracker.java
│           │   ├── game/
│           │   │   ├── Board.java
│           │   │   ├── BoardSnapshot.java
│           │   │   ├── GameView.java
│           │   │   ├── Hand.java
│           │   │   ├── HintCalculator.java
│           │   │   ├── Piece.java
│           │   │   ├── PowerUpManager.java
│           │   │   ├── PowerUpType.java
│           │   │   ├── ScoreManager.java
│           │   │   ├── SpecialTilesManager.java
│           │   │   ├── TimedModeController.java
│           │   │   └── effects/
│           │   │       ├── ComboTextAnim.java
│           │   │       ├── Easing.java
│           │   │       ├── EffectManager.java
│           │   │       ├── Particle.java
│           │   │       ├── ParticlePool.java
│           │   │       ├── ParticleSystem.java
│           │   │       ├── PieceSnapAnim.java
│           │   │       ├── ScorePop.java
│           │   │       ├── ScorePopManager.java
│           │   │       ├── ScreenFlash.java
│           │   │       └── ZoomPulse.java
│           │   ├── progression/
│           │   │   ├── LevelManager.java
│           │   │   └── Reward.java
│           │   ├── stats/
│           │   │   ├── AchievementDef.java
│           │   │   ├── AchievementManager.java
│           │   │   └── StatisticsManager.java
│           │   ├── leaderboard/
│           │   │   ├── AuthManager.java
│           │   │   ├── LeaderboardAdapter.java
│           │   │   ├── LeaderboardEntry.java
│           │   │   ├── LeaderboardService.java
│           │   │   └── ScoreQueue.java
│           │   ├── storage/
│           │   │   └── StorageManager.java
│           │   └── theme/
│           │       ├── BoardSkin.java
│           │       ├── ColorPalette.java
│           │       └── ThemeManager.java
│           └── res/
│               ├── drawable/        (vector drawables, button backgrounds)
│               ├── font/            (silkscreen, silkscreen_bold)
│               ├── layout/          (activity XML layouts)
│               ├── raw/             (sound effects + logo)
│               └── values/          (colors, strings, themes)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── .gitignore
└── README.md
```

---

## Dokumentasi Per File


### Activities

#### `MainActivity.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/MainActivity.java`
**Tujuan:** Layar utama menu game; menampilkan progresi level pemain dan
tombol-tombol untuk masuk ke berbagai mode dan layar.
**Dependencies:** `LevelManager`, `StorageManager`, `R.layout.activity_main`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `onCreate(Bundle)` | Inisialisasi `AppContext`, set fullscreen, bind tombol mode (Classic/Chaos/Timed60/Timed90), bind progression UI dan tombol Settings/Statistics/Achievements |
| `onResume()` | Refresh progression UI setiap kali activity kembali ke foreground |
| `refreshProgressionUi()` | Reload level/XP/prestige dari storage, update `levelText`, `xpProgress`, `prestigeStars`, dan tombol Prestige (visible saat level == MAX) |
| `startGame(int boardSize, int handSize, String mode, float durationSec)` | Bangun Intent ke `GameActivity` dengan extra config untuk mode terpilih |

**Logika Kunci:**
Tombol Prestige hanya tampil saat `level >= LevelManager.MAX_LEVEL`. Dialog konfirmasi
muncul sebelum eksekusi `levelManager.activatePrestige()` agar pemain tidak reset
secara tidak sengaja.

---

#### `GameActivity.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/GameActivity.java`
**Tujuan:** Host untuk `GameView` saat bermain mode Classic/Chaos/Timed.
Mengelola overlay level-up dan auto-pause Timed Mode saat backgrounded.
**Dependencies:** `GameView`, `AudioManager`, `Reward`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `onCreate(Bundle)` | Bangun `FrameLayout` root, inisialisasi `GameView` dengan extras dari Intent (`boardSize`, `handSize`, `mode`, `durationSec`), restore state dari savedInstanceState |
| `onGameOver(int finalScore)` | Callback dari `GameView`; high score lokal sudah disimpan oleh GameView |
| `onPauseRequested()` | Callback yang mengakhiri activity (return ke menu) |
| `onLevelUp(List<Integer>, List<Reward>)` | Callback yang menampilkan overlay level-up dengan level tertinggi tercapai dan list reward baru |
| `showLevelUpOverlay(int newLevel, List<Reward> newRewards)` | Bangun overlay full-screen dengan typography custom dan tombol "TAP TO CONTINUE" |
| `onSaveInstanceState(Bundle)` | Delegate ke `gameView.saveState(outState)` |
| `onPause()` | Auto-pause `GameView` jika game belum berakhir (Timed Mode requirement) |
| `onDestroy()` | Release `AudioManager` singleton |

**Logika Kunci:**
Overlay level-up dibangun manual via `LinearLayout` dan `TextView` dengan typeface
silkscreen. Saat `onBackPressed`, jika overlay sedang tampil, dia di-dismiss dulu;
kemudian delegate ke `GameView.onBackPressed()` untuk cancel power-up targeting,
baru terakhir handle pause/exit.

---

#### `HighScoreActivity.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/HighScoreActivity.java`
**Tujuan:** Tampilkan top-10 high score lokal untuk masing-masing mode (classic, chaos, timed60, timed90).
**Dependencies:** `StorageManager`, `R.layout.activity_highscores`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `onCreate(Bundle)` | Bind 4 `LinearLayout` per mode dan populate via `populateScores` |
| `populateScores(LinearLayout, List<HighScore>, Typeface)` | Render row "#1  10,000 pts • 15 Mar 2025"; rank 1 di-highlight emas |

---

#### `LeaderboardActivity.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/LeaderboardActivity.java`
**Tujuan:** Menampilkan global leaderboard dengan lima tab game-mode dan toggle All-Time / Weekly. Menggunakan Firestore snapshot listener untuk update data secara real-time. Menangani flow otentikasi UI (Google Sign-In, ubah nama).
**Dependencies:** `LeaderboardService`, `AuthManager`, `LeaderboardAdapter`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `onCreate(Bundle)` | Bind UI elements, initialize RecyclerView, TabLayout, ToggleGroup, dan attach event listener. |
| `loadLeaderboard()` | Re-attach snapshot listener Firestore sesuai mode dan filter (weekly/all-time) yang aktif. |
| `updateAuthUi()` | Menampilkan status login (Anonymous / Google) dan display name pemain. |
| `showEditNameDialog()` | Membuka dialog untuk pemain mengganti display name-nya (3-20 karakter). |
| `onAuthButtonClicked()` | Memulai flow Google Sign-In atau Sign-Out bergantung pada status sesi saat ini. |

---

#### `DailyChallengeActivity.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/DailyChallengeActivity.java`
**Tujuan:** Host Daily Challenge dengan seed deterministic dari tanggal lokal.
Menyimpan hasil, update streak, tampilkan dialog reward, dan calendar overlay.
**Dependencies:** `DailyChallengeEngine`, `ChallengeStorage`, `StreakTracker`,
`CalendarView`, `GameView`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `onCreate(Bundle)` | Generate seed via `DailyChallengeEngine`, build `Random`, panggil `gameView.setup(...)` overload yang menerima seeded RNG |
| `onGameOver(int finalScore)` | Record skor, award star bila `finalScore >= TARGET_SCORE`, recompute streak, cek reward, tampilkan dialog |
| `onLevelUp(...)` | No-op intentional supaya level-up tidak mengganggu dialog daily |
| `showResultDialog(int score, boolean starEarned, List<StreakReward> newRewards)` | Build dialog kustom dengan TextView styled, plus tombol "View Calendar" / "Back" |
| `showCalendar()` | Build overlay full-screen dengan `CalendarView` 30 hari terakhir |
| `onSaveInstanceState(Bundle)` | Delegate ke `gameView.saveState` |

---

#### `StatisticsActivity.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/StatisticsActivity.java`
**Tujuan:** Tampilkan statistik kumulatif dari `StatisticsManager` dalam beberapa section
(games, average, combo, lines, playtime, streak).
**Dependencies:** `StatisticsManager`, `R.layout.activity_statistics`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `onCreate(Bundle)` | Bind semua TextView, apply font silkscreen, populate dari `StatisticsManager` |
| `setText(int id, String text)` | Helper safe set untuk TextView via `findViewById` |
| `applyFont(Typeface, int...)` | Helper bulk apply font ke beberapa TextView |
| `formatScore(int)` | Format thousand-separator dengan `Locale.US` |

---

#### `AchievementActivity.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/AchievementActivity.java`
**Tujuan:** Tampilkan grid 20 achievement, locked vs unlocked dengan visual berbeda.
**Dependencies:** `AchievementManager`, `AchievementDef`, `R.layout.activity_achievements`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `onCreate(Bundle)` | Build grid via loop `AchievementDef.values()`, gunakan `buildBadge()` per item |
| `buildBadge(AchievementDef, boolean unlocked, Typeface fontBold, Typeface fontReg)` | Bangun `LinearLayout` dengan background drawable berbeda (gold border vs grey), name dengan ✓ prefix kalau unlocked, deskripsi di bawahnya |
| `dpToPx(int dp)` | Konversi dp → px menggunakan display metrics |

---

#### `SettingsActivity.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/SettingsActivity.java`
**Tujuan:** Settings screen untuk Themes & Customization. Hosts custom Canvas-based
`SettingsView` yang menggambar palette tiles, skin tiles, preview area, tombol back.

#### Inner Class `SettingsView`

| Signature | Deskripsi |
|-----------|-----------|
| `SettingsView(Context)` | Inisialisasi `ThemeManager`, `Paint`, `Typeface`, `RectF[]` arrays |
| `onSizeChanged(int, int, int, int)` | Trigger `layoutRects(w, h)` |
| `layoutRects(int w, int h)` | Hitung posisi backRect, paletteRects (2×3), skinRects (1×5), previewRect |
| `onDraw(Canvas)` | Render header, palette grid, skin strip, preview |
| `drawPaletteTile(...)` | Render satu palette tile dengan 6-color swatch dan selection indicator |
| `drawSkinTile(...)` | Render satu skin tile dengan thumbnail dari `BoardSkin.drawBackground` |
| `drawLockOverlay(Canvas, RectF, int threshold)` | Overlay 50% black + lock glyph + threshold value untuk locked items |
| `drawPreview(Canvas)` | Render mini-grid 4×4 dengan active skin background dan 3 sample piece |
| `onTouchEvent(MotionEvent)` | Hit-test rectangles, panggil `setActivePalette` / `setActiveSkin` atau Toast "Unlock at N points" |

**Logika Kunci:**
Sample shape (`SAMPLE_SHAPE_A/B/C`) di-render dengan beveled block (4 border colors)
sehingga preview menunjukkan tampilan piece sesungguhnya dengan palette aktif.
Lock overlay memakai unicode `🔒` glyph + threshold dengan format thousand-separator.

---

#### `AppContext.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/AppContext.java`
**Tujuan:** Tiny holder application context agar static API (seperti
`Piece.getColors()`) bisa resolve Context tanpa thread-through di setiap call site.
**Dependencies:** —

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `init(Context c)` | Simpan `c.getApplicationContext()` ke field static |
| `get()` | Return application context, atau null bila belum di-init |

**Logika Kunci:**
Activities harus panggil `AppContext.init(getApplicationContext())` sekali saat
`onCreate`. Fallback handling di `Piece.getColors()` mengembalikan
`ColorPalette.DEFAULT` bila context belum siap (untuk unit test atau startup race).


### Game Core

#### `Board.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/Board.java`
**Tujuan:** Representasi grid game dengan state per cell, color index, hover preview,
dan bomb countdown.
**Dependencies:** `Piece`

#### Public Constants

- `EMPTY = 0`, `FILLED = 1`, `HOVERED = 2`, `HOVERED_BREAK_FILLED = 3`,
  `HOVERED_BREAK_EMPTY = 4`, `FROZEN = 5`, `FROZEN_CRACKED = 6`,
  `LOCKED = 7`, `BOMB_TILE = 8` — semua kemungkinan cell state

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `Board(int size)` | Konstruktor; initialize cells, colors, hoverColors, bombCountdowns; pre-fill colors dengan random index |
| `getSize()` | Return ukuran board |
| `getCell(int x, int y)` | Return state cell |
| `getCellColor(int x, int y)` | Return color index cell |
| `getHoverColor(int x, int y)` | Return color hover preview cell |
| `setCell(int x, int y, int state, int colorIdx)` | Set state + color sekaligus |
| `static isSpecial(int cellState)` | True untuk FROZEN/FROZEN_CRACKED/LOCKED/BOMB_TILE |
| `static isLineFillable(int cellState)` | True untuk FILLED + semua special state (utk line completion check) |
| `getBombCountdown(int x, int y)` | Return countdown bomb |
| `setBombCountdown(int x, int y, int value)` | Set countdown bomb |
| `setSpecial(int x, int y, int specialState, int initialCountdown)` | Set special state, init countdown utk BOMB_TILE |
| `canPlace(Piece piece, int px, int py)` | True bila piece bisa ditaruh di posisi (px, py) tanpa collision |
| `placePiece(Piece piece, int px, int py)` | Tulis FILLED + colorIdx ke cells yang piece-nya 1 |
| `setHover(Piece piece, int px, int py)` | Set HOVERED preview, lalu hitung break highlight via `updateHoveredBreaks` |
| `clearHover()` | Reset semua HOVERED/HOVERED_BREAK_FILLED/HOVERED_BREAK_EMPTY ke state aslinya |
| `breakLines()` | Detect rows + cols penuh, apply transition (FILLED→EMPTY, FROZEN→CRACKED→EMPTY, dll), return count |
| `canPlaceAny(Piece[] hand)` | True bila salah satu piece di hand bisa ditaruh di mana pun |
| `isEmpty()` | True bila tidak ada cell FILLED (untuk perfect clear detection) |
| `getValidSpots(Piece piece)` | Return matrix boolean valid placement (untuk hint indicator) |

**Logika Kunci:**
Algoritma `updateHoveredBreaks` melakukan dua pass: (1) tandai row/col penuh
mempertimbangkan HOVERED juga sebagai filled, (2) untuk semua cell di row/col
penuh tersebut, transisikan FILLED→HOVERED_BREAK_FILLED dan EMPTY/HOVERED→
HOVERED_BREAK_EMPTY agar GameView bisa highlight emas dengan warna yang
konsisten.

`applyBreakTransition` adalah switch per cell-state yang menjelaskan rule
special tile: FROZEN butuh dua line break (FROZEN→FROZEN_CRACKED→EMPTY),
LOCKED langsung empty, BOMB_TILE langsung empty + reset countdown.

---

#### `Hand.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/Hand.java`
**Tujuan:** Inventory piece pemain (3 atau 5 slot), refill saat semua null.
**Dependencies:** `Piece`, `java.util.Random`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `Hand(int size)` | Konstruktor default; pakai `Piece.getRandomPiece()` |
| `Hand(int size, Random random)` | Konstruktor seeded; pakai RNG injected (untuk daily challenge) |
| `refill()` | Isi 3 slot dengan piece baru; pakai seeded RNG bila ada |
| `get(int index)` | Return piece di index |
| `remove(int index)` | Set slot ke null |
| `getSize()` | Return jumlah slot |
| `isEmpty()` | True bila semua slot null |
| `getAll()` | Return raw array (untuk snapshot/restore) |

---

#### `Piece.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/Piece.java`
**Tujuan:** Representasi piece dengan shape matrix dan color index. Static utility
untuk generate random piece dengan distribusi tertimbang.
**Dependencies:** `AppContext`, `ThemeManager`, `ColorPalette`

#### Public Fields

- `SHAPES` — array 24 shape matrix (L, T, S/Z, square, line)
- `DISTRIBUTION` — bobot distribusi per shape (square 6, lines 4, dll)
- `matrix` — matrix aktif piece (bisa di-rotate)
- `colorIndex` — index 0–5 ke active palette
- `shapeIndex` — index ke `SHAPES`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `Piece(int shapeIndex, int colorIndex)` | Konstruktor; matrix di-set dari `SHAPES[shapeIndex]` |
| `getRows()` / `getCols()` | Dimensi matrix |
| `getBlockCount()` | Jumlah cell == 1 |
| `getColor()` / `getColorRGB()` | Resolve warna dari palette aktif |
| `getTopBorderColor()` / `getLeftBorderColor()` / `getRightBorderColor()` / `getBottomBorderColor()` | 4 warna border untuk 3D bevel effect |
| `static getColors()` | Resolve 6×3 RGB dari `ThemeManager.getActivePalette()`, fallback `ColorPalette.DEFAULT` |
| `static getColorFromIndex(int)` | Resolve warna utama dari index |
| `static getTopBorder/LeftBorder/RightBorder/BottomBorder(int)` | Static border color resolver untuk arbitrary index |
| `static getRandomPiece()` | Sample shape pakai distribusi global |
| `static getRandomPiece(Random rng)` | Variant seeded untuk deterministic mode |
| `static getRandomColorIndex()` | Random int 0–5 |

**Logika Kunci:**
Border color dihitung dengan multiplier rasional (mis. `(214/131)`) terhadap
RGB asli — angka itu berasal dari sampling pixel default piece sehingga semua
palette mendapat bevel proporsional.

Sampling distribusi: `random.nextFloat() * totalDistribution`, lalu loop kurangi
bobot. Shape 2×2 paling sering muncul (bobot 6), 3×3 dan single-line tetap rare
agar puzzle tetap challenging.

---

#### `ScoreManager.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/ScoreManager.java`
**Tujuan:** Tracking score, combo level, dan turn counter sejak combo terakhir.
**Dependencies:** —

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `ScoreManager(int boardSize)` | Konstruktor; reset score=0, combo=0 |
| `addPlacement(int blockCount)` | Tambah `blockCount` poin (multiplier 1.0) |
| `addPlacement(int blockCount, float multiplier)` | Variant multiplier-aware untuk Timed Mode |
| `processLineBreak(int linesBroken)` | Default multiplier 1.0; return new combo level |
| `processLineBreak(int linesBroken, float multiplier)` | Hitung bonus = `linesBroken * boardSize * 10 * combo`, kalikan multiplier; reset combo bila 2 turn berturut tanpa break |
| `addPerfectClearBonus()` | Tambah +500 |
| `addScore(int amount)` | Tambah arbitrary (dari power-up bomb) |
| `addBonus(int bonus)` | Tambah end-of-round bonus (Timed Mode) |
| `getScore()` / `getCombo()` | Getter |
| `reset()` | Reset semua field |
| `restoreState(int savedScore, int savedCombo)` | Restore dari snapshot/Bundle |

**Logika Kunci:**
Combo di-decay setelah 2 turn tanpa line break, bukan langsung. Ini memberi
"grace turn" buat pemain — kamu masih bisa mempertahankan combo bila placement
selanjutnya men-trigger line break.

---

#### `GameView.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/GameView.java`
**Tujuan:** Surface utama game — render board, hand, HUD, power-up slots,
overlay; handle touch state machine; orkestrasi `Board`, `Hand`,
`ScoreManager`, `EffectManager`, `PowerUpManager`, `SpecialTilesManager`,
`TimedModeController`.
**Dependencies:** Hampir semua class core lainnya, plus
`StorageManager`, `LevelManager`, `StatisticsManager`,
`AchievementManager`, `ThemeManager`, `AudioManager`.

#### Inner Interface `GameCallback`

| Signature | Deskripsi |
|-----------|-----------|
| `onGameOver(int finalScore)` | Dipanggil saat game berakhir |
| `onPauseRequested()` | Dipanggil saat user tap "Quit" dari pause overlay |
| `onLevelUp(List<Integer> newLevels, List<Reward> newRewards)` | Dipanggil saat XP award memicu level-up baru |

#### Public Methods

| Signature | Deskripsi |
|-----------|-----------|
| `GameView(Context)` | Konstruktor; panggil `init` |
| `setup(int boardSize, int handSize, String mode, GameCallback)` | Default: durasi 0, no seed |
| `setup(int boardSize, int handSize, String mode, float duration, GameCallback)` | Variant dengan duration utk Timed |
| `setup(int boardSize, int handSize, String mode, GameCallback, Random seeded)` | Variant seeded utk Daily |
| `setup(int boardSize, int handSize, String mode, float duration, GameCallback, Random seeded)` | Full variant |
| `onSizeChanged(...)` | Recalculate layout saat ukuran view berubah |
| `onTouchEvent(MotionEvent)` | Touch state machine: detect drag piece, snap-to-grid, release commit, handle button taps |
| `onBackPressed()` | True bila konsumsi back (cancel power-up targeting); else false |
| `setPaused(boolean)` | Set pause; trigger pause Timed controller juga |
| `isPaused()` / `isGameOver()` | State queries |
| `enqueueAchievement(String name)` | Tambah notifikasi achievement ke queue |
| `saveState(Bundle)` | Persist semua state ke Bundle |
| `restoreState(Bundle)` | Restore semua state |

#### Key Private Methods

| Signature | Deskripsi |
|-----------|-----------|
| `init(Context)` | Inisialisasi paint, font, density |
| `calculateLayout(int w, int h)` | Hitung grid rect, hand rects, HUD rect, power-up slot rects, hint button rect |
| `onDraw(Canvas)` | Master render loop: HUD → grid → ghost → hand → power-up slots → overlays |
| `drawHUD(Canvas, int w)` | Render score, best, combo, hint count |
| `drawGrid(Canvas)` | Render setiap cell (FILLED/HOVER/SPECIAL), apply zoom transform |
| `drawBeveledBlock(Canvas, ...)` | Render block dengan 4 border colors untuk efek 3D |
| `drawSpecialCell(Canvas, ...)` | Dispatch ke `drawFrozenBase`/`drawLockedTile`/`drawBombTile` |
| `drawFrozenBase` / `drawFrozenCracks` / `drawLockedTile` / `drawBombTile` | Special tile rendering (countdown, crack pattern, ice glow) |
| `drawGhostBlock(Canvas, ...)` | Semi-transparent piece preview saat dragging |
| `drawPlacementIndicator(Canvas, Piece, int gx, int gy, boolean valid)` | Outline merah/hijau di posisi snap |
| `drawLineBreakHighlight(Canvas)` | Pulse emas di row/col yang akan terhapus |
| `drawHintIndicator(Canvas)` | Outline berkedip di posisi hint terbaik |
| `drawHintButton(Canvas, int w)` | Tombol hint + counter |
| `checkSnap(int newGx, int newGy)` | Detect snap-to-grid, vibrate haptic |
| `drawHandPieces(Canvas, int w)` | Render 3 piece slot di bawah board |
| `calculateHandRects(int w)` | Hitung posisi+size masing-masing hand slot |
| `drawPieceAt(Canvas, Piece, ...)` | Helper render piece di posisi free |
| `buildGameContext()` | Bangun `PowerUpManager.GameContext` snapshot dari state saat ini |
| `drawPowerUpSlots(Canvas)` | Render 4 power-up slot dengan icon + count badge; gating visual |
| `drawTargetingOverlay(Canvas)` | Render overlay saat power-up dalam mode TARGETING (highlight cell, instruksi) |
| `drawCancelButton(Canvas)` | Tombol cancel untuk power-up targeting |
| `drawDraggingPiece(Canvas)` | Render piece yang sedang di-drag mengikuti finger |
| `drawPauseButton(Canvas, int w)` | Render tombol pause di pojok |
| `drawPauseOverlay(Canvas, int w, int h)` | Render full-screen pause overlay dengan tombol Resume/Quit |
| `drawGameOverOverlay(Canvas, int w, int h)` | Render full-screen game-over overlay dengan score akhir |
| `drawButton(Canvas, ...)` | Helper umum render rounded button |
| `updateHoverPreview()` | Update `Board` hover state berdasarkan posisi drag terkini |
| `attemptPlacement()` | Validate placement, capture snapshot untuk Undo, place piece, update score, trigger break, refill hand |
| `commitPieceSnap()` | Setelah animasi snap selesai, finalize placement: stat tracking, achievement check, level-up evaluation |
| `triggerGameOver()` | Set `gameOver=true`, simpan high score lokal, panggil callback |
| `recordSessionStats()` | Record game-end ke `StatisticsManager` |
| `awardXPForGame()` | Hitung XP, panggil `LevelManager.awardXP`, trigger callback `onLevelUp` |
| `collectClearedCells()` | Kumpulkan posisi cell yang baru dihapus untuk effect particle |
| `handleHintTap()` | Run `HintCalculator`, set indicator state |
| `handlePauseTouch(...)` / `handleGameOverTouch(...)` | Hit-test tombol di overlay |
| `triggerShake(float intensity)` / `updateShake()` | Screen shake utk feedback negatif (game over, special tile spawn) |
| `vibrate()` / `vibrateSnap(int ms)` | Haptic feedback wrapper |
| `enqueueAchievements(List<String>)` | Bulk enqueue notification |
| `drawAchievementNotification(Canvas, ...)` | Render slide-down notif "Achievement Unlocked: …" |

**Logika Kunci — Touch State Machine:**

`onTouchEvent` adalah state machine besar yang menangani:
1. **Tap pada power-up slot** → panggil `PowerUpManager.activate(...)`
   dengan `GameContext` saat ini. Untuk BOMB/LINE_SWEEP masuk TARGETING;
   untuk ROTATE apply ke piece yang sedang di-drag; untuk UNDO apply
   ke board+hand+score.
2. **Tap pada cell saat TARGETING** → panggil `applyAtCell(...)`
3. **Tap pada hand piece** → mulai drag
4. **Move** → update grid hover preview via `setHover(...)`
5. **Up** → bila valid, trigger `attemptPlacement` + animasi `PieceSnapAnim`,
   lalu `commitPieceSnap`

**Logika Kunci — `attemptPlacement`:**

```
1. Capture BoardSnapshot (untuk Undo)
2. board.placePiece(...)
3. addPlacement score (multiplier dari TimedModeController bila aktif)
4. board.breakLines() → linesBroken
5. processLineBreak(linesBroken, multiplier) → comboLevel
6. Bila linesBroken > 0:
     - clearSnapshot() (Undo tidak boleh undo line break)
     - effectManager.onCombo(comboLevel, linesBroken)
     - effectManager.onLineBreak(clearedCells, ...)
     - audioManager.playBreak() / playCombo(comboLevel)
     - timedController.onLineBreak() (Timed only)
     - specialTilesManager.evaluateSpawns(turnNumber++)
     - powerUpManager.grantOnCombo(comboLevel)
7. powerUpManager.grantOnScore(prevScore, newScore)
8. specialTilesManager.decrementBombsAndDetonate(callback)
9. Cek perfect clear → effectManager.onPerfectClear, addPerfectClearBonus
10. hand.refill() bila empty
11. Cek game over (no valid placement) → triggerGameOver
```

**Logika Kunci — `saveState` / `restoreState`:**

Save bundle berisi: board cells/colors (flatten 1D), hand pieces (shapeIndex,
colorIndex, matrix), score, combo, mode, hint count, dragging state (set false
on restore), `PowerUpManager` state, `SpecialTilesManager` state,
`TimedModeController` state. Total ~30 keys untuk full game state, agar
config change rotation tidak menghilangkan progress.

---

#### `HintCalculator.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/HintCalculator.java`
**Tujuan:** Pure scoring logic untuk hint button — evaluate semua valid placement,
return best.
**Dependencies:** `Board`, `Piece`

#### Inner Class `PlacementResult`

- `pieceIndex` — index di hand
- `gridX`, `gridY` — top-left posisi piece
- `score` — skor yang dihitung

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `HintCalculator(Board)` | Konstruktor; cache board size |
| `findBestPlacement(Piece[] handPieces)` | Loop semua piece × posisi valid, return best |
| `scorePlacement(Piece, int px, int py)` | Skor = `lines*1000 + adjacent*10 + edge*5` |
| `countLinesCompleted(Piece, int, int)` | Simulasi placement tanpa mutasi, hitung row+col yang bisa penuh |
| `countAdjacentFilled(Piece, int, int)` | Hitung filled cells 4-directional adjacent ke piece (exclude piece sendiri) |
| `countEdgeCells(Piece, int, int)` | Hitung cell piece yang menyentuh edge board |
| `distanceToCenter(Piece, int, int)` | Manhattan distance ×2 untuk tiebreaker (avoid float) |

**Logika Kunci:**
Tiebreaker `distanceToCenter` lebih kecil = lebih dekat pusat = preferred,
karena placement di pusat biasanya memberi opsi line break ke depannya.
Skor 1000 per line dominasi formula sehingga hint selalu prioritaskan
combo besar.

---

#### `PowerUpManager.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/PowerUpManager.java`
**Tujuan:** Inventory power-up, acquisition (combo + milestone score),
state machine activation, dan persistence.
**Dependencies:** `Board`, `Hand`, `ScoreManager`, `BoardSnapshot`,
`PowerUpType`, `Piece`

#### Inner Class `ApplyResult`

- `applied` — true bila efek ter-apply
- `cellsCleared` — total cell di area efek
- `filledCellsCleared` — cell FILLED yang dibersihkan
- `wasRow` — untuk LINE_SWEEP, true bila row dipilih
- `targetIndex` — index row/col yang dibersihkan

#### Inner Class `GameContext`

Immutable snapshot context untuk evaluasi gating:
- `paused`, `gameOver`, `dragging`, `hasSnapshot`, `lastPlacementClean`

#### Inner Enum `ActivationState`

- `IDLE` (default), `TARGETING` (BOMB/LINE_SWEEP menunggu target), `EXECUTING` (transient saat apply)

#### Constants

- `MAX_PER_TYPE = 2` — cap inventory per type
- `SCORE_MILESTONES = {1000, 5000, 10000, 25000, 50000}` — threshold acquisition

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `PowerUpManager(Random random)` | Konstruktor; RNG injected untuk testability |
| `getCount(PowerUpType type)` | Return inventory count |
| `newGameReset()` | Reset semua counter, milestones, state |
| `grantOnCombo(int comboLevel)` | Saat combo ≥4, random pilih type, increment bila <cap, return type or null |
| `grantOnScore(int prevScore, int newScore)` | Cek milestone yang baru di-cross, grant per milestone, mark earned (idempotent) |
| `activate(PowerUpType, GameContext)` | Apply gating matrix; return true bila aktivasi sukses (TARGETING atau ready immediate) |
| `cancel()` | Cancel TARGETING, balik ke IDLE |
| `updateTargetCursor(int gridX, int gridY, float fracX, float fracY)` | Update cursor; recompute row/col untuk LINE_SWEEP |
| `applyAtCell(int tx, int ty, float fracX, float fracY, Board, ScoreManager)` | Eksekusi BOMB/LINE_SWEEP; return ApplyResult |
| `applyRotate(Piece dragged)` | Build rotated matrix `R'[c][R-1-r] = M[r][c]`, return new Piece, decrement counter |
| `captureSnapshot(Board, Hand, ScoreManager)` | Deep-copy ke `BoardSnapshot` |
| `clearSnapshot()` | Buang snapshot (saat ada line break) |
| `hasSnapshot()` | True bila snapshot tersimpan |
| `applyUndo(Board, Hand, ScoreManager)` | Validasi size, restore, decrement counter, return snapshot |
| `isUndoEnabled(GameContext)` | Composite gating untuk Undo |
| `getState()` / `getActiveType()` / `isTargeting()` | State queries |
| `getTargetGridX()` / `getTargetGridY()` / `isTargetingRow()` | Targeting cursor queries |
| `saveState(Bundle)` / `restoreState(Bundle)` | Persistence; restore selalu force IDLE state |

**Logika Kunci — Gating Matrix:**

| Type        | Pra-syarat                                              |
|-------------|---------------------------------------------------------|
| BOMB        | count > 0, !paused, !dragging                           |
| LINE_SWEEP  | count > 0, !paused, !dragging                           |
| ROTATE      | count > 0, !paused, !gameOver, dragging                 |
| UNDO        | count > 0, !paused, !gameOver, hasSnapshot, lastPlacementClean |

BOMB/LINE_SWEEP boleh saat gameOver agar pemain bisa "save" game over (kalau
ledakan menciptakan ruang untuk piece di hand).

**Logika Kunci — Row vs Col untuk LINE_SWEEP:**

Rumusnya: row dipilih bila `|fracY - 0.5| < |fracX - 0.5|`. Artinya: kalau
finger lebih dekat ke vertical center cell, baris yang dipilih; kalau lebih
dekat ke horizontal center, kolom. Ini intuitif: "tunjuk sumbu yang ingin
disapu".

---

#### `PowerUpType.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/PowerUpType.java`
**Tujuan:** Enum 4 tipe power-up.

| Value        | Deskripsi                                  |
|--------------|--------------------------------------------|
| `BOMB`       | Hapus 3×3 area                             |
| `LINE_SWEEP` | Hapus 1 row atau col                       |
| `ROTATE`     | Putar piece yang sedang di-drag 90° CW     |
| `UNDO`       | Restore snapshot pre-placement             |

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `static randomFrom(Random r)` | Return uniform random PowerUpType |

---

#### `BoardSnapshot.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/BoardSnapshot.java`
**Tujuan:** Deep-copy snapshot board+hand+score untuk power-up Undo.
**Dependencies:** `Board`, `Hand`, `Piece`, `ScoreManager`, `Bundle`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `static capture(Board, Hand, ScoreManager)` | Deep-copy semua state ke immutable snapshot |
| `restoreInto(Board, Hand, ScoreManager)` | Tulis kembali semua field ke live objects |
| `writeTo(Bundle out)` | Serialize ke Bundle dengan key `pu_snap_*`, flatten 2D → 1D |
| `static readFrom(Bundle in)` | Deserialize; return null bila absent / inconsistent |
| `getBoardSize()` | Return ukuran board snapshot |

**Logika Kunci:**
Hand piece di-snapshot beserta `matrix` aktif (bukan hanya `shapeIndex`)
sehingga rotasi yang sudah di-apply via ROTATE power-up juga ikut di-undo.
Validasi size mismatch (board.size != snapshot.size) di `applyUndo` jaga
agar Undo tidak crash kalau orientation lock berubah jadi mode lain.

---

#### `SpecialTilesManager.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/SpecialTilesManager.java`
**Tujuan:** Spawn evaluation, mode gating, dan bomb decrement/detonation
untuk Frozen, Locked, Bomb tile. Single source of truth untuk tile spesial.
**Dependencies:** `Board`, `Random`, `Bundle`

#### Constants

- `BOMB_INITIAL_COUNTDOWN = 5`
- `FROZEN_MIN_TURN = 5` — frozen baru spawn setelah turn ke-5
- `FROZEN_CAP = 5`, `LOCKED_CAP = 3`, `BOMB_CAP = 2`
- `FROZEN_PROBABILITY = 0.10f`, `LOCKED_PROBABILITY = 0.05f`, `BOMB_PROBABILITY = 0.03f`

#### Inner Interface `DetonationCallback`

| Signature | Deskripsi |
|-----------|-----------|
| `onChainBombGameOver()` | Dipanggil saat bomb mendetonasi bomb lain (instant game over) |

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `SpecialTilesManager(Board, String mode, Random rng)` | Konstruktor; turnNumber=0 |
| `isModeEligible()` | True untuk "chaos" / "daily" (case-insensitive) |
| `getTurnNumber()` / `setTurnNumber(int)` | Akses turn counter |
| `evaluateSpawns(int turnNumber)` | Per turn: roll independent untuk frozen, locked, bomb |
| `decrementBombsAndDetonate(DetonationCallback cb)` | Decrement countdown semua bomb, detonate yang capai 0; chain detection → callback |
| `saveState(Bundle)` / `restoreState(Bundle)` | Persist turnNumber + bomb countdowns flatten |

**Logika Kunci — Spawn Algorithm:**

Per `trySpawn(state, cap, probability, ...)`:
1. Hitung jumlah cell dengan state target (FROZEN dihitung bersama FROZEN_CRACKED)
2. Bila count ≥ cap → skip
3. Bila turnGated dan turnNumber ≤ FROZEN_MIN_TURN → skip
4. List semua EMPTY cell
5. Roll `rng.nextFloat() < probability` → bila gagal skip
6. Pick random empty cell, set special state

Tiga panggilan independen, jadi dalam satu turn paling banyak 3 spesial baru
spawn (1 frozen + 1 locked + 1 bomb).

**Logika Kunci — Chain Detection:**

`decrementBombsAndDetonate` jalan dua-pass:
1. Snapshot list cell bomb saat ini
2. Decrement semua countdown
3. Loop snapshot list; untuk bomb yang countdown == 0:
   - Cek 8 neighbor → bila ada bomb lain → set chainHit
   - Clear semua 9 cell area
   - Bila chainHit → panggil callback dan short-circuit return

Bomb yang sudah di-clear oleh detonasi sebelumnya di-skip via cek `cells[y][x] != BOMB_TILE`.

---

#### `TimedModeController.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/TimedModeController.java`
**Tujuan:** State machine timer untuk Timed Mode round. Pure JVM (Bundle-only Android dep).
**Dependencies:** `Bundle`

#### Constants

- `TIME_BONUS_PER_LINE_BREAK_SEC = 1.5f`
- `END_BONUS_PER_SECOND = 5`
- `TIME_BONUS_FLASH_DURATION_MS = 800L`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `TimedModeController(String modeName, float initialDurationSec)` | Konstruktor; default paused |
| `getModeName()` / `getInitialDurationSec()` | Getter |
| `isPaused()` / `isExpired()` / `isActive()` | State queries |
| `remainingTime()` | `max(0, initial + bonus - elapsed)` |
| `elapsedTime()` | Return elapsed |
| `scoreMultiplier()` | Band table 1.0/1.5/2.0/2.5/3.0; timed60 cap di 2.5 |
| `isTimeBonusFlashActive(long nowMs)` | True selama 800ms setelah line break |
| `endBonus()` | `5 * floor(remainingTime)` |
| `tick(long nowMs)` | Hitung delta dari `lastTickMs`, akumulasi elapsed, set expired bila habis |
| `pause()` / `resume(long nowMs)` | State transition |
| `onLineBreak()` | Tambah `+1.5s` ke accumulatedBonus, set timeBonusFlashEndMs |
| `saveState(Bundle)` / `restoreState(Bundle)` | Persistence; selalu force paused on restore |

**Logika Kunci:**
`tick` tahan terhadap clock yang mundur (nonotonic time): bila `nowMs < lastTickMs`
delta dianggap 0 supaya elapsed tetap monotonic. Saat `remainingTime() <= 0`,
elapsed di-clamp ke `initial + bonus` sehingga `endBonus()` yang dipanggil
setelah expired tidak menghasilkan negatif.

Restore selalu force paused (Requirement 10.2) untuk mencegah skenario:
device rotate → activity recreate → timer berjalan instant tanpa user resume.


### Visual Effects

#### `EffectManager.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/effects/EffectManager.java`
**Tujuan:** Orchestrator semua visual effect — particle, screen flash, zoom pulse,
combo text, score pop, piece snap. Hitung deltaTime sendiri dengan clamp 33ms.
**Dependencies:** Semua subsistem effect, `Piece`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `init(float density)` | Inisialisasi semua subsistem dengan density |
| `onLineBreak(List<int[]> clearedCells, int blockSize, int gridLeft, int gridTop)` | Trigger particle per cell yang dibersihkan |
| `onCombo(int comboLevel, int linesBroken)` | Trigger flash (combo≥3), zoom pulse (combo≥4 atau lines≥3), combo text (combo≥3) |
| `onPerfectClear(float cx, float cy, int viewW, int viewH)` | Trigger celebration: 80–120 particle emas + teks "PERFECT CLEAR" |
| `onScoreGain(int points, float x, float y)` | Spawn `+N` score pop |
| `startPieceSnap(Piece, fromX, fromY, toX, toY, gridX, gridY)` | Mulai animasi snap |
| `update()` | Sekali per frame; hitung deltaMs (clamp 33ms), update semua subsistem |
| `applyZoomTransform(Canvas, float gridCenterX, float gridCenterY)` | Apply scale transform jika zoom pulse aktif |
| `drawParticles(Canvas)` / `drawScreenFlash(Canvas, int viewW, int viewH)` / `drawScorePops(Canvas, Typeface)` / `drawComboText(Canvas, Typeface, int viewW, int viewH)` / `drawPieceSnap(Canvas, float blockSize)` | Render masing-masing layer |
| `isActive()` | True bila ada effect berjalan |
| `isPieceSnapping()` | True bila piece sedang animate snap |
| `getPieceSnapAnim()` | Return `PieceSnapAnim` instance |

---

#### `ParticleSystem.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/effects/ParticleSystem.java`
**Tujuan:** Spawn partikel untuk line break dan celebration.
**Dependencies:** `ParticlePool`, `Particle`, `Piece`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `ParticleSystem(float density)` | Inisialisasi pool dan RNG |
| `spawnLineBreak(int cellX, int cellY, int blockSize, int gridLeft, int gridTop, int colorIndex)` | Spawn 4–8 particle dengan velocity random + bias upward, lifetime 400–700 ms |
| `spawnCelebration(float cx, float cy)` | Spawn 80–120 particle emas dari center, lifetime 500–900 ms |
| `update(float deltaMs)` | Update semua particle |
| `draw(Canvas, Paint)` | Render circle filled |
| `isActive()` | True bila ada particle aktif |

---

#### `ParticlePool.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/effects/ParticlePool.java`
**Tujuan:** Pre-allocated pool 512 particle untuk zero-allocation rendering.
**Dependencies:** `Particle`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `ParticlePool()` | Konstruktor; allocate 512 Particle inactive |
| `obtain()` | Cari Particle inactive, mark active, return; null bila pool penuh |
| `release(Particle p)` | Mark inactive |
| `updateAll(float deltaMs)` | Update semua active; auto-release expired |
| `drawAll(Canvas, Paint)` | Render semua active sebagai circle |
| `hasActive()` | True bila ada slot active |
| `releaseAll()` | Mass-release semua particle |

**Logika Kunci:**
Pool exhaustion handled gracefully via return null. Jangan throw — biar saat
combo besar (banyak line break sekaligus) ekstra particle tersilent-drop
ketimbang crash.

---

#### `Particle.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/effects/Particle.java`
**Tujuan:** Satu partikel dengan position, velocity, gravity, alpha fade, lifetime.
**Dependencies:** —

#### Public Fields

- `x`, `y`, `vx`, `vy`, `alpha`, `radius`, `color`, `lifetime`, `elapsed`, `active`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `Particle()` | Konstruktor; active=false |
| `reset(...)` | Reset ke initial state untuk reuse |
| `update(float deltaMs)` | Apply velocity, gravity 800 px/s², alpha fade linear, mark expired |
| `isExpired()` | True bila elapsed ≥ lifetime |

---

#### `ScreenFlash.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/effects/ScreenFlash.java`
**Tujuan:** Full-screen overlay flash untuk combo besar (≥3).
**Dependencies:** —

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `ScreenFlash()` | Konstruktor; active=false |
| `trigger(int comboLevel)` | Combo 3 = white 15%, combo 4+ = gold 15% + 5% per level above 3 (cap 40%) |
| `update(float deltaMs)` | Linear fade ke 0 dalam 150 ms |
| `draw(Canvas, Paint, int viewW, int viewH)` | Render rect dengan computed alpha |
| `isActive()` | State query |

---

#### `ZoomPulse.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/effects/ZoomPulse.java`
**Tujuan:** Zoom pulse effect 1.0 → 1.03 → 1.0 dalam 300 ms.
**Dependencies:** `Easing`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `ZoomPulse()` | Konstruktor; scale=1.0, inactive |
| `trigger()` | Mulai dari 1.0 |
| `triggerFrom(float currentScale)` | Mulai dari current (untuk interruption) |
| `update(float deltaMs)` | Phase 1 (0-150 ms): ease-out ke 1.03; Phase 2 (150-300 ms): ease-in kembali ke 1.0 |
| `getScale()` | Return current scale |
| `isActive()` | State query |

---

#### `ComboTextAnim.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/effects/ComboTextAnim.java`
**Tujuan:** Animated combo text dengan scale-in overshoot, hold, fade-out.
**Dependencies:** `Easing`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `ComboTextAnim()` | Konstruktor; inactive |
| `trigger(String text, int color, float fontSizePx, float holdDurationMs)` | Mulai animasi 3-phase |
| `update(float deltaMs)` | Phase 1 (0-300 ms): scale-in overshoot ke 1.2 lalu settle 1.0; Phase 2 (hold ms): hold; Phase 3 (last 300 ms): fade alpha ke 0 |
| `draw(Canvas, Paint, Typeface, int viewW, int viewH)` | Render di 30% view height, center align |
| `getScale()` / `getAlpha()` / `isActive()` | State queries |

---

#### `ScorePopManager.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/effects/ScorePopManager.java`
**Tujuan:** Pool 8 ScorePop dengan stacking dalam window 200 ms.
**Dependencies:** `ScorePop`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `ScorePopManager()` | Konstruktor; allocate 8 ScorePop |
| `spawn(String text, float x, float y, float density)` | Spawn pop dengan stack offset (25 dp × stackIndex) |
| `update(float deltaMs)` | Update semua pop |
| `draw(Canvas, Paint, Typeface, float density)` | Render dengan font size 20 dp |
| `isActive()` | True bila ada pop aktif |

**Logika Kunci:**
Stack reset terjadi bila gap > 200 ms sejak spawn terakhir. Bila pool penuh, pop
oldest di-recycle (bukan di-skip) supaya pemain selalu dapat feedback visual.

---

#### `ScorePop.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/effects/ScorePop.java`
**Tujuan:** Satu floating "+N" score text yang naik dan fade dalam 800 ms.
**Dependencies:** —

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `ScorePop()` | Konstruktor; inactive |
| `reset(String text, float x, float y, int stackIndex, float density)` | Init untuk reuse |
| `update(float deltaMs)` | Naik 60 dp, fade alpha 1→0 linear sepanjang 800 ms |
| `isExpired()` | True bila elapsed ≥ 800 ms |

---

#### `PieceSnapAnim.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/effects/PieceSnapAnim.java`
**Tujuan:** Animasi snap piece 100 ms ease-out dari posisi drop ke posisi grid akhir.
**Dependencies:** `Piece`, `Easing`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `PieceSnapAnim()` | Konstruktor; inactive |
| `start(Piece, fromX, fromY, toX, toY, gridX, gridY)` | Mulai animasi |
| `update(float deltaMs)` | Tambah elapsed; auto-stop di 100 ms |
| `getCurrentX()` / `getCurrentY()` | Interpolated position via `Easing.easeOut` |
| `isComplete()` | True saat elapsed ≥ 100 ms |
| `forceComplete()` | Force stop untuk interruption |
| `isActive()` | State query |
| `hasPendingPiece()` / `clearPiece()` / `getPiece()` / `getGridX()` / `getGridY()` | Untuk commit setelah animasi |

---

#### `Easing.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/game/effects/Easing.java`
**Tujuan:** Utility easing functions (semua menerima `t` ∈ [0, 1]).
**Dependencies:** —

| Signature | Formula |
|-----------|---------|
| `easeOut(float t)` | `1 - (1-t)^2` (decelerating) |
| `easeIn(float t)` | `t^2` (accelerating) |
| `overshoot(float t)` | `1 + (2.2t - 2.2) * t * (t - 1)` (cubic, peaks ~1.2) |
| `linear(float t)` | identity (clamped) |

---

### Audio

#### `AudioManager.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/audio/AudioManager.java`
**Tujuan:** Wrapper SoundPool singleton; load semua SFX di constructor, expose
`play*` API yang aware load completion.
**Dependencies:** `SoundPool`, `R.raw.*`, `PowerUpType`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `static getInstance(Context)` | Singleton accessor |
| `playPlace()` | SFX placement piece |
| `playBreak()` | SFX line break |
| `playGameover()` | SFX game over |
| `playJuiciness()` | SFX celebration |
| `playCombo(int comboLevel)` | SFX combo level (5 variant: combo1 sampai combo5) |
| `playPerfectClear()` | Reuse juiciness dengan pitch 1.2 |
| `playPowerUpAcquired()` | Reuse `place` dengan pitch 1.3 (notif acquisition) |
| `playPowerUp(PowerUpType type)` | Switch per type: BOMB→juiciness, LINE_SWEEP→break, ROTATE→place pitch 1.5, UNDO→place pitch 0.7 |
| `setVolume(float v)` / `getVolume()` | Volume control 0-1 |
| `release()` | Release SoundPool, clear singleton |

**Logika Kunci:**
SoundPool max 6 streams (cukup untuk overlap combo+break+place). Sound id
disimpan di `loadedSoundIds Set` via `OnLoadCompleteListener`; `play()`
selalu cek bahwa id sudah loaded supaya tidak crash di startup race.

---

### Storage

#### `StorageManager.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/storage/StorageManager.java`
**Tujuan:** Wrapper `SharedPreferences` untuk high score, volume, progression,
theme selection, total score. Persistence kompleks pakai JSON inline.
**Dependencies:** `SharedPreferences`, `JSONArray`, `JSONObject`

#### Inner Class `HighScore`

- `score` (int)
- `date` (long, timestamp)
- `mode` (String)
- `compareTo` — descending by score

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `StorageManager(Context)` | Konstruktor; `getSharedPreferences("tile_blast_prefs", MODE_PRIVATE)` |
| `saveScore(int score, String mode)` | Append ke JSONArray, sort descending, truncate ke 100 |
| `getHighScores(String mode, int limit)` | Filter by mode, sort, limit; return List<HighScore> |
| `getBestScore(String mode)` | Top-1 score untuk mode |
| `setVolume(float)` / `getVolume()` | Audio volume persistence |
| `saveProgression(int level, int xp, int prestigeCount, List<String> unlockedRewards)` | Single edit untuk semua progression field |
| `loadLevel()` | Read level (clamp 1–100) |
| `loadXP()` | Read XP (clamp ≥0) |
| `loadPrestigeCount()` | Read prestige count (clamp ≥0) |
| `loadUnlockedRewards()` | Parse JSON list reward names; corruption-resilient (return empty list on error) |
| `getActivePaletteId()` / `setActivePaletteId(String)` | Theme palette enum name |
| `getActiveSkinId()` / `setActiveSkinId(String)` | Theme skin enum name |
| `getTotalScore()` | Total cumulative score (clamp ≥0) |
| `setTotalScore(int)` | Persist total score (clamp ≥0) |
| `addToTotalScore(int delta)` | Increment helper; ignore non-positive delta |

**Logika Kunci:**
Semua getter punya default safe (`getInt(KEY, 0)`, dll) dan parser JSON
dibungkus try/catch sehingga corrupted preferences tidak crash app —
fallback ke nilai awal. `saveScore` truncate top-100 saat insert agar
storage tetap bounded.

---

### Daily Challenge Module

#### `DailyChallengeEngine.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/daily/DailyChallengeEngine.java`
**Tujuan:** Generator seed deterministic dan utility tanggal untuk daily challenge.
Pure static (no mutable state).

#### Constants

- `TARGET_SCORE = 2000`
- `BOARD_SIZE = 8`
- `HAND_SIZE = 3`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `static generateSeed()` | Seed dari `LocalDate.now()` |
| `static generateSeed(LocalDate date)` | Format `year * 10000 + month * 100 + day` |
| `static createRandom(long seed)` | Return `new Random(seed)` |
| `static todayKey()` | "YYYYMMDD" untuk `LocalDate.now()` |
| `static dateKey(LocalDate)` | "YYYYMMDD" untuk arbitrary date |

---

#### `DayEntry.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/daily/DayEntry.java`
**Tujuan:** Record satu hari di calendar view.

#### Public Fields

- `dateKey` — "YYYYMMDD"
- `score` — best score (0 = no attempt)
- `starred` — true bila star earned
- `isToday` — true bila hari ini

---

#### `StreakReward.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/daily/StreakReward.java`
**Tujuan:** Enum 3 reward streak.

| Value             | Threshold | Display Name        |
|-------------------|-----------|---------------------|
| `POWER_UP_3DAY`   | 3         | Power-up Bonus      |
| `THEME_7DAY`      | 7         | Theme Unlocked      |
| `XP_BONUS_14DAY`  | 14        | Prestige XP Bonus   |

---

#### `ChallengeStorage.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/daily/ChallengeStorage.java`
**Tujuan:** Persistence layer daily challenge — per-day score, star, streak count,
claimed rewards. Dedicated SharedPreferences file.
**Dependencies:** `SharedPreferences`, `JSONObject`, `JSONArray`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `ChallengeStorage(Context)` | Konstruktor; `getSharedPreferences("daily_challenge_prefs", MODE_PRIVATE)` |
| `recordScore(String dateKey, int score)` | Simpan max(existing, new) |
| `getScore(String dateKey)` | Return score atau 0 |
| `hasStar(String dateKey)` | Cek di JSONArray stars |
| `awardStar(String dateKey)` | Idempotent append |
| `getStreakCount()` / `setStreakCount(int)` | Streak persisted (clamp ≥0) |
| `isRewardClaimed(StreakReward)` / `claimReward(StreakReward)` | Idempotent reward tracking |
| `getCalendarEntries(LocalDate today)` | Return 30 `DayEntry` (oldest first → newest last) |

**Logika Kunci:**
Internal JSON readers (`readScores`, `readStars`, `readClaimedRewards`)
corruption-resilient — saat `JSONObject` parsing failure, log warning dan
return empty struct. Ini memastikan corrupted preferences (misalnya power-cycle
mid-write) tidak menyebabkan permanent crash.

---

#### `StreakTracker.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/daily/StreakTracker.java`
**Tujuan:** Compute & track consecutive starred-day streak; surface reward baru.
**Dependencies:** `ChallengeStorage`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `StreakTracker(ChallengeStorage)` | Konstruktor |
| `getCurrentStreak()` | Return persisted count |
| `calculateStreak()` | Recompute dari `LocalDate.now()` |
| `calculateStreak(LocalDate today)` | Testable overload |
| `checkRewards()` | Return list reward baru (auto-claim, idempotent) |

**Logika Kunci — Algoritma Streak:**

1. Anchor: hari starred terbaru (today atau yesterday). Bila keduanya tidak starred → streak = 0
2. Loop mundur dari anchor sampai 365 hari, hitung consecutive starred
3. Persist hasil

Anchor logic mengizinkan grace period: pemain yang miss "today" tapi sudah
star "kemarin" tetap pertahankan streak.

---

#### `CalendarView.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/daily/CalendarView.java`
**Tujuan:** Custom view yang render grid 6×5 untuk 30 hari terakhir, plus
streak header.
**Dependencies:** `View`, `DayEntry`, `R.font.*`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `CalendarView(Context)` / variants | Konstruktor; load font silkscreen |
| `setData(List<DayEntry>, int streakCount)` | Set data + invalidate |
| `onDraw(Canvas)` | Render header + grid |
| `drawEmptyCell(Canvas, RectF)` | Outline gray utk no-attempt |
| `drawDayCell(Canvas, RectF, DayEntry, float cellSize)` | Fill + outline + content (star/score) |
| `formatScore(int)` | Compact format ("1.5k", "10k") |

**Logika Kunci:**
Layout: 30 entries tile align bottom-right (terbaru di pojok bawah-kanan).
Bila list pendek, leading cells render kosong. Today di-highlight dengan
border putih tebal (bukan emas seperti starred biasa).


### Statistics Module

#### `StatisticsManager.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/stats/StatisticsManager.java`
**Tujuan:** Tracking statistik pemain antar sesi, persist ke SharedPreferences yang sama dengan `StorageManager`.
**Dependencies:** `SharedPreferences`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `StatisticsManager(Context)` | Konstruktor |
| `recordGameEnd(String mode, int finalScore)` | Increment per-mode + total games, accumulate score, update win streak |
| `recordCombo(int comboLevel)` | Update best combo (max) |
| `recordLinesCleared(int count)` | Tambah ke total lines |
| `recordPlayTime(long sessionSeconds)` | Tambah ke total play time |
| `getGamesPlayed(String mode)` / `getTotalGamesPlayed()` | Game count queries |
| `getAverageScore(String mode)` | Cumulative score / games count |
| `getBestCombo()` / `getTotalLinesCleared()` / `getTotalPlayTimeSeconds()` | Cumulative queries |
| `getCurrentWinStreak()` / `getBestWinStreak()` | Win streak queries |

**Logika Kunci:**
Win streak threshold = 1000 poin. `recordGameEnd` jadi titik tunggal yang
update best streak: bila skor > 1000 → increment current; bila tidak → reset
current ke 0; bila current > best → update best.

Mode key di-sanitize ke lowercase + alphanumeric only, jadi mode "Timed 60s"
dan "TIMED60" hash ke key sama (`timed60`). Mengurangi typo bug saat refactor.

---

#### `AchievementManager.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/stats/AchievementManager.java`
**Tujuan:** Evaluate kondisi unlock dan persist state. Idempotent.
**Dependencies:** `SharedPreferences`, `AchievementDef`, `StatisticsManager`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `AchievementManager(Context)` | Konstruktor |
| `evaluateScore(int finalScore, String mode)` | Cek FIRST_GAME, CENTURION, THOUSAND_CLUB, FIVE_THOUSAND, TEN_THOUSAND, SPEED_DEMON (timed only) |
| `evaluateCombo(int comboLevel)` | Cek COMBO_STARTER, COMBO_MASTER, COMBO_LEGEND |
| `evaluateCumulative(StatisticsManager)` | Cek LINE_BREAKER/DESTROYER/ANNIHILATOR, MARATHON, DEDICATED, VETERAN, STREAK_3, STREAK_7 |
| `evaluatePerfectClear()` | Cek PERFECT |
| `evaluatePrestige()` | Cek PRESTIGE |
| `isUnlocked(AchievementDef)` | Boolean state query |
| `getUnlockedCount()` | Total achievement terbuka |
| `getAllAchievements()` | Return `AchievementDef.values()` |

**Logika Kunci:**
Setiap method evaluasi return list `displayName` achievement yang baru
dibuka, supaya GameView bisa enqueue notif. `tryUnlock` bersifat idempotent
— sekali terbuka tetap terbuka. `checkCompletionist` dipanggil tiap kali
ada unlock baru: kalau semua 19 lainnya terbuka, COMPLETIONIST otomatis
dibuka.

---

#### `AchievementDef.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/stats/AchievementDef.java`
**Tujuan:** Enum 20 achievement dengan threshold dan kategori.

| Name              | Display Name         | Description                | Threshold |
|-------------------|----------------------|----------------------------|-----------|
| `FIRST_GAME`      | First Game           | Complete 1 game            | 1         |
| `CENTURION`       | Centurion            | Score 100+                 | 100       |
| `THOUSAND_CLUB`   | Thousand Club        | Score 1000+                | 1000      |
| `FIVE_THOUSAND`   | Five Thousand        | Score 5000+                | 5000      |
| `TEN_THOUSAND`    | Ten Thousand         | Score 10000+               | 10000     |
| `COMBO_STARTER`   | Combo Starter        | Combo x2                   | 2         |
| `COMBO_MASTER`    | Combo Master         | Combo x5                   | 5         |
| `COMBO_LEGEND`    | Combo Legend         | Combo x10                  | 10        |
| `LINE_BREAKER`    | Line Breaker         | Clear 10 lines             | 10        |
| `LINE_DESTROYER`  | Line Destroyer       | Clear 100 lines            | 100       |
| `LINE_ANNIHILATOR`| Line Annihilator     | Clear 1000 lines           | 1000      |
| `MARATHON`        | Marathon             | Play 10 games              | 10        |
| `DEDICATED`       | Dedicated            | Play 50 games              | 50        |
| `VETERAN`         | Veteran              | Play 100 games             | 100       |
| `SPEED_DEMON`     | Speed Demon          | Score 500+ in Timed 60s    | 500       |
| `PERFECT`         | Perfect              | Achieve a perfect clear    | 1         |
| `STREAK_3`        | Streak 3             | Win streak of 3            | 3         |
| `STREAK_7`        | Streak 7             | Win streak of 7            | 7         |
| `PRESTIGE`        | Prestige             | Prestige once              | 1         |
| `COMPLETIONIST`   | Completionist        | Unlock all 19 others       | 19        |

#### Categories

`SCORE`, `COMBO`, `LINES`, `GAMES`, `SPECIAL`

---

### Progression Module

#### `LevelManager.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/progression/LevelManager.java`
**Tujuan:** Owner XP/level/prestige/reward state pemain. Auto-persist via `StorageManager`.
**Dependencies:** `StorageManager`, `Reward`

#### Constants

- `MAX_LEVEL = 100`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `LevelManager(StorageManager)` | Konstruktor; auto-call `load()` |
| `calculateGameXP(int finalScore, int[] comboLevels, int perfectClearCount)` | Pure: `floor((floor(score/10) + sum(5*combo) + 50*perfects) * (1 + 0.1*prestige))` |
| `awardXP(int finalScore, int[] comboLevels, int perfectClearCount)` | Apply XP, handle multi-level-up, cap level 100, auto-persist |
| `getLevel()` | Current level |
| `getCurrentXP()` | XP within current level |
| `getXPForNextLevel()` | `level * 100` |
| `getProgressRatio()` | `currentXP / xpForNext` (0-1, 1.0 di max level) |
| `getNewLevelsReached()` | Drain buffer level baru sejak panggilan terakhir |
| `getPrestigeCount()` | Current prestige count |
| `getPrestigeMultiplier()` | `1.0 + 0.1 * prestigeCount` |
| `canPrestige()` | True saat level == MAX_LEVEL |
| `activatePrestige()` | Reset level=1, xp=0, increment prestigeCount; reward dipertahankan |
| `getUnlockedRewards()` | List Reward dengan `unlockLevel ≤ level` (atau persisted) |
| `isRewardUnlocked(Reward)` | Cek single reward |
| `getRewardsForLevels(List<Integer>)` | Return reward yang `unlockLevel` ada di list (untuk level-up overlay) |
| `save()` / `load()` | Persistence dengan reward list |

**Logika Kunci — Multi Level-Up:**

```java
currentXP += gained;
while (currentXP >= getXPForNextLevel() && level < MAX_LEVEL) {
    currentXP -= getXPForNextLevel();
    level++;
    newLevelsReached.add(level);
}
```

Ini menangani kasus pemain dapat skor besar yang langsung melebihi 2-3 level.
UI menampilkan level tertinggi yang dicapai sebagai headline. Reward dari
semua level yang baru dilewati ditampilkan bersamaan via `getRewardsForLevels`.

**Logika Kunci — Prestige Reward Preservation:**

`save()` selalu persist semua reward yang `level >= unlockLevel` PLUS reward
yang sudah ada di `unlockedRewards` list. Saat `activatePrestige` reset level
ke 1, list `unlockedRewards` tidak di-clear → reward tetap unlocked across
prestige. `isRewardUnlocked(r)` cek dua jalur: level current cukup, atau ada
di persisted list.

---

#### `Reward.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/progression/Reward.java`
**Tujuan:** Enum 7 reward yang bisa di-unlock berdasarkan level milestone.

| Name                  | Unlock Level | Display Name           |
|-----------------------|--------------|------------------------|
| `EXTRA_HINT`          | 5            | Extra Hint             |
| `NEON_PALETTE`        | 10           | Neon Palette           |
| `WOOD_SKIN`           | 20           | Wood Skin              |
| `EXTRA_POWERUP_SLOT`  | 30           | Extra Power-Up Slot    |
| `RETRO_PALETTE`       | 50           | Retro Palette          |
| `SPACE_SKIN`          | 75           | Space Skin             |
| `MASTER_BADGE`        | 100          | Master Title Badge     |

---

### Theme Module

#### `ThemeManager.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/theme/ThemeManager.java`
**Tujuan:** Singleton pemegang active palette, active skin, total score. Owner unlock
logic dan persistence orchestration via `StorageManager`. Thread-safe via DCL.
**Dependencies:** `StorageManager`, `ColorPalette`, `BoardSkin`

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `static getInstance(Context)` | Singleton accessor (lazy init dengan double-checked locking) |
| `getActivePalette()` / `getActiveSkin()` | Getter |
| `getTotalScore()` | Cumulative score |
| `isUnlocked(ColorPalette)` / `isUnlocked(BoardSkin)` | Threshold check vs total score |
| `setActivePalette(ColorPalette)` | Persist if unlocked; return false bila locked |
| `setActiveSkin(BoardSkin)` | Persist if unlocked; return false bila locked |
| `addToTotalScore(int delta)` | Increment total score (clamp ≥0, overflow guard) |
| `static resetForTests()` | Test hook untuk reset singleton |

**Logika Kunci — Resolve on Load:**

`resolvePalette` / `resolveSkin` dipanggil di constructor: bila id persisted
invalid (enum name unknown) atau sekarang locked (total score turun via
test/manual edit), fallback ke DEFAULT. Mencegah skenario "user pernah unlock
neon, lalu data corrupted, sekarang neon-nya stuck tapi locked".

---

#### `ColorPalette.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/theme/ColorPalette.java`
**Tujuan:** Enum 6 color palette untuk piece rendering.

| Name      | Unlock Score | Sample RGB (color 0)   |
|-----------|--------------|------------------------|
| `DEFAULT` | 0            | (227, 143, 16) orange  |
| `NEON`    | 5,000        | (255, 20, 147) magenta |
| `PASTEL`  | 10,000       | (255, 179, 186) rose   |
| `RETRO`   | 25,000       | (229, 96, 36) burnt-orange |
| `DARK`    | 50,000       | (139, 0, 0) maroon     |
| `OCEAN`   | 100,000      | (0, 119, 190) deep-blue |

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `getColors()` | Return raw 6×3 RGB matrix |
| `getColor(int idx)` | Resolve `Color.rgb(r,g,b)` |
| `static fromId(String)` | Parse enum name; fallback DEFAULT |

**Logika Kunci:**
Constructor validasi `colors.length == 6` dan setiap row `length == 3` →
throw `IllegalStateException` bila salah. Class-load time validation.

---

#### `BoardSkin.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/theme/BoardSkin.java`
**Tujuan:** Enum 5 board skin dengan `drawBackground` Canvas-only — no bitmap aset.

| Name        | Unlock Score | Deskripsi                                  |
|-------------|--------------|--------------------------------------------|
| `DEFAULT`   | 0            | Solid black fill                           |
| `WOOD`      | 5,000        | Brown vertical gradient + horizontal grain lines |
| `METAL`     | 25,000       | Gray gradient + 45° brushed-metal hatching |
| `SPACE`     | 50,000       | Deep-blue gradient + procedural star dots  |
| `PIXEL_ART` | 100,000      | 2x2 alternating dark checkerboard          |

#### Methods

| Signature | Deskripsi |
|-----------|-----------|
| `abstract drawBackground(Canvas, RectF, int blockSize, Paint)` | Setiap variant punya implementation Canvas-only |
| `static fromId(String)` | Parse enum name; fallback DEFAULT |

**Logika Kunci — SPACE Skin Stars:**

Star count dihitung proporsional area: `max(20, area / 4000f)`. Posisi star
deterministic per ukuran rect (seed = float-bit hash dari width/height) supaya
star tidak shift tiap frame. Alpha random 120-255 memberi efek twinkle yang
subtle.

---

### Leaderboard Module

#### `AuthManager.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/leaderboard/AuthManager.java`
**Tujuan:** Mengelola Firebase Authentication. Menangani *anonymous sign-in* saat aplikasi pertama kali dibuka, dan memungkinkan *linking* ke akun Google via `GoogleSignInClient`. Menyimpan auth state ke SharedPreferences.

#### `LeaderboardAdapter.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/leaderboard/LeaderboardAdapter.java`
**Tujuan:** RecyclerView adapter untuk merender list skor. Memberikan highlight warna khusus pada baris skor yang merupakan milik pemain saat ini berdasarkan `userId`.

#### `LeaderboardEntry.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/leaderboard/LeaderboardEntry.java`
**Tujuan:** Plain data class (POJO) untuk merepresentasikan satu baris leaderboard (userId, displayName, score, mode, timestamp).

#### `LeaderboardService.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/leaderboard/LeaderboardService.java`
**Tujuan:** Top-level service untuk operasi Firestore: membaca skor top-100 (all-time & mingguan), submit skor (dengan gating personal-best dan rate limiting), menghitung ranking pemain, serta registrasi real-time listener.
**Dependencies:** `FirebaseFirestore`, `AuthManager`, `ScoreQueue`

#### `ScoreQueue.java`

**Lokasi:** `app/src/main/java/com/allan/tileblast/leaderboard/ScoreQueue.java`
**Tujuan:** Menyimpan skor ke dalam queue lokal (`SharedPreferences`) jika device sedang offline atau submit ke Firestore gagal. Dilengkapi dengan `ConnectivityManager.NetworkCallback` untuk otomatis mensinkronkan antrean saat jaringan internet kembali.

---

## Build & Run

### Prerequisites

- **Android Studio Hedgehog (2023.1.1)** atau lebih baru
- **JDK 17** (Temurin atau Adoptium recommended)
- **Android SDK 34** (compileSdk + targetSdk)
- **Gradle**: project pakai wrapper, tidak perlu install manual

### Catatan untuk Build dari Repo Public

Project ini menggunakan plugin `com.google.gms.google-services` yang
membutuhkan file `app/google-services.json`. Beberapa fitur online di repo
public **tidak diikutkan**, jadi:

- Sediakan file `app/google-services.json` Anda sendiri (config minimal
  cukup), atau
- Hapus baris `id("com.google.gms.google-services")` dan dependensi terkait
  di `app/build.gradle.kts` agar Gradle tidak meminta file tersebut.

Tanpa step ini Gradle akan gagal saat task `processDebugGoogleServices`.

### Clone

```bash
git clone https://github.com/<user>/TileBlast.git
cd TileBlast
```

### Build

```bash
# Build APK debug
./gradlew assembleDebug

# Install ke device/emulator yang terhubung
./gradlew installDebug

# Build release (perlu signing config)
./gradlew assembleRelease
```

Pada Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

### Run Tests

```bash
./gradlew test          # JUnit + jqwik property tests
./gradlew connectedAndroidTest   # instrumented tests (perlu device/emulator)
```

---

## Testing

Project menggunakan kombinasi **JUnit 5** untuk example-based test dan
**[jqwik](https://jqwik.net/) 1.8.5** untuk **Property-Based Testing (PBT)**.
Lokasi: `app/src/test/java/`.

Pendekatan PBT dipakai untuk memastikan invariant penting algoritma game
selalu berlaku, bukan hanya pada contoh yang kebetulan dicoba developer.
Contoh property yang dites:

- `Board.canPlace` simetris antara hover preview dan actual placement
- `BoardSnapshot.capture` lalu `restoreInto` menghasilkan state byte-equal
- `LevelManager.calculateGameXP` selalu non-negatif untuk semua input non-negatif
- `TimedModeController.scoreMultiplier()` monotonically increasing per band
- `HintCalculator.findBestPlacement` skor selalu max dari semua valid placements
- `ScoreManager.processLineBreak` memenuhi rule combo decay setelah 2 turn

Total ~30 properties tersebar di test class per modul. Setiap property
dijalankan dengan ratusan random sample (default 1000) per run, plus
shrinking otomatis kalau ada counter-example.

---

## Persistence Architecture

### SharedPreferences Files

| File                       | Owner                | Isi                                         |
|----------------------------|----------------------|---------------------------------------------|
| `tile_blast_prefs`         | `StorageManager`, `StatisticsManager`, `AchievementManager` | High score, volume, progression, theme, total score, statistics, achievements |
| `daily_challenge_prefs`    | `ChallengeStorage`   | Per-day score, star, streak count, claimed rewards |

### Key List (file `tile_blast_prefs`)

| Key                          | Tipe   | Owner                  | Fungsi                                |
|------------------------------|--------|------------------------|---------------------------------------|
| `high_scores`                | String | StorageManager         | JSONArray top-100 high score          |
| `volume`                     | float  | StorageManager         | Audio volume 0-1                      |
| `player_level`               | int    | StorageManager         | Current level (1-100)                 |
| `player_xp`                  | int    | StorageManager         | XP within level                       |
| `prestige_count`             | int    | StorageManager         | Prestige count                        |
| `unlocked_rewards`           | String | StorageManager         | JSONArray reward enum names           |
| `active_palette`             | String | StorageManager         | Active ColorPalette enum name         |
| `active_skin`                | String | StorageManager         | Active BoardSkin enum name            |
| `total_score`                | int    | StorageManager         | Cumulative score lintas game          |
| `stats_games_<mode>`         | int    | StatisticsManager      | Game count per mode                   |
| `stats_score_<mode>`         | long   | StatisticsManager      | Cumulative score per mode             |
| `stats_games_total`          | int    | StatisticsManager      | Total games                           |
| `stats_best_combo`           | int    | StatisticsManager      | Best combo                            |
| `stats_lines_total`          | int    | StatisticsManager      | Total lines cleared                   |
| `stats_playtime`             | long   | StatisticsManager      | Total play time (seconds)             |
| `stats_streak_current`       | int    | StatisticsManager      | Win streak current                    |
| `stats_streak_best`          | int    | StatisticsManager      | Win streak best                       |
| `ach_<NAME>`                 | bool   | AchievementManager     | Unlocked flag per achievement         |

### Key List (file `daily_challenge_prefs`)

| Key                | Tipe   | Fungsi                                     |
|--------------------|--------|--------------------------------------------|
| `daily_scores`     | String | JSONObject `{dateKey: score}`              |
| `daily_stars`      | String | JSONArray dari dateKey yang starred        |
| `streak_count`     | int    | Streak count saat ini                      |
| `claimed_rewards`  | String | JSONArray `StreakReward.name()` yg claimed |

### Bundle Save/Restore Lifecycle

`GameView.saveState(Bundle)` dan `restoreState(Bundle)` membentuk chain
serialisasi seluruh state aktif game ke `Bundle` Activity sehingga rotasi
device atau low-memory recreate tidak menghilangkan progress:

```
Activity.onSaveInstanceState(outState)
  └─> GameView.saveState(outState)
        ├─ board cells/colors (flatten 1D)
        ├─ hand pieces (shapeIndex, colorIndex, matrix per slot)
        ├─ score, combo, mode, hint count
        ├─ PowerUpManager.saveState(outState)  → counts + milestones + snapshot
        ├─ SpecialTilesManager.saveState(outState)  → turnNumber + bomb countdowns
        └─ TimedModeController.saveState(outState)  → mode + bonus + elapsed

Activity.onCreate(savedInstanceState)
  └─> GameView.restoreState(savedInstanceState)
        ├─ rebuild board, hand, score
        ├─ PowerUpManager.restoreState(...)  → force IDLE state
        ├─ SpecialTilesManager.restoreState(...)
        └─ TimedModeController.restoreState(...)  → force paused
```

Beberapa rule restoration:

- **PowerUp targeting state** selalu di-force `IDLE` saat restore. Activation
  in-flight tidak boleh selamat dari rotation karena UX-nya membingungkan.
- **Timed mode** selalu di-force `paused` saat restore. Pemain harus tap
  tombol play untuk resume agar tidak ada game-time progress saat user
  belum siap.
- **Snapshot Undo** di-serialize via `BoardSnapshot.writeTo(Bundle)` —
  field `pu_snap_present` jadi flag eksistensi.

---

## Metodologi Pengembangan

Project ini dikembangkan menggunakan **pendekatan fitur bertahap** dengan
prioritas berdasarkan dependency antar komponen. Setiap fitur didesain mulai
dari requirements, diikuti technical design dan task breakdown, sebelum
implementasi dilakukan.

### Tahapan Implementasi

**Tahap 1 — Foundation:**

- Enhanced visual feedback (particle, flash, zoom pulse)
- Ghost preview & hint button
- Timed Mode (60s/90s)

**Tahap 2 — Core Mechanics:**

- Power-Ups System (4 tipe + state machine)
- Special Tiles (frozen, locked, bomb)

**Tahap 3 — Cross-Cutting Features:**

- Statistics & Achievements (20 achievement + per-mode stats)
- Level Progression & XP (100 level + prestige)
- Themes & Customization (palette + skin + unlock)

**Tahap 4 — Fitur Composite:**

- Daily Challenge (seed deterministic + streak + calendar)
- Online Leaderboard (Firebase Auth + Firestore)

**Tahap 5 — Stabilisasi:**

- Bug fixes, edge case handling, dan UI polish

### Prinsip Desain Kode

- **Zero-allocation render path** — `ParticlePool` 512 slot, tidak ada heap alloc di hot path
- **Corruption-resilient persistence** — semua JSON parser dibungkus try/catch, default safe
- **Testable state machines** — `PowerUpManager`, `TimedModeController` didesain sebagai pure state machine tanpa Android dependency
- **Property-Based Testing** — invariant algoritmik (combo decay, XP formula, hint scoring) diverifikasi dengan jqwik

---

## Roadmap / Future Work

- **Multiplayer mode** — turn-based dengan friends, asynchronous play
- **Replay system** — record sequence placement, share/playback
- **Custom theme editor** — user-created palette/skin via color picker
- **More piece shapes** — pentomino, X-shape, plus shape
- **Cloud save sync** — backup progress lintas device via Firebase Storage
- **Tutorial mode** — guided onboarding interaktif untuk first-time player
- **Adaptive difficulty** — distribusi piece menyesuaikan skill pemain
- **Push notifications** — reminder daily challenge via FCM
- **Localization** — full i18n ke Bahasa Indonesia dan English

---

## Contributing

Kontribusi welcome! Workflow standar:

1. **Fork** repo ini ke akun GitHub Anda
2. **Clone** fork lokal: `git clone https://github.com/<you>/TileBlast.git`
3. Buat **branch** dari `main`: `git checkout -b feature/<nama-fitur>` atau `bugfix/<nama-bug>`
4. Implement perubahan; ikuti style existing (4-space indent, Java naming convention)
5. Pastikan `./gradlew test` lulus
6. Commit dengan message yang deskriptif
7. **Push** ke fork: `git push -u origin feature/<nama-fitur>`
8. Buka **Pull Request** ke branch `main` di repo upstream

### Coding Style

- Java 17 syntax (text blocks, switch expressions OK kalau diperlukan)
- 4-space indentation, no tabs
- Class names UpperCamelCase, methods/fields lowerCamelCase, constants UPPER_SNAKE
- Javadoc untuk public API yang non-trivial
- Hindari alokasi heap di hot path render (lihat pattern `ParticlePool`)

---

## License

MIT License.

```
Copyright (c) 2026 Allan

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, ...
```

---

## Credits

- **Game Design, Engineering & Capstone**: Allan
- **Inspirasi gameplay**: Blockudoku, 1010!, classic Tetris-style block puzzlers
- **Font**: [Silkscreen](https://kottke.org/plus/type/silkscreen/) — pixel-style typeface
- **Sound effects**: kustom + sample royalty-free
- **Backend**: Firebase (Google) — Auth + Firestore
- **PBT Library**: [jqwik](https://jqwik.net) by Johannes Link
- **Testing framework**: JUnit 5 (Jupiter) + Mockito

---

> Dikembangkan sebagai Capstone Project — dibuat dengan ❤️ untuk pemain block puzzle yang suka angka, pattern, dan juicy effects.
