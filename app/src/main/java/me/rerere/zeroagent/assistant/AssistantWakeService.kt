package me.rerere.zeroagent.assistant

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.rerere.zeroagent.R
import me.rerere.zeroagent.assistant.wake.*
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AssistantWakeService : Service(), KoinComponent {
    companion object {
        private const val ID = 0x5A091
        private const val CHANNEL = "zeroagent_voice_assistant"
        private const val STOP = "app.zeroagent.assistant.STOP"
        suspend fun enableWake(context: Context, store: SettingsStore) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                throw AssistantFailure(AssistantIssue.MICROPHONE_PERMISSION)
            if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) throw AssistantFailure(AssistantIssue.NOTIFICATION_PERMISSION)
            if (!AssistantEntry.isDefault(context)) throw AssistantFailure(AssistantIssue.ROLE_REQUIRED)
            val profile = WakeProfileStore(context).load() ?: throw AssistantFailure(AssistantIssue.WAKE_UNTRAINED)
            val engine = WakeEngines.find(store.settingsFlow.value.voiceAssistant.wakeEngine) ?: throw AssistantFailure(AssistantIssue.WAKE_ENGINE)
            if (!engine.valid(profile)) throw AssistantFailure(AssistantIssue.WAKE_CORRUPT)
            val config = store.settingsFlow.value.voiceAssistant
            if (!config.enabled) throw AssistantFailure(AssistantIssue.DISABLED)
            if (profile.phrase != config.wakePhrase.trim()) throw AssistantFailure(AssistantIssue.WAKE_PHRASE_CHANGED)
            store.updateVoiceAssistant { it.copy(wakeEnabled = true) }
            AssistantRuntime.update(WakeStatus.STARTING)
            try { ContextCompat.startForegroundService(context, Intent(context, AssistantWakeService::class.java)) }
            catch (e: Exception) { store.updateVoiceAssistant { it.copy(wakeEnabled = false) }; AssistantRuntime.update(WakeStatus.ERROR, AssistantIssue.SERVICE_STOPPED); throw e }
        }
    }
    private val store: SettingsStore by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeJob: Job? = null
    private var capture: AssistantPcmCapture? = null
    private var observed: Triple<Boolean, Boolean, String>? = null
    private var lastNotice: String? = null
    private var stopping = false
    private var foregroundActive = false
    override fun attachBaseContext(base: Context) { super.attachBaseContext(base.zeroAgentLocalizedContext()) }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.zeroagent9_title), NotificationManager.IMPORTANCE_LOW))
        try { foreground(); AssistantAudioSessions.serviceReady.value = true }
        catch (_: Exception) { AssistantRuntime.update(WakeStatus.ERROR, AssistantIssue.SERVICE_STOPPED); finishService(); return }
        scope.launch {
            delay(10_000)
            if (!stopping && store.settingsFlow.value.init) {
                AssistantRuntime.update(WakeStatus.ERROR, AssistantIssue.SERVICE_STOPPED)
                AssistantAudioSessions.stopAll()
                finishService()
            }
        }
        scope.launch {
            combine(store.settingsFlow.filter { !it.init }, AssistantAudioSessions.count) { settings, sessions -> settings.voiceAssistant to sessions }
                .collect { (config, sessions) ->
                    if (stopping) return@collect
                    val key = Triple(config.enabled, config.wakeEnabled, config.wakePhrase)
                    if (observed != key) {
                        observed = key
                        capture?.stop(); wakeJob?.cancelAndJoin(); wakeJob = null
                        if (config.enabled && config.wakeEnabled) {
                            if (!AssistantEntry.isDefault(this@AssistantWakeService)) {
                                AssistantRuntime.update(WakeStatus.ERROR, AssistantIssue.ROLE_REQUIRED)
                                store.updateVoiceAssistant { it.copy(wakeEnabled = false) }
                            } else wakeJob = scope.launch { runWake(config) }
                        } else if (AssistantRuntime.wake.value.issue == null) AssistantRuntime.update(WakeStatus.STOPPED)
                    }
                    if (!config.enabled) AssistantAudioSessions.stopAll()
                    val latest = store.settingsFlow.value.voiceAssistant
                    if ((!latest.enabled || !latest.wakeEnabled) && AssistantAudioSessions.count.value == 0) finishService() else foreground()
                }
        }
        scope.launch {
            combine(AssistantRuntime.wake, me.rerere.zeroagent.utils.ZeroAgentLocale.changes) { _, _ -> Unit }
                .collect { lastNotice = null; updateNotice() }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            stopping = true
            capture?.stop(); wakeJob?.cancel()
            AssistantAudioSessions.stopAll()
            clearForeground()
            scope.launch {
                var issue: AssistantIssue? = null
                try { withTimeout(2000) { store.updateVoiceAssistant { it.copy(wakeEnabled = false) } }
                } catch (_: TimeoutCancellationException) { issue = AssistantIssue.SERVICE_STOPPED
                } catch (cancel: CancellationException) { throw cancel
                } catch (_: Exception) { issue = AssistantIssue.SERVICE_STOPPED
                } finally { AssistantRuntime.update(WakeStatus.STOPPED, issue); stopSelf() }
            }
            return START_NOT_STICKY
        }
        // A system restart reloads the persisted opt-in; the collector stops disabled services.
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun foreground() {
        ServiceCompat.startForeground(this, ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or (if (AssistantAudioSessions.count.value > 0) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0))
        foregroundActive = true
    }
    private fun clearForeground() {
        foregroundActive = false
        AssistantAudioSessions.serviceReady.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(ID)
    }
    private fun finishService() {
        stopping = true
        capture?.stop(); wakeJob?.cancel()
        clearForeground()
        stopSelf()
    }
    private fun notification(): android.app.Notification {
        val stop = PendingIntent.getService(this, 1909, Intent(this, AssistantWakeService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val settings = PendingIntent.getActivity(this, 1910, Intent(this, AssistantSettingsActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val message = if (AssistantAudioSessions.count.value > 0) R.string.zeroagent9_notification_session else when (AssistantRuntime.wake.value.status) {
            WakeStatus.LISTENING -> R.string.zeroagent9_notification_wake
            WakeStatus.PAUSED -> R.string.zeroagent9_notification_paused
            WakeStatus.ERROR -> R.string.zeroagent9_wake_error
            WakeStatus.STOPPED -> R.string.zeroagent9_wake_stopped
            WakeStatus.STARTING -> R.string.zeroagent9_notification_starting
        }
        val localized = applicationContext.zeroAgentLocalizedContext()
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_zeroagent)
            .setContentTitle(localized.getString(R.string.zeroagent9_title)).setContentText(localized.getString(message))
            .setContentIntent(settings).setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, localized.getString(R.string.zeroagent9_stop_audio), stop).build()
    }
    private fun updateNotice() {
        if (!foregroundActive || stopping) return
        val key = "${AssistantRuntime.wake.value.status}:${AssistantAudioSessions.count.value}"
        if (key != lastNotice) { lastNotice = key; runCatching { getSystemService(NotificationManager::class.java).notify(ID, notification()) } }
    }
    private suspend fun runWake(config: VoiceAssistantSettings) {
        try {
            val profile = WakeProfileStore(this).load() ?: throw AssistantFailure(AssistantIssue.WAKE_UNTRAINED)
            val engine = WakeEngines.find(config.wakeEngine) ?: throw AssistantFailure(AssistantIssue.WAKE_ENGINE)
            if (!engine.valid(profile)) throw AssistantFailure(AssistantIssue.WAKE_CORRUPT)
            if (profile.phrase != config.wakePhrase.trim()) throw AssistantFailure(AssistantIssue.WAKE_PHRASE_CHANGED)
            while (currentCoroutineContext().isActive) {
                val lease = AssistantMicrophone.tryWake()
                if (lease == null) { AssistantRuntime.update(WakeStatus.PAUSED); delay(40); continue }
                val pcm = AssistantPcmCapture(this); capture = pcm
                val power = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:VoiceWake").apply { setReferenceCounted(false) }
                val started = SystemClock.elapsedRealtime()
                var matched = false
                val segmenter = AdaptiveWakeSegmenter()
                try {
                    power.acquire(30 * 60 * 1000L)
                    pcm.read(keepGoing = { !matched && AssistantMicrophone.foregroundRequests.value == 0 && SystemClock.elapsedRealtime() - started < 20 * 60 * 1000L }) { frame, _ ->
                        AssistantRuntime.update(WakeStatus.LISTENING)
                        // Use the same calibrated onset/noise logic as enrollment, including quiet voices.
                        val segment = segmenter.accept(frame)
                        segment?.pcm?.let { utterance ->
                            matched = engine.detect(profile, utterance)
                            if (matched) pcm.stop()
                        }
                    }
                } finally { pcm.stop(); capture = null; lease.close(); if (power.isHeld) power.release() }
                if (matched) {
                    val latest = store.settingsFlow.value.voiceAssistant
                    if (!latest.enabled || !latest.wakeEnabled) break
                    if (getSystemService(KeyguardManager::class.java).isDeviceLocked) {
                        AssistantRuntime.update(WakeStatus.PAUSED, AssistantIssue.LOCKED)
                    } else if (!ZeroVoiceInteractionService.invoke()) throw AssistantFailure(if (AssistantEntry.isDefault(this)) AssistantIssue.SERVICE_STOPPED else AssistantIssue.ROLE_REQUIRED)
                    delay(2500)
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Throwable) {
            val issue = when (e) { is AssistantFailure -> e.issue; is SecurityException -> AssistantIssue.MICROPHONE_PERMISSION; is OutOfMemoryError -> AssistantIssue.MEMORY; else -> AssistantIssue.MICROPHONE_BUSY }
            AssistantRuntime.update(WakeStatus.ERROR, issue)
            try { withTimeout(2000) { store.updateVoiceAssistant { it.copy(wakeEnabled = false) } }
            } catch (_: TimeoutCancellationException) { }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { }
            if (AssistantAudioSessions.count.value == 0) finishService()
        }
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        lastNotice = null; updateNotice()
    }
    override fun onLowMemory() {
        capture?.stop(); wakeJob?.cancel(); AssistantRuntime.update(WakeStatus.ERROR, AssistantIssue.MEMORY)
        scope.launch { store.updateVoiceAssistant { it.copy(wakeEnabled = false) }; if (AssistantAudioSessions.count.value == 0) stopSelf() }
        super.onLowMemory()
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        val config = store.settingsFlow.value.voiceAssistant
        if (!config.enabled || (!config.wakeEnabled && AssistantAudioSessions.count.value == 0)) finishService()
        super.onTaskRemoved(rootIntent)
    }
    override fun onDestroy() {
        stopping = true; foregroundActive = false
        capture?.stop(); wakeJob?.cancel(); scope.cancel()
        AssistantAudioSessions.serviceReady.value = false
        AssistantAudioSessions.stopAll()
        val issue = AssistantRuntime.wake.value.issue
        AssistantRuntime.update(WakeStatus.STOPPED, issue)
        clearForeground()
        super.onDestroy()
    }
}
