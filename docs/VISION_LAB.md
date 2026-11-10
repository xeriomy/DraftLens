# Vision Lab

The Vision Lab is the second tab of the app. The first tab, Debug Capture, stays one tap away
and capture keeps running while the Vision Lab is open — the projection lives in a foreground
service, not in a screen.

## Frame Preview

Shows the frozen frame when one is pinned, otherwise the latest live frame.

- Original width × height
- Display rotation and the pixel-orientation correction
- Source state (`CAPTURING`, `PAUSED`, `IDLE`, `STOPPED`, `ERROR`, `UNKNOWN`)
- A `LIVE` / `FROZEN` badge
- *Freeze* / *Resume* — *Freeze* pins the frame on screen; live frames keep arriving but are
  not displayed. *Resume* releases the pin.
- *Process Frame* — processes the pinned frame if there is one, otherwise the latest live
  frame at the moment of the press.
- *Clear* — releases the pinned reference and the processed frame.

Nothing is written to disk. Frames live in memory only.

## Annotation Canvas

The canvas draws the oriented, uncropped frame inside a dark card.

- Tap a rectangle to select it; tap empty canvas to deselect.
- Drag inside the selected rectangle to move it. The move is clamped to the frame.
- Drag one of the four corner handles to resize.
- Drag empty canvas to create a rectangle. Drags smaller than `0.01` normalized units in
  either axis are discarded rather than creating an accidental slot.
- Each rectangle carries a label such as `A-B1`, `E-P2` or `G-B1` — team initial, type
  initial, index.

The overlay never redraws or mutates the source bitmap. Positions are mapped through the
frame transform, so the overlay stays aligned with the preview.

## Manual slots

The same annotation set is editable as text, which is the accessible path to every value.

Each slot card shows:

- The slot id (short form)
- Type (`BAN` / `PICK`) and team (`ALLY` / `ENEMY` / `GLOBAL`) as filter chips
- Index and label fields
- Editable `left`, `top`, `right`, `bottom` fields in normalized coordinates
- *Select*, *Apply* and *Delete*

*Apply* validates bounds, the minimum size, and the index. Validation issues are listed per
slot: an overlap inside the same `(type, team)` scope is an error, an overlap across scopes is
a warning, and a duplicated `(type, team, index)` is an error.

*Reset annotations* clears every slot after a confirmation.

An index is unique per `(type, team)` scope: the same index may be used for an ally slot and
an enemy slot, and for a ban and a pick.

## Processing Inspector

Actual values only — source dimensions, output dimensions, rotation, crop region, scale
factor, processing duration, the last error, and whether the displayed pixels are frozen or
live.

There is no confidence, no hero name and no draft phase anywhere in this card. It states
**"Detection: not implemented"**.

*Max processed dimension* sets the long side of the processed output (default 1280). Smaller
sources are never upscaled.

## Profiles

A calibration profile records a label, the target aspect ratio, the expected orientation and
the manual slots.

- **Save current annotations** stores the current slots for the displayed frame.
- **Apply** checks the profile against the displayed frame first. An incompatible profile is
  refused and the reason is shown with the expected and actual ratio and orientation.
- **Update** rewrites the profile with the current slots and label.
- **Delete** removes one profile, after a confirmation.

Aspect ratio compatibility uses the **oriented** dimensions — after the orientation
correction, before crop and before scale — and a tolerance of 1 %:

```text
abs(candidate - target) / target <= 0.01
```

The orientation must match exactly: `PORTRAIT`, `LANDSCAPE` and `SQUARE` are distinct.

A profile never changes the slots silently. Applying one is an explicit action, and it is
refused when the frame does not match.

If the stored document uses a schema newer than this build supports, saving and updating are
disabled and only **Delete all calibration data** is offered. A corrupt document is reported
the same way and is left untouched until it is deleted.
