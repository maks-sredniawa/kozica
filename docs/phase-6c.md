# Phase 6c — The user taps a Trail and sees all of it, by name

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
relies on ADR-0007 (every Region's pack is rebuilt and delivered again), ADR-0008 (nothing new reaches
the network: the Trail index ships in the app like the name index) and ADR-0010 (the Trail network,
which gains a third base file and an edge cut wherever the Trails on a way change; the router itself
is unchanged). [`CONTEXT.md`](../CONTEXT.md) gains **Long-distance Trail** and **Section**, and
**Trail** gains the rule for its title and that a regional Trail OSM maps in pieces is one Trail.
Planned with the owner on 2026-10-27.

**What closing this phase means.** Today a Trail on the map is a coloured line that does nothing when
tapped, and the search knows places but not Trails. After this phase the user taps the green line
above Kuźnice and sees that whole Trail picked out from Zakopane's Bulwary Słowackiego to Kasprowy
Wierch with the rest of the map faded, although OSM maps it as four relations (§ 2, *A regional
Trail mapped in pieces*); reads its name, length, climb each way and profile on a sheet, and with one
button turns it into a Route or a GPX file. Typing "Główny Szlak Beskidzki" into search finds it
too. The difference
a device can be asked about: on the test phone, a tap on each of the four strands where four Trails
share a way selects the Trail it was on, by way of a chooser, and the highlight draws within phase
4's frame gate.

`PLAN.md`'s paragraph still holds; it named the change to the data and nothing more.

---

## 0. What this phase inherits

- **The tiles know a Trail's colour, symbol, `ref`, its place among the Trails sharing a way
  (`index`, `count`), and nothing that names it** (`scripts/map/KozicaProfile.java`, `trails`
  layer, from zoom 10). One feature is written per Trail per way. Two pieces of the same Trail are
  therefore indistinguishable from two Trails of the same colour: the map cannot pick out "all of
  this one" until a relation id is in every feature. That one attribute is the whole of the tiles'
  change.
- **Fading the rest of the map already exists.** Layers the style marks `kozica:fade`
  (`scripts/map/gen_style.py`) are faded by `MapStyle.kt` while a Route or a Hike is shown
  (`MapViewModel.faded`, phase 5 § 4, phase 6 § 6). A selected Trail reuses that, so there is one way
  the map steps back, not two.
- **The build lists the Trails' ways, but not per Trail.** `KozicaProfile` gathers every hiking
  relation's ways into one flat set and writes it sorted to `map-data/archives/<id>.ways` (phase 5
  § 2), and the network keeps each edge's first way id. Nothing records which Trails a way belongs
  to. And `chains()` in `scripts/build-network.py` joins ways through every node where exactly two
  meet, so where one Trail ends at such a node and another carries on, that end falls inside an
  edge. The sheet's length, climb and profile, the Route along a Trail and the highlight below zoom
  10 all need a Trail's edges exactly, so B makes the profile write the membership and cuts the
  edges where it changes (§ 2).
- **The name index is SQLite with FTS4 over folded names** (phase 5 § 2, `PlaceIndex.kt`). Trails
  join it as a kind of their own rather than as a second index, so search stays one query and one
  fold rule. `PlaceIndex.nearest` already names a point by the place nearest it, by phase 5's rule,
  for the demanding stretches.
- **A tap on the map today only opens a Hike's photo**; a long press plans a Route. The Trail tap
  takes the plain tap where it lands on no photo, and the long press is untouched.
- **Phase 6 exports a saved Route as GPX** through the share sheet, with the share-grant fix MIUI
  needed. A Trail's GPX is that writer with the Trail's line.

## What is in, and what is deliberately not

**In:** a relation id on every Trail feature and a Trail index built from a freshly pinned extract;
tapping a Trail to select it, with a chooser where several share the way; the selected Trail
highlighted whole with the rest faded; a sheet with the Trail's title, colour and symbol, length,
ascent and descent each way, elevation profile, "Plan a Route along it" (the whole Trail, either
direction) and "Export GPX"; Trails found by name in the existing search; a regional Trail that OSM
maps in pieces taken as one Trail; a Long-distance Trail's Sections selectable one at a time, with
the Long-distance Trail named and shown whole on request.

**Not in, and each with a reason rather than a phase:**

- **A stretch of a Trail as a Route.** The owner chose the whole Trail, end to end. A stretch needs
  a picking mode on the highlighted line, and a Route made from the whole Trail is already editable
  in the Route sheet, where its ends can be moved like any Route's.
- **Trail names drawn on the map as labels.** Names along four side-by-side strands would collide
  with each other and with the symbols already drawn every few hundred metres; the sheet carries the
  name instead.
- **Storing a Trail as anything of the user's.** A selection is not saved; the Route it makes is, as a
  Route: its points, via points included, like any other. No schema change.
- **Unmarked Paths in the tap.** A Path is never named or selected as if it were a Trail
  (`CONTEXT.md`).

**The tempting one: names in the tiles.** Writing each Trail's name into every feature looks free,
since the sheet needs the name. It is not: a name is repeated on every way of the Trail in every tile
at every zoom, and four Trails on one way carry four names. The tiles get one integer, the relation
id; the names, `from`/`to`, the Long-distance Trail and the length live once each in the Trail index,
which the sheet and the search both read.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The gate** — do the Regions' Trail relations carry enough to title them? | `chore:` the tally script; the verdict is a reading | — |
| **B** | The data: relation ids in the tiles, membership from the profile, edges cut where the Trails change, the Trail index, `trails.edges`, the fresh pin | `feat:` (the map data refreshes) | A |
| **C** | Tap, chooser, highlight, the sheet's facts and profile | `feat:` | B |
| **D** | Trails in search | `feat:` | C |
| **E** | Plan a Route along it, and Export GPX | `feat:` | C |

