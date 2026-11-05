# Sprint 0 Implementation Report

**Result:** Sprint 0 implementation complete at PR #1 / head `6244637` (implementation commit `da25b04`).
GitHub Actions run [42031536957](https://github.com/xeriomy/DraftLens/actions/runs/42031536957) completed successfully.

## Implemented
- Native Kotlin + Compose project, single module `:app`; repository LICENSE remains MIT.
- MediaProjection consent flow with screen-capture intent.
- Foreground service type `mediaProjection`; foregrounding happens before `getMediaProjection()`.
- Persistent low-priority notification with explicit Stop action.
- Android 13+ `POST_NOTIFICATIONS` is optional; denial leaves capture available, with in-app status and Stop control.
- Frame sampler defaults to 2 FPS, clamps to 334–2000 ms / ≤3 FPS, interval persisted in DataStore.
- ImageReader bounded at 3 images; capture callback acquires latest image, drops when interval not elapsed or capture paused.
- Latest-frame Compose preview; capture status, resolution, frame count, timestamp, latency, errors, pause/resume.
- Rotation/resize callback recreates the VirtualDisplay without a new consent prompt.
- Teardown releases VirtualDisplay, MediaProjection, ImageReader/Surface, HandlerThread, notification.
- Detection interfaces return explicit `NotImplemented`; no fake recognition results.
- Room deferred until first persisted entity; empty database omitted.

## Verification actually executed
CI steps green: checkout, JDK setup, SDK install, unit tests, lint, debug APK build, artifact upload, manifest service check.

Artifact `draftlens-debug` downloaded locally from the run. `app-debug.apk` is 10,184,327 bytes; ZIP contains `AndroidManifest.xml` and `classes.dex`.

**Not verified:** MediaProjection behavior on a physical device, rotation on a real display, revoked consent during capture, 10-minute memory stability, OEM restrictions. The instrumentation plan in `app/src/androidTest/kotlin/...` is not run by CI and must be executed on a device.

## Tests executed by CI
`./gradlew test` passed. Unit coverage includes capture state transitions, sampler clamp/reset, state holder reset/start/pause/resume, projection result acceptance, detection stubs returning NotImplemented. `./gradlew lint` passed.

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
