# Implementation Plan: Online Leaderboard (Firebase)

## Overview

Incremental implementation of the Firebase-backed online leaderboard system for TileBlast. The plan builds from SDK setup through core services (AuthManager, ScoreQueue, LeaderboardService), then UI (LeaderboardActivity), and finally integration with existing activities. Each step builds on the previous, ensuring no orphaned code.

## Tasks

- [ ] 1. Firebase SDK setup and project configuration
  - [ ] 1.1 Add Firebase dependencies and google-services plugin
    - Add `id("com.google.gms.google-services") version "4.4.2" apply false` to project-level `build.gradle.kts`
    - Add `id("com.google.gms.google-services")` plugin to `app/build.gradle.kts`
    - Add Firebase BoM, firebase-auth, firebase-firestore, play-services-auth dependencies to `app/build.gradle.kts`
    - Add jqwik, mockito-core, and junit test dependencies
    - _Requirements: 4.4 (Firebase SDK needed for score submission)_

  - [ ] 1.2 Create google-services.json placeholder and setup documentation
    - Create a placeholder `app/google-services.json` with a comment structure indicating it must be replaced with the real file from Firebase Console
    - Add `google-services.json` to `.gitignore` if not already present
    - _Requirements: 1.1 (Firebase Auth initialization requires google-services.json)_

- [ ] 2. Implement AuthManager
  - [ ] 2.1 Create AuthManager class with singleton pattern and anonymous sign-in
    - Create `app/src/main/java/com/allan/tileblast/leaderboard/AuthManager.java`
    - Implement singleton `getInstance(Context)` method
    - Implement `signInAnonymously(AuthCallback)` using `FirebaseAuth.signInAnonymously()`
    - Implement `generateAnonymousDisplayName()` producing "Player" + 4 random digits (1000–9999)
    - Implement `persistAuthState()` to save userId and displayName via StorageManager
    - Implement `isSignedIn()`, `isAnonymous()`, `getUserId()`, `getDisplayName()` state methods
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 3.1, 3.2_

  - [ ]* 2.2 Write property test for display name format validity
    - **Property 1: Display name format validity**
    - Test that `generateAnonymousDisplayName()` always matches pattern "Player" + 4 digits (1000–9999)
    - **Validates: Requirements 3.1**

  - [ ] 2.3 Implement Google Sign-In and account linking
    - Implement `signInWithGoogle(Activity, int)` to launch Google Sign-In intent
    - Implement `handleGoogleSignInResult(Intent, AuthCallback)` to process the result
    - Implement `linkGoogleAccount(AuthCredential, AuthCallback)` using `linkWithCredential()`
    - Handle `ERROR_CREDENTIAL_ALREADY_IN_USE` by offering sign-in to existing account
    - Update display name from Google account on successful link
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.3_

  - [ ] 2.4 Implement sign-out and account deletion
    - Implement `signOut()` to sign out from Firebase Auth
    - Implement `deleteAccount(AuthCallback)` to delete the Firebase Auth account
    - After deletion, sign in with a new anonymous account
    - _Requirements: 11.2, 11.3_

- [ ] 3. Checkpoint - Ensure AuthManager compiles and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 4. Implement ScoreQueue
  - [ ] 4.1 Create ScoreQueue class with queue operations and persistence
    - Create `app/src/main/java/com/allan/tileblast/leaderboard/ScoreQueue.java`
    - Implement `enqueue(Map<String, Object>)` to add score documents to the queue
    - Implement `getQueuedScores()`, `removeFromQueue(int)`, `clearQueue()`, `getQueueSize()`
    - Implement `saveQueue()` and `loadQueue()` using JSON serialization in SharedPreferences under key `"score_queue"`
    - _Requirements: 5.1, 5.5_

  - [ ]* 4.2 Write property test for score queue persistence round-trip
    - **Property 5: Score queue persistence round-trip**
    - Test that serializing and deserializing a list of score documents produces identical results
    - **Validates: Requirements 5.5**

  - [ ]* 4.3 Write property test for queue chronological ordering
    - **Property 6: Queue chronological ordering**
    - Test that queued scores are submitted in strictly ascending timestamp order
    - **Validates: Requirements 5.2**

  - [ ] 4.4 Implement connectivity listener and sync logic
    - Implement `registerConnectivityListener()` using `ConnectivityManager.NetworkCallback`
    - Implement `unregisterConnectivityListener()`
    - Implement `syncQueuedScores(SyncCallback)` that iterates queue oldest-first, submits to Firestore, removes on success, retains on transient failure, removes on permanent failure
    - _Requirements: 5.2, 5.3, 5.4, 9.5_

