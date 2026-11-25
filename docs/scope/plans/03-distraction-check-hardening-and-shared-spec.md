# 03. Distraction Check, Hardening & Shared Spec

- **Date:** 2026-11-25
- **Phase:** 03 — Distraction Check, Hardening & Shared Spec
- **Project Mode:** Prototype / MVP
- **Build Approach:** Tracer Bullet
- **Status:** Verified
- **Stack & Libraries:** Native Android (Kotlin, Gradle Kotlin DSL + Version Catalog, `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`), Jetpack Compose + Material 3 (flat Monastic Maroon & Saffron tokens), Jetpack Media3 (`media3-exoplayer`, `media3-session`, `media3-common`), Room SQLite + KSP (v2), Kotlin Coroutines + `StateFlow`, Android system services (`Settings.Global`, `AudioManager`, `NotificationManager`), manual `AppContainer` DI, JUnit 4 + `kotlinx-coroutines-test` (zero new dependencies, zero new runtime permissions)

## Summary
Completes the Android reference app by adding a non-blocking pre-session Distraction Check (detecting Airplane Mode, Do Not Disturb, and ringer silence with a flat Settings-shortcut prompt and `"Continue anyway"`), hardening off-screen playback and audio-interruption edge cases, auditing strict flat Monastic Maroon & Saffron UI / Burmese Unicode rendering across all screens and dialogs, and authoring the single normative cross-platform behavior specification at `docs/spec/dhammameditate-shared-spec.md` for future native iOS (Swift + AVFoundation) parity.

## Rationale & Trade-Offs
- **Chosen direction:** Split distraction detection into a pure-Kotlin `DistractionPolicy` (unit-testable on JVM) and a thin `AndroidDistractionChecker` reading zero-permission system state (`AIRPLANE_MODE_ON`, `AudioManager.ringerMode`, `NotificationManager.currentInterruptionFilter`), surfaced through one reusable flat `DistractionCheckDialog` with live `ON_RESUME` re-checking. Document all cross-platform behavior in one single-file normative spec (`docs/spec/dhammameditate-shared-spec.md`).
- **Runner-up considered:** Requiring strict Airplane Mode only (annoying for users who rely on DND/Silent), adding a persisted `"Don't show again"` toggle (adds settings UI clutter for MVP when `"Continue anyway"` is already 1 tap), or offering multiple Settings buttons (clutters a minimalist prompt).
- **Trade-off accepted:** Android cannot toggle Airplane Mode programmatically and OEMs vary on DND sub-settings, so `"Open Settings"` uses an intent fallback chain (`ACTION_AIRPLANE_MODE_SETTINGS` → `ACTION_WIRELESS_SETTINGS` → `ACTION_SETTINGS`) and never blocks playback if the user taps `"Continue anyway"`. On return from Settings, the prompt updates live to `"Ready"` rather than auto-starting audio unannounced.

