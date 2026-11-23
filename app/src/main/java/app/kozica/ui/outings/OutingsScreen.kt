package app.kozica.ui.outings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kozica.R
import app.kozica.data.OutingSummary
import app.kozica.theme.Spacing
import app.kozica.ui.appViewModelExtras
import app.kozica.ui.backup.text
import app.kozica.ui.common.DetailScaffold
import app.kozica.ui.common.WarningBanner
import app.kozica.ui.common.shareBackupArchive
import app.kozica.ui.map.length
import app.kozica.ui.map.short
import java.time.Duration
import kotlin.time.toKotlinDuration

/** The Outings tab (docs/phase-6.md § 4, docs/phase-7c.md § 7): newest first; one opens on the map. */
@Composable
fun OutingsScreen(
    onShow: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OutingsViewModel = viewModel(factory = OutingsViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = state.outings
    val context = LocalContext.current

    LaunchedEffect(state.exported) {
        state.exported?.let { archive ->
            context.shareBackupArchive(archive)
            viewModel.clearExported()
        }
    }

    DetailScaffold(title = stringResource(R.string.hikes_title), onBack = null, modifier = modifier) { content ->
        Column(content.fillMaxSize()) {
            if (state.photosNotBackedUp > 0) {
                WarningBanner(
                    title =
                        pluralStringResource(
                            R.plurals.hikes_photos_not_backed_up,
                            state.photosNotBackedUp,
                            state.photosNotBackedUp,
                        ),
                    body = listOf(stringResource(R.string.backup_auto_photos)),
                    actionLabel =
                        stringResource(
                            if (state.exporting) R.string.backup_exporting else R.string.hikes_backup_export,
                        ),
                    onAct = viewModel::export,
                )
            }
            state.notice?.let { notice ->
                Row(Modifier.padding(horizontal = Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                    Text(notice.text(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::dismissNotice) { Text(stringResource(R.string.action_ok)) }
                }
            }
            when {
                rows == null -> Unit
                rows.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(Spacing.base), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.hikes_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                else ->
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(rows, key = { it.id }) { outing ->
                            OutingRow(
                                outing = outing,
                                onShow = {
                                    viewModel.show(outing.id)
                                    onShow()
                                },
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun OutingRow(
    outing: OutingSummary,
    onShow: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val facts =
        listOfNotNull(
            hikeDate(outing.startedAt, locale),
            length(outing.metres, locale),
            outing.endedAt?.let { Duration.between(outing.startedAt, it).toKotlinDuration().short(locale) },
            outing.ascentMetres?.let { "↑ ${climbMetres(it, outing.ascentFromGps, locale)}" },
        )
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onShow)
            .padding(horizontal = Spacing.base, vertical = Spacing.tight),
    ) {
        Text(outing.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            facts.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
