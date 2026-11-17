package app.kozica.data.backup

import app.kozica.media.MediaKind

/**
 * What a manual export carries besides the database and the preferences, which every scope does
 * (docs/phase-6.md § 7). Stored by name in the manifest.
 */
enum class BackupScope(
    val mediaKinds: List<MediaKind>,
) {
    Records(listOf(MediaKind.Thumbnail, MediaKind.Bike)),

    Everything(MediaKind.entries.toList()),
    ;

    /** For the filename, which is for people; a restore reads the manifest. */
    val slug: String get() = name.lowercase()
}
