package me.rerere.zeroagent.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AssistantWakeService : Service(), KoinComponent {
    companion object {
        private const val ID = 0x5A091
        private const val CHANNEL = "zeroagent_voice_assistant"
    }
    private val store: SettingsStore by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun attachBaseContext(base: Context) { super.attachBaseContext(base.zeroAgentLocalizedContext()) }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.zeroagent9_title), NotificationManager.IMPORTANCE_LOW)
        )
        try {
            foreground()
            AssistantAudioSessions.serviceReady.value = true
        } catch (_: Exception) {
            finishService()
            return
        }

        scope.launch {
            AssistantAudioSessions.count.collect { sessions ->
                if (sessions == 0) {
                    finishService()
                } else {
                    foreground()
                }
            }
        }
    }

    private fun foreground() {
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(getString(R.string.zeroagent9_title))
            .setContentText(getString(R.string.zeroagent9_notification_session))
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    private fun finishService() {
        AssistantAudioSessions.serviceReady.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        AssistantAudioSessions.serviceReady.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
