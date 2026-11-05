# Architecture — DraftLens Sprint 0

## Stack
Kotlin 2.0.20 · AGP 8.6.1 · Gradle 8.7 · compileSdk/targetSdk 35 · minSdk 26 · Jetpack Compose (material3) ·
Coroutines + Flow · DataStore Preferences. Single Gradle module `:app`.

## Package map (Sprint 0 subset; later packages added as milestones justify them)
```
com.draftlens
  app/            MainActivity, DraftLensApp (DI root, no framework)
  capture/        CaptureSessionService (foreground), CaptureController, FramePipeline, FrameSampler
  models/         CaptureSessionState + CaptureTransitions, CapturedFrame
  permissions/    ProjectionPermission (consent intent factory)
  datastore/      AppPreferencesRepository (sampling interval)
  detection/      Interfaces only — ScreenCaptureProvider, FrameProcessor, DraftPhaseDetector,
                  HeroRecognizer, HeroSlotDetector, DetectionValidator (all NotImplemented)
  feature/home/   HomeViewModel, DebugCaptureScreen
```
Room and the remaining abstract packages are **not** created yet — Sprint 0
has no persisted records or independent feature modules, so empty scaffolding is omitted.

## State machine
`IDLE → AWAITING_PERMISSION → STARTING → CAPTURING ⇄ PAUSED → STOPPING → IDLE`, any → `ERROR → IDLE`.
`CaptureTransitions.canTransition()` is the single authority. UI and service both speak
`CaptureSessionState`; the service is the source of truth, the ViewModel mirrors actions.

## Data flow
MediaProjection → VirtualDisplay → ImageReader(maxImages=3) → HandlerThread("draftlens-capture")
→ FrameSampler(monotonic elapsed time, ≥interval) → Bitmap (latest-only, stale ref cleared on stop/reset) → `StateFlow<CapturedFrame>`
→ ViewModel → Compose preview. Main thread never decodes pixels; UI stays responsive.

## Key decisions
- Latest-frame-only slot because stale frames are worthless for draft detection.
- Service foregrounds before `getMediaProjection()`.
- Consent re-requested and token used once per session—never cached.
- Capture dimensions prefer `maximumWindowMetrics` on API 30+; legacy fallback is retained below that.
- Bitmaps are never recycled while Compose may still render them; stopping/resetting clears the state reference.
- Self-capture: no `FLAG_SECURE`; users see system scope options to choose “Entire screen” (debug UI included) or “A single app”.
- No network code in Sprint 0. Data-source audit is documentation-only (`docs/DATA_SOURCES.md`).
