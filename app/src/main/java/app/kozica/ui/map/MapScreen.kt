package app.kozica.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PointF
import android.graphics.RectF
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kozica.R
import app.kozica.data.MapCamera
import app.kozica.data.Mode
import app.kozica.data.routes.FollowFix
import app.kozica.theme.LocalDarkTheme
import app.kozica.theme.Spacing
import app.kozica.ui.appViewModelExtras
import app.kozica.ui.common.WarningBanner
import app.kozica.ui.common.osmDateLine
import app.kozica.ui.common.shareGpx
import app.kozica.ui.common.startActivitySafely
import app.kozica.ui.hikes.HikeSheet
import app.kozica.ui.hikes.PhotoViewer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.time.Instant
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

/** Home: the map, drawn by MapLibre from the Regions on the phone (docs/phase-2.md § 2). */
@Composable
fun MapScreen(
    onRecord: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSafety: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(factory = MapViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val gpx by viewModel.gpx.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    LaunchedEffect(dark) { viewModel.darkTheme(dark) }
    LaunchedEffect(gpx.file) {
        gpx.file?.let { file ->
            context.shareGpx(file)
            viewModel.gpxShared()
        }
    }
    // Saved state rather than the ViewModel's, so the camera also survives process death.
    var shown by rememberSaveable(stateSaver = CameraSaver) { mutableStateOf<MapCamera?>(null) }
    val mapView = rememberMapView(onStop = viewModel::storeCamera)
    val confirmation = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    val map by produceState<MapLibreMap?>(null, mapView) {
        mapView.getMapAsync { map ->
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isLogoEnabled = false
            value = map
        }
    }

    // "Denied" and "not asked" read the same until a denial comes back (docs/phase-2.md § 4).
    var denied by rememberSaveable { mutableStateOf(false) }
    var preciseRefused by rememberSaveable { mutableStateOf(false) }
    var askingForPrecise by remember { mutableStateOf(false) }
    var centreWanted by remember { mutableStateOf(false) }
    // docs/phase-6b.md § 3: while following, each fix recentres the map until the user pans it.
    var keepInView by remember { mutableStateOf(false) }
    var followWanted by rememberSaveable { mutableStateOf(false) }
    var needsPrecise by rememberSaveable { mutableStateOf(false) }
    var positionOutside by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        viewModel.locationRead(context.readLocation())
        onPauseOrDispose {}
    }
    // The quick-settings tile flips the switch without pausing the activity.
    LifecycleStartEffect(Unit) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = viewModel.locationRead(context.readLocation())
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(LocationManager.MODE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val read = context.readLocation()
            viewModel.locationRead(read)
            if (read.grant == LocationGrant.NONE) denied = true
            if (askingForPrecise && read.grant == LocationGrant.APPROXIMATE) preciseRefused = true
            if (followWanted && read.grant != LocationGrant.PRECISE) {
                followWanted = false
                needsPrecise = true
            }
            askingForPrecise = false
        }
    val showPosition: () -> Unit = {
        val read = state.location
        when {
            read == null -> Unit
            read.grant == LocationGrant.NONE ->
                if (!denied || activity.mayAskAgain()) {
                    centreWanted = true
                    ask.launch(LOCATION_PERMISSIONS)
                } else {
                    context.startActivitySafely(context.appSettings())
                }
            !read.on -> context.startActivitySafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else -> {
                centreWanted = true
                if (state.route.following != null) keepInView = true
            }
        }
    }
    // docs/phase-6b.md § 3's table: the Record asks, and following starts once precise location is granted.
    val follow: () -> Unit = {
        val read = state.location
        when {
            read == null -> Unit
            read.grant == LocationGrant.PRECISE -> viewModel.follow()
            read.grant == LocationGrant.NONE && (!denied || activity.mayAskAgain()) -> {
                followWanted = true
                ask.launch(LOCATION_PERMISSIONS)
            }
            read.grant == LocationGrant.APPROXIMATE && !preciseRefused -> {
                followWanted = true
                askingForPrecise = true
                ask.launch(LOCATION_PERMISSIONS)
            }
            else -> needsPrecise = true
        }
    }
    val approximateOnly = state.location?.grant == LocationGrant.APPROXIMATE
    val age = rememberPositionAge(state.fix, approximateOnly)
    var dotActivated by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf<Style?>(null) }
    var regions by remember { mutableStateOf<CameraBounds?>(null) }
    // A fit before the first camera is placed would be undone by placing it.
    var placed by remember { mutableStateOf(false) }
    // The zoom that shows every Region depends on the view's size, so it is taken again on a resize.
    var mapSize by remember { mutableStateOf(0 to 0) }
    DisposableEffect(mapView) {
        val listener =
            View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> mapSize = view.width to view.height }
        mapView.addOnLayoutChangeListener(listener)
        onDispose { mapView.removeOnLayoutChangeListener(listener) }
    }
    LaunchedEffect(map, regions) {
        val map = map ?: return@LaunchedEffect
        map.setLatLngBoundsForCameraTarget(regions?.latLngBounds() ?: return@LaunchedEffect)
    }
    // Setting the bounds jumps the camera, ending a Route's fit, so only the minimum zoom follows the sheet's resizes.
    LaunchedEffect(map, regions, mapSize) {
        val map = map ?: return@LaunchedEffect
        map.zoomOutNoFurtherThan(regions ?: return@LaunchedEffect)
    }
    var searchText by rememberSaveable { mutableStateOf("") }
    // MapLibre's compass shows once the map is turned, top-end by default: under the Safety and
    // Settings buttons and the banners below them unless it is moved below that whole overlay.
    var overlayHeight by remember { mutableIntStateOf(0) }
    var mapHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val compassRim = with(density) { CompassDrawable.RIM.roundToPx() }
    val compassEnd = with(density) { Spacing.tight.roundToPx() }
    LaunchedEffect(map, overlayHeight) {
        val map = map ?: return@LaunchedEffect
        map.uiSettings.setCompassMargins(0, (overlayHeight - compassRim).coerceAtLeast(0), compassEnd, 0)
    }
    LaunchedEffect(map, colors) {
        val map = map ?: return@LaunchedEffect
        map.uiSettings.setCompassImage(CompassDrawable(colors, density))
    }
    val focus = LocalFocusManager.current
    val trailTouchPx = with(density) { TRAIL_TOUCH_RADIUS.toPx() }
    var viewingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    // Saved rather than read from the ViewModel: the picker in front is when a low-memory kill comes.
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }
    val pickPhotos =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS_PICKED),
        ) { picked ->
            val hikeId = pickingFor ?: return@rememberLauncherForActivityResult
            pickingFor = null
            viewModel.addPhotos(hikeId, picked)
        }

    LaunchedEffect(map) {
        val map = map ?: return@LaunchedEffect
        map.addOnMapLongClickListener {
            viewModel.addPoint(it.latitude, it.longitude)
            true
        }
        map.addOnMapClickListener {
            viewModel.dismissOffer()
            focus.clearFocus()
            val photo =
                map
                    .queryRenderedFeatures(map.projection.toScreenLocation(it), HIKE_PHOTOS_LAYER)
                    .firstNotNullOfOrNull { feature -> feature.getStringProperty("id") }
            photo?.let { id -> viewingPhoto = id }
            if (photo != null) return@addOnMapClickListener true
            val cycling = state.mode == Mode.CYCLING
            val tapped =
                if (cycling) {
                    map.routesAt(it, loaded, trailTouchPx, BIKE_ROUTES_LAYER, BIKE_ROUTE_ID)
                } else {
                    map.routesAt(it, loaded, trailTouchPx, TRAILS_LAYER, TRAIL_ID)
                }
            if (cycling) viewModel.tapBikeRoutes(tapped) else viewModel.tapTrails(tapped)
            tapped.isNotEmpty()
        }
        val start = shown ?: viewModel.state.mapNotNull { it.camera }.first()
        map.moveCamera(start.toCameraUpdate())
        placed = true
        map.addOnCameraMoveStartedListener { reason ->
            positionOutside = false
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                centreWanted = false
                keepInView = false
            }
        }
        map.addOnCameraIdleListener {
            map.cameraPosition.toMapCamera()?.let { camera ->
                shown = camera
                viewModel.cameraIdle(camera)
            }
        }
    }

    LaunchedEffect(map, state.style) {
        val map = map ?: return@LaunchedEffect
        val style = state.style ?: return@LaunchedEffect
        loaded = null
        map.setStyle(Style.Builder().fromJson(style)) { ready ->
            loaded = ready
            regions = cameraBoundsOf(style)
            if (!map.locationComponent.isLocationComponentActivated) {
                map.locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions
                        .Builder(context, ready)
                        .useDefaultLocationEngine(false)
                        .locationComponentOptions(dotOptions(context, colors, stale = false))
                        .build(),
                )
                // A fix fed before the component's first frame is dropped, and approximate only
                // sends the next one ten minutes later.
                mapView.addOnDidFinishRenderingFrameListener(
                    object : MapView.OnDidFinishRenderingFrameListener {
                        override fun onDidFinishRenderingFrame(
                            fully: Boolean,
                            frameEncodingTime: Double,
                            frameRenderingTime: Double,
                        ) {
                            if (!fully) return
                            mapView.removeOnDidFinishRenderingFrameListener(this)
                            dotActivated = true
                        }
                    },
                )
            }
        }
    }

    val stale = age is PositionAge.Stale
    val hasFix = state.fix != null
    LaunchedEffect(map, dotActivated, hasFix, approximateOnly, stale, colors) {
        val component = map?.locationComponent?.takeIf { dotActivated } ?: return@LaunchedEffect
        component.show(hasFix)
        if (!hasFix) return@LaunchedEffect
        component.applyStyle(dotOptions(context, colors, stale))
        component.renderMode = if (approximateOnly) RenderMode.NORMAL else RenderMode.COMPASS
    }

    LaunchedEffect(map, dotActivated, state.fix) {
        val component = map?.locationComponent?.takeIf { dotActivated } ?: return@LaunchedEffect
        state.fix?.let(component::forceLocationUpdate)
    }

    val drawnTrack = state.drawnTrack
    LaunchedEffect(loaded, drawnTrack) {
        val style = loaded ?: return@LaunchedEffect
        (style.getSource(HIKE_TRACK_SOURCE) as? GeoJsonSource)?.setGeoJson(trackGeoJson(drawnTrack))
    }

    val photos = state.hike?.photos.orEmpty()
    LaunchedEffect(loaded, photos) {
        val style = loaded ?: return@LaunchedEffect
        (style.getSource(HIKE_PHOTOS_SOURCE) as? GeoJsonSource)?.setGeoJson(photoPinsGeoJson(photos))
    }

    val fade = remember(state.style) { state.style?.let(::fadeOf) }
    // A selection sets the Route aside: drawn again, as it was, once it is closed.
    val routeAside = state.selection
    LaunchedEffect(loaded, state.route, state.faded, routeAside, state.mode) {
        val style = loaded ?: return@LaunchedEffect
        (
            style.getSource(
                ROUTE_SOURCE,
            ) as? GeoJsonSource
        )?.setGeoJson(
            if (routeAside) routeGeoJson(emptyList(), null) else routeGeoJson(state.route.points, state.route.planned),
        )
        for (id in fade?.layers.orEmpty()) {
            val opacity = fade?.opacityOf(id, state.mode, state.faded) ?: 1f
            val layer = style.getLayer(id)
            layer?.setProperties(
                PropertyFactory.visibility(
                    if (fade?.isShown(id, state.mode) != false) Property.VISIBLE else Property.NONE,
                ),
            )
            when (layer) {
                is LineLayer -> layer.setProperties(PropertyFactory.lineOpacity(opacity))
                is SymbolLayer ->
                    layer.setProperties(
                        PropertyFactory.iconOpacity(opacity),
                        PropertyFactory.textOpacity(opacity),
                    )
            }
        }
    }

    val back = (state.route.following?.back as? LegBack.Found)?.planned?.takeUnless { routeAside }
    LaunchedEffect(loaded, back) {
        val style = loaded ?: return@LaunchedEffect
        (style.getSource(ROUTE_BACK_SOURCE) as? GeoJsonSource)?.setGeoJson(routeGeoJson(emptyList(), back))
    }

    val bikeRouteKind = state.bikeRouteFilter.kind.takeIf { state.mode == Mode.CYCLING }
    val styleFilters = remember(loaded) { mutableMapOf<String, Expression?>() }
    LaunchedEffect(loaded, bikeRouteKind) {
        val style = loaded ?: return@LaunchedEffect
        for (layer in style.layers) {
            if (layer.id.startsWith(BIKE_ROUTE_LAYERS)) {
                val own = styleFilters.holdFirst(layer.id) { filterOf(layer) }
                setFilterOf(layer, bikeRouteFilter(own, bikeRouteKind))
            }
        }
    }

    val highlightedTrails = state.trail.highlighted
    val highlightedBikeRoutes = state.bikeRoute.highlighted
    LaunchedEffect(loaded, highlightedTrails, highlightedBikeRoutes) {
        val style = loaded ?: return@LaunchedEffect
        (style.getSource(TRAIL_WALK_SOURCE) as? GeoJsonSource)?.setGeoJson(
            if (highlightedBikeRoutes.isNotEmpty()) {
                bikeRouteWalkGeoJson(highlightedBikeRoutes)
            } else {
                trailWalkGeoJson(highlightedTrails)
            },
        )
        val trails = selectedFilter(TRAIL_ID, highlightedTrails.map { it.id })
        val bikeRoutes = selectedFilter(BIKE_ROUTE_ID, highlightedBikeRoutes.map { it.id })
        for (layer in style.layers) {
            when {
                layer.id.startsWith(SELECTED_TRAIL_LAYERS) && layer is LineLayer -> layer.setFilter(trails)
                layer.id.startsWith(SELECTED_BIKE_ROUTE_LAYERS) && layer is LineLayer -> layer.setFilter(bikeRoutes)
                layer.id.startsWith(SELECTED_BIKE_ROUTE_LAYERS) && layer is SymbolLayer ->
                    layer.setFilter(Expression.all(Expression.has("dir"), bikeRoutes))
            }
        }
    }

    val planned = state.route.planned
    // Clear of the search row above and the position button and credit below.
    val sidePadding = with(LocalDensity.current) { Spacing.block.roundToPx() }
    val topPadding = sidePadding * 3 + with(LocalDensity.current) { MODE_ROW_HEIGHT.roundToPx() }
    val bottomPadding = sidePadding * 5
    // Each return to the map brings a new MapLibreMap, and these two would otherwise move the camera
    // away from where the user left it; so each Route is fitted to, and each place centred on, once.
    var fitted by rememberSaveable { mutableStateOf<Int?>(null) }
    val keyboard = WindowInsets.ime
    // After the Regions' bounds are set, since setting them ends a fit already under way.
    LaunchedEffect(map, planned, state.route.opening, regions, placed) {
        if (planned == null) fitted = null
        val map = map ?: return@LaunchedEffect
        if (regions == null || !placed) return@LaunchedEffect
        val line = planned?.line?.takeIf { it.size >= 2 } ?: return@LaunchedEffect
        val route = 31 * planned.hashCode() + state.route.opening
        if (route == fitted) return@LaunchedEffect
        awaitLayoutSettled(height = { mapHeight }, keyboard = { keyboard.getBottom(density) })
        fitted = route
        val bounds =
            LatLngBounds
                .Builder()
                .includes(line.map { LatLng(it.latitude, it.longitude) })
                .build()
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, sidePadding, topPadding, sidePadding, bottomPadding),
        )
    }

    // Not saved: a ViewModel rebuilt after process death counts its fits from 0 again.
    val selectionFits = state.trail.fits + state.bikeRoute.fits
    var fittedSelection by remember { mutableIntStateOf(selectionFits) }
    LaunchedEffect(map, selectionFits, regions, placed) {
        val map = map ?: return@LaunchedEffect
        if (regions == null || !placed || selectionFits == fittedSelection) return@LaunchedEffect
        awaitLayoutSettled(height = { mapHeight }, keyboard = { keyboard.getBottom(density) })
        val boxes =
            (state.trail.highlighted.map { it.facts } + state.bikeRoute.highlighted.map { it.facts })
                .ifEmpty { return@LaunchedEffect }
        fittedSelection = selectionFits
        val bounds =
            LatLngBounds.from(
                boxes.maxOf { it.north },
                boxes.maxOf { it.east },
                boxes.minOf { it.south },
                boxes.minOf { it.west },
            )
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, sidePadding, topPadding, sidePadding, bottomPadding),
        )
    }

    val hike = state.hike
    var fittedHike by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(map, hike?.id, hike?.opening, hike?.track, regions, placed) {
        if (hike == null) fittedHike = null
        val map = map ?: return@LaunchedEffect
        if (regions == null || !placed) return@LaunchedEffect
        val points = hike?.track?.flatten()?.takeIf { it.size >= 2 } ?: return@LaunchedEffect
        val opened = "${hike.id}#${hike.opening}"
        if (opened == fittedHike) return@LaunchedEffect
        fittedHike = opened
        val bounds =
            LatLngBounds
                .Builder()
                .includes(points.map { LatLng(it.latitude, it.longitude) })
                .build()
        // A jump, not an animation: the sheet opening beside it resizes the map and ended the animation.
        map.moveCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, sidePadding, topPadding, sidePadding, bottomPadding),
        )
    }
    BackHandler(enabled = hike != null) { if (hike?.keep != null) viewModel.cancelTrim() else viewModel.closeHike() }
    // After the Hike's, which a chooser opens over: the handler composed last wins.
    BackHandler(enabled = state.bikeRoute.shown != null || state.bikeRoute.choices.isNotEmpty()) {
        if (state.bikeRoute.choices.isNotEmpty()) viewModel.dismissBikeRouteChoices() else viewModel.closeSelection()
    }
    BackHandler(enabled = state.trail.shown != null || state.trail.choices.isNotEmpty()) {
        if (state.trail.choices.isNotEmpty()) viewModel.dismissTrailChoices() else viewModel.closeSelection()
    }

    val offered = state.search.offered
    var centred by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(map, offered) {
        if (offered == null) centred = null
        val map = map ?: return@LaunchedEffect
        val place = offered ?: return@LaunchedEffect
        if (place.id == centred) return@LaunchedEffect
        centred = place.id
        val zoom = maxOf(map.cameraPosition.zoom, OFFERED_ZOOM)
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), zoom))
    }
    BackHandler(enabled = offered != null, onBack = viewModel::dismissOffer)

    LaunchedEffect(state.fix, regions) {
        val fix = state.fix ?: return@LaunchedEffect
        if (regions?.contains(fix.latitude, fix.longitude) != false) positionOutside = false
    }
    LaunchedEffect(followWanted, state.location) {
        if (!followWanted || state.location?.grant != LocationGrant.PRECISE) return@LaunchedEffect
        followWanted = false
        viewModel.follow()
    }
    val following = state.route.following != null
    // A Follow tap and a Route resumed on reopening alike (docs/phase-6b.md § 3).
    LaunchedEffect(following) {
        if (following) keepInView = true
    }
    LaunchedEffect(following, state.fix) {
        if (following) state.fix?.let { viewModel.followFix(it.toFollowFix()) }
    }
    // The top padding puts the user in the part of the map the search row and the banners leave open.
    LaunchedEffect(map, following, keepInView, state.fix, overlayHeight, mapHeight) {
        val map = map ?: return@LaunchedEffect
        val fix = state.fix ?: return@LaunchedEffect
        if (!following || !keepInView) return@LaunchedEffect
        val northUp =
            CameraPosition
                .Builder(
                    map.cameraPosition,
                ).target(LatLng(fix.latitude, fix.longitude))
                .bearing(0.0)
                .padding(0.0, min(overlayHeight, mapHeight / 2).toDouble(), 0.0, 0.0)
        map.animateCamera(CameraUpdateFactory.newCameraPosition(northUp.build()))
    }
    LaunchedEffect(map, following) {
        if (!following) map?.clearPadding()
    }
    if (needsPrecise) {
        AlertDialog(
            onDismissRequest = { needsPrecise = false },
            text = { Text(stringResource(R.string.following_needs_precise)) },
            confirmButton = {
                TextButton(onClick = {
                    needsPrecise = false
                    context.startActivitySafely(context.appSettings())
                }) { Text(stringResource(R.string.following_location_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { needsPrecise = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    LaunchedEffect(map, centreWanted, state.fix) {
        val map = map ?: return@LaunchedEffect
        val fix = state.fix ?: return@LaunchedEffect
        if (!centreWanted) return@LaunchedEffect
        centreWanted = false
        // The camera cannot leave the Regions, so it would stop short on a shaded spot with the dot off-screen.
        positionOutside = regions?.contains(fix.latitude, fix.longitude) == false
        if (!positionOutside) map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(fix.latitude, fix.longitude)))
    }

    val viewing = viewingPhoto?.let { id -> photos.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
    if (viewing != null && hike != null) {
        PhotoViewer(
            photos = photos,
            initialIndex = viewing,
            hikeName = hike.name,
            onShown = { viewingPhoto = it },
            onSetCaption = viewModel::setCaption,
            onUnpin = viewModel::unpinPhoto,
            onDelete = viewModel::deletePhoto,
            onClose = { viewingPhoto = null },
        )
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // A share of the height rather than a fixed one, so landscape and a large font still leave the map.
        val sheetHeight = maxHeight / 2
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { mapHeight = it.height }) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                // Above the position button, which search results reach down over.
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(1f)
                        .onSizeChanged { overlayHeight = it.height },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(Spacing.tight),
                    ) {
                        SearchField(
                            text = searchText,
                            available = state.search.available,
                            onText = {
                                searchText = it
                                viewModel.search(it)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalIconButton(onClick = onOpenSafety) {
                            Icon(
                                painterResource(R.drawable.ic_emergency),
                                contentDescription = stringResource(R.string.safety_title),
                            )
                        }
                        FilledTonalIconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                    }
                    if (searchText.isBlank()) {
                        ModeSwitch(
                            mode = state.mode,
                            filter = state.bikeRouteFilter,
                            onMode = viewModel::setMode,
                            onFilter = viewModel::setBikeRouteFilter,
                            modifier = Modifier.padding(horizontal = Spacing.tight),
                        )
                    }
                    val searchFoundAny = state.search.results.isNotEmpty() || state.search.otherModeMatches > 0
                    if (searchText.isNotBlank() && searchFoundAny) {
                        // A new Mode's results start at their best match, not at the old list's scroll.
                        key(state.mode) {
                            SearchResults(
                                results = state.search.results,
                                onPick = {
                                    searchText = ""
                                    focus.clearFocus()
                                    viewModel.pick(it)
                                },
                                modifier = Modifier.padding(horizontal = Spacing.tight),
                                last = {
                                    OtherModeHint(
                                        mode = state.mode.other,
                                        matches = state.search.otherModeMatches,
                                        onSwitch = { viewModel.setMode(state.mode.other) },
                                    )
                                },
                            )
                        }
                    }
                    offered?.let {
                        OfferedPlace(
                            place = it,
                            first = state.route.points.isEmpty(),
                            onAdd = viewModel::addOffered,
                            modifier = Modifier.padding(horizontal = Spacing.tight),
                        )
                    }
                    state.progress?.let {
                        ProgressLine(
                            progress = it,
                            onRetry = viewModel::retry,
                            onConfirm = { viewModel.confirm(confirmation) },
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = Spacing.base),
                        )
                    }
                    LocationBanner(
                        read = state.location,
                        age = age,
                        preciseRefused = preciseRefused,
                        onAllowPrecise = {
                            askingForPrecise = true
                            ask.launch(LOCATION_PERMISSIONS)
                        },
                    )
                    if (positionOutside) WarningBanner(title = stringResource(R.string.map_position_outside))
                    state.route.following
                        ?.leaving
                        ?.takeIf { it.shown }
                        ?.let { leaving ->
                            WarningBanner(
                                title =
                                    stringResource(
                                        R.string.following_left,
                                        distance(leaving.metres, LocalConfiguration.current.locales[0]),
                                    ),
                                actionLabel = stringResource(R.string.following_back),
                                onAct = { viewModel.backToRoute(state.fix?.toFollowFix()) },
                            )
                        }
                }
                // The credit takes a row of its own: a long translation reaches across the whole width.
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Spacing.tight),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Spacing.tight),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        if (state.recording == null) {
                            FilledTonalButton(onClick = onRecord) {
                                Icon(painterResource(R.drawable.ic_record), contentDescription = null)
                                Spacer(Modifier.width(Spacing.tight))
                                Text(stringResource(R.string.record_hike))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        FilledTonalIconButton(onClick = showPosition) {
                            Icon(
                                painterResource(R.drawable.ic_my_location),
                                contentDescription = stringResource(R.string.map_position_show),
                            )
                        }
                    }
                    OsmCredit(state.osmDate, relief = state.reliefShown)
                }
            }
            val trailWords = rememberTrailWords()
            val bikeRouteWords = rememberBikeRouteWords()
            val shownBikeRoute = state.bikeRoute.shown
            val shownTrail = state.trail.shown
            if (state.bikeRoute.choices.isNotEmpty()) {
                BikeRouteChooser(
                    choices = state.bikeRoute.choices,
                    words = bikeRouteWords,
                    onPick = viewModel::chooseBikeRoute,
                    onDismiss = viewModel::dismissBikeRouteChoices,
                    modifier = Modifier.heightIn(max = sheetHeight),
                )
            } else if (shownBikeRoute != null) {
                BikeRouteSheet(
                    route = shownBikeRoute,
                    words = bikeRouteWords,
                    onClose = viewModel::closeSelection,
                    onShowWhole = viewModel::showWholeBikeRoute,
                    onShowSections = viewModel::showBikeRouteSections,
                    planningRoute = state.bikeRoute.planningRoute,
                    onPlanRoute = viewModel::planAlongBikeRoute.takeIf { state.route.following == null },
                    gpx = gpx,
                    onExportGpx = viewModel::exportBikeRouteGpx,
                    onDismissGpxFailure = viewModel::dismissGpxFailure,
                    modifier = Modifier.heightIn(max = sheetHeight),
                )
            } else if (state.trail.choices.isNotEmpty()) {
                TrailChooser(
                    choices = state.trail.choices,
                    words = trailWords,
                    onPick = viewModel::chooseTrail,
                    onDismiss = viewModel::dismissTrailChoices,
                    modifier = Modifier.heightIn(max = sheetHeight),
                )
            } else if (shownTrail != null) {
                TrailSheet(
                    trail = shownTrail,
                    words = trailWords,
                    onClose = viewModel::closeSelection,
                    onShowWhole = viewModel::showWholeTrail,
                    onShowLongDistance = viewModel::showLongDistance,
                    planningRoute = state.trail.planningRoute,
                    onPlanRoute = viewModel::planAlongTrail,
                    gpx = gpx,
                    onExportGpx = viewModel::exportTrailGpx,
                    onDismissGpxFailure = viewModel::dismissGpxFailure,
                    modifier = Modifier.heightIn(max = sheetHeight),
                )
            } else if (hike != null) {
                HikeSheet(
                    hike = hike,
                    onClose = viewModel::closeHike,
                    onRename = viewModel::renameHike,
                    onStartTrim = viewModel::startTrim,
                    onTrimChange = viewModel::trimTo,
                    onCancelTrim = viewModel::cancelTrim,
                    onTrim = viewModel::trim,
                    onDelete = viewModel::deleteHike,
                    onSetNote = viewModel::setNote,
                    onAddPhotos = {
                        pickingFor = hike.id
                        pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onOpenPhoto = { viewingPhoto = it },
                    gpx = gpx,
                    onExportGpx = viewModel::exportHikeGpx,
                    onDismissGpxFailure = viewModel::dismissGpxFailure,
                    modifier = Modifier.heightIn(max = sheetHeight),
                )
            } else if (state.route.points.isNotEmpty()) {
                RouteSheet(
                    route = state.route,
                    pace = state.pace,
                    onRemove = viewModel::removePoint,
                    onMove = viewModel::movePoint,
                    onReverse = viewModel::reverse,
                    onShowSaveDialog = viewModel::showSaveDialog,
                    onSave = viewModel::save,
                    onFollow = follow,
                    onStopFollowing = viewModel::stopFollowing,
                    onFindMe = { viewModel.findMeOnRoute(state.fix?.toFollowFix()) },
                    onDismissNote = viewModel::dismissFollowingNote,
                    modifier = Modifier.heightIn(max = sheetHeight),
                )
            }
        }
    }
}

