# 02. Two-Zone Session Editor & Queue Execution

- **Date:** 2026-11-25
- **Phase:** 02 — Two-Zone Session Editor & Queue Execution
- **Project Mode:** Prototype / MVP
- **Build Approach:** Tracer Bullet
- **Status:** Verified
- **Stack & Libraries:** Native Android (Kotlin, Gradle Kotlin DSL + Version Catalog, `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`), Jetpack Compose + Material 3 (flat Monastic Maroon & Saffron tokens), Jetpack Media3 (`media3-exoplayer`, `media3-session`, `media3-common`), Room SQLite + KSP (`room-runtime`, `room-ktx`, `room-compiler`, `MIGRATION_1_2`), Kotlin Coroutines + `StateFlow`, manual `AppContainer` DI, JUnit 4 + `kotlinx-coroutines-test` (zero new third-party dependencies)

## Summary
Builds reusable two-zone meditation sessions on top of Phase 01's offline audio library and foreground playback engine: each session combines an optional untimed **Intro Queue** (0+ ordered tracks) with a required timed **Main Queue** (1+ ordered tracks) and a saved meditation timer strictly capped to the combined Main Queue duration. Extends Room (`version = 2` via `MIGRATION_1_2`) with `sessions` and `session_tracks`, upgrades `MeditationTimerEngine` and `MeditationPlaybackService` for gapless Intro-to-Main multi-track execution (keeping the countdown frozen throughout Intro and starting the Main Queue + monotonic timer simultaneously), and introduces bottom-nav routing (`Sessions` | `Library`) with a two-zone Session Editor, Session Player, preserved Library Quick-Play, and safe cascade-remove/auto-clamp track deletion.

## Rationale & Trade-Offs
- **Chosen direction:** Extend Room with relational `sessions` + `session_tracks` tables (UUID entry PKs for duplicate track support + `ON DELETE CASCADE` FKs) and feed `[...introItems, ...mainItems]` into one `ExoPlayer` playlist guarded by `RestrictedMeditationPlayer` and a two-zone `MeditationTimerEngine`. Zero new dependencies, gapless screen-off transitions handled natively by `ExoPlayer`, and lock-screen skip/seek commands remain blocked automatically.
- **Runner-up considered:** Playing tracks one-by-one via manual `STATE_ENDED` reloading, and adding a third-party Compose drag-and-drop reorderable library. Manual track reloading risks audio gaps/wake-lock blips with the screen off; Up/Down move buttons keep the editor dependency-free, accessible, and conflict-free inside scrollable forms.
- **Trade-off accepted:** Session timers are edited and persisted exclusively inside `SessionEditorScreen` (keeping `SessionPlayerScreen` distraction-free), and single-track Library Quick-Play remains supported alongside saved sessions via the same unified queue service. Pre-session silence/Airplane Mode checks remain scoped to Phase 03.

