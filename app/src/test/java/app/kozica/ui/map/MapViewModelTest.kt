package app.kozica.ui.map

import android.location.Location
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.viewModelScope
import app.kozica.data.AppPreferences
import app.kozica.data.MapCamera
import app.kozica.data.Mode
import app.kozica.data.SavedRoute
import app.kozica.data.regions.GeoBounds
import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionState
import app.kozica.data.routes.BikeRouteDetail
import app.kozica.data.routes.FOLLOWED_KEPT_MILLIS
import app.kozica.data.routes.FollowFix
import app.kozica.data.routes.FollowedRoute
import app.kozica.data.routes.Place
import app.kozica.data.routes.PlaceKind
import app.kozica.data.routes.PlaceSearch
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.Progress
import app.kozica.data.routes.RoutePlanner
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.SearchResult
import app.kozica.data.routes.TrailColour
import app.kozica.data.routes.TrailDetail
import app.kozica.data.routes.TrailFacts
import app.kozica.data.routes.TrailNetwork
import app.kozica.data.routes.bikeRouteFacts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    // Work a ViewModel resumes on Main after resetMain fails whichever test runs next, so each one's
    // scope is joined while Main is still set.
    @After
    fun tearDown() {
        val scopes = viewModels.map { it.viewModelScope.coroutineContext.job }
        viewModels.clear()
        try {
            runBlocking { withTimeout(5.seconds) { scopes.forEach { it.cancelAndJoin() } } }
        } finally {
            Dispatchers.resetMain()
        }
    }

    // On a cold start the resume effect reads location before the screen subscribes to the state.
    @Test
    fun `the style arrives while the first fix is still pending`() =
        runTest {
            val viewModel =
                MapViewModel(
                    regions = flowOf(emptyList()),
                    preferences = AppPreferences(NoPreferences),
                    readAsset = { """{"version":8,"sources":{},"layers":[]}""" },
                    applicationScope = CoroutineScope(Dispatchers.Unconfined),
                    positions = { MutableSharedFlow<Location>() },
                    fetch = {},
                    confirmFetch = { false },
                    io = Dispatchers.Main,
                    compute = Dispatchers.Main,
                ).tracked()
            viewModel.darkTheme(false)
            viewModel.locationRead(LocationRead(LocationGrant.PRECISE, on = true))

            val drawn =
                withContext(Dispatchers.Default) {
                    withTimeoutOrNull(5.seconds) { viewModel.state.first { it.style != null } }
                }

            assertNotNull("no style while waiting for a fix", drawn)
        }

    @Test
    fun `the first launch opens on the Tatry`() =
        runTest {
            val viewModel = mapViewModel(flowOf(listOf(tatry(READY))))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            assertEquals(
                49.23,
                viewModel.state.value.camera
                    ?.latitude,
            )
            assertEquals(
                11.0,
                viewModel.state.value.camera
                    ?.zoom,
            )
        }

    @Test
    fun `a stored camera wins over the Tatry`() =
        runTest {
            val preferences = AppPreferences(MemoryPreferences())
            val stored = MapCamera(latitude = 50.74, longitude = 15.74, zoom = 13.0)
            preferences.setMapCamera(stored)
            val viewModel = mapViewModel(flowOf(listOf(tatry(READY))), preferences)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            assertEquals(stored, viewModel.state.value.camera)
        }

    @Test
    fun `the map asks once for every Region never asked for`() =
        runTest {
            val fetched = mutableListOf<List<String>>()
            val regions =
                MutableStateFlow(
                    listOf(
                        tatry(RegionState.NotDownloaded),
                        bieszczady(RegionState.Downloading(percent = 30)),
                        Region("beskid_niski", name = 0, RegionState.Failed(errorCode = -6)),
                    ),
                )
            val viewModel = mapViewModel(regions, fetch = { fetched += it })
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            regions.value = listOf(tatry(RegionState.Downloading(percent = 0)), bieszczady(BIESZCZADY_READY))

            assertEquals(listOf(listOf("tatry")), fetched)
        }

    @Test
    fun `the relief is fetched and counted with the Regions`() =
        runTest {
            val fetched = mutableListOf<List<String>>()
            val relief = Region("relief", name = 0, RegionState.NotDownloaded)
            val viewModel =
                mapViewModel(flowOf(listOf(tatry(READY))), fetch = { fetched += it }, relief = flowOf(relief))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            assertEquals(listOf(listOf("relief")), fetched)
            assertEquals(MapProgress.Downloading(ready = 1, total = 2), viewModel.state.value.progress)
        }

    @Test
    fun `long-pressed points are named after the place near them and planned from two on`() =
        runTest {
            val viewModel = routingViewModel()
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)

            val route =
                settled(viewModel) { state ->
                    state.route.planned != null &&
                        state.route.points.all { it.label != null }
                }.route

            assertEquals(listOf("Near 49.0", "Near 49.0"), route.points.map { it.label })
            assertNotNull(route.planned?.legs?.single())
        }

    @Test
    fun `changing the Route while following it stops following, and says why`() =
        runTest {
            val viewModel = routingViewModel()
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)
            settled(viewModel) { it.route.planned?.complete == true && !it.route.planning }

            viewModel.follow()
            assertNotNull(settled(viewModel) { it.route.following != null }.route.following)
            viewModel.reverse()

            val route = settled(viewModel) { it.route.followingNote != null }.route
            assertNull(route.following)
            assertEquals(FollowingNote.ROUTE_CHANGED, route.followingNote)
        }

    @Test
    fun `follow is refused while the Route is planning`() =
        runTest {
            val held = CompletableDeferred<Unit>()
            var plans = 0
            val viewModel =
                routingViewModel(planRoute = {
                    if (plans++ > 0) held.await()
                    RoutePlanner(fixture).plan(it)
                })
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)
            settled(viewModel) { it.route.planned?.complete == true && !it.route.planning }
            viewModel.reverse()
            settled(viewModel) { it.route.planning }

            viewModel.follow()

            assertNull(settled(viewModel) { true }.route.following)
            held.complete(Unit)
        }

    @Test
    fun `follow is refused on a Route with a leg missing`() =
        runTest {
            val viewModel = routingViewModel()
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(52.23, 21.01)
            settled(viewModel) { it.route.planned?.complete == false && !it.route.planning }

            viewModel.follow()

            assertNull(settled(viewModel) { true }.route.following)
        }

    @Test
    fun `a vague or accuracy-less fix at the end does not end following`() =
        runTest {
            val viewModel = routingViewModel()
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)
            val line =
                settled(
                    viewModel,
                ) { it.route.planned?.complete == true && !it.route.planning }.route.planned!!.line
            viewModel.follow()
            val midway = line[line.size / 2]
            viewModel.followFix(FollowFix(midway.latitude, midway.longitude, 10.0, 0, 0))
            val progress = settled(viewModel) { it.route.following?.progress != null }.route.following!!.progress

            val end = line.last()
            viewModel.followFix(FollowFix(end.latitude, end.longitude, 2000.0, 0, 60_000))
            viewModel.followFix(FollowFix(end.latitude, end.longitude, Double.MAX_VALUE, 0, 60_000))

            val route = settled(viewModel) { true }.route
            assertNull(route.followingNote)
            assertEquals(progress, route.following?.progress)
        }

    @Test
    fun `find me with no fix or far from the Route says so, until the next Progress`() =
        runTest {
            val viewModel = routingViewModel()
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)
            val line =
                settled(
                    viewModel,
                ) { it.route.planned?.complete == true && !it.route.planning }.route.planned!!.line
            viewModel.follow()

            viewModel.findMeOnRoute(null)
            assertEquals(
                FollowingNote.NOT_FOUND,
                settled(viewModel) { it.route.followingNote != null }.route.followingNote,
            )
            viewModel.dismissFollowingNote()
            viewModel.findMeOnRoute(FollowFix(49.5, 20.0, 10.0, 0, 0))
            assertEquals(
                FollowingNote.NOT_FOUND,
                settled(viewModel) { it.route.followingNote != null }.route.followingNote,
            )

            val midway = line[line.size / 2]
            viewModel.followFix(FollowFix(midway.latitude, midway.longitude, 10.0, 0, 0))
            val route = settled(viewModel) { it.route.following?.progress != null }.route
            assertNull(route.followingNote)
        }

    /** A followed Route with Progress at its middle, and a fix [north] metres north of it at [atMillis]. */
    private suspend fun followedMidway(viewModel: MapViewModel): (north: Double, atMillis: Long) -> FollowFix {
        viewModel.addPoint(49.0, 20.0)
        viewModel.addPoint(49.0, 20.02)
        val line = settled(viewModel) { it.route.planned?.complete == true && !it.route.planning }.route.planned!!.line
        viewModel.follow()
        val midway = line[line.size / 2]
        viewModel.followFix(FollowFix(midway.latitude, midway.longitude, 10.0, 0, 0))
        settled(viewModel) { it.route.following?.progress != null }
        return { north, at -> FollowFix(midway.latitude + north / 111_195.0, midway.longitude, 10.0, 0, at) }
    }

    @Test
    fun `following keeps the Route and its Progress in the settings, and Stop clears them`() =
        runTest {
            val preferences = AppPreferences(MemoryPreferences())
            val viewModel = routingViewModel(preferences = preferences, now = { 5_000 })
            followedMidway(viewModel)
            val progress =
                viewModel.state.value.route.following!!
                    .progress

            val kept = preferences.followedRoute.first()
            assertEquals(viewModel.state.value.route.points, kept?.points)
            assertEquals(progress, kept?.progress)
            assertEquals(5_000L, kept?.touchedAtMillis)

            viewModel.stopFollowing()
            settled(viewModel) { it.route.following == null }
            assertNull(preferences.followedRoute.first())
        }

    @Test
    fun `a Route followed when the app closed is followed again from its Progress`() =
        runTest {
            val preferences = AppPreferences(MemoryPreferences())
            val points = listOf(RoutePoint(49.0, 20.0, "Start"), RoutePoint(49.0, 20.02, "End"))
            val progress = Progress(700.0, 1_000)
            preferences.setFollowedRoute(FollowedRoute(points, "Saved", "route-1", progress, touchedAtMillis = 0))

            val viewModel = routingViewModel(preferences = preferences, now = { FOLLOWED_KEPT_MILLIS })
            val route = settled(viewModel) { it.route.following != null }.route

            assertEquals(points, route.points)
            assertEquals("Saved", route.name)
            assertEquals("route-1", route.savedId)
            assertEquals(progress, route.following?.progress)
        }

    @Test
    fun `a followed Route untouched for over a day is dropped on the next open`() =
        runTest {
            val preferences = AppPreferences(MemoryPreferences())
            val points = listOf(RoutePoint(49.0, 20.0), RoutePoint(49.0, 20.02))
            preferences.setFollowedRoute(FollowedRoute(points, progress = Progress(700.0, 0), touchedAtMillis = 0))

            val viewModel = routingViewModel(preferences = preferences, now = { FOLLOWED_KEPT_MILLIS + 1 })
            stored(preferences) { it == null }
            val route = settled(viewModel) { it.camera != null }.route

            assertNull(route.following)
            assertEquals(emptyList<RoutePoint>(), route.points)
        }

    @Test
    fun `a stored Route that no longer reads is removed on the next open`() =
        runTest {
            val key = stringPreferencesKey("followed_route")
            val store = MemoryPreferences().apply { data.value = preferencesOf(key to "{\"points\":") }
            routingViewModel(preferences = AppPreferences(store))

            withContext(Dispatchers.Default) { withTimeout(5.seconds) { store.data.first { key !in it } } }
        }

    @Test
    fun `a resumed Route keeps its stamp until something about it changes`() =
        runTest {
            val preferences = AppPreferences(MemoryPreferences())
            val points = listOf(RoutePoint(49.0, 20.0), RoutePoint(49.0, 20.02))
            preferences.setFollowedRoute(FollowedRoute(points, progress = Progress(0.0, 0), touchedAtMillis = 0))

            val viewModel = routingViewModel(preferences = preferences, now = { FOLLOWED_KEPT_MILLIS })
            val line = settled(viewModel) { it.route.following != null }.route.planned!!.line
            assertEquals(0L, preferences.followedRoute.first()?.touchedAtMillis)

            val midway = line[line.size / 2]
            viewModel.followFix(FollowFix(midway.latitude, midway.longitude, 10.0, 0, 3_600_000))
            val kept = stored(preferences) { it?.progress?.atMillis == 3_600_000L }
            assertEquals(FOLLOWED_KEPT_MILLIS, kept?.touchedAtMillis)
        }

    @Test
    fun `a stored Route that now plans with a leg missing stays drawn, unfollowed, and the value goes`() =
        runTest {
            val preferences = AppPreferences(MemoryPreferences())
            val points = listOf(RoutePoint(49.0, 20.0), RoutePoint(52.23, 21.01))
            preferences.setFollowedRoute(FollowedRoute(points, progress = Progress(700.0, 0), touchedAtMillis = 0))

            val viewModel = routingViewModel(preferences = preferences, now = { 0 })
            stored(preferences) { it == null }
            val route = settled(viewModel) { it.route.planned?.complete == false && !it.route.planning }.route

            assertEquals(points, route.points)
            assertNull(route.following)
        }

    @Test
    fun `a Route shown before the read wins over the stored one, and the value goes`() =
        runTest {
            val preferences = AppPreferences(MemoryPreferences())
            val stored = listOf(RoutePoint(49.0, 20.0), RoutePoint(49.0, 20.02))
            preferences.setFollowedRoute(FollowedRoute(stored, progress = Progress(700.0, 0), touchedAtMillis = 0))
            val shown = listOf(RoutePoint(49.0, 20.0), RoutePoint(49.0, 20.01))
            val saved = SavedRoute("r", "Shown", shown)

            val viewModel =
                routingViewModel(
                    routeToShow = MutableStateFlow("r"),
                    loadRoute = { saved },
                    preferences = preferences,
                    now = { 0 },
                )
            stored(preferences) { it == null }
            val route = settled(viewModel) { it.route.savedId == "r" && !it.route.planning }.route

            assertEquals(shown, route.points)
            assertNull(route.following)
        }

    @Test
    fun `an edit while the resumed Route is planned stops it being followed`() =
        runTest {
            val preferences = AppPreferences(MemoryPreferences())
            val points = listOf(RoutePoint(49.0, 20.0), RoutePoint(49.0, 20.02))
            preferences.setFollowedRoute(FollowedRoute(points, progress = Progress(700.0, 0), touchedAtMillis = 0))
            var first = true
            val viewModel =
                routingViewModel(
                    planRoute = {
                        if (first) {
                            first = false
                            awaitCancellation()
                        }
                        RoutePlanner(fixture).plan(it)
                    },
                    preferences = preferences,
                    now = { 0 },
                )
            settled(viewModel) { it.route.points == points && it.route.planning }

            viewModel.addPoint(49.0, 20.01)
            val route = settled(viewModel) { it.route.points.size == 3 && !it.route.planning }.route

            assertEquals(true, route.planned?.complete)
            assertNull(route.following)
        }

    @Test
    fun `the banner shows after 30 s off the Route, and goes with the leg back at the first fix on it`() =
        runTest {
            val viewModel = routingViewModel()
            val near = followedMidway(viewModel)

            for (at in 5_000L..30_000L step 5_000) viewModel.followFix(near(150.0, at))
            assertFalse(
                settled(viewModel) { it.route.following?.leaving != null }
                    .route.following!!
                    .leaving!!
                    .shown,
            )
            viewModel.followFix(near(150.0, 35_000))
            assertTrue(
                settled(viewModel) {
                    it.route.following
                        ?.leaving
                        ?.shown == true
                }.route.following!!.leaving!!.shown,
            )

            viewModel.backToRoute(near(150.0, 35_000))
            val back = settled(viewModel) { it.route.following?.back is LegBack.Found }.route.following!!.back
            assertTrue((back as LegBack.Found).planned.metres > 0)

            viewModel.followFix(near(0.0, 40_000))
            val following = settled(viewModel) { it.route.following?.leaving == null }.route.following!!
            assertNull(following.back)
        }

    @Test
    fun `a leg back still planning when the user is back on the Route never lands`() =
        runTest {
            val held = CompletableDeferred<Unit>()
            var plans = 0
            val viewModel =
                routingViewModel(planRoute = {
                    if (plans++ > 0) held.await()
                    RoutePlanner(fixture).plan(it)
                })
            val near = followedMidway(viewModel)
            for (at in 5_000L..35_000L step 5_000) viewModel.followFix(near(150.0, at))
            viewModel.backToRoute(near(150.0, 35_000))
            settled(viewModel) { it.route.following?.back == LegBack.Planning }

            viewModel.followFix(near(0.0, 40_000))
            held.complete(Unit)

            assertNull(settled(viewModel) { it.route.following?.leaving == null }.route.following!!.back)
            assertEquals(2, plans)
        }

    @Test
    fun `asking again plans the leg again, and Stop takes it with following`() =
        runTest {
            var plans = 0
            val viewModel =
                routingViewModel(planRoute = {
                    plans++
                    RoutePlanner(fixture).plan(it)
                })
            val near = followedMidway(viewModel)
            for (at in 5_000L..35_000L step 5_000) viewModel.followFix(near(150.0, at))
            viewModel.backToRoute(near(150.0, 35_000))
            settled(viewModel) { it.route.following?.back is LegBack.Found }

            viewModel.followFix(near(200.0, 40_000))
            viewModel.backToRoute(near(200.0, 40_000))
            settled(viewModel) { it.route.following?.back is LegBack.Found && plans == 3 }
            viewModel.stopFollowing()

            assertNull(settled(viewModel) { it.route.following == null }.route.following)
        }

    private suspend fun legBackPlanned(viewModel: MapViewModel): LegBack? =
        settled(viewModel) {
            it.route.following
                ?.back
                .let { back -> back != null && back != LegBack.Planning }
        }.route.following
            ?.back

    @Test
    fun `a leg back the router finds no way for says so`() =
        runTest {
            var plans = 0
            val viewModel = routingViewModel(planRoute = { if (plans++ > 0) null else RoutePlanner(fixture).plan(it) })
            val near = followedMidway(viewModel)
            for (at in 5_000L..35_000L step 5_000) viewModel.followFix(near(150.0, at))
            viewModel.backToRoute(near(150.0, 35_000))

            assertEquals(LegBack.NoWay, legBackPlanned(viewModel))
        }

    @Test
    fun `leaving counts from Follow, before any Progress, and the leg back still plans`() =
        runTest {
            val viewModel = routingViewModel()
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)
            val midway =
                settled(viewModel) { it.route.planned?.complete == true && !it.route.planning }
                    .route.planned!!
                    .line
                    .let { it[it.size / 2] }
            viewModel.follow()
            val away = { at: Long -> FollowFix(midway.latitude + 150.0 / 111_195.0, midway.longitude, 10.0, 0, at) }

            for (at in 0L..30_000L step 5_000) viewModel.followFix(away(at))
            val following =
                settled(viewModel) {
                    it.route.following
                        ?.leaving
                        ?.shown == true
                }.route.following!!
            assertNull(following.progress)

            viewModel.backToRoute(away(30_000))
            assertTrue(legBackPlanned(viewModel) is LegBack.Found)
        }

    @Test
    fun `long-pressed points join in the order pressed, however long their names take`() =
        runTest {
            val viewModel = routingViewModel(places = { awaitCancellation() })
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)

            assertEquals(
                listOf(20.0, 20.02),
                viewModel.state.value.route.points
                    .map { it.longitude },
            )
        }

    @Test
    fun `the last Route stays on the map while the next is planned`() =
        runTest {
            val held = CompletableDeferred<Unit>()
            var plans = 0
            val viewModel =
                routingViewModel(planRoute = {
                    if (plans++ > 0) held.await()
                    RoutePlanner(fixture).plan(it)
                })
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)
            val first = settled(viewModel) { it.route.planned != null && !it.route.planning }.route.planned

            viewModel.reverse()

            val planning = settled(viewModel) { it.route.planning }.route
            assertEquals(first, planning.planned)
            held.complete(Unit)
        }

    @Test
    fun `reverse plans the same points the other way`() =
        runTest {
            val viewModel = routingViewModel()
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)
            settled(viewModel) { it.route.planned != null }

            viewModel.reverse()

            val route =
                settled(
                    viewModel,
                ) {
                    it.route.planned != null &&
                        it.route.points
                            .first()
                            .longitude == 20.02
                }.route
            assertEquals(listOf(20.02, 20.0), route.points.map { it.longitude })
        }

    @Test
    fun `an offered place joins as the next point, under its name`() =
        runTest {
            val viewModel = routingViewModel()
            val place = Place(1, PlaceKind.PEAK, 49.0, 20.01, "tatry", "Kopa")
            viewModel.pick(SearchResult.AtPlace(place))
            viewModel.addOffered()

            val state = settled(viewModel) { it.route.points.isNotEmpty() }
            assertEquals(listOf("Kopa"), state.route.points.map { it.label })
            assertNull(state.search.offered)
        }

    @Test
    fun `a place picked while a Trail is selected closes it and joins the Route brought back`() =
        runTest {
            val viewModel = routingViewModel(loadTrail = ::trailDetail)
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)
            settled(viewModel) { it.route.planned != null }
            viewModel.tapTrails(listOf(7))
            settled(viewModel) { it.trail.shown != null }

            val place = Place(1, PlaceKind.PEAK, 49.0, 20.01, "tatry", "Kopa")
            viewModel.pick(SearchResult.AtPlace(place))
            assertEquals(place, settled(viewModel) { it.trail.shown == null }.search.offered)
            viewModel.addOffered()
            assertEquals(
                "Kopa",
                settled(viewModel) { it.route.points.size == 3 }
                    .route.points
                    .last()
                    .label,
            )
        }

    @Test
    fun `a Trail still loading when a place is picked does not replace the offered place`() =
        runTest {
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { io ->
                val release = CountDownLatch(1)
                val viewModel =
                    routingViewModel(
                        io = io,
                        loadTrail = {
                            release.await()
                            trailDetail(it)
                        },
                    )
                viewModel.tapTrails(listOf(7))
                val place = Place(1, PlaceKind.PEAK, 49.0, 20.01, "tatry", "Kopa")
                viewModel.pick(SearchResult.AtPlace(place))
                release.countDown()
                withContext(io) {}

                val state = settled(viewModel) { it.search.offered != null }
                assertEquals(place, state.search.offered)
                assertNull(state.trail.shown)
            }
        }

    @Test
    fun `a build without the network says planning is not in it`() =
        runTest {
            val viewModel = routingViewModel(planRoute = { null })
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)

            assertTrue(settled(viewModel) { it.route.unavailable }.route.unavailable)
        }

    @Test
    fun `a saved Route asked for by the Routes tab replaces the map's and is planned again`() =
        runTest {
            val asked = MutableStateFlow<String?>(null)
            val saved = SavedRoute("r", "Kopa", listOf(RoutePoint(49.0, 20.0, "A"), RoutePoint(49.0, 20.02, "C")))
            val viewModel = routingViewModel(routeToShow = asked, loadRoute = { if (it == "r") saved else null })
            viewModel.addPoint(49.0, 20.01)

            asked.value = "r"

            val route = settled(viewModel) { it.route.savedId == "r" && it.route.planned != null }.route
            assertEquals("Kopa", route.name)
            assertEquals(saved.points, route.points)
            assertNull("the request is taken once", asked.value)
        }

    @Test
    fun `saving again writes over the Route it was opened from`() =
        runTest {
            val writes = mutableListOf<Pair<String?, String>>()
            val viewModel =
                routingViewModel(saveRoute = { id, name, _ ->
                    writes += id to name
                    id ?: "new"
                })
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)

            viewModel.save("First")
            settled(viewModel) { it.route.savedId == "new" }
            viewModel.save("Second")

            assertEquals("Second", settled(viewModel) { it.route.name == "Second" }.route.name)
            assertEquals(listOf(null to "First", "new" to "Second"), writes)
        }

    @Test
    fun `removing every point forgets the saved Route, so the next is saved as new`() =
        runTest {
            val viewModel = routingViewModel(saveRoute = { _, _, _ -> "r" })
            viewModel.addPoint(49.0, 20.0)
            viewModel.save("Kopa")
            settled(viewModel) { it.route.savedId == "r" }

            viewModel.removePoint(0)

            assertEquals(RouteUiState(), settled(viewModel) { it.route.points.isEmpty() }.route)
        }

    @Test
    fun `a Save that returns after another Route was opened leaves that Route's id alone`() =
        runTest {
            val asked = MutableStateFlow<String?>(null)
            val written = CompletableDeferred<String>()
            val other = SavedRoute("b", "Kopa", listOf(RoutePoint(49.0, 20.0, "A"), RoutePoint(49.0, 20.02, "C")))
            val viewModel =
                routingViewModel(
                    routeToShow = asked,
                    loadRoute = { other },
                    saveRoute = { _, _, _ -> written.await() },
                )
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.01)
            viewModel.save("First")

            asked.value = "b"
            settled(viewModel) { it.route.savedId == "b" }
            written.complete("a")

            val route = settled(viewModel) { it.route.planned != null }.route
            assertEquals("b" to "Kopa", route.savedId to route.name)
        }

    @Test
    fun `a rename in the Routes tab renames the Route on the map, and a delete leaves its points unsaved`() =
        runTest {
            val asked = MutableStateFlow<String?>(null)
            val name = MutableStateFlow<String?>("Kopa")
            val saved = SavedRoute("r", "Kopa", listOf(RoutePoint(49.0, 20.0, "A"), RoutePoint(49.0, 20.02, "C")))
            val viewModel =
                routingViewModel(routeToShow = asked, loadRoute = { saved }, observeName = { name })
            asked.value = "r"
            settled(viewModel) { it.route.savedId == "r" }

            name.value = "Przez Zawrat"
            assertEquals("Przez Zawrat", settled(viewModel) { it.route.name == "Przez Zawrat" }.route.name)

            name.value = null
            val route = settled(viewModel) { it.route.savedId == null }.route
            assertNull(route.name)
            assertEquals(saved.points, route.points)
        }

    @Test
    fun `the Save dialog closes on Save and goes with the Route it names`() =
        runTest {
            val viewModel = routingViewModel(saveRoute = { _, _, _ -> "r" })
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)

            viewModel.showSaveDialog(true)
            settled(viewModel) { it.route.naming }
            viewModel.save("Kopa")
            assertFalse(settled(viewModel) { it.route.savedId == "r" }.route.naming)

            viewModel.showSaveDialog(true)
            viewModel.removePoint(1)
            viewModel.removePoint(0)
            assertFalse(settled(viewModel) { it.route.points.isEmpty() }.route.naming)
        }

    @Test
    fun `a photo's caption, unpin and delete reach the store`() =
        runTest {
            val stored = mutableListOf<String>()
            val viewModel =
                MapViewModel(
                    regions = flowOf(emptyList()),
                    preferences = AppPreferences(MemoryPreferences()),
                    readAsset = { "" },
                    applicationScope = CoroutineScope(Dispatchers.Unconfined),
                    positions = { MutableSharedFlow<Location>() },
                    fetch = {},
                    confirmFetch = { false },
                    saveCaption = { id, caption -> stored += "caption $id $caption" },
                    savePinRemoved = { stored += "unpin $it" },
                    deleteStoredPhoto = { stored += "delete $it" },
                ).tracked()

            viewModel.setCaption("p", "Hala")
            viewModel.unpinPhoto("p")
            viewModel.deletePhoto("p")

            assertEquals(listOf("caption p Hala", "unpin p", "delete p"), stored)
        }

    @Test
    fun `a picked file that is not an image is counted and the rest are still added`() =
        runTest {
            val added = mutableListOf<String>()

            val failed =
                addEach(listOf("a.jpg", "empty.png", "b.jpg")) {
                    if (it ==
                        "empty.png"
                    ) {
                        error("not an image")
                    } else {
                        added += it
                    }
                }

            assertEquals(listOf("a.jpg", "b.jpg"), added)
            assertEquals(1, failed)
        }

    @Test
    fun `a tap on one Trail selects it, on several offers them, and on none closes it`() =
        runTest {
            val viewModel = routingViewModel(loadTrail = ::trailDetail)

            viewModel.tapTrails(listOf(7))
            assertTrue(settled(viewModel) { it.trail.shown?.id == 7L }.faded)

            viewModel.tapTrails(listOf(7, 8))
            val offered = settled(viewModel) { it.trail.choices.size == 2 }.trail.choices
            viewModel.chooseTrail(offered[1])
            assertEquals(emptyList<TrailDetail>(), settled(viewModel) { it.trail.shown?.id == 8L }.trail.choices)

            viewModel.tapTrails(emptyList())
            assertFalse(settled(viewModel) { it.trail.shown == null }.faded)
        }

    @Test
    fun `a tap on one Bike route selects it, on several offers them, and on none closes it`() =
        runTest {
            val viewModel = routingViewModel(loadBikeRoute = ::bikeRouteDetail)

            viewModel.tapBikeRoutes(listOf(7))
            assertTrue(settled(viewModel) { it.bikeRoute.shown?.id == 7L }.faded)

            viewModel.tapBikeRoutes(listOf(7, 8))
            val offered = settled(viewModel) { it.bikeRoute.choices.size == 2 }.bikeRoute.choices
            viewModel.chooseBikeRoute(offered[1])
            assertEquals(
                emptyList<BikeRouteDetail>(),
                settled(viewModel) { it.bikeRoute.shown?.id == 8L }.bikeRoute.choices,
            )

            viewModel.tapBikeRoutes(emptyList())
            assertFalse(settled(viewModel) { it.bikeRoute.shown == null }.faded)
        }

    @Test
    fun `a selected Bike route sets the Route aside, and a long press brings the Route back`() =
        runTest {
            val viewModel = routingViewModel(loadBikeRoute = ::bikeRouteDetail)
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)
            settled(viewModel) { it.route.planned != null }

            viewModel.tapBikeRoutes(listOf(7))
            assertTrue(settled(viewModel) { it.bikeRoute.shown != null }.selection)
            viewModel.addPoint(49.01, 20.01)
            assertEquals(3, settled(viewModel) { it.bikeRoute.shown == null }.route.points.size)
        }

    @Test
    fun `a Trail and a Bike route are never selected together`() =
        runTest {
            val viewModel = routingViewModel(loadTrail = ::trailDetail, loadBikeRoute = ::bikeRouteDetail)

            viewModel.tapBikeRoutes(listOf(7))
            settled(viewModel) { it.bikeRoute.shown != null }
            viewModel.tapTrails(listOf(8))
            assertNull(settled(viewModel) { it.trail.shown != null }.bikeRoute.shown)

            viewModel.tapBikeRoutes(listOf(7))
            assertNull(settled(viewModel) { it.bikeRoute.shown != null }.trail.shown)
        }

    @Test
    fun `a change of Mode closes the selection, and picking the Mode already on does not`() =
        runTest {
            val viewModel = routingViewModel(loadBikeRoute = ::bikeRouteDetail)
            settled(viewModel) { it.mode == Mode.HIKING }

            viewModel.tapBikeRoutes(listOf(7))
            settled(viewModel) { it.bikeRoute.shown != null }
            viewModel.setMode(Mode.HIKING)
            assertEquals(7L, settled(viewModel) { it.bikeRoute.shown != null }.bikeRoute.shown?.id)

            viewModel.setMode(Mode.CYCLING)
            settled(viewModel) { it.mode == Mode.CYCLING && it.bikeRoute.shown == null }
        }

    @Test
    fun `the whole of a parent highlights what its index lists, and fits once for each ask`() =
        runTest {
            val viewModel =
                routingViewModel(
                    loadBikeRoute = { bikeRouteDetail(it, wholeId = 100) },
                    bikeRouteWhole = { listOf(7, 8, 100) },
                )

            viewModel.tapBikeRoutes(listOf(7))
            val before = settled(viewModel) { it.bikeRoute.shown != null }.bikeRoute.fits
            viewModel.showBikeRouteSections()
            val whole = settled(viewModel) { it.bikeRoute.whole.size == 3 }.bikeRoute
            assertEquals(listOf(7L, 8L, 100L), whole.highlighted.map { it.id })
            assertEquals(before + 1, whole.fits)

            viewModel.showWholeBikeRoute()
            assertEquals(
                listOf(7L),
                settled(viewModel) { it.bikeRoute.whole.isEmpty() }.bikeRoute.highlighted.map { it.id },
            )
        }

    @Test
    fun `a selected Trail sets the Route aside, and closing it or a long press brings the Route back`() =
        runTest {
            val viewModel = routingViewModel(loadTrail = ::trailDetail)
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)
            settled(viewModel) { it.route.planned != null }

            viewModel.tapTrails(listOf(7))
            assertEquals(2, settled(viewModel) { it.trail.shown != null }.route.points.size)
            viewModel.closeSelection()
            assertEquals(2, settled(viewModel) { it.trail.shown == null }.route.points.size)

            viewModel.tapTrails(listOf(7))
            settled(viewModel) { it.trail.shown != null }
            viewModel.addPoint(49.01, 20.01)
            assertEquals(3, settled(viewModel) { it.trail.shown == null }.route.points.size)
        }

    @Test
    fun `a Route along a Trail found off the main thread comes with its labels`() =
        runTest {
            val viewModel =
                routingViewModel(loadTrail = ::trailDetail, routeAlong = {
                    withContext(Dispatchers.Default) {
                        delay(20)
                        listOf(RoutePoint(49.0, 20.0), RoutePoint(49.0, 20.02))
                    }
                })
            viewModel.tapTrails(listOf(7))
            settled(viewModel) { it.trail.shown?.id == 7L }

            viewModel.planAlongTrail("Trail 7")
            assertTrue(settled(viewModel) { true }.trail.planningRoute)

            val state =
                settled(
                    viewModel,
                ) { state -> state.route.planned != null && state.route.points.any { it.label != null } }
            assertEquals(listOf("Near 49.0", "Near 49.0"), state.route.points.map { it.label })
            assertEquals("Trail 7", state.route.suggestedName)
            assertEquals(TrailUiState(), state.trail)
        }

    @Test
    fun `closing the Trail stops a Route along it still being found`() =
        runTest {
            val stopped = CompletableDeferred<Unit>()
            val viewModel =
                routingViewModel(loadTrail = ::trailDetail, routeAlong = {
                    try {
                        awaitCancellation()
                    } finally {
                        stopped.complete(Unit)
                    }
                })
            viewModel.tapTrails(listOf(7))
            settled(viewModel) { it.trail.shown?.id == 7L }
            viewModel.planAlongTrail("Trail 7")

            viewModel.closeSelection()
            assertTrue(stopped.isCompleted)
            assertFalse(settled(viewModel) { it.trail.shown == null }.trail.planningRoute)
        }

    @Test
    fun `a tap that offers several Trails stops a Route along the shown one and frees its button`() =
        runTest {
            val viewModel = routingViewModel(loadTrail = ::trailDetail, routeAlong = { awaitCancellation() })
            viewModel.tapTrails(listOf(7))
            settled(viewModel) { it.trail.shown?.id == 7L }
            viewModel.planAlongTrail("Trail 7")
            assertTrue(settled(viewModel) { true }.trail.planningRoute)

            viewModel.tapTrails(listOf(7, 8))
            settled(viewModel) { it.trail.choices.size == 2 }
            viewModel.dismissTrailChoices()

            val trail = settled(viewModel) { it.trail.choices.isEmpty() }.trail
            assertEquals(7L, trail.shown?.id)
            assertFalse(trail.planningRoute)
        }

    @Test
    fun `a fit asked for is never asked again by selecting or closing another Trail`() =
        runTest {
            val viewModel = routingViewModel(loadTrail = ::trailDetail)
            viewModel.tapTrails(listOf(7))
            settled(viewModel) { it.trail.shown?.id == 7L }
            viewModel.showWholeTrail()
            settled(viewModel) { it.trail.fits == 1 }

            viewModel.tapTrails(listOf(8))
            assertEquals(1, settled(viewModel) { it.trail.shown?.id == 8L }.trail.fits)
            viewModel.chooseTrail(trailDetail(9))
            assertEquals(1, settled(viewModel) { it.trail.shown?.id == 9L }.trail.fits)
            viewModel.closeSelection()
            assertEquals(1, settled(viewModel) { it.trail.shown == null }.trail.fits)
        }

    @Test
    fun `a Long-distance Trail that loads after its Section is closed or replaced is dropped`() =
        runTest {
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { io ->
                for (leave in listOf<
                    (
                        MapViewModel,
                    ) -> Unit,
                >({ it.closeSelection() }, { it.chooseTrail(trailDetail(8)) })) {
                    val release = CountDownLatch(1)
                    val viewModel =
                        routingViewModel(
                            io = io,
                            loadTrail = { trailDetail(it, longDistanceId = 100) },
                            sectionsOf = {
                                release.await()
                                listOf(7, 8, 9)
                            },
                        )
                    viewModel.tapTrails(listOf(7))
                    settled(viewModel) { it.trail.shown?.id == 7L }

                    viewModel.showLongDistance()
                    leave(viewModel)
                    release.countDown()
                    withContext(io) {}

                    val trail = settled(viewModel) { it.trail.shown?.id != 7L }.trail
                    assertEquals(listOfNotNull(trail.shown), trail.highlighted)
                    assertEquals(0, trail.fits)
                }
            }
        }

    private suspend fun settled(
        viewModel: MapViewModel,
        until: (MapUiState) -> Boolean,
    ): MapUiState =
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(5.seconds) { viewModel.state.first(until) }
        } ?: error("the state never settled")

    private suspend fun stored(
        preferences: AppPreferences,
        until: (FollowedRoute?) -> Boolean,
    ): FollowedRoute? =
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { preferences.followedRoute.first(until) } }
}

