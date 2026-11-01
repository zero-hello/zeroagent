package me.rerere.zeroagent.data.telegram

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Upgrade-only cleanup; no client, polling, credentials reader or outbound transport remains. */
object RetiredTelegramCleanup {
    /** Invoke after WorkManager initialization, before background scheduling, including after restore. */
    suspend fun run(context: Context): Boolean = withContext(Dispatchers.IO) {
        var success = true
        fun step(action: () -> Unit) {
            try { action() } catch (e: Exception) {
                success = false
                Log.w("RetiredTelegramCleanup", "Retired component cleanup failed", e)
            }
        }
        step { WorkManager.getInstance(context).cancelUniqueWork("telegram_bot_health").result.get(5, TimeUnit.SECONDS) }
        step { context.stopService(Intent().setClassName(context.packageName, "me.rerere.zeroagent.service.TelegramBotService")) }
        step {
            context.getSystemService(NotificationManager::class.java)?.apply {
                cancel(0xA1B2)
                deleteNotificationChannel("zeroagent_telegram_bot")
            }
        }
        step { if (!purgeRetiredTelegramFiles(context.filesDir)) success = false }
        success
    }
}
