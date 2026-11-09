package app.kozica.data.routes

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.io.File
import java.io.IOException
import kotlin.math.cos

/** The kinds `scripts/build-network.py` writes, by the name it stores (docs/phase-5.md § 2). */
enum class PlaceKind(
    val stored: String,
) {
    PEAK("peak"),
    PASS("pass"),
    HUT("hut"),
    SHELTER("shelter"),
    VIEWPOINT("viewpoint"),
    TOWN("town"),
    VILLAGE("village"),
    HAMLET("hamlet"),
    LAKE("water"),
    CLEARING("clearing"),
    PLACE("place"),
    ;

    companion object {
        fun of(stored: String): PlaceKind = entries.firstOrNull { it.stored == stored } ?: PLACE
    }
}

/** @param matched the name the search matched, which may be another language's or an older one. */
data class Place(
    val id: Long,
    val kind: PlaceKind,
    val latitude: Double,
    val longitude: Double,
    val region: String,
    val name: String,
    val matched: String = name,
)

/** One search result, a place or a Trail, ranked together (docs/phase-6c.md § 4). */
sealed interface SearchResult {
    /** The name the search matched, which may be another language's or an older one. */
    val matched: String

    data class AtPlace(
        val place: Place,
    ) : SearchResult {
        override val matched: String get() = place.matched
    }

    data class OnTrail(
        val facts: TrailFacts,
        override val matched: String,
    ) : SearchResult

    /** A Long-distance Trail, found by its own name; it has no row, only its [sections]. */
    data class LongDistance(
        val id: Long,
        val name: String,
        val sections: Int,
        override val matched: String,
    ) : SearchResult
}

interface PlaceSearch {
    fun search(
        text: String,
        latitude: Double,
        longitude: Double,
        limit: Int = 20,
    ): List<Place>

    fun nearest(
        latitude: Double,
        longitude: Double,
    ): Place?

    /** Places, Trails and Long-distance Trails, ranked by [search]'s rule; a build without Trails finds places alone. */
    fun searchAll(
        text: String,
        latitude: Double,
        longitude: Double,
        limit: Int = 20,
    ): List<SearchResult> = search(text, latitude, longitude, limit).map(SearchResult::AtPlace)

    /** A Trail's row, null for an id the index does not hold (docs/phase-6c.md § 2). */
    fun trail(id: Long): TrailFacts? = null

    /** The Sections of a Long-distance Trail, by id. */
    fun sections(longDistance: Long): List<Long> = emptyList()

    /** A Bike route's row, null for an id the index does not hold (docs/phase-7a.md § 2). */
    fun bikeRoute(id: Long): BikeRouteFacts? = null

    /** What "Show the whole" of [parent] shows: its Sections, and its own row where its own ways have one. */
    fun bikeRouteWhole(parent: Long): List<Long> = emptyList()
}

