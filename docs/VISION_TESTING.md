# Vision Testing

## Where the tests run

GitHub Actions is the build of record for this sprint.

The authoring host is **aarch64**, and AGP 8.6.1 publishes only an **x86_64** `aapt2` — the
binary that compiles and links resources. No Gradle task that touches resources can run
there; the failure is `Cannot run program ".../aapt2": error=2`. Installing an x86_64 SDK,
emulating it, or writing a second build system would each cost more than they are worth, so
every verification below ran in CI on `ubuntu-latest` (x86_64) with JDK 17, the same
`./gradlew` tasks a developer would run locally.

Workflow: `.github/workflows/build.yml`, triggered by `pull_request`.

```sh
./gradlew test --stacktrace              # JVM unit tests
./gradlew assembleAndroidTest --stacktrace   # instrumentation sources compile
./gradlew lint --stacktrace              # strict lint, abortOnError = true
./gradlew assembleDebug --stacktrace     # debug APK
```

The run also prints the unit-test totals and uploads the APK as the `draftlens-debug`
artifact, then re-reads the packaged manifest to confirm the foreground-service permissions
and the `mediaProjection` service type survived the build.

Latest green run for the code state: <https://github.com/xeriomy/DraftLens/actions/runs/42918290133>
(commit `691b9d2`) — 167 unit tests, 0 failures, 0 errors, 0 skipped, executed in both the
debug and the release unit-test variants (354 test executions in total).

## Test inventory

| Suite | File | Covers |
|---|---|---|
| Vision model | `vision/model/VisionFrameTest.kt` | payload copying, byte estimates, empty/mismatched input, orientation categories, invalid rectangles |
| Geometry | `vision/geometry/GeometryTest.kt` | all four rotations, portrait/landscape/square, source↔normalized↔source, source↔processed↔source, crop-aware and downscaled mappings, invalid dimensions, NaN/infinity |
| Preprocessing math | `preprocessing/PreprocessingMathTest.kt` | validation, aspect-preserving output sizing, long-edge and pixel budgets, no upscale |
| Preprocessing render | `preprocessing/PixelArrayFramePreprocessorTest.kt` | rotation, crop and downscale of synthetic frames, identity path, failure reporting |
| Matrix | `preprocessing/SourceToProcessedMatrixTest.kt` | the Android matrix matches the pure-Kotlin elements |
| Annotation store | `annotation/SlotAnnotationStoreTest.kt` | add/move/resize/update/delete/select, stable ids, duplicate `(type, team, index)`, overlap severity, bounds clamping |
| Annotation codec | `annotation/SlotAnnotationCodecTest.kt` | JSON round trip, schema versions, unknown fields, invalid rectangles |
| Calibration | `calibration/CalibrationCodecCompatibilityTest.kt` | ratio tolerance, exact orientation match, unsupported envelope/profile/slot schema, malformed JSON, blank id, round trip |
| Calibration store | `datastore/CalibrationProfileDataStoreTest.kt` | empty store, upsert, replace, delete, unsupported document refused and left untouched, corrupt document refused and not reset, clear-as-escape-hatch |
| Vision source | `capture/CaptureVisionFrameSourceTest.kt` | capture-status mapping, later state changes, latest-frame publication |
| Vision Lab state | `feature/visionlab/VisionLabViewModelTest.kt` | live frame exposure, freeze/resume/clear, processing success and failure, stale-result discard, profile save refusal, delete-all escape hatch, apply compatibility |
| Capture regression | `capture/CaptureStateHolderTest.kt`, `capture/FrameSamplerTest.kt`, `capture/CaptureStartupExecutorTest.kt` | the Sprint 0 capture lifecycle, sampling and startup behaviour still hold |
| Models | `models/CaptureSessionStateTest.kt`, `permissions/ProjectionPermissionTest.kt` | state machine transitions, consent intent |
| Detection stubs | `detection/DetectionStubsTest.kt` | the Sprint 0 seams still report `NotImplemented` and never fake a result |

## Fixture policy

- **Synthetic images only.** No real game screenshots, no captured frames, no image assets are
  committed. Fixtures use `PixelArrayFramePayload` with generated pixel values.
- The shared fixture is `com.draftlens.vision.testCapturedFrame()`.
- The calibration store test writes to a temporary directory under the system temp folder and
  removes it afterwards.

## What is not covered

- **No physical-device test.** No device or emulator was available in the authoring
  environment. The MediaProjection lifecycle, the Vision Lab overlay alignment on a real
  screen, and the calibration flow on a real game were **not** executed. The checklist in
  `docs/TESTING_PHYSICAL_DEVICE.md` remains pending.
- **Instrumentation tests are compiled, not executed.** `assembleAndroidTest` proves the
  instrumentation sources compile; it does not run them.
- **No performance claim.** The JVM suite measures nothing about real hardware. Processing
  durations are recorded per run in the UI; no 24/7 stability or memory bound is claimed.
- **No recognizer**, so there are no detection-accuracy tests to write.

## Honest failure behaviour

The suites assert that failures are reported rather than hidden: invalid dimensions, payload
mismatches, oversized output, unsupported formats and render errors all produce a
`ProcessFrameResult.Failure` with a reason code, and no partial frame is published. The
calibration suites assert that an unsupported or corrupt document is reported and left
untouched, and that only clearing the document recovers from it.
