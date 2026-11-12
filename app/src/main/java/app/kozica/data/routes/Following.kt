package app.kozica.data.routes

import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

/** docs/phase-6b.md § 3 and § 4; the readings may move them, nothing else should. */
internal const val LEAVING_METRES = 40.0
internal const val FIX_WORST_ACCURACY_METRES = 50.0
internal const val FIX_OLDEST_MILLIS = 30_000L
internal const val WINDOW_METRES_PER_SECOND = 6000.0 / 3600
internal const val WINDOW_LEAST_METRES = 200.0
internal const val LEAVING_MILLIS = 30_000L

/** docs/phase-6b.md § 3: a followed Route untouched for longer is dropped on the next open. */
internal const val FOLLOWED_KEPT_MILLIS = 24 * 60 * 60 * 1000L

/** A position fix as Following reads it; [ageMillis] is how old it was when it arrived, [atMillis] its own time. */
data class FollowFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Double,
    val ageMillis: Long,
    val atMillis: Long,
) {
    /** A stale or vague fix moves the dot and nothing else. */
    val counts: Boolean get() = ageMillis <= FIX_OLDEST_MILLIS && accuracyMetres <= FIX_WORST_ACCURACY_METRES

    val leavingMetres: Double get() = max(LEAVING_METRES, accuracyMetres)
}

/** Where the user is along the Route's line, [metres] from its start, as of the fix matched [atMillis]. */
@Serializable
data class Progress(
    val metres: Double,
    val atMillis: Long,
)

/**
 * What following keeps in the app's settings so it survives a closed app (docs/phase-6b.md § 3): the
 * points rather than the saved Route's id, so a Route followed before it was saved survives too, and
 * the Mode and chip they are planned under, absent from one stored before phase 7b and so a walk.
 */
@Serializable
data class FollowedRoute(
    val points: List<RoutePoint>,
    val name: String? = null,
    val savedId: String? = null,
    val progress: Progress? = null,
    val touchedAtMillis: Long = 0,
    val mode: Mode = Mode.HIKING,
    val bikeRouteFilter: BikeRouteFilter = BikeRouteFilter.ALL,
)

/** What is left to a place along the Route, by the signposts' rule; a Pace applies to [seconds]. */
data class Ahead(
    val metres: Double,
    val ascent: Double,
    val seconds: Double,
)

/** The next point by its index on the Route, and the end. */
data class Left(
    val nextPoint: Int,
    val toNext: Ahead,
    val toEnd: Ahead,
)

/**
 * docs/phase-6b.md § 4: the counting fixes beyond their leaving distance since [sinceMillis], the
 * latest [metres] from the line; once [shown], only a counting fix back within the distance clears it.
 */
data class Leaving(
    val sinceMillis: Long,
    val lastMillis: Long,
    val metres: Double,
    val shown: Boolean,
)

/** Leaving after [fix], [metres] from the line: a fix that does not count changes nothing, and a gap over [LEAVING_MILLIS] restarts the count. */
fun Leaving?.after(
    fix: FollowFix,
    metres: Double,
): Leaving? {
    if (!fix.counts) return this
    if (metres <= fix.leavingMetres) return null
    val since = if (this == null || fix.atMillis - lastMillis > LEAVING_MILLIS) fix.atMillis else sinceMillis
    return Leaving(since, fix.atMillis, metres, shown = this?.shown == true || fix.atMillis - since >= LEAVING_MILLIS)
}

