package app.kozica.ui.bikes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kozica.R
import app.kozica.theme.Spacing

/**
 * The Bike chip (docs/phase-7c.md § 4 and § 7): the bike icon, [bikeId]'s name or "No bike", and a drop-down
 * arrow, opening a sheet of every Bike, "No bike" and "My bikes".
 */
@Composable
fun BikeChip(
    bikes: List<BikeRow>,
    bikeId: String?,
    onPick: (bikeId: String?) -> Unit,
    onOpenBikes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val name = bikes.firstOrNull { it.id == bikeId }?.name ?: stringResource(R.string.bike_none)
    val description = stringResource(R.string.bike_chip, name)
    AssistChip(
        onClick = { choosing = true },
        label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_bike), contentDescription = null) },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        modifier =
            modifier.semantics {
                role = Role.DropdownList
                contentDescription = description
            },
    )
    if (choosing) {
        BikeSheet(
            bikes = bikes,
            bikeId = bikeId,
            onPick = {
                choosing = false
                onPick(it)
            },
            onOpenBikes = {
                choosing = false
                onOpenBikes()
            },
            onDismiss = { choosing = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BikeSheet(
    bikes: List<BikeRow>,
    bikeId: String?,
    onPick: (bikeId: String?) -> Unit,
    onOpenBikes: () -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = bikeId?.takeIf { id -> bikes.any { it.id == id } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.base)) {
            Text(
                stringResource(R.string.bike_title),
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier
                        .padding(
                            horizontal = Spacing.base,
                            vertical = Spacing.tight,
                        ).semantics { heading() },
            )
            Column(Modifier.selectableGroup()) {
                bikes.forEach { bike ->
                    BikeChoiceRow(selected = bike.id == chosen, onClick = { onPick(bike.id) }) {
                        BikeThumbnail(bike.photo, CHOICE_THUMBNAIL_SIZE)
                        Text(
                            bike.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                BikeChoiceRow(selected = chosen == null, onClick = { onPick(null) }) {
                    Spacer(Modifier.size(CHOICE_THUMBNAIL_SIZE))
                    Text(
                        stringResource(R.string.bike_none),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(CHOICE_ROW_HEIGHT)
                        .clickable(role = Role.Button, onClick = onOpenBikes)
                        .padding(horizontal = Spacing.base),
            ) {
                Text(
                    stringResource(R.string.bikes_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun BikeChoiceRow(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.base),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CHOICE_ROW_HEIGHT)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = Spacing.base),
    ) {
        content()
        RadioButton(selected = selected, onClick = null)
    }
}

private val CHOICE_ROW_HEIGHT = 56.dp
private val CHOICE_THUMBNAIL_SIZE = 40.dp
