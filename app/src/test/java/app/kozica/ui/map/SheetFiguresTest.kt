package app.kozica.ui.map

import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode
import app.kozica.data.hikes.Pace
import app.kozica.data.hikes.minutes
import app.kozica.data.routes.DemandingStretch
import app.kozica.data.routes.RideGrade
import app.kozica.data.routes.RoutePlanner
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.TrailNetwork
import app.kozica.data.routes.ridingMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/phase-7b.md § Tests, the sheet's figures: a ride's time by the riding rule alone, "≈" and rounded to 5 minutes, and no Pace line. */
class SheetFiguresTest {
    private val network = TrailNetwork.read(javaClass.getResource("/trails-fixture.network")!!.readBytes())
    private val planner = RoutePlanner(network)
    private val q = RoutePoint(49.5, 20.52)
    private val s = RoutePoint(49.5, 20.54)
    private val t = RoutePoint(49.51, 20.55)
    private val hiking = NamedStretch(DemandingStretch(0.0, 100.0, 3), "Zawrat", "Kozi Wierch")

    private val pace = Pace(1.3, hikes = 4, floored = false)

    @Test
    fun `a riding time is rounded to the nearest 5 minutes, and under 5 is none`() {
        assertEquals(0, ridingMinutes(0.0))
        assertEquals(0, ridingMinutes(299.0))
        assertEquals(5, ridingMinutes(300.0))
        assertEquals(5, ridingMinutes(449.0))
        assertEquals(10, ridingMinutes(451.0))
        assertEquals(10, ridingMinutes(600.0))
        assertEquals(15, ridingMinutes(1049.0))
        assertEquals(100, ridingMinutes(6000.0))
    }

    @Test
    fun `a ride shows its riding time and each point's, rounded alike, with no Pace and no Pace line`() {
        val ride = planner.plan(listOf(q, s, t), Mode.CYCLING)
        val figures = sheetFigures(ride, emptyList())

        assertEquals(
            ShownTime(ridingMinutes(ride.seconds), approximate = true),
            shownTime(ride.seconds, Mode.CYCLING, pace),
        )
        assertEquals(ride.seconds, ride.secondsToPoint.last()!!, 1e-9)
        assertEquals(
            ShownTime(ridingMinutes(ride.secondsToPoint[1]!!), approximate = true),
            shownTime(ride.secondsToPoint[1]!!, Mode.CYCLING, pace),
        )
        assertFalse(figures.paceLine)
    }

    @Test
    fun `a walk's time is at the Pace and rounded up, as it was`() {
        assertEquals(ShownTime(pace.minutes(1000.0), approximate = false), shownTime(1000.0, Mode.HIKING, pace))
        assertEquals(ShownTime(25, approximate = false), shownTime(1000.0, Mode.HIKING, pace))
    }

    @Test
    fun `a walk's sheet is as it was`() {
        val figures =
            sheetFigures(planner.plan(listOf(RoutePoint(49.0, 20.0), RoutePoint(49.0, 20.02))), listOf(hiking))
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
