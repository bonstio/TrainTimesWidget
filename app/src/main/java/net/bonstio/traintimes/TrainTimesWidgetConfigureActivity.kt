package net.bonstio.traintimes

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
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView
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
    private lateinit var fromStationEditText: MaterialAutoCompleteTextView
    private lateinit var toStationEditText: MaterialAutoCompleteTextView
    private lateinit var titleEditText: TextInputEditText
    private lateinit var widgetTitleLayout: TextInputLayout
    private lateinit var alignmentGroup: RadioGroup
    private lateinit var addButton: Button
    private lateinit var cancelButton: Button
    private lateinit var titleStyleGroup: RadioGroup
    private lateinit var showIconCheckbox: CheckBox
    private lateinit var showStopsCheckbox: CheckBox
    private lateinit var timeOffsetEditText: TextInputEditText
    private lateinit var departureCountEditText: TextInputEditText
    private lateinit var fontSizeSlider: Slider

    private lateinit var timeNormalEditText: TextInputEditText
    private lateinit var timeReverseEditText: TextInputEditText

    // Color Tab Views
    private lateinit var colorSourceGroup: RadioGroup
    private lateinit var customColorControls: View

    private lateinit var textHueSlider: Slider
    private lateinit var textHueValueLabel: TextView
    private lateinit var textSaturationSlider: Slider
    private lateinit var textSaturationValueLabel: TextView
    private lateinit var textValueSlider: Slider
    private lateinit var textLightnessValueLabel: TextView
    private lateinit var textAlphaSlider: Slider
    private lateinit var textAlphaValueLabel: TextView
    private lateinit var textHexInput: TextInputEditText

    private lateinit var textHueBackground: View
    private lateinit var textSaturationBackground: View
    private lateinit var textValueBackground: View
    private lateinit var textAlphaBackground: View
    private lateinit var textAlphaCheckerboard: View

    // Color Mode Views
    private lateinit var colorModeTextCard: MaterialCardView
    private lateinit var colorModeTextPreview: View
    private lateinit var colorModeTextPreviewCheckerboard: View

    private lateinit var colorModeBackgroundCard: MaterialCardView
    private lateinit var colorModeBackgroundPreview: View
    private lateinit var colorModeBackgroundPreviewCheckerboard: View

    private lateinit var tabLayout: TabLayout
    private lateinit var routeContent: View
    private lateinit var layoutContent: View
    private lateinit var colorsContent: View
    private lateinit var advancedContent: View

    private var startTimeNormal = 270 // 04:30
    private var startTimeReverse = 720 // 12:00

    private enum class ColorMode { TEXT, BACKGROUND }

    private var activeColorMode = ColorMode.TEXT

    private var currentTextColor = Color.WHITE
    private var currentBackgroundColor = Color.argb(128, 0, 0, 0)

    private var isTextSystemColor = true
    private var isBackgroundSystemColor = true

    private var isUpdatingColor = false

    private lateinit var stations: List<Station>

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

        fromStationEditText = findViewById(R.id.from_station)
        toStationEditText = findViewById(R.id.to_station)
        titleEditText = findViewById(R.id.widget_title)
        widgetTitleLayout = findViewById(R.id.widget_title_layout)
        alignmentGroup = findViewById(R.id.alignment_group)
        addButton = findViewById(R.id.add_button)
        cancelButton = findViewById(R.id.cancel_button)
        titleStyleGroup = findViewById(R.id.title_style_group)
        showIconCheckbox = findViewById(R.id.show_icon_checkbox)
        showStopsCheckbox = findViewById(R.id.show_stops_checkbox)
        timeOffsetEditText = findViewById(R.id.time_offset)
        departureCountEditText = findViewById(R.id.departure_count)
        fontSizeSlider = findViewById(R.id.font_size_slider)

        timeNormalEditText = findViewById(R.id.time_normal)
        timeReverseEditText = findViewById(R.id.time_reverse)

        val timeNormalLayout = findViewById<TextInputLayout>(R.id.time_normal_layout)
        val timeReverseLayout = findViewById<TextInputLayout>(R.id.time_reverse_layout)

        // Color Views
        colorSourceGroup = findViewById(R.id.color_source_group)
        customColorControls = findViewById(R.id.custom_color_controls)

        textHueSlider = findViewById(R.id.text_hue_slider)
        textHueValueLabel = findViewById(R.id.text_hue_value_label)
        textSaturationSlider = findViewById(R.id.text_saturation_slider)
        textSaturationValueLabel = findViewById(R.id.text_saturation_value_label)
        textValueSlider = findViewById(R.id.text_value_slider)
        textLightnessValueLabel = findViewById(R.id.text_value_value_label)
        textAlphaSlider = findViewById(R.id.text_alpha_slider)
        textAlphaValueLabel = findViewById(R.id.text_alpha_value_label)
        textHexInput = findViewById(R.id.text_hex_input)

        textHueBackground = findViewById(R.id.text_hue_background)
        textSaturationBackground = findViewById(R.id.text_saturation_background)
        textValueBackground = findViewById(R.id.text_value_background)
        textAlphaBackground = findViewById(R.id.text_alpha_background)
        textAlphaCheckerboard = findViewById(R.id.text_alpha_checkerboard)

        // Color Mode Views
        colorModeTextCard = findViewById(R.id.color_mode_text_card)
        colorModeTextPreview = findViewById(R.id.color_mode_text_preview)
        colorModeTextPreviewCheckerboard = findViewById(R.id.color_mode_text_preview_checkerboard)

        colorModeBackgroundCard = findViewById(R.id.color_mode_background_card)
        colorModeBackgroundPreview = findViewById(R.id.color_mode_background_preview)
        colorModeBackgroundPreviewCheckerboard =
            findViewById(R.id.color_mode_background_preview_checkerboard)

        tabLayout = findViewById(R.id.tabs)
        routeContent = findViewById(R.id.tab_route_content)
        layoutContent = findViewById(R.id.tab_layout_content)
        colorsContent = findViewById(R.id.tab_colors_content)
        advancedContent = findViewById(R.id.tab_advanced_content)

        stations = StationRepository.getStations(this)
        val adapter = StationAdapter(this, stations)
        fromStationEditText.setAdapter(adapter)
        toStationEditText.setAdapter(adapter)

        setupInputListeners(fromStationEditText)
        setupInputListeners(toStationEditText)
        setupValidation()

        titleStyleGroup.setOnCheckedChangeListener { _, checkedId ->
            val isCustom = (checkedId == R.id.style_custom)
            titleEditText.isEnabled = isCustom
            widgetTitleLayout.visibility = if (isCustom) View.VISIBLE else View.GONE
        }

        timeNormalEditText.setOnClickListener {
            showTimePicker(startTimeNormal) { minutes ->
                startTimeNormal = minutes
                updateTimeLabels()
            }
        }

        timeNormalEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) {
                    startTimeNormal = -1
                    timeNormalLayout.isEndIconVisible = false
                } else {
                    timeNormalLayout.isEndIconVisible = true
                }
            }
        })

        timeNormalLayout.setEndIconOnClickListener {
            timeNormalEditText.setText("")
        }

        timeReverseEditText.setOnClickListener {
            showTimePicker(startTimeReverse) { minutes ->
                startTimeReverse = minutes
                updateTimeLabels()
            }
        }

        timeReverseEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) {
                    startTimeReverse = -1
                    timeReverseLayout.isEndIconVisible = false
                } else {
                    timeReverseLayout.isEndIconVisible = true
                }
            }
        })

        timeReverseLayout.setEndIconOnClickListener {
            timeReverseEditText.setText("")
        }

        findViewById<ImageButton>(R.id.swap_times).setOnClickListener {
            val temp = startTimeNormal
            startTimeNormal = startTimeReverse
            startTimeReverse = temp
            updateTimeLabels()
        }

        setupColorListeners()

        // Setup checkerboards
        val density = resources.displayMetrics.density
        textAlphaCheckerboard.background = createCheckerboardDrawable(12f * density)
        colorModeTextPreviewCheckerboard.background = createCheckerboardDrawable(0f)
        colorModeBackgroundPreviewCheckerboard.background = createCheckerboardDrawable(0f)

        // Mode listeners
        colorModeTextCard.setOnClickListener { updateActiveMode(ColorMode.TEXT) }
        colorModeBackgroundCard.setOnClickListener { updateActiveMode(ColorMode.BACKGROUND) }

        colorSourceGroup.setOnCheckedChangeListener { _, checkedId ->
            val isSystem = (checkedId == R.id.color_source_system)
            if (activeColorMode == ColorMode.TEXT) {
                isTextSystemColor = isSystem
            } else {
                isBackgroundSystemColor = isSystem
            }
            updateColorControlsVisibility()
            updatePreviews()
        }

        cancelButton.setOnClickListener {
            finish()
        }

        addButton.setOnClickListener {
            val context = this@TrainTimesWidgetConfigureActivity

            val fromInput = fromStationEditText.text.toString()
            val toInput = toStationEditText.text.toString()

            val fromStation = extractStationCode(fromInput, stations)
            val toStation = extractStationCode(toInput, stations)

            val title = titleEditText.text.toString()

            val titleStyle = when (titleStyleGroup.checkedRadioButtonId) {
                R.id.style_short -> "SHORT"
                R.id.style_custom -> "CUSTOM"
                else -> "LONG"
            }

            val showIcon = showIconCheckbox.isChecked
            val showStops = showStopsCheckbox.isChecked
            val timeOffset = timeOffsetEditText.text.toString().toIntOrNull() ?: 0
            val departureCount = departureCountEditText.text.toString().toIntOrNull() ?: 4

            val alignment = when (alignmentGroup.checkedRadioButtonId) {
                R.id.alignment_center -> "CENTER"
                R.id.alignment_right -> "END"
                else -> "START"
            }

            val transparency = Color.alpha(currentBackgroundColor)
            val bgColor = Color.rgb(
                Color.red(currentBackgroundColor),
                Color.green(currentBackgroundColor),
                Color.blue(currentBackgroundColor)
            )

            val fontSize = fontSizeSlider.value.toInt()

            WidgetConfigurationStorage.saveConfiguration(
                context,
                appWidgetId,
                title,
                titleStyle,
                showIcon,
                showStops,
                fromStation,
                toStation,
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
                fontSize
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

        findViewById<ImageButton>(R.id.swap_stations).setOnClickListener {
            val from = fromStationEditText.text
            val to = toStationEditText.text
            fromStationEditText.text = to
            toStationEditText.text = from
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
            setStationText(fromStationEditText, existingConfig.fromStation)
            setStationText(toStationEditText, existingConfig.toStation)
            titleEditText.setText(existingConfig.title)

            when (existingConfig.titleStyle) {
                "SHORT" -> titleStyleGroup.check(R.id.style_short)
                "CUSTOM" -> titleStyleGroup.check(R.id.style_custom)
                else -> titleStyleGroup.check(R.id.style_long)
            }
            val isCustom = (existingConfig.titleStyle == "CUSTOM")
            titleEditText.isEnabled = isCustom
            widgetTitleLayout.visibility = if (isCustom) View.VISIBLE else View.GONE

            showIconCheckbox.isChecked = existingConfig.showIcon
            showStopsCheckbox.isChecked = existingConfig.showStops
            when (existingConfig.alignment) {
                "CENTER" -> alignmentGroup.check(R.id.alignment_center)
                "END" -> alignmentGroup.check(R.id.alignment_right)
                else -> alignmentGroup.check(R.id.alignment_left)
            }
            startTimeNormal = existingConfig.startTimeNormal
            startTimeReverse = existingConfig.startTimeReverse
            timeOffsetEditText.setText(existingConfig.timeOffset.toString())
            departureCountEditText.setText(existingConfig.departureCount.toString())

            currentTextColor = existingConfig.textColor
            currentBackgroundColor = ColorUtils.setAlphaComponent(
                existingConfig.bgColor,
                existingConfig.transparency
            )

            isTextSystemColor = existingConfig.useSystemTextColor
            isBackgroundSystemColor = existingConfig.useSystemBgColor
            fontSizeSlider.value = existingConfig.fontSize.toFloat()
        } else {
            alignmentGroup.check(R.id.alignment_left)
            titleStyleGroup.check(R.id.style_short)
            titleEditText.isEnabled = false
            widgetTitleLayout.visibility = View.GONE
            titleEditText.setText(R.string.default_custom_title)
            showIconCheckbox.isChecked = true
            showStopsCheckbox.isChecked = true
            timeOffsetEditText.setText("0")
            departureCountEditText.setText("4")
            currentTextColor = Color.WHITE
            currentBackgroundColor = Color.argb(128, 0, 0, 0)
            isTextSystemColor = true
            isBackgroundSystemColor = true
            fontSizeSlider.value = 1f
        }

        updateActiveMode(ColorMode.TEXT)
        updatePreviews()

        validateInputs()
        updateTimeLabels()
        updateTabVisibility(0)
    }

    private fun updateTabVisibility(position: Int) {
        routeContent.visibility = if (position == 0) View.VISIBLE else View.GONE
        layoutContent.visibility = if (position == 1) View.VISIBLE else View.GONE
        colorsContent.visibility = if (position == 2) View.VISIBLE else View.GONE
        advancedContent.visibility = if (position == 3) View.VISIBLE else View.GONE
    }

    private fun showTimePicker(minutes: Int, callback: (Int) -> Unit) {
        val safeMinutes = if (minutes == -1) 720 else minutes
        val hour = safeMinutes / 60
        val minute = safeMinutes % 60
        TimePickerDialog(this, { _, h, m ->
            callback(h * 60 + m)
        }, hour, minute, true).show()
    }

    private fun updateTimeLabels() {
        if (startTimeNormal == -1) {
            timeNormalEditText.setText("")
        } else {
            timeNormalEditText.setText(formatTime(startTimeNormal))
        }

        if (startTimeReverse == -1) {
            timeReverseEditText.setText("")
        } else {
            timeReverseEditText.setText(formatTime(startTimeReverse))
        }
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
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateInputs()
            }
        }
        fromStationEditText.addTextChangedListener(watcher)
        toStationEditText.addTextChangedListener(watcher)
        timeOffsetEditText.addTextChangedListener(watcher)
        departureCountEditText.addTextChangedListener(watcher)
    }

    private fun validateInputs() {
        val fromText = fromStationEditText.text.toString()
        val toText = toStationEditText.text.toString()
        val offsetText = timeOffsetEditText.text.toString()
        val departureCountText = departureCountEditText.text.toString()

        val fromValid = isValidStation(fromText)
        val toValid = isValidStation(toText)

        val offset = offsetText.toIntOrNull()
        val offsetValid = offset != null && offset >= -120 && offset <= 120

        val departureCount = departureCountText.toIntOrNull()
        val departureCountValid =
            departureCount != null && departureCount >= 1 && departureCount <= 100

        addButton.isEnabled = fromValid && toValid && offsetValid && departureCountValid
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

    private fun setupColorListeners() {
        val colorListener = Slider.OnChangeListener { _, _, _ ->
            if (isUpdatingColor) return@OnChangeListener
            isUpdatingColor = true
            updateFromSliders()
            isUpdatingColor = false
        }
        textHueSlider.addOnChangeListener(colorListener)
        textSaturationSlider.addOnChangeListener(colorListener)
        textValueSlider.addOnChangeListener(colorListener)
        textAlphaSlider.addOnChangeListener(colorListener)

        textHexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingColor) return
                isUpdatingColor = true
                updateFromHex(s.toString())
                isUpdatingColor = false
            }
        })
    }

    private fun updateActiveMode(mode: ColorMode) {
        activeColorMode = mode
        val strokeWidth = (2 * resources.displayMetrics.density).toInt()
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
        val primaryColor = typedValue.data

        if (mode == ColorMode.TEXT) {
            colorModeTextCard.strokeWidth = strokeWidth
            colorModeTextCard.strokeColor = primaryColor
            colorModeBackgroundCard.strokeWidth = 0
            updateSlidersFromColor(currentTextColor)
        } else {
            colorModeTextCard.strokeWidth = 0
            colorModeBackgroundCard.strokeWidth = strokeWidth
            colorModeBackgroundCard.strokeColor = primaryColor
            updateSlidersFromColor(currentBackgroundColor)
        }

        val isSystem = if (mode == ColorMode.TEXT) isTextSystemColor else isBackgroundSystemColor
        colorSourceGroup.check(if (isSystem) R.id.color_source_system else R.id.color_source_custom)
        updateColorControlsVisibility()
        updateColorPreview(true)
    }

    private fun updateColorControlsVisibility() {
        val isSystem =
            if (activeColorMode == ColorMode.TEXT) isTextSystemColor else isBackgroundSystemColor
        customColorControls.visibility = if (isSystem) View.GONE else View.VISIBLE
    }

    private fun updateFromSliders() {
        val hue = textHueSlider.value
        val saturation = textSaturationSlider.value / 100f
        val lightness = textValueSlider.value / 100f
        val alpha = textAlphaSlider.value.toInt()

        textHueValueLabel.text = hue.toInt().toString()
        textSaturationValueLabel.text = textSaturationSlider.value.toInt().toString()
        textLightnessValueLabel.text = textValueSlider.value.toInt().toString()
        textAlphaValueLabel.text = formatAlpha(alpha)

        val colorInt = ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness))
        val color = ColorUtils.setAlphaComponent(colorInt, alpha)

        if (activeColorMode == ColorMode.TEXT) {
            currentTextColor = color
        } else {
            currentBackgroundColor = color
        }

        updateSliderBackgrounds(hue, saturation, lightness, alpha)
        updatePreviews()
        updateColorPreview(true)
    }

    private fun updateFromHex(hex: String) {
        try {
            val color = Color.parseColor(hex)

            if (activeColorMode == ColorMode.TEXT) {
                currentTextColor = color
            } else {
                currentBackgroundColor = color
            }

            updateSlidersFromColor(color)
            updatePreviews()
            updateColorPreview(false)
        } catch (e: IllegalArgumentException) {
            // Invalid hex
        }
    }

    private fun updateSlidersFromColor(color: Int) {
        val hsl = floatArrayOf(0f, 0f, 0f)
        ColorUtils.colorToHSL(color, hsl)

        val hue = hsl[0].roundToInt().toFloat()
        val saturation = (hsl[1] * 100f).roundToInt().toFloat()
        val lightness = (hsl[2] * 100f).roundToInt().toFloat()
        val alpha = Color.alpha(color).toFloat()

        textHueSlider.value = hue
        textSaturationSlider.value = saturation
        textValueSlider.value = lightness
        textAlphaSlider.value = alpha

        textHueValueLabel.text = hue.toInt().toString()
        textSaturationValueLabel.text = saturation.toInt().toString()
        textLightnessValueLabel.text = lightness.toInt().toString()
        textAlphaValueLabel.text = formatAlpha(alpha.toInt())

        updateSliderBackgrounds(hue, saturation / 100f, lightness / 100f, alpha.toInt())
    }

    private fun updateSliderBackgrounds(
        hue: Float,
        saturation: Float,
        lightness: Float,
        alpha: Int
    ) {
        val cornerRadius = 12f * resources.displayMetrics.density

        val hueColors = IntArray(7)
        for (i in 0..6) {
            hueColors[i] = ColorUtils.HSLToColor(floatArrayOf(i * 60f, 1f, 0.5f))
        }
        val hueDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, hueColors)
        hueDrawable.cornerRadius = cornerRadius
        textHueBackground.background = hueDrawable

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
        val alphaColors = intArrayOf(
            Color.TRANSPARENT,
            baseColor
        )
        val alphaDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, alphaColors)
        alphaDrawable.cornerRadius = cornerRadius
        textAlphaBackground.background = alphaDrawable
    }

    private fun getSystemColor(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun updatePreviews() {
        val textColor =
            if (isTextSystemColor) getSystemColor(com.google.android.material.R.attr.colorOnSurface) else currentTextColor
        val bgColor =
            if (isBackgroundSystemColor) getSystemColor(com.google.android.material.R.attr.colorSurface) else currentBackgroundColor

        colorModeTextPreview.setBackgroundColor(textColor)
        colorModeBackgroundPreview.setBackgroundColor(bgColor)
    }

    private fun updateColorPreview(updateText: Boolean) {
        val color =
            if (activeColorMode == ColorMode.TEXT) currentTextColor else currentBackgroundColor
        if (updateText) {
            val hex = String.format("#%08X", color)
            textHexInput.setText(hex)
        }
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