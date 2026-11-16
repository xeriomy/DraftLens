# Sprint 1C implementation plan (archived)

**Status:** completed and merged as PR #4. Current behaviour is documented in
[Screen capture](../../SCREEN_CAPTURE.md).

## Goal

Let a maintainer freeze and resume MLBB capture from a small circular overlay without leaving
the game, with single-app capture preferred and the last valid game frame preserved when MLBB
is hidden. No recommendation UI of any kind.

## Shape of the work

1. A pure `CaptureVisibilityPolicy` that decides whether a captured window's visibility
   permits publication, with a JVM truth table.
2. Wire `onCapturedContentVisibilityChanged` (API 34+) into the capture guard and the frame
   pipeline; keep acquiring and closing images while publication is blocked.
3. Normalized overlay position math with edge snapping, persisted in DataStore.
4. A one-button `WindowManager` overlay attached only during a session, with tap to toggle and
   long-press drag to move; requested permission only on explicit enable.
5. Service attach/detach on every teardown path, rotation re-anchor, and capture-mode guidance.
6. Tests for the policy, position math, freeze/resume/visibility sequences and overlay
   attachment; documentation and device-checklist rows.

## Decisions retained

The overlay gate touches only frame publication, never the overlay's own window or touch
handling. The visibility policy is pure Kotlin. The notification and overlay derive their
state from one shared publish-state flow. See the current capture documentation for details.
