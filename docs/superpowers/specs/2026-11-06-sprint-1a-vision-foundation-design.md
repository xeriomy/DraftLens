# Sprint 1A — Vision Foundation, Frame Preprocessing & Vision Lab Design

Date: 2026-11-06
Repository: `xeriomy/DraftLens`
Branch: `sprint-1-vision-foundation`
Base: `main` after PR #1 (`ae92eaf`)

## Intent

Make captured frames usable by independent computer-vision components without claiming automatic detection. Sprint 1A delivers a real image-bearing frame contract, rotation-aware preprocessing, tested normalized-coordinate geometry, manual slot annotation, a Vision Lab debugging screen, and versioned calibration profiles.

This sprint deliberately does **not** implement OCR, hero recognition, automatic draft detection, ML models, overlays, or any network behavior. Manual annotations are calibration data, not detections.

## Goals

1. Replace metadata-only detection hooks with a production `VisionFrame` contract carrying real pixels or an explicit test payload.
2. Keep capture working: latest-frame-only ownership, no recycling while Compose may read a bitmap, no disk persistence.
3. Add a pure, JVM-tested geometry subsystem for source, normalized, oriented, cropped, scaled, and processed coordinates.
4. Add a preprocessing engine that validates input, applies right-angle rotation and optional crop, preserves aspect ratio, and reports dimensions, scale, and duration honestly.
5. Add manual `BAN` / `PICK` slot annotations with stable IDs, normalized rectangles, team/type metadata, validation, and JSON serialization.
6. Add a dedicated Compose Vision Lab behind tabs that keep Debug Capture one tap away.
7. Persist calibration metadata only in DataStore; never persist screen images.
8. Run CI as the build of record and deliver a debug APK artifact.

## Non-goals and anti-goals

- No OCR, LiteRT, TensorFlow, OpenCV, model training, hero catalog, recognition, or automatic ban/pick detection.
- No hero IDs, confidence values, or detection results may be fabricated.
- No automatic full-resolution processing on every frame by default.
- No writing screenshots to disk automatically.
- No upload, packet inspection, game-process access, automated touches, or `FLAG_SECURE`.
- No Room unless a concrete persisted entity is introduced; DataStore is sufficient for the profile envelope.
- No separate Gradle module in this sprint.

## Constraints

- Single Gradle module `:app`; logical packages only.
- Kotlin 2.0.20, AGP 8.6.1, Gradle 8.7, compileSdk/targetSdk 35, minSdk 26, JDK 17.
- Vision must not depend on `CaptureSessionService` or `CaptureStateHolder`. Capture supplies frames through a narrow `VisionFrameSource` interface; an adapter in capture wiring maps live capture state.
- Capture behavior must remain operational and keep its consent flow.
- Local host is aarch64 with x86_64 Android SDK tools; local `assembleDebug` is not the build of record. GitHub Actions runs test, Android-test compilation, strict lint, and debug assembly.
- CI must remain green; instrumentation tests are compiled but not claimed as executed without a device or emulator.

## Approaches considered

### A. Layered single-module foundation (selected)

Pure Kotlin packages `vision/model`, `vision/geometry`, `preprocessing`, `annotation`, and `calibration` carry domain logic and tests. Android Bitmap operations, Compose UI, and DataStore stay at the edges. `CapturedFrame` is refactored to a non-null payload. A capture-side adapter implements `VisionFrameSource` without exposing capture services to Vision.

Trade-offs: keeps the sprint within one module and reuses Compose/DataStore conventions; tests cover the parts most likely to break. It does not physically isolate Vision into a separate artifact yet.

### B. Separate `:vision` Gradle module now

Cleaner dependency graph immediately, but ADR-001 and this sprint require one module until a second feature justifies independent build and deploy. It adds build configuration before a stable interface exists.

### C. UI-first prototype with core extracted later

Fastest way to see rectangles move, but the UI would be built against temporary shapes and likely fake state. It conflicts with the requirement for a production contract and JVM-tested geometry before UI claims alignment.

**Decision:** A.

## Repository audit findings that shape the design

