# Scope: DhammaMeditate (Off-Screen Meditation Helper)

A minimalist, 100% offline mobile app that combines a local audio player with a programmable meditation timer for Burmese Dhamma (တရား) talks and preparation tracks. Users assemble reusable two-zone sessions (untimed Intro + timed Main) and meditate reliably with the screen off.

- **Project Mode:** Prototype / MVP — Solid core playback, strict timer validation, and screen-off reliability without store-release ceremony (Cap: 2–4 phases per release)
- **Build Approach:** Journey for v0.2.0 (Finish one complete user flow deep per phase on top of the v0.1.0 Tracer Bullet core)
- **Source Idea:** `docs/scope/idea.md` (v0.2.0: Track Renaming & Skip to Main)
- **Tech Stack:** Native Android (Kotlin, Gradle Kotlin DSL, `compileSdk = 35`, `minSdk = 26`), Jetpack Compose + Material 3 (flat Monastic Maroon & Saffron tokens), Jetpack Media3 (`ExoPlayer` + `MediaSessionService`), Room SQLite (KSP), Kotlin Coroutines + `StateFlow`, manual `AppContainer` DI, JUnit 4

## Non-Goals & Deferred
Out of scope for the current roadmap so phases stay focused:
- **Native iOS App (Swift + AVFoundation):** Deferred until the Android reference app is ready; will be enrolled via `/scope` using the shared spec (`docs/spec/dhammameditate-shared-spec.md`), with `v1.0.0` planned once iOS is complete.
- **Outro Tracks & Closing Bells:** Explicitly out of scope for current pre-1.0 releases.
- **General Seeking, Main-Queue Skipping & Cross-Session Resume:** Intentionally excluded by product rules; every new session starts fresh from `00:00`, Main tracks cannot be skipped or scrubbed, and lock-screen controls omit seek/next/previous actions. *(In-app **Skip to Main** during untimed Intro is the sole exception, scoped for v0.2.0.)*
- **Cloud Streaming, User Accounts & Analytics:** App is strictly offline and local-first.
- **Configurable Fade-Out Duration:** Uses a fixed smooth exponential fade-out window; per-session or global fade tuning is deferred.
- **Video-to-Audio Extraction & DRM Audio:** Imports standard local audio files only.
- **Bilingual UI Localization:** Uses a clean English interface that renders Burmese Unicode cleanly in track titles and session names; translated UI strings and language switching are deferred.

## At a Glance

| # | Phase | Status | Plan |
|---|---|---|---|
| 01 | Core Offline Audio & Timed Playback | Verified | [01-core-offline-audio-and-timed-playback.md](plans/01-core-offline-audio-and-timed-playback.md) |
| 02 | Two-Zone Session Editor & Queue Execution | Verified | [02-two-zone-session-editor-and-queue-execution.md](plans/02-two-zone-session-editor-and-queue-execution.md) |
| 03 | Distraction Check, Hardening & Shared Spec | Verified | [03-distraction-check-hardening-and-shared-spec.md](plans/03-distraction-check-hardening-and-shared-spec.md) |
| 04 | Custom Track Naming & Import Rename Flow | Verified | [04-custom-track-naming-and-import-rename-flow.md](plans/04-custom-track-naming-and-import-rename-flow.md) |
| 05 | Intro Skip to Main & v0.2.0 Release Prep | Verified | [05-intro-skip-to-main-and-v0-2-0-release-prep.md](plans/05-intro-skip-to-main-and-v0-2-0-release-prep.md) |

---

## Phases

### 01. Core Offline Audio & Timed Playback · Verified
Scaffold the native Android project and prove the core vertical thread end to end: import common local audio formats (MP3, M4A/AAC, WAV, FLAC, OGG) into app-private storage so OS permissions never expire, display track title and duration, and run a timed playback session reliably with the screen off.
**Done when:** You can import supported local audio files (with clear handling for unreadable files and persistence across app restarts), set a duration, start playback, turn off the screen, and get timestamp-accurate playback with restricted Start / Pause-Continue / Stop (Reset) controls (both in-app and on the lock screen) ending in a smooth exponential fade-out to zero.
Plan: [01-core-offline-audio-and-timed-playback.md](plans/01-core-offline-audio-and-timed-playback.md) · Code: `app/src/main/java/com/dhammameditate/`, `app/src/test/java/com/dhammameditate/`
- [x] Architect plan: `/architect Core Offline Audio & Timed Playback`
- [x] Build phase: `/develop Core Offline Audio & Timed Playback`
  - [x] Scaffold Android Gradle project, `AppContainer`, and flat Monastic Maroon & Saffron Compose theme
  - [x] Implement Room `tracks` persistence and atomic app-private `AudioFileImporter` with format/duration validation
  - [x] Build pure-Kotlin `MeditationTimerEngine`, exponential `FadeCurve`, and timer capping helpers with unit tests
  - [x] Implement foreground Media3 `MeditationPlaybackService` with restricted lock-screen controls and off-screen fade loop
  - [x] Wire `LibraryScreen`, `PlayerScreen`, and `MainActivity` end to end
