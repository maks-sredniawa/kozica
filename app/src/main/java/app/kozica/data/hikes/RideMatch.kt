package app.kozica.data.hikes

import app.kozica.data.Mode
import app.kozica.data.routes.Router

/** docs/phase-7c.md § 5. */
const val RIDE_MATCH_METRES = 30.0

/** One piece of a Ride's Track, between two neighbouring fixes: on Bike route edge [edge], or off the Bike routes when null. */
data class RidePiece(
    val metres: Double,
    val edge: Int?,
)

/** Each stretch's pieces, each matched by its midpoint to the nearest Bike route edge within [RIDE_MATCH_METRES]. */
fun Router.matchRide(track: Track): List<List<RidePiece>> =
    track.map { stretch ->
        stretch.zipWithNext { a, b ->
            val snap = nearest((a.latitude + b.latitude) / 2, (a.longitude + b.longitude) / 2, Mode.CYCLING)
            RidePiece(
                Router.metres(a.latitude, a.longitude, b.latitude, b.longitude),
                snap?.takeIf { it.metresAway <= RIDE_MATCH_METRES }?.edge,
            )
        }
    }
