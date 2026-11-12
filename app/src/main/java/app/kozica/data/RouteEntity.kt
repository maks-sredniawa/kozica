package app.kozica.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.Instant
import java.util.UUID

/**
 * A saved Route: its name, its points, and the Mode and chip it is planned under (docs/phase-5.md § 5,
 * docs/phase-7b.md § 4). Its legs, totals and profile are planned again every time it opens, so it
 * always follows today's Trails and Bike routes.
 */
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
    @ColumnInfo(defaultValue = "HIKING")
    val mode: Mode = Mode.HIKING,
    @ColumnInfo(defaultValue = "ALL")
    val bikeRouteFilter: BikeRouteFilter = BikeRouteFilter.ALL,
)

/**
 * One point of a saved Route, at [position] in the user's order. A child table rather than a JSON
 * column, so a point can gain a column later without rewriting every Route.
 *
 * @param label the place's name, null for a point with no place near it.
 */
@Entity(
    tableName = "route_points",
    primaryKeys = ["routeId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RoutePointEntity(
    val routeId: String,
    val position: Int,
    val latitude: Double,
    val longitude: Double,
    val label: String?,
)

data class RouteWithPoints(
    @Embedded val route: RouteEntity,
    @Relation(parentColumn = "id", entityColumn = "routeId")
    val points: List<RoutePointEntity>,
)
