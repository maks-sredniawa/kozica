package app.kozica.ui.map

import app.kozica.data.routes.Approach
import app.kozica.data.routes.BikeRouteDetail
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.RouteSample
import app.kozica.data.routes.TrailColour
import app.kozica.data.routes.TrailDetail
import app.kozica.data.routes.TrailFacts
import app.kozica.data.routes.TrailPoint
import app.kozica.data.routes.bikeRouteFacts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteLayerTest {
    @Test
    fun `the line runs solid over the Trails, dashed off them, and not at all across no way`() {
        val line =
            listOf(
                sample(0.0, Approach.OFF_TRAIL),
                sample(1.0, Approach.OFF_TRAIL),
                sample(2.0, Approach.TRAIL),
                sample(3.0, Approach.TRAIL),
                sample(4.0, Approach.NO_WAY),
                sample(5.0, Approach.TRAIL),
            )
        val points = listOf(RoutePoint(49.0, 20.0), RoutePoint(49.0, 20.005))
        val features = features(routeGeoJson(points, planned(line)))

        assertEquals(
            listOf("LineString off 2", "LineString trail 3", "LineString trail 2", "Point 1", "Point 2"),
            features,
        )
    }

    @Test
    fun `points alone are numbered before a Route is planned`() {
        assertEquals(listOf("Point 1"), features(routeGeoJson(listOf(RoutePoint(49.0, 20.0)), null)))
    }

    @Test
    fun `each stretch of a highlighted walk carries its Trail's id, for a tap below zoom 10`() {
        val facts =
            TrailFacts(
                id = 7,
                name = null,
                namePl = null,
                ref = null,
                colour = TrailColour.RED,
                symbol = null,
                from = null,
                to = null,
                longDistanceId = null,
                longDistanceName = null,
                startLatitude = 49.0,
                startLongitude = 20.0,
                endLatitude = 49.0,
                endLongitude = 20.003,
                metres = 300.0,
                ascent = 0.0,
                descent = 0.0,
                gaps = 1,
                south = 49.0,
                west = 20.0,
                north = 49.0,
                east = 20.003,
            )
        val line = (0..3).map { TrailPoint(49.0, 20.0 + it / 1000.0, it * 100.0, 0.0, breakBefore = it == 2) }
        val ids =
            Json
                .parseToJsonElement(
                    trailWalkGeoJson(listOf(TrailDetail(facts, line))),
                ).jsonObject
                .getValue("features")
                .jsonArray
                .map {
                    it.jsonObject
                        .getValue("properties")
                        .jsonObject
                        .getValue("trail")
                        .jsonPrimitive.long
                }
        assertEquals(listOf(7L, 7L), ids)
    }

    @Test
    fun `a Bike route's walk has its id for a tap, its violet, and chevrons only when ridden one way`() {
        val line = (0..1).map { TrailPoint(49.0, 20.0 + it / 1000.0, it * 100.0, 0.0, breakBefore = false) }
        val facts = bikeRouteFacts().copy(id = 7, colour = TrailColour.OTHER)

        fun properties(oneWay: Boolean) =
            Json
                .parseToJsonElement(bikeRouteWalkGeoJson(listOf(BikeRouteDetail(facts.copy(oneWay = oneWay), line))))
                .jsonObject
                .getValue("features")
                .jsonArray
                .single()
                .jsonObject
                .getValue("properties")
                .jsonObject

        assertEquals(setOf("colour", "bike_route"), properties(oneWay = false).keys)
        assertEquals(setOf("colour", "bike_route", "one_way"), properties(oneWay = true).keys)
        assertEquals("violet", properties(oneWay = false).getValue("colour").jsonPrimitive.content)
        assertEquals(7L, properties(oneWay = false).getValue("bike_route").jsonPrimitive.long)
    }

    private fun features(geoJson: String) =
        Json.parseToJsonElement(geoJson).jsonObject.getValue("features").jsonArray.map {
            val feature = it.jsonObject
            val geometry = feature.getValue("geometry").jsonObject
            val properties = feature.getValue("properties").jsonObject
            val type = geometry.getValue("type").jsonPrimitive.content
            if (type == "Point") {
                "Point ${properties.getValue("number").jsonPrimitive.content}"
            } else {
                "$type ${properties.getValue("approach").jsonPrimitive.content} " +
                    geometry.getValue("coordinates").jsonArray.size
            }
        }

    private fun sample(
        metres: Double,
        approach: Approach,
    ) = RouteSample(49.0, 20.0 + metres / 1000, metres, 1000.0, approach)

    private fun planned(line: List<RouteSample>) =
        PlannedRoute(
            legs = emptyList(),
            line = line,
            ascent = 0.0,
            descent = 0.0,
            seconds = 0.0,
            secondsToPoint = emptyList(),
            demanding = emptyList(),
        )
}
