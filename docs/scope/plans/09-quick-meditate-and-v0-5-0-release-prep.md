# 09. Quick Meditate & v0.5.0 Release Prep

- **Date:** 2026-11-28
- **Phase:** 09 — Quick Meditate & v0.5.0 Release Prep
- **Project Mode:** Prototype / MVP
- **Build Approach:** Journey
- **Status:** Verified
- **Stack & Libraries:** Native Android (Kotlin, Gradle Kotlin DSL, `compileSdk = 35`, `minSdk = 26`), Jetpack Compose + Material 3 (flat Monastic Maroon & Saffron tokens, `tonalElevation = 0.dp`, zero gradients/emojis), Jetpack Media3 (`ExoPlayer` + `MediaSessionService`), Room SQLite (KSP), `SharedPreferences` (`dhamma_meditate_prefs`), Kotlin Coroutines + `StateFlow`, manual `AppContainer` DI, JUnit 4

## Summary
Adds a one-off **Quick Meditate** flow launched directly from the Home (`Sessions`) screen so users can pick optional Intro audio, required Main audio, and a validated timer, then tap **Start Meditation** to run the Distraction Check and enter playback immediately without creating a saved session template. Last selections persist in `SharedPreferences` (gracefully dropping deleted Library tracks and clamping the timer) with a top-bar **Reset** action, all new UI copy is localized in English and user-supplied Burmese (zero auto-translation), `docs/spec/dhammameditate-shared-spec.md` is updated to `v1.4`, and Android is bumped to `v0.5.0` (`versionCode = 5`).

## Rationale & Trade-Offs
- **Chosen direction:** Store the single remembered Quick Meditate draft in existing `dhamma_meditate_prefs` (`SharedPreferences`) and play it through `MeditationPlaybackService` + `SessionPlayerScreen` using a reserved ephemeral session ID (`"__quick_meditate__"`). Reuses `QueueZoneCard`, `SessionTimerCard`, and `TrackPickerSheetContent` from `SessionEditorScreen` with zero Room schema migration and full parity for Intro Skip to Main, lock-screen controls, `ActiveSessionBanner`, and Distraction Check.
- **Runner-up considered:** Adding an `isEphemeral` column to Room's `sessions` table (`MIGRATION_2_3`) and filtering it out of every `SessionDao` query, or building a separate player screen. Skipped to avoid unnecessary schema churn and duplicated playback UI.
- **Trade-off accepted:** Only the most recent Quick Meditate selection is remembered (no multi-history), and Quick Meditate intentionally omits a "Save as Session" conversion or dedicated guided tutorial (both deferred in `SCOPE.md`).

