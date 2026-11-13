# Vision Frame Contract

## Payload

Every frame boundary carries a non-null payload. The nullable `CapturedFrame.bitmap` of
Sprint 0 is gone.

```kotlin
sealed interface FramePayload {
    val width: Int
    val height: Int
    val format: PixelFormat
    val byteCountEstimate: Long
}
```

Two implementations exist:

- `PixelArrayFramePayload(width, height, pixels)` — pure Kotlin, for JVM tests and synthetic
  fixtures. It copies the input array in `init` and returns a copy from `pixels()`, so the
  caller cannot mutate a frame after the fact. `byteCountEstimate` assumes 4 bytes per pixel
  (ARGB_8888). It rejects empty or mismatched input.
- `BitmapFramePayload(bitmap, ownership = SHARED_READ_ONLY)` — the production path. It reads
  width, height and `allocationByteCount` straight from the bitmap and reports zero once the
  bitmap has been recycled, so a recycled bitmap never looks like a valid frame.

## Frames

```kotlin
interface VisionFrame

data class OriginalFrame(
    val id: FrameId,
    val payload: FramePayload,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val displayRotation: FrameRotation,
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

`SlotImage` is part of the contract but has no UI or crop-export flow in this sprint.

## Two rotations, two meanings

- `displayRotation` is the device display rotation recorded at acquisition. It is
  **information**, not a transform.
- `rotation` is the pixel-orientation correction needed to make the pixels upright.

MediaProjection delivers buffers that are already display-oriented, so production frames
carry `rotation = ROTATION_0` and only record `displayRotation`. Rotating an upright capture
is never implicit. Synthetic fixtures and any future authorized import may set a non-zero
`rotation`, and the geometry and preprocessing code apply that correction normally.

## Capture integration

`CapturedFrame` is the capture-layer model. It embeds an `OriginalFrame`, forwards the
acquisition metadata cheaply (`width`, `height`, `displayRotation`, `wallClockTimestampMs`,
`sampledAtElapsedRealtimeMs`) and exposes `toVisionFrame(): OriginalFrame`.

`FramePipeline.start(width, height, densityDpi, displayRotation)` passes the rotation into
every frame it produces. `CaptureSessionService` reads the rotation of
`DisplayManager.DEFAULT_DISPLAY` at capture start and whenever the display changes, and maps
`Surface.ROTATION_*` to `FrameRotation`; if the display is unavailable it falls back to
`ROTATION_0` and logs.

The rotation is tracked **independently of the capture size**. A device can rotate while
the captured content keeps its dimensions, so:

- `CaptureSessionService` watches the default display with a `DisplayManager.DisplayListener`
  and updates the rotation on every display change, whether or not MediaProjection reports a
  resize. The listener is unregistered during session cleanup.
- `FramePipeline` records the current rotation in a field that the image listener reads when
  a frame is emitted, so a frame never carries a rotation that went stale at start-up.
- A change in capture size still replaces the ImageReader and the VirtualDisplay; a change
  in rotation alone does not.

## Timing

Two clocks, kept apart on purpose:

- `capturedAtElapsedRealtimeMs` — monotonic `SystemClock.elapsedRealtime()`. Used for
  sampling intervals and latency arithmetic, immune to wall-clock changes.
- `wallClockTimestampMs` — `System.currentTimeMillis()`. Used only for display and for
  calibration profile timestamps.

`captureLatencyMs` and `processingLatencyMs` are acquisition-to-payload costs. They are not
detection latency, because there is no detection.

## Ownership rules

1. The capture pipeline owns the bitmap it produced.
2. `CaptureStateHolder` keeps only the latest frame; older frames become unreferenced.
3. No code path calls `recycle()`. References are dropped and native memory is reclaimed by
   the garbage collector.
4. `SHARED_READ_ONLY` consumers may inspect and draw a bitmap but never mutate or recycle it.
5. The Vision Lab pins one extra reference while a frame is frozen, and *Clear* releases it.
6. Frames are never written to disk and never uploaded. There is no network client in the app.
