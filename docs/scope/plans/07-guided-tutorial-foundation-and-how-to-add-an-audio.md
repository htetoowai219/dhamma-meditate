# 07. Guided Tutorial Foundation & "How to Add an Audio"

- **Date:** 2026-11-26
- **Phase:** 07 — Guided Tutorial Foundation & "How to Add an Audio"
- **Project Mode:** Prototype / MVP
- **Build Approach:** Tracer Bullet
- **Status:** Verified
- **Stack & Libraries:** Native Android (Kotlin, Jetpack Compose + Material 3 with flat Monastic Maroon & Saffron tokens), custom zero-dependency Compose spotlight overlay (`onGloballyPositioned` + `Canvas` cutout), `SharedPreferences` (`dhamma_meditate_prefs`), manual `AppContainer` DI, JUnit 4

## Summary
This phase builds DhammaMeditate's extensible guided tutorial foundation—a pure-Kotlin tutorial state machine and registry, a custom zero-dependency Compose multi-screen spotlight overlay, first-time-user auto-offer persistence, a tutorial topic menu, and a compact secondary launcher button above the primary CTA on both Home tabs—and proves the vertical slice end to end with the first walkthrough: **"How to add an audio"**. All copy is backed by Android string resources from day one so Phase 08 can add the **"How to create a session"** walkthrough and Burmese translations without rewriting the engine.

## Rationale & Trade-Offs
- **Chosen direction:** Custom zero-dependency Compose spotlight overlay (`onGloballyPositioned` root-bounds registry + `Canvas` dim/cutout + flat Material 3 tooltip card) paired with a pure-Kotlin `TutorialEngine` state reducer. This matches DhammaMeditate's flat Monastic Maroon & Saffron aesthetic, handles multi-screen transitions cleanly in one root coordinate space, supports Burmese Unicode text wrapping in Phase 08, and keeps the pure-Kotlin logic 100% unit-testable in JUnit 4.
- **Runner-up considered:** Pulling in a third-party Compose showcase library (e.g., `intro-showcase-view`). Skipped because external showcase libraries add dependency/Compose-BOM upgrade risk, often assume single-screen anchoring, and fight custom flat theming and custom target hit-testing.
- **Trade-off accepted:** Phase 07 ships English string resources only (`res/values/strings.xml`), while Burmese translations (`res/values-my/strings.xml`) for both tutorials are intentionally deferred to Phase 08 per `SCOPE.md` so all Burmese copy is collected from you in one clean pass.

## Requirements & Acceptance Criteria
- **AC-1 (First-Time Auto-Offer & Persistence):** On first launch (when `tutorial_offer_seen` is `false` and no active playback destination is open), a flat welcome dialog offers **"View Tutorials"** and **"Skip"**. Choosing either option (or dismissing the dialog) persists `tutorial_offer_seen = true` in `dhamma_meditate_prefs` so the welcome dialog is never shown again on subsequent launches.
- **AC-2 (Active Playback Non-Interruption):** If the app opens directly into active playback (`SessionPlayer` or `QuickTrackPlayer`), the first-time welcome dialog is suppressed and does not interrupt meditation playback.
- **AC-3 (On-Demand Launcher Placement & Hierarchy):** A compact secondary tutorial launcher button (using a distinct flat secondary palette—`NavActivePill` surface, `1.dp` `OutlineNeutral` border, `MonasticMaroon` `HelpOutline` icon + short label) appears directly above the full-width `Saffron` primary CTA on both `SessionsScreen` and `LibraryScreen`, keeping the `Saffron` button visually dominant. Tapping it opens the tutorial topic list dialog anytime.
- **AC-4 (Extensible Tutorial Registry & Topic List):** `TutorialRegistry` defines tutorials declaratively as ordered `TutorialStep` lists targeting `TutorialTargetId` anchors. The topic list dialog renders all registered tutorials (title, short description, Western-digit step count) and launches the selected tutorial; adding Phase 08's walkthrough requires only registering a new `TutorialDefinition` and attaching target modifiers—zero changes to the spotlight renderer.
- **AC-5 ("How to Add an Audio" Multi-Screen Walkthrough):** Selecting **"How to add an audio"** from either `Sessions` or `Library` navigates to `Home(HomeTab.SESSIONS)` and starts at `Step 1 / 2`:
  - **Step 1 / 2:** Highlights the `Library` tab in `HomeBottomNavigationBar`, displays a floating flat tooltip with `Step 1 / 2`, concise guidance, a tap hint, and a **Skip** button. Tapping outside the cutout does nothing; tapping the highlighted `Library` tab switches to `HomeTab.LIBRARY` and advances to `Step 2 / 2`.
  - **Step 2 / 2:** Highlights the `Import Audio` button on `LibraryScreen`, displays a floating flat tooltip with `Step 2 / 2`, concise guidance covering supported formats (MP3, M4A, WAV, FLAC, OGG), and a **"Got it"** confirmation button. Tapping **"Got it"** (or tapping the highlighted button) finishes and dismisses the tutorial safely **without** launching the OS document picker or mutating any data.
