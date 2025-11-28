package net.bonstio.traintimes

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.appcompat.view.ContextThemeWrapper
import java.util.Calendar

/**
 * Shared utility functions and data classes for the widget.
 */

data class WidgetStyling(
    val textColor: Int,
    val bgColor: Int,
    val transparency: Int,
    val useSystemTextColor: Boolean,
    val useSystemBgColor: Boolean
)

object WidgetUtils {

    fun resolveWidgetStyling(context: Context, config: WidgetConfiguration): WidgetStyling {
        val textColor = if (config.useSystemTextColor) getThemeColor(context, com.google.android.material.R.attr.colorOnSurface) else config.textColor
        val widgetBgColor = if (config.useSystemBgColor) getThemeColor(context, com.google.android.material.R.attr.colorSurfaceContainerHighest) else config.bgColor
        val transparency = if (config.useSystemBgColor) 255 else config.transparency
        return WidgetStyling(textColor, widgetBgColor, transparency, config.useSystemTextColor, config.useSystemBgColor)
    }

    fun getThemeColor(context: Context, attr: Int): Int {
        // Explicitly use a configuration context to ensure the correct resources (Day/Night) are used.
        // This fixes an issue where the Receiver context might have stale or incorrect Day/Night configuration.
        val configuration = context.resources.configuration
        val targetContext = context.createConfigurationContext(configuration)
        val wrapper = ContextThemeWrapper(targetContext, R.style.Theme_TrainTimes)
        val typedValue = TypedValue()
        wrapper.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    fun determineDirection(config: WidgetConfiguration): Pair<String, String> {
        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val isReversed = if (config.toStation.isNotEmpty()) isTimeReversed(currentMinutes, config.startTimeNormal, config.startTimeReverse) else false
        val fromStation = if (isReversed) config.toStation else config.fromStation
        val toStation = if (isReversed) config.fromStation else config.toStation
        return Pair(fromStation, toStation)
    }

    private fun isTimeReversed(currentMinutes: Int, startNormal: Int, startReverse: Int): Boolean {
        if (startNormal == -1 || startReverse == -1) return false
        return if (startNormal < startReverse) {
            !(currentMinutes >= startNormal && currentMinutes < startReverse)
        } else if (startNormal > startReverse) {
            currentMinutes >= startReverse && currentMinutes < startNormal
        } else false
    }

    fun isDepartureInPast(service: TrainService, now: Calendar): Boolean {
        val timeToParse = if (service.status.contains("Exp")) {
            service.status.split(" ")[1]
        } else if (service.status != "On time" && service.status.matches(Regex("\\d{2}:\\d{2}"))) {
            service.status
        } else {
            service.std
        }

        return try {
            val parts = timeToParse.split(":")
            val departureHour = parts[0].toInt()
            val departureMinute = parts[1].toInt()
            val departureTotalMinutes = departureHour * 60 + departureMinute

            val currentTotalMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            departureTotalMinutes < currentTotalMinutes
        } catch (e: Exception) {
            false
        }
    }

    fun getTitleSize(index: Int): Float {
        return when (index) {
            0 -> 14f
            1 -> 16f
            3 -> 20f
            4 -> 22f
            else -> 22f
        }
    }

    fun getBodySize(index: Int): Float {
        return when (index) {
            0 -> 12f
            1 -> 14f
            3 -> 18f
            4 -> 20f
            else -> 16f
        }
    }

    fun calculateDisplayTitle(context: Context, titleStyle: String, customTitle: String, fromStation: String, toStation: String): String {
        return when (titleStyle) {
            "SHORT" -> if (toStation.isNotEmpty()) "${fromStation.uppercase()} -> ${toStation.uppercase()}" else fromStation.uppercase()
            "CUSTOM" -> {
                var t = customTitle.replace("\$f", fromStation.uppercase()).replace("\$t", toStation.uppercase())
                if (t.contains("\$F") || t.contains("\$T")) {
                    val fromName = StationRepository.getStationName(context, fromStation)
                    val toName = if (toStation.isNotEmpty()) StationRepository.getStationName(context, toStation) else ""
                    t = t.replace("\$F", fromName).replace("\$T", toName)
                }
                t
            }
            else -> {
                val fromName = StationRepository.getStationName(context, fromStation)
                if (toStation.isNotEmpty()) "$fromName -> ${StationRepository.getStationName(context, toStation)}" else fromName
            }
        }
    }
}
