package ro.mehene.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogJsonCodecPathTest {
    @Test
    fun normalizesBuilderPaths() {
        assertEquals(
            listOf("Povesti", "Sezonul 01", "Episod 01.mp4"),
            normalizedCatalogSegments("Povesti\\\\Sezonul 01/Episod 01.mp4"),
        )
    }

    @Test
    fun acceptsTheCatalogRoot() {
        assertEquals(emptyList<String>(), normalizedCatalogSegments("."))
    }

    @Test
    fun rejectsTraversalAndEmptyPaths() {
        assertNull(normalizedCatalogSegments("../Episod.mp4"))
        assertNull(normalizedCatalogSegments("Povesti/../Episod.mp4"))
        assertNull(normalizedCatalogSegments(" "))
    }
}
