# Vision Testing

## Where the tests run

GitHub Actions is the build of record for this sprint. The authoring host is aarch64 and
AGP 8.6.1's `aapt2` is x86_64-only, so nothing that touches resources can be assembled
there. The current, authoritative verification record for this branch is the
**Sprint 1B review-fix pass** section below; the older Sprint 1A/1B run
records further down remain as history.

See the sections below for the exact commands CI runs.

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

| Plane/stride conversion | `capture/RgbaPlaneCopyTest.kt` | landscape 2400x1080 & portrait 1080x2400 exact pixels, non-square frames, padded rows (full-pixel pads and one-pixel pads), offset & edge-touching `cropRect`s, `cropRect` smaller than the frame (letterbox), pixelStride 4/8, big-endian buffers, caller-buffer position independence, malformed inputs refused, full-crop equals direct read |
| Generation counter | `capture/GenerationCounterTest.kt` | generations monotonic, stale after bump, frozen resume does not advance it |
| Display policy | `capture/DisplayChangePolicyTest.kt` | portrait↔landscape always a size-swap resize, identical resize callbacks idempotent |
| Instrumentation | `androidTest/.../FramePipelineInstrumentedTest.kt` | pipeline starts/resizes with new surface and generation bump, freeze with no frames keeps the reader alive, stop releases the surface |

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

## Sprint 1B review-fix pass (this revision)

GitHub Actions is the build of record. The authoring host is **aarch64**, and AGP 8.6.1
publishes only an **x86_64** `aapt2`, so nothing that touches resources can be assembled
locally; every verification below ran in CI on `ubuntu-latest` (x86_64) with JDK 17.

Workflow: `.github/workflows/build.yml` (one `build` job), triggered by `pull_request`
(and pushes to `main`/`master`).

Docs-only commits after the code freeze do not change the build; the exact final head and its CI run are recorded in PR #3's description.

