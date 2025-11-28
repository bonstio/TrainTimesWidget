package net.bonstio.traintimes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
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
    private lateinit var doneButton: Button
    private lateinit var addToHomeButton: Button

    private val frequencyValues = intArrayOf(0, 30, 60, 120)

    companion object {
        const val EXTRA_INVALID_API_KEY = "invalid_api_key"
    }

    /**
     * Called when the activity is starting.
     * Initializes the UI and loads saved settings.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down, this Bundle contains the data it most recently supplied in onSaveInstanceState.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        apiKeyInput = findViewById(R.id.api_key_input)
        val apiKeyInputLayout = findViewById<TextInputLayout>(R.id.api_key_input_layout)
        updateFrequencySpinner = findViewById(R.id.update_frequency_spinner)
        doneButton = findViewById(R.id.done_button)
        addToHomeButton = findViewById(R.id.add_to_home_button)

        // Setup Request API Key Link
        val requestApiKeyLink = findViewById<TextView>(R.id.request_api_key_link)
        val url = "https://realtime.nationalrail.co.uk/OpenLDBWSRegistration"
        val text = "Request API key"
        val spannable = SpannableString(text)
        spannable.setSpan(URLSpan(url), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        requestApiKeyLink.text = spannable
        requestApiKeyLink.movementMethod = LinkMovementMethod.getInstance()

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

        doneButton.setOnClickListener {
            saveSettings()
            updateWidgets()
            finish()
        }

        setupAddToHomeButton()

        if (intent.getBooleanExtra(EXTRA_INVALID_API_KEY, false)) {
            apiKeyInputLayout.error = "Invalid API key"
        }
    }

    private fun setupAddToHomeButton() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
            addToHomeButton.setOnClickListener {
                Log.d("TrainWidgetDebug", "Requesting pin widget...")
                val myProvider = ComponentName(this, TrainTimesWidgetProvider::class.java)
                
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
            }
        } else {
            addToHomeButton.isEnabled = false
            addToHomeButton.visibility = View.GONE
        }
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     * Saves the current settings.
     */
    override fun onPause() {
        super.onPause()
        saveSettings()
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

        prefs.edit {
            putString(PREF_API_KEY, apiKeyInput.text.toString())
            putInt(PREF_UPDATE_FREQUENCY, frequency)
        }

        WidgetUpdateScheduler.scheduleUpdate(this, frequency)
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
}