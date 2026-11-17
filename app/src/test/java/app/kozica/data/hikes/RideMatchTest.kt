package app.kozica.data.hikes

import app.kozica.data.routes.Router
import app.kozica.data.routes.TrailNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** docs/phase-7c.md § 5's matching, over the fixture's way 111, a Bike route due east along latitude 49.5. */
class RideMatchTest {
    private val network = TrailNetwork.read(javaClass.getResource("/trails-fixture.network")!!.readBytes())
    private val router = Router(network)
    private val way111 = (0 until network.edgeCount).single { network.edgeWay[it] == 111L }

    private fun pieceNorthOf111(metres: Double): RidePiece {
        val latitude = 49.5 + metres / 111_320.0
        val track = listOf(listOf(TrackPoint(0, latitude, 20.529, null), TrackPoint(5_000, latitude, 20.531, null)))
        return router.matchRide(track).single().single()
    }

    @Test
    fun `a piece within 30 m of a Bike route edge matches it`() {
        val piece = pieceNorthOf111(20.0)
        assertEquals(way111, piece.edge)
        assertEquals(Router.metres(49.5, 20.529, 49.5, 20.531), piece.metres, 1.0)
    }

    @Test
    fun `a piece beyond 30 m of every Bike route edge matches none`() {
        assertNull(pieceNorthOf111(40.0).edge)
    }

    @Test
    fun `each stretch keeps its own pieces`() {
        val stretch = listOf(TrackPoint(0, 49.5, 20.525, null), TrackPoint(5_000, 49.5, 20.527, null))
        assertEquals(listOf(1, 1), router.matchRide(listOf(stretch, stretch)).map { it.size })
    }
}
