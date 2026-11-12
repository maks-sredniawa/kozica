package app.kozica.data.routes

import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode
import app.kozica.data.hikes.Pace
import app.kozica.data.hikes.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class FollowingTest {
    private val degreesPerMetre = 1 / (6_371_008.8 * PI / 180)

    /** Straight north from (49, 20) by [metres] along the way, and back down where the line turns at [turn]. */
    private fun at(
        metres: Double,
        turn: Double = Double.MAX_VALUE,
    ) = 49.0 + (if (metres <= turn) metres else 2 * turn - metres) * degreesPerMetre

    private val snap = Snap(0, 0.0, 49.0, 20.0, 0.0)

    private fun leg(seconds: Double) = Leg(snap, snap, emptyList(), seconds, 0)

    /** Up a 1 km valley and back down it, the turn being a via point; 600 m climbed and descended at a steady grade. */
    private val outAndBackRoute =
        PlannedRoute(
            legs = listOf(leg(1800.0), leg(1500.0)),
            line =
                (0..20).map { i ->
                    val metres = i * 100.0
                    val height = 1000.0 + 600 * (if (metres <= 1000) metres else 2000 - metres) / 1000
                    RouteSample(at(metres, turn = 1000.0), 20.0, metres, height, Approach.TRAIL)
                },
            ascent = 600.0,
            descent = 600.0,
            seconds = 3300.0,
            secondsToPoint = listOf(0.0, 1800.0, 3300.0),
            demanding = emptyList(),
            metresToPoint = listOf(0.0, 1000.0, 2000.0),
        )

    private val outAndBack = FollowedLine(outAndBackRoute)

    private fun fix(
        alongTheWay: Double,
        atMillis: Long,
        turn: Double = 1000.0,
        accuracy: Double = 10.0,
        age: Long = 0,
        east: Double = 0.0,
    ) = FollowFix(at(alongTheWay, turn), 20.0 + east * degreesPerMetre / 0.656, accuracy, age, atMillis)

    @Test
    fun `on the way up a valley Progress is on the way up, not the way back down`() {
        val progress = outAndBack.follow(Progress(200.0, 0), fix(300.0, 30_000))

        assertEquals(300.0, progress!!.metres, 1.0)
    }

    /** [outAndBackRoute] with the way down drawn 2 m east of the way up, as two sides of a turn are on a real line. */
    private val sideBySide =
        FollowedLine(
            outAndBackRoute.copy(
                line =
                    outAndBackRoute.line.map {
                        if (it.metres > 1000) it.copy(longitude = 20.0 + 2.0 * degreesPerMetre / 0.656) else it
                    },
            ),
        )

    @Test
    fun `just before the turn Progress stays on the way up, though the way down is a little nearer`() {
        val progress = sideBySide.follow(Progress(900.0, 0), fix(980.0, 30_000, east = 1.2))

        assertEquals(980.0, progress!!.metres, 1.0)
    }

    @Test
    fun `past the turn the same place along the valley is the way down`() {
        val atTurn = Progress(1000.0, 0)

        val progress = outAndBack.follow(atTurn, fix(1300.0, 180_000))

        assertEquals(1300.0, progress!!.metres, 1.0)
    }

    @Test
    fun `walking back the way they came leaves Progress where it was`() {
        val progress = outAndBack.follow(Progress(600.0, 0), fix(400.0, 60_000, turn = Double.MAX_VALUE))

        assertEquals(600.0, progress!!.metres, 0.0)
    }

    @Test
    fun `a stale, a vague or a distant fix moves nothing`() {
        val progress = Progress(200.0, 0)

        assertEquals(progress, outAndBack.follow(progress, fix(300.0, 30_000, age = 31_000)))
        assertEquals(progress, outAndBack.follow(progress, fix(300.0, 30_000, accuracy = 60.0)))
        assertEquals(progress, outAndBack.follow(progress, fix(300.0, 30_000, east = 60.0)))
    }

    @Test
    fun `the window grows with the time since the last fix`() {
        val start = Progress(0.0, 0)

        assertEquals(start, outAndBack.follow(start, fix(800.0, 10_000)))
        assertEquals(800.0, outAndBack.follow(start, fix(800.0, 600_000))!!.metres, 1.0)
    }

    @Test
    fun `find me searches the whole line ahead but still takes the first pass`() {
        val found = outAndBack.findMe(null, fix(800.0, 0))

        assertEquals(800.0, found!!.metres, 1.0)
    }

    @Test
    fun `from the start what is left is the sheet's own total, and at the turn exactly the legs'`() {
        val fromStart = outAndBack.left(Progress(0.0, 0))
        val fromTurn = outAndBack.left(Progress(1000.0, 0))

        assertEquals(3300.0, fromStart.toEnd.seconds, 1e-6)
        assertEquals(1800.0, fromStart.toNext.seconds, 1e-6)
        assertEquals(1, fromStart.nextPoint)
        assertEquals(1500.0, fromTurn.toEnd.seconds, 1e-6)
        assertEquals(2, fromTurn.nextPoint)
    }

    @Test
    fun `partway along a leg the time left is the rule's rest of the way, and at a pace it is rounded up`() {
        val lefts = (0..20).map { outAndBack.left(Progress(it * 100.0 + 37, 0)).toEnd.seconds }
        val pace = Pace(1.2, hikes = 3, floored = false)
        val partway = outAndBack.left(Progress(437.0, 0)).toEnd

        assertTrue(lefts.zipWithNext().all { (a, b) -> b <= a })
        assertEquals(3300 - 1800 * 0.437, partway.seconds, 1e-6)
        assertEquals(55, pace.minutes(partway.seconds))
        assertEquals(600.0 * (1000 - 437) / 1000, partway.ascent, 6.0)
    }

    @Test
    fun `from the start the climb left is the sheet's Ascent, though the line's own differs`() {
        val sheet = 610.0
        val line = FollowedLine(outAndBackRoute.copy(ascent = sheet))

        assertEquals(sheet, line.left(Progress(0.0, 0)).toEnd.ascent, 1e-9)
    }

    @Test
    fun `over bumps inside the dead band and up a climb neither the time nor the climb left rises`() {
        val bumpy =
            FollowedLine(
                PlannedRoute(
                    legs = listOf(leg(700.0), leg(600.0)),
                    line =
                        (0..40).map { i ->
                            val metres = i * 25.0
                            val height =
                                1000.0 + 0.6 * (metres - 500).coerceAtLeast(0.0) +
                                    if (i % 2 == 1 && i < 20) 4.9 else 0.0
                            RouteSample(at(metres), 20.0, metres, height, Approach.TRAIL)
                        },
                    ascent = 300.0,
                    descent = 0.0,
                    seconds = 1300.0,
                    secondsToPoint = listOf(0.0, 700.0, 1300.0),
                    demanding = emptyList(),
                    metresToPoint = listOf(0.0, 500.0, 1000.0),
                ),
            )

        val lefts = (0..1000).map { bumpy.left(Progress(it.toDouble(), 0)).toEnd }

        assertTrue(lefts.zipWithNext().all { (a, b) -> b.seconds <= a.seconds && b.ascent <= a.ascent })
        assertEquals(300.0, lefts.first().ascent, 1e-9)
        assertEquals(1300.0, lefts.first().seconds, 1e-9)
    }

    @Test
    fun `the end is reached at the last point, not on passing near it`() {
        val nearEnd = Progress(1990.0, 0)

        assertTrue(outAndBack.ended(nearEnd, fix(1995.0, 0)))
        assertFalse(outAndBack.ended(Progress(1000.0, 0), fix(1995.0, 0)))
    }

    private fun leavingOver(vararg fixes: FollowFix): Leaving? =
        fixes.fold(null as Leaving?) { leaving, fix -> leaving.after(fix, outAndBack.distance(fix)) }

    private fun offEvery5s(
        from: Long,
        to: Long,
        east: Double = 60.0,
        accuracy: Double = 10.0,
    ) = (from..to step 5_000).map { fix(300.0, it, accuracy = accuracy, east = east) }.toTypedArray()

    @Test
    fun `leaving shows after 30 s of counting fixes beyond the distance, and not before`() {
        val before = leavingOver(*offEvery5s(0, 25_000))
        val after = leavingOver(*offEvery5s(0, 30_000))

        assertFalse(before!!.shown)
        assertTrue(after!!.shown)
        assertEquals(60.0, after.metres, 1.0)
    }

    @Test
    fun `the fix's own accuracy widens the leaving distance`() {
        assertNull(leavingOver(*offEvery5s(0, 60_000, east = 45.0, accuracy = 48.0)))
        assertTrue(leavingOver(*offEvery5s(0, 60_000, east = 45.0))!!.shown)
    }

    @Test
    fun `a gap over 30 s restarts the count rather than satisfying it`() {
        val gapped = offEvery5s(0, 10_000) + offEvery5s(45_000, 70_000)

        assertFalse(leavingOver(*gapped)!!.shown)
        assertTrue(leavingOver(*gapped, fix(300.0, 75_000, east = 60.0))!!.shown)
    }

    @Test
    fun `once shown, leaving clears only at a counting fix back within the distance`() {
        val shown = leavingOver(*offEvery5s(0, 30_000))!!
        val back = fix(300.0, 35_000)

        assertEquals(shown, shown.after(back.copy(ageMillis = 31_000), 0.0))
        assertEquals(shown, shown.after(back.copy(accuracyMetres = 60.0), 0.0))
        assertTrue(shown.after(fix(300.0, 120_000, east = 60.0), 60.0)!!.shown)
        assertNull(shown.after(back, outAndBack.distance(back)))
    }

    /** Up 1 km and back down a path [apart] metres east of the way up. */
    private fun upAndDownApart(apart: Double) =
        FollowedLine(
            outAndBackRoute.copy(
                line =
                    outAndBackRoute.line.map {
                        if (it.metres > 1000) it.copy(longitude = 20.0 + apart * degreesPerMetre / 0.656) else it
                    },
            ),
        )

    private fun assertNear(
        latitude: Double,
        longitude: Double,
        point: RoutePoint,
    ) = assertTrue(Router.metres(latitude, longitude, point.latitude, point.longitude) < 2.0)

    @Test
    fun `the leg back on an out-and-back rejoins the way up, never skipping ahead to the way down`() {
        val line = upAndDownApart(30.0)
        val off = fix(800.0, 600_000, east = 100.0)

        assertNear(at(800.0), 20.0, line.rejoin(Progress(500.0, 0), off))
    }

    @Test
    fun `a later pass much nearer than the leaving distance wins the rejoin`() {
        val line = upAndDownApart(90.0)
        val off = fix(800.0, 600_000, east = 150.0)

        assertNear(at(800.0), 20.0 + 90.0 * degreesPerMetre / 0.656, line.rejoin(Progress(500.0, 0), off))
    }

    /** 2 km straight north, a ride's or a walk's, so a fix [metres] on lies on the line. */
    private fun straight(mode: Mode) =
        FollowedLine(
            PlannedRoute(
                legs = listOf(leg(600.0)),
                line = (0..20).map { RouteSample(at(it * 100.0), 20.0, it * 100.0, 500.0, Approach.TRAIL) },
                ascent = 0.0,
                descent = 0.0,
                seconds = 600.0,
                secondsToPoint = listOf(0.0, 600.0),
                demanding = emptyList(),
                metresToPoint = listOf(0.0, 2000.0),
                mode = mode,
            ),
        )

    private val twentyKmh = 20 / 3.6

    @Test
    fun `in Cycling a fix 340 m on after a minute's gap moves Progress, where Hiking's 200 m window does not`() {
        val from = Progress(100.0, 0)
        val on = fix(100.0 + 60 * twentyKmh + 7, 60_000, turn = Double.MAX_VALUE)

        assertTrue(straight(Mode.CYCLING).follow(from, on)!!.metres > 400.0)
        assertEquals(from, straight(Mode.HIKING).follow(from, on))
    }

    @Test
    fun `R9's gap, 340 m and 61 s, moves Progress in Cycling, and the next fix catches up`() {
        val ride = straight(Mode.CYCLING)
        val after = ride.follow(Progress(100.0, 0), fix(440.0, 61_000, turn = Double.MAX_VALUE))!!
        assertTrue(after.metres > 100.0 + 61 * fastestMetresPerSecondIn(Mode.CYCLING) - 1)

        assertEquals(450.0, ride.follow(after, fix(450.0, 62_800, turn = Double.MAX_VALUE))!!.metres, 1.0)
    }

    @Test
    fun `with a fix every few seconds a ride and a walk both keep up`() {
        for (mode in Mode.entries) {
            var progress: Progress? = Progress(0.0, 0)
            for (second in 2..180 step 2) {
                progress =
                    straight(mode).follow(progress, fix(second * twentyKmh, second * 1000L, turn = Double.MAX_VALUE))
            }
            assertEquals("$mode", 180 * twentyKmh, progress!!.metres, 1.0)
        }
    }

    /** 2 km flat north in one leg of [seconds], its first km on [first] and its second on [second]. */
    private fun twoWays(
        mode: Mode,
        first: RideWay,
        second: RideWay,
        seconds: Double = 600.0,
    ) = FollowedLine(
        PlannedRoute(
            legs = listOf(leg(seconds)),
            line =
                (0..20).map {
                    RouteSample(
                        at(it * 100.0),
                        20.0,
                        it * 100.0,
                        500.0,
                        Approach.TRAIL,
                        if (it <=
                            10
                        ) {
                            first
                        } else {
                            second
                        },
                    )
                },
            ascent = 0.0,
            descent = 0.0,
            seconds = seconds,
            secondsToPoint = listOf(0.0, seconds),
            demanding = emptyList(),
            metresToPoint = listOf(0.0, 2000.0),
            mode = mode,
        ),
    )

    private val paved = RideWay(TrailNetwork.PAVED, 0, 0)
    private val rough = RideWay(TrailNetwork.ROUGH, 0, 0)

    @Test
    fun `in Cycling the time to go is the leg's riding seconds spread by the riding rule, slower on rough ground`() {
        val ride = twoWays(Mode.CYCLING, paved, rough)
        val onPaved = 1000 * 3.6 / RIDING_FLAT_KMH[TrailNetwork.PAVED]
        val onRough = 1000 * 3.6 / RIDING_FLAT_KMH[TrailNetwork.ROUGH]

        assertEquals(600.0, ride.left(Progress(0.0, 0)).toEnd.seconds, 1e-9)
        assertEquals(600.0 * onRough / (onPaved + onRough), ride.left(Progress(1000.0, 0)).toEnd.seconds, 1e-6)
        assertEquals(300.0, twoWays(Mode.HIKING, paved, rough).left(Progress(1000.0, 0)).toEnd.seconds, 1e-6)
    }

    @Test
    fun `in Cycling up a valley and back the time to go is exact at each point and never rises`() {
        val ride =
            FollowedLine(
                outAndBackRoute.copy(
                    line = outAndBackRoute.line.map { it.copy(ride = RideWay(TrailNetwork.UNPAVED, 4, 0)) },
                    mode = Mode.CYCLING,
                ),
            )
        val lefts = (0..2000).map { ride.left(Progress(it.toDouble(), 0)).toEnd.seconds }

        assertTrue(lefts.zipWithNext().all { (a, b) -> b <= a })
        assertEquals(3300.0, lefts.first(), 1e-9)
        assertEquals(1500.0, lefts[1000], 1e-6)
        assertEquals(0.0, lefts.last(), 1e-6)
    }

    @Test
    fun `a ride planned over the network is followed by its own riding seconds`() {
        val network = TrailNetwork.read(javaClass.getResource("/trails-fixture.network")!!.readBytes())
        val planned =
            RoutePlanner(network).plan(
                listOf(RoutePoint(49.5, 20.52), RoutePoint(49.51, 20.55)),
                Mode.CYCLING,
                BikeRouteFilter.MTB,
            )
        val line = FollowedLine(planned)
        val lefts = (0..planned.metres.toInt()).map { line.left(Progress(it.toDouble(), 0)).toEnd.seconds }

        assertTrue(planned.line.any { it.ride != null })
        assertEquals(planned.seconds, lefts.first(), 1e-6)
        assertTrue(lefts.zipWithNext().all { (a, b) -> b <= a + 1e-9 })
    }

    @Test
    fun `the leg back rejoins within the window ahead of Progress`() {
        val off = fix(800.0, 60_000, east = 100.0)

        assertNear(at(WINDOW_LEAST_METRES), 20.0, outAndBack.rejoin(Progress(0.0, 0), off))
    }
}
