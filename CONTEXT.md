# Vocabulary

The words Kozica uses, and the ones it does not.

**The rule this file exists to enforce: one concept, one word, everywhere.** The entity, the DAO, the
route key, the string resource and the sentence in the UI all use it. When the domain word and the
user-facing word genuinely differ, both are written here with the difference stated.

## The domain

| Term | Means | Not |
| --- | --- | --- |
| **Trail** | A marked hiking route with a colour and a symbol, as on the signposts, running over any number of paths and roads. A regional Trail that OSM maps in pieces is one Trail. Its title is its name, else "*colour* Trail: *from* – *to*" from the ends a mapper wrote or the places nearest them, else the colour alone ([`docs/phase-6c.md`](docs/phase-6c.md) § 2). Polish UI: *szlak*. | "route", "track", "path" |
| **Long-distance Trail** | A Trail made of Sections, each its own Trail: an `nwn` or `iwn` relation whose members are relations, `type=superroute` or `route=hiking`; where one holds another, the outermost. E3, the Tatranská magistrála. Polish UI: *szlak długodystansowy*. | "parent", "long Trail", "superroute" |
| **Section** | One Trail of a Long-distance Trail, or one Bike route of a Bike route with Sections, selected and walked or ridden on its own. Polish UI: *odcinek*. | "stage", "part", "piece" (a piece is not a Trail) |
| **Path** | Any footway on the map, marked or not. A Path is never drawn or named as if it were a Trail. Polish UI: *ścieżka*. | "trail" for an unmarked way |
| **Hut** | A staffed mountain hut. Polish UI: *schronisko*. | "shelter", "refuge", "hostel" |
| **Shelter** | An unstaffed roof on or near a Trail. Polish UI: *wiata*. A staffed *chatka* is a Hut. | "hut", *chatka* |
| **Route** | What the user plans: a start, an end and any via points, in an order the user can change, each leg joined over Trails only in Hiking, over Bike routes only in Cycling. It can be saved under a name. Polish UI: *trasa*. | "trail" for a route, "track" (kept for a recorded hike) |
| **Region** | A group of neighbouring mountain ranges that the map data is built and delivered in, e.g. *Beskidy Zachodnie*. Its boundary follows the ranges, not administrative borders, and crosses into Slovakia and Czechia where the ranges do; it stops at Ukraine's and Germany's. The user never picks one: every Region arrives with the app. | "area", "pack", "map" (a Region is what the map shows, not the map itself) |
| **Hike** | One recorded outing on foot, from Record to Stop: its track, its photos, its note. Saved in the Hikes tab. Polish UI: *wycieczka*. | "activity", "walk", "trip", "recording" (the act, not the thing kept) |
| **Ride** | One recorded outing on a Bike, kept in the same log as Hikes and shown as a Ride; a Hike can be made a Ride afterwards, and back. The tab's name once it holds Rides is open, decided when phase 7c opens. Polish UI: *wycieczka rowerowa*. | "activity", "trip", "Hike" for a Ride |
| **Track** | The line a Hike or a Ride leaves: the phone's fixes in order, broken into stretches at each pause. Polish UI: *ślad*. | "route", "trail", "path" |
| **Following** | Walking or riding a Route with the map keeping the user in view along it, from "Follow" to "Stop" or the Route's end: on screen only in Hiking, with spoken junction directions in Cycling. Polish UI: *prowadzenie*. | "navigation", "guidance", "tracking" (a Track is a Hike's or a Ride's) |
| **Progress** | How far along the Route's line the user is while Following; it only moves forward. Polish UI: *postęp*. | "position" (that is the fix), "distance walked" or "ridden" |
| **Pace** | The user's walking speed against the signposts' rule: one factor from their recorded Hikes, never below 0.75 ([`docs/phase-6b.md`](docs/phase-6b.md) § 2). In Cycling, one factor per Bike for Touring and one for MTB, from its Rides, against our riding-time rule ([ADR-0011](docs/adr/0011-cycling-follows-marked-bike-routes-and-its-times-are-our-own-estimate.md)). Polish UI: *twoje tempo*. | "speed", "fitness" |
| **Mode** | Hiking or Cycling: the one switch the map, search, planning and recording all follow. Search finds places in both, and only the current Mode's Trails or Bike routes, with a hint offering the other's. Polish UI: *pieszo* / *rowerem*. | "profile", "activity type", "layer" |
| **Bike route** | A signed cycling route, as on the cycling signs: Touring (`route=bicycle`) or MTB (`route=mtb`). One made of others has Sections when it is `icn` or `ncn` or none of its parts carries its marking, as a trail centre's lines; one whose parts carry its marking is one Bike route ([`docs/phase-7a.md`](docs/phase-7a.md) § 2). In titles a Touring route is a "Bike route" and an MTB one an "MTB route"; "Bike routes" alone means both. Polish UI: *szlak rowerowy*, *szlak MTB*. | "Trail" (kept for hiking), "cycle path" (a way, not a route) |
| **MTB route** | A Bike route of the MTB kind (`route=mtb`), marked for mountain bikes; its sheet says "for mountain bikes" under the kind. The chip reads "MTB". Polish UI: *szlak MTB*. | "MTB trail", "mountain bike route" |
| **Bike** | One of the user's bikes in "My bikes": a unique name and an optional photo, nothing else. With none added, every Ride is the same Bike. Polish UI: *rower*. | "profile", "vehicle", "equipment" |

## Words we deliberately avoid

- **"Trail" never names an unmarked way.** In the Tatras only marked Trails may be walked, so the
  word, the map and the copy keep a Path from passing for one.
- **"Trail" never names a Bike route.** In Polish both are *szlak* on the signs; in code and in
  English the two stay apart, because a Route in Cycling follows Bike routes only and one in Hiking
  Trails only.
- **"Route" and "track" never name a Trail**, because each is kept for a concept of its own: what the
  user plans (a Route, above) and the line a Hike leaves (a Track, above).

## Naming conventions in code

Some names in this codebase are deliberately generic — `AppDatabase`, `AppTheme`, `AppPreferences`,
`AppContainer`, `MainApplication`, `MainActivity`. They stay correct whatever the app is called, so
renaming the app is a package move rather than a repo-wide find-and-replace. Don't rename them to
match the product; name the *domain* types after the domain instead.
