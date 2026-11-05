# Sprint 0 Implementation Report

**Status:** Critical MediaProjection startup fix is on PR #1. No real-device capture claim is made yet.
The GitHub Actions workflow is the build of record: it runs unit tests, lint, debug APK build,
uploads the artifact, compiles instrumentation tests, and inspects the debug APK manifest.

## Implemented
- Native Kotlin + Compose project, single module `:app`; repository LICENSE remains MIT.
- MediaProjection consent flow with screen-capture intent.
- `CaptureSessionService` promotes itself with `ServiceCompat.startForeground()` and
  `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` before `getMediaProjection()`.
- `CaptureStartupExecutor` keeps the mandatory order in one place: foreground promotion,
  projection acquisition, projection initialization.
- Persistent low-priority notification with explicit Stop action.
- Android 13+ `POST_NOTIFICATIONS` is optional; denial leaves capture available, with in-app
  status and Stop control.
- Frame sampler defaults to 2 FPS, clamps to 334–2000 ms / ≤3 FPS, interval persisted in DataStore.
- ImageReader bounded at 3 images; capture callback acquires latest image, drops when interval
  not elapsed or capture paused.
- Latest-frame Compose preview; capture status, resolution, frame count, timestamp, latency,
  errors, pause/resume.
- Rotation/resize keeps the existing VirtualDisplay and calls `setSurface()` then `resize()`—
  no second consent prompt and no extra VirtualDisplay.
- Teardown releases VirtualDisplay, MediaProjection, ImageReader/Surface, HandlerThread,
  notification.
- Detection interfaces return explicit `NotImplemented`; no fake recognition results.
- Room deferred until first persisted entity; empty database omitted.

## Verification required
CI checks the JVM suite, lint, APK assembly, instrumentation-test compilation, and the APK's
merged manifest service type/permissions. CI cannot grant screen-capture consent or prove
hardware MediaProjection behavior.

**Not verified here:** real-device capture, initial frames, rotation on hardware, consent
revocation during capture, 10-minute memory stability, OEM restrictions. Run the checklist in
`CaptureLifecycleInstrumentedTest` on a physical device before release.

## Tests
`./gradlew test` and `./gradlew lint` are CI gates. Unit coverage includes capture state
transitions, sampler clamp/reset, startup-order enforcement and failure propagation, state
holder reset, projection result acceptance, and detection stubs returning NotImplemented.
Instrumentation source is compile-checked; device-dependent checks remain for the hardware run.

No test can prove consent UI behavior or hardware screen capture from a JVM test.

## Known limitations
- No ML hero recognition, recommendations, counter/synergy scoring, overlays, API sync, player profiling.
- Data sources are documented only; Sprint 0 performs zero network calls.
- MLBBDex and Rone share ranked-stats lineage; definitions are not merged or reinterpreted.
- Consent is per session and cannot be replayed.
- OEM/store/policy questions in `docs/PRIVACY.md` remain unresolved; prototype is not shippable.

## Recommended next tasks
1. Physical-device matrix (Samsung/Google/Xiaomi; rotation, revocation, long session).
2. Data-source sync prototype with offline Room cache and attribution UI.
3. Manual draft-log screen and hero-slot crop dataset with separate opt-in.
4. Lightweight baseline detection research before adding models.
5. Privacy/store review for overlay and screenshot analysis.