## Requirements & Acceptance Criteria
- **AC-1 (Pre-Session Distraction Check Policy & Triggers):** Tapping fresh `"Start Session"` (from `IDLE` or `COMPLETED`) on either `SessionPlayerScreen` or `PlayerScreen` (Library Quick-Play) inspects device readiness without requesting new permissions. A device is **ready** iff `isAirplaneModeOn || isDndActive || ringerMode == SILENT`. When ready, playback starts immediately with no prompt. When not ready (Airplane Mode is off and ringer is `NORMAL`, `VIBRATE`, or `UNKNOWN`), a flat `DistractionCheckDialog` opens instead. Tapping `"Continue"` from `PAUSED` or `INTRO_PAUSED` never triggers the prompt.
- **AC-2 (Flat Distraction Prompt, Settings Shortcut & Live Re-Check):** `DistractionCheckDialog` renders in strict flat styling (`#F8F8F9` surface, `tonalElevation = 0.dp`, `1dp` `#D4D4D8` border, `16dp` radius, zero gradients, zero emojis) showing the current state summary (e.g., `"Airplane Mode is off and your ringer is on."` or `"...vibration is on."`), a concise recommendation to enable Airplane Mode or silence notifications, a single `"Open Settings"` action (launching `ACTION_AIRPLANE_MODE_SETTINGS` with fallback to `ACTION_WIRELESS_SETTINGS` → `ACTION_SETTINGS`), and `"Continue anyway"` (which proceeds through the existing `POST_NOTIFICATIONS` check on API 33+ and starts playback). Returning from Settings refreshes readiness via `ON_RESUME`: if now ready, the dialog calmly updates to `"Device is quiet and ready for meditation."` with a primary Saffron `"Start Session"` CTA.
- **AC-3 (Playback & Interruption Hardening):** `MeditationPlaybackService` handles off-screen runs, pause/continue cycles, and audio interruptions cleanly with fresh `00:00` resets:
  - Calling `pauseSession()` or `pauseTimerOnly()` at or past `targetDurationMs` transitions cleanly via `finishSessionAtZero(...)` (`volume = 0.0f`, player paused and rewound to `(0, 0L)`, status `COMPLETED`) instead of leaving stale volume or non-zero position.
  - Transient audio focus loss (phone calls, speech-duck pauses via `AUDIO_CONTENT_TYPE_SPEECH`) pauses both audio and timer and resumes only if the user did not manually pause or stop during the interruption, restoring exact `FadeCurve` volume when resuming inside the fade window.
  - Becoming noisy (headphones unplugged) and permanent audio focus loss pause both audio and timer and stay paused until the user explicitly taps `"Continue"`.
  - Stopping or completing always leaves the player ready for a fresh `00:00` / index `0` / `1.0f` volume start and releases active playback wake locks while paused/idle/completed.
- **AC-4 (Flat Minimal UI & Burmese Unicode Polish):** All screens, cards, bottom sheets, and dialogs (`LibraryScreen`, `SessionsScreen`, `SessionEditorScreen`, `SessionPlayerScreen`, `PlayerScreen`, `ActiveSessionBanner`, `DistractionCheckDialog`) enforce flat Material 3 styling: `tonalElevation = 0.dp` on all `AlertDialog`, `ModalBottomSheet`, and `NavigationBar` surfaces; zero gradients (`Brush.*`); zero drop shadows; zero emojis in UI copy; and Burmese-safe `1.45x–1.5x` line heights with ellipsis/wrap safety on all user-authored titles and session names.
- **AC-5 (Hardened Unit Test Coverage):** JVM unit tests verify `DistractionPolicy` across all combinations of Airplane Mode, DND, Silent, Vibrate, Normal, and Unknown ringer states, plus `MeditationTimerEngine` / `FadeCurve` long screen-off runs (`60+` minutes), repeated pause/continue cycles across Intro and Main, pause-at-exact-zero completion, mid-fade pause/resume gain continuity, and exact `FadeCurve` reference vectors matching the shared spec.
- **AC-6 (Normative Shared Spec for iOS Parity):** `docs/spec/dhammameditate-shared-spec.md` exists and serves as the complete, unambiguous source of truth for the future native iOS (Swift + AVFoundation) port—matching the implemented Android behavior for data model & constraints, supported audio formats & import rules, strict timer validation, 7-state playback FSM, exponential fade-out equation + numeric reference vectors, interruption & screen-off rules, distraction check contract (including Android vs. iOS platform capability notes), and flat design tokens / Burmese Unicode typography.

## Technical Design

