package app.kozica.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.kozica.R
import app.kozica.data.Mode
import app.kozica.data.hikes.Pace
import app.kozica.data.hikes.ProfilePoint
import app.kozica.data.hikes.minutes
import app.kozica.data.routes.Approach
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RideGrade
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.ridingMinutes
import app.kozica.theme.Spacing
import java.util.Locale

/** docs/phase-5.md § 4: the totals and warnings always, the points, the profile and Reverse on a tap. */
@Composable
internal fun RouteSheet(
    route: RouteUiState,
    pace: Pace?,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onReverse: () -> Unit,
    onShowSaveDialog: (Boolean) -> Unit,
    onSave: (name: String) -> Unit,
    onFollow: () -> Unit,
    onStopFollowing: () -> Unit,
    onFindMe: () -> Unit,
    onDismissNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Set as the dialog opens, so a draft restored after the Route itself was lost is never shown.
    var draft by rememberSaveable { mutableStateOf("") }
    val planned = route.planned.takeUnless { route.planning }
    val figures = planned?.let { sheetFigures(it, route.stretches) }
    val open = expanded || route.points.size < 2
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
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = route.points.size >= 2,
                            onClickLabel =
                                stringResource(
                                    if (expanded) R.string.route_details_hide else R.string.route_details_show,
                                ),
                            role = Role.Button,
                        ) { expanded = !expanded }
                        .padding(vertical = Spacing.base),
            ) {
                Box(
                    Modifier
                        .size(width = 32.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                )
            }
            route.name?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            if (route.changed) {
                Text(
                    stringResource(R.string.route_unsaved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                route.unavailable -> Text(stringResource(R.string.map_planning_missing))
                route.points.size < 2 -> Unit
                planned == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                route.following != null -> Following(route, route.following, pace, planned.mode, locale)
                else -> {
                    Totals(planned, pace, locale)
                    if (figures?.paceLine == true) PaceLine(pace, locale)
                }
            }
            route.followingNote?.let { note ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            when (note) {
                                FollowingNote.ENDED -> R.string.following_ended
                                FollowingNote.ROUTE_CHANGED -> R.string.following_route_changed
                                FollowingNote.NOT_FOUND -> R.string.following_not_found
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissNote) { Text(stringResource(R.string.action_ok)) }
                }
            }
            if (route.following != null) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onFindMe) { Text(stringResource(R.string.following_find_me)) }
                    TextButton(onClick = onStopFollowing) { Text(stringResource(R.string.following_stop)) }
                }
            } else if (route.points.size >= 2) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (planned?.complete == true && !route.planning) {
                        TextButton(onClick = onFollow) { Text(stringResource(R.string.following_follow)) }
                    }
                    TextButton(onClick = onReverse) { Text(stringResource(R.string.route_reverse)) }
                    TextButton(onClick = {
                        draft = route.name ?: route.suggestedName
                            ?: listOf(route.points.first(), route.points.last()).joinToString(" – ") { it.name() }
                        onShowSaveDialog(true)
                    }) { Text(stringResource(R.string.route_save)) }
                }
            }
            if (figures != null) Warnings(figures.warnings, route, locale)
            if (open) {
                route.points.forEachIndexed { i, point ->
                    PointRow(
                        index = i,
                        point = point,
                        count = route.points.size,
                        time =
                            planned
                                ?.secondsToPoint
                                ?.get(i)
                                ?.takeIf { i > 0 }
                                ?.let { shownTime(it, planned.mode, pace) },
                        offTrailMetres = planned?.offTrailMetres(i),
                        cycling = planned?.mode == Mode.CYCLING,
                        locale = locale,
                        onRemove = { onRemove(i) },
                        onMove = { to -> onMove(i, to) },
                    )
                }
                if (planned != null && planned.metres > 0) {
                    Profile(planned.profile(), locale, Modifier.padding(top = Spacing.base))
                }
            }
        }
    }
    if (route.naming) {
        RouteNameDialog(
            title = stringResource(R.string.route_save),
            name = draft,
            onNameChange = { draft = it },
            confirm = stringResource(R.string.route_save),
            onConfirm = onSave,
            onDismiss = { onShowSaveDialog(false) },
        )
    }
}

