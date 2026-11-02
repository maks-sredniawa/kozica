# Phase 7a — The map shows the Bike routes, and one switch turns it to Cycling

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
relies on [ADR-0011](adr/0011-cycling-follows-marked-bike-routes-and-its-times-are-our-own-estimate.md)
(one Mode switch, Bike routes only), ADR-0007 (every Region's pack is rebuilt and delivered again),
ADR-0008 (the Bike routes' index ships in the app like the Trails') and ADR-0010 (the network gains
the Bike routes' ways as a second set of edges, with their direction; from B its router keeps a Route in
Hiking off them, and plans along them from 7b). [`CONTEXT.md`](../CONTEXT.md) already holds **Mode**, **Bike route** and **Bike**; it gains
**MTB route**, a Bike route of the MTB kind (*szlak MTB*), confirmed with the owner on 2026-11-02. Planned
with the owner on 2026-11-01, the design advised by a design review of the style and the mark.

**What closing this phase means.** Today the map draws Trails and nothing else that is marked; the
Regions' 1,450 signed cycling routes are dropped by the profile. After this phase a developer build
has a walker | bike switch under the search row: in Cycling the Bike routes are drawn in their
marking colours with R-1 plates along them, the Trails faded beneath; a Bike route tapped is seen
whole with its title, length, ascent, profile and MTB difficulty, and a one-way descent shows only
the way it is ridden; search finds Bike routes and offers the other Mode's matches. A release build
shows Hiking alone, with the Bike routes faded beneath the Trails, until phase 7c opens the switch
to everyone. The difference a device can be asked about: on the test phone, in Cycling, Bielsko's
Twister draws its chevrons downhill and its sheet gives one direction only, and the map at zoom 13
with every Bike route drawn stays within phase 4's frame gate.

**`PLAN.md`'s paragraphs move one thing.** The Bike routes' edges, their membership and their
direction were 7b's; they are built here, because the sheet's length, ascent and profile need them
and a one-way line must never be offered the wrong way on a sheet. 7b keeps the router, the riding
time and planning.

---

## 0. What this phase inherits

- **Phase 6c built everything a Bike route needs once it is data**: a relation id on every Trail
  feature, membership from the profile, edges cut where the routes on a way change, `trails.edges`,
  the walk that draws a whole Trail below zoom 10, the tap with its chooser, the highlight and fade,
  the sheet with its profile, Trails in the name index, Long-distance Trails with Sections. 7a feeds
  Bike routes through the same paths rather than building a second set.
- **`KozicaProfile` keeps hiking relations and drops the rest.** Its `trails()` and `reversed()`
  compute each way's `index`/`count` and its drawn direction over Trails only (phase 2 § 3). A way a
  Bike route shares with a Trail needs one order over both, or a red Bike route lies on a red Trail
  and the X-crossing phase 2 fixed comes back.
- **`build-network.py`'s `oneway()` reads foot tags only.** A walker has no direction; a rider on a
  trail-centre line has one, put in four places by OSM (`cycling-research.md` § 3).
- **The style's fade is one key.** `faded()` in `gen_style.py` asserts no `-opacity` of its own in a
  layer's paint, and `MapStyle.kt` applies `kozica:fade` while something is selected. A second fade
  for the other Mode is a second key.
- **The launcher icon is one in both variants.** `art/mark.py` is the identity, `draw-mark.py`
  rewrites `MARK` and `HOLE` only, and the generators iterate `PARTS`.
- **Developer-only surface lives in `app/src/debug/`**, with its other half in `app/src/release/`
  (`CLAUDE.md`). The switch and the new mark follow that rule until 7c.

## What is in, and what is deliberately not

**In:** `route=bicycle` and `route=mtb` relations in every Region's tiles, in a `bike_routes` layer
counted with the Trails; their edges, membership and direction in the network; Bike routes in the
name index; the style (lines, plates, chevrons, violet for no colour, numbers, zoom thresholds, the
Mode fade); the Mode switch and the All · Touring · MTB chips, developer-only; the tap, highlight
and sheet in Cycling; search in Cycling with the hint row; the mark with wheels in its crag,
developer-only.

**Not in, and each with a reason rather than a phase:**

- **Planning a Route in Cycling, and a riding time.** The router's second set of edges and the
  timing rule are 7b's: the rule is checked against published riding times, which is its own work.
  The sheet in 7a shows no time and no "Plan a Route along it".
- **GPX of a Bike route.** It is the Route along it written out, so it comes with that in 7b.
- **The switch in release builds.** A release user who could switch to Cycling could not plan or
  record there, and a long press would plan a walk. The switch opens in 7c, when both work.
- **The new mark in release builds.** An icon that promises bikes in an app that cannot switch to
  them is the one thing in 7a a release user would see. It ships with the switch.
- **`mtb:scale` on the map.** On 14 % of MTB-route ways it would draw a patchwork that looks like
  data where there is none; the sheet says it with its share (§ 4).

**The tempting one: letting a tap reach the other Mode's routes.** Where a Bike route shares a way
with a Trail, a chooser listing both looks like a free convenience. It is not: the tap would switch
the Mode by a side door, and the chooser 6c kept short doubles. A tap answers the current Mode only;
search's hint row is the one way across (§ 5).

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The title tally, no gate** | `chore:` the tally | — |
| **B** | The data: the `bike_routes` layer counted with the Trails, direction, edges and membership, the index, a fresh pin; the archives' growth read on its branch before it merges | `feat:` (the map data refreshes) | — |
| **C** | The style, the switch and the chips (developer-only) | `feat:` | B |
| **D** | The tap, the highlight and the sheet in Cycling | `feat:` | C |
| **E** | Search in Cycling and the hint row | `feat:` | D |
| **F** | The mark with wheels in its crag (developer-only) | `feat:` | — |

**There is no gate.** Bike routes ship whatever they cost: the owner decided on 2026-11-01 that size
blocks nothing, so R1's and R2's figures are recorded, not judged. **B is one checkpoint** for the
reason 6c's was: the profile, the network and the index move the same pin, and each alone ships
nothing. **F depends on nothing** and can land at any point; it only waits for the owner's approval
of the renders.

---

## 1. Readings

- **The title tally**, before the build (A): `scripts/map/trail-tally.py` gains Bike routes,
  counting how many land at each step of § 4's title rule, per kind and per Region, and the largest
  union `count` on any way (§ 2).
- **The archives' growth**, on B's branch before it merges: each Region's `.pmtiles` and the three
  base files at B's pin, against 0.14.4's (355 MB of Regions, `trails.network` 9.7 MB,
  `trails.edges` 128 KB, `places.sqlite` 7.2 MB). Recorded per Region and in total.

## 2. The data

- **Kept relations**: `route=bicycle` (Touring) and `route=mtb` (MTB), whatever their `type` (R2
  found `site` and `network` among them), with `name`, `name:pl`, `ref`,
  `colour`, `network`, `from`/`to`, `mtb:scale`, `mtb:scale:uphill`, `signed_direction`.
- **`state=proposed` and `state=abandoned` are not kept**, in either kind, decided with the owner on
  2026-11-02: a line that looks marked where there may be no paint misleads someone at a junction.
  That takes Trails out of a release for the first time: at 6c's pin, the 13 Trails of Via Slovakia and
  Mária-út / Mariánska Cesta with Via Slovakia's parent, and Szlak Zabytków Ziemi Pszczyńskiej. A
  Section of a dropped parent stays a route of its own. `state=recommended` is kept.
- **Relations of relations**, decided with the owner on 2026-11-02 (19 Bike routes at the current
  pin hold Bike routes), following 6c's per-member reading:
  - **An `icn` or `ncn` parent has Sections**, whatever their marking, and never takes a piece: each
    member relation without a role, or with `part` or `connection`, is a Bike route of its own, and
    "Show the whole" shows the parent, as a Long-distance Trail's does. A member with a role from the
    profile's `ROLES` is a Bike route of its own outside that whole, as Via Mariae M02 r4649443's
    `alternative`s are Trails of their own in the profile: Green Velo r5141547's `excursion`s r8748314
    and r8748654. A role outside `ROLES` counts as no role, as it does in the profile. The profile's
    rule for a parent whose members all carry a role from `ROLES`, that they are pieces, does not
    carry over: such an `ncn` parent has Bike routes of its own too.
  - **Any other parent is read per member.** A member carrying the parent's marking is a piece: its
    ways write the parent's id, and the parent is one Bike route. A member marked otherwise is a
    Bike route of its own. A parent with no member carrying its marking has Sections, as an `icn`
    one does. That is the trail centres: Singltrek pod Smrkem r611323 (`rcn`, ten loops in four
    colours), Single Track Świeradów r13699712 (`rcn`), Bike Park Czarna Góra r3132963 (`lcn`) and
    Babia Góra Trails r13222440 (its `network` is its own name), each line ridden on its own. The
    marking decides, not `network` alone, because these are not `icn` or `ncn`. A parent with no
    colour has no marking for a member to carry, so all its members are Sections: 6c's fallback to
    the members' shared colour would make the Saxon network r1705154 one Bike route.
  - **A parent's own ways stay on the map as the parent's**, unlike a Long-distance Trail's
    (6c § 2): `part` and `connection` count as members. A parent with Sections and ways of its own
    gets a row for those ways, titled as the parent, with its own walk and sheet; a tap on them
    takes it, and "Show the whole" adds its Sections. ncn 21 r38927 (277 ways of its own) and ncn 22
    r3365635 (832) keep theirs. Rychlebské stezky r377944 holds ways only. Greenway r2110268 had
    2,210, and is `abandoned`, so only its Section is left.
- **The `bike_routes` layer**, from tile zoom 10: one feature per Bike route per way, with
  `bike_route` (the relation id), `kind` (`touring` | `mtb`), `colour` (the five or `other`),
  `network`, `ref`, `index`, `count`, and `dir` (`forward` | `backward`) on a one-way way only.
  **`index`/`count` and the way's drawn direction are computed over the union** of Trails and Bike
  routes on the way: Trails by colour rank then id, then Touring, then MTB, each by colour rank then
  id; the reference relation is the union's first. `trails()` and `reversed()` change to read the
  union. The `trails` layer keeps its `index`/`count` over Trails alone, which the style reads
  until C, and gains the union's as `union_index`/`union_count`, which C's style reads; the
  Trails-alone pair goes with the first pin after C ships. `symbol_offset()`'s match covers slots up
  to the largest union `count` R2 finds, not −8..8, or a crowded way's symbols and plates fall to
  `[0, 0]`.
- **Direction**, per relation, from every place OSM puts it: `oneway:bicycle`, else `oneway`, on the
  way; the member's `forward`/`backward` role; and `signed_direction=yes`, whose sense is the
  relation's own member order. Each one-way relation's sense is computed against its own order, then
  written as `dir` against the way as drawn. Two-way ways carry no `dir`.
- **Ridden one way**, for the sheet: a Bike route with `signed_direction=yes`, or one with at least
  75 % of its walk's length directed for it, by role or by the way's tags, and no stretch against the
  rest. Decided with the owner on 2026-11-02, once the first rule, every edge directed, left Twister
  two-way for three untagged connectors at 98 %: a sheet that hides a direction costs less than one
  offering the climb up a descent. A ring on the walk goes round with the ride: either way round is the
  same length, so the search's tie says nothing, and a closed stretch running mostly against the ride
  is turned where turning the whole walk does not bring it round. A Touring route with a one-way street
  or a `forward`/`backward` pair in town is two-way on the sheet; those ways still draw chevrons. A one-way route's walk starts where its direction leaves: a line
  is turned when 6c's walk runs against it, and a loop goes round the way it is ridden rather than
  the way its first way runs.
- **A Bike route's `backward` member** is off its walk only where it is the other strand of a
  `forward` one: joined with the `backward` members it shares a node with, it touches one `forward`
  strand, joined the same way, at two nodes or more, and no point of it is further than 150 m from
  that strand. The widest pair at B's pin is 109 m apart, and a ring of one `forward` and one
  `backward` strand is wider, so it goes round whole. Every other `backward` member is a stretch drawn against the
  ride, in sequence with the rest and on the walk; the sense its way carries already says it is
  ridden against its nodes, so along the ride it runs with the `forward` ones. A pair's `forward`
  strand counts towards Ridden one way as any directed way does, which leaves Rapický okruh one-way.
  6c's rule, every `backward` off the walk once a relation holds a `forward`, stays the Trails', and
  at B's pin it reaches none of them: 5 Trails hold a `forward`, 1 a `backward`, none both.
- **A walk is decided over its own kind's ways**, not the network's edges: the Trails' walks over
  the edges the Trails' ways alone make, the Bike routes' over theirs, then laid onto the network's,
  which cut those further. So a Bike route's arrival changes no Trail's length, direction or start,
  and a Trail's none of a Bike route's. Joining each route's own chains of edges for every decision
  was measured first and is not enough: a line's direction comes from where the edge holding its
  first way lies, and with one edge there is nothing to tell, so 752 Trails turned against a
  Trails-only build. The one thing the network can refuse is a loop's start, where 6c split a closed
  chain and the network, cut elsewhere, did not: that loop starts at the start of the edge holding
  it and goes the same way round. The longest-path search does join them, for the size of the search alone: 6c's limit
  counts a route's runs, its edges joined through every vertex where only two of them meet and no
  walk starts or ends, so a real network is past it and a long route with one junction is not.
- **The network**: `trails.network` goes to format version 2. The Bike routes' ways join
  `build-network.py` beside the Trails', and four bytes join each edge: `sets` (1 Trail, 2 Bike
  route, 3 both), `ride` (the rider's direction from `oneway:bicycle`, else `oneway` on any highway,
  as `flags` is the walker's), and `mtb` and `mtb_up` (the leading digit of `mtb:scale` and
  `mtb:scale:uphill`, plus one; 0 ungraded). `read_cuts()` keeps those four tags; `walks_like()`
  compares `sets`, `ride`, `mtb` and `mtb_up`, so edges are cut where any of them changes, as they
  are for `sac`; the membership a cut follows carries each Bike route's own sense. `TrailNetwork.kt`
  reads version 2 in B: `canWalkForward()`/`canWalkBackward()` refuse an edge without the Trail bit,
  and `Router` leaves those edges out of its grid, so a Hiking Route neither snaps to nor runs over a
  bike-only edge; the edge indices stay one numbering, which `trails.edges` steps into. A Bike route's
  walk, length, ascent each way and profile come from these edges; the router plans over them from
  7b.
