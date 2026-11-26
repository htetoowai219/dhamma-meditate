# 08. "How to Create a Session" Walkthrough, Burmese Copy & v0.4.0 Release Prep

- **Date:** 2026-11-26
- **Phase:** 08 — "How to Create a Session" Walkthrough, Burmese Copy & v0.4.0 Release Prep
- **Project Mode:** Prototype / MVP
- **Build Approach:** Tracer Bullet
- **Status:** Verified
- **Stack & Libraries:** Native Android (Kotlin, Gradle Kotlin DSL, `compileSdk = 35`, `minSdk = 26`), Jetpack Compose + Material 3 (flat Monastic Maroon & Saffron tokens, custom zero-dependency `TutorialSpotlightOverlay`), Jetpack Media3, Room SQLite (KSP), `SharedPreferences`, Kotlin Coroutines + `StateFlow`, manual `AppContainer` DI, JUnit 4

## Summary
Expands the Phase 07 guided tutorial foundation with the 5-step **How to create a session** walkthrough across live `SessionsScreen` and `SessionEditorScreen` surfaces—auto-scrolling off-screen editor cards into view and showing a non-blocking empty-Library note when the Library has 0 tracks. Localizes all 39 tutorial strings (Phase 07 + Phase 08) into English and Burmese (`English` / `မြန်မာ`) using user-reviewed Burmese translations, updates the cross-platform specification (`docs/spec/dhammameditate-shared-spec.md`) to v1.3, and bumps the Android release to `v0.4.0` (`versionCode = 4`).

## Rationale & Trade-Offs
- **Chosen direction:** Extend the existing data-driven `TutorialRegistry` and `TutorialSpotlightOverlay` with `TutorialId.CREATE_SESSION`, intermediate `Next` confirmation labels, `BringIntoViewRequester` auto-scrolling in `SessionEditorScreen`, and an optional `emptyLibraryNoteRes` field on `TutorialStep`.
- **Runner-up considered:** Adding a blocking pre-flight dialog when the Library has 0 tracks, or building a separate Session Editor tutorial overlay. Skipped because a pre-flight gate interrupts exploration and a custom overlay would duplicate Phase 07's spotlight engine.
- **Trade-off accepted:** Inside `SessionEditorScreen`, steps 2–5 use `CONFIRM_ONLY` (`Next` / `Got it`) rather than requiring the user to type a name, pick tracks, and save—keeping the walkthrough 100% non-destructive and safe on an empty Library while leaving the user on the live editor ready to build a real session.

## Requirements & Acceptance Criteria
- **AC-1:** Selecting **How to create a session** from the tutorial topic list starts on `SessionsScreen` and runs a 5-step spotlight walkthrough: (1) `New Session` button (`TAP_TARGET` → opens live `SessionEditorScreen`), (2) `Intro Queue` card (`Next`), (3) `Main Queue` card (`Next`), (4) `Meditation Timer` card (`Next`), and (5) `Save Session` button (`Got it`, mentioning Session Name at the top).
- **AC-2:** Inside `SessionEditorScreen`, advancing to any scrollable target (`Intro Queue`, `Main Queue`, `Meditation Timer`) automatically scrolls that card into view so the spotlight cutout and tooltip remain clearly visible without manual scrolling.
- **AC-3:** When the user's Library has 0 tracks, Step 3 (`Main Queue`) displays a subtle flat inline note inside the tooltip reminding them to import at least one audio file in the Library tab first—without blocking the walkthrough or creating synthetic tracks/sessions.
- **AC-4:** Finishing (`Got it`) or skipping (`Skip` / system back) the walkthrough inside `SessionEditorScreen` dismisses the overlay and leaves the user on `SessionEditorScreen` with zero fake saves.
- **AC-5:** All 41 tutorial UI strings (Phase 07 + Phase 08) exist in both `res/values/strings.xml` and `res/values-my/strings.xml` using strictly user-provided Burmese translations (zero auto-translation), switching live between `English` and `မြန်မာ` with Western digits (`0-9`) for step numbers/counts and clean Burmese wrapping.
- **AC-6:** `docs/spec/dhammameditate-shared-spec.md` is updated to **v1.3** (Android v0.4.0 Reference Parity) with the Guided Tutorials contract and canonical Burmese tutorial glossary, and `app/build.gradle.kts` is bumped to `versionCode = 4`, `versionName = "0.4.0"`.

