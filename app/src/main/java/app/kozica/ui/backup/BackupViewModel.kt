package app.kozica.ui.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kozica.AppContainer
import app.kozica.MainApplication
import app.kozica.data.PreservedCopy
import app.kozica.data.backup.AutoBackupStatus
import app.kozica.data.backup.BackupManifest
import app.kozica.data.backup.BackupScope
import app.kozica.data.backup.ExportFolderState
import app.kozica.data.backup.ExportResult
import app.kozica.data.backup.FolderFallback
import app.kozica.data.backup.RestoreOutcome
import app.kozica.data.backup.RestoreRefusal
import app.kozica.data.backup.autoBackupStatus
import app.kozica.data.backup.exportFolderState
import app.kozica.data.backup.forgetExportFolder
import app.kozica.data.backup.readAutoBackupMarker
import app.kozica.data.backup.rememberExportFolder
import app.kozica.data.deletePreservedCopy
import app.kozica.data.listPreservedCopies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant

sealed interface RestoreSource {
    data class Picked(
        val uri: Uri,
    ) : RestoreSource

    /** A pre-restore snapshot in `preserved/`, the way back from a bad restore. */
    data class Snapshot(
        val file: File,
    ) : RestoreSource
}

enum class BackupWork { Exporting, Restoring }

/** Said once and dismissed. None of them loses an export. */
sealed interface BackupNotice {
    data class Saved(
        val name: String,
    ) : BackupNotice

    data object FolderRefused : BackupNotice

    data object FolderUnavailable : BackupNotice

    data object NotRemembered : BackupNotice

    data object NoPicker : BackupNotice

    data object ExportFailed : BackupNotice
}

data class PendingRestore(
    val source: RestoreSource,
    val manifest: BackupManifest,
)

data class BackupUiState(
    val scope: BackupScope = BackupScope.Records,
    val autoBackup: AutoBackupStatus = AutoBackupStatus.NeverRecorded,
    val preserved: List<PreservedCopy> = emptyList(),
    val folder: ExportFolderState = ExportFolderState.None,
    val notice: BackupNotice? = null,
    val working: BackupWork? = null,
    /** A finished export waiting for the share sheet. */
    val exported: File? = null,
    val pendingRestore: PendingRestore? = null,
    val refusal: RestoreRefusal? = null,
    /** Once set, the screen is the restore's terminal report. */
    val restored: RestoreOutcome.Restored? = null,
    val pendingDelete: PreservedCopy? = null,
)

/** Backup and restore (ADR-0005, docs/phase-6.md § 7). */
class BackupViewModel(
    private val container: AppContainer,
    private val contentResolver: ContentResolver,
) : ViewModel() {
    /** `preserved/` and the marker are files with no change notification, so this ticks after writes. */
    private val refresh = MutableStateFlow(0)
    private val transient = MutableStateFlow(BackupUiState())

    val state: StateFlow<BackupUiState> =
        combine(container.preferences.backupFolderUri, refresh, transient) { folder, _, now ->
            now.copy(
                folder = contentResolver.exportFolderState(folder),
                autoBackup = autoBackupStatus(readAutoBackupMarker(container.filesDir), Instant.now()),
                preserved = listPreservedCopies(container.preservedDir),
            )
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun setScope(scope: BackupScope) = transient.update { it.copy(scope = scope) }

    fun export(toFolder: Boolean) {
        if (transient.value.working != null) return
        transient.update { it.copy(working = BackupWork.Exporting, notice = null) }
        viewModelScope.launch {
            val result = container.backupExports.export(transient.value.scope, toFolder)
            transient.update {
                when (result) {
                    is ExportResult.Saved -> it.copy(working = null, notice = BackupNotice.Saved(result.name))
                    is ExportResult.Share ->
                        it.copy(working = null, exported = result.archive, notice = result.fallback?.notice())
                    ExportResult.Failed -> it.copy(working = null, notice = BackupNotice.ExportFailed)
                }
            }
        }
    }

    fun clearExported() = transient.update { it.copy(exported = null) }

    fun rememberFolder(uri: Uri) {
        viewModelScope.launch {
            if (withContext(Dispatchers.IO) { contentResolver.rememberExportFolder(uri) }) {
                container.preferences.setBackupFolderUri(uri.toString())
                transient.update { it.copy(notice = null) }
            } else {
                transient.update { it.copy(notice = BackupNotice.NotRemembered) }
            }
        }
    }

    fun forgetFolder() {
        val current = state.value.folder
        viewModelScope.launch {
            if (current is ExportFolderState.Remembered) {
                withContext(Dispatchers.IO) { contentResolver.forgetExportFolder(current.uri) }
            }
            container.preferences.setBackupFolderUri(null)
            transient.update { it.copy(notice = null) }
        }
    }

    fun folderPickerUnavailable() = transient.update { it.copy(notice = BackupNotice.NoPicker) }

    fun dismissNotice() = transient.update { it.copy(notice = null) }

    /** A file with no readable manifest is refused before any confirmation. */
    fun inspect(source: RestoreSource) {
        viewModelScope.launch {
            val manifest = container.backupRestorer.readManifest { source.open() }
            transient.update {
                if (manifest == null) {
                    it.copy(refusal = RestoreRefusal.NotABackup)
                } else {
                    it.copy(pendingRestore = PendingRestore(source, manifest))
                }
            }
        }
    }

    fun cancelRestore() = transient.update { it.copy(pendingRestore = null) }

    fun confirmRestore() {
        val pending = transient.value.pendingRestore ?: return
        if (transient.value.working != null) return
        transient.update { it.copy(working = BackupWork.Restoring, pendingRestore = null) }
        viewModelScope.launch {
            if (container.outings.observeUnfinished().first() != null) {
                transient.update { it.copy(working = null, refusal = RestoreRefusal.RecordingUnderway) }
                return@launch
            }
            val outcome = container.backupRestorer.restore(open = { pending.source.open() })
            transient.update {
                when (outcome) {
                    is RestoreOutcome.Restored -> it.copy(working = null, restored = outcome)
                    is RestoreOutcome.Refused -> it.copy(working = null, refusal = outcome.reason)
                }
            }
            refresh.value++
        }
    }

    fun dismissRefusal() = transient.update { it.copy(refusal = null) }

    fun requestDelete(copy: PreservedCopy) = transient.update { it.copy(pendingDelete = copy) }

    fun cancelDelete() = transient.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val target = transient.value.pendingDelete ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { deletePreservedCopy(target) }
            transient.update { it.copy(pendingDelete = null) }
            refresh.value++
        }
    }

    private fun RestoreSource.open(): InputStream =
        when (this) {
            is RestoreSource.Picked -> contentResolver.openInputStream(uri) ?: throw IOException("Cannot read $uri")
            is RestoreSource.Snapshot -> file.inputStream()
        }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    BackupViewModel(app.container, app.contentResolver)
                }
            }
    }
}

internal fun FolderFallback.notice(): BackupNotice =
    when (this) {
        FolderFallback.Refused -> BackupNotice.FolderRefused
        FolderFallback.Unavailable -> BackupNotice.FolderUnavailable
    }
