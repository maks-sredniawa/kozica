package app.kozica

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.kozica.theme.Spacing
import app.kozica.ui.appViewModelExtras
import app.kozica.ui.backup.BackupScreen
import app.kozica.ui.common.WarningBanner
import app.kozica.ui.hikes.HikesScreen
import app.kozica.ui.map.MapScreen
import app.kozica.ui.record.RecordingBar
import app.kozica.ui.record.RecordingViewModel
import app.kozica.ui.record.StopDialog
import app.kozica.ui.record.UnfinishedDialog
import app.kozica.ui.record.rememberRecordStart
import app.kozica.ui.record.rememberTakePhoto
import app.kozica.ui.routes.RoutesScreen
import app.kozica.ui.safety.SafetyScreen
import app.kozica.ui.settings.SettingsScreen
import app.kozica.ui.support.LicenceTextScreen
import app.kozica.ui.support.LicencesScreen
import app.kozica.ui.support.SupportScreen

/**
 * What every back-stack entry is wrapped in — above all, **one `ViewModelStore` per entry**.
 *
 * Nav3 does not do this on its own. The ViewModel decorator ships in a separate artifact
 * (`lifecycle-viewmodel-navigation3`), which `navigation3-ui` does not depend on, so `NavDisplay`'s
 * default list cannot contain it. Without it every `viewModel()` resolves to the **Activity's**
 * store and outlives the screen that created it — an editor comes back with its `saved` flag still
 * set and bounces straight out of the second "Add" of a session, and only killing the process
 * clears it. It is a genuinely confusing bug, because the screen is correct and its state is not.
 *
 * `rememberSaveableStateHolderNavEntryDecorator` is Nav3's own default, restated because passing a list
 * replaces the defaults rather than adding to them.
 *
 * Extracted from [MainNavigation] so a test can assert the scoping directly.
 */
@Composable
internal fun appEntryDecorators(): List<NavEntryDecorator<NavKey>> =
    listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    )

/**
 * The app shell: a [NavDisplay] rooted at [Home], so Back from anywhere walks back there and Back
 * from there exits, and the tab bar under the three tabs, Map, [Routes] and [Hikes]
 * (docs/phase-5.md § 5, docs/phase-6.md § 4). A tab replaces the one above Home rather than
 * stacking on it, so Back from either returns to the map.
 */