/** Asks for a Route's name, for Save and for Rename; a blank name cannot be confirmed. */
@Composable
internal fun RouteNameDialog(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.route_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** What the sheet shows beside a Route's distance, ascent, descent and time, which it always shows. */
internal data class SheetFigures(
    val paceLine: Boolean,
    val warnings: List<RouteWarning>,
)

internal sealed interface RouteWarning {
    /** No way between point [from] and the next, over the Trails or, in Cycling, the Bike routes. */
    data class NoWay(
        val from: Int,
        val cycling: Boolean,
    ) : RouteWarning

    data class Demanding(
        val stretch: NamedStretch,
    ) : RouteWarning

    data class Ride(
        val grade: RideGrade,
    ) : RouteWarning
}

/** docs/phase-7b.md § 3: a ride shows no Pace line, since the walking Pace never touches it, and its warning is the MTB scale's. */
internal fun sheetFigures(
    planned: PlannedRoute,
    stretches: List<NamedStretch>,
): SheetFigures {
    val cycling = planned.mode == Mode.CYCLING
    val noWay =
        planned.legs.mapIndexedNotNull { i, leg -> if (leg == null) RouteWarning.NoWay(i, cycling) else null }
    val demanding =
        if (cycling) {
            listOfNotNull(planned.hardestRide?.let(RouteWarning::Ride))
        } else {
            stretches.map(RouteWarning::Demanding)
        }
    return SheetFigures(paceLine = !cycling, warnings = noWay + demanding)
}

/** A time as shown: a walk's at the Pace and rounded up as a signpost's, a ride's by the riding rule alone, "≈" (docs/phase-7b.md § 3). */
internal data class ShownTime(
    val minutes: Int,
    val approximate: Boolean,
)

internal fun shownTime(
    seconds: Double,
    mode: Mode,
    pace: Pace?,
): ShownTime =
    if (mode == Mode.CYCLING) {
        ShownTime(ridingMinutes(seconds), approximate = true)
    } else {
        ShownTime(pace.minutes(seconds), approximate = false)
    }

@Composable
internal fun ShownTime.text(locale: Locale): String {
    val time = walkingTime(minutes, locale)
    return if (approximate) stringResource(R.string.route_time_approximate, time) else time
}

@Composable
private fun Totals(
    planned: PlannedRoute,
    pace: Pace?,
    locale: Locale,
) {
    val time = shownTime(planned.seconds, planned.mode, pace)
    val signposts = planned.minutes
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Total(stringResource(R.string.route_length), length(planned.metres, locale))
        Total(stringResource(R.string.route_ascent), wholeMetres(planned.ascent, locale))
        Total(stringResource(R.string.route_descent), wholeMetres(planned.descent, locale))
        if (planned.mode == Mode.CYCLING) {
            Total(stringResource(R.string.route_riding_time), time.text(locale))
        } else {
            Total(
                stringResource(R.string.route_walking_time),
                time.text(locale),
                beside =
                    stringResource(R.string.route_time_signposts, walkingTime(signposts, locale))
                        .takeIf { time.minutes != signposts },
            )
        }
    }
}

