package ro.mehene.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminTapDetectorTest {
    @Test
    fun opensOnFifthTapInsideWindow() {
        var now = 1_000L
        val detector = AdminTapDetector(clock = { now })

        repeat(4) {
            assertFalse(detector.onTap())
            now += 500L
        }
        assertTrue(detector.onTap())
    }

    @Test
    fun resetsAfterWindowExpires() {
        var now = 1_000L
        val detector = AdminTapDetector(clock = { now })

        repeat(4) {
            assertFalse(detector.onTap())
            now += 500L
        }
        now += AdminTapDetector.DEFAULT_WINDOW_MS + 1L
        assertFalse(detector.onTap())
        repeat(3) {
            now += 100L
            assertFalse(detector.onTap())
        }
        now += 100L
        assertTrue(detector.onTap())
    }

    @Test
    fun acceptsTapAtWindowBoundary() {
        var now = 1_000L
        val detector = AdminTapDetector(clock = { now })

        repeat(4) {
            assertFalse(detector.onTap())
            now += 750L
        }
        now = 1_000L + AdminTapDetector.DEFAULT_WINDOW_MS
        assertTrue(detector.onTap())
    }

    @Test
    fun resetsAfterSuccessfulSequence() {
        var now = 1_000L
        val detector = AdminTapDetector(clock = { now })

        repeat(4) {
            assertFalse(detector.onTap())
            now += 100L
        }
        assertTrue(detector.onTap())
        now += 100L
        repeat(4) {
            assertFalse(detector.onTap())
            now += 100L
        }
        assertTrue(detector.onTap())
    }
}