## Requirements & Acceptance Criteria
- **AC-1 (Session CRUD, Two-Zone Queues & Reordering):** Users can create, edit, and delete reusable sessions from the `Sessions` home tab. Each session has a required non-blank name (English or Burmese Unicode), an optional **Intro Queue** (`0+` ordered tracks), and a required **Main Queue** (`1+` ordered tracks). Users can add tracks from the Library (including repeating the same track multiple times in a queue), reorder tracks within each zone using Up/Down buttons, and remove tracks cleanly. All session data persists across app restarts while preserving existing Phase 01 imported tracks via `MIGRATION_1_2`.
- **AC-2 (Strict Timer Validation, Snap-Down Notice & Invalid Save Guard):** In `SessionEditorScreen`, timer presets (`10m`, `15m`, `30m`, `45m`, `60m`, `Full`) and custom minutes input validate against the **combined Main Queue duration** (ignoring Intro Queue length). Presets exceeding combined Main duration are disabled. Entering custom minutes above combined Main duration—or removing a Main track so total duration drops below the current timer—immediately snaps the timer down to the new combined Main duration and displays an inline `#D93025` notice. Saving is blocked whenever session name is blank, Main Queue has 0 tracks, or timer input is invalid.
- **AC-3 (Seamless Intro-to-Main Execution with Frozen Intro Countdown):** Starting a session with 1+ Intro tracks plays all Intro tracks to completion in order while the countdown remains **frozen** at `targetDurationMs` (`elapsedMs = 0`, progress `0f`, volume `1.0f`, status `INTRO_PLAYING`). Pausing/continuing during Intro (`INTRO_PAUSED` ↔ `INTRO_PLAYING`) keeps the timer frozen. At the exact transition from the last Intro track to the first Main track, `onIntroComplete()` fires **once** and starts the monotonic countdown simultaneously with Main audio. Sessions with 0 Intro tracks (`introCount == 0`) start directly in Main playback (`PLAYING`, or `FADING` if `targetDurationMs <= 10s`).
- **AC-4 (Multi-Track Main Playback, Exponential Fade-Out & Audio-End Safety):** Main playback advances seamlessly across multiple Main tracks with the screen off, tracks monotonic elapsed/remaining time via `SystemClock.elapsedRealtime()`, and applies the fixed 10-second exponential fade-out (`FadeCurve`) to exact `0.0f` at `00:00` before pausing and rewinding to index `0`, position `0L` (`COMPLETED`). If `ExoPlayer` reaches `Player.STATE_ENDED` slightly before countdown hits `0` (e.g., MP3 metadata duration drift when timer equals total Main duration), the service cleanly completes and resets the session (`volume = 0.0f`, `remainingMs = 0L`, `COMPLETED`) without stalling.
- **AC-5 (Restricted Controls & Fresh `00:00` Start Across Queues):** Both `SessionPlayerScreen` and Android lock-screen/notification controls expose only Start / Pause-Continue / Stop (Reset)—never seek bars, next/previous track buttons, or tappable queue-skipping. Starting after Stop or Completion always restarts from Intro track `0` at `00:00` (or Main track `0` if Intro is empty) at full `1.0f` volume. Library Quick-Play for individual tracks continues working with the same restrictions.
- **AC-6 (Safe Library Track Deletion & Active-Playback Stop Guards):** Deleting a Library track referenced by one or more saved sessions shows a confirmation dialog listing affected sessions (calling out any session whose Main Queue will become empty). On confirm, an atomic transaction removes the track from referencing queues, re-indexes remaining positions, auto-clamps affected timers to the shortened Main total, and sets `targetDurationMs = 0L` on sessions left with 0 Main tracks (displaying a `#D93025` `"Needs Main Track"` badge and blocking playback until edited). Active playback is automatically stopped and reset before completing: (a) deletion of a track currently playing in Quick-Play or inside the active session, (b) deletion of the currently active session, or (c) saving edits to the currently active session.
- **AC-7 (Flat Monastic Navigation & Consistent Visual System):** Home uses a flat bottom navigation bar (`Sessions` | `Library`) with a shared **Active Session Banner** tapping back to the active Session or Quick-Play player. All new screens strictly follow the flat Monastic Maroon (`#5E2418`), Saffron (`#F06A1A`), Lotus Gold (`#C98A1E`), and Light Neutral (`#F3F3F4`) tokens with `1dp` `#D4D4D8` outlines, zero gradients, zero emojis, and Burmese-safe `1.4x–1.5x` line heights.

## Technical Design

