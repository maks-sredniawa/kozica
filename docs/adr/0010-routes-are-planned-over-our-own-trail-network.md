# Routes are planned over our own Trail network, built with the map data and shipped in the app

## Context

Phase 5 plans a Route on the phone: the shortest walking time over Trails between the user's
points, with its ascent from the height grid. ADR-0008 rules out any routing service, so the router
and its data are on the phone. The map's PMTiles cannot be routed over: their geometry is clipped at
tile edges, simplified per zoom and carries no junctions, so the planner needs a network of its own.
The Trails are a small part of OSM, and a Route may cross from one Region into the next, where the
packs overlap by a few kilometres.

## Decision

**`build-map-data.py` writes the network**, from the same pinned extracts as the Region archives:
every way drawn as a Trail, from the union of the nine Regions, each once, split at junctions, with
heights sampled from the pinned relief at build time. It writes the name index for search in the
same step. **One network for all Regions**, so no Trail is cut at a Region's edge.

**Our own A\* searches it**, in Kotlin, with walking time as the cost and one-way Trails obeyed
(`docs/phase-5.md` § 3).

**Both files ship in the app's base module**, not in a pack: they are a few megabytes, needed the
moment the map opens, and change only when the pin moves, which is a release anyway. They are
attached to each release with the archives, under ODbL and, for the network's heights, the relief's
licence, and they join `scripts/map-archives.py`'s record, with `scripts/relief.pin` among its
inputs.

Decided with the owner on 2026-10-25, while phase 5 was planned.

## Alternatives

**GraphHopper on the phone.** A mature router with hiking profiles. But its Android support is
unofficial, it is a heavy dependency for a network of Trails alone, and it brings a graph format of
its own that would be built from the same extracts anyway, by a second tool the build would have to
pin and keep deterministic.

**BRouter.** Strong for hiking, built for offline Android, and MIT since 2019, so its engine could
be linked into the app; handing off to the BRouter app instead would break the "one app, nothing
else to install" promise. Linked, it loses on the build side rather than on the phone. Its segment
files come from its own multi-stage Java map creator, a second tool to pin and prove deterministic
beside planetiler, which takes its heights in its own raster format, so the relief would reach it
through a conversion the contours do not go through. Its 5° tiles match neither the Regions nor one
network. "What the map draws as a Trail" would be written a second time, in a BRouter profile that
sees hiking relations through its own tag mapping. And the walking-time rule, applied over whatever
it returns, and the name index would still be ours. Against that, our writer and A\* are small, over
a network small enough to load whole. It is the nearest alternative, and on these costs the owner
decided on 2026-10-25 to keep our own network.

**A network per Region, inside each Region's pack.** It would refresh with the Region. But every
Trail that crosses a boundary would be cut there, a Route across two Regions would need the two
networks stitched on the phone, and a pack not yet unpacked would leave holes in planning.

**Heights read from the relief tiles on the phone.** It keeps the network free of heights and the
grid as the one source at runtime. But it means decoding lossless WebP Terrarium tiles while
planning, on a 2019 phone, for numbers that never change between relief builds; sampling once in the
build gives the same numbers, under a test in Python.

## Consequences

- The build grows a second output and a dependency on the relief pin; a relief rebuild now changes
  the network too, and the publish run's reuse sees it.
- Every OSM pin changes the network, so a saved Route, which stores only its points, can take a
  different way after an update.
- The network's format is ours to version. Its reader and writer are tested against one fixture,
  and a format change is a release like any other, since both ship together.
- The base APK grows by the two files' size, read in phase 5's gate.

## Amendment — 2026-10-30: edges are also cut where the Trails change, and a third file walks them

Phase 6c selects a Trail whole and turns it into a Route ([`docs/phase-6c.md`](../phase-6c.md) § 2),
which needs a Trail's end to be an edge's end. Two ways now join into one edge only where they also
carry the same Trails, each with whether the way is on that Trail's walk. The network gains those cuts,
and a Long-distance Trail's own ways leave it with the Trail they no longer carry (31 ways at this
pin); its format is unchanged. **`trails.edges`** is a third base file beside the network
and the name index: each Trail's walk as steps into the network's edges. It is an ODbL derivative, and
it ships, is recorded and is attached to the release as the other two are. The router is unchanged:
a Route along a Trail is an ordinary Route that its via points hold on the Trail.

## Amendment — 2026-11-01: the network carries Bike routes too

ADR-0011 adds Cycling, whose Routes run only along Bike routes; Rides and their timing are decided
there. The network gains the Bike routes' ways as a second set of edges beside the Trails', with
their own direction and timing, so it holds every way drawn as a Trail or a Bike route, and a Route
in Hiking still runs over the Trails' edges alone. The base files' growth is measured in phases 7a
and 7b.

## Amendment — 2026-11-02: the Bike routes' edges are built in phase 7a, and Hiking keeps off them

The second set of edges and its direction are built in phase 7a rather than 7b, because a Bike route's
sheet reads its length, ascent and profile from them. The network's format goes to version 2, each edge
saying which sets it is in. From 7a the router keeps a Route in Hiking off an edge without the Trail bit,
for snapping as well as for walking; from 7b it plans along them in Cycling.

## Amendment — 2026-11-11: the Bike routes' edges carry their kinds, direction and timing

Phase 7b ([`phase-7b.md`](../phase-7b.md) § 2) takes the network to format version 3: each edge says which
kinds of Bike route it is on, Touring and MTB apart, its surface class, a rider direction per kind, and its
riding seconds each way by ADR-0011's rule, computed at build time as the walking seconds are. The surface
class cuts only a Bike route's edges, so the Trails' edges, and the Hiking network, stay as phase 7a built
them.
