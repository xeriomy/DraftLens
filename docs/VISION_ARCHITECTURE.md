# Vision Architecture — Sprints 1A + 1B

## What this layer is

Sprint 1A gives the app a real image-bearing pipeline. Frames carry pixels, geometry is
tested in pure Kotlin, preprocessing validates before it renders, and a Vision Lab lets a
user place manual slot annotations and save calibration profiles. Sprint 1B adds the hero
recognition pipeline on top — still offline, still manual-confirmation only.

**What it is not:** there is no recognizer that selects a hero by itself. No OCR, no automatic
ban/pick detection, no calibrated confidence values, no ML model. Manual slots are calibration
data, never detections.

## Package map

```text
com.draftlens
  capture/
    CaptureSessionService      foreground service, MediaProjection owner, notification actions
    CaptureController          start/stop/pause entry points
    CaptureFreeze              freeze/resume command bridge (service commands + in-app button)
    FramePipeline              ImageReader -> Bitmap, latest-frame-only, freeze-aware
    FrameSampler               monotonic sampling
    CaptureStateHolder         process-local state holder (live + frozen frame)
    CaptureVisionFrameSource   adapter: capture -> VisionFrameSource  (the only bridge)
  models/
    CaptureSessionState, CaptureTransitions, CapturedFrame, FrameSnapshots
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
    val frozenFrame: StateFlow<OriginalFrame?>
    fun freeze(): Boolean
    fun resume()
}
```

`CaptureVisionFrameSource` (in `capture/`) is the only file that knows about both sides.
It maps `CaptureStatus` to `VisionSourceState` and delegates the freeze commands to
`CaptureFreeze`, the same bridge the service's notification action uses — in-app and
notification freezes are one code path.

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

The freeze workflow is a side channel on the same pipeline — never a second capture system:

```text
in-app Freeze / the floating button (pinning via CaptureFreeze) or the Vision Lab Freeze button
  -> CaptureFreeze.freezeCurrentFrame() (the notification issues Pause = ACTION_PAUSE)

  -> CaptureFreeze.freezeCurrentFrame()
  -> FramePipeline freezes publication; copies the latest published frame
  -> CaptureStateHolder.frozenFrame (independently owned copy, at most one)
  -> CaptureVisionFrameSource.frozenFrame -> VisionLabViewModel -> Vision Lab canvas
  -> notification re-posted as FROZEN with a "Resume live capture" action
```

While frozen, the ImageReader keeps acquiring and closing incoming `Image`s (the reader and
VirtualDisplay stay healthy) but performs no conversion or publication, so new frames can
never replace the pinned one. Capture keeps running when the Vision Lab tab is not visible:
the service owns the projection and the process-local holder owns the latest frame. The
Vision Lab only reads.

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
- A frozen frame is an **independently owned copy** (`FrameSnapshots`): pixels are copied and
  metadata (dimensions, rotation, display rotation, timestamps, latency) is preserved
  verbatim; the live pipeline never shares a payload buffer with it. The copy survives the
  projection stopping (for editing while the process lives) but not process death. A new
  capture session drops it. `Clear` releases it.
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

## Sprint 1B — hero recognition boundary

`vision/heroes/` holds the recognition pipeline. It consumes `VisionFrame` inputs (the same
`OriginalFrame`/`SlotImage` contract as the annotation canvas) and never imports capture code.
Everything network-facing goes through `HeroCatalogTransport`; tests use fake transports.

- **Catalog** (`HeroCatalog*`) is public, static metadata only: hero id/name/images/roles/lanes.
  Downloaded only on the user's explicit **Sync catalog** action; cached in the app's private
  storage; works offline after sync. The cache binds each asset to its source URL and a
  SHA-256 of the bytes, validates before reuse, and flushes/syncs snapshots atomically.
- **Extraction** (`SlotImageExtractor`) maps the slot's normalized source rect to source pixels
  and produces an upright in-memory `SlotImage`. Never written to disk.
- **Visual information** (`VisualInformationAnalyzer`) runs before matching and refuses
  blank/near-uniform/detail-free crops as `InsufficientVisualInformation`; it is a pure
  Kotlin, deterministic gate, with conservative uncalibrated heuristics — never presented as
  a recognition accuracy.
- **Matching** (`HeroMatcher`) is a deterministic dHash + color descriptor, an uncalibrated
  prototype ranking baseline. Output is a ranked list of candidates or an explicit no-match;
  neither says the hero exists in the crop. The UI turns that into manual confirm/correct —
  never an automatic selection.
- **Confirmations** (`HeroRecognitionStore`) persist `slotId → {heroId, heroName, confirmedAtMs}`
  in a separate preferences document; it never touches the annotation schema or the calibration
  store, and it contains no images.
- **Privacy**: frames and crops stay in memory on-device; only the public static catalog and
  its reference images are downloaded; INTERNET is the only new permission.
