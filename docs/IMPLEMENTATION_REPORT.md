# DraftLens — Sprint 0.1.3 Capture Lifecycle Report

**Branch:** `sprint-0-android-foundation` · **PR:** #1

## Physical-device status
- **Baseline maintainer-reported smoke (Sprint 0): PARTIAL.** Capture and live preview were reported working on a physical device. Device model, Android version, frame counts and measurements were not supplied, so full PASS is not claimed.
- Expanded physical-device lifecycle matrix in `docs/TESTING_PHYSICAL_DEVICE.md` remains **NOT RUN** except the one PARTIAL row. Expanded scenarios not claimed executed.

## Verified fixes (Sprint 0.1 set)
- AGP **8.6.1**, Gradle 8.7, Kotlin 2.0.20, compile/target SDK 35, minSdk 26
- Strict lint enforced and used as CI gate
- Foreground service promoted synchronously with `mediaProjection` type before projection acquisition
- `START_NOT_STICKY`; null `Intent` is ignored; fresh consent per session
- `CaptureStartupExecutor` ordering: promotion → projection → initialization
- One `VirtualDisplay` per session; rotation uses `setSurface()` + `resize()`
- Capture dimensions prefer `WindowManager.maximumWindowMetrics` on API 30+, fallback below
- `VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR`
- Monotonic sampling; wall-clock retained separately; UI reports frame-processing latency only
- Latest frame is published by `CaptureStateHolder`; Compose bitmap is not recycled while rendering
- Termination paths are unified through `terminateSession()`:
  - normal Stop → IDLE
  - projection revocation/startup failure → ERROR, with frame cleared and metrics reset
  - task removal/process end uses the same state cleanup
  - permission and dimensions are cleared on termination, ERROR is preserved
- Consent intent is requested fresh at Start time

## Tests covered by CI
- Startup order and failure propagation
- Target sampled time versus wall clock: monotonic reset, pause/resume semantics
- Clear/retained-frame removal tests using shared `terminateSession()`
- Session state reset and start-blocking rules
- Projection consent-result validation
- Detection stubs explicitly `NotImplemented`

## CI (source of truth)
Local Gradle assembly is unavailable on this host because Android SDK build tools are x86_64 and host is aarch64. GitHub Actions is the build of record for `test`, `lint`, `assembleAndroidTest`, `assembleDebug`, APK artifact upload and merged-manifest inspection.

Note: invocation `./gradlew assembleDebug` is intentionally not claimed locally; CI output is the deliverable.

## Open limitations
- No recognition, OCR, recommendations, overlays, API sync, or hero data
- Analysis frame pipeline not added yet; preview is one full-size frame
- Self-capture: no `FLAG_SECURE` by maintainer preference; user can choose “Entire screen” or “A single app”
- Native-memory reclamation after reference clearing is not guaranteed and is not a strict one-Bitmap bound
- OEM/background/store policies and review gates remain for release
- Physical-device matrix needs execution before store release

## Next: Sprint 1 Vision Foundation
1. Rotation-aware preprocessing and slot capture analysis
2. Deterministic hero-ID lookup
3. Minimal frame logging only through explicit opt-in
4. Execute physical-device matrix before any store submission
