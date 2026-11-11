package app.kozica.data.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/phase-5.md § Tests: the reader against the fixture `scripts/test_build_network.py` writes. */
class TrailNetworkTest {
    private val network = TrailNetwork.read(javaClass.getResource("/trails-fixture.network")!!.readBytes())
    private val router = Router(network)

    private fun ways(leg: Leg) = leg.steps.map { network.edgeWay[it.edge] }

    @Test
    fun `reads every array the writer wrote`() {
        assertEquals(14, network.nodeCount)
        assertEquals(13, network.edgeCount)
        assertEquals((100L..112L).toList(), network.edgeWay.toList())
        assertEquals(TrailNetwork.FORWARD_ONLY, network.edgeOneway[1].toInt())
        assertEquals(3, network.edgeSac[2].toInt())
        assertEquals(490_000_000, network.nodeLat[0])
        assertEquals(7000.toShort(), network.nodeHeight[3])
        assertEquals(3, network.edgePointCount[2])
        assertTrue(network.edgeAscent[2] > 150)
        assertEquals(listOf<Byte>(1, 1, 1, 1, 1, 3, 1, 4, 3, 3, 3, 6, 4), network.edgeSets.toList())
        assertEquals(TrailNetwork.FORWARD_ONLY, network.edgeRide[7].toInt())
        assertEquals(3, network.edgeMtb[7].toInt())
        assertEquals(5, network.edgeMtbUphill[7].toInt())
    }

    @Test
    fun `reads format 3's Bike route kinds, surface, rider directions and riding seconds`() {
        assertEquals(TrailNetwork.SET_TRAIL or TrailNetwork.SET_TOURING, network.edgeSets[5].toInt())
        assertEquals(TrailNetwork.SET_MTB, network.edgeSets[7].toInt())
        assertEquals(TrailNetwork.PAVED, network.edgeSurface[5].toInt())
        assertEquals(TrailNetwork.UNPAVED, network.edgeSurface[7].toInt())
        assertEquals(TrailNetwork.ROUGH, network.edgeSurface[0].toInt())
        assertEquals(List(13) { 0 }, network.edgeRideTouring.map { it.toInt() })
        assertEquals(TrailNetwork.FORWARD_ONLY, network.edgeRideMtb[7].toInt())
        assertEquals(network.edgeLength[5] / (17.53f / 3.6f), network.edgeRidingForward[5], 0.01f)
        assertEquals(network.edgeLength[7] / (10f / 3.6f), network.edgeRidingBackward[7], 0.01f)
        for (edge in listOf(0, 1, 2, 3, 4, 6)) {
            assertTrue(network.edgeRidingForward[edge].isNaN() && network.edgeRidingBackward[edge].isNaN())
        }
    }

    @Test
    fun `format 2 is refused by name`() {
        val bytes = javaClass.getResource("/trails-fixture.network")!!.readBytes()
        bytes[4] = 2
        val refused = assertThrows(IllegalArgumentException::class.java) { TrailNetwork.read(bytes) }
        assertEquals("Trail network format 2, this build reads 3", refused.message)
    }

    @Test
    fun `a Route in Hiking goes round a Bike route's shorter way`() {
        val leg = router.leg(49.3, 20.3, 49.3, 20.32)!!
        assertEquals(listOf(105L, 106L), ways(leg))
    }

    @Test
    fun `a point beside a Bike route alone snaps to the Trail`() {
        val snap = router.nearest(49.3, 20.31)!!
        assertTrue(network.edgeWay[snap.edge] in listOf(105L, 106L))
    }

    @Test
    fun `an edge in both sets is walked`() {
        assertTrue(network.canWalkForward(5))
        assertTrue(network.canWalkBackward(5))
        assertFalse(network.canWalkForward(7))
        assertFalse(network.canWalkBackward(7))
    }

    @Test
    fun `the flat way is the shortest`() {
        val leg = router.leg(49.0, 20.0, 49.0, 20.02)!!
        assertEquals(listOf(100L, 101L), ways(leg))
        assertEquals((network.edgeLength[0] + network.edgeLength[1]) / (4000.0 / 3600), leg.seconds, 1.0)
    }

    @Test
    fun `a one-way Trail is never walked against its direction`() {
        val leg = router.leg(49.0, 20.02, 49.0, 20.0)!!
        assertFalse(101L in ways(leg))
        assertEquals(listOf(103L, 102L), ways(leg))
    }

    @Test
    fun `each direction takes its own climb, and its own steep descent`() {
        val flat = { edge: Int -> network.edgeLength[edge] / (4000f / 3600) }
        assertEquals(flat(2) + 1200, network.edgeSecondsForward[2], 1f)
        assertEquals(flat(2) + 600, network.edgeSecondsBackward[2], 1f)
        assertEquals(flat(3) + 600, network.edgeSecondsForward[3], 1f)
        assertEquals(flat(3) + 1200, network.edgeSecondsBackward[3], 1f)
    }

    @Test
    fun `a join on a one-way Trail's node stands on that node`() {
        fun at(
            edge: Int,
            fraction: Double,
            latitude: Double,
            longitude: Double,
        ) = Snap(edge, fraction, latitude, longitude, 0.0)

        val fromB = router.leg(listOf(at(1, 0.0, 49.0, 20.01)), listOf(at(0, 0.0, 49.0, 20.0)), 49.0, 20.0)!!
        assertEquals(listOf(100L), ways(fromB))
        val toC = router.leg(listOf(at(3, 0.0, 49.01, 20.01)), listOf(at(1, 1.0, 49.0, 20.02)), 49.0, 20.02)!!
        assertEquals(listOf(103L), ways(toC))
    }

    @Test
    fun `no way to an island says so`() {
        assertNull(router.leg(49.0, 20.0, 49.2, 20.205))
    }
}
