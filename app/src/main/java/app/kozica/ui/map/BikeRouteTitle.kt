package app.kozica.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.kozica.R
import app.kozica.data.routes.BikeRouteDetail
import app.kozica.data.routes.BikeRouteFacts
import app.kozica.data.routes.BikeRouteKind
import app.kozica.data.routes.TrailColour

/** One kind's title copy: the number and kind, the colour and ends, and the colour alone. */
internal data class BikeRouteKindWords(
    val numbered: String,
    val withEnds: String,
    val colourOnly: String,
)

/** The resources a Bike route's title is composed from, held apart from Compose as [TrailWords] are. */
internal data class BikeRouteWords(
    val colours: Map<TrailColour, String>,
    val kinds: Map<BikeRouteKind, BikeRouteKindWords>,
    val polish: Boolean,
) {
    fun colourOnly(facts: BikeRouteFacts): String =
        kinds.getValue(facts.kind).colourOnly.format(colours.getValue(facts.colour))
}

/** The title rule of docs/phase-7a.md § 4. */
internal fun bikeRouteTitle(
    facts: BikeRouteFacts,
    nearestFrom: String?,
    nearestTo: String?,
    words: BikeRouteWords,
): String {
    val name = if (words.polish) facts.namePl ?: facts.name else facts.name ?: facts.namePl
    if (name != null) return name
    val kind = words.kinds.getValue(facts.kind)
    if (facts.ref != null) return kind.numbered.format(facts.ref)
    val ends =
        when {
            facts.from != null && facts.to != null -> facts.from to facts.to
            nearestFrom != null && nearestTo != null -> nearestFrom to nearestTo
            else -> null
        }
    val colour = words.colours.getValue(facts.colour)
    return if (ends == null) kind.colourOnly.format(colour) else kind.withEnds.format(colour, ends.first, ends.second)
}

/** A Section's parent above the title, as a Trail's Long-distance Trail is, and a named route's number under it. */
internal fun bikeRouteHeading(
    route: BikeRouteDetail,
    words: BikeRouteWords,
): SheetHeading {
    val facts = route.facts
    val title = bikeRouteTitle(facts, route.nearestFrom, route.nearestTo, words)
    return SheetHeading(
        above = facts.longDistanceName?.takeIf { it != title },
        title = title,
        below = facts.ref?.takeIf { facts.name != null || facts.namePl != null },
    )
}

internal fun bikeRouteSectionsSubject(
    route: BikeRouteDetail,
    title: String,
): String? =
    when {
        route.wholeId == null -> null
        route.facts.longDistanceId == null -> title
        else -> route.facts.longDistanceName
    }

@Composable
internal fun rememberBikeRouteWords(): BikeRouteWords =
    BikeRouteWords(
        colours = trailColourWords(),
        kinds =
            mapOf(
                BikeRouteKind.TOURING to
                    BikeRouteKindWords(
                        numbered = stringResource(R.string.bike_route_numbered),
                        withEnds = stringResource(R.string.bike_route_with_ends),
                        colourOnly = stringResource(R.string.bike_route_colour_only),
                    ),
                BikeRouteKind.MTB to
                    BikeRouteKindWords(
                        numbered = stringResource(R.string.mtb_route_numbered),
                        withEnds = stringResource(R.string.mtb_route_with_ends),
                        colourOnly = stringResource(R.string.mtb_route_colour_only),
                    ),
            ),
        polish = LocalConfiguration.current.locales[0].language == "pl",
    )