### Files & Module Layout
- `app/src/main/java/com/dhammameditate/data/local/QueueZone.kt` — Enum `QueueZone { INTRO, MAIN }` + string serialization helpers (new).
- `app/src/main/java/com/dhammameditate/data/local/SessionEntity.kt` — Room entity for `sessions` table (new).
- `app/src/main/java/com/dhammameditate/data/local/SessionTrackEntity.kt` — Room entity for `session_tracks` join table with FKs & unique position index (new).
- `app/src/main/java/com/dhammameditate/data/local/SessionDao.kt` — Room DAO for observing sessions/items, atomic session upsert, reference queries, position re-indexing, and timer updates (new).
- `app/src/main/java/com/dhammameditate/data/local/AppDatabase.kt` — Bump `version = 2`, register `SessionEntity` & `SessionTrackEntity`, expose `sessionDao()`, add explicit `MIGRATION_1_2` (modified).
- `app/src/main/java/com/dhammameditate/data/repository/SessionModels.kt` — Immutable domain models: `QueueTrackItem`, `SessionWithQueues`, `TrackReferenceSummary`, `SessionDraft` (new).
- `app/src/main/java/com/dhammameditate/data/repository/SessionRepository.kt` — Coordinates Room transactions for session CRUD, reactive `Flow<List<SessionWithQueues>>`, referenced-session lookup, and atomic safe track deletion + clamping (new).
- `app/src/main/java/com/dhammameditate/data/repository/TrackRepository.kt` — Retain file resolution & single-track operations; delegate safe deletion transaction cleanly with `SessionRepository` (modified).
- `app/src/main/java/com/dhammameditate/di/AppContainer.kt` — Wire `SessionRepository` alongside `TrackRepository`, `AudioFileImporter`, and `PlaybackClient` (modified).
- `app/src/main/java/com/dhammameditate/playback/MeditationTimerEngine.kt` — Add `INTRO_PLAYING` / `INTRO_PAUSED` states, `hasIntro` param on `start()`, and one-shot `onIntroComplete(nowMs)` transition into `PLAYING`/`FADING` (modified).
- `app/src/main/java/com/dhammameditate/playback/TimerValidation.kt` — Add queue-aware validation/capping copy (`capDurationMs(..., scopeLabel)` / `reconcileTimerAfterMainChange(...)`) while preserving Phase 01 API compatibility (modified).
- `app/src/main/java/com/dhammameditate/playback/PlaybackState.kt` — Extend `PlaybackSnapshot` with `activeSessionId`, `activeSessionName`, `activeTrackIds`, `currentZone`, `currentQueueIndex`, `zoneTrackNumber`, `zoneTrackCount`, `introCount`, `mainCount`, `errorSessionId` (modified).
- `app/src/main/java/com/dhammameditate/playback/MeditationPlaybackService.kt` — Unified multi-track playlist execution (`ACTION_START_SAVED_SESSION` + Phase 01 `ACTION_START_SESSION` quick-track), `onMediaItemTransition` one-shot Intro→Main hook, `STATE_ENDED` safety completion, and `ACTION_STOP_IF_SESSION` / `ACTION_STOP_IF_TRACK` (matching `activeTrackIds`) guards (modified).
- `app/src/main/java/com/dhammameditate/playback/PlaybackClient.kt` — Add `startSavedSession(sessionId)`, `stopIfActiveSessionAndWait(sessionId)`, and extend `stopIfActiveTrackAndWait(trackId)` to match `trackId in activeTrackIds` (modified).
- `app/src/main/java/com/dhammameditate/ui/common/ActiveSessionBanner.kt` — Shared flat active-playback banner composable used on both `SessionsScreen` and `LibraryScreen` (new).
- `app/src/main/java/com/dhammameditate/ui/sessions/SessionsViewModel.kt`, `SessionsScreen.kt` — Saved sessions list, empty state, `"Needs Main Track"` badge, edit/delete confirmation, and `"New Session"` CTA (new).
- `app/src/main/java/com/dhammameditate/ui/editor/SessionEditorViewModel.kt`, `SessionEditorScreen.kt` — Two-zone Intro/Main editor, Library track-picker bottom sheet, Up/Down reorder & remove, live timer snap-down notice, and strict save guard (new).
- `app/src/main/java/com/dhammameditate/ui/sessionplayer/SessionPlayerViewModel.kt`, `SessionPlayerScreen.kt` — Monastic Maroon two-zone player with zone badge, active track title, frozen-during-Intro countdown, non-interactive progress bar, read-only queue list, and restricted transport controls (new).
- `app/src/main/java/com/dhammameditate/ui/library/LibraryViewModel.kt`, `LibraryScreen.kt` — Query affected sessions before track delete confirmation; use shared `ActiveSessionBanner` supporting both Session and Quick-Play return navigation (modified).
- `app/src/main/java/com/dhammameditate/MainActivity.kt` — Sealed `AppDestination` routing (`Home(HomeTab.SESSIONS | HomeTab.LIBRARY)`, `SessionEditor(sessionId?)`, `SessionPlayer(sessionId)`, `QuickTrackPlayer(trackId)`), flat bottom `NavigationBar`, and notification deep-link routing (modified).
- `app/src/test/java/com/dhammameditate/playback/MeditationTimerEngineTest.kt` & `TimerValidationTest.kt` — Expand unit tests for Intro-frozen countdown, Intro pause/resume, one-shot `onIntroComplete()`, `introCount == 0` direct Main start, short-Main fade after Intro, and Main-removal timer auto-snap (modified).

### Data Model / State Shape

