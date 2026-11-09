package app.kozica.data.routes

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The colours the map draws a Trail in, by the name the build stores; [OTHER] is any colour outside the five, or none. */
enum class TrailColour(
    val stored: String,
) {
    RED("red"),
    BLUE("blue"),
    GREEN("green"),
    YELLOW("yellow"),
    BLACK("black"),
    OTHER("other"),
    ;

    companion object {
        fun of(stored: String?): TrailColour = entries.firstOrNull { it.stored == stored } ?: OTHER
    }
}

/** What the camera is fitted to when a selection is shown whole. */
interface Bounded {
    val south: Double
    val west: Double
    val north: Double
    val east: Double
}

/**
 * A row of `places.sqlite`'s `trails` table (docs/phase-6c.md § 2). [ascent] and [descent] are along the
 * walk, from its start to its end; walked the other way, the two swap.
 *
 * @param from the start a mapper wrote, null where none was written, and so [to].
 */
data class TrailFacts(
    val id: Long,
    val name: String?,
    val namePl: String?,
    val ref: String?,
    val colour: TrailColour,
    val symbol: String?,
    val from: String?,
    val to: String?,
    val longDistanceId: Long?,
    val longDistanceName: String?,
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double,
    val endLongitude: Double,
    val metres: Double,
    val ascent: Double,
    val descent: Double,
    val gaps: Int,
    override val south: Double,
    override val west: Double,
    override val north: Double,
    override val east: Double,
) : Bounded

/**
 * A Trail as the map shows it: its row, its walk as a line, and where it has neither a name nor ends a
 * mapper wrote, the places nearest the walk's two ends, for its title.
 */
data class TrailDetail(
    val facts: TrailFacts,
    val line: List<TrailPoint>,
    val nearestFrom: String? = null,
    val nearestTo: String? = null,
) {
    val id: Long get() = facts.id
}

/** One vertex of a Trail's walk, [metres] along it, the walk broken before it where the Trail has a gap. */
data class TrailPoint(
    val latitude: Double,
    val longitude: Double,
    val metres: Double,
    val heightMetres: Double,
    val breakBefore: Boolean,
)

/**
 * `trails.edges`, each Trail's walk as steps into the network's edges: `scripts/build-network.py`'s
 * `write_walks` is the other end of this format, and `TrailWalksTest` holds the two together.
 */
class TrailWalks private constructor(
    private val ids: LongArray,
    private val firstStep: IntArray,
    private val stepCount: IntArray,
    private val steps: IntArray,
) {
    val trailCount: Int get() = ids.size

    /** The Trail's steps in walking order, or null for a Trail this file does not hold. */
    fun steps(trail: Long): IntArray? {
        val at = ids.binarySearch(trail)
        if (at < 0) return null
        return steps.copyOfRange(firstStep[at], firstStep[at] + stepCount[at])
    }

    companion object {
        const val FORMAT_VERSION = 1
        private val MAGIC = "KZTE".toByteArray(Charsets.US_ASCII)

        fun edge(step: Int): Int = step ushr 2

        fun gapBefore(step: Int): Boolean = step and 2 != 0

        /** Walked from the edge's last point to its first. */
        fun reversed(step: Int): Boolean = step and 1 != 0

        fun read(bytes: ByteArray): TrailWalks {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(MAGIC.size).also(buffer::get)
            require(magic.contentEquals(MAGIC)) { "not a Trail walks file" }
            val version = buffer.short.toInt()
            require(version == FORMAT_VERSION) { "Trail walks format $version, this build reads $FORMAT_VERSION" }
            buffer.short
            val trails = buffer.int
            val stepTotal = buffer.int
            val ids = LongArray(trails).also { buffer.asLongBuffer().get(it) }
            buffer.position(buffer.position() + trails * 8)

            fun ints(count: Int) =
                IntArray(count).also {
                    buffer.asIntBuffer().get(it)
                    buffer.position(buffer.position() + count * 4)
                }
            val first = ints(trails)
            val count = ints(trails)
            val steps = ints(stepTotal)
            require(!buffer.hasRemaining()) { "${buffer.remaining()} bytes past the end of the Trail walks" }
            return TrailWalks(ids, first, count, steps)
        }
    }
}

/** The walk's points in order, every point of every edge, its first point dropped where it joins the edge before. */
fun TrailNetwork.walkLine(steps: IntArray): List<TrailPoint> {
    val line = mutableListOf<TrailPoint>()
    var metres = 0.0
    for (step in steps) {
        val edge = TrailWalks.edge(step)
        val first = edgeFirstPoint[edge]
        val count = edgePointCount[edge]
        val order = if (TrailWalks.reversed(step)) (count - 1 downTo 0) else (0 until count)
        val gap = TrailWalks.gapBefore(step) || line.isEmpty()
        for ((k, index) in order.withIndex()) {
            if (k == 0 && !gap) continue
            val latitude = pointLat[first + index] * 1e-7
            val longitude = pointLon[first + index] * 1e-7
            val previous = line.lastOrNull()
            if (previous != null && !(k == 0 && gap)) {
                metres += Router.metres(previous.latitude, previous.longitude, latitude, longitude)
            }
            line +=
                TrailPoint(
                    latitude,
                    longitude,
                    metres,
                    pointHeight[first + index] / 10.0,
                    k == 0 && gap && line.isNotEmpty(),
                )
        }
    }
    return line
}
