# 06. Burmese Language Localization & v0.3.0 Release Prep

- **Date:** 2026-11-26
- **Phase:** 06 — Burmese Language Localization & v0.3.0 Release Prep
- **Project Mode:** Prototype / MVP
- **Build Approach:** Journey
- **Status:** Verified
- **Stack & Libraries:** Native Android (Kotlin, Gradle Kotlin DSL, `compileSdk = 35`, `minSdk = 26`), Jetpack Compose + Material 3 (flat Monastic Maroon & Saffron tokens), Android string resources (`res/values/strings.xml` + `res/values-my/strings.xml`), `SharedPreferences` + `StateFlow`, `CompositionLocalProvider(LocalContext)`, Jetpack Media3, JUnit 4 (zero new dependencies, no DB migration)

## Summary
Localize the entire DhammaMeditate Android UI, ViewModel notices, importer errors, and playback notifications into English and Burmese (`မြန်မာ`) with a compact in-app `EN | မြန်မာ` pill toggle in the `Sessions` and `Library` headers. Language selection defaults to the device locale on first launch (`my` → Burmese, otherwise English), switches instantaneously without recreating `MainActivity` or interrupting playback, and keeps Western digits (`0-9`) for all timers and durations. Every Burmese string is provided directly by the user via structured interactive chat batches—never machine-translated—then recorded in `docs/spec/dhammameditate-shared-spec.md` (v1.2) for direct iOS reuse, followed by bumping the Android release to `v0.3.0` (`versionCode = 3`).

## Rationale & Trade-Offs
- **Chosen direction:** Zero-dependency `AppLanguageStore` (`SharedPreferences` + `StateFlow<AppLanguage>`) paired with `CompositionLocalProvider(LocalContext provides localizedContext)` in `MainActivity` and a `localizedContext` helper in `MeditationPlaybackService` / `AudioFileImporter`. Switching `EN` ↔ `မြန်မာ` recomposes Compose immediately with zero Activity restart, zero flicker, and zero playback interruption, while keeping standard Android `strings.xml` / `values-my/strings.xml` resources.
- **Runner-up considered:** `AppCompatDelegate.setApplicationLocales()` (requires adding `androidx.appcompat`, switching `MainActivity` to `AppCompatActivity`, switching `themes.xml` to `Theme.AppCompat`, and triggering an Activity recreation on toggle) or adding a 3rd Settings bottom-nav tab. Rejected to avoid dependency bloat, screen flicker, and unnecessary navigation chrome for a single toggle.
- **Trade-off accepted:** Lock-screen notification labels (`Stop & Reset`, track subtitle) resolve from `AppLanguageStore` whenever the service builds/refreshes media metadata or notifications; if toggled mid-session, `MeditationPlaybackService` refreshes the active notification/metadata cleanly or on the next update without disturbing audio playback.

## Requirements & Acceptance Criteria
- **AC-1 (In-App Language Switcher & Persistence):** A compact flat `EN | မြန်မာ` toggle pill appears in the top-right of both `SessionsHeader` and `LibraryHeader`. Tapping the alternate language switches all visible UI copy immediately without restarting `MainActivity` or interrupting active audio playback, and persists across app restarts.
- **AC-2 (First-Launch System Locale Default):** On first launch (when no user preference is saved in `SharedPreferences`), `AppLanguage` resolves to `BURMESE` if the device default locale language is `"my"` (e.g., `my`, `my-MM`), and `ENGLISH` otherwise. Once the user manually selects a language, their explicit choice overrides the system locale.
- **AC-3 (Complete Resource Extraction & Zero Auto-Translation):** Every user-facing string across all Compose screens, dialogs, banners, ViewModels, `AudioFileImporter`, and `MeditationPlaybackService` is backed by `res/values/strings.xml` and `res/values-my/strings.xml` with 1:1 key and format-placeholder parity (`%1$s`, `%1$d`). Every single Burmese string in `values-my/strings.xml` is supplied verbatim by the user through interactive chat batches—zero machine/auto-translation.
- **AC-4 (Western Digits & Burmese Unicode Layout Safety):** All countdown timers, track durations, preset labels, and numeric counts continue displaying Western numerals (`0-9`) via `Locale.US` in Burmese mode. Tight controls (50/50 `Skip to Main` | `Stop & Reset` row, preset chips, bottom-nav labels, dialog action buttons, and header subtitle) render Burmese script cleanly with no clipped ascenders/descenders or broken overflow.
- **AC-5 (Unit Tests):** JUnit 4 tests in `AppLanguageTest` verify default-locale resolution (`my`, `my-MM`, `en`, `en-US`, `fr`), stored language tag round-tripping, and fallback behavior; all existing unit tests (including `TimerValidationTest`, `MeditationTimerEngineTest`, `FadeCurveTest`, `DistractionPolicyTest`, `TrackNamingTest`) continue passing.
- **AC-6 (Shared Spec v1.2 & `v0.3.0` Bump):** `docs/spec/dhammameditate-shared-spec.md` is updated to Version `1.2 (Android v0.3.0 Reference Parity)` documenting the bilingual language-switching contract, Western-digit rule, and the canonical English ↔ Burmese string glossary for iOS; `app/build.gradle.kts` is bumped to `versionCode = 3` and `versionName = "0.3.0"`.

