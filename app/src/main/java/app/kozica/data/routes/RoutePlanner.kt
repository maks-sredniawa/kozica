package app.kozica.data.routes

import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A position the user put on a Route: its start, its end or a via point.
 *
 * @param label the place's name, or null for a point with no place near it, shown by its position.
 */
@Serializable
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null,
)

/** How the line comes to a vertex from the one before it. */
enum class Approach {
    TRAIL,

    /** The walk between a point and where it joins the Trails, drawn dashed; its vertices have the join's height. */
    OFF_TRAIL,

    /** Across a leg with no way over the Trails: neither drawn nor counted in [RouteSample.metres]. */
    NO_WAY,
}

/**
 * One vertex of the drawn line and the profile, [metres] along the Route; in Cycling, [ride] is what
 * the riding rule reads of the edge the line comes to it along, null off the Bike routes.
 */
data class RouteSample(
    val latitude: Double,
    val longitude: Double,
    val metres: Double,
    val heightMetres: Double,
    val approach: Approach,
    val ride: RideWay? = null,
)

/** An edge's surface class and its `mtb:scale` bytes, as [ridingStretch] takes them. */
data class RideWay(
    val surface: Int,
    val mtb: Int,
    val mtbUphill: Int,
)

/** A stretch graded `demanding_mountain_hiking` or harder, by distance along the Route, at its hardest [sac]. */
data class DemandingStretch(
    val fromMetres: Double,
    val toMetres: Double,
    val sac: Int,
)

/**
 * docs/phase-7b.md § 3: the hardest `mtb:scale` from S2 up a ride takes, read in the direction it is
 * ridden; [uphill] where `mtb:scale:uphill` gave it.
 */
data class RideGrade(
    val scale: Int,
    val uphill: Boolean,
)

/**
 * docs/phase-5.md § 3: a Route's legs, one per pair of consecutive points, null where no way over the
 * Trails, or in Cycling the Bike routes, joins them; the totals and the rest cover the legs that were
 * found, and the time to a point is null from the first missing leg on. A ride has no [demanding]
 * stretches, which are hiking grades, and may have a [hardestRide].
 */
data class PlannedRoute(
    val legs: List<Leg?>,
    val line: List<RouteSample>,
    val ascent: Double,
    val descent: Double,
    val seconds: Double,
    val secondsToPoint: List<Double?>,
    val demanding: List<DemandingStretch>,
    val metresToPoint: List<Double?> = emptyList(),
    val mode: Mode = Mode.HIKING,
    val hardestRide: RideGrade? = null,
) {
    val metres: Double get() = line.lastOrNull()?.metres ?: 0.0
    val complete: Boolean get() = legs.all { it != null }
    val minutes: Int get() = roundedUpToFiveMinutes(seconds)
}

/** As a signpost gives it: the unrounded sum, rounded up to the next 5 minutes. */
fun roundedUpToFiveMinutes(seconds: Double): Int = ceil(seconds / 300 - 1e-9).toInt() * 5

/** docs/phase-7b.md § 3: a riding time, rounded to the nearest 5 minutes and never under 5. */
fun ridingMinutes(seconds: Double): Int = maxOf(5, (seconds / 300).roundToInt() * 5)

