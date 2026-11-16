# Vision coordinates

This document defines the coordinate spaces used by capture, preprocessing, annotation and
recognition. The rules are implemented in `com.draftlens.vision.geometry` and are pure Kotlin
so they can be tested on the JVM.

## Spaces

| Space | Type | Units | Origin |
|---|---|---|---|
| Source | `NormalizedRect`, `NormalizedPoint` | `0..1` per axis, as captured | top-left of the captured pixels |
| Source pixels | `PixelPoint`, `PixelRect` | integer pixels | top-left of the captured pixels |
| Oriented pixels | `FloatPoint` | continuous pixels | top-left after rotation correction |
| Crop pixels | `FloatPoint` | continuous pixels | top-left of the crop rectangle |
| Processed pixels | `PixelRect` | integer pixels | top-left of the processed output |

Normalized values are not display dp, are not density-scaled, and do not change when a crop
is applied. A `NormalizedRect` is valid only when every value is finite, `left < right`,
`top < bottom`, and all four values are inside `[0, 1]`. A `PixelRect` has exclusive right and
bottom edges, so it always keeps at least one pixel between opposite edges.

Conversions round outward so the pixel rectangle covers the requested area:

```kotlin
fun NormalizedRect.toPixelRect(width: Int, height: Int): PixelRect
fun PixelRect.toNormalized(width: Int, height: Int): NormalizedRect
```

`OrientationCategory.from(width, height)` returns `LANDSCAPE`, `PORTRAIT` or `SQUARE`; square
is its own category.

## Two different rotations

| Field | Meaning | Effect on pixels |
|---|---|---|
| `displayRotation` | device rotation at acquisition | none — a record only |
| `rotation` | pixel-orientation correction | applied by the transform |

MediaProjection buffers are already display-oriented, so production frames carry
`rotation = ROTATION_0`. Non-zero corrections exist for synthetic fixtures and for imports
that genuinely need straightening.

Source-to-oriented mappings for a continuous point `(x, y)` in a `W × H` image:

| Rotation | Oriented size | Oriented point |
|---|---|---|
| `ROTATION_0` | `W × H` | `(x, y)` |
| `ROTATION_90` | `H × W` | `(H - y, x)` |
| `ROTATION_180` | `W × H` | `(W - x, H - y)` |
| `ROTATION_270` | `H × W` | `(y, W - x)` |

The 90° and 270° cases swap axes; 180° does not.

## Processing order

```text
validate → orient → crop in oriented space → resize without distortion
```

The crop stored in `FrameTransform` is normalized in **oriented** coordinates; a null crop
means the full oriented frame. The output aspect ratio must match the integer crop rectangle
within one pixel of width or height.

`FrameTransform` records the source size, correction, crop, oriented size, integer crop
rectangle, scale and output size. Its continuous mapping chain is
`source → oriented → crop → processed` and is reversible up to floating-point error; the
integer conversions round outward and stay within one pixel. Downscaling discards detail, so
scaling back up cannot recover it.

## Canvas and preview

The Vision Lab shows the **oriented, uncropped** frame. Cropping belongs to processing and
inspection, not to the annotation viewport. The canvas builds an identity-crop transform
from the frame's oriented size and maps both pointer positions and slot rectangles through
it, so overlays stay aligned in portrait, landscape and square without screen-dp guessing.

`OrientedBitmapLayout` computes where the bitmap is really drawn: one uniform scale maps the
oriented size into the fitted rectangle, the bitmap is centred there, and rotation is applied
around the same centre. The rotated bitmap's axis-aligned bounds equal the fitted rectangle,
so a slot covering `0,0 → 1,1` lands exactly on the rendered image. The workspace height
comes from the oriented aspect ratio with a usable minimum; touches on letterbox bands are
not touches on the image.
