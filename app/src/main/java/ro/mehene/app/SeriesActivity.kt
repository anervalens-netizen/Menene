package ro.mehene.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import ro.mehene.app.data.LibraryRepository
import ro.mehene.app.data.LibraryResult
import ro.mehene.app.data.PlaybackProgressStore
import ro.mehene.app.databinding.ActivitySeriesBinding
import ro.mehene.app.kiosk.KioskController
import ro.mehene.app.model.EpisodeItem
import ro.mehene.app.ui.EpisodeAdapter
import ro.mehene.app.util.meheneGridColumns
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class SeriesActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySeriesBinding
    private lateinit var repository: LibraryRepository
    private lateinit var adapter: EpisodeAdapter
    private val scanExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var scanTask: Future<*>? = null

    private val seriesTitle: String by lazy {
        intent.getStringExtra(EXTRA_SERIES_TITLE).orEmpty()
    }
    private val seriesUri: String by lazy {
        intent.getStringExtra(EXTRA_SERIES_URI).orEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySeriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = LibraryRepository(this)
        adapter = EpisodeAdapter(PlaybackProgressStore(this), ::openEpisode)
        binding.episodeList.layoutManager = GridLayoutManager(this, meheneGridColumns())
        binding.episodeList.adapter = adapter
        binding.episodeList.setHasFixedSize(true)
        binding.seriesTitle.text = seriesTitle
        binding.backButton.setOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        loadEpisodes()
        KioskController.applyImmersive(this)
    }

    override fun onResume() {
        super.onResume()
        KioskController.applyImmersive(this)
        adapter.refreshPlaybackState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) KioskController.applyImmersive(this)
    }

    override fun onDestroy() {
        scanTask?.cancel(true)
        scanExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun loadEpisodes() {
        binding.loading.visibility = View.VISIBLE
        binding.emptyMessage.visibility = View.GONE
        binding.episodeList.visibility = View.INVISIBLE
        scanTask?.cancel(true)

        scanTask = scanExecutor.submit {
            val result = repository.scanEpisodes(seriesUri)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.loading.visibility = View.GONE
                showEpisodeResult(result)
            }
        }
    }

    private fun showEpisodeResult(result: LibraryResult<List<EpisodeItem>>) {
        when (result) {
            is LibraryResult.Success -> {
                val episodes = result.value
                adapter.submitItems(episodes)
                binding.seriesSubtitle.text = resources.getQuantityString(
                    R.plurals.episodes_count,
                    episodes.size,
                    episodes.size,
                )
                binding.episodeList.visibility = if (episodes.isEmpty()) View.INVISIBLE else View.VISIBLE
                binding.emptyMessage.visibility = if (episodes.isEmpty()) View.VISIBLE else View.GONE
                binding.emptyMessage.setText(R.string.no_episodes)
            }

            LibraryResult.PermissionLost -> showError(R.string.library_permission_lost)
            LibraryResult.StorageUnavailable -> showError(R.string.library_storage_unavailable)
            LibraryResult.NotConfigured -> showError(R.string.library_not_configured)
            is LibraryResult.Failure -> showError(R.string.scan_error)
        }
    }

    private fun showError(messageRes: Int) {
        adapter.submitItems(emptyList())
        binding.seriesSubtitle.text = ""
        binding.episodeList.visibility = View.INVISIBLE
        binding.emptyMessage.visibility = View.VISIBLE
        binding.emptyMessage.setText(messageRes)
    }

    private fun openEpisode(episode: EpisodeItem) {
        startActivity(
            Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_EPISODE_TITLE, episode.title)
                putExtra(PlayerActivity.EXTRA_MEDIA_URI, episode.mediaUri)
                putExtra(PlayerActivity.EXTRA_PLAYBACK_KEY, episode.playbackKey)
            },
        )
    }

    companion object {
        const val EXTRA_SERIES_TITLE = "series_title"
        const val EXTRA_SERIES_URI = "series_uri"
    }
}
