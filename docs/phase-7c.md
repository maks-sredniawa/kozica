# Phase 7c — Rides are recorded, kept beside the Hikes, and teach each Bike its Pace

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
relies on [ADR-0011](adr/0011-cycling-follows-marked-bike-routes-and-its-times-are-our-own-estimate.md)
(a recording is what the Mode says, a Pace per Bike for Touring and one for MTB, a Bike's photo a media
kind of its own) and amends it once: how a Ride teaches a Pace, below. It relies on ADR-0001 (schema 5,
the first migration since 7b's), ADR-0002 (a Bike's photo goes through `MediaFiles`), ADR-0005 (Backup
carries the Bikes and their photos) and ADR-0008 (nothing leaves the phone). [`CONTEXT.md`](../CONTEXT.md)
is owed one term, **Outing**, a Hike or a Ride and the tab that holds them; the Hike row takes its own
Polish word, *wycieczka piesza*, and the Ride row loses "the tab's name is open". Planned with the owner
on 2026-11-17.

**What closing this phase means.** Today a release build is Hiking alone: the switch, the chips and the
mark with wheels are developer-only, and a developer build in Cycling records a Hike. After this phase
every build has the switch. Record in Cycling records a Ride on the current Bike; the Hikes tab is
Outings and holds both; "My bikes" in Settings keeps a name and a photo per Bike; and each Bike learns a
Touring and an MTB Pace from its Rides, which the Route sheet's riding time follows. The launcher icon
is the chamois on the bike. The difference a device can be asked about: on the test phone, three Rides
replayed on a Bike named Gravel along Touring routes at 0.8× the rule's time turn the Route sheet's
"≈ 2 h 05 min" for a Touring Route into "≈ 1 h 40 min", with "Your pace on Gravel: 0.8× the rule, from
3 rides" beneath it, and a second Bike with no Rides shows the rule's time again.

**`PLAN.md`'s paragraph holds**, with the tab's name now decided (Outings, Polish *Wycieczki* as
before) and the switch and the new mark named as this phase's, as 7a promised.

---

## 0. What this phase inherits

- **The recorder is phase 6's**, a location foreground service taking a fix at most every 5 s and every
  5 m, built and gated for walking. At 25 km/h that is a fix every 35 m, which cuts corners on
  singletrack and is what § 1 asks about.
- **A Hike is a row in `hikes`** with its Track encoded at Stop, its photos in `hike_photos`; schema 4
  since 7b. Phase 6's R2, the walk in a pocket, is still owed; a Ride rides on the same service and
  inherits whatever that walk finds.
- **The walking Pace** (6b § 2) is a median of ratios against the signposts' rule, a `Flow` stored
  nowhere. The Ride Pace is the same shape against the riding rule.
- **The riding rule** (7b § 2) times an edge from its surface class and `mtb:scale` bytes:
  `ridingStretch()`. The network holds both per edge, and `Router.nearest()` finds the nearest Bike
  route edge in Cycling.
- **The switch's seam** (7a § 3): `ModeSwitch.kt` in `app/src/debug/`, a no-op twin in
  `app/src/release/`; `modeOf()` pins release builds to Hiking and `otherModeMatches()` keeps a release
  search off the Bike routes. 7a also left `CYCLING_PARTS` in `art/mark.py` with the debug launcher
  icon, the draw order favouring Hiking, and the Mode row crowding a 360 dp phone at font scale 1.3.
- **A ride saved in a developer build opens as a walk in a release one** (7b, *Known gaps*); it is a
  ride again from E.

## What is in, and what is deliberately not

**In:** § 1's gate; schema 5 with the Bikes, a recording's Mode and its Bike; "My bikes"; Record in
Cycling recording a Ride on the current Bike, changeable on the recording bar and afterwards; the
Outings tab; a Hike made a Ride and back; a Pace per Bike and kind from its Rides, on the Route sheet and
in Following; the switch, the filter menu, the shorter placeholder and the new mark reaching every build;
the draw order following the Mode; the listing's descriptions.

**Not in, and each with a reason rather than a phase:**

- **A Ride's own statistics** (average speed, top speed, a speed profile). The log keeps what it keeps
  for a Hike: distance, moving time, ascent, the profile by height. Speed is moving time over distance,
  already on the row.
