# Phase 2 — The map draws the mountains as the signposts mark them, and shows where you are

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
builds on [ADR-0007](adr/0007-osm-data-ships-per-mountain-range-as-play-asset-packs.md) and owns one
of the two decisions that ADR leaves to "the map phase": the filter. It works inside
[ADR-0008](adr/0008-kozica-never-opens-a-network-connection.md). `CONTEXT.md` is owed nothing:
**Trail**, **Path**, **Hut**, **Shelter** and **Region** already name everything the map draws.

**What closing this phase means.** Today the release build opens on a placeholder, and only a debug
screen draws the Tatry, as plain lines. After this phase the app opens on the map, in the release
build too. It draws every Trail in its signpost colour and symbol, with two Trails on one way drawn
side by side, Paths apart from them, and Huts, Shelters, peaks, passes, springs, viewpoints, the way
in and out, the land, national-park boundaries and the state border, in light and dark. One tap
shows where the user is, with accuracy and heading. The difference a device can be asked about: a
debug build with the Tatry on the phone shows a yellow and a black Trail sharing a way, both drawn,
and the position dot over them, with no network permission in the merged manifest. The dot gets
there by § 4's `PositionReceiver` broadcast, not a walk; R9 is that reading.

**`PLAN.md`'s paragraph is split, and amended with this file.** It bundled the map with every Region
and the picker that downloads them. The two halves are blocked by different things: the map can be
built and read against the Tatry archive already on the developer's machine, but a picker that
reaches users needs the asset-delivery library in the release build, which is phase 1's checkpoint E
and waits on Play work. By `PLAN.md` rule 2 they are two phases. This one draws; **phase 2b** builds
every Region, owns the Region list, and brings the picker and "Download all".

**Phase 2b owns three things the split would otherwise leave with two owners or none:**

- **Every Region's polygon, `tatry.poly` included.** Phase 1's R5 found its Slovak half draws
  nothing, because the extract stops at the state border; whether a polygon follows that border
  exactly is 2b's question too. This phase draws the Tatry from the polygon as it stands.
- **Checkpoint E.** 2b takes it over, and phase 1 closes on its phone readings alone. Phase 1 § 5
  stays E's brief, and E1 and E2 move into 2b's file when it opens.
- **ADR-0007's list of Regions**, fixed when 2b opens. This phase fixes the filter. ADR-0007's
  amendment of 2026-10-19 records the split.

---

## 0. What this phase inherits

- **A gate nobody has read.** Phase 1's R1, whether MapLibre draws with every network permission
  stripped, was deferred on 2026-10-19 with R8, R9 and R10's screen. `DOD.md` makes them due before
  this phase builds on MapLibre, and phase 1's § 1 holds their verdicts. This phase's A is those
  readings.
- **A thin tile schema.** `scripts/map/KozicaProfile.java` emits water, ways with a sorted list of
  Trail colours, and named peaks: what phase 1's gate and readings needed and nothing else. Phase 1
  left the full schema to this phase, under three rules that stand: no compression inside the file,
  deterministic output, one archive per Region.
- **Trail colours read the way the profile reads them.** A Trail's colour is the first field of its
  relation's `osmc:symbol`, and `colour=` only when there is no symbol. 140 ways in the Tatry
  archive carry two or more colours (phase 1, R10).
- **A debug map, not a map.** `DebugMapActivity`, the gate style and Noto Sans glyphs live under
  `app/src/debug/`. `RegionFiles` there reads the Tatry pack's state, falling back to a hand-pushed
  archive in `files/regions/`.
- **The palette is waiting for a map.** Phase 0 § 3 fixed four seeds on the condition that nothing
  the app paints over the map reads as a Trail colour, and left the reading to this phase.
- **Rule 4's location row is this phase's.** Precise location, asked on the first tap on "show me
  where I am", never at launch; fine and coarse together; and from API 31 a third answer,
  approximate only.
- **MapLibre merges the fine and coarse location pair** with its three network permissions
  (ADR-0008). The network three stay stripped; the location pair becomes the ask.

## What is in, and what is deliberately not

**In:** the map screen in `main/`, replacing Home's placeholder, drawn by MapLibre with every
network permission stripped from the release build. The full tile schema, which is the filter, and
the style that draws it, in light and dark. The position: the ask, the dot, accuracy, heading, and
what the user sees for each answer. The OSM credit and data date on the map. ODbL and the font's OFL
on the licences screen. The palette reading. The camera remembered across launches, as one stored
key in `AppPreferences`: it lands before the door freezes keys (`PLAN.md` rule 1), and a wrong
value costs one bad first frame.

**Not in, and each with a reason rather than a phase:**

- **Any Region in the release build.** The release map draws whatever Region is on the phone, and in
  this phase nothing can put one there: the asset-delivery library stays debug-only until phase 2b.
  The release map therefore shows its empty state, "no Regions downloaded", which ADR-0007
  requires anyway. No OSM-derived data is conveyed, so the ODbL offer is not yet owed.
- **The Region list, every Region's polygon, and the picker.** Phase 2b's. One Region is enough to
  draw and read every layer; building nine would only multiply the readings.
