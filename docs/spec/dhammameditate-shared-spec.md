# DhammaMeditate — Normative Cross-Platform Behavior Specification

- **Version:** 1.4 (Android v0.5.0 Reference Parity)
- **Status:** Normative Source of Truth
- **Reference Implementation:** Native Android (Kotlin, Jetpack Compose, Media3 ExoPlayer + MediaSessionService, Room SQLite v2)
- **Target Parity Platforms:** Android (reference) & Native iOS (Swift, SwiftUI, AVFoundation + `AVAudioSession` + `MPRemoteCommandCenter` + SQLite/GRDB/SwiftData)

This document defines the exact, unambiguous product behavior, data contracts, state machines, mathematical formulas, and flat visual tokens for **DhammaMeditate**. Any future native iOS port MUST satisfy every invariant and numeric reference vector specified below unless an explicit platform capability note applies.

---

## 1. Product Principles & Explicit Non-Goals

### Core Principles
1. **100% Offline & Local-First:** All audio files are copied into app-private storage upon import. No cloud streaming, user accounts, network calls, or analytics exist.
2. **Screen-Off Meditation Reliability:** Once started, playback and the monotonic meditation timer run reliably in the background with the screen locked until the target duration expires in a smooth exponential fade-out to silence.
3. **Two-Zone Session Structure:** Users assemble reusable sessions combining an optional untimed **Intro Queue** (0+ ordered tracks) and a required timed **Main Queue** (1+ ordered tracks) with a saved countdown duration validated against the combined Main Queue length.
4. **Fresh Start Discipline:** Every new session start begins from track index `0`, position `00:00`, and volume `1.0` (`100%`). There is no cross-session resume, seeking, scrubbing, Main-queue skipping, or playback speed adjustment. *(In-app **Skip to Main** during untimed Intro playback is the sole explicit exception.)*
5. **Advisory Distraction Awareness:** Before starting a fresh session, the app checks whether the device is quiet (Airplane Mode, Do Not Disturb, or Silent) and gently recommends silencing distractions without ever blocking meditation.
6. **Calm, Flat, Monastic Minimalism:** Strict flat UI (zero gradients, zero drop shadows, zero Material 3 tonal tint overlays, zero emojis) in Monastic Maroon & Saffron with Burmese Unicode-safe typography.

### Explicit v1 Non-Goals
- Outro tracks or closing bells.
- Seeking, scrubbing, Main-queue skipping, or playback speed controls (both in-app and on lock screen / headset remotes). Lock-screen and headset remotes never expose skip/next/previous actions even during Intro playback.
- Cross-session playback position resume.
- Cloud sync, streaming, accounts, or telemetry.
- User-configurable fade-out duration (fixed to `10,000 ms` exponential decay).
- Video-to-audio extraction or DRM-protected audio playback.

---

## 2. Data Model, Persistence & Cascade Rules

### 2.1 App-Private Audio Storage
- Imported files are copied into an app-private directory:
  - **Android:** `<appFilesDir>/audio/<trackId>.<extension>`
  - **iOS:** `<Application Support>/audio/<trackId>.<extension>` (marked `isExcludedFromBackup = true` unless user opts into device backup, and never reliant on ephemeral security-scoped bookmark lifetimes).
- File paths stored in the database are relative filenames (`local_file_name = "<trackId>.<ext>"`), sanitized to a single path segment on read (`basename`) to prevent path traversal.
- File writes are atomic: copy to `<trackId>.tmp`, validate/probe, sync to disk, atomically rename to `<trackId>.<ext>`, then insert the database row. On any failure or cancellation, delete both `.tmp` and target files.

### 2.2 Relational Schema (SQLite v2)

#### Table: `tracks`
| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `TEXT` | `PRIMARY KEY NOT NULL` | UUID v4 string |
| `title` | `TEXT` | `NOT NULL` | Display name (supports Burmese Unicode). Initialized from embedded audio title tag if non-blank; else filename without last extension; fallback `"Untitled Track"`. User-editable via post-import rename or Library rename; original app-private audio file is never modified. |
| `local_file_name` | `TEXT` | `NOT NULL, UNIQUE` | `<id>.<ext>` inside app-private `audio/` directory |
| `mime_type` | `TEXT` | `NOT NULL` | Probed audio MIME type (e.g., `audio/mpeg`, `audio/mp4`, `audio/wav`, `audio/flac`, `audio/ogg`) |
| `duration_ms` | `INTEGER` | `NOT NULL` (`> 0`) | Validated track duration in milliseconds |
| `created_at_epoch_ms` | `INTEGER` | `NOT NULL` | Unix epoch milliseconds; Library lists sorted `ORDER BY created_at_epoch_ms DESC` |

- Unique index: `local_file_name`.

#### Table: `sessions`
| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `TEXT` | `PRIMARY KEY NOT NULL` | UUID v4 string |
| `name` | `TEXT` | `NOT NULL` | Trimmed non-blank session title (supports Burmese Unicode) |
| `target_duration_ms` | `INTEGER` | `NOT NULL` | Saved Main Queue countdown in ms; `1..mainTotalDurationMs` when valid, or `0` if cascade track deletion emptied the Main Queue |
| `created_at_epoch_ms` | `INTEGER` | `NOT NULL` | Unix epoch milliseconds |
| `updated_at_epoch_ms` | `INTEGER` | `NOT NULL` | Unix epoch milliseconds; Sessions list sorted `ORDER BY updated_at_epoch_ms DESC` |

#### Table: `session_tracks`
| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `TEXT` | `PRIMARY KEY NOT NULL` | UUID v4 string (allows duplicate occurrences of the same track in a queue) |
| `session_id` | `TEXT` | `NOT NULL, FK -> sessions(id) ON DELETE CASCADE` | Owning session |
| `track_id` | `TEXT` | `NOT NULL, FK -> tracks(id) ON DELETE CASCADE` | Referenced library track |
| `zone` | `TEXT` | `NOT NULL` | Exact enum string: `"INTRO"` or `"MAIN"` |
| `position` | `INTEGER` | `NOT NULL` | Zero-based contiguous index (`0, 1, 2, ...`) within `(session_id, zone)` |

- Indexes:
  - `index_session_tracks_session_id (session_id)`
  - `index_session_tracks_track_id (track_id)`
  - `UNIQUE index_session_tracks_session_id_zone_position (session_id, zone, position)`

### 2.3 Derived Session Properties
- `introTotalDurationMs = sum(introItems.track.durationMs)`
- `mainTotalDurationMs = sum(mainItems.track.durationMs)`
- `needsMainTrack = mainItems.isEmpty()`
- `isPlayable = name.isNotBlank() && mainItems.isNotEmpty() && targetDurationMs in 1..mainTotalDurationMs`