- **AC-6 (Safe Cancellation & Back Handling):** Tapping **Skip** on any step or pressing the Android system Back button while a spotlight walkthrough is active cleanly exits the tutorial without leaving the app or corrupting navigation state.
- **AC-7 (Flat Theme & Localization Readiness):** All new dialogs, tooltips, and buttons adhere to flat Material 3 rules (`tonalElevation = 0.dp`, `shadowElevation = 0.dp`, zero gradients, zero emojis), wrap dialog contents in `ProvideLocalizedDialogContext`, use `res/values/strings.xml` for all user-facing copy, and render `Step X / N` and step counts with Western digits (`0-9`).

## Technical Design

### Files & Module Layout
- `app/src/main/java/com/dhammameditate/data/preferences/TutorialPreferencesStore.kt` *(new)* — Persists `hasSeenTutorialOffer` (`Boolean`) in `dhamma_meditate_prefs` and exposes `StateFlow<Boolean>` + `markOfferSeen()`.
- `app/src/main/java/com/dhammameditate/di/AppContainer.kt` *(modified)* — Instantiates and exposes `tutorialPreferencesStore: TutorialPreferencesStore`.
- `app/src/main/java/com/dhammameditate/ui/tutorial/TutorialModels.kt` *(new)* — Pure-Kotlin immutable types: `TutorialId`, `TutorialTargetId`, `TutorialStartRoute`, `TutorialStepInteraction`, `TutorialStep`, `TutorialDefinition`, `TutorialSessionState`.
- `app/src/main/java/com/dhammameditate/ui/tutorial/TutorialEngine.kt` *(new)* — Pure-Kotlin state reducer (`start`, `onTargetTapped`, `confirmCurrentStep`, `skip`, `shouldShowFirstTimeOffer`, `formatStepProgress`).
- `app/src/main/java/com/dhammameditate/ui/tutorial/TutorialRegistry.kt` *(new)* — Declarative catalog of guided tutorials; Phase 07 registers `TutorialId.ADD_AUDIO` (2 steps).
- `app/src/main/java/com/dhammameditate/ui/tutorial/TutorialAnchors.kt` *(new)* — `TutorialAnchorRegistry` (`SnapshotStateMap<TutorialTargetId, Rect>`), `LocalTutorialAnchorRegistry`, and `Modifier.tutorialTarget(targetId)` with `onGloballyPositioned { boundsInRoot() }` and `DisposableEffect` cleanup.
- `app/src/main/java/com/dhammameditate/ui/tutorial/TutorialSpotlightOverlay.kt` *(new)* — Full-screen root Compose overlay drawing dimmed scrim + rounded-rect `BlendMode.Clear` cutout + `Saffron` border, selective pointer hit-testing, auto-positioned flat tooltip card, and `BackHandler`.
- `app/src/main/java/com/dhammameditate/ui/tutorial/TutorialDialogs.kt` *(new)* — Flat `TutorialWelcomeDialog` and `TutorialTopicListDialog` (wrapped in `ProvideLocalizedDialogContext`).
- `app/src/main/java/com/dhammameditate/ui/tutorial/TutorialLauncherButton.kt` *(new)* — Compact secondary pill button (`NavActivePill` + `1.dp` `OutlineNeutral` border + `MonasticMaroon` `HelpOutline` icon & label) mounted above primary CTAs.
- `app/src/main/java/com/dhammameditate/MainActivity.kt` *(modified)* — Hosts `LocalTutorialAnchorRegistry`, tutorial session/dialog state, first-time offer trigger, `NAV_LIBRARY_TAB` anchor in `HomeBottomNavigationBar`, and root `TutorialSpotlightOverlay`.
- `app/src/main/java/com/dhammameditate/ui/sessions/SessionsScreen.kt` *(modified)* — Accepts `onOpenTutorials: () -> Unit` and mounts `TutorialLauncherButton` directly above the `New Session` Saffron CTA.
- `app/src/main/java/com/dhammameditate/ui/library/LibraryScreen.kt` *(modified)* — Accepts `onOpenTutorials: () -> Unit`, mounts `TutorialLauncherButton` directly above the `Import Audio` Saffron CTA, and attaches `Modifier.tutorialTarget(TutorialTargetId.LIBRARY_IMPORT_BUTTON)` to the `Import Audio` button.
- `app/src/main/res/values/strings.xml` *(modified)* — Adds all English strings for the welcome dialog, topic list, launcher button, overlay chrome (`Step %1$s / %2$s`, Skip, Got it, tap hint), and **"How to add an audio"** steps.
- `app/src/test/java/com/dhammameditate/ui/tutorial/TutorialEngineTest.kt` *(new)* — JUnit 4 unit tests for `TutorialEngine` and `TutorialRegistry`.

