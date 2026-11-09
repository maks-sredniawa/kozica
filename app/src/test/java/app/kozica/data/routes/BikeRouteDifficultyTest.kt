package app.kozica.data.routes

import org.junit.Assert.assertEquals
import org.junit.Test

/** docs/phase-7a.md § Tests, the difficulty line: fails towards an easy grade claimed for an ungraded route. */
class BikeRouteDifficultyTest {
    private val network = TrailNetwork.read(javaClass.getResource("/trails-fixture.network")!!.readBytes())
    private val walks = TrailWalks.read(javaClass.getResource("/trails-fixture.edges")!!.readBytes())

    @Test
    fun `the route's own tag is over its ways'`() {
        val difficulty =
            bikeRouteDifficulty(
                bikeRouteFacts(mtbScale = 1, mtbScaleUphill = 2),
                1000.0,
                listOf(GradedSpan(0.0, 500.0, 4)),
                listOf(GradedSpan(0.0, 500.0, 5)),
            )
        assertEquals(BikeRouteDifficulty(MtbGrade(1, null), MtbGrade(2, null)), difficulty)
    }

    @Test
    fun `its ways give the hardest grade and the share of the walk they grade`() {
        val graded = listOf(GradedSpan(0.0, 100.0, 1), GradedSpan(400.0, 700.0, 3))
        assertEquals(MtbGrade(3, 40), bikeRouteDifficulty(bikeRouteFacts(), 1000.0, graded, emptyList()).grade)
    }

    @Test
    fun `nothing graded gives no line`() {
        assertEquals(BikeRouteDifficulty(), bikeRouteDifficulty(bikeRouteFacts(), 1000.0, emptyList(), emptyList()))
    }

    @Test
    fun `a sliver is never rounded to nought, nor most of it to all`() {
        fun percent(metres: Double) =
            bikeRouteDifficulty(
                bikeRouteFacts(),
                1000.0,
                listOf(GradedSpan(0.0, metres, 2)),
                emptyList(),
            ).grade?.gradedPercent
        assertEquals(1, percent(1.0))
        assertEquals(99, percent(999.0))
        assertEquals(100, percent(1000.0))
    }

    @Test
    fun `a graded edge's span is where it lies along the walk, across a gap too`() {
        val steps = walks.steps(8)!!
        val line = network.walkLine(steps)
        val grades = ByteArray(network.edgeCount)
        grades[TrailWalks.edge(steps[1])] = 4
        val span = network.gradedSpans(steps, line, grades).single()
        val gap = line.indexOfFirst { it.breakBefore }
        assertEquals(3, span.scale)
        assertEquals(line[gap].metres, span.fromMetres, 1e-9)
        assertEquals(line.last().metres, span.toMetres, 1e-9)
    }

    @Test
    fun `a reversed edge's span is measured along the walk, not the edge`() {
        val steps = walks.steps(9)!!
        val line = network.walkLine(steps)
        val grades = ByteArray(network.edgeCount)
        grades[TrailWalks.edge(steps.single())] = 1
        val span = network.gradedSpans(steps, line, grades).single()
        assertEquals(0.0, span.fromMetres, 1e-9)
        assertEquals(line.last().metres, span.toMetres, 1e-9)
    }
}

internal fun bikeRouteFacts(
    mtbScale: Int? = null,
    mtbScaleUphill: Int? = null,
) = BikeRouteFacts(
    id = 1,
    name = null,
    namePl = null,
    ref = null,
    colour = TrailColour.RED,
    from = null,
    to = null,
    longDistanceId = null,
    longDistanceName = null,
    startLatitude = 49.0,
    startLongitude = 20.0,
    endLatitude = 49.1,
    endLongitude = 20.1,
    metres = 1000.0,
    ascent = 0.0,
    descent = 0.0,
    gaps = 0,
    south = 49.0,
    west = 20.0,
    north = 49.1,
    east = 20.1,
    kind = BikeRouteKind.MTB,
    oneWay = false,
    mtbScale = mtbScale,
    mtbScaleUphill = mtbScaleUphill,
)
