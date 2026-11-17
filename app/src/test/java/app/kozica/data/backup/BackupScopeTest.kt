package app.kozica.data.backup

import app.kozica.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupScopeTest {
    /** A new media kind left out of every scope would be missing from every export, silently. */
    @Test
    fun `the widest scope covers every media kind`() {
        assertEquals(MediaKind.entries.toSet(), BackupScope.Everything.mediaKinds.toSet())
    }

    @Test
    fun `Records carries the thumbnails and the Bikes' photos, and not the photos`() {
        assertEquals(listOf(MediaKind.Thumbnail, MediaKind.Bike), BackupScope.Records.mediaKinds)
        assertEquals("bikes", MediaKind.Bike.directory)
    }

    @Test
    fun `every scope has a filename-safe slug`() {
        assertTrue(BackupScope.entries.all { it.slug.matches(Regex("[a-z][a-z-]*")) })
    }
}
