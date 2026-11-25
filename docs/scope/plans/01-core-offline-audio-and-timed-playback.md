# 01. Core Offline Audio & Timed Playback

- **Date:** 2026-11-25
- **Phase:** 01 — Core Offline Audio & Timed Playback
- **Project Mode:** Prototype / MVP
- **Build Approach:** Tracer Bullet
- **Status:** Verified
- **Stack & Libraries:** Native Android (Kotlin, Gradle Kotlin DSL + Version Catalog, `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`), Jetpack Compose + Material 3 (flat Monastic Maroon & Saffron tokens), Jetpack Media3 (`media3-exoplayer`, `media3-session`, `media3-common`), Room SQLite + KSP (`room-runtime`, `room-ktx`, `room-compiler`), Kotlin Coroutines + `StateFlow`, manual `AppContainer` DI, JUnit 4 + `kotlinx-coroutines-test`

## Summary
Scaffolds the native Android app and proves the complete offline meditation tracer bullet end to end: importing local audio files (MP3, M4A/AAC, WAV, FLAC, OGG) into app-private storage with Room persistence, displaying track titles (including Burmese Unicode) and durations in a flat Monastic Maroon & Saffron UI, and running timestamp-accurate off-screen timed playback via a foreground Media3 `MediaSessionService`. Controls are strictly restricted to Start / Pause-Continue / Stop (Reset) both in-app and on the lock screen (always starting fresh from `00:00`), ending in a smooth fixed 10-second exponential fade-out to zero. Choosing Room + a decoupled pure-Kotlin timer engine now keeps Phase 01 lean while giving Phase 02's two-zone sessions and queue playback a direct extension path with zero storage or playback rewrites.

## Rationale & Trade-Offs
- **Chosen direction:** Jetpack Compose (Material 3) + Room SQLite + Jetpack Media3 `MediaSessionService` with a `ForwardingPlayer` command filter and pure-Kotlin `MeditationTimerEngine` anchored to `SystemClock.elapsedRealtime()`. Matches Android's modern background media lifecycle (`FOREGROUND_SERVICE_MEDIA_PLAYBACK` + `WAKE_MODE_LOCAL`), enforces lock-screen control restrictions reliably, and extends directly to Phase 02's relational Session/Queue schema.
- **Runner-up considered:** Local JSON file (`kotlinx.serialization`) for persistence and Hilt for DI. JSON was skipped because Phase 02 introduces cross-referenced sessions and safe track deletion; Hilt was skipped to avoid extra plugin/kapt/codegen ceremony in a 3-phase `Prototype / MVP`.
- **Trade-off accepted:** Phase 01 runs single-track timed playback and single-activity Compose state navigation (no multi-track queue UI, no pre-session distraction check, no per-session fade tuning). `TrackEntity` and `MeditationTimerEngine` are cleanly structured so Phase 02 layers Intro/Main queues on top without replacing them.

