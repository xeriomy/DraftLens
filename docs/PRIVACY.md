# Privacy & Game Compatibility — DraftLens

## Data rules (enforced by design, Sprint 0)
- Screen capture happens **only** after the user taps Start and grants the Android system
  consent dialog; consent is per-session, never stored or replayed. Proof: consent intent is
  built in `permissions/ProjectionPermission`, launched from the one button in
  `DebugCaptureScreen`; the service refuses to project without an Android-issued result code.
- Frames live **in memory only** (single latest `Bitmap` slot). Zero files written, zero
  uploads, zero network calls in the Sprint 0 build (no HTTP client dependency exists).
- Preferences (sampling interval, last-capture timestamp) stay in on-device DataStore.
  Room ships empty (no entities); backups exclude the DB and DataStore (`backup_rules.xml`).
- Any future dataset collection (saving frames for ML) requires a **separate explicit opt-in**
  that does not exist yet — it is not implied by granting capture consent.

## What DraftLens never does (forbidden by architecture)
DraftLens only observes pixels through the public MediaProjection API. It never:
reads game process memory, modifies game files, hooks the game, injects packets, automates
touches, auto-selects heroes, or interacts with MLBB programmatically. There is no accessibility
service, no input injection, no overlay input interception in Sprint 0 (overlays are a later
milestone and must stay read-only/informational).

## Unresolved questions (must be answered before store release)
1. **Moonton ToS / fair-play**: is real-time screenshot analysis of Draft Mode allowed, and
   does it require a specific in-game disclosure or make DraftLens ineligible for certain
   competitive contexts? Unknown — needs a policy review.
2. **Screenshot detection**: does MLBB anti-cheat flag MediaProjection capture of the draft
   screen (vs. general screen recording)? Not tested — needs device testing across regions.
3. **Store policies**: Play Data Safety will require declaring "screen contents" access;
   a foreground-service disclosure and a prominent consent flow are required. Not yet done.
4. **Overlay future**: floating overlays over a game must use `TYPE_APPLICATION_OVERLAY` with
   its own consent and must never capture through or interact with game input — design pending.
5. **Regional variance**: OEMs restrict background services/MediaProjection differently
   (Xiaomi MIUI etc.); per-OEM hardening is future work.

Until (1)–(3) are resolved, DraftLens is a **development prototype**, not a shippable product.