class RoutePlanner(
    private val network: TrailNetwork,
    private val router: Router = Router(network),
) {
    fun plan(
        points: List<RoutePoint>,
        mode: Mode = Mode.HIKING,
        filter: BikeRouteFilter = BikeRouteFilter.ALL,
    ): PlannedRoute {
        require(points.size >= 2) { "a Route has a start and an end" }
        val joins = points.map { router.joins(it.latitude, it.longitude, mode, filter) }
        val legs =
            points.indices.drop(1).map { i ->
                router.leg(joins[i - 1], joins[i], points[i].latitude, points[i].longitude, mode, filter)
            }
        val cycling = mode == Mode.CYCLING
        var hardestRide: RideGrade? = null
        val line = mutableListOf<RouteSample>()
        val demanding = mutableListOf<DemandingStretch>()
        var ascent = 0.0
        var descent = 0.0
        val secondsToPoint = mutableListOf<Double?>(0.0)
        val metresToPoint = MutableList<Double?>(points.size) { null }
        var acrossNoWay = false
        for ((i, leg) in legs.withIndex()) {
            secondsToPoint += leg?.let { secondsToPoint.last()?.plus(it.seconds) }
            if (leg == null) {
                acrossNoWay = true
                continue
            }
            val reached = if (line.isEmpty() || legs[i - 1] != null) Approach.OFF_TRAIL else Approach.NO_WAY
            line.join(points[i].latitude, points[i].longitude, heightAt(leg.start), reached)
            metresToPoint[i] = line.last().metres
            line.join(leg.start.latitude, leg.start.longitude, heightAt(leg.start), Approach.OFF_TRAIL)
            for (step in leg.steps) {
                val part = network.part(step.edge, step.from, step.to)
                ascent += part.ascent
                descent += part.descent
                val from = line.last().metres
                walk(step, line, if (cycling) rideWay(step.edge) else null)
                if (cycling) {
                    hardestRide = harder(hardestRide, rideGrade(step.edge, uphill = part.ascent > part.descent))
                    continue
                }
                val sac = network.edgeSac[step.edge].toInt()
                if (sac >= DEMANDING_SAC) {
                    demanding.extend(from, line.last().metres, sac, apart = acrossNoWay)
                    acrossNoWay = false
                }
            }
            line.join(leg.end.latitude, leg.end.longitude, heightAt(leg.end), Approach.TRAIL, line.last().ride)
            line.join(points[i + 1].latitude, points[i + 1].longitude, heightAt(leg.end), Approach.OFF_TRAIL)
            metresToPoint[i + 1] = line.last().metres
        }
        val seconds = legs.sumOf { it?.seconds ?: 0.0 }
        return PlannedRoute(
            legs,
            line,
            ascent,
            descent,
            seconds,
            secondsToPoint,
            demanding,
            metresToPoint,
            mode,
            hardestRide,
        )
    }

    /** `mtb:scale:uphill` for a step ridden uphill where the edge has one, else `mtb:scale`; null under S2. */
    private fun rideGrade(
        edge: Int,
        uphill: Boolean,
    ): RideGrade? {
        val readUphill = uphill && network.edgeMtbUphill[edge].toInt() != 0
        val stored = if (readUphill) network.edgeMtbUphill[edge] else network.edgeMtb[edge]
        return RideGrade(stored - 1, readUphill).takeIf { it.scale >= DEMANDING_MTB }
    }

    /** The higher scale; on a tie the one read as it is, which names no direction. */
    private fun harder(
        a: RideGrade?,
        b: RideGrade?,
    ): RideGrade? =
        when {
            a == null -> b
            b == null -> a
            b.scale > a.scale || (b.scale == a.scale && !b.uphill) -> b
            else -> a
        }

    private fun rideWay(edge: Int) =
        RideWay(
            network.edgeSurface[edge].toInt(),
            network.edgeMtb[edge].toInt(),
            network.edgeMtbUphill[edge].toInt(),
        )

    /** The step's stretch of its edge's line, in walking order, ends interpolated. */
    private fun walk(
        step: Step,
        line: MutableList<RouteSample>,
        ride: RideWay?,
    ) {
        val first = network.edgeFirstPoint[step.edge]
        val along = network.along(step.edge)
        val total = along.last()
        val (low, high) = if (step.forward) step.from to step.to else step.to to step.from
        val inside = along.indices.filter { along[it] > low * total && along[it] < high * total }
        val start = sampleAt(first, along, step.from * total)
        val end = sampleAt(first, along, step.to * total)
        val between =
            (if (step.forward) inside else inside.reversed()).map { k ->
                Triple(
                    network.pointLat[first + k] * 1e-7,
                    network.pointLon[first + k] * 1e-7,
                    network.pointHeight[first + k] / 10.0,
                )
            }
        for ((lat, lon, height) in listOf(start) + between + listOf(end)) {
            line.join(lat, lon, height, Approach.TRAIL, ride)
        }
    }

    private fun sampleAt(
        first: Int,
        along: DoubleArray,
        metres: Double,
    ): Triple<Double, Double, Double> {
        val k = (1 until along.size).firstOrNull { along[it] >= metres } ?: (along.size - 1)
        val span = along[k] - along[k - 1]
        val t = if (span == 0.0) 0.0 else ((metres - along[k - 1]) / span).coerceIn(0.0, 1.0)

        fun lerp(
            a: Number,
            b: Number,
        ) = a.toDouble() + (b.toDouble() - a.toDouble()) * t
        return Triple(
            lerp(network.pointLat[first + k - 1], network.pointLat[first + k]) * 1e-7,
            lerp(network.pointLon[first + k - 1], network.pointLon[first + k]) * 1e-7,
            lerp(network.pointHeight[first + k - 1], network.pointHeight[first + k]) / 10.0,
        )
    }

    private fun heightAt(snap: Snap): Double {
        val along = network.along(snap.edge)
        return sampleAt(network.edgeFirstPoint[snap.edge], along, snap.fraction * along.last()).third
    }

    /** Appends a vertex unless it is where the line already stands. */
    private fun MutableList<RouteSample>.join(
        latitude: Double,
        longitude: Double,
        heightMetres: Double,
        approach: Approach,
        ride: RideWay? = null,
    ) {
        val last = lastOrNull()
        val step = last?.let { Router.metres(it.latitude, it.longitude, latitude, longitude) } ?: 0.0
        if (last != null && step < SAME_PLACE_METRES) return
        val metres = (last?.metres ?: 0.0) + if (approach == Approach.NO_WAY) 0.0 else step
        add(RouteSample(latitude, longitude, metres, heightMetres, approach, ride))
    }

    private fun MutableList<DemandingStretch>.extend(
        from: Double,
        to: Double,
        sac: Int,
        apart: Boolean,
    ) {
        val last = lastOrNull()
        if (!apart && last != null && from - last.toMetres < SAME_PLACE_METRES) {
            set(size - 1, DemandingStretch(last.fromMetres, to, maxOf(last.sac, sac)))
        } else {
            add(DemandingStretch(from, to, sac))
        }
    }

    private companion object {
        const val DEMANDING_SAC = 3
        const val DEMANDING_MTB = 2
        const val SAME_PLACE_METRES = 0.5
    }
}
