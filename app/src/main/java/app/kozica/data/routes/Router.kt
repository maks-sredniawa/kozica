package app.kozica.data.routes

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Where a point joins the network: on an edge, at a fraction of its length from `edgeFrom`. */
data class Snap(
    val edge: Int,
    val fraction: Double,
    val latitude: Double,
    val longitude: Double,
    val metresAway: Double,
)

/** One edge of a leg, walked from fraction [from] of its length to [to], `edgeFrom` being 0. */
data class Step(
    val edge: Int,
    val from: Double,
    val to: Double,
) {
    val forward: Boolean get() = to > from
}

/** A leg's [seconds] include the walk from each point to where it joins the Trails, at 4 km/h. */
data class Leg(
    val start: Snap,
    val end: Snap,
    val steps: List<Step>,
    val seconds: Double,
    val nodesVisited: Int,
)

/** docs/phase-5.md § 3: the shortest walking time between two points over the Trails. */
class Router(
    private val network: TrailNetwork,
) {
    private val pointEdge = IntArray(network.pointLat.size)

    /** Every segment, keyed by each grid cell its bounding box touches, sorted: `cell shl 32 or point`. */
    private val cells: LongArray

    init {
        var keys = LongArray(network.pointLat.size + network.pointLat.size / 4)
        var count = 0
        for (edge in 0 until network.edgeCount) {
            val first = network.edgeFirstPoint[edge]
            for (point in first until first + network.edgePointCount[edge]) pointEdge[point] = edge
            if (!network.isTrail(edge)) continue
            for (point in first until first + network.edgePointCount[edge] - 1) {
                val lat1 = network.pointLat[point]
                val lat2 = network.pointLat[point + 1]
                val lon1 = network.pointLon[point]
                val lon2 = network.pointLon[point + 1]
                for (r in row(minOf(lat1, lat2))..row(maxOf(lat1, lat2))) {
                    for (c in column(minOf(lon1, lon2))..column(maxOf(lon1, lon2))) {
                        if (count == keys.size) keys = keys.copyOf(count * 2)
                        keys[count++] = cell(r, c).toLong() shl 32 or point.toLong()
                    }
                }
            }
        }
        cells = keys.copyOf(count).also { it.sort() }
    }

    /** The nearest point on any Trail, or null when none is within [MAX_RINGS] cells. */
    fun nearest(
        latitude: Double,
        longitude: Double,
    ): Snap? {
        for (ring in 0..MAX_RINGS) {
            val found = nearestPerEdge(latitude, longitude, ring).minByOrNull { it.metresAway }
            if (found != null && found.metresAway <= ring * CELL_METRES) return found
        }
        return null
    }

    /**
     * Every Trail within [JOIN_METRES], each at its nearest point, or the nearest Trail when none is: the
     * leg then picks the join that makes it shortest (decided with the owner on 2026-10-25). Empty when
     * [nearest] finds none.
     */
    fun joins(
        latitude: Double,
        longitude: Double,
    ): List<Snap> {
        val near =
            nearestPerEdge(latitude, longitude, ceil(JOIN_METRES / CELL_METRES).toInt()).filter {
                it.metresAway <=
                    JOIN_METRES
            }
        return near.ifEmpty { listOfNotNull(nearest(latitude, longitude)) }
    }

    /** The nearest point of each edge with a segment in the cells up to [rings] away. */
    private fun nearestPerEdge(
        latitude: Double,
        longitude: Double,
        rings: Int,
    ): List<Snap> {
        val scale = cos(latitude * PI / 180)
        val bestSquared = HashMap<Int, Double>()
        val bestAt = HashMap<Int, Pair<Int, Double>>()
        val centreRow = row((latitude * 1e7).toInt())
        val centreColumn = column((longitude * 1e7).toInt())
        for (r in centreRow - rings..centreRow + rings) {
            for (c in centreColumn - rings..centreColumn + rings) {
                var index = lowerBound(cell(r, c).toLong() shl 32)
                while (index < cells.size && (cells[index] ushr 32).toInt() == cell(r, c)) {
                    val point = (cells[index] and 0xffffffffL).toInt()
                    index++
                    val ax = network.pointLon[point] * 1e-7 * scale
                    val ay = network.pointLat[point] * 1e-7
                    val dx = network.pointLon[point + 1] * 1e-7 * scale - ax
                    val dy = network.pointLat[point + 1] * 1e-7 - ay
                    val px = longitude * scale - ax
                    val py = latitude - ay
                    val lengthSquared = dx * dx + dy * dy
                    val t = if (lengthSquared == 0.0) 0.0 else ((px * dx + py * dy) / lengthSquared).coerceIn(0.0, 1.0)
                    val ex = px - t * dx
                    val ey = py - t * dy
                    val squared = ex * ex + ey * ey
                    val edge = pointEdge[point]
                    if (squared < (bestSquared[edge] ?: Double.MAX_VALUE)) {
                        bestSquared[edge] = squared
                        bestAt[edge] = point to t
                    }
                }
            }
        }
        return bestAt.entries.sortedBy { it.key }.map { (edge, at) ->
            snapAt(edge, at.first, at.second, latitude, longitude)
        }
    }

    private fun snapAt(
        edge: Int,
        point: Int,
        t: Double,
        latitude: Double,
        longitude: Double,
    ): Snap {
        val first = network.edgeFirstPoint[edge]
        var before = 0.0
        var total = 0.0
        for (p in first until first + network.edgePointCount[edge] - 1) {
            val segment = segmentMetres(p)
            if (p < point) before += segment
            if (p == point) before += segment * t
            total += segment
        }
        val lat = lerp(network.pointLat[point], network.pointLat[point + 1], t)
        val lon = lerp(network.pointLon[point], network.pointLon[point + 1], t)
        return Snap(edge, if (total == 0.0) 0.0 else before / total, lat, lon, metres(latitude, longitude, lat, lon))
    }

    /** The leg between two positions, through whichever of each one's [joins] makes it shortest. */
    fun leg(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
    ): Leg? = leg(joins(fromLatitude, fromLongitude), joins(toLatitude, toLongitude), toLatitude, toLongitude)

    /**
     * A* from every start join to every end join, with the straight line to the end position at 4 km/h
     * as its heuristic: no Trail and no walk to one is faster than the flat. A walk to a Trail counts
     * [JOIN_WEIGHT] times while the join is chosen, and once in the leg's time.
     */
    fun leg(
        starts: List<Snap>,
        ends: List<Snap>,
        toLatitude: Double,
        toLongitude: Double,
    ): Leg? {
        if (starts.isEmpty() || ends.isEmpty()) return null
        val nodes = network.nodeCount
        val cost = DoubleArray(nodes) { Double.POSITIVE_INFINITY }
        // The edge a node was reached by, or -1 - the index of the start join it was opened from.
        val via = IntArray(nodes)
        val closed = BooleanArray(nodes)
        val heap = Heap(64)
        var best = Double.POSITIVE_INFINITY
        var bestNode = -1
        var bestEnd = -1
        var direct: Pair<Int, Int>? = null
        var visited = 0

        // Weighted while choosing, so a farther Trail wins only for a much shorter Route, never as a shortcut.
        fun offTrail(snap: Snap) = snap.metresAway / FLAT_METRES_PER_SECOND * JOIN_WEIGHT

        fun walked(
            start: Snap,
            end: Snap,
            weighted: Double,
        ) = weighted - (JOIN_WEIGHT - 1) * (start.metresAway + end.metresAway) / FLAT_METRES_PER_SECOND

        for ((s, start) in starts.withIndex()) {
            for ((e, end) in ends.withIndex()) {
                if (start.edge != end.edge) continue
                val forward = end.fraction >= start.fraction
                val possible = if (forward) network.canWalkForward(start.edge) else network.canWalkBackward(start.edge)
                if (!possible && end.fraction != start.fraction) continue
                val seconds = network.part(start.edge, start.fraction, end.fraction).seconds
                val total = offTrail(start) + seconds + offTrail(end)
                if (total < best) {
                    best = total
                    direct = s to e
                }
            }
        }

        fun heuristic(node: Int): Double =
            metres(network.nodeLat[node] * 1e-7, network.nodeLon[node] * 1e-7, toLatitude, toLongitude) /
                FLAT_METRES_PER_SECOND

        fun open(
            node: Int,
            seconds: Double,
            reachedBy: Int,
        ) {
            if (seconds < cost[node]) {
                cost[node] = seconds
                via[node] = reachedBy
                heap.push(node, seconds + heuristic(node))
            }
        }
        // A join on a node stands on it, whichever way the edge may be walked.
        for ((s, start) in starts.withIndex()) {
            if (network.canWalkBackward(start.edge) || start.fraction == 0.0) {
                val seconds = network.part(start.edge, start.fraction, 0.0).seconds
                open(network.edgeFrom[start.edge], offTrail(start) + seconds, -1 - s)
            }
            if (network.canWalkForward(start.edge) || start.fraction == 1.0) {
                val seconds = network.part(start.edge, start.fraction, 1.0).seconds
                open(network.edgeTo[start.edge], offTrail(start) + seconds, -1 - s)
            }
        }
        val endsAt = HashMap<Int, MutableList<Int>>()
        for ((e, end) in ends.withIndex()) {
            endsAt.getOrPut(network.edgeFrom[end.edge]) { mutableListOf() }.add(e)
            endsAt.getOrPut(network.edgeTo[end.edge]) { mutableListOf() }.add(e)
        }
        val restFromEdgeFrom = ends.map { network.part(it.edge, 0.0, it.fraction).seconds }
        val restFromEdgeTo = ends.map { network.part(it.edge, 1.0, it.fraction).seconds }

        while (heap.isNotEmpty()) {
            val node = heap.peekNode()
            val estimate = heap.pop()
            if (closed[node]) continue
            if (estimate >= best) break
            closed[node] = true
            visited++
            for (e in endsAt[node].orEmpty()) {
                val end = ends[e]
                val rest =
                    when {
                        node == network.edgeFrom[end.edge] &&
                            (network.canWalkForward(end.edge) || end.fraction == 0.0) ->
                            restFromEdgeFrom[e]
                        node == network.edgeTo[end.edge] &&
                            (network.canWalkBackward(end.edge) || end.fraction == 1.0) ->
                            restFromEdgeTo[e]
                        else -> Double.POSITIVE_INFINITY
                    }
                if (cost[node] + rest + offTrail(end) < best) {
                    best = cost[node] + rest + offTrail(end)
                    bestNode = node
                    bestEnd = e
                    direct = null
                }
            }
            for (index in network.adjacencyStart[node] until network.adjacencyStart[node + 1]) {
                val edge = network.adjacentEdges[index]
                if (network.edgeFrom[edge] == node && network.canWalkForward(edge)) {
                    open(network.edgeTo[edge], cost[node] + network.edgeSecondsForward[edge], edge)
                }
                if (network.edgeTo[edge] == node && network.canWalkBackward(edge)) {
                    open(network.edgeFrom[edge], cost[node] + network.edgeSecondsBackward[edge], edge)
                }
            }
        }

        if (best == Double.POSITIVE_INFINITY) return null
        direct?.let { (s, e) ->
            return Leg(
                starts[s],
                ends[e],
                listOf(Step(starts[s].edge, starts[s].fraction, ends[e].fraction)).filter { it.from != it.to },
                walked(starts[s], ends[e], best),
                visited,
            )
        }
        val end = ends[bestEnd]
        val steps = ArrayDeque<Step>()
        val endForward = bestNode == network.edgeFrom[end.edge]
        if (if (endForward) end.fraction > 0 else end.fraction < 1) {
            steps.addFirst(Step(end.edge, if (endForward) 0.0 else 1.0, end.fraction))
        }
        var node = bestNode
        while (via[node] >= 0) {
            val edge = via[node]
            val forward = network.edgeTo[edge] == node
            steps.addFirst(if (forward) Step(edge, 0.0, 1.0) else Step(edge, 1.0, 0.0))
            node = if (forward) network.edgeFrom[edge] else network.edgeTo[edge]
        }
        val start = starts[-1 - via[node]]
        val startForward = node == network.edgeTo[start.edge]
        if (if (startForward) start.fraction < 1 else start.fraction > 0) {
            steps.addFirst(Step(start.edge, start.fraction, if (startForward) 1.0 else 0.0))
        }
        return Leg(start, end, steps.toList(), walked(start, end, best), visited)
    }

    private fun row(latitudeE7: Int) = Math.floorDiv(latitudeE7, CELL_LAT_E7)

    private fun column(longitudeE7: Int) = Math.floorDiv(longitudeE7, CELL_LON_E7)

    private fun cell(
        row: Int,
        column: Int,
    ) = row * 65_536 + column

    private fun lowerBound(key: Long): Int {
        var low = 0
        var high = cells.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (cells[middle] < key) low = middle + 1 else high = middle
        }
        return low
    }

    private fun segmentMetres(point: Int): Double =
        metres(
            network.pointLat[point] * 1e-7,
            network.pointLon[point] * 1e-7,
            network.pointLat[point + 1] * 1e-7,
            network.pointLon[point + 1] * 1e-7,
        )

    private fun lerp(
        a: Int,
        b: Int,
        t: Double,
    ): Double = (a + (b - a) * t) * 1e-7

    private class Heap(
        capacity: Int,
    ) {
        private var nodes = IntArray(capacity)
        private var keys = DoubleArray(capacity)
        private var size = 0

        fun isNotEmpty() = size > 0

        fun peekNode() = nodes[0]

        fun push(
            node: Int,
            key: Double,
        ) {
            if (size == nodes.size) {
                nodes = nodes.copyOf(size * 2)
                keys = keys.copyOf(size * 2)
            }
            var i = size++
            while (i > 0) {
                val parent = (i - 1) / 2
                if (keys[parent] <= key) break
                nodes[i] = nodes[parent]
                keys[i] = keys[parent]
                i = parent
            }
            nodes[i] = node
            keys[i] = key
        }

        /** Removes the smallest entry and returns its key; [peekNode] named its node. */
        fun pop(): Double {
            val top = keys[0]
            val lastNode = nodes[--size]
            val lastKey = keys[size]
            var i = 0
            while (true) {
                var child = 2 * i + 1
                if (child >= size) break
                if (child + 1 < size && keys[child + 1] < keys[child]) child++
                if (keys[child] >= lastKey) break
                nodes[i] = nodes[child]
                keys[i] = keys[child]
                i = child
            }
            nodes[i] = lastNode
            keys[i] = lastKey
            return top
        }
    }

    companion object {
        /** How far from a point a Trail may be and still be considered for joining it. */
        const val JOIN_METRES = 1500.0
        const val JOIN_WEIGHT = 10.0
        private const val EARTH_RADIUS = 6_371_008.8
        private const val CELL_LAT_E7 = 100_000
        private const val CELL_LON_E7 = 150_000

        /** The shortest side of a cell anywhere in the Regions, about 49–51° N. */
        private const val CELL_METRES = 1_000.0
        private const val MAX_RINGS = 60

        fun metres(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
        ): Double {
            val p1 = lat1 * PI / 180
            val p2 = lat2 * PI / 180
            val dp = p2 - p1
            val dl = (lon2 - lon1) * PI / 180
            val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * EARTH_RADIUS * asin(sqrt(a))
        }
    }
}
