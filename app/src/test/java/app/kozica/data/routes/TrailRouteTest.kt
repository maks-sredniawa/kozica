package app.kozica.data.routes

import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

/** docs/phase-6c.md § Tests and docs/phase-7b.md §§ 5 and 6, the via points, over the fixture `scripts/test_build_network.py` writes. */
class TrailRouteTest {
    private val network = TrailNetwork.read(javaClass.getResource("/trails-fixture.network")!!.readBytes())
    private val router = Router(network)
    private val a = 49.0 to 20.0
    private val b = 49.0 to 20.01
    private val c = 49.0 to 20.02
    private val d = 49.01 to 20.01

    private fun step(
        edge: Int,
        reversed: Boolean = false,
        gapBefore: Boolean = false,
    ) = (edge shl 2) or (if (gapBefore) 2 else 0) or (if (reversed) 1 else 0)

    private val p = 49.5 to 20.5
    private val q = 49.5 to 20.52
    private val s = 49.5 to 20.54
    private val t = 49.51 to 20.55

    private fun along(vararg steps: Int) = along(Mode.HIKING, BikeRouteFilter.ALL, *steps)

    private fun along(
        mode: Mode,
        filter: BikeRouteFilter,
        vararg steps: Int,
    ) = network.pointsAlong(steps, router, mode, filter).map {
        Math.round(it.latitude * 1e4) / 1e4 to
            Math.round(it.longitude * 1e4) / 1e4
    }

    private fun routeMetres(vararg steps: Int) =
        RoutePlanner(network, router).plan(along(*steps).map { RoutePoint(it.first, it.second) }).metres

    private fun walkMetres(vararg steps: Int) = steps.sumOf { network.edgeLength[it ushr 2].toDouble() }

    @Test
    fun `a Trail the router follows by itself both ways needs only its ends`() {
        assertEquals(listOf(d, b), along(step(2, reversed = true), step(0)))
    }

    @Test
    fun `a one-way stretch is a span of its own, so the two-way one before it still holds reversed`() {
        assertEquals(listOf(a, b, c), along(step(0), step(1)))
    }

    @Test
    fun `a loop keeps to its own way round`() {
        val loop = intArrayOf(step(0), step(1), step(3, reversed = true), step(2, reversed = true))
        assertEquals(listOf(a, b, c, d, a), along(*loop))
        assertEquals(walkMetres(*loop), routeMetres(*loop), 1.0)
    }

    @Test
    fun `a walk through the same junction twice keeps both visits`() {
        val back = intArrayOf(step(2), step(3), step(3, reversed = true))
        assertEquals(listOf(a, d, c, d), along(*back))
        assertEquals(walkMetres(*back), routeMetres(*back), 1.0)
    }

    @Test
    fun `a via point holds the Route where another Trail is a shortcut`() {
        assertEquals(listOf(a, d, c), along(step(2), step(3)))
        assertEquals(walkMetres(step(2), step(3)), routeMetres(step(2), step(3)), 1.0)
    }

    @Test
    fun `a gap gets a point on each side`() {
        assertEquals(listOf(d, a, b, c), along(step(2, reversed = true), step(1, gapBefore = true)))
    }

    @Test
    fun `an empty walk has no points`() {
        assertEquals(emptyList<Pair<Double, Double>>(), along())
    }

    @Test
    fun `a ride along a rough Touring route is held there where a paved one is faster, and a walk needs no point`() {
        assertEquals(listOf(p, 49.5 to 20.51, q), along(Mode.CYCLING, BikeRouteFilter.TOURING, step(8)))
        assertEquals(listOf(p, q), along(step(8)))
    }

    @Test
    fun `in Cycling a stretch one-way for the chip's kind is a span of its own, and under All it is not one-way`() {
        assertEquals(listOf(q, s, t), along(Mode.CYCLING, BikeRouteFilter.MTB, step(11), step(12)))
        assertEquals(listOf(q, t), along(Mode.CYCLING, BikeRouteFilter.ALL, step(11), step(12)))
    }

    private val k = 49.9 to 20.9
    private val l = 49.905 to 20.91
    private val m = 49.905 to 20.93
    private val n = 49.9 to 20.94
    private val there = intArrayOf(step(17), step(18), step(20))
    private val back = intArrayOf(step(20, reversed = true), step(19), step(17, reversed = true))

    private fun alongBoth(
        filter: BikeRouteFilter,
        back: IntArray = this.back,
    ) = network.pointsAlong(there, router, Mode.CYCLING, filter, back = back).map {
        Math.round(it.latitude * 1e4) / 1e4 to
            Math.round(it.longitude * 1e4) / 1e4
    }

    private fun ridden(points: List<Pair<Double, Double>>) =
        RoutePlanner(network, router)
            .plan(points.map { RoutePoint(it.first, it.second) }, Mode.CYCLING, BikeRouteFilter.TOURING)
            .legs
            .flatMap { leg -> leg!!.steps.filter { it.from != it.to }.map { it.edge to it.forward } }

    @Test
    fun `along a pair of strands the via points stand where they rejoin, the Route rides there and Reverse back`() {
        val points = alongBoth(BikeRouteFilter.TOURING)
        assertEquals(listOf(k, m, n), points)
        assertEquals(listOf(17 to true, 18 to true, 20 to true), ridden(points))
        assertEquals(listOf(20 to false, 19 to true, 17 to false), ridden(points.asReversed()))
    }

    @Test
    fun `no halfway point stands on a strand only one walk rides`() {
        assertEquals(listOf(k, l, m, n), alongBoth(BikeRouteFilter.ALL))
    }

    @Test
    fun `a gap in the back walk alone gets a via point on each side`() {
        val gapped = intArrayOf(step(20, reversed = true), step(17, reversed = true, gapBefore = true))
        assertEquals(listOf(k, l, m, n), alongBoth(BikeRouteFilter.TOURING, gapped))
    }
}
