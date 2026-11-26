# 04. Custom Track Naming & Import Rename Flow

- **Date:** 2026-11-26
- **Phase:** 04 — Custom Track Naming & Import Rename Flow
- **Project Mode:** Prototype / MVP
- **Build Approach:** Journey
- **Status:** Verified
- **Stack & Libraries:** Native Android (Kotlin, Gradle Kotlin DSL, `compileSdk = 35`, `minSdk = 26`), Jetpack Compose + Material 3 (flat Monastic Maroon & Saffron tokens, `tonalElevation = 0.dp`), Jetpack Media3 (`ExoPlayer` + `MediaSessionService`), Room SQLite v2 (KSP, no schema migration required), Kotlin Coroutines + `StateFlow`, manual `AppContainer` DI, JUnit 4

## Summary
Phase 04 adds custom track naming across the full import and library lifecycle: single-file imports open a pre-filled rename dialog, multi-file imports offer a batch choice (`Rename tracks` vs `Skip all`) followed by a step-through wizard (`Track X of N`), and existing library tracks can be renamed anytime via an inline Edit button. Track display names remain stored in the existing `tracks.title` Room column (with UUID-backed `local_file_name` untouched) and propagate immediately to Library, Session Editor, Player, Session Player, `ActiveSessionBanner`, and live lock-screen Media3 metadata without interrupting active playback.

## Rationale & Trade-Offs
- **Chosen direction:** Reuse `tracks.title` as the single source of truth (zero Room migration), isolate naming validation and import-wizard transitions in pure Kotlin for fast JUnit 4 tests, and push live title updates to `MeditationPlaybackService` via `exoPlayer.replaceMediaItem(...)` so active playback and timers are never interrupted.
- **Runner-up considered:** Adding a separate `custom_title` nullable column alongside `original_title`, or replacing Library card actions with an overflow menu (`⋮`). Skipped because a single editable `title` column keeps all existing queries and iOS spec parity simple, and an inline Edit pencil button matches the existing 1-tap flat Delete pattern in `LibraryScreen`.
- **Trade-off accepted:** Duplicate display names are intentionally permitted (tracks are uniquely identified by UUID), and renaming updates the display title only—never the underlying app-private filename or embedded audio tags.

## Requirements & Acceptance Criteria
- **AC-1 (Single-File Import Rename):** Importing 1 valid audio file saves the track with its probed default title and immediately opens a flat Rename dialog pre-filled with that title. Tapping **Save** persists the trimmed custom title; tapping **Keep default** or dismissing leaves the probed title intact without deleting the track.
- **AC-2 (Multi-File Import Batch Prompt & Step-Through):** Importing 2+ valid audio files opens a flat batch prompt showing how many tracks were imported with **Rename tracks** and **Skip all**. Choosing **Skip all** (or dismissing) closes the prompt and keeps default titles. Choosing **Rename tracks** steps through each newly imported track in import order with a `Track X of N` header, pre-filled title input, **Save & Next** (**Save** on the final track), **Skip** (keeps default for this track and advances), and **Skip remaining** (exits immediately, keeping defaults for remaining tracks).
- **AC-3 (Partial Import Failure Handling):** When a multi-file selection has both successes and failures, the `ImportErrorBanner` displays the failed files while the rename flow runs only for the successfully imported tracks (single rename if 1 succeeded, batch prompt if 2+ succeeded, no rename dialog if 0 succeeded).
- **AC-4 (Existing Track Rename from Library):** Each `TrackCard` in `LibraryScreen` displays an inline Edit (pencil) icon button next to Delete. Tapping it opens a flat Rename dialog pre-filled with `track.title`; saving persists the trimmed title immediately.
- **AC-5 (Validation & Unicode Safety):** Leading/trailing whitespace is trimmed on save. Blank or whitespace-only input disables **Save** / **Save & Next** and shows an inline error message (`"Track name cannot be empty"`). Duplicate track display names are allowed. Burmese Unicode titles render cleanly without clipping in the input field and across all display surfaces.
- **AC-6 (Immediate Propagation Across All Surfaces & Live Playback):** After any track rename, the new display name updates immediately in `LibraryScreen`, `SessionEditorScreen` (including already-open drafts and track picker), `SessionsScreen`, `PlayerScreen`, `SessionPlayerScreen`, `ActiveSessionBanner`, and lock-screen/notification Media3 metadata—without stopping, restarting, or seeking active playback.

## Technical Design

