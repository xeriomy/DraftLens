# Sprint 1A implementation plan (archived)

**Status:** completed and merged as PR #2. Kept as a record; current behaviour is documented
in [Architecture](../../ARCHITECTURE.md) and [Vision architecture](../../VISION_ARCHITECTURE.md).

## Goal

Give capture an image-bearing foundation without claiming automatic detection: non-null frame
contracts, tested preprocessing and geometry, manual draft-slot annotation, a Vision Lab and
versioned calibration profiles.

## Shape of the work

1. Pure Kotlin foundation — frame enums and ids, `FramePayload`, normalized and pixel value
   types, geometry with rotation round trips.
2. Capture adaptation — non-null captured frames carrying display rotation; a narrow
   `VisionFrameSource` seam so Vision never imports the capture service.
3. Preprocessing — a validator that returns failure codes instead of throwing, pure pixel-path
   math and an Android renderer using the same matrix elements.
4. Annotation — `DraftSlot`, a validating store with stable ids, overlap and duplicate checks,
   undo/redo, and a strict JSON codec that refuses unknown fields and unsupported schemas.
5. Calibration — versioned profiles with aspect-ratio tolerance, exact orientation match and
   a delete-all escape hatch for documents this build cannot read.
6. UI — Vision Lab with frame preview, freeze, explicit processing, annotation canvas, slot
   editor and profile controls.
7. Documentation and CI — package docs, test inventory, unit tests, strict lint and an APK
   artifact.

## Decisions retained

Single `:app` module; pure logic in JVM-testable packages; latest-frame-only publication; no
bitmap recycling while Compose may render; metadata-only persistence with no pixels or
screenshots. These decisions still hold and are described in ADR-001 and the current docs.
