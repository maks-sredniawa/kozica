package app.kozica.ui.common

import androidx.annotation.DrawableRes
import app.kozica.R
import app.kozica.data.Mode

@get:DrawableRes
val Mode.icon: Int get() = if (this == Mode.HIKING) R.drawable.ic_hike else R.drawable.ic_bike
