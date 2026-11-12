package app.kozica.data.routes

import app.kozica.data.Mode
import kotlin.math.abs

// docs/phase-5.md § 3's rule; scripts/build-network.py's constants of the same names are the source.
internal const val DEAD_BAND_METRES = 5.0
internal const val FLAT_METRES_PER_SECOND = 4000.0 / 3600
internal const val SECONDS_PER_METRE_UP = 600.0 / 100
internal const val SECONDS_PER_METRE_STEEP_DOWN = 300.0 / 100
internal const val STEEP_GRADE = 0.10

// docs/phase-7b.md § 2's riding rule; scripts/build-network.py's RIDING is the source. Indexed by surface class.
internal val RIDING_FLAT_KMH = doubleArrayOf(17.53, 15.26, 11.6)
internal val RIDING_DESCENT_CAP_KMH = doubleArrayOf(19.28, 16.79, 18.2)
internal const val RIDING_SECONDS_PER_METRE_UP = 3.22
internal const val RIDING_DESCENT_FACTOR = 1.92
internal const val RIDING_PUSHING_FACTOR = 0.92

/** S2's, S3's, and S4's and over. */
internal val RIDING_MTB_CAP_KMH = doubleArrayOf(10.0, 6.0, 4.0)

/** What walking an edge from fraction `from` of its length to `to` takes, by docs/phase-5.md § 3's rule, or riding it by docs/phase-7b.md § 2's. */
data class EdgePart(
    val seconds: Double,
    val ascent: Double,
    val descent: Double,
)

/**
 * A whole edge gets exactly what the build stored; a part of one gets `climb()`'s rule run over the
 * heights it walks, in walking order, its cut ends interpolated, and in Cycling [ridingSeconds] over them.
 */
fun TrailNetwork.part(
    edge: Int,
    from: Double,
    to: Double,
    mode: Mode = Mode.HIKING,
): EdgePart {
    val forward = to >= from
    val cycling = mode == Mode.CYCLING
    if (minOf(from, to) == 0.0 && maxOf(from, to) == 1.0) {
        val seconds =
            when {
                cycling -> if (forward) edgeRidingForward else edgeRidingBackward
                else -> if (forward) edgeSecondsForward else edgeSecondsBackward
            }
        return EdgePart(
            seconds[edge].toDouble(),
            (if (forward) edgeAscent else edgeDescent)[edge].toDouble(),
            (if (forward) edgeDescent else edgeAscent)[edge].toDouble(),
        )
    }
    val first = edgeFirstPoint[edge]
    val along = along(edge)
    val total = along.last()
    val (low, high) = minOf(from, to) * total to maxOf(from, to) * total
    val inside = along.indices.filter { along[it] > low && along[it] < high }
    val metres = listOf(from * total) + (if (forward) inside else inside.reversed()).map { along[it] } + (to * total)
    val heights = metres.map { heightAt(first, along, it) }
    val walked = walk(metres, heights)
    val seconds =
        if (cycling) {
            val ridden = if (forward) metres to heights else metres.reversed() to heights.reversed()
            ridingSeconds(edge, ridden.first, ridden.second, forward)
        } else {
            ruleSeconds(abs(to - from) * edgeLength[edge], walked.ascent, walked.steepDescent)
        }
    return EdgePart(seconds, walked.ascent, walked.descent)
}

/**
 * build-network.py's `riding_seconds()` for one direction, over [heights] at [metres] along the edge in
 * the edge's own order: its runs are always counted that way, and backward rides them reversed, each
 * change negated, so a whole edge gives exactly the seconds the build stored.
 */
internal fun TrailNetwork.ridingSeconds(
    edge: Int,
    metres: List<Double>,
    heights: List<Double>,
    forward: Boolean,
): Double {
    val surface = edgeSurface[edge].toInt()
    val mtb = edgeMtb[edge].toInt()
    val mtbUp = edgeMtbUphill[edge].toInt()
    val stretches = runs(metres, heights)
    return if (forward) {
        stretches.sumOf { (length, change) -> ridingStretch(length, change, surface, mtb, mtbUp) }
    } else {
        stretches.asReversed().sumOf { (length, change) -> ridingStretch(length, -change, surface, mtb, mtbUp) }
    }
}