- `CapturedFrame.bitmap` is nullable solely to keep JVM tests simple; production should not accept null pixels.
- `FramePipeline` already performs all pixel work on `HandlerThread("draftlens-capture")`, keeps one latest bitmap, and drops stale frames.
- `CaptureStateHolder` is a process-local singleton; stopping clears frame references; Compose must never see a recycled bitmap.
- `DetectionInterfaces.FrameRef` is metadata-only and cannot feed preprocessing or annotation.
- Tests can use synthetic images if frame metadata and payloads are decoupled from Android Bitmap.
- DataStore is already used for preferences and is an appropriate store for a small number of JSON profile documents.
- Static screens may not emit new frames under `AUTO_MIRROR`; UI must show freshness/live/frozen state honestly.
- `AUTO_MIRROR` and partial screen capture mean the Vision Lab must tolerate crops, empty states, and changing dimensions.

## Package map

```text
com.draftlens
  capture/
    ... existing capture code
    CaptureVisionFrameSource.kt       # adapter: CaptureStateHolder/CapturedFrame -> VisionFrameSource
  datastore/
    AppPreferencesRepository.kt       # unchanged
    CalibrationProfileDataStore.kt   # profile JSON envelope, schema gate
  models/
    CapturedFrame.kt                 # payload becomes non-null
  vision/
    model/                           # pure Kotlin contracts and metadata
    model/android/                   # Android Bitmap payload implementation
    geometry/                        # pure Kotlin normalized/pixel/transform tests
    preprocessing/                   # validation, sizing, Android Bitmap renderer
    annotation/                      # pure Kotlin slot store, JSON codec
    calibration/                     # pure profile compatibility + DataStore adapter
    processing/                      # explicit-frame processor interface and generation gate
  feature/
    home/
      DebugCaptureScreen.kt          # existing capture UI retained
    visionlab/
      VisionLabScreen.kt
      VisionLabViewModel.kt
      AnnotationCanvas.kt
```

No empty packages or new Gradle modules are introduced. `vision/processing` exists only if the implementation needs a narrow explicit processor interface; otherwise the interface stays in `vision/model`.

## Vision Input Contract

### Types

```kotlin
@JvmInline value class FrameId(val value: String)

enum class PixelFormat { ARGB_8888 }
enum class FrameRotation { ROTATION_0, ROTATION_90, ROTATION_180, ROTATION_270 }
enum class OrientationCategory { PORTRAIT, LANDSCAPE, SQUARE }

sealed interface FramePayload {
    val width: Int
    val height: Int
    val format: PixelFormat
    val byteCountEstimate: Long
}

class PixelArrayFramePayload(
    override val width: Int,
    override val height: Int,
    pixels: IntArray,
    override val format: PixelFormat = PixelFormat.ARGB_8888,
) : FramePayload
```

`PixelArrayFramePayload` defensively copies pixels and exists for JVM tests and synthetic fixtures.

Android implementation:

```kotlin
package com.draftlens.vision.model.android

class BitmapFramePayload(
    val bitmap: Bitmap,
    val ownership: BitmapOwnership = BitmapOwnership.SHARED_READ_ONLY,
) : FramePayload
```

`SHARED_READ_ONLY` means consumers may inspect and draw the bitmap but must not mutate or recycle it, and must drop their reference on freeze/reset/stop. Outputs created by preprocessing are ordinary shared-read-only bitmaps; ownership is descriptive, not a synchronized lifetime system. No Compose or Vision path calls `recycle()`.

### Original, processed, and slot images

```kotlin
data class OriginalFrame(
    val id: FrameId,
    val payload: FramePayload,
    val sourceWidth: Int,
    val sourceHeight: Int,
    /** Display rotation at acquisition, recorded by capture for inspection. */
    val displayRotation: FrameRotation,
    /** Correction needed to make the pixels upright. MediaProjection buffers are usually already display-oriented. */
    val rotation: FrameRotation,
    val capturedAtElapsedRealtimeMs: Long,
    val wallClockTimestampMs: Long,
    val captureLatencyMs: Long?,
)

data class ProcessedFrame(
    val id: FrameId,
    val sourceId: FrameId,
    val payload: FramePayload,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val displayRotation: FrameRotation,
    val rotation: FrameRotation,
    val capturedAtElapsedRealtimeMs: Long,
    val wallClockTimestampMs: Long,
    val transform: FrameTransform,
    val processingDurationMs: Long,
)

data class SlotImage(
    val id: FrameId,
    val sourceId: FrameId,
    val slotId: String,
    val payload: FramePayload,
    val sourceRect: PixelRect,
    val createdAtElapsedRealtimeMs: Long,
)
```

