package app.kozica.record

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import app.kozica.R
import app.kozica.data.OutingRepository
import app.kozica.data.hikes.TrackPoint
import app.kozica.data.routes.PlaceSearch
import app.kozica.ui.settings.inAppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * An Outing being recorded, as [RecordingService] last reported it.
 *
 * @param resumedByMotion whether the last resume was the motion sensor's rather than the user's.
 */
data class Recording(
    val outingId: String,
    val startedAt: Instant,
    val paused: Boolean = false,
    val metres: Double = 0.0,
    val locationOff: Boolean = false,
    val resumedByMotion: Boolean = false,
)

/** What the screens see of the recording, and how they drive it (docs/phase-6.md § 2–3). */
class Recorder(
    private val context: Context,
    private val outings: OutingRepository,
    private val places: () -> PlaceSearch?,
    private val applicationScope: CoroutineScope,
) {
    private val current = MutableStateFlow<Recording?>(null)

    /** Null when nothing is recording in this process. */
    val recording: StateFlow<Recording?> = current.asStateFlow()

    internal fun report(recording: Recording?) {
        current.value = recording
    }

    /** Starts a new Outing, or resumes [outingId] in a new stretch. Only from the visible app. */
    fun start(outingId: String? = null) = RecordingService.start(context, outingId)

    fun pause() = RecordingService.send(context, RecordingService.ACTION_PAUSE)

    fun resume() = RecordingService.send(context, RecordingService.ACTION_RESUME)

    fun stop() = RecordingService.send(context, RecordingService.ACTION_STOP)

    /**
     * Ends [outingId] at its last point and names it, or discards it if it kept no point; Stop's ending,
     * and a lost recording's. Its ascent is read after, on [applicationScope], so the ending does not
     * wait on the relief.
     */
    suspend fun finish(
        outingId: String,
        now: Instant,
    ) {
        withContext(Dispatchers.IO) {
            outings.finish(outingId, now) { outing, first -> nameFor(outing.startedAt, first) }
        }
        applicationScope.launch { outings.fillClimb(outingId) }
    }

    /** The date, and the nearest named place to where the Outing began, by phase 5's naming rule. */
    private fun nameFor(
        startedAt: Instant,
        first: TrackPoint?,
    ): String {
        val context = context.inAppLanguage()
        val date =
            DateTimeFormatter
                .ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(context.resources.configuration.locales[0])
                .format(startedAt.atZone(ZoneId.systemDefault()))
        val place = first?.let { places()?.nearest(it.latitude, it.longitude) }
        return place?.let { context.getString(R.string.hike_name, date, it.name) } ?: date
    }
}

fun Context.hasMotionSensor(): Boolean =
    getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) != null