- **Search, tapping a feature for its details, and route planning.** Phase 5 brings search and
  routes, and a details sheet needs the copy and layout decisions that come with them.
- **Contours and hillshading.** Phase 4: a DEM pipeline with its own licence.
- **Following the user as they move.** One tap centres the map on the position once; the map does
  not then chase the dot. A camera that moves itself under a finger is a fault report in waiting,
  and recording a hike is phase 6.

**The tempting one** is drawing Trails from a ready-made outdoor style, such as OpenTopoMap's look
or an OpenMapTiles schema with a hiking overlay. It looks like a week saved. But those schemas carry
hiking routes as separate relation geometry or not at all, and none of them offsets two Trails on
one way, which is the thing a Polish trail map has to get right: a junction where a red and a blue
Trail share a way for a kilometre is where people take the wrong one. The schema stays ours, small,
and shaped around that.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The gate** — phase 1's deferred R1, R8, R9 and R10's screen | readings only | — |
| **B** | **The map in `main/`** — MapLibre in the release build with its network permissions stripped, Home becomes the map, its empty state, credit and licences | `feat:` | A |
| **C** | **The schema and the style** — the filter, Trails side by side with their symbols, everything else `PLAN.md` lists, light and dark | `feat:` | B |
| **D** | **You are here** — the ask, its three answers and location off, the dot, accuracy, heading, a stale fix | `feat:` | B |
| **E** | **The palette** — phase 0's reading over the finished style | readings, or `fix:` if a seed moves | C, D |

**A goes first because B is a bet otherwise.** Phase 1 built B–D on the assumption that MapLibre
draws offline. This phase would put that assumption into the release build. If R1 takes phase 1's
verdict 2, the renderer changes, and B–D here are rewritten for Mapsforge before any of them is
built.

**B is small on purpose.** It moves the debug map into `main/` and makes it the app's first screen,
with the style phase 1 already has. Everything the release artifact gains in this phase — MapLibre's
native code, the stripped permissions and feature, the licences — lands in that one commit, so one
`aab-permissions.py` run reads all of it.

**C and D are independent.** C changes the profile, the archive and the style; D changes the screen
and the manifest. Either can merge first. E reads both, so it comes last.

---

## 1. The gate

Phase 1 § 1, unchanged: the question, the apparatus and the four verdicts are written there and are
not re-argued here. R8 and R9 are phase 1's too, with their verdicts in phase 1 § 3. The readings
are recorded in phase 1's table, where their rows are; this phase's R1 points to them.

If R1 takes verdict 1, this phase goes ahead as written. If it takes verdict 2, this file stops at A
and is rewritten for Mapsforge/VTM before B. If it takes verdict 3, MapLibre draws only with the
location pair kept: B keeps the pair rather than stripping it, and D's document and allowlist
changes move into B, because the release then declares the pair before the ask exists. Those are
the EXPECTED rows, the two location features, and the lines in `privacy-policy.md` and
`play-app-content.md`.

## 2. The map in `main/`

- **MapLibre becomes `implementation`**, still `android-sdk-opengl` (phase 1 § 1: the default
  `android-sdk` is Vulkan, and its `uses-feature` would be a Play device filter). The release
  artifact gains about 13 MB of native code per ABI, which phase 1 named as a known gap for this
  phase to decide. It is decided: the map is the app.
- **The main manifest strips the three network permissions** with `tools:node="remove"`, moved from
  the debug manifest. `aab-permissions.py` already forbids them, so its run on the release bundle is
  the proof. The location pair is also stripped in B and returns in D, with its ask, unless R1 takes
  verdict 3 (§ 1).
- **It strips MapLibre's `<uses-feature android:name="android.hardware.wifi">` too**, with
  `tools:node="remove"`. Phase 1's R2 read it merged at `required="false"`. The app never uses
  Wi-Fi (ADR-0008), so the feature is removed rather than allowed, and `EXPECTED_FEATURES` stays
  empty in B. `aab-permissions.py` fails on any feature missing from that map, whatever its
  `required`.
