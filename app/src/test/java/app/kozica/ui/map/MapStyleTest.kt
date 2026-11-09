package app.kozica.ui.map

import app.kozica.data.Mode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapStyleTest {
    private val template =
        """
        {"version":8,"sources":{},"layers":[
          {"id":"land","type":"fill","source":"region","source-layer":"land"},
          {"id":"hillshade","type":"hillshade","source":"relief"},
          {"id":"contours","type":"line","source":"contours","source-layer":"contours"}
        ]}
        """.trimIndent()

    private val regions = listOf("tatry" to File("/r/tatry.pmtiles"), "bieszczady" to File("/r/bieszczady.pmtiles"))

    @Test
    fun `without the relief its layers and sources are left out`() {
        val style = Json.parseToJsonElement(expandStyle(template, regions)).jsonObject
        assertEquals(listOf("land-tatry", "land-bieszczady"), style.layerIds())
        assertFalse("relief" in style.getValue("sources").jsonObject)
    }

    @Test
    fun `with the relief its layers are drawn once, not per Region`() {
        val style = Json.parseToJsonElement(expandStyle(template, regions, File("/p/relief.pmtiles"))).jsonObject
        assertEquals(listOf("land-tatry", "land-bieszczady", "hillshade", "contours"), style.layerIds())
        val sources = style.getValue("sources").jsonObject
        assertEquals(
            "pmtiles://file:///p/contours.pmtiles",
            sources
                .getValue("contours")
                .jsonObject
                .getValue("url")
                .jsonPrimitive.content,
        )
        assertEquals(
            "terrarium",
            sources
                .getValue("relief")
                .jsonObject
                .getValue("encoding")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `the Route's source is kept and its layers drawn once`() {
        val withRoute =
            """
            {"version":8,"metadata":{"kozica:faded-opacity":0.3},
             "sources":{"route":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}},
             "layers":[
              {"id":"trails","type":"line","source":"region","source-layer":"trails","metadata":{"kozica:fade":true}},
              {"id":"roads","type":"line","source":"region","source-layer":"roads"},
              {"id":"route","type":"line","source":"route"}
            ]}
            """.trimIndent()
        val expanded = expandStyle(withRoute, regions)
        val style = Json.parseToJsonElement(expanded).jsonObject
        assertEquals(
            listOf("trails-tatry", "trails-bieszczady", "roads-tatry", "roads-bieszczady", "route"),
            style.layerIds(),
        )
        assertTrue("route" in style.getValue("sources").jsonObject)
        assertEquals(Fade(listOf("trails-tatry", "trails-bieszczady"), 0.3f), fadeOf(expanded))
    }

    @Test
    fun `the other Mode has its own opacities, and the current one fades only under a selection`() {
        val fade =
            fadeOf(
                """
                {"version":8,"metadata":{"kozica:faded-opacity":0.3,"kozica:mode-opacity":0.4,
                 "kozica:mode-faded-opacity":0.2},
                 "sources":{},
                 "layers":[
                  {"id":"trails-tatry","type":"line","metadata":{"kozica:fade":true,"kozica:mode":"hiking"}},
                  {"id":"bike-routes-tatry","type":"line","metadata":{"kozica:fade":true,"kozica:mode":"cycling"}},
                  {"id":"bike-route-refs-tatry","type":"symbol",
                   "metadata":{"kozica:fade":true,"kozica:mode":"cycling","kozica:mode-only":true}},
                  {"id":"paths-tatry","type":"line","metadata":{"kozica:fade":true}}
                ]}
                """.trimIndent(),
            )
        assertEquals(1f, fade.opacityOf("trails-tatry", Mode.HIKING, faded = false))
        assertEquals(0.3f, fade.opacityOf("trails-tatry", Mode.HIKING, faded = true))
        assertEquals(0.4f, fade.opacityOf("trails-tatry", Mode.CYCLING, faded = false))
        assertEquals(0.2f, fade.opacityOf("trails-tatry", Mode.CYCLING, faded = true))
        assertEquals(0.4f, fade.opacityOf("bike-routes-tatry", Mode.HIKING, faded = false))
        assertEquals(1f, fade.opacityOf("bike-routes-tatry", Mode.CYCLING, faded = false))
        assertEquals(0.3f, fade.opacityOf("paths-tatry", Mode.CYCLING, faded = true))
        assertEquals(1f, fade.opacityOf("paths-tatry", Mode.CYCLING, faded = false))
        assertEquals(false, fade.isShown("bike-route-refs-tatry", Mode.HIKING))
        assertEquals(true, fade.isShown("bike-route-refs-tatry", Mode.CYCLING))
        assertEquals(true, fade.isShown("bike-routes-tatry", Mode.HIKING))
    }

    @Test
    fun `a selected Bike route is out of the chips' reach and never faded`() {
        for (style in listOf("light", "dark")) {
            val layers =
                Json
                    .parseToJsonElement(File("src/main/assets/map/style-$style.json").readText())
                    .jsonObject
                    .getValue("layers")
                    .jsonArray
                    .map { it.jsonObject }
                    .filter {
                        it
                            .getValue("id")
                            .jsonPrimitive.content
                            .startsWith(SELECTED_BIKE_ROUTE_LAYERS)
                    }
            assertEquals(3, layers.size)
            for (layer in layers) {
                assertFalse(
                    layer
                        .getValue("id")
                        .jsonPrimitive.content
                        .startsWith(BIKE_ROUTE_LAYERS),
                )
                assertEquals(null, layer["metadata"])
            }
        }
    }

    @Test
    fun `a layer with no filter of its own keeps none after a chip has set one on it`() {
        val held = mutableMapOf<String, String?>()
        var onTheLayer: String? = null

        assertEquals(null, held.holdFirst("bike-route-plates-tatry") { onTheLayer })
        onTheLayer = "kind==mtb"
        assertEquals(null, held.holdFirst("bike-route-plates-tatry") { onTheLayer })
    }

    @Test
    fun `a layer's own filter is the one first read, not what a chip narrowed it to`() {
        val held = mutableMapOf<String, String?>()
        var onTheLayer: String? = "has dir"

        assertEquals("has dir", held.holdFirst("bike-route-chevrons-tatry") { onTheLayer })
        onTheLayer = "has dir and kind==mtb"
        assertEquals("has dir", held.holdFirst("bike-route-chevrons-tatry") { onTheLayer })
    }

    @Test
    fun `the Regions' outlines are drawn once, and their bounds are where the camera may go`() {
        val withRegions =
            """
            {"version":8,"metadata":{"kozica:faded-opacity":0.3,"kozica:bounds":[14.95,48.95,22.95,51.12]},
             "sources":{"regions":{"type":"geojson","data":"asset://map/regions.geojson"}},
             "layers":[
              {"id":"land","type":"fill","source":"region","source-layer":"land"},
              {"id":"outside","type":"fill","source":"regions"}
            ]}
            """.trimIndent()
        val expanded = expandStyle(withRegions, regions)

        assertEquals(
            listOf("land-tatry", "land-bieszczady", "outside"),
            Json.parseToJsonElement(expanded).jsonObject.layerIds(),
        )
        assertEquals(CameraBounds(14.95, 48.95, 22.95, 51.12), cameraBoundsOf(expanded))
    }

    @Test
    fun `a changed polygon fails until gen_regions py redraws its Region's outline, ring for ring`() {
        val outlines =
            Json
                .parseToJsonElement(File("src/main/assets/map/regions.geojson").readText())
                .jsonObject
                .getValue("features")
                .jsonArray
                .map { it.jsonObject }
                .filter {
                    it
                        .getValue("properties")
                        .jsonObject["kind"]
                        ?.jsonPrimitive
                        ?.content == "region"
                }.associate { feature ->
                    val geometry = feature.getValue("geometry").jsonObject
                    val coordinates = geometry.getValue("coordinates").jsonArray
                    val lines =
                        if (geometry.getValue("type").jsonPrimitive.content == "LineString") {
                            listOf(coordinates)
                        } else {
                            coordinates.map { it.jsonArray }
                        }
                    feature
                        .getValue("properties")
                        .jsonObject
                        .getValue("id")
                        .jsonPrimitive.content to
                        lines
                            .map { line ->
                                line
                                    .map { point ->
                                        point.jsonArray.let {
                                            rounded(
                                                it[0].jsonPrimitive.double,
                                                it[1].jsonPrimitive.double,
                                            )
                                        }
                                    }.toSet()
                            }.toSet()
                }

        assertEquals(polygons, outlines)
    }

    @Test
    fun `both styles' bounds are the Regions' polygons' bounds`() {
        val points = polygons.values.flatten().flatten()
        val expected =
            listOf(
                points.minOf { it.first },
                points.minOf { it.second },
                points.maxOf { it.first },
                points.maxOf { it.second },
            )

        for (theme in listOf("light", "dark")) {
            val bounds = cameraBoundsOf(File("src/main/assets/map/style-$theme.json").readText())
            assertEquals(
                theme,
                expected,
                listOf(bounds.west, bounds.south, bounds.east, bounds.north).map {
                    Math.round(
                        it * 1e5,
                    )
                },
            )
        }
    }

    @Test
    fun `a position is inside the bounds up to their edges`() {
        val bounds = CameraBounds(west = 14.95, south = 48.95, east = 22.95, north = 51.12)

        assertTrue(bounds.contains(latitude = 49.23, longitude = 19.98))
        assertTrue(bounds.contains(latitude = 51.12, longitude = 14.95))
        assertFalse(bounds.contains(latitude = 52.23, longitude = 21.01))
    }

    private val polygons =
        File("../scripts/regions").listFiles { file -> file.extension == "poly" }!!.associate {
            it.nameWithoutExtension to
                rings(it)
        }

    /** An osmium polygon file's rings, each as its set of points in hundred-thousandths of a degree, as gen_regions.py rounds them. */
    private fun rings(poly: File): Set<Set<Pair<Long, Long>>> {
        val rings = mutableListOf<MutableSet<Pair<Long, Long>>>()
        var ring: MutableSet<Pair<Long, Long>>? = null
        for (line in poly
            .readLines()
            .drop(1)
            .map(String::trim)
            .filter(String::isNotEmpty)) {
            val values = line.split(Regex("\\s+")).mapNotNull(String::toDoubleOrNull)
            when {
                line == "END" -> ring = null
                ring != null && values.size == 2 -> ring.add(rounded(values[0], values[1]))
                else -> ring = mutableSetOf<Pair<Long, Long>>().also(rings::add)
            }
        }
        return rings.toSet()
    }

    private fun rounded(
        longitude: Double,
        latitude: Double,
    ) = Math.round(longitude * 1e5) to Math.round(latitude * 1e5)

    private fun kotlinx.serialization.json.JsonObject.layerIds() =
        getValue("layers").jsonArray.map {
            it.jsonObject
                .getValue("id")
                .jsonPrimitive.content
        }
}
