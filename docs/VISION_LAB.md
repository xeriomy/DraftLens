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
- *Freeze* / *Resume* — *Freeze* locks the current frame before annotation; *Resume* returns to
  live frames.
- *Process Frame* — processes the pinned frame if there is one, otherwise the latest live
  frame at the moment of the press. It resizes and prepares the frame only; no recognition
  exists yet.
- *Clear* — releases the pinned reference and the processed frame, and cancels any
  processing that is still in flight so a late result cannot come back.

Nothing is written to disk. Frames live in memory only.

## Annotation Canvas

The canvas draws the oriented, uncropped frame inside a dark card. The workspace is sized
from the frame's own aspect ratio, so a portrait capture gets a tall workspace and a landscape
capture keeps a usable minimum height. The image is never cropped and never stretched.

### The interaction model

The rules live in one explicit state machine (`AnnotationInteraction`) so two gestures can
never compete:

| Interaction | Action |
|---|---|
| Short tap on a rectangle | Select that slot |
| Drag inside a selected rectangle | Move the selected slot |
| Long press on a rectangle | Open its contextual property editor |
| Drag a resize handle | Resize the selected slot |
| Long press on empty image space, then drag | Draw a new slot |
| Short tap on empty image space | Nothing |
| Tap the X control | Delete the selected slot |

Rules that follow from it:

- Only one slot is selected at a time, and the selection is the slot's stable id.
- A tap never moves anything. A drag on an **unselected** rectangle selects it and stops
  there, so a drag can never move the wrong slot.
- A stationary long press is never read as a move, and it opens the property editor
  immediately when the timeout is reached.
- A drag on empty space never creates a rectangle: creating requires the long press first.
- A rectangle is never created outside the displayed image.
- The canvas takes the gesture from the first touch, so **the page never scrolls while a
  slot is being moved, resized or drawn**. The rest of the page scrolls normally.

### The selection

The selected rectangle gets a stronger outline, a translucent fill and three resize handles:
top-left, bottom-left and bottom-right.

The **top-right corner carries exactly one control: the X that deletes the slot.** It sits
on the corner where the top-right resize handle used to be — there is no handle behind or
beside it, and nothing floats above the rectangle. The X is drawn in red, so it can never be
mistaken for a handle, and its touch target is larger than the drawn circle.

The missing top-right handle does not limit resizing: the top-left handle changes the width
*and* the height, the bottom-right handle does the same from the other side, and the
bottom-left handle changes the width alone.

### Colours and labels

| Team | Colour |
|---|---|
| `ALLY` | blue |
| `ENEMY` | red |
| `GLOBAL` | neutral grey |

Colour is never the only cue: every rectangle carries a readable label such as `A-B1`,
`E-P2` or `G-B1`, followed by the user's own label when there is one.

## The contextual property editor

A long press on a rectangle opens a small editor next to it. The menu appears **as soon as
the long-press timeout is reached** — the finger does not have to move at all, and a small
movement afterwards does not delay it either. It never occupies a permanent part of the
canvas, and it positions itself inside the window, so it is never clipped off-screen in
either orientation.

It edits:

- Slot type: `BAN` / `PICK`.
- Team: `ALLY` / `ENEMY` / `GLOBAL`.
- Index.
- Optional label.

And it offers three actions:

- **Copy** — puts the slot into the application's own clipboard. This is deliberately *not*
  the Android system clipboard: a slot is calibration data that belongs to this app.
- **Paste** — creates a copy of the clipboard slot next to the current slot. The copy gets a
  **new unique id**, is offset a little from its source, stays inside the image and takes
  the next free index in its `(type, team)` scope so it never collides with an existing
  logical slot. The index can be changed afterwards.
- **Duplicate** — the same thing for the slot the editor belongs to.

The original slot is always preserved by a copy, a paste or a duplicate.

## Manual slots

The list under the canvas is the text view of the same annotation set.

Slots are grouped into **ALLY · BAN**, **ENEMY · BAN**, **ALLY · PICK**, **ENEMY · PICK** and
**GLOBAL**. Every row shows the type, team, index, the user's label and the normalized
rectangle, and every row is keyed by the slot's own id — the list never relies on positions.

- Tapping a row selects that rectangle on the canvas.
- Selecting a rectangle on the canvas highlights its row.
- *Select* and *Delete* act on that exact slot.
- *Edit geometry* reveals the `left` / `top` / `right` / `bottom` fields for typing exact
  coordinates.

*Undo* and *Redo* sit above the list. Undo covers a deletion, a creation and a move or
resize; it restores the correct slot id and metadata. A deletion is undone by putting the
slot back with its own id, never with a new one.

There is no fixed number of bans or picks: the user creates as many slots as the layout
needs. The index is always configurable and a duplicate `(type, team, index)` is refused.

## Fullscreen annotation editor

*Fullscreen* opens the image on its own screen, in whichever orientation the device is in.
The button is a compact icon in the corner of the canvas card.

- The image and the canvas fill the available phone screen, using the maximum width and
  height, with the aspect ratio preserved.
- The slot overlays stay perfectly aligned with the image.
- There is no zoom and no pan: the frame is fitted into the screen, and nothing about the
  view is adjusted while editing.
- Only the essential controls are visible: a compact collapse icon in the corner and the
  dismissible gesture hint.
- The collapse icon returns to the embedded editor and the previous screen arrangement.
- Android Back leaves the fullscreen editor first, and only then the Vision Lab.
- The orientation is never forced.

## What the controls mean

| Control | What it does |
|---|---|
| **Freeze** | locks the current frame before annotation |
| **Resume** | returns to live frames |
| **Slot** | a manually marked region where a ban/pick hero portrait is expected |
| **Process Frame** | resizes and prepares the frame only; no recognition exists yet |
| **Calibration profile** | saves and restores normalized slot coordinates for matching screen layouts |

The screen keeps stating **"Detection: not implemented"**.
