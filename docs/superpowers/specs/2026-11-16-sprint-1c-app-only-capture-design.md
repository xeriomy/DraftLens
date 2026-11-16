# Sprint 1C — App-Only Capture & Circular Floating Control (Design)

**Date:** 2026-11-16 · **Branch:** `sprint-1c-app-only-capture` (stacked on `sprint-1b-hero-recognition`, PR #3) · **Spec:** maintainer Sprint 1C brief (in task tracker)

## Intent

Let the user freeze/resume the live MLBB capture without leaving the game. One small
circular overlay button; the notification remains only a Stop affordance; no recommendation
UI of any kind.

## Key decisions

1. **Capture chooser is never forced.** `createScreenCaptureIntent()` is unchanged. The
   debug screen guides the user to pick *Single app / app window → Mobile Legends*
   (Android 14+ partial capture). The full-display path stays supported and documented as
   the fallback, with no promise of notification-free frames. The old guidance text that
   told users to choose Entire screen is corrected.
2. **Visibility is a gate, not a frame source.** API 34+
   `MediaProjection.Callback.onCapturedContentVisibilityChanged()` is overridden. While the
   selected window is hidden, images are acquired and closed but never converted or
   published; the last valid published frame is preserved untouched. While visible, live
   publication resumes automatically *unless a frame is pinned* (user intent = FROZEN).
   A resume requested while hidden is safely remembered: it flips the intent LIVE, clears
   the pin, and frames flow again when visibility returns — publication while hidden stays
   gated regardless of the pin state.
3. **Freeze semantics are unchanged.** `CaptureFreeze` + `FrameSnapshots` keep their
   contracts (independently owned copy, projection stays running, idempotent). The overlay
   button tap runs the same `freezeCurrentFrame()` / `resumeLive()` calls as everything else.
4. **The floating control is a button, not a panel.** One 48–56dp circular `WindowManager`
   view with `TYPE_APPLICATION_OVERLAY`, attached by `CaptureSessionService` (the existing
   foreground mediaProjection service, so it is alive exactly while capture is running).
   `SYSTEM_ALERT_WINDOW` is requested (ACTION_MANAGE_OVERLAY_PERMISSION) only when the user
   taps **Enable floating control**; denied → capture + in-app Freeze/Resume unaffected,
   explanation shown; the enable is retried on enable tap or re-request, never forced.
5. **Overlay drives off pure Kotlin seams.** Position storage/snapping/clamping and the
   visibility/publication gate are pure classes with JVM unit tests. The `WindowManager`
   shell stays thin (attach/detach/touch). No DataStore on the hot path; position persists
   as normalized coordinates on drag end.
6. **Overlay is attached only during capture sessions** and always removed on stop,
   terminate, revocation and resize-failure teardown paths. Rotation re-derives pixel
   position from the normalized coordinates.
7. **No masking of capture content.** The overlay sets no flags to hide itself from capture;
   whether it appears in single-app vs full-display captures is a physical-device finding,
   documented honestly, never "proven" by JVM tests.
8. **Stacked delivery.** PR base = `sprint-1b-hero-recognition`; 1B history untouched; no
   merge; physical-device acceptance is maintainer-run and marked NOT RUN.

## Non-goals

Everything in the spec §10: no draft/phase detection, no slot detection, no recognition
models, no scoring, no recommendation overlays, no game interaction, no accessibility service.

## Verification

CI (GitHub Actions) is the build of record (host has no Android toolchain): `test`,
`lint`, `assembleAndroidTest`, `assembleDebug` + APK artifact. Instrumentation tests are
compiled, not executed (no KVM emulator). The physical-device matrix for overlay and
single-app capture is written into `docs/TESTING_PHYSICAL_DEVICE.md` and the sprint doc,
marked NOT RUN.