## Technical Design

### Files & Module Layout
- `app/src/main/java/com/dhammameditate/ui/tutorial/TutorialModels.kt` — Add `TutorialId.CREATE_SESSION`, 5 editor/session `TutorialTargetId` entries, and optional `@StringRes val emptyLibraryNoteRes: Int? = null` on `TutorialStep` *(modified)*.
- `app/src/main/java/com/dhammameditate/ui/tutorial/TutorialRegistry.kt` — Register `createSession` (5 steps) and expose `listOf(addAudio, createSession)` in `all` *(modified)*.
- `app/src/main/java/com/dhammameditate/ui/tutorial/TutorialSpotlightOverlay.kt` — Accept `hasLibraryTracks: Boolean`; render `Next` (`tutorial_action_next`) for non-final `CONFIRM_ONLY` steps and `Got it` (`tutorial_action_got_it`) for the final step; show `step.emptyLibraryNoteRes` banner when `!hasLibraryTracks` *(modified)*.
- `app/src/main/java/com/dhammameditate/MainActivity.kt` — Observe `hasLibraryTracks` from `container.trackRepository.observeTracks()`, pass `hasLibraryTracks` to `TutorialSpotlightOverlay`, map `TutorialTargetId.SESSIONS_NEW_BUTTON` in `onTargetTapped` to open a new `AppDestination.SessionEditor`, and pass `activeTutorialTargetId` to `SessionEditorScreen` *(modified)*.
- `app/src/main/java/com/dhammameditate/ui/sessions/SessionsScreen.kt` — Attach `Modifier.tutorialTarget(TutorialTargetId.SESSIONS_NEW_BUTTON)` to the `New Session` button *(modified)*.
- `app/src/main/java/com/dhammameditate/ui/editor/SessionEditorScreen.kt` — Accept `activeTutorialTargetId: TutorialTargetId? = null`; attach `BringIntoViewRequester` + `Modifier.tutorialTarget(...)` to Intro Queue, Main Queue, Meditation Timer cards, and `Modifier.tutorialTarget(...)` to the anchored Save button; trigger `bringIntoView()` via `LaunchedEffect(activeTutorialTargetId)` *(modified)*.
- `app/src/test/java/com/dhammameditate/ui/tutorial/TutorialEngineTest.kt` — Add unit tests verifying `TutorialRegistry.createSession` structure, `emptyLibraryNoteRes` placement, and full 5-step transition flow *(modified)*.
- `app/src/main/res/values/strings.xml` — Add 17 new English strings (`tutorial_action_next`, `tutorial_hint_next`, `tutorial_create_session_*`) bringing total keys to 209 *(modified)*.
- `app/src/main/res/values-my/strings.xml` — Add all 39 user-reviewed Burmese `tutorial_*` strings (22 from Phase 07 + 17 from Phase 08) for 100% 1-to-1 key parity *(modified)*.
- `docs/spec/dhammameditate-shared-spec.md` — Bump to v1.3, add Section 11 (Guided Tutorials), and update Section 10.4 key count and Burmese glossary *(modified)*.
- `app/build.gradle.kts` — Bump `versionCode = 4` and `versionName = "0.4.0"` *(modified)*.

### Data Model / State Shape
- **`TutorialId`:** `ADD_AUDIO`, `CREATE_SESSION`.
- **`TutorialTargetId`:**
  - `NAV_LIBRARY_TAB`, `LIBRARY_IMPORT_BUTTON` (Phase 07)
  - `SESSIONS_NEW_BUTTON`, `EDITOR_INTRO_CARD`, `EDITOR_MAIN_CARD`, `EDITOR_TIMER_CARD`, `EDITOR_SAVE_BUTTON` (Phase 08)
