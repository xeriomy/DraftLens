# Sprint 1C — App-Only Capture & Circular Floating Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A maintainer can freeze/resume MLBB capture from a small circular overlay button without leaving the game, with single-app capture preferred and the last valid game frame preserved when MLBB is hidden.

**Architecture:** A pure-Kotlin visibility policy gates frame publication in the existing `FramePipeline` (replacing the implicit "only freeze gates" model); the capture service feeds it from `onCapturedContentVisibilityChanged`. A thin `TYPE_APPLICATION_OVERLAY` WindowManager view (attached only during capture sessions) calls the existing `CaptureFreeze` path on tap. Position/snap math is pure Kotlin + unit-tested.

**Tech Stack:** Kotlin, Compose, WindowManager, MediaProjection (API 34+ callback), DataStore preferences, JUnit4 (JVM) + compiled-only instrumented tests.

**Spec:** `docs/superpowers/specs/2026-11-16-sprint-1c-app-only-capture-design.md` (+ the maintainer Sprint 1C brief, expanded in `docs/sprints/SPRINT_1C_APP_ONLY_CAPTURE.md`)

## Global Constraints

- minSdk 26, compileSdk/targetSdk 35, JDK 17, AGP 8.6.1, Gradle 8.7 — unchanged.
- Never force single-app mode; both capture modes must keep working.
- No new third-party dependencies.
- The overlay is one circular button ≤56dp; no panel, cards, menu, toolbar, fullscreen overlay, accessibility service, or game touches.
- `SYSTEM_ALERT_WINDOW` requested only when the user enables the floating control.
- Anti-goals enforced: no masking of captured content, no screenshot cropping of shade content, no silent exclusion claims.
- Build of record: GitHub Actions `test` → `assembleAndroidTest` → `lint` → `assembleDebug`; do not merge; PR base is `sprint-1b-hero-recognition`.
- Keep Sprint 1B history intact; never modify `main`.

## Review Focus

1. Black/hidden-app frames replacing the pinned frame (single-app capture while MLBB is backgrounded) — pinned by Task 1 policy tests + Task 2 pipeline wiring + Task 7 docs matrix row.
2. Visibility callback racing a resize re-creation (both fire on rotation/visibility flips) — Task 2 keeps `recreateDisplay` behind the same CAPTURING guard; Task 1 tests the gate truth table.
3. Overlay persisting stale position after rotation or after density change — Task 3 normalized math + Task 6 reposition on display metrics; pinned by Task 3 tests.
4. Overlay permission denied leaving a "dead" enable toggle — Task 4 decision helper tests + Task 6 screen copy.
5. Service stopped while frozen leaving an orphaned overlay attached — Task 6 removal in the shared teardown path; pinned by Task 8 holder/freeze tests where JVM-reachable.

---

### Task 1: Pure visibility/publication gate

**Files:**
- Create: `app/src/main/kotlin/com/draftlens/capture/CaptureVisibilityPolicy.kt`
- Test: `app/src/test/kotlin/com/draftlens/capture/CaptureVisibilityPolicyTest.kt`

**Interfaces:**
- Produces: `CaptureVisibilityPolicy` (class) — `fun isContentVisible(): Boolean` (initial true), `fun onContentVisibilityChanged(visible: Boolean): VisibilityTransition`, `fun isContentVisibleNow(): Boolean`, `fun shouldPublish(isActive: Boolean, generationCurrent: Boolean, frozen: Boolean): Boolean`; `data class VisibilityTransition(val wasVisible: Boolean, val isVisible: Boolean)` with `val changed: Boolean`. Used by Tasks 2 and 6.

- [ ] **Step 1: Write failing tests** — truth table: initially visible; hidden→visible transitions; `shouldPublish` = active && generationCurrent && !frozen && visible; `shouldPublish` false when hidden even if not frozen; transitions report was/is.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*CaptureVisibilityPolicyTest*'` → FAIL (class missing). (Local host has no JDK — delegate this run to CI if local Gradle is unavailable; record that.)
- [ ] **Step 3: Implement** the policy as a single class with a lock, no Android imports.
- [ ] **Step 4: Re-run** the same filter → PASS.
- [ ] **Step 5: Commit** `feat: capture visibility gate (pure policy) for app-only windows`

