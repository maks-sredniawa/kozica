# Cycling follows marked Bike routes only, and its riding times are our own estimate

## Context

Kozica's Regions hold about 1,450 signed cycling routes, `route=bicycle` and `route=mtb` relations,
counted in [`cycling-research.md`](../cycling-research.md) § 3 with how each count was made. They
come in the same Geofabrik extracts the map is already built from, and the map profile drops them
today. Planned with the owner on 2026-11-01 for 1.0.0; that file is the research behind it, with its
sources.

Three facts shape it. **No signpost gives a riding time**: Polish, Czech and Slovak cycling signs
give kilometres, and PTTK's marking instruction has no cycling counterpart to the walking rule
ADR-0010's times follow. **The national parks in the Regions allow bikes only on designated routes
and public roads**, as far as the research could confirm: the Polish and Czech statutes say so,
Slovakia's did in its 2014 text, whose current wording was not verified, and the parks' own pages
agree, except that Karkonoski's (PL) and Babia Góra's rules are **[unconfirmed]**, and Babia Góra
may allow no bikes at all. **One-way MTB lines are mapped inconsistently**: on the way (`oneway`,
`oneway:bicycle`), in the relation's roles, or in `signed_direction`.

## Decision

**One map, one switch: the Mode, Hiking or Cycling.** Everything follows it: what the map draws in
full colour, which routes search finds, how a Route is planned and timed, and what a recording is.
Search finds places in both Modes, and Trails in Hiking or Bike routes in Cycling, with a hint
offering the other Mode's matches. The other kind of route stays visible, faded. In Cycling a
sub-filter chooses All, Touring (`route=bicycle`) or MTB (`route=mtb`), and it limits planning as it
limits the map. The app opens in the Mode last used, in Hiking on a first launch.

**A Route in Cycling runs only along Bike routes**, Touring and MTB, as one in Hiking runs only
along Trails (ADR-0010). The two never borrow from each other: a Route in Hiking does not use a
bike-only way, and one in Cycling does not use a Trail that is not also a Bike route. A way's
direction in Cycling is read from every place OSM puts it: `oneway:bicycle`, else `oneway`, the
relation's `forward`/`backward` roles and `signed_direction`.

**A Route in Cycling shows its distance and ascent first, and its time as an estimate**, "≈", from
our own rule in the shape of the walking one: a flat speed per kind of Bike route, a time per metre
climbed, and descents faster than the flat, capped by surface and `mtb:scale`. Its numbers are set
from the published routers' (BRouter, GraphHopper, Valhalla) and checked against the riding times
mapy.cz, Komoot and official route guides give for 10–15 of our Bike routes, both ways, before
release; only compared, never copied.

**The Pace is personal and per Bike.** "My bikes" is optional: a unique name and an optional photo,
nothing else, so an e-bike is simply another Bike. Each Bike learns one factor for Touring and one
for MTB from its recorded Rides; with no Bike added, every Ride is the same Bike. All of it stays on
the phone (ADR-0008).

## Alternatives

**A filter on the map alone, with planning choosing its Mode apart.** The map and a plan could
disagree: Bike routes on screen, a walk planned. One switch keeps them in step.

**Trails and Bike routes both in full colour.** Polish signs use the same five colours for both, so
the two would be one tangle. A Bike route is told apart by bicycle symbols along its line, and the
other kind is faded.

**Routes in Cycling joined over any way open to bikes, or along Bike routes first with roads
between.** It connects more places, at the cost of the promise that a Route is always something
marked, and inside the parks OSM's plain access tags are not reliable enough to keep a rider legal:
in a box over the Polish Tatras about a third of the paths, tracks and footways carry `bicycle=no`
and only 29 ways `bicycle=yes`, so most of TPN's six legal routes appear not to be tagged as open
**[unconfirmed per route]**.

**A physics model, as BRouter's**: rider mass, power, air resistance. More exact in principle, but
it asks for numbers riders do not know, and its result is hard to check. The walking-shaped rule is
explained in a sentence, and the personal factor corrects it within a few Rides.

**Time first, as in Hiking.** It would present an invented number as firmly as a signpost time.

**A Pace per kind of Bike route (Touring or MTB) alone, or per Bike alone.** The same person rides a
road and a singletrack at very different speeds, and the same route on an e-bike and a plain one;
keying the factor by both covers each without any setting but a name.

## Consequences

- Every Region's archive grows with the Bike routes and is delivered again; the growth is measured
  in phase 7a before it ships.
- The network ADR-0010 builds gains the Bike routes' ways as a second set of edges, with their own
  direction and timing, in an amendment there; the edges and direction are built in phase 7a, the
  timing in 7b, and the base files' growth is measured in both. A riding time is never compared with a signpost's, since no signpost gives one.
- The hike log holds Rides too, each marked and changeable afterwards. The Bikes, whether a
  recording is a Hike or a Ride, and a Ride's Bike are a schema change, shipped under the house rule
  on migrations: a registered `MIGRATION_x_y`, the exported schema, the gate's assertion and a
  migration test. A Bike's photo is a media kind of its own, with its own directory, written through
  `MediaFiles` (ADR-0002) and stored relative, so Backup carries it as it carries a Hike's photos.
- Following a Route in Cycling keeps the screen on and speaks junction directions through the
  phone's own speech engine, in an on-device voice only (`Voice.isNetworkConnectionRequired()`
  false), so the spoken text never leaves the phone; Kozica opens no connection for it. Phase 7d
  checks on a phone that the engine speaks with no network connection and records it against
  ADR-0008. Sharp-bend warnings are our own reading of a way's shape, so they stay an experimental
  setting, off by default: OSM marks almost no bends off the roads (in all of Poland, 62 objects
  tagged `hazard=curve` or `curves` and 138 bend signs, with one on a cycleway and one on a track;
  [`cycling-research.md`](../cycling-research.md) § 3).