## Requirements & Acceptance Criteria
- **AC-1 (Home Entry & Visual Hierarchy):** `SessionsScreen` displays a 50/50 side-by-side bottom CTA row beneath `TutorialLauncherButton`: **Quick Meditate** (filled Saffron) on the left and **New Session** (flat outlined Monastic Maroon) on the right. `TutorialTargetId.SESSIONS_NEW_BUTTON` remains attached to **New Session** so the existing "How to create a session" tutorial works unchanged, and both buttons wrap cleanly up to 2 lines in Burmese.
- **AC-2 (Dedicated Setup Screen & Reset):** Tapping **Quick Meditate** opens `QuickMeditateScreen` with a Maroon top bar (**Back**, `"Quick Meditate"`, right-aligned **Reset** text action), optional Intro Queue card, required Main Queue card, validated Meditation Timer card (no Session Name field), and a single full-width Saffron **Start Meditation** bottom button. **Reset** is enabled only when at least one track or timer selection exists and clears both in-memory state and persisted preferences back to empty.
- **AC-3 (Immediate Start, Distraction Check & Ephemeral Playback):** Tapping **Start Meditation** runs `rememberSessionStartLauncher` (Distraction Check dialog + one-time API 33+ notification prompt), starts two-zone playback via `MeditationPlaybackService` (`ACTION_START_QUICK_MEDITATE`), and navigates immediately to `QuickMeditatePlayer` (reusing `SessionPlayerScreen` titled `"Quick Meditate"`). No row is ever written to Room's `sessions` table.
- **AC-4 (Full Playback Parity & Banner/Notification Return):** While Quick Meditate is active, Intro-to-Main transition, in-app **Skip to Main**, Pause/Continue, Stop & Reset, smooth exponential fade-out, restricted lock-screen controls, live track-title updates, and `ActiveSessionBanner` all behave identically to saved sessions. Tapping `ActiveSessionBanner` or the lock-screen notification returns to `QuickMeditatePlayer`; tapping the player's Edit icon (when idle/completed) returns to `QuickMeditateSetup`; tapping Back returns to Home (`Sessions`).
- **AC-5 (Remember Last Picks & Deleted-Track Resilience):** Selections persist automatically to `QuickMeditateStore` on change and restore on next open. If any previously selected track was deleted from the Library, it is silently dropped while preserving order of remaining tracks, and `targetDurationMs` is reconciled/clamped against the remaining `mainTotalDurationMs` (resetting to `0` if Main is now empty).
- **AC-6 (English & User-Supplied Burmese Localization):** All new Quick Meditate strings are extracted to `res/values/strings.xml` and localized in `res/values-my/strings.xml` using **only user-supplied Burmese translations** (zero auto-translation). Switching `English` ↔ `မြန်မာ` updates `QuickMeditateScreen`, `QuickMeditatePlayer`, `ActiveSessionBanner`, and active playback notification metadata immediately, keeping Western digits (`0-9`) for timers/durations.
- **AC-7 (Shared Spec & Version Bump):** `docs/spec/dhammameditate-shared-spec.md` is updated to `v1.4` documenting Quick Meditate behavior, draft persistence/reconciliation, ephemeral playback rules, and the updated English ↔ Burmese glossary; `app/build.gradle.kts` is bumped to `versionCode = 5` and `versionName = "0.5.0"`.

## Technical Design

### Files & Module Layout
- `app/src/main/java/com/dhammameditate/data/preferences/QuickMeditateStore.kt` *(new)* — `QuickMeditateDraft` data class, pure-Kotlin codec & `reconcile(tracksById)` logic, and `SharedPreferences`-backed `QuickMeditateStore` exposing `StateFlow<QuickMeditateDraft>`.
- `app/src/test/java/com/dhammameditate/data/preferences/QuickMeditateDraftTest.kt` *(new)* — Pure JUnit 4 tests for ID-list encoding/decoding, deleted-track pruning, duplicate-track preservation, timer clamping/defaulting, and empty/reset states.
- `app/src/main/java/com/dhammameditate/di/AppContainer.kt` *(modified)* — Provide `quickMeditateStore: QuickMeditateStore`.
- `app/src/main/java/com/dhammameditate/playback/PlaybackClient.kt` *(modified)* — Add `QUICK_MEDITATE_SESSION_ID` constant and `startQuickMeditate(introTrackIds, mainTrackIds, targetDurationMs)`.
- `app/src/main/java/com/dhammameditate/playback/MeditationPlaybackService.kt` *(modified)* — Handle `ACTION_START_QUICK_MEDITATE`, factor out shared `startTwoZoneSession(...)` used by both saved and Quick Meditate sessions, and update `refreshLocalizedMediaMetadata()` to re-localize `activeSessionName` when `activeSessionId == QUICK_MEDITATE_SESSION_ID`.
- `app/src/main/java/com/dhammameditate/ui/editor/SessionEditorScreen.kt` *(modified)* — Change visibility of `QueueZoneCard`, `SessionTimerCard`, and `TrackPickerSheetContent` from `private` to `internal` (zero behavior change to Session Editor).
- `app/src/main/java/com/dhammameditate/ui/quickmeditate/QuickMeditateViewModel.kt` *(new)* — Manages Quick Meditate draft state, live `trackRepository.observeTracks()` resolution, queue add/reorder/remove, timer presets/Full/custom minutes, auto-persistence to `QuickMeditateStore`, **Reset**, and `startQuickMeditate()`.
- `app/src/main/java/com/dhammameditate/ui/quickmeditate/QuickMeditateScreen.kt` *(new)* — Setup UI reusing `QueueZoneCard`, `SessionTimerCard`, `TrackPickerSheetContent`, and `rememberSessionStartLauncher`.
- `app/src/main/java/com/dhammameditate/ui/sessionplayer/SessionPlayerViewModel.kt` *(modified)* — When `sessionId == QUICK_MEDITATE_SESSION_ID`, observe `quickMeditateStore.draft` + `trackRepository.observeTracks()` to emit an ephemeral `SessionWithQueues` and dispatch `playbackClient.startQuickMeditate(...)` on restart.
- `app/src/main/java/com/dhammameditate/ui/sessionplayer/SessionPlayerScreen.kt` *(modified)* — Render localized `R.string.quick_meditate_title` when `session.id == QUICK_MEDITATE_SESSION_ID` (so language switches reflect immediately) and route unavailable-state CTA cleanly when Main is empty.
- `app/src/main/java/com/dhammameditate/ui/sessions/SessionsScreen.kt` *(modified)* — Add `onQuickMeditate: () -> Unit` and render 50/50 bottom CTA row (`Quick Meditate` filled Saffron + `New Session` outlined Maroon with `TutorialTargetId.SESSIONS_NEW_BUTTON`).
- `app/src/main/java/com/dhammameditate/MainActivity.kt` *(modified)* — Add `AppDestination.QuickMeditateSetup` and `AppDestination.QuickMeditatePlayer` (including `AppDestinationSaver` entries), route `activePlaybackDestination` for `QUICK_MEDITATE_SESSION_ID` to `QuickMeditatePlayer`, and connect callbacks.
- `app/src/main/res/values/strings.xml` & `app/src/main/res/values-my/strings.xml` *(modified)* — Add ~7 new English + user-supplied Burmese strings.
- `docs/spec/dhammameditate-shared-spec.md` *(modified)* — Update spec to `v1.4` (Android `v0.5.0` parity).
- `app/build.gradle.kts` *(modified)* — Bump `versionCode = 5` and `versionName = "0.5.0"`.

