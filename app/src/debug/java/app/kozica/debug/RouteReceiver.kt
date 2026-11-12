package app.kozica.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import app.kozica.MainApplication
import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode
import app.kozica.data.hikes.minutes
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RoutePlanner
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.Step
import app.kozica.data.routes.TrailNetwork
import app.kozica.data.routes.TrailNetworks
import app.kozica.data.routes.TrailWalks
import app.kozica.data.routes.Travel
import app.kozica.data.routes.WalkedEdge
import app.kozica.data.routes.chipFrom
import app.kozica.data.routes.ridingMinutes
import app.kozica.data.routes.roundedUpToFiveMinutes
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.concurrent.thread

/**
 * Plans a Route and logs its timings and totals, for phase 5's readings (docs/phase-5.md § Readings);
 * its line goes to `cache/route.txt`, one `lat lon height approach` per line. `via` is optional, its
 * points separated by `;`. Delivery needs `-f 0x00000020`.
 *
 * ```bash
 * adb shell am broadcast -n <pkg>.debug/app.kozica.debug.RouteReceiver -f 0x00000020 \
 *   --es from 49.2697,19.9813 --es to 49.2010,20.0710 --es via '49.2420,20.0080'
 * adb logcat -d -s KozicaRoute
 * ```
 *
 * With `--el trail <id>` (and `--ez reversed true` for the other way) it makes the Route along that
 * Trail by "Plan a Route along it"'s code, logs the Trail's row beside it, and plans the points
 * again as a saved Route is on open, for phase 6c's R4.
 *
 * With `--es mode CYCLING` and `--es filter ALL|TOURING|MTB` it plans as the map would in that Mode under
 * that chip, for phase 7b's R4 and R5, and logs each leg's ways.
 *
 * With `--el bikeRoute <id>` (and `--ez reversed true`) it does the same along that Bike route, by
 * "Plan a Route along it"'s code with `--es filter` as the map's chip, and logs the chip it planned
 * under, the nodes the search visited and the Route's own planning, for phase 7b's R8.
 *
 * With `--ez show true` it also saves the Route as "Reading", under the Mode and chip it was planned in,
 * and opens it on the map's sheet in them, so Follow is one tap, for phase 6b's R3 and R4.
 */
class RouteReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.hasExtra("bikeRoute")) {
            val id = intent.getLongExtra("bikeRoute", 0)
            val reversed = intent.getBooleanExtra("reversed", false)
            val current =
                intent.getStringExtra("filter")?.let { name -> BikeRouteFilter.entries.firstOrNull { it.name == name } }
            if (intent.hasExtra("filter") && current == null) {
                resultCode = 1
                resultData = "filter is ALL, TOURING or MTB"
                return
            }
            val pending = goAsync()
            val app = context.applicationContext as MainApplication
            thread(name = "RouteReceiver") {
                try {
                    alongBikeRoute(app, id, reversed, current ?: BikeRouteFilter.ALL)
                } catch (e: Exception) {
                    Log.e(TAG, "planning along a Bike route failed", e)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent.hasExtra("trail")) {
            val trail = intent.getLongExtra("trail", 0)
            val reversed = intent.getBooleanExtra("reversed", false)
            val pending = goAsync()
            val app = context.applicationContext as MainApplication
            thread(name = "RouteReceiver") {
                try {
                    along(app, trail, reversed)
                } catch (e: Exception) {
                    Log.e(TAG, "planning along a Trail failed", e)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        val from = intent.getStringExtra("from")?.let(::position)
        val to = intent.getStringExtra("to")?.let(::position)
        val via =
            intent
                .getStringExtra("via")
                ?.split(";")
                ?.map(::position)
                .orEmpty()
        if (from == null || to == null || via.any { it == null }) {
            resultCode = 1
            resultData = "from and to are required and via is optional, each point as lat,lon, via's joined by ;"
            return
        }
        val mode = intent.getStringExtra("mode")?.let { name -> Mode.entries.firstOrNull { it.name == name } }
        val filter =
            intent.getStringExtra("filter")?.let { name -> BikeRouteFilter.entries.firstOrNull { it.name == name } }
        if ((intent.hasExtra("mode") && mode == null) || (intent.hasExtra("filter") && filter == null)) {
            resultCode = 1
            resultData = "mode is HIKING or CYCLING, filter ALL, TOURING or MTB"
            return
        }
        val show = intent.getBooleanExtra("show", false)
        val pending = goAsync()
        val app = context.applicationContext as MainApplication
        thread(name = "RouteReceiver") {
            try {
                val points = listOf(from) + via.filterNotNull() + listOf(to)
                val under = mode ?: Mode.HIKING
                val chip = filter ?: BikeRouteFilter.ALL
                val route = plan(app, points, under, chip)
                if (route?.mode == Mode.HIKING) logAtPace(app, route.seconds, route.minutes)
                if (show) {
                    val id = runBlocking { app.container.routes.save(null, "Reading", points, under, chip) }
                    app.container.routeToShow.value = id
                    Log.i(TAG, "shown as saved Route $id")
                }
            } catch (e: Exception) {
                Log.e(TAG, "planning failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun plan(
        context: Context,
        points: List<RoutePoint>,
        mode: Mode,
        filter: BikeRouteFilter,
    ): PlannedRoute? {
        var started = SystemClock.elapsedRealtime()
        val network = TrailNetworks.load(context)
        if (network == null) {
            Log.i(TAG, "no network in this build")
            return null
        }
        Log.i(
            TAG,
            "load ${SystemClock.elapsedRealtime() - started} ms: ${network.nodeCount} nodes, ${network.edgeCount} edges",
        )
        started = SystemClock.elapsedRealtime()
        val planner = RoutePlanner(network)
        Log.i(TAG, "index ${SystemClock.elapsedRealtime() - started} ms")
        started = SystemClock.elapsedRealtime()
        val route = planner.plan(points, mode, filter)
        Log.i(
            TAG,
            "plan ${SystemClock.elapsedRealtime() - started} ms, $mode under $filter, " +
                "${route.legs.sumOf { it?.nodesVisited ?: 0 }} nodes visited",
        )
        for ((i, leg) in route.legs.withIndex()) {
            if (leg == null) {
                Log.i(TAG, "leg ${i + 1}: no way")
                continue
            }
            Log.i(
                TAG,
                "leg ${i + 1}: joined ${leg.start.metresAway.toInt()} m and ${leg.end.metresAway.toInt()} m " +
                    "from its points, ${leg.steps.size} edges, ${leg.nodesVisited} nodes visited, " +
                    "${route.secondsToPoint[i + 1]?.let { minutesIn(mode, it) }} min to its end",
            )
            val ways =
                leg.steps.map { network.edgeWay[it.edge] }.fold(listOf<Long>()) { kept, way ->
                    if (kept.lastOrNull() == way) kept else kept + way
                }
            Log.i(TAG, "leg ${i + 1} ways: ${ways.joinToString(" ")}")
        }
        Log.i(
            TAG,
            "route: ${route.metres.toInt()} m, ascent ${route.ascent.toInt()} m, descent ${route.descent.toInt()} m, " +
                "${minutesIn(mode, route.seconds)} min (${(route.seconds / 60).toInt()} unrounded)",
        )
        route.hardestRide?.let { Log.i(TAG, "demanding ride: S${it.scale}${if (it.uphill) " uphill" else ""}") }
        for (stretch in route.demanding) {
            Log.i(
                TAG,
                "demanding: sac ${stretch.sac} from ${stretch.fromMetres.toInt()} m to ${stretch.toMetres.toInt()} m",
            )
        }
        File(context.cacheDir, "route.txt").printWriter().use { out ->
            for (sample in route.line) {
                out.println("${sample.latitude} ${sample.longitude} ${sample.heightMetres} ${sample.approach}")
            }
        }
        return route
    }

    /** The Route's time at the user's Pace beside the signposts', for phase 6b's R2; the Pace is read for up to 5 s. */
    private fun logAtPace(
        app: MainApplication,
        seconds: Double,
        minutes: Int,
    ) {
        val pace =
            runBlocking {
                withTimeoutOrNull(5_000) {
                    app.container.pace
                        .filterNotNull()
                        .first()
                }
            }
        Log.i(
            TAG,
            "at your pace: ${pace.minutes(seconds)} min (pace ${pace?.factor ?: "none"}), signposts $minutes min",
        )
    }

    private fun along(
        app: MainApplication,
        id: Long,
        reversed: Boolean,
    ) {
        val trail = app.container.trail(id)
        if (trail == null) {
            Log.i(TAG, "no Trail $id in this build")
            return
        }
        val facts = trail.facts
        Log.i(
            TAG,
            "trail $id: %.1f m, ascent %.1f m, descent %.1f m, %d gaps".format(
                facts.metres,
                facts.ascent,
                facts.descent,
                facts.gaps,
            ),
        )
        val started = SystemClock.elapsedRealtime()
        val points =
            runBlocking {
                app.container.routeAlong(
                    trail,
                )
            }.orEmpty().let { if (reversed) it.reversed() else it }
        Log.i(TAG, "along: ${points.size} points in ${SystemClock.elapsedRealtime() - started} ms")
        matchWalk(app, id, reversed, points, Mode.HIKING, BikeRouteFilter.ALL)
    }

    /** Plans [points] as the map would, logs how much of the walk [id] it follows in order, then plans them again. */
    private fun matchWalk(
        app: MainApplication,
        id: Long,
        reversed: Boolean,
        points: List<RoutePoint>,
        mode: Mode,
        filter: BikeRouteFilter,
    ) {
        val started = SystemClock.elapsedRealtime()
        val route = runBlocking { app.container.planRoute(points, mode, filter) } ?: return
        Log.i(
            TAG,
            "route: %.1f m, ascent %.1f m, descent %.1f m, %d legs, planned in %d ms, %d nodes visited".format(
                route.metres,
                route.ascent,
                route.descent,
                route.legs.size,
                SystemClock.elapsedRealtime() - started,
                route.legs.sumOf { it?.nodesVisited ?: 0 },
            ),
        )
        val network = TrailNetworks.load(app) ?: return
        val walks = TrailNetworks.walks(app) ?: return
        val there = walks.steps(id) ?: return
        // Reversed, the Route is held on the back walk (docs/phase-7b.md § 6).
        val steps = if (reversed) walks.back(id) ?: TrailWalks.turned(there) else there
        val walk = steps.map { WalkedEdge(TrailWalks.edge(it), !TrailWalks.reversed(it)) }
        // pointsAlong's test over the whole Route: each edge walked is the walk's next, its way; a point inside an
        // edge splits it into two steps, so the edge just matched may come again.
        var next = 0
        var matched: WalkedEdge? = null
        for ((i, leg) in route.legs.withIndex()) {
            val off = mutableListOf<Step>()
            for (step in leg?.steps.orEmpty().filter { it.from != it.to }) {
                when (WalkedEdge(step.edge, step.forward)) {
                    walk.getOrNull(next) -> matched = walk[next++]
                    matched -> Unit
                    else -> {
                        off += step
                        matched = null
                    }
                }
            }
            if (off.isNotEmpty()) {
                val metres = off.sumOf { kotlin.math.abs(it.to - it.from) * network.edgeLength[it.edge] }
                Log.i(
                    TAG,
                    "leg ${i + 1} leaves the walk over ${off.size} edges, %.1f m, from %s".format(metres, points[i]),
                )
            }
        }
        Log.i(TAG, "follows $next of the walk's ${walk.size} edges in order")
        logWalkCover(network, steps, walk, route, mode, filter)
        val again =
            runBlocking { app.container.planRoute(points.map { RoutePoint(it.latitude, it.longitude) }, mode, filter) }
                ?: return
        Log.i(TAG, "again: %.1f m, ascent %.1f m, descent %.1f m".format(again.metres, again.ascent, again.descent))
    }

    /**
     * The same comparison by membership rather than order, so one leg off the walk does not count every
     * leg after it: the metres ridden on and off the walk's edges, each its way, the legs across its gaps,
     * and the walk's metres never ridden, with how many of those the chip's rider direction makes one-way.
     */
    private fun logWalkCover(
        network: TrailNetwork,
        steps: IntArray,
        walk: List<WalkedEdge>,
        route: PlannedRoute,
        mode: Mode,
        filter: BikeRouteFilter,
    ) {
        val onWalk = walk.toSet()
        val ridden = mutableSetOf<WalkedEdge>()
        var on = 0.0
        var off = 0.0
        var acrossGaps = 0.0
        val gapSides =
            steps.indices
                .filter { TrailWalks.gapBefore(steps[it]) }
                .flatMap { listOf(endOf(network, steps[it - 1]), startOf(network, steps[it])) }
                .toSet()
        for ((i, leg) in route.legs.withIndex()) {
            if (leg == null) continue
            var legOff = 0.0
            var legOn = 0.0
            for (step in leg.steps.filter { it.from != it.to }) {
                val metres = kotlin.math.abs(step.to - step.from) * network.edgeLength[step.edge]
                val way = WalkedEdge(step.edge, step.forward)
                if (way in onWalk) legOn += metres else legOff += metres
                ridden += way
            }
            val gap = nodeAt(network, leg.start) in gapSides && nodeAt(network, leg.end) in gapSides
            if (gap) acrossGaps += legOn + legOff else off += legOff
            if (!gap) on += legOn
            if (gap || legOff > 0) {
                Log.i(TAG, "leg ${i + 1}${if (gap) ", across a gap" else ""}: %.1f m off the walk".format(legOff))
            }
        }
        val travel = Travel(network, mode, filter)
        val missed = walk.filter { it !in ridden }
        val missedOneWay = missed.filter { travel.oneWay(it.edge) }
        for (way in missed) {
            val against = if (way.forward) !travel.forward(way.edge) else !travel.backward(way.edge)
            Log.i(
                TAG,
                "never ridden: way ${network.edgeWay[way.edge]}, %.1f m${if (against) ", walked against its rider direction" else ""}"
                    .format(network.edgeLength[way.edge]),
            )
        }
        Log.i(
            TAG,
            "by membership: %.1f m on the walk, %.1f m off it, %.1f m across its %d gaps; ".format(
                on,
                off,
                acrossGaps,
                gapSides.size / 2,
            ) +
                "%d of its edges never ridden, %.1f m, %d of them one-way, %.1f m; %d one-way edges on the walk".format(
                    missed.size,
                    missed.sumOf { network.edgeLength[it.edge].toDouble() },
                    missedOneWay.size,
                    missedOneWay.sumOf { network.edgeLength[it.edge].toDouble() },
                    walk.count { travel.oneWay(it.edge) },
                ),
        )
    }

    private fun startOf(
        network: TrailNetwork,
        step: Int,
    ) = TrailWalks.edge(step).let { if (TrailWalks.reversed(step)) network.edgeTo[it] else network.edgeFrom[it] }

    private fun endOf(
        network: TrailNetwork,
        step: Int,
    ) = TrailWalks.edge(step).let { if (TrailWalks.reversed(step)) network.edgeFrom[it] else network.edgeTo[it] }

    /** The node a join stands on, or -1 for one inside its edge. */
    private fun nodeAt(
        network: TrailNetwork,
        snap: app.kozica.data.routes.Snap,
    ) = when (snap.fraction) {
        0.0 -> network.edgeFrom[snap.edge]
        1.0 -> network.edgeTo[snap.edge]
        else -> -1
    }

    private fun alongBikeRoute(
        app: MainApplication,
        id: Long,
        reversed: Boolean,
        current: BikeRouteFilter,
    ) {
        val bikeRoute = app.container.bikeRoute(id)
        if (bikeRoute == null) {
            Log.i(TAG, "no Bike route $id in this build")
            return
        }
        val facts = bikeRoute.facts
        Log.i(
            TAG,
            "bike route $id, ${facts.kind}${if (facts.oneWay) ", one way" else ""}: " +
                "%.1f m, ascent %.1f m, descent %.1f m, %d gaps".format(
                    facts.metres,
                    facts.ascent,
                    facts.descent,
                    facts.gaps,
                ),
        )
        val chip = facts.kind.chipFrom(current)
        var legs = 0
        var visited = 0
        val started = SystemClock.elapsedRealtime()
        val points =
            runBlocking {
                app.container.routeAlongBikeRoute(bikeRoute, chip) { leg ->
                    legs++
                    visited += leg?.nodesVisited ?: 0
                }
            }.orEmpty().let { if (reversed) it.reversed() else it }
        Log.i(
            TAG,
            "along under $chip, the map's chip $current: ${points.size} points in " +
                "${SystemClock.elapsedRealtime() - started} ms, $legs legs searched, $visited nodes visited",
        )
        if (points.size >= 2) matchWalk(app, id, reversed, points, Mode.CYCLING, chip)
    }

    /** As the sheet rounds a time in [mode], with no Pace. */
    private fun minutesIn(
        mode: Mode,
        seconds: Double,
    ) = if (mode == Mode.CYCLING) ridingMinutes(seconds) else roundedUpToFiveMinutes(seconds)

    private fun position(text: String): RoutePoint? {
        val parts = text.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        return if (parts.size == 2) RoutePoint(parts[0], parts[1]) else null
    }

    private companion object {
        const val TAG = "KozicaRoute"
    }
}
