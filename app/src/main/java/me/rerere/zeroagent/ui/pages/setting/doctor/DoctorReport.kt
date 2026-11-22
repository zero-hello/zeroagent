package me.rerere.zeroagent.ui.pages.setting.doctor

import android.content.Context
import me.rerere.zeroagent.R
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain-text formatter shared by the in-app Doctor screen ("Copy report" button) and the
 * diagnostic report. Categorised, grouped, with a consistent severity prefix so the
 * output is greppable for support flows.
 */
object DoctorReport {
    fun format(context: Context, checks: List<DoctorCheck>, header: String? = null): String {
        val localized = context.zeroAgentLocalizedContext()
        return format(
            checks = checks,
            header = header ?: localized.getString(R.string.doctor_report_title),
            generatedLabel = localized.getString(R.string.doctor_report_generated),
            summaryLabel = localized.getString(R.string.doctor_report_summary),
            severityName = { severity -> localized.getString(when (severity) {
                Severity.FAIL -> R.string.doctor_pill_failures
                Severity.WARN -> R.string.doctor_pill_warnings
                Severity.OK -> R.string.doctor_pill_ok
                Severity.INFO -> R.string.doctor_pill_info
            }) },
            categoryName = { localized.getString(it.displayNameRes) },
        )
    }

    fun format(
        checks: List<DoctorCheck>,
        header: String = "ZeroAgent — diagnostic report",
        generatedLabel: String = "Generated",
        summaryLabel: String = "Summary",
        severityName: (Severity) -> String = { it.name.lowercase() },
        categoryName: (DoctorCategory) -> String,
    ): String =
        buildString {
            appendLine(header)
            appendLine("$generatedLabel: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            val counts = mutableMapOf<Severity, Int>()
            checks.forEach { counts[it.severity] = (counts[it.severity] ?: 0) + 1 }
            val summary = listOf(Severity.FAIL, Severity.WARN, Severity.OK, Severity.INFO)
                .joinToString("  ") { "${severityName(it)}=${counts[it] ?: 0}" }
            appendLine("$summaryLabel: $summary")
            appendLine()
            DoctorCategory.entries.forEach { cat ->
                val rows = checks.filter { it.category == cat }
                if (rows.isEmpty()) return@forEach
                appendLine("## ${categoryName(cat)}")
                rows.forEach { r ->
                    val mark = when (r.severity) {
                        Severity.OK -> "[ok]   "
                        Severity.INFO -> "[info] "
                        Severity.WARN -> "[warn] "
                        Severity.FAIL -> "[fail] "
                    }
                    appendLine("  $mark ${r.label} — ${r.detail}")
                }
                appendLine()
            }
        }.trimEnd()
}
