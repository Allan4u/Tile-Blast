# Requirements Document

## Introduction

Online leaderboard system for TileBlast using Firebase. Players can compete globally across game modes (classic, chaos, timed60, timed90, daily) with both all-time and weekly leaderboards. The system supports anonymous play with optional Google Sign-In linking, offline-first score submission, real-time leaderboard updates, and basic anti-cheat validation via Firestore security rules.

## Glossary

- **Leaderboard_Service**: The client-side module responsible for managing leaderboard data, score submissions, and synchronization with Firestore
- **Auth_Manager**: The module responsible for Firebase Authentication, managing Google Sign-In and Anonymous authentication flows
- **Score_Queue**: The local persistence layer that stores unsynced scores when the device is offline
- **Firestore_Rules**: Server-side security rules that validate score submissions and enforce rate limits
- **Game_Mode**: One of the supported play modes: classic, chaos, timed60, timed90, or daily
- **Weekly_Leaderboard**: A leaderboard that resets every Monday at 00:00 UTC, tracking scores submitted during the current week
- **All_Time_Leaderboard**: A leaderboard that persists indefinitely, tracking the highest scores ever submitted
- **Display_Name**: The player-visible name shown on leaderboards, derived from Google account or generated for anonymous players
- **Player_Rank**: The player's numerical position on the global all-time leaderboard for a given mode
- **StorageManager**: The existing local storage class that manages SharedPreferences for high scores and settings
- **GameActivity**: The existing activity that runs game sessions and reports final scores
- **MainActivity**: The existing main menu activity with mode selection and navigation

## Requirements

### Requirement 1: Anonymous Authentication

**User Story:** As a player, I want to play the game without creating an account, so that I can start competing on leaderboards immediately.

#### Acceptance Criteria

1. WHEN the application launches for the first time, THE Auth_Manager SHALL sign in the player anonymously using Firebase Anonymous Authentication
2. WHEN anonymous sign-in succeeds, THE Auth_Manager SHALL persist the anonymous user ID locally for future sessions
3. IF anonymous sign-in fails due to network unavailability, THEN THE Auth_Manager SHALL allow the player to continue playing in offline mode without leaderboard access
4. WHILE the player is signed in anonymously, THE Leaderboard_Service SHALL allow score submissions using the anonymous user ID

### Requirement 2: Google Sign-In

**User Story:** As a player, I want to sign in with my Google account, so that my scores are preserved across devices.

#### Acceptance Criteria

1. WHEN the player taps the Google Sign-In button, THE Auth_Manager SHALL initiate the Google Sign-In flow using Firebase Authentication
2. WHEN Google Sign-In succeeds for a previously anonymous player, THE Auth_Manager SHALL link the anonymous account to the Google account, preserving all existing scores
3. IF Google Sign-In fails, THEN THE Auth_Manager SHALL display an error message and retain the current anonymous session
4. WHEN the player is signed in with Google, THE Auth_Manager SHALL use the Google account display name as the Display_Name
5. IF account linking fails because the Google account is already linked to another anonymous account, THEN THE Auth_Manager SHALL notify the player and offer to sign in to the existing account

### Requirement 3: Display Name Generation

**User Story:** As an anonymous player, I want a unique display name on the leaderboard, so that other players can identify my scores.

#### Acceptance Criteria

1. WHEN a player signs in anonymously, THE Auth_Manager SHALL generate a Display_Name in the format "Player" followed by 4 random digits (e.g., "Player7283")
2. THE Auth_Manager SHALL persist the generated Display_Name locally so it remains consistent across sessions
3. WHEN a player links to a Google account, THE Auth_Manager SHALL replace the generated Display_Name with the Google account display name

### Requirement 4: Score Submission

**User Story:** As a player, I want my score submitted to the global leaderboard after each game, so that I can compete with other players.

#### Acceptance Criteria

1. WHEN a game ends, THE Leaderboard_Service SHALL compare the final score to the player's existing best score for that Game_Mode
2. WHEN the final score exceeds the player's existing best for that Game_Mode, THE Leaderboard_Service SHALL submit the new score to Firestore
3. WHEN the final score is equal to or lower than the player's existing best for that Game_Mode, THE Leaderboard_Service SHALL not submit the score to Firestore
4. THE Leaderboard_Service SHALL include the player's user ID, Display_Name, score value, Game_Mode, and submission timestamp with each score submission
5. WHEN a score is submitted for a week that matches the current Weekly_Leaderboard period, THE Leaderboard_Service SHALL update both the All_Time_Leaderboard and the Weekly_Leaderboard

### Requirement 5: Offline Score Queue

**User Story:** As a player, I want my scores saved when I'm offline, so that they sync to the leaderboard when I reconnect.

#### Acceptance Criteria