### Data Model / State Shape

#### Persistence (`SharedPreferences`)
- Preferences file: `"dhamma_meditate_prefs"` (reusing existing app prefs file from `AppLanguageStore`).
- Key: `"tutorial_offer_seen"` (`Boolean`, default `false`).

#### Pure-Kotlin Tutorial Models (`TutorialModels.kt`)
- `enum class TutorialId { ADD_AUDIO }` *(extensible in Phase 08 with `CREATE_SESSION`)*
- `enum class TutorialTargetId { NAV_LIBRARY_TAB, LIBRARY_IMPORT_BUTTON }` *(extensible in Phase 08 with `SESSIONS_NEW_BUTTON`, editor targets, etc.)*
- `enum class TutorialStartRoute { HOME_SESSIONS, HOME_LIBRARY }`
- `enum class TutorialStepInteraction { TAP_TARGET, CONFIRM_ONLY }`
- `data class TutorialStep(val targetId: TutorialTargetId, val titleRes: Int, val bodyRes: Int, val tapHintRes: Int?, val interaction: TutorialStepInteraction)`
- `data class TutorialDefinition(val id: TutorialId, val titleRes: Int, val descriptionRes: Int, val startRoute: TutorialStartRoute, val steps: List<TutorialStep>)`
- `data class TutorialSessionState(val definition: TutorialDefinition, val currentStepIndex: Int)` with derived properties:
  - `currentStep: TutorialStep`
  - `stepNumber: Int` (`currentStepIndex + 1`)
  - `totalSteps: Int` (`definition.steps.size`)
  - `isLastStep: Boolean` (`currentStepIndex == definition.steps.lastIndex`)

