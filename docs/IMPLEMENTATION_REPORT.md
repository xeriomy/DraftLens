# DraftLens — Sprint 1A Vision Foundation Report

**Branch:** `sprint-1-vision-foundation` · **PR:** #2 (draft, **not merged**)

## What Sprint 1A delivers

An image-bearing foundation for vision work. Frames carry pixels, geometry is tested,
preprocessing validates before it renders, and a Vision Lab lets a user place manual slot
annotations and save calibration profiles.

**No recognizer.** No OCR, no hero identification, no automatic ban/pick detection, no
confidence values, no ML model, no network client, no screenshot persistence. The Vision Lab
states "Detection: not implemented"; manual slots are calibration data, never detections.

## Changed areas

| Area | Files | Notes |
|---|---|---|
| Frame contract | `vision/model/*`, `models/CapturedFrame.kt` | non-null payload; `displayRotation` recorded, `rotation` left at `ROTATION_0` for MediaProjection buffers |
| Geometry | `vision/geometry/FrameTransform.kt` | source→oriented→crop→processed round trips, crop and downscale |
| Preprocessing | `vision/preprocessing/*` | validation, aspect-preserving sizing, single-pass Android render, honest failure codes |
| Annotations | `vision/annotation/*` | `SlotAnnotationStore` with stable ids, duplicate/overlap/bounds validation, strict JSON codec |
| Calibration | `vision/calibration/*`, `datastore/CalibrationProfileDataStore.kt` | 1 % aspect tolerance, exact orientation match, unsupported-schema refusal, delete-as-escape-hatch |
| Capture adapter | `capture/CaptureVisionFrameSource.kt` | the only code that knows about both capture and Vision |
| Vision Lab | `feature/visionlab/*` | preview, freeze/resume, explicit processing, annotation canvas, text slot editor, profile controls |
| Tabs | `MainActivity.kt`, `DraftLensApp.kt`, `feature/home/DebugCaptureScreen.kt` | Material 3 `TabRow`; capture stays alive across tabs |

## Verification actually performed

Everything below ran in **GitHub Actions** (`ubuntu-latest`, x86_64, JDK 17). The authoring
host is aarch64 and AGP 8.6.1 publishes only an x86_64 `aapt2`, so no Gradle task that
touches resources can run there — verified as
`Cannot run program ".../aapt2": error=2`. CI is the build of record.

**Final green run:** <https://github.com/xeriomy/DraftLens/actions/runs/42918290133>
(commit `691b9d2`, 2026-11-10).

| CI run | Commit | Result | What it showed |
|---|---|---|---|
| `42905335945` | `34ebb10` | success | baseline before this sprint's work |
| `42905781209` | `b84b69f` | failure (expected) | Task 6 red: the store had no injectable constructor |
| `42906245805` | `85f04c8` | success | Task 6 green |
| `42906470342` | `473b632` | failure (expected) | Task 7 red: the seam and ViewModel did not exist |
| `42907112482` | `fedaa4e` | failure | nested test fakes could not reach an outer helper |
| `42907516553` | `82c330a` | failure | `advanceUntilIdle` does not drive `backgroundScope` collectors |
| `42907916398` | `0c7d286` | failure | the stale-result fake blocked by call order while the two jobs race |
| `42908409411` | `606206d` | failure | the first request can be cancelled before it reaches the preprocessor |
| `42908944687` | `b4c8c78` | success | Task 7 green |
| `42909440502` | `303b818` | failure | `MainActivity`: `this` resolved to `ColumnScope`, not the Activity |
| `42909895639` | `45e2f2c` | success | Task 8 green — Vision Lab UI compiles, lints and assembles |
| `42917700880` | `90cb807` | failure (expected) | review-fix red: the new pins had no implementation |
| `42918290133` | `691b9d2` | **success** | final green: 354 test executions (167 tests in both the debug and release variants), 0 failures |

Green run tasks: `./gradlew test` (167 unit tests, 0 failures, 0 errors, 0 skipped),
`./gradlew assembleAndroidTest`, `./gradlew lint` (strict, `abortOnError = true`),
`./gradlew assembleDebug`, APK uploaded as the `draftlens-debug` artifact, and the packaged
manifest re-inspected for the foreground-service permissions and the `mediaProjection`
service type.