**Room Entity: `sessions` (`SessionEntity`)**
| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `TEXT` (`String`) | `PRIMARY KEY` | UUID v4 string |
| `name` | `TEXT` (`String`) | `NOT NULL` | Trimmed non-blank session name (UTF-8, Burmese-safe) |
| `target_duration_ms` | `INTEGER` (`Long`) | `NOT NULL` (`>= 0`) | Saved Main Queue timer in ms (`0L` only when library track deletion empties Main Queue) |
| `created_at_epoch_ms` | `INTEGER` (`Long`) | `NOT NULL` | Creation timestamp |
| `updated_at_epoch_ms` | `INTEGER` (`Long`) | `NOT NULL` | Last edit timestamp; `SessionsScreen` orders `updated_at_epoch_ms DESC` |

**Room Entity: `session_tracks` (`SessionTrackEntity`)**
| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `TEXT` (`String`) | `PRIMARY KEY` | UUID v4 per queue entry (permits repeating the same `track_id` in a queue) |
| `session_id` | `TEXT` (`String`) | `NOT NULL`, FK → `sessions(id)` `ON DELETE CASCADE`, `Index` | Owning session |
| `track_id` | `TEXT` (`String`) | `NOT NULL`, FK → `tracks(id)` `ON DELETE CASCADE`, `Index` | Referenced library track |
| `zone` | `TEXT` (`String`) | `NOT NULL` | `"INTRO"` or `"MAIN"` |
| `position` | `INTEGER` (`Int`) | `NOT NULL` | Dense `0`-based order within `(session_id, zone)` |

- **Unique Composite Index:** `UNIQUE(session_id, zone, position)` guarantees deterministic queue order per zone.
- **Explicit `MIGRATION_1_2`:** Creates `sessions`, `session_tracks`, and the three indexes (`index_session_tracks_session_id`, `index_session_tracks_track_id`, `index_session_tracks_session_id_zone_position`) without modifying `tracks`.

**Extended Playback State Machine (`PlaybackStatus`)**
- `IDLE`: Ready (`elapsedMs = 0`, `remainingMs = targetDurationMs`, `volume = 1.0f`).
- `INTRO_PLAYING`: Playing Intro Queue (`index < introCount`); countdown frozen (`elapsedMs = 0`, `remainingMs = targetDurationMs`, `volume = 1.0f`).
- `INTRO_PAUSED`: Paused during Intro Queue; countdown remains frozen (`elapsedMs = 0`, `remainingMs = targetDurationMs`, `volume = 1.0f`). Resuming returns to `INTRO_PLAYING`.
- `PLAYING`: Playing Main Queue (`index >= introCount`); monotonic countdown active (`remainingMs > effectiveFadeMs`, `volume = 1.0f`).
- `PAUSED`: Paused during Main Queue; `accumulatedElapsedMs` frozen. Resuming returns to `FADING` if `remainingMs <= effectiveFadeMs`, else `PLAYING`.
- `FADING`: Main Queue within final `min(10_000L, targetDurationMs)`; `volume` updated at `50ms` cadence via `FadeCurve.gain(...)`.
- `COMPLETED`: Countdown hit `00:00` or Main Queue ended (`remainingMs = 0L`, `volume = 0.0f`, player paused & rewound to `(0, 0L)`).
- `PlaybackSnapshot.isActive` is `true` for `INTRO_PLAYING`, `INTRO_PAUSED`, `PLAYING`, `PAUSED`, and `FADING`.

### Interface Surface (UI Screens & Service Contract)

1. **Bottom Navigation Shell (`MainActivity`)**
   - Two flat tabs: `Sessions` (default landing tab) and `Library`.
   - Flat `NavigationBar` (`#F8F8F9` container, `1dp` top `#D4D4D8` divider, `#5E2418` active icon/text, subtle `#EFE5E2` active pill, `#6E6E73` inactive).
   - Shared `ActiveSessionBanner` appears at the top of either tab whenever `playback.isActive`, showing session/track title, phase (`Intro`, `Main`, `Paused`, `Fading`), and remaining countdown; tapping routes to `SessionPlayer(activeSessionId)` or `QuickTrackPlayer(activeTrackId)`.

