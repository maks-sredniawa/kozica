package app.kozica.ui.map

import android.annotation.SuppressLint
import android.location.Location
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kozica.MainApplication
import app.kozica.data.AppPreferences
import app.kozica.data.BikeRouteFilter
import app.kozica.data.HikeEntity
import app.kozica.data.HikePhotoEntity
import app.kozica.data.MapCamera
import app.kozica.data.Mode
import app.kozica.data.SavedRoute
import app.kozica.data.gpx.Gpx
import app.kozica.data.gpx.hikeGpx
import app.kozica.data.gpx.trailGpx
import app.kozica.data.hikes.Climb
import app.kozica.data.hikes.Height
import app.kozica.data.hikes.Pace
import app.kozica.data.hikes.Pin
import app.kozica.data.hikes.ProfilePoint
import app.kozica.data.hikes.Track
import app.kozica.data.hikes.along
import app.kozica.data.hikes.climb
import app.kozica.data.hikes.decodeTrack
import app.kozica.data.hikes.profileOf
import app.kozica.data.hikes.trim
import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionState
import app.kozica.data.routes.BikeRouteDetail
import app.kozica.data.routes.DemandingStretch
import app.kozica.data.routes.FOLLOWED_KEPT_MILLIS
import app.kozica.data.routes.FollowFix
import app.kozica.data.routes.FollowedLine
import app.kozica.data.routes.FollowedRoute
import app.kozica.data.routes.Leaving
import app.kozica.data.routes.Left
import app.kozica.data.routes.Place
import app.kozica.data.routes.PlaceSearch
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.Progress
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.SearchResult
import app.kozica.data.routes.TrailDetail
import app.kozica.data.routes.after
import app.kozica.data.routes.boxAway
import app.kozica.record.Recording
import app.kozica.ui.common.GpxExport
import app.kozica.ui.common.GpxExports
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * @param regions null until the first read of the phone's Regions.
 * @param relief the relief pack, null until its first read or in a build without one.
 * @param style the expanded style JSON, null until the template is read.
 * @param camera where a new map view opens, null until the stored camera is read.
 * @param location null until the first resume reads it.
 * @param fix the newest position, null until the first arrives or while location cannot be read.
 * @param recording the Hike being recorded, null when none is.
 * @param track the recording Hike's Track so far, drawn live (docs/phase-6.md § 2).
 * @param hike the Hike the Hikes tab asked the map to show, null when none is (docs/phase-6.md § 4).
 * @param trail the Trail picked on the map, or the Trails a tap offers (docs/phase-6c.md § 3).
 * @param bikeRoute the same for Bike routes, in Cycling (docs/phase-7a.md § 4); at most one of the two is selected.
 */
data class MapUiState(
    val regions: List<Region>? = null,
    val relief: Region? = null,
    val style: String? = null,
    val camera: MapCamera? = null,
    val location: LocationRead? = null,
    val fix: Location? = null,
    val mode: Mode = Mode.HIKING,
    val bikeRouteFilter: BikeRouteFilter = BikeRouteFilter.ALL,
    val search: SearchUiState = SearchUiState(),
    val route: RouteUiState = RouteUiState(),
    val recording: Recording? = null,
    val track: Track = emptyList(),
    val hike: HikeUiState? = null,
    val trail: TrailUiState = TrailUiState(),
    val bikeRoute: BikeRouteUiState = BikeRouteUiState(),
    val pace: Pace? = null,
) {
    private val ready: List<RegionState.Ready> get() = regions.orEmpty().mapNotNull { it.state as? RegionState.Ready }

    /** The Regions and the relief, which arrive the same way and are counted together. */
    val packs: List<Region>? get() = regions?.let { it + listOfNotNull(relief) }

    val progress: MapProgress? get() = packs?.let(::mapProgress)

    val reliefShown: Boolean get() = relief?.state is RegionState.Ready

    /** The stalest ready Region's date, because the map is no fresher than its oldest part. */
    val osmDate: Instant? get() = ready.mapNotNull { it.osmDate }.minOrNull()

    /** The recording's Track and the shown Hike's, each stretch its own line. */
    val drawnTrack: Track get() = track + hike?.drawn.orEmpty()

    /** A selected Trail or Bike route sets a planned Route aside until it is closed. */
    val selection: Boolean get() = trail.shown != null || bikeRoute.shown != null

    /** A planned Route, a shown Hike or a selection fades the rest of the map (docs/phase-5.md § 4). */
    val faded: Boolean get() = route.planned != null || hike != null || selection
}

/**
 * A Hike shown on the map, its sheet's whole content.
 *
 * @param along metres along the Track to each of its points, in order.
 * @param climb null when no ascent is stored and no height could be read, so none is shown.
 * @param keep the metres Trim keeps, null while not trimming.
 * @param photos in the order they were taken (docs/phase-6.md § 6).
 */
data class HikeUiState(
    val id: String,
    val name: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val metres: Double,
    val movingSeconds: Long,
    val track: Track,
    val along: List<Double>,
    val climb: Climb?,
    val heights: List<Height?> = emptyList(),
    val profile: List<ProfilePoint> = emptyList(),
    val note: String? = null,
    val photos: List<HikePhoto> = emptyList(),
    val keep: ClosedFloatingPointRange<Double>? = null,
) {
    val photoCount: Int get() = photos.size

    /** The points [keep] holds, by their index among the Track's points in order; null when fewer than two. */
    val kept: IntRange?
        get() {
            val keep = keep ?: return null
            val total = along.lastOrNull() ?: return null
            // The slider hands back floats, so a handle left at an end can land a float step inside it.
            val slack = Math.ulp(total.toFloat()).toDouble()
            val atEnd = keep.endInclusive >= total - slack
            val first = if (keep.start <= slack) 0 else along.indexOfFirst { it >= keep.start }
            val last = if (atEnd) along.lastIndex else along.indexOfLast { it <= keep.endInclusive }
            return (first..last).takeIf { first >= 0 && last > first }
        }

    /** Whether Trim would cut anything. */
    val cuts: Boolean get() = kept?.let { it.first > 0 || it.last < along.lastIndex } == true

    /** The Track, or while trimming the part that stays. */
    val drawn: Track get() = if (keep == null) track else kept?.let { track.trim(it.first, it.last) }.orEmpty()
}

