# Phase 7b — A ride is planned along the Bike routes, one-way lines their way

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
relies on [ADR-0011](adr/0011-cycling-follows-marked-bike-routes-and-its-times-are-our-own-estimate.md)
(Bike routes only, distance and ascent first, the time our own estimate) and amends it four times: the
flat speed is the way's surface class rather than the Bike route's kind, a riding time ships only once it
has passed § 1's gate, a saved Route keeps its Mode, a schema change here rather than in 7c, and
Following in Cycling is on screen from here, its screen kept on and its voice still 7d's. It
relies on [ADR-0010](adr/0010-routes-are-planned-over-our-own-trail-network.md) (the router, whose
second set of edges phase 7a built), ADR-0001 (a saved Route gains two columns, the first migration
since phase 6) and ADR-0008 (the published times are read on the owner's PC, never by the app). [`CONTEXT.md`](../CONTEXT.md) is owed nothing: **Route**, **Following** and **Pace** already say what
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

**`PLAN.md` moves three things.**

- **The routes.** 7b's paragraph says the rule is "checked against published riding times for 10–15 of
  our Bike routes". § 1 picks 21, tunes on 7 and judges on 7 others, and reads the last 7 only for a
  second attempt, so 14 are read and up to 21.
- **Following in Cycling**, on screen, comes forward from 7d: it is 6b's Following over a ride's line,
  and needs nothing 7d adds. 7d keeps what a handlebar needs, the screen kept on and the voice, and its paragraph says so.
- **A saved ride's Mode** arrives here, ahead of 7c's Rides: a ride that can be planned can be saved,
  and without its Mode it would open as a walk (§ 4).

---

## 0. What this phase inherits

- **Phase 7a built the Bike routes as data**: their edges in `trails.network` (format 2, each edge's
  sets saying Trail, Bike route or both), membership in `trails.edges`, the way's own direction for a
  rider in each edge's `ride` byte (`oneway:bicycle`, else `oneway`), and `mtb:scale` and
  `mtb:scale:uphill` in `edgeMtb` and `edgeMtbUphill`. Since 7a's B the router keeps a Route in Hiking
  off an edge without the Trail bit. What it does not store is **which kind** of Bike route an edge is
  on, so the chips cannot limit a Route yet, **anything about the surface**, and **the direction a
  Bike route gives a way**: each relation's sense, from its members' roles and `signed_direction`,
  reaches the build's edges (`Edge.bikes`, which `walks_like` cuts on) and the sheet's "Ridden one way",
  but not `trails.network`. Some of the 237 Bike routes the sheet calls Ridden one way have no edge the
  `ride` byte directs at all, Asfaltový traverz (r2193737) and Żółta Bielawa (r12909385) among them,
  and more have over a quarter of their walk undirected by it.
- **Times are computed at build time.** `build-network.py`'s `walking_seconds()` writes each edge's
  seconds forward and backward from its length and its climb point by point, and the app only adds
  them, except for a part of an edge, which `EdgePart.kt` times by a mirror of the same rule. The riding
  time follows the same path, so the app gains no rule beyond that mirror's riding half (§ 3).
- **A saved Route is its points.** `RouteEntity` holds a name and dates, `route_points` the points in
  order, and phase 5 § 5 plans it again on every open. Without a stored Mode a saved ride comes back as a
  walk. `APP_SCHEMA_VERSION` is 3.
- **6c's "Plan a Route along it" and "Export GPX"** work on any walk of edges; 7a left them off the Bike
  route's sheet for this phase (7a § *Not in*).
- **6b's Following** is on screen only, timed by the walking Pace, with 6b's leaving rule as its gate
  settles it: 40 m for 30 s until then, and still to be read on foot (6b § 1, § 4).
- **The switch and the chips are developer-only until 7c** (7a), so everything here that a user sees is
  developer-only too. The migration and the network's growth reach release builds.

## What is in, and what is deliberately not

**In:** each edge's surface class, Bike route kinds, rider direction per kind and riding seconds each
way, in `trails.network` format 3; the published riding times for up to 21 Bike routes, and the rule
tuned and judged against them; the router in Cycling, along Bike routes only and only the chips' kind, one-way lines
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
| **A** | The references: 21 Bike routes picked and split, the 14 in tune and judge read in each direction they may be ridden, the reserve only after a failure | `docs:` R1's rows | — |
| **B** | The data: surface class, Bike route kinds, a rider direction per kind and riding seconds in `trails.network` format 3, at a fresh pin; the base files' growth read on its branch | `feat:` (the map data refreshes) | — |
| **C** | **The gate**: the rule tuned on 7, judged on 7 | `chore:` the script, its verdict in R3 | A, B |
| **D** | The router and the Route sheet in Cycling, with no time | `feat:` | B |
| **E** | A saved Route's Mode and chip, the migration, and the replan on a switch | `feat:` | D |
| **F** | Following in Cycling, and a Route along a Bike route with its GPX | `feat:` | D |
| **G** | The riding time on the Route sheet and in Following | `feat:` | C passed, D, F |

**The gate is not first**, against the template, because it can veto only G. Planning, saving and
following stand without a time (§ 1's third verdict), so D, E and F do not wait for it; C needs B's surface class to
be tuned at all. **A needs no code** and starts at once: the references are read before B exists, so
nothing in the build can lean on them. **G does not merge if C fails twice.**

---

## 1. The gate: does our riding time land within 20 % of what riders are told elsewhere?

### The question, in the form that can be measured

For 7 Bike routes the rule was not tuned on, each in every direction it may be ridden, one for a one-way
route and two for the rest, is the median of |estimate − reference| / reference over those directions at
most 20 %, and is none above 40 %? The reference for a route and a direction is the midpoint
of mapy.cz's time and Komoot's at its default fitness for the same line (decided with the owner on
2026-11-11). Official guides' times are recorded beside them where they exist and counted nowhere: they
are scattered, mostly one direction, and written for different riders.

### The apparatus

- **The routes**, picked in A before any reference is read: 21 Bike routes, 9 MTB and 12 Touring, in
  seven strata of three alike, one kind each, flat or hilly, paved or rough, the strata together
  spanning Poland, Czechia and Slovakia. Twister (r5738733) and one other one-way trail-centre line
  share an MTB stratum, so they never land in one set. A seeded shuffle, its seed written in R1, deals
  each stratum's three one to **tune**, one to **judge** and one to **reserve**, so each set holds 7
  routes, 3 MTB and 4 Touring, and the same mix of ground. A one-way route counts one direction, so a
  set holds 14 directions less one for each one-way route in it.
- **The readings**: for each route and direction, the line drawn along its walk in mapy.cz's planner
  (cycling) and Komoot's (touring bike for Touring, mountain bike for MTB, default fitness), its time
  written down with the distance each gives, to catch a line that strayed. Both are read in the owner's
  Chrome through Playwright, said in chat before each session. **The reserve is read only after a first
  failure**, so it can influence nothing before it.
- **`scripts/riding-time.py`**, on the PC: it imports `build-network.py`, builds the edges at B's pin
  with its own `edges_of()`, and for each route takes the edges along its walk (7a § 2's walk) and
  times them by B's riding-seconds function with candidate constants, so the rule it judges is the one
  that ships, on the same densified points and heights (§ 2). It prints the estimate against the
  reference. `--fit tune` fits the constants by least squares on log error, each bounded within the
  published routers' spread (`cycling-research.md` § 2); `--judge judge` prints the median and the
  worst. The constants it settles go into `build-network.py`, and the network is
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

  On a Bike route a way's class is part of what makes two pieces one edge (`walks_like`), so a Bike
  route's edge has exactly one. A Trail-only edge is never cut by it and stores its longest piece's class,
  which nothing reads (decided with the owner on 2026-11-11: the Hiking network keeps 7a's edges).
- **The Bike route kinds per edge**: the sets byte splits 7a's Bike route bit into Touring and MTB, so an
  edge on both kinds carries both, and the router can keep to the chips.
- **A rider direction per kind**, Touring and MTB, a byte each beside `ride`, folded from every place
  ADR-0011 reads direction: the way's `oneway:bicycle`, else `oneway`, where it sets one (the `ride`
  byte), else the senses of that kind's Bike routes on the edge, from their members' roles,
  `signed_direction` and the relation's own `oneway` (`Edge.bikes`): one way where every one of them rides it in the same sense, both
  ways where any of them rides it both ways or two disagree. `walks_like` already cuts an edge where a
  route's sense changes, so each edge has one direction per kind. Under All a direction either kind on
  the edge allows is open.
- **A Bike route relation's own `oneway`** is read as its sense, beside its members' roles and
  `signed_direction` (decided with the owner on 2026-11-11): `yes` rides it in its members' order, as
  `signed_direction=yes` does, `-1` the reverse, and `no` says nothing. It reaches `Edge.bikes`, the rider
  direction per kind and the sheet's "Ridden one way". 21 Bike routes carry it, and 3, Singletrack 14
  (r10113028) among them, were ridden both ways without it. Where sources disagree, 7a's order holds: the
  way's own tag, then the member's role, then the relation, whose `signed_direction=yes` wins over its
  `oneway`.
- **Riding seconds forward and backward**, computed as walking seconds are, from the same densified
  points and heights `edges_of()` measures the climb on, by one function that takes them and the
  constants (C imports it). A stretch is one of `climb()`'s runs: from the point the height was last
  counted at to the next point 5 m or more above or below it, the last run ending at the edge's end
  whatever its height differs by there, as `climb()` counts that rest too. Each run is timed by its
  length and its change, so the metres a ride's time climbs are exactly the edge's ascent, a wobble
  under 5 m inside a run adds nothing, and a run's grade is its change over its length. Each stretch at
  its class's flat speed, slowed uphill by a
  time per metre climbed, faster downhill by a factor up to a descent cap per class, and capped below
  by `mtb:scale` in the direction ridden (`mtb:scale:uphill` uphill where it is tagged). No stretch
  takes longer than walking it: a rider who cannot ride it pushes, timed by `walking_seconds()`'s rule,
  the PTTK marker's, times a pushing factor, so a 15 % push is 2 km/h as on foot rather than a fixed
  speed that overrides the climb term on every steep stretch. An edge on no Bike route gets none. The
  starting constants, from `cycling-research.md` § 2, every one C's to tune:

  | | paved | unpaved | rough |
  | --- | --- | --- | --- |
  | Flat | 20 km/h | 15 km/h | 10 km/h |
  | Descent cap | 40 km/h | 25 km/h | 15 km/h |

  Climbing 10 s per metre up; descents ×1.5 the flat before the cap; pushing ×1 the walking time.
  `mtb:scale` caps at S2 10 km/h, S3 6 km/h, S4 and over 4 km/h: the 4 km/h is GraphHopper's, which it
  applies from S3 on; S2's 10 and S3's 6 are our own steps down to it, which GraphHopper does not
  take, and C keeps them only if the routes bear them out.
- **`trails.network` goes to format 3** with the five arrays (the surface class, the two rider
  directions, the riding seconds each way) and the split bit. The base files' growth, against 7a's
  (`trails.network` 15,198,240 B, `trails.edges` 275,796 B), is R2, recorded, not judged: the owner
  decided on 2026-11-01 that size blocks nothing.

## 3. Planning in Cycling

- **The router plans over the edges the chips allow**: in Cycling under All any Bike route edge, under
  Touring or MTB only that kind's, and never an edge against that kind's rider direction (§ 2), so a
  line directed only by its relation's roles or `signed_direction` is planned its way as Twister is. In
  Hiking it is as it was. The long press, the points, Reverse and the sheet are phase 5's; Reverse on a
  Route with a one-way stretch plans whatever the router finds the other way.
- **In Cycling it minimises B's riding seconds**, whether or not § 1's gate passed: a failed gate hides
  the time, never the cost, so a ride takes a rider's fastest line rather than a walker's. A* keeps phase
  5's straight line to the end as its heuristic, at the fastest speed the rule gives anywhere, the
  highest descent cap (40 km/h at B's constants), so it never overestimates and the leg found is the
  fastest. At phase 5's 4 km/h it would overestimate a ride about fivefold, miss the fastest leg, and
  misplace 6c § 5's via points with it. The constants are B's until G and the tuned ones from G; if G
  never merges, B's stay the cost and are shown nowhere. In Hiking the cost and the heuristic are
  phase 5's. A ride's riding seconds average far below 40 km/h, so the bound is loose and A* may visit
  many more nodes than in Hiking: R5 and R8 time it against phase 5's and 6c's marks, and decide
  whether it needs a tighter one.
- **A part of an edge**, where a point joins it between its ends, is timed as Hiking times one
  (`EdgePart.kt`): the riding rule run over the heights it rides, by its own runs, with the edge's
  class and grades, so its time follows the point along the edge rather than taking a share of the
  edge's. The app so keeps the riding constants beside the walking ones it already mirrors,
  `build-network.py`'s the source, and a test holds the two together (Tests).
- **A point off the Bike routes** joins as phase 5's ends join the Trails: every edge the chips allow
  within 1.5 km is a candidate, and the stretch from the point to it is timed at the rough class's flat
  speed (10 km/h at B's constants, the slowest a rider is assumed to go where nothing is known of the
  way), counted ten times while the join is chosen and once in the leg's time, so a farther Bike route
  wins only for a much shorter Route. It is drawn dashed, and the sheet shows its length over 50 m.
- **The Route sheet in Cycling** shows distance, ascent and descent, the profile, and the warning; from G
  the riding time under them, "≈" and rounded to 5 minutes, never under 5, and each point's time,
  summed in riding seconds unrounded and rounded as the total. Without G no point shows a time. None of
  the Pace lines shows in Cycling (`route_time_signposts`, `pace_yours`, `pace_floored`, `pace_none`):
  they speak of signposts and hikes, and the walking Pace never touches a ride (*Not in*). Hiking's
  sheet is unchanged.
- **The warning**: "Includes a demanding stretch" with the hardest grade from S2 up, read per edge in the
  direction it is ridden: `mtb:scale` level or downhill, `mtb:scale:uphill` uphill where it has one, else
  `mtb:scale`. An ungraded stretch adds nothing, as 7a's sheet says nothing.

Copy: "%1$s from the nearest Bike route", "No way over the Bike routes between %1$s and %2$s",
"Riding time", "≈ %1$s", and the warning's own string, "Includes a demanding stretch: %1$s", whose
argument is 7a's `mtb_grade` or `mtb_grade_uphill` ("S3", "Uphill S2") and whose comment says the grade
is the MTB scale's from S2 up. Phase 5's `route_demanding` stays Hiking's: its argument names a stretch
by its ends and its comment calls the grade a hiking one. Every other string the Cycling sheet shows
names no Trail, hike or signpost and is Hiking's as it is.

## 4. A saved ride, and the switch

- **A saved Route stores its Mode and its chip**: two columns on `routes`, `mode` and
  `bikeRouteFilter`, enums by name, defaulting to `HIKING` and `ALL`, so every Route saved before stays a
  walk (decided with the owner on 2026-11-11). `MIGRATION_3_4`, registered, its schema exported, the
  gate's assertion and a migration test that proves the rows and their points survive.
- **Opening a saved Route sets the map's Mode and chip to its own** and plans it under them, in one step
  with the Route itself: they are handed to the router with its points rather than read back from the
  map, and change together with the new Route, so the replan below never runs on the Route being
  replaced. Opening one while Following ends Following first, as it does today.
- **The Routes tab plans a saved Route under its own Mode and chip as well.** Its rows (`RouteTotals`,
  keyed by the points, the Mode and the chip rather than the points alone) and
  `RoutesViewModel.exportGpx` both plan. A ride's row shows its length with no walking time and no
  signposts' Pace, and no time before G; from G it shows the riding time, "≈" and rounded as the sheet
  rounds it. A ride's GPX export plans over the Bike routes its chip allows and, with no way, says § 5's
  Cycling string. Both read the stored Mode through the map's own seam (`modeOf`), so in a release build
  a ride's row and export are a walk's, as it opens (*Known gaps*).
- **Every other caller of the planner passes a Mode and chip too**: `AppContainer.planRoute` takes them
  beside the points, so nothing can plan a ride as a walk by default. The followed Route kept for a
  closed app (`FollowedRoute`, 6b § 3) stores them beside its points, Hiking and All when absent so one
  stored before this phase decodes as a walk, and resuming sets the map's Mode and chip to them before
  it plans, as opening a saved Route does: the map keeps its Mode across a closed app but not its chip,
  which would come back All and replan an MTB ride over Touring lines. "Back to the Route" plans under
  the followed Route's (§ 5). The debug seeder's Hike along a Route stays a walk, and `RouteReceiver`
  takes them from `--es mode` and `--es filter` (Readings).
- **A change of Mode or chip with a Route open replans its points** in the new Mode, saved or not. A leg
  that cannot be joined says so as in § 3. On a saved Route it is an unsaved change, and Save stores the
  new Mode and chip.
- **While Following, the switch and the chips are locked**: changing the line under someone on the way
  helps nobody.

## 5. Following, and a Route along a Bike route

- **Following in Cycling is 6b's**: Progress, the line ahead, the distance left, and, from G, the time
  to go from the riding seconds alone, spread over the line by the riding rule as 6b spreads the
  walking one. The leaving rule is 6b's, as its gate settles it (40 m for 30 s until then); "Back to the
  Route" plans over the Bike routes the chips allow by § 3's router.
- **Progress's forward window reaches as far as a ride does**: 6b's window is `max(200 m, 6 km/h × the
  time since the last match)`, so while counted fixes arrive every few seconds a rider never outruns it.
  It stalls only after a gap in matched fixes, under trees or with the phone in a pocket: at 20 km/h a
  gap of more than about 36 s carries the rider past 200 m while the window is still 200 m, and the
  next fix lies beyond it. In Cycling the speed is the rule's fastest, the highest descent cap (40 km/h
  at B's constants), never less than 200 m, so after any gap the window reaches past where a ride can
  be; in Hiking it stays 6 km/h.
- **"Plan a Route along it"** on a Bike route's sheet is 6c § 5's, over the Bike route's walk, planned
  under the route's own kind: where the current chip excludes it, the chip is set to that kind first,
  and All stays All. Search in Cycling finds both kinds whatever the chip and a chip change leaves a
  selection open (7a § 4), so the chip alone would plan an MTB route opened under Touring as "No way"
  or a detour. The via points are found by § 3's router under that chip, and the Route saves with it
  (§ 4). A one-way Bike route is planned its way only; Reverse then plans whatever the router finds
  the other way, as on a Trail with a one-way stretch. **It is not offered while Following**, since the
  chips are locked then (§ 4); a Trail's sheet keeps 6c's, where the new Route ends Following, as it
  changes no chip. The chip changes together with the new Route in one step, as on opening a saved
  Route (§ 4): the open Route is set aside first and the new one planned under the new chip, so § 4's
  replan never runs on the Route being replaced, and a saved one is never marked changed just before it
  goes.
- **"Export GPX"** on a Bike route's sheet is 6c § 5's, named after its title, with the OSM
  `<copyright>` and `<link>`. It writes the walk and plans nothing, so it has no "no way"; a saved
  ride's export from the Routes tab, which plans, has its own (§ 4).

Copy: "Back to the Route from the nearest Bike route, %1$s away", "No way back to the Route over the
Bike routes", and "This route has no way over the Bike routes right now, so there is no GPX to export.".
Following's lines without a time, used until G and for good if G never merges: "Next: %1$s · %2$s · ↑
%3$s", "To the end: %1$s · ↑ %2$s" and "Back to the Route: %1$s"; from G, 6b's own with the riding
time. `following_back`, "Back to the Route", holds as it is, its comment gaining the Bike routes beside
the Trails.

---

## Tests

- **`test_build_network.py`**: the class of each `surface` in the table and of each fallback, `surface`
  winning over `highway`; riding seconds on a flat, a climb, a
  descent reaching its cap, an `mtb:scale` cap in each direction and a steep push timed as walking it,
  forward and backward swapping climb and descent; a wobble under 5 m inside a run adding no time, a
  last run inside the band timed by the change `climb()` counts for it, and the metres the time climbs
  equal to the edge's ascent; the split kinds on a way shared by a Touring and an
  MTB route; the rider direction per kind from a way's tag, from a `forward` role alone, from
  `signed_direction` alone, both ways where two routes of a kind disagree, and each kind its own on a
  way shared by both; a route one way from the relation's own `oneway` alone, and `oneway=no` leaving it
  two-way (decided with the owner on 2026-11-11, with `test_kozica_profile.py` reading the sense `yes` and
  `-1` give); no riding seconds on a Trail-only edge; format 3 written and read back. Fails
  toward a wrong time, a Route over the wrong kind or one against the ride.
- **`TrailNetworkTest`**: format 3 read, and format 2 refused by name.
- **The router**: in Cycling it keeps off Trail-only edges, off the other kind under a chip, and off a
  one-way edge backwards, whether the way's tag directs it or only its Bike route's role or
  `signed_direction`; under All an edge one kind directs and the other does not is open both ways; in
  Hiking nothing changes. In Cycling it takes a longer paved line over a shorter rough one where riding
  seconds say so, where Hiking takes the shorter; its heuristic never exceeds a leg's riding seconds; a
  point off the Bike routes joins at the rough flat speed, weighted ten times while choosing and once in
  the time. Fails toward a Route a rider may not take, or a slower one than the network holds.
- **`EdgePart`'s riding half**: run over a whole edge's points, it gives the riding seconds the build
  stored for that edge, so the app's mirror cannot drift from `build-network.py`'s constants.
- **The sheet's figures**: the time rounded to 5 minutes and never under 5, and each point's time
  rounded as it is, none without G; no Pace line in Cycling; the warning from S2 only, uphill read from
  `mtb:scale:uphill` where it is tagged, in its own string.
- **Following in Cycling**: after 60 s with no fix, a fix 340 m on along the line, a ride at 20 km/h,
  moves Progress, where in Hiking, whose window is then still 200 m, it does not; with fixes every few
  seconds both keep up; without G, the lines carry no time.
- **"Plan a Route along it"** on an MTB route under Touring sets the chip to MTB, and under All leaves
  All. In the map's ViewModel, with a saved Route open under Touring, it plans only the new Route, once,
  under MTB, and leaves the saved one unmarked; while Following it is not offered and does nothing.
- **The Routes tab's ViewModel**: a saved ride's row is planned under its Mode and chip and shows its
  length with no walking time and no Pace, and no time without G; the same points saved as a walk and
  as a ride are planned apart; a walk's row is as it was. Its GPX export plans a ride under its chip
  and, with no way over the Bike routes, fails with the Cycling string.
- **`FollowedRoute`**: one stored before this phase decodes as Hiking and All; resuming a followed ride
  sets the Mode and chip before it plans.
- **`MIGRATION_3_4`** and **`SchemaGateTest`**, under the house rule: a v3 database with Routes and
  points opens at v4 with both intact and every Route `HIKING` and `ALL`.
- **The map's ViewModel**: a change of Mode or chip replans an open Route and marks a saved one changed;
  both are refused while Following. Opening a saved ride with a walk open plans only the ride, under its
  own Mode and chip, and marks nothing changed.
- **`riding-time.py`**: its error measure and the split are deterministic from the seed.

## Documents this phase amends

- **ADR-0011**: the flat speed is the way's surface class; a riding time ships only after § 1's gate;
  saving a ride is a schema change in 7b, before 7c's; Following in Cycling is on screen from 7b, its
  screen kept on and voice from 7d.
- **`PLAN.md`**: 7b's paragraph names this file, the 14 routes read with 7 in reserve, Following on
  screen and a saved ride's Mode; 7d's says on-screen Following arrives in 7b and 7d adds the screen
  kept on and the voice.
- **`CONTEXT.md`**: nothing; see the header.
- **The privacy policy and the listing**: nothing. The published times are read on the owner's PC, and
  nothing in this phase reaches a release user's screen.

## Known gaps, deliberately

- **A ride saved in a developer build, restored into a release build, opens as a walk**, and its row and
  GPX export in the Routes tab are a walk's: release builds stay in Hiking until 7c. Its Mode is kept in the row, so it is a ride again once the switch ships,
  unless it is saved there first: Save in a release build stores the Mode it is open in, Hiking, and it
  stays a walk.
- **The leaving rule is read on foot, if at all.** 6b's gate settles it on walks; if it keeps 40 m for
  30 s, that covers several hundred metres at riding speed. 7d, which follows a ride for real, reads it
  on a ride and changes it only by that reading.
- **Surface is OSM's.** A way with no `surface` is classed by its `highway`, so an asphalt `track`
  tagged with nothing else is timed as unpaved.
- **The reference is two planners' midpoint, not a rider.** 7c's Pace per Bike moves the time toward each
  rider; until then the time is a first guess, and says so with its "≈".

## Readings

`PLAN.md` rule 3: filled in as the phase runs, from the device or the PC, with the command that read it.
The device readings run on an emulator where it can show them, a debug build with the network
hand-pushed to `files/network/`.

**`RouteReceiver` gains what the readings need**, in `app/src/debug/` with the rest of it: in D,
`--es mode CYCLING` and `--es filter ALL|TOURING|MTB`, planning under that Mode and chip as the map
would, for R4 and R5; in F, `--el bikeRoute <id>`, which plans along that Bike route by "Plan a Route
along it"'s code from the chip `--es filter` gives, as the map's current one, and logs its row and the
chip it planned under beside it and the time taken, as `--el trail` does for a Trail, and the nodes
visited, which `--el trail` does not log, for R8.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The references: each route's id, kind, country, stratum and set, and per direction mapy.cz's and Komoot's time and distance, the guide's where one exists, and the midpoint; the seed | Playwright in the owner's Chrome, said in chat first; `scripts/riding-time.py --split <seed>` | Read 2026-11-11, seed `20261111`: § *R1, the references* below. 14 routes, 25 directions; every reading within 5 % of our walk's length, Singletrack 14 replaced before its first reading counted |
| R2 | The base files' growth at B's pin, against 7a's | `ls -l` on the three base files at both pins | 2026-11-11, the PC, B's branch at pin `poland 4965`, `slovakia 4965`, `czech-republic 4966` (2026-11-10T21:21:39Z), against 7a's at `4956`, `4956`, `4957`: `trails.network` 15,198,240 → 15,782,943 B (+584,703, +3.8 %), `trails.edges` 275,796 → 297,232 B (+21,436, +7.8 %), `places.sqlite` 7,565,312 → 7,581,696 B (+16,384, +0.2 %). Edges 30,843 → 33,993, nodes 23,251 → 26,424, points 1,342,356 → 1,344,511, a walk's steps 53,257 → 58,588. The five new arrays are 11 B an edge, 373,923 B; the rest is the surface class cutting the Bike routes' edges, 3,176 more than a build with the class ignored at the same pin (30,817 edges, 15,525,687 B), and the pin. **The Hiking network keeps 7a's edges**: 14,536 Trail-only edges at 7a's pin, 14,560 at B's, and those 14,560 are the same edges, points, heights, times and climbs as in the build with the class ignored. Classes: 17,798 edges paved, 9,809 unpaved, 6,386 rough, the Trail-only ones by their longest piece; 16,033 edges on a Touring route, 3,860 on an MTB route, 460 on both. Bike routes ridden one way: 237 without the relation's own `oneway`, 240 with it (Świeradowiec r8781520, Singletrack 14 r10113028, Chabówka - Rabka r14390917). Recorded, not judged |
| R3 | The gate: the constants fitted, the judge set's median and worst, each route's error; on a failure, the same for the reserve | `scripts/riding-time.py --fit tune --judge judge` | |
| R4 | Direction: Twister, directed by its ways' tags, and Asfaltový traverz (r2193737), directed by its relation alone: under MTB a Route from each one's start to its end takes it, the reverse does not | `RouteReceiver --es mode CYCLING --es filter MTB` both ways along each; the logged edges | |
| R5 | The chips: the same two points under All, Touring and MTB give the lines each kind allows, or "No way". The planning time: a ride of 20 km or more between two points, its plan time and nodes visited logged beside Hiking's for the same two points. Pass: within phase 5's 500 ms. A miss holds D until the router is fixed, a tighter heuristic first (the flat paved speed with an admissible correction for the descent left to the end), and is not shipped | `RouteReceiver --es mode CYCLING` with each `--es filter`; `adb logcat -d -s KozicaRoute` for the plan time and nodes visited | |
| R6 | An upgrade: a v3 install with saved Routes updated to this build keeps every Route, each opening as a walk | emulator, the last release installed, Routes saved, this build `install -r` over it | |
| R7 | The switch: a saved Route replans in Cycling and shows unsaved changes; the switch and chips refuse while Following, and a Bike route's sheet offers no "Plan a Route along it". A saved ride's row in the Routes tab shows its length and no walking time, and its GPX export is the ride | emulator, screencaps; the export's file pulled | |
| R8 | A Route along a Bike route matches it: Twister one way, and the longest Touring route in the Regions each way, length to the metre and climb within 6c's dead band; Twister planned with the chip on Touring comes back under MTB, with the chip logged. The longest one's time taken and nodes visited logged as 6c's R4 logged them. Pass: made within 6c's 2 s, whose 2.4 s for Główny Szlak Beskidzki the owner accepted; a miss is fixed in the router as R5's is, before F merges, and not shipped | `RouteReceiver --el bikeRoute <id>`, the last with `--es filter TOURING`; `adb logcat -d -s KozicaRoute` | |
| R9 | Following a ride: Progress and the distance left along a scripted ride at 20 km/h, Progress keeping up, across a 60 s gap with no fix that would stall Hiking's window, and from G the time to go | emulator; a ride planned by `RouteReceiver --es mode CYCLING` and followed on the map; `scripts/replay-track.py --route --kmh 20 --last <n> -s <serial>`, then `--first <n+34> --t0 <its t0>`: the 33 points skipped, 10 m and 1.8 s apart, put the next fix 340 m and 61 s on, where Hiking's 200 m window stops short; screencaps either side of the gap | |

### R1, the references

**The pick and the split.** Seven strata of three alike, chosen from 7a's `places.sqlite` by kind,
climb per kilometre, the surface mix of their member ways and country, each route with no gap in its
walk. The seed is `20261111`, the date it was taken. The deal: one `random.Random(20261111)`, the
strata in the order below, each stratum's three ids sorted ascending, shuffled, and dealt to tune,
judge and reserve in that order. `riding-time.py --split 20261111` reproduces it and then applies the
one replacement below. The seed put Twister in the reserve; the deal stands.

**One replacement, before any of its readings counted.** Singletrack 14 (r10113028) was dealt to judge
in M3. Its relation is tagged `oneway=yes` and 18 of its 40 ways are one-way cycleways, so it is
ridden one way. 7a gives a route its sense from each member way's own `oneway:bicycle` or `oneway`,
then roles and `signed_direction`, never from the relation's own `oneway`; Singletrack 14's 18
directed ways fall under the 0.75 share (`ONE_WAY_SHARE` in `scripts/build-network.py`), so 7a lists
it as ridden both ways. Komoot could not draw our walk in either direction, straying 13 % and 68 %
with 12 waypoints and 190 % on a half; mapy.com was not tried. The rule was fixed before any
candidate was looked at. The two-way MTB route closest to it in climb per kilometre and unpaved share, with no relation
`oneway` and no one-way member, takes its judge slot. That is Rowerowa 500 – G6 (r14354061), and the
deal is otherwise unchanged. Twenty more Bike routes carry a relation-level `oneway`; 7a lists two of
them, beside Singletrack 14, as ridden both ways.

**How each direction was read.** The walk is 7a's, from `trails.edges` at 7a's pin, as a line in the
walk's direction ("walk's") or turned ("reverse"). A one-way route counts its walk's direction only,
the direction it is ridden. Each planner was given up to 12 points sampled along that line, every
800 m or every eleventh of it, whichever is longer. Komoot's planner refuses more than 13. Times and
lengths are the planners' own seconds and metres, taken from their responses:

- **Komoot** was read logged out, at its default fitness (`constitution` 3, "Moderate"), as
  `mtb` for MTB and `touringbicycle` for Touring.
- **mapy.com** was read at its default pace, as *Mountain bike* (`c` 123) for MTB and
  *Bicycle touring* (`c` 121, 18 km/h on the flat) for Touring.

A direction whose planned length strayed more than 5 % from our walk's was read again as two halves
of the walk, each with its own 12 points, and the halves summed. That was Okolo Oravskej Jasenice on
Komoot (+27.5 % and +6.3 % whole) and G6's walk's direction on mapy.com (−8.4 % whole). Every
reading in the table is within 4.3 % of our length. Guides' times were not looked up; they count
nowhere. The reserve is not read until a first failure.

**What the readings already say.** On paved Touring routes the two planners agree within 10 %, and
mapy.com is the slower in six of the eight directions. Everywhere else Komoot is the slower: by
7–34 % on MTB and by 9–43 % on unpaved or mixed Touring, the most on the Czech gravel of T2. The
midpoint halves that spread, and it is what the gate is judged against.

| Stratum | Set | Bike route | Country | Ours | Direction | mapy.com | Komoot | Midpoint |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M1 MTB, one-way trail-centre descent, unpaved | tune | TT Bunkr Trail (r5487422) | CZ | 5.45 km, ↑58 ↓354 m, one way | walk's | 22.2 min, 5.39 km | 23.6 min, 5.43 km | **22.9 min** |
| M1 MTB, one-way trail-centre descent, unpaved | judge | Griftof trail (r16129431) | SK | 5.34 km, ↑50 ↓361 m, one way | walk's | 20.1 min, 5.34 km | 22.9 min, 5.34 km | **21.5 min** |
| M1 MTB, one-way trail-centre descent, unpaved | reserve | Twister (r5738733) | PL | 4.14 km, ↑0 ↓267 m, one way | — | not read | not read | — |
| M2 MTB, hilly, unpaved | tune | Iľovnícky hrebeň (r20344673) | SK | 9.38 km, ↑297 ↓496 m | walk's | 43.6 min, 9.38 km | 52.3 min, 9.40 km | **48.0 min** |
| M2 MTB, hilly, unpaved | tune | Iľovnícky hrebeň (r20344673) | SK | 9.38 km, ↑297 ↓496 m | reverse | 56.0 min, 9.38 km | 72.5 min, 9.40 km | **64.2 min** |
| M2 MTB, hilly, unpaved | judge | Czerwony rowerowy na Turbacz (r2828552) | PL | 23.42 km, ↑748 ↓626 m | walk's | 116.0 min, 22.42 km | 155.2 min, 23.36 km | **135.6 min** |
| M2 MTB, hilly, unpaved | judge | Czerwony rowerowy na Turbacz (r2828552) | PL | 23.42 km, ↑748 ↓626 m | reverse | 108.1 min, 22.42 km | 142.8 min, 23.43 km | **125.5 min** |
| M2 MTB, hilly, unpaved | reserve | K Boudě (r19423445) | CZ | 15.16 km, ↑352 ↓369 m, one way | — | not read | not read | — |
| M3 MTB, rolling loop, unpaved | tune | Pętla Kłodzka (r10030377) | PL | 17.67 km, ↑327 ↓329 m, one way | walk's | 73.5 min, 17.44 km | 84.1 min, 17.48 km | **78.8 min** |
| M3 MTB, rolling loop, unpaved | judge | Rowerowa 500 - G6 (r14354061) | PL | 15.51 km, ↑292 ↓292 m | walk's | 66.8 min, 15.34 km (2 halves) | 81.5 min, 15.00 km | **74.1 min** |
| M3 MTB, rolling loop, unpaved | judge | Rowerowa 500 - G6 (r14354061) | PL | 15.51 km, ↑292 ↓292 m | reverse | 64.7 min, 14.93 km | 84.3 min, 15.49 km | **74.5 min** |
| M3 MTB, rolling loop, unpaved | reserve | Trasa Towarzystwa Izerskiego - Konna Ścieżka (r10108765) | PL | 17.69 km, ↑309 ↓309 m | — | not read | not read | — |
| T1 Touring, flat, paved | tune | Rodzinna trasa rowerowa - Chłopy i Pany (r10799101) | PL | 17.73 km, ↑92 ↓83 m | walk's | 63.1 min, 17.65 km | 62.1 min, 17.65 km | **62.6 min** |
| T1 Touring, flat, paved | tune | Rodzinna trasa rowerowa - Chłopy i Pany (r10799101) | PL | 17.73 km, ↑92 ↓83 m | reverse | 62.7 min, 17.72 km | 61.9 min, 17.71 km | **62.3 min** |
| T1 Touring, flat, paved | judge | Kłodzko - Bystrzyca Kłodzka (r8652589) | PL | 19.47 km, ↑113 ↓59 m | walk's | 70.5 min, 19.47 km | 70.9 min, 19.46 km | **70.7 min** |
| T1 Touring, flat, paved | judge | Kłodzko - Bystrzyca Kłodzka (r8652589) | PL | 19.47 km, ↑113 ↓59 m | reverse | 66.5 min, 19.28 km | 64.5 min, 19.42 km | **65.5 min** |
| T1 Touring, flat, paved | reserve | Cyklookruh Rozkoš (r20829119) | CZ | 18.41 km, ↑68 ↓68 m | — | not read | not read | — |
| T2 Touring, rolling, mixed | tune | Szlak niebieski po Paśmie Posłowickim (r7511699) | PL | 16.35 km, ↑164 ↓164 m | walk's | 62.8 min, 16.40 km | 75.7 min, 16.47 km | **69.2 min** |
| T2 Touring, rolling, mixed | tune | Szlak niebieski po Paśmie Posłowickim (r7511699) | PL | 16.35 km, ↑164 ↓164 m | reverse | 63.2 min, 16.54 km | 75.4 min, 16.87 km | **69.3 min** |
| T2 Touring, rolling, mixed | judge | 6284 Ludvíkov Nad Bohušem - Andělská Stezka (r9637589) | CZ | 9.95 km, ↑113 ↓183 m | walk's | 40.0 min, 9.98 km | 55.5 min, 9.96 km | **47.8 min** |
| T2 Touring, rolling, mixed | judge | 6284 Ludvíkov Nad Bohušem - Andělská Stezka (r9637589) | CZ | 9.95 km, ↑113 ↓183 m | reverse | 44.1 min, 9.98 km | 63.0 min, 9.96 km | **53.5 min** |
| T2 Touring, rolling, mixed | reserve | 8858 (no name) (r6517987) | SK | 16.87 km, ↑242 ↓354 m | — | not read | not read | — |
| T3 Touring, hilly, paved | tune | Istebna - Jaworzynka - Koniaków (r2728701) | PL | 27.61 km, ↑742 ↓759 m | walk's | 131.2 min, 27.40 km | 125.6 min, 27.37 km | **128.4 min** |
| T3 Touring, hilly, paved | tune | Istebna - Jaworzynka - Koniaków (r2728701) | PL | 27.61 km, ↑742 ↓759 m | reverse | 132.1 min, 27.40 km | 124.2 min, 26.73 km | **128.1 min** |
| T3 Touring, hilly, paved | judge | Terchovsko-zázrivský okruh (r1278665) | SK | 35.37 km, ↑831 ↓283 m | walk's | 165.2 min, 35.40 km | 169.9 min, 34.69 km | **167.6 min** |
| T3 Touring, hilly, paved | judge | Terchovsko-zázrivský okruh (r1278665) | SK | 35.37 km, ↑831 ↓283 m | reverse | 131.2 min, 35.39 km | 119.4 min, 35.26 km | **125.3 min** |
| T3 Touring, hilly, paved | reserve | 461 Ostravice - Klokočov (CZ/SK) (r174543) | CZ | 24.15 km, ↑551 ↓267 m | — | not read | not read | — |
| T4 Touring, hilly, unpaved | tune | 12 (no name) (r1671065) | CZ | 9.99 km, ↑332 ↓290 m | walk's | 53.2 min, 10.03 km | 57.8 min, 9.89 km | **55.5 min** |
| T4 Touring, hilly, unpaved | tune | 12 (no name) (r1671065) | CZ | 9.99 km, ↑332 ↓290 m | reverse | 50.7 min, 10.03 km | 58.0 min, 9.89 km | **54.4 min** |
| T4 Touring, hilly, unpaved | judge | Okolo Oravskej Jasenice (r11293190) | SK | 20.88 km, ↑668 ↓668 m | walk's | 107.4 min, 20.89 km | 137.2 min, 20.94 km (2 halves) | **122.3 min** |
| T4 Touring, hilly, unpaved | judge | Okolo Oravskej Jasenice (r11293190) | SK | 20.88 km, ↑668 ↓668 m | reverse | 107.4 min, 20.89 km | 137.3 min, 20.94 km (2 halves) | **122.4 min** |
| T4 Touring, hilly, unpaved | reserve | Półrzeczki - Chyszówki (r3934748) | PL | 13.57 km, ↑543 ↓523 m | — | not read | not read | — |