All three implement the common `VisionFrame` marker interface or sealed contract. Metadata is immutable. Pixel payloads are non-null at every production boundary. The previous nullable `CapturedFrame.bitmap` is removed; tests construct `PixelArrayFramePayload`.

`CapturedFrame` remains the capture-layer model and embeds `OriginalFrame` or exposes `toVisionFrame()`. It carries no density-derived coordinates.

### Capture rotation wiring

`CaptureSessionService` reads `WindowManager.defaultDisplay.rotation` at capture start and display recreation, maps `Surface.ROTATION_*` to `FrameRotation`, passes it through `FramePipeline`, and stores it on `CapturedFrame` as `displayRotation`.

MediaProjection receives an already display-oriented buffer, so production frames set `rotation = ROTATION_0` and keep `displayRotation` for inspection. Double-rotating an upright capture is never implicit. Synthetic fixtures and future authorized imports may set a non-zero `rotation`, and the preprocessing/geometry code applies that correction normally. This keeps display orientation visible without pretending MediaProjection pixels need another transform.

### Frame source seam

```kotlin
enum class VisionSourceState { IDLE, CAPTURING, PAUSED, ERROR, STOPPED, UNKNOWN }

interface VisionFrameSource {
    val frames: StateFlow<OriginalFrame?>
    val state: StateFlow<VisionSourceState>
}
```

`CaptureVisionFrameSource` in `capture/` maps `CaptureStateHolder.latestFrame` and `CaptureStateHolder.state` to this interface. Vision packages never import capture services. Tests use `StaticVisionFrameSource` with synthetic frames.

## Coordinate conventions

Normalized coordinates use source-image pixel axes as captured: `x` from left to right, `y` from top to bottom, both in `[0.0, 1.0]`. They are not display dp, not density-scaled, and not affected by crop origin.

Rectangle rules:

- `left < right`
- `top < bottom`
- all four coordinates in `[0.0, 1.0]`
- `NormalizedRect.EMPTY` is not valid for storage or input

Rotation semantics: `rotation` is the pixel-orientation correction required to make the source upright. `displayRotation` records the device display rotation at acquisition and is not by itself a reason to transform already-upright MediaProjection pixels. The same 0/90/180/270 mappings apply to any non-zero correction. Source-to-oriented mappings are:

| Rotation | Output dimensions | Source point `(x, y)` → oriented point `(x', y')` |
|---|---|---|
| 0° | `W × H` | `(x, y)` |
| 90° CW | `H × W` | `(H - 1 - y, x)` |
| 180° | `W × H` | `(W - 1 - x, H - 1 - y)` |
| 270° CW | `H × W` | `(y, W - 1 - x)` |

Processing pipeline order is fixed:

```text
validate → normalize orientation → optional crop on oriented image → resize without distortion
```

The crop rectangle is a normalized rectangle in **oriented** coordinates. The final `ProcessedFrame.transform` stores:

- source dimensions,
- rotation,
- crop rectangle or identity crop,
- oriented dimensions,
- crop pixel rectangle,
- scale factor,
- processed dimensions.

`FrameTransform` provides round trips:

- `normalized ↔ sourcePixel`
- `sourcePixel ↔ orientedPixel`
- `orientedPixel ↔ cropPixel`
- `sourcePixel ↔ processedPixel`
- `sourceNormalizedRect ↔ processedPixelRect`
- inverse transforms with a documented floating-point tolerance

Transformations are reversible when the output does not intentionally discard pixels. Crop and downscale are affine maps and remain reversible within tolerance. Anything below the configured processed resolution is not recovered by scaling up.

## Preprocessing

```kotlin
data class ProcessFrameRequest(
    val maxProcessedDimension: Int = 1280,
    val maxPixels: Long = 8_000_000,
    val crop: NormalizedRect? = null,
)

sealed interface ProcessFrameResult {
    data class Success(val frame: ProcessedFrame) : ProcessFrameResult
    data class Failure(val reason: PreprocessFailure) : ProcessFrameResult
}
```

Validation rejects:

- width or height ≤ 0
- pixel count > configured max
- source dimensions inconsistent with payload
- unsupported/non-ARGB format
- empty or out-of-range crop
- `left >= right` or `top >= bottom`
- output dimension ≤ 0
- processed byte estimate above the configured limit

`calculateOutputSize()` uses the oriented/cropped aspect ratio. The processed long side is bounded by `maxProcessedDimension`; smaller sources are never upscaled. Width and height are rounded to preserve aspect ratio within one pixel and to avoid stretching.

