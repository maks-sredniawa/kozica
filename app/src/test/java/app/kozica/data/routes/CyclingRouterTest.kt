package app.kozica.data.routes

import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/phase-7b.md § Tests: the router and `EdgePart`'s riding half in Cycling, over the fixture's rides. */
class CyclingRouterTest {
    private val network = TrailNetwork.read(javaClass.getResource("/trails-fixture.network")!!.readBytes())
    private val router = Router(network)
    private val planner = RoutePlanner(network, router)

    private val g = RoutePoint(49.3, 20.3)
    private val i = RoutePoint(49.3, 20.32)
    private val p = RoutePoint(49.5, 20.5)
    private val q = RoutePoint(49.5, 20.52)
    private val s = RoutePoint(49.5, 20.54)
    private val t = RoutePoint(49.51, 20.55)
    private val u = RoutePoint(49.7, 20.7)
    private val y = RoutePoint(49.7, 20.78)
    private val bikeEdges = listOf(5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)

    private fun ways(
        from: RoutePoint,
        to: RoutePoint,
        mode: Mode = Mode.CYCLING,
        filter: BikeRouteFilter = BikeRouteFilter.ALL,
    ): List<Long>? =
        router.leg(from.latitude, from.longitude, to.latitude, to.longitude, mode, filter)?.steps?.map {
            network.edgeWay[it.edge]
        }

    @Test
    fun `in Cycling a longer paved line beats a shorter rough one, where Hiking takes the shorter`() {
        assertEquals(listOf(109L, 110L), ways(p, q))
        assertEquals(listOf(108L), ways(p, q, Mode.HIKING))
        val leg = router.leg(p.latitude, p.longitude, q.latitude, q.longitude, Mode.CYCLING)!!
        assertEquals((network.edgeRidingForward[9] + network.edgeRidingForward[10]).toDouble(), leg.seconds, 0.01)
    }

    @Test
    fun `in Cycling it keeps off an edge only a Trail is on`() {
        assertEquals(listOf(107L), ways(g, i))
        assertEquals(listOf(105L, 106L), ways(g, i, Mode.HIKING))
    }

    @Test
    fun `under a chip it keeps off the other kind`() {
        assertFalse(107L in ways(g, i, filter = BikeRouteFilter.TOURING)!!)
        assertTrue(ways(p, q, filter = BikeRouteFilter.MTB)!!.none { it in listOf(108L, 109L, 110L) })
        assertEquals(listOf(109L, 110L), ways(p, q, filter = BikeRouteFilter.TOURING))
        assertEquals(listOf(107L), ways(g, i, filter = BikeRouteFilter.MTB))
    }

    @Test
    fun `never against an edge its way's own tag directs`() {
        assertNull(ways(i, g, filter = BikeRouteFilter.MTB))
        assertFalse(107L in ways(i, g)!!)
    }

    @Test
    fun `never against an edge only its Bike route's role or signed_direction directs`() {
        assertEquals(listOf(111L), ways(q, s, filter = BikeRouteFilter.MTB))
        assertFalse(111L in ways(s, q, filter = BikeRouteFilter.MTB)!!)
    }

    @Test
    fun `under All an edge one kind directs and the other does not is open both ways`() {
        assertEquals(listOf(111L), ways(s, q))
        assertEquals(listOf(111L), ways(s, q, filter = BikeRouteFilter.TOURING))
    }

    @Test
    fun `its heuristic never exceeds a leg's riding seconds`() {
        val fastest = Travel(network, Mode.CYCLING, BikeRouteFilter.ALL).fastestMetresPerSecond
        assertEquals(19.28 / 3.6, fastest, 1e-9)
        for (edge in bikeEdges) {
            assertTrue(network.edgeRidingForward[edge] >= network.edgeLength[edge] / fastest)
            assertTrue(network.edgeRidingBackward[edge] >= network.edgeLength[edge] / fastest)
        }
        val points = listOf(g, i, p, q, s, t, u, y)
        for (filter in BikeRouteFilter.entries) {
            for (from in points) {
                for (to in points) {
                    val leg = router.leg(from.latitude, from.longitude, to.latitude, to.longitude, Mode.CYCLING, filter)
                    val straight = Router.metres(from.latitude, from.longitude, to.latitude, to.longitude)
                    if (leg != null) assertTrue("$from to $to under $filter", straight / fastest <= leg.seconds + 1e-6)
                }
            }
        }
    }

    @Test
    fun `a point off the Bike routes joins at the rough flat speed, ten times while choosing and once in the time`() {
        val rough = 11.6 / 3.6
        val near = Snap(8, 0.5, 49.5, 20.51, 50.0)
        val far = Snap(10, 0.5, 49.503, 20.515, 200.0)
        val end = Snap(10, 1.0, q.latitude, q.longitude, 0.0)
        val fromNear = network.part(8, 0.5, 1.0, Mode.CYCLING).seconds
        val fromFar = network.part(10, 0.5, 1.0, Mode.CYCLING).seconds
        assertTrue(
            "unweighted, the far join would win",
            far.metresAway / rough + fromFar < near.metresAway / rough + fromNear,
        )
        val leg = router.leg(listOf(near, far), listOf(end), q.latitude, q.longitude, Mode.CYCLING)!!
        assertEquals(near, leg.start)
        assertEquals(near.metresAway / rough + fromNear, leg.seconds, 0.01)
    }

    @Test
    fun `run over a whole edge's points, the riding half gives the seconds the build stored`() {
        for (edge in bikeEdges) {
            val metres = network.along(edge).toList()
            val first = network.edgeFirstPoint[edge]
            val heights = metres.indices.map { network.pointHeight[first + it] / 10.0 }
            assertEquals(
                network.edgeRidingForward[edge].toDouble(),
                network.ridingSeconds(edge, metres, heights, true),
                0.05,
            )
            assertEquals(
                network.edgeRidingBackward[edge].toDouble(),
                network.ridingSeconds(edge, metres, heights, false),
                0.05,
            )
        }
    }

    @Test
    fun `a part of an edge is ridden by the same rule, so almost all of it takes almost the whole edge's time`() {
        val almost = 1 - 1e-9
        for (edge in bikeEdges) {
            assertEquals(
                network.edgeRidingForward[edge].toDouble(),
                network.part(edge, 0.0, almost, Mode.CYCLING).seconds,
                0.05,
            )
            assertEquals(
                network.edgeRidingBackward[edge].toDouble(),
                network.part(edge, almost, 0.0, Mode.CYCLING).seconds,
                0.05,
            )
        }
        assertTrue(network.part(12, 0.0, 0.5, Mode.CYCLING).seconds < network.edgeRidingForward[12])
    }

    @Test
    fun `the warning is the hardest grade from S2 up in the direction ridden`() {
        assertEquals(
            RideGrade(2, uphill = true),
            planner.plan(listOf(q, t), Mode.CYCLING, BikeRouteFilter.MTB).hardestRide,
        )
        assertEquals(RideGrade(3, uphill = false), planner.plan(listOf(t, q), Mode.CYCLING).hardestRide)
        assertEquals(RideGrade(2, uphill = false), planner.plan(listOf(g, i), Mode.CYCLING).hardestRide)
        assertNull(planner.plan(listOf(q, s), Mode.CYCLING).hardestRide)
        assertNull(planner.plan(listOf(p, q), Mode.CYCLING).hardestRide)
    }
}
