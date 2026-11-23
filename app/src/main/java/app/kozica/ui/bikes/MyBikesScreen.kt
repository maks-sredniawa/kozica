package app.kozica.ui.bikes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kozica.R
import app.kozica.theme.Spacing
import app.kozica.ui.appViewModelExtras
import app.kozica.ui.common.DetailScaffold
import coil3.compose.AsyncImage
import java.io.File

private val BIKE_THUMBNAIL_SIZE = 56.dp

/** My bikes (docs/phase-7c.md § 3): a name and a photo per Bike, and how many Rides each has. */
@Composable
fun MyBikesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyBikesViewModel = viewModel(factory = MyBikesViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bikes = state.bikes
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var removingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }
    val pickPhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val id = pickingFor ?: return@rememberLauncherForActivityResult
            pickingFor = null
            if (uri != null) viewModel.setPhoto(id, uri)
        }

    DetailScaffold(title = stringResource(R.string.bikes_title), onBack = onBack, modifier = modifier) { content ->
        if (bikes != null) {
            LazyColumn(content.fillMaxSize()) {
                if (bikes.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.bikes_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.base),
                        )
                    }
                }
                items(bikes, key = { it.id }) { bike ->
                    BikeRowItem(
                        bike = bike,
                        onRename = { viewModel.startRenaming(bike.id) },
                        onPickPhoto = {
                            pickingFor = bike.id
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onRemovePhoto = { removingPhoto = bike.id },
                        onDelete = { deleting = bike.id },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = viewModel::startAdding,
                        modifier = Modifier.padding(Spacing.base),
                    ) { Text(stringResource(R.string.bikes_add)) }
                }
            }
        }
    }

    state.naming?.let { naming ->
        val bike = naming.bikeId?.let { id -> bikes?.firstOrNull { it.id == id } }
        if (naming.bikeId == null || bike != null) {
            BikeNameDialog(
                key = naming.bikeId ?: "",
                initial = bike?.name.orEmpty(),
                title = stringResource(if (bike == null) R.string.bikes_add else R.string.bikes_rename),
                confirm = stringResource(if (bike == null) R.string.bikes_add_confirm else R.string.bikes_rename),
                taken = naming.taken,
                onEdited = viewModel::nameEdited,
                onConfirm = viewModel::saveName,
                onDismiss = viewModel::dismissNaming,
            )
        }
    }

    bikes?.firstOrNull { it.id == deleting }?.let { bike ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = {
                Text(
                    if (bike.rides > 0) {
                        pluralStringResource(R.plurals.bikes_delete_confirm_rides, bike.rides, bike.name, bike.rides)
                    } else {
                        stringResource(R.string.bikes_delete_confirm, bike.name)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(bike.id)
                    deleting = null
                }) { Text(stringResource(R.string.bikes_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    bikes?.firstOrNull { it.id == removingPhoto && it.photo != null }?.let { bike ->
        AlertDialog(
            onDismissRequest = { removingPhoto = null },
            text = { Text(stringResource(R.string.bikes_remove_photo_confirm, bike.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removePhoto(bike.id)
                    removingPhoto = null
                }) { Text(stringResource(R.string.bikes_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removingPhoto = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun BikeRowItem(
    bike: BikeRow,
    onRename: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.base),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.base, top = Spacing.tight, bottom = Spacing.tight),
    ) {
        BikeThumbnail(bike.photo)
        Column(Modifier.weight(1f)) {
            Text(
                bike.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(R.plurals.bikes_rides, bike.rides, bike.rides),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.bikes_more, bike.name))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuItem(R.string.bikes_rename, { menu = false }, onRename)
                MenuItem(
                    if (bike.photo == null) R.string.bikes_add_photo else R.string.bikes_change_photo,
                    { menu = false },
                    onPickPhoto,
                )
                if (bike.photo != null) MenuItem(R.string.bikes_remove_photo, { menu = false }, onRemovePhoto)
                MenuItem(R.string.bikes_delete, { menu = false }, onDelete)
            }
        }
    }
}

@Composable
private fun MenuItem(
    label: Int,
    close: () -> Unit,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = {
            close()
            onClick()
        },
    )
}

/** The bike icon on `surfaceVariant`, under the photo, so a Bike with none or whose file is gone shows the icon. */
@Composable
internal fun BikeThumbnail(
    photo: File?,
    size: Dp = BIKE_THUMBNAIL_SIZE,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(size)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Icon(
            painterResource(R.drawable.ic_bike),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun BikeNameDialog(
    key: String,
    initial: String,
    title: String,
    confirm: String,
    taken: Boolean,
    onEdited: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(key) { mutableStateOf(initial) }
    val takenReason = stringResource(R.string.bikes_name_taken)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    onEdited()
                },
                label = { Text(stringResource(R.string.bikes_name)) },
                singleLine = true,
                isError = taken,
                supportingText = if (taken) ({ Text(takenReason) }) else null,
                modifier = if (taken) Modifier.semantics { error(takenReason) } else Modifier,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