### Files & Module Layout
- `app/src/main/java/com/dhammameditate/playback/DistractionCheck.kt` — Pure-Kotlin `RingerState`, `DistractionSnapshot`, `DistractionEvaluation`, `DistractionPolicy.evaluate(...)`, `DistractionChecker` interface, `AndroidDistractionChecker`, and `DistractionSettingsNavigator.openAirplaneOrDeviceSettings(context)` (new).
- `app/src/main/java/com/dhammameditate/ui/common/DistractionCheckDialog.kt` — Reusable flat dialog composable with `ON_RESUME` lifecycle observer for live readiness refresh (new).
- `app/src/main/java/com/dhammameditate/di/AppContainer.kt` — Expose `val distractionChecker: DistractionChecker` (modified).
- `app/src/main/java/com/dhammameditate/MainActivity.kt` — Pass `container.distractionChecker` to `SessionPlayerScreen` and `PlayerScreen` (modified).
- `app/src/main/java/com/dhammameditate/ui/sessionplayer/SessionPlayerScreen.kt` — Gate fresh `"Start Session"` on `distractionChecker.checkStatus()`; show `DistractionCheckDialog` when `!isReady` before `POST_NOTIFICATIONS` and `viewModel.startSession()` (modified).
- `app/src/main/java/com/dhammameditate/ui/player/PlayerScreen.kt` — Same fresh `"Start Session"` distraction check gate for Library Quick-Play (modified).
- `app/src/main/java/com/dhammameditate/playback/MeditationPlaybackService.kt` — Route `COMPLETED` results from `pauseSession()` / `pauseTimerOnly()` into `finishSessionAtZero(...)`, harden `onPlaybackStateChanged(STATE_ENDED)` / interruption / fresh-start invariants (modified).
- `app/src/main/java/com/dhammameditate/ui/library/LibraryScreen.kt` — Set `tonalElevation = 0.dp` (and clean flat dialog chrome) on track delete confirmation `AlertDialog`; audit Burmese-safe typography (modified).
- `app/src/main/java/com/dhammameditate/ui/sessions/SessionsScreen.kt` — Set `tonalElevation = 0.dp` (and clean flat dialog chrome) on session delete confirmation `AlertDialog`; audit Burmese-safe typography (modified).
- `app/src/test/java/com/dhammameditate/playback/DistractionPolicyTest.kt` — Exhaustive JVM unit tests for `DistractionPolicy.evaluate(...)` (new).
- `app/src/test/java/com/dhammameditate/playback/MeditationTimerEngineTest.kt` — Add long-run (60m+), multi-pause/resume (Intro + Main + mid-fade), and pause-at-zero boundary tests (modified).
- `app/src/test/java/com/dhammameditate/playback/FadeCurveTest.kt` — Pin exact numeric test vectors (`p = 0.0, 0.25, 0.5, 0.75, 1.0`, `3000ms` short-target fade) matching `docs/spec/dhammameditate-shared-spec.md` (modified).
- `docs/spec/dhammameditate-shared-spec.md` — Normative cross-platform behavior specification for Android reference & future iOS port (new).

### Data Model / State Shape

No Room schema migration is required (`AppDatabase` stays at `version = 2`).

**Pure-Kotlin Distraction Check Domain (`DistractionCheck.kt`)**
- `enum class RingerState { SILENT, VIBRATE, NORMAL, UNKNOWN }`
- `data class DistractionSnapshot(val isAirplaneModeOn: Boolean, val isDndActive: Boolean, val ringerState: RingerState)`
- `data class DistractionEvaluation(val snapshot: DistractionSnapshot, val isReady: Boolean, val statusSummary: String, val guidanceText: String)`
- `interface DistractionChecker { fun evaluate(): DistractionEvaluation }`

**Readiness Evaluation Table (`DistractionPolicy.evaluate`)**
| `isAirplaneModeOn` | `isDndActive` | `ringerState` | `isReady` | `statusSummary` |
|---|---|---|---|---|
| `true` | any | any | `true` | `"Airplane Mode is on. Ready for quiet meditation."` |
| `false` | `true` | any | `true` | `"Do Not Disturb is active. Ready for quiet meditation."` |
| `false` | `false` | `SILENT` | `true` | `"Phone is silenced. Ready for quiet meditation."` |
| `false` | `false` | `NORMAL` | `false` | `"Airplane Mode is off and your ringer is on."` |
| `false` | `false` | `VIBRATE` | `false` | `"Airplane Mode is off and vibration is on."` |
| `false` | `false` | `UNKNOWN` | `false` | `"Airplane Mode is off and alerts may interrupt your session."` |

