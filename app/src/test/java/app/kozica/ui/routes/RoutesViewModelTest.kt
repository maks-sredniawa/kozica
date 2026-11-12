package app.kozica.ui.routes

import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode
import app.kozica.data.SavedRoute
import app.kozica.data.gpx.Gpx
import app.kozica.data.gpx.routeGpx
import app.kozica.data.routes.PlanKey
import app.kozica.data.routes.RoutePlanner
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.RouteTotals
import app.kozica.data.routes.TrailNetwork
import app.kozica.data.routes.ridingMinutes
import app.kozica.ui.common.GpxExport
import app.kozica.ui.common.GpxFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RoutesViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val network =
        TrailNetwork.read(RoutesViewModelTest::class.java.getResource("/trails-fixture.network")!!.readBytes())

    private val across = listOf(RoutePoint(49.0, 20.0), RoutePoint(49.0, 20.02))
    private val nowhere = listOf(RoutePoint(52.23, 21.01), RoutePoint(52.24, 21.02))

    private val planner = RoutePlanner(network)

    // The fixture's rides (CyclingRouterTest): p to q rides a longer paved line than Hiking walks,
    // g to i rides an MTB line one way only.
    private val pq = listOf(RoutePoint(49.5, 20.5), RoutePoint(49.5, 20.52))
    private val ig = listOf(RoutePoint(49.3, 20.32), RoutePoint(49.3, 20.3))

    @Test
    fun `saved Routes by name, each planned for its length and time, or left without where it cannot be`() =
        runTest {
            val asked = MutableStateFlow<String?>(null)
            val viewModel =
                RoutesViewModel(
                    saved = flowOf(listOf(SavedRoute("z", "Żleb", nowhere), SavedRoute("a", "Ascent", across))),
                    totals = RouteTotals(planner::plan),
                    renameRoute = { _, _ -> },
                    deleteRoute = {},
                    routeToShow = asked,
                    compute = Dispatchers.Main,
                )

            val rows = planned(viewModel)

            assertEquals(listOf("Ascent", "Żleb"), rows.map { it.route.name })
            assertEquals(RoutePlanner(network).plan(across).minutes, rows[0].minutes)
            assertNull("a Route with no way over the Trails has no totals", rows[1].minutes)

            viewModel.show("a")
            assertEquals("a", asked.value)
        }

    @Test
    fun `a Route planned on one visit to the tab is not planned again on the next`() =
        runTest {
            val asked = mutableListOf<List<RoutePoint>>()
            val totals =
                RouteTotals { points, mode, filter ->
                    asked += points
                    planner.plan(points, mode, filter)
                }
            val saved = flowOf(listOf(SavedRoute("a", "Ascent", across)))

            repeat(2) {
                val visit = RoutesViewModel(saved, totals, { _, _ -> }, {}, MutableStateFlow(null), Dispatchers.Main)
                val row = planned(visit).single()
                assertEquals(RoutePlanner(network).plan(across).minutes, row.minutes)
            }

            assertEquals(listOf(across), asked)
        }

    @Test
    fun `a ride's row is planned under its Mode and chip, with its length and riding time, apart from the same walk`() =
        runTest {
            val asked = mutableListOf<PlanKey>()
            val totals =
                RouteTotals { points, mode, filter ->
                    asked += PlanKey(points, mode, filter)
                    planner.plan(points, mode, filter)
                }
            val saved =
                listOf(
                    SavedRoute("w", "A walk", pq),
                    SavedRoute("r", "B ride", pq, Mode.CYCLING, BikeRouteFilter.TOURING),
                )
            val viewModel =
                RoutesViewModel(flowOf(saved), totals, { _, _ -> }, {}, MutableStateFlow(null), Dispatchers.Main)

            val (walk, ride) = every(viewModel)

            assertEquals(
                listOf(
                    PlanKey(pq, Mode.HIKING, BikeRouteFilter.ALL),
                    PlanKey(pq, Mode.CYCLING, BikeRouteFilter.TOURING),
                ),
                asked,
            )
            val walked = planner.plan(pq)
            assertEquals(walked.metres, walk.metres!!, 1e-9)
            assertEquals(walked.minutes, walk.minutes)
            assertEquals(walked.minutes, walk.signpostMinutes)
            assertFalse(walk.approximate)
            val ridden = planner.plan(pq, Mode.CYCLING, BikeRouteFilter.TOURING)
            assertEquals(ridden.metres, ride.metres!!, 1e-9)
            assertTrue("the ride takes its own line", ride.metres != walk.metres)
            assertEquals(ridingMinutes(ridden.seconds), ride.minutes)
            assertTrue(ride.approximate)
            assertNull("no signposts' time on a ride", ride.signpostMinutes)
        }

    @Test
    fun `a ride's GPX export is planned under its chip, and with no way over the Bike routes says so`() =
        runTest {
            val asked = mutableListOf<PlanKey>()
            val written = mutableListOf<Gpx>()
            val saved =
                listOf(
                    SavedRoute("a", "A ride", pq, Mode.CYCLING, BikeRouteFilter.TOURING),
                    SavedRoute("b", "B against MTB", ig, Mode.CYCLING, BikeRouteFilter.MTB),
                )
            val viewModel =
                RoutesViewModel(
                    flowOf(saved),
                    RouteTotals(planner::plan),
                    { _, _ -> },
                    {},
                    MutableStateFlow(null),
                    Dispatchers.Main,
                    planRoute = { points, mode, filter ->
                        asked += PlanKey(points, mode, filter)
                        planner.plan(points, mode, filter)
                    },
                    writeGpx = { gpx, _ ->
                        written += gpx
                        File(gpx.name)
                    },
                )
            every(viewModel, ready = { it.route.id == "b" || it.metres != null })

            viewModel.exportGpx("a")
            assertEquals(GpxExport(file = File("A ride")), viewModel.gpx.value)
            assertEquals(
                listOf(routeGpx("A ride", planner.plan(pq, Mode.CYCLING, BikeRouteFilter.TOURING), pq)),
                written,
            )
            viewModel.gpxShared()

            viewModel.exportGpx("b")
            assertEquals(GpxExport(failure = GpxFailure.NoWayOverBikeRoutes), viewModel.gpx.value)
            assertEquals(
                listOf(
                    PlanKey(pq, Mode.CYCLING, BikeRouteFilter.TOURING),
                    PlanKey(ig, Mode.CYCLING, BikeRouteFilter.MTB),
                ),
                asked,
            )
        }

    private suspend fun every(
        viewModel: RoutesViewModel,
        ready: (RouteRow) -> Boolean = { it.metres != null },
    ): List<RouteRow> =
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(5.seconds) { viewModel.state.first { state -> state.routes?.all(ready) == true } }
        }!!.routes!!

    private suspend fun planned(viewModel: RoutesViewModel): List<RouteRow> =
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(5.seconds) {
                viewModel.state.first { state -> state.routes?.firstOrNull()?.minutes != null }
            }
        }!!.routes!!
}