## Requirements & Acceptance Criteria
- **AC-1 (Supported Offline Audio Import & Storage):** Users can pick one or more local audio files (MP3, M4A/AAC, WAV, FLAC, OGG) via Android's system document picker; each valid file is copied into app-private storage (`filesDir/audio/<uuid>.<ext>`), probed for audio stream validity, title, and duration (`> 0 ms`), saved in Room, and displayed immediately in the Library with clean Burmese Unicode and English rendering.
- **AC-2 (Unreadable File Handling & Persistence Across Restarts):** Corrupt, DRM-protected, non-audio, or zero-duration files are rejected cleanly with a clear inline error banner and leave zero orphaned files in `filesDir/audio/` or rows in Room. Valid imported tracks remain listed and playable after force-stopping and relaunching the app (even if the original external file is moved or deleted, since playback reads the app-private copy).
- **AC-3 (Timer Selection & Strict Track-Duration Cap):** On the Timed Player screen, users can choose a duration via quick minute presets (`10m`, `15m`, `30m`, `45m`, `60m`, `Full`) or a custom minutes input. Presets exceeding the selected track's duration are disabled; custom entries exceeding track duration snap down to `track.durationMs` with an inline `#D93025` notice. Timer inputs lock while a session is active (`PLAYING`, `PAUSED`, or `FADING`).
- **AC-4 (Restricted Controls In-App & Lock Screen, Fresh `00:00` Start):** In-app and lock-screen/notification media controls expose only Start / Pause-Continue / Stop (Reset). Seek bars, scrub actions, fast-forward/rewind, and next/previous track commands are omitted from the UI and stripped from the Media3 `MediaSession` player commands (including headset seek events). Starting after a Stop or Completion always restarts from `00:00` at full `1.0f` volume.
- **AC-5 (Screen-Off Timestamp Accuracy & Pause/Continue Sync):** Turning the screen off during playback continues audio uninterrupted under a foreground media service (`WAKE_MODE_LOCAL`). Elapsed/remaining time is computed from monotonic `SystemClock.elapsedRealtime()` (never naive `delay` counters), and pausing/continuing—whether from the app, lock screen, or audio focus loss/unplugged headphones—freezes and resumes the countdown accurately without drift.
- **AC-6 (Smooth Fixed 10-Second Exponential Fade-Out):** During the final `min(10_000L, targetDurationMs)` milliseconds of the timer, playback volume attenuates smoothly along the exponential decay curve at 50ms intervals to exact `0.0f` at `00:00`, pauses/resets the player cleanly, and transitions to `COMPLETED` (with audio-end safety handling if the file ends milliseconds early).
- **AC-7 (Track Deletion & Clean Flat Theme):** Users can delete an imported track from the Library (removing both its Room row and its `filesDir/audio/` file, stopping playback first if that track is currently active). All UI surfaces strictly follow the flat Monastic Maroon (`#5E2418`) & Saffron (`#F06A1A`) palette with `1dp` `#D4D4D8` card borders, zero gradients, zero emojis, and generous Burmese-safe line height (`1.4x–1.5x`).

## Technical Design

### Files & Module Layout
- `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `app/build.gradle.kts` — Android project scaffold, Kotlin/Compose/KSP/Media3/Room dependencies (`compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`).
- `app/src/main/AndroidManifest.xml` — Permissions (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`), `DhammaMeditateApp`, `MainActivity`, and `MeditationPlaybackService` (`foregroundServiceType="mediaPlayback"` + `MediaSessionService` intent filter).
- `app/src/main/java/com/dhammameditate/DhammaMeditateApp.kt` — `Application` subclass initializing `AppContainer`.
- `app/src/main/java/com/dhammameditate/di/AppContainer.kt` — Manual DI container wiring `AppDatabase`, `TrackRepository`, `AudioFileImporter`, and `PlaybackClient`.
- `app/src/main/java/com/dhammameditate/data/local/TrackEntity.kt` — Room entity for `tracks` table.
- `app/src/main/java/com/dhammameditate/data/local/TrackDao.kt` — Room DAO (`observeAll()`, `getById()`, `insert()`, `deleteById()`).
- `app/src/main/java/com/dhammameditate/data/local/AppDatabase.kt` — Room database (`version = 1`, `entities = [TrackEntity::class]`).
- `app/src/main/java/com/dhammameditate/data/repository/TrackRepository.kt` — Coordinates Room metadata and app-private `filesDir/audio/` file resolution & deletion.
- `app/src/main/java/com/dhammameditate/data/import/AudioFileImporter.kt` — SAF stream copy to temp file → `MediaExtractor` audio-track validation + `MediaMetadataRetriever` duration/title probe → atomic move into `filesDir/audio/<uuid>.<ext>` + structured `ImportResult` (`Success` / `Failure`).
- `app/src/main/java/com/dhammameditate/playback/FadeCurve.kt` — Pure exponential fade-out math (`FADE_DURATION_MS = 10_000L`, `DECAY_K = 3.5`).
- `app/src/main/java/com/dhammameditate/playback/MeditationTimerEngine.kt` — Pure clock-injected (`() -> Long`) state machine (`IDLE | PLAYING | PAUSED | FADING | COMPLETED`) computing monotonic elapsed/remaining ms and fade gain.
- `app/src/main/java/com/dhammameditate/playback/TimerValidation.kt` — Pure helpers for duration formatting (`MM:SS` / `H:MM:SS`), preset eligibility, default duration, and track-duration capping.
- `app/src/main/java/com/dhammameditate/playback/PlaybackState.kt` — Immutable `PlaybackSnapshot` (`status`, `activeTrackId`, `activeTrackTitle`, `targetDurationMs`, `remainingMs`, `elapsedMs`, `volume`, `errorMessage`) shared via an observable holder/repository between service and UI.
- `app/src/main/java/com/dhammameditate/playback/RestrictedMeditationPlayer.kt` — Media3 `ForwardingPlayer` removing all seek/skip/next/previous commands and turning seek methods into no-ops.
- `app/src/main/java/com/dhammameditate/playback/MeditationPlaybackService.kt` — Foreground `MediaSessionService` owning `ExoPlayer`, `RestrictedMeditationPlayer`, `MediaSession` (with Play/Pause + custom `ACTION_STOP_RESET` command button), `MeditationTimerEngine`, tick coroutine (`250ms` normal / `50ms` fading), and audio focus / becoming-noisy pause sync.
- `app/src/main/java/com/dhammameditate/playback/PlaybackClient.kt` — UI-facing controller sending Start / Pause / Continue / Stop-Reset intents/commands to `MeditationPlaybackService` and exposing `StateFlow<PlaybackSnapshot>`.
- `app/src/main/java/com/dhammameditate/ui/theme/Color.kt`, `Type.kt`, `Theme.kt` — Flat Material 3 color tokens (Monastic Maroon `#5E2418`, Saffron `#F06A1A`, Deep Saffron `#E05614`, Lotus Gold `#C98A1E`, Light Neutral `#F3F3F4`, Card Surface `#F8F8F9`, Outline `#D4D4D8`, Ink `#1F1F1F`, Secondary `#6E6E73`, Alert `#D93025`) and Burmese-safe typography (`1.4x–1.5x` line heights, tabular numerals for countdown).
- `app/src/main/java/com/dhammameditate/ui/library/LibraryViewModel.kt`, `LibraryScreen.kt` — Audio library list, SAF import trigger, inline error banner, active-session return banner, empty state, and track delete confirmation.
- `app/src/main/java/com/dhammameditate/ui/player/PlayerViewModel.kt`, `PlayerScreen.kt` — Monastic Maroon player screen, countdown display, non-interactive flat progress bar, timer presets + capped custom minutes input, inline snap notice, and restricted transport controls.
- `app/src/main/java/com/dhammameditate/MainActivity.kt` — Edge-to-edge Compose host, two-screen navigation (`Library` ↔ `Player(trackId)`), and Android 13+ `POST_NOTIFICATIONS` permission request prior to starting foreground playback.
- `app/src/test/java/com/dhammameditate/playback/` — Unit tests: `FadeCurveTest.kt`, `MeditationTimerEngineTest.kt`, `TimerValidationTest.kt`.