### Task 2: Pipeline visibility gate + service callback wiring

**Files:**
- Modify: `app/src/main/kotlin/com/draftlens/capture/FramePipeline.kt` — add `@Volatile`-backed policy `val visibilityPolicy`, `fun onContentVisibilityChanged(visible: Boolean): VisibilityTransition`, fold `shouldPublish` into the image-available listener (next to the `frozen` checks) and into the publish-time `synchronized(freezeGate)` block.
- Modify: `app/src/main/kotlin/com/draftlens/capture/CaptureSessionService.kt` — override `onCapturedContentVisibilityChanged(isVisible: Boolean)` in `projectionCallback` (API 34+); route to `pipeline.onContentVisibilityChanged`; log; call `overlayController?.onContentVisibilityChanged` if added in Task 6 (keep one call site); no release of any resource on hide (projection stays valid); ignore when not CAPTURING.
- Test: covered by Task 1 truth-table (pipeline is Android-bound) + instrumented compile in Task 7.

**Key points:** images acquired & closed while hidden; no conversion; `_latestFrame` untouched; resize path unchanged and still allowed during freeze; the visibility gate adds a second, independent condition — never replace latest valid frame with an image acquired while hidden.

- [ ] **Step 1:** Implement pipeline gate (one flag set, two check sites).
- [ ] **Step 2:** Implement `onCapturedContentVisibilityChanged` override (mirror `onCapturedContentResize` style; guard the CAPTURING state like the resize path does).
- [ ] **Step 3:** Commit `feat: gate frame publication on captured-content visibility (API 34+)`

### Task 3: Overlay position math (pure)

**Files:**
- Create: `app/src/main/kotlin/com/draftlens/overlay/OverlayPosition.kt`
- Test: `app/src/test/kotlin/com/draftlens/overlay/OverlayPositionTest.kt`

**Interfaces:**
- Produces: `OverlayPosition` — `data class Normalized(val x: Float, val y: Float)` (0..1 fraction of free axis, edge-agnostic), `data class SnapResult(val edge: HorizontalEdge, val normalizedX: Float, val normalizedY: Float)`, `enum class HorizontalEdge { LEFT, RIGHT }`; functions: `pixelsToNormalized(pxLeft: Int, pxTop: Int, screenWidth: Int, screenHeight: Int, sizePx: Int): Normalized`, `normalizedToPixels(n: Normalized, screenWidth: Int, screenHeight: Int, sizePx: Int): Pair<Int, Int>`, `clampToScreen(...)`, `snapToNearestEdge(leftPx: Int, topPx: Int, screenWidth: Int, sizePx: Int): Pair<Int, Int>` (snaps x to `0` or `screenWidth - sizePx`, keeps y clamped), `snapNormalized(n: Normalized, screenWidth: Int, screenHeight: Int, sizePx: Int)`. All pure, no Android imports (density handled by callers in px). Used by Tasks 4 and 6.

- [ ] **Step 1: Write failing tests** — round-trip px→normalized→px; clamp leaves in-screen positions untouched; out-of-screen clamps are pulled in; `snapToNearestEdge` returns LEFT below/at half and RIGHT above; y untouched by snap; landscape→portrait round-trip keeps the normalized position and changes only px; all functions handle zero/degenerate sizes without NaN.
- [ ] **Step 2:** Run filter → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Re-run → PASS.
- [ ] **Step 5:** Commit `feat: normalized overlay position with edge-snapping math`

### Task 4: Overlay enablement decision + preferences

**Files:**
- Create: `app/src/main/kotlin/com/draftlens/overlay/OverlayEnablement.kt` — pure `fun decideOverlayRequest(canDrawOverlays: Boolean, overlayEnabledPreference: Boolean): OverlayRequestOutcome` with `sealed interface` outcomes: `Show`, `RequestPermission`, `Disabled`. Used by the screen (Task 6).
- Modify: `app/src/main/kotlin/com/draftlens/datastore/AppPreferencesRepository.kt` — add keys `floating_control_enabled` (bool), `floating_position_x`/`floating_position_y` (float); flows `floatingControlEnabled: Flow<Boolean>`, `floatingPosition: Flow<OverlayPosition.Normalized?>`; suspend `setFloatingControlEnabled(Boolean)`, `setFloatingPosition(OverlayPosition.Normalized)`, `clearFloatingPosition()` not needed (keep minimal).
- Test: `app/src/test/kotlin/com/draftlens/overlay/OverlayEnablementTest.kt` — grant/deny truth tables; denial never implies enablement.

