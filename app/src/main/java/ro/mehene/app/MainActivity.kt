package ro.mehene.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.launch
import ro.mehene.app.data.LibraryPreferences
import ro.mehene.app.data.PlaybackMode
import ro.mehene.app.databinding.ActivityMainBinding
import ro.mehene.app.kiosk.KioskController
import ro.mehene.app.model.EpisodeItem
import ro.mehene.app.model.SeriesItem
import ro.mehene.app.ui.SeriesAdapter
import ro.mehene.app.ui.state.LibraryUnavailableReason
import ro.mehene.app.ui.state.MainUiState
import ro.mehene.app.util.meheneGridColumns

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: LibraryPreferences
    private val viewModel: MainViewModel by viewModels()
    private val adapter = SeriesAdapter(::openSeries)

    private val adminTapDetector = AdminTapDetector(clock = SystemClock::elapsedRealtime)
    private var touchTapHandled = false
    private var folderPickerActive = false
    private var skipNextResumeRefresh = false

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        folderPickerActive = false
        uri?.let {
            skipNextResumeRefresh = true
            viewModel.setLibrary(it)
        }
        resumeKioskAfterExternalActivity()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        folderPickerActive = savedInstanceState?.getBoolean(STATE_FOLDER_PICKER_ACTIVE) ?: false
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        preferences = LibraryPreferences(this)

        binding.seriesList.layoutManager = GridLayoutManager(this, meheneGridColumns())
        binding.seriesList.adapter = adapter
        binding.seriesList.setHasFixedSize(true)
        binding.setupButton.setOnClickListener { chooseLibraryFolder() }
        binding.logo.setOnClickListener {
            if (!touchTapHandled) openAdministrationIfReady()
            touchTapHandled = false
        }
        binding.logo.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    touchTapHandled = true
                    openAdministrationIfReady()
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    view.performClick()
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    touchTapHandled = false
                    true
                }
                else -> true
            }
        }
        binding.tvCard.setOnClickListener {
            (binding.tvCard.tag as? EpisodeItem)?.let { openEpisode(it, PlaybackMode.MEHENE_TV) }
        }
        binding.continueCard.setOnClickListener {
            (binding.continueCard.tag as? EpisodeItem)?.let { openEpisode(it, PlaybackMode.SINGLE) }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }

        KioskController.prepareAndEnter(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_FOLDER_PICKER_ACTIVE, folderPickerActive)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (!folderPickerActive) {
            if (preferences.resumeKioskAfterExternalActivity) resumeKioskAfterExternalActivity()
            else KioskController.prepareAndEnter(this)
            if (skipNextResumeRefresh) skipNextResumeRefresh = false
            else viewModel.refresh()
        } else {
            KioskController.applyImmersive(this)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !folderPickerActive) KioskController.applyImmersive(this)
    }

    private fun render(state: MainUiState) = with(binding) {
        loading.visibility = if (state is MainUiState.Loading) View.VISIBLE else View.GONE
        seriesList.visibility = View.GONE
        emptyPanel.visibility = View.GONE
        premiumActions.visibility = View.GONE

        when (state) {
            MainUiState.Loading -> Unit
            MainUiState.NotConfigured -> showMessage(
                R.string.library_not_configured,
                R.string.empty_message,
                allowSetup = true,
            )
            is MainUiState.Unavailable -> showMessage(
                R.string.library_unavailable_child,
                when (state.reason) {
                    LibraryUnavailableReason.PERMISSION_LOST -> R.string.library_child_call_adult
                    LibraryUnavailableReason.STORAGE_UNAVAILABLE -> R.string.library_child_call_adult
                    LibraryUnavailableReason.FAILURE -> R.string.library_child_call_adult
                },
                allowSetup = false,
            )
            is MainUiState.Content -> {
                adapter.submitList(state.catalog.series)
                if (state.catalog.series.isEmpty()) {
                    showMessage(R.string.empty_title, R.string.no_series, allowSetup = false)
                } else {
                    seriesList.visibility = View.VISIBLE
                    bindPremiumActions(state.continueEpisode, state.tvEpisode)
                }
            }
        }
    }

    private fun bindPremiumActions(continueEpisode: EpisodeItem?, tvEpisode: EpisodeItem?) = with(binding) {
        tvCard.tag = tvEpisode
        tvCard.visibility = if (tvEpisode == null) View.GONE else View.VISIBLE
        tvSubtitle.text = tvEpisode?.let { getString(R.string.tv_starts_with, it.title) }.orEmpty()

        continueCard.tag = continueEpisode
        continueCard.visibility = if (continueEpisode == null) View.GONE else View.VISIBLE
        continueTitle.text = continueEpisode?.title.orEmpty()
        premiumActions.visibility = if (tvEpisode != null || continueEpisode != null) View.VISIBLE else View.GONE
    }

    private fun showMessage(titleRes: Int, messageRes: Int, allowSetup: Boolean) = with(binding) {
        adapter.submitList(emptyList())
        emptyPanel.visibility = View.VISIBLE
        emptyTitle.setText(titleRes)
        emptyMessage.setText(messageRes)
        setupButton.visibility = if (allowSetup) View.VISIBLE else View.GONE
    }

    private fun openSeries(series: SeriesItem) {
        startActivity(
            Intent(this, SeriesActivity::class.java).apply {
                putExtra(SeriesActivity.EXTRA_SERIES_ID, series.id)
            },
        )
    }

    @OptIn(markerClass = [UnstableApi::class])
    private fun openEpisode(episode: EpisodeItem, mode: PlaybackMode) {
        startActivity(PlayerActivity.intent(this, episode.id, mode))
    }

    private fun chooseLibraryFolder() {
        preferences.resumeKioskAfterExternalActivity =
            preferences.kioskEnabled && KioskController.isLocked(this)
        KioskController.prepareForExternalActivity(this)
        folderPickerActive = true
        runCatching { folderPicker.launch(null) }
            .onFailure {
                folderPickerActive = false
                resumeKioskAfterExternalActivity()
            }
    }

    private fun resumeKioskAfterExternalActivity() {
        val shouldResume = preferences.resumeKioskAfterExternalActivity
        preferences.resumeKioskAfterExternalActivity = false
        if (shouldResume && preferences.kioskEnabled) KioskController.prepareAndEnter(this)
        else KioskController.applyImmersive(this)
    }

    private fun openAdministrationIfReady() {
        val opened = adminTapDetector.onTap()
        if (opened) {
            startActivity(Intent(this, AdminActivity::class.java))
        }
    }

    companion object {
        private const val STATE_FOLDER_PICKER_ACTIVE = "folder_picker_active"
    }
}
