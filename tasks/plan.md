# Implementation Plan: DraftLens

## Sprint 1A — Vision Foundation, Preprocessing & Vision Lab
**Objective:** Give the app a real image-bearing foundation — non-null frames, tested
geometry, validated preprocessing, manual slot annotation, a Vision Lab, and versioned
calibration profiles. No recognition, no recommendations, no overlays.

### Work items in order
- [x] Task 1: Vision model and geometry value types (`a2731d3`)
- [x] Task 2: Rotation-aware geometry and round trips (`357a40f`)
- [x] Task 3: Non-null capture frames plus display rotation (`8e1381a`)
- [x] Task 4: Validated preprocessing engine (`79bd211`, `41aa126`, `b81e740`)
- [x] Task 5: Manual slot annotations and JSON codec (`1086b75`, `cab3d8d`)
- [x] Task 6: Versioned calibration profiles (`08ca414` reviewed, completed in `b84b69f`, `85f04c8`)
- [x] Task 7: Vision frame source and ViewModel (`fedaa4e`, `ec24a9a`, `0c7d286`, `b4c8c78`)
- [x] Task 8: Compose Vision Lab (`6ad2287`, `45e2f2c`, `691b9d2`)
- [x] Task 9: Vision documentation (`303b818`, `da35ca0`)
- [x] Task 10: CI, PR, APK artifact, implementation report
- [x] Final whole-branch review; findings fixed in `691b9d2` with a failing test each
- [x] Focused review fixes (`8f046c0`, `313775d`, `007621f`): display rotation updated
      independently of the capture size, canvas letterbox rejected, Clear cancels in-flight
      processing, shared slot validator for calibration documents, frozen-frame required for
      profile save/update/apply, documentation and CI record updated
- [x] Canvas rendering and workspace fixes (`01603de`, `4c42513`): the bitmap is scaled
      uniformly into the fitted rectangle so the pixels and the overlay share one set of
      bounds, the canvas is sized from the oriented aspect ratio, `displayRotation` is
      `@Volatile`, and the Vision Lab explains its controls in place
- [x] Sprint 1A.1 interaction redesign (`927e8e0`, `dffc12d`): an explicit gesture state
      machine, a contextual slot property editor with copy/paste/duplicate on an internal
      clipboard, a delete control with undo, a grouped slot list keyed by slot id, a
      fullscreen editor, and team colours
- [x] Sprint 1A.1 corrections (`24a43af`, `593eeb4`): the delete control took over the
      top-right resize handle, the long press opens the menu on its own timeout, the
      fullscreen editor lost its zoom and fit controls, and the canvas no longer lets the
      page scroll during annotation
- [x] Sprint 1A.2 bug fixes (`25307b8`): paste reads from the clipboard with the target
      only anchoring the placement, the menu selects the slot it shows, an early drag
      cancels the pending long press, one rotation snapshot per captured frame, and
      validation failures surfaced in the property editor

### Sprint 1A verification
CI (GitHub Actions, ubuntu-latest x86_64) is the build of record: `test`,
`assembleAndroidTest`, `lint`, `assembleDebug`, plus a `draftlens-debug` APK artifact.
The authoring host is aarch64 and AGP 8.6.1 ships only an x86_64 `aapt2`, so no Gradle
task that touches resources can run there. Instrumentation tests are compiled, not executed:
no device or emulator was available. Vision Lab interaction behavior is maintainer-reported
PASS on a physical device (device details not supplied); the MediaProjection lifecycle
matrix is **NOT RUN**.

## Sprint 0.1 — Capture Hardening & Technical Cleanup
**Objective:** Harden the reported-working capture path before Vision Engine. No recognition,
recommendations, overlays, or architecture rewrites.

### Work items in order
- [x] Task 1: AGP 8.5.2 → 8.6.1; keep Gradle 8.7, Kotlin 2.0.20; run test/lint/assembleAndroidTest
- [x] Task 2: Strict lint: remove silent `abortOnError=false` / release checks; fail CI on lint errors
- [x] Task 3: Frame memory ownership: no unsafe Bitmap recycling; latest-only ownership; stale refs cleared
- [x] Task 4: Frame timing: monotonic/Android elapsed-realtime sampler; wall-clock retained separately; latency semantics clarified; tests
- [x] Task 5: MediaProjection: one VirtualDisplay per projection, API-appropriate resize path, WindowMetrics/appropriate fallback, minimum display flags
- [x] Task 6: Physical-device lifecycle matrix document; mark results explicitly pending until executed
- [x] Task 7: Docs updated: IMPLEMENTATION_REPORT, SCREEN_CAPTURE, ARCHITECTURE
- [x] Task 8: CI green: test, lint, assembleAndroidTest, assembleDebug, APK manifest check

### Sprint 0.1 verification
Gradle: `test`, `lint`, `assembleAndroidTest`, `assembleDebug`. Physical device matrix is pending and is not claimed here.
Manual smoke test already performed by maintainer and is recorded as such.

