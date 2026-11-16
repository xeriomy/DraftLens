# Sprint 1B — hero recognition (historical)

**Status:** merged into `main` as PR #3 on 2026-11-16.

Sprint 1B added the first on-device hero-identification pipeline. It is kept here as a
historical record; the current behaviour is described in
[Vision architecture](../VISION_ARCHITECTURE.md) and the
[implementation report](../IMPLEMENTATION_REPORT.md).

## What shipped

- **Catalog.** Rone Arena is the primary source (`GET /api/heroes`, paginated, HTTPS only);
  MLBBDex supplies a completeness cross-check and roles/lanes by slug. Identity is a
  name-derived slug. A file-based snapshot and private image cache give offline browsing;
  sync happens only when the user asks.
- **Cache safety.** Per-asset sidecars bind URL, SHA-256, size and content type. Assets are
  revalidated when served; changed URLs force a new download; a failed refresh keeps the
  previous asset. Redirects are HTTPS-only. Snapshot writes are temp-sibling plus flush,
  sync and atomic move, with no direct-write fallback.
- **Slot extraction.** The source rectangle is cropped upright for every rotation following
  the `FrameTransform.sourceToOriented` convention. The source bitmap is never mutated;
  short-lived temporaries are recycled by their owner.
- **Recognition v0.** dHash plus a normalized 3×3 mean-RGB descriptor form an explicitly
  uncalibrated similarity, never a probability. Blank, near-uniform and detail-free crops are
  rejected as insufficient visual information. Below threshold the result is an honest no
  match. Manual confirm and manual correction are available in every state.
- **Capture.** Freeze and resume from the ongoing notification, an independently owned pinned
  frame, and the LIVE/FROZEN presentation. The landscape capture fix re-derives size on
  resize, numbers reader generations to drop stale frames, honours `image.cropRect` and padded
  rows, and adds structured diagnostics without logging image contents.

## Verification and caveats

CI was green on the final head with 840 unit tests, strict lint and a debug APK. Instrumented
tests compiled but did not execute because the hosted runner has no KVM. Accuracy on real
MLBB screens was **not** measured, and the threshold and heuristics remain starting defaults.
The physical-device smoke of freeze and landscape capture remained pending at merge time; see
the [physical-device checklist](../TESTING_PHYSICAL_DEVICE.md).
