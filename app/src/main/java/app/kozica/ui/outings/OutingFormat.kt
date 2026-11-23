package app.kozica.ui.outings

import androidx.annotation.StringRes
import app.kozica.R
import app.kozica.data.Mode
import app.kozica.ui.map.wholeMetres
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The day an Outing started, as the recording named it (docs/phase-6.md § 2). */
internal fun hikeDate(
    startedAt: Instant,
    locale: Locale,
): String =
    DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .format(startedAt.atZone(ZoneId.systemDefault()))

/** Marked "≈" when any of it came from GPS height (docs/phase-6.md § 5). */
internal fun climbMetres(
    metres: Double,
    fromGps: Boolean,
    locale: Locale,
): String = if (fromGps) "≈ ${wholeMetres(metres, locale)}" else wholeMetres(metres, locale)

/** "Hike" or "Ride", as a row's icon reads and a sheet names it (docs/phase-7c.md § 7). */
@get:StringRes
internal val Mode.outingKind: Int get() = if (this == Mode.HIKING) R.string.outing_hike else R.string.outing_ride