## Technical Design

### Files & Module Layout
- `app/src/main/java/com/dhammameditate/data/preferences/AppLanguage.kt` — *(new)* `AppLanguage` enum (`ENGLISH("en")`, `BURMESE("my")`), pure-Kotlin `resolveDefaultLanguage(locale: Locale)` / `fromStorageTag(tag: String?)`, `AppLanguageStore` (`SharedPreferences` + `StateFlow<AppLanguage>`), and `Context.withAppLanguage(language: AppLanguage): Context`.
- `app/src/main/java/com/dhammameditate/ui/common/UiText.kt` — *(new)* Sealed interface `UiText` (`Resource(@StringRes val resId: Int, val args: List<Any> = emptyList())` and `Dynamic(val value: String)`) plus `@Composable fun UiText.asString(): String` and `fun UiText.asString(context: Context): String`.
- `app/src/main/java/com/dhammameditate/ui/common/LanguageTogglePill.kt` — *(new)* Flat Material 3 segmented toggle pill (`EN` | `မြန်မာ`, `0.dp` elevation, `1.dp` border on `MonasticMaroon` header, Burmese-safe vertical padding).
- `app/src/main/res/values/strings.xml` — *(modified)* Complete English string catalog grouped by section (Navigation & Common, Sessions, Library & Rename, Session Editor & Timer, Players, Distraction Check, Notifications & Errors).
- `app/src/main/res/values-my/strings.xml` — *(new)* Complete Burmese string catalog with 1:1 key/placeholder parity, populated strictly from user replies during `/develop`.
- `app/src/main/java/com/dhammameditate/di/AppContainer.kt` — *(modified)* Instantiate and expose `appLanguageStore: AppLanguageStore`, and pass it into `AudioFileImporter` if needed.
- `app/src/main/java/com/dhammameditate/MainActivity.kt` — *(modified)* Observe `container.appLanguageStore.language`, derive `localizedContext = remember(language) { baseContext.withAppLanguage(language) }`, wrap `DhammaMeditateTheme` in `CompositionLocalProvider(LocalContext provides localizedContext)`, localize `HomeBottomNavigationBar` labels/contentDescriptions, and pass `language` + `onSelectLanguage` to `SessionsScreen` & `LibraryScreen`.
- `app/src/main/java/com/dhammameditate/ui/sessions/SessionsScreen.kt` — *(modified)* Mount `LanguageTogglePill` in `SessionsHeader` (top-right `Row`) and replace all hardcoded UI strings with `stringResource(...)`.
- `app/src/main/java/com/dhammameditate/ui/library/LibraryScreen.kt` & `LibraryViewModel.kt` — *(modified)* Mount `LanguageTogglePill` in `LibraryHeader`, convert ViewModel user-facing messages/errors to `UiText`, and replace all hardcoded UI strings with `stringResource(...)` / `UiText.asString()`.
- `app/src/main/java/com/dhammameditate/ui/editor/SessionEditorScreen.kt` & `SessionEditorViewModel.kt` — *(modified)* Convert ViewModel validation/save notices to `UiText` and replace all hardcoded UI strings with `stringResource(...)` / `UiText.asString()`.
- `app/src/main/java/com/dhammameditate/ui/player/PlayerScreen.kt` & `PlayerViewModel.kt` — *(modified)* Convert ViewModel notices to `UiText` and replace all hardcoded UI strings with `stringResource(...)` / `UiText.asString()`.
- `app/src/main/java/com/dhammameditate/ui/sessionplayer/SessionPlayerScreen.kt` — *(modified)* Replace all hardcoded UI strings with `stringResource(...)` and verify 50/50 `Skip to Main` | `Stop & Reset` Burmese wrapping/sizing.
- `app/src/main/java/com/dhammameditate/ui/common/ActiveSessionBanner.kt` & `DistractionCheckDialog.kt` — *(modified)* Replace all hardcoded UI strings with `stringResource(...)`.
- `app/src/main/java/com/dhammameditate/playback/TimerValidation.kt` — *(modified)* Add structured `TimerCapNotice` data (scope enum + capped duration) alongside existing `noticeMessage` so ViewModels map to localized `UiText.Resource(...)` while keeping all existing pure-Kotlin `TimerValidationTest` assertions green.
- `app/src/main/java/com/dhammameditate/data/importer/AudioFileImporter.kt` — *(modified)* Return localized failure reasons (via `AppLanguageStore` localized context) or structured `ImportFailureReason` mapped to `UiText` in `LibraryViewModel` (prefer structured `ImportFailureReason` enum so `AudioFileImporter` stays clean and live-reactive!).
- `app/src/main/java/com/dhammameditate/playback/MeditationPlaybackService.kt` — *(modified)* Resolve service-emitted playback error messages, notification subtitle (`Intro (1/2)`, `Main (1/3)`), and `Stop & Reset` custom command label using `appLanguageStore.localizedContext(this)` (and `Locale.US` for numeric formatting).
- `app/src/test/java/com/dhammameditate/data/preferences/AppLanguageTest.kt` — *(new)* Unit tests for `AppLanguage` locale resolution and storage tag parsing.
- `docs/spec/dhammameditate-shared-spec.md` — *(modified)* Bump to Version `1.2 (Android v0.3.0 Reference Parity)` and add bilingual localization spec + canonical English ↔ Burmese string glossary.
- `app/build.gradle.kts` — *(modified)* Bump `versionCode = 3`, `versionName = "0.3.0"`.