- **`MapLibre.setConnected(false)` runs in `MainApplication.onCreate`**, before any `MapView` can
  exist (ADR-0008's consequence).
- **`ui/map/MapScreen.kt`** replaces Home's placeholder. It is an `AndroidView` around `MapView`, as
  in the gate: maplibre-compose is still out for the Kotlin pin (phase 1, *the tempting one*).
  `MapViewModel` holds one immutable UI state: every Region's state, the camera and the position.
- **Which Regions are on the phone is a seam with two halves.** `data/regions/Regions.kt` in `main/`
  defines a `Flow` of every Region's state, not only the ready ones, with the states `RegionFiles`
  already has: not downloaded, downloading, waiting for confirmation, update in progress, ready (a
  path and an OSM date), failed. ADR-0007 requires an "update in progress" state, and a list of
  ready Regions alone would show "No Regions downloaded yet." to a user whose Region Play is
  patching. The map draws the ready ones. The debug half is `RegionFiles`, pack or hand-pushed. The
  release half, in `src/release/`, emits an empty list until phase 2b gives it the library. Same
  shape as `DebugSettings.kt`'s seam.
- **The style is a template the app expands**, in `src/main/assets/map/` with its glyphs. A style
  layer binds to one source id, so two static files cannot hold layers for Regions not yet on the
  phone. The app adds one source per ready Region, `pmtiles://file://<path>`, and repeats each
  layer of the template per Region, with the Region in its id. The order is by layer, not by
  Region: every Region's land, then every Region's water, and so on up to the labels, so where two
  Regions' tiles overlap one Region's land never paints over the other's Trails.
- **A light/dark switch or a change in the ready Regions rebuilds the whole style.** `setStyle`
  drops every source and layer added at runtime, and the `LocationComponent` with them, so the
  expanded style is set as a whole and the component is activated again, with the last fix, in the
  style-loaded callback. R6 reads it.
- **The camera** is stored in `AppPreferences` (latitude, longitude, zoom) when the screen stops,
  and a launch opens on it. With none stored, a launch opens on the ready Regions' bounds; with no
  Region ready, on a fixed view over southern Poland's mountains, from the Karkonosze to the
  Bieszczady: 49.0° N 14.8° E to 50.9° N 23.0° E. Process death keeps the camera through saved
  state as well.
- **The empty state** is the map's own background with one line and no button, because in this phase
  there is nothing to press. With no Region ready, the line depends on the states: "Map data is
  being updated." when any Region is in "update in progress", and "No Regions downloaded yet."
  otherwise. Play invalidates every pack in one app update, so a Region updating beside a ready one
  is not drawn and not named here; 2b's picker shows it. Phase 2b adds the button.
- **The credit and the date.** "© OpenStreetMap contributors" sits on the map, linked to the
  copyright page through `ui/common/Intents.kt`, and beside it "OSM data: <date>" for the oldest
  ready Region. Every Region in a release comes from one pin, so the dates differ only while one
  pack is patched and another not yet; then the map is no fresher than its stalest part, and that
  is the date a hiker needs. MapLibre's own attribution control and logo are turned off. The
  control is an (i) icon whose dialog, titled "MapLibre Android", lists each source's attribution
  and opens its link; for planetiler's archive that is OSM's credit, but the house rule wants the
  credit itself visible on the map and linked, not behind a tap.
- **Licences.** `app.cash.licensee` picks up MapLibre's BSD-2-Clause by itself, but supplies no
  text. `BSD-2-Clause.txt` moves from `src/debug/assets/licences/` to `src/main/assets/licences/`,
  and `OFL.txt` from beside the debug glyphs to the same directory, and the `licensee` block's
  "debug only in phase 1" comment goes with them. ODbL and Noto Sans's OFL are not dependencies, so
  they become two hand-written entries beside the generated list, and ODbL's text joins them.
- **The debug map goes.** `DebugMapActivity` and its Settings row are removed; the debug section
  keeps "Download Tatry", "Remove Tatry" and the pack state.

Copy: "No Regions downloaded yet.", "Map data is being updated.", "© OpenStreetMap contributors",
"OSM data: %1$s".

## 3. The schema and the style

**The filter is the profile.** ADR-0007 has `osmium tags-filter` drop what nothing in the app reads.
But planetiler emits only what the profile names, so a second list in a filter step would only have
to be kept in step with the first. The cut goes straight to planetiler, and the layer table below is
the filter. ADR-0007's amendment written with C records that.

| Layer | From OSM | Min zoom | Attributes |
| --- | --- | --- | --- |
| `land` | `natural=wood\|scrub\|grassland\|heath\|scree\|bare_rock\|glacier`, `landuse=forest\|meadow\|residential` | 8 | `kind` |
| `water` | `natural=water`, `waterway=river\|stream` | 8 (rivers), 12 (streams) | `kind` |
| `boundaries` | lines: the member ways of `boundary=national_park` relations, `boundary=protected_area` with `protect_class=2`, and `boundary=administrative` with `admin_level=2` | 6 | `kind` |
| `roads` | `highway=` motorway to `unclassified`, `residential`, `service`, `track` | 8–13 by class | `kind` |
| `paths` | `highway=path\|footway\|steps\|bridleway`, `sac_scale` | 12 | `kind`, `sac_scale` |
| `trails` | each `route=hiking` relation's member ways, less `state=proposed` and `abandoned` (phase 7a § 2) | 10 | `trail` (the Trail's relation id, phase 6c § 2), `colour`, `symbol`, `index`, `count`, `union_index`, `union_count`, `ref` |
| `bike_routes` | each `route=bicycle` and `route=mtb` relation's member ways, as phase 7a § 2 reads them | 10 | `bike_route` (the relation id), `kind`, `colour`, `network`, `ref`, `index`, `count` (the union's), `dir` on a one-way way |
| `pois` | nodes, and a point on the surface of each area: Huts `tourism=alpine_hut`; Shelters `tourism=wilderness_hut`, `amenity=shelter` but not `shelter_type=public_transport`; `natural=peak\|saddle\|spring`, `mountain_pass=yes`, `tourism=viewpoint`; the way in and out: `amenity=parking`, `highway=bus_stop`, `railway=station\|halt` | 11–14 by kind | `kind`, `name`, `ele` |
| `places` | `place=town\|village\|hamlet` | 8–12 by kind | `kind`, `name` |

