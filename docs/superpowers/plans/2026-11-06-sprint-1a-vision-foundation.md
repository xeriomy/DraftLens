# Sprint 1A Vision Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the image-bearing foundation for DraftLens vision work: non-null frame contracts, tested preprocessing and geometry, manual draft-slot annotation, Vision Lab, and versioned calibration profiles.

**Architecture:** Single module `:app`, layered into pure Kotlin `vision/model`, `vision/geometry`, `preprocessing`, `annotation`, and `calibration`, with Android Bitmap and Compose at the edges. Capture exposes frames through a narrow `VisionFrameSource`; Vision never imports `CaptureSessionService` or `CaptureStateHolder`.

**Tech Stack:** Kotlin 2.0.20, Android SDK 35, Compose Material 3, Coroutines, DataStore Preferences, `kotlinx-serialization-json`.

**Spec:** `docs/superpowers/specs/2026-11-06-sprint-1a-vision-foundation-design.md`

## Execution Checkpoint (2026-11-06)

Branch: `sprint-1-vision-foundation`, based on `origin/main` at `ae92eaf`.

| Task | Status | Commits | Review |
|------|--------|---------|--------|
| 1. Vision model and geometry value types | Done | `a2731d3` | Clean |
| 2. Geometry subsystem | Done | `357a40f` | Clean |
| 3. Capture non-null frames | Done | `8e1381a` | Clean |
| 4. Preprocessing engine | Done | `79bd211`, `41aa126`, `b81e740` | Clean after 2 fix rounds |
| 5. Slot annotation store and codec | Done | `1086b75`, `cab3d8d` | Clean after 1 fix round |
| 6. Calibration profiles | **In progress** | `08ca414` (WIP, unreviewed) | Not started |
| 7. Vision frame source and ViewModel | Not started | — | — |
| 8. Compose Vision Lab | Not started | — | — |
| 9. Documentation | Not started | — | — |
| 10. CI, PR, APK, report | Not started | — | — |

Last verified state: `cab3d8d`, full JVM unit suite 127/127 passing (local aarch64 host).
Task 6 work exists but was not compiled or tested before the local Android SDK was cleared
from `/tmp/opencode/android-sdk`; treat `08ca414` as unverified.

Resume point:

1. Review the Task 6 WIP code against Task 6 of this plan and the design spec. The accepted
   `CalibrationLoadResult` shape is `Success(profiles)`, `UnsupportedSchema(message)`,
   `Corrupt(message)`.
2. Run `./gradlew :app:testDebugUnitTest --tests 'com.draftlens.calibration.*'` and the full
   `./gradlew test` once a working Android SDK is available; fix and commit Task 6.
3. Continue with Tasks 7–10. Push the branch and let CI be the build of record for lint,
   `assembleAndroidTest`, and `assembleDebug`.

## Global Constraints

- Single Gradle module `:app`; logical packages only; no empty packages or new modules.
- Kotlin 2.0.20 · AGP 8.6.1 · Gradle 8.7 · compileSdk/targetSdk 35 · minSdk 26 · JDK 17.
- Vision must not depend directly on `CaptureSessionService` or `CaptureStateHolder`; the capture adapter lives in `capture/` and implements the Vision source seam.
- No ML model, OCR, hero recognition, automatic draft/ban/pick detection, or fabricated confidence/detection data.
- No network upload, no screenshot persistence, no game-process access, no automated interaction.
- Existing capture consent remains mandatory and unchanged.
- No `FLAG_SECURE`.
- No real game screenshots or captures in fixtures; use synthetic images.
- CI is the build of record; final verification runs `test`, `assembleAndroidTest`, `lint`, and `assembleDebug`.

## Review Focus

1. MediaProjection buffers are already display-oriented; a non-zero `displayRotation` must not rotate ordinary captures. Task 3 pins production wiring (`ROTATION_0` correction); Task 4 pins synthetic non-zero correction.
2. Cropped, rotated, scaled inverse mappings drift. Task 2 pins `FrameTransform` round trips for 90/180/270 with crop and downscale.
3. A process request can target a frame that is superseded before the worker finishes. Task 7 pins frozen-selection and stale-result discard.
4. A newer/corrupt DataStore profile document must not be silently replaced or exported. Task 6 pins unsupported-schema refusal and delete-as-escape-hatch behavior.
5. Manual rectangles can exceed bounds or duplicate indexes and appear valid. Task 5 pins invalid rect, duplicate `(type, team, index)`, and overlap validation.

---

## File Structure

### Create

