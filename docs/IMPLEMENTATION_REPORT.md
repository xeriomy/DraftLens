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

## Sprint 1A.1 — Vision Lab interaction redesign

The annotation workflow was rebuilt around an explicit gesture model and a contextual
property editor. The frame sizing and rendering fix was confirmed working on a real device
by the user before this work started and was left untouched.

### The interaction model

`AnnotationInteraction` is a pure state machine that decides what every gesture means, so
two gestures can never compete and the rules are unit-tested:

| Interaction | Action |
|---|---|
| Short tap on a rectangle | select it |
| Drag inside a selected rectangle | move it |
| Long press on a rectangle | open its property editor |
| Drag a resize handle | resize it |
| Long press on empty image space, then drag | draw a new slot |
| Short tap on empty image space | nothing |
| Tap the X control | delete it |

A tap never moves anything, a drag on an unselected rectangle only selects it, a stationary
long press is not a move, and a drag on empty space never creates a rectangle.

### The contextual property editor

A long press opens a compact editor next to the slot: type, team, index and an optional
label, plus **Copy**, **Paste** and **Duplicate**. It positions itself inside the window, so
it is never clipped off-screen in either orientation, and it no longer occupies a permanent
part of the canvas. The old bottom property controls and the "Add slot" card with its
hardcoded rectangle are gone; creating a slot is now a long press and a drag on the image.

Copy and paste use the application's own clipboard, never the Android system clipboard. A
pasted slot gets a new unique id, is offset a little, stays inside the image and takes the
next free index in its `(type, team)` scope. The source slot is always preserved.

### Deletion and undo

The selected slot shows an X control at its top-right corner, drawn in red and distinct from
the resize handles. Deleting uses the slot's stable id. **Undo** covers a deletion, a
creation and a move or resize; a deletion is undone by restoring the slot with its own id.

### The slot list

The list is grouped into ALLY · BAN, ENEMY · BAN, ALLY · PICK, ENEMY · PICK and GLOBAL.
Every row is keyed by `key(slot.id)`, so the list never relies on positions. Tapping a row
selects that rectangle, and selecting a rectangle highlights its row.

Investigating the deletion path: `SlotAnnotationStore.delete(id)` already removed by id and
was never deleting the first item. The real risk was Compose list identity, which is now
removed by keying every row with the slot's own id; a regression test deletes the second of
three slots and checks that the other two survive.

### Fullscreen editor

*Fullscreen* opens the image on its own screen in either orientation, fitted to the screen,
with pinch-to-zoom and pan, *Fit to screen* and *Reset zoom*, a compact control row and a
*Done* action. The image and the overlay are drawn under the same transform and pointer
positions are mapped back through its inverse, so the overlay stays aligned while zooming.
The gesture hint appears when the editor is entered and can be dismissed.

### Slot colours

`ALLY` is blue, `ENEMY` is red and `GLOBAL` is neutral grey, and every rectangle also
carries a readable label, so colour is never the only way to tell teams apart.

### Unchanged on purpose

The frame sizing and rendering fix, the letterbox rejection, the source-normalized
coordinate semantics, the display-rotation handling, the calibration codecs and the capture
lifecycle were not rewritten.

### Sprint 1A.1 corrections

Four corrections were applied on top of the interaction rework.

1. **The delete control replaces the top-right resize handle.** The top-right handle is gone
   and the X sits on exactly that corner, so the corner carries one control and not two.
   The remaining handles — top-left, bottom-left, bottom-right — still change the width and
   the height from either side, so nothing became unreachable.
2. **The long press opens the property menu immediately.** The long press is detected on its
   own timeout instead of waiting for a pointer event, so the menu no longer needs the
   finger to move first, and a small movement afterwards neither delays nor repeats it.
3. **Fullscreen editing was simplified.** Pinch-to-zoom, pan, *Fit to screen* and *Reset
   zoom* were removed together with the view-transform state they needed. The fullscreen
   entry is now a compact icon, the editor fills the screen, a collapse icon leaves it, and
   Android Back leaves the editor before the Vision Lab.
