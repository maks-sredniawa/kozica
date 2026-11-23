package app.kozica.ui.bikes

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kozica.MainApplication
import app.kozica.data.BikeNaming
import app.kozica.data.BikeRepository
import app.kozica.data.BikeWithRides
import app.kozica.ui.map.addEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** @param photo null without one; a file gone missing shows the placeholder too. */
data class BikeRow(
    val id: String,
    val name: String,
    val photo: File?,
    val rides: Int,
)

fun List<BikeWithRides>.toRows(resolveMedia: (relativePath: String) -> File): List<BikeRow> =
    map { BikeRow(it.bike.id, it.bike.name, it.bike.thumbnailPath?.let(resolveMedia), it.rides) }

/** The name dialog, for a new Bike when [bikeId] is null; [taken] after a save another Bike's name refused. */
data class BikeNameDialog(
    val bikeId: String?,
    val taken: Boolean = false,
)

/** @param bikes null until the first read, then in the order they were added. */
data class MyBikesUiState(
    val bikes: List<BikeRow>? = null,
    val naming: BikeNameDialog? = null,
)

/** docs/phase-7c.md § 3. Writes run in [applicationScope], so leaving the screen never cuts one off between file and row. */
class MyBikesViewModel(
    private val repository: BikeRepository,
    private val resolveMedia: (relativePath: String) -> File,
    private val applicationScope: CoroutineScope,
    private val photosLeftOut: MutableStateFlow<Int>,
) : ViewModel() {
    private val naming = MutableStateFlow<BikeNameDialog?>(null)

    val state: StateFlow<MyBikesUiState> =
        combine(repository.observeAll(), naming) { bikes, naming ->
            MyBikesUiState(
                bikes = bikes.toRows(resolveMedia),
                naming = naming,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyBikesUiState())

    fun startAdding() {
        naming.value = BikeNameDialog(bikeId = null)
    }

    fun startRenaming(id: String) {
        naming.value = BikeNameDialog(bikeId = id)
    }

    fun nameEdited() {
        naming.update { it?.copy(taken = false) }
    }

    fun dismissNaming() {
        naming.value = null
    }

    fun saveName(name: String) {
        val dialog = naming.value ?: return
        viewModelScope.launch {
            val result = dialog.bikeId?.let { repository.rename(it, name) } ?: repository.add(name)
            naming.update { current ->
                if (current == null || current.bikeId != dialog.bikeId) {
                    current
                } else {
                    when (result) {
                        BikeNaming.Saved -> null
                        BikeNaming.Taken -> current.copy(taken = true)
                        BikeNaming.Blank -> current
                    }
                }
            }
        }
    }

    fun setPhoto(
        id: String,
        source: Uri,
    ) {
        applicationScope.launch {
            val failed = addEach(listOf(source)) { repository.setPhoto(id, it) }
            if (failed > 0) photosLeftOut.update { it + failed }
        }
    }

    fun removePhoto(id: String) {
        applicationScope.launch { repository.removePhoto(id) }
    }

    fun delete(id: String) {
        applicationScope.launch { repository.delete(id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as MainApplication).container
                    MyBikesViewModel(
                        container.bikes,
                        container.media::resolve,
                        container.applicationScope,
                        container.photosLeftOut,
                    )
                }
            }
    }
}
