# ADR-001 — Native Android, single module

**Status:** Accepted. Confirmed by Sprints 1A, 1B and 1C.

## Context

DraftLens needs continuous screen capture through MediaProjection, a typed foreground
service, a persistent notification, and a `TYPE_APPLICATION_OVERLAY` control. These are
Android platform APIs. It also needs image handling, local persistence and a small amount
of deterministic matching. The team is one maintainer shipping increments in public.

## Decision

Build a native Android app in Kotlin with Jetpack Compose (Material 3), Coroutines and Flow,
and DataStore Preferences. Keep a single Gradle module, `:app`, with logical layers expressed
as Kotlin packages.

The build pins JDK 17, Gradle 8.7, AGP 8.6.1, Kotlin 2.0.20, compileSdk and targetSdk 35,
minSdk 26. Versions live in `gradle/libs.versions.toml`. CI uses GitHub Actions on
ubuntu-latest and is the build of record because the authoring host is aarch64 while the
Android SDK's `aapt2` is x86_64-only.

## Reasons

- MediaProjection, foreground-service types and overlays have no useful cross-platform
  abstraction here; Flutter or KMP would add a bridge without removing platform work.
- One module keeps the build and CI short while the feature scope is still one app. A second
  module becomes justified when a feature needs an independent test or release cycle.
- Pure Kotlin packages for geometry, codecs, policy and matching keep the risky logic
  unit-testable on the JVM without an emulator.

## Consequences

- Android lifecycle and permission behaviour must be handled directly, including consent,
  notification permission and the overlay permission requested only when the user opts in.
- Verification splits in two: CI proves compilation, pure logic and lint; a physical device
  is still required for the MediaProjection and overlay rows in the testing checklist.
- A future split into modules should start with the pure vision/recognition layer, since it
  already has no Android dependencies at its core.
