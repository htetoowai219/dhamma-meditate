# Code Review: Burmese Language Localization & v0.3.0 Release Prep (`2026-11-26`)

- **Project Mode:** Prototype / MVP
- **Scope:** 26 files (uncommitted Phase 06 changes on `main`)
- **Reviewer:** Fresh-eyes Staff Code Reviewer subagent (`pro` model)
- **Verdict:** **Blocked**

## Summary
Phase 06 cleanly structures `AppLanguage` / `AppLanguageStore`, `UiText`, `TimerCapNotice`, `ImportFailureReason`, 1:1 English/Burmese `strings.xml` parity (170 keys with strict Western-digit formatting), `dhammameditate-shared-spec.md` v1.2, and the `v0.3.0` (`versionCode = 3`) version bump. However, overriding `LocalContext` with raw `createConfigurationContext(...)` at the `MainActivity` root breaks `LocalActivityResultRegistryOwner`—causing immediate runtime crashes on 3 of the 5 screens—and Compose `Dialog`/`AlertDialog`/`ModalBottomSheet` subcompositions overwrite `LocalContext`/`LocalConfiguration` back to the unlocalized `Activity` context, leaving every modal in the device system language. Additionally, `MeditationPlaybackService` does not react to runtime language switches for its `MediaSession` custom command label and active notification metadata.

## Blockers

### 🔴 `rememberLauncherForActivityResult` crashes immediately on Library, SessionPlayer, and QuickTrackPlayer — `app/src/main/java/com/dhammameditate/MainActivity.kt:157-165`
- **Problem:** `baseContext.withAppLanguage(currentLanguage)` returns `Context.createConfigurationContext(config)`, which is a raw `ContextImpl`—neither an `ActivityResultRegistryOwner` nor a `ContextWrapper` wrapping `MainActivity`. `androidx.activity.compose.LocalActivityResultRegistryOwner.current` walks `ContextWrapper.baseContext` to find the `Activity`; replacing `LocalContext` with `ContextImpl` makes that lookup return `null`, throwing `IllegalStateException: No ActivityResultRegistryOwner was provided via LocalActivityResultRegistryOwner` in `LibraryScreen.kt:94-100` and `DistractionCheckDialog.kt:109-114` (called unconditionally from `SessionPlayerScreen.kt:90` and `PlayerScreen.kt:89`).
- **Impact:** Opening the **Library** tab, **SessionPlayer**, or **QuickTrackPlayer** crashes immediately at runtime. `DistractionSettingsNavigator.findActivity()` also fails to find the `Activity`.
- **Suggested Fix:** Wrap `this@MainActivity` in a `ContextWrapper(this@MainActivity)` whose `getResources()` and `getAssets()` delegate to the `createConfigurationContext` instance (preserving the `Activity` at the base of the `ContextWrapper` chain), and explicitly provide `LocalActivityResultRegistryOwner provides this@MainActivity` in `MainActivity.kt`'s `CompositionLocalProvider`.

### 🔴 All `AlertDialog`, `Dialog`, and `ModalBottomSheet` windows revert to the device system locale — `app/src/main/java/com/dhammameditate/MainActivity.kt:162-165`
- **Problem:** Compose `Dialog` (used by `AlertDialog` and custom dialogs) and `ModalBottomSheet` build a separate window from `LocalView.current.context` (the unlocalized `MainActivity`) and call `ProvideAndroidCompositionLocals`, unconditionally shadowing the outer `LocalContext` and `LocalConfiguration` providers with the unlocalized `Activity` context. Affects:
  - `SessionsScreen.kt:177-228` (Delete session `AlertDialog`)
  - `LibraryScreen.kt:221-305` (Delete track `AlertDialog`)
  - `LibraryScreen.kt:312-355` (Batch rename `AlertDialog`)
  - `LibraryScreen.kt:581-683` (`TrackRenameDialog` `Dialog`)
  - `DistractionCheckDialog.kt:202-355` (`DistractionCheckDialog` `Dialog`)
  - `SessionEditorScreen.kt:327-343` (`ModalBottomSheet` track picker)
- **Impact:** Whenever the in-app language differs from the OS locale (e.g., toggling `EN` → `မြန်မာ` on an English phone), every dialog and bottom sheet renders in the OS language instead of the selected in-app language (breaking AC-1 and AC-3).
- **Suggested Fix:** Expose the active localized `Context` via a custom `CompositionLocal` (e.g., `LocalAppLocalizedContext`) from `MainActivity`, and re-provide `LocalContext` and `LocalConfiguration` inside every `Dialog`, `AlertDialog`, and `ModalBottomSheet` content block (e.g., via a small `ProvideAppLocalizedContext` helper in `ui/common/`).

## Major

### 🟠 `MeditationPlaybackService` does not update `MediaSession` command button or active notification metadata on runtime language toggle — `app/src/main/java/com/dhammameditate/playback/MeditationPlaybackService.kt:236-240, 462-467, 574-627`
- **Problem:** `MeditationPlaybackService` never observes `appLanguageStore.language`. Because `MediaSessionService` persists across language toggles and sessions, `stopResetButton` (`R.string.action_stop_reset`) remains frozen in the language active at `onCreate()`, and active `ExoPlayer` `MediaItem` `MediaMetadata` (`subtitle`/`artist`) stays in the old language when toggled mid-session.
- **Impact:** Lock-screen controls and active notification subtitles display stale language copy after an in-app toggle.
- **Suggested Fix:** Collect `appLanguageStore.language` (dropping the initial emission) in `serviceScope.launch` to rebuild `stopResetButton`, call `mediaSession?.setMediaButtonPreferences(listOf(updatedButton))`, and refresh loaded `exoPlayer` `MediaItem` metadata (`subtitle`/`artist`) via `replaceMediaItem`.