@Composable
fun MainNavigation(
    stopAsked: Boolean,
    onStopAnswered: () -> Unit,
    modifier: Modifier = Modifier,
    recordingViewModel: RecordingViewModel =
        viewModel(factory = RecordingViewModel.Factory, extras = appViewModelExtras()),
) {
    val backStack = rememberNavBackStack(Home)
    val activity = LocalActivity.current
    val showMap = { while (backStack.size > 1) backStack.removeLastOrNull() }
    val showTab = { tab: NavKey ->
        if (backStack.lastOrNull() != tab) {
            showMap()
            backStack.add(tab)
        }
    }
    val recording by recordingViewModel.state.collectAsStateWithLifecycle()
    val takePhoto = rememberTakePhoto(onTaken = recordingViewModel::addPhoto, onDiscard = recordingViewModel::discard)
    val record =
        rememberRecordStart(
            notificationsAsked = recording.notificationsAsked,
            onNotificationsAsked = recordingViewModel::notificationsAsked,
            onStart = recordingViewModel::start,
        )
    var stopping by rememberSaveable { mutableStateOf(false) }
    val nothingToStop = recording.loaded && recording.recording == null
    LaunchedEffect(stopAsked, nothingToStop) {
        if (stopAsked && nothingToStop) onStopAnswered()
    }
    if ((stopping || stopAsked) && recording.recording != null) {
        StopDialog(
            onStop = {
                recordingViewModel.stop()
                stopping = false
                onStopAnswered()
            },
            onKeep = {
                stopping = false
                onStopAnswered()
            },
        )
    }
    recording.unfinished?.let { hike ->
        UnfinishedDialog(hike = hike, onResume = record, onFinish = recordingViewModel::finishUnfinished)
    }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            val top = backStack.lastOrNull()
            Column {
                if (recording.photosLeftOut > 0) {
                    WarningBanner(
                        title =
                            pluralStringResource(
                                R.plurals.photos_left_out,
                                recording.photosLeftOut,
                                recording.photosLeftOut,
                            ),
                        actionLabel = stringResource(R.string.action_ok),
                        onAct = recordingViewModel::dismissPhotosLeftOut,
                        modifier = Modifier.padding(Spacing.tight),
                    )
                }
                recording.recording?.let {
                    RecordingBar(
                        recording = it,
                        onPause = recordingViewModel::pause,
                        onResume = recordingViewModel::resume,
                        onTakePhoto = { takePhoto(it.hikeId) },
                        onStop = { stopping = true },
                    )
                }
                if (top == Home || top == Routes || top == Hikes) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = top == Home,
                            onClick = showMap,
                            icon = { Icon(painterResource(R.drawable.ic_map), contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_map)) },
                        )
                        NavigationBarItem(
                            selected = top == Routes,
                            onClick = { showTab(Routes) },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                            label = { Text(stringResource(R.string.routes_title)) },
                        )
                        NavigationBarItem(
                            selected = top == Hikes,
                            onClick = { showTab(Hikes) },
                            icon = { Icon(painterResource(R.drawable.ic_hike), contentDescription = null) },
                            label = { Text(stringResource(R.string.hikes_title)) },
                        )
                    }
                }
            }
        },
    ) { insets ->
        NavDisplay(
            backStack = backStack,
            // **The keyboard is an inset like any other, and this Scaffold owns them.**
            //
            // `MainActivity` sets `decorFitsSystemWindows = false` for edge-to-edge, which makes the
            // manifest's `adjustResize` inoperative from API 30 — the window manager downgrades it
            // to a *pan*. With nothing consuming `WindowInsets.ime`, the system then pans the whole
            // window to keep the focused field in view, which slides the top of a long form under
            // the status bar and pushes its top bar — Save included — off the screen entirely.
            //
            // `consumeWindowInsets` before `imePadding` is what stops it double-counting: the
            // keyboard's inset is measured from the bottom of the *screen*, so it already contains
            // the navigation-bar height that `padding(insets)` just applied. Consuming says "that
            // part is handled", leaving `imePadding` to add only the rest.
            modifier = Modifier.padding(insets).consumeWindowInsets(insets).imePadding(),
            entryDecorators = appEntryDecorators(),
            onBack = {
                if (backStack.size > 1) backStack.removeLastOrNull() else activity?.finish()
            },
            // Kotlin note: this is a builder DSL, not a map literal — `entry<Home> { … }` registers
            // the composable that renders that key, and the lambda receives the key itself, which is
            // how a key carrying arguments passes them in.
            entryProvider =
                entryProvider {
                    entry<Home> {
                        MapScreen(
                            onRecord = { record(null) },
                            onOpenSettings = { backStack.add(Settings) },
                            onOpenSafety = { backStack.add(Safety) },
                        )
                    }
                    entry<Routes> {
                        RoutesScreen(onShow = showMap)
                    }
                    entry<Hikes> {
                        HikesScreen(onShow = showMap)
                    }
                    entry<Safety> {
                        SafetyScreen(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Settings> {
                        SettingsScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenLicences = { backStack.add(Licences) },
                            onOpenSupport = { backStack.add(Support) },
                            onOpenBackup = { backStack.add(Backup) },
                        )
                    }
                    entry<Backup> {
                        BackupScreen(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Support> {
                        SupportScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenCredits = { backStack.add(Licences) },
                        )
                    }
                    entry<Licences> {
                        LicencesScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenLicenceText = { spdxId, title -> backStack.add(LicenceText(spdxId, title)) },
                        )
                    }
                    entry<LicenceText> { key ->
                        LicenceTextScreen(
                            spdxId = key.spdxId,
                            title = key.title,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                },
        )
    }
}
