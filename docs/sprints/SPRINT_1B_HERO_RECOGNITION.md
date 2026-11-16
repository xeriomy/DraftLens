# Sprint 1B — Hero Recognition

**Branch:** `sprint-1b-hero-recognition` · **PR:** #3 (draft, **not merged**)

Sprint 1B builds the first real, on-device hero-identification pipeline: a hero catalog from
public data, slot-image extraction from the annotation canvas, and a deterministic matching
baseline with a manual confirmation flow. It is about identifying heroes, not about draft
recommendations.

**What is explicitly not here:** no recommendation or synergy scoring, no counter ranking, no
floating overlay for MLBB itself, no automatic draft-phase detection, no OCR of arbitrary
pixels beyond the slot crop, no cloud recognition API, no ML model, no OpenCV. Those arrive
after recognition has been measured on real device samples.

## Review-fix pass (this revision)

This branch now also contains the fixes for the PR #3 review findings:

| Part | Finding | Resolution |
|---|---|---|
| A | No way to freeze the game frame while inside MLBB | Capture-side freeze via an explicit `ACTION_FREEZE` / `ACTION_RESUME_LIVE` command routed to the running service; notification gains a **Freeze current frame** action; an independently owned snapshot is pinned in the process-local holder; Vision Lab shows the pinned frame; a **Resume live capture** notification action and an in-app **Resume** both exist |
| B | 90°/270° pixel rotation used incorrect indexing | `PixelSlotImageExtractor` rewritten to the exact mappings established by `FrameTransform.sourceToOriented()`; tests now use asymmetric non-square images and hand-computed expectations |
| C | Android bitmap extractor: unverified Bitmap/rotation contract | Payload dimensions validated against `OriginalFrame.sourceWidth/sourceHeight`; rotation rewritten to pixel-exact `sourceToOriented` mapping; source bitmap never mutated/recycled; temporaries recycled in every path; instrumented tests added |
| D | Blank gray crop produced three hero candidates at ~0.73 | New pure-Kotlin `VisualInformationAnalyzer` gate before matching; new distinct `InsufficientVisualInformation` outcome and `INSUFFICIENT_INFO` UI state; blank crops never produce candidates |
| E | Manual correction hidden in some states | Manual search reachable from CANDIDATES, NO_MATCH, FAILED, INSUFFICIENT_INFO and CONFIRMED; **Change…** now opens correction instead of re-running Identify; confirmation can be corrected without clearing first; failed clears report failure instead of showing success |
| F | Cache freshness: non-empty file was always reused | Per-asset `*.meta` sidecar binds the cached image to URL, SHA-256, size and content type; serve-time validation (size, hash, magic bytes, container structure, content type, plus a real decode on Android); changed URLs force re-download; failed refreshes keep the previous asset and never expose it under a changed URL; HTTPS-only redirect policy re-validates every hop |
| G | `renameTo` failure fell back to a direct write, destroying snapshots | `writeAtomically()` is now temp-sibling + flush/sync + `ATOMIC_MOVE` only; failure preserves the previous target and cleans the temp file; stale `*.tmp` siblings swept on init |
| H | `loadReference` recycled the bitmap, then read its width/height | Dimensions are read before recycling; pixel reads complete before release; exceptional paths recycle too |
| I | Matcher honesty | Matcher stays labelled a prototype ranking baseline; threshold/weights marked uncalibrated; evaluation section added below |
| J | Scope | Sprint 1A behavior preserved (see the preservation section) |
| K | Reporting | See "Evaluation and verification" and `docs/IMPLEMENTATION_REPORT.md` |

## Capturing Mobile Legends: the freeze workflow

> **Superseded by Sprint 1C** (`docs/sprints/SPRINT_1C_APP_ONLY_CAPTURE.md`): prefer
> **Single app / app window → Mobile Legends** when offered; the freeze is now primarily
> the circular floating button (rows 33–43 of `docs/TESTING_PHYSICAL_DEVICE.md`). The
> full-display guidance below remains valid as the fallback.

1. Open DraftLens → **Debug Capture**. When Android asks what to capture, choose
   **Entire screen / full device display**. App-window capture shows the DraftLens screen, not
   the game, and cannot support this workflow. (The app says this on screen too.)
2. Open Mobile Legends and wait for the draft screen you want to annotate.
3. Pull down the notification shade (without leaving the game) and tap
   **Freeze current frame** on the ongoing DraftLens capture notification.
4. DraftLens pins an independently owned copy of the latest validated game frame in memory.
   The MediaProjection session, VirtualDisplay and ImageReader stay running; images are still
   acquired and closed, but no conversion or publication work is performed while frozen.
