package app.kozica.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import app.kozica.data.hikes.ProfilePoint
import app.kozica.data.routes.GradedSpan
import java.util.Locale

/**
 * Height against distance, broken where [ProfilePoint.breakBefore] says. With [kept], the metres
 * outside it are covered, for Trim's handles below it. With [graded], those stretches are inked
 * more the harder they are.
 */
@Composable
internal fun Profile(
    line: List<ProfilePoint>,
    locale: Locale,
    modifier: Modifier = Modifier,
    kept: ClosedFloatingPointRange<Double>? = null,
    graded: List<GradedSpan> = emptyList(),
) {
    val low = line.minOf { it.heightMetres }
    val high = line.maxOf { it.heightMetres }
    val stroke = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primaryContainer
    val cut = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(wholeMetres(high, locale), style = MaterialTheme.typography.labelSmall)
            Text(length(line.last().metres, locale), style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val total = line.last().metres.coerceAtLeast(1.0)
            val span = (high - low).coerceAtLeast(PROFILE_MIN_SPAN_METRES)

            fun at(point: ProfilePoint) =
                Offset(
                    (point.metres / total * size.width).toFloat(),
                    (size.height - (point.heightMetres - low) / span * size.height).toFloat(),
                )
            val outline = Path()
            val area = Path()
            for ((i, point) in line.withIndex()) {
                val offset = at(point)
                if (i == 0 || point.breakBefore) {
                    if (i > 0) area.lineTo(at(line[i - 1]).x, size.height)
                    outline.moveTo(offset.x, offset.y)
                    area.moveTo(offset.x, size.height)
                    area.lineTo(offset.x, offset.y)
                } else {
                    outline.lineTo(offset.x, offset.y)
                    area.lineTo(offset.x, offset.y)
                }
            }
            area.lineTo(at(line.last()).x, size.height)
            area.close()
            drawPath(area, fill)
            clipPath(area) {
                for (span in graded) {
                    val start = (span.fromMetres / total * size.width).toFloat()
                    val end = (span.toMetres / total * size.width).toFloat()
                    drawRect(
                        stroke.copy(alpha = GRADE_INK[span.scale.coerceIn(GRADE_INK.indices)]),
                        topLeft = Offset(start, 0f),
                        size = Size(end - start, size.height),
                    )
                }
            }
            drawPath(outline, stroke, style = Stroke(width = 2.dp.toPx()))
            if (kept != null) {
                val start = (kept.start / total * size.width).toFloat()
                val end = (kept.endInclusive / total * size.width).toFloat()
                drawRect(cut, size = Size(start, size.height))
                drawRect(cut, topLeft = Offset(end, 0f), size = Size(size.width - end, size.height))
            }
        }
        Text(wholeMetres(low, locale), style = MaterialTheme.typography.labelSmall)
    }
}

private const val PROFILE_MIN_SPAN_METRES = 50.0

// S0 to S6. As ink over the fill, since a dark theme's fill has too few tones left to darken through.
private val GRADE_INK = floatArrayOf(0.10f, 0.22f, 0.36f, 0.52f, 0.68f, 0.84f, 1f)
