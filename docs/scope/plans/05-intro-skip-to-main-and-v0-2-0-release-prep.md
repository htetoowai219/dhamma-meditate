# 05. Intro Skip to Main & v0.2.0 Release Prep

- **Date:** 2026-11-26
- **Phase:** 05 — Intro Skip to Main & v0.2.0 Release Prep
- **Project Mode:** Prototype / MVP
- **Build Approach:** Journey
- **Status:** Verified
- **Stack & Libraries:** Native Android (Kotlin, Gradle Kotlin DSL, `compileSdk = 35`, `minSdk = 26`), Jetpack Compose + Material 3 (flat Monastic Maroon & Saffron tokens), Jetpack Media3 (`ExoPlayer` + `MediaSessionService`), Kotlin Coroutines + `StateFlow`, JUnit 4 (zero new dependencies, no DB migration)

## Summary
Add an in-app-only **Skip to Main** control to `SessionPlayerScreen` during untimed Intro playback (`INTRO_PLAYING` and `INTRO_PAUSED`) so meditators who are already ready can jump straight past all remaining Intro tracks to Main track 1 and start the meditation countdown immediately. Lock-screen and headset controls remain strictly restricted (no skip/next/previous/seek). Complete v0.2.0 release readiness by expanding unit tests for the skip transition, updating `docs/spec/dhammameditate-shared-spec.md` with v0.2.0 track naming and Intro Skip to Main parity rules, and bumping `app/build.gradle.kts` to `versionCode = 2` and `versionName = "0.2.0"`.

## Rationale & Trade-Offs
- **Chosen direction:** Dedicated `MeditationTimerEngine.skipIntroToMain(nowMs)` + `MeditationPlaybackService.ACTION_SKIP_TO_MAIN` wired to a side-by-side 50/50 secondary row (`Skip to Main` | `Stop & Reset`) beneath `Pause`/`Continue` in `SessionTransportControls`. Keeps bottom bar height constant (`54dp + 10dp + 50dp`) across the Intro → Main boundary and isolates manual skip semantics from natural `onIntroComplete()` boundary handling.
- **Runner-up considered:** Reusing `onIntroComplete()` + calling `resume()` when paused, or stacking a 3rd full-width button. Rejected because `onIntroComplete()` intentionally preserves `PAUSED` when a natural boundary occurs while suppressed/paused, whereas explicit `Skip to Main` must immediately begin Main playback and countdown from both `INTRO_PLAYING` and `INTRO_PAUSED`; a 50/50 secondary row avoids any bottom-bar height jump.
- **Trade-off accepted:** `Skip to Main` lives strictly on `SessionPlayerScreen` (not in `ActiveSessionBanner` or lock-screen notifications), keeping distraction-free minimalism intact.