- **Choosing the kind a Ride teaches.** Decided with the owner: the Bike routes it rode decide (§ 5).
  A chip-at-Record rule teaches the wrong Pace whenever the chip was left on the wrong kind.
- **A Bike's anything beyond a name and a photo** (wheel size, e-bike flag, weight). ADR-0011: an
  e-bike is another Bike, and its Pace says the rest.
- **The voice and the screen kept on.** 7d's.
- **Linking a Ride to the Route it followed.** As 6b keeps Hikes and Following apart.

**The tempting one: recording Rides at one fix a second from the start.** It looks free and safe. It
costs battery on every Ride for a precision nobody has shown is needed, and it skips the one question
§ 1 can answer on the PC. The gate decides it.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The gate** — does a Ride sampled as the recorder samples it match its Bike routes and keep its length? | `chore:` the matcher's replay; the verdict is a reading | — |
| **B** | Schema 5, the `Outing` names, My bikes, all behind the Cycling seam | `refactor:`, `feat:` | — |
| **C** | Rides recorded, the Outings tab, a Hike made a Ride and back, behind the seam | `feat:` | A, B |
| **D** | The Pace per Bike, on the Route sheet and in Following, behind the seam | `feat:` | A, C |
| **E** | **Cycling for everyone**: the switch, the filter menu, the placeholder, the Record label, the mark, the draw order, the listing | `feat:` | B, C, D |

**A comes first** because its verdict sets the Ride's fix interval, which C's recorder uses and D's
matching depends on. **B does not wait for A**: the schema and My bikes do not care how often a fix is
taken. **Everything from B to D sits behind the seam**, so a release user meets no Bike, no Ride and no
"Make it a Ride" until E opens Cycling in one release; a ride a release user could record but not plan,
or a Bike they could add but never ride, is the half-open state 7a refused. `modeOf()` and
`otherModeMatches()` pin only the Mode, and most of B–D's surfaces do not depend on it, so B adds
**`CYCLING_OPEN`** to `ui/map/ModeSwitch.kt`, `true` in the debug half and `false` in the release one,
and every B–D surface outside the Mode reads it: the "My bikes" Settings row, the tab's "Outings" label
(a release build keeps "Hikes" until E) and its empty line, a row's walker or bike icon and its Bike,
the sheet's "Ride"/"Hike" line, "Make it a Ride", Record's "Record" and Mode icon (a release build keeps
"Record hike" and the record icon until E), the recording bar's Mode icon, the Bike chip on the
recording bar and the Route sheet, and the Ride Pace line. The GPX `<type>` and the Polish Pace lines'
Hikes on foot are not surfaces of Cycling and reach every build from C. B's first `feat:` lands with it,
so no surface reaches a release build early. E deletes the seam's release halves, `CYCLING_OPEN` with
them.

---

## 1. The gate: does a Ride sampled at 5 s match its Bike routes?

### The question, in the form that can be measured

Every Bike route at the current pin, ridden each way it may be ridden, sampled as the recorder would
sample it: a fix every 5 s at 15, 25 and 35 km/h along its line, dropped where it moved under 5 m, each
fix moved by GPS noise (a 2-D normal with σ 5 m, correlated over 30 s). Two numbers per speed, over all
routes: **the share of the Track's length the matcher (§ 5) puts on an edge of the right Bike route**,
and **the Track's length against the line's**.

### The apparatus

The matcher is a pure function in `data/hikes/` (§ 5), so the replay runs it on the JVM:
`RideMatchReplay`, a test in `app/src/test/` skipped unless `-PrideGate` names a base-assets directory
(`trails.network` and `trails.edges` in walks format 2). `scripts/ride-gate.py <dir>` exports each Bike
route's kind and one-way flag from its `places.sqlite` to `<dir>/../ride-gate/bike-routes.tsv` and runs
the test, which writes one row per route, direction and speed to `ride-gate.tsv` beside it and the
totals to its log.
`app/build.gradle.kts` passes `-PrideGate` to the test JVM as the `rideGate` system property, and with
it set marks the test task never up to date, since the replay reads a file Gradle cannot see. The
noise is seeded. The same run at 1 s is the comparison verdict 2 needs.

### The verdicts, decided now rather than argued about later

1. **At all three speeds, 95 % or more of the length matched to the right route and the length within
   3 %** → Rides keep the walker's 5 s and 5 m.