### 2.4 Save & Delete Invariants
- **Saving a Session (Atomic Transaction):**
  1. If editing an existing session that is currently active in playback, stop and reset playback first (`stopIfActiveSessionAndWait`).
  2. Require `name.trim().isNotBlank()`, `mainTrackIds.isNotEmpty()`, all referenced tracks exist, `mainTotalDurationMs > 0`, and `targetDurationMs in 1..mainTotalDurationMs`.
  3. Upsert `sessions` row (preserving `created_at_epoch_ms` on edit, updating `updated_at_epoch_ms = now`).
  4. Replace all `session_tracks` rows for `session_id` with contiguous `0..n-1` positions per zone.
- **Deleting a Session:**
  1. If the session is currently active in playback, stop and reset playback first.
  2. Delete the `sessions` row (cascades `session_tracks`; library `tracks` and audio files remain untouched).
- **Safe Library Track Deletion (Atomic Transaction + File Cleanup):**
  1. Before confirmation, display a warning listing all referencing sessions (ordered by `updated_at_epoch_ms DESC`), explicitly flagging any session whose Main Queue will become empty (`willEmptyMainQueue`) and whether active playback will stop.
  2. If the track is part of active playback (Quick-Play or any track in an active saved session), stop and reset playback first.
  3. Inside one DB transaction:
     - Capture all `affectedSessionIds` referencing `track.id`.
     - Delete `tracks` row (cascading its `session_tracks` rows).
     - For each affected session:
       - Re-index remaining `INTRO` rows to contiguous `0..k-1` and remaining `MAIN` rows to `0..m-1`.
       - Compute `newTargetDurationMs`:
         - If `remainingMain` is empty → `0` (session enters `"Needs Main Track"` state).
         - Else if `session.targetDurationMs > remainingMainTotalMs` → auto-clamp to `remainingMainTotalMs`.
         - Else → keep `session.targetDurationMs`.
       - Update `target_duration_ms` **without** altering `updated_at_epoch_ms`.
  4. Delete the app-private audio file after the transaction commits.

---

## 3. Supported Audio Formats & Import Validation Contract

### 3.1 Supported Extensions & MIME Types
| Format | Extensions | Accepted MIME Types | Android Reference | iOS AVFoundation Parity Notes |
|---|---|---|---|---|
| MP3 | `.mp3` | `audio/mpeg`, `audio/mp3` | Native (`MediaExtractor` / ExoPlayer) | Native (`AVPlayer` / `AVURLAsset`) |
| M4A / AAC | `.m4a`, `.aac` | `audio/mp4`, `audio/m4a`, `audio/x-m4a`, `audio/aac`, `audio/aacp` | Native | Native |
| WAV | `.wav` | `audio/wav`, `audio/x-wav`, `audio/wave`, `audio/vnd.wave` | Native | Native |
| FLAC | `.flac` | `audio/flac`, `audio/x-flac` | Native | Native (iOS 11+) |
| OGG (Vorbis/Opus) | `.ogg`, `.oga` | `audio/ogg`, `application/ogg`, `audio/vorbis`, `audio/opus` | Native | **Platform Caveat:** `AVFoundation` does not decode OGG Vorbis/Opus containers natively (Opus in `.caf`/`.m4a` is supported, not `.ogg`). iOS port MUST either bundle a lightweight decoder (e.g., libvorbis/libopus or FFmpeg-free Ogg demuxer converting/decoding locally) OR explicitly surface an unsupported-container message if OGG decoding is deferred. |

### 3.2 Import Validation Rules
Each selected file is validated independently (batch imports succeed partially and report per-file failures):
1. **Extension / MIME Gate:** Match lowercase file extension against `{"mp3", "m4a", "aac", "wav", "flac", "ogg", "oga"}`, falling back to MIME mapping. Reject otherwise.
2. **Non-Empty Copy:** Copy to `<trackId>.tmp`; reject if `bytesCopied <= 0`.
3. **DRM Check:** Reject immediately if container carries DRM initialization data (`"DRM-protected audio files are not supported."`).
4. **Audio-Only Check:** Reject if any stream has a `video/*` MIME type (`"Video files are not supported. Select an audio file."`) or if zero `audio/*` streams exist.
5. **Duration Extraction:** Probe duration via platform metadata retriever, falling back to container track duration. Reject if `finalDurationMs <= 0`.
6. **Title Extraction:** Prefer trimmed non-blank embedded title metadata; fallback to display filename with last extension stripped; final fallback `"Untitled Track"`.

### 3.3 Custom Track Naming & Post-Import Rename Flow (v1.1)
Users can customize any track's display name (`tracks.title`) immediately after import or later from the Library. Renaming updates only the database `tracks.title` column; the app-private audio file (`local_file_name`) and its `created_at_epoch_ms` remain untouched.

1. **Title Validation (`TrackTitleValidation`):**
   - `normalize(raw) = raw.trim().ifEmpty { null }`.
   - Blank or whitespace-only titles are rejected with inline error `"Track name cannot be empty"` and disable the Save CTA.
   - Burmese Unicode script is supported verbatim.
2. **Post-Import Rename State Machine (`ImportRenameFlow`):**
   - **0 tracks imported** (all failed or picker cancelled): remain `Hidden`.
   - **1 track imported:** open the single-track rename dialog directly (`isBatch = false`), pre-filled with the extracted default title. Actions: `"Save"` (persists trimmed title if changed) and `"Keep Default"` / dismiss (keeps extracted title).
   - **N > 1 tracks imported:** open a flat batch prompt (`BatchPrompt`) asking whether to rename the imported tracks now, offering `"Rename One by One"` and `"Skip All"`.
   - Choosing `"Rename One by One"` steps sequentially through `1..N` (`Renaming`, `isBatch = true`), showing step indicator (`"Track i of N"`), pre-filled title input, `"Save & Next"` (or `"Save & Finish"` on the final track), `"Skip"` (keep default for current track and advance), and `"Skip All"` (dismiss remaining).
3. **Existing Track Rename & Live Propagation:**
   - Every track row in the Library exposes a `"Rename track"` action opening a flat dialog pre-filled with the current `track.title`.
   - On save, persist `UPDATE tracks SET title = :trimmedTitle WHERE id = :trackId`.
   - Because Sessions, Session Editor, Session Player, and Quick-Play Player observe reactive track/session queries, the new display name updates immediately across all screens.
   - If the renamed track is currently loaded in active playback (Quick-Play or any entry in an active Saved Session), refresh the active playback snapshot (`activeTrackTitle`), in-memory queue entry title, and Media3/lock-screen metadata (`ACTION_REFRESH_TRACK_TITLE` on Android; `MPNowPlayingInfoCenter` / `AVPlayerItem` metadata on iOS) **without** interrupting playback or resetting the countdown timer.

---

## 4. Strict Timer Validation & Snapping Rules

All rules below are implemented in `TimerValidation` and MUST behave identically on iOS.

### 4.1 Constants
- `MIN_DURATION_MS = 1_000` (1 second minimum bound when `maxDurationMs >= 1_000`; otherwise `maxDurationMs`)
- `DEFAULT_DURATION_MS = 1_800_000` (30 minutes)
- `PRESET_MINUTES = [10, 15, 30, 45, 60]` plus `"Full"`
- Scope labels for notices:
  - Quick-Play single track: `"track length"`
  - Saved two-zone session: `"Main Queue length"`

