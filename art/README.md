# Identity assets

Source art for the launcher icon and the Play listing. **Everything in `app/src/main/res` that draws
the mark is generated from here** — edit `mark.py` and regenerate, never hand-edit the path data in
the `VectorDrawable`s.

| Asset | Source | State |
| --- | --- | --- |
| `mark.py`'s MARK and HOLE curves | anchor points in `draw-mark.py` → `mark.py`, in place | drawn — provenance below |
| `drawable/ic_launcher_{background,foreground,monochrome}.xml`, `drawable/ic_notification.xml`, `mipmap-*`, `play-icon-512.png` | `mark.py` → `make-launcher-icon.py` | generated |
| `app/src/debug/res/`'s `drawable/ic_launcher_{foreground,monochrome}.xml` and `mipmap-*`: the mark with wheels in its crag, the debug build's until 7c | `mark.py`'s `CYCLING_PARTS` → `make-launcher-icon.py --cycling` | generated |
| `play-feature-graphic.png`, `play-feature-graphic-pl.png` (1024×500, English and Polish) | `mark.py` (+ `feature-background.png` if present) → `make-feature-graphic.py` | generated |
| `play-screenshots/<n>_<scene>-<tag>.png` | `scripts/screenshots.py --build release --numbered` → `pad-screenshot.py` | — |

## The mark

`mark.py` is the single definition, as **two cubic subpaths** — the silhouette and a hole punched
out of it. Both generators import it, so the launcher icon, the notification icon and the feature
graphic cannot drift into being different marks.

What ships is the chamois on its crag, both horns hooked back and a short tail; `draw-mark.py` holds the
anchors it is drawn from, and rewrites `mark.py`'s curves when one moves.

- **Recolouring is two values in `mark.py` and a re-run**: `GROUND` (the icon's field) and
  `MARK_INK` (the mark on it). `make-launcher-icon.py` writes every drawable from them, the
  background included — it used to be hand-written, and a second copy of a colour in a file nobody
  regenerates is the drift the first app built from this template found the first time its palette
  moved.
- The two colours are the icon's own, cream and crimson, chosen by the owner outside the app's
  scheme. **Judge a change at 44dp on a real home screen, light and dark.**
- The feature graphic takes the same two from `mark.py`, so the listing and the icon agree.
- **The hole must be a real hole in the path**, not a shape painted in the background colour. A
  themed launcher (API 33+) tints the whole monochrome layer one flat colour, and a painted hole
  simply vanishes.

## Where the art has to come from

**Use art you have the right to ship.** This is not a formality — it is the thing most likely to
block a first Play upload, and it is discovered late.

- An icon traced from an **emoji font** carries that font's licence. Noto Emoji is Apache-2.0 or
  OFL depending on the release, and both impose obligations on the *outlines* — which is what
  tracing copies.
- A **stock icon** is somebody's asset with somebody's terms, whatever the site's marketing says.
- **Art you drew, or art generated from your own prompt, is clean.** Keep a note of which, in this
  file, so the provenance survives you forgetting it.

Rendering *text* with a font is a different question from redistributing its *outlines*: the OFL
explicitly permits documents produced with a font. `make-feature-graphic.py` renders the wordmark
with Noto Sans on that basis and ships no glyph outlines.

## Replacing the mark

```bash
# 1. Move the anchors in draw-mark.py, then let it rewrite every path literal in mark.py.
python3 art/draw-mark.py

# 2. Regenerate everything that derives from it.
python3 art/make-launcher-icon.py       # icons, mipmaps, the 512² Play asset
python3 art/make-launcher-icon.py --cycling   # the debug build's icon and mipmaps
python3 art/make-feature-graphic.py     # the 1024×500 listing graphic
```

A mark that starts as an image rather than anchors can still come from `trace-mark.py concept.png`,
which prints MARK and HOLE to paste into `mark.py`; delete `draw-mark.py` then, or its next run
overwrites the trace.

`make-launcher-icon.py` prints a **safe-radius check** and will tell you when the art is clipped by
a circular launcher mask. It is a real constraint rather than a formality: a launcher may mask the
icon to a 66dp circle, so anything outside a 33dp radius from the centre is cut off. Shrink
`ART_SIZE` until it says `inside`.

Two sizes worth knowing, both already handled:

- The **notification icon** is scaled up relative to the launcher icon. At the launcher's own art
  size the mark would be a speck in a 24dp slot.
- The **flat mipmaps** are cropped to the 72dp a launcher actually shows of the 108dp adaptive
  canvas, so they match what the adaptive icon looks like rather than appearing to shrink.

## The feature graphic and Play's crop

Play shows the 1024×500 graphic in a **16:9 box with `object-fit: cover`** on the search-results
card, so 68px of each side never reaches a viewer. It is correct in every local preview and in the
Console's uploader, and wrong only on the surface most people see. `make-feature-graphic.py` lays out
against the cropped frame, prints the spans it measured, and **exits 1** when the copy is clipped or
runs into the mark — shorten the tagline rather than moving the margin.

A photographic ground is `art/feature-background.png` (cover-fitted, never stretched). Record where
it came from in the provenance table below.

## Provenance

| File | Where it came from | Licence / terms |
| --- | --- | --- |
| `mark.py` / `draw-mark.py` | drawn by hand as anchor points, round 3, from round-1 B, treatment a: both horns and a tail added to the owner's chosen round-1 drawing; no image traced | the repo's, all rights reserved |
| `mark.py`'s `CYCLING_MARK` and wheels | `draw-mark.py`'s `TYRES` and `WHEELS`, anchors drawn for phase 7a § 6 and chosen by the owner from rendered variants; no image traced | the repo's, all rights reserved |
| `feature-background.png` | *(if used)* | |