## Overview
Single-module native Android app (Kotlin + Compose, MVVM, Coroutines/Flow) that validates
MediaProjection screen capture in a foreground service and shows a live debug preview.
No recognition models, no recommenders, no overlays this sprint. Foundation must be ready to
grow without re-architecture.

## Architecture Decisions
- Kotlin 2.0.20, AGP 8.5.2, Gradle 8.7, compileSdk/targetSdk 35, minSdk 26 (MediaProjection
  baseline), JDK 17. Rationale: stable, compatible with SDK 35 + Android 14/15 MediaProjection
  changes; minSdk 26 avoids legacy permission-branch complexity while covering vast majority.
- Single Gradle module `:app`. Rationale: Sprint 0 has no justified module boundaries yet; ADR-001
  documents the split trigger (second feature needing independent test/deploy cycle).
- Foreground service type `mediaProjection`, notification with Stop action, consent re-requested
  every session via `ActivityResultContracts.CreateScreenCaptureIntent()`. Android 14+:
  service must start before `getMediaProjection()`; `onStop` + `onCapturedContentResize`
  handled; `MediaProjection.Callback` releases resources.
- Frame pipeline: ImageReader (maxImages=3, backpressure-drop oldest), sampled at 2 FPS default
  (configurable 500–2000 ms via DataStore, capped 3 FPS), frames held in memory as `Bitmap`
  (LIFO single-slot — only latest kept to avoid OOM), no disk writes, no upload.
- State: `CaptureSessionState` sealed-ish data class + StateFlow; ViewModel exposes UI state.
  Rotation/resize recreates VirtualDisplay via `onCapturedContentResize` + config-change.
- Domain seams (explicit NotImplemented, never fake data): ScreenCaptureProvider,
  FrameProcessor, DraftPhaseDetector, HeroRecognizer, HeroSlotDetector, DetectionValidator.
- Data sources: Gradle dependencies for lifecycle/compose/coroutines/datastore. Room
  ships with first persisted entity to avoid empty database. Preferences repository has
  `samplingIntervalMs`. No network client in Sprint 0 —
  DATA_SOURCES.md records audit; API sync deferred to later milestone per scope rules.

## Task List
### Phase 1: Foundation
- [x] Task 1: Scaffold Gradle project, manifest, nav (single landing screen), CI workflow
- [x] Task 2: Core models + DataStore preferences repository (Room deferred)
- [x] Task 3: Capture state machine + unit tests (TDD, red first)

### Phase 2: Capture pipeline
- [x] Task 4: CaptureSessionService (foreground, notification, stop action) + ScreenCaptureController
- [x] Task 5: FramePipeline (ImageReader → Bitmap → StateFlow) + FrameSampler
- [x] Task 6: MediaProjection permission launcher + ViewModel wiring

### Phase 3: UI
- [x] Task 7: Debug screen (Compose) with status, preview, metrics, pause/resume, errors
- [x] Task 8: Detection stub interfaces (NotImplemented states)

### Phase 4: Docs + verify
- [x] Task 9: README, ARCHITECTURE, SCREEN_CAPTURE, DATA_SOURCES (verified live), ADR-001
- [x] Task 10: Tests pass, lint passes, debug APK built, manifest verified, report written

## Risks and Mitigations
| Risk | Impact | Mitigation |
|------|--------|------------|
| MediaProjection revoked mid-session on Android 14+ | Service crash / leaked resources | Callback + try/catch → STOPPED state, full resource release in finally |
| ImageReader backpressure OOM on high-res | App kill | maxImages=3, single-latest-bitmap slot, drop-oldest, sampling throttle |
| Emulator lacks MediaProjection reliability | False "works" claim | Docs + report explicitly state emulator-only, physical-device test plan required |
| API data definitions differ (Rone vs MLBBDex) | Wrong recommendations later | No recommendation code in Sprint 0; DATA_SOURCES.md records definitions verbatim |

## Open Questions
- App ID / name: using `com.draftlens` / "DraftLens" (rename freely).
- Kotlin/AGP/SDK pins above are builder calls, recorded in ADR-001.

## Checkpoints
- After Phase 1: `./gradlew test` green, app assembles (empty landing OK).
- After Phase 2: unit tests for state machine + sampler green; lint + assemble green.
- Final: test + lint + assembleDebug all green; APK exists; manifest grep verified.

### Sprint 1B — Hero recognition (first pipeline)
- Hero catalog sync (manual refresh, atomic snapshot, offline browsing) wired through a small transport seam.
- Slot extraction from the annotation canvas with rotation coverage.
- Recognition v0 with labeled similarity and explicit no-match; manual confirmation required.
- Draft PR #3; accuracy on real screens not measured; device smoke pending.

## Sprint 1B — Review fixes and game-frame freeze (this revision)
Objective: fix the PR #3 review findings (freeze from the notification; rotation bugs;
blank-image rejection; manual correction everywhere; cache freshness; atomic persistence;
reference-loader ownership) and wire the freeze workflow through the capture architecture.
No recommendation features, no overlays, no automation, PR stays draft and unmerged.

### Work items in order
- [x] Freeze a game frame from the notification (`CaptureFreeze`, `ACTION_FREEZE` /
      `ACTION_RESUME_LIVE`, `FramePipeline` freeze gate, independently owned snapshot via
      `FrameSnapshots`, FROZEN notification with Resume action, in-app Resume unchanged)