- **A Trail is one feature per Trail per way, not a list on the way.** R10's sorted list cannot be
  drawn: a style cannot loop over it. So the profile emits a way that `count` Trails share `count`
  times, each with its own `colour`, `symbol` and `index` in that sorted order. The style offsets
  each by `(index - (count - 1) / 2) × width`, so two Trails sit side by side and three keep the
  middle one on the way. Sorting by colour, then by relation id, keeps the order stable between
  builds. Phase 7a adds the union: the Trails, then Touring, then MTB Bike routes, each sorted that
  way, one order over both layers, in `union_index`/`union_count` on a Trail and `index`/`count` on a
  Bike route. The first of them is the reference relation below.
- **A shared way's direction follows the stretch, not the compass.** `line-offset` is signed
  against the line's direction, and OSM does not orient the ways of a relation consistently: where
  two ways of one shared stretch point opposite ways, red and blue would swap sides at the node
  between them and draw an X. A compass rule such as "west first" fixes that only while the
  stretch keeps running one way in longitude; on a serpentine, or a stretch that bends from
  north-east to north-west, two meeting ways get opposite directions and the X comes back. So the
  profile orients each way by a reference relation: of the Trails sharing it, the one that sorts
  first by colour, then by relation id, the same order as `index`. The way is oriented to continue
  from the previous member of that relation, by the relation's member order and the ways' end node
  ids. If neither end connects to it (a gap in the relation, or the first member), the way is
  oriented to lead into the next member. If neither applies, it falls back to west first, or
  south first when its ends share a longitude. Along a stretch whose set of Trails does not
  change, the reference relation stays the same, so `index` keeps its side. A tile post-process
  that merges lines must keep the direction.
- **The orientation needs each way's end node ids and each relation's ordered members.**
  planetiler 0.10.2's first pass hands the profile both: `preprocessOsmWay` gets each way's node
  ids, and `preprocessOsmRelation` each relation's members in order, ways before relations. So
  the profile keeps every way's two end nodes, and no pre-pass or sidecar is needed. The pass runs
  on several threads, so the map is concurrent, and nothing read from it depends on their order.
- **A Trail may change side at a junction, and that is accepted.** `index` is per way, so a blue
  Trail beside a green one is index 0, and becomes index 1 where a red Trail joins. Paper trail
  maps do the same; a fixed slot per Trail would leave gaps where its companions are absent.
- **The symbol is the `osmc:symbol` foreground**, such as `red_bar`, `red_dot`, `red_cross`,
  `red_triangle`, `red_corner`. It is drawn as an icon repeated along the line from zoom 14. A
  foreground the sprite sheet does not hold falls back to the bar in the Trail's colour, never to
  nothing. A `symbol-placement: line` layer ignores `line-offset`, so the symbol layer takes the
  same expression as a data-driven `icon-offset` perpendicular to the line, with
  `icon-keep-upright` off so a symbol does not flip to the other side, and overlap allowed so two
  Trails' symbols on one way do not hide each other. The sprites are generated by
  `scripts/map/sprites.py` from the shapes × the five Trail colours, plus the fallback colour and
  the POI icons (Hut, Shelter, peak, pass, spring, viewpoint, parking, stop, station), and committed
  with the style, so the build needs no network.
- **A hiking relation is a Trail whatever its colour says.** Today's profile returns no Trail for a
  `route=hiking` relation with neither `osmc:symbol` nor `colour=`, so its ways draw as Paths: a
  marked Trail shown as unmarked, which `CONTEXT.md` forbids. C changes phase 1's profile to emit
  every hiking relation. A colour missing or outside the five is drawn at Trail width in a neutral
  that reads as none of them, never as a Path; its symbol falls back to the bar in that neutral.
  Polish names (`czerwony`, `niebieski`, `zielony`, `żółty`, `czarny`) and the five pure hex values
  (`#ff0000` and so on) map to their colour first; nothing else is guessed, so orange is not red.
  R4 counts the relations that reach the fallback.
- **Boundaries are lines from the relations' member ways.** A Region's cut holds the state border's
  relation and a national park's only in part: `build-map-data.py` cuts with osmium's `smart`
  strategy, which completes multipolygon relations only, and these are `type=boundary`. So their
  polygons cannot be assembled, and their member ways are mostly untagged. The profile reads the
  relations in `preprocessOsmRelation`, as it does Trails, and emits each member way through
  `relationInfo` with the relation's `kind`. `admin_level=2` is matched with
  `boundary=administrative`, because alone it also matches other things. A park mapped as one
  closed way is drawn from that way's own tags. R5 reads the border along the Tatry ridge.
- **POIs mapped as areas are points too.** Huts are often a building polygon, and parking far more
  often an area than a node, so the profile emits `pointOnSurface()` for areas in `pois`; a
  point-only layer would drop them with nothing to say so. `amenity=shelter` excludes
  `shelter_type=public_transport`, or a village bus stop would show as a Shelter; a picnic shelter
  on a Trail is still a roof a hiker can use. POIs are drawn with their sprite icon and their name.
  R4 counts Huts in the cut and in the archive.
- **Paths are drawn so they can never pass for a Trail** (`CONTEXT.md`): thin, dashed, in a neutral,
  never in a Trail colour, and drawn under Trails.
