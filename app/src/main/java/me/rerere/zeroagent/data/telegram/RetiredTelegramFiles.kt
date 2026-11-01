package me.rerere.zeroagent.data.telegram

import java.io.File

/** Exact retired stores only; settings, browser data, grants and user files are never traversed. */
internal fun purgeRetiredTelegramFiles(filesDir: File): Boolean {
    var success = true
    listOf("telegram_bot.preferences_pb", "notification_listener.preferences_pb").forEach { name ->
        listOf(name, "$name.tmp").forEach { fileName ->
            val file = File(filesDir, "datastore/$fileName")
            if (file.exists() && !file.delete()) success = false
        }
    }
    return success
}
