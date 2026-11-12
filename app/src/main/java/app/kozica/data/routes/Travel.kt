package app.kozica.data.routes

import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode

/**
 * What a Route may use and how its time counts: in Hiking phase 5's walk over the Trails, in Cycling
 * docs/phase-7b.md § 3's ride over the Bike routes [filter] allows, never against their rider direction.
 */
internal class Travel(
    private val network: TrailNetwork,
    val mode: Mode,
    filter: BikeRouteFilter,
) {
    private val cycling = mode == Mode.CYCLING
    private val kinds =
        when (filter) {
            BikeRouteFilter.ALL -> TrailNetwork.SET_TOURING or TrailNetwork.SET_MTB
            BikeRouteFilter.TOURING -> TrailNetwork.SET_TOURING
            BikeRouteFilter.MTB -> TrailNetwork.SET_MTB
        }

    fun allows(edge: Int): Boolean = if (cycling) network.isBikeRoute(edge, kinds) else network.isTrail(edge)

    fun forward(edge: Int): Boolean = if (cycling) network.canRide(edge, kinds, true) else network.canWalkForward(edge)

    fun backward(edge: Int): Boolean =
        if (cycling) network.canRide(edge, kinds, false) else network.canWalkBackward(edge)

    fun seconds(
        edge: Int,
        forward: Boolean,
    ): Double {
        val seconds =
            when {
                cycling && forward -> network.edgeRidingForward
                cycling -> network.edgeRidingBackward
                forward -> network.edgeSecondsForward
                else -> network.edgeSecondsBackward
            }
        return seconds[edge].toDouble()
    }

    fun part(
        edge: Int,
        from: Double,
        to: Double,
    ): EdgePart = network.part(edge, from, to, mode)

    /** From a point to where it joins the network, where nothing is known of the way. */
    val offNetworkMetresPerSecond: Double =
        if (cycling) kmh(RIDING_FLAT_KMH[TrailNetwork.ROUGH]) else FLAT_METRES_PER_SECOND

    /** No edge and no join is counted faster than this, so A*'s straight line at it never overestimates. */
    val fastestMetresPerSecond: Double =
        if (cycling) kmh(maxOf(RIDING_FLAT_KMH.max(), RIDING_DESCENT_CAP_KMH.max())) else FLAT_METRES_PER_SECOND

    private fun kmh(speed: Double) = speed / 3.6
}
