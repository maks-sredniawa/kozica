# Phase 6 — The user records a Hike and looks back at it

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
relies on ADR-0001 (the schema gate, which schema 3 passes), ADR-0002 (every photo through
`MediaFiles`), ADR-0003's amendments (nothing of ours is scheduled, so no reminder and no
notification outside a recording), ADR-0005 (amended at checkpoint E for the backup this phase
ports from binky), ADR-0008 (nothing leaves the phone except by the user's own share) and ADR-0009
(the height grid a Hike's ascent is read from). [`CONTEXT.md`](../CONTEXT.md) gains **Hike**, Polish
UI *wycieczka*, and gives **Track** its row.

**What closing this phase means.** Today the app plans a Route and shows where the user is, and
forgets everything the moment the dot moves. After this phase the user presses Record at Kuźnice,
puts the phone in a pocket, pauses at Murowaniec, takes photos on the way up, presses Stop at Kasprowy
Wierch, and finds the Hike in the Hikes tab with its Track, distance, times, ascent, profile, photos
pinned where they were taken and a note, exportable as GPX, and safe in a backup. The difference a
device can be asked about: on the owner's phone, a walk of two hours or more in a pocket, screen off,
with no exemption granted, comes back as one Hike with no gap longer than two minutes.

**`PLAN.md`'s paragraph bundled two costs, and was split on 2026-10-26 with the owner** (`PLAN.md`
rule 2). This phase is the hike log and GPX export. Following a Route while walking and walking times
from the user's own pace are phase 6b, which needs this phase's recorded Hikes to learn a pace from.
Named Trails and highlighting a whole Trail are phase 6c, a map-data change that needs nothing from
either.

---

## 0. What this phase inherits

- **The app reads fixes through the platform `LocationManager`** (`data/location/PositionFixes.kt`),
  with no Play services. So Google's Activity Recognition is not available, and auto-resume uses a
  hardware sensor instead (§ 3).
- **Precise location is already asked** at the position button (phase 2), and from API 31 the user
  can grant approximate only. Recording reuses the same ask, at its own moment (§ 2).
- **Nothing in the app is scheduled, and it posts no notification of its own.** The only foreground
  service in the bundle is Play Asset Delivery's `dataSync`, whose notification and channel are the
  library's, and whose Console declaration is still open in `DOD.md`. The recording's notification
  is Kozica's first, and `POST_NOTIFICATIONS` is its first notification ask.
- **`aab-permissions.py` forbids `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** (Play restricts it). If the
  gate's verdict 2 needs a battery exemption, it is a hand-off to the system's list, never the
  direct request.
- **The height grid is on the phone** (`relief.pmtiles`, zoom 12, Terrarium in lossless WebP, about
  12 m), but phase 5 never decodes it on the phone: the Trail network carries its heights. A Hike's
  Track goes where no network edge does, so this phase is the first to read the grid on the device.
- **The backup is the template's basic version**: an agent carrying the database and every
  thumbnail, export at two scopes, and restore. The Backup screen exists, but its row left Settings in
  phase 0 until this phase. Binky (`maks-sredniawa/binky-app`, its ADR-0005) grew the design this phase
  ports.
- **No tester holds a schema-1 database**, and schema 2 is the saved Routes (phase 5 § 5). Schema 3
  is the second bump since the door, so the whole standing gate applies (`DOD.md`).
- **Nothing is being published to Play during this phase**, by the owner's choice, so the order of
  the checkpoints is not bound to a release.

## What is in, and what is deliberately not

**In:** recording a Hike from the Map tab with a `location` foreground service and its notification;
pause, and auto-resume when the phone starts moving; the Hikes tab with each Hike's Track, distance,
moving and total time, ascent and descent, profile, rename, delete and trim; photos from the camera
during a recording and from the Photo Picker afterwards, pinned to the Track; a note per Hike and a
caption per photo; binky's backup design ported, with the row back in Settings; GPX export of Hikes
and of saved Routes, through the share sheet.

**Not in, and each with a reason rather than a phase:**

- **Searching the Hikes list.** The owner wants it, but only once a list is long enough to need it;
  a new list sorted newest first does not.
- **Following a Route while walking, and a pace of the user's own.** Phase 6b: a pace needs recorded
  Hikes, which this phase creates.
- **Named Trails and highlighting a whole Trail.** Phase 6c: a change to `KozicaProfile` and the
  tiles, a rebuild and every pack delivered again, and nothing to do with recording.
- **Notes pinned to points.** One note per Hike, and a caption on a photo for "this spot was X". A
  second kind of pin is more UI for what a captioned photo already says.
- **Editing points in the middle of a Track, or splitting a Hike.** Trimming the start and the end
  covers the common accident, a forgotten Stop, and the rest is a lot of UI for a rare need.
- **Detecting a car.** A guess either way, and trim is the remedy for the case it would catch.
- **Taking a photo outside a recording.** A photo always belongs to a Hike.
- **A recurring export reminder and the "thumbnails left out" notification** binky has. Both are
  scheduled work or a notification outside a recording, which Kozica does not do (ADR-0003's
  amendments); the Hikes tab's banner says the same thing inside the app (§ 7).
- **Imperial units.** Metric only, as on every signpost the app draws.
- **GPX import.** Out of the plan (`PLAN.md`).

**The tempting one** is a sticky service: `START_STICKY`, or a boot receiver, so that a recording
killed by the ROM restarts by itself. It looks like resilience and holds no location: a foreground
service started from the background has no while-in-use access to location (a platform rule since
API 30, not ours), so it would record an empty Track while promising a full one. A recording the
process lost waits for the next launch, which offers to resume or finish it (§ 2).

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The gate** — does a recording survive a pocket on Xiaomi's ROMs? | `chore:` the debug-only recorder; the verdict is a reading | — |
| **B** | Recording: Record, Pause, auto-resume, Stop, the asks, schema 3 | `feat:` | A |
| **C** | The Hikes tab: list, details, ascent, profile, rename, delete, trim | `feat:` | B |
| **D** | Photos and the note | `feat:` | C |
| **E** | Backup, ported from binky, and its row back in Settings | `feat:` | D |
| **F** | GPX export of Hikes and saved Routes | `feat:` | C |

**A comes first** because every later checkpoint is a recording: a service MIUI or HyperOS kills in a
pocket is rethought before anything is built on it. **E comes after D** because photos are what make
backup matter: until D, Auto Backup already carries everything. **F needs only C**, so it can land
anywhere after it. **Schema 3 lands in B and is frozen there**: C, D and E add columns only by
another migration.

---

## 1. The gate: does a recording survive a pocket?

### The question, in the form that can be measured

With a `location` foreground service started from the visible app, asking for a fix every 5 s:
**with the screen off, unplugged and untouched, does it keep storing points for a whole Hike, with no
battery exemption and no autostart?** On the test phone (MIUI 12.5, API 30) for four hours forced
idle, and on the owner's phone (HyperOS, API 36) on a real walk of two hours or more. And does the
significant-motion sensor fire, screen off, when a paused phone starts walking? And what does an
hour of recording cost in battery?

### The apparatus

- **`RecorderService` in `app/src/debug/`**, its manifest entry in the debug manifest: a foreground
  service of type `location` with a plain notification and a Stop action, started by a debug
  Settings row, writing each fix to a `gate_points` table in a debug-only database file. It asks
  `GPS_PROVIDER` for a fix every 5 s with no minimum distance and stores every one with its
  accuracy, the ones worse than 30 m too: § 2's filters would leave a still phone with no points at
  all, and the gate asks whether the service lives, not what a Track keeps. Pause and resume are a
  second debug row; while paused it arms the significant-motion sensor and logs `KozicaRecord
  motion` when it fires. While recording, a `gnss` event at most once a minute, from the GNSS status
  callback, is the heartbeat: it tells a live service with no fix from a dead or frozen one.
- **`scripts/track-gaps.py`**: reads a pulled database (with its `-wal`, checkpointed on the host)
  and prints the point count, the span, every gap over 60 s between fixes of any accuracy and the
  longest; then, apart, the gaps § 2's 5 m and 30 m filters would have left. Gaps are on the clock
  each fix was stored by, and run from a start or resume to the first fix and from the last fix to a
  pause or stop. A gap spanned by heartbeats with no satellite in a fix reads "alive, no fix" and
  is listed apart; one with a satellite in a fix means a fix never stored, and excuses nothing.
  Verdict 1's bar applies to silence, neither a stored fix nor a no-fix heartbeat.
- **The test phone, simulated:** `adb shell dumpsys battery unplug`, screen off with `adb shell input
  keyevent KEYCODE_SLEEP`, `adb shell dumpsys deviceidle force-idle`, four hours, then `dumpsys
  battery reset`. It never leaves USB (the PC's internet runs through it), so a real-walk reading
  there is not possible; indoors, its fixes are weak, which makes it a test of the service and not of
  the Track. Ten minutes with the screen on must store fixes before the idle starts; a desk that gets
  none moves the phone to the window on its cable, since a reading with no fixes at all says nothing
  about fixes. When the phone cannot reach a window, as the test phone cannot, a no-fix reading is
  read on heartbeats: they show whether the service and the GNSS engine are alive through the idle,
  not whether fixes are delivered or what a Track would keep.
- **The owner's walk:** the debug build installed beside the Play build, no exemption and no
  autostart granted (read with `scripts/device-gate.py` first), battery level read before and after,
  one pause of a few minutes partway and then walking on without touching the phone.

### The verdicts, decided now rather than argued about later

1. **The walk passes** — no gap over 2 minutes between fixes outside a pause, and the service still
   in the foreground at the end (`adb shell dumpsys activity services`; not the notification, which
   the user can swipe away from API 34 while the service runs on) → no extra asks; B goes ahead as
   planned.
2. **The walk fails** → B also owns a battery-exemption hand-off, made when the user presses
   Record: the system's battery-optimisation list (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`)
   and on Xiaomi the app's battery-saver page, re-read with
   `PowerManager.isIgnoringBatteryOptimizations`, and a `WarningBanner` on the recording bar while
   the answer is no. Then the walk is read again with the exemption.
