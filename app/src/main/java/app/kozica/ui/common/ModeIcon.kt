package app.kozica.ui.common

import androidx.annotation.DrawableRes
import app.kozica.R
import app.kozica.data.Mode

/** The walker or the bike, for Record, the recording bar and an Outing's row (docs/phase-7c.md § 4 and § 7). */
@get:DrawableRes
val Mode.icon: Int get() = if (this == Mode.HIKING) R.drawable.ic_hike else R.drawable.ic_bike