**A comes first** because the title rule (§ 2) is only sound if the data has the tags it reads; if
most relations have neither a name nor `from`/`to`, the place fallback becomes the main path and
search needs the ends' places in the index, which is built in B (§ 1, verdict 2) rather than
discovered in C. **B is one checkpoint** although it touches the profile, the network build and the
index, because each half alone ships nothing and they move the same pin. **D and E need only C** and
can land in either order.

---

## 1. The gate: do the Trails carry what titles them?

### The question, in the form that can be measured

Of the `route=hiking` relations with a way in any of the nine Regions, each counted once across the
union (the cuts overlap, so a relation in two Regions is one relation): what share has a `name` (or
`name:pl`), what share has `from` and `to` but no name, and what share has neither? And how many
Long-distance Trails (`type=superroute`, or a `route=hiking` relation whose members are relations)
have Sections among them, and how many of those carry a name?

### The apparatus

A candidate pin, written to `scripts/osm-data.pin` locally and never committed in A, and
`build-map-data.py` run over it for the merged cuts. `scripts/map/trail-tally.py` reads the nine
merged cuts, counts each relation once by its id, and prints the counts over the union, the same
counts per Region for the record, and twenty sample titles from each rule, so the reading is the
printout. The pin commit lands with B (§ 2): committed in A, it would be one of `map-archives.py`'s
inputs, and the next release would rebuild and re-deliver every pack before B's `trail` attribute
exists, two re-deliveries for one change.

### The verdicts, decided now rather than argued about later

Each is read on the counts over the union; no verdict reads one Region alone.

1. **A name or `from`/`to` on 80 % or more** → § 2's rule as written. The rest are titled by the
   place fallback on the phone, and search cannot find them (Known gaps).
2. **Under 80 %** → the place fallback is the main path, and search would miss most Trails. B then
   also writes each such Trail's two end places into `trail_names` (§ 2) under the Trail's id, so a
   row matched by one shows that place as the name that matched, by phase 5's naming
   rule (the nearest named peak, pass or Hut within 500 m, then any place) ported to the build and
   tested against `PlaceIndex.nearest` on one fixture, as the fold is.
3. **Fewer than five named Long-distance Trails** → the Section rule (§ 2) ships, and the "Show the
   whole *name*" action is dropped rather than built for no data.

### The verdict, taken 2026-10-30

**Verdict 1, on the rule widened to the ends Slovak and Czech mappers write** (R1). Over the
candidate pin (`poland 4952`, `slovakia 4952`, `czech-republic 4953`, all 2026-10-28T21:21:02Z),
2,691 Trails in the union.

**The rule as written missed by 0.9 points: 79.1 %**, 2,119 named and 10 with `from`/`to`, which is
verdict 2. The 562 left were not obscure Trails but a convention: 463 were KST's or KČT's, 7 PTTK's.
Slovak mappers write the ends as `description=A - B`, Czech ones as `note`, `note:cs` or `osmc:name`
in the same shape, and in the Tatry the Trails without a name were among the busiest on the Slovak
side (`Biely Váh - Kriváň`, `Rázc. pri Žabom potoku - Rysy`). Read only as the rule was written, the
gate would have titled the Polish half of a range by its signposts' places and the Slovak half by
the nearest peak. The owner widened the rule rather than take verdict 2 for a tagging habit: the
ends are read from those four tags too, in that order, by the shape check in § 2. `destinations`,
which KČT also writes, was left out: it lists what the signposts point to, not the Trail's two ends.
116 of the 295 below carry it, and 44 of those name a single place.

**Widened, the union reads 89.0 %**: 2,119 named, 10 `from`/`to`, 228 `description`, 2
`osmc:name`, 13 `note:cs`, 24 `note`, 295 with none, and every Region at 83.3 % or more (Tatry
97.4 %, Beskid Niski the lowest). The 295 take the place fallback. 171 are KČT's
(`operator=cz:KČT`): 113 of them carry `destinations`, and 55 carry no ends in any tag. 32 are
KST's, 25 of them without a `description`. The other 92 are 9 PTTK Trails and 83 local and
unofficial ones, with a town, a club, a park or no `operator` at all, 58 of them `lwn` (notes such
as "szlak dziki, operator nie znany").

The shape check misreads a few values either way. It turned away the two prose notes holding a
spaced dash, each by the 40-character limit, but also five values that are ends in other
punctuation: a dash spaced on one side or neither (`[Z] Kočičí hrad- Police nad Metují`,
`[Z] Náchod -Dobrošov- Běloves`, `Košariská-Tvrdošín`) and places parted by `;`
(`Oravské Veselé - Pilsko, št. hr;Mútne - Pilsko, 1557m.n.m`, `Větrník; Česká Čermná; Nad
Bělovsí`). Of the 267 values it read, one is prose, `okružná trasa Mestský úrad - u Šustkov - … -
Mestský úrad`, which titles the loop "okružná trasa Mestský úrad – Mestský úrad", and two ends
carry more than a place: `Dudová 2:10h` and `Volovec (- Pyšné sedlo)`. These are the rule's known
misses (Known gaps); the Tests' fixtures pin the rule as written, not these values. 449 names lead
with KČT's colour letter, `[Ž] A - B`; the build strips it (§ 2).

