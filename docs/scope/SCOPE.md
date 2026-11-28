# Scope: DhammaMeditate (Off-Screen Meditation Helper)

A minimalist, 100% offline mobile app that combines a local audio player with a programmable meditation timer for Burmese Dhamma (တရား) talks and preparation tracks. Users assemble reusable two-zone sessions (untimed Intro + timed Main) and meditate reliably with the screen off.

- **Project Mode:** Prototype / MVP — Solid core playback, strict timer validation, and screen-off reliability without store-release ceremony (Cap: 2–4 phases per release)
- **Build Approach:** Tracer Bullet for v0.1.0 & v0.4.0 (prove the core vertical slice end to end first, then expand); Journey for v0.2.0, v0.3.0 & v0.5.0 (finish one complete user flow deep per phase)
- **Source Idea:** `docs/scope/idea.md` (v0.1.0–v0.2.0; updated for v0.4.0 Guided Tutorials and v0.5.0 Quick Meditate) + `/scope` enrollment (v0.3.0: Burmese Language Localization)
- **Tech Stack:** Native Android (Kotlin, Gradle Kotlin DSL, `compileSdk = 35`, `minSdk = 26`), Jetpack Compose + Material 3 (flat Monastic Maroon & Saffron tokens, custom zero-dependency Compose spotlight overlay), Jetpack Media3 (`ExoPlayer` + `MediaSessionService`), Room SQLite (KSP), `SharedPreferences`, Kotlin Coroutines + `StateFlow`, manual `AppContainer` DI, JUnit 4

## Non-Goals & Deferred
Out of scope for the current roadmap so phases stay focused:
- **Native iOS App (Swift + AVFoundation):** Deferred until the Android reference app is ready; will be enrolled via `/scope` using the shared spec (`docs/spec/dhammameditate-shared-spec.md`), with `v1.0.0` planned once iOS is complete.
- **Outro Tracks & Closing Bells:** Explicitly out of scope for current pre-1.0 releases.
- **General Seeking, Main-Queue Skipping & Cross-Session Resume:** Intentionally excluded by product rules; every new session starts fresh from `00:00`, Main tracks cannot be skipped or scrubbed, and lock-screen controls omit seek/next/previous actions. *(In-app **Skip to Main** during untimed Intro is the sole exception, scoped for v0.2.0.)*
- **Cloud Streaming, User Accounts & Analytics:** App is strictly offline and local-first.
- **Configurable Fade-Out Duration:** Uses a fixed smooth exponential fade-out window; per-session or global fade tuning is deferred.
- **Video-to-Audio Extraction & DRM Audio:** Imports standard local audio files only.
- **Additional Guided Tutorials (Beyond the Initial Two):** Extra walkthroughs (e.g., playback controls, track renaming, Intro Skip to Main) are deferred; the tutorial engine is built to be extensible so they can be added later without rework.
- **Mandatory File Imports or Synthetic Data During Tutorials:** Walkthroughs run safely over live screens without forcing a real OS file import or saving fake tracks/sessions.
- **Quick Meditate Guided Tutorial & "Save as Session" Conversion:** Deferred to keep v0.5.0 focused; the existing two guided tutorials remain unchanged, and Quick Meditate stays strictly one-off without a template-save flow.

## At a Glance