/** The ids drawn under a finger's width around [at], from every Region's [layer] and, below zoom 10, the highlighted walk. */
private fun MapLibreMap.routesAt(
    at: LatLng,
    style: Style?,
    radius: Float,
    layer: String,
    idProperty: String,
): List<Long> {
    val layers =
        style
            ?.layers
            ?.map { it.id }
            ?.filter { it.startsWith("$layer-") || it == TRAIL_WALK_LAYER } ?: return emptyList()
    val point = projection.toScreenLocation(at)
    val box = RectF(point.x - radius, point.y - radius, point.x + radius, point.y + radius)
    return queryRenderedFeatures(box, *layers.toTypedArray())
        .mapNotNull { it.getNumberProperty(idProperty)?.toLong() }
        .distinct()
}

/**
 * Until the map stops changing size and the keyboard has gone: a bounds fit is worked out from the map's
 * size at that moment, and a search pick closes the keyboard and opens a sheet as it asks for one.
 */
private suspend fun awaitLayoutSettled(
    height: () -> Int,
    keyboard: () -> Int,
) {
    withTimeoutOrNull(LAYOUT_SETTLE_LIMIT_MS) {
        var last = height()
        while (true) {
            delay(LAYOUT_SETTLE_STEP_MS)
            val now = height()
            if (now == last && keyboard() == 0) break
            last = now
        }
    }
}