- **`TutorialStep`:**
  - `targetId: TutorialTargetId`
  - `@StringRes titleRes: Int`
  - `@StringRes bodyRes: Int`
  - `@StringRes tapHintRes: Int?`
  - `interaction: TutorialStepInteraction`
  - `@StringRes emptyLibraryNoteRes: Int? = null`

### Interface Surface (UI Screens & Walkthrough Sequence)
| Step | Target ID | Screen | Interaction | Primary Action | Empty-Library Behavior |
|---|---|---|---|---|---|
| 1 / 5 | `SESSIONS_NEW_BUTTON` | `SessionsScreen` | `TAP_TARGET` | Tap highlighted `New Session` button → opens `SessionEditorScreen` | Same |
| 2 / 5 | `EDITOR_INTRO_CARD` | `SessionEditorScreen` | `CONFIRM_ONLY` | `Next` (or tap cutout); auto-scrolls card into view | Same |
| 3 / 5 | `EDITOR_MAIN_CARD` | `SessionEditorScreen` | `CONFIRM_ONLY` | `Next` (or tap cutout); auto-scrolls card into view | Shows flat `SaffronTintSurface` inline note in tooltip reminding user to import audio in Library |
| 4 / 5 | `EDITOR_TIMER_CARD` | `SessionEditorScreen` | `CONFIRM_ONLY` | `Next` (or tap cutout); auto-scrolls card into view | Same |
| 5 / 5 | `EDITOR_SAVE_BUTTON` | `SessionEditorScreen` | `CONFIRM_ONLY` | `Got it` (or tap cutout); closes overlay and stays on `SessionEditorScreen` | Same (Save remains disabled until Main track + name are set; zero fake saves) |

### Value Sourcing & Key Invariants
- **`hasLibraryTracks`:** Sourced live from `container.trackRepository.observeTracks().map { it.isNotEmpty() }` collected in `MainActivity.kt` · invariant: updates reactively from Room without extra queries.
- **Step progress & counts:** Formatted via `TutorialEngine.formatStepNumbers` / `TutorialEngine.formatCount` (`Locale.US`) · invariant: always Western digits `0-9` in both English and Burmese.
- **Burmese strings (`values-my/strings.xml`):** Sourced exclusively from user replies across 2 interactive chat batches during `/develop` · invariant: zero machine/auto-translated Burmese text, 1-to-1 key parity with `values/strings.xml`.
- **Non-destructive tutorial guarantee:** `TutorialSpotlightOverlay` intercepts taps on `CONFIRM_ONLY` targets (including `EDITOR_SAVE_BUTTON`) and never invokes `viewModel.saveSession(...)` or OS pickers.

### Security, Validation & Edge Cases
- **Auto-scroll timing:** `SessionEditorScreen` runs `LaunchedEffect(activeTutorialTargetId)` and invokes `bringIntoView()` on the matching `BringIntoViewRequester`. Because `Modifier.tutorialTarget` updates `TutorialAnchorRegistry` inside `onGloballyPositioned`, the spotlight cutout tracks the card live as scrolling settles.
- **Active playback interruption:** Existing `openActivePlayback` guard in `MainActivity.kt` clears `activeTutorial` if the user enters active playback via notification or banner.
- **Burmese layout safety:** Verify `TutorialLauncherButton`, `TutorialWelcomeDialog`, `TutorialTopicListDialog`, and `TutorialSpotlightOverlay` (`Step X / N` badge, `Skip`, `Next`, `Got it`, and `emptyLibraryNoteRes` banner) wrap cleanly without clipping or overlapping on narrow widths.

