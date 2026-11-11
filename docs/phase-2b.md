# Phase 2b — Every mountain range downloads from a list, across the border, delivered by Play

**Partly undone by [`phase-2c.md`](phase-2c.md), 2026-10-22:** every Region became a fast-follow
pack, and § 5's Regions screen, welcome screen and camera arrival rule were deleted. The rest
stands.

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
builds on [ADR-0007](adr/0007-osm-data-ships-per-mountain-range-as-play-asset-packs.md) and owns the
second decision it leaves to "the map phase": the list of Regions, with each one's polygon. It
reopens one of ADR-0007's premises, that the data is Poland's extract alone, and amends the ADR in
this file's commit. It works inside [ADR-0008](adr/0008-kozica-never-opens-a-network-connection.md)
and takes over phase 1's checkpoint E, whose brief is [`phase-1.md`](phase-1.md) § 5. `CONTEXT.md`
owes one change: a **Region** no longer stops at the state border.

**What closing this phase means.** Today the release map says "No Regions downloaded yet." and
nothing can change that: the asset-delivery library is in the debug build only, and only the Tatry
exists, cut at the border. After this phase nine Regions are built in CI from Poland's, Slovakia's
and Czechia's extracts, each an on-demand pack, and a Regions screen downloads any of them, or all of
them, and removes them. The difference a device can be asked about: a release build installed from
Play downloads the Tatry, draws the Slovak side's Trails beside the Polish ones, and a one-pin data
refresh arrives as a patch Play reports at a fraction of the pack.

**Closed on 2026-11-10 with the owner, the Tatry's patch not shown.** Every reading is in. The Tatry
came whole in R10–R12; Play patched six other Regions once, in R10, and sent every pack whole
otherwise, and § 1's verdict 3, taken on 2026-11-01, keeps the format ([`DOD.md`](DOD.md)).

**`PLAN.md`'s paragraph changed in one place, decided with the owner on 2026-10-20 before this file
was written, and is amended with it.** It asks "whether a polygon follows the state border exactly".
The answer is that no polygon follows it: Regions cross into Slovakia and Czechia, where the ranges
do. Half the Tatry's Trails are Slovak, and the Karkonosze's main ridge Trail walks the border. That
is pipeline work with a cost of its own, so it is a checkpoint, C, not a footnote.

---

## 0. What this phase inherits

- **A provisional strip.** Phase 1 § 3 took verdict 3: under `bundletool --local-testing` the Tatry
  pack arrived but `ExtractionWorker` never ran, so whether extraction survives `ACCESS_NETWORK_STATE`
  stripped is unknown. **JobScheduler checks that permission only from API 34** (phase 1, R8's
  reading). The test phone is API 30 and cannot answer it; the owner's phone, API 36, can.
- **A proxy, not a reading.** Phase 1's R6 put a week of churn at 0.36 % of the Tatry archive by
  bsdiff. Play's algorithm for packs is undocumented. E1, the update size Play reports, is the
  reading that counts, and it moves into this file's table.
- **The Region seam, half built.** `data/regions/Regions.kt` in `main/` holds the states and reads a
  PMTiles header. `RegionSource.kt` is the seam: the debug half wraps `RegionFiles`, which knows one
  pack, `region_tatry`, by name; the release half emits an empty list. The map already draws every
  ready Region, by layer and not by Region, and shows the oldest one's OSM date (phase 2 § 2).
- **One pack module, gated.** `region_tatry/` joins the build only with `-PmapPacks`, which no CI job
  passes yet. `app/build.gradle.kts` names it by hand.
- **One pin, one extract.** `scripts/osm-data.pin` holds one Geofabrik sequence number;
  `build-map-data.py` brings Poland's extract to it and checks its header.
- **The tile schema is final for now.** Phase 2 fixed it, and the Tatry archive it builds is 7.3 MB
  at pin 4942 (`region_tatry/src/main/assets/tatry.pmtiles`, 7,274,738 bytes).
- **A public repository** and a GitHub release per release-please tag, which the ODbL offer rides.
- **The test phone has Play and a Google account**, read 2026-10-20 with `dumpsys account`, but sits
  in airplane mode. Its Wi-Fi stays on, which a reading through Play needs; `CLAUDE.md`'s
  test-phone bullet says why.

## What is in, and what is deliberately not

**In:** the asset-delivery library in the release build, with the Console's foreground-service
declaration and the documents that say Kozica schedules nothing. Nine Regions, each a polygon and an
on-demand pack. Slovakia's and Czechia's extracts merged into the build at one moment. The data build
in `publish-play.yml`, and the archives attached to each GitHub release as the ODbL offer. The
Regions screen: every Region's state and size, Download, Remove, and "Download all". A button for it
on the map and in the empty state. The readings through Play that phase 1 could not take.

**Not in, and each with a reason rather than a phase:**

- **Ukraine's side of the Bieszczady.** Its trails are few and its extract is large against what it
  adds. The polygon stops at the border there.
- **Germany's side of the Góry Izerskie.** The same, and smaller still. Sudety Zachodnie stops at
  that border.
- **Translated Region names.** Decided with the owner: a Region's name is Polish in every language,
  `translatable="false"`, as on the signposts and the map's own labels. Most of the nine have no
  exonym, and a name that changes between the picker and the ground costs more than one that does not
  match the UI language.
- **Showing a Region's outline on the map before it is downloaded.** It would need the polygons in the
  app as geometry, and a picker that draws. A row with a name and a size is enough to choose by; the
  names are the ones on every guidebook's cover.
- **A refresh outside a release.** ADR-0007 ties data to releases, and nothing here reopens that.