2. **`SessionsScreen` (Split-Tone: Maroon Header `#5E2418` + Light Neutral `#F3F3F4`)**
   - Header: `"DhammaMeditate"` / `"Meditation Sessions"`.
   - Empty State: Flat `#F8F8F9` card explaining two-zone Intro + Main sessions, prompting user to tap `"New Session"` (or import audio in Library if no tracks exist yet).
   - Session Cards (`#F8F8F9`, `1dp` `#D4D4D8`, `16dp` radius):
     - Title (`16sp` SemiBold, `1.45x` line height, max 2 lines).
     - Zone summary line: `"Intro: 2 tracks (04:10) · Main: 2 tracks (45:00)"` (or `"No Intro · Main: 1 track (30:00)"`).
     - Timer badge: flat Saffron-tinted pill `"Timer 30:00"`, or `#D93025` Alert pill `"Needs Main Track"` when `mainItems.isEmpty()`.
     - Card tap → `SessionPlayer(sessionId)` (or `SessionEditor(sessionId)` directly if `needsMainTrack`).
     - Trailing `Edit` and `Delete` icon buttons (Delete shows flat confirmation dialog and stops playback first if active).
   - Anchored bottom primary CTA (`#F06A1A`, `54dp`, `16dp` radius): `"New Session"`.

3. **`SessionEditorScreen` (Create / Edit Two-Zone Session)**
   - Top bar (`#5E2418`): Back button + `"New Session"` / `"Edit Session"`.
   - Scrollable Light Neutral `#F3F3F4` body with flat `#F8F8F9` section cards:
     - **Session Name card:** Flat `OutlinedTextField` (`"Session Name"`, Burmese-safe line height), inline `#D93025` error if blank after touch/submit.
     - **Intro Queue card (Optional · Untimed):** Header (`"Intro Queue"`, `"Optional · Countdown stays paused"`) + `"Add Track"` button → opens flat `ModalBottomSheet` listing all Library tracks (with duration and empty-library guidance). Ordered rows display 1-based index, track title (`1.45x` line height), duration, Up (`ArrowUpward`, disabled at index `0`), Down (`ArrowDownward`, disabled at last index), and Remove (`Close`). Footer shows `"Intro total: MM:SS (untimed)"` when non-empty.
     - **Main Queue card (Required · Timed):** Header (`"Main Queue"`, `"Required · Combined: MM:SS"`) + `"Add Track"` button (same bottom sheet). Empty state shows inline `#D93025` prompt `"Add at least 1 track to the Main Queue"`. Identical Up / Down / Remove row controls. Removing a track automatically re-validates timer and snaps down with inline `#D93025` notice if needed.
     - **Meditation Timer card:** Quick preset chips (`10m`, `15m`, `30m`, `45m`, `60m`, `Full`) + custom minutes numeric input (`min 1`), all validated against combined `mainTotalDurationMs`. Disabled when `mainItems.isEmpty()`. Displays inline `#D93025` cap/snap notice when clamped.
     - Optional subtle notice if editing the currently active session: `"Saving changes will stop and reset the active session."`
   - Anchored bottom primary CTA (`#F06A1A`, `54dp`, `16dp` radius): `"Save Session"` (disabled when `name.isBlank() || mainItems.isEmpty() || targetDurationMs <= 0L || hasInvalidCustomInput`).

4. **`SessionPlayerScreen` (Monastic Maroon `#5E2418` Hero + Read-Only Queue)**
   - Top bar: Back button, `"Meditation Session"` label, and `"Edit"` icon button (enabled only in `IDLE` / `COMPLETED`; hidden or disabled while active).
   - Hero section:
     - Session name (`20sp` Medium, white, `1.45x` line height).
     - Zone status pill: `"INTRO · Track 1 of 2 · Timer paused"` (during `INTRO_PLAYING`/`INTRO_PAUSED`), `"MAIN · Track 1 of 2"` (during `PLAYING`/`PAUSED`/`FADING`), or `"READY · Intro + Main"` / `"READY · Main only"` (during `IDLE`).
     - Active track title (`15sp`, `#D9C7C2`, `1.45x` line height).
     - Tabular countdown (`52sp`, white): shows saved `targetDurationMs` while `IDLE`/`INTRO_PLAYING`/`INTRO_PAUSED`, live remaining ms while `PLAYING`/`PAUSED`/`FADING`, `00:00` when `COMPLETED`.
     - Flat `4dp` non-interactive Lotus Gold progress bar (`0f` during Intro & Idle, `elapsedMs / targetDurationMs` during Main, `1f` on Completed).
   - Read-only Queue Overview card (subtle translucent maroon surface with `#834234` outline): displays Intro tracks and Main tracks in order with duration and a Lotus Gold active indicator on `currentQueueIndex` — strictly non-clickable (no skip/seek).
   - Bottom restricted transport controls (`Start Session`, `Pause` / `Continue`, `Stop & Reset`) + inline `#D93025` error banner (e.g., missing file or `"Needs Main Track"` with `"Edit Session"` shortcut).