### Data Model / State Shape

**Room Entity: `tracks` (`TrackEntity`)**
| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `TEXT` (`String`) | `PRIMARY KEY` | UUID v4 string; stable FK target for Phase 02 `SessionTrackEntity` |
| `title` | `TEXT` (`String`) | `NOT NULL` | Embedded title tag or sanitized display filename; UTF-8 (Burmese-safe) |
| `local_file_name` | `TEXT` (`String`) | `NOT NULL, UNIQUE` | Relative filename inside `filesDir/audio/` (e.g., `<uuid>.mp3`) |
| `mime_type` | `TEXT` (`String`) | `NOT NULL` | Normalized MIME or probed audio MIME (e.g., `audio/mpeg`) |
| `duration_ms` | `INTEGER` (`Long`) | `NOT NULL` (`> 0`) | Probed duration in milliseconds at import time |
| `created_at_epoch_ms` | `INTEGER` (`Long`) | `NOT NULL` | `System.currentTimeMillis()` for newest-first or oldest-first ordering |

**Playback State Machine (`PlaybackStatus` in `MeditationTimerEngine`)**
- `IDLE`: Ready to start; `elapsedMs = 0`, `remainingMs = targetDurationMs`, `volume = 1.0f`.
- `PLAYING`: Active audio & monotonic countdown (`remainingMs > effectiveFadeMs`, `volume = 1.0f`).
- `PAUSED`: Audio paused; `accumulatedElapsedMs` frozen; `segmentStartedAtMs = null`. Resuming returns to `FADING` if `remainingMs <= effectiveFadeMs`, else `PLAYING`.
- `FADING`: Active audio within final `effectiveFadeMs`; `volume` continuously updated via `FadeCurve.gain(...)` every `50ms`.
- `COMPLETED`: Timer reached `00:00` (or audio ended at/near timer end); player paused at `0L` with `volume = 0.0f` then restored to `1.0f` on next `Start`.

### Interface Surface (UI Screens & Service Contract)

