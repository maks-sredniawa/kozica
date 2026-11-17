package app.kozica.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

/** docs/phase-7c.md § Tests. Instrumented, because the foreign key that keeps a deleted Bike's Rides is SQLite's. */
@RunWith(AndroidJUnit4::class)
class BikeRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var preferencesFile: File
    private lateinit var preferences: AppPreferences
    private lateinit var bikes: BikeRepository
    private val deleted = mutableListOf<String>()
    private var persisted = 0

    private val start = Instant.parse("2026-09-12T06:30:00Z")

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        preferencesFile = File(context.cacheDir, "bike-test-${UUID.randomUUID()}.preferences_pb")
        preferences = AppPreferences(PreferenceDataStoreFactory.create { preferencesFile })
        bikes =
            BikeRepository(
                database.bikeDao(),
                preferences,
                persistPhoto = { "bikes/${++persisted}.jpg" },
                deleteMedia = { deleted += it },
            )
    }

    @After
    fun close() {
        database.close()
        preferencesFile.delete()
    }

    private suspend fun bikeNamed(name: String): BikeEntity =
        bikes.observeAll().first().map { it.bike }.single {
            it.name ==
                name
        }

    private suspend fun outing(
        mode: Mode,
        bikeId: String?,
        ended: Boolean = true,
    ): String {
        val id = UUID.randomUUID().toString()
        database.outingDao().insert(
            OutingEntity(
                id = id,
                name = "",
                startedAt = start,
                endedAt = if (ended) start.plusSeconds(3_600) else null,
                mode = mode,
                bikeId = bikeId,
            ),
        )
        return id
    }

    @Test
    fun aNameIsTakenIgnoringCaseAndSurroundingSpacesInPolishToo() =
        runTest {
            assertEquals(BikeNaming.Saved, bikes.add("  Łukasz ", start))
            assertEquals(BikeNaming.Taken, bikes.add("łukasz"))
            assertEquals(BikeNaming.Taken, bikes.add(" ŁUKASZ"))
            assertEquals(BikeNaming.Blank, bikes.add("   "))
            assertEquals(BikeNaming.Saved, bikes.add("Gravel", start.plusSeconds(1)))

            val gravel = bikeNamed("Gravel")
            assertEquals(BikeNaming.Taken, bikes.rename(gravel.id, "łukasz "))
            assertEquals(BikeNaming.Saved, bikes.rename(bikeNamed("Łukasz").id, "łukasz"))
            assertEquals(BikeNaming.Saved, bikes.rename(gravel.id, "Szosa"))

            assertEquals(listOf("łukasz", "Szosa"), bikes.observeAll().first().map { it.bike.name })
        }

    @Test
    fun deletingABikeKeepsItsRidesWithNoBikeAndUnsetsTheCurrentBike() =
        runTest {
            bikes.add("Gravel")
            bikes.add("Szosa")
            val gravel = bikeNamed("Gravel")
            val ride = outing(Mode.CYCLING, gravel.id)
            preferences.setCurrentBike(gravel.id)

            bikes.delete(gravel.id)

            val kept = database.outingDao().get(ride)!!
            assertEquals(Mode.CYCLING, kept.mode)
            assertNull(kept.bikeId)
            assertNull(preferences.currentBikeId.first())
            assertEquals(listOf("Szosa"), bikes.observeAll().first().map { it.bike.name })
        }

    @Test
    fun aBikeAddedWhileNoneIsCurrentBecomesTheCurrentOne() =
        runTest {
            bikes.add("Gravel", start)
            val gravel = bikeNamed("Gravel").id
            assertEquals(gravel, preferences.currentBikeId.first())

            bikes.add("Szosa", start.plusSeconds(1))
            assertEquals(gravel, preferences.currentBikeId.first())

            bikes.delete(gravel)
            assertNull(preferences.currentBikeId.first())
            bikes.add("MTB", start.plusSeconds(2))
            assertEquals(bikeNamed("MTB").id, preferences.currentBikeId.first())
        }

    @Test
    fun deletingAnotherBikeLeavesTheCurrentOne() =
        runTest {
            bikes.add("Gravel")
            bikes.add("Szosa")
            preferences.setCurrentBike(bikeNamed("Szosa").id)

            bikes.delete(bikeNamed("Gravel").id)

            assertEquals(bikeNamed("Szosa").id, preferences.currentBikeId.first())
        }

    @Test
    fun aHikeKeepingABikeIdIsNotAmongThatBikesRides() =
        runTest {
            bikes.add("Gravel")
            val gravel = bikeNamed("Gravel").id
            outing(Mode.CYCLING, gravel)
            outing(Mode.CYCLING, gravel)
            outing(Mode.HIKING, gravel)
            outing(Mode.CYCLING, gravel, ended = false)
            outing(Mode.CYCLING, null)

            assertEquals(
                2,
                bikes
                    .observeAll()
                    .first()
                    .single()
                    .rides,
            )
        }

    @Test
    fun aPhotoReplacedRemovedOrDeletedWithItsBikeTakesItsFile() =
        runTest {
            bikes.add("Gravel")
            val gravel = bikeNamed("Gravel").id
            val source = Uri.parse("content://picked/1")

            bikes.setPhoto(gravel, source)
            assertEquals("bikes/1.jpg", bikeNamed("Gravel").thumbnailPath)
            bikes.setPhoto(gravel, source)
            assertEquals(listOf("bikes/1.jpg"), deleted)
            assertEquals("bikes/2.jpg", bikeNamed("Gravel").photoPath)

            bikes.removePhoto(gravel)
            assertNull(bikeNamed("Gravel").photoPath)
            assertEquals(listOf("bikes/1.jpg", "bikes/2.jpg"), deleted)

            bikes.setPhoto(gravel, source)
            bikes.delete(gravel)
            assertEquals(listOf("bikes/1.jpg", "bikes/2.jpg", "bikes/3.jpg"), deleted)
        }
}
