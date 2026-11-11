# Phase 2c — Every Region arrives with the app, and nobody has to pick one

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
amends [ADR-0007](adr/0007-osm-data-ships-per-mountain-range-as-play-asset-packs.md)'s delivery
decision, each Region an on-demand pack, in this file's commit, and undoes the user-facing half of
[`phase-2b.md`](phase-2b.md) § 5. [`CONTEXT.md`](../CONTEXT.md)'s **Region** was redefined with it:
the unit the map data is built and delivered in, which the user never picks.

**What closing this phase means.** Today a fresh install opens on a welcome screen asking which of
nine Regions to download, and the map draws nothing until the user has chosen and Play has fetched
them; a Regions screen downloads and removes them one by one or all at once. After this phase every
Region is a **fast-follow** pack: Play fetches all nine by itself right after the install, without
the app being opened, and the app has no screen for choosing. The difference a device can be asked
about: a build installed from Play and never opened has had all nine packs downloaded by Play a few
minutes later, its first launch finds each one `COMPLETED` or `TRANSFERRING` rather than
`NOT_INSTALLED`, and it opens on the Tatry's Trails. The library enables its extraction services
only at runtime, so moving a pack into the app's storage may wait for that launch.

Decided with the owner on 2026-10-22, while phase 4 was being planned: relief for every Region
would grow the downloads by about 200 MB, and the owner chose to ship everything to everyone rather
than add relief to a per-Region choice. Kozica is on the internal track only, so nothing a stranger
installed loses a screen.

---

## 0. What this phase inherits

- **Phase 2b's packs and library stay as they are.** Nine `region_<id>` modules built by
  `gradle/region-pack.gradle.kts`, the asset-delivery library in `main/`, its four permissions and
  the `dataSync` extraction service. Only the pack's `deliveryType` changes, in one line.
- **`RegionFiles` already reads every state a fast-follow pack passes through.** Play reports a
  fast-follow pack with the same `AssetPackStatus` values as an on-demand one, and `fetch` accepts
  one that has not arrived, so the Retry and Play's confirmation dialog keep their code.
- **What a device has proven and what it has not.** R7 proved Play delivers all nine on-demand
  packs to a Play install. Nothing has yet read a fast-follow pack, on either phone, or what an
  install that holds on-demand packs does when the update turns them fast-follow.
- **The map's camera is stored** (phase 2), and phase 2b § 5 moves it to a Region on its first
  download, remembered in `AppPreferences.readyRegions`.

## What is in, and what is deliberately not

**In:** `deliveryType` `fast-follow` for every Region pack. The map's progress line while any Region
has not arrived, with Retry and Play's confirmation. The first launch's camera on the Tatry. Deleting
the Regions screen, the welcome screen, the map's download button, Settings' *Map* section, the
camera's arrival rule and both stored keys that served them. The listing, the privacy policy and the
foreground-service answers that described a Region the user chose.

**Not in, and each with a reason rather than a phase:**

- **Removing a Region to free space.** About 560 MB on the phone is real on a small one, but a
  Remove brings back the per-Region choice this phase removes, and a map with holes in it. If
  support mail asks for it, it returns as its own change.
- **Install-time packs.** The data would be there before the first launch, but the store listing
  would show about 485 MB, Android wants about twice that free to install or update, and the
  difference over fast-follow is the minute after install.
- **A read-only list of Regions and their states.** It answers a question few users will ask, and
  the debug build's Settings already lists every pack's state.

**The tempting one** is keeping the Regions screen "for later", behind the progress line's Retry or
in Settings. It costs nothing to leave in, it seems. But every string on it stays inside the
translation gate, its states have to stay true to a delivery mode that no longer asks, and a later
phase adding Remove back would start from a screen built for choosing rather than freeing space.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **Every Region arrives by itself** — fast-follow, the progress line, the Tatry on first launch, the screens and keys deleted | `feat:` | — |
| **B** | **What the documents say** — the listing, its 0.6.0 notes, the privacy policy, the foreground-service answers | `docs:` | A |

**No gate.** Nothing here can veto the phase: if Play fetched nothing by itself, the app's own
`fetch` on the first launch (§ 1) still brings every Region, as phase 2b's did. R1 reads which of
the two did it, and R2 the update from 0.5.0's on-demand packs.

**A and B ship in one release.** B's listing describes A's behaviour, so its notes are written on A's
branch, as phase 3's were, and the release PR waits for B.

