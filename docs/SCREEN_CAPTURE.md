# Screen Capture — DraftLens Sprint 0.1.3 + 1B + 1C

## Mechanism
`MediaProjectionManager.createScreenCaptureIntent()` → user consent → `ServiceCompat.startForeground()`
with `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` → `getMediaProjection()` →
`createVirtualDisplay()` → `ImageReader` surface. Consent acquisition stays in Activity;
`CaptureSessionService` performs foreground promotion, projection creation, VirtualDisplay and all frame work.

## The floating freeze button (Sprint 1C)

A single circular 52dp button rendered via `WindowManager` + `TYPE_APPLICATION_OVERLAY`,
attached by the capture foreground service **only while a capture session is running** and
removed on every stop/terminate path. It is the primary in-game freeze control; the
notification retains its Stop action (and the Freeze/Resume actions) but is no longer the
primary workflow.

Gestures: **tap** toggles LIVE ↔ FROZEN (green ↔ orange, white glyph, 52dp, non-focusable,
content description set). **Long-press (400 ms) then drag** moves it; any movement before
the long-press fires cancels the gesture entirely. On release it **snaps to the nearest
horizontal edge** and persists its position as normalized coordinates in DataStore
(`floating_control_enabled`, `floating_position_x/y`). Rotation re-anchors it via the
display-metrics path; no re-consent, no re-attachment races.

`SYSTEM_ALERT_WINDOW` is requested only when the user taps **Enable** on the capture
screen (Android overlay-settings screen); the capture itself never needs it. If the
permission is missing or revoked: capture and in-app/notification Freeze/Resume are
unaffected, the toggle explains how to grant it, and no button is shown. The button calls
the exact same `CaptureFreeze` path as everything else — it never creates a second
VirtualDisplay or re-requests consent.

**Isolation caveat:** the overlay sets no flags to hide itself, and no capture region is
masked. Whether it appears inside single-app or full-display captures is a
physical-device finding per OEM — not verified in CI. See
`docs/sprints/SPRINT_1C_APP_ONLY_CAPTURE.md` and rows 33–43 of
`docs/TESTING_PHYSICAL_DEVICE.md`.

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
  are both handled. A resize signal dispatches to one authoritative path: the VirtualDisplay
  keeps running, but the ImageReader/Surface generation is replaced and re-attached;
  no second consent prompt is requested.
- **Orientation changes without the resize callback** (pre-API-34 devices, and any API level
  where the callback does not fire) are handled by a `DisplayManager` fallback: when the
  default display's metrics change, the capture size is re-derived from the current window
  metrics and the same resize path runs. Both paths are idempotent and deduped by
  `DisplayChangePolicy`.
- **Single-app capture (API 34+ partial capture)**: supported and preferred for the MLBB workflow —
  the system chooser offers it; DraftLens never forces it. Prefer **Single app / app window →
  Mobile Legends: Bang Bang**. The captured window's size is known through
  `onCapturedContentResize` (API 34+) and flows through the resize path; its on-screen
  visibility flows through `onCapturedContentVisibilityChanged` (see below). Full-display
  capture remains the fallback for older Android and behaves exactly as before.
- **onCapturedContentVisibilityChanged (API 34+) IS handled (Sprint 1C)**: while the
  selected window is hidden, images are acquired and closed but never converted or
  published; the last valid published game frame is preserved. When the window returns,
  publication resumes automatically unless a frame is pinned. Full-display captures never
  report hidden, so this path is inert there. Below API 34 the signal does not exist and
  the behavior degrades to the previous full-display semantics.
- **Capture dimensions:** API 30+ uses `WindowManager.maximumWindowMetrics`; lower APIs retain
  the legacy `defaultDisplay.getSize()` path.
- **Android 12+**: partial screen capture (share one app) is possible in the system dialog.
  For the MLBB workflow, **prefer Single app → Mobile Legends**; choose **Entire screen /
  full device display** only when that option is missing. Full-display capture may include
  the notification shade, Quick Settings, other apps and DraftLens itself — DraftLens
  cannot promise notification-free capture on that path. The app says this on screen too.