### Interface Surface (UI Screens, Service & Spec Contract)

1. **`DistractionCheckDialog` (`ui/common/DistractionCheckDialog.kt`)**
   - Rendered using `Dialog` + flat `Surface` (`color = CardSurface #F8F8F9`, `shape = RoundedCornerShape(16.dp)`, `border = BorderStroke(1.dp, OutlineNeutral #D4D4D8)`, `tonalElevation = 0.dp`, `shadowElevation = 0.dp`) so there is guaranteed zero Material 3 tonal tint or shadow.
   - **Header:** Outlined icon (`Icons.Outlined.AirplanemodeActive` in `#5E2418` when not ready; `Icons.Outlined.CheckCircle` in `#C98A1E` when ready) + Title (`"Silence Distractions?"` when `!isReady`, `"Ready to Meditate"` when `isReady`).
   - **Body:** `evaluation.statusSummary` (`bodyMedium`, `InkPrimary`) + `evaluation.guidanceText` (`bodySmall`, `InkSecondary`: `"Turn on Airplane Mode or silence alerts so calls and notifications do not break your stillness."`).
   - **Actions when `!evaluation.isReady`:**
     - Primary / outlined action: `"Open Settings"` (`MonasticMaroon` outline / label) — calls `onOpenSettings()` without dismissing the dialog so `ON_RESUME` re-checks when the user returns.
     - Proceed action: `"Continue anyway"` (`Saffron` filled `50dp` flat button or clear primary CTA) — dismisses dialog and triggers `onProceedToStart()`.
     - Subtle dismiss: tapping outside or Back cancels start cleanly (stays in `IDLE`/`COMPLETED`).
   - **Actions when `evaluation.isReady` (after returning from Settings):**
     - Primary Saffron filled button: `"Start Session"` — dismisses dialog and triggers `onProceedToStart()`.
     - Subtle `"Cancel"` text button.
   - **Lifecycle Refresh:** Uses `LocalLifecycleOwner` + `DisposableEffect` observing `Lifecycle.Event.ON_RESUME` while the dialog is visible to invoke `onRecheck()` automatically on return from Settings.

2. **Start Session Flow in `SessionPlayerScreen` & `PlayerScreen`**
   - User taps `"Start Session"` (only visible in `IDLE` or `COMPLETED`; never `"Continue"`):
     1. Evaluate `val eval = distractionChecker.evaluate()`.
     2. If `eval.isReady` → run `proceedToNotificationAndStart()`.
     3. If `!eval.isReady` → store `pendingDistractionEval = eval` to show `DistractionCheckDialog`.
     4. In `DistractionCheckDialog`:
        - `"Open Settings"` → `DistractionSettingsNavigator.openAirplaneOrDeviceSettings(context)`.
        - `ON_RESUME` → `pendingDistractionEval = distractionChecker.evaluate()`.
        - `"Continue anyway"` / `"Start Session"` → `pendingDistractionEval = null; proceedToNotificationAndStart()`.
        - Dismiss / Back → `pendingDistractionEval = null` (does not start playback).
   - `proceedToNotificationAndStart()` preserves the existing API 33+ one-time `POST_NOTIFICATIONS` check followed by `viewModel.startSession()`.

