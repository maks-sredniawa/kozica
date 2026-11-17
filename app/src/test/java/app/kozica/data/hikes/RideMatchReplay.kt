package app.kozica.data.hikes

import app.kozica.data.routes.Router
import app.kozica.data.routes.TrailNetwork
import app.kozica.data.routes.TrailPoint
import app.kozica.data.routes.TrailWalks
import app.kozica.data.routes.walkLine
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * docs/phase-7c.md § 1, R1: every Bike route ridden each way it may be, sampled as the recorder samples a fix,
 * matched back. Runs only through `scripts/ride-gate.py`, which names the base assets in `-PrideGate`.
 */
class RideMatchReplay {
    private data class Sampling(
        val kmh: Double,
        val intervalSeconds: Int,
        val minMetres: Double,
    )

    private class Totals {
        var lineMetres = 0.0
        var trackMetres = 0.0
        var matchedMetres = 0.0
        var routes = 0
    }

    @Test
    fun replay() {
        val dir = System.getProperty("rideGate")
        assumeTrue("set -PrideGate to the base assets", dir != null)
        val assets = File(dir!!)
        val network = TrailNetwork.read(File(assets, "trails.network").readBytes())
        val walks = TrailWalks.read(File(assets, "trails.edges").readBytes())
        val router = Router(network)
        val routes =
            File(assets.parentFile, "ride-gate/bike-routes.tsv").readLines().drop(1).map { line ->
                val (id, kind, oneWay) = line.split('\t')
                Triple(id.toLong(), kind, oneWay == "1")
            }
        val samplings =
            listOf(15.0, 25.0, 35.0).map { Sampling(it, 5, 5.0) } +
                listOf(15.0, 25.0, 35.0).map { Sampling(it, 1, 2.0) }
        val totals = samplings.associateWith { Totals() }
        val rows = StringBuilder("id\tkind\tdirection\tkmh\tinterval_s\tline_m\ttrack_m\tmatched_m\n")
        val random = Random(20261117)
        for ((n, route) in routes.withIndex()) {
            val (id, kind, oneWay) = route
            val there = walks.steps(id) ?: continue
            val directions =
                listOf("there" to there) +
                    if (oneWay) emptyList() else listOf("back" to (walks.back(id) ?: TrailWalks.turned(there)))
            for ((direction, steps) in directions) {
                val own = steps.map { TrailWalks.edge(it) }.toSet()
                val line = network.walkLine(steps)
                val lineMetres = stretchesOf(line).sumOf { it.last().metres - it.first().metres }
                for (sampling in samplings) {
                    val track = sample(line, sampling, random)
                    val pieces = router.matchRide(track).flatten()
                    val trackMetres = pieces.sumOf { it.metres }
                    val matched = pieces.filter { it.edge != null && it.edge in own }.sumOf { it.metres }
                    totals.getValue(sampling).apply {
                        this.lineMetres += lineMetres
                        this.trackMetres += trackMetres
                        this.matchedMetres += matched
                        this.routes++
                    }
                    rows.append("$id\t$kind\t$direction\t${sampling.kmh}\t${sampling.intervalSeconds}\t")
                    rows.append("%.0f\t%.0f\t%.0f\n".format(lineMetres, trackMetres, matched))
                }
            }
            if (n % 100 == 0) System.err.println("[${n + 1}/${routes.size}] $id")
        }
        File(assets.parentFile, "ride-gate/ride-gate.tsv").writeText(rows.toString())
        for ((sampling, t) in totals) {
            System.err.println(
                "every %d s, %.0f km/h: %d rides, matched %.2f %%, length %+.2f %%".format(
                    sampling.intervalSeconds,
                    sampling.kmh,
                    t.routes,
                    100 * t.matchedMetres / t.trackMetres,
                    100 * (t.trackMetres - t.lineMetres) / t.lineMetres,
                ),
            )
        }
    }

    private fun stretchesOf(line: List<TrailPoint>): List<List<TrailPoint>> {
        val stretches = mutableListOf<MutableList<TrailPoint>>()
        for (point in line) {
            if (point.breakBefore || stretches.isEmpty()) stretches += mutableListOf<TrailPoint>()
            stretches.last() += point
        }
        return stretches.filter { it.size > 1 }
    }

    /** A fix every interval at the speed along each stretch, moved by noise correlated over 30 s, kept once [Sampling.minMetres] from the last kept. */
    private fun sample(
        line: List<TrailPoint>,
        sampling: Sampling,
        random: Random,
    ): Track =
        stretchesOf(line).map { stretch ->
            val step = sampling.kmh / 3.6 * sampling.intervalSeconds
            val rho = exp(-sampling.intervalSeconds / NOISE_SECONDS)
            val kick = NOISE_METRES * sqrt(1 - rho * rho)
            var east = random.nextGaussian() * NOISE_METRES
            var north = random.nextGaussian() * NOISE_METRES
            val fixes = mutableListOf<TrackPoint>()
            var at = stretch.first().metres
            var i = 0
            var time = 0L
            while (true) {
                while (i < stretch.size - 2 && stretch[i + 1].metres < at) i++
                val a = stretch[i]
                val b = stretch[i + 1]
                val span = b.metres - a.metres
                val t = if (span <= 0) 0.0 else ((at - a.metres) / span).coerceIn(0.0, 1.0)
                val latitude = a.latitude + t * (b.latitude - a.latitude) + north / METRES_PER_DEGREE
                val longitude =
                    a.longitude + t * (b.longitude - a.longitude) +
                        east / (METRES_PER_DEGREE * cos(a.latitude * PI / 180))
                val fix = TrackPoint(time, latitude, longitude, null)
                val last = fixes.lastOrNull()
                if (last == null ||
                    Router.metres(last.latitude, last.longitude, latitude, longitude) >= sampling.minMetres
                ) {
                    fixes +=
                        fix
                }
                if (at >= stretch.last().metres) break
                at = minOf(at + step, stretch.last().metres)
                time += sampling.intervalSeconds * 1000L
                east = rho * east + kick * random.nextGaussian()
                north = rho * north + kick * random.nextGaussian()
            }
            fixes
        }

    private companion object {
        const val NOISE_METRES = 5.0
        const val NOISE_SECONDS = 30.0
        const val METRES_PER_DEGREE = 111_320.0
    }
}