Latest green run: <https://github.com/xeriomy/DraftLens/actions/runs/43773398149>
— head SHA `5252016cd15c824009368e831ed499f24209980d` (docs-only commits after the code freeze
are not part of the 840-tests green state; PR #3's description records the final head).

- **Unit tests: 840 executions (debug + release), 0 failures, 0 errors, 0 skipped.**
- `assembleAndroidTest` — instrumentation sources compile.
- strict `lint` (`abortOnError = true`) — green.
- `assembleDebug` — green; artifact `draftlens-debug`, 10 369 786 bytes,
  digest `sha256:c46580570a85054f225bd4d927350b153991f98956b1e3ee9a877d5ed9c1b3f1`.

The instrumentation suite is **compiled but not executed**. The CI runner has no KVM
access: the default API-30 image does not boot within the runner's timeout, and the
`aosp_atd` image boots but is reported to Gradle as an "Unknown API Level" and is skipped
(`Skipping device 'emulator-5554' ... No compatible devices connected`). Instrumented
tests must be run on a physical device or an emulator with KVM (see
`docs/TESTING_PHYSICAL_DEVICE.md`); they are not claimed as executed anywhere.

The run also re-reads the packaged manifest to confirm the foreground-service permissions
and the `mediaProjection` service type survived the build.

## Test inventory (Sprint 1B additions)

| Suite | File | Covers |
|---|---|---|
| Pixel extraction | `heroes/SlotImageExtractorTest.kt` | asymmetric 3x2/5x3 fixtures, exact pixel order for all four rotations (hand-computed), edge-touching crops, minimum-size and refused crops, source immutability |
| Matcher | `heroes/HeroMatcherTest.kt` | identical images match, ranking, no-match threshold, ambiguous gap, corrupt references skipped, determinism |
| Recognition orchestration | `heroes/HeroRecognitionTest.kt` | candidates path, unusable references → failure, below-threshold → no-match (inverse-gradient fixture), uniform crop → `InsufficientVisualInformation` |
| Visual information | `heroes/VisualInformationTest.kt` | uniform gray/black/white, saturated solids, near-uniform noise, low-resolution crops, smooth & low-contrast gradients (not rejected), textured & pseudo-random detail (accepted), dimension mismatch, end-to-end blank → `InsufficientVisualInformation`, `NoMatch` kept distinct |
| Catalog sync | `heroes/HeroCatalogSyncTest.kt` | unchanged behaviour (pagination, mirror, duplicates, cross-check) |
| Catalog repository | `heroes/FileHeroCatalogRepositoryTest.kt` | snapshot + assets, offline restart, **unchanged URL → no re-download**, **URL change → re-download & new asset served**, **URL change + failed refresh → old asset not exposed**, **corrupt file → not served + re-downloaded**, metadata forgery rejected, missing/corrupt sidecar → re-download, oversized cached file rejected, truncated/HTML-as-PNG rejected, null content-type accepted, oversized download rejected, fresh assets served via network outage |
| Atomic writes | `heroes/WriteAtomicallyTest.kt` | exact-bytes write, failure leaves a directory target untouched and removes the temp file, blocked parent reports failure without touching it, hourly `*.tmp` sweep on init, no temp leftovers on a failed refresh |
| Redirect policy | `heroes/HeroRedirectPolicyTest.kt` | HTTPS targets accepted (absolute/relative), HTTPS→HTTP downgrade refused, non-HTTP schemes refused, missing/blank/over-long targets refused |
| Structural validator | `heroes/StructuralImageValidatorTest.kt` | real PNG passes, truncation/CRC corruption/HTML fail, GIF/WebP framing checks |
| Freeze flow (JVM) | `capture/CaptureFreezeTest.kt` | freeze without an active capture → `NoPipeline`; resume drops the pin; a duplicate freeze keeps the original frame |
| Freeze flow (JVM) | `capture/CaptureStateHolderTest.kt` | pinned frame survives `terminateSession` (stop and error), new session drops it, clear drops it, reset drops it |
| Freeze flow (JVM) | `models/FrameSnapshotsTest.kt` | copy preserves dimensions/rotation/timestamps/id, pixel values equal, caller-array mutation does not affect the copy, capture latency wrapper preserved |
| Vision source | `capture/CaptureVisionFrameSourceTest.kt` | adapter publishes capture-side frozen frames; freeze with no capture → false; resume is a safe no-op |
| ViewModel | `feature/visionlab/VisionLabViewModelTest.kt` | **capture-side freeze shows the pinned frame while newer live frames keep arriving**, resume reveals live frames again, freeze with no frame reports honestly, uniform-gray Frame produces `INSUFFICIENT_INFO` instead of candidates, inverse-gradient → `NO_MATCH`, confirmation/correct of candidates works, direct correction without clearing first, **confirm/clear persistence failure reported, never shown as success** |
| Instrumentation | `androidTest/.../AndroidBitmapExtractionInstrumentedTest.kt` | all four rotations pixel-exact (asymmetric non-square fixtures), edge crops, payload/source dimension mismatch refused, source Bitmap valid after extraction, minimum-size and refused crops, recycled Bitmap refused, bitmap snapshot copies pixels independently |
| Instrumentation | `androidTest/.../ReferenceImageValidatorInstrumentedTest.kt` | real PNG decodes on device, truncated PNG and HTML do not decode, JPEG structure-vs-decode layering |
| Instrumentation | `androidTest/.../CaptureLifecycleInstrumentedTest.kt` | manifest declares the service, `mediaProjection` foreground type, required permissions |

## Fixture policy

- **Synthetic images only.** No real game screenshots, no captured frames, no image assets are
  committed. Fixtures are generated pixel arrays or the built-in 1x1 red/green PNGs
  (`com.draftlens.vision.testRealPng()` / `testRealGreenPng()`), which are real, decodable
  containers with correct CRCs.
- The calibration store test writes to a temporary directory under the system temp folder and
  removes it afterwards.

## What is not covered

- **Instrumentation tests: compiled, not executed.** `assembleAndroidTest` proves the
  instrumentation sources compile; nothing ran them. The CI runner has no KVM access: the
  default API-30 image did not boot within the runner's timeout, and the `aosp_atd` image
  boots but is skipped as "Unknown API Level" by Gradle. Two consecutive attempts failed
  for these reasons, so the emulator job was removed rather than left flaky. Instrumented
  tests must be executed on a physical device or a KVM-enabled emulator before the PR is
  marked verified; none of their results are claimed as executed here.
- **Physical-device smoke of the freeze workflow: pending.** The manual lifecycle matrix
  in `docs/TESTING_PHYSICAL_DEVICE.md` (rows 14–22) covers the notification freeze, the
  FROZEN/Resume cycle, duplicate taps, disabled notifications and stop-with-frozen-frame.
  None of it has been run on a device.
- **MediaProjection lifecycle matrix remains NOT RUN** (rows 2–11).
- **On-device recognition accuracy: not measured.** The no-match threshold and the matcher
  weights are uncalibrated; the visual-information heuristics are conservative starting
  points, not MLBB-calibrated thresholds. Real-screen evaluation and calibration remain
  pending; see `docs/sprints/SPRINT_1B_HERO_RECOGNITION.md` § Known limitations.
- **Catalog entries actually loaded** are measured at device sync time; not recorded here.
- Compose screens are not UI-tested (no compose-ui-test dependency exists in this project);
  the interaction model is covered by the interaction state-machine tests.
- The service-level notification actions (`ACTION_PAUSE` / `ACTION_RESUME_LIVE` delivery through
  `onStartCommand`) is exercised only through the JVM-level `CaptureFreeze`/`CaptureStateHolder`
  paths and manual device verification; no instrumented test taps the system notification.

## Honest failure behaviour

The suites assert that failures are reported rather than hidden: invalid dimensions, payload
mismatches, oversized output, unsupported formats and render errors produce a
`ProcessFrameResult.Failure` with a reason code; unsupported/corrupt calibration documents
are reported and untouched; a failed cache download never replaces a good asset and never
serves a stale one under a changed URL; a failed persistence operation is reported, not
hidden behind a success state. The instrumentation suite covers the Android-only paths
(bitmap extraction, decode validation, manifest).