- **Both style files are generated by one script**, `scripts/map/gen_style.py`, from one source:
  `style-light.json` and `style-dark.json`, the templates § 2's app expands, committed and picked
  by the app's own night mode (`theme/NightMode.kt`), not by the system's alone. Two hand-kept
  files would drift, and a seed change would not reach them.
- **Trail colours are the signposts' red, blue, green, yellow and black, the same in light and
  dark.** This is the exception to "colours come from `MaterialTheme`" that phase 0 § 3 promised to
  record, and `CLAUDE.md` records it with C. They are the only literal colours in the style.
- **Every other colour of the style comes from the scheme.** The base colours (land, water, roads,
  labels, Paths, casings, the fallback neutral) are read from the scheme `scripts/gen_scheme.py`
  generates, as roles or tones of its palettes, so the house rule's "edit the seeds and regenerate"
  stays true for the map, and E's seed change reaches it. `gen_scheme.py` runs `main()` on import
  today, so it gains a `__main__` guard for `gen_style.py` to import it.
- **Casings keep the two hard Trail colours visible**: yellow gets a dark casing in the light style,
  black a light one in the dark style. Phase 0 § 3's rule holds in both modes, and yellow on light
  land is the usual failure.
- **Labels are Polish names**, the `name` tag, in Noto Sans with the glyph ranges 0–255, 256–511
  (Latin Extended-A: *ł*, *ś*, *ż*, *ą*) and 8192–8447 (*„* *”* *–* *’*, which hut and shelter
  names carry). A missing range draws nothing, silently, so R6 derives the ranges from the
  archive's names rather than trusting this list, and reads a label with each.
- **Determinism is re-read.** The schema change rebuilds the archive, so phase 1's R4 is taken
  again, and the archive's size and the one-Trail patch are re-read against R5 and R7.

## 4. You are here

The ask, and every answer to it, in one table (`PLAN.md` rule 4):

| State | When | What the user sees |
| --- | --- | --- |
| Not asked | Until the first tap on the position button | The button, nothing else. No ask at launch |
| Precise | Fine granted | The dot, the accuracy circle, the heading cone; the map centres once |
| Approximate only | API 31+, coarse granted, fine not | The large accuracy circle, no heading cone, and a banner: "Approximate location only. Allow precise location to see where you are on a Trail." with "Allow", which asks for precise again, then "Settings" once that is refused |
| Denied | The ask refused | The button stays; a tap asks again while `shouldShowRequestPermissionRationale` is true, and otherwise opens the app's settings |
| Location off | Granted, but the system switch is off | A banner: "Location is turned off." with "Settings" |
| Stale | No fix for 30 s, or 15 min with approximate only | The dot greys, and the banner says "Last position %1$s ago." With approximate only, that line joins the approximate banner rather than a second one |

- **Fine and coarse are asked together**, with `RequestMultiplePermissions`, as Android requires
  from API 31 for the approximate option to be offered.
- **Every state but "not asked" is re-read** on each `ON_RESUME`, and the switch also on
  `LocationManager.MODE_CHANGED_ACTION` while Home is started, because the quick-settings tile
  flips it without pausing the activity: `checkSelfPermission` and
  `LocationManagerCompat.isLocationEnabled` can both be read back reliably, so the banners use
  `WarningBanner` (`ui/common/Surfaces.kt`). The compat call, because
  `LocationManager.isLocationEnabled` is API 28 and `minSdk` is 26.
- **Re-asking after a denial is decided by `shouldShowRequestPermissionRationale`.** From API 30 a
  second denial makes the request return denied with no dialog, and nothing reports that directly.
  So after a denied result, the next tap asks again while the rationale call is true, and otherwise
  opens the app's settings. The first tap of each launch always asks: at launch "never asked" and
  "permanently denied" both read false, and telling them apart would take a stored key. A
  permanently denied user therefore gets one silent tap per launch before Settings opens. That is
  accepted, rather than a key added to "fix" it.
- **Approximate only re-asks for precise in the app first.** On API 31+, with coarse granted,
  asking for fine and coarse again shows the system's precise-upgrade dialog, so the banner's
  action is that ask, and Settings only once it is refused. Opening Settings first is the heavier
  hand-off.
- **The app reads location itself**, through `LocationManagerCompat`: the platform's fused provider
  where the phone has one, else GPS, or `NETWORK_PROVIDER` with approximate only, because GPS needs
  the fine permission. R8's `SecurityException` grep catches a provider asked for without its
  permission. None of them is Play services. MapLibre's `LocationComponent` is
  activated with `useDefaultLocationEngine(false)` and fed each fix with `forceLocationUpdate`, so
  there is one reader of location, not two. The assistance that speeds a first fix is Android's,
  not ours (ADR-0008).
- **The dot is that `LocationComponent`**, in `RenderMode.COMPASS`. Its own stale state is off: its
  timer restarts when a fix is *received*, never reads the fix's age, and on activation an hour-old
  last-known fix would draw as fresh for 30 s. The grey look is applied from `PositionAge` instead.
- **The position button centres the map once.** It does not follow, and a pan does not need to be
  undone.
- **A position outside every ready Region is still drawn**, over the empty background, because "you
  are outside the map" is the information.