/** `climb()`'s runs, as (length, change), the last ending at the last point whatever its height differs by there. */
internal fun runs(
    metres: List<Double>,
    heights: List<Double>,
): List<Pair<Double, Double>> {
    val found = mutableListOf<Pair<Double, Double>>()
    var reference = heights.first()
    var run = 0.0
    for (i in 1 until heights.size) {
        run += abs(metres[i] - metres[i - 1])
        val change = heights[i] - reference
        if (abs(change) < DEAD_BAND_METRES && i < heights.size - 1) continue
        found += run to change
        reference = heights[i]
        run = 0.0
    }
    return found
}

internal fun ridingStretch(
    length: Double,
    change: Double,
    surface: Int,
    mtb: Int,
    mtbUp: Int,
): Double {
    val flat = RIDING_FLAT_KMH[surface]
    val seconds: Double
    val cap: Double
    if (change > 0) {
        seconds = atKmh(length, flat) + change * RIDING_SECONDS_PER_METRE_UP
        cap = mtbCapKmh(if (mtbUp != 0) mtbUp else mtb)
    } else {
        val speed = if (change < 0) minOf(flat * RIDING_DESCENT_FACTOR, RIDING_DESCENT_CAP_KMH[surface]) else flat
        seconds = atKmh(length, speed)
        cap = mtbCapKmh(mtb)
    }
    val steepDown = if (change < 0 && length > 0 && -change / length > STEEP_GRADE) -change else 0.0
    val pushing = ruleSeconds(length, maxOf(change, 0.0), steepDown)
    return minOf(maxOf(seconds, atKmh(length, cap)), pushing * RIDING_PUSHING_FACTOR)
}

/** The speed an `mtb` byte, its digit plus one, caps a stretch at; none below S2. */
private fun mtbCapKmh(mtb: Int): Double {
    val scale = mtb - 1
    return if (scale < 2) Double.POSITIVE_INFINITY else RIDING_MTB_CAP_KMH[minOf(scale, 4) - 2]
}

private fun atKmh(
    length: Double,
    kmh: Double,
): Double = length * 3.6 / kmh

internal data class Walked(
    val ascent: Double,
    val descent: Double,
    val steepDescent: Double,
)

/** Climbs over [heights] at [metres] along a line, in walking order, by the dead band; a descent steeper than [STEEP_GRADE] counts apart. */
internal fun walk(
    metres: List<Double>,
    heights: List<Double>,
): Walked {
    var ascent = 0.0
    var descent = 0.0
    var steepDescent = 0.0
    var reference = heights.first()
    var run = 0.0
    for (i in 1 until heights.size) {
        run += abs(metres[i] - metres[i - 1])
        val change = heights[i] - reference
        if (abs(change) < DEAD_BAND_METRES && i < heights.size - 1) continue
        val steep = run > 0 && abs(change) / run > STEEP_GRADE
        if (change > 0) ascent += change else descent -= change
        if (change < 0 && steep) steepDescent -= change
        reference = heights[i]
        run = 0.0
    }
    return Walked(ascent, descent, steepDescent)
}

internal fun ruleSeconds(
    flatMetres: Double,
    ascent: Double,
    steepDescent: Double,
): Double =
    flatMetres / FLAT_METRES_PER_SECOND + ascent * SECONDS_PER_METRE_UP + steepDescent * SECONDS_PER_METRE_STEEP_DOWN

/** Metres from the edge's first point to each of its points. */
internal fun TrailNetwork.along(edge: Int): DoubleArray {
    val first = edgeFirstPoint[edge]
    val along = DoubleArray(edgePointCount[edge])
    for (k in 1 until along.size) {
        along[k] = along[k - 1] +
            Router.metres(
                pointLat[first + k - 1] * 1e-7,
                pointLon[first + k - 1] * 1e-7,
                pointLat[first + k] * 1e-7,
                pointLon[first + k] * 1e-7,
            )
    }
    return along
}

/** In metres, interpolated between the edge's points. */
private fun TrailNetwork.heightAt(
    first: Int,
    along: DoubleArray,
    metres: Double,
): Double {
    val k = (1 until along.size).firstOrNull { along[it] >= metres } ?: (along.size - 1)
    val span = along[k] - along[k - 1]
    val t = if (span == 0.0) 0.0 else ((metres - along[k - 1]) / span).coerceIn(0.0, 1.0)
    return (pointHeight[first + k - 1] + (pointHeight[first + k] - pointHeight[first + k - 1]) * t) / 10.0
}