## Requirements & Acceptance Criteria
- **AC-1 (Intro-Only In-App Visibility):** On `SessionPlayerScreen`, `Skip to Main` is visible **only** when the active session status is `INTRO_PLAYING` or `INTRO_PAUSED` (and the session has at least one Main track). It is hidden in `IDLE`, `PLAYING`, `PAUSED`, `FADING`, and `COMPLETED`, and never appears on Quick-Play `PlayerScreen` or `ActiveSessionBanner`.
- **AC-2 (Immediate Skip from Playing or Paused Intro):** Tapping `Skip to Main` while in `INTRO_PLAYING` or `INTRO_PAUSED` skips all remaining Intro tracks (whether 1 or N Intro tracks), jumps `ExoPlayer` to Main track 1 (`index = activeIntroCount`, position `0L`, `volume = 1.0f`), starts Main audio playback immediately, transitions `MeditationTimerEngine` to `PLAYING` (or `FADING` if `targetDurationMs <= effectiveFadeMs`) at `elapsed = 0`, starts the monotonic countdown tick loop, and updates `SessionPlayerScreen`, `ActiveSessionBanner`, and Media3 lock-screen metadata to Main track 1 without re-triggering the Distraction Check dialog.
- **AC-3 (No-Op Safety & Audio Focus Respect):** Calling `skipIntroToMain` outside `INTRO_PLAYING` / `INTRO_PAUSED` (or when `activeIntroCount <= 0` or `activeMainCount <= 0`) is a safe no-op that never resets or re-anchors an already-running Main countdown. If audio focus is blocked/suppressed when `exoPlayer.play()` is invoked during skip, `MeditationPlaybackService` falls back cleanly via `pauseTimerOnly()`.
- **AC-4 (Lock-Screen Restriction Preserved):** `RestrictedMeditationPlayer` and `MediaSession` continue to expose only Play/Pause and Stop & Reset; `ACTION_SKIP_TO_MAIN` is never added to `MediaSession` custom commands or `ALLOWED_PLAYER_COMMANDS`.
- **AC-5 (Unit Tests):** JUnit 4 tests in `MeditationTimerEngineTest` verify `skipIntroToMain` from `INTRO_PLAYING`, from `INTRO_PAUSED`, with a short `<= 10s` Main target (entering `FADING` immediately), and no-op behavior in `IDLE`, `PLAYING`, `PAUSED`, `FADING`, and `COMPLETED`.
- **AC-6 (Shared Spec & Version Bump):** `docs/spec/dhammameditate-shared-spec.md` is updated to Version `1.1 (Android v0.2.0 Reference Parity)` covering Phase 04 (Custom Track Naming & Import Rename Flow) and Phase 05 (In-App Intro Skip to Main); `app/build.gradle.kts` is updated to `versionCode = 2` and `versionName = "0.2.0"`.

## Technical Design

### Files & Module Layout
- `app/src/main/java/com/dhammameditate/playback/MeditationTimerEngine.kt` — *(modified)* Add `skipIntroToMain(nowMs: Long = clockMs()): TimerTick`.
- `app/src/main/java/com/dhammameditate/playback/MeditationPlaybackService.kt` — *(modified)* Add `ACTION_SKIP_TO_MAIN` constant, `onStartCommand` dispatch, and `skipIntroToMain()` handler.
- `app/src/main/java/com/dhammameditate/playback/PlaybackClient.kt` — *(modified)* Add `skipToMain()` helper sending `ACTION_SKIP_TO_MAIN`.
- `app/src/main/java/com/dhammameditate/ui/sessionplayer/SessionPlayerViewModel.kt` — *(modified)* Add `canSkipToMain` computed property on `SessionPlayerUiState` and `skipToMain()` method.
- `app/src/main/java/com/dhammameditate/ui/sessionplayer/SessionPlayerScreen.kt` — *(modified)* Update `SessionTransportControls` to render a 50/50 secondary button row (`Skip to Main` | `Stop & Reset`) when `canSkipToMain == true`, collapsing back to full-width `Stop & Reset` during Main playback.
- `app/src/test/java/com/dhammameditate/playback/MeditationTimerEngineTest.kt` — *(modified)* Add unit tests for `skipIntroToMain` across Intro playing, Intro paused, short target, and non-Intro no-op states.
- `docs/spec/dhammameditate-shared-spec.md` — *(modified)* Update to Version `1.1` with normative v0.2.0 contracts for track naming/import rename flow and in-app Intro Skip to Main.
- `app/build.gradle.kts` — *(modified)* Bump `versionCode = 2`, `versionName = "0.2.0"`.

### Data Model / State Shape
- No Room schema or entity changes (remains SQLite v2).
- `MeditationTimerEngine.skipIntroToMain(nowMs: Long = clockMs()): TimerTick`:
  - When `status == INTRO_PLAYING || status == INTRO_PAUSED`:
    - `accumulatedElapsedMs = 0L`
    - `segmentStartedAtMs = nowMs`
    - `status = if (targetDurationMs <= FadeCurve.effectiveFadeMs(targetDurationMs)) FADING else PLAYING`
    - returns `tick(nowMs)`
  - In all other states (`IDLE`, `PLAYING`, `PAUSED`, `FADING`, `COMPLETED`): returns `tick(nowMs)` unchanged.
- `SessionPlayerUiState`:
  - `val canSkipToMain: Boolean get() = (status == PlaybackStatus.INTRO_PLAYING || status == PlaybackStatus.INTRO_PAUSED) && (session?.mainItems?.isNotEmpty() == true)`