### 4.2 Validation & Snapping Rules
1. **Default Duration:** When a track is opened in Quick-Play or the first track is added to an empty Main Queue, default timer to `min(DEFAULT_DURATION_MS, maxDurationMs)` (returns `0` if `maxDurationMs <= 0`).
2. **Preset Enabling:** A preset of `M` minutes is enabled iff `M > 0 && maxDurationMs > 0 && (M * 60_000) <= maxDurationMs`. Disabled presets cannot be selected.
3. **Full Option:** Enabled whenever `maxDurationMs > 0`; sets `targetDurationMs = maxDurationMs`.
4. **Custom Minutes Input:**
   - Accepts digits only (max 4 digits).
   - Values `< 1` (e.g., `"0"`) show inline notice `"Enter at least 1 minute"` and block `"Save Session"` / `"Start Session"`.
   - Values `>= 1` compute `requestedMs = minutes * 60_000`.
   - If `requestedMs > maxDurationMs`: **snap immediately** to `maxDurationMs`, mark `wasCapped = true` (Full selected), and display inline notice:
     `Adjusted to <scopeLabel> (<formattedMaxDuration>)`
5. **Reconciling Timer After Main Queue Edit (`reconcileTimerAfterMainChange`):**
   - If `newMainTotalMs <= 0`: set `targetDurationMs = 0`, clear notice, disable timer card & `"Save Session"`.
   - Else if `previousMainTotalMs <= 0 || currentDurationMs <= 0`: set `defaultDurationMs(newMainTotalMs)`.
   - Else if `currentDurationMs > newMainTotalMs`: snap down to `newMainTotalMs` with inline notice `"Adjusted to Main Queue length (<formattedNewMax>)"`.
   - Else: preserve `currentDurationMs`.
6. **Needs Main Track Guard:** Any saved session with `mainItems.isEmpty()` displays `"Needs Main Track"` badge, opens the Editor on tap from the Sessions list, and disables `"Start Session"` if viewed in the Player.
7. **Duration Formatting (`formatDuration`):**
   - Negative inputs clamp to `0`.
   - `0 ms` → `0` seconds (`"00:00"`).
   - `1..999 ms` → `1` second (`"00:01"`) so the countdown never displays `"00:00"` before actual completion.
   - `>= 1000 ms` → `floor(ms / 1000)` seconds.
   - If `hours > 0`: `"H:MM:SS"` (e.g., `"1:05:00"`). Otherwise: `"MM:SS"` (e.g., `"09:59"`, `"30:00"`).

---

## 5. Two-Zone Playback State Machine & Restricted Controls

### 5.1 7-State Finite State Machine (`PlaybackStatus`)
| State | Active? | Audio Playing? | Countdown Moving? | Volume |
|---|---|---|---|---|
| `IDLE` | No | No | No (`elapsed = 0`, `remaining = target`) | `1.0` |
| `INTRO_PLAYING` | Yes | Yes (Intro zone) | **Frozen** (`elapsed = 0`, `remaining = target`) | `1.0` |
| `INTRO_PAUSED` | Yes | No (Intro zone) | **Frozen** (`elapsed = 0`, `remaining = target`) | `1.0` |
| `PLAYING` | Yes | Yes (Main zone) | Running (`remaining > effectiveFadeMs`) | `1.0` |
| `PAUSED` | Yes | No (Main zone) | Frozen at `accumulatedElapsedMs` | `FadeCurve.gain(remaining, target)` |
| `FADING` | Yes | Yes (Main zone) | Running (`0 < remaining <= effectiveFadeMs`) | `FadeCurve.gain(remaining, target)` |
| `COMPLETED` | No | No | Stopped (`elapsed = target`, `remaining = 0`) | `0.0` |

### 5.2 State Transitions
```text
IDLE / COMPLETED
  -- start(targetMs, hasIntro=true)  --> INTRO_PLAYING   (queue index 0, pos 00:00, vol 1.0)
  -- start(targetMs, hasIntro=false) --> PLAYING         (if targetMs > effectiveFadeMs)
                                     --> FADING          (if targetMs <= effectiveFadeMs)

INTRO_PLAYING
  -- pause()                         --> INTRO_PAUSED
  -- onIntroComplete() [one-shot]    --> PLAYING         (if targetMs > effectiveFadeMs, elapsed=0)
                                     --> FADING          (if targetMs <= effectiveFadeMs, elapsed=0)
  -- skipIntroToMain() [in-app only] --> PLAYING         (if targetMs > effectiveFadeMs, elapsed=0, jumps to Main track 1)
                                     --> FADING          (if targetMs <= effectiveFadeMs, elapsed=0, jumps to Main track 1)
  -- stopAndReset()                  --> IDLE
  -- queue ended early               --> COMPLETED       (completeEarly)

INTRO_PAUSED
  -- resume()                        --> INTRO_PLAYING
  -- onIntroComplete() [one-shot]    --> PAUSED          (elapsed=0, remaining=target)
  -- skipIntroToMain() [in-app only] --> PLAYING         (if targetMs > effectiveFadeMs, elapsed=0, jumps to Main track 1 & plays)
                                     --> FADING          (if targetMs <= effectiveFadeMs, elapsed=0, jumps to Main track 1 & plays)
  -- stopAndReset()                  --> IDLE

PLAYING
  -- tick(now) when remaining <= effectiveFadeMs --> FADING
  -- tick(now) when remaining == 0               --> COMPLETED
  -- pause(now)                                  --> PAUSED (or COMPLETED if elapsed >= target)
  -- stopAndReset()                              --> IDLE
  -- queue ended early                           --> COMPLETED (completeEarly)

FADING
  -- tick(now) when remaining > 0    --> FADING (updates volume = FadeCurve.gain)
  -- tick(now) when remaining == 0   --> COMPLETED (volume = 0.0, pause & rewind to (0, 0))
  -- pause(now)                      --> PAUSED (or COMPLETED if elapsed >= target)
  -- stopAndReset()                  --> IDLE
  -- queue ended early               --> COMPLETED (completeEarly)

PAUSED
  -- resume(now) when remaining > effectiveFadeMs        --> PLAYING (vol 1.0)
  -- resume(now) when 0 < remaining <= effectiveFadeMs   --> FADING  (vol = FadeCurve.gain)
  -- resume(now) when remaining == 0                     --> COMPLETED
  -- stopAndReset()                                      --> IDLE
```

### 5.3 Monotonic Clock Rule (Zero Drift)
- Elapsed time MUST be computed strictly from a monotonic system clock that continues advancing during device sleep: `SystemClock.elapsedRealtime()` on Android, and `mach_continuous_time()` (or Swift `ContinuousClock` / `CLOCK_MONOTONIC_RAW`-equivalent continuous time) on iOS. Do **not** use `CACurrentMediaTime()` or `ProcessInfo.processInfo.systemUptime` on iOS, as they may pause during deep sleep.
- NEVER accumulate elapsed time by counting `delay()` or `Timer` ticks.
- Formula while running in `PLAYING` or `FADING`:
  - `segmentDelta = max(0, nowMs - segmentStartedAtMs)`
  - `elapsedMs = min(targetDurationMs, accumulatedElapsedMs + segmentDelta)`
  - `remainingMs = max(0, targetDurationMs - elapsedMs)`
