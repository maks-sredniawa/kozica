package app.kozica.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.kozica.R
import app.kozica.data.routes.TrailColour
import app.kozica.data.routes.TrailDetail
import app.kozica.data.routes.TrailFacts

/**
 * The resources a Trail's title is composed from, in the app's language, held apart from Compose so
 * the rule runs in a JVM test against the shipped copy.
 *
 * @param withEnds `trail_with_ends`, the colour and the two ends.
 * @param colourOnly `trail_colour_only`.
 */
internal data class TrailWords(
    val colours: Map<TrailColour, String>,
    val withEnds: String,
    val colourOnly: String,
    val polish: Boolean,
)

/**
 * docs/phase-6c.md § 2's title: the name, the Polish one first in Polish and the plain one first
 * otherwise; else the colour and the ends a mapper wrote; else the colour and the places nearest the
 * walk's two ends; else the colour alone.
 */
internal fun trailTitle(
    facts: TrailFacts,
    nearestFrom: String?,
    nearestTo: String?,
    words: TrailWords,
): String {
    val name = if (words.polish) facts.namePl ?: facts.name else facts.name ?: facts.namePl
    if (name != null) return name
    val colour = words.colours.getValue(facts.colour)
    val ends =
        when {
            facts.from != null && facts.to != null -> facts.from to facts.to
            nearestFrom != null && nearestTo != null -> nearestFrom to nearestTo
            else -> null
        }
    return if (ends == null) {
        words.colourOnly.format(colour)
    } else {
        words.withEnds.format(colour, ends.first, ends.second)
    }
}

/** What names a Trail or Bike route on its sheet and in the chooser: its [title], a Section's parent [above] it, and a named Bike route's number [below]. */
internal data class SheetHeading(
    val above: String?,
    val title: String,
    val below: String? = null,
)

/** A Section named as its Long-distance Trail, as the Tatranská magistrála's are, shows the name once. */
internal fun trailHeading(
    trail: TrailDetail,
    words: TrailWords,
): SheetHeading {
    val title = trailTitle(trail.facts, trail.nearestFrom, trail.nearestTo, words)
    return SheetHeading(above = trail.facts.longDistanceName?.takeIf { it != title }, title = title)
}

@Composable
internal fun rememberTrailWords(): TrailWords =
    TrailWords(
        colours = trailColourWords(),
        withEnds = stringResource(R.string.trail_with_ends),
        colourOnly = stringResource(R.string.trail_colour_only),
        polish = LocalConfiguration.current.locales[0].language == "pl",
    )

@Composable
internal fun trailColourWords(): Map<TrailColour, String> =
    mapOf(
        TrailColour.RED to stringResource(R.string.trail_colour_red),
        TrailColour.BLUE to stringResource(R.string.trail_colour_blue),
        TrailColour.GREEN to stringResource(R.string.trail_colour_green),
        TrailColour.YELLOW to stringResource(R.string.trail_colour_yellow),
        TrailColour.BLACK to stringResource(R.string.trail_colour_black),
        TrailColour.OTHER to stringResource(R.string.trail_colour_other),
    )
