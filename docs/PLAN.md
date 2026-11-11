# Roadmap

Sequence and status only. The reasoning behind a decision lives in [`docs/adr/`](adr/); the words
live in [`CONTEXT.md`](../CONTEXT.md); commands, layout and house rules live in
[`CLAUDE.md`](../CLAUDE.md).

**This file is the record, not the worklist.** What is still open — the boxes to tick, with the
device state and the commands to read it — lives in [`DOD.md`](DOD.md), which stays short enough to
open every session. Read the phase you are in here; read `DOD.md` to know what to do next.

**A phase being planned or built gets its own file** — `phase-N.md` beside this one — so working on
it costs the phase rather than the whole history. This file stays deliberately thin: one paragraph
per phase, no task lists. The detail is written when the phase opens, not now. The app this template
was extracted from and the first app built from it settled on exactly this split independently.
[`phase-template.md`](phase-template.md) is the shape a phase file settled into; copy it on the day.

## Status

- [x] **Phase 0** — Skeleton: the template's reminders and Items gone, the ochre palette, one map screen
- [x] **Phase P** — The pipeline: release-please releases reach internal testing by themselves
- [x] **Phase 1** — The data reaches the phone: one Region built, drawn offline and delivered as a pack; Play's reading (E) moved to phase 2b
- [x] **Phase 2** — The map: the Tatry drawn as the signposts mark them, "you are here"
- [x] **Phase 2b** — Every Region: the list, the picker and "Download all", across the border, delivered by Play
- [x] **Phase 2c** — Every Region arrives with the app: fast-follow packs, no picker
- [x] **Phase 3** — Safe for a stranger: the Safety screen ← **the door: closed testing opens once its list is done**
- [x] **Phase 4** — Elevation: contours and hillshading, from the three national lidar models
- [x] **Phase 5** — Route planning, and search by name
- [ ] **Phase 6** — The hike log: record a hike, its photos and a note, look back, back it up, export GPX
- [ ] **Phase 6b** — Walking a Route: following it on the way, and times from the user's own pace
- [x] **Phase 6c** — Named Trails: a whole Trail highlighted, the rest of the map fainter
- [x] **Phase 7a** — Cycling on the map: Bike routes, the Hiking/Cycling switch, search, the mark
- [ ] **Phase 7b** — Planning a ride: along Bike routes, one-way lines, riding times
- [ ] **Phase 7c** — Recording Rides: Rides in the log, My bikes, a Pace per Bike
- [ ] **Phase 7d** — Following a ride: the screen kept on, voice directions
- [ ] **Phase N** — Ship shape: screenshots, production access, **1.0**

---

## Rules that cut across every phase

### 1. Decide where the door is, early

**The door** is the first build a stranger installs — closed testing, for a new personal Play
account. Everything that reaches a tester's phone is harder to change afterwards: a stored key, a
permission, a `minSdk`, the `applicationId`. So name the phase the door sits after, and write down what
it freezes. The closed-test window is calendar time and other people's replies, so later phases can
run while it waits.

**Do not plan a decision on the testers' answers.** The first app built from this template left two
phases' open questions — defaults, whether a behaviour was too clever — to be asked of its closed-test
testers. The survey that did go out asked none of them, and both closed *unanswered, on the values
that shipped*. What carried
the decisions was the device readings. Decide on a reading, ship a default, and let the mail hand-off
(`ui/support/`, live from the door) tell you if it is wrong.

### 2. When the two halves of a phase cost wildly different amounts, they are two phases

The first app built from this template split every one of its middle phases along cost or along
kind, because in each case a bundle of a cheap item with an expensive one — or a feature with the
safety work it depends on — hid which half was blocking. However neatly one sentence describes both, two costs are two phases.

### 3. A phase proves itself on a device, not in a test

Every phase ends with readings taken off a device and written into its `phase-N.md`, with the
command that produced them. A green test run says the code does what the test asked; the phone says
what the ROM allowed. Vendor ROMs are where those two part company.

