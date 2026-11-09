package app.kozica.ui.map

import app.kozica.data.routes.TrailColour
import app.kozica.data.routes.TrailDetail
import app.kozica.data.routes.TrailFacts
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** docs/phase-6c.md § Tests, the title rule, composed from the shipped English and Polish copy. */
class TrailTitleTest {
    private val english = words("src/main/res/values/strings.xml", polish = false)
    private val polish = words("src/main/res/values-pl/strings.xml", polish = true)

    private val unnamed =
        TrailFacts(
            id = 1,
            name = null,
            namePl = null,
            ref = null,
            colour = TrailColour.RED,
            symbol = "red_bar",
            from = null,
            to = null,
            longDistanceId = null,
            longDistanceName = null,
            startLatitude = 49.0,
            startLongitude = 20.0,
            endLatitude = 49.1,
            endLongitude = 20.1,
            metres = 1000.0,
            ascent = 0.0,
            descent = 0.0,
            gaps = 0,
            south = 49.0,
            west = 20.0,
            north = 49.1,
            east = 20.1,
        )

    @Test
    fun `a name is the title, the Polish one first in Polish`() {
        val named = unnamed.copy(name = "European long distance path E3", namePl = "Europejski szlak E3")
        assertEquals("European long distance path E3", trailTitle(named, null, null, english))
        assertEquals("Europejski szlak E3", trailTitle(named, null, null, polish))
        val polishOnly = unnamed.copy(namePl = "Główny Szlak Beskidzki")
        assertEquals("Główny Szlak Beskidzki", trailTitle(polishOnly, null, null, english))
    }

    @Test
    fun `the ends a mapper wrote, over the nearest places`() {
        val ends = unnamed.copy(from = "Biely Váh", to = "Kriváň")
        assertEquals("Red Trail: Biely Váh – Kriváň", trailTitle(ends, "Štrba", "Kôprovský štít", english))
        assertEquals("Czerwony szlak: Biely Váh – Kriváň", trailTitle(ends, "Štrba", "Kôprovský štít", polish))
    }

    @Test
    fun `the places nearest the walk's ends, then the colour alone`() {
        assertEquals("Red Trail: Kuźnice – Kasprowy Wierch", trailTitle(unnamed, "Kuźnice", "Kasprowy Wierch", english))
        assertEquals("Red Trail", trailTitle(unnamed, "Kuźnice", null, english))
        assertEquals("Czerwony szlak", trailTitle(unnamed, null, null, polish))
    }

    @Test
    fun `a colour outside the five is marked`() {
        val other = unnamed.copy(colour = TrailColour.OTHER)
        assertEquals("Marked Trail", trailTitle(other, null, null, english))
        assertEquals("Znakowany szlak", trailTitle(other, null, null, polish))
    }

    @Test
    fun `a Section has its own title, with its Long-distance Trail above it`() {
        val section =
            unnamed.copy(
                from = "Ustroń",
                to = "Wołosate",
                longDistanceId = 9,
                longDistanceName = "Główny Szlak Beskidzki",
            )
        assertEquals(
            SheetHeading(above = "Główny Szlak Beskidzki", title = "Red Trail: Ustroń – Wołosate"),
            trailHeading(TrailDetail(section, line = emptyList()), english),
        )
        assertEquals(
            SheetHeading(above = null, title = "Czerwony szlak"),
            trailHeading(TrailDetail(unnamed, line = emptyList()), polish),
        )
        val sameName = section.copy(namePl = "Magistrala Tatrzańska", longDistanceName = "Magistrala Tatrzańska")
        assertEquals(
            SheetHeading(above = null, title = "Magistrala Tatrzańska"),
            trailHeading(TrailDetail(sameName, line = emptyList()), polish),
        )
    }

    private fun words(
        path: String,
        polish: Boolean,
    ): TrailWords {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("string")
        val strings =
            (0 until nodes.length).map { nodes.item(it) as Element }.associate {
                it.getAttribute("name") to
                    it.textContent
            }
        return TrailWords(
            colours = TrailColour.entries.associateWith { strings.getValue("trail_colour_${it.stored}") },
            withEnds = strings.getValue("trail_with_ends"),
            colourOnly = strings.getValue("trail_colour_only"),
            polish = polish,
        )
    }
}
