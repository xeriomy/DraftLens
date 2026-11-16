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
| 14 | Full-device capture for the MLBB freeze workflow | When Android asks what to capture, **Entire screen / full device display** is chosen; live preview shows MLBB content | NOT RUN |
| 15 | Freeze from the notification while in MLBB | Tap **Freeze current frame** on the capture notification without leaving the game; the pinned frame survives returning to DraftLens; Vision Lab shows the MLBB frame, not the DraftLens UI | NOT RUN |
| 16 | Newer live frames cannot replace the frozen frame | While frozen, DraftLens keeps acquiring/closing images but publishes none; Vision Lab keeps showing the MLBB frame | NOT RUN |
| 17 | Resume from Vision Lab and from the notification | **Resume** (Vision Lab) or **Resume live capture** (notification) reveals live frames without a new projection or VirtualDisplay; notification action flips back to LIVE | NOT RUN |
| 18 | Freeze with no frame captured yet | **Freeze current frame** is a no-op (logged); nothing is pinned; no crash, no new session | NOT RUN |
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

## Evidence to capture each run
- `git rev-parse --short HEAD`
- Device model and Android version (`adb shell getprop ro.product.model`, `adb shell getprop ro.build.version.release`)
- DraftLens version/APK artifact run ID
- Logcat lines tagged `CaptureSessionService`, `FramePipeline`, `VisionPreprocessor`
  (`adb logcat -v time -s CaptureSessionService FramePipeline VisionPreprocessor`) for the
  capture dimensions, resize events, plane layout and frame-generation lines
- Initial resolution, frame count after 10 seconds, preview visible Y/N
- For failures: full exception and steps immediately before it

## CI scope
CI runs JVM unit tests, lint, APK assembly and merged-manifest inspection; instrumentation
tests compile but do not execute (no KVM on the runner — see `docs/VISION_TESTING.md`).
Neither substitutes for this checklist.
