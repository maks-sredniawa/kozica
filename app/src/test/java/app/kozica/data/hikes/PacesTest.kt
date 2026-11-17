package app.kozica.data.hikes

import app.kozica.data.OutingSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.time.Instant

class PacesTest {
    private val hikes = MutableStateFlow<List<OutingSummary>>(emptyList())
    private val relief = MutableStateFlow(true)
    private val ruleSeconds = mutableMapOf<String, Double?>()
    private val failing = mutableSetOf<String>()
    private val read = mutableListOf<String>()

    private val paces =
        Paces(hikes, relief) { id ->
            read += id
            if (id in failing) throw IOException(id)
            ruleSeconds.getValue(id)
        }

    private fun hike(
        id: String,
        metres: Double = 10_000.0,
        movingSeconds: Long = 4 * 3600,
        name: String = id,
    ) = OutingSummary(
        id,
        name,
        Instant.EPOCH,
        Instant.EPOCH,
        metres,
        movingSeconds,
        ascentMetres = null,
        ascentFromGps = false,
    )

    private fun walked(
        id: String,
        ratio: Double,
    ): OutingSummary = hike(id).also { ruleSeconds[id] = it.movingSeconds / ratio }

    @Test
    fun `stops reading once ten Hikes count`() =
        runTest {
            hikes.value = List(10) { walked("new$it", 1.2) } + List(5) { walked("old$it", 0.8) }

            assertEquals(1.2, paces.pace.first()!!.factor, 1e-9)
            assertEquals(List(10) { "new$it" }, read)
        }

    @Test
    fun `a Hike too short to count is not read`() =
        runTest {
            hikes.value =
                listOf(
                    walked("a", 1.0),
                    hike("short", metres = 800.0),
                    hike("brief", movingSeconds = 1_200),
                    walked("b", 1.0),
                    walked("c", 1.0),
                )

            assertEquals(3, paces.pace.first()!!.hikes)
            assertEquals(listOf("a", "b", "c"), read)
        }

    @Test
    fun `a trimmed Hike is timed again`() =
        runTest {
            hikes.value = listOf(walked("a", 1.0), walked("b", 1.2), walked("c", 1.4))
            assertEquals(1.2, paces.pace.first()!!.factor, 1e-9)

            ruleSeconds["a"] = 4 * 3600 / 1.6
            hikes.value = listOf(hike("a", metres = 9_000.0), hike("b"), hike("c"))

            assertEquals(1.4, paces.pace.first()!!.factor, 1e-9)
            assertEquals(listOf("a", "b", "c", "a"), read)
        }

    @Test
    fun `a Hike that cannot be timed is not read again on a rename`() =
        runTest {
            hikes.value = listOf(walked("a", 1.0), walked("b", 1.0), hike("outside"))
            ruleSeconds["outside"] = null
            assertNull(paces.pace.first())

            read.clear()
            hikes.value = listOf(hike("a", name = "renamed"), hike("b"), hike("outside", name = "renamed"))

            assertNull(paces.pace.first())
            assertEquals(emptyList<String>(), read)
        }

    @Test
    fun `a failed read is tried again on the next pass`() =
        runTest {
            hikes.value = listOf(walked("a", 1.0), walked("b", 1.0), walked("c", 1.0))
            failing += "c"
            assertNull(paces.pace.first())

            failing.clear()
            read.clear()
            hikes.value = listOf(hike("a", name = "renamed"), hike("b"), hike("c"))

            assertEquals(3, paces.pace.first()!!.hikes)
            assertEquals(listOf("c"), read)
        }

    @Test
    fun `the relief arriving or leaving times every Hike again`() =
        runTest {
            hikes.value = listOf(walked("a", 1.0), walked("b", 1.0), walked("c", 1.0))
            paces.pace.first()

            read.clear()
            relief.value = false

            paces.pace.first()
            assertEquals(listOf("a", "b", "c"), read)
        }
}
