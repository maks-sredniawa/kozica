package app.kozica.ui.support

import app.kozica.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BundledCreditsTest {
    private val groups = groupLicences("[]", SupportConfig.BUNDLED_CREDITS)

    private fun sourcesUnder(spdxId: String?) = groups.single { it.spdxId == spdxId }.artifacts.map { it.source }

    @Test
    fun `the map data, the relief and the font each sit under their own licence`() {
        assertEquals(listOf("CC-BY-4.0", "ODbL-1.0", "OFL-1.1", null), groups.map { it.spdxId })
        assertEquals(listOf("ČÚZK: DMR 4G", "ÚGKK SR: DMR 5.0"), sourcesUnder("CC-BY-4.0"))
        assertEquals(listOf("GUGiK"), sourcesUnder(null))
    }

    @Test
    fun `the OpenStreetMap credit names its contributors and this version's map data release`() {
        val source = sourcesUnder("ODbL-1.0").single()!!
        assertTrue(source.startsWith("© OpenStreetMap contributors"))
        assertTrue(source.endsWith("/releases/tag/v${BuildConfig.VERSION_NAME}"))
    }

    @Test
    fun `every SPDX licence credited has its text bundled`() {
        val bundled = File("src/main/assets/licences").list()!!.toSet()
        groups.mapNotNull { it.spdxId }.forEach { assertTrue(it, "$it.txt" in bundled) }
    }

    @Test
    fun `the relief credits name the relief build the release packs`() {
        val pinned = File("../scripts/relief.pin").readLines().single { it.startsWith("tag ") }.removePrefix("tag ")
        assertEquals(pinned, SupportConfig.RELIEF_TAG)
    }
}
