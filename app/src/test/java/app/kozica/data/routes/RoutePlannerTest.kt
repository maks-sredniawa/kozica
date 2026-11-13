package app.kozica.data.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/phase-5.md § Tests: a Route's totals over the fixture `TrailNetworkTest` reads. */
class RoutePlannerTest {
    private val network = TrailNetwork.read(javaClass.getResource("/trails-fixture.network")!!.readBytes())
    private val planner = RoutePlanner(network)

    private val a = RoutePoint(49.0, 20.0)
    private val b = RoutePoint(49.0, 20.01)
    private val c = RoutePoint(49.0, 20.02)
    private val d = RoutePoint(49.01, 20.01)
    private val e = RoutePoint(49.2, 20.2)
    private val summit = RoutePoint(49.2, 20.205)
    private val f = RoutePoint(49.2, 20.21)
    private val warsaw = RoutePoint(52.23, 21.01)
    private val flat = 4000.0 / 3600

    @Test
    fun `a signpost's rounding, up to 5 minutes`() {
        assertEquals(0, roundedUpToFiveMinutes(0.0))
        assertEquals(5, roundedUpToFiveMinutes(1.0))
        assertEquals(5, roundedUpToFiveMinutes(300.0))
        assertEquals(10, roundedUpToFiveMinutes(301.0))
    }

    @Test
    fun `the total is the sum rounded, not the rounded parts summed`() {
        val route = planner.plan(listOf(a, b, c))
        val leg = route.legs[0]!!.seconds
        assertEquals(15, route.minutesTo(1))
        assertEquals(roundedUpToFiveMinutes(2 * leg), route.minutes)
        assertTrue(route.minutes < 2 * route.minutesTo(1)!!)
    }

    @Test
    fun `a time after a leg with no way is not given`() {
        val route = planner.plan(listOf(a, b, e, f))
        assertEquals(15, route.minutesTo(1))
        assertNull(route.secondsToPoint[2])
        assertNull(route.minutesTo(2))
        assertNull(route.minutesTo(3))
    }

    @Test
    fun `a point far from every Trail leaves its legs missing rather than failing`() {
        val route = planner.plan(listOf(a, warsaw, b))
        assertEquals(listOf(null, null), route.legs)
        assertFalse(route.complete)
        assertNull(route.minutesTo(2))
    }

    @Test
    fun `each point's metres are where the line reaches it, its dashed approach included`() {
        val points = listOf(RoutePoint(49.0003, 20.0), b, RoutePoint(48.9997, 20.02))
        val route = planner.plan(points)

        assertEquals(Approach.OFF_TRAIL, route.line[1].approach)
        assertEquals(0.0, route.metresToPoint.first()!!, 0.0)
        assertEquals(route.line.last().metres, route.metresToPoint.last()!!, 0.0)
        for ((point, metres) in points.zip(route.metresToPoint)) {
            val sample = route.line.single { it.metres == metres }
            assertTrue(Router.metres(sample.latitude, sample.longitude, point.latitude, point.longitude) < 0.5)
        }
    }

    @Test
    fun `a whole edge costs exactly what the build stored, each way`() {
        assertEquals(EdgePart(network.edgeSecondsForward[4].toDouble(), 100.0, 60.0), network.part(4, 0.0, 1.0))
        assertEquals(EdgePart(network.edgeSecondsBackward[4].toDouble(), 60.0, 100.0), network.part(4, 1.0, 0.0))
    }

    @Test
    fun `the phone's rule for part of an edge is the build's rule for the whole`() {
        val almost = 1 - 1e-9
        for (edge in (0 until network.edgeCount).filter(network::isTrail)) {
            assertEquals(network.edgeSecondsForward[edge].toDouble(), network.part(edge, 0.0, almost).seconds, 1.0)
            assertEquals(network.edgeSecondsBackward[edge].toDouble(), network.part(edge, almost, 0.0).seconds, 1.0)
        }
    }

