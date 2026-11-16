# Task ledger

## Delivered

- **Sprint 0 / 0.1** — Android foundation, MediaProjection capture, foreground service,
  sampling, permissions, capture state machine, strict lint and CI.
- **Sprint 1A** — image-bearing frame contract, rotation-aware geometry, validated
  preprocessing, manual slots and strict codecs, calibration profiles, Vision Lab.
- **Sprint 1B** — hero catalog sync, private cached references, slot extraction, recognition
  v0, manual confirm/correct, capture freeze from the notification, landscape capture fix.
- **Sprint 1C** — captured-window visibility gate, one circular overlay control, notification
  pause/resume on a shared publish state, permissions-only capture UI, first-frame fix for
  single-app capture.

All four pull requests are merged into `main`. The old per-commit histories live in git; this
file tracks only what remains open.

## Open work

- [ ] Run the physical-device rows in `docs/TESTING_PHYSICAL_DEVICE.md` and record commit
      SHA, APK run ID, device model and Android version for each result. Nothing may be marked
      PASS without that evidence.
- [ ] Verify the freeze/pause workflow on a device (rows 14–22).
- [ ] Verify the landscape capture fix, rotation and resize storm handling (rows 23–32).
- [ ] Verify app-only visibility and the floating control on real MLBB and across OEMs
      (rows 33–43 and 44–53).
- [ ] Measure identification accuracy on real MLBB screens; calibrate the no-match threshold
      and the visual-information heuristics. The current values are starting defaults.
- [ ] Execute the instrumented tests on a device or a KVM-enabled emulator. CI compiles them
      but the hosted runner has no KVM, so they are not executed.
- [ ] Decide whether a second Gradle module is warranted once a feature needs its own test or
      release cycle; see `docs/decisions/ADR-001-android-native.md`.

## Not planned

Recommendation, counter/synergy/role scoring, automatic draft-phase detection, game-memory
reading, injected touches, cloud recognition, ML models, OpenCV, and any overlay beyond the
single capture-freeze control.

## Verification records

CI is the build of record: `test`, `assembleAndroidTest` (compile only), `lint`,
`assembleDebug`, APK upload and manifest inspection run on every push and pull request in
`.github/workflows/build.yml`. The latest verification record is PR #5 head `4eb9948`; see
`docs/IMPLEMENTATION_REPORT.md` for the summary and `docs/VISION_TESTING.md` for the test
inventory.