5. Return to DraftLens. Vision Lab shows the pinned MLBB frame, not newer frames showing
   DraftLens itself. Draw slot rectangles and run **Identify hero** on that frame.
6. Tap **Resume** (Vision Lab) or **Resume live capture** (notification) to see live frames
   again. No second projection or VirtualDisplay is ever created for this.

Properties of the implementation:

- The notification action is an explicit component with an immutable `PendingIntent`
  (`PendingIntent.getService` → `CaptureSessionService`). No Activity is launched.
- The action never re-promotes the service, never restarts MediaProjection, never requests
  consent again and never creates a VirtualDisplay. With no active capture session it is a
  no-op (a stale command re-creating the service stops itself immediately).
- Only one frozen frame is kept; a new capture session drops it, and resuming replaces live
  publication. Freezing is idempotent (a duplicate tap keeps the first pinned frame).
- The frozen snapshot is independent of the live pipeline's payloads: pixels are copied, the
  source is never mutated or recycled, and the frozen frame is displayed while newer live
  frames keep arriving (they never overwrite it).
- A projection stopped by Android releases the VirtualDisplay, ImageReader and projection,
  but preserves the pinned frame in memory for editing while the process lives. It does not
  survive process death.
- **Notifications:** the ongoing foreground-service notification stays visible with an
  **Stop** action and, depending on state, **Freeze current frame** (LIVE) or
  **Resume live capture** (FROZEN). Actions appear in the expanded notification.
  If the user disabled notifications (API 33+), the capture can still run but the actions are
  not reachable from the shade; the app checks and shows this on the capture screen and in
  the Vision Lab guidance.
- Nothing is written to disk or uploaded by the freeze or the capture path.

## New modules

| Module | File(s) | What it does |
|---|---|---|
| Hero catalog model | `vision/heroes/HeroModels.kt`, `HeroIds.kt` | Provider-independent `HeroId` (slug from the English name), entries, assets, attribution, cross-check result |
| Catalog codec | `vision/heroes/HeroCatalogCodec.kt` | Strict v1 snapshot codec; duplicate-id protection; HTTPS-only asset URLs |
| HTTP transport | `vision/heroes/HeroCatalogTransport.kt`, `AndroidHeroCatalogTransport.kt` | HTTPS-only `HttpURLConnection` behind a small interface; size cap enforced while reading; one retry on I/O/5xx; 429 fails fast; manual redirects re-validated per hop (HTTPS only); no API key |
| Redirect policy | `vision/heroes/HeroRedirectPolicy.kt` | Every redirect hop resolved and constrained to HTTPS; relative redirects resolved; HTTP downgrades and non-HTTP schemes refused; unit tested |
| Catalog sync | `vision/heroes/HeroCatalogSync.kt` | Paged `GET /heroes` on Rone Arena (plus mirror fallback), MLBBDex for completeness cross-check and roles/lanes by slug |
| Catalog repository | `vision/heroes/FileHeroCatalogRepository.kt`, `HeroCatalogRepository.kt` | Temp-sibling + flush/sync + `ATOMIC_MOVE` snapshot write (no direct-write fallback); private image cache (`<cacheDir>/hero-assets/`) with per-asset `*.meta` sidecars binding URL, SHA-256, size and content type; serve-time revalidation; offline browsing after sync |
| Image validation | `vision/heroes/ReferenceImageValidator.kt`, `AndroidReferenceImageValidator.kt` | Two-layer validation: a pure-Kotlin container structure check (signature, chunks, CRCs, trailers) used on all platforms, plus an on-device `BitmapFactory` decode check wired in `DraftLensApp`; JVM tests exercise the structure check, instrumentation tests exercise both |
| Slot extraction | `vision/heroes/SlotImageExtractor.kt`, `AndroidBitmapSlotImageExtractor.kt` | Axis-aligned crop of the source rect, upright for every rotation via the `FrameTransform.sourceToOriented` mapping (pixel-exact on both paths); never mutates/recycles the source; temporaries released exactly once; nothing written to disk |
| Visual information | `vision/heroes/VisualInformation.kt` | Deterministic, Android-free analysis of a crop (luma/color variance, edge density, meaningful differences, minimum dimensions); rejects blank/near-uniform/detail-free crops before matching |
| Matcher v0 | `vision/heroes/HeroMatcher.kt`, `HeroRecognition.kt` | dHash perceptual hash + normalized 3×3 mean-RGB descriptor; combined similarity in [0, 1]; ranked top-3; explicit no-match below threshold; explicit insufficient-information outcome |
| Reference loader | `vision/heroes/HeroReferenceImages.kt` | Decodes cached reference images; dimensions read before recycling; pixel reads complete before release; downsampled ≤256 px for cheap matching |
| Frame snapshots | `models/FrameSnapshots.kt` | Independently owned copies of frames for the freeze workflow (pixel copy, metadata preserved verbatim) |
| Freeze bridge | `capture/CaptureFreeze.kt`, `capture/FramePipeline.kt`, `capture/CaptureStateHolder.kt` | The freeze command path: service/in-app triggers → `CaptureFreeze` → pipeline publication gate → `CaptureStateHolder.frozenFrame` → Vision layer |
| Recognition store | `datastore/HeroRecognitionStore.kt` | Per-slot confirmed hero (id, name, timestamp) in its own preferences document; corrupt documents read as empty |
| UI | `feature/visionlab/HeroRecognitionCard.kt` | Sync button, Identify action, candidates, INSUFFICIENT_INFO / NO_MATCH / FAILED / CONFIRMED states, manual search everywhere |
| Permissions | `AndroidManifest.xml` | `INTERNET` (catalog) and `POST_NOTIFICATIONS` (freeze action reachability) declared; capture still requires explicit consent; `mediaProjection` foreground-service type unchanged |

