# Screen Capture — DraftLens Sprint 0.1.3 + 1B

## Mechanism
`MediaProjectionManager.createScreenCaptureIntent()` → user consent → `ServiceCompat.startForeground()`
with `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` → `getMediaProjection()` →
`createVirtualDisplay()` → `ImageReader` surface. Consent acquisition stays in Activity;
`CaptureSessionService` performs foreground promotion, projection creation, VirtualDisplay and all frame work.

## Freezing the game frame from the notification (Sprint 1B)
The ongoing capture notification carries an action (**Freeze current frame**) implemented as
an explicit `PendingIntent.getService` back to the running service — no Activity launch, no
consent re-request, no second projection or VirtualDisplay. The service snapshots the latest
validated frame into an independently owned copy (`FrameSnapshots`), keeps the projection
valid, and re-posts the notification as FROZEN with **Resume live capture**. Images arriving
while frozen are acquired and closed but not converted or published. The freeze can equally be
triggered from the Vision Lab button; both run `CaptureFreeze.freezeCurrentFrame()`. After an
Android-initiated stop the pinned frame survives in process memory for editing, but not
process death.

## Android version notes (compile/target SDK 35)
- Android 13+ `POST_NOTIFICATIONS` may be denied; capture still proceeds with a valid ongoing
  foreground notification that Android may hide. In-app status and Stop control remain
  available. The **Freeze current frame** notification action is not reachable while the
  notification is hidden, so the app checks the permission (and requests it on Android 13+)
  and says so on the capture screen; without it, freezing is done from the Vision Lab
  (or with the notification enabled first).
- **Android 14+**: service calls `startForeground` with the `mediaProjection` type **before**
  `getMediaProjection()`; the consent token is single-session and dies with the service.
  `MediaProjection.Callback.onStop()` (revocation) and `onCapturedContentResize()` (rotation)
  are both handled. Rotation keeps the existing VirtualDisplay and calls `setSurface()` then
  `resize()` with the replacement `ImageReader` surface—no second consent prompt.
- **Capture dimensions:** API 30+ uses `WindowManager.maximumWindowMetrics`; lower APIs retain
  the legacy `defaultDisplay.getSize()` path.
- **Android 12+**: partial screen capture (share one app) is possible in the system dialog.
  DraftLens requests full-screen capture; choosing app-window capture for the Mobile Legends
  workflow shows DraftLens itself, not the game — choose **Entire screen / full device
  display**. The app tells the user this in both capture screens.
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
Normal user Stop produces IDLE. External revocation, startup failure, resize failure, process
termination and task removal all use the same terminate path:

- clear the latest-frame reference
- reset session counters, timing, dimensions and density
- clear stale permission/session metadata
- preserve ERROR and its original message when applicable
- release VirtualDisplay → MediaProjection → ImageReader/Surface → HandlerThread → notification
- if no ERROR already exists, transition to IDLE

Stopping does **not** clear the pinned (frozen) frame: it survives the projection session in
process memory for editing, and only disappears with process death, an explicit Resume/Clear,
or a new capture session.

Any exception in the acquisition path is caught per frame so one bad frame never kills the session.

## Limitations (honest list)
- Frame→Bitmap conversion uses the RGBA_8888 plane path; padded-stride rows are cropped.
  If a device reports a non-RGBA ImageReader format the frame is dropped (counted, not faked).
- The ViewModel mirrors service state through explicit calls (Sprint 0 uses a single
  activity); a Binder/AIDL API is the documented upgrade if the service must survive longer.
- Emulator testing proves compile + logic, **not** MediaProjection behavior on real devices.
  Physical-device runbook: `CaptureLifecycleInstrumentedTest` class comment.
