# Sprint 1B — Hero Recognition

**Branch:** `sprint-1b-hero-recognition` · **PR:** #3 (draft, **not merged**)

Sprint 1B builds the first real, on-device hero-identification pipeline: a hero catalog from
public data, slot-image extraction from the annotation canvas, and a deterministic matching
baseline with a manual confirmation flow. It is about identifying heroes, not about draft
recommendations.

**What is explicitly not here:** no recommendation or synergy scoring, no counter ranking, no
floating overlay, no automatic draft-phase detection, no OCR of arbitrary pixels beyond the
slot crop, no cloud recognition API, no ML model, no OpenCV. Those arrive after recognition
has been measured on real device samples.

## New modules

| Module | File(s) | What it does |
|---|---|---|
| Hero catalog model | `vision/heroes/HeroModels.kt`, `HeroIds.kt` | Provider-independent `HeroId` (slug from the English name), entries, assets, attribution, cross-check result |
| Catalog codec | `vision/heroes/HeroCatalogCodec.kt` | Strict v1 snapshot codec; duplicate-id protection; HTTPS-only asset URLs |
| HTTP transport | `vision/heroes/HeroCatalogTransport.kt`, `AndroidHeroCatalogTransport.kt` | HTTPS-only `HttpURLConnection` behind a small interface; size cap enforced while reading; one retry on I/O/5xx; 429 fails fast; no API key |
| Catalog sync | `vision/heroes/HeroCatalogSync.kt` | Paged `GET /heroes` on Rone Arena (plus mirror fallback), MLBBDex for completeness cross-check and roles/lanes by slug |
| Catalog repository | `vision/heroes/FileHeroCatalogRepository.kt`, `HeroCatalogRepository.kt` | Atomic snapshot (`<filesDir>/hero-catalog/catalog.json`), private image cache (`<cacheDir>/hero-assets/`), offline browsing after sync |
| Slot extraction | `vision/heroes/SlotImageExtractor.kt`, `AndroidBitmapSlotImageExtractor.kt` | Axis-aligned crop of the source rect, upright for every rotation; never mutates/recycles the source; nothing written to disk |
| Matcher v0 | `vision/heroes/HeroMatcher.kt`, `HeroRecognition.kt` | dHash perceptual hash + normalized 3×3 mean-RGB descriptor; combined similarity in [0, 1]; ranked top-3; explicit no-match below threshold |
| Reference loader | `vision/heroes/HeroReferenceImages.kt` | Decodes cached reference images; downsampled ≤256 px for cheap matching |
| Recognition store | `datastore/HeroRecognitionStore.kt` | Per-slot confirmed hero (id, name, timestamp) in its own preferences document; corrupt documents read as empty |
| UI | `feature/visionlab/HeroRecognitionCard.kt` | Sync button, Identify action, candidate cards, confirm/correct/clear, unofficial notice and attribution |
| Permissions | `AndroidManifest.xml` | `INTERNET` added — hero catalog download only; capture still requires explicit consent |

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
- `GET /heroes/{hero_identifier}` — additional variants `head_big`, `squarehead`, `squareheadbig`
  (detail endpoint; not fetched in this sprint — `HEAD` is enough to establish the baseline).
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
  (Measured at sync time; see the sync section in `docs/VISION_TESTING.md` for what CI ran.)
- **Caching.** Snapshot JSON is written to a temp file and renamed into place; an interrupted
  sync cannot leave a half snapshot. Reference images go to a private per-app cache with
  magic-byte validation, `image/*` content-type check and a 4 MiB cap per image.
- **Offline.** After a successful sync the catalog serves from cache with no network at all.
  A corrupt cache is reported as an error and never silently replaced; re-sync is the fix.
- **Asset failure handling.** If one image fails, the hero is still in the catalog; the failure
  is reported by name in `assetFailures` and matching simply skips that hero.

## Slot extraction

- The slot rectangle (normalized *source* coordinates) maps to whole source pixels
  (`NormalizedRect.toPixelRect`, floor origin / ceil far edge / ≥1 px) — the same rounding
  rule the annotation canvas uses — and the crop is cropped axis-aligned, then rotated
  upright to match what the user sees on screen.
