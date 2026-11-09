package app.kozica.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode

/** The release half of the switch, until phase 7c opens it to everyone: Hiking, whatever is stored. */
fun modeOf(stored: Mode): Mode = Mode.HIKING

/** A release search never queries the Bike routes until the switch opens (docs/phase-7a.md § 5). */
fun otherModeMatches(count: () -> Int): Int = 0

@Composable
fun OtherModeHint(
    mode: Mode,
    matches: Int,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) = Unit

@Composable
fun ModeSwitch(
    mode: Mode,
    filter: BikeRouteFilter,
    onMode: (Mode) -> Unit,
    onFilter: (BikeRouteFilter) -> Unit,
    modifier: Modifier = Modifier,
) = Unit