**1. `LibraryScreen` (Split-Tone: Maroon Header `#5E2418` + Light Neutral Body `#F3F3F4`)**
- **Top Bar:** Monastic Maroon `#5E2418` header with title `"DhammaMeditate"` and subtitle `"Offline Audio Library"` (white text, `1.4x` line-height).
- **Active Session Banner (conditional):** If playback is `PLAYING` / `PAUSED` / `FADING` for a track while user is on Library, shows a flat maroon-accented card (`"Active Session · <Title> · <MM:SS>"`) tapping directly back to `PlayerScreen`.
- **Import Error Banner (conditional):** Dismissible flat banner with `#D93025` border/text listing failed file(s) and reason without interrupting valid files from the same multi-select batch.
- **Loading State:** Non-blocking inline progress indicator on `"Import Audio"` button (`"Importing..."`) while SAF files copy & probe on `Dispatchers.IO`.
- **Empty State:** Centered flat `#F8F8F9` card (`1dp` `#D4D4D8` outline, `16dp` radius) explaining offline import of MP3, M4A, WAV, FLAC, OGG files.
- **Track List:** Flat `#F8F8F9` cards (`1dp` `#D4D4D8` outline, `16dp` radius): leading `40dp` circular `#F06A1A` badge with white outlined audio icon; middle column with track title (`16sp`, `1.45x` line height, max 2 lines with ellipsis) and formatted duration (`13sp`, `#6E6E73`); trailing delete icon button (with simple flat confirmation dialog) and subtle chevron (`>`).
- **Primary Bottom CTA:** Anchored full-width solid `#F06A1A` button (`54dp` height, `16dp` radius, `16dp` horizontal margin), white SemiBold label `"Import Audio"`, launching `OpenMultipleDocuments(arrayOf("audio/*"))`.

**2. `PlayerScreen` (Monastic Maroon Hero `#5E2418` + Flat Controls)**
- **Top Bar:** Back button to Library + subtle `"Timed Session"` header label (white).
- **Hero Section:** Track title (`20sp` Medium, white, `1.45x` line height), large tabular countdown (`52sp`, white, `MM:SS` or `H:MM:SS`), status label (`"Ready"`, `"Playing"`, `"Paused"`, `"Fading out..."`, `"Session complete"`), and a flat `4dp` non-interactive progress indicator (`#C98A1E` Lotus Gold fill on translucent dark track — strictly non-draggable).
- **Timer Selector Card (editable in `IDLE` / `COMPLETED`; disabled/dimmed while active):**
  - Quick chips: `10m`, `15m`, `30m`, `45m`, `60m`, `Full` (`track.durationMs`). Presets `> track.durationMs` are disabled.
  - Custom minutes numeric input (`min 1`): on value exceeding `track.durationMs`, snaps to `track.durationMs` and renders inline `#D93025` notice `"Adjusted to track length (<formatted>)"`.
- **Restricted Transport Controls (bottom area):**
  - `IDLE` / `COMPLETED`: Full-width solid `#F06A1A` `"Start Session"` button (`54dp`, `16dp` radius).
  - `PLAYING` / `FADING`: Full-width solid `#F06A1A` `"Pause"` button + secondary flat outlined `"Stop & Reset"` button.
  - `PAUSED`: Full-width solid `#F06A1A` `"Continue"` button + secondary flat outlined `"Stop & Reset"` button.
- **Error State:** If the local audio file is missing/unplayable on start, displays an inline `#D93025` error banner and stays in `IDLE` without crashing.

**3. Lock Screen / Media Notification (`MeditationPlaybackService`)**
- Title: active track title; subtitle: `"DhammaMeditate · Timed Session"`.
- Allowed `Player.Commands`: `COMMAND_PLAY_PAUSE`, `COMMAND_STOP`, `COMMAND_GET_CURRENT_MEDIA_ITEM`, `COMMAND_GET_METADATA`, `COMMAND_GET_TIMELINE`.
- Explicitly stripped commands: all `COMMAND_SEEK_*` and `COMMAND_SET_SPEED_AND_PITCH`.
- Custom `SessionCommand("ACTION_STOP_RESET")` exposed as a `CommandButton` in the session media layout so the user can trigger Stop (Reset) directly from the lock screen / shade alongside Play/Pause.

