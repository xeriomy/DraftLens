# Testing

## Where tests run

GitHub Actions is the build of record. `.github/workflows/build.yml` runs `test`,
`assembleAndroidTest`, `lint`, `assembleDebug`, uploads the APK and inspects its manifest on
every push and pull request. `main` is protected by the build check.

The authoring host is aarch64 while the SDK's `aapt2` is x86_64-only, so resource-touching
Gradle tasks cannot run locally here. At head `60dd412` CI reported **926 unit tests, 0
failures, 0 errors, 0 skipped**, strict lint passing, and instrumentation compiled.

## JVM unit tests

The 47 JVM test files cover the pure logic. Grouped by area:

- **Geometry and frames** — `vision/geometry/GeometryTest`, `vision/model/FrameOrientationTest`,
  `vision/model/VisionFrameTest`, plus `TestFrames` fixtures. Rotation round trips, normalized
  and pixel conversions, frame contracts.
- **Preprocessing** — `PreprocessingMathTest`, `PixelArrayFramePreprocessorTest`,
  `SourceToProcessedMatrixTest`. Validation failures, sizing and matrix agreement.
- **Annotation and calibration** — `SlotAnnotationStoreTest`, `SlotAnnotationCodecTest`,
  `CalibrationCodecCompatibilityTest`, `CalibrationProfileDataStoreTest`. Validation, strict
  codecs, profile compatibility and persistence.
- **Canvas and gestures** — `AnnotationGeometryTest`, `AnnotationHitTestTest`,
  `AnnotationInteractionTest`, `CanvasGeometryTest`, `OrientedBitmapLayoutTest`,
  `SlotPlacementTest`, `VisionLabViewModelTest`. Hit testing, gesture state machine, fitted
  layout and ViewModel flows.
- **Capture logic** — `FrameSamplerTest`, `DisplayChangePolicyTest`, `CaptureStartupExecutorTest`,
  `CaptureStateHolderTest`, `GenerationCounterTest`, `PendingResizeTest`, `RgbaPlaneCopyTest`,
  `CaptureDiagnosticsTest`, `CaptureFreezeTest`, `CaptureVisibilityPolicyTest`,
  `VisibilityFreezeSequenceTest`, `PublishStateTest`, `CaptureVisionFrameSourceTest`,
  `CaptureSessionStateTest`, `FrameSnapshotsTest`, `ProjectionPermissionTest`.
- **Recognition** — `HeroCatalogCodecTest`, `HeroCatalogSyncTest`, `FileHeroCatalogRepositoryTest`,
  `HeroRedirectPolicyTest`, `WriteAtomicallyTest`, `SlotImageExtractorTest`,
  `StructuralImageValidatorTest`, `VisualInformationTest`, `HeroMatcherTest`,
  `HeroRecognitionTest`, `HeroRecognitionStoreTest`. Cache safety, extraction for all
  rotations, visual-information rejection, ranking and confirmation persistence.
- **Overlay** — `OverlayPositionTest`, `OverlayEnablementTest`. Position math, edge snapping and
  permission decision.

## Instrumented tests

Five files compile in CI but are **not executed**: `CaptureLifecycleInstrumentedTest`,
`FramePipelineInstrumentedTest`, `AndroidBitmapExtractionInstrumentedTest`,
`ReferenceImageValidatorInstrumentedTest` and `FloatingControlOverlayInstrumentedTest`. The
hosted runner has no KVM; the default emulator image does not boot within the timeout and the
`aosp_atd` image is skipped as an unknown API level. Run these on a physical device or a
KVM-enabled emulator.

## What CI cannot prove

CI does not exercise MediaProjection consent, overlay visibility on real hardware, rotation and
resize storms, notification shade behaviour, or recognition accuracy on actual MLBB screens.
Those items are in [TESTING_PHYSICAL_DEVICE.md](TESTING_PHYSICAL_DEVICE.md) and remain honestly
NOT RUN. Transport tests use fake responses, so the number of catalog entries actually loaded
is known only at device sync time.