## Minor & Nits
- 🟡 `app/src/main/java/com/dhammameditate/ui/player/PlayerScreen.kt:375-392` — `TimerSelectorCard` locked header `Row(SpaceBetween)` lacks `Modifier.weight(1f)` + spacing between title and `"ဖွင့်နေစဥ် ပြောင်းလဲ၍မရပါ"`.
- 🟡 `app/src/main/java/com/dhammameditate/ui/editor/SessionEditorScreen.kt:847-866` — `TrackPickerSheetContent` header title lacks `Modifier.weight(1f)` before the close `IconButton`.
- 🟡 `app/src/main/java/com/dhammameditate/ui/library/LibraryScreen.kt:643-666` — `TrackRenameDialog` action buttons use a fixed `Row` instead of `FlowRow`, which can cramp longer Burmese button labels on narrow widths.
- 🟡 `app/src/main/java/com/dhammameditate/ui/library/LibraryScreen.kt:455-461` & `SessionsScreen.kt:255-261` — Header subtitle `maxLines = 1` can truncate `"သိမ်းဆည်းထားသောတရားများ"` alongside `LanguageTogglePill` on narrow phones or scaled fonts; `maxLines = 2` is safer.
- 🟡 `app/src/main/java/com/dhammameditate/ui/editor/SessionEditorViewModel.kt:390` & `PlayerViewModel.kt:193` — `Char.isDigit()` accepts Burmese numerals (`၀–၉`) on Burmese keyboards; filter strictly on `it in '0'..'9'` (or normalize to ASCII `'0'..'9'`) to preserve the Western-digits invariant.
- 🟡 `app/src/main/java/com/dhammameditate/ui/editor/SessionEditorViewModel.kt:466` — `SessionRepository.saveSession` exception messages wrapped in `UiText.Dynamic(msg)` can surface raw English `require(...)` messages in Burmese mode.
- ⚪ `app/src/main/java/com/dhammameditate/ui/sessionplayer/SessionPlayerScreen.kt:701-710` & `PlayerScreen.kt:597-641` — Single-button `Stop & Reset` uses fixed `height(50.dp)` + `maxLines = 1`; prefer `heightIn(min = 50.dp)` + `maxLines = 2`.
- ⚪ `app/src/main/res/values-my/strings.xml:58` — `rename_step_title` reads `"ပြီးစီး"` ("completed") while editing step 1; confirm if intentional.
- ⚪ `docs/spec/dhammameditate-shared-spec.md:140-141` — Pre-existing v1.1 prose in §3.3 still uses `"Rename One by One"` / `"Save & Finish"` / `"Skip All"` vs the 170-key catalog.

## Strengths
- Clean domain-to-UI localization decoupling (`UiText`, `TimerCapNotice`, `ImportFailureReason`, `DistractionStatusKind`) keeps pure-Kotlin business logic Android-resource-free and preserves full unit test compatibility.
- Exact 170/170 key parity between `values/strings.xml` and `values-my/strings.xml` with strict `%s` Western-digit safety and valid XML escaping.
- Clear, complete `docs/spec/dhammameditate-shared-spec.md` v1.2 bilingual parity contract and `v0.3.0` (`versionCode = 3`) bump.

## Resolution (Post-Review Fix Pass)
- ✅ **Blocker #1 fixed:** `Context.withAppLanguage` now wraps `this` in a `ContextWrapper(this)` delegating `getResources()` / `getAssets()` to `createConfigurationContext(config)`, and `MainActivity` explicitly provides `LocalActivityResultRegistryOwner provides this@MainActivity`.
- ✅ **Blocker #2 fixed:** Added `LocalAppLocalizedContext` and `ProvideLocalizedDialogContext` in `ui/common/LocalizedDialogContext.kt` and wrapped every `Dialog`, `AlertDialog`, and `ModalBottomSheet` across `DistractionCheckDialog`, `SessionsScreen`, `LibraryScreen`, and `SessionEditorScreen`.
- ✅ **Major #1 fixed:** `MeditationPlaybackService` now observes `appLanguageStore.language.drop(1)` in `serviceScope` to rebuild `stopResetButton`, update `mediaSession.setMediaButtonPreferences(...)`, and refresh loaded `ExoPlayer` `MediaItem` subtitles via `replaceMediaItem`.
- ✅ **Minors fixed:** Added `weight(1f)` + spacing in `PlayerScreen` locked timer header and `SessionEditorScreen` picker header, switched `TrackRenameDialog` actions to `FlowRow`, bumped header subtitles to `maxLines = 2`, enforced ASCII `'0'..'9'` in custom minutes inputs, used localized `editor_save_error_generic` on save failure, and updated single-button `Stop & Reset` to `heightIn(min = 50.dp)` + `maxLines = 2`.
- ✅ **Re-verified:** `./gradlew testDebugUnitTest --rerun-tasks assembleDebug` exited `0` (`BUILD SUCCESSFUL`, 49/49 unit tests passing).