### Data Model / State Shape
- **`QuickMeditateDraft`** (pure Kotlin):
  - `introTrackIds: List<String> = emptyList()`
  - `mainTrackIds: List<String> = emptyList()`
  - `targetDurationMs: Long = 0L`
  - `val hasAnySelection: Boolean get() = introTrackIds.isNotEmpty() || mainTrackIds.isNotEmpty() || targetDurationMs > 0L`
- **`SharedPreferences` (`dhamma_meditate_prefs`):**
  - `quick_meditate_intro_ids`: comma-separated `String` (empty string = `emptyList()`)
  - `quick_meditate_main_ids`: comma-separated `String` (empty string = `emptyList()`)
  - `quick_meditate_target_duration_ms`: `Long` (default `0L`)
- **`ResolvedQuickMeditateDraft`** (pure Kotlin result of `reconcile(tracksById)`):
  - `introTracks: List<TrackEntity>`
  - `mainTracks: List<TrackEntity>`
  - `targetDurationMs: Long`
  - `wasCapped: Boolean`
  - `droppedMissingTracks: Boolean`
- **Reserved Ephemeral Session ID:** `PlaybackClient.QUICK_MEDITATE_SESSION_ID = "__quick_meditate__"` (never collides with UUID v4 saved session IDs).

### Interface Surface (UI Screens & Navigation)
| Destination / Surface | Entry | Key Controls & States | Exit / Transitions |
|---|---|---|---|
| `SessionsScreen` (Home) | Default tab | 50/50 bottom bar: **Quick Meditate** (Saffron filled) · **New Session** (Maroon outlined, tutorial anchor). `ActiveSessionBanner` when active. | **Quick Meditate** → `QuickMeditateSetup`; **New Session** → `SessionEditor`; Banner (when `QUICK_MEDITATE_SESSION_ID`) → `QuickMeditatePlayer`. |
| `QuickMeditateSetup` | From Home or Player Edit | Top bar: Back, `"Quick Meditate"`, right **Reset** `TextButton` (enabled iff `hasAnySelection`). Optional active-playback notice if Quick Meditate is currently playing. Intro Queue card, Main Queue card, Timer card, Track Picker bottom sheet. Bottom full-width Saffron **Start Meditation** button (enabled iff `mainItems.isNotEmpty() && targetDurationMs in 1..mainTotal && !hasInvalidCustomInput`). | **Back** → Home (`Sessions`); **Reset** → clears draft in-place (does not kill active playback); **Start Meditation** → Distraction Check → `startQuickMeditate()` → `QuickMeditatePlayer`. |
| `QuickMeditatePlayer` | Immediately after Start, or via Banner/Notification | Reuses `SessionPlayerScreen` with localized `"Quick Meditate"` title, zone pill, countdown, progress bar, read-only queue overview, Pause/Continue, **Skip to Main** (during Intro), Stop & Reset. | **Back** → Home (`Sessions`); **Edit** icon (enabled when idle/completed) → `QuickMeditateSetup`. |

