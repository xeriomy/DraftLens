# DraftLens

DraftLens is a native Android app for players of Mobile Legends: Bang Bang draft mode. It
captures the game screen, lets you mark the ban/pick portrait slots by hand, freezes the
game frame without leaving the match, and runs a small on-device hero-identification
baseline that always asks you to confirm or correct the result. It is a calibration and
inspection tool, not an automatic draft advisor.

Current code is the merge of three increments: **1A** (image-bearing frame and Vision Lab
foundation), **1B** (hero catalog, slot extraction, recognition v0) and **1C** (app-only
capture, visibility gating and the circular floating freeze control). The status of each,
including what has not been verified on hardware, is recorded in
[docs/IMPLEMENTATION_REPORT.md](docs/IMPLEMENTATION_REPORT.md).

## What it can do

**Capture**
- MediaProjection consent flow; a typed foreground service owns the projection and the
  notification.
- Frames at 2 FPS by default, configurable 334–2000 ms (hard cap 3 FPS).
- Pause and resume, rotation and resize handling, explicit teardown on stop or revocation.
- Freeze from the notification or from one floating button; a pinned frame survives pause,
  resize and visibility changes inside the session.
- App-only capture on Android 14+: when the captured window is hidden, images are still
  acquired and closed but never published, so the last valid game frame is kept.
- One 52 dp overlay button, attached only while capture runs, with edge snapping and a
  normalized position saved in DataStore. It is opt-in and never touches the game.

**Vision Lab**
- Non-null frame contract with separate original, processed and slot images.
- Rotation-aware geometry and validated preprocessing with explicit failure codes.
- Manual slot annotation with stable ids, bounds/duplicate/overlap checks, undo/redo,
  copy/paste/duplicate and a strict JSON codec.
- Versioned calibration profiles: 1 % aspect-ratio tolerance, exact orientation match,
  refusal of unsupported schemas, and a delete-all escape hatch.

**Recognition v0**
- Public static hero catalog from Rone Arena, cross-checked against MLBBDex, synced only
  when you ask, cached privately on the device.
- Upright slot crops for every rotation, then a dHash plus 3×3 mean-RGB similarity.
- Blank or detail-free crops are rejected; low similarity is reported as no match.
- Candidates are labelled as uncalibrated similarity, never probability. You always
  confirm or correct manually. Accuracy on real MLBB screens has not been measured.

**Deliberately not included:** recommendation, counter/synergy/role scoring, automatic
draft-phase detection, game-memory reading, injected touches, cloud recognition, ML models,
OpenCV, and any overlay beyond the single capture-freeze button. Captured pixels never
leave the device and are never written to disk.

## Build and verify

JDK 17 and Android SDK platform 35 with build-tools 35 are required.

```sh
./gradlew test               # JVM unit tests
./gradlew lint               # strict lint (abortOnError = true)
./gradlew assembleAndroidTest # compile instrumented tests
./gradlew assembleDebug      # debug APK
```

GitHub Actions (`.github/workflows/build.yml`) runs these on every push and pull request and
uploads the `draftlens-debug` APK. CI is the build of record: the authoring host is aarch64
while the SDK's `aapt2` is x86_64-only, so resource-touching tasks cannot run locally here.

## Run

1. Install the `draftlens-debug` APK from CI on a physical device.
2. Open **Debug Capture**, enable notifications, and optionally enable the floating control
   (this is the only time the overlay permission is requested).
3. Tap **Start Capture**. When Android asks what to share, prefer **Single app → Mobile
   Legends**; choose the whole display only as a fallback, since it can include the shade.
4. In MLBB, freeze with the floating button or with **Pause** in the notification.
5. Return to **Vision Lab**. Draw a slot, run **Identify hero**, and confirm or correct the
   candidate. You can also process the frame and save the slots as a calibration profile.
6. Tap **Resume** in the notification or the Vision Lab to return to live frames. Stop from
   the app, the notification, or by revoking consent.

Physical-device behaviour is not yet verified end to end. The checklist and its honest
status are in [docs/TESTING_PHYSICAL_DEVICE.md](docs/TESTING_PHYSICAL_DEVICE.md).

## Documentation

Current state:
- [Architecture](docs/ARCHITECTURE.md) — modules, layers, flows and boundaries.
- [Screen capture](docs/SCREEN_CAPTURE.md) — MediaProjection lifecycle, freeze, visibility, resize, overlay.
- [Vision architecture](docs/VISION_ARCHITECTURE.md) — frame, geometry, preprocessing, annotation, recognition.
- [Vision frame contract](docs/VISION_FRAME_CONTRACT.md) — payload and ownership rules.
- [Vision coordinates](docs/VISION_COORDINATES.md) — normalized space, rotation and crop semantics.
- [Vision Lab](docs/VISION_LAB.md) — user-facing controls.
- [Data sources](docs/DATA_SOURCES.md) — hero catalog providers and their contracts.
- [Testing](docs/VISION_TESTING.md) — test inventory and CI gates.
- [Physical-device checklist](docs/TESTING_PHYSICAL_DEVICE.md) — what still needs a device.
- [Implementation report](docs/IMPLEMENTATION_REPORT.md) — delivered scope and verification status.
- [ADR-001](docs/decisions/ADR-001-android-native.md) — native, single-module decision.

Historical records, kept for provenance:
- [Sprint 1B](docs/sprints/SPRINT_1B_HERO_RECOGNITION.md), [Sprint 1C](docs/sprints/SPRINT_1C_APP_ONLY_CAPTURE.md).
- [Sprint 1A plan](docs/superpowers/plans/2026-11-06-sprint-1a-vision-foundation.md), [Sprint 1C plan](docs/superpowers/plans/2026-11-16-sprint-1c-app-only-capture.md).
- [Sprint 1A design](docs/superpowers/specs/2026-11-06-sprint-1a-vision-foundation-design.md), [Sprint 1C design](docs/superpowers/specs/2026-11-16-sprint-1c-app-only-capture-design.md).
- [Task history](tasks/todo.md), [roadmap](tasks/plan.md).
