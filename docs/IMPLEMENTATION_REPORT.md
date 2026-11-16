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
Opening the menu selects the slot first, so the menu and the selection always agree. A type, team or index change that
would produce a duplicate logical identity is refused with the reason shown next to the
controls, the slot keeps its previous values and the editor stays open; invalid index text is
flagged inline.

Copy and paste use the application's own clipboard, never the Android system clipboard. The
copied slot is always the source: a pasted slot gets a new unique id, takes the copied
slot's type, team, label and rectangle dimensions, is offset a little from the anchor (the
target slot when paste is invoked from its menu, the copied slot otherwise), stays inside
the image and takes the next free index in the copied slot's `(type, team)` scope. The
source and the target are never modified or deleted.

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

*Fullscreen* opens the image on its own screen in either orientation: a compact expand icon
in the corner of the canvas card opens it, the image and canvas fill the full available
phone workspace, and a compact collapse icon in the corner leaves it. There is no zoom, no
pan, no *Fit to screen* and no *Reset zoom* — the frame is fitted into the screen and the
slot overlay stays aligned with it. Android Back leaves the fullscreen editor first, and
only then the Vision Lab. The gesture hint appears when the editor is entered and can be
dismissed.

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

### Sprint 1A.2 — bug-fix pass

Six focused fixes on top of 1A.1, each with regression tests:

1. **Paste reads from the clipboard.** `pasteSlot()` used to read the target slot as the
   source, so pasting from another slot's menu duplicated that slot instead of the copied
   one. The clipboard slot is now always the source of type, team, label and rectangle
   dimensions; the target only anchors the placement, and the pasted rectangle is offset from
   the anchor (or from the copied slot when no target exists) and kept inside the image.
   The copied source and the target are never modified.
2. **The menu and the selection are the same slot.** `openSlotMenu()` validates the id,
   selects that exact slot and only then opens the menu, so the outline, the handles, the
   delete control, the list highlight and the menu always refer to one slot.
3. **An early drag cancels the pending long press.** Moving past the touch slop before the
   timeout now cancels it: a drag on an unselected slot only selects it, a drag on a
   selected slot moves it, and a drag on empty space never turns into slot creation. Once
   the timeout fires, the deadline is abandoned, so later movement can neither delay nor
   re-open the menu. The 450 ms threshold is unchanged.
4. **One rotation snapshot per captured frame.** `FramePipeline.displayRotation` stays
   `@Volatile`, and the ImageReader thread reads it once per frame; the same local value is
   stamped on the frame and used for the orientation correction, so the two can never
   disagree. The MediaProjection pixel-orientation behaviour is unchanged.
5. **Validation failures are visible.** A type, team or index change refused because of a
   duplicate logical identity now shows the reason next to the controls, keeps the slot's
   previous values and keeps the editor open. Invalid index text is flagged inline in the
   index field.
6. **This report.** The fullscreen section no longer describes the removed pinch-to-zoom,
   pan, *Fit to screen* and *Reset zoom* controls.

Note: an earlier revision of this pass reworked the delete control (a density-aware 48 dp
touch target drawn outside the canvas clip region) and made slot creation open the property
editor automatically. Both were reverted at the maintainer's request and are **not** part of
the current build; the delete control and the creation flow behave exactly as they did in
1A.1.

---

## Verification actually performed

Everything below ran in **GitHub Actions** (`ubuntu-latest`, x86_64, JDK 17). The authoring
host is aarch64 and AGP 8.6.1 publishes only an x86_64 `aapt2`, so no Gradle task that
touches resources can run there — verified as
`Cannot run program ".../aapt2": error=2`. CI is the build of record.

### Latest green run