- [ ] 5. Implement LeaderboardService
  - [ ] 5.1 Create LeaderboardService class with score submission logic
    - Create `app/src/main/java/com/allan/tileblast/leaderboard/LeaderboardService.java`
    - Implement singleton `getInstance(Context)` method
    - Implement `shouldSubmitScore(int score, String mode)` comparing against local best in StorageManager
    - Implement `buildScoreDocument(int score, String mode)` producing a Map with exactly: userId, displayName, score, mode, timestamp
    - Implement `submitScore(int score, String mode)` that checks threshold, validates score range (0–999999) and mode, checks rate limit (10s), then writes to Firestore or enqueues via ScoreQueue
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 9.1, 9.2, 9.3, 9.4_

  - [ ]* 5.2 Write property test for score submission threshold
    - **Property 2: Score submission threshold**
    - Test that `shouldSubmitScore()` returns true iff score > existing best
    - **Validates: Requirements 4.1, 4.2, 4.3**

  - [ ]* 5.3 Write property test for score document completeness
    - **Property 3: Score document completeness and minimality**
    - Test that `buildScoreDocument()` produces exactly the fields: userId, displayName, score, mode, timestamp
    - **Validates: Requirements 4.4, 11.4**

  - [ ]* 5.4 Write property test for score range validation
    - **Property 9: Score range validation**
    - Test that validation accepts integers in [0, 999999] and rejects all others
    - **Validates: Requirements 9.1**

  - [ ]* 5.5 Write property test for game mode validation
    - **Property 11: Game mode validation**
    - Test that validation accepts only "classic", "chaos", "timed60", "timed90", "daily" and rejects all other strings
    - **Validates: Requirements 9.4**

  - [ ] 5.6 Implement week boundary calculation and leaderboard queries
    - Implement `getWeekStartTimestamp()` returning Monday 00:00:00 UTC of current week
    - Implement `getWeekEndTimestamp()` returning Sunday 23:59:59 UTC of current week
    - Implement `getTopScores(String mode, boolean weekly, int limit, LeaderboardCallback)` querying Firestore with orderBy score DESC, limit 100, and optional timestamp filter for weekly
    - Implement `getPlayerRank(String mode, RankCallback)` counting documents with score > player's best
    - _Requirements: 6.1, 6.4, 7.1, 7.2, 7.3, 10.1, 10.2, 10.3, 10.4_

  - [ ]* 5.7 Write property test for UTC week boundary calculation
    - **Property 12: UTC week boundary calculation**
    - Test that for any UTC timestamp, week start is the most recent Monday 00:00 UTC and week end is the following Sunday 23:59:59 UTC
    - **Validates: Requirements 10.1, 10.3**

  - [ ] 5.8 Implement real-time listener and data deletion
    - Implement `attachListener(String mode, boolean weekly, LeaderboardCallback)` using `addSnapshotListener()`
    - Implement `detachListener()` calling `remove()` on the `ListenerRegistration`
    - Implement `deleteAllPlayerData(DeleteCallback)` removing all player scores across all modes and the player document
    - _Requirements: 8.1, 8.2, 8.3, 11.1_