3. **The recording dies even with the exemption** → stop. The phase is rethought with the owner
   before B: nothing is built on a recorder the ROM kills.

**The walk decides; the test phone informs.** A failed R1 beside a passing walk adds no ask: R1 is
read again with the exemption, and *Known gaps* records whether MIUI 12.5 needs one. A failed R1
beside a failed walk is verdict 2.

**The motion sensor's reading is separate, and cannot veto the phase.** It fires → auto-resume as in
§ 3. It is absent, or does not fire with the screen off → the phone falls back to resuming by hand,
and the pause screen says so.

### The verdict, taken *(pending the walk)*

**Not yet taken: R2, the walk, is still owed.** What is read so far, 2026-10-26:

- **The owner's phone at a window (R2a) passes the bar.** A still phone, screen locked, unplugged, no
  exemption: 2.26 h, no gap over 60 s. HyperOS kept the GPS on through an hour of deep Doze; whether
  it does in a pocket is R2's question.
- **The test phone (R1) fails the bar, read on heartbeats.** MIUI 12.5 held the foreground service
  through four hours of forced deep idle, but its GNSS heartbeats stopped for as long as the idle
  lasted, about 4 h of silence, with or without the AOSP battery exemption (R1b). If the walk passes,
  the rule above applies: no ask, and *Known gaps* records it. If the walk fails, verdict 2's hand-off
  is the exemption, and R1b read that the AOSP one (`dumpsys deviceidle whitelist +<pkg>`) does not
  keep MIUI 12.5's GPS on; MIUI's own battery-saver page (*No restrictions*) and autostart were not
  read, so verdict 2 reads those first.