- [x] Verify phase: `/check verify Core Offline Audio & Timed Playback`

### 02. Two-Zone Session Editor & Queue Execution · Verified
Build reusable meditation sessions with an optional untimed Intro Queue (0+ ordered tracks) and required timed Main Queue (1+ ordered tracks), strict timer validation, library/session CRUD, and seamless Intro-to-Main queue playback.
**Done when:** You can create, edit, reorder, and delete sessions (and safely delete library tracks referenced by sessions); setting a timer above the combined Main Queue duration—or removing a track below the timer—snaps the timer down with an inline notice and blocks invalid saves; and playback completes all Intro tracks with the countdown frozen before starting the Main Queue and timer simultaneously across multiple tracks.
Plan: [02-two-zone-session-editor-and-queue-execution.md](plans/02-two-zone-session-editor-and-queue-execution.md) · Code: `app/src/main/java/com/dhammameditate/`, `app/src/test/java/com/dhammameditate/`
- [x] Architect plan: `/architect Two-Zone Session Editor & Queue Execution`
- [x] Build phase: `/develop Two-Zone Session Editor & Queue Execution`
  - [x] Add Room v2 `sessions`/`session_tracks` schema (`MIGRATION_1_2`) and `SessionRepository` with safe cascade-remove/clamp track deletion
  - [x] Extend `MeditationTimerEngine` (Intro frozen states + one-shot `onIntroComplete`) and `TimerValidation` with unit tests
  - [x] Upgrade `MeditationPlaybackService` & `PlaybackClient` for seamless Intro-to-Main multi-track playlists, `STATE_ENDED` safety, and active-session/track stop guards
  - [x] Build two-zone `SessionEditorScreen`, `SessionsScreen`, `SessionPlayerScreen`, and shared `ActiveSessionBanner`
  - [x] Wire bottom-nav `MainActivity` routing (`Sessions` | `Library`) and affected-session warnings in `LibraryScreen`
- [x] Verify phase: `/check verify Two-Zone Session Editor & Queue Execution`

### 03. Distraction Check, Hardening & Shared Spec · Verified
Add the pre-session silence/Airplane Mode check, finalize the clean flat minimal UI (no gradients, no emojis, verified Burmese Unicode rendering, and screen-off interruption edge cases), and produce the shared source-of-truth behavior specification for the future iOS port.
**Done when:** Starting a session while unsilenced or outside Airplane Mode shows a flat prompt recommending Airplane Mode/silencing with a Settings shortcut and "Continue anyway"; long screen-off runs, pause/continue cycles, and audio interruptions behave reliably with fresh `00:00` resets; and a shared spec documents the exact data model, timer validation, playback state machine, fade curve, supported audio formats, and distraction check contract for iOS parity.
Plan: [03-distraction-check-hardening-and-shared-spec.md](plans/03-distraction-check-hardening-and-shared-spec.md) · Code: `app/src/main/java/com/dhammameditate/`, `app/src/test/java/com/dhammameditate/`, `docs/spec/dhammameditate-shared-spec.md`
- [x] Architect plan: `/architect Distraction Check, Hardening & Shared Spec`
- [x] Build phase: `/develop Distraction Check, Hardening & Shared Spec`
  - [x] Implement pure-Kotlin `DistractionPolicy`, zero-permission `AndroidDistractionChecker`, and `DistractionPolicyTest`
  - [x] Build flat `DistractionCheckDialog` with live `ON_RESUME` re-check and wire into `SessionPlayerScreen` & `PlayerScreen`
  - [x] Harden `MeditationPlaybackService` interruption/boundary edge cases and expand timer & fade unit tests
  - [x] Audit and enforce flat Material 3 UI (`tonalElevation = 0.dp`, zero gradients/emojis) and Burmese Unicode line-height safety
  - [x] Author normative cross-platform specification at `docs/spec/dhammameditate-shared-spec.md` for iOS parity
