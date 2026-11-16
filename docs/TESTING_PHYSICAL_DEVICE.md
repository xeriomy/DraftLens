# Physical-device lifecycle checklist — DraftLens

Status values: **PASS**, **FAIL**, **PARTIAL**, **NOT RUN**. Record the commit SHA, APK artifact run ID, device model and Android version for each execution. Do not mark a row PASS without evidence from that run.

> Maintainer-reported baseline: Sprint 0 capture and preview were smoke-tested on a physical Android device and confirmed working. Device details, versions, frame counts and latency measurements from that test were not provided, so they are not recorded here. First capture is **PARTIAL** on that evidence; all expanded lifecycle rows below remain **NOT RUN**.

> Maintainer-reported (Vision Lab, 1A.2 retained state): the Vision Lab interaction behavior in the currently shipped build was smoke-tested on a physical Android device and confirmed working. Device model, Android version, install source/APK artifact, test date, logs and recordings were **not** supplied, so they are not recorded here and no measurements are claimed. Tested and confirmed working:
>
> - Slot selection, movement and resizing.
> - Long-press property menu.
> - Long-press-and-drag slot creation (creation selects the new slot; the property editor does not open automatically).
> - The original compact red X deletion control at the top-right corner.
> - Copy, paste and duplicate.
> - Fullscreen entry/exit and Android Back.
> - Annotation gesture isolation from page scrolling.
> - Existing portrait/landscape rendering behavior.
>
> This does **not** cover the MediaProjection lifecycle matrix. Repeated start/stop cycles, projection revocation, app-process interruption, rotation/resize handling, background stability, sustained capture and the remaining lifecycle rows below remain **NOT RUN**.