- On `pause(nowMs)`: evaluate `tick(nowMs)` first. If `COMPLETED`, finish at zero; otherwise store `accumulatedElapsedMs = current.elapsedMs` and clear `segmentStartedAtMs = null`.
- On `onIntroComplete(nowMs)`: strictly **one-shot**. Only transitions out of `INTRO_PLAYING` or `INTRO_PAUSED`; subsequent Main → Main track boundary transitions MUST NOT re-anchor or reset `segmentStartedAtMs`.
- On `skipIntroToMain(nowMs)` **(v1.1, in-app only)**:
  - Only active when `status ∈ {INTRO_PLAYING, INTRO_PAUSED}` and `activeIntroCount > 0 && activeMainCount > 0`; strict no-op in all other states (`IDLE`, `PLAYING`, `PAUSED`, `FADING`, `COMPLETED`).
  - Unlike `onIntroComplete` (which preserves `PAUSED` when triggered from `INTRO_PAUSED`), `skipIntroToMain` transitions **both** `INTRO_PLAYING` and `INTRO_PAUSED` directly into `PLAYING` (or `FADING` if `targetDurationMs <= effectiveFadeMs`) with `accumulatedElapsedMs = 0`, `segmentStartedAtMs = nowMs`, and `volume = 1.0`.
  - Immediately jumps the player past all remaining Intro tracks to Main track 1 (`playlistIndex = activeIntroCount`, position `0 ms`), sets `volume = skippedTick.volume`, and starts playback inside an internal-change guard so transition listeners do not double-fire `onIntroComplete()` or `resumeTimerOnly()`.
  - If audio focus is blocked or suppressed when playback starts, fall back cleanly via `pauseTimerOnly()` so timer and audio remain synchronized in `PAUSED` at `00:00` of Main track 1.
  - Never re-triggers the Distraction Check dialog.

### 5.4 Restricted In-App & Lock-Screen Controls
- **In-App Controls:**
  - `IDLE` / `COMPLETED`: `"Start Session"` only.
  - `INTRO_PLAYING` (Saved Session Player only, when `mainItems.isNotEmpty()`): `"Pause"` (full-width primary) + 50/50 secondary row (`"Skip to Main"` | `"Stop & Reset"`).
  - `INTRO_PAUSED` (Saved Session Player only, when `mainItems.isNotEmpty()`): `"Continue"` (full-width primary) + 50/50 secondary row (`"Skip to Main"` | `"Stop & Reset"`).
  - `PLAYING` / `FADING`: `"Pause"` + full-width `"Stop & Reset"` (no `"Skip to Main"`).
  - `PAUSED`: `"Continue"` + full-width `"Stop & Reset"` (no `"Skip to Main"`).
  - `"Skip to Main"` is **never** shown on Quick-Play `PlayerScreen` or `ActiveSessionBanner`.
  - Progress bar is strictly **non-interactive** (4pt/4dp flat bar; no scrub gesture).
  - Queue overview in Player is strictly **read-only** (no tap-to-jump).
- **Lock-Screen / External Media Controls:**
  - **Allowed:** Play/Pause (`COMMAND_PLAY_PAUSE` ↔ `MPRemoteCommandCenter.playCommand`, `pauseCommand`, `togglePlayPauseCommand`) and Stop & Reset (`COMMAND_STOP` + custom `ACTION_STOP_RESET` ↔ `MPRemoteCommandCenter.stopCommand`).
  - **Explicitly Disabled / Ignored:** Seek forward/backward, scrub position (`changePlaybackPositionCommand`), next/previous track (`nextTrackCommand`, `previousTrackCommand`), playback rate (`changePlaybackRateCommand`), ratings/bookmarks, and `ACTION_SKIP_TO_MAIN` (strictly in-app only).
  - Lock-screen Play from `IDLE` / `COMPLETED` restarts the loaded session/track fresh from `(0, 00:00, volume 1.0)`.

---

## 6. Exponential Fade-Out Specification & Reference Test Vectors

### 6.1 Constants & Cadence
- `FADE_DURATION_MS = 10_000` (10 seconds)
- `DECAY_K = 3.5`
- Normal polling cadence (during `PLAYING`): `250 ms` (clamped dynamically to `msUntilFade` right before entering the fade window so fade onset is accurate).
- Fade polling cadence (during `FADING`): `50 ms` (clamped to `max(1, remainingMs)` near completion).

### 6.2 Exact Equation
For target duration $T = \max(0, \text{targetDurationMs})$ and remaining time $r$:
1. Effective fade window:
   $$F = \min(10\,000,\; T)$$
2. If $F \le 0$ or $r \le 0$, gain $g = 0.0$.
3. If $r \ge F$, gain $g = 1.0$.
4. Otherwise, normalized fade progress $p \in [0, 1]$:
   $$p = \text{clamp}\!\left(\frac{F - r}{F},\; 0.0,\; 1.0\right)$$
5. Exponential gain (normalized to hit exact endpoints $g(0) = 1.0$ and $g(1) = 0.0$):
   $$g(p) = \frac{e^{-3.5p} - e^{-3.5}}{1 - e^{-3.5}}$$
   where $e^{-3.5} \approx 0.0301973834$ and $1 - e^{-3.5} \approx 0.9698026166$.

### 6.3 Normative Reference Test Vectors (Tolerance: $\pm 10^{-5}$)

#### Standard Progress Vectors (`gainForProgress(p)`)
| Progress `p` | Remaining (`T >= 10,000 ms`) | Expected Gain `g(p)` |
|---|---|---|
| `0.00` | `10,000 ms` | `1.000000` |
| `0.10` | `9,000 ms` | `0.695493` |
| `0.25` | `7,500 ms` | `0.398704` |
| `0.50` | `5,000 ms` | `0.148047` |
| `0.75` | `2,500 ms` | `0.043558` |
| `0.90` | `1,000 ms` | `0.013049` |
| `1.00` | `0 ms` | `0.000000` |

#### Short-Target Vectors (`T = 3,000 ms`, `effectiveFadeMs = 3,000 ms`)
| Remaining `r` | Progress `p` | Expected Gain `gain(r, 3000)` |
|---|---|---|
| `3,000 ms` | `0.00` | `1.000000` |
| `2,250 ms` | `0.25` | `0.398704` |
| `1,500 ms` | `0.50` | `0.148047` |
| `750 ms` | `0.75` | `0.043558` |
| `0 ms` | `1.00` | `0.000000` |

---

## 7. Screen-Off Reliability & Audio Interruption Contract

