# Architecture

## Stack

Kotlin 2.0.20 · AGP 8.6.1 · Gradle 8.7 · JDK 17 · compileSdk/targetSdk 35 · minSdk 26 ·
Jetpack Compose (Material 3) · Coroutines and Flow · DataStore Preferences ·
kotlinx-serialization. One Gradle module, `:app`. Versions are centralised in
`gradle/libs.versions.toml`.

## Layout

```text
com.draftlens
  MainActivity            two-tab host; DraftLensApp holds process-scoped dependencies
  capture/                foreground service, controller, pipeline, sampler, freeze, visibility,
                          generations, plane copy, diagnostics, the capture->Vision adapter
  models/                 capture session state and transitions, captured frame, snapshots
  datastore/              preferences, calibration document, hero confirmations
  permissions/            projection consent intent factory
  overlay/                floating control view, controller, enablement, position math
  vision/model/           ids, enums, payloads, frames, VisionFrameSource
  vision/model/android/   BitmapFramePayload
  vision/geometry/        normalized and pixel value types, FrameTransform
  vision/preprocessing/   plan/result, pure math, renderers, diagnostics
  vision/annotation/      slots, store, codecs, validator
  vision/calibration/     profiles, compatibility, codec, repository
  vision/heroes/          catalog, transport, cache, extraction, visual gate, matcher
  feature/home/           capture debug screen and ViewModel
  feature/visionlab/      Vision Lab ViewModel, screens, canvas and panels
```

`vision/**` is pure Kotlin except the explicitly Android-named adapters. This is what makes
geometry, codecs, policy and matching unit-testable without an emulator.

## State and ownership

The capture state machine is
`IDLE → AWAITING_PERMISSION → STARTING → CAPTURING ⇄ PAUSED → STOPPING → IDLE`, with any state
able to enter `ERROR → IDLE`. The service owns the projection and publishes state; the
ViewModel mirrors it. `CaptureStateHolder` is process-local by design: process death clears it
because MediaProjection tokens cannot survive.

Publication is gated by three independent conditions: the session is capturing, no frame is
pinned, and the captured window is visible (where the platform reports visibility). Freeze,
pause and hide all stop conversion without tearing down the projection.

## Data flow

```text
MediaProjection → VirtualDisplay → ImageReader (maxImages 3)
  → RgbaPlaneCopy → Bitmap (latest only, generation-checked)
  → CaptureStateHolder → CaptureVisionFrameSource → VisionLabViewModel
  → Compose preview / annotation canvas
  → explicit Process Frame → Dispatchers.Default → ProcessedFrame
  → select slot → extract → visual gate → match → confirm → store
```

The reader runs on a dedicated `HandlerThread`; the main thread never decodes pixels. Stale
callbacks from replaced reader generations are dropped at the listener and again before
publication. A resize replaces the ImageReader and VirtualDisplay at the reported size; a pure
rotation change only updates metadata.

## Boundaries

- **Capture ↔ Vision:** `VisionFrameSource` is the only seam. `CaptureVisionFrameSource` is the
  single file that imports both packages.
- **Pure ↔ Android:** geometry, codecs, policy and matching have no Android imports; platform
  adapters are thin and named.
- **Frames ↔ disk:** captured pixels and slot crops never leave memory. Only metadata,
  calibration profiles and public catalog assets are persisted.

## Build and verification

Gradle tasks in CI (`.github/workflows/build.yml`): `test`, `assembleAndroidTest` (compile
only), `lint` with `abortOnError = true`, `assembleDebug`, APK upload and manifest inspection.
`main` is protected by the build check. CI is the build of record because the authoring host is
aarch64 and the SDK's `aapt2` is x86_64-only.

See [Screen capture](SCREEN_CAPTURE.md) for the platform lifecycle,
[Vision architecture](VISION_ARCHITECTURE.md) for the processing pipeline, and
[Physical-device checklist](TESTING_PHYSICAL_DEVICE.md) for what CI cannot prove.
