package me.rerere.zeroagent.data.telegram

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RetiredTelegramFilesTest {
    @Test fun removesOnlyRetiredStoresAndCanRunAgain() {
        val root = Files.createTempDirectory("retired-telegram-fixture").toFile()
        try {
            val dir = root.resolve("datastore").apply { mkdirs() }
            listOf("telegram_bot.preferences_pb", "telegram_bot.preferences_pb.tmp", "notification_listener.preferences_pb").forEach {
                dir.resolve(it).writeText("fixture-only")
            }
            val keep = listOf("settings.preferences_pb", "browser_prefs.preferences_pb", "termux_prefs.preferences_pb", "tool_approval.preferences_pb")
            keep.forEach { dir.resolve(it).writeText("keep") }
            assertTrue(purgeRetiredTelegramFiles(root))
            assertTrue(purgeRetiredTelegramFiles(root))
            assertFalse(dir.resolve("telegram_bot.preferences_pb").exists())
            assertFalse(dir.resolve("notification_listener.preferences_pb").exists())
            keep.forEach { assertEquals("keep", dir.resolve(it).readText()) }
        } finally { root.deleteRecursively() }
    }
}
