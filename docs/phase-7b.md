# Phase 7b — A ride is planned along the Bike routes, one-way lines their way

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
relies on [ADR-0011](adr/0011-cycling-follows-marked-bike-routes-and-its-times-are-our-own-estimate.md)
(Bike routes only, distance and ascent first, the time our own estimate) and amends it twice: the flat
speed is the way's surface class rather than the Bike route's kind, and a riding time ships only once it
has passed § 1's gate. It relies on [ADR-0010](adr/0010-routes-are-planned-over-our-own-trail-network.md)
(the router, whose second set of edges phase 7a built), ADR-0001 (a saved Route gains two columns, the
first migration since phase 6) and ADR-0008 (the published times are read on the owner's PC, never by the
app). [`CONTEXT.md`](../CONTEXT.md) is owed nothing: **Route**, **Following** and **Pace** already say what
each is in Cycling. Planned with the owner on 2026-11-11.

**What closing this phase means.** Today a developer build can switch to Cycling and see the Bike routes,
but a long press there plans a walk along the Trails, and a saved Route forgets everything but its points.
After this phase, in Cycling, a long press plans a Route along the Bike routes the chips allow, never
against a one-way line, with its distance and ascent first, a warning from S2 in the direction it is
ridden, and, if § 1's gate passed, "≈ 1 h 40 min" from our own rule. It saves with its Mode and chip,
opens in them, replans when either changes, and can be followed on screen. A Bike route's sheet plans a
Route along it and exports it as GPX. The difference a device can be asked about: on the test phone, in
Cycling under MTB, a Route from the top of Twister to its foot takes Twister, and one from its foot to
its top does not.

**`PLAN.md`'s paragraph moves one thing.** It says the rule is "checked against published riding times
for 10–15 of our Bike routes". § 1 takes 14, tunes on 7 and judges on the other 7, and keeps 7 more in
reserve for a second attempt, so up to 21 are read.

---

## 0. What this phase inherits

- **Phase 7a built the Bike routes as data**: their edges in `trails.network` (format 2, each edge's
  sets saying Trail, Bike route or both), membership in `trails.edges`, the rider's direction in each
  edge's `ride` byte from `oneway:bicycle`, `oneway`, the relation's roles and `signed_direction`, and
  `mtb:scale` and `mtb:scale:uphill` in `edgeMtb` and `edgeMtbUphill`. Since 7a's B the router keeps a
  Route in Hiking off an edge without the Trail bit. What it does not store is **which kind** of Bike
  route an edge is on, so the chips cannot limit a Route yet, and **anything about the surface**.
- **Times are computed at build time.** `build-network.py`'s `walking_seconds()` writes each edge's
  seconds forward and backward from its length and its climb point by point, and the app only adds
  them. The riding time follows the same path, so the app gains no rule of its own.
- **A saved Route is its points.** `RouteEntity` holds a name and dates, `route_points` the points in
  order, and phase 5 § 5 plans it again on every open. Without a stored Mode a saved ride comes back as a
  walk. `APP_SCHEMA_VERSION` is 3.
- **6c's "Plan a Route along it" and "Export GPX"** work on any walk of edges; 7a left them off the Bike
  route's sheet for this phase (7a § *Not in*).
- **6b's Following** is on screen only, timed by the walking Pace, with a leaving rule of 40 m for 30 s
  read on foot (6b § 4).
- **The switch and the chips are developer-only until 7c** (7a), so everything here that a user sees is
  developer-only too. The migration and the network's growth reach release builds.

## What is in, and what is deliberately not

**In:** each edge's surface class and Bike route kinds, and its riding seconds each way, in
`trails.network` format 3; the published riding times for up to 21 Bike routes, and the rule tuned and
judged against them; the router in Cycling, along Bike routes only and only the chips' kind, one-way lines
their way; the Route sheet in Cycling with distance and ascent first, the S2 warning and, once the gate
has passed, the riding time; a saved Route's Mode and chip, a migration; the replan when the Mode or the
chip changes; Following in Cycling; "Plan a Route along it" and "Export GPX" on a Bike route's sheet.

**Not in, and each with a reason rather than a phase:**

- **The Pace per Bike.** It is learned from Rides, which 7c records. Until then a ride's time is the
  rule's alone, and the walking Pace never touches it: it was learned on foot.
- **The screen kept on, and the voice.** 7d's, since they are what makes following on a handlebar
  different from following on foot. Following itself is not, so it is here.
- **A riding time on a Bike route's sheet.** The Trail's sheet shows no walking time (6c § 3); the sheet
  says what the route is, and the Route planned along it says how long it takes.
