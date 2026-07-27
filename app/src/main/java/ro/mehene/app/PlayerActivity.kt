package ro.mehene.app

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var player: ExoPlayer
    private lateinit var progressStore: PlaybackProgressStore
    private val handler = Handler(Looper.getMainLooper())

    private val mediaUri: String by lazy {
        intent.getStringExtra(EXTRA_MEDIA_URI).orEmpty()
    }
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                progressStore.markCompleted(mediaUri, player.duration)
                handler.postDelayed({ if (!isFinishing) finish() }, 700L)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(this@PlayerActivity, R.string.video_error, Toast.LENGTH_LONG).show()
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        initializePlayer()
        KioskController.applyImmersive(this)
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
        saveProgress()
        player.pause()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        saveProgress()
        player.removeListener(playerListener)
        binding.playerView.player = null
        player.release()
        super.onDestroy()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            binding.playerView.player = exoPlayer
            exoPlayer.addListener(playerListener)
            exoPlayer.setMediaItem(MediaItem.fromUri(mediaUri))
            exoPlayer.prepare()
            val savedPosition = progressStore.savedPosition(mediaUri)
            if (savedPosition > 0L) exoPlayer.seekTo(savedPosition)
            exoPlayer.playWhenReady = true
        }
    }

    private fun togglePlayback() {
        if (player.isPlaying) {
            player.pause()
            showIndicator(R.drawable.ic_play)
        } else {
            player.play()
            showIndicator(R.drawable.ic_pause)
        }
    }

    private fun showIndicator(drawableRes: Int) {
        binding.playPauseIndicator.setImageResource(drawableRes)
        binding.playPauseIndicator.visibility = View.VISIBLE
        handler.removeCallbacks(hideIndicator)
        handler.postDelayed(hideIndicator, 650L)
    }

    private val hideIndicator = Runnable {
        binding.playPauseIndicator.visibility = View.GONE
    }

    private fun changeVolume(direction: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
    }

    private fun saveProgress() {
        if (!::player.isInitialized || mediaUri.isBlank()) return
        progressStore.save(mediaUri, player.currentPosition, player.duration)
    }

    private fun closePlayer() {
        saveProgress()
        player.pause()
        finish()
    }

    companion object {
        const val EXTRA_EPISODE_TITLE = "episode_title"
        const val EXTRA_MEDIA_URI = "media_uri"
    }
}
