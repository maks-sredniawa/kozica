package app.kozica.data

import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import app.kozica.data.hikes.Climb
import app.kozica.data.hikes.Height
import app.kozica.data.hikes.Track
import app.kozica.data.hikes.TrackPoint
import app.kozica.data.hikes.climb
import app.kozica.data.hikes.decodeTrack
import app.kozica.data.hikes.encodeTrack
import app.kozica.data.hikes.lastFixAt
import app.kozica.data.hikes.metres
import app.kozica.data.hikes.movingSeconds
import app.kozica.data.hikes.pinAt
import app.kozica.data.hikes.ruleSeconds
import app.kozica.data.hikes.trim
import app.kozica.media.PersistedPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * @param heights each point's height by docs/phase-6.md § 5's rule, one per point of the Track in order.
 * @param deleteMedia deletes a file by its relative path, as `MediaFiles.delete` does.
 * @param persistPhoto writes a photo and its thumbnail, as `MediaFiles.persistPhoto` does.
 */
class OutingRepository(
    private val dao: OutingDao,
    private val heights: suspend (Track) -> List<Height?> = { track -> track.flatten().map { null } },
    private val deleteMedia: (relativePath: String) -> Unit = {},
    private val persistPhoto: suspend (source: Uri) -> PersistedPhoto = { error("No media in this repository") },
) {
    /** Starts an Outing with a placeholder name; Stop names it (docs/phase-6.md § 2). */
    suspend fun start(
        id: String,
        at: Instant,
    ) = dao.insert(OutingEntity(id = id, name = "", startedAt = at))

    suspend fun get(id: String): OutingEntity? = dao.get(id)

    fun observeUnfinished(): Flow<OutingEntity?> = dao.observeUnfinished()

    /** Newest first. */
    fun observeEnded(): Flow<List<OutingSummary>> = dao.observeEnded()

    fun observe(id: String): Flow<OutingEntity?> = dao.observe(id)

    fun observePhotos(id: String): Flow<List<OutingPhotoEntity>> = dao.observePhotos(id)

    /** Every photo when [since] is null, before any Everything export (docs/phase-6.md § 7). */
    fun observePhotosAddedAfter(since: Instant?): Flow<Int> = dao.observePhotosAddedAfter(since ?: Instant.EPOCH)

    suspend fun rename(
        id: String,
        name: String,
    ) = dao.rename(id, name)

    /** A blank note is no note. */
    suspend fun setNote(
        id: String,
        note: String,
    ) = dao.setNote(id, note.trim().ifEmpty { null })

    /**
     * Writes [source] as one of [outingId]'s photos, files before the row, pinned by docs/phase-6.md
     * § 6's rule: a photo from the camera at the last fix when its button was [pressedAt], one picked
     * afterwards at the Track's position when it was taken. An Outing gone before its row is written
     * takes the files with it.
     */
    suspend fun addPhoto(
        outingId: String,
        source: Uri,
        pressedAt: Instant? = null,
        now: Instant = Instant.now(),
    ) {
        if (dao.get(outingId) == null) return
        val media = persistPhoto(source)
        val forget = {
            deleteMedia(media.path)
            deleteMedia(media.thumbnailPath)
        }
        val track = dao.get(outingId)?.let { trackOf(it) } ?: return forget()
        val pin =
            if (pressedAt != null) {
                track.lastFixAt(pressedAt.toEpochMilli())
            } else {
                media.capturedAt?.let { track.pinAt(it.toEpochMilli()) }
            }
        val photo =
            OutingPhotoEntity(
                hikeId = outingId,
                photoPath = media.path,
                thumbnailPath = media.thumbnailPath,
                addedAt = now,
                takenAt = media.capturedAt ?: pressedAt,
                latitude = pin?.latitude,
                longitude = pin?.longitude,
                caption = null,
            )
        try {
            dao.insertPhoto(photo)
        } catch (_: SQLiteConstraintException) {
            // The foreign key: the Outing was deleted between the read above and this write.
            forget()
        }
    }

    private suspend fun trackOf(outing: OutingEntity): Track =
        if (outing.endedAt == null) track(outing.id) else outing.track?.let(::decodeTrack).orEmpty()

    /** A blank caption is no caption. */
    suspend fun setCaption(
        photoId: String,
        caption: String,
    ) = dao.setCaption(photoId, caption.trim().ifEmpty { null })

    /** "Remove from map": the photo stays in the Outing's strip, off its Track and map (decided with the owner on 2026-10-27). */
    suspend fun unpin(photoId: String) = dao.unpin(photoId)

    /** The row, then its files. */
    suspend fun deletePhoto(photoId: String) {
        val photo = dao.photo(photoId) ?: return
        dao.deletePhoto(photoId)
        deleteMedia(photo.photoPath)
        deleteMedia(photo.thumbnailPath)
    }

    /** Reads the height grid for every ended Outing that has not had it read. */
    suspend fun fillClimbs() {
        for (id in dao.withoutClimb()) fillClimb(id)
    }

    /** Reads [id]'s height grid unless it has been read; a read that fails leaves it unread, to be tried again. */
    suspend fun fillClimb(id: String) {
        val outing = dao.get(id)?.takeIf { it.endedAt != null && it.ascentMetres == null } ?: return
        val climb = outing.track?.let(::decodeTrack)?.let { climbOf(it) } ?: return
        dao.setClimb(id, climb.ascent, climb.descent, climb.fromGps)
    }

    /** The rule's time for [id]'s own Track (docs/phase-6b.md § 2); null where its heights cannot give one, and a failed read throws. */
    suspend fun ruleSeconds(id: String): Double? =
        dao
            .get(id)
            ?.track
            ?.let(::decodeTrack)
            ?.let { it.ruleSeconds(heights(it)) }

    private suspend fun climbOf(track: Track): Climb? =
        try {
            climb(heights(track))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    /**
     * Keeps the points [first] to [last] of the Track in order and deletes the rest for good; the
     * totals are computed again, and the start or end moves to the first or last point kept where
     * that end was cut (docs/phase-6.md § 4). A height read that fails leaves the ascent unread.
     */
    suspend fun trim(
        id: String,
        first: Int,
        last: Int,
    ) {
        val outing = dao.get(id) ?: return
        val endedAt = outing.endedAt ?: return
        val whole = outing.track?.let(::decodeTrack) ?: return
        val count = whole.sumOf { it.size }
        val track = whole.trim(first, last)
        val points = track.flatten()
        if (points.isEmpty()) return
        val climb = climbOf(track)
        dao.trim(
            id,
            OutingTrim(
                startedAt = if (first > 0) Instant.ofEpochMilli(points.first().timeMillis) else outing.startedAt,
                endedAt = if (last < count - 1) Instant.ofEpochMilli(points.last().timeMillis) else endedAt,
                track = encodeTrack(track),
                metres = track.metres(),
                movingSeconds = track.movingSeconds(),
                ascent = climb?.ascent,
                descent = climb?.descent,
                fromGps = climb?.fromGps == true,
            ),
        )
    }

    /** Deletes the Outing and its photos' rows, then their files (docs/phase-6.md § 4). */
    suspend fun delete(id: String) {
        for (photo in dao.deleteWithPhotos(id)) {
            deleteMedia(photo.photoPath)
            deleteMedia(photo.thumbnailPath)
        }
    }

    /** The stretch the next fix of [outingId] starts: one past the last, so a resume never joins the two. */
    suspend fun nextStretch(outingId: String): Int = dao.lastStretch(outingId) + 1

    suspend fun addPoint(
        outingId: String,
        stretch: Int,
        point: TrackPoint,
    ) = dao.insertPoint(
        TrackPointEntity(
            hikeId = outingId,
            stretch = stretch,
            time = Instant.ofEpochMilli(point.timeMillis),
            latitude = point.latitude,
            longitude = point.longitude,
            altitude = point.altitude,
        ),
    )

    /** The Track of an Outing still recording, as its rows arrive. */
    fun observeTrack(outingId: String): Flow<Track> = dao.observePoints(outingId).map { it.toTrack() }

    suspend fun track(outingId: String): Track = dao.points(outingId).toTrack()

    /**
     * Ends [outingId] at its last point, or at [now] with none, under the name [name] gives it from
     * its first point; does nothing to an Outing that has already ended.
     */
    suspend fun finish(
        outingId: String,
        now: Instant,
        name: suspend (outing: OutingEntity, first: TrackPoint?) -> String,
    ) = dao.finish(outingId) { outing, rows ->
        val track = rows.toTrack()
        val points = track.flatten()
        OutingEnding(
            name = name(outing, points.firstOrNull()),
            endedAt = points.lastOrNull()?.let { Instant.ofEpochMilli(it.timeMillis) } ?: now,
            track = encodeTrack(track),
            metres = track.metres(),
            movingSeconds = track.movingSeconds(),
        )
    }

    private fun List<TrackPointEntity>.toTrack(): Track =
        groupBy { it.stretch }
            .toSortedMap()
            .values
            .map { stretch ->
                stretch.map { TrackPoint(it.time.toEpochMilli(), it.latitude, it.longitude, it.altitude) }
            }
}