private const val LAYOUT_SETTLE_STEP_MS = 150L
private const val LAYOUT_SETTLE_LIMIT_MS = 1_500L

/** Matches no feature for no ids, as the style's own filter does until something is selected. */
private fun selectedFilter(
    idProperty: String,
    ids: List<Long>,
): Expression =
    if (ids.isEmpty()) {
        Expression.eq(Expression.get(idProperty), Expression.literal(-1))
    } else {
        Expression.any(
            *ids.distinct().map { Expression.eq(Expression.get(idProperty), Expression.literal(it)) }.toTypedArray(),
        )
    }

private const val TRAIL_ID = "trail"
private const val BIKE_ROUTE_ID = "bike_route"

private fun CameraBounds.latLngBounds() = LatLngBounds.from(north, east, south, west)

/** As far as shows every Region north-up and flat, so the limit depends on the view's size alone. */
private fun MapLibreMap.zoomOutNoFurtherThan(regions: CameraBounds) {
    getCameraForLatLngBounds(
        regions.latLngBounds(),
        intArrayOf(0, 0, 0, 0),
        0.0,
        0.0,
    )?.zoom?.let(::setMinZoomPreference)
}

@Composable
private fun rememberMapView(onStop: () -> Unit): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null) }.also(::logFrames) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnStop by rememberUpdatedState(onStop)
    DisposableEffect(lifecycle, mapView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> {
                        mapView.onStop()
                        currentOnStop()
                    }
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            // Leaving Home for another entry disposes it with no ON_STOP, so the stop is this.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                mapView.onStop()
                currentOnStop()
            }
            mapView.onDestroy()
        }
    }
    return mapView
}

