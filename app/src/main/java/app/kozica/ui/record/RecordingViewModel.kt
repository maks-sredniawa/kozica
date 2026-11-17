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
import app.kozica.data.OutingEntity
import app.kozica.record.Recorder
import app.kozica.record.Recording
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * @param loaded false until the first reading; before it, a null [recording] does not mean nothing
 *   records.
 * @param unfinished a Hike whose recording was lost with its process, offered to resume or finish
 *   while nothing records and until one of the two is chosen.
 * @param photosLeftOut how many photos, taken or picked, could not be added, shown until dismissed.
 */
data class RecordingUiState(
    val loaded: Boolean = false,
    val recording: Recording? = null,
    val unfinished: OutingEntity? = null,
    val autoResume: Boolean = true,
    val notificationsAsked: Boolean = false,
    val photosLeftOut: Int = 0,
)

/** The recording bar's and its dialogs', which every tab shares (docs/phase-6.md § 2). */
class RecordingViewModel(
    private val recorder: Recorder,
    unfinished: Flow<OutingEntity?>,
    private val preferences: AppPreferences,
    private val finish: suspend (hikeId: String, now: Instant) -> Unit,
    private val applicationScope: CoroutineScope,
    private val savePhoto: suspend (hikeId: String, shot: Uri, pressedAt: Instant) -> Unit = { _, _, _ -> },
    private val deleteShot: (shot: Uri) -> Unit = {},
    private val photosLeftOut: MutableStateFlow<Int> = MutableStateFlow(0),
) : ViewModel() {
    private val answered = MutableStateFlow<String?>(null)

    val state: StateFlow<RecordingUiState> =
        combine(
            recorder.recording,
            unfinished,
            answered,
            preferences.autoResume,
            combine(preferences.notificationsAsked, photosLeftOut, ::Pair),
        ) { recording, unfinished, answered, autoResume, (asked, leftOut) ->
            RecordingUiState(
                loaded = true,
                recording = recording,
                unfinished = unfinished.takeIf { recording == null && it?.id != answered },
                autoResume = autoResume,
                notificationsAsked = asked,
                photosLeftOut = leftOut,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingUiState())

    /** Starts a new Hike, or resumes an unfinished one unless it has already been resumed or finished. */
    fun start(hikeId: String?) {
        if (hikeId != null && !answer(hikeId)) return
        recorder.start(hikeId)
    }

    fun pause() = recorder.pause()

    fun resume() = recorder.resume()

    fun stop() = recorder.stop()

    /** On the application's scope, so leaving the screen cannot cut it short. */
    fun finishUnfinished(hikeId: String) {
        if (!answer(hikeId)) return
        applicationScope.launch { finish(hikeId, Instant.now()) }
    }

    /** The camera's shot, written to [hikeId]'s photos past the screen's life; the camera's own file then goes. */
    fun addPhoto(
        hikeId: String,
        shot: Uri,
        pressedAt: Instant,
    ) {
        applicationScope.launch {
            try {
                savePhoto(hikeId, shot, pressedAt)
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

    private fun answer(hikeId: String): Boolean = answered.getAndUpdate { hikeId } != hikeId

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
                        { hikeId, shot, pressedAt -> container.outings.addPhoto(hikeId, shot, pressedAt) },
                        { shot -> app.contentResolver.delete(shot, null, null) },
                        container.photosLeftOut,
                    )
                }
            }
    }
}