**B was built on R2a's reading, an exception to *A comes first***, decided with the owner on
2026-10-26: the walk's date is not known, and B does not wait for it. Verdicts 2 and 3 still apply
when the walk is read: a failed walk adds verdict 2's hand-off to B, and a recording that dies even
with the exemption stops the phase, B included, until it is rethought with the owner.

## 2. Recording

**Starting.** A Record button on the Map tab. Pressing it walks through the asks in order, each only
if needed, and starts the service from the visible app:

| State at Record | What happens |
| --- | --- |
| Precise location granted | The recording starts |
| Location never asked, or denied once | The system's ask (fine and coarse together, as phase 2 asks) |
| Approximate only (API 31+) | The system's precise-upgrade ask, as phase 2's banner makes it (phase 2 § 4) |
| Either ask answered without precise location, or location denied for good | Nothing starts; a screen says why, with a button into the app's settings. A later Record asks again while Android still allows it |
| Notifications not granted (API 33+) | The system's ask, once, at Record and never at launch (ADR-0003); a no still records, and the recording bar says the shade has no Stop button |
| The phone's location switched off | The recording starts, stores nothing, and the bar and the notification say so; the gap is a break in the Track |

**While recording.** The Track draws live on the map. A recording bar sits at the bottom of every tab,
with distance and time and the Pause, camera and Stop buttons. The notification carries the same
time and distance, with Pause (or Resume) and Stop. No live ascent: ascent is read once the Hike
ends (§ 5).

**The notification's channel**, "Hike recording", is created at `IMPORTANCE_LOW`: no sound and no
heads-up, for a notification that is up for hours. A channel's importance can only be lowered
afterwards, and only by the user (ADR-0003), so this is decided once. It is Kozica's only channel of its
own (Play Asset Delivery creates one for its `dataSync` service), and every change to the notification, auto-resume's included (§ 3), is a silent update of the same
one.

**Fixes.** `GPS_PROVIDER`, at least 5 s and 5 m apart (Android requires both before it sends the
next one). Fixes worse than 30 m accuracy are dropped, which removes the zig-zags under a cliff or
in a forest. That is 2,000 to 4,000 points for a day's walk. The gate's battery reading may change
these numbers; nothing before it does.

**Storage.** Each fix is a row in `track_points` as it arrives, so nothing recorded is lost to the
process dying. At Stop, the Hike's points are encoded into one compact Track on its `hikes` row (a
segment per stretch between pauses, delta-encoded, about 25 KB for a day) and the rows are deleted.
Rows would cost about ten times that, and Auto Backup's 25 MB holds the database whole or not at
all (§ 7). The encoding is B's to choose, under a round-trip test.

**Stopping.** From the notification or the bar, with a confirmation. The Hike is saved straight
away, named with the date and the nearest named place to where it started, by phase 5's rule for
demanding stretches (peaks, passes and Huts first, within 500 m); the date alone when nothing is that
close. Renaming is in the Hike's details. A Hike that stored no point, a Record and Stop with no
fix kept between them, is discarded rather than saved as an empty one (decided with the owner on
2026-10-26).

**One recording at a time**, never tied to a saved Route in this phase.

**When the process dies.** The service is not sticky and there is no boot receiver (the tempting
one, above). The next launch finds a Hike with no end, and offers to **resume** it, which starts a
new stretch, or to **finish** it at its last point. It never throws a recorded point away.

**The schema guard.** Record starts the service from the visible app, past the launch gate, but a
notification's Pause, Resume or Stop reaches the service with no screen in front. So the service
asks `BackgroundSchemaGuard` (`schemaBlocksBackgroundWork()`) on every start before it touches the
database, as ADR-0003's 2026-10-18 amendment promised; a blocked start stops the service and writes
nothing.

**Schema 3**: `hikes` (name, start and end, the encoded Track, the note, the totals computed at Stop
and after a trim) and `track_points` (while recording), with the photos' table in D's shape decided
now, since the schema freezes at B: `hike_photos` (the media path by kind, the time taken, a nullable
position and the caption). `MIGRATION_2_3`, its schema JSON, the `SchemaGateTest` assertion, a
migration test reading back a saved Route, and an upgrade watched on the test phone, per the
standing gate in `DOD.md`.

**Copy:** "Record hike", "Hike recording", "Recording", "Paused", "Resume", "Stop", "Stop
recording? The hike is saved and you can't add to it afterwards.", "Recording a hike needs precise
location", "Allow precise location in the app's settings to record a hike. Approximate location can
be off by more than a kilometre.", "Open settings", "Notifications are off, so there's no Stop
button outside the app", "Location is off — nothing is being recorded", "A hike didn't finish
recording", "Resume recording", "Finish it here".

## 3. Pause, and resuming by itself

**Pause stops GPS**, which is the battery reason to pause, and keeps the foreground service and its
notification, now "Paused" with Resume and Stop. Stopping the service instead would need a restart
from the background to resume, which Android 12+ refuses and which would hold no location anyway.
**The Track breaks at a pause**: the next fix starts a new stretch, and nothing joins the two with a
straight line.