### 7.1 Background Execution & Audio Session Configuration
- **Android:** Foreground `MediaSessionService` (`foregroundServiceType="mediaPlayback"`), `AudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_SPEECH)`, `handleAudioFocus = true`, `handleAudioBecomingNoisy = true`, `WAKE_MODE_LOCAL` (active only while playing; released when paused/idle/completed).
- **iOS:** `UIBackgroundModes = ["audio"]`, `AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [])`. Using `.spokenAudio` mirrors Android's `AUDIO_CONTENT_TYPE_SPEECH` so navigation prompts and system interruptions pause instead of ducking over Dhamma talks.

### 7.2 Interruption & Boundary Rules
| Event | Required Behavior |
|---|---|
| **Transient Interruption** (incoming phone call, Siri/navigation speech interruption) | Pause both audio and monotonic timer (`PLAYING`/`FADING` → `PAUSED`; `INTRO_PLAYING` → `INTRO_PAUSED`). When the interruption ends (`shouldResume`), resume both audio and timer **only if** the user did not manually pause or stop during the interruption, restoring exact `FadeCurve.gain(remaining, target)` volume if inside the fade window. |
| **Route Change / Headphones Unplugged** (`ACTION_AUDIO_BECOMING_NOISY` ↔ `AVAudioSession.routeChangeNotification` `.oldDeviceUnavailable`) | Pause both audio and timer; stay paused until the user explicitly taps `"Continue"`. |
| **Permanent Audio Focus Loss** (another media app takes audio focus) | Pause both audio and timer; stay paused until the user explicitly taps `"Continue"`. |
| **Pause at or past `targetDurationMs`** | If `timerEngine.pause(now)` returns `COMPLETED`, immediately route through `finishSessionAtZero` (`volume = 0.0`, pause player, rewind to index `0`, position `0`, publish `COMPLETED`). |
| **Natural Queue End (`STATE_ENDED` / `AVPlayerItemDidPlayToEndTime` on final item)** | Only when status is active-playing (`INTRO_PLAYING`, `PLAYING`, `FADING`), complete cleanly via `completeEarly()` → `finishSessionAtZero` (`COMPLETED`, `remaining = 0`, `volume = 0.0`, rewound to `(0, 0)`). Ignore `STATE_ENDED` while `IDLE`, `COMPLETED`, `PAUSED`, or `INTRO_PAUSED`. |
| **Playback Error** (corrupt/missing file) | Cancel tick loop, reset timer to `IDLE`, pause & rewind player to `(0, 0)`, reset `volume = 1.0`, and publish a dismissible inline error message. |
| **Fresh Start Invariant** | Every `startQuickTrackSession` / `startSavedSession` cancels any pending tick loop, rebuilds the ordered playlist (`introItems + mainItems`), sets `index = 0`, `position = 0 ms`, `volume = 1.0`, and begins playback. |

---

## 8. Pre-Session Distraction Check Contract

### 8.1 Trigger Scope
- Evaluated **only** when the user taps fresh `"Start Session"` from `IDLE` or `COMPLETED` on either the Saved Session Player or Library Quick-Play screen.
- **Never** triggered when tapping `"Continue"` from `PAUSED` or `INTRO_PAUSED`, or when tapping `"Skip to Main"` during Intro playback.
- Strictly **advisory**: `"Continue anyway"` is always available so meditation is never blocked.

### 8.2 Pure Readiness Predicate (`DistractionPolicy.evaluate`)
Inputs: `DistractionSnapshot(isAirplaneModeOn: Boolean, isDndActive: Boolean, ringerState: RingerState)` where `RingerState ∈ {SILENT, VIBRATE, NORMAL, UNKNOWN}`.

$$\text{isReady} \iff \text{isAirplaneModeOn} \lor \text{isDndActive} \lor (\text{ringerState} == \text{SILENT})$$

| `isAirplaneModeOn` | `isDndActive` | `ringerState` | `isReady` | `statusSummary` | `guidanceText` |
|---|---|---|---|---|---|
| `true` | any | any | `true` | `"Airplane Mode is on. Ready for quiet meditation."` | `"Device is quiet and ready for meditation."` |
| `false` | `true` | any | `true` | `"Do Not Disturb is active. Ready for quiet meditation."` | `"Device is quiet and ready for meditation."` |
| `false` | `false` | `SILENT` | `true` | `"Phone is silenced. Ready for quiet meditation."` | `"Device is quiet and ready for meditation."` |
| `false` | `false` | `NORMAL` | `false` | `"Airplane Mode is off and your ringer is on."` | `"Turn on Airplane Mode or silence alerts so calls and notifications do not break your stillness."` |
| `false` | `false` | `VIBRATE` | `false` | `"Airplane Mode is off and vibration is on."` | `"Turn on Airplane Mode or silence alerts so calls and notifications do not break your stillness."` |
| `false` | `false` | `UNKNOWN` | `false` | `"Airplane Mode is off and alerts may interrupt your session."` | `"Turn on Airplane Mode or silence alerts so calls and notifications do not break your stillness."` |

### 8.3 Prompt Flow & Live Re-Check
1. If `eval.isReady == true`: bypass the prompt and proceed immediately to playback start (and on Android API 33+, the one-time `POST_NOTIFICATIONS` check).
2. If `eval.isReady == false`: display the flat `DistractionCheckDialog`:
   - Title: `"Silence Distractions?"`
   - Primary outlined action: `"Open Settings"` (keeps dialog open so returning from Settings re-checks automatically).
   - Filled Saffron action: `"Continue anyway"` (dismisses dialog and proceeds immediately to start).
   - Subtle `"Cancel"` / outside tap / Back: dismisses without starting playback.
3. On app foreground resume (`ON_RESUME` on Android; `scenePhase == .active` / `willEnterForegroundNotification` on iOS) while the dialog is visible, re-evaluate readiness. If `isReady` becomes `true`, update the dialog in place:
   - Icon switches to CheckCircle (`#C98A1E` Lotus Gold), Title to `"Ready to Meditate"`, primary Saffron CTA to `"Start Session"` (never auto-starts audio unannounced).

### 8.4 Platform Capability Mapping (Android vs. iOS)
- **Android (Zero New Permissions):**
  - `isAirplaneModeOn`: `Settings.Global.getInt(contentResolver, AIRPLANE_MODE_ON, 0) != 0`
  - `isDndActive`: `NotificationManager.currentInterruptionFilter` (`!= INTERRUPTION_FILTER_ALL && != INTERRUPTION_FILTER_UNKNOWN`)
  - `ringerState`: `AudioManager.ringerMode` (`RINGER_MODE_SILENT` / `VIBRATE` / `NORMAL` / else `UNKNOWN`)
  - Each read wrapped in safe error handling with conservative fallbacks (`false` / `UNKNOWN`).
  - Settings intent fallback chain: `ACTION_AIRPLANE_MODE_SETTINGS` → `ACTION_WIRELESS_SETTINGS` → `ACTION_SETTINGS`.