**The tempting one** is drawing the polygons along the state border "properly", or along each
range's watershed. It looks like care. But a Region's edge is never seen: the map draws a Region's
tiles, not its outline, and two ready Regions that overlap draw as one (phase 2 § 2). What the edge
decides is only whether a Trail near it is in the pack. So polygons are generous, overlap their
neighbours by a few kilometres, and are drawn coarsely, in tens of points, not hundreds.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The gate** — Play delivers the Tatry pack to a release-shaped build with `ACCESS_NETWORK_STATE` stripped, on API 36, and patches it for a one-pin refresh | readings only, from B's branch | — |
| **B** | **The library in `main/`** — `RegionFiles` for any pack, the release half of the seam, the declaration, the four permission rows | `chore:` | A |
| **C** | **Across the border** — Slovakia and Czechia merged into the build at one moment, a pin per extract | `chore:` | — |
| **D** | **Nine Regions** — the polygons, one pack module per polygon, built and measured | `chore:` | C |
| **E** | **The Regions screen** — the list, Download, Remove, "Download all", the map's button and the empty state's | `feat:` | B, D |
| **F** | **Play delivers it** — the data build in `publish-play.yml`, `-PmapPacks`, the archives on the GitHub release, the licences screen's offer | `feat:` | B, D, E |

**A reads B's branch before B merges.** The question is whether the release build may carry the
library at all, and the only apparatus that answers it is that build, delivered by Play. So B is
written on a branch, bundled locally with the Tatry pack, and given to Play through internal app
sharing. A local bundle is allowed here: ADR-0007 forbids local data only in a *release*, and an
internal-sharing link reaches the owner alone. If A takes verdict 2, B does not merge.

**C needs no gate and runs beside A.** It is local pipeline work and puts nothing in front of a user.
It goes before D so the nine polygons are drawn once, already across the border, rather than drawn
to the border and redrawn.

**B, C and D cut no release; E and F cut one together.** B puts the library into the release while
the release still carries no pack, so a user sees nothing. E is the first thing a user sees, and on
its own it shows nine Regions whose every Download fails, because no pack is in the bundle until F.
So release-please's release PR stays unmerged from E's merge until F's, and E and F reach Play in
one release. F is the first release that conveys OSM-derived data, so the ODbL offer ships in F's
commit, not after it.

---

## 1. The gate: does Play deliver a pack to a build that cannot read network state?

### The question, in the form that can be measured

On the owner's phone (`132bc856`, API 36, asked first), a release-variant bundle from B's branch,
with `asset-delivery` as `implementation`, `ACCESS_NETWORK_STATE` removed from the merged manifest
by `tools:node="remove"` and the Tatry pack included, is uploaded to internal app sharing and
installed from its link. Asked for the Tatry, does the app reach Ready with the OSM date, draw the
map from the pack's path, and log no `SecurityException`? And did `ExtractionWorker` run, or did the
Play Store hand the pack over already extracted?

Then a second bundle, one pin newer, is uploaded to internal app sharing and installed over the
first from its link. What download size does Play report, and does the Tatry reach Ready again
without being asked? An internal-track install was taken not to update in place, so it was not
used here; it does (`RELEASING.md`), and R10 reads the refresh through it.

### The apparatus

- B's branch, built with `./gradlew bundleRelease -PmapPacks` and the upload key, twice: at the pin
  on `main` and at the newest pin Geofabrik serves on the day, each with the Tatry archive built by
  `build-map-data.py --region tatry` at that pin. Whether they cross the border depends on whether
  C has merged; A does not wait for C, and C does not wait for A. The second carries a higher `versionCode` by hand on the branch, never committed.
- **Two more hand edits on the branch, never committed**, because a release bundle has neither the
  debug section nor E's screen: tapping the empty state's line fetches the Tatry, and `RegionFiles`
  logs every `AssetPackState` under the tag `KozicaPack`, with its status, `bytesDownloaded()` and
  `totalBytesToDownload()`. Neither touches the manifest or a dependency, so the build stays
  release-shaped where the gate looks.
- Internal app sharing, enabled in the Console with the owner as an uploader. Each bundle goes up on
  the Console's page through Claude in Chrome; two uploads do not earn a script.
- **Each phone set up for the links.** The Play Store's *Internal app sharing* switch on (Settings ›
  About, the Play Store version tapped seven times). Play re-signs every internal-sharing upload
  with its own test certificate, not the app-signing key, so a link cannot install over the
  internal-track copy: that copy is uninstalled first, which deletes its data, so anything in the
  owner's install that matters is exported first (ADR-0005). Going back to the internal track after
  A is another uninstall. Who may open a link is set on the Console's *Internal app sharing* page,
  *Uploaders and testers* tab: "Anyone you shared the link with can download", the default, or an
  email list, which then names the test phone's Google account for R2. Google's help also says a
  tester needs access to the app's store listing, and Kozica is on no track but testing ones, so
  if R2's link refuses that account, it goes on the internal testers' list too.
- The owner's phone on Wi-Fi, because internal app sharing installs through the Play Store app. The
  test phone stands in for nothing here except R2, the second API level.
- Logcat filtered for `WM-WorkerWrapper`, `ExtractionWorker`, `AssetPack`, `KozicaPack` and
  `SecurityException`; `dumpsys jobscheduler` for the app's jobs; for the refresh, the bytes the
  Play Store app's UID receives, from `dumpsys netstats detail` read before the second link is
  tapped and after the Tatry is Ready again, with the Play Store's auto-update off for that window
  so no other app's update is counted.

### The verdicts, decided now rather than argued about later

Verdicts 1 and 2 are taken across both installs, R1 and R3. The first install can pass with
`ExtractionWorker` never having run, and the refresh, where Play patches the pack while the app may
be closed, is where it is most likely to.

1. **On API 36, the Tatry reaches Ready and the map draws from the pack after the first install and
   again after the refresh, and no `SecurityException` is logged in either, whether or not
   `ExtractionWorker` ran.** → The strip stands, and B merges. Which of the two paths delivered the
   pack each time is recorded, because it says whether this question can return with a later
   library version.
2. **Extraction fails for want of `ACCESS_NETWORK_STATE`, in either install.** → Phase 1 § 3's
   verdict 2, unchanged: no workaround in code. The owner chooses between a narrow ADR-0008
   amendment that allows `ACCESS_NETWORK_STATE` alone, since it opens no connection and changes no
   Data safety answer, and a different delivery for map data. B does not merge until then; C and D
   go on.

And for the refresh's size, R3:

3. **The pack arrives, but the refresh costs close to the whole pack.** → The format question goes
   back to the owner before F, as phase 1 § 4's verdict 2 says: determinism first (`--check-determinism`
   at both pins), then the layout. A refresh that costs the whole pack every release is not a reason
   to stop the phase, but it is a reason to know before the first user pays it.

