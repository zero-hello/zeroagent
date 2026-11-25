package me.rerere.zeroagent.data.local

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.data.usage.UsagePurpose

internal class GenerationResourceLimitException(message: String) : IllegalStateException(message)

internal class GenerationResourceLimits(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("generation_resource_limits", Context.MODE_PRIVATE)

    private val batteryLow = MutableStateFlow(preferences.getBoolean("pause_local_low", false))
    val pauseLocalLow = batteryLow.asStateFlow()
    private val batchCharging = MutableStateFlow(preferences.getBoolean("batch_charging", false))
    val requireBatchCharging = batchCharging.asStateFlow()

    fun setPauseLocalLow(enabled: Boolean) {
        preferences.edit().putBoolean("pause_local_low", enabled).apply()
        batteryLow.value = enabled
    }

    fun setRequireBatchCharging(enabled: Boolean) {
        preferences.edit().putBoolean("batch_charging", enabled).apply()
        batchCharging.value = enabled
    }

    fun ensureAllowed(provider: ProviderSetting, purpose: UsagePurpose) {
        val local = provider is ProviderSetting.LlamaCppLocal || provider is ProviderSetting.LiteRtLocal ||
            provider is ProviderSetting.AICore
        val localRule = local && batteryLow.value
        val batchRule = purpose == UsagePurpose.LAB_BATCH && batchCharging.value
        if (!localRule && !batchRule) return
        val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) > 0
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (batchRule && !plugged) throw GenerationResourceLimitException("批次工作需要接上電源。接上後可重試未完成的項目。")
        if (localRule && !plugged && level >= 0 && scale > 0 && level.toLong() * 100 <= scale.toLong() * 15) {
            throw GenerationResourceLimitException("電量剩下 15% 或更低，已暫停本機生成。充電後可重試。")
        }
    }
}