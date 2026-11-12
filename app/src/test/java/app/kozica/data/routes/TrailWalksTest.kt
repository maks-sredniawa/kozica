package app.kozica.data.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/phase-6c.md § Tests and docs/phase-7b.md § 6: the walks' reader against the fixture `scripts/test_build_network.py` writes. */
class TrailWalksTest {
    private val network = TrailNetwork.read(javaClass.getResource("/trails-fixture.network")!!.readBytes())
    private val walks = TrailWalks.read(javaClass.getResource("/trails-fixture.edges")!!.readBytes())

    @Test
    fun `reads each Trail's steps in order`() {
        assertEquals(4, walks.trailCount)
        val steps = walks.steps(7)!!
        assertEquals(listOf(0, 1), steps.map(TrailWalks::edge))
        assertFalse(steps.any(TrailWalks::reversed))
        assertTrue(TrailWalks.reversed(walks.steps(9)!!.single()))
        assertNull(walks.steps(10))
    }

    @Test
    fun `a reversed edge is walked from its last point`() {
        val line = network.walkLine(walks.steps(9)!!)
        assertEquals(49.2, line.first().latitude, 1e-9)
        assertEquals(20.21, line.first().longitude, 1e-9)
        assertEquals(listOf(940.0, 1000.0, 900.0), line.map { it.heightMetres })
        assertEquals(network.edgeLength[4].toDouble(), line.last().metres, 1.0)
    }

    @Test
    fun `a gap breaks the line and adds no metres`() {
        val line = network.walkLine(walks.steps(8)!!)
        val gap = line.indexOfFirst { it.breakBefore }
        assertEquals(3, gap)
        assertEquals(line[gap - 1].metres, line[gap].metres, 1e-9)
        assertEquals(listOf(700.0, 600.0, 500.0), line.take(3).map { it.heightMetres })
        assertEquals(1, line.count { it.breakBefore })
        assertEquals(network.edgeLength[2] + network.edgeLength[1].toDouble(), line.last().metres, 1.0)
    }

    @Test
    fun `reads format 2's back walk, and none where it is the walk turned`() {
        val there = walks.steps(11)!!
        val back = walks.back(11)!!
        assertEquals(listOf(17, 18, 20), there.map(TrailWalks::edge))
        assertEquals(listOf(20, 19, 17), back.map(TrailWalks::edge))
        assertEquals(listOf(true, false, true), back.map(TrailWalks::reversed))
        assertNull(walks.back(7))
        assertNull(walks.back(10))
    }

    @Test
    fun `a walk turned keeps each gap between the same two stretches`() {
        val turned = TrailWalks.turned(walks.steps(8)!!)
        assertEquals(listOf(1, 2), turned.map(TrailWalks::edge))
        assertEquals(listOf(true, false), turned.map(TrailWalks::reversed))
        assertEquals(listOf(false, true), turned.map(TrailWalks::gapBefore))
    }

    @Test
    fun `format 1 is refused by name`() {
        val bytes = javaClass.getResource("/trails-fixture.edges")!!.readBytes()
        bytes[4] = 1
        val refused = assertThrows(IllegalArgumentException::class.java) { TrailWalks.read(bytes) }
        assertEquals("Trail walks format 1, this build reads 2", refused.message)
    }
}
