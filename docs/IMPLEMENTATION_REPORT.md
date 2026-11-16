# Implementation report

**Current head:** `60dd412` on `main` · **CI:** green, run `43938370022`
(926 unit tests, 0 failures, 0 errors, 0 skipped)

This report describes what the merged code contains and what has actually been verified. The
sprint documents in `docs/sprints/` and `docs/superpowers/` are historical records.

## Delivered

**Capture (Sprints 0–1C).** MediaProjection consent, a typed foreground service with Stop and
one state action, frame sampling, pause/resume, freeze with an independently owned snapshot,
captured-window visibility gating, resize and rotation handling with numbered reader
generations, `cropRect`-aware plane copying, and one opt-in circular overlay control.

**Vision foundation (1A).** Non-null frame payloads, rotation-aware geometry with round-trip
tests, preprocessing that returns failure codes, manual slot annotation with validation,
undo/redo and strict codecs, calibration profiles with compatibility checks, and the Vision Lab.

**Recognition baseline (1B).** Public static hero catalog with manual sync and a private,
validated cache; upright slot extraction; a visual-information gate; dHash plus mean-RGB
similarity reported as uncalibrated ranking; manual confirmation and correction; per-slot
confirmation storage.

## Verification evidence

| Gate | Result at head `60dd412` |
|---|---|
| `./gradlew test` | 926 unit tests, 0 failures, 0 errors, 0 skipped |
| `./gradlew lint` | passes with `abortOnError = true` |
| `./gradlew assembleAndroidTest` | instrumented tests compile |
| `./gradlew assembleDebug` | debug APK produced and uploaded |
| Manifest inspection | `FOREGROUND_SERVICE_MEDIA_PROJECTION` and type `0x20` present |

Instrumented tests are **compiled, not executed**: the hosted runner has no KVM, so the
emulator image either does not boot in time or is skipped as an unknown API level. CI proves
compilation, pure logic and lint only.

## Not verified

- The MediaProjection lifecycle matrix and the freeze workflow on a physical device
  (rows 1–22 of the checklist).
- The landscape capture fix, rotation, resize storms and letterbox transients (rows 23–32).
- App-only visibility, the floating control and its OEM-specific capture isolation
  (rows 33–53).
- Identification accuracy on real MLBB screens. Top-1/top-3 and rejection behaviour are
  evaluated on synthetic fixtures only; the no-match threshold and visual-information
  heuristics are starting defaults, not calibrated values.
- Live network behaviour: transport tests use fake responses, and the number of catalog
  entries loaded is only known at device sync time.

No device model, Android version, APK artifact or measurement was supplied for the
maintainer-reported smoke tests, so those are recorded as reports rather than evidence. See
[TESTING_PHYSICAL_DEVICE.md](TESTING_PHYSICAL_DEVICE.md) for the full status and
[VISION_TESTING.md](VISION_TESTING.md) for the test inventory.

## Scope boundaries

No recommendation, counter or synergy scoring; no automatic draft-phase detection; no
game-memory reading, injected touches, cloud recognition, ML model or OpenCV; no overlay other
than the single capture-freeze control. Captured frames and slot crops stay in memory and are
never uploaded.
