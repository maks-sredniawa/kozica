package app.kozica.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A recorded Outing, a Hike or a Ride (docs/phase-6.md § 2). While it records, its Track is rows in `track_points` and
 * [endedAt] is null; at Stop the rows are encoded into [track] and deleted.
 *
 * @param ascentMetres null until the height grid has been read for it (§ 5).
 * @param ascentFromGps whether any part of the ascent came from GPS height rather than the grid.
 * @param mode Cycling for a Ride (docs/phase-7c.md § 2).
 * @param bikeId the Ride's Bike, null for none; a Hike may keep one, which counts for nothing until it is a Ride again.
 */
@Entity(
    tableName = "hikes",
    foreignKeys = [
        ForeignKey(
            entity = BikeEntity::class,
            parentColumns = ["id"],
            childColumns = ["bikeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("bikeId")],
)
data class OutingEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val track: ByteArray? = null,
    val note: String? = null,
    val metres: Double = 0.0,
    val movingSeconds: Long = 0,
    val ascentMetres: Double? = null,
    val descentMetres: Double? = null,
    val ascentFromGps: Boolean = false,
    @ColumnInfo(defaultValue = "HIKING")
    val mode: Mode = Mode.HIKING,
    val bikeId: String? = null,
) {
    // A data class compares a ByteArray by reference, which no caller wants.
    override fun equals(other: Any?): Boolean =
        other is OutingEntity &&
            id == other.id &&
            name == other.name &&
            startedAt == other.startedAt &&
            endedAt == other.endedAt &&
            track.contentEquals(other.track) &&
            note == other.note &&
            metres == other.metres &&
            movingSeconds == other.movingSeconds &&
            ascentMetres == other.ascentMetres &&
            descentMetres == other.descentMetres &&
            ascentFromGps == other.ascentFromGps &&
            mode == other.mode &&
            bikeId == other.bikeId

    override fun hashCode(): Int = id.hashCode()
}

/**
 * One fix of a Hike still recording, in [stretch], the run between two pauses.
 *
 * @param altitude the phone's GPS height above the ellipsoid, null when the fix had none.
 */
@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = OutingEntity::class,
            parentColumns = ["id"],
            childColumns = ["hikeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("hikeId")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val hikeId: String,
    val stretch: Int,
    val time: Instant,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
)

/**
 * A photo of a Hike (docs/phase-6.md § 6), in schema 3 from the start because the schema freezes at
 * checkpoint B. Its paths are relative, by kind, as `MediaFiles` writes them.
 *
 * @param takenAt when the photo was taken, null when nothing says.
 * @param latitude where it is pinned on the Track, null with [longitude] for a photo with no pin.
 */
@Entity(
    tableName = "hike_photos",
    foreignKeys = [
        ForeignKey(
            entity = OutingEntity::class,
            parentColumns = ["id"],
            childColumns = ["hikeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("hikeId")],
)
data class OutingPhotoEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val hikeId: String,
    val photoPath: String,
    val thumbnailPath: String,
    val addedAt: Instant,
    val takenAt: Instant?,
    val latitude: Double?,
    val longitude: Double?,
    val caption: String?,
)
