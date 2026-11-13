package app.kozica

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every route in the app, as a key.
 *
 * **Decide the navigation structure before the first screen.** Routes that exist from the start —
 * even where the screen behind them is still a stub — are what stop navigation from being reverse
 * engineered out of whatever the screens happened to need.
 *
 * Kotlin note: `data object` is a singleton with a sensible `toString`/`equals` — the right shape
 * for a route with no arguments. A route that carries arguments is a `data class` instead, and
 * `@Serializable` is what lets Nav3 save the whole back stack across process death.
 */
@Serializable data object Home : NavKey

/** The second tab, beside [Home]; pushed over it, so Back returns to the map. */
@Serializable data object Routes : NavKey

/** The third tab, pushed over [Home] as [Routes] is (docs/phase-6.md § 4). */
@Serializable data object Hikes : NavKey

@Serializable data object Settings : NavKey

@Serializable data object Backup : NavKey

@Serializable data object Safety : NavKey

/**
 * Help and feedback, and the licences below it — both pushed from Settings' *About* section
 * rather than being roots of their own. Somewhere you go once, from the place you already go to
 * look things up.
 */
@Serializable data object Support : NavKey

@Serializable data object Licences : NavKey

@Serializable
data class LicenceText(
    val spdxId: String,
    val title: String,
) : NavKey
