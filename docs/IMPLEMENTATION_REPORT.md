# DraftLens — Sprint 0.1 Capture Hardening Report

**Branch:** `sprint-0-android-foundation` · **PR:** #1

## Physical-device status
- **Maintainer-reported smoke test: PASS for basic capture and live preview** (Sprint 0 device test, details not supplied).
- Device model, Android version, frame counts and latency measurements from that run were not provided and are not claimed.
- Expanded lifecycle matrix (`docs/TESTING_PHYSICAL_DEVICE.md`) remains mostly **NOT RUN** until executed.

## Verified fixes in this sprint
- AGP **8.6.1** for API 35 support; Gradle 8.7 and Kotlin 2.0.20 unchanged.
- Lint is strict (`abortOnError = true`, release checks enabled) and CI gates on it.
- `CaptureStartupExecutor`: promotion → projection → initialization; failure propagation tested.
- Foreground promotion occurs before `getMediaProjection()`; consent is fresh each session.
- One `VirtualDisplay` per `MediaProjection`: rotation keeps it and calls `setSurface()` + `resize()`.
- `AUTO_MIRROR` replaced `PUBLIC` flag.
- Capture dimensions prefer `maximumWindowMetrics` on API 30+, with the legacy path retained below it.
- FrameSampler uses monotonic elapsed time on Android (`SystemClock.elapsedRealtime()`), with an injectable JVM clock for tests.
- Memory ownership: no recycling while Compose can render a Bitmap; stale frames are unreferenced and GC-reclaimed.
- Frame timing wall-clock values are kept separate; UI reports frame-processing time only, not detection latency.
- Consent intent is created at Start time rather than composition time.

## Regression coverage
- Startup ordering, promotion failure and projection-acquisition failure.
- Sampler monotonic behavior, interval clamping/reset and pause/resume reset.
- Capture-state reset and start-blocking rules.
- Projection consent-result validation.
- Detection stubs return `NotImplemented`.
- Android instrumentation source checks service manifest type/permissions and compiles in CI.

## Limits
- No recognition, OCR, recommendations, overlay, API sync, or hero data.
- Sprint 0.1 keeps one full-size preview frame only; analysis crop/downscale is future Vision Foundation work.
- Self-capture: no `FLAG_SECURE`; user can choose “Entire screen” (includes debug UI) or “A single app” in the system scope dialog.

## Sprint 1 Vision Foundation recommendation
Start with rotation-aware preprocessing plus slot annotation, then a deterministic hero-ID lookup. Keep capture/Vision interfaces separate so ML evaluation can use the same sanitized frame input. Add the expanded physical-device matrix before store release.