| | |
|---|---|
| Run | **43643640990** — <https://github.com/xeriomy/DraftLens/actions/runs/43643640990> |
| Tested head SHA | `5ffb0c193cb84da77fb49984c81e219178c7ef3f` |
| Unit tests | 269 tests · 0 failures · 0 errors · 0 skipped (538 executions, debug + release variants) |
| Other gates | `assembleAndroidTest`, strict `lint`, `assembleDebug` — all green |
| APK artifact | `draftlens-debug` · 10 215 534 bytes · digest `sha256:fef08a8b0233e78b30fb18094fd31e90c17557766954fc8fbf3d6a2d20db8a8f` · APK SHA-256 `937541942ff13d54b22d5fae28dbb7343ecb330ccc7e8b0d16f5048dbec758c5` |

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
| `43515376361` | `e1aca69` | **success** | final: corrections plus a small cleanup |
| `43516005404` | `c9a2cef` | **success** | documentation-only pointer before the 1A.2 pass |
| `43641588245` | `25307b8` | **success** | Sprint 1A.2 bug fixes green |
| `43642050380` | `f4df20f` | **success** | documentation-only pointer to the 1A.2 run |
| `43643640990` | `5ffb0c1` | **success** | 1A.2 with the delete control and creation flow reverted to 1A.1 |
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

- **Sprint 1A Vision Lab interaction: maintainer-reported PASS (device smoke).** The Vision
  Lab interaction behavior in the retained 1A.2 state was confirmed working on a physical
  Android device: slot selection, movement and resizing; long-press property menu;
  long-press-and-drag creation; the original compact red X deletion control; copy, paste and
  duplicate; fullscreen entry/exit and Android Back; annotation gestures isolated from page
  scrolling; and existing portrait/landscape rendering. Device model, Android version,
  install source/APK artifact, test date, logs and recordings were **not** supplied, so they
  are not recorded here and no measurements are claimed.
- **MediaProjection lifecycle matrix remains NOT RUN.** The repeated start/stop, projection
  revocation, app-process interruption, rotation/resize, background stability and sustained
  capture scenarios in `docs/TESTING_PHYSICAL_DEVICE.md` are untested.
- **Sprint 0: PARTIAL, maintainer-reported.** Capture and live preview were reported working
  on a physical device; device model, Android version, frame counts and measurements were not
  supplied, so full PASS is not claimed.

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

## Sprint 1B — review fixes and game-frame freeze (current)

Branch `sprint-1b-hero-recognition`, PR #3 (draft, **not merged**). The full report is
`docs/sprints/SPRINT_1B_HERO_RECOGNITION.md`; this section records what changed for the
review-fix pass and how it was verified.

### Changes made

