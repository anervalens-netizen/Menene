package ro.mehene.app

internal class AdminTapDetector(
    private val requiredTaps: Int = DEFAULT_REQUIRED_TAPS,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val clock: () -> Long,
) {
    private var firstTapAt = 0L
    private var tapCount = 0

    fun onTap(): Boolean {
        val now = clock()
        if (firstTapAt == 0L || now - firstTapAt > windowMs) {
            firstTapAt = now
            tapCount = 0
        }
        tapCount += 1
        if (tapCount < requiredTaps) return false
        reset()
        return true
    }

    fun reset() {
        firstTapAt = 0L
        tapCount = 0
    }

    companion object {
        const val DEFAULT_REQUIRED_TAPS = 5
        const val DEFAULT_WINDOW_MS = 3_000L
    }
}
