# Architecture — DraftLens Sprint 0

## Stack
Kotlin 2.0.20 · AGP 8.5.2 · compileSdk/targetSdk 35 · minSdk 26 · Jetpack Compose (material3) ·
Coroutines + Flow · Room (scaffolding) · DataStore Preferences. Single Gradle module `:app`.

## Package map (Sprint 0 subset; later packages added as milestones justify them)
```
com.draftlens
  app/            MainActivity, DraftLensApp (DI root, no framework)
  capture/        CaptureSessionService (foreground), CaptureController, FramePipeline, FrameSampler
  models/         CaptureSessionState + CaptureTransitions, CapturedFrame
  permissions/    ProjectionPermission (consent intent factory)
  database/       DraftLensDatabase (no entities in Sprint 0)
  datastore/      AppPreferencesRepository (sampling interval)
  detection/      Interfaces only — ScreenCaptureProvider, FrameProcessor, DraftPhaseDetector,
                  HeroRecognizer, HeroSlotDetector, DetectionValidator (all NotImplemented)
  feature/home/   HomeViewModel, DebugCaptureScreen
```
`common/`, `preprocessing/`, `validation/`, `settings/`, `testing/` from the brief are **not**
created yet — Sprint 0 has no code that needs them (see ADR-001 module-split trigger).

## State machine
`IDLE → AWAITING_PERMISSION → STARTING → CAPTURING ⇄ PAUSED → STOPPING → IDLE`, any → `ERROR → IDLE`.
`CaptureTransitions.canTransition()` is the single authority. UI and service both speak
`CaptureSessionState`; the service is the source of truth, the ViewModel mirrors actions.

## Data flow
MediaProjection → VirtualDisplay → ImageReader(maxImages=3) → HandlerThread("draftlens-capture")
→ FrameSampler(≥interval) → Bitmap (latest-only, previous recycled) → `StateFlow<CapturedFrame>`
→ ViewModel → Compose preview. Main thread never decodes pixels; UI stays responsive.

## Key decisions
- Latest-frame-only slot: OOM-safe on 4K displays; stale frames are worthless for draft detection.
- Service foregrounds before `getMediaProjection()` (Android 14+ requirement).
- Consent re-requested every session — Android requires it per capture; never cached.
- No network code in Sprint 0. Data-source audit is documentation-only (`docs/DATA_SOURCES.md`).