| # | Scenario | Expected behavior | Status |
|---:|---|---|---|
| 1 | First capture from a cold launch | Consent appears, foreground service starts, preview shows frames | PARTIAL (maintainer-reported basic smoke: capture + live preview confirmed working; device/version/timings not supplied) |
| 2 | Stop, then fresh second session | New consent dialog; new MediaProjection; no stale frame count | NOT RUN |
| 3 | 10 start/stop cycles | No duplicate notification, no parallel session, no repeated service error | NOT RUN |
| 4 | Stop via Android media-projection system controls | Callback cleanup; IDLE state; service stopped | NOT RUN |
| 5 | Consent revoked during capture | ERROR state shown; resources released; no crash | NOT RUN |
| 6 | App process interruption | Service exits cleanly; no notification leak | NOT RUN |
| 7 | Screen rotation | Existing VirtualDisplay receives same MediaProjection; preview continues; no new consent | NOT RUN |
| 8 | Resolution change | Preview tracks new bounds; no display recreation | NOT RUN |
| 9 | 10-minute continuous capture | No crash/OOM; preview updates; frame interval remains clamped | NOT RUN |
| 10 | Background/background-service stability | Capture survives backgrounding within Android limits | NOT RUN |
| 11 | Sustained capture memory/latency | Memory bounded; frame processing latency reported separately from detection latency | NOT RUN |
| 12 | DraftLens foreground vs MLBB foreground | Selected capture target is clear; preview corresponds to chosen scope | NOT RUN |
| 13 | Screenshots in app | System screenshot path works normally | NOT RUN |
| 14 | Full-device capture for the MLBB freeze workflow | When Android asks what to capture, **Entire screen / full device display** is chosen (fallback mode; preferred is Single app → MLBB — see rows 33–43); live preview shows MLBB content | NOT RUN |
| 15 | Pause from the notification while in MLBB | Tap **Pause** on the capture notification without leaving the game; the last valid frame is kept, publication stops, projection persists; the notification flips to Resume | NOT RUN |
| 16 | Newer live frames cannot replace the frozen frame | While frozen, DraftLens keeps acquiring/closing images but publishes none; Vision Lab keeps showing the MLBB frame | NOT RUN |
| 17 | Resume from Vision Lab and from the notification | **Resume** (Vision Lab) or **Resume live capture** (notification) reveals live frames without a new projection or VirtualDisplay; notification action flips back to LIVE | NOT RUN |
| 18 | Freeze with no frame captured yet (in-app / floating button) | **Freeze** is a no-op (logged); nothing is pinned; the tap surfaces a “No frame captured yet” toast; no crash, no new session | NOT RUN |
| 19 | Duplicate taps of Freeze / Resume | Idempotent: the first pinned frame is kept; notification stays FROZEN | NOT RUN |
| 20 | Notifications disabled | Freeze action unreachable from the shade; the app warns and offers to enable them; in-app Freeze/Resume still work | NOT RUN |
| 21 | Stop with a frame frozen | Projection and resources are released; the frozen frame remains available in Vision Lab until Resume/Clear or a new capture session; it does not survive process death | NOT RUN |
| 22 | Freeze during low frame availability | No frame → the action logs and keeps state; first valid frame freezes on the next tap | NOT RUN |
| 23 | Landscape game capture end-to-end | MLBB in landscape: frame shows the *entire* game, no black region; verify both session-start-in-landscape and portrait→landscape rotation; also landscape→portrait→landscape twice | NOT RUN |
| 24 | Portrait app capture after landscape game | Switch back to a portrait app; frame again shows the whole portrait app without black regions | NOT RUN |
| 25 | Resize with freeze active | Freeze from the notification while in MLBB; rotate device; frozen frame still shows the pinned game frame; no stale-frame overwrite; Resume continues the *current* generation | NOT RUN |
| 26 | cropRect/letterbox handling | Session starts in portrait, game opens landscape before any resize lands: frame is letterboxed-but-complete (no garbage), replaced by the full-size frame after the resize | NOT RUN |
| 27 | Stride-heavy device | On a device/GPU whose ImageReader reports `rowStride > width * 4`: landscape frame is pixel-shifted-free, no skewed rows; compare a full-screen region against a hardware screenshot | NOT RUN |
| 28 | Resize storm | Rotate device repeatedly portrait↔landscape 6+ times: one generation settles per change; no crash, no stale-dimension frame, notification still shows LIVE | NOT RUN |
| 29 | Resize while capture is paused | Pause during landscape→portrait; the resize still applies and is logged; resume shows the resized frame | NOT RUN |
| 30 | Freeze after resize | After a portrait→landscape resize, tap Freeze: pinned frame is the landscape frame at the new dimensions, complete | NOT RUN |
| 31 | Mismatch diagnosis | Intentionally start capture with the game in landscape, then force-stop rotation events via lock/unlock: collect `CaptureSessionService`/`FramePipeline` logcat and verify dimensions, cropRect, rowStride and generation lines; confirm no frame mixes generations | NOT RUN |
| 32 | Single-app share resize (API 34+) | Share only the MLBB window: capture resizes to the window's content size via `onCapturedContentResize`; the app logs the resize; releasing the app restores the full share | NOT RUN |
| 33 | Floating control appears while capturing | Enable it on the capture screen (grant the overlay permission when prompted); start capture, switch to MLBB; the circular button is visible, green, ~52dp | NOT RUN |
| 34 | Overlay absent from single-app capture | With single-app MLBB capture, confirm the button is **not** visible in the captured frame (logcat: `OverlayController` + Vision Lab pinned frame) | NOT RUN |
| 35 | Overlay present in full-display fallback | Repeat the capture-frame check in full-display mode; document whether the button appears there; confirm it snaps to an edge and away from the draft area | NOT RUN |
| 36 | Tap freeze inside MLBB | In game, tap the circle: glyph becomes the pause glyph on orange; Vision Lab shows the pinned frame, not DraftLens | NOT RUN |
| 37 | Tap resume inside MLBB | Tap the circle again: live capture resumes without a new consent dialog or VirtualDisplay; glyph returns to LIVE green | NOT RUN |
| 38 | Drag, long-press and edge snap | Long-press (~0.4 s) then drag the button to the other edge; on release it snaps and stays there after rotation | NOT RUN |
| 39 | Rotation with overlay shown | Rotate portrait↔landscape with the button shown: it stays inside the screen in the same relative position; draft regions remain usable | NOT RUN |
| 40 | Overlay permission denied | Decline the overlay permission: capture still starts, in-app Freeze/Resume still work, the capture screen shows how to grant it later; no button is shown | NOT RUN |
| 41 | Stop while frozen with overlay shown | Tap Stop with a frame pinned and the button visible: button disappears, projection released, the pinned frame survives in Vision Lab | NOT RUN |
| 42 | Notification shade never replaces the pinned frame | In game with single-app MLBB capture, open the notification shade repeatedly: the pinned frame and live preview never show the shade | NOT RUN |
| 43 | App hidden resume-intent | While frozen, return to DraftLens with MLBB in the background; tap Resume in DraftLens; return to MLBB: live capture is resumed (the hidden-period intent is remembered) | NOT RUN |
| 44 | Overlay stays interactive over MLBB in app-only capture | Select Single app → MLBB; button stays visible above MLBB; taps toggle LIVE/FROZEN; long-press drag works; visibility changes (DraftLens ↔ MLBB, shade open/closed) never detach or stale it | NOT RUN |
| 45 | Overlay tap before the first frame in app-only mode | Fresh session with the game not yet rendered: tap shows “No frame captured yet” toast; button stays attached and responsive | NOT RUN |
| 46 | Overlay ↔ notification state sync | Freeze via overlay in MLBB: notification shows Resume (no Pause); pause via notification: overlay turns orange; both surface the same LIVE/PAUSED/FROZEN state | NOT RUN |
| 47 | Notification actions while LIVE | Actions are exactly Stop + Pause; no “Freeze current frame” wording remains anywhere in the notification | NOT RUN |
| 48 | Notification Pause keeps the frame | Tap Pause in MLBB: latest game frame stays shown everywhere; no new frames replace it; projection and VirtualDisplay persist; notification flips to Resume | NOT RUN |
| 49 | Notification Resume recovers | Tap Resume: live frames resume without a new consent dialog or VirtualDisplay; notification flips back to Stop + Pause | NOT RUN |
| 50 | Notification Resume while pinned in-app | Freeze via the in-app control, then Resume from the notification: pin clears and live publication resumes (gate opens unless MLBB is hidden) | NOT RUN |
| 51 | Stop from the notification | Stop is always present; removes the overlay, releases projection/VirtualDisplay/notification; Vision Lab retains the pinned frame | NOT RUN |
| 52 | Pause then process restart | Pause during MLBB; kill the app from recents; reopen: capture is gone (process death), no stale PAUSED state carried into the next session | NOT RUN |
| 53 | Single-app capture of another app | Repeat rows 44–51 with a second single-app target to confirm no MLBB-specific assumptions | NOT RUN |