#### Pure-Kotlin Reducer (`TutorialEngine.kt`)
- `shouldShowFirstTimeOffer(hasSeenOffer: Boolean, hasActivePlayback: Boolean, hasActiveTutorial: Boolean): Boolean`
- `start(definition: TutorialDefinition): TutorialSessionState`
- `onTargetTapped(state: TutorialSessionState, targetId: TutorialTargetId): TutorialSessionState?` — If `targetId != state.currentStep.targetId`, returns `state` unchanged. If `state.isLastStep`, returns `null` (completed). Otherwise advances `currentStepIndex + 1`.
- `confirmCurrentStep(state: TutorialSessionState): TutorialSessionState?` — For `CONFIRM_ONLY` steps (e.g., final **"Got it"**): completes (`null`) if last step, otherwise advances.
- `skip(): TutorialSessionState?` — Always returns `null`.
- `formatStepNumbers(stepNumber: Int, totalSteps: Int): Pair<String, String>` — Formats using `Locale.US` so Western digits (`0-9`) are guaranteed regardless of device locale.

### Interface Surface (UI Screens, Overlay & Components)

| Component | Placement & Visual Spec | Interactions & States |
|---|---|---|
| `TutorialLauncherButton` | Right-aligned compact secondary pill inside the bottom CTA `Surface` directly above the full-width `54.dp` `Saffron` primary button on both `SessionsScreen` and `LibraryScreen` (`8.dp` vertical gap). `NavActivePill` fill, `1.dp` `OutlineNeutral` border, `RoundedCornerShape(12.dp)`, `MonasticMaroon` `HelpOutline` (`16.dp`) + short label (`Tutorials`). | Always enabled; opens `TutorialTopicListDialog`. |
| `TutorialWelcomeDialog` | Flat `AlertDialog` (`CardSurface`, `1.dp` `OutlineNeutral` border, `tonalElevation = 0.dp`, `RoundedCornerShape(16.dp)`), wrapped in `ProvideLocalizedDialogContext`. | Shown once on first launch when `shouldShowFirstTimeOffer` is `true`. Primary: **View Tutorials** (marks seen + opens `TutorialTopicListDialog`). Secondary/dismiss: **Skip** (marks seen + closes). |
| `TutorialTopicListDialog` | Flat `Dialog` (`CardSurface`, `1.dp` `OutlineNeutral` border, `RoundedCornerShape(16.dp)`), wrapped in `ProvideLocalizedDialogContext`. Lists clickable topic rows from `TutorialRegistry.all`. | Tapping **How to add an audio** closes dialog, navigates to `Home(HomeTab.SESSIONS)`, and starts `TutorialId.ADD_AUDIO`. **Close** / outside tap dismisses. |
| `TutorialSpotlightOverlay` | Root-level `Box` overlay in `MainActivity` spanning the full window (including status/nav bar coordinate space via `boundsInRoot()`). Dimmed scrim (`InkPrimary.copy(alpha = 0.68f)`), offscreen-composited `Canvas` with `BlendMode.Clear` rounded-rect cutout (`6.dp` padding, `16.dp` corner radius) + `2.dp` `Saffron` highlight stroke. | Blocks touches outside the cutout. On `TAP_TARGET`, tapping inside the cutout executes the mapped real action (e.g., `onSelectTab(HomeTab.LIBRARY)`) and calls `TutorialEngine.onTargetTapped`. On `CONFIRM_ONLY`, tapping inside the cutout or tapping **"Got it"** calls `TutorialEngine.confirmCurrentStep` without invoking the underlying OS file picker. Tooltip card auto-places above the cutout when target is in lower half of screen, below otherwise, with `Step X / N`, title, body, optional tap hint, **Skip**, and **"Got it"** (final step). |