- **iOS Platform Capability Notes:**
  - Apple does not expose public APIs to read the hardware Silent switch, Focus/Do Not Disturb status (without Communication Notifications entitlement), or Airplane Mode toggle directly, nor a public URL scheme to open Airplane Mode Settings directly (`App-Prefs:` is private/rejected on App Store).
  - Recommended iOS implementation strategy:
    - Detect cellular/Wi-Fi offline state conservatively via `NWPathMonitor` (`path.status == .unsatisfied` as an airplane/offline indicator) or treat readiness as `UNKNOWN` when network interfaces are active.
    - Present the same flat advisory prompt (`"Silence Distractions?"`) instructing the user to open Control Center to enable Airplane Mode / Focus or flip the Silent switch, with `"Continue anyway"` as the instant 1-tap proceed action (and optionally `UIApplication.openSettingsURLString` or a brief Control Center hint rather than a broken private URL scheme).

---

## 9. Flat Visual Design Tokens & Burmese Unicode Typography

### 9.1 Strict Flat Rules
- **Zero gradients** (`Brush.*` / `LinearGradient` / `RadialGradient`).
- **Zero drop shadows** (`shadowElevation = 0`, `.shadow(...)` forbidden).
- **Zero Material 3 tonal tint overlays** (`tonalElevation = 0.dp` explicitly set on all dialogs, bottom sheets, and navigation bars).
- **Zero emojis** in any UI copy.
- Flat depth is achieved exclusively through solid color contrast and crisp `1pt / 1dp` outline borders.

### 9.2 Color Palette (`ui/theme/Color.kt`)
| Token | Hex | Role |
|---|---|---|
| `MonasticMaroon` | `#5E2418` | Top headers, active session banner, player canvas, selected nav/outline accents |
| `Saffron` | `#F06A1A` | Primary flat CTAs (`Start Session`, `Pause`, `Continue`, `Save Session`, `Import Audio`, `New Session`), selected timer chips |
| `DeepSaffron` | `#E05614` | Disabled/pressed saffron container, picker badge accent |
| `LotusGold` | `#C98A1E` | Non-interactive progress fill, active queue dot/duration, ready checkmark icon, `Skip to Main` `1dp` border |
| `LightNeutral` | `#F3F3F4` | Light screen canvas (`Sessions`, `Library`, `Session Editor`), inactive chip fill |
| `CardSurface` | `#F8F8F9` | Cards, dialogs, bottom sheets, bottom navigation bar |
| `OutlineNeutral` | `#D4D4D8` | `1dp` card, dialog, divider, and input borders |
| `InkPrimary` | `#1F1F1F` | Primary text on light surfaces |
| `InkSecondary` | `#6E6E73` | Secondary metadata, hints, inactive icons |
| `AlertRed` | `#D93025` | Validation errors, timer cap notices, `Needs Main Track`, delete actions |
| `AlertSurface` | `#FDF2F2` | Flat error/alert banner fill |
| `PureWhite` | `#FFFFFF` | Text/icons on maroon/saffron surfaces, text input fields |
| `WhiteMuted` | `#D9C7C2` | Secondary text/subtitles on `MonasticMaroon` canvas |
| `MaroonOutline` | `#834234` | `1dp` outlines on dark maroon surfaces (`Stop & Reset`, queue card) |
| `MaroonSurfaceSubtle` | `#4E1D13` | Read-only queue overview card, zone status pill, and `Skip to Main` fill on player canvas |
| `ProgressTrackDark` | `#3E160E` | Non-interactive progress bar track |
| `NavActivePill` | `#EFE5E2` | Active bottom navigation indicator pill |
| `SaffronTintSurface` | `#FDF0E7` | Timer badge & active-edit warning banner fill |

### 9.3 Shapes & Radii
- Cards, dialogs, primary buttons, banners: `16dp / 16pt` corner radius.
- Chips, text fields, queue item rows: `12dp / 12pt` corner radius.
- Status pills: `20dp / 20pt` pill radius; compact badges (`Timer`, `Needs Main Track`): `8dp / 8pt`.
- Bottom sheet top corners: `20dp / 20pt`.
- Progress bar: `4dp / 4pt` height, `2dp / 2pt` radius.

### 9.4 Typography & Burmese Unicode Rendering Safety
Burmese Unicode script (e.g., `တရားတော်`, `မေတ္တာဘာဝနာ`, stacked subjoined consonants and tall upper/lower diacritics) clips vertically under tight Latin line heights. Every text style MUST use a `1.45x–1.5x` line-height multiplier and safe ellipsis/wrapping (`maxLines = 2, overflow = Ellipsis` on titles/names; `maxLines = 1, overflow = Ellipsis` on compact banners):

| Style | Weight | Size | Line Height | Multiplier | Usage |
|---|---|---|---|---|---|
| `CountdownTextStyle` | SemiBold | `52sp` | `64sp` | `1.23x` (tabular digits `tnum`, `letterSpacing = 1sp`) | Primary countdown clock (`MM:SS` / `H:MM:SS`) |
| `headlineMedium` | SemiBold | `24sp` | `35sp` | `1.46x` | App header title |
| `titleLarge` | Medium | `20sp` | `29sp` | `1.45x` | Player session/track title, dialog titles, editor bar |
| `titleMedium` | Medium | `16sp` | `24sp` | `1.50x` | Card titles, session/track names |
| `bodyLarge` | Normal | `16sp` | `24sp` | `1.50x` | Session Name text input |
| `bodyMedium` | Normal | `14sp` | `21sp` | `1.50x` | Body copy, queue rows, dialog summaries |
| `bodySmall` | Normal | `13sp` | `19sp` | `1.46x` | Durations, metadata, cap notices, guidance copy |
| `labelLarge` | SemiBold | `15sp` | `22sp` | `1.47x` | Buttons, active track subtitle |
| `labelMedium` | Medium | `13sp` | `19sp` | `1.46x` | Chips, zone pills, nav labels, badges |

---

## 10. Bilingual Localization (English & Burmese `မြန်မာ`)

### 10.1 Supported Languages & First-Launch Default
- **Supported locales:** English (`en`) and Burmese (`my`).
- **First-launch resolution (when no user preference is saved):**
  - If the device system language code is `"my"` (e.g., `my`, `my-MM`), initialize to **Burmese (`my`)**.
  - Otherwise, initialize to **English (`en`)**.
- **Persistence:**
  - **Android:** `SharedPreferences` (`"dhamma_meditate_prefs"`, key `"app_language_tag"` = `"en"` | `"my"`).
  - **iOS:** `UserDefaults.standard` (same key `"app_language_tag"` = `"en"` | `"my"`).
  - Once explicitly selected by the user, the saved preference overrides the device locale across app restarts.

### 10.2 In-App Switcher & Live Switching Contract
- **Placement:** Compact flat segmented toggle pill (`EN` | `မြန်မာ`) in the top-right of the `Sessions` and `Library` Monastic Maroon headers. The toggle labels always use native autonyms (`EN` and `မြန်မာ`).
- **Instant, Playback-Safe Switching:** Toggling `EN` ↔ `မြန်မာ` MUST update all visible UI strings immediately without restarting the app or interrupting active audio playback / countdown ticks.
  - **Android:** `CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides localizedContext.resources.configuration)` at the Compose root; `MeditationPlaybackService` resolves error/notification strings from `AppLanguageStore.localizedContext(this)`.
  - **iOS:** SwiftUI `.environment(\.locale, selectedLocale)` (or bundle-backed `Localizable.xcstrings` lookup driven by an `Observable` language store) so all views re-render live without recreating the audio engine.

