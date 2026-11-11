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

Latest green run: <https://github.com/xeriomy/DraftLens/actions/runs/42943730823>
— head SHA `4c42513690ab1e05b817c3a97f6949ba57a60fb7`, **216 unit tests, 0 failures, 0 errors,
0 skipped**, executed in both the debug and the release unit-test variants (432 test
executions in total).

Artifact `draftlens-debug` from that run: digest
`sha256:dd501ac7fbe282de56c318ff581f77b76813b087db8d8b8944a041065deaf7c4`; the APK inside it
(`app-debug.apk`) has SHA-256
`692a9221afa57763e6a39bf840d1fca409e3c433f5e69d75ba760af86ff3fe7f`.

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
| Calibration | `calibration/CalibrationCodecCompatibilityTest.kt` | ratio tolerance, exact orientation match, unsupported envelope/profile/slot schema, malformed JSON, blank id, round trip, unusable slots, duplicate slot ids, duplicate `(type, team, index)`, duplicate profile ids |
| Calibration store | `datastore/CalibrationProfileDataStoreTest.kt` | empty store, upsert, replace, delete, unsupported document refused and left untouched, corrupt document refused and not reset, a document with an unusable slot is corrupt and not replaced, clear-as-escape-hatch |
| Vision source | `capture/CaptureVisionFrameSourceTest.kt` | capture-status mapping, later state changes, latest-frame publication |
| Display changes | `capture/DisplayChangePolicyTest.kt` | a rotation with unchanged dimensions needs only a rotation update; a changed size needs a resize; the pipeline records and updates its rotation |
| Canvas geometry | `feature/visionlab/CanvasGeometryTest.kt` | points inside the image map to normalized source coordinates; all four letterbox boundaries are rejected; the image edges themselves are accepted |
| Canvas rendering | `feature/visionlab/OrientedBitmapLayoutTest.kt` | the rendered bitmap bounds match the fitted rectangle for all four rotations; the scale is uniform so the image is never stretched; portrait and landscape frames map their image edges onto the workspace edges; the overlay of the whole frame aligns with the rendered bitmap bounds; the bitmap is drawn around the fitted centre |
| Canvas gestures | `feature/visionlab/AnnotationGeometryTest.kt` | a created rectangle spans the two drag points in either direction, is clamped to the unit square and is discarded below the minimum size; a resize moves only the dragged corner and is refused when it would collapse the rectangle |
| Canvas workspace | `feature/visionlab/AnnotationGeometryTest.kt` | a portrait frame gets a tall workspace; a landscape frame keeps the minimum workspace height; an unknown ratio falls back to the minimum |
| Canvas hit testing | `feature/visionlab/AnnotationHitTestTest.kt` | the topmost slot wins where rectangles overlap |
| Vision Lab state | `feature/visionlab/VisionLabViewModelTest.kt` | live frame exposure, freeze/resume/clear, clear cancels in-flight processing, processing success and failure, stale-result discard, profile save/update/apply require a frozen frame, profile save refusal, delete-all escape hatch, apply compatibility |
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
