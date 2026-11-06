# ADR-001 — Native Android (Kotlin + Compose), single module for Sprint 0

**Status:** Accepted (Sprint 0).

## Context
DraftLens needs continuous screen capture (MediaProjection), a foreground service, and later a
floating overlay — all Android-platform capabilities. The brief explicitly requires native.

## Decision
- Native Kotlin + Jetpack Compose, MVVM, Coroutines/Flow. Not Flutter/KMP: MediaProjection and
  foreground-service-type APIs are Android-only and need no cross-platform abstraction today.
- **Single Gradle module** (`:app`). The brief's logical packages exist as Kotlin packages,
  not modules. Rationale: at Sprint 0 scale, module boundaries cost build time and config
  surface without buying test/deploy independence.
- Version pins: Kotlin 2.0.20, AGP 8.5.2, Gradle 8.7, compile/target 35, min 26, JDK 17 —
  the newest stable line that a stock `ubuntu-latest` GitHub runner supports with zero
  toolchain workarounds.

## Split trigger
Split into modules (`:core`, `:feature-*`, …) when the first feature needs to (a) be tested
independently of the capture service, (b) deploy/ship separately, or (c) be reused by a second
app. The `capture/`, `detection/`, `database/`, `datastore/` packages are already seam-shaped
to make that move mechanical.

## Consequences
- `DataStore` is used for preferences now; `Room` is deferred until the first persisted entity
  to avoid an empty database and dead code.
- All detection interfaces return explicit `NotImplemented`; adding a real model later means
  writing *one* new implementation per interface, not restructuring.
