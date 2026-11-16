# Sprint 1C app-only capture design (archived)

**Status:** implemented and merged as PR #4. Current behaviour is documented in
[Screen capture](../../SCREEN_CAPTURE.md).

## Intent

Let the user freeze and resume the live MLBB capture without leaving the game. One small
circular overlay button; the notification keeps Stop and a single state action; no
recommendation UI of any kind.

## Key decisions

- **Pure visibility policy.** `CaptureVisibilityPolicy` decides whether a captured window is
  visible enough to publish. It is JVM-tested and has no Android dependencies.
- **Publication gate only.** Visibility blocks conversion and publication in `FramePipeline`;
  images continue to be acquired and closed so the reader stays healthy. The overlay's own
  window, rendering and touch handling are independent of capture visibility.
- **One shared state.** `CapturePublishState` derives LIVE, PAUSED and FROZEN from the pinned
  frame and session state. The overlay glyph, the overlay tap and the notification action all
  use it, so they cannot disagree.
- **Opt-in overlay.** `SYSTEM_ALERT_WINDOW` is declared but requested only when the user
  enables the control. Denial does not affect capture or in-app freeze/resume.
- **Normalized position.** The button stores a normalized position with edge snapping and
  re-anchors on rotation.
- **No masking or automation.** The app never reads game memory, injects touches or masks the
  capture region. On API levels below 34 there is no visibility signal and whole-display
  semantics apply.

## Verification boundary

CI proves compilation, pure policy and position math, and lint. Overlay behaviour across OEMs
and single-app capture on a real device remain hardware findings recorded in the
[physical-device checklist](../../TESTING_PHYSICAL_DEVICE.md).
