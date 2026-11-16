# Hero data sources

The audit below was verified live on 2026-11-04. Sprint 1B implements only the catalog part:
Rone Arena supplies hero identities and portraits, and MLBBDex supplies a completeness
cross-check plus role and lane tags. No win rate, counter, synergy, rating or player endpoint
is integrated. The app has no recommendation or scoring code.

## Primary — Rone Arena

`https://arena.rone.dev/api/docs` · OpenAPI 3 spec `https://arena.rone.dev/api/openapi.json`,
v1.6.2, 56 paths. License per spec: BSD 3-Clause, with a non-affiliation notice requested for
derivative UIs.

- Hero data: `GET /api/heroes`, `/api/heroes/{id}`, `/api/heroes/rank`, `/api/heroes/positions`.
- Roles, lanes, ranks and items under `/api/academy/*`.
- Portraits are per-hero `head` and `smallmap` URLs, proxied through an image service at fetch
  time. The app downloads and caches the bytes; it never hot-links them.
- Counters and relations exist (`/relations`, `/counters`, `/compatibility`) but are **not**
  used by DraftLens.
- `/api/user/*` endpoints exist and are explicitly out of scope: no player accounts or player
  data will ever be collected.
- Rate limits and availability guarantees are unpublished. Treat the service as best-effort;
  sync is manual and the cached snapshot works offline.

## Cross-check — MLBBDex

`https://mlbbdex.com/en/api-doc` · "No key, no quota", static cached responses, informal
"be reasonable" guidance. On-page counts at audit time: 134 heroes, 133 ranked, 102 items,
276 patches.

- `GET /api/v1/heroes`, `/api/v1/heroes/{slug}`, `/api/v1/items`, `/api/v1/patches`,
  `/api/v1/rankings`.
- Every response carries a source block with name, URL and CC BY-SA license; that attribution
  must travel with any use.
- Roles and lanes on the hero object are **static tags**, not performance data.
- Rankings carry a proprietary `score` with an undocumented formula and a `lowSample` flag.
  Neither is used by DraftLens.
- Counters are written analysis on the site, not a structured endpoint; there is no synergy
  endpoint.
- MLBBDex states its statistics come from the Rone Arena community API, so the two providers
  share an upstream and are **not independent corroboration**.
- No player data is offered.

## How DraftLens uses them

- One identity per hero, derived from the name as a slug, independent of provider-specific ids.
- Rone Arena is the primary catalog and portrait source; MLBBDex is read for completeness and
  role/lane tags.
- Sync is explicit, HTTPS-only, and writes a snapshot plus a private image cache atomically.
- Assets are revalidated when served: URL, SHA-256, size, content type, magic bytes and
  container structure, with a real decode on Android. Changed URLs force a new download; a
  failed refresh keeps the previous asset rather than exposing it under a changed URL.
- No statistics from either provider are shown as facts about the current match. The only
  similarity shown is the local matcher's uncalibrated ranking of reference images.

## Do not assume

1. Win, pick and ban numbers are not comparable across providers: different windows, rank
   pools and proprietary formulas.
2. Provider tiers and ranks are not one ladder; never merge them without re-deriving raw rates.
3. Portrait URLs may expire — cache bytes, never hot-link.
4. Hero counts and ids are snapshots and will change.
5. Player-account endpoints are out of scope for privacy reasons.
