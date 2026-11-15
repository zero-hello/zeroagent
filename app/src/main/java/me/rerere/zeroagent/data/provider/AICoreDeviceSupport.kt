package me.rerere.zeroagent.data.provider

import android.os.Build

/** A device-name gate only; ML Kit must still report that its model is available. */
fun supportsPixelAICore(): Boolean = supportsPixelAICoreDevice(Build.MANUFACTURER, Build.MODEL)

internal fun supportsPixelAICoreDevice(manufacturer: String?, model: String?): Boolean {
    if (!manufacturer.equals("Google", ignoreCase = true)) return false
    val generation = PIXEL_MODEL.matchEntire(model?.trim().orEmpty())?.groupValues?.getOrNull(1)
        ?.toIntOrNull() ?: return false
    return generation >= MIN_AICORE_PIXEL_GENERATION
}

// Match complete Pixel phone names, including the a-series, rather than arbitrary suffixes
// such as "Pixel 8 Tablet" or a vendor string merely containing "Pixel 8".
private val PIXEL_MODEL = Regex("Pixel ([1-9]\\d*)(?:a| Pro(?: XL| Fold)?)?", RegexOption.IGNORE_CASE)
private const val MIN_AICORE_PIXEL_GENERATION = 8
