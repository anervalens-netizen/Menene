package ro.mehene.app

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.mehene.app.data.LibraryResult
import ro.mehene.app.data.PlaybackMode
import ro.mehene.app.databinding.ActivityPlayerBinding
import ro.mehene.app.kiosk.KioskController
import ro.mehene.app.media.PlaybackQueuePlanner
import ro.mehene.app.model.EpisodeItem
import ro.mehene.app.model.LibraryCatalog
import java.util.Locale

@UnstableApi
class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private val handler = Handler(Looper.getMainLooper())
    private val container by lazy { meheneContainer() }

    private var player: ExoPlayer? = null
    private var catalog: LibraryCatalog? = null
    private var currentEpisode: EpisodeItem? = null
    private var playbackMode = PlaybackMode.SINGLE
    private var playbackModeProvided = false
    private var preferredAudioLanguage = "ron"
    private var sessionEpisodeId = ""
    private var savedPositionMs = 0L
    private var pausedByUser = false
    private var playbackFailed = false
    private var countdownNext: EpisodeItem? = null
    private var countdownSeconds = 0
    private var restoredNextEpisodeId: String? = null
    private var restoredCountdownSeconds = 0
    private var isUserSeeking = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    binding.loading.visibility = View.VISIBLE
                    handler.removeCallbacks(bufferTimeout)
                    handler.postDelayed(bufferTimeout, BUFFER_TIMEOUT_MS)
                }
                Player.STATE_READY -> {
                    playbackFailed = false
                    handler.removeCallbacks(bufferTimeout)
                    handler.removeCallbacks(seekControlsUpdater)
                    handler.post(seekControlsUpdater)
                    binding.loading.visibility = View.GONE
                    binding.errorPanel.visibility = View.GONE
                    binding.playbackControls.visibility = View.VISIBLE
                    updateSeekControls()
                }
                Player.STATE_ENDED -> {
                    handler.removeCallbacks(bufferTimeout)
                    binding.loading.visibility = View.GONE
                    handleEpisodeEnded()
                }
                Player.STATE_IDLE -> binding.loading.visibility = View.GONE
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            saveProgress(critical = true)
            playbackFailed = true
            handler.removeCallbacks(bufferTimeout)
            handler.removeCallbacks(seekControlsUpdater)
            binding.loading.visibility = View.GONE
            binding.playbackControls.visibility = View.GONE
            binding.errorPanel.visibility = View.VISIBLE
        }
    }

    private val bufferTimeout = Runnable {
        if (player?.playbackState == Player.STATE_BUFFERING) {
            saveProgress(critical = true)
            playbackFailed = true
            player?.pause()
            handler.removeCallbacks(seekControlsUpdater)
            binding.loading.visibility = View.GONE
            binding.playbackControls.visibility = View.GONE
            binding.errorPanel.visibility = View.VISIBLE
        }
    }

    private val periodicProgressSave = object : Runnable {
        override fun run() {
            saveProgress(critical = false)
            if (player != null) handler.postDelayed(this, PROGRESS_SAVE_INTERVAL_MS)
        }
    }

    private val seekControlsUpdater = object : Runnable {
        override fun run() {
            if (!isUserSeeking) updateSeekControls()
            if (player != null) handler.postDelayed(this, SEEK_UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setVolumeControlStream(AudioManager.STREAM_MUSIC)

        sessionEpisodeId = savedInstanceState?.getString(STATE_EPISODE_ID)
            ?: intent.getStringExtra(EXTRA_EPISODE_ID).orEmpty()
        val restoredPlaybackMode = savedInstanceState?.getString(STATE_PLAYBACK_MODE)
        val requestedPlaybackMode = intent.getStringExtra(EXTRA_PLAYBACK_MODE)
        playbackModeProvided = restoredPlaybackMode != null || requestedPlaybackMode != null
        playbackMode = restoredPlaybackMode
            ?.let { runCatching { PlaybackMode.valueOf(it) }.getOrNull() }
            ?: requestedPlaybackMode
                ?.let { runCatching { PlaybackMode.valueOf(it) }.getOrNull() }
                ?: PlaybackMode.SINGLE
        pausedByUser = savedInstanceState?.getBoolean(STATE_PAUSED_BY_USER) ?: false
        savedPositionMs = savedInstanceState?.getLong(STATE_POSITION_MS) ?: 0L
        restoredNextEpisodeId = savedInstanceState?.getString(STATE_NEXT_EPISODE_ID)
        restoredCountdownSeconds = savedInstanceState?.getInt(STATE_COUNTDOWN_SECONDS) ?: 0

        binding.playerView.useController = false
        binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        binding.closeButton.setOnClickListener { closePlayer() }
        binding.volumeUpButton.setOnClickListener { changeVolume(AudioManager.ADJUST_RAISE) }
        binding.volumeDownButton.setOnClickListener { changeVolume(AudioManager.ADJUST_LOWER) }
        binding.seekBackwardButton.setOnClickListener { seekBy(-SEEK_STEP_MS) }
        binding.seekForwardButton.setOnClickListener { seekBy(SEEK_STEP_MS) }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = true
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                binding.currentTime.text = formatTime(positionForProgress(progress))
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val target = positionForProgress(seekBar.progress)
                player?.seekTo(target)
                savedPositionMs = target
                isUserSeeking = false
                updateSeekControls(positionOverrideMs = target)
            }
        })
        binding.playerView.setOnClickListener { togglePlayback() }
        binding.retryButton.setOnClickListener { retryPlayback() }
        binding.nextCancelButton.setOnClickListener { closePlayer() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        loadSession()
        KioskController.applyImmersive(this)
    }

    override fun onStart() {
        super.onStart()
        if (usesStartStopLifecycle()) activatePlayback()
    }

    override fun onResume() {
        super.onResume()
        KioskController.applyImmersive(this)
        if (!usesStartStopLifecycle()) activatePlayback()
    }

    override fun onPause() {
        if (!usesStartStopLifecycle()) deactivatePlayback()
        super.onPause()
    }

    override fun onStop() {
        if (usesStartStopLifecycle()) deactivatePlayback()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureProgress()?.let { outState.putLong(STATE_POSITION_MS, it.positionMs) }
        outState.putString(STATE_EPISODE_ID, currentEpisode?.id ?: sessionEpisodeId)
        outState.putString(STATE_PLAYBACK_MODE, playbackMode.name)
        outState.putBoolean(STATE_PAUSED_BY_USER, pausedByUser)
        outState.putString(STATE_NEXT_EPISODE_ID, countdownNext?.id)
        outState.putInt(STATE_COUNTDOWN_SECONDS, countdownSeconds)
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) KioskController.applyImmersive(this)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        super.onDestroy()
    }

    private fun usesStartStopLifecycle(): Boolean = Build.VERSION.SDK_INT > Build.VERSION_CODES.M

    private fun playbackLifecycleActive(): Boolean = if (usesStartStopLifecycle()) {
        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    } else {
        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    private fun activatePlayback() {
        if (currentEpisode != null && player == null && countdownNext == null) initializePlayer()
        if (countdownNext != null) resumeCountdown()
    }

    private fun deactivatePlayback() {
        pauseCountdown()
        saveProgress(critical = true)
        releasePlayer()
    }

    private fun loadSession() {
        binding.loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = container.libraryRepository.loadCatalog()
            if (sessionEpisodeId.isBlank() || result !is LibraryResult.Success) {
                showPermanentError()
                return@launch
            }
            val episode = result.value.findEpisode(sessionEpisodeId)
            if (episode == null) {
                showPermanentError()
                return@launch
            }
            catalog = result.value
            currentEpisode = episode
            if (!playbackModeProvided) {
                playbackMode = container.settingsRepository.playbackMode.first()
            }
            preferredAudioLanguage = container.settingsRepository.preferredAudioLanguage.first()
            if (savedPositionMs <= 0L) {
                savedPositionMs = container.progressRepository.get(episode.id)?.positionMs ?: 0L
            }
            binding.episodeTitle.text = episode.title

            val restoredNext = restoredNextEpisodeId?.let(result.value::findEpisode)
            if (restoredNext != null && restoredCountdownSeconds > 0) {
                startCountdown(restoredNext, restoredCountdownSeconds)
                restoredNextEpisodeId = null
            } else if (playbackLifecycleActive()) {
                initializePlayer()
            }
        }
    }

    private fun initializePlayer() {
        val episode = currentEpisode ?: return
        if (player != null || countdownNext != null || !playbackLifecycleActive()) return
        playbackFailed = false
        binding.errorPanel.visibility = View.GONE
        binding.playbackControls.visibility = View.VISIBLE
        binding.seekBar.isEnabled = false
        binding.loading.visibility = View.VISIBLE

        val trackSelector = DefaultTrackSelector(this).apply {
            val parameters = buildUponParameters()
            if (preferredAudioLanguage.isNotBlank()) parameters.setPreferredAudioLanguage(preferredAudioLanguage)
            setParameters(parameters.build())
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
            .also { exoPlayer ->
                binding.playerView.player = exoPlayer
                exoPlayer.setAudioAttributes(audioAttributes, true)
                exoPlayer.setHandleAudioBecomingNoisy(true)
                exoPlayer.addListener(playerListener)
                exoPlayer.setMediaItem(mediaItem(episode))
                exoPlayer.prepare()
                if (savedPositionMs > 0L) exoPlayer.seekTo(savedPositionMs)
                exoPlayer.playWhenReady = !pausedByUser
            }
        handler.removeCallbacks(periodicProgressSave)
        handler.postDelayed(periodicProgressSave, PROGRESS_SAVE_INTERVAL_MS)
        handler.removeCallbacks(seekControlsUpdater)
        handler.post(seekControlsUpdater)
    }

    private fun mediaItem(episode: EpisodeItem): MediaItem {
        val builder = MediaItem.Builder().setUri(episode.mediaUri).setMediaId(episode.id)
        episode.subtitleUri?.let { subtitleUri ->
            val mimeType = if (subtitleUri.lowercase().endsWith(".vtt")) {
                MimeTypes.TEXT_VTT
            } else {
                MimeTypes.APPLICATION_SUBRIP
            }
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUri))
                        .setMimeType(mimeType)
                        .setLanguage(preferredAudioLanguage.takeIf(String::isNotBlank))
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build(),
                ),
            )
        }
        return builder.build()
    }

    private fun releasePlayer() {
        isUserSeeking = false
        binding.playbackControls.visibility = View.GONE
        handler.removeCallbacks(periodicProgressSave)
        handler.removeCallbacks(seekControlsUpdater)
        handler.removeCallbacks(bufferTimeout)
        player?.removeListener(playerListener)
        binding.playerView.player = null
        player?.release()
        player = null
    }

    private fun retryPlayback() {
        saveProgress(critical = true)
        playbackFailed = false
        releasePlayer()
        initializePlayer()
    }

    private fun togglePlayback() {
        if (playbackFailed || binding.nextPanel.visibility == View.VISIBLE) return
        val activePlayer = player ?: return
        if (activePlayer.isPlaying) {
            pausedByUser = true
            activePlayer.pause()
            showIndicator(R.drawable.ic_play)
        } else {
            pausedByUser = false
            activePlayer.play()
            showIndicator(R.drawable.ic_pause)
        }
    }

    private fun showIndicator(drawableRes: Int) {
        binding.playPauseIndicator.setImageResource(drawableRes)
        binding.playPauseIndicator.visibility = View.VISIBLE
        handler.removeCallbacks(hidePlayPauseIndicator)
        handler.postDelayed(hidePlayPauseIndicator, 650L)
    }

    private val hidePlayPauseIndicator = Runnable {
        binding.playPauseIndicator.visibility = View.GONE
    }

    private fun changeVolume(direction: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val activeSegments = ((current.toFloat() / maximum) * VOLUME_SEGMENTS).toInt().coerceIn(0, VOLUME_SEGMENTS)
        binding.volumeIndicator.text = buildString {
            append(getString(R.string.volume))
            append("  ")
            repeat(VOLUME_SEGMENTS) { index -> append(if (index < activeSegments) "▮" else "▯") }
        }
        binding.volumeIndicator.visibility = View.VISIBLE
        handler.removeCallbacks(hideVolumeIndicator)
        handler.postDelayed(hideVolumeIndicator, 1_000L)
    }

    private val hideVolumeIndicator = Runnable {
        binding.volumeIndicator.visibility = View.GONE
    }

    private fun seekBy(deltaMs: Long) {
        val activePlayer = player ?: return
        val duration = validDuration(activePlayer.duration)
        if (duration <= 0L) return
        val target = (activePlayer.currentPosition + deltaMs).coerceIn(0L, duration)
        activePlayer.seekTo(target)
        savedPositionMs = target
        updateSeekControls(positionOverrideMs = target)
    }

    private fun updateSeekControls(positionOverrideMs: Long? = null) {
        val activePlayer = player
        val duration = validDuration(activePlayer?.duration ?: 0L)
        val position = (positionOverrideMs ?: activePlayer?.currentPosition ?: savedPositionMs)
            .coerceAtLeast(0L)
            .coerceAtMost(duration.takeIf { it > 0L } ?: Long.MAX_VALUE)
        binding.currentTime.text = formatTime(position)
        binding.totalTime.text = if (duration > 0L) {
            formatTime(duration)
        } else {
            getString(R.string.time_unknown)
        }
        binding.seekBar.isEnabled = duration > 0L
        if (!isUserSeeking) {
            binding.seekBar.progress = if (duration > 0L) {
                ((position.toDouble() / duration) * SEEK_BAR_MAX).toInt().coerceIn(0, SEEK_BAR_MAX)
            } else {
                0
            }
        }
    }

    private fun positionForProgress(progress: Int): Long {
        val duration = validDuration(player?.duration ?: 0L)
        if (duration <= 0L) return 0L
        return ((progress.coerceIn(0, SEEK_BAR_MAX).toDouble() / SEEK_BAR_MAX) * duration).toLong()
    }

    private fun formatTime(positionMs: Long): String {
        val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }

    private data class ProgressCheckpoint(
        val episodeId: String,
        val positionMs: Long,
        val durationMs: Long,
        val capturedAtEpochMs: Long,
    )

    private fun captureProgress(): ProgressCheckpoint? {
        val episode = currentEpisode ?: return null
        val activePlayer = player ?: return null
        val duration = validDuration(activePlayer.duration)
        if (duration <= 0L) return null
        savedPositionMs = activePlayer.currentPosition.coerceAtLeast(0L)
        return ProgressCheckpoint(episode.id, savedPositionMs, duration, System.currentTimeMillis())
    }

    private fun saveProgress(critical: Boolean) {
        val checkpoint = captureProgress() ?: return
        if (critical) {
            // The critical API commits the atomic backup synchronously before finish or force-kill.
            container.progressRepository.checkpointCritical(
                checkpoint.episodeId,
                checkpoint.positionMs,
                checkpoint.durationMs,
                checkpoint.capturedAtEpochMs,
            )
        } else {
            container.applicationScope.launch {
                container.progressRepository.save(
                    checkpoint.episodeId,
                    checkpoint.positionMs,
                    checkpoint.durationMs,
                    checkpoint.capturedAtEpochMs,
                )
            }
        }
    }

    private fun handleEpisodeEnded() {
        val episode = currentEpisode ?: return
        val duration = validDuration(player?.duration ?: 0L)
        val catalogSnapshot = catalog
        container.applicationScope.launch {
            container.progressRepository.markCompleted(episode.id, duration)
            val progress = container.progressRepository.snapshot()
            val next = catalogSnapshot?.let { catalogValue ->
                PlaybackQueuePlanner.nextEpisode(
                    catalog = catalogValue,
                    currentEpisodeId = episode.id,
                    mode = playbackMode,
                    progress = progress,
                )
            }
            withContext(Dispatchers.Main.immediate) {
                if (isFinishing || isDestroyed) return@withContext
                if (next == null) closePlayer() else startCountdown(next)
            }
        }
    }

    private fun startCountdown(next: EpisodeItem, seconds: Int = NEXT_COUNTDOWN_SECONDS) {
        releasePlayer()
        countdownNext = next
        countdownSeconds = seconds.coerceAtLeast(1)
        binding.nextTitle.text = next.title
        binding.nextPanel.visibility = View.VISIBLE
        updateCountdown()
    }

    private fun resumeCountdown() {
        if (countdownNext == null || countdownSeconds <= 0) return
        binding.nextPanel.visibility = View.VISIBLE
        handler.removeCallbacks(countdownTick)
        handler.postDelayed(countdownTick, 1_000L)
    }

    private fun pauseCountdown() {
        handler.removeCallbacks(countdownTick)
    }

    private fun updateCountdown() {
        binding.nextCountdown.text = resources.getQuantityString(
            R.plurals.next_episode_countdown,
            countdownSeconds,
            countdownSeconds,
        )
        if (countdownSeconds <= 0) {
            val next = countdownNext
            clearCountdown()
            if (next != null) switchEpisode(next)
            return
        }
        countdownSeconds -= 1
        handler.postDelayed(countdownTick, 1_000L)
    }

    private val countdownTick = Runnable(::updateCountdown)

    private fun clearCountdown() {
        handler.removeCallbacks(countdownTick)
        countdownNext = null
        countdownSeconds = 0
        binding.nextPanel.visibility = View.GONE
    }

    private fun switchEpisode(next: EpisodeItem) {
        clearCountdown()
        releasePlayer()
        currentEpisode = next
        sessionEpisodeId = next.id
        intent.putExtra(EXTRA_EPISODE_ID, next.id)
        savedPositionMs = 0L
        pausedByUser = false
        playbackFailed = false
        binding.episodeTitle.text = next.title
        lifecycleScope.launch {
            savedPositionMs = container.progressRepository.get(next.id)?.positionMs ?: 0L
            if (playbackLifecycleActive()) initializePlayer()
        }
    }

    private fun validDuration(duration: Long): Long =
        if (duration == C.TIME_UNSET || duration < 0L) 0L else duration

    private fun showPermanentError() {
        binding.loading.visibility = View.GONE
        binding.playbackControls.visibility = View.GONE
        binding.errorPanel.visibility = View.VISIBLE
        binding.retryButton.visibility = View.GONE
    }

    private fun closePlayer() {
        saveProgress(critical = true)
        finish()
    }

    companion object {
        const val EXTRA_EPISODE_ID = "episode_id"
        const val EXTRA_PLAYBACK_MODE = "playback_mode"
        private const val PROGRESS_SAVE_INTERVAL_MS = 10_000L
        private const val BUFFER_TIMEOUT_MS = 20_000L
        private const val NEXT_COUNTDOWN_SECONDS = 5
        private const val VOLUME_SEGMENTS = 7
        private const val SEEK_STEP_MS = 10_000L
        private const val SEEK_UPDATE_INTERVAL_MS = 250L
        private const val SEEK_BAR_MAX = 1_000
        private const val STATE_EPISODE_ID = "state_episode_id"
        private const val STATE_PLAYBACK_MODE = "state_playback_mode"
        private const val STATE_PAUSED_BY_USER = "state_paused_by_user"
        private const val STATE_POSITION_MS = "state_position_ms"
        private const val STATE_NEXT_EPISODE_ID = "state_next_episode_id"
        private const val STATE_COUNTDOWN_SECONDS = "state_countdown_seconds"

        fun intent(context: Context, episodeId: String, mode: PlaybackMode): Intent =
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_EPISODE_ID, episodeId)
                putExtra(EXTRA_PLAYBACK_MODE, mode.name)
            }
    }
}
