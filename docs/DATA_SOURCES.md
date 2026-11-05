# Data Source Audit — DraftLens (verified live 2026-11-04)

Both candidates were fetched live during Sprint 0. **Neither provider's rates are hard-coded
anywhere in the app** — Sprint 0 contains no recommendation code. Findings below are the
contract a future sync milestone must code against.

## Primary: Rone Arena — https://arena.rone.dev/api/docs
- OpenAPI 3 spec: `https://arena.rone.dev/api/openapi.json`, **v1.6.2**, 56 paths.
- License (per spec): **BSD 3-Clause** (not affiliated with/endorsed by Moonton; all game
  names/marks belong to their owners).
- Hero data: `GET /api/heroes`, `/api/heroes/{id}`, `/api/heroes/rank`, `/api/heroes/positions`.
- Stats: `/api/heroes/{id}/stats`, `/trends`, `/skill-combos`.
- **Counters/relations**: `/api/heroes/{id}/relations` returns `{"strong":[...ids],"weak":[...],"assist":[...]}`;
  `/counters`, `/compatibility` also exist — the richest counter/synergy surface of the two.
- Win/pick/ban: via `/api/heroes/rank` (rank-scoped); **statistical definitions are upstream's
  and must be quoted, not reinterpreted.**
- Ranks/roles/lanes/items/emblems/spells: under `/api/academy/*` (e.g. `/api/academy/roles`,
  `/api/academy/heroes/{id}/lane`, `/api/academy/ranks/{rank_id}`).
- Portraits: per-hero `head`/`smallmap` image URLs (proxied through wsrv.nl at fetch time).
- Esports: `/api/esports/{id,ph,my,kh}/*` schedules/standings.
- **User endpoints (`/api/user/*` — auth, profiles, match history) exist but are explicitly
  out of scope:** DraftLens must not add player accounts or collect player data.
- Rate limits / availability guarantee: **none published** in the spec. Treat as best-effort;
  cache locally (Room) and degrade silently offline.
- Attribution: BSD-3 requires preserving the notice; the spec also asks for the
  non-affiliation disclaimer in any derivative UI.

## Secondary: MLBBDex — https://mlbbdex.com/en/api-doc
- No key, no quota ("No key, no quota. Responses are static and cached."), rate guidance is
  informal ("Be reasonable"). Hero count surfaced on-page: **134 heroes, 133 ranked, 102 items,
  276 patches.**
- Endpoints: `GET /api/v1/heroes` (role/lane filters), `/api/v1/heroes/{slug}` (profile =
  skills, skins, images, written analysis), `/api/v1/items`, `/api/v1/patches`, `/api/v1/rankings`.
- Response envelope: `{"data": …, "total": …, "source": {"name": "Mobile Legends Wiki",
  "url": "https://mobilelegends.fandom.com", "license": "CC BY-SA"}}` — **attribution travels
  with every response and must be displayed.**
- `/api/v1/rankings` (verified live, `measuredAt: 2026-10-19`): per-hero `{slug, tier (S+..C),
  winRate, banRate, pickRate, score, lowSample}`. This is the cleanest win/ban/pick surface —
  but **`score` is MLBBDex's proprietary composite** (undocumented formula) and `lowSample`
  must be respected by any consumer.
- Hero shape: `{slug, name, roles[], lanes[], specialties[], release, ratings{offense,
  durability, abilityEffects, difficulty}, skins, skills[]}` — roles and lanes here are **static
  tags**, not ranked-performance data.
- Counters: present on-site as written analysis, **not** as a structured endpoint; no synergy
  endpoint. Weaker than Rone for counter/synergy.
- Upstream honesty: MLBBDex states its win/ban/pick stats come "through the arena.rone.dev
  community API" — **the two providers share an upstream and are not independent corroboration.**
- License: game data CC BY-SA (Mobile Legends Wiki); MLBBDex's written analyses MIT.
  One sync cadence: "weekly sync," latest shown 2026-10-21.
- No player data anywhere ("No player data… this API offers none").

## Comparison

| Dimension | Rone Arena | MLBBDex |
|---|---|---|
| Hero IDs | numeric `hero_id`; id-or-name accepted | slug (`khufra`, `yi-sun-shin`) |
| Catalog completeness | full incl. new heroes (observed ids ≥133) | 134 heroes, 133 ranked |
| Portraits | head/smallmap URLs | images in hero profile |
| Role/lane | positions + academy lane endpoints | role/lane tags on hero |
| Win/pick/ban | `/heroes/rank`, rank-scoped | `/rankings`, with `lowSample` flag |
| Counters | structured strong/weak/assist | written analysis only |
| Synergy/compatibility | `/compatibility`, relations.assist | not exposed |
| Rank filters | academy ranks | page-level rank brackets |
| Patch metadata | academy meta/version | `/patches` (276) |
| Attribution | BSD-3 + non-affiliation notice | CC BY-SA source block per response |
| Auth | has user auth (unused by us) | none, none needed |
| Rate limits | unpublished | "no quota, be reasonable" |
| Availability guarantee | none | none |

## Do-not-assume list (for the future sync milestone)
1. Win/pick/ban numbers are **not comparable** across providers — different measurement windows,
   rank pools, and (for MLBBDex `score`) proprietary formulas.
2. `tier` (S+…C) is MLBBDex-calculated; Rone uses ranks — never merge them into one ladder
   without re-deriving from raw rates.
3. Portrait URLs may be proxied/expire — cache bytes locally, don't hot-link.
4. MLBBDex hero counts (134/133) and Rone hero ids are snapshots; treat both as volatile.
5. No player-account endpoints will ever be integrated (privacy rule, Sprint 0 brief §7).

**Recommended default for later milestones:** Rone Arena as the structured-stats/tier source
(counters + compatibility), MLBBDex rankings envelope for win/ban/pick cross-checks — but only
after re-verifying definitions, and with attribution UI for both.
