package app.kozica.ui.outings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kozica.MainApplication
import app.kozica.data.OutingSummary
import app.kozica.data.backup.BackupScope
import app.kozica.data.backup.ExportResult
import app.kozica.ui.backup.BackupNotice
import app.kozica.ui.backup.notice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * @param outings null until the first read, then newest first.
 * @param photosNotBackedUp photos added since the last Everything export (docs/phase-6.md § 7).
 */
data class OutingsUiState(
    val outings: List<OutingSummary>? = null,
    val photosNotBackedUp: Int = 0,
    val exporting: Boolean = false,
    val exported: File? = null,
    val notice: BackupNotice? = null,
)

/**
 * docs/phase-6.md § 4: ended Hikes newest first. Opening the tab reads the height grid for any
 * Hike still without its ascent, which a Stop the process did not outlive leaves behind.
 */
class OutingsViewModel(
    ended: Flow<List<OutingSummary>>,
    photosNotBackedUp: Flow<Int>,
    private val fillClimbs: suspend () -> Unit,
    private val hikeToShow: MutableStateFlow<String?>,
    private val exportEverything: suspend () -> ExportResult,
    io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val export = MutableStateFlow(OutingsUiState())

    val state: StateFlow<OutingsUiState> =
        combine(ended, photosNotBackedUp, export) { outings, photos, export ->
            export.copy(outings = outings, photosNotBackedUp = photos)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OutingsUiState())

    init {
        viewModelScope.launch(io) { fillClimbs() }
    }

    fun show(id: String) {
        hikeToShow.value = id
    }

    /** The banner's button: to the remembered folder if one is granted, else the share sheet. */
    fun export() {
        if (export.value.exporting) return
        export.update { it.copy(exporting = true, notice = null) }
        viewModelScope.launch {
            val result = exportEverything()
            export.update {
                when (result) {
                    is ExportResult.Saved -> it.copy(exporting = false, notice = BackupNotice.Saved(result.name))
                    is ExportResult.Share ->
                        it.copy(exporting = false, exported = result.archive, notice = result.fallback?.notice())
                    ExportResult.Failed -> it.copy(exporting = false, notice = BackupNotice.ExportFailed)
                }
            }
        }
    }

    fun clearExported() = export.update { it.copy(exported = null) }

    fun dismissNotice() = export.update { it.copy(notice = null) }

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    val container = app.container
                    OutingsViewModel(
                        container.outings.observeEnded(),
                        container.preferences.lastEverythingExport.flatMapLatest(
                            container.outings::observePhotosAddedAfter,
                        ),
                        container.outings::fillClimbs,
                        container.hikeToShow,
                        { container.backupExports.export(BackupScope.Everything, toFolder = true) },
                    )
                }
            }
    }
}
