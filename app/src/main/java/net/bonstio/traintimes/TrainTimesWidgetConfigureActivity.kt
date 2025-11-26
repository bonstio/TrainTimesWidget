package net.bonstio.traintimes

import android.animation.ValueAnimator
import android.app.Activity
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.PaintDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Activity for configuring the Train Times widget.
 * Handles station selection, display options, and color customization.
 */
class TrainTimesWidgetConfigureActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    
    // Route Tab Views
    private lateinit var rowDepartingFrom: View
    private lateinit var summaryDepartingFrom: TextView
    private lateinit var timeDepartingFrom: TextView
    private lateinit var rowDestination: View
    private lateinit var summaryDestination: TextView
    private lateinit var timeDestination: TextView
    
    // Layout Tab Views
    private lateinit var rowStyle: View
    private lateinit var summaryStyle: TextView
    private lateinit var rowCustomTitle: View
    private lateinit var summaryCustomTitle: TextView
    private lateinit var rowAlignment: View
    private lateinit var summaryAlignment: TextView
    private lateinit var rowFontSize: View
    private lateinit var summaryFontSize: TextView
    private lateinit var rowStationStops: View
    private lateinit var summaryStationStops: TextView
    private lateinit var rowDivider: View
    private lateinit var switchShowDivider: MaterialSwitch
    private lateinit var rowIconVisibility: View
    private lateinit var summaryIconVisibility: TextView
    private lateinit var rowFooter: View
    private lateinit var summaryFooter: TextView
    
    private var showWidgetIcon = WidgetConfigurationDefaults.SHOW_ICON
    private var showRefreshIcon = WidgetConfigurationDefaults.SHOW_REFRESH_ICON
    private var showSettingsIcon = WidgetConfigurationDefaults.SHOW_SETTINGS_ICON
    
    private var showMapsIcon = WidgetConfigurationDefaults.SHOW_MAPS_ICON
    private var showLastUpdateTime = WidgetConfigurationDefaults.SHOW_LAST_UPDATE_TIME
    private var showDivider = WidgetConfigurationDefaults.SHOW_DIVIDER

    // Advanced Tab Views
    private lateinit var rowOffset: View
    private lateinit var summaryOffset: TextView
    private lateinit var rowDepartureCount: View
    private lateinit var summaryDepartureCount: TextView
    private lateinit var switchHidePastDepartures: MaterialSwitch
    private lateinit var rowGlobalSettings: View

    private lateinit var addButton: Button
    private lateinit var cancelButton: Button

    // Color Tab Views
    private lateinit var rowTextColor: View
    private lateinit var summaryTextColor: TextView
    private lateinit var previewTextColor: View
    private lateinit var previewTextColorCheckerboard: View
    private lateinit var rowBackgroundColor: View
    private lateinit var summaryBackgroundColor: TextView
    private lateinit var previewBackgroundColor: View
    private lateinit var previewBackgroundColorCheckerboard: View

    private lateinit var tabLayout: TabLayout
    private lateinit var routeContent: View
    private lateinit var layoutContent: View
    private lateinit var colorsContent: View
    private lateinit var advancedContent: View

    private var startTimeNormal = 360  // 06:00
    private var startTimeReverse = 960  // 16:00

    private enum class ColorMode { TEXT, BACKGROUND }

    private var activeColorMode = ColorMode.TEXT

    private var currentTextColor = WidgetConfigurationDefaults.TEXT_COLOR
    private var currentBackgroundColor = ColorUtils.setAlphaComponent(WidgetConfigurationDefaults.BG_COLOR, WidgetConfigurationDefaults.TRANSPARENCY)

    private var isTextSystemColor = true
    private var isBackgroundSystemColor = true

    private var isUpdatingColor = false

    // Flags for smart default title style logic
    private var userChangedTitleStyle = false
    private var isUpdatingTitleStyle = false

    private lateinit var stations: List<Station>
    
    // State variables
    private var fromStationCode: String = ""
    private var toStationCode: String = ""
    private var selectedTitleStyle: String = WidgetConfigurationDefaults.TITLE_STYLE
    private var selectedAlignment: String = WidgetConfigurationDefaults.ALIGNMENT
    private var selectedFontSize: Int = WidgetConfigurationDefaults.FONT_SIZE
    private var customTitleText: String = ""
    private var selectedStationStopsMode: String = WidgetConfigurationDefaults.STATION_STOPS_MODE
    private var selectedOffset: Int = WidgetConfigurationDefaults.TIME_OFFSET
    private var selectedDepartureCount: Int = WidgetConfigurationDefaults.DEPARTURE_COUNT

    /**
     * Called when the activity is first created.
     * Initializes the UI, listeners, and loads any existing configuration.
     *
     * @param savedInstanceState Bundle containing the activity's previously frozen state, if there was one.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)
        setContentView(R.layout.widget_configure)

        val window = window
        val layoutParams = window.attributes
        layoutParams.gravity = Gravity.BOTTOM
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        window.attributes = layoutParams

        // Route Tab Bindings
        rowDepartingFrom = findViewById(R.id.row_departing_from)
        summaryDepartingFrom = findViewById(R.id.summary_departing_from)
        timeDepartingFrom = findViewById(R.id.time_departing_from)
        rowDestination = findViewById(R.id.row_destination)
        summaryDestination = findViewById(R.id.summary_destination)
        timeDestination = findViewById(R.id.time_destination)
        
        addButton = findViewById(R.id.add_button)
        cancelButton = findViewById(R.id.cancel_button)
        
        // Layout Tab Bindings
        rowStyle = findViewById(R.id.row_style)
        summaryStyle = findViewById(R.id.summary_style)
        rowCustomTitle = findViewById(R.id.row_custom_title)
        summaryCustomTitle = findViewById(R.id.summary_custom_title)
        rowAlignment = findViewById(R.id.row_alignment)
        summaryAlignment = findViewById(R.id.summary_alignment)
        rowFontSize = findViewById(R.id.row_font_size)
        summaryFontSize = findViewById(R.id.summary_font_size)
        rowStationStops = findViewById(R.id.row_station_stops)
        summaryStationStops = findViewById(R.id.summary_station_stops)
        rowDivider = findViewById(R.id.row_divider)
        switchShowDivider = findViewById(R.id.switch_show_divider)
        rowIconVisibility = findViewById(R.id.row_icon_visibility)
        summaryIconVisibility = findViewById(R.id.summary_icon_visibility)
        rowFooter = findViewById(R.id.row_footer)
        summaryFooter = findViewById(R.id.summary_footer)
        
        // Advanced Tab Bindings
        rowOffset = findViewById(R.id.row_offset)
        summaryOffset = findViewById(R.id.summary_offset)
        rowDepartureCount = findViewById(R.id.row_departure_count)
        summaryDepartureCount = findViewById(R.id.summary_departure_count)
        switchHidePastDepartures = findViewById(R.id.switch_hide_past_departures)
        rowGlobalSettings = findViewById(R.id.row_global_settings)

        // Color Views
        rowTextColor = findViewById(R.id.row_text_color)
        summaryTextColor = findViewById(R.id.summary_text_color)
        previewTextColor = findViewById(R.id.preview_text_color)
        previewTextColorCheckerboard = findViewById(R.id.preview_text_color_checkerboard)
        rowBackgroundColor = findViewById(R.id.row_background_color)
        summaryBackgroundColor = findViewById(R.id.summary_background_color)
        previewBackgroundColor = findViewById(R.id.preview_background_color)
        previewBackgroundColorCheckerboard = findViewById(R.id.preview_background_color_checkerboard)

        tabLayout = findViewById(R.id.tabs)
        routeContent = findViewById(R.id.tab_route_content)
        layoutContent = findViewById(R.id.tab_layout_content)
        colorsContent = findViewById(R.id.tab_colors_content)
        advancedContent = findViewById(R.id.tab_advanced_content)

        stations = StationRepository.getStations(this)

        setupRouteListeners()
        setupLayoutListeners()
        setupAdvancedListeners()

        // Setup checkerboards for color previews
        previewTextColorCheckerboard.background = createCheckerboardDrawable(0f)
        previewBackgroundColorCheckerboard.background = createCheckerboardDrawable(0f)

        rowTextColor.setOnClickListener { showColorPickerDialog(ColorMode.TEXT) }
        rowBackgroundColor.setOnClickListener { showColorPickerDialog(ColorMode.BACKGROUND) }

        cancelButton.setOnClickListener {
            finish()
        }

        addButton.setOnClickListener {
            val context = this@TrainTimesWidgetConfigureActivity

            val title = customTitleText
            val titleStyle = selectedTitleStyle
            val alignment = selectedAlignment
            
            val stationStopsMode = selectedStationStopsMode
            val showStops = (stationStopsMode != "NONE") 
            
            val timeOffset = selectedOffset
            val departureCount = selectedDepartureCount
            val hidePastDepartures = switchHidePastDepartures.isChecked

            val transparency = Color.alpha(currentBackgroundColor)
            val bgColor = Color.rgb(
                Color.red(currentBackgroundColor),
                Color.green(currentBackgroundColor),
                Color.blue(currentBackgroundColor)
            )

            val fontSize = selectedFontSize

            WidgetConfigurationStorage.saveConfiguration(
                context,
                appWidgetId,
                title,
                titleStyle,
                showWidgetIcon,
                showRefreshIcon,
                showSettingsIcon,
                hidePastDepartures,
                showStops,
                stationStopsMode,
                fromStationCode,
                toStationCode,
                alignment,
                startTimeNormal,
                startTimeReverse,
                timeOffset,
                departureCount,
                transparency,
                currentTextColor,
                bgColor,
                isTextSystemColor,
                isBackgroundSystemColor,
                fontSize,
                showMapsIcon,
                showLastUpdateTime,
                showDivider
            )

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val intent = Intent(context, TrainTimesWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            sendBroadcast(intent)

            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                updateTabVisibility(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val existingConfig = WidgetConfigurationStorage.loadConfiguration(this, appWidgetId)
        if (existingConfig != null) {
            fromStationCode = existingConfig.fromStation
            toStationCode = existingConfig.toStation
            
            customTitleText = existingConfig.title
            userChangedTitleStyle = true // Treat existing as manually set
            selectedTitleStyle = existingConfig.titleStyle
            
            showWidgetIcon = existingConfig.showIcon
            showRefreshIcon = existingConfig.showRefreshIcon
            showSettingsIcon = existingConfig.showSettingsIcon
            showMapsIcon = existingConfig.showMapsIcon
            showLastUpdateTime = existingConfig.showLastUpdateTime
            showDivider = existingConfig.showDivider
            
            selectedStationStopsMode = existingConfig.stationStopsMode
            selectedAlignment = existingConfig.alignment
            
            startTimeNormal = existingConfig.startTimeNormal
            startTimeReverse = existingConfig.startTimeReverse
            selectedOffset = existingConfig.timeOffset
            selectedDepartureCount = existingConfig.departureCount
            switchHidePastDepartures.isChecked = existingConfig.hidePastDepartures

            currentTextColor = existingConfig.textColor
            currentBackgroundColor = ColorUtils.setAlphaComponent(
                existingConfig.bgColor,
                existingConfig.transparency
            )

            isTextSystemColor = existingConfig.useSystemTextColor
            isBackgroundSystemColor = existingConfig.useSystemBgColor
            selectedFontSize = existingConfig.fontSize
        } else {
            selectedAlignment = WidgetConfigurationDefaults.ALIGNMENT
            
            isUpdatingTitleStyle = true
            selectedTitleStyle = "CUSTOM" // Default for new widget
            isUpdatingTitleStyle = false
            
            customTitleText = getString(R.string.default_from_only_title)
            
            showWidgetIcon = WidgetConfigurationDefaults.SHOW_ICON
            showRefreshIcon = WidgetConfigurationDefaults.SHOW_REFRESH_ICON
            showSettingsIcon = WidgetConfigurationDefaults.SHOW_SETTINGS_ICON
            showMapsIcon = WidgetConfigurationDefaults.SHOW_MAPS_ICON
            showLastUpdateTime = WidgetConfigurationDefaults.SHOW_LAST_UPDATE_TIME
            showDivider = WidgetConfigurationDefaults.SHOW_DIVIDER
            selectedStationStopsMode = WidgetConfigurationDefaults.STATION_STOPS_MODE
            
            selectedOffset = WidgetConfigurationDefaults.TIME_OFFSET
            selectedDepartureCount = WidgetConfigurationDefaults.DEPARTURE_COUNT
            switchHidePastDepartures.isChecked = WidgetConfigurationDefaults.HIDE_PAST_DEPARTURES
            currentTextColor = WidgetConfigurationDefaults.TEXT_COLOR
            currentBackgroundColor = ColorUtils.setAlphaComponent(WidgetConfigurationDefaults.BG_COLOR, WidgetConfigurationDefaults.TRANSPARENCY)
            isTextSystemColor = true
            isBackgroundSystemColor = true
            selectedFontSize = WidgetConfigurationDefaults.FONT_SIZE
        }
        
        // Sync switch state with loaded config
        switchShowDivider.isChecked = showDivider

        updateColorSummariesAndPreviews()
        updateLayoutSummaries()
        updateRouteSummaries()
        updateAdvancedSummaries()

        validateInputs()
        updateTabVisibility(0)
    }

    private fun setupRouteListeners() {
        rowDepartingFrom.setOnClickListener {
            showRouteDialog(
                R.string.route_departing_from,
                fromStationCode,
                startTimeNormal,
                360 // 06:00
            ) { station, time ->
                fromStationCode = station
                startTimeNormal = time
                updateRouteSummaries()
                validateInputs()
            }
        }

        rowDestination.setOnClickListener {
            showRouteDialog(
                R.string.route_destination,
                toStationCode,
                startTimeReverse,
                960 // 16:00
            ) { station, time ->
                toStationCode = station
                startTimeReverse = time
                updateRouteSummaries()
                validateInputs()
            }
        }
    }

    private fun showRouteDialog(titleRes: Int, currentStationCode: String, currentTime: Int, defaultTime: Int, onConfirm: (String, Int) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_route_config, null)
        val stationInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.dialog_station_input)
        val timeInput = view.findViewById<TextInputEditText>(R.id.dialog_time_input)
        val timeLayout = view.findViewById<TextInputLayout>(R.id.dialog_time_layout)
        val timeHelper = view.findViewById<TextView>(R.id.dialog_time_helper)

        timeLayout.visibility = View.VISIBLE
        timeHelper.visibility = View.VISIBLE

        val adapter = StationAdapter(this, stations)
        stationInput.setAdapter(adapter)
        setStationText(stationInput, currentStationCode)
        setupInputListeners(stationInput)

        var selectedTime = currentTime
        
        fun updateTimeDisplay() {
            if (selectedTime == -1) {
                timeInput.setText("")
                timeLayout.isEndIconVisible = false
            } else {
                timeInput.setText(formatTime(selectedTime))
                timeLayout.isEndIconVisible = true
            }
        }
        updateTimeDisplay()

        timeInput.setOnClickListener {
            showTimePicker(selectedTime, defaultTime) { minutes ->
                selectedTime = minutes
                updateTimeDisplay()
            }
        }
        
        timeLayout.setEndIconOnClickListener {
            selectedTime = -1
            updateTimeDisplay()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val stationCode = extractStationCode(stationInput.text.toString(), stations)
                val finalTime = selectedTime
                onConfirm(stationCode, finalTime)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            
        stationInput.post {
             stationInput.selectAll()
             stationInput.requestFocus()
        }
    }

    private fun updateRouteSummaries() {
        val fromName = if (fromStationCode.isNotEmpty()) {
            StationRepository.getStationName(this, fromStationCode)
        } else {
            getText(R.string.summary_station_required)
        }
        summaryDepartingFrom.text = fromName
        
        val toName = if (toStationCode.isNotEmpty()) {
            StationRepository.getStationName(this, toStationCode)
        } else {
            getText(R.string.summary_station_unspecified)
        }
        summaryDestination.text = toName
        
        val hasToStation = toStationCode.isNotEmpty()

        if (hasToStation && startTimeNormal != -1) {
            timeDepartingFrom.text = getString(R.string.shown_from_summary, formatTime(startTimeNormal))
            timeDepartingFrom.visibility = View.VISIBLE
        } else {
            timeDepartingFrom.visibility = View.GONE
        }

        if (hasToStation && startTimeReverse != -1) {
            timeDestination.text = getString(R.string.shown_from_summary, formatTime(startTimeReverse))
            timeDestination.visibility = View.VISIBLE
        } else {
            timeDestination.visibility = View.GONE
        }
    }

    private fun setupLayoutListeners() {
        rowStyle.setOnClickListener {
            val items = arrayOf(getString(R.string.title_style_short), getString(R.string.title_style_long), getString(R.string.title_style_custom))
            val values = arrayOf("SHORT", "LONG", "CUSTOM")
            val selectedIndex = values.indexOf(selectedTitleStyle).coerceAtLeast(0)
            
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_style)
                .setSingleChoiceItems(items, selectedIndex) { dialog, which ->
                    val newStyle = values[which]
                    if (newStyle != selectedTitleStyle) {
                        selectedTitleStyle = newStyle
                        if (selectedTitleStyle == "CUSTOM") {
                             if (customTitleText.isEmpty()) {
                                 customTitleText = getString(R.string.default_from_only_title)
                             }
                        }
                        userChangedTitleStyle = true
                        updateLayoutSummaries()
                    }
                    dialog.dismiss()
                }
                .show()
        }
        
        rowAlignment.setOnClickListener {
             val items = arrayOf(getString(R.string.alignment_left), getString(R.string.alignment_center), getString(R.string.alignment_right))
             val values = arrayOf("START", "CENTER", "END")
             val selectedIndex = values.indexOf(selectedAlignment).coerceAtLeast(0)
             
             MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_alignment)
                .setSingleChoiceItems(items, selectedIndex) { dialog, which ->
                    selectedAlignment = values[which]
                    updateLayoutSummaries()
                    dialog.dismiss()
                }
                .show()
        }
        
        rowFontSize.setOnClickListener {
             val items = arrayOf(
                 getString(R.string.font_size_extra_small),
                 getString(R.string.font_size_small), 
                 getString(R.string.font_size_regular), 
                 getString(R.string.font_size_large),
                 getString(R.string.font_size_extra_large)
             )
             val values = arrayOf(0, 1, 2, 3, 4)
             val selectedIndex = values.indexOf(selectedFontSize).coerceAtLeast(2)
             
             MaterialAlertDialogBuilder(this)
                .setTitle(R.string.font_size_section_header)
                .setSingleChoiceItems(items, selectedIndex) { dialog, which ->
                    selectedFontSize = values[which]
                    updateLayoutSummaries()
                    dialog.dismiss()
                }
                .show()
        }
        
        rowCustomTitle.setOnClickListener {
            val container = LinearLayout(this)
            container.orientation = LinearLayout.VERTICAL
            val margin = (24 * resources.displayMetrics.density).toInt()
            container.setPadding(margin, margin / 2, margin, 0)

            val input = TextInputEditText(this)
            input.setText(customTitleText)
            container.addView(input)

            val helperText = TextView(this)
            helperText.text = getString(R.string.widget_title_helper)
            val typedValue = TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.textAppearanceCaption, typedValue, true)
            if (typedValue.resourceId != 0) {
                helperText.setTextAppearance(typedValue.resourceId)
            }
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.topMargin = (8 * resources.displayMetrics.density).toInt()
            helperText.layoutParams = params
            container.addView(helperText)
            
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.widget_title_hint)
                .setView(container)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    customTitleText = input.text.toString()
                    updateLayoutSummaries()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            
            input.post {
                 input.selectAll()
                 input.requestFocus()
            }
        }
        
        rowStationStops.setOnClickListener {
            val items = arrayOf(
                getString(R.string.stops_first),
                getString(R.string.stops_all),
                getString(R.string.stops_hidden)
            )
            val values = arrayOf("FIRST", "ALL", "NONE")
            val selectedIndex = values.indexOf(selectedStationStopsMode).coerceAtLeast(0)
            
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.show_stops)
                .setSingleChoiceItems(items, selectedIndex) { dialog, which ->
                    selectedStationStopsMode = values[which]
                    updateLayoutSummaries()
                    dialog.dismiss()
                }
                .show()
        }

        rowDivider.setOnClickListener {
            switchShowDivider.toggle()
        }
        
        switchShowDivider.setOnCheckedChangeListener { _, isChecked ->
            showDivider = isChecked
        }

        rowIconVisibility.setOnClickListener {
            showIconVisibilityDialog()
        }
        
        rowFooter.setOnClickListener {
            showFooterVisibilityDialog()
        }
    }

    private fun showIconVisibilityDialog() {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        val padding = (24 * resources.displayMetrics.density).toInt()
        container.setPadding(padding, padding, padding, padding)

        val iconOptions = mapOf(
            getString(R.string.icon_widget) to { b: Boolean -> showWidgetIcon = b },
            getString(R.string.icon_refresh) to { b: Boolean -> showRefreshIcon = b },
            getString(R.string.icon_settings) to { b: Boolean -> showSettingsIcon = b }
        )
        val initialValues = listOf(showWidgetIcon, showRefreshIcon, showSettingsIcon)

        iconOptions.keys.forEachIndexed { index, text ->
            val switch = MaterialSwitch(this)
            switch.text = text
            switch.isChecked = initialValues[index]
            switch.setOnCheckedChangeListener { _, isChecked ->
                iconOptions[text]?.invoke(isChecked)
            }
            container.addView(switch)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.header_items_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                updateLayoutSummaries()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun showFooterVisibilityDialog() {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        val padding = (24 * resources.displayMetrics.density).toInt()
        container.setPadding(padding, padding, padding, padding)

        val options = mapOf(
            getString(R.string.item_maps) to { b: Boolean -> showMapsIcon = b },
            getString(R.string.item_last_updated) to { b: Boolean -> showLastUpdateTime = b }
        )
        val initialValues = listOf(showMapsIcon, showLastUpdateTime)

        options.keys.forEachIndexed { index, text ->
            val switch = MaterialSwitch(this)
            switch.text = text
            switch.isChecked = initialValues[index]
            switch.setOnCheckedChangeListener { _, isChecked ->
                options[text]?.invoke(isChecked)
            }
            container.addView(switch)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.footer_items_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                updateLayoutSummaries()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupAdvancedListeners() {
        rowOffset.setOnClickListener {
            val container = LinearLayout(this)
            container.orientation = LinearLayout.VERTICAL
            val margin = (24 * resources.displayMetrics.density).toInt()
            container.setPadding(margin, margin / 2, margin, 0)

            val input = TextInputEditText(this)
            input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            input.setText(selectedOffset.toString())
            container.addView(input)

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.offset_row_title)
                .setView(container)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val value = input.text.toString().toIntOrNull() ?: 0
                    if (value >= -120 && value <= 120) {
                        selectedOffset = value
                        updateAdvancedSummaries()
                        validateInputs()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
                
            input.post {
                 input.selectAll()
                 input.requestFocus()
            }
        }

        rowDepartureCount.setOnClickListener {
            val container = LinearLayout(this)
            container.orientation = LinearLayout.VERTICAL
            val margin = (24 * resources.displayMetrics.density).toInt()
            container.setPadding(margin, margin / 2, margin, 0)

            val input = TextInputEditText(this)
            input.inputType = InputType.TYPE_CLASS_NUMBER
            input.setText(selectedDepartureCount.toString())
            container.addView(input)

            val helperText = TextView(this)
            helperText.text = getString(R.string.departure_count_helper)
            val typedValue = TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.textAppearanceCaption, typedValue, true)
            if (typedValue.resourceId != 0) {
                helperText.setTextAppearance(typedValue.resourceId)
            }
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.topMargin = (8 * resources.displayMetrics.density).toInt()
            helperText.layoutParams = params
            container.addView(helperText)

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.departure_count_row_title)
                .setView(container)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val value = input.text.toString().toIntOrNull() ?: 12
                    if (value >= 1 && value <= 100) {
                        selectedDepartureCount = value
                        updateAdvancedSummaries()
                        validateInputs()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
                
            input.post {
                 input.selectAll()
                 input.requestFocus()
            }
        }

        rowGlobalSettings.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun updateAdvancedSummaries() {
        summaryOffset.text = getString(R.string.offset_summary_format, selectedOffset)
        summaryDepartureCount.text = selectedDepartureCount.toString()
    }

    private fun updateLayoutSummaries() {
        summaryStyle.text = when (selectedTitleStyle) {
            "SHORT" -> getString(R.string.title_style_short)
            "LONG" -> getString(R.string.title_style_long)
            "CUSTOM" -> getString(R.string.title_style_custom)
            else -> selectedTitleStyle
        }
        
        rowCustomTitle.visibility = if (selectedTitleStyle == "CUSTOM") View.VISIBLE else View.GONE
        summaryCustomTitle.text = if (customTitleText.isEmpty()) getString(R.string.default_custom_title) else customTitleText
        
        summaryAlignment.text = when (selectedAlignment) {
            "START" -> getString(R.string.alignment_left)
            "CENTER" -> getString(R.string.alignment_center)
            "END" -> getString(R.string.alignment_right)
            else -> getString(R.string.alignment_left)
        }
        
        summaryFontSize.text = when (selectedFontSize) {
            0 -> getString(R.string.font_size_extra_small)
            1 -> getString(R.string.font_size_small)
            3 -> getString(R.string.font_size_large)
            4 -> getString(R.string.font_size_extra_large)
            else -> getString(R.string.font_size_regular)
        }
        
        summaryStationStops.text = when (selectedStationStopsMode) {
            "FIRST" -> getString(R.string.stops_first)
            "ALL" -> getString(R.string.stops_all)
            "NONE" -> getString(R.string.stops_hidden)
            else -> getString(R.string.stops_first)
        }
        
        updateIconVisibilitySummary()
        updateFooterSummary()
    }

    private fun updateIconVisibilitySummary() {
        val visibleIcons = mutableListOf<String>()
        if (showWidgetIcon) visibleIcons.add(getString(R.string.icon_widget))
        if (showRefreshIcon) visibleIcons.add(getString(R.string.icon_refresh))
        if (showSettingsIcon) visibleIcons.add(getString(R.string.icon_settings))

        summaryIconVisibility.text = when {
            visibleIcons.isEmpty() -> getString(R.string.summary_no_icons)
            else -> getString(R.string.summary_items_displayed_format, visibleIcons.joinToString(", "))
        }
    }
    
    private fun updateFooterSummary() {
        val visibleItems = mutableListOf<String>()
        if (showMapsIcon) visibleItems.add(getString(R.string.item_maps))
        if (showLastUpdateTime) visibleItems.add(getString(R.string.item_last_updated))
        
        summaryFooter.text = when {
            visibleItems.isEmpty() -> getString(R.string.summary_nothing_displayed)
            else -> getString(R.string.summary_items_displayed_format, visibleItems.joinToString(", "))
        }
    }

    // ... rest of the file ...
    private fun updateTabVisibility(position: Int) {
        // Get the root view of the bottom sheet content
        val rootView = findViewById<View>(R.id.config_root_layout)
        val startHeight = window.decorView.height
    
        // Set the visibility of the tab content
        routeContent.visibility = if (position == 0) View.VISIBLE else View.GONE
        layoutContent.visibility = if (position == 1) View.VISIBLE else View.GONE
        colorsContent.visibility = if (position == 2) View.VISIBLE else View.GONE
        advancedContent.visibility = if (position == 3) View.VISIBLE else View.GONE
    
        // Post a runnable to measure the new height and animate
        rootView.post {
            // Force a measure of the root view to get the new target height
            rootView.measure(
                View.MeasureSpec.makeMeasureSpec(rootView.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val targetHeight = rootView.measuredHeight
    
            if (startHeight != targetHeight) {
                val animator = ValueAnimator.ofInt(startHeight, targetHeight)
                animator.addUpdateListener { animation ->
                    val params = window.attributes
                    params.height = animation.animatedValue as Int
                    window.attributes = params
                }
                animator.duration = 250
                animator.start()
            }
        }
    }

    private fun showTimePicker(minutes: Int, defaultMinutes: Int, callback: (Int) -> Unit) {
        val safeMinutes = if (minutes == -1) defaultMinutes else minutes
        val hour = safeMinutes / 60
        val minute = safeMinutes % 60
        TimePickerDialog(this, { _, h, m ->
            callback(h * 60 + m)
        }, hour, minute, true).show()
    }

    private fun updateTimeLabels() {
        // No longer used in the main UI, but keep method or remove?
        // Removing as it referenced removed views
    }

    private fun formatTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format(Locale.getDefault(), "%02d:%02d", h, m)
    }

    private fun extractStationCode(input: String, stations: List<Station>): String {
        val trimmed = input.trim()
        val match = stations.find {
            it.code.equals(trimmed, ignoreCase = true) ||
                    it.name.equals(trimmed, ignoreCase = true) ||
                    it.toString().equals(trimmed, ignoreCase = true)
        }
        return match?.code ?: trimmed.uppercase(Locale.getDefault())
    }

    private fun setStationText(view: MaterialAutoCompleteTextView, codeOrName: String) {
        val match = stations.find { it.code.equals(codeOrName, ignoreCase = true) }
        if (match != null) {
            view.setText(match.toString())
        } else {
            view.setText(codeOrName)
        }
    }

    private fun setupInputListeners(view: MaterialAutoCompleteTextView) {
        view.setOnClickListener {
            view.selectAll()
        }
        view.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                view.post { view.selectAll() }
            }
        }
    }

    private fun setupValidation() {
        // State changes handled in dialogs
    }

    private fun validateInputs() {
        val fromValid = isValidStation(fromStationCode)
        val toValid = toStationCode.trim().isEmpty() || isValidStation(toStationCode)

        addButton.isEnabled = fromValid && toValid

        // Smart default for title style
        if (!userChangedTitleStyle) {
            isUpdatingTitleStyle = true
            if (toStationCode.trim().isEmpty()) {
                if (selectedTitleStyle != "CUSTOM") {
                    selectedTitleStyle = "CUSTOM"
                }
                val defaultCustomTitle = getString(R.string.default_from_only_title)
                if (customTitleText != defaultCustomTitle) {
                    customTitleText = defaultCustomTitle
                }
            } else {
                if (selectedTitleStyle != "SHORT") {
                    selectedTitleStyle = "SHORT"
                }
            }
            updateLayoutSummaries()
            isUpdatingTitleStyle = false
        }
    }

    private fun isValidStation(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        return stations.any {
            it.code.equals(trimmed, ignoreCase = true) ||
                    it.name.equals(trimmed, ignoreCase = true) ||
                    it.toString().equals(trimmed, ignoreCase = true)
        }
    }



    private fun updateColorSummariesAndPreviews() {
        summaryTextColor.text = if (isTextSystemColor) getString(R.string.color_source_theme) else getString(R.string.color_source_custom)
        val textColor = if (isTextSystemColor) WidgetUtils.getThemeColor(this, com.google.android.material.R.attr.colorOnSurface) else currentTextColor
        previewTextColor.setBackgroundColor(textColor)
        previewTextColorCheckerboard.visibility = if (Color.alpha(textColor) < 255) View.VISIBLE else View.GONE


        summaryBackgroundColor.text = if (isBackgroundSystemColor) getString(R.string.color_source_theme) else getString(R.string.color_source_custom)
        val bgColor = if (isBackgroundSystemColor) WidgetUtils.getThemeColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest) else currentBackgroundColor
        previewBackgroundColor.setBackgroundColor(bgColor)
        previewBackgroundColorCheckerboard.visibility = if (Color.alpha(bgColor) < 255) View.VISIBLE else View.GONE
    }

    private fun showColorPickerDialog(mode: ColorMode) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)

        val colorSourceGroup: RadioGroup = dialogView.findViewById(R.id.color_source_group)
        val customColorControls: View = dialogView.findViewById(R.id.custom_color_controls)
        val textHueSlider: Slider = dialogView.findViewById(R.id.text_hue_slider)
        val textHueValueLabel: TextView = dialogView.findViewById(R.id.text_hue_value_label)
        val textSaturationSlider: Slider = dialogView.findViewById(R.id.text_saturation_slider)
        val textSaturationValueLabel: TextView = dialogView.findViewById(R.id.text_saturation_value_label)
        val textValueSlider: Slider = dialogView.findViewById(R.id.text_value_slider)
        val textLightnessValueLabel: TextView = dialogView.findViewById(R.id.text_value_value_label)
        val textAlphaSlider: Slider = dialogView.findViewById(R.id.text_alpha_slider)
        val textAlphaValueLabel: TextView = dialogView.findViewById(R.id.text_alpha_value_label)
        val textHexInput: TextInputEditText = dialogView.findViewById(R.id.text_hex_input)
        val textHueBackground: View = dialogView.findViewById(R.id.text_hue_background)
        val textSaturationBackground: View = dialogView.findViewById(R.id.text_saturation_background)
        val textValueBackground: View = dialogView.findViewById(R.id.text_value_background)
        val textAlphaBackground: View = dialogView.findViewById(R.id.text_alpha_background)
        val textAlphaCheckerboard: View = dialogView.findViewById(R.id.text_alpha_checkerboard)

        var isSystemColor = if (mode == ColorMode.TEXT) isTextSystemColor else isBackgroundSystemColor
        var currentColor = if (mode == ColorMode.TEXT) currentTextColor else currentBackgroundColor
        var dialogIsUpdatingColor = false

        fun updateDialogPreviews(color: Int) {
            val hex = String.format("#%08X", color)
            if (textHexInput.text.toString() != hex) {
                 textHexInput.setText(hex)
            }
        }

        fun updateSliderBackgrounds(hue: Float, saturation: Float, lightness: Float) {
            val cornerRadius = 12f * resources.displayMetrics.density

            val satColors = intArrayOf(
                ColorUtils.HSLToColor(floatArrayOf(hue, 0f, lightness)),
                ColorUtils.HSLToColor(floatArrayOf(hue, 1f, lightness))
            )
            val satDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, satColors)
            satDrawable.cornerRadius = cornerRadius
            textSaturationBackground.background = satDrawable

            val valueColors = intArrayOf(
                Color.BLACK,
                ColorUtils.HSLToColor(floatArrayOf(hue, saturation, 0.5f)),
                Color.WHITE
            )
            val valueDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, valueColors)
            valueDrawable.cornerRadius = cornerRadius
            textValueBackground.background = valueDrawable

            val baseColor = ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness))
            val alphaColors = intArrayOf(Color.TRANSPARENT, baseColor)
            val alphaDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, alphaColors)
            alphaDrawable.cornerRadius = cornerRadius
            textAlphaBackground.background = alphaDrawable
        }

        fun updateSlidersFromColor(color: Int) {
            if (dialogIsUpdatingColor) return
            dialogIsUpdatingColor = true

            val hsl = floatArrayOf(0f, 0f, 0f)
            ColorUtils.colorToHSL(color, hsl)

            val hue = hsl[0]
            val saturation = hsl[1]
            val lightness = hsl[2]
            val alpha = Color.alpha(color)

            textHueSlider.value = hue
            textSaturationSlider.value = saturation * 100f
            textValueSlider.value = lightness * 100f
            textAlphaSlider.value = alpha.toFloat()

            textHueValueLabel.text = hue.toInt().toString()
            textSaturationValueLabel.text = (saturation * 100f).roundToInt().toString()
            textLightnessValueLabel.text = (lightness * 100f).roundToInt().toString()
            textAlphaValueLabel.text = formatAlpha(alpha)

            updateSliderBackgrounds(hue, saturation, lightness)
            updateDialogPreviews(color)
            dialogIsUpdatingColor = false
        }

        fun updateFromSliders() {
            if (dialogIsUpdatingColor) return
            dialogIsUpdatingColor = true

            val hue = textHueSlider.value
            val saturation = textSaturationSlider.value / 100f
            val lightness = textValueSlider.value / 100f
            val alpha = textAlphaSlider.value.toInt()

            textHueValueLabel.text = hue.toInt().toString()
            textSaturationValueLabel.text = textSaturationSlider.value.toInt().toString()
            textLightnessValueLabel.text = textValueSlider.value.toInt().toString()
            textAlphaValueLabel.text = formatAlpha(alpha)

            val colorInt = ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness))
            currentColor = ColorUtils.setAlphaComponent(colorInt, alpha)

            updateSliderBackgrounds(hue, saturation, lightness)
            updateDialogPreviews(currentColor)
            dialogIsUpdatingColor = false
        }

        fun updateFromHex(hex: String) {
            if (dialogIsUpdatingColor) return
            try {
                val color = Color.parseColor(hex)
                currentColor = color
                updateSlidersFromColor(color)
            } catch (e: IllegalArgumentException) {
                // Invalid hex
            }
        }

        // Initial state
        colorSourceGroup.check(if (isSystemColor) R.id.color_source_system else R.id.color_source_custom)
        customColorControls.visibility = if (isSystemColor) View.GONE else View.VISIBLE
        updateSlidersFromColor(currentColor)

        // Setup checkerboards
        val density = resources.displayMetrics.density
        textAlphaCheckerboard.background = createCheckerboardDrawable(12f * density)

        val hueColors = IntArray(7)
        for (i in 0..6) {
            hueColors[i] = ColorUtils.HSLToColor(floatArrayOf(i * 60f, 1f, 0.5f))
        }
        val hueDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, hueColors)
        hueDrawable.cornerRadius = 12f * density
        textHueBackground.background = hueDrawable

        // Listeners
        colorSourceGroup.setOnCheckedChangeListener { _, checkedId ->
            isSystemColor = (checkedId == R.id.color_source_system)
            customColorControls.visibility = if (isSystemColor) View.GONE else View.VISIBLE
        }

        val sliderListener = Slider.OnChangeListener { _, _, _ -> updateFromSliders() }
        textHueSlider.addOnChangeListener(sliderListener)
        textSaturationSlider.addOnChangeListener(sliderListener)
        textValueSlider.addOnChangeListener(sliderListener)
        textAlphaSlider.addOnChangeListener(sliderListener)

        textHexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateFromHex(s.toString())
            }
        })

        MaterialAlertDialogBuilder(this)
            .setTitle(if (mode == ColorMode.TEXT) R.string.text_color else R.string.background_color)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (mode == ColorMode.TEXT) {
                    isTextSystemColor = isSystemColor
                    currentTextColor = currentColor
                } else {
                    isBackgroundSystemColor = isSystemColor
                    currentBackgroundColor = currentColor
                }
                updateColorSummariesAndPreviews()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createCheckerboardDrawable(cornerRadius: Float): Drawable {
        val density = resources.displayMetrics.density
        val size = (16 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.style = Paint.Style.FILL

        val color1 = Color.LTGRAY
        val color2 = Color.WHITE

        paint.color = color1
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        paint.color = color2
        val half = size / 2f
        canvas.drawRect(0f, 0f, half, half, paint)
        canvas.drawRect(half, half, size.toFloat(), size.toFloat(), paint)

        val drawable = PaintDrawable()
        drawable.shape = RectShape()
        if (cornerRadius > 0) {
            drawable.setCornerRadius(cornerRadius)
        }
        drawable.shaderFactory = object : ShapeDrawable.ShaderFactory() {
            override fun resize(width: Int, height: Int): Shader {
                return BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
        }
        return drawable
    }

    private fun formatAlpha(alpha: Int): String {
        return ((alpha / 255f) * 100).roundToInt().toString()
    }
}