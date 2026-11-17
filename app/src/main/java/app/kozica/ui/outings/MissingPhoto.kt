package app.kozica.ui.outings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import app.kozica.R

/**
 * What a photo whose file is gone shows, as a restore may legitimately lack it: the image icon in the
 * theme's colour, centred at twice its size however large the frame, rather than stretched to fill it.
 */
@Composable
internal fun missingPhoto(): Painter {
    val icon = painterResource(R.drawable.ic_image)
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(icon, tint) { CentredIcon(icon, tint) }
}

private class CentredIcon(
    private val icon: Painter,
    private val tint: Color,
) : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified

    override fun DrawScope.onDraw() {
        val edge = minOf(icon.intrinsicSize.minDimension * 2, size.minDimension)
        translate((size.width - edge) / 2, (size.height - edge) / 2) {
            with(icon) { draw(Size(edge, edge), colorFilter = ColorFilter.tint(tint)) }
        }
    }
}