### Interface Surface (Service & UI)
- **Service Intent Action:** `MeditationPlaybackService.ACTION_SKIP_TO_MAIN = "com.dhammameditate.action.SKIP_TO_MAIN"` (internal app-to-service only; not registered on `MediaSession`).
- **Client API:** `PlaybackClient.skipToMain()` dispatches `ACTION_SKIP_TO_MAIN` via `context.startService(intent)`.
- **`SessionPlayerScreen` (`SessionTransportControls`):**
  - `INTRO_PLAYING` / `INTRO_PAUSED` (`canSkipToMain == true`):
    - Top row: full-width flat Saffron `Button` (`54.dp` height, `RoundedCornerShape(16.dp)`, `0.dp` elevation) — `"Pause"` (playing) or `"Continue"` (paused).
    - Bottom row (`Row`, `Arrangement.spacedBy(10.dp)`):
      - Left (`weight(1f)`, `50.dp` height): flat `OutlinedButton` with `MaroonSurfaceSubtle` container, `1.dp` `LotusGold` border, `PureWhite` content, `RoundedCornerShape(16.dp)`, leading `Icons.Outlined.SkipNext` (`18.dp`) + `"Skip to Main"` (`maxLines = 1, overflow = TextOverflow.Ellipsis`).
      - Right (`weight(1f)`, `50.dp` height): flat `OutlinedButton` with transparent container, `1.dp` `MaroonOutline` border, `PureWhite` content, `RoundedCornerShape(16.dp)`, `"Stop & Reset"` (`maxLines = 1, overflow = TextOverflow.Ellipsis`).
  - `PLAYING` / `PAUSED` / `FADING` (`canSkipToMain == false`):
    - Top row: full-width `"Pause"` / `"Continue"` (`54.dp`).
    - Bottom row: full-width `"Stop & Reset"` (`50.dp`).
  - `IDLE` / `COMPLETED`: full-width `"Start Session"` (`54.dp`).

