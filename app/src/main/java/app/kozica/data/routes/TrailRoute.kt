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
 * (docs/phase-7b.md § 5), the leg back held on its [back] walk where it has one of its own (§ 6).
 * [checkCancelled] runs before each leg is planned, so a search no longer wanted can stop by throwing;
 * [legPlanned] sees each leg the search plans.
 */
fun TrailNetwork.pointsAlong(
    steps: IntArray,
    router: Router,
    mode: Mode = Mode.HIKING,
    filter: BikeRouteFilter = BikeRouteFilter.ALL,
    back: IntArray? = null,
    checkCancelled: () -> Unit = {},
    legPlanned: (Leg?) -> Unit = {},
): List<RoutePoint> {
    if (steps.isEmpty()) return emptyList()
    val travel = Travel(this, mode, filter)
    val there = moves(steps)
    val other = back?.let { moves(TrailWalks.turned(it)) } ?: there
    val visit = if (other === there) IntArray(there.vertices.size) { it } else there.alignedWith(other)
    val at = there.vertices.map(::pointAt)
    val loop = null !in there.moves && there.vertices.first() == there.vertices.last()

    fun follows(
        from: Int,
        to: Int,
        ways: List<WalkedEdge>,
    ): Boolean {
        checkCancelled()
        val leg = router.leg(at[from].latitude, at[from].longitude, at[to].latitude, at[to].longitude, mode, filter)
        legPlanned(leg)
        return leg?.walked() == ways
    }

    // Where both walks ride the same edges, only a span that is all one-way is walked its own way alone, so only
    // it is not asked to hold reversed; across a gap in the back walk alone, the leg back is the router's.
    fun holds(
        from: Int,
        to: Int,
    ): Boolean {
        val ways = there.moves.subList(from, to).requireNoNulls()
        if (!follows(from, to, ways)) return false
        if (visit[from] < 0 || visit[to] < 0) return true
        val backWays = other.moves.subList(visit[from], visit[to])
        return when {
            backWays == ways -> ways.all { travel.oneWay(it.edge) } || follows(to, from, ways.turned())
            null in backWays -> backWays.size == 1
            else -> follows(to, from, backWays.requireNoNulls().turned())
        }
    }

    fun bothRide(from: Int): Boolean =
        visit[from] >= 0 &&
            visit[from + 1] == visit[from] + 1 &&
            other.moves[visit[from]] == there.moves[from]

    val points = mutableListOf<RoutePoint>()
    for ((first, last) in there.stretches()) {
        val held = (first..last).filter { it == first || it == last || visit[it] >= 0 }
        val end = held.lastIndex
        var here = 0
        // Neighbouring points are about as far apart, so each search starts at the span the last one found.
        var start = 1
        points += at[first]
        while (here < end) {
            // A loop's first leg may not close it, or the Route would have nowhere to go.
            val reach = if (loop && here == 0) end - 1 else end
            var good = here
            var span = minOf(start, reach - here).coerceAtLeast(1)
            while (here + span <= reach && holds(held[here], held[here + span])) {
                good = here + span
                span *= 2
            }
            var bad = minOf(here + span, reach + 1)
            while (bad - good > 1) {
                val middle = (good + bad) / 2
                if (holds(held[here], held[middle])) good = middle else bad = middle
            }
            if (good == here) {
                // Another way joins these two junctions more cheaply, so a point on the Trail's own edge holds it;
                // halfway, a rival has half the edge to beat from either end, and if one still does, nothing better holds it.
                val from = held[here]
                if (held[here + 1] == from + 1 && bothRide(from)) points += halfway(there.moves[from]!!.edge)
                here++
            } else {
                start = good - here
                here = good
            }
            points += at[held[here]]
        }
    }
    return points
}

private fun List<WalkedEdge>.turned() = asReversed().map(WalkedEdge::reversed)

/** A walk as its vertices in order and the edge walked from each to the next, null across a gap. */
private class Moves(
    val vertices: List<Int>,
    val moves: List<WalkedEdge?>,
) {
    /** Each stretch between gaps as the indices of its first and last vertex. */
    fun stretches(): List<Pair<Int, Int>> {
        val gaps = moves.indices.filter { moves[it] == null }
        return (listOf(-1) + gaps).zip(gaps + vertices.lastIndex) { gap, next -> gap + 1 to next }
    }

    /**
     * For each vertex, the index of the same visit in [other], walked the same way round, or -1: move for move
     * where the two walk the same edge, and where they part, the nearest vertex both reach again.
     */
    fun alignedWith(other: Moves): IntArray {
        val visit = IntArray(vertices.size) { -1 }
        var pair = rejoin(other, 0, 0)
        while (pair != null) {
            val (i, k) = pair
            visit[i] = k
            if (i == vertices.lastIndex || k == other.vertices.lastIndex) break
            pair =
                if (moves[i] == other.moves[k] && vertices[i + 1] == other.vertices[k + 1]) {
                    i + 1 to k + 1
                } else {
                    rejoin(other, i + 1, k + 1)
                }
        }
        return visit
    }

    private fun rejoin(
        other: Moves,
        i: Int,
        k: Int,
    ): Pair<Int, Int>? {
        val ahead = vertices.size - i
        val otherAhead = other.vertices.size - k
        for (d in 0 until ahead + otherAhead - 1) {
            for (a in maxOf(0, d - otherAhead + 1)..minOf(d, ahead - 1)) {
                if (vertices[i + a] == other.vertices[k + d - a]) return i + a to k + d - a
            }
        }
        return null
    }
}

private fun TrailNetwork.moves(steps: IntArray): Moves {
    val vertices = mutableListOf<Int>()
    val moves = mutableListOf<WalkedEdge?>()
    for (step in steps) {
        val way = WalkedEdge(TrailWalks.edge(step), !TrailWalks.reversed(step))
        val (from, to) =
            if (way.forward) edgeFrom[way.edge] to edgeTo[way.edge] else edgeTo[way.edge] to edgeFrom[way.edge]
        if (vertices.isEmpty()) {
            vertices += from
        } else if (TrailWalks.gapBefore(step)) {
            moves += null
            vertices += from
        }
        moves += way
        vertices += to
    }
    return Moves(vertices, moves)
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