Rendering:

- Android path uses `Bitmap` with a destination sized to the final output and one canvas/matrix operation for rotation, crop, and scale. No intermediate full-size oriented bitmap is created.
- If rotation is 0°, crop is null, and scaling is not required, the result may reference the source payload instead of allocating a duplicate; the result still records an identity transform and honest duration.
- JVM path renders `PixelArrayFramePayload` using pure Kotlin for tests and synthetic fixtures.
- Processing runs on a dedicated `Dispatchers.Default` job, never on the UI thread, never on the capture HandlerThread, and only after explicit user request.
- One processing job may be active. A new request cancels or discards the older job; stale results cannot overwrite the current request.
- Duration uses monotonic nanoseconds (`System.nanoTime()`), not wall-clock time.
- Failure is returned as `ProcessFrameResult.Failure`; no partial bitmap is published as success.

No strict memory bound is claimed. The docs record expected allocations and measured durations from synthetic fixtures, but do not claim stable 24/7 behavior without a physical-device soak test.

## Draft Slot Annotation

```kotlin
enum class SlotType { BAN, PICK }
enum class SlotTeam { ALLY, ENEMY, GLOBAL }

data class DraftSlot(
    val schemaVersion: Int = SlotSchema.CURRENT,
    val id: String,
    val type: SlotType,
    val team: SlotTeam,
    val index: Int,
    val rect: NormalizedRect,
    val label: String? = null,
)
```

`SlotAnnotationStore` is pure Kotlin and owns operations:

- add with injected ID generator; default next index in the selected `(type, team)` scope
- move and resize with clamping to normalized bounds
- delete and select
- update team, type, index, label
- change index with duplicate validation in `(type, team)` scope
- overlap detection across all slots; same-scope overlap is an error, cross-scope overlap is a warning
- reset to empty
- JSON encode/decode with schema version

Bounds are always validated. Rectangle creation requires a minimum size in normalized units (`0.01`), so accidental taps do not create slots. IDs are stable and are not recycled after deletion.

Annotations store normalized source-frame coordinates. They are calibration metadata, never hardcoded device pixels and never presented as detected slots. The UI labels the card “Manual slots” and shows “Not detected” rather than a confidence value.

## Calibration profiles

### Schema

```json
{
  "schemaVersion": 1,
  "profileId": "uuid",
  "label": "MLBB draft — 16:9 landscape",
  "targetAspectRatio": 1.7777778,
  "expectedOrientation": "LANDSCAPE",
  "createdAtMs": 1762400000000,
  "updatedAtMs": 1762400000000,
  "slots": [
    {
      "schemaVersion": 1,
      "id": "uuid",
      "type": "BAN",
      "team": "ALLY",
      "index": 1,
      "rect": { "left": 0.05, "top": 0.10, "right": 0.14, "bottom": 0.28 },
      "label": null
    }
  ]
}
```

The enclosing DataStore document also has an envelope version. Profiles are stored in a dedicated `draftlens_calibration` preferences DataStore under one serialized JSON value. This is small metadata only; it never contains bitmaps, pixels, or screenshots.

Rules:

- Version `1` is accepted and written.
- `schemaVersion > CURRENT` is rejected with a clear error; the stored document is left untouched, `update/save` is refused, and deletion remains allowed as the escape hatch. There is no export action in this sprint.
- Corrupted JSON produces a visible error state, not an empty list that could overwrite user data.
- Target and candidate aspect ratios use **oriented dimensions**: source dimensions after `rotation` is applied, before crop and before scale. This describes the frame as the user sees and annotates it.
- Aspect ratio compatibility uses `abs(candidate - target) / target <= 0.01` (1%).
- Expected orientation must match the candidate’s `PORTRAIT` / `LANDSCAPE` / `SQUARE` classification exactly.
- Applying an incompatible profile is refused; the UI shows expected vs actual ratio/orientation and offers recalibration.
- Saving never applies silently. Apply, update, and delete are explicit actions.
- Created and updated timestamps use wall-clock milliseconds; frame transform IDs and bitmaps are not persisted.

Required CRUD:

- create profile from current annotations and selected reference frame metadata
- list profiles
- load/apply profile into the current annotation set after compatibility check
- update label/slots/timestamp
- delete profile

## Vision Lab UI

