package me.rerere.zeroagent.ui.pages.setting.components

import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale

/** Accept canonical pasted values and the app locale's decimal digits, never partial/grouped input. */
internal fun parseModelParameterNumber(text: String, locale: Locale): Double? {
    val value = text.trim()
    if (value.isEmpty()) return null
    value.toDoubleOrNull()?.let { return it.takeIf(Double::isFinite) }
    val format = NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false }
    val position = ParsePosition(0)
    val parsed = format.parse(value, position)?.toDouble() ?: return null
    return parsed.takeIf { it.isFinite() && position.index == value.length }
}

internal fun formatModelParameterNumber(text: String, locale: Locale): String = text.toDoubleOrNull()?.let {
    NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false; maximumFractionDigits = 8 }.format(it)
}.orEmpty()
