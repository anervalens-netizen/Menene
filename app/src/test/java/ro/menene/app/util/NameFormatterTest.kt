package ro.menene.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NameFormatterTest {
    @Test
    fun createsReadableTitleFromFileName() {
        assertEquals("Episod 01 – Pilot", NameFormatter.displayName("Episod_01-Pilot.mp4"))
    }
}