### Value Sourcing & Key Invariants
- **Quick Meditate display title:** sourced from `R.string.quick_meditate_title` (never hardcoded, never stored as a static English string in Room); re-resolved dynamically on language change in Compose and in `MeditationPlaybackService.refreshLocalizedMediaMetadata()`.
- **Displayed timer & preset selection in `QuickMeditateScreen`:** sourced from `QuickMeditateDraft.targetDurationMs` reconciled against `mainItems.sumOf { it.track.durationMs }` via `TimerValidation`; invariant: `targetDurationMs` is always `0L` when `mainItems` is empty, and always `1L..mainTotalDurationMs` before `Start Meditation` is enabled.
- **No accidental template creation:** `QuickMeditateViewModel` and `MeditationPlaybackService` must **never** call `sessionRepository.saveSession(...)`.
- **Deleted-track resilience:** `QuickMeditateDraft.reconcile(tracksById)` drops missing IDs on load/observation; if a track currently playing in Quick Meditate is deleted from the Library, existing `PlaybackClient.stopIfActiveTrackAndWait(trackId)` stops playback because `activeTrackIds` contains all Intro + Main track IDs.
- **Reset isolation:** Tapping **Reset** on `QuickMeditateSetup` clears `QuickMeditateStore` and resets the setup UI to empty without interrupting any currently playing session.

### Security, Validation & Edge Cases
- **Validation:** Identical to `SessionEditorViewModel` — Main queue requires `>= 1` track, custom minutes must be integer `>= 1`, values exceeding `mainTotalDurationMs` snap to `mainTotalDurationMs` with inline `capNotice`, and removing a Main track below the timer snaps the timer down via `TimerValidation.reconcileTimerAfterMainChange`.
- **Empty Library:** Track Picker sheet shows the existing empty-library message; **Start Meditation** stays cleanly disabled until at least 1 Main track is added.
- **Process death / configuration change:** `AppDestination.QuickMeditateSetup` and `AppDestination.QuickMeditatePlayer` are saved/restored in `AppDestinationSaver`, and draft selections survive process death via `QuickMeditateStore`.
- **Active Quick Meditate replacement:** If Quick Meditate is already playing and the user re-opens `QuickMeditateSetup`, changes tracks/timer, and taps **Start Meditation**, `MeditationPlaybackService` cleanly cancels any pending start and replaces the active queue starting fresh from `00:00`.

