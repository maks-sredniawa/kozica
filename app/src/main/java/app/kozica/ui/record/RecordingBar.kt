package app.kozica.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.kozica.R
import app.kozica.data.OutingEntity
import app.kozica.record.Recording
import app.kozica.record.hasMotionSensor
import app.kozica.theme.Spacing
import app.kozica.ui.common.WarningBanner
import app.kozica.ui.map.distance
import app.kozica.ui.map.short
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Under every tab while a Hike records: what it has so far, and Pause, Resume, the camera and Stop. */
@Composable
fun RecordingBar(
    recording: Recording,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onTakePhoto: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1.seconds)
            now = System.currentTimeMillis()
        }
    }
    var notificationsOn by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose {}
    }
    val sensor = remember { context.hasMotionSensor() }
    val title =
        when {
            recording.paused -> R.string.record_paused
            recording.resumedByMotion -> R.string.record_resumed_moving
            else -> R.string.record_recording
        }
    val elapsed = (now - recording.startedAt.toEpochMilli()).coerceAtLeast(0).milliseconds
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight)) {
            if (recording.locationOff) WarningBanner(title = stringResource(R.string.record_location_off))
            if (!notificationsOn) WarningBanner(title = stringResource(R.string.record_no_notifications))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${distance(recording.metres, locale)} · ${elapsed.inWholeSeconds.seconds.short(locale)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (recording.paused && !sensor) {
                        Text(
                            stringResource(R.string.record_by_hand),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (recording.paused) {
                    FilledTonalIconButton(onClick = onResume) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.record_resume))
                    }
                } else {
                    FilledTonalIconButton(onClick = onPause) {
                        Icon(
                            painterResource(R.drawable.ic_pause),
                            contentDescription = stringResource(R.string.record_pause),
                        )
                    }
                }
                FilledTonalIconButton(onClick = onTakePhoto) {
                    Icon(
                        painterResource(R.drawable.ic_photo_camera),
                        contentDescription = stringResource(R.string.hike_take_photo),
                    )
                }
                FilledTonalIconButton(onClick = onStop) {
                    Icon(painterResource(R.drawable.ic_stop), contentDescription = stringResource(R.string.record_stop))
                }
            }
        }
    }
}

@Composable
fun StopDialog(
    onStop: () -> Unit,
    onKeep: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.record_stop_title)) },
        text = { Text(stringResource(R.string.record_stop_body)) },
        confirmButton = { TextButton(onClick = onStop) { Text(stringResource(R.string.record_stop)) } },
        dismissButton = { TextButton(onClick = onKeep) { Text(stringResource(R.string.record_keep)) } },
    )
}

/** A recording lost with its process: resumed in a new stretch, or finished at its last point, or discarded if it kept none. */
@Composable
fun UnfinishedDialog(
    hike: OutingEntity,
    onResume: (hikeId: String) -> Unit,
    onFinish: (hikeId: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.record_unfinished_title)) },
        confirmButton = {
            TextButton(onClick = { onResume(hike.id) }) { Text(stringResource(R.string.record_unfinished_resume)) }
        },
        dismissButton = {
            TextButton(onClick = { onFinish(hike.id) }) { Text(stringResource(R.string.record_unfinished_finish)) }
        },
    )
}
