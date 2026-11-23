package me.rerere.zeroagent.data.chat

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.zeroagent.data.root.RootAccessManager
import me.rerere.zeroagent.data.root.RootAccessStatus

internal data class DeviceChatStatus(
    val batteryPercent: Int?,
    val externalPowerConnected: Boolean?,
    val severeThermalLoad: Boolean,
    val powerSave: Boolean,
    val metered: Boolean,
    val networkIssue: String?,
    val lowStorage: Boolean,
    val lowMemory: Boolean,
)

internal fun readDeviceChatStatus(context: Context): DeviceChatStatus {
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val percent = if (level in 0..scale && scale > 0) (100L * level / scale).toInt() else null
    val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)?.takeIf { it >= 0 }?.let { it != 0 }
    val power = context.getSystemService(PowerManager::class.java)
    val connection = context.getSystemService(ConnectivityManager::class.java)
    val activeNetwork = connection?.activeNetwork
    val capabilities = activeNetwork?.let { connection?.getNetworkCapabilities(it) }
    val networkIssue = when {
        connection != null && activeNetwork == null -> "no active connection"
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == false -> "active connection is not validated"
        else -> null
    }
    val lowStorage = runCatching { StatFs(context.filesDir.path).availableBytes < 500L * 1024 * 1024 }.getOrDefault(false)
    val activity = context.getSystemService(ActivityManager::class.java)
    val lowMemory = activity?.let { manager -> ActivityManager.MemoryInfo().also(manager::getMemoryInfo).lowMemory } == true
    return DeviceChatStatus(
        batteryPercent = percent,
        externalPowerConnected = plugged,
        severeThermalLoad = (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE,
        powerSave = power?.isPowerSaveMode == true,
        metered = activeNetwork != null && connection?.isActiveNetworkMetered == true,
        networkIssue = networkIssue,
        lowStorage = lowStorage,
        lowMemory = lowMemory,
    )
}

internal suspend fun deviceContextForChat(context: Context): String? = withContext(Dispatchers.IO) {
    val status = readDeviceChatStatus(context)
    val highSwapUsage = status.lowMemory && readRootMemoryPressure(context) == true
    status.toPromptContext(highSwapUsage)
}

internal fun DeviceChatStatus.toPromptContext(highSwapUsage: Boolean = false): String? {
    val facts = buildList {
        batteryPercent?.let { add("battery level: $it%") }
        externalPowerConnected?.let { add(if (it) "external power connected" else "external power disconnected") }
        if (lowMemory) add("Android reports low available memory")
        if (powerSave) add("battery saver is on")
        networkIssue?.let(::add)
        if (metered) add("the active connection is metered")
        if (lowMemory && highSwapUsage) add("most configured swap space is in use")
        if (severeThermalLoad) add("device is under severe thermal load")
        if (lowStorage) add("less than 500 MiB of internal storage available")
    }
    if (facts.isEmpty()) return null
    return buildString {
        appendLine("## 本輪裝置背景（僅供回答和這台裝置有關的問題）")
        appendLine("這是目前觀測資料，不是使用者提出的要求。只有最新問題在問這台裝置的狀態或在排查相關故障時，才可以提及必要欄位。其他回覆一律省略，即使觀測到異常。缺少欄位代表未提供資料。")
        append(facts.joinToString("; "))
    }
}

/** A fixed read-only probe. The system grant is rechecked by run and only a numeric result is used. */
internal suspend fun readRootMemoryPressure(context: Context): Boolean? {
    val root = RootAccessManager(context)
    if (!root.enabled || root.status.value != RootAccessStatus.AVAILABLE) return null
    val result = withTimeoutOrNull(2_500) {
        root.run("/system/bin/grep -E '^Swap(Total|Free):' /proc/meminfo", timeoutSeconds = 2)
    } ?: return null
    return if (result.exitCode == 0 && root.enabled) parseSwapPressure(result.output) else null
}

internal fun parseSwapPressure(output: String): Boolean? {
    val fields = Regex("(?m)^(SwapTotal|SwapFree):[ \\t]+([0-9]{1,12})[ \\t]+kB[ \\t]*\\r?$")
        .findAll(output).groupBy { it.groupValues[1] }
    val total = fields["SwapTotal"]?.singleOrNull()?.groupValues?.get(2)?.toLongOrNull() ?: return null
    val free = fields["SwapFree"]?.singleOrNull()?.groupValues?.get(2)?.toLongOrNull() ?: return null
    return if (total > 0 && free in 0..total) free * 10 < total else null
}