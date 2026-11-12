package app.kozica.ui.routes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kozica.R
import app.kozica.theme.Spacing
import app.kozica.ui.appViewModelExtras
import app.kozica.ui.common.DetailScaffold
import app.kozica.ui.common.GpxFailureNotice
import app.kozica.ui.common.shareGpx
import app.kozica.ui.map.RouteNameDialog
import app.kozica.ui.map.length
import app.kozica.ui.map.walkingTime

/** The Routes tab (docs/phase-5.md § 5): saved Routes by name; one opens on the map. */
@Composable
fun RoutesScreen(
    onShow: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutesViewModel = viewModel(factory = RoutesViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val rows = state.routes
    val context = LocalContext.current
    val gpx by viewModel.gpx.collectAsStateWithLifecycle()

    LaunchedEffect(gpx.file) {
        gpx.file?.let { file ->
            context.shareGpx(file)
            viewModel.gpxShared()
        }
    }

    DetailScaffold(title = stringResource(R.string.routes_title), onBack = null, modifier = modifier) { content ->
        when {
            rows == null -> Unit
            rows.isEmpty() ->
                Box(content.fillMaxSize().padding(Spacing.base), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.routes_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            else ->
                Column(content.fillMaxSize()) {
                    gpx.failure?.let {
                        GpxFailureNotice(it, viewModel::dismissGpxFailure, Modifier.padding(horizontal = Spacing.base))
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(rows, key = { it.route.id }) { row ->
                            RouteRowItem(
                                row = row,
                                onShow = {
                                    viewModel.show(row.route.id)
                                    onShow()
                                },
                                onRename = {
                                    renaming = row.route.id
                                    draft = row.route.name
                                },
                                onDelete = { deleting = row.route.id },
                                exportingGpx = gpx.running,
                                onExportGpx = { viewModel.exportGpx(row.route.id) },
                            )
                        }
                    }
                }
        }
    }

    rows?.firstOrNull { it.route.id == renaming }?.let { row ->
        RouteNameDialog(
            title = stringResource(R.string.routes_rename),
            name = draft,
            onNameChange = { draft = it },
            confirm = stringResource(R.string.routes_rename),
            onConfirm = { name ->
                viewModel.rename(row.route.id, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    rows?.firstOrNull { it.route.id == deleting }?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.routes_delete_confirm, row.route.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(row.route.id)
                    deleting = null
                }) { Text(stringResource(R.string.routes_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun RouteRowItem(
    row: RouteRow,
    onShow: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    exportingGpx: Boolean,
    onExportGpx: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onShow)
                .padding(start = Spacing.base, top = Spacing.tight, bottom = Spacing.tight),
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.route.name, style = MaterialTheme.typography.bodyLarge)
            if (row.metres != null) {
                val time = row.minutes?.let { " · " + walkingTime(it, locale) }.orEmpty()
                val signposts =
                    row.signpostMinutes
                        ?.takeIf { it != row.minutes }
                        ?.let { " · " + stringResource(R.string.route_time_signposts, walkingTime(it, locale)) }
                        .orEmpty()
                Text(
                    "${length(row.metres, locale)}$time$signposts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.routes_more, row.route.name))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.routes_rename)) },
                    onClick = {
                        menu = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.gpx_export)) },
                    enabled = row.metres != null && !exportingGpx,
                    onClick = {
                        menu = false
                        onExportGpx()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.routes_delete)) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}
