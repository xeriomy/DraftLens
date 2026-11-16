# Vision architecture

Vision turns captured frames into inspectable pixels and geometry, then applies a deliberately
small identification baseline whose results always require manual confirmation. The layer is
mostly pure Kotlin so the risky logic is testable on the JVM.

## Package map

| Package | Responsibility |
|---|---|
| `vision/model` | Frame ids, enums, payloads, original/processed/slot frames, `VisionFrameSource` |
| `vision/model/android` | `BitmapFramePayload` — the only Android type in the model package |
| `vision/geometry` | Normalized and pixel value types, `FrameTransform` |
| `vision/preprocessing` | Plan/result types, pure math, pixel and Android renderers, diagnostics |
| `vision/annotation` | `DraftSlot`, validating store, strict codecs, shared validator |
| `vision/calibration` | Profiles, compatibility check, codec and repository interface |
| `vision/heroes` | Catalog, transport, cache, slot extraction, visual-information gate, matcher |
| `capture` | Capture service, freeze, visibility policy, generations, plane copy, the only adapter |
| `datastore` | Preferences, calibration document, hero confirmation store |
| `feature/visionlab` | ViewModel, screens, canvas, geometry helpers, panels |
| `feature/home` | Capture debug screen and ViewModel |
| `overlay` | Floating control view, controller, enablement decision, position math |

Everything under `vision/**` is pure Kotlin except `BitmapFramePayload`,
`AndroidFramePreprocessor`, `AndroidBitmapSlotImageExtractor`,
`AndroidHeroCatalogTransport` and `AndroidReferenceImageValidator`, which touch Android APIs.

## The boundary

Vision never imports `CaptureSessionService` or `CaptureStateHolder`. It sees only:

```kotlin
interface VisionFrameSource {
    val frames: StateFlow<OriginalFrame?>
    val state: StateFlow<VisionSourceState>
    val frozenFrame: StateFlow<OriginalFrame?>
    fun freeze(): Boolean
    fun resume()
}
```

`CaptureVisionFrameSource` is the single file that knows both sides. It maps capture status to
`VisionSourceState` and delegates freeze commands to `CaptureFreeze`, the same bridge used by
the notification and the overlay.

## Data flow

```text
ImageReader -> RgbaPlaneCopy -> Bitmap -> capture holder
  -> CaptureVisionFrameSource -> VisionLabViewModel (live or pinned)
  -> explicit Process Frame -> ProcessedFrame -> inspector
  -> select slot -> SlotImageExtractor -> VisualInformation gate
  -> HeroMatcher against cached reference images -> candidates
  -> manual confirm or correct -> HeroRecognitionStore
```

Processing and identification are separate explicit actions. At most one processing job runs:
a generation counter discards stale results, and failures are reported rather than published as
partial frames.

## Frame and geometry rules

- Payloads are non-null and immutable from the consumer's point of view.
- `displayRotation` is a record; `rotation` is the correction actually applied. Production
  frames use `ROTATION_0` because MediaProjection buffers are already display-oriented.
- Normalized coordinates are in source space, bounded to `[0, 1]`; pixel rectangles have
  exclusive far edges.
- The processing order is validate → orient → crop in oriented space → resize without
  distortion. Crops are stored in oriented normalized coordinates.
- The annotation canvas maps pointers and rectangles through an identity-crop transform so
  overlays align with the rendered image for every rotation.

## Recognition rules

- The catalog has one identity per hero derived from the name. Rone Arena is primary; MLBBDex
  cross-checks completeness and supplies role and lane tags. Sync and image download happen
  only on explicit request and are cached privately.
- Every asset is validated when served and re-downloaded if its URL, hash or structure changes;
  a failed refresh keeps the previous asset. Snapshot writes are atomic.
- Slot crops are extracted upright for every rotation. Blank, near-uniform and detail-free
  crops are rejected before matching.
- The matcher combines dHash and a mean-RGB descriptor into an uncalibrated similarity. It is
  a ranking baseline, not a probability and not an ML model. Below threshold the result is no
  match.
- Manual confirmation is mandatory and available from every state, including correction by
  name search. Confirmations are stored per slot; failed writes are reported honestly.

## Persistence

Calibration profiles and hero confirmations are metadata only. No captured pixels, slot crops
or screenshots are persisted. The app's only network client fetches public static catalog data
and reference images over HTTPS.
