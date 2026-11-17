package app.kozica.data

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.time.Instant

enum class BikeNaming {
    Saved,
    Blank,
    Taken,
}

/**
 * My bikes (docs/phase-7c.md § 3).
 *
 * @param persistPhoto writes a `MediaKind.Bike` file and returns its relative path, as `MediaFiles.persist` does.
 * @param deleteMedia deletes a file by its relative path, as `MediaFiles.delete` does.
 */
class BikeRepository(
    private val dao: BikeDao,
    private val preferences: AppPreferences,
    private val persistPhoto: suspend (source: Uri) -> String = { error("No media in this repository") },
    private val deleteMedia: (relativePath: String) -> Unit = {},
) {
    /** In the order they were added. */
    fun observeAll(): Flow<List<BikeWithRides>> = dao.observeAll()

    suspend fun add(
        name: String,
        now: Instant = Instant.now(),
    ): BikeNaming {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return BikeNaming.Blank
        val added = dao.insertUnlessTaken(BikeEntity(name = trimmed, addedAt = now))
        return if (added) BikeNaming.Saved else BikeNaming.Taken
    }

    suspend fun rename(
        id: String,
        name: String,
    ): BikeNaming {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return BikeNaming.Blank
        return if (dao.renameUnlessTaken(id, trimmed)) BikeNaming.Saved else BikeNaming.Taken
    }

    /** The file before the row; a Bike deleted meanwhile takes the new file with it, a replaced photo its old one. */
    suspend fun setPhoto(
        id: String,
        source: Uri,
    ) {
        if (dao.get(id) == null) return
        val path = persistPhoto(source)
        val before = dao.replacePhoto(id, path) ?: return deleteMedia(path)
        before.filesExcept(path).forEach(deleteMedia)
    }

    suspend fun removePhoto(id: String) {
        dao.replacePhoto(id, null)?.filesExcept(null)?.forEach(deleteMedia)
    }

    /** Its Rides stay with no Bike; a current Bike it was is unset. */
    suspend fun delete(id: String) {
        val bike = dao.delete(id) ?: return
        preferences.clearCurrentBike(id)
        bike.filesExcept(null).forEach(deleteMedia)
    }

    private fun BikeEntity.filesExcept(kept: String?): List<String> =
        listOfNotNull(photoPath, thumbnailPath).distinct().filter { it != kept }
}
