# Sprint 1C — app-only capture and floating control (historical)

**Status:** merged into `main` as PR #4 on 2026-11-16. It was originally stacked on Sprint 1B;
the base was retargeted to `main` before merge.

This is a historical record. Current behaviour is described in
[Screen capture](../SCREEN_CAPTURE.md).

## What shipped

- **App-only capture guidance.** Single app / app window → Mobile Legends is preferred on
  Android 14+; whole-display capture remains a documented fallback that may include the shade
  and other apps. Nothing is forced and no capture region is masked.
- **Visibility gate.** `onCapturedContentVisibilityChanged` feeds a pure
  `CaptureVisibilityPolicy` that gates frame publication. When the captured window is hidden,
  images are still acquired and closed but never converted or published, so the last valid
  game frame and any pinned frame are preserved. The gate survives resize and resets only on a
  fresh session.
- **One floating control.** A 52 dp circular `TYPE_APPLICATION_OVERLAY` view, attached by the
  capture service only while a session runs. Tap toggles LIVE and paused/frozen through the
  shared `CaptureFreeze` path; long-press-and-drag moves it with edge snapping; its normalized
  position is persisted. The overlay permission is requested only when the user enables it,
  and denial leaves capture and in-app controls working.
- **Shared publish state.** `CapturePublishState` derives LIVE, PAUSED and FROZEN from the
  frozen frame and session state so the overlay glyph, its tap behaviour and the notification
  action always agree. The notification offers Stop and exactly one state action.
- **Capture UI.** Tutorial and in-game help text were removed, leaving a compact permissions
  card and the existing capture controls.

## Verification and caveats

CI was green on the final head with 926 unit tests, strict lint, compiled instrumentation and
a debug APK. Instrumented tests were not executed. Physical-device rows 33–43 and 44–53,
including OEM overlay isolation and app-only capture behaviour, were **NOT RUN** at merge
time. On API levels below 34 there is no visibility signal, so app-only capture cannot be
detected and whole-display semantics apply. See the
[physical-device checklist](../TESTING_PHYSICAL_DEVICE.md).
