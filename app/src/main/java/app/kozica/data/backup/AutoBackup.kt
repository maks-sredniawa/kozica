package app.kozica.data.backup

import app.kozica.data.PRESERVED_SIDECAR_SUFFIXES
import app.kozica.media.MediaKind
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant

/*
 * Auto Backup as functions over `File` (ADR-0005, docs/phase-6.md § 7): the agent runs in a process
 * bound to the base `Application`, with no container to reach for.
 */

/**
 * The checkpointed copy the agent hands over, relative to `filesDir`. Under `filesDir` because a
 * full restore puts each file back at its own relative path, where [adoptRestoredDatabase] finds it.
 */
const val AUTO_BACKUP_STAGING_PATH = "autobackup/kozica.db"

const val AUTO_BACKUP_MARKER_FILE = "auto-backup-marker.txt"

val AUTO_BACKUP_STALE_AFTER: Duration = Duration.ofDays(14)

/**
 * Below the ~25 MB quota, because Android rejects an over-quota dataset whole, the database with it.
 */
const val AUTO_BACKUP_BUDGET_BYTES: Long = 20L * 1024 * 1024

private const val MARKER_LAST_BACKUP_KEY = "lastBackupAtEpochMilli"
private const val MARKER_EXCLUDED_KEY = "excludedThumbnails"
private const val MARKER_OVER_QUOTA_KEY = "overQuota"

data class AutoBackupSet(
    val files: List<File>,
    val excludedThumbnails: Int,
)

/**
 * The thumbnails that fit under what [budget] leaves after [coreBytes], in the order given. Skips
 * rather than stops, so one large file does not exclude the smaller ones after it.
 */
fun admitThumbnails(
    coreBytes: Long,
    newestFirst: List<File>,
    budget: Long = AUTO_BACKUP_BUDGET_BYTES,
): AutoBackupSet {
    var remaining = (budget - coreBytes).coerceAtLeast(0)
    val admitted = mutableListOf<File>()
    for (file in newestFirst) {
        val size = file.length()
        if (size <= remaining) {
            admitted += file
            remaining -= size
        }
    }
    return AutoBackupSet(files = admitted, excludedThumbnails = newestFirst.size - admitted.size)
}

/**
 * What the agent carries: the staged database and the preferences always, then the Bikes' photos,
 * few and each a Bike's only picture, then thumbnails newest first in what the budget leaves. Photos only on a [deviceToDeviceTransfer], which has no quota, so there
 * every thumbnail goes too. `preserved/` and the marker are never in it.
 */
fun autoBackupFileSet(
    filesDir: File,
    stagedDatabase: File,
    deviceToDeviceTransfer: Boolean,
    budget: Long = AUTO_BACKUP_BUDGET_BYTES,
): AutoBackupSet {
    val core =
        buildList {
            if (stagedDatabase.isFile) add(stagedDatabase)
            File(filesDir, PREFERENCES_FILE_PATH).takeIf { it.isFile }?.let(::add)
            if (deviceToDeviceTransfer) addAll(mediaFilesFor(listOf(MediaKind.Photo), filesDir).map { it.file })
        }
    val thumbnails = listOf(MediaKind.Bike, MediaKind.Thumbnail).flatMap { newestFirst(it, filesDir) }
    val admission =
        if (deviceToDeviceTransfer) {
            AutoBackupSet(thumbnails, 0)
        } else {
            admitThumbnails(core.sumOf { it.length() }, thumbnails, budget)
        }
    return AutoBackupSet(core + admission.files, admission.excludedThumbnails)
}

private fun newestFirst(
    kind: MediaKind,
    filesDir: File,
): List<File> =
    mediaFilesFor(listOf(kind), filesDir)
        .map { it.file }
        .sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name })

/** [overQuota]: the last attempt was rejected whole, and carries no time worth showing. */
data class AutoBackupMarker(
    val lastBackupAt: Instant?,
    val excludedThumbnails: Int = 0,
    val overQuota: Boolean = false,
)