**Verdict 3 does not apply**: 70 Long-distance Trails have Sections in the Regions (57 `route=hiking`
relations of relations, 13 `type=superroute`), 66 of them named, with 306 Sections. "Show the whole
*name*" is built. Most of the 70 are not long-distance, though: 53 are `rwn` or `lwn` relations, a
regional Trail split at its junctions into pieces, which is how PTTK's Tatry Trails are mapped; A
counted the pieces as Sections, and these figures are A's. r1593233, the green
`Bulwary Słowackiego - Kasprowy Wierch (stacja kolejki)`, holds four pieces, among them
`Kuźnice - Myślenickie Turnie` and `Myślenickie Turnie - Kasprowy Wierch`; r1593481, the blue
`Kuźnice - Schronisko PTTK przy Morskim Oku`, holds eleven; and 137 of the Tatry's 311 Trails are
pieces or Sections, 121 of them pieces. Taken as Sections, a tap there would select one piece, not
the Trail a walker would name, with the whole Trail shown only on request. The verdict holds
without them: 16 of the 66 named are `nwn` or `iwn` (E3, Via Slovakia, the Tatranská magistrála),
over the five it asks for. **The owner then made the regional ones Trails** (2026-10-30, § 2): a
tap selects the Trail a walker names, as signposted, and only the 17 `nwn` and `iwn` relations keep
Sections. A member marked otherwise than its parent stays a Trail of its own, so over A's cuts 41
of the 53 become Trails from 233 pieces, 10 are Trails of their own ways alone, and 2 are nothing.
The tally above counted the pieces as Trails and is not taken again for it; recounted, the union
becomes 2,496 Trails, 88.2 % titled by tags, which is still verdict 1.

## 2. The data

- **`KozicaProfile.java` writes `trail` (the relation id) on every `trails` feature**, alongside
  colour, symbol and `index`. A Section writes its own id; the Long-distance Trail lives only in the
  index. A piece of a regional Trail (below) writes its parent's id, so the tap and the highlight
  take the whole Trail; a piece in two such Trails is written once for each. Features stay one per
  Trail per way, deduplicated by the id written: a way held by both a parent and one of its pieces
  (r1020537's w325798318, also in its piece r105387), or by two of its pieces, is one feature, so
  `count` counts the Trail once and the chooser lists it once.
- **A regional Trail mapped in pieces is one Trail** (the owner, 2026-10-30). Where an `rwn` or
  `lwn` relation has `route=hiking` relations among its members, as PTTK's Tatry Trails are mapped,
  split at their junctions, the members marked as the parent is are its pieces, and the parent is
  the Trail: its row, its title, its walk, its Route and its GPX. A member is marked as the parent
  when its colour and symbol, read as the profile reads them, equal the parent's own, or, where the
  parent carries neither `osmc:symbol` nor `colour`, the one marking all its members share. A member
  marked otherwise is a different Trail on the ground and stays its own Trail with its own row: PTTK's
  *szlak dojściowy* and KČT's `odb.`, a triangle or a turned T branching off a bar Trail, which OSM
  nests under the Trail they leave (r1716770 `Szklary - Bielawa - Przełęcz Puchacza` holds two). A
  parent left with no ways and no pieces is nothing: r5012450, red, whose only member is the blue
  r5120297, and r7466160 `Ścieżka Rycerska`, whose Polish and Czech halves are marked differently
  and so are two Trails. The walk is built from its own ways and its pieces' ways together, by the
  walk rule below, and its colour and symbol are the ones its pieces share. The pieces are not rows,
  not tapped and not found by search. Over A's cuts that is 41 Trails from 233 pieces (121 of them in
  the Tatry), and 15 members of 11 parents left Trails of their own.
- **A Long-distance Trail is an `nwn` or `iwn` relation of relations** (the owner, 2026-10-30), and
  its member relations without a role are its Sections. Beside them, one with a role is a Trail of its
  own, as Via Mariae M02 r4649443's `alternative`s are. In a parent with none of them, it is a piece
  taking that role, as a regional parent's is: KČT's main Trails are `nwn`, and r549809 `Hronov - Kohoutov` (351 ways)
  and r2824733 (87) each hold one spur as an `excursion`, so each is a Trail, its spur tapped and
  highlighted with it and off its walk. The role decides, not the marking: read as the profile reads
  it, r4649443 `Via Mariae M02` and its Sections are all blue with no symbol, their `M02` and `SzM`
  being text. A Long-distance Trail's own ways carry no Trail: r4649443's three (887 m of track and
  service road) and E8 r1175728's one (55 m) are drawn as the paths and roads they are. **A nest
  flattens to its outermost** (the owner, 2026-10-30): where one Long-distance Trail holds another,
  the inner one's Sections are the outer one's, and the inner one is neither a row nor a
  Long-distance Trail. E3 r1175707 holds its Czech part r83249, so a Czech E3 Section is named
  "European long distance path E3" and "Show the whole" shows all of it; `Via Mariae (combined)`
  r11154539 holds r4649443, and the two Svatojakubská cesty hold r10364398 and r11078362. B's build
  at the pin reads 2,492 Trails, 62 of them Sections of 15 Long-distance Trails, all 15 named, still
  over verdict 3's five; and walks 1,929 lines, 75 loops, 267 with a branch and 221 with gaps, 203 of
  their 340 gaps under 100 m, where two ways of a relation do not quite meet, and 54 over a kilometre,
  where a Trail leaves the Regions. 4 walks were past the search, and 3 cover under half of their
  Trail's edges.