### Value Sourcing & Key Invariants
- **Main Queue Start Index:** Sourced from `MeditationPlaybackService.activeIntroCount` (0-based index of Main track 1 in `activeQueueEntries` and `exoPlayer`'s playlist) · invariant: `activeIntroCount > 0 && activeMainCount > 0 && activeIntroCount < activeQueueEntries.size && activeIntroCount < exoPlayer.mediaItemCount`.
- **Countdown Anchor on Skip:** Sourced from `SystemClock.elapsedRealtime()` at the instant `skipIntroToMain` executes · invariant: `elapsedMs = 0L`, `remainingMs = targetDurationMs`, `volume = 1.0f` (or `FadeCurve.gain` on subsequent fade ticks if `targetDurationMs <= effectiveFadeMs`).
- **Atomic Player Transition:** `exoPlayer.seekTo(activeIntroCount, 0L)`, `exoPlayer.volume = skippedTick.volume`, and `exoPlayer.play()` execute inside `withInternalPlayerChange { ... }` so `onMediaItemTransition` / `onPlayWhenReadyChanged` callbacks do not double-trigger `onIntroComplete()` or `resumeTimerOnly()`.
- **Flat Visual Compliance:** Zero gradients, zero drop shadows (`0.dp` button elevation), zero `tonalElevation`, zero emojis, Burmese Unicode-safe typography preserved.

### Validation & Edge Cases
- **Multi-track Intro (e.g., 3 Intro tracks):** Tapping `Skip to Main` during Intro track 1, 2, or 3 jumps directly to index `activeIntroCount` (Main track 1), skipping all remaining Intro tracks in one action.
- **Double-tap / Rapid tap:** Second `ACTION_SKIP_TO_MAIN` arrives when `status` is already `PLAYING` or `FADING` and hits the service & timer engine guard, becoming a safe no-op.
- **Audio focus blocked on skip from `INTRO_PAUSED`:** After `withInternalPlayerChange { ... exoPlayer.play() }`, `if (isPlaybackBlockedOrSuppressed()) pauseTimerOnly()` keeps timer and audio strictly synchronized in `PAUSED` at `00:00` of Main track 1 instead of running the timer silently.
- **Session without Intro (`introCount == 0`):** Never enters `INTRO_PLAYING` / `INTRO_PAUSED`, so `canSkipToMain` is always `false`.

## Build Plan
- [x] 1. Add `MeditationTimerEngine.skipIntroToMain(nowMs)` and add unit tests in `MeditationTimerEngineTest` covering `INTRO_PLAYING`, `INTRO_PAUSED`, short `<= 10s` Main duration, and non-Intro no-op safety *(satisfies AC-2, AC-3, AC-5)*
- [x] 2. Add `ACTION_SKIP_TO_MAIN` and `skipIntroToMain()` in `MeditationPlaybackService` and `skipToMain()` in `PlaybackClient`, verifying `RestrictedMeditationPlayer` and `MediaSession` remain unchanged *(satisfies AC-2, AC-3, AC-4)*
- [x] 3. Add `canSkipToMain` and `skipToMain()` in `SessionPlayerViewModel`, and update `SessionTransportControls` in `SessionPlayerScreen` to show the 50/50 secondary row (`Skip to Main` | `Stop & Reset`) only during Intro playback *(satisfies AC-1, AC-2)*
- [x] 4. Update `docs/spec/dhammameditate-shared-spec.md` to Version `1.1 (Android v0.2.0 Reference Parity)` documenting Phase 04 (Custom Track Naming & Import Rename Flow) and Phase 05 (In-App Intro Skip to Main) *(satisfies AC-6)*
- [x] 5. Bump `app/build.gradle.kts` to `versionCode = 2` and `versionName = "0.2.0"` *(satisfies AC-6)*

## Implementation Notes
None — built strictly per plan.

## Verification Checklist
### Build & Static Checks
- [x] `./gradlew testDebugUnitTest --rerun-tasks assembleDebug` exits `0` with all unit tests passing (including new `skipIntroToMain` tests in `MeditationTimerEngineTest`) and debug APK built cleanly.

### System Integration & Wiring
- [x] `SessionPlayerScreen` → `SessionPlayerViewModel.skipToMain()` → `PlaybackClient.skipToMain()` → `MeditationPlaybackService.ACTION_SKIP_TO_MAIN` → `MeditationTimerEngine.skipIntroToMain()` is wired end to end with zero dead handlers or orphaned symbols.
- [x] `RestrictedMeditationPlayer` and `MeditationPlaybackService` `MediaSession` callback/commands contain no skip/next/previous/seek actions (`ACTION_SKIP_TO_MAIN` is strictly in-app).
- [x] `PlayerScreen` (Quick-Play) and `ActiveSessionBanner` do not expose `Skip to Main`.

### Functional & Acceptance Checks
- [x] **AC-1:** `canSkipToMain` is `true` only for `INTRO_PLAYING` / `INTRO_PAUSED` with non-empty `mainItems`, and `SessionTransportControls` renders the 50/50 row (`Skip to Main` | `Stop & Reset`) only when `canSkipToMain` is `true`, collapsing to full-width `Stop & Reset` during Main playback.
- [x] **AC-2:** `skipIntroToMain()` jumps `exoPlayer` to `(activeIntroCount, 0L)`, sets `volume = skippedTick.volume`, calls `exoPlayer.play()` inside `withInternalPlayerChange`, publishes Main track 1 snapshot, and starts `startTickLoop()` from both `INTRO_PLAYING` and `INTRO_PAUSED` without invoking Distraction Check.
- [x] **AC-3:** `skipIntroToMain()` is a strict no-op outside Intro states and handles `isPlaybackBlockedOrSuppressed()` via `pauseTimerOnly()`.
- [x] **AC-4:** Lock-screen/headset controls remain restricted to Play/Pause and Stop & Reset.
- [x] **AC-5:** Unit tests cover `skipIntroToMain` from `INTRO_PLAYING`, `INTRO_PAUSED`, short target `<= 10s`, and non-Intro no-op states.
- [x] **AC-6:** `docs/spec/dhammameditate-shared-spec.md` is updated to Version `1.1` with v0.2.0 track naming + Intro Skip to Main rules, and `app/build.gradle.kts` has `versionCode = 2` and `versionName = "0.2.0"`.