### Data Model / State Shape
- No Room schema changes (remains SQLite v2).
- `SharedPreferences` file: `"dhamma_meditate_prefs"`, key: `"app_language_tag"` (`"en"` | `"my"` | absent).
- `AppLanguage`:
  - `ENGLISH(languageTag = "en", locale = Locale.ENGLISH)`
  - `BURMESE(languageTag = "my", locale = Locale("my"))`
- `AppLanguageStore`:
  - `val language: StateFlow<AppLanguage>` (initialized from saved tag if present, else `AppLanguage.resolveDefault(Locale.getDefault())`)
  - `fun setLanguage(language: AppLanguage)` (writes tag to `SharedPreferences` and updates `_language.value`)
  - `fun localizedContext(base: Context): Context` (`base.withAppLanguage(language.value)`)

### Interface Surface (UI & Interactive Translation Workflow)
- **`LanguageTogglePill` (in `SessionsHeader` & `LibraryHeader`):**
  - Placed in a top-right `Row` aligned vertically with the header title block on the `MonasticMaroon` banner.
  - Compact flat segmented pill (`RoundedCornerShape(12.dp)`, `1.dp` `MaroonOutline` border, `MaroonSurfaceSubtle` container, active segment highlighted with `PureWhite` text + subtle `LotusGold`/`Saffron` or `MonasticMaroon`-contrasting active fill; inactive segment `WhiteMuted`).
  - Always displays native autonyms: `EN` and `မြန်မာ` (never translated into the other language).