2. **Short of either at any speed, and met at 1 s** → a Ride records at 1 s and 2 m, a Hike stays at
   5 s, and *Known gaps* records the battery cost, read on the owner's phone on the first real Ride
   after release (a post-merge reading).
3. **Short of either even at 1 s** → the matcher, not the interval, is wrong: § 5's matching is
   rethought before C, and the owner sees the rows first.

### The verdict, taken 2026-11-17

**Verdict 1: Rides keep the walker's 5 s and 5 m.** Over all 1,443 Bike routes, 2,646 rides counting each
way, at 5 s: 99.73 %, 99.76 % and 99.75 % of the Track's length matched to the right route at 15, 25 and 35 km/h,
and the length off by +0.37 %, −0.58 % and −1.14 %. Among the 1,683 rides of 5 km or more the worst
twentieth is still 99.0 % matched, and the 95th percentile of the length error is 1.0 %, 1.8 % and 2.9 %.
No ride of 5 km or more matched under 89 %; the misses below it are lines under 2.6 km, which a Ride never
counts on alone (§ 5's 5 km floor). R1 has the run.

One thing neither verdict anticipated: **1 s reads a Ride longer, not truer.** At 15 km/h a fix every
second measures the GPS noise as distance, +4.6 % against 5 s's +0.4 %; only at 35 km/h does 1 s do
better. The interval that keeps corners at speed costs more at the speeds most Rides go.

## 2. Schema 5

- **`bikes`**: `id` (UUID text), `name`, `photoPath` and `thumbnailPath` (both null without a photo),
  `addedAt`. A name is unique ignoring case and surrounding spaces, enforced by the repository against
  the folded name, since SQLite's `NOCASE` folds ASCII alone and Polish names do not.
- **`hikes.mode`**, `HIKING` or `CYCLING` by name (the house rule on enums), `HIKING` for every existing
  row. A Ride is an Outing whose Mode is Cycling; the Mode enum is the one the map, planning and a saved
  Route already use, so there is no second word for the same choice.
- **`hikes.bikeId`**, nullable, a foreign key to `bikes` with `ON DELETE SET NULL` and an index. Null on a
  Ride means "no Bike", which is every Ride while no Bike exists and every Ride of a deleted Bike.
- **The current Bike** is an `AppPreferences` key, not a column: the Bike Record starts on and the Route
  sheet times by. A deleted current Bike leaves it unset, and a Bike added while none is current
  becomes it, as the last Bike used would be when none has been.
- **`MediaKind.Bike`**: directory `bikes/`, a 512² centre crop at q85 as a thumbnail is; the photo is
  shown no larger than a sheet. One file, so `photoPath` and `thumbnailPath` name the same one, and both
  columns stay so a later larger view needs no migration. Backup's Records scope carries it (it is as
  small as a thumbnail); Everything carries it by being every kind. Android Auto Backup builds its own
  file set from an explicit list of kinds (`autoBackupFileSet()`, `data/backup/AutoBackup.kt`), so
  `bikes/` joins `MediaKind.Thumbnail` there under the same budget, or a new phone's restore shows every
  Bike as the placeholder. The Bikes' photos are admitted first, since they are few and each is a Bike's
  only picture; the thumbnails, newest first, fill what is left.

`MIGRATION_4_5` adds the table and the two columns. The house rule's four: registered in
`APP_MIGRATIONS`, `5.json` committed, `SchemaGateTest`'s assertion, and a migration test reading back a
Hike with photos and a saved ride. R4 is the upgrade watched on a device.

**The names.** The table stays `hikes`, since renaming a table buys nothing a user sees. The Kotlin types
holding both kinds become Outing's: `OutingEntity`, `OutingDao`, `OutingRepository`, `ui/outings/`, in
one `refactor:` commit before anything else in B, so the diff that changes behaviour is not also the
diff that renames. Types that are a Hike's alone, as the walking Pace, keep their names.

## 3. My bikes

A "My bikes" row in Settings under *Your data*, above Backup, opens the screen; the Bike chooser (§ 4)
ends with a row to it too. With no Bike the Settings row is the only trace of Bikes anywhere.

