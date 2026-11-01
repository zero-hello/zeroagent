package me.rerere.zeroagent.workflow.tools

import android.content.Context
import me.rerere.zeroagent.R

/** Uses the already-localized app Context supplied by the approval card. */
internal fun workflowApprovalLabels(context: Context) = WorkflowApprovalLabels { key, arguments ->
    val resource = when (key) {
        WorkflowApprovalText.CREATE -> R.string.zeroagent_language_workflow_create
        WorkflowApprovalText.UPDATE -> R.string.zeroagent_language_workflow_update
        WorkflowApprovalText.WHEN -> R.string.zeroagent_language_workflow_when
        WorkflowApprovalText.ONLY_IF -> R.string.zeroagent_language_workflow_only_if
        WorkflowApprovalText.DO -> R.string.zeroagent_language_workflow_do
        WorkflowApprovalText.COOLDOWN -> R.string.zeroagent_language_workflow_cooldown
        WorkflowApprovalText.DAILY_CAP -> R.string.zeroagent_language_workflow_daily_cap
        WorkflowApprovalText.ALWAYS -> R.string.zeroagent_language_workflow_always
        WorkflowApprovalText.NONE -> R.string.zeroagent_language_workflow_none
        WorkflowApprovalText.UNLIMITED -> R.string.zeroagent_language_workflow_unlimited
        WorkflowApprovalText.SECONDS -> R.string.zeroagent_language_workflow_seconds
        WorkflowApprovalText.RUNS -> R.string.zeroagent_language_workflow_runs
        WorkflowApprovalText.DAILY -> R.string.zeroagent_language_workflow_daily
        WorkflowApprovalText.DAYS -> R.string.zeroagent_language_workflow_days
        WorkflowApprovalText.AT_TIME -> R.string.zeroagent_language_workflow_at_time
        WorkflowApprovalText.SCHEDULE_CRON -> R.string.zeroagent_language_workflow_schedule_cron
        WorkflowApprovalText.SCHEDULE -> R.string.zeroagent_language_workflow_schedule
        WorkflowApprovalText.WIFI_CONNECTS -> R.string.zeroagent_language_workflow_wifi_connects
        WorkflowApprovalText.WIFI_CONNECTS_TO -> R.string.zeroagent_language_workflow_wifi_connects_to
        WorkflowApprovalText.WIFI_DISCONNECTS -> R.string.zeroagent_language_workflow_wifi_disconnects
        WorkflowApprovalText.WIFI_DISCONNECTS_FROM -> R.string.zeroagent_language_workflow_wifi_disconnects_from
        WorkflowApprovalText.BLUETOOTH_CONNECTS -> R.string.zeroagent_language_workflow_bluetooth_connects
        WorkflowApprovalText.BLUETOOTH_DISCONNECTS -> R.string.zeroagent_language_workflow_bluetooth_disconnects
        WorkflowApprovalText.HEADPHONES_IN -> R.string.zeroagent_language_workflow_headphones_in
        WorkflowApprovalText.HEADPHONES_OUT -> R.string.zeroagent_language_workflow_headphones_out
        WorkflowApprovalText.POWER_IN -> R.string.zeroagent_language_workflow_power_in
        WorkflowApprovalText.POWER_OUT -> R.string.zeroagent_language_workflow_power_out
        WorkflowApprovalText.BATTERY_DROPS -> R.string.zeroagent_language_workflow_battery_drops
        WorkflowApprovalText.BATTERY_RISES -> R.string.zeroagent_language_workflow_battery_rises
        WorkflowApprovalText.ENTER -> R.string.zeroagent_language_workflow_enter
        WorkflowApprovalText.LEAVE -> R.string.zeroagent_language_workflow_leave
        WorkflowApprovalText.PLACE -> R.string.zeroagent_language_workflow_place
        WorkflowApprovalText.APP_LAUNCHED -> R.string.zeroagent_language_workflow_app_launched
        WorkflowApprovalText.APP_CLOSED -> R.string.zeroagent_language_workflow_app_closed
        WorkflowApprovalText.NOTIFICATION -> R.string.zeroagent_language_workflow_notification
        WorkflowApprovalText.NOTIFICATION_FILTERS -> R.string.zeroagent_language_workflow_notification_filters
        WorkflowApprovalText.FROM -> R.string.zeroagent_language_workflow_from
        WorkflowApprovalText.TITLE_CONTAINS -> R.string.zeroagent_language_workflow_title_contains
        WorkflowApprovalText.TEXT_CONTAINS -> R.string.zeroagent_language_workflow_text_contains
        WorkflowApprovalText.TITLE_MATCHES -> R.string.zeroagent_language_workflow_title_matches
        WorkflowApprovalText.TEXT_MATCHES -> R.string.zeroagent_language_workflow_text_matches
        WorkflowApprovalText.BOOT -> R.string.zeroagent_language_workflow_boot
        WorkflowApprovalText.SCREEN_ON -> R.string.zeroagent_language_workflow_screen_on
        WorkflowApprovalText.SCREEN_OFF -> R.string.zeroagent_language_workflow_screen_off
        WorkflowApprovalText.MANUAL -> R.string.zeroagent_language_workflow_manual
        WorkflowApprovalText.TIME_BETWEEN -> R.string.zeroagent_language_workflow_time_between
        WorkflowApprovalText.AFTER_SUNSET -> R.string.zeroagent_language_workflow_after_sunset
        WorkflowApprovalText.BEFORE_SUNRISE -> R.string.zeroagent_language_workflow_before_sunrise
        WorkflowApprovalText.OFFSET -> R.string.zeroagent_language_workflow_offset
        WorkflowApprovalText.WIFI_IS -> R.string.zeroagent_language_workflow_wifi_is
        WorkflowApprovalText.WIFI_IN -> R.string.zeroagent_language_workflow_wifi_in
        WorkflowApprovalText.BATTERY_ABOVE -> R.string.zeroagent_language_workflow_battery_above
        WorkflowApprovalText.BATTERY_BELOW -> R.string.zeroagent_language_workflow_battery_below
        WorkflowApprovalText.CHARGING -> R.string.zeroagent_language_workflow_charging
        WorkflowApprovalText.NOT_CHARGING -> R.string.zeroagent_language_workflow_not_charging
        WorkflowApprovalText.FOREGROUND -> R.string.zeroagent_language_workflow_foreground
        WorkflowApprovalText.FOREGROUND_MATCH -> R.string.zeroagent_language_workflow_foreground_match
        WorkflowApprovalText.COND_SCREEN_ON -> R.string.zeroagent_language_workflow_cond_screen_on
        WorkflowApprovalText.COND_SCREEN_OFF -> R.string.zeroagent_language_workflow_cond_screen_off
        WorkflowApprovalText.NOT -> R.string.zeroagent_language_workflow_not
        WorkflowApprovalText.ACTION -> R.string.zeroagent_language_workflow_action
        WorkflowApprovalText.DELETE -> R.string.zeroagent_language_workflow_delete
        WorkflowApprovalText.ENABLE -> R.string.zeroagent_language_workflow_enable
        WorkflowApprovalText.DISABLE -> R.string.zeroagent_language_workflow_disable
        WorkflowApprovalText.RUN_NOW -> R.string.zeroagent_language_workflow_run_now
    }
    context.getString(resource, *arguments)
}
