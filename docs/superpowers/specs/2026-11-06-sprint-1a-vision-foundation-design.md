# Sprint 1A vision foundation design (archived)

**Status:** implemented and merged as PR #2. Current contracts are documented in
[Vision frame contract](../../VISION_FRAME_CONTRACT.md),
[Vision coordinates](../../VISION_COORDINATES.md) and
[Vision architecture](../../VISION_ARCHITECTURE.md).

## Intent

Make captured frames usable by independent vision components without claiming automatic
detection. Deliver an image-bearing frame contract, rotation-aware preprocessing, tested
normalized-coordinate geometry, manual slot annotation, a Vision Lab and versioned calibration
profiles. Explicitly out of scope: OCR, recognition, draft detection, ML models, overlays and
network behaviour. Manual annotations are calibration data, never detections.

## Key decisions

- **Single `:app` module** with pure Kotlin packages for model, geometry, preprocessing,
  annotation and calibration. Android Bitmap and Compose live at the edges.
- **Non-null payloads** at every frame boundary; the nullable capture bitmap is gone.
- **Two rotations** — `displayRotation` is a record, `rotation` is a correction applied by the
  transform. MediaProjection buffers are already display-oriented, so production sets the
  correction to `ROTATION_0`.
- **Validate before render.** Preprocessing returns failure codes for invalid dimensions,
  payload mismatch, bad crops and oversized output instead of throwing into the UI.
- **Strict codecs.** Unknown fields and unsupported schema versions are refused, never silently
  converted to empty data. A delete-all action is the escape hatch for unreadable documents.
- **Latest-frame-only publication.** Stale frames are worthless for draft inspection; bitmap
  references are cleared on stop and reset rather than recycled while Compose may draw them.
- **Narrow seam.** Vision consumes `VisionFrameSource` only.

## Consequences

Pure logic is JVM-testable without an emulator, and the capture and vision layers can evolve
independently. Hardware behaviour of the capture path remains a separate verification concern.
