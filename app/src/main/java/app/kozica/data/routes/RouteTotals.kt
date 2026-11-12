package app.kozica.data.routes

import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A Route's length and its time by its Mode's rule, unrounded: the signposts' for a walk, the riding rule's for a ride. */
data class Totals(
    val metres: Double,
    val seconds: Double,
)

/** What a Route is planned from: the same points planned as a walk and as a ride are two Routes (docs/phase-7b.md § 4). */
data class PlanKey(
    val points: List<RoutePoint>,
    val mode: Mode,
    val filter: BikeRouteFilter,
)

/**
 * Saved Routes' totals by their points, Mode and chip, each planned once for the life of the process,
 * since the network cannot change under a running one. A Route with no complete way over the Trails,
 * or the Bike routes its chip allows, is known as null.
 */
class RouteTotals(
    private val planRoute: suspend (List<RoutePoint>, Mode, BikeRouteFilter) -> PlannedRoute?,
) {
    private val known = MutableStateFlow<Map<PlanKey, Totals?>>(emptyMap())

    val planned: StateFlow<Map<PlanKey, Totals?>> = known.asStateFlow()

    suspend fun plan(key: PlanKey) {
        if (key.points.size < 2 || key in known.value) return
        val totals =
            planRoute(key.points, key.mode, key.filter)
                ?.takeIf { it.complete }
                ?.let { Totals(it.metres, it.seconds) }
        known.update { it + (key to totals) }
    }
}
