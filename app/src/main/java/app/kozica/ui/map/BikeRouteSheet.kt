package app.kozica.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.kozica.R
import app.kozica.data.hikes.ProfilePoint
import app.kozica.data.routes.BikeRouteDetail
import app.kozica.data.routes.BikeRouteFacts
import app.kozica.data.routes.BikeRouteKind
import app.kozica.data.routes.MtbGrade
import app.kozica.data.routes.TrailColour
import app.kozica.theme.Spacing
import app.kozica.ui.common.GpxExport
import app.kozica.ui.common.GpxFailureNotice

/**
 * The selected Bike route's sheet (docs/phase-7a.md § 4), with a Route along it and its GPX (docs/phase-7b.md
 * § 5); [onPlanRoute] is null while Following, which offers no Route along it.
 */
@Composable
internal fun BikeRouteSheet(
    route: BikeRouteDetail,
    words: BikeRouteWords,
    onClose: () -> Unit,
    onShowWhole: () -> Unit,
    onShowSections: () -> Unit,
    planningRoute: Boolean,
    onPlanRoute: ((title: String) -> Unit)?,
    gpx: GpxExport,
    onExportGpx: (title: String) -> Unit,
    onDismissGpxFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val facts = route.facts
    val heading = bikeRouteHeading(route, words)
    SheetSurface(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.tight)) {
            BikeRoutePlate(facts, words)
            HeadingText(
                heading,
                MaterialTheme.typography.titleMedium,
                Modifier.weight(1f).padding(horizontal = Spacing.tight),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.bike_route_close))
            }
        }
        // A title that is not a name already says the kind.
        if (facts.name != null || facts.namePl != null) {
            Text(
                stringResource(
                    if (facts.kind ==
                        BikeRouteKind.MTB
                    ) {
                        R.string.bike_route_kind_mtb
                    } else {
                        R.string.bike_route_kind_touring
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (facts.kind == BikeRouteKind.MTB) {
            Text(
                stringResource(R.string.bike_route_for_mountain_bikes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.hair),
            )
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.tight),
        ) {
            val metres = length(facts.metres, locale)
            Total(
                stringResource(R.string.route_length),
                if (facts.gaps > 0) "$metres · ${stringResource(R.string.trail_with_gaps)}" else metres,
            )
            Total(stringResource(R.string.route_ascent), wholeMetres(facts.ascent, locale))
            Total(stringResource(R.string.route_descent), wholeMetres(facts.descent, locale))
        }
        Text(
            if (facts.oneWay) {
                stringResource(R.string.bike_route_one_way)
            } else {
                stringResource(
                    R.string.trail_other_way,
                    wholeMetres(facts.descent, locale),
                    wholeMetres(facts.ascent, locale),
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.tight),
        )
        route.difficulty.grade?.let { DifficultyLine(gradeText(it)) }
        route.difficulty.uphill?.let { DifficultyLine(uphillText(it)) }
        if (route.line.size >= 2) {
            Profile(
                route.line.map { ProfilePoint(it.metres, it.heightMetres, it.breakBefore) },
                locale,
                Modifier.padding(top = Spacing.base),
                graded = route.graded,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            onPlanRoute?.let { plan ->
                TextButton(onClick = { plan(heading.title) }, enabled = !planningRoute) {
                    Text(stringResource(R.string.trail_plan_route))
                }
            }
            TextButton(onClick = { onExportGpx(heading.title) }, enabled = !gpx.running) {
                Text(stringResource(R.string.gpx_export))
            }
            TextButton(onClick = onShowWhole) { Text(stringResource(R.string.bike_route_show_whole)) }
            bikeRouteSectionsSubject(route, heading.title)?.let { subject ->
                TextButton(onClick = onShowSections) {
                    Text(stringResource(R.string.trail_show_long_distance, subject))
                }
            }
        }
        if (planningRoute) LinearProgressIndicator(Modifier.fillMaxWidth())
        gpx.failure?.let { GpxFailureNotice(it, onDismissGpxFailure) }
    }
}

@Composable
private fun DifficultyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.tight),
    )
}

@Composable
private fun gradeText(grade: MtbGrade): String =
    if (grade.gradedPercent == null) {
        stringResource(R.string.mtb_grade, grade.scale)
    } else {
        stringResource(R.string.mtb_grade_hardest, grade.scale, grade.gradedPercent)
    }

