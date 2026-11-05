# Screen Capture — DraftLens Sprint 0

## Mechanism
`MediaProjectionManager.createScreenCaptureIntent()` → user consent → `ServiceCompat.startForeground()`
with `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` → `getMediaProjection()` →
`createVirtualDisplay()` → `ImageReader` surface. Consent acquisition stays in Activity;
`CaptureSessionService` performs foreground promotion, projection creation, VirtualDisplay and all frame work.

## Android version notes (compile/target SDK 35)
- Android 13+ `POST_NOTIFICATIONS` may be denied; capture still proceeds with a valid ongoing
  foreground notification that Android may hide. In-app status and Stop control remain available.
- **Android 14+**: service calls `startForeground` with the `mediaProjection` type **before**
  `getMediaProjection()`; the consent token is single-session and dies with the service.
  `MediaProjection.Callback.onStop()` (revocation) and `onCapturedContentResize()` (rotation)
  are both handled. Rotation keeps the existing VirtualDisplay and calls `setSurface()` then
  `resize()` with the replacement `ImageReader` surface—no second consent prompt.
- **Capture dimensions:** API 30+ uses `WindowManager.maximumWindowMetrics`; lower APIs retain
  the legacy `defaultDisplay.getSize()` path.
- **Android 12+**: partial screen capture (share one app) is possible in the system dialog;
  DraftLens requests full-screen capture; behavior is identical when partial is chosen.
- **minSdk 26**: `ImageReader`, `VirtualDisplay`, and foreground-service APIs used are all
  available since API 21–26; no version-branched capture code is needed.

## Sampling
Default 500 ms (2 FPS), configurable 334–2000 ms via DataStore, hard-capped at ~3 FPS
(`FrameSampler`). `ImageReader(maxImages=3)` bounds backpressure; the listener drops (does not
queue) frames arriving faster than the sampler. The app publishes and retains the latest frame.
When a newer frame replaces it, the older frame becomes eligible for garbage collection if no
other component references it. DraftLens does not manually recycle a Bitmap that Compose may
still be rendering. **Stopping clears references; it does not guarantee immediate native-memory reclamation.** This is not a strict one-Bitmap memory bound. No frames touch disk.

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