**Auto-resume** uses the significant-motion sensor (`TYPE_SIGNIFICANT_MOTION`): a one-shot hardware
trigger that fires when the user starts walking or travelling and ignores the phone shaken in place.
It needs no permission, costs next to nothing, and wakes the phone from Doze. Armed at Pause,
cancelled at Resume and Stop. When it fires, the recording resumes and the notification says so,
silently (§ 2's channel). A wrong resume records a few extra metres; a missed one loses a stretch
nobody can get back, so it resumes rather than asks.

**A Settings switch turns it off**, on by default, because the owner knows it can fire while sitting
still. On a phone without the sensor the switch is not shown, and the pause screen says resuming is
by hand.

**A forgotten pause** stays paused: nothing resumes on a timer and nothing reminds, since nothing is
scheduled. The recording bar is the reminder, and the notification where it shows: from API 34 the
user can swipe it away while the service runs on, and with notifications denied it is only in the
system's list of active apps.

**Copy:** "Pause", "Resumed — you're moving", "Resume when I start moving", "Resumes by itself when
the phone senses you walking. It can sometimes resume while you're sitting still.", "Resume by hand
on this phone".

## 4. The Hikes tab

A third tab beside Map and Routes, **Hikes** (*Wycieczki*). The list is newest first: name, date,
distance, total time and ascent. A Hike's details: the Track on the map with the rest of the map
faded as a Route's is, distance, moving time and total time, ascent and descent, the profile, the
photos, the note, and Rename, Export (F), Trim and Delete.

**The details open on the Map tab**, as a saved Route does (decided with the owner on 2026-10-27): a
tapped Hike replaces whatever Route is on the map, its sheet takes the Route sheet's place, and a
long press or a found place closes it again. One map, with everything it already draws, rather than
a second map view in a screen of its own.

**Moving time** leaves out pauses and stretches with no movement; **total time** runs from Record to
Stop. Saved Routes and Hikes stay separate lists: one is a plan, the other what happened.

**Trim**: two handles on the profile, and the Track between them is what stays. The cut points are
deleted for good after a confirmation; the totals are computed again, and a photo whose time falls
outside the kept span keeps its place in the Hike but loses its pin.

**Delete**: a confirmation that names how many photos go with the Hike; the photos' files are
deleted with their rows.

**Copy:** "Hikes", "No hikes yet. Press Record on the map to record one.", "Distance", "Moving
time", "Total time", "Ascent", "Descent", "Rename", "Trim", "Trim the start and end of the track",
"The cut part of the track is deleted. This can't be undone.", "Delete hike?", "Delete this hike and
its %d photos? This can't be undone." (plural), and "This can't be undone." alone for a Hike with no
photos.

## 5. Ascent and descent

**Inside the Regions, from the height grid**, so a Hike walked along a planned Route shows the same
numbers the plan did. Read once, at Stop and after a trim: the zoom-12 relief tiles under the Track
are decoded on the phone (lossless WebP, which every supported API decodes) and each point takes the
grid's height at its position. Those heights are summed with phase 5's 5 m dead band, the rule a
Route's ascent is summed by, so a Track wobbling sideways across a slope's 12 m cells does not add a
climb at every point, and the two numbers compare.

**Outside the grid, from GPS altitude, smoothed**: a climb or a drop counts only once the height has
moved 10 m in one direction since the last turn, which removes most of the jitter. **Ascent is summed
within each run of one source and never across a change of source**: GPS gives height above the
ellipsoid, about 35–40 m above sea level in southern Poland, and a sum across the boundary would
count that offset as a climb.

When any part came from GPS, the number shows "≈" and one line under it. Each point's height, by the
same rule, is what the profile draws and what GPX writes; from API 34, GPS heights are converted to
sea level with the platform's `AltitudeConverter`, offline (the known gap below).

**Copy:** "Partly from GPS height, less precise".

## 6. Photos and the note

**During a recording**, the camera button opens the system camera (`TakePicture`, no permission).
The photo is pinned at the last fix when the button was pressed, because `MediaFiles` strips GPS from
the file itself, as the house rules require.

**Afterwards**, "Add photos" on a Hike opens the Photo Picker (no permission). The time taken
(`DateTimeOriginal`, with `OffsetTimeOriginal` where the camera wrote it, else the phone's zone) is
read before `MediaFiles` strips the metadata. A time within a stretch pins the photo at the Track's
position at that moment. A time between two stretches, in a pause or a break, pins it at the last
point before them, where the user stopped, as the camera button would have: nothing is drawn across
a break (§ 3), so nothing is placed on one. Any other time, or none, adds it unpinned.

Each photo is written by `MediaFiles` as `photos/<uuid>.jpg` with its thumbnail. Pins on the Hike's
map open the photo, and the details show a strip; each photo can be captioned, removed from the Hike
or deleted. A missing file is a placeholder.

**"Remove from map" takes the photo's pin off** the Track and the map, and the photo stays in the
strip; "Delete photo" deletes it and its files (decided with the owner on 2026-10-27; first worded
"Remove from hike", which reads as taking the photo out). A photo always belongs to a Hike, so no
third state is needed, and no column.

**A file that is not an image Kozica can read**, taken or picked, is left out and counted in a
banner over the tab bar until dismissed, so a pick of ten that lands nine says so on any tab.

**The camera's pin is read from the stored Track**, as the last point at or before the moment the
button was pressed, rather than held in memory: the camera app in front is when a low-memory kill
takes the process, and the press time travels in saved state to the new one.

**One note per Hike**, free text in the details, editable any time.

**Copy:** "Take photo", "Add photos", "Caption", "Note", "Add a note", "Remove from map", "Delete
photo", "%d photos couldn't be added: the files aren't images Kozica can read" (plural), "OK".

## 7. Backup, ported from binky

Binky's ADR-0005 design, and its code where it fits (`data/backup/` and `ui/backup/` there), with two
changes. ADR-0005 is amended at E to record it.

- **The agent carries the database and the preferences always**, then thumbnails **newest first up
  to 20 MiB** in all (binky's `AUTO_BACKUP_BUDGET_BYTES`), never the full photos. The budget is a
  pure function over files, under a test. At about 50 KB per 512² thumbnail, a few hundred fit
  beside the database.
- **The full photos travel on a phone-to-phone transfer** (`FLAG_DEVICE_TO_DEVICE_TRANSFER`, API
  28+; binky's ADR-0005 says 30, wrongly), where there is no quota and no cloud.
- **A marker file outside the database** records the last automatic backup and how many thumbnails
  were left out. It is never in the backup set and is cleared at restore. Backup settings shows four
  states: when the last one ran, that it is stale past 14 days, that the last attempt was over quota
  and not saved (`onQuotaExceeded`), or that none has been recorded on this phone, with a button into
  the system's backup settings.
- **Backup settings says in words** that automatic backup keeps Hikes, notes and Routes but not the
  photos.
- **Export through the share sheet, with a remembered folder** (a persisted tree grant, checked on
  every read), falling back to the share sheet whenever the folder fails.
- **Restore**: a confirmation naming the scope and the date, an automatic Records export of the
  current state first, then stage, migrate, swap; a newer schema refused; the manifest is the
  authority, and only entries of a known shape are extracted.
- **The Backup row returns to Settings.**

**Changed for Kozica:** two export scopes, **Records** (database, preferences, thumbnails) and
**Everything** (and the photos); binky's third scope separates avatars and documents, which Kozica
does not have.

**Left out:** binky's recurring export reminder and its exclusion notification (*Not in*, above).
Instead **the Hikes tab shows a banner** while photos exist that no Everything export has covered, with
an Export button. The app knows this: it records when it last exported Everything.

**Decided with the owner at E (2026-10-27):**

- **Binky's Saved copies list is ported**, at the bottom of the Backup screen: a pre-restore snapshot
  has Restore, Share and Delete, a copy taken before a schema wipe has Share and Delete, and Delete
  asks first. It is how a bad restore is undone.
- **The banner sits at the top of the Hikes list and cannot be dismissed.** It counts the photos
  whose `addedAt` is after the last Everything export's start, kept in the preferences, so it needs no
  column and schema 3 stays frozen. Its Export runs an Everything export at once, to the remembered
  folder if one is granted and otherwise to the share sheet, without opening the Backup screen. The
  time is written once the archive is whole, so an export that never finished leaves the banner up;
  the preferences inside the archive predate it, so restoring an Everything archive raises the time
  to its manifest's `createdAt` at the next start. The share sheet never says whether the file went
  anywhere, so the file existing is what counts.

**The preferences joining the backup carry the map's last centre**, which the privacy policy says
stays on the phone. With Hikes in the database, the backup already carries far more location than
that, from B; the sentence goes at E, with the preferences, since `docs/` is published on merge.

**Copy:** binky's, translated into Kozica's words, plus "%d photos aren't in any backup" (plural),
"Export", "Automatic backup keeps your hikes, notes and Routes, not your photos. Export everything
to keep the photos."

## 8. GPX export

**From a Hike**: one `<trk>` with a `<trkseg>` per stretch between pauses; each point with its time
and its height by § 5's rule; the Hike's name and note as `<name>` and `<desc>`; each pinned photo as
a `<wpt>` with its caption, without the image.

**From a saved Route**: the planned line as a `<trk>`, which watches and other apps import most
reliably, with its heights from the network and its points as `<wpt>`s. Its line and points come
from OSM, so its `<metadata>` carries the attribution: a `<copyright>` by "OpenStreetMap
contributors" with the ODbL as its `<license>`, and a `<link>` to
https://www.openstreetmap.org/copyright. A Hike's own Track is the user's data and carries none.

**Delivery**: the file is written to the cache and handed to the share sheet, named after the Hike or
Route and its date: the day a Hike started, and for a Route the day of the export, since its line is
planned again from that day's network. Nothing leaves the phone unless the user picks where it goes;
ADR-0008 stands, because the share is the user's. Every app that takes the share is granted read
access to the file before the sheet opens: MIUI 12.5's share sheet starts the picked app as the
system, and the platform refuses the grant the intent carries (R8). The sheet's chosen-app callback
takes that access back from every app but the pick, and a return to the app with no callback heard
is a cancel, which takes it from all; a pick heard after that, from a target that hands off to a
service and finishes before the callback lands, gets its grant back, for the latest share only. The
pick keeps it until the next export or share from the same directory, or a reboot. There is no sweep
at start: on MIUI the task outlives a killed process, so a launch rarely starts afresh, and a share
target reopening the file after the ROM killed the app starts the process too.

**Copy:** "Export GPX".

---

## Tests

- **Track encoding round trip** (B): points in, the same points out, segment breaks included. Fails
  towards losing a recorded Hike.
- **Ascent** (C): the grid run's 5 m dead band, the GPS hysteresis, and no climb counted across a
  change of source. Fails towards a wrong ascent beside a planned Route's.
- **Moving time and trim** (C): a pause and a stationary stretch left out; a trim recomputes the
  totals and unpins the photos outside it.
- **Photo time to Track position** (D): inside, outside, in a pause (the last point before it), no
  time at all.
- **Auto Backup admission** (E): the database and preferences always in, thumbnails newest first up
  to the budget and never over. Fails towards the whole backup being silently dropped.
- **The marker** (E): never in the backup set, cleared at restore, stale past 14 days, over quota
  until a backup goes through.
- **GPX writer** (F): well-formed with names that need escaping, a `<trkseg>` per stretch, the OSM
  attribution in a Route's and none in a Hike's.

## Documents this phase amends

- **`CONTEXT.md`**: Hike and Track, with this file.
- **`PLAN.md`**: phases 6b and 6c, with this file.
- **The privacy policy**, each sentence with the checkpoint that makes it true, since `docs/` is
  published on merge: at B, the app stores location, on the phone only, in Hikes, and Auto Backup
  carries them to the user's own Google account; at D, photos have their location stripped; at E,
  the preferences join the backup (the "stays on the phone" sentence goes) and a backup leaves only by
  the user's own share; at F, so does a GPX.
- **`play-app-content.md`** (B): the `location` foreground service's declaration and its answers
  (the owner's, with a short video), recording a Hike added to the `ACCESS_FINE_LOCATION` and
  `ACCESS_COARSE_LOCATION` rows, and Data safety's reasoning for stored location. It stays "no data
  collected": nothing is transmitted off the device by the app.
- **`scripts/aab-permissions.py`** (B): `FOREGROUND_SERVICE_LOCATION` and `POST_NOTIFICATIONS` join
  the allowlist, and `FOREGROUND_SERVICE`, `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` gain
  the recording as a reason, in step with `play-app-content.md`'s rows.
- **ADR-0003** (B): an amendment saying `POST_NOTIFICATIONS` returns with the recording's
  notification, asked at Record, on § 2's one channel, and that the recording service asks
  `BackgroundSchemaGuard`, as its 2026-10-18 amendment promised.
- **ADR-0005** (E): amended for binky's design.
- **`docs/store-listing.md`**: the hike log in the description, before the release that carries it.
- **`scripts/edge-to-edge.py`**: scenes for the Hikes tab, a Hike's details and the recording bar.

## Known gaps, deliberately

- **On MIUI 12.5 (API 30), deep Doze turns the GPS off under a recording**, with or without the AOSP
  battery exemption (R1, R1b); MIUI's battery-saver page and autostart were not read. It is a gap
  only if the walk passes (§ 1's verdict). Read indoors on a phone that never moved: a no-fix
  heartbeat 2 s after `deviceidle unforce`. AOSP's default enters deep Doze after about half an hour
  of stillness and leaves it on movement, not read on MIUI, so the gap would be a long rest without
  Pause and the first walking after it, until the phone leaves idle and gets a fix.
- **Below API 34, GPS heights outside the grid are above the ellipsoid**, about 35–40 m above sea
  level in southern Poland, in the profile and in GPX. Ascent is unaffected (§ 5). Closing it means
  bundling a geoid model, for Hikes outside every Region on older phones.
- **The significant-motion sensor can fire while sitting still**, and some phones lack it. The
  Settings switch and the by-hand fallback cover both.
- **A recording the ROM kills stops until the next launch.** The sticky service that would hide that
  records nothing (the tempting one).
- **Photos picked afterwards with no time taken stay unpinned.** Reading the photo's own location
  instead would need `ACCESS_MEDIA_LOCATION`, a new ask for a rare case.

## Readings

Device state the readings depend on: the test phone unplugged in software (`dumpsys battery
unplug`) and reset afterwards, its keyguard dismissed for screenshots; the owner's phone with no
battery exemption and no autostart, read with `scripts/device-gate.py` before the walk, since the
autostart grant lapses on its own, and notifications allowed for the debug build, as a user who
answered yes at Record has them. Every database is pulled with its `-wal` and checkpointed on the
host.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The gate, test phone: four hours forced idle, screen off, unplugged in software | `dumpsys battery unplug`, `input keyevent KEYCODE_SLEEP`, `dumpsys deviceidle force-idle` (17:22:19); `dumpsys deviceidle unforce` and `dumpsys battery reset` (21:24:08). R1b the same with `dumpsys deviceidle whitelist +io.github.srednimax.kozica.debug`, `force-idle` at 21:28:43, `unforce` about 21:45, the whitelist entry removed after. `scripts/track-gaps.py` on the pulled database; heartbeat times from `adb logcat -d -s KozicaRecord` and `sqlite3 gate.db "SELECT … FROM gate_events WHERE kind='gnss' …"` on it |**Fail**, on silence: about 4 h with neither a stored fix nor a heartbeat. 2026-10-26, 16:55–21:27, indoors, so no fix at all (0 of about 30 satellites), read on heartbeats (§ 1); `track-gaps.py` stops at "no fixes: the reading says nothing". The service was in the foreground from start to Stop. Heartbeats stopped at 17:22:20, one second after `force-idle`, and came back at 21:24:10, 2 s after `unforce`. **R1b**, 21:28–21:46: heartbeats stopped about 45 s into the idle again, so the AOSP exemption makes no difference |
| R2 | The gate, owner's phone: a real walk of two hours or more in a pocket, one pause, battery per hour | `scripts/track-gaps.py`; battery level before and after |*(owed: the walk)* |
| R2a | The gate on the owner's phone, still: a window, screen locked, unplugged, no exemption and no autostart (`device-gate.py`) | `scripts/track-gaps.py --pull -s <serial>`; `dumpsys batterystats --history` for Doze; `adb shell dumpsys activity services <pkg>` | **Pass**, 2026-10-26, 17:26–19:41: 1,593 fixes, 1,558 within 30 m, no gap over 60 s, longest lag from fix to storing 1.2 s. Deep Doze (`device_idle=full`) from 18:14:22 to 19:14:22, with fixes every 5 s through it. Battery 64 → 57 %, 3.1 %/h. The service was in the foreground at Stop |
| R3 | The significant-motion sensor: present on both phones, and firing on the walk after the pause | `adb shell dumpsys sensorservice`; `adb logcat -s KozicaRecord` |**Present on both**: the test phone's MTK `SIGNIFICANT_MOTION`, and `sns_smd` on the owner's phone. Armed at a pause on the test phone (`motion sensor armed`). Firing on the walk is *(owed)* |
| R4 | Process death mid-recording: the next launch offers resume or finish, and no point is lost | `adb root; adb shell kill -9 $(pidof <pkg>)` (emulator), `adb shell run-as <pkg> kill -9 <pid>` (owner's phone; the emulator answers 'unknown pid') during a recording; the pulled database |**Pass on the API 33 emulator**, 2026-10-31, debug build at 1a6f173: 20 fixes by `adb emu geo fix`, 22 m and 6 s apart, stored as 21 rows (the fix from before Record and the 20); then `kill -9` under `adb root`. The service did not come back (`START_NOT_STICKY`); the 21 rows and the open Hike were still there. The next launch offered 'A hike didn't finish recording'; Resume began a second stretch and the bar carried 445 → 667 m on; 10 more fixes, then Stop: the Hike ended at its last fix, 667.2 m, its Track decoding as 21 + 11 points (the last fix before the kill, which the provider sent again when Resume asked for updates, and the 10), none lost. Read from the database with its `-wal` checkpointed on the host: an on-device `sqlite3` opened on the live file took itself for the last connection and unlinked the app's WAL, so the WAL holding the Stop's writes was copied out through `/proc/<pid>/fd`. **Pass on the owner's phone** (API 36, HyperOS), 2026-10-31, debug build at 1a6f173 over wireless adb, its fixes from a shell test provider (`cmd location providers add-test-provider gps` and `set-test-provider-location`, the shell's `mock_location` appop allowed for the reading and reset after): 20 fixes stored as 20 rows, then `run-as <pkg> kill -9 <pid>`, which works here. The service did not come back; the rows and the open Hike survived. The next launch offered 'A hike didn't finish recording'; Resume began a second stretch; 10 more fixes, then Stop: 622.7 m, its Track decoding as 20 + 10 points, none lost (the test provider does not send the last fix again). The database was pulled with its `-wal` through `run-as cat`. Injected taps on this phone are sometimes dropped: here Resume's three taps did nothing and it went through by Tab then Enter on the focused button; in a later repeat of this reading Record needed two taps and a single Resume tap worked, and R9's first Record tap did nothing. So each step was confirmed before the next |
| R5 | Schema 2 to 3 on the test phone: the previous release-shaped build with a saved Route, this one over the top | `assembleDebug -PreleaseShapedDebug` both times; `dumpsys dbinfo` |**Pass**, 2026-10-26, test phone: the release-shaped build of 6cec096 (schema 2) showed a saved Route, 48.9609, 19.9825 – 48.9241, 20.0074, 26.9 km. The release-shaped build of 1c09169 (schema 3) went over the top with no schema screen. It showed the same Route, which is what shows `MIGRATION_2_3` kept the rows; `hikes` answered a query (`dumpsys dbinfo`), so schema 3 is live. A release-shaped build has `isDebuggable = false`, so no destructive fallback |
| R6 | A Hike's ascent beside the planned Route over the same Trails | the Hikes tab and the Routes tab, same Trails | **Read on a recorded line, 2026-10-27**, test phone, debug build with the Regions, the network and the relief hand-pushed: the `route-hike` seed plans Kuźnice → Kasprowy Wierch and records its planned line as a Hike. Planned 969 m, the Hike 975 m from the grid, 6.5 km both; the network sums each edge's climb apart and the Hike one run, so the 6 m. The same build read 1,040 m for the `hikes` seed's Kasprowy line, as the host read it with `build-network.py`'s sampler (1,039.6 m). A walked Hike beside its Route is *(owed: the walk)* |
| R10 | Photos picked afterwards, pinned by their time taken (§ 6) | three JPEGs pushed to `/sdcard/DCIM/`, picked with "Add photos"; the pulled database | **Pass**, 2026-10-27, test phone (API 30, the system file chooser standing in for the Photo Picker): one dated 09:53:15 with no offset was pinned on the Track point at 07:53:15Z; one dated 10:20:00 with `OffsetTimeOriginal` +02:00 fell in a pause and was pinned at the last point before it; one with no date was added unpinned; two 0-byte PNGs picked with them were left out, and a second pick of all five raised the banner over the tab bar, "Nie udało się dodać 2 zdjęć…", which OK dismissed. Caption, "Remove from map" (then worded "Remove from hike"), the note and "Delete photo" (row and both files) read in the app; a photo with its files removed by hand drew the placeholder in the strip and the viewer |
| R11 | The camera during a recording pins at the last fix when pressed (§ 6) | API 33 emulator, `adb emu geo fix`; the pulled database | **Pass**, 2026-10-27: seven fixes, then Take photo, then a fix while the camera was open. The photo was pinned at the last fix before the press, not the one during the camera; its time was the camera's own; `cache/camera/` was empty after. After Stop the photo was in the Hike's strip |
| R7 | Auto Backup with photos past the budget: the database still in the backup | `adb shell bmgr backupnow`; the marker file | **Pass**, 2026-10-27, test phone (API 30), debug build, on the local transport (`bmgr enable true`, `bmgr transport com.android.localtransport/.LocalTransport`; the phone has no Google account), with 30 random 1 MiB files pushed into `files/thumbnails/` beside the 2 real thumbnails: `bmgr backupnow` reported Success at 20,017,152 bytes. `file_backup_helper` listed `f/autobackup/kozica.db`, `f/datastore/app_preferences.preferences_pb` and 21 thumbnails, and no photo, `preserved/` or marker; the marker read `excludedThumbnails=11` and `autobackup/` was gone after. `bmgr restore 1` then cleared the marker and dropped the staged copy beside the live database. Afterwards the files were removed, the local transport's copy wiped (`bmgr wipe`), and the GMS transport and `bmgr enable false` restored. A Google Drive backup is not read |
| R12 | An export and a restore round trip in the app | the debug build on the test phone; `unzip -l` on the pulled archive | **Pass**, 2026-10-27, test phone, debug build, the "Kasprowy test" Hike (3 photo rows, 2 with files): the banner read "3 zdjęcia nie są w żadnej kopii zapasowej"; a folder made in the picker was remembered; the banner's Eksportuj saved `kozica-everything-…zip` there (manifest, database, preferences, 2 photos, 2 thumbnails) and the banner went. The Hike was deleted with its photos, then restored from the file through the picker: the terminal screen read "Przywrócono 4 obrazy", and after Zamknij Kozicę and a relaunch the Hike and all four files were back, with a before-restore snapshot under Zapisane kopie. The first try raised the banner again after the restore, because the archive's preferences predate the export; a restore now raises the time to the manifest's. Rechecked after review: the banner's export saved the archive and cleared the banner and `cache/exports`, and restoring that archive (its preferences' time older than the photos) raised no banner after the relaunch. Restoring a snapshot from Zapisane kopie kept the 2 photos ("Zachowano 2 obrazy"), and Delete asked first. The system file picker took no taps on its items on MIUI 12.5 and was driven with the keyboard |
| R8 | A GPX export opened in another app, with its photos as waypoints | the share sheet to Mapy.cz or Files; on the test phone neither takes a GPX, so Organic Maps' signed APK (2026.09.29) installed for the reading and removed after; `adb logcat` for `UriGrantsManagerService` | **Pass**, 2026-10-27, test phone (API 30, MIUI 12.5), debug build. "Kasprowy test" exported as `Kasprowy test 2026-09-12.gpx` and opened in Organic Maps through the share sheet: its name, its note, the 3.1 km track and the two pinned photos as waypoints (no captions, so Organic Maps names them by position). A saved Route, Kuźnice – Kasprowy Stawek, the same way: `Kuźnice – Kasprowy Stawek 2026-10-27.gpx`, 147 points with the network's heights, both points as named waypoints and the OSM `<copyright>` and `<link>`; it opened with two bookmarks and one track. **The first tries failed** with "No content provider": MIUI's share sheet, and its plain resolver too, starts the picked app as the system, and the platform refuses that grant ("use startActivityAsCaller() instead"). The share now grants read access to every app that takes it before the sheet opens (§ 8 *Delivery*), which also covers the backup's share. MIUI's file manager "Kopiuj do…" wrote nothing on paste, before that change; not read again. Mapy.cz on the owner's phone is *(owed)* |
| R9 | Each row of § 2's table, with the API level: on the owner's phone (API 36) approximate only, its upgrade kept, denied for good, notifications denied (does it record, what the bar says) and the notification swiped away (the service runs on); on the test phone (API 30) denied and location off; and on both, the `location` service starting with no `SecurityException` | `adb shell pm revoke`/`grant`, `cmd location set-location-enabled`, a screencap per state; `adb logcat -d \| grep SecurityException`; `adb shell dumpsys activity services` |**Pass**, 2026-10-31, matching § 2 as amended by this reading. First read in part on 2026-10-26: API 33 emulator, no screencaps: approximate only led to the precise-location screen; the next Record made the upgrade ask; notifications denied still recorded, with the bar's line. Owner's phone (API 36): location switched off mid-recording showed 'Location is off — nothing is being recorded' in the notification, and switching it back on brought back the distance; two screenshots at 20:13 of the recording bar and the notification, none of the location-off state. Test phone (API 30), 2026-10-31, debug build at 1a6f173: denied for good (`pm revoke` keeps `USER_FIXED`, and API 30 has no command to clear it) showed 'Nagrywanie wycieczki wymaga dokładnej lokalizacji', and 'Otwórz ustawienia' opened the app's own page in Settings; with location off (`cmd location set-location-enabled false`, Play services' warning dismissed) the recording started and the bar and the notification (its text from `dumpsys notification`) read 'Lokalizacja jest wyłączona — nic nie jest nagrywane', cleared when it came back on, and Stop with no point discarded the Hike. The service ran in the foreground, declared `location`, with no `SecurityException` in the log. Screencaps of the denied-for-good screen and the location-off bar were taken but not kept. Owner's phone (API 36, HyperOS), 2026-10-31, debug build at 1a6f173, screencaps taken but not kept: approximate only made the system's precise-upgrade ask (at the second Record tap; the first was dropped), and keeping approximate showed 'Recording a hike needs precise location', whose 'Open settings' opened HyperOS's App info for the app, and nothing started; denied for good (`pm set-permission-flags … user-fixed`) showed the same. Never asked made the system's ask for fine and coarse together; a 'Don't allow' showed the same screen and the next Record asked again, so denied once still reaches the system's ask and a second no makes it for good. § 2 showed the screen only after approximate was kept, but `rememberRecordStart` shows it after any ask that leaves precise location missing, by design, so § 2's row now says so. Notifications never asked were asked at Record, not at launch; with 'Don't allow' the recording ran on and the bar said 'Notifications are off, so there's no Stop button outside the app'. The notification swiped away was cancelled (reason 2, the user's) while the service stayed in the foreground and stored fixes, and a later fix posted it again (`notifyState` posts at most every 30 s). Neither phone logged a `SecurityException` from the app (this one's 149 are all `SettingsProvider`'s). Afterwards the debug build was back as before: fine, coarse and notifications granted with no user-set or user-fixed flag, location on, the test provider removed and the shell's `mock_location` appop at its default, and the four test Hikes deleted |
