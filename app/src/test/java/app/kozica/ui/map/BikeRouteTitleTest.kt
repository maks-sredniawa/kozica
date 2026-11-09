package app.kozica.ui.map

import app.kozica.data.routes.BikeRouteDetail
import app.kozica.data.routes.BikeRouteKind
import app.kozica.data.routes.TrailColour
import app.kozica.data.routes.bikeRouteFacts
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** docs/phase-7a.md § Tests, the title rule, composed from the shipped English and Polish copy. */
class BikeRouteTitleTest {
    private val english = words("src/main/res/values/strings.xml", polish = false)
    private val polish = words("src/main/res/values-pl/strings.xml", polish = true)
    private val mtb = bikeRouteFacts()
    private val touring = mtb.copy(kind = BikeRouteKind.TOURING)

    @Test
    fun `a name is the title, the Polish one first in Polish`() {
        val named = mtb.copy(name = "Twister", namePl = "Skręt", ref = "E1")
        assertEquals("Twister", bikeRouteTitle(named, null, null, english))
        assertEquals("Skręt", bikeRouteTitle(named, null, null, polish))
    }

    @Test
    fun `no name gives the number and kind`() {
        assertEquals("MTB route E1", bikeRouteTitle(mtb.copy(ref = "E1"), "A", "B", english))
        assertEquals("Bike route R10", bikeRouteTitle(touring.copy(ref = "R10"), "A", "B", english))
        assertEquals("Szlak MTB E1", bikeRouteTitle(mtb.copy(ref = "E1"), "A", "B", polish))
        assertEquals("Szlak rowerowy R10", bikeRouteTitle(touring.copy(ref = "R10"), "A", "B", polish))
    }

    @Test
    fun `the ends a mapper wrote, over the nearest places`() {
        val ends = touring.copy(from = "Ustroń", to = "Wisła")
        assertEquals("Red Bike route: Ustroń – Wisła", bikeRouteTitle(ends, "Skoczów", "Istebna", english))
        assertEquals("Czerwony szlak rowerowy: Ustroń – Wisła", bikeRouteTitle(ends, "Skoczów", "Istebna", polish))
    }

    @Test
    fun `the places nearest the walk's ends, then the colour alone`() {
        assertEquals("Red MTB route: Szczyrk – Bielsko", bikeRouteTitle(mtb, "Szczyrk", "Bielsko", english))
        assertEquals("Czerwony szlak MTB: Szczyrk – Bielsko", bikeRouteTitle(mtb, "Szczyrk", "Bielsko", polish))
        assertEquals("Red MTB route", bikeRouteTitle(mtb, "Szczyrk", null, english))
        assertEquals("Czerwony szlak rowerowy", bikeRouteTitle(touring, null, null, polish))
    }

    @Test
    fun `no marking colour is marked`() {
        val other = touring.copy(colour = TrailColour.OTHER)
        assertEquals("Marked Bike route", bikeRouteTitle(other, null, null, english))
        assertEquals("Znakowany szlak rowerowy", bikeRouteTitle(other, null, null, polish))
    }

    @Test
    fun `a named route's number is under its title, and a Section's parent above it`() {
        val section = mtb.copy(name = "Twister", ref = "T1", longDistanceId = 9, longDistanceName = "Enduro Trails")
        assertEquals(
            SheetHeading(above = "Enduro Trails", title = "Twister", below = "T1"),
            bikeRouteHeading(BikeRouteDetail(section, line = emptyList()), english),
        )
        assertEquals(
            SheetHeading(above = null, title = "MTB route T1", below = null),
            bikeRouteHeading(BikeRouteDetail(mtb.copy(ref = "T1"), line = emptyList()), english),
        )
    }

    @Test
    fun `the Sections button names the parent, a parent's own row its title, and an unnamed parent has none`() {
        val named = mtb.copy(longDistanceId = 9, longDistanceName = "Enduro Trails")
        assertEquals("Enduro Trails", bikeRouteSectionsSubject(BikeRouteDetail(named, line = emptyList()), "Twister"))
        val unnamed = mtb.copy(longDistanceId = 9, longDistanceName = null)
        assertEquals(null, bikeRouteSectionsSubject(BikeRouteDetail(unnamed, line = emptyList()), "Twister"))
        val ownRow = BikeRouteDetail(mtb, line = emptyList(), wholeId = 7)
        assertEquals("Twister", bikeRouteSectionsSubject(ownRow, "Twister"))
        assertEquals(null, bikeRouteSectionsSubject(BikeRouteDetail(mtb, line = emptyList()), "Twister"))
    }

    private fun words(
        path: String,
        polish: Boolean,
    ): BikeRouteWords {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("string")
        val strings =
            (0 until nodes.length).map { nodes.item(it) as Element }.associate {
                it.getAttribute("name") to
                    it.textContent
            }

        fun kind(prefix: String) =
            BikeRouteKindWords(
                numbered = strings.getValue("${prefix}_numbered"),
                withEnds = strings.getValue("${prefix}_with_ends"),
                colourOnly = strings.getValue("${prefix}_colour_only"),
            )
        return BikeRouteWords(
            colours = TrailColour.entries.associateWith { strings.getValue("trail_colour_${it.stored}") },
            kinds = mapOf(BikeRouteKind.TOURING to kind("bike_route"), BikeRouteKind.MTB to kind("mtb_route")),
            polish = polish,
        )
    }
}
