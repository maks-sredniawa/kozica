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
import app.kozica.data.routes.TrailNetworks
import app.kozica.data.routes.TrailWalks
import app.kozica.data.routes.WalkedEdge
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
 * With `--ez show true` it also saves the Route as "Reading" and opens it on the map's sheet, so
 * Follow is one tap, for phase 6b's R3 and R4.
 */
class RouteReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
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
                val route = plan(app, points, mode ?: Mode.HIKING, filter ?: BikeRouteFilter.ALL)
                if (route?.mode == Mode.HIKING) logAtPace(app, route.seconds, route.minutes)
                if (show) {
                    val id = runBlocking { app.container.routes.save(null, "Reading", points) }
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
                    "${route.minutesTo(i + 1)} min to its end",
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
                "${route.minutes} min (${(route.seconds / 60).toInt()} unrounded)",
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
        val route = runBlocking { app.container.planRoute(points) } ?: return
        Log.i(
            TAG,
            "route: %.1f m, ascent %.1f m, descent %.1f m, %d legs".format(
                route.metres,
                route.ascent,
                route.descent,
                route.legs.size,
            ),
        )
        val network = TrailNetworks.load(app) ?: return
        val walk =
            TrailNetworks
                .walks(app)
                ?.steps(id)
                ?.map { WalkedEdge(TrailWalks.edge(it), !TrailWalks.reversed(it)) }
                .orEmpty()
                .let { if (reversed) it.asReversed().map(WalkedEdge::reversed) else it }
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
        val again =
            runBlocking { app.container.planRoute(points.map { RoutePoint(it.latitude, it.longitude) }) } ?: return
        Log.i(TAG, "again: %.1f m, ascent %.1f m, descent %.1f m".format(again.metres, again.ascent, again.descent))
    }

    private fun position(text: String): RoutePoint? {
        val parts = text.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        return if (parts.size == 2) RoutePoint(parts[0], parts[1]) else null
    }

    private companion object {
        const val TAG = "KozicaRoute"
    }
}
