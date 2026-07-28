package ro.mehene.app

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import ro.mehene.app.data.PlaybackProgressStore
import ro.mehene.app.databinding.ActivityPlayerBinding
import ro.mehene.app.kiosk.KioskController

@UnstableApi
class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var progressStore: PlaybackProgressStore
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var pausedByUser = false
    private var pausedByLifecycle = false
    private var playbackFailed = false

    private val mediaUri: String by lazy {
        intent.getStringExtra(EXTRA_MEDIA_URI).orEmpty()
    }
    private val playbackKey: String by lazy {
        intent.getStringExtra(EXTRA_PLAYBACK_KEY).orEmpty().ifBlank { mediaUri }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> binding.loading.visibility = View.VISIBLE
                Player.STATE_READY -> {
                    binding.loading.visibility = View.GONE
                    binding.errorPanel.visibility = View.GONE
                }
                Player.STATE_ENDED -> {
                    binding.loading.visibility = View.GONE
                    player?.let { progressStore.markCompleted(playbackKey, validDuration(it.duration)) }
                    handler.postDelayed({ if (!isFinishing) finish() }, 700L)
                }
                Player.STATE_IDLE -> binding.loading.visibility = View.GONE
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            playbackFailed = true
            binding.loading.visibility = View.GONE
            binding.errorPanel.visibility = View.VISIBLE
        }
    }

    private val hideIndicator = Runnable {
        binding.playPauseIndicator.visibility = View.GONE
    }

    private val hideVolumeIndicator = Runnable {
        binding.volumeIndicator.visibility = View.GONE
    }

    private val periodicProgressSave = object : Runnable {
        override fun run() {
            saveProgress()
            handler.postDelayed(this, PROGRESS_SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        progressStore = PlaybackProgressStore(this)
        setVolumeControlStream(AudioManager.STREAM_MUSIC)

        binding.playerView.useController = false
        binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        binding.closeButton.setOnClickListener { closePlayer() }
        binding.volumeUpButton.setOnClickListener { changeVolume(AudioManager.ADJUST_RAISE) }
        binding.volumeDownButton.setOnClickListener { changeVolume(AudioManager.ADJUST_LOWER) }
        binding.playerView.setOnClickListener { togglePlayback() }
        binding.retryButton.setOnClickListener { retryPlayback() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        initializePlayer()
        handler.postDelayed(periodicProgressSave, PROGRESS_SAVE_INTERVAL_MS)
        KioskController.applyImmersive(this)
    }

    override fun onStart() {
        super.onStart()
        if (pausedByLifecycle && !pausedByUser && !playbackFailed) {
            player?.play()
        }
        pausedByLifecycle = false
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
        val activePlayer = player
        pausedByLifecycle = activePlayer?.isPlaying == true && !pausedByUser && !playbackFailed
        saveProgress()
        activePlayer?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        saveProgress()
        player?.removeListener(playerListener)
        binding.playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun initializePlayer() {
        if (mediaUri.isBlank()) {
            showPermanentError()
            return
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            binding.playerView.player = exoPlayer
            exoPlayer.setAudioAttributes(audioAttributes, true)
            exoPlayer.setHandleAudioBecomingNoisy(true)
            exoPlayer.addListener(playerListener)
            prepareMedia(exoPlayer)
        }
    }

    private fun prepareMedia(exoPlayer: ExoPlayer) {
        playbackFailed = false
        binding.errorPanel.visibility = View.GONE
        binding.loading.visibility = View.VISIBLE
        exoPlayer.setMediaItem(MediaItem.fromUri(mediaUri))
        exoPlayer.prepare()
        val savedPosition = progressStore.savedPosition(playbackKey)
        if (savedPosition > 0L) exoPlayer.seekTo(savedPosition)
        exoPlayer.playWhenReady = !pausedByUser
    }

    private fun retryPlayback() {
        val activePlayer = player ?: run {
            initializePlayer()
            return
        }
        activePlayer.stop()
        prepareMedia(activePlayer)
    }

    private fun togglePlayback() {
        if (playbackFailed) return
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
        handler.removeCallbacks(hideIndicator)
        handler.postDelayed(hideIndicator, 650L)
    }

    private fun changeVolume(direction: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        binding.volumeIndicator.text = getString(R.string.volume_level, current, maximum)
        binding.volumeIndicator.visibility = View.VISIBLE
        handler.removeCallbacks(hideVolumeIndicator)
        handler.postDelayed(hideVolumeIndicator, 1_000L)
    }

    private fun saveProgress() {
        val activePlayer = player ?: return
        if (playbackKey.isBlank()) return
        val duration = validDuration(activePlayer.duration)
        if (duration <= 0L) return
        progressStore.save(playbackKey, activePlayer.currentPosition, duration)
    }

    private fun validDuration(duration: Long): Long =
        if (duration == C.TIME_UNSET || duration < 0L) 0L else duration

    private fun showPermanentError() {
        playbackFailed = true
        binding.loading.visibility = View.GONE
        binding.errorPanel.visibility = View.VISIBLE
        binding.retryButton.visibility = View.GONE
    }

    private fun closePlayer() {
        saveProgress()
        player?.pause()
        finish()
    }

    companion object {
        const val EXTRA_EPISODE_TITLE = "episode_title"
        const val EXTRA_MEDIA_URI = "media_uri"
        const val EXTRA_PLAYBACK_KEY = "playback_key"
        private const val PROGRESS_SAVE_INTERVAL_MS = 10_000L
    }
}
