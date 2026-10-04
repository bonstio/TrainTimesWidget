package net.bonstio.traintimes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import com.google.android.material.color.DynamicColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Main Activity of the application.
 * Used for general settings like the API key configuration.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var updateFrequencySpinner: Spinner
    private lateinit var prefs: SharedPreferences
    private lateinit var addToHomeButton: Button
    private lateinit var batteryOptimizationBanner: View
    private lateinit var apiKeySourceRadioGroup: android.widget.RadioGroup
    private lateinit var radioApiKeyDefault: com.google.android.material.radiobutton.MaterialRadioButton
    private lateinit var radioApiKeyCustom: com.google.android.material.radiobutton.MaterialRadioButton
    private lateinit var customApiKeyContainer: View

    private val frequencyValues = intArrayOf(0, 30, 60, 120)

    private var validationJob: Job? = null

    companion object {
        const val EXTRA_INVALID_API_KEY = "invalid_api_key"
        const val EXTRA_THROTTLED_API_KEY = "throttled_api_key"
    }

    /**
     * Called when the activity is starting.
     * Initializes the UI and loads saved settings.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down, this Bundle contains the data it most recently supplied in onSaveInstanceState.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        apiKeyInput = findViewById(R.id.api_key_input)
        val apiKeyInputLayout = findViewById<TextInputLayout>(R.id.api_key_input_layout)
        updateFrequencySpinner = findViewById(R.id.update_frequency_spinner)
        addToHomeButton = findViewById(R.id.add_to_home_button)
        batteryOptimizationBanner = findViewById(R.id.battery_optimization_banner)

        apiKeySourceRadioGroup = findViewById(R.id.api_key_source_radio_group)
        radioApiKeyDefault = findViewById(R.id.radio_api_key_default)
        radioApiKeyCustom = findViewById(R.id.radio_api_key_custom)
        customApiKeyContainer = findViewById(R.id.custom_api_key_container)

        // Setup Request API Key Link
        val requestApiKeyLink = findViewById<TextView>(R.id.request_api_key_link)
        val url = "https://github.com/bonstio/TrainTimesWidget/blob/main/API_KEY_GUIDE.md"
        val text = getString(R.string.request_api_key)
        val spannable = SpannableString(text)
        spannable.setSpan(URLSpan(url), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        requestApiKeyLink.text = spannable
        requestApiKeyLink.movementMethod = LinkMovementMethod.getInstance()

        // Setup API Key Source Radio Group
        val savedSource = ApiKeyManager.getApiKeySource(this)
        val isCustom = savedSource == API_KEY_SOURCE_CUSTOM
        radioApiKeyCustom.isChecked = isCustom
        radioApiKeyDefault.isChecked = !isCustom
        customApiKeyContainer.visibility = if (isCustom) View.VISIBLE else View.GONE
        
        val sharedKeyWarningBanner = findViewById<View>(R.id.shared_key_warning_banner)
        sharedKeyWarningBanner.visibility = if (isCustom) View.GONE else View.VISIBLE

        val sharedKeyInstructionsLink = findViewById<View>(R.id.shared_key_instructions_link)
        sharedKeyInstructionsLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bonstio/TrainTimesWidget/blob/main/API_KEY_GUIDE.md"))
            startActivity(intent)
        }

        apiKeySourceRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val customSelected = checkedId == R.id.radio_api_key_custom
            customApiKeyContainer.visibility = if (customSelected) View.VISIBLE else View.GONE
            sharedKeyWarningBanner.visibility = if (customSelected) View.GONE else View.VISIBLE
            apiKeyInputLayout.error = null
        }

        // Setup Spinner
        ArrayAdapter.createFromResource(
            this,
            R.array.update_frequency_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            updateFrequencySpinner.adapter = adapter
        }

        // Load saved settings
        apiKeyInput.setText(prefs.getString(PREF_API_KEY, ""))
        val savedFrequency = prefs.getInt(PREF_UPDATE_FREQUENCY, 30) // Default to 30 mins
        val selectionIndex = frequencyValues.indexOf(savedFrequency)
        if (selectionIndex >= 0) {
            updateFrequencySpinner.setSelection(selectionIndex)
        } else {
            updateFrequencySpinner.setSelection(1) // Default to 30m
        }

        setupAddToHomeButton()
        setupBatteryOptimizationBanner()

        if (intent.getBooleanExtra(EXTRA_INVALID_API_KEY, false)) {
            radioApiKeyCustom.isChecked = true
            apiKeyInputLayout.error = getString(R.string.invalid_api_key)
        } else if (intent.getBooleanExtra(EXTRA_THROTTLED_API_KEY, false)) {
            radioApiKeyCustom.isChecked = true
            apiKeyInputLayout.error = getString(R.string.api_throttled_error)
        }

        apiKeyInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && radioApiKeyCustom.isChecked) {
                validateApiKey(apiKeyInput.text?.toString().orEmpty(), apiKeyInputLayout)
            }
        }

        if (!prefs.getBoolean(PREF_PROMINENT_DISCLOSURE_SHOWN, false)) {
            showProminentDisclosure()
        }
    }

    private fun validateApiKey(key: String, layout: TextInputLayout) {
        val trimmedKey = key.trim()
        if (trimmedKey.isEmpty()) {
            layout.error = null
            return
        }

        validationJob?.cancel()
        validationJob = lifecycleScope.launch {
            try {
                val client = RailDataClient(trimmedKey)
                // Test the key by requesting departures for a known station (e.g. MAN = Manchester Piccadilly)
                withContext(Dispatchers.IO) {
                    client.getNextTrain("MAN", "", numRows = 1)
                }
                layout.error = null
                // Clear any stored INVALID_KEY errors across widgets so they refresh cleanly
                clearWidgetInvalidKeyErrors()
            } catch (e: Exception) {
                if ((e is io.ktor.client.plugins.ClientRequestException) &&
                    (e.response.status.value == 401 || e.response.status.value == 403)) {
                    layout.error = getString(R.string.invalid_api_key)
                }
            }
        }
    }

    private fun clearWidgetInvalidKeyErrors() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val ids = appWidgetManager.getAppWidgetIds(
            ComponentName(this, TrainTimesWidgetProvider::class.java)
        )
        prefs.edit {
            for (id in ids) {
                val lastError = prefs.getString(TrainTimesWidgetProvider.PREF_LAST_ERROR + id, null)
                if (lastError == "INVALID_KEY") {
                    remove(TrainTimesWidgetProvider.PREF_LAST_ERROR + id)
                }
            }
        }
    }

    private fun setupAddToHomeButton() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val myProvider = ComponentName(this, TrainTimesWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(myProvider)
        val hasExistingWidget = widgetIds.any { id ->
            WidgetConfigurationStorage.loadConfiguration(this, id) != null
        }

        if (hasExistingWidget) {
            addToHomeButton.text = getString(R.string.done)
            addToHomeButton.isEnabled = true
            addToHomeButton.visibility = View.VISIBLE
            addToHomeButton.setOnClickListener {
                saveSettings()
                updateWidgets()
                finish()
            }
        } else {
            addToHomeButton.text = getString(R.string.add_to_home_screen)
            addToHomeButton.isEnabled = true
            addToHomeButton.visibility = View.VISIBLE
            addToHomeButton.setOnClickListener {
                saveSettings()
                updateWidgets()
                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    Log.d("TrainWidgetDebug", "Requesting pin widget...")
                    val intent = Intent(this, TrainTimesWidgetProvider::class.java).apply {
                        action = TrainTimesWidgetProvider.ACTION_WIDGET_PINNED
                    }

                    val successCallback = PendingIntent.getBroadcast(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )

                    appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
                    finish()
                } else {
                    Toast.makeText(this, R.string.pinning_not_supported, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupBatteryOptimizationBanner() {
        batteryOptimizationBanner.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     * Saves the current settings.
     */
    override fun onResume() {
        super.onResume()
        setupAddToHomeButton()
        checkBatteryOptimization()
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
        updateWidgets()
    }

    /**
     * Saves the API key and other settings to SharedPreferences.
     */
    private fun saveSettings() {
        val selectedPosition = updateFrequencySpinner.selectedItemPosition
        val frequency = if (selectedPosition in frequencyValues.indices) {
            frequencyValues[selectedPosition]
        } else {
            30
        }

        val source = if (radioApiKeyCustom.isChecked) API_KEY_SOURCE_CUSTOM else API_KEY_SOURCE_DEFAULT

        prefs.edit {
            putString(PREF_API_KEY_SOURCE, source)
            putString(PREF_API_KEY, apiKeyInput.text.toString().trim())
            putInt(PREF_UPDATE_FREQUENCY, frequency)
        }

        WidgetUpdateScheduler.scheduleUpdate(this, frequency)
    }

    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName)

        if (!isIgnoring) {
            batteryOptimizationBanner.visibility = View.VISIBLE
        } else {
            batteryOptimizationBanner.visibility = View.GONE
        }
    }

    /**
     * Sends a broadcast to update all instances of the TrainTimesWidget.
     */
    private fun updateWidgets() {
        val intent = Intent(this, TrainTimesWidgetProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(
            ComponentName(application, TrainTimesWidgetProvider::class.java)
        )
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    private fun showProminentDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Disclosure")
            .setMessage("Train Times Widget collects location data to enable finding the nearest train stations and updating widget information based on your current location, even when the app is closed or not in use.")
            .setPositiveButton("Acknowledge") { _, _ ->
                prefs.edit { putBoolean(PREF_PROMINENT_DISCLOSURE_SHOWN, true) }
            }
            .setNegativeButton("No thanks") { _, _ ->
                prefs.edit { putBoolean(PREF_PROMINENT_DISCLOSURE_SHOWN, true) }
            }
            .setCancelable(false)
            .show()
    }
}