---

## 1. Every Region arrives by itself

- **`deliveryType.set("fast-follow")`** in `gradle/region-pack.gradle.kts`. Nothing else in the
  build changes: `-PmapPacks`, the data build and the archives on the GitHub release are as phase
  2b left them.
- **The app asks only for what was never asked for.** On the map's first collection of the
  Regions, a Region Play reports `NOT_INSTALLED` is fetched, all of them in one `fetch` call, as
  "Download all" did. That covers a fast-follow download Play has not started and a Region added to
  a later build, which Play fetches unprompted as part of the install anyway. **A failed Region, or
  one the user cancelled in the Play Store** (`FAILED`, `CANCELED`), is not fetched again behind the
  user's back: it waits for Retry, and Retry shows what it will download, "Retry — %1$s", once Play
  has reported the size of every failed Region.
- **The progress line** replaces the empty state, "No Regions downloaded yet." and its button, which
  go with `MapEmpty`. It shows while any Region is not ready, over the map rather than instead of
  it, since the Regions already ready draw beneath it:

  | State of the Regions not ready | Line | Action |
  | --- | --- | --- |
  | Any downloading, pending or not yet asked for | "Downloading maps — %1$d of %2$d" (ready of all) | — |
  | Any waiting for Wi-Fi | "Waiting for Wi-Fi" | "Download over mobile data", Play's dialog |
  | Any needing confirmation | "Play needs your confirmation" | "Confirm", Play's dialog |
  | Any failed or cancelled, none of the above | "Download failed" | "Retry — 68 MB" (or "Retry" while a size is unknown), one `fetch` for every failed Region |
  | Any updating | "Map data is being updated." (phase 2's) | — |

  The first matching row wins, in that order: the line names what the user is waiting on, so a
  download still moving is reported before one that failed, and Play's dialog before a Retry,
  because the dialog may be what the failure waits on. A Region not yet asked for counts as
  downloading: the first launch's `fetch` is already asking for it. With every Region ready the
  line is gone.
- **Without a Play session the line says "Download failed".** `installDebug` has none, so the
  first launch's `fetch` fails and every Region not hand-pushed to `files/regions/` is `Failed`: a
  debug build without all nine archives pushed shows "Download failed" and Retry over the map. That
  is true of that build, and accepted. `scripts/edge-to-edge.py`'s scenes show the line as it is;
  phase 4's gate reading pushes all nine archives, so it has none.
- **The first launch opens on the Tatry.** With no stored camera, the map centres on the Tatry at
  about zoom 11, where Trails, Huts and peaks draw, instead of phase 2's overview from the
  Karkonosze to the Bieszczady, which draws borders and park outlines only. After that the stored
  camera applies, as it does now.
- **The camera never moves by itself.** Phase 2b § 5's arrival rule goes, with
  `AppPreferences.readyRegions`: nine Regions landing within a minute of the install would
  otherwise move the map under a finger that is already panning it.
- **Deleted:**
  - `ui/regions/`, its screen, `ViewModel` and `RegionsUiStateTest`, and the `Regions` NavKey.
  - `ui/welcome/` with `WelcomeUiStateTest` and `WelcomeViewModelTest`. The welcome screen has no
    NavKey: what shows it is `Navigation.kt`'s `onboardingDone` gate and
    `MainApplication.onboardingDone`, and both go, with the `onboarding_done` key.
  - The map's empty state and download button: `MapEmpty`, `MapStart` and `ic_download`.
  - Settings' *Map* section and its row.
  - The camera's arrival rule: `MapUiState.arrival`, `MapScreen`'s arrival `LaunchedEffect`,
    `MapViewModelTest`'s arrival tests and `AppPreferences.readyRegions`.
  - `RegionFiles.remove`.
  - In every locale, every `regions_*` and `welcome_*` string, `map_no_regions`,
    `map_choose_regions` and `settings_map`; `TranslationTest.quotedPairs` is left empty. The
    strings the progress line keeps are renamed `map_retry`, `map_waiting_for_wifi`,
    `map_mobile_data`, `map_needs_confirmation`, `map_confirm` and `map_download_failed`, beside the
    new `map_downloading`.
  - In `scripts/edge-to-edge.py`, the `welcome` scene; `home-empty` becomes the wipe alone, and
    `SEED_WALK` loses its "Not now". Every seeded cell walks `SEED_WALK`, so without that edit the
    whole matrix breaks, not one scene.

  A stored key nothing reads any more is harmless on the internal track's installs, so neither key
  gets a removal step. `REGION_NAMES` and the Region name strings stay: the debug build's pack list
  and the progress line's count read them.
- **The first launch's pack states are logged**, under `KozicaPack`: each pack's state as the map
  first reads it, and each `fetch` the app sends with the packs it names. R1 reads a release
  installed from Play, which has neither a debug build's log nor its pack list, so the line is in
  `main/`; it carries no string and no screen.
- **The map's buttons** are Safety and Settings.

Copy: "Downloading maps — %1$d of %2$d". The other lines are phase 2b's, kept under new names:
"Waiting for Wi-Fi", "Download over mobile data", "Play needs your confirmation", "Confirm",
"Download failed", "Retry". New: "Retry — %1$s", with the size. "Map data is being updated." is
phase 2's.

## 2. What the documents say

- **The listing's description** loses "Download the ranges you need before you go": every range
  arrives with the app. The 0.6.0 notes say the map now arrives with the app, all nine Regions,
  with nothing to choose, and that the Regions screen is gone.
- **The website's front page**, `docs/index.md`, loses "once you have downloaded the ranges you
  need" from its opening line.
- **The privacy policy** says the map data is downloaded by the Play Store app after Kozica is
  installed, rather than "when you choose a Region".
- **[`play-app-content.md`](play-app-content.md)'s foreground-service answers**, all three, since
  each rested on a Region the user asked for and with fast-follow nobody asks. The declaration is
  still checkpoint E's, owed in the Console, and is filed with these:
  - **What the user sees:** the maps that come with the app, which the Play Store downloads right
    after the install, being made ready to use offline, with the system's foreground-service
    notification while it runs.
  - **Why it cannot wait:** the map has to be on the phone before the user goes where there is no
    signal, and Play delivers it while the app may be in the background; left for later, the map
    is missing on the trail.
  - **Type:** `dataSync`, which covers transferring to the device the data the app needs to work.

---

## Tests

- **The progress line's state** is a pure function from the Regions' states to one line and its
  action, with the order in § 1's table. A test per row, and one with a downloading and a failed
  Region proving downloading wins: the line names what the user is waiting on, and a Retry shown
  while a download is still moving misreports it.
- **What the first launch fetches**: not-installed Regions only, never one ready, downloading,
  updating, failed or cancelled. A cancelled one fetched unasked would undo the user's own decision,
  with no size shown.

## Documents this phase amends

- **ADR-0007**, with an amendment dated 2026-10-22: every Region a fast-follow pack.
- **`phase-2b.md`**, at its head: § 5's screens and the camera's arrival rule are undone here.
- **`CONTEXT.md`**: Region, already amended.
- **`docs/store-listing.md`**, **`docs/index.md`**, **`docs/privacy-policy.md`**,
  **`docs/play-app-content.md`**: § 2.
- **`PLAN.md`**: this phase's line and paragraph.
- **`CLAUDE.md`**: the Stack's Play Asset Delivery row and the Layout's `region_<id>/` line ("one
  on-demand asset pack per polygon"), with A.

## Known gaps, deliberately

- **Every install carries every Region, about 275 MB from Play and 351 MB on the phone**, before
  phase 4's relief. Someone who hikes only the Tatry pays for the Sudety. Accepted for an app with
  nothing to choose.
- **A data refresh reaches everyone's nine packs.** Before, it reached only the Regions a user had
  chosen. What a refresh costs through a track was phase 2b § 6's reading to take: 2b read it on the
  internal track (R10–R12), and the owner kept the format on 2026-11-01.
- **Opening the app in the first minute shows a part-drawn map.** The progress line says why.

## Readings

`PLAN.md` rule 3. A reading through Play needs Wi-Fi on the test phone, which stays on: `CLAUDE.md`'s test-phone bullet says why.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | A fresh install from Play has all nine Regions downloaded by Play before it is opened, each pack's state at the first launch (`TRANSFERRING` or `COMPLETED`, against `NOT_INSTALLED`), and which of Play and the app's own `fetch` brought them | install A's release from the internal track with the app never opened; after five minutes Play's downloads, `adb -s mnbm7hw8ivdqzx9l logcat -d \| grep -E 'AssetPack\|Finsky.*region_'`; then the first launch, `adb -s mnbm7hw8ivdqzx9l logcat -c`, open the app, `adb -s mnbm7hw8ivdqzx9l logcat -d -s KozicaPack` and `adb -s mnbm7hw8ivdqzx9l exec-out screencap -p > r1.png` | 2026-10-25, test phone, 0.6.0 (`versionCode` 163) from the internal track after `adb uninstall`, never opened. Play installed the APKs, then **requested every one of the nine packs by itself**, one request each, 4 s after the install was scheduled, and downloaded them whole (e.g. `region_sudety_srodkowe` 46,727,593 bytes). Opened four minutes later: `KozicaPack` read all nine **`TRANSFERRING` (3)**, downloaded but not yet unpacked, since the extraction services are enabled only at runtime, and no `fetch` line: Play brought them all, the app's own fetch was not needed. Six seconds after the launch the map showed the Tatry drawn at zoom 11 and no progress line, which by `mapProgress` means all nine were ready. **Pass** |
| R2 | The update from 0.5.0: an install holding some on-demand Regions and missing others ends with all nine, the ones it held kept rather than fetched again | 0.5.0 from the internal track with three Regions downloaded, then A's release over it; R1's logcat filter, and Finsky's byte counts per pack | 2026-10-25, test phone, from 0.3.0 rather than 0.5.0 (0.3.0 was what the phone held, and the internal track no longer serves 0.5.0): its own Regions screen removed six Regions, keeping the Tatry, Bieszczady and Sudety Zachodnie, then "Aktualizuj" in Play, the app not opened. Play's update requested all nine packs as part of the install, and the session fetched 263,280,666 bytes in 20 s on Wi-Fi: **the three held came as patches of 10,264, 10,363 and 10,362 bytes**, the six missing whole (24.6, 67.7, 44.3, 25.6, 46.7 and 41.3 MB, about 250.2 MB together), the rest the APKs. Opened after: all nine `COMPLETED` (4), no `fetch`, no progress line, the map's buttons Safety and Settings. The archives were byte-identical between 0.3.0 and 0.6.0, one pin, so the patches show only that an unchanged pack is not downloaded again on an update; what a data refresh costs stays owed (*Known gaps*, phase 2b § 6). **Pass** |
| R3 | The first launch mid-download: the Tatry, the progress line counting up, Regions drawing in place, the camera unmoved | R1's fresh install, the app unopened while Play fetches the packs; `adb -s mnbm7hw8ivdqzx9l shell screenrecord --time-limit 15` started 1 s before the launch, `adb -s mnbm7hw8ivdqzx9l shell monkey -p io.github.srednimax.kozica -c android.intent.category.LAUNCHER 1`, and its frames extracted at 2 fps with `ffmpeg`; R1's `KozicaPack` filter | 2026-10-25, test phone, uninstalled and installed afresh from Play at 16:47, the packs fetched by Play with the app unopened. `KozicaPack` read all nine `TRANSFERRING` (3) at 16:47:36.990. The recording's frames: 1.0 s the app starting; 1.5 s "Pobieranie map — 0 z 9" over an empty map; 2.0 s "1 z 9"; 2.5 s "2 z 9", the state border drawn; 3.0 s "3 z 9", Trails; 3.5 s "5 z 9", Zakopane and the peaks; 4.0 s "6 z 9"; 4.5 s "7 z 9"; 5.0 s "8 z 9"; 5.5 s the line gone and the map complete. The camera never moved: Zakopane sits at the same place on screen in every frame from 3.5 s. **Pass** |
| R4 | The same fresh install on the owner's phone, API 36, HyperOS | R1 with `-s 132bc856`, after saying so in chat | 2026-10-25, the owner's phone, API 36, HyperOS, English, dark mode. Its Play copy (0.2.0) was uninstalled; the owner's reinstall from Play brought **0.4.1**, because that phone's Play Store still held the app's old details for the same account the test phone uses, and opening Kozica's Play page refreshed them and updated it to 0.6.0 (`versionCode` 163) before it was ever opened. Play then requested all nine packs by itself at 19:32:42 and downloaded them whole. First launch, recorded with `screenrecord`: `KozicaPack` read all nine `TRANSFERRING` (3), no `fetch` line; "Downloading maps — 0 of 9" at 2.0 s, 2, 5 and 8 of 9 by 3.5 s, and at 4.0 s the Tatry drawn with the line gone, the camera unmoved. The `dataSync` unpacking runs on API 36 for fast-follow packs as it did for on-demand ones in phase 2b's R1. **Pass** |