## Data sources and attribution

| Provider | What is used | License / notice |
|---|---|---|
| **Rone Arena** (`arena.rone.dev`, mirror `arena.fastapicloud.dev/api`) | `GET /api/heroes` (paginated, English) for entries, `hero_id`, head/smallmap portrait URLs | BSD-3-Clause (community-maintained, unofficial). Not affiliated with or endorsed by Moonton. Game names and visual assets remain the property of their respective rights holders |
| **MLBBDex** (`mlbbdex.com`) | `GET /api/v1/heroes` for the completeness cross-check and roles/lanes/specialties by slug (`roles`, `lanes`, `specialties`) | Community wiki data under CC BY-SA (https://mobilelegends.fandom.com); written content MIT. Game visuals remain the property of Shanghai Moonton Technology Co., Ltd. |

- No written analyses are copied into the app. No counter or build statistics are shown.
- Provider-independent identity is a slug derived from the English name; display names are
  never an identity.
- `externalIds` records `roneArenaHeroId` and `mlbbdexSlug` per entry.
- Snapshot attribution and notices are kept in the cached document and shown in the app.
- The unofficial/non-affiliation notice is shown in the recognition card.

### Re-verified contracts (2026-11-15)

Rone Arena (primary):
- Base: `https://arena.fastapicloud.dev/api` (primary) / `https://arena.rone.dev/api` (mirror).
  No key. Public endpoints only.
- `GET /heroes?size&index&lang` — `data.records[].{data.hero.data.{head,name,smallmap}, hero_id, relation}`,
  `data.total`. Records ordered by `hero_id`.
- Error handling: a 503 means maintenance/high traffic (`alternative_endpoint` fallback);
  a 429 `UPSTREAM_RATE_LIMITED` means back off, never parallel-hammer.

MLBBDex (cross-check):
- `GET /api/v1/heroes` — `{data, total, source{name,url,license}}` with `slug`, `name`,
  `roles`, `lanes`, `specialties`. Envelope source attribution is credited here and in the app.

## Catalog behaviour

- **Manual sync only.** Nothing downloads at startup; the user presses *Sync catalog*.
- **Pagination** with `size=100`, `index=1..`, capped at 20 pages; duplicates by
  `hero_id` are collapsed and counted; records missing id/name/both images are skipped and counted.
- **Mirror fallback.** If the primary host fails, the request is retried once against the mirror.
- **Catalog entries actually loaded are reported**, never a hardcoded expected count.
- **Caching (freshness enforced).** Per reference image, a small sidecar
  (`<cache>/<asset>.img.meta`) records the source URL, SHA-256 of the bytes, size, content
  type and a save timestamp. An asset is reused only if everything matches: file present,
  4 MiB cap, sidecar URL equals the URL the current catalog advertises, size and SHA-256
  match, magic bytes valid, container parses (PNG CRCs / JPEG & GIF trailers / WebP RIFF
  framing) and — on Android — the image actually decodes. Anything else is re-downloaded.
- **Changed URLs.** When the catalog starts advertising a different URL for the same hero
  asset, the old file is never exposed as the new asset; the refresh re-downloads it, and
  the old one is not served even if the refresh fails.
- **Failed refreshes.** A refresh failure never deletes the previous asset and never serves
  it under a changed URL. With an unchanged URL and a valid file on disk, the cache is
  simply kept; with a corrupt file on disk, that hero reports an `assetFailures` entry and
  matching skips it.
- **Redirects.** The transport follows redirects manually and re-validates every hop:
  HTTPS only, relative redirects resolved, non-HTTP schemes and over-long targets refused.
  Downgrades are never accepted.
- **Snapshot atomicity.** `writeAtomically()` writes to a `*.tmp` sibling in the same
  directory, flushes and syncs where supported, then performs an `ATOMIC_MOVE` into place.
  If atomic replacement cannot be completed (e.g. `EISDIR`, cross-device), the previous
  snapshot is preserved, the temp file is deleted and the write fails loudly. There is no
  direct-write fallback. Abandoned `*.tmp` siblings older than an hour are swept on init.
- **Offline.** After a successful sync the catalog serves from cache with no network at all.
- **Asset failure handling.** If one image fails, the hero is still in the catalog; the
  failure is reported by name in `assetFailures` and matching simply skips that hero.
- **Privacy.** The cache lives in the app's private dirs. No frames, crops or confirmation
  data ever leave the device; no runtime permissions beyond INTERNET/POST_NOTIFICATIONS/
  the capture ones are used.

## Slot extraction

- The slot rectangle (normalized *source* coordinates) maps to whole source pixels
  (`NormalizedRect.toPixelRect`, floor origin / ceil far edge / ≥1 px) — the same rounding
  rule the annotation canvas uses — and the crop is cropped axis-aligned, then rotated
  upright to match what the user sees on screen.
- The rotation mappings follow `FrameTransform.sourceToOriented()` exactly (oriented pixel
  `(x, y)` ← source pixel at the inverse point), on both the JVM extractor and the Android
  one, so Java and Android paths produce byte-identical outputs for the same crop.
- Aspect ratio is never changed; nothing is stretched.
- The original frame bitmap is never mutated or recycled. The Android path validates the
  Bitmap dimensions against the frame's `sourceWidth/sourceHeight` before cropping.
- Ownership: the crop creates a fresh bitmap; the rotation creates at most one more; every
  temporary is recycled exactly once; failures recycle their temporaries and return `null`/
  `Result.failure` instead of leaking them.
- Extracted crops live in memory only and are never written to disk.
- Tests cover portrait and landscape frames, all four rotations with non-square asymmetric
  fixtures, crops touching every edge, minimum-size rectangles, below-one-pixel rectangles,
  invalid coordinates, payload mismatch, recycled-bitmap refusal, source immutability, and
  the Android path end-to-end on an instrumented device.

## Recognition v0 — what it is and is not

**This is a prototype ranking baseline, not production-grade hero recognition.**

- Two deterministic descriptors: a **dHash** perceptual hash (9×8 grayscale, area-average
  downsample, bit = left pixel brighter than right) and a normalized **3×3 mean-RGB**
  descriptor (27 floats), combined as `0.6 × (1 − hamming/64) + 0.4 × (1 − colorDistance)`,
  clamped to [0, 1].
- **The combined number is a heuristic similarity, not a probability.** It must never be read
  as a likelihood, a confidence, or a "73% chance". The no-match threshold (0.55) and the
  ambiguities gap (0.05) are **uncalibrated starting defaults** chosen so the pipeline is
  exercisable; they were **not** tuned on real MLBB screens.
- **Before matching** a crop goes through the visual-information gate (`VisualInformationAnalyzer`):
  a near-uniform gray/black/white/solid-color crop, a detail-free crop, or one smaller than
  12×12 is reported as `InsufficientVisualInformation` and no candidate is generated. The
  thresholds (luma variance < 2.0 and channel variance < 2.0 for "near-uniform"; < 8.0 with
  under 1% edge density for "too little detail") are **conservative heuristics, not calibrated
  MLBB thresholds**. Low-contrast-but-detailed crops are deliberately *not* rejected.
  Real-screen calibration of these heuristics is pending device testing.
- `InsufficientVisualInformation` is deliberately distinct from `NoMatch`: the first says the
  crop carries no usable signal, the second says the signal was present but unrecognizable.
- Recognition **never** assigns a hero by itself. The user confirms or corrects every outcome
  (candidates, no-match, insufficient-information, failed, or already-confirmed) through the
  manual name search; the confirmation flow is explicit everywhere, and a failed persistence
  operation is reported, not hidden.
- dHash works best on near-uniform headshots; it does not understand hero identity, and it
  can rank wrong heroes confidently. **Accuracy on real MLBB screens is not measured.**

### Evaluation methodology (synthetic fixtures only)

| What | How it is measured | Status |
|---|---|---|
| Algorithm correctness | Deterministic tests: identical images match, ranking order, threshold behaviour, ambiguous gap, corrupt references skipped, hamming/color math, round trips. | CI (see `docs/VISION_TESTING.md`) |
| Blank-image rejection | Deterministic fixtures: uniform gray/black/white, saturated solids, near-uniform with tiny noise, low-resolution crops. | CI; `VisualInformationTest` covers all of them and the `InsufficientVisualInformation` outcome |
| Top-1 / Top-3 accuracy | **Method when run:** manually identify heroes in slot crops on real device captures, run Identify, record the rank of the correct hero among returned candidates, aggregate. Requires consented captures with manually verified labels; none exist yet. | **Not run** |
| False-match rate | **Method when run:** feed known-non-matching hero crops (and blank UI regions) and count how often any candidate clears the threshold. Blank/low-information crops are now rejected before matching; whether *detailed but wrong* crops still false-match is unmeasured. | **Not run** |
| Top-1/Top-3 on synthetic fixtures | Identical fixtures match (similarity ≈ 1); the inverse-gradient fixture yields NoMatch. No synthetic benchmark suite beyond these deterministic cases exists; no synthetic numbers are claimed as accuracy. | CI only |
| Real-screen status | No MLBB device session has been run. | **Pending** |

### Known limitations

- The matcher does not understand hero identity; two similar portraits can both rank highly.
- The threshold and weights are uncalibrated; 0.55 may be far too low or too high on real
  captures (compression, UI effects, partial crops, anti-aliasing).
- The visual-information heuristics may reject genuinely flat but correct crops, or admit
  genuinely empty but noisy ones. They are not a substitute for calibration.
- Reference images are `HEAD` portraits; full-body or in-game appearance usually differs.
- Java and Android extraction paths are intended to be pixel-identical, on the strength of
  the shared mapping and the fixture tests, but have not been compared on two devices.
- Nothing here is a measurement of recognition quality on MLBB content.

## UI

Vision Lab, under the slot list:

- *Sync catalog* button; sync state shown (`Not synced`, progress while syncing, `Ready: N heroes`, or error with the cached count if offline).
- *Identify hero* on the selected slot; per-slot state (`idle → processing → candidates | no match | insufficient information | confirmed | failed`).
- Candidate cards: cached image, name, similarity, `ambiguous` marker when present; **Confirm** per candidate.
- **No match** and **Insufficient information** states both offer manual name search.
- **Confirmed** view with Clear and **Change…**; **Change…** opens the manual correction
  search (it does not re-run Identify). Corrections persist per slot without clearing first.
- Manual name search is available in every recognition state (candidates, no-match,
  insufficient information, failed, confirmed), plus the share note for saved confirmations.
- Attribution footer with the unofficial notice; images load from the local cache only.
- Capture guidance: choosing **Entire screen / full device display** when Android asks is
  required for the notification freeze workflow; the Debug Capture screen and the Vision Lab
  both say so.

## Preservation of existing behavior (Part J)

- Compact red X delete control at the top-right: unchanged.
- Long-press creation selects the slot without opening the property editor: unchanged.
- 450 ms long-press and early-drag cancellation: unchanged.
- Paste source/anchor semantics: unchanged.
- Context menu and selection synchronization: unchanged.
- Property-editor validation feedback: unchanged.
- Fullscreen editor without zoom/fit controls: unchanged.
- Gesture isolation from page scrolling: unchanged.
- Portrait/landscape rendering and the Vision Lab interaction model: unchanged.
- MediaProjection lifecycle: unchanged except the required freeze/resume integration; the
  `mediaProjection` foreground-service type, `POST_NOTIFICATIONS` and capture
  permissions are preserved; no overlay, accessibility, touch automation or memory reading.
- No hero counters, recommendations, synergy scoring, overlays, packet inspection or cloud
  recognition were added.

## Acceptance status

- **CI green**: see the final CI section of `docs/VISION_TESTING.md` /
  `docs/IMPLEMENTATION_REPORT.md` for the exact head SHA and run URL after this revision.
- Unit tests: see the CI section (totals vary as the suite grows; the required gate is
  0 failures / 0 errors / 0 skipped for both debug and release variants).
- Instrumentation tests: executed on an emulator in CI if the `instrumented` job is green;
  otherwise compiled but not executed — see `docs/VISION_TESTING.md` for the honest status.
- Physical-device smoke: **pending** (full matrix in `docs/TESTING_PHYSICAL_DEVICE.md`).
- Catalog sync on a device: **pending** (verified only through fake transports in unit tests).
- On-device accuracy: **not measured**; real-screen threshold calibration is **pending**.
- Catalog entries actually loaded: measured at sync time on the device that syncs; not yet
  recorded here.