The screen: a list, each row a 56 dp rounded thumbnail (the bike icon on `surfaceVariant` without a
photo, which is also the missing-file placeholder), the name, and "12 rides" beneath it; a ⋮ menu with
Rename, Add photo or Change photo, Remove photo, and Delete. "Add bike" at the bottom opens the name
dialog. Its "Add" (and Rename's confirm) stays disabled for an empty name, as the Route name dialog's
does; a taken name shows "That name is taken." as the field's `supportingText` with `isError`, and the
field carries `Modifier.semantics { error(reason) }`, so TalkBack reads the reason rather than the
press seeming to do nothing. A photo comes from the Photo Picker, as a Hike's does, through
`MediaFiles`.

**Remove photo** asks first, as a Hike's "Delete photo" does, since the file goes for good.

**Delete** asks first and says what happens to the Rides: they stay, with no Bike, and teach the no-Bike
Pace; the Bike's own Pace goes with it.

Copy: "My bikes", "Add bike", "Bike name", "That name is taken.", "Rename", "Add photo", "Change photo",
"Remove photo", "Remove the photo of %1$s? This can't be undone.", "Remove", "Delete", "Delete %1$s?
Its %2$d rides stay, with no bike." (plural), "Delete %1$s?", "%d rides" (plural), "No bikes yet. Add
one to keep a pace per bike.", "Options for %1$s" (the ⋮ button's name, per Bike, as `routes_more` is
for a saved Route's).

## 4. Recording a Ride

**Record follows the Mode.** The button reads "Record" / "Nagraj" in both, its icon the walker or the
bike, and its accessible name says which: "Record a hike" or "Record a ride". In Cycling it records a
Ride, at § 1's interval, on the current Bike, with no question asked.

**The recording bar** carries the same icon in its title line, and in a Ride, once a Bike exists, a third
line: a chip with the bike icon, the Bike's name (one line, ellipsised) and a drop-down arrow, its
accessible name "Bike: Trek", keeping the visible label. A tap opens
a bottom sheet, one 56 dp row per Bike (thumbnail, name, radio button), then "No bike" and "My bikes".
Picking one changes the Ride's Bike and the current Bike. The Route sheet in Cycling shows the same chip
(§ 6), bound to the same current Bike.

Everything else is phase 6's: pause, auto-resume, Stop, the asks, the notification, which reads "Recording
a ride" in Cycling.

Copy: "Record", "Record a hike", "Record a ride", "Recording a ride", "No bike", "Bike", "Bike: %1$s".

## 5. A Ride's Pace

**Matching.** Each pair of neighbouring fixes in a stretch is one piece. Its midpoint goes to
`Router.nearest()` in Cycling under All; within `RIDE_MATCH_METRES` (30 m) the piece takes that edge's
surface class, `mtb` and `mtb_up` bytes and its kinds, `SET_TOURING`, `SET_MTB` or both. Beyond it, or
with no network, the piece is **off the Bike routes** and is timed as the planner times the road to the
start (7b § 3): its length at the rough class's flat speed, `Travel.offNetworkMetresPerSecond`. The
Pace is a ratio against the rule it multiplies on the Route sheet, so both sides time that stretch
alike.

**The rule's time for a Ride** is `ridingStretch()` over its matched pieces, each with its height change
from the grid as a Hike's is (`HikeHeights`, the 5 m dead band), and the off-route speed over the rest,
each stretch of the Track timed alone so pauses are not. The ratio is the moving seconds over it.

**The kind a Ride teaches** is MTB when more than half of its matched length lies on edges in an MTB
route, else Touring. A piece on both kinds counts for neither side of that comparison, so a Touring line
that shares a forest road with an MTB route stays Touring. Nothing for the user to set (decided with the
owner).

**A Ride counts** when it is at least 5 km long, has at least 20 minutes of moving time, every point has
the grid's height, and its ratio lies between 0.4 and 2.5, which leaves out a car and a lift. **The
Pace** for a Bike and a kind is the median ratio of its 10 newest counting Rides of that kind, used once 3
count, and **never below 0.5**: an e-bike can ride the rule in half its time, and the walking floor of
0.75 would hold it back. Rides with no Bike make one more Pace pair of their own, the no-Bike one.

It is recomputed as the walking Pace is, a `Flow` over the ended Rides and the relief, cached per Ride by
id and length, and nothing stored: deleting, trimming, changing a Ride's Bike or making it a Hike changes
it at once.

## 6. Where the Ride Pace shows

Every riding time is the rule's at the current Bike's Pace for the kind the Route is planned under: the
chip's, and under All the majority kind of the Route's own edges, by § 5's rule. Then "≈", rounded as 7b
rounds, "< 5 min" under 5. That is the Route sheet's total and each point's time, the Routes list's row
for a ride, and Following's sheet.

Under the totals, in Cycling, one line, as the walk's Pace line, with the Bike chip (§ 4) beside it once a
Bike exists:

- "Your pace on Gravel: 0.8× the rule, from 3 rides"
- No Bike: "Your pace: 0.8× the rule, from 3 rides"
- Without one: "Kozica's estimate. Your own pace starts after 3 rides of 5 km and 20 minutes or more."
- Floored: "Your pace on Gravel: 0.5× the rule, the fastest Kozica shows, from 6 rides"

The walking Pace never touches a ride and the Ride Pace never touches a walk.

## 7. Outings

**The tab** is "Outings" / "Wycieczki"; `CONTEXT.md`'s Outing.

**A row**, once the log holds at least one Ride, starts with a 24 dp walker or bike icon in
`onSurfaceVariant`, its content description this section's "Hike" or "Ride", read with the row's merged
text, so a screen reader tells the two apart; a Ride's Bike is the last item on its facts line ("12 Oct
· 34 km · 2 h 10 · ↑ 620 m · Gravel"). A log with no Ride looks as it does today.

**A Ride's sheet** has a "Ride" line under its title with the Bike chip, which changes that Ride's Bike
alone, not the current one. A Hike shows no such line unless the log holds Rides, and then "Hike".

**"Make it a Ride" / "Make it a Hike"** is a button in the sheet's action row after Rename, no confirm, as
it is undone by the same button. A Hike made a Ride gets the current Bike; a Ride made a Hike keeps its
`bikeId`, so making it a Ride again restores it. That kept `bikeId` is the Bike's only on a Ride: a
Hike holding one never counts among the Bike's rides on My bikes and never teaches its Pace, as both
read Rides alone. The action row wraps (`FlowRow`): five buttons do not fit one line in Polish.

GPX export carries `<type>` `hiking` or `cycling`, as Strava and Komoot read it.

Copy: "Outings", "Ride", "Hike", "Make it a Ride", "Make it a Hike", "No outings yet. Record one from
the map." (`outings_title` and `outings_empty`, beside `hikes_title` and `hikes_empty` while a release
build still shows those; E deletes the two, with `record_hike`). In Polish a Hike beside a Ride is
*wycieczka piesza* (`CONTEXT.md`), so the sheet's "Hike" line and the walking Pace line's count
(`pace_*`, `pace_none`, today "z %d wycieczki") are translated again to name Hikes on foot, and do not
read as counting every Outing.