/** A photo of a Hike, its paths resolved; [pin] is null for one with no place on the Track. */
data class HikePhoto(
    val id: String,
    val photo: File,
    val thumbnail: File,
    val caption: String?,
    val pin: Pin?,
)

/** Adds each of [sources] in turn, going on past one that fails; returns how many failed. */
internal suspend fun <T> addEach(
    sources: List<T>,
    add: suspend (T) -> Unit,
): Int =
    sources.count { source ->
        try {
            add(source)
            false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            true
        }
    }

/** The stored ascent, else one read from [heights] when at least one height came back. */
internal fun shownClimb(
    stored: Climb?,
    heights: List<Height?>,
): Climb? = stored ?: heights.takeIf { read -> read.any { it != null } }?.let(::climb)

/**
 * @param available null until the name index has been looked for, false in a build without one.
 * @param offered the result picked, centred on the map and offered as a point.
 * @param otherModeMatches how many routes the other Mode would find for the same text, behind the switch's seam.
 */
data class SearchUiState(
    val available: Boolean? = null,
    val results: List<SearchResult> = emptyList(),
    val offered: Place? = null,
    val otherModeMatches: Int = 0,
)

/**
 * @param planned the Route last planned, null under two points or in a build with no network
 *   ([unavailable]). While [planning] it is the one before the latest change, kept so the map does not
 *   flash back to unfaded between the two, but it no longer matches [points].
 * @param naming whether the Save dialog is open. Held here rather than in the sheet's saved state, so
 *   it goes with the Route when a dead process comes back without one.
 */
data class RouteUiState(
    val points: List<RoutePoint> = emptyList(),
    val planned: PlannedRoute? = null,
    val stretches: List<NamedStretch> = emptyList(),
    val unavailable: Boolean = false,
    val planning: Boolean = false,
    val savedId: String? = null,
    val name: String? = null,
    val naming: Boolean = false,
    val suggestedName: String? = null,
    val following: FollowingUiState? = null,
    val followingNote: FollowingNote? = null,
)

/**
 * docs/phase-6b.md § 3: what is left from [progress], which is null until the first fix that counts;
 * § 4: whether the user is leaving the Route, and the leg back to it once asked for.
 */
data class FollowingUiState(
    val progress: Progress? = null,
    val left: Left,
    val leaving: Leaving? = null,
    val back: LegBack? = null,
)

/** The leg back to the Route, planned by the router as it is and never part of the Route. */
sealed interface LegBack {
    data object Planning : LegBack

    data class Found(
        val planned: PlannedRoute,
    ) : LegBack

    data object NoWay : LegBack
}

/** What the sheet says about following, until the next change: why it ended, or that "Find me" found nothing. */
enum class FollowingNote { ENDED, ROUTE_CHANGED, NOT_FOUND }

/**
 * @param shown the selected Trail, which sets a planned Route aside until it is closed.
 * @param choices the Trails under a tap that found several, until one is picked.
 * @param whole every Section of [shown]'s Long-distance Trail, once asked for; shown with it.
 * @param fits counts the asks to fit the camera to the highlighted Trails, across selections, so a new one never reads as an ask.
 * @param planningRoute while the points of a Route along [shown] are being found.
 */
data class TrailUiState(
    val shown: TrailDetail? = null,
    val choices: List<TrailDetail> = emptyList(),
    val whole: List<TrailDetail> = emptyList(),
    val fits: Int = 0,
    val planningRoute: Boolean = false,
) {
    val highlighted: List<TrailDetail> get() = whole.ifEmpty { listOfNotNull(shown) }
}

/** [TrailUiState] for Bike routes, which have no Route to plan along them until phase 7b. */
data class BikeRouteUiState(
    val shown: BikeRouteDetail? = null,
    val choices: List<BikeRouteDetail> = emptyList(),
    val whole: List<BikeRouteDetail> = emptyList(),
    val fits: Int = 0,
) {
    val highlighted: List<BikeRouteDetail> get() = whole.ifEmpty { listOfNotNull(shown) }
}

/** A demanding stretch with the places that name its two ends, either null where nothing is near. */
data class NamedStretch(
    val stretch: DemandingStretch,
    val from: String?,
    val to: String?,
)