/** `places.sqlite`, opened read-only. */
class PlaceIndex(
    file: File,
) : PlaceSearch {
    private val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)

    /**
     * Names starting with [text] before names holding it from a word's start on, each group nearest to
     * the centre first; one row per place, by its best-matched name.
     */
    override fun search(
        text: String,
        latitude: Double,
        longitude: Double,
        limit: Int,
    ): List<Place> {
        val folded = fold(text)
        if (folded.isEmpty()) return emptyList()
        val squeeze = cos(Math.toRadians(latitude)).let { it * it }
        val found = LinkedHashMap<Long, Place>()
        db
            .rawQuery(
                """
                SELECT p.id, p.kind, p.lat, p.lon, p.region, p.name, n.name
                FROM place_names n JOIN places p ON p.id = n.place
                WHERE n.folded MATCH ?
                ORDER BY substr(n.folded, 1, ?) = ? DESC,
                    (p.lat - ?) * (p.lat - ?) + (p.lon - ?) * (p.lon - ?) * ?
                LIMIT ?
                """.trimIndent(),
                arrayOf(
                    "\"$folded*\"",
                    folded.length.toString(),
                    folded,
                    latitude.toString(),
                    latitude.toString(),
                    longitude.toString(),
                    longitude.toString(),
                    squeeze.toString(),
                    (limit * 4).toString(),
                ),
            ).use { rows ->
                while (rows.moveToNext() && found.size < limit) {
                    val id = rows.getLong(0)
                    if (id in found) continue
                    found[id] =
                        Place(
                            id = id,
                            kind = PlaceKind.of(rows.getString(1)),
                            latitude = rows.getDouble(2),
                            longitude = rows.getDouble(3),
                            region = rows.getString(4),
                            name = rows.getString(5),
                            matched = rows.getString(6),
                        )
                }
            }
        return found.values.toList()
    }

    /** The place that best names a spot: within [metres], a peak, pass or Hut before anything else, then the nearest. */
    override fun nearest(
        latitude: Double,
        longitude: Double,
    ): Place? {
        val metres = NAMING_METRES
        val dLat = metres / METRES_PER_DEGREE
        val dLon = dLat / cos(Math.toRadians(latitude))
        val near = mutableListOf<Place>()
        db
            .rawQuery(
                "SELECT id, kind, lat, lon, region, name FROM places WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",
                arrayOf(
                    latitude - dLat,
                    latitude + dLat,
                    longitude - dLon,
                    longitude + dLon,
                ).map { it.toString() }.toTypedArray(),
            ).use { rows ->
                while (rows.moveToNext()) {
                    near +=
                        Place(
                            rows.getLong(0),
                            PlaceKind.of(rows.getString(1)),
                            rows.getDouble(2),
                            rows.getDouble(3),
                            rows.getString(4),
                            rows.getString(5),
                        )
                }
            }
        return near
            .map { it to Router.metres(latitude, longitude, it.latitude, it.longitude) }
            .filter { (_, away) -> away <= metres }
            .minWithOrNull(compareBy({ (place, _) -> place.kind !in LANDMARKS }, { (_, away) -> away }))
            ?.first
    }

    override fun trail(id: Long): TrailFacts? =
        db.rawQuery("SELECT $TRAIL_COLUMNS FROM trails WHERE id = ?", arrayOf(id.toString())).use { rows ->
            if (rows.moveToFirst()) trailFacts(rows) else null
        }

    /**
     * [search]'s places, the Trails whose names match by the same fold and the Long-distance Trails
     * whose own names do, together: a match at a name's start first, then nearest the centre, a Trail
     * measured to its bounding box and nought inside it.
     */
    override fun searchAll(
        text: String,
        latitude: Double,
        longitude: Double,
        limit: Int,
    ): List<SearchResult> {
        val folded = fold(text)
        if (folded.isEmpty()) return emptyList()
        val squeeze = cos(Math.toRadians(latitude)).let { it * it }
        val ranked = mutableListOf<Triple<Boolean, Double, SearchResult>>()
        for (place in search(text, latitude, longitude, limit)) {
            ranked +=
                Triple(
                    fold(place.matched).startsWith(folded),
                    boxAway(latitude, longitude, place.latitude, place.longitude, place.latitude, place.longitude),
                    SearchResult.AtPlace(place),
                )
        }
        val trails = mutableSetOf<Long>()
        db
            .rawQuery(
                """
                SELECT ${TRAIL_COLUMNS.split(", ").joinToString { "t.${it.trim()}" }}, n.name
                FROM trail_names n JOIN trails t ON t.id = n.trail
                WHERE n.folded MATCH ?1
                ORDER BY substr(n.folded, 1, ?2) = ?3 DESC,
                    max(t.min_lat - ?4, 0, ?4 - t.max_lat) * max(t.min_lat - ?4, 0, ?4 - t.max_lat) +
                    max(t.min_lon - ?5, 0, ?5 - t.max_lon) * max(t.min_lon - ?5, 0, ?5 - t.max_lon) * ?6
                LIMIT ?7
                """.trimIndent(),
                arrayOf(
                    "\"$folded*\"",
                    folded.length.toString(),
                    folded,
                    latitude.toString(),
                    longitude.toString(),
                    squeeze.toString(),
                    (limit * 4).toString(),
                ),
            ).use { rows ->
                while (rows.moveToNext()) {
                    val facts = trailFacts(rows)
                    if (!trails.add(facts.id)) continue
                    val matched = rows.getString(TRAIL_COLUMN_COUNT)
                    ranked +=
                        Triple(
                            fold(matched).startsWith(folded),
                            boxAway(latitude, longitude, facts.south, facts.west, facts.north, facts.east),
                            SearchResult.OnTrail(facts, matched),
                        )
                }
            }
        db
            .rawQuery(
                "SELECT long_distance, long_distance_name, COUNT(*), " +
                    "MIN(min_lat), MIN(min_lon), MAX(max_lat), MAX(max_lon) " +
                    "FROM trails WHERE long_distance_name IS NOT NULL GROUP BY long_distance",
                null,
            ).use { rows ->
                while (rows.moveToNext()) {
                    val name = rows.getString(1)
                    val words = fold(name)
                    if (!words.startsWith(folded) && " $folded" !in " $words") continue
                    ranked +=
                        Triple(
                            words.startsWith(folded),
                            boxAway(
                                latitude,
                                longitude,
                                rows.getDouble(3),
                                rows.getDouble(4),
                                rows.getDouble(5),
                                rows.getDouble(6),
                            ),
                            SearchResult.LongDistance(rows.getLong(0), name, rows.getInt(2), name),
                        )
                }
            }
        return ranked
            .sortedWith(compareBy({ !it.first }, { it.second }))
            .take(limit)
            .map { it.third }
    }

    private fun trailFacts(rows: Cursor): TrailFacts {
        fun text(column: Int) = if (rows.isNull(column)) null else rows.getString(column)
        return TrailFacts(
            id = rows.getLong(0),
            name = text(1),
            namePl = text(2),
            ref = text(3),
            colour = TrailColour.of(rows.getString(4)),
            symbol = text(5),
            from = text(6),
            to = text(7),
            longDistanceId = if (rows.isNull(8)) null else rows.getLong(8),
            longDistanceName = text(9),
            startLatitude = rows.getDouble(10),
            startLongitude = rows.getDouble(11),
            endLatitude = rows.getDouble(12),
            endLongitude = rows.getDouble(13),
            metres = rows.getDouble(14),
            ascent = rows.getDouble(15),
            descent = rows.getDouble(16),
            gaps = rows.getInt(17),
            south = rows.getDouble(18),
            west = rows.getDouble(19),
            north = rows.getDouble(20),
            east = rows.getDouble(21),
        )
    }

    override fun sections(longDistance: Long): List<Long> =
        db
            .rawQuery("SELECT id FROM trails WHERE long_distance = ? ORDER BY id", arrayOf(longDistance.toString()))
            .use { rows -> buildList { while (rows.moveToNext()) add(rows.getLong(0)) } }

    override fun bikeRoute(id: Long): BikeRouteFacts? =
        db.rawQuery("SELECT $BIKE_ROUTE_COLUMNS FROM bike_routes WHERE id = ?", arrayOf(id.toString())).use { rows ->
            if (rows.moveToFirst()) bikeRouteFacts(rows) else null
        }

    private fun bikeRouteFacts(rows: Cursor): BikeRouteFacts {
        fun text(column: Int) = if (rows.isNull(column)) null else rows.getString(column)

        fun whole(column: Int) = if (rows.isNull(column)) null else rows.getInt(column)
        return BikeRouteFacts(
            id = rows.getLong(0),
            name = text(1),
            namePl = text(2),
            ref = text(3),
            colour = TrailColour.of(rows.getString(4)),
            from = text(5),
            to = text(6),
            longDistanceId = if (rows.isNull(7)) null else rows.getLong(7),
            longDistanceName = text(8),
            startLatitude = rows.getDouble(9),
            startLongitude = rows.getDouble(10),
            endLatitude = rows.getDouble(11),
            endLongitude = rows.getDouble(12),
            metres = rows.getDouble(13),
            ascent = rows.getDouble(14),
            descent = rows.getDouble(15),
            gaps = rows.getInt(16),
            south = rows.getDouble(17),
            west = rows.getDouble(18),
            north = rows.getDouble(19),
            east = rows.getDouble(20),
            kind = BikeRouteKind.of(rows.getString(21)),
            oneWay = rows.getInt(22) != 0,
            mtbScale = whole(23),
            mtbScaleUphill = whole(24),
        )
    }

    override fun bikeRouteWhole(parent: Long): List<Long> =
        db
            .rawQuery(
                "SELECT id FROM bike_routes WHERE long_distance = ?1 OR id = ?1 ORDER BY id",
                arrayOf(parent.toString()),
            ).use { rows -> buildList { while (rows.moveToNext()) add(rows.getLong(0)) } }

    private companion object {
        const val BIKE_ROUTE_COLUMNS =
            "id, name, name_pl, ref, colour, from_place, to_place, long_distance, long_distance_name, " +
                "start_lat, start_lon, end_lat, end_lon, length, ascent, descent, gaps, " +
                "min_lat, min_lon, max_lat, max_lon, kind, one_way, mtb_scale, mtb_scale_uphill"
        const val TRAIL_COLUMNS =
            "id, name, name_pl, ref, colour, symbol, from_place, to_place, long_distance, long_distance_name, " +
                "start_lat, start_lon, end_lat, end_lon, length, ascent, descent, gaps, min_lat, min_lon, max_lat, max_lon"
        const val TRAIL_COLUMN_COUNT = 22
        const val NAMING_METRES = 500.0
        const val METRES_PER_DEGREE = 111_320.0
        val LANDMARKS = setOf(PlaceKind.PEAK, PlaceKind.PASS, PlaceKind.HUT)
    }
}

