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

- Implementation head `6e2d7b7` (docs-only commits after it change no build inputs):
  CI run 43782436092 — **888 unit tests, 0 failures / 0 errors / 0 skipped**; strict lint green;
  `assembleAndroidTest` green; `assembleDebug` green; APK `app-debug.apk`
  sha256 `3f390ac92458815e292897dfe937ae66bd9179946d533a33cf1a9386b864b551`.
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
