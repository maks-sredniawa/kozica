package app.kozica.ui.map

import android.util.Log
import app.kozica.data.Mode
import app.kozica.data.hikes.Pace
import app.kozica.data.routes.FIX_OLDEST_MILLIS
import app.kozica.data.routes.FIX_WORST_ACCURACY_METRES
import app.kozica.data.routes.FollowFix
import app.kozica.data.routes.Progress
import java.util.Locale

/**
 * One line per fix while following, under `KozicaFollow`, for phase 6b's R3 to R5
 * (docs/phase-6b.md, Readings). `moved` is whether this fix moved Progress on from [before]; `why` names
 * a reason it could not, `-` when none applies. `_min` is the time left as the sheet shows it, `_s`
 * the seconds before rounding, at the Pace in Hiking.
 */
fun logFollow(
    fix: FollowFix,
    before: Progress?,
    metres: Double,
    reach: Double,
    following: FollowingUiState?,
    note: FollowingNote?,
    pace: Pace?,
    mode: Mode,
) {
    val why =
        when {
            fix.ageMillis > FIX_OLDEST_MILLIS -> "stale"
            fix.accuracyMetres > FIX_WORST_ACCURACY_METRES -> "inaccurate"
            metres > fix.leavingMetres -> "beyond"
            else -> "-"
        }
    val leaving = following?.leaving
    val left = following?.left
    val line =
        String.format(
            Locale.ROOT,
            "time=%d accuracy=%.0f distance=%.1f counts=%b moved=%b why=%s progress=%s reach=%.0f leaving=%s " +
                "banner=%b next=%s next_min=%s next_s=%s end_m=%s end_min=%s end_s=%s note=%s",
            fix.atMillis,
            fix.accuracyMetres,
            metres,
            fix.counts,
            following?.progress?.metres != before?.metres,
            why,
            following?.progress?.metres?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "-",
            reach,
            leaving?.let { ((it.lastMillis - it.sinceMillis) / 1000).toString() } ?: "-",
            leaving?.shown == true,
            left?.nextPoint ?: "-",
            left?.let { shownTime(it.toNext.seconds, mode, pace).minutes } ?: "-",
            left?.let { atPace(pace.takeIf { mode == Mode.HIKING }, it.toNext.seconds) } ?: "-",
            left?.let { String.format(Locale.ROOT, "%.0f", it.toEnd.metres) } ?: "-",
            left?.let { shownTime(it.toEnd.seconds, mode, pace).minutes } ?: "-",
            left?.let { atPace(pace.takeIf { mode == Mode.HIKING }, it.toEnd.seconds) } ?: "-",
            note ?: "-",
        )
    // JVM unit tests run the debug source set, where android.util.Log is a stub that throws.
    runCatching { Log.i("KozicaFollow", line) }
}

private fun atPace(
    pace: Pace?,
    seconds: Double,
) = String.format(Locale.ROOT, "%.0f", seconds * (pace?.factor ?: 1.0))