### 10.3 Western Numerals & Burmese Layout Invariants
- **Western Numerals (`0-9`):** In Burmese mode, all countdown timers (`00:00`, `45:00`), track durations, preset numbers (`10 မိနစ်`), and counts MUST continue displaying Western ASCII digits (`0-9`) using `Locale.US` (Android) / `Locale(identifier: "en_US_POSIX")` (iOS) — never localized Burmese digits (`၀-၉`).
- **Session Card Duration-Only Summary:** Session cards display duration-only summaries without track counts:
  - English: `Intro: 05:00 · Main: 45:00` (or `No Intro` / `Main: None`).
  - Burmese: `ပရိကံ : 05:00 · ကမ္မဋ္ဌာန်းတရား : 45:00` (or `ပရိကံတရား မပါဝင်ပါ` / `ကမ္မဋ္ဌာန်းတရား : မရှိပါ`).
- **Tight Controls:** Side-by-side buttons (such as the 50/50 `Skip to Main` | `Stop & Reset` row during Intro playback) MUST allow up to 2 centered lines (`heightIn(min = 50dp)` / flexible min-height in SwiftUI) so Burmese labels never clip on narrow screens.

### 10.4 Canonical English ↔ Burmese Terminology & String Catalog
The complete 217-key English and Burmese string catalog lives in:
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-my/strings.xml`

The iOS port MUST import these exact 217 keys and Burmese strings verbatim into `Localizable.xcstrings`. Core domain terms:

| Concept | English | Canonical Burmese (`my`) |
|---|---|---|
| App Name | `DhammaMeditate` | `တရားဖော်` |
| Sessions Tab / Header | `Sessions` / `Meditation Sessions` | `တရားချိန်များ` |
| Library Tab / Header | `Library` / `Offline Audio Library` | `တရားများ` / `သိမ်းဆည်းထားသောတရားများ` |
| Intro Queue (Untimed) | `Intro Queue` / `Intro` | `ပရိကံတရား` / `ပရိကံ` |
| Main Queue (Timed) | `Main Queue` / `Main` | `ကမ္မဋ္ဌာန်းတရား` / `ကမ္မဋ္ဌာန်း` |
| Meditation Timer | `Meditation Timer` | `တရားထိုင်ကြာချိန်` |
| Start / Pause / Continue | `Start Session` / `Pause` / `Continue` | `စတင်မည်` / `ခေတ္တရပ်မည်` / `ဆက်ဖွင့်မည်` |
| Skip to Main | `Skip to Main` | `ကမ္မဋ္ဌာန်းသို့ ကျော်မည်` |
| Stop & Reset | `Stop & Reset` | `ရပ်၍ အစမှပြန်စမည်` |
| Save / Cancel / Delete | `Save` / `Cancel` / `Delete` | `သိမ်းဆည်းမည်` / `မလုပ်ပါ` / `ဖျက်မည်` |
| Preset Minutes / Full | `{1}m` / `Full` | `{1} မိနစ်` / `အပြည့်` |
| Distraction Prompt | `Silence Distractions?` / `Continue anyway` | `အနှောင့်အယှက်များ ပိတ်ထားမည်လား?` / `ဆက်လက်စတင်မည်` |
| Tutorials Launcher / Title | `Tutorials` / `Guided Tutorials` | `အသုံးပြုနည်း` / `အသုံးပြုနည်း လမ်းညွှန်များ` |
| Tutorial Actions | `Skip` / `Next` / `Got it` | `ကျော်မည်` / `နောက်တစ်ဆင့်` / `နားလည်ပါပြီ` |
| Tutorial Step Progress | `Step {1} / {2}` | `အဆင့် {1} / {2}` |
| Tutorial Topics | `How to add an audio` / `How to create a session` | `တရားဖိုင် ထည့်သွင်းနည်း` / `တရားချိန် ဖန်တီးနည်း` |
| Quick Meditate Title / CTA | `Quick Meditate` | `အမြန်တရားထိုင်မည်` |
| Quick Meditate Reset / Start | `Reset` / `Start Meditation` | `အကုန်ဖျက်မည်` / `တရားစထိုင်မည်` |

---

## 11. Guided Tutorials (Interactive Spotlight Walkthroughs)

### 11.1 First-Time Auto-Offer & Persistence
- **Preference key:** `"tutorial_offer_seen"` (`Boolean`, default `false`) stored in:
  - **Android:** `SharedPreferences` (`"dhamma_meditate_prefs"`).
  - **iOS:** `UserDefaults.standard`.
- **Auto-offer gate:** Show the flat welcome dialog (`Welcome to DhammaMeditate`) automatically ONLY when:
  1. `hasSeenOffer == false`, AND
  2. No playback session/track screen is currently active (`!hasActivePlayback`), AND
  3. No tutorial walkthrough is currently running (`!hasActiveTutorial`).
- **Dismissal:** Tapping **View Tutorials**, **Skip**, or dismissing the dialog immediately persists `tutorial_offer_seen = true` so the user is never prompted again on subsequent launches.

### 11.2 On-Demand Launcher & Topic List
- **Launcher placement:** A compact flat pill button (`Tutorials` / `အသုံးပြုနည်း`, `NavActivePill` fill, `1dp` `OutlineNeutral` border, `MonasticMaroon` icon + text, `12dp` radius) sits right-aligned directly above the anchored bottom primary CTA on both the `Sessions` (`New Session`) and `Library` (`Import Audio`) screens.
- **Topic list dialog:** Opens a flat modal listing all registered tutorials with their title, 1-line description, and step count pill formatted with Western ASCII digits (`0-9`).

### 11.3 Extensible Tutorial Registry & Walkthroughs
All walkthroughs are driven by a data registry (`TutorialId`, `TutorialTargetId`, `TutorialStartRoute`, `TutorialStepInteraction = TAP_TARGET | CONFIRM_ONLY`, optional `emptyLibraryNoteRes`).

| Tutorial ID | Start Route | Steps |
|---|---|---|
| `ADD_AUDIO` | `HOME_SESSIONS` | 1. `NAV_LIBRARY_TAB` (`TAP_TARGET` → switches to Library tab)<br>2. `LIBRARY_IMPORT_BUTTON` (`CONFIRM_ONLY` → `Got it`, ends without opening OS file picker) |
| `CREATE_SESSION` | `HOME_SESSIONS` | 1. `SESSIONS_NEW_BUTTON` (`TAP_TARGET` → opens live `SessionEditorScreen`)<br>2. `EDITOR_INTRO_CARD` (`CONFIRM_ONLY` → `Next`, auto-scrolls into view)<br>3. `EDITOR_MAIN_CARD` (`CONFIRM_ONLY` → `Next`, auto-scrolls into view; displays flat `SaffronTintSurface` empty-Library note when Library has 0 tracks)<br>4. `EDITOR_TIMER_CARD` (`CONFIRM_ONLY` → `Next`, auto-scrolls into view)<br>5. `EDITOR_SAVE_BUTTON` (`CONFIRM_ONLY` → `Got it`, reminds user to enter Session Name at the top; dismisses overlay and stays on `SessionEditorScreen` with zero fake saves) |

### 11.4 Spotlight Overlay & Non-Destructive Invariants
- **Visual styling:** Flat `InkPrimary` (`#1F1F1F`) backdrop at `68%` alpha with a clear rounded cutout (`16dp` corner radius, `6dp` padding around the target bounds, `2dp` `Saffron` border) and an auto-positioned flat `CardSurface` tooltip (`1dp` `OutlineNeutral` border, `0dp` elevation) placed above targets in the lower half of the viewport and below targets in the upper half.
- **Interaction & hit-testing:**
  - Taps outside the cutout are consumed without affecting underlying UI.
  - On `TAP_TARGET` steps, tapping inside the cutout triggers the mapped navigation action and advances the step.
  - On `CONFIRM_ONLY` steps, tapping the primary button (`Next` for non-final steps, `Got it` for the final step) or tapping inside the cutout advances/finishes the step without invoking destructive or external actions (never opens OS file pickers or saves synthetic sessions).
  - Tapping **Skip** or system back immediately terminates the active tutorial on the current screen.
  - Entering active playback (e.g., via notification or active session banner) clears any active tutorial overlay.
