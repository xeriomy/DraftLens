# Sprint 1C — App-Only Capture & Circular Floating Control

**Branch:** `sprint-1c-app-only-capture` (stacked on `sprint-1b-hero-recognition`, PR #3 — **not merged**) · **PR:** #4 (base `sprint-1b-hero-recognition`)

Sprint 1C makes the MLBB freeze workflow playable from inside the game: the user
captures only the MLBB window, and a single circular overlay button freezes/resumes
the capture — no need to open the notification shade and no in-app step at all.
No recommendation UI of any kind is added.

**Dependency on Sprint 1B:** the freeze/snapshot machinery (`CaptureFreeze`,
`FrameSnapshots`), the Vision Lab pinned frame and the capture foreground service all
come from PR #3. Sprint 1C was built on top of it and cannot be reviewed meaningfully
until #3 lands. Do not merge this PR before #3 is merged and this PR is rebased onto
`main` if the maintainer prefers a clean stack.

## What shipped

| Piece | Where | What it does |
|---|---|---|
| Visibility gate (pure) | `capture/CaptureVisibilityPolicy.kt` | `shouldPublish(active, generationCurrent, frozen)` plus visibility transitions; JVM-tested |
| Pipeline gate | `FramePipeline.kt` | While the captured-content window is hidden, images are acquired and closed but never converted/published; the publish-time synchronized block double-checks |
| Visibility callback | `CaptureSessionService.projectionCallback.onCapturedContentVisibilityChanged` | API 34+; forwards to the pipeline; ignored unless CAPTURING; resize path unchanged |
| Overlay math (pure) | `overlay/OverlayPosition.kt` | Normalized (0..1) position on the free axis; pixel round-trip; edge snapping; clamping; JVM-tested |
| Overlay enablement (pure) | `overlay/OverlayEnablement.kt` | `shouldAttachOverlay(preference, permission, session)` gate; JVM-tested |
| Pending-attach invalidation | `OverlayController.kt` | generation counter: a detach() invalidates any in-flight show(), so a teardown during the DataStore read can never orphan the button |
| Overlay position persistence | `AppPreferencesRepository` | `floating_control_enabled`, `floating_position_x/y` in the existing preferences store |
| Floating button | `overlay/FloatingControlView.kt` | 52dp circle, LIVE green / FROZEN orange, white glyph, content description, system font |
| Overlay window | `overlay/OverlayController.kt` | `TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_FOCUSABLE`; tap = freeze/resume via the shared `CaptureFreeze` path; long-press (400 ms) drag; edge snap + persist on release; re-anchored on rotation via the display listener |
| Service wiring | `CaptureSessionService.kt` | Overlay attached only while capture is running (user enabled + permission granted), repainted on freeze/resume, removed on every teardown path; `ACTION_OVERLAY_UPDATE` re-syncs from the UI |
| Enable surface | Debug Capture screen | “Floating control” card: opt-in switch, permission explanation, open Android overlay settings, live LIVE/FROZEN label; capture works fully without it |
| Guidance | Debug Capture + Vision Lab screens | Two capture modes documented (single-app preferred; full-display fallback with honest limitation) |
| Manifest | `AndroidManifest.xml` | `SYSTEM_ALERT_WINDOW` declared; **requested only when the user enables the floating control** via `ACTION_MANAGE_OVERLAY_PERMISSION` |

## Single-app capture (Android 14+)

DraftLens never forces the mode. The system chooser decides what the user picks; the app
only guides:

1. Tap **Start Capture**.
2. Prefer **Single app / app window** and choose **Mobile Legends: Bang Bang**.
   The projection then carries only that window: live frames while MLBB is on screen,
   and the pinned last-valid frame whenever it is hidden.
3. If the single-app option is not offered (older Android), choose **Entire screen /
   full device display**. That fallback captures everything, including the notification
   shade, system UI and other apps. **DraftLens cannot promise notification-free
   capture on the full-display path.** The freeze workflow still works.

Below API 34 the visibility callback does not exist, so a hidden app-only window is
undetectable — frames keep flowing exactly as before (full-display behavior).

## Visibility lifecycle

`onCapturedContentVisibilityChanged(isVisible)` (API 34+) is wired into the existing
CAPTURING-state guard alongside `onCapturedContentResize`:

- **Hidden:** the pipeline acquires and closes images but performs no conversion and
  publishes nothing. `latestFrame` (and, if frozen, the pinned snapshot) are untouched.
  The shade/DraftLens/other apps can never replace the last valid game frame.
- **Visible again:** publication resumes automatically — unless a frame is pinned
  (user intent LIVE vs FROZEN wins). A resume requested while hidden flips the intent
  to LIVE and takes effect exactly when visibility returns; nothing black is ever shown
  in between.
- **Freeze while hidden:** the pin stores a copy of the last valid frame; still no
  publication; visibility callbacks change nothing until a resume.
- **Stop:** the overlay is detached first, then the normal termination path runs; the
  pinned frame survives in process memory as documented (Sprint 1B contract).
- **Resize during freeze:** unchanged — the generation-switch resize path works while
  frozen; freeze/resume never recreate the projection or the VirtualDisplay and never
  create a second VirtualDisplay.

## Overlay and capture isolation (critical, not settled by code)

**Whether the DraftLens overlay appears inside a MediaProjection capture is a physical-device
question and is NOT verified in this sprint.** The overlay does not set any flag to hide
itself from capture, and no capture region is masked. Expectations to check on device
(rows 34/35 of `docs/TESTING_PHYSICAL_DEVICE.md`):

- Single-app MLBB capture **should** carry only the MLBB window; on many devices the
  overlay (a separate window belonging to DraftLens) will not be included.
- Full-display fallback capture will very likely include the overlay. If that happens,
  the control sits in the top/bottom corner, snaps away from the central draft region,
  and must not be read by the hero-slot cropping (annotation rects are user-placed on
  the last valid frame, and the overlay is small — but this is not automatically safe).
- If the overlay contaminates hero slot crops on the fallback path, the documented
  remedy is: use single-app capture, or disable the floating control and freeze from
  the notification/in-app. **Do not** crop arbitrary regions to hide it.

No claim is made that app-only capture excludes every overlay window; only on-device runs
can establish it per device/OEM.

## Verification

CI (GitHub Actions, ubuntu-latest) is the build of record — the authoring host has no
Android toolchain.

- Implementation head `f992d45` (docs-only commits after it change no build inputs):
  CI run 43784157138 — **886 unit tests, 0 failures / 0 errors / 0 skipped**; strict lint green;
  `assembleAndroidTest` green; `assembleDebug` green; APK `app-debug.apk`
  sha256 `1d1f50629e145d48e64ed56d0cf74228e7d48d4c6f94fde81eb6c22fc4795810`.
- Instrumentation tests: **compiled via `assembleAndroidTest`; not executed** (no KVM on the runner).
- Automated coverage gaps, stated honestly: **LIVE→FROZEN pinning a real frame** (an
  ImageReader surface cannot be filled from a test Canvas, and the pin path is exercised
  on device — rows 36/37) and **resize during freeze** (the gate is dimension-agnostic by
  construction; policy truth-table pins it — on-device rows 25/30 pin the pipeline path).
- Physical-device rows 33–43: **NOT RUN** — see `docs/TESTING_PHYSICAL_DEVICE.md`

## Explicitly not built (scope restrictions)

No draft-phase detection, no slot detection, no hero models, no counter/synergy scoring,
no recommendation ranking or overlays, no game memory access, no automated interaction
with MLBB, no accessibility service, no panel/menu/toolbar from the overlay button.

## Follow-up fixes (notification Pause, permissions-only UI, overlay independence)

### Root cause analysis (overlay "freezing" during app-only capture)

Traced end to end: `MediaProjection.Callback` → `onCapturedContentVisibilityChanged` →
`CaptureVisibilityPolicy` → `FramePipeline` → `CaptureFreeze` → overlay/notification state.

What the trace showed:

1. **The overlay was never lifecycle-coupled to capture visibility** — `FloatingControlView`
   renders and handles touches with no frame input; `onCapturedContentVisibilityChanged`
   gates only the `FramePipeline` (by design after the 1C review fixes). Window attachment
   is independent of the ImageReader generation and of the sampler.
2. **Three real defects made the button misbehave though:**
   - **Silent no-op tap**: tapping freeze before any frame had ever been published returned
     `NoFrame` and produced no feedback (common in app-only mode, where nothing publishes
     until MLBB is first on screen). The tap now surfaces a short toast ("No frame captured
     yet"), and the button is no longer perceived as dead.
   - **Glyph/notification could disagree with reality**: the overlay glyph and the
     notification only followed `frozenFrame`. Any publication block that was not a pin
     (e.g. notification Pause) left both surfaces showing LIVE/green while publication was
     blocked. Both now derive from one shared `CapturePublishState` (LIVE/PAUSED/FROZEN,
     pin wins over pause for presentation) — see `CapturePublishState.kt` — collected from a
     combined `frozenFrame` + session-state flow.
   - **Visual workaround risk ruled out**: nothing in the fix hides the button, masks
     regions, or falls back to full-display capture.
3. **Unverifiable from CI**: whether a specific OEM/immersive game covers or excludes the
   overlay window on the physical screen is a hardware finding — rows 44–53 of
   `docs/TESTING_PHYSICAL_DEVICE.md` (all NOT RUN). No OEM workaround is shipped; if an
   OEM covers overlays during immersive games, that is a documented limitation, not a code
   defect, and the notification Stop/Pause actions remain available.

### Notification: Freeze → Pause

The ongoing notification now carries **Stop** and exactly one of **Pause** (while LIVE) /
**Resume** (while PAUSED or FROZEN) — never both, never a Freeze action. Pause maps onto the
existing shared `CaptureStatus.PAUSED` gate (`setPaused(true)` stops conversion/publication,
keeps the projection, VirtualDisplay and latest valid frame). Resume is a unified handler:
`CaptureFreeze.resumeLive()` (clear any pin) + `setPaused(false)` (clear any pause). The
in-app and overlay Freeze/Resume (pinning a snapshot) are unchanged.

### Permissions-only UI

Removed: the two-mode capture tutorial card, the floating-control tutorial card, the
in-game freeze help text, and the stale "Detection: not implemented in Sprint 0" line.
Kept, compacted in one "Permissions" card: the notification permission flow (status
Allowed/Not allowed + grant action), and the floating-overlay permission flow (status,
switch, open Android overlay settings, pending-enable on return from settings). The
in-app capture controls (Start/Stop/Pause), status readout, sampling interval and live
preview are unchanged.

### Verification

- Implementation head `130ce6c`: CI run 43922482183 — **904 unit tests, 0 failures / 0 errors / 0 skipped**;
  lint + `assembleAndroidTest` + `assembleDebug` green; APK sha256 `48b510673919fe2caa9c906747d9dc766eb27bb170c589f67b58c9a175dd7b72`.
- Physical device: rows 44–53 remain **NOT RUN**.

## Follow-up: single-app first-frame bug

Root cause: Sprint 1C registered MediaProjection callbacks before the pipeline,
VirtualDisplay and session state were ready. Early visibility `false` and early
captured-content resize were lost/overwritten or treated as fatal; the boolean gate could
block the first frame forever. Diagnostics were added first (`CaptureDiagnostics`, callback
seq/readiness logs, per-stage drop reasons, no pixels); then the fix: service-owned explicit
UNKNOWN/VISIBLE/HIDDEN policy (UNKNOWN intentionally open, first frame promotes to VISIBLE),
service-owned `PendingResize` applied to both VirtualDisplay and ImageReader at startup,
no pipeline-side visibility reset, and holder delivery of frames published during STARTING.
See `docs/IMPLEMENTATION_REPORT.md` and the focused A–E device test in
`docs/TESTING_PHYSICAL_DEVICE.md` (**NOT RUN**).