- `app/src/main/kotlin/com/draftlens/vision/model/FrameId.kt` — value class and `FrameId.random()`.
- `app/src/main/kotlin/com/draftlens/vision/model/FrameEnums.kt` — `PixelFormat`, `FrameRotation`, `OrientationCategory`.
- `app/src/main/kotlin/com/draftlens/vision/model/FramePayload.kt` — payload contract and `PixelArrayFramePayload`.
- `app/src/main/kotlin/com/draftlens/vision/model/VisionFrame.kt` — `OriginalFrame`, `ProcessedFrame`, `SlotImage`, marker interface.
- `app/src/main/kotlin/com/draftlens/vision/model/VisionFrameSource.kt` — source seam and `VisionSourceState`.
- `app/src/main/kotlin/com/draftlens/vision/model/android/BitmapFramePayload.kt` — Bitmap payload and ownership marker.
- `app/src/main/kotlin/com/draftlens/vision/geometry/NormalizedPoint.kt`
- `app/src/main/kotlin/com/draftlens/vision/geometry/NormalizedRect.kt`
- `app/src/main/kotlin/com/draftlens/vision/geometry/PixelPoint.kt`
- `app/src/main/kotlin/com/draftlens/vision/geometry/PixelRect.kt`
- `app/src/main/kotlin/com/draftlens/vision/geometry/FrameTransform.kt`
- `app/src/main/kotlin/com/draftlens/vision/preprocessing/PreprocessTypes.kt`
- `app/src/main/kotlin/com/draftlens/vision/preprocessing/PreprocessingMath.kt`
- `app/src/main/kotlin/com/draftlens/vision/preprocessing/PixelArrayFramePreprocessor.kt`
- `app/src/main/kotlin/com/draftlens/vision/preprocessing/AndroidFramePreprocessor.kt`
- `app/src/main/kotlin/com/draftlens/vision/annotation/DraftSlot.kt`
- `app/src/main/kotlin/com/draftlens/vision/annotation/SlotAnnotationStore.kt`
- `app/src/main/kotlin/com/draftlens/vision/annotation/SlotAnnotationCodec.kt`
- `app/src/main/kotlin/com/draftlens/vision/calibration/CalibrationProfile.kt`
- `app/src/main/kotlin/com/draftlens/vision/calibration/CalibrationCompatibility.kt`
- `app/src/main/kotlin/com/draftlens/vision/calibration/CalibrationProfileCodec.kt`
- `app/src/main/kotlin/com/draftlens/vision/calibration/CalibrationRepository.kt` — interface consumed by ViewModel.
- `app/src/main/kotlin/com/draftlens/datastore/CalibrationProfileDataStore.kt` — Android/DataStore implementation.
- `app/src/main/kotlin/com/draftlens/capture/CaptureVisionFrameSource.kt` — capture-to-Vision adapter.
- `app/src/main/kotlin/com/draftlens/feature/visionlab/VisionLabViewModel.kt`
- `app/src/main/kotlin/com/draftlens/feature/visionlab/VisionLabUiState.kt`
- `app/src/main/kotlin/com/draftlens/feature/visionlab/VisionLabScreen.kt`
- `app/src/main/kotlin/com/draftlens/feature/visionlab/AnnotationCanvas.kt`
- `app/src/main/kotlin/com/draftlens/feature/visionlab/SlotEditor.kt`
- `app/src/main/kotlin/com/draftlens/feature/visionlab/ProfileControls.kt`

### Modify

- `app/build.gradle.kts` — add `kotlinx-serialization-json` and the Kotlin serialization plugin.
- `gradle/libs.versions.toml` — pin `kotlinx-serialization-json` and plugin.
- `app/src/main/kotlin/com/draftlens/models/CapturedFrame.kt` — non-null frame payload/vision frame; no `bitmap: Bitmap?`.
- `app/src/main/kotlin/com/draftlens/capture/FramePipeline.kt` — return/publish non-null `CapturedFrame`; pass capture rotation.
- `app/src/main/kotlin/com/draftlens/capture/CaptureSessionService.kt` — read and pass display rotation on start/resize; preserve capture lifecycle.
- `app/src/main/kotlin/com/draftlens/capture/CaptureStateHolder.kt` — record/clear new `CapturedFrame` shape.
- `app/src/main/kotlin/com/draftlens/feature/home/DebugCaptureScreen.kt` — read bitmap through `BitmapFramePayload` only.
- `app/src/main/kotlin/com/draftlens/MainActivity.kt` — Material 3 `TabRow` with Debug Capture and Vision Lab.
- `app/src/main/kotlin/com/draftlens/DraftLensApp.kt` — expose calibration DataStore repository.
- `README.md`, `docs/ARCHITECTURE.md`, `docs/VISION_*.md`, `tasks/plan.md`, `tasks/todo.md` — status and recorded verification.

### Test

- `app/src/test/kotlin/com/draftlens/vision/model/VisionFrameTest.kt`
- `app/src/test/kotlin/com/draftlens/vision/geometry/GeometryTest.kt`
- `app/src/test/kotlin/com/draftlens/preprocessing/PreprocessingMathTest.kt`
- `app/src/test/kotlin/com/draftlens/preprocessing/PixelArrayFramePreprocessorTest.kt`
- `app/src/test/kotlin/com/draftlens/annotation/SlotAnnotationStoreTest.kt`
- `app/src/test/kotlin/com/draftlens/annotation/SlotAnnotationCodecTest.kt`
- `app/src/test/kotlin/com/draftlens/calibration/CalibrationCodecCompatibilityTest.kt`
- `app/src/test/kotlin/com/draftlens/capture/CaptureVisionFrameSourceTest.kt`
- `app/src/test/kotlin/com/draftlens/feature/visionlab/VisionLabViewModelTest.kt`
- Existing `CaptureStateHolderTest.kt`, fixtures updated for non-null frames.

---

### Task 1: Vision model and geometry value types

**Files:**
- Create: `app/src/main/kotlin/com/draftlens/vision/model/FrameId.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/model/FrameEnums.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/model/FramePayload.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/model/VisionFrame.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/model/android/BitmapFramePayload.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/geometry/NormalizedPoint.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/geometry/NormalizedRect.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/geometry/PixelPoint.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/geometry/PixelRect.kt`
- Test: `app/src/test/kotlin/com/draftlens/vision/model/VisionFrameTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `FrameId`, `FrameRotation`, `OrientationCategory`, `PixelFormat`, `FramePayload`, `PixelArrayFramePayload`, `BitmapFramePayload`, `OriginalFrame`, `ProcessedFrame`, `SlotImage`, `NormalizedPoint`, `NormalizedRect`, `PixelPoint`, `PixelRect`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `pixel array payload copies input and estimates bytes`() {
    val source = intArrayOf(1, 2, 3, 4)
    val payload = PixelArrayFramePayload(width = 2, height = 2, pixels = source)
    source[0] = 99
    assertEquals(1, payload.pixels()[0])
    assertEquals(16L, payload.byteCountEstimate)
}

@Test fun `pixel array payload rejects empty or mismatched input`() {
    assertThrows(IllegalArgumentException::class.java) {
        PixelArrayFramePayload(width = 0, height = 1, pixels = IntArray(0))
    }
    assertThrows(IllegalArgumentException::class.java) {
        PixelArrayFramePayload(width = 2, height = 2, pixels = IntArray(3))
    }
}

@Test fun `orientation category is explicit`() {
    assertEquals(OrientationCategory.PORTRAIT, OrientationCategory.from(720, 1280))
    assertEquals(OrientationCategory.LANDSCAPE, OrientationCategory.from(1280, 720))
    assertEquals(OrientationCategory.SQUARE, OrientationCategory.from(100, 100))
}

@Test fun `invalid normalized and pixel rectangles throw`() {
    assertThrows(IllegalArgumentException::class.java) { NormalizedRect(0.5f, 0f, 0.2f, 1f) }
    assertThrows(IllegalArgumentException::class.java) { PixelRect(0, 0, 0, 10) }
}
```