### Value Sourcing & Key Invariants
- **First-time offer seen flag:** Sourced from `TutorialPreferencesStore.hasSeenTutorialOffer` (`SharedPreferences` `"dhamma_meditate_prefs"`, key `"tutorial_offer_seen"`, default `false`). Invariant: written `true` on any resolution of `TutorialWelcomeDialog` (`View Tutorials`, `Skip`, or dismiss) so it never reappears.
- **Step progress (`Step X / N`) and topic step count:** Derived from `TutorialSessionState.stepNumber` / `totalSteps` and `TutorialDefinition.steps.size`, formatted via `Locale.US` Western digits (`0-9`).
- **Spotlight target bounds:** Sourced from `TutorialAnchorRegistry[currentStep.targetId]` via `Modifier.onGloballyPositioned { it.boundsInRoot() }`. Invariant: `Modifier.tutorialTarget` clears its key in `DisposableEffect.onDispose` only if its own registration is disposed, preventing stale off-screen coordinates.
- **Safe final-step execution:** For `TutorialId.ADD_AUDIO` Step 2 (`LIBRARY_IMPORT_BUTTON`), `interaction = TutorialStepInteraction.CONFIRM_ONLY`. The overlay intercepts taps on the cutout and finishes the tutorial without forwarding a click to `documentPickerLauncher.launch(...)`, guaranteeing zero forced file imports.
- **Active target action dispatch:** `MainActivity` maps `TutorialTargetId` → action lambda for `TAP_TARGET` steps (`NAV_LIBRARY_TAB` → `destination = AppDestination.Home(HomeTab.LIBRARY)`), keeping `TutorialSpotlightOverlay` decoupled from screen-specific navigation details.

### Validation & Edge Cases
- **1-frame layout transition gap:** After tapping `Library` in Step 1, `LibraryScreen` composes on the next frame before `LIBRARY_IMPORT_BUTTON` reports its `Rect`. While `targetRect == null`, `TutorialSpotlightOverlay` renders the dimmed backdrop + centered fallback tooltip (still showing **Skip** / **Got it**) without crashing, then snaps cleanly onto the cutout as soon as `onGloballyPositioned` fires.
- **Starting from `Library` tab:** Launching **How to add an audio** while already on `Library` explicitly sets `destination = AppDestination.Home(HomeTab.SESSIONS)` first so Step 1 (`Library` tab) and Step 2 (`Import Audio`) always run consistently.
- **Active playback notification tap during tutorial:** If `openActiveSessionTrigger` fires while a tutorial is open, clear `activeTutorial = null` and dismiss tutorial dialogs before navigating to the active player so playback is never obscured by a stale overlay.
- **Language switch while dialog open:** `TutorialWelcomeDialog` and `TutorialTopicListDialog` use `ProvideLocalizedDialogContext` so strings always match `AppLanguageStore`.
- **System Back button:** `BackHandler(enabled = activeTutorial != null)` calls `activeTutorial = TutorialEngine.skip()`, preventing accidental app exit mid-walkthrough.

## Build Plan
- [x] 1. Create pure-Kotlin `TutorialModels.kt`, `TutorialEngine.kt`, `TutorialRegistry.kt` (registering `ADD_AUDIO` with its 2 steps), and unit tests in `TutorialEngineTest.kt`. *(satisfies AC-1, AC-2, AC-4, AC-5, AC-6, AC-7)*
- [x] 2. Create `TutorialPreferencesStore.kt` backed by `dhamma_meditate_prefs` (`tutorial_offer_seen`) and wire it into `AppContainer.kt`. *(satisfies AC-1)*
- [x] 3. Add all English tutorial string resources (`tutorial_*`) to `app/src/main/res/values/strings.xml`. *(satisfies AC-7)*
- [x] 4. Implement `TutorialAnchors.kt` (`TutorialAnchorRegistry`, `LocalTutorialAnchorRegistry`, `Modifier.tutorialTarget`), `TutorialSpotlightOverlay.kt`, `TutorialDialogs.kt` (`TutorialWelcomeDialog`, `TutorialTopicListDialog`), and `TutorialLauncherButton.kt`. *(satisfies AC-1, AC-3, AC-4, AC-5, AC-6, AC-7)*
- [x] 5. Wire `LocalTutorialAnchorRegistry`, first-time offer check, `TutorialWelcomeDialog`, `TutorialTopicListDialog`, `NAV_LIBRARY_TAB` anchor, active-playback tutorial-clear guard, and root `TutorialSpotlightOverlay` into `MainActivity.kt`. *(satisfies AC-1, AC-2, AC-4, AC-5, AC-6)*
- [x] 6. Mount `TutorialLauncherButton` above the primary Saffron CTA in `SessionsScreen.kt` and `LibraryScreen.kt`, and attach `Modifier.tutorialTarget(TutorialTargetId.LIBRARY_IMPORT_BUTTON)` in `LibraryScreen.kt`. *(satisfies AC-3, AC-5)*

