# Kozica

A hiking app for the Polish mountains, built on OpenStreetMap trail data.

- **Vocabulary:** [`CONTEXT.md`](CONTEXT.md) — use these terms in code and UI.
- **Decisions and why:** [`docs/adr/`](docs/adr/) — read before changing anything they cover.
- **What's still open:** [`docs/DOD.md`](docs/DOD.md) — the live checklist. **Read this one first.**
- **The arc, in phases:** [`docs/PLAN.md`](docs/PLAN.md) — sequence only; a phase being built gets
  its own `docs/phase-N.md`. Read the phase you're in, not the file.
- **How to work here:** invoke the `lean-loop` skill at the start of every session and keep it
  active. This repo is written before it is built — plans, ADRs and phase documents are long on
  purpose — so the waste is re-reading what is already in the transcript and testing code whose
  shape has not settled.
- **Commits & releasing:** [`docs/RELEASING.md`](docs/RELEASING.md). Commit subjects **must** be
  [Conventional Commits](https://www.conventionalcommits.org) (`feat:`, `fix:`, `feat!:`, `docs:`, …)
  — a `commit-msg` hook rejects anything else, and release-please derives the version and
  `CHANGELOG.md` from them. `versionName` and `versionCode` are automated; never hand-edit either.

Don't restate ADR reasoning here; link to it. This file is loaded every session and must stay short.

## Working with the person who owns this repo

<Their background, and what to explain rather than assume. For example: "Fluent in
JavaScript/TypeScript, new to Kotlin. Comment code where a Kotlin/Compose idiom has no direct JS
analogue — `Flow` vs promises/observables, `suspend` vs `async`, `remember`/`derivedStateOf` vs
`useMemo`, data class `copy()` vs object spread, sealed classes vs discriminated unions. Explain the
*why*, don't restate the line. Prefer explicit and readable over clever.

The Android **platform** is the other half of the gap and has no JS analogue at all: the manifest as
a declaration the OS reads before any code runs, permission models that differ per API level, process
death, and vendor ROMs overriding documented behaviour. Say when a constraint comes from the platform
rather than from this codebase — that is the difference between "we chose this" and "Android will not
let us do otherwise".">

## Stack

| Choice | Note |
| --- | --- |
| Kotlin, Jetpack Compose, Material 3 | Android only, `minSdk` 26 |
| **Navigation 3** (`androidx.navigation3`) | Replaces Navigation Compose 2.x — don't reintroduce the old one |
| Room via KSP | DAOs return `Flow`; schema evolution per ADR-0001 |
| Manual DI via `AppContainer`, **not Hilt** | Constructor injection is clearer at this size and easy to migrate later |
| Nothing of ours scheduled | Reminders left in phase 0; WorkManager is Play Asset Delivery's alone — ADR-0003's amendments |
| Photo Picker + `TakePicture` intent, **not CameraX** | Far less code; the system camera is fine here |
| Coil 3 images | Compose-native, local files only |
| On-device storage only | No backend. Backup per ADR-0005 |
| MapLibre Native (OpenGL ES) + PMTiles | Offline, every network permission stripped — ADR-0008, `docs/phase-2.md` § 2 |
| Play Asset Delivery | One fast-follow pack per Region and one for the relief, `ACCESS_NETWORK_STATE` stripped from its WorkManager — `docs/phase-2b.md` § 1, `docs/phase-2c.md`, `docs/phase-4.md` § 3 |

## House rules

- **OpenStreetMap data keeps its ODbL obligations.** Every map screen shows "© OpenStreetMap
  contributors" linked to https://www.openstreetmap.org/copyright, and the licences screen lists
  ODbL. Shipping a modified or filtered OSM extract, or publicly conveying maps produced from one, is
  public use of a derivative database: from that first release, offer the database or the script that
  builds it under ODbL (§4.6). Ours is the database itself: `publish-play.yml` attaches every
  Region's `.pmtiles`, with its ODbL notice, to the version's GitHub release before Play sees the bundle, and the licences
  screen links there (`docs/phase-2b.md` § 6). The app's own data (tracks, photos, notes) stays an independent
  database, never merged into OSM-derived data. We build OSM data ourselves from Geofabrik extracts,
  never from `tile.openstreetmap.org` or the public Overpass instance. The code's `LICENSE` stays all
  rights reserved.
- **An update never loses a user's data.** Any change to `APP_SCHEMA_VERSION` ships with its
  `MIGRATION_x_y` *registered in* `APP_MIGRATIONS`, the exported `app/schemas/*/N.json` committed, a
  `SchemaGateTest` assertion that the launch gate lets the upgrade through, and a migration test that
  proves the rows survive. `scripts/schema-gate.py` enforces the mechanical half in CI.
  **Every migration test opens the database directly and so walks past the launch gate** — that is
  how an app ships a refusal screen to every existing install with a green test run behind it.
  Verify an upgrade on the phone, not only in a test.
- **A branch merges with every shipped language complete.** Adding an English string does *not*
  redden your build — completeness is a merge gate (`scripts/translation-gate.py`), not a test, so
  copy is translated **once** rather than against draft wording and again after review. Everything
  else about a translation — format arguments, plural categories per CLDR, orphans,
  `translatable="false"` — is `TranslationTest` and holds continuously. Translate from **English
  only**, per [`docs/translator-brief.md`](docs/translator-brief.md).
- **Media paths in the DB are relative** and split by kind — `thumbnails/<uuid>.jpg`,
  `photos/<uuid>.jpg`, `documents/<uuid>.jpg` — resolved against `filesDir` at read time. Absolute
  paths change across installs and break restored backups; the split makes the backup export scopes a
  list of directories.
- **All image writes go through `media/MediaFiles.kt`** — it downsamples and re-encodes per kind,
  bakes in EXIF rotation, strips metadata (GPS included), and writes the file before the row.
  Bypassing it puts full-resolution bitmaps in memory and blows up any image grid.
- **Missing media renders as a placeholder, never a crash.** A restore may legitimately lack files.
- **DAOs return `Flow`**, screens collect it. Don't hand-roll refresh calls.
- **Enums with `TypeConverter`s, not loose strings**, for closed vocabularies. Store enums by
  **name, never ordinal**, so adding a value can't rewrite history.
- One `ViewModel` per screen, UI state as a single immutable data class.
- **Developer-only surface lives in `app/src/debug/`, never in `main/` behind `BuildConfig.DEBUG`.**
  With `isMinifyEnabled = false` a statically-false branch is still compiled into the release AAB —
  a hide, not a strip — and its strings are still inside the translation gate. The seam is
  `ui/settings/DebugSettings.kt`, one file per variant, with a no-op in `src/release/`.
- **Colours come from `MaterialTheme`, never literals.** The palette is generated from four seeds by
  `scripts/gen_scheme.py`; edit the seeds and regenerate, never a single role. **The exceptions
  are the map style's five Trail colours and the violet of a Bike route with no marking colour**, the signposts' own, and the launcher icon's two, in
  `art/mark.py`. The style's other colours and its
  sprites are tones of the scheme's palettes, generated by `scripts/map/gen_style.py` and
  `scripts/map/sprites.py`, so after a seed change run those two as well.
- **Spacing comes from `theme/Spacing.kt`.** Six steps on a 4dp grid; no screen invents a seventh.

## Commands

```bash
./gradlew assembleDebug          # build
./gradlew installDebug           # build + install on the connected phone
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented Room/DAO tests — needs a device
./gradlew lint
./gradlew spotlessApply          # format (the CI gate is spotlessCheck)
./gradlew assembleDebug -PreleaseShapedDebug   # debug id, R8 on — find minification bugs early

python3 scripts/project.py                      # what the toolchain thinks this app is called
python3 scripts/translation-gate.py --report    # what translations does this branch still owe?
python3 scripts/translation-gate.py origin/main # the gate itself — would this merge?
python3 scripts/schema-gate.py origin/main
python3 scripts/notes-gate.py
adb devices                                     # confirm the phone is attached
```

## Layout

```
app/src/main/java/<namespace>/
  MainActivity.kt, MainApplication.kt, AppContainer.kt, Navigation.kt, NavigationKeys.kt
               the app shell and Nav3 wiring stay at the package root, not under ui/ — they describe
               how the app hangs together rather than any one screen
  AppBackupAgent.kt   Android Auto Backup, taking control of its own file set (ADR-0005)
  data/        Room entities, DAOs, database, converters, migrations, the schema gate, preferences
  data/regions/ every Region's pack and state and what its PMTiles header says; the list itself is
               generated from scripts/regions/ into RegionList.kt
  data/backup/ export, restore, the manifest, the WAL checkpoint
  data/location/ the app's own reader of the phone's fixes; each variant wraps it in
               PositionOverride.kt
  data/routes/ the Trail network's reader, the router (ADR-0010) and the name index's search;
               each variant says where the network and the index come from in NetworkSource.kt
  media/       MediaFiles.kt — the single path for persisting images, kind-aware
  theme/       generated palette, type scale, spacing, the night-mode window half
  ui/          Compose screens + ViewModels, one package per area; ui/map/ is Home

app/src/debug/    the developer-only build: the sample-data seeder, Settings' debug section and its
                  strings, the schema-mismatch screen's continue button, SeedReceiver for the
                  capture driver, ui/map/ModeSwitch.kt with its ic_bike and strings, data/regions/'s RegionSource.kt, which falls back to an archive
                  hand-pushed to files/regions/<id>.pmtiles, and the position override:
                  PositionReceiver for scripted readings and Settings' "real position" row to
                  undo it (the long press plans a Route in every build); a relief hand-pushed to files/relief/ stands in for the
                  relief pack, ui/map/FrameLog.kt logs frame times and CameraReceiver sets the
                  camera, for phase 4's readings; a network and a name index hand-pushed to
                  files/network/ stand in for the base assets, and RouteReceiver plans a Route and logs it, for phase 5's
                  readings
app/src/release/  only the other half of each seam, so main/ can call it unconditionally: no debug
                  settings, no continue button, no frame log, no Mode switch and Hiking always, and the Regions, the relief and the
                  real position passed through
scripts/          the Python toolchain. project.py is the one place the app's identity lives
scripts/map/      the planetiler profile build-map-data.py runs, decode-tile.py, the style and
                  sprite generators, and gen_regions.py, which writes the Regions' outlines and
                  the mask outside them from scripts/regions/ (re-run it when a polygon changes)
scripts/regions/  one boundary polygon per Region
region_<id>/      one fast-follow asset pack per polygon in scripts/regions/, all built by
                  gradle/region-pack.gradle.kts; they join the build only with -PmapPacks
relief/           the relief's fast-follow pack, by the same build file: the archives
                  scripts/relief.pin names, which scripts/fetch-relief.py puts there.
                  scripts/build-relief.py builds them, once, on the owner's PC (ADR-0009)
art/             mark.py is the identity; both generators derive from it
```

## Versions

Pinned in `gradle/libs.versions.toml`: **AGP 9.0.1, Kotlin 2.3.20, Gradle 9.1.0, Compose BOM
2026.03.01**. `compileSdk`/`targetSdk` 36, `minSdk` 26, JDK 21 toolchain.

`compileSdk` stays at **36**: it is the combination AGP 9.0.1 validates against. Bump deliberately,
with a build to prove it.

## Environment notes

- **Two phones, so name one.** Gradle and adb both reach every attached device; set
  `ANDROID_SERIAL` (both honour it) or pass `adb -s <serial>`.
  - **The test phone**, `mnbm7hw8ivdqzx9l`: a wiped, debloated Redmi Note 8 Pro, Android 11 (API
    30), MIUI 12.5, Polish, on USB for good, in airplane mode with no SIM. Location stays on: turning
    it off raises a Play services popup. It has **no screen lock** since 2026-10-22,
    but MIUI still shows a swipe keyguard, and `screencap` captures that instead of the app;
    `svc power stayon usb` keeps it awake on USB, `adb shell input keyevent KEYCODE_WAKEUP` wakes it
    and `adb shell wm dismiss-keyguard` clears the keyguard. Drive it at will. The
    map draws on it, unlike the API 26 emulator. `connectedAndroidTest` runs on it with no prompt,
    but its closing uninstall takes the hand-pushed `files/regions/tatry.pmtiles` with it.
    **This PC's internet runs through it**: its USB tether outranks the PC's Ethernet (route
    metric 100 against 101), so its Wi-Fi stays on; switching it off takes the PC offline.
  - **The owner's phone**, `132bc856`: a Redmi Note 14 Pro 5G, API 36, HyperOS. Drive it without
    asking whenever adb reaches it (the owner's standing permission since 2026-10-20), but it is in
    use: say so in chat before a reading takes its screen, and restore any toggle afterwards.
- **MIUI optimisation is off on the test phone**, and that is what makes it tap-free. With it on,
  every adb install fails `INSTALL_FAILED_USER_RESTRICTED`, and the "Install via USB" fix wants a Mi
  account and a SIM. An update can turn it back on, so check before installing:
  `adb shell getprop persist.sys.miui_optimization` reads `false`. The toggle is at the bottom of
  Developer options, revealed by tapping "Reset to default values" a few times.
- **On the owner's phone (HyperOS)**, split-APK installs (`connectedAndroidTest`) prompt on the
  phone and a missed prompt fails the run with `INSTALL_FAILED_USER_RESTRICTED`; the workaround is
  to install both APKs plain and run the instrumentation directly:
  ```bash
  adb install -r -t app/build/outputs/apk/debug/app-debug.apk
  adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb shell am instrument -w <applicationId>.debug.test/androidx.test.runner.AndroidJUnitRunner
  ```
  **A brand-new `applicationId` is a different failure with the same error string**: HyperOS refuses
  the *first* install of a package outright, with no dialog to miss. Either enable USB installation
  in developer options, or `adb push` the APK to `/sdcard/Download/` and tap it in the file manager.
- **Xiaomi kills background work aggressively.** Anything that must run without the app on screen
  may need battery-optimisation exemption and **autostart**, and autostart is the load-bearing one:
  without it the ROM does not start the process for a broadcast at all. It has no appop, it is a
  Settings toggle, and the grant **lapses on its own** — re-read it before any run that depends on
  it. `scripts/device-gate.py` reads and sets it.
- **A device reading can look identical to a pass when it is nothing at all.** A database pulled
  without its `-wal` sidecar is **stale** — the app's most recent writes sit in the WAL, so a deleted
  row still reads as present. Pull both and `PRAGMA wal_checkpoint(TRUNCATE)` on the host. Never
  open the live file with `sqlite3` on the device while the app runs: it can unlink the app's WAL,
  and every later write lands in a deleted file, readable only through `/proc/<pid>/fd` while the
  app holds it open.
