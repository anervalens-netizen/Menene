package ro.menene.app

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import ro.menene.app.data.LibraryPreferences
import ro.menene.app.data.PlaybackMode
import ro.menene.app.databinding.ActivityAdminBinding
import ro.menene.app.kiosk.KioskController
import ro.menene.app.kiosk.KioskState
import ro.menene.app.ui.state.AdminUiState

class AdminActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminBinding
    private lateinit var preferences: LibraryPreferences
    private val viewModel: AdminViewModel by viewModels()
    private var playbackSpinnerBinding = true
    private var audioSpinnerBinding = true
    private var folderPickerActive = false
    private var skipNextResumeRefresh = false
    private var restoreAfterSettings = false

    private val playbackModes = listOf(
        PlaybackMode.SINGLE,
        PlaybackMode.CONTINUE_SERIES,
        PlaybackMode.MENENE_TV,
    )

    private val audioLanguages = listOf("ron", "eng", "")

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        folderPickerActive = false
        uri?.let {
            skipNextResumeRefresh = true
            viewModel.setLibrary(it)
        }
        restoreKioskIfNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folderPickerActive = savedInstanceState?.getBoolean(STATE_FOLDER_PICKER_ACTIVE) ?: false
        restoreAfterSettings = savedInstanceState?.getBoolean(STATE_RESTORE_AFTER_SETTINGS) ?: false
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        preferences = LibraryPreferences(this)

        binding.closeButton.setOnClickListener { finish() }
        binding.chooseLibraryButton.setOnClickListener { openFolderPicker() }
        binding.rescanButton.setOnClickListener { viewModel.refresh(force = true) }
        binding.kioskButton.setOnClickListener { toggleKiosk() }
        binding.androidButton.setOnClickListener { openAndroidTemporarily() }
        binding.resetProgressButton.setOnClickListener { confirmResetProgress() }

        val labels = listOf(
            getString(R.string.mode_single),
            getString(R.string.mode_continue_series),
            getString(R.string.mode_menene_tv),
        )
        binding.playbackModeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labels,
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.playbackModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!playbackSpinnerBinding) viewModel.setPlaybackMode(playbackModes[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.audioLanguageSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf(
                getString(R.string.audio_romanian),
                getString(R.string.audio_english),
                getString(R.string.audio_original),
            ),
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.audioLanguageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!audioSpinnerBinding) viewModel.setPreferredAudioLanguage(audioLanguages[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        KioskController.applyImmersive(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_FOLDER_PICKER_ACTIVE, folderPickerActive)
        outState.putBoolean(STATE_RESTORE_AFTER_SETTINGS, restoreAfterSettings)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (restoreAfterSettings || (!folderPickerActive && preferences.resumeKioskAfterExternalActivity)) {
            restoreAfterSettings = false
            restoreKioskIfNeeded()
        } else if (!folderPickerActive) {
            KioskController.applyImmersive(this)
        }
        viewModel.refreshKioskState()
        if (skipNextResumeRefresh) skipNextResumeRefresh = false
        else viewModel.refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !folderPickerActive && !restoreAfterSettings) KioskController.applyImmersive(this)
    }

    private fun render(state: AdminUiState) = with(binding) {
        loading.visibility = if (state is AdminUiState.Loading) View.VISIBLE else View.GONE
        content.visibility = if (state is AdminUiState.Loading) View.INVISIBLE else View.VISIBLE
        when (state) {
            AdminUiState.Loading -> Unit
            is AdminUiState.Error -> {
                libraryPath.text = preferences.libraryUri ?: getString(R.string.not_configured)
                librarySummary.text = state.message
                diagnostics.text = getString(R.string.admin_diagnostics_unavailable)
                bindKiosk(KioskController.state(this@AdminActivity))
            }
            is AdminUiState.Ready -> {
                libraryPath.text = state.libraryUri ?: getString(R.string.not_configured)
                val catalog = state.catalog
                librarySummary.text = if (catalog == null) {
                    getString(R.string.not_configured)
                } else {
                    getString(
                        R.string.admin_library_summary,
                        catalog.series.size,
                        catalog.episodeCount,
                        catalog.source.name,
                    )
                }
                diagnostics.text = if (catalog == null) {
                    getString(R.string.admin_diagnostics_unavailable)
                } else {
                    buildString {
                        append(
                            getString(
                                R.string.admin_diagnostics_summary,
                                catalog.diagnostics.ignoredVideoCount,
                                catalog.diagnostics.missingSeriesArtworkCount,
                                catalog.diagnostics.missingEpisodeArtworkCount,
                                catalog.diagnostics.scanDurationMs,
                            ),
                        )
                        catalog.diagnostics.lastError?.takeIf(String::isNotBlank)?.let { warning ->
                            append("\n")
                            append(warning)
                        }
                    }
                }
                bindPlaybackMode(state.playbackMode)
                bindAudioLanguage(state.preferredAudioLanguage)
                bindKiosk(state.kioskState)
            }
        }
        versionText.text = getString(R.string.admin_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    }

    private fun bindPlaybackMode(mode: PlaybackMode) {
        playbackSpinnerBinding = true
        binding.playbackModeSpinner.setSelection(playbackModes.indexOf(mode).coerceAtLeast(0), false)
        binding.playbackModeSpinner.post { playbackSpinnerBinding = false }
    }

    private fun bindAudioLanguage(language: String) {
        audioSpinnerBinding = true
        binding.audioLanguageSpinner.setSelection(audioLanguages.indexOf(language).takeIf { it >= 0 } ?: 0, false)
        binding.audioLanguageSpinner.post { audioSpinnerBinding = false }
    }

    private fun bindKiosk(state: KioskState) {
        binding.kioskStatus.text = getString(
            when (state) {
                KioskState.DISABLED -> R.string.kiosk_disabled_status
                KioskState.FULLSCREEN_ONLY -> R.string.kiosk_soft
                KioskState.SCREEN_PINNING -> R.string.kiosk_screen_pinning
                KioskState.LOCK_TASK_ACTIVE -> R.string.kiosk_device_owner
                KioskState.DEVICE_OWNER_READY -> R.string.kiosk_device_owner_ready
            },
        )
        binding.kioskButton.setText(
            if (preferences.kioskEnabled) R.string.disable_kiosk else R.string.enable_kiosk,
        )
    }

    private fun confirmResetProgress() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_progress)
            .setMessage(R.string.reset_progress_confirmation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.reset) { _, _ -> viewModel.clearProgress() }
            .show()
    }

    private fun toggleKiosk() {
        if (preferences.kioskEnabled) KioskController.disableKiosk(this)
        else if (preferences.libraryUri != null) KioskController.enableKiosk(this)
        viewModel.refreshKioskState()
    }

    private fun openFolderPicker() {
        preferences.resumeKioskAfterExternalActivity =
            preferences.kioskEnabled && KioskController.isLocked(this)
        KioskController.prepareForExternalActivity(this)
        folderPickerActive = true
        runCatching { folderPicker.launch(null) }
            .onFailure {
                folderPickerActive = false
                restoreKioskIfNeeded()
            }
    }

    private fun openAndroidTemporarily() {
        preferences.resumeKioskAfterExternalActivity = preferences.kioskEnabled
        restoreAfterSettings = true
        KioskController.openAndroidSettingsTemporarily(this)
    }

    private fun restoreKioskIfNeeded() {
        folderPickerActive = false
        val shouldResume = preferences.resumeKioskAfterExternalActivity
        preferences.resumeKioskAfterExternalActivity = false
        if (shouldResume && preferences.kioskEnabled) KioskController.prepareAndEnter(this)
        else KioskController.applyImmersive(this)
        viewModel.refreshKioskState()
    }

    companion object {
        private const val STATE_FOLDER_PICKER_ACTIVE = "folder_picker_active"
        private const val STATE_RESTORE_AFTER_SETTINGS = "restore_after_settings"
    }
}