## Implementation Notes
- Used `Icons.AutoMirrored.Outlined.HelpOutline` in `TutorialLauncherButton.kt` instead of deprecated `Icons.Outlined.HelpOutline` to keep Kotlin compilation warning-free.
- Added `TutorialEngine.formatCount(value: Int)` (backed by `Locale.US`) alongside `formatStepNumbers` so topic step counts in `TutorialTopicListDialog` also guarantee Western digits (`0-9`).

## Verification Checklist

### Build & Static Checks
- [x] `./gradlew testDebugUnitTest` passes cleanly (including all new `TutorialEngineTest` cases and existing unit tests).
- [x] `./gradlew assembleDebug` compiles cleanly with zero errors.

### System Integration & Wiring
- [x] `TutorialPreferencesStore` is wired into `AppContainer` and read in `MainActivity`.
- [x] `LocalTutorialAnchorRegistry` wraps the root app tree in `MainActivity`, and `TutorialSpotlightOverlay` renders above `Scaffold` + `HomeBottomNavigationBar` in the same root coordinate space.
- [x] `TutorialLauncherButton` is mounted on both `SessionsScreen` and `LibraryScreen`, and both `NAV_LIBRARY_TAB` and `LIBRARY_IMPORT_BUTTON` anchors are attached with zero orphaned tutorial files or dead handlers.

### Functional & Acceptance Checks
- [x] **AC-1:** Fresh launch (cleared app data, no active playback) shows `TutorialWelcomeDialog`; tapping **Skip** dismisses it and it does not reappear after app restart; tapping **View Tutorials** opens `TutorialTopicListDialog` and marks the offer seen.
- [x] **AC-2:** Launching into active playback (`SessionPlayer` / `QuickTrackPlayer`) suppresses `TutorialWelcomeDialog`.
- [x] **AC-3:** Compact secondary `TutorialLauncherButton` (`NavActivePill` + `MonasticMaroon` icon/label) renders cleanly above the full-width `Saffron` CTA on both `Sessions` and `Library`, and opens `TutorialTopicListDialog`.
- [x] **AC-4:** `TutorialTopicListDialog` displays entries from `TutorialRegistry` (including **How to add an audio** with step count `2`), and `TutorialRegistry` / `TutorialSpotlightOverlay` require no overlay-engine changes to register additional tutorials.
- [x] **AC-5:** Starting **How to add an audio** (from either tab) navigates to `Sessions`, highlights the `Library` tab (`Step 1 / 2`), switches to `Library` on target tap, highlights `Import Audio` (`Step 2 / 2`), and finishes cleanly on **"Got it"** (or target tap) without opening the OS document picker.
- [x] **AC-6:** Tapping **Skip** or pressing Android Back mid-walkthrough cleanly exits the overlay without closing the app or corrupting navigation.
- [x] **AC-7:** All tutorial UI uses flat Material 3 styling (`tonalElevation = 0.dp`, zero gradients/emojis), `ProvideLocalizedDialogContext`, `strings.xml` resources, and Western digits (`0-9`) for `Step X / N`.
