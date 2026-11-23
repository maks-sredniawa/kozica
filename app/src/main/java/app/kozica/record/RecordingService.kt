package app.kozica.record

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import app.kozica.MainActivity
import app.kozica.MainApplication
import app.kozica.R
import app.kozica.data.Mode
import app.kozica.data.hikes.TrackPoint
import app.kozica.data.hikes.metres
import app.kozica.data.routes.Router
import app.kozica.data.schemaBlocksBackgroundWork
import app.kozica.ui.map.distance
import app.kozica.ui.settings.inAppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/**
 * The `location` foreground service that records an Outing (docs/phase-6.md § 2–3).
 *
 * Started only from the visible app: a foreground service started from the background holds no
 * while-in-use location (a platform rule from API 30), which is also why it is not sticky and no
 * boot receiver restarts it. A recording the process loses waits for the next launch, which offers
 * to resume or finish it.
 *
 * Every fix and every command runs whole, one at a time and in arrival order, under one lock: a
 * suspended handler never lets another act on the state it read, and a fix still queued behind Stop
 * finds nothing recording.
 */
@SuppressLint("MissingPermission", "InlinedApi")
class RecordingService : Service() {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val serial = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + serial)
    private val lock = Mutex()
    private lateinit var locations: LocationManager
    private lateinit var sensors: SensorManager
    private var stretch = 0
    private var last: TrackPoint? = null
    private var lastNotified = 0L

    private val app get() = application as MainApplication
    private val recorder get() = app.container.recorder
    private val state get() = recorder.recording.value

    private val listener =
        object : LocationListenerCompat {
            override fun onLocationChanged(location: Location) {
                serially { store(location) }
            }

            override fun onProviderEnabled(provider: String) = locationOff(false)

            override fun onProviderDisabled(provider: String) = locationOff(true)
        }

    private val motion =
        object : TriggerEventListener() {
            override fun onTrigger(trigger: TriggerEvent?) {
                serially { resume(byMotion = true) }
            }
        }

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(newBase.inAppLanguage())

    override fun onCreate() {
        super.onCreate()
        locations = getSystemService(LocationManager::class.java)
        sensors = getSystemService(SensorManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val action = intent?.action
        // A notification's Pause, Resume or Stop reaches here with no screen in front (ADR-0003).
        if (schemaBlocksBackgroundWork()) {
            if (action == ACTION_START) startForeground(placeholder())
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when {
            action == ACTION_START ->
                start(
                    intent.getStringExtra(EXTRA_OUTING),
                    Mode.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_MODE) } ?: Mode.HIKING,
                )
            state == null -> stopSelf(startId)
            action == ACTION_PAUSE -> serially { pause() }
            action == ACTION_RESUME -> serially { resume(byMotion = false) }
            action == ACTION_STOP -> serially { stop() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        LocationManagerCompat.removeUpdates(locations, listener)
        motionSensor()?.let { sensors.cancelTriggerSensor(motion, it) }
        scope.cancel()
        super.onDestroy()
    }

    private fun start(
        resumeId: String?,
        mode: Mode,
    ) {
        startForeground(placeholder())
        if (state != null) return
        val outings = app.container.outings
        val off = !LocationManagerCompat.isLocationEnabled(locations)
        if (resumeId == null) {
            val id = UUID.randomUUID().toString()
            val now = Instant.now()
            // Reported before its row exists, so the new Outing never reads as a lost one.
            recorder.report(Recording(id, now, locationOff = off, mode = mode))
            serially {
                val bikeId =
                    if (mode == Mode.CYCLING) {
                        app.container.bikes
                            .observeCurrentId()
                            .first()
                    } else {
                        null
                    }
                outings.start(id, now, mode, bikeId)
                stretch = 0
                begin()
            }
        } else {
            serially {
                if (state != null) return@serially
                val outing = outings.get(resumeId)?.takeIf { it.endedAt == null } ?: return@serially stop()
                stretch = outings.nextStretch(resumeId)
                recorder.report(
                    Recording(
                        resumeId,
                        outing.startedAt,
                        metres = outings.track(resumeId).metres(),
                        locationOff = off,
                        mode = outing.mode,
                    ),
                )
                begin()
            }
        }
    }

    private suspend fun begin() {
        last = null
        try {
            requestFixes()
        } catch (_: SecurityException) {
            // Precise location revoked between Record's ask and here: nothing can be recorded.
            return stop()
        }
        notifyState(force = true)
    }

    private suspend fun store(fix: Location) {
        val recording = state ?: return
        if (recording.paused || !fix.hasAccuracy() || fix.accuracy > MAX_ACCURACY_METRES) return
        val point = TrackPoint(fix.time, fix.latitude, fix.longitude, if (fix.hasAltitude()) fix.altitude else null)
        app.container.outings.addPoint(recording.outingId, stretch, point)
        val step = last?.let { Router.metres(it.latitude, it.longitude, point.latitude, point.longitude) } ?: 0.0
        last = point
        recorder.report(recording.copy(metres = recording.metres + step, locationOff = false))
        notifyState(force = false)
    }

    private suspend fun pause() {
        val recording = state ?: return
        if (recording.paused) return
        LocationManagerCompat.removeUpdates(locations, listener)
        if (app.preferences.autoResume.first()) motionSensor()?.let { sensors.requestTriggerSensor(motion, it) }
        recorder.report(recording.copy(paused = true, resumedByMotion = false))
        notifyState(force = true)
    }

    private suspend fun resume(byMotion: Boolean) {
        val recording = state ?: return
        if (!recording.paused) return
        motionSensor()?.let { sensors.cancelTriggerSensor(motion, it) }
        stretch++
        last = null
        recorder.report(recording.copy(paused = false, resumedByMotion = byMotion))
        requestFixes()
        notifyState(force = true)
    }

    private suspend fun stop() {
        LocationManagerCompat.removeUpdates(locations, listener)
        motionSensor()?.let { sensors.cancelTriggerSensor(motion, it) }
        state?.let { recorder.finish(it.outingId, Instant.now()) }
        recorder.report(null)
        withContext(Dispatchers.Main) {
            ServiceCompat.stopForeground(this@RecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun locationOff(off: Boolean) {
        serially {
            val recording = state ?: return@serially
            if (recording.locationOff == off) return@serially
            recorder.report(recording.copy(locationOff = off))
            notifyState(force = true)
        }
    }

    private fun serially(handler: suspend () -> Unit) {
        scope.launch { lock.withLock { handler() } }
    }

    private suspend fun requestFixes() =
        withContext(Dispatchers.Main) {
            LocationManagerCompat.requestLocationUpdates(
                locations,
                LocationManager.GPS_PROVIDER,
                LocationRequestCompat
                    .Builder(INTERVAL_MS)
                    .setMinUpdateDistanceMeters(MIN_METRES)
                    .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                    .build(),
                ContextCompat.getMainExecutor(this@RecordingService),
                listener,
            )
        }

    private fun motionSensor(): Sensor? = sensors.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    private fun startForeground(notification: Notification) =
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

    private fun placeholder(): Notification = builder().setContentTitle(getString(R.string.record_recording)).build()

    /** At most every half minute for a new fix; at once for anything the user did or should see. */
    private fun notifyState(force: Boolean) {
        val recording = state ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastNotified < NOTIFY_EVERY_MS) return
        lastNotified = now
        NotificationManagerCompat.from(this).notifySafely(notification(recording))
    }

    @SuppressLint("MissingPermission")
    private fun NotificationManagerCompat.notifySafely(notification: Notification) {
        if (areNotificationsEnabled()) notify(NOTIFICATION_ID, notification)
    }

    private fun notification(recording: Recording): Notification {
        val locale = resources.configuration.locales[0]
        val title =
            when {
                recording.paused -> R.string.record_paused
                recording.resumedByMotion -> R.string.record_resumed_moving
                recording.mode == Mode.CYCLING -> R.string.record_recording_ride
                else -> R.string.record_recording
            }
        val text =
            if (recording.locationOff) getString(R.string.record_location_off) else distance(recording.metres, locale)
        val toggle =
            if (recording.paused) {
                NotificationCompat.Action(0, getString(R.string.record_resume), service(ACTION_RESUME))
            } else {
                NotificationCompat.Action(0, getString(R.string.record_pause), service(ACTION_PAUSE))
            }
        return builder()
            .setContentTitle(getString(title))
            .setContentText(text)
            .setWhen(recording.startedAt.toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .addAction(toggle)
            .addAction(NotificationCompat.Action(0, getString(R.string.record_stop), confirmStop()))
            .build()
    }

    private fun builder(): NotificationCompat.Builder {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.record_channel))
                .build(),
        )
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(activity(null))
    }

    private fun service(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, RecordingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )

    /** Stop opens the app to confirm, as the recording bar's does (docs/phase-6.md § 2). */
    private fun confirmStop(): PendingIntent = activity(MainActivity.ACTION_CONFIRM_STOP)

    private fun activity(action: String?): PendingIntent =
        PendingIntent.getActivity(
            this,
            action.hashCode(),
            Intent(this, MainActivity::class.java)
                .setAction(action ?: Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        internal const val ACTION_START = "app.kozica.record.START"
        internal const val ACTION_PAUSE = "app.kozica.record.PAUSE"
        internal const val ACTION_RESUME = "app.kozica.record.RESUME"
        internal const val ACTION_STOP = "app.kozica.record.STOP"
        private const val EXTRA_OUTING = "outing"
        private const val EXTRA_MODE = "mode"
        private const val CHANNEL_ID = "hike_recording"
        private const val NOTIFICATION_ID = 1
        private const val INTERVAL_MS = 5_000L
        private const val MIN_METRES = 5f
        private const val MAX_ACCURACY_METRES = 30f
        private const val NOTIFY_EVERY_MS = 30_000L

        internal fun start(
            context: Context,
            outingId: String?,
            mode: Mode,
        ) = ContextCompat.startForegroundService(
            context,
            Intent(context, RecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_OUTING, outingId)
                .putExtra(EXTRA_MODE, mode.name),
        )

        internal fun send(
            context: Context,
            action: String,
        ) {
            context.startService(Intent(context, RecordingService::class.java).setAction(action))
        }
    }
}