class MapViewModel(
    regions: Flow<List<Region>>,
    private val preferences: AppPreferences,
    private val readAsset: (path: String) -> String,
    private val applicationScope: CoroutineScope,
    private val positions: (approximateOnly: Boolean) -> Flow<Location>,
    private val fetch: suspend (ids: List<String>) -> Unit,
    private val confirmFetch: suspend (ActivityResultLauncher<IntentSenderRequest>) -> Boolean,
    relief: Flow<Region?> = flowOf(null),
    private val places: suspend () -> PlaceSearch? = { null },
    private val planRoute: suspend (List<RoutePoint>) -> PlannedRoute? = { null },
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val routeToShow: MutableStateFlow<String?> = MutableStateFlow(null),
    private val loadRoute: suspend (id: String) -> SavedRoute? = { null },
    private val saveRoute: suspend (id: String?, name: String, points: List<RoutePoint>) -> String = { _, _, _ -> "" },
    private val observeName: (id: String) -> Flow<String?> = { emptyFlow() },
    private val recording: Flow<Recording?> = flowOf(null),
    private val observeTrack: (hikeId: String) -> Flow<Track> = { flowOf(emptyList()) },
    private val hikeToShow: MutableStateFlow<String?> = MutableStateFlow(null),
    private val observeHike: (id: String) -> Flow<HikeEntity?> = { flowOf(null) },
    private val observePhotos: (id: String) -> Flow<List<HikePhotoEntity>> = { flowOf(emptyList()) },
    private val resolveMedia: (relativePath: String) -> File = ::File,
    private val heights: suspend (Track) -> List<Height?> = { track -> track.flatten().map { null } },
    private val renameHike: suspend (id: String, name: String) -> Unit = { _, _ -> },
    private val trimHike: suspend (id: String, first: Int, last: Int) -> Unit = { _, _, _ -> },
    private val deleteHike: suspend (id: String) -> Unit = {},
    private val saveNote: suspend (id: String, note: String) -> Unit = { _, _ -> },
    private val savePhoto: suspend (hikeId: String, source: Uri) -> Unit = { _, _ -> },
    private val saveCaption: suspend (photoId: String, caption: String) -> Unit = { _, _ -> },
    private val savePinRemoved: suspend (photoId: String) -> Unit = {},
    private val deleteStoredPhoto: suspend (photoId: String) -> Unit = {},
    private val photosLeftOut: MutableStateFlow<Int> = MutableStateFlow(0),
    private val writeGpx: suspend (Gpx, LocalDate) -> File = { _, _ -> error("No GPX in this view model") },
    private val loadTrail: (id: Long) -> TrailDetail? = { null },
    private val sectionsOf: (longDistance: Long) -> List<Long> = { emptyList() },
    private val routeAlong: suspend (TrailDetail) -> List<RoutePoint>? = { null },
    private val loadBikeRoute: (id: Long) -> BikeRouteDetail? = { null },
    private val bikeRouteWhole: (parent: Long) -> List<Long> = { emptyList() },
    pace: Flow<Pace?> = flowOf(null),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    // Paired before stateIn, so the style is never built from Regions whose relief has not been read yet.
    private val packs: StateFlow<Pair<List<Region>, Region?>?> =
        combine(regions, relief, ::Pair).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val camera = MutableStateFlow<MapCamera?>(null)

    private val dark = MutableStateFlow<Boolean?>(null)

    private val style: StateFlow<String?> =
        combine(
            dark.filterNotNull().map { readAsset(styleTemplate(it)) },
            packs
                .filterNotNull()
                .map { (regions, relief) -> regions.readyArchives() to (relief?.state as? RegionState.Ready)?.archive }
                .distinctUntilChanged(),
        ) { template, (archives, relief) -> expandStyle(template, archives, relief) }
            .flowOn(io)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val location = MutableStateFlow<LocationRead?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    private val fix: Flow<Location?> =
        location
            .flatMapLatest { read ->
                when {
                    read == null || !read.on -> flowOf(null)
                    read.grant == LocationGrant.NONE -> flowOf(null)
                    else -> positions(read.grant == LocationGrant.APPROXIMATE)
                }
            }.onStart { emit(null) }

    private val search = MutableStateFlow(SearchUiState())

    private val route = MutableStateFlow(RouteUiState())

    private var searching: Job? = null

    private var planning: Job? = null

    private var followed: FollowedLine? = null

    private var planningBack: Job? = null

    private var resuming: Job? = null

    private val followedWrites = Channel<FollowedRoute?>(Channel.CONFLATED)

    private var keepingFollowed = false

    // Changes whenever the map's Route is replaced, so a Save that returns late lands only on its own.
    private var routeGeneration = 0

    @OptIn(ExperimentalCoroutinesApi::class)
    private val track: Flow<Track> =
        recording
            .map { it?.hikeId }
            .distinctUntilChanged()
            .flatMapLatest { id -> id?.let(observeTrack) ?: flowOf(emptyList()) }

    private val shownHike = MutableStateFlow<String?>(null)

    private val trail = MutableStateFlow(TrailUiState())

    private val bikeRoute = MutableStateFlow(BikeRouteUiState())

    private var tapping: Job? = null

    private var planningAlong: Job? = null

    private var loadingLongDistance: Job? = null

    private val hikeKeep = MutableStateFlow<ClosedFloatingPointRange<Double>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val hike: Flow<HikeUiState?> =
        combine(
            shownHike.flatMapLatest { id ->
                if (id == null) {
                    flowOf(null)
                } else {
                    combine(observeHike(id), observePhotos(id), ::Pair).mapLatest { (hike, photos) ->
                        hike?.let { hikeState(it, photos) }
                    }
                }
            },
            hikeKeep,
        ) { hike, keep -> hike?.copy(keep = keep) }

    private val mode: StateFlow<Mode> =
        preferences.mapMode
            .map(::modeOf)
            .stateIn(viewModelScope, SharingStarted.Eagerly, Mode.HIKING)

    private var searchText = ""
    private val bikeRouteFilter = MutableStateFlow(BikeRouteFilter.ALL)

    val state: StateFlow<MapUiState> =
        combine(
            combine(packs, style, camera, location, fix) { packs, style, camera, location, fix ->
                MapUiState(packs?.first, packs?.second, style, camera, location, fix)
            },
            combine(combine(search, pace, ::Pair), combine(mode, bikeRouteFilter, ::Pair), ::Pair),
            combine(route, hike.onStart { emit(null) }, combine(trail, bikeRoute, ::Pair), ::Triple),
            recording,
            track.onStart { emit(emptyList()) },
        ) { map, (found, chosen), (route, hike, selection), recording, track ->
            val (search, pace) = found
            val (trail, bikeRoute) = selection
            val (mode, bikeRouteFilter) = chosen
            map.copy(
                mode = mode,
                bikeRouteFilter = bikeRouteFilter,
                search = search,
                route = route,
                recording = recording,
                track = track,
                hike = hike,
                trail = trail,
                bikeRoute = bikeRoute,
                pace = pace,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun setMode(mode: Mode) {
        if (mode != this.mode.value) closeSelection()
        applicationScope.launch { preferences.setMapMode(mode) }
    }

    fun setBikeRouteFilter(filter: BikeRouteFilter) {
        bikeRouteFilter.value = filter
    }

    init {
        viewModelScope.launch { mode.drop(1).collect { if (searchText.isNotBlank()) search(searchText) } }
        viewModelScope.launch { camera.compareAndSet(null, preferences.mapCamera.first() ?: TATRY) }
        viewModelScope.launch {
            val found = withContext(io) { places() } != null
            search.update { it.copy(available = found) }
        }
        viewModelScope.launch {
            routeToShow.filterNotNull().collect { id ->
                loadRoute(id)?.let(::show)
                routeToShow.compareAndSet(id, null)
            }
        }
        viewModelScope.launch {
            hikeToShow.filterNotNull().collect { id ->
                showHike(id)
                hikeToShow.compareAndSet(id, null)
            }
        }
        viewModelScope.launch { followSavedName() }
        viewModelScope.launch {
            val stored = preferences.readFollowedRoute()
            applicationScope.launch { writeFollowed(stored) }
            stored?.let { resume(it) }
            keepFollowed()
        }
        // Play fetches fast-follow packs by itself; this covers one it has not started and a Region a
        // later build adds. A failed or cancelled one waits for Retry (docs/phase-2c.md § 1).
        viewModelScope.launch {
            val missing = toFetch(packs.filterNotNull().first().flattened())
            if (missing.isNotEmpty()) applicationScope.launch { fetch(missing) }
        }
    }

    fun retry() {
        val failed =
            packs.value
                ?.flattened()
                .orEmpty()
                .filter { it.state is RegionState.Failed }
                .map { it.id }
        applicationScope.launch { fetch(failed) }
    }

    /** Nothing awaiting confirmation means the state shown was stale, so what showed it is asked for again. */
    fun confirm(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val waiting =
            packs.value
                ?.flattened()
                .orEmpty()
                .filter { it.state == RegionState.WaitingForWifi || it.state == RegionState.NeedsConfirmation }
                .map { it.id }
        viewModelScope.launch { if (!confirmFetch(launcher)) applicationScope.launch { fetch(waiting) } }
    }

    fun cameraIdle(at: MapCamera) {
        camera.value = at
    }

    fun darkTheme(on: Boolean) {
        dark.value = on
    }

    fun locationRead(read: LocationRead) {
        location.value = read
    }

    fun storeCamera() {
        val at = camera.value ?: return
        // Back finishes the activity, and that cancels viewModelScope before this write would run.
        applicationScope.launch { preferences.setMapCamera(at) }
    }

    /** Results nearest the map's centre first, so the newest text wins over a slower older one. */
    fun search(text: String) {
        searching?.cancel()
        searchText = text
        val centre = camera.value ?: TATRY
        val current = mode.value
        searching =
            viewModelScope.launch {
                val (results, otherMode) =
                    if (text.isBlank()) {
                        emptyList<SearchResult>() to 0
                    } else {
                        withContext(io) {
                            val index = places()
                            index?.searchAll(text, centre.latitude, centre.longitude, mode = current).orEmpty() to
                                (index?.let { otherModeMatches { it.matchesIn(current.other, text) } } ?: 0)
                        }
                    }
                search.update { it.copy(results = results, otherModeMatches = otherMode) }
            }
    }

    /**
     * A place closes any selected Trail, bringing the Route back, and is centred and offered as a
     * point; a Trail is selected and fitted, and a Long-distance Trail by its Section nearest the
     * centre, shown whole (docs/phase-6c.md § 4).
     */
    fun pick(result: SearchResult) {
        searching?.cancel()
        searchText = ""
        search.update { it.copy(results = emptyList(), otherModeMatches = 0) }
        when (result) {
            is SearchResult.AtPlace -> {
                closeSelection()
                search.update { it.copy(offered = result.place) }
            }
            is SearchResult.OnTrail -> pickTrail(result.facts.id)
            is SearchResult.LongDistance -> pickLongDistance(result.id)
            is SearchResult.OnBikeRoute -> pickBikeRoute(result.facts.id)
            is SearchResult.BikeRouteParent -> pickBikeRouteParent(result.id)
        }
    }

    private fun pickTrail(id: Long) {
        tapping?.cancel()
        tapping =
            viewModelScope.launch {
                val found = withContext(io) { loadTrail(id) } ?: return@launch
                showTrail(found)
                trail.update { it.copy(fits = it.fits + 1) }
            }
    }

    private fun pickBikeRoute(id: Long) {
        tapping?.cancel()
        tapping =
            viewModelScope.launch {
                val found = withContext(io) { loadBikeRoute(id) } ?: return@launch
                showBikeRoute(found)
                bikeRoute.update { it.copy(fits = it.fits + 1) }
            }
    }

    private fun pickBikeRouteParent(id: Long) {
        val centre = camera.value ?: TATRY
        tapping?.cancel()
        tapping =
            viewModelScope.launch {
                val sections = withContext(io) { bikeRouteWhole(id).mapNotNull(loadBikeRoute) }
                val nearest =
                    sections.minByOrNull {
                        with(it.facts) { boxAway(centre.latitude, centre.longitude, south, west, north, east) }
                    } ?: return@launch
                showBikeRoute(nearest)
                bikeRoute.update { it.copy(whole = sections, fits = it.fits + 1) }
            }
    }

    private fun pickLongDistance(id: Long) {
        val centre = camera.value ?: TATRY
        tapping?.cancel()
        tapping =
            viewModelScope.launch {
                val sections = withContext(io) { sectionsOf(id).mapNotNull(loadTrail) }
                val nearest =
                    sections.minByOrNull {
                        with(it.facts) { boxAway(centre.latitude, centre.longitude, south, west, north, east) }
                    } ?: return@launch
                showTrail(nearest)
                trail.update { it.copy(whole = sections, fits = it.fits + 1) }
            }
    }

    fun dismissOffer() {
        search.update { it.copy(offered = null) }
    }

    fun addOffered() {
        val place = search.value.offered ?: return
        dismissOffer()
        closeHike()
        changePoints { it + RoutePoint(place.latitude, place.longitude, place.name) }
    }

    /** A long-press: the point joins at once, in press order, then takes the name of the place nearest it, if any is near. */
    fun addPoint(
        latitude: Double,
        longitude: Double,
    ) {
        val point = RoutePoint(latitude, longitude)
        closeHike()
        closeSelection()
        changePoints { it + point }
        viewModelScope.launch {
            val label = withContext(io) { places()?.nearest(latitude, longitude)?.name } ?: return@launch
            route.update { state ->
                state.copy(points = state.points.map { if (it === point) it.copy(label = label) else it })
            }
        }
    }

    fun removePoint(index: Int) {
        changePoints { points -> points.filterIndexed { i, _ -> i != index } }
    }

    fun movePoint(
        from: Int,
        to: Int,
    ) {
        changePoints { points ->
            if (to !in points.indices) return@changePoints points
            points.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    fun reverse() {
        changePoints { it.reversed() }
    }

    /** Replaces whatever is on the map with a saved Route, planned again from today's network. */
    private fun show(saved: SavedRoute) {
        dismissOffer()
        closeHike()
        routeGeneration++
        unfollow()
        route.update { RouteUiState(savedId = saved.id, name = saved.name) }
        changePoints { saved.points }
    }

    /** A rename in the Routes tab renames the Route on the map; a delete leaves its points unsaved. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun followSavedName() {
        route
            .map { it.savedId }
            .distinctUntilChanged()
            .flatMapLatest { id -> id?.let { observeName(it).map { name -> id to name } } ?: emptyFlow() }
            .collect { (id, name) ->
                route.update {
                    when {
                        it.savedId != id -> it
                        name == null -> it.copy(savedId = null, name = null)
                        else -> it.copy(name = name)
                    }
                }
            }
    }

    /** Replaces whatever is on the map with a Hike from the Hikes tab. */
    private fun showHike(id: String) {
        dismissOffer()
        planning?.cancel()
        routeGeneration++
        unfollow()
        route.value = RouteUiState()
        hikeKeep.value = null
        readHeights = null
        closeSelection()
        shownHike.value = id
    }

    /**
     * docs/phase-6c.md § 3: a tap on one Trail selects it, on several offers them, and on none closes
     * whatever Trail is selected or offered.
     */
    fun tapTrails(ids: List<Long>) {
        tapping?.cancel()
        planningAlong?.cancel()
        loadingLongDistance?.cancel()
        if (ids.isEmpty()) {
            closeSelection()
            return
        }
        tapping =
            viewModelScope.launch {
                val found = withContext(io) { ids.mapNotNull(loadTrail) }
                when (found.size) {
                    0 -> Unit
                    1 -> showTrail(found.single())
                    else -> trail.update { it.copy(choices = found) }
                }
            }
    }

    /** The same for Bike routes in Cycling (docs/phase-7a.md § 4). */
    fun tapBikeRoutes(ids: List<Long>) {
        tapping?.cancel()
        planningAlong?.cancel()
        loadingLongDistance?.cancel()
        if (ids.isEmpty()) {
            closeSelection()
            return
        }
        tapping =
            viewModelScope.launch {
                val found = withContext(io) { ids.mapNotNull(loadBikeRoute) }
                when (found.size) {
                    0 -> Unit
                    1 -> showBikeRoute(found.single())
                    else -> bikeRoute.update { it.copy(choices = found) }
                }
            }
    }

    fun chooseBikeRoute(picked: BikeRouteDetail) = showBikeRoute(picked)

    fun dismissBikeRouteChoices() = bikeRoute.update { it.copy(choices = emptyList()) }

    private fun showBikeRoute(picked: BikeRouteDetail) {
        if (shownHike.value != null) closeHike()
        dismissOffer()
        loadingLongDistance?.cancel()
        planningAlong?.cancel()
        trail.update { TrailUiState(fits = it.fits) }
        bikeRoute.update { BikeRouteUiState(shown = picked, fits = it.fits) }
    }

    fun showWholeBikeRoute() = bikeRoute.update { it.copy(whole = emptyList(), fits = it.fits + 1) }

    /** Every Section of the selected one's parent, or of the selected parent, highlighted together. */
    fun showBikeRouteSections() {
        val shown = bikeRoute.value.shown ?: return
        val parent = shown.wholeId ?: return
        loadingLongDistance?.cancel()
        loadingLongDistance =
            viewModelScope.launch {
                val sections = withContext(io) { bikeRouteWhole(parent).mapNotNull(loadBikeRoute) }
                bikeRoute.update { if (it.shown?.id != shown.id) it else it.copy(whole = sections, fits = it.fits + 1) }
            }
    }

    fun chooseTrail(picked: TrailDetail) = showTrail(picked)

    fun dismissTrailChoices() = trail.update { it.copy(choices = emptyList()) }

    /** A shown Hike gives way, a planned Route is set aside, and a recording goes on (docs/phase-6c.md § 3). */
    private fun showTrail(picked: TrailDetail) {
        if (shownHike.value != null) closeHike()
        dismissOffer()
        loadingLongDistance?.cancel()
        planningAlong?.cancel()
        bikeRoute.update { BikeRouteUiState(fits = it.fits) }
        trail.update { TrailUiState(shown = picked, fits = it.fits) }
    }

    fun closeSelection() {
        planningAlong?.cancel()
        clearSelection()
    }

    private fun clearSelection() {
        tapping?.cancel()
        loadingLongDistance?.cancel()
        trail.update { TrailUiState(fits = it.fits) }
        bikeRoute.update { BikeRouteUiState(fits = it.fits) }
    }

    /**
     * Replaces the Route set aside, unsaved changes and all, with one the Trail's points hold on it,
     * offered for saving under [title] (docs/phase-6c.md § 5).
     */
    fun planAlongTrail(title: String) {
        val shown = trail.value.shown ?: return
        planningAlong?.cancel()
        trail.update { it.copy(planningRoute = true) }
        planningAlong =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                val self = coroutineContext[Job]
                try {
                    val points = withContext(compute) { routeAlong(shown) }?.takeIf { it.size >= 2 }
                    if (points == null || trail.value.shown?.id != shown.id) return@launch
                    clearSelection()
                    planning?.cancel()
                    routeGeneration++
                    route.value = RouteUiState(suggestedName = title)
                    changePoints { points }
                    labelPoints(points)
                } finally {
                    if (planningAlong === self) trail.update { it.copy(planningRoute = false) }
                }
            }
        planningAlong?.start()
    }

    // Its own job, so a Trail selected while the labels are read does not lose them.
    private fun labelPoints(points: List<RoutePoint>) {
        viewModelScope.launch {
            val labels = withContext(io) { points.map { places()?.nearest(it.latitude, it.longitude)?.name } }
            route.update { state ->
                if (state.points !=
                    points
                ) {
                    state
                } else {
                    state.copy(points = points.zip(labels) { p, l -> p.copy(label = l) })
                }
            }
        }
    }

    /** The shown Trail's walk as a GPX file for the share sheet, named [title]. */
    fun exportTrailGpx(title: String) {
        val shown = trail.value.shown ?: return
        gpxExports.start(LocalDate.now()) { trailGpx(title, shown.line) }
    }

    fun showWholeTrail() = trail.update { it.copy(whole = emptyList(), fits = it.fits + 1) }

    /** Every Section of the selected one's Long-distance Trail, highlighted together. */
    fun showLongDistance() {
        val section = trail.value.shown ?: return
        val longDistance = section.facts.longDistanceId ?: return
        loadingLongDistance?.cancel()
        loadingLongDistance =
            viewModelScope.launch {
                val sections = withContext(io) { sectionsOf(longDistance).mapNotNull(loadTrail) }
                trail.update { if (it.shown?.id != section.id) it else it.copy(whole = sections, fits = it.fits + 1) }
            }
    }

    fun closeHike() {
        shownHike.value = null
        hikeKeep.value = null
    }

    // The last Track's heights, so a note or a photo does not read the relief again.
    private var readHeights: Pair<ByteArray, List<Height?>>? = null

    private suspend fun hikeState(
        hike: HikeEntity,
        photos: List<HikePhotoEntity>,
    ): HikeUiState? {
        val endedAt = hike.endedAt ?: return null
        val bytes = hike.track ?: return null
        val track = decodeTrack(bytes)
        val heights =
            readHeights?.takeIf { it.first.contentEquals(bytes) }?.second
                ?: heightsOf(track).also { readHeights = bytes to it }
        val stored =
            hike.ascentMetres?.let { ascent -> Climb(ascent, hike.descentMetres ?: 0.0, hike.ascentFromGps) }
        return HikeUiState(
            id = hike.id,
            name = hike.name,
            startedAt = hike.startedAt,
            endedAt = endedAt,
            metres = hike.metres,
            movingSeconds = hike.movingSeconds,
            track = track,
            along = track.along(),
            climb = shownClimb(stored, heights),
            heights = heights,
            profile = profileOf(track, heights),
            note = hike.note,
            photos =
                photos.map {
                    HikePhoto(
                        id = it.id,
                        photo = resolveMedia(it.photoPath),
                        thumbnail = resolveMedia(it.thumbnailPath),
                        caption = it.caption,
                        pin = if (it.latitude != null && it.longitude != null) Pin(it.latitude, it.longitude) else null,
                    )
                },
        )
    }

    // A failed read draws no profile rather than ending the map's state with it.
    private suspend fun heightsOf(track: Track): List<Height?> =
        try {
            withContext(io) { heights(track) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            track.flatten().map { null }
        }

    /** Trim's handles start at the two ends, keeping everything. */
    fun startTrim() {
        val along = state.value.hike?.along ?: return
        hikeKeep.value = 0.0..(along.lastOrNull() ?: 0.0)
    }

    fun trimTo(keep: ClosedFloatingPointRange<Double>) {
        if (hikeKeep.value != null) hikeKeep.value = keep
    }

    fun cancelTrim() {
        hikeKeep.value = null
    }

    fun trim() {
        val hike = state.value.hike ?: return
        val kept = hike.kept ?: return
        hikeKeep.value = null
        applicationScope.launch { trimHike(hike.id, kept.first, kept.last) }
    }

    fun renameHike(name: String) {
        val id = shownHike.value ?: return
        viewModelScope.launch { renameHike(id, name) }
    }

    fun setNote(note: String) {
        val id = shownHike.value ?: return
        applicationScope.launch { saveNote(id, note) }
    }

    /** One at a time, past the screen's life, each appearing in the strip as it is written. */
    fun addPhotos(
        hikeId: String,
        sources: List<Uri>,
    ) {
        if (sources.isEmpty()) return
        applicationScope.launch {
            val failed = addEach(sources) { savePhoto(hikeId, it) }
            if (failed > 0) photosLeftOut.update { it + failed }
        }
    }

    fun setCaption(
        photoId: String,
        caption: String,
    ) {
        applicationScope.launch { saveCaption(photoId, caption) }
    }

    fun unpinPhoto(photoId: String) {
        applicationScope.launch { savePinRemoved(photoId) }
    }

    fun deletePhoto(photoId: String) {
        applicationScope.launch { deleteStoredPhoto(photoId) }
    }

    private val gpxExports = GpxExports(viewModelScope, writeGpx)
    val gpx: StateFlow<GpxExport> = gpxExports.state

    /** The shown Hike as a GPX file for the share sheet (docs/phase-6.md § 8). */
    fun exportHikeGpx() {
        val hike = state.value.hike ?: return
        val pinned = hike.photos.mapNotNull { photo -> photo.pin?.let { it to photo.caption } }
        gpxExports.start(hike.startedAt.atZone(ZoneId.systemDefault()).toLocalDate()) {
            hikeGpx(hike.name, hike.note, hike.track, hike.heights, pinned)
        }
    }

    fun gpxShared() = gpxExports.shared()

    fun dismissGpxFailure() = gpxExports.dismiss()

    fun deleteHike() {
        val id = shownHike.value ?: return
        closeHike()
        // Past the screen's life: Back right after Delete must not leave the Hike behind.
        applicationScope.launch { deleteHike(id) }
    }

    fun showSaveDialog(shown: Boolean) {
        route.update { it.copy(naming = shown) }
    }

    /** Saves the points under [name]: over the Route they were opened from, or as a new one. */
    fun save(name: String) {
        val current = route.updateAndGet { it.copy(naming = false) }
        val generation = routeGeneration
        viewModelScope.launch {
            val id = saveRoute(current.savedId, name, current.points)
            if (generation == routeGeneration) route.update { it.copy(savedId = id, name = name) }
        }
    }

    /** Follow on a complete Route's sheet; Progress waits for the first fix that counts. */
    fun follow() = startFollowing(null)

    private fun startFollowing(progress: Progress?) {
        val current = route.value
        val planned = current.planned?.takeIf { it.complete && !current.planning } ?: return
        val line = FollowedLine(planned).also { followed = it }
        val kept = progress?.let { it.copy(metres = it.metres.coerceIn(0.0, line.metres)) }
        route.update {
            it.copy(
                following = FollowingUiState(progress = kept, left = line.left(kept ?: Progress(0.0, 0))),
                followingNote = null,
            )
        }
    }

    /**
     * docs/phase-6b.md § 3: the Route followed when the app was closed, planned again and followed from
     * its Progress, unless it was last touched over a day ago or the map already shows something else.
     */
    private suspend fun resume(stored: FollowedRoute) {
        if (now() - stored.touchedAtMillis > FOLLOWED_KEPT_MILLIS) return
        if (route.value.points.isNotEmpty() || shownHike.value != null) return
        routeGeneration++
        route.value = RouteUiState(savedId = stored.savedId, name = stored.name)
        changePoints { stored.points }
        val job =
            viewModelScope.launch {
                route.first { !it.planning }
                startFollowing(stored.progress)
            }
        resuming = job
        job.join()
    }

    /** Hands the followed Route to [writeFollowed] as it changes, and null once following ends, for [resume]. */
    private suspend fun keepFollowed() {
        keepingFollowed = true
        route.map(::followedOf).distinctUntilChanged().collect { followedWrites.trySend(it) }
    }

    private fun followedOf(state: RouteUiState): FollowedRoute? =
        state.following?.let { FollowedRoute(state.points, state.name, state.savedId, it.progress) }

    /**
     * The followed Route's only writer, in [applicationScope] so the last value lands even after the
     * screen has gone. An unchanged value keeps its stamp, so a reopen alone never restarts the day.
     */
    private suspend fun writeFollowed(stored: FollowedRoute?) {
        var kept = stored
        for (followed in followedWrites) {
            if (followed == kept?.copy(touchedAtMillis = 0)) continue
            kept = followed?.copy(touchedAtMillis = now())
            preferences.setFollowedRoute(kept)
        }
    }

    // A Stop just before Back may not have reached keepFollowed's collector before its scope was cancelled.
    override fun onCleared() {
        if (keepingFollowed) followedWrites.trySend(followedOf(route.value))
        followedWrites.close()
    }

    fun stopFollowing() {
        unfollow()
        route.update { it.copy(following = null) }
    }

    private fun unfollow() {
        followed = null
        planningBack?.cancel()
        resuming?.cancel()
    }

    fun dismissFollowingNote() {
        route.update { it.copy(followingNote = null) }
    }

    /** Each fix while the map is on screen; one that does not count moves nothing (docs/phase-6b.md § 3). */
    fun followFix(fix: FollowFix) {
        val line = followed ?: return
        val current = route.value.following ?: return
        val metres = line.distance(fix)
        val leaving = current.leaving.after(fix, metres)
        if (leaving != current.leaving) {
            if (leaving == null) planningBack?.cancel()
            route.update { state ->
                state.copy(
                    following =
                        state.following?.let { it.copy(leaving = leaving, back = it.back.takeIf { leaving != null }) },
                )
            }
        }
        line.follow(current.progress, fix)?.let { moveTo(line, it, fix) }
        logFollow(
            fix,
            current.progress,
            metres,
            line.reach(current.progress, fix),
            route.value.following,
            route.value.followingNote,
            state.value.pace,
        )
    }

    /** "Back to the Route": a leg from [fix] to where § 4's rule rejoins the line, planned again on each ask. */
    fun backToRoute(fix: FollowFix?) {
        val line = followed ?: return
        val following = route.value.following ?: return
        if (fix == null) return
        val rejoin = line.rejoin(following.progress, fix)
        planningBack?.cancel()
        route.update { state -> state.copy(following = state.following?.copy(back = LegBack.Planning)) }
        planningBack =
            viewModelScope.launch {
                val planned =
                    withContext(compute) { planRoute(listOf(RoutePoint(fix.latitude, fix.longitude), rejoin)) }
                val back = planned?.takeIf { it.complete }?.let(LegBack::Found) ?: LegBack.NoWay
                route.update { state ->
                    val now = state.following?.takeIf { it.back == LegBack.Planning } ?: return@update state
                    state.copy(following = now.copy(back = back))
                }
            }
    }

    /** "Find me on the Route", from the latest fix, if any; says so when it finds nothing. */
    fun findMeOnRoute(fix: FollowFix?) {
        val line = followed ?: return
        val found = fix?.let { line.findMe(route.value.following?.progress, it) }
        if (fix == null || found == null) {
            route.update { it.copy(followingNote = FollowingNote.NOT_FOUND) }
        } else {
            moveTo(line, found, fix)
        }
    }

    private fun moveTo(
        line: FollowedLine,
        progress: Progress,
        fix: FollowFix,
    ) {
        val current = route.value.following ?: return
        if (fix.counts && line.ended(progress, fix)) {
            unfollow()
            route.update { it.copy(following = null, followingNote = FollowingNote.ENDED) }
        } else if (progress != current.progress) {
            route.update { state ->
                state.copy(
                    following = state.following?.copy(progress = progress, left = line.left(progress)),
                    followingNote = null,
                )
            }
        }
    }

    private fun changePoints(change: (List<RoutePoint>) -> List<RoutePoint>) {
        val stopped = route.value.following != null
        unfollow()
        route.update { it.copy(following = null, followingNote = if (stopped) FollowingNote.ROUTE_CHANGED else null) }
        val points =
            route
                .updateAndGet {
                    val points = change(it.points)
                    if (points.isEmpty()) {
                        RouteUiState()
                    } else if (points.size < 2) {
                        it.copy(points = points, planned = null, stretches = emptyList(), planning = false)
                    } else {
                        it.copy(points = points, planning = true)
                    }
                }.points
        if (points.isEmpty()) routeGeneration++
        planning?.cancel()
        if (points.size < 2) return
        planning =
            viewModelScope.launch {
                val planned = withContext(compute) { planRoute(points) }
                if (planned == null) {
                    route.update {
                        it.copy(
                            planned = null,
                            stretches = emptyList(),
                            unavailable = true,
                            planning = false,
                        )
                    }
                    return@launch
                }
                val stretches = withContext(io) { name(planned) }
                route.update {
                    it.copy(planned = planned, stretches = stretches, unavailable = false, planning = false)
                }
            }
    }

    private suspend fun name(planned: PlannedRoute): List<NamedStretch> {
        val index = places()
        return planned.demanding.map { stretch ->
            fun at(metres: Double) =
                planned.line.firstOrNull { it.metres >= metres }?.let {
                    index
                        ?.nearest(
                            it.latitude,
                            it.longitude,
                        )?.name
                }
            NamedStretch(stretch, at(stretch.fromMetres), at(stretch.toMetres))
        }
    }

    companion object {
        /** The first launch's camera, before any is stored: the Tatry at a zoom where Trails draw. */
        private val TATRY = MapCamera(latitude = 49.23, longitude = 20.0, zoom = 11.0)

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    MapViewModel(
                        app.container.regions,
                        app.preferences,
                        { path ->
                            app.assets
                                .open(path)
                                .bufferedReader()
                                .use { it.readText() }
                        },
                        app.container.applicationScope,
                        app.container::positions,
                        app.container.regionFiles::download,
                        app.container.regionFiles::confirm,
                        app.container.relief,
                        app.container::places,
                        app.container::planRoute,
                        routeToShow = app.container.routeToShow,
                        loadRoute = app.container.routes::get,
                        saveRoute = app.container.routes::save,
                        observeName = app.container.routes::observeName,
                        recording = app.container.recorder.recording,
                        observeTrack = app.container.hikes::observeTrack,
                        hikeToShow = app.container.hikeToShow,
                        observeHike = app.container.hikes::observe,
                        observePhotos = app.container.hikes::observePhotos,
                        resolveMedia = app.container.media::resolve,
                        heights = app.container.hikeHeights::of,
                        renameHike = app.container.hikes::rename,
                        trimHike = app.container.hikes::trim,
                        deleteHike = app.container.hikes::delete,
                        saveNote = app.container.hikes::setNote,
                        savePhoto = { id, source -> app.container.hikes.addPhoto(id, source) },
                        saveCaption = app.container.hikes::setCaption,
                        savePinRemoved = app.container.hikes::unpin,
                        deleteStoredPhoto = app.container.hikes::deletePhoto,
                        photosLeftOut = app.container.photosLeftOut,
                        writeGpx = app.container.gpxFiles::write,
                        loadTrail = app.container::trail,
                        sectionsOf = app.container::sections,
                        routeAlong = app.container::routeAlong,
                        loadBikeRoute = app.container::bikeRoute,
                        bikeRouteWhole = app.container::bikeRouteWhole,
                        pace = app.container.pace,
                    )
                }
            }
    }
}

private fun Pair<List<Region>, Region?>.flattened(): List<Region> = first + listOfNotNull(second)

private fun List<Region>.readyArchives(): List<Pair<String, File>> =
    mapNotNull { region -> (region.state as? RegionState.Ready)?.let { region.id to it.archive } }
