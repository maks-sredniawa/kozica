package app.kozica.ui.record

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * The recording bar's camera button (docs/phase-6.md § 6): the system camera through `TakePicture`,
 * which needs no permission, writing into a file of ours in the cache.
 *
 * What the result needs is saved state, because the camera app in front is exactly when a
 * low-memory kill takes this process, and the result is then delivered to a new one.
 *
 * @param onTaken the shot, for [outingId]'s photos, taken after the button was pressed at [pressedAt].
 * @param onDiscard a target the camera left no photo in.
 */
@Composable
fun rememberTakePhoto(
    onTaken: (outingId: String, shot: Uri, pressedAt: Instant) -> Unit,
    onDiscard: (target: Uri) -> Unit,
): (outingId: String) -> Unit {
    val context = LocalContext.current
    var target by rememberSaveable { mutableStateOf<Uri?>(null) }
    var outingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pressedAt by rememberSaveable { mutableLongStateOf(0L) }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            val shot = target ?: return@rememberLauncherForActivityResult
            val outing = outingId
            target = null
            if (taken && outing != null) onTaken(outing, shot, Instant.ofEpochMilli(pressedAt)) else onDiscard(shot)
        }
    return { id ->
        val shot = context.cameraTarget()
        target = shot
        outingId = id
        pressedAt = System.currentTimeMillis()
        try {
            camera.launch(shot)
        } catch (_: ActivityNotFoundException) {
            target = null
            onDiscard(shot)
        }
    }
}

/** A `content://` Uri, since a `file://` one may not be handed to another app since Android 7. */
private fun Context.cameraTarget(): Uri {
    val directory = File(cacheDir, "camera").apply { mkdirs() }
    return FileProvider.getUriForFile(this, "$packageName.fileprovider", File(directory, "${UUID.randomUUID()}.jpg"))
}