- **The stale line's age is a safety value.** A position shown as current when it is minutes old
  puts a hiker on the wrong side of a ridge, so its age and its threshold are a pure function with a
  test (below). The age is the fix's `elapsedRealtimeNanos` against
  `SystemClock.elapsedRealtimeNanos()`, a clock no user or network change can move, so a clock
  change cannot happen rather than needing a case.
- **Approximate only has its own threshold, 15 minutes.** Android throttles a coarse-only client to
  fixes minutes apart, about ten on current releases, so at 30 s "stale" would be the steady state
  and the banner would always say so. 15 minutes clears that interval with room for a late fix and
  still marks a position from the previous valley as old. The threshold lives in `PositionAge` with
  the other, and R7 reads the real coarse interval on the phone; a reading longer than 15 minutes
  moves the threshold.

- **A debug build can put "you are here" anywhere.** Reading the dot, its accuracy circle, the
  stale state and a position outside every Region should not need a hike. So the real reader stays
  in `main/`, and a variant seam, the same shape as `DebugSettings.kt`, decides only what wraps it:
  the release half passes it through, and the debug half adds an override that can swap its fix
  for a chosen one. A long press on the map puts the position there; a debug row returns it to
  the real reader; `PositionReceiver`, an `adb` broadcast in `SeedReceiver`'s pattern, sets
  latitude, longitude and accuracy for scripted readings:

  ```bash
  adb shell am broadcast -n io.github.srednimax.kozica.debug/app.kozica.debug.PositionReceiver \
    -f 0x00000020 --ef lat <lat> --ef lon <lon> --ef accuracy 10
  ```

  A chosen fix takes the same path as a real one, `forceLocationUpdate` and `PositionAge`, with its
  `elapsedRealtimeNanos` stamped at the moment it is set, so going stale is also testable. It
  carries no bearing: in `RenderMode.COMPASS` the heading cone follows MapLibre's `CompassEngine`,
  the phone's real compass, and the debug half does not fake it, so the cone is read by turning
  the phone. The chosen position lives in memory
  only, so process death or the debug row clears it; backing out and reopening the app usually
  keeps the process, and the chosen fix with it. It replaces only the fix, and only in the
  states that draw one, precise and approximate only: "not asked", "denied" and "location off"
  behave exactly as without it. Android's mock-location test provider is the later way to exercise
  the real provider without walking.

Copy: "Show my position", "Approximate location only. Allow precise location to see where you are on
a Trail.", "Allow", "Location is turned off.", "Last position %1$s ago.", "Settings".

## 5. The palette

Phase 0 § 3's reading, now that there is a map: the position dot, the accuracy circle, the heading
cone, the position button and the banners, over a yellow and a black Trail, in light and dark. If
any of them reads as a Trail colour, or a Trail disappears under one, a seed changes in
`scripts/gen_scheme.py`, the scheme is regenerated, and the reading is taken again.

---

## Tests

One, for D's stale position: `PositionAge`, a pure function from a fix's `elapsedRealtimeNanos`,
now's `SystemClock.elapsedRealtimeNanos()` and whether the grant is approximate only, to fresh or
stale and the age shown. It fails in the direction the app exists to prevent: a fix that is old
reported as fresh. The test covers the 30 s boundary on both sides, the 15 min approximate boundary
on both sides, a fix whose stamp is 0, which `Location` returns when a provider never set it and
which must read stale, never fresh, and an age that crosses an hour.

Nothing in C is a safety value. A Trail drawn in the wrong colour is a real fault, but it is the
profile reading OSM, and a reading on the phone and in the tile catches it, not a unit test.

## Documents this phase amends

- **`PLAN.md`**: phase 2's paragraph and status line, split into 2 and 2b, with this file.
- **ADR-0007**: two dated amendments. The first is written with this file: the map phase was split,
  phase 2 fixes the filter and phase 2b the list of Regions. The second comes with C: the profile
  is the filter, and no `tags-filter` step runs. It stays with C rather than joining the first
  because an ADR records what was built, and C's schema can still move under the build and R4.
- **`CLAUDE.md`**: with B, the stack table's MapLibre row loses "debug only", and the layout
  changes: `app/src/debug/` loses the offline map gate, `main/` gains `ui/map/` and
  `data/regions/`, and `app/src/release/` gains the Regions seam. With C, the colour house rule
  gains the map style's Trail colours as its one exception, and says the style's other colours are
  generated from the scheme by `scripts/map/gen_style.py`. With D, the layout gains the position
  seam's two halves around the real reader in `main/`: the override, the long press, the debug row
  and `PositionReceiver` in `app/src/debug/`, and the pass-through in `app/src/release/`.
- **`scripts/aab-permissions.py`**: with D, `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` move
  into EXPECTED, each naming the position button. D also declares
  `<uses-feature android:name="android.hardware.location">` and `android.hardware.location.gps`,
  both `required="false"`, so Play's device filtering never depends on what it derives from the
  permissions: the position is optional to the map. Both join `EXPECTED_FEATURES` as `False`, the
  map's value for `required`, its "Empty on purpose" comment is rewritten, and
  `play-app-content.md` gets their reason, since `EXPECTED_FEATURES` holds none.