- [x] Verify phase: `/check verify Distraction Check, Hardening & Shared Spec`

### 04. Custom Track Naming & Import Rename Flow · Verified
Let users set custom display names for newly imported tracks (single-file rename, or a batch prompt with Skip all and step-through renaming for multi-file imports) and rename existing tracks later from the Library, showing the custom display name everywhere tracks appear in the app.
**Done when:** Importing a single track prompts to customize its display name; importing multiple tracks asks whether to rename them (with a Skip all option or stepping through each track in order); existing tracks can be renamed from the Library with blank-name validation; and the custom display name updates immediately across Library, Session Editor, Player/Session Player, active session banner, and lock-screen metadata.
Plan: [04-custom-track-naming-and-import-rename-flow.md](plans/04-custom-track-naming-and-import-rename-flow.md) · Code: `app/src/main/java/com/dhammameditate/`, `app/src/test/java/com/dhammameditate/`
- [x] Architect plan: `/architect Custom Track Naming & Import Rename Flow`
- [x] Build phase: `/develop Custom Track Naming & Import Rename Flow`
  - [x] Add pure-Kotlin `TrackNaming` validation + import-rename state machine and `TrackNamingTest` unit tests
  - [x] Add reactive `observeById` and `updateTitle` / `renameTrack` in `TrackDao` and `TrackRepository`
  - [x] Implement live track-title refresh (`ACTION_REFRESH_TRACK_TITLE`) in `MeditationPlaybackService` and `PlaybackClient`
  - [x] Wire live track-title observation into `PlayerViewModel` and `SessionEditorViewModel`
  - [x] Build existing-track rename and post-import single/batch/step-through rename dialogs in `LibraryViewModel` & `LibraryScreen`
- [x] Verify phase: `/check verify Custom Track Naming & Import Rename Flow`

### 05. Intro Skip to Main & v0.2.0 Release Prep · Verified
Add an in-app-only Skip to Main control during untimed Intro playback that immediately jumps to the Main queue and starts the meditation timer, then update the shared iOS spec and bump the Android release version to `v0.2.0` (`versionCode = 2`).
**Done when:** During session playback, Skip to Main is visible only while an Intro track is active (hidden during Main and omitted from lock-screen controls); tapping it skips all remaining Intro tracks and starts Main track 1 and the meditation countdown immediately (working cleanly from both playing and paused Intro states); unit tests cover the skip transition; `docs/spec/dhammameditate-shared-spec.md` reflects track renaming and Intro Skip to Main; and `app/build.gradle.kts` is bumped to `versionCode = 2` and `versionName = "0.2.0"`.
Plan: [05-intro-skip-to-main-and-v0-2-0-release-prep.md](plans/05-intro-skip-to-main-and-v0-2-0-release-prep.md) · Code: `app/src/main/java/com/dhammameditate/`, `app/src/test/java/com/dhammameditate/`, `docs/spec/dhammameditate-shared-spec.md`, `app/build.gradle.kts`
- [x] Architect plan: `/architect Intro Skip to Main & v0.2.0 Release Prep`
- [x] Build phase: `/develop Intro Skip to Main & v0.2.0 Release Prep`
  - [x] Add `MeditationTimerEngine.skipIntroToMain` and unit tests in `MeditationTimerEngineTest`
  - [x] Wire in-app `ACTION_SKIP_TO_MAIN` in `MeditationPlaybackService` and `PlaybackClient` (keeping lock-screen controls restricted)
  - [x] Add `canSkipToMain` / `skipToMain()` in `SessionPlayerViewModel` and 50/50 secondary button row in `SessionPlayerScreen`
  - [x] Update `docs/spec/dhammameditate-shared-spec.md` to v1.1 (Android v0.2.0 parity)
  - [x] Bump `app/build.gradle.kts` to `versionCode = 2` and `versionName = "0.2.0"`
- [x] Verify phase: `/check verify Intro Skip to Main & v0.2.0 Release Prep`
