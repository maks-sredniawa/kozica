package app.kozica.ui.outings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.kozica.R
import app.kozica.theme.Spacing
import app.kozica.ui.map.OutingPhoto
import coil3.compose.AsyncImage

/**
 * An Outing's photos full screen, from its strip or a pin on its map (docs/phase-6.md § 6). A dialog
 * rather than a destination, so it opens over whichever of the two it came from.
 *
 * @param onShown the photo now on screen, so a recreated screen opens the viewer at it again.
 */
@Composable
internal fun PhotoViewer(
    photos: List<OutingPhoto>,
    initialIndex: Int,
    outingName: String,
    onShown: (id: String) -> Unit,
    onSetCaption: (id: String, caption: String) -> Unit,
    onUnpin: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onClose: () -> Unit,
) {
    // The count is read again on every recomposition, so a deleted photo shortens the pager.
    val pager = rememberPagerState(initialPage = initialIndex) { photos.size }
    val current = photos.getOrNull(pager.currentPage)
    LaunchedEffect(current?.id) { current?.let { onShown(it.id) } }
    var captioning by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.tight)) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.photo_close))
                    }
                    Text(
                        outingName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (current != null) {
                        IconButton(onClick = { deleting = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.photo_delete))
                        }
                    }
                }
                val missing = missingPhoto()
                HorizontalPager(
                    state = pager,
                    key = { photos[it].id },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                ) { page ->
                    val photo = photos[page]
                    AsyncImage(
                        model = photo.photo,
                        contentDescription = photo.caption ?: outingName,
                        contentScale = ContentScale.Fit,
                        error = missing,
                        fallback = missing,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (current != null) {
                    Column(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight)) {
                        current.caption?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        Row {
                            TextButton(onClick = { captioning = true }) { Text(stringResource(R.string.photo_caption)) }
                            if (current.pin != null) {
                                TextButton(onClick = { onUnpin(current.id) }) {
                                    Text(stringResource(R.string.photo_remove))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (captioning && current != null) {
        TextDialog(
            title = stringResource(R.string.photo_caption),
            initial = current.caption.orEmpty(),
            onSave = {
                captioning = false
                onSetCaption(current.id, it)
            },
            onDismiss = { captioning = false },
        )
    }
    if (deleting && current != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.photo_delete)) },
            text = { Text(stringResource(R.string.hike_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    // Moved off first, since the screen closes the viewer on a photo it can no longer find.
                    val next = photos.getOrNull(pager.currentPage + 1) ?: photos.getOrNull(pager.currentPage - 1)
                    if (next != null) onShown(next.id) else onClose()
                    onDelete(current.id)
                }) { Text(stringResource(R.string.hike_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** A caption or a note, written once on Save rather than on every keystroke. A blank one is cleared. */
@Composable
internal fun TextDialog(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    singleLine: Boolean = true,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(title) },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else NOTE_LINES,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val NOTE_LINES = 3