    @Test
    fun `part of an edge walked backward climbs as the build counted it, in the edge's own order`() {
        val almost = 1 - 1e-9
        for (edge in 0 until network.edgeCount) {
            val part = network.part(edge, almost, 0.0)
            assertEquals("edge $edge", network.edgeSecondsBackward[edge].toDouble(), part.seconds, 1.0)
            assertEquals("edge $edge", network.edgeDescent[edge].toDouble(), part.ascent, 0.5)
            assertEquals("edge $edge", network.edgeAscent[edge].toDouble(), part.descent, 0.5)
        }
    }

    @Test
    fun `a leg to a summit midway along an edge climbs only its own side`() {
        val up = planner.plan(listOf(e, summit))
        assertEquals(100.0, up.ascent, 0.5)
        assertEquals(0.0, up.descent, 0.5)
        assertEquals(network.edgeLength[4] / 2 / flat + 100 * 6, up.seconds, 2.0)
        val down = planner.plan(listOf(summit, f))
        assertEquals(0.0, down.ascent, 0.5)
        assertEquals(60.0, down.descent, 0.5)
        assertEquals(network.edgeLength[4] / 2 / flat + 60 * 3, down.seconds, 2.0)
    }

    @Test
    fun `a noisy ridge takes no climb from the real one before it`() {
        val climb = network.part(3, 1.0, 0.7)
        assertEquals(200.0, climb.ascent, 0.5)
        assertEquals(0.0, climb.descent, 0.5)
        assertEquals(network.edgeLength[3] * 0.3 / flat + 200 * 6, climb.seconds, 1.0)
        val ridge = network.part(3, 0.7, 0.0)
        assertEquals(0.0, ridge.ascent, 0.5)
        assertEquals(0.0, ridge.descent, 0.5)
        assertEquals(network.edgeLength[3] * 0.7 / flat, ridge.seconds, 1.0)
    }

    @Test
    fun `an edge walked backward swaps its ascent and descent`() {
        val route = planner.plan(listOf(c, d))
        assertEquals(
            listOf(103L),
            route.legs
                .single()!!
                .steps
                .map { network.edgeWay[it.edge] },
        )
        assertEquals(200.0, route.ascent, 1.0)
        assertEquals(0.0, route.descent, 1.0)
    }

    @Test
    fun `a demanding stretch never runs across a leg with no way`() {
        val route = planner.plan(listOf(a, d, e, f))
        assertNull(route.legs[1])
        assertEquals(2, route.demanding.size)
        assertTrue(route.demanding[0].toMetres <= route.demanding[1].fromMetres)
    }

    @Test
    fun `against the one-way Trail the Route climbs over D and names the demanding stretch`() {
        val route = planner.plan(listOf(c, a))
        assertEquals(200.0, route.ascent, 1.0)
        assertEquals(200.0, route.descent, 1.0)
        assertEquals(route.metres, network.edgeLength[2] + network.edgeLength[3].toDouble(), 5.0)
        val stretch = route.demanding.single()
        assertEquals(3, stretch.sac)
        assertEquals(network.edgeLength[3].toDouble(), stretch.fromMetres, 5.0)
        assertEquals(route.metres, stretch.toMetres, 1.0)
        assertEquals(700.0, route.line.maxOf { it.heightMetres }, 0.1)
    }

    @Test
    fun `a leg from partway along an edge climbs only its part`() {
        val route = planner.plan(listOf(RoutePoint(49.005, 20.005), a))
        assertEquals(0.0, route.ascent, 0.1)
        assertEquals(100.0, route.descent, 10.0)
        assertEquals(600.0, route.line.first().heightMetres, 1.0)
        assertEquals(500.0, route.line.last().heightMetres, 0.1)
    }

    @Test
    fun `a leg with no way leaves a gap the length does not cross`() {
        val route = planner.plan(listOf(a, b, e, f))
        assertFalse(route.complete)
        assertNull(route.legs[1])
        val across = route.line.single { it.approach == Approach.NO_WAY }
        val before = route.line[route.line.indexOf(across) - 1]
        assertEquals(before.metres, across.metres, 0.0)
        assertEquals(network.edgeLength[0] + network.edgeLength[4].toDouble(), route.metres, 5.0)
    }
}