- Aspect ratio is never changed; nothing is stretched.
- The original frame bitmap is never mutated or recycled.
- Extracted crops live in memory only and are never written to disk.
- Tests cover portrait and landscape frames, all four rotations, edge-touching rectangles,
  minimum-size rectangles, invalid coordinates, payload mismatch and original-frame ownership.

## Matching algorithm and why

Two deterministic descriptors, compared on a fixed grid:

1. **dHash perceptual hash** (9×8 grayscale, area-average downsample, bit = left pixel
   brighter than right). 64 bits, hamming distance, robust to scale and mild recompression.
2. **Normalized 3×3 mean-RGB appearance descriptor** (27 floats). Weaker signal but catches
   hue/lighting layout that a hash alone can miss.

Combined similarity: `0.6 × (1 − hamming/64) + 0.4 × (1 − colorDistance)`, clamped to [0, 1].

Why this over anything fancier:

- No training data, no model download, no inference dependency; runs on any Android device this app targets.
- Reference images (head portraits) are small and near-uniform in composition, which is exactly
  where dHash works well.
- It is fully deterministic, so tests are stable and failures are reproducible.

**This is a heuristic ranking aid, not a probability or a calibrated confidence.** Scores are
labeled as similarity everywhere they appear. The no-match threshold (0.55) is a starting
default, not a calibration — it was **not** tuned on real MLBB screens (none were used).
Two candidates closer together than 0.05 are flagged `ambiguous`. The UI never selects a
hero automatically: the user confirms or corrects every match.

## Evaluation (honest)

There is **no accuracy claim on real game screens** in this sprint. The matcher is evaluated
by deterministic algorithm tests only (identical images match, ranking order, threshold
behaviour, ambiguous gap, corrupt references skipped). Those tests prove the mechanism works,
not that 134 MLBB hero portraits are linearly separable on a capture full of effects.

| Metric | Value | Data |
|---|---|---|
| Algorithm tests (deterministic) | see CI | synthetic fixtures only |
| Top-1 accuracy on real MLBB screens | **not measured** | no samples available |
| Top-3 accuracy on real MLBB screens | **not measured** | no samples available |
| No-match false-rejection rate | **not measured** | no samples available |
| Sample count | 0 | no device images committed |

Synthetic tests are useful for the algorithm; they do not prove accuracy on real MLBB screens.
`head` and `smallmap` are headshots on flat backgrounds and should be more separable than the
matcher assumes; this needs a device smoke test with manually labeled slots before any claim
is printed in the app.

Physical-device evaluation is **pending** and must use real app captures with manually
identified hero slots. Device details and the number of correctly identified samples must be
recorded here when run; upload or commit those images is prohibited.

## UI

Vision Lab, under the slot list:

- *Sync catalog* button; sync state shown (`Not synced`, progress while syncing, `Ready: N heroes`, or error with the cached count if offline).
- *Identify hero* on the selected slot; per-slot state (`idle → processing → candidates | no match → confirmed | failed`).
- Candidate cards: cached image, name, similarity, `ambiguous` marker when present; **Confirm** per candidate.
- **No match** state with the best similarity, plus manual name search.
- **Confirmed** view with Clear and Change…; manual name search available everywhere.
- Attribution footer with the unofficial notice; images load from the local cache only.

Recognition never runs continuously, its button never appears on slot creation, and it never
opens the property menu automatically. Slot creation keeps the maintainer's 1A preference.

## Acceptance status

- **CI green** (code head `7d0c9032a52db8e0f962beea72ceae7120822c88`, run 43731484922):
  **323 unit tests (646 executions, debug + release), 0 failures, 0 errors, 0 skipped**;
  `assembleAndroidTest` **compiled, not executed**; strict lint passed; `assembleDebug` passed;
  artifact `draftlens-debug`, 10 334 760 bytes, digest
  `sha256:122433490a5da130390a8a7b7b1f69eb0040551ca0c7fa82f8bc375f9405741f`.
- Physical-device smoke: **pending**.
- Catalog sync on a device: **pending** (verified only through fake transport in unit tests).
- On-device accuracy: **not measured**.
- **Catalog entries actually loaded:** measured at sync time on the device that syncs; not yet
  recorded here. The pagination logic is exercised against fake transports in unit tests;
  the real total is whatever the API returns when the user presses *Sync catalog*.

See `tasks/todo.md` and `docs/IMPLEMENTATION_REPORT.md` for the running checklist.
