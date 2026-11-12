package app.kozica.data.routes

import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode

/** An edge of a Trail's walk, and whether it is walked from `edgeFrom` to `edgeTo`. */
internal data class WalkedEdge(
    val edge: Int,
    val forward: Boolean,
) {
    fun reversed() = WalkedEdge(edge, !forward)
}

/** The edges the leg walks, in order, each its way; a step of no length walks none. */
private fun Leg.walked(): List<WalkedEdge> = steps.filter { it.from != it.to }.map { WalkedEdge(it.edge, it.forward) }

/**
 * docs/phase-6c.md § 5: the points that hold an ordinary Route on a Trail's walk. The walk's two ends,
 * a point on each side of a gap, and between them a via point at the farthest junction to which a
 * leg, planned either way, walks exactly the walk's edges in order; found by doubling the span along
 * the walk, then bisecting back, since a leg that follows the walk as far as one junction follows it
 * as far as every junction before. A Bike route's walk is held the same way in Cycling under [filter]
 * (docs/phase-7b.md § 5). [checkCancelled] runs before each leg is planned, so a search no longer
 * wanted can stop by throwing; [legPlanned] sees each leg the search plans.
 */
fun TrailNetwork.pointsAlong(
    steps: IntArray,
    router: Router,
    mode: Mode = Mode.HIKING,
    filter: BikeRouteFilter = BikeRouteFilter.ALL,
    checkCancelled: () -> Unit = {},
    legPlanned: (Leg?) -> Unit = {},
): List<RoutePoint> {
    val travel = Travel(this, mode, filter)
    val stretches = mutableListOf<MutableList<Int>>()
    val stretchWays = mutableListOf<MutableList<WalkedEdge>>()
    for (step in steps) {
        val way = WalkedEdge(TrailWalks.edge(step), !TrailWalks.reversed(step))
        val (from, to) =
            if (way.forward) edgeFrom[way.edge] to edgeTo[way.edge] else edgeTo[way.edge] to edgeFrom[way.edge]
        if (stretches.isEmpty() || TrailWalks.gapBefore(step)) {
            stretches += mutableListOf(from)
            stretchWays += mutableListOf<WalkedEdge>()
        }
        stretches.last() += to
        stretchWays.last() += way
    }
    val loop = stretches.size == 1 && stretches[0].first() == stretches[0].last()

    fun follows(
        from: RoutePoint,
        to: RoutePoint,
        ways: List<WalkedEdge>,
    ): Boolean {
        checkCancelled()
        val leg = router.leg(from.latitude, from.longitude, to.latitude, to.longitude, mode, filter)
        legPlanned(leg)
        return leg?.walked() == ways
    }

    // Only a span that is all one-way is walked its own way alone, so only it is not asked to hold reversed.
    fun holds(
        from: RoutePoint,
        to: RoutePoint,
        ways: List<WalkedEdge>,
    ): Boolean =
        follows(from, to, ways) &&
            (
                ways.all { travel.oneWay(it.edge) } ||
                    follows(to, from, ways.asReversed().map(WalkedEdge::reversed))
            )

    val points = mutableListOf<RoutePoint>()
    for ((nodes, ways) in stretches.zip(stretchWays)) {
        val last = nodes.lastIndex
        val at = nodes.map(::pointAt)

        fun holds(
            from: Int,
            to: Int,
        ) = holds(at[from], at[to], ways.subList(from, to))

        var here = 0
        // Neighbouring points are about as far apart, so each search starts at the span the last one found.
        var start = 1
        points += at[0]
        while (here < last) {
            // A loop's first leg may not close it, or the Route would have nowhere to go.
            val reach = if (loop && here == 0) last - 1 else last
            var good = here
            var span = minOf(start, reach - here).coerceAtLeast(1)
            while (here + span <= reach && holds(here, here + span)) {
                good = here + span
                span *= 2
            }
            var bad = minOf(here + span, reach + 1)
            while (bad - good > 1) {
                val middle = (good + bad) / 2
                if (holds(here, middle)) good = middle else bad = middle
            }
            if (good == here) {
                // Another way joins these two junctions more cheaply, so a point on the Trail's own edge holds it;
                // halfway, a rival has half the edge to beat from either end, and if one still does, nothing better holds it.
                points += halfway(ways[here].edge)
                here++
            } else {
                start = good - here
                here = good
            }
            points += at[here]
        }
    }
    return points
}

/** Halfway along the edge by length, between the two of its points either side of it. */
private fun TrailNetwork.halfway(edge: Int): RoutePoint {
    val along = along(edge)
    val metres = along.last() / 2
    val k = (1 until along.size).first { along[it] >= metres }
    val span = along[k] - along[k - 1]
    val t = if (span == 0.0) 0.0 else (metres - along[k - 1]) / span
    val point = edgeFirstPoint[edge] + k - 1
    return RoutePoint(
        (pointLat[point] + (pointLat[point + 1] - pointLat[point]) * t) * 1e-7,
        (pointLon[point] + (pointLon[point + 1] - pointLon[point]) * t) * 1e-7,
    )
}

private fun TrailNetwork.pointAt(node: Int) = RoutePoint(nodeLat[node] * 1e-7, nodeLon[node] * 1e-7)