| # | Phase | Status | Plan |
|---|---|---|---|
| 01 | Core Offline Audio & Timed Playback | Verified | [01-core-offline-audio-and-timed-playback.md](plans/01-core-offline-audio-and-timed-playback.md) |
| 02 | Two-Zone Session Editor & Queue Execution | Verified | [02-two-zone-session-editor-and-queue-execution.md](plans/02-two-zone-session-editor-and-queue-execution.md) |
| 03 | Distraction Check, Hardening & Shared Spec | Verified | [03-distraction-check-hardening-and-shared-spec.md](plans/03-distraction-check-hardening-and-shared-spec.md) |
| 04 | Custom Track Naming & Import Rename Flow | Verified | [04-custom-track-naming-and-import-rename-flow.md](plans/04-custom-track-naming-and-import-rename-flow.md) |
| 05 | Intro Skip to Main & v0.2.0 Release Prep | Verified | [05-intro-skip-to-main-and-v0-2-0-release-prep.md](plans/05-intro-skip-to-main-and-v0-2-0-release-prep.md) |
| 06 | Burmese Language Localization & v0.3.0 Release Prep | Verified | [06-burmese-language-localization-and-v0-3-0-release-prep.md](plans/06-burmese-language-localization-and-v0-3-0-release-prep.md) |
| 07 | Guided Tutorial Foundation & "How to Add an Audio" | Verified | [07-guided-tutorial-foundation-and-how-to-add-an-audio.md](plans/07-guided-tutorial-foundation-and-how-to-add-an-audio.md) |
| 08 | "How to Create a Session" Walkthrough, Burmese Copy & v0.4.0 Release Prep | Verified | [08-how-to-create-a-session-burmese-copy-and-v0-4-0-release-prep.md](plans/08-how-to-create-a-session-burmese-copy-and-v0-4-0-release-prep.md) |
| 09 | Quick Meditate & v0.5.0 Release Prep | Verified | [09-quick-meditate-and-v0-5-0-release-prep.md](plans/09-quick-meditate-and-v0-5-0-release-prep.md) |

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