1. WHEN the device is offline and a score qualifies for submission, THE Score_Queue SHALL persist the score locally
2. WHEN network connectivity is restored, THE Score_Queue SHALL submit all queued scores to Firestore in chronological order
3. WHEN a queued score is successfully submitted, THE Score_Queue SHALL remove it from the local queue
4. IF a queued score submission fails due to a transient error, THEN THE Score_Queue SHALL retain the score and retry on the next connectivity event
5. THE Score_Queue SHALL persist across application restarts

### Requirement 6: Global Leaderboard Display

**User Story:** As a player, I want to view the top 100 scores for each game mode, so that I can see how I compare to other players.

#### Acceptance Criteria

1. WHEN the player opens the leaderboard screen, THE Leaderboard_Service SHALL display the top 100 scores for the selected Game_Mode
2. THE Leaderboard_Service SHALL display each entry with rank position, Display_Name, and score value
3. THE Leaderboard_Service SHALL provide tabs to switch between Game_Modes (classic, chaos, timed60, timed90, daily)
4. THE Leaderboard_Service SHALL provide a toggle to switch between All_Time_Leaderboard and Weekly_Leaderboard views
5. WHEN the player's score appears in the top 100, THE Leaderboard_Service SHALL highlight the player's entry visually

### Requirement 7: Player Rank Display

**User Story:** As a player, I want to see my global rank on the main menu, so that I know my standing without opening the leaderboard.

#### Acceptance Criteria

1. WHEN the main menu loads, THE Leaderboard_Service SHALL display the player's global rank for the most recently played Game_Mode (e.g., "You are #247")
2. IF the player has no submitted scores, THEN THE Leaderboard_Service SHALL display "Unranked" instead of a rank number
3. THE Leaderboard_Service SHALL cache the player's rank locally to display immediately while fetching updated rank data

### Requirement 8: Real-Time Leaderboard Updates

**User Story:** As a player, I want the leaderboard to update in real time, so that I can see new scores appear without refreshing.

#### Acceptance Criteria

1. WHILE the leaderboard screen is visible, THE Leaderboard_Service SHALL maintain a Firestore snapshot listener for the currently displayed Game_Mode and leaderboard type
2. WHEN a new score is added or updated in Firestore, THE Leaderboard_Service SHALL update the displayed leaderboard within 2 seconds of receiving the snapshot
3. WHEN the leaderboard screen is no longer visible, THE Leaderboard_Service SHALL detach the Firestore snapshot listener to conserve resources

### Requirement 9: Anti-Cheat Score Validation

**User Story:** As a player, I want the leaderboard to be fair, so that invalid scores are rejected.

#### Acceptance Criteria

1. THE Firestore_Rules SHALL reject any score submission with a value less than 0 or greater than 999999
2. THE Firestore_Rules SHALL reject any score submission that does not include a valid user ID matching the authenticated user
3. THE Firestore_Rules SHALL reject score submissions from the same user if the previous submission for any mode occurred less than 10 seconds ago
4. THE Firestore_Rules SHALL reject any score submission with a Game_Mode value not in the set (classic, chaos, timed60, timed90, daily)
5. IF a score submission is rejected by Firestore_Rules, THEN THE Leaderboard_Service SHALL remove the score from the Score_Queue and log the rejection reason locally

### Requirement 10: Weekly Leaderboard Reset

**User Story:** As a player, I want a weekly leaderboard that resets every Monday, so that I have recurring competition opportunities.

#### Acceptance Criteria

1. THE Weekly_Leaderboard SHALL track scores submitted from Monday 00:00 UTC to Sunday 23:59 UTC
2. WHEN a new week begins (Monday 00:00 UTC), THE Weekly_Leaderboard SHALL start empty for all Game_Modes
3. THE Leaderboard_Service SHALL determine the current week boundary using UTC timestamps on score documents
4. THE Weekly_Leaderboard SHALL maintain separate top 100 rankings per Game_Mode, independent of the All_Time_Leaderboard

### Requirement 11: Data Privacy and Deletion

**User Story:** As a player, I want to delete my leaderboard data, so that my information is removed from the system.

#### Acceptance Criteria

1. WHEN the player requests data deletion, THE Leaderboard_Service SHALL remove all of the player's scores from both All_Time_Leaderboard and Weekly_Leaderboard across all Game_Modes
2. WHEN the player requests data deletion, THE Auth_Manager SHALL delete the player's Firebase Authentication account
3. WHEN data deletion completes, THE Auth_Manager SHALL sign in the player with a new anonymous account
4. THE Leaderboard_Service SHALL store only Display_Name, score values, Game_Mode, and timestamps — no additional personal data

### Requirement 12: Leaderboard Navigation

**User Story:** As a player, I want to access the leaderboard from the main menu, so that I can check rankings at any time.

#### Acceptance Criteria

