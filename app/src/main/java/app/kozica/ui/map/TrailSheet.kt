package app.kozica.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.kozica.R
import app.kozica.data.hikes.ProfilePoint
import app.kozica.data.routes.TrailColour
import app.kozica.data.routes.TrailDetail
import app.kozica.theme.Spacing
import app.kozica.ui.common.GpxExport
import app.kozica.ui.common.GpxFailureNotice

/** The selected Trail's sheet (docs/phase-6c.md § 3): its title, mark, length, climb each way and profile. */
@Composable
internal fun TrailSheet(
    trail: TrailDetail,
    words: TrailWords,
    onClose: () -> Unit,
    onShowWhole: () -> Unit,
    onShowLongDistance: () -> Unit,
    planningRoute: Boolean,
    onPlanRoute: (title: String) -> Unit,
    gpx: GpxExport,
    onExportGpx: (title: String) -> Unit,
    onDismissGpxFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val facts = trail.facts
    val heading = trailHeading(trail, words)
    SheetSurface(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.tight)) {
            TrailMark(facts.colour, facts.symbol)
            HeadingText(
                heading,
                MaterialTheme.typography.titleMedium,
                Modifier.weight(1f).padding(horizontal = Spacing.tight),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.trail_close))
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            val metres = length(facts.metres, locale)
            Total(
                stringResource(R.string.route_length),
                if (facts.gaps > 0) "$metres · ${stringResource(R.string.trail_with_gaps)}" else metres,
            )
            Total(stringResource(R.string.route_ascent), wholeMetres(facts.ascent, locale))
            Total(stringResource(R.string.route_descent), wholeMetres(facts.descent, locale))
        }
        Text(
            stringResource(
                R.string.trail_other_way,
                wholeMetres(facts.descent, locale),
                wholeMetres(facts.ascent, locale),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.tight),
        )
        if (trail.line.size >= 2) {
            Profile(
                trail.line.map { ProfilePoint(it.metres, it.heightMetres, it.breakBefore) },
                locale,
                Modifier.padding(top = Spacing.base),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            TextButton(onClick = { onPlanRoute(heading.title) }, enabled = !planningRoute) {
                Text(stringResource(R.string.trail_plan_route))
            }
            TextButton(onClick = { onExportGpx(heading.title) }, enabled = !gpx.running) {
                Text(stringResource(R.string.gpx_export))
            }
            TextButton(onClick = onShowWhole) { Text(stringResource(R.string.trail_show_whole)) }
            facts.longDistanceName?.let {
                TextButton(onClick = onShowLongDistance) { Text(stringResource(R.string.trail_show_long_distance, it)) }
            }
        }
        if (planningRoute) LinearProgressIndicator(Modifier.fillMaxWidth())
        gpx.failure?.let { GpxFailureNotice(it, onDismissGpxFailure) }
    }
}

@Composable
internal fun HeadingText(
    heading: SheetHeading,
    titleStyle: TextStyle,
    modifier: Modifier,
) {
    Column(modifier) {
        heading.above?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(heading.title, style = titleStyle)
        heading.below?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The Trails under a tap that found several, each by its mark and title (docs/phase-6c.md § 3). */
@Composable
internal fun TrailChooser(
    choices: List<TrailDetail>,
    words: TrailWords,
    onPick: (TrailDetail) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetSurface(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.tight)) {
            Text(
                stringResource(R.string.trail_chooser_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.trail_close))
            }
        }
        for (choice in choices) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onPick(choice) }.padding(vertical = Spacing.tight),
            ) {
                TrailMark(choice.facts.colour, choice.facts.symbol)
                HeadingText(
                    trailHeading(choice, words),
                    MaterialTheme.typography.bodyLarge,
                    Modifier.padding(start = Spacing.tight),
                )
            }
        }
    }
}

@Composable
internal fun SheetSurface(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.base)
                .padding(bottom = Spacing.base),
        ) { content() }
    }
}

@Composable
internal fun Total(
    label: String,
    value: String,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The Trail's mark as the map draws it, by `scripts/map/sprites.py`'s shapes: the symbol where it is
 * one of the five colours' shapes, else a bar in the Trail's colour, as the style's `trail-symbols` picks.
 */
@Composable
internal fun TrailMark(
    colour: TrailColour,
    symbol: String?,
) {
    val (markColour, shape) =
        symbol
            ?.split('_', limit = 2)
            ?.takeIf { it.size == 2 && TrailColour.of(it[0]) != TrailColour.OTHER && it[1] in SHAPES }
            ?.let { TrailColour.of(it[0]) to it[1] }
            ?: (colour to "bar")
    val fill = TRAIL_COLOURS[markColour] ?: MaterialTheme.colorScheme.outline
    val ground = MaterialTheme.colorScheme.surfaceBright
    val edge = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(width = 32.dp, height = 24.dp)) {
        val w = size.width
        val h = size.height
        drawRect(ground)
        drawRect(edge, style = Stroke(width = 1.dp.toPx()))
        val cx = w / 2
        val cy = h / 2
        when (shape) {
            "bar" -> drawRect(fill, topLeft = Offset(0f, h / 3), size = Size(w, h / 3))
            "dot" -> drawCircle(fill, radius = h * 0.28f, center = Offset(cx, cy))
            "cross" -> {
                val arm = h * 0.11f
                drawRect(fill, topLeft = Offset(cx - arm, h * 0.12f), size = Size(2 * arm, h * 0.76f))
                drawRect(fill, topLeft = Offset(cx - h * 0.33f, cy - arm), size = Size(h * 0.66f, 2 * arm))
            }
            "triangle" ->
                drawPath(
                    Path().apply {
                        moveTo(cx, h * 0.12f)
                        lineTo(cx + h * 0.36f, h * 0.88f)
                        lineTo(cx - h * 0.36f, h * 0.88f)
                        close()
                    },
                    fill,
                )
            "corner" ->
                drawPath(
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(w, 0f)
                        lineTo(0f, h)
                        close()
                    },
                    fill,
                )
        }
    }
}

private val SHAPES = setOf("bar", "dot", "cross", "triangle", "corner")

// The map style's five Trail colours and a Bike route's violet, the house rule's exceptions to colours
// from the theme; their source is TRAIL and BIKE_ROUTE in scripts/map/gen_style.py.
private val TRAIL_COLOURS =
    mapOf(
        TrailColour.RED to Color(0xFFD62828),
        TrailColour.BLUE to Color(0xFF1D4ED8),
        TrailColour.GREEN to Color(0xFF2E8B3A),
        TrailColour.YELLOW to Color(0xFFE6B800),
        TrailColour.BLACK to Color(0xFF1A1A1A),
    )

internal val BIKE_ROUTE_COLOURS = TRAIL_COLOURS + (TrailColour.OTHER to Color(0xFF7F3FBF))
