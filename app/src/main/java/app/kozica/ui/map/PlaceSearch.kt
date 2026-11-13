package app.kozica.ui.map

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.kozica.R
import app.kozica.data.regions.REGION_NAMES
import app.kozica.data.routes.BikeRouteKind
import app.kozica.data.routes.Place
import app.kozica.data.routes.PlaceKind
import app.kozica.data.routes.SearchResult
import app.kozica.theme.Spacing
import java.util.Locale

/** docs/phase-5.md § 4's search field, disabled with a reason in a build without the name index. */
@Composable
internal fun SearchField(
    text: String,
    available: Boolean?,
    onText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    TextField(
        value = text,
        onValueChange = onText,
        enabled = available == true,
        singleLine = true,
        placeholder = {
            Text(stringResource(if (available == false) R.string.map_planning_missing else R.string.map_search))
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onText("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.map_search_clear))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // Results follow the text as it is typed, so the key's work is only to clear the keyboard off them.
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        modifier = modifier,
    )
}

@Composable
internal fun SearchResults(
    results: List<SearchResult>,
    onPick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
    last: @Composable () -> Unit = {},
) {
    val words = rememberTrailWords()
    val bikeRouteWords = rememberBikeRouteWords()
    val locale = LocalConfiguration.current.locales[0]
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp, modifier = modifier) {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(results, key = { it.key }) { result ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(result) }
                            .padding(horizontal = Spacing.base, vertical = Spacing.tight),
                ) {
                    when (result) {
                        is SearchResult.AtPlace -> Column { PlaceLines(result.place) }
                        is SearchResult.OnTrail -> {
                            TrailMark(result.facts.colour, result.facts.symbol)
                            Column(Modifier.padding(start = Spacing.tight)) { TrailLines(result, words, locale) }
                        }
                        is SearchResult.LongDistance -> Column { LongDistanceLines(result) }
                        is SearchResult.OnBikeRoute -> {
                            BikeRoutePlate(result.facts, bikeRouteWords)
                            Column(Modifier.padding(start = Spacing.tight)) {
                                BikeRouteLines(result, bikeRouteWords, locale)
                            }
                        }
                        is SearchResult.BikeRouteParent -> Column { BikeRouteParentLines(result) }
                    }
                }
            }
            item(key = "last") { last() }
        }
    }
}

private val SearchResult.key: String
    get() =
        when (this) {
            is SearchResult.AtPlace -> "place ${place.id}"
            is SearchResult.OnTrail -> "trail ${facts.id}"
            is SearchResult.LongDistance -> "long-distance $id"
            is SearchResult.OnBikeRoute -> "bike route ${facts.id}"
            is SearchResult.BikeRouteParent -> "bike route parent $id"
        }

/** Its title, the name that matched where that differs, then "Trail" or the Long-distance Trail it is a Section of, and its length. */
@Composable
private fun TrailLines(
    result: SearchResult.OnTrail,
    words: TrailWords,
    locale: Locale,
) {
    val title = trailTitle(result.facts, null, null, words)
    Text(title, style = MaterialTheme.typography.bodyLarge)
    if (result.matched != title) {
        Text(result.matched, style = MaterialTheme.typography.bodyMedium)
    }
    val kind =
        result.facts.longDistanceName?.let { stringResource(R.string.search_section_of, it) }
            ?: stringResource(R.string.search_kind_trail)
    Text(
        listOf(kind, length(result.facts.metres, locale)).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LongDistanceLines(result: SearchResult.LongDistance) {
    Text(result.name, style = MaterialTheme.typography.bodyLarge)
    Text(
        stringResource(R.string.search_kind_long_distance),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Its title, the name that matched where that differs, then its kind or the parent it is a Section of, and its length. */
@Composable
private fun BikeRouteLines(
    result: SearchResult.OnBikeRoute,
    words: BikeRouteWords,
    locale: Locale,
) {
    val title = bikeRouteTitle(result.facts, null, null, words)
    Text(title, style = MaterialTheme.typography.bodyLarge)
    bikeRouteMatchedLine(result.facts, result.matched, title)?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium)
    }
    val kind =
        result.facts.longDistanceName?.let { stringResource(R.string.search_section_of, it) }
            ?: stringResource(
                if (result.facts.kind ==
                    BikeRouteKind.MTB
                ) {
                    R.string.bike_route_kind_mtb
                } else {
                    R.string.bike_route_kind_touring
                },
            )
    Text(
        listOf(kind, length(result.facts.metres, locale)).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BikeRouteParentLines(result: SearchResult.BikeRouteParent) {
    Text(result.name, style = MaterialTheme.typography.bodyLarge)
    Text(
        stringResource(R.string.search_kind_bike_route_parent),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The place offered as a point: added as the start of an empty Route, or else as its new end. */
@Composable
internal fun OfferedPlace(
    place: Place,
    first: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight),
        ) {
            Column(Modifier.weight(1f)) { PlaceLines(place) }
            FilledTonalButton(onClick = onAdd) {
                Text(stringResource(if (first) R.string.route_start else R.string.route_end))
            }
        }
    }
}

/** Its local name, the name that matched where that differs, then its kind and Region. */
@Composable
private fun PlaceLines(place: Place) {
    Text(place.name, style = MaterialTheme.typography.bodyLarge)
    if (place.matched != place.name) {
        Text(place.matched, style = MaterialTheme.typography.bodyMedium)
    }
    val region = REGION_NAMES[place.region]?.let { stringResource(it) }
    Text(
        listOfNotNull(stringResource(place.kind.label), region).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@get:StringRes
private val PlaceKind.label: Int
    get() =
        when (this) {
            PlaceKind.PEAK -> R.string.place_peak
            PlaceKind.PASS -> R.string.place_pass
            PlaceKind.HUT -> R.string.place_hut
            PlaceKind.SHELTER -> R.string.place_shelter
            PlaceKind.VIEWPOINT -> R.string.place_viewpoint
            PlaceKind.TOWN -> R.string.place_town
            PlaceKind.VILLAGE -> R.string.place_village
            PlaceKind.HAMLET -> R.string.place_hamlet
            PlaceKind.LAKE -> R.string.place_lake
            PlaceKind.CLEARING -> R.string.place_clearing
            PlaceKind.PLACE -> R.string.place_place
        }
