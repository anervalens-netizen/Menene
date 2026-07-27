package ro.mehene.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalOrderComparatorTest {
    @Test
    fun ordersEpisodeNumbersNaturally() {
        val names = listOf("Episod 10.mp4", "Episod 2.mp4", "Episod 1.mp4")
        assertEquals(
            listOf("Episod 1.mp4", "Episod 2.mp4", "Episod 10.mp4"),
            names.sortedWith(NaturalOrderComparator),
        )
    }
}
