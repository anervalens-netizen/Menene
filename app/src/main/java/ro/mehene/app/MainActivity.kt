package ro.mehene.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import ro.mehene.app.data.LibraryPreferences
import ro.mehene.app.data.LibraryRepository
import ro.mehene.app.databinding.ActivityMainBinding
import ro.mehene.app.kiosk.KioskController
import ro.mehene.app.model.SeriesItem
import ro.mehene.app.ui.SeriesAdapter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: LibraryPreferences
    private lateinit var repository: LibraryRepository
    private lateinit var adapter: SeriesAdapter
    private val scanExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var logoTapCount = 0
    private var firstLogoTapAt = 0L

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        val permissionTaken = runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (!permissionTaken) {
            Toast.makeText(this, R.string.folder_error, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        preferences.libraryUri = uri.toString()
        Toast.makeText(this, R.string.folder_saved, Toast.LENGTH_SHORT).show()
        loadLibrary()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = LibraryPreferences(this)
        repository = LibraryRepository(this)
        adapter = SeriesAdapter(::openSeries)

        binding.seriesList.layoutManager = GridLayoutManager(this, 3)
        binding.seriesList.adapter = adapter
        binding.seriesList.setHasFixedSize(true)
        binding.setupButton.setOnClickListener { chooseLibraryFolder() }
        binding.logo.setOnClickListener { registerParentTap() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        KioskController.prepareAndEnter(this)
        loadLibrary()
    }

    override fun onResume() {
        super.onResume()
        KioskController.prepareAndEnter(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) KioskController.applyImmersive(this)
    }

    override fun onDestroy() {
        scanExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun loadLibrary() {
        binding.loading.visibility = View.VISIBLE
        binding.emptyPanel.visibility = View.GONE
        binding.seriesList.visibility = View.INVISIBLE

        scanExecutor.execute {
            val configured = repository.isConfigured()
            val readable = !configured || repository.isLibraryReadable()
            val result = runCatching {
                if (configured && !readable) error("Library permission unavailable")
                repository.scanSeries()
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.loading.visibility = View.GONE
                result.fold(
                    onSuccess = { series -> showSeries(series, configured) },
                    onFailure = { showLibraryError() },
                )
            }
        }
    }

    private fun showSeries(series: List<SeriesItem>, configured: Boolean) {
        adapter.submitItems(series)
        binding.seriesList.visibility = if (series.isEmpty()) View.INVISIBLE else View.VISIBLE
        binding.emptyPanel.visibility = if (series.isEmpty()) View.VISIBLE else View.GONE

        if (series.isEmpty()) {
            binding.emptyTitle.setText(R.string.empty_title)
            binding.emptyMessage.setText(
                if (configured) R.string.no_series else R.string.empty_message,
            )
            binding.setupButton.visibility = View.VISIBLE
        }
    }

    private fun showLibraryError() {
        adapter.submitItems(emptyList())
        binding.seriesList.visibility = View.INVISIBLE
        binding.emptyPanel.visibility = View.VISIBLE
        binding.emptyTitle.setText(R.string.empty_title)
        binding.emptyMessage.setText(R.string.scan_error)
        binding.setupButton.visibility = View.VISIBLE
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
        folderPicker.launch(null)
    }

    private fun registerParentTap() {
        val now = SystemClock.elapsedRealtime()
        if (now - firstLogoTapAt > PARENT_TAP_WINDOW_MS) {
            firstLogoTapAt = now
            logoTapCount = 0
        }
        logoTapCount += 1
        if (logoTapCount >= PARENT_TAP_COUNT) {
            logoTapCount = 0
            showPinDialog()
        }
    }

    private fun showPinDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.parent_pin_hint)
            textSize = 22f
            setPadding(48, 20, 48, 20)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.parent_pin_title)
            .setMessage(
                if (preferences.parentPin == LibraryPreferences.DEFAULT_PARENT_PIN) {
                    getString(R.string.default_pin_info)
                } else null,
            )
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (input.text.toString() == preferences.parentPin) {
                    dialog.dismiss()
                    showParentMenu()
                } else {
                    input.error = getString(R.string.parent_pin_wrong)
                }
            }
        }
        dialog.show()
    }

    private fun showParentMenu() {
        val fullyManaged = KioskController.isDeviceOwner(this)
        val status = getString(
            if (fullyManaged) R.string.kiosk_device_owner else R.string.kiosk_soft,
        )
        val kioskAction = getString(
            if (preferences.kioskEnabled) R.string.disable_kiosk else R.string.enable_kiosk,
        )
        val entries = arrayOf(
            getString(R.string.choose_library),
            getString(R.string.rescan_library),
            getString(R.string.change_pin),
            kioskAction,
            getString(R.string.temporary_exit),
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.parent_menu_title)
            .setMessage(status)
            .setItems(entries) { _, index ->
                when (index) {
                    0 -> chooseLibraryFolder()
                    1 -> loadLibrary()
                    2 -> showChangePinDialog()
                    3 -> toggleKiosk()
                    4 -> exitTemporarily()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showChangePinDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "4–8 cifre"
            textSize = 22f
            setPadding(48, 20, 48, 20)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.change_pin)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newPin = input.text.toString()
                if (newPin.length in 4..8 && newPin.all(Char::isDigit)) {
                    preferences.parentPin = newPin
                    Toast.makeText(this, R.string.pin_changed, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    input.error = "PIN-ul trebuie să aibă 4–8 cifre"
                }
            }
        }
        dialog.show()
    }

    private fun toggleKiosk() {
        if (preferences.kioskEnabled) {
            preferences.kioskEnabled = false
            KioskController.disableKiosk(this)
        } else {
            preferences.kioskEnabled = true
            if (KioskController.isDeviceOwner(this)) {
                KioskController.prepareAndEnter(this)
            } else {
                KioskController.requestScreenPinning(this)
            }
        }
        KioskController.applyImmersive(this)
    }

    private fun exitTemporarily() {
        preferences.kioskEnabled = false
        KioskController.openAndroidSettings(this)
    }

    companion object {
        private const val PARENT_TAP_COUNT = 5
        private const val PARENT_TAP_WINDOW_MS = 3_000L
    }
}