1. THE MainActivity SHALL display a leaderboard button that navigates to the leaderboard screen
2. WHEN the player taps the leaderboard button, THE MainActivity SHALL launch the leaderboard screen with the most recently viewed Game_Mode tab selected
3. THE leaderboard screen SHALL provide a back button to return to the main menu

---

## Tutorial: Firebase Setup for TileBlast

This section provides step-by-step instructions for setting up Firebase for this project.

### Step 1: Create Firebase Project

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project"
3. Enter project name: "TileBlast" (or your preferred name)
4. Disable Google Analytics (optional, not needed for leaderboard)
5. Click "Create project"

### Step 2: Add Android App

1. In the Firebase project overview, click the Android icon to add an app
2. Enter the Android package name: `com.allan.tileblast`
3. Enter app nickname: "TileBlast"
4. Enter the SHA-1 certificate fingerprint (see Step 8 for how to get this)
5. Click "Register app"

### Step 3: Download google-services.json

1. After registering the app, download the `google-services.json` file
2. Place it in the `app/` directory of the project: `TileBlast/app/google-services.json`
3. Verify the file contains the correct package name (`com.allan.tileblast`)

### Step 4: Add Firebase SDK Dependencies

Add to the project-level `build.gradle.kts`:
```kotlin
plugins {
    id("com.google.gms.google-services") version "4.4.2" apply false
}
```

Add to the app-level `app/build.gradle.kts`:
```kotlin
plugins {
    id("com.google.gms.google-services")
}

dependencies {
    // Firebase BoM (Bill of Materials) - manages all Firebase library versions
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    
    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth")
    
    // Cloud Firestore
    implementation("com.google.firebase:firebase-firestore")
    
    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.3.0")
}
```

### Step 5: Enable Authentication Providers

1. In Firebase Console, go to "Authentication" > "Sign-in method"
2. Enable "Google" provider:
   - Click "Google"
   - Toggle "Enable"
   - Set a project support email
   - Click "Save"
3. Enable "Anonymous" provider:
   - Click "Anonymous"
   - Toggle "Enable"
   - Click "Save"

### Step 6: Create Firestore Database

1. In Firebase Console, go to "Firestore Database"
2. Click "Create database"
3. Select a location closest to your primary user base (e.g., `us-central1`)
4. Start in "Production mode" (we will set up custom rules next)
5. Click "Enable"

### Step 7: Set Up Security Rules

In Firestore Database > Rules, replace the default rules with:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Leaderboard scores
    match /leaderboards/{mode}/scores/{scoreId} {
      // Anyone can read leaderboard scores
      allow read: if true;
      
      // Authenticated users can write their own scores with validation
      allow create: if request.auth != null
        && request.resource.data.userId == request.auth.uid
        && request.resource.data.score >= 0
        && request.resource.data.score <= 999999
        && request.resource.data.mode in ['classic', 'chaos', 'timed60', 'timed90', 'daily']
        && request.resource.data.keys().hasAll(['userId', 'displayName', 'score', 'mode', 'timestamp']);
      
      // Users can only update/delete their own scores
      allow update, delete: if request.auth != null
        && resource.data.userId == request.auth.uid;
    }
    
    // Player profiles (for rank caching and display names)
    match /players/{userId} {
      allow read: if true;
      allow write: if request.auth != null
        && request.auth.uid == userId;
    }
  }
}
```

**Note:** Rate limiting (1 submission per 10 seconds) requires Cloud Functions or a custom server-side check, as Firestore rules alone cannot track time between writes from the same user. An alternative approach is to use a `lastSubmission` field on the player document and validate against it in rules:

```
// Add to the score create rule:
&& (
  !exists(/databases/$(database)/documents/players/$(request.auth.uid))
  || request.time > resource.data.lastSubmission + duration.value(10, 's')
)
```

### Step 8: Configure SHA-1 for Google Sign-In

Google Sign-In requires the SHA-1 fingerprint of your signing certificate.

**For debug builds:**
```bash
cd c:\Allan\CODE\GAME\TileBlast
gradlew signingReport
```
Look for the SHA1 value under "Variant: debug".

**For release builds:**
Use your release keystore:
```bash
keytool -list -v -keystore your-release-key.keystore -alias your-alias
```

**Add SHA-1 to Firebase:**
1. In Firebase Console, go to Project Settings > Your Apps
2. Click "Add fingerprint"
3. Paste the SHA-1 value
4. Click "Save"

**Important:** You need both debug and release SHA-1 fingerprints if you test with debug builds and publish with release builds.

### Step 9: Verify Setup

After completing all steps:
1. Build the project to ensure no dependency conflicts
2. Run the app — Firebase should initialize automatically via `google-services.json`
3. Check the Firebase Console Authentication tab to see anonymous users being created
4. Check Firestore to see score documents being written after games
