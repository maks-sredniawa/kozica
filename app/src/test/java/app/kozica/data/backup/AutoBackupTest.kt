package app.kozica.data.backup

import app.kozica.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.Instant

/** docs/phase-6.md § "Tests", E: Auto Backup admission and the marker. */
class AutoBackupTest {
    @get:Rule val temp = TemporaryFolder()

    private val photo = "3f2504e0-4f89-41d3-9a0c-0305e82c3301.jpg"
    private val thumbnail = "3f2504e0-4f89-41d3-9a0c-0305e82c3302.jpg"
    private val olderThumbnail = "3f2504e0-4f89-41d3-9a0c-0305e82c3303.jpg"
    private val bike = "3f2504e0-4f89-41d3-9a0c-0305e82c3304.jpg"

    private lateinit var filesDir: File
    private lateinit var staged: File

    @Before
    fun setUp() {
        filesDir = temp.newFolder("files")
        staged = File(filesDir, AUTO_BACKUP_STAGING_PATH).also { write(it, "a checkpointed database") }
        write(File(filesDir, PREFERENCES_FILE_PATH), "preferences")
        write(File(filesDir, "${MediaKind.Photo.directory}/$photo"), "photo")
        write(File(filesDir, "${MediaKind.Thumbnail.directory}/$thumbnail"), "thumbnail").setLastModified(2_000)
        write(
            File(filesDir, "${MediaKind.Thumbnail.directory}/$olderThumbnail"),
            "older thumbnail",
        ).setLastModified(1_000)
        write(File(filesDir, "preserved/db-20261027T073547Z.db"), "a copy taken before a wipe")
        write(File(filesDir, "${MediaKind.Thumbnail.directory}/notes.txt"), "stray")
    }

    @Test
    fun `a cloud backup carries the database, the preferences and the thumbnails`() {
        val set = autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = false)

