package app.kozica.data.gpx

import app.kozica.data.hikes.Height
import app.kozica.data.hikes.Pin
import app.kozica.data.hikes.Track
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.TrailPoint
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

data class GpxPoint(
    val latitude: Double,
    val longitude: Double,
    val heightMetres: Double? = null,
    val time: Instant? = null,
)

data class GpxWaypoint(
    val latitude: Double,
    val longitude: Double,
    val name: String?,
)

/**
 * One file of docs/phase-6.md § 8: a single `<trk>` of [segments] and its [waypoints].
 *
 * @param fromOsm whether the line came from OpenStreetMap, so the file carries its attribution.
 */
data class Gpx(
    val name: String,
    val description: String?,
    val segments: List<List<GpxPoint>>,
    val waypoints: List<GpxWaypoint>,
    val fromOsm: Boolean,
)

/** A `<trkseg>` per stretch, each point with its height by § 5's rule, and a `<wpt>` per pinned photo. */
fun outingGpx(
    name: String,
    note: String?,
    track: Track,
    heights: List<Height?>,
    photos: List<Pair<Pin, String?>>,
): Gpx {
    val each = heights.iterator()
    val segments =
        track.map { stretch ->
            stretch.map { point ->
                val height = if (each.hasNext()) each.next() else null
                GpxPoint(point.latitude, point.longitude, height?.metres, Instant.ofEpochMilli(point.timeMillis))
            }
        }
    val waypoints = photos.map { (pin, caption) -> GpxWaypoint(pin.latitude, pin.longitude, caption) }
    return Gpx(name, note, segments, waypoints, fromOsm = false)
}

/** The planned line with the network's heights, and the user's points as `<wpt>`s. */
fun routeGpx(
    name: String,
    planned: PlannedRoute,
    points: List<RoutePoint>,
): Gpx {
    val line = planned.line.map { GpxPoint(it.latitude, it.longitude, it.heightMetres) }
    val waypoints = points.map { GpxWaypoint(it.latitude, it.longitude, it.label) }
    return Gpx(name, null, listOf(line), waypoints, fromOsm = true)
}

/** A Trail's walk with the network's heights, a `<trkseg>` per stretch where it has gaps (docs/phase-6c.md § 5). */
fun trailGpx(
    name: String,
    line: List<TrailPoint>,
): Gpx {
    val segments = mutableListOf<MutableList<GpxPoint>>()
    for (point in line) {
        if (point.breakBefore || segments.isEmpty()) segments += mutableListOf<GpxPoint>()
        segments.last() += GpxPoint(point.latitude, point.longitude, point.heightMetres)
    }
    return Gpx(name, null, segments, emptyList(), fromOsm = true)
}

fun Gpx.write(out: Appendable) {
    out.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
    out.append("""<gpx version="1.1" creator="Kozica" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
    if (fromOsm) {
        out.append(" <metadata>\n")
        out
            .append(
                """  <copyright author="OpenStreetMap contributors"><license>$ODBL</license></copyright>""",
            ).append('\n')
        out.append("""  <link href="$OSM_COPYRIGHT"><text>© OpenStreetMap contributors</text></link>""").append('\n')
        out.append(" </metadata>\n")
    }
    for (waypoint in waypoints) {
        out.append(" <wpt ").position(waypoint.latitude, waypoint.longitude).append(">")
        waypoint.name?.let { out.append("<name>").text(it).append("</name>") }
        out.append("</wpt>\n")
    }
    out.append(" <trk>\n  <name>").text(name).append("</name>\n")
    description?.let { out.append("  <desc>").text(it).append("</desc>\n") }
    for (segment in segments) {
        out.append("  <trkseg>\n")
        for (point in segment) {
            out.append("   <trkpt ").position(point.latitude, point.longitude).append(">")
            point.heightMetres?.let {
                out
                    .append(
                        "<ele>",
                    ).append(String.format(Locale.ROOT, "%.1f", it))
                    .append("</ele>")
            }
            point.time?.let { out.append("<time>").append(it.toString()).append("</time>") }
            out.append("</trkpt>\n")
        }
        out.append("  </trkseg>\n")
    }
    out.append(" </trk>\n</gpx>\n")
}

/** The Outing's or Route's name and [date], with what a file system or a share target would refuse left out. */
fun gpxFileName(
    name: String,
    date: LocalDate,
): String {
    val safe =
        name
            .filterNot { it in FORBIDDEN_IN_FILE_NAMES || it.isISOControl() }
            .trim()
            .take(MAX_NAME_CHARS)
            .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
            .trim()
    return if (safe.isEmpty()) "kozica $date.gpx" else "$safe $date.gpx"
}

private fun Appendable.position(
    latitude: Double,
    longitude: Double,
): Appendable = append(String.format(Locale.ROOT, """lat="%.7f" lon="%.7f"""", latitude, longitude))

/** Escaped for both text and attributes, with the characters XML 1.0 cannot carry at all dropped. */
private fun Appendable.text(value: String): Appendable {
    for (c in value) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            '\t', '\n', '\r' -> append(c)
            else -> if (c >= ' ' && c != '￾' && c != '￿') append(c)
        }
    }
    return this
}

private const val ODBL = "https://opendatacommons.org/licenses/odbl/1-0/"
private const val OSM_COPYRIGHT = "https://www.openstreetmap.org/copyright"
private const val FORBIDDEN_IN_FILE_NAMES = "/\\:*?\"<>|"
private const val MAX_NAME_CHARS = 60
