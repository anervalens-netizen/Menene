package ro.mehene.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressPolicyTest {
    @Test
    fun distinguishesUnwatchedInProgressAndCompleted() {
        assertEquals(
            EpisodePlaybackState.UNWATCHED,
            PlaybackProgressPolicy.evaluate(2_000L, 100_000L).state,
        )

        val inProgress = PlaybackProgressPolicy.evaluate(40_000L, 100_000L)
        assertEquals(EpisodePlaybackState.IN_PROGRESS, inProgress.state)
        assertTrue(inProgress.fraction in 0.39f..0.41f)

        assertEquals(
            EpisodePlaybackState.COMPLETED,
            PlaybackProgressPolicy.evaluate(96_000L, 100_000L).state,
        )
    }
}