- **minSdk 26**: `ImageReader`, `VirtualDisplay`, and foreground-service APIs used are all
  available since API 21–26; no version-branched capture code is needed.

## Landscape capture (MLBB)

Full-screen captures mirror the display. If the device rotates from portrait to landscape
while a capture is running, the mirror's VirtualDisplay must change size with it. Three
inputs cover every supported API level, and all three funnel into one idempotent resize
path (`CaptureSessionService.recreateDisplay` → `FramePipeline.prepareResize`):

1. `MediaProjection.Callback.onCapturedContentResize(width, height)` — the authoritative
   content dimensions (API 34+). Both the buffer and the VirtualDisplay move to those dims.
2. `DisplayManager.DisplayListener.onDisplayChanged` — re-derives the size from the current
   window metrics; the standard path on pre-34 devices.
3. `Resources.configuration` changes — the resize path itself is configuration-independent.

While the capture size is stale (typically before the first callback after a rotation),
letterboxed or partial frames can be delivered. The conversion honors `image.cropRect`, so
such a frame shows the full game at its content size instead of garbage, until the resize
completes (typically the next frame). Nothing is ever stretched, padded with invented pixels,
or hidden by invented crops.

**Reader generations.** Every ImageReader/Surface generation is numbered. Images captured
with an older generation are dropped at the listener and again at publish time, so a late
frame from a released reader can never overwrite the replacement reader's frames. A freeze
snapshot taken during a resize pins the newest *valid* published frame; resuming continues
with the current generation. Stale frames never reach the pinned slot either.

### Diagnosing the landscape bug with logs

All capture diagnostics are structured `key=value` lines under the tags
`CaptureSessionService`, `FramePipeline` and `VisionPreprocessor`. To collect them:

```sh
adb logcat -c && adb logcat -v time -s CaptureSessionService FramePipeline VisionPreprocessor
```

Look for, in order:

1. `session=N capture-start: dimensions=WxH dpi=D rotation=R source=windowMetrics` — the
   size chosen at session start. If it is `1080x2400` while the game is landscape, this is
   the session-start case, and the resize must follow (see 3).
2. `session=N default-display changed` and/or `captured-content-resize: CxC -> WxH` — which
   signal fired and what dimensions it carried. If neither line appears on rotation, the
   platform delivered no signal; the capture stays at the session-start size forever.
3. `session=N surface-detach ... surface-attach ... virtual-display resized: WxH dpi=D generation=G`
   — the resize sequence completed; generation G is attached to the VirtualDisplay.
   `pipeline resize: detaching previous generation=G-1 ...` shows the old generation detaching first.
4. `generation=G plane: image=WxH crop=.. rowStride=R pixelStride=4 paddingBytes=P fullCrop=true`
   — one frame parsed straight out of the plane. Compare `rowStride` with `image width x 4`
   and `crop` with the game's content rect. A mismatch here means the black region originates
   in the acquired image (acquisition-side), not in the conversion.
5. `generation=G frame stored: source=WxH rotation=...` and later
   `preprocess(Bitmap): source=... output=WxH` — the frame left the capture pipeline and the
   preprocessing step; if (5) disagrees with (4), the black region is introduced in
   conversion or transformation, not acquisition.

Image contents are never logged, written to disk or uploaded.

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
- Frame→Bitmap conversion honors the RGBA_8888 plane layout, padded rows and `image.cropRect`;
  offset crops produce tightly packed images of the content region. A malformed plane drops
  the frame instead of producing a partial or shifted one.
- The ViewModel mirrors service state through explicit calls (Sprint 0 uses a single
  activity); a Binder/AIDL API is the documented upgrade if the service must survive longer.
- Emulator testing proves compile + logic, **not** MediaProjection behavior on real devices.
  Physical-device runbook: `CaptureLifecycleInstrumentedTest` class comment and rows 14–33
  of `docs/TESTING_PHYSICAL_DEVICE.md`.
