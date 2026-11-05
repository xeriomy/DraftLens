# Sprint 0 Task List

## Sprint 0.1 Capture Hardening
- [x] Task 1: AGP 8.6.1 (API 35 support); Gradle/Kotlin unchanged
- [x] Task 2: Strict lint gate in module and CI
- [x] Task 3: Frame memory ownership: safe bounded latest-frame model
- [x] Task 4: FrameSampler monotonic/Android elapsed time + wall-clock separation + tests
- [x] Task 5: WindowMetrics (API 30+) and deprecated fallback; minimize VIRTUAL_DISPLAY flags
- [x] Task 6: Physical-device lifecycle matrix checklist; results pending
- [x] Task 7: Update implementation, architecture and capture docs
- [x] Task 8: CI green; deliver hardened APK and report

## Sprint 0

## Phase 1: Foundation
- [x] Task 1: Scaffold Gradle project, manifest, single-screen nav, CI workflow
- [x] Task 2: Core models + DataStore preferences repository
- [x] Task 3: Capture state machine + unit tests (TDD red first)

## Phase 2: Capture pipeline
- [x] Task 4: CaptureSessionService (foreground, notification, stop action) + controller
- [x] Task 5: FramePipeline (ImageReader → Bitmap) + FrameSampler (TDD)
- [x] Task 6: Permission launcher + ViewModel wiring

## Phase 3: UI
- [x] Task 7: Debug screen (Compose): status, preview, metrics, pause/resume, errors
- [x] Task 8: Detection stub interfaces with explicit NotImplemented states

## Phase 4: Docs + verify
- [x] Task 9: README, ARCHITECTURE, SCREEN_CAPTURE, DATA_SOURCES, PRIVACY, ADR-001
- [x] Task 10: Tests + lint + assembleDebug green; APK + manifest verified; report written
