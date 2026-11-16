# DraftLens — Sprint 1A Vision Foundation

Native Android app that assists MLBB Draft Mode players. Sprint 1A turns the previously
working capture path into a real **image-bearing** foundation: frames carry pixels, geometry
is tested, preprocessing validates before it renders, and a Vision Lab lets you place manual
slot annotations and save calibration profiles.

**Maintainer-reported device smoke:** the Sprint 0 capture path and the Sprint 1A Vision
Lab interaction behavior were used on a physical Android device and confirmed working.
Device details, the exact build and test dates were not supplied, and the MediaProjection
lifecycle matrix remains **NOT RUN**. See [docs/TESTING_PHYSICAL_DEVICE.md](docs/TESTING_PHYSICAL_DEVICE.md)
and [docs/VISION_TESTING.md](docs/VISION_TESTING.md).

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
- Hero recognition v0 (Sprint 1B, PR #3): hero catalog sync from public static data,
  explicit Identify action for the selected slot, ranked candidates with similarity scores,
  manual confirm/correct — **accuracy on real game screens is not measured yet**
- Freeze the game frame from the notification (Sprint 1B): tap **Freeze current frame** on the
  capture notification while you are in MLBB — no need to leave the game; Vision Lab shows the
  pinned game frame, not the DraftLens screen; **Resume live capture** from the notification or
  in the Vision Lab
- App-only capture guidance (Sprint 1C): prefer **Single app / app window → Mobile Legends**
  (Android 14+ chooser); the captured window's visibility drives publication —
  MLBB hidden ⇒ the last valid game frame is kept, never DraftLens/shade/system UI;
  full-display capture remains the documented fallback (it may include the shade/system UI)
- Circular floating freeze button (Sprint 1C): one 52dp circular `TYPE_APPLICATION_OVERLAY`
  button shown only while capture runs — tap toggles LIVE (green) / FROZEN (orange),
  long-press-and-drag moves it, it snaps to a screen edge, position persists normalized in
  DataStore; notifications/in-app Freeze/Resume keep working without it; the overlay
  permission is requested only when you enable the button

## What is deliberately NOT here (later milestones)
Recommendation engine, counter/synergy/role scoring — and no recommendation overlays
(Sprint 1C ships exactly one capture-freeze overlay button, and nothing else).
ML hero recognition is Sprint 1B (PR #3). API sync, player profiling, builds.
The Vision Lab says **"Detection: not implemented"** — manual slots are calibration data,
never detections. See `tasks/plan.md` and `docs/ARCHITECTURE.md`.

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
3. Tap **Start Capture** → grant the system screen-capture consent dialog. When Android asks
   what to capture, prefer **Single app / app window → Mobile Legends: Bang Bang** (Android 14+);
   if that option is missing, choose **Entire screen / full device display** (it can capture the
   shade and system UI). See the in-app guide.
4. Open Mobile Legends and wait for the draft screen you want to annotate.
5. With MLBB on screen, tap the circular **Floating control** button (enable it on the capture
   screen first) to freeze the frame — or, if you never granted the overlay permission, pull
   down the notification shade and tap **Freeze current frame** (notifications must be enabled).
6. Switch back to **DraftLens → Vision Lab**. Capture kept running; the Vision Lab shows the
   pinned MLBB frame instead of the DraftLens screen.
7. Draw a slot rectangle, then run **Identify hero** on the frozen frame. Candidates are
   similarity scores, not probabilities — confirm or correct manually (the manual search is
   available in every recognition state).
8. Tap **Resume live capture** in the notification, or **Resume** in the Vision Lab, to show
   live frames again. No second capture session is created.
9. Press **Process Frame** to run the preprocessing engine on the selected frame.
10. Save the annotations as a calibration profile; apply it later on a matching frame.
11. **Stop Capture**, or the notification's **Stop**, or revoke consent in Settings to
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
