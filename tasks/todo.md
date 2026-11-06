# Sprint 0 Task List

## Sprint 1A: Vision Foundation (in progress)
- [x] Task 1: Vision model and geometry value types (`a2731d3`)
- [x] Task 2: Rotation-aware geometry and round trips (`357a40f`)
- [x] Task 3: Non-null capture frames plus display rotation (`8e1381a`)
- [x] Task 4: Validated preprocessing engine (`79bd211`, `41aa126`, `b81e740`)
- [x] Task 5: Manual slot annotations and JSON codec (`1086b75`, `cab3d8d`)
- [ ] Task 6: Versioned calibration profiles — WIP `08ca414`, unreviewed and untested
- [ ] Task 7: Vision frame source and ViewModel
- [ ] Task 8: Compose Vision Lab
- [ ] Task 9: Vision documentation
- [ ] Task 10: CI, PR, APK artifact, implementation report

Resume from `docs/superpowers/plans/2026-11-06-sprint-1a-vision-foundation.md`,
section "Execution Checkpoint (2026-11-06)". Last verified unit-suite state: `cab3d8d`
(127/127 tests). No PR or CI run exists yet for this branch.

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
- [x] Task 9: README, ARCHITECTURE, SCREEN_CAPTURE, DATA_SOURCES, ADR-001
- [x] Task 10: Tests + lint + assembleDebug green; APK + manifest verified; report written