3. **Hardened `MeditationPlaybackService` Behavior**
   - `pauseSession()` and `pauseTimerOnly()`: if `timerEngine.pause(now)` returns `PlaybackStatus.COMPLETED`, call `finishSessionAtZero(pausedTick)` immediately (sets `volume = 0.0f`, pauses, rewinds to `(0, 0L)`, publishes `COMPLETED`) rather than only calling `exoPlayer.pause()`.
   - `onPlaybackStateChanged(Player.STATE_ENDED)`: completes cleanly only when status is active-playing (`INTRO_PLAYING`, `PLAYING`, `FADING`); ignores stale `STATE_ENDED` events while `IDLE`, `COMPLETED`, `PAUSED`, or `INTRO_PAUSED`.
   - `finishSessionAtZero(...)`: cancels `tickJob`, resets `currentQueueIndex = 0`, sets `exoPlayer.volume = 0.0f`, pauses & seeks `(0, 0L)` (releasing `WAKE_MODE_LOCAL` lock), and publishes `COMPLETED`.
   - Confirm `startQuickTrackSession` and `startSavedSession` always enforce the Start-Fresh invariant (`index = 0`, `position = 0L`, `volume = 1.0f`) regardless of prior `COMPLETED`, `IDLE`, or error state.

4. **Shared Cross-Platform Spec (`docs/spec/dhammameditate-shared-spec.md`)**
   - Normative sections:
     1. Product Principles & Explicit Non-Goals
     2. Data Model, Persistence & Cascade / Auto-Clamp Rules (`tracks`, `sessions`, `session_tracks`, app-private storage)
     3. Supported Audio Formats & Import Validation Contract (Android + iOS AVFoundation notes, e.g., OGG container caveat)
     4. Strict Timer Validation & Snapping Rules (`10m/15m/30m/45m/60m/Full`, custom minutes, `Needs Main Track` guard)
     5. Two-Zone Playback State Machine (`IDLE`, `INTRO_PLAYING`, `INTRO_PAUSED`, `PLAYING`, `PAUSED`, `FADING`, `COMPLETED`), Monotonic Clock Rule, and Restricted Lock-Screen Controls (`MediaSession` ↔ `MPRemoteCommandCenter`)
     6. Exponential Fade-Out Specification (`FADE_DURATION_MS = 10_000`, `DECAY_K = 3.5`, `50ms` cadence) + Exact Reference Test Vectors
     7. Screen-Off Reliability & Audio Interruption Contract (calls, speech-ducking pause, route-change/headphone unplug, `STATE_ENDED` drift safety, fresh `00:00` reset)
     8. Pre-Session Distraction Check Contract (logical readiness predicate + Android zero-permission signal mapping + iOS platform capability guidance)
     9. Flat Visual Design Tokens & Burmese Unicode Typography Spec (exact hex palette, radii, outlines, `1.45x–1.5x` line heights, zero gradients/shadows/emojis)

### Value Sourcing & Key Invariants
- `DistractionSnapshot.isAirplaneModeOn`: sourced from `Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0` (fallback `false` on exception).
- `DistractionSnapshot.ringerState`: sourced from `AudioManager.ringerMode` (`RINGER_MODE_SILENT` → `SILENT`, `RINGER_MODE_VIBRATE` → `VIBRATE`, `RINGER_MODE_NORMAL` → `NORMAL`, else `UNKNOWN`).
- `DistractionSnapshot.isDndActive`: sourced from `NotificationManager.currentInterruptionFilter` (`filter != INTERRUPTION_FILTER_ALL && filter != INTERRUPTION_FILTER_UNKNOWN`, wrapped in `runCatching { ... }.getOrDefault(false)`).
- `DistractionEvaluation.isReady`: derived purely by `DistractionPolicy.evaluate(snapshot)` as `isAirplaneModeOn || isDndActive || ringerState == RingerState.SILENT`.
- `Settings` intent target: resolved in order `Settings.ACTION_AIRPLANE_MODE_SETTINGS` → `Settings.ACTION_WIRELESS_SETTINGS` → `Settings.ACTION_SETTINGS`; launched with `Intent.FLAG_ACTIVITY_NEW_TASK` only when non-Activity context, otherwise standard Activity launch, wrapped in `runCatching` so missing Settings activities on exotic devices never crash.
- **Invariant — Never Block Meditation:** Distraction Check is strictly advisory; `"Continue anyway"` is always available when `!isReady` and immediately proceeds to start playback.
- **Invariant — Zero New Permissions:** Do not add `ACCESS_NOTIFICATION_POLICY` or `MODIFY_AUDIO_SETTINGS` to `AndroidManifest.xml`.
- **Invariant — Spec-Code Parity:** Every numeric constant, state name, formula, and reference vector in `docs/spec/dhammameditate-shared-spec.md` must match `FadeCurve.kt`, `TimerValidation.kt`, `MeditationTimerEngine.kt`, `AudioFileImporter.kt`, `DistractionCheck.kt`, and `ui/theme/Color.kt` + `Type.kt`.

