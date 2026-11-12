package app.kozica.ui.map

import app.kozica.data.Mode
import app.kozica.data.hikes.Pace
import app.kozica.data.routes.FollowFix
import app.kozica.data.routes.Progress

/** The release half of phase 6b's following log: nothing. */
@Suppress("UNUSED_PARAMETER")
fun logFollow(
    fix: FollowFix,
    before: Progress?,
    metres: Double,
    reach: Double,
    following: FollowingUiState?,
    note: FollowingNote?,
    pace: Pace?,
    mode: Mode,
) = Unit