- **Part A — Freeze from the notification.** New `CaptureFreeze` bridge (service command +
  in-app button are one code path). The ongoing capture notification now exposes
  **Freeze current frame** (LIVE state) / **Resume live capture** (FROZEN state) plus
  (superseded by the Sprint 1C follow-up: the notification now carries **Pause**/**Resume**)

  **Stop**. The command goes to the running service via an explicit, immutable
  `PendingIntent.getService` (`ACTION_FREEZE` / `ACTION_RESUME_LIVE`); it never launches an
  Activity, never re-promotes the service, never re-requests consent and never creates a
  VirtualDisplay. `FramePipeline` gates publication on a `freezeGate` lock shared with the
  image listener: while frozen, images are still acquired and closed (the reader stays
  healthy) but not converted or published; a freeze that lands mid-conversion wins and the
  in-flight frame is dropped. `FrameSnapshots.copyOf` copies pixels (bitmap `copy()` or
  `IntArray.copyOf`) preserving dimensions, rotation, display rotation, timestamps and the
  capture-latency metadata; the live pipeline never shares a payload buffer with a
  snapshot; only one frozen frame is kept; a new capture session drops it; an Android-initiated
  stop keeps it in process memory for editing (not across process death). The Vision Lab
  in-app Freeze/Resume went through the same path. Guidance for full-device capture is in
  the capture screen, the Vision Lab card and the README; `POST_NOTIFICATIONS` is checked
  and surfaced (request on Android 13+, settings deep-link otherwise); the notification is
  re-posted whenever the frozen state changes.
- **Part B — Pixel rotation fixed.** `PixelSlotImageExtractor` now follows
  `FrameTransform.sourceToOriented()` exactly; tests rewritten with asymmetric non-square
  fixtures and hand-computed expected pixel orders for all four rotations, crops touching
  every edge, minimum-size and refused rectangles, and source-immutability checks.
- **Part C — Android extractor.** Validates Bitmap dimensions against
  `OriginalFrame.sourceWidth/sourceHeight`; rotation rewritten to pixel-exact mappings;
  source Bitmap never mutated/recycled; every temporary created in `extract()` is recycled
  exactly once; failures recycle their temporaries. Instrumented tests added (all four
  rotations, non-square edge crops, payload mismatch, source validity, minimum sizes,
  recycled-bitmap refusal, bitmap snapshot ownership).
- **Part D — Blank rejection.** New pure-Kotlin `VisualInformationAnalyzer` (luma/color
  variance, edge density, meaningful differences, minimum dimension 12×12) runs before
  matching. Deterministic, no Android APIs. New distinct outcome
  `InsufficientVisualInformation` and `INSUFFICIENT_INFO` UI state with
  "Not enough visual information. Select a clearer hero crop." — uniform/near-uniform/
  detail-free crops never produce candidates. Thresholds are conservative heuristics,
  documented as uncalibrated for MLBB.
- **Part E — Manual correction everywhere.** Manual name search and confirmation are now
  reachable from CANDIDATES, NO_MATCH, FAILED, INSUFFICIENT_INFO and CONFIRMED.
  **Change…** in CONFIRMED opens the manual correction search instead of re-running
  Identify; a candidate can be corrected without clearing first. Failed persistence
  operations are reported and never shown as success (confirm already reported; clear did
  not — fixed with tests).
- **Part F — Cache freshness.** Per-asset `*.meta` sidecar records source URL, SHA-256,
  size, content type. Serve-time validation: size cap 4 MiB, sidecar URL == catalog URL,
  size and SHA-256 match, magic bytes, container structure (PNG chunks + CRCs, JPEG/GIF
  trailers, WebP RIFF), content type, plus a real `BitmapFactory` decode on Android
  (`AndroidReferenceImageValidator`, wired in `DraftLensApp`). Changed URLs force
  re-download; the old asset is never served under a changed URL; a failed refresh keeps
  the previous asset (unchanged URL) and never exposes it as the new one. Redirects are
  followed manually with per-hop HTTPS re-validation (`HeroRedirectPolicy`); HTTP
  downgrades and non-HTTP schemes refused. Snapshot writes flush/sync and `ATOMIC_MOVE`.
- **Part G — Atomic persistence.** `writeAtomically()` is now: temp sibling in the same
  directory → flush + `FileDescriptor.sync()` (best effort) → `Files.move(ATOMIC_MOVE,
  REPLACE_EXISTING)` → on any failure the temp file is deleted and the previous target is
  preserved. No direct-write fallback. Stale `*.tmp` siblings older than an hour are swept
  on init. Failure-path tests added.
- **Part H — Reference loader.** Dimensions are read before recycling; pixel reads
  complete before release; exceptional paths recycle.
- **Part I — Matcher honesty.** The matcher remains clearly labelled a prototype ranking
  baseline; weights and the 0.55 threshold are marked uncalibrated; an honest evaluation
  section (synthetic fixtures, blank rejection, top-1/top-3 methodology, false-match
  methodology, real-screen status, known limitations) is in the Sprint 1B report. No
  production-accuracy or probability claims anywhere.
- **Part J — Scope.** Sprint 1A behavior preserved as-is (compact X delete control, 450 ms
  long-press, early-drag cancellation, paste semantics, fullscreen without zoom/fit, gesture
  isolation, portrait/landscape rendering, MediaProjection lifecycle apart from the freeze
  integration).
- **Part K — Verification.** See the exact run below. Instrumented tests run on an
  emulator job when available.

### Verification actually performed (this revision)

Docs-only commits after the code freeze do not change the build; the exact final head and its CI run are recorded in PR #3's description.

- **Unit tests, debug and release: 792 executions, 0 failures, 0 errors, 0 skipped** —
  <https://github.com/xeriomy/DraftLens/actions/runs/43745075261>,
  head SHA `7a839d1422ee5ba53f65a78ef746d323091d3b40`.
- strict `lint` (abortOnError = true) — green; `assembleDebug` — green; public artifact
  `draftlens-debug` 10 362 309 bytes, digest
  `sha256:53c07492517fdfc2837ebb179393e526d6b397db2146228aa853eb4914221677`.
- `assembleAndroidTest` — instrumentation sources compile; **the suite was not executed**.
  The CI runner has no KVM access: the default API-30 image did not boot within the
  timeout, and the `aosp_atd` image boots but Gradle reports it as an "Unknown API Level"
  and skips it. The emulator job was removed rather than left flaky; instrumented tests
  must run on a physical device or a KVM-enabled emulator. Nothing here is claimed as
  executed.

### Claims NOT made

- No production-grade recognition, no calibrated confidence, no MLBB accuracy claim, no
  accuracy-based-on-synthetic-fixtures claim. The threshold and weights are uncalibrated.
- No physical-device freeze verification yet (rows 14–22 in
  `docs/TESTING_PHYSICAL_DEVICE.md`). Device model, install source, test date and logs are
  not recorded; the MediaProjection lifecycle matrix remains NOT RUN.
- The instrumentation suite does not and cannot simulate the consent UI; the notification
  action end-to-end (tap in the shade while the game is foreground) needs a device.
- No screenshots or captured frames are committed; none were uploaded anywhere.

### Remaining acceptance criteria

- Physical-device smoke of the freeze workflow (notification tap while MLBB is foreground;
  Vision Lab displays the pinned frame; Resume returns to live frames; stop keeps the pinned
  frame for editing).
- On-device accuracy evaluation with manually labeled samples; threshold and
  visual-information-heuristic calibration against real captures.
- Catalog entries loaded at device sync time recorded here when a device sync happens.
- PR #3 stays Draft and unmerged; do not merge without this verification.

Historical green runs from before the review-fix pass (kept as history; do not cite these
as the current verification): `e58611c` — run 43732908605 (646 executions); `7d0c903` — run
43731484922 (same totals).

## Critical fix — landscape capture shows only part of the game (device-reported)

Reported by the maintainer on a physical phone: a normal screenshot captures MLBB correctly,
but the MediaProjection preview/capture shows only part of the game with the rest black;
portrait apps capture fine. (PR #3, still Draft and unmerged.)

### Root cause analysis

The visible path from Android's capture-resize callback to the frame on screen was inspected:
`CaptureSessionService` → `FramePipeline` → `ImageReader` → `imageToPayload` →
`PixelArrayFramePreprocessor`/`AndroidFramePreprocessor` → Vision Lab.

Three defects combined, any one sufficient to produce the symptom:

1. **The capture size was fixed at session start and never re-derived.** The only resize
   trigger was `MediaProjection.Callback.onCapturedContentResize` (API 34+), so on pre-34
   devices — and on API 34+ whenever the callback does not fire for whole-screen captures —
   rotating to landscape left the VirtualDisplay at the portrait session-start size forever.
   The mirrored landscape content was clipped/letterboxed into the stale buffer and the rest
   stayed black. The `DisplayManager` listener only updated rotation *metadata*, never size.
2. **Late frames from a released reader could publish into the replacement reader's stream**
   (no generation guard) — stale frames could land on top of fresh ones after a resize.
3. **`image.cropRect` was ignored** in the plane conversion, so letterboxed/partial content
   (e.g. before the first resize after a rotation) produced shifted garbage rather than the
   full game.

What was NOT the cause: the game does not block screen capture (a hardware screenshot works);
the matcher/Vision Lab are unaffected (this bug is in the capture pipeline only).

### Fix

- The captured-content resize callback now feeds a shared, authoritative resize path
  (`recreateDisplay`) that re-creates the ImageReader/Surface generation at the *reported*
  dimensions; `DisplayManager` changes re-derive the size from the current window metrics
  and funnel into the same path — covered on all API levels ≥ 26; both paths are idempotent
  and deduped by `DisplayChangePolicy`.
- Every reader/surface generation is numbered (`GenerationCounter`); images captured with a
  stale generation are dropped at the listener and again at publish time, so a late frame
  from a released reader can never reach the UI or the frozen snapshot.
- The plane conversion is rewritten into a pure, JVM-tested `RgbaPlaneCopy`: it honors
  padded rows (`rowStride > width * pixelStride`), `image.cropRect` (letterboxed/offset
  content produces a tightly packed image of the content), arbitrary `pixelStride >= 1`
  and both byte orders; it never mutates the caller's buffer and drops malformed frames
  instead of faking pixels. Bitmap dimensions used by the vision pipeline equal the
  payload dimensions exactly.
- Structured `key=value` diagnostics cover every stage (`session=` id, `generation=`,
  image dims, cropRect, rowStride, pixelStride, padding, bitmap dims, preprocess in/out).
  No image contents are logged. See `docs/SCREEN_CAPTURE.md` → "Diagnosing the landscape bug".
- Freeze continues to work unchanged: the pinned frame is never overwritten by newer frames;
  a resize during freeze pins the newest *valid* frame; Resume continues with the current
  generation; the frozen frame is an independently owned copy with no shared payload buffer.

### Evidence

- CI at head `5252016cd15c824009368e831ed499f24209980d` (run 43773398149): **840 unit tests
  (debug+release), 0 failures / 0 errors / 0 skipped**; strict lint green; `assembleDebug`
  green; artifact `draftlens-debug`, digest
  `sha256:c46580570a85054f225bd4d927350b153991f98956b1e3ee9a877d5ed9c1b3f1`.
- Letterbox/stride/offset behavior verified by deterministic JVM tests
  (`RgbaPlaneCopyTest` — 22 cases) and the orientation swap in `DisplayChangePolicyTest`.
- Instrumented tests compiled, not executed (no KVM on the CI runner); the new
  `FramePipelineInstrumentedTest` covers lifecycle invariants and must run on a device.
- On-device verification is **pending**: rows 23–32 of `docs/TESTING_PHYSICAL_DEVICE.md`
  (portrait→landscape, resize storm, freeze-during-rotate, letterbox transient, API-34+
  single-app resize). **The fix is not claimed to work on a device until those rows are run.**
- If anything still shows black after this fix, the new logs pin down whether acquisition
  (`generation=... plane:` line) or conversion (`... frame stored: ... bitmap:` lines) is
  the source — see `docs/SCREEN_CAPTURE.md` → "Diagnosing the landscape bug with logs".

---

# Sprint 1C — App-Only Capture & Circular Floating Control

**Branch:** `sprint-1c-app-only-capture` · stacked on PR #3 (`sprint-1b-hero-recognition`, unmerged) · **PR:** #4 (base `sprint-1b-hero-recognition`)
**Spec:** maintainer Sprint 1C brief · **Design:** `docs/superpowers/specs/2026-11-16-sprint-1c-app-only-capture-design.md`

## What shipped

- Single-app (API 34+ partial) capture is now the recommended MLBB mode; the system chooser
  is never forced. `onCapturedContentVisibilityChanged` drives frame publication: while the
  selected window is hidden, images are acquired and closed but never converted or published;
  the last valid game frame (and, if pinned, the frozen snapshot) is preserved. Publication
  resumes on visibility return unless a frame is pinned. Full-display capture remains the
  documented fallback with an explicit no-notification-free-promise note.
- One circular 52dp floating button (`TYPE_APPLICATION_OVERLAY`), shown only while a capture
  session runs and only after the user grants the overlay permission (requested from the
  capture screen, never at install time). Tap toggles LIVE (green) / FROZEN (orange) by calling
  the shared `CaptureFreeze` path; 400 ms long-press arms drag; release snaps to the nearest
  horizontal edge and persists normalized coordinates (DataStore). Rotation re-anchors it.
- Overlay is non-focusable, exactly button-sized, and removed on every teardown path
  (stop, error, revoke, task removal, resize failure).
- Guidance rewritten: the "choose Entire screen" instruction is gone from the debug and
  Vision Lab screens.
- No recommendation UI, no panel, no menu, no accessibility service; `SYSTEM_ALERT_WINDOW`
  declared, requested only on opt-in.

## Verification

- Implementation head `f992d45`: CI run 43784157138 — **886 unit tests, 0 failures / 0 errors / 0 skipped**;
  strict lint green; `assembleDebug` green; APK `app-debug.apk`
  sha256 `1d1f50629e145d48e64ed56d0cf74228e7d48d4c6f94fde81eb6c22fc4795810`. Docs-only commits after
  it change no build inputs and are re-checked by CI on every push.
- Instrumentation: compiled via `assembleAndroidTest` in CI; not executed — no KVM on the runner
- Physical device (rows 33–43 of `docs/TESTING_PHYSICAL_DEVICE.md`): NOT RUN — maintainer task;
  in particular the overlay-vs-capture isolation finding (rows 34/35) is an on-device question
  that CI cannot answer; no exclusion claim is made.

## Known limitations / compatibility notes

- API < 34: no visibility signal; app-only windows are undetectable — behavior degrades to
  full-display semantics.
- If the captured window is already hidden when a session starts, publication stays open
  until Android's first visibility callback arrives (API 34+; typically milliseconds). The
  gate defaults to open because full-display projections may never call the callback at all.
- Full-display fallback may capture the shade/system UI/other apps/DraftLens; the freeze
  controls still work.
- Whether the overlay appears inside captures is unverified per device (see sprint doc).
- A resume requested while MLBB is hidden flips the user's intent to LIVE and takes effect
  when MLBB returns; publication stays gated while hidden (no black/DraftLens frames).
- Resize during freeze is enforced by the pipeline's freeze gate (policy truth-table +
  `CaptureVisibilityPolicyTest`) and by on-device rows 25/30; no synthetic-pixel
  instrumented test exists — an ImageReader surface cannot be filled from a test Canvas.

---

# Sprint 1C — Follow-up fixes (notification Pause, permissions-only UI)

**Branch:** `sprint-1c-app-only-capture` (stacked on PR #3, unmerged) · **PR:** #4

## Root cause of the overlay misbehaving during app-only capture

Full trace (callback → policy → pipeline → freeze → overlay) shows the overlay's lifecycle
was never coupled to capture visibility — render, touch and window lifetime have no frame
input. Three real defects were causing the observed "frozen" control:

1. silent no-op tap on `NoFrame` in fresh app-only sessions (nothing published yet);
2. overlay glyph and notification derived only from `frozenFrame`, so a non-pin publication
   block (PAUSED) showed LIVE/green everywhere while publishing nothing;
3. no single derivation for "is live publication happening" across surfaces.

Fixed by: unified `CapturePublishState` (publishStateFor / publishActionFor /
floatingTapFor) shared by notification and overlay; combined frozen-frame + session-state
collector; toast feedback on `NoFrame`; OEM/immersive-game overlay coverage documented as a
hardware finding (device rows 44–53, NOT RUN), with no masking or mode fallback shipped.

## Notification: Freeze → Pause

Stop + one state action: Pause (LIVE) / Resume (PAUSED or FROZEN). Pause maps onto the
existing `CaptureStatus.PAUSED` pipeline gate — no second projection, no second state, latest
valid frame preserved. Resume = `resumeLive()` + `setPaused(false)`.

## UI cleanup

General instructions removed: capture-mode tutorial card, floating-control tutorial card,
in-game freeze help texts, stale Sprint-0 marker. Kept: notification permission (request,
status, settings action) and overlay permission (request, status, settings action) in one
compact card.

## Verification

- CI head / APK digest: see the PR #4 description (edited after each green run).
- Instrumented tests: compiled via `assembleAndroidTest`; not executed (no KVM).
- Physical device rows 44–53: **NOT RUN** — see `docs/TESTING_PHYSICAL_DEVICE.md`.
