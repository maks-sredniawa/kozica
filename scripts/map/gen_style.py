#!/usr/bin/env python3
"""Generates the map's light and dark style templates, per docs/phase-2.md § 3.

    python3 scripts/map/gen_style.py && python3 scripts/map/sprites.py

The five Trail colours and the violet of a Bike route with no marking colour are the only literals. Every other colour is a tone of a palette that
scripts/gen_scheme.py derives from its seeds, so a seed change reaches the map when both re-run.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "scripts"))

from gen_scheme import N, NV, P, S, T  # noqa: E402

ASSETS = ROOT / "app/src/main/assets/map"
# Written by gen_regions.py, whose bounds are where the camera may go.
REGIONS = ASSETS / "regions.geojson"
FONT = ["Noto Sans Regular"]

TRAIL = {
    "red": "#d62828",
    "blue": "#1d4ed8",
    "green": "#2e8b3a",
    "yellow": "#e6b800",
    "black": "#1a1a1a",
}
BIKE_ROUTE = TRAIL | {"other": "#7f3fbf"}
BIKE_KINDS = ("touring", "mtb")
CHEVRONS = ("route_casing", "yellow_casing", "black_casing")
SHAPES = ("bar", "dot", "cross", "triangle", "corner")
POIS = ("hut", "shelter", "peak", "pass", "spring", "viewpoint", "parking", "stop", "station")


def tone(palette, value: int) -> str:
    return "#" + palette.hexes(value).lower()


def palette(dark: bool) -> dict[str, str]:
    def pick(light_tone: int, dark_tone: int) -> int:
        return dark_tone if dark else light_tone

    return {
        "background": tone(N, pick(96, 8)),
        "meadow": tone(N, pick(93, 11)),
        "wood": tone(S, pick(87, 15)),
        "scrub": tone(S, pick(83, 18)),
        "rock": tone(NV, pick(89, 21)),
        "glacier": tone(N, pick(99, 26)),
        "residential": tone(NV, pick(91, 14)),
        "water": tone(T, pick(84, 22)),
        "waterway": tone(T, pick(66, 45)),
        "park": tone(S, pick(50, 60)),
        "country": tone(NV, pick(30, 75)),
        "road": tone(N, pick(100, 30)),
        "road_casing": tone(NV, pick(70, 18)),
        "track": tone(NV, pick(50, 55)),
        "path": tone(NV, pick(40, 70)),
        "trail_other": tone(NV, 60),
        "yellow_casing": tone(NV, 30),
        "black_casing": tone(N, 90),
        "text": tone(N, pick(10, 90)),
        "text_halo": tone(N, pick(98, 10)),
        "hut_text": tone(P, pick(35, 80)),
        "badge": tone(N, pick(99, 20)),
        "badge_edge": tone(NV, pick(55, 60)),
        "ink": tone(NV, pick(25, 90)),
        "hut_ink": tone(P, pick(40, 80)),
        "spring_ink": tone(T, pick(45, 75)),
        "symbol_white": tone(N, 100),
        "symbol_edge": tone(NV, 50),
        "shade": tone(NV, pick(20, 0)),
        "light": tone(N, pick(100, 35)),
        "shade_accent": tone(NV, pick(35, 10)),
        "contour": tone(P, pick(45, 55)),
        "contour_text": tone(P, pick(35, 70)),
        "outside": tone(NV, pick(82, 6)),
        "region_edge": tone(P, pick(40, 70)),
        "route": tone(P, pick(40, 60)),
        "route_casing": tone(N, pick(100, 10)),
        "route_number": tone(N, pick(100, 10)),
        "hike_track": tone(T, pick(45, 70)),
        "route_back": tone(N, pick(10, 90)),
    }


def by_zoom(*stops: tuple[float, object]) -> list:
    return ["interpolate", ["linear"], ["zoom"], *(value for stop in stops for value in stop)]


TRAIL_WIDTHS = ((10, 2.0), (13, 3.0), (15, 4.5), (17, 7.0))
SYMBOL_STEP = 18
FADED_OPACITY = 0.3
MODE_FADED_OPACITY = 0.2
NO_TRAIL = ["==", ["get", "trail"], -1]
# phase 7a § 2: the largest union `count` on a way at B's pin is 10, so a twice-slot runs −9..9.
LARGEST_COUNT = 10
PLATE_STEP = 13
# The union's slots, with the Trails' own pair in an archive built before phase 7a.
TRAIL_INDEX = ["coalesce", ["get", "union_index"], ["get", "index"], 0]
TRAIL_COUNT = ["coalesce", ["get", "union_count"], ["get", "count"], 1]
BIKE_INDEX = ["coalesce", ["get", "index"], 0]
BIKE_COUNT = ["coalesce", ["get", "count"], 1]
TWICE_SLOTS = range(1 - LARGEST_COUNT, LARGEST_COUNT)


def twice_slot(index: list, count: list) -> list:
    return ["-", ["*", 2, index], ["-", count, 1]]


def trail_width(extra: float = 0) -> list:
    return by_zoom(*((zoom, width + extra) for zoom, width in TRAIL_WIDTHS))


def line_offset(index: list, count: list) -> list:
    slot = ["-", index, ["/", ["-", count, 1], 2]]
    return by_zoom(*((zoom, ["*", slot, width]) for zoom, width in TRAIL_WIDTHS))


def by_slot(index: list, count: list, offset) -> list:
    """An expression cannot build an array from data, so each slot gets its own literal offset; twice the
    slot is an integer, which match needs."""
    slots = (value for twice in TWICE_SLOTS for value in (twice, ["literal", offset(twice / 2)]))
    return ["match", twice_slot(index, count), *slots, ["literal", [0, 0]]]


def symbol_offset() -> list:
    # The step along the line keeps two Trails' symbols beside each other rather than stacked.
    return by_zoom(*((zoom, by_slot(TRAIL_INDEX, TRAIL_COUNT, lambda slot, w=width: [slot * SYMBOL_STEP, slot * w]))
                     for zoom, width in TRAIL_WIDTHS if zoom >= 13))


def chevron_offset() -> list:
    # icon-offset turns with icon-rotate, so a backward chevron's slot is on the other side.
    def at(width: float) -> list:
        return ["case", ["==", ["get", "dir"], "backward"],
                by_slot(BIKE_INDEX, BIKE_COUNT, lambda slot: [0, -slot * width]),
                by_slot(BIKE_INDEX, BIKE_COUNT, lambda slot: [0, slot * width])]

    return by_zoom(*((zoom, at(width)) for zoom, width in TRAIL_WIDTHS if zoom >= 13))


def ref_offset() -> list:
    return by_zoom(*((zoom, by_slot(BIKE_INDEX, BIKE_COUNT, lambda slot, w=width: [0, slot * w / REF_SIZE]))
                     for zoom, width in TRAIL_WIDTHS if zoom >= 13))


REF_SIZE = 10
# docs/phase-7a.md § 3's thresholds, the same in both Modes.
BIKE_ROUTE_SHOWN = ["case",
                    ["==", ["get", "kind"], "mtb"], [">=", ["zoom"], 12],
                    ["match", ["get", "network"], ["icn", "ncn"], True, False], True,
                    ["==", ["get", "network"], "rcn"], [">=", ["zoom"], 11],
                    [">=", ["zoom"], 12]]


def faded(layer: dict, mode: str | None = None, mode_only: bool = False) -> dict:
    """Marked for MapStyle.kt, which fades it while a Route is shown (docs/phase-5.md § 4), and in the other
    Mode, or hides it there if it is `mode_only` (docs/phase-7a.md § 3)."""
    assert not any(key.endswith("-opacity") for key in layer.get("paint", {})), layer["id"]
    metadata = {"kozica:fade": True}
    if mode:
        metadata["kozica:mode"] = mode
    if mode_only:
        metadata["kozica:mode-only"] = True
    return layer | {"metadata": metadata}


def layer(id: str, type: str, source_layer: str | None = None, **parts) -> dict:
    result = {"id": id, "type": type}
    if source_layer is not None:
        result |= {"source": "region", "source-layer": source_layer}
    return result | {key.replace("_", "-"): value for key, value in parts.items()}


def style(dark: bool) -> dict:
    c = palette(dark)
    casing = "black" if dark else "yellow"
    trail_colour = ["match", ["get", "colour"], *(v for pair in TRAIL.items() for v in pair), c["trail_other"]]
    bike_colour = ["match", ["get", "colour"], *(v for pair in TRAIL.items() for v in pair), BIKE_ROUTE["other"]]

    def text_layout(size, **more):
        return {"text-font": FONT, "text-size": size, **more}

    def text_paint(colour=None):
        return {"text-color": colour or c["text"], "text-halo-color": c["text_halo"], "text-halo-width": 1.2}

    layers = [
        layer("background", "background", paint={"background-color": c["background"]}),
        layer(
            "land", "fill", "land",
            paint={"fill-color": ["match", ["get", "kind"],
                                  ["wood", "forest"], c["wood"],
                                  "scrub", c["scrub"],
                                  ["grassland", "meadow", "heath"], c["meadow"],
                                  ["scree", "bare_rock"], c["rock"],
                                  "glacier", c["glacier"],
                                  "residential", c["residential"],
                                  c["background"]]},
        ),
        # The relief (docs/phase-4.md § 3): one source each, not repeated per Region, and dropped by
        # MapStyle.kt while the relief pack has not arrived.
        {"id": "hillshade", "type": "hillshade", "source": "relief",
         "paint": {"hillshade-illumination-direction": 315, "hillshade-exaggeration": 0.3 if dark else 0.35,
                   "hillshade-shadow-color": c["shade"], "hillshade-highlight-color": c["light"],
                   "hillshade-accent-color": c["shade_accent"]}},
        {"id": "contours", "type": "line", "source": "contours", "source-layer": "contours",
         "paint": {"line-color": c["contour"], "line-opacity": 0.6,
                   "line-width": by_zoom((11, ["case", ["get", "index"], 0.6, 0.3]), (16, ["case", ["get", "index"], 1.4, 0.8]))}},
        # Beyond the Regions there is no map, only the relief's smeared edges: shaded over as nothing.
        {"id": "outside", "type": "fill", "source": "regions", "filter": ["==", ["get", "kind"], "outside"],
         "paint": {"fill-color": c["outside"], "fill-opacity": 0.85}},
        layer("water-area", "fill", "water", filter=["==", ["geometry-type"], "Polygon"],
              paint={"fill-color": c["water"]}),
        layer(
            "water-line", "line", "water", filter=["==", ["geometry-type"], "LineString"],
            paint={"line-color": c["waterway"],
                   "line-width": by_zoom(*((zoom, ["match", ["get", "kind"], "river", river, stream])
                                           for zoom, river, stream in ((8, 1.0, 0.3), (12, 2.0, 0.6), (16, 4.0, 1.6))))},
        ),
        layer("boundary-park", "line", "boundaries", filter=["==", ["get", "kind"], "national_park"],
              paint={"line-color": c["park"], "line-width": by_zoom((6, 1.0), (14, 2.5)),
                     "line-dasharray": ["literal", [4, 2]], "line-opacity": 0.8}),
        layer("boundary-country", "line", "boundaries", filter=["==", ["get", "kind"], "country"],
              paint={"line-color": c["country"], "line-width": by_zoom((6, 1.2), (14, 3.0)),
                     "line-dasharray": ["literal", [6, 2, 1, 2]]}),
        layer("road-casing", "line", "roads", filter=["!=", ["get", "kind"], "track"],
              layout={"line-join": "round", "line-cap": "round"},
              paint={"line-color": c["road_casing"], "line-width": by_zoom((8, 1.2), (12, 3.0), (16, 9.0))}),
        layer("roads", "line", "roads", filter=["!=", ["get", "kind"], "track"],
              layout={"line-join": "round", "line-cap": "round"},
              paint={"line-color": c["road"], "line-width": by_zoom((8, 0.6), (12, 1.8), (16, 7.0))}),
        layer("tracks", "line", "roads", filter=["==", ["get", "kind"], "track"],
              paint={"line-color": c["track"], "line-width": by_zoom((12, 0.8), (16, 2.0)),
                     "line-dasharray": ["literal", [5, 2]]}),
        faded(layer("paths", "line", "paths",
                    paint={"line-color": c["path"], "line-width": by_zoom((12, 0.6), (16, 1.4)),
                           "line-dasharray": ["literal", [2, 2]]})),
        faded({"id": "contour-labels", "type": "symbol", "source": "contours", "source-layer": "contours",
               "minzoom": 12, "filter": ["get", "index"],
               "layout": text_layout(10, **{"symbol-placement": "line", "text-field": ["to-string", ["get", "ele"]]}),
               "paint": text_paint(c["contour_text"])}),
        faded(layer("trail-casings", "line", "trails", filter=["==", ["get", "colour"], casing],
                    layout={"line-join": "round"},
                    paint={"line-color": c[f"{casing}_casing"], "line-width": trail_width(2.0),
                           "line-offset": line_offset(TRAIL_INDEX, TRAIL_COUNT)}), "hiking"),
        faded(layer("trails", "line", "trails",
                    layout={"line-join": "round"},
                    paint={"line-color": trail_colour, "line-width": trail_width(),
                           "line-offset": line_offset(TRAIL_INDEX, TRAIL_COUNT)}), "hiking"),
        faded(layer("bike-route-casings", "line", "bike_routes",
                    filter=["all", ["==", ["get", "colour"], casing], BIKE_ROUTE_SHOWN],
                    layout={"line-join": "round"},
                    paint={"line-color": c[f"{casing}_casing"], "line-width": trail_width(2.0),
                           "line-offset": line_offset(BIKE_INDEX, BIKE_COUNT)}), "cycling"),
        faded(layer("bike-routes", "line", "bike_routes", filter=BIKE_ROUTE_SHOWN,
                    layout={"line-join": "round"},
                    paint={"line-color": bike_colour, "line-width": trail_width(),
                           "line-offset": line_offset(BIKE_INDEX, BIKE_COUNT)}), "cycling"),
        faded(layer(
            "trail-symbols", "symbol", "trails", minzoom=14,
            layout={
                "symbol-placement": "line",
                "symbol-spacing": 160,
                "icon-image": ["coalesce", ["image", ["coalesce", ["get", "symbol"], ""]],
                               ["image", ["concat", ["get", "colour"], "_bar"]], ["image", "other_bar"]],
                "icon-rotation-alignment": "map",
                "icon-keep-upright": False,
                "icon-allow-overlap": True,
                "icon-ignore-placement": True,
                "icon-offset": symbol_offset(),
            },
        ), "hiking"),
        faded(layer(
            "bike-route-chevrons", "symbol", "bike_routes", minzoom=13, filter=["has", "dir"],
            layout={
                "symbol-placement": "line",
                "symbol-spacing": by_zoom((13, 120), (16, 80)),
                "icon-image": ["match", ["get", "colour"], "yellow", "chevron_yellow_casing",
                               "black", "chevron_black_casing", "chevron_route_casing"],
                "icon-rotate": ["match", ["get", "dir"], "backward", 180, 0],
                "icon-rotation-alignment": "map",
                "icon-keep-upright": False,
                "icon-allow-overlap": True,
                "icon-ignore-placement": True,
                "icon-offset": chevron_offset(),
            },
        ), "cycling"),
        faded(layer(
            "bike-route-plates", "symbol", "bike_routes", minzoom=13,
            layout={
                "symbol-placement": "line",
                "symbol-spacing": by_zoom((13, 260), (16, 180)),
                "icon-image": ["concat", "bike_route_", ["get", "kind"], "_", ["get", "colour"]],
                "icon-size": by_zoom((13, 0.85), (17, 1.3)),
                "icon-rotation-alignment": "viewport",
                "icon-allow-overlap": True,
                "icon-ignore-placement": True,
                "icon-offset": by_slot(BIKE_INDEX, BIKE_COUNT, lambda slot: [0, slot * PLATE_STEP]),
            },
        ), "cycling"),
        faded(layer(
            "bike-route-refs", "symbol", "bike_routes", minzoom=13,
            filter=["all", ["has", "ref"], ["any", ["==", ["get", "colour"], "other"], [">=", ["zoom"], 15]]],
            layout=text_layout(REF_SIZE, **{"symbol-placement": "line", "text-field": ["get", "ref"],
                                            "text-offset": ref_offset()}),
            paint=text_paint(),
        ), "cycling", mode_only=True),
        faded(layer(
            "pois", "symbol", "pois",
            layout=text_layout(
                12,
                **{
                    "icon-image": ["concat", "poi_", ["get", "kind"]],
                    "symbol-sort-key": ["match", ["get", "kind"], "hut", 0, "shelter", 1, "peak", 2, "pass", 3,
                                        "station", 4, 5],
                    "text-field": ["case", ["has", "ele"],
                                   ["format", ["coalesce", ["get", "name"], ""], {}, "\n", {},
                                    ["concat", ["get", "ele"], " m"], {"font-scale": 0.85}],
                                   ["coalesce", ["get", "name"], ""]],
                    "text-anchor": "top",
                    "text-offset": ["literal", [0, 0.9]],
                    "text-optional": True,
                    "text-max-width": 8,
                },
            ),
            paint=text_paint(["match", ["get", "kind"], ["hut", "shelter"], c["hut_text"], c["text"]]),
        )),
        faded(layer(
            "places", "symbol", "places",
            layout=text_layout(["match", ["get", "kind"], "town", 15, "village", 13, 11],
                               **{"text-field": ["get", "name"], "text-max-width": 8}),
            paint=text_paint(),
        )),
        # Zoomed out, below where land and towns draw, the Regions themselves are the map.
        {"id": "region-edges", "type": "line", "source": "regions", "maxzoom": 10,
         "filter": ["==", ["get", "kind"], "region"],
         "paint": {"line-color": c["region_edge"], "line-width": 1.5, "line-dasharray": ["literal", [3, 2]],
                   "line-opacity": by_zoom((8, 1.0), (10, 0.0))}},
        {"id": "region-names", "type": "symbol", "source": "regions", "maxzoom": 9,
         "filter": ["==", ["get", "kind"], "label"],
         "layout": text_layout(by_zoom((5, 12), (8, 16)),
                               **{"text-field": ["get", "name"], "text-max-width": 7, "text-ignore-placement": ["step", ["zoom"], False, 8, True]}),
         "paint": text_paint(c["region_edge"]) | {"text-opacity": by_zoom((8, 1.0), (9, 0.0))}},
        # The selected Trail (docs/phase-6c.md § 3), never faded: from zoom 10 on its own strand in the
        # tiles, by the filter MapScreen.kt sets on its id; below it, its walk from MapScreen.kt's GeoJSON.
        layer("trail-selected-casing", "line", "trails", minzoom=10, filter=NO_TRAIL,
              layout={"line-join": "round", "line-cap": "round"},
              paint={"line-color": c["route_casing"], "line-width": trail_width(5.0),
                     "line-offset": line_offset(TRAIL_INDEX, TRAIL_COUNT)}),
        layer("trail-selected", "line", "trails", minzoom=10, filter=NO_TRAIL,
              layout={"line-join": "round", "line-cap": "round"},
              paint={"line-color": trail_colour, "line-width": trail_width(2.0),
                     "line-offset": line_offset(TRAIL_INDEX, TRAIL_COUNT)}),
        {"id": "trail-walk-casing", "type": "line", "source": "trail-walk", "maxzoom": 10,
         "layout": {"line-join": "round", "line-cap": "round"},
         "paint": {"line-color": c["route_casing"], "line-width": 6.0}},
        {"id": "trail-walk", "type": "line", "source": "trail-walk", "maxzoom": 10,
         "layout": {"line-join": "round", "line-cap": "round"},
         "paint": {"line-color": trail_colour, "line-width": 3.0}},
        # The planned Route (docs/phase-5.md § 4), drawn from MapScreen.kt's GeoJSON over everything.
        {"id": "route-casing", "type": "line", "source": "route", "filter": ["==", ["get", "approach"], "trail"],
         "layout": {"line-join": "round", "line-cap": "round"},
         "paint": {"line-color": c["route_casing"], "line-width": trail_width(6.0)}},
        {"id": "route", "type": "line", "source": "route", "filter": ["==", ["get", "approach"], "trail"],
         "layout": {"line-join": "round", "line-cap": "round"},
         "paint": {"line-color": c["route"], "line-width": trail_width(3.0)}},
        {"id": "route-off-trail", "type": "line", "source": "route", "filter": ["==", ["get", "approach"], "off"],
         "paint": {"line-color": c["route"], "line-width": trail_width(), "line-dasharray": ["literal", [1, 1.5]]}},
        {"id": "route-points", "type": "circle", "source": "route", "filter": ["==", ["geometry-type"], "Point"],
         "paint": {"circle-color": c["route"], "circle-radius": 11, "circle-stroke-color": c["route_casing"],
                   "circle-stroke-width": 2}},
        {"id": "route-numbers", "type": "symbol", "source": "route", "filter": ["==", ["geometry-type"], "Point"],
         "layout": text_layout(13, **{"text-field": ["get", "number"], "text-allow-overlap": True,
                                      "text-ignore-placement": True}),
         "paint": {"text-color": c["route_number"]}},
        # The Hike being recorded (docs/phase-6.md § 2), each stretch its own line, above the Route.
        {"id": "hike-track-casing", "type": "line", "source": "hike-track",
         "layout": {"line-join": "round", "line-cap": "round"},
         "paint": {"line-color": c["route_casing"], "line-width": trail_width(6.0)}},
        {"id": "hike-track", "type": "line", "source": "hike-track",
         "layout": {"line-join": "round", "line-cap": "round"},
         "paint": {"line-color": c["hike_track"], "line-width": trail_width(3.0)}},
        # The leg back to a followed Route (docs/phase-6b.md § 4), above the Track it often retraces.
        {"id": "route-back-casing", "type": "line", "source": "route-back",
         "filter": ["==", ["get", "approach"], "trail"],
         "layout": {"line-join": "round"},
         "paint": {"line-color": c["route_casing"], "line-width": trail_width(5.0)}},
        {"id": "route-back", "type": "line", "source": "route-back", "filter": ["==", ["get", "approach"], "trail"],
         "layout": {"line-join": "round"},
         "paint": {"line-color": c["route_back"], "line-width": trail_width(2.0),
                   "line-dasharray": ["literal", [2, 1]]}},
        {"id": "route-back-off-trail", "type": "line", "source": "route-back",
         "filter": ["==", ["get", "approach"], "off"],
         "paint": {"line-color": c["route_back"], "line-width": trail_width(), "line-dasharray": ["literal", [1, 1.5]]}},
        # The shown Hike's pinned photos (docs/phase-6.md § 6), tapped to open; above its Track.
        {"id": "hike-photos", "type": "circle", "source": "hike-photos",
         "paint": {"circle-color": c["hike_track"], "circle-radius": 9, "circle-stroke-color": c["route_casing"],
                   "circle-stroke-width": 3}},
    ]
    return {
        "version": 8,
        "metadata": {"kozica:faded-opacity": FADED_OPACITY,
                     "kozica:mode-opacity": 0.5 if dark else 0.4,
                     "kozica:mode-faded-opacity": MODE_FADED_OPACITY,
                     "kozica:bounds": json.loads(REGIONS.read_text())["bbox"]},
        "sources": {"route": {"type": "geojson", "data": {"type": "FeatureCollection", "features": []}},
                    "route-back": {"type": "geojson", "data": {"type": "FeatureCollection", "features": []}},
                    "trail-walk": {"type": "geojson", "data": {"type": "FeatureCollection", "features": []}},
                    "hike-track": {"type": "geojson", "data": {"type": "FeatureCollection", "features": []}},
                    "hike-photos": {"type": "geojson", "data": {"type": "FeatureCollection", "features": []}},
                    "regions": {"type": "geojson", "data": "asset://map/regions.geojson"}},
        "glyphs": "asset://map/glyphs/{fontstack}/{range}.pbf",
        "sprite": f"asset://map/sprite-{'dark' if dark else 'light'}",
        "layers": layers,
    }


def main() -> None:
    for name, dark in (("light", False), ("dark", True)):
        target = ASSETS / f"style-{name}.json"
        target.write_text(json.dumps(style(dark), indent=1, ensure_ascii=False) + "\n")
        print(target.relative_to(ROOT))


if __name__ == "__main__":
    main()