## 8. Cycling for everyone

**The seam's release halves go.** `ModeSwitch.kt`, `ic_bike` and the switch's strings move to `main/`;
`modeOf()` and `otherModeMatches()` pass through in every build, `CYCLING_OPEN` (§ Checkpoints) goes
with every read of it, and their release twins are deleted. The switch's strings are
`translatable="false"` in `app/src/debug/`, which the translation gate skips, so they lose it on the move
and get Polish (*pieszo* / *rowerem* per `CONTEXT.md`); the copy below lists them. A release build
opens in the stored Mode, Hiking on a first launch (ADR-0011).

**The Mode row** (7a's known gap, decided with the owner):

- The search placeholder becomes "Search" / "Szukaj", one line, ellipsised. "Search places and Trails"
  was wrong in Cycling anyway.
- The three chips become one chip showing the current filter, "All ▾", opening a menu of All,
  Touring and MTB. It is a menu's trigger, not a toggle, so it is an `AssistChip` with
  `Modifier.semantics { role = Role.DropdownList }` and its expanded or collapsed state, the `▾` a
  decorative trailing icon rather than a character in the label, and its accessible name "Bike routes:
  All" (or Touring, MTB), keeping the visible label. The row then never wraps in either language, and
  `MODE_ROW_HEIGHT` stays true.
- The row is hidden while Following, where it was only disabled.

**The draw order follows the Mode.** In Cycling the Bike routes' lines, chevrons and plates lie above the
Trails' and their symbols, as the Trails' lie above in Hiking; `MapStyle.kt` moves the two groups when
the Mode changes. 7a § 4 left it to this phase.

**The mark.** `PARTS` becomes the mark with the tyres (`CYCLING_PARTS`), `CYCLING_*` folds into it, the
debug launcher copies in `app/src/debug/res/` go, and `make-launcher-icon.py` writes `main/` from it, as
do `ic_notification`, `play-icon-512.png` and the feature graphic. Rendered at 44 dp, 24 dp and 512 px
for the owner before it merges.

Copy, moved from `app/src/debug/` and now translated: `mode_hiking`, `mode_cycling`, `filter_all`,
`filter_touring`, `filter_mtb`, `search_hint_trails`, `search_hint_bike_routes`. New: "Search", "Bike
routes: %1$s" (the menu chip's name). Deleted with their reads: `hikes_title`, `hikes_empty` and
`record_hike` (§ 7).

**Copy that says "hike" of what may be a Ride** is reworded here, since this is where a release user
first meets a Ride; until E only a developer build shows one. R3 found it on a Ride's Stop confirm
(`record_stop_body`), its sheet (`hike_delete_title`, `hike_delete_body_photos`, `hike_close`), and
the recording's other asks and channel (`record_precise_title`, `record_precise_body`,
`record_unfinished_title`, `record_channel`); Backup's lines that list "your hikes" go with them.
Each names the Outing it is about where it knows the Mode, or says "outing" where it does not.

