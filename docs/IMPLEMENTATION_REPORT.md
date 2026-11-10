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

### Latest green run

| | |
|---|---|
| Run | **42926731121** — <https://github.com/xeriomy/DraftLens/actions/runs/42926731121> |
| Tested head SHA | `007621f808182355103c884582b31145040c3837` |
| Unit tests | 199 tests · 0 failures · 0 errors · 0 skipped (398 executions, debug + release variants) |
| Other gates | `assembleAndroidTest`, strict `lint`, `assembleDebug` — all green |
| Artifact | `draftlens-debug`, 10 162 789 bytes |
| Artifact digest | `sha256:c8c88e09b2399539107ad3156683ca82d39011263f755fa7681b545ee18ee2ba` |
| APK SHA-256 | `b13fcc80e816bbdee5a60b4e4f30333bbad347c6f35e2513c9a14f8657dae247` (`app-debug.apk`) |

### Run history

| CI run | Head SHA | Result | What it showed |
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
| `42918290133` | `691b9d2` | success | review fixes green |
| `42918914137` | `da35ca0` | success | documentation-only |
| `42919023498` | `8d3a330` | success | documentation-only |
| `42919485331` | `3e9e88c4be76933f0c6cc060f51acf3ab0444b14` | success | pre-fix state; artifact digest `sha256:ae7264df1ac2ec1f9c7091baf0ad0bb865f35db8f65d84bcb86bbfe398633eae`, APK SHA-256 `c9936bdbf64863c6baec7ceff227ea73d2b8be9a32593c748ef8d33321de710f` |
| `42925456760` | `1789d44a37143fcb59fbdefa6a43b876a9022773` | failure (expected) | focused-fix red: the new pins had no implementation |
| `42925901901` | `8f046c0` | failure | 5 tests still assumed the old profile frame policy |
| `42926242828` | `313775d` | success | focused fixes green |
| `42926731121` | `007621f` | **success** | final: profile-controls hint + documentation |

Green run tasks: `./gradlew test`, `./gradlew assembleAndroidTest`, `./gradlew lint`
(strict, `abortOnError = true`), `./gradlew assembleDebug`, APK uploaded as the
`draftlens-debug` artifact, and the packaged manifest re-inspected for the foreground-service
permissions and the `mediaProjection` service type.

**Instrumentation tests were compiled, not executed.** No device and no emulator was
available.

## Focused review fixes (second pass)

Six areas were reworked on top of the first green state. Each has a regression test that
failed before the fix.

1. **Display rotation metadata updates.** `CaptureSessionService.recreateDisplay()` used to
   return early when the capture size was unchanged, so a rotation that kept the size was
   never recorded. The rotation is now updated independently of the ImageReader resize, a
   `DisplayManager.DisplayListener` watches the default display (and is unregistered during
   cleanup), and `FramePipeline` reads the current rotation when a frame is emitted instead
   of capturing the value from start-up. The pixel correction stays `ROTATION_0`: MediaProjection
   buffers are display-oriented, so no double rotation is introduced.
2. **Annotation canvas letterbox.** `CanvasGeometry.canvasToNormalized()` rejects a point
   outside the fitted image rectangle instead of clamping it onto the image edge, so a touch
   on a letterbox band selects nothing and cannot start a drag.
3. **Clear cancels in-flight processing.** `VisionLabViewModel.clear()` bumps the processing
   generation, cancels the job and drops the references, so a late result cannot restore
   itself.
4. **Calibration slot semantics.** `SlotValidator` is now the single validator used by both
   the annotation codec and the calibration codec. A stored document with an unusable slot —
   too small a rectangle, an index below 1, a duplicate slot id, a duplicate
   `(type, team, index)` — or with a duplicate profile id is reported as `Corrupt` and is
   never silently replaced. Unsupported-schema refusal and the delete-only recovery are
   unchanged.
5. **Profile frame policy.** Saving, updating and applying a profile now require a frozen
   frame; a moving live frame is never measured.
6. **Documentation and CI.** This report, `docs/VISION_TESTING.md` and PR #2 name the exact
   green run, its head SHA and the artifact digest.

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