**The number that decides is the Play Store app's received bytes** across the second install, from
`dumpsys netstats`. The update sheet may show the APK's update alone, and a completed pack's
`bytesDownloaded()` may count the whole pack rather than the patch; both are recorded beside it. The
netstats figure includes the APK's patch, so it overstates the pack's, which errs towards verdict 3.

"Close to the whole pack" is above 25 % of the Tatry pack's compressed size. The baseline is a
bsdiff between the two archives actually uploaded, `build-map-data.py --region tatry --patch-from
<the first bundle's pin file>` at the second bundle's pin: phase 1's R6 measured the thin schema of 1 MB, and phase 2's 7.3 MB schema has
never had one. Play is free to do worse than bsdiff; it is not free to do forty times worse without
that being the finding. Internal app sharing is not documented to patch as a track does, so a
number above 25 % goes to the owner with that said. Confirming it on the closed track, where an
install updates in place, needs a release that carries packs, which is F; so the owner chooses
between reopening the format now and reading F's first data refresh there before reopening it.

### The verdict, taken *(date)*

**Verdict 1, 2026-10-20** (R1, the owner's phone, API 36): Play delivered the Tatry to the release
build with `ACCESS_NETWORK_STATE` stripped, extracted it through the library's own services, and
the map drew it; nothing was thrown. B merges. What else the day found, R2 and R3 on the test phone among it:

- **Play delivers a pack to the release build with `ACCESS_NETWORK_STATE` stripped.** The Play Store
  extracts through the app's own `AssetPackExtractionService`; no `ExtractionWorker` ran. Below API 34
  that is not the gate's answer; R1 read the same path on API 36, with the `dataSync`
  `ExtractionForegroundService` beside it. Neither phone ran an `ExtractionWorker`, so WorkManager's
  missing network-state permission is never asked for on this path.
- **A Region that arrives while the map shows the overview is not seen.** The camera stays where it
  was, and at the overview's zoom the archive draws nothing, so the map looks empty until a restart
  fits it to the Region. E moves the camera to a Region on its first download.
- **The refresh: verdict 3, decided by the owner the same day.** Internal app sharing downloaded the
  refreshed pack whole, and the app's own APKs whole with it, so its number says how internal sharing
  delivers rather than how the format patches. The format stands; the refresh is read again on F's
  first data refresh through the closed track (§ 6), and the format is reopened only if it is whole
  there too. R10 is that reading, taken on the internal track rather than the closed one: the Tatry
  came whole again, six Regions as patches, 33 minutes after the upload. R11 read the next one two
  hours after its upload, at the first request, and every pack came whole. The owner reopened
  nothing on 2026-11-01: the format stays ([`DOD.md`](DOD.md)).

## 2. The library in `main/`

Phase 1 § 5's first two bullets are this checkpoint, and stand as written there. What this phase adds:

- **`RegionFiles` stops knowing the Tatry.** It takes the list of pack names and exposes one `Flow`
  of every Region's state, from one `AssetPackStateUpdateListener` and one `getPackStates` call over
  the whole list. `fetch` and `removePack` take a Region. It moves to `main/`'s `data/regions/`.
  `RegionSource.kt` stays a seam with two halves, as the position override's is: the release half
  passes `RegionFiles` through, and the debug half wraps it with the fallback to a hand-pushed
  `files/regions/<id>.pmtiles`.
- **The Region list is the polygons.** `scripts/regions/<id>.poly` names every Region. Gradle reads
  that directory: `settings.gradle.kts` includes one pack module per polygon, `region_<id>`, from one
  shared build script rather than nine copies, and `app/build.gradle.kts` adds them all to
  `assetPacks` under `-PmapPacks`. The app's list is generated from the same directory into a Kotlin
  source, in the order a hiker reads the mountains, § 4's table: west to east, with Góry
  Świętokrzyskie last, each entry naming `R.string.region_<id>`. One list, so a Region cannot exist
  in the build and not in the app, and one without a name does not compile. The order is a list of
  ids in the generator, not a field in the polygon, and the build fails when that list and the
  polygons disagree.
- **`WaitingForConfirmation` splits in two, with one handler.** `RegionFiles` maps two Play statuses
  to it today, and they mean different things: `WAITING_FOR_WIFI` holds a large download over mobile
  data for the user's yes; `REQUIRES_USER_CONFIRMATION` means the installed app did not come from
  Play. So they become `WaitingForWifi` and `NeedsConfirmation`, each with its own copy in § 5.
  `AssetPackManager.showConfirmationDialog` answers both and needs an `ActivityResultLauncher`, so
  the Regions screen registers one; the map's state row only names the state. Confirming
  `NeedsConfirmation` may make Play reinstall the app and cancel every request, so the row falls
  back to Download.
- **The Console declaration** for `FOREGROUND_SERVICE_DATA_SYNC`: the library's extraction service,
  started by the Play Store's delivery, user-initiated by a download. The Console offers the form
  only once a bundle declaring the permission reaches a track, so it is filled, through Claude in
  Chrome with the owner's OK, when E and F's release is uploaded, and recorded in
  `play-app-content.md`.

## 3. Across the border

- **Three extracts, one moment.** `scripts/osm-data.pin` becomes one line per extract,
  `<geofabrik name> <sequence>`, for `poland`, `slovakia` and `czech-republic`. Geofabrik numbers
  each extract's replication on its own: on 2026-10-18 at 20:25:06Z Poland and Slovakia were at 4942
  and Czechia at 4943. So a pin is three numbers, and the build **fails unless all three updated
  headers carry the same `osmosis_replication_timestamp`**. That timestamp is the data's date, and
  the one the map shows.
- **Each extract is brought to its sequence as Poland's is today**, from the newest dated extract
  before it that Geofabrik still serves with its md5, by `pyosmium-up-to-date --end-id`, then checked
  against its line of the pin; the build fails when Geofabrik keeps no such extract, or not the diffs
  from it (ADR-0007's 2026-10-26 amendment). Sizes read 2026-10-20: Poland 2.11 GB, Czechia
  0.95 GB, Slovakia 0.35 GB, so a release build downloads about 3.4 GB, not 2.1.
- **Each country is cut into every Region before anything is merged**, one `osmium extract` pass per
  Region, each keeping the country's replication header. Not one `--config` pass for all nine:
  osmium keeps ID sets per polygon sized to OSM's largest IDs, and Poland cut for nine Regions in
  one pass peaked at 29.4 GB (`smart`; 17.8 GB even with `simple`), against 4.0 GB for one and
  11.7 GB for three, read 2026-10-20 with `/usr/bin/time -f %M`. A hosted runner has 16 GB. The nine
  passes over Poland cost about 80 s more than one, and Slovakia's and Czechia's smaller extracts
  add little: a full build of all nine Regions at the current pin took 198 s, with a 4.1 GB memory
  peak. With `--discard-extracts`, which `publish-play.yml` passes, the country's dated and updated
  extracts are deleted as soon as its cuts are written, so the build's disk peak is Poland's two,
  about 4.2 GB, not three countries' full extracts and a merged copy. On a workstation they stay, as
  today, a cache re-verified on every run.
- **`osmium merge` then joins each Region's three cuts.** Geofabrik's extracts overlap a little at
  the borders, and `merge` keeps one copy of an object present in two.
- **The merged file carries the shared timestamp, and no sequence.** The three sequences differ, so
  no one of them is the data's. `osmosis_replication_timestamp` is written with `--output-header`,
  as phase 1 § 4's edit does, or planetiler would read no date, and the app reads only that
  (`planetiler:osm:osmosisreplicationtime` in `Regions.kt`). `tile()`'s check that the cut kept the
  extract's header moves from the sequence to the timestamp.
- **Determinism is re-read.** `--check-determinism` builds twice from the merged cuts. A merge whose
  output order depended on input order would be the stray timestamp ADR-0007 warns of.
- **A data refresh is still one `fix:` commit** that moves the pin: now three numbers, one moment.
- **The profile is unchanged.** Slovakia's and Czechia's Trails use the same four colours and the
  same `osmc:symbol` grammar; R5 reads the Tatry's Slovak side off the screen to prove it.

## 4. Nine Regions

Decided with the owner, 2026-10-20. Ids are the pack names, ASCII and underscores; names are
Polish, `translatable="false"`.

| Id | Name | Ranges | Crosses into |
| --- | --- | --- | --- |
| `sudety_zachodnie` | Sudety Zachodnie | Karkonosze, Góry Izerskie, Rudawy Janowickie, Góry Kaczawskie | CZ |
| `sudety_srodkowe` | Sudety Środkowe | Góry Stołowe, Sowie, Wałbrzyskie, Kamienne, Bystrzyckie, Orlickie | CZ |
| `sudety_wschodnie` | Sudety Wschodnie | Masyw Śnieżnika, Góry Bialskie, Złote, Opawskie | CZ |
| `beskidy_zachodnie` | Beskidy Zachodnie | Beskid Śląski, Mały, Żywiecki, Makowski | CZ, SK |
| `gorce_pieniny` | Gorce i Pieniny | Gorce, Pieniny, Beskid Sądecki, Beskid Wyspowy | SK |
| `tatry` | Tatry | the Tatry, with Podhale's edge to Zakopane | SK |
| `beskid_niski` | Beskid Niski | | SK |
| `bieszczady` | Bieszczady | | SK; not UA |
| `gory_swietokrzyskie` | Góry Świętokrzyskie | | — |

- **Polygons are coarse and generous**: tens of points, a few kilometres past each range's last
  Trail, overlapping their neighbours by about as much. Each is drawn on the range's own map, not
  derived from OSM boundaries, and committed as `scripts/regions/<id>.poly`.
- **`tatry.poly` is redrawn**, across the border. Phase 1's R5 found its Slovak half empty because the
  extract stopped there; after C it is not.
- **Each Region's archive is measured** (R6) and must stay under ADR-0007's 1.5 GB; at the Tatry's
  7.3 MB none is expected near it, and the build's check stays the guard.
- **The debug map row "Download Tatry"** becomes the Regions screen, in E. The debug section keeps
  the pack states as text, for readings.

## 5. The Regions screen

- **A `Regions` NavKey** and `ui/regions/`, one `ViewModel`, one immutable UI state: every Region with
  its state and its download size. Reached from a download button on the map, beside Settings, and
  from the empty state, which gains the button phase 2 left for this phase ("Choose Regions"), and
  from a "Regions" row in a new *Map* section at the top of Settings (decided with the owner,
  2026-10-20).
- **A row** is the Region's name, then its state: the size to download; downloading with a percent;
  waiting for Wi-Fi, with the button that opens Play's confirmation; needing confirmation, with the
  same button; update in progress; ready, with its OSM date, phase 2's "OSM data: %1$s"; failed,
  with Retry. Download or Remove sits at the row's end. The size is
  `AssetPackState.totalBytesToDownload()`, which Play reports before anything is fetched; a build
  with no Play session (`installDebug`) has none, and the row shows the name alone.
- **"Confirm" when nothing awaits confirmation** asks for the Region again: the state shown was
  stale, and the tap meant "download it".
- **"Download all"** fetches every Region neither ready nor already on its way (not downloaded, or
  failed), in one `fetch` call, and says how many and how large: "Download all — %1$d Regions,
  %2$s". While any of their sizes is unknown it drops the size rather than show a partial sum. It
  is absent when nothing is left to fetch.
- **The map goes to a Region on its first download** (§ 1's verdict): it remembers which Regions
  it has taken in, and a Region ready since is an arrival, whether the download ended on this
  screen or while the app was closed. The camera moves only when its centre lies outside every
  Region that was already ready, so the user is not looking at downloaded data, and then fits **one**
  arrival, the first in `REGION_NAMES` order, never their union: the union of two Regions is the
  overview that draws nothing. "Fits" means the arrival's middle at its fitted zoom but **never below
  zoom 10**, the Trails' minimum: a whole Region fits at about 7.3 to 8.1 on a phone, where R7 found the
  archive draws borders and little else. So "Download all" on screen moves once, to the first Region that
  finishes, and stays, because the centre is now inside a ready Region; off screen it fits one
  Region. An arrival is remembered once the camera has moved to it, or straight away when the
  camera stays, so a process killed before the map returns still moves it. Only a Region not
  downloaded is forgotten, at once, so one downloaded again moves the camera again and an update
  passing through "Updating" is no arrival.
- **The first launch asks which Regions to download** (decided with the owner, 2026-10-20, after
  E shipped): a welcome screen in the map's place, every Region with a box and its size, "All
  Regions" over them, nothing ticked. A Region already ready or on its way, as after an update from
  0.3.0, shows ticked and fixed. "Download" fetches the ticked ones in one call and opens the Regions
  screen, which shows their progress and Play's confirmation; "Not now" opens the map. Either answer
  stores `onboarding_done`, so the screen never returns; one line names the map's button and Settings'
  row as the places to change it later. With every Region already ready or on its way there is
  nothing to ask: once the Regions are read, `onboarding_done` is stored and the map opens.
- **Remove asks first.** A Region removed on a ridge with no signal does not come back until there is
  signal. The dialog says that, in one line, and Remove is its confirming button. Download asks
  nothing: Play's own confirmation covers mobile data.
- **The map's button and the empty state** read the same `RegionSource`, so the empty state's line
  is still phase 2's: "Map data is being updated." during an update, "No Regions downloaded yet."
  otherwise, now with the button under it.

Copy: "Regions", "Map" (the Settings section), "Choose Regions", "Download", "Remove", "Retry", "Download all — %1$d Regions,
%2$s" (a plural) and its sizeless twin "Download all — %1$d Regions", "Downloading — %1$d%%", "Waiting for Wi-Fi", "Download over mobile data",
"Play needs your confirmation", "Confirm", "Updating", "Remove %1$s?", "You won't see this Region's
map until you download it again, which needs a connection.", "Download failed". The welcome screen's: "Welcome to Kozica", "Download the mountains you need before you go.
After that the map works with no signal.", "All Regions", "You can change this any time with the
download button on the map, or in Settings → Regions.", "Download %1$d Regions, %2$s" (a plural) and
its sizeless twin, "Not now". The Region names are not copy.

## 6. Play delivers it

Phase 1 § 5's last three bullets are this checkpoint, with C's three extracts and D's nine packs:

- **The data build joins `publish-play.yml`** before `bundleRelease`: `osmium-tool` from apt and
  pyosmium 4.2 or newer from pip, then `build-map-data.py --discard-extracts` for every Region at
  the pin, `--check-determinism` on one of them (the Tatry, which changes in every refresh), then
  `bundleRelease -PmapPacks`, which fails when an archive is missing. A release whose inputs match
  the last version's record skips the build and its check (ADR-0007's 2026-10-25 amendment). A
  hosted `ubuntu-latest` runner guarantees 14 GB of SSD and a job may run six hours, so the build
  logs the runner's free disk after each country's cut, and R9 records the peak and the minutes.
- **The refresh size is read here**, on the closed track, where an install updates in place: the
  first release after F that only moves the pin, installed over F's, read as R3 was (§ 1's verdict).
  R10 and R11 read it on the internal track instead, which updates in place too, 0.14.1 → 0.14.2
  and 0.14.2 → 0.14.3; the owner took their verdict on 2026-11-01: the format stays.
  Gate A's CI run spent 1 h 46 min on the data alone (R9), most of it fetching Poland. **No cache**,
  decided with the owner on 2026-10-20: ADR-0007's reasons stand, and two hours inside a six-hour
  limit, unattended, is cheaper than a second workflow keeping a cache warm. Narrowed on
  2026-10-25 by ADR-0007's amendment: a release with unchanged inputs reuses the last version's
  archives, and a full build now takes about 40 minutes.
- **The ODbL offer is the database itself.** Decided with the owner, 2026-10-20: the workflow
  attaches the nine `.pmtiles` to the release-please GitHub release, under ODbL, with
  `scripts/map/MAP-DATA-LICENCE.txt` beside them saying so, and the licences
  screen's ODbL entry links to the release page for the app's own version. That offers the
  derivative database (ODbL §4.6) without licensing the code, whose `LICENSE` stays all rights
  reserved. **The archives are attached before *Upload to internal testing***, with `gh release
  upload --clobber` so a retry can attach them again, and the job gains `contents: write`, which a
  release upload needs and the workflow's `contents: read` refuses. A failed attach then stops the
  run before Play sees the bundle: a release on Play without its offer on GitHub is the one outcome
  the house rule exists to prevent.
- **The listing and the policy say where the data comes from.** The privacy policy gains one
  sentence: map data is downloaded by the Play Store app, under Google's terms; Kozica opens no
  connection. The listing's description names both sides of the border.

---

## Tests

None for a safety value: nothing in this phase computes one (`PLAN.md` rule 3). Two guards that are
not unit tests stand where tests might be expected:

- **The pin's one moment** is `build-map-data.py`'s check that three headers carry one timestamp. A
  build at three different moments would draw a border where Trails end on one side a day early.
- **The Region list's one source** is Gradle reading `scripts/regions/`. A Region with a polygon and
  no name string fails to compile, not at runtime.

## Documents this phase amends

- **`PLAN.md`**: phase 2b's paragraph, with this file: the polygons cross the border, and C exists.
- **ADR-0007**: a dated amendment with this file: the nine Regions, the three extracts at one moment
  and their pin, and the archives on the GitHub release as the ODbL offer.
- **`CONTEXT.md`**: Region's definition, with this file: it crosses the state border where the ranges
  do, and stops at Ukraine's and Germany's.
- **`DOD.md`**: checkpoint E's item points here, with this file.
- **With B**, as phase 1 § 5 lists: `CLAUDE.md`'s stack rows for Play Asset Delivery and "Nothing
  scheduled", ADR-0003's amendment, `PLAN.md`'s "Reminders and scheduled work",
  `play-app-content.md` § *Foreground service permissions*, and `aab-permissions.py`'s four EXPECTED
  rows. `CLAUDE.md`'s layout: `region_tatry/` becomes one `region_<id>/` per polygon, and
  `data/regions/` loses "each variant supplies the states". ADR-0008's list of libraries known to
  merge a forbidden permission gains a third, the asset-delivery library with WorkManager, naming
  A's verdict.
- **With D**: `.gitignore`'s two `/region_tatry/` lines become `/region_*/src/main/assets/` and
  `/region_*/build/`, so no Region's archive can be committed (ADR-0007).
- **With F**: `CLAUDE.md`'s OSM house rule names the offer this phase chose; `docs/privacy-policy.md`
  and `docs/store-listing.md` per § 6; `RELEASING.md` gains the data build and the release assets.

## Known gaps, deliberately

- **The test phone cannot answer the gate.** API 30 is below the level where JobScheduler checks
  `ACCESS_NETWORK_STATE`, so A is the owner's phone's, and waits until it is free.
- **A release that moves the pin downloads 3.4 GB from Geofabrik.** ADR-0007's alternative of a
  cache still fails for the same reason, a tag ref; a release with unchanged inputs reuses the last
  version's archives instead (its 2026-10-25 amendment).
- **Ukraine's and Germany's sides are missing.** Named above, with their reasons.

## Readings

`PLAN.md` rule 3: filled in as the phase runs, from the device or the build, with the command that
read it. A reading on the owner's phone waits until the owner says it is free. A reading through
Play needs Wi-Fi on; the test phone's stays on, per `CLAUDE.md`'s test-phone bullet.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The gate: the Tatry through internal app sharing on API 36 reaches Ready and draws, no `SecurityException`, and whether `ExtractionWorker` ran | `adb -s 132bc856 logcat -d \| grep -E 'WM-WorkerWrapper\|ExtractionWorker\|AssetPack\|KozicaPack\|SecurityException'`; `adb -s 132bc856 shell dumpsys jobscheduler \| grep io.github.srednimax.kozica`; `adb -s 132bc856 exec-out screencap -p > r1.png` | 2026-10-20, owner's phone over wireless adb, API 36, HyperOS, `versionCode` 104 from the gate's CI run, installed from its internal-sharing link (no internal-track copy was installed, so nothing was lost). After `pm clear`, a tap on the empty state: `KozicaPack` read DOWNLOADING 0 → 18,735,456 bytes in about 1 s on Wi-Fi, TRANSFERRING, COMPLETED. The Play Store extracted through the app's `AssetPackExtractionService` and its `dataSync` `ExtractionForegroundService`; no `ExtractionWorker` or job ran. No `SecurityException` or `FATAL` in the app. After a restart the map drew the Tatry from the pack in dark mode, both sides of the border, "OSM data: Oct 11, 2026". **Verdict 1** |
| R2 | The same install on the test phone, API 30: the second API level, below JobScheduler's check | as R1, with `-s mnbm7hw8ivdqzx9l` | 2026-10-20, test phone, API 30, build `versionCode` 104 from `gate/a` (pin `poland 4935`, `slovakia 4935`, `czech-republic 4936`, all nine packs), built and uploaded by the gate workflow's CI run, installed from its internal-sharing link (installer `com.android.vending`). A tap on the empty state fetched the Tatry: `KozicaPack` read PENDING, DOWNLOADING 2.1 → 10.5 → 18.7 MB in about 6 s, TRANSFERRING, COMPLETED, 18,735,456 bytes. The Play Store bound the app's own `AssetPackExtractionService` to extract it; WorkManager initialised but no `ExtractionWorker` ran. No `SecurityException` or `FATAL`. After a cold start the map drew the Tatry from the pack, both sides of the border, "Dane OSM: 11 paź 2026". Below API 34, so not the gate's answer |
| R3 | *(phase 1's E1)* The bytes a one-pin refresh of the Tatry costs through Play, against a bsdiff of the same two archives; the pack back to Ready unasked; no `SecurityException` | internal app sharing, the second link over the first; `adb -s 132bc856 shell dumpsys netstats detail` for the Play Store's UID before and after; the update sheet and `KozicaPack`'s `bytesDownloaded` beside it; `python3 scripts/build-map-data.py --region tatry --patch-from <the first bundle's pin file>`; R1's logcat filter | 2026-10-20, test phone (API 30, not 36: the refresh's size does not depend on the API level), `versionCode` 105 at pin `poland 4942`, built locally on the PC and uploaded by hand, installed over 104 from its link with "Aktualizuj", data kept. The first attempt minutes after the upload failed in Play with "Request to PGS failed because all packs are unavailable"; four minutes later it went through. **Play downloaded every file whole**: Finsky's log lists the base APK at 4,044,621 bytes, `config.arm64_v8a` 11,100,499, `config.xxhdpi` 61,268, `config.pl` 20,627 and `region_tatry` 18,751,840, against a bsdiff of the same two Tatry archives of 57,373 bytes (0.47 % of the newer one zstd, `build-map-data.py`'s measure); `wlan0` received 36,934,261 bytes across both attempts. The pack came back Ready without being asked, "Dane OSM: 18 paź 2026", no `SecurityException`. Internal app sharing patched nothing, not even the APK, so the number says how internal sharing delivers, not how the format patches: **§ 1's verdict 3, with its caveat, goes to the owner**; the owner took it the same day (§ 1) |
| R4 | *(phase 1's E2)* The release bundle declares none of the FORBIDDEN permissions with the library and WorkManager in, and exactly the four new EXPECTED rows | `./gradlew bundleRelease && python3 scripts/aab-permissions.py` | 2026-10-20, B's branch with `asset-delivery` 2.3.0 as `implementation`: before the rows, exactly four NEW, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `RECEIVE_BOOT_COMPLETED` and `WAKE_LOCK`, and none of the 8 FORBIDDEN, so WorkManager's `ACCESS_NETWORK_STATE` merges into the main manifest's removal. With the four EXPECTED rows: "7 permissions, all accounted for" |
| R5 | Across the border: the three extracts at one moment, the merged build deterministic, and the Tatry's Slovak Trails drawn | `python3 scripts/build-map-data.py --check-determinism`; push the archive; `adb exec-out screencap -p > r5.png` over Štrbské Pleso | 2026-10-20, pin `poland 4942`, `slovakia 4942`, `czech-republic 4943`, one moment 2026-10-18T20:25:06Z: the dated extracts already at their sequences, so no update ran. Two builds of the Tatry byte-identical, 11,594,670 bytes (7,544,813 zstd -19) against phase 2's 7,274,738 for Poland alone, sha256 `c628cb98…376a`; the metadata's `osmosisreplicationtime` is the moment, its sequence 0. Tile 14/9104/5618, wholly Slovak, at Štrbské Pleso: Trails in green, blue, red and yellow. Of the merged cut's 298 hiking relations, 7 reach the colour fallback, none for a Slovak colour word: 3 stages of Via Slovakia, a route over other Trails' marks, 1 Slovak educational path and 2 Polish relations with neither tag, and the E3's `:blue::E3:yellow`. Screen, 2026-10-20, test phone, debug build with that archive pushed: Štrbské Pleso, Chata pod Soliskom and Predné Solisko drawn with Slovak names, the red Tatranská magistrála, blue, yellow and green Trails in their colours, Via Slovakia's "other" grey side by side with the red on their shared way, "OSM data: Oct 18, 2026"; logcat shows no `SecurityException` or `FATAL` |
| R6 | Every Region's archive size, raw and zstd, at one pin | `ls -l`, `zstd -19 -c … \| wc -c` per archive | 2026-10-20, pin `poland 4942`, `slovakia 4942`, `czech-republic 4943`, raw / zstd -19 in bytes: `beskid_niski` 44,209,100 / 29,374,888; `beskidy_zachodnie` 67,686,247 / 44,062,424; `bieszczady` 37,415,962 / 25,747,943; `gorce_pieniny` 41,287,776 / 27,209,188; `gory_swietokrzyskie` 24,567,680 / 15,304,654; `sudety_srodkowe` 46,683,625 / 30,262,606; `sudety_wschodnie` 25,600,148 / 16,929,555; `sudety_zachodnie` 44,815,090 / 29,265,529; `tatry` 18,740,013 / 12,260,500. In all 351,005,641 raw and 230,417,287 zstd; the largest, Beskidy Zachodnie, is 4.5 % of the 1.5 GB limit. The Tatry grew from R5's 11.6 MB to 18.7 MB with Pasmo Gubałowskie, Podhale's edge and the Orava seam. `./gradlew bundleDebug -PmapPacks` packs all nine, each `region_<id>` with its own manifest |
| R7 | The Regions screen on the phone: one Region downloaded, removed with its dialog, then "Download all" to every Region ready, from a build Play delivers | F's release on the internal track, uninstalled and reinstalled from Play; `adb exec-out screencap -p` per state | 2026-10-20, test phone, API 30, 0.3.0 (`versionCode` 116) installed from the internal track over Wi-Fi after the owner joined the test (the internal-sharing copy, signed by another key, uninstalled first). The Regions screen read Play's sizes before any fetch: 31, 32, 18, 68, 29, 13, 31, 27, 25 MB, "Pobierz wszystkie — 9 regionów, 275 MB". The Tatry: "Pobieranie — 0%" to ready, "Dane OSM: 18 paź 2026", in about 3 s, and the map moved to it on return. Removed through "Usunąć Tatry?" and back to "13 MB". "Download all": one fetch, up to five Regions downloading at once, all nine ready 28 s after the tap, the button gone. No `SecurityException`, `FATAL` or `ExtractionWorker` in logcat. **One defect:** after "Download all" the camera fitted Sudety Zachodnie whole, about zoom 7.6, which draws borders and park outlines only; the fit gains a floor at zoom 10 (§ 5), and the debug build with the same nine archives then opened on the Karkonosze's Trails. Wi-Fi off again after |
| R8 | The ODbL offer: the release page holds nine archives whose hashes match the bundle's packs | `gh release view v<x.y.z> --json assets`; `sha256sum` against `bundletool`'s extracted packs | 2026-10-20, v0.3.0 from `publish-play.yml` run 39462967609: the release holds the nine `.pmtiles` and `MAP-DATA-LICENCE.txt`; each archive's sha256 matches its pack in the run's `app-release.aab` (`unzip region_*/assets/*.pmtiles`), e.g. `tatry` `f4b7d20e…`; sizes byte-identical to R6, the same pin |
| R9 | The data build in CI: the runner's free disk at its peak, and its minutes | the free disk `build-map-data.py` logs after each country's cut; the step's duration in F's first `publish-play.yml` run | Early, 2026-10-20, from gate A's CI run 39425215860 (the week-old pin, `--discard-extracts`, a hosted `ubuntu-latest`): the data step took 1 h 46 min, of which Poland's download, update and cut 81 min, Slovakia's 7 and Czechia's 14; free disk never fell below 85 GB. Locally, with the extracts cached, the same build takes about 3 min. F's own run, 2026-10-20, run 39462967609 for v0.3.0: the data step took 24 min 21 s (17:05:50 to 17:30:11 UTC), the Tatry built twice byte-identical; free disk 88.0 GB after Poland's cut, 89.7 after Slovakia's, 89.0 after Czechia's |
| R10 | § 6's refresh through a track: the first release that only moves the pin, installed in place over the release just before it, read as R3 was | test phone (API 30), Play installs from the internal track with "Aktualizuj"; the Play Store UID's (10026) `rb` from `dumpsys netstats detail` before and after; Finsky's `DS: enqueue` sizes from logcat; `bsdiff` and `zstd -19` on the two releases' `tatry.pmtiles` attachments; `KozicaPack` after launch | 2026-11-01. **0.14.1 (428) → 0.14.2 (431)**, pin `poland 4952`, `slovakia 4952`, `czech-republic 4953` → 4955, 4955, 4956 (#131), every pack held and complete before the update. Play would not deliver at first: a minute after the upload Finsky failed with "Request to PGS failed because app is unavailable" (status 6400), having planned every file whole; four tries from 10:32 to 10:46 failed with status 1010, "Delivery received non-OK response". The sixth request, after five refused (10:18 among them), 33 minutes after the upload, went through: 378,537,550 B on the Play Store's UID, 361,692,253 B queued. Both cover the APK and all ten packs together, so here the per-pack sizes in Finsky's `DS: enqueue` lines decide, not § 1's netstats. **Six Regions came small**: Beskid Niski 252,535 B, Gorce i Pieniny 100,771, Góry Świętokrzyskie 98,262, Beskidy Zachodnie 76,378, Bieszczady 73,744, Sudety Wschodnie 37,897, where the 10:18 try had planned them whole (Bieszczady 27,308,850; Beskid Niski it requested but never queued). Only a patch explains 38–253 KB: Finsky never calls them patches, but Play Core logged `PatchSliceTaskHandler` "Patching and extraction finished" for exactly these six and `ExtractChunkTaskHandler` for the three below and the relief; no bsdiff was taken for them. **Three came whole**, at exactly their 10:18 sizes: Tatry 13,240,881, Sudety Środkowe 32,395,687, Sudety Zachodnie 31,295,872, and so did the relief, 267,784,630 B, its `scripts/relief.pin` unchanged since 0.10.0. The APK and every split came whole too, Finsky logging "Can't download patch … didn't receive one in delivery response" for each; the APK and `config.arm64_v8a` were only smaller than at 10:18 (13,270,906 against 16,386,931 B, 2,942,063 against 11,129,234). The Tatry's bsdiff between the two attachments is 9,261 B, 0.075 % of the newer archive's 12,295,669 B zstd; Play sent the pack whole, above § 1's 25 %. Afterwards all ten packs read status 4, the map drew the Tatry with "Dane OSM: 31 paź 2026", and Kozica logged no `SecurityException`. Beside it, the same morning, about 8 minutes after 0.14.1's upload, **0.13.0 → 0.14.1**, two releases whose nine archives are byte-identical (the releases' sha256) and whose relief is the same `scripts/relief.pin`: Play queued all ten packs whole, 580,071,555 B (606,648,300 B on the UID), and the APK and every split with them. So Play patches this format's packs, but not every time, and this reading cannot say why: the three whole Regions were planned at exactly their 10:18 sizes, which fits "Play won't patch them" as well as "their patches were not ready 33 minutes after the upload", and 0.14.1's identical packs, sent whole 8 minutes after its upload, may be the same lag. **§ 1's verdict 3 on the Tatry alone, for an update 33 minutes after the upload; no later update was read, and whether that reopens the format goes to the owner**; the owner kept the format on 2026-11-01 ([`DOD.md`](DOD.md)) |
| R11 | R10 again with time to prepare: the next release that only moves the pin, installed in place over the release just before it more than two hours after its upload | as R10 | 2026-11-01. **0.14.2 (431) → 0.14.3 (435)**, pin 4955, 4955, 4956 → `poland 4954`, `slovakia 4954`, `czech-republic 4955` (#134), 2026-10-30 21:24:51 UTC: a day back, a moment no release had shipped, chosen because Geofabrik had nothing newer. Uploaded by `publish-play.yml` at 15:13; the update was started at 17:18, 2 h 5 min later, and Play delivered it at the first try. **Every pack came whole**: 562,789,368 B queued, 590,083,801 B on the Play Store's UID. The nine Regions came whole, from the Tatry's 13,240,055 B to Beskidy Zachodnie's 68,047,275, and so did the relief, 267,784,619 B, its bytes still unchanged, and the APK and every split, Finsky logging "Can't download patch" for each as in R10. Play Core logged `ExtractChunkTaskHandler` for all ten packs and no `PatchSliceTaskHandler` at all. The Tatry's bsdiff between the two releases' attachments is 7,019 B, against 12,291,691 B zstd. Afterwards all ten packs read status 4, the map drew with "Dane OSM: 30 paź 2026", and Kozica logged no `SecurityException` (the one in the log is Darek's debug build). **So two hours after the upload, at the first request, Play patched nothing.** Two accounts fit R10, R11 and 0.13.0 → 0.14.1 alike: Play builds a pair's patches some time after the upload, which R11 makes unlikely, since R10 had six of them by 33 minutes; or it builds them only once a device asks, so a first request gets whole packs. R10's patches came on its sixth request, after five refused, and R11 and 0.13.0 → 0.14.1 were first requests. Across the three, Play sent the unchanged relief whole every time, and patched packs once, six of nine. **§ 1's verdict 3 on every Region for a first request, and on the Tatry for R10's later one; whether a later request for a pair patches more is unread, and what it reopens is the owner's**; the owner kept the format on 2026-11-01 ([`DOD.md`](DOD.md)) |
| R12 | The first release with the relief at whole metres, installed in place over the release before it | as R10 | 2026-11-01. **0.14.3 (435) → 0.14.4 (439)**, the relief from relief-2026-10-25 to relief-2026-11-01 (#137), the OSM pin unchanged, so the nine Regions' archives are byte-identical between the two releases (their attachments' sha256). Uploaded at 20:13; Play answered "all packs are unavailable" at 20:13 and 20:28, and delivered at the third request, 20:42. **The relief pack came at 96,801,253 B**, against 267,784,619 B in R11. Every pack came whole again, the nine identical Regions included, with `ExtractChunkTaskHandler` for all ten and no `PatchSliceTaskHandler`; the APK and every split whole, as before. Six Regions came within 51 B of their R11 sizes, from the Tatry's 13,240,039 B; **three identical Regions came at other whole sizes**: Beskidy Zachodnie 68,047,275 → 47,046,711 B, now in two chunks, Bieszczady 37,527,972 → 27,306,186 and Gorce i Pieniny 29,127,526 → 41,550,247, 18.8 MB less between them. 373,463,996 B queued against R11's 562,789,368: of the 189 MB less, 171 MB is the relief and most of the rest those three Regions, Play sizing identical archives differently. The Play Store's UID read 368,977,124 B, 4.5 MB under what was queued, where R10 and R11 read about 5 % over. The two refused requests do not explain it: the count starts at the third request's baseline, each refused one moved the UID by under 64 KB, and neither log has an `onProgress` or `onSuccess`. This reading cannot say why it ran under. Afterwards all ten packs read status 4 and the map drew the shading with "Dane OSM: 30 paź 2026". **The relief costs every update 171 MB less; identical packs are still sent whole, and not always at the same size** |
