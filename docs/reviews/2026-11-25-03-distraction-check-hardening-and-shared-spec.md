# Code Review: 03. Distraction Check, Hardening & Shared Spec (`2026-11-25`)

- **Project Mode:** Prototype / MVP
- **Scope:** 15 files (uncommitted working tree on `main`: 10 modified, 5 untracked)
- **Reviewer:** Fresh read-only review subagent (`Senior Code Reviewer`)
- **Verdict:** Changes requested

## Summary
Phase 03 cleanly delivers the pre-session Distraction Check, Media3 playback/interruption hardening, flat UI/Burmese typography audit, expanded JVM tests, and normative cross-platform shared spec with zero new dependencies or permissions. Core architecture and spec-to-code parity are strong, with no blockers. Two `Major` UI/lifecycle edge cases in `DistractionCheckDialog` (process-death restoration race + Binder IPC during composition, and missing vertical scroll on landscape/large font scales) should be fixed before merge.

## Major

### 🟠 Restored `DistractionCheckDialog` races async loading after process death and runs Binder IPC in Composition — `app/src/main/java/com/dhammameditate/ui/sessionplayer/SessionPlayerScreen.kt:100-107, 325-345` & `app/src/main/java/com/dhammameditate/ui/player/PlayerScreen.kt:100-107, 311-331`
- **Problem:**
  1. `showDistractionDialog` is saved via `rememberSaveable`. If Android kills the app process while the user is away in system Settings toggling Airplane Mode, returning restores `showDistractionDialog = true` while `SessionPlayerViewModel` / `PlayerViewModel` is still loading (`isLoading = true`, `canStart = false`). Because the dialog is gated only on `if (showDistractionDialog)`, it displays immediately with an enabled `"Start Session"` / `"Continue anyway"` button before the session or track is loaded.
  2. `val currentEval = distractionEvaluation ?: distractionChecker.evaluate()` executes synchronously inside the `@Composable` body, performing three Android system Binder IPC reads (`Settings.Global`, `NotificationManager`, `AudioManager`) during composition, followed by redundant re-evaluations from `LaunchedEffect(showDistractionDialog)` and `DistractionCheckDialog`'s `DisposableEffect(lifecycleOwner)` (`ON_RESUME` replays immediately when attaching to a resumed lifecycle).
