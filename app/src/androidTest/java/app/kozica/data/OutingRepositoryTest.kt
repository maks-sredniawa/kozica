package app.kozica.data

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kozica.data.hikes.Height
import app.kozica.data.hikes.HeightSource
import app.kozica.data.hikes.TrackPoint
import app.kozica.data.hikes.decodeTrack
import app.kozica.media.PersistedPhoto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Instrumented, because Room generates real SQLite code and a JVM test has no SQLite. */
@RunWith(AndroidJUnit4::class)
class OutingRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var outings: OutingRepository
    private val deleted = mutableListOf<String>()
    private var capturedAt: Instant? = null
    private var whilePersisting: suspend () -> Unit = {}
    private var persisted = 0

    private val start = Instant.parse("2026-09-12T06:30:00Z")

    @Before
    fun open() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        // A metre of climb for every point, from the grid, so every total is easy to predict.
        outings =
            OutingRepository(
                database.outingDao(),
                heights = { track ->
                    track.flatten().mapIndexed { i, _ -> Height(1_000.0 + i * 10, HeightSource.GRID) }
                },
                deleteMedia = { deleted += it },
                persistPhoto = {
                    whilePersisting()
                    persisted++
                    PersistedPhoto("photos/$persisted.jpg", "thumbnails/$persisted.jpg", capturedAt)
                },
            )
    }

    @After
    fun close() = database.close()

    /** Ten fixes a minute apart, a pause, ten more; Record a minute before the first fix. */
    private suspend fun recorded(): String =
        recording().also {
            outings.finish(it, start.plusSeconds(3_600)) { _, _ -> "Kasprowy" }
        }

    private suspend fun recording(): String {
        val id = "hike"
        outings.start(id, start)
        for (stretch in 0..1) {
            for (i in 0 until 10) {
                val minute = 1L + stretch * 30 + i
                outings.addPoint(
                    id,
                    stretch,
                    TrackPoint(
                        start.toEpochMilli() + minute * 60_000,
                        49.2 + (stretch * 10 + i) * 0.001,
                        20.0,
                        null,
                    ),
                )
            }
        }
        return id
    }

    private fun minute(m: Long): Instant = start.plusSeconds(m * 60)

    @Test
    fun fillingClimbsReadsEveryEndedHikeWithoutOne() =
        runTest {
            val id = recorded()

            outings.fillClimbs()

            val outing = outings.get(id)!!
            assertEquals(190.0, outing.ascentMetres!!, 1e-9)
            assertFalse(outing.ascentFromGps)
            assertEquals(listOf(id), outings.observeEnded().first().map { it.id })
        }

    @Test
    fun aHeightReadThatFailsLeavesTheAscentUnreadForTheNextFill() =
        runTest {
            val id = recorded()
            val failing = OutingRepository(database.outingDao(), heights = { error("not a 512 px tile") })

            failing.fillClimb(id)
            failing.trim(id, first = 3, last = 19)

            assertNull(outings.get(id)!!.ascentMetres)
            outings.fillClimbs()
            assertEquals(160.0, outings.get(id)!!.ascentMetres!!, 1e-9)
        }

    @Test
    fun aFillThatFinishesAfterATrimDoesNotPutBackTheUntrimmedAscent() =
        runTest {
            val id = recorded()

            outings.trim(id, first = 3, last = 19)
            database.outingDao().setClimb(id, ascent = 190.0, descent = 0.0, fromGps = false)

            assertEquals(160.0, outings.get(id)!!.ascentMetres!!, 1e-9)
        }

    @Test
    fun aTrimKeepsItsSpanRecomputesTheTotalsAndUnpinsThePhotosOutsideIt() =
        runTest {
            val id = recorded()
            val dao = database.outingDao()
            val before = outings.get(id)!!
            val inside = photo(id, "in", minute(5))
            val outside = photo(id, "out", minute(2))
            database.outingDao().insertPhoto(inside)
            database.outingDao().insertPhoto(outside)

            outings.trim(id, first = 3, last = 19)

            val after = outings.get(id)!!
            val track = decodeTrack(after.track!!)
            assertEquals(listOf(7, 10), track.map { it.size })
            assertEquals(minute(4), after.startedAt)
            assertEquals(before.endedAt, after.endedAt)
            assertTrue(after.metres < before.metres)
            assertEquals(160.0, after.ascentMetres!!, 1e-9)
            val photos = dao.photos(id).associateBy { it.id }
            assertEquals(inside.latitude, photos.getValue("in").latitude)
            assertNull(photos.getValue("out").latitude)
            assertNull(photos.getValue("out").longitude)
        }

    @Test
    fun deletingAHikeDeletesItsPhotosRowsAndFiles() =
        runTest {
            val id = recorded()
            database.outingDao().insertPhoto(photo(id, "p", minute(5)))

            outings.delete(id)

            assertNull(outings.get(id))
            assertEquals(emptyList<OutingPhotoEntity>(), database.outingDao().photos(id))
            assertEquals(listOf("photos/p.jpg", "thumbnails/p.jpg"), deleted)
        }

    @Test
    fun aPickedPhotoIsPinnedWhereTheTrackWasWhenItWasTaken() =
        runTest {
            val id = recorded()
            val shot = Uri.parse("content://picker/1")

            capturedAt = minute(5)
            outings.addPhoto(id, shot)
            capturedAt = minute(20)
            outings.addPhoto(id, shot)
            capturedAt = null
            outings.addPhoto(id, shot)

            val photos = outings.observePhotos(id).first()
            assertEquals(listOf(minute(5), minute(20), null), photos.map { it.takenAt })
            assertEquals(49.204, photos[0].latitude!!, 1e-9)
            assertEquals(49.209, photos[1].latitude!!, 1e-9)
            assertNull(photos[2].latitude)
            assertEquals("thumbnails/1.jpg", photos[0].thumbnailPath)
        }

    @Test
    fun theCameraPinsAtTheLastFixWhenItsButtonWasPressed() =
        runTest {
            val id = recording()

            outings.addPhoto(id, Uri.parse("content://camera/1"), pressedAt = minute(5).plusSeconds(50))

            val photo = outings.observePhotos(id).first().single()
            assertEquals(49.204, photo.latitude!!, 1e-9)
            assertEquals(minute(5).plusSeconds(50), photo.takenAt)
        }

    @Test
    fun aHikeDeletedWhileItsPhotoIsWrittenTakesTheFilesWithIt() =
        runTest {
            val id = recorded()
            whilePersisting = { outings.delete(id) }

            outings.addPhoto(id, Uri.parse("content://picker/1"))

            assertEquals(listOf("photos/1.jpg", "thumbnails/1.jpg"), deleted)
        }

    @Test
    fun aPhotoIsCaptionedUnpinnedAndDeletedWithItsFiles() =
        runTest {
            val id = recorded()
            capturedAt = minute(5)
            outings.addPhoto(id, Uri.parse("content://picker/1"))
            val added = outings.observePhotos(id).first().single()

            outings.setCaption(added.id, "  Hala Gąsienicowa ")
            outings.unpin(added.id)
            val changed = outings.observePhotos(id).first().single()
            assertEquals("Hala Gąsienicowa", changed.caption)
            assertNull(changed.latitude)
            outings.setCaption(added.id, " ")
            assertNull(
                outings
                    .observePhotos(id)
                    .first()
                    .single()
                    .caption,
            )

            outings.deletePhoto(added.id)

            assertEquals(emptyList<OutingPhotoEntity>(), outings.observePhotos(id).first())
            assertEquals(listOf("photos/1.jpg", "thumbnails/1.jpg"), deleted)
        }

    private suspend fun bike(name: String): String =
        BikeEntity(name = name, addedAt = start).also { database.bikeDao().insert(it) }.id

    private suspend fun ride(
        id: String,
        bikeId: String?,
    ) {
        outings.start(id, start, Mode.CYCLING, bikeId)
        outings.addPoint(id, 0, TrackPoint(start.toEpochMilli() + 60_000, 49.2, 20.0, null))
        outings.finish(id, start.plusSeconds(3_600)) { _, _ -> id }
    }

    @Test
    fun aRideIsRecordedOnItsBikeAndAHikeKeepsNone() =
        runTest {
            val gravel = bike("Gravel")
            ride("ride", gravel)
            outings.start("walk", start, Mode.HIKING, gravel)

            assertEquals(Mode.CYCLING, outings.get("ride")!!.mode)
            assertEquals(gravel, outings.get("ride")!!.bikeId)
            assertEquals(Mode.HIKING, outings.get("walk")!!.mode)
            assertNull(outings.get("walk")!!.bikeId)
        }

    @Test
    fun aRideStartedOnABikeDeletedMeanwhileHasNone() =
        runTest {
            ride("ride", "gone")

            assertEquals(Mode.CYCLING, outings.get("ride")!!.mode)
            assertNull(outings.get("ride")!!.bikeId)
        }

    @Test
    fun aRideMadeAHikeKeepsItsBikeAndGetsItBackWhenMadeARideAgain() =
        runTest {
            val gravel = bike("Gravel")
            val road = bike("Road")
            ride("ride", gravel)

            outings.setMode("ride", Mode.HIKING, road)
            assertEquals(Mode.HIKING, outings.get("ride")!!.mode)
            assertEquals(gravel, outings.get("ride")!!.bikeId)
            val asHike = outings.observeEnded().first().single()
            assertEquals("Gravel", asHike.bikeName)
            assertNull(asHike.rideBikeName)

            outings.setMode("ride", Mode.CYCLING, road)
            assertEquals(Mode.CYCLING, outings.get("ride")!!.mode)
            assertEquals(gravel, outings.get("ride")!!.bikeId)
            assertEquals(
                "Gravel",
                outings
                    .observeEnded()
                    .first()
                    .single()
                    .rideBikeName,
            )
        }

    @Test
    fun aHikeMadeARideGetsTheCurrentBike() =
        runTest {
            val gravel = bike("Gravel")
            val id = recorded()

            outings.setMode(id, Mode.CYCLING, gravel)

            assertEquals(Mode.CYCLING, outings.get(id)!!.mode)
            assertEquals(gravel, outings.get(id)!!.bikeId)
        }

    @Test
    fun aRidesBikeChangesToAnotherOrToNone() =
        runTest {
            val gravel = bike("Gravel")
            val road = bike("Road")
            ride("ride", gravel)

            outings.setBike("ride", road)
            assertEquals(
                "Road",
                outings
                    .observeEnded()
                    .first()
                    .single()
                    .rideBikeName,
            )

            outings.setBike("ride", "gone")
            assertNull(outings.get("ride")!!.bikeId)

            outings.setBike("ride", gravel)
            outings.setBike("ride", null)
            assertNull(outings.get("ride")!!.bikeId)
        }

    private fun photo(
        hikeId: String,
        id: String,
        takenAt: Instant,
    ) = OutingPhotoEntity(
        id = id,
        hikeId = hikeId,
        photoPath = "photos/$id.jpg",
        thumbnailPath = "thumbnails/$id.jpg",
        addedAt = takenAt,
        takenAt = takenAt,
        latitude = 49.2,
        longitude = 20.0,
        caption = null,
    )
}
