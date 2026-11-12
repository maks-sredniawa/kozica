package app.kozica

import android.Manifest
import android.content.Context
import android.location.Location
import androidx.annotation.RequiresPermission
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.kozica.data.APP_DATABASE_FILE
import app.kozica.data.AppPreferences
import app.kozica.data.BikeRouteFilter
import app.kozica.data.HikeRepository
import app.kozica.data.Mode
import app.kozica.data.PRESERVED_DIRECTORY
import app.kozica.data.RouteRepository
import app.kozica.data.backup.BackupExporter
import app.kozica.data.backup.BackupExports
import app.kozica.data.backup.BackupRestorer
import app.kozica.data.backup.EXPORTS_DIRECTORY
import app.kozica.data.buildAppDatabase
import app.kozica.data.gpx.GPX_DIRECTORY
import app.kozica.data.gpx.GpxFiles
import app.kozica.data.hikes.HikeHeights
import app.kozica.data.hikes.Pace
import app.kozica.data.hikes.Paces
import app.kozica.data.location.positionFixes
import app.kozica.data.location.withPositionOverride
import app.kozica.data.regions.RegionFiles
import app.kozica.data.regions.RegionOutline
import app.kozica.data.regions.RegionState
import app.kozica.data.regions.regionStates
import app.kozica.data.regions.reliefState
import app.kozica.data.routes.BikeRouteDetail
import app.kozica.data.routes.Leg
import app.kozica.data.routes.PlaceSearch
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RoutePlanner
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.RouteTotals
import app.kozica.data.routes.Router
import app.kozica.data.routes.TrailDetail
import app.kozica.data.routes.TrailNetworks
import app.kozica.data.routes.bikeRouteDifficulty
import app.kozica.data.routes.gradedSpans
import app.kozica.data.routes.openPlaces
import app.kozica.data.routes.placesFile
import app.kozica.data.routes.pointsAlong
import app.kozica.data.routes.walkLine
import app.kozica.media.MediaFiles
import app.kozica.record.Recorder
import app.kozica.ui.common.revokeShares
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * `internal` rather than private: [MainApplication] holds the one [AppPreferences] over this store
 * and hands it to the container, because the theme has to read a preference before the container is
 * allowed to exist (ADR-0001's gate).
 *
 * Kotlin note: `by preferencesDataStore(...)` is a property delegate on `Context` — it creates the
 * store once per process and hands the same instance to every caller. Creating a second DataStore
 * over the same file throws, so this must stay the only one.
 */
internal val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

/**
 * Manual dependency injection — deliberately not Hilt.
 *
 * At the size a single-developer app reaches, constructing things by hand is clearer than a
 * generated graph: the whole object graph is this file, you can read it top to bottom, and there is
 * no annotation processor round trip in every build. Migrating to Hilt later is mechanical, because
 * everything here is already constructor injection.
 *
 * The rule that makes it work: **nothing constructs its own dependencies.** A ViewModel takes a
 * repository, a repository takes a DAO and a `MediaFiles`, and only this file calls a constructor.
 * The moment a class reaches for a singleton instead, it stops being testable without a device.
 *
 * Lives at the package root with the app shell: like `Navigation.kt`, it describes how the app hangs
 * together rather than belonging to any one screen.
 */
class AppContainer(
    context: Context,
    val preferences: AppPreferences,
    val applicationScope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    /**
     * Kotlin note: `by lazy` here is the ordinary decorative kind — build it on first use. It is
     * **not** the gate; the gate is the `lazy` on `MainApplication.container`, which decides whether
     * this object is ever constructed at all.
     */
    private val database by lazy { buildAppDatabase(appContext) }

    val media by lazy { MediaFiles(appContext) }

    val filesDir: File get() = appContext.filesDir

    val preservedDir: File get() = File(appContext.filesDir, PRESERVED_DIRECTORY)

    private val backupExporter by lazy {
        BackupExporter(
            databaseFile = appContext.getDatabasePath(APP_DATABASE_FILE),
            filesDir = appContext.filesDir,
            scratchDir = File(appContext.cacheDir, "backup-scratch"),
        )
    }

    /** Exports and restores share scratch files, so one runs at a time. */
    private val backupLock = Mutex()

    val backupExports by lazy {
        BackupExports(
            backupExporter,
            File(appContext.cacheDir, EXPORTS_DIRECTORY),
            preferences,
            appContext.contentResolver,
            backupLock,
        ) { appContext.revokeShares(EXPORTS_DIRECTORY) }
    }

    val backupRestorer by lazy {
        BackupRestorer(appContext, appContext.filesDir, preservedDir, backupExporter, APP_DATABASE_FILE, backupLock)
    }

    val routes by lazy { RouteRepository(database.routeDao()) }

    val gpxFiles by lazy {
        GpxFiles(File(appContext.cacheDir, GPX_DIRECTORY)) { appContext.revokeShares(GPX_DIRECTORY) }
    }

    val hikeHeights by lazy {
        HikeHeights(
            appContext,
            RegionOutline.of(
                appContext.assets
                    .open(REGIONS_OUTLINE)
                    .bufferedReader()
                    .use { it.readText() },
            ),
        ) { (relief.first().state as? RegionState.Ready)?.archive }
    }

    val hikes by lazy { HikeRepository(database.hikeDao(), hikeHeights::of, { media.delete(it) }, media::persistPhoto) }

    val recorder by lazy { Recorder(appContext, hikes, ::places, applicationScope) }

    val pace: StateFlow<Pace?> by lazy {
        Paces(
            hikes.observeEnded(),
            relief.map {
                it.state is RegionState.Ready
            },
            hikes::ruleSeconds,
        ).pace.stateIn(applicationScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    /** A saved Route the Routes tab asked the map to show, until the map takes it. */
    val routeToShow = MutableStateFlow<String?>(null)

    /** A Hike the Hikes tab asked the map to show, until the map takes it. */
    val hikeToShow = MutableStateFlow<String?>(null)

    /** Photos that could not be read as an image and so were not added, until the user dismisses the count. */
    val photosLeftOut = MutableStateFlow(0)

    val regions by lazy { regionStates(appContext) }

    val relief by lazy { reliefState(appContext) }

    val regionFiles by lazy { RegionFiles.of(appContext) }

    private val placeIndex by lazy { placesFile(appContext)?.let(::openPlaces) }

    private val router by lazy { TrailNetworks.load(appContext)?.let(::Router) }

    private val planner by lazy { router?.let { RoutePlanner(TrailNetworks.load(appContext)!!, it) } }

    private val planning = Mutex()

    val routeTotals by lazy { RouteTotals(::planRoute) }

    /** Null in a build without the name index. Blocks on its first call, which copies it out of the assets. */
    fun places(): PlaceSearch? = placeIndex

    /**
     * Null in a build without the network. Blocks on its first call, which loads it (docs/phase-5.md § 1).
     * No default Mode or chip, so nothing plans a ride as a walk by leaving them out (docs/phase-7b.md § 4).
     */
    suspend fun planRoute(
        points: List<RoutePoint>,
        mode: Mode,
        filter: BikeRouteFilter,
    ): PlannedRoute? = planning.withLock { planner?.plan(points, mode, filter) }

    /** A Trail as the map shows it (docs/phase-6c.md § 3); reads the index and the network, so off the main thread. */
    fun trail(id: Long): TrailDetail? {
        val index = placeIndex ?: return null
        val facts = index.trail(id) ?: return null
        val network = TrailNetworks.load(appContext)
        val steps = TrailNetworks.walks(appContext)?.steps(id)
        val line = if (network != null && steps != null) network.walkLine(steps) else emptyList()
        val named = facts.name != null || facts.namePl != null || (facts.from != null && facts.to != null)
        return TrailDetail(
            facts,
            line,
            nearestFrom = if (named) null else index.nearest(facts.startLatitude, facts.startLongitude)?.name,
            nearestTo = if (named) null else index.nearest(facts.endLatitude, facts.endLongitude)?.name,
        )
    }

    /** A Bike route as the map shows it (docs/phase-7a.md § 4); reads the index and the network, so off the main thread. */
    fun bikeRoute(id: Long): BikeRouteDetail? {
        val index = placeIndex ?: return null
        val facts = index.bikeRoute(id) ?: return null
        val network = TrailNetworks.load(appContext)
        val steps = TrailNetworks.walks(appContext)?.steps(id)
        val line = if (network != null && steps != null) network.walkLine(steps) else emptyList()
        val graded = if (network != null && steps != null) network.gradedSpans(steps, line) else emptyList()
        val gradedUphill =
            if (network != null &&
                steps != null
            ) {
                network.gradedSpans(steps, line, network.edgeMtbUphill)
            } else {
                emptyList()
            }
        val named =
            facts.name != null || facts.namePl != null || facts.ref != null || (facts.from != null && facts.to != null)
        return BikeRouteDetail(
            facts,
            line,
            nearestFrom = if (named) null else index.nearest(facts.startLatitude, facts.startLongitude)?.name,
            nearestTo = if (named) null else index.nearest(facts.endLatitude, facts.endLongitude)?.name,
            graded = graded,
            difficulty = bikeRouteDifficulty(facts, line.lastOrNull()?.metres ?: 0.0, graded, gradedUphill),
            wholeId = facts.longDistanceId ?: id.takeIf { index.bikeRouteWhole(id).size > 1 },
        )
    }

    fun bikeRouteWhole(parent: Long): List<Long> = placeIndex?.bikeRouteWhole(parent).orEmpty()

    /** The points that hold a Route on the Trail's walk (docs/phase-6c.md § 5), null in a build without them. */
    suspend fun routeAlong(trail: TrailDetail): List<RoutePoint>? = pointsAlongWalk(trail.id, Mode.HIKING)

    /** The same along a Bike route's walk, ridden under [filter] (docs/phase-7b.md § 5). */
    suspend fun routeAlongBikeRoute(
        bikeRoute: BikeRouteDetail,
        filter: BikeRouteFilter,
        legPlanned: (Leg?) -> Unit = {},
    ): List<RoutePoint>? = pointsAlongWalk(bikeRoute.id, Mode.CYCLING, filter, legPlanned)

    private suspend fun pointsAlongWalk(
        id: Long,
        mode: Mode,
        filter: BikeRouteFilter = BikeRouteFilter.ALL,
        legPlanned: (Leg?) -> Unit = {},
    ): List<RoutePoint>? =
        planning.withLock {
            val network = TrailNetworks.load(appContext) ?: return@withLock null
            val walks = TrailNetworks.walks(appContext) ?: return@withLock null
            val steps = walks.steps(id) ?: return@withLock null
            val context = currentCoroutineContext()
            router?.let {
                network.pointsAlong(
                    steps,
                    it,
                    mode,
                    filter,
                    back = walks.back(id),
                    checkCancelled = { context.ensureActive() },
                    legPlanned = legPlanned,
                )
            }
        }

    fun sections(longDistance: Long): List<Long> = placeIndex?.sections(longDistance).orEmpty()

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun positions(approximateOnly: Boolean): Flow<Location> =
        positionFixes(appContext, approximateOnly).withPositionOverride()
}

private const val REGIONS_OUTLINE = "map/regions.geojson"
