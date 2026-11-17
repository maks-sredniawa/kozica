package app.kozica.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.Locale

@Dao
interface BikeDao {
    fun observeAll(): Flow<List<BikeWithRides>> = observeAllCounting(Mode.CYCLING)

    @Query(
        "SELECT bikes.*, (SELECT COUNT(*) FROM hikes WHERE hikes.bikeId = bikes.id AND hikes.mode = :rides " +
            "AND hikes.endedAt IS NOT NULL) AS rides FROM bikes ORDER BY addedAt, id",
    )
    fun observeAllCounting(rides: Mode): Flow<List<BikeWithRides>>

    @Query("SELECT id FROM bikes")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT * FROM bikes WHERE id = :id")
    suspend fun get(id: String): BikeEntity?

    @Query("SELECT * FROM bikes")
    suspend fun all(): List<BikeEntity>

    @Insert
    suspend fun insert(bike: BikeEntity)

    @Query("UPDATE bikes SET name = :name WHERE id = :id")
    suspend fun setName(
        id: String,
        name: String,
    )

    @Query("UPDATE bikes SET photoPath = :path, thumbnailPath = :path WHERE id = :id")
    suspend fun setPhotoPath(
        id: String,
        path: String?,
    )

    @Query("DELETE FROM bikes WHERE id = :id")
    suspend fun deleteRow(id: String)

    /** In one transaction, so two names that fold alike cannot both pass the check. */
    @Transaction
    suspend fun insertUnlessTaken(bike: BikeEntity): Boolean {
        if (all().any { foldBikeName(it.name) == foldBikeName(bike.name) }) return false
        insert(bike)
        return true
    }

    /** False when another Bike holds the name; a Bike may take its own name in another case. */
    @Transaction
    suspend fun renameUnlessTaken(
        id: String,
        name: String,
    ): Boolean {
        if (all().any { it.id != id && foldBikeName(it.name) == foldBikeName(name) }) return false
        setName(id, name)
        return true
    }

    /** The Bike as it was, or null when it is gone and nothing was written. */
    @Transaction
    suspend fun replacePhoto(
        id: String,
        path: String?,
    ): BikeEntity? = get(id)?.also { setPhotoPath(id, path) }

    /** The Bike as it was; its Rides' `bikeId` goes null by the foreign key. */
    @Transaction
    suspend fun delete(id: String): BikeEntity? = get(id)?.also { deleteRow(id) }
}

/**
 * What two Bike names are compared by (docs/phase-7c.md § 2): SQLite's `NOCASE` folds ASCII alone, so
 * "Łukasz" and "łukasz" would both pass a unique index.
 */
fun foldBikeName(name: String): String = name.trim().lowercase(Locale.ROOT)
