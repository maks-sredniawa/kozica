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
(a release build keeps "Hikes" until E), a row's walker or bike icon and its Bike, the sheet's
"Ride"/"Hike" line, "Make it a Ride", the Bike chip on the recording bar and the Route sheet, and the
Ride Pace line. B's first `feat:` lands with it, so no surface reaches a release build early. E deletes
the seam's release halves, `CYCLING_OPEN` with them.

---

## 1. The gate: does a Ride sampled at 5 s match its Bike routes?

### The question, in the form that can be measured

Every Bike route at the current pin, ridden each way it may be ridden, sampled as the recorder would
sample it: a fix every 5 s at 15, 25 and 35 km/h along its line, dropped where it moved under 5 m, each
fix moved by GPS noise (a 2-D normal with σ 5 m, correlated over 30 s). Two numbers per speed, over all
routes: **the share of the line's length the matcher (§ 5) puts on an edge of the right Bike route**,
and **the Track's length against the line's**.

### The apparatus

The matcher is a pure function in `data/hikes/` (§ 5), so the replay runs it on the JVM:
`RideMatchReplay`, a test in `app/src/test/` skipped unless `-PrideGate=<trails.network>` names a
network, writing one row per route and speed to `build/ride-gate.tsv` and the totals to its log.
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

### The verdict, taken *(date)*

*(Which one, the reading behind it, and anything it found that neither verdict anticipated.)*

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

**Delete** asks first and says what happens to the Rides: they stay, with no Bike, and teach the no-Bike
Pace; the Bike's own Pace goes with it.

Copy: "My bikes", "Add bike", "Bike name", "That name is taken.", "Rename", "Add photo", "Change photo",
"Remove photo", "Delete", "Delete %1$s? Its %2$d rides stay, with no bike." (plural), "Delete %1$s?",
"%d rides" (plural), "No bikes yet. Add one to keep a pace per bike.", "Options for %1$s" (the ⋮
button's name, per Bike, as `routes_more` is for a saved Route's).

## 4. Recording a Ride

**Record follows the Mode.** The button reads "Record" / "Nagraj" in both, its icon the walker or the
bike; in Cycling it records a Ride, at § 1's interval, on the current Bike, with no question asked.

**The recording bar** carries the same icon in its title line, and in a Ride, once a Bike exists, a third
line: a chip with the bike icon, the Bike's name (one line, ellipsised) and a drop-down arrow. A tap opens
a bottom sheet, one 56 dp row per Bike (thumbnail, name, radio button), then "No bike" and "My bikes".
Picking one changes the Ride's Bike and the current Bike. The Route sheet in Cycling shows the same chip
(§ 6), bound to the same current Bike.

Everything else is phase 6's: pause, auto-resume, Stop, the asks, the notification, which reads "Recording
a ride" in Cycling.

Copy: "Record", "Recording a ride", "No bike", "Bike".

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

Copy: "Outings", "Ride", "Hike", "Make it a Ride", "Make it a Hike", "No outings yet. Record one from the
map." (replacing the empty Hikes line in the same key's place). In Polish a Hike beside a Ride is
*wycieczka piesza* (`CONTEXT.md`), so the sheet's "Hike" line and the walking Pace line's count (`pace_*`,
`pace_none`, today "z %d wycieczki") are translated again to name Hikes on foot, and do not read as
counting every Outing.

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
routes: %1$s" (the menu chip's name).

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
| R1 | The gate (§ 1): matched share and length per speed at 5 s, and at 1 s | `./gradlew testDebugUnitTest --tests '*RideMatchReplay*' -PrideGate=<trails.network>` | *(owed)* |
| R2 | My bikes: add, rename to a taken name refused, photo, delete with Rides, at 360 dp and font scale 1.3 in Polish | by hand on `pixel-api37` | *(owed)* |
| R3 | A replayed Ride on a Bike, the chip changed mid-Ride, Stop, the row and sheet in Outings, made a Hike and back | position override replay | *(owed)* |
| R4 | Schema 4 to 5 on a device: the previous release-shaped build with a Hike, photos and a saved ride, the new one over it | `./gradlew assembleDebug -PreleaseShapedDebug` both builds | *(owed)* |
| R5 | Three replayed Rides at 0.8× along Touring routes: the Route sheet's time and Pace line before and after, and a second Bike's | position override replay | *(owed)* |
| R6 | The release-shaped build in Cycling: the switch, "All ▾", "Szukaj", Record's icon, the draw order, at 360 dp and 1.3 in Polish | `-PreleaseShapedDebug`, `pixel-api37` with `-gpu host` | *(owed)* |
| R7 | The new mark at 44 dp, 24 dp and 512 px | `art/make-launcher-icon.py`, the owner's eye | *(owed)* |
