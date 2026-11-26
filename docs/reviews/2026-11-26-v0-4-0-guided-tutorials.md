# Code Review: v0.4.0 Guided Tutorials (`2026-11-26`)

- **Project Mode:** Prototype / MVP
- **Scope:** 22 files (uncommitted on `main` — Phases 07 & 08)
- **Reviewer:** Senior Code Reviewer subagent (`pro` model, read-only)
- **Verdict:** Blocked *(All 7 findings resolved & re-verified on 2026-11-26)*

## Summary
The pure-Kotlin tutorial state machine, anchor token registry, flat Material 3 styling, and bilingual English/Burmese localization (with Western digits and `ProvideLocalizedDialogContext`) are cleanly structured and well-tested. However, two real Compose bugs affect the **How to create a session** walkthrough in `SessionEditorScreen`: a `BackHandler` LIFO priority inversion that pops the editor and strands the tutorial overlay on `SessionsScreen`, and a spotlight tooltip placement/scroll gap that can clip the `Next` button on tall editor cards or compact screens. Fixing these two issues (plus the minor state-saving, pointer-input, and spec key-name drift) will make v0.4.0 rock-solid.

## Blockers
### 🔴 `BackHandler` LIFO priority inversion pops `SessionEditorScreen` while stranding the tutorial overlay — `app/src/main/java/com/dhammameditate/ui/editor/SessionEditorScreen.kt:119`
- **Problem:** During `CREATE_SESSION` Step 1 (`SESSIONS_NEW_BUTTON`), `TutorialSpotlightOverlay` (`TutorialSpotlightOverlay.kt:72-74`) is already composed and has registered its `BackHandler` on `OnBackPressedDispatcher`. When tapping `New Session` transitions `destination` to `SessionEditor`, `SessionEditorScreen` enters composition *after* `TutorialSpotlightOverlay` and registers its own `BackHandler(enabled = !uiState.isSaving)` on top of the LIFO dispatcher stack while `TutorialSpotlightOverlay` remains composed.
- **Impact:** Pressing system Back on Steps 2–5 of `CREATE_SESSION` triggers `SessionEditorScreen`'s `onBack()` instead of `TutorialSpotlightOverlay`'s `onSkip()`, bouncing the user back to `SessionsScreen` while `activeTutorial` stays non-null pointing at an off-screen `EDITOR_*` target (breaks Phase 08 AC-4 and Phase 07 AC-6).
- **Suggested Fix:** Gate `SessionEditorScreen`'s `BackHandler` on `enabled = !uiState.isSaving && activeTutorialTargetId == null` (and/or key `TutorialSpotlightOverlay`'s `BackHandler` on `step.targetId` so it stays highest priority while active).

## Major
### 🟠 Tooltip placement can clip the `Next` CTA on tall editor cards and compact screens — `app/src/main/java/com/dhammameditate/ui/tutorial/TutorialSpotlightOverlay.kt:178-218`
- **Problem:** Tooltip placement checks `cutoutRect.center.y >= containerHeightPx / 2f` instead of comparing available space above vs. below (`spaceAbove` vs. `spaceBelow`), and `TutorialTooltipCard`'s inner `Column` (`243-247`) has no `verticalScroll`. On tall cards (`EDITOR_TIMER_CARD`, or `EDITOR_MAIN_CARD` with `emptyLibraryNoteRes` in Burmese / larger font scales), remaining vertical room can drop below the tooltip height, clipping the bottom `Next` button. Also, `TutorialTooltipCard`'s `Surface` does not consume non-button pointer events if it overlaps `cutoutRect`.
- **Impact:** The primary `Next` button can be clipped off-screen on compact viewports, landscape/split-screen, or larger font sizes, blocking step progression.
- **Suggested Fix:** Compare `spaceAbove` vs. `spaceBelow` (accounting for status/nav bar insets), fall back to a centered/safe-edge inset layout when neither side has enough room (e.g., `< 200.dp`), add `.verticalScroll(rememberScrollState())` inside `TutorialTooltipCard`, and consume pointer events on the tooltip `Surface`.

## Minor & Nits
- 🟡 `app/src/main/java/com/dhammameditate/MainActivity.kt:197-199` — `showTutorialTopicList` and `activeTutorial` use `remember` instead of `rememberSaveable` (with a lightweight `listSaver` like `AppDestinationSaver`), so rotating the device mid-walkthrough dismisses the active tutorial.
- 🟡 `app/src/main/java/com/dhammameditate/ui/tutorial/TutorialSpotlightOverlay.kt:122-149` & `MainActivity.kt:407-431` — Keying `pointerInput` on `cutoutRect` cancels/restarts gestures on every `bringIntoView()` scroll frame; capture `cutoutRect` and callbacks via `rememberUpdatedState`, track `down.id`, and guard `onTargetTapped` in `MainActivity.kt` with `if (activeTutorial?.currentStep?.targetId != targetId) return@TutorialSpotlightOverlay` before mutating `destination`.
- 🟡 `docs/spec/dhammameditate-shared-spec.md:497,504` vs. `app/src/main/java/com/dhammameditate/data/preferences/TutorialPreferencesStore.kt:29` — Shared Spec Section 11.1 names the preference key `"has_seen_tutorial_offer"` while `TutorialPreferencesStore.kt` uses `"tutorial_offer_seen"`. Align the spec to `"tutorial_offer_seen"` (and update stale `41`/`211` counts in Plan 08 lines 11, 36, 89 to `39`/`209`).
- ⚪ `app/src/main/java/com/dhammameditate/ui/tutorial/TutorialAnchors.kt:51-57` — When `bounds.width <= 0f || bounds.height <= 0f` (e.g., scrolled out of a clipped container), add `else { registry.remove(targetId, token) }` to avoid retaining stale coordinates.
- ⚪ `app/src/main/java/com/dhammameditate/ui/tutorial/TutorialDialogs.kt:135-147` — Add `Modifier.weight(1f, fill = false)` to the scrollable topic list `Column` so the `Close` footer stays visible on very short/landscape screens as more tutorials are added.

## Strengths
- **Clean pure-Kotlin core & tests:** `TutorialModels`, `TutorialEngine`, and `TutorialRegistry` stay Android-free and well-tested (`TutorialEngineTest`), including explicit `Locale.US` Western-digit verification under `my-MM`.
- **Safe, non-destructive walkthroughs:** `CONFIRM_ONLY` on `LIBRARY_IMPORT_BUTTON` and editor targets prevents accidental OS pickers or fake saves, and `openActivePlayback` clears any active tutorial overlay/dialog cleanly.
- **Race-free anchor token ownership:** `TutorialAnchorRegistry` tracks per-modifier identity tokens (`===`), preventing departing screens from deleting incoming screens' anchors during navigation swaps.

## Resolution (`2026-11-26`)
All 7 findings (1 Blocker, 1 Major, 3 Minor, 2 Nit) have been fixed and re-verified with `./gradlew testDebugUnitTest --rerun-tasks assembleDebug` (exit 0, zero warnings, 57/57 unit tests passing).
