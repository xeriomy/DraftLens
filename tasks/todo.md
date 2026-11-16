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
- [x] Sprint 1A.1 Vision Lab interaction redesign: explicit gesture state machine, contextual
      slot property editor, copy/paste/duplicate on an internal clipboard, delete control
      with undo, grouped slot list with stable keys, fullscreen editor, slot colours
      (`927e8e0`, `dffc12d`)
- [x] Sprint 1A.1 corrections: the delete control replaced the top-right resize handle, the
      long press opens the menu on its own timeout, the fullscreen editor lost zoom and fit
      controls, and the page no longer scrolls during annotation (`24a43af`, `593eeb4`)
- [x] Sprint 1A.2 bug fixes: paste reads from the clipboard with the target only anchoring
      placement, the property menu selects the same slot it shows, an early drag cancels
      the pending long press, one rotation snapshot per captured frame, validation failures
      surfaced in the property editor (`25307b8`)

Verification for Sprint 1A runs in GitHub Actions (ubuntu-latest, x86_64) because the
authoring host is aarch64 and AGP's `aapt2` is an x86_64-only binary, so no Gradle task that
touches resources can run locally. Draft PR: https://github.com/xeriomy/DraftLens/pull/2

Executed commands (CI, `./gradlew`):
- `./gradlew test` — 269 JVM unit tests, 0 failures, 0 errors, 0 skipped (538 executions, both variants)
- `./gradlew assembleAndroidTest` — instrumentation tests compiled, **not executed**
- `./gradlew lint` — strict lint, `abortOnError = true`
- `./gradlew assembleDebug` — debug APK, uploaded as the `draftlens-debug` artifact

Latest green run: https://github.com/xeriomy/DraftLens/actions/runs/43643640990
(head SHA `5ffb0c1`).

The frame sizing and rendering fix was confirmed by the user on a physical device before the
Sprint 1A.1 work started and was left unchanged. The Vision Lab interaction behavior in the
retained 1A.2 state was also smoke-tested on a physical device by the maintainer and confirmed
working: slot selection, movement and resizing; the long-press property menu; long-press-and-
drag creation; the original compact red X deletion control; copy, paste and duplicate;
fullscreen entry/exit and Android Back; annotation gestures isolated from page scrolling; and
portrait/landscape rendering. Device model, Android version, install source/APK artifact, test
date, logs and recordings were not supplied. The MediaProjection lifecycle matrix in
`docs/TESTING_PHYSICAL_DEVICE.md` remains **NOT RUN**.

Whole-branch review against the plan and spec: eight findings fixed in `691b9d2`, each with a
test that failed first. The remaining minor findings are listed in the implementation report
and the review ledger.

Physical-device verification of the Vision Lab interaction behavior is **maintainer-reported
PASS** (device details not supplied). The MediaProjection lifecycle matrix is **NOT RUN**.

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

## Sprint 1B: Hero catalog, slot extraction and recognition
- [x] Hero catalog module (Rone Arena primary, MLBBDex cross-check, atomic cache, offline)
- [x] Slot image extraction (all rotations, edge cases)
- [x] Recognition v0: dHash + color descriptor, explicit no-match, manual confirm/correct
- [x] Vision Lab card: sync, Identify, candidates, confirmed state, manual correction
- [x] Unit coverage for catalog, cache, extraction, matching, VM flows
- [x] CI green: 323 tests (646 executions), strict lint, assembleDebug, assembleAndroidTest compiled (run 43732908605, head e58611c; code head 7d0c903 run 43731484922)

## Sprint 1B review fixes + game-frame freeze (PR #3 follow-up)
- [x] Part A: freeze/resume from the notification through the capture architecture
      (explicit action to the running service; independently owned snapshot; images stay
      acquired+closed while frozen; one frozen frame; notification LIVE/FROZEN; in-app Resume;
      full-device capture guidance; POST_NOTIFICATIONS handling)
- [x] Part B: pixel rotation fixed to the FrameTransform convention; asymmetric non-square
      tests with independently calculated expectations
- [x] Part C: Android bitmap extractor validated and made pixel-exact; instrumentation tests
      for all rotations, edge crops, mismatches, source validity and snapshot ownership
- [x] Part D: visual-information gate (uniform/near-uniform/detail-free crops rejected);
      distinct InsufficientVisualInformation outcome and UI state
- [x] Part E: manual correction everywhere; failed clears reported honestly
- [x] Part F: cache freshness (URL-bound sidecar metadata; hash/structure/decode validation;
      URL change forces re-download; failed refresh keeps previous asset; HTTPS-only redirects)
- [x] Part G: atomic snapshot/asset writes; failure preserves previous snapshot; temp files swept
- [x] Part H: reference Bitmap dims read before recycling
- [x] Landscape capture fix (MLBB): authoritative captured-content resize via
      `onCapturedContentResize` + `DisplayManager` fallback to current window metrics;
      VirtualDisplay/ImageReader re-created at the reported size; generation numbers reject
      stale reader callbacks; plane conversion honors `image.cropRect` and padded rows;
      structured diagnostics in `CaptureSessionService`/`FramePipeline`/`VisionPreprocessor`
- [x] CI green on the new head for this revision (run 43773398149, head 5252016cd15c824009368e831ed499f24209980d; 840 unit tests; instrumentation compiled, not executed — no KVM on the runner)
- [ ] Physical-device smoke of the freeze workflow; on-device accuracy evaluation;
      threshold/heuristic calibration (pending)
- [ ] Physical-device verification of the landscape capture fix in MLBB (rows 23–32) (pending)

## Sprint 1C: App-Only Capture & Circular Floating Control
- [x] Task 1: Pure visibility gate (`CaptureVisibilityPolicy`) + JVM truth table (`d68f7bc`)
- [x] Task 2: Pipeline visibility gate; `onCapturedContentVisibilityChanged` (API 34+) wired into the CAPTURING guard (`6eaceb9`)
- [x] Task 3: Overlay position math (normalized/free-axis, edge snap) + JVM tests (`ffc4f5e`)
- [x] Task 4: Overlay enablement decision + DataStore keys (`48e0e11`)
- [x] Task 5: 52dp circular overlay button (`FloatingControlView`/`OverlayController`), long-press drag, tap toggle (`f88cce4`)
- [x] Task 6: Service attach/detach on every teardown path, rotation re-anchor, enable card + two-mode capture guidance, manifest `SYSTEM_ALERT_WINDOW` (`494df62`)
- [x] Task 7: Instrumented overlay attach/remove test (compile-only) (`c6edb52`)
- [x] Task 8: Visibility/freeze/stop sequence tests (`94662b3`)
- [x] Task 9: Documentation (sprint doc, capture docs, device matrix rows 33–43) (this branch)
- [x] Task 10: CI green record (run 43782436092 at `6e2d7b7`: 888 unit tests, 0 failures; APK sha256 `3f39…`; stacked PR #4)
Verification runs in GitHub Actions because the authoring host has no Android toolchain (below). Physical-device rows 33–43 are **NOT RUN**.