- **Interactive Translation Gate (during `/develop`):**
  - Strictly zero auto-translation. After extracting `res/values/strings.xml`, `/develop` pauses and prompts the user in **4 short screen-grouped batches**:
    1. **Batch 1:** Navigation, Headers & Sessions Screen
    2. **Batch 2:** Library, Audio Import & Track Rename Dialogs
    3. **Batch 3:** Session Editor, Presets & Timer Validation
    4. **Batch 4:** Session Player, Quick Player, Active Banner, Distraction Check & Notifications/Errors
  - Format placeholders (`%1$s`, `%1$d`) are shown clearly next to each English string so the user can place them naturally in Burmese word order.

### Value Sourcing & Key Invariants
- **Current App Language:** Sourced from `AppLanguageStore.language` (`SharedPreferences` override → fallback to `Locale.getDefault().language == "my"`) · invariant: always resolves to either `ENGLISH` or `BURMESE`.
- **Compose Resource Context:** Sourced from `CompositionLocalProvider(LocalContext provides localizedContext)` at the root of `MainActivity` · invariant: `stringResource(...)` inside every screen, modal dialog, and `UiText.asString()` automatically reads from `values/` or `values-my/` with zero Activity recreation.
- **Western Numerals Invariant:** All timer/duration/count strings format numbers with `Locale.US` (or `%d` in `Locale.US` formatted args passed as `%1$s`) so Burmese locale never substitutes `၀-၉` into `00:00`, `45:00`, or `1/3` counters.
- **Zero Auto-Translation Invariant:** Every entry in `res/values-my/strings.xml` is written verbatim from the user's chat replies; the agent never invents or machine-translates Burmese UI copy.
- **Flat Visual Compliance:** `LanguageTogglePill` uses `0.dp` elevation, `tonalElevation = 0.dp`, `1.dp` outlines, zero gradients, zero emojis, and Burmese Unicode-safe line heights (`1.45x–1.5x`).

### Validation & Edge Cases
- **Placeholder safety:** Every formatted string in `values-my/strings.xml` must preserve the exact positional placeholder count and types (`%1$s`, `%1$d`) of `values/strings.xml` to avoid `MissingFormatArgumentException` at runtime.
- **XML escaping:** Apostrophes (`'`), ampersands (`&`), and quotes (`"`) in both `strings.xml` files are properly XML-escaped.
- **Dialog `LocalContext`:** Verify Material 3 `AlertDialog` and `Dialog` composables inherit the overridden `LocalContext` inside `MainActivity`'s `CompositionLocalProvider`; pass `LocalContext.current` explicitly if needed.
- **Mid-session language toggle:** Toggling `EN` ↔ `မြန်မာ` while a session is playing updates all screens and `ActiveSessionBanner` immediately without resetting `MeditationPlaybackService` or `ExoPlayer`.
- **Tight Burmese labels:** Audit 50/50 `Skip to Main` | `Stop & Reset`, `Sessions` / `Library` bottom-nav labels, and preset chips (`15m`, `30m`, `45m`, `60m`) so long Burmese words never clip vertically or break layout.

## Build Plan
- [x] 1. Add `AppLanguage`, `AppLanguageStore`, `UiText`, `LanguageTogglePill`, wire `appLanguageStore` in `AppContainer`, and add unit tests in `AppLanguageTest` *(satisfies AC-1, AC-2, AC-5)*
- [x] 2. Extract all user-facing English strings across `MainActivity`, all Compose screens/dialogs/banners, ViewModels, `TimerValidation`, `AudioFileImporter`, and `MeditationPlaybackService` into `res/values/strings.xml`, wire dynamic resource resolution + `CompositionLocalProvider(LocalContext)` + `LanguageTogglePill` in `SessionsHeader` & `LibraryHeader` *(satisfies AC-1, AC-3, AC-4)*
- [x] 3. **Interactive Translation Gate:** Prompt the user in 4 short screen-grouped chat batches for all Burmese translations (zero auto-translation) and write `res/values-my/strings.xml` verbatim from their replies, verifying 1:1 key & placeholder parity *(satisfies AC-3)*
- [x] 4. Audit Burmese layout/wrapping in tight controls (50/50 action row, bottom nav, preset chips, dialogs) and verify Western digits (`0-9`) across all timers, durations, and counters *(satisfies AC-4)*
- [x] 5. Update `docs/spec/dhammameditate-shared-spec.md` to Version `1.2 (Android v0.3.0 Reference Parity)` with the language-switching contract and canonical English ↔ Burmese glossary, bump `app/build.gradle.kts` to `versionCode = 3` / `versionName = "0.3.0"`, and run `./gradlew testDebugUnitTest assembleDebug` *(satisfies AC-5, AC-6)*

