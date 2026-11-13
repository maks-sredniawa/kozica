package app.kozica.ui.support

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kozica.BuildConfig
import app.kozica.R
import app.kozica.theme.Spacing
import app.kozica.ui.appViewModelExtras
import app.kozica.ui.common.DetailScaffold
import app.kozica.ui.common.LinkRow
import app.kozica.ui.common.LinkTarget
import app.kozica.ui.common.SectionHeader
import app.kozica.ui.common.startActivitySafely

/**
 * Attribution for every dependency in *this* binary.
 *
 * The list is generated at build time by `app.cash.licensee` over the resolved runtime classpath —
 * not typed from `libs.versions.toml`, which would name a dozen artifacts where the build actually
 * ships two hundred. Apache-2.0 §4 travels with each of them, and a hand-written list cannot stay
 * true across a dependency bump.
 *
 * Kozica's own copy (`scripts/support-sync.keep`): the template's screen plus the ODbL §4.6 offer
 * under the map data's licence and the CC BY 4.0 change notice under the relief's.
 */
@Composable
fun LicencesScreen(
    onBack: () -> Unit,
    onOpenLicence: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LicencesViewModel =
        viewModel(factory = LicencesViewModel.Factory, extras = appViewModelExtras()),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val context = LocalContext.current

    DetailScaffold(
        title = stringResource(R.string.settings_licences),
        onBack = onBack,
        modifier = modifier,
    ) { contentModifier ->
        LazyColumn(modifier = contentModifier.fillMaxSize()) {
            for (group in groups) {
                item(key = group.title) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                // Only an SPDX licence has text bundled with the binary; a vendor's
                                // own terms can only be linked, so that row is not tappable here.
                                .then(
                                    if (group.spdxId != null) {
                                        Modifier.clickable { onOpenLicence(group.spdxId!!) }
                                    } else {
                                        Modifier
                                    },
                                ),
                    ) {
                        SectionHeader(group.title)
                        Text(
                            text = group.artifacts.joinToString("\n") { it.listing },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.base),
                        )
                    }
                }
                when (group.spdxId) {
                    ODBL ->
                        item(key = SupportConfig.MAP_DATA_RELEASE) {
                            LinkRow(
                                title = stringResource(R.string.licences_map_data),
                                subtitle = stringResource(R.string.licences_map_data_hint),
                                target = LinkTarget.Outside,
                                onClick = {
                                    context.startActivitySafely(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            "${SupportConfig.MAP_DATA_RELEASE}${BuildConfig.VERSION_NAME}".toUri(),
                                        ),
                                    )
                                },
                            )
                        }
                    CC_BY ->
                        item(key = "relief-modified") {
                            Text(
                                text = stringResource(R.string.licences_relief_modified),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight),
                            )
                        }
                }
            }
        }
    }
}

private const val ODBL = "ODbL-1.0"
private const val CC_BY = "CC-BY-4.0"