- **`docs/privacy-policy.md`** and **`docs/play-app-content.md`**, in the same commit as D: the app
  reads the phone's location to draw it on the map, on the phone only, and never stores or sends it.
  Data safety stays "no data collected", because collection means leaving the device. The line
  "Would change any of it: location of any kind" is rewritten to say what does: storing it, which
  phase 6 does.
- **`DOD.md`**: phase 1's phone readings item closes with A. Its checkpoint E item names phase 2b
  as E's owner, written with this file.

## Known gaps, deliberately

- **The release map is empty until phase 2b.** A release from this phase reaches only the owner on
  internal testing, and its map says "No Regions downloaded yet." That is honest, and it keeps OSM
  data out of the release until the ODbL offer ships with it.
- **No feature can be tapped.** A Hut's name is on the map; its opening times, phone number and
  everything else in its tags are not reachable until a details sheet exists.
- **The compass is the phone's.** A heading cone from an uncalibrated magnetometer points wrong, and
  the app cannot tell. Android's own calibration prompt is the remedy.

## Readings

`PLAN.md` rule 3: filled in as the phase runs, from the device or the build, with the command that
read it. A reading that drives the phone waits until the owner says it is free.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The gate: phase 1's R1, R8, R9 and R10's screen | phase 1's Readings table | 2026-10-19, in phase 1's table: R1 **verdict 1**, R8 verdict 3 (checkpoint E moves to 2b), R9 passes after the `RegionFiles` fix, R10's screen shows Trails coloured |
| R2 | The release bundle declares none of the network three after B, and no `uses-feature`: MapLibre's `android.hardware.wifi` is stripped. After D, exactly the location pair beside AndroidX's own, and `android.hardware.location` and `.gps` at `required="false"` | `./gradlew bundleRelease && python3 scripts/aab-permissions.py` | After B, 2026-10-19: `1 permissions, all accounted for; none of the 8 forbidden ones present; 0 <uses-feature> declared`, the one being AndroidX's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; the AAB is 19.9 MB with MapLibre's native code. After D, 2026-10-19: `3 permissions, all accounted for; none of the 8 forbidden ones present; 2 <uses-feature> declared`, the location pair beside AndroidX's own, both features `required="false"` |
| R3 | The release map opens on its empty state and the credit, no crash, in English and Polish | `./gradlew installRelease` or the internal-testing build, `adb exec-out screencap -p` | 2026-10-19, API 36, `installRelease` signed with the upload key, then uninstalled: "No Regions downloaded yet." and the credit; in Polish "Nie pobrano jeszcze żadnego regionu." and "© Współtwórcy OpenStreetMap"; no crash; `dumpsys package` lists none of the network three or the location pair |
| R4 | Two builds of the new schema are byte-identical; its size and the one-Trail patch against phase 1's R5 and R7; the hiking relations that reach the colour fallback, by tag value; the Huts in the cut and in the archive | `python3 scripts/build-map-data.py --check-determinism`, then `--patch-from-extract`; `osmium tags-filter` over the cut and `decode-tile.py` over the archive's tile range for the counts | 2026-10-19, pin 4942: two builds byte-identical, 7,274,738 bytes, sha256 `059da7b7…9169`, against phase 1's 1,051,173; the one-Trail patch of phase 1's R7 is 429 bytes, 0.01 % of the archive raw and of it zstd. Of the cut's 221 hiking relations 5 reach the colour fallback: 4 with neither `osmc:symbol` nor `colour=`, and 1 `osmc:symbol=:blue::E3:yellow`; 8 ways carry one. Huts: 10 in the cut, all areas, and 10 in the archive at z14. Every POI kind reaches it: 483 peaks, 430 parkings, 314 passes, 183 stops, 80 springs, 54 viewpoints, 29 Shelters, 3 stations. The orientation, read off the tiles with pyosmium over the cut: at all 142 junctions between consecutive ways of one relation that share the same set of two or more Trails, one way's drawn end meets the other's drawn start, including the 10 where the OSM ways point opposite ways and the 13 where the stretch doubles back in longitude |
| R5 | A way shared by two Trails draws both, side by side, with their symbols, across a way boundary where the OSM ways point opposite ways, along a shared stretch that doubles back in longitude, and at a junction where a third Trail joins; Paths read as Paths; the state border runs along the Tatry ridge | debug build with the Tatry, `adb exec-out screencap -p` at zoom 15 over w22773921, then over the boundary, the doubling-back stretch and the junction picked from the tile decode | 2026-10-19, API 36, light, the camera written to `AppPreferences` before each launch. w22773921 at z15: red beside green, then green beside blue past Ťatliakova chata, each with its bar, the symbols paired along the line. w248684601 to w115396457, whose OSM ways point opposite ways, at z16: black stays north of blue across the node, no X. w243702628 to w243702631, where the stretch doubles back, at z16: green stays north of blue through the bend. w115396457 to w255573530, where a red Trail joins black and blue, at z16: black, blue, then red, the pair keeping its order. Paths are thin, dotted and grey under every Trail. The state border's dash runs beside the red Trail along the ridge at Kasprowy Wierch, z13 |
| R6 | The glyph ranges the names need, derived from the archive: the code points outside the shipped ranges in its `name` attributes, which must come back empty. Labels with *ł*, *ś*, *ż*, *ą* and a „ ” or – render, in light and dark; switching night mode with the map open keeps the Trails and the dot | `decode-tile.py` over the archive's tile range, as phase 1's R10, listing code points outside the shipped ranges; the debug build at places named with them; `adb shell cmd uimode night yes` / `no` | 2026-10-19, the archive half: no `name` in any z14 tile has a code point outside 0–255, 256–511 and 8192–8447; those past Latin-1 are all Latin Extended-A (*ą ę ł ń ś ź ż* and Slovak *č ľ ň š ť ž*). The Tatry's names use nothing in 8192–8447, which ships for other Regions' quotes and dashes. On the phone, API 36: *Wrzące Źródło* (ą, Ź), *Kościelisko* (ś), *Mała Królowa Kopa* and *Mały Wołoszyn* (ł), *Krzyżne* (ż) draw in light and dark. With the map open at Krzyżne and the dot on it, `cmd uimode night yes` then `no` swaps the style both ways and keeps the Trails and the dot. No „ ” or – could be read, because no Tatry name carries one |
| R7 | Each row of § 4's table, with the API level: the 31+ rows on the phone, the 26–30 rows (one dialog, no approximate) on an emulator, which the row says; and the interval between coarse fixes with approximate only | The debug position override off first, with `adb shell am force-stop io.github.srednimax.kozica.debug` or the debug row; then `adb shell pm revoke`/`grant`, the system location switch, a screencap per state; `adb logcat` timestamps of the fixes the app logs | 2026-10-19. **API 36, the phone:** not asked shows the button only, with no ask at launch. The first tap shows the system dialog with Precise and Approximate. Precise draws the dot and heading cone and centres once. Approximate only draws the 2 km circle, no cone, and the banner with Allow; Allow shows the precise-upgrade dialog, and after Keep approximate the action reads Settings. Denied: tap asks again, deny, tap asks again, deny (USER_FIXED), and the next tap opens the app's details. Location off, flipped with `cmd location set-location-enabled` while resumed: the banner shows and clears with no pause. Stale, through a long press: grey dot and cone, "Last position 34 sec ago.", in Polish "Ostatnia pozycja 34 sek. temu." (the first wording, *sprzed %1$s.*, read "34 sek.." and was changed). **API 26, the emulator:** one dialog, ALLOW or DENY. Deny, tap asks again with "Don't ask again", then the next tap opens Settings. Location off shows and clears. Its headless GPU drew no Trails and a black dot, so the look is the phone's reading only. **Coarse interval**, approximate only with the app in front for 20 min: fixes at 20:41:40, 20:51:32 and 21:01:32, so 9 min 52 s and 10 min 00 s apart; `dumpsys location` shows the 1 s request coarsened to `@+10m0s0ms LOW_POWER`. The 15 min threshold stands. That run also found a fix fed before MapLibre's dot had drawn its first frame was dropped: invisible with precise, which sends another a second later, but a blank map for ten minutes with approximate only. The dot now waits for its first fully rendered frame, and four cold starts in approximate only drew it |
| R8 | The app's own reader: which provider it got, no `SecurityException`, no Play services | The debug position override off first, with `adb shell am force-stop io.github.srednimax.kozica.debug` or the debug row; then `adb logcat -d \| grep -E 'SecurityException\|KozicaLocation'` | 2026-10-19: `provider fused, approximate only false` and `… true` on API 36, `fused` on API 26 too. First fix 2 s after start (16.7 m precise, 2000 m approximate). No `SecurityException` in any run. The release runtime classpath has no `com.google.android.gms` artifact; debug has only `play-services-basement` and `-tasks`, brought in by Play Asset Delivery |
| R9 | The palette over a yellow and a black Trail, light and dark | The dot put on a way the two share: `adb shell am broadcast -n io.github.srednimax.kozica.debug/app.kozica.debug.PositionReceiver -f 0x00000020 --ef lat <lat> --ef lon <lon> --ef accuracy 10`; then `adb shell cmd uimode night yes` / `no`, screencaps | 2026-10-19, API 36, the camera written to `AppPreferences`: the dot on w114309677, which only a black and a yellow Trail share, below Hala Gąsienicowa, at z16. **No seed moves.** Light: the dot is the primary brown in a white ring, the cone brown; at 80 m the circle is a faint brown wash that tints both Trails slightly, and both still read; stale, the dot and cone go grey and the banner is the pale error container, none of them a Trail colour. Dark: the dot is P80 peach in a near-black ring, which merges into the black Trail while the peach stays clear; the circle tints both Trails warmer. The black Trail's body merges into the dark ground, on rock and scree and under the circle, and it reads by its white casing alone, which stays clear on all three: which is how checkpoint C's dark style draws black, so it passes phase 0 § 3's "clearly visible". The stale banner is the dark error container and reads red, but as a card at the top, never as a line, and error is M3 baseline rather than a seed; no red Trail is in frame. The position button is the surface's, in both. Beside Murowaniec the Hut's ink is the dot's primary in both themes, told apart by shape alone: a ringed house against a filled disc with a cone |
