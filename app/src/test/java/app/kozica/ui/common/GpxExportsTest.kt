package app.kozica.ui.common

import app.kozica.data.gpx.Gpx
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.LocalDate

class GpxExportsTest {
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val date = LocalDate.of(2026, 10, 27)
    private val gpx = Gpx("Tatry", null, emptyList(), emptyList(), fromOsm = false)

    @Test
    fun `a second export while one runs is ignored, and the file waits until the screen shares it`() {
        var builds = 0
        val planned = CompletableDeferred<Gpx>()
        val exports = GpxExports(scope) { written, _ -> File(written.name) }

        exports.start(date) {
            builds++
            planned.await()
        }
        exports.start(date) {
            builds++
            gpx
        }
        assertEquals(GpxExport(running = true), exports.state.value)

        planned.complete(gpx)
        assertEquals(1, builds)
        assertEquals(GpxExport(file = File("Tatry")), exports.state.value)

        exports.shared()
        assertEquals(GpxExport(), exports.state.value)
    }

    @Test
    fun `no line, no ride and a failed write each say why`() {
        val exports = GpxExports(scope) { _, _ -> throw IOException("full") }

        exports.start(date) { null }
        assertEquals(GpxExport(failure = GpxFailure.NoWay), exports.state.value)

        exports.start(date, GpxFailure.NoWayOverBikeRoutes) { null }
        assertEquals(GpxExport(failure = GpxFailure.NoWayOverBikeRoutes), exports.state.value)

        exports.start(date) { gpx }
        assertEquals(GpxExport(failure = GpxFailure.NotWritten), exports.state.value)

        exports.dismiss()
        assertEquals(GpxExport(), exports.state.value)
    }
}