- [ ] **Step 1:** Write failing decision tests → FAIL.
- [ ] **Step 2:** Implement + wire preferences (no new DataStore instance; reuse `draftlens_preferences`).
- [ ] **Step 3:** Re-run → PASS.
- [ ] **Step 4:** Commit `feat: overlay enablement decision and persisted position keys`

### Task 5: Floating control view + WindowManager shell

**Files:**
- Create: `app/src/main/res/drawable/ic_floating_live.xml`, `app/src/main/res/drawable/ic_floating_frozen.xml` (simple white vector glyphs on transparent background).
- Create: `app/src/main/kotlin/com/draftlens/overlay/FloatingControlView.kt` — circular 52dp view built in code: solid circle background (LIVE green `#1DB954`-like blue/green; FROZEN orange `#F57C00`-like), white glyph `ImageView` centered, content description "Freeze game frame"/"Resume live capture", high-contrast stroke for visibility on bright and dark scenes.
- Create: `app/src/main/kotlin/com/draftlens/overlay/OverlayController.kt` — owns `WindowManager` handle; `attach()`, `detach()`, `setFrozen(Boolean)` (repaint), `repositionForDisplay(screenWidthPx, screenHeightPx, sizePx)` using Task-3 math + persisted normalized position (default: RIGHT edge, upper third), `toggleFreeze()` calling `CaptureFreeze.freezeCurrentFrame()` / `CaptureFreeze.resumeLive()`. Touch handling in the view: DOWN → 400 ms long-press timeout (Handler); MOVE beyond 12dp before timeout → cancel gesture (no drag, no toggle); after timeout → drag (update `LayoutParams.x/y`); UP → snap to nearest edge, persist normalized position, `setFrozen(CaptureStateHolder.frozenFrame.value != null)`. TAP (no cancel, short) → `toggleFreeze()`.
- No new foreground service; the controller is attached/removed by `CaptureSessionService` only.

- [ ] **Step 1:** Implement view + vectors (no automated test; instrumented in Task 7).
- [ ] **Step 2:** Implement controller with gesture state machine; keep < 300 lines.
- [ ] **Step 3:** Commit `feat: circular floating freeze button (WindowManager overlay)`

### Task 6: Service + screen + manifest wiring

**Files:**
- Modify: `app/src/main/kotlin/com/draftlens/capture/CaptureSessionService.kt` — `OverlayController?` field created when (user preference enabled) && (SYSTEM_ALERT_WINDOW granted); attach after `CaptureStateHolder.beginCapture(...)`; remove in `releaseCaptureResources()`; re-attach/update on existing display-listener metrics change; observe `frozenFrame` to repaint (the existing `frozenJob` can call `overlayController?.setFrozen(...)`); gate actual attach on `Build.VERSION.SDK_INT` not needed (TYPE_APPLICATION_OVERLAY API 26+, minSdk 26 ✓).
- Modify: `app/src/main/kotlin/com/draftlens/capture/CaptureController.kt` — no change (enablement flows through the preferences/DataStore, not the service intent).
- Modify: `app/src/main/AndroidManifest.xml` — add `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />`.
- Modify: `app/src/main/kotlin/com/draftlens/feature/home/DebugCaptureScreen.kt` — replace the Entire-screen guidance with the two-mode guide (single-app preferred with MLBB selection steps; full-display fallback with honest limitation); add a **Floating control** card: switch ("Show the freeze button while capture runs"), current LIVE/FROZEN state when overlay attached, denial explanation + "Open Android settings to enable drawing over other apps" button (`ACTION_MANAGE_OVERLAY_PERMISSION`, package-scoped), and a note that capture works fully without it.
- Modify: `app/src/main/kotlin/com/draftlens/feature/visionlab/VisionLabScreen.kt` (only if the guidance card lives there too — check; update the same capture-mode wording if present).

