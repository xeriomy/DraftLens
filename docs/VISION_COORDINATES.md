# Vision Coordinates

## Normalized coordinates

All annotation geometry is stored in **normalized source coordinates**:

- `x` runs left to right, `y` runs top to bottom, both in `[0.0, 1.0]`.
- The axes are the source image's axes **as captured** — before any rotation, crop or scale.
- They are not display dp, not density-scaled, and not affected by a crop origin.

`NormalizedRect(left, top, right, bottom)` is valid only when every value is finite,
`left < right`, `top < bottom`, and all four are inside `[0, 1]`. An empty rectangle is not
storable.

`PixelRect(left, top, right, bottom)` uses integer pixels with **exclusive** right and bottom
edges, so at least one pixel always remains between opposite edges.

Conversions:

```kotlin
fun NormalizedRect.toPixelRect(width: Int, height: Int): PixelRect   // rounds outward
fun PixelRect.toNormalized(width: Int, height: Int): NormalizedRect
```

Rounding outward means the pixel rectangle always covers the requested area.

## Orientation categories

`OrientationCategory.from(width, height)` returns `LANDSCAPE` when `width > height`,
`PORTRAIT` when `height > width`, and `SQUARE` when they are equal. Square is a category of
its own rather than being squeezed into portrait or landscape.

## Two rotations

| Field | Meaning | Effect on pixels |
|---|---|---|
| `displayRotation` | device display rotation at acquisition | none — it is a record |
| `rotation` | pixel-orientation correction | the transform is applied |

MediaProjection buffers are already display-oriented, so production frames set
`rotation = ROTATION_0`. A non-zero `rotation` appears only in synthetic fixtures or in an
authorized import that genuinely needs straightening.

Source-to-oriented mappings, applied to a continuous source point `(x, y)`:

| Rotation | Oriented size | Source point → oriented point |
|---|---|---|
| `ROTATION_0` | `W × H` | `(x, y)` |
| `ROTATION_90` | `H × W` | `(H - y, x)` |
| `ROTATION_180` | `W × H` | `(W - x, H - y)` |
| `ROTATION_270` | `H × W` | `(y, W - x)` |

`ROTATION_90` and `ROTATION_270` swap the axes; `ROTATION_180` preserves them.

## Pipeline order

```text
validate -> normalize orientation -> optional crop on the oriented image -> resize without distortion
```

The crop rectangle is a normalized rectangle in **oriented** coordinates. The crop is applied
after the orientation correction and before the resize.

## FrameTransform

`FrameTransform` is the record of one preprocessing run:

| Field | Meaning |
|---|---|
| `sourceWidth`, `sourceHeight` | captured size |
| `rotation` | the correction that was applied |
| `crop` | the oriented crop, or the full oriented frame when nothing was cropped |
| `orientedWidth`, `orientedHeight` | size after the correction |
| `cropPixelRect` | the integer pixel rectangle of the crop inside the oriented image |
| `scale` | processed pixels per crop pixel, horizontally |
| `processedWidth`, `processedHeight` | the output size |

`FrameTransform.create(sourceWidth, sourceHeight, rotation, crop, processedWidth, processedHeight)`
validates positive dimensions and requires the output to preserve the oriented crop's aspect
ratio within one pixel of width or height.

The mapping chain is continuous (floating point) and reversible:

```text
source pixels -> oriented pixels -> crop pixels -> processed pixels
```

Round trips hold up to floating-point error for the continuous mappings; the public integer
conversions round outward and stay within one pixel. A downscale discards detail that cannot
be recovered by scaling back up.

## How the canvas uses this

The Vision Lab canvas draws the **oriented, uncropped** frame. Crop belongs to processing and
inspection, not to the annotation viewport.

Because slot rectangles live in source-normalized coordinates while the canvas shows oriented
pixels, the canvas builds the identity transform

```kotlin
FrameTransform.create(
    sourceWidth = frame.sourceWidth,
    sourceHeight = frame.sourceHeight,
    rotation = frame.rotation,
    crop = null,
    processedWidth = orientedWidth,
    processedHeight = orientedHeight,
)
```

and maps every pointer position and every rectangle through it. The overlay therefore stays
aligned with the preview for portrait, landscape and square frames, and for any
`rotation` — no screen-dp guessing is involved.

The preview box itself fits the frame with `ContentScale.Fit`; the fitted rectangle is
computed from the canvas size and the oriented aspect ratio, and all drawing and hit testing
use that rectangle.