- [ ] 6. Checkpoint - Ensure all services compile and property tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 7. Implement LeaderboardActivity UI
  - [ ] 7.1 Create LeaderboardEntry data class and LeaderboardAdapter
    - Create `app/src/main/java/com/allan/tileblast/leaderboard/LeaderboardEntry.java` with fields: userId, displayName, score, mode, timestamp, rank
    - Create `app/src/main/java/com/allan/tileblast/leaderboard/LeaderboardAdapter.java` extending RecyclerView.Adapter
    - Implement player row highlighting when `userId` matches current user
    - Create `app/src/main/res/layout/item_leaderboard_entry.xml` for row layout (rank, name, score)
    - _Requirements: 6.2, 6.5_

  - [ ] 7.2 Create LeaderboardActivity with tabs and toggle
    - Create `app/src/main/res/layout/activity_leaderboard.xml` with TabLayout (5 tabs: Classic, Chaos, Timed 60, Timed 90, Daily), ToggleButton (All-Time / Weekly), RecyclerView, and toolbar with back button
    - Create `app/src/main/java/com/allan/tileblast/LeaderboardActivity.java`
    - Implement `onCreate()` setting up tabs, toggle, and RecyclerView with LeaderboardAdapter
    - Implement `onStart()` to attach Firestore snapshot listener for current mode/toggle
    - Implement `onStop()` to detach listener
    - Implement `loadLeaderboard()` triggered by tab change or toggle change
    - Implement `highlightPlayerEntry()` to visually distinguish the current player's row
    - Register activity in `AndroidManifest.xml`
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 8.1, 8.2, 8.3, 12.3_

  - [ ]* 7.3 Write unit tests for LeaderboardAdapter
    - Test that adapter correctly binds data to ViewHolder
    - Test that player highlighting logic works for matching userId
    - _Requirements: 6.2, 6.5_

- [ ] 8. Integrate with MainActivity and GameActivity
  - [ ] 8.1 Add leaderboard button and rank display to MainActivity
    - Add leaderboard button to `activity_main.xml` layout
    - Add rank display TextView (e.g., "You are #247" or "Unranked")
    - In `MainActivity.java`, initialize AuthManager on first launch (trigger anonymous sign-in)
    - Wire leaderboard button to launch `LeaderboardActivity` with last viewed mode
    - On `onResume()`, fetch and display player rank via `LeaderboardService.getPlayerRank()`
    - Display cached rank immediately, update when fresh data arrives
    - _Requirements: 1.1, 7.1, 7.2, 7.3, 12.1, 12.2_

  - [ ] 8.2 Integrate score submission in GameActivity
    - In `GameActivity.java`, on game over call `LeaderboardService.submitScore(score, mode)`
    - Ensure ScoreQueue connectivity listener is registered on activity start and unregistered on stop
    - _Requirements: 4.1, 4.2, 4.3, 5.1, 5.2_

- [ ] 9. Firestore security rules deployment guide
  - [ ] 9.1 Create Firestore security rules file and deployment documentation
    - Create `firebase/firestore.rules` with the complete security rules from the design document
    - Create `firebase/README.md` with step-by-step deployment instructions (Firebase Console or Firebase CLI)
    - Include composite index creation instructions for `(timestamp ASC, score DESC)` per mode subcollection
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

- [ ] 10. Final checkpoint - Ensure all tests pass and project compiles
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- The `google-services.json` placeholder must be replaced with the real file from Firebase Console before running the app
- Composite Firestore indexes will be auto-prompted by Firestore on first query attempt; the deployment guide documents manual creation as well
- Rate limiting (Requirement 9.3) is enforced client-side; server-side enforcement requires Cloud Functions (out of scope for initial implementation)

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1"] },
    { "id": 2, "tasks": ["2.2", "2.3"] },
    { "id": 3, "tasks": ["2.4"] },
    { "id": 4, "tasks": ["4.1"] },
    { "id": 5, "tasks": ["4.2", "4.3", "4.4"] },
    { "id": 6, "tasks": ["5.1"] },
    { "id": 7, "tasks": ["5.2", "5.3", "5.4", "5.5", "5.6"] },
    { "id": 8, "tasks": ["5.7", "5.8"] },
    { "id": 9, "tasks": ["7.1", "9.1"] },
    { "id": 10, "tasks": ["7.2"] },
    { "id": 11, "tasks": ["7.3", "8.1", "8.2"] }
  ]
}
```