### Files & Module Layout
- `app/src/main/java/com/dhammameditate/ui/library/TrackNaming.kt` — *(new, pure Kotlin)* Title normalization/validation (`TrackTitleValidation`) and import-rename state machine (`ImportRenameState` + transition functions).
- `app/src/main/java/com/dhammameditate/data/local/TrackDao.kt` — *(modified)* Add `observeById(id: String): Flow<TrackEntity?>` and `updateTitle(id: String, title: String): Int`.
- `app/src/main/java/com/dhammameditate/data/repository/TrackRepository.kt` — *(modified)* Add `observeTrack(id: String): Flow<TrackEntity?>` and `renameTrack(id: String, rawTitle: String): String?` (runs on `Dispatchers.IO`, validates via `TrackTitleValidation`, returns persisted trimmed title or `null` if invalid/not found).
- `app/src/main/java/com/dhammameditate/playback/PlaybackClient.kt` — *(modified)* Add `notifyTrackRenamed(trackId: String, newTitle: String)` to dispatch `ACTION_REFRESH_TRACK_TITLE` when the renamed track is present in the current playback snapshot.
- `app/src/main/java/com/dhammameditate/playback/MeditationPlaybackService.kt` — *(modified)* Handle `ACTION_REFRESH_TRACK_TITLE` (`EXTRA_TRACK_ID`, `EXTRA_TRACK_TITLE`): update `activeQueueEntries`, replace matching `MediaItem` metadata in-place via `exoPlayer.replaceMediaItem(index, updatedItem)` inside `withInternalPlayerChange`, and update `PlaybackSnapshot.activeTrackTitle` in `stateStore`.
- `app/src/main/java/com/dhammameditate/ui/player/PlayerViewModel.kt` — *(modified)* Observe `trackRepository.observeTrack(trackId)` in `combine(...)` instead of a one-shot `getTrack()` so `PlayerScreen` reflects renames live.
- `app/src/main/java/com/dhammameditate/ui/editor/SessionEditorViewModel.kt` — *(modified)* Map `draft.introItems` and `draft.mainItems` through the live `libraryTracks` map inside `combine(...)` so queued track titles update live even while the editor is open.
- `app/src/main/java/com/dhammameditate/ui/library/LibraryViewModel.kt` — *(modified)* Hold `importRenameState` and `editingTrackState`, trigger post-import rename flow from `importFiles()`, persist renames via `trackRepository.renameTrack()`, and notify `playbackClient.notifyTrackRenamed()`.
- `app/src/main/java/com/dhammameditate/ui/library/LibraryScreen.kt` — *(modified)* Add inline `Icons.Outlined.Edit` button on `TrackCard` and render flat Material 3 dialogs (`tonalElevation = 0.dp`, `1.dp` `OutlineNeutral` border, `CardSurface`, `RoundedCornerShape(16.dp)`) for batch choice, single/step-through import rename, and existing-track rename.
- `app/src/test/java/com/dhammameditate/ui/library/TrackNamingTest.kt` — *(new)* JUnit 4 unit tests for title normalization/blank rejection (including Burmese Unicode) and all `ImportRenameState` transitions.

### Data Model / State Shape
- **Database (`tracks` table — unchanged v2 schema):**
  - `tracks.title: TEXT NOT NULL` — canonical display name updated in place via `UPDATE tracks SET title = :title WHERE id = :id`.
  - `tracks.local_file_name: TEXT NOT NULL` — UUID-based filename, never modified on rename.
- **Pure-Kotlin Import Rename State (`ImportRenameState`):**
  - `Hidden`
  - `BatchPrompt(tracks: List<TrackEntity>)` *(only when `tracks.size >= 2`)*
  - `Renaming(tracks: List<TrackEntity>, currentIndex: Int, draftTitle: String, showBlankError: Boolean, isBatch: Boolean)`
    - Derived properties: `currentTrack: TrackEntity`, `totalCount: Int`, `stepNumber: Int` (`currentIndex + 1`), `isLastStep: Boolean` (`currentIndex == tracks.lastIndex`), `trimmedTitle: String?`.
- **Existing Track Rename State (`PendingTrackRename`):**
  - `PendingTrackRename(track: TrackEntity, draftTitle: String, showBlankError: Boolean)`
- **`LibraryUiState` additions:**
  - `val importRenameState: ImportRenameState = ImportRenameState.Hidden`
  - `val pendingTrackRename: PendingTrackRename? = null`

