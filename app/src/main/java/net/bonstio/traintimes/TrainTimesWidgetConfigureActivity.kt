package net.bonstio.traintimes

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.PaintDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.min

/**
 * Activity for configuring the Train Times widget.
 * Handles station selection, display options, and color customization.
 */
class TrainTimesWidgetConfigureActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    
    // Route Tab Views
    private lateinit var rowDepartingFrom: View
    private lateinit var summaryDepartingFrom: TextView
    private lateinit var rowDestination: View
    private lateinit var summaryDestination: TextView
    private lateinit var rowCommuteTimes: View
    private lateinit var labelCommuteTimes: TextView
    private lateinit var summaryCommuteTimes: TextView
    
    // Layout Tab Views
    private lateinit var rowStyle: View
    private lateinit var summaryStyle: TextView
    private lateinit var rowFontStyle: View
    private lateinit var summaryFontStyle: TextView
    private lateinit var rowCustomTitle: View
    private lateinit var summaryCustomTitle: TextView
    private lateinit var rowAlignment: View
    private lateinit var summaryAlignment: TextView
    private lateinit var rowFontSize: View
    private lateinit var summaryFontSize: TextView
    private lateinit var sliderFontSize: Slider
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
    private lateinit var rowMaxJourneyDuration: View
    private lateinit var switchMaxJourneyDuration: MaterialSwitch
    private lateinit var summaryMaxJourneyDuration: TextView
    private lateinit var sliderMaxJourneyDuration: Slider
    private lateinit var rowGlobalSettings: View

    private lateinit var addButton: Button
    private lateinit var cancelButton: Button
    private lateinit var batteryOptimizationBanner: View

    // Color Views
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
    private lateinit var rootView: View
    private lateinit var slidingSheet: View

    private var startTimeNormal = 360  // 06:00
    private var startTimeReverse = 960  // 16:00

    private enum class ColorMode { TEXT, BACKGROUND }

    private var currentTextColor = WidgetConfigurationDefaults.TEXT_COLOR
    private var currentBackgroundColor = ColorUtils.setAlphaComponent(WidgetConfigurationDefaults.BG_COLOR, WidgetConfigurationDefaults.TRANSPARENCY)

    private var isTextSystemColor = true
    private var isBackgroundSystemColor = true

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
    private var selectedFontStyle: String = WidgetConfigurationDefaults.FONT_STYLE
    private var customTitleText: String = ""
    private var selectedStationStopsMode: String = WidgetConfigurationDefaults.STATION_STOPS_MODE
    private var selectedOffset: Int = WidgetConfigurationDefaults.TIME_OFFSET
    private var selectedDepartureCount: Int = WidgetConfigurationDefaults.DEPARTURE_COUNT
    private var selectedCommutingMode: String = WidgetConfigurationDefaults.COMMUTING_MODE
    private var enableJourneyDurationFilter: Boolean = WidgetConfigurationDefaults.ENABLE_JOURNEY_DURATION_FILTER
    private var maxJourneyDuration: Int = WidgetConfigurationDefaults.MAX_JOURNEY_DURATION

    // Initial config for comparison
    private var initialFromStationCode: String = ""
    private var initialToStationCode: String = ""
    private var initialStartTimeNormal: Int = 360
    private var initialStartTimeReverse: Int = 960
    private var initialOffset: Int = WidgetConfigurationDefaults.TIME_OFFSET
    private var initialDepartureCount: Int = WidgetConfigurationDefaults.DEPARTURE_COUNT
    private var initialHidePastDepartures: Boolean = WidgetConfigurationDefaults.HIDE_PAST_DEPARTURES
    private var initialEnableJourneyDurationFilter: Boolean = WidgetConfigurationDefaults.ENABLE_JOURNEY_DURATION_FILTER
    private var initialMaxJourneyDuration: Int = WidgetConfigurationDefaults.MAX_JOURNEY_DURATION

    // Permission launcher
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        if (granted) {
            updateRouteSummaries()
        }
    }

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
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
        window.attributes = layoutParams

        rootView = findViewById(R.id.config_root_layout)
        slidingSheet = findViewById(R.id.sliding_sheet_container)

        // Route Tab Bindings
        rowDepartingFrom = findViewById(R.id.row_departing_from)
        summaryDepartingFrom = findViewById(R.id.summary_departing_from)
        rowDestination = findViewById(R.id.row_destination)
        summaryDestination = findViewById(R.id.summary_destination)
        rowCommuteTimes = findViewById(R.id.row_commute_times)
        labelCommuteTimes = findViewById(R.id.label_commute_times)
        summaryCommuteTimes = findViewById(R.id.summary_commute_times)
        
        addButton = findViewById(R.id.add_button)
        cancelButton = findViewById(R.id.cancel_button)
        batteryOptimizationBanner = findViewById(R.id.battery_optimization_banner)
        
        // Layout Tab Bindings
        rowStyle = findViewById(R.id.row_style)
        summaryStyle = findViewById(R.id.summary_style)
        rowFontStyle = findViewById(R.id.row_font_style)
        summaryFontStyle = findViewById(R.id.summary_font_style)
        rowCustomTitle = findViewById(R.id.row_custom_title)
        summaryCustomTitle = findViewById(R.id.summary_custom_title)
        rowAlignment = findViewById(R.id.row_alignment)
        summaryAlignment = findViewById(R.id.summary_alignment)
        rowFontSize = findViewById(R.id.row_font_size)
        summaryFontSize = findViewById(R.id.summary_font_size)
        // sliderFontSize bind
        sliderFontSize = findViewById(R.id.slider_font_size)
        
        rowStationStops = findViewById(R.id.row_station_stops)
        summaryStationStops = findViewById(R.id.summary_station_stops)
        rowDivider = findViewById(R.id.row_divider)
        switchShowDivider = findViewById(R.id.switch_show_divider)
        rowIconVisibility = findViewById(R.id.row_icon_visibility)
        summaryIconVisibility = findViewById(R.id.summary_icon_visibility)
        rowFooter = findViewById(R.id.row_footer)
        summaryFooter = findViewById(R.id.summary_footer)
        
        // Hide the separate custom title row as it is now integrated into the dialog
        rowCustomTitle.visibility = View.GONE
        
        // Advanced Tab Bindings
        rowOffset = findViewById(R.id.row_offset)
        summaryOffset = findViewById(R.id.summary_offset)
        rowDepartureCount = findViewById(R.id.row_departure_count)
        summaryDepartureCount = findViewById(R.id.summary_departure_count)
        switchHidePastDepartures = findViewById(R.id.switch_hide_past_departures)
        
        rowMaxJourneyDuration = findViewById(R.id.row_max_journey_duration)
        switchMaxJourneyDuration = findViewById(R.id.switch_max_journey_duration)
        summaryMaxJourneyDuration = findViewById(R.id.summary_max_journey_duration)
        sliderMaxJourneyDuration = findViewById(R.id.slider_max_journey_duration)
        
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
        setupDragToMove()
        setupBatteryOptimizationBanner()
        
        setupFadeAnimation(sliderFontSize)
        setupFadeAnimation(sliderMaxJourneyDuration)

        // Setup checkerboards for color previews
        previewTextColorCheckerboard.background = createCheckerboardDrawable(0f)
        previewBackgroundColorCheckerboard.background = createCheckerboardDrawable(0f)

        rowTextColor.setOnClickListener { showColorPickerDialog(ColorMode.TEXT) }
        rowBackgroundColor.setOnClickListener { showColorPickerDialog(ColorMode.BACKGROUND) }

        cancelButton.setOnClickListener {
            finish()
        }

        addButton.setOnClickListener {
            // Read global frequency preference, defaulting to 30 mins
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val frequency = prefs.getInt(PREF_UPDATE_FREQUENCY, 30)
            WidgetUpdateScheduler.scheduleUpdate(this, frequency)
            saveAndBroadcast(finish = true)
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
            selectedCommutingMode = existingConfig.commutingMode
            
            enableJourneyDurationFilter = existingConfig.enableJourneyDurationFilter
            maxJourneyDuration = existingConfig.maxJourneyDuration

            currentTextColor = existingConfig.textColor
            currentBackgroundColor = ColorUtils.setAlphaComponent(
                existingConfig.bgColor,
                existingConfig.transparency
            )

            isTextSystemColor = existingConfig.useSystemTextColor
            isBackgroundSystemColor = existingConfig.useSystemBgColor
            selectedFontSize = existingConfig.fontSize
            selectedFontStyle = existingConfig.fontStyle
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
            
            enableJourneyDurationFilter = WidgetConfigurationDefaults.ENABLE_JOURNEY_DURATION_FILTER
            maxJourneyDuration = WidgetConfigurationDefaults.MAX_JOURNEY_DURATION
            
            currentTextColor = WidgetConfigurationDefaults.TEXT_COLOR
            currentBackgroundColor = ColorUtils.setAlphaComponent(WidgetConfigurationDefaults.BG_COLOR, WidgetConfigurationDefaults.TRANSPARENCY)
            isTextSystemColor = true
            isBackgroundSystemColor = true
            selectedFontSize = WidgetConfigurationDefaults.FONT_SIZE
            selectedFontStyle = WidgetConfigurationDefaults.FONT_STYLE
        }

        // Store initial data-affecting config
        initialFromStationCode = fromStationCode
        initialToStationCode = toStationCode
        initialStartTimeNormal = startTimeNormal
        initialStartTimeReverse = startTimeReverse
        initialOffset = selectedOffset
        initialDepartureCount = selectedDepartureCount
        initialHidePastDepartures = switchHidePastDepartures.isChecked
        initialEnableJourneyDurationFilter = enableJourneyDurationFilter
        initialMaxJourneyDuration = maxJourneyDuration
        
        // Sync switch state with loaded config
        switchShowDivider.isChecked = showDivider
        // Set initial slider value
        sliderFontSize.value = selectedFontSize.toFloat()
        
        switchMaxJourneyDuration.isChecked = enableJourneyDurationFilter
        sliderMaxJourneyDuration.value = maxJourneyDuration.toFloat()
        
        // Visibility of journey duration elements
        val durationVisible = if (enableJourneyDurationFilter) View.VISIBLE else View.GONE
        summaryMaxJourneyDuration.visibility = durationVisible
        sliderMaxJourneyDuration.visibility = durationVisible

        updateColorSummariesAndPreviews()
        updateLayoutSummaries()
        updateRouteSummaries()
        updateAdvancedSummaries()

        validateInputs()
        updateTabVisibility(0)
    }

    override fun onResume() {
        super.onResume()
        checkBatteryOptimization()
    }
    
    private fun setUiFade(faded: Boolean, excludedView: View) {
        val targetAlpha = if (faded) 0f else 1f
        val duration = 200L

        // Helper to animate alpha
        fun animateView(v: View) {
            v.animate().alpha(targetAlpha).setDuration(duration).start()
        }

        // Animate sliding sheet background transparency
        val bg = slidingSheet.background
        if (bg != null) {
            bg.mutate()
            val targetBgAlpha = if (faded) 0 else 255
            val startBgAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                bg.alpha
            } else {
                if (faded) 255 else 0
            }

            ValueAnimator.ofInt(startBgAlpha, targetBgAlpha).apply {
                this.duration = duration
                addUpdateListener { animator ->
                    bg.alpha = animator.animatedValue as Int
                }
                start()
            }
        }
        
        // Animate the window background dim
        val window = window
        val initialDim = window.attributes.dimAmount
        val targetDim = if (faded) 0f else 0.5f // Assuming default dim is around 0.5-0.6
        
        // If we are starting fresh (not reversing mid-animation), assume current dim is what we want
        // But dimAmount might not be readable on older APIs or reliable if we haven't set it.
        // It's safer to just set the target dim.
        
        val valueAnimator = ValueAnimator.ofFloat(if (faded) 0.5f else 0f, targetDim)
        valueAnimator.duration = duration
        valueAnimator.addUpdateListener { animator ->
            val dim = animator.animatedValue as Float
            val layoutParams = window.attributes
            layoutParams.dimAmount = dim
            window.attributes = layoutParams
        }
        valueAnimator.start()

        // Walk up from excludedView
        var current: View = excludedView
        while (current.parent is ViewGroup) {
            val parent = current.parent as ViewGroup
            if (parent.id == android.R.id.content) break // Stop at content root
            if (parent.id == R.id.config_root_layout) {
                 // Also fade siblings of sliding sheet (e.g. pinned buttons)
                 for (i in 0 until parent.childCount) {
                     val child = parent.getChildAt(i)
                     if (child != slidingSheet) { 
                         animateView(child)
                     }
                 }
                 break
            }

            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child != current) {
                    animateView(child)
                }
            }
            current = parent
        }
    }
    
    private fun setupFadeAnimation(slider: Slider) {
        var isFaded = false
        val fadeRunnable = Runnable { 
            isFaded = true
            setUiFade(true, slider) 
        }
        
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                isFaded = false
                slider.postDelayed(fadeRunnable, 750L)
            }

            override fun onStopTrackingTouch(slider: Slider) {
                slider.removeCallbacks(fadeRunnable)
                if (isFaded) {
                    setUiFade(false, slider)
                    isFaded = false
                }
            }
        })
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

    private fun setupBatteryOptimizationBanner() {
        batteryOptimizationBanner.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }
    }

    private fun performHapticFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use a slightly stronger vibration (40ms) for feedback
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(40)
        }
    }

    private fun setupDragToMove() {
        val headerView = findViewById<View>(R.id.header_container) ?: return

        headerView.setOnClickListener {
            togglePosition()
        }

        headerView.setOnTouchListener(object : View.OnTouchListener {
            private var lastY = 0f
            private var startY = 0f
            private var isDragging = false
            private val touchSlop = 10f
            private var hasVibratedTop = false
            private var hasVibratedBottom = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastY = event.rawY
                        startY = event.rawY
                        isDragging = false
                        
                        // Mark as already vibrated if starting in the trigger zones
                        hasVibratedTop = slidingSheet.translationY < 2f
                        val maxTrans = calculateMaxTranslation()
                        hasVibratedBottom = slidingSheet.translationY > maxTrans - 2f
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - lastY
                        val totalDy = event.rawY - startY
                        
                        if (!isDragging && abs(totalDy) > touchSlop) {
                            isDragging = true
                        }
                        
                        if (isDragging) {
                            val maxTrans = calculateMaxTranslation()
                            val newTranslationY = slidingSheet.translationY + dy
                            // Clamp between 0 and maxTranslation
                            val clampedTranslationY = min(maxTrans, max(0f, newTranslationY))
                            slidingSheet.translationY = clampedTranslationY

                            // Vibration feedback when hitting top
                            if (clampedTranslationY < 2f && !hasVibratedTop) {
                                performHapticFeedback()
                                hasVibratedTop = true
                            } else if (clampedTranslationY > 20f) {
                                hasVibratedTop = false
                            }
                            
                            // Vibration feedback when hitting bottom
                            if (clampedTranslationY > maxTrans - 2f && !hasVibratedBottom) {
                                performHapticFeedback()
                                hasVibratedBottom = true
                            } else if (clampedTranslationY < maxTrans - 20f) {
                                hasVibratedBottom = false
                            }
                            
                            lastY = event.rawY
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            // User requested to stop automatic move back.
                            // We leave it as is. 
                        } else {
                            // It was a tap
                            v.performClick()
                        }
                        isDragging = false
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun getVisibleStripHeight(): Int {
        val contentView = findViewById<View>(android.R.id.content)
        val headerView = findViewById<View>(R.id.header_container) ?: return 0

        // Calculate bottom inset to ensure header is visible above nav bar
        var bottomInset = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            contentView.rootWindowInsets?.let {
                bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    it.getInsets(WindowInsets.Type.systemBars()).bottom
                } else {
                    @Suppress("DEPRECATION")
                    it.systemWindowInsetBottom
                }
            }
        }

        // Extra buffer to ensure it clears the nav bar comfortably (e.g. 16dp)
        val extraBuffer = (16 * resources.displayMetrics.density).toInt()

        return headerView.height + bottomInset + extraBuffer
    }

    private fun calculateMaxTranslation(): Float {
        val visibleStripHeight = getVisibleStripHeight()
        // slidingSheet.height can be 0 if the view is not laid out yet.
        if (slidingSheet.height == 0) return 0f
        return max(0f, (slidingSheet.height - visibleStripHeight).toFloat())
    }

    private fun togglePosition() {
        val maxTranslation = calculateMaxTranslation()
        
        val targetY = if (slidingSheet.translationY < maxTranslation / 2) maxTranslation else 0f
        
        ObjectAnimator.ofFloat(slidingSheet, "translationY", targetY).apply {
            duration = 300
            start()
        }
    }

    private fun triggerUpdate(forceStyleOnly: Boolean = false) {
        saveAndBroadcast(finish = false)
    }

    private fun saveAndBroadcast(finish: Boolean) {
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
        val fontStyle = selectedFontStyle

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
            showDivider,
            selectedCommutingMode,
            fontStyle,
            enableJourneyDurationFilter,
            maxJourneyDuration
        )

        // Check if data changed
        val dataChanged = initialFromStationCode != fromStationCode ||
                initialToStationCode != toStationCode ||
                initialStartTimeNormal != startTimeNormal ||
                initialStartTimeReverse != startTimeReverse ||
                initialOffset != timeOffset ||
                initialDepartureCount != departureCount ||
                initialHidePastDepartures != hidePastDepartures ||
                initialEnableJourneyDurationFilter != enableJourneyDurationFilter ||
                initialMaxJourneyDuration != maxJourneyDuration

        // If data changed, update our "initial" reference so subsequent style updates don't trigger data fetch
        if (dataChanged) {
            initialFromStationCode = fromStationCode
            initialToStationCode = toStationCode
            initialStartTimeNormal = startTimeNormal
            initialStartTimeReverse = startTimeReverse
            initialOffset = timeOffset
            initialDepartureCount = departureCount
            initialHidePastDepartures = hidePastDepartures
            initialEnableJourneyDurationFilter = enableJourneyDurationFilter
            initialMaxJourneyDuration = maxJourneyDuration
        }

        val intent = Intent(context, TrainTimesWidgetProvider::class.java)
        if (dataChanged) {
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        } else {
            intent.action = TrainTimesWidgetProvider.ACTION_WIDGET_STYLE_UPDATE
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        sendBroadcast(intent)

        if (finish) {
            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }

    private fun setupRouteListeners() {
        rowDepartingFrom.setOnClickListener {
            showRouteDialog(
                R.string.route_departing_from,
                fromStationCode
            ) { station ->
                fromStationCode = station
                updateRouteSummaries()
                validateInputs()
                updateLayoutSummaries()
                if (addButton.isEnabled) {
                    triggerUpdate()
                }
            }
        }

        rowDestination.setOnClickListener {
            showRouteDialog(
                R.string.route_destination,
                toStationCode
            ) { station ->
                toStationCode = station
                updateRouteSummaries()
                validateInputs()
                updateLayoutSummaries()
                if (addButton.isEnabled) {
                    triggerUpdate()
                }
            }
        }
        
        rowCommuteTimes.setOnClickListener {
            showCommuteTimesDialog()
        }
    }

    private fun showRouteDialog(titleRes: Int, currentStationCode: String, onConfirm: (String) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_route_config, null)
        val stationInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.dialog_station_input)
        val stationLayout = view.findViewById<TextInputLayout>(R.id.dialog_station_layout)

        // Configure clear button
        stationLayout.setEndIconOnClickListener {
            stationInput.setText("")
        }

        stationInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                stationLayout.isEndIconVisible = !s.isNullOrEmpty()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Initial visibility
        stationLayout.isEndIconVisible = !currentStationCode.isEmpty()

        val adapter = StationAdapter(this, stations)
        stationInput.setAdapter(adapter)
        setStationText(stationInput, currentStationCode)
        setupInputListeners(stationInput)

        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val stationCode = extractStationCode(stationInput.text.toString(), stations)
                onConfirm(stationCode)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            
        stationInput.post {
             stationInput.selectAll()
             stationInput.requestFocus()
        }
    }
    
    private fun showCommuteTimesDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_commute_times, null)
        val commutingModeGroup = view.findViewById<RadioGroup>(R.id.commuting_mode_radio_group)
        val timeModeContainer = view.findViewById<View>(R.id.time_mode_container)
        val locationModeContainer = view.findViewById<View>(R.id.location_mode_container)
        val outboundInput = view.findViewById<TextInputEditText>(R.id.outbound_time_input)
        val outboundLayout = view.findViewById<TextInputLayout>(R.id.outbound_time_layout)
        val returnInput = view.findViewById<TextInputEditText>(R.id.return_time_input)
        val returnLayout = view.findViewById<TextInputLayout>(R.id.return_time_layout)

        fun updateVisibility(mode: String) {
            if (mode == "LOCATION") {
                timeModeContainer.visibility = View.INVISIBLE
                locationModeContainer.visibility = View.VISIBLE
            } else {
                timeModeContainer.visibility = View.VISIBLE
                locationModeContainer.visibility = View.INVISIBLE
            }
        }

        // Initial State
        var currentMode = selectedCommutingMode
        if (currentMode == "LOCATION") {
            commutingModeGroup.check(R.id.radio_location_mode)
        } else {
            commutingModeGroup.check(R.id.radio_time_mode)
        }
        updateVisibility(currentMode)

        commutingModeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radio_location_mode) {
                currentMode = "LOCATION"
                updateVisibility("LOCATION")
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                     locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            } else {
                currentMode = "TIME"
                updateVisibility("TIME")
            }
        }

        fun setupTimeInput(input: TextInputEditText, layout: TextInputLayout, initialTime: Int, defaultTime: Int, onTimeChanged: (Int) -> Unit) {
             var currentTime = initialTime
             
             fun updateDisplay() {
                 if (currentTime == -1) {
                     input.setText("")
                     layout.isEndIconVisible = false
                 } else {
                     input.setText(formatTime(currentTime))
                     layout.isEndIconVisible = true
                 }
             }
             updateDisplay()
             
             input.setOnClickListener {
                 showTimePicker(currentTime, defaultTime) { minutes ->
                     currentTime = minutes
                     onTimeChanged(minutes)
                     updateDisplay()
                 }
             }
             
             layout.setEndIconOnClickListener {
                 currentTime = -1
                 onTimeChanged(-1)
                 updateDisplay()
             }
        }
        
        var tempStartTimeNormal = startTimeNormal
        var tempStartTimeReverse = startTimeReverse
        
        setupTimeInput(outboundInput, outboundLayout, startTimeNormal, 360) { tempStartTimeNormal = it }
        setupTimeInput(returnInput, returnLayout, startTimeReverse, 960) { tempStartTimeReverse = it }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.commute_times)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                selectedCommutingMode = currentMode
                startTimeNormal = tempStartTimeNormal
                startTimeReverse = tempStartTimeReverse
                updateRouteSummaries()
                updateLayoutSummaries()
                triggerUpdate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        
        rowCommuteTimes.isEnabled = hasToStation
        labelCommuteTimes.alpha = if (hasToStation) 1.0f else 0.5f
        summaryCommuteTimes.alpha = if (hasToStation) 1.0f else 0.5f
        
        if (!hasToStation) {
             summaryCommuteTimes.text = getString(R.string.not_set)
        } else if (selectedCommutingMode == "LOCATION") {
            summaryCommuteTimes.text = getString(R.string.summary_commuting_location)
            
            // Check permissions and fetch location to update text
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                
                val client = LocationServices.getFusedLocationProviderClient(this)
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            val closestStation = findClosestStation(location, fromStationCode, toStationCode)
                            if (closestStation != null) {
                                summaryCommuteTimes.text = getString(R.string.location_closest_summary, closestStation.code)
                            }
                        }
                    }
            }
        } else {
            val outboundStr = if (startTimeNormal != -1) formatTime(startTimeNormal) else getString(R.string.not_set)
            val returnStr = if (startTimeReverse != -1) formatTime(startTimeReverse) else getString(R.string.not_set)
            
            if (startTimeNormal == -1 && startTimeReverse == -1) {
                summaryCommuteTimes.text = getString(R.string.not_set)
            } else {
                summaryCommuteTimes.text = getString(R.string.commute_times_summary_format, outboundStr, returnStr)
            }
        }

        rowMaxJourneyDuration.isEnabled = hasToStation
        switchMaxJourneyDuration.isEnabled = hasToStation
        rowMaxJourneyDuration.alpha = if (hasToStation) 1.0f else 0.5f
    }
    
    private fun findClosestStation(location: Location, fromCode: String, toCode: String): Station? {
         val fromStation = StationRepository.getStation(this, fromCode)
         val toStation = StationRepository.getStation(this, toCode)
         
         if (fromStation == null && toStation == null) return null
         if (fromStation == null) return toStation
         if (toStation == null) return fromStation
         
         val distFrom = FloatArray(1)
         Location.distanceBetween(location.latitude, location.longitude, fromStation.lat, fromStation.lon, distFrom)
         
         val distTo = FloatArray(1)
         Location.distanceBetween(location.latitude, location.longitude, toStation.lat, toStation.lon, distTo)
         
         return if (distTo[0] < distFrom[0]) toStation else fromStation
    }

    // Custom Adapter for Single Choice Items with Checkmark
    private class CheckedItemAdapter(
        context: Context,
        private val items: Array<String>,
        private val selectedIndex: Int
    ) : ArrayAdapter<String>(context, R.layout.dialog_single_choice_item, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.dialog_single_choice_item, parent, false)
            
            val textView = view.findViewById<TextView>(R.id.text1)
            val iconView = view.findViewById<ImageView>(R.id.icon)
            
            textView.text = items[position]
            
            if (position == selectedIndex) {
                iconView.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.GONE
            }
            
            return view
        }
    }

    private fun setupLayoutListeners() {
        rowStyle.setOnClickListener {
            val view = layoutInflater.inflate(R.layout.dialog_title_style, null)
            val radioGroup = view.findViewById<RadioGroup>(R.id.title_style_radio_group)
            val customTitleContainer = view.findViewById<LinearLayout>(R.id.custom_title_container)
            val customTitleInput = view.findViewById<TextInputEditText>(R.id.custom_title_input)
            val titlePreview = view.findViewById<TextView>(R.id.dialog_title_preview)

            val helperf = view.findViewById<TextView>(R.id.helper_f)
            val helperF = view.findViewById<TextView>(R.id.helper_F)
            val helpert = view.findViewById<TextView>(R.id.helper_t)
            val helperT = view.findViewById<TextView>(R.id.helper_T)
            val helperC = view.findViewById<TextView>(R.id.helper_C)

            val calendar = Calendar.getInstance()
            val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            val isReversed = if (toStationCode.isNotEmpty()) {
                WidgetUtils.isTimeReversed(currentMinutes, startTimeNormal, startTimeReverse)
            } else {
                false
            }
            val displayFrom = if (isReversed) toStationCode else fromStationCode
            val displayTo = if (isReversed) fromStationCode else toStationCode

            val valf = displayFrom.uppercase()
            val valF = StationRepository.getStationName(this, displayFrom)
            val valt = displayTo.uppercase()
            val valT = if (displayTo.isNotEmpty()) StationRepository.getStationName(this, displayTo) else ""
            val valC = if (isReversed) "home" else "to work"

            helperf.text = "\$f [$valf]"
            helperF.text = "\$F [$valF]"
            helpert.text = "\$t [$valt]"
            helperT.text = "\$T [$valT]"
            helperC.text = "\$C [$valC]"

            fun insertText(text: String) {
                val start = max(customTitleInput.selectionStart, 0)
                val end = max(customTitleInput.selectionEnd, 0)
                customTitleInput.text?.replace(min(start, end), max(start, end), text)
            }
            
            fun updatePreview() {
                val selectedId = radioGroup.checkedRadioButtonId
                val style = when (selectedId) {
                    R.id.radio_short -> "SHORT"
                    R.id.radio_long -> "LONG"
                    R.id.radio_custom -> "CUSTOM"
                    else -> selectedTitleStyle
                }
                val customText = customTitleInput.text.toString()

                titlePreview.text = WidgetUtils.calculateDisplayTitle(
                    this,
                    style,
                    if (customText.isEmpty()) getString(R.string.default_from_only_title) else customText,
                    displayFrom,
                    displayTo,
                    fromStationCode
                )
            }

            helperf.setOnClickListener { insertText("\$f") }
            helperF.setOnClickListener { insertText("\$F") }
            helpert.setOnClickListener { insertText("\$t") }
            helperT.setOnClickListener { insertText("\$T") }
            helperC.setOnClickListener { insertText("\$C") }
            
            customTitleInput.setText(customTitleText)
            
            when(selectedTitleStyle) {
                "SHORT" -> radioGroup.check(R.id.radio_short)
                "LONG" -> radioGroup.check(R.id.radio_long)
                "CUSTOM" -> radioGroup.check(R.id.radio_custom)
            }
            
            customTitleContainer.visibility = if (selectedTitleStyle == "CUSTOM") View.VISIBLE else View.GONE
            
            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                customTitleContainer.visibility = if (checkedId == R.id.radio_custom) View.VISIBLE else View.GONE
                updatePreview()
            }
            
            customTitleInput.addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    updatePreview()
                }
            })
            
            updatePreview()
            
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.heading_format)
                .setView(view)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val checkedId = radioGroup.checkedRadioButtonId
                    val newStyle = when(checkedId) {
                        R.id.radio_short -> "SHORT"
                        R.id.radio_long -> "LONG"
                        R.id.radio_custom -> "CUSTOM"
                        else -> "SHORT"
                    }
                    
                    if (newStyle != selectedTitleStyle) {
                        selectedTitleStyle = newStyle
                        userChangedTitleStyle = true
                        triggerUpdate()
                    }
                    
                    if (newStyle == "CUSTOM") {
                        val newTitle = customTitleInput.text.toString()
                        if (newTitle.isNotEmpty()) {
                            customTitleText = newTitle
                            triggerUpdate()
                        } else {
                            customTitleText = getString(R.string.default_from_only_title)
                            triggerUpdate()
                        }
                    }
                    updateLayoutSummaries()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
                
            customTitleInput.post {
                 customTitleInput.selectAll()
            }
        }
        
        rowFontStyle.setOnClickListener {
             val items = arrayOf(getString(R.string.font_style_system), getString(R.string.font_style_retro))
             val values = arrayOf("SYSTEM", "RETRO")
             val selectedIndex = values.indexOf(selectedFontStyle).coerceAtLeast(0)
             
             val adapter = CheckedItemAdapter(this, items, selectedIndex)
             
             val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.font_style_label)
                .setAdapter(adapter) { dialog, which ->
                    selectedFontStyle = values[which]
                    updateLayoutSummaries()
                    triggerUpdate()
                    dialog.dismiss()
                }
                .create()
                
             dialog.show()
        }
        
        rowAlignment.setOnClickListener {
             val items = arrayOf(getString(R.string.alignment_left), getString(R.string.alignment_center), getString(R.string.alignment_right))
             val values = arrayOf("START", "CENTER", "END")
             val selectedIndex = values.indexOf(selectedAlignment).coerceAtLeast(0)
             
             val adapter = CheckedItemAdapter(this, items, selectedIndex)
             
             val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_alignment)
                .setAdapter(adapter) { dialog, which ->
                    selectedAlignment = values[which]
                    updateLayoutSummaries()
                    triggerUpdate()
                    dialog.dismiss()
                }
                .create()
                
             dialog.show()
        }
        
        // Inline slider listener
        sliderFontSize.addOnChangeListener { _, value, _ ->
            selectedFontSize = value.toInt()
            updateLayoutSummaries()
            triggerUpdate()
        }
        
        rowStationStops.setOnClickListener {
            val items = arrayOf(
                getString(R.string.stops_first),
                getString(R.string.stops_all),
                getString(R.string.stops_hidden)
            )
            val values = arrayOf("FIRST", "ALL", "NONE")
            val selectedIndex = values.indexOf(selectedStationStopsMode).coerceAtLeast(0)
            
            val adapter = CheckedItemAdapter(this, items, selectedIndex)
            
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.show_stops)
                .setAdapter(adapter) { dialog, which ->
                    selectedStationStopsMode = values[which]
                    updateLayoutSummaries()
                    triggerUpdate()
                    dialog.dismiss()
                }
                .create()
            
            dialog.show()
        }

        rowDivider.setOnClickListener {
            switchShowDivider.toggle()
        }
        
        switchShowDivider.setOnCheckedChangeListener { _, isChecked ->
            showDivider = isChecked
            triggerUpdate()
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
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val verticalMargin = (8 * resources.displayMetrics.density).toInt()
            params.topMargin = verticalMargin
            params.bottomMargin = verticalMargin
            switch.layoutParams = params
            
            container.addView(switch)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.header_items_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                updateLayoutSummaries()
                triggerUpdate()
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
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val verticalMargin = (8 * resources.displayMetrics.density).toInt()
            params.topMargin = verticalMargin
            params.bottomMargin = verticalMargin
            switch.layoutParams = params

            container.addView(switch)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.footer_items_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                updateLayoutSummaries()
                triggerUpdate()
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
        
        switchMaxJourneyDuration.setOnCheckedChangeListener { _, isChecked ->
            enableJourneyDurationFilter = isChecked
            val visibility = if (isChecked) View.VISIBLE else View.GONE
            summaryMaxJourneyDuration.visibility = visibility
            sliderMaxJourneyDuration.visibility = visibility
            triggerUpdate()
        }
        
        sliderMaxJourneyDuration.addOnChangeListener { _, value, _ ->
            maxJourneyDuration = value.toInt()
            updateAdvancedSummaries()
            triggerUpdate()
        }

        rowGlobalSettings.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun updateAdvancedSummaries() {
        summaryOffset.text = getString(R.string.offset_summary_format, selectedOffset)
        summaryDepartureCount.text = selectedDepartureCount.toString()
        summaryMaxJourneyDuration.text = getString(R.string.journey_duration_format, maxJourneyDuration)
    }

    private fun updateLayoutSummaries() {
        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val isReversed = if (toStationCode.isNotEmpty()) {
            WidgetUtils.isTimeReversed(currentMinutes, startTimeNormal, startTimeReverse)
        } else {
            false
        }

        val displayFrom = if (isReversed) toStationCode else fromStationCode
        val displayTo = if (isReversed) fromStationCode else toStationCode

        summaryStyle.text = WidgetUtils.calculateDisplayTitle(
            this,
            selectedTitleStyle,
            if (customTitleText.isEmpty()) getString(R.string.default_from_only_title) else customTitleText,
            displayFrom,
            displayTo,
            fromStationCode
        )
        
        // rowCustomTitle.visibility is handled in onCreate (GONE)
        
        summaryAlignment.text = when (selectedAlignment) {
            "START" -> getString(R.string.alignment_left)
            "CENTER" -> getString(R.string.alignment_center)
            "END" -> getString(R.string.alignment_right)
            else -> getString(R.string.alignment_left)
        }
        
        summaryFontSize.text = when (selectedFontSize) {
            0 -> getString(R.string.font_size_tiny)
            1 -> getString(R.string.font_size_extra_small)
            2 -> getString(R.string.font_size_small)
            3 -> getString(R.string.font_size_regular)
            4 -> getString(R.string.font_size_large)
            5 -> getString(R.string.font_size_extra_large)
            6 -> getString(R.string.font_size_massive)
            else -> getString(R.string.font_size_regular)
        }
        
        summaryStationStops.text = when (selectedStationStopsMode) {
            "FIRST" -> getString(R.string.stops_first)
            "ALL" -> getString(R.string.stops_all)
            "NONE" -> getString(R.string.stops_hidden)
            else -> getString(R.string.stops_first)
        }
        
        summaryFontStyle.text = when(selectedFontStyle) {
            "SYSTEM" -> getString(R.string.font_style_system)
            "RETRO" -> getString(R.string.font_style_retro)
            else -> getString(R.string.font_style_system)
        }
        
        if (selectedFontStyle == "RETRO") {
            try {
                 val typeface = ResourcesCompat.getFont(this, R.font.pixeloid_sans)
                 summaryFontStyle.typeface = typeface
            } catch (e: Exception) {
                 // Fallback
                 summaryFontStyle.typeface = Typeface.DEFAULT
            }
        } else {
            summaryFontStyle.typeface = Typeface.DEFAULT
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

    private fun updateTabVisibility(position: Int) {
        val rootView = findViewById<View>(R.id.config_root_layout)
    
        routeContent.visibility = if (position == 0) View.VISIBLE else View.GONE
        layoutContent.visibility = if (position == 1) View.VISIBLE else View.GONE
        colorsContent.visibility = if (position == 2) View.VISIBLE else View.GONE
        advancedContent.visibility = if (position == 3) View.VISIBLE else View.GONE
    
        rootView.post {
            val slidingSheet = findViewById<View>(R.id.sliding_sheet_container)
            // Safety check
            if (slidingSheet == null || slidingSheet.width == 0 || slidingSheet.height == 0) return@post
            
            val availableHeight = slidingSheet.height

            // Measure the sheet content
            val widthSpec = View.MeasureSpec.makeMeasureSpec(slidingSheet.width, View.MeasureSpec.EXACTLY)
            // Use UNSPECIFIED to allow it to measure full desired height
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            slidingSheet.measure(widthSpec, heightSpec)
            val contentHeight = slidingSheet.measuredHeight

            // Calculate target translation (bottom aligned to the available space)
            // If content is smaller than available space, we translate down.
            var targetTranslationY = (availableHeight - contentHeight).toFloat()
            if (targetTranslationY < 0f) targetTranslationY = 0f

            val animator = ObjectAnimator.ofFloat(slidingSheet, "translationY", targetTranslationY)
            animator.duration = 250
            animator.start()
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
    }

    private fun validateInputs() {
        val fromValid = isValidStation(fromStationCode)
        val toValid = toStationCode.trim().isEmpty() || isValidStation(toStationCode)

        addButton.isEnabled = fromValid && toValid

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
        val textColor = if (isTextSystemColor) {
            val typedValue = TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
            typedValue.data
        } else currentTextColor
        previewTextColor.setBackgroundColor(textColor)
        previewTextColorCheckerboard.visibility = if (Color.alpha(textColor) < 255) View.VISIBLE else View.GONE


        summaryBackgroundColor.text = if (isBackgroundSystemColor) getString(R.string.color_source_theme) else getString(R.string.color_source_custom)
        val bgColor = if (isBackgroundSystemColor) {
            val typedValue = TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHighest, typedValue, true)
            typedValue.data
        } else currentBackgroundColor
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
        
        val dialogPreviewCheckerboard: View = dialogView.findViewById(R.id.dialog_preview_checkerboard)
        val dialogPreviewColor: View = dialogView.findViewById(R.id.dialog_preview_color)

        var isSystemColor = if (mode == ColorMode.TEXT) isTextSystemColor else isBackgroundSystemColor
        var currentColor = if (mode == ColorMode.TEXT) currentTextColor else currentBackgroundColor
        var dialogIsUpdatingColor = false

        fun updateDialogPreviews(color: Int) {
            val hex = String.format("#%08X", color)
            if (textHexInput.text.toString() != hex) {
                 textHexInput.setText(hex)
            }
            dialogPreviewColor.setBackgroundColor(color)
            dialogPreviewCheckerboard.visibility = if (Color.alpha(color) < 255) View.VISIBLE else View.GONE
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

            textHueSlider.value = hue.roundToInt().toFloat()
            textSaturationSlider.value = (saturation * 100f).roundToInt().toFloat()
            textValueSlider.value = (lightness * 100f).roundToInt().toFloat()
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
        dialogPreviewCheckerboard.background = createCheckerboardDrawable(0f) // Square checkerboard

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
        
        textLightnessValueLabel.setOnClickListener {
            textValueSlider.value = 50f
        }

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
                triggerUpdate()
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