/** Every row of docs/phase-2.md § 4's table but "not asked" and "denied", which show only the button. */
@Composable
private fun LocationBanner(
    read: LocationRead?,
    age: PositionAge?,
    preciseRefused: Boolean,
    onAllowPrecise: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val staleLine =
        (age as? PositionAge.Stale)?.age?.let {
            stringResource(
                R.string.map_position_stale,
                it.short(locale),
            )
        }
    val settings = stringResource(R.string.map_location_settings)
    when {
        read == null || read.grant == LocationGrant.NONE -> Unit
        !read.on ->
            WarningBanner(
                title = stringResource(R.string.map_location_off),
                actionLabel = settings,
                onAct = { context.startActivitySafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            )
        read.grant == LocationGrant.APPROXIMATE ->
            WarningBanner(
                title = stringResource(R.string.map_location_approximate),
                body = listOfNotNull(stringResource(R.string.map_location_approximate_body), staleLine),
                actionLabel = if (preciseRefused) settings else stringResource(R.string.map_location_allow),
                onAct = {
                    if (preciseRefused) {
                        context.startActivitySafely(
                            context.appSettings(),
                        )
                    } else {
                        onAllowPrecise()
                    }
                },
            )
        staleLine != null -> WarningBanner(title = staleLine)
    }
}

/** Re-read every second, so a fix that stops arriving goes stale on screen. */
@Composable
private fun rememberPositionAge(
    fix: Location?,
    approximateOnly: Boolean,
): PositionAge? {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(fix) {
        while (fix != null) {
            delay(1.seconds)
            tick++
        }
    }
    return remember(fix, approximateOnly, tick) {
        fix?.let { positionAge(it.elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos(), approximateOnly) }
    }
}

// Its own stale state is off: it never reads the fix's age, so PositionAge greys the dot instead.
private fun dotOptions(
    context: Context,
    colors: ColorScheme,
    stale: Boolean,
): LocationComponentOptions {
    val tint = (if (stale) colors.outline else colors.primary).toArgb()
    return LocationComponentOptions
        .builder(context)
        .enableStaleState(false)
        .foregroundTintColor(tint)
        .bearingTintColor(tint)
        .accuracyColor(tint)
        .backgroundTintColor(colors.surface.toArgb())
        .build()
}

@SuppressLint("MissingPermission")
private fun LocationComponent.show(shown: Boolean) {
    isLocationComponentEnabled = shown
}

private fun android.app.Activity?.mayAskAgain(): Boolean =
    this != null && ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)