### Interface Surface (UI Screens & Service Actions)
- **`LibraryScreen` — `TrackCard`:**
  - Row layout: `[Waveform Icon] [Title (maxLines=2) + Duration] [Edit IconButton] [Delete IconButton] [ChevronRight]`.
  - `contentDescription` for Edit button: `"Rename ${track.title}"`.
- **`LibraryScreen` — Dialogs (all flat M3: `CardSurface`, `tonalElevation = 0.dp`, `1.dp` `OutlineNeutral` border, `RoundedCornerShape(16.dp)`):**
  - **Batch Prompt (`ImportRenameState.BatchPrompt`):**
    - Title: `"Rename imported tracks?"`
    - Body: `"Imported ${tracks.size} tracks. Would you like to customize their display names?"`
    - Confirm: `"Rename tracks"` (`MonasticMaroon` / `Saffron`) · Dismiss: `"Skip all"` (`InkSecondary`).
  - **Import Rename (`ImportRenameState.Renaming`):**
    - Title: `"Name imported track"` (when `!isBatch`) or `"Rename track (${stepNumber} of ${totalCount})"` (when `isBatch`).
    - Body: single-line `OutlinedTextField` pre-filled with `draftTitle`, `isError = showBlankError`, supporting text `"Track name cannot be empty"` when `showBlankError`, plus subtle duration subtitle (`TimerValidation.formatDuration(currentTrack.durationMs)`).
    - Buttons when `!isBatch`: **Save** (enabled when trimmed non-blank) + **Keep default**.
    - Buttons when `isBatch`: **Save & Next** (or **Save** on last step, enabled when trimmed non-blank), **Skip** (advances without saving), and **Skip remaining** (secondary/dismiss action that closes wizard immediately). Dismissing via Back/scrim acts as **Skip remaining** (or **Keep default** when single).
  - **Existing Track Rename (`PendingTrackRename`):**
    - Title: `"Rename track"`
    - Body: single-line `OutlinedTextField` pre-filled with `draftTitle`, inline blank error when `showBlankError`.
    - Buttons: **Save** (enabled when trimmed non-blank) + **Cancel**.
- **`MeditationPlaybackService` Intent Action:**
  - `ACTION_REFRESH_TRACK_TITLE = "com.dhammameditate.action.REFRESH_TRACK_TITLE"`
  - Extras: `EXTRA_TRACK_ID` (`String`), `EXTRA_TRACK_TITLE` (`String`).

### Value Sourcing & Key Invariants
- **Track display name across all screens:** sourced from `tracks.title` in Room SQLite (`TrackDao.observeAll()` / `TrackDao.observeById()`).
- **Active playback & lock-screen title:** initialized from `tracks.title` at playback start and synchronized live via `PlaybackClient.notifyTrackRenamed(trackId, newTitle)` → `MeditationPlaybackService` (`activeQueueEntries`, `exoPlayer.replaceMediaItem`, `PlaybackSnapshot.activeTrackTitle`).
- **Pre-filled default title on import:** sourced from `ImportResult.Success.track.title` (already probed by `AudioFileImporter`: embedded metadata title fallback to filename without extension, fallback `"Untitled Track"`).
- **Invariant — Non-destructive dismiss:** Tracks are already atomically imported and persisted before the rename prompt appears; skipping, canceling, or dismissing any rename dialog NEVER deletes an imported track.
- **Invariant — Zero playback disruption:** `ACTION_REFRESH_TRACK_TITLE` updates `MediaMetadata` in-place via `exoPlayer.replaceMediaItem(index, updatedItem)` inside `withInternalPlayerChange` without calling `prepare()`, `seekTo()`, `pause()`, or modifying `MeditationTimerEngine`.
- **Invariant — Clean UI state:** Only one Library modal (`importRenameState`, `pendingTrackRename`, `pendingTrackDeletion`) is active at a time; starting a new import clears any stale rename dialog.

### Security, Validation & Edge Cases
- **Validation:** `TrackTitleValidation.normalize(raw)` trims leading/trailing Unicode whitespace and rejects empty/blank strings (`null`). `TrackRepository.renameTrack` enforces the same validation at the repository boundary so invalid titles cannot be written to Room.
- **Active Playback Edge Cases:**
  - If the renamed track appears multiple times in a session queue (e.g., repeated in Intro and Main), `MeditationPlaybackService` updates every matching `ActiveQueueEntry` and `MediaItem` index.
  - If the renamed track is currently loaded in `IDLE` or `COMPLETED` state (e.g., after `stopAndReset(clearSelection = false)`), `activeQueueEntries`, `exoPlayer`, and `stateStore` still update cleanly so lock-screen/banner metadata stays fresh on replay.
  - If a track is deleted while a rename dialog is somehow open, `updateTitle` affects 0 rows safely without crashing.