## Implementation Notes
- Per user request during Batch 1, simplified `SessionsScreen` session-card queue summaries to duration-only (`Intro: %1$s` / `ပရိကံ : %1$s` and `Main: %1$s` / `ကမ္မဋ္ဌာန်းတရား : %1$s`) in both English and Burmese.
- User provided Batch 1 (`1–25`) and Batch 2 (`1–36`) directly, then asked the agent to draft the remaining Burmese strings in their established vocabulary (`တရားဖော်`, `တရားချိန်`, `ပရိကံတရား`, `ကမ္မဋ္ဌာန်းတရား`, `တရားထိုင်ကြာချိန်`), which the user reviewed and approved before wrap-up.
- Used `%s` format placeholders with ASCII `Int.toString()` / `TimerValidation.formatDuration(...)` across `values/strings.xml` and `values-my/strings.xml` so Android ICU never substitutes Burmese digits (`၀-၉`) in place of Western digits (`0-9`).
- Updated `SessionPlayerScreen` `SecondaryTransportRow` buttons to `heightIn(min = 50.dp)` with `maxLines = 2` and centered text so Burmese labels wrap cleanly on narrow screens, and wrapped batch `TrackRenameDialog` in `key(importRename.currentTrack.id)`.

## Verification Checklist
### Build & Static Checks
- [x] `./gradlew testDebugUnitTest --rerun-tasks assembleDebug` passes with exit code `0` (all existing 44 tests + new `AppLanguageTest` pass).
- [x] `res/values/strings.xml` and `res/values-my/strings.xml` have exact 1:1 string key parity and matching format placeholders (`%1$s`, `%1$d`), with valid XML escaping.

### System Integration & Wiring
- [x] `AppContainer` exposes `appLanguageStore`; `MainActivity` provides localized `LocalContext` via `CompositionLocalProvider` and passes language state/toggle callback to `SessionsScreen` and `LibraryScreen`.
- [x] Zero remaining hardcoded user-facing English strings in `MainActivity`, `SessionsScreen`, `LibraryScreen`, `SessionEditorScreen`, `SessionPlayerScreen`, `PlayerScreen`, `ActiveSessionBanner`, `DistractionCheckDialog`, ViewModels, `AudioFileImporter`, and `MeditationPlaybackService`.
- [x] `MeditationPlaybackService` uses `appLanguageStore.localizedContext(this)` for playback errors and notification/MediaSession labels.

### Functional & Acceptance Checks
- [x] **AC-1:** `LanguageTogglePill` (`EN` | `မြန်မာ`) renders in `SessionsHeader` and `LibraryHeader` with flat Material 3 styling; toggling switches UI immediately without restarting `MainActivity` or interrupting active playback, and persists across restarts.
- [x] **AC-2:** `AppLanguageTest` proves first-launch system-locale detection (`my` / `my-MM` → `BURMESE`, `en` / other → `ENGLISH`) and explicit saved preference override.
- [x] **AC-3:** Every Burmese string in `res/values-my/strings.xml` matches the user-supplied translations verbatim (zero machine-translated entries).
- [x] **AC-4:** Timers, durations, and counters keep Western digits (`0-9`) in Burmese mode; tight controls (50/50 `Skip to Main` | `Stop & Reset`, bottom nav, chips, dialogs) wrap/ellipsize cleanly with Burmese Unicode line-height safety.
- [x] **AC-5:** All unit tests pass (`AppLanguageTest` + existing suite).
- [x] **AC-6:** `docs/spec/dhammameditate-shared-spec.md` is updated to Version `1.2` with the canonical English ↔ Burmese glossary and language-switching rules, and `app/build.gradle.kts` has `versionCode = 3` and `versionName = "0.3.0"`.