private fun Context.appSettings() =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

@Composable
private fun ProgressLine(
    progress: MapProgress,
    onRetry: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MapLabel(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight),
        ) {
            Text(
                text =
                    when (progress) {
                        is MapProgress.Downloading ->
                            stringResource(
                                R.string.map_downloading,
                                progress.ready,
                                progress.total,
                            )
                        MapProgress.WaitingForWifi -> stringResource(R.string.map_waiting_for_wifi)
                        MapProgress.NeedsConfirmation -> stringResource(R.string.map_needs_confirmation)
                        is MapProgress.Failed -> stringResource(R.string.map_download_failed)
                        MapProgress.Updating -> stringResource(R.string.map_updating)
                    },
                style = MaterialTheme.typography.bodyLarge,
            )
            when (progress) {
                MapProgress.WaitingForWifi ->
                    TextButton(onClick = onConfirm) { Text(stringResource(R.string.map_mobile_data)) }
                MapProgress.NeedsConfirmation ->
                    TextButton(onClick = onConfirm) { Text(stringResource(R.string.map_confirm)) }
                is MapProgress.Failed ->
                    TextButton(onClick = onRetry) {
                        Text(
                            progress.size?.let {
                                stringResource(
                                    R.string.map_retry_sized,
                                    Formatter.formatShortFileSize(LocalContext.current, it),
                                )
                            }
                                ?: stringResource(R.string.map_retry),
                        )
                    }
                is MapProgress.Downloading, MapProgress.Updating -> Unit
            }
        }
    }
}

