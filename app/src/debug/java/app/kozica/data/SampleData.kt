package app.kozica.data

import app.kozica.data.hikes.TrackPoint
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RoutePoint
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID

/** The debug fixture: two saved Routes in the Tatry, the same every time (docs/phase-0.md § 2). */
object SampleData {
    suspend fun seed(repository: RouteRepository) {
        wipe(repository)
        repository.save(
            null,
            "Kuźnice – Morskie Oko",
            listOf(
                RoutePoint(49.2702510, 19.9808508, "Kuźnice"),
                RoutePoint(49.2190881, 20.0163920, "Zawrat"),
                RoutePoint(49.1987000, 20.0712000, "Morskie Oko"),
            ),
            Mode.HIKING,
            BikeRouteFilter.ALL,
        )
        repository.save(
            null,
            "Kasprowy Wierch",
            listOf(
                RoutePoint(49.2702510, 19.9808508, "Kuźnice"),
                RoutePoint(49.2318000, 19.9818000, "Kasprowy Wierch"),
            ),
            Mode.HIKING,
            BikeRouteFilter.ALL,
        )
    }

    suspend fun wipe(repository: RouteRepository) {
        for (route in repository.observeAll().first()) repository.delete(route.id)
    }

    /**
     * The `hikes` variant: two Hikes recorded through the same path a Stop takes. Kuźnice to
     * Kasprowy Wierch with a pause at Myślenickie Turnie, inside the Tatry, so its ascent is the
     * grid's; and a walk along the Vistula in Warsaw, outside every Region, so it is GPS's.
     */
    suspend fun seedHikes(repository: HikeRepository) {
        for (hike in repository.observeEnded().first()) repository.delete(hike.id)
        val morning = Instant.parse("2026-09-12T06:30:00Z")
        hike(
            repository,
            "Kuźnice – Kasprowy Wierch",
            morning,
            listOf(
                line(49.2700, 19.9810, 49.2510, 19.9700, 1_020.0, 1_360.0, 400),
                line(49.2510, 19.9700, 49.2320, 19.9820, null, null, 450),
            ),
            pauseMillis = 20 * 60_000L,
        )
        hike(
            repository,
            "Wisła",
            morning.plusSeconds(3 * 24 * 3_600),
            listOf(line(52.2550, 21.0350, 52.2290, 21.0500, 118.0, 152.0, 300)),
            pauseMillis = 0,
        )
        repository.fillClimbs()
    }

    /**
     * The `route-hike` variant, for docs/phase-6.md's R6: Kuźnice to Kasprowy Wierch planned over the
     * Trails, then its planned line recorded as a Hike, so the two ascents can be read side by side.
     */
    suspend fun seedRouteHike(
        repository: HikeRepository,
        plan: suspend (List<RoutePoint>) -> PlannedRoute?,
    ) {
        val points =
            listOf(RoutePoint(49.2702510, 19.9808508, "Kuźnice"), RoutePoint(49.2318000, 19.9818000, "Kasprowy Wierch"))
        val planned = plan(points) ?: error("no Trail network in this build")
        hike(
            repository,
            "R6 · ${Math.round(planned.ascent)} m planned",
            Instant.parse("2026-09-20T06:30:00Z"),
            listOf(Stretch(planned.line.map { Triple(it.latitude, it.longitude, null) })),
            pauseMillis = 0,
        )
        repository.fillClimbs()
    }

    /**
     * The `pace` variant, for docs/phase-6b.md § 2: Kuźnice to Kasprowy Wierch walked three times at
     * three speeds, every height the grid's, so the Pace is their middle one.
     */
    suspend fun seedPace(repository: HikeRepository) {
        for (hike in repository.observeEnded().first()) repository.delete(hike.id)
        listOf(12_000L, 15_000L, 18_000L).forEachIndexed { day, fixMillis ->
            hike(
                repository,
                "Pace ${fixMillis / 1000} s",
                Instant.parse("2026-09-12T06:30:00Z").plusSeconds(day * 24 * 3_600L),
                listOf(line(49.2700, 19.9810, 49.2320, 19.9820, null, null, 850)),
                pauseMillis = 0,
                fixMillis = fixMillis,
            )
        }
        repository.fillClimbs()
    }

    private class Stretch(
        val points: List<Triple<Double, Double, Double?>>,
    )

    /** [count] fixes 15 s apart from one end to the other, the height straight between the two or none. */
    private fun line(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        fromHeight: Double?,
        toHeight: Double?,
        count: Int,
    ) = Stretch(
        List(count) { i ->
            val t = i / (count - 1.0)
            val height = if (fromHeight != null && toHeight != null) fromHeight + (toHeight - fromHeight) * t else null
            Triple(fromLat + (toLat - fromLat) * t, fromLon + (toLon - fromLon) * t, height)
        },
    )

    private suspend fun hike(
        repository: HikeRepository,
        name: String,
        start: Instant,
        stretches: List<Stretch>,
        pauseMillis: Long,
        fixMillis: Long = FIX_MILLIS,
    ) {
        val id = UUID.randomUUID().toString()
        repository.start(id, start)
        var time = start.toEpochMilli()
        stretches.forEachIndexed { stretch, line ->
            if (stretch > 0) time += pauseMillis
            for ((latitude, longitude, height) in line.points) {
                time += fixMillis
                repository.addPoint(id, stretch, TrackPoint(time, latitude, longitude, height))
            }
        }
        repository.finish(id, Instant.ofEpochMilli(time)) { _, _ -> name }
    }

    private const val FIX_MILLIS = 15_000L
}