## Build Plan
- [x] 1. Extend `TutorialModels.kt`, `TutorialRegistry.kt`, `app/src/main/res/values/strings.xml`, and `TutorialEngineTest.kt` with `TutorialId.CREATE_SESSION` (5 steps, `emptyLibraryNoteRes`, `tutorial_action_next`) and unit tests *(satisfies AC-1, AC-3)*
- [x] 2. Update `TutorialSpotlightOverlay.kt` (`Next` vs `Got it` label + conditional `emptyLibraryNoteRes` banner) and wire `hasLibraryTracks`, `SESSIONS_NEW_BUTTON` navigation, and `activeTutorialTargetId` in `MainActivity.kt` *(satisfies AC-1, AC-3, AC-4)*
- [x] 3. Attach `Modifier.tutorialTarget(...)` in `SessionsScreen.kt` and `SessionEditorScreen.kt` and add `BringIntoViewRequester` auto-scrolling for Intro, Main, and Timer cards *(satisfies AC-1, AC-2, AC-4)*
- [x] 4. Collect all 41 Burmese tutorial strings from the user via 2 interactive chat batches (zero auto-translation), write them into `app/src/main/res/values-my/strings.xml`, and audit Burmese wrapping / Western digits (`0-9`) *(satisfies AC-5)*
- [x] 5. Update `docs/spec/dhammameditate-shared-spec.md` to v1.3 (Guided Tutorials contract + Burmese glossary) and bump `app/build.gradle.kts` to `versionCode = 4`, `versionName = "0.4.0"` *(satisfies AC-6)*

## Implementation Notes
- Deduplicated the repeated `"Tap Next to continue."` hint across `CREATE_SESSION` steps 2–4 into a single shared key `tutorial_hint_next` (39 total `tutorial_*` keys across Phase 07 + Phase 08; 209 total keys per locale file).
- Used `heightIn(min = 46.dp)` on `TutorialTooltipCard`'s primary button so taller Burmese glyphs never clip.
- At the user's explicit request during `/develop`, drafted the 39 Burmese tutorial strings matching existing canonical terms in `values-my/strings.xml` and applied all user review edits (items 8, 23, 31, 36) before writing them to `res/values-my/strings.xml`.

## Verification Checklist
### Build & Static Checks
- [x] `./gradlew testDebugUnitTest --rerun-tasks assembleDebug` passes with exit code 0, zero compiler warnings, and all unit tests green (including expanded `TutorialEngineTest`)
- [x] Exact 1-to-1 string key parity between `app/src/main/res/values/strings.xml` and `app/src/main/res/values-my/strings.xml` (209 keys each, zero missing keys, zero unused `tutorial_*` keys)

### System Integration & Wiring
- [x] `TutorialRegistry.all` exposes both `ADD_AUDIO` and `CREATE_SESSION`, and every `TutorialTargetId` has a live `Modifier.tutorialTarget(...)` attachment and `MainActivity.kt` `onTargetTapped` branch
- [x] `SessionEditorScreen` receives `activeTutorialTargetId` from `MainActivity.kt` and wires `BringIntoViewRequester` for `EDITOR_INTRO_CARD`, `EDITOR_MAIN_CARD`, and `EDITOR_TIMER_CARD`
- [x] Zero TODOs, no-op stubs, or hardcoded user-facing English strings in `ui/tutorial/`

### Functional & Acceptance Checks
- [x] **AC-1:** Selecting **How to create a session** runs the 5-step spotlight flow (`New Session` → `Intro Queue` → `Main Queue` → `Meditation Timer` → `Save Session`) with `Step 1 / 5` .. `Step 5 / 5`
- [x] **AC-2:** Advancing through Intro, Main, and Timer steps scrolls off-screen cards smoothly into view with the spotlight cutout aligned
- [x] **AC-3:** When Library has 0 tracks, Step 3 (`Main Queue`) displays the flat empty-Library note inside the tooltip; when Library has ≥1 track, the note is hidden
- [x] **AC-4:** Tapping `Got it` on Step 5 (or `Skip` / back on any step inside the editor) dismisses the overlay and stays on `SessionEditorScreen` without saving a fake session
- [x] **AC-5:** Switching `EN` ↔ `မြန်မာ` updates all tutorial dialogs, launcher pill, and overlay copy live using user-supplied Burmese text with Western digits (`0-9`) and no text clipping
- [x] **AC-6:** `docs/spec/dhammameditate-shared-spec.md` is v1.3 with Section 11 (Guided Tutorials) + updated Burmese glossary, and `app/build.gradle.kts` has `versionCode = 4` and `versionName = "0.4.0"`
