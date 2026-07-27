package ro.mehene.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import ro.mehene.app.data.LibraryRepository
import ro.mehene.app.data.PlaybackProgressStore
import ro.mehene.app.databinding.ActivitySeriesBinding
import ro.mehene.app.kiosk.KioskController
import ro.mehene.app.model.EpisodeItem
import ro.mehene.app.ui.EpisodeAdapter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SeriesActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySeriesBinding
    private lateinit var repository: LibraryRepository
    private lateinit var adapter: EpisodeAdapter
    private val scanExecutor: ExecutorService = Executors.newSingleThreadExecutor()

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
        binding.episodeList.layoutManager = GridLayoutManager(this, 3)
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
        adapter.notifyDataSetChanged()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) KioskController.applyImmersive(this)
    }

    override fun onDestroy() {
        scanExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun loadEpisodes() {
        binding.loading.visibility = View.VISIBLE
        binding.emptyMessage.visibility = View.GONE
        binding.episodeList.visibility = View.INVISIBLE

        scanExecutor.execute {
            val episodes = runCatching { repository.scanEpisodes(seriesUri) }.getOrDefault(emptyList())
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.loading.visibility = View.GONE
                adapter.submitItems(episodes)
                binding.seriesSubtitle.text = if (episodes.size == 1) {
                    getString(R.string.one_episode)
                } else {
                    getString(R.string.episodes_count, episodes.size)
                }
                binding.episodeList.visibility = if (episodes.isEmpty()) View.INVISIBLE else View.VISIBLE
                binding.emptyMessage.visibility = if (episodes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openEpisode(episode: EpisodeItem) {
        startActivity(
            Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_EPISODE_TITLE, episode.title)
                putExtra(PlayerActivity.EXTRA_MEDIA_URI, episode.mediaUri)
            },
        )
    }

    companion object {
        const val EXTRA_SERIES_TITLE = "series_title"
        const val EXTRA_SERIES_URI = "series_uri"
    }
}