**APK artifact:** `draftlens-debug`, from run `42918290133`
(<https://github.com/xeriomy/DraftLens/actions/runs/42918290133> → Artifacts).

**Instrumentation tests were compiled, not executed.** No device and no emulator was
available.

## Physical-device status

- **Sprint 1A: NOT RUN.** The Vision Lab, the annotation canvas, overlay alignment on a real
  screen and the calibration flow were not exercised on hardware.
- **Sprint 0: PARTIAL, maintainer-reported.** Capture and live preview were reported working
  on a physical device; device model, Android version, frame counts and measurements were not
  supplied, so full PASS is not claimed.
- The expanded lifecycle matrix in `docs/TESTING_PHYSICAL_DEVICE.md` remains **NOT RUN**.

## Open limitations

- No recognition, OCR, recommendations, overlays, API sync, or hero data.
- No automatic processing: the user must press *Process Frame*.
- No automatic profile application; an incompatible profile is refused with the expected and
  actual ratio and orientation.
- Applying a profile regenerates slot ids (the store owns id generation).
- Native-memory reclamation after reference clearing is not guaranteed and is not a strict
  one-bitmap bound.
- Self-capture: no `FLAG_SECURE` by maintainer preference; the user can choose "Entire
  screen" or "A single app".
- OEM/background/store policies and review gates remain for release.
- Physical-device matrix needs execution before any store submission.

## Next

1. Execute the physical-device matrix, including the Vision Lab workflow.
2. Recognizer seam: a real hero-ID lookup over the slot images, with no fabricated results.
3. Recommendation scoring over detected slots, still behind an explicit user action.
4. Only then: floating overlay and API sync.

---

# DraftLens — Sprint 0.1.3 Capture Lifecycle Report

**Branch:** `sprint-0-android-foundation` · **PR:** #1

## Physical-device status
- **Baseline maintainer-reported smoke (Sprint 0): PARTIAL.** Capture and live preview were reported working on a physical device. Device model, Android version, frame counts and measurements were not supplied, so full PASS is not claimed.
- Expanded physical-device lifecycle matrix in `docs/TESTING_PHYSICAL_DEVICE.md` remains **NOT RUN** except the one PARTIAL row. Expanded scenarios not claimed executed.

## Verified fixes (Sprint 0.1 set)
- AGP **8.6.1**, Gradle 8.7, Kotlin 2.0.20, compile/target SDK 35, minSdk 26
- Strict lint enforced and used as CI gate
- Foreground service promoted synchronously with `mediaProjection` type before projection acquisition
- `START_NOT_STICKY`; null `Intent` is ignored; fresh consent per session
- `CaptureStartupExecutor` ordering: promotion → projection → initialization
- One `VirtualDisplay` per session; rotation uses `setSurface()` + `resize()`
- Capture dimensions prefer `WindowManager.maximumWindowMetrics` on API 30+, fallback below
- `VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR`
- Monotonic sampling; wall-clock retained separately; UI reports frame-processing latency only
- Latest frame is published by `CaptureStateHolder`; Compose bitmap is not recycled while rendering
- Termination paths are unified through `terminateSession()`:
  - normal Stop → IDLE
  - projection revocation/startup failure → ERROR, with frame cleared and metrics reset
  - task removal/process end uses the same state cleanup
  - permission and dimensions are cleared on termination, ERROR is preserved
- Consent intent is requested fresh at Start time

## Tests covered by CI
- Startup order and failure propagation
- Target sampled time versus wall clock: monotonic reset, pause/resume semantics
- Clear/retained-frame removal tests using shared `terminateSession()`
- Session state reset and start-blocking rules
- Projection consent-result validation
- Detection stubs explicitly `NotImplemented`

## CI (source of truth)
Local Gradle assembly is unavailable on this host because Android SDK build tools are x86_64 and host is aarch64. GitHub Actions is the build of record for `test`, `lint`, `assembleAndroidTest`, `assembleDebug`, APK artifact upload and merged-manifest inspection.

Note: invocation `./gradlew assembleDebug` is intentionally not claimed locally; CI output is the deliverable.

## Open limitations
- No recognition, OCR, recommendations, overlays, API sync, or hero data
- Analysis frame pipeline not added yet; preview is one full-size frame
- Self-capture: no `FLAG_SECURE` by maintainer preference; user can choose “Entire screen” or “A single app”
- Native-memory reclamation after reference clearing is not guaranteed and is not a strict one-Bitmap bound
- OEM/background/store policies and review gates remain for release
- Physical-device matrix needs execution before store release
