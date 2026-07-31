package ro.menene.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.launch
import ro.menene.app.data.PlaybackMode
import ro.menene.app.databinding.ActivitySeriesBinding
import ro.menene.app.databinding.ItemSeasonTabBinding
import ro.menene.app.kiosk.KioskController
import ro.menene.app.model.EpisodeItem
import ro.menene.app.model.SeasonItem
import ro.menene.app.ui.EpisodeAdapter
import ro.menene.app.ui.state.SeriesUiState
import ro.menene.app.util.meneneGridColumns

class SeriesActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySeriesBinding
    private val viewModel: SeriesViewModel by viewModels()
    private val adapter = EpisodeAdapter(::openEpisode)
    private var playbackMode = PlaybackMode.SINGLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySeriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.episodeList.layoutManager = GridLayoutManager(this, meneneGridColumns())
        binding.episodeList.adapter = adapter
        binding.episodeList.setHasFixedSize(true)
        binding.backButton.setOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        val seriesId = intent.getStringExtra(EXTRA_SERIES_ID).orEmpty()
        viewModel.load(seriesId)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
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

    private fun render(state: SeriesUiState) = with(binding) {
        loading.visibility = if (state is SeriesUiState.Loading) View.VISIBLE else View.GONE
        when (state) {
            SeriesUiState.Loading -> Unit
            SeriesUiState.Unavailable -> {
                episodeList.visibility = View.GONE
                emptyMessage.visibility = View.VISIBLE
                emptyMessage.setText(R.string.library_child_call_adult)
            }
            is SeriesUiState.Content -> {
                playbackMode = state.playbackMode
                seriesTitle.text = state.series.title
                seriesSubtitle.text = resources.getQuantityString(
                    R.plurals.episodes_count,
                    state.selectedSeason.episodes.size,
                    state.selectedSeason.episodes.size,
                )
                bindSeasons(state.seasons, state.selectedSeason)
                adapter.submitList(state.selectedSeason.episodes)
                adapter.updateProgress(state.progress)
                episodeList.visibility = if (state.selectedSeason.episodes.isEmpty()) View.GONE else View.VISIBLE
                emptyMessage.visibility = if (state.selectedSeason.episodes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun bindSeasons(seasons: List<SeasonItem>, selected: SeasonItem) {
        binding.seasonTabs.removeAllViews()
        binding.seasonScroll.visibility = if (seasons.size > 1) View.VISIBLE else View.GONE
        if (seasons.size <= 1) return
        seasons.forEach { season ->
            val tab = ItemSeasonTabBinding.inflate(LayoutInflater.from(this), binding.seasonTabs, false)
            tab.root.text = season.title
            tab.root.isSelected = season.number == selected.number
            tab.root.setOnClickListener { viewModel.selectSeason(season.number) }
            binding.seasonTabs.addView(tab.root)
        }
    }

    @OptIn(markerClass = [UnstableApi::class])
    private fun openEpisode(episode: EpisodeItem) {
        startActivity(PlayerActivity.intent(this, episode.id, playbackMode))
    }

    companion object {
        const val EXTRA_SERIES_ID = "series_id"
    }
}
