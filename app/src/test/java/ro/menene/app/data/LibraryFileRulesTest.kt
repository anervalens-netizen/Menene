package ro.menene.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFileRulesTest {
    @Test
    fun detectsSupportedVideoAndSeasonFolders() {
        assertTrue(LibraryFileRules.isSupportedVideoName("Episod 01.MP4"))
        assertFalse(LibraryFileRules.isSupportedVideoName("Episod 01.mkv"))
        assertEquals(2, LibraryFileRules.seasonNumber("Sezonul 02"))
        assertEquals(12, LibraryFileRules.seasonNumber("S12"))
        assertEquals(3, LibraryFileRules.episodeNumber("S01E03 - Pilot.mp4", 1))
        assertEquals(12, LibraryFileRules.episodeNumber("Episodul 12.mp4", 1))
    }
}