- **Telling a ride from a walk in the Routes tab.** Only a developer build can save a ride until 7c
  opens the switch, and the bike glyph lives in `app/src/debug/` with the switch. 7c marks the row.
- **A physics model, or a time per kind of Bike route.** ADR-0011 rejected the first; the amendment
  below replaces the second.

**The tempting one: tuning until the numbers pass.** With the references in hand it is easy to keep
adjusting the rule and judging it on the same routes until every one is within 20 %. That proves the fit
and nothing else. § 1 fixes the split before a single reference is read, judges once, and after a second
failure ships no time at all.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | The references: 14 Bike routes picked and split, their published times read both ways | `docs:` R1's rows | — |
| **B** | The data: surface class, Bike route kinds and riding seconds in `trails.network` format 3, at a fresh pin; the base files' growth read on its branch | `feat:` (the map data refreshes) | — |
| **C** | **The gate**: the rule tuned on 7, judged on 7 | `chore:` the script, its verdict in R3 | A, B |
| **D** | The router and the Route sheet in Cycling, with no time | `feat:` | B |
| **E** | A saved Route's Mode and chip, the migration, and the replan on a switch | `feat:` | D |
| **F** | Following in Cycling, and a Route along a Bike route with its GPX | `feat:` | D |
| **G** | The riding time on the Route sheet and in Following | `feat:` | C passed, D |

