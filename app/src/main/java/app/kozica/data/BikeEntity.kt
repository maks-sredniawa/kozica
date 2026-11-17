package app.kozica.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * One of the user's bikes (docs/phase-7c.md § 2). Its photo is one `MediaKind.Bike` file, which both
 * paths name, so a larger view later needs no migration.
 */
@Entity(tableName = "bikes")
data class BikeEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val photoPath: String? = null,
    val thumbnailPath: String? = null,
    val addedAt: Instant,
)

/** A Bike with how many ended Rides are on it; a Hike keeping its `bikeId` is not one of them. */
data class BikeWithRides(
    @Embedded val bike: BikeEntity,
    val rides: Int,
)