- **The name index**: a table of their own, `bike_routes`, beside `trails`, with `bike_route_names`
  as its FTS4, folded by the one rule. Its columns are `trails`' less `symbol`, plus `kind`,
  `network`, `one_way`, and the route's own `mtb_scale` and `mtb_scale_uphill`. Their walks go into
  `trails.edges` under their relation ids, which no Trail shares.

## 3. On the map

- **The line**: the Trails' widths (`TRAIL_WIDTHS`: 2.0 at 10, 3.0 at 13, 4.5 at 15, 7.0 at 17),
  solid, round joins; yellow and black take the Trails' casing rule, nothing else is cased. Strands
  sit side by side in the union's order.
- **Zoom thresholds**, in the style, the same in both Modes: `icn` and `ncn` from 10, `rcn` from 11,
  `lcn`, no `network` and every MTB route from 12. Judged on the phone (R3) and moved there if the
  map reads badly.
- **The plates**: 20×12 px at 1×, white with the Trail plates' edge, a bar in the marking colour
  across the bottom and a black bicycle over it, after the Polish R-1 sign. Touring: thin wheels,
  drop bar. MTB: heavy wheels, flat bar, standing on a zigzag of rough ground. Upright in the
  viewport, stacked by slot where routes share a way, from zoom 13, size 0.85 at 13 to 1.3 at 17,
  spacing 260 px at 13 to 180 at 16. Sprites `bike_route_touring_<colour>` and
  `bike_route_mtb_<colour>`, `other` included.