- [x] Full-device-capture guidance; POST_NOTIFICATIONS handling; freeze actions in the
      expanded notification; no Activity launched by the command
- [x] Part B: pixel extractor rotation fixed to the `FrameTransform.sourceToOriented()`
      convention; tests rewritten with asymmetric non-square fixtures and hand-computed
      expected values
- [x] Landscape capture fix (MLBB portrait→landscape rotation bug):
      - authoritative `onCapturedContentResize` signal feeds one idempotent resize path;
      - `DisplayManager` fallback re-derives the capture size on any display change;
      - VirtualDisplay/ImageReader re-created at the reported size;
      - capture generations reject stale reader callbacks at listener and publish time;
      - plane conversion honors `image.cropRect` and padded rows (`RgbaPlaneCopy`, JVM-tested
        with asymmetric fixtures, letterboxed crops and stride variants);
      - structured diagnostics in `CaptureSessionService`/`FramePipeline`/`VisionPreprocessor`;
      - physical-device verification pending (rows 23–32 of `docs/TESTING_PHYSICAL_DEVICE.md`)
- [x] Part C: Android extractor validates Bitmap dimensions, pixel-exact rotation, source
      bitmap preserved, every temporary released exactly once; instrumentation tests added
- [x] Part D: `VisualInformationAnalyzer` (variance, edge density, meaningful differences,
      minimum dimensions) gates matching with a distinct `InsufficientVisualInformation`
      outcome and `INSUFFICIENT_INFO` UI state; blank crops never produce candidates
- [x] Part E: manual correction reachable from CANDIDATES, NO_MATCH, FAILED,
      INSUFFICIENT_INFO and CONFIRMED; Change… opens correction instead of Identify;
      failed persistence reported, never shown as success
- [x] Part F: cache freshness — per-asset `*.meta` sidecar (URL, SHA-256, size, content
      type), serve-time validation (hash, magic bytes, container structure, Android decode),
      URL change forces re-download, failed refresh keeps previous asset, HTTP downgrades
      refused everywhere
- [x] Part G: snapshot/asset writes are temp-sibling + flush/sync + `ATOMIC_MOVE` only;
      no direct-write fallback; failures preserve the previous target; `*.tmp` swept
- [x] Part H: reference loader reads dimensions before recycling; pixels read before release;
      exceptional paths recycle too
- [x] CI green on the final head (run 43746648882, head 1c7397b925dd2683b3d763e552d610d2e464ecb7; instrumentation compiled, not executed — no KVM on the runner)
      (no KVM on the runner) — needs a device or KVM-enabled emulator
- [ ] Physical-device smoke of the freeze workflow; on-device accuracy
      evaluation; real-screen threshold and heuristic calibration

### Sprint 1B verification
See `docs/VISION_TESTING.md` for the exact CI runs, test totals and what was and was not
executed. The authoring host remains aarch64, so the CI is the build of record.
Physical-device verification of the freeze workflow is pending.

## Sprint 1C — App-only capture & circular floating control
Objective: let the user freeze/resume the live MLBB capture without leaving the game —
prefer single-app (partial) capture, keep the last valid game frame when MLBB is hidden,
and add exactly one circular overlay button. No recommendation UI, no panels, no automation.
PR #4 stacked on PR #3 (`sprint-1b-hero-recognition`); nothing merges automatically;
Sprint 1B history intact.

### Work items in order
- [x] Pure visibility policy + truth-table tests (hidden ⇒ nothing publishes; frozen wins over visible)
- [x] `FramePipeline` gate: images acquired/closed but never converted while hidden;
      publish-time synchronized re-check; API-34 `onCapturedContentVisibilityChanged` in the
      existing CAPTURING guard
- [x] Freeze-while-hidden / resume-while-hidden semantics: last valid frame preserved;
      LIVE intent remembered across hidden periods; no second VirtualDisplay/projection ever
- [x] Overlay position math (normalized free-axis coords, edge snap) + JVM tests
- [x] Overlay enablement decision + DataStore persistence (position + enabled)
- [x] 52dp circular `TYPE_APPLICATION_OVERLAY` button: tap = Freeze/Resume (green/orange,
      white glyph), 400 ms long-press drag, edge snap, rotation re-anchor, non-focusable
- [x] Service attach/detach only during capture, every teardown path removes it;
      `ACTION_OVERLAY_UPDATE` re-sync; manifest declares `SYSTEM_ALERT_WINDOW`, requested
      only from the enable toggle
- [x] Two-mode capture guidance in the debug screen + Vision Lab; denial path documented
      (capture + in-app Freeze/Resume unaffected)
- [x] Instrumentation test for overlay attach/remove (compile-only in CI)
- [x] JVM sequence tests: hidden→freeze→resume→visible; explicit freeze blocks auto-resume
- [ ] CI green record, APK digest, device rows 33–43 NOT RUN (maintainer)
- [ ] PR #4 description + report; physical-device acceptance checklist is maintainer-run
