# Vision Architecture — Sprint 1A

## What this layer is

Sprint 1A gives the app a real image-bearing pipeline. Frames carry pixels, geometry is
tested in pure Kotlin, preprocessing validates before it renders, and a Vision Lab lets a
user place manual slot annotations and save calibration profiles.

**What it is not:** there is no recognizer. No OCR, no hero identification, no automatic
ban/pick detection, no confidence values, no ML model. The Vision Lab prints
"Detection: not implemented". Manual slots are calibration data, never detections.

## Package map

```text
com.draftlens
  capture/
    CaptureSessionService      foreground service, MediaProjection owner
    CaptureController          start/stop/pause entry points
    FramePipeline              ImageReader -> Bitmap, latest-frame-only
    FrameSampler               monotonic sampling
    CaptureStateHolder         process-local state holder
    CaptureVisionFrameSource   adapter: capture -> VisionFrameSource  (the only bridge)
  models/
    CaptureSessionState, CaptureTransitions, CapturedFrame
  datastore/
    AppPreferencesRepository    sampling interval
    CalibrationProfileDataStore one JSON document under `profiles_json`
  vision/
    model/                     FrameId, FrameEnums, FramePayload, VisionFrame, VisionFrameSource
    model/android/             BitmapFramePayload (the only Android type in vision/model)
    geometry/                  NormalizedPoint/Rect, PixelPoint/Rect, FrameTransform
    preprocessing/             PreprocessTypes, PreprocessingMath, PixelArrayFramePreprocessor,
                               AndroidFramePreprocessor
    annotation/                DraftSlot, SlotAnnotationStore, SlotAnnotationCodec
    calibration/               CalibrationProfile, CalibrationCompatibility, CalibrationProfileCodec,
                               CalibrationRepository
  feature/
    home/                      HomeViewModel, DebugCaptureScreen (tab 0)
    visionlab/                 VisionLabViewModel, VisionLabScreen, AnnotationCanvas,
                               SlotEditor, ProfileControls (tab 1)
```

`vision/**` is pure Kotlin except `vision/model/android/BitmapFramePayload.kt` and
`vision/preprocessing/AndroidFramePreprocessor.kt`, which touch `android.graphics`.

## The boundary

Vision never imports `CaptureSessionService` or `CaptureStateHolder`. It consumes a narrow
seam:

```kotlin
interface VisionFrameSource {
    val frames: StateFlow<OriginalFrame?>
    val state: StateFlow<VisionSourceState>
}
```

`CaptureVisionFrameSource` (in `capture/`) is the only file that knows about both sides.
It maps `CaptureStatus` to `VisionSourceState`:

| CaptureStatus | VisionSourceState |
|---|---|
| `CAPTURING` | `CAPTURING` |
| `PAUSED` | `PAUSED` |
| `IDLE` | `IDLE` |
| `STOPPING` | `STOPPED` |
| `ERROR` | `ERROR` |
| `AWAITING_PERMISSION`, `STARTING` | `UNKNOWN` |

`AWAITING_PERMISSION` and `STARTING` map to `UNKNOWN` because the system consent dialog and
service foregrounding are not yet meaningful to Vision.

## Data flow

```text
MediaProjection -> VirtualDisplay -> ImageReader -> capture HandlerThread
  -> Bitmap (latest only) -> CaptureStateHolder -> CaptureVisionFrameSource
  -> VisionLabViewModel (live or frozen reference) -> VisionLabScreen
  -> explicit Process Frame -> Dispatchers.Default job -> ProcessedFrame
  -> Compose overlay and inspectors
```

Capture keeps running when the Vision Lab tab is not visible: the service owns the
projection and the process-local holder owns the latest frame. The Vision Lab only reads.

## Processing rules

- Processing is **explicit**. Nothing processes automatically; the user presses *Process Frame*.
- At most **one** job runs. A new request increments a generation counter and cancels the
  previous job. A result whose generation is no longer current is discarded, so a slow
  frame can never overwrite a newer one.
- The frame is captured at press time (frozen frame if pinned, otherwise the latest live
  frame), so a frame that arrives mid-flight cannot be substituted.
- Duration uses `System.nanoTime()`.
- Failure is returned as `ProcessFrameResult.Failure` with a reason code. No partial frame is
  ever published as a success.

## Ownership

- The capture pipeline owns the bitmap. `CaptureStateHolder` keeps only the latest frame.
- No path calls `Bitmap.recycle()`. References are dropped; native memory is reclaimed by GC.
- `BitmapOwnership.SHARED_READ_ONLY` is descriptive, not a synchronized lifetime system:
  consumers may draw the bitmap but must not mutate or recycle it.
- The Vision Lab pins exactly one extra reference while a frame is frozen. *Clear* releases it.
- Calibration profiles store normalized rectangles, labels and metadata only — never pixels,
  bitmaps, transform ids or screenshots.

## Persistence

`CalibrationProfileDataStore` writes one JSON string under `profiles_json` in the
`draftlens_calibration` preferences store. Every mutating operation reads and decodes the
current document inside the same transaction, so an unsupported or corrupt document is never
overwritten. `clearDocument()` is the only escape route out of a document this build cannot
read; it is what the "Delete all calibration data" action calls.

## Failure model

| Situation | Result |
|---|---|
| Frame dimensions ≤ 0 | `INVALID_DIMENSIONS` |
| Payload size ≠ source dimensions | `PAYLOAD_MISMATCH` |
| Source pixels above `maxPixels` | `TOO_MANY_PIXELS` |
| Crop empty or out of range | `INVALID_CROP` |
| Output above the pixel budget | `OUTPUT_TOO_LARGE` |
| Non-bitmap payload on the Android path | `UNSUPPORTED_FORMAT` |
| Bitmap drawing threw | `RENDER_FAILED` |
| Calibration document newer than schema 1 | `CalibrationLoadResult.UnsupportedSchema`, writes refused |
| Calibration document unparseable | `CalibrationLoadResult.Corrupt`, writes refused |
| Calibration slot with a too-small rectangle, an index below 1, a duplicate id or a duplicate `(type, team, index)` | `CalibrationLoadResult.Corrupt`, writes refused |
| Two calibration profiles with the same id | `CalibrationLoadResult.Corrupt`, writes refused |

Slot semantics are validated once, in `vision/annotation/SlotValidator`, and used by both
the annotation codec and the calibration codec. A slot the editor would refuse cannot be
persisted as calibration data, and a stored document that contains one is reported as
`Corrupt` and left untouched until it is deleted.
