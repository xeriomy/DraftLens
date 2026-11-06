# Implementation Plan: DraftLens Sprint 0 — Android Foundation & Live Capture PoC

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
