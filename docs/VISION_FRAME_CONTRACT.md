# Vision frame contract

The contract in `com.draftlens.vision.model` is the only way Vision receives pixels. Capture
and Vision meet at `VisionFrameSource`, which now also carries the freeze commands.

## Payload

Every frame boundary carries a non-null payload.

```kotlin
interface FramePayload {
    val width: Int
    val height: Int
    val format: PixelFormat
    val byteCountEstimate: Long
}
```

- `PixelArrayFramePayload` is pure Kotlin for JVM tests and synthetic fixtures. It copies the
  input array on construction and returns a copy from `pixels()`, so a frame cannot be mutated
  after the fact. It rejects empty or mismatched input.
- `BitmapFramePayload` is the production path. It reads size and allocation from the bitmap
  and reports zero once the bitmap has been recycled, so a recycled bitmap never looks like a
  valid frame.

## Frames

- `OriginalFrame` — captured pixels plus id, source size, `displayRotation`, pixel-correction
  `rotation`, monotonic and wall-clock timestamps and acquisition latency.
- `ProcessedFrame` — an original plus a `FrameTransform`, the processed payload and the
  processing duration. It keeps the source id so the result can be traced back.
- `SlotImage` — one extracted slot crop, with its own id, source id, slot id, payload and
  `sourceRect`. It is the input to identification and is produced by `SlotImageExtractor`.

## Two rotations

`displayRotation` records the device rotation at acquisition; it is information only.
`rotation` is the correction applied to make pixels upright. MediaProjection buffers are
already display-oriented, so production frames carry `rotation = ROTATION_0`. Non-zero
corrections are used by synthetic fixtures and imports that genuinely need straightening.

The service reads the default display rotation at session start and on display changes,
maps `Surface.ROTATION_*` to `FrameRotation`, and falls back to `ROTATION_0` with a log entry
if the display is unavailable. Rotation is tracked independently of capture size: a rotation
with unchanged dimensions updates metadata only, while a size change replaces the ImageReader
and VirtualDisplay.

## Timing

Two clocks are kept apart:

- `capturedAtElapsedRealtimeMs` — monotonic elapsed realtime, used for sampling and latency.
- `wallClockTimestampMs` — wall clock, used for display and profile timestamps.

`captureLatencyMs` and `processingDurationMs` describe acquisition and preprocessing, **not**
identification. There is no calibrated confidence value anywhere in the contract.

## Freeze in the seam

```kotlin
interface VisionFrameSource {
    val frames: StateFlow<OriginalFrame?>
    val state: StateFlow<VisionSourceState>
    val frozenFrame: StateFlow<OriginalFrame?>
    fun freeze(): Boolean
    fun resume()
}
```

`frozenFrame` is an independently owned snapshot published by capture. `freeze()` pins the
newest valid frame and stops publication of live frames; it returns false when there is no
frame or no session. `resume()` releases the pin and restores live publication. Neither call
creates a second projection or display.

## Ownership

- The pipeline owns the bitmap it created and keeps only the latest frame; older references
  become garbage.
- The Vision Lab keeps one extra reference while a frame is pinned; Clear releases it.
- Frame payloads and slot crops are shared read-only. Consumers may inspect and draw them but
  must not mutate them.
- Short-lived temporaries created during slot extraction and reference-image validation are
  recycled exactly once by their owners after their pixels have been copied.
- Captured pixels and slot crops are never written to disk and never uploaded. The app's only
  network client downloads public static hero catalog data and reference images.