- **The colours**: the five are `TRAIL`'s hexes in `gen_style.py`. **No marking colour**: violet
  `#7f3fbf`, the same in both themes, the one new literal beside them in `CLAUDE.md`'s exceptions.
  The `ref` runs along the line, size 10, from zoom 13 on violet routes and from 15 on coloured
  ones; hidden, not faded, in the other Mode.
- **One-way**: open chevrons on the line, 8×8 px, in the strand's casing tone (`route_casing` on red,
  blue, green and violet, the yellow and black casings on theirs), rotated by `dir`, from zoom 13,
  spacing 120 px at 13 to 80 at 16. The slot offset's sign flips with a backward chevron, since the
  offset turns with the icon. A selected route's chevrons are 0.2 larger and never faded, and its
  walk below zoom 10 carries them too.
- **The Mode fade**: the other Mode's lines, plates and Trail symbols keep their colour, marked by a
  second layer key `kozica:mode` (`hiking` | `cycling`). Each style's metadata holds two
  opacities beside `kozica:faded-opacity` (0.3, the current Mode's layers under a selection):
  `kozica:mode-opacity`, the other Mode with nothing selected, 0.4 in light and 0.5 in dark, and
  `kozica:mode-faded-opacity`, the other Mode under a selection, 0.2 in both. `MapStyle.kt` picks
  one per layer; none is derived from another.
- **The switch**: a two-segment button, walker and bike, under the search row at the left; in
  Cycling the chips All · Touring · MTB beside it, filtering the map as the sub-filter will filter
  planning. The Mode is stored and the app opens in the last one, in Hiking on a first launch. In
  `app/src/debug/`; `app/src/release/`'s half passes Hiking.

Copy: "Hiking", "Cycling" (the switch's descriptions), "All", "Touring", "MTB".

## 4. The tap and the sheet

- **Only the current Mode answers a tap**: Trails in Hiking, Bike routes in Cycling. The chooser
  lists the current Mode's routes only.
- **The title**: its `name` (`name:pl` over it in Polish), else its number and kind, else "*colour*
  *kind*: *from* – *to*" from the ends a mapper wrote or the places nearest them, else the colour
  and kind alone. `other` is "marked", as 6c's Trails are. A route with both a name and a number
  shows the number under the title.
- **The facts**: kind, with "for mountain bikes" under an MTB route's, colour and plate, length, ascent and descent each way, the profile. A one-way
  route gives the ridden direction only. No time.
- **The MTB difficulty**: the route's own `mtb:scale` if it carries one (the row's `mtb_scale`),
  else the hardest grade on its walk's edges with the share of its length graded, read from the
  edges' `mtb` byte as the profile reads their heights; `mtb:scale:uphill` the same way; nothing at
  all when nothing is graded. The graded stretches are tinted on the profile, darker for harder.

Copy: "Bike route *n*", "MTB route *n*", "*colour* Bike route: *from* – *to*", "*colour* MTB route:
*from* – *to*", "*colour* Bike route", "*colour* MTB route", with "marked" among the colours; "S*n*"
and "Uphill S*n*" for the route's own tags; "For mountain bikes"; "S*n* at its hardest · graded over *p* % of its length"
and "Uphill S*n* at its hardest" for its ways'; "Ridden one way".

## 5. Search

Search finds places in both Modes, Trails in Hiking and Bike routes in Cycling. A parent with a row for its own ways is found once, as that row, not again as its Sections' parent. When the other Mode
has matches, one row closes the results: a tap switches the Mode and keeps the text, so the results
are now those routes. The row switches the Mode, so it sits behind the switch's seam until 7c:
`app/src/release/`'s half counts no Bike routes and shows no row, and a release search never queries
`bike_routes`.

Copy: "*n* Trails match: show them in Hiking", "*n* Bike routes match: show them in Cycling", each
with its plural forms.

## 6. The mark

The crag becomes the bike the chamois stands on: the slab deepened by 12 units, with two round holes
of radius 17 at (−30, 76) and (18, 76) as wheels; the chamois untouched. A third hole, the frame
diamond (−12, 58) (6, 58) (−3, 78), stays only if it reads at 44 dp.

**Two marks in one identity until 7c.** `PARTS` stays the shipped mark. `mark.py` gains
`CYCLING_PARTS`: `CYCLING_MARK`, the outline with the deepened slab, the same `HOLE`, and the wheels
as `Subpath(hole=True)`. `draw-mark.py` writes `CYCLING_MARK` and the wheels beside `MARK` and
`HOLE`, or its next run loses them. `make-launcher-icon.py --cycling` writes the foreground, the
monochrome and the mipmaps from `CYCLING_PARTS` into `app/src/debug/res/`, which the debug build's
resource merge takes over `main/`'s; `ic_notification`, the background, `play-icon-512.png` and the
feature graphic stay on `PARTS`. In 7c `PARTS` becomes the new mark and the debug copies go.
Rendered at 44 dp, as `art/README.md` judges, 24 dp and 512 px for the owner's approval before it
merges.

---

## Tests

- **Direction**, in the build, one fixture per place OSM puts it: `oneway:bicycle` over `oneway`
  (both senses and `no`), a `forward` and a `backward` role, `signed_direction=yes` with no roles in
  a member order opposite to a way's drawing, and a way drawn reversed by the union's reference
  relation. Fails towards a descent offered uphill.
- **One order over both kinds**: a way with two Trails and two Bike routes, one of each red, gets
  four distinct slots, in the union's order, and the drawn direction of the union's first. Fails
  towards a strand drawn over another.
- **The title rule**, each branch, in English and Polish, as 6c's test does for Trails.
- **Not signed**, in the build: `proposed` and `abandoned` routes of both kinds are not kept, a live
  route on the same way stays, `recommended` is kept, and a Section outlives its dropped parent. Fails
  towards a line on the map with no paint on the ground.
- **Sections or pieces**, in the build, one fixture per branch: an `ncn` parent with same-marked
  members (Sections), one with a Section and an `excursion` (a Bike route of its own, outside "Show
  the whole"), one with only a `connection` member (a Section, not a piece), one with only an `excursion` (a Bike
  route of its own, not a piece), an `lcn` parent with
  all members marked otherwise (Sections), a mixed `rcn`
  parent (the same-marked member a piece writing the parent's id, the other a Bike route of its
  own), and a parent with Sections and `part`/`connection` ways of its own (a row for those ways,
  under the parent's id). Fails towards a tap that takes a fragment, or a parent's own ways that
  vanish.
- **The difficulty line**: the route's own tag over its ways', the hardest grade and its share, and
  no line when nothing is graded; in the build, an edge cut where `mtb:scale` changes along a way.
  Fails towards an easy grade claimed for an ungraded route.
- **Ridden one way**, in the build: a one-way descent whose member order runs uphill gets a walk,
  ascent and descent downhill, and its row `one_way`; so does one 80 % directed past an untagged
  connector, and one whose ring the search took against the ride; a stretch against the rest keeps a route two-way at any share; a Touring loop with one
  one-way street is two-way. Fails towards a sheet describing the climb a rider never makes.
- **`backward` members**, in the build: the far strand of a close pair is off the walk, a stretch
  drawn against the ride between two others is on it and ridden with them, a ring of one `forward`
  and one `backward` strand goes round whole, and a Trail keeps 6c's rule. Fails towards a sheet
  giving a fraction of a route's length.
- **A walk's own ways**, in the build: a Bike route's cut neither turns a Trail nor moves its loop, a
  Trail's cut does not turn a Bike route, a gap stays before its stretch, and a loop starting inside
  a network edge starts at that edge's start and goes round once. In the search, 73 edges with two
  junctions are walked round the long side, and a ladder of 31 rungs is still past the limit. Fails
  towards a Trail's sheet changing because a bike line arrived.
- **Hiking keeps to Trails**, in `RouterTest`: a fixture where a bike-only edge is the shorter way
  plans the Route over the Trail, and a point beside the bike-only edge snaps to the Trail. Fails
  towards a walker sent along a downhill bike line.

## Documents this phase amends

- **ADR-0010**: an amendment: the Bike routes' ways are a second set of edges with direction, built
  in 7a; from B the router keeps a Route in Hiking off them, and from 7b it plans along them.
- **ADR-0011**: its Consequences note that the edges and direction are built in 7a.
- **`PLAN.md`**: 7a's and 7b's paragraphs, for the edges and direction moved here.
- **`CLAUDE.md`**: the colour exceptions gain violet, and `gen_style.py`'s docstring with them; the
  Layout gains the switch's seam and the debug launcher icon.
- **`docs/phase-2.md` § 3**: the `bike_routes` layer, the union's order and the `trails` layer's
  `union_index`/`union_count`.
- **`docs/phase-5.md` § 2–3**: the network's format version 2 (`sets`, `ride`, `mtb`, `mtb_up`), and
  `oneway()` staying the walker's beside the rider's.
- **`art/README.md`**: the second mark and `--cycling`, until 7c.
- **`docs/store-listing.md`** and the release notes: not until 7c, when release users can switch.

## Known gaps, deliberately

- **A route's `state` is OSM's.** A route tagged `proposed` or `abandoned` that is in fact signed is
  gone from the map until a mapper corrects the tag and a later pin brings it back.
- **A Bike route's direction is OSM's.** A trail-centre line mapped with none of the four tags is
  drawn and given as two-way. Fixing it is an OSM edit, reaching the app with the next pin.
- **A ring with a longer ring hanging on it can stay against the ride.** A closed stretch is
  measured with the ring hanging on it, so it can stay as it is while a walk with nothing against
  the ride exists, and the route reads two-way. No route at B's pin is left two-way by it.
- **A route with no ends starts where its first edge does**, as 6c's Trails do, so a ring with a
  chord leaves a run out when that is no junction. Żółta Bielawa r12909385 walks 17.9 of its 21.2 km,
  all of it along the ride. Starting at the junctions too would walk it whole with 8.8 km against the
  ride, so two-way, and would lengthen 14 Bike routes and 4 Trails.
- **Difficulty is thin.** 14 % of MTB-route ways and 1.5 % of Touring ones carry `mtb:scale`; most
  sheets will have no difficulty line, and that is said by its absence rather than guessed.
- **Release users see faded Bike routes they cannot select** from C until 7c. Harmless, and the
  alternative, a second set of archives, is not worth it.
- **Strands hidden by zoom or by a chip keep their slots**, accepted with the owner on 2026-11-02.
  Below zoom 12 and under Touring or MTB, the drawn strands sit off-centre with gaps where hidden
  ones would be; the other Mode's are faded, not hidden, so a switch of Mode leaves none. R3 looks
  at it; if it reads badly, each zoom tier and chip gets its own slots in the tiles.

## Readings

`PLAN.md` rule 3. The test phone (`mnbm7hw8ivdqzx9l`) unless named, its keyguard cleared, a debug
build with the Regions hand-pushed.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The archives' and base files' growth, per Region and in total, on B's branch before it merges | `build-map-data.py` over B's pin; `ls -l map-data/archives/` against the v0.14.4 release's attachments | 2026-11-02, pin `poland 4956`, `slovakia 4956`, `czech-republic 4957`: the nine Regions 354,023,014 → 371,743,028 bytes, +5.0 %: Beskid Niski 45.1 → 46.1 MB, Beskidy Zachodnie 68.0 → 70.8, Bieszczady 37.5 → 38.3, Gorce i Pieniny 41.5 → 43.2, Góry Świętokrzyskie 25.1 → 26.5, Sudety Środkowe 47.0 → 51.1, Sudety Wschodnie 25.8 → 27.6, Sudety Zachodnie 45.1 → 48.5, Tatry 18.8 → 19.5. `trails.network` 9,661,388 → 15,198,240, `trails.edges` 128,444 → 275,796, `places.sqlite` 7,176,192 → 7,565,312. 1,444 Bike routes, 237 ridden one way (230 MTB, 7 Touring), one past the longest-path search: Pasmo Olbrzymy r9972917, a real network, 15.9 km of its 68.2. No Trail is past it, against 5 in a Trails-only build. 2,478 Trails against 2,492, the 14 of § 2 and no other. The Trails' network loses 152 km of 20,879 and no large part of it splits off. **Against a Trails-only build at this pin**, 2,470 Trails tread the same nodes in the same order. Of the other 8, 5 were past the search there and are searched here: Drabina Wałbrzyska r16904530 39.6 → 79.5 km, The Loop r19308791 83.2 → 116.6, Beskidzka Droga św. Jakuba r3076284 291.0 → 291.2, Główny Szlak Beskidu Wyspowego r17794451 190.6 → 190.7, Kłodzka Droga św. Jakuba r3483133 68.5 → 68.6. The other 3 are loops whose start no network edge ends at, so it moves along the same walk, as § 2 says: NS U Mlejna r5990716 568 m, Ścieżka „Dolina potoku Jaszcze” r6199591 2.5 km, Oravská Lesná r8832357 532 m. **Against v0.14.4**, 2,469 Trails keep their length to a metre. Of the other 9, 4 are those the search now reaches (Kłodzka's length is v0.14.4's), and 5 moved under 20 m with the pin: r387407, r1714137, r1705720, r11754098, r1692077. Of the 2,469, 10 walk the other way and 9 loops start elsewhere, by up to 2.5 km: 3 of those loops as above, the rest the pin's, since the Trails-only build gives them too. Their climb moves 0.37 %, 0.09 % with the pin and 0.28 % with the Bike routes' cuts, each of which ends a dead band: 76 Trails move 10 m or more, the most 87 m of 13.2 km. Two builds of the Tatry archive and of the three base files are byte-identical |
| R2 | The title tally for Bike routes, and the largest union `count` on a way | `map-data/network-venv/bin/python scripts/map/trail-tally.py` | 2026-11-02, the cuts stamped 2026-10-28 (6c R1's pin, not the committed one): 1,450 Bike routes, 1,040 Touring and 410 MTB; 1,135 by name, 241 by number, 8 by ends, 66 by neither (52 of them coloured), so 95.4 % titled by tags, the lowest Sudety Środkowe's Touring at 89.7 %. Of 19 parents, 7 keep a row for their own ways and none has a piece at this pin, because every parent outside `icn`/`ncn` is uncoloured. **The largest union `count` is 10**, on 2 ways, against 9 for Trails alone, so `symbol_offset()` needs −9..9; B re-read it at its own pin, with `proposed` and `abandoned` dropped: still 10, on the same 2 ways; 1,444 Bike routes and 18 parents, 6 of them with a row |
| R3 | The plates, the fade, the zoom thresholds and the gaps where strands are hidden (below zoom 12, under each chip) at real size, in both themes | test phone, screencaps at zoom 10–17 over Beskidy Zachodnie and the Sudety || 2026-11-02, debug build, all nine Regions at B's pin: screencaps at zoom 10, 11, 12, 13, 15 and 17 over Beskidy Zachodnie (49.77, 19.05), and 12 and 13 over the Sudety, in light; 11, 13 and 15 in dark; All, Touring and MTB at 12. The thresholds and the fade read as written and nothing moved. The gaps where strands are hidden do not read as a fault under either chip. The plates are legible at 13 and over, and a black route's light casing carries it in dark |
| R4 | Direction: Twister (r5738733) and Rapický okruh (r1636172) draw their chevrons the way they are ridden and their sheets give one direction; Rychlebské stezky (r377944), part directed, draws chevrons on its directed ways and its sheet gives both | test phone, `CameraReceiver` to each, screencaps | |
| R5 | A tap in Cycling where Trails and Bike routes share a way lists the Bike routes only | test phone, screencap of the chooser | |
| R6 | The frame time at zoom 11 and 13 in Cycling with every Bike route drawn, against the same pan with v0.14.4's Region archives hand-pushed instead, taken straight before it. Pass: p95 within 25 % of the baseline's, and no frame over 100 ms beyond the baseline's | phase 4's R1 apparatus and pan as 6c's R3 reread it, all nine Regions and the relief hand-pushed; `CameraReceiver` to Bielsko-Biała's Enduro Trails on Twister (49.77, 19.05), the town's Touring routes in the same frame at zoom 11; `logcat -d -s KozicaFrames \| python3 scripts/map/frame-stats.py` || 2026-11-02, test phone, debug build, Cycling, relief hand-pushed. p95 against v0.14.4's archives, with none over 100 ms in any run: zoom 11, 24.2 ms against 24.1 and 25.2; zoom 13, 20.2 ms against 20.7 and 20.6; the longest frame 44 ms against 41. **Pass** |
| R7 | The hint row switches the Mode and keeps the text | test phone, screencaps | |
| R8 | The mark at 44 dp, 24 dp and 512 px | renders, the owner's verdict | |
