package me.rerere.zeroagent.workflow.tools

import java.util.Locale

/** Only fixed UI phrases are translated; user content is always a formatting argument. */
enum class WorkflowApprovalText(val english: String) {
    CREATE("Create workflow \"%1\$s\""),
    UPDATE("Update workflow \"%1\$s\""),
    WHEN("When: %1\$s"),
    ONLY_IF("Only if: %1\$s"),
    DO("Do:"),
    COOLDOWN("Cooldown: %1\$s"),
    DAILY_CAP("Daily cap: %1\$s"),
    ALWAYS("always"),
    NONE("none"),
    UNLIMITED("unlimited"),
    SECONDS("%1\$ds"),
    RUNS("%1\$d runs"),
    DAILY("every day"),
    DAYS("on day(s) %1\$s"),
    AT_TIME("every %1\$s %2\$s"),
    SCHEDULE_CRON("schedule (%1\$s)"),
    SCHEDULE("schedule"),
    WIFI_CONNECTS("WiFi connects"),
    WIFI_CONNECTS_TO("WiFi connects to %1\$s"),
    WIFI_DISCONNECTS("WiFi disconnects"),
    WIFI_DISCONNECTS_FROM("WiFi disconnects from %1\$s"),
    BLUETOOTH_CONNECTS("Bluetooth device connects"),
    BLUETOOTH_DISCONNECTS("Bluetooth device disconnects"),
    HEADPHONES_IN("headphones plugged in"),
    HEADPHONES_OUT("headphones unplugged"),
    POWER_IN("power connected"),
    POWER_OUT("power disconnected"),
    BATTERY_DROPS("battery drops below %1\$d%%"),
    BATTERY_RISES("battery rises above %1\$d%%"),
    ENTER("you enter %1\$s"),
    LEAVE("you leave %1\$s"),
    PLACE("a place (%1\$s,%2\$s, %3\$sm)"),
    APP_LAUNCHED("%1\$s is launched"),
    APP_CLOSED("%1\$s is closed"),
    NOTIFICATION("a notification arrives"),
    NOTIFICATION_FILTERS("%1\$s (%2\$s)"),
    FROM("from %1\$s"),
    TITLE_CONTAINS("title contains '%1\$s'"),
    TEXT_CONTAINS("text contains '%1\$s'"),
    TITLE_MATCHES("title matches /%1\$s/"),
    TEXT_MATCHES("text matches /%1\$s/"),
    BOOT("device boots"),
    SCREEN_ON("screen turns on"),
    SCREEN_OFF("screen turns off"),
    MANUAL("you fire it manually (Run now)"),
    TIME_BETWEEN("between %1\$s and %2\$s"),
    AFTER_SUNSET("after sunset"),
    BEFORE_SUNRISE("before sunrise"),
    OFFSET("(%1\$dm offset)"),
    WIFI_IS("WiFi is %1\$s"),
    WIFI_IN("WiFi in %1\$s"),
    BATTERY_ABOVE("battery > %1\$d%%"),
    BATTERY_BELOW("battery < %1\$d%%"),
    CHARGING("charging"),
    NOT_CHARGING("not charging"),
    FOREGROUND("%1\$s is foreground"),
    FOREGROUND_MATCH("%1\$d-app foreground match"),
    COND_SCREEN_ON("screen on"),
    COND_SCREEN_OFF("screen off"),
    NOT("NOT (%1\$s)"),
    ACTION("%1\$s(%2\$s)"),
    DELETE("Delete workflow id=%1\$s"),
    ENABLE("Enable workflow id=%1\$s"),
    DISABLE("Disable workflow id=%1\$s"),
    RUN_NOW("Run workflow now id=%1\$s"),
    ;

    val resourceName: String get() = "zeroagent_language_workflow_" + name.lowercase(Locale.ROOT)
}

fun interface WorkflowApprovalLabels {
    fun text(key: WorkflowApprovalText, vararg arguments: Any): String

    companion object {
        val ENGLISH = WorkflowApprovalLabels { key, arguments ->
            String.format(Locale.ENGLISH, key.english, *arguments)
        }
    }
}