### Value Sourcing & Key Invariants
- **Track Title:** Sourced from `MediaMetadataRetriever.METADATA_KEY_TITLE` (trimmed, if non-blank) → fallback to SAF `OpenableColumns.DISPLAY_NAME` stripped of its last `.ext` → fallback `"Untitled Track"`.
- **Track Duration (`durationMs`):** Sourced during import from `MediaMetadataRetriever.METADATA_KEY_DURATION` (parsed as `Long > 0`), with fallback to `MediaExtractor` track format `MediaFormat.KEY_DURATION / 1000L`. Invariant: `durationMs > 0` required to accept import.
- **Supported Audio Validation:** Allowed file extensions/containers: `.mp3`, `.m4a`, `.aac`, `.wav`, `.flac`, `.ogg`, `.oga` (verified via `MediaExtractor` finding at least one track whose MIME starts with `"audio/"`, plus `durationMs > 0`).
- **Default Selected Timer:** Sourced as `min(30 * 60_000L, track.durationMs)` when opening a track in `IDLE`; invariant: `1_000L <= targetDurationMs <= track.durationMs`.
- **Elapsed & Remaining Time:** Sourced exclusively from monotonic `SystemClock.elapsedRealtime()` deltas accumulated inside `MeditationTimerEngine`: `elapsedMs = accumulatedElapsedMs + (now - segmentStartedAtMs).coerceAtLeast(0L)` while playing/fading; `remainingMs = (targetDurationMs - elapsedMs).coerceAtLeast(0L)`. Never derived from `delay()` tick counts.
- **Exponential Fade Volume:** Fixed `FADE_DURATION_MS = 10_000L`; `effectiveFadeMs = min(10_000L, targetDurationMs)`. For `0 < remainingMs <= effectiveFadeMs`, `p = (effectiveFadeMs - remainingMs).toDouble() / effectiveFadeMs.toDouble()` in `[0.0, 1.0]`, and `gain(p) = ((exp(-3.5 * p) - exp(-3.5)) / (1.0 - exp(-3.5))).toFloat().coerceIn(0f, 1f)`. Invariant: `gain(0.0) == 1.0f`, `gain(1.0) == 0.0f`, strictly monotonically decreasing.
- **Start-Fresh Invariant:** Every `Start` action sets `ExoPlayer.seekTo(0L)`, `ExoPlayer.volume = 1.0f`, and resets `MeditationTimerEngine` to `elapsedMs = 0L` before starting playback.

### Security, Validation & Edge Cases
- **App-Private File Isolation:** Copied audio lives strictly in `context.filesDir/audio/` (not shared external storage), preventing SAF URI permission expiration and surviving external file deletion. Copy writes to `<uuid>.tmp` first and deletes it in a `finally` block if probing or Room insertion fails.
- **Audio Interruptions & Headphone Unplug:** `ExoPlayer` is configured with `AudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_SPEECH)`, `handleAudioFocus = true`, and `handleAudioBecomingNoisy = true`. External pauses reported by `Player.Listener.onPlayWhenReadyChanged` immediately call `timerEngine.pause(now)` so countdown never advances while audio is interrupted.
- **Audio-End Edge Case:** If `ExoPlayer` emits `Player.STATE_ENDED` while `PLAYING` or `FADING` (e.g., metadata duration was 100ms longer than actual encoded samples when timer equals full track), the service cleanly completes the session (`volume = 0f`, `status = COMPLETED`, `remainingMs = 0L`) rather than stalling.
- **Android 13+ Notification Permission:** If the user denies `POST_NOTIFICATIONS`, foreground playback still runs reliably (Android permits FGS playback without the runtime permission, merely hiding the visible notification); the app remains crash-free and non-blocking.
- **Deleting Active Track:** Deleting the track currently loaded in `MeditationPlaybackService` triggers `stopAndReset()` before deleting the file and Room row.