### Security, Validation & Edge Cases
- **OEM System Service Quirks:** Reading `NotificationManager.currentInterruptionFilter` or `AudioManager.ringerMode` on restricted/emulated devices may throw `SecurityException` or return `null` service; `AndroidDistractionChecker` wraps each read independently in `runCatching` with safe conservative fallbacks (`isAirplaneModeOn = false`, `isDndActive = false`, `ringerState = UNKNOWN`) so the app never crashes.
- **Settings Activity Unavailability:** If an emulator or stripped ROM lacks `ACTION_AIRPLANE_MODE_SETTINGS`, `DistractionSettingsNavigator` falls back cleanly to `ACTION_WIRELESS_SETTINGS` then `ACTION_SETTINGS` (and no-ops safely if none resolve).
- **Double-Tap / Config Change Safety:** Opening `DistractionCheckDialog` holds lightweight boolean/state in composition; if the screen rotates while the dialog is open, either dismiss safely or re-evaluate via `distractionChecker.evaluate()` using `rememberSaveable` boolean `showDistractionDialog` so the prompt persists cleanly across rotation and re-reads live device state.
- **Pause/Interruption At Exact Zero:** Handled in `MeditationPlaybackService.pauseSession()` and `pauseTimerOnly()` by checking `pausedTick.status == PlaybackStatus.COMPLETED` and routing through `finishSessionAtZero(pausedTick)`.

## Build Plan
- [x] 1. Implement `DistractionCheck.kt` (`RingerState`, `DistractionSnapshot`, `DistractionEvaluation`, `DistractionPolicy`, `DistractionChecker`, `AndroidDistractionChecker`, `DistractionSettingsNavigator`), expose `distractionChecker` in `AppContainer`, and add `DistractionPolicyTest.kt` *(satisfies AC-1, AC-5)*
- [x] 2. Build flat `DistractionCheckDialog.kt` with live `ON_RESUME` re-check and wire it into `SessionPlayerScreen.kt`, `PlayerScreen.kt`, and `MainActivity.kt` on fresh Start Session only *(satisfies AC-1, AC-2)*
- [x] 3. Harden `MeditationPlaybackService.kt` (`pauseSession`/`pauseTimerOnly` completion boundary, interruption/focus/noisy and `STATE_ENDED` safety, fresh `00:00` reset invariants) and expand `MeditationTimerEngineTest.kt` and `FadeCurveTest.kt` with long-run, multi-pause, pause-at-zero, and spec vector tests *(satisfies AC-3, AC-5)*
- [x] 4. Audit and polish flat UI & Burmese Unicode compliance across all screens and dialogs (`tonalElevation = 0.dp` on `LibraryScreen` & `SessionsScreen` `AlertDialog`s, verify zero gradients, zero shadows, zero emojis, Burmese-safe `1.45x–1.5x` line heights) *(satisfies AC-4)*
- [x] 5. Write the normative cross-platform specification at `docs/spec/dhammameditate-shared-spec.md` verified against all implemented Android constants, state transitions, and fade test vectors *(satisfies AC-6)*