`MainActivity` uses a Material 3 `TabRow` with two destinations: `Debug Capture` and `Vision Lab`. Capture state lives in the existing service/holder, so switching tabs does not stop capture. Debug Capture remains visible and usable. No `FLAG_SECURE` is added.

### A. Frame Preview

Shows either the frozen frame or the latest live frame:

- original width × height
- rotation
- processed width × height when available
- processing duration in milliseconds or `—`
- current source state (`CAPTURING`, `PAUSED`, `IDLE`, etc.)
- `LIVE` / `FROZEN` badge
- Freeze/Resume button; freeze pins the currently displayed frame in memory only
- Process Frame button; explicitly processes the frozen frame if any, otherwise the latest frame at press time
- Clear freeze control; releases the pinned reference

Nothing is written to disk automatically.

### B. Annotation Canvas

The canvas draws the **oriented, uncropped** frame with `ContentScale.Fit` geometry inside a black rounded card. "Oriented" means `rotation` is applied to produce an upright view; crop is not part of the annotation viewport. Overlay coordinates are computed from the actual fitted rectangle, not from screen dp guesses. Pointer positions are converted through the frame transform to source-normalized annotation coordinates; if the underlying buffer is already display-oriented (`rotation = ROTATION_0`), the conversion is an affine fit of the source image.

Interactions:

- tap an existing rectangle to select it
- drag inside a rectangle to move it
- drag one of four corner handles to resize
- drag empty canvas to create a rectangle
- tap empty canvas to deselect
- delete via button or keyboard action
- overlay never redraws or mutates the source bitmap

Each overlay shows a short label such as `A-B1`, `E-P2`, or `G-B1`. Selection is visible. The same annotation set is available below the canvas as text controls: select from list, edit normalized `left/top/right/bottom`, change type, team, index, label, and delete. This is the accessibility alternative; screen readers can edit all metadata without gesture-only resize.

### C. Slot Inspector

A list of manual slots with:

- slot ID (short ID shown; full ID available)
- type and team
- index
- rectangle coordinates
- validation state and overlap/duplicate messages

Editing metadata updates the in-memory annotation set immediately and invalidates any “profile saved” indicator.

### D. Processing Inspector

Displays actual values only:

- source dimensions
- output dimensions
- rotation
- crop region (or `none`)
- scale factor
- processing duration
- validation errors or honest failure
- whether the displayed pixels are frozen or live

No detection confidence, hero result, or draft phase is shown. The card explicitly says “Detection: not implemented”.

### Profiles

The screen includes profile controls: save current annotations, list profiles, apply compatible profile, update profile, delete profile, and show mismatch warnings. A profile action never silently changes slots.

## State and ownership flow

```text
MediaProjection → VirtualDisplay → ImageReader → capture HandlerThread
  → Bitmap (latest only) → CaptureStateHolder → CaptureVisionFrameSource
  → VisionLabViewModel (live or frozen reference) → VisionLabScreen
  → explicit ProcessFrame request → background job → ProcessedFrame
  → Compose overlay / inspectors
```

Freeze stores one extra bitmap reference. Resume releases it. Capture keeps running independently. Vision preprocessing does not share the capture HandlerThread or ImageReader.

## Serialization and schema testing

`kotlinx-serialization-json` is added for profile JSON. `SlotAnnotationCodec` and `CalibrationProfileCodec` are pure Kotlin and JVM-tested. Unknown future schema versions are rejected; malformed JSON is reported; valid v1 round-trips byte-for-byte at the object level; slot IDs survive round-trip; invalid rectangles are rejected after decode.

## Testing

### Geometry (JVM)

- all four rotations
- portrait, landscape, and square classification
- source→normalized→source round trip
- source→processed→source round trip
- normalization after 90/180/270 rotation
- crop-aware mapping and inverse
- downscaled mapping and inverse
- invalid rectangles: reversed edges, out-of-range, NaN/infinity
- invalid image dimensions and inconsistent payload dimensions
- orientation category mismatch
- crop outside oriented image
- aspect-ratio mismatch calculation

### Preprocessing (JVM via `PixelArrayFramePayload` + Bitmap compile checks)

- valid synthetic input accepted
- zero/negative dimensions rejected
- inconsistent payload and metadata rejected
- aspect ratio preserved for portrait and landscape
- output respects long-edge and pixel-count limits
- no unnecessary upscale
- 0/90/180/270 metadata produces expected dimensions and pixels
- crop dimensions are correct and output aspect matches crop
- failure is returned honestly, without partial success
- duration is recorded and non-negative
- transform metadata matches output
- identity path does not create an unnecessary copy