## Sprint 1C capture-isolation finding (maintainer to fill in)

Whether the overlay appears in single-app and full-display captures is an on-device finding.
Record the outcome of rows 34/35 here (device model, Android version, OEM skin) and whether
the button contaminated hero-slot crops taken on the fallback path. Do not mark a row PASS
without evidence from that run.

## Focused first-frame test: Single-app MLBB (Sprint 1C follow-up)

**Status: NOT RUN.** This is the decisive test for the reported "No frame captured yet"
bug. Run each part on a physical device and record the log lines below with device model,
Android version, OEM skin, APK run ID and commit SHA. Do not mark PASS without evidence.

Collect bounded diagnostics (first 4 + every 20 per event; no pixels are logged):

```sh
adb logcat -c && adb logcat -v time -s CaptureSessionService FramePipeline
```

Key lines and what they answer:
- `visibility-callback seq=N visible=... pipelineInitialized=... status=...` — was a
  callback received, with what value, before or after pipeline init, and in what order.
- `resize-callback seq=N WxH status=... virtualDisplayReady=...` and
  `capture-start ... source=pending-captured-content-resize` — was the selected app's
  early resize retained and applied to both reader and VirtualDisplay?
- `acquire: ... acquired=N image=WxH isActive=... generationCurrent=... frozen=...
  contentVisible=...` — does ImageReader receive images at all (no lines = platform did
  not deliver; lines present = our code rejected them).
- `drop gate=<reason>` (`isActive` / `staleGeneration` / `frozen` / `contentHidden`),
  `drop sampling`, `drop conversion-failed`, `converted ... bitmap=WxH`,
  `published to _latestFrame`, `recordFrame delivered` — where the frames stopped.
- `freeze: NoFrame ... visibility=...` — the state at the moment the button reported it.

A. Full-display baseline
- [ ] Start capture with **Entire screen**; confirm `acquire:` and `published to
      _latestFrame` lines, preview updates, and Freeze pins the current frame. (NOT RUN)

B. Single-app MLBB
- [ ] Fresh session → **Single app** → **Mobile Legends**.
- [ ] Wait until MLBB is visibly in the foreground; record whether
      `onCapturedContentVisibilityChanged(true)` arrived (and its seq/order).
- [ ] Confirm ImageReader receives images (`acquire:` lines) and at least one frame is
      converted and published (`published to _latestFrame`, `recordFrame delivered`).
- [ ] Tap the floating Freeze button; confirm the game frame is pinned. (NOT RUN)

C. Visibility transitions
- [ ] Switch MLBB → DraftLens: no hidden-window frame replaces the last valid game frame.
- [ ] Return to MLBB: publication resumes; Freeze/Resume work again. (NOT RUN)

D. Isolate overlay effects
- [ ] Repeat B with the floating overlay **disabled**. The first-frame behavior must be
      identical; if it is not, the overlay window — not the capture gate — is implicated.
      (NOT RUN)

E. Resize
- [ ] Record the selected app's reported dimensions from `resize-callback` and confirm
      ImageReader/VirtualDisplay match (`virtual-display resized` / `bitmap=WxH`).
- [ ] Repeat with MLBB in landscape. (NOT RUN)

If no `acquire:` lines appear at all in B, the platform did not deliver images to
ImageReader — report that finding with the logcat rather than changing the policy again.

## Evidence to capture each run
- `git rev-parse --short HEAD`
- Device model and Android version (`adb shell getprop ro.product.model`, `adb shell getprop ro.build.version.release`)
- DraftLens version/APK artifact run ID
- Logcat lines tagged `CaptureSessionService`, `FramePipeline`, `VisionPreprocessor`, `OverlayController`
  (`adb logcat -v time -s CaptureSessionService FramePipeline VisionPreprocessor OverlayController`) for the
  capture dimensions, resize events, plane layout and frame-generation lines
- Initial resolution, frame count after 10 seconds, preview visible Y/N
- For failures: full exception and steps immediately before it

## CI scope
CI runs JVM unit tests, lint, APK assembly and merged-manifest inspection; instrumentation
tests compile but do not execute (no KVM on the runner — see `docs/VISION_TESTING.md`).
Neither substitutes for this checklist.
