package me.rerere.zeroagent.workflow.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.zeroagent.workflow.model.ConditionSpec
import me.rerere.zeroagent.workflow.model.TriggerSpec
import me.rerere.zeroagent.workflow.model.WorkflowAction
import me.rerere.zeroagent.workflow.model.WorkflowDefinition
import me.rerere.zeroagent.workflow.model.WorkflowJson
import me.rerere.zeroagent.workflow.tools.WorkflowApprovalText.*

/**
 * Phase 12 — human-readable approval prompt renderer for `workflow_create` /
 * `workflow_update` (in-app approval dialog).
 *
 * Output template (matches spec §"Approval prompt rendering"):
 * ```
 * Create workflow "<name>"
 *
 * When: <trigger summary>
 * Only if: <comma-separated condition summaries, or "always">
 * Do:
 *   1. <action 1 summary>
 *   2. <action 2 summary>
 *   ...
 *
 * Cooldown: <X seconds, or "none">
 * Daily cap: <N runs, or "unlimited">
 * ```
 *
 */
object WorkflowApprovalRenderer {

    /** True if the tool is one of ours and should use this renderer (not the JSON dump). */
    fun isWorkflowTool(toolName: String): Boolean = toolName in WORKFLOW_TOOL_NAMES

    /** Plain-text rendering for the approval card; default English keeps non-Android callers stable. */
    fun renderPlain(
        toolName: String,
        argsJson: String,
        labels: WorkflowApprovalLabels = WorkflowApprovalLabels.ENGLISH,
    ): String {
        val heading = when (toolName) {
            "workflow_create" -> CREATE
            "workflow_update" -> UPDATE
            "workflow_delete" -> return renderDelete(argsJson, labels)
            "workflow_set_enabled" -> return renderSetEnabled(argsJson, labels)
            "workflow_run" -> return renderRunNow(argsJson, labels)
            else -> return argsJson
        }
        val def = parseDefinition(argsJson) ?: return argsJson
        val conditions = if (def.conditions.isEmpty()) labels.text(ALWAYS)
            else def.conditions.joinToString(", ") { conditionSummary(it, labels) }
        val cooldown = if (def.cooldownSeconds == 0) labels.text(NONE) else labels.text(SECONDS, def.cooldownSeconds)
        val dailyCap = def.maxRunsPerDay?.let { labels.text(RUNS, it) } ?: labels.text(UNLIMITED)
        return buildString {
            appendLine(labels.text(heading, def.name))
            appendLine()
            appendLine(labels.text(WHEN, triggerSummary(def.trigger, labels)))
            appendLine(labels.text(ONLY_IF, conditions))
            appendLine(labels.text(DO))
            def.actions.forEachIndexed { index, action ->
                appendLine("  ${index + 1}. ${actionSummary(action, labels)}")
            }
            appendLine()
            appendLine(labels.text(COOLDOWN, cooldown))
            appendLine(labels.text(DAILY_CAP, dailyCap))
        }.trimEnd()
    }

    private fun parseDefinition(argsJson: String): WorkflowDefinition? {
        val obj = runCatching { Json.parseToJsonElement(argsJson) as? JsonObject }.getOrNull() ?: return null
        val defStr = (obj["definition"] as? JsonObject)?.toString() ?: argsJson
        return WorkflowJson.parseStored(defStr)
    }

    internal fun triggerSummary(t: TriggerSpec, labels: WorkflowApprovalLabels = WorkflowApprovalLabels.ENGLISH): String = when (t) {
        is TriggerSpec.TimeCron -> when {
            !t.timeOfDay.isNullOrBlank() -> labels.text(AT_TIME, t.timeOfDay,
                if (t.daysOfWeek.isEmpty()) labels.text(DAILY) else labels.text(DAYS, t.daysOfWeek.joinToString(",")))
            !t.cron.isNullOrBlank() -> labels.text(SCHEDULE_CRON, t.cron)
            else -> labels.text(SCHEDULE)
        }
        is TriggerSpec.WifiConnected -> t.ssid?.let { labels.text(WIFI_CONNECTS_TO, it) } ?: labels.text(WIFI_CONNECTS)
        is TriggerSpec.WifiDisconnected -> t.ssid?.let { labels.text(WIFI_DISCONNECTS_FROM, it) } ?: labels.text(WIFI_DISCONNECTS)
        is TriggerSpec.BluetoothDeviceConnected -> labels.text(BLUETOOTH_CONNECTS) + t.deviceAddress?.let { " ($it)" }.orEmpty()
        is TriggerSpec.BluetoothDeviceDisconnected -> labels.text(BLUETOOTH_DISCONNECTS) + t.deviceAddress?.let { " ($it)" }.orEmpty()
        is TriggerSpec.HeadphonesPlugged -> labels.text(HEADPHONES_IN)
        is TriggerSpec.HeadphonesUnplugged -> labels.text(HEADPHONES_OUT)
        is TriggerSpec.PowerConnected -> labels.text(POWER_IN)
        is TriggerSpec.PowerDisconnected -> labels.text(POWER_OUT)
        is TriggerSpec.BatteryBelow -> labels.text(BATTERY_DROPS, t.thresholdPercent)
        is TriggerSpec.BatteryAbove -> labels.text(BATTERY_RISES, t.thresholdPercent)
        is TriggerSpec.GeofenceEnter -> labels.text(ENTER, t.label ?: labels.text(PLACE, t.lat, t.lng, t.radiusM))
        is TriggerSpec.GeofenceExit -> labels.text(LEAVE, t.label ?: labels.text(PLACE, t.lat, t.lng, t.radiusM))
        is TriggerSpec.AppLaunched -> labels.text(APP_LAUNCHED, t.packageName)
        is TriggerSpec.AppClosed -> labels.text(APP_CLOSED, t.packageName)
        is TriggerSpec.NotificationReceived -> {
            val parts = mutableListOf<String>()
            t.packageName?.let { parts += labels.text(FROM, it) }
            t.titleContains?.let { parts += labels.text(TITLE_CONTAINS, it) }
            t.textContains?.let { parts += labels.text(TEXT_CONTAINS, it) }
            t.titleMatches?.let { parts += labels.text(TITLE_MATCHES, it) }
            t.textMatches?.let { parts += labels.text(TEXT_MATCHES, it) }
            val base = labels.text(NOTIFICATION)
            if (parts.isEmpty()) base else labels.text(NOTIFICATION_FILTERS, base, parts.joinToString("; "))
        }
        is TriggerSpec.BootCompleted -> labels.text(BOOT)
        is TriggerSpec.ScreenOn -> labels.text(SCREEN_ON)
        is TriggerSpec.ScreenOff -> labels.text(SCREEN_OFF)
        is TriggerSpec.Manual -> labels.text(MANUAL)
    }

