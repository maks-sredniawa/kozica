package app.kozica.data.routes

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The Trail network of docs/phase-5.md § 2, read whole into arrays: `scripts/build-network.py`'s
 * `write_network` is the other end of this format, and `TrailNetworkTest` holds the two together.
 * Positions are in 1e-7 degrees, heights in decimetres, lengths in metres, times in seconds; an edge on
 * no Bike route has NaN riding seconds.
 */
class TrailNetwork private constructor(
    val nodeLat: IntArray,
    val nodeLon: IntArray,
    val nodeHeight: ShortArray,
    val edgeWay: LongArray,
    val edgeFrom: IntArray,
    val edgeTo: IntArray,
    val edgeFirstPoint: IntArray,
    val edgePointCount: IntArray,
    val edgeLength: FloatArray,
    val edgeSecondsForward: FloatArray,
    val edgeSecondsBackward: FloatArray,
    val edgeAscent: FloatArray,
    val edgeDescent: FloatArray,
    val pointLat: IntArray,
    val pointLon: IntArray,
    val pointHeight: ShortArray,
    val edgeOneway: ByteArray,
    val edgeSac: ByteArray,
    val edgeSets: ByteArray,
    val edgeRide: ByteArray,
    val edgeMtb: ByteArray,
    val edgeMtbUphill: ByteArray,
    val edgeRidingForward: FloatArray,
    val edgeRidingBackward: FloatArray,
    val edgeSurface: ByteArray,
    val edgeRideTouring: ByteArray,
    val edgeRideMtb: ByteArray,
) {
    val nodeCount: Int get() = nodeLat.size
    val edgeCount: Int get() = edgeFrom.size

    /** The edges at node n are `adjacentEdges[adjacencyStart[n] until adjacencyStart[n + 1]]`. */
    internal val adjacencyStart = IntArray(nodeCount + 1)
    internal val adjacentEdges = IntArray(2 * edgeCount)

    init {
        for (edge in 0 until edgeCount) {
            adjacencyStart[edgeFrom[edge] + 1]++
            adjacencyStart[edgeTo[edge] + 1]++
        }
        for (node in 0 until nodeCount) adjacencyStart[node + 1] += adjacencyStart[node]
        val filled = adjacencyStart.copyOf()
        for (edge in 0 until edgeCount) {
            adjacentEdges[filled[edgeFrom[edge]]++] = edge
            adjacentEdges[filled[edgeTo[edge]]++] = edge
        }
    }

    fun isTrail(edge: Int): Boolean = edgeSets[edge].toInt() and SET_TRAIL != 0

    fun canWalkForward(edge: Int): Boolean = isTrail(edge) && edgeOneway[edge].toInt() != BACKWARD_ONLY

    fun canWalkBackward(edge: Int): Boolean = isTrail(edge) && edgeOneway[edge].toInt() != FORWARD_ONLY

    companion object {
        const val FORMAT_VERSION = 3
        const val FORWARD_ONLY = 1
        const val BACKWARD_ONLY = 2
        const val SET_TRAIL = 1
        const val SET_TOURING = 2
        const val SET_MTB = 4
        const val PAVED = 0
        const val UNPAVED = 1
        const val ROUGH = 2
        private val MAGIC = "KZTN".toByteArray(Charsets.US_ASCII)

        fun read(bytes: ByteArray): TrailNetwork {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(MAGIC.size).also(buffer::get)
            require(magic.contentEquals(MAGIC)) { "not a Trail network" }
            val version = buffer.short.toInt()
            require(version == FORMAT_VERSION) { "Trail network format $version, this build reads $FORMAT_VERSION" }
            buffer.short
            val nodes = buffer.int
            val edges = buffer.int
            val points = buffer.int

            fun longs(count: Int) =
                LongArray(count).also { buffer.asLongBuffer().get(it) }.also {
                    advance(
                        buffer,
                        count * 8,
                    )
                }

            fun ints(count: Int) =
                IntArray(count).also { buffer.asIntBuffer().get(it) }.also {
                    advance(
                        buffer,
                        count * 4,
                    )
                }

            fun floats(count: Int) =
                FloatArray(count).also { buffer.asFloatBuffer().get(it) }.also {
                    advance(
                        buffer,
                        count * 4,
                    )
                }

            fun shorts(count: Int) =
                ShortArray(count).also { buffer.asShortBuffer().get(it) }.also {
                    advance(
                        buffer,
                        count * 2,
                    )
                }

            fun bytes(count: Int) = ByteArray(count).also { buffer.get(it) }

            val edgeWay = longs(edges)
            val nodeLat = ints(nodes)
            val nodeLon = ints(nodes)
            val edgeFrom = ints(edges)
            val edgeTo = ints(edges)
            val edgeFirstPoint = ints(edges)
            val edgePointCount = ints(edges)
            val edgeLength = floats(edges)
            val edgeSecondsForward = floats(edges)
            val edgeSecondsBackward = floats(edges)
            val edgeAscent = floats(edges)
            val edgeDescent = floats(edges)
            val edgeRidingForward = floats(edges)
            val edgeRidingBackward = floats(edges)
            val pointLat = ints(points)
            val pointLon = ints(points)
            val nodeHeight = shorts(nodes)
            val pointHeight = shorts(points)
            val edgeOneway = bytes(edges)
            val edgeSac = bytes(edges)
            val edgeSets = bytes(edges)
            val edgeRide = bytes(edges)
            val edgeMtb = bytes(edges)
            val edgeMtbUphill = bytes(edges)
            val edgeSurface = bytes(edges)
            val edgeRideTouring = bytes(edges)
            val edgeRideMtb = bytes(edges)
            require(!buffer.hasRemaining()) { "${buffer.remaining()} bytes past the end of the Trail network" }
            return TrailNetwork(
                nodeLat,
                nodeLon,
                nodeHeight,
                edgeWay,
                edgeFrom,
                edgeTo,
                edgeFirstPoint,
                edgePointCount,
                edgeLength,
                edgeSecondsForward,
                edgeSecondsBackward,
                edgeAscent,
                edgeDescent,
                pointLat,
                pointLon,
                pointHeight,
                edgeOneway,
                edgeSac,
                edgeSets,
                edgeRide,
                edgeMtb,
                edgeMtbUphill,
                edgeRidingForward,
                edgeRidingBackward,
                edgeSurface,
                edgeRideTouring,
                edgeRideMtb,
            )
        }

        private fun advance(
            buffer: ByteBuffer,
            bytes: Int,
        ) {
            buffer.position(buffer.position() + bytes)
        }
    }
}
