# Roadmap

DraftLens is built in small vertical increments. Each increment ends with a green CI run and
an APK artifact; hardware behaviour is tracked separately in the physical-device checklist.
This file describes the current shape of the work, not a commit-by-commit history.

## Done

### Sprint 0 and 0.1 — capture foundation
Native Android app, single `:app` module, MediaProjection consent flow, typed foreground
service with a persistent notification, frame sampling, capture state machine, debug preview,
permission handling, strict lint and CI.

### Sprint 1A — vision foundation
Non-null frame contract with pixel payloads; rotation-aware geometry with round-trip tests;
validated preprocessing with honest failure codes; manual slot annotation with validation,
undo/redo and strict JSON codecs; versioned calibration profiles; Vision Lab tab. No
recognizer: slots are calibration data, never detections.

### Sprint 1B — hero recognition
Public static hero catalog from Rone Arena with an MLBBDex completeness cross-check, private
image cache with content validation and HTTPS-only redirects, atomic snapshot writes, upright
slot extraction for every rotation, and a dHash plus mean-RGB similarity baseline. Blank or
detail-free crops are rejected and low scores are reported as no match. The user always
confirms or corrects manually; results are labelled as uncalibrated similarity, not
probability. Also includes notification freeze/resume and the landscape capture fix with
reader generations and `cropRect`-aware plane copying.

### Sprint 1C — app-only capture and floating control
Captured-window visibility gates frame publication so the last valid game frame is kept when
MLBB is hidden. One 52 dp overlay button, attached only during capture and opt-in, toggles
LIVE and paused/frozen through the shared publish state. The notification offers Stop and
exactly one state action. The capture UI is reduced to permissions and status, and the
single-app first-frame ordering is fixed with explicit visibility and pending-resize states.

## Current focus

1. **Device verification.** CI proves compilation, pure logic and lint. It does not prove
   MediaProjection behaviour, overlay behaviour across OEMs, or recognition quality. The
   checklists and their honest NOT RUN status are in
   `docs/TESTING_PHYSICAL_DEVICE.md`.
2. **Calibration.** Measure top-1/top-3 and rejection behaviour on real MLBB screens, then
   replace the starting threshold and heuristic values in the recognition code.
3. **Instrumented tests.** Run them where KVM is available; the hosted runner compiles them
   only.

## Architecture constraints for future work

- Keep pure logic in JVM-testable packages: geometry, codecs, policy and matching have no
  Android imports at their core.
- Vision receives frames and freeze commands only through `VisionFrameSource`.
- Frames, slot crops and catalog references stay on-device; only public static catalog data
  and reference images are downloaded.
- A second Gradle module becomes justified when a feature needs an independent test or release
  cycle. Until then, logical packages are enough (see ADR-001).