### Annotation and profiles (JVM)

- add/update/delete/select
- stable IDs across edits
- duplicate type+team+index rejected
- bounds enforced for move/resize/create
- overlap validation
- JSON round-trip
- unsupported schema version rejected
- corrupt DataStore payload reported, not silently reset
- aspect-ratio/orientation compatibility accepts and rejects correctly

### Capture regression

- `CaptureStateHolderTest` fixtures now use `PixelArrayFramePayload`; null-bitmap tests are removed rather than preserved by special cases.
- Existing capture lifecycle and sampler tests remain green.
- Debug Capture remains available in the tab UI; no new capture permission flow.

### CI

CI continues to run:

```sh
./gradlew test --stacktrace
./gradlew assembleAndroidTest --stacktrace
./gradlew lint --stacktrace
./gradlew assembleDebug --stacktrace
```

The APK artifact remains `draftlens-debug`. Instrumentation tests are not claimed as executed.

## Documentation deliverables

- `docs/VISION_ARCHITECTURE.md` — package boundaries, data flow, ownership, processing rules
- `docs/VISION_FRAME_CONTRACT.md` — contract, payload ownership, original/processed/slot images
- `docs/VISION_COORDINATES.md` — normalized coordinates, rotation tables, crop semantics, transforms
- `docs/VISION_LAB.md` — user-facing controls, freeze behavior, slot editing, profile rules
- `docs/VISION_TESTING.md` — test inventory, synthetic fixture policy, CI gates, physical-device limits
- `docs/ARCHITECTURE.md` — updated package map and Vision data flow
- `README.md` — Sprint 1A status and Vision Lab usage
- `tasks/plan.md` and `tasks/todo.md` — Sprint 1A checklist and verification state

No real game screenshots are committed. Physical-device verification remains explicitly pending unless actually run.

## Privacy and performance

- Frames remain in memory; no automatic screenshot file, upload, or network client.
- No game-process access, packet inspection, taps, overlays outside the app, or hidden capture.
- Existing consent flow remains mandatory; session-scoped and per-session.
- Profiles persist only normalized rectangles and labels.
- No image is persisted for diagnostics without an explicit opt-in and review.
- Canvas overlay draws only current composition; it does not alter source pixels.
- Processing is explicit, background, latest/frozen only, and at most one job.
- Docs record measured synthetic processing times if obtained; no claim of 24/7 readiness or strict memory bounds.

## Decided product and builder calls

1. `TabRow` is used for the two current destinations instead of Navigation Compose. Add navigation only when a third top-level destination or independent back stack is required.
2. The calibration DataStore uses one serialized envelope to keep updates atomic and schema rejection all-or-nothing.
3. Aspect compatibility tolerance is 1%; it is displayed to the user and can be tightened later without schema change.
4. `SQUARE` is classified separately rather than squeezed into portrait or landscape.
5. The default processed long edge is 1280 px. It is configurable in advanced Vision Lab controls and recorded in test fixtures.
6. No automatic application of profiles, ever; compatibility failure blocks slot replacement.
7. Capture wires display rotation into frame metadata. MediaProjection buffers are already display-oriented, so production `rotation` correction is `ROTATION_0`; `displayRotation` records device orientation without double-rotating pixels.
8. The annotation canvas draws the oriented, uncropped frame. Crop belongs to processing/inspection and is not the annotation viewport.
9. Profile aspect ratio and expected orientation are derived from oriented dimensions, before crop and scale.
10. `SlotImage` is part of the production contract but has no UI or crop-export flow in this sprint. A `vision/processing` package is not created unless the processor needs it.
11. A DataStore document newer than supported schema is rejected. `update` and `save` are refused, deletion remains available, and there is no export action in this sprint.

## Completion criteria

Sprint 1A is complete when all of the following are true:

- Vision code consumes real non-null payloads, not metadata placeholders.
- Capture still starts, stops, pauses, resumes, and displays frames from Debug Capture.
- Geometry and preprocessing JVM suites pass locally and in CI.
- Vision Lab can receive live/frozen frames and draw/select/move/resize/delete manual slots.
- Overlay remains aligned with preview aspect ratio for portrait and landscape images.
- Profiles save/load/reject incompatible inputs without screenshot persistence.
- CI is green and produces a debug APK artifact.
- Docs state exactly what ran, what did not, and what is not implemented.
