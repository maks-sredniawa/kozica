package app.kozica.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.kozica.R
import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode
import app.kozica.theme.Spacing

/** The Mode this build opens in: the stored one, while the switch is developer-only (docs/phase-7a.md § 3). */
fun modeOf(stored: Mode): Mode = stored

/** Counts the other Mode's matches in this build; the release half never queries them (docs/phase-7a.md § 5). */
fun otherModeMatches(count: () -> Int): Int = count()

/** The row that closes the search results, switching to [mode] and keeping the text. */
@Composable
fun OtherModeHint(
    mode: Mode,
    matches: Int,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (matches == 0) return
    Text(
        pluralStringResource(
            if (mode == Mode.HIKING) R.plurals.search_hint_trails else R.plurals.search_hint_bike_routes,
            matches,
            matches,
        ),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onSwitch)
                .padding(horizontal = Spacing.base, vertical = Spacing.snug),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSwitch(
    mode: Mode,
    filter: BikeRouteFilter,
    onMode: (Mode) -> Unit,
    onFilter: (BikeRouteFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        SingleChoiceSegmentedButtonRow {
            Mode.entries.forEachIndexed { index, each ->
                val label = stringResource(if (each == Mode.HIKING) R.string.mode_hiking else R.string.mode_cycling)
                SegmentedButton(
                    selected = mode == each,
                    onClick = { onMode(each) },
                    shape = SegmentedButtonDefaults.itemShape(index, Mode.entries.size),
                    colors =
                        SegmentedButtonDefaults.colors(
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    icon = {},
                    label = {
                        Icon(
                            painterResource(if (each == Mode.HIKING) R.drawable.ic_hike else R.drawable.ic_bike),
                            contentDescription = label,
                        )
                    },
                    modifier = Modifier.width(LocalMinimumInteractiveComponentSize.current),
                )
            }
        }
        if (mode == Mode.CYCLING) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                verticalArrangement = Arrangement.spacedBy(Spacing.tight),
            ) {
                BikeRouteFilter.entries.forEach { each ->
                    FilterChip(
                        selected = filter == each,
                        onClick = { onFilter(each) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        label = {
                            Text(
                                stringResource(
                                    when (each) {
                                        BikeRouteFilter.ALL -> R.string.filter_all
                                        BikeRouteFilter.TOURING -> R.string.filter_touring
                                        BikeRouteFilter.MTB -> R.string.filter_mtb
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}
