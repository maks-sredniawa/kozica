package app.kozica.ui.outings

import app.kozica.ui.map.wholeMetres
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The day a Hike started, as the recording named it (docs/phase-6.md § 2). */
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