@Composable
private fun uphillText(grade: MtbGrade): String =
    stringResource(
        if (grade.gradedPercent == null) R.string.mtb_grade_uphill else R.string.mtb_grade_hardest_uphill,
        grade.scale,
    )

/** The Bike routes under a tap that found several, the current Mode's only (docs/phase-7a.md § 4). */
@Composable
internal fun BikeRouteChooser(
    choices: List<BikeRouteDetail>,
    words: BikeRouteWords,
    onPick: (BikeRouteDetail) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetSurface(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.tight)) {
            Text(
                stringResource(R.string.bike_route_chooser_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.bike_route_close))
            }
        }
        for (choice in choices) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onPick(choice) }.padding(vertical = Spacing.tight),
            ) {
                BikeRoutePlate(choice.facts, words)
                HeadingText(
                    bikeRouteHeading(choice, words),
                    MaterialTheme.typography.bodyLarge,
                    Modifier.padding(start = Spacing.tight),
                )
            }
        }
    }
}

/**
 * The plate as `scripts/map/sprites.py` draws it, on the sheet's own ground as [TrailMark] is. Its
 * description gives the colour the bar shows, which a title that is a name does not.
 */
@Composable
internal fun BikeRoutePlate(
    facts: BikeRouteFacts,
    words: BikeRouteWords,
) {
    val mtb = facts.kind == BikeRouteKind.MTB
    val bar = BIKE_ROUTE_COLOURS.getValue(facts.colour)
    val ground = MaterialTheme.colorScheme.surfaceBright
    val edge = MaterialTheme.colorScheme.outline
    val ink = MaterialTheme.colorScheme.onSurface
    val black = BIKE_ROUTE_COLOURS.getValue(TrailColour.BLACK)
    // Black on the bar for every colour but black, where the lighter of the theme's two holds in both themes.
    val roughGround =
        if (facts.colour ==
            TrailColour.BLACK
        ) {
            maxOf(ground, ink, compareBy(Color::luminance))
        } else {
            black
        }
    val description = words.colourOnly(facts)
    Canvas(Modifier.size(width = 40.dp, height = 24.dp).semantics { contentDescription = description }) {
        val unit = size.width / PLATE_WIDTH
        val border = 1.dp.toPx()
        drawRect(ground)
        drawRect(edge, style = Stroke(width = border))
        drawRect(
            bar,
            topLeft = Offset(border, size.height - 3.2f * unit),
            size = Size(size.width - 2 * border, 3.2f * unit - border),
        )
        val tyre = if (mtb) 1.3f else 0.7f
        for (x in listOf(6.2f, 13.8f)) {
            drawCircle(
                ink,
                radius = 2.3f * unit,
                center = Offset(x * unit, 6.4f * unit),
                style =
                    Stroke(
                        width =
                            tyre * unit,
                    ),
            )
        }
        plateLine(ink, unit, 0.9f, 6.2f to 6.4f, 9.6f to 6.4f, 12.4f to 3.4f, 13.8f to 6.4f)
        plateLine(ink, unit, 0.9f, 6.2f to 6.4f, 8f to 3.4f, 9.6f to 6.4f)
        plateLine(ink, unit, 0.9f, 8f to 3.4f, 12.4f to 3.4f)
        plateLine(ink, unit, 0.9f, 7.2f to 2.8f, 8.8f to 2.8f)
        if (mtb) {
            plateLine(ink, unit, 1.1f, 11.2f to 2.4f, 13.6f to 2.4f)
            plateLine(ink, unit, 0.9f, 12.4f to 3.4f, 12.4f to 2.4f)
            val zigzag = (0..10).map { step -> 1.6f + 1.7f * step to if (step % 2 == 1) 9.6f else 8.9f }
            plateLine(roughGround, unit, 0.8f, *zigzag.toTypedArray())
        } else {
            plateLine(ink, unit, 0.7f, 12.4f to 3.4f, 13.6f to 2.6f, 14.4f to 3.2f, 13.6f to 3.9f)
        }
    }
}

private fun DrawScope.plateLine(
    colour: Color,
    unit: Float,
    width: Float,
    vararg points: Pair<Float, Float>,
) {
    val path = Path()
    points.forEachIndexed { i, (x, y) ->
        if (i ==
            0
        ) {
            path.moveTo(x * unit, y * unit)
        } else {
            path.lineTo(x * unit, y * unit)
        }
    }
    drawPath(path, colour, style = Stroke(width = width * unit, join = StrokeJoin.Round))
}

private const val PLATE_WIDTH = 20f