    internal fun conditionSummary(c: ConditionSpec, labels: WorkflowApprovalLabels = WorkflowApprovalLabels.ENGLISH): String {
        val base = when (c) {
            is ConditionSpec.TimeBetween -> labels.text(TIME_BETWEEN, c.start, c.end)
            is ConditionSpec.TimeAfterSunset -> labels.text(AFTER_SUNSET) + if (c.offsetMinutes != 0) " " + labels.text(OFFSET, c.offsetMinutes) else ""
            is ConditionSpec.TimeBeforeSunrise -> labels.text(BEFORE_SUNRISE) + if (c.offsetMinutes != 0) " " + labels.text(OFFSET, c.offsetMinutes) else ""
            is ConditionSpec.DayOfWeekIn -> labels.text(DAYS, c.days.joinToString(","))
            is ConditionSpec.WifiSsidIs -> labels.text(WIFI_IS, c.ssid)
            is ConditionSpec.WifiSsidIn -> labels.text(WIFI_IN, c.ssids.joinToString(","))
            is ConditionSpec.BatteryAbove -> labels.text(BATTERY_ABOVE, c.percent)
            is ConditionSpec.BatteryBelow -> labels.text(BATTERY_BELOW, c.percent)
            is ConditionSpec.IsCharging -> labels.text(CHARGING)
            is ConditionSpec.IsNotCharging -> labels.text(NOT_CHARGING)
            is ConditionSpec.ForegroundAppIs -> labels.text(FOREGROUND, c.packageName)
            is ConditionSpec.ForegroundAppIn -> labels.text(FOREGROUND_MATCH, c.packageNames.size)
            is ConditionSpec.ScreenIsOn -> labels.text(COND_SCREEN_ON)
            is ConditionSpec.ScreenIsOff -> labels.text(COND_SCREEN_OFF)
        }
        return if (c.invert) labels.text(NOT, base) else base
    }

    private fun actionSummary(action: WorkflowAction, labels: WorkflowApprovalLabels): String =
        labels.text(ACTION, action.tool, actionArgsHint(action))

    /**
     * Truncate args at ~80 chars per spec. Best-effort `key=value` rendering. Values whose
     * key matches the secret-redaction list (token, password, private_key, etc.) are masked
     * with `***` regardless of length — otherwise an LLM-authored workflow whose action is
     * `save_ssh_host({password: "..."})` would echo
     * the secret into the user's chat history (in-app approval card).
     */
    private fun actionArgsHint(action: WorkflowAction): String {
        if (action.args.isEmpty()) return ""
        val pairs = action.args.entries.joinToString(", ") { (k, v) ->
            val str = if (isSensitiveKey(k)) {
                "***"
            } else when (v) {
                is JsonPrimitive -> v.contentOrNull?.take(40).orEmpty()
                else -> v.toString().take(40)
            }
            "$k=\"$str\""
        }
        return pairs.take(80) + if (pairs.length > 80) "…" else ""
    }

    /**
     * Match a key name against the redaction list (case-insensitive, snake_case AND
     * camelCase aware). Mirrors the redaction model already used by McpApprovalRenderer
     * for header values.
     */
    private fun isSensitiveKey(key: String): Boolean {
        val lower = key.lowercase().replace("-", "_").replace(" ", "_")
        return SENSITIVE_KEY_PARTS.any { part -> lower == part || lower.contains(part) }
    }

    private val SENSITIVE_KEY_PARTS = setOf(
        "token", "password", "passphrase", "private_key", "privatekey",
        "secret", "api_key", "apikey", "authorization", "auth_token", "access_token",
        "client_secret", "credential", "credentials",
    )

    private fun renderDelete(argsJson: String, labels: WorkflowApprovalLabels): String {
        val id = runCatching {
            (Json.parseToJsonElement(argsJson) as? JsonObject)
                ?.get("id")?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: "?"
        return labels.text(DELETE, id)
    }

    private fun renderSetEnabled(argsJson: String, labels: WorkflowApprovalLabels): String {
        val obj = runCatching { Json.parseToJsonElement(argsJson) as? JsonObject }.getOrNull()
        val id = obj?.get("id")?.jsonPrimitive?.contentOrNull ?: "?"
        val enabled = obj?.get("enabled")?.jsonPrimitive?.contentOrNull
        return labels.text(if (enabled == "true") ENABLE else DISABLE, id)
    }

    private fun renderRunNow(argsJson: String, labels: WorkflowApprovalLabels): String {
        val id = runCatching {
            (Json.parseToJsonElement(argsJson) as? JsonObject)
                ?.get("id")?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: "?"
        return labels.text(RUN_NOW, id)
    }

}
