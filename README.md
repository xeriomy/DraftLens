# DraftLens — Sprint 1A Vision Foundation

Native Android app that assists MLBB Draft Mode players. Sprint 1A turns the previously
working capture path into a real **image-bearing** foundation: frames carry pixels, geometry
is tested, preprocessing validates before it renders, and a Vision Lab lets you place manual
slot annotations and save calibration profiles.

**Maintainer-reported device smoke:** the Sprint 0 capture path was used on a physical Android
device and confirmed working. Sprint 1A itself has **not** been verified on a physical device —
see [docs/TESTING_PHYSICAL_DEVICE.md](docs/TESTING_PHYSICAL_DEVICE.md) and
[docs/VISION_TESTING.md](docs/VISION_TESTING.md).

## What works in Sprint 1A
- MediaProjection consent flow (`ActivityResultContracts.CreateScreenCaptureIntent`)
- Foreground service (`mediaProjection` type) with persistent notification + Stop action
- Frame acquisition at 2 FPS default, configurable 334–2000 ms (hard cap 3 FPS)
- Live frame preview in the debug screen, frame count / resolution / latency / timestamp
- Pause / resume, rotation & resize handling, clean resource teardown on stop/revoke
- Non-null frame contract: `FramePayload`, `OriginalFrame`, `ProcessedFrame`, `SlotImage`
- Rotation-aware geometry with round-trip tests (`FrameTransform`)
- Validated preprocessing with honest failure codes (`PreprocessingMath`,
  `PixelArrayFramePreprocessor`, `AndroidFramePreprocessor`)
- Manual slot annotations with stable ids, bounds/duplicate/overlap validation and a strict
  JSON codec
- Versioned calibration profiles: 1 % aspect-ratio tolerance, exact orientation match,
  unsupported-schema refusal, delete-as-escape-hatch
- Vision Lab tab: frame preview, freeze/resume, explicit processing, interactive annotation
  canvas, text slot editor, profile controls

## What is deliberately NOT here (later milestones)
ML hero recognition, recommendation engine, counter/synergy/role scoring, floating overlays,
API sync, player profiling, builds. The Vision Lab says **"Detection: not implemented"** —
manual slots are calibration data, never detections. See `tasks/plan.md` and
`docs/ARCHITECTURE.md`.

## Build
Requires JDK 17 and Android SDK platform 35, build-tools 35.
```sh
./gradlew test            # JVM unit tests
./gradlew lint            # Android lint
./gradlew assembleDebug   # produces app/build/outputs/apk/debug/*.apk
```
GitHub Actions (`.github/workflows/build.yml`) runs exactly those steps on push/PR and uploads
the APK artifact. **This authoring environment is aarch64 and the SDK's aapt2/build-tools are
x86_64-only, so nothing that touches resources can be assembled locally — the CI run is the
build of record.** See `docs/VISION_TESTING.md` for exactly what ran.

## Run
1. Install the APK from the `draftlens-debug` CI artifact on a physical device (the emulator
   validates compilation, not MediaProjection reliability).
2. Open the app; the **Debug Capture** tab is first.
3. Tap **Start Capture** → grant the system screen-capture consent dialog.
4. MLBB (or any app) — the debug screen shows live frames.
5. Switch to the **Vision Lab** tab. Capture keeps running while you are there.
6. Freeze a frame, drag out a slot rectangle, then edit it in the text editor below the
   canvas. Press **Process Frame** to run the preprocessing engine on the selected frame.
7. Save the annotations as a calibration profile; apply it later on a matching frame.
8. **Stop Capture**, or the notification's **Stop**, or revoke consent in Settings to
   exercise teardown.

## Docs
- `docs/ARCHITECTURE.md` — module layout, state machine, data flow, Vision boundary
- `docs/VISION_ARCHITECTURE.md` — Vision packages, ownership, processing rules
- `docs/VISION_FRAME_CONTRACT.md` — payload contract, ownership, original/processed/slot frames
- `docs/VISION_COORDINATES.md` — normalized coordinates, rotation tables, crop semantics
- `docs/VISION_LAB.md` — user-facing controls, freeze behavior, slot editing, profile rules
- `docs/VISION_TESTING.md` — test inventory, CI gates, what was and was not run
- `docs/SCREEN_CAPTURE.md` — MediaProjection lifecycle, Android 14+ rules, limits
- `docs/DATA_SOURCES.md` — Rone Arena vs MLBBDex audit (verified live 2026-11-04)
- `docs/decisions/ADR-001-android-native.md` — why native, single-module, and version pins
- `docs/IMPLEMENTATION_REPORT.md` — executed verification, limitations, next tasks
