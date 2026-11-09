package app.kozica.ui.map

import app.kozica.data.hikes.Track
import app.kozica.data.routes.Approach
import app.kozica.data.routes.BikeRouteDetail
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.TrailColour
import app.kozica.data.routes.TrailDetail
import app.kozica.data.routes.TrailPoint
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The style's `route` source: the line in runs the style draws solid over the Trails and dashed off
 * them, a leg with no way left out, and the points numbered in order.
 */
internal fun routeGeoJson(
    points: List<RoutePoint>,
    planned: PlannedRoute?,
): String {
    val runs = mutableListOf<Pair<Approach, MutableList<List<Double>>>>()
    val line = planned?.line.orEmpty()
    for (i in 1 until line.size) {
        val approach = line[i].approach
        if (approach == Approach.NO_WAY) continue
        val from = listOf(line[i - 1].longitude, line[i - 1].latitude)
        val to = listOf(line[i].longitude, line[i].latitude)
        val run = runs.lastOrNull()
        if (run != null && run.first == approach && run.second.last() == from) {
            run.second += to
        } else {
            runs += approach to mutableListOf(from, to)
        }
    }
    val features =
        runs.map { (approach, coordinates) ->
            feature(
                "LineString",
                JsonArray(coordinates.map { position(it[0], it[1]) }),
                "approach" to JsonPrimitive(if (approach == Approach.TRAIL) "trail" else "off"),
            )
        } +
            points.mapIndexed { i, point ->
                feature("Point", position(point.longitude, point.latitude), "number" to JsonPrimitive("${i + 1}"))
            }
    return buildJsonObject {
        put("type", "FeatureCollection")
        put("features", JsonArray(features))
    }.toString()
}

/** The style's `trail-walk` source, below zoom 10: each highlighted Trail's walk, one line per stretch, by its colour and with its id for a tap. */
internal fun trailWalkGeoJson(trails: List<TrailDetail>): String =
    walkGeoJson(
        trails.flatMap { trail ->
            stretchFeatures(
                trail.line,
                "colour" to JsonPrimitive(trail.facts.colour.stored),
                "trail" to JsonPrimitive(trail.id),
            )
        },
    )

/** The same source for a selected Bike route, whose walk runs the way it is ridden, so a one-way one carries chevrons along it. */
internal fun bikeRouteWalkGeoJson(routes: List<BikeRouteDetail>): String =
    walkGeoJson(
        routes.flatMap { route ->
            val colour = if (route.facts.colour == TrailColour.OTHER) "violet" else route.facts.colour.stored
            stretchFeatures(
                route.line,
                "colour" to JsonPrimitive(colour),
                "bike_route" to JsonPrimitive(route.id),
                *(if (route.facts.oneWay) arrayOf("one_way" to JsonPrimitive(true)) else emptyArray()),
            )
        },
    )

private fun stretchFeatures(
    line: List<TrailPoint>,
    vararg properties: Pair<String, JsonPrimitive>,
): List<JsonObject> {
    val stretches = mutableListOf<MutableList<TrailPoint>>()
    for (point in line) {
        if (point.breakBefore || stretches.isEmpty()) stretches += mutableListOf<TrailPoint>()
        stretches.last() += point
    }
    return stretches.filter { it.size >= 2 }.map { stretch ->
        feature("LineString", JsonArray(stretch.map { position(it.longitude, it.latitude) }), *properties)
    }
}

private fun walkGeoJson(features: List<JsonObject>): String =
    buildJsonObject {
        put("type", "FeatureCollection")
        put("features", JsonArray(features))
    }.toString()

/** The style's `hike-track` source: one line per stretch, so nothing joins two across a pause. */
internal fun trackGeoJson(track: Track): String =
    buildJsonObject {
        put("type", "FeatureCollection")
        put(
            "features",
            JsonArray(
                track.filter { it.size >= 2 }.map { stretch ->
                    buildJsonObject {
                        put("type", "Feature")
                        put(
                            "geometry",
                            buildJsonObject {
                                put("type", "LineString")
                                put("coordinates", JsonArray(stretch.map { position(it.longitude, it.latitude) }))
                            },
                        )
                        put("properties", JsonObject(emptyMap()))
                    }
                },
            ),
        )
    }.toString()

/** The style's `hike-photos` source: a point per pinned photo, its `id` what a tap opens. */
internal fun photoPinsGeoJson(photos: List<HikePhoto>): String =
    buildJsonObject {
        put("type", "FeatureCollection")
        put(
            "features",
            JsonArray(
                photos.mapNotNull { photo ->
                    photo.pin?.let {
                        feature(
                            "Point",
                            position(it.longitude, it.latitude),
                            "id" to JsonPrimitive(photo.id),
                        )
                    }
                },
            ),
        )
    }.toString()

private fun position(
    longitude: Double,
    latitude: Double,
) = buildJsonArray {
    add(JsonPrimitive(longitude))
    add(JsonPrimitive(latitude))
}

private fun feature(
    type: String,
    coordinates: JsonArray,
    vararg properties: Pair<String, JsonPrimitive>,
) = buildJsonObject {
    put("type", "Feature")
    put(
        "geometry",
        buildJsonObject {
            put("type", type)
            put("coordinates", coordinates)
        },
    )
    put("properties", JsonObject(properties.toMap()))
}