**The listing.** `docs/store-listing.md`'s descriptions gain Cycling, the Bike routes and the Rides; the
Play icon and feature graphic change with the mark. The Console upload of the listing and graphics is
asked first (`DOD.md`'s Play rules).

---

## Tests

- **`RideMatchTest`**: a piece 20 m from an MTB-only edge takes its bytes; one 40 m from every edge is
  off the routes and timed at `Travel.offNetworkMetresPerSecond`, as the planner's join is; a piece on a
  both-kinds edge counts for neither side of the majority. Fails toward a Ride teaching the wrong kind.
- **`RidePaceTest`**: the counting rule at each boundary (4.99 km, 19 min 59 s, ratio 0.39 and 2.51), 3
  Rides needed, the 10 newest, the floor at 0.5 with `floored`, a Bike's two kinds kept apart, and a
  Hike keeping a `bikeId` teaching that Bike nothing. Fails toward a time promised faster than any Ride
  showed.
- **`MIGRATION_4_5`'s test** and **`SchemaGateTest`** per the house rule.
- **`BikeRepositoryTest`**: a name taken ignoring case and spaces, "Łukasz" against "łukasz"; delete
  leaves its Rides with a null Bike and clears the current Bike; a Hike keeping a `bikeId` is not in
  that Bike's ride count.
- **`BackupScopeTest`**: Records carries `bikes/`.
- **`AutoBackupTest`**: `autoBackupFileSet()` carries `bikes/` with the thumbnails, under the same
  budget.

## Documents this phase amends

- **ADR-0011**: a Ride teaches the kind most of its matched length rides, timed by § 5's matching; the
  counting rule and the 0.5 floor.
- **`CONTEXT.md`**: **Outing**; **Hike** takes *wycieczka piesza* and the Outings tab; **Ride** loses
  "the tab's name is open"; **Pace** names the 0.5 floor.
- **`PLAN.md`**: 7c's paragraph names this file and the tab's name.
- **`CLAUDE.md`**: the media house rule's directories gain `bikes/`, at B; the layout's `app/src/debug/`
  and `app/src/release/` lines lose the Mode switch, the bike icon and the launcher copies, at E.
- **`art/README.md`**: one mark again.
- **The privacy policy**: a Bike's name and photo are kept on the phone with the Outings and go into a
  backup, nowhere else. Data safety's answers do not change: nothing is collected.
- **`docs/store-listing.md`**: § 8.

## Known gaps, deliberately

- **A road between Bike routes is timed at the rough flat speed**, as the planner's join is. A paved
  road off the network rides faster than that, so its Ride reads fast; it is the same bias the Route
  sheet's join carries, and across many Rides the median absorbs it.
- **A Ride's kind is its majority.** A Ride half on each teaches one of them; the median of 10 keeps one
  such Ride from moving the Pace.
- **The no-Bike Pace and a Bike's are not merged.** Adding a first Bike after 20 Rides starts it at the
  rule's time until it has 3 Rides of its own, unless the old Rides are moved to it on their sheets.

## Readings

`PLAN.md` rule 3. The emulators unless named; a Ride is replayed through the debug build's position
override, as 6b's R3 replays a walk.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The gate (§ 1): matched share and length per speed at 5 s, and at 1 s | `python3 scripts/ride-gate.py <base-assets dir>` | **Verdict 1**, 2026-11-17, the PC: pin 4965's network rebuilt at `ce4df02` (walks format 2) into `map-data/pin-4965-net2`, seed 20261117, 20 min. Every 5 s: matched 99.73 / 99.76 / 99.75 %, length +0.37 / −0.58 / −1.14 % at 15 / 25 / 35 km/h; every 1 s: 99.66 / 99.68 / 99.68 %, +4.61 / +1.55 / +0.65 %. Rows in `map-data/ride-gate/ride-gate.tsv`. |
| R2 | My bikes: add, rename to a taken name refused, photo, delete with Rides, at 360 dp and font scale 1.3 in Polish | by hand on `pixel-api37` | 2026-11-17, emulator `pixel-api37` (API 37), headless, the debug build at `78ad3c3`, `wm density 480` on 1080 px for 360 dp, font scale 1.3, the app's language set to Polski in Settings. "Moje rowery" sits under *Twoje dane* above the backup row; with no Bike the screen reads "Nie masz jeszcze rowerów. Dodaj rower, aby mieć tempo dla każdego z nich." over "Dodaj rower", and the dialog's "Dodaj" is disabled while the name is empty. "Gravel" added; " gRAVEL " refused with "Ta nazwa jest już zajęta." in red under the field, gone at the next keystroke; "Szosa" added, renamed to "GRAVEL" and refused the same way, then renamed to "Szosa Kross"; "Gravel" renamed to its own name recased, "GrAvEl", accepted. A photo through the Photo Picker shows as the row's centre crop, and the ⋮ menu then reads Zmień nazwę, Zmień zdjęcie, Usuń zdjęcie, Usuń; Usuń zdjęcie brings back the bike icon and empties `files/bikes/`. Delete with no Rides: "Usunąć Szosa Kross?". Delete with Rides: B records none, so two `CYCLING` rows on GrAvEl were written into the pulled database and pushed back; the row read "2 wycieczki rowerowe", the confirm "Usunąć GrAvEl? 2 wycieczki rowerowe na nim zostaną, bez roweru.", and afterwards both rows stay with `bikeId` null, `bikes` and `files/bikes/` are empty and no current-Bike key is left in the preferences. Nothing on the new surfaces is clipped, overlapping or untranslated; "0 wycieczek rowerowych" wraps to a second line and the row grows to hold it. No crash. TalkBack's reading of the error not heard. Outside B's surfaces, Settings' Wygląd and Język chips break "Ciemny" and "Polski" mid-word at this size. Screencaps `r2/02-my-bikes-empty.png` to `r2/20-deleted-with-rides.png`. **Pass** |
| R3 | A replayed Ride on a Bike, the chip changed mid-Ride, Stop, the row and sheet in Outings, made a Hike and back | position override replay | 2026-11-23, emulator `pixel-api36` (API 36), headless, the debug build at `d0af1d1` `install -r` over a debug copy holding Gravel and two Hikes. The recorder reads `GPS_PROVIDER` itself, so the Ride was fed with `emu geo fix` near Kuźnice, eleven fixes 6 s and about 90 m apart, not through `PositionReceiver`. "Szosa" added on My bikes beside Gravel, the current Bike. Cycling: Record reads "Record" with the bike icon; Record: the bar reads "Recording a ride" with the bike icon and a Gravel chip, the notification "Recording a ride". After 445 m the chip opened its sheet, "Bike" over 56 dp rows Gravel (selected), Szosa, "No bike", then "My bikes"; Szosa picked, 467 m more, Stop. The tab reads "Outings"; the Ride's row has the bike icon ("Ride") and its facts end "· Szosa", both Hikes the walker ("Hike"). Its sheet has "Ride" and the Szosa chip under the title; the chip set to Gravel changed that Ride and left the current Bike Szosa in the preferences, and the next Record started on Szosa. "Make it a Hike" with no other Ride in the log: no icons on any row, no Bike on the facts, no kind line on the sheet, My bikes 0 rides each, as § 7 says of a log with no Ride; with a second Ride on Szosa recorded, the made Hike's row has the walker and its sheet "Hike", and Szosa counts 1 ride. "Make it a Ride" brings Szosa back, also with Gravel current, so the kept `bikeId` wins. Export GPX writes `<type>cycling</type>` in the `<trk>`, read from `cache/gpx/`. Polski, `wm density 480` for 360 dp, font scale 1.3: "Wycieczki", "Wycieczka rowerowa" / "Wycieczka piesza" on rows and sheets, "Nagrywanie wycieczki rowerowej", "Rower", "Bez roweru", "Moje rowery"; the action row wraps to two lines (Zmień nazwę, Zmień na pieszą / Przytnij, Eksportuj GPX, Usuń); with Szosa renamed to a 50-character name the sheet's chip reads "Szosa Kross Vento 4.…", the recording bar's "Szosa …", the Bike sheet's row ellipsises, and the facts line wraps the whole name. Nothing clipped or untranslated on 7c's surfaces. `-PreleaseShapedDebug` over it opens under R8 with no crash, but it is the debug variant, so `src/debug/`'s seam keeps Cycling open (the switch, "Wycieczki", icons, chips: what R6 reads). The release half was read on `assembleRelease`, fresh, with a records backup from the debug copy restored into it: "Hikes", "Record hike", no switch, no icons, no Bike on the facts, no chip, no kind line, the action row Rename, Trim, Export GPX, Delete. The plain debug build `install -r` back, the database pulled with its `-wal` and checkpointed on the PC: both Rides `CYCLING` with Szosa's `bikeId`, both Hikes `HIKING` with `bikeId` null. No crash. Outside 7c's scope: the Stop ask reads "The hike is saved…", the delete confirm "Delete hike?" and the sheet's close button "Close hike" on a Ride in English (Polish "Wycieczka", "Usunąć wycieczkę?" read right), all phase 6's copy that § 4 leaves as it is; Stop with no fix saves a 0 km Ride; the debug switch's "All", "Touring", "MTB" stay English in Polish; `wm density` changed while the map was open hung the app (main thread in MapLibre's `RenderThread.onWindowResize`, its GL thread in `eglSwapBuffers`), and a relaunch at the new density was fine. Screencaps `r3/02-cycling-record.png` to `r3/49-release-ride-sheet-expanded.png`, the export `r3/ride.gpx`. **Pass** |
| R4 | Schema 4 to 5 on a device: the previous release-shaped build with a Hike, photos and a saved ride, the new one over it | `./gradlew assembleDebug -PreleaseShapedDebug` both builds | 2026-11-17, emulator `pixel-api36` (API 36). `main` at `ce4df02` (schema 4) as `assembleDebug -PreleaseShapedDebug`, so `BuildConfig.DEBUG` is false and the migrations are registered with no destructive fallback, installed fresh (versionCode 576). `SeedReceiver`'s plain variant saved two Routes, Kuźnice – Morskie Oko and Kasprowy Wierch, both walks: the emulator has no network to plan a ride. Its `hikes` variant recorded two Hikes through the path a Stop takes, Kuźnice – Kasprowy Wierch 4.5 km and Wisła 3.1 km, and a photo was added to the first through the Photo Picker. B at `78ad3c3` the same way, `adb install -r` over it (576 → 579, first-install time unchanged), opened straight to the map past the launch gate with no refusal screen; Hikes lists both, the first's sheet shows its photo, and Routes lists both. B's plain debug build `install -r` over it, not opened, the database pulled with its `-wal` and checkpointed on the PC: `user_version` 5; `bikes` present and empty; both `hikes` rows `HIKING` with `bikeId` null, `index_hikes_bikeId` and the `ON DELETE SET NULL` key in place; `hike_photos` 1 row with its `photos/` and `thumbnails/` files on disk; `routes` 2, `route_points` 5. Screencaps `r4/03-hike-photo-v4.png`, `r4/05-7c-open.png`, `r4/07-7c-hike-photo.png`, `r4/08-7c-routes.png`. **Pass** |
| R5 | Three replayed Rides at 0.8× along Touring routes: the Route sheet's time and Pace line before and after, and a second Bike's | position override replay | *(owed)* |
| R6 | The release-shaped build in Cycling: the switch, "All ▾", "Szukaj", Record's icon, the draw order, at 360 dp and 1.3 in Polish | `-PreleaseShapedDebug`, `pixel-api37` with `-gpu host` | *(owed)* |
| R7 | The new mark at 44 dp, 24 dp and 512 px | `art/make-launcher-icon.py`, the owner's eye | *(owed)* |