- [ ] **Step 2: Run failing test**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.draftlens.vision.model.VisionFrameTest' --stacktrace`
Expected: FAIL because model classes do not exist.

- [ ] **Step 3: Implement contract**

`FrameId` is `public value class FrameId(val value: String)` with `fun random()`. `PixelArrayFramePayload` defensively copies `pixels` in `init`, exposes `pixels(): IntArray` returning a copy, and uses `ARGB_8888 = 4 bytes/pixel` for `byteCountEstimate`. `BitmapFramePayload(width/height/format/byteCountEstimate)` reads directly from non-null `bitmap`; `bitmap.isRecycled` returns zero/invalid rather than a frame. `VisionFrame` is a marker interface. Data classes carry the exact fields from the spec: `displayRotation` and `rotation` are separate `FrameRotation` values; `rotation` is the pixel-orientation correction only. `SlotImage` carries `slotId`, `sourceRect: PixelRect`, and creates no pixels in this task.

`NormalizedRect` validates finite non-NaN coordinates, `left < right`, `top < bottom`, and `[0,1]` bounds. `PixelRect` uses exclusive edges and validates non-negative dimensions.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.draftlens.vision.model.VisionFrameTest' --stacktrace`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/draftlens/vision app/src/test/kotlin/com/draftlens/vision/model/VisionFrameTest.kt
git commit -m "feat: add non-null vision frame contract"
```

---

### Task 2: Geometry subsystem with rotation and crop round trips

**Files:**
- Create: `app/src/main/kotlin/com/draftlens/vision/geometry/FrameTransform.kt`
- Test: `app/src/test/kotlin/com/draftlens/vision/geometry/GeometryTest.kt`

**Interfaces:**
- Consumes: `FrameRotation` from Task 1.
- Produces: `NormalizedPoint`, `NormalizedRect`, `PixelPoint`, `PixelRect`, `FrameTransform`, `FrameTransform.create(...)`, coordinate round-trip methods.

- [ ] **Step 1: Write the failing geometry tests**

Create tests with these exact cases:

```kotlin
@Test fun `normalized rect rejects reversed or out of range bounds`() {
    assertThrows(IllegalArgumentException::class.java) { NormalizedRect(0.5f, 0f, 0.2f, 1f) }
    assertThrows(IllegalArgumentException::class.java) { NormalizedRect(-0.1f, 0f, 0.5f, 1f) }
    assertThrows(IllegalArgumentException::class.java) { NormalizedRect(0f, 0f, 1.1f, 1f) }
}

@Test fun `all rotations round trip source points`() {
    for (rotation in FrameRotation.entries) {
        val swapsAxes = rotation == FrameRotation.ROTATION_90 || rotation == FrameRotation.ROTATION_270
        val transform = FrameTransform.create(
            sourceWidth = 4, sourceHeight = 2,
            rotation = rotation, crop = null,
            processedWidth = if (swapsAxes) 2 else 4,
            processedHeight = if (swapsAxes) 4 else 2,
        )
        val source = FloatPoint(1.25f, 0.75f)
        assertNear(source, transform.processedToSource(transform.sourceToProcessed(source)), 0.01f)
    }
}

@Test fun `cropped downscaled 90 degree transform round trips`() {
    val crop = NormalizedRect(0.1f, 0.2f, 0.9f, 0.8f)
    val transform = FrameTransform.create(
        sourceWidth = 1000, sourceHeight = 500,
        rotation = FrameRotation.ROTATION_90,
        crop = crop, processedWidth = 160, processedHeight = 240,
    )
    val point = FloatPoint(123f, 210f)
    assertNear(point, transform.processedToSource(transform.sourceToProcessed(point)), 1.0f)
}
```

Also cover portrait/landscape, crop 100% identity, rectangle mapping, NaN/infinity, invalid dimensions, crop outside range, and aspect mismatch.

- [ ] **Step 2: Run failing tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.draftlens.vision.geometry.GeometryTest' --stacktrace`
Expected: FAIL because geometry classes do not exist.

- [ ] **Step 3: Implement types**

`FrameTransform` stores source dimensions, pixel-orientation correction, oriented crop rect, oriented dimensions, crop-pixel rectangle, scale, and processed dimensions.

`FrameTransform.create` validates positive dimensions, non-empty crop in oriented pixels with at least 1 px per edge, and output aspect ratio equal to the oriented crop aspect within one pixel of width or height. `sourceToProcessed` and `processedToSource` are continuous float mappings; integer `PixelRect` is used only for public pixel conversion. Rotation maps edges like the spec: `ROTATION_90` outputs `H × W`, `ROTATION_270` outputs `H × W`, and `ROTATION_180` preserves `W × H`.

Add public helpers:

```kotlin
fun NormalizedRect.toPixelRect(width: Int, height: Int): PixelRect
fun PixelRect.toNormalized(width: Int, height: Int): NormalizedRect
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.draftlens.vision.geometry.GeometryTest' --stacktrace`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/draftlens/vision/geometry app/src/test/kotlin/com/draftlens/vision/geometry
git commit -m "feat: add rotation-aware vision geometry"
```

---

### Task 3: Capture carries non-null frames and display rotation

**Files:**
- Modify: `app/src/main/kotlin/com/draftlens/models/CapturedFrame.kt`
- Modify: `app/src/main/kotlin/com/draftlens/capture/FramePipeline.kt`
- Modify: `app/src/main/kotlin/com/draftlens/capture/CaptureSessionService.kt`
- Modify: `app/src/main/kotlin/com/draftlens/capture/CaptureStateHolder.kt`
- Modify: `app/src/main/kotlin/com/draftlens/feature/home/DebugCaptureScreen.kt`
- Modify: `app/src/test/kotlin/com/draftlens/capture/CaptureStateHolderTest.kt`
- Create: `app/src/test/kotlin/com/draftlens/vision/TestFrames.kt`

**Interfaces:**
- Consumes: model contract from Task 1.
- Produces: `CapturedFrame.toVisionFrame()`, `CaptureStateHolder.latestFrame: StateFlow<CapturedFrame?>` with no nullable pixels; production `displayRotation` on the frame.

- [ ] **Step 1: Update capture tests first**

Replace `capturedFrameNullBitmap()` with a shared synthetic fixture:

```kotlin
fun testCapturedFrame(): CapturedFrame = CapturedFrame(
    frame = OriginalFrame(
        id = FrameId("test-frame"),
        payload = PixelArrayFramePayload(2, 2, intArrayOf(1, 2, 3, 4)),
        sourceWidth = 2,
        sourceHeight = 2,
        displayRotation = FrameRotation.ROTATION_90,
        rotation = FrameRotation.ROTATION_0,
        capturedAtElapsedRealtimeMs = 10_000,
        wallClockTimestampMs = 1_700_000_000_000,
        captureLatencyMs = 4,
    ),
    processingLatencyMs = 12,
)
```

Update fixture assertions to use `CapturedFrame.width`, `.height`, `.wallClockTimestampMs`, and `.processingLatencyMs` forwarding properties or call sites to `frame.frame.*`. Keep CaptureStateHolder behavior unchanged.

- [ ] **Step 2: Run capture tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.draftlens.capture.CaptureStateHolderTest' --stacktrace`
Expected: FAIL because `CapturedFrame` still exposes `bitmap`.

- [ ] **Step 3: Implement non-null capture frame**

`CapturedFrame` contains `frame: OriginalFrame`, exposes `width`, `height`, `displayRotation`, `wallClockTimestampMs`, `sampledAtElapsedRealtimeMs`, and `processingLatencyMs` via cheap forwards, and `fun toVisionFrame(): OriginalFrame = frame`. `FramePipeline.start(width, height, densityDpi, displayRotation: FrameRotation = FrameRotation.ROTATION_0)` passes rotation into every `OriginalFrame`.

The pipeline’s `imageToBitmap` becomes `imageToPayload`: return a non-null `BitmapFramePayload` or drop the frame and log a warning. No nullable payload crosses into `CapturedFrame`. Stop paths still clear references without calling `recycle()`.

`CaptureSessionService` reads `DisplayManager.DEFAULT_DISPLAY` rotation at start and display recreation. Map `Surface.ROTATION_0/90/180/270` to `FrameRotation`; if unavailable, use `ROTATION_0` and log. Pass `displayRotation` into `FramePipeline.start` and `prepareResize`. MediaProjection buffers are display-oriented, so pipeline sets `rotation = ROTATION_0` always; `displayRotation` records the device orientation without rotating the pixels.

`DebugCaptureScreen` accepts only `BitmapFramePayload` for preview; synthetic/test payloads show the existing empty-state message. Remove `bitmap != null && !bitmap.isRecycled` checks and invalid-bitmap special cases.

- [ ] **Step 4: Run capture tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.draftlens.capture.*' --stacktrace`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/draftlens/models/CapturedFrame.kt app/src/main/kotlin/com/draftlens/capture app/src/main/kotlin/com/draftlens/feature/home/DebugCaptureScreen.kt app/src/test
git commit -m "refactor: carry non-null vision frames from capture"
```

---

### Task 4: Preprocessing math and PixelArray renderer

**Files:**
- Create: `app/src/main/kotlin/com/draftlens/vision/preprocessing/PreprocessTypes.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/preprocessing/PreprocessingMath.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/preprocessing/PixelArrayFramePreprocessor.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/preprocessing/AndroidFramePreprocessor.kt`
- Test: `app/src/test/kotlin/com/draftlens/preprocessing/PreprocessingMathTest.kt`
- Test: `app/src/test/kotlin/com/draftlens/preprocessing/PixelArrayFramePreprocessorTest.kt`

**Interfaces:**
- Consumes: `OriginalFrame`, `PixelArrayFramePayload`, `FrameTransform`.
- Produces: `ProcessFrameRequest`, `ProcessFrameResult`, `PreprocessFailure`, `FramePreprocessor`, `AndroidFramePreprocessor`, `PixelArrayFramePreprocessor`.

- [ ] **Step 1: Write failing validation and math tests**

```kotlin
@Test fun `valid landscape frame is accepted and preserves aspect ratio`() {
    val frame = syntheticOriginal(width = 1920, height = 1080)
    val plan = PreprocessingMath.plan(frame, ProcessFrameRequest(maxProcessedDimension = 1280))
    assertTrue(plan.isSuccess)
    assertEquals(1280, plan.getOrThrow().outputWidth)
    assertEquals(720, plan.getOrThrow().outputHeight)
}

@Test fun `invalid input reports honest failure`() {
    val frame = syntheticOriginal(width = 0, height = 100)
    val result = AndroidFramePreprocessor.process(frame, ProcessFrameRequest())
    assertEquals(PreprocessFailureCode.INVALID_DIMENSIONS, (result as ProcessFrameResult.Failure).reason.code)
}

