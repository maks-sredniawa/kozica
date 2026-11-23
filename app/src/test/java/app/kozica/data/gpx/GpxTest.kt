package app.kozica.data.gpx

import app.kozica.data.Mode
import app.kozica.data.hikes.Height
import app.kozica.data.hikes.HeightSource
import app.kozica.data.hikes.Pin
import app.kozica.data.hikes.TrackPoint
import app.kozica.data.routes.Approach
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.RouteSample
import app.kozica.data.routes.TrailPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory

class GpxTest {
    private val start = 1_761_480_000_000L

    private val hike =
        outingGpx(
            name = "Kuźnice & <Kasprowy> \"szczyt\" 'tam'\u0001",
            note = "Mgła < 10 m & wiatr",
            track =
                listOf(
                    listOf(
                        TrackPoint(start, 49.2702, 19.9808, 1012.0),
                        TrackPoint(start + 5_000, 49.2701, 19.9809, 1013.0),
                    ),
                    listOf(TrackPoint(start + 600_000, 49.2321, 20.0047, null)),
                ),
            heights = listOf(Height(1014.2, HeightSource.GRID), null, Height(1890.0, HeightSource.GPS)),
            photos = listOf(Pin(49.2701, 19.9809) to "Widok > mgła", Pin(49.2321, 20.0047) to null),
        )

    private val route =
        routeGpx(
            name = "Kuźnice – Kasprowy",
            planned =
                PlannedRoute(
                    legs = emptyList(),
                    line =
                        listOf(
                            RouteSample(49.2702, 19.9808, 0.0, 1012.5, Approach.OFF_TRAIL),
                            RouteSample(49.2321, 20.0047, 6500.0, 1987.0, Approach.TRAIL),
                        ),
                    ascent = 975.0,
                    descent = 0.0,
                    seconds = 10_800.0,
                    secondsToPoint = listOf(0.0, 10_800.0),
                    demanding = emptyList(),
                ),
            points = listOf(RoutePoint(49.2702, 19.9808, "Kuźnice"), RoutePoint(49.2321, 20.0047)),
        )

    @Test
    fun `a Hike's GPX is well formed with names that need escaping, a trkseg per stretch, and no attribution`() {
        val gpx = parse(hike)

        assertEquals(
            "Kuźnice & <Kasprowy> \"szczyt\" 'tam'",
            gpx
                .all("trk")
                .single()
                .texts("name")
                .first(),
        )
        assertEquals(listOf("Mgła < 10 m & wiatr"), gpx.texts("desc"))
        assertEquals(listOf(2, 1), gpx.all("trkseg").map { it.getElementsByTagName("trkpt").length })
        assertEquals(listOf("1014.2", "1890.0"), gpx.texts("ele"))
        assertEquals("2025-10-26T12:00:00Z", gpx.texts("time").first())
        assertEquals(2, gpx.all("wpt").size)
        assertEquals(listOf("Widok > mgła"), gpx.all("wpt").flatMap { it.texts("name") })
        assertEquals(0, gpx.all("metadata").size)
    }

    @Test
    fun `an Outing's GPX names its type as Strava and Komoot read it, after the description`() {
        val ride = outingGpx("Gorce", null, emptyList(), emptyList(), emptyList(), Mode.CYCLING)

        assertEquals(listOf("hiking"), parse(hike).texts("type"))
        assertEquals(listOf("cycling"), parse(ride).texts("type"))
        val children = parse(hike).all("trk").single().childNodes
        val tags = List(children.length) { children.item(it) }.filterIsInstance<Element>().map { it.tagName }
        assertEquals(listOf("name", "desc", "type", "trkseg", "trkseg"), tags)
        assertEquals(0, parse(route).all("type").size)
    }

    @Test
    fun `a Route's GPX carries the OSM attribution, the network's heights and its points as waypoints`() {
        val gpx = parse(route)

        val copyright = gpx.all("copyright").single()
        assertEquals("OpenStreetMap contributors", copyright.getAttribute("author"))
        assertEquals(listOf("https://opendatacommons.org/licenses/odbl/1-0/"), copyright.texts("license"))
        assertEquals("https://www.openstreetmap.org/copyright", gpx.all("link").single().getAttribute("href"))
        assertEquals(listOf(2), gpx.all("trkseg").map { it.getElementsByTagName("trkpt").length })
        assertEquals(listOf("1012.5", "1987.0"), gpx.texts("ele"))
        assertEquals(listOf("49.2702000", "49.2321000"), gpx.all("wpt").map { it.getAttribute("lat") })
        assertEquals(listOf("Kuźnice"), gpx.all("wpt").flatMap { it.texts("name") })
    }

    @Test
    fun `a Trail's GPX is its walk, a trkseg per stretch, with the OSM attribution`() {
        val line =
            listOf(
                TrailPoint(49.27, 19.98, 0.0, 1010.0, breakBefore = false),
                TrailPoint(49.26, 19.98, 1100.0, 1100.0, breakBefore = false),
                TrailPoint(49.24, 19.98, 1100.0, 1500.0, breakBefore = true),
                TrailPoint(49.23, 19.98, 2200.0, 1987.0, breakBefore = false),
            )
        val gpx = parse(trailGpx("Bulwary Słowackiego – Kasprowy Wierch", line))

        assertEquals(listOf(2, 2), gpx.all("trkseg").map { it.getElementsByTagName("trkpt").length })
        assertEquals(listOf("1010.0", "1100.0", "1500.0", "1987.0"), gpx.texts("ele"))
        assertEquals("OpenStreetMap contributors", gpx.all("copyright").single().getAttribute("author"))
        assertEquals(listOf("Bulwary Słowackiego – Kasprowy Wierch"), gpx.all("trk").flatMap { it.texts("name") })
        assertEquals(0, gpx.all("wpt").size)
    }

    @Test
    fun `a file name drops what a file system refuses and falls back when nothing is left`() {
        val date = LocalDate.of(2026, 10, 27)
        assertEquals("Kuźnice – Kasprowy 2026-10-27.gpx", gpxFileName("Kuźnice – Kasprowy", date))
        assertEquals("ab 2026-10-27.gpx", gpxFileName(" a/b\u0001 ", date))
        assertEquals("kozica 2026-10-27.gpx", gpxFileName("?*", date))
    }

    @Test
    fun `a file name cut short never ends halfway through an emoji`() {
        val date = LocalDate.of(2026, 10, 27)
        assertEquals("${"a".repeat(59)} 2026-10-27.gpx", gpxFileName("a".repeat(59) + "\uD83C\uDFD4 Tatry", date))
    }

    private fun parse(gpx: Gpx): Document {
        val text = StringBuilder().also { gpx.write(it) }.toString()
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(text.byteInputStream())
    }

    private fun Document.all(tag: String): List<Element> = documentElement.all(tag)

    private fun Document.texts(tag: String): List<String> = documentElement.texts(tag)

    private fun Element.all(tag: String): List<Element> =
        getElementsByTagName(tag).let { nodes -> List(nodes.length) { nodes.item(it) as Element } }

    private fun Element.texts(tag: String): List<String> = all(tag).map { it.textContent }
}