private val viewModels = mutableListOf<MapViewModel>()

private fun MapViewModel.tracked() = also { viewModels += it }

private val fixture =
    TrailNetwork.read(
        MapViewModelTest::class.java.getResource("/trails-fixture.network")!!.readBytes(),
    )

private object NearbyNames : PlaceSearch {
    override fun search(
        text: String,
        latitude: Double,
        longitude: Double,
        limit: Int,
    ) = emptyList<Place>()

    override fun nearest(
        latitude: Double,
        longitude: Double,
    ) = Place(0, PlaceKind.PLACE, latitude, longitude, "tatry", "Near $latitude")
}

private fun routingViewModel(
    places: suspend () -> PlaceSearch? = { NearbyNames },
    planRoute: suspend (List<RoutePoint>) -> PlannedRoute? = {
        RoutePlanner(fixture).plan(it)
    },
    routeToShow: MutableStateFlow<String?> = MutableStateFlow(null),
    loadRoute: suspend (String) -> SavedRoute? = { null },
    saveRoute: suspend (String?, String, List<RoutePoint>) -> String = { _, _, _ -> "" },
    observeName: (String) -> Flow<String?> = { emptyFlow() },
    loadTrail: (Long) -> TrailDetail? = { null },
    sectionsOf: (Long) -> List<Long> = { emptyList() },
    routeAlong: suspend (TrailDetail) -> List<RoutePoint>? = { null },
    loadBikeRoute: (Long) -> BikeRouteDetail? = { null },
    bikeRouteWhole: (Long) -> List<Long> = { emptyList() },
    io: CoroutineDispatcher = Dispatchers.Main,
    preferences: AppPreferences = AppPreferences(MemoryPreferences()),
    now: () -> Long = System::currentTimeMillis,
) = MapViewModel(
    regions = flowOf(emptyList()),
    preferences = preferences,
    readAsset = { "" },
    applicationScope = CoroutineScope(Dispatchers.Unconfined),
    positions = { MutableSharedFlow<Location>() },
    fetch = {},
    confirmFetch = { false },
    places = places,
    planRoute = planRoute,
    io = io,
    compute = Dispatchers.Main,
    routeToShow = routeToShow,
    loadRoute = loadRoute,
    saveRoute = saveRoute,
    observeName = observeName,
    loadTrail = loadTrail,
    sectionsOf = sectionsOf,
    routeAlong = routeAlong,
    loadBikeRoute = loadBikeRoute,
    bikeRouteWhole = bikeRouteWhole,
    now = now,
).tracked()

