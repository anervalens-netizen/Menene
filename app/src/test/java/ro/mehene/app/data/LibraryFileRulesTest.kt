package ro.mehene.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFileRulesTest {
    @Test
    fun publishesOnlyTabletFriendlyContainers() {
        assertTrue(LibraryFileRules.isSupportedVideoName("Episod 1.MP4"))
        assertTrue(LibraryFileRules.isSupportedVideoName("Episod 2.m4v"))
        assertFalse(LibraryFileRules.isSupportedVideoName("Episod 3.mkv"))
        assertTrue(LibraryFileRules.isKnownVideoName("Episod 3.mkv"))
    }
}
