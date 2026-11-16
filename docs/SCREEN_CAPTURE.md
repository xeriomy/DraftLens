# Screen capture

## Mechanism

`MediaProjectionManager.createScreenCaptureIntent()` → user consent → `ServiceCompat.startForeground()`
with `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` → `getMediaProjection()` →
`createVirtualDisplay()` → `ImageReader` surface. The Activity acquires consent;
`CaptureSessionService` owns foreground promotion, projection, display, frames and teardown.
The service starts before the projection is obtained, and the consent token is used once per
session.

## Capture modes

Prefer **Single app / app window → Mobile Legends** on Android 14+. DraftLens never forces the
choice. Whole-display capture remains the fallback and may include the notification shade,
Quick Settings, other apps and DraftLens itself; the app says this on screen.

When the selected window is hidden (API 34+ `onCapturedContentVisibilityChanged`), images are
still acquired and closed but never converted or published, so the last valid game frame is
kept instead of a screenshot of the shade. Below API 34 there is no visibility signal and
whole-display semantics apply.

## Freeze, pause and resume

- **Freeze** pins an independently owned snapshot (`CaptureFreeze` / `FrameSnapshots`). While
  frozen, the reader keeps acquiring and closing images but performs no conversion or
  publication, so a new frame can never overwrite the pinned one.
- **Pause** maps to `CaptureStatus.PAUSED`: publication stops, the latest valid frame is kept,
  and the projection and VirtualDisplay keep running.
- **Resume** releases the pin and clears the pause. No second projection or display is created.

The notification carries **Stop** and exactly one state action — Pause while live, Resume when
paused or frozen — as immutable `PendingIntent.getService`s to the running service. The
in-app controls and the overlay use the same `CaptureFreeze` and shared `CapturePublishState`
derivation, so the LIVE / PAUSED / FROZEN badge, the overlay glyph and the notification action
cannot disagree.

## Floating control

One 52 dp circular `TYPE_APPLICATION_OVERLAY` button, attached by the capture service only
while a session runs and removed on every teardown path. A detach invalidates any in-flight
attach, so teardown cannot orphan the window. Tap toggles publication state; long-press then
drag moves the button; on release it snaps to the nearest horizontal edge. Its normalized
position is stored in DataStore and re-anchored on rotation. The button's lifecycle is
independent of capture visibility.

`SYSTEM_ALERT_WINDOW` is requested only when the user enables the control from the capture
screen. Denial leaves capture, in-app controls and notification actions working. There is no
flag hiding the button from captures and no masked region: whether it appears inside
single-app or full-display capture is an OEM-specific physical-device finding.

## Resize and rotation

Three inputs funnel into one idempotent resize path
(`CaptureSessionService.recreateDisplay` → `FramePipeline.prepareResize`):

1. `onCapturedContentResize(width, height)` — authoritative content size, API 34+.
2. `DisplayManager.DisplayListener.onDisplayChanged` — re-derives size from current window
   metrics; the standard path below API 34.
3. Configuration changes — handled by the same path.

Every reader and surface generation is numbered, and stale callbacks are dropped at the
listener and again at publication, so a frame from a released reader cannot reach the UI or
the pinned snapshot. Plane conversion honours `image.cropRect` and padded rows, with pure
JVM-tested logic. While the size is stale, a letterboxed frame is shown complete rather than
garbage; nothing is stretched or padded with invented pixels.

A rotation that leaves the capture size unchanged updates rotation metadata only. API 30+
uses `WindowManager.maximumWindowMetrics`; lower APIs keep the legacy `defaultDisplay.getSize()`
fallback.

## Android notes

- **13+**: `POST_NOTIFICATIONS` may be denied. Capture still runs, but shade actions are
  unreachable; the capture screen explains this and in-app controls remain available.
- **14+**: `startForeground` with the mediaProjection type happens before `getMediaProjection()`.
  `onStop()` (consent revoked) is handled as session termination.
- **minSdk 26**: the ImageReader, VirtualDisplay and foreground-service APIs used are available.
- There is no `FLAG_SECURE`, no root access, no game-memory reading, no injected touches and no
  network upload of captured pixels.

Hardware behaviour of all of the above is tracked in
[TESTING_PHYSICAL_DEVICE.md](TESTING_PHYSICAL_DEVICE.md) and is largely still NOT RUN.