/**
 * Squared degrees from a point to a bounding box, nought inside it, a degree of longitude shrunk by
 * the cosine of the point's latitude: the distance search ranks by (docs/phase-6c.md § 4).
 */
fun boxAway(
    latitude: Double,
    longitude: Double,
    south: Double,
    west: Double,
    north: Double,
    east: Double,
): Double {
    val dLat = maxOf(south - latitude, 0.0, latitude - north)
    val dLon = maxOf(west - longitude, 0.0, longitude - east)
    return dLat * dLat + dLon * dLon * cos(Math.toRadians(latitude)).let { it * it }
}

/**
 * The assets' `places.sqlite`, copied out once per install of an APK, since SQLite cannot open a file
 * inside one; null when this build carries none or the copy fails. The copy is in `noBackupFilesDir`,
 * which the next install rewrites rather than restores, and is named for the install it was made by,
 * since the wall clock can run backwards between one install and the next.
 */
fun copiedPlaces(context: Context): File? {
    val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    val copy = File(context.noBackupFilesDir, "places-$installed.sqlite")
    if (copy.exists()) return copy
    context.noBackupFilesDir.listFiles { file -> file.name.startsWith("places") }?.forEach { it.delete() }
    val partial = File(context.noBackupFilesDir, "${copy.name}.part")
    return try {
        context.assets.open(PLACES).use { input -> partial.outputStream().use { input.copyTo(it) } }
        if (partial.renameTo(copy)) copy else null
    } catch (_: IOException) {
        null
    } finally {
        partial.delete()
    }
}

/** Null when [file] does not open as a database, so search shows its placeholder rather than crash. */
fun openPlaces(file: File): PlaceIndex? =
    try {
        PlaceIndex(file)
    } catch (_: SQLiteException) {
        null
    }

const val PLACES = "places.sqlite"
