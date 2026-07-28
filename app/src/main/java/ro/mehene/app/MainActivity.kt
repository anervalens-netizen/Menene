package ro.mehene.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import ro.mehene.app.data.LibraryPreferences
import ro.mehene.app.data.LibraryRepository
import ro.mehene.app.data.LibraryResult
import ro.mehene.app.databinding.ActivityMainBinding
import ro.mehene.app.kiosk.KioskController
import ro.mehene.app.kiosk.KioskState
import ro.mehene.app.model.SeriesItem
import ro.mehene.app.ui.ArtworkLoader
import ro.mehene.app.ui.SeriesAdapter
import ro.mehene.app.util.meheneGridColumns
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: LibraryPreferences
    private lateinit var repository: LibraryRepository
    private lateinit var adapter: SeriesAdapter
    private val scanExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var scanTask: Future<*>? = null

    private var logoTapCount = 0
    private var firstLogoTapAt = 0L
    private var folderPickerActive = false
    private var lastIgnoredVideoCount = 0

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        folderPickerActive = false
        if (uri != null) {
            repository.persistLibraryUri(uri).fold(
                onSuccess = {
                    ArtworkLoader.clearCache()
                    Toast.makeText(this, R.string.folder_saved, Toast.LENGTH_SHORT).show()
                    loadLibrary()
                },
                onFailure = {
                    Toast.makeText(this, R.string.folder_error, Toast.LENGTH_LONG).show()
                },
            )
        }
        resumeKioskAfterExternalActivity()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = LibraryPreferences(this)
        repository = LibraryRepository(this)
        adapter = SeriesAdapter(::openSeries)

        binding.seriesList.layoutManager = GridLayoutManager(this, meheneGridColumns())
        binding.seriesList.adapter = adapter
        binding.seriesList.setHasFixedSize(true)
        binding.setupButton.setOnClickListener { chooseLibraryFolder() }
        binding.logo.setOnClickListener { registerAdministrationTap() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        KioskController.prepareAndEnter(this)
        loadLibrary()
    }

    override fun onResume() {
        super.onResume()
        if (!folderPickerActive && !preferences.resumeKioskAfterExternalActivity) {
            KioskController.prepareAndEnter(this)
        } else {
            KioskController.applyImmersive(this)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !folderPickerActive) KioskController.applyImmersive(this)
    }

    override fun onDestroy() {
        scanTask?.cancel(true)
        scanExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun loadLibrary() {
        binding.loading.visibility = View.VISIBLE
        binding.emptyPanel.visibility = View.GONE
        binding.seriesList.visibility = View.INVISIBLE
        scanTask?.cancel(true)

        scanTask = scanExecutor.submit {
            val result = repository.scanSeries()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.loading.visibility = View.GONE
                showLibraryResult(result)
            }
        }
    }

    private fun showLibraryResult(result: LibraryResult<List<SeriesItem>>) {
        when (result) {
            is LibraryResult.Success -> {
                lastIgnoredVideoCount = result.ignoredVideoCount
                showSeries(result.value)
            }

            LibraryResult.NotConfigured -> showLibraryMessage(
                titleRes = R.string.library_not_configured,
                messageRes = R.string.empty_message,
                allowSetup = true,
            )

            LibraryResult.PermissionLost -> showLibraryMessage(
                titleRes = R.string.library_unavailable,
                messageRes = R.string.library_permission_lost,
                allowSetup = false,
            )

            LibraryResult.StorageUnavailable -> showLibraryMessage(
                titleRes = R.string.library_unavailable,
                messageRes = R.string.library_storage_unavailable,
                allowSetup = false,
            )

            is LibraryResult.Failure -> showLibraryMessage(
                titleRes = R.string.library_unavailable,
                messageRes = R.string.scan_error,
                allowSetup = false,
            )
        }
    }

    private fun showSeries(series: List<SeriesItem>) {
        adapter.submitItems(series)
        binding.seriesList.visibility = if (series.isEmpty()) View.INVISIBLE else View.VISIBLE
        binding.emptyPanel.visibility = if (series.isEmpty()) View.VISIBLE else View.GONE
        binding.setupButton.visibility = View.GONE

        if (series.isEmpty()) {
            binding.emptyTitle.setText(R.string.empty_title)
            binding.emptyMessage.setText(R.string.no_series)
        }
    }

    private fun showLibraryMessage(titleRes: Int, messageRes: Int, allowSetup: Boolean) {
        adapter.submitItems(emptyList())
        binding.seriesList.visibility = View.INVISIBLE
        binding.emptyPanel.visibility = View.VISIBLE
        binding.emptyTitle.setText(titleRes)
        binding.emptyMessage.setText(messageRes)
        binding.setupButton.visibility = if (allowSetup) View.VISIBLE else View.GONE
    }

    private fun openSeries(series: SeriesItem) {
        startActivity(
            Intent(this, SeriesActivity::class.java).apply {
                putExtra(SeriesActivity.EXTRA_SERIES_TITLE, series.title)
                putExtra(SeriesActivity.EXTRA_SERIES_URI, series.directoryUri)
            },
        )
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
                Toast.makeText(this, R.string.folder_error, Toast.LENGTH_LONG).show()
            }
    }

    private fun resumeKioskAfterExternalActivity() {
        val shouldResume = preferences.resumeKioskAfterExternalActivity
        preferences.resumeKioskAfterExternalActivity = false
        if (shouldResume && preferences.kioskEnabled) {
            KioskController.prepareAndEnter(this)
        } else {
            KioskController.applyImmersive(this)
        }
    }

    // Deliberately opens administration without authentication; see docs/NO_SECURITY.md.
    private fun registerAdministrationTap() {
        val now = SystemClock.elapsedRealtime()
        if (now - firstLogoTapAt > ADMIN_TAP_WINDOW_MS) {
            firstLogoTapAt = now
            logoTapCount = 0
        }
        logoTapCount += 1
        if (logoTapCount >= ADMIN_TAP_COUNT) {
            logoTapCount = 0
            showAdministrationMenu()
        }
    }

    private fun showAdministrationMenu() {
        val kioskState = KioskController.state(this)
        val kioskAction = getString(
            if (preferences.kioskEnabled) R.string.disable_kiosk else R.string.enable_kiosk,
        )
        val entries = arrayOf(
            getString(R.string.choose_library),
            getString(R.string.rescan_library),
            kioskAction,
            getString(R.string.library_diagnostics),
            getString(R.string.temporary_exit),
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.administration_menu_title)
            .setMessage(kioskStatusText(kioskState))
            .setItems(entries) { _, index ->
                when (index) {
                    0 -> chooseLibraryFolder()
                    1 -> {
                        ArtworkLoader.clearCache()
                        loadLibrary()
                    }
                    2 -> toggleKiosk()
                    3 -> showLibraryDiagnostics()
                    4 -> KioskController.openAndroidSettings(this)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toggleKiosk() {
        if (preferences.kioskEnabled) {
            KioskController.disableKiosk(this)
            Toast.makeText(this, R.string.kiosk_disabled, Toast.LENGTH_SHORT).show()
            return
        }
        if (!repository.isConfigured()) {
            Toast.makeText(this, R.string.configure_library_first, Toast.LENGTH_LONG).show()
            return
        }

        KioskController.enableKiosk(this)
        val message = if (KioskController.isDeviceOwner(this)) {
            R.string.kiosk_enabled_full
        } else {
            R.string.kiosk_enabled_soft
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showLibraryDiagnostics() {
        val text = if (lastIgnoredVideoCount == 0) {
            getString(R.string.library_diagnostics_ok)
        } else {
            getString(R.string.library_diagnostics_ignored, lastIgnoredVideoCount)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.library_diagnostics)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun kioskStatusText(state: KioskState): String = getString(
        when (state) {
            KioskState.DISABLED -> R.string.kiosk_disabled_status
            KioskState.FULLSCREEN_ONLY -> R.string.kiosk_soft
            KioskState.SCREEN_PINNING -> R.string.kiosk_screen_pinning
            KioskState.LOCK_TASK_ACTIVE -> R.string.kiosk_device_owner
            KioskState.DEVICE_OWNER_READY -> R.string.kiosk_device_owner_ready
        },
    )

    companion object {
        private const val ADMIN_TAP_COUNT = 5
        private const val ADMIN_TAP_WINDOW_MS = 3_000L
    }
}
