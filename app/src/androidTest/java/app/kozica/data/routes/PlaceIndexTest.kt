package app.kozica.data.routes

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** docs/phase-5.md § 4's search order, instrumented because FTS4 is the phone's SQLite. */
@RunWith(AndroidJUnit4::class)
class PlaceIndexTest {
    private lateinit var file: File
    private lateinit var index: PlaceIndex

    @Before
    fun setUp() {
        file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "places-test.sqlite")
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            // scripts/build-network.py's write_places.
            db.execSQL(
                "CREATE TABLE places (id INTEGER PRIMARY KEY, kind TEXT NOT NULL, lat REAL NOT NULL, " +
                    "lon REAL NOT NULL, region TEXT NOT NULL, name TEXT NOT NULL)",
            )
            db.execSQL(
                "CREATE VIRTUAL TABLE place_names USING fts4(place, name, folded, notindexed=place, notindexed=name)",
            )
            place(db, 1, "clearing", 49.540, 20.110, "Polana Długa")
            place(db, 2, "clearing", 49.700, 20.110, "Polana Gabryśkowa")
            place(db, 3, "hamlet", 49.545, 20.115, "Wielka Polana")
            place(db, 4, "peak", 49.5431, 20.1114, "Turbacz", "Polana pod Turbaczem")
            place(db, 5, "village", 49.5440, 20.1114, "Koninki")
            place(db, 6, "peak", 49.300, 20.100, "Polanka")
            db.execSQL(
                "CREATE TABLE trails (id INTEGER PRIMARY KEY, name TEXT, name_pl TEXT, ref TEXT, " +
                    "colour TEXT NOT NULL, " +
                    "symbol TEXT, from_place TEXT, to_place TEXT, long_distance INTEGER, long_distance_name TEXT, " +
                    "start_lat REAL NOT NULL, start_lon REAL NOT NULL, end_lat REAL NOT NULL, end_lon REAL NOT NULL, " +
                    "length REAL NOT NULL, ascent REAL NOT NULL, descent REAL NOT NULL, gaps INTEGER NOT NULL, " +
                    "min_lat REAL NOT NULL, min_lon REAL NOT NULL, max_lat REAL NOT NULL, max_lon REAL NOT NULL)",
            )
            db.execSQL(
                "CREATE VIRTUAL TABLE trail_names USING fts4(trail, name, folded, notindexed=trail, notindexed=name)",
            )
            trail(db, 100, "Polanami na Turbacz", null, null, 49.50, 20.05, 49.60, 20.15)
            trail(db, 101, "Przez Polany", null, null, 49.80, 20.50, 49.90, 20.60)
            trail(db, 102, "Szlak nad Popradem", 900, "Główny Szlak Beskidzki", 49.30, 20.60, 49.40, 20.70)
            db.execSQL(
                "CREATE TABLE bike_routes (id INTEGER PRIMARY KEY, name TEXT, name_pl TEXT, ref TEXT, " +
                    "colour TEXT NOT NULL, " +
                    "from_place TEXT, to_place TEXT, long_distance INTEGER, long_distance_name TEXT, " +
                    "start_lat REAL NOT NULL, start_lon REAL NOT NULL, end_lat REAL NOT NULL, end_lon REAL NOT NULL, " +
                    "length REAL NOT NULL, ascent REAL NOT NULL, descent REAL NOT NULL, gaps INTEGER NOT NULL, " +
                    "min_lat REAL NOT NULL, min_lon REAL NOT NULL, max_lat REAL NOT NULL, max_lon REAL NOT NULL, " +
                    "kind TEXT NOT NULL, network TEXT, one_way INTEGER NOT NULL, mtb_scale INTEGER, " +
                    "mtb_scale_uphill INTEGER)",
            )
            db.execSQL(
                "INSERT INTO bike_routes VALUES (7, 'Twister', 'Skręt', 'T1', 'black', 'Dół', 'Góra', 900, 'Enduro', " +
                    "49.1, 19.1, 49.2, 19.2, 3000.5, 10.5, 400.5, 1, 49.05, 19.05, 49.25, 19.25, 'mtb', 'lcn', 1, 3, 2)",
            )
            db.execSQL(
                "INSERT INTO bike_routes VALUES (900, NULL, NULL, NULL, 'other', NULL, NULL, NULL, NULL, " +
                    "49.1, 19.1, 49.2, 19.2, 1.0, 0.0, 0.0, 0, 49.1, 19.1, 49.2, 19.2, 'touring', NULL, 0, NULL, NULL)",
            )
            db.execSQL(
                "INSERT INTO bike_routes VALUES (8, NULL, NULL, NULL, 'red', NULL, NULL, 900, NULL, " +
                    "49.1, 19.1, 49.2, 19.2, 1.0, 0.0, 0.0, 0, 49.1, 19.1, 49.2, 19.2, 'touring', NULL, 0, NULL, NULL)",
            )
        }
        index = PlaceIndex(file)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun aBikeRoutesRowIsReadColumnForColumn() {
        val expected =
            BikeRouteFacts(
                id = 7,
                name = "Twister",
                namePl = "Skręt",
                ref = "T1",
                colour = TrailColour.BLACK,
                from = "Dół",
                to = "Góra",
                longDistanceId = 900,
                longDistanceName = "Enduro",
                startLatitude = 49.1,
                startLongitude = 19.1,
                endLatitude = 49.2,
                endLongitude = 19.2,
                metres = 3000.5,
                ascent = 10.5,
                descent = 400.5,
                gaps = 1,
                south = 49.05,
                west = 19.05,
                north = 49.25,
                east = 19.25,
                kind = BikeRouteKind.MTB,
                oneWay = true,
                mtbScale = 3,
                mtbScaleUphill = 2,
            )
        assertEquals(expected, index.bikeRoute(7))
        val bare = index.bikeRoute(900)!!
        assertEquals(TrailColour.OTHER to BikeRouteKind.TOURING, bare.colour to bare.kind)
        assertEquals(null, bare.mtbScale)
        assertEquals(false, bare.oneWay)
        assertEquals(null, index.bikeRoute(1))
    }

    @Test
    fun theWholeOfAParentIsItsSectionsAndItsOwnRow() {
        assertEquals(listOf(7L, 8L, 900L), index.bikeRouteWhole(900))
        assertEquals(listOf(7L), index.bikeRouteWhole(7))
    }

    @Test
    fun startingWithBeforeHoldingEachNearestFirstAndOnePerPlace() {
        val found = index.search("polana", 49.5431, 20.1114)

        assertEquals(
            listOf("Turbacz", "Polana Długa", "Polana Gabryśkowa", "Wielka Polana"),
            found.map { it.name },
        )
        assertEquals("Polana pod Turbaczem", found.first().matched)
    }

    @Test
    fun trailsRankWithPlacesByTheirBoundingBox() {
        val found = index.searchAll("polan", 49.5431, 20.1114)

        assertEquals(
            listOf(
                "place Turbacz",
                "trail 100",
                "place Polana Długa",
                "place Polana Gabryśkowa",
                "place Polanka",
                "place Wielka Polana",
                "trail 101",
            ),
            found.map {
                when (it) {
                    is SearchResult.AtPlace -> "place ${it.place.name}"
                    is SearchResult.OnTrail -> "trail ${it.facts.id}"
                    is SearchResult.LongDistance -> "long-distance ${it.id}"
                }
            },
        )
        assertEquals(2.5, (found[1] as SearchResult.OnTrail).facts.ascent, 0.0)
    }

    @Test
    fun aLongDistanceTrailIsFoundByItsOwnName() {
        val found = index.searchAll("glowny szl", 49.5, 20.1).single() as SearchResult.LongDistance
        assertEquals(900L, found.id)
        assertEquals(1, found.sections)
        assertEquals(listOf(102L), index.sections(900))
    }

    @Test
    fun theNearestTrailsAreReadBeforeTheLimit() {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            for (id in 200L..290L) trail(db, id, "Szlak daleki $id", null, null, 54.0, 14.0, 54.1, 14.1)
            trail(db, 300, "Na Szlak bliski", null, null, 49.54, 20.11, 49.55, 20.12)
            db.execSQL(
                "INSERT INTO trail_names (trail, name, folded) VALUES (?, ?, ?)",
                arrayOf<Any>(300, "Szlak bliski", fold("Szlak bliski")),
            )
        }

        val nearest = index.searchAll("szlak", 49.5431, 20.1114).first() as SearchResult.OnTrail

        assertEquals(300L, nearest.facts.id)
        assertEquals("Szlak bliski", nearest.matched)
    }

    @Test
    fun accentsAndCaseDoNotMatter() {
        assertEquals(listOf("Polana Długa"), index.search("POLANA DLU", 49.5, 20.1).map { it.name })
    }

    @Test
    fun aPeakNamesASpotBeforeANearerVillage() {
        assertEquals("Turbacz", index.nearest(49.5439, 20.1114)?.name)
        assertEquals(null, index.nearest(49.0, 19.0))
    }

    private fun trail(
        db: SQLiteDatabase,
        id: Long,
        name: String,
        longDistance: Long?,
        longDistanceName: String?,
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ) {
        db.execSQL(
            "INSERT INTO trails VALUES (?, ?, NULL, NULL, 'green', NULL, NULL, NULL, ?, ?, " +
                "?, ?, ?, ?, 1000.0, 2.5, 1.5, 0, ?, ?, ?, ?)",
            arrayOf<Any?>(id, name, longDistance, longDistanceName, south, west, north, east, south, west, north, east),
        )
        db.execSQL("INSERT INTO trail_names (trail, name, folded) VALUES (?, ?, ?)", arrayOf<Any>(id, name, fold(name)))
    }

    private fun place(
        db: SQLiteDatabase,
        id: Int,
        kind: String,
        latitude: Double,
        longitude: Double,
        vararg names: String,
    ) {
        db.execSQL(
            "INSERT INTO places VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(id, kind, latitude, longitude, "gorce_pieniny", names[0]),
        )
        for (name in names) {
            db.execSQL(
                "INSERT INTO place_names (place, name, folded) VALUES (?, ?, ?)",
                arrayOf<Any>(id, name, fold(name)),
            )
        }
    }
}