@Composable
private fun OsmCredit(
    osmDate: Instant?,
    relief: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val credit = stringResource(R.string.map_osm_credit)
    val reliefCredit = stringResource(R.string.map_relief_credit, RELIEF_SOURCES)
    MapLabel(modifier) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(horizontal = Spacing.tight, vertical = Spacing.hair),
        ) {
            Text(
                text =
                    buildAnnotatedString {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = OSM_COPYRIGHT,
                                styles = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary)),
                            ) { context.startActivitySafely(Intent(Intent.ACTION_VIEW, OSM_COPYRIGHT.toUri())) },
                        ) { append(credit) }
                        if (relief) append(" · $reliefCredit")
                    },
                style = MaterialTheme.typography.labelSmall,
            )
            if (osmDate != null) {
                Text(osmDateLine(osmDate), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Text laid straight over Trails and map labels is unreadable, so it sits on a surface of its own. */
@Composable
private fun MapLabel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        content = content,
    )
}

private val CameraSaver =
    Saver<MapCamera?, DoubleArray>(
        save = { camera -> camera?.let { doubleArrayOf(it.latitude, it.longitude, it.zoom) } },
        restore = { (latitude, longitude, zoom) -> MapCamera(latitude, longitude, zoom) },
    )

/** Close enough that the Trails around a picked place are drawn. */
private const val OFFERED_ZOOM = 13.0

