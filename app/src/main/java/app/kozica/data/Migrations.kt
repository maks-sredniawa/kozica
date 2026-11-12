package app.kozica.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Saved Routes replace the dormant `items` table, which no release ever wrote a row to: its screen
 * went in phase 0 (docs/phase-5.md § 5). Transcribed from `schemas/…/2.json`.
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `items`")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `routes` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `route_points` (`routeId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                    "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `label` TEXT, " +
                    "PRIMARY KEY(`routeId`, `position`), FOREIGN KEY(`routeId`) REFERENCES `routes`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

/**
 * Recorded Hikes (docs/phase-6.md § 2): three new tables, nothing existing touched. Transcribed from
 * `schemas/…/3.json`.
 */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `hikes` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `track` BLOB, `note` TEXT, " +
                    "`metres` REAL NOT NULL, `movingSeconds` INTEGER NOT NULL, `ascentMetres` REAL, " +
                    "`descentMetres` REAL, `ascentFromGps` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `track_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`hikeId` TEXT NOT NULL, `stretch` INTEGER NOT NULL, `time` INTEGER NOT NULL, " +
                    "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `altitude` REAL, " +
                    "FOREIGN KEY(`hikeId`) REFERENCES `hikes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_points_hikeId` ON `track_points` (`hikeId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `hike_photos` (`id` TEXT NOT NULL, `hikeId` TEXT NOT NULL, " +
                    "`photoPath` TEXT NOT NULL, `thumbnailPath` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, " +
                    "`takenAt` INTEGER, `latitude` REAL, `longitude` REAL, `caption` TEXT, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`hikeId`) REFERENCES `hikes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_hike_photos_hikeId` ON `hike_photos` (`hikeId`)")
        }
    }

/**
 * A saved Route keeps its Mode and chip (docs/phase-7b.md § 4): two columns, every Route before them a
 * walk under All. Transcribed from `schemas/…/4.json`.
 */
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `routes` ADD COLUMN `mode` TEXT NOT NULL DEFAULT 'HIKING'")
            db.execSQL("ALTER TABLE `routes` ADD COLUMN `bikeRouteFilter` TEXT NOT NULL DEFAULT 'ALL'")
        }
    }

/**
 * Every migration this build can run, in the order they were written.
 *
 * **Registering it here is the half that is easy to forget**, and forgetting it fails silently: the
 * migration compiles, its test passes (a test opens the database directly), and Room simply never
 * runs it on a real upgrade. `scripts/schema-gate.py` checks this array for exactly that reason.
 *
 * Kotlin note: `arrayOf()` rather than `listOf()` because Room's `addMigrations` takes a vararg,
 * and `*APP_MIGRATIONS` spreads an array into one. `SchemaGate` converts it to a list of integer
 * pairs so the reachability rule can be a pure function.
 */
val APP_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

/*
 * ---------------------------------------------------------------------------------------------
 * Writing the next one
 * ---------------------------------------------------------------------------------------------
 *
 *     val MIGRATION_1_2 =
 *         object : Migration(1, 2) {
 *             override fun migrate(db: SupportSQLiteDatabase) {
 *                 db.execSQL("ALTER TABLE `routes` ADD COLUMN `favourite` INTEGER NOT NULL DEFAULT 0")
 *             }
 *         }
 *
 * **Transcribe the SQL from `schemas/<n>.json`, do not paraphrase the entities.** Room compares the
 * migrated database against that JSON and fails on any difference — a missing index, a column
 * declared TEXT where the entity says INTEGER, a foreign key with a different ON DELETE. Copy the
 * `createSql` out of the exported schema; typing it from the Kotlin is how you get a migration that
 * passes review and fails on a device.
 *
 * The two that are cheap
 * ----------------------
 * CREATE TABLE and ALTER TABLE … ADD COLUMN touch nothing that already exists. No existing row is
 * read or rewritten, so no user's data is at risk. Most migrations are one of these.
 *
 * The one that is not
 * -------------------
 * **SQLite at minSdk 26 has no ALTER TABLE … DROP COLUMN.** Removing or retyping a column means
 * rebuilding the table: create the new shape under a temporary name, INSERT INTO … SELECT the rows
 * across, DROP the original, ALTER TABLE … RENAME. Three things go wrong there, and all three have
 * shipped in real apps:
 *
 *   - **The DROP cascades.** Any table with a foreign key onto the one being rebuilt loses its rows
 *     when the parent is dropped, and `runMigrationsAndValidate` compares *shapes* — it cannot see
 *     that a child table is now empty. Stage those rows into a temporary table and put them back,
 *     and have the test count **rows**, not columns.
 *   - **PRAGMA foreign_keys is off inside a Room migration** and on outside it, so the behaviour you
 *     test by hand in a SQLite client is not the behaviour you get.
 *   - **Indices are not carried across a rename.** Re-create every one the schema JSON lists.
 *
 * Batch a phase's changes into one version
 * ----------------------------------------
 * If three features landing this month all change the schema, one migration that does all three
 * beats three that each prove a shape no shipped build ever held. The version climbs when you
 * release, not when you commit.
 */