- **The profile writes the membership**, since it already decides what a Trail is: `<id>.ways`
  becomes `<id>.trails`, one line per Trail, a `route=hiking` relation or a regional parent, with
  its id and its member ways in member order with their roles. A regional parent's line holds its
  own ways and, at each piece's place, that piece's ways in its own order, with the piece's role
  where it has one and each way's own where not; the profile picks the pieces, since it reads the
  markings, and a piece has no line of its own. And one per Long-distance Trail with its id and its
  Sections (`preprocessOsmRelation` sees `type=superroute` too). A line is tab-separated: `trail`,
  the id, the colour and symbol as the tiles carry them, and the ways as `<way>` or `<way>/<role>`;
  or `long`, the id and the Sections. Names and ends are tags, which `build-network.py` reads from the
  relation in the cut.
  `build-network.py` reads the wanted ways from it, as it reads `<id>.ways` today, and the mapping
  with them, so the rule for a Trail stays written once. The profile runs once per Region, so a relation crossing a boundary is in two
  or more files, each with only the members its cut holds: `build-network.py` merges the lines by
  relation id before any walk is built, the union of their members, with the member order and roles
  of the line holding the most members and the others' extra members after it. A regional parent's
  lines merge so too, as r373033 and r1716770 cross Regions, and so do a Long-distance Trail's.
- **Edges are cut where the Trails on a way change.** `chains()` joins two ways only where both
  walk alike; "alike" gains "carry the same Trails", the Trails whose lines hold the way, so a
  piece's ways carry its parent's Trail, every way of an edge belongs to the same Trails and a
  Trail's end is always an edge's end. The network gains those cuts and loses only a Long-distance
  Trail's own ways (above), 31 at the pin: it is the ways drawn as Trails (phase 5 § 2), and they
  leave it with the Trail they no longer carry. Its format is unchanged, and its bytes change with
  the new pin anyway. ADR-0010 and phase 5 § 2 are amended to say so.
- **The extract is re-pinned** (`scripts/osm-data.pin`) in B's commit, so the one re-delivery of
  every pack also brings the newest OSM. B may take a newer pin than A's candidate; the tally is not
  taken again for it.
