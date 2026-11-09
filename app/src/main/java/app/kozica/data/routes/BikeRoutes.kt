package app.kozica.data.routes

import kotlin.math.roundToInt

/** The kinds the build stores for a Bike route, which the tiles carry too (docs/phase-7a.md § 2). */
enum class BikeRouteKind(
    val stored: String,
) {
    TOURING("touring"),
    MTB("mtb"),
    ;

    companion object {
        fun of(stored: String): BikeRouteKind = entries.firstOrNull { it.stored == stored } ?: TOURING
    }
}

/**
 * A row of `places.sqlite`'s `bike_routes` table (docs/phase-7a.md § 2). [ascent] and [descent] are
 * along the walk, which a one-way route's runs the way it is ridden.
 *
 * @param mtbScale the digit of the route's own `mtb:scale`, null where it carries none, and so [mtbScaleUphill].
 */
data class BikeRouteFacts(
    val id: Long,
    val name: String?,
    val namePl: String?,
    val ref: String?,
    val colour: TrailColour,
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
    val kind: BikeRouteKind,
    val oneWay: Boolean,
    val mtbScale: Int?,
    val mtbScaleUphill: Int?,
) : Bounded

/**
 * A Bike route as the map shows it, as [TrailDetail] is a Trail.
 *
 * @param graded the stretches of its walk the edges grade, for the profile.
 * @param wholeId whose Sections "Show the whole" shows, null for a route with none.
 */
data class BikeRouteDetail(
    val facts: BikeRouteFacts,
    val line: List<TrailPoint>,
    val nearestFrom: String? = null,
    val nearestTo: String? = null,
    val graded: List<GradedSpan> = emptyList(),
    val difficulty: BikeRouteDifficulty = BikeRouteDifficulty(),
    val wholeId: Long? = facts.longDistanceId,
) {
    val id: Long get() = facts.id
}

/** A stretch of a walk between two distances along it, of one `mtb:scale` digit. */
data class GradedSpan(
    val fromMetres: Double,
    val toMetres: Double,
    val scale: Int,
)

/** @param gradedPercent how much of the walk its ways grade, null where [scale] is the route's own tag. */
data class MtbGrade(
    val scale: Int,
    val gradedPercent: Int?,
)

/** docs/phase-7a.md § 4: each is null when nothing grades it. */
data class BikeRouteDifficulty(
    val grade: MtbGrade? = null,
    val uphill: MtbGrade? = null,
)

/**
 * Where [grades], one byte per edge holding the digit plus one and 0 for none, grade the walk, in
 * metres along [line], which `walkLine` made from the same [steps].
 */
fun TrailNetwork.gradedSpans(
    steps: IntArray,
    line: List<TrailPoint>,
    grades: ByteArray = edgeMtb,
): List<GradedSpan> {
    val spans = mutableListOf<GradedSpan>()
    var next = 0
    for ((i, step) in steps.withIndex()) {
        val edge = TrailWalks.edge(step)
        val joined = i > 0 && !TrailWalks.gapBefore(step)
        val first = if (joined) next - 1 else next
        val last = first + edgePointCount[edge] - 1
        val grade = grades[edge].toInt()
        if (grade > 0) spans += GradedSpan(line[first].metres, line[last].metres, grade - 1)
        next = last + 1
    }
    return spans
}

/** The route's own tag over its ways', the hardest of those with the share of the walk they grade. */
fun bikeRouteDifficulty(
    facts: BikeRouteFacts,
    walkedMetres: Double,
    graded: List<GradedSpan>,
    gradedUphill: List<GradedSpan>,
): BikeRouteDifficulty {
    fun gradeOf(
        own: Int?,
        spans: List<GradedSpan>,
    ): MtbGrade? {
        if (own != null) return MtbGrade(own, gradedPercent = null)
        val hardest = spans.maxOfOrNull { it.scale } ?: return null
        return MtbGrade(hardest, percentOf(spans.sumOf { it.toMetres - it.fromMetres }, walkedMetres))
    }
    return BikeRouteDifficulty(gradeOf(facts.mtbScale, graded), gradeOf(facts.mtbScaleUphill, gradedUphill))
}

/** Never a rounded 0 for something graded, nor a rounded 100 for something not wholly so. */
private fun percentOf(
    part: Double,
    whole: Double,
): Int = if (part >= whole) 100 else (100 * part / whole).roundToInt().coerceIn(1, 99)