### 06. Burmese Language Localization & v0.3.0 Release Prep · Verified
Localize all Android UI copy into English and Burmese with an in-app language switcher (`English` / `မြန်မာ`, defaulting to the device language on first launch) and Western numerals (`0-9`) for timers and durations. All Burmese translations are supplied directly by you via structured prompts—no automatic or machine translation.
**Done when:** Every user-facing string across all screens, dialogs, validation notices, and playback notifications is extracted cleanly into English and Burmese resources using only your provided Burmese translations; switching between `English` and `မြန်မာ` in-app updates the UI immediately and persists across restarts (defaulting to system locale on first launch); Western digits (`0-9`) remain used for timers and durations with clean Burmese Unicode wrapping/layout across tight controls; `docs/spec/dhammameditate-shared-spec.md` is updated to v1.2 with your canonical English ↔ Burmese glossary and language-switching rules for iOS parity; and `app/build.gradle.kts` is bumped to `versionCode = 3` and `versionName = "0.3.0"`.
Plan: [06-burmese-language-localization-and-v0-3-0-release-prep.md](plans/06-burmese-language-localization-and-v0-3-0-release-prep.md) · Code: `app/src/main/java/com/dhammameditate/`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-my/strings.xml`, `app/src/test/java/com/dhammameditate/data/preferences/AppLanguageTest.kt`, `docs/spec/dhammameditate-shared-spec.md`, `app/build.gradle.kts`
- [x] Architect plan: `/architect Burmese Language Localization & v0.3.0 Release Prep`
- [x] Build phase: `/develop Burmese Language Localization & v0.3.0 Release Prep`
  - [x] Add `AppLanguage`, `AppLanguageStore`, `UiText`, `LanguageTogglePill`, and `AppLanguageTest`
  - [x] Extract all English strings to `res/values/strings.xml`, wire dynamic Compose/ViewModel/Service localization, and mount `LanguageTogglePill`
  - [x] Collect your Burmese translations via 4 interactive chat batches (zero auto-translation) and write `res/values-my/strings.xml`
  - [x] Audit Burmese layout/wrapping in tight controls and verify Western digits (`0-9`)
  - [x] Update `docs/spec/dhammameditate-shared-spec.md` to v1.2 and bump `app/build.gradle.kts` to `versionCode = 3` / `versionName = "0.3.0"`
- [x] Verify phase: `/check verify Burmese Language Localization & v0.3.0 Release Prep`

### 07. Guided Tutorial Foundation & "How to Add an Audio" · Verified
Build the extensible guided tutorial engine, interactive multi-screen spotlight overlay (dimmed backdrop, highlighted target element, floating tooltip with `Step X / N`, tap hint, **Skip**, and final **"Got it"** confirmation), first-time-user auto-offer (skippable and persisted), small on-demand launcher button, and tutorial topic list—proven end to end with the first walkthrough: **How to add an audio**.
**Done when:** First-time users are automatically offered the guided tutorial (and can skip it without being prompted again); a small on-demand button opens the tutorial topic list anytime; selecting **How to add an audio** runs an interactive spotlight walkthrough across the live app screens up to the Add Audio control (safely ending with a **"Got it"** confirmation without requiring a real file import); and the underlying registry/step model supports adding new tutorials without rewriting the overlay engine.
Plan: [07-guided-tutorial-foundation-and-how-to-add-an-audio.md](plans/07-guided-tutorial-foundation-and-how-to-add-an-audio.md) · Code: `app/src/main/java/com/dhammameditate/ui/tutorial/`, `app/src/main/java/com/dhammameditate/data/preferences/TutorialPreferencesStore.kt`, `app/src/main/java/com/dhammameditate/MainActivity.kt`, `app/src/main/res/values/strings.xml`, `app/src/test/java/com/dhammameditate/ui/tutorial/TutorialEngineTest.kt`
- [x] Architect plan: `/architect Guided Tutorial Foundation & "How to Add an Audio"`
- [x] Build phase: `/develop Guided Tutorial Foundation & "How to Add an Audio"`
  - [x] Add pure-Kotlin `TutorialModels`, `TutorialEngine`, `TutorialRegistry` (`ADD_AUDIO`), and `TutorialEngineTest`
  - [x] Add `TutorialPreferencesStore` (`dhamma_meditate_prefs`) and wire into `AppContainer`
  - [x] Build `TutorialAnchors`, custom `TutorialSpotlightOverlay`, `TutorialDialogs`, `TutorialLauncherButton`, and English `strings.xml` entries
  - [x] Wire root overlay, welcome/topic dialogs, and `NAV_LIBRARY_TAB` anchor in `MainActivity`
  - [x] Mount `TutorialLauncherButton` above primary CTAs in `SessionsScreen` & `LibraryScreen` and attach `LIBRARY_IMPORT_BUTTON` anchor
- [x] Verify phase: `/check verify Guided Tutorial Foundation & "How to Add an Audio"`

### 08. "How to Create a Session" Walkthrough, Burmese Copy & v0.4.0 Release Prep · Verified
Add the **How to create a session** walkthrough on top of the Phase 07 tutorial foundation—handling both populated and empty Library states safely without forcing fake saves—then localize all tutorial UI and step copy into English and Burmese (`English` / `မြန်မာ`) using your supplied Burmese translations (zero auto-translation), update the shared iOS spec, and bump Android to `v0.4.0`.
**Done when:** Selecting **How to create a session** from the tutorial list guides users step by step across live screens into the Session Editor (clearly covering Intro/Main queues, timer, and saving, and behaving gracefully when the Library has 0 tracks); all tutorial menus, prompts, buttons, and step instructions switch cleanly between `English` and `မြန်မာ` using only your provided Burmese copy; `docs/spec/dhammameditate-shared-spec.md` is updated to v1.3 with the Guided Tutorials contract and Burmese glossary for iOS parity; and `app/build.gradle.kts` is bumped to `versionCode = 4` and `versionName = "0.4.0"`.
Plan: [08-how-to-create-a-session-burmese-copy-and-v0-4-0-release-prep.md](plans/08-how-to-create-a-session-burmese-copy-and-v0-4-0-release-prep.md) · Code: `app/src/main/java/com/dhammameditate/ui/tutorial/`, `app/src/main/java/com/dhammameditate/MainActivity.kt`, `app/src/main/java/com/dhammameditate/ui/sessions/SessionsScreen.kt`, `app/src/main/java/com/dhammameditate/ui/editor/SessionEditorScreen.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-my/strings.xml`, `app/src/test/java/com/dhammameditate/ui/tutorial/TutorialEngineTest.kt`, `docs/spec/dhammameditate-shared-spec.md`, `app/build.gradle.kts`
- [x] Architect plan: `/architect "How to Create a Session" Walkthrough, Burmese Copy & v0.4.0 Release Prep`
- [x] Build phase: `/develop "How to Create a Session" Walkthrough, Burmese Copy & v0.4.0 Release Prep`
  - [x] Extend `TutorialModels`, `TutorialRegistry` (`CREATE_SESSION`), English `strings.xml`, and `TutorialEngineTest`
  - [x] Upgrade `TutorialSpotlightOverlay` (`Next` vs `Got it` + empty-Library note) and wire `MainActivity`
  - [x] Attach `SessionsScreen` & `SessionEditorScreen` anchors with `BringIntoViewRequester` auto-scrolling
  - [x] Collect all 41 Burmese tutorial strings via 2 interactive batches (zero auto-translation) and write `res/values-my/strings.xml`
  - [x] Update `docs/spec/dhammameditate-shared-spec.md` to v1.3 and bump `app/build.gradle.kts` to `versionCode = 4` / `versionName = "0.4.0"`
- [x] Verify phase: `/check verify "How to Create a Session" Walkthrough, Burmese Copy & v0.4.0 Release Prep`

### 09. Quick Meditate & v0.5.0 Release Prep · Verified
Add a one-off **Quick Meditate** flow from the home page so users can select optional Intro audio, required Main audio, and a validated timer, then start meditating immediately without saving a session template. Remember the user's last Quick Meditate selections across opens with a **Clear all / Reset** action, localize all new UI copy into English and Burmese (using your supplied Burmese translations—zero auto-translation), update the shared iOS spec to v1.4, and bump Android to `v0.5.0`.
**Done when:** Tapping **Quick Meditate** on the home page opens the dedicated setup screen (no session name field, clear **Start** and **Clear all / Reset** actions); users can pick Intro/Main audio and a validated timer and launch directly into playback (including the existing distraction check and off-screen playback behavior) without creating a saved session in the Sessions list; the last Quick Meditate selections are remembered on next open (gracefully dropping any deleted library tracks) and wiped cleanly by **Clear all / Reset**; all new strings switch cleanly between `English` and `မြန်မာ` using only your provided Burmese copy; `docs/spec/dhammameditate-shared-spec.md` is updated to v1.4; and `app/build.gradle.kts` is bumped to `versionCode = 5` and `versionName = "0.5.0"`.
Plan: [09-quick-meditate-and-v0-5-0-release-prep.md](plans/09-quick-meditate-and-v0-5-0-release-prep.md) · Code: `app/src/main/java/com/dhammameditate/data/preferences/QuickMeditateStore.kt`, `app/src/main/java/com/dhammameditate/ui/quickmeditate/`, `app/src/main/java/com/dhammameditate/playback/`, `app/src/main/java/com/dhammameditate/ui/sessionplayer/`, `app/src/main/java/com/dhammameditate/ui/sessions/SessionsScreen.kt`, `app/src/main/java/com/dhammameditate/MainActivity.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-my/strings.xml`, `app/src/test/java/com/dhammameditate/data/preferences/QuickMeditateDraftTest.kt`, `docs/spec/dhammameditate-shared-spec.md`, `app/build.gradle.kts`
- [x] Architect plan: `/architect Quick Meditate & v0.5.0 Release Prep`
- [x] Build phase: `/develop Quick Meditate & v0.5.0 Release Prep`
  - [x] Add `QuickMeditateDraft`, `QuickMeditateStore` (`dhamma_meditate_prefs`), `AppContainer` wiring, and `QuickMeditateDraftTest`
  - [x] Add ephemeral `ACTION_START_QUICK_MEDITATE` playback in `PlaybackClient` & `MeditationPlaybackService` with localized title refresh
  - [x] Reuse `SessionEditorScreen` cards (`internal`) to build `QuickMeditateViewModel` & `QuickMeditateScreen` (top-bar Reset + immediate Distraction Check → Start)
  - [x] Wire `QuickMeditatePlayer` in `SessionPlayerViewModel`/`SessionPlayerScreen`, 50/50 home CTA row in `SessionsScreen`, and `MainActivity` routing
  - [x] Collect Burmese translations (zero auto-translation), update `docs/spec/dhammameditate-shared-spec.md` to v1.4, and bump `app/build.gradle.kts` to `versionCode = 5` / `versionName = "0.5.0"`
- [x] Verify phase: `/check verify Quick Meditate & v0.5.0 Release Prep`