- **`trails` table in `places.sqlite`**: one row per Trail with its id, `name`, `name:pl`, `ref`,
  colour, symbol, `from`, `to`, its Long-distance Trail's id and name, the positions of its walk's
  two ends, its length, its ascent and descent along the walk (the other way's are the two swapped),
  its number of gaps, and the bounding box of all its ways, its walk's and the rest. No title column: the
  title is composed on the phone (below). The climbs are the relief's heights, so `RELIEF-DATA-LICENCE.txt` names
  `places.sqlite` beside `trails.network`.
- **The row's `from` and `to` are the ends a mapper wrote, in whichever country's convention**
  (§ 1's verdict): the `from` and `to` tags, else the first of `description`, `osmc:name`,
  `note:cs` and `note` holding place names between spaced dashes, `A - B` or `A - B - C`, whose
  first and last places are the ends. A value counts only when every place is 1 to 40 characters
  with no `;`, which keeps most of a mapper's prose in a note out (§ 1 names what it misreads). A
  leading colour code, KČT's
  `[Ž] ` (one to three characters in brackets), is stripped from these values and from `name` and
  `name:pl`, since the sheet shows the colour already. `scripts/map/trail-tally.py` holds the rule
  as A read it; B writes it into `build-network.py`, tested, and the tally is not kept in step.
- **`trail_names`**, an FTS4 table of its own beside `place_names`, with the same columns and the
  one fold: `place_names.place` is a `places.id` and the search joins `places` on it, so a Trail's
  rows there would point at a place or drop out of the join. It takes each Trail's folded `name` and
  `name:pl`, under the Trail's id; a Trail with neither has nothing to be found by (Known gaps).
  `PlaceIndex.search` runs one query over both, a `UNION ALL` of the places joined to
  `place_names` and the Trails joined to `trail_names`, ordered by the same two keys: the match at
  the name's start first, then the distance from the map's centre, for a place to its point and for
  a Trail to its bounding box, nought when the centre lies inside it. A result is then a place or a
  Trail, one sealed type, and the list stays one list.
- **`trails.edges`**, a third base file beside `trails.network`: for each Trail, its walk (below) as
  edges in order, each with its direction, and where the walk breaks. Little-endian, like the
  network: `KZTE`, a format version and the counts of Trails and steps, then the Trails' ids in
  order, each one's first step and number of steps, and the steps, each the edge's index in the
  network shifted left two, its second bit a gap before it, its first the edge walked from its last
  point to its first. Length, climb and the profile
  are summed from the network's edges along it, as a Route's are, so a gapless Trail and the Route
  made from it agree to the metre. A gapped one cannot: its length and climb are its stretches' sum,
  and its Route adds a router leg across each gap (§ 5), so the sheet's numbers say "with gaps" and
  the Route is longer by those legs. It is an ODbL derivative and ships, records and publishes like the other
  two, so it joins every list that names them: `BASE_FILES` in `build-map-data.py` and
  `map-archives.py` (so the reuse and `--check-determinism` cover it), the `-PmapPacks` check in
  `settings.gradle.kts`, the `gh release upload` in `publish-play.yml`, `MAP-DATA-LICENCE.txt`,
  `NetworkSource.kt` in both variants (the debug half reading a copy hand-pushed to
  `files/network/`), and `docs/RELEASING.md`'s account of what a release attaches.
- **A Trail's walk** is built from its edges as a graph, not from its members' order, which OSM does
  not keep reliably; the members' order only picks which end comes first, the one nearer the first
  way in member order. A regional Trail's ways are its own and its pieces', each piece's ways taking
  its place in the parent's member order, in the piece's own order; nodes and any other member that
  is not a way are ignored, and a role on a piece applies to each of its ways. Members with role
  `alternative`, `excursion`, `approach` or `branch` are the Trail's, in its tiles and so tapped and
  highlighted with it, but off its walk, so not in its length, profile, Route or GPX; where `forward`
  and `backward` members split the line in two, the walk takes the `forward` strand. What is left
  takes one of four shapes. **A line** is walked from its first end to its other. **A loop**, with no
  end, starts and finishes at the start of the edge holding the first way in member order, and goes
  round once the way that way runs. **Gaps**, the edges falling in more than one stretch, are walked stretch
  by stretch, each entered at the end nearest the one just left, from whichever stretch's end leaves
  the least gap in all; where two starts leave the same to a metre, the members' order picks, as it
  does a line's first end. Each break is recorded: the
  length and climb are the stretches' sum, and the profile breaks at each gap. **A branch
  with no role**, a node where three of the Trail's own edges meet, is walked along the longest path
  between two of the Trail's ends that takes no edge twice, so round the long side of a loop between
  them and round a loop it passes on the way; where a walk from an end that stops elsewhere is more
  than twice as long, as where a loop hangs off the Trail beyond a short stub, it takes that one.
  With fewer than two ends, it is the longest such walk from the one end, or else from the start of
  the edge holding the first way. The rest is treated as an `alternative`. Where the edges hold a
  loop every such walk is searched; past 60 runs of its edges (phase 7a § 2) or 200,000 steps of search, the walk is the path
  between the two ends farthest apart, as the build logs. A Trail whose every way
  has one of those roles is walked over all of them, so that every Trail has a walk. The build logs
  how many Trails took each shape.
- **The title is composed on the phone**, in the app's language, from the row's parts: `name:pl` or
  `name` if present; else "*colour* Trail: *from* – *to*" from the row's ends; else the same with the
  place `PlaceIndex.nearest` names at each end (phase 5's rule); else the colour alone, "*colour*
  Trail". The colour is one of the five as an adjective, or, for `other` (any colour outside the
  five, or none, which phase 2 draws grey), "marked": every Trail is marked, so the word claims
  nothing the signpost might contradict. A Section shows its own title, and its Long-distance
  Trail's name above it. The build writes no composed string, so no title can reach the phone in the
  wrong language.

## 3. On the map

- **A tap** queries the `trails` layer in a finger-sized box around the point. One Trail id →
  selected. Several → a chooser listing each with its colour chip, symbol and title. None → the tap
  does what it does today. A Hike's photo under the finger still wins the tap.
- **The selected Trail** is drawn over the faded map (`kozica:fade`). From zoom 10, by a highlight
  layer over the `trails` layer filtered on its id, so it sits on its own strand at its offset,
  across every Region whose tiles it crosses. Below zoom 10, where the `trails` layer has no features
  (`setMinZoom(10)`), by a line drawn from the network's geometry along its `trails.edges`, as the
  drawn Route is: no strands are drawn there for it to sit beside, and a 500 km Trail fitted whole
  still shows. A Long-distance Trail's "Show the whole *name*" highlights every Section that names
  it, the same two ways.
- **The camera** stays where it is after a tap; "Show whole Trail" on the sheet fits its bounding
  box, at whatever zoom the box needs. A Trail picked from search fits it straight away.
- **What else may be on the map** (decided with the owner on 2026-10-27). **A Route being planned**
  is set aside when a Trail is selected, by tap or from search: its points are kept, the map and the
  sheet show the Trail, and closing the Trail brings the Route back as it was; "Plan a Route along
  it" replaces the Route set aside, unsaved changes and all, while a saved Route stays in the Routes
  tab. **A shown Hike** gives way to a Trail tapped off its photos: the
  Trail replaces the Hike's view, as opening another Hike would, and the saved Hike is untouched.
  **During a recording** the Trail is selected on top and the recording bar stays; nothing stops or
  pauses, and closing the Trail leaves the recording as it was.
- **Leaving:** closing the sheet, back, or a tap on empty map clears the selection and the fade, and
  brings back a Route set aside.
- **The sheet:** title (the Long-distance Trail's name above it for a Section), colour and symbol,
  length, ascent and descent each way, the elevation profile (the Route sheet's chart), and two
  buttons: "Plan a Route along it" and "Export GPX".

- **Settled in C** (by the agent, 2026-10-30, while the owner slept; each is the owner's to reverse).
  The name in the title is `name:pl` first in Polish and `name` first in English, so an English
  sheet does not show a Polish name where OSM has both. The tap's box is 16 dp each way of the
  finger, which takes in strands drawn side by side. Below zoom 10 the tap is also checked against
  the highlighted walk, the only Trails drawn there, so a tap on it is not a tap on empty map. A long press while a Trail is selected closes it
  and adds the point to the Route set aside, so the Route the press changes is the one on screen. The
  sheet's mark is drawn in Compose by `sprites.py`'s shapes, in the map style's five Trail colours.
  "Plan a Route along it" and "Export GPX" arrive with E.

**Copy:** "Trails here", "Show whole Trail", "Show the whole %1$s", "Plan a Route along it", "Export
GPX", "%1$s Trail: %2$s – %3$s" (colour adjective, ends), "%1$s Trail" (the colour alone), "with
gaps" (beside a gapped Trail's length), and the five colour names and "marked" as adjectives for a
Trail.

## 4. Search

Trails appear in the existing search results with their colour chip and symbol, ranked with places by
the same folded match and, within it, by the distance to their bounding box (§ 2); a Long-distance Trail matches by its own name and its Sections by theirs. A
Trail's row shows its title, the name that matched underneath where it differs, and then "Trail" and
its length where a place shows its kind and its Region, since a Trail lies in no one Region; a
Section's adds the Long-distance Trail it belongs to. The field's hint becomes "Search places and
Trails".

Picking a Trail selects it and fits the camera (§ 3); it is not offered as a Route point. Mid-plan,
the Route's points are kept and the Route comes back when the Trail is closed (§ 3, decided with the
owner on 2026-10-27). Picking a place does what it does today: the map centres there and offers it
as a point.

**Settled in D** (by the agent, 2026-10-30, the owner's to reverse). The ranking runs as three reads
merged in `PlaceIndex.searchAll` rather than one `UNION ALL`, by the same two keys. A Long-distance
Trail has no row, so its own name is matched against its Sections' `long_distance_name` with the one
fold; its result says "Long-distance Trail", and picking it selects the Section nearest the map's
centre with the whole Long-distance Trail shown and fitted, as "Show the whole *name*" would. A
Section named as its Long-distance Trail (the Tatranská magistrála's) shows the name once.

**Copy:** "Search places and Trails", "Trail" (the kind), "Long-distance Trail" (its kind), "Section of %1$s"; the length as the Routes
tab writes one.

## 5. A Route along it, and GPX

- **"Plan a Route along it"** makes an ordinary Route that stays on the Trail by its via points
  (decided with the owner on 2026-10-27); ADR-0010's router plans it unchanged. Its start and end are
  the walk's two ends, and a via point goes at each of the Trail's junctions where the router, left
  to itself, would take a shortcut over another Trail. The phone finds them along the walk: from the
  last point placed, the next goes at the farthest junction to which a leg, planned either way,
  walks exactly the walk's edges between them, in order and each the walk's way. That junction is
  found by doubling the span along the walk and then bisecting back, since a leg that follows the
  walk as far as one junction follows it as far as every junction before. A gap in the walk gets a point on each side, and its leg is whatever the
  router finds between them. A loop's Route is a round trip, its start picked again as its end
  (phase 5 § 4), held open by at least one via point.
- **The points are the Route's like any other**: shown and numbered on the map, movable and
  removable in the sheet, saved as a Route's points are and planned again on open (phase 5 § 5). The
  ordinary router follows the Trail on every re-plan because the points hold it there, reversed too,
  since each leg was checked both ways; only a one-way stretch, which no Route walks against, leaves
  the Trail when reversed. The Route opens in the ordinary Route sheet, saved under the Trail's title
  by default.
- **Settled in E** (by the agent, 2026-10-30, the owner's to reverse). Where another way joins two
  neighbouring junctions more cheaply than the Trail's own edge, no junction can hold the Route, so
  the via point goes halfway along that edge by length. A span of nothing but one-way edges is
  checked only its own way, since no Route walks it the other; a span that mixes them is checked
  both ways, so a one-way stretch always ends one span and makes the next by itself. Each point's search starts
  at the span the last one found rather than at one junction, as neighbouring points lie about as far
  apart. The Route keeps to the Trail exactly; a point inside an edge splits its climb, so the Route's
  ascent and descent can differ from the row's by up to the 5 m dead band per such point (R4).
- **"Export GPX"** writes the Trail's walk with the network's heights through phase 6's writer and
  share path, one track segment per stretch where the walk has gaps, named after its title, with the
  OSM `<copyright>` and `<link>` the Route export carries.

---

## Tests

- **The title rule**, each branch, composed on the phone in English and in Polish: name, `name:pl`
  over `name`, `from`/`to`, the place fallback, the colour alone, `other` as "marked", a Section
  with its Long-distance Trail. Fails towards a blank or a wrong-language title.
- **The ends as written**, in the build, one fixture value per convention: `from`/`to` over a
  `description`, each of the four tags in its order, a chain of three, a colour code stripped from a
  value and from a name, and refused: a value with one place, a place over 40 characters (a note in
  prose) and one holding `;`. Fails towards a title made of a mapper's sentence.
- **The walk**, on a fixture with each shape: a line, a loop, a gap, an `alternative` branch, a
  branch with no role, and members out of order. Consecutive edges join end to end except at a
  recorded gap, a loop returns to its start once, branches are off the walk, and the length equals
  the sum of the walk's edges. Fails towards a profile that jumps.
- **A regional Trail in pieces**, in the build: an `rwn` bar-marked parent with ways of its own,
  one of them also in a piece, three bar-marked pieces, one of them also in a second parent, and a
  triangle-marked spur. One row, under the parent's id, in the parent's colour and symbol; a walk
  over its ways and its pieces'; the pieces' features carrying the parent's id, one per way however
  many of its relations hold it, the shared piece's once per parent; no row for a piece; the spur a
  row of its own under its own id. A parent with no ways whose only member is marked otherwise gives
  no row. An `nwn` parent of the same shape stays a Long-distance Trail with Sections. Fails towards a
  tap that takes a fragment of a Trail, or a spur taken for the Trail it leaves.
- **The cut where the Trails change**: on a fixture where one Trail ends at a node where only two
  ways meet and another Trail carries on, that node is an edge's end. Fails towards a Trail whose end
  lies inside an edge.
- **The via points**: on a fixture where another Trail is a shortcut, the Route along the Trail stays
  on it both ways, and again when only its stored points are planned anew, as a saved Route is on
  open; a loop and a walk through one junction twice are walked whole, and a one-way stretch is a
  span of its own. Fails towards a Route that leaves the Trail once reopened, or cuts a loop short.
- **Folding**: Trail names fold by the one rule places use (`ł`, accents), in the build and on the
  phone.

## Documents this phase amends

- **ADR-0010**: an amendment: the network's edges are also cut where the Trails on a way change, and
  `trails.edges` is a third base file beside the network and the name index, shipped, recorded and
  attached as they are. The router is unchanged; a Route along a Trail is an ordinary Route that its
  via points hold on the Trail.
- **`docs/phase-5.md` § 2**: `<id>.ways` becomes `<id>.trails`, and the edge cut gains the Trails on
  a way.
- **`docs/RELEASING.md`**: the release attaches `trails.edges` with the other base files.
- **`CONTEXT.md`**: **Long-distance Trail** (a Trail made of Sections, each its own relation: an
  `nwn` or `iwn` relation whose members are relations, `type=superroute` or `route=hiking`; Polish UI
  *szlak długodystansowy*; not "parent", "long Trail" or "superroute") and **Section** (one relation
  of a Long-distance Trail; Polish UI *odcinek*), and under **Trail** the title rule and that a
  regional Trail OSM maps in pieces is one Trail.
- **`docs/store-listing.md`**: the full description gains tapping and finding Trails by name, in
  both languages, and the release notes say so.
- **`docs/phase-2.md` § 3**: the `trails` layer's attributes gain `trail`.

## Known gaps, deliberately

- **A Trail's title is OSM's, not the signposts'.** Where a relation has no name, no ends written in
  a convention § 2 reads and no place near an end, the title is the colour alone, "Red Trail" or
  "Marked Trail". The shape check that reads the written ends misreads a few values either way
  (§ 1). Search finds a Trail by its `name` or `name:pl` alone, so the 572 Trails with neither,
  22.9 % of the 2,496 the regional rule leaves (§ 1), are not found: the 277 titled by `from`/`to`
  or by written ends as well as the 295 titled by nothing. Nor is a Trail found by the places at its
  ends; the places themselves are. A convention not yet read, such as KČT's `destinations`, is a
  change to § 2's list; a Trail with no ends anywhere is an OSM edit. Either reaches the app with the next data pin.
- **A piece's own name is not searched.** The pieces are not rows, so `trail_names` holds the
  parent's name and not theirs, although all 233 carry one: `Kuźnice - Myślenickie Turnie`, a piece
  of r1593233, is not found, and its Trail is found as
  `Bulwary Słowackiego - Kasprowy Wierch (stacja kolejki)`. The places at a piece's ends are found.
- **A Trail's walk is the build's reading of a relation OSM may map loosely.** A branch with no role
  is walked by the longest path, which may not be the signposted one.
- **A Trail crossing out of every Region is cut where the data stops.** Its length and Route are
  those of the part Kozica has.

## Readings

`PLAN.md` rule 3. The test phone (`mnbm7hw8ivdqzx9l`) unless named; its keyguard cleared and every
debug broadcast sent as phase 5's readings send them.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The gate's tally over the union and per Region | `build-map-data.py` over A's uncommitted candidate pin, then `python3 scripts/map/trail-tally.py` | **Verdict 1**, 2026-10-30, pin `poland 4952`, `slovakia 4952`, `czech-republic 4953`: 89.0 % of 2,691 titled by the rule as § 2 widened it (79.1 % as first written), every Region at 83.3 % or more; 66 of 70 Long-distance Trails named (§ 1) |
| R2 | Four strands at one point: each tap's chooser lists all four, and each pick highlights the right one | test phone, debug build with the Regions hand-pushed; screencaps | 2026-10-30, test phone, debug build at 713064f, this pin's Regions hand-pushed: way 24336375 in the High Tatras (r1692927, r1208354 Magistrala Tatrzańska, r15469655 Via Slovakia, r11249593 Náučný chodník Hrebienok), zoom 17. One tap at the bundle's middle opened the chooser with all four, each with its mark and title, the two Sections with their Long-distance Trail above; each pick highlighted its own strand, the two greens told apart where they part. **Pass** |
| R3 | The highlight and fade against phase 4's gate: the pan at zoom 11 and 13 with the green Trail from Bulwary Słowackiego to Kasprowy Wierch (r1593233, four pieces) selected, against the same pan with nothing selected taken straight before it. Pass: p95 within 25 % of the baseline's, and no frame over 100 ms beyond the baseline's | phase 4's R1 apparatus and pan, all nine Regions and the relief hand-pushed; `CameraReceiver` to a point on that Trail at each zoom, then for the selected run `input tap 540 995`, the map's centre, and the chooser's green row if one opens; `logcat -d -s KozicaFrames \| python3 scripts/map/frame-stats.py` | 2026-10-30, test phone, debug build at 6c9374d, all nine Regions and the full relief pack hand-pushed, camera on r1593233's way 249237712 (49.23725, 19.97760). The swipes are at y 600–800, since with the sheet open phase 4's y 1100–1300 land on it; the baseline takes the same swipes, after one warm-up pan. The first reading tapped the Trail at the screen's centre and was read with `frame-stats.py --idle 100` on a log from before the camera lines, not at phase 4's default 500 ms: each 1.2 s pause between swipes is now split by a frame or two into gaps under 500 ms, which the default counts as frames, and at zoom 13 they reach p95 (233.4 / 303.2 ms), since the pauses' frames are mostly the position dot's: with the dot shown the map draws 3–4 frames about every 470 ms with the camera at rest, and the zoom-13 pan's rests drew 159 frames, 25 with location revoked (2026-10-30, test phone). p95 baseline / selected: zoom 11, 20.3 / 21.0 ms; zoom 13, 19.0 / 23.3 ms. Reread the same day at the frame log's camera lines, the frames timed from each move to the map's idle after it, the Trail tapped at (540, 995), the map's centre: p95 baseline / selected, zoom 11, 20.1 / 22.0 ms (522 / 471 frames); zoom 13, 18.2 / 18.2 ms (355 / 359); no frame over 100 ms in any run, the longest 43 ms. **Pass** |
| R4 | A Route along a Trail matches the Trail: the green Bulwary Słowackiego – Kasprowy Wierch (r1593233, one Trail from four pieces) and Główny Szlak Beskidzki (r545421, one relation), each way, and again planned anew from its stored points. Pass: length to the metre and ascent and descent equal to the Trail's row plus the legs across its gaps, if any (none for a gapless walk), and Główny Szlak Beskidzki's Route made within 2 s | `RouteReceiver` gains `--el trail <id>` and `--ez reversed true`: it makes the Route by the button's code, logs the Trail's row, its number of gaps with each gap leg's length, ascent and descent, and the time taken beside its `route:` line, then plans the stored points again and logs that `route:` line too; `adb logcat -d -s KozicaRoute` | 2026-10-30, test phone, debug build, `RouteReceiver --el trail <id> [--ez reversed true]`, rerun after a leg had to walk the walk's own edges in order. r1593233: 3 points in 690 and 185 ms; the Trail 8,712.9 m, 1,228.6 m up, 191.5 m down, and the Route and its replanning the same to the tenth of a metre both ways, reversed swapping the climbs. r545421 Główny Szlak Beskidzki: 51 points, the Route 499,942.5 m, the Trail's length to the tenth both ways and again on replanning, each way walking all 336 of the walk's edges in order; ascent 20,513.8 against 20,518.0 m and descent 20,883.2 against 20,887.4 m, 4.2 m under, and reversed 20,890.3 and 20,520.9 m, 2.9 m over (the points inside edges, § 5); made in 2,441 and 2,402 ms. **Length passes; the climb misses by 3–4 m in 20 km and the time by 0.4 s, both accepted as they are by the owner (2026-10-30)** |
| R5 | The update size Play reports for the re-delivered packs, and the three base files' sizes beside the last release's two | Play Console, the release's size; the release's attachments | 2026-10-30, the App bundle explorer for 345 (0.11.0). Play's update size is the app's alone: 11.9 MB from 315 (0.10.0), 12 MB from 301, 12.5 MB from 243, against 17.7 MB for a new install. The asset packs show only a download size, the nine Regions 247.4 MB together (Tatry 13.2 MB to Beskidy Zachodnie 47.1 MB), with no update size for any pack and none in the comparison with 315; the relief, its pin unchanged since 0.10.0, was not re-delivered. Base files, 0.10.0 / 0.11.0: `trails.network` 9,597,124 / 9,660,550 B, `places.sqlite` 6,475,776 / 7,172,096 B, `trails.edges` 128,448 B, new. **Read; a pack's update cost was owed until phase 2b's R10**: the Console does not report it. Read on a phone 2026-10-31 without settling it: the owner's phone (API 36), its Play install at 0.10.0 updated in place from the internal track to 0.14.0 (408) with "Update", which packs it held before not recorded. Play fetched all ten packs as part of the update, in 2 min 33 s, 646 MB by the Play page and 662,224,097 B on `wlan0`, and each came whole. For six Regions Finsky's last progress count before each finished is already past the 0.14.0 archive, by 41.6–44.2 KB: Bieszczady (37,515,684 B), Beskidy Zachodnie (68,043,179), Sudety Środkowe (47,055,273), Beskid Niski (45,011,366), Gorce i Pieniny (41,542,055) and Sudety Wschodnie (25,846,186). Tatry (8,340,716 against 18,840,991), Góry Świętokrzyskie (16,958,135 against 25,076,157) and Sudety Zachodnie (31,270,169 against 45,095,856) last reported short, so for those three "whole" rests on the totals: the nine archives' 353,769,616 B and the relief's at least 267,756,005 B, 621.5 MB together, against Play's 646 MB and `wlan0`'s 662 MB. The relief, unchanged since 0.10.0, came whole too, though whether the install held it is not known; either way the update spans four releases rather than one that only moves the pin, so it says nothing about the format. The 0.10.0 → 0.14.0 update ran no migration (schema 3 both sides); afterwards it showed no Hike and no saved Route, but neither was read before the update, so this says nothing about data kept. The owed reading was taken 2026-11-01 as [`phase-2b.md`](phase-2b.md)'s R10: 0.14.1 → 0.14.2, a release that only moves the pin, patched six Regions at 38–253 KB and sent the Tatry, both Sudety Środkowe and Zachodnie and the unchanged relief whole, 33 minutes after the upload, and as R11: 0.14.2 → 0.14.3, read 2 hours after its upload, sent every pack whole at the first request; their verdict is the owner's ([`DOD.md`](DOD.md)) |