// About a fingertip's half-width, so a tap between two strands drawn side by side catches both.
private val TRAIL_TOUCH_RADIUS = 16.dp

// Bounds how long one pick takes to write, each photo decoded and encoded twice; not the picker's own limit.
private const val MAX_PHOTOS_PICKED = 50

private const val OSM_COPYRIGHT = "https://www.openstreetmap.org/copyright"

/** The relief's three agencies, by the names their licences ask for (docs/phase-4.md § 3). */
private const val RELIEF_SOURCES = "GUGiK, ÚGKK SR, ČÚZK"

private fun MapCamera.toCameraUpdate(): CameraUpdate =
    CameraUpdateFactory.newCameraPosition(
        CameraPosition
            .Builder()
            .target(LatLng(latitude, longitude))
            .zoom(zoom)
            .build(),
    )

private fun CameraPosition.toMapCamera(): MapCamera? = target?.let { MapCamera(it.latitude, it.longitude, zoom) }

/** Drops the follow camera's padding, keeping what the map shows where it is. */
private fun MapLibreMap.clearPadding() {
    if (cameraPosition.padding?.any { it != 0.0 } != true) return
    val centre = projection.fromScreenLocation(PointF(width / 2, height / 2))
    val unpadded =
        CameraPosition
            .Builder(cameraPosition)
            .target(centre)
            .padding(0.0, 0.0, 0.0, 0.0)
            .build()
    moveCamera(CameraUpdateFactory.newCameraPosition(unpadded))
}

/** Freshness by the arrival's clock, the window by the fix's own `time` (docs/phase-6b.md, Readings). */
private fun Location.toFollowFix() =
    FollowFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMetres = if (hasAccuracy()) accuracy.toDouble() else Double.MAX_VALUE,
        ageMillis = (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000,
        atMillis = time,
    )

private fun filterOf(layer: Layer): Expression? =
    when (layer) {
        is LineLayer -> layer.filter
        is SymbolLayer -> layer.filter
        else -> null
    }

private fun setFilterOf(
    layer: Layer,
    filter: Expression,
) {
    when (layer) {
        is LineLayer -> layer.setFilter(filter)
        is SymbolLayer -> layer.setFilter(filter)
    }
}

private fun bikeRouteFilter(
    own: Expression?,
    kind: String?,
): Expression {
    val chosen = kind?.let { Expression.eq(Expression.get("kind"), Expression.literal(it)) }
    return when {
        own != null && chosen != null -> Expression.all(own, chosen)
        else -> own ?: chosen ?: Expression.literal(true)
    }
}