**An emulator reading counts wherever an emulator can show what the reading needs**, even where a
phase document names the test phone: record it as read on the emulator. The phones stay for what no
emulator can show — MIUI and HyperOS killing background work and the autostart grant, a real GPS fix,
frame times on a real GPU (phase 4's gate), and an install from Play. The owner's rule since
2026-11-10.

**But the rule has a second clause, and leaving it out is how a roadmap arrives at no tests at all.**
Most of the risk in an app this size is device behaviour, and none of that is reachable by a unit
test — but the pure functions that *compute or bound a safety value* are the exception, and each of
them fails in exactly the direction the app exists to prevent. So: device behaviour is proven by
measurement; a safety value is proven by test, and **the phase that introduces one introduces its
test**. For the first app built from this template that came to five tests across the whole roadmap
— a window that crosses midnight, two deadlines resolved against a clock, a ramp whose bottom end
turns the backlight off over a live touchscreen, the width of a window that swallows touches, and a
restored intent that had to be refused. Not a testing culture. Five tests standing in front of the
properties `CLAUDE.md` calls load-bearing.

⚠ **Reconcile the list when the roadmap closes, and expect the count to be wrong.** That app planned
four and shipped five: the fifth arrived because a device reading found a fault no test had been
written for. That is the rule working, not an exception to it — the top of a ramp is not visible on a
device until it strands somebody.

### 4. Ask for nothing before the feature that needs it

An app of this shape ends up asking the user for three or four things, and most of them are hand-offs
to settings screens whose result cannot be read back reliably — `appops` has been caught lying about
`SYSTEM_ALERT_WINDOW` on a real phone. So the sequence is a rule rather than a phase:

**Every ask waits for the moment the feature needing it is switched on. The phase that introduces an
ask owns its hand-off, its re-read, and what the user sees when the answer is no.**

Kozica's asks — the moment each is made and the phase that owns it — are this table, so a later
phase cannot quietly move one forward to "get it out of the way".

| Ask | Moment it is made | Phase that owns it |
| --- | --- | --- |
| Precise location | The first tap on "show me where I am" — never at launch. Fine and coarse are asked together, and from API 31 the user may grant approximate only: a third answer the map has to handle | 2 |
| Notifications | Starting a hike recording: its notification is the stop button | 6 |
| Background location | Never: a `location` foreground service started from the visible app keeps while-in-use access | 6 |
| Battery exemption, Xiaomi autostart | Never, unless a reading shows a recording dying without them | 6 |
| *(none for photos)* | The Photo Picker and the system camera's `TakePicture` intent need no permission | 6 |


**The third clause is the one that gets left out, and it is the one that generates support mail.**
These denials nearly all fail *silently*: a denied notification permission leaves a foreground
service running with no way to stop it, a denied autostart leaves reboot restore never firing, a
denied battery exemption leaves a schedule never firing. In each case the app knows and the user does
not. A re-read tells the app; **nothing tells the person holding the phone** unless a screen does.
`ui/common/Surfaces.kt`'s `WarningBanner` is the idiom for a state the app can re-read; a permanent
row that makes no claim is the idiom for one it cannot.

**Autostart is the case where even the re-read is out of reach.** It is not an appop and there is no
API for it, so no code of yours can tell a granted state from a denied one — only
`scripts/device-gate.py`, over `adb`, run by you. The clause survives it: the phase still owns the
hand-off and still owns saying what a denial costs, without claiming to know the answer. What changes
is only who can check.

**The same logic applies to side effects, not only to permissions.** A feature that makes one of the
phone's own controls go inert is a system control that appears broken, caused by you. The phase that
introduces the behaviour owns saying so.

---
---

## Phase 0 — Skeleton

Everything the template shipped for an app that is not this one goes: reminders and WorkManager, the
Items screens and the tab bar built around them. Room, photos and backup stay, because the hike log
needs all three. The app opens on one screen, the map's place, with Settings behind an icon, in
the chamois-ochre palette, in English and Polish. The mark stays a placeholder until its concept is
drawn. [`phase-0.md`](phase-0.md).

## Phase P — The pipeline

The repository, the upload key, the Play entry, the secrets and the service account are in place.
Version 0.1.0 reached internal testing by hand; 0.2.0 was the first release `publish-play.yml`
uploaded by itself, on 2026-10-20 ([`RELEASING.md`](RELEASING.md)).

## Phase 1 — The data reaches the phone

`scripts/build-map-data.py` builds one Region, the Tatry, from the pinned extract as a PMTiles
archive ([ADR-0007](adr/0007-osm-data-ships-per-mountain-range-as-play-asset-packs.md)), and a debug
build asks for it as an on-demand asset pack and draws it with MapLibre. The gate draws, because a
format cannot be judged apart from its renderer, and MapLibre is sound only if it runs with every
network permission stripped. What an update costs is measured locally, with bsdiff as a proxy; the
reading through a build Play delivers is checkpoint E, taken when Play work resumes and before any
release carries a pack; phase 2b took E over when phase 2 split. The phase closes on A–D. Of
ADR-0007's decisions for "the map phase", this one owns the file format. [`phase-1.md`](phase-1.md).

## Phase 2 — The map

The app opens on the map, in the release build too. It draws Trails in their marking colours and
symbols, two on one way side by side, Paths apart from them, Huts, Shelters, peaks, passes, springs,
viewpoints, the way in and out, the land, national-park boundaries and the state border, and the
user's position, accuracy and heading. The OSM credit and the data date sit on the map. It is built
and read against the Tatry, and owns ADR-0007's filter. It also takes the palette reading phase 0
could not ([`phase-0.md`](phase-0.md) § 3). Its gate is phase 1's deferred phone readings.
[`phase-2.md`](phase-2.md).

## Phase 2b — Every Region

Every Region built and downloadable from a picker, with "Download all". It owns ADR-0007's Region
list and every Region's polygon, `tatry.poly` included. Regions cross into Slovakia and Czechia
where the ranges do, which brings those two extracts into the build at the same moment as Poland's.
It brings the asset-delivery library into the release build: the Console's foreground-service
declaration, the data build in CI and the ODbL offer. That is phase 1's
checkpoint E, which this phase takes over, so phase 1 closes on its phone readings alone. Split
from phase 2 when that phase opened, because the map can be built locally and this half waits on
Play work. [`phase-2b.md`](phase-2b.md). Its picker is undone by phase 2c.

## Phase 2c — Every Region arrives with the app

Every Region becomes a fast-follow pack, which Play fetches right after the install, and the
Regions screen and the first launch's choice are deleted: the user picks nothing. Decided with the
owner on 2026-10-22, while phase 4 was planned, as shipping everything rather than adding relief to
a per-Region choice. Split from phase 4 by rule 2: one line of build and some deleting, against a new
data pipeline. [`phase-2c.md`](phase-2c.md).

## Phase 3 — Safe for a stranger

What a stranger needs before installing: the hand-offs for live information, from a Safety button on
the map, for each country a Region reaches — Poland's 985, Ratunek, TOPR's and GOPR's avalanche
bulletins and IMGW's weather, and Slovakia's and Czechia's equivalents beside them, Czechia's for the
Karkonosze and the Jesioniki both ([ADR-0008](adr/0008-kozica-never-opens-a-network-connection.md)).
The listing, the privacy policy and the App content answers were written in phase P, and the support
address chosen on 2026-10-20 (`DOD.md`). Closed testing opens when this phase closes **and** the
items [`phase-3.md`](phase-3.md) § *The door to closed testing* names are done; the next three
phases are built while it runs.

## Phase 4 — Elevation

Contours and hillshading from a DEM, through a second pipeline with its own source, licence and
attribution. Route planning's walking times come from the same data. The DEM is built by us from
Poland's, Slovakia's and Czechia's lidar terrain models, once rather than per release, and ships as
one pack for every Region ([ADR-0009](adr/0009-relief-is-built-by-us-from-the-three-national-lidar-models.md),
[`phase-4.md`](phase-4.md)).

## Phase 5 — Route planning

The user plans a Route over Trails, and only Trails, from a start through any via points to an end,
and sees its length, ascent, walking time and elevation profile, entirely on the phone; a Route can
be saved, and saved Routes are the first data of the user's own in Room. Search by name arrives
here, because picking a start and an end needs it. A second area of the app arrives with it, so this
is the phase that brings a tab bar: Map and Routes. The network and the name index are our own,
built with the map data and shipped in the app
([ADR-0010](adr/0010-routes-are-planned-over-our-own-trail-network.md), [`phase-5.md`](phase-5.md)).

## Phase 6 — The hike log

Record a hike — the track, photos and notes — and look back at it, back it up and export it as GPX.
It brings the `location` foreground service, with `FOREGROUND_SERVICE_LOCATION` from API 34 and the
Console's foreground-service declaration; the notifications ask; and photos, which Auto Backup cannot
carry, so the manual Backup returns to Settings, in the shape binky's backup grew into. Planned with
the owner on 2026-10-26 in [`phase-6.md`](phase-6.md), which split off 6b and 6c by rule 2.

## Phase 6b — Walking a Route

Following a Route while walking, and walking times from the user's own pace. Moved here from phase 5
with the owner on 2026-10-25, and out of phase 6 on 2026-10-26: a pace is learned from recorded
hikes, so it comes after them. Planned with the owner on 2026-10-30 in [`phase-6b.md`](phase-6b.md):
following is on screen only, with no service of its own, and the pace is one factor from the Hikes,
never under 0.75× the signposts'.

## Phase 6c — Named Trails

Named Trails, with a whole Trail highlighted and the rest of the map fainter. It needs each Trail's
name or relation id in the tiles, which `KozicaProfile` does not write today: a map-data change, a
rebuild and every Region's pack delivered again, needing nothing from 6 or 6b, so it can run beside
either. Moved here from phase 5 with the owner on 2026-10-25. Planned with the owner on 2026-10-27 in
[`phase-6c.md`](phase-6c.md): tap or search a Trail, see it whole, and make it a Route or a GPX file.

## Phase 7a — Cycling on the map

The Regions' signed cycling routes in the tiles, Touring and MTB, drawn in their marking colours with
bicycle symbols along the line, and one switch, Hiking or Cycling, that the map, search and later
planning and recording follow, the other kind faded; a sub-filter for All, Touring or MTB; a
Bike route tapped and seen whole, with its MTB difficulty; the mark with a bike drawn into its rock,
read at launcher size (44 dp) first. A Bike route with no marking colour, as the Czech KČT numbered
routes and any relation without `colour`, gets a style decided in this phase, and CLAUDE.md's colour
exceptions gain the Bike routes' colours when it builds them. Planned with the owner on 2026-11-01
for 1.0.0, in four phases built alongside everything else
([ADR-0011](adr/0011-cycling-follows-marked-bike-routes-and-its-times-are-our-own-estimate.md),
[`cycling-research.md`](cycling-research.md)). Planned in full on 2026-11-01 in [`phase-7a.md`](phase-7a.md): the
Bike routes' edges, membership and direction moved here from 7b, for the sheet, and the switch and
the new mark stay developer-only until 7c.

## Phase 7b — Planning a ride

A Route in Cycling, along Bike routes only and the sub-filter's kind, honouring one-way MTB lines
by the direction 7a reads into the network. It shows distance and ascent first and an estimated time from our
own rule, checked against published riding times for 10–15 of our Bike routes before release.
Planned with the owner on 2026-11-11 in [`phase-7b.md`](phase-7b.md): 14 Bike routes, the rule tuned on
7 and judged on the other 7, with 7 in reserve, and no time at all if it fails twice; the flat speed
is the way's surface class; a saved Route keeps its Mode and sub-filter, and a ride can be followed.

## Phase 7c — Recording Rides

A Ride recorded as a Hike is and kept in the same log, shown as a Ride and changeable afterwards;
"My bikes", a name and an optional photo each; a Pace per Bike for Touring and MTB, learned from its
Rides. The log's tab name once it holds Rides is open, decided when this phase opens.

## Phase 7d — Following a ride

Following in Cycling with the screen kept on in a look for a handlebar mount, and spoken junction
directions through the phone's own speech engine in an on-device voice, checked to speak with no
network connection, with experimental sharp-bend warnings off by default and the voice able to be
turned off. Hiking stays silent.

## Phase N — Ship shape

Screenshots, the production-access application and **1.0**: everything above, in the public's hands.

## Not in this plan

- **A network connection**, ever: no online tiles, weather, sync, accounts or crash reporting of our
  own ([ADR-0008](adr/0008-kozica-never-opens-a-network-connection.md)). Live information is a
  hand-off.
- **Ads, paid features and sign-in.** Kozica is free. An optional tip that unlocks nothing may come
  after 1.0, through Play Billing, only once a purchase works on a phone with Billing's telemetry
  stripped as ADR-0008 requires.
- **Data beyond Slovakia's and Czechia's borders** in 1.0. Regions cross into those two where the
  ranges do (ADR-0007's amendment of 2026-10-20); Ukraine's and Germany's sides stay out.
- **GPX import and an "avoid difficult Trails" setting.** An imported track can leave the Trails,
  which a Route cannot, and the planner's warning already names a hard stretch
  ([`phase-5.md`](phase-5.md)). Reopened only if testers ask.
- **Reminders and scheduled work.** Nothing in the plan schedules anything of its own. WorkManager
  is in the build for Play Asset Delivery's extraction alone (ADR-0003's amendment of 2026-10-20).