- **Keyboard & UX Polish:** `OutlinedTextField` uses `singleLine = true`, `ImeAction.Done` (or `ImeAction.Next` during non-final batch steps), and `KeyboardActions` wired to save/advance when valid.

## Build Plan
- [x] 1. Create pure-Kotlin `TrackNaming.kt` (`TrackTitleValidation` + `ImportRenameState` transitions) and unit tests in `TrackNamingTest.kt` *(satisfies AC-1, AC-2, AC-3, AC-5)*
- [x] 2. Add `observeById` and `updateTitle` to `TrackDao` and `observeTrack` / `renameTrack` to `TrackRepository` *(satisfies AC-4, AC-5, AC-6)*
- [x] 3. Implement live track-title refresh (`ACTION_REFRESH_TRACK_TITLE`) in `MeditationPlaybackService` and `PlaybackClient.notifyTrackRenamed` *(satisfies AC-6)*
- [x] 4. Update `PlayerViewModel` and `SessionEditorViewModel` to reflect live `TrackEntity` updates from Room *(satisfies AC-6)*
- [x] 5. Wire existing-track rename and post-import single/batch/step-through rename flows in `LibraryViewModel` and flat Material 3 dialogs + inline Edit button in `LibraryScreen` *(satisfies AC-1, AC-2, AC-3, AC-4, AC-5, AC-6)*

## Implementation Notes
- `PlaybackClient.notifyTrackRenamed` updates `PlaybackStateStore.activeTrackTitle` immediately when the renamed track is active, in addition to dispatching `ACTION_REFRESH_TRACK_TITLE` to `MeditationPlaybackService` for in-place `exoPlayer.replaceMediaItem` metadata refresh.
- `TrackRenameDialog` renders as a flat Material 3 `Dialog` (`CardSurface`, `tonalElevation = 0.dp`, `1.dp` `OutlineNeutral` border) shared across single-import, batch step-through, and existing-track rename so 3-action batch controls (`Save & Next`, `Skip`, `Skip remaining`) never cramp on narrow widths.

## Verification Checklist
### Build & Static Checks
- [x] `./gradlew testDebugUnitTest` passes cleanly (including new `TrackNamingTest` covering trimming, blank rejection, Burmese Unicode, single import, batch skip-all, step-through save/skip/skip-remaining)
- [x] `./gradlew assembleDebug` compiles cleanly with zero errors

### System Integration & Wiring
- [x] `TrackDao`, `TrackRepository`, `LibraryViewModel`, `LibraryScreen`, `PlayerViewModel`, `SessionEditorViewModel`, `PlaybackClient`, and `MeditationPlaybackService` are wired end to end with zero unused actions or orphaned states
- [x] All new dialogs in `LibraryScreen` adhere to the flat Monastic Maroon & Saffron design system (`tonalElevation = 0.dp`, `1.dp` `OutlineNeutral` border, `CardSurface`, zero gradients/emojis)

### Functional & Acceptance Checks
- [x] **AC-1:** Importing 1 audio file opens the pre-filled single-track rename dialog; saving updates the Library title immediately; tapping Keep default or dismissing preserves the probed title and keeps the track.
- [x] **AC-2:** Importing 2+ audio files opens the batch prompt; Skip all closes immediately keeping defaults; Rename tracks steps `1 of N` in order with Save & Next / Save, Skip, and Skip remaining working cleanly.
- [x] **AC-3:** Importing a mix of valid and invalid files shows `ImportErrorBanner` for failures and triggers the rename flow only for the succeeded tracks (none if all failed).
- [x] **AC-4:** Tapping the inline Edit pencil button on any `TrackCard` opens the pre-filled Rename dialog and persists the updated title on Save.
- [x] **AC-5:** Entering empty or whitespace-only text disables Save / Save & Next and shows `"Track name cannot be empty"`; leading/trailing spaces are trimmed; duplicate names are allowed; Burmese Unicode titles display cleanly without clipping.
- [x] **AC-6:** Renaming a track updates its title immediately across Library, Session Editor, Sessions, Player, Session Player, `ActiveSessionBanner`, and lock-screen notification metadata without interrupting or resetting active playback.
