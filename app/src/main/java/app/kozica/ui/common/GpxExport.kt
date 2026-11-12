package app.kozica.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.kozica.R
import app.kozica.data.gpx.Gpx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.LocalDate

/** [NoWayOverBikeRoutes] is a ride's [NoWay] (docs/phase-7b.md § 4). */
enum class GpxFailure { NoWay, NoWayOverBikeRoutes, NotWritten }

/** @param file written and waiting for the share sheet, held in the view model so a rotation does not drop it. */
data class GpxExport(
    val running: Boolean = false,
    val file: File? = null,
    val failure: GpxFailure? = null,
)

/** A screen's GPX exports (docs/phase-6.md § 8), one at a time, in [scope] so they outlive a rotation. */
class GpxExports(
    private val scope: CoroutineScope,
    private val write: suspend (Gpx, LocalDate) -> File,
) {
    private val current = MutableStateFlow(GpxExport())
    val state: StateFlow<GpxExport> = current.asStateFlow()

    /** [build] gives null when there is no whole line to export, which fails as [noWay]. */
    fun start(
        date: LocalDate,
        noWay: GpxFailure = GpxFailure.NoWay,
        build: suspend () -> Gpx?,
    ) {
        if (current.value.running) return
        current.value = GpxExport(running = true)
        scope.launch {
            val gpx = build()
            current.value =
                if (gpx == null) {
                    GpxExport(failure = noWay)
                } else {
                    try {
                        GpxExport(file = write(gpx, date))
                    } catch (_: IOException) {
                        GpxExport(failure = GpxFailure.NotWritten)
                    }
                }
        }
    }

    fun shared() = current.update { it.copy(file = null) }

    fun dismiss() = current.update { it.copy(failure = null) }
}

@Composable
fun GpxFailureNotice(
    failure: GpxFailure,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(
                when (failure) {
                    GpxFailure.NoWay -> R.string.gpx_export_no_way
                    GpxFailure.NoWayOverBikeRoutes -> R.string.gpx_export_no_way_bike_routes
                    GpxFailure.NotWritten -> R.string.gpx_export_failed
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
    }
}