        assertEquals(
            setOf("kozica.db", "app_preferences.preferences_pb", thumbnail, olderThumbnail),
            set.files.map { it.name }.toSet(),
        )
        assertEquals(0, set.excludedThumbnails)
    }

    @Test
    fun `a cloud backup carries the Bikes' photos with the thumbnails`() {
        write(File(filesDir, "${MediaKind.Bike.directory}/$bike"), "bike").setLastModified(1_500)

        val set = autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = false)

        assertTrue(set.files.any { it.path.endsWith("/${MediaKind.Bike.directory}/$bike") })
        assertEquals(0, set.excludedThumbnails)
    }

    @Test
    fun `a Bike's photo takes its turn under the thumbnails' budget, newest first`() {
        write(File(filesDir, "${MediaKind.Bike.directory}/$bike"), "bike").setLastModified(3_000)
        val coreBytes = staged.length() + File(filesDir, PREFERENCES_FILE_PATH).length()

        val set =
            autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = false, budget = coreBytes + "bike".length)

        assertEquals(setOf("kozica.db", "app_preferences.preferences_pb", bike), set.files.map { it.name }.toSet())
        assertEquals(2, set.excludedThumbnails)
    }

    @Test
    fun `the photos, the preserved copies, stray files and the marker are never in a cloud backup`() {
        writeAutoBackupMarker(filesDir, Instant.now())

        val paths = autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = false).files.map { it.path }

        assertTrue(paths.none { it.contains("/${MediaKind.Photo.directory}/") })
        assertTrue(paths.none { it.contains("/preserved/") })
        assertTrue(paths.none { it.endsWith("notes.txt") })
        assertTrue(paths.none { it.endsWith(AUTO_BACKUP_MARKER_FILE) })
    }

    @Test
    fun `thumbnails past the budget are left out and counted, and the database and preferences stay`() {
        val coreBytes = staged.length() + File(filesDir, PREFERENCES_FILE_PATH).length()

        val set = autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = false, budget = coreBytes)

        assertEquals(setOf("kozica.db", "app_preferences.preferences_pb"), set.files.map { it.name }.toSet())
        assertEquals(2, set.excludedThumbnails)
    }

    @Test
    fun `the newest thumbnail takes the room there is`() {
        val coreBytes = staged.length() + File(filesDir, PREFERENCES_FILE_PATH).length()
        val budget = coreBytes + "thumbnail".length

        val set = autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = false, budget = budget)

        assertTrue(set.files.any { it.name == thumbnail })
        assertTrue(set.files.none { it.name == olderThumbnail })
        assertEquals(1, set.excludedThumbnails)
    }

    @Test
    fun `a device-to-device transfer carries the photos and every thumbnail, with no budget`() {
        val set = autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = true, budget = 1)

        assertTrue(set.files.map { it.name }.containsAll(listOf("kozica.db", photo, thumbnail, olderThumbnail)))
        assertEquals(0, set.excludedThumbnails)
        assertTrue(set.files.none { it.path.contains("/preserved/") })
    }

    @Test
    fun `a phone whose database was never opened backs up what it has`() {
        staged.delete()

        val names = autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = false).files.map { it.name }

        assertTrue("kozica.db" !in names)
        assertTrue(thumbnail in names)
    }

    @Test
    fun `admission never goes over the budget`() {
        val files = listOf(sized("a", 300), sized("b", 300), sized("c", 300))

        val admission = admitThumbnails(coreBytes = 100, newestFirst = files, budget = 1_000)

        assertEquals(files, admission.files)
        assertTrue(100 + admission.files.sumOf { it.length() } <= 1_000)
        assertEquals(1, admitThumbnails(coreBytes = 101, newestFirst = files, budget = 1_000).excludedThumbnails)
    }

    @Test
    fun `a core already over the budget admits nothing`() {
        val admission = admitThumbnails(coreBytes = 5_000, newestFirst = listOf(sized("a", 1)), budget = 1_000)

        assertTrue(admission.files.isEmpty())
        assertEquals(1, admission.excludedThumbnails)
    }

    @Test
    fun `one large thumbnail does not exclude the smaller ones after it`() {
        val huge = sized("huge", 900)
        val small = sized("small", 100)

        val admission = admitThumbnails(coreBytes = 0, newestFirst = listOf(huge, small), budget = 500)

        assertEquals(listOf(small), admission.files)
        assertEquals(1, admission.excludedThumbnails)
    }

    @Test
    fun `the marker survives a round trip and leaves no part file`() {
        val at = Instant.parse("2026-10-27T06:15:00Z")

        writeAutoBackupMarker(filesDir, at, excludedThumbnails = 12)

        assertEquals(AutoBackupMarker(at, excludedThumbnails = 12), readAutoBackupMarker(filesDir))
        assertFalse(File(filesDir, "$AUTO_BACKUP_MARKER_FILE.part").exists())
    }

    @Test
    fun `an absent, unreadable or cleared marker reads as none`() {
        assertNull(readAutoBackupMarker(filesDir))

        write(File(filesDir, AUTO_BACKUP_MARKER_FILE), "who knows what this is")
        assertNull(readAutoBackupMarker(filesDir))

        writeAutoBackupMarker(filesDir, Instant.now())
        clearAutoBackupMarker(filesDir)
        assertNull(readAutoBackupMarker(filesDir))
    }

    @Test
    fun `an over-quota attempt reads as such until a backup goes through`() {
        writeAutoBackupMarker(filesDir, Instant.parse("2026-10-27T06:15:00Z"))
        writeOverQuotaMarker(filesDir)

        assertEquals(AutoBackupStatus.OverQuota, autoBackupStatus(readAutoBackupMarker(filesDir), Instant.now()))

        val at = Instant.parse("2026-10-28T06:15:00Z")
        writeAutoBackupMarker(filesDir, at)
        assertEquals(AutoBackupStatus.Recorded(at, stale = false), autoBackupStatus(readAutoBackupMarker(filesDir), at))
    }

    @Test
    fun `no marker is never a working backup`() {
        assertEquals(AutoBackupStatus.NeverRecorded, autoBackupStatus(null, Instant.now()))
    }

    @Test
    fun `a marker goes stale past 14 days`() {
        val now = Instant.parse("2026-10-27T06:15:00Z")

        fun staleAfterDays(days: Long) =
            (
                autoBackupStatus(
                    AutoBackupMarker(now.minus(Duration.ofDays(days))),
                    now,
                ) as AutoBackupStatus.Recorded
            ).stale

        assertFalse(staleAfterDays(13))
        assertFalse(staleAfterDays(14))
        assertTrue(staleAfterDays(15))
        assertFalse(staleAfterDays(-30))
    }

    @Test
    fun `the status carries the excluded count`() {
        val now = Instant.parse("2026-10-27T06:15:00Z")

        val status = autoBackupStatus(AutoBackupMarker(now, excludedThumbnails = 7), now)

        assertEquals(7, (status as AutoBackupStatus.Recorded).excludedThumbnails)
    }

    @Test
    fun `a restored database is moved into place, its sidecars and staging gone`() {
        val databaseFile = File(temp.newFolder("databases"), "kozica.db")
        write(File("${databaseFile.path}-wal"), "a log for a file that no longer exists")

        assertTrue(adoptRestoredDatabase(filesDir, databaseFile))

        assertEquals("a checkpointed database", databaseFile.readText())
        assertFalse(File("${databaseFile.path}-wal").exists())
        assertFalse(staged.exists())
        assertFalse(staged.parentFile!!.exists())
    }

    @Test
    fun `a backup staged by a build before 0_9_0 is adopted too`() {
        staged.delete()
        write(File(filesDir, "auto-backup-staged.db"), "an older backup")
        val databaseFile = File(temp.newFolder("databases"), "kozica.db")

        assertTrue(adoptRestoredDatabase(filesDir, databaseFile))

        assertEquals("an older backup", databaseFile.readText())
        assertFalse(File(filesDir, "auto-backup-staged.db").exists())
    }

    @Test
    fun `a staged copy beside a live database is dropped, never adopted over it`() {
        val databaseFile = File(temp.newFolder("databases"), "kozica.db").also { write(it, "a summer of hikes") }

        assertFalse(adoptRestoredDatabase(filesDir, databaseFile))

        assertEquals("a summer of hikes", databaseFile.readText())
        assertFalse(staged.exists())
    }

    @Test
    fun `restored preferences replace the live file at the next start, once`() {
        write(File(filesDir, RESTORED_PREFERENCES_PATH), "restored preferences")

        assertTrue(adoptRestoredPreferences(filesDir))
        assertEquals("restored preferences", File(filesDir, PREFERENCES_FILE_PATH).readText())
        assertFalse(adoptRestoredPreferences(filesDir))
    }

    @Test
    fun `a restored Everything archive's time is handed over at the next start, once`() {
        write(File(filesDir, RESTORED_EVERYTHING_EXPORT_PATH), "1793088000000")

        assertEquals(Instant.ofEpochMilli(1_793_088_000_000), adoptRestoredEverythingExport(filesDir))
        assertNull(adoptRestoredEverythingExport(filesDir))
    }

    private fun sized(
        name: String,
        bytes: Int,
    ): File = write(File(temp.root, name), "x".repeat(bytes))

    private fun write(
        file: File,
        text: String,
    ): File {
        file.parentFile?.mkdirs()
        file.writeText(text)
        return file
    }
}
