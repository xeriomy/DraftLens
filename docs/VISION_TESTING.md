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

Latest green run: <https://github.com/xeriomy/DraftLens/actions/runs/43643640990>
— head SHA `5ffb0c193cb84da77fb49984c81e219178c7ef3f`, **269 unit tests, 0 failures, 0 errors,
0 skipped**, executed in both the debug and the release unit-test variants (538 test
executions in total).

The APK artifact digest is reported in the implementation report and in the pull request.

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
| Annotation interaction | `feature/visionlab/AnnotationInteractionTest.kt` | a tap selects a slot; a tap on empty space does nothing; a drag on the selected slot moves it; a drag on an unselected slot selects it and never moves it; a movement below the touch slop never starts a move; a long press on a slot opens its menu and is not a move; a long press on empty space starts drawing; drawing produces a rectangle on release and nothing without one; the delete control deletes that slot; a handle drag resizes and never moves; a press outside the image does nothing |
| Annotation targets | `feature/visionlab/AnnotationInteractionTest.kt` | a point on a slot resolves to that slot; empty image space resolves to empty; a point outside the image resolves to outside; the delete control wins over the slot itself; the delete control sits exactly where the top-right handle was; the top-right corner has no resize handle; the remaining handles are still there; a resize handle resolves to that handle |
| Long press | `feature/visionlab/AnnotationInteractionTest.kt` | a long press opens the menu without any movement; a slight movement after the long press neither delays nor repeats the menu; a normal tap never opens the menu; moving a selected slot never opens the menu; an early drag past the slop cancels the pending long press (unselected drag selects, selected drag moves, empty-space drag never draws); the deadline stays armed within the slop; after the timeout the deadline is abandoned and movement can neither delay nor cancel the menu; creating requires hold, threshold, drag |
| Slot placement | `feature/visionlab/SlotPlacementTest.kt` | an offset slot stays inside the image and keeps its size; an offset slot is moved away from its source |
| Slot restore | `annotation/SlotAnnotationStoreTest.kt` | a deleted slot is restored with its own id and metadata; restoring a slot whose index or id is taken is refused |
| Canvas hit testing | `feature/visionlab/AnnotationHitTestTest.kt` | the topmost slot wins where rectangles overlap |
| Vision Lab state | `feature/visionlab/VisionLabViewModelTest.kt` | live frame exposure, freeze/resume/clear, clear cancels in-flight processing, processing success and failure, stale-result discard, profile save/update/apply require a frozen frame, profile save refusal, delete-all escape hatch, apply compatibility, selecting a slot by id, deleting an arbitrary slot, undo of a deletion and of a creation, copy/paste with a new id and the next free index, duplicate keeping the source, the pasted slot staying inside the image, paste using the copied slot as the source with the target only anchoring the placement, paste without a target offsetting from the copied slot, the menu opening for an existing slot, opening the menu on an unselected slot selecting it, an undoable move |
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

- **Physical-device smoke: maintainer-reported PASS for the Vision Lab interaction behavior.**
  Slot selection, movement and resizing; the long-press property menu; long-press-and-drag
  creation; the original compact red X deletion control; copy, paste and duplicate; fullscreen
  entry/exit and Android Back; annotation gestures isolated from page scrolling; and existing
  portrait/landscape rendering were confirmed working on a physical device. Device model,
  Android version, install source/APK artifact, test date, logs and recordings were **not**
  supplied, so they are not recorded here.
- **MediaProjection lifecycle matrix remains NOT RUN.** Repeated start/stop, projection
  revocation, app-process interruption, rotation/resize, background stability and sustained
  capture scenarios in `docs/TESTING_PHYSICAL_DEVICE.md` are untested.
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

## Sprint 1B tests (inventory)

| Suite | File | Covers |
|---|---|---|
| Hero catalog sync | `heroes/HeroCatalogSyncTest.kt` | pagination to the reported total, mirror fallback, non-zero API code, duplicate-ID collapse, invalid-record skip, MLBBDex cross-check match/unavailable, roles/lanes by slug |
| Hero catalog codec | `heroes/HeroCatalogCodecTest.kt` | snapshot round-trip, schema refusal, blank id/name, non-HTTPS asset URL, unknown fields |
| Hero catalog repository | `heroes/FileHeroCatalogRepositoryTest.kt` | refresh stores snapshot + assets, atomic cache, offline restart, corrupt cache error, asset magic-byte/content-type/size validation, refresh failure keeps cache |
| Slot extraction | `heroes/SlotImageExtractorTest.kt` | portrait/landscape, 0/90/180/270 upright output, edge-touching and minimum-size rectangles, below-one-pixel rejection, payload mismatch, source untouched |
| Matcher | `heroes/HeroMatcherTest.kt` | identical images match, ranking order, no-match threshold, ambiguous gap, corrupt references skipped, hamming/color distance, determinism |
| Recognition orchestration | `heroes/HeroRecognitionTest.kt` | candidates path, no usable references → failure, no-match path |
| Confirmation store | `datastore/HeroRecognitionStoreTest.kt` | confirm/load/replace/clear, corrupt and future documents read as empty |
| ViewModel | `feature/visionlab/VisionLabViewModelTest.kt` | identify without slot → message, identify without catalog → FAILED honestly, synced catalog → candidates, confirm → persisted, clear → removed, catalog name search |

Sprint 1B green runs: latest branch head `e58611c25d7d9a40592aa3d81ad3347869771cc3` (class-scoped
test Main, test-only) —
<https://github.com/xeriomy/DraftLens/actions/runs/43732908605> (646 executions, debug +
release, **0 failures, 0 errors, 0 skipped**); prior green heads `b4eac9d856fe049d8d6ae8d232472bd4627c1f4e`
(run 43732111174) and `7d0c9032a52db8e0f962beea72ceae7120822c88` (run 43731484922), same totals. Instrumentation
tests compiled, not executed. The 1B transport is exercised only through fake responses; no
test performs a real network call. The number of catalog entries loaded from the live API is
measured at device sync time, not in CI.