- **Auto-scrolling:** Scrollable screens (`SessionEditorScreen`) scroll each active target card into view automatically (`BringIntoViewRequester` on Android; `ScrollViewReader.scrollTo(...)` on iOS) so the spotlight cutout stays aligned without manual scrolling.
- **Western numerals:** `Step X / N` (`အဆင့် X / N`) and step counts MUST always format numbers with `Locale.US` / `en_US_POSIX` (`0-9`).

---

## 12. Quick Meditate (One-Off Ephemeral Two-Zone Session)

### 12.1 Home Entry & Visual Hierarchy
- On the Home (`Sessions`) screen, directly beneath the right-aligned `Tutorials` launcher pill, display a **50/50 side-by-side CTA row** (`10dp` horizontal gap, `54dp` minimum height, `16dp` corner radius, 2-line centered wrapping for Burmese):
  1. **Left (`Quick Meditate` / `အမြန်တရားထိုင်မည်`):** Primary filled `Saffron` button (`PureWhite` text, `0dp` elevation). Opens `QuickMeditateSetup`.
  2. **Right (`New Session` / `တရားချိန် အသစ်`):** Flat outlined `MonasticMaroon` button (`PureWhite` fill, `1dp` `MonasticMaroon` border, `MonasticMaroon` text). Opens `SessionEditor` and retains the `SESSIONS_NEW_BUTTON` tutorial anchor.

### 12.2 Setup Screen & Reset Behavior
- `QuickMeditateSetup` mirrors the two-zone Session Editor layout **without a Session Name field**:
  - **Top bar (`MonasticMaroon`):** Back button, localized `Quick Meditate` title, and a right-aligned subtle **Reset** (`အကုန်ဖျက်မည်`) text button enabled only when at least one Intro track, Main track, timer, or custom input is set.
  - **Optional Active Notice:** If Quick Meditate is currently playing, display a flat `SaffronTintSurface` notice (`1dp` `Saffron` border) informing the user that starting again will replace the active session and begin fresh from `00:00`.
  - **Cards:** Optional untimed **Intro Queue**, required timed **Main Queue** (1+ tracks), and validated **Meditation Timer** (presets `10m/15m/30m/45m/60m`, `Full`, custom minutes `>= 1`, identical clamping rules to Section 4).
  - **Anchored Bottom CTA:** Full-width filled `Saffron` **Start Meditation** (`တရားစထိုင်မည်`) button enabled iff `mainItems.isNotEmpty() && targetDurationMs in 1..mainTotalDurationMs && !hasInvalidCustomInput`.
- **Reset isolation:** Tapping **Reset** clears all in-memory selections and persists an empty draft without interrupting any currently active playback session.

### 12.3 Draft Persistence & Deleted-Track Reconciliation
- Quick Meditate **never writes rows to the `sessions` or `session_tracks` database tables**.
- Last selections persist automatically on every change to local key-value preferences:
  - **Android:** `SharedPreferences` (`"dhamma_meditate_prefs"`).
  - **iOS:** `UserDefaults.standard`.
  - **Keys:**
    - `quick_meditate_intro_ids`: comma-separated ordered `String` of track IDs (duplicates preserved; empty string = empty list).
    - `quick_meditate_main_ids`: comma-separated ordered `String` of track IDs (duplicates preserved; empty string = empty list).
    - `quick_meditate_target_duration_ms`: `Int64` / `Long` (default `0`).
- **Reconciliation on open / live library observation:**
  1. Silently drop any track ID that no longer exists in the Library while preserving the exact order and duplicates of remaining tracks.
  2. If remaining `mainTotalDurationMs <= 0`, set `targetDurationMs = 0`.
  3. Else if `targetDurationMs <= 0`, default to `min(30 min, mainTotalDurationMs)`.
  4. Else if `targetDurationMs > mainTotalDurationMs`, clamp `targetDurationMs = mainTotalDurationMs` (marking `wasCapped = true` and showing the standard Main Queue cap notice).
  5. Persist the reconciled draft back to preferences if any track was dropped or timer was adjusted.

### 12.4 Ephemeral Playback, Full Parity & Localization
- **Reserved Ephemeral Session ID:** `"__quick_meditate__"` (never collides with UUID v4 saved session IDs).
- **Immediate Start Flow:** Tapping **Start Meditation** runs the standard Distraction Check (and one-time API 33+ notification prompt on Android), dispatches ephemeral two-zone playback (`ACTION_START_QUICK_MEDITATE` on Android), and navigates directly to `QuickMeditatePlayer` (reusing the Maroon `SessionPlayerScreen` UI).
- **Full Parity:** Intro-to-Main transitions, in-app **Skip to Main** during Intro, Pause/Continue, Stop & Reset, smooth 10s exponential fade-out, restricted lock-screen controls (Play/Pause + Stop & Reset only; no seek/skip), live track-title updates, active-track deletion stop guards, `ActiveSessionBanner` tap-to-return, and idle/completed **Edit** → `QuickMeditateSetup` all behave identically to saved sessions.
- **Dynamic Localization:** The Quick Meditate display title is always resolved from the localized `quick_meditate_title` resource (never hardcoded as a static string). Switching `English` ↔ `မြန်မာ` immediately updates `QuickMeditateSetup`, `QuickMeditatePlayer`, `ActiveSessionBanner`, and active playback notification/Now Playing metadata.