## Build Plan
- [x] 1. Implement `QuickMeditateDraft`, pure-Kotlin codec & deleted-track/timer reconciler, and `QuickMeditateStore` (`dhamma_meditate_prefs`), wire into `AppContainer`, and add unit tests in `QuickMeditateDraftTest.kt` *(satisfies AC-5)*
- [x] 2. Add `QUICK_MEDITATE_SESSION_ID` and `startQuickMeditate(...)` in `PlaybackClient`, refactor shared `startTwoZoneSession(...)` in `MeditationPlaybackService` for `ACTION_START_QUICK_MEDITATE`, and refresh localized Quick Meditate title on language switch *(satisfies AC-3, AC-4, AC-6)*
- [x] 3. Promote `QueueZoneCard`, `SessionTimerCard`, and `TrackPickerSheetContent` to `internal` in `SessionEditorScreen.kt`, then build `QuickMeditateViewModel.kt` and `QuickMeditateScreen.kt` (top-bar **Reset**, auto-persisted draft, bottom **Start Meditation** wired to `rememberSessionStartLauncher`) *(satisfies AC-2, AC-3, AC-5)*
- [x] 4. Extend `SessionPlayerViewModel` & `SessionPlayerScreen` for `QUICK_MEDITATE_SESSION_ID`, update `SessionsScreen` bottom bar to the 50/50 **Quick Meditate** / **New Session** row (preserving `TutorialTargetId.SESSIONS_NEW_BUTTON`), and wire `AppDestination.QuickMeditateSetup` & `AppDestination.QuickMeditatePlayer` in `MainActivity` *(satisfies AC-1, AC-3, AC-4)*
- [x] 5. Add new English strings to `res/values/strings.xml`, collect Burmese translations interactively from the user in 1 batch (zero auto-translation), and write them to `res/values-my/strings.xml` *(satisfies AC-6)*
- [x] 6. Update `docs/spec/dhammameditate-shared-spec.md` to `v1.4` and bump `app/build.gradle.kts` to `versionCode = 5` and `versionName = "0.5.0"` *(satisfies AC-7)*

## Implementation Notes
- Passed `PlaybackStatus.IDLE` to `rememberSessionStartLauncher` in `QuickMeditateScreen.kt` so tapping **Start Meditation** while a previous Quick Meditate session is active still runs the Distraction Check before replacing playback.
- Handled `PlaybackClient.QUICK_MEDITATE_SESSION_ID` in `MeditationPlaybackService.handleExternalPlay()` so lock-screen Play after Stop/Complete restarts Quick Meditate cleanly from active queue metadata.

## Verification Checklist
### Build & Static Checks
- [x] `./gradlew testDebugUnitTest` passes with zero failures (including new `QuickMeditateDraftTest`)
- [x] `./gradlew assembleDebug` compiles cleanly with zero errors

### System Integration & Wiring
- [x] `AppContainer.quickMeditateStore` is wired into `QuickMeditateViewModel` and `SessionPlayerViewModel`
- [x] `AppDestination.QuickMeditateSetup` and `AppDestination.QuickMeditatePlayer` are saved/restored in `AppDestinationSaver`, and `activePlaybackDestination()` routes `QUICK_MEDITATE_SESSION_ID` to `QuickMeditatePlayer`
- [x] Zero orphaned files, unused imports, or dead handlers; `SessionRepository.saveSession` is never called by Quick Meditate

### Functional & Acceptance Checks
- [x] **AC-1:** Home (`SessionsScreen`) shows **Quick Meditate** (filled Saffron) and **New Session** (outlined Maroon) side-by-side 50/50 beneath `TutorialLauncherButton`; running the "How to create a session" tutorial still spotlights **New Session** cleanly
- [x] **AC-2:** Tapping **Quick Meditate** opens `QuickMeditateScreen` without a session name field; **Reset** in top bar is disabled when empty and wipes all selections when tapped
- [x] **AC-3:** Selecting Intro (optional), Main (required), and a validated timer, then tapping **Start Meditation** triggers Distraction Check, starts playback immediately on `QuickMeditatePlayer`, and adds zero rows to the saved Sessions list
- [x] **AC-4:** During Quick Meditate playback, Intro-to-Main transition, in-app **Skip to Main**, Pause/Continue, Stop & Reset, lock-screen controls, `ActiveSessionBanner` tap-to-return, and idle Edit → `QuickMeditateSetup` all work end to end
- [x] **AC-5:** Reopening Quick Meditate restores last selections; deleting a referenced track from the Library drops it gracefully and clamps/resets the timer on next open
- [x] **AC-6:** Switching `English` ↔ `မြန်မာ` updates all new Quick Meditate UI, player title, banner, and notification text cleanly using only user-supplied Burmese copy, with Western digits (`0-9`) for all timers/durations
- [x] **AC-7:** `docs/spec/dhammameditate-shared-spec.md` is updated to `v1.4` and `app/build.gradle.kts` has `versionCode = 5` and `versionName = "0.5.0"`
