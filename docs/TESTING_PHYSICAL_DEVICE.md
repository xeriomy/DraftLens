# Physical-device lifecycle checklist — DraftLens

Status values: **PASS**, **FAIL**, **PARTIAL**, **NOT RUN**. Record the commit SHA, APK artifact run ID, device model and Android version for each execution. Do not mark a row PASS without evidence from that run.

> Maintainer-reported baseline: Sprint 0 capture and preview were smoke-tested on a physical Android device and confirmed working. Device details, versions, frame counts and latency measurements from that test were not provided, so they are not recorded here. All expanded lifecycle rows below remain **NOT RUN**.

| # | Scenario | Expected behavior | Status |
|---:|---|---|---|
| 1 | First capture from a cold launch | Consent appears, foreground service starts, preview shows frames | NOT RUN (smoke result reported by maintainer; no details recorded) |
| 2 | Stop, then fresh second session | New consent dialog; new MediaProjection; no stale frame count | NOT RUN |
| 3 | 10 start/stop cycles | No duplicate notification, no parallel session, no repeated service error | NOT RUN |
| 4 | Stop via Android media-projection system controls | Callback cleanup; IDLE state; service stopped | NOT RUN |
| 5 | Consent revoked during capture | ERROR state shown; resources released; no crash | NOT RUN |
| 6 | App process interruption | Service exits cleanly; no notification leak | NOT RUN |
| 7 | Screen rotation | Existing VirtualDisplay receives same MediaProjection; preview continues; no new consent | NOT RUN |
| 8 | Resolution change | Preview tracks new bounds; no display recreation | NOT RUN |
| 9 | 10-minute continuous capture | No crash/OOM; preview updates; frame interval remains clamped | NOT RUN |
| 10 | Background/background-service stability | Capture survives backgrounding within Android limits | NOT RUN |
| 11 | Sustained capture memory/latency | Memory bounded; frame processing latency reported separately from detection latency | NOT RUN |
| 12 | DraftLens foreground vs MLBB foreground | Selected capture target is clear; preview corresponds to chosen scope | NOT RUN |
| 13 | Screenshots in app | System screenshot path works normally | NOT RUN |

## Evidence to capture each run
- `git rev-parse --short HEAD`
- Device model and Android version (`adb shell getprop ro.product.model`, `adb shell getprop ro.build.version.release`)
- DraftLens version/APK artifact run ID
- Logcat lines tagged `CaptureSessionService`
- Initial resolution, frame count after 10 seconds, preview visible Y/N
- For failures: full exception and steps immediately before it

## CI scope
CI compiles instrumentation tests but does not execute them. JVM/unit tests, lint, APK assembly and merged-manifest inspection are not substitutes for this checklist.