4. **The page no longer scrolls during annotation.** The canvas takes the pointer from the
   first touch, so moving, resizing and drawing a slot never scrolls the page. Scrolling
   stays available everywhere else, and the fullscreen canvas is not inside a scrolling
   parent at all.

---

## Verification actually performed

Everything below ran in **GitHub Actions** (`ubuntu-latest`, x86_64, JDK 17). The authoring
host is aarch64 and AGP 8.6.1 publishes only an x86_64 `aapt2`, so no Gradle task that
touches resources can run there — verified as
`Cannot run program ".../aapt2": error=2`. CI is the build of record.

### Latest green run

| | |
|---|---|
| Run | **43514508049** — <https://github.com/xeriomy/DraftLens/actions/runs/43514508049> |
| Tested head SHA | `593eeb43315ea40a27d55c248bc914aeb3b8a3c8` |
| Unit tests | 258 tests · 0 failures · 0 errors · 0 skipped (516 executions, debug + release variants) |
| Other gates | `assembleAndroidTest`, strict `lint`, `assembleDebug` — all green |

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
| `42926731121` | `007621f` | success | profile-controls hint and documentation |
| `42942937802` | `d978309` | failure (expected) | canvas-fix red: the new pins had no implementation |
| `42943163574` | `01603de` | failure | 1 test used an out-of-range `NormalizedPoint`, which the type forbids |
| `42943730823` | `4c42513` | success | canvas rendering and workspace fixes green |
| `42953552163` | `927e8e0` | failure | the interaction rework did not compile: wrong pointer imports and a rectangle type mismatch |
| `42954140054` | `0762e96` | failure | 1 test expected a duplicate index that was not actually taken |
| `42955353155` | `dffc12d` | success | interaction rework green |
| `43513876694` | `24a43af` | failure | the annotation icons gave a `StrokeCap` where a `StrokeJoin` was expected |
| `43514508049` | `593eeb4` | success | corrections green |
| `42943730823` | `4c42513` | **success** | canvas rendering and workspace fixes green |

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

## Canvas rendering and workspace fixes (third pass)

A user reported that drawing rectangles was restricted to a small central area after the
letterbox fix. Two causes, both fixed.

1. **The bitmap was drawn at its raw pixel size.** `drawOrientedBitmap()` passed the
   bitmap's source pixel dimensions straight through as the Compose destination size, while
   the geometry mapped annotations against the smaller fitted rectangle — so the pixels and
   the overlay did not share the same bounds. `OrientedBitmapLayout` now computes the draw
   destination: the oriented dimensions for all four rotations, one uniform scale into the
   fitted rectangle, the bitmap centred on `fitted.center`, and the rotation applied around
   that same centre. The image is never stretched, and the axis-aligned bounds of the
   rotated bitmap are exactly the fitted rectangle. The preview card and the annotation
   canvas use the same renderer.
2. **The workspace was a fixed 260 dp.** The canvas is now sized from the oriented aspect
   ratio: a portrait capture gets a tall workspace that grows inside the scrollable page, a
   landscape capture keeps a 240 dp minimum. The frame is never cropped and never stretched.

Coordinate behaviour is unchanged and still pinned by tests: a letterbox touch is rejected at
gesture start, a tap outside the fitted image is rejected, an in-progress move or resize is
clamped to the image edge, and the create drag is deterministic.

`FramePipeline.displayRotation` is now `@Volatile`, because it is written by the display
listener's thread and read by the ImageReader thread. The pixel correction stays separate
from the display metadata and remains `ROTATION_0` — no double rotation.

The Vision Lab now explains its controls in place: *Freeze* locks the current frame before
annotation, *Resume* returns to live frames, a *slot* is a manually marked region where a
ban/pick hero portrait is expected, *Process Frame* resizes and prepares the frame only
because no recognition exists yet, and a *calibration profile* saves and restores normalized
slot coordinates for matching screen layouts. The "Detection: not implemented" notice stays.

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