5. **`LibraryScreen` (Updated)**
   - Keeps Phase 01 import, Burmese-safe track cards, and Quick-Play tap-to-open `QuickTrackPlayer(trackId)`.
   - Delete confirmation queries `SessionRepository.getTrackReferenceSummary(trackId)` before showing dialog:
     - Unreferenced: standard Phase 01 confirmation.
     - Referenced: lists affected session names and flags any session that will hit 0 Main tracks (`"Will need a new Main track before playing"`), then executes the stop-guard + atomic cascade-remove/clamp + file delete on confirm.

### Value Sourcing & Key Invariants
- **Combined Zone Durations:** `introTotalDurationMs = introItems.sumOf { it.track.durationMs }` and `mainTotalDurationMs = mainItems.sumOf { it.track.durationMs }`, derived strictly from Room `tracks.duration_ms` summed across ordered `session_tracks` entries.
- **Timer Initialization & Cap Invariant:**
  - When a new session adds its first Main track (transitioning from `0` → `1` Main track), `targetDurationMs` initializes to `TimerValidation.defaultDurationMs(mainTotalDurationMs)` (`min(30m, mainTotalDurationMs)`).
  - Whenever `mainTotalDurationMs` changes (track removed in editor or track deleted in library): if `mainTotalDurationMs == 0L`, `targetDurationMs = 0L`; else if `targetDurationMs > mainTotalDurationMs`, `targetDurationMs = mainTotalDurationMs` with inline notice `"Adjusted to Main Queue length (<formatted>)"`.
  - Invariant for playable/saveable session: `name.isNotBlank() && mainItems.isNotEmpty() && targetDurationMs in 1L..mainTotalDurationMs`.
- **One-Shot Intro→Main Transition Invariant:**
  - `ExoPlayer` playlist is strictly `[...introItems, ...mainItems]` (`introCount = introItems.size`).
  - If `introCount == 0`, `timerEngine.start(targetDurationMs, hasIntro = false, nowMs)` starts directly in `PLAYING` (or `FADING` if `targetDurationMs <= effectiveFadeMs`) and starts the tick loop immediately.
  - If `introCount > 0`, `timerEngine.start(targetDurationMs, hasIntro = true, nowMs)` enters `INTRO_PLAYING` with countdown frozen (`elapsedMs = 0L`, `remainingMs = targetDurationMs`, `volume = 1.0f`).
  - In `Player.Listener.onMediaItemTransition`: `timerEngine.onIntroComplete(elapsedRealtime())` is called **only** when `currentMediaItemIndex >= introCount` AND `timerEngine.currentStatus() in setOf(INTRO_PLAYING, INTRO_PAUSED)` — guaranteeing it fires at most **once** per session run and never re-triggers on Main→Main transitions.
- **Lock-Screen Metadata Sourcing:** Each `MediaItem` sets `title = track.title` and `subtitle/artist = "<Session Name> · Intro (k/n)"` or `"<Session Name> · Main (k/n)"` (or `"DhammaMeditate · Timed Session"` for Library Quick-Play).
- **Start-Fresh Invariant:** `Start` and `Stop & Reset` always rewind `ExoPlayer` to `seekTo(0, 0L)` (media item index `0`, position `0L`) and `volume = 1.0f`, resetting `currentQueueIndex = 0` and `MeditationTimerEngine` to `elapsedMs = 0L`.
- **Session List Ordering:** `SessionDao` queries sessions ordered by `updated_at_epoch_ms DESC` (library track deletion clamps `target_duration_ms` without altering `updated_at_epoch_ms` so unrelated session ordering stays stable).

