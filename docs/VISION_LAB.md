# Vision Lab

The Vision Lab is the second tab. Capture lives in the foreground service, so switching tabs
never stops it. The page has six cards: frame preview, annotation canvas, manual slots,
hero recognition, processing inspector and calibration profiles.

## Frame preview

Shows the pinned frame when one is frozen, otherwise the latest live frame, and reports source
size, display rotation, correction and source state. Controls:

- **Freeze / Resume** — pin the current frame for inspection or return to live frames. Freezing
  is also available from the capture notification and from the floating control while you are
  in another app; the pinned frame is an independent snapshot that live frames never replace.
- **Process Frame** — run preprocessing on the pinned frame, or on the latest live frame at the
  moment of the press. This only prepares pixels and geometry; identification is a separate
  action in the hero recognition card.
- **Clear** — release the pinned frame and processed result and cancel any processing in flight.

Frames stay in memory; nothing is written to disk from this screen.

## Annotation canvas

The oriented, uncropped frame is drawn inside a dark card. The workspace is sized from the
frame's aspect ratio with a usable minimum, so the image is never cropped or stretched.

The gesture rules live in one state machine (`AnnotationInteraction`):

| Interaction | Action |
|---|---|
| Short tap on a rectangle | Select that slot |
| Drag inside a selected rectangle | Move the selected slot |
| Long press on a rectangle | Open its contextual editor |
| Drag a resize handle | Resize the selected slot |
| Long press on empty image, then drag | Draw a new slot |
| Short tap on empty image | Nothing |
| Tap the X control | Delete the selected slot |

Consequences worth knowing: a tap never moves anything; a drag on an unselected rectangle only
selects it; moving past the touch slop before the timeout cancels a pending long press; and a
drag on empty space never creates a slot without the long press first. The canvas takes the
gesture from the first touch, so the page never scrolls during an annotation gesture.

The selected rectangle gets three resize handles (top-left, bottom-left, bottom-right) and a
red X at the top-right corner for deletion. The missing top-right handle does not limit
resizing: the other corners change both dimensions and the bottom-left changes the width.

Team colours are blue for `ALLY`, red for `ENEMY` and grey for `GLOBAL`, always accompanied by
a label such as `A-B1` or `E-P2`.

The **fullscreen editor** fits the image to the screen without zoom or pan, keeps overlays
aligned, and uses Android Back to leave fullscreen before leaving the tab. Orientation is
never forced.

## Contextual editor and clipboard

A long press opens a compact editor for the selected slot: type (`BAN`/`PICK`), team
(`ALLY`/`ENEMY`/`GLOBAL`), index and optional label. Invalid changes are refused with the
reason shown inline and the previous values kept.

- **Copy** puts the slot in the app's own clipboard, not the system clipboard.
- **Paste** creates a copy of the clipboard slot next to the target slot, which only anchors
  placement. The copy gets a new id, the next free index and a small offset.
- **Duplicate** does the same for the slot the editor belongs to.

The original is always preserved.

## Manual slots and profiles

The list under the canvas is the text view of the same annotation set, grouped by team and
type and keyed by slot id. Tapping a row selects the rectangle and vice versa. **Undo** and
**Redo** cover creation, deletion and geometry changes; a deleted slot is restored with its own
id. There is no fixed number of slots.

Calibration profiles save normalized rectangles and metadata for matching layouts. Saving,
updating and applying require a pinned frame, and applying first checks aspect ratio and
orientation. Corrupt or unsupported documents are never silently emptied; the only recovery is
**clear all calibration data**.

## Hero recognition

This card contains the identification flow added in Sprint 1B.

1. **Sync catalog** downloads public static hero data and reference images on explicit request.
   The catalog is cached privately for offline browsing.
2. Select a slot on the canvas, then tap **Identify hero**. The slot crop is extracted upright
   for the current rotation and rejected if it has too little visual information.
3. Candidates are shown as **similarity, not a probability**. The matcher is an uncalibrated
   baseline; a low score is reported honestly as no match.
4. **Confirm** stores the chosen hero for that slot. **Change…** opens manual correction by
   name, available from candidates, no-match, insufficient information, failed and confirmed
   states. **Clear** removes a confirmation.

Manual confirmation is always required; the app never selects a hero by itself.

## Processing inspector

Shows source and output dimensions, rotation, crop, scale, duration, status and errors, plus a
maximum processed dimension setting. Acquisition latency and processing duration are distinct
from identification time.
