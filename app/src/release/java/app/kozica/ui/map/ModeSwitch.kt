package app.kozica.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kozica.data.BikeRouteFilter
import app.kozica.data.Mode

/** The release half of the switch, until phase 7c opens it to everyone: Hiking, whatever is stored. */
fun modeOf(stored: Mode): Mode = Mode.HIKING

@Composable
fun ModeSwitch(
    mode: Mode,
    filter: BikeRouteFilter,
    onMode: (Mode) -> Unit,
    onFilter: (BikeRouteFilter) -> Unit,
    modifier: Modifier = Modifier,
) = Unit
