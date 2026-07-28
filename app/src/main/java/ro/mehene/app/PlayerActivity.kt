package ro.mehene.app

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ro.mehene.app.data.LibraryResult
import ro.mehene.app.data.PlaybackMode
import ro.mehene.app.databinding.ActivityPlayerBinding
import ro.mehene.app.kiosk.KioskController
import ro.mehene.app.media.PlaybackQueuePlanner
import ro.mehene.app.model.EpisodeItem
import ro.mehene.app.model.LibraryCatalog

@UnstableApi
class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private val handler = Handler(Looper.getMainLooper())
    private val container by lazy { meheneContainer() }

    private var player: ExoPlayer? = null
    private var catalog: LibraryCatalog? = null
    private var currentEpisode: EpisodeItem? = null
    private var playbackMode = PlaybackMode.SINGLE
    private var preferredAudioLanguage = "ron"
    private var savedPositionMs = 0L
    private var pausedByUser = false
    private var playbackFailed = false
    private var countdownNext: EpisodeItem? = null
    private var countdownSeconds = 0

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
                    binding.loading.visibility = View.GONE
                    binding.errorPanel.visibility = View.GONE
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
            playbackFailed = true
            handler.removeCallbacks(bufferTimeout)
            binding.loading.visibility = View.GONE
            binding.errorPanel.visibility = View.VISIBLE
        }
    }

    private val bufferTimeout = Runnable {
        if (player?.playbackState == Player.STATE_BUFFERING) {
            playbackFailed = true
            player?.pause()
            binding.loading.visibility = View.GONE
            binding.errorPanel.visibility = View.VISIBLE
        }
    }

    private val periodicProgressSave = object : Runnable {
        override fun run() {
            saveProgress()
            if (player != null) handler.postDelayed(this, PROGRESS_SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setVolumeControlStream(AudioManager.STREAM_MUSIC)

        binding.playerView.useController = false
        binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        binding.closeButton.setOnClickListener { closePlayer() }
        binding.volumeUpButton.setOnClickListener { changeVolume(AudioManager.ADJUST_RAISE) }
        binding.volumeDownButton.setOnClickListener { changeVolume(AudioManager.ADJUST_LOWER) }
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
        if (currentEpisode != null && player == null) initializePlayer()
    }

    override fun onResume() {
        super.onResume()
        KioskController.applyImmersive(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) KioskController.applyImmersive(this)
    }

    override fun onStop() {
        cancelCountdown()
        saveProgress()
        releasePlayer()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        super.onDestroy()
    }

    private fun loadSession() {
        binding.loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val episodeId = intent.getStringExtra(EXTRA_EPISODE_ID).orEmpty()
            val result = container.libraryRepository.loadCatalog()
            if (episodeId.isBlank() || result !is LibraryResult.Success) {
                showPermanentError()
                return@launch
            }
            val episode = result.value.findEpisode(episodeId)
            if (episode == null) {
                showPermanentError()
                return@launch
            }
            catalog = result.value
            currentEpisode = episode
            playbackMode = intent.getStringExtra(EXTRA_PLAYBACK_MODE)
                ?.let { runCatching { PlaybackMode.valueOf(it) }.getOrNull() }
                ?: container.settingsRepository.playbackMode.first()
            preferredAudioLanguage = container.settingsRepository.preferredAudioLanguage.first()
            savedPositionMs = container.progressRepository.get(episode.id)?.positionMs ?: 0L
            binding.episodeTitle.text = episode.title
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) initializePlayer()
        }
    }

    private fun initializePlayer() {
        val episode = currentEpisode ?: return
        if (player != null) return
        playbackFailed = false
        binding.errorPanel.visibility = View.GONE
        binding.loading.visibility = View.VISIBLE

        val trackSelector = DefaultTrackSelector(this).apply {
            val parameters = buildUponParameters()
            if (preferredAudioLanguage.isNotBlank()) {
                parameters.setPreferredAudioLanguage(preferredAudioLanguage)
            }
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
    }

    private fun mediaItem(episode: EpisodeItem): MediaItem {
        val builder = MediaItem.Builder().setUri(episode.mediaUri).setMediaId(episode.id)
        episode.subtitleUri?.let { subtitleUri ->
            val mimeType = when {
                subtitleUri.lowercase().endsWith(".vtt") -> MimeTypes.TEXT_VTT
                else -> MimeTypes.APPLICATION_SUBRIP
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
        handler.removeCallbacks(periodicProgressSave)
        handler.removeCallbacks(bufferTimeout)
        player?.removeListener(playerListener)
        binding.playerView.player = null
        player?.release()
        player = null
    }

    private fun retryPlayback() {
        saveProgress()
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

    private fun saveProgress() {
        val episode = currentEpisode ?: return
        val activePlayer = player ?: return
        val duration = validDuration(activePlayer.duration)
        if (duration <= 0L) return
        savedPositionMs = activePlayer.currentPosition.coerceAtLeast(0L)
        lifecycleScope.launch {
            container.progressRepository.save(episode.id, savedPositionMs, duration)
        }
    }

    private fun handleEpisodeEnded() {
        val episode = currentEpisode ?: return
        val duration = validDuration(player?.duration ?: 0L)
        lifecycleScope.launch {
            container.progressRepository.markCompleted(episode.id, duration)
            val catalogSnapshot = catalog ?: return@launch closePlayer()
            val progress = container.progressRepository.snapshot()
            val next = PlaybackQueuePlanner.nextEpisode(
                catalog = catalogSnapshot,
                currentEpisodeId = episode.id,
                mode = playbackMode,
                progress = progress,
            )
            if (next == null) closePlayer() else startCountdown(next)
        }
    }

    private fun startCountdown(next: EpisodeItem) {
        countdownNext = next
        countdownSeconds = NEXT_COUNTDOWN_SECONDS
        binding.nextTitle.text = next.title
        binding.nextPanel.visibility = View.VISIBLE
        updateCountdown()
    }

    private fun updateCountdown() {
        binding.nextCountdown.text = resources.getQuantityString(
            R.plurals.next_episode_countdown,
            countdownSeconds,
            countdownSeconds,
        )
        if (countdownSeconds <= 0) {
            val next = countdownNext
            cancelCountdown()
            if (next != null) switchEpisode(next)
            return
        }
        countdownSeconds -= 1
        handler.postDelayed(countdownTick, 1_000L)
    }

    private val countdownTick = Runnable(::updateCountdown)

    private fun cancelCountdown() {
        handler.removeCallbacks(countdownTick)
        countdownNext = null
        binding.nextPanel.visibility = View.GONE
    }

    private fun switchEpisode(next: EpisodeItem) {
        releasePlayer()
        currentEpisode = next
        savedPositionMs = 0L
        pausedByUser = false
        playbackFailed = false
        binding.episodeTitle.text = next.title
        lifecycleScope.launch {
            savedPositionMs = container.progressRepository.get(next.id)?.positionMs ?: 0L
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) initializePlayer()
        }
    }

    private fun validDuration(duration: Long): Long =
        if (duration == C.TIME_UNSET || duration < 0L) 0L else duration

    private fun showPermanentError() {
        binding.loading.visibility = View.GONE
        binding.errorPanel.visibility = View.VISIBLE
        binding.retryButton.visibility = View.GONE
    }

    private fun closePlayer() {
        saveProgress()
        finish()
    }

    companion object {
        const val EXTRA_EPISODE_ID = "episode_id"
        const val EXTRA_PLAYBACK_MODE = "playback_mode"
        private const val PROGRESS_SAVE_INTERVAL_MS = 10_000L
        private const val BUFFER_TIMEOUT_MS = 20_000L
        private const val NEXT_COUNTDOWN_SECONDS = 5
        private const val VOLUME_SEGMENTS = 7

        fun intent(context: Context, episodeId: String, mode: PlaybackMode): Intent =
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_EPISODE_ID, episodeId)
                putExtra(EXTRA_PLAYBACK_MODE, mode.name)
            }
    }
}
