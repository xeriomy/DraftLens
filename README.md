# DraftLens — Sprint 0.1 Capture Hardening

Native Android app that assists MLBB Draft Mode players. Sprint 0.1 hardens the previously working Foundation: **live screen capture with user consent, in a foreground service, with real-time debug preview, strict lifecycle accounting and bounded/stale-free state handling.**

**Maintainer-reported device smoke:** Sprint 0 capture and preview were used on a physical Android device and confirmed working. Device/version details and expanded matrix results are listed in [docs/TESTING_PHYSICAL_DEVICE.md](docs/TESTING_PHYSICAL_DEVICE.md) as pending.**

## What works in Sprint 0
- MediaProjection consent flow (`ActivityResultContracts.CreateScreenCaptureIntent`)
- Foreground service (`mediaProjection` type) with persistent notification + Stop action
- Frame acquisition at 2 FPS default, configurable 334–2000 ms (hard cap 3 FPS)
- Live frame preview in the debug screen, frame count / resolution / latency / timestamp
- Pause / resume, rotation & resize handling, clean resource teardown on stop/revoke
- Explicit `NotImplemented` detection seams — no fake results anywhere

## What is deliberately NOT here (later milestones)
ML hero recognition, recommendation engine, counter/synergy/role scoring, floating overlays,
API sync, player profiling, builds. See `tasks/plan.md` and `docs/ARCHITECTURE.md`.

## Build
Requires JDK 17 and Android SDK platform 35, build-tools 35.
```sh
./gradlew test            # JVM unit tests
./gradlew lint            # Android lint
./gradlew assembleDebug   # produces app/build/outputs/apk/debug/*.apk
```
GitHub Actions (`.github/workflows/build.yml`) runs exactly those steps on push/PR and uploads
the APK artifact. **This authoring environment is aarch64 and the SDK's aapt2/build-tools are
x86_64-only, so no APK was assembled locally — the CI run is the build of record.** See
Implementation Report in this repo's history / final message for what was and was not executed.

## Run
1. Install APK on a physical device (emulator validates compile, not MediaProjection reliability).
2. Tap **Start Capture** → grant the system screen-capture consent dialog.
3. MLBB (or any app) — the debug screen shows live frames.
4. **Stop Capture**, or the notification's **Stop**, or revoke consent in Settings to exercise teardown.

## Docs
- `docs/ARCHITECTURE.md` — module layout, state machine, data flow
- `docs/SCREEN_CAPTURE.md` — MediaProjection lifecycle, Android 14+ rules, limits
- `docs/DATA_SOURCES.md` — Rone Arena vs MLBBDex audit (verified live 2026-11-04)
- `docs/PRIVACY.md` — consent, no-upload, game-internals rules
- `docs/decisions/ADR-001-android-native.md` — why native, single-module, and version pins
- `docs/IMPLEMENTATION_REPORT.md` — executed verification, limitations, next tasks