### Security, Validation & Edge Cases
- **Active Playback Stop Guards (All Three Paths):**
  1. **Library track deleted:** `PlaybackClient.stopIfActiveTrackAndWait(trackId)` stops & clears playback if `activeTrackId == trackId || trackId in activeTrackIds` (covers both Quick-Play and any active two-zone session using that track) before running the DB transaction and file deletion.
  2. **Saved session deleted:** `PlaybackClient.stopIfActiveSessionAndWait(sessionId)` stops & clears playback if `activeSessionId == sessionId` before deleting the `sessions` row.
  3. **Saved session edited:** `SessionEditorViewModel.save()` calls `PlaybackClient.stopIfActiveSessionAndWait(sessionId)` before persisting edits if `activeSessionId == sessionId`, ensuring playback never continues against stale queue definitions.
- **Premature `Player.STATE_ENDED` Safety:** If `ExoPlayer` emits `STATE_ENDED` while `PLAYING` or `FADING` (e.g., MP3 encoded sample length is slightly shorter than metadata duration when timer equals combined Main duration), `completeSessionEarly()` cleanly sets `volume = 0.0f`, pauses, rewinds to `(0, 0L)`, and publishes `COMPLETED` (`remainingMs = 0L`). If `STATE_ENDED` occurs unexpectedly during `INTRO_PLAYING` (defensive guard), it likewise stops cleanly without hanging.
- **Audio Focus / Headphone Unplug During Intro:** `onPlayWhenReadyChanged` and `onPlaybackSuppressionReasonChanged` mirror Phase 01 for `INTRO_PLAYING` ↔ `INTRO_PAUSED` so interruptions pause Intro cleanly without starting the Main countdown; if audio focus is suppressed at the exact instant Intro transitions to Main, `onIntroComplete()` anchors `0L` elapsed and immediately pauses until focus returns.
- **Missing Audio File on Start:** Before calling `exoPlayer.setMediaItems(...)`, `MeditationPlaybackService` validates that every track file in `introItems + mainItems` exists and is readable; if any file is missing, publishes a clear inline error naming the missing track and stays `IDLE`.
- **Room `MIGRATION_1_2` Preservation:** Never use `fallbackToDestructiveMigration()`; explicit SQL migration preserves all Phase 01 `tracks` rows across upgrades.

## Build Plan
- [x] 1. Add Room v2 schema (`QueueZone`, `SessionEntity`, `SessionTrackEntity`, `SessionDao`, `AppDatabase` with explicit `MIGRATION_1_2`), domain models (`SessionModels`), and `SessionRepository` (session CRUD, reactive queue assembly, track-reference lookup, and atomic cascade-remove + position re-indexing + timer auto-clamp), wired in `AppContainer`. *(satisfies AC-1, AC-6)*
- [x] 2. Extend pure-Kotlin `MeditationTimerEngine` (`INTRO_PLAYING`/`INTRO_PAUSED`, `hasIntro`, one-shot `onIntroComplete()`) and `TimerValidation` (combined Main Queue cap & removal auto-snap), adding thorough JUnit unit tests in `MeditationTimerEngineTest` and `TimerValidationTest`. *(satisfies AC-2, AC-3, AC-4)*
- [x] 3. Upgrade `PlaybackState`, `PlaybackClient`, and `MeditationPlaybackService` for unified multi-track `[...introItems, ...mainItems]` execution, one-shot `onMediaItemTransition` Intro→Main trigger, `introCount == 0` direct Main start, `STATE_ENDED` early-completion safety, lock-screen per-track zone metadata, and active-track/active-session stop guards. *(satisfies AC-3, AC-4, AC-5, AC-6)*
- [x] 4. Build `SessionEditorViewModel` and `SessionEditorScreen` with Session Name validation, two-zone Intro/Main cards, Library track-picker bottom sheet (allowing duplicate tracks), compact Up/Down reorder + remove controls, combined Main Queue timer selector with live `#D93025` snap-down notice, strict save blocking, and active-session edit reset guard. *(satisfies AC-1, AC-2, AC-6, AC-7)*
- [x] 5. Build `SessionsViewModel`, `SessionsScreen`, `SessionPlayerViewModel`, `SessionPlayerScreen`, and shared `ActiveSessionBanner` with flat Monastic Maroon & Saffron cards, `"Needs Main Track"` badge, frozen-during-Intro countdown, read-only queue highlight list, and restricted Start / Pause-Continue / Stop (Reset) controls. *(satisfies AC-1, AC-3, AC-4, AC-5, AC-7)*
- [x] 6. Wire bottom-nav `MainActivity` (`Sessions` | `Library`, `SessionEditor`, `SessionPlayer`, `QuickTrackPlayer`, notification deep-link) and upgrade `LibraryViewModel`/`LibraryScreen` with affected-session track deletion warnings and safe atomic cascade-remove/clamp execution. *(satisfies AC-1, AC-5, AC-6, AC-7)*