## Build Plan
- [x] 1. Scaffold the native Android Gradle Kotlin DSL project (`settings.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml`), `DhammaMeditateApp`, `AppContainer`, and the flat Monastic Maroon & Saffron Material 3 Compose theme (`ui/theme/`). *(satisfies AC-7)*
- [x] 2. Implement Room persistence (`TrackEntity`, `TrackDao`, `AppDatabase`), `TrackRepository`, and `AudioFileImporter` with atomic app-private copying to `filesDir/audio/`, `MediaExtractor`/`MediaMetadataRetriever` validation (MP3, M4A/AAC, WAV, FLAC, OGG), and error/temp cleanup. *(satisfies AC-1, AC-2, AC-7)*
- [x] 3. Implement pure-Kotlin `FadeCurve`, `MeditationTimerEngine`, and `TimerValidation` alongside JUnit unit tests covering monotonic pause/continue drift immunity, exponential fade boundaries (`1.0f -> 0.0f`), short-track fade clamping, and timer capping. *(satisfies AC-3, AC-5, AC-6)*
- [x] 4. Implement `RestrictedMeditationPlayer`, `MeditationPlaybackService` (`MediaSessionService` + `WAKE_MODE_LOCAL` + restricted lock-screen Play/Pause & Stop-Reset commands + `250ms`/`50ms` timer/fade loop + audio focus sync), and `PlaybackClient`. *(satisfies AC-4, AC-5, AC-6)*
- [x] 5. Build `LibraryViewModel` and `LibraryScreen` wired to SAF `OpenMultipleDocuments`, Room `StateFlow`, Burmese-safe flat track cards, active-session resume banner, inline import error banner, and safe track deletion. *(satisfies AC-1, AC-2, AC-7)*
- [x] 6. Build `PlayerViewModel`, `PlayerScreen`, and `MainActivity` wiring (navigation, minute presets + capped custom input with inline snap notice, non-interactive progress bar, restricted transport buttons, and Android 13+ notification permission request). *(satisfies AC-3, AC-4, AC-5, AC-6, AC-7)*

## Implementation Notes
- Placed `AudioFileImporter` in `com.dhammameditate.data.importer` (`app/src/main/java/com/dhammameditate/data/importer/AudioFileImporter.kt`) instead of `data.import` because `import` is a reserved Kotlin keyword.
- Passed `arrayOf("audio/*", "application/ogg")` to `OpenMultipleDocuments` so Android's system document picker reliably includes `.ogg` files categorized under `application/ogg`.
- Explicitly registered `mediaSession` via `addSession(session)` and `setMediaButtonPreferences(listOf(stopResetButton))` in `MeditationPlaybackService.onCreate()` so Media3 1.5.1 promotes the service to foreground (`FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`) and exposes the `Stop & Reset` custom action on lock-screen/media notifications when started via `PlaybackClient`.

## Verification Checklist
### Build & Static Checks
- [x] `./gradlew assembleDebug` compiles cleanly with zero errors against `compileSdk = 35`.
- [x] `./gradlew testDebugUnitTest` passes all unit tests (`FadeCurveTest`, `MeditationTimerEngineTest`, `TimerValidationTest`).

### System Integration & Wiring
- [x] `MeditationPlaybackService` is registered in `AndroidManifest.xml` with `android:foregroundServiceType="mediaPlayback"` and `androidx.media3.session.MediaSessionService` intent filter, and connects cleanly from `PlaybackClient`.
- [x] `AppContainer` wires `AppDatabase`, `TrackRepository`, `AudioFileImporter`, and `PlaybackClient` into `MainActivity`, `LibraryViewModel`, and `PlayerViewModel` with zero orphaned files or stubbed handlers.

### Functional & Acceptance Checks
- [x] **AC-1:** Importing local MP3, M4A/AAC, WAV, FLAC, and OGG files copies each into `filesDir/audio/<uuid>.<ext>`, extracts title & duration, and renders both English and Burmese Unicode titles cleanly without diacritic clipping.
- [x] **AC-2:** Importing a corrupt or non-audio file shows an inline error banner, inserts no Room row, and leaves no leftover temp file in `filesDir/audio/`; valid tracks persist and play after force-stop and relaunch.
- [x] **AC-3:** Selecting presets above track duration is blocked (disabled); entering custom minutes above track duration snaps down to track length with an inline `#D93025` notice; timer controls lock while active.
- [x] **AC-4:** In-app Player and Android lock-screen/notification controls expose only Start / Pause-Continue / Stop (Reset) with no seek bar or next/previous actions; starting after Stop or Completion always restarts at `00:00` and full volume.
- [x] **AC-5:** Starting a timed session, turning the screen off, and performing Pause/Continue (in-app or on lock screen) maintains continuous audio and clock-accurate countdown with no drift.
- [x] **AC-6:** In the final 10 seconds (or full duration if `< 10s`), volume attenuates smoothly along the exponential curve to exact `0.0f` at `00:00` and transitions cleanly to `COMPLETED`.
- [x] **AC-7:** Deleting a track (including while active) stops playback if active, removes the Room row and app-private file; UI uses flat Monastic Maroon & Saffron colors with zero gradients and zero emojis.