## Implementation Notes
- `DistractionSettingsNavigator` attempts `startActivity` across the `ACTION_AIRPLANE_MODE_SETTINGS` → `ACTION_WIRELESS_SETTINGS` → `ACTION_SETTINGS` chain inside `runCatching` (checking `ContextWrapper` chain for Activity context) to stay resilient under Android 11+ package visibility without adding `<queries>` or permissions to `AndroidManifest.xml`.
- `MeditationPlaybackService.pauseSession()` also clears `exoPlayer.playWhenReady` if an explicit Pause arrives while transiently suppressed by audio focus loss, ensuring playback will not auto-resume after the interruption ends.
- `SessionPlayerScreen` and `PlayerScreen` auto-dismiss `DistractionCheckDialog` if playback becomes active externally (e.g., via lock-screen controls) while the prompt is open.

## Verification Checklist
### Build & Static Checks
- [x] `./gradlew testDebugUnitTest` passes with 0 failures (including `DistractionPolicyTest`, expanded `MeditationTimerEngineTest`, `FadeCurveTest`, and `TimerValidationTest`)
- [x] `./gradlew assembleDebug` succeeds cleanly with 0 errors
- [x] `./gradlew lintDebug` passes with 0 errors (and no unexpected permission or deprecation issues)
- [x] Static scan of `app/src/main/java/` confirms zero `Brush.` gradients, zero emojis in UI strings, `tonalElevation = 0.dp` on all dialogs/sheets/nav bar, and zero new permissions added to `AndroidManifest.xml`

### System Integration & Wiring
- [x] `AppContainer.distractionChecker` is wired through `MainActivity` to both `SessionPlayerScreen` and `PlayerScreen` with zero orphaned files or unused parameters
- [x] `DistractionCheckDialog` registers and cleanly disposes its `LifecycleEventObserver` (`ON_RESUME`) when shown/dismissed
- [x] `docs/spec/dhammameditate-shared-spec.md` constants, state names, fade vectors, and color tokens match the live Android codebase

### Functional & Acceptance Checks
- [x] **AC-1:** Starting a saved session or Quick-Play track from `IDLE` or `COMPLETED` while Airplane Mode is off and ringer is `NORMAL` or `VIBRATE` opens `DistractionCheckDialog`; starting while Airplane Mode, DND, or `SILENT` ringer is active bypasses the prompt; tapping `"Continue"` from `PAUSED` or `INTRO_PAUSED` never shows the prompt
- [x] **AC-2:** Tapping `"Open Settings"` launches Airplane Mode / Wireless / Device Settings safely; toggling Airplane Mode or silencing and returning to the app updates the open dialog via `ON_RESUME` to ready state with `"Start Session"`; tapping `"Continue anyway"` proceeds through notification check (API 33+) and starts playback immediately
- [x] **AC-3:** Unit tests and code inspection prove `pauseSession()`/`pauseTimerOnly()` at or past `targetDurationMs` cleanly calls `finishSessionAtZero(...)`, transient interruptions pause/resume in sync (restoring exact fade volume), noisy/permanent focus loss stays paused, and Stop/Completion always resets cleanly to `00:00` / index `0` / `1.0f` on next start
- [x] **AC-4:** All screens and dialogs render flat Monastic Maroon & Saffron tokens with `tonalElevation = 0.dp`, `1dp` outlines, zero gradients/emojis, and Burmese Unicode titles/session names render without diacritic clipping (`1.45x–1.5x` line heights)
- [x] **AC-5:** `DistractionPolicyTest`, `MeditationTimerEngineTest` (60m+ run, multi-pause/resume across Intro/Main/Fade, pause-at-zero), and `FadeCurveTest` (pinned spec reference vectors) all pass
- [x] **AC-6:** `docs/spec/dhammameditate-shared-spec.md` completely specifies data model, audio formats, timer validation, 7-state FSM, fade curve + test vectors, interruption rules, distraction check contract (Android + iOS notes), and flat design tokens
