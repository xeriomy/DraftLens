# Sprint 0 Task List

## Sprint 1A: Vision Foundation
- [x] Task 1: Vision model and geometry value types (`a2731d3`)
- [x] Task 2: Rotation-aware geometry and round trips (`357a40f`)
- [x] Task 3: Non-null capture frames plus display rotation (`8e1381a`)
- [x] Task 4: Validated preprocessing engine (`79bd211`, `41aa126`, `b81e740`)
- [x] Task 5: Manual slot annotations and JSON codec (`1086b75`, `cab3d8d`)
- [x] Task 6: Versioned calibration profiles — WIP `08ca414` reviewed and completed in
      `b84b69f` + `85f04c8` (red CI `42905781209`, green CI `42906245805`)
- [x] Task 7: Vision frame source and ViewModel (`fedaa4e`, `ec24a9a`)
- [x] Task 8: Compose Vision Lab (`6ad2287`, `45e2f2c`, `691b9d2`)
- [x] Task 9: Vision documentation (`303b818`, `da35ca0`)
- [x] Task 10: CI, PR, APK artifact, implementation report
- [x] Final whole-branch review; findings fixed in `691b9d2` with a failing test each
- [x] Focused review fixes: display rotation updates, canvas letterbox, clear cancels
      processing, calibration slot validation, frozen-frame profile policy, docs and CI
      (`8f046c0`, `313775d`, `007621f`)
- [x] Canvas rendering and workspace fixes: bitmap scaled into the fitted rectangle, canvas
      sized by the oriented aspect ratio, `@Volatile` display rotation, control explanations
      (`01603de`, `4c42513`)

Verification for Sprint 1A runs in GitHub Actions (ubuntu-latest, x86_64) because the
authoring host is aarch64 and AGP's `aapt2` is an x86_64-only binary, so no Gradle task that
touches resources can run locally. Draft PR: https://github.com/xeriomy/DraftLens/pull/2

Executed commands (CI, `./gradlew`):
- `./gradlew test` — 216 JVM unit tests, 0 failures, 0 errors, 0 skipped
- `./gradlew assembleAndroidTest` — instrumentation tests compiled, **not executed**
- `./gradlew lint` — strict lint, `abortOnError = true`
- `./gradlew assembleDebug` — debug APK, uploaded as the `draftlens-debug` artifact

Latest green run: https://github.com/xeriomy/DraftLens/actions/runs/42943730823
(head SHA `4c42513`). Artifact digest
`sha256:dd501ac7fbe282de56c318ff581f77b76813b087db8d8b8944a041065deaf7c4`; APK SHA-256
`692a9221afa57763e6a39bf840d1fca409e3c433f5e69d75ba760af86ff3fe7f`.

Whole-branch review against the plan and spec: eight findings fixed in `691b9d2`, each with a
test that failed first. The remaining minor findings are listed in the implementation report
and the review ledger.

Physical-device verification of the Vision Lab is **pending**. No emulator or device was
available in this environment.

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
