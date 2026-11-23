package app.kozica.ui.record

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kozica.MainApplication
import app.kozica.data.AppPreferences
import app.kozica.data.Mode
import app.kozica.data.OutingEntity
import app.kozica.record.Recorder
import app.kozica.record.Recording
import app.kozica.ui.bikes.BikeRow
import app.kozica.ui.bikes.toRows
import app.kozica.ui.map.modeOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * @param loaded false until the first reading; before it, a null [recording] does not mean nothing
 *   records.
 * @param unfinished an Outing whose recording was lost with its process, offered to resume or finish
 *   while nothing records and until one of the two is chosen.
 * @param photosLeftOut how many photos, taken or picked, could not be added, shown until dismissed.
 * @param bikes every Bike, for the recording bar's Bike chip on a Ride (docs/phase-7c.md § 4).
 * @param bikeId the recording Ride's Bike as stored, null for none.
 */
data class RecordingUiState(
    val loaded: Boolean = false,
    val recording: Recording? = null,
    val unfinished: OutingEntity? = null,
    val autoResume: Boolean = true,
    val notificationsAsked: Boolean = false,
    val photosLeftOut: Int = 0,
    val bikes: List<BikeRow> = emptyList(),
    val bikeId: String? = null,
)

/** The recording bar's and its dialogs', which every tab shares (docs/phase-6.md § 2). */
class RecordingViewModel(
    private val recorder: Recorder,
    unfinished: Flow<OutingEntity?>,
    private val preferences: AppPreferences,
    private val finish: suspend (outingId: String, now: Instant) -> Unit,
    private val applicationScope: CoroutineScope,
    private val savePhoto: suspend (outingId: String, shot: Uri, pressedAt: Instant) -> Unit = { _, _, _ -> },
    private val deleteShot: (shot: Uri) -> Unit = {},
    private val photosLeftOut: MutableStateFlow<Int> = MutableStateFlow(0),
    bikes: Flow<List<BikeRow>> = flowOf(emptyList()),
    private val observeOuting: (id: String) -> Flow<OutingEntity?> = { flowOf(null) },
    private val chooseBike: suspend (outingId: String, bikeId: String?) -> Unit = { _, _ -> },
) : ViewModel() {
    private val answered = MutableStateFlow<String?>(null)

    // Eager, so Record reads the Mode the map shows rather than a default from before the first read.
    private val mode: StateFlow<Mode> =
        preferences.mapMode.map(::modeOf).stateIn(viewModelScope, SharingStarted.Eagerly, Mode.HIKING)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val recordingBikeId: Flow<String?> =
        recorder.recording
            .map { it?.outingId }
            .distinctUntilChanged()
            .flatMapLatest { id -> id?.let { observeOuting(it).map { outing -> outing?.bikeId } } ?: flowOf(null) }

    val state: StateFlow<RecordingUiState> =
        combine(
            recorder.recording,
            unfinished,
            answered,
            combine(preferences.autoResume, preferences.notificationsAsked, photosLeftOut, ::Triple),
            combine(bikes, recordingBikeId, ::Pair),
        ) { recording, unfinished, answered, (autoResume, asked, leftOut), (bikes, bikeId) ->
            RecordingUiState(
                loaded = true,
                recording = recording,
                unfinished = unfinished.takeIf { recording == null && it?.id != answered },
                autoResume = autoResume,
                notificationsAsked = asked,
                photosLeftOut = leftOut,
                bikes = bikes,
                bikeId = bikeId,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingUiState())

    /**
     * Starts a new Outing in the map's Mode, or resumes an unfinished one unless it has already been resumed or
     * finished.
     */
    fun start(outingId: String?) {
        if (outingId != null && !answer(outingId)) return
        recorder.start(outingId, mode.value)
    }

    /** The recording Ride's Bike, which becomes the current Bike too (docs/phase-7c.md § 4). */
    fun chooseBike(bikeId: String?) {
        val outingId = state.value.recording?.outingId ?: return
        applicationScope.launch { chooseBike(outingId, bikeId) }
    }

    fun pause() = recorder.pause()

    fun resume() = recorder.resume()

    fun stop() = recorder.stop()

    /** On the application's scope, so leaving the screen cannot cut it short. */
    fun finishUnfinished(outingId: String) {
        if (!answer(outingId)) return
        applicationScope.launch { finish(outingId, Instant.now()) }
    }

    /** The camera's shot, written to [outingId]'s photos past the screen's life; the camera's own file then goes. */
    fun addPhoto(
        outingId: String,
        shot: Uri,
        pressedAt: Instant,
    ) {
        applicationScope.launch {
            try {
                savePhoto(outingId, shot, pressedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                photosLeftOut.update { it + 1 }
            } finally {
                deleteShot(shot)
            }
        }
    }

    fun discard(shot: Uri) = deleteShot(shot)

    fun dismissPhotosLeftOut() {
        photosLeftOut.value = 0
    }

    private fun answer(outingId: String): Boolean = answered.getAndUpdate { outingId } != outingId

    fun notificationsAsked() {
        viewModelScope.launch { preferences.setNotificationsAsked() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    val container = app.container
                    RecordingViewModel(
                        container.recorder,
                        container.outings.observeUnfinished(),
                        app.preferences,
                        container.recorder::finish,
                        container.applicationScope,
                        { outingId, shot, pressedAt -> container.outings.addPhoto(outingId, shot, pressedAt) },
                        { shot -> app.contentResolver.delete(shot, null, null) },
                        container.photosLeftOut,
                        container.bikes.observeAll().map { it.toRows(container.media::resolve) },
                        container.outings::observe,
                        { outingId, bikeId ->
                            container.outings.setBike(outingId, bikeId)
                            container.bikes.setCurrent(bikeId)
                        },
                    )
                }
            }
    }
}