- [ ] **Step 1:** Manifest + service attach/detach (teardown removes overlay on every end path).
- [ ] **Step 2:** Debug screen rewrite of the guide + floating-control card; keep Sprint 1B content intact otherwise.
- [ ] **Step 3:** Commit `feat: enable floating freeze control during capture; two-mode capture guidance`

### Task 7: Instrumented tests (compile-only)

**Files:**
- Create: `app/src/androidTest/kotlin/com/draftlens/FloatingControlOverlayInstrumentedTest.kt` — attaches the view via `OverlayController` under an `ActivityScenario`/service context, asserts `WindowManager` view added then removed after `detach()`, asserts `setFrozen` toggles glyph resource, skips gracefully with `Assume.assumeTrue(Settings.canDrawOverlays(context))` when overlay permission is absent (always, on CI emulators).

- [ ] **Step 1:** Write the test; mirror the header/skip style of `CaptureLifecycleInstrumentedTest`.
- [ ] **Step 2:** Commit `test: instrumented overlay attach/removal (compile-only)`

### Task 8: Remaining JVM coverage

**Files:**
- Test: `app/src/test/kotlin/com/draftlens/capture/ServiceStopAndFreezeTest.kt` (or extend `CaptureFreezeTest`) — repeated freeze commands (AlreadyFrozen keeps first pin), freeze with no frame (NoFrame), stale command after stop (`hasActivePipeline` false → NoPipeline), `terminateSession` while frozen preserves the pin, `reset` clears it, `resumeLive` while "hidden" (policy) then visible resumes publication (policy truth table via Task 1), resize during freeze does not clear the frozen flag (policy-level: `shouldPublish` stays false with frozen=true across dimension deltas — assert the policy takes no dimensions as input).
- Verify existing `CaptureFreezeTest`, `CaptureStateHolderTest`, `FrameSnapshotsTest` pass unchanged; add only what's missing.

- [ ] **Step 1:** Add the missing cases; run the full JVM suite in CI (no local toolchain).
- [ ] **Step 2:** Commit `test: freeze/visibility/stop edge cases for Sprint 1C`

### Task 9: Documentation

**Files:**
- Create: `docs/sprints/SPRINT_1C_APP_ONLY_CAPTURE.md` — objective, branch/PR, what shipped, visibility lifecycle section, overlay section, honest overlay/capture isolation statement, verification matrix (CI run id, device rows marked NOT RUN), limitations.
- Modify: `docs/SCREEN_CAPTURE.md` — rewrite the single-app (Android 14+ chooser guidance) and visibility sections; remove "onCapturedContentVisibilityChanged is not handled"; document overlay isolation unknowns.
- Modify: `docs/TESTING_PHYSICAL_DEVICE.md` — add rows: overlay appearance in single-app capture, overlay appearance in full-display capture, tap-freeze in MLBB, edge snap, rotation, denial path, shade test.
- Modify: `README.md`, `tasks/todo.md`, `tasks/plan.md`, `docs/IMPLEMENTATION_REPORT.md` — Sprint 1C sections; CI run id, unit totals, APK digest after the green run (fill in during Task 10, second docs commit).

- [ ] **Step 1:** Write all docs except CI numbers; commit `docs: Sprint 1C capture-visibility and overlay documentation`

### Task 10: CI, PR, report

**Files:** none (verification only).

- [ ] **Step 1:** Push branch; note the CI run id from `gh run list --branch sprint-1c-app-only-capture`.
- [ ] **Step 2:** If red: diagnose systematically (superpowers:systematic-debugging), fix, re-push.
- [ ] **Step 3:** Harvest unit totals from the run, APK digest from the artifact (`gh run download` + `sha256sum`), instrumented compiled-vs-executed status; fill into docs; commit `docs: record Sprint 1C CI results` and push.
- [ ] **Step 4:** `gh pr create --base sprint-1b-hero-recognition --title "Sprint 1C: App-only capture & circular floating control"` with a body covering requirements 1–11 (unmerged dependency on #3 stated; device verification NOT RUN).
- [ ] **Step 5:** Link the session to the PR (openchamber), name the task; final report with SHA, run id, unit totals, digest, instrumentation status, device status, limitations. Do not merge.