- **Impact:** Tapping `"Start Session"` right after returning from Settings following background process death can silently dismiss the dialog and drop playback start (`viewModel.startSession()` returns early when `session == null` / `track == null`). Running unremembered system IPC directly in composition also violates Compose side-effect hygiene.
- **Suggested Fix:** Gate dialog display on `if (showDistractionDialog && uiState.canStart)` (and `uiState.canStartSession` in `PlayerScreen`) and `distractionEvaluation != null`; populate `distractionEvaluation` only from `requestStartSession`, `LaunchedEffect(showDistractionDialog)` when `null`, and genuine post-pause `ON_RESUME` transitions (tracking `hasPaused` inside `DistractionCheckDialog`'s `DisposableEffect`, or using a single effect for refresh).

### 🟠 `DistractionCheckDialog` is not vertically scrollable, clipping actions on landscape or large font scales — `app/src/main/java/com/dhammameditate/ui/common/DistractionCheckDialog.kt:84-206`
- **Problem:** The dialog's root `Column` stacks a `48dp` icon, title, two body paragraphs, two `50dp` buttons, and a `"Cancel"` `TextButton` (~`435dp` at 1.0x scale, `500+dp` at 1.3x–1.5x accessibility font scale) without `.verticalScroll(rememberScrollState())`.
- **Impact:** In phone landscape orientation or with Android large font / display size enabled, `"Continue anyway"` and `"Cancel"` clip off-screen and become unreachable, violating the "Never Block Meditation" invariant.
- **Suggested Fix:** Add `.verticalScroll(rememberScrollState())` to the dialog's `Column` at `DistractionCheckDialog.kt:85-88`.

## Minor & Nits
- 🟡 `app/src/main/java/com/dhammameditate/ui/sessionplayer/SessionPlayerScreen.kt:335-339` & `app/src/main/java/com/dhammameditate/ui/player/PlayerScreen.kt:321-325` — Guard `onProceedToStart` with `if (!showDistractionDialog) return` so a same-frame double-tap cannot invoke `notificationPermissionLauncher.launch(...)` or `viewModel.startSession()` twice.
- 🟡 `app/src/main/java/com/dhammameditate/playback/DistractionCheck.kt:155-159` — `ContextWrapper.baseContext` is `@Nullable` in Android; change `Context.findActivity()` to `Context?.findActivity()` so an unattached `ContextWrapper(null)` returns `null` instead of throwing an NPE.
- 🟡 `app/src/main/java/com/dhammameditate/playback/MeditationPlaybackService.kt:120-158` — `onMediaItemTransition` should ignore `PlaybackStatus.IDLE` and `PlaybackStatus.COMPLETED` before mutating `currentQueueIndex` so a late async transition from ExoPlayer after `stopAndReset()` or `finishSessionAtZero()` cannot re-run completion or publish a stale index.
- 🟡 `app/src/main/java/com/dhammameditate/ui/library/LibraryScreen.kt:201-208`, `app/src/main/java/com/dhammameditate/ui/sessions/SessionsScreen.kt:167-174`, `docs/spec/dhammameditate-shared-spec.md:363, 374` — Delete confirmation `AlertDialog`s have `tonalElevation = 0.dp` but no `1dp` `OutlineNeutral` border, unlike `DistractionCheckDialog` and Section 9.2's `"dialog ... borders"` note; either align the border chrome or clarify borderless confirmation alerts in the spec.
- 🟡 `docs/spec/dhammameditate-shared-spec.md:224` — Remove `ProcessInfo.processInfo.systemUptime` from the allowed iOS monotonic clock list (the same sentence immediately notes `systemUptime` pauses during deep sleep) and specify `mach_continuous_time()` / Swift `ContinuousClock` unambiguously.
- ⚪ `app/src/main/java/com/dhammameditate/playback/DistractionCheck.kt:34-58` & `app/src/main/java/com/dhammameditate/ui/common/DistractionCheckDialog.kt:111-134` — Ready state repeats the word "ready" across three stacked text lines (`"Ready to Meditate"` / `"Airplane Mode is on. Ready for quiet meditation."` / `"Device is quiet and ready for meditation."`); matches spec table verbatim, optional tightening.
- ⚪ `app/src/main/java/com/dhammameditate/ui/sessionplayer/SessionPlayerScreen.kt:100-146` & `app/src/main/java/com/dhammameditate/ui/player/PlayerScreen.kt:100-147` — Both player screens duplicate ~45 lines of Distraction Check + `POST_NOTIFICATIONS` wiring; extracting a shared composable helper would keep future fixes in one place.

## Strengths
- **Clean domain/platform split & exhaustive unit tests:** Pure-Kotlin `DistractionPolicy` enabled `DistractionPolicyTest` to verify all 16 combinations of `(isAirplaneModeOn, isDndActive, ringerState)` on JVM, while `AndroidDistractionChecker` isolates each zero-permission system read in `runCatching`.
- **Thoughtful Media3 interruption & boundary hardening:** Clearing `exoPlayer.playWhenReady` on explicit pause during transient audio-focus suppression (`MeditationPlaybackService.kt:677-683`), routing pause-at-zero through `finishSessionAtZero(...)`, and restoring exact `FadeCurve` volume on resume/transitions show solid mastery of Media3 focus semantics.
- **High-fidelity spec-to-code parity:** All 18 color tokens, 9 typography specs, 7 FSM states, 7+5 pinned `FadeCurve` reference vectors, SQLite v2 schema, and pragmatic iOS platform caveats (OGG container decoding, Focus/Silent switch limits) align accurately across code and `docs/spec/dhammameditate-shared-spec.md`.

## Resolution
All 2 Major items, all 5 Minor items, and the shared-launcher Nit were resolved on `2026-11-25` and re-verified with `./gradlew testDebugUnitTest assembleDebug lintDebug` (30/30 tests passed, 0 lint errors/Kotlin issues):
- Extracted `rememberSessionStartLauncher` into `DistractionCheckDialog.kt` (used by both `SessionPlayerScreen` and `PlayerScreen`), gating restored dialogs on `canStart && currentEval != null`, removing Binder IPC from Composition, tracking `hasPaused` in the `LifecycleEventObserver` to skip initial attach replay, and adding same-frame double-tap guards.
- Added `.verticalScroll(rememberScrollState())` to `DistractionCheckDialog`.
- Updated `DistractionSettingsNavigator.findActivity()` to a nullable `Context?` receiver.
- Guarded `MeditationPlaybackService.onMediaItemTransition` against `IDLE` / `COMPLETED` states before mutating `currentQueueIndex`.
- Added `1dp` `OutlineNeutral` borders to the delete confirmation `AlertDialog`s in `LibraryScreen` and `SessionsScreen`.
- Clarified iOS monotonic clock guidance in `docs/spec/dhammameditate-shared-spec.md` (`mach_continuous_time()` / Swift `ContinuousClock`, explicitly warning against `CACurrentMediaTime()` / `systemUptime`).
- Preserved ready-state copy verbatim to maintain exact parity with AC-2 and `DistractionPolicyTest`.
