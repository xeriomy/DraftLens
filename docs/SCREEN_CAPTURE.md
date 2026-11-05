# Screen Capture — DraftLens Sprint 0

## Mechanism
`MediaProjectionManager.createScreenCaptureIntent()` → user consent → `getMediaProjection()` →
`createVirtualDisplay()` → `ImageReader` surface. All inside `CaptureSessionService`
(foreground, `mediaProjection` type, `FOREGROUND_SERVICE_MEDIA_PROJECTION` permission).

## Android version notes (compile/target SDK 35)
- **Android 14+**: service must call `startForeground` with the `mediaProjection` type **before**
  `getMediaProjection()`; the consent token is single-session and dies with the service.
  `MediaProjection.Callback.onStop()` (revocation) and `onCapturedContentResize()` (rotation)
  are both handled — resize recreates the VirtualDisplay without new consent.
- **Android 12+**: partial screen capture (share one app) is possible in the system dialog;
  DraftLens requests full-screen capture; behavior is identical when partial is chosen.
- **minSdk 26**: `ImageReader`, `VirtualDisplay`, and foreground-service APIs used are all
  available since API 21–26; no version-branched capture code is needed.

## Sampling
Default 500 ms (2 FPS), configurable 334–2000 ms via DataStore, hard-capped at ~3 FPS
(`FrameSampler`). `ImageReader(maxImages=3)` bounds backpressure; the listener drops (does not
queue) frames arriving faster than the sampler. Only the newest `Bitmap` is retained; the
previous one is recycled after publish. No frames touch disk.

## Lifecycle / teardown
`stopCapture()` and `onDestroy()` both release: VirtualDisplay → MediaProjection (unregister +
stop) → ImageReader/Surface → HandlerThread → foreground notification. Any exception in the
acquisition path is caught per-frame so one bad frame never kills the session; permission
failures surface as `ERROR` with a message, never a crash.

## Limitations (honest list)
- Frame→Bitmap conversion uses the RGBA_8888 plane path; padded-stride rows are cropped.
  If a device reports a non-RGBA ImageReader format the frame is dropped (counted, not faked).
- The ViewModel mirrors service state through explicit calls (Sprint 0 uses a single
  activity); a Binder/AIDL API is the documented upgrade if the service must survive longer.
- Emulator testing proves compile + logic, **not** MediaProjection behavior on real devices.
  Physical-device runbook: `CaptureLifecycleInstrumentedTest` class comment.