## Implementation Notes
- Updated `PlayerViewModel` and `PlayerScreen` (Library Quick-Play) to handle `INTRO_PLAYING`/`INTRO_PAUSED` exhaustively and scope Quick-Play state checks to `playback.activeSessionId == null` so active two-zone sessions do not bleed into Quick-Play UI.
- Replaced `TrackRepository.deleteTrack` with `TrackRepository.deleteAudioFile` called after `SessionRepository.deleteTrackSafely` commits its atomic Room transaction, eliminating any unguarded track-deletion path.
- Added flat palette helper tokens (`MaroonSurfaceSubtle`, `NavActivePill`, `SaffronTintSurface`) in `ui/theme/Color.kt` matching the plan's flat Monastic Maroon & Saffron surface spec.

## Verification Checklist
### Build & Static Checks
- [x] `./gradlew assembleDebug` compiles cleanly with zero errors against `compileSdk = 35`.
- [x] `./gradlew testDebugUnitTest` passes all unit tests (`FadeCurveTest`, `MeditationTimerEngineTest`, `TimerValidationTest`), including Intro-frozen countdown, Intro pause/resume, one-shot `onIntroComplete()` (no re-trigger on Main→Main), `introCount == 0` direct Main start, short-Main fade after Intro, and Main-removal timer auto-snap.

### System Integration & Wiring
- [x] `AppDatabase` upgrades cleanly from `version = 1` to `version = 2` via `MIGRATION_1_2` without deleting existing `tracks` rows; foreign keys (`ON DELETE CASCADE`) and unique `(session_id, zone, position)` index are enforced.
- [x] `AppContainer`, `MainActivity`, `SessionsScreen`, `SessionEditorScreen`, `SessionPlayerScreen`, `LibraryScreen`, `PlayerScreen`, and `MeditationPlaybackService` are wired end to end with zero orphaned files, dead routes, or broken notification deep-links.

### Functional & Acceptance Checks
- [x] **AC-1:** Creating, editing, reordering (Up/Down), repeating the same track in a queue, and deleting sessions works cleanly across both Intro and Main zones and persists across app restarts (with clean English & Burmese Unicode rendering).
- [x] **AC-2:** Presets above combined Main Queue duration are disabled; custom minutes above combined Main duration—and removing a Main track below the current timer—immediately snap down to the new combined Main duration with an inline `#D93025` notice; saving with blank name or 0 Main tracks is blocked.
- [x] **AC-3:** Starting a session with Intro + Main tracks plays all Intro tracks to completion while the countdown remains frozen at `targetDurationMs` (including through Pause/Continue during Intro), then triggers `onIntroComplete()` once at the Intro→Main boundary to start Main audio and monotonic countdown simultaneously; sessions with 0 Intro tracks start directly in Main playback.
- [x] **AC-4:** Multi-track Main playback transitions seamlessly across tracks with the screen off, fades out smoothly along the 10s exponential curve to `0.0f` at `00:00`, and cleanly completes/resets if `ExoPlayer` hits `STATE_ENDED` slightly before `00:00`.
- [x] **AC-5:** In-app `SessionPlayerScreen`, Library Quick-Play, and lock-screen controls expose only Start / Pause-Continue / Stop (Reset) with no seek/skip/next/previous actions; starting after Stop or Completion always restarts from track `0` at `00:00` and `1.0f` volume.
- [x] **AC-6:** Deleting a referenced Library track displays affected sessions, stops active playback if that track is playing (Quick-Play or inside active session), cascade-removes references, clamps shortened Main timers, and marks 0-Main sessions `"Needs Main Track"` (blocked from starting); deleting or saving edits to the currently active session stops and resets playback cleanly first.
- [x] **AC-7:** Bottom nav (`Sessions` | `Library`) and shared `ActiveSessionBanner` work across both tabs; all screens maintain flat Monastic Maroon & Saffron styling with zero gradients, zero emojis, and clean Burmese Unicode line heights.
