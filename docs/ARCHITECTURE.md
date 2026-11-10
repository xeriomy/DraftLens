# Architecture — DraftLens

## Stack
Kotlin 2.0.20 · AGP 8.6.1 · Gradle 8.7 · compileSdk/targetSdk 35 · minSdk 26 · Jetpack Compose (material3) ·
Coroutines + Flow · DataStore Preferences. Single Gradle module `:app`.

## Package map
```
com.draftlens
  app/            MainActivity (tab host), DraftLensApp (DI root, no framework)
  capture/        CaptureSessionService (foreground), CaptureController, FramePipeline, FrameSampler,
                  CaptureStateHolder, CaptureVisionFrameSource (capture -> Vision adapter)
  models/         CaptureSessionState + CaptureTransitions, CapturedFrame
  permissions/    ProjectionPermission (consent intent factory)
  datastore/      AppPreferencesRepository (sampling interval),
                  CalibrationProfileDataStore (one JSON calibration document)
  detection/      Interfaces only — ScreenCaptureProvider, FrameProcessor, DraftPhaseDetector,
                  HeroRecognizer, HeroSlotDetector, DetectionValidator (all NotImplemented)
  vision/
    model/        FrameId, FrameEnums, FramePayload, VisionFrame, VisionFrameSource (pure Kotlin)
    model/android/BitmapFramePayload
    geometry/     NormalizedPoint/Rect, PixelPoint/Rect, FrameTransform (pure Kotlin)
    preprocessing/PreprocessTypes, PreprocessingMath, PixelArrayFramePreprocessor,
                  AndroidFramePreprocessor
    annotation/   DraftSlot, SlotAnnotationStore, SlotAnnotationCodec (pure Kotlin)
    calibration/  CalibrationProfile, CalibrationCompatibility, CalibrationProfileCodec,
                  CalibrationRepository (pure Kotlin)
  feature/home/   HomeViewModel, DebugCaptureScreen (tab 0)
  feature/visionlab/ VisionLabViewModel, VisionLabScreen, AnnotationCanvas, SlotEditor,
                  ProfileControls (tab 1)
```
Room and a separate Gradle module are **not** created — Sprint 1A has no independent feature
module, so empty scaffolding is omitted.

## State machine
`IDLE → AWAITING_PERMISSION → STARTING → CAPTURING ⇄ PAUSED → STOPPING → IDLE`, any → `ERROR → IDLE`.
`CaptureTransitions.canTransition()` is the single authority. UI and service both speak
`CaptureSessionState`; the service is the source of truth, the ViewModel mirrors actions.

## Data flow
MediaProjection → VirtualDisplay → ImageReader(maxImages=3) → HandlerThread("draftlens-capture")
→ FrameSampler(monotonic elapsed time, ≥interval) → Bitmap (latest-only, stale ref cleared on stop/reset) → `StateFlow<CapturedFrame>`
→ ViewModel → Compose preview. Main thread never decodes pixels; UI stays responsive.

## Vision boundary
Vision consumes frames through `VisionFrameSource` only. It never imports `CaptureSessionService`
or `CaptureStateHolder`; `capture/CaptureVisionFrameSource.kt` is the single adapter between the
two, mapping `CaptureStatus` to `VisionSourceState` and `CapturedFrame` to `OriginalFrame`.

Processing is explicit: the user presses *Process Frame*, one job runs on `Dispatchers.Default`,
a generation counter discards stale results, and failures are reported instead of published as
partial frames. There is no recognizer, so nothing reports a detection. See
`docs/VISION_ARCHITECTURE.md`.

## Key decisions
- Latest-frame-only slot because stale frames are worthless for draft detection.
- Service foregrounds before `getMediaProjection()`.
- Consent re-requested and token used once per session—never cached.
- Capture dimensions prefer `maximumWindowMetrics` on API 30+; legacy fallback is retained below that.
- Bitmaps are never recycled while Compose may still render them; stopping/resetting clears the state reference.
- Self-capture: no `FLAG_SECURE`; users see system scope options to choose “Entire screen” (debug UI included) or “A single app”.
- MediaProjection buffers are display-oriented, so production frames keep `rotation = ROTATION_0` and only record `displayRotation`.
- Calibration profiles store normalized rectangles and metadata only — never pixels or screenshots.
- No network code. Data-source audit is documentation-only (`docs/DATA_SOURCES.md`).