@Test fun `90 degree correction swaps output axes without stretching`() {
    val frame = syntheticOriginal(width = 1920, height = 1080, rotation = FrameRotation.ROTATION_90)
    val result = PixelArrayFramePreprocessor.process(frame, ProcessFrameRequest(maxProcessedDimension = 500))
    val success = result as ProcessFrameResult.Success
    assertEquals(281, success.frame.payload.width)
    assertEquals(500, success.frame.payload.height)
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.draftlens.preprocessing.*' --stacktrace`
Expected: FAIL because preprocessing classes do not exist.

- [ ] **Step 3: Implement planning and rendering**

`ProcessFrameRequest` contains `maxProcessedDimension: Int = 1280`, `maxPixels: Long = 8_000_000`, and `crop: NormalizedRect? = null`. `PreprocessFailure(code, message)` codes are `INVALID_DIMENSIONS`, `PAYLOAD_MISMATCH`, `TOO_MANY_PIXELS`, `INVALID_CROP`, `OUTPUT_TOO_LARGE`, `UNSUPPORTED_FORMAT`, `RENDER_FAILED`. `FramePreprocessor` is a `fun interface` with `process(input: OriginalFrame, request: ProcessFrameRequest): ProcessFrameResult`.

`PreprocessingMath.plan` validates source dimensions, payload consistency, `maxProcessedDimension`, `maxPixels`, oriented dimensions, crop in oriented pixels, output dimensions, aspect ratio preservation within one pixel, and output pixel budget. The plan stores source/oriented/crop/output dimensions, rotation, and scale.

`PixelArrayFramePreprocessor` performs rotation, crop, and downscale with nearest-neighbor sampling. It never upscales. If `rotation == ROTATION_0`, crop is null, and output equals source size, it returns the same `PixelArrayFramePayload` reference and an identity transform. For every other path it allocates exactly one output pixel array and returns a copied payload. Duration uses `System.nanoTime()`.

`AndroidFramePreprocessor` validates through the same math and, for `BitmapFramePayload`, draws into exactly one destination bitmap with a Canvas/Matrix. It catches rendering exceptions and returns `RENDER_FAILED`; it never publishes a partial frame. For non-Bitmap payloads in Android code it returns `UNSUPPORTED_FORMAT`; JVM tests use the PixelArray implementation.

- [ ] **Step 4: Run preprocessing tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.draftlens.preprocessing.*' --stacktrace`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/draftlens/vision/preprocessing app/src/test/kotlin/com/draftlens/preprocessing
git commit -m "feat: add validated frame preprocessing engine"
```

---

### Task 5: Manual slot annotation store and JSON codec

**Files:**
- Create: `app/src/main/kotlin/com/draftlens/vision/annotation/DraftSlot.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/annotation/SlotAnnotationStore.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/annotation/SlotAnnotationCodec.kt`
- Modify: `app/src/main/kotlin/com/draftlens/vision/geometry/NormalizedRect.kt`
- Modify: `app/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Test: `app/src/test/kotlin/com/draftlens/annotation/SlotAnnotationStoreTest.kt`
- Test: `app/src/test/kotlin/com/draftlens/annotation/SlotAnnotationCodecTest.kt`

**Interfaces:**
- Consumes: `NormalizedRect`, `FrameRotation` not required.
- Produces: `SlotType`, `SlotTeam`, `DraftSlot`, `SlotAnnotationStore`, `SlotAnnotationCodec`, `SlotIssue`.

- [ ] **Step 1: Add failing annotation tests**

```kotlin
@Test fun `duplicate index is rejected within type team scope`() {
    val store = SlotAnnotationStore(idGenerator = sequenceOf("a", "b").iterator()::next)
    assertTrue(store.add(SlotType.BAN, SlotTeam.ALLY, rect(0.1f), index = 1).isSuccess)
    val duplicate = store.add(SlotType.BAN, SlotTeam.ALLY, rect(0.5f), index = 1)
    assertEquals(SlotIssueCode.DUPLICATE_INDEX, duplicate.exceptionOrNull()?.issueCode)
}

@Test fun `move clamps rectangle to normalized bounds`() {
    val store = SlotAnnotationStore(idGenerator = { "slot" })
    val slot = store.add(SlotType.PICK, SlotTeam.ENEMY, NormalizedRect(0.8f, 0.1f, 0.95f, 0.4f)).getOrThrow()
    store.move(slot.id, deltaX = 0.5f, deltaY = 0f)
    assertEquals(1.0f, store.slots().single().rect.right, 0.0001f)
}

@Test fun `codec round trip preserves ids and rect`() { ... }
@Test fun `codec rejects unsupported schema`() { ... }
```

Cover add/update/delete/reset, stable IDs, team/type update, duplicate index, overlap severity, bounds, serialization/deserialization, and unsupported versions.

- [ ] **Step 2: Run red tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.draftlens.annotation.*' --stacktrace`
Expected: FAIL because annotation classes do not exist.

- [ ] **Step 3: Implement annotation model**

`DraftSlot` is `@Serializable`, with `schemaVersion = 1`, `id: String`, `type: BAN|PICK`, `team: ALLY|ENEMY|GLOBAL`, `index: Int`, `rect: NormalizedRect`, and `label: String?`. Annotate `NormalizedRect` with `@Serializable`; enums are serialized as their names.

`SlotAnnotationStore` exposes immutable snapshots:

```kotlin
fun slots(): List<DraftSlot>
fun selectedSlotId(): String?
fun add(type, team, rect, index: Int? = null, label: String? = null): Result<DraftSlot>
fun move(id: String, deltaX: Float, deltaY: Float): Result<DraftSlot>
fun resize(id: String, rect: NormalizedRect): Result<DraftSlot>
fun updateTeam(id: String, team: SlotTeam): Result<DraftSlot>
fun updateType(id: String, type: SlotType): Result<DraftSlot>
fun updateIndex(id: String, index: Int): Result<DraftSlot>
fun updateLabel(id: String, label: String?): Result<DraftSlot>
fun select(id: String?)
fun delete(id: String): Boolean
fun reset()
fun validate(): List<SlotIssue>
```

Index uniqueness is `(type, team, index)`. Overlap across all rects is reported; same `(type, team)` overlap is `ERROR`, cross-scope overlap is `WARNING`. `resize` accepts a new valid `NormalizedRect`; Compose computes it from handles. Minimum rectangle size is `0.01` in both axes. IDs are injected and never reused.

`SlotAnnotationCodec` uses `kotlinx.serialization-json` with `ignoreUnknownKeys = false`; `schemaVersion != 1` throws `SlotSchemaException`. Decode validates rectangles and duplicate indexes; invalid JSON throws a codec exception with a user-readable message.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.draftlens.annotation.*' --stacktrace`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts gradle/libs.versions.toml app/src/main/kotlin/com/draftlens/vision/annotation app/src/test/kotlin/com/draftlens/annotation
git commit -m "feat: add manual draft slot annotations"
```

---

### Task 6: Versioned calibration profiles with rejection rules

**Files:**
- Create: `app/src/main/kotlin/com/draftlens/vision/calibration/CalibrationProfile.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/calibration/CalibrationCompatibility.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/calibration/CalibrationProfileCodec.kt`
- Create: `app/src/main/kotlin/com/draftlens/vision/calibration/CalibrationRepository.kt`
- Create: `app/src/main/kotlin/com/draftlens/datastore/CalibrationProfileDataStore.kt`
- Test: `app/src/test/kotlin/com/draftlens/calibration/CalibrationCodecCompatibilityTest.kt`

**Interfaces:**
- Consumes: `OrientationCategory`, slots from Task 5.
- Produces: `CalibrationProfile`, `CalibrationRepository`, `CalibrationLoadResult`, Android DataStore implementation.

- [ ] **Step 1: Write failing compatibility and codec tests**

```kotlin
@Test fun `equal ratio and orientation is compatible`() {
    val profile = profile(targetAspectRatio = 16.0 / 9.0, expectedOrientation = OrientationCategory.LANDSCAPE)
    assertEquals(
        CalibrationCompatibility.Compatible,
        CalibrationCompatibility.check(profile, orientedWidth = 2400, orientedHeight = 1080),
    )
}

@Test fun `ratio mismatch returns expected and actual values`() {
    val result = CalibrationCompatibility.check(profile(16.0 / 9.0, LANDSCAPE), 1080, 1920)
    assertTrue(result is CalibrationCompatibility.Incompatible)
}

@Test fun `future schema is rejected and delete remains possible`() {
    val document = """{"schemaVersion":2,"profiles":[]}"""
    assertThrows(UnsupportedCalibrationSchemaException::class.java) {
        CalibrationProfileCodec.decodeDocument(document)
    }
}
```

- [ ] **Step 2: Run failing tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.draftlens.calibration.*' --stacktrace`
Expected: FAIL because calibration classes do not exist.

- [ ] **Step 3: Implement model, codec, repository**

`CalibrationProfile` fields: `schemaVersion = 1`, `profileId`, `label?`, `targetAspectRatio: Double`, `expectedOrientation: OrientationCategory`, `createdAtMs`, `updatedAtMs`, `slots: List<DraftSlot>`. `CalibrationDocument` is `schemaVersion = 1` and `profiles: List<CalibrationProfile>`. Target ratio is always `orientedWidth / orientedHeight`, where oriented means after pixel-orientation correction and before crop or scale. Compatibility tolerance is 1% and orientation must match exactly; report expected vs actual values for UI.

`CalibrationRepository` interface:

```kotlin
val profiles: Flow<CalibrationLoadResult>
suspend fun upsert(profile: CalibrationProfile): Result<Unit>
suspend fun delete(profileId: String): Result<Unit>
suspend fun clearDocument(): Result<Unit>
```

`CalibrationProfileCodec` accepts only envelope/profile/slot schema versions equal to 1. Unknown future versions throw `UnsupportedCalibrationSchemaException`; malformed JSON throws `CalibrationDocumentException`; neither is converted to an empty list.

`CalibrationProfileDataStore` stores one JSON string under `profiles_json` in `draftlens_calibration`. Every mutating operation reads and decodes the current document first; on unsupported/corrupt data it returns failure without writing. `clearDocument()` is the only operation available for a too-new/corrupt document and is used by the "Delete all calibration data" action. It never writes screenshots, bitmaps, transform IDs, or pixels. Preferences backup exclusion already covers DataStore; no manifest permission is added.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.draftlens.calibration.*' --stacktrace`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/draftlens/vision/calibration app/src/main/kotlin/com/draftlens/datastore app/src/test/kotlin/com/draftlens/calibration
git commit -m "feat: add versioned calibration profiles"
```

---

### Task 7: Vision frame source and explicit processing state

**Files:**
- Create: `app/src/main/kotlin/com/draftlens/vision/model/VisionFrameSource.kt`
- Create: `app/src/main/kotlin/com/draftlens/capture/CaptureVisionFrameSource.kt`
- Create: `app/src/main/kotlin/com/draftlens/feature/visionlab/VisionLabUiState.kt`
- Create: `app/src/main/kotlin/com/draftlens/feature/visionlab/VisionLabViewModel.kt`
- Test: `app/src/test/kotlin/com/draftlens/capture/CaptureVisionFrameSourceTest.kt`
- Test: `app/src/test/kotlin/com/draftlens/feature/visionlab/VisionLabViewModelTest.kt`
- Modify: `app/src/main/kotlin/com/draftlens/DraftLensApp.kt`

**Interfaces:**
- Consumes: `CaptureStateHolder`, `CapturedFrame`, `OriginalFrame`, `FramePreprocessor`, `CalibrationRepository`, annotation store.
- Produces: `VisionSourceState`, `VisionFrameSource`, `VisionLabViewModel`, freeze/process/annotation actions.

- [ ] **Step 1: Write failing source and viewmodel tests**

```kotlin
@Test fun `adapter maps paused capture to paused vision state`() = runTest {
    val frames = MutableStateFlow(testCapturedFrame())
    val capture = MutableStateFlow(CaptureSessionState(status = CaptureStatus.PAUSED))
    val source = CaptureVisionFrameSource(frames, capture, backgroundScope)
    assertEquals(VisionSourceState.PAUSED, source.state.first())
    assertEquals("test-frame", source.frames.first()?.id?.value)
}

@Test fun `freeze pins current frame while live frames continue`() = runTest { ... }

@Test fun `stale processing result is discarded after a newer request`() = runTest {
    val preprocessor = BlockingFakePreprocessor()
    val vm = VisionLabViewModel(source, preprocessor, fakeCalibrationRepository())
    vm.processFrame()
    vm.processFrame()
    preprocessor.releaseFirst()
    assertEquals("second", vm.uiState.value.processed?.sourceId?.value)
}
```

- [ ] **Step 2: Run red tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.draftlens.capture.CaptureVisionFrameSourceTest' --tests 'com.draftlens.feature.visionlab.VisionLabViewModelTest' --stacktrace`
Expected: FAIL because seam and ViewModel do not exist.

- [ ] **Step 3: Implement source seam and viewmodel**

`CaptureVisionFrameSource` is the only code importing both capture and vision. It maps ` CapturedFrame.toVisionFrame()` and maps `CaptureStatus` to `VisionSourceState`: `CAPTURING`, `PAUSED`, `IDLE/STOPPING→STOPPED`, `ERROR→ERROR`, `AWAITING_PERMISSION/STARTING→UNKNOWN`. It accepts injected flows and scope for tests; production defaults read the singleton holder.

`VisionLabViewModel` holds:

- `liveFrame` and `sourceState` from source
- `frozenFrame: OriginalFrame?`
- `displayFrame = frozenFrame ?: liveFrame`
- `processedFrame: ProcessedFrame?`
- `processStatus`, `processError`, `processingGeneration: Long`
- `SlotAnnotationStore` state
- `CalibrationLoadResult` and profile error/success messages
- selected `maxProcessedDimension` (default 1280)

Freeze stores the displayed `OriginalFrame`; resume clears it; clear releases the reference. `processFrame()` selects frozen frame or latest live frame, captures it locally, and starts one job on `Dispatchers.Default`. A new request increments `processingGeneration` and cancels the prior job; only a result whose generation is still current is published. `onCleared` cancels the job. No automatic process call exists.

`DraftLensApp` exposes `calibrationProfiles: CalibrationRepository` lazily. The ViewModel does not instantiate Android APIs itself. Profile create/update/delete actions call the injected repository and refresh `profiles`; a newer-than-supported envelope leaves delete-all available and save/update disabled.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.draftlens.capture.CaptureVisionFrameSourceTest' --tests 'com.draftlens.feature.visionlab.VisionLabViewModelTest' --stacktrace`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/draftlens/vision/model/VisionFrameSource.kt app/src/main/kotlin/com/draftlens/capture/CaptureVisionFrameSource.kt app/src/main/kotlin/com/draftlens/feature/visionlab app/src/main/kotlin/com/draftlens/DraftLensApp.kt app/src/test
git commit -m "feat: wire explicit frame processing into vision lab state"
```

---

### Task 8: Compose Vision Lab with synchronized preview and overlays

**Files:**
- Modify: `app/src/main/kotlin/com/draftlens/MainActivity.kt`
- Create: `app/src/main/kotlin/com/draftlens/feature/visionlab/VisionLabScreen.kt`
- Create: `app/src/main/kotlin/com/draftlens/feature/visionlab/AnnotationCanvas.kt`
- Create: `app/src/main/kotlin/com/draftlens/feature/visionlab/SlotEditor.kt`
- Create: `app/src/main/kotlin/com/draftlens/feature/visionlab/ProfileControls.kt`
- Modify: `app/src/main/kotlin/com/draftlens/feature/home/DebugCaptureScreen.kt`

**Interfaces:**
- Consumes: `VisionLabViewModel` state and actions.
- Produces: user-visible tab UI, interactive canvas, profile controls.

- [ ] **Step 1: Capture the current screenshot or layout reference**

No product screenshot is committed. Use the existing debug UI and this plan/design as the visual source. Do not add copied game imagery. If a design mock is needed for local iteration, keep it under `/tmp/opencode` and out of git.

- [ ] **Step 2: Implement MainActivity tabs**

`MainActivity` owns `rememberSaveable { mutableIntStateOf(0) }` and a `TabRow` for Debug Capture (index 0) and Vision Lab (index 1). Each tab is a full-height screen. Capture remains alive regardless of selected tab. Use Material 3 color scheme already configured by MaterialTheme/Dark default; no new navigation dependency.

- [ ] **Step 3: Implement VisionLabScreen sections**

Build a scrollable column with these cards:

1. **Frame Preview** — image or empty state; original dimensions; display rotation; processing correction; processed dimensions; duration; source state; `LIVE`/`FROZEN`; Freeze/Resume; Process Frame; Clear.
2. **Annotation Canvas** — interactive card described below.
3. **Slot Inspector** — list with ID, type, team, index, coordinates, validation.
4. **Processing Inspector** — source/output dims, rotation, crop, scale, duration, validation errors, live/frozen status. Show “Detection: not implemented” text; no confidence or hero fields. Include a "Max processed dimension" dropdown/text setting (default 1280) so processing resolution is configurable.
5. **Profiles** — label text field, save, list, apply, update, delete, delete-all when schema is unsupported, and mismatch message with expected/actual ratio and orientation.

The preview image box uses the aspect ratio of the oriented frame and `ContentScale.Fit`. If only a `PixelArrayFramePayload` is present, show a neutral test-fixture placeholder instead of crashing; production emits Bitmap payloads.

- [ ] **Step 4: Implement annotation canvas**

`AnnotationCanvas` computes its fitted image rectangle from constraints and frame aspect ratio; all drawing and hit testing use that rectangle. It draws:

- bitmap with unmodified pixels,
- translucent selected fill, outlined rectangles, 1 dp strokes,
- four corner handles on the selected rectangle,
- label `A-B1`, `E-P2`, `G-B1` from team and type/index.

Gestures: tap select/topmost or deselect; drag inside moves (clamped); drag corner handle resizes; drag empty canvas creates a pending rect and commits above `0.01` normalized size; no source pixels are read/written beyond rendering. Pointer positions map display→oriented→source through `FrameTransform.create(..., crop = null, processedWidth = orientedWidth, processedHeight = orientedHeight)`.

Overlay labels use uppercase team initial, `-`, type initial, index (`A-B2`). Canvas exposes selected slot ID to the parent.

- [ ] **Step 5: Implement accessible editor and profile controls**

`SlotEditor` displays each slot as a card with select/delete buttons, team/type/index controls (`FilterChip` rows), label text field, and editable normalized `left/top/right/bottom` OutlinedTextFields. Apply validates bounds, duplicate index, and overlap; errors shown inline. Include "Reset annotations" with a confirmation dialog. Slot metadata and geometry are fully editable without canvas gestures.

`ProfileControls` disables save/apply for incompatible/corrupt states; apply shows exact compatibility result; delete requires confirmation; too-new schema shows only "Delete all calibration data". Use monospace text for rectangle values where useful.

- [ ] **Step 6: Build and manually verify against existing debug workflow**

Run: `./gradlew :app:compileDebugKotlin --stacktrace`
Expected: BUILD SUCCESSFUL.

Run the app only if a device/emulator is actually available; do not claim device verification otherwise.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/draftlens/MainActivity.kt app/src/main/kotlin/com/draftlens/feature/visionlab app/src/main/kotlin/com/draftlens/feature/home/DebugCaptureScreen.kt
git commit -m "feat: add Vision Lab annotation interface"
```

---

### Task 9: Documentation and project notes

**Files:**
- Create: `docs/VISION_ARCHITECTURE.md`
- Create: `docs/VISION_FRAME_CONTRACT.md`
- Create: `docs/VISION_COORDINATES.md`
- Create: `docs/VISION_LAB.md`
- Create: `docs/VISION_TESTING.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `README.md`
- Modify: `tasks/plan.md`
- Modify: `tasks/todo.md`

Before writing docs, record the wall-clock duration of the JVM test task and the output of the geometry/preprocessing test filters, for example:

```bash
TIMEFORMAT='%R seconds'
time ./gradlew test --stacktrace
./gradlew :app:testDebugUnitTest --tests 'com.draftlens.vision.geometry.GeometryTest' --tests 'com.draftlens.preprocessing.*' --stacktrace
```

Use only measurements actually obtained; if a command fails, record the failure instead of a duration.

**Interfaces:**
- Consumes: implementation results and CI run state.
- Produces: accurate docs, no fake verification claims.

- [ ] **Step 1: Write new Vision documents**

Each document states what exists, what does not, and what was actually run. Include:

- data flow and package boundaries,
- payload ownership/lifetime and capture adapter,
- coordinate conventions and rotation correction vs display rotation,
- preprocessing limits and honest failure behavior,
- annotation model and profile schema/1% tolerance,
- freeze/resume, explicit process-only operation,
- test inventory, synthetic fixtures only,
- CI/APK status and limitations,
- deliberate exclusions: no recognition, no OCR, no screenshots persisted, no upload.

Do not cite `docs/PRIVACY.md`; it was removed. Do not claim physical-device tests unless run.

- [ ] **Step 2: Update README and architecture docs**

README gets Sprint 1A header, Vision Lab status, tab workflow, and build commands. `docs/ARCHITECTURE.md` lists Vision packages and the capture→Vision adapter boundary. Both state Debug Capture remains available and no automatic detection exists.

- [ ] **Step 3: Update task lists**

Add Sprint 1A checklist with actual executed commands and leave device verification explicitly unchecked/pending unless run. Keep prior Sprint 0.1 history unchanged.

- [ ] **Step 4: Commit docs**

```bash
git status --short
git add README.md docs tasks
git commit -m "docs: document Sprint 1A vision foundation"
```

---

### Task 10: Full verification, PR, CI, APK, report

**Files:**
- Modify: files from Task 9 only if verification reveals stale docs.
- No new feature files.

- [ ] **Step 1: Run full JVM tests locally**

Run: `./gradlew test --stacktrace`
Expected: all unit tests pass. If aarch64/SDK mismatch blocks an Android task, record the exact failure and rely on CI; do not change assertions to fit the host.

- [ ] **Step 2: Run CI-equivalent assembly/lint locally only if host supports it**

Run: `./gradlew lint assembleAndroidTest assembleDebug --stacktrace`
Expected: success on compatible host; if host fails due to x86_64 build-tools, record it and do not claim local build.

- [ ] **Step 3: Inspect diff and assert no prohibited artifacts**

```bash
git status --short
git diff --check
git diff --stat main...HEAD
```

Verify no screenshots, no image assets, no network client, no `FLAG_SECURE`, no generated build output.

- [ ] **Step 4: Push branch and open PR**

```bash
git push -u origin sprint-1-vision-foundation
gh pr create --base main --head sprint-1-vision-foundation --title "Sprint 1A: Vision foundation and Vision Lab" --body "<summary, tests, limitations, APK workflow link>"
```

Do not merge. If PR already exists, update it rather than create a duplicate.

- [ ] **Step 5: Watch CI and capture artifact**

Use `gh pr checks --watch` or the OpenChamber PR watch for the new PR. Record run URL. When green:

```bash
gh run list --branch sprint-1-vision-foundation --limit 5
gh run view <run-id> --json url,conclusion,workflowName
```

Find `draftlens-debug` artifact URL from the run. If CI fails, fix the smallest cause, commit, push, and watch again; do not bypass lint or tests.

- [ ] **Step 6: Update docs with actual CI result and commit**

Replace any pending CI language in `docs/VISION_TESTING.md` or task lists with the run URL and outcome. Commit:

```bash
git add README.md docs tasks
git commit -m "docs: record Sprint 1A CI verification"
git push
```

- [ ] **Step 7: Final report**

Report: changed files, commits, tests actually run, CI run link, APK artifact link, merged status (not merged), limitations, and exact statement that instrumentation tests were compiled but not run unless an emulator/device was used.