/** A complete Route's line, which Progress moves along (docs/phase-6b.md § 3). */
class FollowedLine(
    private val planned: PlannedRoute,
) {
    private val line = planned.line

    /** docs/phase-7b.md § 5. */
    private val windowMetresPerSecond =
        if (planned.mode == Mode.CYCLING) fastestMetresPerSecondIn(Mode.CYCLING) else WINDOW_METRES_PER_SECOND
    private val pointMetres: List<Double> =
        planned.metresToPoint.map {
            requireNotNull(it) { "a Route with a gap cannot be followed" }
        }

    /** At each vertex, the rule's seconds from the start, the dead band committed in walking order so they never fall. */
    private val rule = DoubleArray(line.size)

    /** At each vertex, the climb from the start by the same walk, scaled so the whole line climbs the sheet's Ascent. */
    private val climb = DoubleArray(line.size)

    init {
        require(planned.complete && line.size >= 2)
        var reference = line[0].heightMetres
        var run = 0.0
        var ascent = 0.0
        var steepDescent = 0.0
        for (k in 1 until line.size) {
            run += line[k].metres - line[k - 1].metres
            val change = line[k].heightMetres - reference
            if (abs(change) >= DEAD_BAND_METRES || k == line.lastIndex) {
                if (change > 0) ascent += change
                if (change < 0 && run > 0 && -change / run > STEEP_GRADE) steepDescent -= change
                reference = line[k].heightMetres
                run = 0.0
            }
            climb[k] = ascent
            rule[k] = ruleSeconds(line[k].metres, ascent, steepDescent)
        }
        for (k in climb.indices) {
            climb[k] =
                if (ascent > 0) climb[k] * planned.ascent / ascent else planned.ascent * line[k].metres / metres
        }
    }

    val metres: Double get() = line.last().metres

    /**
     * Progress after [fix]: unchanged unless the fix counts and the line passes within its leaving
     * distance inside the window ahead; where it passes more than once, the pass least far along wins.
     */
    fun follow(
        progress: Progress?,
        fix: FollowFix,
    ): Progress? {
        if (!fix.counts) return progress
        val from = progress?.metres ?: 0.0
        val matched = firstPass(fix, from, from + window(progress, fix), fix.leavingMetres) ?: return progress
        return Progress(max(from, matched), fix.atMillis)
    }

    /**
     * Where the leg back from [fix] rejoins the line (docs/phase-6b.md § 4): the nearest point inside
     * the window ahead of [progress], or an earlier pass no more than the leaving distance farther.
     */
    fun rejoin(
        progress: Progress?,
        fix: FollowFix,
    ): RoutePoint {
        val from = progress?.metres ?: 0.0
        val to = from + window(progress, fix)
        val nearest = nearestOnLine(fix, from, to).second
        return pointAt(firstPass(fix, from, to, nearest + fix.leavingMetres) ?: from)
    }

    /** How far along the line the window ahead of [progress] reaches for [fix]. */
    fun reach(
        progress: Progress?,
        fix: FollowFix,
    ): Double = ((progress?.metres ?: 0.0) + window(progress, fix)).coerceAtMost(metres)

    private fun window(
        progress: Progress?,
        fix: FollowFix,
    ): Double {
        val elapsed = progress?.let { (fix.atMillis - it.atMillis).coerceAtLeast(0) / 1000.0 } ?: Double.MAX_VALUE
        return max(WINDOW_LEAST_METRES, elapsed * windowMetresPerSecond)
    }

    private fun pointAt(at: Double): RoutePoint {
        val k = line.indexOfFirst { it.metres >= at }.let { if (it < 0) line.lastIndex else it.coerceAtLeast(1) }
        val a = line[k - 1]
        val b = line[k]
        val t = if (b.metres == a.metres) 1.0 else ((at - a.metres) / (b.metres - a.metres)).coerceIn(0.0, 1.0)
        return RoutePoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
    }

    /**
     * "Find me on the Route": the same rule over the whole line ahead of [progress], or from the start;
     * null when the fix does not count or no pass ahead is within its leaving distance.
     */
    fun findMe(
        progress: Progress?,
        fix: FollowFix,
    ): Progress? {
        if (!fix.counts) return null
        val from = progress?.metres ?: 0.0
        val matched = firstPass(fix, from, metres, fix.leavingMetres) ?: return null
        return Progress(max(from, matched), fix.atMillis)
    }

    /** Distance from [fix] to the nearest point of the whole line. */
    fun distance(fix: FollowFix): Double = nearestOnLine(fix, 0.0, metres).second

    /** The end is reached at the last point, within the leaving distance of it. */
    fun ended(
        progress: Progress,
        fix: FollowFix,
    ): Boolean {
        val end = line.last()
        return progress.metres >= metres - fix.leavingMetres &&
            Router.metres(fix.latitude, fix.longitude, end.latitude, end.longitude) <= fix.leavingMetres
    }

    fun left(progress: Progress): Left {
        val next =
            pointMetres.indexOfFirst { it > progress.metres + SAME_PLACE }.takeIf { it >= 0 } ?: pointMetres.lastIndex
        return Left(next, ahead(progress.metres, pointMetres[next]), ahead(progress.metres, metres))
    }

    private fun ahead(
        from: Double,
        to: Double,
    ): Ahead = Ahead(to - from, interpolated(climb, to) - interpolated(climb, from), secondsAt(to) - secondsAt(from))

    /**
     * The router's time to [at] metres: exact at each point, and between two points the leg's time
     * spread by the rule's seconds along the line, which never fall, so the time left never rises.
     */
    internal fun secondsAt(at: Double): Double {
        val leg =
            (0 until pointMetres.lastIndex).firstOrNull { at <= pointMetres[it + 1] } ?: (pointMetres.lastIndex - 1)
        val start = interpolated(rule, pointMetres[leg])
        val whole = interpolated(rule, pointMetres[leg + 1]) - start
        val legSeconds = planned.secondsToPoint[leg + 1]!! - planned.secondsToPoint[leg]!!
        val fraction =
            if (whole >
                0
            ) {
                (interpolated(rule, at.coerceIn(pointMetres[leg], pointMetres[leg + 1])) - start) / whole
            } else {
                0.0
            }
        return planned.secondsToPoint[leg]!! + legSeconds * fraction
    }

    private fun interpolated(
        atVertex: DoubleArray,
        at: Double,
    ): Double {
        val k = line.indexOfFirst { it.metres >= at }
        if (k == 0) return atVertex.first()
        if (k < 0) return atVertex.last()
        val a = line[k - 1].metres
        val span = line[k].metres - a
        val t = if (span <= 0) 1.0 else (at - a) / span
        return atVertex[k - 1] + (atVertex[k] - atVertex[k - 1]) * t
    }

    /**
     * The nearest point of the first pass of the line within [within] metres of [fix], between [from]
     * and [to] metres. A pass also ends where the line, past its nearest point, draws more than
     * [PASS_RISE] away again, so the two sides of a turn are two passes (docs/phase-6b.md, R3).
     */
    private fun firstPass(
        fix: FollowFix,
        from: Double,
        to: Double,
        within: Double,
    ): Double? {
        var best: Pair<Double, Double>? = null
        for (k in 1 until line.size) {
            val a = line[k - 1]
            val b = line[k]
            if (b.metres < from || a.metres > to || b.metres == a.metres) {
                if (best != null) return best.first
                continue
            }
            if (best != null && a.metres >= from && metresTo(fix, a) > best.second + PASS_RISE) return best.first
            val (at, distance) = nearestOnSegment(fix, a, b, from, to)
            if (distance <= within) {
                if (best == null || distance < best.second) best = at to distance
            } else if (best != null) {
                return best.first
            }
        }
        return best?.first
    }

    private fun nearestOnLine(
        fix: FollowFix,
        from: Double,
        to: Double,
    ): Pair<Double, Double> {
        var best = 0.0 to Double.MAX_VALUE
        for (k in 1 until line.size) {
            val a = line[k - 1]
            val b = line[k]
            if (b.metres < from || a.metres > to || b.metres == a.metres) continue
            val candidate = nearestOnSegment(fix, a, b, from, to)
            if (candidate.second < best.second) best = candidate
        }
        return best
    }

    private fun metresTo(
        fix: FollowFix,
        sample: RouteSample,
    ): Double {
        val east = (sample.longitude - fix.longitude) * cos(fix.latitude * PI / 180) * METRES_PER_DEGREE
        val north = (sample.latitude - fix.latitude) * METRES_PER_DEGREE
        return sqrt(east * east + north * north)
    }

    /** Metres along the line of [fix]'s nearest point on the segment from [a] to [b], kept within [from]..[to], and its distance. */
    private fun nearestOnSegment(
        fix: FollowFix,
        a: RouteSample,
        b: RouteSample,
        from: Double,
        to: Double,
    ): Pair<Double, Double> {
        val east = cos(fix.latitude * PI / 180) * METRES_PER_DEGREE
        val ax = (a.longitude - fix.longitude) * east
        val ay = (a.latitude - fix.latitude) * METRES_PER_DEGREE
        val bx = (b.longitude - fix.longitude) * east
        val by = (b.latitude - fix.latitude) * METRES_PER_DEGREE
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        var t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val span = b.metres - a.metres
        val lowest = ((from - a.metres) / span).coerceIn(0.0, 1.0)
        val highest = ((to - a.metres) / span).coerceIn(0.0, 1.0)
        t = t.coerceIn(lowest, highest)
        val x = ax + dx * t
        val y = ay + dy * t
        return a.metres + span * t to sqrt(x * x + y * y)
    }

    private companion object {
        const val METRES_PER_DEGREE = 6_371_008.8 * PI / 180
        const val SAME_PLACE = 1.0
        const val PASS_RISE = 5.0
    }
}
