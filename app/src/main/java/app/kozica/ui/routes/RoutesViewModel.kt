package app.kozica.ui.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kozica.MainApplication
import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode
import app.kozica.data.SavedRoute
import app.kozica.data.gpx.Gpx
import app.kozica.data.gpx.routeGpx
import app.kozica.data.hikes.Pace
import app.kozica.data.routes.PlanKey
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.RouteTotals
import app.kozica.data.routes.roundedUpToFiveMinutes
import app.kozica.ui.common.GpxExport
import app.kozica.ui.common.GpxExports
import app.kozica.ui.common.GpxFailure
import app.kozica.ui.map.modeOf
import app.kozica.ui.map.shownTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Collator
import java.time.LocalDate

/**
 * A saved Route with its totals, null until it is planned or where it cannot be. A ride's [minutes] are
 * the riding rule's, [approximate], with no [signpostMinutes]: the walking Pace never touches it
 * (docs/phase-7b.md § 4).
 */
data class RouteRow(
    val route: SavedRoute,
    val metres: Double? = null,
    val minutes: Int? = null,
    val signpostMinutes: Int? = null,
    val approximate: Boolean = false,
)

/** @param routes null until the first read, then by name. */
data class RoutesUiState(
    val routes: List<RouteRow>? = null,
)

/**
 * docs/phase-5.md § 5: saved Routes by name, each with its length and time, planned from the current
 * network one Route at a time as the list is shown and kept by [totals] for the next visit.
 */
class RoutesViewModel(
    saved: Flow<List<SavedRoute>>,
    private val totals: RouteTotals,
    private val renameRoute: suspend (id: String, name: String) -> Unit,
    private val deleteRoute: suspend (id: String) -> Unit,
    private val routeToShow: MutableStateFlow<String?>,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val planRoute: suspend (List<RoutePoint>, Mode, BikeRouteFilter) -> PlannedRoute? = { _, _, _ -> null },
    private val writeGpx: suspend (Gpx, LocalDate) -> File = { _, _ -> error("No GPX in this view model") },
    pace: Flow<Pace?> = flowOf(null),
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<RoutesUiState> =
        saved
            .map { routes -> routes.sortedWith(compareBy(Collator.getInstance()) { it.name }) }
            .flatMapLatest { routes ->
                channelFlow {
                    launch { for (route in routes) withContext(compute) { totals.plan(route.planKey()) } }
                    combine(totals.planned, pace, ::Pair).collect { (planned, pace) ->
                        val rows =
                            routes.map { route ->
                                val key = route.planKey()
                                val totals = planned[key]
                                val time = totals?.seconds?.let { shownTime(it, key.mode, pace) }
                                RouteRow(
                                    route,
                                    totals?.metres,
                                    time?.minutes,
                                    totals?.seconds?.takeIf { key.mode == Mode.HIKING }?.let(::roundedUpToFiveMinutes),
                                    time?.approximate == true,
                                )
                            }
                        send(RoutesUiState(rows))
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutesUiState())

    fun show(id: String) {
        routeToShow.value = id
    }

    fun rename(
        id: String,
        name: String,
    ) {
        viewModelScope.launch { renameRoute(id, name) }
    }

    fun delete(id: String) {
        viewModelScope.launch { deleteRoute(id) }
    }

    private val gpxExports = GpxExports(viewModelScope, writeGpx)
    val gpx: StateFlow<GpxExport> = gpxExports.state

    /**
     * [id] as a GPX file for the share sheet, planned again from today's network under its own Mode and
     * chip and dated today (docs/phase-6.md § 8, docs/phase-7b.md § 4).
     */
    fun exportGpx(id: String) {
        val route =
            state.value.routes
                ?.firstOrNull { it.route.id == id }
                ?.route ?: return
        val key = route.planKey()
        val noWay = if (key.mode == Mode.CYCLING) GpxFailure.NoWayOverBikeRoutes else GpxFailure.NoWay
        gpxExports.start(LocalDate.now(), noWay) {
            withContext(compute) { planRoute(key.points, key.mode, key.filter) }
                ?.takeIf { it.complete }
                ?.let { routeGpx(route.name, it, route.points) }
        }
    }

    fun gpxShared() = gpxExports.shared()

    fun dismissGpxFailure() = gpxExports.dismiss()

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    RoutesViewModel(
                        app.container.routes.observeAll(),
                        app.container.routeTotals,
                        app.container.routes::rename,
                        app.container.routes::delete,
                        app.container.routeToShow,
                        planRoute = app.container::planRoute,
                        writeGpx = app.container.gpxFiles::write,
                        pace = app.container.pace,
                    )
                }
            }
    }
}

/** Through the map's own seam, so a release build plans a saved ride as the walk it opens as. */
private fun SavedRoute.planKey() = PlanKey(points, modeOf(mode), bikeRouteFilter)
