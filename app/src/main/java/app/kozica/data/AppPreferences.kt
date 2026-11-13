package app.kozica.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.kozica.data.routes.FollowedRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant

/** Which colour scheme applies, regardless of what the phone is set to. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class MapCamera(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
)

/**
 * Everything the app remembers that is not a row in the database.
 *
 * **DataStore, not SharedPreferences.** SharedPreferences' `commit()` blocks and its `apply()` can
 * still write on the main thread at the wrong moment; DataStore is `suspend`-only and transactional,
 * so there is no API here that can stall a frame.
 *
 * Kotlin note: every read is a `Flow`, not a value. There is no "current value" to ask for
 * synchronously — the first emission arrives after a disk read. That matters at startup: a screen
 * collecting `themeMode` gets `SYSTEM` for one frame and the stored value on the next, which is a
 * visible flash. [themeModeNow] exists for the one caller that must have the answer *before* the
 * first frame, and it is `suspend` because reading a file is.
 *
 * ## Adding a preference
 *
 * A key, a `Flow` that reads it with a default, and a `suspend` setter. Keep the default in the
 * `Flow` rather than writing it on first launch — a default that has been written to disk cannot be
 * changed in a later version without a migration, and a default that has not can.
 */
class AppPreferences(
    private val store: DataStore<Preferences>,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MAP_MODE = stringPreferencesKey("map_mode")
        val MAP_BIKE_ROUTE_FILTER = stringPreferencesKey("map_bike_route_filter")
        val MATERIAL_YOU = booleanPreferencesKey("material_you")
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val MAP_LATITUDE = doublePreferencesKey("map_latitude")
        val MAP_LONGITUDE = doublePreferencesKey("map_longitude")
        val MAP_ZOOM = doublePreferencesKey("map_zoom")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val NOTIFICATIONS_ASKED = booleanPreferencesKey("notifications_asked")
        val LAST_EVERYTHING_EXPORT = longPreferencesKey("last_everything_export")
        val FOLLOWED_ROUTE = stringPreferencesKey("followed_route")
    }

    /**
     * Light, dark or follow-the-system.
     *
     * Unknown stored values fall back to [ThemeMode.SYSTEM] rather than throwing: a preference file
     * survives a downgrade, and a crash loop at startup is a far worse outcome than the wrong theme.
     * This is the opposite of the rule for enums stored in the *database*, where an unknown value
     * means the schema gate missed something and should be loud.
     */
    val themeMode: Flow<ThemeMode> =
        store.data.map { prefs ->
            prefs[Keys.THEME_MODE]?.let { name ->
                runCatching { enumValueOf<ThemeMode>(name) }.getOrNull()
            } ?: ThemeMode.SYSTEM
        }

    /**
     * Whether to take the wallpaper's palette instead of the app's own (ADR-0006).
     *
     * Defaults **off**. With it on, nothing on Android 12+ reads the generated scheme at all, so the
     * app's identity would be invisible on almost every device that runs it.
     */
    val materialYou: Flow<Boolean> = store.data.map { it[Keys.MATERIAL_YOU] ?: false }

    /** The remembered export folder's tree Uri: a hint, checked against the grants on every read. */
    val backupFolderUri: Flow<String?> = store.data.map { it[Keys.BACKUP_FOLDER_URI] }

    /** When the last whole Everything export started, or null before the first (docs/phase-6.md § 7). */
    val lastEverythingExport: Flow<Instant?> =
        store.data.map { prefs -> prefs[Keys.LAST_EVERYTHING_EXPORT]?.let(Instant::ofEpochMilli) }

    /** The camera as the map screen last stopped on, or null before it ever has. */
    val mapCamera: Flow<MapCamera?> =
        store.data.map { prefs ->
            val latitude = prefs[Keys.MAP_LATITUDE] ?: return@map null
            val longitude = prefs[Keys.MAP_LONGITUDE] ?: return@map null
            val zoom = prefs[Keys.MAP_ZOOM] ?: return@map null
            MapCamera(latitude, longitude, zoom)
        }

    /** Whether the motion sensor resumes a paused recording (docs/phase-6.md § 3). On by default. */
    val autoResume: Flow<Boolean> = store.data.map { it[Keys.AUTO_RESUME] ?: true }

    /** Whether Record has asked for notifications, which it does once (docs/phase-6.md § 2). */
    val notificationsAsked: Flow<Boolean> = store.data.map { it[Keys.NOTIFICATIONS_ASKED] ?: false }

    /** The Route being followed and its Progress, null when none is; unreadable reads as none, like [themeMode]. */
    val followedRoute: Flow<FollowedRoute?> =
        store.data.map { prefs ->
            prefs[Keys.FOLLOWED_ROUTE]?.let(::decodeFollowed)
        }

    /** [followedRoute] read once, removing a value that no longer decodes so its points do not linger. */
    suspend fun readFollowedRoute(): FollowedRoute? {
        var followed: FollowedRoute? = null
        store.edit { prefs ->
            val stored = prefs[Keys.FOLLOWED_ROUTE] ?: return@edit
            followed = decodeFollowed(stored)
            if (followed == null) prefs.remove(Keys.FOLLOWED_ROUTE)
        }
        return followed
    }

    /** Stored by name; Hiking on a first launch or for a name this build does not know. */
    val mapMode: Flow<Mode> =
        store.data.map { stored -> Mode.entries.firstOrNull { it.name == stored[Keys.MAP_MODE] } ?: Mode.HIKING }

    suspend fun setMapMode(mode: Mode) {
        store.edit { it[Keys.MAP_MODE] = mode.name }
    }

    /** The map's chip, stored by name as [mapMode] is; All on a first launch or for a name this build does not know. */
    val mapBikeRouteFilter: Flow<BikeRouteFilter> =
        store.data.map { stored ->
            BikeRouteFilter.entries.firstOrNull { it.name == stored[Keys.MAP_BIKE_ROUTE_FILTER] } ?: BikeRouteFilter.ALL
        }

    suspend fun setMapBikeRouteFilter(filter: BikeRouteFilter) {
        store.edit { it[Keys.MAP_BIKE_ROUTE_FILTER] = filter.name }
    }

    /**
     * The theme mode, read once, before any Activity exists.
     *
     * The one place a `suspend` read is worth its cost: `MainApplication.onCreate` needs the answer
     * to call `applyThemeMode` *before* the first window is created, and a `Flow` collected in a
     * composition arrives a frame too late — which is a light flash on every cold start for a user
     * who chose dark.
     */
    suspend fun themeModeNow(): ThemeMode = themeMode.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAutoResume(enabled: Boolean) {
        store.edit { it[Keys.AUTO_RESUME] = enabled }
    }

    suspend fun setNotificationsAsked() {
        store.edit { it[Keys.NOTIFICATIONS_ASKED] = true }
    }

    suspend fun setMaterialYou(enabled: Boolean) {
        store.edit { it[Keys.MATERIAL_YOU] = enabled }
    }

    suspend fun setMapCamera(camera: MapCamera) {
        store.edit { prefs ->
            prefs[Keys.MAP_LATITUDE] = camera.latitude
            prefs[Keys.MAP_LONGITUDE] = camera.longitude
            prefs[Keys.MAP_ZOOM] = camera.zoom
        }
    }

    suspend fun setLastEverythingExport(at: Instant) {
        store.edit { it[Keys.LAST_EVERYTHING_EXPORT] = at.toEpochMilli() }
    }

    /** Never moves it back: a restored archive can be older than the last export. */
    suspend fun raiseLastEverythingExport(to: Instant) {
        store.edit { prefs ->
            val current = prefs[Keys.LAST_EVERYTHING_EXPORT]
            if (current == null || current < to.toEpochMilli()) prefs[Keys.LAST_EVERYTHING_EXPORT] = to.toEpochMilli()
        }
    }

    suspend fun setFollowedRoute(followed: FollowedRoute?) {
        store.edit { prefs ->
            if (followed == null) {
                prefs.remove(Keys.FOLLOWED_ROUTE)
            } else {
                prefs[Keys.FOLLOWED_ROUTE] = json.encodeToString(FollowedRoute.serializer(), followed)
            }
        }
    }

    suspend fun setBackupFolderUri(uri: String?) {
        store.edit { prefs ->
            if (uri == null) prefs.remove(Keys.BACKUP_FOLDER_URI) else prefs[Keys.BACKUP_FOLDER_URI] = uri
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun decodeFollowed(stored: String): FollowedRoute? =
    runCatching { json.decodeFromString<FollowedRoute>(stored) }.getOrNull()
