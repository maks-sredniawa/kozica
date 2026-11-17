package app.kozica.data

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrations against the exported schemas, reading **rows** back: `runMigrationsAndValidate` compares
 * shapes only. This walks past the launch gate; `SchemaGateTest` is the other half.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    fun theExportedSchemaIsReadableOnTheDevice() {
        // If this fails, it is almost always the assets wiring in app/build.gradle.kts:
        // `variant.androidTest?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")`.
        // Room's exported JSON has to be *inside the test APK*, not merely committed to the repo.
        helper.createDatabase(TEST_DB, 1).close()
    }

    @Test
    fun migrate1To2KeepsASavedRouteAndItsPointsInOrder() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO items (id, title, createdAt) VALUES ('a', 'dormant', 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
        db.execSQL("INSERT INTO routes (id, name, createdAt, updatedAt) VALUES ('r', 'Kuźnice – Morskie Oko', 1, 1)")
        db.execSQL("INSERT INTO route_points VALUES ('r', 0, 49.2702, 19.9808, 'Kuźnice')")
        db.execSQL("INSERT INTO route_points VALUES ('r', 1, 49.2191, 20.0164, 'Zawrat')")
        db.execSQL("INSERT INTO route_points VALUES ('r', 2, 49.1972, 20.0700, NULL)")

        db.query("SELECT label FROM route_points WHERE routeId = 'r' ORDER BY position").use {
            val read = mutableListOf<String?>()
            while (it.moveToNext()) read += if (it.isNull(0)) null else it.getString(0)
            assertEquals(listOf("Kuźnice", "Zawrat", null), read)
        }
        db.query("SELECT name FROM routes WHERE id = 'r'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Kuźnice – Morskie Oko", it.getString(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE name = 'items'").use { assertFalse(it.moveToFirst()) }
    }

    @Test
    fun migrate2To3KeepsASavedRouteAndTakesAHike() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL("INSERT INTO routes (id, name, createdAt, updatedAt) VALUES ('r', 'Kuźnice – Morskie Oko', 1, 1)")
            execSQL("INSERT INTO route_points VALUES ('r', 0, 49.2702, 19.9808, 'Kuźnice')")
            execSQL("INSERT INTO route_points VALUES ('r', 1, 49.1972, 20.0700, NULL)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)
        db.query("SELECT r.name, COUNT(p.position) FROM routes r JOIN route_points p ON p.routeId = r.id").use {
            assertTrue(it.moveToFirst())
            assertEquals("Kuźnice – Morskie Oko", it.getString(0))
            assertEquals(2, it.getInt(1))
        }
        db.execSQL(
            "INSERT INTO hikes (id, name, startedAt, metres, movingSeconds, ascentFromGps) VALUES ('h', '', 1, 0, 0, 0)",
        )
        db.execSQL(
            "INSERT INTO track_points (hikeId, stretch, time, latitude, longitude) VALUES ('h', 0, 2, 49.27, 19.98)",
        )
        db.query("SELECT COUNT(*) FROM track_points WHERE hikeId = 'h'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun migrate3To4KeepsEverySavedRouteAndItsPointsAsAWalkUnderAll() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL("INSERT INTO routes (id, name, createdAt, updatedAt) VALUES ('r', 'Kuźnice – Morskie Oko', 1, 2)")
            execSQL("INSERT INTO routes (id, name, createdAt, updatedAt) VALUES ('k', 'Kasprowy Wierch', 3, 4)")
            execSQL("INSERT INTO route_points VALUES ('r', 0, 49.2702, 19.9808, 'Kuźnice')")
            execSQL("INSERT INTO route_points VALUES ('r', 1, 49.2191, 20.0164, 'Zawrat')")
            execSQL("INSERT INTO route_points VALUES ('r', 2, 49.1972, 20.0700, NULL)")
            execSQL("INSERT INTO route_points VALUES ('k', 0, 49.2702, 19.9808, 'Kuźnice')")
            execSQL("INSERT INTO route_points VALUES ('k', 1, 49.2318, 19.9818, 'Kasprowy Wierch')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        db.query("SELECT id, name, createdAt, updatedAt, mode, bikeRouteFilter FROM routes ORDER BY id").use {
            val read = mutableListOf<List<Any>>()
            while (it.moveToNext()) {
                val dates = listOf(it.getLong(2), it.getLong(3))
                read += listOf(it.getString(0), it.getString(1)) + dates + listOf(it.getString(4), it.getString(5))
            }
            assertEquals(
                listOf(
                    listOf("k", "Kasprowy Wierch", 3L, 4L, "HIKING", "ALL"),
                    listOf("r", "Kuźnice – Morskie Oko", 1L, 2L, "HIKING", "ALL"),
                ),
                read,
            )
        }
        db.query("SELECT routeId, position, latitude, longitude, label FROM route_points ORDER BY 1, 2").use {
            val read = mutableListOf<List<Any?>>()
            while (it.moveToNext()) {
                read += listOf(it.getString(0), it.getInt(1), it.getDouble(2), it.getDouble(3), it.getStringOrNull(4))
            }
            assertEquals(
                listOf(
                    listOf("k", 0, 49.2702, 19.9808, "Kuźnice"),
                    listOf("k", 1, 49.2318, 19.9818, "Kasprowy Wierch"),
                    listOf("r", 0, 49.2702, 19.9808, "Kuźnice"),
                    listOf("r", 1, 49.2191, 20.0164, "Zawrat"),
                    listOf("r", 2, 49.1972, 20.0700, null),
                ),
                read,
            )
        }
    }

    @Test
    fun migrate4To5KeepsAHikeWithItsPhotosAndASavedRideAndTakesABike() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                "INSERT INTO hikes (id, name, startedAt, endedAt, track, note, metres, movingSeconds, ascentMetres, " +
                    "descentMetres, ascentFromGps) VALUES ('h', 'Kasprowy Wierch', 1, 2, X'0102', 'Mgła', " +
                    "8400.5, 14400, 1020.0, 980.0, 0)",
            )
            execSQL(
                "INSERT INTO hike_photos VALUES ('p1', 'h', 'photos/a.jpg', 'thumbnails/a.jpg', 3, 4, 49.23, 19.98, 'Szczyt')",
            )
            execSQL(
                "INSERT INTO hike_photos VALUES ('p2', 'h', 'photos/b.jpg', 'thumbnails/b.jpg', 5, NULL, NULL, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO routes (id, name, createdAt, updatedAt, mode, bikeRouteFilter) " +
                    "VALUES ('r', 'Zakopane – Nowy Targ', 1, 2, 'CYCLING', 'TOURING')",
            )
            execSQL("INSERT INTO route_points VALUES ('r', 0, 49.2992, 19.9496, 'Zakopane')")
            execSQL("INSERT INTO route_points VALUES ('r', 1, 49.4774, 20.0322, 'Nowy Targ')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        db
            .query(
                "SELECT name, startedAt, endedAt, hex(track), note, metres, movingSeconds, ascentMetres, " +
                    "descentMetres, ascentFromGps, mode, bikeId FROM hikes WHERE id = 'h'",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(
                    listOf<Any?>(
                        "Kasprowy Wierch",
                        1L,
                        2L,
                        "0102",
                        "Mgła",
                        8400.5,
                        14400L,
                        1020.0,
                        980.0,
                        0,
                        "HIKING",
                        null,
                    ),
                    listOf(
                        it.getString(0),
                        it.getLong(1),
                        it.getLong(2),
                        it.getString(3),
                        it.getString(4),
                        it.getDouble(5),
                        it.getLong(6),
                        it.getDouble(7),
                        it.getDouble(8),
                        it.getInt(9),
                        it.getString(10),
                        it.getStringOrNull(11),
                    ),
                )
            }
        db.query("SELECT id, photoPath, thumbnailPath, caption FROM hike_photos WHERE hikeId = 'h' ORDER BY id").use {
            val read = mutableListOf<List<String?>>()
            while (it.moveToNext()) {
                read +=
                    listOf(it.getString(0), it.getString(1), it.getString(2), it.getStringOrNull(3))
            }
            assertEquals(
                listOf(
                    listOf("p1", "photos/a.jpg", "thumbnails/a.jpg", "Szczyt"),
                    listOf("p2", "photos/b.jpg", "thumbnails/b.jpg", null),
                ),
                read,
            )
        }
        db
            .query(
                "SELECT r.name, r.mode, r.bikeRouteFilter, COUNT(p.position) FROM routes r " +
                    "JOIN route_points p ON p.routeId = r.id",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(listOf("Zakopane – Nowy Targ", "CYCLING", "TOURING", "2"), (0..3).map(it::getString))
            }

        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL(
            "INSERT INTO bikes (id, name, photoPath, thumbnailPath, addedAt) VALUES ('b', 'Gravel', NULL, NULL, 6)",
        )
        db.execSQL("UPDATE hikes SET mode = 'CYCLING', bikeId = 'b' WHERE id = 'h'")
        db.execSQL("DELETE FROM bikes WHERE id = 'b'")
        db.query("SELECT mode, bikeId FROM hikes WHERE id = 'h'").use {
            assertTrue(it.moveToFirst())
            assertEquals("CYCLING", it.getString(0))
            assertTrue("a deleted Bike leaves its Ride with no Bike", it.isNull(1))
        }
    }

    private fun Cursor.getStringOrNull(column: Int): String? = if (isNull(column)) null else getString(column)

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
