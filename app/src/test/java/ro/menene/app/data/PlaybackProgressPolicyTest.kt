package ro.menene.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProgressPolicyTest {
    @Test
    fun distinguishesUnwatchedInProgressAndCompleted() {
        assertEquals(
            EpisodePlaybackState.UNWATCHED,
            PlaybackProgressPolicy.evaluate("e", 2_000, 100_000, 1).state,
        )
        assertEquals(
            EpisodePlaybackState.IN_PROGRESS,
            PlaybackProgressPolicy.evaluate("e", 30_000, 100_000, 1).state,
        )
        assertEquals(
            EpisodePlaybackState.COMPLETED,
            PlaybackProgressPolicy.evaluate("e", 96_000, 100_000, 1).state,
        )
    }
}