private fun bikeRouteDetail(
    id: Long,
    wholeId: Long? = null,
) = BikeRouteDetail(bikeRouteFacts().copy(id = id), line = emptyList(), wholeId = wholeId)

private fun trailDetail(
    id: Long,
    longDistanceId: Long? = null,
) = TrailDetail(
    TrailFacts(
        id = id,
        name = "Trail $id",
        namePl = null,
        ref = null,
        colour = TrailColour.GREEN,
        symbol = null,
        from = null,
        to = null,
        longDistanceId = longDistanceId,
        longDistanceName = longDistanceId?.let { "Long-distance $it" },
        startLatitude = 49.0,
        startLongitude = 20.0,
        endLatitude = 49.0,
        endLongitude = 20.02,
        metres = 1460.0,
        ascent = 0.0,
        descent = 0.0,
        gaps = 0,
        south = 49.0,
        west = 20.0,
        north = 49.0,
        east = 20.02,
    ),
    line = emptyList(),
)

private fun mapViewModel(
    regions: Flow<List<Region>>,
    preferences: AppPreferences = AppPreferences(MemoryPreferences()),
    fetch: suspend (List<String>) -> Unit = {},
    relief: Flow<Region?> = flowOf(null),
) = MapViewModel(
    regions = regions,
    preferences = preferences,
    readAsset = { "" },
    applicationScope = CoroutineScope(Dispatchers.Unconfined),
    positions = { MutableSharedFlow<Location>() },
    fetch = fetch,
    confirmFetch = { false },
    relief = relief,
    io = Dispatchers.Main,
    compute = Dispatchers.Main,
).tracked()

private fun tatry(state: RegionState) = Region("tatry", name = 0, state)

private val TATRY_BOUNDS = GeoBounds(south = 49.1, west = 19.6, north = 49.4, east = 20.3)

private val READY = RegionState.Ready(File("tatry.pmtiles"), osmDate = null, bounds = TATRY_BOUNDS)

private fun bieszczady(state: RegionState) = Region("bieszczady", name = 0, state)

private val BIESZCZADY_BOUNDS = GeoBounds(south = 49.0, west = 22.0, north = 49.5, east = 22.9)

private val BIESZCZADY_READY = RegionState.Ready(File("bieszczady.pmtiles"), osmDate = null, bounds = BIESZCZADY_BOUNDS)

private class MemoryPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(data.value).also { data.value = it }
}

private object NoPreferences : DataStore<Preferences> {
    override val data: Flow<Preferences> = flowOf(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(emptyPreferences())
}