fun writeAutoBackupMarker(
    filesDir: File,
    at: Instant,
    excludedThumbnails: Int = 0,
) = writeMarker(filesDir, "$MARKER_LAST_BACKUP_KEY=${at.toEpochMilli()}\n$MARKER_EXCLUDED_KEY=$excludedThumbnails\n")

/** Over what [writeAutoBackupMarker] wrote moments before, from the same attempt's measuring pass. */
fun writeOverQuotaMarker(filesDir: File) = writeMarker(filesDir, "$MARKER_OVER_QUOTA_KEY=true\n")

/** Temp-then-rename, so a kill mid-write leaves the previous marker rather than garbage. */
private fun writeMarker(
    filesDir: File,
    text: String,
) {
    val marker = File(filesDir, AUTO_BACKUP_MARKER_FILE)
    val part = File(filesDir, "$AUTO_BACKUP_MARKER_FILE.part")
    try {
        filesDir.mkdirs()
        part.writeText(text)
        if (!part.renameTo(marker)) {
            part.copyTo(marker, overwrite = true)
            part.delete()
        }
    } catch (e: IOException) {
        part.delete()
    }
}

/** Null when absent or unreadable, which renders as "none recorded", never as a working backup. */
fun readAutoBackupMarker(filesDir: File): AutoBackupMarker? {
    val marker = File(filesDir, AUTO_BACKUP_MARKER_FILE)
    if (!marker.isFile) return null
    return try {
        val values =
            marker
                .readLines()
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
                }.toMap()
        val overQuota = values[MARKER_OVER_QUOTA_KEY]?.trim() == "true"
        val lastBackupAt = values[MARKER_LAST_BACKUP_KEY]?.trim()?.toLongOrNull()?.let(Instant::ofEpochMilli)
        if (lastBackupAt == null && !overQuota) return null
        AutoBackupMarker(
            lastBackupAt = lastBackupAt,
            excludedThumbnails = values[MARKER_EXCLUDED_KEY]?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            overQuota = overQuota,
        )
    } catch (e: IOException) {
        null
    }
}

fun clearAutoBackupMarker(filesDir: File) {
    File(filesDir, AUTO_BACKUP_MARKER_FILE).delete()
}

/** Backup settings' states (docs/phase-6.md § 7). */
sealed interface AutoBackupStatus {
    data object NeverRecorded : AutoBackupStatus

    data object OverQuota : AutoBackupStatus

    data class Recorded(
        val at: Instant,
        val stale: Boolean,
        val excludedThumbnails: Int = 0,
    ) : AutoBackupStatus
}

/** A marker dated in the future is a clock that moved, and reads as fresh. */
fun autoBackupStatus(
    marker: AutoBackupMarker?,
    now: Instant,
): AutoBackupStatus {
    if (marker?.overQuota == true) return AutoBackupStatus.OverQuota
    val at = marker?.lastBackupAt ?: return AutoBackupStatus.NeverRecorded
    return AutoBackupStatus.Recorded(
        at = at,
        stale = Duration.between(at, now) > AUTO_BACKUP_STALE_AFTER,
        excludedThumbnails = marker.excludedThumbnails,
    )
}

/** Where builds before 0.9.0 staged the copy, so their backups still restore. */
private const val LEGACY_STAGING_PATH = "auto-backup-staged.db"

/**
 * Moves a restored copy into place, from `onRestoreFinished` and again at process start as the
 * backstop for one that never ran. Only where there is no live database: beside one, the copy is
 * stale and is dropped.
 */
fun adoptRestoredDatabase(
    filesDir: File,
    databaseFile: File,
): Boolean {
    val staged =
        listOf(AUTO_BACKUP_STAGING_PATH, LEGACY_STAGING_PATH).map { File(filesDir, it) }.filter { it.isFile }
    if (staged.isEmpty()) return false
    val adopted =
        if (databaseFile.isFile) {
            false
        } else {
            databaseFile.parentFile?.mkdirs()
            staged.first().copyTo(databaseFile, overwrite = true)
            PRESERVED_SIDECAR_SUFFIXES.forEach { File(databaseFile.path + it).delete() }
            true
        }
    staged.forEach { it.delete() }
    File(filesDir, AUTO_BACKUP_STAGING_PATH).parentFile?.delete()
    return adopted
}