@Composable
private fun Total(
    label: String,
    value: String,
    beside: String? = null,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
        beside?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** docs/phase-6b.md § 3: what is left to the next point and to the end, from the user's Progress, timed by [mode]'s rule. */
@Composable
private fun Following(
    route: RouteUiState,
    following: FollowingUiState,
    pace: Pace?,
    mode: Mode,
    locale: Locale,
) {
    val left = following.left
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.hair)) {
        if (left.nextPoint < route.points.lastIndex) {
            val name = route.points[left.nextPoint].name()
            val metres = length(left.toNext.metres, locale)
            val ascent = wholeMetres(left.toNext.ascent, locale)
            Text(
                stringResource(
                    R.string.following_next,
                    name,
                    metres,
                    ascent,
                    shownTime(left.toNext.seconds, mode, pace).text(locale),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        val metres = length(left.toEnd.metres, locale)
        val ascent = wholeMetres(left.toEnd.ascent, locale)
        Text(
            stringResource(
                R.string.following_to_end,
                metres,
                ascent,
                shownTime(left.toEnd.seconds, mode, pace).text(locale),
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        following.back?.let { LegBackLines(it, pace, mode == Mode.CYCLING, locale) }
    }
}

/** docs/phase-6b.md § 4: the leg back, its length and time at the Pace, and how far its start is from the network. */
@Composable
private fun LegBackLines(
    back: LegBack,
    pace: Pace?,
    cycling: Boolean,
    locale: Locale,
) {
    when (back) {
        LegBack.Planning -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = Spacing.tight))
        LegBack.NoWay ->
            Text(
                stringResource(
                    if (cycling) R.string.following_back_no_way_bike_routes else R.string.following_back_no_way,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        is LegBack.Found -> {
            val metres = distance(back.planned.metres, locale)
            Text(
                stringResource(
                    R.string.following_back_leg,
                    metres,
                    shownTime(back.planned.seconds, back.planned.mode, pace).text(locale),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            back.planned.offTrailMetres(0)?.takeIf { it > OFF_TRAIL_SHOWN_METRES }?.let {
                Text(
                    stringResource(
                        if (cycling) R.string.following_back_off_bike_route else R.string.following_back_off_trail,
                        distance(it, locale),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** docs/phase-6b.md § 2: where the times come from, the user's own or the signposts'. */
@Composable
private fun PaceLine(
    pace: Pace?,
    locale: Locale,
) {
    val text =
        when {
            pace == null -> stringResource(R.string.pace_none)
            pace.floored -> pluralStringResource(R.plurals.pace_floored, pace.hikes, factor(pace, locale), pace.hikes)
            else -> pluralStringResource(R.plurals.pace_yours, pace.hikes, factor(pace, locale), pace.hikes)
        }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.hair),
    )
}

private fun factor(
    pace: Pace,
    locale: Locale,
): String = String.format(locale, "%.2f", pace.factor)

@Composable
private fun Warnings(
    warnings: List<RouteWarning>,
    route: RouteUiState,
    locale: Locale,
) {
    val lines =
        warnings.map { warning ->
            when (warning) {
                is RouteWarning.NoWay ->
                    stringResource(
                        if (warning.cycling) R.string.route_no_way_bike_routes else R.string.route_no_way,
                        route.points[warning.from].name(),
                        route.points[warning.from + 1].name(),
                    )
                is RouteWarning.Demanding -> {
                    val named = warning.stretch
                    val name =
                        listOfNotNull(named.from, named.to).distinct().joinToString(" – ").ifEmpty {
                            "${length(named.stretch.fromMetres, locale)} – ${length(named.stretch.toMetres, locale)}"
                        }
                    stringResource(R.string.route_demanding, name)
                }
                is RouteWarning.Ride -> {
                    val grade =
                        stringResource(
                            if (warning.grade.uphill) R.string.mtb_grade_uphill else R.string.mtb_grade,
                            warning.grade.scale,
                        )
                    stringResource(R.string.route_demanding_ride, grade)
                }
            }
        }
    for (line in lines) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.tight)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.tight))
        }
    }
}

@Composable
private fun PointRow(
    index: Int,
    point: RoutePoint,
    count: Int,
    time: ShownTime?,
    offTrailMetres: Double?,
    cycling: Boolean,
    locale: Locale,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.tight)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Text(
                "${index + 1}",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = Spacing.tight)) {
            val name = point.name()
            when (index) {
                0 -> PointRole(stringResource(R.string.route_start))
                count - 1 -> PointRole(stringResource(R.string.route_end))
            }
            Text(
                if (index == 0 || index == count - 1) name else stringResource(R.string.route_via, name),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (time != null) Text(time.text(locale), style = MaterialTheme.typography.bodyMedium)
            if (offTrailMetres != null && offTrailMetres > OFF_TRAIL_SHOWN_METRES) {
                Text(
                    stringResource(
                        if (cycling) R.string.route_off_bike_route else R.string.route_off_trail,
                        distance(offTrailMetres, locale),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { onMove(index - 1) }, enabled = index > 0) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.route_move_up))
        }
        IconButton(onClick = { onMove(index + 1) }, enabled = index < count - 1) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.route_move_down))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.route_remove))
        }
    }
}

@Composable
private fun PointRole(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A point's place name, or its position where no place is near it. */
private fun RoutePoint.name(): String = label ?: String.format(Locale.ROOT, "%.4f, %.4f", latitude, longitude)

private fun PlannedRoute.profile(): List<ProfilePoint> =
    line.mapIndexed { i, sample ->
        ProfilePoint(
            sample.metres,
            sample.heightMetres,
            i > 0 && sample.approach == Approach.NO_WAY,
        )
    }

/** How far point [i] is from where the Route joins the Trails, by the leg leaving it or else the one reaching it. */
private fun PlannedRoute.offTrailMetres(i: Int): Double? =
    legs.getOrNull(i)?.start?.metresAway ?: legs.getOrNull(i - 1)?.end?.metresAway

private const val OFF_TRAIL_SHOWN_METRES = 50.0