**The gate is not first**, against the template, because it can veto only G. Planning, saving and
following stand without a time (§ 1's third verdict), so D, E and F do not wait for it; C needs B's surface class to
be tuned at all. **A needs no code** and starts at once: the references are read before B exists, so
nothing in the build can lean on them. **G does not merge if C fails twice.**

---

## 1. The gate: does our riding time land within 20 % of what riders are told elsewhere?

### The question, in the form that can be measured

For 7 Bike routes the rule was not tuned on, ridden both ways, is the median of |estimate − reference| /
reference at most 20 %, and is none above 40 %? The reference for a route and a direction is the midpoint
of mapy.cz's time and Komoot's at its default fitness for the same line (decided with the owner on
2026-11-11). Official guides' times are recorded beside them where they exist and counted nowhere: they
are scattered, mostly one direction, and written for different riders.

### The apparatus

- **The routes**, picked in A before any reference is read: 21 Bike routes, 7 MTB and 14 Touring, in
  three strata of 7, each stratum mixing flat and hilly, paved and rough, and Poland, Czechia and
  Slovakia, with Twister (r5738733) and one other one-way trail-centre line among the MTB ones. A seeded
  shuffle, its seed written in R1, deals each stratum into **tune**, **judge** and **reserve**. A one-way
  line counts one direction, so a stratum can hold 13 directions rather than 14.
- **The readings**: for each route and direction, the line drawn along its walk in mapy.cz's planner
  (cycling) and Komoot's (touring bike for Touring, mountain bike for MTB, default fitness), its time
  written down with the distance each gives, to catch a line that strayed. Both are read in the owner's
  Chrome through Playwright, said in chat before each session. **The reserve is read only after a first
  failure**, so it can influence nothing before it.
- **`scripts/riding-time.py`**, on the PC: for each route it takes the edges along its walk (7a § 2's
  walk), recomputes their riding seconds from B's per-point data with candidate constants, and prints the
  estimate against the reference. `--fit tune` fits the constants by least squares on log error,
  each bounded within the published routers' spread (`cycling-research.md` § 2); `--judge judge` prints
  the median and the worst. The constants it settles go into `build-network.py`, and the network is
  rebuilt once, in G.

### The verdicts, decided now rather than argued about later

Decided with the owner on 2026-11-11:

1. **Median ≤ 20 % and none > 40 % on the judge set** → the constants are committed and G ships the time.
2. **It fails** → tune once more, on tune and judge together, and judge on the reserve by the same marks.
   Pass → as 1.
3. **It fails again** → **no time in Cycling.** The Route sheet shows distance and ascent only, G does not
   merge, and the phase closes on D, E and F. The rule and both comparisons stay recorded in R3. A later
   attempt that passes the same marks, on routes it was not tuned on, adds the time in its own `feat:`.

### The verdict, taken *(date)*

## 2. The data

- **A surface class per edge**: paved, unpaved or rough, from `surface` where a way has one, else from
  `highway` and `tracktype`, which every way has. The table B starts from, C's to move a member between
  classes and say so in R3:

  | Class | `surface` | Else `highway` / `tracktype` |
  | --- | --- | --- |
  | **paved** | `asphalt`, `concrete*`, `paving_stones`, `sett`, `chipseal`, `paved`, `metal`, `wood` | roads, `cycleway`, `service`; `track` with `grade1` |
  | **unpaved** | `compacted`, `fine_gravel`, `gravel`, `unpaved`, `dirt`, `earth`, `ground`, `grass_paver` | `track` with `grade2`, `grade3` or none |
  | **rough** | `rock`, `stone`, `pebblestone`, `roots`, `mud`, `sand`, `grass`, `woodchips` | `path`, `footway`, `bridleway`, `steps`; `track` with `grade4`, `grade5` |

  A way's class is part of what makes two pieces one edge (`walks_like`), so an edge has exactly one.
- **The Bike route kinds per edge**: the sets byte splits 7a's Bike route bit into Touring and MTB, so an
  edge on both kinds carries both, and the router can keep to the chips.
- **Riding seconds forward and backward**, computed as walking seconds are, along the edge's points with
  their heights: each stretch at its class's flat speed, slowed uphill by a time per metre climbed,
  faster downhill by a factor up to a descent cap per class, and capped below by `mtb:scale` in the
  direction ridden (`mtb:scale:uphill` uphill where it is tagged). No stretch goes under 4 km/h, the
  pushing speed GraphHopper and OSRM use: a rider who cannot ride it pushes. An edge on no Bike route
  gets none. The starting constants, from `cycling-research.md` § 2, C's to tune:

  | | paved | unpaved | rough |
  | --- | --- | --- | --- |
  | Flat | 20 km/h | 15 km/h | 10 km/h |
  | Descent cap | 40 km/h | 25 km/h | 15 km/h |

  Climbing 10 s per metre up; descents ×1.5 the flat before the cap; `mtb:scale` caps at S2 10 km/h, S3
  6 km/h, S4 and over 4 km/h.
- **`trails.network` goes to format 3** with the three arrays and the split bit. The base files' growth,
  against 7a's (`trails.network` 15,198,240 B, `trails.edges` 275,796 B), is R2, recorded, not judged:
  the owner decided on 2026-11-01 that size blocks nothing.

## 3. Planning in Cycling

- **The router plans over the edges the chips allow**: in Cycling under All any Bike route edge, under
  Touring or MTB only that kind's, and never an edge against its `ride` direction. In Hiking it is as it
  was. The long press, the points, Reverse and the sheet are phase 5's; Reverse on a Route with a one-way
  stretch plans whatever the router finds the other way.
- **A point off the Bike routes** joins the nearest edge the chips allow, as phase 5's ends join the
  nearest Trail, and the sheet says how far.
- **The Route sheet in Cycling** shows distance, ascent and descent, the profile, and the warning; from G
  the riding time under them, "≈" and rounded to 5 minutes, never under 5. Hiking's sheet is unchanged.
- **The warning**: "Includes a demanding stretch" with the hardest grade from S2 up, read per edge in the
  direction it is ridden: `mtb:scale` level or downhill, `mtb:scale:uphill` uphill where it has one, else
  `mtb:scale`. An ungraded stretch adds nothing, as 7a's sheet says nothing.

Copy: "%1$s from the nearest Bike route", "No way over the Bike routes between %1$s and %2$s",
"Riding time", "≈ %1$s"; the warning's string is phase 5's, its argument "S%1$d" or "Uphill S%1$d".

## 4. A saved ride, and the switch

- **A saved Route stores its Mode and its chip**: two columns on `routes`, `mode` and
  `bikeRouteFilter`, enums by name, defaulting to `HIKING` and `ALL`, so every Route saved before stays a
  walk (decided with the owner on 2026-11-11). `MIGRATION_3_4`, registered, its schema exported, the
  gate's assertion and a migration test that proves the rows and their points survive.
- **Opening a saved Route sets the map's Mode and chip to its own** and plans it under them.
- **A change of Mode or chip with a Route open replans its points** in the new Mode, saved or not. A leg
  that cannot be joined says so as in § 3. On a saved Route it is an unsaved change, and Save stores the
  new Mode and chip.
- **While Following, the switch and the chips are locked**: changing the line under someone on the way
  helps nobody.

## 5. Following, and a Route along a Bike route

- **Following in Cycling is 6b's as it is**: Progress, the line ahead, the distance left, and, from G,
  the time to go from the riding seconds alone. The leaving rule is 6b's 40 m for 30 s; "Back to the
  Route" plans over the Bike routes the chips allow, and off them says "Back to the Route from the
  nearest Bike route, %1$s away".
- **"Plan a Route along it"** on a Bike route's sheet is 6c § 5's, over the Bike route's walk, under the
  current chip, which allows it since the route was selectable. A one-way Bike route is planned its way
  only; Reverse then plans whatever the router finds the other way, as on a Trail with a one-way
  stretch.
- **"Export GPX"** on a Bike route's sheet is 6c § 5's, named after its title, with the OSM
  `<copyright>` and `<link>`.

---

## Tests

- **`test_build_network.py`**: the class of each `surface` in the table and of each fallback, `surface`
  winning over `highway`; riding seconds on a flat, a climb, a descent reaching its cap, an `mtb:scale`
  cap in each direction and the 4 km/h floor, forward and backward swapping climb and descent; the split
  kinds on a way shared by a Touring and an MTB route; no riding seconds on a Trail-only edge; format 3
  written and read back. Fails toward a wrong time or a Route over the wrong kind.
- **`TrailNetworkTest`**: format 3 read, and format 2 refused by name.
- **The router**: in Cycling it keeps off Trail-only edges, off the other kind under a chip, and off a
  one-way edge backwards; in Hiking nothing changes. Fails toward a Route a rider may not take.
- **The sheet's figures**: the time rounded to 5 minutes and never under 5; the warning from S2 only,
  uphill read from `mtb:scale:uphill` where it is tagged.
- **`MIGRATION_3_4`** and **`SchemaGateTest`**, under the house rule: a v3 database with Routes and
  points opens at v4 with both intact and every Route `HIKING` and `ALL`.
- **The map's ViewModel**: a change of Mode or chip replans an open Route and marks a saved one changed;
  both are refused while Following.
- **`riding-time.py`**: its error measure and the split are deterministic from the seed.

## Documents this phase amends

- **ADR-0011**: the flat speed is the way's surface class; a riding time ships only after § 1's gate;
  saving a ride is a schema change in 7b, before 7c's.
- **`PLAN.md`**: 7b's paragraph names this file, and the 14 routes with 7 in reserve.
- **`CONTEXT.md`**: nothing; see the header.
- **The privacy policy and the listing**: nothing. The published times are read on the owner's PC, and
  nothing in this phase reaches a release user's screen.

## Known gaps, deliberately

- **A ride saved in a developer build, restored into a release build, opens as a walk**: release builds
  stay in Hiking until 7c. Its Mode is kept in the row, so it is a ride again once the switch ships.
- **The leaving rule was read on foot.** 40 m for 30 s covers several hundred metres at riding speed;
  7d, which follows a ride for real, reads it on a ride and changes it only by that reading.
- **Surface is OSM's.** A way with no `surface` is classed by its `highway`, so an asphalt `track`
  tagged with nothing else is timed as unpaved.
- **The reference is two planners' midpoint, not a rider.** 7c's Pace per Bike moves the time toward each
  rider; until then the time is a first guess, and says so with its "≈".

## Readings

`PLAN.md` rule 3: filled in as the phase runs, from the device or the PC, with the command that read it.
The device readings run on an emulator where it can show them, a debug build with the network
hand-pushed to `files/network/`.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The references: each route's id, kind, country, stratum and set, and per direction mapy.cz's and Komoot's time and distance, the guide's where one exists, and the midpoint; the seed | Playwright in the owner's Chrome, said in chat first; `scripts/riding-time.py --split <seed>` | |
| R2 | The base files' growth at B's pin, against 7a's | `ls -l` on the three base files at both pins | |
| R3 | The gate: the constants fitted, the judge set's median and worst, each route's error; on a failure, the same for the reserve | `scripts/riding-time.py --fit tune --judge judge` | |
| R4 | Twister: a Route from its top to its foot under MTB takes it, the reverse does not | `RouteReceiver` both ways in Cycling; the logged edges | |
| R5 | The chips: the same two points under All, Touring and MTB give the lines each kind allows, or "No way" | `RouteReceiver` with each chip | |
| R6 | An upgrade: a v3 install with saved Routes updated to this build keeps every Route, each opening as a walk | emulator, the last release installed, Routes saved, this build `install -r` over it | |
| R7 | The switch: a saved Route replans in Cycling and shows unsaved changes; the switch and chips refuse while Following | emulator, screencaps | |
| R8 | A Route along a Bike route matches it: Twister one way, and a two-way Touring route each way, length to the metre and climb within 6c's dead band | `RouteReceiver --el bikeRoute <id>` | |
| R9 | Following a ride: Progress and the distance left along a scripted ride, and from G the time to go | emulator, `emu geo fix` along the line | |
