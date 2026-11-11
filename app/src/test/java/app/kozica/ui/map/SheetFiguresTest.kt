package app.kozica.ui.map

import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode
import app.kozica.data.routes.DemandingStretch
import app.kozica.data.routes.RideGrade
import app.kozica.data.routes.RoutePlanner
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.TrailNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/phase-7b.md § Tests, the sheet's figures: without phase 7b's G a ride shows no time and no Pace line. */
class SheetFiguresTest {
    private val network = TrailNetwork.read(javaClass.getResource("/trails-fixture.network")!!.readBytes())
    private val planner = RoutePlanner(network)
    private val q = RoutePoint(49.5, 20.52)
    private val s = RoutePoint(49.5, 20.54)
    private val t = RoutePoint(49.51, 20.55)
    private val hiking = NamedStretch(DemandingStretch(0.0, 100.0, 3), "Zawrat", "Kozi Wierch")

    @Test
    fun `a ride shows no time, no point's time and no Pace line`() {
        val figures = sheetFigures(planner.plan(listOf(q, t), Mode.CYCLING, BikeRouteFilter.MTB), emptyList())
        assertFalse(figures.time)
        assertFalse(figures.paceLine)
    }

    @Test
    fun `a walk's sheet is as it was`() {
        val figures =
            sheetFigures(planner.plan(listOf(RoutePoint(49.0, 20.0), RoutePoint(49.0, 20.02))), listOf(hiking))
        assertTrue(figures.time)
        assertTrue(figures.paceLine)
        assertEquals(listOf(RouteWarning.Demanding(hiking)), figures.warnings)
    }

    @Test
    fun `a ride's warning is its own, uphill read from mtb scale uphill, and no hiking grade`() {
        val up = sheetFigures(planner.plan(listOf(q, t), Mode.CYCLING, BikeRouteFilter.MTB), listOf(hiking))
        assertEquals(listOf(RouteWarning.Ride(RideGrade(2, uphill = true))), up.warnings)
        val down = sheetFigures(planner.plan(listOf(t, q), Mode.CYCLING), emptyList())
        assertEquals(listOf(RouteWarning.Ride(RideGrade(3, uphill = false))), down.warnings)
    }

    @Test
    fun `a stretch under S2 warns of nothing`() {
        assertTrue(sheetFigures(planner.plan(listOf(q, s), Mode.CYCLING), emptyList()).warnings.isEmpty())
    }

    @Test
    fun `a ride with no way says so over the Bike routes`() {
        val i = RoutePoint(49.3, 20.32)
        val g = RoutePoint(49.3, 20.3)
        val figures = sheetFigures(planner.plan(listOf(i, g), Mode.CYCLING, BikeRouteFilter.MTB), emptyList())
        assertEquals(listOf(RouteWarning.NoWay(0, cycling = true)), figures.warnings)
    }
}
