package app.kozica.ui.record

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import app.kozica.R
import app.kozica.ui.common.startActivitySafely
import app.kozica.ui.map.LOCATION_PERMISSIONS
import app.kozica.ui.map.LocationGrant
import app.kozica.ui.map.readLocation

/**
 * Record's asks, in docs/phase-6.md § 2's order, each only if needed: precise location (the
 * system's ask, or its upgrade from approximate), then notifications once on API 33+, then [onStart].
 * Precise location still missing after its ask shows why, with the app's settings one tap away.
 *
 * Returns what Record calls, with the Outing to resume or null for a new one.
 */
@Composable
fun rememberRecordStart(
    notificationsAsked: Boolean,
    onNotificationsAsked: () -> Unit,
    onStart: (outingId: String?) -> Unit,
): (outingId: String?) -> Unit {
    val context = LocalContext.current
    // Saveable, so an ask that outlives a configuration change still knows what it was for.
    var asking by rememberSaveable { mutableStateOf(false) }
    var outingToResume by rememberSaveable { mutableStateOf<String?>(null) }
    var needsPrecise by remember { mutableStateOf(false) }
    val notifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (asking) onStart(outingToResume)
            asking = false
        }
    val afterLocation: () -> Unit = {
        val granted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (granted || notificationsAsked) {
            asking = false
            onStart(outingToResume)
        } else {
            onNotificationsAsked()
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val location =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (!asking) return@rememberLauncherForActivityResult
            if (context.readLocation().grant == LocationGrant.PRECISE) {
                afterLocation()
            } else {
                asking = false
                needsPrecise = true
            }
        }

    if (needsPrecise) {
        AlertDialog(
            onDismissRequest = { needsPrecise = false },
            title = { Text(stringResource(R.string.record_precise_title)) },
            text = { Text(stringResource(R.string.record_precise_body)) },
            confirmButton = {
                TextButton(onClick = {
                    needsPrecise = false
                    context.startActivitySafely(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                }) { Text(stringResource(R.string.record_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { needsPrecise = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    return { outingId ->
        asking = true
        outingToResume = outingId
        if (context.readLocation().grant ==
            LocationGrant.PRECISE
        ) {
            afterLocation()
        } else {
            location.launch(LOCATION_PERMISSIONS)
        }
    }
}
